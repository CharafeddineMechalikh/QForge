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
import com.mechalikh.qforge.math.MatrixOperations;
import com.mechalikh.qforge.math.Parameter;
import com.mechalikh.qforge.math.RandomGenerator;
import java.util.Arrays;
import java.util.Random;
import org.junit.jupiter.api.Test;

class MathCoverageTest {
    private static final double EPS = 1e-9;

    private static void assertComplex(double real, double imag, Complex actual, double delta) {
        assertEquals(real, actual.real(), delta, "real part");
        assertEquals(imag, actual.imag(), delta, "imaginary part");
    }

    private static void assertMatrix(double[][] expected, ComplexMatrix actual, double delta) {
        assertEquals(expected.length, actual.rows(), "rows");
        assertEquals(expected[0].length, actual.cols(), "cols");
        for (int i = 0; i < expected.length; i++) {
            for (int j = 0; j < expected[i].length; j++) {
                assertEquals(expected[i][j], actual.get(i, j).real(), delta, "real[" + i + "][" + j + "]");
                assertEquals(0.0, actual.get(i, j).imag(), delta, "imag[" + i + "][" + j + "]");
            }
        }
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

    // ---------------------------------------------------------------- Complex

    @Test
    void constantsAndAccessorsDescribeAComplexNumber() {
        assertComplex(0.0, 0.0, Complex.ZERO, 0.0);
        assertComplex(1.0, 0.0, Complex.ONE, 0.0);
        assertComplex(0.0, 1.0, Complex.I, 0.0);

        Complex value = new Complex(1.25, -2.5);
        assertEquals(1.25, value.real(), 0.0);
        assertEquals(-2.5, value.imag(), 0.0);

        Complex copy = new Complex(value);
        assertNotSame(value, copy);
        assertComplex(1.25, -2.5, copy, 0.0);
    }

    @Test
    void additionSubtractionAndNegation() {
        Complex a = new Complex(1, 2);
        Complex b = new Complex(3, -5);

        assertComplex(4, -3, a.add(b), EPS);
        assertComplex(-1, 0, Complex.ZERO.add(new Complex(-1, 0)), EPS);
        assertComplex(-2, 7, a.subtract(b), EPS);
        assertComplex(-1, -2, a.negate(), EPS);
        assertComplex(0, 0, a.add(a.negate()), EPS);
        // The operands are never modified.
        assertComplex(1, 2, a, EPS);
        assertComplex(3, -5, b, EPS);
    }

    @Test
    void multiplicationFollowsTheComplexProductRule() {
        Complex a = new Complex(1, 2);
        Complex b = new Complex(3, 4);

        assertComplex(-5, 10, a.multiply(b), EPS);
        assertComplex(-1, 0, Complex.I.multiply(Complex.I), EPS);
        assertComplex(1, 2, a.multiply(Complex.ONE), EPS);
        assertComplex(2.5, 5, a.multiply(2.5), EPS);
        assertComplex(-1, -2, a.scale(-1.0), EPS);
    }

    @Test
    void divisionIsTheInverseOfMultiplication() {
        Complex a = new Complex(1, 2);
        Complex b = new Complex(3, 4);

        assertComplex(0.44, 0.08, a.divide(b), EPS);
        assertComplex(1, 0, a.divide(a), EPS);
        assertComplex(0.5, 1.0, a.divide(2.0), EPS);
        assertComplex(1, 2, a.divide(b).multiply(b), EPS);

        assertThrows(ArithmeticException.class, () -> a.divide(Complex.ZERO));
        assertThrows(ArithmeticException.class, () -> a.divide(0.0));
        assertThrows(ArithmeticException.class, () -> a.divide(1e-20));
    }

    @Test
    void modulusPhaseRootsAndPowers() {
        Complex z = new Complex(3, 4);

        assertEquals(5.0, z.abs(), EPS);
        assertEquals(25.0, z.absSquare(), EPS);
        assertEquals(Math.PI / 2, Complex.I.phase(), EPS);
        assertComplex(3, -4, z.conjugate(), EPS);

        assertComplex(2, 1, z.sqrt(), EPS);
        assertComplex(0, 1, new Complex(-1, 0).sqrt(), EPS);
        assertComplex(0, 0, Complex.ZERO.sqrt(), 0.0);
        Complex rootSquared = z.sqrt().multiply(z.sqrt());
        assertComplex(3, 4, rootSquared, EPS);

        assertComplex(-7, 24, z.pow(2), 1e-9);
        assertComplex(2, 0, new Complex(4, 0).pow(0.5), EPS);
    }

    @Test
    void exponentialLogarithmAndPolarForm() {
        assertComplex(-1, 0, new Complex(0, Math.PI).exp(), 1e-12);
        assertComplex(1, 0, Complex.ZERO.exp(), EPS);
        assertComplex(1, 0, new Complex(Math.E, 0).log(), 1e-12);
        assertComplex(0.5, 0.25, new Complex(0.5, 0.25).exp().log(), EPS);

        assertComplex(0, 2, Complex.fromPolar(2.0, Math.PI / 2), 1e-12);
        assertComplex(-3, 0, Complex.fromPolar(3.0, Math.PI), 1e-12);
        assertEquals(2.0, Complex.fromPolar(2.0, 1.234).abs(), EPS);
    }

    @Test
    void parseAcceptsAllDocumentedSpellings() {
        assertComplex(3.14, 2.71, Complex.parse("3.14+2.71i"), 1e-12);
        assertComplex(1.5, -2.5, Complex.parse("1.5-2.5i"), 1e-12);
        assertComplex(0, 1, Complex.parse("i"), 0.0);
        assertComplex(0, 1, Complex.parse("j"), 0.0);
        assertComplex(0, -1, Complex.parse("-i"), 0.0);
        assertComplex(0, 3, Complex.parse("3i"), 0.0);
        assertComplex(4, 0, Complex.parse("4"), 0.0);
        assertComplex(2, 3, Complex.parse("2+3j"), 0.0);
        assertComplex(3, 4, Complex.parse(" +3+4i "), 0.0);

        assertThrows(NumberFormatException.class, () -> Complex.parse("abc"));
        assertThrows(NumberFormatException.class, () -> Complex.parse("1.2.3+4i"));
    }

    @Test
    void equalityUsesToleranceWhileHashingUsesStoredValues() {
        Complex value = new Complex(1, 1);
        assertTrue(value.equals(value));
        assertTrue(value.equals(new Complex(1 + 5e-11, 1 - 5e-11)));
        assertFalse(value.equals(new Complex(1 + 1e-9, 1)));
        assertFalse(value.equals(null));
        assertFalse(value.equals("1+1i"));

        assertEquals(new Complex(2, -3).hashCode(), new Complex(2, -3).hashCode());
        // Zero components are trimmed from the textual form.
        assertEquals("1.500000", new Complex(1.5, 0).toString());
        assertEquals("2.000000i", new Complex(0, 2).toString());
        assertEquals("1.000000 - 2.000000i", new Complex(1, -2).toString());
        assertEquals("5.000000 angle 0.927295 rad", new Complex(3, 4).toPolarString());
    }

    // ----------------------------------------------------------- ComplexMatrix

    @Test
    void matrixConstructorsValidateAndFill() {
        ComplexMatrix zero = new ComplexMatrix(2, 3);
        assertEquals(2, zero.rows());
        assertEquals(3, zero.cols());
        assertMatrix(new double[][]{{0, 0, 0}, {0, 0, 0}}, zero, 0.0);

        assertMatrix(new double[][]{{1, 0}, {0, 1}}, ComplexMatrix.identity(2), 0.0);
        assertMatrix(new double[][]{{1, 2}, {3, 4}}, ComplexMatrix.fromReal(new double[][]{{1, 2}, {3, 4}}), 0.0);
        assertMatrix(new double[][]{{0, 0}, {0, 0}}, ComplexMatrix.zero(2, 2), 0.0);

        ComplexMatrix fromComplex = new ComplexMatrix(new Complex[][]{
            {Complex.ONE, Complex.I}, {Complex.ZERO, Complex.ONE}});
        assertComplex(0, 1, fromComplex.get(0, 1), 0.0);

        assertThrows(IllegalArgumentException.class, () -> new ComplexMatrix(0, 2));
        assertThrows(IllegalArgumentException.class, () -> new ComplexMatrix(2, -1));
        assertThrows(IllegalArgumentException.class, () -> ComplexMatrix.identity(0));
        assertThrows(IllegalArgumentException.class, () -> new ComplexMatrix(new Complex[0][0]));
        assertThrows(IllegalArgumentException.class, () -> new ComplexMatrix(new Complex[][]{
            {Complex.ONE}, {Complex.ONE, Complex.ONE}}));
        assertThrows(IllegalArgumentException.class, () -> new ComplexMatrix(new double[0][]));
        assertThrows(IllegalArgumentException.class, () -> new ComplexMatrix(new double[][]{
            {1.0, 2.0}, {3.0}}));
    }

    @Test
    void matrixGetAndSetUseDefensiveCopiesAndCheckBounds() {
        ComplexMatrix matrix = new ComplexMatrix(2, 2);
        Complex value = new Complex(0.5, -0.25);
        matrix.set(1, 0, value);
        assertEquals(0.0, matrix.get(0, 0).real(), 0.0); // untouched entry stays zero
        assertComplex(0.5, -0.25, matrix.get(1, 0), 0.0);
        assertNotSame(value, matrix.get(1, 0));
        assertNotSame(matrix.get(1, 0), matrix.get(1, 0));

        matrix.set(0, 1, 2.0, -3.0);
        assertComplex(2.0, -3.0, matrix.get(0, 1), 0.0);

        assertThrows(IndexOutOfBoundsException.class, () -> matrix.get(2, 0));
        assertThrows(IndexOutOfBoundsException.class, () -> matrix.get(0, -1));
        assertThrows(IndexOutOfBoundsException.class, () -> matrix.set(2, 0, Complex.ONE));
    }

    @Test
    void matrixAdditionSubtractionAndScaling() {
        ComplexMatrix a = new ComplexMatrix(new Complex[][]{
            {new Complex(1, 1), new Complex(2, 0)},
            {new Complex(0, -1), new Complex(3, 2)}});
        ComplexMatrix b = new ComplexMatrix(new Complex[][]{
            {Complex.ONE, new Complex(0, 1)},
            {new Complex(1, 1), new Complex(2, 0)}});

        ComplexMatrix sum = a.add(b);
        assertComplex(2, 1, sum.get(0, 0), EPS);
        assertComplex(2, 1, sum.get(0, 1), EPS);
        assertComplex(1, 0, sum.get(1, 0), EPS);
        assertComplex(5, 2, sum.get(1, 1), EPS);

        ComplexMatrix difference = a.subtract(b);
        assertComplex(0, 1, difference.get(0, 0), EPS);
        assertComplex(2, -1, difference.get(0, 1), EPS);

        ComplexMatrix scaled = a.scale(2.0);
        assertComplex(2, 2, scaled.get(0, 0), EPS);
        assertComplex(-4, 6, scaled.multiply(Complex.I).get(1, 1), EPS);

        assertThrows(IllegalArgumentException.class, () -> a.add(new ComplexMatrix(3, 2)));
        assertThrows(IllegalArgumentException.class, () -> a.subtract(new ComplexMatrix(2, 1)));
    }

    @Test
    void matrixMultiplicationAndIdentity() {
        ComplexMatrix a = new ComplexMatrix(new Complex[][]{
            {Complex.ONE, new Complex(0, 1)},
            {new Complex(2, 0), new Complex(1, -1)}});
        ComplexMatrix b = new ComplexMatrix(new Complex[][]{
            {new Complex(1, 0), new Complex(2, 0)},
            {new Complex(0, 1), new Complex(3, 0)}});

        ComplexMatrix product = a.multiply(b);
        // (0,0): 1*1 + i*i = 0; (0,1): 1*2 + i*3 = 2+3i
        assertComplex(0, 0, product.get(0, 0), EPS);
        assertComplex(2, 3, product.get(0, 1), EPS);
        // (1,0): 2*1 + (1-i)*i = 2 + 1+i = 3+i
        assertComplex(3, 1, product.get(1, 0), EPS);
        // (1,1): 2*2 + (1-i)*3 = 4 + 3-3i = 7-3i
        assertComplex(7, -3, product.get(1, 1), EPS);

        ComplexMatrix identity = ComplexMatrix.identity(2);
        assertEquals(0.0, maxAbsDifference(a.multiply(identity), a), EPS);
        assertEquals(0.0, maxAbsDifference(identity.multiply(a), a), EPS);

        ComplexMatrix rectangular = a.multiply(new ComplexMatrix(2, 3));
        assertEquals(2, rectangular.rows());
        assertEquals(3, rectangular.cols());
        assertThrows(IllegalArgumentException.class, () -> a.multiply(new ComplexMatrix(3, 3)));
    }

    @Test
    void transposeConjugateAndAdjoint() {
        ComplexMatrix matrix = new ComplexMatrix(new Complex[][]{
            {new Complex(1, 1), new Complex(2, -3)},
            {new Complex(0, 4), new Complex(5, 0)}});

        ComplexMatrix transpose = matrix.transpose();
        assertEquals(2, transpose.rows());
        assertComplex(2, -3, transpose.get(1, 0), EPS);
        assertComplex(0, 4, transpose.get(0, 1), EPS);

        ComplexMatrix conjugate = matrix.conjugate();
        assertComplex(1, -1, conjugate.get(0, 0), EPS);
        assertComplex(2, 3, conjugate.get(0, 1), EPS);

        ComplexMatrix adjoint = matrix.adjoint();
        assertComplex(1, -1, adjoint.get(0, 0), EPS);
        assertComplex(0, -4, adjoint.get(0, 1), EPS);
        assertComplex(2, 3, adjoint.get(1, 0), EPS);

        assertEquals(0.0, maxAbsDifference(matrix.adjoint().adjoint(), matrix), EPS);
        assertEquals(0.0, maxAbsDifference(matrix.transpose().conjugate(), adjoint), EPS);
    }

    @Test
    void traceAndTensorProduct() {
        ComplexMatrix matrix = new ComplexMatrix(new Complex[][]{
            {Complex.ONE, Complex.I},
            {new Complex(2, 0), new Complex(3, 0)}});
        assertComplex(4, 0, matrix.trace(), EPS);
        assertComplex(0, 0, ComplexMatrix.pauliX().trace(), EPS);
        assertThrows(IllegalArgumentException.class, () -> new ComplexMatrix(2, 3).trace());

        ComplexMatrix tensor = ComplexMatrix.pauliX().tensorProduct(ComplexMatrix.identity(2));
        assertEquals(4, tensor.rows());
        assertEquals(4, tensor.cols());
        assertMatrix(new double[][]{
            {0, 0, 1, 0},
            {0, 0, 0, 1},
            {1, 0, 0, 0},
            {0, 1, 0, 0}}, tensor, EPS);

        ComplexMatrix kron = MatrixOperations.kroneckerProduct(ComplexMatrix.identity(2), ComplexMatrix.pauliZ());
        assertMatrix(new double[][]{
            {1, 0, 0, 0},
            {0, -1, 0, 0},
            {0, 0, 1, 0},
            {0, 0, 0, -1}}, kron, EPS);
    }

    @Test
    void pauliAndHadamardIdentities() {
        ComplexMatrix x = ComplexMatrix.pauliX();
        ComplexMatrix y = ComplexMatrix.pauliY();
        ComplexMatrix z = ComplexMatrix.pauliZ();
        ComplexMatrix h = ComplexMatrix.hadamard();

        for (ComplexMatrix m : new ComplexMatrix[]{x, y, z, h}) {
            assertTrue(m.isHermitian());
            assertTrue(m.isUnitary());
            assertEquals(0.0, maxAbsDifference(m.multiply(m), ComplexMatrix.identity(2)), EPS);
        }

        // XY = iZ
        assertEquals(0.0, maxAbsDifference(x.multiply(y), z.multiply(Complex.I)), EPS);
        // YZ = iX
        assertEquals(0.0, maxAbsDifference(y.multiply(z), x.multiply(Complex.I)), EPS);
        // ZX = iY
        assertEquals(0.0, maxAbsDifference(z.multiply(x), y.multiply(Complex.I)), EPS);

        ComplexMatrix triangular = new ComplexMatrix(new double[][]{{0, 1}, {0, 0}});
        assertFalse(triangular.isHermitian());
        assertFalse(triangular.isUnitary());
        assertFalse(new ComplexMatrix(2, 3).isHermitian());
        assertFalse(new ComplexMatrix(2, 3).isUnitary());
    }

    @Test
    void hermitianEigenvaluesAndEigenDecompositionAreConsistent() {
        assertArrayEquals(new double[]{-1.0, 1.0}, ComplexMatrix.pauliZ().eigenvalues(), EPS);
        assertArrayEquals(new double[]{1.0, 2.0, 3.0},
            new ComplexMatrix(new double[][]{{3, 0, 0}, {0, 1, 0}, {0, 0, 2}}).eigenvalues(), EPS);

        ComplexMatrix matrix = new ComplexMatrix(new double[][]{{2, 1, 0}, {1, 2, 1}, {0, 1, 2}});
        double[] eigenvalues = matrix.eigenvalues();
        assertArrayEquals(new double[]{2 - Math.sqrt(2), 2.0, 2 + Math.sqrt(2)}, eigenvalues, EPS);

        ComplexMatrix[] decomposition = matrix.eigenDecomposition();
        ComplexMatrix diagonal = decomposition[0];
        ComplexMatrix vectors = decomposition[1];
        assertComplex(2 - Math.sqrt(2), 0, diagonal.get(0, 0), EPS);
        assertComplex(2.0, 0, diagonal.get(1, 1), EPS);
        assertComplex(2 + Math.sqrt(2), 0, diagonal.get(2, 2), EPS);
        // A = V D V^dagger and V is unitary.
        assertTrue(maxAbsDifference(vectors.multiply(diagonal).multiply(vectors.adjoint()), matrix) < 1e-8);
        assertTrue(maxAbsDifference(vectors.multiply(vectors.adjoint()), ComplexMatrix.identity(3)) < 1e-8);
        assertTrue(maxAbsDifference(matrix.multiply(vectors), vectors.multiply(diagonal)) < 1e-8);

        assertThrows(IllegalArgumentException.class, () -> new ComplexMatrix(2, 3).eigenvalues());
        assertThrows(IllegalArgumentException.class, () -> new ComplexMatrix(2, 3).eigenDecomposition());
    }

    @Test
    void eigenDecompositionOfANonHermitianTriangularMatrix() {
        ComplexMatrix upper = new ComplexMatrix(new double[][]{{1, 1}, {0, 2}});
        assertFalse(upper.isHermitian());
        ComplexMatrix[] decomposition = upper.eigenDecomposition();
        assertComplex(1.0, 0, decomposition[0].get(0, 0), EPS);
        assertComplex(2.0, 0, decomposition[0].get(1, 1), EPS);
        assertComplex(0.0, 0, decomposition[0].get(0, 1), EPS);
        // Already triangular, so the QR iteration leaves the factor accumulated so far at identity.
        assertComplex(1.0, 0, decomposition[1].get(0, 0), EPS);
        assertComplex(1.0, 0, decomposition[1].get(1, 1), EPS);
    }

    @Test
    void realImaginaryPartsCopyAndEquality() {
        ComplexMatrix matrix = new ComplexMatrix(new Complex[][]{
            {new Complex(1, -2), new Complex(3, 4)}});
        assertArrayEquals(new double[]{1, 3}, matrix.getRealParts()[0], 0.0);
        assertArrayEquals(new double[]{-2, 4}, matrix.getImaginaryParts()[0], 0.0);

        ComplexMatrix copy = matrix.copy();
        assertTrue(copy.equals(matrix));
        assertEquals(copy.hashCode(), copy.hashCode());
        copy.set(0, 0, new Complex(9, 9));
        assertComplex(1, -2, matrix.get(0, 0), 0.0);
        assertFalse(copy.equals(matrix));

        assertTrue(matrix.equals(matrix));
        assertEquals(0.0, maxAbsDifference(matrix, new ComplexMatrix(new Complex[][]{
            {new Complex(1 + 1e-12, -2), new Complex(3, 4)}})), 1e-11);
        assertFalse(matrix.equals(new ComplexMatrix(new Complex[][]{
            {new Complex(1 + 1e-6, -2), new Complex(3, 4)}})));
        assertFalse(matrix.equals(null));
        assertTrue(matrix.toString().contains("1x2"));
    }

    // -------------------------------------------------------- MatrixOperations

    @Test
    void staticArithmeticDelegatesToMatrixOperations() {
        ComplexMatrix x = ComplexMatrix.pauliX();
        ComplexMatrix z = ComplexMatrix.pauliZ();

        assertMatrix(new double[][]{{1, 1}, {1, -1}}, MatrixOperations.add(x, z), EPS);
        assertMatrix(new double[][]{{-1, 1}, {1, 1}}, MatrixOperations.subtract(x, z), EPS);
        assertEquals(0.0, maxAbsDifference(MatrixOperations.multiply(x, z), x.multiply(z)), EPS);
        assertEquals(0.0, maxAbsDifference(MatrixOperations.tensorProduct(x, z), x.tensorProduct(z)), EPS);
        assertComplex(0, 0, MatrixOperations.trace(x), EPS);
        assertMatrix(new double[][]{{1, 0}, {0, 1}}, MatrixOperations.identity(2), EPS);
        assertMatrix(new double[][]{{0, 0, 0}, {0, 0, 0}}, MatrixOperations.zero(2, 3), EPS);

        ComplexMatrix[] decomposition = MatrixOperations.eigenDecomposition(z);
        assertComplex(-1, 0, decomposition[0].get(0, 0), EPS);
        assertComplex(1, 0, decomposition[0].get(1, 1), EPS);
        assertThrows(IllegalArgumentException.class,
            () -> MatrixOperations.eigenDecomposition(new ComplexMatrix(2, 3)));
        assertThrows(IllegalArgumentException.class,
            () -> MatrixOperations.eigenvalues(new ComplexMatrix(2, 3)));
    }

    @Test
    void matrixExponentialMatchesTheTruncatedSeries() {
        ComplexMatrix identity = MatrixOperations.exp(ComplexMatrix.zero(2, 2));
        assertMatrix(new double[][]{{1, 0}, {0, 1}}, identity, EPS);
        assertThrows(IllegalArgumentException.class,
            () -> MatrixOperations.exp(new ComplexMatrix(2, 3)));

        // exp(i * 0.5 * X) = cos(0.5) I + i sin(0.5) X
        ComplexMatrix generator = ComplexMatrix.pauliX().scale(0.5).multiply(Complex.I);
        ComplexMatrix exponential = MatrixOperations.exp(generator);
        double cos = Math.cos(0.5);
        double sin = Math.sin(0.5);
        assertComplex(cos, 0, exponential.get(0, 0), EPS);
        assertComplex(cos, 0, exponential.get(1, 1), EPS);
        assertComplex(0, sin, exponential.get(0, 1), EPS);
        assertComplex(0, sin, exponential.get(1, 0), EPS);
    }

    @Test
    void matrixNorms() {
        ComplexMatrix h = ComplexMatrix.hadamard();
        assertEquals(Math.sqrt(2), MatrixOperations.norm(h, "frobenius"), EPS);
        assertEquals(Math.sqrt(2), MatrixOperations.norm(h, "FROBENIUS"), EPS);
        assertEquals(1.0 / Math.sqrt(2), MatrixOperations.norm(h, "max"), EPS);
        assertEquals(2.0, MatrixOperations.norm(h, "trace"), EPS);

        ComplexMatrix identity = ComplexMatrix.identity(2);
        assertEquals(Math.sqrt(2), MatrixOperations.norm(identity, "frobenius"), EPS);
        assertEquals(1.0, MatrixOperations.norm(identity, "max"), EPS);
        assertEquals(2.0, MatrixOperations.norm(identity, "trace"), EPS);

        assertThrows(IllegalArgumentException.class, () -> MatrixOperations.norm(h, "spectral"));
        assertThrows(IllegalArgumentException.class,
            () -> MatrixOperations.norm(new ComplexMatrix(2, 3), "trace"));
    }

    @Test
    void partialTraceReducesSubsystems() {
        // Bell state |00> + |11> density matrix.
        ComplexMatrix bell = new ComplexMatrix(4, 4);
        bell.set(0, 0, 0.5, 0);
        bell.set(0, 3, 0.5, 0);
        bell.set(3, 0, 0.5, 0);
        bell.set(3, 3, 0.5, 0);

        ComplexMatrix reduced = MatrixOperations.partialTrace(bell, 2, new int[]{0});
        assertMatrix(new double[][]{{0.5, 0}, {0, 0.5}}, reduced, EPS);

        // Keeping every subsystem returns the original matrix.
        int[] keepAll = {1, 0};
        ComplexMatrix full = MatrixOperations.partialTrace(bell, 2, keepAll);
        assertEquals(0.0, maxAbsDifference(full, bell), EPS);
        // The supplied index array is sorted in place.
        assertArrayEquals(new int[]{0, 1}, keepAll);

        // Product state |01> keeps a definite value on either subsystem.
        ComplexMatrix product = new ComplexMatrix(4, 4);
        product.set(1, 1, 1.0, 0);
        assertMatrix(new double[][]{{1, 0}, {0, 0}},
            MatrixOperations.partialTrace(product, 2, new int[]{0}), EPS);
        assertMatrix(new double[][]{{0, 0}, {0, 1}},
            MatrixOperations.partialTrace(product, 2, new int[]{1}), EPS);

        assertThrows(IllegalArgumentException.class,
            () -> MatrixOperations.partialTrace(bell, 3, new int[]{0}));
    }

    // --------------------------------------------------------------- Parameter

    @Test
    void parameterConstructionAndGetters() {
        Parameter parameter = new Parameter("theta", 0.5, 0.0, 1.0, 0.25);
        assertEquals("theta", parameter.getName());
        assertEquals(Parameter.ParameterType.CONTINUOUS, parameter.getType());
        assertEquals(0.5, parameter.getValue(), 0.0);
        assertEquals(0.5, parameter.getInitialValue(), 0.0);
        assertEquals(0.0, parameter.getMinValue(), 0.0);
        assertEquals(1.0, parameter.getMaxValue(), 0.0);
        assertEquals(0.25, parameter.getRegularization(), 0.0);
        assertEquals(1.0, parameter.getRange(), 0.0);
        assertEquals(0.5, parameter.getNormalizedValue(), EPS);
        assertFalse(parameter.isAtBounds());

        Parameter noRegularization = new Parameter("phi", 2.0, 0.0, 4.0);
        assertEquals(0.0, noRegularization.getRegularization(), 0.0);
        assertEquals(2, noRegularization.getIntValue());
    }

    @Test
    void parameterRejectsInvalidArguments() {
        assertThrows(IllegalArgumentException.class, () -> new Parameter(null, 0.5, 0, 1));
        assertThrows(IllegalArgumentException.class, () -> new Parameter("  ", 0.5, 0, 1));
        assertThrows(IllegalArgumentException.class, () -> new Parameter("p", 0.5, 2, 1));
        assertThrows(IllegalArgumentException.class, () -> new Parameter("p", -0.1, 0, 1));
        assertThrows(IllegalArgumentException.class, () -> new Parameter("p", 1.1, 0, 1));
        assertThrows(IllegalArgumentException.class, () -> new Parameter("p", 0.5, 0, 1, -1.0));
        assertThrows(IllegalArgumentException.class,
            () -> new Parameter("p", 0.5, 0, 1, -0.5, Parameter.ParameterType.CONTINUOUS));
    }

    @Test
    void parameterClipsValuesToBounds() {
        Parameter parameter = new Parameter("p", 0.5, 0.0, 1.0);
        parameter.setValue(1.5);
        assertEquals(1.0, parameter.getValue(), 0.0);
        assertTrue(parameter.isAtBounds());

        parameter.setValue(-3.0);
        assertEquals(0.0, parameter.getValue(), 0.0);
        assertTrue(parameter.isAtBounds());

        parameter.setValue(0.75);
        assertEquals(0.75, parameter.getValue(), 0.0);
        parameter.setValue(1); // int overload
        assertEquals(1.0, parameter.getValue(), 0.0);
        assertEquals(1.0, parameter.getNormalizedValue(), 0.0);
    }

    @Test
    void integerParametersRoundBeforeClipping() {
        Parameter integer = Parameter.createInteger("n", 2, 0, 4);
        assertEquals(Parameter.ParameterType.INTEGER, integer.getType());
        assertEquals(2, integer.getIntValue());

        integer.setValue(2.6);
        assertEquals(3.0, integer.getValue(), 0.0);
        integer.setValue(2.4);
        assertEquals(2.0, integer.getValue(), 0.0);
        integer.setValue(4.6);
        assertEquals(4.0, integer.getValue(), 0.0);
        integer.setValue(-7.2);
        assertEquals(0.0, integer.getValue(), 0.0);
    }

    @Test
    void discreteAndIntegerFactoriesDeriveTheirBounds() {
        Parameter discrete = Parameter.createDiscrete("d", new double[]{0.5, 1.5, 2.5}, 1.5);
        assertEquals(Parameter.ParameterType.DISCRETE, discrete.getType());
        assertEquals(0.5, discrete.getMinValue(), 0.0);
        assertEquals(2.5, discrete.getMaxValue(), 0.0);
        assertEquals(1.5, discrete.getValue(), 0.0);

        assertThrows(IllegalArgumentException.class,
            () -> Parameter.createDiscrete("d", null, 1.5));
        assertThrows(IllegalArgumentException.class,
            () -> Parameter.createDiscrete("d", new double[0], 1.5));
        assertThrows(IllegalArgumentException.class,
            () -> Parameter.createDiscrete("d", new double[]{0.5, 1.5}, 1.4));
        assertThrows(IllegalArgumentException.class,
            () -> Parameter.createInteger("n", 5, 0, 4));
    }

    @Test
    void normalizedValuesMapLinearlyOntoTheBounds() {
        Parameter parameter = new Parameter("p", 2.0, 0.0, 4.0);
        assertEquals(0.5, parameter.getNormalizedValue(), EPS);

        parameter.setNormalizedValue(0.25);
        assertEquals(1.0, parameter.getValue(), EPS);
        parameter.setNormalizedValue(-1.0);
        assertEquals(0.0, parameter.getValue(), EPS);
        parameter.setNormalizedValue(2.0);
        assertEquals(4.0, parameter.getValue(), EPS);

        Parameter integer = new Parameter("n", 0.0, 0.0, 10.0, 0.0, Parameter.ParameterType.INTEGER);
        integer.setNormalizedValue(0.5);
        assertEquals(5.0, integer.getValue(), 0.0);

        Parameter fixed = new Parameter("f", 1.0, 1.0, 1.0);
        assertEquals(0.0, fixed.getNormalizedValue(), 0.0);
    }

    @Test
    void regularizationCostDependsOnType() {
        Parameter continuous = new Parameter("c", 2.0, 0.0, 4.0, 3.0);
        assertEquals(12.0, continuous.getRegularizationCost(), EPS);

        Parameter discrete = new Parameter("d", 2.0, 0.0, 4.0, 4.0, Parameter.ParameterType.DISCRETE);
        assertEquals(16.0, discrete.getRegularizationCost(), EPS);

        Parameter integer = new Parameter("i", 3.0, 0.0, 10.0, 2.0, Parameter.ParameterType.INTEGER);
        assertEquals(6.0, integer.getRegularizationCost(), EPS);

        Parameter categorical = new Parameter("k", 1.0, 0.0, 2.0, 5.0, Parameter.ParameterType.CATEGORICAL);
        assertEquals(0.0, categorical.getRegularizationCost(), 0.0);

        Parameter unregularized = new Parameter("u", 2.0, 0.0, 4.0);
        assertEquals(0.0, unregularized.getRegularizationCost(), 0.0);
    }

    @Test
    void copyResetGradientNoiseAndEquality() {
        Parameter parameter = new Parameter("p", 0.5, 0.0, 1.0);
        parameter.setValue(0.8);

        Parameter copy = parameter.copy();
        assertEquals(0.8, copy.getInitialValue(), 0.0);
        assertEquals(0.8, copy.getValue(), 0.0);
        copy.reset();
        assertEquals(0.8, copy.getValue(), 0.0);
        parameter.reset();
        assertEquals(0.5, parameter.getValue(), 0.0);

        parameter.setValue(0.5);
        parameter.applyGradient(0.5, 0.2);
        assertEquals(0.4, parameter.getValue(), EPS);
        parameter.applyGradient(-10.0, 1.0);
        assertEquals(1.0, parameter.getValue(), EPS);

        // A zero-width range makes the Gaussian noise term vanish, independent of the random draw.
        Parameter fixed = new Parameter("f", 1.0, 1.0, 1.0);
        fixed.addNoise(10.0);
        assertEquals(1.0, fixed.getValue(), 0.0);

        Parameter noisy = new Parameter("n", 0.5, 0.0, 1.0);
        noisy.addNoise(10.0);
        assertTrue(noisy.getValue() >= 0.0 && noisy.getValue() <= 1.0);

        // equals ignores the initial value but compares every other field.
        Parameter sameState = new Parameter("p", 1.0, 0.0, 1.0);
        Parameter otherInitial = new Parameter("p", 0.9, 0.0, 1.0);
        otherInitial.setValue(1.0);
        assertTrue(sameState.equals(otherInitial));
        assertEquals(sameState.hashCode(), new Parameter("p", 1.0, 0.0, 1.0).hashCode());
        assertFalse(sameState.equals(new Parameter("q", 1.0, 0.0, 1.0)));
        assertFalse(sameState.equals(new Parameter("p", 1.0, 0.0, 2.0)));
        assertFalse(sameState.equals(null));

        assertTrue(parameter.toString().contains("Parameter[name='p'"));
        assertTrue(parameter.toString().contains("CONTINUOUS"));
    }

    // --------------------------------------------------------- RandomGenerator

    @Test
    void seededGeneratorReplaysTheSameSequence() {
        try {
            Random seeded = new Random(12345);
            RandomGenerator.setRandom(seeded);
            assertSame(seeded, RandomGenerator.getRandom());

            double uniform = RandomGenerator.nextDouble();
            int integer = RandomGenerator.nextInt(1000);
            double gaussian = RandomGenerator.nextGaussian();
            boolean bool = RandomGenerator.nextBoolean();
            double ranged = RandomGenerator.nextDouble(2.0, 5.0);

            assertTrue(uniform >= 0.0 && uniform < 1.0);
            assertTrue(integer >= 0 && integer < 1000);
            assertTrue(ranged >= 2.0 && ranged < 5.0);

            RandomGenerator.setRandom(new Random(12345));
            assertEquals(uniform, RandomGenerator.nextDouble(), 0.0);
            assertEquals(integer, RandomGenerator.nextInt(1000));
            assertEquals(gaussian, RandomGenerator.nextGaussian(), 0.0);
            assertEquals(bool, RandomGenerator.nextBoolean());
            assertEquals(ranged, RandomGenerator.nextDouble(2.0, 5.0), 0.0);
        } finally {
            RandomGenerator.resetToDefault();
        }
    }

    @Test
    void boundedPrimitivesRespectTheirContracts() {
        try {
            RandomGenerator.setRandom(new Random(7));
            assertEquals(0, RandomGenerator.nextInt(1));
            assertEquals(5, RandomGenerator.nextInt(5, 6));
            assertEquals(2.5, RandomGenerator.nextDouble(2.5, 2.5), 0.0);
            assertFalse(RandomGenerator.nextBoolean(0.0));
            assertTrue(RandomGenerator.nextBoolean(1.0));
            assertTrue(RandomGenerator.nextDouble(0.0, 1.0) < 1.0);
            assertTrue(RandomGenerator.nextInt(4) < 4);
            // A zero standard deviation makes the shifted Gaussian value exact.
            assertEquals(2.0, RandomGenerator.nextGaussian(2.0, 0.0), 0.0);
            assertEquals(-1.5, RandomGenerator.nextGaussian(-1.5, 0.0), 0.0);
        } finally {
            RandomGenerator.resetToDefault();
        }
    }

    @Test
    void randomComplexAndVectorHelpers() {
        try {
            RandomGenerator.setRandom(new Random(11));

            Complex uniform = RandomGenerator.nextComplex();
            assertTrue(uniform.real() >= 0.0 && uniform.real() < 1.0);
            assertTrue(uniform.imag() >= 0.0 && uniform.imag() < 1.0);

            assertEquals(3.0, RandomGenerator.nextComplex(3.0).abs(), EPS);
            assertEquals(1.0, RandomGenerator.nextComplexUnit().abs(), EPS);

            int[] permutation = RandomGenerator.randomPermutation(8);
            int[] sorted = permutation.clone();
            Arrays.sort(sorted);
            assertArrayEquals(new int[]{0, 1, 2, 3, 4, 5, 6, 7}, sorted);

            double[] vector = RandomGenerator.randomVector(5);
            assertEquals(5, vector.length);
            for (double entry : vector) {
                assertTrue(entry >= 0.0 && entry < 1.0);
            }
            assertEquals(4, RandomGenerator.randomNormalVector(4).length);
            assertEquals(3, RandomGenerator.randomComplexVector(3).length);

            Complex[] normalized = RandomGenerator.randomNormalizedComplexVector(6);
            double norm = 0.0;
            for (Complex value : normalized) {
                norm += value.absSquare();
            }
            assertEquals(1.0, Math.sqrt(norm), EPS);
            assertEquals(0, RandomGenerator.randomNormalizedComplexVector(0).length);
        } finally {
            RandomGenerator.resetToDefault();
        }
    }

    @Test
    void randomMatricesAreHermitianAndNormalized() {
        try {
            RandomGenerator.setRandom(new Random(2024));

            ComplexMatrix hermitian = RandomGenerator.randomHermitianMatrix(3);
            assertEquals(3, hermitian.rows());
            assertTrue(hermitian.isHermitian());
            for (int i = 0; i < 3; i++) {
                assertEquals(0.0, hermitian.get(i, i).imag(), 0.0);
            }

            ComplexMatrix density = RandomGenerator.randomDensityMatrix(2);
            assertEquals(2, density.rows());
            assertEquals(2, density.cols());
            assertTrue(density.isHermitian());
            assertEquals(1.0, density.trace().real(), EPS);
            assertEquals(0.0, density.trace().imag(), EPS);

            ComplexMatrix random = RandomGenerator.randomUnitaryMatrix(2);
            assertEquals(2, random.rows());
            assertEquals(2, random.cols());
            for (int i = 0; i < 2; i++) {
                for (int j = 0; j < 2; j++) {
                    assertTrue(random.get(i, j).real() >= 0.0 && random.get(i, j).real() < 1.0);
                    assertTrue(random.get(i, j).imag() >= 0.0 && random.get(i, j).imag() < 1.0);
                }
            }
        } finally {
            RandomGenerator.resetToDefault();
        }
    }

    @Test
    void samplingUsesTheCumulativeDistribution() {
        double[] weights = {0.2, 0.3, 0.5};
        assertEquals(0, RandomGenerator.sampleFromDistribution(weights, 0.1));
        assertEquals(1, RandomGenerator.sampleFromDistribution(weights, 0.3));
        assertEquals(2, RandomGenerator.sampleFromDistribution(weights, 0.9));
        assertEquals(2, RandomGenerator.sampleFromDistribution(weights, 5.0));
        assertEquals(-1, RandomGenerator.sampleFromDistribution(new double[0], 0.5));

        try {
            RandomGenerator.setRandom(new Random(3));
            int sampled = RandomGenerator.sampleFromDistribution(weights);
            assertTrue(sampled >= 0 && sampled < 3);
            assertEquals(-1, RandomGenerator.sampleFromDistribution(new double[0]));
        } finally {
            RandomGenerator.resetToDefault();
        }
    }
}
