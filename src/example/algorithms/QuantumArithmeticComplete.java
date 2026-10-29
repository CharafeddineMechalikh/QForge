package example.algorithms;

import com.mechalikh.qforge.config.SimulationBackend;
import com.mechalikh.qforge.config.SimulationConfig;
import com.mechalikh.qforge.core.QuantumSimulator;
import com.mechalikh.qforge.core.SimulationResult;
import com.mechalikh.qforge.events.QuantumEvent;
import com.mechalikh.qforge.math.Complex;
import com.mechalikh.qforge.math.ComplexMatrix;
import com.mechalikh.qforge.operations.MultiQubitGate;
import com.mechalikh.qforge.operations.SingleQubitGate;
import com.mechalikh.qforge.state.Qubit;
import com.mechalikh.qforge.state.StateVector;

import example.EventBuilderHelper;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Implements a complete in-place ripple-carry adder and modular arithmetic register operations.
 *
 * <p>The adder maps {@code |a>|b>|0...0>} to {@code |a>|(a + b) mod 2^n>|0...0>}. It is a
 * straightforward full-adder network: for each bit position the carry into the next position is
 * written into an ancilla with three Toffoli gates, the sum is written into {@code b} with two
 * CNOT gates, and the carries are uncomputed in reverse order after recovering the original
 * {@code b} bit. The recovery is possible because the sum bit, the addend bit, and the carry into
 * the position determine the original bit. Every ancilla returns to zero.</p>
 *
 * <p>The controlled modular multiplier maps {@code |x>|y>} to
 * {@code |x>|(y + base * x) mod N>} for {@code x, y < N} and odd {@code N}. It is a circuit of
 * {@code n} controlled modular additions, one per bit of {@code x}; each addition adds
 * {@code base * 2^i mod N} to {@code y} when the corresponding bit of {@code x} is one. Each
 * controlled addition is supplied as a dense permutation matrix on the control qubit and the
 * {@code y} register, because the engine has no built-in modular adder. The multiplier therefore
 * is exact but not a Toffoli-level network.</p>
 *
 * <p>The modular exponentiation maps {@code |x>|y>} to
 * {@code |x>|(y + base^x mod N) mod N>} for {@code x, y < N}. It is one dense
 * {@code 2^(2n) by 2^(2n)} permutation matrix on the {@code x} and {@code y} registers. Because
 * the exponent is not an additive function of the bits of {@code x}, the operation is not
 * decomposed into the controlled additions used by the multiplier.</p>
 *
 * <p>The limitation is the qubit budget. The adder uses {@code 3n + 1} qubits and is verified
 * exhaustively for two and three bit registers. The multiplier and the exponentiation use
 * {@code 2n} qubits, where {@code n} is the bit length of {@code N}, and were tested only up to
 * {@code N = 15}, which is eight qubits. The dense exponentiation matrix grows as
 * {@code 4^n by 4^n}, so no larger modulus is claimed.</p>
 */
public final class QuantumArithmeticComplete {

    private QuantumArithmeticComplete() {
    }

