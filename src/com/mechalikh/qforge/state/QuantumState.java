package com.mechalikh.qforge.state;

import com.mechalikh.qforge.operations.QuantumOperation;
import com.mechalikh.qforge.math.Complex;
import com.mechalikh.qforge.math.ComplexMatrix;
import com.mechalikh.qforge.math.MatrixOperations;
import java.util.Arrays;
import java.util.List;
import java.util.Random;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Abstract base class for a quantum state of a fixed number of qubits.
 *
 * <p>Two concrete backends exist: {@link StateVector} stores pure states and
 * {@link DensityMatrix} stores mixed states. Both use the same basis-state indexing: a
 * basis state is an integer in {@code [0, 2^numQubits)}, and the bit of qubit {@code q}
 * is bit {@code numQubits - 1 - q} of that integer. Qubit {@code 0} is therefore the most
 * significant bit and the last qubit is the least significant bit.</p>
 *
 * <p>This class implements the operations shared by both backends: Kraus operator
 * application, the partial trace, bit extraction and insertion for multi-qubit targets,
 * expectation values, and variances. The concrete subclasses define gate and noise
 * semantics where they differ.</p>
 */
public abstract class QuantumState {
    /** Number of qubits represented by this state. */
    protected final int numQubits;

    /** Metadata for the qubits, in index order from {@code 0} to {@code numQubits - 1}. */
    protected final List<Qubit> qubits;
    
    
    
    /**
     * Creates a state for the given number of qubits and initializes the qubit metadata.
     *
     * @param numQubits number of qubits, must be positive
     * @throws IllegalArgumentException if {@code numQubits} is not positive
     */
    protected QuantumState(int numQubits) {
        if (numQubits <= 0) {
            throw new IllegalArgumentException("Number of qubits must be positive");
        }
        this.numQubits = numQubits;
        this.qubits = Qubit.createQubits(numQubits);
    }
    
    /**
     * Applies a quantum operation and returns the resulting state.
     *
     * <p>Each implementation defines which operation types are supported. Unsupported
     * types generally cause an {@link UnsupportedOperationException}.</p>
     *
     * @param operation operation to apply
     * @return the resulting state, which may be a new instance
     */
    public abstract QuantumState applyOperation(QuantumOperation operation);
    
    /**
     * Returns the backend representation of one computational basis state.
     *
     * <p>A {@link StateVector} returns the complex coefficient. A {@link DensityMatrix}
     * returns the square root of the corresponding diagonal entry.</p>
     *
     * @param basisState basis-state index in {@code [0, 2^numQubits)}
     * @return the amplitude, or the density-matrix analogue of it
     */
    public abstract Complex getAmplitude(int basisState);
    
    /**
     * Returns the population of one computational basis state.
     *
     * @param basisState basis-state index in {@code [0, 2^numQubits)}
     * @return the probability of measuring this basis state
     */
    public abstract double getProbability(int basisState);
    
    /**
     * Returns the populations of all computational basis states.
     *
     * @return an array of length {@code 2^numQubits}, indexed by basis-state index
     */
    public abstract double[] getProbabilities();
    
    /**
     * Returns the tensor product of this state and another state of the same backend.
     *
     * <p>The qubits of this state keep their positions {@code 0} to {@code numQubits - 1}
     * in the result, and the qubits of {@code other} follow in order. The result therefore
     * has {@code this.numQubits + other.numQubits} qubits.</p>
     *
     * @param other right-hand factor, must be the same concrete backend
     * @return the combined state
     * @throws IllegalArgumentException if {@code other} has a different backend type
     */
    public abstract QuantumState tensorProduct(QuantumState other);
    
    /**
     * Returns a normalized version of this state.
     *
     * <p>For a pure state this means unit Euclidean norm of the amplitudes; for a mixed
     * state this means unit trace. The returned instance may be this state or a new one,
     * depending on the implementation.</p>
     *
     * @return a normalized state
     */
    public abstract QuantumState normalize();
    
    /**
     * Tests whether this state is pure.
     *
     * @return {@code true} for pure states
     */
    public abstract boolean isPure();
    
    /**
     * Returns the purity of this state.
     *
     * @return {@code 1} for a pure state, and {@code tr(rho^2)} for a density matrix
     */
    public abstract double getPurity();
    
    /**
     * Returns an independent copy of this state.
     *
     * @return a new state with the same amplitudes or matrix
     */
    public abstract QuantumState copy();
    
