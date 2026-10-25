package com.quantum.simulator.math;

import java.util.Locale;
import java.util.Objects;

public class Complex {
    public static final Complex ZERO = new Complex(0, 0);
    public static final Complex ONE = new Complex(1, 0);
    public static final Complex I = new Complex(0, 1);
    
    private final double real;
    private final double imag;
    
    public Complex(double real, double imag) {
        this.real = real;
        this.imag = imag;
    }
    
    public Complex(Complex other) {
        this.real = other.real;
        this.imag = other.imag;
    }
    
    public double real() {
        return real;
    }
    
    public double imag() {
        return imag;
    }
    
    public Complex add(Complex other) {
        return new Complex(this.real + other.real, this.imag + other.imag);
    }
    
    public Complex subtract(Complex other) {
        return new Complex(this.real - other.real, this.imag - other.imag);
    }
    
    public Complex multiply(Complex other) {
        double newReal = this.real * other.real - this.imag * other.imag;
        double newImag = this.real * other.imag + this.imag * other.real;
        return new Complex(newReal, newImag);
    }
    
    public Complex multiply(double scalar) {
        return new Complex(this.real * scalar, this.imag * scalar);
    }
    
    public Complex divide(Complex other) {
        double denominator = other.real * other.real + other.imag * other.imag;
        if (Math.abs(denominator) < 1e-15) {
            throw new ArithmeticException("Division by zero complex number");
        }
        
        double newReal = (this.real * other.real + this.imag * other.imag) / denominator;
        double newImag = (this.imag * other.real - this.real * other.imag) / denominator;
        return new Complex(newReal, newImag);
    }
    
    public Complex divide(double scalar) {
        if (Math.abs(scalar) < 1e-15) {
            throw new ArithmeticException("Division by zero");
        }
        return new Complex(this.real / scalar, this.imag / scalar);
    }
    
    public Complex conjugate() {
        return new Complex(this.real, -this.imag);
    }
    
    public Complex negate() {
        return new Complex(-this.real, -this.imag);
    }
    
    public double abs() {
        return Math.sqrt(real * real + imag * imag);
    }
    
    public double absSquare() {
        return real * real + imag * imag;
    }
    
    public double phase() {
        return Math.atan2(imag, real);
    }
    
    public Complex sqrt() {
        double r = Math.sqrt(this.abs());
        double theta = this.phase() / 2;
        return new Complex(r * Math.cos(theta), r * Math.sin(theta));
    }
    
    public Complex exp() {
        double expReal = Math.exp(real);
        return new Complex(expReal * Math.cos(imag), expReal * Math.sin(imag));
    }
    
    public Complex log() {
        return new Complex(Math.log(this.abs()), this.phase());
    }
    
    public Complex pow(double exponent) {
        double logR = Math.log(this.abs());
        double theta = this.phase();
        double newR = Math.exp(exponent * logR);
        double newTheta = exponent * theta;
        return new Complex(newR * Math.cos(newTheta), newR * Math.sin(newTheta));
    }
    
    public Complex scale(double factor) {
        return new Complex(real * factor, imag * factor);
    }
    
    public static Complex fromPolar(double magnitude, double phase) {
        return new Complex(magnitude * Math.cos(phase), magnitude * Math.sin(phase));
    }
    
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
    
    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        
        Complex other = (Complex) obj;
        return Math.abs(this.real - other.real) < 1e-10 &&
               Math.abs(this.imag - other.imag) < 1e-10;
    }
    
    @Override
    public int hashCode() {
        return Objects.hash(real, imag);
    }
    
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
    
    public String toPolarString() {
        return String.format(Locale.ROOT, "%.6f angle %.6f rad", this.abs(), this.phase());
    }
}