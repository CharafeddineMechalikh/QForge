package com.quantum.simulator.math;

import java.util.Arrays;

public class MatrixOperations {
    
    public static ComplexMatrix multiply(ComplexMatrix a, ComplexMatrix b) {
        return a.multiply(b);
    }
    
    public static ComplexMatrix add(ComplexMatrix a, ComplexMatrix b) {
        return a.add(b);
    }
    
    public static ComplexMatrix subtract(ComplexMatrix a, ComplexMatrix b) {
        return a.subtract(b);
    }
    
    public static ComplexMatrix tensorProduct(ComplexMatrix a, ComplexMatrix b) {
        return a.tensorProduct(b);
    }
    
    public static Complex trace(ComplexMatrix matrix) {
        return matrix.trace();
    }
    
    public static ComplexMatrix identity(int size) {
        return ComplexMatrix.identity(size);
    }
    
    public static ComplexMatrix zero(int rows, int cols) {
        return ComplexMatrix.zero(rows, cols);
    }
    
   
    public static ComplexMatrix[] eigenDecomposition(ComplexMatrix matrix) {
        if (!matrix.isSquare()) {
            throw new IllegalArgumentException("Matrix must be square for eigen decomposition");
        }
        return matrix.eigenDecomposition();
    }
    
    public static ComplexMatrix kroneckerProduct(ComplexMatrix a, ComplexMatrix b) {
        return tensorProduct(a, b);
    }
    
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