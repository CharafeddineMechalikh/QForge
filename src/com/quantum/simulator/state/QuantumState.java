package com.quantum.simulator.state;

import com.quantum.simulator.operations.QuantumOperation;
import com.quantum.simulator.math.Complex;
import com.quantum.simulator.math.ComplexMatrix;
import com.quantum.simulator.math.MatrixOperations;
import java.util.Arrays;
import java.util.List;
import java.util.Random;
import java.util.concurrent.ThreadLocalRandom;

public abstract class QuantumState {
    protected final int numQubits;
    protected final List<Qubit> qubits;
    
    
    
    protected QuantumState(int numQubits) {
        if (numQubits <= 0) {
            throw new IllegalArgumentException("Number of qubits must be positive");
        }
        this.numQubits = numQubits;
        this.qubits = Qubit.createQubits(numQubits);
    }
    
    public abstract QuantumState applyOperation(QuantumOperation operation);
    
    public abstract Complex getAmplitude(int basisState);
    
    public abstract double getProbability(int basisState);
    
    public abstract double[] getProbabilities();
    
    public abstract QuantumState tensorProduct(QuantumState other);
    
    public abstract QuantumState normalize();
    
    public abstract boolean isPure();
    
    public abstract double getPurity();
    
    public abstract QuantumState copy();
    
    public abstract QuantumState collapseToBasisState(int[] targetQubits, int outcome);
    
    public abstract QuantumState collapseToEigenvector(int[] targetQubits, Complex[] eigenvector);
    
    public QuantumState applyKrausOperators(ComplexMatrix[] krausOperators, int[] targetQubits) {
        return applyKrausOperators(krausOperators, targetQubits, ThreadLocalRandom.current());
    }
    
    public QuantumState applyKrausOperators(ComplexMatrix[] krausOperators, int[] targetQubits, Random random) {
        int[] targets = validateTargetQubits(targetQubits);
        int targetDim = 1 << targets.length;
        if (krausOperators == null || krausOperators.length == 0) {
            throw new IllegalArgumentException("At least one Kraus operator is required");
        }
        for (ComplexMatrix operator : krausOperators) {
            if (operator.rows() != targetDim || operator.cols() != targetDim) {
                throw new IllegalArgumentException(
                    "Kraus operator dimension " + operator.rows() + "x" + operator.cols()
                        + " does not match " + targets.length + " target qubits");
            }
        }
        
        int restDim = 1 << (numQubits - targets.length);
        
        if (this instanceof StateVector) {
            Complex[] amplitudes = ((StateVector) this).getAmplitudes();
            Complex[][] candidates = new Complex[krausOperators.length][];
            double[] probabilities = new double[krausOperators.length];
            
            for (int i = 0; i < krausOperators.length; i++) {
                ComplexMatrix operator = krausOperators[i];
                Complex[] result = new Complex[1 << numQubits];
                Arrays.fill(result, Complex.ZERO);
                
                for (int rest = 0; rest < restDim; rest++) {
                    Complex[] input = new Complex[targetDim];
                    for (int a = 0; a < targetDim; a++) {
                        input[a] = amplitudes[insertBits(rest, a, targets)];
                    }
                    for (int out = 0; out < targetDim; out++) {
                        Complex sum = Complex.ZERO;
                        for (int in = 0; in < targetDim; in++) {
                            sum = sum.add(operator.get(out, in).multiply(input[in]));
                        }
                        result[insertBits(rest, out, targets)] = sum;
                    }
                }
                
                double probability = 0.0;
                for (Complex amplitude : result) {
                    probability += amplitude.absSquare();
                }
                candidates[i] = result;
                probabilities[i] = probability;
            }
            
            double total = 0.0;
            for (double probability : probabilities) {
                total += probability;
            }
            if (total < 1e-15) {
                throw new IllegalStateException("Kraus operators produced a zero-norm state");
            }
            
            double draw = random.nextDouble() * total;
            double cumulative = 0.0;
            int chosen = probabilities.length - 1;
            for (int i = 0; i < probabilities.length; i++) {
                cumulative += probabilities[i];
                if (draw <= cumulative || i == probabilities.length - 1) {
                    chosen = i;
                    break;
                }
            }
            
            double scale = 1.0 / Math.sqrt(probabilities[chosen]);
            Complex[] normalized = candidates[chosen];
            for (int i = 0; i < normalized.length; i++) {
                normalized[i] = normalized[i].scale(scale);
            }
            return new StateVector(normalized, numQubits);
        }
        
        if (this instanceof DensityMatrix) {
            ComplexMatrix rho = ((DensityMatrix) this).getMatrix();
            ComplexMatrix result = new ComplexMatrix(1 << numQubits, 1 << numQubits);
            
            for (ComplexMatrix operator : krausOperators) {
                ComplexMatrix operatorDag = operator.adjoint();
                for (int restRow = 0; restRow < restDim; restRow++) {
                    for (int restCol = 0; restCol < restDim; restCol++) {
                        ComplexMatrix block = new ComplexMatrix(targetDim, targetDim);
                        for (int a = 0; a < targetDim; a++) {
                            for (int b = 0; b < targetDim; b++) {
                                block.set(a, b, rho.get(
                                    insertBits(restRow, a, targets),
                                    insertBits(restCol, b, targets)));
                            }
                        }
                        
                        ComplexMatrix transformed = operator.multiply(block).multiply(operatorDag);
                        for (int a = 0; a < targetDim; a++) {
                            for (int b = 0; b < targetDim; b++) {
                                int row = insertBits(restRow, a, targets);
                                int col = insertBits(restCol, b, targets);
                                result.set(row, col, result.get(row, col).add(transformed.get(a, b)));
                            }
                        }
                    }
                }
            }
            
            return new DensityMatrix(result, numQubits);
        }
        
        throw new UnsupportedOperationException("Kraus operators not implemented for " + getClass());
    }
    
