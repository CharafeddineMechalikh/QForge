package com.mechalikh.qforge.state;

import java.util.Locale;
import com.mechalikh.qforge.operations.QuantumOperation;
import com.mechalikh.qforge.operations.QuantumGate;
import com.mechalikh.qforge.noise.NoiseModel;
import com.mechalikh.qforge.math.Complex;
import com.mechalikh.qforge.math.ComplexMatrix;
import com.mechalikh.qforge.math.MatrixOperations;

/**
 * Mixed quantum state stored as a dense density matrix.
 *
 * <p>The matrix has dimension {@code 2^numQubits} in both directions. Row and column
 * indices use the basis-state convention of {@link QuantumState}: the bit of qubit
 * {@code q} is bit {@code numQubits - 1 - q} of the index, so qubit {@code 0} is the most
 * significant bit. The diagonal entry {@code rho[i][i]} is the population of basis state
 * {@code i}.</p>
 *
 * <p>The constructor checks that the matrix is square, Hermitian, and of unit trace, but
 * it does not check positivity. The supplied matrix is stored directly, not copied, while
 * {@link #getMatrix()} returns a copy of it. Purity and entropy are computed lazily and
 * cached in mutable fields.</p>
 */
public class DensityMatrix extends QuantumState implements AmplitudeAccess {
    private final ComplexMatrix matrix;
    private final int dimension;
    private Double purity = null;
    private Double entropy = null;
    
    /**
     * Creates a density matrix from a matrix and a qubit count.
     *
     * <p>The matrix is stored as given, without copying. It must be square, of dimension
     * {@code 2^numQubits}, equal to its adjoint according to
     * {@link ComplexMatrix#equals(Object)}, and of unit trace within {@code 1e-10} in both
     * components. Positivity is not verified.</p>
     *
     * @param matrix density matrix data
     * @param numQubits number of qubits
     * @throws IllegalArgumentException if the matrix is not square, has the wrong
     *         dimension, is not Hermitian, or does not have unit trace
     */
    public DensityMatrix(ComplexMatrix matrix, int numQubits) {
        super(numQubits);
        
        if (matrix.rows() != matrix.cols()) {
            throw new IllegalArgumentException("Density matrix must be square");
        }
        
        int expectedDim = 1 << numQubits;
        if (matrix.rows() != expectedDim) {
            throw new IllegalArgumentException(
                String.format(Locale.ROOT, "Matrix dimension %d does not match 2^%d = %d",
                    matrix.rows(), numQubits, expectedDim)
            );
        }
        
        this.matrix = matrix;
        this.dimension = matrix.rows();
        
        // Validate properties of density matrix
        validateDensityMatrix();
    }
    
    /**
     * Creates the pure state {@code |0...0>} as a density matrix, with a single unit entry
     * at row {@code 0}, column {@code 0}.
     *
     * @param numQubits number of qubits, must be positive
     * @return the density matrix of {@code |0...0>}
     */
    public static DensityMatrix createZeroState(int numQubits) {
        int dim = 1 << numQubits;
        ComplexMatrix matrix = new ComplexMatrix(dim, dim);
        matrix.set(0, 0, Complex.ONE); // |0...0><0...0|
        return new DensityMatrix(matrix, numQubits);
    }
    
    /**
     * Creates the maximally mixed state {@code I / 2^numQubits}.
     *
     * @param numQubits number of qubits, must be positive
     * @return the maximally mixed density matrix
     */
    public static DensityMatrix createMaximallyMixedState(int numQubits) {
        int dim = 1 << numQubits;
        ComplexMatrix matrix = new ComplexMatrix(dim, dim);
        Complex value = new Complex(1.0 / dim, 0);
        for (int i = 0; i < dim; i++) {
            matrix.set(i, i, value);
        }
        return new DensityMatrix(matrix, numQubits);
    }
    
    /**
     * Creates a density matrix from a pure state.
     *
     * @param stateVector pure state to convert
     * @return the density matrix {@code |psi><psi|}
     */
    public static DensityMatrix fromStateVector(StateVector stateVector) {
        return stateVector.toDensityMatrix();
    }
    
