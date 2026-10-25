package com.quantum.simulator.strategy;

import com.quantum.simulator.state.QuantumState;
import com.quantum.simulator.state.DensityMatrix;
import com.quantum.simulator.operations.QuantumGate;
import com.quantum.simulator.state.Qubit;
import com.quantum.simulator.noise.NoiseModel;
import com.quantum.simulator.measurement.MeasurementResult;
import com.quantum.simulator.measurement.Observable;
import com.quantum.simulator.math.Complex;
import com.quantum.simulator.math.ComplexMatrix;
import com.quantum.simulator.math.MatrixOperations;
import java.util.Arrays;

public class DensityMatrixStrategy implements SimulationStrategy {
    private static final double EPSILON = 1e-12;
    
    @Override
    public QuantumState createInitialState(int numQubits) {
        return DensityMatrix.createZeroState(numQubits);
    }
    
    @Override
    public QuantumState applyGate(QuantumState state, QuantumGate gate, Qubit[] targetQubits) {
        if (!(state instanceof DensityMatrix)) {
            throw new IllegalArgumentException("DensityMatrixStrategy requires DensityMatrix quantum state");
        }
        
        return state.applyKrausOperators(
            new ComplexMatrix[]{gate.getMatrix()}, gate.getMatrixQubitIndices());
    }
    
    @Override
    public QuantumState applyNoise(QuantumState state, NoiseModel noise, Qubit[] targetQubits) {
        if (!(state instanceof DensityMatrix)) {
            throw new IllegalArgumentException("DensityMatrixStrategy requires DensityMatrix quantum state");
        }
        
        int[] targetIndices = Arrays.stream(targetQubits).mapToInt(Qubit::getIndex).toArray();
        return state.applyKrausOperators(noise.getKrausOperators(targetQubits.length), targetIndices);
    }
    
    @Override
    public MeasurementResult measure(QuantumState state, Qubit[] targetQubits, int shots) {
        if (!(state instanceof DensityMatrix)) {
            throw new IllegalArgumentException("DensityMatrixStrategy requires DensityMatrix quantum state");
        }
        return Observable.pauliZ(targetQubits).measureMultiple(state, shots);
    }
    
    @Override
    public QuantumState cloneState(QuantumState state) {
        if (!(state instanceof DensityMatrix)) {
            throw new IllegalArgumentException("DensityMatrixStrategy requires DensityMatrix quantum state");
        }
        
        DensityMatrix densityMatrix = (DensityMatrix) state;
        return new DensityMatrix(densityMatrix.getMatrix().copy(), densityMatrix.getNumQubits());
    }
    
    @Override
    public double calculateFidelity(QuantumState state1, QuantumState state2) {
        if (!(state1 instanceof DensityMatrix) || !(state2 instanceof DensityMatrix)) {
            throw new IllegalArgumentException("Both states must be DensityMatrix instances");
        }
        
        DensityMatrix dm1 = (DensityMatrix) state1;
        DensityMatrix dm2 = (DensityMatrix) state2;
        
        if (dm1.getNumQubits() != dm2.getNumQubits()) {
            throw new IllegalArgumentException("States must have same number of qubits");
        }
        
        // Fidelity F(ρ, σ) = (tr √(√ρ σ √ρ))²
        // For simplicity, use Uhlmann's theorem when both are pure
        if (dm1.isPure() && dm2.isPure()) {
            // Convert to state vectors and compute overlap
            StateVectorStrategy svStrategy = new StateVectorStrategy();
            QuantumState sv1 = dm1.toStateVector();
            QuantumState sv2 = dm2.toStateVector();
            return svStrategy.calculateFidelity(sv1, sv2);
        }
        
        // General case - Uhlmann fidelity F = ||sqrt(rho1) sqrt(rho2)||_1^2
        return DensityMatrix.fidelity(dm1, dm2);
    }
    
    @Override
    public double calculateEntropy(QuantumState state) {
        if (!(state instanceof DensityMatrix)) {
            throw new IllegalArgumentException("DensityMatrixStrategy requires DensityMatrix quantum state");
        }
        
        DensityMatrix densityMatrix = (DensityMatrix) state;
        
        // Compute eigenvalues
        ComplexMatrix rho = densityMatrix.getMatrix();
        double[] eigenvalues = MatrixOperations.eigenvalues(rho);
        
        // Von Neumann entropy: S = -Σ λ_i log₂ λ_i
        double entropy = 0.0;
        for (double lambda : eigenvalues) {
            if (lambda > EPSILON) {
                entropy -= lambda * Math.log(lambda) / Math.log(2);
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
        // A 12-qubit density matrix has 4096^2 entries at 16 bytes each (~256 MiB)
        // and gate application needs temporary copies, so 12 is a practical ceiling.
        return 12;
    }
    
    @Override
    public String getStrategyName() {
        return "DensityMatrixSimulator";
    }
    
    @Override
    public void initialize() {
        // Nothing to initialize
    }
    
    @Override
    public void cleanup() {
        // Nothing to clean up
    }
}