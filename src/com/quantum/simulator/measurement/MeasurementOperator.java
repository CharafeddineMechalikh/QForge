package com.quantum.simulator.measurement;

import java.util.Locale;
import com.quantum.simulator.operations.QuantumOperation;
import com.quantum.simulator.state.QuantumState;
import com.quantum.simulator.state.Qubit;

public class MeasurementOperator implements QuantumOperation {
    private final String name;
    private final Observable observable;
    private final int shots;
    private final boolean destructive;
    
    public MeasurementOperator(String name, Observable observable, int shots, boolean destructive) {
        this.name = name;
        this.observable = observable;
        this.shots = shots;
        this.destructive = destructive;
    }
    
    public MeasurementOperator(Observable observable, int shots) {
        this(observable.getName() + "_Measurement", observable, shots, true);
    }
    
    @Override
    public QuantumState apply(QuantumState state) {
        return measureAndCollapse(state).getCollapsedState();
    }
    
    public MeasurementResult measure(QuantumState state) {
        return observable.measureMultiple(state, shots);
    }
    
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
    
    public static class MeasurementOutcome {
        private final MeasurementResult result;
        private final QuantumState collapsedState;
        private final int outcome;
        
        public MeasurementOutcome(MeasurementResult result, QuantumState collapsedState, int outcome) {
            this.result = result;
            this.collapsedState = collapsedState;
            this.outcome = outcome;
        }
        
        public MeasurementResult getResult() {
            return result;
        }
        
        public QuantumState getCollapsedState() {
            return collapsedState;
        }
        
        public int getOutcome() {
            return outcome;
        }
    }
    
    @Override
    public String getName() {
        return name;
    }
    
    @Override
    public double getDuration() {
        return 1.0; // Measurement takes unit time
    }
    
    @Override
    public Qubit[] getTargetQubits() {
        return observable.getTargetQubits();
    }
    
    @Override
    public Qubit[] getControlQubits() {
        return new Qubit[0]; // Measurements don't have control qubits
    }
    
    @Override
    public boolean isUnitary() {
        return false; // Measurements are not unitary
    }
    
    @Override
    public boolean isNoise() {
        return false;
    }
    
    @Override
    public boolean isMeasurement() {
        return true;
    }
    
    @Override
    public QuantumOperation inverse() {
        throw new UnsupportedOperationException("Measurement operations are not invertible");
    }
    
    public Observable getObservable() {
        return observable;
    }
    
    public int getShots() {
        return shots;
    }
    
    public boolean isDestructive() {
        return destructive;
    }
    
    public boolean isComputationalBasis() {
        return observable.isComputationalBasis();
    }
    
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
    
    public static MeasurementOperator pauliXMeasurement(Qubit qubit, int shots) {
        return new MeasurementOperator(
            Observable.pauliX(qubit),
            shots
        );
    }
    
    public static MeasurementOperator pauliYMeasurement(Qubit qubit, int shots) {
        return new MeasurementOperator(
            Observable.pauliY(qubit),
            shots
        );
    }
    
    public static MeasurementOperator pauliZMeasurement(Qubit qubit, int shots) {
        return new MeasurementOperator(
            Observable.pauliZ(qubit),
            shots
        );
    }
    
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
    
    @Override
    public int hashCode() {
        int result = name.hashCode();
        result = 31 * result + observable.hashCode();
        result = 31 * result + shots;
        result = 31 * result + (destructive ? 1 : 0);
        return result;
    }
    
    @Override
    public String toString() {
        return String.format(Locale.ROOT, 
            "MeasurementOperator[name='%s', observable=%s, shots=%d, destructive=%s]",
            name, observable.getName(), shots, destructive
        );
    }
}