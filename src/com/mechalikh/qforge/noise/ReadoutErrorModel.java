package com.mechalikh.qforge.noise;

/**
 * Per-qubit readout confusion applied when a computational-basis measurement is sampled.
 *
 * <p>Each qubit has an independent two-outcome confusion matrix: a qubit prepared in
 * {@code |0>} is reported as {@code 1} with probability {@code p01}, and a qubit prepared in
 * {@code |1>} is reported as {@code 0} with probability {@code p10}. The model transforms a
 * true outcome distribution into the observed one by applying that matrix along each bit
 * axis, which costs {@code O(n 2^n)} for {@code n} qubits.</p>
 *
 * <p>The model is immutable. Qubits without explicit probabilities are treated as perfect.</p>
 */
public final class ReadoutErrorModel {
    private final double[] p01;
    private final double[] p10;

    /**
     * Creates a readout model.
     *
     * @param p01 probability of reading {@code 1} for a true {@code 0}, per qubit
     * @param p10 probability of reading {@code 0} for a true {@code 1}, per qubit
     * @throws IllegalArgumentException if the arrays have different lengths, or a value is
     *         outside the closed interval from zero to one
     * @throws NullPointerException if either array is {@code null}
     */
    public ReadoutErrorModel(double[] p01, double[] p10) {
        if (p01.length != p10.length) {
            throw new IllegalArgumentException("Readout probability arrays must have equal length");
        }
        this.p01 = p01.clone();
        this.p10 = p10.clone();
        for (int i = 0; i < this.p01.length; i++) {
            if (!valid(this.p01[i]) || !valid(this.p10[i])) {
                throw new IllegalArgumentException(
                    "Readout probability must be between 0 and 1 for qubit " + i);
            }
        }
    }

    private static boolean valid(double value) {
        return value >= 0.0 && value <= 1.0;
    }

    /**
     * Reports whether any qubit has a nonzero error probability.
     *
     * @return {@code true} if at least one probability is positive
     */
    public boolean isEmpty() {
        for (int i = 0; i < p01.length; i++) {
            if (p01[i] > 0.0 || p10[i] > 0.0) {
                return false;
            }
        }
        return true;
    }

    /**
     * Returns the reported-error probability for a true {@code 0}.
     *
     * @param qubit qubit index
     * @return the probability, zero for a qubit without an entry
     */
    public double getP01(int qubit) {
        return qubit >= 0 && qubit < p01.length ? p01[qubit] : 0.0;
    }

    /**
     * Returns the reported-error probability for a true {@code 1}.
     *
     * @param qubit qubit index
     * @return the probability, zero for a qubit without an entry
     */
    public double getP10(int qubit) {
        return qubit >= 0 && qubit < p10.length ? p10[qubit] : 0.0;
    }

    /**
     * Applies the confusion to a distribution over {@code numQubits} bits.
     *
     * <p>Bit {@code b} of an outcome index stands for the readout of qubit
     * {@code numQubits - 1 - b}, matching the most-significant-bit-first qubit ordering used
     * by the simulator. The input is not modified.</p>
     *
     * @param probabilities true outcome probabilities, length {@code 2^numQubits}
     * @param numQubits number of qubits
     * @return the observed outcome probabilities
     * @throws IllegalArgumentException if the array length does not match the qubit count
     */
    public double[] apply(double[] probabilities, int numQubits) {
        if (probabilities.length != (1 << numQubits)) {
            throw new IllegalArgumentException("Probability array length does not match qubit count");
        }
        double[] current = probabilities.clone();
        for (int qubit = 0; qubit < numQubits; qubit++) {
            int mask = 1 << (numQubits - 1 - qubit);
            double zeroGivenZero = 1.0 - getP01(qubit);
            double oneGivenZero = getP01(qubit);
            double zeroGivenOne = getP10(qubit);
            double oneGivenOne = 1.0 - getP10(qubit);
            double[] next = new double[current.length];
            for (int index = 0; index < current.length; index++) {
                double probability = current[index];
                if (probability == 0.0) {
                    continue;
                }
                boolean bit = (index & mask) != 0;
                int cleared = index & ~mask;
                if (bit) {
                    next[cleared] += probability * zeroGivenOne;
                    next[cleared | mask] += probability * oneGivenOne;
                } else {
                    next[cleared] += probability * zeroGivenZero;
                    next[cleared | mask] += probability * oneGivenZero;
                }
            }
            current = next;
        }
        return current;
    }

    /**
     * Returns a description of the model.
     *
     * @return the maximum {@code p01} and {@code p10} over all qubits
     */
    @Override
    public String toString() {
        double max01 = 0.0;
        double max10 = 0.0;
        for (int i = 0; i < p01.length; i++) {
            max01 = Math.max(max01, p01[i]);
            max10 = Math.max(max10, p10[i]);
        }
        return "ReadoutErrorModel[qubits=" + p01.length + ", maxP01=" + max01
            + ", maxP10=" + max10 + "]";
    }
}
