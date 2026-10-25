package com.quantum.simulator.measurement;

import com.quantum.simulator.state.Qubit;
import java.util.*;

/**
 * The outcome of a (possibly repeated) quantum measurement, together with derived statistics.
 *
 * <p>A result stores the measured qubits, a count per outcome index, the probability of each
 * outcome and one real eigenvalue per outcome. Outcome {@code i} is the {@code i}-th eigenvalue
 * of the measured observable, so for a computational basis measurement the outcome integer is the
 * binary reading of the measured qubits and the eigenvalues default to {@code 0}, {@code 1},
 * ..., {@code 2^n - 1}. For a general Hermitian observable the outcomes are the eigenvalues in
 * ascending order, as produced by the operator's eigen decomposition.</p>
 *
 * <p>From those arrays the class derives the expectation value
 * {@code sum(p_i * lambda_i)}, the variance {@code sum(p_i * (lambda_i - E)^2)}, the standard
 * error {@code sqrt(variance / shots)} and the standard deviation {@code sqrt(variance)}.
 * When the shot count is zero the standard error is defined as zero.</p>
 *
 * <p>The stored qubit array, counts, probabilities and eigenvalues are copied on input, and the
 * accessors return copies or unmodifiable views. Instances are snapshots: later changes to the
 * arrays or maps passed to a constructor are not reflected, and no method mutates the stored
 * state. {@link #combine(MeasurementResult)} returns a new instance and leaves both inputs
 * unchanged.</p>
 */
public class MeasurementResult {
    private final String observableName;
    private final Qubit[] measuredQubits;
    private final Map<Integer, Integer> outcomeCounts;
    private final double[] outcomeProbabilities;
    private final double[] outcomeEigenvalues;
    private final int totalShots;
    private final double expectationValue;
    private final double variance;
    private final double standardError;
    private final long timestamp;
    
    /**
     * Creates a result from sampled counts, using outcome indices as eigenvalues.
     *
     * <p>Probabilities are derived from the counts divided by {@code totalShots}. The eigenvalues
     * default to the outcome indices {@code 0} to {@code 2^n - 1}, where {@code n} is the number
     * of measured qubits.</p>
     *
     * @param observableName the name of the measured observable
     * @param measuredQubits the measured qubits, whose length fixes the outcome count
     * @param outcomeCounts the number of times each outcome index was observed
     * @param totalShots the number of shots the counts were collected over
     * @throws NullPointerException if {@code measuredQubits} or {@code outcomeCounts} is
     *         {@code null}
     */
    public MeasurementResult(String observableName, Qubit[] measuredQubits,
                           Map<Integer, Integer> outcomeCounts, int totalShots) {
        this(observableName, measuredQubits, outcomeCounts, totalShots, null);
    }
    
    /**
     * Creates a result from sampled counts with explicit outcome eigenvalues.
     *
     * <p>Probabilities are derived from the counts divided by {@code totalShots}. No consistency
     * check is made between the counts and {@code totalShots}, so the caller must supply a shot
     * count that matches the counts.</p>
     *
     * @param observableName the name of the measured observable
     * @param measuredQubits the measured qubits, whose length fixes the outcome count
     * @param outcomeCounts the number of times each outcome index was observed
     * @param totalShots the number of shots the counts were collected over
     * @param outcomeEigenvalues one eigenvalue per outcome, or {@code null} to use the outcome
     *        indices
     * @throws IllegalArgumentException if {@code outcomeEigenvalues} is non {@code null} but its
     *         length is not {@code 2^n} for {@code n} measured qubits
     * @throws NullPointerException if {@code measuredQubits} or {@code outcomeCounts} is
     *         {@code null}
     */
    public MeasurementResult(String observableName, Qubit[] measuredQubits,
                           Map<Integer, Integer> outcomeCounts, int totalShots,
                           double[] outcomeEigenvalues) {
        this.observableName = observableName;
        this.measuredQubits = measuredQubits.clone();
        this.outcomeCounts = new HashMap<>(outcomeCounts);
        this.totalShots = totalShots;
        this.timestamp = System.currentTimeMillis();
        this.outcomeEigenvalues = resolveEigenvalues(outcomeEigenvalues, measuredQubits.length);
        
        // Calculate probabilities
        this.outcomeProbabilities = calculateProbabilities(outcomeCounts, totalShots);
        
        // Calculate statistics
        this.expectationValue = calculateExpectationValue();
        this.variance = calculateVariance();
        this.standardError = calculateStandardError();
    }
    
