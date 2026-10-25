package com.mechalikh.qforge;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.mechalikh.qforge.math.Complex;
import com.mechalikh.qforge.math.ComplexMatrix;
import com.mechalikh.qforge.noise.AmplitudeDampingNoise;
import com.mechalikh.qforge.operations.MultiQubitGate;
import com.mechalikh.qforge.operations.SingleQubitGate;
import com.mechalikh.qforge.state.ComplexVector;
import com.mechalikh.qforge.state.DensityMatrix;
import com.mechalikh.qforge.state.QuantumState;
import com.mechalikh.qforge.state.Qubit;
import com.mechalikh.qforge.state.StateVector;
import java.util.Random;
import org.junit.jupiter.api.Test;

class StateCoverageTest {
    private static final double EPS = 1e-9;

    private static void assertComplex(double real, double imag, Complex actual, double delta) {
        assertEquals(real, actual.real(), delta, "real part");
        assertEquals(imag, actual.imag(), delta, "imaginary part");
    }

    private static double maxAbsDifference(ComplexMatrix a, ComplexMatrix b) {
        assertEquals(a.rows(), b.rows());
        assertEquals(a.cols(), b.cols());
        double max = 0.0;
        for (int i = 0; i < a.rows(); i++) {
            for (int j = 0; j < a.cols(); j++) {
                max = Math.max(max, a.get(i, j).subtract(b.get(i, j)).abs());
            }
        }
        return max;
    }

    private static StateVector bellState() {
        return StateVector.fromComputationalBasis(0, 2)
            .applySingleQubitGate(ComplexMatrix.hadamard(), 0)
            .applyGate(MultiQubitGate.createCNOT(new Qubit(0), new Qubit(1)));
    }

    // ----------------------------------------------------------- ComplexVector

    @Test
    void vectorConstructorsAndLengths() {
        ComplexVector zero = new ComplexVector(3);
        assertEquals(3, zero.getLength());
        assertTrue(zero.isZero());

        ComplexVector fromComplex = new ComplexVector(new Complex[]{Complex.ONE, Complex.I});
        assertEquals(2, fromComplex.getLength());
        assertComplex(0, 1, fromComplex.get(1), 0.0);

        ComplexVector fromReal = new ComplexVector(new double[]{1.5, -2.5});
        assertEquals(2, fromReal.getLength());
        assertComplex(1.5, 0, fromReal.get(0), 0.0);
        assertComplex(-2.5, 0, fromReal.get(1), 0.0);

        // The double-array constructor deliberately accepts an empty array.
        assertEquals(0, new ComplexVector(new double[0]).getLength());

        assertThrows(IllegalArgumentException.class, () -> new ComplexVector(0));
        assertThrows(IllegalArgumentException.class, () -> new ComplexVector((Complex[]) null));
        assertThrows(IllegalArgumentException.class, () -> new ComplexVector(new Complex[0]));
    }

    @Test
    void vectorGetAndSetCopyValues() {
        Complex[] source = {new Complex(1, 2), new Complex(3, 4)};
        ComplexVector vector = new ComplexVector(source);
        source[0] = new Complex(9, 9);
        assertComplex(1, 2, vector.get(0), 0.0);

        vector.set(0, new Complex(5, -6));
        assertComplex(5, -6, vector.get(0), 0.0);
        assertNotSame(vector.get(0), vector.get(0));

        vector.set(1, 7.0, 8.0);
        assertComplex(7, 8, vector.get(1), 0.0);

        assertArrayEquals(new double[]{5, 7}, vector.getRealParts(), 0.0);
        assertArrayEquals(new double[]{-6, 8}, vector.getImaginaryParts(), 0.0);
        assertFalse(vector.isZero());

        assertThrows(IndexOutOfBoundsException.class, () -> vector.get(2));
        assertThrows(IndexOutOfBoundsException.class, () -> vector.get(-1));
        assertThrows(IndexOutOfBoundsException.class, () -> vector.set(2, Complex.ONE));
    }

    @Test
    void vectorArithmeticScalesAndSubtracts() {
        ComplexVector a = new ComplexVector(new Complex[]{new Complex(1, 1), new Complex(2, -2)});
        ComplexVector b = new ComplexVector(new Complex[]{Complex.ONE, Complex.I});

        ComplexVector sum = a.add(b);
        assertComplex(2, 1, sum.get(0), EPS);
        assertComplex(2, -1, sum.get(1), EPS);

        ComplexVector difference = a.subtract(b);
        assertComplex(0, 1, difference.get(0), EPS);
        assertComplex(2, -3, difference.get(1), EPS);
        assertTrue(a.subtract(a).isZero());

        ComplexVector scaled = a.scale(new Complex(0, 1));
        assertComplex(-1, 1, scaled.get(0), EPS);
        assertComplex(2, 2, scaled.get(1), EPS);
        assertComplex(2, -2, a.scale(1.0).get(1), EPS);

        // Operands are unchanged by the arithmetic methods.
        assertComplex(1, 1, a.get(0), EPS);

        assertThrows(IllegalArgumentException.class, () -> a.add(new ComplexVector(3)));
        assertThrows(IllegalArgumentException.class, () -> a.dotProduct(new ComplexVector(1)));
    }

