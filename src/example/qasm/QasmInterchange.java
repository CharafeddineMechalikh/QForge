package example.qasm;

import com.mechalikh.qforge.classical.ShotRunner;
import com.mechalikh.qforge.config.SimulationConfig;
import com.mechalikh.qforge.core.QuantumSimulator;
import com.mechalikh.qforge.core.SimulationResult;
import com.mechalikh.qforge.events.QuantumEvent;
import com.mechalikh.qforge.math.RandomGenerator;
import com.mechalikh.qforge.operations.QuantumOperation;
import com.mechalikh.qforge.qasm.QasmEmitter;
import com.mechalikh.qforge.qasm.QasmException;
import com.mechalikh.qforge.qasm.QasmParser;
import com.mechalikh.qforge.qasm.QasmProgram;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.TreeMap;

/**
 * Imports, runs, exports and re-imports OpenQASM.
 *
 * <p>The example loads a teleportation circuit from a {@code .qasm} file, builds a configuration
 * from the registers the program declares, schedules every operation, and checks that the
 * conditional corrections leave Bob's qubit in the payload state. It then emits the parsed
 * program back to OpenQASM 2.0, parses the emitted text again, and verifies that a second emission
 * is byte for byte identical and that all 200 shots still land on the same classical outcome.</p>
 *
 * <p>Two further cases show the edges of the supported subset: an OpenQASM 3.0 declaration block
 * is accepted, and a {@code reset} instruction is rejected with the parser's message.</p>
 */
public final class QasmInterchange {

    private static final Path SOURCE = Paths.get("src", "example", "qasm", "teleportation.qasm");
    private static final int SHOTS = 200;
    private static final long SEED = 20261029L;

    private QasmInterchange() {
    }

    public static void main(String[] args) throws Exception {
        System.out.println("=== OpenQASM interchange ===");
        System.out.println("Source file: " + SOURCE);

        QasmParser parser = new QasmParser();
        QasmProgram program = parser.parse(Files.readString(SOURCE));
        System.out.printf(Locale.ROOT, "%nParsed program: %d qubits, %d operations, registers %s%n",
            program.getNumQubits(), program.getOperations().size(), program.getRegisters());

        SimulationConfig config = configFor(program);
        SimulationResult single = run(config, program.getOperations());
        System.out.println("\nRegister values after one run:");
        for (Map.Entry<String, Long> entry : new TreeMap<>(single.getRegisterValues()).entrySet()) {
            System.out.printf(Locale.ROOT, "  %-4s = %d%n", entry.getKey(), entry.getValue());
        }

        String emitted = new QasmEmitter().emit(program);
        System.out.println("\nEmitted OpenQASM 2.0:");
        for (String line : emitted.split("\n")) {
            System.out.println("  " + line);
        }

        QasmProgram reparsed = parser.parse(emitted);
        String emittedAgain = new QasmEmitter().emit(reparsed);
        System.out.printf(Locale.ROOT, "%nRound trip: operations %d -> %d, text stable = %s%n",
            program.getOperations().size(), reparsed.getOperations().size(),
            emitted.equals(emittedAgain));

        RandomGenerator.setRandom(new Random(SEED));
        ShotRunner.ShotStatistics statistics = ShotRunner.run(config,
            () -> events(reparsed.getOperations()), SHOTS);
        System.out.printf(Locale.ROOT, "%nClassical registers over %d shots of the reparsed "
            + "circuit:%n", SHOTS);
        for (String register : new String[] {"a", "b", "out"}) {
            StringBuilder line = new StringBuilder("  " + register + ":");
            for (long value = 0; value <= 1; value++) {
                line.append(String.format(Locale.ROOT, "  %d -> %.4f", value,
                    statistics.frequency(register, value)));
            }
            System.out.println(line);
        }
        boolean teleported = statistics.frequency("out", 1L) == 1.0;
        System.out.println(teleported
            ? "SUCCESS: every shot recorded out=1, the teleported payload"
            : "FAILED: the payload was not recovered in every shot");

        System.out.println("\nOpenQASM 3.0 declarations:");
        QasmProgram qasm3 = parser.parse("OPENQASM 3.0;\n"
            + "include \"stdgates.inc\";\n"
            + "qubit[2] q;\n"
            + "bit[2] c;\n"
            + "h q[0];\n"
            + "cx q[0],q[1];\n"
            + "measure q -> c;\n");
        System.out.printf(Locale.ROOT, "  2 qubits, 1 classical register of width 2, "
            + "%d operations%n", qasm3.getOperations().size());

        System.out.println("\nUnsupported instruction:");
        try {
            parser.parse("OPENQASM 2.0;\nqreg q[1];\nreset q[0];\n");
            System.out.println("  reset accepted (unexpected)");
        } catch (QasmException e) {
            System.out.println("  " + e.getMessage());
        }
    }

    private static SimulationConfig configFor(QasmProgram program) {
        SimulationConfig config = new SimulationConfig();
        config.setNumQubits(program.getNumQubits());
        for (Map.Entry<String, Integer> register : program.getRegisters().entrySet()) {
            config.addClassicalRegister(register.getKey(), register.getValue());
        }
        return config;
    }

    private static List<QuantumEvent> events(List<QuantumOperation> operations) {
        Instant time = Instant.now().minusSeconds(5);
        List<QuantumEvent> events = new ArrayList<>(operations.size());
        int index = 0;
        for (QuantumOperation operation : operations) {
            events.add(new QuantumEvent.Builder("qasm_event_" + index)
                .withScheduledTime(time)
                .withOperation(operation)
                .build());
            index++;
            time = time.plusMillis(1);
        }
        return events;
    }

    private static SimulationResult run(SimulationConfig config,
                                        List<QuantumOperation> operations) throws Exception {
        QuantumSimulator simulator = new QuantumSimulator();
        simulator.initialize(config);
        for (QuantumEvent event : events(operations)) {
            simulator.scheduleEvent(event);
        }
        return simulator.run();
    }
}
