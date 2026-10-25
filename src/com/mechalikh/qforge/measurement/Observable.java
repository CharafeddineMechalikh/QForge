package com.mechalikh.qforge.measurement;

import java.util.Locale;
import com.mechalikh.qforge.state.DensityMatrix;
import com.mechalikh.qforge.state.QuantumState;
import com.mechalikh.qforge.state.Qubit;
import com.mechalikh.qforge.math.Complex;
import com.mechalikh.qforge.math.ComplexMatrix;
import com.mechalikh.qforge.math.MatrixOperations;
import java.util.Arrays;
import java.util.Random;

/**
 * A Hermitian measurement observable acting on a fixed list of target qubits.
 *
 * <p>An observable is a square Hermitian matrix whose dimension must be {@code 2^n} for its
 * {@code n} target qubits. Construction rejects non-Hermitian matrices and dimension mismatches,
 * so every instance represents a valid observable with real eigenvalues.</p>
 *
 * <p>Two measurement paths are supported and they differ in how outcomes are indexed:</p>
 * <ul>
 *   <li>If the operator is diagonal, it is treated as a computational basis measurement. The
 *       probability of an outcome is the marginal probability of the corresponding bit pattern on
 *       the target qubits, computed from the diagonal of the state, and outcome {@code i} has the
 *       eigenvalue {@code operator[i][i]}. Only the real part of each diagonal entry is used, so
 *       the imaginary part of a diagonal entry is ignored.</li>
 *   <li>Otherwise the target qubits are traced out of the state, the resulting density matrix is
 *       projected onto each eigenvector of the operator, and the resulting probabilities are
 *       clamped at zero and renormalized. Outcome {@code i} corresponds to eigenvector {@code i}
 *       and its eigenvalue, both taken from the operator's eigen decomposition, which orders
 *       eigenvalues in ascending order.</li>
 * </ul>
 *
 * <p>Bit order follows {@link Qubit#getIndex()} with qubit {@code 0} as the most significant bit.
 * In the computational basis path the first target in the array becomes the most significant bit
 * of the outcome integer, and the last target becomes the least significant bit.</p>
 *
 * <p>{@link #collapse(QuantumState, int)} performs the destructive counterpart: it projects the
 * state onto the computational basis state or onto the eigenvector matching the given outcome.
 * The measurements themselves never modify the state.</p>
 *
 * <p>The {@link ObservableType} stored with an observable is a descriptive label. Measurement
 * behaviour is derived from the operator matrix, not from the type.</p>
 */
public class Observable {
    private final String name;
    private final ComplexMatrix operator;
    private final Qubit[] targetQubits;
    private final ObservableType type;
    
    /**
     * Creates an observable from a Hermitian matrix and its target qubits.
     *
     * <p>The target array is copied. The operator must be Hermitian and its size must equal
     * {@code 2^n}, where {@code n} is the number of targets. Repeated target indices are not
     * checked here; they are rejected later by the non computational basis probability path when
     * the state is asked to trace out the targets.</p>
     *
     * @param name the display name of the observable
     * @param operator the Hermitian operator, of size {@code 2^n} by {@code 2^n}
     * @param targetQubits the qubits the operator acts on, at least one, in significance order
     * @param type the descriptive observable type
     * @throws IllegalArgumentException if {@code operator} is not Hermitian, or its dimensions do
     *         not match the number of target qubits
     * @throws NullPointerException if {@code operator} or {@code targetQubits} is {@code null}
     */
    public Observable(String name, ComplexMatrix operator, Qubit[] targetQubits, ObservableType type) {
        this.name = name;
        this.operator = operator;
        this.targetQubits = targetQubits.clone();
        this.type = type;
        validateObservable();
    }
    
    private void validateObservable() {
        if (!operator.isHermitian()) {
            throw new IllegalArgumentException("Observable operator must be Hermitian");
        }
        
        int expectedDim = 1 << targetQubits.length;
        if (operator.rows() != expectedDim || operator.cols() != expectedDim) {
            throw new IllegalArgumentException(
                String.format(Locale.ROOT, "Operator dimension %dx%d does not match %d target qubits (expected %dx%d)",
                    operator.rows(), operator.cols(), targetQubits.length, expectedDim, expectedDim)
            );
        }
    }
    
