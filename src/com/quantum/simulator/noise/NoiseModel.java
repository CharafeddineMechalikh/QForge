package com.quantum.simulator.noise;

import java.util.Locale;
import com.quantum.simulator.operations.QuantumOperation;
import com.quantum.simulator.state.QuantumState;
import com.quantum.simulator.state.Qubit;
import com.quantum.simulator.math.ComplexMatrix;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Base class for noise channels described by a Kraus operator decomposition.
 *
 * <p>A model stores a name, a probability in the closed interval from zero to one, and the target
 * qubits it acts on. Subclasses supply the Kraus operators for a given number of target qubits,
 * which must be a complete set satisfying {@code sum(K_i^dagger K_i) = I}. The operators returned
 * for {@code n} targets are {@code 2^n} by {@code 2^n} matrices.</p>
 *
 * <p>{@link #apply(QuantumState)} embeds those operators on the target qubits and delegates to
 * {@link QuantumState#applyKrausOperators(ComplexMatrix[], int[])}. How the channel is realized
 * then depends on the backend: a {@link com.quantum.simulator.state.StateVector} draws a single
 * Kraus operator at random with probability equal to its squared norm and renormalizes the
 * resulting pure state, forming one stochastic trajectory, while a
 * {@link com.quantum.simulator.state.DensityMatrix} applies the sum over all operators and stays
 * deterministic. A model with no target qubits cannot be applied and is rejected.</p>
 *
 * <p>Instances are immutable in the sense that fields are final and arrays are copied on the way
 * in and out. The subclasses' {@code withProbability} and {@code withTargetQubits} methods return
 * new instances instead of changing the receiver.</p>
 */
public abstract class NoiseModel implements QuantumOperation {
    /** The display name of this model, also used for equality and hashing. */
    protected final String name;
    /** The noise strength, in the closed interval from zero to one. */
    protected final double probability;
    /** The target qubits, copied from the constructor argument. */
    protected final Qubit[] targetQubits;
    
    /**
     * Creates a noise model.
     *
     * @param name the display name
     * @param probability the noise strength, between zero and one inclusive
     * @param targetQubits the target qubits, possibly empty for an unassigned global model
     * @throws IllegalArgumentException if {@code probability} is below zero or above one
     * @throws NullPointerException if {@code targetQubits} is {@code null}
     */
    protected NoiseModel(String name, double probability, Qubit[] targetQubits) {
        if (probability < 0.0 || probability > 1.0) {
            throw new IllegalArgumentException("Noise probability must be between 0 and 1");
        }
        
        this.name = name;
        this.probability = probability;
        this.targetQubits = targetQubits.clone();
    }
    
    /**
     * Applies this noise channel to the state.
     *
     * <p>The target qubits are converted to indices and the operators from
     * {@link #getKrausOperators(int)} are applied to exactly those qubits. On a
     * {@link com.quantum.simulator.state.StateVector} one operator is selected stochastically,
     * while on a {@link com.quantum.simulator.state.DensityMatrix} the full sum is applied.</p>
     *
     * @param state the state to disturb
     * @return the state after the channel
     * @throws IllegalStateException if this model has no target qubits
     * @throws IllegalArgumentException if the operators returned by the subclass do not match the
     *         number of targets
     */
    @Override
    public QuantumState apply(QuantumState state) {
        if (targetQubits.length == 0) {
            throw new IllegalStateException(
                "Noise model '" + name + "' has no target qubits; assign targets before applying");
        }
        int[] targets = new int[targetQubits.length];
        for (int i = 0; i < targetQubits.length; i++) {
            targets[i] = targetQubits[i].getIndex();
        }
        return state.applyKrausOperators(getKrausOperators(targetQubits.length), targets);
    }
    
    /**
     * Builds the independent product channel over several qubits.
     *
     * <p>The returned list contains every tensor product of one single qubit operator per qubit,
     * so a single qubit set of size {@code k} produces {@code k^n} operators for {@code n} qubits.
     * Operators are generated with the first qubit as the leftmost factor and the last qubit
     * varying fastest, and the identity on one qubit is used as the seed.</p>
     *
     * <p>The qubit limit is a guard against the multiplicative growth of the list: with the two
     * operator channels in this package, eight qubits already need 256 operators.</p>
     *
     * @param singleQubitKraus the Kraus operators for one qubit, in a fixed order
     * @param numQubits the number of qubits to cover, not larger than eight
     * @return all tensor products, each of size {@code 2^n} by {@code 2^n}
     * @throws UnsupportedOperationException if {@code numQubits} is greater than eight
     * @throws NullPointerException if {@code singleQubitKraus} is {@code null}
     */
    protected static ComplexMatrix[] independentKraus(ComplexMatrix[] singleQubitKraus, int numQubits) {
        if (numQubits > 8) {
            throw new UnsupportedOperationException(
                "Independent noise on more than 8 qubits produces too many Kraus operators");
        }
        List<ComplexMatrix> accumulated = new ArrayList<>();
        accumulated.add(ComplexMatrix.identity(1));
        
        for (int q = 0; q < numQubits; q++) {
            List<ComplexMatrix> next = new ArrayList<>();
            for (ComplexMatrix existing : accumulated) {
                for (ComplexMatrix single : singleQubitKraus) {
                    next.add(existing.tensorProduct(single));
                }
            }
            accumulated = next;
        }
        
        return accumulated.toArray(new ComplexMatrix[0]);
    }
    
    /**
     * Returns the model name.
     *
     * @return the name supplied at construction
     */
    @Override
    public String getName() {
        return name;
    }
    
    /**
     * Returns the duration of the operation.
     *
     * <p>Noise application is modelled as instantaneous.</p>
     *
     * @return always {@code 0.0}
     */
    @Override
    public double getDuration() {
        return 0.0; // Noise application is instantaneous
    }
    
    /**
     * Returns the target qubits.
     *
     * @return a copy of the target qubit array, which may be empty
     */
    @Override
    public Qubit[] getTargetQubits() {
        return targetQubits.clone();
    }
    
    /**
     * Returns the control qubits.
     *
     * <p>Noise channels have no control qubits.</p>
     *
     * @return a new empty array
     */
    @Override
    public Qubit[] getControlQubits() {
        return new Qubit[0]; // Noise doesn't have control qubits
    }
    
    /**
     * Reports whether the operation is unitary.
     *
     * @return always {@code false}
     */
    @Override
    public boolean isUnitary() {
        return false;
    }
    
    /**
     * Reports whether the operation represents noise.
     *
     * @return always {@code true}
     */
    @Override
    public boolean isNoise() {
        return true;
    }
    
    /**
     * Reports whether the operation represents a measurement.
     *
     * @return always {@code false}
     */
    @Override
    public boolean isMeasurement() {
        return false;
    }
    
    /**
     * Returns the inverse operation.
     *
     * <p>Noise channels are not invertible, so this method always fails.</p>
     *
     * @return never returns
     * @throws UnsupportedOperationException always
     */
    @Override
    public QuantumOperation inverse() {
        throw new UnsupportedOperationException("Noise models don't have inverses");
    }
    
    /**
     * Returns the configured noise strength.
     *
     * @return the probability, between zero and one inclusive
     */
    public double getProbability() {
        return probability;
    }
    
    /**
     * Returns the Kraus operators of this channel for a given number of target qubits.
     *
     * <p>Implementations must return operators of size {@code 2^n} by {@code 2^n} when asked for
     * {@code n} qubits, and must reject a count that differs from their own target count.</p>
     *
     * @param numQubits the number of qubits the returned operators act on
     * @return the Kraus operators, in a fixed order
     * @throws IllegalArgumentException if {@code numQubits} differs from the target count
     * @throws UnsupportedOperationException if the channel does not support that many qubits
     */
    public abstract ComplexMatrix[] getKrausOperators(int numQubits);
    
    /**
     * Returns a copy of this model with a different noise strength.
     *
     * @param newProbability the new probability, between zero and one inclusive
     * @return a new model of the same type with the same targets
     * @throws IllegalArgumentException if {@code newProbability} is outside the allowed range
     */
    public abstract NoiseModel withProbability(double newProbability);
    
    /**
     * Returns a copy of this model acting on different target qubits.
     *
     * @param newTargetQubits the new target qubits
     * @return a new model of the same type with the same probability
     * @throws NullPointerException if {@code newTargetQubits} is {@code null}
     */
    public abstract NoiseModel withTargetQubits(Qubit[] newTargetQubits);
    
    /**
     * Compares this model with another object.
     *
     * <p>Two models are equal when they have the same runtime class, name, probability to within
     * {@code 1e-10}, and target array. Qubits compare by index, so labels do not matter.</p>
     *
     * @param obj the object to compare with
     * @return {@code true} if {@code obj} is an equal noise model
     */
    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        
        NoiseModel other = (NoiseModel) obj;
        return name.equals(other.name) &&
               Math.abs(probability - other.probability) < 1e-10 &&
               Arrays.equals(targetQubits, other.targetQubits);
    }
    
    /**
     * Returns a hash code consistent with {@link #equals(Object)}.
     *
     * <p>The tolerance used by {@code equals} means that two nearly equal probabilities can share
     * an equality relation while hashing differently.</p>
     *
     * @return the hash code of the name, probability and targets
     */
    @Override
    public int hashCode() {
        int result = name.hashCode();
        result = 31 * result + Double.hashCode(probability);
        result = 31 * result + Arrays.hashCode(targetQubits);
        return result;
    }
    
    /**
     * Returns a description of this model.
     *
     * @return a string containing the name, probability and target qubits
     */
    @Override
    public String toString() {
        return String.format(Locale.ROOT, "NoiseModel[name='%s', probability=%.4f, targets=%s]",
            name, probability, Arrays.toString(targetQubits));
    }
}