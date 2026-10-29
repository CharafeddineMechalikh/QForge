package com.mechalikh.qforge.math;

import java.util.ArrayList;
import java.util.List;
import org.apache.commons.math3.linear.Array2DRowRealMatrix;
import org.apache.commons.math3.linear.RealMatrix;
import org.apache.commons.math3.linear.SingularValueDecomposition;

/**
 * Thin wrapper around a real singular value decomposition that factorizes a complex matrix.
 *
 * <p>A complex matrix {@code A} of size {@code m} by {@code n} is embedded in the real block
 * matrix {@code [[Re(A), -Im(A)], [Im(A), Re(A)]]}, which has size {@code 2m} by {@code 2n}.
 * The real decomposition of that block is computed with
 * {@link org.apache.commons.math3.linear.SingularValueDecomposition}. Every singular value
 * of the block occurs twice, because the block is the realification of a complex matrix.
 * The real singular vectors are grouped by singular value, and each group is converted into
 * complex vectors by pairing the upper half of a real vector with its lower half. A real
 * Gram-Schmidt pass keeps one member of each dependent pair, which makes the conversion
 * correct for degenerate singular values as well. The result satisfies
 * {@code A = U * diag(singularValues) * VAdjoint} up to floating point rounding.</p>
 *
 * <p>The returned matrices have the thin dimensions {@code U} is {@code m} by {@code k},
 * the singular values have length {@code k}, and {@code VAdjoint} is {@code k} by {@code n},
 * where {@code k = min(m, n)}. Singular values are ordered from largest to smallest.</p>
 */
public final class ComplexSvd {
    private static final double GROUP_TOLERANCE = 1e-12;
    private static final double ZERO_TOLERANCE = 1e-14;
    private static final double INDEPENDENCE_TOLERANCE = 1e-10;

    private ComplexSvd() {
    }

    /**
     * Factorizes a complex matrix as {@code U * diag(singularValues) * VAdjoint}.
     *
     * <p>The matrix is not modified. The returned object holds independent copies of the
     * factors and the singular values.</p>
     *
     * @param matrix the complex matrix to factorize, with at least one row and one column
     * @return the complex singular value decomposition
     * @throws IllegalArgumentException if {@code matrix} is null
     */
    public static Result decompose(ComplexMatrix matrix) {
        if (matrix == null) {
            throw new IllegalArgumentException("Matrix must not be null");
        }

        int m = matrix.rows();
        int n = matrix.cols();
        double[][] block = new double[2 * m][2 * n];
        for (int row = 0; row < m; row++) {
            for (int col = 0; col < n; col++) {
                Complex value = matrix.get(row, col);
                double real = value.real();
                double imaginary = value.imag();
                block[row][col] = real;
                block[row][n + col] = -imaginary;
                block[m + row][col] = imaginary;
                block[m + row][n + col] = real;
            }
        }

        SingularValueDecomposition realSvd =
            new SingularValueDecomposition(new Array2DRowRealMatrix(block, false));
        RealMatrix realU = realSvd.getU();
        RealMatrix realV = realSvd.getV();
        double[] realSingularValues = realSvd.getSingularValues();

        int k = Math.min(m, n);
        double largest = realSingularValues.length == 0 ? 0.0 : realSingularValues[0];
        double groupTolerance = largest > 0.0 ? GROUP_TOLERANCE * largest : Double.MAX_VALUE;
        double zeroTolerance = largest > 0.0 ? ZERO_TOLERANCE * largest : Double.MAX_VALUE;

        List<Complex[]> leftVectors = new ArrayList<>();
        List<Complex[]> rightVectors = new ArrayList<>();
        List<Double> singularValues = new ArrayList<>();

        int firstPair = 0;
        while (firstPair + 1 < realSingularValues.length && rightVectors.size() < k) {
            int end = firstPair + 2;
            while (end + 1 < realSingularValues.length
                    && Math.abs(realSingularValues[end] - realSingularValues[firstPair])
                        <= groupTolerance) {
                end += 2;
            }
            int needed = Math.min((end - firstPair) / 2, k - rightVectors.size());
            if (needed > 0) {
                List<double[]> rawRight = new ArrayList<>();
                List<double[]> rawLeft = new ArrayList<>();
                double groupValue = 0.0;
                for (int index = firstPair; index < end; index++) {
                    rawRight.add(realColumn(realV, index));
                    rawLeft.add(realColumn(realU, index));
                    groupValue += realSingularValues[index];
                }
                groupValue /= (end - firstPair);
                if (groupValue <= zeroTolerance) {
                    appendZeroTriplets(rawRight, rawLeft, needed, m, n,
                        leftVectors, rightVectors, singularValues);
                } else {
                    appendTriplets(rawRight, rawLeft, needed, m, n,
                        groupValue, leftVectors, rightVectors, singularValues);
                }
            }
            firstPair = end;
        }

        if (rightVectors.size() < k) {
            throw new IllegalStateException(
                "The real singular value decomposition produced too few complex vectors");
        }

        orthonormalize(rightVectors);
        orthonormalize(leftVectors);

        ComplexMatrix u = new ComplexMatrix(m, k);
        ComplexMatrix vAdjoint = new ComplexMatrix(k, n);
        double[] resultValues = new double[k];
        for (int index = 0; index < k; index++) {
            resultValues[index] = singularValues.get(index);
            Complex[] left = leftVectors.get(index);
            Complex[] right = rightVectors.get(index);
            for (int row = 0; row < m; row++) {
                u.set(row, index, left[row]);
            }
            for (int row = 0; row < n; row++) {
                vAdjoint.set(index, row, right[row].conjugate());
            }
        }

        return new Result(u, resultValues, vAdjoint);
    }