    private void validateDensityMatrix() {
        // 1. Check Hermiticity: ρ = ρ†
        if (!matrix.equals(matrix.adjoint())) {
            throw new IllegalArgumentException("Density matrix must be Hermitian");
        }
        
        // 2. Check unit trace: tr(ρ) = 1
        Complex trace = MatrixOperations.trace(matrix);
        if (Math.abs(trace.real() - 1.0) > 1e-10 || Math.abs(trace.imag()) > 1e-10) {
            throw new IllegalArgumentException("Density matrix must have unit trace");
        }
        
        // 3. Check positivity (by checking eigenvalues are non-negative)
        // This is computationally expensive, so we'll skip it unless explicitly requested
    }
    
    /**
     * Applies an operation and returns the resulting mixed state.
     *
     * <p>A {@link QuantumGate} is applied as {@code K * rho * K^dagger} with the gate
     * matrix {@code K}, through a single Kraus operator. A {@link NoiseModel} is applied
     * through its Kraus operators, summed deterministically over the whole set.</p>
     *
     * @param operation gate or noise model to apply
     * @return the resulting density matrix
     * @throws IllegalArgumentException if a noise model has no target qubits, or if the
     *         resulting matrix fails density-matrix validation
     * @throws UnsupportedOperationException if the operation type is not supported
     */
    @Override
    public QuantumState applyOperation(QuantumOperation operation) {
        if (operation instanceof QuantumGate) {
            QuantumGate gate = (QuantumGate) operation;
            return applyKrausOperators(
                new ComplexMatrix[]{gate.getMatrix()}, gate.getMatrixQubitIndices());
        }
        if (operation instanceof NoiseModel) {
            NoiseModel noise = (NoiseModel) operation;
            Qubit[] targets = noise.getTargetQubits();
            if (targets.length == 0) {
                throw new IllegalArgumentException(
                    "Noise model has no target qubits; assign targets before applying");
            }
            int[] indices = new int[targets.length];
            for (int i = 0; i < targets.length; i++) {
                indices[i] = targets[i].getIndex();
            }
            return applyKrausOperators(noise.getKrausOperators(targets.length), indices);
        }
        throw new UnsupportedOperationException(
            "Operation application not implemented for: " + operation.getClass().getName());
    }
    
    /**
     * Returns the square root of the diagonal entry for one basis state.
     *
     * <p>The diagonal entry of a valid density matrix is real and non-negative, so this
     * value is the square root of the population. It is a representation choice, not a
     * pure-state amplitude; use {@link #getProbability(int)} for the population itself.</p>
     *
     * @param basisState basis-state index
     * @return the square root of {@code rho[basisState][basisState]}
     * @throws IndexOutOfBoundsException if {@code basisState} is outside the matrix bounds
     */
    @Override
    public Complex getAmplitude(int basisState) {
        // For density matrix, "amplitude" doesn't have the same meaning
        // Return diagonal element as probability amplitude
        return matrix.get(basisState, basisState).sqrt();
    }
    
    /**
     * Returns the population of one basis state, the real part of its diagonal entry.
     *
     * @param basisState basis-state index
     * @return the population
     * @throws IndexOutOfBoundsException if {@code basisState} is outside the matrix bounds
     */
    @Override
    public double getProbability(int basisState) {
        return matrix.get(basisState, basisState).real();
    }
    
    /**
     * Returns the populations of all basis states, taken from the diagonal.
     *
     * @return a new array of length {@code 2^numQubits}
     */
    @Override
    public double[] getProbabilities() {
        double[] probs = new double[dimension];
        for (int i = 0; i < dimension; i++) {
            probs[i] = matrix.get(i, i).real();
        }
        return probs;
    }
    
    /**
     * Returns the tensor product of this state and another density matrix.
     *
     * <p>This state keeps qubit positions {@code 0} to {@code numQubits - 1}; the qubits
     * of {@code other} follow. Entry {@code (i, j)} of this state and entry {@code (k, l)}
     * of {@code other} are stored at
     * {@code (i * otherDim + k, j * otherDim + l)}.</p>
     *
     * @param other right-hand factor
     * @return the combined mixed state
     * @throws IllegalArgumentException if {@code other} is not a {@code DensityMatrix}
     */
    @Override
    public QuantumState tensorProduct(QuantumState other) {
        if (!(other instanceof DensityMatrix)) {
            throw new IllegalArgumentException("Can only tensor product with another DensityMatrix");
        }
        
        DensityMatrix otherMatrix = (DensityMatrix) other;
        ComplexMatrix tensorProduct = MatrixOperations.tensorProduct(this.matrix, otherMatrix.matrix);
        int totalQubits = this.numQubits + otherMatrix.numQubits;
        
        return new DensityMatrix(tensorProduct, totalQubits);
    }
    
