package com.mechalikh.qforge;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.mechalikh.qforge.config.SimulationConfig;
import com.mechalikh.qforge.core.QuantumSimulator;
import com.mechalikh.qforge.core.SimulationResult;
import com.mechalikh.qforge.events.QuantumEvent;
import com.mechalikh.qforge.math.Complex;
import com.mechalikh.qforge.math.ComplexMatrix;
import com.mechalikh.qforge.measurement.MeasurementOperator;
import com.mechalikh.qforge.operations.ConditionalOperation;
import com.mechalikh.qforge.operations.MultiQubitGate;
import com.mechalikh.qforge.operations.QuantumGate;
import com.mechalikh.qforge.operations.QuantumOperation;
import com.mechalikh.qforge.operations.SingleQubitGate;
import com.mechalikh.qforge.qasm.QasmEmitter;
import com.mechalikh.qforge.qasm.QasmException;
import com.mechalikh.qforge.qasm.QasmParser;
import com.mechalikh.qforge.qasm.QasmProgram;
import com.mechalikh.qforge.state.Qubit;
import com.mechalikh.qforge.state.StateVector;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

public class QasmTest {
    private static final double EPS = 1e-9;
    private static final double ROUND_TRIP_EPS = 1e-6;
    private static final QasmParser PARSER = new QasmParser();
    private static final QasmEmitter EMITTER = new QasmEmitter();

    private static final String BELL = "OPENQASM 2.0;\n"
        + "include \"qelib1.inc\";\n"
        + "qreg q[2];\n"
        + "creg c[2];\n"
        + "h q[0];\n"
        + "cx q[0],q[1];\n";

    private static final String GHZ = "OPENQASM 2.0;\n"
        + "include \"qelib1.inc\";\n"
        + "qreg q[3];\n"
        + "h q[0];\n"
        + "cx q[0],q[1];\n"
        + "cx q[1],q[2];\n";

    private static final String CONDITIONAL_BELL = "OPENQASM 2.0;\n"
        + "include \"qelib1.inc\";\n"
        + "qreg q[2];\n"
        + "creg c[2];\n"
        + "h q[0];\n"
        + "cx q[0],q[1];\n"
        + "measure q[0] -> c[0];\n"
        + "if(c==1) x q[1];\n";

    @Test
    public void bellStateMatchesGateLibrary() throws Exception {
        QasmProgram program = PARSER.parse(BELL);
        assertEquals(2, program.getNumQubits());
        assertEquals(2, program.getOperations().size());

        StateVector actual = simulate(program);
        StateVector expected = simulateGates(2, Arrays.asList(
            SingleQubitGate.createHadamard(new Qubit(0)),
            MultiQubitGate.createCNOT(new Qubit(0), new Qubit(1))));

        assertEquals(1.0 / Math.sqrt(2.0), actual.getAmplitude(0).real(), EPS);
        assertEquals(0.0, actual.getAmplitude(1).abs(), EPS);
        assertEquals(0.0, actual.getAmplitude(2).abs(), EPS);
        assertEquals(1.0 / Math.sqrt(2.0), actual.getAmplitude(3).real(), EPS);
        assertStateEquals(expected, actual, EPS);
    }

    @Test
    public void ghzStateMatchesGateLibrary() throws Exception {
        QasmProgram program = PARSER.parse(GHZ);
        assertEquals(3, program.getNumQubits());

        StateVector actual = simulate(program);
        StateVector expected = simulateGates(3, Arrays.asList(
            SingleQubitGate.createHadamard(new Qubit(0)),
            MultiQubitGate.createCNOT(new Qubit(0), new Qubit(1)),
            MultiQubitGate.createCNOT(new Qubit(1), new Qubit(2))));

        assertEquals(1.0 / Math.sqrt(2.0), actual.getAmplitude(0).real(), EPS);
        assertEquals(1.0 / Math.sqrt(2.0), actual.getAmplitude(7).real(), EPS);
        for (int i = 1; i < 7; i++) {
            assertEquals(0.0, actual.getAmplitude(i).abs(), EPS);
        }
        assertStateEquals(expected, actual, EPS);
    }