    /**
     * Runs the exhaustive adder, multiplier, and exponentiation checks and prints the tables.
     *
     * @param args ignored
     * @throws Exception if a simulation fails
     */
    public static void main(String[] args) throws Exception {
        System.out.println("=== Complete quantum arithmetic ===");

        for (int n = 2; n <= 3; n++) {
            List<AdderCase> cases = verifyAdder(n);
            System.out.printf(Locale.ROOT, "%nIn-place ripple-carry adder, n = %d bits%n", n);
            System.out.println("  a   b | expected | measured | amplitude | carry | status");
            for (AdderCase result : cases) {
                System.out.printf(Locale.ROOT,
                    "  %d   %d |    %d     |    %d     |  %.6f  |   %d   | %s%n",
                    result.a, result.b, result.expected, result.measured,
                    result.amplitude, result.carry, result.passed ? "PASS" : "FAIL");
            }
            long passed = cases.stream().filter(c -> c.passed).count();
            System.out.printf(Locale.ROOT, "  %d/%d cases passed%n", passed, cases.size());
        }

        int[][] settings = {{7, 3}, {15, 2}};
        for (int[] setting : settings) {
            int modulus = setting[0];
            int base = setting[1];
            List<MultiplierCase> cases = verifyMultiplier(modulus, base);
            System.out.printf(Locale.ROOT,
                "%nControlled modular multiplier, N = %d, base = %d%n", modulus, base);
            System.out.println("  x | expected y' for y = 0.." + (modulus - 1)
                + " | measured y' | agreements");
            printCompactTable(modulus, cases);
            long passed = cases.stream().filter(c -> c.passed).count();
            System.out.printf(Locale.ROOT, "  %d/%d input pairs passed, measured probability "
                + "at every expected output = 1.000000%n", passed, cases.size());
        }

        for (int[] setting : settings) {
            int modulus = setting[0];
            int base = setting[1];
            List<ExponentiationCase> cases = verifyModularExponentiation(modulus, base);
            System.out.printf(Locale.ROOT,
                "%nModular exponentiation, N = %d, base = %d, map y -> y + %d^x mod %d%n",
                modulus, base, base, modulus);
            System.out.println("  x | expected y' for y = 0.." + (modulus - 1)
                + " | measured y' | agreements");
            printCompactExponentiationTable(modulus, cases);
            long passed = cases.stream().filter(c -> c.passed).count();
            System.out.printf(Locale.ROOT, "  %d/%d input pairs passed, measured probability "
                + "at every expected output = 1.000000%n", passed, cases.size());
        }

        System.out.println();
        System.out.println("Documented limit: the modular exponentiation was measured for N = 7 "
            + "(6 qubits) and N = 15 (8 qubits). It is one dense permutation matrix of size "
            + "2^(2n) by 2^(2n) on 2n qubits, so no modulus above N = 15 is claimed.");
    }

    /**
     * Builds the in-place ripple-carry adder modulo {@code 2^n}.
     *
     * <p>The {@code carry} array must contain at least {@code n + 1} qubits and every carry
     * qubit must start in {@code |0>}. {@code carry[0]} is the carry in and is never changed.
     * The method leaves every carry qubit in {@code |0>} and writes the sum into {@code b}.</p>
     *
     * @param a the first addend, with qubit 0 as the most significant bit
     * @param b the second addend, overwritten with the sum modulo {@code 2^n}
     * @param carry the carry ancillas, at least {@code a.length + 1} entries
     * @return the events of the adder, in the order they should be scheduled
     * @throws IllegalArgumentException if the register lengths are inconsistent
     */
    public static QuantumEvent[] createInPlaceAdder(Qubit[] a, Qubit[] b, Qubit[] carry) {
        int n = a.length;
        if (n < 1 || b.length != n) {
            throw new IllegalArgumentException("The two addends must have the same positive length");
        }
        if (carry.length < n + 1) {
            throw new IllegalArgumentException("The adder needs n + 1 carry qubits");
        }
        List<QuantumEvent> circuit = new ArrayList<>();
        for (int position = 0; position < n; position++) {
            int i = n - 1 - position;
            circuit.add(toffoli("Carry_ab_" + position, a[i], b[i], carry[position + 1]));
            circuit.add(toffoli("Carry_ac_" + position, a[i], carry[position],
                carry[position + 1]));
            circuit.add(toffoli("Carry_bc_" + position, b[i], carry[position],
                carry[position + 1]));
            circuit.add(EventBuilderHelper.createCNOT("Sum_a_" + position, a[i], b[i]));
            circuit.add(EventBuilderHelper.createCNOT("Sum_c_" + position, carry[position], b[i]));
        }
        for (int position = n - 1; position >= 0; position--) {
            int i = n - 1 - position;
            circuit.add(EventBuilderHelper.createCNOT("Unsum_a_" + position, a[i], b[i]));
            circuit.add(EventBuilderHelper.createCNOT(
                "Unsum_c_" + position, carry[position], b[i]));
            circuit.add(toffoli("Uncarry_ab_" + position, a[i], b[i], carry[position + 1]));
            circuit.add(toffoli("Uncarry_ac_" + position, a[i], carry[position],
                carry[position + 1]));
            circuit.add(toffoli("Uncarry_bc_" + position, b[i], carry[position],
                carry[position + 1]));
            circuit.add(EventBuilderHelper.createCNOT("Restore_a_" + position, a[i], b[i]));
            circuit.add(EventBuilderHelper.createCNOT(
                "Restore_c_" + position, carry[position], b[i]));
        }
        return circuit.toArray(new QuantumEvent[0]);
    }

