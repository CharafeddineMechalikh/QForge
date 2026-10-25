package com.quantum.simulator.operations;

import com.quantum.simulator.state.QuantumState;
import com.quantum.simulator.state.Qubit;
import com.quantum.simulator.state.StateVector;
import com.quantum.simulator.math.Complex;
import com.quantum.simulator.math.ComplexMatrix;
import java.util.Objects;

/**
 * Gate that acts on exactly one target qubit and has no control qubits.
 *
 * <p>The gate matrix must be 2x2. The factory methods create the common single-qubit gates. As
 * with other {@link QuantumGate} subclasses, {@link #apply(QuantumState)} is not supported and the
 * gate is applied by the simulation strategy.</p>
 */
public class SingleQubitGate extends QuantumGate {
    private static final double DEFAULT_DURATION = 1.0;
    
    /**
     * Creates a single-qubit gate with the default duration of {@code 1.0}.
     *
     * @param name the gate name, must not be {@code null}
     * @param matrix the 2x2 gate matrix
     * @param targetQubit the target qubit
     * @throws IllegalArgumentException if the matrix is not 2x2
     * @throws NullPointerException if {@code name}, {@code matrix}, or {@code targetQubit} is
     *         {@code null}
     */
    public SingleQubitGate(String name, ComplexMatrix matrix, Qubit targetQubit) {
        this(name, matrix, targetQubit, DEFAULT_DURATION);
    }
    
    /**
     * Creates a single-qubit gate with the given duration.
     *
     * @param name the gate name, must not be {@code null}
     * @param matrix the 2x2 gate matrix
     * @param targetQubit the target qubit
     * @param duration the gate duration
     * @throws IllegalArgumentException if the matrix is not 2x2
     * @throws NullPointerException if {@code name}, {@code matrix}, or {@code targetQubit} is
     *         {@code null}
     */
    public SingleQubitGate(String name, ComplexMatrix matrix, Qubit targetQubit, double duration) {
        super(name, matrix, new Qubit[]{Objects.requireNonNull(targetQubit, "targetQubit")},
            new Qubit[0], duration);
        
        if (matrix.rows() != 2 || matrix.cols() != 2) {
            throw new IllegalArgumentException("Single-qubit gate matrix must be 2x2");
        }
    } 
    
    /**
     * Always throws {@link UnsupportedOperationException}; gate application is delegated to the
     * simulation strategy.
     *
     * @param state the state to operate on
     * @return never returns normally
     * @throws UnsupportedOperationException always
     */
    @Override
    public QuantumState apply(QuantumState state) {
        // Single-qubit gate application should also be handled by the strategy
        throw new UnsupportedOperationException(
            "Gate application should be handled through SimulationStrategy. " +
            "Use context.applyOperation() or strategy.applyGate() instead."
        );
    }
    
    /**
     * Creates a Pauli X gate on the target qubit.
     *
     * @param target the target qubit
     * @return a gate named {@code "X"} with the default duration
     */
    public static SingleQubitGate createPauliX(Qubit target) {
        ComplexMatrix matrix = new ComplexMatrix(2, 2);
        matrix.set(0, 0, Complex.ZERO);
        matrix.set(0, 1, Complex.ONE);
        matrix.set(1, 0, Complex.ONE);
        matrix.set(1, 1, Complex.ZERO);
        return new SingleQubitGate("X", matrix, target);
    }
    
    /**
     * Creates a Pauli Y gate on the target qubit.
     *
     * @param target the target qubit
     * @return a gate named {@code "Y"} with the default duration
     */
    public static SingleQubitGate createPauliY(Qubit target) {
        ComplexMatrix matrix = new ComplexMatrix(2, 2);
        matrix.set(0, 0, Complex.ZERO);
        matrix.set(0, 1, new Complex(0, -1));
        matrix.set(1, 0, new Complex(0, 1));
        matrix.set(1, 1, Complex.ZERO);
        return new SingleQubitGate("Y", matrix, target);
    }
    
    /**
     * Creates a Pauli Z gate on the target qubit.
     *
     * @param target the target qubit
     * @return a gate named {@code "Z"} with the default duration
     */
    public static SingleQubitGate createPauliZ(Qubit target) {
        ComplexMatrix matrix = new ComplexMatrix(2, 2);
        matrix.set(0, 0, Complex.ONE);
        matrix.set(0, 1, Complex.ZERO);
        matrix.set(1, 0, Complex.ZERO);
        matrix.set(1, 1, new Complex(-1, 0));
        return new SingleQubitGate("Z", matrix, target);
    }
    