    @Test
    void vectorDotProductNormAndNormalization() {
        ComplexVector a = new ComplexVector(new Complex[]{new Complex(1, 1), Complex.I});
        ComplexVector b = new ComplexVector(new Complex[]{Complex.ONE, Complex.ZERO});
        assertEquals(1.0, a.dotProduct(b).real(), EPS);
        assertEquals(-1.0, a.dotProduct(b).imag(), EPS);
        // Conjugate-linear in the first argument.
        assertComplex(0, -1, a.dotProduct(new ComplexVector(new Complex[]{Complex.ZERO, Complex.ONE})), EPS);

        ComplexVector real = new ComplexVector(new double[]{3, 4});
        assertEquals(5.0, real.norm(), EPS);
        ComplexVector normalized = real.normalize();
        assertEquals(1.0, normalized.norm(), EPS);
        assertEquals(0.6, normalized.get(0).real(), EPS);
        assertEquals(0.8, normalized.get(1).real(), EPS);

        assertThrows(ArithmeticException.class, () -> new ComplexVector(2).normalize());
    }

    @Test
    void vectorTensorConjugateCopyAndEquality() {
        ComplexVector a = new ComplexVector(new double[]{1, 2});
        ComplexVector b = new ComplexVector(new double[]{3, 4});

        ComplexVector tensor = a.tensorProduct(b);
        assertEquals(4, tensor.getLength());
        assertArrayEquals(new double[]{3, 4, 6, 8}, tensor.getRealParts(), 0.0);

        ComplexVector complex = new ComplexVector(new Complex[]{new Complex(1, 2), new Complex(-3, 4)});
        ComplexVector conjugate = complex.conjugate();
        assertComplex(1, -2, conjugate.get(0), 0.0);
        assertComplex(-3, -4, conjugate.get(1), 0.0);

        ComplexVector copy = complex.copy();
        assertTrue(copy.equals(complex));
        copy.set(0, new Complex(9, 9));
        assertComplex(1, 2, complex.get(0), 0.0);
        assertFalse(copy.equals(complex));

        assertTrue(complex.equals(complex));
        assertFalse(complex.equals(new ComplexVector(new Complex[]{new Complex(1, 2)})));
        assertFalse(complex.equals(null));
        assertEquals(complex.hashCode(), new ComplexVector(new Complex[]{
            new Complex(1, 2), new Complex(-3, 4)}).hashCode());

        assertTrue(new ComplexVector(new Complex[]{new Complex(1e-11, 0)}).isZero());
        assertTrue(complex.toString().contains("ComplexVector[length=2]"));
    }

    // ------------------------------------------------------------- StateVector

    @Test
    void zeroStateHasASingleUnitAmplitude() {
        StateVector state = StateVector.createZeroState(2);
        assertEquals(2, state.getNumQubits());
        assertEquals(4, state.getDimension());
        assertEquals(1.0, state.getProbability(0), 0.0);
        for (int i = 1; i < 4; i++) {
            assertEquals(0.0, state.getProbability(i), 0.0);
        }
        assertComplex(1, 0, state.getAmplitude(0), 0.0);
        assertEquals(1.0, state.getNorm(), EPS);
        assertTrue(state.isPure());
        assertEquals(1.0, state.getPurity(), 0.0);
        assertArrayEquals(new double[]{1, 0, 0, 0}, state.getProbabilities(), 0.0);

        assertThrows(IllegalArgumentException.class, () -> StateVector.createZeroState(0));
    }

    @Test
    void plusStateIsAUniformSuperposition() {
        StateVector state = StateVector.createPlusState(3);
        assertEquals(8, state.getDimension());
        for (int i = 0; i < 8; i++) {
            assertEquals(0.125, state.getProbability(i), EPS);
            assertEquals(1.0 / Math.sqrt(8), state.getAmplitude(i).real(), EPS);
        }
        assertEquals(1.0, state.getNorm(), EPS);
    }

    @Test
    void basisStatesValidateTheirIndex() {
        StateVector state = StateVector.fromComputationalBasis(2, 2);
        assertEquals(1.0, state.getProbability(2), 0.0);
        assertEquals(0.0, state.getProbability(0), 0.0);

        assertThrows(IllegalArgumentException.class, () -> StateVector.fromComputationalBasis(4, 2));
        assertThrows(IllegalArgumentException.class, () -> StateVector.fromComputationalBasis(-1, 2));
        assertThrows(IndexOutOfBoundsException.class, () -> state.getAmplitude(4));
        assertThrows(IndexOutOfBoundsException.class, () -> state.getProbability(-1));
    }

    @Test
    void constructorNormalizesAndCopiesAmplitudes() {
        StateVector state = new StateVector(new Complex[]{
            new Complex(3, 0), new Complex(4, 0)}, 1);
        assertEquals(1.0, state.getNorm(), EPS);
        assertComplex(0.6, 0, state.getAmplitude(0), EPS);
        assertComplex(0.8, 0, state.getAmplitude(1), EPS);

        Complex[] amplitudes = state.getAmplitudes();
        amplitudes[0] = new Complex(9, 9);
        assertComplex(0.6, 0, state.getAmplitude(0), EPS);

        StateVector alreadyNormalized = new StateVector(new Complex[]{
            new Complex(1.0 / Math.sqrt(2), 0), new Complex(1.0 / Math.sqrt(2), 0)}, 1);
        assertEquals(0.5, alreadyNormalized.getProbability(0), EPS);

        assertThrows(IllegalArgumentException.class,
            () -> new StateVector(new Complex[]{Complex.ONE}, 2));
        assertThrows(IllegalArgumentException.class,
            () -> new StateVector(new Complex[]{Complex.ONE}, 0));
    }