    private static void appendTriplets(List<double[]> rawRight, List<double[]> rawLeft,
            int needed, int m, int n, double singularValue, List<Complex[]> leftVectors,
            List<Complex[]> rightVectors, List<Double> singularValues) {
        List<double[]> selectedRight = new ArrayList<>();
        List<double[]> selectedLeft = new ArrayList<>();
        extractPairedBasis(rawRight, rawLeft, needed, selectedRight, selectedLeft);
        if (selectedRight.size() < needed) {
            throw new IllegalStateException(
                "The real singular value decomposition produced too few complex vectors");
        }
        for (int index = 0; index < needed; index++) {
            rightVectors.add(complexVector(selectedRight.get(index), n));
            leftVectors.add(complexVector(selectedLeft.get(index), m));
            singularValues.add(singularValue);
        }
    }

    private static void appendZeroTriplets(List<double[]> rawRight, List<double[]> rawLeft,
            int needed, int m, int n, List<Complex[]> leftVectors,
            List<Complex[]> rightVectors, List<Double> singularValues) {
        List<double[]> selectedRight = extractIndependentBasis(rawRight, needed);
        List<double[]> selectedLeft = extractIndependentBasis(rawLeft, needed);
        if (selectedRight.size() < needed || selectedLeft.size() < needed) {
            throw new IllegalStateException(
                "The real singular value decomposition produced too few null vectors");
        }
        for (int index = 0; index < needed; index++) {
            rightVectors.add(complexVector(selectedRight.get(index), n));
            leftVectors.add(complexVector(selectedLeft.get(index), m));
            singularValues.add(0.0);
        }
    }

    private static List<double[]> extractIndependentBasis(List<double[]> rawVectors, int limit) {
        List<double[]> selected = new ArrayList<>();
        List<double[]> basis = new ArrayList<>();
        for (double[] candidate : rawVectors) {
            if (selected.size() >= limit) {
                break;
            }
            double[] vector = candidate.clone();
            for (double[] existing : basis) {
                double coefficient = dot(vector, existing);
                subtract(vector, existing, coefficient);
            }
            double length = length(vector);
            if (length > INDEPENDENCE_TOLERANCE) {
                scale(vector, 1.0 / length);
                selected.add(vector);
                basis.add(vector);
                basis.add(multiplyByImaginaryUnit(vector));
            }
        }
        return selected;
    }

    private static void extractPairedBasis(List<double[]> rawRight, List<double[]> rawLeft,
            int limit, List<double[]> selectedRight, List<double[]> selectedLeft) {
        List<double[]> basisRight = new ArrayList<>();
        List<double[]> basisLeft = new ArrayList<>();
        for (int index = 0; index < rawRight.size() && selectedRight.size() < limit; index++) {
            double[] right = rawRight.get(index).clone();
            double[] left = rawLeft.get(index).clone();
            for (int basis = 0; basis < basisRight.size(); basis++) {
                double coefficient = dot(right, basisRight.get(basis));
                subtract(right, basisRight.get(basis), coefficient);
                subtract(left, basisLeft.get(basis), coefficient);
            }
            double length = length(right);
            if (length > INDEPENDENCE_TOLERANCE) {
                scale(right, 1.0 / length);
                scale(left, 1.0 / length);
                selectedRight.add(right);
                selectedLeft.add(left);
                basisRight.add(right);
                basisLeft.add(left);
                basisRight.add(multiplyByImaginaryUnit(right));
                basisLeft.add(multiplyByImaginaryUnit(left));
            }
        }
    }

