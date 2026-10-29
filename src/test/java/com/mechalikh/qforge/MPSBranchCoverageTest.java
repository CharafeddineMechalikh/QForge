package com.mechalikh.qforge;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.mechalikh.qforge.math.Complex;
import com.mechalikh.qforge.math.ComplexMatrix;
import com.mechalikh.qforge.math.ComplexSvd;
import com.mechalikh.qforge.measurement.MeasurementOperator;
import com.mechalikh.qforge.measurement.MeasurementResult;
import com.mechalikh.qforge.measurement.Observable;
import com.mechalikh.qforge.operations.MultiQubitGate;
import com.mechalikh.qforge.operations.QuantumGate;
import com.mechalikh.qforge.operations.SingleQubitGate;
import com.mechalikh.qforge.state.DensityMatrix;
import com.mechalikh.qforge.state.MPSState;
import com.mechalikh.qforge.state.QuantumState;
import com.mechalikh.qforge.state.Qubit;
import com.mechalikh.qforge.state.StateVector;
import com.mechalikh.qforge.strategy.TensorNetworkStrategy;
import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.Test;

/**
 * Branch coverage tests for the matrix product state backend, its singular value
 * decomposition helper, and the tensor network strategy.
 */
class MPSBranchCoverageTest {
    private static final double EPS = 1e-10;

    // ------------------------------------------------------- MPS construction

    @Test
    void simpleConstructorRejectsInvalidBondLimitAndQubitCount() {
        assertThrows(IllegalArgumentException.class, () -> new MPSState(2, 0));
        assertThrows(IllegalArgumentException.class, () -> new MPSState(2, -3));
        assertThrows(IllegalArgumentException.class, () -> new MPSState(0, 2));
    }

    @Test
    void flattenedConstructorRejectsInvalidBondLimit() {
        Complex[][][] tensors = new Complex[1][][];
        tensors[0] = new Complex[][]{
            {Complex.ONE},
            {Complex.ZERO}};
        Complex[][] lambdas = {{Complex.ONE}};

        assertThrows(IllegalArgumentException.class,
            () -> new MPSState(tensors, lambdas, 1, 0));
    }

    @Test
    void indexedConstructorRejectsInvalidBondLimit() {
        Complex[][][][] tensors = zeroTensors(1);

        assertThrows(IllegalArgumentException.class,
            () -> new MPSState(tensors, new Complex[][]{{Complex.ONE}, {Complex.ONE}}, 1, 0));
    }

    @Test
    void flattenedConstructorValidatesShapeAndEntries() {
        assertThrows(IllegalArgumentException.class,
            () -> new MPSState((Complex[][][]) null, new Complex[][]{{Complex.ONE}}, 1, 2));
        assertThrows(IllegalArgumentException.class,
            () -> new MPSState(new Complex[2][][], new Complex[][]{{Complex.ONE}}, 1, 2));

        Complex[][][] nullTensor = new Complex[1][][];
        assertThrows(IllegalArgumentException.class,
            () -> new MPSState(nullTensor, new Complex[][]{{Complex.ONE}}, 1, 2));

        Complex[][][] emptyTensor = new Complex[1][][];
        emptyTensor[0] = new Complex[0][];
        assertThrows(IllegalArgumentException.class,
            () -> new MPSState(emptyTensor, new Complex[][]{{Complex.ONE}}, 1, 2));

        Complex[][][] oddTensor = new Complex[1][][];
        oddTensor[0] = new Complex[3][];
        assertThrows(IllegalArgumentException.class,
            () -> new MPSState(oddTensor, new Complex[][]{{Complex.ONE}}, 1, 2));

        Complex[][][] nullRow = new Complex[1][][];
        nullRow[0] = new Complex[][]{null, {Complex.ONE}};
        assertThrows(IllegalArgumentException.class,
            () -> new MPSState(nullRow, new Complex[][]{{Complex.ONE}}, 1, 2));

        Complex[][][] nullOddRow = new Complex[1][][];
        nullOddRow[0] = new Complex[][]{{Complex.ONE}, null};
        assertThrows(IllegalArgumentException.class,
            () -> new MPSState(nullOddRow, new Complex[][]{{Complex.ONE}}, 1, 2));

        Complex[][][] emptyRow = new Complex[1][][];
        emptyRow[0] = new Complex[][]{new Complex[0], new Complex[0]};
        assertThrows(IllegalArgumentException.class,
            () -> new MPSState(emptyRow, new Complex[][]{{Complex.ONE}}, 1, 2));

        Complex[][][] unevenRow = new Complex[1][][];
        unevenRow[0] = new Complex[][]{{Complex.ONE}, {Complex.ONE, Complex.ZERO}};
        assertThrows(IllegalArgumentException.class,
            () -> new MPSState(unevenRow, new Complex[][]{{Complex.ONE}}, 1, 2));

        Complex[][][] unevenBond = new Complex[1][][];
        unevenBond[0] = new Complex[][]{
            {Complex.ONE}, {Complex.ONE},
            {Complex.ONE, Complex.ZERO}, {Complex.ONE, Complex.ZERO}};
        assertThrows(IllegalArgumentException.class,
            () -> new MPSState(unevenBond, new Complex[][]{{Complex.ONE}}, 1, 2));

        Complex[][][] nullEntry = new Complex[1][][];
        nullEntry[0] = new Complex[][]{{null}, {Complex.ONE}};
        assertThrows(IllegalArgumentException.class,
            () -> new MPSState(nullEntry, new Complex[][]{{Complex.ONE}}, 1, 2));
    }

