package com.quantum.simulator.measurement;

import com.quantum.simulator.math.Complex;
import com.quantum.simulator.state.QuantumState;
import com.quantum.simulator.state.StateVector;
import com.quantum.simulator.state.Qubit;
import java.util.*;

/**
 * Stateless convenience routines for sampling measurements in the computational basis.
 *
 * <p>The measurement methods take a pure {@link StateVector} and a list of target qubits, build
 * the marginal distribution over the {@code 2^n} bit patterns of those qubits by summing
 * {@code |amplitude|^2} over all remaining basis states, and then draw independent samples from
 * that distribution. The target qubits are read in the order they are supplied: the first qubit
 * in the array becomes the most significant bit of the reported outcome integer, and the last
 * qubit becomes the least significant bit.</p>
 *
 * <p>None of these methods changes the supplied state. In particular they do not collapse it,
 * so repeated calls on the same state sample the same distribution. Use
 * {@link MeasurementOperator#measureAndCollapse(com.quantum.simulator.state.QuantumState)} when
 * a collapsed state is required.</p>
 *
 * <p>All sampling in this class draws from one shared {@link Random} instance. Results are
 * therefore not reproducible between runs and are not seeded.</p>
 */
public class MeasurementHelper {
    private static final Random RANDOM = new Random();
    
    /**
     * Samples the computational basis measurement of the given target qubits.
     *
     * <p>The probability of an outcome {@code k} is the total probability of every basis state
     * whose target bits equal the binary form of {@code k}, with target qubit {@code i} mapped to
     * outcome bit {@code targetSize - 1 - i}. Each of the {@code shots} draws is independent and
     * uses that same distribution.</p>
     *
     * <p>The returned result is named {@code "ComputationalBasis"}. Its outcome counts record
     * how often each outcome integer was drawn, and its total shot count is {@code shots}. The
     * per-draw outcome array is not exposed by this method; use
     * {@link #measureInComputationalBasisWithProbabilities(StateVector, Qubit[], int)} when it is
     * needed. Eigenvalues of the result default to the outcome indices, so the reported
     * expectation value is the mean sampled outcome integer.</p>
     *
     * <p>An empty target array is accepted: the marginal distribution then has the single outcome
     * zero, which is drawn for every shot.</p>
     *
     * @param state the pure state to measure, which is left unchanged
     * @param qubits the target qubits, in significance order
     * @param shots the number of independent samples to draw; zero yields an empty result
     * @return the sampled outcome counts together with probabilities and statistics
     * @throws NullPointerException if {@code state}, {@code qubits}, or an element of
     *         {@code qubits} is {@code null}
     */
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
    /**
     * Samples the computational basis measurement and returns probabilities, outcomes and counts.
     *
     * <p>The sampling is identical to
     * {@link #measureInComputationalBasis(StateVector, Qubit[], int)}. The result map contains
     * three entries:</p>
     * <ul>
     *   <li>{@code "probabilities"}: a {@code double[]} of length {@code 2^n} holding the exact
     *       marginal probability of each outcome integer</li>
     *   <li>{@code "outcomes"}: an {@code int[]} of length {@code shots} holding the outcome drawn
     *       for each shot, in draw order</li>
     *   <li>{@code "counts"}: a {@code Map<Integer, Integer>} from outcome integer to the number
     *       of draws that produced it</li>
     * </ul>
     *
     * <p>The state is not modified and no collapse is performed. An empty target array is
     * accepted and produces the single outcome zero for every shot.</p>
     *
     * @param state the pure state to measure, which is left unchanged
     * @param qubits the target qubits, in significance order
     * @param shots the number of independent samples to draw
     * @return a mutable map with the keys {@code "probabilities"}, {@code "outcomes"} and
     *         {@code "counts"}
     * @throws NullPointerException if {@code state}, {@code qubits}, or an element of
     *         {@code qubits} is {@code null}
     */
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
    
    /**
     * Samples a single target qubit in the computational basis.
     *
     * <p>Equivalent to calling
     * {@link #measureInComputationalBasis(StateVector, Qubit[], int)} with a one element array,
     * so outcome {@code 0} means the qubit was read as {@code |0>} and outcome {@code 1} means it
     * was read as {@code |1>}. The state is not collapsed.</p>
     *
     * @param state the pure state to measure, which is left unchanged
     * @param qubit the target qubit
     * @param shots the number of independent samples to draw
     * @return the sampled result for that qubit
     * @throws NullPointerException if {@code state} or {@code qubit} is {@code null}
     */
    public static MeasurementResult measureSingleQubit(StateVector state, Qubit qubit, int shots) {
        return measureInComputationalBasis(state, new Qubit[]{qubit}, shots);
    }
    
    /**
     * Samples every qubit of the state in the computational basis.
     *
     * <p>Fresh {@link Qubit} instances are created for positions {@code 0} to
     * {@code numQubits - 1} and passed in ascending order, so qubit {@code 0} is the most
     * significant bit of each reported outcome integer. Outcomes range over
     * {@code 0} to {@code 2^n - 1}, matching the basis state indices of the state. The state is
     * not collapsed.</p>
     *
     * @param state the pure state to measure, which is left unchanged
     * @param shots the number of independent samples to draw
     * @return the sampled result over all qubits
     * @throws NullPointerException if {@code state} is {@code null}
     */
    public static MeasurementResult measureAllQubits(StateVector state, int shots) {
        int numQubits = state.getNumQubits();
        Qubit[] allQubits = new Qubit[numQubits];
        for (int i = 0; i < numQubits; i++) {
            allQubits[i] = new Qubit(i);
        }
        return measureInComputationalBasis(state, allQubits, shots);
    }
    
    /**
     * Returns the non-negligible computational basis probabilities of the state.
     *
     * <p>Each key is the zero padded binary form of a basis state index, using exactly
     * {@code numQubits} characters, so qubit {@code 0} is the leftmost character. Only basis
     * states whose probability is greater than {@code 1e-12} are included; the returned map is
     * therefore a sparse view and its values do not sum to one in general.</p>
     *
     * <p>The probabilities are read from {@link StateVector#getProbabilities()} and the state is
     * not modified.</p>
     *
     * @param state the pure state to inspect
     * @return a mutable map from bit string to probability, containing only significant entries
     * @throws NullPointerException if {@code state} is {@code null}
     */
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