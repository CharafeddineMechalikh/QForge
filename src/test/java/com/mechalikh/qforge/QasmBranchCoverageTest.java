package com.mechalikh.qforge;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.mechalikh.qforge.math.Complex;
import com.mechalikh.qforge.math.ComplexMatrix;
import com.mechalikh.qforge.measurement.MeasurementOperator;
import com.mechalikh.qforge.noise.DephasingNoise;
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
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Additional branch coverage for the OpenQASM lexer, parser, program and emitter.
 */
class QasmBranchCoverageTest {
    private static final QasmParser PARSER = new QasmParser();
    private static final QasmEmitter EMITTER = new QasmEmitter();

    private static QasmProgram parse(String source) {
        return PARSER.parse(source);
    }

    private static QasmException parseError(String source) {
        return assertThrows(QasmException.class, () -> PARSER.parse(source));
    }

    private static void assertMessage(String source, String fragment) {
        QasmException error = parseError(source);
        assertTrue(error.getMessage().contains(fragment),
            "Expected '" + fragment + "' but was: " + error.getMessage());
    }

    @Test
    void lexerHandlesWhitespaceCommentsAndNumbers() {
        QasmProgram program = parse("// leading comment\n"
            + "OPENQASM 2.0;\r\n"
            + "\t include \"qelib1.inc\"; // trailing comment without newline\n"
            + "qreg q_1[2];\n"
            + "creg c1[2];\n"
            + "rx(.5) q_1[0];\n"
            + "ry(1e2) q_1[0];\n"
            + "rz(1E-2) q_1[0];\n"
            + "u1(2.) q_1[1];\n");
        assertEquals(2, program.getNumQubits());
        assertEquals(4, program.getOperations().size());
        assertEquals(Math.sin(0.25), ((QuantumGate) program.getOperations().get(0)).getMatrix()
            .get(1, 0).abs(), 1e-12);
    }

    @Test
    void lexerRejectsUnexpectedCharactersAndBadLiterals() {
        assertMessage("OPENQASM 2.0;\n@\n", "Unexpected character '@'");
        assertMessage("OPENQASM 2.0;\n$\n", "Unexpected character '$'");
        assertMessage("OPENQASM 2.0;\nqreg q[1];\nrx(1e) q[0];\n",
            "Invalid numeric literal '1e'");
        assertMessage("OPENQASM 2.0;\nqreg q[1];\nrx(1e999) q[0];\n", "out of range");
    }

    @Test
    void lexerHandlesStringsEscapesAndUnterminatedInput() {
        assertMessage("OPENQASM 2.0;\ninclude \"qelib1.inc;\n", "Unterminated string");
        assertMessage("OPENQASM 2.0;\ninclude \"qelib1.inc\n", "Unterminated string");
        parse("OPENQASM 2.0;\ninclude \"qelib\\1.inc\";\nqreg q[1];\n");
        assertMessage("OPENQASM 2.0;\ninclude \"other.inc\";\n", "Unsupported include");
    }

    @Test
    void lexerCoversEndOfInputBoundaries() {
        assertThrows(QasmException.class, () -> parse("OPENQASM 2.0;\nqreg q"));
        assertThrows(QasmException.class, () -> parse("OPENQASM 2.0;\n."));
        assertThrows(QasmException.class, () -> parse("OPENQASM 2.0;\n.x"));
        assertThrows(QasmException.class, () -> parse("OPENQASM 2.0;\n-"));
        assertThrows(QasmException.class, () -> parse("OPENQASM 2.0;\n="));
        assertThrows(QasmException.class, () -> parse("OPENQASM 2.0;\n=;"));
        assertThrows(QasmException.class, () -> parse("OPENQASM 2.0;\nqreg q[2"));
        assertThrows(QasmException.class, () -> parse("OPENQASM 2.0;\n2.5"));
        assertThrows(QasmException.class, () -> parse("OPENQASM 2.0;\n1e2"));
        assertThrows(QasmException.class, () -> parse("OPENQASM 2.0;\nqreg q[1];\nrx(1e"));
        assertThrows(QasmException.class, () -> parse("OPENQASM 2.0;\ninclude \"abc"));
        assertThrows(QasmException.class, () -> parse("OPENQASM 2.0;\ninclude \"abc\\"));
        assertThrows(QasmException.class, () -> parse("OPENQASM 2.0;\n/"));

        parse("OPENQASM 2.0;\nqreg q[1]; // trailing comment at end of input");
        parse("OPENQASM 2.0;\nqreg _q[1];");
        parse("OPENQASM 2.0;\nqreg q[1];\nrx(1e+2) q[0];");
        parse("OPENQASM 2.0;\nqreg q[1];\nrx(1E+2) q[0];");
    }

