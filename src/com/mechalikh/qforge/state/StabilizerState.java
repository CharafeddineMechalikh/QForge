package com.mechalikh.qforge.state;

import com.mechalikh.qforge.math.Complex;
import com.mechalikh.qforge.operations.QuantumGate;
import com.mechalikh.qforge.operations.QuantumOperation;
import java.util.Arrays;
import java.util.Locale;
import java.util.Objects;
import java.util.Random;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Pure quantum state stored as an Aaronson-Gottesman Clifford tableau.
 *
 * <p>The tableau has {@code 2n} rows and {@code 2n} binary columns plus one phase column, where
 * {@code n} is the number of qubits. Rows {@code 0} to {@code n - 1} are the stabilizer
 * generators of the state, and rows {@code n} to {@code 2n - 1} are their destabilizer partners.
 * Row {@code i} for {@code i < n} and row {@code n + i} anticommute, while every other pair of
 * rows commutes. Each row stores an {@code X} part, a {@code Z} part and a phase exponent in
 * {@code {0, 1, 2, 3}} that represents the Pauli operator {@code i^phase X^x Z^z}. The state is
 * the simultaneous {@code +1} eigenspace of the stabilizer rows.</p>
 *
 * <p>The backend stores two bits per qubit per row, so memory grows quadratically in the qubit
 * count rather than exponentially. It supports the Clifford gates {@code X}, {@code Y},
 * {@code Z}, {@code H}, {@code S}, {@code S} dagger, {@code CNOT}, {@code CX}, {@code CZ},
 * {@code SWAP} and the identity, and it supports Pauli {@code Z} measurement and sampling.
 * Gates are applied in place and the state is always normalized. Non-Clifford gates, amplitude
 * access and projection onto arbitrary eigenvectors are rejected.</p>
 *
 * <p>Qubit {@code 0} is the most significant bit of every packed measurement outcome, matching
 * {@link QuantumState#collapseToBasisState(int[], int)} and the basis-state convention of
 * {@link StateVector}.</p>
 */
public class StabilizerState extends QuantumState {
    private static final int MAX_PACKED_QUBITS = 32;
    private static final int MAX_EXACT_TARGETS = 16;

    private final Random random;
    private final long[][] x;
    private final long[][] z;
    private final byte[] phase;
    private final int words;
    private final int rows;

    /**
     * Creates the all-zero state {@code |0...0>} with a thread-local random source.
     *
     * <p>This constructor is equivalent to calling {@link #StabilizerState(int, Random)} with
     * {@link ThreadLocalRandom#current()}.</p>
     *
     * @param numQubits number of qubits, must be positive
     * @throws IllegalArgumentException if {@code numQubits} is not positive
     */
    public StabilizerState(int numQubits) {
        this(numQubits, ThreadLocalRandom.current());
    }

    /**
     * Creates the all-zero state {@code |0...0>} with the supplied random source.
     *
     * <p>The initial tableau has the stabilizer rows {@code Z_0} to {@code Z_(n-1)} and the
     * destabilizer rows {@code X_0} to {@code X_(n-1)}, all with phase exponent zero. The
     * random source is used for measurement outcomes and for sampling, so a fixed seed makes
     * tests reproducible.</p>
     *
     * @param numQubits number of qubits, must be positive
     * @param random random source used for measurement outcomes, must not be null
     * @throws IllegalArgumentException if {@code numQubits} is not positive
     * @throws NullPointerException if {@code random} is null
     */
    public StabilizerState(int numQubits, Random random) {
        super(numQubits);
        this.random = Objects.requireNonNull(random, "random");
        this.words = (numQubits + 63) >>> 6;
        this.rows = 2 * numQubits;
        this.x = new long[rows][words];
        this.z = new long[rows][words];
        this.phase = new byte[rows];
        for (int qubit = 0; qubit < numQubits; qubit++) {
            setBit(z[qubit], qubit, true);
            setBit(x[numQubits + qubit], qubit, true);
        }
    }

    private StabilizerState(int numQubits, Random random, long[][] x, long[][] z, byte[] phase) {
        super(numQubits);
        this.random = random;
        this.words = (numQubits + 63) >>> 6;
        this.rows = 2 * numQubits;
        this.x = x;
        this.z = z;
        this.phase = phase;
    }

    /**
     * Applies a quantum operation to this state.
     *
     * <p>Only {@link QuantumGate} is supported and it is applied through
     * {@link #applyGate(QuantumGate)}. Every other operation type causes an
     * {@link UnsupportedOperationException}.</p>
     *
     * @param operation operation to apply
     * @return the resulting stabilizer state, which is this instance
     * @throws UnsupportedOperationException if the operation is not a gate
     */
    @Override
    public QuantumState applyOperation(QuantumOperation operation) {
        if (operation instanceof QuantumGate) {
            return applyGate((QuantumGate) operation);
        }
        throw new UnsupportedOperationException("Unsupported operation type: " + operation.getClass());
    }

    /**
     * Applies a Clifford gate to this state in place.
     *
     * <p>The gate name selects the update rule, ignoring case. The supported names are
     * {@code X}, {@code Y}, {@code Z}, {@code H}, {@code S}, {@code SDG}, {@code S} dagger,
     * {@code CNOT}, {@code CX}, {@code CZ}, {@code SWAP}, {@code I} and {@code IDENTITY}.
     * Qubit positions are read from {@link QuantumGate#getControlQubits()} and
     * {@link QuantumGate#getTargetQubits()}, not from the gate matrix. A {@code CZ} gate may be
     * given either as one control and one target qubit or as two target qubits. A {@code SWAP}
     * is implemented as three {@code CNOT} gates. Any other gate name is rejected.</p>
     *
     * @param gate gate to apply, must not be null
     * @return this state after the gate has been applied
     * @throws IllegalArgumentException if the gate qubit counts do not match the named gate, a
     *         qubit index is outside the state, or a gate repeats a qubit
     * @throws UnsupportedOperationException if the gate is not a Clifford gate
     * @throws NullPointerException if {@code gate} is null
     */
    public StabilizerState applyGate(QuantumGate gate) {
        Objects.requireNonNull(gate, "gate");
        String name = gate.getName();
        if (name == null) {
            throw new UnsupportedOperationException("Gate name is null; only Clifford gates are supported");
        }
        Qubit[] controls = gate.getControlQubits();
        Qubit[] targets = gate.getTargetQubits();
        String normalized = name.toUpperCase(Locale.ROOT);

        switch (normalized) {
            case "X":
                applyX(requireSingleTarget(name, controls, targets));
                return this;
            case "Y":
                applyY(requireSingleTarget(name, controls, targets));
                return this;
            case "Z":
                applyZ(requireSingleTarget(name, controls, targets));
                return this;
            case "H":
                applyH(requireSingleTarget(name, controls, targets));
                return this;
            case "S":
                applyS(requireSingleTarget(name, controls, targets), false);
                return this;
            case "SDG":
            case "S\u2020":
                applyS(requireSingleTarget(name, controls, targets), true);
                return this;
            case "CNOT":
            case "CX":
                requireTwoQubitGate(name, controls, targets, 1, 1);
                int control = qubitIndex(name, controls[0]);
                int target = qubitIndex(name, targets[0]);
                requireDistinct(name, control, target);
                applyCnot(control, target);
                return this;
            case "CZ":
                if (controls.length == 1 && targets.length == 1) {
                    int czControl = qubitIndex(name, controls[0]);
                    int czTarget = qubitIndex(name, targets[0]);
                    requireDistinct(name, czControl, czTarget);
                    applyCz(czControl, czTarget);
                } else if (controls.length == 0 && targets.length == 2) {
                    int czFirst = qubitIndex(name, targets[0]);
                    int czSecond = qubitIndex(name, targets[1]);
                    requireDistinct(name, czFirst, czSecond);
                    applyCz(czFirst, czSecond);
                } else {
                    throw new IllegalArgumentException(
                        "CZ gate requires one control and one target qubit, or two target qubits");
                }
                return this;
            case "SWAP":
                if (controls.length != 0 || targets.length != 2) {
                    throw new IllegalArgumentException("SWAP gate requires exactly two target qubits");
                }
                int swapFirst = qubitIndex(name, targets[0]);
                int swapSecond = qubitIndex(name, targets[1]);
                requireDistinct(name, swapFirst, swapSecond);
                applySwap(swapFirst, swapSecond);
                return this;
            case "I":
            case "IDENTITY":
                requireSingleTarget(name, controls, targets);
                return this;
            default:
                throw new UnsupportedOperationException(
                    "Gate '" + name + "' is not supported by the stabilizer backend; "
                        + "only Clifford gates are supported");
        }
    }

    /**
     * Draws joint computational-basis samples for the given target qubits.
     *
     * <p>Every shot is an independent sample of the Pauli {@code Z} observable on exactly the
     * target qubits. The state is not modified, because each shot is drawn from a private copy
     * of the tableau. The bits of a shot are packed into an {@code int} with the bit of target
     * {@code 0} as the most significant bit of the result. At most 32 target qubits can be
     * packed into one {@code int}.</p>
     *
     * @param targets qubits to sample, must not be null or empty and must not repeat a qubit
     * @param shots number of samples to draw, must not be negative
     * @return an array of length {@code shots} with one packed outcome per shot
     * @throws IllegalArgumentException if the target array is invalid, has more than 32
     *         entries, or {@code shots} is negative
     */
    public int[] sampleZ(Qubit[] targets, int shots) {
        Qubit[] samples = validateTargets(targets);
        if (shots < 0) {
            throw new IllegalArgumentException("Number of shots must not be negative: " + shots);
        }
        if (samples.length > MAX_PACKED_QUBITS) {
            throw new IllegalArgumentException(
                "At most " + MAX_PACKED_QUBITS + " target qubits can be packed into an int, got "
                    + samples.length);
        }
        int[] outcomes = new int[shots];
        for (int shot = 0; shot < shots; shot++) {
            StabilizerState work = copy();
            int packed = 0;
            for (int j = 0; j < samples.length; j++) {
                int bit = work.measureQubit(samples[j].getIndex());
                packed |= bit << (samples.length - 1 - j);
            }
            outcomes[shot] = packed;
        }
        return outcomes;
    }

    /**
     * Returns the exact marginal distribution of Pauli {@code Z} measurements on the targets.
     *
     * <p>The distribution is computed by branching over the measurement outcomes, not by
     * enumerating amplitudes. A measurement that anticommutes with a stabilizer generator
     * splits into two equally likely branches, and a deterministic measurement follows the
     * single stabilizer eigenvalue, so the recursion is exact. The state is not modified. The
     * packed outcome index uses target {@code 0} as the most significant bit.</p>
     *
     * @param targets qubits to measure, must not be null or empty and must not repeat a qubit
     * @return an array of length {@code 2^k} with the probability of each packed outcome, where
     *         {@code k} is the number of targets
     * @throws IllegalArgumentException if the target array is invalid or has more than 16
     *         entries
     */
    public double[] outcomeProbabilitiesZ(Qubit[] targets) {
        Qubit[] measured = validateTargets(targets);
        if (measured.length > MAX_EXACT_TARGETS) {
            throw new IllegalArgumentException(
                "Exact outcome probabilities are limited to " + MAX_EXACT_TARGETS
                    + " target qubits, got " + measured.length);
        }
        double[] probabilities = new double[1 << measured.length];
        accumulateProbabilities(this, measured, 0, 0, 1.0, probabilities);
        return probabilities;
    }

    /**
     * Collapses the target qubits onto one computational-basis outcome.
     *
     * <p>Each target is measured with a random outcome and an {@code X} gate is applied to it
     * when the measured bit differs from the requested bit. The supplied state is not modified.
     * Bit {@code j} of {@code outcome}, counted from the most significant bit, belongs to
     * {@code targets[j]}.</p>
     *
     * @param targets qubits to measure, must not be null or empty and must not repeat a qubit
     * @param outcome packed outcome bits, in {@code [0, 2^targets.length)}
     * @return a new stabilizer state with the requested outcome
     * @throws IllegalArgumentException if the target array is invalid, has more than 32
     *         entries, or {@code outcome} is out of range
     */
    public QuantumState collapseZ(Qubit[] targets, int outcome) {
        Qubit[] measured = validateTargets(targets);
        if (measured.length > MAX_PACKED_QUBITS) {
            throw new IllegalArgumentException(
                "At most " + MAX_PACKED_QUBITS + " target outcomes can be packed into an int, got "
                    + measured.length);
        }
        if (measured.length < MAX_PACKED_QUBITS) {
            int limit = 1 << measured.length;
            if (outcome < 0 || outcome >= limit) {
                throw new IllegalArgumentException("Measurement outcome out of range: " + outcome);
            }
        }
        StabilizerState collapsed = copy();
        for (int j = 0; j < measured.length; j++) {
            int qubit = measured[j].getIndex();
            int measuredBit = collapsed.measureQubit(qubit);
            int requestedBit = (outcome >>> (measured.length - 1 - j)) & 1;
            if (measuredBit != requestedBit) {
                collapsed.applyX(qubit);
            }
        }
        return collapsed;
    }

    /**
     * Measures one qubit in the computational basis and updates this state.
     *
     * <p>A random outcome is drawn from the exact marginal distribution of the qubit, so a
     * deterministic qubit always returns its eigenvalue. The tableau is updated to describe
     * the post-measurement state.</p>
     *
     * @param qubit qubit to measure, must belong to this state
     * @return the measured bit, either zero or one
     * @throws IllegalArgumentException if {@code qubit} is null or its index is outside the
     *         state
     */
    public int measureZ(Qubit qubit) {
        if (qubit == null) {
            throw new IllegalArgumentException("Target qubit must not be null");
        }
        return measureQubit(qubitIndex("measurement", qubit));
    }

    /**
     * Returns the tensor product of this state and another stabilizer state.
     *
     * <p>The qubits of this state keep positions {@code 0} to {@code numQubits - 1}, and the
     * qubits of {@code other} follow in order. The stabilizer rows of both factors come first,
     * followed by their destabilizer rows.</p>
     *
     * @param other right-hand factor, must be a {@link StabilizerState}
     * @return the combined stabilizer state
     * @throws IllegalArgumentException if {@code other} is not a {@link StabilizerState}
     */
    @Override
    public QuantumState tensorProduct(QuantumState other) {
        if (!(other instanceof StabilizerState)) {
            throw new IllegalArgumentException("Can only tensor product with another StabilizerState");
        }
        StabilizerState right = (StabilizerState) other;
        int leftQubits = numQubits;
        int rightQubits = right.numQubits;
        int totalQubits = leftQubits + rightQubits;
        int totalWords = (totalQubits + 63) >>> 6;
        int totalRows = 2 * totalQubits;

        long[][] combinedX = new long[totalRows][totalWords];
        long[][] combinedZ = new long[totalRows][totalWords];
        byte[] combinedPhase = new byte[totalRows];

        for (int row = 0; row < leftQubits; row++) {
            copyRow(this, row, combinedX, combinedZ, combinedPhase, row, 0);
        }
        for (int row = 0; row < rightQubits; row++) {
            copyRow(right, row, combinedX, combinedZ, combinedPhase, leftQubits + row, leftQubits);
        }
        for (int row = 0; row < leftQubits; row++) {
            copyRow(this, leftQubits + row, combinedX, combinedZ, combinedPhase,
                totalQubits + row, 0);
        }
        for (int row = 0; row < rightQubits; row++) {
            copyRow(right, rightQubits + row, combinedX, combinedZ, combinedPhase,
                totalQubits + leftQubits + row, leftQubits);
        }

        return new StabilizerState(totalQubits, random, combinedX, combinedZ, combinedPhase);
    }

    /**
     * Returns this state, because a stabilizer state is always normalized.
     *
     * @return this state
     */
    @Override
    public StabilizerState normalize() {
        return this;
    }

    /**
     * Returns {@code true}, because a stabilizer state represents a pure state.
     *
     * @return {@code true}
     */
    @Override
    public boolean isPure() {
        return true;
    }

    /**
     * Returns the purity of this pure state.
     *
     * @return {@code 1}
     */
    @Override
    public double getPurity() {
        return 1.0;
    }

    /**
     * Returns an independent copy of this state.
     *
     * <p>The tableau arrays are copied, so gate application and measurement on either instance
     * do not affect the other. The copy shares the random source of this state.</p>
     *
     * @return a copy with the same tableau
     */
    @Override
    public StabilizerState copy() {
        return new StabilizerState(numQubits, random, copyRows(x), copyRows(z), phase.clone());
    }

    /**
     * Collapses the target qubits onto one computational-basis outcome.
     *
     * <p>The integer indices are converted to qubits and the request is delegated to
     * {@link #collapseZ(Qubit[], int)}. The supplied state is not modified.</p>
     *
     * @param targetQubits qubit indices to measure, in the order used to interpret
     *        {@code outcome}
     * @param outcome packed outcome bits, in {@code [0, 2^targetQubits.length)}
     * @return a new stabilizer state with the requested outcome
     * @throws IllegalArgumentException if the target array is invalid or {@code outcome} is out
     *         of range
     */
    @Override
    public QuantumState collapseToBasisState(int[] targetQubits, int outcome) {
        int[] validated = validateTargetQubits(targetQubits);
        Qubit[] targets = new Qubit[validated.length];
        for (int j = 0; j < validated.length; j++) {
            targets[j] = getQubit(validated[j]);
        }
        return collapseZ(targets, outcome);
    }

    /**
     * Always throws, because a stabilizer state cannot be projected onto an arbitrary
     * eigenvector.
     *
     * @param targetQubits qubit indices that would be projected
     * @param eigenvector vector over the target qubits
     * @return never returns normally
     * @throws UnsupportedOperationException always
     */
    @Override
    public QuantumState collapseToEigenvector(int[] targetQubits, Complex[] eigenvector) {
        throw new UnsupportedOperationException(
            "Eigenvector projection is not available for StabilizerState; "
                + "only computational-basis measurements are supported");
    }

    /**
     * Compares this state with another object.
     *
     * <p>Two stabilizer states are equal when they have the same number of qubits and the same
     * X parts, Z parts and phase exponents in every tableau row. The random source is not
     * compared.</p>
     *
     * @param obj object to compare with
     * @return {@code true} if {@code obj} is a {@link StabilizerState} with the same tableau
     */
    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null || getClass() != obj.getClass()) {
            return false;
        }
        StabilizerState other = (StabilizerState) obj;
        return numQubits == other.numQubits
            && Arrays.deepEquals(x, other.x)
            && Arrays.deepEquals(z, other.z)
            && Arrays.equals(phase, other.phase);
    }

    /**
     * Returns a hash code derived from the qubit count and the full tableau.
     *
     * @return the hash code
     */
    @Override
    public int hashCode() {
        int result = numQubits;
        result = 31 * result + Arrays.deepHashCode(x);
        result = 31 * result + Arrays.deepHashCode(z);
        result = 31 * result + Arrays.hashCode(phase);
        return result;
    }

    /**
     * Returns a diagnostic description of this state.
     *
     * @return a string with the qubit count and the number of stabilizer rows
     */
    @Override
    public String toString() {
        return "StabilizerState[" + numQubits + " qubits, " + numQubits + " stabilizer rows]";
    }

    private void accumulateProbabilities(StabilizerState work, Qubit[] targets, int index,
                                         int packed, double probability, double[] probabilities) {
        if (index == targets.length) {
            probabilities[packed] += probability;
            return;
        }
        int qubit = targets[index].getIndex();
        int pivot = work.findStabilizerWithX(qubit);
        if (pivot < 0) {
            int bit = work.deterministicOutcome(qubit);
            accumulateProbabilities(work, targets, index + 1, (packed << 1) | bit,
                probability, probabilities);
            return;
        }
        StabilizerState zero = work.copy();
        zero.applyForcedMeasurement(qubit, pivot, 0);
        accumulateProbabilities(zero, targets, index + 1, packed << 1,
            probability * 0.5, probabilities);
        StabilizerState one = work.copy();
        one.applyForcedMeasurement(qubit, pivot, 1);
        accumulateProbabilities(one, targets, index + 1, (packed << 1) | 1,
            probability * 0.5, probabilities);
    }

    private int measureQubit(int qubit) {
        int pivot = findStabilizerWithX(qubit);
        if (pivot < 0) {
            return deterministicOutcome(qubit);
        }
        int outcome = random.nextBoolean() ? 1 : 0;
        applyForcedMeasurement(qubit, pivot, outcome);
        return outcome;
    }

    private int findStabilizerWithX(int qubit) {
        for (int row = 0; row < numQubits; row++) {
            if (getBit(x[row], qubit)) {
                return row;
            }
        }
        return -1;
    }

    private void applyForcedMeasurement(int qubit, int pivot, int outcome) {
        if (!getBit(x[pivot], qubit)) {
            throw new IllegalStateException(
                "Qubit " + qubit + " does not have a random measurement outcome");
        }

        for (int row = 0; row < rows; row++) {
            if (row != pivot && getBit(x[row], qubit)) {
                rowSum(row, pivot);
            }
        }

        clearRow(pivot);
        setBit(z[pivot], qubit, true);
        phase[pivot] = (byte) (2 * outcome);

        for (int row = 0; row < numQubits; row++) {
            if (row != pivot && getBit(z[row], qubit)) {
                rowSum(row, pivot);
            }
        }

        int destabilizer = numQubits + pivot;
        clearRow(destabilizer);
        setBit(x[destabilizer], qubit, true);
    }

    private int deterministicOutcome(int qubit) {
        int bitCount = 2 * words * Long.SIZE;
        long[][] basis = new long[bitCount][];
        byte[] basisPhase = new byte[bitCount];

        for (int row = 0; row < numQubits; row++) {
            long[] vector = new long[2 * words];
            System.arraycopy(x[row], 0, vector, 0, words);
            System.arraycopy(z[row], 0, vector, words, words);
            int rowPhase = phase[row];
            while (true) {
                int pivot = highestSetBit(vector);
                if (pivot < 0) {
                    break;
                }
                if (basis[pivot] == null) {
                    basis[pivot] = vector;
                    basisPhase[pivot] = (byte) rowPhase;
                    break;
                }
                rowPhase = combinePhase(vector, rowPhase, basis[pivot], basisPhase[pivot]);
                xor(vector, basis[pivot]);
            }
        }

        long[] target = new long[2 * words];
        setBit(target, words * Long.SIZE + qubit, true);
        int targetPhase = 0;
        while (true) {
            int pivot = highestSetBit(target);
            if (pivot < 0) {
                break;
            }
            long[] vector = basis[pivot];
            if (vector == null) {
                throw new IllegalStateException(
                    "Qubit " + qubit + " does not have a deterministic measurement outcome");
            }
            targetPhase = combinePhase(target, targetPhase, vector, basisPhase[pivot]);
            xor(target, vector);
        }
        if ((targetPhase & 1) != 0) {
            throw new IllegalStateException("Measurement phase is not real for qubit " + qubit);
        }
        return (targetPhase >> 1) & 1;
    }

    private int combinePhase(long[] targetVector, int targetPhase,
                             long[] sourceVector, int sourcePhase) {
        int parity = 0;
        for (int word = 0; word < words; word++) {
            parity ^= (int) (Long.bitCount(targetVector[words + word] & sourceVector[word]) & 1L);
        }
        return (targetPhase + sourcePhase + 2 * parity) & 3;
    }

    private void rowSum(int targetRow, int sourceRow) {
        int parity = 0;
        for (int word = 0; word < words; word++) {
            parity ^= (int) (Long.bitCount(z[targetRow][word] & x[sourceRow][word]) & 1L);
        }
        phase[targetRow] = (byte) ((phase[targetRow] + phase[sourceRow] + 2 * parity) & 3);
        for (int word = 0; word < words; word++) {
            x[targetRow][word] ^= x[sourceRow][word];
            z[targetRow][word] ^= z[sourceRow][word];
        }
    }

    private void applyX(int qubit) {
        for (int row = 0; row < rows; row++) {
            if (getBit(z[row], qubit)) {
                phase[row] = (byte) ((phase[row] + 2) & 3);
            }
        }
    }

    private void applyY(int qubit) {
        for (int row = 0; row < rows; row++) {
            if (getBit(x[row], qubit) ^ getBit(z[row], qubit)) {
                phase[row] = (byte) ((phase[row] + 2) & 3);
            }
        }
    }

    private void applyZ(int qubit) {
        for (int row = 0; row < rows; row++) {
            if (getBit(x[row], qubit)) {
                phase[row] = (byte) ((phase[row] + 2) & 3);
            }
        }
    }

    private void applyH(int qubit) {
        for (int row = 0; row < rows; row++) {
            boolean xBit = getBit(x[row], qubit);
            boolean zBit = getBit(z[row], qubit);
            if (xBit && zBit) {
                phase[row] = (byte) ((phase[row] + 2) & 3);
            }
            setBit(x[row], qubit, zBit);
            setBit(z[row], qubit, xBit);
        }
    }

    private void applyS(int qubit, boolean dagger) {
        for (int row = 0; row < rows; row++) {
            if (getBit(x[row], qubit)) {
                phase[row] = (byte) ((phase[row] + (dagger ? 3 : 1)) & 3);
                setBit(z[row], qubit, !getBit(z[row], qubit));
            }
        }
    }

    private void applyCnot(int control, int target) {
        for (int row = 0; row < rows; row++) {
            boolean controlX = getBit(x[row], control);
            boolean targetZ = getBit(z[row], target);
            if (controlX) {
                setBit(x[row], target, !getBit(x[row], target));
            }
            if (targetZ) {
                setBit(z[row], control, !getBit(z[row], control));
            }
        }
    }

    private void applyCz(int first, int second) {
        for (int row = 0; row < rows; row++) {
            boolean firstX = getBit(x[row], first);
            boolean secondX = getBit(x[row], second);
            boolean firstZ = getBit(z[row], first);
            boolean secondZ = getBit(z[row], second);
            if (firstX && secondX) {
                phase[row] = (byte) ((phase[row] + 2) & 3);
            }
            if (secondX) {
                setBit(z[row], first, !firstZ);
            }
            if (firstX) {
                setBit(z[row], second, !secondZ);
            }
        }
    }

    private void applySwap(int first, int second) {
        for (int row = 0; row < rows; row++) {
            boolean firstX = getBit(x[row], first);
            boolean secondX = getBit(x[row], second);
            boolean firstZ = getBit(z[row], first);
            boolean secondZ = getBit(z[row], second);
            setBit(x[row], first, secondX);
            setBit(x[row], second, firstX);
            setBit(z[row], first, secondZ);
            setBit(z[row], second, firstZ);
        }
    }

    private int requireSingleTarget(String name, Qubit[] controls, Qubit[] targets) {
        if (controls.length != 0 || targets.length != 1) {
            throw new IllegalArgumentException(
                name + " gate requires exactly one target qubit and no control qubits");
        }
        return qubitIndex(name, targets[0]);
    }

    private void requireTwoQubitGate(String name, Qubit[] controls, Qubit[] targets,
                                     int expectedControls, int expectedTargets) {
        if (controls.length != expectedControls || targets.length != expectedTargets) {
            throw new IllegalArgumentException(
                name + " gate requires " + expectedControls + " control qubit(s) and "
                    + expectedTargets + " target qubit(s)");
        }
    }

    private void requireDistinct(String name, int first, int second) {
        if (first == second) {
            throw new IllegalArgumentException(
                name + " gate requires two different qubits, got " + first + " twice");
        }
    }

    private int qubitIndex(String name, Qubit qubit) {
        if (qubit == null) {
            throw new IllegalArgumentException(name + " gate has a null qubit");
        }
        int index = qubit.getIndex();
        if (index < 0 || index >= numQubits) {
            throw new IllegalArgumentException(
                name + " gate qubit index " + index + " is outside [0, " + numQubits + ")");
        }
        return index;
    }

    private Qubit[] validateTargets(Qubit[] targets) {
        if (targets == null || targets.length == 0) {
            throw new IllegalArgumentException("At least one target qubit is required");
        }
        Qubit[] copy = targets.clone();
        boolean[] seen = new boolean[numQubits];
        for (Qubit target : copy) {
            int index = qubitIndex("measurement", target);
            if (seen[index]) {
                throw new IllegalArgumentException("Duplicate target qubit index: " + index);
            }
            seen[index] = true;
        }
        return copy;
    }

    private void clearRow(int row) {
        Arrays.fill(x[row], 0L);
        Arrays.fill(z[row], 0L);
        phase[row] = 0;
    }

    private static boolean getBit(long[] row, int bit) {
        return ((row[bit >>> 6] >>> (bit & 63)) & 1L) != 0;
    }

    private static void setBit(long[] row, int bit, boolean value) {
        int word = bit >>> 6;
        long mask = 1L << (bit & 63);
        if (value) {
            row[word] |= mask;
        } else {
            row[word] &= ~mask;
        }
    }

    private static int highestSetBit(long[] vector) {
        for (int word = vector.length - 1; word >= 0; word--) {
            if (vector[word] != 0L) {
                return (word << 6) + 63 - Long.numberOfLeadingZeros(vector[word]);
            }
        }
        return -1;
    }

    private static void xor(long[] target, long[] source) {
        for (int word = 0; word < target.length; word++) {
            target[word] ^= source[word];
        }
    }

    private static long[][] copyRows(long[][] source) {
        long[][] copied = new long[source.length][];
        for (int row = 0; row < source.length; row++) {
            copied[row] = source[row].clone();
        }
        return copied;
    }

    private static void copyRow(StabilizerState source, int sourceRow,
                                long[][] targetX, long[][] targetZ, byte[] targetPhase,
                                int targetRow, int qubitOffset) {
        for (int qubit = 0; qubit < source.numQubits; qubit++) {
            if (getBit(source.x[sourceRow], qubit)) {
                setBit(targetX[targetRow], qubitOffset + qubit, true);
            }
            if (getBit(source.z[sourceRow], qubit)) {
                setBit(targetZ[targetRow], qubitOffset + qubit, true);
            }
        }
        targetPhase[targetRow] = source.phase[sourceRow];
    }
}
