package example.algorithms;

import com.mechalikh.qforge.config.SimulationBackend;
import com.mechalikh.qforge.config.SimulationConfig;
import com.mechalikh.qforge.core.QuantumSimulator;
import com.mechalikh.qforge.core.SimulationResult;
import com.mechalikh.qforge.events.QuantumEvent;
import com.mechalikh.qforge.operations.MultiQubitGate;
import com.mechalikh.qforge.operations.SingleQubitGate;
import com.mechalikh.qforge.state.Qubit;
import com.mechalikh.qforge.state.StateVector;

import example.EventBuilderHelper;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Runs Grover style amplitude amplification for a fixed set of marked basis states.
 *
 * <p>For {@code k} marked items out of {@code N = 2^n}, the oracle is a product of marking
 * oracles, one per marked item. Each marking oracle applies Pauli X gates to the qubits that
 * must be zero, a multi-controlled Z gate on the whole register, and the same X gates in
 * reverse. The diffusion operator is {@code H^n X^n MCZ X^n H^n}, the inversion about the
 * uniform superposition. The number of iterations is exactly
 * {@code floor(pi / 4 * sqrt(N / k))}, without a manual correction.</p>
 *
 * <p>The sign of every amplitude is checked after the oracle and before any diffusion, so the
 * output states which items the oracle marked rather than assuming it. The measured success
 * probability is compared with {@code sin^2((2m + 1) theta)}, where
 * {@code theta = asin(sqrt(k / N))} and {@code m} is the iteration count.</p>
 *
 * <p>The limitation is that the marked set is fixed and small, the register is four qubits, and
 * the multi-controlled Z gate is applied as a dense matrix by the engine. The numbers below are
 * exact state vector probabilities, not sampled estimates.</p>
 */
public final class AmplitudeAmplificationComplete {

    private static final int NUM_QUBITS = 4;
    private static final int SEARCH_SPACE = 1 << NUM_QUBITS;

    private AmplitudeAmplificationComplete() {
    }

    /**
     * Runs the sign check and the amplification for one, two, and three marked items and prints
     * the measured and analytic success probabilities.
     *
     * @param args ignored
     * @throws Exception if a simulation fails
     */
    public static void main(String[] args) throws Exception {
        System.out.println("=== Amplitude amplification for k marked items in N = "
            + SEARCH_SPACE + " ===");

        int[][] markedSets = {
            {5},
            {3, 12},
            {1, 7, 14}
        };

        for (int[] marked : markedSets) {
            int k = marked.length;
            int iterations = optimalIterations(NUM_QUBITS, k);
            double theta = Math.asin(Math.sqrt(k / (double) SEARCH_SPACE));
            double analytic = Math.pow(Math.sin((2 * iterations + 1) * theta), 2);

            System.out.printf(Locale.ROOT, "%nMarked set: %s (k = %d)%n",
                formatSet(marked), k);
            System.out.printf(Locale.ROOT,
                "  optimal iterations m = floor(pi/4 * sqrt(N/k)) = %d%n", iterations);
            System.out.printf(Locale.ROOT, "  theta = asin(sqrt(k/N)) = %.9f%n", theta);

            SignCheck signCheck = checkOracleSigns(NUM_QUBITS, marked);
            System.out.printf(Locale.ROOT,
                "  oracle sign check before diffusion: %d marked amplitudes negative, "
                    + "%d unmarked amplitudes positive, max magnitude error %.2e, %s%n",
                signCheck.negativeMarked, signCheck.positiveUnmarked,
                signCheck.maxMagnitudeError, signCheck.passed ? "PASS" : "FAIL");

            AmplificationResult result = amplify(NUM_QUBITS, marked, iterations);
            System.out.printf(Locale.ROOT, "  measured success probability = %.9f%n",
                result.successProbability);
            System.out.printf(Locale.ROOT,
                "  analytic sin^2((2m+1)theta) = %.9f%n", analytic);
            System.out.printf(Locale.ROOT, "  absolute error = %.2e%n",
                Math.abs(result.successProbability - analytic));
            System.out.printf(Locale.ROOT, "  probability of each marked item: ");
            for (int i = 0; i < marked.length; i++) {
                System.out.printf(Locale.ROOT, "%s|%s> = %.6f",
                    i == 0 ? "" : ", ", binary(marked[i], NUM_QUBITS),
                    result.markedProbabilities[i]);
            }
            System.out.println();
        }

        System.out.println();
        System.out.println("Limitation: the register is four qubits, the marked sets are fixed, "
            + "and each multi-controlled Z gate is applied as a dense matrix.");
    }

