package com.mechalikh.qforge.state;

import java.util.Locale;
import com.mechalikh.qforge.operations.QuantumOperation;
import com.mechalikh.qforge.operations.QuantumGate;
import com.mechalikh.qforge.math.Complex;
import com.mechalikh.qforge.math.ComplexMatrix;
import com.mechalikh.qforge.math.MatrixOperations;
import java.util.Arrays;

/**
 * Pure quantum state stored as a dense array of complex amplitudes.
 *
 * <p>The amplitude at index {@code i} belongs to the computational basis state whose bit
 * for qubit {@code q} is bit {@code numQubits - 1 - q} of {@code i}. Qubit {@code 0} is
 * therefore the most significant bit, and basis state {@code 0} is {@code |0...0>}.</p>
 *
 * <p>The constructor normalizes the amplitudes to unit norm. Gate application is
 * deterministic. Noise described by several Kraus operators is applied as a sampled
 * trajectory, see
 * {@link QuantumState#applyKrausOperators(ComplexMatrix[], int[], java.util.Random)}.</p>
 */
public class StateVector extends QuantumState {
    private final Complex[] amplitudes;
    private final int dimension;
    
    /**
     * Creates a state from an amplitude array and normalizes it.
     *
     * <p>The array is cloned before use. If the sum of squared magnitudes differs from
     * {@code 1} by more than {@code 1e-12}, every amplitude is divided by the norm. A zero
     * array is not rejected here; normalization then divides by zero.</p>
     *
     * @param amplitudes amplitudes in basis-state index order, length
     *        {@code 2^numQubits}
     * @param numQubits number of qubits
     * @throws IllegalArgumentException if the array length is not {@code 2^numQubits}, or
     *         if {@code numQubits} is not positive
     */
    public StateVector(Complex[] amplitudes, int numQubits) {
        super(numQubits);
        if (amplitudes.length != (1 << numQubits)) {
            throw new IllegalArgumentException(
                String.format(Locale.ROOT, "Amplitude array length %d does not match 2^%d = %d",
                    amplitudes.length, numQubits, 1 << numQubits)
            );
        }
        this.amplitudes = amplitudes.clone();
        this.dimension = amplitudes.length;
        normalize();
    }
    
    /**
     * Creates the all-zero basis state {@code |0...0>}, with amplitude {@code 1} at index
     * {@code 0} and zero elsewhere.
     *
     * @param numQubits number of qubits, must be positive
     * @return the state {@code |0...0>}
     */
    public static StateVector createZeroState(int numQubits) {
        Complex[] amplitudes = new Complex[1 << numQubits];
        Arrays.fill(amplitudes, Complex.ZERO);
        amplitudes[0] = Complex.ONE; // |0...0>
        return new StateVector(amplitudes, numQubits);
    }
    
    /**
     * Creates an equal superposition of all basis states, with amplitude
     * {@code 1 / sqrt(2^numQubits)} on every index.
     *
     * @param numQubits number of qubits, must be positive
     * @return the uniform superposition
     */
    public static StateVector createPlusState(int numQubits) {
        int dim = 1 << numQubits;
        Complex[] amplitudes = new Complex[dim];
        Complex value = new Complex(1.0 / Math.sqrt(dim), 0);
        Arrays.fill(amplitudes, value);
        return new StateVector(amplitudes, numQubits);
    }
    
    /**
     * Creates a computational basis state with amplitude {@code 1} at the given index.
     *
     * @param basisState basis-state index in {@code [0, 2^numQubits)}
     * @param numQubits number of qubits, must be positive
     * @return the basis state
     * @throws IllegalArgumentException if {@code basisState} is out of range
     */
    public static StateVector fromComputationalBasis(int basisState, int numQubits) {
        if (basisState < 0 || basisState >= (1 << numQubits)) {
            throw new IllegalArgumentException("Basis state out of range");
        }
        
        Complex[] amplitudes = new Complex[1 << numQubits];
        Arrays.fill(amplitudes, Complex.ZERO);
        amplitudes[basisState] = Complex.ONE;
        return new StateVector(amplitudes, numQubits);
    }
    