    @Test
    void singleQubitGatesRespectBitOrdering() {
        StateVector zero = StateVector.createZeroState(2);
        assertEquals(1.0, zero.applySingleQubitGate(ComplexMatrix.pauliX(), 0).getProbability(2), EPS);
        assertEquals(1.0, zero.applySingleQubitGate(ComplexMatrix.pauliX(), 1).getProbability(1), EPS);

        StateVector oneQubit = zero.applySingleQubitGate(ComplexMatrix.hadamard(), 0);
        assertEquals(0.5, oneQubit.getProbability(0), EPS);
        assertEquals(0.5, oneQubit.getProbability(2), EPS);

        assertThrows(IllegalArgumentException.class,
            () -> zero.applySingleQubitGate(ComplexMatrix.pauliX(), 2));
        assertThrows(IllegalArgumentException.class,
            () -> zero.applySingleQubitGate(ComplexMatrix.pauliX(), -1));
    }

    @Test
    void gateApplicationThroughOperationsProducesExpectedStates() {
        Qubit q0 = new Qubit(0);
        Qubit q1 = new Qubit(1);

        StateVector flipped = StateVector.createZeroState(1)
            .applyGate(SingleQubitGate.createPauliX(q0));
        assertEquals(1.0, flipped.getProbability(1), EPS);

        QuantumState viaOperation = StateVector.createZeroState(1)
            .applyOperation(SingleQubitGate.createHadamard(q0));
        assertEquals(0.5, viaOperation.getProbability(0), EPS);
        assertEquals(0.5, viaOperation.getProbability(1), EPS);

        StateVector bell = bellState();
        assertEquals(0.5, bell.getProbability(0), EPS);
        assertEquals(0.5, bell.getProbability(3), EPS);
        assertEquals(0.0, bell.getProbability(1), EPS);

        // Noise models are not supported by the pure-state backend.
        assertThrows(UnsupportedOperationException.class,
            () -> StateVector.createZeroState(1)
                .applyOperation(new AmplitudeDampingNoise(0.1, new Qubit(0))));
    }

    @Test
    void tensorProductOfBasisStatesHasOneAmplitude() {
        StateVector zero = StateVector.fromComputationalBasis(0, 1);
        StateVector one = StateVector.fromComputationalBasis(1, 1);

        StateVector product = (StateVector) zero.tensorProduct(one);
        assertEquals(2, product.getNumQubits());
        assertEquals(4, product.getDimension());
        assertArrayEquals(new double[]{0, 1, 0, 0}, product.getProbabilities(), 0.0);
        assertComplex(1, 0, product.getAmplitude(1), 0.0);

        StateVector doubled = (StateVector) one.tensorProduct(one);
        assertArrayEquals(new double[]{0, 0, 0, 1}, doubled.getProbabilities(), 0.0);

        assertThrows(IllegalArgumentException.class,
            () -> zero.tensorProduct(DensityMatrix.createZeroState(1)));
    }

    @Test
    void partialTraceOfPureStatesYieldsReducedDensityMatrices() {
        StateVector bell = bellState();
        DensityMatrix reduced = (DensityMatrix) bell.partialTrace(new int[]{0});
        assertEquals(1, reduced.getNumQubits());
        assertEquals(2, reduced.getMatrix().rows());
        assertArrayEquals(new double[]{0.5, 0.5}, reduced.getProbabilities(), EPS);
        assertEquals(0.5, reduced.getPurity(), EPS);
        assertEquals(1.0, reduced.getVonNeumannEntropy(), EPS);

        DensityMatrix otherQubit = (DensityMatrix) bell.partialTrace(new int[]{1});
        assertArrayEquals(new double[]{0.5, 0.5}, otherQubit.getProbabilities(), EPS);

        // Product state |01> keeps a definite value on either subsystem.
        StateVector product = StateVector.fromComputationalBasis(1, 2);
        assertArrayEquals(new double[]{1, 0},
            ((DensityMatrix) product.partialTrace(new int[]{0})).getProbabilities(), EPS);
        assertArrayEquals(new double[]{0, 1},
            ((DensityMatrix) product.partialTrace(new int[]{1})).getProbabilities(), EPS);

        // Keeping both qubits reproduces the original density matrix.
        DensityMatrix full = (DensityMatrix) bell.partialTrace(new int[]{0, 1});
        assertEquals(0.0, maxAbsDifference(full.getMatrix(), bell.toDensityMatrix().getMatrix()), EPS);

        assertThrows(IllegalArgumentException.class, () -> bell.partialTrace(new int[]{2}));
        assertThrows(IllegalArgumentException.class, () -> bell.partialTrace(new int[]{0, 0}));
        assertThrows(IllegalArgumentException.class, () -> bell.partialTrace(new int[0]));
        assertThrows(IllegalArgumentException.class, () -> bell.partialTrace(null));
    }