    /**
     * Creates a Hadamard gate on the target qubit.
     *
     * @param target the target qubit
     * @return a gate named {@code "H"} with the default duration
     */
    public static SingleQubitGate createHadamard(Qubit target) {
        double factor = 1.0 / Math.sqrt(2);
        ComplexMatrix matrix = new ComplexMatrix(2, 2);
        matrix.set(0, 0, new Complex(factor, 0));
        matrix.set(0, 1, new Complex(factor, 0));
        matrix.set(1, 0, new Complex(factor, 0));
        matrix.set(1, 1, new Complex(-factor, 0));
        return new SingleQubitGate("H", matrix, target);
    }
    
    /**
     * Creates a phase gate that multiplies the {@code |1>} amplitude by {@code e^(i angle)}.
     *
     * @param angle the phase angle in radians
     * @param target the target qubit
     * @return a gate named {@code "P(angle)"} with the default duration
     */
    public static SingleQubitGate createPhase(double angle, Qubit target) {
        ComplexMatrix matrix = new ComplexMatrix(2, 2);
        matrix.set(0, 0, Complex.ONE);
        matrix.set(0, 1, Complex.ZERO);
        matrix.set(1, 0, Complex.ZERO);
        matrix.set(1, 1, Complex.fromPolar(1.0, angle));
        return new SingleQubitGate("P(" + angle + ")", matrix, target);
    }
    
    /**
     * Creates a rotation about the X axis of the Bloch sphere by the given angle.
     *
     * <p>The matrix is built from {@code cos(angle / 2)} and {@code sin(angle / 2)}.</p>
     *
     * @param angle the rotation angle in radians
     * @param target the target qubit
     * @return a gate named {@code "RX(angle)"} with the default duration
     */
    public static SingleQubitGate createRX(double angle, Qubit target) {
        double cos = Math.cos(angle / 2);
        double sin = Math.sin(angle / 2);
        
        ComplexMatrix matrix = new ComplexMatrix(2, 2);
        matrix.set(0, 0, new Complex(cos, 0));
        matrix.set(0, 1, new Complex(0, -sin));
        matrix.set(1, 0, new Complex(0, -sin));
        matrix.set(1, 1, new Complex(cos, 0));
        return new SingleQubitGate("RX(" + angle + ")", matrix, target);
    }
    
    /**
     * Creates a rotation about the Y axis of the Bloch sphere by the given angle.
     *
     * <p>The matrix is built from {@code cos(angle / 2)} and {@code sin(angle / 2)}.</p>
     *
     * @param angle the rotation angle in radians
     * @param target the target qubit
     * @return a gate named {@code "RY(angle)"} with the default duration
     */
    public static SingleQubitGate createRY(double angle, Qubit target) {
        double cos = Math.cos(angle / 2);
        double sin = Math.sin(angle / 2);
        
        ComplexMatrix matrix = new ComplexMatrix(2, 2);
        matrix.set(0, 0, new Complex(cos, 0));
        matrix.set(0, 1, new Complex(-sin, 0));
        matrix.set(1, 0, new Complex(sin, 0));
        matrix.set(1, 1, new Complex(cos, 0));
        return new SingleQubitGate("RY(" + angle + ")", matrix, target);
    }
    
    /**
     * Creates a rotation about the Z axis of the Bloch sphere by the given angle.
     *
     * <p>The matrix is diagonal, with phases {@code e^(-i angle / 2)} and
     * {@code e^(i angle / 2)}.</p>
     *
     * @param angle the rotation angle in radians
     * @param target the target qubit
     * @return a gate named {@code "RZ(angle)"} with the default duration
     */
    public static SingleQubitGate createRZ(double angle, Qubit target) {
        Complex phase0 = Complex.fromPolar(1.0, -angle / 2);
        Complex phase1 = Complex.fromPolar(1.0, angle / 2);
        
        ComplexMatrix matrix = new ComplexMatrix(2, 2);
        matrix.set(0, 0, phase0);
        matrix.set(0, 1, Complex.ZERO);
        matrix.set(1, 0, Complex.ZERO);
        matrix.set(1, 1, phase1);
        return new SingleQubitGate("RZ(" + angle + ")", matrix, target);
    }
    
    /**
     * Creates an S gate, equivalent to a phase gate with angle {@code Math.PI / 2}.
     *
     * @param target the target qubit
     * @return the phase gate returned by {@link #createPhase(double, Qubit)}
     */
    public static SingleQubitGate createS(Qubit target) {
        return createPhase(Math.PI / 2, target);
    }
    
    /**
     * Creates a T gate, equivalent to a phase gate with angle {@code Math.PI / 4}.
     *
     * @param target the target qubit
     * @return the phase gate returned by {@link #createPhase(double, Qubit)}
     */
    public static SingleQubitGate createT(Qubit target) {
        return createPhase(Math.PI / 4, target);
    }
}