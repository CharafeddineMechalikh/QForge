package com.quantum.simulator.strategy;

import com.quantum.simulator.state.QuantumState;
import com.quantum.simulator.operations.QuantumGate;
import com.quantum.simulator.state.Qubit;
import com.quantum.simulator.noise.NoiseModel;
import com.quantum.simulator.measurement.MeasurementResult;

public interface SimulationStrategy {
    QuantumState createInitialState(int numQubits);
    
    QuantumState applyGate(QuantumState state, QuantumGate gate, Qubit[] targetQubits);
    
    QuantumState applyNoise(QuantumState state, NoiseModel noise, Qubit[] targetQubits);
    
    MeasurementResult measure(QuantumState state, Qubit[] targetQubits, int shots);
    
    QuantumState cloneState(QuantumState state);
    
    double calculateFidelity(QuantumState state1, QuantumState state2);
    
    double calculateEntropy(QuantumState state);
    
    boolean canSimulateNoise();
    
    int getMaxSupportedQubits();
    
    String getStrategyName();
    
    void initialize();
    
    void cleanup();
}