    /**
     * Applies an operation to this state.
     *
     * <p>Only {@link QuantumGate} is supported; it is applied through
     * {@link #applyGate(QuantumGate)}. Other operation types, including noise models,
     * cause an {@link UnsupportedOperationException}.</p>
     *
     * @param operation operation to apply
     * @return the resulting pure state
     * @throws UnsupportedOperationException if the operation is not a gate
     */
    @Override
    public QuantumState applyOperation(QuantumOperation operation) {
        if (operation instanceof QuantumGate) {
            return applyGate((QuantumGate) operation);
        }
        throw new UnsupportedOperationException("Unsupported operation type: " + operation.getClass());
    }
    
    
    /**
     * Applies a gate matrix to the qubits reported by the gate.
     *
     * <p>The gate acts on {@code gate.getMatrixQubitIndices()}, in that order. For a
     * controlled gate the array lists control qubits first and target qubits after, so the
     * first index corresponds to the most significant bit of the gate matrix index.</p>
     *
     * @param gate gate to apply
     * @return the resulting pure state
     * @throws IllegalArgumentException if a target index is invalid for this state or a
     *         duplicate, or if the gate matrix size does not match the number of qubits
     */
    public StateVector applyGate(QuantumGate gate) {
        return (StateVector) applyKrausOperators(
            new ComplexMatrix[]{gate.getMatrix()}, gate.getMatrixQubitIndices());
    }
    
    /**
     * Applies a 2 by 2 gate matrix to one qubit.
     *
     * <p>Basis states are processed in pairs that differ only in the bit of
     * {@code targetQubit}. Index {@code 0} of the matrix multiplies the amplitude whose
     * bit is {@code 0}, and index {@code 1} multiplies the amplitude whose bit is
     * {@code 1}.</p>
     *
     * @param gate 2 by 2 gate matrix
     * @param targetQubit qubit the gate acts on
     * @return the resulting pure state
     * @throws IllegalArgumentException if {@code targetQubit} is outside
     *         {@code [0, numQubits)}
     */
    public StateVector applySingleQubitGate(ComplexMatrix gate, int targetQubit) {
        if (targetQubit < 0 || targetQubit >= numQubits) {
            throw new IllegalArgumentException("Target qubit index out of bounds");
        }
        
        Complex[] newAmplitudes = new Complex[dimension];
        int stride = 1 << (numQubits - 1 - targetQubit);
        int blockSize = stride;
        
        for (int block = 0; block < dimension; block += 2 * blockSize) {
            for (int offset = 0; offset < blockSize; offset++) {
                int idx0 = block + offset;
                int idx1 = block + offset + blockSize;
                
                Complex amp0 = amplitudes[idx0];
                Complex amp1 = amplitudes[idx1];
                
                // Apply 2x2 gate: |ψ> -> U|ψ>
                newAmplitudes[idx0] = gate.get(0, 0).multiply(amp0)
                                          .add(gate.get(0, 1).multiply(amp1));
                newAmplitudes[idx1] = gate.get(1, 0).multiply(amp0)
                                          .add(gate.get(1, 1).multiply(amp1));
            }
        }
        
        return new StateVector(newAmplitudes, numQubits);
    }
    
    /**
     * Returns a copy of the amplitude of one basis state.
     *
     * @param basisState basis-state index
     * @return the complex amplitude
     * @throws IndexOutOfBoundsException if {@code basisState} is outside
     *         {@code [0, 2^numQubits)}
     */
    @Override
    public Complex getAmplitude(int basisState) {
        if (basisState < 0 || basisState >= dimension) {
            throw new IndexOutOfBoundsException("Basis state index out of bounds");
        }
        return new Complex(amplitudes[basisState]);
    }
    
    /**
     * Returns the probability of one basis state, the squared magnitude of its amplitude.
     *
     * @param basisState basis-state index
     * @return the probability
     * @throws IndexOutOfBoundsException if {@code basisState} is out of range
     */
    @Override
    public double getProbability(int basisState) {
        return getAmplitude(basisState).absSquare();
    }
    
    /**
     * Returns the probabilities of all basis states.
     *
     * @return a new array of length {@code 2^numQubits}
     */
    @Override
    public double[] getProbabilities() {
        double[] probs = new double[dimension];
        for (int i = 0; i < dimension; i++) {
            probs[i] = amplitudes[i].absSquare();
        }
        return probs;
    }
    