    @Test
    void parserRejectsMalformedHeadersAndIncludes() {
        assertMessage("OPENQASM;\n", "a version number");
        assertMessage("OPENQASM foo;\n", "a version number");
        assertMessage("OPENQASM 2.5;\n", "Unsupported OpenQASM version");
        assertMessage("OPENQASM 2.0\n", "';'");
        assertMessage("OPENQASM 2.0;\ninclude q;\n", "quoted include path");
        assertMessage("OPENQASM 2.0;\nqreg q[1];\ninclude \"qelib1.inc\";\n",
            "only allowed before the first instruction");
        assertMessage("OPENQASM 2.0;\n1;\n", "a statement");
        assertMessage("OPENQASM 2.0;\nopaque o a;\n",
            "Opaque gate declarations are not supported");
        parse("OPENQASM 2.0;\ninclude \"qelib1.inc\";\ninclude \"stdgates.inc\";\nqreg q[1];\n");
    }

    @Test
    void parserRejectsMalformedRegisterDeclarations() {
        assertMessage("OPENQASM 2.0;\nqreg 3;\n", "quantum register name");
        assertMessage("OPENQASM 2.0;\nqreg q;\n", "'['");
        assertMessage("OPENQASM 2.0;\nqreg q[];\n", "must be an integer literal");
        assertMessage("OPENQASM 2.0;\nqreg q[1.5];\n", "must be an integer literal");
        assertMessage("OPENQASM 2.0;\nqreg q[99999999999999999999];\n",
            "Register size is out of range");
        assertMessage("OPENQASM 2.0;\nqreg q[0];\n", "must be positive");
        assertMessage("OPENQASM 2.0;\ncreg 4;\n", "classical register name");
        assertMessage("OPENQASM 3.0;\nqubit q;\n", "'['");
        assertMessage("OPENQASM 3.0;\nqubit[2];\n", "quantum register name");
        assertMessage("OPENQASM 3.0;\nbit[2];\n", "classical register name");
        assertMessage("OPENQASM 3.0;\nqubit[0] q;\n", "must be positive");
        assertMessage("OPENQASM 3.0;\nqubit[2] q\n", "';'");
        parse("OPENQASM 3.0;\nbit[2] c;\nqubit[2] q;\nmeasure q -> c;\n");
        assertMessage("OPENQASM 2.0;\ncreg q[1];\nqreg q[1];\n", "Duplicate register name");
        assertMessage("OPENQASM 2.0;\nqreg \"q\"[1];\n", "a quantum register name");
    }

    @Test
    void gateDefinitionErrorsAreReported() {
        assertMessage("OPENQASM 2.0;\ngate g a { x a; }\ngate g b { x b; }\n",
            "already defined");
        assertMessage("OPENQASM 2.0;\ngate g(a,a) q { x q; }\n",
            "Duplicate gate parameter");
        assertMessage("OPENQASM 2.0;\ngate g a,a { x a; }\n",
            "Duplicate formal qubit");
        assertMessage("OPENQASM 2.0;\ngate g { x; }\n", "formal qubit name");
        assertMessage("OPENQASM 2.0;\ngate g a { x a;\n", "Unterminated definition");
        assertMessage("OPENQASM 2.0;\ngate g a { 1; }\n", "gate application");
        assertMessage("OPENQASM 2.0;\ngate g a { x b; }\n", "Unknown formal qubit");
        assertMessage("OPENQASM 2.0;\ngate g a { x a[0]; }\n",
            "may not index a qubit");
        assertMessage("OPENQASM 2.0;\ngate g a { mystery a; }\nqreg q[1];\ng q[0];\n",
            "Unknown gate 'mystery'");
        parse("OPENQASM 2.0;\ngate g() a { x a; }\nqreg q[1];\ng q[0];\n");
    }

    @Test
    void gateDefinitionBodyRejectsNonGateStatements() {
        String[] statements = {"measure a;", "reset a;", "if(c==1) x a;", "qreg r[1];",
            "creg r[1];", "qubit[1] r;", "bit[1] r;", "gate h b { x b; }",
            "include \"qelib1.inc\";", "opaque o a;"};
        for (String statement : statements) {
            String source = "OPENQASM 2.0;\ngate g a { " + statement + " }\n";
            QasmException error = parseError(source);
            assertTrue(error.getMessage().contains("not allowed inside a gate definition")
                    || error.getMessage().contains("gate application"),
                "Unexpected message for " + statement + ": " + error.getMessage());
        }
    }

    @Test
    void gateDefinitionBarrierIsAcceptedAndDropped() {
        QasmProgram program = parse("OPENQASM 2.0;\n"
            + "gate g a,b { barrier a,b; x a; barrier b; }\n"
            + "qreg q[2];\n"
            + "g q[0],q[1];\n");
        assertEquals(1, program.getOperations().size());
    }

