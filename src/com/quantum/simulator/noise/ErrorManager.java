package com.quantum.simulator.noise;

import com.quantum.simulator.state.QuantumState;
import com.quantum.simulator.state.StateVector;
import com.quantum.simulator.state.DensityMatrix;
import com.quantum.simulator.state.Qubit;
import java.util.*;

/**
 * Applies a {@link NoiseProfile} to states and records error statistics.
 *
 * <p>Noise is applied one model at a time, in the order returned by
 * {@link NoiseProfile#getAllNoiseModels()}, so the state passed to each model is the output of the
 * previous one. On a {@link StateVector} backend each channel draws one Kraus trajectory, which
 * means a single call produces one sampled noisy state rather than an average; on a
 * {@link DensityMatrix} backend the channels act deterministically.</p>
 *
 * <p>Targetless global models are expanded here: a model with an empty target array is applied
 * once per qubit of the state, each time using a copy whose target is that single qubit. Models
 * that already carry targets are applied once with their own target list. The log records only
 * the first target of a multi target model.</p>
 *
 * <p>The manager keeps a running average of fidelity, updated once per
 * {@link #applyNoise(QuantumState, NoiseProfile)} call by comparing the original state with the
 * final noisy state. It also counts applications: {@code getTotalOperations()} counts attempted
 * channel applications and {@code getErrorCount()} counts the attempts that completed without an
 * exception, so the reported error rate is a channel application ratio and not a physical error
 * probability. A failed application is logged with a message and leaves the state as it was
 * before that channel.</p>
 *
 * <p>All counters start at zero with a fidelity of one, and {@link #reset()} restores them.</p>
 */
public class ErrorManager {
    private final List<ErrorLogEntry> errorLog;
    private double totalFidelity;
    private double fidelitySum;
    private int fidelitySamples;
    private int errorCount;
    private int totalOperations;
    
    /**
     * Creates a manager with an empty log, a fidelity of one and zero counters.
     */
    public ErrorManager() {
        this.errorLog = new ArrayList<>();
        this.totalFidelity = 1.0;
        this.fidelitySum = 0.0;
        this.fidelitySamples = 0;
        this.errorCount = 0;
        this.totalOperations = 0;
    }
    
    /**
     * Applies every model in a profile and updates the statistics.
     *
     * <p>A {@code null} or empty profile returns the supplied state unchanged and does not touch
     * the fidelity statistics or the counters. Otherwise the models are applied in the order the
     * profile lists them, targetless global models are expanded to one application per qubit, and
     * the fidelity between the supplied state and the final state is added to the running
     * average.</p>
     *
     * @param state the state to disturb
     * @param profile the profile whose models are applied
     * @return the final noisy state, or {@code state} itself when nothing was applied
     * @throws UnsupportedOperationException if the state is neither a {@link StateVector} nor a
     *         {@link DensityMatrix}, so no fidelity can be computed
     */
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
    
    /**
     * Computes the fidelity between an ideal state and a noisy one.
     *
     * <p>Both states are converted to density matrices and compared with
     * {@link DensityMatrix#fidelity(DensityMatrix, DensityMatrix)}, which returns a value between
     * zero and one where one means indistinguishable. Neither state is modified.</p>
     *
     * @param idealState the reference state
     * @param noisyState the state to compare against it
     * @return the fidelity between the two states
     * @throws IllegalArgumentException if the states have different numbers of qubits
     * @throws UnsupportedOperationException if a state is neither a {@link StateVector} nor a
     *         {@link DensityMatrix}
     */
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
    
    /**
     * Creates a snapshot report of the current statistics.
     *
     * <p>The report copies the current log entries, so later changes to the manager are not
     * visible through the returned report.</p>
     *
     * @return a new report with the current fidelity, counters and error rate
     */
    public ErrorReport generateErrorReport() {
        return new ErrorReport(
            errorLog,
            totalFidelity,
            errorCount,
            totalOperations,
            calculateErrorRate()
        );
    }
    
    /**
     * Clears the log and restores the initial statistics.
     *
     * <p>The fidelity returns to one, the running average is discarded and all counters reset to
     * zero.</p>
     */
    public void reset() {
        errorLog.clear();
        totalFidelity = 1.0;
        fidelitySum = 0.0;
        fidelitySamples = 0;
        errorCount = 0;
        totalOperations = 0;
    }
    
    /**
     * Returns the running average fidelity.
     *
     * @return the mean fidelity over calls to {@link #applyNoise(QuantumState, NoiseProfile)},
     *         starting at one before any call
     */
    public double getCurrentFidelity() {
        return totalFidelity;
    }
    
    /**
     * Returns the ratio of completed applications to attempted applications.
     *
     * @return {@code errorCount / totalOperations}, or zero when no application was attempted
     */
    public double getErrorRate() {
        return calculateErrorRate();
    }
    
    /**
     * Returns the number of channel applications that completed without an exception.
     *
     * @return the count of successful applications
     */
    public int getErrorCount() {
        return errorCount;
    }
    
    /**
     * Returns the number of channel applications that were attempted.
     *
     * @return the attempted application count, including those that failed
     */
    public int getTotalOperations() {
        return totalOperations;
    }
    
    /**
     * Returns the recorded error log.
     *
     * @return an unmodifiable view of the live log, which reflects later additions
     */
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
    /**
     * One entry in the error log.
     *
     * <p>An entry records either a successful channel application, carrying the trace distance
     * between the state before and after, or a failure, carrying a message instead. The two cases
     * are distinguished by {@link #hasError()}.</p>
     */
    public static class ErrorLogEntry {
        private final long timestamp;
        private final String noiseType;
        private final Qubit affectedQubit;
        private final double noiseProbability;
        private final double stateDistance;
        private final String errorMessage;
        