    /**
     * Returns the exact expectation value of this observable on the given state.
     *
     * <p>This is {@code sum(p_i * lambda_i)} over all outcomes, using the probabilities from
     * {@link #getOutcomeProbabilities(QuantumState)} and the eigenvalues from
     * {@link #getOutcomeEigenvalues()}. The state is not collapsed.</p>
     *
     * @param state the state to evaluate
     * @return the expectation value of the observable
     * @throws IllegalArgumentException if a target qubit is outside the state
     * @throws IllegalStateException if the observable yields no measurement probability
     */
    public double measure(QuantumState state) {
        double[] probabilities = getOutcomeProbabilities(state);
        double[] eigenvalues = getOutcomeEigenvalues();
        double expectation = 0.0;
        for (int i = 0; i < probabilities.length; i++) {
            expectation += probabilities[i] * eigenvalues[i];
        }
        return expectation;
    }
    
    /**
     * Samples this observable {@code shots} times and packages the results.
     *
     * <p>The exact outcome probabilities are computed once and stored in the result, while the
     * counts come from independent draws from that distribution. No collapse is performed. The
     * result carries this observable's name, targets and eigenvalues.</p>
     *
     * @param state the state to measure, which is left unchanged
     * @param shots the number of independent draws
     * @return the sampled result with exact probabilities and sampled counts
     * @throws IllegalArgumentException if a target qubit is outside the state
     * @throws IllegalStateException if the observable yields no measurement probability
     */
    public MeasurementResult measureMultiple(QuantumState state, int shots) {
        double[] probabilities = getOutcomeProbabilities(state);
        int[] outcomes = sampleMeasurementOutcomes(probabilities, shots);
        return new MeasurementResult(name, targetQubits, outcomes, probabilities, getOutcomeEigenvalues());
    }
    
    /**
     * Computes the probability of each outcome of this observable on the given state.
     *
     * <p>For a diagonal operator the state's computational basis probabilities are summed over the
     * non target qubits, so each entry is the marginal probability of the matching target bit
     * pattern. For a general operator the target qubits are traced out into a
     * {@link DensityMatrix}, and entry {@code i} is {@code <v_i|rho|v_i>} for eigenvector
     * {@code v_i} of the operator.</p>
     *
     * <p>Because the computed values can carry small negative parts from floating point
     * arithmetic, each value is replaced by zero when negative, and the array is then divided by
     * its total, so the returned array sums to one.</p>
     *
     * <p>The state is only read. A {@link com.mechalikh.qforge.state.StateVector} is converted to a density matrix by the
     * partial trace, so the method works for both backends.</p>
     *
     * @param state the state to inspect, which is left unchanged
     * @return a new array of {@code 2^n} outcome probabilities that sums to one
     * @throws IllegalArgumentException if a target qubit is outside the state, or if a target
     *         index is repeated in the non computational basis path
     * @throws IllegalStateException if all computed probabilities are zero
     */
    public double[] getOutcomeProbabilities(QuantumState state) {
        int[] targets = targetIndices();
        int numStateQubits = state.getNumQubits();
        for (int target : targets) {
            if (target < 0 || target >= numStateQubits) {
                throw new IllegalArgumentException("Observable targets qubit " + target
                    + " outside a " + numStateQubits + "-qubit state");
            }
        }
        
        int numOutcomes = 1 << targets.length;
        double[] probabilities = new double[numOutcomes];
        
        if (isComputationalBasis()) {
            int stateDimension = 1 << numStateQubits;
            for (int basisState = 0; basisState < stateDimension; basisState++) {
                int outcome = extractTargetBits(basisState, targets, numStateQubits);
                probabilities[outcome] += state.getProbability(basisState);
            }
        } else {
            DensityMatrix reduced = (DensityMatrix) state.partialTrace(targets);
            ComplexMatrix reducedMatrix = reduced.getMatrix();
            ComplexMatrix eigenvectors = operator.eigenDecomposition()[1];
            for (int i = 0; i < numOutcomes; i++) {
                Complex value = Complex.ZERO;
                for (int row = 0; row < numOutcomes; row++) {
                    for (int col = 0; col < numOutcomes; col++) {
                        value = value.add(eigenvectors.get(row, i).conjugate()
                            .multiply(reducedMatrix.get(row, col))
                            .multiply(eigenvectors.get(col, i)));
                    }
                }
                probabilities[i] = Math.max(0.0, value.real());
            }
        }
        
        normalizeProbabilities(probabilities);
        return probabilities;
    }
    