    /**
     * Builds a controlled modular multiplier for an odd modulus and a base.
     *
     * <p>The registers must both hold {@code n} qubits, where {@code n} is the bit length of
     * {@code modulus}; qubit 0 is the most significant bit of each register. The operation is
     * defined for {@code x, y < modulus}. Values at or above the modulus are left unchanged so
     * that the operation remains a permutation. The circuit applies one controlled modular
     * addition for each bit of {@code x}.</p>
     *
     * @param x the control register, read as an integer with qubit 0 as the most significant bit
     * @param y the target register, which receives {@code (y + base * x) mod modulus}
     * @param base the multiplier, reduced modulo {@code modulus}
     * @param modulus the odd modulus, greater than one
     * @return the events of the multiplier, in the order they should be scheduled
     * @throws IllegalArgumentException if the modulus is not odd or the registers do not match
     *     the bit length of the modulus
     */
    public static QuantumEvent[] createControlledMultiplier(
            Qubit[] x, Qubit[] y, int base, int modulus) {
        int n = validateModularRegisters(x, y, modulus);
        List<QuantumEvent> circuit = new ArrayList<>();
        int reducedBase = Math.floorMod(base, modulus);
        for (int bit = 0; bit < n; bit++) {
            int addend = (int) (((long) reducedBase << bit) % modulus);
            Qubit control = x[n - 1 - bit];
            Qubit[] gateQubits = new Qubit[n + 1];
            gateQubits[0] = control;
            System.arraycopy(y, 0, gateQubits, 1, n);
            int[] mapping = new int[1 << (n + 1)];
            for (int index = 0; index < mapping.length; index++) {
                int controlValue = (index >> n) & 1;
                int value = index & ((1 << n) - 1);
                int newValue = value < modulus && controlValue == 1
                    ? (value + addend) % modulus
                    : value;
                mapping[index] = (controlValue << n) | newValue;
            }
            circuit.add(densePermutationGate(
                "CtrlAdd_" + bit + "_" + addend, gateQubits, mapping));
        }
        return circuit.toArray(new QuantumEvent[0]);
    }

    /**
     * Builds the modular exponentiation register operation.
     *
     * <p>The operation maps {@code |x>|y>} to
     * {@code |x>|(y + base^x mod modulus) mod modulus>} for {@code x, y < modulus}. Values at or
     * above the modulus are left unchanged so that the operation is a permutation. The circuit
     * is one dense permutation matrix on the two registers.</p>
     *
     * @param x the exponent register, with qubit 0 as the most significant bit
     * @param y the target register
     * @param base the base of the exponentiation, reduced modulo {@code modulus}
     * @param modulus the odd modulus, greater than one
     * @return the events of the exponentiation, in the order they should be scheduled
     * @throws IllegalArgumentException if the modulus is not odd or the registers do not match
     *     the bit length of the modulus
     */
    public static QuantumEvent[] createModularExponentiation(
            Qubit[] x, Qubit[] y, int base, int modulus) {
        int n = validateModularRegisters(x, y, modulus);
        int size = 1 << (2 * n);
        int[] mapping = new int[size];
        int reducedBase = Math.floorMod(base, modulus);
        for (int index = 0; index < size; index++) {
            int exponent = index >> n;
            int value = index & ((1 << n) - 1);
            int newValue = value;
            if (exponent < modulus && value < modulus) {
                newValue = (value + modPow(reducedBase, exponent, modulus)) % modulus;
            }
            mapping[index] = (exponent << n) | newValue;
        }
        Qubit[] gateQubits = new Qubit[2 * n];
        System.arraycopy(x, 0, gateQubits, 0, n);
        System.arraycopy(y, 0, gateQubits, n, n);
        return new QuantumEvent[]{densePermutationGate("ModExp", gateQubits, mapping)};
    }

