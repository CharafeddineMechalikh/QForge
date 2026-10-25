package com.quantum.simulator.measurement;

import com.quantum.simulator.math.Complex;
import com.quantum.simulator.state.QuantumState;
import com.quantum.simulator.state.StateVector;
import com.quantum.simulator.state.Qubit;
import java.util.*;

public class MeasurementHelper {
    private static final Random RANDOM = new Random();
    
    public static MeasurementResult measureInComputationalBasis(StateVector state, Qubit[] qubits, int shots) {
        int numQubits = state.getNumQubits();
        int targetSize = qubits.length;
        int[] targetIndices = new int[targetSize];
        
        for (int i = 0; i < targetSize; i++) {
            targetIndices[i] = qubits[i].getIndex();
        }
        
        // Calculate probabilities
        double[] outcomeProbabilities = new double[1 << targetSize];
        Complex[] amplitudes = state.getAmplitudes();
        
        for (int idx = 0; idx < amplitudes.length; idx++) {
            // Extract bits for target qubits
            int outcome = 0;
            for (int t = 0; t < targetSize; t++) {
                int bitPos = numQubits - 1 - targetIndices[t];
                int bit = (idx >> bitPos) & 1;
                outcome |= (bit << (targetSize - 1 - t));
            }
            
            double probability = amplitudes[idx].absSquare();
            outcomeProbabilities[outcome] += probability;
        }
        
        // Perform measurements
        int[] outcomes = new int[shots];
        Map<Integer, Integer> outcomeCounts = new HashMap<>();
        for (int s = 0; s < shots; s++) {
            int outcome = sampleFromDistribution(outcomeProbabilities);
            outcomes[s] = outcome;
            outcomeCounts.put(outcome, outcomeCounts.getOrDefault(outcome, 0) + 1);
        }
        
        // Use the correct constructor that takes Map<Integer, Integer>
        return new MeasurementResult("ComputationalBasis", qubits, outcomeCounts, shots);
    }
    
    // Alternative method that returns both probabilities and outcomes
    public static Map<String, Object> measureInComputationalBasisWithProbabilities(
            StateVector state, Qubit[] qubits, int shots) {
        
        Map<String, Object> result = new HashMap<>();
        
        int numQubits = state.getNumQubits();
        int targetSize = qubits.length;
        int[] targetIndices = new int[targetSize];
        
        for (int i = 0; i < targetSize; i++) {
            targetIndices[i] = qubits[i].getIndex();
        }
        
        // Calculate probabilities
        double[] outcomeProbabilities = new double[1 << targetSize];
        Complex[] amplitudes = state.getAmplitudes();
        
        for (int idx = 0; idx < amplitudes.length; idx++) {
            // Extract bits for target qubits
            int outcome = 0;
            for (int t = 0; t < targetSize; t++) {
                int bitPos = numQubits - 1 - targetIndices[t];
                int bit = (idx >> bitPos) & 1;
                outcome |= (bit << (targetSize - 1 - t));
            }
            
            double probability = amplitudes[idx].absSquare();
            outcomeProbabilities[outcome] += probability;
        }
        
        // Perform measurements
        int[] outcomes = new int[shots];
        Map<Integer, Integer> outcomeCounts = new HashMap<>();
        for (int s = 0; s < shots; s++) {
            int outcome = sampleFromDistribution(outcomeProbabilities);
            outcomes[s] = outcome;
            outcomeCounts.put(outcome, outcomeCounts.getOrDefault(outcome, 0) + 1);
        }
        
        result.put("probabilities", outcomeProbabilities);
        result.put("outcomes", outcomes);
        result.put("counts", outcomeCounts);
        
        return result;
    }
    
    public static MeasurementResult measureSingleQubit(StateVector state, Qubit qubit, int shots) {
        return measureInComputationalBasis(state, new Qubit[]{qubit}, shots);
    }
    
    public static MeasurementResult measureAllQubits(StateVector state, int shots) {
        int numQubits = state.getNumQubits();
        Qubit[] allQubits = new Qubit[numQubits];
        for (int i = 0; i < numQubits; i++) {
            allQubits[i] = new Qubit(i);
        }
        return measureInComputationalBasis(state, allQubits, shots);
    }
    
    public static Map<String, Double> getProbabilities(StateVector state) {
        int numQubits = state.getNumQubits();
        Map<String, Double> probabilities = new HashMap<>();
        double[] probs = state.getProbabilities();
        
        for (int i = 0; i < probs.length; i++) {
            if (probs[i] > 1e-12) { // Only include significant probabilities
                String binary = String.format(Locale.ROOT, "%" + numQubits + "s", 
                    Integer.toBinaryString(i)).replace(' ', '0');
                probabilities.put(binary, probs[i]);
            }
        }
        
        return probabilities;
    }
    
    private static int sampleFromDistribution(double[] probabilities) {
        double rand = RANDOM.nextDouble();
        double cumulative = 0.0;
        
        for (int i = 0; i < probabilities.length; i++) {
            cumulative += probabilities[i];
            if (rand <= cumulative + 1e-12) {
                return i;
            }
        }
        
        // Fallback in case of numerical errors
        return probabilities.length - 1;
    }
}