    @Test
    void measureErrorsAndRegisterTargetsAreCovered() {
        assertMessage("OPENQASM 2.0;\ncreg c[1];\nmeasure r[0] -> c[0];\n",
            "Unknown quantum register 'r'");
        assertMessage("OPENQASM 2.0;\nqreg q[1];\nmeasure q[0] -> q[0];\n",
            "is a quantum register");
        assertMessage("OPENQASM 2.0;\nqreg q[1];\nmeasure q[0] -> c[0];\n",
            "Unknown classical register 'c'");
        assertMessage("OPENQASM 2.0;\nqreg q[1];\ncreg c[1];\nmeasure q[1] -> c[0];\n",
            "Qubit index 1 is out of range");
        assertMessage("OPENQASM 2.0;\nqreg q[1];\ncreg c[1];\nmeasure q[0] -> c[1];\n",
            "Classical bit index 1 is out of range");
        assertMessage("OPENQASM 2.0;\nqreg q[1];\ncreg c[1];\nmeasure q[0] -> c;\n",
            "must map either one qubit to one bit");
        assertMessage("OPENQASM 2.0;\nqreg q[1];\ncreg c[1];\nmeasure q -> c[0];\n",
            "must map either one qubit to one bit");

        QasmProgram program = parse("OPENQASM 2.0;\n"
            + "qreg q[2];\nqreg r[2];\ncreg c[2];\n"
            + "measure q -> c;\n");
        assertEquals(2, program.getOperations().size());
        MeasurementOperator first = (MeasurementOperator) program.getOperations().get(0);
        assertEquals(0, first.getRegisterOffset());
    }

    @Test
    void barrierIsValidatedAndDiscarded() {
        QasmProgram program = parse("OPENQASM 2.0;\nqreg q[2];\n"
            + "barrier q;\nbarrier q[0], q[1];\n");
        assertEquals(0, program.getOperations().size());
        assertMessage("OPENQASM 2.0;\nbarrier r;\n", "Unknown quantum register 'r'");
        assertMessage("OPENQASM 2.0;\nqreg q[1];\nbarrier q[1];\n", "out of range");
        assertMessage("OPENQASM 2.0;\ncreg c[1];\nbarrier c;\n",
            "is a classical register");
    }

    @Test
    void conditionalErrorsAndBlocksAreCovered() {
        assertMessage("OPENQASM 2.0;\nqreg q[1];\ncreg c[1];\n"
            + "if(q==1) x q[0];\n", "is a quantum register");
        assertMessage("OPENQASM 2.0;\nqreg q[1];\ncreg c[1];\n"
            + "if(c==-1) x q[0];\n", "non-negative integer");
        assertMessage("OPENQASM 2.0;\nqreg q[1];\ncreg c[1];\n"
            + "if(c==1.5) x q[0];\n", "non-negative integer");
        assertMessage("OPENQASM 2.0;\nqreg q[1];\ncreg c[1];\n"
            + "if(c==1e19) x q[0];\n", "non-negative integer");
        assertMessage("OPENQASM 2.0;\nqreg q[1];\ncreg c[1];\n"
            + "if(c==1) if(c==0) x q[0];\n", "Nested conditions");
        assertMessage("OPENQASM 2.0;\nqreg q[1];\ncreg c[1];\n"
            + "if(c==1) barrier q;\n", "barrier cannot be guarded");
        assertMessage("OPENQASM 2.0;\nqreg q[1];\ncreg c[1];\n"
            + "if(c==1) reset q[0];\n", "reset is not supported");
        assertMessage("OPENQASM 2.0;\nqreg q[1];\ncreg c[1];\n"
            + "if(c==1) 1;\n", "gate application or a measurement");
        assertMessage("OPENQASM 2.0;\nqreg q[1];\ncreg c[1];\n"
            + "if(c==1) { x q[0];\n", "Unterminated conditional block");
        String[] declarations = {"qreg r[1];", "creg r[1];", "qubit[1] r;", "bit[1] r;",
            "gate g a { x a; }", "include \"qelib1.inc\";", "opaque o a;"};
        for (String declaration : declarations) {
            assertMessage("OPENQASM 2.0;\nqreg q[1];\ncreg c[1];\nif(c==1) " + declaration + "\n",
                "gate application or a measurement");
        }

        QasmProgram program = parse("OPENQASM 2.0;\nqreg q[1];\ncreg c[1];\n"
            + "if(c==(1+2)) { x q[0]; measure q[0] -> c[0]; }\n");
        assertEquals(2, program.getOperations().size());
        ConditionalOperation measurement =
            (ConditionalOperation) program.getOperations().get(1);
        assertTrue(measurement.getGuardedOperation() instanceof MeasurementOperator);

        QasmProgram wide = parse("OPENQASM 2.0;\nqreg q[1];\ncreg c[64];\n"
            + "if(c==1) x q[0];\n");
        assertEquals(-1L, ((ConditionalOperation) wide.getOperations().get(0)).getMask());
    }

