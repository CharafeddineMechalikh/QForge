package com.quantum.simulator.math;

import java.util.Arrays;

/**
 * Static convenience operations on {@link ComplexMatrix} instances.
 *
 * <p>Most methods delegate to the corresponding instance methods. Methods that
 * compute a new matrix return new instances and do not modify their arguments,
 * except that {@link #partialTrace(ComplexMatrix, int, int[])} sorts the supplied
 * index array.</p>
 */
public class MatrixOperations {
    
    /**
     * Multiplies two matrices.
     *
     * @param a the left factor
     * @param b the right factor
     * @return the product {@code a * b}
     * @throws IllegalArgumentException if the dimensions are incompatible
     */
    public static ComplexMatrix multiply(ComplexMatrix a, ComplexMatrix b) {
        return a.multiply(b);
    }
    
    /**
     * Adds two matrices element by element.
     *
     * @param a the first matrix
     * @param b the second matrix
     * @return the element-wise sum
     * @throws IllegalArgumentException if the dimensions do not match
     */
    public static ComplexMatrix add(ComplexMatrix a, ComplexMatrix b) {
        return a.add(b);
    }
    
    /**
     * Subtracts one matrix from another element by element.
     *
     * @param a the matrix to subtract from
     * @param b the matrix to subtract
     * @return the element-wise difference {@code a - b}
     * @throws IllegalArgumentException if the dimensions do not match
     */
    public static ComplexMatrix subtract(ComplexMatrix a, ComplexMatrix b) {
        return a.subtract(b);
    }
    
    /**
     * Computes the Kronecker product of two matrices.
     *
     * @param a the left factor
     * @param b the right factor
     * @return the tensor product {@code a} with {@code b}
     */
    public static ComplexMatrix tensorProduct(ComplexMatrix a, ComplexMatrix b) {
        return a.tensorProduct(b);
    }
    
    /**
     * Returns the trace of a square matrix.
     *
     * @param matrix the square matrix
     * @return the sum of the diagonal entries
     * @throws IllegalArgumentException if the matrix is not square
     */
    public static Complex trace(ComplexMatrix matrix) {
        return matrix.trace();
    }
    
    /**
     * Creates an identity matrix.
     *
     * @param size the number of rows and columns
     * @return a new identity matrix of the given size
     * @throws IllegalArgumentException if {@code size} is not positive
     */
    public static ComplexMatrix identity(int size) {
        return ComplexMatrix.identity(size);
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
        return ComplexMatrix.zero(rows, cols);
    }
    
   
    /**
     * Computes an eigendecomposition of a square matrix.
     *
     * @param matrix the square matrix
     * @return a two element array with the diagonal eigenvalue matrix at index 0
     *         and the eigenvector matrix at index 1
     * @throws IllegalArgumentException if the matrix is not square
     */
    public static ComplexMatrix[] eigenDecomposition(ComplexMatrix matrix) {
        if (!matrix.isSquare()) {
            throw new IllegalArgumentException("Matrix must be square for eigen decomposition");
        }
        return matrix.eigenDecomposition();
    }
    
    /**
     * Computes the Kronecker product, an alias for
     * {@link #tensorProduct(ComplexMatrix, ComplexMatrix)}.
     *
     * @param a the left factor
     * @param b the right factor
     * @return the tensor product {@code a} with {@code b}
     */
    public static ComplexMatrix kroneckerProduct(ComplexMatrix a, ComplexMatrix b) {
        return tensorProduct(a, b);
    }
    
    /**
     * Traces out the subsystems that are not listed in {@code keepIndices}.
     *
     * <p>{@code systemSize} is the number of subsystems, so the input matrix is
     * expected to be square with {@code 2^systemSize} rows and columns. Subsystem
     * position {@code 0} corresponds to the most significant bit of the basis
     * index. The result has dimension {@code 2^keepIndices.length}. The supplied
     * array is sorted in place, and the sorted positions determine the bit order of
     * the reduced matrix.</p>
     *
     * @param matrix the square matrix of dimension {@code 2^systemSize}
     * @param systemSize the number of subsystems
     * @param keepIndices the subsystem positions to keep, sorted in place
     * @return the reduced matrix of dimension {@code 2^keepIndices.length}
     * @throws IllegalArgumentException if the matrix dimension is not
     *         {@code 2^systemSize}
     */
    public static ComplexMatrix partialTrace(ComplexMatrix matrix, int systemSize, int[] keepIndices) {
        // Perform partial trace on a composite system
        Arrays.sort(keepIndices);
        int keepSize = keepIndices.length;
        int traceSize = systemSize - keepSize;
        
        int totalDim = matrix.rows();
        int keptDim = 1 << keepSize;
        int tracedDim = 1 << traceSize;
        
        if (totalDim != (1 << systemSize)) {
            throw new IllegalArgumentException("Matrix dimension doesn't match system size");
        }
        
        ComplexMatrix result = new ComplexMatrix(keptDim, keptDim);
        
        // Map from composite indices to subsystem indices
        for (int i = 0; i < totalDim; i++) {
            for (int j = 0; j < totalDim; j++) {
                // Extract bits for kept subsystems
                int iKept = extractBits(i, keepIndices, systemSize);
                int jKept = extractBits(j, keepIndices, systemSize);
                
                // Check if traced subsystems match
                int iTraced = extractComplementBits(i, keepIndices, systemSize);
                int jTraced = extractComplementBits(j, keepIndices, systemSize);
                
                if (iTraced == jTraced) {
                    Complex current = result.get(iKept, jKept);
                    Complex addition = matrix.get(i, j);
                    result.set(iKept, jKept, current.add(addition));
                }
            }
        }
        
        return result;
    }
    