    @Test
    void collapseToBasisStateSelectsTheOutcome() {
        StateVector bell = bellState();

        StateVector zeroOutcome = (StateVector) bell.collapseToBasisState(new int[]{0}, 0);
        assertEquals(1.0, zeroOutcome.getProbability(0), EPS);
        assertEquals(0.0, zeroOutcome.getProbability(3), EPS);

        StateVector oneOutcome = (StateVector) bell.collapseToBasisState(new int[]{0}, 1);
        assertEquals(1.0, oneOutcome.getProbability(3), EPS);
        assertEquals(0.0, oneOutcome.getProbability(0), EPS);

        // Collapsing the second qubit of the Bell state gives the same pairing.
        assertArrayEquals(new double[]{1, 0, 0, 0},
            ((StateVector) bell.collapseToBasisState(new int[]{1}, 0)).getProbabilities(), EPS);

        assertThrows(IllegalStateException.class,
            () -> StateVector.createZeroState(1).collapseToBasisState(new int[]{0}, 1));
        assertThrows(IllegalArgumentException.class, () -> bell.collapseToBasisState(new int[]{0}, 2));
        assertThrows(IllegalArgumentException.class, () -> bell.collapseToBasisState(new int[]{0}, -1));
    }

    @Test
    void collapseToEigenvectorProjectsOntoTheGivenDirection() {
        StateVector zero = StateVector.createZeroState(1);
        Complex half = new Complex(1.0 / Math.sqrt(2), 0);

        StateVector plus = (StateVector) zero.collapseToEigenvector(new int[]{0},
            new Complex[]{half, half});
        assertEquals(0.5, plus.getProbability(0), EPS);
        assertEquals(0.5, plus.getProbability(1), EPS);

        // The eigenvector need not be normalized.
        StateVector unnormalized = (StateVector) zero.collapseToEigenvector(new int[]{0},
            new Complex[]{Complex.ONE, Complex.ONE});
        assertEquals(0.5, unnormalized.getProbability(0), EPS);
        assertEquals(0.5, unnormalized.getProbability(1), EPS);

        // Projecting |0> onto |1> has zero probability.
        assertThrows(IllegalStateException.class,
            () -> zero.collapseToEigenvector(new int[]{0}, new Complex[]{Complex.ZERO, Complex.ONE}));
        assertThrows(IllegalArgumentException.class,
            () -> zero.collapseToEigenvector(new int[]{0}, new Complex[]{Complex.ZERO, Complex.ZERO}));
        assertThrows(IllegalArgumentException.class,
            () -> zero.collapseToEigenvector(new int[]{0}, new Complex[]{Complex.ONE}));
    }

    @Test
    void expectationValuesAndVariancesOfPauliObservables() {
        StateVector zero = StateVector.createZeroState(1);
        StateVector plus = zero.applySingleQubitGate(ComplexMatrix.hadamard(), 0);

        assertEquals(1.0, zero.getExpectationValue(ComplexMatrix.pauliZ()), EPS);
        assertEquals(0.0, plus.getExpectationValue(ComplexMatrix.pauliZ()), EPS);
        assertEquals(1.0, plus.getExpectationValue(ComplexMatrix.pauliX()), EPS);
        assertEquals(0.0, zero.getExpectationValue(ComplexMatrix.pauliX()), EPS);

        assertEquals(0.0, zero.getVariance(ComplexMatrix.pauliZ()), EPS);
        assertEquals(1.0, plus.getVariance(ComplexMatrix.pauliZ()), EPS);

        // <ZZ> = 1 on the Bell state.
        ComplexMatrix zz = ComplexMatrix.pauliZ().tensorProduct(ComplexMatrix.pauliZ());
        assertEquals(1.0, bellState().getExpectationValue(zz), EPS);

        assertThrows(IllegalArgumentException.class,
            () -> StateVector.createZeroState(2).getExpectationValue(ComplexMatrix.pauliZ()));
        assertThrows(IllegalArgumentException.class,
            () -> zero.getExpectationValue(new ComplexMatrix(2, 3)));
        assertThrows(IllegalArgumentException.class,
            () -> zero.getVariance(new ComplexMatrix(3, 3)));
    }

    @Test
    void innerProductsAndDensityMatrixConversion() {
        StateVector zero = StateVector.createZeroState(1);
        StateVector one = StateVector.fromComputationalBasis(1, 1);
        Complex half = new Complex(1.0 / Math.sqrt(2), 0);
        StateVector plus = new StateVector(new Complex[]{half, half}, 1);
        StateVector minus = new StateVector(new Complex[]{half, new Complex(-half.real(), 0)}, 1);

        assertComplex(1, 0, zero.innerProduct(zero), EPS);
        assertComplex(0, 0, zero.innerProduct(one), EPS);
        assertComplex(0, 0, plus.innerProduct(minus), EPS);
        assertComplex(1, 0, plus.innerProduct(plus), EPS);
        assertThrows(IllegalArgumentException.class,
            () -> plus.innerProduct(StateVector.createZeroState(2)));

        DensityMatrix density = plus.toDensityMatrix();
        assertEquals(2, density.getMatrix().rows());
        assertEquals(1.0, density.getMatrix().trace().real(), EPS);
        assertEquals(0.5, density.getProbability(0), EPS);
        assertEquals(1.0, density.getPurity(), EPS);
        assertTrue(density.isPure());
        assertTrue(DensityMatrix.fromStateVector(plus).equals(density));
    }

