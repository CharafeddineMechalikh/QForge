package com.quantum.simulator.state;

import java.util.Locale;
import com.quantum.simulator.math.Complex;
import java.util.Arrays;

/**
 * Fixed-length dense vector of complex numbers.
 *
 * <p>Accessors return defensive copies: {@link #get(int)} and the array-returning methods
 * never expose internal storage, and {@link #set(int, Complex)} copies the supplied value.
 * The constructors that take an array copy its contents.</p>
 *
 * <p>The vector is mutable through its {@code set} methods. Arithmetic methods return new
 * vectors and leave both operands unchanged. Element equality in {@link #equals(Object)}
 * delegates to {@link Complex#equals(Object)}, which compares components with a tolerance
 * of {@code 1e-10}.</p>
 */
public class ComplexVector {
    private final Complex[] elements;
    private final int length;
    
    /**
     * Creates a zero vector of the given length.
     *
     * @param length number of elements, must be positive
     * @throws IllegalArgumentException if {@code length} is not positive
     */
    public ComplexVector(int length) {
        if (length <= 0) {
            throw new IllegalArgumentException("Vector length must be positive");
        }
        this.length = length;
        this.elements = new Complex[length];
        Arrays.fill(this.elements, Complex.ZERO);
    }
    
    /**
     * Creates a vector from the given elements, copying each element.
     *
     * @param elements element values, must not be null or empty
     * @throws IllegalArgumentException if {@code elements} is null or empty
     */
    public ComplexVector(Complex[] elements) {
        if (elements == null || elements.length == 0) {
            throw new IllegalArgumentException("Elements array cannot be null or empty");
        }
        this.length = elements.length;
        this.elements = new Complex[length];
        for (int i = 0; i < length; i++) {
            this.elements[i] = new Complex(elements[i]);
        }
    }
    
    /**
     * Creates a vector whose elements have the given real parts and zero imaginary parts.
     *
     * <p>The array length becomes the vector length. Unlike the other constructors, this
     * one does not reject an empty array.</p>
     *
     * @param realParts real part of each element
     */
    public ComplexVector(double[] realParts) {
        this.length = realParts.length;
        this.elements = new Complex[length];
        for (int i = 0; i < length; i++) {
            this.elements[i] = new Complex(realParts[i], 0.0);
        }
    }
    
    /**
     * Returns the number of elements.
     *
     * @return the vector length
     */
    public int getLength() {
        return length;
    }
    
    /**
     * Returns a copy of the element at the given position.
     *
     * @param index zero-based element position
     * @return a copy of the element
     * @throws IndexOutOfBoundsException if {@code index} is outside {@code [0, length)}
     */
    public Complex get(int index) {
        checkIndex(index);
        return new Complex(elements[index]);
    }
    
    /**
     * Replaces the element at the given position with a copy of the supplied value.
     *
     * @param index zero-based element position
     * @param value new element value, copied on entry
     * @throws IndexOutOfBoundsException if {@code index} is outside {@code [0, length)}
     */
    public void set(int index, Complex value) {
        checkIndex(index);
        elements[index] = new Complex(value);
    }
    
    /**
     * Replaces the element at the given position with {@code real + i * imag}.
     *
     * @param index zero-based element position
     * @param real real part of the new value
     * @param imag imaginary part of the new value
     * @throws IndexOutOfBoundsException if {@code index} is outside {@code [0, length)}
     */
    public void set(int index, double real, double imag) {
        checkIndex(index);
        elements[index] = new Complex(real, imag);
    }
    
    /**
     * Returns the element-wise sum of this vector and another vector.
     *
     * @param other vector with the same length
     * @return a new vector holding {@code this[i] + other[i]} at each index
     * @throws IllegalArgumentException if the lengths differ
     */
    public ComplexVector add(ComplexVector other) {
        checkSameLength(other);
        
        Complex[] result = new Complex[length];
        for (int i = 0; i < length; i++) {
            result[i] = this.elements[i].add(other.elements[i]);
        }
        
        return new ComplexVector(result);
    }
    
    /**
     * Returns the element-wise difference of this vector and another vector.
     *
     * @param other vector with the same length
     * @return a new vector holding {@code this[i] - other[i]} at each index
     * @throws IllegalArgumentException if the lengths differ
     */
    public ComplexVector subtract(ComplexVector other) {
        checkSameLength(other);
        
        Complex[] result = new Complex[length];
        for (int i = 0; i < length; i++) {
            result[i] = this.elements[i].subtract(other.elements[i]);
        }
        
        return new ComplexVector(result);
    }
    
    /**
     * Returns this vector multiplied element-wise by a complex scalar.
     *
     * @param scalar factor applied to every element
     * @return a new scaled vector
     */
    public ComplexVector scale(Complex scalar) {
        Complex[] result = new Complex[length];
        for (int i = 0; i < length; i++) {
            result[i] = this.elements[i].multiply(scalar);
        }
        
        return new ComplexVector(result);
    }
    
    /**
     * Returns this vector multiplied element-wise by a real scalar.
     *
     * @param scalar factor applied to every element
     * @return a new scaled vector
     */
    public ComplexVector scale(double scalar) {
        return scale(new Complex(scalar, 0.0));
    }
    