    private static int extractBits(int index, int[] bitPositions, int totalBits) {
        int result = 0;
        for (int i = 0; i < bitPositions.length; i++) {
            int bitPos = totalBits - 1 - bitPositions[i];
            int bit = (index >> bitPos) & 1;
            result |= (bit << (bitPositions.length - 1 - i));
        }
        return result;
    }
    
    private static int extractComplementBits(int index, int[] keepPositions, int totalBits) {
        int result = 0;
        int resultPos = 0;
        
        for (int pos = 0; pos < totalBits; pos++) {
            final int currentPos = pos;
            if (Arrays.stream(keepPositions).noneMatch(k -> k == currentPos)) {
                int bitPos = totalBits - 1 - pos;
                int bit = (index >> bitPos) & 1;
                result |= (bit << ((totalBits - keepPositions.length) - 1 - resultPos));
                resultPos++;
            }
        }
        return result;
    }
    
    /**
     * Approximates the matrix exponential with a truncated Taylor series.
     *
     * <p>The returned value is the sum of {@code A^k / k!} for {@code k} from 0 to
     * 10. No scaling and squaring is applied.</p>
     *
     * @param matrix the square matrix {@code A}
     * @return the truncated series approximation of {@code exp(A)}
     * @throws IllegalArgumentException if the matrix is not square
     */
    public static ComplexMatrix exp(ComplexMatrix matrix) {
        // Matrix exponential using Taylor series (for small matrices)
        if (!matrix.isSquare()) {
            throw new IllegalArgumentException("Matrix must be square for exponential");
        }
        
        int n = matrix.rows();
        ComplexMatrix result = ComplexMatrix.identity(n);
        ComplexMatrix term = ComplexMatrix.identity(n);
        
        int maxTerms = 10;
        for (int k = 1; k <= maxTerms; k++) {
            term = term.multiply(matrix).multiply(new Complex(1.0 / k, 0));
            result = result.add(term);
        }
        
        return result;
    }
    
    /**
     * Computes a matrix norm.
     *
     * <p>The trace norm is computed as the sum of the square roots of the
     * eigenvalues of {@code matrix * matrix.adjoint()}.</p>
     *
     * @param matrix the matrix
     * @param normType one of {@code "frobenius"}, {@code "max"}, or {@code "trace"},
     *        compared case-insensitively
     * @return the selected norm
     * @throws IllegalArgumentException if {@code normType} is not recognized, or if
     *         the trace norm is requested for a non-square matrix
     */
    public static double norm(ComplexMatrix matrix, String normType) {
        switch (normType.toLowerCase()) {
            case "frobenius":
                return frobeniusNorm(matrix);
            case "max":
                return maxNorm(matrix);
            case "trace":
                return traceNorm(matrix);
            default:
                throw new IllegalArgumentException("Unknown norm type: " + normType);
        }
    }
    
    private static double frobeniusNorm(ComplexMatrix matrix) {
        double sum = 0.0;
        for (int i = 0; i < matrix.rows(); i++) {
            for (int j = 0; j < matrix.cols(); j++) {
                double abs = matrix.get(i, j).abs();
                sum += abs * abs;
            }
        }
        return Math.sqrt(sum);
    }
    
    private static double maxNorm(ComplexMatrix matrix) {
        double max = 0.0;
        for (int i = 0; i < matrix.rows(); i++) {
            for (int j = 0; j < matrix.cols(); j++) {
                max = Math.max(max, matrix.get(i, j).abs());
            }
        }
        return max;
    }
    
