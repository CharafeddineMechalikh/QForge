package com.mechalikh.qforge.qasm;

import com.mechalikh.qforge.math.Complex;
import com.mechalikh.qforge.math.ComplexMatrix;
import com.mechalikh.qforge.measurement.MeasurementOperator;
import com.mechalikh.qforge.operations.ConditionalOperation;
import com.mechalikh.qforge.operations.MultiQubitGate;
import com.mechalikh.qforge.operations.QuantumGate;
import com.mechalikh.qforge.operations.QuantumOperation;
import com.mechalikh.qforge.operations.SingleQubitGate;
import com.mechalikh.qforge.state.Qubit;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;

/**
 * Parser for OpenQASM 2.0 and a documented subset of OpenQASM 3.0.
 *
 * <p>The following constructs are supported:</p>
 * <ul>
 *   <li>The header {@code OPENQASM 2.0;} or {@code OPENQASM 3.0;}, followed by an optional
 *       {@code include "qelib1.inc";} or {@code include "stdgates.inc";}.</li>
 *   <li>The declarations {@code qreg name[n];} and {@code creg name[n];}, and for version 3
 *       also {@code qubit[n] name;} and {@code bit[n] name;}.</li>
 *   <li>The built-in gates x, y, z, h, s, sdg, t, tdg, id, rx, ry, rz, u1, u2, u3, cx, cnot,
 *       cz, swap, ccx, cp, and cphase.</li>
 *   <li>Gate definitions written as {@code gate name(params) a,b { body }}. A definition is
 *       expanded inline at every use, recursively, with a recursion depth limit of 32 and
 *       cycle detection. The formal qubit names are mapped, in order, to the actual qubit
 *       arguments.</li>
 *   <li>Measurements of a single qubit, as in {@code measure q[0] -> c[0];}, and of a whole
 *       register, as in {@code measure q -> c;}.</li>
 *   <li>Classical conditions written as {@code if(c==N) statement}, or with a braced statement
 *       list in version 3. The comparison covers the whole classical register.</li>
 *   <li>{@code barrier} statements, which are validated and then discarded because the engine
 *       has no barrier operation.</li>
 *   <li>Line comments introduced by {@code //}.</li>
 * </ul>
 *
 * <p>A gate argument may name one bit, as in {@code q[2]}, or a whole register, as in
 * {@code q}. A gate applied to whole registers is expanded element by element, and every whole
 * register used in one application must have the same width. Parameters are decimal literals,
 * {@code pi}, the parameter names of the enclosing gate definition, and expressions built with
 * {@code +}, {@code -}, {@code *}, {@code /}, and parentheses.</p>
 *
 * <p>The parameters of u1, u2, and u3 are converted to their exact matrices and stored in
 * {@link QuantumGate.CustomGate} instances. In particular u1 is the matrix
 * {@code [[1, 0], [0, e^(i theta)]]}, so no global phase is dropped.</p>
 *
 * <p>The subset does not include {@code reset}, opaque gate declarations, custom instructions,
 * loops, or nested conditions. These constructs are rejected with a {@link QasmException}
 * instead of being ignored. Qubit and register sizes must be integer literals.</p>
 */
public final class QasmParser {
    private static final int MAX_EXPANSION_DEPTH = 32;
    private static final Set<String> BUILTIN_GATES;

    static {
        Set<String> names = new TreeSet<>();
        Collections.addAll(names, "x", "y", "z", "h", "s", "sdg", "t", "tdg", "id", "rx", "ry",
            "rz", "u1", "u2", "u3", "cx", "cnot", "cz", "swap", "ccx", "cp", "cphase");
        BUILTIN_GATES = Collections.unmodifiableSet(names);
    }

    /**
     * Parses OpenQASM source text.
     *
     * @param source the source text
     * @return the parsed program
     * @throws NullPointerException if {@code source} is {@code null}
     * @throws QasmException if the source is not valid in the supported subset
     */
    public QasmProgram parse(String source) {
        Objects.requireNonNull(source, "source");
        return new Parser(source).parseProgram();
    }

