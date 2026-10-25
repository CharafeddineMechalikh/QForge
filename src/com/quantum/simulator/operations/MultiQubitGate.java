package com.quantum.simulator.operations;

import com.quantum.simulator.state.QuantumState;
import com.quantum.simulator.state.Qubit;
import com.quantum.simulator.math.Complex;
import com.quantum.simulator.math.ComplexMatrix;

/**
 * Gate that covers multiple qubits.
 *
 * <p>The constructor requires at least two target qubits or at least one control qubit. The matrix
 * covers all control and target qubits, with control qubits ordered first and target qubits
 * ordered last. The factory methods build the standard controlled and multi-qubit gates. As with
 * other {@link QuantumGate} subclasses, {@link #apply(QuantumState)} is not supported and the gate
 * is applied by the simulation strategy.</p>
 */
public class MultiQubitGate extends QuantumGate {
    
	
	/**
	 * Creates a multi-qubit gate with no control qubits and a duration of {@code 1.0}.
	 *
	 * @param name the gate name, must not be {@code null}
	 * @param matrix the gate matrix
	 * @param targetQubits the target qubits
	 * @throws IllegalArgumentException if the matrix dimensions are invalid, or if fewer than two
	 *         target qubits are supplied and there are no control qubits
	 * @throws NullPointerException if {@code name}, {@code matrix}, or {@code targetQubits} is
	 *         {@code null}
	 */
	public MultiQubitGate(String name, ComplexMatrix matrix, Qubit[] targetQubits) {
        this(name, matrix, targetQubits, new Qubit[0], 1.0);
    }
    
	/**
	 * Creates a multi-qubit gate with no control qubits and the given duration.
	 *
	 * @param name the gate name, must not be {@code null}
	 * @param matrix the gate matrix
	 * @param targetQubits the target qubits
	 * @param duration the gate duration
	 * @throws IllegalArgumentException if the matrix dimensions are invalid, or if fewer than two
	 *         target qubits are supplied and there are no control qubits
	 * @throws NullPointerException if {@code name}, {@code matrix}, or {@code targetQubits} is
	 *         {@code null}
	 */
    public MultiQubitGate(String name, ComplexMatrix matrix, Qubit[] targetQubits, double duration) {
        this(name, matrix, targetQubits, new Qubit[0], duration);
    }
    