    /**
     * Returns the tensor product of this state and another pure state.
     *
     * <p>This state keeps qubit positions {@code 0} to {@code numQubits - 1}; the qubits
     * of {@code other} follow. Amplitude pair {@code (i, j)} is stored at index
     * {@code (i << other.numQubits) | j}.</p>
     *
     * @param other right-hand factor
     * @return the combined pure state
     * @throws IllegalArgumentException if {@code other} is not a {@code StateVector}
     */
    @Override
    public QuantumState tensorProduct(QuantumState other) {
        if (!(other instanceof StateVector)) {
            throw new IllegalArgumentException("Can only tensor product with another StateVector");
        }
        
        StateVector otherVector = (StateVector) other;
        int totalQubits = this.numQubits + otherVector.numQubits;
        int totalDim = 1 << totalQubits;
        
        Complex[] newAmplitudes = new Complex[totalDim];
        
        for (int i = 0; i < this.dimension; i++) {
            for (int j = 0; j < otherVector.dimension; j++) {
                // Index in combined system: i shifted left by other.numQubits bits, plus j
                int idx = (i << otherVector.numQubits) | j;
                newAmplitudes[idx] = this.amplitudes[i].multiply(otherVector.amplitudes[j]);
            }
        }
        
        return new StateVector(newAmplitudes, totalQubits);
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
     * <p>Amplitudes outside the selected subspace are set to zero, and the remaining
     * amplitudes are divided by their norm. Bit {@code j} of {@code outcome}, counted from
     * the most significant bit, is placed on qubit {@code targetQubits[j]}.</p>
     *
     * @param targetQubits qubit indices to measure, in the order used to interpret
     *        {@code outcome}
     * @param outcome packed outcome bits, in {@code [0, 2^targetQubits.length)}
     * @return the collapsed pure state
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
        Complex[] collapsed = new Complex[dimension];
        Arrays.fill(collapsed, Complex.ZERO);
        double normSquared = 0.0;
        
        for (int rest = 0; rest < restDim; rest++) {
            int index = insertBits(rest, outcome, targets);
            collapsed[index] = amplitudes[index];
            normSquared += amplitudes[index].absSquare();
        }
        
        if (normSquared < 1e-15) {
            throw new IllegalStateException("Cannot collapse onto a measurement outcome with zero probability");
        }
        
        double scale = 1.0 / Math.sqrt(normSquared);
        for (int i = 0; i < dimension; i++) {
            if (collapsed[i].absSquare() > 0) {
                collapsed[i] = collapsed[i].scale(scale);
            }
        }
        
        return new StateVector(collapsed, numQubits);
    }
    
    /**
     * Projects the target qubits onto a supplied eigenvector and renormalizes.
     *
     * <p>The remaining amplitudes are contracted with the conjugated eigenvector into one
     * coefficient per configuration of the non-target qubits, and the result is rebuilt as
     * the tensor product of the eigenvector with those coefficients. After the operation
     * the target qubits are in the given eigenvector up to a global phase. The eigenvector
     * need not be normalized.</p>
     *
     * @param targetQubits qubit indices that are projected
     * @param eigenvector vector over the target qubits, with length
     *        {@code 2^targetQubits.length}
     * @return the collapsed pure state
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
        Complex[] environment = new Complex[restDim];
        Arrays.fill(environment, Complex.ZERO);
        
        for (int index = 0; index < dimension; index++) {
            int sub = extractTargetBits(index, targets);
            int rest = extractComplementBits(index, targets);
            Complex coefficient = eigenvector[sub].conjugate().scale(eigenScale);
            environment[rest] = environment[rest].add(coefficient.multiply(amplitudes[index]));
        }
        
        double normSquared = 0.0;
        for (Complex value : environment) {
            normSquared += value.absSquare();
        }
        if (normSquared < 1e-15) {
            throw new IllegalStateException("Cannot collapse onto an eigenvector with zero probability");
        }
        double environmentScale = eigenScale / Math.sqrt(normSquared);
        
        Complex[] collapsed = new Complex[dimension];
        Arrays.fill(collapsed, Complex.ZERO);
        for (int rest = 0; rest < restDim; rest++) {
            if (environment[rest].absSquare() < 1e-15) {
                continue;
            }
            for (int sub = 0; sub < targetDim; sub++) {
                collapsed[insertBits(rest, sub, targets)] =
                    eigenvector[sub].multiply(environment[rest]).scale(environmentScale);
            }
        }
        
        return new StateVector(collapsed, numQubits);
    }
    
    /**
     * Normalizes this state in place and returns it.
     *
     * <p>If the sum of squared magnitudes differs from {@code 1} by more than
     * {@code 1e-12}, every amplitude is divided by the norm. Otherwise the amplitudes are
     * left unchanged. This instance is modified.</p>
     *
     * @return this state
     */
    @Override
    public StateVector normalize() {
        double norm = 0.0;
        for (Complex amp : amplitudes) {
            norm += amp.absSquare();
        }
        
        if (Math.abs(norm - 1.0) > 1e-12) {
            double scale = 1.0 / Math.sqrt(norm);
            for (int i = 0; i < dimension; i++) {
                amplitudes[i] = amplitudes[i].scale(scale);
            }
        }
        
        return this;
    }
    
    /**
     * Returns {@code true}, because a state vector always describes a pure state.
     *
     * @return {@code true}
     */
    @Override
    public boolean isPure() {
        return true; // State vectors always represent pure states
    }
    