    /**
     * Returns the eigenvalue attached to each outcome of this observable.
     *
     * <p>For a diagonal operator the eigenvalues are the real parts of the diagonal entries, in
     * outcome order. Otherwise they are the eigenvalues produced by the operator's eigen
     * decomposition, which sorts them in ascending order; outcome {@code i} then refers to that
     * sorted position, matching the eigenvector order used for probabilities and collapse.</p>
     *
     * @return a new array of {@code 2^n} eigenvalues, indexed by outcome
     * @throws IllegalStateException if the eigen decomposition does not return exactly
     *         {@code 2^n} eigenvalues
     */
    public double[] getOutcomeEigenvalues() {
        int numOutcomes = 1 << targetQubits.length;
        if (isComputationalBasis()) {
            double[] eigenvalues = new double[numOutcomes];
            for (int i = 0; i < numOutcomes; i++) {
                eigenvalues[i] = operator.get(i, i).real();
            }
            return eigenvalues;
        }
        double[] eigenvalues = operator.eigenvalues();
        if (eigenvalues.length != numOutcomes) {
            throw new IllegalStateException("Eigenvalue count does not match observable dimension");
        }
        return eigenvalues;
    }
    
    /**
     * Projects the state onto the eigenspace selected by one outcome.
     *
     * <p>For a diagonal operator the state is projected onto the computational basis state whose
     * target bits equal {@code outcome}. Otherwise it is projected onto the eigenvector stored in
     * column {@code outcome} of the operator's eigenvector matrix, matching the ordering used by
     * {@link #getOutcomeProbabilities(QuantumState)}.</p>
     *
     * <p>The result is a new state; the supplied state is not modified.</p>
     *
     * @param state the state to collapse
     * @param outcome the outcome index to collapse onto
     * @return the normalized post measurement state
     * @throws IllegalArgumentException if {@code outcome} is outside the outcome range, if a
     *         target qubit is outside the state, or if the target list repeats an index
     * @throws IllegalStateException if the selected outcome has zero probability in the state
     */
    public QuantumState collapse(QuantumState state, int outcome) {
        int[] targets = targetIndices();
        int numOutcomes = 1 << targets.length;
        if (outcome < 0 || outcome >= numOutcomes) {
            throw new IllegalArgumentException("Measurement outcome out of range: " + outcome);
        }
        
        if (isComputationalBasis()) {
            return state.collapseToBasisState(targets, outcome);
        }
        
        ComplexMatrix eigenvectors = operator.eigenDecomposition()[1];
        Complex[] eigenvector = new Complex[numOutcomes];
        for (int row = 0; row < numOutcomes; row++) {
            eigenvector[row] = eigenvectors.get(row, outcome);
        }
        return state.collapseToEigenvector(targets, eigenvector);
    }
    
    /**
     * Draws a single outcome from the distribution of this observable.
     *
     * @param state the state to measure, which is left unchanged
     * @return one sampled outcome index
     * @throws IllegalArgumentException if a target qubit is outside the state
     * @throws IllegalStateException if the observable yields no measurement probability
     */
    public int sampleOutcome(QuantumState state) {
        return sampleMeasurementOutcomes(getOutcomeProbabilities(state), 1)[0];
    }
    
    /**
     * Draws {@code shots} outcomes from a supplied probability distribution.
     *
     * <p>This is the sampling helper used by
     * {@link MeasurementOperator#measureAndCollapse(QuantumState)}. The distribution is expected to
     * sum to one but is not validated; selection walks the array accumulating probabilities and
     * stops at the first index whose cumulative value reaches the drawn uniform number, with a
     * tolerance of {@code 1e-12}. If rounding prevents any index from being selected, the entry
     * keeps its default value of zero.</p>
     *
     * @param probabilities the probability of each outcome, indexed by outcome
     * @param shots the number of draws
     * @return an array of {@code shots} outcome indices
     * @throws NullPointerException if {@code probabilities} is {@code null}
     * @throws NegativeArraySizeException if {@code shots} is negative
     */
    int[] sampleOutcomes(double[] probabilities, int shots) {
        return sampleMeasurementOutcomes(probabilities, shots);
    }
    