    @Test
    void gateApplicationErrorsAndBroadcastingAreCovered() {
        assertMessage("OPENQASM 2.0;\nqreg q[2];\nx q[0],q[1];\n",
            "expects 1 qubit argument but 2");
        assertMessage("OPENQASM 2.0;\nqreg q[1];\ngate g a,b { x a; }\ng q[0];\n",
            "expects 2 qubit arguments but 1");
        assertMessage("OPENQASM 2.0;\nqreg q[1];\nx r[0];\n",
            "Unknown quantum register 'r'");
        assertMessage("OPENQASM 2.0;\nqreg q[1];\nx q[2];\n", "out of range");
        assertMessage("OPENQASM 2.0;\nqreg q[2];\nqreg r[3];\ncx q,r;\n",
            "must have the same width");
        assertMessage("OPENQASM 2.0;\nqreg q[1];\nx q[1.5];\n",
            "must be an integer literal");
        assertMessage("OPENQASM 2.0;\nqreg q[1];\nx q[foo];\n",
            "must be an integer literal");
        assertMessage("OPENQASM 2.0;\nqreg q[1];\nrx(,) q[0];\n",
            "a number, 'pi', a parameter name");
        assertMessage("OPENQASM 2.0;\nqreg q[1];\nx q[99999999999999999999];\n",
            "Qubit index is out of range");
        assertMessage("OPENQASM 2.0;\nqreg q[1];\nx(\n", "a number, 'pi', a parameter name");

        QasmProgram broadcast = parse("OPENQASM 2.0;\nqreg q[3];\nx q;\n");
        assertEquals(3, broadcast.getOperations().size());

        QasmProgram paired = parse("OPENQASM 2.0;\nqreg q[2];\nqreg r[2];\ncx q,r;\n");
        assertEquals(2, paired.getOperations().size());

        QasmProgram emptyParameters = parse("OPENQASM 2.0;\nqreg q[1];\nx() q[0];\n");
        assertEquals(1, emptyParameters.getOperations().size());
    }

    @Test
    void builtInParameterValidationIsCovered() {
        assertMessage("OPENQASM 2.0;\nqreg q[1];\nx(1) q[0];\n", "expects 0 parameters");
        assertMessage("OPENQASM 2.0;\nqreg q[1];\nu1() q[0];\n", "expects 1 parameter");
        assertMessage("OPENQASM 2.0;\nqreg q[1];\nu2(1) q[0];\n", "expects 2 parameters");
        assertMessage("OPENQASM 2.0;\nqreg q[1];\nu3(1,2) q[0];\n", "expects 3 parameters");
        assertMessage("OPENQASM 2.0;\nqreg q[2];\ncp() q[0],q[1];\n", "expects 1 parameter");
        assertMessage("OPENQASM 2.0;\nqreg q[2];\ncx(1) q[0],q[1];\n", "expects 0 parameters");

        assertMessage("OPENQASM 2.0;\nqreg q[1];\ngate g(a) t { x t; }\ng() q[0];\n",
            "expects 1 parameter but 0");
        assertMessage("OPENQASM 2.0;\nqreg q[1];\ngate g(a,b) t { x t; }\ng(1) q[0];\n",
            "expects 2 parameters but 1");
    }

    @Test
    void builtInGatesAllExpand() {
        String source = "OPENQASM 2.0;\nqreg q[3];\n"
            + "y q[0];\nz q[0];\ns q[0];\nsdg q[0];\nt q[0];\ntdg q[0];\nid q[0];\n"
            + "rx(1) q[0];\nry(1) q[0];\nrz(1) q[0];\nu1(1) q[0];\nu2(1,2) q[0];\n"
            + "u3(1,2,3) q[0];\ncnot q[0],q[1];\ncz q[0],q[1];\nswap q[0],q[1];\n"
            + "ccx q[0],q[1],q[2];\ncp(1) q[0],q[1];\ncphase(1) q[0],q[1];\n";
        QasmProgram program = parse(source);
        assertEquals(19, program.getOperations().size());
        for (QuantumOperation operation : program.getOperations()) {
            assertTrue(operation instanceof QuantumGate);
        }
    }

