package com.mechalikh.qforge.math;

import java.util.Locale;
import java.util.Objects;

/**
 * Immutable complex number with double precision real and imaginary parts.
 *
 * <p>Both fields are {@code final}, and every arithmetic method returns a new
 * instance, so the shared constants {@link #ZERO}, {@link #ONE}, and {@link #I}
 * are also immutable.</p>
 *
 * <p>{@link #equals(Object)} compares both parts with an absolute tolerance of
 * {@code 1e-10}, while {@link #hashCode()} is derived from the exact stored
 * values.</p>
 */
public class Complex {
    /** The complex number {@code 0 + 0i}. */
    public static final Complex ZERO = new Complex(0, 0);
    /** The complex number {@code 1 + 0i}. */
    public static final Complex ONE = new Complex(1, 0);
    /** The imaginary unit {@code 0 + 1i}. */
    public static final Complex I = new Complex(0, 1);
    
    private final double real;
    private final double imag;
    
    /**
     * Creates a complex number from its real and imaginary parts.
     *
     * @param real the real part
     * @param imag the imaginary part
     */
    public Complex(double real, double imag) {
        this.real = real;
        this.imag = imag;
    }
    
    /**
     * Creates a copy of another complex number.
     *
     * @param other the complex number to copy
     */
    public Complex(Complex other) {
        this.real = other.real;
        this.imag = other.imag;
    }
    
    /**
     * Returns the real part.
     *
     * @return the real part
     */
    public double real() {
        return real;
    }
    
    /**
     * Returns the imaginary part.
     *
     * @return the imaginary part
     */
    public double imag() {
        return imag;
    }
    
    /**
     * Adds another complex number to this one.
     *
     * @param other the addend
     * @return the sum {@code this + other} as a new instance
     */
    public Complex add(Complex other) {
        return new Complex(this.real + other.real, this.imag + other.imag);
    }
    
    /**
     * Subtracts another complex number from this one.
     *
     * @param other the subtrahend
     * @return the difference {@code this - other} as a new instance
     */
    public Complex subtract(Complex other) {
        return new Complex(this.real - other.real, this.imag - other.imag);
    }
    
    /**
     * Multiplies this complex number by another.
     *
     * @param other the multiplicand
     * @return the product {@code this * other} as a new instance
     */
    public Complex multiply(Complex other) {
        double newReal = this.real * other.real - this.imag * other.imag;
        double newImag = this.real * other.imag + this.imag * other.real;
        return new Complex(newReal, newImag);
    }
    
    /**
     * Multiplies both parts by a real scalar.
     *
     * @param scalar the real scalar
     * @return a new complex number scaled by {@code scalar}
     */
    public Complex multiply(double scalar) {
        return new Complex(this.real * scalar, this.imag * scalar);
    }
    
    /**
     * Divides this complex number by another.
     *
     * @param other the divisor
     * @return the quotient {@code this / other} as a new instance
     * @throws ArithmeticException if the squared magnitude of {@code other} is less
     *         than {@code 1e-15}
     */
    public Complex divide(Complex other) {
        double denominator = other.real * other.real + other.imag * other.imag;
        if (Math.abs(denominator) < 1e-15) {
            throw new ArithmeticException("Division by zero complex number");
        }
        
        double newReal = (this.real * other.real + this.imag * other.imag) / denominator;
        double newImag = (this.imag * other.real - this.real * other.imag) / denominator;
        return new Complex(newReal, newImag);
    }
    
    /**
     * Divides both parts by a real scalar.
     *
     * @param scalar the real divisor
     * @return a new complex number divided by {@code scalar}
     * @throws ArithmeticException if the absolute value of {@code scalar} is less
     *         than {@code 1e-15}
     */
    public Complex divide(double scalar) {
        if (Math.abs(scalar) < 1e-15) {
            throw new ArithmeticException("Division by zero");
        }
        return new Complex(this.real / scalar, this.imag / scalar);
    }
    
    /**
     * Returns the complex conjugate, with the sign of the imaginary part flipped.
     *
     * @return the complex conjugate as a new instance
     */
    public Complex conjugate() {
        return new Complex(this.real, -this.imag);
    }
    
    /**
     * Returns the additive inverse.
     *
     * @return {@code -this} as a new instance
     */
    public Complex negate() {
        return new Complex(-this.real, -this.imag);
    }
    
    /**
     * Returns the modulus.
     *
     * @return {@code sqrt(real^2 + imag^2)}
     */
    public double abs() {
        return Math.sqrt(real * real + imag * imag);
    }
    
    /**
     * Returns the squared modulus, avoiding a square root.
     *
     * @return {@code real^2 + imag^2}
     */
    public double absSquare() {
        return real * real + imag * imag;
    }
    
    /**
     * Returns the argument of this complex number.
     *
     * @return the angle in radians, as computed by {@code Math.atan2(imag, real)}
     */
    public double phase() {
        return Math.atan2(imag, real);
    }
    
    /**
     * Returns the principal square root, which has a non-negative real part.
     *
     * @return the principal square root of this complex number
     */
    public Complex sqrt() {
        double r = Math.sqrt(this.abs());
        double theta = this.phase() / 2;
        return new Complex(r * Math.cos(theta), r * Math.sin(theta));
    }
    
    /**
     * Returns the complex exponential.
     *
     * @return {@code e^real * (cos(imag) + i sin(imag))} as a new instance
     */
    public Complex exp() {
        double expReal = Math.exp(real);
        return new Complex(expReal * Math.cos(imag), expReal * Math.sin(imag));
    }
    
