package com.quantum.simulator.math;

import java.util.Locale;
import java.util.Objects;

/**
 * Represents a parameter for optimization with bounds and regularization
 */
public class Parameter {
    private final String name;
    private final ParameterType type;
    private double value;
    private final double minValue;
    private final double maxValue;
    private final double regularization;
    private final double initialValue;
    
    public enum ParameterType {
        CONTINUOUS,
        DISCRETE,
        INTEGER,
        CATEGORICAL
    }
    
    /**
     * Create a continuous parameter
     */
    public Parameter(String name, double initialValue, double minValue, double maxValue) {
        this(name, initialValue, minValue, maxValue, 0.0, ParameterType.CONTINUOUS);
    }
    
    /**
     * Create a continuous parameter with regularization
     */
    public Parameter(String name, double initialValue, double minValue, double maxValue, 
                    double regularization) {
        this(name, initialValue, minValue, maxValue, regularization, ParameterType.CONTINUOUS);
    }
    
    /**
     * Create a parameter with specified type
     */
    public Parameter(String name, double initialValue, double minValue, double maxValue,
                    double regularization, ParameterType type) {
        if (name == null || name.trim().isEmpty()) {
            throw new IllegalArgumentException("Parameter name cannot be null or empty");
        }
        if (minValue > maxValue) {
            throw new IllegalArgumentException("Min value cannot be greater than max value");
        }
        if (initialValue < minValue || initialValue > maxValue) {
            throw new IllegalArgumentException(
                String.format(Locale.ROOT, "Initial value %.4f must be within bounds [%.4f, %.4f]", 
                    initialValue, minValue, maxValue));
        }
        if (regularization < 0) {
            throw new IllegalArgumentException("Regularization must be non-negative");
        }
        
        this.name = name;
        this.type = type;
        this.initialValue = initialValue;
        this.value = initialValue;
        this.minValue = minValue;
        this.maxValue = maxValue;
        this.regularization = regularization;
    }
    
    /**
     * Create a discrete parameter from a list of values
     */
    public static Parameter createDiscrete(String name, double[] allowedValues, double initialValue) {
        if (allowedValues == null || allowedValues.length == 0) {
            throw new IllegalArgumentException("Allowed values cannot be null or empty");
        }
        
        double min = Double.MAX_VALUE;
        double max = Double.MIN_VALUE;
        boolean foundInitial = false;
        
        for (double val : allowedValues) {
            min = Math.min(min, val);
            max = Math.max(max, val);
            if (Math.abs(val - initialValue) < 1e-10) {
                foundInitial = true;
            }
        }
        
        if (!foundInitial) {
            throw new IllegalArgumentException(
                String.format(Locale.ROOT, "Initial value %.4f is not in allowed values", initialValue));
        }
        
        Parameter param = new Parameter(name, initialValue, min, max, 0.0, ParameterType.DISCRETE);
        // Store allowed values in a custom property if needed
        return param;
    }
    
    /**
     * Create an integer parameter
     */
    public static Parameter createInteger(String name, int initialValue, int minValue, int maxValue) {
        return new Parameter(name, initialValue, minValue, maxValue, 0.0, ParameterType.INTEGER);
    }
    
    public String getName() {
        return name;
    }
    
    public ParameterType getType() {
        return type;
    }
    
    public double getValue() {
        return value;
    }
    
    public int getIntValue() {
        return (int) Math.round(value);
    }
    
    public void setValue(double newValue) {
        if (type == ParameterType.INTEGER) {
            newValue = Math.round(newValue);
        }
        
        // Clip to bounds
        this.value = Math.max(minValue, Math.min(maxValue, newValue));
    }
    
    public void setValue(int newValue) {
        setValue((double) newValue);
    }
    
    public double getMinValue() {
        return minValue;
    }
    
    public double getMaxValue() {
        return maxValue;
    }
    
    public double getInitialValue() {
        return initialValue;
    }
    
    public double getRegularization() {
        return regularization;
    }
    
    public double getRange() {
        return maxValue - minValue;
    }
    
    public boolean isAtBounds() {
        return Math.abs(value - minValue) < 1e-10 || Math.abs(value - maxValue) < 1e-10;
    }
    
    public double getNormalizedValue() {
        if (getRange() == 0) {
            return 0.0;
        }
        return (value - minValue) / getRange();
    }
    
    public void setNormalizedValue(double normalized) {
        normalized = Math.max(0.0, Math.min(1.0, normalized));
        this.value = minValue + normalized * getRange();
        
        if (type == ParameterType.INTEGER) {
            this.value = Math.round(this.value);
        }
    }
    
    public double getRegularizationCost() {
        if (regularization == 0.0) {
            return 0.0;
        }
        
        switch (type) {
            case CONTINUOUS:
            case DISCRETE:
                // L2 regularization
                return regularization * value * value;
            case INTEGER:
                // Different regularization for integers
                return regularization * value;
            default:
                return 0.0;
        }
    }
    
    public Parameter copy() {
        return new Parameter(name, value, minValue, maxValue, regularization, type);
    }
    
    public void reset() {
        this.value = initialValue;
    }
    
    public void addNoise(double noiseScale) {
        double noise = RandomGenerator.nextGaussian() * noiseScale * getRange();
        setValue(value + noise);
    }
    
    public void applyGradient(double gradient, double learningRate) {
        double newValue = value - learningRate * gradient;
        setValue(newValue);
    }
    
    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        
        Parameter other = (Parameter) obj;
        return name.equals(other.name) &&
               type == other.type &&
               Math.abs(value - other.value) < 1e-10 &&
               Math.abs(minValue - other.minValue) < 1e-10 &&
               Math.abs(maxValue - other.maxValue) < 1e-10 &&
               Math.abs(regularization - other.regularization) < 1e-10;
    }
    
    @Override
    public int hashCode() {
        return Objects.hash(name, type, value, minValue, maxValue, regularization);
    }
    
    @Override
    public String toString() {
        return String.format(Locale.ROOT, "Parameter[name='%s', type=%s, value=%.4f, range=[%.4f, %.4f], reg=%.4f]",
            name, type, value, minValue, maxValue, regularization);
    }
}