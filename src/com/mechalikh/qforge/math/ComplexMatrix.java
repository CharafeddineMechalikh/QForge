package com.mechalikh.qforge.math;

import java.util.Locale;
import java.util.Arrays;

/**
 * Mutable dense matrix of {@link Complex} entries, stored as a rectangular array
 * indexed by row and column.
 *
 * <p>Dimensions are fixed at construction. Constructor arrays are copied,
 * {@link #get(int, int)} returns a copy, and {@link #set(int, int, Complex)}
 * stores a copy, so no internal {@code Complex} instance is exposed.</p>
 *
 * <p>{@link #equals(Object)}, {@link #isHermitian()}, and {@link #isUnitary()}
 * use the tolerant {@link Complex#equals(Object)} comparison, which allows an
 * absolute difference of {@code 1e-10} per part.</p>
 */
public class ComplexMatrix {
    private final int rows;
    private final int cols;
    private final Complex[][] data;
    
    /**
     * Creates a zero matrix with the given dimensions.
     *
     * @param rows the number of rows, must be positive
     * @param cols the number of columns, must be positive
     * @throws IllegalArgumentException if {@code rows} or {@code cols} is not
     *         positive
     */
    public ComplexMatrix(int rows, int cols) {
        if (rows <= 0 || cols <= 0) {
            throw new IllegalArgumentException("Matrix dimensions must be positive");
        }
        this.rows = rows;
        this.cols = cols;
        this.data = new Complex[rows][cols];
        
        // Initialize with zeros
        for (int i = 0; i < rows; i++) {
            for (int j = 0; j < cols; j++) {
                data[i][j] = Complex.ZERO;
            }
        }
    }
    
    /**
     * Creates a matrix from a rectangular array of complex values. The array is
     * copied, so later changes to {@code data} do not affect this matrix.
     *
     * @param data the source values, indexed as {@code data[row][col]}
     * @throws IllegalArgumentException if {@code data} is null or empty, or if the
     *         rows do not all have the same length
     */
    public ComplexMatrix(Complex[][] data) {
        if (data == null || data.length == 0 || data[0].length == 0) {
            throw new IllegalArgumentException("Data array cannot be null or empty");
        }
        
        this.rows = data.length;
        this.cols = data[0].length;
        this.data = new Complex[rows][cols];
        
        // Copy and validate data
        for (int i = 0; i < rows; i++) {
            if (data[i].length != cols) {
                throw new IllegalArgumentException("All rows must have the same length");
            }
            for (int j = 0; j < cols; j++) {
                this.data[i][j] = new Complex(data[i][j]);
            }
        }
    }
    
    /**
     * Creates a matrix from a rectangular array of real values, using zero
     * imaginary parts. The array is copied.
     *
     * @param realData the source values, indexed as {@code realData[row][col]}
     * @throws IllegalArgumentException if {@code realData} is null or empty, or if
     *         the rows do not all have the same length
     */
    public ComplexMatrix(double[][] realData) {
        if (realData == null || realData.length == 0 || realData[0].length == 0) {
            throw new IllegalArgumentException("Data array cannot be null or empty");
        }
        
        this.rows = realData.length;
        this.cols = realData[0].length;
        this.data = new Complex[rows][cols];
        
        for (int i = 0; i < rows; i++) {
            if (realData[i].length != cols) {
                throw new IllegalArgumentException("All rows must have the same length");
            }
            for (int j = 0; j < cols; j++) {
                this.data[i][j] = new Complex(realData[i][j], 0.0);
            }
        }
    }
    
    /**
     * Returns the number of rows.
     *
     * @return the number of rows
     */
    public int rows() {
        return rows;
    }
    
    /**
     * Returns the number of columns.
     *
     * @return the number of columns
     */
    public int cols() {
        return cols;
    }
    
    /**
     * Returns the entry at the given position.
     *
     * @param row the row index
     * @param col the column index
     * @return a copy of the entry
     * @throws IndexOutOfBoundsException if either index is out of range
     */
    public Complex get(int row, int col) {
        checkIndices(row, col);
        return new Complex(data[row][col]);
    }
    