    /**
     * Projects the target qubits onto one computational basis outcome and renormalizes.
     *
     * <p>Bit {@code j} of {@code outcome}, counted from the most significant bit, is
     * placed on qubit {@code targetQubits[j]}. After the projection the state has unit
     * norm (pure) or unit trace (mixed), and all amplitude outside the selected subspace
     * is zero.</p>
     *
     * @param targetQubits qubit indices to measure, in the order used to interpret
     *        {@code outcome}
     * @param outcome packed outcome bits, in {@code [0, 2^targetQubits.length)}
     * @return the collapsed state
     * @throws IllegalArgumentException if the target array is null, empty, contains a
     *         duplicate, contains an out-of-range index, or if {@code outcome} is out of
     *         range
     * @throws IllegalStateException if the outcome has probability below {@code 1e-15}
     */
    public abstract QuantumState collapseToBasisState(int[] targetQubits, int outcome);
    
    /**
     * Projects the target qubits onto a supplied eigenvector and renormalizes.
     *
     * <p>The eigenvector need not be normalized. Bit {@code j} of an eigenvector index,
     * counted from the most significant bit, corresponds to qubit
     * {@code targetQubits[j]}. After the operation the target qubits are in the given
     * eigenvector up to a global phase.</p>
     *
     * @param targetQubits qubit indices that are projected
     * @param eigenvector vector over the target qubits, with length
     *        {@code 2^targetQubits.length}
     * @return the collapsed state
     * @throws IllegalArgumentException if the target array is invalid, the eigenvector
     *         length does not match the target count, or the eigenvector is zero
     * @throws IllegalStateException if the eigenvector has probability below
     *         {@code 1e-15}
     */
    public abstract QuantumState collapseToEigenvector(int[] targetQubits, Complex[] eigenvector);
    
    /**
     * Applies a set of Kraus operators to the target qubits, using a thread-local random
     * source for the pure-state trajectory.
     *
     * <p>This overload is equivalent to calling
     * {@link #applyKrausOperators(ComplexMatrix[], int[], Random)} with
     * {@link ThreadLocalRandom#current()}.</p>
     *
     * @param krausOperators operators, each of size {@code 2^k} by {@code 2^k} for
     *        {@code k} targets
     * @param targetQubits qubit indices the operators act on, in matrix index order
     * @return the resulting state
     * @throws IllegalArgumentException if the operators or targets are invalid
     * @throws IllegalStateException if the operators produce a zero-norm pure state
     */
    public QuantumState applyKrausOperators(ComplexMatrix[] krausOperators, int[] targetQubits) {
        return applyKrausOperators(krausOperators, targetQubits, ThreadLocalRandom.current());
    }
    
    /**
     * Applies a set of Kraus operators to the target qubits.
     *
     * <p>The backends differ in how the set is combined. A {@link DensityMatrix} applies
     * the deterministic channel {@code rho -> sum(K * rho * K^dagger)} over every
     * operator. A {@link StateVector} draws exactly one operator, with probability
     * proportional to the squared norm of its image, and returns that normalized image, so
     * repeated calls form sampled trajectories. The supplied random source is used only by
     * the state-vector path.</p>
     *
     * <p>Bit {@code j} of an operator index, counted from the most significant bit,
     * corresponds to qubit {@code targetQubits[j]}. The input state is not modified.</p>
     *
     * @param krausOperators operators, each of size {@code 2^k} by {@code 2^k} for
     *        {@code k} targets; must not be null or empty
     * @param targetQubits qubit indices the operators act on, in matrix index order
     * @param random random source used to select a trajectory for a pure state
     * @return the resulting state
     * @throws IllegalArgumentException if the array is null or empty, an operator has the
     *         wrong size, the targets are invalid, or, for a density matrix, the resulting
     *         matrix is not Hermitian or does not have unit trace
     * @throws IllegalStateException if the pure-state images all have norm below
     *         {@code 1e-15}
     * @throws UnsupportedOperationException if the concrete state type is neither backend
     */
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
    
    /**
     * Validates a list of target qubits and returns a defensive copy.
     *
     * @param targetQubits qubit indices to validate
     * @return a copy of the validated array
     * @throws IllegalArgumentException if the array is null or empty, or contains an index
     *         outside {@code [0, numQubits)} or a duplicate index
     */
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
    
    /**
     * Packs the bits of the target qubits into an integer.
     *
     * <p>The result has one bit per target. The bit of {@code targetQubits[0]} becomes the
     * most significant bit of the result, and the bit of the last target becomes the least
     * significant bit. The bit of qubit {@code q} in {@code basisIndex} is bit
     * {@code numQubits - 1 - q}.</p>
     *
     * @param basisIndex basis-state index to read
     * @param targetQubits qubit indices to extract, in packing order
     * @return the packed target bits
     */
    protected int extractTargetBits(int basisIndex, int[] targetQubits) {
        int result = 0;
        for (int j = 0; j < targetQubits.length; j++) {
            int bit = (basisIndex >> (numQubits - 1 - targetQubits[j])) & 1;
            result |= bit << (targetQubits.length - 1 - j);
        }
        return result;
    }
    
