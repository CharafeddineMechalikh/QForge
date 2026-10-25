package com.quantum.simulator.measurement;

import java.util.Locale;
import com.quantum.simulator.operations.QuantumOperation;
import com.quantum.simulator.state.QuantumState;
import com.quantum.simulator.state.Qubit;

/**
 * A {@link QuantumOperation} that measures a Hermitian {@link Observable}.
 *
 * <p>The operator bundles an observable, a shot count and a collapse policy. Measurement is
 * handled in two separate steps: {@link #measure(QuantumState)} only samples outcomes from the
 * observable distribution and leaves the state alone, while
 * {@link #measureAndCollapse(QuantumState)} additionally returns the state after collapse. The
 * {@link QuantumOperation#apply(QuantumState)} entry point delegates to the latter and returns
 * only its post measurement state.</p>
 *
 * <p>Collapse is controlled by the {@code destructive} flag. When it is {@code true}, the state
 * is projected onto the sampled outcome, either onto a computational basis state or onto the
 * matching eigenvector of the observable. When it is {@code false}, the sampled state is the
 * supplied state itself, so the observable is read without disturbing the state.</p>
 *
 * <p>The shot count is used for sampling. {@link #measureAndCollapse(QuantumState)} records all
 * {@code shots} sampled outcomes in the returned result but collapses onto the first sampled
 * outcome; the statistics and the collapse therefore come from the same draw only for the first
 * shot.</p>
 */
public class MeasurementOperator implements QuantumOperation {
    private final String name;
    private final Observable observable;
    private final int shots;
    private final boolean destructive;
    
    /**
     * Creates a measurement operator with an explicit name and collapse policy.
     *
     * @param name the operation name reported by {@link #getName()}
     * @param observable the observable to measure
     * @param shots the number of samples to draw when measuring
     * @param destructive {@code true} to collapse the state onto the sampled outcome,
     *        {@code false} to leave the state untouched
     */
    public MeasurementOperator(String name, Observable observable, int shots, boolean destructive) {
        this.name = name;
        this.observable = observable;
        this.shots = shots;
        this.destructive = destructive;
    }
    
    /**
     * Creates a destructive measurement operator whose name is derived from the observable.
     *
     * <p>The operation is named {@code observable.getName() + "_Measurement"} and is always
     * destructive, so applying it collapses the state.</p>
     *
     * @param observable the observable to measure
     * @param shots the number of samples to draw when measuring
     * @throws NullPointerException if {@code observable} is {@code null}
     */
    public MeasurementOperator(Observable observable, int shots) {
        this(observable.getName() + "_Measurement", observable, shots, true);
    }
    
    /**
     * Applies the measurement and returns the resulting state.
     *
     * <p>This is {@link #measureAndCollapse(QuantumState)} reduced to its state component. The
     * result is a new collapsed state when the operator is destructive, and the identical input
     * instance when it is not.</p>
     *
     * @param state the state to measure
     * @return the post measurement state
     * @throws IllegalArgumentException if the observable targets qubits outside {@code state}
     * @throws IllegalStateException if the observable yields no measurement probability
     */
    @Override
    public QuantumState apply(QuantumState state) {
        return measureAndCollapse(state).getCollapsedState();
    }
    
    /**
     * Samples the observable without modifying or collapsing the state.
     *
     * <p>Probabilities are computed from {@code state}, {@code shots} independent outcomes are
     * drawn, and the supplied probabilities are stored in the result alongside the sampled
     * counts. No collapse is performed and the state reference is not retained.</p>
     *
     * @param state the state to measure, which is left unchanged
     * @return the sampled measurement result
     * @throws IllegalArgumentException if the observable targets qubits outside {@code state}
     * @throws IllegalStateException if the observable yields no measurement probability
     */
    public MeasurementResult measure(QuantumState state) {
        return observable.measureMultiple(state, shots);
    }
    