    @Test
    void indexedConstructorValidatesShapeAndEntries() {
        Complex[][][][] nullArray = null;
        assertThrows(IllegalArgumentException.class,
            () -> new MPSState(nullArray, new Complex[][]{{Complex.ONE}}, 1, 2));
        assertThrows(IllegalArgumentException.class,
            () -> new MPSState(new Complex[2][][][], new Complex[][]{{Complex.ONE}}, 1, 2));

        Complex[][][][] nullTensor = new Complex[1][][][];
        assertThrows(IllegalArgumentException.class,
            () -> new MPSState(nullTensor, new Complex[][]{{Complex.ONE}}, 1, 2));

        Complex[][][][] emptyTensor = new Complex[1][][][];
        emptyTensor[0] = new Complex[0][][];
        assertThrows(IllegalArgumentException.class,
            () -> new MPSState(emptyTensor, new Complex[][]{{Complex.ONE}}, 1, 2));

        Complex[][][][] nullLeft = new Complex[1][][][];
        nullLeft[0] = new Complex[1][][];
        assertThrows(IllegalArgumentException.class,
            () -> new MPSState(nullLeft, new Complex[][]{{Complex.ONE}}, 1, 2));

        Complex[][][][] wrongPhysicalCount = new Complex[1][][][];
        wrongPhysicalCount[0] = new Complex[1][1][1];
        assertThrows(IllegalArgumentException.class,
            () -> new MPSState(wrongPhysicalCount, new Complex[][]{{Complex.ONE}}, 1, 2));

        Complex[][][][] nullPhysical = new Complex[1][][][];
        nullPhysical[0] = new Complex[1][2][1];
        nullPhysical[0][0][1] = new Complex[]{Complex.ONE};
        assertThrows(IllegalArgumentException.class,
            () -> new MPSState(nullPhysical, new Complex[][]{{Complex.ONE}}, 1, 2));

        Complex[][][][] emptyBond = new Complex[1][][][];
        emptyBond[0] = new Complex[1][2][0];
        assertThrows(IllegalArgumentException.class,
            () -> new MPSState(emptyBond, new Complex[][]{{Complex.ONE}}, 1, 2));

        Complex[][][][] unevenBond = new Complex[1][][][];
        unevenBond[0] = new Complex[2][2][1];
        unevenBond[0][0][0] = new Complex[]{Complex.ONE};
        unevenBond[0][0][1] = new Complex[]{Complex.ONE};
        unevenBond[0][1][0] = new Complex[]{Complex.ONE, Complex.ZERO};
        unevenBond[0][1][1] = new Complex[]{Complex.ONE, Complex.ZERO};
        assertThrows(IllegalArgumentException.class,
            () -> new MPSState(unevenBond, new Complex[][]{{Complex.ONE, Complex.ONE}}, 1, 2));

        Complex[][][][] unevenPhysical = new Complex[1][][][];
        unevenPhysical[0] = new Complex[1][2][1];
        unevenPhysical[0][0][0] = new Complex[]{Complex.ONE};
        unevenPhysical[0][0][1] = new Complex[]{Complex.ONE, Complex.ZERO};
        assertThrows(IllegalArgumentException.class,
            () -> new MPSState(unevenPhysical, new Complex[][]{{Complex.ONE}}, 1, 2));

        Complex[][][][] nullEntry = new Complex[1][][][];
        nullEntry[0] = new Complex[1][2][1];
        nullEntry[0][0][0] = new Complex[]{Complex.ONE};
        nullEntry[0][0][1] = new Complex[]{null};
        assertThrows(IllegalArgumentException.class,
            () -> new MPSState(nullEntry, new Complex[][]{{Complex.ONE}}, 1, 2));

        Complex[][][][] nullSecondPhysical = new Complex[1][][][];
        nullSecondPhysical[0] = new Complex[1][2][1];
        nullSecondPhysical[0][0][0] = new Complex[]{Complex.ONE};
        assertThrows(IllegalArgumentException.class,
            () -> new MPSState(nullSecondPhysical, new Complex[][]{{Complex.ONE}}, 1, 2));
    }