    /**
     * Packs the bits of all qubits that are not targets into an integer.
     *
     * <p>Non-target qubits are read in ascending qubit order, and the lowest-index
     * non-target qubit becomes the most significant bit of the result. The result has
     * {@code numQubits - targetQubits.length} bits.</p>
     *
     * @param basisIndex basis-state index to read
     * @param targetQubits qubit indices to omit
     * @return the packed bits of the non-target qubits
     */
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
    
    /**
     * Builds a basis-state index from separate target and non-target bits.
     *
     * <p>This method reverses the combination of {@link #extractTargetBits(int, int[])}
     * and {@link #extractComplementBits(int, int[])}. Bits of {@code targetBits} are
     * written to the target positions, most significant bit first, so the most significant
     * bit of {@code targetBits} goes to {@code targetQubits[0]}. Bits of {@code restBits}
     * fill the remaining positions in ascending qubit order, most significant bit
     * first.</p>
     *
     * @param restBits packed bits for the non-target qubits
     * @param targetBits packed bits for the target qubits
     * @param targetQubits qubit indices that receive the target bits
     * @return the combined basis-state index
     */
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
    
    /**
     * Traces out every qubit that is not listed and returns the reduced state.
     *
     * <p>The result is always a {@link DensityMatrix} over the kept qubits, even when this
     * state is a {@link StateVector}. The kept qubits are ordered as given: bit position
     * {@code 0} (the most significant bit) of the reduced index corresponds to
     * {@code qubitsToKeep[0]}. For a pure state the reduced entry is
     * {@code sum over rest of psi[rest, a] * conj(psi[rest, b])}; for a density matrix it
     * is the sum of the corresponding entries over the traced-out configurations.</p>
     *
     * @param qubitsToKeep qubit indices to keep, in the order used for the reduced state
     * @return the reduced density matrix over the kept qubits
     * @throws IllegalArgumentException if the array is null or empty, or contains an
     *         out-of-range or duplicate index
     * @throws UnsupportedOperationException if the concrete state type is neither backend
     */
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
    
    /**
     * Returns the number of qubits.
     *
     * @return the qubit count
     */
    public int getNumQubits() {
        return numQubits;
    }
    
    /**
     * Returns an unmodifiable list of the qubit metadata, in index order.
     *
     * @return an unmodifiable list of qubits
     */
    public List<Qubit> getQubits() {
        return List.copyOf(qubits);
    }
    
    /**
     * Returns the metadata of one qubit.
     *
     * @param index qubit index
     * @return the qubit at {@code index}
     * @throws IndexOutOfBoundsException if {@code index} is outside
     *         {@code [0, numQubits)}
     */
    public Qubit getQubit(int index) {
        if (index < 0 || index >= numQubits) {
            throw new IndexOutOfBoundsException("Qubit index out of bounds: " + index);
        }
        return qubits.get(index);
    }
    
    /**
     * Returns the expectation value of an observable.
     *
     * <p>A {@link StateVector} evaluates {@code <psi|O|psi>}; a {@link DensityMatrix}
     * evaluates {@code tr(O * rho)}. In both cases only the real part is returned, and the
     * observable is not checked for Hermiticity, so a non-Hermitian observable yields the
     * real part of a complex value.</p>
     *
     * @param observable square matrix of dimension {@code 2^numQubits}
     * @return the real part of the expectation value
     * @throws IllegalArgumentException if the observable is not square or has the wrong
     *         dimension
     * @throws UnsupportedOperationException if the concrete state type is neither backend
     */
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
    
    /**
     * Returns the variance of an observable, computed as {@code <O^2> - <O>^2}.
     *
     * @param observable square matrix of dimension {@code 2^numQubits}
     * @return the variance
     * @throws IllegalArgumentException if the observable is not square or has the wrong
     *         dimension
     */
    public double getVariance(ComplexMatrix observable) {
        // Variance = <O²> - <O>²
        double expectation = getExpectationValue(observable);
        
        // Compute O²
        ComplexMatrix observableSquared = MatrixOperations.multiply(observable, observable);
        double expectationSquared = getExpectationValue(observableSquared);
        
        return expectationSquared - expectation * expectation;
    }
     
    
    /**
     * Compares this state with another object. The comparison rules are defined by the
     * concrete backend.
     *
     * @param obj object to compare with
     * @return {@code true} if {@code obj} represents the same state
     */
    @Override
    public abstract boolean equals(Object obj);
    
    /**
     * Returns a hash code consistent with {@link #equals(Object)} for the concrete
     * backend.
     *
     * @return the hash code
     */
    @Override
    public abstract int hashCode();
    
    /**
     * Returns a diagnostic description of this state.
     *
     * @return a description of the state and its dominant entries
     */
    @Override
    public abstract String toString();
}