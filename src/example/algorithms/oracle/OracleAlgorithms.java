package example.algorithms.oracle;

import com.mechalikh.qforge.config.SimulationBackend;
import com.mechalikh.qforge.config.SimulationConfig;
import com.mechalikh.qforge.core.QuantumSimulator;
import com.mechalikh.qforge.events.QuantumEvent;
import com.mechalikh.qforge.operations.SingleQubitGate;
import com.mechalikh.qforge.state.Qubit;

import example.EventBuilderHelper;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Runs the three classic query-model algorithms that separate constant from balanced oracles:
 * Deutsch, Deutsch-Jozsa and Bernstein-Vazirani.
 *
 * <p>Every demo prepares the query register in a uniform superposition, puts the ancilla in
 * {@code |->}, applies a phase oracle through phase kickback, and finishes with Hadamards on the
 * query register. The measured query register then carries the answer directly. All three demos
 * read the joint probability distribution of the final state, so the numbers printed here are the
 * exact state-vector probabilities rather than sampling noise.</p>
 *
 * <p>Qubit {@code 0} is the most significant bit of a basis-state index, and the ancilla is the
 * least significant qubit, which is why the marginal extraction below shifts a query value left by
 * one bit before indexing the probability array.</p>
 */
public final class OracleAlgorithms {

    private OracleAlgorithms() {
    }

    public static void main(String[] args) throws Exception {
        System.out.println("=== Oracle Algorithms: Deutsch, Deutsch-Jozsa, Bernstein-Vazirani ===");
        demoDeutsch();
        demoDeutschJozsa();
        demoBernsteinVazirani();
    }

    private static void demoDeutsch() throws Exception {
        System.out.println("\n1. === Deutsch's Algorithm ===");
        System.out.println("Separating constant from balanced 1-bit oracles with one query");

        SimulationConfig config = new SimulationConfig();
        config.setNumQubits(2);
        config.setBackend(SimulationBackend.STATE_VECTOR);

        Qubit input = new Qubit(0, "x");
        Qubit ancilla = new Qubit(1, "aux");

        String[] labels = {"f(x) = 0 (constant)", "f(x) = 1 (constant)",
            "f(x) = x (balanced)", "f(x) = NOT x (balanced)"};
        boolean[] expectedBalanced = {false, false, true, true};

        for (int oracle = 0; oracle < 4; oracle++) {
            EventBuilderHelper.resetTimer();
            List<QuantumEvent> events = new ArrayList<>();
            events.add(EventBuilderHelper.createSingleQubitGate(
                "H_in", SingleQubitGate.createHadamard(input), input));
            events.add(EventBuilderHelper.createSingleQubitGate(
                "X_aux", SingleQubitGate.createPauliX(ancilla), ancilla));
            events.add(EventBuilderHelper.createSingleQubitGate(
                "H_aux", SingleQubitGate.createHadamard(ancilla), ancilla));

            switch (oracle) {
                case 0 -> { /* constant 0: identity oracle */ }
                case 1 -> events.add(EventBuilderHelper.createSingleQubitGate(
                    "X_aux2", SingleQubitGate.createPauliX(ancilla), ancilla));
                case 2 -> events.add(EventBuilderHelper.createCNOT("CNOT", input, ancilla));
                default -> {
                    events.add(EventBuilderHelper.createCNOT("CNOT", input, ancilla));
                    events.add(EventBuilderHelper.createSingleQubitGate(
                        "X_aux3", SingleQubitGate.createPauliX(ancilla), ancilla));
                }
            }

            events.add(EventBuilderHelper.createSingleQubitGate(
                "H_in2", SingleQubitGate.createHadamard(input), input));

            double[] probs = run(config, events);
            double[] query = inputMarginal(probs, 1);
            boolean balanced = query[1] > 0.99;
            boolean correct = balanced == expectedBalanced[oracle];
            System.out.printf(Locale.ROOT,
                "  %-24s P(query=0)=%.4f P(query=1)=%.4f -> %s (%s)%n",
                labels[oracle], query[0], query[1],
                balanced ? "balanced" : "constant", correct ? "SUCCESS" : "FAILED");
        }
    }

    private static void demoDeutschJozsa() throws Exception {
        System.out.println("\n2. === Deutsch-Jozsa Algorithm ===");
        int n = 3;
        System.out.printf(Locale.ROOT,
            "Distinguishing constant from balanced 3-bit oracles in one query%n");

        SimulationConfig config = new SimulationConfig();
        config.setNumQubits(n + 1);
        config.setBackend(SimulationBackend.STATE_VECTOR);

        Qubit[] query = qubits(n);
        Qubit ancilla = new Qubit(n, "aux");

        // Constant zero: no oracle at all.
        double[] constant = queryRegisterDistribution(runDeutschJozsa(config, query, ancilla, -1), n);
        double constantZeroMass = constant[0];
        System.out.printf(Locale.ROOT,
            "  constant f(x)=0     : P(query=000)=%.4f -> %s%n",
            constantZeroMass, constantZeroMass > 0.99 ? "constant (SUCCESS)" : "FAILED");

        // Balanced f(x) = x0: a single CNOT from the most significant query bit.
        double[] balanced = queryRegisterDistribution(runDeutschJozsa(config, query, ancilla, 0), n);
        int expected = 1 << (n - 1); // query pattern 100 for f(x) = x0
        System.out.printf(Locale.ROOT,
            "  balanced f(x)=x0    : P(query=%s)=%.4f -> %s%n",
            toBinary(expected, n), balanced[expected],
            balanced[expected] > 0.99 ? "balanced (SUCCESS)" : "FAILED");
        System.out.printf(Locale.ROOT,
            "  all-zeros mass      : constant=%.4f balanced=%.2e%n",
            constant[0], balanced[0]);
    }

