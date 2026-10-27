package example.algorithms.shor;

import com.mechalikh.qforge.config.SimulationBackend;
import com.mechalikh.qforge.config.SimulationConfig;
import com.mechalikh.qforge.core.QuantumSimulator;
import com.mechalikh.qforge.events.QuantumEvent;
import com.mechalikh.qforge.math.Complex;
import com.mechalikh.qforge.math.ComplexMatrix;
import com.mechalikh.qforge.operations.QuantumGate;
import com.mechalikh.qforge.operations.SingleQubitGate;
import com.mechalikh.qforge.state.Qubit;

import example.EventBuilderHelper;
import example.algorithms.QuantumPhaseEstimation;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Factors 15 with Shor's algorithm by estimating the order of 7 modulo 15.
 *
 * <p>The circuit is a textbook order-finding run: four estimation qubits in a uniform superposition
 * control powers of the modular-multiply unitary {@code U|y> = |7y mod 15>}, and an inverse Quantum
 * Fourier Transform on the estimation register exposes the phase. The measured phase
 * {@code c/16} is turned into a candidate order with continued fractions, and the order yields the
 * factors through {@code gcd(a^{r/2} +/- 1, N)}.</p>
 *
 * <p>The modular multiply is a permutation on the four work qubits: {@code y -> 7y mod 15} for
 * {@code 0 <= y < 15}, with 15 held fixed as a sentinel so the map stays a bijection on 16 basis
 * states. It is supplied as a single custom gate, and the existing phase-estimation helper builds
 * the controlled powers and the inverse transform. No step is faked: the counting-register
 * distribution printed below comes straight from the state vector.</p>
 */
public final class ShorFactoring {

    private static final int N = 15;
    private static final int BASE = 7;
    private static final int ESTIMATION_BITS = 4;
    private static final int WORK_BITS = 4;

    private ShorFactoring() {
    }

    public static void main(String[] args) throws Exception {
        System.out.println("=== Shor's Algorithm: factoring N = 15 with a = " + BASE + " ===");
        System.out.println("True order of " + BASE + " mod " + N + " is " + trueOrder());
        System.out.printf(Locale.ROOT,
            "Estimation register: %d qubits, work register: %d qubits%n",
            ESTIMATION_BITS, WORK_BITS);

        double[] counting = runOrderFinding();

        System.out.println("\nCounting-register distribution (phase = c / 16):");
        double usefulProbability = 0.0;
        Set<Long> factors = new LinkedHashSet<>();
        for (int c = 0; c < counting.length; c++) {
            if (counting[c] < 1e-6) {
                continue;
            }
            int order = orderFromPhase(c);
            String outcome;
            if (c == 0) {
                outcome = "phase 0, no order information, retry";
            } else if (order < 0) {
                outcome = "continued fraction gives no valid order, retry";
            } else if (order % 2 != 0) {
                outcome = "order " + order + " is odd, retry";
            } else {
                long halfPower = modPow(BASE, order / 2, N);
                long first = gcd(halfPower - 1, N);
                long second = gcd(halfPower + 1, N);
                if (first > 1 && first < N && second > 1 && second < N) {
                    factors.add(first);
                    factors.add(second);
                    usefulProbability += counting[c];
                    outcome = String.format(Locale.ROOT,
                        "order %d -> gcd(%d-1,15)=%d, gcd(%d+1,15)=%d",
                        order, halfPower, first, halfPower, second);
                } else {
                    outcome = "order " + order + " gives trivial factors, retry";
                }
            }
            System.out.printf(Locale.ROOT, "  c=%2d  P=%.4f  %s%n", c, counting[c], outcome);
        }

        System.out.printf(Locale.ROOT,
            "%nProbability of a run that yields the factors: %.2f%n", usefulProbability);
        System.out.println("Recovered factors: " + factors);
        boolean success = factors.contains(3L) && factors.contains(5L);
        System.out.println(success
            ? "SUCCESS: 15 = 3 x 5"
            : "FAILED: the run did not recover both factors");

        System.out.println("\nA handful of repeats makes success certain, because each run either");
        System.out.println("returns the factors or reports that it must be retried.");
    }