	/**
	 * Creates a multi-qubit gate with control and target qubits.
	 *
	 * @param name the gate name, must not be {@code null}
	 * @param matrix the gate matrix, ordered with control qubits first, then target qubits
	 * @param targetQubits the target qubits
	 * @param controlQubits the control qubits
	 * @param duration the gate duration
	 * @throws IllegalArgumentException if the matrix dimensions are invalid, or if fewer than two
	 *         target qubits are supplied and there are no control qubits
	 * @throws NullPointerException if {@code name}, {@code matrix}, {@code targetQubits}, or
	 *         {@code controlQubits} is {@code null}
	 */
    public MultiQubitGate(String name, ComplexMatrix matrix, 
                         Qubit[] targetQubits, Qubit[] controlQubits, double duration) {
        super(name, matrix, targetQubits, controlQubits, duration);
        
        if (targetQubits.length < 2 && controlQubits.length == 0) {
            throw new IllegalArgumentException("Multi-qubit gate must act on at least 2 qubits");
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
        // Multi-qubit gate application should be handled by the simulation strategy
        // This method should not be called directly
        throw new UnsupportedOperationException(
            "Gate application should be handled through SimulationStrategy. " +
            "Use context.applyOperation() or strategy.applyGate() instead."
        );
    }
    
    
    /**
     * Creates a controlled NOT gate.
     *
     * <p>The control qubit is stored as the only control and the target as the only target, so the
     * 4x4 matrix uses the order {@code [control, target]}. The matrix exchanges the basis states
     * where the control is one and the target differs.</p>
     *
     * @param control the control qubit
     * @param target the target qubit
     * @return a gate named {@code "CNOT"} with duration {@code 1.0}
     */
    public static MultiQubitGate createCNOT(Qubit control, Qubit target) {
        ComplexMatrix matrix = new ComplexMatrix(4, 4);
        // Initialize to zero
        for (int i = 0; i < 4; i++) {
            for (int j = 0; j < 4; j++) {
                matrix.set(i, j, Complex.ZERO);
            }
        }
        
        // CNOT: control is the most significant bit, target the least significant
        matrix.set(0, 0, Complex.ONE);  // |00> -> |00>
        matrix.set(1, 1, Complex.ONE);  // |01> -> |01>
        matrix.set(2, 3, Complex.ONE);  // |10> -> |11> (flip target)
        matrix.set(3, 2, Complex.ONE);  // |11> -> |10> (flip target)
        
        return new MultiQubitGate("CNOT", matrix, new Qubit[]{target}, new Qubit[]{control}, 1.0);
    }
    
    /**
     * Creates a SWAP gate.
     *
     * <p>Both qubits are stored as targets, in the given order, and the 4x4 matrix exchanges the
     * basis states where the two qubits differ.</p>
     *
     * @param qubit1 the first qubit
     * @param qubit2 the second qubit
     * @return a gate named {@code "SWAP"} with duration {@code 1.0}
     */
    public static MultiQubitGate createSWAP(Qubit qubit1, Qubit qubit2) {
        ComplexMatrix matrix = new ComplexMatrix(4, 4);
        // |00><00| + |01><10| + |10><01| + |11><11|
        matrix.set(0, 0, Complex.ONE);
        matrix.set(1, 2, Complex.ONE);
        matrix.set(2, 1, Complex.ONE);
        matrix.set(3, 3, Complex.ONE);
        return new MultiQubitGate("SWAP", matrix, new Qubit[]{qubit1, qubit2}, 1.0);
    }
    
    /**
     * Creates a controlled Z gate.
     *
     * <p>The control qubit is stored as the only control and the target as the only target. The
     * 4x4 matrix is diagonal, with a value of {@code -1} for the basis state where both qubits are
     * one.</p>
     *
     * @param control the control qubit
     * @param target the target qubit
     * @return a gate named {@code "CZ"} with duration {@code 1.0}
     */
    public static MultiQubitGate createCZ(Qubit control, Qubit target) {
        ComplexMatrix matrix = new ComplexMatrix(4, 4);
        // |00><00| + |01><01| + |10><10| - |11><11|
        matrix.set(0, 0, Complex.ONE);
        matrix.set(1, 1, Complex.ONE);
        matrix.set(2, 2, Complex.ONE);
        matrix.set(3, 3, new Complex(-1, 0));
        return new MultiQubitGate("CZ", matrix, new Qubit[]{target}, new Qubit[]{control}, 1.0);
    }
    
    /**
     * Creates a Toffoli gate with two controls and one target.
     *
     * <p>The 8x8 matrix uses the order {@code [control1, control2, target]} and flips the target
     * amplitude only for the basis state where both controls are one.</p>
     *
     * @param control1 the first control qubit
     * @param control2 the second control qubit
     * @param target the target qubit
     * @return a gate named {@code "Toffoli"} with duration {@code 1.0}
     */
    public static MultiQubitGate createToffoli(Qubit control1, Qubit control2, Qubit target) {
        ComplexMatrix matrix = new ComplexMatrix(8, 8);
        // Basis order is [control1, control2, target]; only |111> is affected.
        for (int i = 0; i < 6; i++) {
            matrix.set(i, i, Complex.ONE);
        }
        matrix.set(6, 7, Complex.ONE);
        matrix.set(7, 6, Complex.ONE);
        return new MultiQubitGate("Toffoli", matrix, new Qubit[]{target}, 
                                 new Qubit[]{control1, control2}, 1.0);
    }
    
    /**
     * Creates a controlled phase gate.
     *
     * <p>The 4x4 matrix is diagonal and multiplies the amplitude of the basis state where both
     * qubits are one by {@code e^(i angle)}.</p>
     *
     * @param angle the phase angle in radians
     * @param control the control qubit
     * @param target the target qubit
     * @return a gate named {@code "CPHASE(angle)"} with duration {@code 1.0}
     */
    public static MultiQubitGate createCPHASE(double angle, Qubit control, Qubit target) {
        ComplexMatrix matrix = new ComplexMatrix(4, 4);
        Complex phase = Complex.fromPolar(1.0, angle);
        
        matrix.set(0, 0, Complex.ONE);
        matrix.set(1, 1, Complex.ONE);
        matrix.set(2, 2, Complex.ONE);
        matrix.set(3, 3, phase);
        return new MultiQubitGate("CPHASE(" + angle + ")", matrix, new Qubit[]{target}, 
                                 new Qubit[]{control}, 1.0);
    }
    
    /**
     * Creates a Z gate controlled by all supplied qubits.
     *
     * <p>Every supplied qubit is stored as a target qubit and no control qubits are set. The
     * matrix is the identity except for the last diagonal entry, which is {@code -1}.</p>
     *
     * @param qubits the qubits the gate acts on, at least two and at most ten
     * @return a gate named {@code "MCZ"} followed by the qubit count, with duration {@code 1.0}
     * @throws IllegalArgumentException if fewer than two qubits are supplied
     * @throws UnsupportedOperationException if more than ten qubits are supplied, because the
     *         matrix would have more than {@code 2^10} entries per dimension
     */
    public static MultiQubitGate createMultiControlledZ(Qubit... qubits) {
        if (qubits.length < 2) {
            throw new IllegalArgumentException("Multi-controlled Z requires at least 2 qubits");
        }
        if (qubits.length > 10) {
            throw new UnsupportedOperationException(
                "Multi-controlled Z above 10 qubits requires a 2^" + qubits.length + " matrix");
        }
        
        int dim = 1 << qubits.length;
        ComplexMatrix matrix = ComplexMatrix.identity(dim);
        matrix.set(dim - 1, dim - 1, new Complex(-1, 0));
        return new MultiQubitGate("MCZ" + qubits.length, matrix, qubits, 1.0);
    }
    
    /**
     * Creates a controlled SWAP gate.
     *
     * <p>The control qubit is stored as the only control, and {@code first} and {@code second} are
     * stored as targets in that order. The 8x8 matrix uses the order
     * {@code [control, first, second]} and exchanges the two basis states where the control is one
     * and the two targets differ.</p>
     *
     * @param control the control qubit
     * @param first the first qubit to swap
     * @param second the second qubit to swap
     * @return a gate named {@code "CSWAP"} with duration {@code 1.0}
     */
    public static MultiQubitGate createControlledSwap(Qubit control, Qubit first, Qubit second) {
        ComplexMatrix matrix = ComplexMatrix.identity(8);
        matrix.set(5, 5, Complex.ZERO);
        matrix.set(6, 6, Complex.ZERO);
        matrix.set(5, 6, Complex.ONE);
        matrix.set(6, 5, Complex.ONE);
        return new MultiQubitGate("CSWAP", matrix,
            new Qubit[]{first, second}, new Qubit[]{control}, 1.0);
    }
}