    private int[] sampleMeasurementOutcomes(double[] probabilities, int shots) {
        Random random = new Random();
        int[] outcomes = new int[shots];
        for (int i = 0; i < shots; i++) {
            double rand = random.nextDouble();
            double cumulative = 0.0;
            for (int outcome = 0; outcome < probabilities.length; outcome++) {
                cumulative += probabilities[outcome];
                if (rand <= cumulative + 1e-12) {
                    outcomes[i] = outcome;
                    break;
                }
            }
        }
        return outcomes;
    }
    
    private void normalizeProbabilities(double[] probabilities) {
        double total = 0.0;
        for (double probability : probabilities) {
            total += probability;
        }
        if (total <= 0.0) {
            throw new IllegalStateException("Observable produced no measurement probability");
        }
        for (int i = 0; i < probabilities.length; i++) {
            probabilities[i] /= total;
        }
    }
    
    private int[] targetIndices() {
        int[] indices = new int[targetQubits.length];
        for (int i = 0; i < targetQubits.length; i++) {
            indices[i] = targetQubits[i].getIndex();
        }
        return indices;
    }
    
    private static int extractTargetBits(int basisState, int[] targets, int numQubits) {
        int outcome = 0;
        for (int t = 0; t < targets.length; t++) {
            int bit = (basisState >> (numQubits - 1 - targets[t])) & 1;
            outcome |= bit << (targets.length - 1 - t);
        }
        return outcome;
    }
    
    /**
     * Reports whether this observable is diagonal in the computational basis.
     *
     * <p>The test scans every off diagonal entry and returns {@code false} as soon as one has a
     * magnitude above {@code 1e-12}. Diagonal entries are not inspected, so any diagonal matrix
     * counts as a computational basis observable even when its diagonal values are not the
     * outcomes {@code 0} to {@code 2^n - 1}.</p>
     *
     * @return {@code true} if all off diagonal entries are negligible
     */
    public boolean isComputationalBasis() {
        for (int row = 0; row < operator.rows(); row++) {
            for (int col = 0; col < operator.cols(); col++) {
                if (row != col && operator.get(row, col).abs() > 1e-12) {
                    return false;
                }
            }
        }
        return true;
    }
    
    
    
    /**
     * Returns the display name of this observable.
     *
     * @return the name supplied at construction
     */
    public String getName() {
        return name;
    }
    
    /**
     * Returns the operator matrix.
     *
     * @return a copy of the Hermitian operator
     */
    public ComplexMatrix getOperator() {
        return operator.copy();
    }
    
    /**
     * Returns the target qubits.
     *
     * @return a copy of the target qubit array
     */
    public Qubit[] getTargetQubits() {
        return targetQubits.clone();
    }
    
    /**
     * Returns the descriptive type of this observable.
     *
     * @return the type supplied at construction
     */
    public ObservableType getType() {
        return type;
    }
    
    /**
     * Returns the number of target qubits.
     *
     * @return the number of qubits the operator acts on
     */
    public int getNumQubits() {
        return targetQubits.length;
    }
    
    
    
    
    /**
     * Returns the eigenvalues of the operator.
     *
     * <p>This is the raw spectrum of the operator matrix, not the per outcome array used for
     * measurement. Use {@link #getOutcomeEigenvalues()} for the values indexed by outcome.</p>
     *
     * @return the eigenvalues in ascending order for a Hermitian operator
     */
    public double[] getEigenvalues() {
        // Compute eigenvalues of the observable
        return operator.eigenvalues();
    }
    