    @Test
    void constructorRejectsMismatchedBondDimensions() {
        Complex[][][][] leftBoundary = new Complex[1][][][];
        leftBoundary[0] = new Complex[2][2][1];
        for (int left = 0; left < 2; left++) {
            leftBoundary[0][left][0] = new Complex[]{Complex.ONE};
            leftBoundary[0][left][1] = new Complex[]{Complex.ZERO};
        }
        assertThrows(IllegalArgumentException.class,
            () -> new MPSState(leftBoundary, new Complex[][]{
                {Complex.ONE, Complex.ONE}, {Complex.ONE}}, 1, 2));

        assertThrows(IllegalArgumentException.class,
            () -> new MPSState(zeroTensors(1), new Complex[][]{
                {Complex.ONE}, {Complex.ONE, Complex.ONE}}, 1, 2));

        assertThrows(IllegalArgumentException.class,
            () -> new MPSState(zeroTensors(1), new Complex[][]{
                {Complex.ONE, Complex.ONE}, {Complex.ONE}}, 1, 2));

        assertThrows(IllegalArgumentException.class,
            () -> new MPSState(zeroTensors(2), new Complex[][]{
                {Complex.ONE}, {Complex.ONE, Complex.ONE}, {Complex.ONE}}, 2, 3));
    }

    @Test
    void constructorRejectsBadBondVectors() {
        assertThrows(IllegalArgumentException.class,
            () -> new MPSState(zeroTensors(1), null, 1, 2));

        assertThrows(IllegalArgumentException.class,
            () -> new MPSState(zeroTensors(1), new Complex[][]{
                {Complex.ONE}, {Complex.ONE}, {Complex.ONE}, {Complex.ONE}}, 1, 2));

        assertThrows(IllegalArgumentException.class,
            () -> new MPSState(zeroTensors(1), new Complex[][]{null, {Complex.ONE}}, 1, 2));

        assertThrows(IllegalArgumentException.class,
            () -> new MPSState(zeroTensors(1), new Complex[][]{{}, {Complex.ONE}}, 1, 2));

        assertThrows(IllegalArgumentException.class,
            () -> new MPSState(zeroTensors(1), new Complex[][]{{null}, {Complex.ONE}}, 1, 2));
    }

    @Test
    void constructorsAcceptEveryBondVectorLength() {
        Complex[][][][] tensors = zeroTensors(2);

        MPSState full = new MPSState(tensors, new Complex[][]{
            {Complex.ONE}, {Complex.ONE}, {Complex.ONE}}, 2, 4);
        MPSState withoutTail = new MPSState(tensors, new Complex[][]{
            {Complex.ONE}, {Complex.ONE}}, 2, 4);
        MPSState internalOnly = new MPSState(tensors, new Complex[][]{
            {Complex.ONE}}, 2, 4);

        assertEquals(1.0, full.getProbability(0), EPS);
        assertEquals(1.0, withoutTail.getProbability(0), EPS);
        assertEquals(1.0, internalOnly.getProbability(0), EPS);
        assertArrayEquals(new int[]{1, 1, 1}, internalOnly.getBondDimensions());
    }

    @Test
    void constructorRejectsZeroNormState() {
        Complex[][][][] zeros = new Complex[1][1][2][1];
        zeros[0][0][0][0] = Complex.ZERO;
        zeros[0][0][1][0] = Complex.ZERO;

        assertThrows(IllegalStateException.class,
            () -> new MPSState(zeros, new Complex[][]{{Complex.ONE}, {Complex.ONE}}, 1, 2));
    }

    // --------------------------------------------------------- gate dispatch

    @Test
    void applyOperationRejectsNullAndNonGateOperations() {
        MPSState state = new MPSState(1, 2);

        assertThrows(IllegalArgumentException.class, () -> state.applyOperation(null));

        MeasurementOperator measurement = new MeasurementOperator(
            Observable.pauliZ(new Qubit(0)), 1);
        assertThrows(UnsupportedOperationException.class,
            () -> state.applyOperation(measurement));
    }

    @Test
    void applyOperationAcceptsRealGate() {
        MPSState state = new MPSState(1, 2);
        QuantumState flipped = state.applyOperation(
            SingleQubitGate.createPauliX(new Qubit(0)));

        assertEquals(1.0, flipped.getProbability(1), EPS);
        assertEquals(1.0, state.getProbability(0), EPS);
    }

    @Test
    void applyGateRejectsNullAndWideGates() {
        MPSState state = new MPSState(3, 4);

        assertThrows(IllegalArgumentException.class, () -> state.applyGate(null));

        MultiQubitGate threeQubit = new MultiQubitGate("CCC", ComplexMatrix.identity(8),
            new Qubit[]{new Qubit(0), new Qubit(1), new Qubit(2)});
        assertThrows(UnsupportedOperationException.class, () -> state.applyGate(threeQubit));
    }

