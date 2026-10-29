package example.algorithms.teleportation;

import com.mechalikh.qforge.classical.ShotRunner;
import com.mechalikh.qforge.config.SimulationBackend;
import com.mechalikh.qforge.config.SimulationConfig;
import com.mechalikh.qforge.core.QuantumSimulator;
import com.mechalikh.qforge.events.QuantumEvent;
import com.mechalikh.qforge.math.Complex;
import com.mechalikh.qforge.math.RandomGenerator;
import com.mechalikh.qforge.measurement.MeasurementOperator;
import com.mechalikh.qforge.operations.ConditionalOperation;
import com.mechalikh.qforge.operations.SingleQubitGate;
import com.mechalikh.qforge.state.QuantumState;
import com.mechalikh.qforge.state.Qubit;

import example.EventBuilderHelper;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Random;

/**
 * Teleports a payload with measurement-driven feed-forward inside a single shot.
 *
 * <p>Unlike the branch analysis in {@link QuantumTeleportation}, this demo lets the engine
 * execute the correction. Alice's two qubits are measured at the middle of the circuit, the
 * outcomes are written into the classical register {@code alice}, and two
 * {@link ConditionalOperation} events act on Bob's qubit only when the matching bit is set. The
 * condition is evaluated by the simulation context when the event is dispatched, so every shot
 * follows the branch selected by its own measurement.</p>
 *
 * <p>Two statistics are reported. {@link ShotRunner} aggregates the classical register over all
 * shots, which shows the four equally likely branches. A second loop keeps the final state of
 * every shot and computes the fidelity of Bob's qubit against the payload, plus a control run
 * with the conditional events removed. The control run is the evidence that the corrections are
 * doing the work: without feed-forward the payload survives only in the identity branch.</p>
 */
public final class FeedForwardTeleportation {

    private static final double THETA = Math.PI / 3.0;
    private static final int SHOTS = 200;
    private static final long SEED = 20261029L;

    private FeedForwardTeleportation() {
    }

    public static void main(String[] args) throws Exception {
        System.out.println("=== Feed-Forward Quantum Teleportation ===");

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
        config.addClassicalRegister("alice", 2);

        RandomGenerator.setRandom(new Random(SEED));
        ShotRunner.ShotStatistics statistics = ShotRunner.run(
            config, () -> buildCircuit(true), SHOTS);

        System.out.printf(Locale.ROOT, "%nAlice's classical register over %d shots:%n", SHOTS);
        for (long value = 0; value < 4; value++) {
            System.out.printf(Locale.ROOT, "  alice=%d  count=%3d  frequency=%.4f%n", value,
                statistics.getCounts("alice").getOrDefault(value, 0),
                statistics.frequency("alice", value));
        }

        RandomGenerator.setRandom(new Random(SEED));
        FidelityReport corrected = runWithFinalState(config, true, payload);
        RandomGenerator.setRandom(new Random(SEED));
        FidelityReport control = runWithFinalState(config, false, payload);

        System.out.printf(Locale.ROOT,
            "%nWith feed-forward:    shots=%d  success=%.4f  mean fidelity=%.6f  min=%.6f%n",
            corrected.shots, corrected.successRate(), corrected.mean(), corrected.min);
        System.out.printf(Locale.ROOT,
            "Without corrections:  shots=%d  success=%.4f  mean fidelity=%.6f  min=%.6f%n",
            control.shots, control.successRate(), control.mean(), control.min);

        boolean ok = corrected.successRate() == 1.0 && corrected.min > 1.0 - 1e-9;
        System.out.println(ok
            ? "SUCCESS: every shot recovered the payload through conditional corrections"
            : "FAILED: at least one shot did not recover the payload");
    }

