package com.quantum.simulator.measurement;

import com.quantum.simulator.state.Qubit;
import java.util.*;

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
    
    public MeasurementResult(String observableName, Qubit[] measuredQubits,
                           Map<Integer, Integer> outcomeCounts, int totalShots) {
        this(observableName, measuredQubits, outcomeCounts, totalShots, null);
    }
    
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
    
    public MeasurementResult(Qubit[] measuredQubits, int[] outcomes, double[] probabilities) {
        this(measuredQubits, outcomes, probabilities, null);
    }
    
    public MeasurementResult(Qubit[] measuredQubits, int[] outcomes, double[] probabilities,
                             double[] outcomeEigenvalues) {
        this("CustomMeasurement", measuredQubits, outcomes, probabilities, outcomeEigenvalues);
    }
    
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
    
    public String getObservableName() {
        return observableName;
    }
    
    public Qubit[] getMeasuredQubits() {
        return measuredQubits.clone();
    }
    
    public Map<Integer, Integer> getOutcomeCounts() {
        return Collections.unmodifiableMap(outcomeCounts);
    }
    
    public double[] getOutcomeProbabilities() {
        return outcomeProbabilities.clone();
    }
    
    public double[] getOutcomeEigenvalues() {
        return outcomeEigenvalues.clone();
    }
    
    public double getEigenvalue(int outcome) {
        if (outcome < 0 || outcome >= outcomeEigenvalues.length) {
            throw new IndexOutOfBoundsException("Outcome index out of bounds");
        }
        return outcomeEigenvalues[outcome];
    }
    
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
    
    public double getProbability(int outcome) {
        if (outcome < 0 || outcome >= outcomeProbabilities.length) {
            throw new IndexOutOfBoundsException("Outcome index out of bounds");
        }
        return outcomeProbabilities[outcome];
    }
    
    public int getCount(int outcome) {
        return outcomeCounts.getOrDefault(outcome, 0);
    }
    
    public int getTotalShots() {
        return totalShots;
    }
    
    public double getExpectationValue() {
        return expectationValue;
    }
    
    public double getVariance() {
        return variance;
    }
    
    public double getStandardError() {
        return standardError;
    }
    
    public double getStandardDeviation() {
        return Math.sqrt(variance);
    }
    
    public long getTimestamp() {
        return timestamp;
    }
    
    public int getNumQubits() {
        return measuredQubits.length;
    }
    
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
    
    public double getEntropy() {
        double entropy = 0.0;
        for (double prob : outcomeProbabilities) {
            if (prob > 0) {
                entropy -= prob * Math.log(prob) / Math.log(2);
            }
        }
        return entropy;
    }
    
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
    
    @Override
    public String toString() {
        return String.format(Locale.ROOT, 
            "MeasurementResult[observable='%s', qubits=%d, shots=%d, expectation=%.4f +/- %.4f]",
            observableName, measuredQubits.length, totalShots, expectationValue, standardError
        );
    }
}