    @Test
    void applyGateRejectsNullEmptyAndOutOfRangeIndices() {
        MPSState state = new MPSState(2, 4);

        SingleQubitGate nullIndices = new SingleQubitGate("N", ComplexMatrix.identity(2),
            new Qubit(0)) {
            @Override
            public int[] getMatrixQubitIndices() {
                return null;
            }
        };
        assertThrows(IllegalArgumentException.class, () -> state.applyGate(nullIndices));

        SingleQubitGate emptyIndices = new SingleQubitGate("E", ComplexMatrix.identity(2),
            new Qubit(0)) {
            @Override
            public int[] getMatrixQubitIndices() {
                return new int[0];
            }
        };
        assertThrows(IllegalArgumentException.class, () -> state.applyGate(emptyIndices));

        SingleQubitGate negative = new SingleQubitGate("N", ComplexMatrix.identity(2),
            new Qubit(0)) {
            @Override
            public int[] getMatrixQubitIndices() {
                return new int[]{-1};
            }
        };
        assertThrows(IllegalArgumentException.class, () -> state.applyGate(negative));

        SingleQubitGate tooHigh = new SingleQubitGate("H", ComplexMatrix.identity(2),
            new Qubit(0)) {
            @Override
            public int[] getMatrixQubitIndices() {
                return new int[]{5};
            }
        };
        assertThrows(IllegalArgumentException.class, () -> state.applyGate(tooHigh));

        MultiQubitGate repeated = MultiQubitGate.createCNOT(new Qubit(0), new Qubit(0));
        assertThrows(IllegalArgumentException.class, () -> state.applyGate(repeated));
    }

    @Test
    void adjacentReversedGateMatchesStateVector() {
        List<QuantumGate> gates = List.of(
            SingleQubitGate.createPauliX(new Qubit(0)),
            SingleQubitGate.createHadamard(new Qubit(1)),
            MultiQubitGate.createCNOT(new Qubit(1), new Qubit(0)));

        MPSState mps = runMps(2, 8, gates);
        StateVector vector = runStateVector(2, gates);

        assertEquals(0.0, maxDifference(mps.getAmplitudes(), vector.getAmplitudes()), EPS);
    }

    @Test
    void singleQubitGateTruncatesWhenBondLimitIsSmall() {
        MPSState state = runMps(3, 2, List.of(
            SingleQubitGate.createHadamard(new Qubit(0)),
            MultiQubitGate.createCNOT(new Qubit(0), new Qubit(1)),
            SingleQubitGate.createHadamard(new Qubit(1)),
            MultiQubitGate.createCNOT(new Qubit(1), new Qubit(2))));

        MPSState constrained = new MPSState(3, 1);
        MPSState result = constrained.applyGate(
            SingleQubitGate.createHadamard(new Qubit(0)));
        assertEquals(1.0, totalProbability(result), EPS);

        assertTrue(state.getTruncationError() >= 0.0);
        assertTrue(result.getTruncationError() >= 0.0);
    }

    // ------------------------------------------------------ amplitude access

    @Test
    void amplitudeAccessRejectsOutOfRangeBasisStates() {
        MPSState state = new MPSState(2, 4);

        assertThrows(IndexOutOfBoundsException.class, () -> state.getAmplitude(-1));
        assertThrows(IndexOutOfBoundsException.class, () -> state.getAmplitude(4));
        assertThrows(IndexOutOfBoundsException.class, () -> state.getProbability(-1));
        assertThrows(IndexOutOfBoundsException.class, () -> state.getProbability(4));
    }

    @Test
    void denseAccessRejectsLargeStates() {
        MPSState large = new MPSState(25, 1);

        assertThrows(UnsupportedOperationException.class, () -> large.getAmplitude(0));
        assertThrows(UnsupportedOperationException.class, () -> large.getProbability(0));
        assertThrows(UnsupportedOperationException.class, () -> large.getProbabilities());
        assertThrows(UnsupportedOperationException.class, () -> large.getAmplitudes());
        assertThrows(UnsupportedOperationException.class,
            () -> large.getExpectationValue(ComplexMatrix.identity(2)));
    }

    // ------------------------------------------------------- partial trace

    @Test
    void partialTraceMatchesStateVector() {
        List<QuantumGate> gates = List.of(
            SingleQubitGate.createHadamard(new Qubit(0)),
            MultiQubitGate.createCNOT(new Qubit(0), new Qubit(2)),
            SingleQubitGate.createRY(0.8, new Qubit(1)));
        MPSState mps = runMps(3, 8, gates);
        StateVector vector = runStateVector(3, gates);

        QuantumState expected = vector.partialTrace(new int[]{0, 2});
        QuantumState actual = mps.partialTrace(new int[]{0, 2});

        assertTrue(actual instanceof DensityMatrix);
        assertEquals(expected, actual);
    }

    @Test
    void partialTraceRejectsInvalidTargets() {
        MPSState state = new MPSState(2, 4);

        assertThrows(IllegalArgumentException.class, () -> state.partialTrace(null));
        assertThrows(IllegalArgumentException.class, () -> state.partialTrace(new int[0]));
        assertThrows(IllegalArgumentException.class, () -> state.partialTrace(new int[]{2}));
        assertThrows(IllegalArgumentException.class,
            () -> state.partialTrace(new int[]{0, 0}));
    }

    // ----------------------------------------------------------- collapse