    @Test
    void userGateParameterExpressionsAreEvaluated() {
        QasmProgram program = parse("OPENQASM 2.0;\n"
            + "gate rot(theta,phi) a { rx(-theta) a; ry(+phi) a; rz((theta+phi)/2) a; }\n"
            + "qreg q[1];\n"
            + "rot(pi/2, pi/4) q[0];\n");
        assertEquals(3, program.getOperations().size());

        QasmProgram variable = parse("OPENQASM 2.0;\n"
            + "gate g(theta) a { rx(theta*2 - 1) a; }\n"
            + "qreg q[1];\ng(pi) q[0];\n");
        assertEquals(1, variable.getOperations().size());

        assertMessage("OPENQASM 2.0;\nqreg q[1];\nrx(1/0) q[0];\n", "Division by zero");
        assertMessage("OPENQASM 2.0;\nqreg q[1];\nrx(1e308*10) q[0];\n", "non-finite");
        assertMessage("OPENQASM 2.0;\ngate g(a) t { rx(b) t; }\nqreg q[1];\ng(1) q[0];\n",
            "Unknown parameter 'b'");
    }

    @Test
    void customU1U2U3MatricesHaveExpectedPhases() {
        QasmProgram program = parse("OPENQASM 2.0;\nqreg q[1];\n"
            + "u1(pi/4) q[0];\nu2(pi/2,pi/4) q[0];\nu3(pi/2,pi/3,pi/4) q[0];\n");
        ComplexMatrix u1 = ((QuantumGate) program.getOperations().get(0)).getMatrix();
        ComplexMatrix u2 = ((QuantumGate) program.getOperations().get(1)).getMatrix();
        ComplexMatrix u3 = ((QuantumGate) program.getOperations().get(2)).getMatrix();
        assertEquals(1.0, u1.get(0, 0).real(), 1e-12);
        assertEquals(Math.cos(Math.PI / 4), u1.get(1, 1).real(), 1e-12);
        assertEquals(Math.sin(Math.PI / 4), u1.get(1, 1).imag(), 1e-12);
        assertEquals(1.0 / Math.sqrt(2.0), u2.get(0, 0).real(), 1e-12);
        assertEquals(-0.5, u3.get(0, 1).real(), 1e-12);
        assertEquals(-0.5, u3.get(0, 1).imag(), 1e-12);
    }

    @Test
    void parseFileReadsUtf8AndReportsFailures() throws IOException {
        Path file = Files.createTempFile("qasm-branch", ".qasm");
        try {
            Files.writeString(file, "OPENQASM 2.0;\nqreg q[1];\nh q[0];\n",
                StandardCharsets.UTF_8);
            QasmProgram program = PARSER.parseFile(file.toString());
            assertEquals(1, program.getNumQubits());

            assertThrows(NullPointerException.class, () -> PARSER.parseFile(null));
            QasmException missing = assertThrows(QasmException.class,
                () -> PARSER.parseFile(file.resolveSibling("missing-qasm-file.qasm").toString()));
            assertTrue(missing.getMessage().contains("Failed to read QASM file"));
            assertTrue(missing.getCause() instanceof IOException);

            QasmException invalid = assertThrows(QasmException.class,
                () -> PARSER.parseFile("bad\0path"));
            assertTrue(invalid.getMessage().contains("Invalid QASM file path"));
            assertTrue(invalid.getCause() instanceof RuntimeException);

            assertThrows(NullPointerException.class, () -> PARSER.parse(null));
        } finally {
            Files.deleteIfExists(file);
        }
    }

    @Test
    void qasmExceptionDecoratesPositions() {
        QasmException bare = new QasmException("plain");
        assertEquals(-1, bare.getLine());
        assertEquals(-1, bare.getColumn());
        assertEquals("plain", bare.getMessage());

        QasmException caused = new QasmException("cause", new IllegalStateException("root"));
        assertEquals(-1, caused.getLine());
        assertNotNull(caused.getCause());

        QasmException positioned = new QasmException("positioned", 2, 7,
            new IllegalStateException("root"));
        assertEquals(2, positioned.getLine());
        assertEquals(7, positioned.getColumn());
        assertTrue(positioned.getMessage().contains("(line 2, column 7)"));
    }