    /**
     * Returns the eigenvectors of the operator.
     *
     * <p>The eigen decomposition returns two matrices, the diagonal matrix of eigenvalues first
     * and the matrix of eigenvectors second, with eigenvector {@code j} stored in column
     * {@code j}. This order is what the probability and collapse paths rely on.</p>
     *
     * @return a two element array holding the eigenvalue matrix and the eigenvector matrix
     */
    public ComplexMatrix[] getEigenvectors() {
        // Compute eigenvectors of the observable
        return operator.eigenDecomposition();
    }
    
   
    /**
     * Builds the projector onto a single eigenvector of this observable.
     *
     * <p>The returned observable is {@code |v><v|} for eigenvector {@code v} at the given index,
     * over the same targets, named {@code name + "_Projector_" + eigenvalueIndex} and tagged
     * {@link ObservableType#PROJECTOR}. The construction assumes distinct eigenvalues: for a
     * degenerate spectrum this projects onto one eigenvector rather than onto the whole
     * eigenspace.</p>
     *
     * @param eigenvalueIndex the index of the eigenvector, in the sorted eigenvalue order
     * @return a new observable representing that projector
     * @throws IndexOutOfBoundsException if {@code eigenvalueIndex} is negative or beyond the last
     *         eigenvalue
     */
    public Observable getProjector(int eigenvalueIndex) {
        // Get projector onto specific eigenspace
        double[] eigenvalues = getEigenvalues();
        if (eigenvalueIndex < 0 || eigenvalueIndex >= eigenvalues.length) {
            throw new IndexOutOfBoundsException("Eigenvalue index out of bounds");
        }
        
        ComplexMatrix[] eigendecomp = operator.eigenDecomposition();
        ComplexMatrix eigenvectors = eigendecomp[1];
        
        // Create projector matrix
        int dim = operator.rows();
        ComplexMatrix projector = new ComplexMatrix(dim, dim);
        
        // For simplicity, assume non-degenerate eigenvalues
        for (int i = 0; i < dim; i++) {
            for (int j = 0; j < dim; j++) {
                Complex element = eigenvectors.get(i, eigenvalueIndex)
                    .multiply(eigenvectors.get(j, eigenvalueIndex).conjugate());
                projector.set(i, j, element);
            }
        }
        
        return new Observable(
            name + "_Projector_" + eigenvalueIndex,
            projector,
            targetQubits,
            ObservableType.PROJECTOR
        );
    }
    
    /**
     * Forms the tensor product of this observable with another one.
     *
     * <p>The resulting operator is this operator tensored with the other, so the other observable
     * acts on the less significant bits. Targets are concatenated in the same order: the targets
     * of this observable first, then those of the other. The new observable is named
     * {@code this.name + "x" + other.name} and tagged {@link ObservableType#COMPOSITE}.</p>
     *
     * @param other the observable to append
     * @return a new composite observable over the combined targets
     * @throws NullPointerException if {@code other} is {@code null}
     */
    public Observable tensorProduct(Observable other) {
        ComplexMatrix tensorOp = this.operator.tensorProduct(other.operator);
        
        Qubit[] combinedTargets = new Qubit[this.targetQubits.length + other.targetQubits.length];
        System.arraycopy(this.targetQubits, 0, combinedTargets, 0, this.targetQubits.length);
        System.arraycopy(other.targetQubits, 0, combinedTargets, this.targetQubits.length, other.targetQubits.length);
        
        return new Observable(
            this.name + "x" + other.name,
            tensorOp,
            combinedTargets,
            ObservableType.COMPOSITE
        );
    }
    
    /**
     * Traces out all but the selected target positions.
     *
     * <p>Positions refer to places in this observable's target array, not to qubit indices. The
     * supplied positions are sorted ascending before the trace, so the reduced observable's target
     * order is ascending by original position, which can differ from the order supplied. If every
     * target is kept, this observable is returned unchanged rather than a copy. Otherwise the
     * result is named {@code name + "_partialTrace"} and keeps the type of this observable.</p>
     *
     * @param qubitsToKeep the target positions to retain, at least one, without duplicates
     * @return this observable when all targets are kept, otherwise a reduced observable
     * @throws IllegalArgumentException if the array is {@code null} or empty, if a position is
     *         out of range, or if a position occurs twice
     */
    public Observable partialTrace(int[] qubitsToKeep) {
        if (qubitsToKeep == null || qubitsToKeep.length == 0) {
            throw new IllegalArgumentException("At least one qubit must be kept");
        }
        
        boolean[] seen = new boolean[targetQubits.length];
        for (int index : qubitsToKeep) {
            if (index < 0 || index >= targetQubits.length) {
                throw new IllegalArgumentException("Qubit position out of range: " + index);
            }
            if (seen[index]) {
                throw new IllegalArgumentException("Duplicate qubit position: " + index);
            }
            seen[index] = true;
        }
        
        if (qubitsToKeep.length == targetQubits.length) {
            return this;
        }
        
        int[] keep = qubitsToKeep.clone();
        Arrays.sort(keep);
        ComplexMatrix reduced = MatrixOperations.partialTrace(
            operator, targetQubits.length, keep);
        Qubit[] reducedTargets = new Qubit[keep.length];
        for (int i = 0; i < keep.length; i++) {
            reducedTargets[i] = targetQubits[keep[i]];
        }
        
        return new Observable(name + "_partialTrace", reduced, reducedTargets, type);
    }
    
