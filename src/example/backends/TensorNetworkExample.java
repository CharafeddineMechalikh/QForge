package example.backends;

import com.mechalikh.qforge.config.SimulationBackend;
import com.mechalikh.qforge.config.SimulationConfig;
import com.mechalikh.qforge.core.QuantumSimulator;
import com.mechalikh.qforge.events.QuantumEvent;
import com.mechalikh.qforge.math.RandomGenerator;
import com.mechalikh.qforge.operations.MultiQubitGate;
import com.mechalikh.qforge.operations.QuantumOperation;
import com.mechalikh.qforge.operations.SingleQubitGate;
import com.mechalikh.qforge.state.AmplitudeAccess;
import com.mechalikh.qforge.state.MPSState;
import com.mechalikh.qforge.state.QuantumState;
import com.mechalikh.qforge.state.Qubit;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Shows how bond-dimension truncation trades accuracy for cost on the tensor network backend.
 *
 * <p>A twelve-qubit circuit of Hadamards, {@code T} gates and a chain of CNOTs produces more
 * entanglement than a small matrix product state can store. The state vector backend computes the
 * exact distribution, and the tensor network backend is run with bond limits from one to
 * sixty-four. For each limit the example reports the accumulated discarded weight, the largest
 * bond the state actually used, and the maximum absolute difference from the exact probability of
 * every one of the 4096 basis states.</p>
 *
 * <p>The remaining sections show the two hard limits: a three-qubit gate is rejected because the
 * backend only implements one- and two-qubit gates, and every amplitude or probability accessor
 * is capped at 24 qubits. A thirty-qubit state therefore evolves, and its bond dimensions, purity
 * and discarded weight remain readable, but no basis-state probability can be extracted from it.</p>
 */
public final class TensorNetworkExample {

    private static final int ENTANGLING_QUBITS = 12;
    private static final int LAYERS = 3;
    private static final int LARGE_QUBITS = 30;

    private TensorNetworkExample() {
    }

    public static void main(String[] args) throws Exception {
        System.out.println("=== Tensor network backend ===");
        truncationSweep();
        threeQubitGate();
        denseExtractionLimit();
    }

    private static void truncationSweep() throws Exception {
        double[] exact = ((AmplitudeAccess) run(config(ENTANGLING_QUBITS,
            SimulationBackend.STATE_VECTOR, 64), entanglingEvents(ENTANGLING_QUBITS)))
            .getProbabilities();

        System.out.printf(Locale.ROOT,
            "%n%d-qubit circuit, %d layers of H, T and a CNOT chain, compared with the exact "
                + "state vector:%n", ENTANGLING_QUBITS, LAYERS);
        System.out.println("  bond limit   discarded weight   largest bond   max |P - P_exact|");
        for (int bond : new int[] {1, 2, 4, 8, 64}) {
            MPSState state = (MPSState) run(config(ENTANGLING_QUBITS,
                SimulationBackend.TENSOR_NETWORK, bond), entanglingEvents(ENTANGLING_QUBITS));
            double[] probabilities = state.getProbabilities();
            double difference = 0.0;
            for (int index = 0; index < exact.length; index++) {
                difference = Math.max(difference, Math.abs(exact[index] - probabilities[index]));
            }
            System.out.printf(Locale.ROOT, "  %10d   %16.6f   %12d   %15.6f%n", bond,
                state.getTruncationError(), largestBond(state), difference);
        }
    }

    private static void threeQubitGate() throws Exception {
        SimulationConfig config = new SimulationConfig();
        config.setNumQubits(3);
        config.setBackend(SimulationBackend.TENSOR_NETWORK);
        Qubit q0 = new Qubit(0, "q0");
        Qubit q1 = new Qubit(1, "q1");
        Qubit q2 = new Qubit(2, "q2");
        List<QuantumEvent> events = new ArrayList<>();
        events.add(event("toffoli", MultiQubitGate.createToffoli(q0, q1, q2), 0));

        String outcome;
        try {
            run(config, events);
            outcome = "accepted (unexpected)";
        } catch (Exception e) {
            outcome = e.getClass().getSimpleName() + ": " + e.getMessage();
        }
        System.out.println("\nA Toffoli gate on the tensor network backend -> " + outcome);
    }

    private static void denseExtractionLimit() throws Exception {
        SimulationConfig config = new SimulationConfig();
        config.setNumQubits(LARGE_QUBITS);
        config.setBackend(SimulationBackend.TENSOR_NETWORK);
        config.setMaxBondDimension(8);
        MPSState state = (MPSState) run(config, ghzEvents(LARGE_QUBITS));

        System.out.printf(Locale.ROOT, "%n%d-qubit GHZ in an MPS: largest bond %d, purity "
            + "%.6f, discarded weight %.6f%n", LARGE_QUBITS, largestBond(state),
            state.getPurity(), state.getTruncationError());

        String outcome;
        try {
            state.getProbability(0);
            outcome = "returned a probability (unexpected)";
        } catch (UnsupportedOperationException e) {
            outcome = e.getMessage();
        }
        System.out.println("  P(0...0) -> " + outcome);
    }

    private static SimulationConfig config(int qubits, SimulationBackend backend, int bond) {
        SimulationConfig config = new SimulationConfig();
        config.setNumQubits(qubits);
        config.setBackend(backend);
        config.setMaxBondDimension(bond);
        return config;
    }

    private static QuantumState run(SimulationConfig config, List<QuantumEvent> events)
            throws Exception {
        RandomGenerator.resetToDefault();
        QuantumSimulator simulator = new QuantumSimulator();
        simulator.initialize(config);
        for (QuantumEvent event : events) {
            simulator.scheduleEvent(event);
        }
        return simulator.run().getFinalState();
    }

    private static int largestBond(MPSState state) {
        int largest = 0;
        for (int dimension : state.getBondDimensions()) {
            largest = Math.max(largest, dimension);
        }
        return largest;
    }

    private static List<QuantumEvent> entanglingEvents(int qubits) {
        List<QuantumEvent> events = new ArrayList<>();
        int step = 0;
        for (int layer = 0; layer < LAYERS; layer++) {
            for (int qubit = 0; qubit < qubits; qubit++) {
                Qubit target = new Qubit(qubit, "q" + qubit);
                events.add(event("h_" + layer + "_" + qubit,
                    SingleQubitGate.createHadamard(target), step++));
                events.add(event("t_" + layer + "_" + qubit,
                    SingleQubitGate.createT(target), step++));
            }
            for (int qubit = 0; qubit < qubits - 1; qubit++) {
                Qubit control = new Qubit(qubit, "q" + qubit);
                Qubit target = new Qubit(qubit + 1, "q" + (qubit + 1));
                events.add(event("cx_" + layer + "_" + qubit,
                    MultiQubitGate.createCNOT(control, target), step++));
            }
        }
        return events;
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
}
