package example.backends;

import com.mechalikh.qforge.config.SimulationBackend;
import com.mechalikh.qforge.config.SimulationConfig;
import com.mechalikh.qforge.core.QuantumSimulator;
import com.mechalikh.qforge.events.QuantumEvent;
import com.mechalikh.qforge.operations.MultiQubitGate;
import com.mechalikh.qforge.operations.QuantumOperation;
import com.mechalikh.qforge.operations.SingleQubitGate;
import com.mechalikh.qforge.state.AmplitudeAccess;
import com.mechalikh.qforge.state.QuantumState;
import com.mechalikh.qforge.state.Qubit;
import com.mechalikh.qforge.state.StabilizerState;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Exercises the stabilizer backend on a GHZ state that a state vector cannot hold.
 *
 * <p>The example prepares a 200-qubit GHZ state, draws 500 joint samples on five spread-out
 * qubits, and reports the timing. It then compares the exact three-qubit marginal with the
 * analytic 0.5 / 0.5 split, checks eight-qubit GHZ probabilities against the state vector
 * backend, and shows that a {@code T} gate is rejected because the tableau can only represent
 * Clifford operations.</p>
 *
 * <p>A 200-qubit state vector would need {@code 2^200} amplitudes, so the comparison against the
 * state vector is done at eight qubits. The large run is evidence of the scaling, not of an
 * approximation: the stabilizer backend is exact for this gate set.</p>
 */
public final class StabilizerExample {

    private static final int LARGE_QUBITS = 200;
    private static final int SAMPLE_SHOTS = 500;

    private StabilizerExample() {
    }

    public static void main(String[] args) throws Exception {
        System.out.println("=== Stabilizer backend ===");
        largeGhz();
        exactMarginal();
        crossCheckWithStateVector();
        nonCliffordGate();
    }

    private static void largeGhz() throws Exception {
        Qubit[] sampled = {
            new Qubit(0, "q0"), new Qubit(50, "q50"), new Qubit(100, "q100"),
            new Qubit(150, "q150"), new Qubit(199, "q199")
        };

        SimulationConfig warmup = new SimulationConfig();
        warmup.setNumQubits(20);
        warmup.setBackend(SimulationBackend.STABILIZER);
        run(warmup, ghzEvents(20));

        SimulationConfig config = new SimulationConfig();
        config.setNumQubits(LARGE_QUBITS);
        config.setBackend(SimulationBackend.STABILIZER);
        QuantumSimulator simulator = new QuantumSimulator();
        List<QuantumEvent> events = ghzEvents(LARGE_QUBITS);
        long start = System.nanoTime();
        simulator.initialize(config);
        for (QuantumEvent event : events) {
            simulator.scheduleEvent(event);
        }
        StabilizerState state = (StabilizerState) simulator.run().getFinalState();
        double millis = (System.nanoTime() - start) / 1_000_000.0;

        int[] shots = state.sampleZ(sampled, SAMPLE_SHOTS);
        int zeros = 0;
        int ones = 0;
        for (int shot : shots) {
            if (shot == 0) {
                zeros++;
            } else if (shot == 31) {
                ones++;
            }
        }

        System.out.printf(Locale.ROOT, "%nGHZ on %d qubits: %.3f ms, tableau state = %s%n",
            LARGE_QUBITS, millis, state.getClass().getSimpleName());
        System.out.printf(Locale.ROOT, "  %d samples on q0, q50, q100, q150, q199:%n",
            SAMPLE_SHOTS);
        System.out.printf(Locale.ROOT, "  all zeros  %4d  frequency %.4f%n", zeros,
            (double) zeros / SAMPLE_SHOTS);
        System.out.printf(Locale.ROOT, "  all ones   %4d  frequency %.4f%n", ones,
            (double) ones / SAMPLE_SHOTS);
    }