    @Test
    public void quantumRegistersMapLocalBitsToGlobalQubits() {
        QasmProgram program = PARSER.parse("OPENQASM 2.0;\n"
            + "qreg a[2];\n"
            + "qreg b[3];\n"
            + "creg c[2];\n"
            + "creg d[4];\n"
            + "h b[0];\n"
            + "x a[1];\n");

        assertEquals(5, program.getNumQubits());
        assertEquals(0, program.getQuantumRegisterOffset("a"));
        assertEquals(2, program.getQuantumRegisterOffset("b"));
        assertEquals(Arrays.asList("c", "d"), new ArrayList<>(program.getRegisters().keySet()));
        assertEquals(3, program.getQuantumRegisters().get("b"));

        QuantumOperation first = program.getOperations().get(0);
        assertEquals(2, first.getTargetQubits()[0].getIndex());
        assertSame(program.getQubit(2), first.getTargetQubits()[0]);
        assertEquals("q2", first.getTargetQubits()[0].getLabel());

        QuantumOperation second = program.getOperations().get(1);
        assertEquals(1, second.getTargetQubits()[0].getIndex());
        assertSame(program.getQubit(1), second.getTargetQubits()[0]);

        assertThrows(IllegalArgumentException.class,
            () -> program.getQuantumRegisterOffset("missing"));
    }

    @Test
    public void qasm3DeclarationsAndWholeRegisterMeasurement() {
        QasmProgram program = PARSER.parse("OPENQASM 3.0;\n"
            + "include \"stdgates.inc\";\n"
            + "qubit[3] q;\n"
            + "bit[3] c;\n"
            + "h q[0];\n"
            + "measure q -> c;\n");

        assertEquals(3, program.getNumQubits());
        assertEquals(Map.of("q", 3), program.getQuantumRegisters());
        assertEquals(Map.of("c", 3), program.getRegisters());
        assertEquals(4, program.getOperations().size());

        for (int i = 0; i < 3; i++) {
            MeasurementOperator measurement =
                (MeasurementOperator) program.getOperations().get(i + 1);
            assertEquals("c", measurement.getRegisterName());
            assertEquals(i, measurement.getRegisterOffset());
            assertEquals(i, measurement.getTargetQubits()[0].getIndex());
            assertTrue(measurement.isDestructive());
            assertTrue(measurement.isComputationalBasis());
        }
    }

