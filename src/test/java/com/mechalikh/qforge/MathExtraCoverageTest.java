package com.mechalikh.qforge;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.mechalikh.qforge.math.Complex;
import com.mechalikh.qforge.math.ComplexMatrix;
import com.mechalikh.qforge.math.MatrixOperations;
import java.lang.reflect.Method;
import org.junit.jupiter.api.Test;

class MathExtraCoverageTest {
    private static final double EPS = 1e-9;

    private static Object invoke(Object target, String name, Class<?>[] types, Object... args)
        throws Exception {
        Method method = target.getClass().getDeclaredMethod(name, types);
        method.setAccessible(true);
        return method.invoke(target, args);
    }

    private static Object invokeStatic(Class<?> owner, String name, Class<?>[] types, Object... args)
        throws Exception {
        Method method = owner.getDeclaredMethod(name, types);
        method.setAccessible(true);
        return method.invoke(null, args);
    }

    private static double maxAbs(ComplexMatrix m) {
        double max = 0.0;
        for (int i = 0; i < m.rows(); i++) {
            for (int j = 0; j < m.cols(); j++) {
                max = Math.max(max, m.get(i, j).abs());
            }
        }
        return max;
    }

    // ------------------------------------------------------------- toString

    @Test
    void matrixToStringTruncatesLargeMatrices() {
        ComplexMatrix big = new ComplexMatrix(8, 8);
        for (int i = 0; i < 8; i++) {
            for (int j = 0; j < 8; j++) {
                big.set(i, j, i + j, 0);
            }
        }
        String text = big.toString();
        assertTrue(text.contains(", ..."));
        assertTrue(text.contains("...\n"));

        String small = new ComplexMatrix(2, 2).toString();
        assertFalse(small.contains("..."));
    }

    // ----------------------------------------------------------- eigenvalues

    @Test
    void eigenvaluesOfNonHermitianUseIterativePath() {
        ComplexMatrix rotation = new ComplexMatrix(3, 3);
        rotation.set(0, 0, 2, 0);
        rotation.set(0, 1, 1, 0);
        rotation.set(1, 2, 1, 0);
        rotation.set(2, 2, 3, 0);
        assertFalse(rotation.isHermitian());

        double[] values = rotation.eigenvalues();
        assertEquals(3, values.length);
        for (double v : values) {
            assertFalse(Double.isNaN(v));
        }
    }

    @Test
    void privateEigenvalueHelpersProduceFiniteResults() throws Exception {
        ComplexMatrix hermitian = ComplexMatrix.pauliX();
        double[] twoByTwo = (double[]) invoke(hermitian, "eigenvalues2x2", new Class<?>[]{});
        assertEquals(2, twoByTwo.length);
        assertEquals(-1.0, Math.min(twoByTwo[0], twoByTwo[1]), 1e-6);
        assertEquals(1.0, Math.max(twoByTwo[0], twoByTwo[1]), 1e-6);

        ComplexMatrix rotation = new ComplexMatrix(2, 2);
        rotation.set(0, 1, 1, 0);
        rotation.set(1, 0, 1, 0);
        double[] iterative = (double[]) invoke(rotation, "eigenvaluesIterative", new Class<?>[]{});
        assertEquals(2, iterative.length);
        for (double v : iterative) {
            assertFalse(Double.isNaN(v));
        }

        double estimate = (double) invoke(hermitian, "powerIteration", new Class<?>[]{int.class}, 20);
        assertFalse(Double.isNaN(estimate));

        double noIterations = (double) invoke(hermitian, "powerIteration",
            new Class<?>[]{int.class}, 0);
        assertFalse(Double.isNaN(noIterations));
    }

    @Test
    void matrixOperationsPrivateIterativeEigenvalues() throws Exception {
        ComplexMatrix symmetric = new ComplexMatrix(2, 2);
        symmetric.set(0, 0, 2, 0);
        symmetric.set(0, 1, 1, 0);
        symmetric.set(1, 0, 1, 0);
        symmetric.set(1, 1, 2, 0);

        double[] values = (double[]) invokeStatic(MatrixOperations.class,
            "computeEigenvaluesIterative", new Class<?>[]{ComplexMatrix.class}, symmetric);
        assertEquals(2, values.length);
        assertTrue(values[0] <= values[1] + EPS);
        for (double v : values) {
            assertFalse(Double.isNaN(v));
        }

        double estimate = (double) invokeStatic(MatrixOperations.class, "powerIteration",
            new Class<?>[]{ComplexMatrix.class}, symmetric);
        assertTrue(estimate > 0.0);

        ComplexMatrix deflated = (ComplexMatrix) invokeStatic(MatrixOperations.class,
            "deflateMatrix", new Class<?>[]{ComplexMatrix.class, double.class}, symmetric, 1.0);
        assertEquals(1.0, deflated.get(0, 0).real(), EPS);
        assertEquals(1.0, deflated.get(1, 1).real(), EPS);
    }