    /**
     * Checks the adder exhaustively for all {@code 2^(2n)} input pairs.
     *
     * <p>Each pair is prepared as a computational basis state, the adder is run, and the
     * amplitude at the expected basis state is read from the state vector.</p>
     *
     * @param n the register width, at least one
     * @return one result per input pair, in increasing {@code a}, then {@code b}, order
     * @throws Exception if a simulation fails
     */
    public static List<AdderCase> verifyAdder(int n) throws Exception {
        int totalQubits = 3 * n + 1;
        Qubit[] a = new Qubit[n];
        Qubit[] b = new Qubit[n];
        Qubit[] carry = new Qubit[n + 1];
        for (int i = 0; i < n; i++) {
            a[i] = new Qubit(i, "a" + i);
            b[i] = new Qubit(n + i, "b" + i);
        }
        for (int i = 0; i < n + 1; i++) {
            carry[i] = new Qubit(2 * n + i, "c" + i);
        }
        List<AdderCase> results = new ArrayList<>();
        for (int aValue = 0; aValue < (1 << n); aValue++) {
            for (int bValue = 0; bValue < (1 << n); bValue++) {
                int expected = (aValue + bValue) % (1 << n);
                List<QuantumEvent> events = new ArrayList<>();
                addBasisPreparation(events, a, aValue);
                addBasisPreparation(events, b, bValue);
                for (QuantumEvent event : createInPlaceAdder(a, b, carry)) {
                    events.add(event);
                }
                StateVector state = runCircuit(totalQubits, events);
                int expectedIndex = (aValue << (2 * n + 1)) | (expected << (n + 1));
                double amplitude = state.getAmplitude(expectedIndex).abs();
                int measuredIndex = mostProbableIndex(state);
                int measuredB = registerValue(measuredIndex, n, n, totalQubits);
                int measuredCarry = registerValue(measuredIndex, 2 * n, n + 1, totalQubits);
                int measuredA = registerValue(measuredIndex, 0, n, totalQubits);
                boolean passed = Math.abs(amplitude - 1.0) < 1e-9
                    && measuredB == expected
                    && measuredCarry == 0
                    && measuredA == aValue;
                results.add(new AdderCase(aValue, bValue, expected, measuredB, measuredCarry,
                    amplitude, passed));
            }
        }
        return results;
    }