    @Test
    public void u3GateUsesExactMatrix() throws Exception {
        QasmProgram program = PARSER.parse("OPENQASM 2.0;\n"
            + "qreg q[1];\n"
            + "u3(pi/2,pi/3,pi/4) q[0];\n");
        QuantumGate gate = (QuantumGate) program.getOperations().get(0);
        assertTrue(gate instanceof QuantumGate.CustomGate);

        double theta = Math.PI / 2.0;
        double phi = Math.PI / 3.0;
        double lambda = Math.PI / 4.0;
        double cosine = Math.cos(theta / 2.0);
        double sine = Math.sin(theta / 2.0);

        ComplexMatrix actual = gate.getMatrix();
        ComplexMatrix expected = new ComplexMatrix(2, 2);
        expected.set(0, 0, new Complex(cosine, 0.0));
        expected.set(0, 1, Complex.fromPolar(sine, lambda).negate());
        expected.set(1, 0, Complex.fromPolar(sine, phi));
        expected.set(1, 1, Complex.fromPolar(cosine, phi + lambda));
        assertMatrixEquals(expected, actual, 1e-12);

        StateVector state = simulate(program);
        assertEquals(cosine, state.getAmplitude(0).real(), EPS);
        assertEquals(0.0, state.getAmplitude(0).imag(), EPS);
        assertEquals(sine * Math.cos(phi), state.getAmplitude(1).real(), EPS);
        assertEquals(sine * Math.sin(phi), state.getAmplitude(1).imag(), EPS);

        QasmProgram u1 = PARSER.parse("OPENQASM 2.0;\nqreg q[1];\nu1(pi/2) q[0];\n");
        ComplexMatrix u1Matrix = ((QuantumGate) u1.getOperations().get(0)).getMatrix();
        assertEquals(1.0, u1Matrix.get(0, 0).real(), 1e-12);
        assertEquals(0.0, u1Matrix.get(0, 1).abs(), 1e-12);
        assertEquals(0.0, u1Matrix.get(1, 0).abs(), 1e-12);
        assertEquals(0.0, u1Matrix.get(1, 1).real(), 1e-12);
        assertEquals(1.0, u1Matrix.get(1, 1).imag(), 1e-12);

        QasmProgram u2 = PARSER.parse("OPENQASM 2.0;\nqreg q[1];\nu2(0,0) q[0];\n");
        ComplexMatrix u2Matrix = ((QuantumGate) u2.getOperations().get(0)).getMatrix();
        double factor = 1.0 / Math.sqrt(2.0);
        ComplexMatrix u2Expected = new ComplexMatrix(2, 2);
        u2Expected.set(0, 0, new Complex(factor, 0.0));
        u2Expected.set(0, 1, new Complex(-factor, 0.0));
        u2Expected.set(1, 0, new Complex(factor, 0.0));
        u2Expected.set(1, 1, new Complex(factor, 0.0));
        assertMatrixEquals(u2Expected, u2Matrix, 1e-12);
        assertEquals(factor, u2Matrix.get(0, 0).real(), 1e-12);
    }

    @Test
    public void userDefinedGateIsExpandedRecursively() throws Exception {
        QasmProgram program = PARSER.parse("OPENQASM 2.0;\n"
            + "include \"qelib1.inc\";\n"
            + "qreg q[2];\n"
            + "gate bell a,b { h a; cx a,b; }\n"
            + "gate phasebell(a) x,y { bell x,y; rz(a) x; }\n"
            + "phasebell(pi/2) q[0],q[1];\n");

        assertEquals(3, program.getOperations().size());
        StateVector actual = simulate(program);
        StateVector expected = simulateGates(2, Arrays.asList(
            SingleQubitGate.createHadamard(new Qubit(0)),
            MultiQubitGate.createCNOT(new Qubit(0), new Qubit(1)),
            SingleQubitGate.createRZ(Math.PI / 2.0, new Qubit(0))));
        assertStateEquals(expected, actual, EPS);
    }

    @Test
    public void conditionalOperationTestsAgainstRegisterValue() {
        QasmProgram program = PARSER.parse(CONDITIONAL_BELL);
        assertEquals(4, program.getOperations().size());

        ConditionalOperation conditional =
            (ConditionalOperation) program.getOperations().get(3);
        assertEquals("c", conditional.getRegisterName());
        assertEquals(1L, conditional.getExpectedValue());
        assertEquals(3L, conditional.getMask());
        assertTrue(conditional.getGuardedOperation() instanceof QuantumGate);
        assertFalse(conditional.test(0L));
        assertTrue(conditional.test(1L));
        assertFalse(conditional.test(2L));
        assertFalse(conditional.test(3L));

        QasmProgram narrow = PARSER.parse("OPENQASM 2.0;\n"
            + "qreg q[1];\n"
            + "creg c[1];\n"
            + "if(c==1) x q[0];\n");
        ConditionalOperation narrowConditional =
            (ConditionalOperation) narrow.getOperations().get(0);
        assertEquals(1L, narrowConditional.getMask());
        assertTrue(narrowConditional.test(1L));
        assertFalse(narrowConditional.test(2L));
    }