    @Test
    void normalizeReturnsTheSameInstance() {
        StateVector state = StateVector.createZeroState(1);
        assertSame(state, state.normalize());
        assertEquals(1.0, state.getNorm(), EPS);

        StateVector copy = state.copy();
        assertNotSame(state, copy);
        assertTrue(copy.equals(state));
        assertEquals(state.hashCode(), copy.hashCode());
        assertTrue(state.toString().contains("StateVector[1 qubits"));
    }

    @Test
    void krausTrajectoriesSelectOneOperatorForPureStates() {
        StateVector plus = StateVector.createZeroState(1)
            .applySingleQubitGate(ComplexMatrix.hadamard(), 0);
        ComplexMatrix projectZero = new ComplexMatrix(new double[][]{{1, 0}, {0, 0}});
        ComplexMatrix projectOne = new ComplexMatrix(new double[][]{{0, 0}, {0, 1}});

        Random alwaysLow = new Random(0) {
            private static final long serialVersionUID = 1L;

            @Override
            public double nextDouble() {
                return 0.1;
            }
        };
        Random alwaysHigh = new Random(0) {
            private static final long serialVersionUID = 1L;

            @Override
            public double nextDouble() {
                return 0.9;
            }
        };

        QuantumState low = plus.applyKrausOperators(
            new ComplexMatrix[]{projectZero, projectOne}, new int[]{0}, alwaysLow);
        assertEquals(1.0, low.getProbability(0), EPS);
        assertEquals(0.0, low.getProbability(1), EPS);

        QuantumState high = plus.applyKrausOperators(
            new ComplexMatrix[]{projectZero, projectOne}, new int[]{0}, alwaysHigh);
        assertEquals(1.0, high.getProbability(1), EPS);
        assertEquals(0.0, high.getProbability(0), EPS);

        // The source state is left untouched.
        assertEquals(0.5, plus.getProbability(0), EPS);

        assertThrows(IllegalArgumentException.class,
            () -> plus.applyKrausOperators(null, new int[]{0}, alwaysLow));
        assertThrows(IllegalArgumentException.class,
            () -> plus.applyKrausOperators(new ComplexMatrix[0], new int[]{0}, alwaysLow));
        assertThrows(IllegalArgumentException.class,
            () -> plus.applyKrausOperators(new ComplexMatrix[]{ComplexMatrix.identity(4)},
                new int[]{0}, alwaysLow));
        assertThrows(IllegalArgumentException.class,
            () -> plus.applyKrausOperators(new ComplexMatrix[]{projectZero},
                new int[]{1}, alwaysLow));
        assertThrows(IllegalStateException.class,
            () -> plus.applyKrausOperators(new ComplexMatrix[]{new ComplexMatrix(2, 2)},
                new int[]{0}, alwaysLow));
    }

    @Test
    void qubitMetadataIsExposedReadOnly() {
        StateVector state = StateVector.createZeroState(2);
        assertEquals(2, state.getQubits().size());
        assertEquals(0, state.getQubit(0).getIndex());
        assertEquals(1, state.getQubit(1).getIndex());
        assertEquals("q0", state.getQubit(0).getLabel());

        assertThrows(IndexOutOfBoundsException.class, () -> state.getQubit(2));
        assertThrows(IndexOutOfBoundsException.class, () -> state.getQubit(-1));
        assertThrows(UnsupportedOperationException.class,
            () -> state.getQubits().add(new Qubit(5)));
    }

    // ---------------------------------------------------------- DensityMatrix

    @Test
    void densityMatrixFactories() {
        DensityMatrix zero = DensityMatrix.createZeroState(1);
        assertArrayEquals(new double[]{1, 0}, zero.getProbabilities(), 0.0);
        assertEquals(1.0, zero.getPurity(), EPS);
        assertTrue(zero.isPure());
        assertEquals(0.0, zero.getVonNeumannEntropy(), EPS);
        assertComplex(1, 0, zero.getAmplitude(0), EPS);

        DensityMatrix mixed = DensityMatrix.createMaximallyMixedState(1);
        assertArrayEquals(new double[]{0.5, 0.5}, mixed.getProbabilities(), EPS);
        assertEquals(0.5, mixed.getPurity(), EPS);
        assertFalse(mixed.isPure());
        assertEquals(1.0, mixed.getVonNeumannEntropy(), EPS);
        assertComplex(Math.sqrt(0.5), 0, mixed.getAmplitude(0), EPS);
        assertEquals(0.5, mixed.getProbability(0), EPS);

        DensityMatrix mixed2 = DensityMatrix.createMaximallyMixedState(2);
        assertEquals(0.25, mixed2.getPurity(), EPS);
        assertEquals(2.0, mixed2.getVonNeumannEntropy(), EPS);
        assertEquals(1.0, mixed2.getMatrix().trace().real(), EPS);

        assertThrows(IllegalArgumentException.class, () -> DensityMatrix.createZeroState(0));
    }

