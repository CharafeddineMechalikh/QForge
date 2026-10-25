package com.quantum.simulator.strategy;

import com.quantum.simulator.state.QuantumState;
import com.quantum.simulator.state.StateVector;
import com.quantum.simulator.operations.QuantumGate;
import com.quantum.simulator.state.Qubit;
import com.quantum.simulator.noise.NoiseModel;
import com.quantum.simulator.measurement.MeasurementResult;
import com.quantum.simulator.measurement.Observable;
import com.quantum.simulator.math.Complex;
import com.quantum.simulator.math.ComplexMatrix;
import java.util.Arrays;
public class StateVectorStrategy implements SimulationStrategy {
    private static final double EPSILON = 1e-12;
    
    @Override
    public QuantumState createInitialState(int numQubits) {
        return StateVector.createZeroState(numQubits);
    }
    

    @Override
    public QuantumState applyNoise(QuantumState state, NoiseModel noise, Qubit[] targetQubits) {
        if (!(state instanceof StateVector)) {
            throw new IllegalArgumentException("StateVectorStrategy requires StateVector quantum state");
        }
        int[] targetIndices = Arrays.stream(targetQubits).mapToInt(Qubit::getIndex).toArray();
        return state.applyKrausOperators(noise.getKrausOperators(targetQubits.length), targetIndices);
    }
    
    @Override
    public MeasurementResult measure(QuantumState state, Qubit[] targetQubits, int shots) {
        if (!(state instanceof StateVector)) {
            throw new IllegalArgumentException("StateVectorStrategy requires StateVector quantum state");
        }
        return Observable.pauliZ(targetQubits).measureMultiple(state, shots);
    }
    
    @Override
    public QuantumState cloneState(QuantumState state) {
        if (!(state instanceof StateVector)) {
            throw new IllegalArgumentException("StateVectorStrategy requires StateVector quantum state");
        }
        
        StateVector stateVector = (StateVector) state;
        Complex[] amplitudes = stateVector.getAmplitudes();
        Complex[] clonedAmplitudes = new Complex[amplitudes.length];
        
        for (int i = 0; i < amplitudes.length; i++) {
            clonedAmplitudes[i] = new Complex(amplitudes[i]);
        }
        
        return new StateVector(clonedAmplitudes, stateVector.getNumQubits());
    }
    
    @Override
    public double calculateFidelity(QuantumState state1, QuantumState state2) {
        if (!(state1 instanceof StateVector) || !(state2 instanceof StateVector)) {
            throw new IllegalArgumentException("Both states must be StateVector instances");
        }
        
        StateVector sv1 = (StateVector) state1;
        StateVector sv2 = (StateVector) state2;
        
        if (sv1.getNumQubits() != sv2.getNumQubits()) {
            throw new IllegalArgumentException("States must have same number of qubits");
        }
        
        Complex innerProduct = Complex.ZERO;
        Complex[] amps1 = sv1.getAmplitudes();
        Complex[] amps2 = sv2.getAmplitudes();
        
        for (int i = 0; i < amps1.length; i++) {
            innerProduct = innerProduct.add(amps1[i].conjugate().multiply(amps2[i]));
        }
        
        double fidelity = innerProduct.absSquare();
        return Math.min(1.0, Math.max(0.0, fidelity)); // Clamp to [0, 1]
    }
    
    @Override
    public double calculateEntropy(QuantumState state) {
        if (!(state instanceof StateVector)) {
            throw new IllegalArgumentException("StateVectorStrategy requires StateVector quantum state");
        }
        
        StateVector stateVector = (StateVector) state;
        double entropy = 0.0;
        
        for (Complex amplitude : stateVector.getAmplitudes()) {
            double probability = amplitude.absSquare();
            if (probability > EPSILON) {
                entropy -= probability * Math.log(probability) / Math.log(2);
            }
        }
        
        return entropy;
    }
    
    @Override
    public boolean canSimulateNoise() {
        return true;
    }
    
    @Override
    public int getMaxSupportedQubits() {
        // One complex amplitude per basis state (16 bytes); 2^30 is ~16 GiB
        return 30;
    }
    
    @Override
    public String getStrategyName() {
        return "StateVectorSimulator";
    }
    
    @Override
    public void initialize() {
        // Nothing to initialize
    }
    
    @Override
    public void cleanup() {
        // Nothing to clean up
    }
    
    @Override
    public QuantumState applyGate(QuantumState state, QuantumGate gate, Qubit[] targetQubits) {
        if (!(state instanceof StateVector)) {
            throw new IllegalArgumentException("StateVectorStrategy requires StateVector quantum state");
        }
        
        StateVector stateVector = (StateVector) state;
        int[] qubitIndices = gate.getMatrixQubitIndices();
        Complex[] newAmplitudes = applyGateToStateVector(
            stateVector.getAmplitudes(),
            gate.getMatrix(),
            qubitIndices,
            stateVector.getNumQubits()
        );
        
        return new StateVector(newAmplitudes, stateVector.getNumQubits());
    }
    
    private Complex[] applyGateToStateVector(Complex[] amplitudes, ComplexMatrix gateMatrix, 
                                           int[] qubitIndices, int numQubits) {
        int targetSize = qubitIndices.length;
        int stateSize = amplitudes.length;
        
        Complex[] newAmplitudes = new Complex[stateSize];
        for (int i = 0; i < stateSize; i++) {
            newAmplitudes[i] = new Complex(0, 0);
        }
        
        for (int idx = 0; idx < stateSize; idx++) {
            int gateIn = 0;
            for (int t = 0; t < targetSize; t++) {
                int bit = (idx >> (numQubits - 1 - qubitIndices[t])) & 1;
                gateIn |= (bit << (targetSize - 1 - t));
            }
            
            for (int gateOut = 0; gateOut < (1 << targetSize); gateOut++) {
                int outIdx = idx;
                for (int t = 0; t < targetSize; t++) {
                    int oldBit = (gateIn >> (targetSize - 1 - t)) & 1;
                    int newBit = (gateOut >> (targetSize - 1 - t)) & 1;
                    if (oldBit != newBit) {
                        outIdx ^= (1 << (numQubits - 1 - qubitIndices[t]));
                    }
                }
                
                Complex matrixElement = gateMatrix.get(gateOut, gateIn);
                if (matrixElement.absSquare() > 0) {
                    newAmplitudes[outIdx] = newAmplitudes[outIdx]
                        .add(amplitudes[idx].multiply(matrixElement));
                }
            }
        }
        
        return newAmplitudes;
    }
    
}