    // ------------------------------------------------------ decomposition

    @Test
    void nonHermitianDecompositionRunsQrAlgorithm() {
        ComplexMatrix shear = new ComplexMatrix(2, 2);
        shear.set(0, 0, 1, 0);
        shear.set(0, 1, 1, 0);
        shear.set(1, 0, 0, 0);
        shear.set(1, 1, 1, 0);
        assertFalse(shear.isHermitian());

        ComplexMatrix[] eigen = shear.eigenDecomposition();
        assertNotNull(eigen[0]);
        assertNotNull(eigen[1]);
        assertEquals(2, eigen[0].rows());
    }

    @Test
    void qrDecompositionHandlesRankDeficientColumns() throws Exception {
        ComplexMatrix zero = new ComplexMatrix(2, 2);
        ComplexMatrix[] qr = (ComplexMatrix[]) invoke(zero, "qrDecomposition",
            new Class<?>[]{ComplexMatrix.class}, zero);
        assertEquals(0.0, qr[1].get(0, 0).abs(), EPS);
        assertEquals(0.0, qr[0].get(1, 0).abs(), EPS);

        ComplexMatrix identity = ComplexMatrix.identity(2);
        ComplexMatrix[] qrFull = (ComplexMatrix[]) invoke(zero, "qrDecomposition",
            new Class<?>[]{ComplexMatrix.class}, identity);
        assertEquals(1.0, qrFull[1].get(0, 0).real(), EPS);
    }

    @Test
    void upperTriangularDetection() throws Exception {
        ComplexMatrix lower = new ComplexMatrix(2, 2);
        lower.set(1, 0, 5, 0);
        assertFalse((boolean) invoke(lower, "isUpperTriangular",
            new Class<?>[]{ComplexMatrix.class, double.class}, lower, 1e-6));

        ComplexMatrix triangular = new ComplexMatrix(2, 2);
        triangular.set(0, 0, 1, 0);
        triangular.set(0, 1, 2, 0);
        assertTrue((boolean) invoke(lower, "isUpperTriangular",
            new Class<?>[]{ComplexMatrix.class, double.class}, triangular, 1e-6));
    }

    @Test
    void squareRootOfComplexMatrixHasSquareEqualToInput() {
        ComplexMatrix dm = new ComplexMatrix(2, 2);
        dm.set(0, 0, 0.5, 0);
        dm.set(1, 1, 0.5, 0);
        com.mechalikh.qforge.state.DensityMatrix rho =
            new com.mechalikh.qforge.state.DensityMatrix(dm, 1);
        ComplexMatrix root = rho.squareRoot();
        assertEquals(0.0, maxAbs(root.multiply(root).subtract(dm)), 1e-6);
    }

    @Test
    void generalDecompositionFallsBackWhenQrFails() throws Exception {
        ComplexMatrix matrix = new ComplexMatrix(2, 2);
        matrix.set(0, 0, 1, 0);
        matrix.set(1, 1, 1, 0);
        ComplexMatrix[] pair = (ComplexMatrix[]) invoke(matrix, "eigenDecompositionGeneral",
            new Class<?>[]{});
        assertEquals(2, pair.length);
        assertEquals(2, pair[0].rows());
    }

    // ----------------------------------------------------- MatrixOperations

    @Test
    void eigenvalueSumAndTraceHelpers() {
        ComplexMatrix identity = ComplexMatrix.identity(2);
        assertEquals(2.0, MatrixOperations.trace(identity).real(), EPS);
        double[] values = MatrixOperations.eigenvalues(
            com.mechalikh.qforge.state.DensityMatrix.createMaximallyMixedState(1).getMatrix());
        assertEquals(2, values.length);
    }

    // ------------------------------------------------------------ Complex

    @Test
    void complexParseCoversLeadingSignSplits() {
        Complex a = Complex.parse("+3+4i");
        assertEquals(3.0, a.real(), EPS);
        assertEquals(4.0, a.imag(), EPS);

        Complex b = Complex.parse("-2-5j");
        assertEquals(-2.0, b.real(), EPS);
        assertEquals(-5.0, b.imag(), EPS);

        Complex pureImaginary = Complex.parse("3i");
        assertEquals(0.0, pureImaginary.real(), EPS);
        assertEquals(3.0, pureImaginary.imag(), EPS);

        Complex negativeUnit = Complex.parse("-i");
        assertEquals(-1.0, negativeUnit.imag(), EPS);

        Complex plusImaginary = Complex.parse("+i");
        assertEquals(0.0, plusImaginary.real(), EPS);
        assertEquals(1.0, plusImaginary.imag(), EPS);

        assertThrows(NumberFormatException.class, () -> Complex.parse("2+"));

        Complex bare = Complex.parse("i");
        assertEquals(Complex.I, bare);
    }
}