    @Test
    void densityMatrixConstructorValidatesItsInput() {
        assertThrows(IllegalArgumentException.class,
            () -> new DensityMatrix(new ComplexMatrix(4, 2), 1));
        assertThrows(IllegalArgumentException.class,
            () -> new DensityMatrix(ComplexMatrix.identity(2), 2));

        // Not Hermitian, although the trace is one.
        ComplexMatrix notHermitian = new ComplexMatrix(new Complex[][]{
            {Complex.ONE, Complex.ONE}, {Complex.ZERO, Complex.ZERO}});
        assertThrows(IllegalArgumentException.class, () -> new DensityMatrix(notHermitian, 1));

        // Hermitian but trace 1.4.
        ComplexMatrix wrongTrace = new ComplexMatrix(new double[][]{{0.7, 0}, {0, 0.7}});
        assertThrows(IllegalArgumentException.class, () -> new DensityMatrix(wrongTrace, 1));
    }

    @Test
    void densityMatrixGetMatrixReturnsACopy() {
        DensityMatrix mixed = DensityMatrix.createMaximallyMixedState(2);
        ComplexMatrix copy = mixed.getMatrix();
        copy.set(0, 0, 0.9, 0);
        assertEquals(0.9, copy.get(0, 0).real(), 0.0);
        assertEquals(0.25, mixed.getProbability(0), EPS);
        assertEquals(0.25, mixed.getMatrix().get(0, 0).real(), EPS);
    }

    @Test
    void densityMatrixGateAndNoiseApplication() {
        Qubit q0 = new Qubit(0);

        DensityMatrix flipped = (DensityMatrix) DensityMatrix.createZeroState(1)
            .applyOperation(SingleQubitGate.createPauliX(q0));
        assertArrayEquals(new double[]{0, 1}, flipped.getProbabilities(), EPS);
        assertEquals(1.0, flipped.getPurity(), EPS);

        DensityMatrix superposition = (DensityMatrix) DensityMatrix.createZeroState(1)
            .applyOperation(SingleQubitGate.createHadamard(q0));
        assertArrayEquals(new double[]{0.5, 0.5}, superposition.getProbabilities(), EPS);
        assertEquals(0.5, superposition.getMatrix().get(0, 1).real(), EPS);
        assertEquals(1.0, superposition.getPurity(), EPS);

        // Amplitude damping with gamma = 0.25 on |1> gives 25% |0> and 75% |1>.
        DensityMatrix excited = DensityMatrix.fromStateVector(StateVector.fromComputationalBasis(1, 1));
        DensityMatrix damped = (DensityMatrix) excited.applyOperation(
            new AmplitudeDampingNoise(0.25, q0));
        assertEquals(0.25, damped.getProbability(0), EPS);
        assertEquals(0.75, damped.getProbability(1), EPS);
        assertEquals(1.0, damped.getMatrix().trace().real(), EPS);
        assertEquals(0.0, damped.getMatrix().trace().imag(), EPS);
        assertTrue(damped.isPositive());

        // The same channel applied through the explicit Kraus set also sums deterministically.
        ComplexMatrix[] kraus = new AmplitudeDampingNoise(0.25, q0).getKrausOperators(1);
        DensityMatrix dampedDirectly = (DensityMatrix) excited.applyKrausOperators(kraus, new int[]{0});
        assertEquals(0.25, dampedDirectly.getProbability(0), EPS);
        assertEquals(0.75, dampedDirectly.getProbability(1), EPS);

        // The channel acts on the addressed qubit of a larger register: |01> decays to |00>.
        DensityMatrix register = DensityMatrix.fromStateVector(StateVector.fromComputationalBasis(1, 2));
        DensityMatrix decayed = (DensityMatrix) register.applyOperation(
            new AmplitudeDampingNoise(0.25, new Qubit(1)));
        assertEquals(0.25, decayed.getProbability(0), EPS);
        assertEquals(0.75, decayed.getProbability(1), EPS);
        assertEquals(0.0, decayed.getProbability(2), EPS);

        // A noise model without targets is rejected.
        assertThrows(IllegalArgumentException.class,
            () -> DensityMatrix.createZeroState(1).applyOperation(new AmplitudeDampingNoise(0.1)));

        // A non-trace-preserving Kraus set is rejected by density-matrix validation.
        assertThrows(IllegalArgumentException.class,
            () -> DensityMatrix.fromStateVector(StateVector.fromComputationalBasis(1, 1))
                .applyKrausOperators(new ComplexMatrix[]{new ComplexMatrix(new double[][]{{1, 0}, {0, 0}})},
                    new int[]{0}));
    }