    @Test
    public void conditionalAfterBellMeasurementClearsThePartner() throws Exception {
        QasmProgram program = PARSER.parse(CONDITIONAL_BELL);
        SimulationResult result = run(program);
        StateVector state = (StateVector) result.getFinalState();

        long registerValue = result.getRegisterValues().get("c");
        int expectedIndex = (int) (registerValue << 1);
        assertEquals(1.0, state.getProbability(expectedIndex), EPS);
        for (int i = 0; i < 4; i++) {
            if (i != expectedIndex) {
                assertEquals(0.0, state.getProbability(i), EPS);
            }
        }
    }

    @Test
    public void measurementRegisterMappingIsPreserved() {
        QasmProgram program = PARSER.parse("OPENQASM 2.0;\n"
            + "qreg q[3];\n"
            + "creg c[3];\n"
            + "measure q[2] -> c[0];\n"
            + "measure q[0] -> c[2];\n");

        assertEquals(2, program.getOperations().size());
        MeasurementOperator first = (MeasurementOperator) program.getOperations().get(0);
        assertEquals("c", first.getRegisterName());
        assertEquals(0, first.getRegisterOffset());
        assertEquals(2, first.getTargetQubits()[0].getIndex());

        MeasurementOperator second = (MeasurementOperator) program.getOperations().get(1);
        assertEquals("c", second.getRegisterName());
        assertEquals(2, second.getRegisterOffset());
        assertEquals(0, second.getTargetQubits()[0].getIndex());

        String text = EMITTER.emit(program);
        assertTrue(text.contains("measure q[2] -> c[0];"));
        assertTrue(text.contains("measure q[0] -> c[2];"));
    }

    @Test
    public void roundTripTextIsStableAndKeepsTheState() throws Exception {
        String source = "OPENQASM 2.0;\n"
            + "include \"qelib1.inc\";\n"
            + "qreg q[2];\n"
            + "creg c[2];\n"
            + "h q[0];\n"
            + "cx q[0],q[1];\n"
            + "rx(pi/4) q[0];\n"
            + "u3(pi/2,pi/3,pi/4) q[1];\n"
            + "measure q[0] -> c[0];\n"
            + "if(c==1) x q[1];\n";

        QasmProgram first = PARSER.parse(source);
        String text = EMITTER.emit(first);
        QasmProgram second = PARSER.parse(text);
        assertEquals(text, EMITTER.emit(second));

        assertTrue(text.startsWith("OPENQASM 2.0;\ninclude \"qelib1.inc\";\n"));
        assertTrue(text.contains("rx(0.785398) q[0];"));
        assertTrue(text.contains("u3(1.570796,1.047198,0.785398) q[1];"));
        assertTrue(text.contains("measure q[0] -> c[0];"));
        assertTrue(text.contains("if(c==1) x q[1];"));

        String simulationSource = "OPENQASM 2.0;\n"
            + "include \"qelib1.inc\";\n"
            + "qreg q[2];\n"
            + "h q[0];\n"
            + "cx q[0],q[1];\n"
            + "rx(pi/4) q[0];\n"
            + "u3(pi/2,pi/3,pi/4) q[1];\n";
        QasmProgram beforeRoundTrip = PARSER.parse(simulationSource);
        QasmProgram afterRoundTrip = PARSER.parse(EMITTER.emit(beforeRoundTrip));
        assertStateEquals(simulate(beforeRoundTrip), simulate(afterRoundTrip),
            ROUND_TRIP_EPS);
    }

    @Test
    public void emitterOptionsControlTheText() {
        QasmProgram program = PARSER.parse("OPENQASM 2.0;\n"
            + "qreg q[1];\n"
            + "rx(pi/4) q[0];\n");
        String text = EMITTER.emit(program, new QasmEmitter.Options()
            .withFractionalDigits(3)
            .withIncludeQelib(false)
            .withTrailingNewline(false));

        assertFalse(text.contains("include"));
        assertTrue(text.contains("rx(0.785) q[0];"));
        assertFalse(text.endsWith("\n"));
        assertThrows(IllegalArgumentException.class,
            () -> new QasmEmitter.Options().withFractionalDigits(16));
    }