    /**
     * Stores a complex value at the given position. The value is copied.
     *
     * @param row the row index
     * @param col the column index
     * @param value the value to store
     * @throws IndexOutOfBoundsException if either index is out of range
     */
    public void set(int row, int col, Complex value) {
        checkIndices(row, col);
        data[row][col] = new Complex(value);
    }
    
    /**
     * Stores a complex value given by its parts at the given position.
     *
     * @param row the row index
     * @param col the column index
     * @param real the real part
     * @param imag the imaginary part
     * @throws IndexOutOfBoundsException if either index is out of range
     */
    public void set(int row, int col, double real, double imag) {
        set(row, col, new Complex(real, imag));
    }
    
    /**
     * Returns the element-wise sum of this matrix and another.
     *
     * @param other the matrix to add
     * @return a new matrix with the sums
     * @throws IllegalArgumentException if the dimensions do not match
     */
    public ComplexMatrix add(ComplexMatrix other) {
        checkSameDimensions(other);
        
        ComplexMatrix result = new ComplexMatrix(rows, cols);
        for (int i = 0; i < rows; i++) {
            for (int j = 0; j < cols; j++) {
                result.data[i][j] = this.data[i][j].add(other.data[i][j]);
            }
        }
        
        return result;
    }
    
    /**
     * Returns the element-wise difference of this matrix and another.
     *
     * @param other the matrix to subtract
     * @return a new matrix with the differences
     * @throws IllegalArgumentException if the dimensions do not match
     */
    public ComplexMatrix subtract(ComplexMatrix other) {
        checkSameDimensions(other);
        
        ComplexMatrix result = new ComplexMatrix(rows, cols);
        for (int i = 0; i < rows; i++) {
            for (int j = 0; j < cols; j++) {
                result.data[i][j] = this.data[i][j].subtract(other.data[i][j]);
            }
        }
        
        return result;
    }
    
    /**
     * Returns the matrix product {@code this * other}.
     *
     * @param other the right hand factor
     * @return a new matrix with {@code this.rows} rows and {@code other.cols}
     *         columns
     * @throws IllegalArgumentException if {@code this.cols} is not equal to
     *         {@code other.rows}
     */
    public ComplexMatrix multiply(ComplexMatrix other) {
        if (this.cols != other.rows) {
            throw new IllegalArgumentException(
                String.format(Locale.ROOT, "Matrix dimensions incompatible for multiplication: %dx%d * %dx%d",
                    rows, cols, other.rows, other.cols)
            );
        }
        
        ComplexMatrix result = new ComplexMatrix(rows, other.cols);
        
        for (int i = 0; i < rows; i++) {
            for (int j = 0; j < other.cols; j++) {
                Complex sum = Complex.ZERO;
                for (int k = 0; k < cols; k++) {
                    sum = sum.add(this.data[i][k].multiply(other.data[k][j]));
                }
                result.data[i][j] = sum;
            }
        }
        
        return result;
    }
    
    /**
     * Returns a new matrix with every entry multiplied by a complex scalar.
     *
     * @param scalar the scalar factor
     * @return a new scaled matrix
     */
    public ComplexMatrix multiply(Complex scalar) {
        ComplexMatrix result = new ComplexMatrix(rows, cols);
        for (int i = 0; i < rows; i++) {
            for (int j = 0; j < cols; j++) {
                result.data[i][j] = this.data[i][j].multiply(scalar);
            }
        }
        return result;
    }
    
    /**
     * Returns a new matrix with every entry multiplied by a real scalar.
     *
     * @param scalar the real scalar factor
     * @return a new scaled matrix
     */
    public ComplexMatrix scale(double scalar) {
        return multiply(new Complex(scalar, 0));
    }
    
    /**
     * Returns the transpose, with rows and columns exchanged. Entries are not
     * conjugated.
     *
     * @return a new matrix of dimensions {@code cols x rows}
     */
    public ComplexMatrix transpose() {
        ComplexMatrix result = new ComplexMatrix(cols, rows);
        for (int i = 0; i < rows; i++) {
            for (int j = 0; j < cols; j++) {
                result.data[j][i] = this.data[i][j];
            }
        }
        return result;
    }
    
