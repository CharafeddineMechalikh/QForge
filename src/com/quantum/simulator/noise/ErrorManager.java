package com.quantum.simulator.noise;

import com.quantum.simulator.state.QuantumState;
import com.quantum.simulator.state.StateVector;
import com.quantum.simulator.state.DensityMatrix;
import com.quantum.simulator.state.Qubit;
import java.util.*;

public class ErrorManager {
    private final List<ErrorLogEntry> errorLog;
    private double totalFidelity;
    private double fidelitySum;
    private int fidelitySamples;
    private int errorCount;
    private int totalOperations;
    
    public ErrorManager() {
        this.errorLog = new ArrayList<>();
        this.totalFidelity = 1.0;
        this.fidelitySum = 0.0;
        this.fidelitySamples = 0;
        this.errorCount = 0;
        this.totalOperations = 0;
    }
    
    public QuantumState applyNoise(QuantumState state, NoiseProfile profile) {
        if (profile == null || profile.isEmpty()) {
            return state;
        }
        
        QuantumState noisyState = state;
        for (NoiseModel noise : profile.getAllNoiseModels()) {
            Qubit[] targets = noise.getTargetQubits();
            if (targets.length == 0) {
                for (Qubit qubit : state.getQubits()) {
                    NoiseApplication result = applyNoiseModel(noisyState, noise.withTargetQubits(new Qubit[]{qubit}), qubit);
                    noisyState = result.state;
                }
            } else {
                NoiseApplication result = applyNoiseModel(noisyState, noise, targets[0]);
                noisyState = result.state;
            }
        }
        
        updateFidelity(state, noisyState);
        return noisyState;
    }
    
    private static class NoiseApplication {
        final QuantumState state;
        NoiseApplication(QuantumState state) { this.state = state; }
    }
    
    private NoiseApplication applyNoiseModel(QuantumState state, NoiseModel noise, Qubit qubit) {
        totalOperations++;
        try {
            QuantumState newState = noise.apply(state);
            logError(noise, qubit, state, newState);
            errorCount++;
            return new NoiseApplication(newState);
        } catch (Exception e) {
            logError("Failed to apply noise: " + e.getMessage(), noise, qubit);
            return new NoiseApplication(state);
        }
    }
    
    public double calculateFidelity(QuantumState idealState, QuantumState noisyState) {
        if (idealState.getNumQubits() != noisyState.getNumQubits()) {
            throw new IllegalArgumentException("States must have the same number of qubits");
        }
        return DensityMatrix.fidelity(toDensityMatrix(idealState), toDensityMatrix(noisyState));
    }
    
    private static DensityMatrix toDensityMatrix(QuantumState state) {
        if (state instanceof DensityMatrix) {
            return (DensityMatrix) state;
        }
        if (state instanceof StateVector) {
            return ((StateVector) state).toDensityMatrix();
        }
        throw new UnsupportedOperationException("Unsupported state type: " + state.getClass());
    }
    
    public ErrorReport generateErrorReport() {
        return new ErrorReport(
            errorLog,
            totalFidelity,
            errorCount,
            totalOperations,
            calculateErrorRate()
        );
    }
    
    public void reset() {
        errorLog.clear();
        totalFidelity = 1.0;
        fidelitySum = 0.0;
        fidelitySamples = 0;
        errorCount = 0;
        totalOperations = 0;
    }
    
    public double getCurrentFidelity() {
        return totalFidelity;
    }
    
    public double getErrorRate() {
        return calculateErrorRate();
    }
    
    public int getErrorCount() {
        return errorCount;
    }
    
    public int getTotalOperations() {
        return totalOperations;
    }
    
    public List<ErrorLogEntry> getErrorLog() {
        return Collections.unmodifiableList(errorLog);
    }
    
    private void logError(NoiseModel noise, Qubit qubit, QuantumState before, QuantumState after) {
        ErrorLogEntry entry = new ErrorLogEntry(
            System.currentTimeMillis(),
            noise.getName(),
            qubit,
            noise.getProbability(),
            calculateStateDistance(before, after)
        );
        errorLog.add(entry);
    }
    
    private void logError(String message, NoiseModel noise, Qubit qubit) {
        ErrorLogEntry entry = new ErrorLogEntry(
            System.currentTimeMillis(),
            noise.getName(),
            qubit,
            noise.getProbability(),
            message
        );
        errorLog.add(entry);
    }
    
    private void updateFidelity(QuantumState ideal, QuantumState noisy) {
        double currentFidelity = calculateFidelity(ideal, noisy);
        fidelitySum += currentFidelity;
        fidelitySamples++;
        totalFidelity = fidelitySum / fidelitySamples;
    }
    