    /**
     * Traces out all qubits not listed and returns the reduced density matrix.
     *
     * <p>See {@link QuantumState#partialTrace(int[])} for the index order of the kept
     * qubits.</p>
     *
     * @param qubitsToKeep qubit indices to keep, in reduced-state order
     * @return the reduced density matrix
     * @throws IllegalArgumentException if the array is null or empty, or contains an
     *         out-of-range or duplicate index
     */
    @Override
    public QuantumState partialTrace(int[] qubitsToKeep) {
        return super.partialTrace(qubitsToKeep);
    }
    
    /**
     * Projects the target qubits onto one computational basis outcome and renormalizes.
     *
     * <p>The probability is the sum of the diagonal entries whose target bits equal
     * {@code outcome}. Every entry of the surviving block is divided by that probability,
     * and entries outside the block are zero. Bit {@code j} of {@code outcome}, counted
     * from the most significant bit, is placed on qubit {@code targetQubits[j]}.</p>
     *
     * @param targetQubits qubit indices to measure, in the order used to interpret
     *        {@code outcome}
     * @param outcome packed outcome bits, in {@code [0, 2^targetQubits.length)}
     * @return the collapsed density matrix
     * @throws IllegalArgumentException if the target array or the outcome is invalid
     * @throws IllegalStateException if the outcome has probability below {@code 1e-15}
     */
    @Override
    public QuantumState collapseToBasisState(int[] targetQubits, int outcome) {
        int[] targets = validateTargetQubits(targetQubits);
        int numOutcomes = 1 << targets.length;
        if (outcome < 0 || outcome >= numOutcomes) {
            throw new IllegalArgumentException("Measurement outcome out of range: " + outcome);
        }
        
        int restDim = 1 << (numQubits - targets.length);
        double probability = 0.0;
        for (int rest = 0; rest < restDim; rest++) {
            int index = insertBits(rest, outcome, targets);
            probability += matrix.get(index, index).real();
        }
        
        if (probability < 1e-15) {
            throw new IllegalStateException("Cannot collapse onto a measurement outcome with zero probability");
        }
        
        ComplexMatrix collapsed = new ComplexMatrix(dimension, dimension);
        for (int restRow = 0; restRow < restDim; restRow++) {
            int row = insertBits(restRow, outcome, targets);
            for (int restCol = 0; restCol < restDim; restCol++) {
                int col = insertBits(restCol, outcome, targets);
                collapsed.set(row, col, matrix.get(row, col).divide(probability));
            }
        }
        
        return new DensityMatrix(collapsed, numQubits);
    }
    
