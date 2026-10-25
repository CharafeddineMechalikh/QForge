package com.mechalikh.qforge.strategy;

import com.mechalikh.qforge.state.QuantumState;
import com.mechalikh.qforge.state.DensityMatrix;
import com.mechalikh.qforge.operations.QuantumGate;
import com.mechalikh.qforge.state.Qubit;
import com.mechalikh.qforge.noise.NoiseModel;
import com.mechalikh.qforge.measurement.MeasurementResult;
import com.mechalikh.qforge.measurement.Observable;
import com.mechalikh.qforge.math.Complex;
import com.mechalikh.qforge.math.ComplexMatrix;
import com.mechalikh.qforge.math.MatrixOperations;
import java.util.Arrays;

/**
 * Simulation strategy that represents a mixed state as a {@link DensityMatrix}.
 *
 * <p>Gates and noise are applied as Kraus maps that update the density matrix
 * deterministically, without trajectory sampling. Measurement samples the Pauli Z observable
 * on the target qubits.
 */
public class DensityMatrixStrategy implements SimulationStrategy {
    private static final double EPSILON = 1e-12;
    
    /**
     * Creates the all-zero state {@code |0...0><0...0|} as a density matrix.
     *
     * @param numQubits number of qubits in the state
     * @return a pure density matrix with unit population in the zero basis state
     * @throws IllegalArgumentException if the density matrix constructor rejects the qubit
     *         count or the constructed matrix fails density matrix validation
     */
    @Override
    public QuantumState createInitialState(int numQubits) {
        return DensityMatrix.createZeroState(numQubits);
    }
    
    /**
     * Applies a gate to a density matrix.
     *
     * <p>The gate matrix is used as a single Kraus operator at the positions reported by
     * {@link QuantumGate#getMatrixQubitIndices()}; the {@code targetQubits} argument is not
     * consulted. The state implementation computes {@code K rho K^dagger}, which for a
     * unitary gate is a deterministic conjugation with no sampling.
     *
     * @param state state to transform
     * @param gate gate whose matrix and qubit indices are used
     * @param targetQubits target qubits reported by the caller, not used
     * @return a new density matrix with the gate applied
     * @throws IllegalArgumentException if {@code state} is not a {@link DensityMatrix}
     */
    @Override
    public QuantumState applyGate(QuantumState state, QuantumGate gate, Qubit[] targetQubits) {
        if (!(state instanceof DensityMatrix)) {
            throw new IllegalArgumentException("DensityMatrixStrategy requires DensityMatrix quantum state");
        }
        
        return state.applyKrausOperators(
            new ComplexMatrix[]{gate.getMatrix()}, gate.getMatrixQubitIndices());
    }
    
    /**
     * Applies a noise channel to a density matrix.
     *
     * <p>The qubit indices are read from {@code targetQubits}, and the Kraus operators are
     * requested for the number of target qubits. The state implementation sums every term
     * {@code K rho K^dagger}, so the result is the full channel output with no branch
     * selection.
     *
     * @param state state to transform
     * @param noise noise model to apply
     * @param targetQubits qubits affected by the noise
     * @return a new density matrix with the channel applied
     * @throws IllegalArgumentException if {@code state} is not a {@link DensityMatrix}
     */
    @Override
    public QuantumState applyNoise(QuantumState state, NoiseModel noise, Qubit[] targetQubits) {
        if (!(state instanceof DensityMatrix)) {
            throw new IllegalArgumentException("DensityMatrixStrategy requires DensityMatrix quantum state");
        }
        
        int[] targetIndices = Arrays.stream(targetQubits).mapToInt(Qubit::getIndex).toArray();
        return state.applyKrausOperators(noise.getKrausOperators(targetQubits.length), targetIndices);
    }
    
    /**
     * Measures the Pauli Z observable on the target qubits.
     *
     * <p>The observable is built as the tensor product of single-qubit Pauli Z operators in
     * the order of {@code targetQubits}, and {@code shots} samples are drawn from the outcome
     * probabilities. The state is not collapsed.
     *
     * @param state state to measure
     * @param targetQubits qubits to measure
     * @param shots number of samples
     * @return the measurement result
     * @throws IllegalArgumentException if {@code state} is not a {@link DensityMatrix}
     */
    @Override
    public MeasurementResult measure(QuantumState state, Qubit[] targetQubits, int shots) {
        if (!(state instanceof DensityMatrix)) {
            throw new IllegalArgumentException("DensityMatrixStrategy requires DensityMatrix quantum state");
        }
        return Observable.pauliZ(targetQubits).measureMultiple(state, shots);
    }
    
    /**
     * Creates a deep copy of a density matrix.
     *
     * <p>The matrix is copied and a new {@link DensityMatrix} with the same number of qubits
     * is created, which also revalidates Hermiticity and unit trace.
     *
     * @param state state to copy
     * @return an independent copy
     * @throws IllegalArgumentException if {@code state} is not a {@link DensityMatrix}
     */
    @Override
    public QuantumState cloneState(QuantumState state) {
        if (!(state instanceof DensityMatrix)) {
            throw new IllegalArgumentException("DensityMatrixStrategy requires DensityMatrix quantum state");
        }
        
        DensityMatrix densityMatrix = (DensityMatrix) state;
        return new DensityMatrix(densityMatrix.getMatrix().copy(), densityMatrix.getNumQubits());
    }
    
    /**
     * Computes the fidelity between two density matrices.
     *
     * <p>When both states report themselves as pure, they are converted to state vectors and
     * the squared overlap is computed by {@link StateVectorStrategy}. Otherwise the Uhlmann
     * fidelity is computed by {@link DensityMatrix#fidelity(DensityMatrix, DensityMatrix)}.
     *
     * @param state1 first state
     * @param state2 second state
     * @return the fidelity
     * @throws IllegalArgumentException if either state is not a {@link DensityMatrix} or the
     *         two states have different qubit counts
     */
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
    
    /**
     * Computes the von Neumann entropy of a density matrix, in bits.
     *
     * <p>The entropy is {@code -sum(lambda * log2(lambda))} over the eigenvalues returned by
     * {@link MatrixOperations#eigenvalues(ComplexMatrix)}; eigenvalues at or below
     * {@code 1e-12} are skipped.
     *
     * @param state state to inspect
     * @return the entropy in bits
     * @throws IllegalArgumentException if {@code state} is not a {@link DensityMatrix}
     */
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
    
    /**
     * Reports that this strategy supports noise.
     *
     * @return always {@code true}
     */
    @Override
    public boolean canSimulateNoise() {
        return true;
    }
    
    /**
     * Reports a practical qubit ceiling of 12.
     *
     * <p>The value reflects a square matrix of {@code 4096} by {@code 4096} complex entries
     * and the temporary copies needed for gate application. It is informational and is not
     * enforced by this class.
     *
     * @return the reported limit, {@code 12}
     */
    @Override
    public int getMaxSupportedQubits() {
        // A 12-qubit density matrix has 4096^2 entries at 16 bytes each (~256 MiB)
        // and gate application needs temporary copies, so 12 is a practical ceiling.
        return 12;
    }
    
    /**
     * Returns the strategy name.
     *
     * @return {@code "DensityMatrixSimulator"}
     */
    @Override
    public String getStrategyName() {
        return "DensityMatrixSimulator";
    }
    
    /**
     * Performs no initialization; this strategy has no internal state.
     */
    @Override
    public void initialize() {
        // Nothing to initialize
    }
    
    /**
     * Performs no cleanup; this strategy has no internal state.
     */
    @Override
    public void cleanup() {
        // Nothing to clean up
    }
}