    /**
     * Creates a result from a raw outcome sequence, using outcome indices as eigenvalues.
     *
     * <p>The result is named {@code "CustomMeasurement"}. Counts are tabulated from the outcome
     * sequence and the shot count is the length of that sequence.</p>
     *
     * @param measuredQubits the measured qubits, whose length fixes the outcome count
     * @param outcomes the sampled outcome index for each shot
     * @param probabilities the probability of each outcome, or {@code null} or empty to derive
     *        them from the counts
     * @throws NullPointerException if {@code measuredQubits} or {@code outcomes} is {@code null}
     */
    public MeasurementResult(Qubit[] measuredQubits, int[] outcomes, double[] probabilities) {
        this(measuredQubits, outcomes, probabilities, null);
    }
    
    /**
     * Creates a result from a raw outcome sequence with explicit outcome eigenvalues.
     *
     * <p>The result is named {@code "CustomMeasurement"}. Counts are tabulated from the outcome
     * sequence and the shot count is the length of that sequence.</p>
     *
     * @param measuredQubits the measured qubits, whose length fixes the outcome count
     * @param outcomes the sampled outcome index for each shot
     * @param probabilities the probability of each outcome, or {@code null} or empty to derive
     *        them from the counts
     * @param outcomeEigenvalues one eigenvalue per outcome, or {@code null} to use the outcome
     *        indices
     * @throws IllegalArgumentException if {@code outcomeEigenvalues} is non {@code null} but its
     *         length is not {@code 2^n} for {@code n} measured qubits
     * @throws NullPointerException if {@code measuredQubits} or {@code outcomes} is {@code null}
     */
    public MeasurementResult(Qubit[] measuredQubits, int[] outcomes, double[] probabilities,
                             double[] outcomeEigenvalues) {
        this("CustomMeasurement", measuredQubits, outcomes, probabilities, outcomeEigenvalues);
    }
    
    /**
     * Creates a result from a raw outcome sequence with an observable name and eigenvalues.
     *
     * <p>The shot count is the length of {@code outcomes}. When {@code probabilities} is non
     * {@code null} and non empty it is copied as supplied and is not checked against the sampled
     * counts, so an exact distribution and an independent sample may differ. Otherwise the
     * probabilities are derived from the counts.</p>
     *
     * @param observableName the name of the measured observable
     * @param measuredQubits the measured qubits, whose length fixes the outcome count
     * @param outcomes the sampled outcome index for each shot
     * @param probabilities the probability of each outcome, or {@code null} or empty to derive
     *        them from the counts
     * @param outcomeEigenvalues one eigenvalue per outcome, or {@code null} to use the outcome
     *        indices
     * @throws IllegalArgumentException if {@code outcomeEigenvalues} is non {@code null} but its
     *         length is not {@code 2^n} for {@code n} measured qubits
     * @throws NullPointerException if {@code measuredQubits} or {@code outcomes} is {@code null}
     */
    public MeasurementResult(String observableName, Qubit[] measuredQubits, int[] outcomes,
                             double[] probabilities, double[] outcomeEigenvalues) {
        this.observableName = observableName;
        this.measuredQubits = measuredQubits.clone();
        this.totalShots = outcomes.length;
        this.timestamp = System.currentTimeMillis();
        this.outcomeEigenvalues = resolveEigenvalues(outcomeEigenvalues, measuredQubits.length);
        
        // Count outcomes
        Map<Integer, Integer> counts = new HashMap<>();
        for (int outcome : outcomes) {
            counts.put(outcome, counts.getOrDefault(outcome, 0) + 1);
        }
        this.outcomeCounts = counts;
        
        // Use provided probabilities or calculate from counts
        if (probabilities != null && probabilities.length > 0) {
            this.outcomeProbabilities = probabilities.clone();
        } else {
            this.outcomeProbabilities = calculateProbabilities(counts, totalShots);
        }
        
        // Calculate statistics
        this.expectationValue = calculateExpectationValue();
        this.variance = calculateVariance();
        this.standardError = calculateStandardError();
    }
    