    private static void exactMarginal() throws Exception {
        Qubit[] targets = {new Qubit(0, "q0"), new Qubit(100, "q100"),
            new Qubit(199, "q199")};
        StabilizerState state = (StabilizerState) runGhz(LARGE_QUBITS,
            SimulationBackend.STABILIZER);
        double[] probabilities = state.outcomeProbabilitiesZ(targets);

        System.out.println("\nExact marginal on q0, q100, q199 (bit order 000 ... 111):");
        for (int outcome = 0; outcome < probabilities.length; outcome++) {
            if (probabilities[outcome] > 0.0) {
                System.out.printf(Locale.ROOT, "  %s -> %.6f%n",
                    String.format("%3s", Integer.toBinaryString(outcome)).replace(' ', '0'),
                    probabilities[outcome]);
            }
        }
    }

    private static void crossCheckWithStateVector() throws Exception {
        int qubits = 8;
        SimulationConfig config = new SimulationConfig();
        config.setNumQubits(qubits);
        config.setBackend(SimulationBackend.STATE_VECTOR);
        double[] exact = ((AmplitudeAccess) runGhz(qubits, SimulationBackend.STATE_VECTOR))
            .getProbabilities();

        Qubit[] all = new Qubit[qubits];
        for (int qubit = 0; qubit < qubits; qubit++) {
            all[qubit] = new Qubit(qubit, "q" + qubit);
        }
        double[] stabilizer = ((StabilizerState) runGhz(qubits,
            SimulationBackend.STABILIZER)).outcomeProbabilitiesZ(all);

        double difference = 0.0;
        for (int index = 0; index < exact.length; index++) {
            difference = Math.max(difference, Math.abs(exact[index] - stabilizer[index]));
        }
        System.out.printf(Locale.ROOT, "%nEight-qubit GHZ: state vector P(0...0) = %.6f, "
            + "stabilizer P(0...0) = %.6f, max |difference| = %.2e%n", exact[0], stabilizer[0],
            difference);
        System.out.println(difference < 1e-12
            ? "SUCCESS: stabilizer probabilities match the state vector exactly"
            : "FAILED: stabilizer probabilities differ from the state vector");
    }

    private static void nonCliffordGate() throws Exception {
        SimulationConfig config = new SimulationConfig();
        config.setNumQubits(2);
        config.setBackend(SimulationBackend.STABILIZER);
        Qubit q0 = new Qubit(0, "q0");
        List<QuantumEvent> events = new ArrayList<>();
        events.add(event("t_q0", SingleQubitGate.createT(q0), 0));

        String outcome;
        try {
            run(config, events);
            outcome = "accepted (unexpected)";
        } catch (Exception e) {
            outcome = e.getClass().getSimpleName() + ": " + e.getMessage();
        }
        System.out.println("\nA T gate on the stabilizer backend -> " + outcome);
    }

    private static QuantumState runGhz(int qubits, SimulationBackend backend) throws Exception {
        SimulationConfig config = new SimulationConfig();
        config.setNumQubits(qubits);
        config.setBackend(backend);
        return run(config, ghzEvents(qubits));
    }

    private static List<QuantumEvent> ghzEvents(int qubits) {
        List<QuantumEvent> events = new ArrayList<>();
        Qubit first = new Qubit(0, "q0");
        events.add(event("h_q0", SingleQubitGate.createHadamard(first), 0));
        for (int qubit = 1; qubit < qubits; qubit++) {
            Qubit control = new Qubit(qubit - 1, "q" + (qubit - 1));
            Qubit target = new Qubit(qubit, "q" + qubit);
            events.add(event("cx_" + qubit, MultiQubitGate.createCNOT(control, target), qubit));
        }
        return events;
    }

    private static QuantumEvent event(String name, QuantumOperation operation, int step) {
        return new QuantumEvent.Builder(name)
            .withScheduledTime(Instant.now().minusSeconds(10).plusMillis(step))
            .withOperation(operation)
            .build();
    }

    private static QuantumState run(SimulationConfig config, List<QuantumEvent> events)
            throws Exception {
        QuantumSimulator simulator = new QuantumSimulator();
        simulator.initialize(config);
        for (QuantumEvent event : events) {
            simulator.scheduleEvent(event);
        }
        return simulator.run().getFinalState();
    }
}