    private static void demoBernsteinVazirani() throws Exception {
        System.out.println("\n3. === Bernstein-Vazirani Algorithm ===");
        int n = 3;
        int secret = 0b101;
        System.out.printf(Locale.ROOT,
            "Recovering a hidden 3-bit string in one query (secret = %s)%n", toBinary(secret, n));

        SimulationConfig config = new SimulationConfig();
        config.setNumQubits(n + 1);
        config.setBackend(SimulationBackend.STATE_VECTOR);

        Qubit[] query = qubits(n);
        Qubit ancilla = new Qubit(n, "aux");

        EventBuilderHelper.resetTimer();
        List<QuantumEvent> events = new ArrayList<>();
        for (Qubit q : query) {
            events.add(EventBuilderHelper.createSingleQubitGate(
                "H_" + q.getIndex(), SingleQubitGate.createHadamard(q), q));
        }
        events.add(EventBuilderHelper.createSingleQubitGate(
            "X_aux", SingleQubitGate.createPauliX(ancilla), ancilla));
        events.add(EventBuilderHelper.createSingleQubitGate(
            "H_aux", SingleQubitGate.createHadamard(ancilla), ancilla));

        for (int k = 0; k < n; k++) {
            if (((secret >> (n - 1 - k)) & 1) == 1) {
                events.add(EventBuilderHelper.createCNOT("CNOT_" + k, query[k], ancilla));
            }
        }
        for (Qubit q : query) {
            events.add(EventBuilderHelper.createSingleQubitGate(
                "H2_" + q.getIndex(), SingleQubitGate.createHadamard(q), q));
        }

        double[] distribution = queryRegisterDistribution(run(config, events), n);
        int recovered = argmax(distribution);
        System.out.printf(Locale.ROOT,
            "  P(query=%s)=%.4f -> recovered %s (%s)%n",
            toBinary(secret, n), distribution[secret], toBinary(recovered, n),
            recovered == secret ? "SUCCESS" : "FAILED");
    }

    private static double[] runDeutschJozsa(SimulationConfig config, Qubit[] query,
                                            Qubit ancilla, int cnotControl) throws Exception {
        EventBuilderHelper.resetTimer();
        List<QuantumEvent> events = new ArrayList<>();
        for (Qubit q : query) {
            events.add(EventBuilderHelper.createSingleQubitGate(
                "H_" + q.getIndex(), SingleQubitGate.createHadamard(q), q));
        }
        events.add(EventBuilderHelper.createSingleQubitGate(
            "X_aux", SingleQubitGate.createPauliX(ancilla), ancilla));
        events.add(EventBuilderHelper.createSingleQubitGate(
            "H_aux", SingleQubitGate.createHadamard(ancilla), ancilla));
        if (cnotControl >= 0) {
            events.add(EventBuilderHelper.createCNOT("CNOT", query[cnotControl], ancilla));
        }
        for (Qubit q : query) {
            events.add(EventBuilderHelper.createSingleQubitGate(
                "H2_" + q.getIndex(), SingleQubitGate.createHadamard(q), q));
        }
        return run(config, events);
    }

    private static double[] run(SimulationConfig config, List<QuantumEvent> events) throws Exception {
        QuantumSimulator simulator = new QuantumSimulator();
        simulator.initialize(config);
        for (QuantumEvent event : events) {
            simulator.scheduleEvent(event);
        }
        double[] probs = simulator.run().getFinalState().getProbabilities();
        simulator.reset();
        return probs;
    }

    private static Qubit[] qubits(int n) {
        Qubit[] qubits = new Qubit[n];
        for (int i = 0; i < n; i++) {
            qubits[i] = new Qubit(i, "q" + i);
        }
        return qubits;
    }

    /** Marginal over the {@code n} query qubits, with the ancilla drop-out on the least significant bit. */
    private static double[] queryRegisterDistribution(double[] probs, int n) {
        double[] marginal = new double[1 << n];
        for (int value = 0; value < marginal.length; value++) {
            marginal[value] = probs[value << 1] + probs[(value << 1) | 1];
        }
        return marginal;
    }

    private static double[] inputMarginal(double[] probs, int queryBits) {
        return queryRegisterDistribution(probs, queryBits);
    }

    private static int argmax(double[] values) {
        int best = 0;
        for (int i = 1; i < values.length; i++) {
            if (values[i] > values[best]) {
                best = i;
            }
        }
        return best;
    }

    private static String toBinary(int value, int bits) {
        String binary = Integer.toBinaryString(value);
        while (binary.length() < bits) {
            binary = "0" + binary;
        }
        return binary;
    }
}
