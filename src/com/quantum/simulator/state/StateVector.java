package com.quantum.simulator.state;

import java.util.Locale;
import com.quantum.simulator.operations.QuantumOperation;
import com.quantum.simulator.operations.QuantumGate;
import com.quantum.simulator.math.Complex;
import com.quantum.simulator.math.ComplexMatrix;
import com.quantum.simulator.math.MatrixOperations;
import java.util.Arrays;

public class StateVector extends QuantumState {
    private final Complex[] amplitudes;
    private final int dimension;
    
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
    
    public static StateVector createZeroState(int numQubits) {
        Complex[] amplitudes = new Complex[1 << numQubits];
        Arrays.fill(amplitudes, Complex.ZERO);
        amplitudes[0] = Complex.ONE; // |0...0>
        return new StateVector(amplitudes, numQubits);
    }
    
    public static StateVector createPlusState(int numQubits) {
        int dim = 1 << numQubits;
        Complex[] amplitudes = new Complex[dim];
        Complex value = new Complex(1.0 / Math.sqrt(dim), 0);
        Arrays.fill(amplitudes, value);
        return new StateVector(amplitudes, numQubits);
    }
    
    public static StateVector fromComputationalBasis(int basisState, int numQubits) {
        if (basisState < 0 || basisState >= (1 << numQubits)) {
            throw new IllegalArgumentException("Basis state out of range");
        }
        
        Complex[] amplitudes = new Complex[1 << numQubits];
        Arrays.fill(amplitudes, Complex.ZERO);
        amplitudes[basisState] = Complex.ONE;
        return new StateVector(amplitudes, numQubits);
    }
    
    @Override
    public QuantumState applyOperation(QuantumOperation operation) {
        if (operation instanceof QuantumGate) {
            return applyGate((QuantumGate) operation);
        }
        throw new UnsupportedOperationException("Unsupported operation type: " + operation.getClass());
    }
    
    
    public StateVector applyGate(QuantumGate gate) {
        return (StateVector) applyKrausOperators(
            new ComplexMatrix[]{gate.getMatrix()}, gate.getMatrixQubitIndices());
    }
    
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
    
    @Override
    public Complex getAmplitude(int basisState) {
        if (basisState < 0 || basisState >= dimension) {
            throw new IndexOutOfBoundsException("Basis state index out of bounds");
        }
        return new Complex(amplitudes[basisState]);
    }
    
    @Override
    public double getProbability(int basisState) {
        return getAmplitude(basisState).absSquare();
    }
    
    @Override
    public double[] getProbabilities() {
        double[] probs = new double[dimension];
        for (int i = 0; i < dimension; i++) {
            probs[i] = amplitudes[i].absSquare();
        }
        return probs;
    }
    
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
    
    @Override
    public boolean isPure() {
        return true; // State vectors always represent pure states
    }
    
    @Override
    public double getPurity() {
        return 1.0; // Pure states have purity 1
    }
    
    @Override
    public StateVector copy() {
        Complex[] copiedAmplitudes = new Complex[dimension];
        for (int i = 0; i < dimension; i++) {
            copiedAmplitudes[i] = new Complex(amplitudes[i]);
        }
        return new StateVector(copiedAmplitudes, numQubits);
    }
    
    public Complex[] getAmplitudes() {
        return amplitudes.clone();
    }
    
    public int getDimension() {
        return dimension;
    }
    
    public double getNorm() {
        double norm = 0.0;
        for (Complex amp : amplitudes) {
            norm += amp.absSquare();
        }
        return Math.sqrt(norm);
    }
    
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
    
    public DensityMatrix toDensityMatrix() {
        ComplexMatrix matrix = new ComplexMatrix(dimension, dimension);
        
        for (int i = 0; i < dimension; i++) {
            for (int j = 0; j < dimension; j++) {
                matrix.set(i, j, amplitudes[i].multiply(amplitudes[j].conjugate()));
            }
        }
        
        return new DensityMatrix(matrix, numQubits);
    }
    
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
    
    @Override
    public int hashCode() {
        int result = numQubits;
        result = 31 * result + dimension;
        result = 31 * result + Arrays.hashCode(amplitudes);
        return result;
    }
    
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