    /**
     * Returns the principal natural logarithm.
     *
     * @return {@code ln|z| + i arg(z)} as a new instance
     */
    public Complex log() {
        return new Complex(Math.log(this.abs()), this.phase());
    }
    
    /**
     * Raises this complex number to a real power using the principal logarithm.
     *
     * @param exponent the real exponent
     * @return the principal value of {@code this^exponent}
     */
    public Complex pow(double exponent) {
        double logR = Math.log(this.abs());
        double theta = this.phase();
        double newR = Math.exp(exponent * logR);
        double newTheta = exponent * theta;
        return new Complex(newR * Math.cos(newTheta), newR * Math.sin(newTheta));
    }
    
    /**
     * Multiplies both parts by a real factor.
     *
     * @param factor the real factor
     * @return a new complex number scaled by {@code factor}
     */
    public Complex scale(double factor) {
        return new Complex(real * factor, imag * factor);
    }
    
    /**
     * Creates a complex number from polar coordinates.
     *
     * @param magnitude the magnitude, normally non-negative
     * @param phase the angle in radians
     * @return the value {@code magnitude * (cos(phase) + i sin(phase))}
     */
    public static Complex fromPolar(double magnitude, double phase) {
        return new Complex(magnitude * Math.cos(phase), magnitude * Math.sin(phase));
    }
    
    /**
     * Parses a textual complex number such as {@code "3.14+2.71i"}.
     *
     * <p>Spaces are removed before parsing. A pure real value, a pure imaginary
     * value such as {@code "3i"}, and the bare symbols {@code "i"} and {@code "j"}
     * are accepted.</p>
     *
     * @param str the text to parse
     * @return the parsed complex number
     * @throws NumberFormatException if the real or imaginary part is not a valid
     *         double
     */
    public static Complex parse(String str) {
        str = str.trim().replace(" ", "");
        
        if (str.equals("i") || str.equals("j")) {
            return I;
        }
        
        // Parse format like "3.14+2.71i" or "3.14-2.71i"
        String realPart = "0";
        String imagPart = "0";
        
        if (!str.contains("i") && !str.contains("j")) {
            // Pure real
            realPart = str;
        } else {
            // Has imaginary part
            int iIndex = Math.max(str.indexOf('i'), str.indexOf('j'));
            String beforeI = str.substring(0, iIndex);
            
            // Find the last + or - before i
            int plusIndex = beforeI.lastIndexOf('+');
            int minusIndex = beforeI.lastIndexOf('-');
            
            if (plusIndex == -1 && minusIndex == -1) {
                // Pure imaginary like "3i"
                if (beforeI.isEmpty() || beforeI.equals("+") || beforeI.equals("-")) {
                    beforeI += "1";
                }
                imagPart = beforeI;
            } else {
                int splitIndex = Math.max(plusIndex, minusIndex);
                if (splitIndex == 0 && (str.charAt(0) == '+' || str.charAt(0) == '-')) {
                    // Handle cases like "+3+4i"
                    int nextSplit = Math.max(beforeI.indexOf('+', 1), beforeI.indexOf('-', 1));
                    if (nextSplit != -1) {
                        splitIndex = nextSplit;
                    }
                }
                
                realPart = beforeI.substring(0, splitIndex);
                imagPart = beforeI.substring(splitIndex);
                
                if (realPart.isEmpty()) realPart = "0";
                if (imagPart.isEmpty() || imagPart.equals("+") || imagPart.equals("-")) {
                    imagPart += "1";
                }
            }
        }
        
        double real = Double.parseDouble(realPart);
        double imag = Double.parseDouble(imagPart.replace("i", "").replace("j", ""));
        
        return new Complex(real, imag);
    }
    
    /**
     * Compares this complex number with another object using an absolute tolerance
     * of {@code 1e-10} on both the real and imaginary parts.
     *
     * @param obj the object to compare with
     * @return {@code true} if {@code obj} is a {@code Complex} whose parts are both
     *         within {@code 1e-10} of this value
     */
    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        
        Complex other = (Complex) obj;
        return Math.abs(this.real - other.real) < 1e-10 &&
               Math.abs(this.imag - other.imag) < 1e-10;
    }
    
    /**
     * Returns a hash code based on the exact stored values.
     *
     * <p>Because {@link #equals(Object)} uses a tolerance, values that compare
     * equal may have different hash codes.</p>
     *
     * @return the hash code
     */
    @Override
    public int hashCode() {
        return Objects.hash(real, imag);
    }
    
    /**
     * Returns a formatted representation with six decimal places.
     *
     * <p>The imaginary term is omitted when its magnitude is less than
     * {@code 1e-10}; the real term is omitted when its magnitude is less than
     * {@code 1e-10} and the imaginary part is not.</p>
     *
     * @return the formatted string
     */
    @Override
    public String toString() {
        if (Math.abs(imag) < 1e-10) {
            return String.format(Locale.ROOT, "%.6f", real);
        } else if (Math.abs(real) < 1e-10) {
            return String.format(Locale.ROOT, "%.6fi", imag);
        } else {
            String sign = imag >= 0 ? "+" : "-";
            return String.format(Locale.ROOT, "%.6f %s %.6fi", real, sign, Math.abs(imag));
        }
    }
    
    /**
     * Returns the magnitude and phase as text.
     *
     * @return a string formatted as {@code "%.6f angle %.6f rad"}
     */
    public String toPolarString() {
        return String.format(Locale.ROOT, "%.6f angle %.6f rad", this.abs(), this.phase());
    }
}