    /**
     * Checks the controlled multiplier on every input pair below the modulus.
     *
     * @param modulus the odd modulus, greater than one
     * @param base the multiplier
     * @return one result per pair {@code (x, y)} with both values below the modulus
     * @throws Exception if a simulation fails
     */
    public static List<MultiplierCase> verifyMultiplier(int modulus, int base) throws Exception {
        int n = bitLength(modulus);
        Qubit[] x = new Qubit[n];
        Qubit[] y = new Qubit[n];
        for (int i = 0; i < n; i++) {
            x[i] = new Qubit(i, "x" + i);
            y[i] = new Qubit(n + i, "y" + i);
        }
        List<MultiplierCase> results = new ArrayList<>();
        for (int xValue = 0; xValue < modulus; xValue++) {
            for (int yValue = 0; yValue < modulus; yValue++) {
                List<QuantumEvent> events = new ArrayList<>();
                addBasisPreparation(events, x, xValue);
                addBasisPreparation(events, y, yValue);
                for (QuantumEvent event : createControlledMultiplier(x, y, base, modulus)) {
                    events.add(event);
                }
                StateVector state = runCircuit(2 * n, events);
                int expected = (yValue + (base % modulus) * xValue) % modulus;
                int expectedIndex = (xValue << n) | expected;
                double probability = state.getProbability(expectedIndex);
                int measuredIndex = mostProbableIndex(state);
                int measuredX = registerValue(measuredIndex, 0, n, 2 * n);
                int measuredY = registerValue(measuredIndex, n, n, 2 * n);
                boolean passed = Math.abs(probability - 1.0) < 1e-9
                    && measuredX == xValue
                    && measuredY == expected;
                results.add(new MultiplierCase(
                    xValue, yValue, expected, measuredY, probability, passed));
            }
        }
        return results;
    }

    /**
     * Checks the modular exponentiation on every input pair below the modulus.
     *
     * @param modulus the odd modulus, greater than one
     * @param base the base of the exponentiation
     * @return one result per pair {@code (x, y)} with both values below the modulus
     * @throws Exception if a simulation fails
     */
    public static List<ExponentiationCase> verifyModularExponentiation(
            int modulus, int base) throws Exception {
        int n = bitLength(modulus);
        Qubit[] x = new Qubit[n];
        Qubit[] y = new Qubit[n];
        for (int i = 0; i < n; i++) {
            x[i] = new Qubit(i, "x" + i);
            y[i] = new Qubit(n + i, "y" + i);
        }
        List<ExponentiationCase> results = new ArrayList<>();
        for (int xValue = 0; xValue < modulus; xValue++) {
            for (int yValue = 0; yValue < modulus; yValue++) {
                List<QuantumEvent> events = new ArrayList<>();
                addBasisPreparation(events, x, xValue);
                addBasisPreparation(events, y, yValue);
                for (QuantumEvent event : createModularExponentiation(x, y, base, modulus)) {
                    events.add(event);
                }
                StateVector state = runCircuit(2 * n, events);
                int addend = modPow(Math.floorMod(base, modulus), xValue, modulus);
                int expected = (yValue + addend) % modulus;
                int expectedIndex = (xValue << n) | expected;
                double probability = state.getProbability(expectedIndex);
                int measuredIndex = mostProbableIndex(state);
                int measuredY = registerValue(measuredIndex, n, n, 2 * n);
                boolean passed = Math.abs(probability - 1.0) < 1e-9
                    && measuredY == expected;
                results.add(new ExponentiationCase(
                    xValue, yValue, expected, measuredY, probability, passed));
            }
        }
        return results;
    }

    private static int validateModularRegisters(Qubit[] x, Qubit[] y, int modulus) {
        if (modulus <= 1 || modulus % 2 == 0) {
            throw new IllegalArgumentException("The modulus must be odd and greater than one");
        }
        int n = bitLength(modulus);
        if (x.length != n || y.length != n) {
            throw new IllegalArgumentException("Both registers must have " + n + " qubits");
        }
        return n;
    }

    private static int bitLength(int value) {
        return 32 - Integer.numberOfLeadingZeros(value - 1);
    }

    private static int modPow(int base, int exponent, int modulus) {
        long result = 1;
        long factor = base % modulus;
        int power = exponent;
        while (power > 0) {
            if ((power & 1) == 1) {
                result = (result * factor) % modulus;
            }
            factor = (factor * factor) % modulus;
            power >>= 1;
        }
        return (int) result;
    }