    @Test
    void collapseRejectsOutOfRangeOutcomesAndZeroProbability() {
        MPSState state = new MPSState(2, 4);

        assertThrows(IllegalArgumentException.class,
            () -> state.collapseToBasisState(new int[]{0}, -1));
        assertThrows(IllegalArgumentException.class,
            () -> state.collapseToBasisState(new int[]{0}, 2));
        assertThrows(IllegalStateException.class,
            () -> state.collapseToBasisState(new int[]{0}, 1));
        assertThrows(IllegalArgumentException.class,
            () -> state.collapseToBasisState(null, 0));
        assertThrows(IllegalArgumentException.class,
            () -> state.collapseToBasisState(new int[0], 0));
    }

    @Test
    void collapseToBasisStateKeepsOnlySelectedOutcome() {
        MPSState plus = runMps(1, 4, List.of(
            SingleQubitGate.createHadamard(new Qubit(0))));

        MPSState collapsed = (MPSState) plus.collapseToBasisState(new int[]{0}, 1);

        assertEquals(0.0, collapsed.getProbability(0), EPS);
        assertEquals(1.0, collapsed.getProbability(1), EPS);
    }

    // ------------------------------------------------------ expectation value

    @Test
    void expectationValueRejectsInvalidObservables() {
        MPSState state = new MPSState(1, 2);

        assertThrows(IllegalArgumentException.class, () -> state.getExpectationValue(null));
        assertThrows(IllegalArgumentException.class,
            () -> state.getExpectationValue(new ComplexMatrix(2, 1)));
        assertThrows(IllegalArgumentException.class,
            () -> state.getExpectationValue(ComplexMatrix.identity(4)));
    }

    // --------------------------------------------------- equality and copies

    @Test
    void equalityCoversIdentityNullTypeAndSize() {
        MPSState state = new MPSState(2, 4);
        MPSState otherSize = new MPSState(3, 4);

        assertTrue(state.equals(state));
        assertFalse(state.equals(null));
        assertFalse(state.equals("not a state"));
        assertFalse(state.equals(otherSize));
        assertTrue(state.equals(state.copy()));
        assertFalse(state.equals(state.applyGate(SingleQubitGate.createPauliX(new Qubit(0)))));
    }

    @Test
    void equalityUsesTensorComparisonForLargeStates() {
        MPSState first = new MPSState(25, 1);
        MPSState second = new MPSState(25, 1);

        assertEquals(first, second);
        assertEquals(first.hashCode(), second.hashCode());
        assertFalse(first.equals(null));
        assertFalse(first.equals("not a state"));

        MPSState flipped = first.applyGate(SingleQubitGate.createPauliX(new Qubit(12)));
        assertNotEquals(first, flipped);

        MPSState entangled = new MPSState(25, 2)
            .applyGate(SingleQubitGate.createHadamard(new Qubit(0)))
            .applyGate(MultiQubitGate.createCNOT(new Qubit(0), new Qubit(1)));
        assertNotEquals(first, entangled);
        assertNotEquals(entangled, first);
    }

    @Test
    void copyPreservesTruncationErrorAndRepresentation() {
        MPSState state = runMps(2, 1, List.of(
            SingleQubitGate.createHadamard(new Qubit(0)),
            MultiQubitGate.createCNOT(new Qubit(0), new Qubit(1))));

        MPSState copy = state.copy();

        assertNotSame(state, copy);
        assertEquals(state.getTruncationError(), copy.getTruncationError(), 0.0);
        assertEquals(state, copy);
        assertEquals(1.0, totalProbability(copy), EPS);
    }

    @Test
    void toStringDescribesState() {
        MPSState state = new MPSState(3, 7);
        String text = state.toString();

        assertTrue(text.contains("3 qubits"));
        assertTrue(text.contains("maxBondDimension=7"));
        assertTrue(text.contains("truncationError"));
    }

    // ------------------------------------------------------- tensor product

    @Test
    void tensorProductRejectsForeignStates() {
        MPSState state = new MPSState(1, 2);

        assertThrows(IllegalArgumentException.class,
            () -> state.tensorProduct(StateVector.createZeroState(1)));
    }

    @Test
    void tensorProductUsesLargerBondLimit() {
        MPSState left = new MPSState(2, 1);
        MPSState right = new MPSState(2, 5);

        MPSState product = (MPSState) left.tensorProduct(right);

        assertEquals(4, product.getNumQubits());
        assertEquals(5, product.getMaxBondDimension());
        assertEquals(1.0, product.getProbability(0), EPS);
        assertArrayEquals(new int[]{1, 1, 1, 1, 1}, product.getBondDimensions());
    }

    @Test
    void tensorProductOfEntangledFactorsMatchesStateVector() {
        MPSState left = runMps(2, 8, List.of(
            SingleQubitGate.createHadamard(new Qubit(0)),
            MultiQubitGate.createCNOT(new Qubit(0), new Qubit(1))));
        MPSState right = runMps(2, 8, List.of(
            SingleQubitGate.createPauliX(new Qubit(0))));

        MPSState product = (MPSState) left.tensorProduct(right);

        assertEquals(1.0, totalProbability(product), EPS);
        assertEquals(0.5, product.getProbability(2), EPS);
        assertEquals(0.5, product.getProbability(14), EPS);
    }