    /**
     * Returns the conjugate transpose (adjoint).
     *
     * @return a new matrix of dimensions {@code cols x rows} with conjugated
     *         entries
     */
    public ComplexMatrix adjoint() {
        ComplexMatrix result = new ComplexMatrix(cols, rows);
        for (int i = 0; i < rows; i++) {
            for (int j = 0; j < cols; j++) {
                result.data[j][i] = this.data[i][j].conjugate();
            }
        }
        return result;
    }
    
    /**
     * Returns a new matrix with every entry conjugated.
     *
     * @return the element-wise conjugate
     */
    public ComplexMatrix conjugate() {
        ComplexMatrix result = new ComplexMatrix(rows, cols);
        for (int i = 0; i < rows; i++) {
            for (int j = 0; j < cols; j++) {
                result.data[i][j] = this.data[i][j].conjugate();
            }
        }
        return result;
    }
    
    /**
     * Returns the sum of the diagonal entries.
     *
     * @return the trace
     * @throws IllegalArgumentException if the matrix is not square
     */
    public Complex trace() {
        if (!isSquare()) {
            throw new IllegalArgumentException("Trace is only defined for square matrices");
        }
        
        Complex sum = Complex.ZERO;
        for (int i = 0; i < rows; i++) {
            sum = sum.add(data[i][i]);
        }
        return sum;
    }
    
    /**
     * Returns the Kronecker product of this matrix and another.
     *
     * <p>Entry {@code (i1, j1)} of this matrix is multiplied by the whole of
     * {@code other} and placed in the result block whose first row is
     * {@code i1 * other.rows} and first column is {@code j1 * other.cols}.</p>
     *
     * @param other the right hand factor
     * @return a new matrix with {@code rows * other.rows} rows and
     *         {@code cols * other.cols} columns
     */
    public ComplexMatrix tensorProduct(ComplexMatrix other) {
        int newRows = this.rows * other.rows;
        int newCols = this.cols * other.cols;
        ComplexMatrix result = new ComplexMatrix(newRows, newCols);
        
        for (int i1 = 0; i1 < this.rows; i1++) {
            for (int j1 = 0; j1 < this.cols; j1++) {
                Complex element = this.data[i1][j1];
                for (int i2 = 0; i2 < other.rows; i2++) {
                    for (int j2 = 0; j2 < other.cols; j2++) {
                        int newRow = i1 * other.rows + i2;
                        int newCol = j1 * other.cols + j2;
                        result.data[newRow][newCol] = element.multiply(other.data[i2][j2]);
                    }
                }
            }
        }
        
        return result;
    }
    
    /**
     * Tests whether the number of rows equals the number of columns.
     *
     * @return {@code true} if the matrix is square
     */
    public boolean isSquare() {
        return rows == cols;
    }
    
    /**
     * Tests whether this matrix is Hermitian, meaning it is square and each entry
     * equals the conjugate of its mirror entry within the tolerance of
     * {@link Complex#equals(Object)}.
     *
     * @return {@code true} if the matrix is square and Hermitian
     */
    public boolean isHermitian() {
        if (!isSquare()) return false;
        
        for (int i = 0; i < rows; i++) {
            for (int j = i; j < cols; j++) {
                if (!data[i][j].equals(data[j][i].conjugate())) {
                    return false;
                }
            }
        }
        return true;
    }
    
    /**
     * Tests whether this matrix is unitary by comparing {@code this * adjoint()}
     * with the identity matrix using {@link #equals(Object)}.
     *
     * @return {@code true} if the matrix is square and the product equals the
     *         identity within the entry tolerance
     */
    public boolean isUnitary() {
        if (!isSquare()) return false;
        
        ComplexMatrix product = this.multiply(this.adjoint());
        ComplexMatrix identity = ComplexMatrix.identity(rows);
        
        return product.equals(identity);
    }
    
    /**
     * Returns a deep copy of this matrix.
     *
     * @return a new matrix with the same dimensions and entry values
     */
    public ComplexMatrix copy() {
        return new ComplexMatrix(data);
    }
    
    /**
     * Returns the real part of every entry.
     *
     * @return a new array of dimensions {@code rows x cols}
     */
    public double[][] getRealParts() {
        double[][] realParts = new double[rows][cols];
        for (int i = 0; i < rows; i++) {
            for (int j = 0; j < cols; j++) {
                realParts[i][j] = data[i][j].real();
            }
        }
        return realParts;
    }
    