    @Test
    public void parserRejectsInvalidSources() {
        assertParseMessage("qreg q[1];\nh q[0];\n", "Missing OPENQASM header");
        assertParseMessage("OPENQASM 2.0;\nqreg q[2];\ncreg q[2];\n",
            "Duplicate register name");
        assertParseMessage("OPENQASM 2.0;\nqreg q[1];\nx q[1];\n", "out of range");
        assertParseMessage("OPENQASM 2.0;\nqreg q[1];\nfrobnicate q[0];\n",
            "Unknown gate 'frobnicate'");
        assertParseMessage("OPENQASM 2.0;\nqreg q[1];\nx r[0];\n",
            "Unknown quantum register 'r'");
        assertParseMessage("OPENQASM 2.0;\nqreg q[2];\ncreg c[3];\nmeasure q -> c;\n",
            "Cannot measure quantum register");
        assertParseMessage("OPENQASM 2.0;\nqreg q[1];\nreset q[0];\n",
            "reset is not supported by the engine");
        assertParseMessage("OPENQASM 2.0;\ninclude \"other.inc\";\nqreg q[1];\n",
            "Unsupported include");
        assertParseMessage("OPENQASM 2.0;\nqubit[1] q;\n", "require OPENQASM 3.0");
        assertParseMessage("OPENQASM 2.0;\nqreg q[1];\ngate x a { h a; }\n",
            "built in");
        assertParseMessage("OPENQASM 2.0;\nqreg q[1];\ncreg c[1];\n"
            + "if(r==1) x q[0];\n", "Unknown classical register 'r'");
    }

    @Test
    public void parserReportsSourcePositions() {
        QasmException exception = assertThrows(QasmException.class,
            () -> PARSER.parse("OPENQASM 2.0;\nqreg q[1];\nx q[3];\n"));
        assertEquals(3, exception.getLine());
        assertTrue(exception.getMessage().contains("out of range"));
        assertTrue(exception.getMessage().contains("line 3"));
    }

    @Test
    public void recursiveGateDefinitionsAreRejected() {
        QasmException cycle = assertThrows(QasmException.class,
            () -> PARSER.parse("OPENQASM 2.0;\n"
                + "qreg q[1];\n"
                + "gate loop a { loop a; }\n"
                + "loop q[0];\n"));
        assertTrue(cycle.getMessage().contains("Cyclic gate definition"));

        StringBuilder source = new StringBuilder("OPENQASM 2.0;\nqreg q[1];\n"
            + "gate g0 a { x a; }\n");
        for (int i = 1; i <= 40; i++) {
            source.append("gate g").append(i).append(" a { g").append(i - 1)
                .append(" a; }\n");
        }
        source.append("g40 q[0];\n");
        QasmException depth = assertThrows(QasmException.class,
            () -> PARSER.parse(source.toString()));
        assertTrue(depth.getMessage().contains("maximum depth of 32"));
    }

