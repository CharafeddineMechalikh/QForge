package com.quantum.simulator.noise;

import java.util.Locale;
import com.quantum.simulator.state.Qubit;
import com.quantum.simulator.math.ComplexMatrix;
import java.util.ArrayList;
import java.util.List;

/**
 * Depolarizing noise on one or more qubits, built from Pauli strings.
 *
 * <p>For {@code n} target qubits the channel mixes the identity with all {@code 4^n - 1} non
 * identity Pauli strings. The Kraus operators are {@code sqrt(1 - p)} times the identity and
 * {@code sqrt(p / (4^n - 1))} times each non identity string, so with probability {@code 1 - p}
 * the state is unchanged and with probability {@code p} one of the non identity strings is
 * applied uniformly at random. Each string is a tensor product of {@code I}, {@code X}, {@code Y}
 * or {@code Z}, so every operator is unitary and the set is a valid Kraus decomposition.</p>
 *
 * <p>The number of operators grows as {@code 4^n}, so multi qubit depolarizing noise is limited
 * to four target qubits. Dephasing and amplitude damping use a different construction with two
 * operators per qubit and are limited to eight.</p>
 */
public class DepolarizingNoise extends NoiseModel {
    
    /**
     * Creates a depolarizing channel.
     *
     * @param probability the total probability of a non identity Pauli string, between zero and
     *        one inclusive
     * @param targetQubits the target qubits; more than four is rejected when the Kraus operators
     *        are built
     * @throws IllegalArgumentException if {@code probability} is outside the closed interval from
     *         zero to one
     */
    public DepolarizingNoise(double probability, Qubit... targetQubits) {
        super("DepolarizingNoise", probability, targetQubits);
    }
    
    /**
     * Builds the Kraus operators for this channel.
     *
     * <p>The list holds the scaled identity first, followed by one scaled Pauli string for every
     * code from {@code 1} to {@code 4^n - 1}. In a code, qubit {@code q} is described by bits
     * {@code 2q} and {@code 2q + 1}, which select {@code I}, {@code X}, {@code Y} or {@code Z}
     * for the values zero to three. When {@code probability} is zero only the identity term
     * remains.</p>
     *
     * @param numQubits the number of qubits the operators act on, which must equal the target
     *        count
     * @return the {@code 4^n} Kraus operators
     * @throws IllegalArgumentException if {@code numQubits} differs from the target count
     * @throws UnsupportedOperationException if {@code numQubits} is greater than four
     */
    @Override
    public ComplexMatrix[] getKrausOperators(int numQubits) {
        if (numQubits != targetQubits.length) {
            throw new IllegalArgumentException(
                String.format(Locale.ROOT, "Expected %d qubits, got %d", targetQubits.length, numQubits)
            );
        }
        if (numQubits > 4) {
            throw new UnsupportedOperationException(
                "Depolarizing noise on more than 4 qubits requires "
                    + ((1 << (2 * numQubits)) - 1) + " Kraus operators");
        }
        
        int dim = 1 << numQubits;
        List<ComplexMatrix> krausOperators = new ArrayList<>();
        
        krausOperators.add(ComplexMatrix.identity(dim).scale(Math.sqrt(1.0 - probability)));
        
        int numPauliStrings = (1 << (2 * numQubits)) - 1;
        if (numPauliStrings > 0 && probability > 0.0) {
            double weight = Math.sqrt(probability / numPauliStrings);
            for (int code = 1; code < (1 << (2 * numQubits)); code++) {
                ComplexMatrix pauliString = ComplexMatrix.identity(1);
                for (int q = 0; q < numQubits; q++) {
                    pauliString = pauliString.tensorProduct(pauliMatrix((code >> (2 * q)) & 3));
                }
                krausOperators.add(pauliString.scale(weight));
            }
        }
        
        return krausOperators.toArray(new ComplexMatrix[0]);
    }
    
    private static ComplexMatrix pauliMatrix(int index) {
        switch (index) {
            case 0:
                return ComplexMatrix.identity(2);
            case 1:
                return ComplexMatrix.pauliX();
            case 2:
                return ComplexMatrix.pauliY();
            case 3:
                return ComplexMatrix.pauliZ();
            default:
                throw new IllegalArgumentException("Unknown Pauli index: " + index);
        }
    }
    
    /**
     * Returns a copy of this channel with a different noise strength.
     *
     * @param newProbability the new total probability, between zero and one inclusive
     * @return a new depolarizing channel on the same targets
     * @throws IllegalArgumentException if {@code newProbability} is outside the allowed range
     */
    @Override
    public NoiseModel withProbability(double newProbability) {
        return new DepolarizingNoise(newProbability, targetQubits);
    }
    
    /**
     * Returns a copy of this channel on different target qubits.
     *
     * @param newTargetQubits the new target qubits
     * @return a new depolarizing channel with the same probability
     * @throws NullPointerException if {@code newTargetQubits} is {@code null}
     */
    @Override
    public NoiseModel withTargetQubits(Qubit[] newTargetQubits) {
        return new DepolarizingNoise(probability, newTargetQubits);
    }
    
    /**
     * Creates a single qubit depolarizing channel.
     *
     * @param probability the total probability of a non identity Pauli operator
     * @param target the target qubit
     * @return a new depolarizing channel
     * @throws IllegalArgumentException if {@code probability} is outside the allowed range
     */
    public static DepolarizingNoise createSingleQubit(double probability, Qubit target) {
        return new DepolarizingNoise(probability, target);
    }
    
    /**
     * Creates a multi qubit depolarizing channel.
     *
     * @param probability the total probability of a non identity Pauli string
     * @param targets the target qubits; at most four can be applied
     * @return a new depolarizing channel
     * @throws IllegalArgumentException if {@code probability} is outside the allowed range
     */
    public static DepolarizingNoise createMultiQubit(double probability, Qubit... targets) {
        return new DepolarizingNoise(probability, targets);
    }
}