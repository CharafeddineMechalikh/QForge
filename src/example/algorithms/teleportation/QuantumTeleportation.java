package example.algorithms.teleportation;

import com.mechalikh.qforge.config.SimulationBackend;
import com.mechalikh.qforge.config.SimulationConfig;
import com.mechalikh.qforge.core.QuantumSimulator;
import com.mechalikh.qforge.events.QuantumEvent;
import com.mechalikh.qforge.math.Complex;
import com.mechalikh.qforge.operations.SingleQubitGate;
import com.mechalikh.qforge.state.Qubit;
import com.mechalikh.qforge.state.QuantumState;

import example.EventBuilderHelper;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Teleports one qubit state through a Bell pair and checks that Bob recovers it.
 *
 * <p>The circuit prepares an arbitrary payload {@code Ry(theta)|0>} on qubit 0, entangles qubits 1
 * and 2 into a Bell pair, and applies Alice's CNOT and Hadamard. The final state is a superposition
 * of the four joint outcomes of Alice's two qubits, and the conditional state of Bob's qubit differs
 * from the payload by a known Pauli correction. Instead of sampling a single measurement, the demo
 * reads the exact state vector, splits it into the four outcome branches, applies the correction,
 * and reports the fidelity of the recovered state in every branch. All four branches must reach
 * fidelity 1, which is the teleportation guarantee.</p>
 *
 * <p>This staging (analyse all branches after one run) is used on purpose: the engine does not yet
 * wire mid-circuit measurement into conditional gates, so the correction is applied to the branch
 * state in code rather than by a feed-forward rule during the run.</p>
 */
public final class QuantumTeleportation {

    private static final double THETA = Math.PI / 3.0;

    private QuantumTeleportation() {
    }

    public static void main(String[] args) throws Exception {
        System.out.println("=== Quantum Teleportation ===");

        Complex[] payload = {
            new Complex(Math.cos(THETA / 2.0), 0.0),
            new Complex(Math.sin(THETA / 2.0), 0.0)
        };
        System.out.printf(Locale.ROOT,
            "Payload: Ry(theta)|0> with theta = pi/3 -> [%.6f, %.6f]%n", payload[0].real(),
            payload[1].real());

        SimulationConfig config = new SimulationConfig();
        config.setNumQubits(3);
        config.setBackend(SimulationBackend.STATE_VECTOR);

        Qubit alice = new Qubit(0, "payload/alice");
        Qubit bellA = new Qubit(1, "alice");
        Qubit bob = new Qubit(2, "bob");

        EventBuilderHelper.resetTimer();
        List<QuantumEvent> events = new ArrayList<>();
        events.add(EventBuilderHelper.createSingleQubitGate(
            "Ry", SingleQubitGate.createRY(THETA, alice), alice));
        events.add(EventBuilderHelper.createSingleQubitGate(
            "H_bell", SingleQubitGate.createHadamard(bellA), bellA));
        events.add(EventBuilderHelper.createCNOT("CNOT_bell", bellA, bob));
        events.add(EventBuilderHelper.createCNOT("CNOT_alice", alice, bellA));
        events.add(EventBuilderHelper.createSingleQubitGate(
            "H_alice", SingleQubitGate.createHadamard(alice), alice));

        QuantumSimulator simulator = new QuantumSimulator();
        simulator.initialize(config);
        for (QuantumEvent event : events) {
            simulator.scheduleEvent(event);
        }
        QuantumState state = simulator.run().getFinalState();
        simulator.reset();

        Complex[] bobReduced = bobMarginal(state);
        System.out.printf(Locale.ROOT,
            "Bob's qubit before correction (marginal): P(0)=%.4f P(1)=%.4f (maximally mixed)%n",
            bobReduced[0].absSquare(), bobReduced[1].absSquare());

        System.out.println("\nBranch analysis (qubit0, qubit1 -> correction -> fidelity):");
        boolean allRecovered = true;
        for (int a = 0; a < 2; a++) {
            for (int b = 0; b < 2; b++) {
                Complex[] conditional = conditionalBob(state, a, b);
                double probability = conditional[0].absSquare() + conditional[1].absSquare();
                Complex[] normalized = normalize(conditional);
                Complex[] uncorrected = normalized.clone();
                Complex[] corrected = applyCorrection(normalized, a, b);

                double uncorrectedFidelity = fidelity(payload, uncorrected);
                double correctedFidelity = fidelity(payload, corrected);
                allRecovered &= correctedFidelity > 1.0 - 1e-9;

                String correction = correctionLabel(a, b);
                System.out.printf(Locale.ROOT,
                    "  (%d,%d) P=%.4f  correction=%-10s  uncorrected F=%.4f  corrected F=%.6f%n",
                    a, b, probability, correction, uncorrectedFidelity, correctedFidelity);
            }
        }

        System.out.println(allRecovered
            ? "SUCCESS: every measurement branch teleports the payload with fidelity 1"
            : "FAILED: at least one branch did not recover the payload");

        System.out.println("\nNote: the classical bits (a,b) must reach Bob for the correction to be");
        System.out.println("applied. Before correction his qubit alone carries no information about");
        System.out.println("the payload, which is why its marginal stays maximally mixed.");
    }

    /** Collects the amplitudes where Alice's qubits are (a,b), in Bob's basis {|0>,|1>}. */
    private static Complex[] conditionalBob(QuantumState state, int a, int b) {
        int base = (a << 2) | (b << 1);
        return new Complex[] {state.getAmplitude(base), state.getAmplitude(base | 1)};
    }

    /** Marginal of Bob's qubit as an unnormalised conditioned vector of its diagonal weight. */
    private static Complex[] bobMarginal(QuantumState state) {
        double zero = 0.0;
        double one = 0.0;
        for (int a = 0; a < 2; a++) {
            for (int b = 0; b < 2; b++) {
                int base = (a << 2) | (b << 1);
                zero += state.getAmplitude(base).absSquare();
                one += state.getAmplitude(base | 1).absSquare();
            }
        }
        return new Complex[] {new Complex(Math.sqrt(zero), 0.0), new Complex(Math.sqrt(one), 0.0)};
    }

    private static Complex[] normalize(Complex[] vector) {
        double norm = Math.sqrt(vector[0].absSquare() + vector[1].absSquare());
        return new Complex[] {vector[0].scale(1.0 / norm), vector[1].scale(1.0 / norm)};
    }

    /** Bob undoes the branch: X when the second bit is set, then Z when the first bit is set. */
    private static Complex[] applyCorrection(Complex[] vector, int a, int b) {
        Complex[] result = vector.clone();
        if (b == 1) {
            Complex tmp = result[0];
            result[0] = result[1];
            result[1] = tmp;
        }
        if (a == 1) {
            result[1] = new Complex(-result[1].real(), -result[1].imag());
        }
        return result;
    }

    private static String correctionLabel(int a, int b) {
        if (a == 0 && b == 0) {
            return "I";
        }
        if (a == 1 && b == 0) {
            return "Z";
        }
        if (a == 0 && b == 1) {
            return "X";
        }
        return "Z then X";
    }

    private static double fidelity(Complex[] expected, Complex[] actual) {
        Complex overlap = Complex.ZERO;
        for (int i = 0; i < expected.length; i++) {
            overlap = overlap.add(expected[i].conjugate().multiply(actual[i]));
        }
        return overlap.absSquare();
    }
}