    @Test
    void qasmProgramValidatesItsInputs() {
        Map<String, Integer> none = Map.of();
        assertThrows(IllegalArgumentException.class,
            () -> new QasmProgram(-1, none, none, List.of(), null));
        assertThrows(NullPointerException.class,
            () -> new QasmProgram(0, null, none, List.of(), null));
        assertThrows(NullPointerException.class,
            () -> new QasmProgram(0, none, null, List.of(), null));
        assertThrows(NullPointerException.class,
            () -> new QasmProgram(0, none, none, null, null));

        Map<String, Integer> nullName = new LinkedHashMap<>();
        nullName.put(null, 1);
        assertThrows(IllegalArgumentException.class,
            () -> new QasmProgram(0, nullName, none, List.of(), null));
        assertThrows(IllegalArgumentException.class,
            () -> new QasmProgram(0, Map.of("", 1), none, List.of(), null));
        Map<String, Integer> nullWidth = new LinkedHashMap<>();
        nullWidth.put("c", null);
        assertThrows(IllegalArgumentException.class,
            () -> new QasmProgram(0, nullWidth, none, List.of(), null));
        assertThrows(IllegalArgumentException.class,
            () -> new QasmProgram(0, Map.of("c", 0), none, List.of(), null));
        assertThrows(IllegalArgumentException.class,
            () -> new QasmProgram(0, none, Map.of("q", 0), List.of(), null));
        assertThrows(IllegalArgumentException.class,
            () -> new QasmProgram(2, none, Map.of("q", 1), List.of(), null));

        QasmProgram program = new QasmProgram(1, Map.of("c", 1), Map.of("q", 1),
            List.of(), null);
        assertEquals("", program.getSource());
        assertEquals("q0", program.getQubit(0).getLabel());
        assertThrows(IndexOutOfBoundsException.class, () -> program.getQubit(1));
        assertEquals("QasmProgram[qubits=1, quantumRegisters=1, classicalRegisters=1, "
            + "operations=0]", program.toString());
        assertEquals(0, program.getQuantumRegisterOffset("q"));
        assertThrows(IllegalArgumentException.class,
            () -> program.getQuantumRegisterOffset("r"));

        QasmProgram parsed = parse("OPENQASM 2.0;\nqreg q[1];\n");
        assertEquals("OPENQASM 2.0;\nqreg q[1];\n", parsed.getSource());
    }

    @Test
    void emitterOptionsExposeAndValidateSettings() {
        QasmEmitter.Options defaults = new QasmEmitter.Options();
        assertTrue(defaults.isIncludeQelib());
        assertEquals(QasmEmitter.Options.DEFAULT_FRACTIONAL_DIGITS,
            defaults.getFractionalDigits());
        assertTrue(defaults.isTrailingNewline());
        assertFalse(defaults.withIncludeQelib(false).isIncludeQelib());
        assertFalse(defaults.withTrailingNewline(false).isTrailingNewline());
        assertEquals(0, defaults.withFractionalDigits(0).getFractionalDigits());
        assertEquals(15, defaults.withFractionalDigits(15).getFractionalDigits());
        assertThrows(IllegalArgumentException.class,
            () -> defaults.withFractionalDigits(-1));
        assertThrows(IllegalArgumentException.class,
            () -> defaults.withFractionalDigits(16));

        assertThrows(NullPointerException.class, () -> EMITTER.emit(null));
        assertThrows(NullPointerException.class,
            () -> EMITTER.emit(new QasmProgram(1, Map.of(), Map.of("q", 1), List.of(), null),
                null));
    }

    @Test
    void emitterFormatsAnglesAndTrailingNewline() {
        QasmProgram program = parse("OPENQASM 2.0;\nqreg q[1];\n"
            + "rx(-0.0) q[0];\nrx(2.0) q[0];\nrx(-1.5) q[0];\n");

        String zeroDigits = EMITTER.emit(program,
            new QasmEmitter.Options().withFractionalDigits(0).withTrailingNewline(false));
        assertFalse(zeroDigits.endsWith("\n"));
        assertTrue(zeroDigits.contains("rx(0) q[0];"));
        assertTrue(zeroDigits.contains("rx(2) q[0];"));
        assertTrue(zeroDigits.contains("rx(-2) q[0];"));

        String trimmed = EMITTER.emit(program, new QasmEmitter.Options()
            .withFractionalDigits(6).withTrailingNewline(false));
        assertTrue(trimmed.contains("rx(0) q[0];"));
        assertTrue(trimmed.contains("rx(2) q[0];"));
        assertTrue(trimmed.contains("rx(-1.5) q[0];"));
    }

    @Test
    void emitterWritesEverySupportedGateName() {
        QasmProgram program = parse("OPENQASM 2.0;\nqreg q[3];\n"
            + "x q[0];\ny q[0];\nz q[0];\nh q[0];\ns q[0];\nsdg q[0];\nt q[0];\n"
            + "tdg q[0];\nid q[0];\nrx(1) q[0];\nry(1) q[0];\nrz(1) q[0];\n"
            + "u1(1) q[0];\nu2(1,2) q[0];\nu3(1,2,3) q[0];\ncx q[0],q[1];\n"
            + "cz q[0],q[1];\nswap q[0],q[1];\nccx q[0],q[1],q[2];\ncp(1) q[0],q[1];\n");
        String text = EMITTER.emit(program);
        for (String name : new String[]{"x ", "y ", "z ", "h ", "s ", "sdg ", "t ", "tdg ",
            "id ", "rx(1) ", "ry(1) ", "rz(1) ", "u1(1) ", "u2(1,2) ", "u3(1,2,3) ",
            "cx ", "cz ", "swap ", "ccx ", "cp(1) "}) {
            assertTrue(text.contains(name), "Missing emitted gate: " + name);
        }
    }

