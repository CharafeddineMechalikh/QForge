package com.quantum.simulator.operations;

import com.quantum.simulator.state.QuantumState;
import com.quantum.simulator.state.Qubit;
import com.quantum.simulator.math.Complex;
import com.quantum.simulator.math.ComplexMatrix;

public class MultiQubitGate extends QuantumGate {
    
	
	public MultiQubitGate(String name, ComplexMatrix matrix, Qubit[] targetQubits) {
        this(name, matrix, targetQubits, new Qubit[0], 1.0);
    }
    
    public MultiQubitGate(String name, ComplexMatrix matrix, Qubit[] targetQubits, double duration) {
        this(name, matrix, targetQubits, new Qubit[0], duration);
    }
    
    public MultiQubitGate(String name, ComplexMatrix matrix, 
                         Qubit[] targetQubits, Qubit[] controlQubits, double duration) {
        super(name, matrix, targetQubits, controlQubits, duration);
        
        if (targetQubits.length < 2 && controlQubits.length == 0) {
            throw new IllegalArgumentException("Multi-qubit gate must act on at least 2 qubits");
        }
    }
    
    @Override
    public QuantumState apply(QuantumState state) {
        // Multi-qubit gate application should be handled by the simulation strategy
        // This method should not be called directly
        throw new UnsupportedOperationException(
            "Gate application should be handled through SimulationStrategy. " +
            "Use context.applyOperation() or strategy.applyGate() instead."
        );
    }
    
    
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
    
    public static MultiQubitGate createSWAP(Qubit qubit1, Qubit qubit2) {
        ComplexMatrix matrix = new ComplexMatrix(4, 4);
        // |00><00| + |01><10| + |10><01| + |11><11|
        matrix.set(0, 0, Complex.ONE);
        matrix.set(1, 2, Complex.ONE);
        matrix.set(2, 1, Complex.ONE);
        matrix.set(3, 3, Complex.ONE);
        return new MultiQubitGate("SWAP", matrix, new Qubit[]{qubit1, qubit2}, 1.0);
    }
    
    public static MultiQubitGate createCZ(Qubit control, Qubit target) {
        ComplexMatrix matrix = new ComplexMatrix(4, 4);
        // |00><00| + |01><01| + |10><10| - |11><11|
        matrix.set(0, 0, Complex.ONE);
        matrix.set(1, 1, Complex.ONE);
        matrix.set(2, 2, Complex.ONE);
        matrix.set(3, 3, new Complex(-1, 0));
        return new MultiQubitGate("CZ", matrix, new Qubit[]{target}, new Qubit[]{control}, 1.0);
    }
    
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