        /**
         * Creates a log entry for a completed channel application.
         *
         * @param timestamp the time of the application in milliseconds since the epoch
         * @param noiseType the name of the applied model
         * @param affectedQubit the first target of the model, used for logging only
         * @param noiseProbability the probability configured on the model
         * @param stateDistance the trace distance between the states before and after
         */
        public ErrorLogEntry(long timestamp, String noiseType, Qubit affectedQubit,
                           double noiseProbability, double stateDistance) {
            this(timestamp, noiseType, affectedQubit, noiseProbability, stateDistance, null);
        }
        
        /**
         * Creates a log entry for a failed channel application.
         *
         * <p>The state distance of such an entry is fixed at zero, because no comparison state
         * exists.</p>
         *
         * @param timestamp the time of the failure in milliseconds since the epoch
         * @param noiseType the name of the model that failed
         * @param affectedQubit the first target of the model, used for logging only
         * @param noiseProbability the probability configured on the model
         * @param errorMessage the failure description
         */
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
        
        /**
         * Returns when the entry was created.
         *
         * @return the time in milliseconds since the epoch
         */
        public long getTimestamp() { return timestamp; }
        /**
         * Returns the name of the model involved.
         *
         * @return the model name
         */
        public String getNoiseType() { return noiseType; }
        /**
         * Returns the qubit recorded for the entry.
         *
         * @return the first target of the model, which for an expanded global model is the single
         *         qubit it was applied to
         */
        public Qubit getAffectedQubit() { return affectedQubit; }
        /**
         * Returns the probability configured on the model.
         *
         * @return the model probability at the time of logging
         */
        public double getNoiseProbability() { return noiseProbability; }
        /**
         * Returns the trace distance caused by the application.
         *
         * @return the distance between the state before and after, or zero for a failed entry
         */
        public double getStateDistance() { return stateDistance; }
        /**
         * Returns the failure description.
         *
         * @return the message for a failed application, or {@code null} for a successful one
         */
        public String getErrorMessage() { return errorMessage; }
        /**
         * Reports whether this entry describes a failure.
         *
         * @return {@code true} if an error message is present
         */
        public boolean hasError() { return errorMessage != null; }
        
        /**
         * Returns a one line description of the entry.
         *
         * @return the formatted entry, including either the distance or the error message
         */
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
    
    /**
     * An immutable snapshot of the statistics held by an {@link ErrorManager}.
     *
     * <p>The constructor copies the supplied entry list, so the report does not change when the
     * manager logs more errors. The counters and rate are stored as reported at creation time.</p>
     */
    public static class ErrorReport {
        private final List<ErrorLogEntry> errors;
        private final double totalFidelity;
        private final int errorCount;
        private final int totalOperations;
        private final double errorRate;
        private final long generationTime;
        
        /**
         * Creates a report from explicit values.
         *
         * @param errors the log entries to include, copied on construction
         * @param totalFidelity the average fidelity to report
         * @param errorCount the completed application count to report
         * @param totalOperations the attempted application count to report
         * @param errorRate the error rate to report
         */
        public ErrorReport(List<ErrorLogEntry> errors, double totalFidelity,
                          int errorCount, int totalOperations, double errorRate) {
            this.errors = new ArrayList<>(errors);
            this.totalFidelity = totalFidelity;
            this.errorCount = errorCount;
            this.totalOperations = totalOperations;
            this.errorRate = errorRate;
            this.generationTime = System.currentTimeMillis();
        }
        
        /**
         * Returns the log entries captured by this report.
         *
         * @return an unmodifiable view of the copied entries
         */
        public List<ErrorLogEntry> getErrors() { return Collections.unmodifiableList(errors); }
        /**
         * Returns the average fidelity captured by this report.
         *
         * @return the fidelity value
         */
        public double getTotalFidelity() { return totalFidelity; }
        /**
         * Returns the completed application count captured by this report.
         *
         * @return the count of successful applications
         */
        public int getErrorCount() { return errorCount; }
        /**
         * Returns the attempted application count captured by this report.
         *
         * @return the count of attempted applications
         */
        public int getTotalOperations() { return totalOperations; }
        /**
         * Returns the error rate captured by this report.
         *
         * @return the ratio of completed to attempted applications
         */
        public double getErrorRate() { return errorRate; }
        /**
         * Returns when this report was created.
         *
         * @return the creation time in milliseconds since the epoch
         */
        public long getGenerationTime() { return generationTime; }
        
        /**
         * Counts the captured entries per affected qubit.
         *
         * <p>Every entry contributes one to the count of its recorded qubit, including failed
         * entries. Qubits compare by index, so entries for the same index are aggregated even when
         * they hold different {@link Qubit} instances.</p>
         *
         * @return a new mutable map from qubit to entry count
         */
        public Map<Qubit, Integer> getErrorsPerQubit() {
            Map<Qubit, Integer> counts = new HashMap<>();
            for (ErrorLogEntry error : errors) {
                counts.merge(error.getAffectedQubit(), 1, Integer::sum);
            }
            return counts;
        }
        
        /**
         * Counts the captured entries per noise type name.
         *
         * @return a new mutable map from noise type name to entry count
         */
        public Map<String, Integer> getErrorsByType() {
            Map<String, Integer> counts = new HashMap<>();
            for (ErrorLogEntry error : errors) {
                counts.merge(error.getNoiseType(), 1, Integer::sum);
            }
            return counts;
        }
        
        /**
         * Returns a multi line summary of this report.
         *
         * <p>The summary shows the fidelity, the error rate as a percentage with the underlying
         * counts, the total error count, and at most the last ten log entries.</p>
         *
         * @return the formatted summary
         */
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