    /**
     * Returns {@code floor(pi / 4 * sqrt(N / k))}, the usual optimal iteration count.
     *
     * @param numQubits the register width, at least one
     * @param markedCount the number of marked items, from one to {@code 2^numQubits}
     * @return the iteration count, truncated toward zero
     * @throws IllegalArgumentException if the marked count is outside the search space
     */
    public static int optimalIterations(int numQubits, int markedCount) {
        if (numQubits < 1) {
            throw new IllegalArgumentException("The register needs at least one qubit");
        }
        int searchSpace = 1 << numQubits;
        if (markedCount < 1 || markedCount > searchSpace) {
            throw new IllegalArgumentException("The marked count must be in [1, 2^n]");
        }
        return (int) Math.floor(Math.PI / 4.0
            * Math.sqrt(searchSpace / (double) markedCount));
    }

    /**
     * Builds and runs the amplification circuit and returns the exact marked probability.
     *
     * @param numQubits the register width
     * @param marked the marked basis states, distinct and inside the register
     * @param iterations the number of oracle and diffusion repetitions
     * @return the measured probabilities of the marked items
     * @throws Exception if a simulation fails
     */
    public static AmplificationResult amplify(int numQubits, int[] marked, int iterations)
            throws Exception {
        validateMarked(numQubits, marked);
        Qubit[] qubits = makeQubits(numQubits);
        List<QuantumEvent> circuit = new ArrayList<>();
        for (int qubit = 0; qubit < numQubits; qubit++) {
            circuit.add(EventBuilderHelper.createSingleQubitGate(
                "Init_H" + qubit, SingleQubitGate.createHadamard(qubits[qubit]), qubits[qubit]));
        }
        for (int iteration = 0; iteration < iterations; iteration++) {
            circuit.addAll(oracleEvents(qubits, marked, "Oracle_" + iteration));
            circuit.addAll(diffusionEvents(qubits, "Diffusion_" + iteration));
        }
        StateVector state = runCircuit(numQubits, circuit);
        double[] markedProbabilities = new double[marked.length];
        double success = 0.0;
        for (int i = 0; i < marked.length; i++) {
            markedProbabilities[i] = state.getProbability(marked[i]);
            success += markedProbabilities[i];
        }
        return new AmplificationResult(iterations, success, markedProbabilities);
    }