    /**
     * Reads a file as UTF-8 and parses its contents.
     *
     * @param path the file system path of the source file
     * @return the parsed program
     * @throws NullPointerException if {@code path} is {@code null}
     * @throws QasmException if the file cannot be read or its contents are not valid in the
     *         supported subset
     */
    public QasmProgram parseFile(String path) {
        Objects.requireNonNull(path, "path");
        String source;
        try {
            source = Files.readString(Path.of(path), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new QasmException("Failed to read QASM file '" + path + "': " + e.getMessage(),
                e);
        } catch (RuntimeException e) {
            throw new QasmException("Invalid QASM file path '" + path + "': " + e.getMessage(),
                e);
        }
        return parse(source);
    }

    private enum TokenType {
        IDENTIFIER, NUMBER, STRING, SYMBOL, EOF
    }

    private static final class Token {
        final TokenType type;
        final String text;
        final double numberValue;
        final int line;
        final int column;

        Token(TokenType type, String text, double numberValue, int line, int column) {
            this.type = type;
            this.text = text;
            this.numberValue = numberValue;
            this.line = line;
            this.column = column;
        }
    }

    private static final class Lexer {
        private final String source;
        private final int length;
        private int index;
        private int line = 1;
        private int column = 1;
        private Token lookahead;

        Lexer(String source) {
            this.source = source;
            this.length = source.length();
        }

        Token peek() {
            if (lookahead == null) {
                lookahead = scan();
            }
            return lookahead;
        }

        Token next() {
            Token token = peek();
            lookahead = null;
            return token;
        }

        private Token scan() {
            skipTrivia();
            if (index >= length) {
                return new Token(TokenType.EOF, "", 0, line, column);
            }
            int startLine = line;
            int startColumn = column;
            char current = source.charAt(index);

            if (isIdentifierStart(current)) {
                StringBuilder text = new StringBuilder();
                while (index < length && isIdentifierPart(source.charAt(index))) {
                    text.append(source.charAt(index));
                    advance();
                }
                return new Token(TokenType.IDENTIFIER, text.toString(), 0, startLine,
                    startColumn);
            }

            if (Character.isDigit(current)
                    || (current == '.' && index + 1 < length
                        && Character.isDigit(source.charAt(index + 1)))) {
                return scanNumber(startLine, startColumn);
            }

            if (current == '"') {
                return scanString(startLine, startColumn);
            }

            if (current == '-' && index + 1 < length && source.charAt(index + 1) == '>') {
                advance();
                advance();
                return new Token(TokenType.SYMBOL, "->", 0, startLine, startColumn);
            }

            if (current == '=' && index + 1 < length && source.charAt(index + 1) == '=') {
                advance();
                advance();
                return new Token(TokenType.SYMBOL, "==", 0, startLine, startColumn);
            }

            if ("{}()[];,+-*/=".indexOf(current) >= 0) {
                advance();
                return new Token(TokenType.SYMBOL, String.valueOf(current), 0, startLine,
                    startColumn);
            }

            throw new QasmException("Unexpected character '" + current + "'", startLine,
                startColumn);
        }

        private Token scanNumber(int startLine, int startColumn) {
            StringBuilder text = new StringBuilder();
            while (index < length && Character.isDigit(source.charAt(index))) {
                text.append(source.charAt(index));
                advance();
            }
            if (index < length && source.charAt(index) == '.') {
                text.append('.');
                advance();
                while (index < length && Character.isDigit(source.charAt(index))) {
                    text.append(source.charAt(index));
                    advance();
                }
            }
            if (index < length
                    && (source.charAt(index) == 'e' || source.charAt(index) == 'E')) {
                text.append(source.charAt(index));
                advance();
                if (index < length
                        && (source.charAt(index) == '+' || source.charAt(index) == '-')) {
                    text.append(source.charAt(index));
                    advance();
                }
                while (index < length && Character.isDigit(source.charAt(index))) {
                    text.append(source.charAt(index));
                    advance();
                }
            }

            String literal = text.toString();
            double value;
            try {
                value = Double.parseDouble(literal);
            } catch (NumberFormatException e) {
                throw new QasmException("Invalid numeric literal '" + literal + "'", startLine,
                    startColumn);
            }
            if (!Double.isFinite(value)) {
                throw new QasmException("Numeric literal '" + literal + "' is out of range",
                    startLine, startColumn);
            }
            return new Token(TokenType.NUMBER, literal, value, startLine, startColumn);
        }

        private Token scanString(int startLine, int startColumn) {
            advance();
            StringBuilder text = new StringBuilder();
            while (index < length && source.charAt(index) != '"') {
                char current = source.charAt(index);
                if (current == '\n') {
                    break;
                }
                if (current == '\\' && index + 1 < length) {
                    text.append(source.charAt(index + 1));
                    advance();
                    advance();
                    continue;
                }
                text.append(current);
                advance();
            }
            if (index >= length || source.charAt(index) != '"') {
                throw new QasmException("Unterminated string literal", startLine, startColumn);
            }
            advance();
            return new Token(TokenType.STRING, text.toString(), 0, startLine, startColumn);
        }

        private void skipTrivia() {
            while (index < length) {
                char current = source.charAt(index);
                if (Character.isWhitespace(current)) {
                    advance();
                    continue;
                }
                if (current == '/' && index + 1 < length
                        && source.charAt(index + 1) == '/') {
                    while (index < length && source.charAt(index) != '\n') {
                        advance();
                    }
                    continue;
                }
                break;
            }
        }

        private void advance() {
            if (source.charAt(index) == '\n') {
                line++;
                column = 1;
            } else {
                column++;
            }
            index++;
        }

        private static boolean isIdentifierStart(char value) {
            return Character.isLetter(value) || value == '_';
        }

        private static boolean isIdentifierPart(char value) {
            return Character.isLetterOrDigit(value) || value == '_';
        }
    }

    private interface Expr {
        double evaluate(Map<String, Double> scope);

        int line();

        int column();
    }

    private static final class NumberExpr implements Expr {
        private final double value;
        private final int line;
        private final int column;

        NumberExpr(double value, int line, int column) {
            this.value = value;
            this.line = line;
            this.column = column;
        }

        @Override
        public double evaluate(Map<String, Double> scope) {
            return value;
        }

        @Override
        public int line() {
            return line;
        }

        @Override
        public int column() {
            return column;
        }
    }

    private static final class PiExpr implements Expr {
        private final int line;
        private final int column;

        PiExpr(int line, int column) {
            this.line = line;
            this.column = column;
        }

        @Override
        public double evaluate(Map<String, Double> scope) {
            return Math.PI;
        }

        @Override
        public int line() {
            return line;
        }

        @Override
        public int column() {
            return column;
        }
    }

    private static final class VariableExpr implements Expr {
        private final String name;
        private final int line;
        private final int column;

        VariableExpr(String name, int line, int column) {
            this.name = name;
            this.line = line;
            this.column = column;
        }

        @Override
        public double evaluate(Map<String, Double> scope) {
            if (!scope.containsKey(name)) {
                throw new QasmException("Unknown parameter '" + name + "'", line, column);
            }
            return scope.get(name);
        }

        @Override
        public int line() {
            return line;
        }

        @Override
        public int column() {
            return column;
        }
    }

    private static final class UnaryExpr implements Expr {
        private final char operator;
        private final Expr operand;
        private final int line;
        private final int column;

        UnaryExpr(char operator, Expr operand, int line, int column) {
            this.operator = operator;
            this.operand = operand;
            this.line = line;
            this.column = column;
        }

        @Override
        public double evaluate(Map<String, Double> scope) {
            double value = operand.evaluate(scope);
            return operator == '-' ? -value : value;
        }

        @Override
        public int line() {
            return line;
        }

        @Override
        public int column() {
            return column;
        }
    }

    private static final class BinaryExpr implements Expr {
        private final char operator;
        private final Expr left;
        private final Expr right;
        private final int line;
        private final int column;

        BinaryExpr(char operator, Expr left, Expr right, int line, int column) {
            this.operator = operator;
            this.left = left;
            this.right = right;
            this.line = line;
            this.column = column;
        }

        @Override
        public double evaluate(Map<String, Double> scope) {
            double first = left.evaluate(scope);
            double second = right.evaluate(scope);
            switch (operator) {
                case '+':
                    return first + second;
                case '-':
                    return first - second;
                case '*':
                    return first * second;
                case '/':
                    if (second == 0.0) {
                        throw new QasmException("Division by zero in a parameter expression",
                            line, column);
                    }
                    return first / second;
                default:
                    throw new IllegalStateException("Unknown operator: " + operator);
            }
        }

        @Override
        public int line() {
            return line;
        }

        @Override
        public int column() {
            return column;
        }
    }

    private static final class Register {
        final String name;
        final int size;
        final int offset;

        Register(String name, int size, int offset) {
            this.name = name;
            this.size = size;
            this.offset = offset;
        }
    }

    private static final class QubitRef {
        final String registerName;
        final int index;
        final int line;
        final int column;

        QubitRef(String registerName, int index, int line, int column) {
            this.registerName = registerName;
            this.index = index;
            this.line = line;
            this.column = column;
        }
    }

    private static final class GateCall {
        final String name;
        final List<Expr> parameters;
        final List<String> qubitNames;
        final int line;
        final int column;

        GateCall(String name, List<Expr> parameters, List<String> qubitNames, int line,
                int column) {
            this.name = name;
            this.parameters = parameters;
            this.qubitNames = qubitNames;
            this.line = line;
            this.column = column;
        }
    }

    private static final class GateDefinition {
        final String name;
        final List<String> parameterNames;
        final List<String> qubitNames;
        final List<GateCall> body;

        GateDefinition(String name, List<String> parameterNames, List<String> qubitNames,
                List<GateCall> body) {
            this.name = name;
            this.parameterNames = parameterNames;
            this.qubitNames = qubitNames;
            this.body = body;
        }
    }

    private static final class Parser {
        private final String source;
        private final Lexer lexer;
        private final Map<String, Register> quantumRegisters = new LinkedHashMap<>();
        private final Map<String, Register> classicalRegisters = new LinkedHashMap<>();
        private final Map<String, GateDefinition> gateDefinitions = new LinkedHashMap<>();
        private final List<Qubit> qubits = new ArrayList<>();
        private final List<QuantumOperation> operations = new ArrayList<>();
        private int numQubits;
        private boolean qasm3;

        Parser(String source) {
            this.source = source;
            this.lexer = new Lexer(source);
        }

        QasmProgram parseProgram() {
            parseHeader();
            parseIncludes();
            while (lexer.peek().type != TokenType.EOF) {
                parseTopLevelStatement();
            }
            return new QasmProgram(qubits, classicalWidths(), quantumWidths(), operations,
                source);
        }

        private void parseHeader() {
            Token token = lexer.peek();
            if (token.type != TokenType.IDENTIFIER || !token.text.equals("OPENQASM")) {
                throw new QasmException("Missing OPENQASM header; the source must begin with "
                    + "OPENQASM 2.0; or OPENQASM 3.0;", token.line, token.column);
            }
            lexer.next();

            Token version = lexer.next();
            if (version.type != TokenType.NUMBER) {
                throw unexpected(version, "a version number after OPENQASM");
            }
            double versionValue;
            try {
                versionValue = Double.parseDouble(version.text);
            } catch (NumberFormatException e) {
                throw new QasmException("Invalid OpenQASM version '" + version.text + "'",
                    version.line, version.column);
            }
            if (versionValue == 2.0) {
                qasm3 = false;
            } else if (versionValue == 3.0) {
                qasm3 = true;
            } else {
                throw new QasmException("Unsupported OpenQASM version '" + version.text
                    + "'; only 2.0 and 3.0 are supported", version.line, version.column);
            }
            expectSymbol(";");
        }

        private void parseIncludes() {
            while (isIdentifier("include")) {
                lexer.next();
                Token path = lexer.next();
                if (path.type != TokenType.STRING) {
                    throw unexpected(path, "a quoted include path");
                }
                if (!path.text.equals("qelib1.inc") && !path.text.equals("stdgates.inc")) {
                    throw new QasmException("Unsupported include \"" + path.text
                        + "\"; only \"qelib1.inc\" and \"stdgates.inc\" are supported",
                        path.line, path.column);
                }
                expectSymbol(";");
            }
        }

        private void parseTopLevelStatement() {
            Token token = lexer.peek();
            if (token.type != TokenType.IDENTIFIER) {
                throw unexpected(token, "a statement");
            }
            switch (token.text) {
                case "qreg":
                    parseQreg();
                    return;
                case "creg":
                    parseCreg();
                    return;
                case "qubit":
                    parseTypedRegister(true);
                    return;
                case "bit":
                    parseTypedRegister(false);
                    return;
                case "gate":
                    parseGateDefinition();
                    return;
                case "measure":
                    parseMeasure(operations);
                    return;
                case "reset":
                    throw new QasmException("reset is not supported by the engine", token.line,
                        token.column);
                case "barrier":
                    parseBarrier();
                    return;
                case "if":
                    parseConditional(operations);
                    return;
                case "include":
                    throw new QasmException("An include statement is only allowed before the "
                        + "first instruction", token.line, token.column);
                case "opaque":
                    throw new QasmException("Opaque gate declarations are not supported",
                        token.line, token.column);
                default:
                    parseGateApplication(operations);
            }
        }

        private void parseQreg() {
            lexer.next();
            Token nameToken = nextIdentifier("a quantum register name");
            expectSymbol("[");
            int size = parseSize("quantum register");
            expectSymbol("]");
            expectSymbol(";");
            declareRegister(nameToken.text, size, true, nameToken.line, nameToken.column);
        }

        private void parseCreg() {
            lexer.next();
            Token nameToken = nextIdentifier("a classical register name");
            expectSymbol("[");
            int size = parseSize("classical register");
            expectSymbol("]");
            expectSymbol(";");
            declareRegister(nameToken.text, size, false, nameToken.line, nameToken.column);
        }

        private void parseTypedRegister(boolean quantum) {
            Token keyword = lexer.next();
            if (!qasm3) {
                throw new QasmException("'" + keyword.text + "[n] name;' declarations require "
                    + "OPENQASM 3.0", keyword.line, keyword.column);
            }
            expectSymbol("[");
            int size = parseSize(quantum ? "quantum register" : "classical register");
            expectSymbol("]");
            Token nameToken = nextIdentifier(quantum ? "a quantum register name"
                : "a classical register name");
            expectSymbol(";");
            declareRegister(nameToken.text, size, quantum, nameToken.line, nameToken.column);
        }

        private int parseSize(String description) {
            Token token = lexer.next();
            if (token.type != TokenType.NUMBER || !isIntegerLiteral(token.text)) {
                throw new QasmException("The size of a " + description
                    + " must be an integer literal", token.line, token.column);
            }
            int size;
            try {
                size = Integer.parseInt(token.text);
            } catch (NumberFormatException e) {
                throw new QasmException("Register size is out of range", token.line,
                    token.column);
            }
            if (size <= 0) {
                throw new QasmException("Register size must be positive", token.line,
                    token.column);
            }
            return size;
        }

        private void declareRegister(String name, int size, boolean quantum, int line,
                int column) {
            if (quantumRegisters.containsKey(name) || classicalRegisters.containsKey(name)) {
                throw new QasmException("Duplicate register name '" + name + "'", line, column);
            }
            if (quantum) {
                if (numQubits > Integer.MAX_VALUE - size) {
                    throw new QasmException("Quantum register '" + name + "' is too large", line,
                        column);
                }
                quantumRegisters.put(name, new Register(name, size, numQubits));
                for (int i = 0; i < size; i++) {
                    int globalIndex = numQubits + i;
                    qubits.add(new Qubit(globalIndex, "q" + globalIndex));
                }
                numQubits += size;
            } else {
                classicalRegisters.put(name, new Register(name, size, 0));
            }
        }

        private void parseGateDefinition() {
            Token keyword = lexer.next();
            Token nameToken = nextIdentifier("a gate name");
            String name = nameToken.text;
            if (BUILTIN_GATES.contains(name)) {
                throw new QasmException("Gate '" + name + "' is built in and cannot be "
                    + "redefined", nameToken.line, nameToken.column);
            }
            if (gateDefinitions.containsKey(name)) {
                throw new QasmException("Gate '" + name + "' is already defined", nameToken.line,
                    nameToken.column);
            }

            List<String> parameterNames = new ArrayList<>();
            if (acceptSymbol("(")) {
                if (!acceptSymbol(")")) {
                    do {
                        Token parameter = nextIdentifier("a gate parameter name");
                        if (parameterNames.contains(parameter.text)) {
                            throw new QasmException("Duplicate gate parameter '"
                                + parameter.text + "'", parameter.line, parameter.column);
                        }
                        parameterNames.add(parameter.text);
                    } while (acceptSymbol(","));
                    expectSymbol(")");
                }
            }

            List<String> qubitNames = new ArrayList<>();
            do {
                Token qubitName = nextIdentifier("a formal qubit name");
                if (qubitNames.contains(qubitName.text)) {
                    throw new QasmException("Duplicate formal qubit '" + qubitName.text + "'",
                        qubitName.line, qubitName.column);
                }
                qubitNames.add(qubitName.text);
            } while (acceptSymbol(","));

            expectSymbol("{");
            List<GateCall> body = new ArrayList<>();
            while (!isSymbol("}")) {
                if (lexer.peek().type == TokenType.EOF) {
                    throw new QasmException("Unterminated definition of gate '" + name + "'",
                        keyword.line, keyword.column);
                }
                parseGateBodyStatement(name, qubitNames, body);
            }
            lexer.next();
            gateDefinitions.put(name,
                new GateDefinition(name, parameterNames, qubitNames, body));
        }

        private void parseGateBodyStatement(String gateName, List<String> formalQubits,
                List<GateCall> body) {
            Token token = lexer.peek();
            if (token.type != TokenType.IDENTIFIER) {
                throw unexpected(token, "a gate application inside the definition of gate '"
                    + gateName + "'");
            }
            switch (token.text) {
                case "barrier":
                    lexer.next();
                    nextIdentifier("a formal qubit name");
                    while (acceptSymbol(",")) {
                        nextIdentifier("a formal qubit name");
                    }
                    expectSymbol(";");
                    return;
                case "measure":
                case "reset":
                case "if":
                case "qreg":
                case "creg":
                case "qubit":
                case "bit":
                case "gate":
                case "include":
                case "opaque":
                    throw new QasmException("'" + token.text + "' is not allowed inside a gate "
                        + "definition", token.line, token.column);
                default:
                    Token callName = lexer.next();
                    List<Expr> parameters = parseParameterExpressions();
                    List<QubitRef> arguments = parseQubitArguments(false);
                    expectSymbol(";");
                    List<String> names = new ArrayList<>();
                    for (QubitRef argument : arguments) {
                        if (argument.index >= 0) {
                            throw new QasmException("A gate definition body may not index a "
                                + "qubit; use the formal qubit name", argument.line,
                                argument.column);
                        }
                        if (!formalQubits.contains(argument.registerName)) {
                            throw new QasmException("Unknown formal qubit '"
                                + argument.registerName + "' in the definition of gate '"
                                + gateName + "'", argument.line, argument.column);
                        }
                        names.add(argument.registerName);
                    }
                    body.add(new GateCall(callName.text, parameters, names, callName.line,
                        callName.column));
            }
        }

        private void parseMeasure(List<QuantumOperation> sink) {
            Token keyword = lexer.next();
            QubitRef source = parseQubitRef(true);
            expectSymbol("->");
            QubitRef target = parseQubitRef(true);
            expectSymbol(";");

            Register quantum = quantumRegisters.get(source.registerName);
            if (quantum == null) {
                throw unknownQuantumRegister(source);
            }
            Register classical = classicalRegisters.get(target.registerName);
            if (classical == null) {
                if (quantumRegisters.containsKey(target.registerName)) {
                    throw new QasmException("Measurement target '" + target.registerName
                        + "' is a quantum register; a classical register is required",
                        target.line, target.column);
                }
                throw new QasmException("Unknown classical register '" + target.registerName
                    + "'", target.line, target.column);
            }
            if (source.index >= 0 && source.index >= quantum.size) {
                throw new QasmException("Qubit index " + source.index + " is out of range for "
                    + "register '" + quantum.name + "' of size " + quantum.size, source.line,
                    source.column);
            }
            if (target.index >= 0 && target.index >= classical.size) {
                throw new QasmException("Classical bit index " + target.index + " is out of "
                    + "range for register '" + classical.name + "' of size " + classical.size,
                    target.line, target.column);
            }

            if (source.index >= 0 && target.index >= 0) {
                Qubit qubit = qubits.get(quantum.offset + source.index);
                sink.add(MeasurementOperator.computationalBasis(new Qubit[]{qubit}, 1)
                    .withClassicalTarget(classical.name, target.index));
            } else if (source.index < 0 && target.index < 0) {
                if (quantum.size != classical.size) {
                    throw new QasmException("Cannot measure quantum register '" + quantum.name
                        + "' of size " + quantum.size + " into classical register '"
                        + classical.name + "' of size " + classical.size, keyword.line,
                        keyword.column);
                }
                for (int i = 0; i < quantum.size; i++) {
                    Qubit qubit = qubits.get(quantum.offset + i);
                    sink.add(MeasurementOperator.computationalBasis(new Qubit[]{qubit}, 1)
                        .withClassicalTarget(classical.name, i));
                }
            } else {
                throw new QasmException("A measurement must map either one qubit to one bit or "
                    + "a whole quantum register to a whole classical register", keyword.line,
                    keyword.column);
            }
        }

        private void parseBarrier() {
            lexer.next();
            List<QubitRef> references = new ArrayList<>();
            references.add(parseQubitRef(true));
            while (acceptSymbol(",")) {
                references.add(parseQubitRef(true));
            }
            expectSymbol(";");
            for (QubitRef reference : references) {
                Register register = quantumRegisters.get(reference.registerName);
                if (register == null) {
                    throw unknownQuantumRegister(reference);
                }
                if (reference.index >= 0 && reference.index >= register.size) {
                    throw new QasmException("Qubit index " + reference.index + " is out of "
                        + "range for register '" + register.name + "' of size " + register.size,
                        reference.line, reference.column);
                }
            }
        }

        private void parseConditional(List<QuantumOperation> sink) {
            Token keyword = lexer.next();
            expectSymbol("(");
            Token registerToken = nextIdentifier("a classical register name");
            Register register = classicalRegisters.get(registerToken.text);
            if (register == null) {
                if (quantumRegisters.containsKey(registerToken.text)) {
                    throw new QasmException("Condition register '" + registerToken.text
                        + "' is a quantum register; a classical register is required",
                        registerToken.line, registerToken.column);
                }
                throw new QasmException("Unknown classical register '" + registerToken.text
                    + "'", registerToken.line, registerToken.column);
            }
            expectSymbol("==");
            Token valueToken = lexer.peek();
            Expr valueExpression = parseExpression();
            double value = evaluate(valueExpression);
            if (value < 0.0 || value != Math.rint(value) || value > Long.MAX_VALUE) {
                throw new QasmException("Condition value must be a non-negative integer",
                    valueToken.line, valueToken.column);
            }
            long expectedValue = (long) value;
            expectSymbol(")");

            List<QuantumOperation> guarded = new ArrayList<>();
            if (acceptSymbol("{")) {
                while (!isSymbol("}")) {
                    if (lexer.peek().type == TokenType.EOF) {
                        throw new QasmException("Unterminated conditional block",
                            keyword.line, keyword.column);
                    }
                    parseGuardedStatement(guarded);
                }
                lexer.next();
            } else {
                parseGuardedStatement(guarded);
            }

            long mask = register.size >= 64 ? -1L : (1L << register.size) - 1L;
            for (QuantumOperation operation : guarded) {
                sink.add(new ConditionalOperation(operation, register.name, expectedValue, mask));
            }
        }

        private void parseGuardedStatement(List<QuantumOperation> sink) {
            Token token = lexer.peek();
            if (token.type != TokenType.IDENTIFIER) {
                throw unexpected(token, "a gate application or a measurement after the "
                    + "condition");
            }
            switch (token.text) {
                case "if":
                    throw new QasmException("Nested conditions are not supported", token.line,
                        token.column);
                case "barrier":
                    throw new QasmException("A barrier cannot be guarded by a classical "
                        + "condition", token.line, token.column);
                case "reset":
                    throw new QasmException("reset is not supported by the engine", token.line,
                        token.column);
                case "measure":
                    parseMeasure(sink);
                    return;
                case "qreg":
                case "creg":
                case "qubit":
                case "bit":
                case "gate":
                case "include":
                case "opaque":
                    throw unexpected(token, "a gate application or a measurement after the "
                        + "condition");
                default:
                    parseGateApplication(sink);
            }
        }

        private void parseGateApplication(List<QuantumOperation> sink) {
            Token nameToken = lexer.next();
            String name = nameToken.text;
            List<Expr> parameterExpressions = parseParameterExpressions();
            List<QubitRef> arguments = parseQubitArguments(true);
            expectSymbol(";");

            List<Double> parameters = new ArrayList<>();
            for (Expr expression : parameterExpressions) {
                parameters.add(evaluate(expression));
            }
            applyGate(name, parameters, arguments, sink, nameToken.line, nameToken.column);
        }

        private void applyGate(String name, List<Double> parameters, List<QubitRef> arguments,
                List<QuantumOperation> sink, int line, int column) {
            int arity = gateArity(name, line, column);
            if (arguments.size() != arity) {
                throw new QasmException("Gate '" + name + "' expects " + arity + " qubit "
                    + "argument" + (arity == 1 ? "" : "s") + " but " + arguments.size()
                    + " were supplied", line, column);
            }

            int broadcast = 1;
            for (QubitRef argument : arguments) {
                Register register = quantumRegisters.get(argument.registerName);
                if (register == null) {
                    throw unknownQuantumRegister(argument);
                }
                if (argument.index >= 0 && argument.index >= register.size) {
                    throw new QasmException("Qubit index " + argument.index + " is out of "
                        + "range for register '" + register.name + "' of size " + register.size,
                        argument.line, argument.column);
                }
                if (argument.index < 0) {
                    if (broadcast == 1) {
                        broadcast = register.size;
                    } else if (broadcast != register.size) {
                        throw new QasmException("Whole register arguments of gate '" + name
                            + "' must have the same width, but '" + argument.registerName
                            + "' has width " + register.size, argument.line, argument.column);
                    }
                }
            }

            for (int step = 0; step < broadcast; step++) {
                Qubit[] actualQubits = new Qubit[arguments.size()];
                for (int i = 0; i < arguments.size(); i++) {
                    QubitRef argument = arguments.get(i);
                    Register register = quantumRegisters.get(argument.registerName);
                    int localIndex = argument.index >= 0 ? argument.index : step;
                    actualQubits[i] = qubits.get(register.offset + localIndex);
                }
                expandGate(name, parameters, actualQubits, 0, new ArrayDeque<>(), sink, line,
                    column);
            }
        }

        private void expandGate(String name, List<Double> parameters, Qubit[] qargs, int depth,
                Deque<String> stack, List<QuantumOperation> sink, int line, int column) {
            if (depth > MAX_EXPANSION_DEPTH) {
                throw new QasmException("Gate expansion exceeded the maximum depth of "
                    + MAX_EXPANSION_DEPTH + " while expanding '" + name + "'; check for a "
                    + "recursive gate definition", line, column);
            }
            if (BUILTIN_GATES.contains(name)) {
                sink.add(createBuiltinGate(name, parameters, qargs, line, column));
                return;
            }

            GateDefinition definition = gateDefinitions.get(name);
            if (definition == null) {
                throw new QasmException("Unknown gate '" + name + "'. Known gates: "
                    + knownGateNames(), line, column);
            }
            if (stack.contains(name)) {
                List<String> chain = new ArrayList<>(stack);
                chain.add(name);
                throw new QasmException("Cyclic gate definition: "
                    + String.join(" -> ", chain), line, column);
            }
            if (parameters.size() != definition.parameterNames.size()) {
                throw new QasmException("Gate '" + name + "' expects "
                    + definition.parameterNames.size() + " parameter"
                    + (definition.parameterNames.size() == 1 ? "" : "s") + " but "
                    + parameters.size() + " were supplied", line, column);
            }

            Map<String, Double> scope = new HashMap<>();
            for (int i = 0; i < definition.parameterNames.size(); i++) {
                scope.put(definition.parameterNames.get(i), parameters.get(i));
            }

            stack.addLast(name);
            for (GateCall call : definition.body) {
                List<Double> values = new ArrayList<>();
                for (Expr expression : call.parameters) {
                    values.add(evaluate(expression, scope));
                }
                Qubit[] mapped = new Qubit[call.qubitNames.size()];
                for (int i = 0; i < call.qubitNames.size(); i++) {
                    int position = definition.qubitNames.indexOf(call.qubitNames.get(i));
                    mapped[i] = qargs[position];
                }
                expandGate(call.name, values, mapped, depth + 1, stack, sink, call.line,
                    call.column);
            }
            stack.removeLast();
        }

        private QuantumGate createBuiltinGate(String name, List<Double> parameters,
                Qubit[] qargs, int line, int column) {
            switch (name) {
                case "x":
                    requireParameters(name, parameters, 0, line, column);
                    return SingleQubitGate.createPauliX(qargs[0]);
                case "y":
                    requireParameters(name, parameters, 0, line, column);
                    return SingleQubitGate.createPauliY(qargs[0]);
                case "z":
                    requireParameters(name, parameters, 0, line, column);
                    return SingleQubitGate.createPauliZ(qargs[0]);
                case "h":
                    requireParameters(name, parameters, 0, line, column);
                    return SingleQubitGate.createHadamard(qargs[0]);
                case "s":
                    requireParameters(name, parameters, 0, line, column);
                    return new SingleQubitGate("s", phaseMatrix(Math.PI / 2), qargs[0]);
                case "sdg":
                    requireParameters(name, parameters, 0, line, column);
                    return new SingleQubitGate("sdg", phaseMatrix(-Math.PI / 2), qargs[0]);
                case "t":
                    requireParameters(name, parameters, 0, line, column);
                    return new SingleQubitGate("t", phaseMatrix(Math.PI / 4), qargs[0]);
                case "tdg":
                    requireParameters(name, parameters, 0, line, column);
                    return new SingleQubitGate("tdg", phaseMatrix(-Math.PI / 4), qargs[0]);
                case "id":
                    requireParameters(name, parameters, 0, line, column);
                    return new SingleQubitGate("id", ComplexMatrix.identity(2), qargs[0]);
                case "rx":
                    requireParameters(name, parameters, 1, line, column);
                    return SingleQubitGate.createRX(parameters.get(0), qargs[0]);
                case "ry":
                    requireParameters(name, parameters, 1, line, column);
                    return SingleQubitGate.createRY(parameters.get(0), qargs[0]);
                case "rz":
                    requireParameters(name, parameters, 1, line, column);
                    return SingleQubitGate.createRZ(parameters.get(0), qargs[0]);
                case "u1":
                    requireParameters(name, parameters, 1, line, column);
                    return customGate("u1(" + parameters.get(0) + ")",
                        u1Matrix(parameters.get(0)), qargs);
                case "u2":
                    requireParameters(name, parameters, 2, line, column);
                    return customGate("u2(" + parameters.get(0) + "," + parameters.get(1) + ")",
                        u2Matrix(parameters.get(0), parameters.get(1)), qargs);
                case "u3":
                    requireParameters(name, parameters, 3, line, column);
                    return customGate("u3(" + parameters.get(0) + "," + parameters.get(1) + ","
                        + parameters.get(2) + ")",
                        u3Matrix(parameters.get(0), parameters.get(1), parameters.get(2)),
                        qargs);
                case "cx":
                case "cnot":
                    requireParameters(name, parameters, 0, line, column);
                    return MultiQubitGate.createCNOT(qargs[0], qargs[1]);
                case "cz":
                    requireParameters(name, parameters, 0, line, column);
                    return MultiQubitGate.createCZ(qargs[0], qargs[1]);
                case "swap":
                    requireParameters(name, parameters, 0, line, column);
                    return MultiQubitGate.createSWAP(qargs[0], qargs[1]);
                case "ccx":
                    requireParameters(name, parameters, 0, line, column);
                    return MultiQubitGate.createToffoli(qargs[0], qargs[1], qargs[2]);
                case "cp":
                case "cphase":
                    requireParameters(name, parameters, 1, line, column);
                    return MultiQubitGate.createCPHASE(parameters.get(0), qargs[0], qargs[1]);
                default:
                    throw new IllegalStateException("Unexpected built-in gate: " + name);
            }
        }

        private void requireParameters(String name, List<Double> parameters, int expected,
                int line, int column) {
            if (parameters.size() != expected) {
                throw new QasmException("Gate '" + name + "' expects " + expected + " "
                    + "parameter" + (expected == 1 ? "" : "s") + " but " + parameters.size()
                    + " were supplied", line, column);
            }
        }

        private int gateArity(String name, int line, int column) {
            switch (name) {
                case "x":
                case "y":
                case "z":
                case "h":
                case "s":
                case "sdg":
                case "t":
                case "tdg":
                case "id":
                case "rx":
                case "ry":
                case "rz":
                case "u1":
                case "u2":
                case "u3":
                    return 1;
                case "cx":
                case "cnot":
                case "cz":
                case "swap":
                case "cp":
                case "cphase":
                    return 2;
                case "ccx":
                    return 3;
                default:
                    GateDefinition definition = gateDefinitions.get(name);
                    if (definition == null) {
                        throw new QasmException("Unknown gate '" + name + "'. Known gates: "
                            + knownGateNames(), line, column);
                    }
                    return definition.qubitNames.size();
            }
        }

        private String knownGateNames() {
            Set<String> names = new TreeSet<>(BUILTIN_GATES);
            names.addAll(gateDefinitions.keySet());
            return String.join(", ", names);
        }

        private List<Expr> parseParameterExpressions() {
            if (!isSymbol("(")) {
                return Collections.emptyList();
            }
            lexer.next();
            List<Expr> parameters = new ArrayList<>();
            if (!isSymbol(")")) {
                do {
                    parameters.add(parseExpression());
                } while (acceptSymbol(","));
            }
            expectSymbol(")");
            return parameters;
        }

        private List<QubitRef> parseQubitArguments(boolean allowIndex) {
            List<QubitRef> arguments = new ArrayList<>();
            arguments.add(parseQubitRef(allowIndex));
            while (acceptSymbol(",")) {
                arguments.add(parseQubitRef(allowIndex));
            }
            return arguments;
        }

        private QubitRef parseQubitRef(boolean allowIndex) {
            Token nameToken = nextIdentifier("a register name");
            int index = -1;
            if (isSymbol("[")) {
                if (!allowIndex) {
                    throw new QasmException("A gate definition body may not index a qubit; use "
                        + "the formal qubit name", nameToken.line, nameToken.column);
                }
                lexer.next();
                Token indexToken = lexer.next();
                if (indexToken.type != TokenType.NUMBER
                        || !isIntegerLiteral(indexToken.text)) {
                    throw new QasmException("A qubit index must be an integer literal",
                        indexToken.line, indexToken.column);
                }
                try {
                    index = Integer.parseInt(indexToken.text);
                } catch (NumberFormatException e) {
                    throw new QasmException("Qubit index is out of range", indexToken.line,
                        indexToken.column);
                }
                expectSymbol("]");
            }
            return new QubitRef(nameToken.text, index, nameToken.line, nameToken.column);
        }

        private Expr parseExpression() {
            Expr left = parseTerm();
            while (isSymbol("+") || isSymbol("-")) {
                Token operator = lexer.next();
                Expr right = parseTerm();
                left = new BinaryExpr(operator.text.charAt(0), left, right, operator.line,
                    operator.column);
            }
            return left;
        }

        private Expr parseTerm() {
            Expr left = parseFactor();
            while (isSymbol("*") || isSymbol("/")) {
                Token operator = lexer.next();
                Expr right = parseFactor();
                left = new BinaryExpr(operator.text.charAt(0), left, right, operator.line,
                    operator.column);
            }
            return left;
        }

        private Expr parseFactor() {
            Token token = lexer.peek();
            if (token.type == TokenType.SYMBOL && (token.text.equals("-")
                    || token.text.equals("+"))) {
                lexer.next();
                Expr operand = parseFactor();
                return new UnaryExpr(token.text.charAt(0), operand, token.line, token.column);
            }
            if (token.type == TokenType.SYMBOL && token.text.equals("(")) {
                lexer.next();
                Expr expression = parseExpression();
                expectSymbol(")");
                return expression;
            }
            if (token.type == TokenType.NUMBER) {
                lexer.next();
                return new NumberExpr(token.numberValue, token.line, token.column);
            }
            if (token.type == TokenType.IDENTIFIER) {
                lexer.next();
                if (token.text.equals("pi")) {
                    return new PiExpr(token.line, token.column);
                }
                return new VariableExpr(token.text, token.line, token.column);
            }
            throw unexpected(token, "a number, 'pi', a parameter name, or '('");
        }

        private double evaluate(Expr expression) {
            return evaluate(expression, Collections.emptyMap());
        }

        private double evaluate(Expr expression, Map<String, Double> scope) {
            double value = expression.evaluate(scope);
            if (!Double.isFinite(value)) {
                throw new QasmException("A parameter expression produced a non-finite value",
                    expression.line(), expression.column());
            }
            return value;
        }

        private QasmException unknownQuantumRegister(QubitRef reference) {
            if (classicalRegisters.containsKey(reference.registerName)) {
                return new QasmException("Register '" + reference.registerName
                    + "' is a classical register and cannot be used as a quantum argument",
                    reference.line, reference.column);
            }
            return new QasmException("Unknown quantum register '" + reference.registerName
                + "'", reference.line, reference.column);
        }

        private Map<String, Integer> classicalWidths() {
            Map<String, Integer> widths = new LinkedHashMap<>();
            for (Register register : classicalRegisters.values()) {
                widths.put(register.name, register.size);
            }
            return widths;
        }

        private Map<String, Integer> quantumWidths() {
            Map<String, Integer> widths = new LinkedHashMap<>();
            for (Register register : quantumRegisters.values()) {
                widths.put(register.name, register.size);
            }
            return widths;
        }

        private boolean isIdentifier(String text) {
            Token token = lexer.peek();
            return token.type == TokenType.IDENTIFIER && token.text.equals(text);
        }

        private boolean isSymbol(String text) {
            Token token = lexer.peek();
            return token.type == TokenType.SYMBOL && token.text.equals(text);
        }

        private boolean acceptSymbol(String text) {
            if (isSymbol(text)) {
                lexer.next();
                return true;
            }
            return false;
        }

        private void expectSymbol(String text) {
            Token token = lexer.next();
            if (token.type != TokenType.SYMBOL || !token.text.equals(text)) {
                throw unexpected(token, "'" + text + "'");
            }
        }

        private Token nextIdentifier(String description) {
            Token token = lexer.next();
            if (token.type != TokenType.IDENTIFIER) {
                throw unexpected(token, description);
            }
            return token;
        }

        private QasmException unexpected(Token token, String expected) {
            return new QasmException("Expected " + expected + " but found " + describe(token),
                token.line, token.column);
        }

        private String describe(Token token) {
            if (token.type == TokenType.EOF) {
                return "end of input";
            }
            if (token.type == TokenType.STRING) {
                return "string \"" + token.text + "\"";
            }
            return "'" + token.text + "'";
        }

        private boolean isIntegerLiteral(String text) {
            if (text.isEmpty()) {
                return false;
            }
            for (int i = 0; i < text.length(); i++) {
                if (!Character.isDigit(text.charAt(i))) {
                    return false;
                }
            }
            return true;
        }
    }

    private static QuantumGate customGate(String name, ComplexMatrix matrix, Qubit[] qargs) {
        return new QuantumGate.CustomGate(name, matrix, new Qubit[]{qargs[0]}, new Qubit[0],
            1.0);
    }

    private static ComplexMatrix phaseMatrix(double angle) {
        ComplexMatrix matrix = ComplexMatrix.identity(2);
        matrix.set(1, 1, Complex.fromPolar(1.0, angle));
        return matrix;
    }

    private static ComplexMatrix u1Matrix(double theta) {
        ComplexMatrix matrix = ComplexMatrix.identity(2);
        matrix.set(1, 1, Complex.fromPolar(1.0, theta));
        return matrix;
    }

    private static ComplexMatrix u2Matrix(double phi, double lambda) {
        double factor = 1.0 / Math.sqrt(2.0);
        ComplexMatrix matrix = new ComplexMatrix(2, 2);
        matrix.set(0, 0, new Complex(factor, 0.0));
        matrix.set(0, 1, Complex.fromPolar(factor, lambda).negate());
        matrix.set(1, 0, Complex.fromPolar(factor, phi));
        matrix.set(1, 1, Complex.fromPolar(factor, phi + lambda));
        return matrix;
    }

    private static ComplexMatrix u3Matrix(double theta, double phi, double lambda) {
        double cosine = Math.cos(theta / 2.0);
        double sine = Math.sin(theta / 2.0);
        ComplexMatrix matrix = new ComplexMatrix(2, 2);
        matrix.set(0, 0, new Complex(cosine, 0.0));
        matrix.set(0, 1, Complex.fromPolar(sine, lambda).negate());
        matrix.set(1, 0, Complex.fromPolar(sine, phi));
        matrix.set(1, 1, Complex.fromPolar(cosine, phi + lambda));
        return matrix;
    }
}