    private static void orthonormalize(List<Complex[]> vectors) {
        for (int index = 0; index < vectors.size(); index++) {
            Complex[] vector = vectors.get(index);
            for (int previous = 0; previous < index; previous++) {
                Complex[] existing = vectors.get(previous);
                Complex coefficient = Complex.ZERO;
                for (int entry = 0; entry < vector.length; entry++) {
                    coefficient = coefficient.add(
                        existing[entry].conjugate().multiply(vector[entry]));
                }
                for (int entry = 0; entry < vector.length; entry++) {
                    vector[entry] = vector[entry].subtract(
                        coefficient.multiply(existing[entry]));
                }
            }
            double length = 0.0;
            for (Complex value : vector) {
                length += value.absSquare();
            }
            length = Math.sqrt(length);
            if (length <= INDEPENDENCE_TOLERANCE) {
                throw new IllegalStateException(
                    "The complex singular vectors are not linearly independent");
            }
            for (int entry = 0; entry < vector.length; entry++) {
                vector[entry] = vector[entry].multiply(1.0 / length);
            }
        }
    }

    private static double[] realColumn(RealMatrix matrix, int column) {
        int rows = matrix.getRowDimension();
        double[] values = new double[rows];
        for (int row = 0; row < rows; row++) {
            values[row] = matrix.getEntry(row, column);
        }
        return values;
    }

    private static Complex[] complexVector(double[] values, int dimension) {
        Complex[] result = new Complex[dimension];
        for (int index = 0; index < dimension; index++) {
            result[index] = new Complex(values[index], values[dimension + index]);
        }
        return result;
    }

    private static double[] multiplyByImaginaryUnit(double[] values) {
        int half = values.length / 2;
        double[] result = new double[values.length];
        for (int index = 0; index < half; index++) {
            result[index] = -values[half + index];
            result[half + index] = values[index];
        }
        return result;
    }

    private static double dot(double[] first, double[] second) {
        double sum = 0.0;
        for (int index = 0; index < first.length; index++) {
            sum += first[index] * second[index];
        }
        return sum;
    }

    private static double length(double[] values) {
        return Math.sqrt(dot(values, values));
    }

    private static void scale(double[] values, double factor) {
        for (int index = 0; index < values.length; index++) {
            values[index] *= factor;
        }
    }

    private static void subtract(double[] target, double[] source, double factor) {
        for (int index = 0; index < target.length; index++) {
            target[index] -= factor * source[index];
        }
    }

    /**
     * Immutable result of a complex singular value decomposition.
     *
     * <p>The factors satisfy {@code A = U * diag(singularValues) * VAdjoint}. Accessors
     * return copies, so changing an array returned by this class does not affect the stored
     * decomposition.</p>
     */
    public static final class Result {
        private final ComplexMatrix u;
        private final double[] singularValues;
        private final ComplexMatrix vAdjoint;

        private Result(ComplexMatrix u, double[] singularValues, ComplexMatrix vAdjoint) {
            this.u = u;
            this.singularValues = singularValues.clone();
            this.vAdjoint = vAdjoint;
        }

        /**
         * Returns the left singular vectors.
         *
         * @return a matrix of size {@code m} by {@code k}
         */
        public ComplexMatrix getU() {
            return u.copy();
        }

        /**
         * Returns the singular values in descending order.
         *
         * @return an array of length {@code k}
         */
        public double[] getSingularValues() {
            return singularValues.clone();
        }

        /**
         * Returns the adjoint of the right singular vector matrix.
         *
         * <p>This is the factor named {@code VAdjoint} in
         * {@code A = U * diag(singularValues) * VAdjoint}.</p>
         *
         * @return a matrix of size {@code k} by {@code n}
         */
        public ComplexMatrix getVAdjoint() {
            return vAdjoint.copy();
        }

        /**
         * Returns the right singular vector matrix, without the adjoint.
         *
         * <p>This is the adjoint of {@link #getVAdjoint()} and has size {@code n} by
         * {@code k}.</p>
         *
         * @return a matrix of size {@code n} by {@code k}
         */
        public ComplexMatrix getV() {
            return vAdjoint.adjoint();
        }
    }
}
