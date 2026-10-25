package com.quantum.simulator.state;

import java.util.Locale;
import com.quantum.simulator.math.Complex;
import java.util.Arrays;

public class ComplexVector {
    private final Complex[] elements;
    private final int length;
    
    public ComplexVector(int length) {
        if (length <= 0) {
            throw new IllegalArgumentException("Vector length must be positive");
        }
        this.length = length;
        this.elements = new Complex[length];
        Arrays.fill(this.elements, Complex.ZERO);
    }
    
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
    
    public ComplexVector(double[] realParts) {
        this.length = realParts.length;
        this.elements = new Complex[length];
        for (int i = 0; i < length; i++) {
            this.elements[i] = new Complex(realParts[i], 0.0);
        }
    }
    
    public int getLength() {
        return length;
    }
    
    public Complex get(int index) {
        checkIndex(index);
        return new Complex(elements[index]);
    }
    
    public void set(int index, Complex value) {
        checkIndex(index);
        elements[index] = new Complex(value);
    }
    
    public void set(int index, double real, double imag) {
        checkIndex(index);
        elements[index] = new Complex(real, imag);
    }
    
    public ComplexVector add(ComplexVector other) {
        checkSameLength(other);
        
        Complex[] result = new Complex[length];
        for (int i = 0; i < length; i++) {
            result[i] = this.elements[i].add(other.elements[i]);
        }
        
        return new ComplexVector(result);
    }
    
    public ComplexVector subtract(ComplexVector other) {
        checkSameLength(other);
        
        Complex[] result = new Complex[length];
        for (int i = 0; i < length; i++) {
            result[i] = this.elements[i].subtract(other.elements[i]);
        }
        
        return new ComplexVector(result);
    }
    
    public ComplexVector scale(Complex scalar) {
        Complex[] result = new Complex[length];
        for (int i = 0; i < length; i++) {
            result[i] = this.elements[i].multiply(scalar);
        }
        
        return new ComplexVector(result);
    }
    
    public ComplexVector scale(double scalar) {
        return scale(new Complex(scalar, 0.0));
    }
    
    public Complex dotProduct(ComplexVector other) {
        checkSameLength(other);
        
        Complex result = Complex.ZERO;
        for (int i = 0; i < length; i++) {
            result = result.add(this.elements[i].conjugate().multiply(other.elements[i]));
        }
        
        return result;
    }
    
    public double norm() {
        double sum = 0.0;
        for (Complex element : elements) {
            sum += element.absSquare();
        }
        return Math.sqrt(sum);
    }
    
    public ComplexVector normalize() {
        double norm = norm();
        if (norm == 0.0) {
            throw new ArithmeticException("Cannot normalize zero vector");
        }
        return scale(1.0 / norm);
    }
    
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
    
    public ComplexVector conjugate() {
        Complex[] result = new Complex[length];
        for (int i = 0; i < length; i++) {
            result[i] = this.elements[i].conjugate();
        }
        return new ComplexVector(result);
    }
    
    public ComplexVector copy() {
        return new ComplexVector(elements);
    }
    
    public double[] getRealParts() {
        double[] realParts = new double[length];
        for (int i = 0; i < length; i++) {
            realParts[i] = elements[i].real();
        }
        return realParts;
    }
    
    public double[] getImaginaryParts() {
        double[] imagParts = new double[length];
        for (int i = 0; i < length; i++) {
            imagParts[i] = elements[i].imag();
        }
        return imagParts;
    }
    
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
    
    @Override
    public int hashCode() {
        int result = length;
        result = 31 * result + Arrays.hashCode(elements);
        return result;
    }
    
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