    private static double[] resolveEigenvalues(double[] eigenvalues, int numQubits) {
        int numOutcomes = 1 << numQubits;
        if (eigenvalues != null) {
            if (eigenvalues.length != numOutcomes) {
                throw new IllegalArgumentException(
                    "Expected " + numOutcomes + " eigenvalues, got " + eigenvalues.length);
            }
            return eigenvalues.clone();
        }
        double[] defaults = new double[numOutcomes];
        for (int i = 0; i < numOutcomes; i++) {
            defaults[i] = i;
        }
        return defaults;
    }
    
    private double[] calculateProbabilities(Map<Integer, Integer> counts, int totalShots) {
        int numOutcomes = 1 << measuredQubits.length;
        double[] probs = new double[numOutcomes];
        
        for (Map.Entry<Integer, Integer> entry : counts.entrySet()) {
            int outcome = entry.getKey();
            if (outcome >= 0 && outcome < numOutcomes) {
                probs[outcome] = (double) entry.getValue() / totalShots;
            }
        }
        
        return probs;
    }
    
    private double calculateExpectationValue() {
        double expectation = 0.0;
        int numOutcomes = outcomeProbabilities.length;
        
        for (int outcome = 0; outcome < numOutcomes; outcome++) {
            expectation += outcomeEigenvalues[outcome] * outcomeProbabilities[outcome];
        }
        
        return expectation;
    }
    
    private double calculateVariance() {
        double variance = 0.0;
        int numOutcomes = outcomeProbabilities.length;
        
        for (int outcome = 0; outcome < numOutcomes; outcome++) {
            double diff = outcomeEigenvalues[outcome] - expectationValue;
            variance += outcomeProbabilities[outcome] * diff * diff;
        }
        
        return variance;
    }
    
    private double calculateStandardError() {
        if (totalShots == 0) {
            return 0.0;
        }
        return Math.sqrt(variance / totalShots);
    }
    
    /**
     * Returns the name of the measured observable.
     *
     * @return the observable name supplied at construction
     */
    public String getObservableName() {
        return observableName;
    }
    
    /**
     * Returns the measured qubits.
     *
     * @return a copy of the measured qubit array
     */
    public Qubit[] getMeasuredQubits() {
        return measuredQubits.clone();
    }
    
    /**
     * Returns the observed count per outcome.
     *
     * @return an unmodifiable view of the internal count map, keyed by outcome index
     */
    public Map<Integer, Integer> getOutcomeCounts() {
        return Collections.unmodifiableMap(outcomeCounts);
    }
    
    /**
     * Returns the probability of each outcome.
     *
     * @return a copy of the probability array, indexed by outcome
     */
    public double[] getOutcomeProbabilities() {
        return outcomeProbabilities.clone();
    }
    
    /**
     * Returns the eigenvalue associated with each outcome.
     *
     * @return a copy of the eigenvalue array, indexed by outcome
     */
    public double[] getOutcomeEigenvalues() {
        return outcomeEigenvalues.clone();
    }
    
    /**
     * Returns the eigenvalue of one outcome.
     *
     * @param outcome the outcome index
     * @return the eigenvalue recorded for that outcome
     * @throws IndexOutOfBoundsException if {@code outcome} is negative or beyond the last outcome
     */
    public double getEigenvalue(int outcome) {
        if (outcome < 0 || outcome >= outcomeEigenvalues.length) {
            throw new IndexOutOfBoundsException("Outcome index out of bounds");
        }
        return outcomeEigenvalues[outcome];
    }
    
    /**
     * Returns the outcome with the highest observed count.
     *
     * <p>Only outcomes present in the count map are considered. If two outcomes share the highest
     * count, the one encountered first while iterating the count map is returned.</p>
     *
     * @return the most frequent outcome index, or {@code -1} if no outcomes were recorded
     */
    public int getMostFrequentOutcome() {
        int bestOutcome = -1;
        int bestCount = -1;
        for (Map.Entry<Integer, Integer> entry : outcomeCounts.entrySet()) {
            if (entry.getValue() > bestCount) {
                bestCount = entry.getValue();
                bestOutcome = entry.getKey();
            }
        }
        return bestOutcome;
    }
    