    private double calculateStateDistance(QuantumState state1, QuantumState state2) {
        if (state1.getNumQubits() != state2.getNumQubits()) {
            throw new IllegalArgumentException("States must have the same number of qubits");
        }
        return DensityMatrix.traceDistance(toDensityMatrix(state1), toDensityMatrix(state2));
    }
    
    private double calculateErrorRate() {
        if (totalOperations == 0) {
            return 0.0;
        }
        return (double) errorCount / totalOperations;
    }
    
    // Inner classes
    public static class ErrorLogEntry {
        private final long timestamp;
        private final String noiseType;
        private final Qubit affectedQubit;
        private final double noiseProbability;
        private final double stateDistance;
        private final String errorMessage;
        
        public ErrorLogEntry(long timestamp, String noiseType, Qubit affectedQubit,
                           double noiseProbability, double stateDistance) {
            this(timestamp, noiseType, affectedQubit, noiseProbability, stateDistance, null);
        }
        
        public ErrorLogEntry(long timestamp, String noiseType, Qubit affectedQubit,
                           double noiseProbability, String errorMessage) {
            this(timestamp, noiseType, affectedQubit, noiseProbability, 0.0, errorMessage);
        }
        
        private ErrorLogEntry(long timestamp, String noiseType, Qubit affectedQubit,
                            double noiseProbability, double stateDistance, String errorMessage) {
            this.timestamp = timestamp;
            this.noiseType = noiseType;
            this.affectedQubit = affectedQubit;
            this.noiseProbability = noiseProbability;
            this.stateDistance = stateDistance;
            this.errorMessage = errorMessage;
        }
        
        public long getTimestamp() { return timestamp; }
        public String getNoiseType() { return noiseType; }
        public Qubit getAffectedQubit() { return affectedQubit; }
        public double getNoiseProbability() { return noiseProbability; }
        public double getStateDistance() { return stateDistance; }
        public String getErrorMessage() { return errorMessage; }
        public boolean hasError() { return errorMessage != null; }
        
        @Override
        public String toString() {
            if (hasError()) {
                return String.format(Locale.ROOT, "[%tT] %s on %s (p=%.3f): %s",
                    timestamp, noiseType, affectedQubit, noiseProbability, errorMessage);
            } else {
                return String.format(Locale.ROOT, "[%tT] %s on %s (p=%.3f, distance=%.4f)",
                    timestamp, noiseType, affectedQubit, noiseProbability, stateDistance);
            }
        }
    }
    
    public static class ErrorReport {
        private final List<ErrorLogEntry> errors;
        private final double totalFidelity;
        private final int errorCount;
        private final int totalOperations;
        private final double errorRate;
        private final long generationTime;
        
        public ErrorReport(List<ErrorLogEntry> errors, double totalFidelity,
                          int errorCount, int totalOperations, double errorRate) {
            this.errors = new ArrayList<>(errors);
            this.totalFidelity = totalFidelity;
            this.errorCount = errorCount;
            this.totalOperations = totalOperations;
            this.errorRate = errorRate;
            this.generationTime = System.currentTimeMillis();
        }
        
        public List<ErrorLogEntry> getErrors() { return Collections.unmodifiableList(errors); }
        public double getTotalFidelity() { return totalFidelity; }
        public int getErrorCount() { return errorCount; }
        public int getTotalOperations() { return totalOperations; }
        public double getErrorRate() { return errorRate; }
        public long getGenerationTime() { return generationTime; }
        
        public Map<Qubit, Integer> getErrorsPerQubit() {
            Map<Qubit, Integer> counts = new HashMap<>();
            for (ErrorLogEntry error : errors) {
                counts.merge(error.getAffectedQubit(), 1, Integer::sum);
            }
            return counts;
        }
        
        public Map<String, Integer> getErrorsByType() {
            Map<String, Integer> counts = new HashMap<>();
            for (ErrorLogEntry error : errors) {
                counts.merge(error.getNoiseType(), 1, Integer::sum);
            }
            return counts;
        }
        
        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder();
            sb.append("Error Report:\n");
            sb.append(String.format(Locale.ROOT, "  Average Fidelity: %.6f\n", totalFidelity));
            sb.append(String.format(Locale.ROOT, "  Error Rate: %.4f%% (%d/%d)\n", errorRate * 100, errorCount, totalOperations));
            sb.append(String.format(Locale.ROOT, "  Total Errors: %d\n", errorCount));
            
            if (!errors.isEmpty()) {
                sb.append("  Recent Errors:\n");
                int limit = Math.min(errors.size(), 10);
                for (int i = Math.max(0, errors.size() - limit); i < errors.size(); i++) {
                    sb.append("    ").append(errors.get(i)).append("\n");
                }
            }
            
            return sb.toString();
        }
    }
}