    /**
     * Samples the observable, collapses the state onto one outcome, and reports both.
     *
     * <p>Outcome probabilities are computed once, then {@code shots} outcomes are sampled. The
     * outcome used for the collapse and reported by {@link MeasurementOutcome#getOutcome()} is
     * the first sampled outcome; when {@code shots} is zero an additional single draw is made for
     * that purpose. The returned result still lists only the {@code shots} sampled outcomes, so a
     * zero shot call yields an empty result whose reported outcome was not part of the sample.</p>
     *
     * <p>When the operator is destructive the collapsed state is a new state produced by
     * {@link Observable#collapse(QuantumState, int)}. Otherwise the collapsed state is the
     * supplied {@code state} instance itself.</p>
     *
     * @param state the state to measure
     * @return the measurement result, the state after the collapse and the collapsed outcome
     * @throws IllegalArgumentException if the observable targets qubits outside {@code state},
     *         or if the sampled outcome is outside the observable outcome range
     * @throws IllegalStateException if the observable yields no measurement probability, or if
     *         the sampled outcome has zero probability in computational basis collapse
     */
    public MeasurementOutcome measureAndCollapse(QuantumState state) {
        double[] probabilities = observable.getOutcomeProbabilities(state);
        int[] outcomes = observable.sampleOutcomes(probabilities, shots);
        
        int sampledOutcome;
        if (shots > 0) {
            sampledOutcome = outcomes[0];
        } else {
            sampledOutcome = observable.sampleOutcomes(probabilities, 1)[0];
        }
        
        MeasurementResult result = new MeasurementResult(
            name, observable.getTargetQubits(), outcomes, probabilities,
            observable.getOutcomeEigenvalues());
        QuantumState collapsedState = destructive
            ? observable.collapse(state, sampledOutcome)
            : state;
        
        return new MeasurementOutcome(result, collapsedState, sampledOutcome);
    }
    
    /**
     * The combined output of a measurement that also collapses the state.
     *
     * <p>It groups the sampled statistics, the state after the collapse, and the single outcome
     * index that was used for that collapse.</p>
     */
    public static class MeasurementOutcome {
        private final MeasurementResult result;
        private final QuantumState collapsedState;
        private final int outcome;
        
        /**
         * Creates an outcome holder.
         *
         * @param result the sampled measurement statistics
         * @param collapsedState the state produced by the collapse, or the unmodified input state
         *        when the measurement was not destructive
         * @param outcome the outcome index that was collapsed onto
         */
        public MeasurementOutcome(MeasurementResult result, QuantumState collapsedState, int outcome) {
            this.result = result;
            this.collapsedState = collapsedState;
            this.outcome = outcome;
        }
        
        /**
         * Returns the sampled measurement statistics.
         *
         * @return the measurement result
         */
        public MeasurementResult getResult() {
            return result;
        }
        
        /**
         * Returns the state after the measurement.
         *
         * @return the collapsed state for a destructive measurement, otherwise the input state
         */
        public QuantumState getCollapsedState() {
            return collapsedState;
        }
        
        /**
         * Returns the outcome index used for the collapse.
         *
         * @return the outcome index, which indexes the outcome eigenvalues of the observable
         */
        public int getOutcome() {
            return outcome;
        }
    }
    
    /**
     * Returns the operation name.
     *
     * @return the name supplied at construction
     */
    @Override
    public String getName() {
        return name;
    }
    
    /**
     * Returns the duration of the operation.
     *
     * <p>Measurement is modelled as taking one unit of time.</p>
     *
     * @return always {@code 1.0}
     */
    @Override
    public double getDuration() {
        return 1.0; // Measurement takes unit time
    }
    
    /**
     * Returns the measured qubits.
     *
     * @return a copy of the target qubits of the underlying observable
     */
    @Override
    public Qubit[] getTargetQubits() {
        return observable.getTargetQubits();
    }
    
    /**
     * Returns the control qubits.
     *
     * <p>Measurements have no control qubits.</p>
     *
     * @return a new empty array
     */
    @Override
    public Qubit[] getControlQubits() {
        return new Qubit[0]; // Measurements don't have control qubits
    }
    
    /**
     * Reports whether the operation is unitary.
     *
     * @return always {@code false}
     */
    @Override
    public boolean isUnitary() {
        return false; // Measurements are not unitary
    }
    
    /**
     * Reports whether the operation represents noise.
     *
     * @return always {@code false}
     */
    @Override
    public boolean isNoise() {
        return false;
    }
    
    /**
     * Reports whether the operation represents a measurement.
     *
     * @return always {@code true}
     */
    @Override
    public boolean isMeasurement() {
        return true;
    }
    
    /**
     * Returns the inverse operation.
     *
     * <p>Measurement is not invertible, so this method always fails.</p>
     *
     * @return never returns
     * @throws UnsupportedOperationException always
     */
    @Override
    public QuantumOperation inverse() {
        throw new UnsupportedOperationException("Measurement operations are not invertible");
    }
    
    /**
     * Returns the observable that is measured.
     *
     * @return the observable
     */
    public Observable getObservable() {
        return observable;
    }
    
    /**
     * Returns the configured shot count.
     *
     * @return the number of samples drawn per measurement
     */
    public int getShots() {
        return shots;
    }
    