    @Test
    public void emitterRejectsUnrepresentableOperations() {
        MeasurementOperator withoutRegister =
            MeasurementOperator.computationalBasis(new Qubit[]{new Qubit(0)}, 1);
        QasmProgram measurementProgram = new QasmProgram(1, Map.of(), Map.of("q", 1),
            List.<QuantumOperation>of(withoutRegister), "");
        QasmException measurementError = assertThrows(QasmException.class,
            () -> EMITTER.emit(measurementProgram));
        assertTrue(measurementError.getMessage().contains("classical register"));

        QuantumGate unknownGate = new QuantumGate.CustomGate("mystery",
            ComplexMatrix.pauliX(), new Qubit[]{new Qubit(0)}, new Qubit[0], 1.0);
        QasmProgram gateProgram = new QasmProgram(1, Map.of(), Map.of("q", 1),
            List.<QuantumOperation>of(unknownGate), "");
        QasmException gateError = assertThrows(QasmException.class,
            () -> EMITTER.emit(gateProgram));
        assertTrue(gateError.getMessage().contains("cannot be expressed"));

        MeasurementOperator outsideRegister =
            MeasurementOperator.computationalBasis(new Qubit[]{new Qubit(0)}, 1)
                .withClassicalTarget("c", 2);
        QasmProgram outsideProgram = new QasmProgram(1, Map.of("c", 2), Map.of("q", 1),
            List.<QuantumOperation>of(outsideRegister), "");
        assertThrows(QasmException.class, () -> EMITTER.emit(outsideProgram));

        ConditionalOperation missingRegister = new ConditionalOperation(
            SingleQubitGate.createPauliX(new Qubit(0)), "missing", 1, 1);
        QasmProgram conditionalProgram = new QasmProgram(1, Map.of(), Map.of("q", 1),
            List.<QuantumOperation>of(missingRegister), "");
        assertThrows(QasmException.class, () -> EMITTER.emit(conditionalProgram));

        QasmProgram noOperations = new QasmProgram(1, Map.of(), Map.of("q", 1),
            List.of(), null);
        String text = EMITTER.emit(noOperations);
        assertNotNull(text);
        assertEquals("OPENQASM 2.0;\ninclude \"qelib1.inc\";\nqreg q[1];\n", text);
    }

    private static void assertParseMessage(String source, String expectedFragment) {
        QasmException exception = assertThrows(QasmException.class,
            () -> PARSER.parse(source));
        assertTrue(exception.getMessage().contains(expectedFragment),
            "Expected message to contain '" + expectedFragment + "' but was: "
                + exception.getMessage());
    }

    private static void assertMatrixEquals(ComplexMatrix expected, ComplexMatrix actual,
            double tolerance) {
        assertEquals(expected.rows(), actual.rows());
        assertEquals(expected.cols(), actual.cols());
        for (int row = 0; row < expected.rows(); row++) {
            for (int column = 0; column < expected.cols(); column++) {
                assertEquals(expected.get(row, column).real(),
                    actual.get(row, column).real(), tolerance);
                assertEquals(expected.get(row, column).imag(),
                    actual.get(row, column).imag(), tolerance);
            }
        }
    }

    private static void assertStateEquals(StateVector expected, StateVector actual,
            double tolerance) {
        assertEquals(expected.getNumQubits(), actual.getNumQubits());
        int dimension = 1 << expected.getNumQubits();
        for (int i = 0; i < dimension; i++) {
            assertEquals(expected.getAmplitude(i).real(), actual.getAmplitude(i).real(),
                tolerance, "Real part of basis state " + i);
            assertEquals(expected.getAmplitude(i).imag(), actual.getAmplitude(i).imag(),
                tolerance, "Imaginary part of basis state " + i);
        }
    }

    private static StateVector simulate(QasmProgram program) throws Exception {
        return (StateVector) run(program).getFinalState();
    }

    private static StateVector simulateGates(int numQubits,
            List<QuantumOperation> operations) throws Exception {
        SimulationConfig config = new SimulationConfig();
        config.setNumQubits(numQubits);
        return (StateVector) run(config, operations).getFinalState();
    }

    private static SimulationResult run(QasmProgram program) throws Exception {
        SimulationConfig config = new SimulationConfig();
        config.setNumQubits(program.getNumQubits());
        for (Map.Entry<String, Integer> register : program.getRegisters().entrySet()) {
            config.addClassicalRegister(register.getKey(), register.getValue());
        }
        return run(config, program.getOperations());
    }

    private static SimulationResult run(SimulationConfig config,
            List<QuantumOperation> operations) throws Exception {
        QuantumSimulator simulator = new QuantumSimulator();
        simulator.initialize(config);
        Instant time = Instant.now().minusSeconds(5);
        int index = 0;
        for (QuantumOperation operation : operations) {
            simulator.scheduleEvent(new QuantumEvent.Builder("qasm_test_" + index)
                .withScheduledTime(time)
                .withOperation(operation)
                .build());
            index++;
            time = time.plusMillis(1);
        }
        return simulator.run();
    }
}