    /**
     * Applies the initialization and the oracle once, then checks the sign of every amplitude.
     *
     * @param numQubits the register width
     * @param marked the marked basis states, distinct and inside the register
     * @return the counts of correctly signed amplitudes and the largest magnitude error
     * @throws Exception if a simulation fails
     */
    public static SignCheck checkOracleSigns(int numQubits, int[] marked) throws Exception {
        validateMarked(numQubits, marked);
        Qubit[] qubits = makeQubits(numQubits);
        List<QuantumEvent> circuit = new ArrayList<>();
        for (int qubit = 0; qubit < numQubits; qubit++) {
            circuit.add(EventBuilderHelper.createSingleQubitGate(
                "Init_H" + qubit, SingleQubitGate.createHadamard(qubits[qubit]), qubits[qubit]));
        }
        circuit.addAll(oracleEvents(qubits, marked, "SignOracle"));
        StateVector state = runCircuit(numQubits, circuit);

        Set<Integer> markedSet = new LinkedHashSet<>();
        for (int item : marked) {
            markedSet.add(item);
        }
        double expectedMagnitude = 1.0 / Math.sqrt(1 << numQubits);
        double maxMagnitudeError = 0.0;
        int negativeMarked = 0;
        int positiveUnmarked = 0;
        for (int basis = 0; basis < (1 << numQubits); basis++) {
            double real = state.getAmplitude(basis).real();
            maxMagnitudeError = Math.max(maxMagnitudeError,
                Math.abs(Math.abs(real) - expectedMagnitude));
            if (markedSet.contains(basis) && real < 0.0) {
                negativeMarked++;
            }
            if (!markedSet.contains(basis) && real > 0.0) {
                positiveUnmarked++;
            }
        }
        boolean passed = negativeMarked == marked.length
            && positiveUnmarked == (1 << numQubits) - marked.length
            && maxMagnitudeError < 1e-12;
        return new SignCheck(negativeMarked, positiveUnmarked, maxMagnitudeError, passed);
    }

    private static List<QuantumEvent> oracleEvents(
            Qubit[] qubits, int[] marked, String name) {
        List<QuantumEvent> events = new ArrayList<>();
        for (int item : marked) {
            for (int qubit = 0; qubit < qubits.length; qubit++) {
                int bit = (item >> (qubits.length - 1 - qubit)) & 1;
                if (bit == 0) {
                    events.add(EventBuilderHelper.createSingleQubitGate(
                        name + "_X" + qubit, SingleQubitGate.createPauliX(qubits[qubit]),
                        qubits[qubit]));
                }
            }
            MultiQubitGate multiControlledZ = MultiQubitGate.createMultiControlledZ(qubits);
            events.add(EventBuilderHelper.createEvent(
                name + "_MCZ", multiControlledZ, qubits, new Qubit[0]));
            for (int qubit = 0; qubit < qubits.length; qubit++) {
                int bit = (item >> (qubits.length - 1 - qubit)) & 1;
                if (bit == 0) {
                    events.add(EventBuilderHelper.createSingleQubitGate(
                        name + "_Xundo" + qubit, SingleQubitGate.createPauliX(qubits[qubit]),
                        qubits[qubit]));
                }
            }
        }
        return events;
    }

    private static List<QuantumEvent> diffusionEvents(Qubit[] qubits, String name) {
        List<QuantumEvent> events = new ArrayList<>();
        int n = qubits.length;
        for (int qubit = 0; qubit < n; qubit++) {
            events.add(EventBuilderHelper.createSingleQubitGate(
                name + "_H" + qubit, SingleQubitGate.createHadamard(qubits[qubit]),
                qubits[qubit]));
        }
        for (int qubit = 0; qubit < n; qubit++) {
            events.add(EventBuilderHelper.createSingleQubitGate(
                name + "_X" + qubit, SingleQubitGate.createPauliX(qubits[qubit]),
                qubits[qubit]));
        }
        MultiQubitGate multiControlledZ = MultiQubitGate.createMultiControlledZ(qubits);
        events.add(EventBuilderHelper.createEvent(
            name + "_MCZ", multiControlledZ, qubits, new Qubit[0]));
        for (int qubit = 0; qubit < n; qubit++) {
            events.add(EventBuilderHelper.createSingleQubitGate(
                name + "_Xundo" + qubit, SingleQubitGate.createPauliX(qubits[qubit]),
                qubits[qubit]));
        }
        for (int qubit = 0; qubit < n; qubit++) {
            events.add(EventBuilderHelper.createSingleQubitGate(
                name + "_Hundo" + qubit, SingleQubitGate.createHadamard(qubits[qubit]),
                qubits[qubit]));
        }
        return events;
    }