    protected int[] validateTargetQubits(int[] targetQubits) {
        if (targetQubits == null || targetQubits.length == 0) {
            throw new IllegalArgumentException("At least one target qubit is required");
        }
        int[] targets = targetQubits.clone();
        boolean[] seen = new boolean[numQubits];
        for (int target : targets) {
            if (target < 0 || target >= numQubits) {
                throw new IllegalArgumentException("Target qubit index out of bounds: " + target);
            }
            if (seen[target]) {
                throw new IllegalArgumentException("Duplicate target qubit index: " + target);
            }
            seen[target] = true;
        }
        return targets;
    }
    
    protected int extractTargetBits(int basisIndex, int[] targetQubits) {
        int result = 0;
        for (int j = 0; j < targetQubits.length; j++) {
            int bit = (basisIndex >> (numQubits - 1 - targetQubits[j])) & 1;
            result |= bit << (targetQubits.length - 1 - j);
        }
        return result;
    }
    
    protected int extractComplementBits(int basisIndex, int[] targetQubits) {
        int result = 0;
        int position = 0;
        for (int q = 0; q < numQubits; q++) {
            boolean isTarget = false;
            for (int target : targetQubits) {
                if (target == q) {
                    isTarget = true;
                    break;
                }
            }
            if (!isTarget) {
                int bit = (basisIndex >> (numQubits - 1 - q)) & 1;
                result |= bit << (numQubits - targetQubits.length - 1 - position);
                position++;
            }
        }
        return result;
    }
    
    protected int insertBits(int restBits, int targetBits, int[] targetQubits) {
        int result = 0;
        int restPosition = 0;
        int restCount = numQubits - targetQubits.length;
        for (int q = 0; q < numQubits; q++) {
            int targetPosition = -1;
            for (int j = 0; j < targetQubits.length; j++) {
                if (targetQubits[j] == q) {
                    targetPosition = j;
                    break;
                }
            }
            int bit;
            if (targetPosition >= 0) {
                bit = (targetBits >> (targetQubits.length - 1 - targetPosition)) & 1;
            } else {
                bit = (restBits >> (restCount - 1 - restPosition)) & 1;
                restPosition++;
            }
            result |= bit << (numQubits - 1 - q);
        }
        return result;
    }
    
