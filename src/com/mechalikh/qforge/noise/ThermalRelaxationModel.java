package com.mechalikh.qforge.noise;

import com.mechalikh.qforge.math.Complex;
import com.mechalikh.qforge.math.ComplexMatrix;
import com.mechalikh.qforge.state.Qubit;
import java.util.Locale;

/**
 * Thermal relaxation for a qubit held for a duration, described by {@code T1} and {@code T2}.
 *
 * <p>The channel is the composition of amplitude damping with probability
 * {@code 1 - exp(-t/T1)} and pure phase damping with probability
 * {@code 1 - exp(-t/T_phi)}, where {@code 1/T_phi = 1/T2 - 1/(2 T1)}. The four Kraus
 * operators are the products of the two amplitude-damping operators and the two phase-damping
 * operators, so the set is complete by construction. {@code T2} must not exceed
 * {@code 2 T1}, because a larger value would need a negative pure dephasing rate.</p>
 *
 * <p>The times can use any unit as long as they agree. The engine passes nanoseconds.
 * The model is immutable; {@link #withProbability(double)} returns a new model whose
 * duration is chosen to give the requested amplitude-damping probability, and
 * {@link #withTargetQubits(Qubit[])} keeps the rates and duration.</p>
 */
public class ThermalRelaxationModel extends NoiseModel {
    private final double t1;
    private final double t2;
    private final double duration;

    /**
     * Creates a thermal relaxation channel.
     *
     * @param t1 energy relaxation time, positive
     * @param t2 coherence time, positive and not larger than {@code 2 T1}
     * @param duration time the qubit is exposed to the channel, non-negative
     * @param targetQubits target qubits, at most two
     * @throws IllegalArgumentException if a time is invalid, {@code T2 > 2 T1}, the duration is
     *         negative, or more than two targets are supplied
     */
    public ThermalRelaxationModel(double t1, double t2, double duration, Qubit... targetQubits) {
        super("ThermalRelaxation", amplitudeDampingProbability(t1, t2, duration), targetQubits);
        if (t1 <= 0.0 || t2 <= 0.0) {
            throw new IllegalArgumentException("T1 and T2 must be positive");
        }
        if (t2 > 2.0 * t1) {
            throw new IllegalArgumentException("T2 cannot exceed 2 * T1");
        }
        if (duration < 0.0) {
            throw new IllegalArgumentException("Duration cannot be negative");
        }
        if (targetQubits.length > 2) {
            throw new IllegalArgumentException("Thermal relaxation supports at most two targets");
        }
        this.t1 = t1;
        this.t2 = t2;
        this.duration = duration;
    }

    private static double amplitudeDampingProbability(double t1, double t2, double duration) {
        if (t1 <= 0.0) {
            return 0.0;
        }
        double probability = 1.0 - Math.exp(-duration / t1);
        return Math.min(1.0, Math.max(0.0, probability));
    }

    /**
     * Returns the amplitude-damping probability for the configured duration.
     *
     * @return {@code 1 - exp(-t/T1)}
     */
    public double getAmplitudeDampingProbability() {
        return 1.0 - Math.exp(-duration / t1);
    }

    /**
     * Returns the pure dephasing probability for the configured duration.
     *
     * @return {@code 1 - exp(-t/T_phi)}
     */
    public double getPhaseDampingProbability() {
        double pureDephasing = 1.0 / t2 - 1.0 / (2.0 * t1);
        return 1.0 - Math.exp(-duration * Math.max(0.0, pureDephasing));
    }

    /**
     * Returns the energy relaxation time.
     *
     * @return {@code T1}
     */
    public double getT1() {
        return t1;
    }

    /**
     * Returns the coherence time.
     *
     * @return {@code T2}
     */
    public double getT2() {
        return t2;
    }

    /**
     * Returns the exposure duration.
     *
     * @return the duration
     */
    public double getDuration() {
        return duration;
    }

    /**
     * Builds the Kraus operators.
     *
     * <p>The single qubit set has four operators. For two targets the independent product is
     * used, which yields sixteen operators.</p>
     *
     * @param numQubits number of target qubits, one or two
     * @return the Kraus operators
     * @throws IllegalArgumentException if the count does not match the targets
     */
    @Override
    public ComplexMatrix[] getKrausOperators(int numQubits) {
        if (numQubits != targetQubits.length || numQubits < 1 || numQubits > 2) {
            throw new IllegalArgumentException(
                String.format(Locale.ROOT, "Expected 1 or 2 qubits, got %d", numQubits));
        }
        double damping = getAmplitudeDampingProbability();
        double dephasing = getPhaseDampingProbability();

        ComplexMatrix dampingIdentity = new ComplexMatrix(2, 2);
        dampingIdentity.set(0, 0, Complex.ONE);
        dampingIdentity.set(1, 1, new Complex(Math.sqrt(1.0 - damping), 0));
        ComplexMatrix dampingDecay = new ComplexMatrix(2, 2);
        dampingDecay.set(0, 1, new Complex(Math.sqrt(damping), 0));

        ComplexMatrix phaseIdentity = ComplexMatrix.identity(2)
            .scale(Math.sqrt(1.0 - dephasing / 2.0));
        ComplexMatrix phaseFlip = ComplexMatrix.pauliZ().scale(Math.sqrt(dephasing / 2.0));

        ComplexMatrix[] single = {
            phaseIdentity.multiply(dampingIdentity),
            phaseIdentity.multiply(dampingDecay),
            phaseFlip.multiply(dampingIdentity),
            phaseFlip.multiply(dampingDecay)
        };
        return independentKraus(single, numQubits);
    }

    /**
     * Returns a copy with a duration that gives the requested amplitude-damping probability.
     *
     * @param newProbability desired {@code 1 - exp(-t/T1)}, between zero and one
     * @return a new model on the same targets
     * @throws IllegalArgumentException if {@code newProbability} is outside the allowed range
     */
    @Override
    public NoiseModel withProbability(double newProbability) {
        if (newProbability < 0.0 || newProbability > 1.0) {
            throw new IllegalArgumentException("Probability must be between 0 and 1");
        }
        if (newProbability == 1.0) {
            return new ThermalRelaxationModel(t1, t2, Double.MAX_VALUE / 4, targetQubits);
        }
        double scaled = -t1 * Math.log(1.0 - newProbability);
        return new ThermalRelaxationModel(t1, t2, scaled, targetQubits);
    }

    /**
     * Returns a copy on different target qubits.
     *
     * @param newTargetQubits new targets, at most two
     * @return a new model with the same rates and duration
     */
    @Override
    public NoiseModel withTargetQubits(Qubit[] newTargetQubits) {
        return new ThermalRelaxationModel(t1, t2, duration, newTargetQubits);
    }

    /**
     * Returns a description of the model.
     *
     * @return the times and the two damping probabilities
     */
    @Override
    public String toString() {
        return String.format(Locale.ROOT,
            "ThermalRelaxationModel[t1=%.3f, t2=%.3f, duration=%.3f, pAmp=%.3e, pPhase=%.3e]",
            t1, t2, duration, getAmplitudeDampingProbability(), getPhaseDampingProbability());
    }
}