    /**
     * Returns the imaginary part of every entry.
     *
     * @return a new array of dimensions {@code rows x cols}
     */
    public double[][] getImaginaryParts() {
        double[][] imagParts = new double[rows][cols];
        for (int i = 0; i < rows; i++) {
            for (int j = 0; j < cols; j++) {
                imagParts[i][j] = data[i][j].imag();
            }
        }
        return imagParts;
    }
    
    // Static factory methods
    /**
     * Creates an identity matrix.
     *
     * @param size the number of rows and columns
     * @return a new identity matrix of the given size
     * @throws IllegalArgumentException if {@code size} is not positive
     */
    public static ComplexMatrix identity(int size) {
        ComplexMatrix matrix = new ComplexMatrix(size, size);
        for (int i = 0; i < size; i++) {
            matrix.data[i][i] = Complex.ONE;
        }
        return matrix;
    }
    
    /**
     * Creates a zero matrix.
     *
     * @param rows the number of rows, must be positive
     * @param cols the number of columns, must be positive
     * @return a new zero matrix
     * @throws IllegalArgumentException if {@code rows} or {@code cols} is not
     *         positive
     */
    public static ComplexMatrix zero(int rows, int cols) {
        return new ComplexMatrix(rows, cols);
    }
    
    /**
     * Creates a complex matrix from real values.
     *
     * @param realData the source values, indexed as {@code realData[row][col]}
     * @return a new matrix with zero imaginary parts
     * @throws IllegalArgumentException if {@code realData} is null or empty, or if
     *         the rows do not all have the same length
     */
    public static ComplexMatrix fromReal(double[][] realData) {
        return new ComplexMatrix(realData);
    }
    
    /**
     * Returns the Pauli X matrix {@code [[0, 1], [1, 0]]}.
     *
     * @return a new {@code 2 x 2} matrix
     */
    public static ComplexMatrix pauliX() {
        ComplexMatrix matrix = new ComplexMatrix(2, 2);
        matrix.set(0, 0, Complex.ZERO);
        matrix.set(0, 1, Complex.ONE);
        matrix.set(1, 0, Complex.ONE);
        matrix.set(1, 1, Complex.ZERO);
        return matrix;
    }
    
    /**
     * Returns the Pauli Y matrix {@code [[0, -i], [i, 0]]}.
     *
     * @return a new {@code 2 x 2} matrix
     */
    public static ComplexMatrix pauliY() {
        ComplexMatrix matrix = new ComplexMatrix(2, 2);
        matrix.set(0, 0, Complex.ZERO);
        matrix.set(0, 1, new Complex(0, -1));
        matrix.set(1, 0, new Complex(0, 1));
        matrix.set(1, 1, Complex.ZERO);
        return matrix;
    }
    
    /**
     * Returns the Pauli Z matrix {@code [[1, 0], [0, -1]]}.
     *
     * @return a new {@code 2 x 2} matrix
     */
    public static ComplexMatrix pauliZ() {
        ComplexMatrix matrix = new ComplexMatrix(2, 2);
        matrix.set(0, 0, Complex.ONE);
        matrix.set(0, 1, Complex.ZERO);
        matrix.set(1, 0, Complex.ZERO);
        matrix.set(1, 1, new Complex(-1, 0));
        return matrix;
    }
    
    /**
     * Returns the Hadamard matrix {@code 1/sqrt(2) * [[1, 1], [1, -1]]}.
     *
     * @return a new {@code 2 x 2} matrix
     */
    public static ComplexMatrix hadamard() {
        double factor = 1.0 / Math.sqrt(2);
        ComplexMatrix matrix = new ComplexMatrix(2, 2);
        matrix.set(0, 0, new Complex(factor, 0));
        matrix.set(0, 1, new Complex(factor, 0));
        matrix.set(1, 0, new Complex(factor, 0));
        matrix.set(1, 1, new Complex(-factor, 0));
        return matrix;
    }
    
    private void checkIndices(int row, int col) {
        if (row < 0 || row >= rows) {
            throw new IndexOutOfBoundsException("Row index out of bounds: " + row);
        }
        if (col < 0 || col >= cols) {
            throw new IndexOutOfBoundsException("Column index out of bounds: " + col);
        }
    }
    
