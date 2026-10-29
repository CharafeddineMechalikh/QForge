package example.algorithms;

import com.mechalikh.qforge.config.SimulationBackend;
import com.mechalikh.qforge.config.SimulationConfig;
import com.mechalikh.qforge.core.QuantumSimulator;
import com.mechalikh.qforge.core.SimulationResult;
import com.mechalikh.qforge.events.QuantumEvent;
import com.mechalikh.qforge.measurement.MeasurementOperator;
import com.mechalikh.qforge.measurement.MeasurementResult;
import com.mechalikh.qforge.operations.SingleQubitGate;
import com.mechalikh.qforge.state.Qubit;
import com.mechalikh.qforge.state.StateVector;

import example.EventBuilderHelper;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.apache.commons.math3.optim.InitialGuess;
import org.apache.commons.math3.optim.MaxEval;
import org.apache.commons.math3.optim.PointValuePair;
import org.apache.commons.math3.optim.SimpleValueChecker;
import org.apache.commons.math3.optim.nonlinear.scalar.GoalType;
import org.apache.commons.math3.optim.nonlinear.scalar.ObjectiveFunction;
import org.apache.commons.math3.optim.nonlinear.scalar.noderiv.NelderMeadSimplex;
import org.apache.commons.math3.optim.nonlinear.scalar.noderiv.SimplexOptimizer;

/**
 * Solves Max-Cut on a fixed five node weighted graph with QAOA at depth one and depth two.
 *
 * <p>The problem Hamiltonian is {@code H_C = sum w_ij Z_i Z_j} over the graph edges. The
 * circuit prepares the plus state, applies {@code exp(-i gamma H_C)} as a CNOT, an RZ rotation
 * by {@code 2 gamma w_ij}, and a second CNOT for every edge, then applies the mixer
 * {@code exp(-i beta H_B)} as an RX rotation by {@code 2 beta} on every qubit. The objective
 * optimized here is the exact expectation value of the cut computed from the state vector
 * probabilities, so no sampling noise enters the optimizer. The reported sample fraction is a
 * separate 2000 shot measurement at the optimized angles.</p>
 *
 * <p>The limitation is the problem size. Five nodes give a 32 dimensional state space, the
 * brute force cut is evaluated over all {@code 2^5} assignments, and the optimizer is a local
 * Nelder-Mead simplex. Depth two is not guaranteed to beat depth one for this optimizer and the
 * output reports the comparison rather than hiding it.</p>
 */
public final class QaoaMaxCut {

    /** The number of nodes in the fixed graph. */
    public static final int NUM_NODES = 5;

    private static final int[][] EDGES = {
        {0, 1},
        {1, 2},
        {2, 3},
        {3, 4},
        {4, 0},
        {0, 2}
    };

    private static final double[] WEIGHTS = {1.0, 1.0, 1.0, 1.0, 1.0, 0.5};

    private static final int SHOTS = 2000;

    private QaoaMaxCut() {
    }

    /**
     * Runs the depth one and depth two QAOA optimizations, samples the optimized circuits, and
     * prints the comparison with the brute force optimum.
     *
     * @param args ignored
     * @throws Exception if a simulation fails
     */
    public static void main(String[] args) throws Exception {
        System.out.println("=== QAOA for Max-Cut on a fixed five node graph ===");
        System.out.println("Nodes: 0, 1, 2, 3, 4");
        System.out.print("Weighted edges:");
        for (int i = 0; i < EDGES.length; i++) {
            System.out.printf(Locale.ROOT, " (%d,%d,%.1f)", EDGES[i][0], EDGES[i][1], WEIGHTS[i]);
        }
        System.out.println();

        BruteForceResult bruteForce = bruteForceMaxCut();
        System.out.printf(Locale.ROOT, "Exact maximum cut: %.4f%n", bruteForce.maxCut);
        System.out.println("Optimal assignments and cut values:");
        for (int i = 0; i < bruteForce.optimalAssignments.size(); i++) {
            int assignment = bruteForce.optimalAssignments.get(i);
            System.out.printf(Locale.ROOT, "  bits %s (decimal %d): cut %.4f%n",
                binary(assignment), assignment, bruteForce.maxCut);
        }

        double[] depthOneStart = {0.5, 0.5};
        QaoaResult depthOne = optimize(1, depthOneStart);
        QaoaResult depthTwo = optimize(2, new double[]{
            depthOne.gamma[0], depthOne.beta[0], 0.0, 0.0});

        printResult(depthOne, bruteForce.maxCut);
        printResult(depthTwo, bruteForce.maxCut);

        if (depthTwo.expectedCut + 1e-9 < depthOne.expectedCut) {
            System.out.printf(Locale.ROOT,
                "NOTE: depth 2 ended below depth 1: %.6f < %.6f. The local optimizer did not "
                    + "improve the warm start.%n",
                depthTwo.expectedCut, depthOne.expectedCut);
        } else {
            System.out.printf(Locale.ROOT,
                "Depth 2 is not worse than depth 1: %.6f >= %.6f.%n",
                depthTwo.expectedCut, depthOne.expectedCut);
        }
    }