    private static double[] runOrderFinding() throws Exception {
        Qubit[] estimation = new Qubit[ESTIMATION_BITS];
        for (int i = 0; i < ESTIMATION_BITS; i++) {
            estimation[i] = new Qubit(i, "est" + i);
        }
        Qubit[] work = new Qubit[WORK_BITS];
        for (int i = 0; i < WORK_BITS; i++) {
            work[i] = new Qubit(ESTIMATION_BITS + i, "work" + i);
        }

        QuantumGate modularMultiply = new QuantumGate.CustomGate(
            "Mul7Mod15", modularMultiplyMatrix(), work, new Qubit[0], 1.0);

        EventBuilderHelper.resetTimer();
        List<QuantumEvent> circuit = new ArrayList<>();
        // Work register starts at |1>, so the multiply acts on the orbit of 1.
        circuit.add(EventBuilderHelper.createSingleQubitGate(
            "X_work1", SingleQubitGate.createPauliX(work[WORK_BITS - 1]),
            work[WORK_BITS - 1]));

        QuantumEvent[] unitary = {EventBuilderHelper.createEvent(
            "U", modularMultiply, work, new Qubit[0])};
        QuantumEvent[] qpe = QuantumPhaseEstimation.createPhaseEstimationCircuit(
            estimation, work, unitary, ESTIMATION_BITS);
        circuit.addAll(List.of(qpe));

        SimulationConfig config = new SimulationConfig();
        config.setNumQubits(ESTIMATION_BITS + WORK_BITS);
        config.setBackend(SimulationBackend.STATE_VECTOR);

        QuantumSimulator simulator = new QuantumSimulator();
        simulator.initialize(config);
        for (QuantumEvent event : circuit) {
            simulator.scheduleEvent(event);
        }
        double[] probs = simulator.run().getFinalState().getProbabilities();
        simulator.reset();

        double[] counting = new double[1 << ESTIMATION_BITS];
        for (int index = 0; index < probs.length; index++) {
            int c = index >> WORK_BITS;
            counting[c] += probs[index];
        }
        return counting;
    }

    /** Permutation {@code y -> 7y mod 15} on 16 basis states, with 15 fixed as a sentinel. */
    private static ComplexMatrix modularMultiplyMatrix() {
        ComplexMatrix matrix = new ComplexMatrix(1 << WORK_BITS, 1 << WORK_BITS);
        for (int y = 0; y < (1 << WORK_BITS); y++) {
            int target = (y == N) ? N : (BASE * y) % N;
            matrix.set(target, y, Complex.ONE);
        }
        return matrix;
    }

    /** Denumerator of the first continued-fraction convergent of {@code c/2^t} that is a valid order. */
    private static int orderFromPhase(int c) {
        long numerator = c;
        long denominator = 1L << ESTIMATION_BITS;
        long hPrev2 = 0;
        long hPrev1 = 1;
        long kPrev2 = 1;
        long kPrev1 = 0;
        long x = numerator;
        long y = denominator;
        while (y != 0) {
            long term = x / y;
            long h = term * hPrev1 + hPrev2;
            long k = term * kPrev1 + kPrev2;
            if (k > 0 && k <= N && modPow(BASE, k, N) == 1) {
                return (int) k;
            }
            if (k > N) {
                return -1;
            }
            hPrev2 = hPrev1;
            hPrev1 = h;
            kPrev2 = kPrev1;
            kPrev1 = k;
            long remainder = x - term * y;
            x = y;
            y = remainder;
        }
        return -1;
    }

    private static long trueOrder() {
        long value = 1;
        for (long r = 1; r <= N; r++) {
            value = (value * BASE) % N;
            if (value == 1) {
                return r;
            }
        }
        return -1;
    }

    private static long modPow(long base, long exponent, long modulus) {
        long result = 1;
        long factor = base % modulus;
        long exp = exponent;
        while (exp > 0) {
            if ((exp & 1) == 1) {
                result = (result * factor) % modulus;
            }
            factor = (factor * factor) % modulus;
            exp >>= 1;
        }
        return result;
    }

    private static long gcd(long a, long b) {
        long x = Math.abs(a);
        long y = Math.abs(b);
        while (y != 0) {
            long t = x % y;
            x = y;
            y = t;
        }
        return x;
    }
}
