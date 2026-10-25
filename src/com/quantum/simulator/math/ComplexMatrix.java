package com.quantum.simulator.math;

import java.util.Locale;
import java.util.Arrays;

public class ComplexMatrix {
    private final int rows;
    private final int cols;
    private final Complex[][] data;
    
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
    
    public int rows() {
        return rows;
    }
    
    public int cols() {
        return cols;
    }
    
    public Complex get(int row, int col) {
        checkIndices(row, col);
        return new Complex(data[row][col]);
    }
    
    public void set(int row, int col, Complex value) {
        checkIndices(row, col);
        data[row][col] = new Complex(value);
    }
    
    public void set(int row, int col, double real, double imag) {
        set(row, col, new Complex(real, imag));
    }
    
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
    
    public ComplexMatrix multiply(Complex scalar) {
        ComplexMatrix result = new ComplexMatrix(rows, cols);
        for (int i = 0; i < rows; i++) {
            for (int j = 0; j < cols; j++) {
                result.data[i][j] = this.data[i][j].multiply(scalar);
            }
        }
        return result;
    }
    
    public ComplexMatrix scale(double scalar) {
        return multiply(new Complex(scalar, 0));
    }
    
    public ComplexMatrix transpose() {
        ComplexMatrix result = new ComplexMatrix(cols, rows);
        for (int i = 0; i < rows; i++) {
            for (int j = 0; j < cols; j++) {
                result.data[j][i] = this.data[i][j];
            }
        }
        return result;
    }
    
    public ComplexMatrix adjoint() {
        ComplexMatrix result = new ComplexMatrix(cols, rows);
        for (int i = 0; i < rows; i++) {
            for (int j = 0; j < cols; j++) {
                result.data[j][i] = this.data[i][j].conjugate();
            }
        }
        return result;
    }
    
    public ComplexMatrix conjugate() {
        ComplexMatrix result = new ComplexMatrix(rows, cols);
        for (int i = 0; i < rows; i++) {
            for (int j = 0; j < cols; j++) {
                result.data[i][j] = this.data[i][j].conjugate();
            }
        }
        return result;
    }
    
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
    
    public boolean isSquare() {
        return rows == cols;
    }
    
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
    
    public boolean isUnitary() {
        if (!isSquare()) return false;
        
        ComplexMatrix product = this.multiply(this.adjoint());
        ComplexMatrix identity = ComplexMatrix.identity(rows);
        
        return product.equals(identity);
    }
    
    public ComplexMatrix copy() {
        return new ComplexMatrix(data);
    }
    
    public double[][] getRealParts() {
        double[][] realParts = new double[rows][cols];
        for (int i = 0; i < rows; i++) {
            for (int j = 0; j < cols; j++) {
                realParts[i][j] = data[i][j].real();
            }
        }
        return realParts;
    }
    
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
    public static ComplexMatrix identity(int size) {
        ComplexMatrix matrix = new ComplexMatrix(size, size);
        for (int i = 0; i < size; i++) {
            matrix.data[i][i] = Complex.ONE;
        }
        return matrix;
    }
    
    public static ComplexMatrix zero(int rows, int cols) {
        return new ComplexMatrix(rows, cols);
    }
    
    public static ComplexMatrix fromReal(double[][] realData) {
        return new ComplexMatrix(realData);
    }
    
    public static ComplexMatrix pauliX() {
        ComplexMatrix matrix = new ComplexMatrix(2, 2);
        matrix.set(0, 0, Complex.ZERO);
        matrix.set(0, 1, Complex.ONE);
        matrix.set(1, 0, Complex.ONE);
        matrix.set(1, 1, Complex.ZERO);
        return matrix;
    }
    
    public static ComplexMatrix pauliY() {
        ComplexMatrix matrix = new ComplexMatrix(2, 2);
        matrix.set(0, 0, Complex.ZERO);
        matrix.set(0, 1, new Complex(0, -1));
        matrix.set(1, 0, new Complex(0, 1));
        matrix.set(1, 1, Complex.ZERO);
        return matrix;
    }
    
    public static ComplexMatrix pauliZ() {
        ComplexMatrix matrix = new ComplexMatrix(2, 2);
        matrix.set(0, 0, Complex.ONE);
        matrix.set(0, 1, Complex.ZERO);
        matrix.set(1, 0, Complex.ZERO);
        matrix.set(1, 1, new Complex(-1, 0));
        return matrix;
    }
    
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
    
    @Override
    public int hashCode() {
        int result = rows;
        result = 31 * result + cols;
        result = 31 * result + Arrays.deepHashCode(data);
        return result;
    }
    
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