    public QuantumState partialTrace(int[] qubitsToKeep) {
        int[] keep = validateTargetQubits(qubitsToKeep);
        int keptDim = 1 << keep.length;
        int restDim = 1 << (numQubits - keep.length);
        ComplexMatrix reduced = new ComplexMatrix(keptDim, keptDim);
        
        if (this instanceof StateVector) {
            Complex[] amplitudes = ((StateVector) this).getAmplitudes();
            for (int a = 0; a < keptDim; a++) {
                for (int b = 0; b < keptDim; b++) {
                    Complex sum = Complex.ZERO;
                    for (int rest = 0; rest < restDim; rest++) {
                        sum = sum.add(amplitudes[insertBits(rest, a, keep)]
                            .multiply(amplitudes[insertBits(rest, b, keep)].conjugate()));
                    }
                    reduced.set(a, b, sum);
                }
            }
        } else if (this instanceof DensityMatrix) {
            ComplexMatrix rho = ((DensityMatrix) this).getMatrix();
            for (int a = 0; a < keptDim; a++) {
                for (int b = 0; b < keptDim; b++) {
                    Complex sum = Complex.ZERO;
                    for (int rest = 0; rest < restDim; rest++) {
                        sum = sum.add(rho.get(insertBits(rest, a, keep), insertBits(rest, b, keep)));
                    }
                    reduced.set(a, b, sum);
                }
            }
        } else {
            throw new UnsupportedOperationException("Partial trace not implemented for " + getClass());
        }
        
        return new DensityMatrix(reduced, keep.length);
    }
    
    public int getNumQubits() {
        return numQubits;
    }
    
    public List<Qubit> getQubits() {
        return List.copyOf(qubits);
    }
    
    public Qubit getQubit(int index) {
        if (index < 0 || index >= numQubits) {
            throw new IndexOutOfBoundsException("Qubit index out of bounds: " + index);
        }
        return qubits.get(index);
    }
    
    public double getExpectationValue(ComplexMatrix observable) {
        // Default implementation that subclasses should override
        if (!observable.isSquare() || observable.rows() != (1 << numQubits)) {
            throw new IllegalArgumentException("Observable dimension mismatch");
        }
        
        if (this instanceof StateVector) {
            // For state vector: <ψ|O|ψ>
            StateVector stateVector = (StateVector) this;
            Complex[] amplitudes = stateVector.getAmplitudes();
            Complex expectation = Complex.ZERO;
            
            for (int i = 0; i < amplitudes.length; i++) {
                Complex rowSum = Complex.ZERO;
                for (int j = 0; j < amplitudes.length; j++) {
                    rowSum = rowSum.add(observable.get(i, j).multiply(amplitudes[j]));
                }
                expectation = expectation.add(amplitudes[i].conjugate().multiply(rowSum));
            }
            
            return expectation.real();
        } else if (this instanceof DensityMatrix) {
            // For density matrix: tr(Oρ)
            DensityMatrix densityMatrix = (DensityMatrix) this;
            ComplexMatrix rho = densityMatrix.getMatrix();
            ComplexMatrix product = MatrixOperations.multiply(observable, rho);
            Complex trace = MatrixOperations.trace(product);
            return trace.real();
        }
        
        throw new UnsupportedOperationException("Expectation value not implemented for this state type");
    }
    
    public double getVariance(ComplexMatrix observable) {
        // Variance = <O²> - <O>²
        double expectation = getExpectationValue(observable);
        
        // Compute O²
        ComplexMatrix observableSquared = MatrixOperations.multiply(observable, observable);
        double expectationSquared = getExpectationValue(observableSquared);
        
        return expectationSquared - expectation * expectation;
    }
     
    
    @Override
    public abstract boolean equals(Object obj);
    
    @Override
    public abstract int hashCode();
    
    @Override
    public abstract String toString();
}