    // Factory methods for common observables
    /**
     * Creates the single qubit Pauli {@code X} observable.
     *
     * <p>The observable is named {@code "X_" + qubit label} and tagged
     * {@link ObservableType#PAULI}. It is not diagonal, so measuring it uses the eigenbasis path,
     * where outcome {@code 0} has eigenvalue {@code -1} and outcome {@code 1} has eigenvalue
     * {@code +1}.</p>
     *
     * @param qubit the target qubit
     * @return a Pauli {@code X} observable on that qubit
     * @throws NullPointerException if {@code qubit} is {@code null}
     */
    public static Observable pauliX(Qubit qubit) {
        return new Observable(
            "X_" + qubit.getLabel(),
            ComplexMatrix.pauliX(),
            new Qubit[]{qubit},
            ObservableType.PAULI
        );
    }
    
    /**
     * Creates the single qubit Pauli {@code Y} observable.
     *
     * <p>The observable is named {@code "Y_" + qubit label} and tagged
     * {@link ObservableType#PAULI}. It is not diagonal, so measuring it uses the eigenbasis path,
     * with the same {@code -1} and {@code +1} eigenvalues as Pauli {@code X}.</p>
     *
     * @param qubit the target qubit
     * @return a Pauli {@code Y} observable on that qubit
     * @throws NullPointerException if {@code qubit} is {@code null}
     */
    public static Observable pauliY(Qubit qubit) {
        return new Observable(
            "Y_" + qubit.getLabel(),
            ComplexMatrix.pauliY(),
            new Qubit[]{qubit},
            ObservableType.PAULI
        );
    }
    
    /**
     * Creates the single qubit Pauli {@code Z} observable.
     *
     * <p>The observable is named {@code "Z_" + qubit label} and tagged
     * {@link ObservableType#PAULI}. It is diagonal, so it acts as a computational basis
     * measurement with eigenvalues {@code +1} for outcome {@code 0} and {@code -1} for outcome
     * {@code 1}.</p>
     *
     * @param qubit the target qubit
     * @return a Pauli {@code Z} observable on that qubit
     * @throws NullPointerException if {@code qubit} is {@code null}
     */
    public static Observable pauliZ(Qubit qubit) {
        return new Observable(
            "Z_" + qubit.getLabel(),
            ComplexMatrix.pauliZ(),
            new Qubit[]{qubit},
            ObservableType.PAULI
        );
    }
 // MULTI-QUBIT VERSIONS (for tensor products)
    /**
     * Creates the tensor product of Pauli {@code X} over several qubits.
     *
     * <p>The first qubit is the most significant factor. A single element array returns the plain
     * single qubit observable, while longer arrays return a composite whose name joins the parts
     * with {@code "x"} and whose type is {@link ObservableType#COMPOSITE}.</p>
     *
     * @param qubits the target qubits, at least one, in significance order
     * @return the tensor product of Pauli {@code X} on those qubits
     * @throws ArrayIndexOutOfBoundsException if {@code qubits} is empty
     * @throws NullPointerException if {@code qubits} is {@code null}
     */
    public static Observable pauliX(Qubit[] qubits) {
        if (qubits.length == 1) {
            return pauliX(qubits[0]);
        }
        Observable result = pauliX(qubits[0]);
        for (int i = 1; i < qubits.length; i++) {
            result = result.tensorProduct(pauliX(qubits[i]));
        }
        return result;
    }
    