    @Test
    void normalizationReturnsThisState() {
        MPSState state = new MPSState(2, 4);

        assertEquals(state, state.normalize());
    }

    // ------------------------------------------------------------- ComplexSvd

    @Test
    void complexSvdRejectsNull() {
        assertThrows(IllegalArgumentException.class, () -> ComplexSvd.decompose(null));
    }

    @Test
    void complexSvdHandlesZeroMatrix() {
        ComplexMatrix zero = ComplexMatrix.zero(2, 2);

        ComplexSvd.Result result = ComplexSvd.decompose(zero);

        assertArrayEquals(new double[]{0.0, 0.0}, result.getSingularValues(), 0.0);
        assertReconstruction(zero, result);
    }

    @Test
    void complexSvdHandlesZeroRectangularMatrices() {
        for (int[] size : new int[][]{{3, 5}, {5, 3}, {2, 6}, {6, 2}}) {
            ComplexMatrix zero = ComplexMatrix.zero(size[0], size[1]);
            ComplexSvd.Result result = ComplexSvd.decompose(zero);

            assertEquals(Math.min(size[0], size[1]), result.getSingularValues().length);
            assertReconstruction(zero, result);
        }
    }

    @Test
    void complexSvdHandlesDegenerateSingularValues() {
        for (int size = 1; size <= 4; size++) {
            ComplexMatrix identity = ComplexMatrix.identity(size);
            ComplexSvd.Result result = ComplexSvd.decompose(identity);

            assertEquals(size, result.getSingularValues().length);
            for (double value : result.getSingularValues()) {
                assertEquals(1.0, value, EPS);
            }
            assertReconstruction(identity, result);
        }

        ComplexMatrix doubled = ComplexMatrix.identity(3).scale(2.0);
        assertReconstruction(doubled, ComplexSvd.decompose(doubled));
    }

