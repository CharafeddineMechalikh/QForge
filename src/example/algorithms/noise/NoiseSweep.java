package example.algorithms.noise;

import com.mechalikh.qforge.config.SimulationBackend;
import com.mechalikh.qforge.config.SimulationConfig;
import com.mechalikh.qforge.core.QuantumSimulator;
import com.mechalikh.qforge.events.QuantumEvent;
import com.mechalikh.qforge.math.Complex;
import com.mechalikh.qforge.math.ComplexMatrix;
import com.mechalikh.qforge.noise.DepolarizingNoise;
import com.mechalikh.qforge.operations.SingleQubitGate;
import com.mechalikh.qforge.state.Qubit;
import com.mechalikh.qforge.state.QuantumState;

import example.EventBuilderHelper;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Sweeps a depolarizing error probability and reports how far a noisy Bell state drifts from the
 * ideal one.
 *
 * <p>The ideal circuit prepares a Bell pair {@code (|00> + |11>)/sqrt(2)} on the state-vector
 * backend. The same circuit is then run on the density-matrix backend with an independent
 * single-qubit depolarizing channel on each qubit, at a range of error probabilities. Fidelity is
 * {@code <psi|rho|psi>}, the probability that the noisy mixed state would pass an ideal-state test,
 * computed as the expectation value of the rank-one projector {@code |psi><psi|}.</p>
 *
 * <p>At zero error the fidelity is 1. As the error probability rises it falls monotonically. The
 * channel parameter here is the probability that one of X, Y or Z is applied, so the Bloch length
 * shrinks as {@code lambda = 1 - 4p/3} and the two-qubit fidelity follows
 * {@code (1 + 3 lambda^2) / 4}. The physical range therefore ends at {@code p = 0.75}, where
 * {@code lambda = 0} and the state is maximally mixed, giving fidelity 0.25. Probabilities above
 * 0.75 over-apply the channel and are not physical.</p>
 */
public final class NoiseSweep {

    private static final int NUM_QUBITS = 2;
    private static final double[] PROBABILITIES = {
        0.00, 0.02, 0.05, 0.10, 0.20, 0.30, 0.50, 0.625, 0.75
    };

    private NoiseSweep() {
    }

    public static void main(String[] args) throws Exception {
        System.out.println("=== Depolarizing Noise Sweep on a Bell State ===");

        Qubit[] qubits = {new Qubit(0, "q0"), new Qubit(1, "q1")};
        QuantumState ideal = idealState(qubits);
        ComplexMatrix projector = projector(ideal);
        System.out.printf(Locale.ROOT, "Ideal state: %s%n", ideal);

        System.out.println("\n  p     fidelity   distance");
        double previous = 1.0;
        double fidelityAtZero = -1.0;
        double fidelityAtMax = -1.0;
        boolean monotone = true;
        for (double p : PROBABILITIES) {
            double fidelity = noisyFidelity(qubits, projector, p);
            double distance = 1.0 - fidelity;
            if (fidelity > previous + 1e-9) {
                monotone = false;
            }
            previous = fidelity;
            if (p == 0.0) {
                fidelityAtZero = fidelity;
            }
            if (p == 0.75) {
                fidelityAtMax = fidelity;
            }
            System.out.printf(Locale.ROOT, "  %.3f  %.6f   %.6f%n", p, fidelity, distance);
        }

        boolean goodStart = Math.abs(fidelityAtZero - 1.0) < 1e-9;
        boolean goodEnd = Math.abs(fidelityAtMax - 0.25) < 1e-9;
        System.out.println();
        if (goodStart && monotone && goodEnd) {
            System.out.println(
                "SUCCESS: fidelity starts at 1, falls monotonically, and reaches 0.25 at p=0.75");
        } else {
            System.out.println("FAILED: the fidelity curve did not match the expected shape");
        }
    }

    private static QuantumState idealState(Qubit[] qubits) throws Exception {
        SimulationConfig config = new SimulationConfig();
        config.setNumQubits(NUM_QUBITS);
        config.setBackend(SimulationBackend.STATE_VECTOR);
        return run(config, idealCircuit(qubits));
    }

    private static double noisyFidelity(Qubit[] qubits, ComplexMatrix projector, double probability)
        throws Exception {
        SimulationConfig config = new SimulationConfig();
        config.setNumQubits(NUM_QUBITS);
        config.setBackend(SimulationBackend.DENSITY_MATRIX);

        EventBuilderHelper.resetTimer();
        List<QuantumEvent> events = new ArrayList<>(idealCircuitEvents(qubits));
        if (probability > 0.0) {
            for (Qubit qubit : qubits) {
                DepolarizingNoise noise =
                    DepolarizingNoise.createSingleQubit(probability, qubit);
                events.add(EventBuilderHelper.createEvent(
                    "depol_" + qubit.getIndex(), noise, new Qubit[] {qubit}, new Qubit[0]));
            }
        }

        QuantumState noisy = run(config, events);
        return noisy.getExpectationValue(projector);
    }

    private static QuantumState run(SimulationConfig config, List<QuantumEvent> events)
        throws Exception {
        QuantumSimulator simulator = new QuantumSimulator();
        simulator.initialize(config);
        for (QuantumEvent event : events) {
            simulator.scheduleEvent(event);
        }
        QuantumState state = simulator.run().getFinalState();
        simulator.reset();
        return state;
    }

    private static QuantumState run(SimulationConfig config, QuantumEvent[] events)
        throws Exception {
        return run(config, List.of(events));
    }

    private static QuantumEvent[] idealCircuit(Qubit[] qubits) {
        return idealCircuitEvents(qubits).toArray(new QuantumEvent[0]);
    }

    private static List<QuantumEvent> idealCircuitEvents(Qubit[] qubits) {
        EventBuilderHelper.resetTimer();
        List<QuantumEvent> events = new ArrayList<>();
        events.add(EventBuilderHelper.createSingleQubitGate(
            "H", SingleQubitGate.createHadamard(qubits[0]), qubits[0]));
        events.add(EventBuilderHelper.createCNOT("CNOT", qubits[0], qubits[1]));
        return events;
    }

    private static ComplexMatrix projector(QuantumState ideal) {
        int dimension = 1 << NUM_QUBITS;
        ComplexMatrix projector = new ComplexMatrix(dimension, dimension);
        for (int i = 0; i < dimension; i++) {
            for (int j = 0; j < dimension; j++) {
                projector.set(i, j, ideal.getAmplitude(i).multiply(ideal.getAmplitude(j).conjugate()));
            }
        }
        return projector;
    }
}