    /**
     * Returns the Hermitian inner product with another vector,
     * {@code sum(conj(this[i]) * other[i])}.
     *
     * <p>The product is conjugate-linear in this vector and linear in {@code other}.</p>
     *
     * @param other vector with the same length
     * @return the inner product
     * @throws IllegalArgumentException if the lengths differ
     */
    public Complex dotProduct(ComplexVector other) {
        checkSameLength(other);
        
        Complex result = Complex.ZERO;
        for (int i = 0; i < length; i++) {
            result = result.add(this.elements[i].conjugate().multiply(other.elements[i]));
        }
        
        return result;
    }
    
    /**
     * Returns the Euclidean norm, {@code sqrt(sum(|element|^2))}.
     *
     * @return the non-negative norm
     */
    public double norm() {
        double sum = 0.0;
        for (Complex element : elements) {
            sum += element.absSquare();
        }
        return Math.sqrt(sum);
    }
    
    /**
     * Returns a unit-norm copy of this vector, obtained by dividing every element by
     * {@link #norm()}.
     *
     * @return a new vector with norm {@code 1}
     * @throws ArithmeticException if the norm is exactly zero
     */
    public ComplexVector normalize() {
        double norm = norm();
        if (norm == 0.0) {
            throw new ArithmeticException("Cannot normalize zero vector");
        }
        return scale(1.0 / norm);
    }
    
    /**
     * Returns the Kronecker product of this vector and another vector.
     *
     * <p>The result has length {@code this.length * other.length}. The pair
     * {@code (i, j)} is stored at index {@code i * other.length + j}, so the index of this
     * vector forms the high-order part of the result index.</p>
     *
     * @param other right-hand factor
     * @return a new vector of length {@code this.length * other.length}
     */
    public ComplexVector tensorProduct(ComplexVector other) {
        int newLength = this.length * other.length;
        Complex[] result = new Complex[newLength];
        
        for (int i = 0; i < this.length; i++) {
            for (int j = 0; j < other.length; j++) {
                result[i * other.length + j] = this.elements[i].multiply(other.elements[j]);
            }
        }
        
        return new ComplexVector(result);
    }
    
    /**
     * Returns a new vector holding the complex conjugate of every element.
     *
     * @return a new conjugated vector
     */
    public ComplexVector conjugate() {
        Complex[] result = new Complex[length];
        for (int i = 0; i < length; i++) {
            result[i] = this.elements[i].conjugate();
        }
        return new ComplexVector(result);
    }
    
    /**
     * Returns an independent copy of this vector.
     *
     * @return a new vector with copied elements
     */
    public ComplexVector copy() {
        return new ComplexVector(elements);
    }
    
    /**
     * Returns a new array holding the real part of every element, in index order.
     *
     * @return an array of length {@link #getLength()}
     */
    public double[] getRealParts() {
        double[] realParts = new double[length];
        for (int i = 0; i < length; i++) {
            realParts[i] = elements[i].real();
        }
        return realParts;
    }
    
    /**
     * Returns a new array holding the imaginary part of every element, in index order.
     *
     * @return an array of length {@link #getLength()}
     */
    public double[] getImaginaryParts() {
        double[] imagParts = new double[length];
        for (int i = 0; i < length; i++) {
            imagParts[i] = elements[i].imag();
        }
        return imagParts;
    }
    
    /**
     * Tests whether every element equals {@link Complex#ZERO} according to
     * {@link Complex#equals(Object)}.
     *
     * @return {@code true} if all elements are zero within the comparison tolerance
     */
    public boolean isZero() {
        for (Complex element : elements) {
            if (!element.equals(Complex.ZERO)) {
                return false;
            }
        }
        return true;
    }
    
    private void checkIndex(int index) {
        if (index < 0 || index >= length) {
            throw new IndexOutOfBoundsException(
                String.format(Locale.ROOT, "Index %d out of bounds for vector of length %d", index, length)
            );
        }
    }
    
    private void checkSameLength(ComplexVector other) {
        if (this.length != other.length) {
            throw new IllegalArgumentException(
                String.format(Locale.ROOT, "Vector lengths don't match: %d != %d", this.length, other.length)
            );
        }
    }
    
    /**
     * Compares this vector with another object.
     *
     * <p>Two vectors are equal when they have the same length and each pair of elements is
     * equal according to {@link Complex#equals(Object)}.</p>
     *
     * @param obj object to compare with
     * @return {@code true} if {@code obj} is a {@code ComplexVector} with equal elements
     */
    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        
        ComplexVector other = (ComplexVector) obj;
        if (length != other.length) return false;
        
        for (int i = 0; i < length; i++) {
            if (!elements[i].equals(other.elements[i])) {
                return false;
            }
        }
        
        return true;
    }
    
    /**
     * Returns a hash code derived from the length and the exact element values.
     *
     * <p>Element equality uses a tolerance, while hashing uses the raw component values,
     * so vectors that compare equal are not guaranteed to share a hash code.</p>
     *
     * @return the hash code
     */
    @Override
    public int hashCode() {
        int result = length;
        result = 31 * result + Arrays.hashCode(elements);
        return result;
    }
    
    /**
     * Returns a diagnostic listing of the first ten elements.
     *
     * @return a multi-line description of this vector
     */
    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("ComplexVector[length=").append(length).append("]:\n");
        
        for (int i = 0; i < Math.min(length, 10); i++) {
            sb.append(String.format(Locale.ROOT, "  [%3d] %s\n", i, elements[i]));
        }
        
        if (length > 10) {
            sb.append("  ...\n");
        }
        
        return sb.toString();
    }
}