    /**
     * Creates the tensor product of Pauli {@code Y} over several qubits.
     *
     * <p>The first qubit is the most significant factor. A single element array returns the plain
     * single qubit observable, while longer arrays return a composite whose name joins the parts
     * with {@code "x"} and whose type is {@link ObservableType#COMPOSITE}.</p>
     *
     * @param qubits the target qubits, at least one, in significance order
     * @return the tensor product of Pauli {@code Y} on those qubits
     * @throws ArrayIndexOutOfBoundsException if {@code qubits} is empty
     * @throws NullPointerException if {@code qubits} is {@code null}
     */
    public static Observable pauliY(Qubit[] qubits) {
        if (qubits.length == 1) {
            return pauliY(qubits[0]);
        }
        Observable result = pauliY(qubits[0]);
        for (int i = 1; i < qubits.length; i++) {
            result = result.tensorProduct(pauliY(qubits[i]));
        }
        return result;
    }
    
    /**
     * Creates the tensor product of Pauli {@code Z} over several qubits.
     *
     * <p>The first qubit is the most significant factor. A single element array returns the plain
     * single qubit observable, while longer arrays return a composite whose name joins the parts
     * with {@code "x"} and whose type is {@link ObservableType#COMPOSITE}. The result is always
     * diagonal, so it is measured as a computational basis measurement.</p>
     *
     * @param qubits the target qubits, at least one, in significance order
     * @return the tensor product of Pauli {@code Z} on those qubits
     * @throws ArrayIndexOutOfBoundsException if {@code qubits} is empty
     * @throws NullPointerException if {@code qubits} is {@code null}
     */
    public static Observable pauliZ(Qubit[] qubits) {
        if (qubits.length == 1) {
            return pauliZ(qubits[0]);
        }
        Observable result = pauliZ(qubits[0]);
        for (int i = 1; i < qubits.length; i++) {
            result = result.tensorProduct(pauliZ(qubits[i]));
        }
        return result;
    }
    
    /**
     * Creates the identity observable on one qubit.
     *
     * <p>Named {@code "I_" + qubit label} and tagged {@link ObservableType#IDENTITY}. Because it
     * is diagonal, it is measured in the computational basis, and every outcome carries the
     * eigenvalue {@code 1}.</p>
     *
     * @param qubit the target qubit
     * @return the identity observable on that qubit
     * @throws NullPointerException if {@code qubit} is {@code null}
     */
    public static Observable identity(Qubit qubit) {
        return new Observable(
            "I_" + qubit.getLabel(),
            ComplexMatrix.identity(2),
            new Qubit[]{qubit},
            ObservableType.IDENTITY
        );
    }
    /**
     * Creates the identity observable on several qubits.
     *
     * <p>The operator is the {@code 2^n} identity matrix and the name is {@code "I_"} followed by
     * the qubit count, so two different target sets of the same size share a name.</p>
     *
     * @param qubits the target qubits, at least one
     * @return the identity observable over those qubits
     * @throws NullPointerException if {@code qubits} is {@code null}
     */
    public static Observable identity(Qubit[] qubits) {
        int dim = 1 << qubits.length;
        return new Observable(
            "I_" + qubits.length,
            ComplexMatrix.identity(dim),
            qubits,
            ObservableType.IDENTITY
        );
    }
    
    /**
     * Creates a projector observable from a supplied matrix.
     *
     * <p>The matrix is validated like any other observable, so it must be Hermitian and of size
     * {@code 2^n}. The type is fixed to {@link ObservableType#PROJECTOR}.</p>
     *
     * @param name the display name
     * @param matrix the Hermitian projector matrix
     * @param qubits the target qubits, at least one
     * @return the projector observable
     * @throws IllegalArgumentException if {@code matrix} is not Hermitian or its size does not
     *         match the number of qubits
     * @throws NullPointerException if {@code matrix} or {@code qubits} is {@code null}
     */
    public static Observable projector(String name, ComplexMatrix matrix, Qubit[] qubits) {
        return new Observable(name, matrix, qubits, ObservableType.PROJECTOR);
    }
    