    /**
     * Returns the recorded probability of one outcome.
     *
     * @param outcome the outcome index
     * @return the probability of that outcome, or zero if it was never assigned
     * @throws IndexOutOfBoundsException if {@code outcome} is negative or beyond the last outcome
     */
    public double getProbability(int outcome) {
        if (outcome < 0 || outcome >= outcomeProbabilities.length) {
            throw new IndexOutOfBoundsException("Outcome index out of bounds");
        }
        return outcomeProbabilities[outcome];
    }
    
    /**
     * Returns the observed count of one outcome.
     *
     * @param outcome the outcome index
     * @return the count for that outcome, or zero if it was never observed
     */
    public int getCount(int outcome) {
        return outcomeCounts.getOrDefault(outcome, 0);
    }
    
    /**
     * Returns the number of shots the result was collected over.
     *
     * @return the total shot count
     */
    public int getTotalShots() {
        return totalShots;
    }
    
    /**
     * Returns the expectation value of the observable.
     *
     * <p>This is {@code sum(p_i * lambda_i)} over all outcomes, using the stored probabilities
     * and eigenvalues. When probabilities were supplied directly they are used as given, so the
     * value is the exact expectation rather than the sample mean of the counts.</p>
     *
     * @return the expectation value
     */
    public double getExpectationValue() {
        return expectationValue;
    }
    
    /**
     * Returns the variance of the observable.
     *
     * @return the variance {@code sum(p_i * (lambda_i - E)^2)}
     */
    public double getVariance() {
        return variance;
    }
    
    /**
     * Returns the standard error of the sampled mean.
     *
     * @return the square root of the variance divided by the shot count, or zero when there are
     *         no shots
     */
    public double getStandardError() {
        return standardError;
    }
    
    /**
     * Returns the standard deviation of the observable.
     *
     * @return the square root of {@link #getVariance()}
     */
    public double getStandardDeviation() {
        return Math.sqrt(variance);
    }
    
    /**
     * Returns when this result was created.
     *
     * @return the creation time in milliseconds since the epoch
     */
    public long getTimestamp() {
        return timestamp;
    }
    
    /**
     * Returns the number of measured qubits.
     *
     * @return the length of the measured qubit array
     */
    public int getNumQubits() {
        return measuredQubits.length;
    }
    
    /**
     * Returns a confidence half width for the sampled mean.
     *
     * <p>The confidence level is validated but does not influence the result: the method always
     * multiplies the standard error by the fixed normal approximation factor {@code 1.96} used
     * for a 95 percent interval. The returned value is the half width, not the interval bounds.</p>
     *
     * @param confidenceLevel the requested confidence level, strictly between zero and one
     * @return {@code 1.96} times {@link #getStandardError()}
     * @throws IllegalArgumentException if {@code confidenceLevel} is not strictly between zero
     *         and one
     */
    public double getConfidenceInterval(double confidenceLevel) {
        // Calculate confidence interval using standard error
        // For normal approximation of binomial
        if (confidenceLevel <= 0 || confidenceLevel >= 1) {
            throw new IllegalArgumentException("Confidence level must be between 0 and 1");
        }
        
        // Z-score for given confidence level (simplified)
        double zScore = 1.96; // For 95% confidence
        
        return zScore * standardError;
    }
    
    /**
     * Returns the bit string probability distribution over the measured qubits.
     *
     * <p>Keys are zero padded binary forms of the outcome index with exactly
     * {@code getNumQubits()} characters. Only outcomes with a strictly positive probability are
     * included.</p>
     *
     * @return a mutable map from bit string to probability
     */
    public Map<String, Double> getBitStringProbabilities() {
        Map<String, Double> bitProbs = new HashMap<>();
        int numOutcomes = outcomeProbabilities.length;
        
        for (int outcome = 0; outcome < numOutcomes; outcome++) {
            if (outcomeProbabilities[outcome] > 0) {
                String bitString = String.format(Locale.ROOT, "%" + measuredQubits.length + "s", 
                    Integer.toBinaryString(outcome))
                    .replace(' ', '0');
                bitProbs.put(bitString, outcomeProbabilities[outcome]);
            }
        }
        
        return bitProbs;
    }
    