    @Test
    void emitterMapsAlternateGateNames() {
        Qubit q0 = new Qubit(0);
        Qubit q1 = new Qubit(1);
        Qubit q2 = new Qubit(2);
        List<QuantumOperation> gates = Arrays.asList(
            new SingleQubitGate("X", ComplexMatrix.pauliX(), q0),
            new SingleQubitGate("P(0.5)", identity2(), q0),
            new MultiQubitGate("CNOT", cnotMatrix(), new Qubit[]{q1}, new Qubit[]{q0}, 1.0),
            new MultiQubitGate("TOFFOLI", toffoliMatrix(),
                new Qubit[]{q2}, new Qubit[]{q0, q1}, 1.0),
            new MultiQubitGate("CPHASE(0.5)", cphaseMatrix(0.5),
                new Qubit[]{q1}, new Qubit[]{q0}, 1.0),
            new MultiQubitGate("CZ", czMatrix(), new Qubit[]{q0, q1}));
        QasmProgram program = new QasmProgram(3, Map.of(), Map.of("q", 3), gates, null);
        String text = EMITTER.emit(program);
        assertTrue(text.contains("x q[0];"));
        assertTrue(text.contains("u1(0.5) q[0];"));
        assertTrue(text.contains("cx q[0], q[1];"));
        assertTrue(text.contains("ccx q[0], q[1], q[2];"));
        assertTrue(text.contains("cp(0.5) q[0], q[1];"));
        assertTrue(text.contains("cz q[0], q[1];"));
    }

    @Test
    void emitterRejectsInvalidGateSignatures() {
        Qubit q0 = new Qubit(0);
        assertEmitError(new SingleQubitGate("rx", identity2(), q0),
            "must carry 1 parameter");
        assertEmitError(new SingleQubitGate("x(0.5)", identity2(), q0),
            "with parameters");
        assertEmitError(new SingleQubitGate("rx(0.5", identity2(), q0),
            "complete parameter list");
        assertEmitError(new SingleQubitGate("rx(abc)", identity2(), q0),
            "invalid parameter 'abc'");
        assertEmitError(new SingleQubitGate("rx(NaN)", identity2(), q0),
            "invalid parameter 'NaN'");
        assertEmitError(new SingleQubitGate("rx()", identity2(), q0),
            "must carry 1 parameter");
        assertEmitError(new SingleQubitGate("rx(0.5,0.5)", identity2(), q0),
            "must carry 1 parameter");
        assertEmitError(new SingleQubitGate("u2(0.5)", identity2(), q0),
            "must carry 2 parameters");
        assertEmitError(new SingleQubitGate("p", identity2(), q0),
            "must carry 1 parameter");
        assertEmitError(new SingleQubitGate("cx", identity2(), q0), "expects 2");
        assertEmitError(new SingleQubitGate("mystery", identity2(), q0),
            "cannot be expressed");
    }

    @Test
    void emitterRejectsInvalidMeasurements() {
        Qubit q0 = new Qubit(0);
        assertEmitError(MeasurementOperator.computationalBasis(new Qubit[]{q0}, 1)
            .withClassicalTarget("missing", 0), "undeclared classical register");
        assertEmitError(MeasurementOperator.computationalBasis(new Qubit[]{q0}, 1)
            .withClassicalTarget("c", 1), "outside classical register");

        MeasurementOperator twoQubits = MeasurementOperator.computationalBasis(
            new Qubit[]{q0, new Qubit(1)}, 1).withClassicalTarget("c", 0);
        QasmException error = assertThrows(QasmException.class,
            () -> EMITTER.emit(new QasmProgram(2, Map.of("c", 2), Map.of("q", 2),
                List.<QuantumOperation>of(twoQubits), null)));
        assertTrue(error.getMessage().contains("2 qubits at once"));
    }

    @Test
    void emitterRejectsInvalidConditionsAndOperations() {
        Qubit q0 = new Qubit(0);
        Map<String, Integer> quantum = Map.of("q", 1);
        Map<String, Integer> classical = Map.of("c", 1);

        ConditionalOperation nested = new ConditionalOperation(
            new ConditionalOperation(SingleQubitGate.createPauliX(q0), "c", 1, 1),
            "c", 1, 1);
        assertEmitError(nested, "Nested conditional operations");

        assertEmitError(new DephasingNoise(0.1, q0), "cannot be expressed");
    }

