package example.algorithms.bb84;

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
import java.util.Random;

/**
 * Runs the BB84 quantum key distribution protocol and shows how an eavesdropper is caught.
 *
 * <p>Alice encodes random bits in a random basis (Z or X), Bob measures in a random basis, and they
 * keep only the rounds where their bases match. Alice's four single-qubit states are first produced
 * by the state-vector backend, so the encoding itself goes through the real simulator. The protocol
 * then samples each measurement from the exact Born probabilities of the current state, which is
 * what a shot-based run would converge to. Two scenarios are compared with the same random choices:
 * a clean channel, and a channel where Eve intercepts every qubit, measures in a random basis, and
 * re-sends the collapsed state.</p>
 *
 * <p>Without an eavesdropper the sifted key matches perfectly. An intercept-resend eavesdropper
 * introduces errors in about a quarter of the sifted bits, because whenever Eve guesses the wrong
 * basis she destroys the state Alice prepared. A QBER near 25 percent is therefore the signature of
 * interception.</p>
 */
public final class BB84 {

    private static final int TRANSMISSIONS = 1000;
    private static final long SEED = 2026L;
    private static final double DETECTION_THRESHOLD = 0.11;

    private BB84() {
    }

    public static void main(String[] args) throws Exception {
        System.out.println("=== BB84 Quantum Key Distribution ===");
        System.out.printf(Locale.ROOT, "Transmissions per scenario: %d, seed: %d%n",
            TRANSMISSIONS, SEED);

        Complex[][][] encoded = new Complex[2][2][];
        for (int bit = 0; bit < 2; bit++) {
            for (int basis = 0; basis < 2; basis++) {
                encoded[bit][basis] = encodedState(bit, basis);
            }
        }
        System.out.println("\nEncoded states from the state-vector backend (Z-basis amplitudes):");
        for (int bit = 0; bit < 2; bit++) {
            for (int basis = 0; basis < 2; basis++) {
                System.out.printf(Locale.ROOT, "  bit=%d basis=%s -> [%.4f, %.4f]%n",
                    bit, basisName(basis),
                    encoded[bit][basis][0].real(), encoded[bit][basis][1].real());
            }
        }

        Random shared = new Random(SEED);
        int[] aliceBits = new int[TRANSMISSIONS];
        int[] aliceBasis = new int[TRANSMISSIONS];
        int[] bobBasis = new int[TRANSMISSIONS];
        for (int i = 0; i < TRANSMISSIONS; i++) {
            aliceBits[i] = shared.nextInt(2);
            aliceBasis[i] = shared.nextInt(2);
            bobBasis[i] = shared.nextInt(2);
        }

        Outcome clean = simulate(encoded, aliceBits, aliceBasis, bobBasis, false);
        Outcome eavesdropped = simulate(encoded, aliceBits, aliceBasis, bobBasis, true);

        report("No eavesdropper", clean, false);
        report("Intercept-resend Eve", eavesdropped, true);
    }

    private static Outcome simulate(Complex[][][] encoded, int[] aliceBits, int[] aliceBasis,
                                    int[] bobBasis, boolean eve) {
        Random rng = new Random(SEED * 31L + (eve ? 1 : 0));
        int sifted = 0;
        int errors = 0;
        for (int i = 0; i < aliceBits.length; i++) {
            Complex[] state = encoded[aliceBits[i]][aliceBasis[i]];

            if (eve) {
                int eveBasis = rng.nextInt(2);
                int eveOutcome = sample(state, eveBasis, rng);
                state = prepareBasisState(eveOutcome, eveBasis);
            }

            int bobOutcome = sample(state, bobBasis[i], rng);
            if (aliceBasis[i] == bobBasis[i]) {
                sifted++;
                if (bobOutcome != aliceBits[i]) {
                    errors++;
                }
            }
        }
        return new Outcome(sifted, errors);
    }

    private static void report(String label, Outcome outcome, boolean expectDetection) {
        double qber = outcome.sifted == 0 ? 0.0 : outcome.errors / (double) outcome.sifted;
        boolean detected = qber > DETECTION_THRESHOLD;
        String verdict;
        if (expectDetection) {
            verdict = detected
                ? "SUCCESS: error rate exceeds the 11% threshold, Eve is detected"
                : "FAILED: Eve was not detected";
        } else {
            verdict = qber == 0.0
                ? "SUCCESS: sifted keys match exactly"
                : "FAILED: errors on a clean channel";
        }
        System.out.printf(Locale.ROOT,
            "%n%s:%n  sifted bits=%d  errors=%d  QBER=%.4f%n  %s%n",
            label, outcome.sifted, outcome.errors, qber, verdict);
    }

    private static Complex[] encodedState(int bit, int basis) throws Exception {
        SimulationConfig config = new SimulationConfig();
        config.setNumQubits(1);
        config.setBackend(SimulationBackend.STATE_VECTOR);

        Qubit qubit = new Qubit(0, "q");
        EventBuilderHelper.resetTimer();
        List<QuantumEvent> events = new ArrayList<>();
        if (bit == 1) {
            events.add(EventBuilderHelper.createSingleQubitGate(
                "X", SingleQubitGate.createPauliX(qubit), qubit));
        }
        if (basis == 1) {
            events.add(EventBuilderHelper.createSingleQubitGate(
                "H", SingleQubitGate.createHadamard(qubit), qubit));
        }

        QuantumSimulator simulator = new QuantumSimulator();
        simulator.initialize(config);
        for (QuantumEvent event : events) {
            simulator.scheduleEvent(event);
        }
        QuantumState state = simulator.run().getFinalState();
        simulator.reset();
        return new Complex[] {state.getAmplitude(0), state.getAmplitude(1)};
    }

    /** Samples a measurement outcome of {@code state} in the given basis (0 = Z, 1 = X). */
    private static int sample(Complex[] state, int basis, Random rng) {
        double[] probabilities = basisProbabilities(state, basis);
        return rng.nextDouble() < probabilities[0] ? 0 : 1;
    }

    private static double[] basisProbabilities(Complex[] state, int basis) {
        if (basis == 0) {
            return new double[] {state[0].absSquare(), state[1].absSquare()};
        }
        double inv = 1.0 / Math.sqrt(2.0);
        Complex plus = state[0].add(state[1]).scale(inv);
        Complex minus = state[0].add(state[1].scale(-1.0)).scale(inv);
        return new double[] {plus.absSquare(), minus.absSquare()};
    }

    private static Complex[] prepareBasisState(int outcome, int basis) {
        double inv = 1.0 / Math.sqrt(2.0);
        if (basis == 0) {
            return outcome == 0
                ? new Complex[] {Complex.ONE, Complex.ZERO}
                : new Complex[] {Complex.ZERO, Complex.ONE};
        }
        return outcome == 0
            ? new Complex[] {new Complex(inv, 0.0), new Complex(inv, 0.0)}
            : new Complex[] {new Complex(inv, 0.0), new Complex(-inv, 0.0)};
    }

    private static String basisName(int basis) {
        return basis == 0 ? "Z" : "X";
    }

    private record Outcome(int sifted, int errors) {
    }
}