    /**
     * Projects the target qubits onto a supplied eigenvector and renormalizes.
     *
     * <p>With {@code P = |v><v|} built from the normalized eigenvector, the probability is
     * {@code tr(P * rho)} and the result is {@code P * rho * P / probability}. After the
     * operation the target qubits are in the given eigenvector up to a global phase. The
     * eigenvector need not be normalized.</p>
     *
     * @param targetQubits qubit indices that are projected
     * @param eigenvector vector over the target qubits, with length
     *        {@code 2^targetQubits.length}
     * @return the collapsed density matrix
     * @throws IllegalArgumentException if the target array is invalid, the eigenvector
     *         length does not match, or the eigenvector is zero
     * @throws IllegalStateException if the eigenvector has probability below
     *         {@code 1e-15}
     */
    @Override
    public QuantumState collapseToEigenvector(int[] targetQubits, Complex[] eigenvector) {
        int[] targets = validateTargetQubits(targetQubits);
        int targetDim = 1 << targets.length;
        if (eigenvector.length != targetDim) {
            throw new IllegalArgumentException(
                "Eigenvector dimension " + eigenvector.length + " does not match " + targets.length + " target qubits");
        }
        
        double eigenNorm = 0.0;
        for (Complex value : eigenvector) {
            eigenNorm += value.absSquare();
        }
        if (eigenNorm < 1e-15) {
            throw new IllegalArgumentException("Eigenvector must be non-zero");
        }
        double eigenScale = 1.0 / Math.sqrt(eigenNorm);
        
        int restDim = 1 << (numQubits - targets.length);
        double probability = 0.0;
        for (int rest = 0; rest < restDim; rest++) {
            for (int a = 0; a < targetDim; a++) {
                for (int b = 0; b < targetDim; b++) {
                    double term = eigenvector[a].conjugate()
                        .multiply(matrix.get(insertBits(rest, a, targets), insertBits(rest, b, targets)))
                        .multiply(eigenvector[b]).real();
                    probability += term;
                }
            }
        }
        probability *= eigenScale * eigenScale;
        
        if (probability < 1e-15) {
            throw new IllegalStateException("Cannot collapse onto an eigenvector with zero probability");
        }
        
        ComplexMatrix collapsed = new ComplexMatrix(dimension, dimension);
        for (int restRow = 0; restRow < restDim; restRow++) {
            for (int restCol = 0; restCol < restDim; restCol++) {
                Complex blockValue = Complex.ZERO;
                for (int a = 0; a < targetDim; a++) {
                    for (int b = 0; b < targetDim; b++) {
                        blockValue = blockValue.add(eigenvector[a].conjugate()
                            .multiply(matrix.get(insertBits(restRow, a, targets), insertBits(restCol, b, targets)))
                            .multiply(eigenvector[b]));
                    }
                }
                blockValue = blockValue.scale(eigenScale * eigenScale / probability);
                
                for (int a = 0; a < targetDim; a++) {
                    for (int b = 0; b < targetDim; b++) {
                        Complex value = eigenvector[a].multiply(blockValue).multiply(eigenvector[b].conjugate());
                        collapsed.set(insertBits(restRow, a, targets), insertBits(restCol, b, targets), value);
                    }
                }
            }
        }
        
        return new DensityMatrix(collapsed, numQubits);
    }
    
    /**
     * Returns a density matrix with unit trace.
     *
     * <p>If the trace differs from {@code 1} in either component by more than
     * {@code 1e-12}, a new density matrix is returned whose entries are divided by the
     * real part of the trace. Otherwise this instance is returned unchanged.</p>
     *
     * @return a density matrix with unit trace
     */
    @Override
    public DensityMatrix normalize() {
        Complex trace = MatrixOperations.trace(matrix);
        if (Math.abs(trace.real() - 1.0) > 1e-12 || Math.abs(trace.imag()) > 1e-12) {
            ComplexMatrix normalized = matrix.scale(1.0 / trace.real());
            return new DensityMatrix(normalized, numQubits);
        }
        return this;
    }
    
    /**
     * Tests whether the state is pure, using the cached purity.
     *
     * @return {@code true} if the purity is within {@code 1e-10} of {@code 1}
     */
    @Override
    public boolean isPure() {
        if (purity == null) {
            purity = getPurity();
        }
        return Math.abs(purity - 1.0) < 1e-10;
    }
    
    /**
     * Returns the purity {@code tr(rho^2)} and caches it.
     *
     * @return the purity, which is {@code 1} for a pure state and smaller for a mixed
     *         state
     */
    @Override
    public double getPurity() {
        if (purity != null) {
            return purity;
        }
        
        // Purity = tr(ρ²)
        ComplexMatrix rhoSquared = MatrixOperations.multiply(matrix, matrix);
        Complex trace = MatrixOperations.trace(rhoSquared);
        purity = trace.real();
        return purity;
    }
    
    /**
     * Returns an independent copy of this state.
     *
     * @return a new density matrix with a copied matrix
     */
    @Override
    public DensityMatrix copy() {
        return new DensityMatrix(matrix.copy(), numQubits);
    }
    
    /**
     * Returns a copy of the underlying matrix.
     *
     * @return a copy of the density matrix data
     */
    public ComplexMatrix getMatrix() {
        return matrix.copy();
    }
    
