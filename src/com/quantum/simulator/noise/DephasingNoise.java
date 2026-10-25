package com.quantum.simulator.noise;

import java.util.Locale;
import com.quantum.simulator.state.Qubit;
import com.quantum.simulator.math.ComplexMatrix;

/**
 * Dephasing, also called phase damping, noise on one or more qubits.
 *
 * <p>The single qubit channel has the two Kraus operators {@code sqrt(1 - p/2)} times the
 * identity and {@code sqrt(p/2)} times Pauli {@code Z}. It leaves populations unchanged and
 * shrinks the off diagonal coherence of each target qubit. At {@code p = 1} the two operators
 * carry equal weight and the coherence is fully removed; at {@code p = 0} only the identity
 * remains.</p>
 *
 * <p>For several qubits the channel is the independent product of the single qubit channel, so
 * the returned set contains {@code 2^n} operators for {@code n} targets, with the first target as
 * the leftmost tensor factor. That doubling per qubit is why the limit is eight targets, which
 * yields 256 operators.</p>
 */
public class DephasingNoise extends NoiseModel {
    
    /**
     * Creates a dephasing channel.
     *
     * @param probability the dephasing strength, between zero and one inclusive
     * @param targetQubits the target qubits; more than eight is rejected when the Kraus operators
     *        are built
     * @throws IllegalArgumentException if {@code probability} is outside the closed interval from
     *         zero to one
     */
    public DephasingNoise(double probability, Qubit... targetQubits) {
        super("DephasingNoise", probability, targetQubits);
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
        
        ComplexMatrix[] singleQubit = {
            ComplexMatrix.identity(2).scale(Math.sqrt(1.0 - probability / 2.0)),
            ComplexMatrix.pauliZ().scale(Math.sqrt(probability / 2.0))
        };
        
        return independentKraus(singleQubit, numQubits);
    }
    
    /**
     * Returns a copy of this channel with a different dephasing strength.
     *
     * @param newProbability the new strength, between zero and one inclusive
     * @return a new dephasing channel on the same targets
     * @throws IllegalArgumentException if {@code newProbability} is outside the allowed range
     */
    @Override
    public NoiseModel withProbability(double newProbability) {
        return new DephasingNoise(newProbability, targetQubits);
    }
    
    /**
     * Returns a copy of this channel on different target qubits.
     *
     * @param newTargetQubits the new target qubits
     * @return a new dephasing channel with the same strength
     * @throws NullPointerException if {@code newTargetQubits} is {@code null}
     */
    @Override
    public NoiseModel withTargetQubits(Qubit[] newTargetQubits) {
        return new DephasingNoise(probability, newTargetQubits);
    }
    
    /**
     * Creates a single qubit dephasing channel.
     *
     * @param probability the dephasing strength, between zero and one inclusive
     * @param target the target qubit
     * @return a new dephasing channel
     * @throws IllegalArgumentException if {@code probability} is outside the allowed range
     */
    public static DephasingNoise createSingleQubit(double probability, Qubit target) {
        return new DephasingNoise(probability, target);
    }
}