    /**
     * Returns the Shannon entropy of the outcome distribution in bits.
     *
     * <p>Computed as {@code -sum(p * log2(p))} over the outcomes with strictly positive
     * probability. A deterministic result has entropy zero.</p>
     *
     * @return the entropy of the stored probabilities, in bits
     */
    public double getEntropy() {
        double entropy = 0.0;
        for (double prob : outcomeProbabilities) {
            if (prob > 0) {
                entropy -= prob * Math.log(prob) / Math.log(2);
            }
        }
        return entropy;
    }
    
    /**
     * Combines this result with another result over the same qubits.
     *
     * <p>The two results must have element wise equal measured qubit arrays, where qubit equality
     * is determined by index. Their counts are summed and their shot counts added, and the new
     * result is named {@code this.observableName + "+" + other.observableName}. Because the
     * combined result is built from counts alone, it uses the default eigenvalues (the outcome
     * indices) and discards any custom eigenvalues of the two inputs.</p>
     *
     * @param other the other result to combine with
     * @return a new result holding the summed counts and shot count
     * @throws IllegalArgumentException if the two results do not cover the same qubits
     * @throws NullPointerException if {@code other} is {@code null}
     */
    public MeasurementResult combine(MeasurementResult other) {
        if (!Arrays.equals(this.measuredQubits, other.measuredQubits)) {
            throw new IllegalArgumentException("Cannot combine measurements of different qubits");
        }
        
        Map<Integer, Integer> combinedCounts = new HashMap<>(this.outcomeCounts);
        other.outcomeCounts.forEach((k, v) -> combinedCounts.merge(k, v, Integer::sum));
        
        int combinedShots = this.totalShots + other.totalShots;
        
        return new MeasurementResult(
            this.observableName + "+" + other.observableName,
            this.measuredQubits,
            combinedCounts,
            combinedShots
        );
    }
    
    /**
     * Renders the result as a text histogram.
     *
     * <p>The output starts with the observable name, shot count, expectation value and variance,
     * then lists the most frequent outcomes in descending count order. Each line shows the zero
     * padded bit string, the count, the sample frequency and a bar of at most 40 hash characters
     * whose length is the frequency times 40. At most {@code maxBars} outcomes are shown, followed
     * by a line stating how many were omitted.</p>
     *
     * <p>Frequencies are computed as count divided by the total shot count, so a result with zero
     * shots produces undefined display values.</p>
     *
     * @param maxBars the maximum number of outcome lines to render
     * @return the formatted histogram text
     */
    public String toHistogramString(int maxBars) {
        StringBuilder sb = new StringBuilder();
        sb.append("Measurement Result: ").append(observableName).append("\n");
        sb.append("Total shots: ").append(totalShots).append("\n");
        sb.append("Expectation: ").append(String.format(Locale.ROOT, "%.4f", expectationValue)).append("\n");
        sb.append("Variance: ").append(String.format(Locale.ROOT, "%.4f", variance)).append("\n\n");
        
        List<Map.Entry<Integer, Integer>> sortedEntries = new ArrayList<>(outcomeCounts.entrySet());
        sortedEntries.sort((a, b) -> b.getValue().compareTo(a.getValue()));
        
        int displayed = Math.min(sortedEntries.size(), maxBars);
        for (int i = 0; i < displayed; i++) {
            Map.Entry<Integer, Integer> entry = sortedEntries.get(i);
            int outcome = entry.getKey();
            int count = entry.getValue();
            double prob = (double) count / totalShots;
            
            String bitString = String.format(Locale.ROOT, "%" + measuredQubits.length + "s", 
                Integer.toBinaryString(outcome))
                .replace(' ', '0');
            
            int barLength = (int) (prob * 40);
            String bar = "#".repeat(barLength);
            
            sb.append(String.format(Locale.ROOT, "%s | %-5d | %.4f | %s%n", 
                bitString, count, prob, bar));
        }
        
        if (sortedEntries.size() > maxBars) {
            sb.append("... and ").append(sortedEntries.size() - maxBars).append(" more outcomes\n");
        }
        
        return sb.toString();
    }
    
    /**
     * Returns a one line summary of this result.
     *
     * @return a string containing the observable name, qubit count, shot count, expectation value
     *         and standard error
     */
    @Override
    public String toString() {
        return String.format(Locale.ROOT, 
            "MeasurementResult[observable='%s', qubits=%d, shots=%d, expectation=%.4f +/- %.4f]",
            observableName, measuredQubits.length, totalShots, expectationValue, standardError
        );
    }
}