    /**
     * Optimizes the QAOA angles of the given depth with a Nelder-Mead simplex.
     *
     * <p>The objective is the negative of the exact expected cut, so the optimizer minimizes it.
     * The angle vector is ordered as {@code gamma_0, beta_0, gamma_1, beta_1, ...}. The initial
     * simplex uses a step of 0.25 on every coordinate.</p>
     *
     * @param depth the number of QAOA layers, at least one
     * @param initialAngles the starting angles, length {@code 2 * depth}
     * @return the optimized angles and their exact expected cut
     * @throws IllegalArgumentException if the depth is not positive or the angle array has the
     *     wrong length
     * @throws Exception if a simulation fails
     */
    public static QaoaResult optimize(int depth, double[] initialAngles) throws Exception {
        if (depth < 1) {
            throw new IllegalArgumentException("Depth must be positive");
        }
        if (initialAngles.length != 2 * depth) {
            throw new IllegalArgumentException("Expected " + (2 * depth) + " angles");
        }

        SimplexOptimizer optimizer = new SimplexOptimizer(new SimpleValueChecker(1e-10, 1e-10));
        PointValuePair optimum = optimizer.optimize(
            new MaxEval(4000),
            new InitialGuess(initialAngles.clone()),
            new ObjectiveFunction(angles -> {
                try {
                    return -expectedCut(angles, depth);
                } catch (Exception exception) {
                    throw new IllegalStateException("QAOA objective failed", exception);
                }
            }),
            GoalType.MINIMIZE,
            new NelderMeadSimplex(2 * depth));

        double[] angles = optimum.getPoint();
        double[] gamma = new double[depth];
        double[] beta = new double[depth];
        for (int layer = 0; layer < depth; layer++) {
            gamma[layer] = angles[2 * layer];
            beta[layer] = angles[2 * layer + 1];
        }
        return new QaoaResult(depth, gamma, beta, -optimum.getValue());
    }

    /**
     * Computes the exact expected cut of the QAOA state at the given angles.
     *
     * @param angles the angle vector ordered as {@code gamma_0, beta_0, gamma_1, beta_1, ...}
     * @param depth the number of QAOA layers
     * @return the expected cut weight, between zero and the sum of the edge weights
     * @throws Exception if a simulation fails
     */
    public static double expectedCut(double[] angles, int depth) throws Exception {
        StateVector state = runQaoa(angles, depth);
        double[] probabilities = state.getProbabilities();
        double expected = 0.0;
        for (int basis = 0; basis < probabilities.length; basis++) {
            if (probabilities[basis] == 0.0) {
                continue;
            }
            for (int edge = 0; edge < EDGES.length; edge++) {
                int first = (basis >> (NUM_NODES - 1 - EDGES[edge][0])) & 1;
                int second = (basis >> (NUM_NODES - 1 - EDGES[edge][1])) & 1;
                if (first != second) {
                    expected += WEIGHTS[edge] * probabilities[basis];
                }
            }
        }
        return expected;
    }