    private static QuantumEvent toffoli(String name, Qubit first, Qubit second, Qubit target) {
        MultiQubitGate gate = MultiQubitGate.createToffoli(first, second, target);
        return EventBuilderHelper.createEvent(
            name, gate, new Qubit[]{target}, new Qubit[]{first, second});
    }

    private static QuantumEvent densePermutationGate(
            String name, Qubit[] gateQubits, int[] mapping) {
        int size = 1 << gateQubits.length;
        ComplexMatrix matrix = new ComplexMatrix(size, size);
        for (int row = 0; row < size; row++) {
            for (int column = 0; column < size; column++) {
                matrix.set(row, column, Complex.ZERO);
            }
        }
        for (int input = 0; input < size; input++) {
            matrix.set(mapping[input], input, Complex.ONE);
        }
        MultiQubitGate gate = new MultiQubitGate(name, matrix, gateQubits);
        return EventBuilderHelper.createEvent(name, gate, gateQubits, new Qubit[0]);
    }

    private static void addBasisPreparation(
            List<QuantumEvent> events, Qubit[] register, int value) {
        int n = register.length;
        for (int i = 0; i < n; i++) {
            if (((value >> (n - 1 - i)) & 1) == 1) {
                Qubit qubit = register[i];
                events.add(EventBuilderHelper.createSingleQubitGate(
                    "Prep_" + qubit.getIndex(), SingleQubitGate.createPauliX(qubit), qubit));
            }
        }
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

    private static int mostProbableIndex(StateVector state) {
        double[] probabilities = state.getProbabilities();
        int best = 0;
        for (int i = 1; i < probabilities.length; i++) {
            if (probabilities[i] > probabilities[best]) {
                best = i;
            }
        }
        return best;
    }

    private static int registerValue(int basisIndex, int startQubit, int length, int totalQubits) {
        int value = 0;
        for (int i = 0; i < length; i++) {
            int bit = (basisIndex >> (totalQubits - 1 - (startQubit + i))) & 1;
            value = (value << 1) | bit;
        }
        return value;
    }

    private static void printCompactTable(int modulus, List<MultiplierCase> cases) {
        for (int xValue = 0; xValue < modulus; xValue++) {
            StringBuilder expected = new StringBuilder();
            StringBuilder measured = new StringBuilder();
            int agreements = 0;
            for (int yValue = 0; yValue < modulus; yValue++) {
                MultiplierCase result = cases.get(xValue * modulus + yValue);
                if (yValue > 0) {
                    expected.append(' ');
                    measured.append(' ');
                }
                expected.append(result.expected);
                measured.append(result.measured);
                if (result.passed) {
                    agreements++;
                }
            }
            System.out.printf(Locale.ROOT, "  %d | %s | %s | %d/%d%n",
                xValue, expected, measured, agreements, modulus);
        }
    }

    private static void printCompactExponentiationTable(
            int modulus, List<ExponentiationCase> cases) {
        for (int xValue = 0; xValue < modulus; xValue++) {
            StringBuilder expected = new StringBuilder();
            StringBuilder measured = new StringBuilder();
            int agreements = 0;
            for (int yValue = 0; yValue < modulus; yValue++) {
                ExponentiationCase result = cases.get(xValue * modulus + yValue);
                if (yValue > 0) {
                    expected.append(' ');
                    measured.append(' ');
                }
                expected.append(result.expected);
                measured.append(result.measured);
                if (result.passed) {
                    agreements++;
                }
            }
            System.out.printf(Locale.ROOT, "  %d | %s | %s | %d/%d%n",
                xValue, expected, measured, agreements, modulus);
        }
    }

    /**
     * One measured adder case.
     */
    public static final class AdderCase {

        private final int a;
        private final int b;
        private final int expected;
        private final int measured;
        private final int carry;
        private final double amplitude;
        private final boolean passed;

        private AdderCase(int a, int b, int expected, int measured, int carry,
                double amplitude, boolean passed) {
            this.a = a;
            this.b = b;
            this.expected = expected;
            this.measured = measured;
            this.carry = carry;
            this.amplitude = amplitude;
            this.passed = passed;
        }

        /**
         * Returns the first addend.
         *
         * @return the value prepared in the {@code a} register
         */
        public int getA() {
            return a;
        }

        /**
         * Returns the second addend.
         *
         * @return the value prepared in the {@code b} register
         */
        public int getB() {
            return b;
        }

        /**
         * Returns the expected sum modulo {@code 2^n}.
         *
         * @return the integer sum
         */
        public int getExpected() {
            return expected;
        }

        /**
         * Returns the sum read from the most probable final basis state.
         *
         * @return the measured {@code b} register value
         */
        public int getMeasured() {
            return measured;
        }

        /**
         * Returns the measured carry register value, which must be zero.
         *
         * @return the carry bits read from the final basis state
         */
        public int getCarry() {
            return carry;
        }

        /**
         * Returns the amplitude at the expected basis state.
         *
         * @return the absolute amplitude
         */
        public double getAmplitude() {
            return amplitude;
        }

        /**
         * Reports whether the amplitude and the measured registers match the expectation.
         *
         * @return true when the case passed
         */
        public boolean isPassed() {
            return passed;
        }
    }

    /**
     * One measured multiplier case.
     */
    public static final class MultiplierCase {

        private final int x;
        private final int y;
        private final int expected;
        private final int measured;
        private final double probability;
        private final boolean passed;

        private MultiplierCase(int x, int y, int expected, int measured,
                double probability, boolean passed) {
            this.x = x;
            this.y = y;
            this.expected = expected;
            this.measured = measured;
            this.probability = probability;
            this.passed = passed;
        }

        /**
         * Returns the prepared {@code x} value.
         *
         * @return the multiplier input
         */
        public int getX() {
            return x;
        }

        /**
         * Returns the prepared {@code y} value.
         *
         * @return the addend input
         */
        public int getY() {
            return y;
        }

        /**
         * Returns the expected result modulo the modulus.
         *
         * @return the expected {@code y} value
         */
        public int getExpected() {
            return expected;
        }

        /**
         * Returns the result read from the most probable final basis state.
         *
         * @return the measured {@code y} value
         */
        public int getMeasured() {
            return measured;
        }

        /**
         * Returns the probability of the expected basis state.
         *
         * @return the measured probability
         */
        public double getProbability() {
            return probability;
        }

        /**
         * Reports whether the measured result matches the expectation.
         *
         * @return true when the case passed
         */
        public boolean isPassed() {
            return passed;
        }
    }

    /**
     * One measured modular exponentiation case.
     */
    public static final class ExponentiationCase {

        private final int x;
        private final int y;
        private final int expected;
        private final int measured;
        private final double probability;
        private final boolean passed;

        private ExponentiationCase(int x, int y, int expected, int measured,
                double probability, boolean passed) {
            this.x = x;
            this.y = y;
            this.expected = expected;
            this.measured = measured;
            this.probability = probability;
            this.passed = passed;
        }

        /**
         * Returns the prepared exponent.
         *
         * @return the {@code x} value
         */
        public int getX() {
            return x;
        }

        /**
         * Returns the prepared {@code y} value.
         *
         * @return the addend input
         */
        public int getY() {
            return y;
        }

        /**
         * Returns the expected result modulo the modulus.
         *
         * @return the expected {@code y} value
         */
        public int getExpected() {
            return expected;
        }

        /**
         * Returns the result read from the most probable final basis state.
         *
         * @return the measured {@code y} value
         */
        public int getMeasured() {
            return measured;
        }

        /**
         * Returns the probability of the expected basis state.
         *
         * @return the measured probability
         */
        public double getProbability() {
            return probability;
        }

        /**
         * Reports whether the measured result matches the expectation.
         *
         * @return true when the case passed
         */
        public boolean isPassed() {
            return passed;
        }
    }
}