    private static void assertEmitError(QuantumOperation operation, String fragment) {
        Qubit qubit = new Qubit(0);
        Map<String, Integer> quantum;
        Map<String, Integer> classical;
        if (operation instanceof ConditionalOperation) {
            quantum = Map.of("q", 1);
            classical = Map.of("c", 1);
        } else if (operation instanceof MeasurementOperator) {
            quantum = Map.of("q", 1);
            classical = Map.of("c", 1);
        } else {
            quantum = Map.of("q", 1);
            classical = Map.of();
        }
        QasmProgram program = new QasmProgram(1, classical, quantum,
            List.<QuantumOperation>of(operation), null);
        QasmException error = assertThrows(QasmException.class, () -> EMITTER.emit(program));
        assertTrue(error.getMessage().contains(fragment),
            "Expected '" + fragment + "' but was: " + error.getMessage());
    }

    @Test
    void emitterResolvesQubitsInLaterRegisters() {
        QasmProgram program = parse("OPENQASM 2.0;\nqreg a[1];\nqreg b[1];\nh b[0];\n");
        String text = EMITTER.emit(program);
        assertTrue(text.contains("h b[0];"));
    }

    @Test
    void emitterRejectsQubitOutsideDeclaredRegisters() {
        Qubit outside = new Qubit(9);
        QasmProgram program = new QasmProgram(1, Map.of(), Map.of("q", 1),
            List.<QuantumOperation>of(new SingleQubitGate("x", ComplexMatrix.pauliX(), outside)),
            null);
        QasmException error = assertThrows(QasmException.class, () -> EMITTER.emit(program));
        assertTrue(error.getMessage().contains("does not belong to any declared quantum register"));
    }

    @Test
    void executionOfParsedProgramMatchesExpectedState() throws Exception {
        QasmProgram program = parse("OPENQASM 3.0;\n"
            + "include \"stdgates.inc\";\n"
            + "qubit[2] q;\nbit[2] c;\n"
            + "h q[0];\ncx q[0],q[1];\nmeasure q -> c;\n");
        com.mechalikh.qforge.config.SimulationConfig config =
            new com.mechalikh.qforge.config.SimulationConfig();
        config.setNumQubits(2);
        config.addClassicalRegister("c", 2);
        com.mechalikh.qforge.core.QuantumSimulator simulator =
            new com.mechalikh.qforge.core.QuantumSimulator();
        simulator.initialize(config);
        java.time.Instant time = java.time.Instant.now();
        int index = 0;
        for (QuantumOperation operation : program.getOperations()) {
            simulator.scheduleEvent(new com.mechalikh.qforge.events.QuantumEvent.Builder("e" + index)
                .withScheduledTime(time).withOperation(operation).build());
            time = time.plusNanos(1);
            index++;
        }
        com.mechalikh.qforge.core.SimulationResult result = simulator.run();
        StateVector state = (StateVector) result.getFinalState();
        long register = result.getRegisterValues().get("c");
        assertTrue(register == 0L || register == 3L);
        assertEquals(1.0, state.getProbability((int) register), 1e-12);
        assertEquals(2, state.getNumQubits());
        assertEquals(4, program.getOperations().size());
    }

    private static ComplexMatrix identity2() {
        return ComplexMatrix.identity(2);
    }

    private static ComplexMatrix cnotMatrix() {
        ComplexMatrix matrix = ComplexMatrix.identity(4);
        matrix.set(2, 2, Complex.ZERO);
        matrix.set(3, 3, Complex.ZERO);
        matrix.set(2, 3, Complex.ONE);
        matrix.set(3, 2, Complex.ONE);
        return matrix;
    }

    private static ComplexMatrix toffoliMatrix() {
        ComplexMatrix matrix = ComplexMatrix.identity(8);
        matrix.set(6, 6, Complex.ZERO);
        matrix.set(7, 7, Complex.ZERO);
        matrix.set(6, 7, Complex.ONE);
        matrix.set(7, 6, Complex.ONE);
        return matrix;
    }

    private static ComplexMatrix czMatrix() {
        ComplexMatrix matrix = ComplexMatrix.identity(4);
        matrix.set(3, 3, new Complex(-1, 0));
        return matrix;
    }

    private static ComplexMatrix cphaseMatrix(double angle) {
        ComplexMatrix matrix = ComplexMatrix.identity(4);
        matrix.set(3, 3, Complex.fromPolar(1.0, angle));
        return matrix;
    }
}