    /**
     * Reports whether the measurement collapses the state.
     *
     * @return {@code true} if the state is projected onto the sampled outcome
     */
    public boolean isDestructive() {
        return destructive;
    }
    
    /**
     * Reports whether the observable is diagonal in the computational basis.
     *
     * @return {@code true} if {@link Observable#isComputationalBasis()} holds for the observable
     */
    public boolean isComputationalBasis() {
        return observable.isComputationalBasis();
    }
    
    /**
     * Creates a destructive measurement of several qubits in the computational basis.
     *
     * <p>The observable is the tensor product {@code Z} on the first qubit, then {@code Z} on each
     * further qubit in array order, so the resulting outcome integer orders the bits the same way
     * as {@link Observable#tensorProduct(Observable)}. The operator is named
     * {@code "ComputationalBasisMeasurement"} and always collapses the state.</p>
     *
     * @param qubits the qubits to measure, at least one, in significance order
     * @param shots the number of samples to draw
     * @return a destructive computational basis measurement
     * @throws ArrayIndexOutOfBoundsException if {@code qubits} is empty
     * @throws NullPointerException if {@code qubits} is {@code null}
     */
    public static MeasurementOperator computationalBasis(Qubit[] qubits, int shots) {
        Observable zObservable = Observable.pauliZ(qubits[0]);
        for (int i = 1; i < qubits.length; i++) {
            zObservable = zObservable.tensorProduct(Observable.pauliZ(qubits[i]));
        }
        
        return new MeasurementOperator(
            "ComputationalBasisMeasurement",
            zObservable,
            shots,
            true
        );
    }
    
    /**
     * Creates a destructive measurement of the Pauli {@code X} observable on one qubit.
     *
     * <p>The observable is obtained from {@link Observable#pauliX(Qubit)} and the operator uses
     * the default constructor, which makes the measurement destructive.</p>
     *
     * @param qubit the target qubit
     * @param shots the number of samples to draw
     * @return a destructive Pauli {@code X} measurement
     */
    public static MeasurementOperator pauliXMeasurement(Qubit qubit, int shots) {
        return new MeasurementOperator(
            Observable.pauliX(qubit),
            shots
        );
    }
    
    /**
     * Creates a destructive measurement of the Pauli {@code Y} observable on one qubit.
     *
     * <p>The observable is obtained from {@link Observable#pauliY(Qubit)} and the operator uses
     * the default constructor, which makes the measurement destructive.</p>
     *
     * @param qubit the target qubit
     * @param shots the number of samples to draw
     * @return a destructive Pauli {@code Y} measurement
     */
    public static MeasurementOperator pauliYMeasurement(Qubit qubit, int shots) {
        return new MeasurementOperator(
            Observable.pauliY(qubit),
            shots
        );
    }
    
    /**
     * Creates a destructive measurement of the Pauli {@code Z} observable on one qubit.
     *
     * <p>This is equivalent to a single qubit computational basis measurement. The observable is
     * obtained from {@link Observable#pauliZ(Qubit)} and the operator uses the default
     * constructor, which makes the measurement destructive.</p>
     *
     * @param qubit the target qubit
     * @param shots the number of samples to draw
     * @return a destructive Pauli {@code Z} measurement
     */
    public static MeasurementOperator pauliZMeasurement(Qubit qubit, int shots) {
        return new MeasurementOperator(
            Observable.pauliZ(qubit),
            shots
        );
    }
    
    /**
     * Compares this operator with another object.
     *
     * <p>Two operators are equal when their names, observables, shot counts and collapse policies
     * are equal.</p>
     *
     * @param obj the object to compare with
     * @return {@code true} if {@code obj} is a measurement operator with equal components
     */
    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        
        MeasurementOperator other = (MeasurementOperator) obj;
        return name.equals(other.name) &&
               observable.equals(other.observable) &&
               shots == other.shots &&
               destructive == other.destructive;
    }
    
    /**
     * Returns a hash code consistent with {@link #equals(Object)}.
     *
     * @return the hash code of the name, observable, shot count and collapse policy
     */
    @Override
    public int hashCode() {
        int result = name.hashCode();
        result = 31 * result + observable.hashCode();
        result = 31 * result + shots;
        result = 31 * result + (destructive ? 1 : 0);
        return result;
    }
    
    /**
     * Returns a description of this operator.
     *
     * @return a string containing the name, observable name, shot count and collapse policy
     */
    @Override
    public String toString() {
        return String.format(Locale.ROOT, 
            "MeasurementOperator[name='%s', observable=%s, shots=%d, destructive=%s]",
            name, observable.getName(), shots, destructive
        );
    }
}