    /**
     * Returns {@code 1}, the purity of a pure state.
     *
     * @return {@code 1}
     */
    @Override
    public double getPurity() {
        return 1.0; // Pure states have purity 1
    }
    
    /**
     * Returns an independent copy of this state.
     *
     * @return a new state with copied amplitudes
     */
    @Override
    public StateVector copy() {
        Complex[] copiedAmplitudes = new Complex[dimension];
        for (int i = 0; i < dimension; i++) {
            copiedAmplitudes[i] = new Complex(amplitudes[i]);
        }
        return new StateVector(copiedAmplitudes, numQubits);
    }
    
    /**
     * Returns a copy of the amplitude array.
     *
     * @return a new array of length {@code 2^numQubits}
     */
    public Complex[] getAmplitudes() {
        return amplitudes.clone();
    }
    
    /**
     * Returns the Hilbert space dimension, {@code 2^numQubits}.
     *
     * @return the dimension
     */
    public int getDimension() {
        return dimension;
    }
    
    /**
     * Returns the Euclidean norm of the amplitude vector,
     * {@code sqrt(sum(|amplitude|^2))}.
     *
     * @return the norm
     */
    public double getNorm() {
        double norm = 0.0;
        for (Complex amp : amplitudes) {
            norm += amp.absSquare();
        }
        return Math.sqrt(norm);
    }
    
    /**
     * Returns the Hermitian inner product with another pure state,
     * {@code sum(conj(this[i]) * other[i])}.
     *
     * @param other state with the same number of qubits
     * @return the inner product
     * @throws IllegalArgumentException if the qubit counts differ
     */
    public Complex innerProduct(StateVector other) {
        if (this.numQubits != other.numQubits) {
            throw new IllegalArgumentException("State vectors must have same number of qubits");
        }
        
        Complex result = Complex.ZERO;
        for (int i = 0; i < dimension; i++) {
            result = result.add(this.amplitudes[i].conjugate().multiply(other.amplitudes[i]));
        }
        return result;
    }
    
    /**
     * Returns the density matrix of this pure state, with entries
     * {@code rho[i][j] = amplitude[i] * conj(amplitude[j])}.
     *
     * @return the density matrix
     */
    public DensityMatrix toDensityMatrix() {
        ComplexMatrix matrix = new ComplexMatrix(dimension, dimension);
        
        for (int i = 0; i < dimension; i++) {
            for (int j = 0; j < dimension; j++) {
                matrix.set(i, j, amplitudes[i].multiply(amplitudes[j].conjugate()));
            }
        }
        
        return new DensityMatrix(matrix, numQubits);
    }
    
    /**
     * Compares this state with another object.
     *
     * <p>Two state vectors are equal when they have the same number of qubits and every
     * amplitude pair is equal according to {@link Complex#equals(Object)}.</p>
     *
     * @param obj object to compare with
     * @return {@code true} if {@code obj} is a {@code StateVector} with equal amplitudes
     */
    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        
        StateVector other = (StateVector) obj;
        if (numQubits != other.numQubits) return false;
        if (dimension != other.dimension) return false;
        
        for (int i = 0; i < dimension; i++) {
            if (!amplitudes[i].equals(other.amplitudes[i])) {
                return false;
            }
        }
        
        return true;
    }
    
    /**
     * Returns a hash code derived from the qubit count, dimension, and exact amplitude
     * values.
     *
     * <p>Amplitude equality uses a tolerance, while hashing uses the raw component values,
     * so states that compare equal are not guaranteed to share a hash code.</p>
     *
     * @return the hash code
     */
    @Override
    public int hashCode() {
        int result = numQubits;
        result = 31 * result + dimension;
        result = 31 * result + Arrays.hashCode(amplitudes);
        return result;
    }
    
    /**
     * Returns a diagnostic listing of the first sixteen basis states with probability
     * above {@code 1e-6}.
     *
     * @return a multi-line description of this state
     */
    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("StateVector[")
          .append(numQubits)
          .append(" qubits, dim=")
          .append(dimension)
          .append("]:\n");
        
        boolean first = true;
        for (int i = 0; i < Math.min(dimension, 16); i++) { // Show first 16 amplitudes
            double prob = amplitudes[i].absSquare();
            if (prob > 1e-6) {
                if (!first) sb.append("\n");
                sb.append(String.format(Locale.ROOT, "  |%s>: %s (p=%.4f)",
                    Integer.toBinaryString(i),
                    amplitudes[i],
                    prob));
                first = false;
            }
        }
        
        if (dimension > 16) {
            sb.append("\n  ... and ").append(dimension - 16).append(" more basis states");
        }
        
        return sb.toString();
    }
}