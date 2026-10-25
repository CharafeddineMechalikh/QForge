package com.mechalikh.qforge.state;

import java.util.Locale;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Metadata for one qubit: a fixed index, a label, and the most recent measurement state.
 *
 * <p>The index identifies the qubit position in a state, where qubit {@code 0} is the most
 * significant bit of the basis-state index. Equality and hashing use only the index, so
 * the label and the measurement fields do not affect comparisons.</p>
 */
public class Qubit {
    private final int index;
    private final String label;
    private boolean measured;
    private MeasurementResult lastMeasurement;
    
    /**
     * Creates a qubit with the given index and label.
     *
     * @param index non-negative qubit position
     * @param label display label; when null, the label defaults to {@code "q" + index}
     * @throws IllegalArgumentException if {@code index} is negative
     */
    public Qubit(int index, String label) {
        if (index < 0) {
            throw new IllegalArgumentException("Qubit index cannot be negative");
        }
        this.index = index;
        this.label = (label != null) ? label : "q" + index;
        this.measured = false;
        this.lastMeasurement = null;
    }
    
    /**
     * Creates a qubit with the given index and the default label {@code "q" + index}.
     *
     * @param index non-negative qubit position
     * @throws IllegalArgumentException if {@code index} is negative
     */
    public Qubit(int index) {
        this(index, "q" + index);
    }
    
    /**
     * Creates a list of qubits with indices {@code 0} to {@code numQubits - 1} and default
     * labels.
     *
     * @param numQubits number of qubits to create
     * @return a new mutable list of qubits
     * @throws IllegalArgumentException if {@code numQubits} is negative
     */
    public static List<Qubit> createQubits(int numQubits) {
        List<Qubit> qubits = new ArrayList<>(numQubits);
        for (int i = 0; i < numQubits; i++) {
            qubits.add(new Qubit(i));
        }
        return qubits;
    }
    
    /**
     * Creates a list of qubits with indices {@code 0} to {@code numQubits - 1} and labels
     * formed as {@code prefix + index}.
     *
     * @param numQubits number of qubits to create
     * @param prefix prefix for every label; a null prefix produces the text {@code "null"}
     * @return a new mutable list of qubits
     * @throws IllegalArgumentException if {@code numQubits} is negative
     */
    public static List<Qubit> createQubits(int numQubits, String prefix) {
        List<Qubit> qubits = new ArrayList<>(numQubits);
        for (int i = 0; i < numQubits; i++) {
            qubits.add(new Qubit(i, prefix + i));
        }
        return qubits;
    }
    
    /**
     * Returns the qubit index.
     *
     * @return the non-negative position of this qubit
     */
    public int getIndex() {
        return index;
    }
    
    /**
     * Returns the display label.
     *
     * @return the label, never null
     */
    public String getLabel() {
        return label;
    }
    
    /**
     * Always throws, because the label is stored in a final field.
     *
     * @param newLabel ignored
     * @throws UnsupportedOperationException always
     */
    public void setLabel(String newLabel) {
        // Note: label is final in this implementation, but we could make it mutable
        throw new UnsupportedOperationException("Qubit labels are immutable in this implementation");
    }
    
    /**
     * Returns whether the qubit is currently flagged as measured.
     *
     * @return the measured flag
     */
    public boolean isMeasured() {
        return measured;
    }
    
    /**
     * Sets the measured flag directly. The stored last measurement is not changed, so the
     * flag can disagree with {@link #getLastMeasurement()}.
     *
     * @param measured new value of the flag
     */
    public void setMeasured(boolean measured) {
        this.measured = measured;
    }
    
    /**
     * Returns the last measurement stored for this qubit.
     *
     * @return the last measurement result, or null if none was stored
     */
    public MeasurementResult getLastMeasurement() {
        return lastMeasurement;
    }
    
    /**
     * Stores a measurement result and sets the measured flag to {@code result != null}.
     *
     * @param result measurement to store; null clears the stored measurement and the flag
     */
    public void setLastMeasurement(MeasurementResult result) {
        this.lastMeasurement = result;
        this.measured = (result != null);
    }
    
    /**
     * Clears the measured flag and the stored measurement.
     */
    public void reset() {
        this.measured = false;
        this.lastMeasurement = null;
    }
    
    /**
     * Compares qubits by index only. The label and the measurement state are ignored.
     *
     * @param obj object to compare with
     * @return {@code true} if {@code obj} is a {@code Qubit} with the same index
     */
    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        
        Qubit other = (Qubit) obj;
        return index == other.index;
    }
    
    /**
     * Returns a hash code of the index, consistent with {@link #equals(Object)}.
     *
     * @return the hash code
     */
    @Override
    public int hashCode() {
        return Objects.hash(index);
    }
    
    /**
     * Returns a description including index, label, and measured flag.
     *
     * @return a diagnostic string
     */
    @Override
    public String toString() {
        return String.format(Locale.ROOT, "Qubit[%d, label='%s', measured=%s]",
            index, label, measured);
    }
    
    // Inner class for measurement results
    /**
     * Immutable snapshot of a single projective measurement on one qubit.
     *
     * <p>The class does not override {@code equals} or {@code hashCode}, so two results
     * with the same values compare equal only when they are the same instance.</p>
     */
    public static class MeasurementResult {
        private final int outcome; // 0 or 1
        private final double probability;
        private final long timestamp;
        
        /**
         * Creates a measurement result stamped with the current system time.
         *
         * @param outcome measured bit, either {@code 0} or {@code 1}
         * @param probability probability of this outcome, in {@code [0, 1]}
         * @throws IllegalArgumentException if {@code outcome} is not {@code 0} or
         *         {@code 1}, or if {@code probability} is outside {@code [0, 1]}
         */
        public MeasurementResult(int outcome, double probability) {
            if (outcome != 0 && outcome != 1) {
                throw new IllegalArgumentException("Measurement outcome must be 0 or 1");
            }
            if (probability < 0.0 || probability > 1.0) {
                throw new IllegalArgumentException("Probability must be between 0 and 1");
            }
            this.outcome = outcome;
            this.probability = probability;
            this.timestamp = System.currentTimeMillis();
        }
        
        /**
         * Returns the measured bit.
         *
         * @return {@code 0} or {@code 1}
         */
        public int getOutcome() {
            return outcome;
        }
        
        /**
         * Returns the probability recorded for this outcome.
         *
         * @return a value in {@code [0, 1]}
         */
        public double getProbability() {
            return probability;
        }
        
        /**
         * Returns the creation time in milliseconds since the epoch.
         *
         * @return the timestamp taken from {@link System#currentTimeMillis()}
         */
        public long getTimestamp() {
            return timestamp;
        }
        
        /**
         * Returns a description including outcome, probability, and timestamp.
         *
         * @return a diagnostic string
         */
        @Override
        public String toString() {
            return String.format(Locale.ROOT, "MeasurementResult{outcome=%d, probability=%.4f, time=%d}",
                outcome, probability, timestamp);
        }
    }
}