    @Test
    void densityMatrixTensorProduct() {
        DensityMatrix mixed = DensityMatrix.createMaximallyMixedState(1);
        DensityMatrix combined = (DensityMatrix) mixed.tensorProduct(mixed);
        assertEquals(2, combined.getNumQubits());
        assertEquals(4, combined.getMatrix().rows());
        assertArrayEquals(new double[]{0.25, 0.25, 0.25, 0.25}, combined.getProbabilities(), EPS);
        assertEquals(0.25, combined.getPurity(), EPS);

        DensityMatrix product = (DensityMatrix) DensityMatrix.createZeroState(1)
            .tensorProduct(DensityMatrix.createZeroState(1));
        assertArrayEquals(new double[]{1, 0, 0, 0}, product.getProbabilities(), 0.0);
        assertEquals(1.0, product.getPurity(), EPS);

        assertThrows(IllegalArgumentException.class,
            () -> mixed.tensorProduct(StateVector.createZeroState(1)));
    }

    @Test
    void densityMatrixPartialTrace() {
        DensityMatrix bell = bellState().toDensityMatrix();
        DensityMatrix reduced = (DensityMatrix) bell.partialTrace(new int[]{1});
        assertArrayEquals(new double[]{0.5, 0.5}, reduced.getProbabilities(), EPS);
        assertEquals(0.0, reduced.getMatrix().get(0, 1).abs(), EPS);
        assertEquals(0.5, reduced.getPurity(), EPS);
        assertEquals(1.0, reduced.getVonNeumannEntropy(), EPS);

        DensityMatrix keptFull = (DensityMatrix) bell.partialTrace(new int[]{0, 1});
        assertEquals(0.0, maxAbsDifference(keptFull.getMatrix(), bell.getMatrix()), EPS);

        assertThrows(IllegalArgumentException.class, () -> bell.partialTrace(new int[]{0, 0}));
    }

    @Test
    void densityMatrixCollapseToBasisState() {
        DensityMatrix bell = bellState().toDensityMatrix();

        DensityMatrix zeroOutcome = (DensityMatrix) bell.collapseToBasisState(new int[]{0}, 0);
        assertEquals(1.0, zeroOutcome.getProbability(0), EPS);
        assertEquals(0.0, zeroOutcome.getMatrix().get(0, 3).abs(), EPS);
        assertEquals(1.0, zeroOutcome.getPurity(), EPS);

        DensityMatrix oneOutcome = (DensityMatrix) bell.collapseToBasisState(new int[]{0}, 1);
        assertEquals(1.0, oneOutcome.getProbability(3), EPS);

        assertThrows(IllegalStateException.class,
            () -> DensityMatrix.createZeroState(1).collapseToBasisState(new int[]{0}, 1));
        assertThrows(IllegalArgumentException.class, () -> bell.collapseToBasisState(new int[]{0}, 2));
        assertThrows(IllegalArgumentException.class,
            () -> bell.collapseToBasisState(new int[]{0, 0}, 0));
    }

    @Test
    void densityMatrixCollapseToEigenvector() {
        Complex half = new Complex(1.0 / Math.sqrt(2), 0);
        DensityMatrix plus = DensityMatrix.fromStateVector(
            new StateVector(new Complex[]{half, half}, 1));

        DensityMatrix collapsed = (DensityMatrix) plus.collapseToEigenvector(new int[]{0},
            new Complex[]{half, half});
        assertArrayEquals(new double[]{0.5, 0.5}, collapsed.getProbabilities(), EPS);
        assertEquals(1.0, collapsed.getPurity(), EPS);

        assertThrows(IllegalStateException.class,
            () -> DensityMatrix.createZeroState(1).collapseToEigenvector(new int[]{0},
                new Complex[]{Complex.ZERO, Complex.ONE}));
        assertThrows(IllegalArgumentException.class,
            () -> plus.collapseToEigenvector(new int[]{0},
                new Complex[]{Complex.ZERO, Complex.ZERO}));
        assertThrows(IllegalArgumentException.class,
            () -> plus.collapseToEigenvector(new int[]{0}, new Complex[]{Complex.ONE}));
    }

    @Test
    void purityAndEntropyAreCachedAndExact() {
        ComplexMatrix diagonal = new ComplexMatrix(2, 2);
        diagonal.set(0, 0, 0.9, 0);
        diagonal.set(1, 1, 0.1, 0);
        DensityMatrix state = new DensityMatrix(diagonal, 1);

        assertEquals(0.82, state.getPurity(), EPS);
        assertEquals(0.82, state.getPurity(), EPS); // cached
        assertFalse(state.isPure());
        assertEquals(0.4689955935892812, state.getVonNeumannEntropy(), EPS);
        assertEquals(0.4689955935892812, state.getVonNeumannEntropy(), EPS); // cached
        assertEquals(0.9, state.getProbability(0), EPS);

        assertTrue(DensityMatrix.createMaximallyMixedState(1).isPositive());
        ComplexMatrix indefinite = new ComplexMatrix(2, 2);
        indefinite.set(0, 0, 1.5, 0);
        indefinite.set(1, 1, -0.5, 0);
        assertFalse(new DensityMatrix(indefinite, 1).isPositive());
    }

    @Test
    void squareRootOfADensityMatrix() {
        DensityMatrix mixed = DensityMatrix.createMaximallyMixedState(1);
        ComplexMatrix root = mixed.squareRoot();
        assertEquals(1.0 / Math.sqrt(2), root.get(0, 0).real(), EPS);
        assertEquals(1.0 / Math.sqrt(2), root.get(1, 1).real(), EPS);
        assertEquals(0.0, root.get(0, 1).abs(), EPS);
        assertEquals(0.0, maxAbsDifference(root.multiply(root), mixed.getMatrix()), EPS);

        DensityMatrix zero = DensityMatrix.createZeroState(1);
        assertEquals(0.0, maxAbsDifference(zero.squareRoot(), zero.getMatrix()), EPS);
    }

