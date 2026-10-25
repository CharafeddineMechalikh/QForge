package com.quantum.simulator.noise;

import java.util.Locale;
import com.quantum.simulator.state.Qubit;
import com.quantum.simulator.math.Complex;
import com.quantum.simulator.math.ComplexMatrix;

/**
 * Amplitude damping noise, modelling relaxation from the excited state to the ground state.
 *
 * <p>The single qubit channel has the two Kraus operators
 * {@code K0 = |0><0| + sqrt(1 - gamma)|1><1|} and {@code K1 = sqrt(gamma)|0><1|}. The second
 * operator moves population from {@code |1>} to {@code |0>} with weight {@code gamma}, so
 * {@code gamma} is the decay probability of an excited qubit. At {@code gamma = 0} the second
 * operator is the zero matrix and the state is unchanged.</p>
 *
 * <p>For several qubits the channel is the independent product of the single qubit channel, so
 * the returned set contains {@code 2^n} operators for {@code n} targets, with the first target as
 * the leftmost tensor factor. The limit is eight targets, which yields 256 operators.</p>
 *
 * <p>The decay parameter is also stored as the model probability, so
 * {@link #getGamma()} and {@link #getProbability()} always report the same value.</p>
 */
public class AmplitudeDampingNoise extends NoiseModel {
    private final double gamma; // Damping parameter
    
    /**
     * Creates an amplitude damping channel.
     *
     * <p>The parameter is passed to the superclass as the model probability and is also validated
     * again here.</p>
     *
     * @param gamma the decay probability, between zero and one inclusive
     * @param targetQubits the target qubits; more than eight is rejected when the Kraus operators
     *        are built
     * @throws IllegalArgumentException if {@code gamma} is outside the closed interval from zero
     *         to one
     */
    public AmplitudeDampingNoise(double gamma, Qubit... targetQubits) {
        super("AmplitudeDampingNoise", gamma, targetQubits); // Using gamma as probability for compatibility
        if (gamma < 0.0 || gamma > 1.0) {
            throw new IllegalArgumentException("Damping parameter gamma must be between 0 and 1");
        }
        this.gamma = gamma;
    }
    
    /**
     * Builds the Kraus operators for this channel.
     *
     * <p>The two single qubit operators are combined with
     * {@link NoiseModel#independentKraus(ComplexMatrix[], int)}, which produces every tensor
     * product across the target qubits. The number of qubits must match the target count.</p>
     *
     * @param numQubits the number of qubits the operators act on, which must equal the target
     *        count
     * @return the {@code 2^n} Kraus operators
     * @throws IllegalArgumentException if {@code numQubits} differs from the target count
     * @throws UnsupportedOperationException if {@code numQubits} is greater than eight
     */
    @Override
    public ComplexMatrix[] getKrausOperators(int numQubits) {
        if (numQubits != targetQubits.length) {
            throw new IllegalArgumentException(
                String.format(Locale.ROOT, "Expected %d qubits, got %d", targetQubits.length, numQubits)
            );
        }
        
        ComplexMatrix[] singleQubit = new ComplexMatrix[2];
        
        // K0 = |0><0| + √(1-γ)|1><1|
        singleQubit[0] = new ComplexMatrix(2, 2);
        singleQubit[0].set(0, 0, Complex.ONE);
        singleQubit[0].set(1, 1, new Complex(Math.sqrt(1.0 - gamma), 0));
        
        // K1 = √γ|0><1|
        singleQubit[1] = new ComplexMatrix(2, 2);
        singleQubit[1].set(0, 1, new Complex(Math.sqrt(gamma), 0));
        
        return independentKraus(singleQubit, numQubits);
    }
    
    /**
     * Returns a copy of this channel with a different decay parameter.
     *
     * @param newProbability the new decay probability, between zero and one inclusive
     * @return a new amplitude damping channel on the same targets
     * @throws IllegalArgumentException if {@code newProbability} is outside the allowed range
     */
    @Override
    public NoiseModel withProbability(double newProbability) {
        return new AmplitudeDampingNoise(newProbability, targetQubits);
    }
    
    /**
     * Returns a copy of this channel on different target qubits.
     *
     * @param newTargetQubits the new target qubits
     * @return a new amplitude damping channel with the same decay parameter
     * @throws NullPointerException if {@code newTargetQubits} is {@code null}
     */
    @Override
    public NoiseModel withTargetQubits(Qubit[] newTargetQubits) {
        return new AmplitudeDampingNoise(gamma, newTargetQubits);
    }
    
    /**
     * Returns the decay parameter.
     *
     * @return the decay probability, equal to {@link #getProbability()}
     */
    public double getGamma() {
        return gamma;
    }
    
    /**
     * Creates a single qubit amplitude damping channel.
     *
     * @param gamma the decay probability, between zero and one inclusive
     * @param target the target qubit
     * @return a new amplitude damping channel
     * @throws IllegalArgumentException if {@code gamma} is outside the allowed range
     */
    public static AmplitudeDampingNoise create(double gamma, Qubit target) {
        return new AmplitudeDampingNoise(gamma, target);
    }
}