    /**
     * Runs the QAOA circuit and returns its final state vector.
     *
     * @param angles the angle vector ordered as {@code gamma_0, beta_0, gamma_1, beta_1, ...}
     * @param depth the number of QAOA layers
     * @return the final state vector of the five qubit register
     * @throws Exception if the simulation fails
     */
    public static StateVector runQaoa(double[] angles, int depth) throws Exception {
        Qubit[] qubits = new Qubit[NUM_NODES];
        for (int i = 0; i < NUM_NODES; i++) {
            qubits[i] = new Qubit(i, "q" + i);
        }
        SimulationConfig config = new SimulationConfig();
        config.setNumQubits(NUM_NODES);
        config.setBackend(SimulationBackend.STATE_VECTOR);

        QuantumSimulator simulator = new QuantumSimulator();
        simulator.initialize(config);
        for (QuantumEvent event : buildCircuit(qubits, angles, depth)) {
            simulator.scheduleEvent(event);
        }
        SimulationResult result = simulator.run();
        return (StateVector) result.getFinalState();
    }

    /**
     * Evaluates the weight of every one of the {@code 2^5} cuts and collects the best ones.
     *
     * @return the maximum cut weight and every assignment that reaches it
     */
    public static BruteForceResult bruteForceMaxCut() {
        double best = Double.NEGATIVE_INFINITY;
        List<Integer> assignments = new ArrayList<>();
        for (int assignment = 0; assignment < (1 << NUM_NODES); assignment++) {
            double cut = cutWeight(assignment);
            if (cut > best + 1e-12) {
                best = cut;
                assignments.clear();
                assignments.add(assignment);
            } else if (Math.abs(cut - best) <= 1e-12) {
                assignments.add(assignment);
            }
        }
        return new BruteForceResult(best, assignments);
    }

    private static double cutWeight(int assignment) {
        double cut = 0.0;
        for (int edge = 0; edge < EDGES.length; edge++) {
            int first = (assignment >> (NUM_NODES - 1 - EDGES[edge][0])) & 1;
            int second = (assignment >> (NUM_NODES - 1 - EDGES[edge][1])) & 1;
            if (first != second) {
                cut += WEIGHTS[edge];
            }
        }
        return cut;
    }

    private static void printResult(QaoaResult result, double maxCut) throws Exception {
        System.out.printf(Locale.ROOT, "%nDepth p = %d%n", result.depth);
        System.out.print("  gamma = [");
        for (int i = 0; i < result.gamma.length; i++) {
            System.out.printf(Locale.ROOT, "%s%.6f", i == 0 ? "" : ", ", result.gamma[i]);
        }
        System.out.println("]");
        System.out.print("  beta  = [");
        for (int i = 0; i < result.beta.length; i++) {
            System.out.printf(Locale.ROOT, "%s%.6f", i == 0 ? "" : ", ", result.beta[i]);
        }
        System.out.println("]");
        System.out.printf(Locale.ROOT, "  optimized expected cut = %.6f%n", result.expectedCut);
        System.out.printf(Locale.ROOT, "  approximation ratio = %.6f%n",
            result.expectedCut / maxCut);

        StateVector state = runQaoa(interleave(result), result.depth);
        Qubit[] qubits = new Qubit[NUM_NODES];
        for (int i = 0; i < NUM_NODES; i++) {
            qubits[i] = new Qubit(i, "q" + i);
        }
        MeasurementOperator measurement = MeasurementOperator.computationalBasis(qubits, SHOTS);
        MeasurementResult sampled = measurement.measure(state);
        Map<Integer, Integer> counts = sampled.getOutcomeCounts();
        int optimalShots = 0;
        for (Map.Entry<Integer, Integer> entry : counts.entrySet()) {
            if (Math.abs(cutWeight(entry.getKey()) - maxCut) <= 1e-9) {
                optimalShots += entry.getValue();
            }
        }
        System.out.printf(Locale.ROOT,
            "  2000 shots: %d samples reached an optimal cut (fraction %.4f)%n",
            optimalShots, optimalShots / (double) SHOTS);
        int mostFrequent = sampled.getMostFrequentOutcome();
        System.out.printf(Locale.ROOT, "  most frequent outcome: bits %s with cut %.4f (%d shots)%n",
            binary(mostFrequent), cutWeight(mostFrequent), sampled.getCount(mostFrequent));
    }