    @Test
    void fidelityBetweenDensityMatrices() {
        DensityMatrix zero = DensityMatrix.createZeroState(1);
        DensityMatrix one = DensityMatrix.fromStateVector(StateVector.fromComputationalBasis(1, 1));
        DensityMatrix mixed = DensityMatrix.createMaximallyMixedState(1);
        Complex half = new Complex(1.0 / Math.sqrt(2), 0);
        DensityMatrix plus = DensityMatrix.fromStateVector(new StateVector(new Complex[]{half, half}, 1));

        assertEquals(1.0, DensityMatrix.fidelity(zero, zero), EPS);
        assertEquals(1.0, DensityMatrix.fidelity(plus, plus), EPS);
        assertEquals(0.0, DensityMatrix.fidelity(zero, one), EPS);
        assertEquals(0.5, DensityMatrix.fidelity(zero, mixed), EPS);
        assertEquals(0.5, DensityMatrix.fidelity(plus, mixed), 1e-9);

        assertThrows(IllegalArgumentException.class,
            () -> DensityMatrix.fidelity(zero, DensityMatrix.createZeroState(2)));
    }

    @Test
    void traceDistanceBetweenDensityMatrices() {
        DensityMatrix zero = DensityMatrix.createZeroState(1);
        DensityMatrix one = DensityMatrix.fromStateVector(StateVector.fromComputationalBasis(1, 1));
        DensityMatrix mixed = DensityMatrix.createMaximallyMixedState(1);
        Complex half = new Complex(1.0 / Math.sqrt(2), 0);
        DensityMatrix plus = DensityMatrix.fromStateVector(new StateVector(new Complex[]{half, half}, 1));

        assertEquals(0.0, DensityMatrix.traceDistance(zero, zero), EPS);
        assertEquals(1.0, DensityMatrix.traceDistance(zero, one), EPS);
        assertEquals(1.0, DensityMatrix.traceDistance(one, zero), EPS);
        assertEquals(0.5, DensityMatrix.traceDistance(zero, mixed), EPS);
        // For |0> and |+> the difference has eigenvalues +/- 1/sqrt(2).
        assertEquals(1.0 / Math.sqrt(2), DensityMatrix.traceDistance(zero, plus), EPS);

        assertThrows(IllegalArgumentException.class,
            () -> DensityMatrix.traceDistance(zero, DensityMatrix.createZeroState(2)));
    }

    @Test
    void pureDensityMatrixConvertsBackToAStateVector() {
        Complex half = new Complex(1.0 / Math.sqrt(2), 0);
        StateVector plus = new StateVector(new Complex[]{half, half}, 1);
        StateVector roundTrip = plus.toDensityMatrix().toStateVector();
        assertEquals(0.5, roundTrip.getProbability(0), EPS);
        assertEquals(0.5, roundTrip.getProbability(1), EPS);
        assertEquals(1.0, roundTrip.getNorm(), EPS);

        StateVector bellRoundTrip = bellState().toDensityMatrix().toStateVector();
        assertEquals(0.5, bellRoundTrip.getProbability(0), EPS);
        assertEquals(0.5, bellRoundTrip.getProbability(3), EPS);
        assertEquals(0.0, bellRoundTrip.getProbability(1), EPS);
        assertEquals(1.0, bellRoundTrip.getNorm(), EPS);

        assertThrows(IllegalStateException.class,
            () -> DensityMatrix.createMaximallyMixedState(1).toStateVector());
    }

    @Test
    void normalizeCopyAndEqualityOfDensityMatrices() {
        DensityMatrix mixed = DensityMatrix.createMaximallyMixedState(2);
        assertSame(mixed, mixed.normalize());
        assertEquals(1.0, mixed.getMatrix().trace().real(), EPS);

        DensityMatrix copy = mixed.copy();
        assertNotSame(mixed, copy);
        assertTrue(copy.equals(mixed));
        assertEquals(copy.hashCode(), mixed.hashCode());

        DensityMatrix other = DensityMatrix.createZeroState(2);
        assertFalse(copy.equals(other));
        assertFalse(copy.equals(null));
        assertTrue(mixed.toString().contains("DensityMatrix[2 qubits"));
        assertTrue(mixed.toString().contains("purity=0.2500"));
    }

    @Test
    void densityMatrixPopulationsSumToOne() {
        DensityMatrix bell = bellState().toDensityMatrix();
        double sum = 0.0;
        for (double probability : bell.getProbabilities()) {
            sum += probability;
        }
        assertEquals(1.0, sum, EPS);
        assertEquals(4, bell.getProbabilities().length);

        // Diagonal populations are the real parts of the diagonal entries.
        assertEquals(0.5, bell.getProbability(0), EPS);
        assertEquals(0.5, bell.getProbability(3), EPS);
        assertEquals(0.5, bell.getMatrix().get(0, 3).abs(), EPS);
    }
}
