package com.mechalikh.qforge.math;

import java.util.Random;

/**
 * Static facade over a replaceable {@link Random} instance used for random values
 * in the simulator.
 *
 * <p>The default generator is a {@link Random} created when the class is loaded.
 * {@link #setRandom(Random)} installs a caller supplied generator, which allows a
 * reproducible sequence when the generator is seeded. {@link #resetToDefault()}
 * switches back to the shared default generator without reseeding it.</p>
 */
public class RandomGenerator {
    private static final Random DEFAULT_RANDOM = new Random();
    private static Random currentRandom = DEFAULT_RANDOM;
    
    /**
     * Replaces the generator used by all subsequent calls.
     *
     * @param random the generator to use
     */
    public static void setRandom(Random random) {
        currentRandom = random;
    }
    
    /**
     * Switches back to the default generator. The default generator is not
     * reseeded, so its state continues from previous use.
     */
    public static void resetToDefault() {
        currentRandom = DEFAULT_RANDOM;
    }
    
    /**
     * Returns the current generator.
     *
     * @return the generator used by this class
     */
    public static Random getRandom() {
        return currentRandom;
    }
    
    /**
     * Returns a uniformly distributed value.
     *
     * @return a value in {@code [0, 1)}
     */
    public static double nextDouble() {
        return currentRandom.nextDouble();
    }
    
    /**
     * Returns a value uniformly distributed between two bounds.
     *
     * @param min the lower bound
     * @param max the upper bound
     * @return {@code min + r * (max - min)} where {@code r} is uniform in
     *         {@code [0, 1)}
     */
    public static double nextDouble(double min, double max) {
        return min + currentRandom.nextDouble() * (max - min);
    }
    
    /**
     * Returns a uniformly distributed integer below a bound.
     *
     * @param bound the exclusive upper bound, must be positive
     * @return a value in {@code [0, bound)}
     * @throws IllegalArgumentException if {@code bound} is not positive
     */
    public static int nextInt(int bound) {
        return currentRandom.nextInt(bound);
    }
    
    /**
     * Returns a uniformly distributed integer between two bounds.
     *
     * @param min the inclusive lower bound
     * @param max the exclusive upper bound
     * @return a value in {@code [min, max)}
     * @throws IllegalArgumentException if {@code max} is not greater than
     *         {@code min}
     */
    public static int nextInt(int min, int max) {
        return min + currentRandom.nextInt(max - min);
    }
    
    /**
     * Returns a uniformly distributed boolean.
     *
     * @return a random boolean
     */
    public static boolean nextBoolean() {
        return currentRandom.nextBoolean();
    }
    
    /**
     * Returns {@code true} with the given probability.
     *
     * @param probability the threshold compared with a uniform value in
     *        {@code [0, 1)}
     * @return {@code true} if a uniform value is less than {@code probability}
     */
    public static boolean nextBoolean(double probability) {
        return currentRandom.nextDouble() < probability;
    }
    
    /**
     * Returns a value from the standard normal distribution.
     *
     * @return a standard normal value
     */
    public static double nextGaussian() {
        return currentRandom.nextGaussian();
    }
    
    /**
     * Returns a value from a normal distribution.
     *
     * @param mean the mean
     * @param stdDev the standard deviation
     * @return {@code mean + gaussian * stdDev}
     */
    public static double nextGaussian(double mean, double stdDev) {
        return mean + currentRandom.nextGaussian() * stdDev;
    }
    
    /**
     * Returns a complex number with independent uniform parts.
     *
     * @return a value whose real and imaginary parts are each uniform in
     *         {@code [0, 1)}
     */
    public static Complex nextComplex() {
        return new Complex(currentRandom.nextDouble(), currentRandom.nextDouble());
    }
    
