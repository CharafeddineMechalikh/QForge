package com.quantum.simulator.state;

import java.util.Locale;
import com.quantum.simulator.operations.QuantumOperation;
import com.quantum.simulator.operations.QuantumGate;
import com.quantum.simulator.noise.NoiseModel;
import com.quantum.simulator.math.Complex;
import com.quantum.simulator.math.ComplexMatrix;
import com.quantum.simulator.math.MatrixOperations;

public class DensityMatrix extends QuantumState {
    private final ComplexMatrix matrix;
    private final int dimension;
    private Double purity = null;
    private Double entropy = null;
    
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
    
    public static DensityMatrix createZeroState(int numQubits) {
        int dim = 1 << numQubits;
        ComplexMatrix matrix = new ComplexMatrix(dim, dim);
        matrix.set(0, 0, Complex.ONE); // |0...0><0...0|
        return new DensityMatrix(matrix, numQubits);
    }
    
    public static DensityMatrix createMaximallyMixedState(int numQubits) {
        int dim = 1 << numQubits;
        ComplexMatrix matrix = new ComplexMatrix(dim, dim);
        Complex value = new Complex(1.0 / dim, 0);
        for (int i = 0; i < dim; i++) {
            matrix.set(i, i, value);
        }
        return new DensityMatrix(matrix, numQubits);
    }
    
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
    
    @Override
    public Complex getAmplitude(int basisState) {
        // For density matrix, "amplitude" doesn't have the same meaning
        // Return diagonal element as probability amplitude
        return matrix.get(basisState, basisState).sqrt();
    }
    
    @Override
    public double getProbability(int basisState) {
        return matrix.get(basisState, basisState).real();
    }
    
    @Override
    public double[] getProbabilities() {
        double[] probs = new double[dimension];
        for (int i = 0; i < dimension; i++) {
            probs[i] = matrix.get(i, i).real();
        }
        return probs;
    }
    
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
    
    @Override
    public QuantumState partialTrace(int[] qubitsToKeep) {
        return super.partialTrace(qubitsToKeep);
    }
    
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
    
    @Override
    public DensityMatrix normalize() {
        Complex trace = MatrixOperations.trace(matrix);
        if (Math.abs(trace.real() - 1.0) > 1e-12 || Math.abs(trace.imag()) > 1e-12) {
            ComplexMatrix normalized = matrix.scale(1.0 / trace.real());
            return new DensityMatrix(normalized, numQubits);
        }
        return this;
    }
    
    @Override
    public boolean isPure() {
        if (purity == null) {
            purity = getPurity();
        }
        return Math.abs(purity - 1.0) < 1e-10;
    }
    
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
    
    @Override
    public DensityMatrix copy() {
        return new DensityMatrix(matrix.copy(), numQubits);
    }
    
    public ComplexMatrix getMatrix() {
        return matrix.copy();
    }
    
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
    
    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        
        DensityMatrix other = (DensityMatrix) obj;
        if (numQubits != other.numQubits) return false;
        if (dimension != other.dimension) return false;
        
        return matrix.equals(other.matrix);
    }
    
    @Override
    public int hashCode() {
        int result = numQubits;
        result = 31 * result + dimension;
        result = 31 * result + matrix.hashCode();
        return result;
    }
    
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