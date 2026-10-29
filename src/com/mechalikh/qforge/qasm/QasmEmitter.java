package com.mechalikh.qforge.qasm;

import com.mechalikh.qforge.measurement.MeasurementOperator;
import com.mechalikh.qforge.operations.ConditionalOperation;
import com.mechalikh.qforge.operations.QuantumGate;
import com.mechalikh.qforge.operations.QuantumOperation;
import com.mechalikh.qforge.state.Qubit;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * Emitter that writes a {@link QasmProgram} as OpenQASM 2.0 source.
 *
 * <p>The output begins with the version header, optionally the qelib1 include, then one
 * declaration per quantum and classical register, then one instruction per line. Built-in gates
 * are written by their OpenQASM names, a conditional operation is written as
 * {@code if(creg==N)} followed by the guarded instruction, and a measurement is written as
 * {@code measure q[i] -> c[j]} using the register name and register offset of the measurement
 * operator.</p>
 *
 * <p>Angles are printed with {@link Locale#ROOT} formatting using six fractional digits by
 * default. Trailing zeros and a trailing decimal point are removed, and negative zero is
 * printed as zero. The format is stable, so emitting a program that was parsed from emitted
 * text produces the same text again.</p>
 *
 * <p>Operations that OpenQASM 2.0 cannot express are rejected with a {@link QasmException}
 * instead of being dropped. This covers noise models, measurements without a classical register
 * target, measurements of more than one qubit at once, measurements that address a bit outside
 * their register, conditions on undeclared registers, nested conditions, and gates whose name
 * does not correspond to a supported gate.</p>
 */
public final class QasmEmitter {
    /**
     * Options that control the emitted text.
     *
     * <p>Instances are immutable. The default options emit the qelib1 include, use six
     * fractional digits for angles, and end the text with a newline.</p>
     */
    public static final class Options {
        /** The default number of fractional digits used for angles. */
        public static final int DEFAULT_FRACTIONAL_DIGITS = 6;

        private final boolean includeQelib;
        private final int fractionalDigits;
        private final boolean trailingNewline;

        /**
         * Creates options with the default values.
         */
        public Options() {
            this(true, DEFAULT_FRACTIONAL_DIGITS, true);
        }

        private Options(boolean includeQelib, int fractionalDigits, boolean trailingNewline) {
            this.includeQelib = includeQelib;
            this.fractionalDigits = fractionalDigits;
            this.trailingNewline = trailingNewline;
        }

        /**
         * Returns a copy of these options with the qelib1 include toggled.
         *
         * @param includeQelib {@code true} to emit the {@code include "qelib1.inc";} line
         * @return new options with the requested include setting
         */
        public Options withIncludeQelib(boolean includeQelib) {
            return new Options(includeQelib, fractionalDigits, trailingNewline);
        }

        /**
         * Returns a copy of these options with a new fractional digit count.
         *
         * @param fractionalDigits the number of fractional digits, from 0 to 15
         * @return new options with the requested precision
         * @throws IllegalArgumentException if the digit count is outside the supported range
         */
        public Options withFractionalDigits(int fractionalDigits) {
            if (fractionalDigits < 0 || fractionalDigits > 15) {
                throw new IllegalArgumentException("Fractional digits must be between 0 and 15");
            }
            return new Options(includeQelib, fractionalDigits, trailingNewline);
        }

        /**
         * Returns a copy of these options with the trailing newline toggled.
         *
         * @param trailingNewline {@code true} to end the text with a newline
         * @return new options with the requested newline setting
         */
        public Options withTrailingNewline(boolean trailingNewline) {
            return new Options(includeQelib, fractionalDigits, trailingNewline);
        }

        /**
         * Reports whether the qelib1 include line is emitted.
         *
         * @return {@code true} when the include line is emitted
         */
        public boolean isIncludeQelib() {
            return includeQelib;
        }

        /**
         * Returns the number of fractional digits used for angles.
         *
         * @return the fractional digit count
         */
        public int getFractionalDigits() {
            return fractionalDigits;
        }

        /**
         * Reports whether the text ends with a newline.
         *
         * @return {@code true} when a trailing newline is emitted
         */
        public boolean isTrailingNewline() {
            return trailingNewline;
        }
    }

    private static final class GateSignature {
        final String name;
        final List<Double> parameters;
        final int arity;

        GateSignature(String name, List<Double> parameters, int arity) {
            this.name = name;
            this.parameters = parameters;
            this.arity = arity;
        }
    }

    /**
     * Creates an emitter that uses the default options.
     */
    public QasmEmitter() {
    }

    /**
     * Emits a program with the default options.
     *
     * @param program the program to emit
     * @return OpenQASM 2.0 source text
     * @throws NullPointerException if {@code program} is {@code null}
     * @throws QasmException if an operation cannot be expressed in OpenQASM 2.0
     */
    public String emit(QasmProgram program) {
        return emit(program, new Options());
    }

    /**
     * Emits a program with explicit options.
     *
     * @param program the program to emit
     * @param options the formatting options
     * @return OpenQASM 2.0 source text
     * @throws NullPointerException if {@code program} or {@code options} is {@code null}
     * @throws QasmException if an operation cannot be expressed in OpenQASM 2.0
     */
    public String emit(QasmProgram program, Options options) {
        Objects.requireNonNull(program, "program");
        Objects.requireNonNull(options, "options");

        StringBuilder output = new StringBuilder();
        output.append("OPENQASM 2.0;\n");
        if (options.isIncludeQelib()) {
            output.append("include \"qelib1.inc\";\n");
        }
        for (Map.Entry<String, Integer> register : program.getQuantumRegisters().entrySet()) {
            output.append("qreg ").append(register.getKey()).append('[')
                .append(register.getValue()).append("];\n");
        }
        for (Map.Entry<String, Integer> register : program.getRegisters().entrySet()) {
            output.append("creg ").append(register.getKey()).append('[')
                .append(register.getValue()).append("];\n");
        }
        for (QuantumOperation operation : program.getOperations()) {
            output.append(instruction(program, operation, options)).append(";\n");
        }

        String text = output.toString();
        if (!options.isTrailingNewline() && text.endsWith("\n")) {
            text = text.substring(0, text.length() - 1);
        }
        return text;
    }

    private String instruction(QasmProgram program, QuantumOperation operation,
            Options options) {
        if (operation instanceof ConditionalOperation) {
            ConditionalOperation conditional = (ConditionalOperation) operation;
            if (!program.getRegisters().containsKey(conditional.getRegisterName())) {
                throw new QasmException("Conditional operation refers to undeclared classical "
                    + "register '" + conditional.getRegisterName() + "'");
            }
            QuantumOperation guarded = conditional.getGuardedOperation();
            if (guarded instanceof ConditionalOperation) {
                throw new QasmException("Nested conditional operations cannot be expressed in "
                    + "OpenQASM 2.0");
            }
            return "if(" + conditional.getRegisterName() + "=="
                + conditional.getExpectedValue() + ") "
                + instruction(program, guarded, options);
        }

        if (operation instanceof MeasurementOperator) {
            return measurement(program, (MeasurementOperator) operation);
        }

        if (operation instanceof QuantumGate) {
            return gate(program, (QuantumGate) operation, options);
        }

        throw new QasmException("Operation '" + operation.getName() + "' of type "
            + operation.getClass().getName() + " cannot be expressed in OpenQASM 2.0");
    }

    private String measurement(QasmProgram program, MeasurementOperator measurement) {
        String registerName = measurement.getRegisterName();
        if (registerName == null) {
            throw new QasmException("A measurement without a classical register target cannot "
                + "be expressed in OpenQASM 2.0");
        }
        Integer width = program.getRegisters().get(registerName);
        if (width == null) {
            throw new QasmException("Measurement writes to undeclared classical register '"
                + registerName + "'");
        }
        Qubit[] targets = measurement.getTargetQubits();
        if (targets.length != 1) {
            throw new QasmException("A measurement of " + targets.length + " qubits at once "
                + "cannot be expressed in OpenQASM 2.0; use one measurement per qubit");
        }
        int offset = measurement.getRegisterOffset();
        if (offset < 0 || offset >= width) {
            throw new QasmException("Measurement offset " + offset + " is outside classical "
                + "register '" + registerName + "' of width " + width);
        }
        return "measure " + qubitReference(program, targets[0]) + " -> " + registerName + "["
            + offset + "]";
    }

    private String gate(QasmProgram program, QuantumGate gate, Options options) {
        GateSignature signature = signature(gate);
        Qubit[] controls = gate.getControlQubits();
        Qubit[] targets = gate.getTargetQubits();
        Qubit[] operands = new Qubit[controls.length + targets.length];
        System.arraycopy(controls, 0, operands, 0, controls.length);
        System.arraycopy(targets, 0, operands, controls.length, targets.length);
        if (operands.length != signature.arity) {
            throw new QasmException("Gate '" + gate.getName() + "' acts on " + operands.length
                + " qubits but OpenQASM gate '" + signature.name + "' expects "
                + signature.arity);
        }

        StringBuilder text = new StringBuilder(signature.name);
        if (!signature.parameters.isEmpty()) {
            text.append('(');
            for (int i = 0; i < signature.parameters.size(); i++) {
                if (i > 0) {
                    text.append(',');
                }
                text.append(formatAngle(signature.parameters.get(i),
                    options.getFractionalDigits()));
            }
            text.append(')');
        }
        for (int i = 0; i < operands.length; i++) {
            if (i > 0) {
                text.append(',');
            }
            text.append(' ').append(qubitReference(program, operands[i]));
        }
        return text.toString();
    }

    private GateSignature signature(QuantumGate gate) {
        String original = gate.getName();
        String lower = original.toLowerCase(Locale.ROOT);
        String base = lower;
        List<Double> parameters = new ArrayList<>();
        int open = original.indexOf('(');
        if (open >= 0) {
            int close = original.lastIndexOf(')');
            if (close <= open) {
                throw new QasmException("Gate name '" + original + "' does not contain a "
                    + "complete parameter list");
            }
            base = lower.substring(0, open);
            String inside = original.substring(open + 1, close).trim();
            if (!inside.isEmpty()) {
                for (String piece : inside.split(",")) {
                    parameters.add(parseAngle(piece.trim(), original));
                }
            }
        }

        switch (base) {
            case "x":
                return new GateSignature("x", noParameters(original, parameters), 1);
            case "y":
                return new GateSignature("y", noParameters(original, parameters), 1);
            case "z":
                return new GateSignature("z", noParameters(original, parameters), 1);
            case "h":
                return new GateSignature("h", noParameters(original, parameters), 1);
            case "s":
                return new GateSignature("s", noParameters(original, parameters), 1);
            case "sdg":
                return new GateSignature("sdg", noParameters(original, parameters), 1);
            case "t":
                return new GateSignature("t", noParameters(original, parameters), 1);
            case "tdg":
                return new GateSignature("tdg", noParameters(original, parameters), 1);
            case "id":
                return new GateSignature("id", noParameters(original, parameters), 1);
            case "rx":
                return parameterized(original, "rx", parameters, 1, 1);
            case "ry":
                return parameterized(original, "ry", parameters, 1, 1);
            case "rz":
                return parameterized(original, "rz", parameters, 1, 1);
            case "u1":
                return parameterized(original, "u1", parameters, 1, 1);
            case "u2":
                return parameterized(original, "u2", parameters, 2, 1);
            case "u3":
                return parameterized(original, "u3", parameters, 3, 1);
            case "p":
                return parameterized(original, "u1", parameters, 1, 1);
            case "cx":
            case "cnot":
                return new GateSignature("cx", noParameters(original, parameters), 2);
            case "cz":
                return new GateSignature("cz", noParameters(original, parameters), 2);
            case "swap":
                return new GateSignature("swap", noParameters(original, parameters), 2);
            case "ccx":
            case "toffoli":
                return new GateSignature("ccx", noParameters(original, parameters), 3);
            case "cp":
            case "cphase":
                return parameterized(original, "cp", parameters, 1, 2);
            default:
                throw new QasmException("Gate '" + original + "' cannot be expressed in "
                    + "OpenQASM 2.0");
        }
    }

    private List<Double> noParameters(String original, List<Double> parameters) {
        if (!parameters.isEmpty()) {
            throw new QasmException("Gate '" + original + "' cannot be expressed in OpenQASM "
                + "2.0 with parameters");
        }
        return parameters;
    }

    private GateSignature parameterized(String original, String name, List<Double> parameters,
            int expectedParameters, int arity) {
        if (parameters.size() != expectedParameters) {
            throw new QasmException("Gate '" + original + "' must carry "
                + expectedParameters + " parameter" + (expectedParameters == 1 ? "" : "s")
                + " in its name to be expressed in OpenQASM 2.0");
        }
        return new GateSignature(name, parameters, arity);
    }

    private double parseAngle(String text, String gateName) {
        try {
            double value = Double.parseDouble(text);
            if (!Double.isFinite(value)) {
                throw new NumberFormatException("not finite");
            }
            return value;
        } catch (NumberFormatException e) {
            throw new QasmException("Gate '" + gateName + "' carries an invalid parameter '"
                + text + "'");
        }
    }

    private String qubitReference(QasmProgram program, Qubit qubit) {
        for (Map.Entry<String, Integer> register : program.getQuantumRegisters().entrySet()) {
            int offset = program.getQuantumRegisterOffset(register.getKey());
            if (qubit.getIndex() >= offset && qubit.getIndex() < offset + register.getValue()) {
                return register.getKey() + "[" + (qubit.getIndex() - offset) + "]";
            }
        }
        throw new QasmException("Qubit " + qubit.getIndex()
            + " does not belong to any declared quantum register");
    }

    private String formatAngle(double angle, int fractionalDigits) {
        String formatted = String.format(Locale.ROOT, "%." + fractionalDigits + "f", angle);
        int dot = formatted.indexOf('.');
        if (dot >= 0) {
            int end = formatted.length();
            while (end > dot + 1 && formatted.charAt(end - 1) == '0') {
                end--;
            }
            if (end == dot + 1) {
                end = dot;
            }
            formatted = formatted.substring(0, end);
        }
        if (formatted.equals("-0")) {
            return "0";
        }
        return formatted;
    }
}