    /**
     * Builds one shot of the protocol.
     *
     * <p>Alice's measurements are placed before the conditional events, and the event helper
     * assigns increasing timestamps, so a correction always observes both outcomes of its own
     * shot. Each call creates new events because an event cannot be executed twice.</p>
     */
    private static List<QuantumEvent> buildCircuit(boolean feedForward) {
        EventBuilderHelper.resetTimer();
        Qubit alice = new Qubit(0, "payload/alice");
        Qubit bellA = new Qubit(1, "alice");
        Qubit bob = new Qubit(2, "bob");

        List<QuantumEvent> events = new ArrayList<>();
        events.add(EventBuilderHelper.createSingleQubitGate(
            "Ry_payload", SingleQubitGate.createRY(THETA, alice), alice));
        events.add(EventBuilderHelper.createSingleQubitGate(
            "H_bell", SingleQubitGate.createHadamard(bellA), bellA));
        events.add(EventBuilderHelper.createCNOT("CNOT_bell", bellA, bob));
        events.add(EventBuilderHelper.createCNOT("CNOT_alice", alice, bellA));
        events.add(EventBuilderHelper.createSingleQubitGate(
            "H_alice", SingleQubitGate.createHadamard(alice), alice));

        events.add(EventBuilderHelper.createEvent("measure_alice",
            MeasurementOperator.computationalBasis(new Qubit[]{alice}, 1)
                .withClassicalTarget("alice", 0),
            new Qubit[]{alice}, new Qubit[0]));
        events.add(EventBuilderHelper.createEvent("measure_bell",
            MeasurementOperator.computationalBasis(new Qubit[]{bellA}, 1)
                .withClassicalTarget("alice", 1),
            new Qubit[]{bellA}, new Qubit[0]));

        if (feedForward) {
            events.add(EventBuilderHelper.createEvent("correct_X",
                new ConditionalOperation(SingleQubitGate.createPauliX(bob), "alice", 2L, 2L),
                new Qubit[]{bob}, new Qubit[0]));
            events.add(EventBuilderHelper.createEvent("correct_Z",
                new ConditionalOperation(SingleQubitGate.createPauliZ(bob), "alice", 1L, 1L),
                new Qubit[]{bob}, new Qubit[0]));
        }
        return events;
    }

    private static FidelityReport runWithFinalState(SimulationConfig config, boolean feedForward,
                                                    Complex[] payload) throws Exception {
        QuantumSimulator simulator = new QuantumSimulator();
        FidelityReport report = new FidelityReport();
        for (int shot = 0; shot < SHOTS; shot++) {
            simulator.initialize(config);
            for (QuantumEvent event : buildCircuit(feedForward)) {
                simulator.scheduleEvent(event);
            }
            QuantumState state = simulator.run().getFinalState();
            simulator.reset();

            Complex[] recovered = bobMarginal(state);
            double fidelity = fidelity(payload, recovered);
            report.record(fidelity);
        }
        return report;
    }

    /** Returns Bob's qubit amplitudes after the final measurement collapsed Alice's qubits. */
    private static Complex[] bobMarginal(QuantumState state) {
        Complex zero = Complex.ZERO;
        Complex one = Complex.ZERO;
        for (int index = 0; index < 8; index++) {
            Complex amplitude = state.getAmplitude(index);
            if ((index & 1) == 0) {
                zero = zero.add(amplitude);
            } else {
                one = one.add(amplitude);
            }
        }
        return new Complex[] {zero, one};
    }

    private static double fidelity(Complex[] expected, Complex[] actual) {
        Complex overlap = Complex.ZERO;
        for (int i = 0; i < expected.length; i++) {
            overlap = overlap.add(expected[i].conjugate().multiply(actual[i]));
        }
        return overlap.absSquare();
    }

    private static final class FidelityReport {
        private int shots;
        private int successes;
        private double total;
        private double min = 1.0;

        private void record(double fidelity) {
            shots++;
            total += fidelity;
            min = Math.min(min, fidelity);
            if (fidelity > 1.0 - 1e-9) {
                successes++;
            }
        }

        private double successRate() {
            return shots == 0 ? 0.0 : (double) successes / shots;
        }

        private double mean() {
            return shots == 0 ? 0.0 : total / shots;
        }
    }
}
