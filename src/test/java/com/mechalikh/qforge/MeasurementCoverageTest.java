package com.mechalikh.qforge;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.mechalikh.qforge.math.Complex;
import com.mechalikh.qforge.math.ComplexMatrix;
import com.mechalikh.qforge.measurement.MeasurementHelper;
import com.mechalikh.qforge.measurement.MeasurementOperator;
import com.mechalikh.qforge.measurement.MeasurementResult;
import com.mechalikh.qforge.measurement.Observable;
import com.mechalikh.qforge.measurement.Observable.ObservableType;
import com.mechalikh.qforge.state.DensityMatrix;
import com.mechalikh.qforge.state.QuantumState;
import com.mechalikh.qforge.state.Qubit;
import com.mechalikh.qforge.state.StateVector;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class MeasurementCoverageTest {
    private static final double EPS = 1e-9;

    private static Qubit q(int index) {
        return new Qubit(index, "q" + index);
    }

    private static Qubit[] qubits(int count) {
        Qubit[] result = new Qubit[count];
        for (int i = 0; i < count; i++) {
            result[i] = q(i);
        }
        return result;
    }

    private static StateVector basis(int index, int numQubits) {
        return StateVector.fromComputationalBasis(index, numQubits);
    }

    private static StateVector plusState() {
        double s = 1 / Math.sqrt(2);
        return new StateVector(new Complex[]{
            new Complex(s, 0), new Complex(s, 0)}, 1);
    }

    private static StateVector bellState() {
        double s = 1 / Math.sqrt(2);
        return new StateVector(new Complex[]{
            new Complex(s, 0), Complex.ZERO, Complex.ZERO, new Complex(s, 0)}, 2);
    }

    private static MeasurementResult counted(String name, int numQubits,
                                             Map<Integer, Integer> counts, int shots) {
        return new MeasurementResult(name, qubits(numQubits), counts, shots);
    }

    // ------------------------------------------------------------------
    // MeasurementResult
    // ------------------------------------------------------------------

    @Test
    void countsConstructorDerivesProbabilitiesAndStatistics() {
        Map<Integer, Integer> counts = new LinkedHashMap<>();
        counts.put(0, 30);
        counts.put(1, 70);
        MeasurementResult result = new MeasurementResult("Z", qubits(1), counts, 100);

        assertEquals("Z", result.getObservableName());
        assertEquals(1, result.getNumQubits());
        assertEquals(100, result.getTotalShots());
        assertArrayEquals(new double[]{0.3, 0.7}, result.getOutcomeProbabilities(), EPS);
        assertArrayEquals(new double[]{0.0, 1.0}, result.getOutcomeEigenvalues(), EPS);
        assertEquals(2, result.getOutcomeCounts().size());
        assertEquals(0.7, result.getExpectationValue(), EPS);
        assertEquals(0.21, result.getVariance(), EPS);
        assertEquals(Math.sqrt(0.21), result.getStandardDeviation(), EPS);
        assertEquals(Math.sqrt(0.0021), result.getStandardError(), EPS);
        assertEquals(1, result.getMostFrequentOutcome());
        assertEquals(70, result.getCount(1));
        assertEquals(0, result.getCount(7));
        assertEquals(0.3, result.getProbability(0), EPS);
        assertEquals(1.0, result.getEigenvalue(1), EPS);
        assertEquals(0.8812908992306927, result.getEntropy(), 1e-9);

        Map<String, Double> bitStrings = result.getBitStringProbabilities();
        assertEquals(2, bitStrings.size());
        assertEquals(0.3, bitStrings.get("0"), EPS);
        assertEquals(0.7, bitStrings.get("1"), EPS);
        assertTrue(result.getTimestamp() > 0);
    }

    @Test
    void explicitEigenvaluesDriveExpectationAndVariance() {
        MeasurementResult result = new MeasurementResult(
            "Z", qubits(1), Map.of(0, 50, 1, 50), 100, new double[]{1.0, -1.0});

        assertEquals(0.0, result.getExpectationValue(), EPS);
        assertEquals(1.0, result.getVariance(), EPS);
        assertEquals(1.0, result.getStandardDeviation(), EPS);
        assertEquals(0.1, result.getStandardError(), EPS);
        assertEquals(-1.0, result.getEigenvalue(1), EPS);
        assertEquals(0.196, result.getConfidenceInterval(0.95), EPS);
    }

    @Test
    void invalidEigenvalueLengthIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> new MeasurementResult(
            "Z", qubits(1), Map.of(0, 1), 1, new double[]{1.0, 2.0, 3.0}));
    }

    @Test
    void rawOutcomeConstructorTabulatesCounts() {
        Qubit[] measured = qubits(2);
        MeasurementResult result = new MeasurementResult(measured, new int[]{0, 0, 3, 3, 0}, null);

        assertEquals("CustomMeasurement", result.getObservableName());
        assertEquals(5, result.getTotalShots());
        assertEquals(3, result.getCount(0));
        assertEquals(2, result.getCount(3));
        assertEquals(0, result.getCount(1));
        assertArrayEquals(new double[]{0.6, 0.0, 0.0, 0.4},
            result.getOutcomeProbabilities(), EPS);
        assertArrayEquals(new double[]{0.0, 1.0, 2.0, 3.0},
            result.getOutcomeEigenvalues(), EPS);
        assertEquals(1.2, result.getExpectationValue(), EPS);
        assertEquals(2.16, result.getVariance(), EPS);
        assertEquals(0, result.getMostFrequentOutcome());
    }

    @Test
    void providedProbabilitiesOverrideSampleFrequencies() {
        Qubit[] measured = qubits(2);
        double[] supplied = {0.5, 0.0, 0.0, 0.5};
        MeasurementResult result = new MeasurementResult(
            "Bell", measured, new int[]{0, 0, 0, 3}, supplied, new double[]{0.0, 1.0, 1.0, 2.0});

        assertEquals(3, result.getCount(0));
        assertEquals(1, result.getCount(3));
        assertEquals(1.0, result.getExpectationValue(), EPS);
        assertEquals(1.0, result.getVariance(), EPS);
        assertEquals(0.5, result.getProbability(0), EPS);

        supplied[0] = 0.9;
        assertEquals(0.5, result.getProbability(0), EPS);
    }

    @Test
    void combineSumsCountsAndShots() {
        MeasurementResult first = counted("A", 1, Map.of(0, 10), 10);
        MeasurementResult second = counted("B", 1, Map.of(1, 30), 30);

        MeasurementResult combined = first.combine(second);

        assertEquals("A+B", combined.getObservableName());
        assertEquals(40, combined.getTotalShots());
        assertEquals(10, combined.getCount(0));
        assertEquals(30, combined.getCount(1));
        assertEquals(0.25, combined.getProbability(0), EPS);
        assertEquals(0.75, combined.getExpectationValue(), EPS);
        assertEquals(10, first.getTotalShots());
        assertEquals(0, first.getCount(1));
        assertEquals(30, second.getTotalShots());
    }

    @Test
    void combineRejectsDifferentQubitsOrNull() {
        MeasurementResult oneQubit = counted("A", 1, Map.of(0, 1), 1);
        MeasurementResult twoQubits = counted("B", 2, Map.of(0, 1), 1);

        assertThrows(IllegalArgumentException.class, () -> oneQubit.combine(twoQubits));
        assertThrows(NullPointerException.class, () -> oneQubit.combine(null));
    }

    @Test
    void histogramRendersBarsAndOmittedLine() {
        Map<Integer, Integer> counts = new LinkedHashMap<>();
        counts.put(0, 50);
        counts.put(1, 30);
        counts.put(2, 20);
        MeasurementResult result = counted("Hist", 2, counts, 100);

        String limited = result.toHistogramString(1);
        assertTrue(limited.contains("Measurement Result: Hist"));
        assertTrue(limited.contains("Total shots: 100"));
        assertTrue(limited.contains("Expectation: 0.7000"));
        assertTrue(limited.contains("Variance: 0.6100"));
        assertTrue(limited.contains("00 | 50    | 0.5000 | ####################"));
        assertTrue(limited.contains("... and 2 more outcomes"));

        String all = result.toHistogramString(5);
        assertFalse(all.contains("more outcomes"));
        assertTrue(all.contains("01 | 30    | 0.3000 | ############"));
        assertTrue(all.contains("10 | 20    | 0.2000 | ########"));
    }

    @Test
    void toStringAndConfidenceInterval() {
        MeasurementResult result = counted("Z", 1, Map.of(0, 30, 1, 70), 100);

        assertEquals("MeasurementResult[observable='Z', qubits=1, shots=100, "
            + "expectation=0.7000 +/- 0.0458]", result.toString());
        assertEquals(0.08981848, result.getConfidenceInterval(0.95), 1e-8);
        assertEquals(result.getConfidenceInterval(0.95),
            result.getConfidenceInterval(0.5), EPS);

        assertThrows(IllegalArgumentException.class, () -> result.getConfidenceInterval(0.0));
        assertThrows(IllegalArgumentException.class, () -> result.getConfidenceInterval(1.0));
        assertThrows(IllegalArgumentException.class, () -> result.getConfidenceInterval(-0.5));
        assertThrows(IllegalArgumentException.class, () -> result.getConfidenceInterval(1.5));
    }

    @Test
    void zeroShotResultIsEmptyButConsistent() {
        MeasurementResult empty = new MeasurementResult(qubits(2), new int[0], null);

        assertEquals(0, empty.getTotalShots());
        assertTrue(empty.getOutcomeCounts().isEmpty());
        assertArrayEquals(new double[]{0.0, 0.0, 0.0, 0.0},
            empty.getOutcomeProbabilities(), EPS);
        assertEquals(0.0, empty.getExpectationValue(), EPS);
        assertEquals(0.0, empty.getVariance(), EPS);
        assertEquals(0.0, empty.getStandardError(), EPS);
        assertEquals(-1, empty.getMostFrequentOutcome());
        assertEquals(0.0, empty.getEntropy(), EPS);
        assertTrue(empty.getBitStringProbabilities().isEmpty());
        assertTrue(empty.toHistogramString(3).contains("Total shots: 0"));
    }

    @Test
    void accessorsReturnDefensiveCopies() {
        Qubit[] measured = qubits(1);
        Map<Integer, Integer> counts = new HashMap<>();
        counts.put(0, 4);
        double[] eigenvalues = {1.0, -1.0};
        MeasurementResult result = new MeasurementResult("M", measured, counts, 4, eigenvalues);

        measured[0] = q(1);
        counts.put(1, 99);
        eigenvalues[0] = 9.0;

        assertEquals(0, result.getMeasuredQubits()[0].getIndex());
        assertFalse(result.getOutcomeCounts().containsKey(1));
        assertEquals(1.0, result.getEigenvalue(0), EPS);

        Qubit[] qubitCopy = result.getMeasuredQubits();
        qubitCopy[0] = q(2);
        assertEquals(0, result.getMeasuredQubits()[0].getIndex());

        double[] probabilityCopy = result.getOutcomeProbabilities();
        probabilityCopy[0] = 0.25;
        assertEquals(1.0, result.getProbability(0), EPS);

        double[] eigenvalueCopy = result.getOutcomeEigenvalues();
        eigenvalueCopy[1] = 7.0;
        assertEquals(-1.0, result.getEigenvalue(1), EPS);

        assertThrows(UnsupportedOperationException.class,
            () -> result.getOutcomeCounts().put(1, 1));
    }

    @Test
    void indexOutOfRangeAccessorsThrow() {
        MeasurementResult result = counted("Z", 1, Map.of(0, 1), 1);

        assertThrows(IndexOutOfBoundsException.class, () -> result.getEigenvalue(-1));
        assertThrows(IndexOutOfBoundsException.class, () -> result.getEigenvalue(2));
        assertThrows(IndexOutOfBoundsException.class, () -> result.getProbability(-1));
        assertThrows(IndexOutOfBoundsException.class, () -> result.getProbability(2));
    }

    // ------------------------------------------------------------------
    // Observable factories, parsing and metadata
    // ------------------------------------------------------------------

    @Test
    void pauliFactoriesExposeMetadataAndSpectra() {
        Qubit q0 = q(0);

        Observable x = Observable.pauliX(q0);
        assertEquals("X_q0", x.getName());
        assertEquals(ObservableType.PAULI, x.getType());
        assertEquals(1, x.getNumQubits());
        assertFalse(x.isComputationalBasis());
        assertArrayEquals(new double[]{-1.0, 1.0}, x.getOutcomeEigenvalues(), EPS);
        assertArrayEquals(new double[]{-1.0, 1.0}, x.getEigenvalues(), EPS);

        Observable y = Observable.pauliY(q0);
        assertEquals("Y_q0", y.getName());
        assertFalse(y.isComputationalBasis());
        assertArrayEquals(new double[]{-1.0, 1.0}, y.getOutcomeEigenvalues(), EPS);

        Observable z = Observable.pauliZ(q0);
        assertEquals("Z_q0", z.getName());
        assertTrue(z.isComputationalBasis());
        assertArrayEquals(new double[]{1.0, -1.0}, z.getOutcomeEigenvalues(), EPS);

        Observable identity = Observable.identity(q0);
        assertEquals("I_q0", identity.getName());
        assertEquals(ObservableType.IDENTITY, identity.getType());
        assertTrue(identity.isComputationalBasis());
        assertArrayEquals(new double[]{1.0, 1.0}, identity.getOutcomeEigenvalues(), EPS);

        Qubit[] two = qubits(2);
        Observable twoQubitIdentity = Observable.identity(two);
        assertEquals("I_2", twoQubitIdentity.getName());
        assertEquals(2, twoQubitIdentity.getNumQubits());
        assertEquals(ComplexMatrix.identity(4), twoQubitIdentity.getOperator());
    }

    @Test
    void multiQubitPauliFactoriesTensor() {
        Qubit[] two = qubits(2);

        Observable xx = Observable.pauliX(two);
        assertEquals("X_q0xX_q1", xx.getName());
        assertEquals(ObservableType.COMPOSITE, xx.getType());
        assertEquals(2, xx.getNumQubits());
        assertEquals(ComplexMatrix.pauliX().tensorProduct(ComplexMatrix.pauliX()),
            xx.getOperator());

        Observable yy = Observable.pauliY(two);
        assertEquals("Y_q0xY_q1", yy.getName());
        assertEquals(ComplexMatrix.pauliY().tensorProduct(ComplexMatrix.pauliY()),
            yy.getOperator());

        Observable zz = Observable.pauliZ(two);
        assertEquals("Z_q0xZ_q1", zz.getName());
        assertTrue(zz.isComputationalBasis());
        assertArrayEquals(new double[]{1.0, -1.0, -1.0, 1.0},
            zz.getOutcomeEigenvalues(), EPS);

        assertThrows(ArrayIndexOutOfBoundsException.class,
            () -> Observable.pauliX(new Qubit[0]));
        assertThrows(ArrayIndexOutOfBoundsException.class,
            () -> Observable.pauliY(new Qubit[0]));
        assertThrows(ArrayIndexOutOfBoundsException.class,
            () -> Observable.pauliZ(new Qubit[0]));
    }

    @Test
    void zMeasurementMarginalsFollowQubitSignificance() {
        StateVector bell = bellState();
        Qubit q0 = q(0);
        Qubit q1 = q(1);

        assertArrayEquals(new double[]{0.5, 0.5},
            Observable.pauliZ(q0).getOutcomeProbabilities(bell), EPS);
        assertArrayEquals(new double[]{0.5, 0.5},
            Observable.pauliZ(q1).getOutcomeProbabilities(bell), EPS);
        assertEquals(0.0, Observable.pauliZ(q0).measure(bell), EPS);

        StateVector ten = basis(2, 2);
        assertArrayEquals(new double[]{0.0, 1.0},
            Observable.pauliZ(q0).getOutcomeProbabilities(ten), EPS);
        assertArrayEquals(new double[]{1.0, 0.0},
            Observable.pauliZ(q1).getOutcomeProbabilities(ten), EPS);
        assertEquals(-1.0, Observable.pauliZ(q0).measure(ten), EPS);
        assertEquals(1.0, Observable.pauliZ(q1).measure(ten), EPS);
    }

    @Test
    void nonDiagonalMeasurementUsesEigenbasis() {
        StateVector plus = plusState();
        Observable x = Observable.pauliX(q(0));
        Observable y = Observable.pauliY(q(0));

        assertArrayEquals(new double[]{0.0, 1.0}, x.getOutcomeProbabilities(plus), EPS);
        assertEquals(1.0, x.measure(plus), EPS);
        assertArrayEquals(new double[]{0.5, 0.5}, y.getOutcomeProbabilities(plus), EPS);
        assertEquals(0.0, y.measure(plus), EPS);

        double[] probabilities = x.getOutcomeProbabilities(bellState());
        assertEquals(1.0, probabilities[0] + probabilities[1], EPS);
        assertArrayEquals(new double[]{0.5, 0.5}, probabilities, EPS);
    }

    @Test
    void measurementSamplingStaysInsideTheDistribution() {
        StateVector plus = plusState();
        Observable z = Observable.pauliZ(q(0));

        MeasurementResult sampled = z.measureMultiple(plus, 200);
        assertEquals(200, sampled.getTotalShots());
        assertEquals(200, sampled.getCount(0) + sampled.getCount(1));
        assertArrayEquals(new double[]{0.5, 0.5}, sampled.getOutcomeProbabilities(), EPS);
        assertArrayEquals(new double[]{1.0, -1.0}, sampled.getOutcomeEigenvalues(), EPS);

        int outcome = z.sampleOutcome(plus);
        assertTrue(outcome == 0 || outcome == 1);
        assertEquals(0, Observable.pauliZ(q(0)).sampleOutcome(basis(0, 1)));
        assertArrayEquals(new double[]{0.5, 0.5}, plus.getProbabilities(), EPS);
    }

    @Test
    void invalidObservableConstructionIsRejected() {
        Qubit q0 = q(0);
        ComplexMatrix nonHermitian = new ComplexMatrix(new double[][]{{0, 1}, {0, 0}});

        assertThrows(IllegalArgumentException.class, () -> new Observable(
            "bad", nonHermitian, new Qubit[]{q0}, ObservableType.CUSTOM));
        assertThrows(IllegalArgumentException.class, () -> new Observable(
            "dim", ComplexMatrix.identity(4), new Qubit[]{q0}, ObservableType.CUSTOM));
        assertThrows(NullPointerException.class, () -> new Observable(
            "null", null, new Qubit[]{q0}, ObservableType.CUSTOM));
        assertThrows(NullPointerException.class, () -> new Observable(
            "nullq", ComplexMatrix.identity(2), null, ObservableType.CUSTOM));
    }

    @Test
    void measurementRejectsTargetsOutsideState() {
        Observable outside = Observable.pauliZ(q(2));
        StateVector twoQubits = basis(0, 2);

        assertThrows(IllegalArgumentException.class,
            () -> outside.getOutcomeProbabilities(twoQubits));
        assertThrows(IllegalArgumentException.class, () -> outside.measure(twoQubits));
        assertThrows(IllegalArgumentException.class,
            () -> outside.measureMultiple(twoQubits, 5));
    }

    @Test
    void collapseValidatesOutcomeAndProbability() {
        Qubit q0 = q(0);
        Observable z = Observable.pauliZ(q0);
        StateVector zero = basis(0, 1);

        assertThrows(IllegalArgumentException.class, () -> z.collapse(zero, 2));
        assertThrows(IllegalArgumentException.class, () -> z.collapse(zero, -1));
        assertThrows(IllegalStateException.class, () -> z.collapse(zero, 1));

        StateVector collapsed = (StateVector) z.collapse(zero, 0);
        assertEquals(1.0, collapsed.getProbability(0), EPS);
        assertEquals(0.0, collapsed.getProbability(1), EPS);
    }

    @Test
    void nonDiagonalCollapseProjectsOntoEigenvector() {
        Qubit q0 = q(0);
        Observable x = Observable.pauliX(q0);
        StateVector zero = basis(0, 1);

        StateVector minus = (StateVector) x.collapse(zero, 0);
        assertEquals(0.5, minus.getProbability(0), EPS);
        assertEquals(-1.0, x.measure(minus), EPS);

        StateVector plus = (StateVector) x.collapse(zero, 1);
        assertEquals(1.0, x.measure(plus), EPS);

        DensityMatrix collapsedDensity = (DensityMatrix) x.collapse(zero.toDensityMatrix(), 0);
        assertEquals(-1.0, x.measure(collapsedDensity), EPS);
    }

    @Test
    void repeatedTargetsAreRejectedOnTheEigenbasisPath() {
        Qubit q0 = q(0);
        Observable repeated = new Observable(
            "repeated",
            ComplexMatrix.pauliX().tensorProduct(ComplexMatrix.pauliX()),
            new Qubit[]{q0, q0},
            ObservableType.CUSTOM);

        assertThrows(IllegalArgumentException.class,
            () -> repeated.getOutcomeProbabilities(bellState()));
    }

    @Test
    void projectorFactoriesAndValidation() {
        Qubit q0 = q(0);
        Observable z = Observable.pauliZ(q0);

        // Eigenvectors follow the ascending eigenvalue order of Z, so index 0 is |1>.
        Observable projectorZero = z.getProjector(0);
        assertEquals("Z_q0_Projector_0", projectorZero.getName());
        assertEquals(ObservableType.PROJECTOR, projectorZero.getType());
        assertArrayEquals(new double[]{0.0, 1.0}, projectorZero.getOutcomeEigenvalues(), EPS);
        assertEquals(0.0, projectorZero.measure(basis(0, 1)), EPS);
        assertEquals(1.0, projectorZero.measure(basis(1, 1)), EPS);

        Observable projectorOne = z.getProjector(1);
        assertArrayEquals(new double[]{1.0, 0.0}, projectorOne.getOutcomeEigenvalues(), EPS);
        assertEquals(0.0, projectorOne.measure(basis(1, 1)), EPS);
        assertEquals(1.0, projectorOne.measure(basis(0, 1)), EPS);
        assertThrows(IndexOutOfBoundsException.class, () -> z.getProjector(2));
        assertThrows(IndexOutOfBoundsException.class, () -> z.getProjector(-1));

        ComplexMatrix diagonal = new ComplexMatrix(2, 2);
        diagonal.set(0, 0, Complex.ONE);
        Observable supplied = Observable.projector("P", diagonal, new Qubit[]{q0});
        assertEquals("P", supplied.getName());
        assertEquals(ObservableType.PROJECTOR, supplied.getType());
        assertArrayEquals(new double[]{1.0, 0.0}, supplied.getOutcomeEigenvalues(), EPS);
    }

    @Test
    void tensorProductAndPartialTrace() {
        Qubit[] two = qubits(2);
        Observable zz = Observable.pauliZ(two);
        assertEquals(1.0, zz.measure(basis(3, 2)), EPS);
        assertEquals(-1.0, zz.measure(basis(2, 2)), EPS);

        Observable zi = Observable.fromString("ZI", two);
        Observable reduced = zi.partialTrace(new int[]{0});
        assertEquals("Z_q0xI_q1_partialTrace", reduced.getName());
        assertEquals(1, reduced.getNumQubits());
        assertEquals(0, reduced.getTargetQubits()[0].getIndex());
        assertArrayEquals(new double[]{2.0, -2.0}, reduced.getOutcomeEigenvalues(), EPS);
        assertSame(zi, zi.partialTrace(new int[]{0, 1}));

        Observable tracedFirst = zi.partialTrace(new int[]{1});
        assertEquals(1, tracedFirst.getTargetQubits()[0].getIndex());
        assertArrayEquals(new double[]{0.0, 0.0}, tracedFirst.getOutcomeEigenvalues(), EPS);
    }

    @Test
    void partialTraceValidation() {
        Observable zi = Observable.fromString("ZI", qubits(2));

        assertThrows(IllegalArgumentException.class, () -> zi.partialTrace(null));
        assertThrows(IllegalArgumentException.class, () -> zi.partialTrace(new int[0]));
        assertThrows(IllegalArgumentException.class, () -> zi.partialTrace(new int[]{2}));
        assertThrows(IllegalArgumentException.class, () -> zi.partialTrace(new int[]{-1}));
        assertThrows(IllegalArgumentException.class, () -> zi.partialTrace(new int[]{0, 0}));
    }

    @Test
    void fromStringParsesExpressions() {
        Qubit[] three = qubits(3);
        Observable parsed = Observable.fromString(" X \u2297 Z , I ", three);
        ComplexMatrix expected = ComplexMatrix.pauliX()
            .tensorProduct(ComplexMatrix.pauliZ())
            .tensorProduct(ComplexMatrix.identity(2));

        assertEquals(expected, parsed.getOperator());
        assertEquals("X_q0xZ_q1xI_q2", parsed.getName());
        assertEquals(3, parsed.getNumQubits());

        Observable separated = Observable.fromString("X x Z", qubits(2));
        assertEquals(ComplexMatrix.pauliX().tensorProduct(ComplexMatrix.pauliZ()),
            separated.getOperator());

        Observable lowerCase = Observable.fromString("z", new Qubit[]{q(0)});
        assertEquals(ComplexMatrix.pauliZ(), lowerCase.getOperator());
    }

    @Test
    void fromStringRejectsInvalidInput() {
        Qubit[] three = qubits(3);

        assertThrows(IllegalArgumentException.class, () -> Observable.fromString(null, three));
        assertThrows(IllegalArgumentException.class, () -> Observable.fromString("XYZ", null));
        assertThrows(IllegalArgumentException.class,
            () -> Observable.fromString("XYZ", new Qubit[0]));
        assertThrows(IllegalArgumentException.class, () -> Observable.fromString("XY", three));
        assertThrows(IllegalArgumentException.class,
            () -> Observable.fromString("A", new Qubit[]{q(0)}));
    }

    @Test
    void observableEqualityAndDefensiveCopies() {
        Observable z = Observable.pauliZ(new Qubit(0, "a"));
        Observable same = Observable.pauliZ(new Qubit(0, "a"));

        assertEquals(z, same);
        assertEquals(z.hashCode(), same.hashCode());
        assertEquals(z, z);
        assertNotEquals(z, Observable.pauliX(new Qubit(0)));
        assertNotEquals(z, null);
        assertNotEquals(z, "Z");
        assertNotEquals(z, new Observable(z.getName(), z.getOperator(),
            z.getTargetQubits(), ObservableType.CUSTOM));

        ComplexMatrix operator = z.getOperator();
        operator.set(0, 0, new Complex(5.0, 0));
        assertEquals(1.0, z.getOperator().get(0, 0).real(), EPS);

        Qubit[] targets = z.getTargetQubits();
        targets[0] = new Qubit(1);
        assertEquals(0, z.getTargetQubits()[0].getIndex());

        assertTrue(z.toString().contains("Observable[name='Z_a'"));
        assertTrue(z.toString().contains("type=PAULI"));
    }

    @Test
    void observableTypeDescriptions() {
        assertEquals("Pauli operator", ObservableType.PAULI.getDescription());
        assertEquals("Projection operator", ObservableType.PROJECTOR.getDescription());
        assertEquals("Hamiltonian", ObservableType.HAMILTONIAN.getDescription());
        assertEquals("Density operator", ObservableType.DENSITY.getDescription());
        assertEquals("Identity operator", ObservableType.IDENTITY.getDescription());
        assertEquals("Composite observable", ObservableType.COMPOSITE.getDescription());
        assertEquals("Custom observable", ObservableType.CUSTOM.getDescription());
        assertEquals(ObservableType.PROJECTOR, ObservableType.valueOf("PROJECTOR"));
    }

    // ------------------------------------------------------------------
    // MeasurementHelper
    // ------------------------------------------------------------------

    @Test
    void measurementHelperMeasuresAllQubits() {
        StateVector ten = basis(2, 2);
        MeasurementResult result = MeasurementHelper.measureAllQubits(ten, 20);

        assertEquals("ComputationalBasis", result.getObservableName());
        assertEquals(20, result.getTotalShots());
        assertEquals(20, result.getCount(2));
        assertEquals(0, result.getCount(0));
        assertEquals(2, result.getMeasuredQubits().length);
        assertArrayEquals(new double[]{0.0, 1.0, 2.0, 3.0},
            result.getOutcomeEigenvalues(), EPS);
        assertEquals(2.0, result.getExpectationValue(), EPS);
        assertEquals(1.0, ten.getProbability(2), EPS);
    }

    @Test
    void measurementHelperComputesMarginals() {
        StateVector bell = bellState();
        Qubit q0 = q(0);
        Qubit q1 = q(1);

        @SuppressWarnings("unchecked")
        Map<String, Object> first = MeasurementHelper
            .measureInComputationalBasisWithProbabilities(bell, new Qubit[]{q0}, 64);
        assertArrayEquals(new double[]{0.5, 0.5},
            (double[]) first.get("probabilities"), EPS);
        assertEquals(64, ((int[]) first.get("outcomes")).length);
        @SuppressWarnings("unchecked")
        Map<Integer, Integer> counts = (Map<Integer, Integer>) first.get("counts");
        assertEquals(64, counts.get(0) + counts.get(1));
        for (int outcome : (int[]) first.get("outcomes")) {
            assertTrue(outcome == 0 || outcome == 1);
        }

        @SuppressWarnings("unchecked")
        Map<String, Object> second = MeasurementHelper
            .measureInComputationalBasisWithProbabilities(bell, new Qubit[]{q1}, 32);
        assertArrayEquals(new double[]{0.5, 0.5},
            (double[]) second.get("probabilities"), EPS);
    }

    @Test
    void measurementHelperSingleQubitMarginals() {
        StateVector zeroOne = basis(1, 2);

        MeasurementResult first = MeasurementHelper.measureSingleQubit(zeroOne, q(0), 15);
        MeasurementResult second = MeasurementHelper.measureSingleQubit(zeroOne, q(1), 15);

        assertEquals(15, first.getCount(0));
        assertEquals(0, first.getCount(1));
        assertEquals(15, second.getCount(1));
        assertEquals(0, second.getCount(0));
        assertEquals(1.0, zeroOne.getProbability(1), EPS);
    }

    @Test
    void measurementHelperProbabilityMap() {
        Map<String, Double> bellProbabilities = MeasurementHelper.getProbabilities(bellState());
        assertEquals(2, bellProbabilities.size());
        assertEquals(0.5, bellProbabilities.get("00"), EPS);
        assertEquals(0.5, bellProbabilities.get("11"), EPS);

        Map<String, Double> zeroProbabilities =
            MeasurementHelper.getProbabilities(basis(0, 3));
        assertEquals(1, zeroProbabilities.size());
        assertEquals(1.0, zeroProbabilities.get("000"), EPS);
    }

    @Test
    void measurementHelperAcceptsEmptyTargets() {
        MeasurementResult result = MeasurementHelper.measureInComputationalBasis(
            basis(0, 1), new Qubit[0], 5);

        assertEquals(0, result.getNumQubits());
        assertEquals(1, result.getOutcomeProbabilities().length);
        assertEquals(1.0, result.getProbability(0), EPS);
        assertEquals(5, result.getCount(0));
        assertEquals(0.0, result.getExpectationValue(), EPS);
    }

    @Test
    void measurementHelperZeroShotsProduceEmptyResults() {
        MeasurementResult empty = MeasurementHelper.measureInComputationalBasis(
            basis(0, 1), new Qubit[]{q(0)}, 0);
        assertEquals(0, empty.getTotalShots());
        assertTrue(empty.getOutcomeCounts().isEmpty());
        assertEquals(-1, empty.getMostFrequentOutcome());
        assertEquals(0.0, empty.getStandardError(), EPS);
        assertEquals(0.0, empty.getProbability(0), EPS);

        @SuppressWarnings("unchecked")
        Map<String, Object> raw = MeasurementHelper
            .measureInComputationalBasisWithProbabilities(
                basis(0, 1), new Qubit[]{q(0)}, 0);
        assertEquals(0, ((int[]) raw.get("outcomes")).length);
        assertTrue(((Map<?, ?>) raw.get("counts")).isEmpty());
        assertArrayEquals(new double[]{1.0, 0.0},
            (double[]) raw.get("probabilities"), EPS);
    }

    @Test
    void measurementHelperHonoursQubitOrder() {
        StateVector ten = basis(2, 2);

        MeasurementResult forward = MeasurementHelper.measureInComputationalBasis(
            ten, new Qubit[]{q(0), q(1)}, 10);
        MeasurementResult reversed = MeasurementHelper.measureInComputationalBasis(
            ten, new Qubit[]{q(1), q(0)}, 10);

        assertEquals(10, forward.getCount(2));
        assertEquals(10, reversed.getCount(1));
        assertEquals(0, reversed.getCount(2));
    }

    // ------------------------------------------------------------------
    // MeasurementOperator
    // ------------------------------------------------------------------

    @Test
    void measurementOperatorMetadata() {
        Qubit q0 = q(0);
        Observable z = Observable.pauliZ(q0);
        MeasurementOperator operator = new MeasurementOperator("m", z, 7, false);

        assertEquals("m", operator.getName());
        assertEquals(7, operator.getShots());
        assertFalse(operator.isDestructive());
        assertTrue(operator.isComputationalBasis());
        assertEquals(1.0, operator.getDuration(), EPS);
        assertFalse(operator.isUnitary());
        assertFalse(operator.isNoise());
        assertTrue(operator.isMeasurement());
        assertEquals(0, operator.getControlQubits().length);
        assertEquals(0, operator.getTargetQubits()[0].getIndex());
        assertSame(z, operator.getObservable());
        assertThrows(UnsupportedOperationException.class, operator::inverse);

        MeasurementOperator defaulted = new MeasurementOperator(z, 3);
        assertEquals("Z_q0_Measurement", defaulted.getName());
        assertTrue(defaulted.isDestructive());
        assertEquals(3, defaulted.getShots());

        MeasurementOperator equivalent = new MeasurementOperator(
            "m", Observable.pauliZ(new Qubit(0, "q0")), 7, false);
        assertEquals(operator, equivalent);
        assertEquals(operator.hashCode(), equivalent.hashCode());
        assertNotEquals(operator, new MeasurementOperator("m", z, 8, false));
        assertNotEquals(operator, null);
        assertEquals(operator, operator);
        assertTrue(operator.toString().contains("name='m'"));
        assertTrue(operator.toString().contains("shots=7"));
        assertTrue(operator.toString().contains("destructive=false"));
    }

    @Test
    void nonDestructiveApplyKeepsStateInstance() {
        StateVector plus = plusState();
        MeasurementOperator operator = new MeasurementOperator(
            "Z", Observable.pauliZ(q(0)), 10, false);

        assertSame(plus, operator.apply(plus));
        assertArrayEquals(new double[]{0.5, 0.5}, plus.getProbabilities(), EPS);
    }

    @Test
    void destructiveApplyCollapsesState() {
        StateVector zero = basis(0, 1);
        MeasurementOperator operator = MeasurementOperator.computationalBasis(
            new Qubit[]{q(0)}, 10);

        QuantumState collapsed = operator.apply(zero);
        assertNotSame(zero, collapsed);
        assertEquals(1.0, collapsed.getProbability(0), EPS);
        assertEquals(0.0, collapsed.getProbability(1), EPS);
    }

    @Test
    void measureDoesNotCollapseTheState() {
        StateVector plus = plusState();
        MeasurementResult result = new MeasurementOperator(
            "Z", Observable.pauliZ(q(0)), 50, true).measure(plus);

        assertEquals(50, result.getTotalShots());
        assertEquals(1.0, result.getProbability(0) + result.getProbability(1), EPS);
        assertArrayEquals(new double[]{0.5, 0.5}, plus.getProbabilities(), EPS);
    }

    @Test
    void measureAndCollapseWithZeroShotsStillPicksAnOutcome() {
        MeasurementOperator operator = new MeasurementOperator(
            "Z", Observable.pauliZ(q(0)), 0, true);

        MeasurementOperator.MeasurementOutcome outcome =
            operator.measureAndCollapse(basis(0, 1));

        assertEquals(0, outcome.getOutcome());
        assertEquals(0, outcome.getResult().getTotalShots());
        assertTrue(outcome.getResult().getOutcomeCounts().isEmpty());
        assertEquals(1.0, outcome.getResult().getProbability(0), EPS);
        assertEquals(1.0, outcome.getCollapsedState().getProbability(0), EPS);
    }

    @Test
    void measurementOperatorFactories() {
        Qubit q0 = q(0);
        MeasurementOperator basisOperator = MeasurementOperator.computationalBasis(
            qubits(3), 4);
        assertEquals("ComputationalBasisMeasurement", basisOperator.getName());
        assertEquals(4, basisOperator.getShots());
        assertTrue(basisOperator.isDestructive());
        assertTrue(basisOperator.isComputationalBasis());
        assertEquals(3, basisOperator.getTargetQubits().length);
        assertEquals("Z_q0xZ_q1xZ_q2", basisOperator.getObservable().getName());

        MeasurementOperator x = MeasurementOperator.pauliXMeasurement(q0, 5);
        assertEquals("X_q0_Measurement", x.getName());
        assertTrue(x.isDestructive());
        assertFalse(x.isComputationalBasis());

        MeasurementOperator y = MeasurementOperator.pauliYMeasurement(q0, 6);
        assertEquals("Y_q0_Measurement", y.getName());
        assertFalse(y.isComputationalBasis());

        MeasurementOperator z = MeasurementOperator.pauliZMeasurement(q0, 7);
        assertEquals("Z_q0_Measurement", z.getName());
        assertTrue(z.isComputationalBasis());

        assertThrows(ArrayIndexOutOfBoundsException.class,
            () -> MeasurementOperator.computationalBasis(new Qubit[0], 1));
    }

    @Test
    void measurementOutcomeHolderExposesComponents() {
        MeasurementResult result = counted("M", 1, Map.of(0, 1), 1);
        StateVector state = basis(0, 1);

        MeasurementOperator.MeasurementOutcome outcome =
            new MeasurementOperator.MeasurementOutcome(result, state, 1);

        assertSame(result, outcome.getResult());
        assertSame(state, outcome.getCollapsedState());
        assertEquals(1, outcome.getOutcome());
    }

    @Test
    void nonDiagonalDestructiveMeasurementCollapsesOntoEigenvector() {
        Observable x = Observable.pauliX(q(0));
        MeasurementOperator operator = MeasurementOperator.pauliXMeasurement(q(0), 5);

        MeasurementOperator.MeasurementOutcome outcome =
            operator.measureAndCollapse(plusState());

        assertEquals(1, outcome.getOutcome());
        assertEquals(1.0, x.measure(outcome.getCollapsedState()), EPS);
        assertEquals(5, outcome.getResult().getTotalShots());
        assertEquals(1.0, outcome.getResult().getExpectationValue(), EPS);
    }

    @Test
    void bellMeasurementOnlyProducesCorrelatedOutcomes() {
        Qubit[] two = qubits(2);
        MeasurementOperator operator = MeasurementOperator.computationalBasis(two, 100);

        MeasurementOperator.MeasurementOutcome outcome =
            operator.measureAndCollapse(bellState());
        MeasurementResult result = outcome.getResult();

        assertEquals(0, result.getCount(1));
        assertEquals(0, result.getCount(2));
        assertEquals(100, result.getCount(0) + result.getCount(3));
        assertEquals(1.0, result.getExpectationValue(), EPS);
        assertArrayEquals(new double[]{1.0, -1.0, -1.0, 1.0},
            result.getOutcomeEigenvalues(), EPS);

        StateVector collapsed = (StateVector) outcome.getCollapsedState();
        double bothZero = collapsed.getProbability(0);
        double bothOne = collapsed.getProbability(3);
        assertEquals(1.0, bothZero + bothOne, EPS);
        assertEquals(1.0, outcome.getOutcome() == 0 ? bothZero : bothOne, EPS);
    }

    @Test
    void observableTargetListIsCopied() {
        Qubit[] targets = {q(0)};
        Observable z = new Observable("copy", ComplexMatrix.pauliZ(), targets,
            ObservableType.PAULI);

        targets[0] = q(1);
        assertEquals(0, z.getTargetQubits()[0].getIndex());
        assertTrue(List.of(ObservableType.values()).contains(z.getType()));
    }
}