    /**
     * Returns the von Neumann entropy {@code -sum(lambda * log2(lambda))} and caches it.
     *
     * <p>Eigenvalues not greater than {@code 1e-12} are skipped.</p>
     *
     * @return the entropy in bits
     */
    public double getVonNeumannEntropy() {
        if (entropy != null) {
            return entropy;
        }
        
        // S = -tr(ρ log₂ ρ) = -Σ λ_i log₂ λ_i
        double[] eigenvalues = MatrixOperations.eigenvalues(matrix);
        entropy = 0.0;
        
        for (double lambda : eigenvalues) {
            if (lambda > 1e-12) {
                entropy -= lambda * Math.log(lambda) / Math.log(2);
            }
        }
        
        return entropy;
    }
    
    /**
     * Returns the Hermitian square root of the density matrix.
     *
     * <p>The matrix is decomposed into {@code V * D * V^dagger}, negative eigenvalues are
     * clamped to zero, and the result is {@code V * sqrt(D) * V^dagger}.</p>
     *
     * @return the positive semidefinite square root
     */
    public ComplexMatrix squareRoot() {
        ComplexMatrix[] eigen = MatrixOperations.eigenDecomposition(matrix);
        ComplexMatrix diagonal = eigen[0];
        ComplexMatrix vectors = eigen[1];
        ComplexMatrix rootDiagonal = new ComplexMatrix(dimension, dimension);
        for (int i = 0; i < dimension; i++) {
            double value = Math.max(0.0, diagonal.get(i, i).real());
            rootDiagonal.set(i, i, new Complex(Math.sqrt(value), 0));
        }
        return vectors.multiply(rootDiagonal).multiply(vectors.adjoint());
    }
    
    /**
     * Returns the fidelity between two density matrices.
     *
     * <p>The value is computed from {@code sqrt(rho1) * sqrt(rho2)}: the square roots of
     * the eigenvalues of its Gram matrix are summed, and the sum is squared. The result is
     * clamped to {@code [0, 1]}.</p>
     *
     * @param first first density matrix
     * @param second second density matrix
     * @return the fidelity in {@code [0, 1]}
     * @throws IllegalArgumentException if the states have different qubit counts
     */
    public static double fidelity(DensityMatrix first, DensityMatrix second) {
        if (first.getNumQubits() != second.getNumQubits()) {
            throw new IllegalArgumentException("States must have the same number of qubits");
        }
        
        ComplexMatrix product = first.squareRoot().multiply(second.squareRoot());
        ComplexMatrix gram = product.adjoint().multiply(product);
        double[] eigenvalues = gram.eigenvalues();
        
        double traceNorm = 0.0;
        for (double eigenvalue : eigenvalues) {
            traceNorm += Math.sqrt(Math.max(0.0, eigenvalue));
        }
        
        double fidelity = traceNorm * traceNorm;
        return Math.min(1.0, Math.max(0.0, fidelity));
    }
    
    /**
     * Returns the trace distance between two density matrices.
     *
     * <p>The value is half the sum of the absolute eigenvalues of
     * {@code first - second}, clamped to {@code [0, 1]}.</p>
     *
     * @param first first density matrix
     * @param second second density matrix
     * @return the trace distance in {@code [0, 1]}
     * @throws IllegalArgumentException if the states have different qubit counts
     */
    public static double traceDistance(DensityMatrix first, DensityMatrix second) {
        if (first.getNumQubits() != second.getNumQubits()) {
            throw new IllegalArgumentException("States must have the same number of qubits");
        }
        
        ComplexMatrix difference = first.matrix.subtract(second.matrix);
        double[] eigenvalues = difference.eigenvalues();
        double distance = 0.0;
        for (double eigenvalue : eigenvalues) {
            distance += Math.abs(eigenvalue);
        }
        return Math.min(1.0, Math.max(0.0, 0.5 * distance));
    }
    
