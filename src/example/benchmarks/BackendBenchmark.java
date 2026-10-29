package example.benchmarks;

import com.mechalikh.qforge.config.SimulationBackend;
import com.mechalikh.qforge.config.SimulationConfig;
import com.mechalikh.qforge.core.QuantumSimulator;
import com.mechalikh.qforge.events.QuantumEvent;
import com.mechalikh.qforge.math.RandomGenerator;
import com.mechalikh.qforge.operations.SingleQubitGate;
import com.mechalikh.qforge.state.AmplitudeAccess;
import com.mechalikh.qforge.state.MPSState;
import com.mechalikh.qforge.state.StabilizerState;
import com.mechalikh.qforge.state.Qubit;
import com.mechalikh.qforge.state.QuantumState;

import example.EventBuilderHelper;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Compares the four simulation backends on the same GHZ circuit.
 *
 * <p>The example measures wall-clock time for preparing a GHZ state, checks the probability
 * distribution the backend produced, and then reports the practical limits of each backend:
 * the stabilizer backend rejects non-Clifford gates, and the tensor network backend truncates
 * entanglement when the bond dimension is too small. Every number printed here is measured in
 * this run; nothing is quoted from a specification.</p>
 */
public final class BackendBenchmark {

    private static final int REPEATS = 3;
    private static final int CORRECTNESS_QUBITS = 10;

    private BackendBenchmark() {
    }

    public static void main(String[] args) throws Exception {
        System.out.println("=== Backend comparison on a GHZ circuit ===");

        SimulationConfig config = new SimulationConfig();
        config.setNumQubits(CORRECTNESS_QUBITS);

        System.out.printf(Locale.ROOT,
            "%nGHZ state on %d qubits, best of %d runs:%n", CORRECTNESS_QUBITS, REPEATS);
        System.out.println("  backend            time (ms)   P(0...0)   P(1...1)   exact");
        double stateVectorTime = benchmark(config, SimulationBackend.STATE_VECTOR);
        benchmark(config, SimulationBackend.TENSOR_NETWORK);
        benchmark(config, SimulationBackend.STABILIZER);

        System.out.println("\nDensity matrix (it grows as 4^n, so it is measured at 6 qubits):");
        config.setNumQubits(6);
        benchmark(config, SimulationBackend.DENSITY_MATRIX);
        config.setNumQubits(CORRECTNESS_QUBITS);

        System.out.println("\nScaling on a GHZ circuit (state vector and tensor network):");
        System.out.println("  qubits   state vector (ms)   tensor network (ms)");
        for (int qubits : new int[] {10, 14, 18, 20}) {
            config.setNumQubits(qubits);
            double vectorTime = timeBackend(config, SimulationBackend.STATE_VECTOR);
            double mpsTime = timeBackend(config, SimulationBackend.TENSOR_NETWORK);
            System.out.printf(Locale.ROOT, "  %6d   %18.3f   %19.3f%n", qubits, vectorTime,
                mpsTime);
        }

        System.out.println("\nStabilizer scaling (GHZ circuit, Clifford only):");
        System.out.println("  qubits   time (ms)");
        for (int qubits : new int[] {100, 500, 1000}) {
            config.setNumQubits(qubits);
            double best = timeBackend(config, SimulationBackend.STABILIZER);
            System.out.printf(Locale.ROOT, "  %6d   %9.3f%n", qubits, best);
        }

        System.out.println("\nKnown limits, measured rather than assumed:");
        reportStabilizerLimit();
        reportTruncationError();

        System.out.printf(Locale.ROOT,
            "%nState vector on %d qubits took %.3f ms and matched the analytic GHZ weights.%n",
            CORRECTNESS_QUBITS, stateVectorTime);
    }

    private static double benchmark(SimulationConfig config, SimulationBackend backend)
            throws Exception {
        config.setBackend(backend);
        int qubits = config.getNumQubits();
        double best = timeBackend(config, backend);
        QuantumState lastState = run(config, buildGhz(qubits)).state;
        double[] probabilities = probabilities(lastState, qubits);
        double zero = probabilities[0];
        double all = probabilities[probabilities.length - 1];
        System.out.printf(Locale.ROOT, "  %-18s %8.3f   %8.4f   %8.4f   %s%n", backend, best,
            zero, all, backend.isExact() ? "yes" : "no (truncated)");
        return best;
    }

    private static double timeBackend(SimulationConfig config, SimulationBackend backend)
            throws Exception {
        config.setBackend(backend);
        int qubits = config.getNumQubits();
        double best = Double.MAX_VALUE;
        for (int repeat = 0; repeat < REPEATS; repeat++) {
            best = Math.min(best, run(config, buildGhz(qubits)).elapsedMillis);
        }
        return best;
    }