    private static double[] interleave(QaoaResult result) {
        double[] angles = new double[2 * result.depth];
        for (int layer = 0; layer < result.depth; layer++) {
            angles[2 * layer] = result.gamma[layer];
            angles[2 * layer + 1] = result.beta[layer];
        }
        return angles;
    }

    private static QuantumEvent[] buildCircuit(Qubit[] qubits, double[] angles, int depth) {
        if (angles.length != 2 * depth) {
            throw new IllegalArgumentException("Angles do not match depth");
        }
        List<QuantumEvent> circuit = new ArrayList<>();
        for (int qubit = 0; qubit < qubits.length; qubit++) {
            circuit.add(EventBuilderHelper.createSingleQubitGate(
                "H_init" + qubit, SingleQubitGate.createHadamard(qubits[qubit]), qubits[qubit]));
        }
        for (int layer = 0; layer < depth; layer++) {
            double gamma = angles[2 * layer];
            double beta = angles[2 * layer + 1];
            for (int edge = 0; edge < EDGES.length; edge++) {
                Qubit first = qubits[EDGES[edge][0]];
                Qubit second = qubits[EDGES[edge][1]];
                circuit.add(EventBuilderHelper.createCNOT(
                    "P_CNOT1_" + layer + "_" + edge, first, second));
                circuit.add(EventBuilderHelper.createSingleQubitGate(
                    "P_RZ_" + layer + "_" + edge,
                    SingleQubitGate.createRZ(2.0 * gamma * WEIGHTS[edge], second), second));
                circuit.add(EventBuilderHelper.createCNOT(
                    "P_CNOT2_" + layer + "_" + edge, first, second));
            }
            for (int qubit = 0; qubit < qubits.length; qubit++) {
                circuit.add(EventBuilderHelper.createSingleQubitGate(
                    "M_RX_" + layer + "_" + qubit,
                    SingleQubitGate.createRX(2.0 * beta, qubits[qubit]), qubits[qubit]));
            }
        }
        return circuit.toArray(new QuantumEvent[0]);
    }

    private static String binary(int value) {
        String bits = Integer.toBinaryString(value);
        while (bits.length() < NUM_NODES) {
            bits = "0" + bits;
        }
        return bits;
    }

    /**
     * Holds the maximum cut weight and all assignments that reach it.
     */
    public static final class BruteForceResult {

        private final double maxCut;
        private final List<Integer> optimalAssignments;

        private BruteForceResult(double maxCut, List<Integer> optimalAssignments) {
            this.maxCut = maxCut;
            this.optimalAssignments = List.copyOf(optimalAssignments);
        }

        /**
         * Returns the maximum cut weight.
         *
         * @return the largest cut weight over all assignments
         */
        public double getMaxCut() {
            return maxCut;
        }

        /**
         * Returns every assignment that reaches the maximum cut.
         *
         * @return the optimal assignments, with the first qubit as the most significant bit
         */
        public List<Integer> getOptimalAssignments() {
            return optimalAssignments;
        }
    }

    /**
     * Holds the optimized angles and the exact expected cut at those angles.
     */
    public static final class QaoaResult {

        private final int depth;
        private final double[] gamma;
        private final double[] beta;
        private final double expectedCut;

        private QaoaResult(int depth, double[] gamma, double[] beta, double expectedCut) {
            this.depth = depth;
            this.gamma = gamma;
            this.beta = beta;
            this.expectedCut = expectedCut;
        }

        /**
         * Returns the QAOA depth that was optimized.
         *
         * @return the number of layers
         */
        public int getDepth() {
            return depth;
        }

        /**
         * Returns the optimized problem angles.
         *
         * @return one gamma per layer
         */
        public double[] getGamma() {
            return gamma.clone();
        }

        /**
         * Returns the optimized mixer angles.
         *
         * @return one beta per layer
         */
        public double[] getBeta() {
            return beta.clone();
        }

        /**
         * Returns the exact expected cut at the optimized angles.
         *
         * @return the expectation value of the cut weight
         */
        public double getExpectedCut() {
            return expectedCut;
        }
    }
}
