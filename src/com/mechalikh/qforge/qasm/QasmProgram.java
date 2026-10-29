package com.mechalikh.qforge.qasm;

import com.mechalikh.qforge.operations.QuantumOperation;
import com.mechalikh.qforge.state.Qubit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Immutable result of parsing an OpenQASM document.
 *
 * <p>A program owns one {@link Qubit} per global qubit index. The qubits are named {@code q0},
 * {@code q1}, and so on, in index order, and every operation in {@link #getOperations()} refers
 * to exactly those objects. Quantum registers are concatenated in declaration order, so the
 * global index of a register bit is the width of every earlier quantum register plus the
 * register local index. {@link #getQuantumRegisterOffset(String)} reports that base index.</p>
 *
 * <p>Both register maps are unmodifiable snapshots that preserve declaration order and map a
 * register name to its width. The operation list is a snapshot of the expanded circuit:
 * user-defined gates are already inlined, barriers are discarded because the engine has no
 * barrier operation, and every measurement carries its classical register target.</p>
 */
public final class QasmProgram {
    private final int numQubits;
    private final Map<String, Integer> registers;
    private final Map<String, Integer> quantumRegisters;
    private final List<Qubit> qubits;
    private final List<QuantumOperation> operations;
    private final String source;

    /**
     * Creates a program and one qubit for each global qubit index.
     *
     * <p>The register maps and the operation list are copied. The qubits created here are not
     * substituted into the supplied operations, so an operation should already refer to qubits
     * whose indices are below {@code numQubits}; the parser uses the package private constructor
     * to keep the identity between its qubits and its operations.</p>
     *
     * @param numQubits total number of qubits, zero or more
     * @param registers classical registers in declaration order
     * @param quantumRegisters quantum registers in declaration order
     * @param operations expanded operations in execution order
     * @param source original source text, may be {@code null}
     * @throws IllegalArgumentException if a width is not positive, if {@code numQubits} is
     *         negative, or if the quantum register widths do not sum to {@code numQubits}
     * @throws NullPointerException if a register map or the operation list is {@code null}
     */
    public QasmProgram(int numQubits, Map<String, Integer> registers,
            Map<String, Integer> quantumRegisters, List<QuantumOperation> operations,
            String source) {
        this(createQubits(numQubits), registers, quantumRegisters, operations, source);
    }

    QasmProgram(List<Qubit> qubits, Map<String, Integer> registers,
            Map<String, Integer> quantumRegisters, List<QuantumOperation> operations,
            String source) {
        Objects.requireNonNull(qubits, "qubits");
        Objects.requireNonNull(registers, "registers");
        Objects.requireNonNull(quantumRegisters, "quantumRegisters");
        Objects.requireNonNull(operations, "operations");

        this.qubits = Collections.unmodifiableList(new ArrayList<>(qubits));
        this.registers = Collections.unmodifiableMap(new LinkedHashMap<>(registers));
        this.quantumRegisters = Collections.unmodifiableMap(new LinkedHashMap<>(quantumRegisters));
        this.operations = Collections.unmodifiableList(new ArrayList<>(operations));
        this.source = source != null ? source : "";
        this.numQubits = this.qubits.size();

        validateWidths(this.registers, "classical");
        validateWidths(this.quantumRegisters, "quantum");
        int declaredQubits = 0;
        for (int width : this.quantumRegisters.values()) {
            declaredQubits += width;
        }
        if (declaredQubits != numQubits) {
            throw new IllegalArgumentException("Quantum register widths sum to " + declaredQubits
                + " but the program has " + numQubits + " qubits");
        }
    }

    private static List<Qubit> createQubits(int numQubits) {
        if (numQubits < 0) {
            throw new IllegalArgumentException("Qubit count cannot be negative");
        }
        List<Qubit> result = new ArrayList<>(numQubits);
        for (int i = 0; i < numQubits; i++) {
            result.add(new Qubit(i, "q" + i));
        }
        return result;
    }

    private static void validateWidths(Map<String, Integer> widths, String kind) {
        for (Map.Entry<String, Integer> entry : widths.entrySet()) {
            if (entry.getKey() == null || entry.getKey().isEmpty()) {
                throw new IllegalArgumentException("A " + kind + " register name cannot be empty");
            }
            if (entry.getValue() == null || entry.getValue() <= 0) {
                throw new IllegalArgumentException("The " + kind + " register '"
                    + entry.getKey() + "' must have a positive width");
            }
        }
    }

    /**
     * Returns the total number of qubits in the program.
     *
     * @return the number of global qubit indices
     */
    public int getNumQubits() {
        return numQubits;
    }

    /**
     * Returns the classical registers in declaration order.
     *
     * @return an unmodifiable map from register name to register width
     */
    public Map<String, Integer> getRegisters() {
        return registers;
    }

    /**
     * Returns the quantum registers in declaration order.
     *
     * @return an unmodifiable map from register name to register width
     */
    public Map<String, Integer> getQuantumRegisters() {
        return quantumRegisters;
    }

    /**
     * Returns the qubits owned by this program.
     *
     * @return an unmodifiable list indexed by global qubit index
     */
    public List<Qubit> getQubits() {
        return qubits;
    }

    /**
     * Returns the qubit for one global index.
     *
     * @param index the global qubit index
     * @return the qubit at that index
     * @throws IndexOutOfBoundsException if the index is outside the program
     */
    public Qubit getQubit(int index) {
        return qubits.get(index);
    }

    /**
     * Returns the operations in execution order.
     *
     * @return an unmodifiable list of expanded operations
     */
    public List<QuantumOperation> getOperations() {
        return operations;
    }

    /**
     * Returns the original source text.
     *
     * @return the source text, or an empty string when no source was supplied
     */
    public String getSource() {
        return source;
    }

    /**
     * Returns the global index of the first qubit of a quantum register.
     *
     * <p>The register local index is added to this offset to obtain the global qubit index used
     * by the operations of this program.</p>
     *
     * @param name the quantum register name
     * @return the global index of register bit zero
     * @throws IllegalArgumentException if no quantum register uses this name
     */
    public int getQuantumRegisterOffset(String name) {
        int offset = 0;
        for (Map.Entry<String, Integer> entry : quantumRegisters.entrySet()) {
            if (entry.getKey().equals(name)) {
                return offset;
            }
            offset += entry.getValue();
        }
        throw new IllegalArgumentException("Unknown quantum register: " + name);
    }

    /**
     * Returns a description of the program size and operations.
     *
     * @return a diagnostic string with the qubit count, register count, and operation count
     */
    @Override
    public String toString() {
        return "QasmProgram[qubits=" + numQubits + ", quantumRegisters=" + quantumRegisters.size()
            + ", classicalRegisters=" + registers.size() + ", operations=" + operations.size()
            + "]";
    }
}