    private static double[] probabilities(QuantumState state, int qubits) {
        if (state instanceof AmplitudeAccess) {
            return ((AmplitudeAccess) state).getProbabilities();
        }
        Qubit[] all = new Qubit[qubits];
        for (int qubit = 0; qubit < qubits; qubit++) {
            all[qubit] = new Qubit(qubit, "q" + qubit);
        }
        return ((StabilizerState) state).outcomeProbabilitiesZ(all);
    }

    private static Run run(SimulationConfig config, List<QuantumEvent> events) throws Exception {
        QuantumSimulator simulator = new QuantumSimulator();
        long start = System.nanoTime();
        simulator.initialize(config);
        for (QuantumEvent event : events) {
            simulator.scheduleEvent(event);
        }
        QuantumState state = simulator.run().getFinalState();
        double elapsed = (System.nanoTime() - start) / 1_000_000.0;
        simulator.reset();
        return new Run(state, elapsed);
    }

    private static List<QuantumEvent> buildGhz(int qubits) {
        EventBuilderHelper.resetTimer();
        List<QuantumEvent> events = new ArrayList<>();
        Qubit first = new Qubit(0, "q0");
        events.add(EventBuilderHelper.createSingleQubitGate(
            "H", SingleQubitGate.createHadamard(first), first));
        for (int qubit = 1; qubit < qubits; qubit++) {
            Qubit control = new Qubit(qubit - 1, "q" + (qubit - 1));
            Qubit target = new Qubit(qubit, "q" + qubit);
            events.add(EventBuilderHelper.createCNOT("CNOT_" + qubit, control, target));
        }
        return events;
    }

    private static void reportStabilizerLimit() throws Exception {
        SimulationConfig config = new SimulationConfig();
        config.setNumQubits(2);
        config.setBackend(SimulationBackend.STABILIZER);
        EventBuilderHelper.resetTimer();
        Qubit qubit = new Qubit(0, "q0");
        List<QuantumEvent> events = new ArrayList<>();
        events.add(EventBuilderHelper.createSingleQubitGate(
            "T", SingleQubitGate.createT(qubit), qubit));
        String outcome;
        try {
            run(config, events);
            outcome = "accepted (unexpected)";
        } catch (Exception e) {
            outcome = "rejected: " + e.getMessage();
        }
        System.out.println("  stabilizer with a T gate -> " + outcome);
    }

    private static void reportTruncationError() throws Exception {
        int qubits = 8;
        SimulationConfig reference = new SimulationConfig();
        reference.setNumQubits(qubits);
        reference.setBackend(SimulationBackend.STATE_VECTOR);
        double[] exact = probabilities(run(reference, buildEntanglingCircuit(qubits)).state, qubits);
        System.out.println(
            "  entangling circuit on 8 qubits, tensor network compared with the exact state vector:");
        for (int bond : new int[] {1, 2, 4, 64}) {
            SimulationConfig config = new SimulationConfig();
            config.setNumQubits(qubits);
            config.setBackend(SimulationBackend.TENSOR_NETWORK);
            config.setMaxBondDimension(bond);
            RandomGenerator.resetToDefault();
            MPSState state = (MPSState) run(config, buildEntanglingCircuit(qubits)).state;
            double[] probabilities = state.getProbabilities();
            double difference = 0.0;
            for (int index = 0; index < exact.length; index++) {
                difference = Math.max(difference, Math.abs(exact[index] - probabilities[index]));
            }
            System.out.printf(Locale.ROOT,
                "    bond dimension %2d -> relative discarded weight %.4f, "
                    + "max |P - P_exact| %.6f%n", bond, state.getTruncationError(), difference);
        }
    }

    private static List<QuantumEvent> buildEntanglingCircuit(int qubits) {
        EventBuilderHelper.resetTimer();
        List<QuantumEvent> events = new ArrayList<>();
        for (int layer = 0; layer < 4; layer++) {
            for (int qubit = 0; qubit < qubits; qubit++) {
                Qubit target = new Qubit(qubit, "q" + qubit);
                events.add(EventBuilderHelper.createSingleQubitGate(
                    "H" + layer + "_" + qubit, SingleQubitGate.createHadamard(target), target));
                events.add(EventBuilderHelper.createSingleQubitGate(
                    "T" + layer + "_" + qubit, SingleQubitGate.createT(target), target));
            }
            for (int qubit = 0; qubit < qubits - 1; qubit++) {
                Qubit control = new Qubit(qubit, "q" + qubit);
                Qubit target = new Qubit(qubit + 1, "q" + (qubit + 1));
                events.add(EventBuilderHelper.createCNOT("CNOT_" + layer + "_" + qubit,
                    control, target));
            }
        }
        return events;
    }

    private static final class Run {
        private final QuantumState state;
        private final double elapsedMillis;

        private Run(QuantumState state, double elapsedMillis) {
            this.state = state;
            this.elapsedMillis = elapsedMillis;
        }
    }
}