    /**
     * Returns a complex number with a fixed magnitude and uniform phase.
     *
     * @param magnitude the magnitude
     * @return a value with the given magnitude and a phase uniform in
     *         {@code [0, 2 * Math.PI)}
     */
    public static Complex nextComplex(double magnitude) {
        double phase = currentRandom.nextDouble() * 2 * Math.PI;
        return Complex.fromPolar(magnitude, phase);
    }
    
    /**
     * Returns a complex number of unit magnitude with uniform phase.
     *
     * @return a value on the unit circle
     */
    public static Complex nextComplexUnit() {
        return nextComplex(1.0);
    }
    
    /**
     * Returns a random permutation of the integers from zero to {@code n - 1},
     * using a Fisher-Yates shuffle.
     *
     * @param n the number of elements
     * @return an array of length {@code n} containing each index exactly once
     */
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
    
    /**
     * Returns a vector of uniform values.
     *
     * @param size the vector length
     * @return a new array whose entries are uniform in {@code [0, 1)}
     */
    public static double[] randomVector(int size) {
        double[] vector = new double[size];
        for (int i = 0; i < size; i++) {
            vector[i] = currentRandom.nextDouble();
        }
        return vector;
    }
    
    /**
     * Returns a vector of standard normal values.
     *
     * @param size the vector length
     * @return a new array of Gaussian entries
     */
    public static double[] randomNormalVector(int size) {
        double[] vector = new double[size];
        for (int i = 0; i < size; i++) {
            vector[i] = currentRandom.nextGaussian();
        }
        return vector;
    }
    
    /**
     * Returns a vector of complex values with uniform parts.
     *
     * @param size the vector length
     * @return a new array whose entries come from {@link #nextComplex()}
     */
    public static Complex[] randomComplexVector(int size) {
        Complex[] vector = new Complex[size];
        for (int i = 0; i < size; i++) {
            vector[i] = nextComplex();
        }
        return vector;
    }
    
    /**
     * Returns a random complex vector scaled to unit Euclidean norm.
     *
     * @param size the vector length
     * @return a new array normalized to unit norm, or an empty array when
     *         {@code size} is zero
     */
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
    
    /**
     * Creates a matrix filled with uniformly random complex entries, with real and
     * imaginary parts in {@code [0, 1)}.
     *
     * <p>Despite the method name, the current implementation returns the generated
     * matrix directly and does not perform a QR decomposition, so the result is not
     * guaranteed to be unitary.</p>
     *
     * @param size the number of rows and columns
     * @return a matrix of random complex entries
     */
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
    
    /**
     * Creates a random Hermitian matrix. Diagonal entries are real Gaussian values;
     * each entry above the diagonal is a random complex value and the mirror entry
     * is its conjugate.
     *
     * @param size the number of rows and columns
     * @return a new Hermitian matrix
     */
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
    
    /**
     * Creates a random density matrix by forming {@code H * H.adjoint()} from a
     * random Hermitian matrix {@code H} and dividing by the real part of the trace.
     *
     * @param size the number of rows and columns
     * @return a new Hermitian matrix with trace 1
     */
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
    
    /**
     * Selects an index from a cumulative distribution.
     *
     * <p>A uniform value in {@code [0, 1)} is drawn and the first index whose
     * cumulative sum is greater than or equal to that value is returned. If the sum
     * never reaches the value, the last index is returned. The probabilities are
     * not normalized or validated; an empty array returns {@code -1}.</p>
     *
     * @param probabilities the weights, interpreted as a cumulative distribution
     * @return the selected index, or {@code -1} for an empty array
     */
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
    
    /**
     * Selects an index from a cumulative distribution using a supplied value.
     *
     * <p>The first index whose cumulative sum is greater than or equal to
     * {@code randomValue} is returned. If the sum never reaches the value, the last
     * index is returned. The probabilities are not normalized or validated; an
     * empty array returns {@code -1}.</p>
     *
     * @param probabilities the weights, interpreted as a cumulative distribution
     * @param randomValue the value compared with the cumulative sum, typically in
     *        {@code [0, 1)}
     * @return the selected index, or {@code -1} for an empty array
     */
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