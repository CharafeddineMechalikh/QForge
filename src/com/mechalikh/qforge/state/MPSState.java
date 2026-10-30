package com.mechalikh.qforge.state;

import com.mechalikh.qforge.math.Complex;
import com.mechalikh.qforge.math.ComplexMatrix;
import com.mechalikh.qforge.math.ComplexSvd;
import com.mechalikh.qforge.operations.QuantumGate;
import com.mechalikh.qforge.operations.QuantumOperation;
import java.util.Arrays;
import java.util.Locale;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Pure quantum state stored as a matrix product state in canonical form.
 *
 * <p>Qubit {@code i} owns a rank three tensor {@code tensors[i]} of shape
 * {@code [bondLeft][2][bondRight]}. Bond {@code i}, which separates qubit {@code i - 1}
 * from qubit {@code i}, stores the Schmidt coefficients {@code lambdas[i]}. The two
 * boundary bonds {@code lambdas[0]} and {@code lambdas[numQubits]} have length one. The
 * state is {@code lambdas[0] * tensors[0] * lambdas[1] * ... * tensors[n-1] * lambdas[n]},
 * where every bond vector acts as a diagonal matrix on the adjacent bond. Qubit {@code 0}
 * is the leftmost tensor and the most significant bit of a packed basis index, matching
 * {@link StateVector}.</p>
 *
 * <p>Every gate application is followed by a canonicalization sweep. The sweep contracts
 * the current bond vectors into the tensors, factorizes each bond with
 * {@link ComplexSvd}, and keeps at most {@code maxBondDimension} singular values per bond.
 * Each truncated bond adds its relative discarded weight to
 * {@link #getTruncationError()}, and the state is renormalized so its norm stays one even
 * when truncation occurs.</p>
 *
 * <p>Two-qubit gates on non-adjacent qubits are implemented with explicit SWAP gates that
 * move one qubit next to the other. The swaps are undone after the gate, so the logical
 * qubit order is unchanged and no dense vector is formed. Computational basis sampling,
 * collapse onto a basis outcome, and the overlap between two states are computed by tensor
 * contraction and work at any size. The accessors that need the whole amplitude vector
 * (single amplitudes, all basis probabilities, the reduced density matrix and matrix
 * expectation values) allocate {@code 2^numQubits} entries and are limited to twenty four
 * qubits.</p>
 */
public class MPSState extends QuantumState implements AmplitudeAccess {
    private static final int MAX_DENSE_QUBITS = 24;
    private static final double NORM_TOLERANCE = 1e-15;

    private Complex[][][][] tensors;
    private Complex[][] lambdas;
    private final int maxBondDimension;
    private double truncationError;

    /**
     * Creates the state {@code |0...0>} with the given bond dimension limit.
     *
     * <p>Every tensor has shape {@code [1][2][1]} with entry one for the physical index
     * zero, and every bond vector is {@code [1]}.</p>
     *
     * @param numQubits number of qubits, must be positive
     * @param maxBondDimension maximum number of Schmidt coefficients per bond, at least one
     * @throws IllegalArgumentException if {@code numQubits} is not positive or
     *         {@code maxBondDimension} is less than one
     */
    public MPSState(int numQubits, int maxBondDimension) {
        super(numQubits);
        if (maxBondDimension < 1) {
            throw new IllegalArgumentException("Maximum bond dimension must be at least one");
        }
        this.maxBondDimension = maxBondDimension;
        this.truncationError = 0.0;
        this.tensors = new Complex[numQubits][][][];
        this.lambdas = new Complex[numQubits + 1][];
        for (int qubit = 0; qubit < numQubits; qubit++) {
            this.tensors[qubit] = new Complex[1][2][1];
            this.tensors[qubit][0][0][0] = Complex.ONE;
            this.tensors[qubit][0][1][0] = Complex.ZERO;
        }
        for (int bond = 0; bond <= numQubits; bond++) {
            this.lambdas[bond] = new Complex[]{Complex.ONE};
        }
    }

    /**
     * Creates a state from tensors stored as matrices and brings it into canonical form.
     *
     * <p>Entry {@code tensors[q]} is a matrix with {@code bondLeft * 2} rows and
     * {@code bondRight} columns. Row {@code left * 2 + physical} holds the coefficient of
     * qubit {@code q} for left bond index {@code left} and physical index
     * {@code physical}, so the matrix represents the logical shape
     * {@code [bondLeft][2][bondRight]}. The arrays are deep copied.</p>
     *
     * <p>The bond vector array may have length {@code numQubits + 1}, in which case entry
     * {@code i} is bond {@code i}; length {@code numQubits}, in which case entry {@code i}
     * is the left bond of qubit {@code i} and a unit right boundary is appended; or length
     * {@code numQubits - 1}, in which case the entries are the internal bonds and unit
     * boundaries are added on both sides.</p>
     *
     * <p>The constructor verifies that adjacent dimensions agree, then canonicalizes and
     * normalizes the state with the given bond dimension limit.</p>
     *
     * @param tensors tensors indexed by qubit, each stored as a matrix of shape
     *        {@code [bondLeft * 2][bondRight]}
     * @param lambdas bond vectors, as described above
     * @param numQubits number of qubits, must be positive
     * @param maxBondDimension maximum number of Schmidt coefficients per bond, at least one
     * @throws IllegalArgumentException if an array or entry is null, a dimension is
     *         inconsistent, or the qubit count or bond limit is invalid
     * @throws IllegalStateException if the supplied state has zero norm
     */
    public MPSState(Complex[][][] tensors, Complex[][] lambdas, int numQubits,
            int maxBondDimension) {
        super(numQubits);
        if (maxBondDimension < 1) {
            throw new IllegalArgumentException("Maximum bond dimension must be at least one");
        }
        this.maxBondDimension = maxBondDimension;
        this.truncationError = 0.0;
        this.tensors = copyFlattenedTensors(tensors, numQubits);
        this.lambdas = copyLambdas(lambdas, numQubits);
        validateDimensions();
        canonicalize(false);
    }

    /**
     * Creates a state from fully indexed tensors and brings it into canonical form.
     *
     * <p>Entry {@code tensors[q]} has shape {@code [bondLeft][2][bondRight]}. The arrays
     * are deep copied and validated in the same way as the matrix form of this
     * constructor. The bond vector array accepts the same three lengths.</p>
     *
     * @param tensors tensors indexed by qubit, each of shape
     *        {@code [bondLeft][2][bondRight]}
     * @param lambdas bond vectors, as described for the matrix form of this constructor
     * @param numQubits number of qubits, must be positive
     * @param maxBondDimension maximum number of Schmidt coefficients per bond, at least one
     * @throws IllegalArgumentException if an array or entry is null, a dimension is
     *         inconsistent, or the qubit count or bond limit is invalid
     * @throws IllegalStateException if the supplied state has zero norm
     */
    public MPSState(Complex[][][][] tensors, Complex[][] lambdas, int numQubits,
            int maxBondDimension) {
        super(numQubits);
        if (maxBondDimension < 1) {
            throw new IllegalArgumentException("Maximum bond dimension must be at least one");
        }
        this.maxBondDimension = maxBondDimension;
        this.truncationError = 0.0;
        this.tensors = copyTensors(tensors, numQubits);
        this.lambdas = copyLambdas(lambdas, numQubits);
        validateDimensions();
        canonicalize(false);
    }

    private MPSState(Complex[][][][] tensors, Complex[][] lambdas, int numQubits,
            int maxBondDimension, boolean canonicalize) {
        super(numQubits);
        this.maxBondDimension = maxBondDimension;
        this.truncationError = 0.0;
        this.tensors = tensors;
        this.lambdas = lambdas;
        if (canonicalize) {
            canonicalize(false);
        }
    }

    /**
     * Applies an operation and returns the resulting state.
     *
     * <p>Only {@link QuantumGate} operations are supported. The returned state is a new
     * instance and this state is not modified.</p>
     *
     * @param operation operation to apply
     * @return the resulting state
     * @throws IllegalArgumentException if {@code operation} is null
     * @throws UnsupportedOperationException if the operation is not a gate, or if the gate
     *         acts on more than two qubits
     */
    @Override
    public QuantumState applyOperation(QuantumOperation operation) {
        if (operation == null) {
            throw new IllegalArgumentException("Operation must not be null");
        }
        if (operation instanceof QuantumGate) {
            return applyGate((QuantumGate) operation);
        }
        throw new UnsupportedOperationException(
            "Unsupported operation type for the matrix product state backend: "
                + operation.getClass().getName());
    }

    /**
     * Applies a gate and returns a new state.
     *
     * <p>The qubit positions are taken from {@link QuantumGate#getMatrixQubitIndices()}.
     * A one-qubit gate acts directly on the physical index of its tensor. A two-qubit gate
     * acts exactly when its qubits are adjacent. For a non-adjacent pair one qubit is moved
     * next to the other with explicit SWAP gates, the gate is applied, and the swaps are
     * undone so the logical qubit order is restored. The result is canonicalized and
     * truncated to the configured maximum bond dimension.</p>
     *
     * @param gate gate to apply
     * @return the resulting state
     * @throws IllegalArgumentException if {@code gate} is null, or if its qubit indices are
     *         out of range or repeated
     * @throws UnsupportedOperationException if the gate acts on more than two qubits
     */
    public MPSState applyGate(QuantumGate gate) {
        if (gate == null) {
            throw new IllegalArgumentException("Gate must not be null");
        }
        int[] indices = gate.getMatrixQubitIndices();
        validateGateIndices(indices);
        MPSState result = copy();
        result.applyGateInPlace(gate.getMatrix(), indices);
        return result;
    }

    private void applyGateInPlace(ComplexMatrix matrix, int[] indices) {
        if (indices.length == 1) {
            applySingleQubitGate(matrix, indices[0]);
            return;
        }
        if (indices.length != 2) {
            throw new UnsupportedOperationException(
                "The matrix product state backend supports one and two qubit gates only");
        }

        int first = indices[0];
        int second = indices[1];
        if (Math.abs(first - second) == 1) {
            int left = Math.min(first, second);
            applyAdjacentGate(matrix, left, first == left);
            return;
        }

        if (first < second) {
            for (int position = second; position > first + 1; position--) {
                applySwap(position - 1);
            }
            applyAdjacentGate(matrix, first, true);
            for (int position = first + 1; position < second; position++) {
                applySwap(position);
            }
        } else {
            for (int position = first; position > second + 1; position--) {
                applySwap(position - 1);
            }
            applyAdjacentGate(matrix, second, false);
            for (int position = second + 1; position < first; position++) {
                applySwap(position);
            }
        }
    }

    private void applySingleQubitGate(ComplexMatrix matrix, int target) {
        int bondLeft = tensors[target].length;
        int bondRight = tensors[target][0][0].length;
        Complex[][][] updated = new Complex[bondLeft][2][bondRight];
        for (int left = 0; left < bondLeft; left++) {
            for (int out = 0; out < 2; out++) {
                for (int right = 0; right < bondRight; right++) {
                    Complex sum = Complex.ZERO;
                    for (int in = 0; in < 2; in++) {
                        sum = sum.add(matrix.get(out, in)
                            .multiply(tensors[target][left][in][right]));
                    }
                    updated[left][out][right] = sum;
                }
            }
        }
        tensors[target] = updated;
        canonicalize(true);
    }

    private void applyAdjacentGate(ComplexMatrix matrix, int left, boolean firstIndexAtLeft) {
        ComplexMatrix ordered = firstIndexAtLeft ? matrix : swapMatrixIndices(matrix);
        int bondLeft = tensors[left].length;
        int middle = lambdas[left + 1].length;
        int bondRight = tensors[left + 1][0][0].length;

        ComplexMatrix contracted = new ComplexMatrix(bondLeft * 2, 2 * bondRight);
        for (int a = 0; a < bondLeft; a++) {
            for (int out0 = 0; out0 < 2; out0++) {
                for (int out1 = 0; out1 < 2; out1++) {
                    for (int c = 0; c < bondRight; c++) {
                        Complex sum = Complex.ZERO;
                        for (int in0 = 0; in0 < 2; in0++) {
                            for (int in1 = 0; in1 < 2; in1++) {
                                for (int b = 0; b < middle; b++) {
                                    sum = sum.add(ordered.get(out0 * 2 + out1, in0 * 2 + in1)
                                        .multiply(tensors[left][a][in0][b])
                                        .multiply(lambdas[left + 1][b])
                                        .multiply(tensors[left + 1][b][in1][c]));
                                }
                            }
                        }
                        contracted.set(a * 2 + out0, out1 * bondRight + c, sum);
                    }
                }
            }
        }

        ComplexSvd.Result decomposition = ComplexSvd.decompose(contracted);
        double[] singularValues = decomposition.getSingularValues();
        ComplexMatrix leftVectors = decomposition.getU();
        ComplexMatrix rightAdjoint = decomposition.getVAdjoint();
        int keep = Math.min(singularValues.length, maxBondDimension);
        truncationError += discardedFraction(singularValues, keep);

        Complex[][][] newLeft = new Complex[bondLeft][2][keep];
        for (int a = 0; a < bondLeft; a++) {
            for (int out0 = 0; out0 < 2; out0++) {
                for (int bond = 0; bond < keep; bond++) {
                    newLeft[a][out0][bond] = leftVectors.get(a * 2 + out0, bond);
                }
            }
        }
        Complex[][][] newRight = new Complex[keep][2][bondRight];
        for (int bond = 0; bond < keep; bond++) {
            for (int out1 = 0; out1 < 2; out1++) {
                for (int c = 0; c < bondRight; c++) {
                    newRight[bond][out1][c] = rightAdjoint.get(bond, out1 * bondRight + c);
                }
            }
        }

        tensors[left] = newLeft;
        tensors[left + 1] = newRight;
        lambdas[left + 1] = new Complex[keep];
        for (int bond = 0; bond < keep; bond++) {
            lambdas[left + 1][bond] = new Complex(singularValues[bond], 0.0);
        }
        canonicalize(false);
    }

    private void applySwap(int position) {
        ComplexMatrix swap = new ComplexMatrix(4, 4);
        swap.set(0, 0, Complex.ONE);
        swap.set(1, 2, Complex.ONE);
        swap.set(2, 1, Complex.ONE);
        swap.set(3, 3, Complex.ONE);
        applyAdjacentGate(swap, position, true);
    }

    private static ComplexMatrix swapMatrixIndices(ComplexMatrix matrix) {
        ComplexMatrix swapped = new ComplexMatrix(4, 4);
        for (int first = 0; first < 2; first++) {
            for (int second = 0; second < 2; second++) {
                for (int inputFirst = 0; inputFirst < 2; inputFirst++) {
                    for (int inputSecond = 0; inputSecond < 2; inputSecond++) {
                        swapped.set(first * 2 + second, inputFirst * 2 + inputSecond,
                            matrix.get(second * 2 + first, inputSecond * 2 + inputFirst));
                    }
                }
            }
        }
        return swapped;
    }

    private void validateGateIndices(int[] indices) {
        if (indices == null || indices.length == 0) {
            throw new IllegalArgumentException("Gate must act on at least one qubit");
        }
        boolean[] seen = new boolean[numQubits];
        for (int index : indices) {
            if (index < 0 || index >= numQubits) {
                throw new IllegalArgumentException("Gate qubit index out of bounds: " + index);
            }
            if (seen[index]) {
                throw new IllegalArgumentException("Duplicate gate qubit index: " + index);
            }
            seen[index] = true;
        }
    }

    private void canonicalize(boolean countTruncation) {
        int n = numQubits;
        Complex[][][][] newTensors = new Complex[n][][][];
        Complex[][] newLambdas = new Complex[n + 1][];
        newLambdas[0] = new Complex[]{Complex.ONE};

        Complex[][] leftFactor = new Complex[][]{{new Complex(lambdas[0][0])}};
        int leftDim = 1;

        for (int site = 0; site < n; site++) {
            int oldLeftDim = tensors[site].length;
            int rightDim = tensors[site][0][0].length;
            ComplexMatrix matrix = new ComplexMatrix(leftDim * 2, rightDim);
            for (int left = 0; left < leftDim; left++) {
                for (int physical = 0; physical < 2; physical++) {
                    for (int right = 0; right < rightDim; right++) {
                        Complex sum = Complex.ZERO;
                        for (int old = 0; old < oldLeftDim; old++) {
                            sum = sum.add(leftFactor[left][old]
                                .multiply(tensors[site][old][physical][right]));
                        }
                        matrix.set(left * 2 + physical, right,
                            sum.multiply(lambdas[site + 1][right]));
                    }
                }
            }

            ComplexSvd.Result decomposition = ComplexSvd.decompose(matrix);
            double[] singularValues = decomposition.getSingularValues();
            ComplexMatrix leftVectors = decomposition.getU();
            ComplexMatrix rightAdjoint = decomposition.getVAdjoint();
            int keep = Math.min(singularValues.length, maxBondDimension);
            if (countTruncation) {
                truncationError += discardedFraction(singularValues, keep);
            }
            if (singularValues[0] < NORM_TOLERANCE) {
                throw new IllegalStateException(
                    "Cannot normalize a zero-norm matrix product state");
            }

            if (site == n - 1) {
                Complex tail = rightAdjoint.get(0, 0)
                    .multiply(new Complex(singularValues[0], 0.0));
                newTensors[site] = new Complex[leftDim][2][1];
                for (int left = 0; left < leftDim; left++) {
                    for (int physical = 0; physical < 2; physical++) {
                        newTensors[site][left][physical][0] =
                            leftVectors.get(left * 2 + physical, 0).multiply(tail);
                    }
                }
                newLambdas[n] = new Complex[]{Complex.ONE};
                break;
            }

            newTensors[site] = new Complex[leftDim][2][keep];
            for (int left = 0; left < leftDim; left++) {
                for (int physical = 0; physical < 2; physical++) {
                    for (int bond = 0; bond < keep; bond++) {
                        newTensors[site][left][physical][bond] =
                            leftVectors.get(left * 2 + physical, bond);
                    }
                }
            }
            newLambdas[site + 1] = new Complex[keep];
            for (int bond = 0; bond < keep; bond++) {
                newLambdas[site + 1][bond] = new Complex(singularValues[bond], 0.0);
            }

            leftFactor = new Complex[keep][rightDim];
            for (int bond = 0; bond < keep; bond++) {
                for (int old = 0; old < rightDim; old++) {
                    leftFactor[bond][old] = rightAdjoint.get(bond, old);
                }
            }
            leftDim = keep;
        }

        this.tensors = newTensors;
        this.lambdas = newLambdas;

        double norm = stateNorm();
        if (norm < NORM_TOLERANCE) {
            throw new IllegalStateException(
                "Cannot normalize a zero-norm matrix product state");
        }
        Complex scale = new Complex(1.0 / norm, 0.0);
        for (int left = 0; left < tensors[n - 1].length; left++) {
            for (int physical = 0; physical < 2; physical++) {
                tensors[n - 1][left][physical][0] =
                    tensors[n - 1][left][physical][0].multiply(scale);
            }
        }
    }

    private double stateNorm() {
        int n = numQubits;
        Complex[][] environment = new Complex[][]{{Complex.ONE}};
        for (int site = n - 1; site >= 0; site--) {
            int leftDim = tensors[site].length;
            int rightDim = tensors[site][0][0].length;
            Complex[][][] folded = new Complex[leftDim][2][rightDim];
            for (int left = 0; left < leftDim; left++) {
                for (int physical = 0; physical < 2; physical++) {
                    for (int right = 0; right < rightDim; right++) {
                        folded[left][physical][right] = lambdas[site][left]
                            .multiply(tensors[site][left][physical][right]);
                    }
                }
            }
            Complex[][][] partial = new Complex[leftDim][2][rightDim];
            for (int left = 0; left < leftDim; left++) {
                for (int physical = 0; physical < 2; physical++) {
                    for (int right = 0; right < rightDim; right++) {
                        Complex sum = Complex.ZERO;
                        for (int inner = 0; inner < rightDim; inner++) {
                            sum = sum.add(folded[left][physical][inner]
                                .multiply(environment[inner][right]));
                        }
                        partial[left][physical][right] = sum;
                    }
                }
            }
            Complex[][] next = new Complex[leftDim][leftDim];
            for (int left = 0; left < leftDim; left++) {
                for (int other = 0; other < leftDim; other++) {
                    Complex sum = Complex.ZERO;
                    for (int physical = 0; physical < 2; physical++) {
                        for (int right = 0; right < rightDim; right++) {
                            sum = sum.add(partial[left][physical][right]
                                .multiply(folded[other][physical][right].conjugate()));
                        }
                    }
                    next[left][other] = sum;
                }
            }
            environment = next;
        }
        return Math.sqrt(environment[0][0].real());
    }


    /**
     * Returns the accumulated relative weight discarded by bond truncation.
     *
     * <p>Every truncation adds the fraction of the local Schmidt weight that it dropped,
     * that is, the discarded squared singular values divided by all squared singular
     * values of the same decomposition. Each addend lies in {@code [0, 1]}, but the sum is
     * not bounded by one because it accumulates over gates and bonds. A separable state
     * that needs no truncation keeps the value at zero. Construction, copying, tensor
     * products, and collapse do not add to it.</p>
     *
     * @return the accumulated relative discarded weight, a non-negative number
     */
    public double getTruncationError() {
        return truncationError;
    }

    private static double discardedFraction(double[] singularValues, int keep) {
        double discarded = 0.0;
        double total = 0.0;
        for (int index = 0; index < singularValues.length; index++) {
            double squared = singularValues[index] * singularValues[index];
            total += squared;
            if (index >= keep) {
                discarded += squared;
            }
        }
        return total > 0.0 ? discarded / total : 0.0;
    }

    /**
     * Returns the dimension of every bond, including the two boundary bonds.
     *
     * @return a new array of length {@code numQubits + 1}, with entry {@code i} holding the
     *         length of bond {@code i}
     */
    public int[] getBondDimensions() {
        int[] dimensions = new int[lambdas.length];
        for (int bond = 0; bond < lambdas.length; bond++) {
            dimensions[bond] = lambdas[bond].length;
        }
        return dimensions;
    }

    /**
     * Returns the configured maximum number of Schmidt coefficients per bond.
     *
     * @return the bond dimension limit, at least one
     */
    public int getMaxBondDimension() {
        return maxBondDimension;
    }

    /**
     * Samples computational basis outcomes of the target qubits without forming the dense
     * amplitude vector.
     *
     * <p>The state is first brought into right canonical form, where the tensor chain from
     * any bond to the right boundary is an isometry. Each shot then walks the chain from the
     * left: the already sampled prefix is carried in a vector over the left bond, the two
     * physical branches are contracted with it, and one branch is chosen with probability
     * proportional to its squared norm. The chosen branch is renormalized and becomes the
     * prefix for the next site, so a draw is a single sequential pass and no object of size
     * {@code 2^numQubits} is allocated. The walk stops after the rightmost target.</p>
     *
     * <p>This operation works at any size. Targets may be listed in any order; the first
     * target becomes the most significant bit of each outcome.</p>
     *
     * @param targetQubits qubit indices to sample, in significance order
     * @param shots number of independent samples, at least zero
     * @return an array of {@code shots} packed outcomes, where bit {@code j} of an outcome,
     *         counted from the most significant bit, is the sampled value of
     *         {@code targetQubits[j]}
     * @throws IllegalArgumentException if the target array is null or empty, contains an
     *         out-of-range or duplicate index, or if {@code shots} is negative
     * @throws IllegalStateException if the state has zero norm
     */
    public int[] sampleComputationalBasis(int[] targetQubits, int shots) {
        int[] targets = validateTargetQubits(targetQubits);
        if (shots < 0) {
            throw new IllegalArgumentException("Shot count must not be negative");
        }
        int[] outcomes = new int[shots];
        if (shots == 0) {
            return outcomes;
        }
        Complex[][][][] canonical = rightCanonicalTensors();
        int[] bitPositions = outcomeBitPositions(targets);
        int maxTarget = targets[0];
        for (int target : targets) {
            maxTarget = Math.max(maxTarget, target);
        }
        for (int shot = 0; shot < shots; shot++) {
            outcomes[shot] = sampleOnce(canonical, bitPositions, maxTarget);
        }
        return outcomes;
    }

    /**
     * Draws one computational basis outcome of the target qubits.
     *
     * <p>Equivalent to a single draw from
     * {@link #sampleComputationalBasis(int[], int)}, so every call rebuilds the right
     * canonical form. Use the multi shot overload when more than one outcome is needed.</p>
     *
     * @param targetQubits qubit indices to sample, in significance order
     * @return one packed outcome, where bit {@code j}, counted from the most significant
     *         bit, is the sampled value of {@code targetQubits[j]}
     * @throws IllegalArgumentException if the target array is null or empty, or contains an
     *         out-of-range or duplicate index
     * @throws IllegalStateException if the state has zero norm
     */
    public int sampleComputationalBasis(int[] targetQubits) {
        int[] targets = validateTargetQubits(targetQubits);
        int[] bitPositions = outcomeBitPositions(targets);
        int maxTarget = targets[0];
        for (int target : targets) {
            maxTarget = Math.max(maxTarget, target);
        }
        return sampleOnce(rightCanonicalTensors(), bitPositions, maxTarget);
    }

    private int[] outcomeBitPositions(int[] targets) {
        int[] positions = new int[numQubits];
        Arrays.fill(positions, -1);
        for (int j = 0; j < targets.length; j++) {
            positions[targets[j]] = targets.length - 1 - j;
        }
        return positions;
    }

    /**
     * Absorbs the bond vectors into the tensors and sweeps from the right so that every
     * tensor after the first is right-isometric, which makes the right environment of the
     * chain the identity.
     */
    private Complex[][][][] rightCanonicalTensors() {
        int n = numQubits;
        Complex[][][][] canonical = new Complex[n][][][];
        for (int site = 0; site < n; site++) {
            int leftDim = tensors[site].length;
            int rightDim = tensors[site][0][0].length;
            canonical[site] = new Complex[leftDim][2][rightDim];
            Complex boundary = site == 0 ? lambdas[0][0] : Complex.ONE;
            for (int left = 0; left < leftDim; left++) {
                for (int physical = 0; physical < 2; physical++) {
                    for (int right = 0; right < rightDim; right++) {
                        canonical[site][left][physical][right] =
                            tensors[site][left][physical][right]
                                .multiply(lambdas[site + 1][right])
                                .multiply(boundary);
                    }
                }
            }
        }

        for (int site = n - 1; site >= 1; site--) {
            int leftDim = canonical[site].length;
            int rightDim = canonical[site][0][0].length;
            ComplexMatrix matrix = new ComplexMatrix(leftDim, 2 * rightDim);
            for (int left = 0; left < leftDim; left++) {
                for (int physical = 0; physical < 2; physical++) {
                    for (int right = 0; right < rightDim; right++) {
                        matrix.set(left, physical * rightDim + right,
                            canonical[site][left][physical][right]);
                    }
                }
            }

            ComplexSvd.Result decomposition = ComplexSvd.decompose(matrix);
            double[] singularValues = decomposition.getSingularValues();
            ComplexMatrix leftVectors = decomposition.getU();
            ComplexMatrix rightAdjoint = decomposition.getVAdjoint();
            int rank = singularValues.length;

            Complex[][][] isometric = new Complex[rank][2][rightDim];
            for (int bond = 0; bond < rank; bond++) {
                for (int physical = 0; physical < 2; physical++) {
                    for (int right = 0; right < rightDim; right++) {
                        isometric[bond][physical][right] =
                            rightAdjoint.get(bond, physical * rightDim + right);
                    }
                }
            }

            Complex[][][] neighbor = canonical[site - 1];
            int neighborLeft = neighbor.length;
            int neighborRight = neighbor[0][0].length;
            Complex[][][] updatedNeighbor = new Complex[neighborLeft][2][rank];
            for (int left = 0; left < neighborLeft; left++) {
                for (int physical = 0; physical < 2; physical++) {
                    for (int bond = 0; bond < rank; bond++) {
                        Complex sum = Complex.ZERO;
                        for (int old = 0; old < neighborRight; old++) {
                            sum = sum.add(neighbor[left][physical][old].multiply(
                                leftVectors.get(old, bond)
                                    .multiply(new Complex(singularValues[bond], 0.0))));
                        }
                        updatedNeighbor[left][physical][bond] = sum;
                    }
                }
            }

            canonical[site] = isometric;
            canonical[site - 1] = updatedNeighbor;
        }
        return canonical;
    }

    private static int sampleOnce(Complex[][][][] canonical, int[] bitPositions,
            int maxTarget) {
        ThreadLocalRandom random = ThreadLocalRandom.current();
        Complex[] prefix = new Complex[]{Complex.ONE};
        int outcome = 0;
        for (int site = 0; site <= maxTarget; site++) {
            int rightDim = canonical[site][0][0].length;
            Complex[] zero = new Complex[rightDim];
            Complex[] one = new Complex[rightDim];
            Arrays.fill(zero, Complex.ZERO);
            Arrays.fill(one, Complex.ZERO);
            for (int left = 0; left < prefix.length; left++) {
                Complex amplitude = prefix[left];
                if (amplitude.absSquare() == 0.0) {
                    continue;
                }
                for (int right = 0; right < rightDim; right++) {
                    zero[right] = zero[right].add(
                        amplitude.multiply(canonical[site][left][0][right]));
                    one[right] = one[right].add(
                        amplitude.multiply(canonical[site][left][1][right]));
                }
            }

            double zeroWeight = squaredNorm(zero);
            double oneWeight = squaredNorm(one);
            double total = zeroWeight + oneWeight;
            if (total <= 0.0) {
                throw new IllegalStateException(
                    "Cannot sample a zero-norm matrix product state");
            }
            int bit;
            if (zeroWeight == 0.0) {
                bit = 1;
            } else if (oneWeight == 0.0) {
                bit = 0;
            } else {
                bit = random.nextDouble() * total < zeroWeight ? 0 : 1;
            }
            if (bitPositions[site] >= 0) {
                outcome |= bit << bitPositions[site];
            }

            Complex[] chosen = bit == 0 ? zero : one;
            double weight = bit == 0 ? zeroWeight : oneWeight;
            double scale = 1.0 / Math.sqrt(weight);
            for (int right = 0; right < rightDim; right++) {
                chosen[right] = chosen[right].scale(scale);
            }
            prefix = chosen;
        }
        return outcome;
    }

    private static double squaredNorm(Complex[] vector) {
        double total = 0.0;
        for (Complex entry : vector) {
            total += entry.absSquare();
        }
        return total;
    }

    /**
     * Returns the amplitude of one computational basis state.
     *
     * <p>The whole matrix product state is contracted. This operation is supported up to
     * twenty four qubits.</p>
     *
     * @param basisState basis-state index in {@code [0, 2^numQubits)}
     * @return the complex amplitude
     * @throws IndexOutOfBoundsException if {@code basisState} is out of range
     * @throws UnsupportedOperationException if the state has more than twenty four qubits
     */
    @Override
    public boolean hasDenseAccess() {
        return numQubits <= MAX_DENSE_QUBITS;
    }

    @Override
    public Complex getAmplitude(int basisState) {
        requireDenseAccess();
        if (basisState < 0 || basisState >= (1 << numQubits)) {
            throw new IndexOutOfBoundsException("Basis state index out of bounds");
        }
        return denseAmplitudes()[basisState];
    }

    /**
     * Returns the probability of one computational basis state.
     *
     * @param basisState basis-state index in {@code [0, 2^numQubits)}
     * @return the squared magnitude of the amplitude
     * @throws IndexOutOfBoundsException if {@code basisState} is out of range
     * @throws UnsupportedOperationException if the state has more than twenty four qubits
     */
    @Override
    public double getProbability(int basisState) {
        return getAmplitude(basisState).absSquare();
    }

    /**
     * Returns the probabilities of all computational basis states.
     *
     * <p>The whole matrix product state is contracted. This operation is supported up to
     * twenty four qubits.</p>
     *
     * @return a new array of length {@code 2^numQubits}
     * @throws UnsupportedOperationException if the state has more than twenty four qubits
     */
    @Override
    public double[] getProbabilities() {
        Complex[] amplitudes = denseAmplitudes();
        double[] probabilities = new double[amplitudes.length];
        for (int index = 0; index < amplitudes.length; index++) {
            probabilities[index] = amplitudes[index].absSquare();
        }
        return probabilities;
    }

    /**
     * Returns a copy of the dense amplitude vector.
     *
     * <p>The whole matrix product state is contracted. This operation is supported up to
     * twenty four qubits.</p>
     *
     * @return a new array of length {@code 2^numQubits}, indexed by basis state
     * @throws UnsupportedOperationException if the state has more than twenty four qubits
     */
    public Complex[] getAmplitudes() {
        return denseAmplitudes();
    }

    private Complex[] denseAmplitudes() {
        requireDenseAccess();
        Complex[] current = new Complex[]{lambdas[0][0]};
        int bondDim = 1;
        int prefixes = 1;
        for (int qubit = 0; qubit < numQubits; qubit++) {
            int rightDim = lambdas[qubit + 1].length;
            Complex[] next = new Complex[rightDim * 2 * prefixes];
            Arrays.fill(next, Complex.ZERO);
            for (int bond = 0; bond < bondDim; bond++) {
                for (int prefix = 0; prefix < prefixes; prefix++) {
                    Complex amplitude = current[bond * prefixes + prefix];
                    if (amplitude.absSquare() == 0.0) {
                        continue;
                    }
                    for (int physical = 0; physical < 2; physical++) {
                        for (int right = 0; right < rightDim; right++) {
                            next[right * 2 * prefixes + prefix * 2 + physical] =
                                next[right * 2 * prefixes + prefix * 2 + physical].add(
                                    amplitude.multiply(tensors[qubit][bond][physical][right])
                                        .multiply(lambdas[qubit + 1][right]));
                        }
                    }
                }
            }
            current = next;
            bondDim = rightDim;
            prefixes *= 2;
        }
        Complex tail = lambdas[numQubits][0];
        if (tail.real() != 1.0 || tail.imag() != 0.0) {
            for (int index = 0; index < current.length; index++) {
                current[index] = current[index].multiply(tail);
            }
        }
        return current;
    }

    private void requireDenseAccess() {
        if (numQubits > MAX_DENSE_QUBITS) {
            throw new UnsupportedOperationException(
                "Dense amplitude access is available only up to " + MAX_DENSE_QUBITS
                    + " qubits for the matrix product state backend; above that limit the "
                    + "state can be evolved but not measured or read out");
        }
    }

    /**
     * Returns the tensor product of this state and another matrix product state.
     *
     * <p>The qubits of this state keep positions {@code 0} to {@code numQubits - 1}, and
     * the qubits of {@code other} follow. The tensors are concatenated and a unit bond joins
     * the two factors. The result uses the larger of the two bond dimension limits.</p>
     *
     * @param other right-hand factor
     * @return the combined state
     * @throws IllegalArgumentException if {@code other} is not an {@code MPSState}
     */
    @Override
    public QuantumState tensorProduct(QuantumState other) {
        if (!(other instanceof MPSState)) {
            throw new IllegalArgumentException("Can only tensor product with another MPSState");
        }
        MPSState right = (MPSState) other;
        int totalQubits = numQubits + right.numQubits;
        Complex[][][][] combinedTensors = new Complex[totalQubits][][][];
        for (int qubit = 0; qubit < numQubits; qubit++) {
            combinedTensors[qubit] = copyTensor(tensors[qubit]);
        }
        for (int qubit = 0; qubit < right.numQubits; qubit++) {
            combinedTensors[numQubits + qubit] = copyTensor(right.tensors[qubit]);
        }

        Complex[][] combinedLambdas = new Complex[totalQubits + 1][];
        for (int bond = 0; bond < numQubits; bond++) {
            combinedLambdas[bond] = lambdas[bond].clone();
        }
        combinedLambdas[numQubits] = new Complex[]{Complex.ONE};
        for (int bond = 1; bond <= right.numQubits; bond++) {
            combinedLambdas[numQubits + bond] = right.lambdas[bond].clone();
        }

        int bondLimit = Math.max(maxBondDimension, right.maxBondDimension);
        return new MPSState(combinedTensors, combinedLambdas, totalQubits, bondLimit, false);
    }

    /**
     * Traces out every qubit that is not listed and returns the reduced density matrix.
     *
     * <p>The reduced entries are built from the dense amplitudes with the same arithmetic
     * used by {@link QuantumState#partialTrace(int[])} for a state vector. This operation is
     * supported up to twenty four qubits.</p>
     *
     * @param qubitsToKeep qubit indices to keep, in reduced-state order
     * @return the reduced density matrix
     * @throws IllegalArgumentException if the array is null or empty, or contains an
     *         out-of-range or duplicate index
     * @throws UnsupportedOperationException if the state has more than twenty four qubits
     */
    @Override
    public QuantumState partialTrace(int[] qubitsToKeep) {
        int[] keep = validateTargetQubits(qubitsToKeep);
        Complex[] amplitudes = denseAmplitudes();
        int keptDimension = 1 << keep.length;
        int restDimension = 1 << (numQubits - keep.length);
        ComplexMatrix reduced = new ComplexMatrix(keptDimension, keptDimension);
        for (int a = 0; a < keptDimension; a++) {
            for (int b = 0; b < keptDimension; b++) {
                Complex sum = Complex.ZERO;
                for (int rest = 0; rest < restDimension; rest++) {
                    sum = sum.add(amplitudes[insertBits(rest, a, keep)]
                        .multiply(amplitudes[insertBits(rest, b, keep)].conjugate()));
                }
                reduced.set(a, b, sum);
            }
        }
        return new DensityMatrix(reduced, keep.length);
    }

    /**
     * Projects the target qubits onto one computational basis outcome and renormalizes.
     *
     * <p>Up to twenty four qubits the dense amplitude vector is formed, entries whose target
     * bits differ from {@code outcome} are set to zero, and the result is rebuilt as a
     * matrix product state with the configured bond dimension limit. Above that limit the
     * target tensors are projected directly: for every target qubit the physical entry that
     * does not match the outcome bit is zeroed, the resulting state is normalized with a
     * tensor contraction, and canonical form is restored. No dense vector is formed on that
     * path.</p>
     *
     * @param targetQubits qubit indices to measure, in the order used to interpret
     *        {@code outcome}
     * @param outcome packed outcome bits, in {@code [0, 2^targetQubits.length)}
     * @return the collapsed state
     * @throws IllegalArgumentException if the target array or the outcome is invalid
     * @throws IllegalStateException if the outcome has probability below {@code 1e-15}
     */
    @Override
    public QuantumState collapseToBasisState(int[] targetQubits, int outcome) {
        int[] targets = validateTargetQubits(targetQubits);
        int numOutcomes = 1 << targets.length;
        if (outcome < 0 || outcome >= numOutcomes) {
            throw new IllegalArgumentException("Measurement outcome out of range: " + outcome);
        }
        if (numQubits <= MAX_DENSE_QUBITS) {
            return collapseDense(targets, outcome);
        }
        return collapseByProjection(targets, outcome);
    }

    private MPSState collapseDense(int[] targets, int outcome) {
        Complex[] amplitudes = denseAmplitudes();
        Complex[] collapsed = new Complex[amplitudes.length];
        Arrays.fill(collapsed, Complex.ZERO);
        int restDimension = 1 << (numQubits - targets.length);
        double normSquared = 0.0;
        for (int rest = 0; rest < restDimension; rest++) {
            int index = insertBits(rest, outcome, targets);
            collapsed[index] = amplitudes[index];
            normSquared += amplitudes[index].absSquare();
        }
        if (normSquared < NORM_TOLERANCE) {
            throw new IllegalStateException(
                "Cannot collapse onto a measurement outcome with zero probability");
        }
        double scale = 1.0 / Math.sqrt(normSquared);
        for (int index = 0; index < collapsed.length; index++) {
            if (collapsed[index].absSquare() > 0.0) {
                collapsed[index] = collapsed[index].scale(scale);
            }
        }
        return buildFromDense(collapsed);
    }

    private MPSState collapseByProjection(int[] targets, int outcome) {
        Complex[][][][] projected = deepCopy(tensors);
        for (int j = 0; j < targets.length; j++) {
            int bit = (outcome >> (targets.length - 1 - j)) & 1;
            Complex[][][] tensor = projected[targets[j]];
            for (int left = 0; left < tensor.length; left++) {
                Arrays.fill(tensor[left][1 - bit], Complex.ZERO);
            }
        }

        MPSState result = new MPSState(projected, deepCopy(lambdas), numQubits,
            maxBondDimension, false);
        double norm = result.stateNorm();
        if (norm * norm < NORM_TOLERANCE) {
            throw new IllegalStateException(
                "Cannot collapse onto a measurement outcome with zero probability");
        }
        double scale = 1.0 / norm;
        Complex[][][] last = result.tensors[numQubits - 1];
        for (int left = 0; left < last.length; left++) {
            for (int physical = 0; physical < 2; physical++) {
                last[left][physical][0] = last[left][physical][0].scale(scale);
            }
        }
        result.canonicalize(false);
        return result;
    }

    /**
     * Always throws, because projection onto an arbitrary eigenvector is not implemented
     * for the matrix product state backend.
     *
     * @param targetQubits qubit indices that would be projected
     * @param eigenvector vector over the target qubits
     * @return never returns normally
     * @throws UnsupportedOperationException always
     */
    @Override
    public QuantumState collapseToEigenvector(int[] targetQubits, Complex[] eigenvector) {
        throw new UnsupportedOperationException(
            "collapseToEigenvector is not implemented for the matrix product state backend");
    }

    private MPSState buildFromDense(Complex[] amplitudes) {
        int n = numQubits;
        Complex[][][][] newTensors = new Complex[n][][][];
        Complex[][] newLambdas = new Complex[n + 1][];
        newLambdas[0] = new Complex[]{Complex.ONE};

        Complex[] remainder = amplitudes.clone();
        int bondLeft = 1;
        for (int site = 0; site < n; site++) {
            int columns = 1 << (n - site - 1);
            ComplexMatrix matrix = new ComplexMatrix(bondLeft * 2, columns);
            for (int left = 0; left < bondLeft; left++) {
                for (int physical = 0; physical < 2; physical++) {
                    for (int column = 0; column < columns; column++) {
                        matrix.set(left * 2 + physical, column, remainder[
                            left * 2 * columns + physical * columns + column]);
                    }
                }
            }

            ComplexSvd.Result decomposition = ComplexSvd.decompose(matrix);
            double[] singularValues = decomposition.getSingularValues();
            ComplexMatrix leftVectors = decomposition.getU();
            ComplexMatrix rightAdjoint = decomposition.getVAdjoint();
            int keep = Math.min(singularValues.length, maxBondDimension);

            newTensors[site] = new Complex[bondLeft][2][keep];
            for (int left = 0; left < bondLeft; left++) {
                for (int physical = 0; physical < 2; physical++) {
                    for (int bond = 0; bond < keep; bond++) {
                        newTensors[site][left][physical][bond] =
                            leftVectors.get(left * 2 + physical, bond);
                    }
                }
            }

            if (site == n - 1) {
                Complex phase = rightAdjoint.get(0, 0)
                    .multiply(new Complex(singularValues[0], 0.0));
                for (int left = 0; left < bondLeft; left++) {
                    for (int physical = 0; physical < 2; physical++) {
                        newTensors[site][left][physical][0] =
                            newTensors[site][left][physical][0].multiply(phase);
                    }
                }
                newLambdas[n] = new Complex[]{Complex.ONE};
            } else {
                newLambdas[site + 1] = new Complex[keep];
                for (int bond = 0; bond < keep; bond++) {
                    newLambdas[site + 1][bond] =
                        new Complex(singularValues[bond], 0.0);
                }
                remainder = new Complex[keep * columns];
                for (int bond = 0; bond < keep; bond++) {
                    for (int column = 0; column < columns; column++) {
                        remainder[bond * columns + column] = rightAdjoint.get(bond, column);
                    }
                }
            }
            bondLeft = keep;
        }

        MPSState result = new MPSState(newTensors, newLambdas, n, maxBondDimension, false);
        result.canonicalize(false);
        return result;
    }

    /**
     * Returns a normalized version of this state.
     *
     * <p>The state is canonicalized again, which removes accumulated norm error. This
     * instance is modified.</p>
     *
     * @return this state
     * @throws IllegalStateException if the state has zero norm
     */
    @Override
    public MPSState normalize() {
        canonicalize(false);
        return this;
    }

    /**
     * Returns {@code true}, because a matrix product state always describes a pure state.
     *
     * @return {@code true}
     */
    @Override
    public boolean isPure() {
        return true;
    }

    /**
     * Returns {@code 1}, the purity of a pure state.
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
     * @return a new state with deep copies of every tensor and bond vector
     */
    @Override
    public MPSState copy() {
        MPSState result = new MPSState(deepCopy(tensors), deepCopy(lambdas), numQubits,
            maxBondDimension, false);
        result.truncationError = truncationError;
        return result;
    }

    /**
     * Returns the expectation value of an observable.
     *
     * <p>The dense amplitude vector is formed and the real part of
     * {@code <psi|O|psi>} is returned. This operation is supported up to twenty four
     * qubits.</p>
     *
     * @param observable square matrix of dimension {@code 2^numQubits}
     * @return the real part of the expectation value
     * @throws IllegalArgumentException if the observable is null, not square, or has the
     *         wrong dimension
     * @throws UnsupportedOperationException if the state has more than twenty four qubits
     */
    @Override
    public double getExpectationValue(ComplexMatrix observable) {
        requireDenseAccess();
        if (observable == null || !observable.isSquare()
                || observable.rows() != (1 << numQubits)) {
            throw new IllegalArgumentException("Observable dimension mismatch");
        }
        Complex[] amplitudes = denseAmplitudes();
        Complex expectation = Complex.ZERO;
        for (int row = 0; row < amplitudes.length; row++) {
            Complex rowSum = Complex.ZERO;
            for (int column = 0; column < amplitudes.length; column++) {
                rowSum = rowSum.add(observable.get(row, column).multiply(amplitudes[column]));
            }
            expectation = expectation.add(amplitudes[row].conjugate().multiply(rowSum));
        }
        return expectation.real();
    }

    /**
     * Returns the inner product {@code <this|other>} by contracting both tensor chains.
     *
     * <p>The contraction walks both states from left to right, carrying an environment matrix
     * over the two current bonds, so its cost is cubic in the bond dimension per site and no
     * dense vector is formed. This operation works at any size. Neither state is modified.</p>
     *
     * @param other the right hand state of the inner product
     * @return the complex inner product {@code <this|other>}
     * @throws IllegalArgumentException if {@code other} is null or has a different number of
     *         qubits
     */
    public Complex overlap(MPSState other) {
        if (other == null) {
            throw new IllegalArgumentException("Other state must not be null");
        }
        if (other.numQubits != numQubits) {
            throw new IllegalArgumentException("States must have the same number of qubits");
        }

        Complex[][] environment = new Complex[][]{{Complex.ONE}};
        for (int site = 0; site < numQubits; site++) {
            Complex[][][] left = tensors[site];
            Complex[][][] right = other.tensors[site];
            Complex[] leftLambdas = lambdas[site + 1];
            Complex[] rightLambdas = other.lambdas[site + 1];
            int leftDim = environment.length;
            int rightDim = environment[0].length;
            int nextLeftDim = leftLambdas.length;
            int nextRightDim = rightLambdas.length;

            Complex[][] next = new Complex[nextLeftDim][nextRightDim];
            for (int a = 0; a < nextLeftDim; a++) {
                Arrays.fill(next[a], Complex.ZERO);
            }
            for (int l1 = 0; l1 < leftDim; l1++) {
                for (int l2 = 0; l2 < rightDim; l2++) {
                    Complex weight = environment[l1][l2];
                    if (weight.absSquare() == 0.0) {
                        continue;
                    }
                    for (int physical = 0; physical < 2; physical++) {
                        for (int r1 = 0; r1 < nextLeftDim; r1++) {
                            Complex entry = left[l1][physical][r1]
                                .multiply(leftLambdas[r1]).conjugate().multiply(weight);
                            if (entry.absSquare() == 0.0) {
                                continue;
                            }
                            for (int r2 = 0; r2 < nextRightDim; r2++) {
                                next[r1][r2] = next[r1][r2].add(entry.multiply(
                                    right[l2][physical][r2].multiply(rightLambdas[r2])));
                            }
                        }
                    }
                }
            }
            environment = next;
        }
        return environment[0][0].multiply(lambdas[0][0].conjugate())
            .multiply(other.lambdas[0][0]);
    }

    /**
     * Compares this state with another object.
     *
     * <p>Up to twenty four qubits the dense amplitudes are compared with
     * {@link Complex#equals(Object)}. Larger states compare every tensor and bond vector
     * entry instead. As with {@link Complex#equals(Object)}, the comparison uses a tolerance
     * while hashing uses the stored values, so states that compare equal are not guaranteed
     * to share a hash code.</p>
     *
     * @param obj object to compare with
     * @return {@code true} if {@code obj} is an {@code MPSState} representing the same state
     */
    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null || getClass() != obj.getClass()) {
            return false;
        }
        MPSState other = (MPSState) obj;
        if (numQubits != other.numQubits) {
            return false;
        }
        if (numQubits <= MAX_DENSE_QUBITS) {
            Complex[] left = denseAmplitudes();
            Complex[] right = other.denseAmplitudes();
            for (int index = 0; index < left.length; index++) {
                if (!left[index].equals(right[index])) {
                    return false;
                }
            }
            return true;
        }
        return deepEquals(tensors, other.tensors) && deepEquals(lambdas, other.lambdas);
    }

    /**
     * Returns a hash code derived from the qubit count and the stored representation.
     *
     * @return the hash code
     */
    @Override
    public int hashCode() {
        if (numQubits <= MAX_DENSE_QUBITS) {
            return 31 * numQubits + Arrays.hashCode(denseAmplitudes());
        }
        int result = numQubits;
        result = 31 * result + Arrays.deepHashCode(tensors);
        result = 31 * result + Arrays.deepHashCode(lambdas);
        return result;
    }

    /**
     * Returns a diagnostic description of this state.
     *
     * @return a string reporting the qubit count, the bond dimension limit, and the
     *         accumulated truncation error
     */
    @Override
    public String toString() {
        return String.format(Locale.ROOT,
            "MPSState[%d qubits, maxBondDimension=%d, truncationError=%.6e]",
            numQubits, maxBondDimension, truncationError);
    }

    private static Complex[][][][] copyFlattenedTensors(Complex[][][] source, int numQubits) {
        if (source == null || source.length != numQubits) {
            throw new IllegalArgumentException(
                "Tensor array length does not match the number of qubits");
        }
        Complex[][][][] result = new Complex[numQubits][][][];
        for (int qubit = 0; qubit < numQubits; qubit++) {
            if (source[qubit] == null || source[qubit].length == 0
                    || source[qubit].length % 2 != 0) {
                throw new IllegalArgumentException(
                    "Tensor " + qubit + " must have an even, positive row count");
            }
            int bondLeft = source[qubit].length / 2;
            int bondRight = -1;
            result[qubit] = new Complex[bondLeft][][];
            for (int left = 0; left < bondLeft; left++) {
                Complex[] evenRow = source[qubit][left * 2];
                Complex[] oddRow = source[qubit][left * 2 + 1];
                if (evenRow == null || oddRow == null) {
                    throw new IllegalArgumentException(
                        "Tensor " + qubit + " contains a null row");
                }
                int width = evenRow.length;
                if (width < 1 || oddRow.length != width) {
                    throw new IllegalArgumentException(
                        "Tensor " + qubit + " is not rectangular");
                }
                if (bondRight < 0) {
                    bondRight = width;
                } else if (bondRight != width) {
                    throw new IllegalArgumentException(
                        "Tensor " + qubit + " is not rectangular");
                }
                result[qubit][left] = new Complex[2][width];
                for (int physical = 0; physical < 2; physical++) {
                    Complex[] row = physical == 0 ? evenRow : oddRow;
                    for (int right = 0; right < width; right++) {
                        if (row[right] == null) {
                            throw new IllegalArgumentException(
                                "Tensor " + qubit + " contains a null entry");
                        }
                        result[qubit][left][physical][right] = new Complex(row[right]);
                    }
                }
            }
        }
        return result;
    }

    private static Complex[][][][] copyTensors(Complex[][][][] source, int numQubits) {
        if (source == null || source.length != numQubits) {
            throw new IllegalArgumentException(
                "Tensor array length does not match the number of qubits");
        }
        Complex[][][][] result = new Complex[numQubits][][][];
        for (int qubit = 0; qubit < numQubits; qubit++) {
            if (source[qubit] == null || source[qubit].length == 0) {
                throw new IllegalArgumentException("Tensor " + qubit + " is null or empty");
            }
            int bondLeft = source[qubit].length;
            int bondRight = -1;
            result[qubit] = new Complex[bondLeft][][];
            for (int left = 0; left < bondLeft; left++) {
                if (source[qubit][left] == null || source[qubit][left].length != 2) {
                    throw new IllegalArgumentException(
                        "Tensor " + qubit + " must have two physical entries per left index");
                }
                int width = source[qubit][left][0] == null
                    ? -1 : source[qubit][left][0].length;
                if (width < 1) {
                    throw new IllegalArgumentException("Tensor " + qubit + " has an empty bond");
                }
                if (bondRight < 0) {
                    bondRight = width;
                } else if (bondRight != width) {
                    throw new IllegalArgumentException(
                        "Tensor " + qubit + " is not rectangular");
                }
                result[qubit][left] = new Complex[2][width];
                for (int physical = 0; physical < 2; physical++) {
                    if (source[qubit][left][physical] == null
                            || source[qubit][left][physical].length != width) {
                        throw new IllegalArgumentException(
                            "Tensor " + qubit + " is not rectangular");
                    }
                    for (int right = 0; right < width; right++) {
                        if (source[qubit][left][physical][right] == null) {
                            throw new IllegalArgumentException(
                                "Tensor " + qubit + " contains a null entry");
                        }
                        result[qubit][left][physical][right] =
                            new Complex(source[qubit][left][physical][right]);
                    }
                }
            }
        }
        return result;
    }

    private static Complex[][] copyLambdas(Complex[][] source, int numQubits) {
        if (source == null) {
            throw new IllegalArgumentException("Bond vector array must not be null");
        }
        Complex[][] result;
        if (source.length == numQubits + 1) {
            result = new Complex[numQubits + 1][];
            for (int bond = 0; bond <= numQubits; bond++) {
                result[bond] = copyVector(source[bond], bond);
            }
        } else if (source.length == numQubits) {
            result = new Complex[numQubits + 1][];
            for (int bond = 0; bond < numQubits; bond++) {
                result[bond] = copyVector(source[bond], bond);
            }
            result[numQubits] = new Complex[]{Complex.ONE};
        } else if (source.length == numQubits - 1) {
            result = new Complex[numQubits + 1][];
            result[0] = new Complex[]{Complex.ONE};
            for (int bond = 0; bond < source.length; bond++) {
                result[bond + 1] = copyVector(source[bond], bond + 1);
            }
            result[numQubits] = new Complex[]{Complex.ONE};
        } else {
            throw new IllegalArgumentException(
                "Bond vector array length must be " + (numQubits - 1) + ", " + numQubits
                    + ", or " + (numQubits + 1));
        }
        return result;
    }

    private static Complex[] copyVector(Complex[] source, int bond) {
        if (source == null || source.length == 0) {
            throw new IllegalArgumentException("Bond " + bond + " is null or empty");
        }
        Complex[] result = new Complex[source.length];
        for (int index = 0; index < source.length; index++) {
            if (source[index] == null) {
                throw new IllegalArgumentException("Bond " + bond + " contains a null entry");
            }
            result[index] = new Complex(source[index]);
        }
        return result;
    }

    private void validateDimensions() {
        if (tensors[0].length != 1) {
            throw new IllegalArgumentException("The left boundary bond must have dimension one");
        }
        if (tensors[numQubits - 1][0][0].length != 1) {
            throw new IllegalArgumentException("The right boundary bond must have dimension one");
        }
        for (int qubit = 0; qubit < numQubits; qubit++) {
            if (tensors[qubit].length != lambdas[qubit].length) {
                throw new IllegalArgumentException(
                    "Left bond of tensor " + qubit + " does not match its lambda vector");
            }
            if (tensors[qubit][0][0].length != lambdas[qubit + 1].length) {
                throw new IllegalArgumentException(
                    "Right bond of tensor " + qubit + " does not match its lambda vector");
            }
        }
    }

    private static Complex[][][][] deepCopy(Complex[][][][] source) {
        Complex[][][][] result = new Complex[source.length][][][];
        for (int qubit = 0; qubit < source.length; qubit++) {
            result[qubit] = copyTensor(source[qubit]);
        }
        return result;
    }

    private static Complex[][] deepCopy(Complex[][] source) {
        Complex[][] result = new Complex[source.length][];
        for (int bond = 0; bond < source.length; bond++) {
            result[bond] = source[bond].clone();
        }
        return result;
    }

    private static Complex[][][] copyTensor(Complex[][][] source) {
        Complex[][][] result = new Complex[source.length][][];
        for (int left = 0; left < source.length; left++) {
            result[left] = new Complex[2][source[left][0].length];
            for (int physical = 0; physical < 2; physical++) {
                for (int right = 0; right < source[left][physical].length; right++) {
                    result[left][physical][right] =
                        new Complex(source[left][physical][right]);
                }
            }
        }
        return result;
    }

    private static boolean deepEquals(Complex[][][][] left, Complex[][][][] right) {
        if (left.length != right.length) {
            return false;
        }
        for (int qubit = 0; qubit < left.length; qubit++) {
            if (!deepEquals(left[qubit], right[qubit])) {
                return false;
            }
        }
        return true;
    }

    private static boolean deepEquals(Complex[][][] left, Complex[][][] right) {
        if (left.length != right.length) {
            return false;
        }
        for (int a = 0; a < left.length; a++) {
            if (left[a].length != right[a].length) {
                return false;
            }
            for (int physical = 0; physical < left[a].length; physical++) {
                if (left[a][physical].length != right[a][physical].length) {
                    return false;
                }
                for (int bond = 0; bond < left[a][physical].length; bond++) {
                    if (!left[a][physical][bond].equals(right[a][physical][bond])) {
                        return false;
                    }
                }
            }
        }
        return true;
    }

    private static boolean deepEquals(Complex[][] left, Complex[][] right) {
        if (left.length != right.length) {
            return false;
        }
        for (int a = 0; a < left.length; a++) {
            if (left[a].length != right[a].length) {
                return false;
            }
            for (int b = 0; b < left[a].length; b++) {
                if (!left[a][b].equals(right[a][b])) {
                    return false;
                }
            }
        }
        return true;
    }
}