    private static double traceNorm(ComplexMatrix matrix) {
        if (!matrix.isSquare()) {
            throw new IllegalArgumentException("Trace norm requires square matrix");
        }
        
        ComplexMatrix product = matrix.multiply(matrix.adjoint());
        double[] eigenvalues = eigenvalues(product);
        
        double sum = 0.0;
        for (double eval : eigenvalues) {
            sum += Math.sqrt(eval);
        }
        return sum;
    }
    
    /**
     * Computes the eigenvalues of a square matrix, delegating to
     * {@link ComplexMatrix#eigenvalues()}.
     *
     * @param matrix the square matrix
     * @return the eigenvalues as real numbers, sorted in ascending order
     * @throws IllegalArgumentException if the matrix is not square
     */
    public static double[] eigenvalues(ComplexMatrix matrix) {
        if (!matrix.isSquare()) {
            throw new IllegalArgumentException("Matrix must be square to compute eigenvalues");
        }
        return matrix.eigenvalues();
    }
    
    private static double[] computeEigenvaluesIterative(ComplexMatrix matrix) {
        int n = matrix.rows();
        double[] eigenvalues = new double[n];
        
        // Simple power iteration for dominant eigenvalue
        ComplexMatrix currentMatrix = matrix.copy();
        
        for (int i = 0; i < n; i++) {
            // Use power iteration to find the next eigenvalue
            double eigenvalue = powerIteration(currentMatrix);
            eigenvalues[i] = eigenvalue;
            
            // Deflate the matrix (remove the found eigenvalue)
            if (i < n - 1) {
                currentMatrix = deflateMatrix(currentMatrix, eigenvalue);
            }
        }
        
        // Sort eigenvalues in descending order
        Arrays.sort(eigenvalues);
        return eigenvalues;
    }
    
    private static double powerIteration(ComplexMatrix matrix) {
        int n = matrix.rows();
        Complex[] vector = new Complex[n];
        
        // Initialize random vector
        for (int i = 0; i < n; i++) {
            vector[i] = new Complex(Math.random(), Math.random());
        }
        
        // Normalize
        double norm = 0.0;
        for (Complex c : vector) {
            norm += c.absSquare();
        }
        norm = Math.sqrt(norm);
        for (int i = 0; i < n; i++) {
            vector[i] = vector[i].divide(norm);
        }
        
        // Power iteration
        for (int iter = 0; iter < 100; iter++) {
            // Multiply matrix by vector
            Complex[] newVector = new Complex[n];
            for (int i = 0; i < n; i++) {
                Complex sum = Complex.ZERO;
                for (int j = 0; j < n; j++) {
                    sum = sum.add(matrix.get(i, j).multiply(vector[j]));
                }
                newVector[i] = sum;
            }
            
            // Estimate eigenvalue (Rayleigh quotient)
            Complex numerator = Complex.ZERO;
            Complex denominator = Complex.ZERO;
            for (int i = 0; i < n; i++) {
                numerator = numerator.add(newVector[i].conjugate().multiply(newVector[i]));
                denominator = denominator.add(vector[i].conjugate().multiply(vector[i]));
            }
            double eigenvalue = numerator.abs() / denominator.abs();
            
            // Normalize for next iteration
            norm = 0.0;
            for (Complex c : newVector) {
                norm += c.absSquare();
            }
            norm = Math.sqrt(norm);
            for (int i = 0; i < n; i++) {
                vector[i] = newVector[i].divide(norm);
            }
            
            // Check convergence (simplified)
            if (iter > 10 && Math.abs(eigenvalue - eigenvalue) < 1e-10) {
                return eigenvalue;
            }
        }
        
        // Return final estimate
        Complex[] finalVector = new Complex[n];
        for (int i = 0; i < n; i++) {
            Complex sum = Complex.ZERO;
            for (int j = 0; j < n; j++) {
                sum = sum.add(matrix.get(i, j).multiply(vector[j]));
            }
            finalVector[i] = sum;
        }
        
        double finalNorm = 0.0;
        for (Complex c : finalVector) {
            finalNorm += c.absSquare();
        }
        return Math.sqrt(finalNorm);
    }
    
    private static ComplexMatrix deflateMatrix(ComplexMatrix matrix, double eigenvalue) {
        // Simplified deflation - subtract eigenvalue from diagonal
        // In production, use proper deflation techniques
        int n = matrix.rows();
        ComplexMatrix deflated = matrix.copy();
        
        for (int i = 0; i < n; i++) {
            Complex current = deflated.get(i, i);
            deflated.set(i, i, current.subtract(new Complex(eigenvalue, 0)));
        }
        
        return deflated;
    }
    
}