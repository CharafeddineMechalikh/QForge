package com.quantum.simulator.operations;

import com.quantum.simulator.state.QuantumState;
import com.quantum.simulator.state.Qubit;

public interface QuantumOperation {
    QuantumState apply(QuantumState state);
    
    String getName();
    
    double getDuration();
    
    Qubit[] getTargetQubits();
    
    Qubit[] getControlQubits();
    
    boolean isUnitary();
    
    boolean isNoise();
    
    boolean isMeasurement();
    
    QuantumOperation inverse();
}