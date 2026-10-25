package com.quantum.simulator.operations;

import com.quantum.simulator.state.QuantumState;
import com.quantum.simulator.state.Qubit;
import com.quantum.simulator.state.StateVector;
import com.quantum.simulator.math.Complex;
import com.quantum.simulator.math.ComplexMatrix;

public class SingleQubitGate extends QuantumGate {
    private static final double DEFAULT_DURATION = 1.0;
    
    public SingleQubitGate(String name, ComplexMatrix matrix, Qubit targetQubit) {
        this(name, matrix, targetQubit, DEFAULT_DURATION);
    }
    
    public SingleQubitGate(String name, ComplexMatrix matrix, Qubit targetQubit, double duration) {
        super(name, matrix, new Qubit[]{targetQubit}, new Qubit[0], duration);
        
        if (matrix.rows() != 2 || matrix.cols() != 2) {
            throw new IllegalArgumentException("Single-qubit gate matrix must be 2x2");
        }
    } 
    
    @Override
    public QuantumState apply(QuantumState state) {
        // Single-qubit gate application should also be handled by the strategy
        throw new UnsupportedOperationException(
            "Gate application should be handled through SimulationStrategy. " +
            "Use context.applyOperation() or strategy.applyGate() instead."
        );
    }
    
    // Factory methods for common single-qubit gates
    public static SingleQubitGate createPauliX(Qubit target) {
        ComplexMatrix matrix = new ComplexMatrix(2, 2);
        matrix.set(0, 0, Complex.ZERO);
        matrix.set(0, 1, Complex.ONE);
        matrix.set(1, 0, Complex.ONE);
        matrix.set(1, 1, Complex.ZERO);
        return new SingleQubitGate("X", matrix, target);
    }
    
    public static SingleQubitGate createPauliY(Qubit target) {
        ComplexMatrix matrix = new ComplexMatrix(2, 2);
        matrix.set(0, 0, Complex.ZERO);
        matrix.set(0, 1, new Complex(0, -1));
        matrix.set(1, 0, new Complex(0, 1));
        matrix.set(1, 1, Complex.ZERO);
        return new SingleQubitGate("Y", matrix, target);
    }
    
    public static SingleQubitGate createPauliZ(Qubit target) {
        ComplexMatrix matrix = new ComplexMatrix(2, 2);
        matrix.set(0, 0, Complex.ONE);
        matrix.set(0, 1, Complex.ZERO);
        matrix.set(1, 0, Complex.ZERO);
        matrix.set(1, 1, new Complex(-1, 0));
        return new SingleQubitGate("Z", matrix, target);
    }
    
    public static SingleQubitGate createHadamard(Qubit target) {
        double factor = 1.0 / Math.sqrt(2);
        ComplexMatrix matrix = new ComplexMatrix(2, 2);
        matrix.set(0, 0, new Complex(factor, 0));
        matrix.set(0, 1, new Complex(factor, 0));
        matrix.set(1, 0, new Complex(factor, 0));
        matrix.set(1, 1, new Complex(-factor, 0));
        return new SingleQubitGate("H", matrix, target);
    }
    
    public static SingleQubitGate createPhase(double angle, Qubit target) {
        ComplexMatrix matrix = new ComplexMatrix(2, 2);
        matrix.set(0, 0, Complex.ONE);
        matrix.set(0, 1, Complex.ZERO);
        matrix.set(1, 0, Complex.ZERO);
        matrix.set(1, 1, Complex.fromPolar(1.0, angle));
        return new SingleQubitGate("P(" + angle + ")", matrix, target);
    }
    
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
    
    public static SingleQubitGate createS(Qubit target) {
        return createPhase(Math.PI / 2, target);
    }
    
    public static SingleQubitGate createT(Qubit target) {
        return createPhase(Math.PI / 4, target);
    }
}