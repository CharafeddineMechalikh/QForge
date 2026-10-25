package com.quantum.simulator.state;

import java.util.Locale;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class Qubit {
    private final int index;
    private final String label;
    private boolean measured;
    private MeasurementResult lastMeasurement;
    
    public Qubit(int index, String label) {
        if (index < 0) {
            throw new IllegalArgumentException("Qubit index cannot be negative");
        }
        this.index = index;
        this.label = (label != null) ? label : "q" + index;
        this.measured = false;
        this.lastMeasurement = null;
    }
    
    public Qubit(int index) {
        this(index, "q" + index);
    }
    
    public static List<Qubit> createQubits(int numQubits) {
        List<Qubit> qubits = new ArrayList<>(numQubits);
        for (int i = 0; i < numQubits; i++) {
            qubits.add(new Qubit(i));
        }
        return qubits;
    }
    
    public static List<Qubit> createQubits(int numQubits, String prefix) {
        List<Qubit> qubits = new ArrayList<>(numQubits);
        for (int i = 0; i < numQubits; i++) {
            qubits.add(new Qubit(i, prefix + i));
        }
        return qubits;
    }
    
    public int getIndex() {
        return index;
    }
    
    public String getLabel() {
        return label;
    }
    
    public void setLabel(String newLabel) {
        // Note: label is final in this implementation, but we could make it mutable
        throw new UnsupportedOperationException("Qubit labels are immutable in this implementation");
    }
    
    public boolean isMeasured() {
        return measured;
    }
    
    public void setMeasured(boolean measured) {
        this.measured = measured;
    }
    
    public MeasurementResult getLastMeasurement() {
        return lastMeasurement;
    }
    
    public void setLastMeasurement(MeasurementResult result) {
        this.lastMeasurement = result;
        this.measured = (result != null);
    }
    
    public void reset() {
        this.measured = false;
        this.lastMeasurement = null;
    }
    
    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        
        Qubit other = (Qubit) obj;
        return index == other.index;
    }
    
    @Override
    public int hashCode() {
        return Objects.hash(index);
    }
    
    @Override
    public String toString() {
        return String.format(Locale.ROOT, "Qubit[%d, label='%s', measured=%s]",
            index, label, measured);
    }
    
    // Inner class for measurement results
    public static class MeasurementResult {
        private final int outcome; // 0 or 1
        private final double probability;
        private final long timestamp;
        
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
        
        public int getOutcome() {
            return outcome;
        }
        
        public double getProbability() {
            return probability;
        }
        
        public long getTimestamp() {
            return timestamp;
        }
        
        @Override
        public String toString() {
            return String.format(Locale.ROOT, "MeasurementResult{outcome=%d, probability=%.4f, time=%d}",
                outcome, probability, timestamp);
        }
    }
}