    private static void validateMarked(int numQubits, int[] marked) {
        if (numQubits < 2) {
            throw new IllegalArgumentException("The multi-controlled Z oracle needs two qubits");
        }
        Set<Integer> seen = new LinkedHashSet<>();
        for (int item : marked) {
            if (item < 0 || item >= (1 << numQubits)) {
                throw new IllegalArgumentException("Marked state out of range: " + item);
            }
            if (!seen.add(item)) {
                throw new IllegalArgumentException("Duplicate marked state: " + item);
            }
        }
    }

    private static Qubit[] makeQubits(int numQubits) {
        Qubit[] qubits = new Qubit[numQubits];
        for (int i = 0; i < numQubits; i++) {
            qubits[i] = new Qubit(i, "q" + i);
        }
        return qubits;
    }

    private static StateVector runCircuit(int numQubits, List<QuantumEvent> events)
            throws Exception {
        SimulationConfig config = new SimulationConfig();
        config.setNumQubits(numQubits);
        config.setBackend(SimulationBackend.STATE_VECTOR);
        QuantumSimulator simulator = new QuantumSimulator();
        simulator.initialize(config);
        for (QuantumEvent event : events) {
            simulator.scheduleEvent(event);
        }
        SimulationResult result = simulator.run();
        return (StateVector) result.getFinalState();
    }

    private static String binary(int value, int width) {
        String bits = Integer.toBinaryString(value);
        while (bits.length() < width) {
            bits = "0" + bits;
        }
        return bits;
    }

    private static String formatSet(int[] values) {
        StringBuilder builder = new StringBuilder("{");
        for (int i = 0; i < values.length; i++) {
            if (i > 0) {
                builder.append(", ");
            }
            builder.append(values[i]);
        }
        return builder.append('}').toString();
    }

    /**
     * Holds the exact measured probability of an amplification run.
     */
    public static final class AmplificationResult {

        private final int iterations;
        private final double successProbability;
        private final double[] markedProbabilities;

        private AmplificationResult(int iterations, double successProbability,
                double[] markedProbabilities) {
            this.iterations = iterations;
            this.successProbability = successProbability;
            this.markedProbabilities = markedProbabilities;
        }

        /**
         * Returns the number of oracle and diffusion repetitions.
         *
         * @return the iteration count
         */
        public int getIterations() {
            return iterations;
        }

        /**
         * Returns the sum of the probabilities of the marked states.
         *
         * @return the success probability
         */
        public double getSuccessProbability() {
            return successProbability;
        }

        /**
         * Returns the probability of every marked state, in the supplied order.
         *
         * @return a copy of the marked probabilities
         */
        public double[] getMarkedProbabilities() {
            return markedProbabilities.clone();
        }
    }

    /**
     * Holds the result of the sign check performed after the oracle.
     */
    public static final class SignCheck {

        private final int negativeMarked;
        private final int positiveUnmarked;
        private final double maxMagnitudeError;
        private final boolean passed;

        private SignCheck(int negativeMarked, int positiveUnmarked,
                double maxMagnitudeError, boolean passed) {
            this.negativeMarked = negativeMarked;
            this.positiveUnmarked = positiveUnmarked;
            this.maxMagnitudeError = maxMagnitudeError;
            this.passed = passed;
        }

        /**
         * Returns how many marked amplitudes are negative.
         *
         * @return the count of negative marked amplitudes
         */
        public int getNegativeMarked() {
            return negativeMarked;
        }

        /**
         * Returns how many unmarked amplitudes are positive.
         *
         * @return the count of positive unmarked amplitudes
         */
        public int getPositiveUnmarked() {
            return positiveUnmarked;
        }

        /**
         * Returns the largest deviation of an amplitude magnitude from {@code 1 / sqrt(N)}.
         *
         * @return the maximum magnitude error
         */
        public double getMaxMagnitudeError() {
            return maxMagnitudeError;
        }

        /**
         * Reports whether the sign pattern matched the marked set exactly.
         *
         * @return true when the oracle marked exactly the requested items
         */
        public boolean isPassed() {
            return passed;
        }
    }
}
