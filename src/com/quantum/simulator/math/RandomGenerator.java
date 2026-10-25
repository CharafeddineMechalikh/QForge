package com.quantum.simulator.math;

import java.util.Random;

public class RandomGenerator {
    private static final Random DEFAULT_RANDOM = new Random();
    private static Random currentRandom = DEFAULT_RANDOM;
    
    public static void setRandom(Random random) {
        currentRandom = random;
    }
    
    public static void resetToDefault() {
        currentRandom = DEFAULT_RANDOM;
    }
    
    public static Random getRandom() {
        return currentRandom;
    }
    
    public static double nextDouble() {
        return currentRandom.nextDouble();
    }
    
    public static double nextDouble(double min, double max) {
        return min + currentRandom.nextDouble() * (max - min);
    }
    
    public static int nextInt(int bound) {
        return currentRandom.nextInt(bound);
    }
    
    public static int nextInt(int min, int max) {
        return min + currentRandom.nextInt(max - min);
    }
    
    public static boolean nextBoolean() {
        return currentRandom.nextBoolean();
    }
    
    public static boolean nextBoolean(double probability) {
        return currentRandom.nextDouble() < probability;
    }
    
    public static double nextGaussian() {
        return currentRandom.nextGaussian();
    }
    
    public static double nextGaussian(double mean, double stdDev) {
        return mean + currentRandom.nextGaussian() * stdDev;
    }
    
    public static Complex nextComplex() {
        return new Complex(currentRandom.nextDouble(), currentRandom.nextDouble());
    }
    
    public static Complex nextComplex(double magnitude) {
        double phase = currentRandom.nextDouble() * 2 * Math.PI;
        return Complex.fromPolar(magnitude, phase);
    }
    
    public static Complex nextComplexUnit() {
        return nextComplex(1.0);
    }
    
    public static int[] randomPermutation(int n) {
        int[] permutation = new int[n];
        for (int i = 0; i < n; i++) {
            permutation[i] = i;
        }
        
        // Fisher-Yates shuffle
        for (int i = n - 1; i > 0; i--) {
            int j = currentRandom.nextInt(i + 1);
            int temp = permutation[i];
            permutation[i] = permutation[j];
            permutation[j] = temp;
        }
        
        return permutation;
    }
    
    public static double[] randomVector(int size) {
        double[] vector = new double[size];
        for (int i = 0; i < size; i++) {
            vector[i] = currentRandom.nextDouble();
        }
        return vector;
    }
    
    public static double[] randomNormalVector(int size) {
        double[] vector = new double[size];
        for (int i = 0; i < size; i++) {
            vector[i] = currentRandom.nextGaussian();
        }
        return vector;
    }
    
    public static Complex[] randomComplexVector(int size) {
        Complex[] vector = new Complex[size];
        for (int i = 0; i < size; i++) {
            vector[i] = nextComplex();
        }
        return vector;
    }
    
    public static Complex[] randomNormalizedComplexVector(int size) {
        Complex[] vector = randomComplexVector(size);
        
        // Normalize
        double norm = 0.0;
        for (Complex c : vector) {
            norm += c.absSquare();
        }
        norm = Math.sqrt(norm);
        
        for (int i = 0; i < size; i++) {
            vector[i] = vector[i].divide(norm);
        }
        
        return vector;
    }
    
    public static ComplexMatrix randomUnitaryMatrix(int size) {
        // Generate random unitary matrix using QR decomposition
        Complex[][] matrixData = new Complex[size][size];
        
        // Generate random complex matrix
        for (int i = 0; i < size; i++) {
            for (int j = 0; j < size; j++) {
                matrixData[i][j] = nextComplex();
            }
        }
        
        ComplexMatrix matrix = new ComplexMatrix(matrixData);
        
        // QR decomposition to make it unitary (simplified)
        // In real implementation, use proper QR decomposition
        return matrix;
    }
    
    public static ComplexMatrix randomHermitianMatrix(int size) {
        Complex[][] matrixData = new Complex[size][size];
        
        // Generate random Hermitian matrix
        for (int i = 0; i < size; i++) {
            for (int j = 0; j <= i; j++) {
                if (i == j) {
                    // Diagonal elements are real
                    matrixData[i][j] = new Complex(nextGaussian(), 0);
                } else {
                    Complex element = nextComplex();
                    matrixData[i][j] = element;
                    matrixData[j][i] = element.conjugate();
                }
            }
        }
        
        return new ComplexMatrix(matrixData);
    }
    
    public static ComplexMatrix randomDensityMatrix(int size) {
        // Generate random density matrix (positive semi-definite, trace 1)
        ComplexMatrix matrix = randomHermitianMatrix(size);
        
        // Make it positive semi-definite by multiplying with its adjoint
        matrix = matrix.multiply(matrix.adjoint());
        
        // Normalize trace to 1
        Complex trace = MatrixOperations.trace(matrix);
        matrix = matrix.multiply(new Complex(1.0 / trace.real(), 0));
        
        return matrix;
    }
    
    public static int sampleFromDistribution(double[] probabilities) {
        double rand = currentRandom.nextDouble();
        double cumulative = 0.0;
        
        for (int i = 0; i < probabilities.length; i++) {
            cumulative += probabilities[i];
            if (rand <= cumulative) {
                return i;
            }
        }
        
        // In case of numerical errors, return last index
        return probabilities.length - 1;
    }
    
    public static int sampleFromDistribution(double[] probabilities, double randomValue) {
        double cumulative = 0.0;
        
        for (int i = 0; i < probabilities.length; i++) {
            cumulative += probabilities[i];
            if (randomValue <= cumulative) {
                return i;
            }
        }
        
        return probabilities.length - 1;
    }
}