    /**
     * Builds a Pauli string observable from a short expression.
     *
     * <p>Before parsing, whitespace, tensor symbols, asterisks, commas and lowercase {@code x}
     * characters are removed and the remainder is upper cased. Each remaining character must be
     * one of {@code I}, {@code X}, {@code Y} or {@code Z} and maps to the qubit at the same
     * position, so the string length must equal the number of qubits. The terms are combined left
     * to right with {@link #tensorProduct(Observable)}, which makes the first character the most
     * significant factor.</p>
     *
     * @param expr the Pauli string, for example {@code "XZI"}
     * @param qubits the target qubits, at least one, in significance order
     * @return the tensor product described by the expression
     * @throws IllegalArgumentException if either argument is {@code null}, if the qubit array is
     *         empty, if the cleaned expression length differs from the qubit count, or if it
     *         contains an unsupported character
     */
    public static Observable fromString(String expr, Qubit[] qubits) {
        if (expr == null || qubits == null || qubits.length == 0) {
            throw new IllegalArgumentException("Expression and qubits are required");
        }
        
        String cleaned = expr.replaceAll("\\s+", "")
            .replace("⊗", "")
            .replace("*", "")
            .replace(",", "")
            .replace("x", "")
            .toUpperCase(Locale.ROOT);
        
        if (cleaned.length() != qubits.length) {
            throw new IllegalArgumentException(
                "Expression has " + cleaned.length() + " Pauli operators but "
                    + qubits.length + " qubits were provided");
        }
        
        Observable result = null;
        for (int i = 0; i < cleaned.length(); i++) {
            Observable term;
            switch (cleaned.charAt(i)) {
                case 'I':
                    term = identity(qubits[i]);
                    break;
                case 'X':
                    term = pauliX(qubits[i]);
                    break;
                case 'Y':
                    term = pauliY(qubits[i]);
                    break;
                case 'Z':
                    term = pauliZ(qubits[i]);
                    break;
                default:
                    throw new IllegalArgumentException(
                        "Unsupported Pauli operator: " + cleaned.charAt(i));
            }
            result = result == null ? term : result.tensorProduct(term);
        }
        
        return result;
    }
    
    /**
     * Compares this observable with another object.
     *
     * <p>Two observables are equal when their names, operator matrices, target arrays and types
     * are all equal. Target arrays are compared element by element, and qubits compare by index,
     * so labels do not affect equality.</p>
     *
     * @param obj the object to compare with
     * @return {@code true} if {@code obj} is an observable with equal components
     */
    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        
        Observable other = (Observable) obj;
        return name.equals(other.name) &&
               operator.equals(other.operator) &&
               Arrays.equals(targetQubits, other.targetQubits) &&
               type == other.type;
    }
    
    /**
     * Returns a hash code consistent with {@link #equals(Object)}.
     *
     * @return the hash code of the name, operator, targets and type
     */
    @Override
    public int hashCode() {
        int result = name.hashCode();
        result = 31 * result + operator.hashCode();
        result = 31 * result + Arrays.hashCode(targetQubits);
        result = 31 * result + type.hashCode();
        return result;
    }
    
    /**
     * Returns a description of this observable.
     *
     * @return a string containing the name, type and target qubits
     */
    @Override
    public String toString() {
        return String.format(Locale.ROOT, 
            "Observable[name='%s', type=%s, qubits=%s]",
            name, type, Arrays.toString(targetQubits)
        );
    }
    
    // Observable types
    /**
     * Descriptive categories for observables.
     *
     * <p>The category is stored with an observable and reported by {@link Observable#getType()}.
     * It does not change how the observable is measured, because the measurement paths inspect
     * the operator matrix instead.</p>
     */
    public enum ObservableType {
        /** A single or multi qubit Pauli operator. */
        PAULI("Pauli operator"),
        /** A projection operator, including those built by {@link Observable#getProjector(int)}. */
        PROJECTOR("Projection operator"),
        /** An operator representing a Hamiltonian. */
        HAMILTONIAN("Hamiltonian"),
        /** A density operator used as an observable. */
        DENSITY("Density operator"),
        /** The identity operator. */
        IDENTITY("Identity operator"),
        /** A tensor product of other observables. */
        COMPOSITE("Composite observable"),
        /** Any other observable. */
        CUSTOM("Custom observable");
        
        private final String description;
        
        /**
         * Creates a type with a human readable description.
         *
         * @param description the description returned by {@link #getDescription()}
         */
        ObservableType(String description) {
            this.description = description;
        }
        
        /**
         * Returns the human readable description of this type.
         *
         * @return the description supplied for this constant
         */
        public String getDescription() {
            return description;
        }
    }
}