    /**
     * Converts a pure density matrix to a state vector.
     *
     * <p>The eigenvector of the dominant eigenvalue is returned, with its global phase
     * fixed so that the first amplitude whose magnitude is at least {@code 1e-12} is real
     * and positive.</p>
     *
     * @return the pure state
     * @throws IllegalStateException if this state is not pure, or if its dominant
     *         eigenvalue is not within {@code 1e-8} of {@code 1}
     */
    public StateVector toStateVector() throws IllegalStateException {
        if (!isPure()) {
            throw new IllegalStateException("Cannot convert mixed state to state vector");
        }
        
        ComplexMatrix[] eigen = MatrixOperations.eigenDecomposition(matrix);
        double[] eigenvalues = new double[dimension];
        for (int i = 0; i < dimension; i++) {
            eigenvalues[i] = eigen[0].get(i, i).real();
        }
        
        int dominant = 0;
        for (int i = 1; i < dimension; i++) {
            if (eigenvalues[i] > eigenvalues[dominant]) {
                dominant = i;
            }
        }
        if (Math.abs(eigenvalues[dominant] - 1.0) > 1e-8) {
            throw new IllegalStateException("Pure density matrix does not have a unit eigenvalue");
        }
        
        ComplexMatrix eigenvectors = eigen[1];
        Complex[] amplitudes = new Complex[dimension];
        for (int i = 0; i < dimension; i++) {
            amplitudes[i] = eigenvectors.get(i, dominant);
        }
        
        int firstNonZero = 0;
        while (firstNonZero < dimension && amplitudes[firstNonZero].abs() < 1e-12) {
            firstNonZero++;
        }
        if (firstNonZero < dimension) {
            Complex phase = amplitudes[firstNonZero].conjugate().divide(amplitudes[firstNonZero].abs());
            for (int i = 0; i < dimension; i++) {
                amplitudes[i] = amplitudes[i].multiply(phase);
            }
        }
        
        return new StateVector(amplitudes, numQubits);
    }
    
    /**
     * Tests whether all eigenvalues are non-negative, within a tolerance of
     * {@code 1e-10}.
     *
     * @return {@code true} if no eigenvalue is below {@code -1e-10}
     */
    public boolean isPositive() {
        // Check if all eigenvalues are non-negative
        double[] eigenvalues = MatrixOperations.eigenvalues(matrix);
        for (double lambda : eigenvalues) {
            if (lambda < -1e-10) { // Small tolerance for numerical errors
                return false;
            }
        }
        return true;
    }
    
    /**
     * Compares this state with another object.
     *
     * <p>Two density matrices are equal when they have the same number of qubits and their
     * matrices are equal according to {@link ComplexMatrix#equals(Object)}.</p>
     *
     * @param obj object to compare with
     * @return {@code true} if {@code obj} is a {@code DensityMatrix} with an equal matrix
     */
    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        
        DensityMatrix other = (DensityMatrix) obj;
        if (numQubits != other.numQubits) return false;
        if (dimension != other.dimension) return false;
        
        return matrix.equals(other.matrix);
    }
    
    /**
     * Returns a hash code derived from the qubit count, dimension, and matrix hash.
     *
     * @return the hash code
     */
    @Override
    public int hashCode() {
        int result = numQubits;
        result = 31 * result + dimension;
        result = 31 * result + matrix.hashCode();
        return result;
    }
    
    /**
     * Returns a diagnostic description with the diagonal populations and some coherences.
     *
     * <p>At most eight diagonal entries with probability above {@code 1e-6} and at most
     * four off-diagonal entries with magnitude above {@code 1e-3} are listed.</p>
     *
     * @return a multi-line description of this state
     */
    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("DensityMatrix[")
          .append(numQubits)
          .append(" qubits, dim=")
          .append(dimension)
          .append(", purity=")
          .append(String.format(Locale.ROOT, "%.4f", getPurity()))
          .append("]:\n");
        
        // Show diagonal elements (probabilities)
        sb.append("Diagonal (probabilities):\n");
        for (int i = 0; i < Math.min(dimension, 8); i++) {
            double prob = matrix.get(i, i).real();
            if (prob > 1e-6) {
                sb.append(String.format(Locale.ROOT, "  |%s>: %.4f\n",
                    Integer.toBinaryString(i), prob));
            }
        }
        
        // Show some off-diagonal elements (coherences)
        sb.append("Significant coherences:\n");
        int shown = 0;
        for (int i = 0; i < dimension && shown < 4; i++) {
            for (int j = i + 1; j < dimension && shown < 4; j++) {
                Complex element = matrix.get(i, j);
                if (element.abs() > 1e-3) {
                    sb.append(String.format(Locale.ROOT, "  <%s|rho|%s> = %s\n",
                        Integer.toBinaryString(i),
                        Integer.toBinaryString(j),
                        element));
                    shown++;
                }
            }
        }
        
        if (dimension > 8) {
            sb.append("  ... truncated\n");
        }
        
        return sb.toString();
    }
}