    private void checkSameDimensions(ComplexMatrix other) {
        if (this.rows != other.rows || this.cols != other.cols) {
            throw new IllegalArgumentException(
                String.format(Locale.ROOT, "Matrix dimensions don't match: %dx%d vs %dx%d",
                    rows, cols, other.rows, other.cols)
            );
        }
    }
    
    /**
     * Compares this matrix with another object entry by entry using the tolerant
     * {@link Complex#equals(Object)}.
     *
     * @param obj the object to compare with
     * @return {@code true} if {@code obj} is a {@code ComplexMatrix} with the same
     *         dimensions and all entries equal within tolerance
     */
    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        
        ComplexMatrix other = (ComplexMatrix) obj;
        if (rows != other.rows || cols != other.cols) return false;
        
        for (int i = 0; i < rows; i++) {
            for (int j = 0; j < cols; j++) {
                if (!data[i][j].equals(other.data[i][j])) {
                    return false;
                }
            }
        }
        
        return true;
    }
    
    /**
     * Returns a hash code based on the dimensions and the exact entry values.
     *
     * <p>Because {@link #equals(Object)} uses a tolerance, matrices that compare
     * equal may have different hash codes.</p>
     *
     * @return the hash code
     */
    @Override
    public int hashCode() {
        int result = rows;
        result = 31 * result + cols;
        result = 31 * result + Arrays.deepHashCode(data);
        return result;
    }
    
    /**
     * Returns a multi-line summary showing at most the first six rows and six
     * columns.
     *
     * @return the formatted summary
     */
    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("ComplexMatrix[").append(rows).append("x").append(cols).append("]:\n");
        
        for (int i = 0; i < Math.min(rows, 6); i++) {
            sb.append("  [");
            for (int j = 0; j < Math.min(cols, 6); j++) {
                if (j > 0) sb.append(", ");
                sb.append(data[i][j]);
            }
            if (cols > 6) sb.append(", ...");
            sb.append("]\n");
        }
        
        if (rows > 6) {
            sb.append("  ...\n");
        }
        
        return sb.toString();
    }
    
    
    /**
     * Computes the eigenvalues as real numbers.
     *
     * <p>For Hermitian matrices the values are the real diagonal entries produced
     * by the cyclic Jacobi eigendecomposition. For other matrices a power iteration
     * based estimate is used. In both cases only real parts are returned and the
     * array is sorted in ascending order.</p>
     *
     * @return the eigenvalues sorted in ascending order
     * @throws IllegalArgumentException if the matrix is not square
     */
    public double[] eigenvalues() {
        if (!isSquare()) {
            throw new IllegalArgumentException("Matrix must be square to compute eigenvalues");
        }
        
        if (isHermitian()) {
            return eigenDecompositionHermitian()[0].diagonalRealParts();
        }
        return eigenvaluesIterative();
    }
    
    private double[] diagonalRealParts() {
        double[] result = new double[Math.min(rows, cols)];
        for (int i = 0; i < result.length; i++) {
            result[i] = data[i][i].real();
        }
        return result;
    }
    
    private double[] eigenvalues2x2() {
        // Solve for eigenvalues of 2x2 matrix: det(A - λI) = 0
        Complex a = data[0][0];
        Complex b = data[0][1];
        Complex c = data[1][0];
        Complex d = data[1][1];
        
        // Trace and determinant
        Complex trace = a.add(d);
        Complex det = a.multiply(d).subtract(b.multiply(c));
        
        // λ = (trace +/- sqrt(trace² - 4*det)) / 2
        Complex discriminant = trace.multiply(trace)
            .subtract(det.multiply(new Complex(4, 0)));
        
        Complex sqrtDisc = sqrt(discriminant);
        
        Complex lambda1 = trace.add(sqrtDisc).divide(2);
        Complex lambda2 = trace.subtract(sqrtDisc).divide(2);
        
        return new double[]{lambda1.real(), lambda2.real()};
    }
    
    private double[] eigenvaluesIterative() {
        int n = rows;
        double[] eigenvalues = new double[n];
        
        // Power iteration for dominant eigenvalue
        Complex[] x = new Complex[n];
        Arrays.fill(x, new Complex(1.0 / Math.sqrt(n), 0));
        
        for (int i = 0; i < n; i++) {
            // Run power iteration for each eigenvalue
            double eigenvalue = powerIteration(i);
            eigenvalues[i] = eigenvalue;
            
            // Deflate the matrix (simplified)
            // In real implementation, use proper deflation
        }
        
        Arrays.sort(eigenvalues);
        return eigenvalues;
    }
    
    private double powerIteration(int maxIterations) {
        int n = rows;
        Complex[] x = randomComplexVector(n);
        normalizeVector(x);
        
        for (int iter = 0; iter < maxIterations; iter++) {
            // x = A * x
            Complex[] Ax = multiplyVector(x);
            
            // Rayleigh quotient: λ = (xᴴ A x) / (xᴴ x)
            Complex numerator = Complex.ZERO;
            Complex denominator = Complex.ZERO;
            
            for (int i = 0; i < n; i++) {
                numerator = numerator.add(x[i].conjugate().multiply(Ax[i]));
                denominator = denominator.add(x[i].conjugate().multiply(x[i]));
            }
            
            Complex lambda = numerator.divide(denominator);
            
            // Normalize for next iteration
            normalizeVector(Ax);
            x = Ax;
            
            // Check convergence (simplified)
            if (iter > 10) {
                return lambda.real();
            }
        }
        
        // Return best estimate
        Complex[] Ax = multiplyVector(x);
        Complex lambda = Complex.ZERO;
        for (int i = 0; i < n; i++) {
            lambda = lambda.add(x[i].conjugate().multiply(Ax[i]));
        }
        
        return lambda.real();
    }
    
    /**
     * Computes an eigendecomposition.
     *
     * <p>For Hermitian matrices a cyclic Jacobi method is used. Each off-diagonal
     * pair is first made real with a phase rotation and then zeroed with a real
     * Jacobi rotation. Iteration stops after 100 sweeps or when the sum of the
     * squared magnitudes of the upper triangle entries is less than
     * {@code 1e-28}. The resulting eigenvalues are real and sorted in ascending
     * order, and column {@code k} of the eigenvector matrix belongs to diagonal
     * entry {@code k} of the eigenvalue matrix.</p>
     *
     * <p>For non-Hermitian matrices a QR iteration with at most 50 iterations is
     * attempted, stopping when the matrix is upper triangular within a tolerance
     * of {@code 1e-6}. If that computation throws an exception, the method falls
     * back to estimated diagonal eigenvalues and the identity eigenvector
     * matrix.</p>
     *
     * @return a two element array with the diagonal eigenvalue matrix at index 0
     *         and the eigenvector matrix at index 1
     * @throws IllegalArgumentException if the matrix is not square
     */
    public ComplexMatrix[] eigenDecomposition() {
        if (!isSquare()) {
            throw new IllegalArgumentException("Matrix must be square for eigen decomposition");
        }
        
        int n = rows;
        
        // For Hermitian matrices (which observables are), we can use simpler methods
        if (isHermitian()) {
            return eigenDecompositionHermitian();
        } else {
            // For non-Hermitian, use simplified approach
            return eigenDecompositionGeneral();
        }
    }
    
    private ComplexMatrix[] eigenDecompositionHermitian() {
        int n = rows;
        ComplexMatrix a = copy();
        ComplexMatrix v = ComplexMatrix.identity(n);
        
        double tolerance = 1e-14;
        int maxSweeps = 100;
        
        for (int sweep = 0; sweep < maxSweeps; sweep++) {
            double offDiagonal = 0.0;
            for (int p = 0; p < n; p++) {
                for (int q = p + 1; q < n; q++) {
                    offDiagonal += a.data[p][q].absSquare();
                }
            }
            if (offDiagonal < tolerance * tolerance) {
                break;
            }
            
            for (int p = 0; p < n - 1; p++) {
                for (int q = p + 1; q < n; q++) {
                    Complex apq = a.data[p][q];
                    if (apq.abs() < 1e-16) {
                        continue;
                    }
                    
                    // Phase rotation U = diag(e^{-i*phase}, 1) makes A_pq real.
                    double phase = apq.phase();
                    Complex phaseMinus = new Complex(Math.cos(-phase), Math.sin(-phase));
                    Complex phasePlus = new Complex(Math.cos(phase), Math.sin(phase));
                    
                    for (int k = 0; k < n; k++) {
                        a.data[p][k] = a.data[p][k].multiply(phaseMinus);
                    }
                    for (int k = 0; k < n; k++) {
                        a.data[k][p] = a.data[k][p].multiply(phasePlus);
                    }
                    
                    double magnitude = apq.abs();
                    a.data[p][q] = new Complex(magnitude, 0);
                    a.data[q][p] = new Complex(magnitude, 0);
                    
                    // Real Jacobi rotation zeroes the remaining real off-diagonal element.
                    double app = a.data[p][p].real();
                    double aqq = a.data[q][q].real();
                    double theta = 0.5 * Math.atan2(2.0 * magnitude, app - aqq);
                    double c = Math.cos(theta);
                    double s = Math.sin(theta);
                    
                    for (int k = 0; k < n; k++) {
                        Complex bpk = a.data[p][k];
                        Complex bqk = a.data[q][k];
                        a.data[p][k] = bpk.multiply(c).add(bqk.multiply(s));
                        a.data[q][k] = bqk.multiply(c).subtract(bpk.multiply(s));
                    }
                    for (int k = 0; k < n; k++) {
                        Complex bkp = a.data[k][p];
                        Complex bkq = a.data[k][q];
                        a.data[k][p] = bkp.multiply(c).add(bkq.multiply(s));
                        a.data[k][q] = bkq.multiply(c).subtract(bkp.multiply(s));
                    }
                    
                    a.data[p][q] = Complex.ZERO;
                    a.data[q][p] = Complex.ZERO;
                    
                    // Accumulate V <- V * U^dagger * J.
                    for (int k = 0; k < n; k++) {
                        v.data[k][p] = v.data[k][p].multiply(phasePlus);
                    }
                    for (int k = 0; k < n; k++) {
                        Complex vpk = v.data[k][p];
                        Complex vqk = v.data[k][q];
                        v.data[k][p] = vpk.multiply(c).add(vqk.multiply(s));
                        v.data[k][q] = vqk.multiply(c).subtract(vpk.multiply(s));
                    }
                }
            }
        }
        
        double[] values = new double[n];
        for (int i = 0; i < n; i++) {
            values[i] = a.data[i][i].real();
        }
        
        Integer[] order = new Integer[n];
        for (int i = 0; i < n; i++) {
            order[i] = i;
        }
        Arrays.sort(order, (x, y) -> Double.compare(values[x], values[y]));
        
        ComplexMatrix eigenvaluesMatrix = new ComplexMatrix(n, n);
        ComplexMatrix eigenvectors = new ComplexMatrix(n, n);
        for (int newIndex = 0; newIndex < n; newIndex++) {
            int oldIndex = order[newIndex];
            eigenvaluesMatrix.set(newIndex, newIndex, new Complex(values[oldIndex], 0));
            for (int row = 0; row < n; row++) {
                eigenvectors.set(row, newIndex, v.data[row][oldIndex]);
            }
        }
        
        return new ComplexMatrix[]{eigenvaluesMatrix, eigenvectors};
    }
    
    private ComplexMatrix[] eigenDecompositionGeneral() {
        int n = rows;
        
        // Placeholder for general matrices
        ComplexMatrix eigenvaluesMatrix = new ComplexMatrix(n, n);
        ComplexMatrix eigenvectors = ComplexMatrix.identity(n);
        
        // Try to compute using simplified QR algorithm
        try {
            return qrAlgorithm(50); // 50 iterations
        } catch (Exception e) {
            // Fall back to identity
            double[] evals = eigenvalues();
            for (int i = 0; i < n; i++) {
                eigenvaluesMatrix.set(i, i, new Complex(evals[i], 0));
            }
        }
        
        return new ComplexMatrix[]{eigenvaluesMatrix, eigenvectors};
    }
    
    private ComplexMatrix[] qrAlgorithm(int maxIterations) {
        int n = rows;
        ComplexMatrix A = this.copy();
        ComplexMatrix Qtotal = ComplexMatrix.identity(n);
        
        for (int iter = 0; iter < maxIterations; iter++) {
            // QR decomposition
            ComplexMatrix[] qr = qrDecomposition(A);
            ComplexMatrix Q = qr[0];
            ComplexMatrix R = qr[1];
            
            // A = R * Q
            A = R.multiply(Q);
            
            // Accumulate Q matrices
            Qtotal = Qtotal.multiply(Q);
            
            // Check for convergence (simplified)
            if (isUpperTriangular(A, 1e-6)) {
                break;
            }
        }
        
        // A should now be approximately upper triangular (Schur form)
        // Diagonal elements are eigenvalues
        ComplexMatrix eigenvaluesMatrix = new ComplexMatrix(n, n);
        for (int i = 0; i < n; i++) {
            eigenvaluesMatrix.set(i, i, A.get(i, i));
        }
        
        return new ComplexMatrix[]{eigenvaluesMatrix, Qtotal};
    }
    
    private ComplexMatrix[] qrDecomposition(ComplexMatrix matrix) {
        int m = matrix.rows();
        int n = matrix.cols();
        
        ComplexMatrix Q = new ComplexMatrix(m, n);
        ComplexMatrix R = new ComplexMatrix(n, n);
        
        // Gram-Schmidt process (simplified)
        ComplexMatrix A = matrix.copy();
        
        for (int j = 0; j < n; j++) {
            // j-th column of A
            Complex[] v = new Complex[m];
            for (int i = 0; i < m; i++) {
                v[i] = A.get(i, j);
            }
            
            // Orthogonalize against previous columns
            for (int k = 0; k < j; k++) {
                // Projection of v onto q_k
                Complex projection = Complex.ZERO;
                for (int i = 0; i < m; i++) {
                    projection = projection.add(Q.get(i, k).conjugate().multiply(v[i]));
                }
                
                R.set(k, j, projection);
                
                // Subtract projection
                for (int i = 0; i < m; i++) {
                    v[i] = v[i].subtract(Q.get(i, k).multiply(projection));
                }
            }
            
            // Normalize v to get q_j
            double norm = 0.0;
            for (int i = 0; i < m; i++) {
                norm += v[i].absSquare();
            }
            norm = Math.sqrt(norm);
            
            if (norm > 1e-10) {
                R.set(j, j, new Complex(norm, 0));
                for (int i = 0; i < m; i++) {
                    Q.set(i, j, v[i].divide(norm));
                }
            } else {
                R.set(j, j, Complex.ZERO);
                for (int i = 0; i < m; i++) {
                    Q.set(i, j, Complex.ZERO);
                }
            }
        }
        
        return new ComplexMatrix[]{Q, R};
    }
    
    private boolean isUpperTriangular(ComplexMatrix matrix, double tolerance) {
        int n = matrix.rows();
        for (int i = 0; i < n; i++) {
            for (int j = 0; j < i; j++) {
                if (matrix.get(i, j).abs() > tolerance) {
                    return false;
                }
            }
        }
        return true;
    }
    
    // Helper methods
    private Complex[] multiplyVector(Complex[] vector) {
        int n = rows;
        Complex[] result = new Complex[n];
        Arrays.fill(result, Complex.ZERO);
        
        for (int i = 0; i < n; i++) {
            for (int j = 0; j < n; j++) {
                result[i] = result[i].add(data[i][j].multiply(vector[j]));
            }
        }
        
        return result;
    }
    
    private Complex[] randomComplexVector(int size) {
        Complex[] vector = new Complex[size];
        for (int i = 0; i < size; i++) {
            vector[i] = new Complex(Math.random(), Math.random());
        }
        return vector;
    }
    
    private void normalizeVector(Complex[] vector) {
        double norm = 0.0;
        for (Complex c : vector) {
            norm += c.absSquare();
        }
        norm = Math.sqrt(norm);
        
        if (norm > 1e-10) {
            for (int i = 0; i < vector.length; i++) {
                vector[i] = vector[i].divide(norm);
            }
        }
    }
    
    private Complex sqrt(Complex c) {
        // Square root of complex number
        double r = Math.sqrt(c.abs());
        double theta = c.phase() / 2;
        return new Complex(r * Math.cos(theta), r * Math.sin(theta));
    }
}