    @Test
    void complexSvdHandlesRankDeficientMatrices() {
        Complex[][] entries = new Complex[3][3];
        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 3; column++) {
                entries[row][column] = new Complex(row + 1.0, column * 0.5);
            }
        }
        ComplexMatrix matrix = new ComplexMatrix(entries);

        ComplexSvd.Result result = ComplexSvd.decompose(matrix);

        assertReconstruction(matrix, result);
        assertTrue(result.getSingularValues()[2] < 1e-9);
    }

    @Test
    void complexSvdHandlesSingleDimensionMatrices() {
        ComplexMatrix scalar = new ComplexMatrix(new Complex[][]{{new Complex(0.0, 3.0)}});
        ComplexSvd.Result scalarResult = ComplexSvd.decompose(scalar);
        assertEquals(3.0, scalarResult.getSingularValues()[0], EPS);
        assertReconstruction(scalar, scalarResult);

        Complex[][] rowData = {{new Complex(1.0, 1.0), new Complex(2.0, 0.0),
            new Complex(0.0, -1.0)}};
        ComplexMatrix row = new ComplexMatrix(rowData);
        assertReconstruction(row, ComplexSvd.decompose(row));

        Complex[][] columnData = {{new Complex(1.0, 0.0)}, {new Complex(0.0, 2.0)}};
        ComplexMatrix column = new ComplexMatrix(columnData);
        assertReconstruction(column, ComplexSvd.decompose(column));
    }

    @Test
    void complexSvdHandlesRepeatedSingularValuesAndDependencies() {
        ComplexMatrix ones = new ComplexMatrix(new double[][]{{1.0, 1.0}, {1.0, 1.0}});
        assertReconstruction(ones, ComplexSvd.decompose(ones));

        Complex[][] diagonal = new Complex[4][4];
        double[] diagonalValues = {3.0, 3.0, 1.0, 1.0};
        for (int row = 0; row < 4; row++) {
            for (int column = 0; column < 4; column++) {
                diagonal[row][column] = row == column
                    ? new Complex(diagonalValues[row], 0.0) : Complex.ZERO;
            }
        }
        ComplexMatrix repeated = new ComplexMatrix(diagonal);
        assertReconstruction(repeated, ComplexSvd.decompose(repeated));

        Complex[][] dependent = new Complex[3][3];
        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 3; column++) {
                dependent[row][column] = new Complex(1.0, 0.0);
            }
        }
        ComplexMatrix identicalRows = new ComplexMatrix(dependent);
        ComplexSvd.Result dependentResult = ComplexSvd.decompose(identicalRows);
        assertEquals(0.0, dependentResult.getSingularValues()[2], 1e-9);
        assertReconstruction(identicalRows, dependentResult);
    }

    @Test
    void complexSvdHandlesZeroMatricesWithEverySmallShape() {
        int[][] shapes = {{1, 1}, {1, 2}, {2, 1}, {1, 5}, {5, 1}, {4, 8}, {8, 4}};
        for (int[] shape : shapes) {
            ComplexMatrix zero = ComplexMatrix.zero(shape[0], shape[1]);
            ComplexSvd.Result result = ComplexSvd.decompose(zero);
            assertEquals(Math.min(shape[0], shape[1]), result.getSingularValues().length);
            assertReconstruction(zero, result);
        }
    }

    @Test
    void complexSvdHandlesIllConditionedMatrices() {
        int size = 8;
        Complex[][] entries = new Complex[size][size];
        for (int row = 0; row < size; row++) {
            for (int column = 0; column < size; column++) {
                entries[row][column] = new Complex(1.0 / (row + column + 1.0), 0.0);
            }
        }
        ComplexMatrix hilbert = new ComplexMatrix(entries);
        assertReconstruction(hilbert, ComplexSvd.decompose(hilbert));
    }

    @Test
    void complexSvdReconstructsRandomTallAndWideMatrices() {
        Random random = new Random(42);
        for (int[] size : new int[][]{{2, 5}, {5, 2}, {4, 4}, {1, 4}, {4, 1}}) {
            Complex[][] entries = new Complex[size[0]][size[1]];
            for (int row = 0; row < size[0]; row++) {
                for (int column = 0; column < size[1]; column++) {
                    entries[row][column] =
                        new Complex(random.nextGaussian(), random.nextGaussian());
                }
            }
            ComplexMatrix matrix = new ComplexMatrix(entries);
            assertReconstruction(matrix, ComplexSvd.decompose(matrix));
        }
    }

    @Test
    void complexSvdResultReturnsIndependentCopies() {
        ComplexMatrix matrix = new ComplexMatrix(new Complex[][]{
            {new Complex(1.0, 0.0), new Complex(0.0, 1.0)},
            {new Complex(0.0, 0.0), new Complex(2.0, 0.0)}});
        ComplexSvd.Result result = ComplexSvd.decompose(matrix);

        double[] values = result.getSingularValues();
        values[0] = -1.0;
        assertNotEquals(-1.0, result.getSingularValues()[0], 0.0);

        ComplexMatrix u = result.getU();
        u.set(0, 0, new Complex(99.0, 99.0));
        assertNotEquals(new Complex(99.0, 99.0), result.getU().get(0, 0));

        ComplexMatrix v = result.getV();
        assertEquals(result.getVAdjoint().adjoint(), v);
    }

    // -------------------------------------------------- TensorNetworkStrategy

    @Test
    void strategyConstructorAndMetadata() {
        assertThrows(IllegalArgumentException.class, () -> new TensorNetworkStrategy(0));
        assertThrows(IllegalArgumentException.class, () -> new TensorNetworkStrategy(-2));

        TensorNetworkStrategy strategy = new TensorNetworkStrategy(4);
        strategy.initialize();
        strategy.cleanup();

        assertEquals("TensorNetworkSimulator", strategy.getStrategyName());
        assertEquals(100, strategy.getMaxSupportedQubits());
        assertFalse(strategy.canSimulateNoise());
    }

    @Test
    void strategyCreateInitialStateRejectsInvalidQubitCount() {
        TensorNetworkStrategy strategy = new TensorNetworkStrategy(4);

        assertThrows(IllegalArgumentException.class,
            () -> strategy.createInitialState(0));

        QuantumState state = strategy.createInitialState(3);
        assertTrue(state instanceof MPSState);
        assertEquals(3, state.getNumQubits());
        assertEquals(1.0, state.getProbability(0), EPS);
    }

    @Test
    void strategyApplyGateRejectsForeignState() {
        TensorNetworkStrategy strategy = new TensorNetworkStrategy(4);
        QuantumState foreign = StateVector.createZeroState(1);

        assertThrows(IllegalArgumentException.class,
            () -> strategy.applyGate(foreign,
                SingleQubitGate.createPauliX(new Qubit(0)), new Qubit[]{new Qubit(0)}));
    }

    @Test
    void strategyApplyGateTransformsMps() {
        TensorNetworkStrategy strategy = new TensorNetworkStrategy(4);
        QuantumState state = strategy.createInitialState(1);

        QuantumState flipped = strategy.applyGate(state,
            SingleQubitGate.createPauliX(new Qubit(0)), new Qubit[]{new Qubit(0)});

        assertEquals(1.0, flipped.getProbability(1), EPS);
        assertEquals(1.0, state.getProbability(0), EPS);
    }

    @Test
    void strategyRejectsNoise() {
        TensorNetworkStrategy strategy = new TensorNetworkStrategy(4);
        QuantumState state = strategy.createInitialState(1);

        assertThrows(UnsupportedOperationException.class,
            () -> strategy.applyNoise(state, null, new Qubit[]{new Qubit(0)}));
    }

    @Test
    void strategyMeasureRequiresMpsAndMeasuresBellState() {
        TensorNetworkStrategy strategy = new TensorNetworkStrategy(8);
        assertThrows(IllegalArgumentException.class,
            () -> strategy.measure(StateVector.createZeroState(1), new Qubit[]{new Qubit(0)}, 5));

        QuantumState bell = strategy.applyGate(
            strategy.applyGate(strategy.createInitialState(2),
                SingleQubitGate.createHadamard(new Qubit(0)), new Qubit[]{new Qubit(0)}),
            MultiQubitGate.createCNOT(new Qubit(0), new Qubit(1)),
            new Qubit[]{new Qubit(0), new Qubit(1)});

        MeasurementResult result = strategy.measure(bell,
            new Qubit[]{new Qubit(0), new Qubit(1)}, 200);

        assertEquals(200, result.getTotalShots());
        assertEquals(0, result.getCount(1));
        assertEquals(0, result.getCount(2));
    }

    @Test
    void strategyCloneRequiresMpsAndCopiesIndependently() {
        TensorNetworkStrategy strategy = new TensorNetworkStrategy(4);
        assertThrows(IllegalArgumentException.class,
            () -> strategy.cloneState(StateVector.createZeroState(1)));

        MPSState state = (MPSState) strategy.createInitialState(2);
        MPSState clone = (MPSState) strategy.cloneState(state);

        assertNotSame(state, clone);
        assertEquals(state, clone);
    }

    @Test
    void strategyFidelityRequiresMpsAndSameSize() {
        TensorNetworkStrategy strategy = new TensorNetworkStrategy(4);
        MPSState zero = (MPSState) strategy.createInitialState(1);
        MPSState one = zero.applyGate(SingleQubitGate.createPauliX(new Qubit(0)));
        MPSState twoQubits = (MPSState) strategy.createInitialState(2);

        assertThrows(IllegalArgumentException.class,
            () -> strategy.calculateFidelity(StateVector.createZeroState(1), zero));
        assertThrows(IllegalArgumentException.class,
            () -> strategy.calculateFidelity(zero, StateVector.createZeroState(1)));
        assertThrows(IllegalArgumentException.class,
            () -> strategy.calculateFidelity(zero, twoQubits));

        assertEquals(1.0, strategy.calculateFidelity(zero, zero), EPS);
        assertEquals(0.0, strategy.calculateFidelity(zero, one), EPS);
    }

    @Test
    void strategyEntropyRequiresMpsAndSkipsZeroProbabilities() {
        TensorNetworkStrategy strategy = new TensorNetworkStrategy(4);
        MPSState zero = (MPSState) strategy.createInitialState(1);
        MPSState plus = zero.applyGate(SingleQubitGate.createHadamard(new Qubit(0)));

        assertThrows(IllegalArgumentException.class,
            () -> strategy.calculateEntropy(StateVector.createZeroState(1)));
        assertEquals(0.0, strategy.calculateEntropy(zero), EPS);
        assertEquals(1.0, strategy.calculateEntropy(plus), EPS);
    }

    // -------------------------------------------------------------- helpers

    private static Complex[][][][] zeroTensors(int numQubits) {
        Complex[][][][] tensors = new Complex[numQubits][1][2][1];
        for (int qubit = 0; qubit < numQubits; qubit++) {
            tensors[qubit][0][0][0] = Complex.ONE;
            tensors[qubit][0][1][0] = Complex.ZERO;
        }
        return tensors;
    }

    private static void assertReconstruction(ComplexMatrix matrix, ComplexSvd.Result result) {
        double[] singularValues = result.getSingularValues();
        ComplexMatrix u = result.getU();
        ComplexMatrix vAdjoint = result.getVAdjoint();
        assertEquals(matrix.rows(), u.rows());
        assertEquals(singularValues.length, u.cols());
        assertEquals(singularValues.length, vAdjoint.rows());
        assertEquals(matrix.cols(), vAdjoint.cols());

        double residual = 0.0;
        for (int row = 0; row < matrix.rows(); row++) {
            for (int column = 0; column < matrix.cols(); column++) {
                Complex sum = Complex.ZERO;
                for (int term = 0; term < singularValues.length; term++) {
                    sum = sum.add(u.get(row, term)
                        .multiply(new Complex(singularValues[term], 0.0))
                        .multiply(vAdjoint.get(term, column)));
                }
                residual = Math.max(residual,
                    matrix.get(row, column).subtract(sum).abs());
            }
        }
        assertTrue(residual < 1e-9, "SVD residual " + residual);
    }

    private static MPSState runMps(int numQubits, int maxBondDimension,
            List<QuantumGate> gates) {
        TensorNetworkStrategy strategy = new TensorNetworkStrategy(maxBondDimension);
        QuantumState state = strategy.createInitialState(numQubits);
        for (QuantumGate gate : gates) {
            state = strategy.applyGate(state, gate, gate.getTargetQubits());
        }
        return (MPSState) state;
    }

    private static StateVector runStateVector(int numQubits, List<QuantumGate> gates) {
        QuantumState state = StateVector.createZeroState(numQubits);
        for (QuantumGate gate : gates) {
            state = state.applyOperation(gate);
        }
        return (StateVector) state;
    }

    private static double maxDifference(Complex[] first, Complex[] second) {
        double maximum = 0.0;
        for (int index = 0; index < first.length; index++) {
            maximum = Math.max(maximum, first[index].subtract(second[index]).abs());
        }
        return maximum;
    }

    private static double totalProbability(MPSState state) {
        double total = 0.0;
        for (double probability : state.getProbabilities()) {
            total += probability;
        }
        return total;
    }
}
