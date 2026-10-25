package com.quantum.simulator.math;

import java.util.Locale;
import java.util.Objects;

/**
 * A named optimization parameter with bounds, an optional regularization weight,
 * and a type that controls rounding and regularization behavior.
 *
 * <p>{@link #setValue(double)} keeps the value within the closed interval
 * {@code [minValue, maxValue]}; integer parameters are rounded before clipping.</p>
 */
public class Parameter {
    private final String name;
    private final ParameterType type;
    private double value;
    private final double minValue;
    private final double maxValue;
    private final double regularization;
    private final double initialValue;
    
    /**
     * Kind of value stored by a {@link Parameter}.
     */
    public enum ParameterType {
        /** Continuous real value; the regularization cost is quadratic. */
        CONTINUOUS,
        /**
         * Value created from a list of allowed values. The list is not retained, so
         * later assignments are only clipped to the bounds derived at creation; the
         * regularization cost is quadratic.
         */
        DISCRETE,
        /**
         * Integer value. Assignments are rounded to the nearest integer; the
         * regularization cost is linear.
         */
        INTEGER,
        /** Categorical value; no regularization cost is applied. */
        CATEGORICAL
    }
    
    /**
     * Creates a continuous parameter with no regularization.
     *
     * @param name the parameter name, must not be null or blank
     * @param initialValue the initial value, must lie within the bounds
     * @param minValue the lower bound
     * @param maxValue the upper bound, must not be less than the lower bound
     * @throws IllegalArgumentException if the name is null or blank, the bounds are
     *         inconsistent, or the initial value is outside the bounds
     */
    public Parameter(String name, double initialValue, double minValue, double maxValue) {
        this(name, initialValue, minValue, maxValue, 0.0, ParameterType.CONTINUOUS);
    }
    
    /**
     * Creates a continuous parameter with a regularization weight.
     *
     * @param name the parameter name, must not be null or blank
     * @param initialValue the initial value, must lie within the bounds
     * @param minValue the lower bound
     * @param maxValue the upper bound, must not be less than the lower bound
     * @param regularization the non-negative regularization weight
     * @throws IllegalArgumentException if the name is null or blank, the bounds are
     *         inconsistent, the initial value is outside the bounds, or the
     *         regularization weight is negative
     */
    public Parameter(String name, double initialValue, double minValue, double maxValue, 
                    double regularization) {
        this(name, initialValue, minValue, maxValue, regularization, ParameterType.CONTINUOUS);
    }
    
    /**
     * Creates a parameter with an explicit type.
     *
     * @param name the parameter name, must not be null or blank
     * @param initialValue the initial value, must lie within the bounds
     * @param minValue the lower bound
     * @param maxValue the upper bound, must not be less than the lower bound
     * @param regularization the non-negative regularization weight
     * @param type the parameter type
     * @throws IllegalArgumentException if the name is null or blank,
     *         {@code minValue > maxValue}, the initial value is outside the bounds,
     *         or the regularization weight is negative
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
     * Creates a discrete parameter whose bounds are derived from the allowed
     * values. The initial value must match one of the allowed values within
     * {@code 1e-10}. The allowed values themselves are not stored, so later calls
     * to {@link #setValue(double)} only clip to the computed bounds.
     *
     * <p>The lower bound is the smallest allowed value. The upper bound is computed
     * with {@code Math.max} starting from {@code Double.MIN_VALUE}.</p>
     *
     * @param name the parameter name
     * @param allowedValues the non-null, non-empty list of permitted values
     * @param initialValue the initial value, which must appear in
     *        {@code allowedValues}
     * @return a parameter of type {@link ParameterType#DISCRETE}
     * @throws IllegalArgumentException if {@code allowedValues} is null or empty,
     *         if the name is null or blank, or if {@code initialValue} is not an
     *         allowed value within {@code 1e-10} and inside the derived bounds
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
     * Creates an integer parameter.
     *
     * <p>The bounds and initial value are not required to be integral, but every
     * assignment is rounded to the nearest integer.</p>
     *
     * @param name the parameter name, must not be null or blank
     * @param initialValue the initial value, must lie within the bounds
     * @param minValue the lower bound
     * @param maxValue the upper bound, must not be less than the lower bound
     * @return a parameter of type {@link ParameterType#INTEGER}
     * @throws IllegalArgumentException if the name is null or blank, the bounds are
     *         inconsistent, or the initial value is outside the bounds
     */
    public static Parameter createInteger(String name, int initialValue, int minValue, int maxValue) {
        return new Parameter(name, initialValue, minValue, maxValue, 0.0, ParameterType.INTEGER);
    }
    
    /**
     * Returns the parameter name.
     *
     * @return the name
     */
    public String getName() {
        return name;
    }
    
    /**
     * Returns the parameter type.
     *
     * @return the type
     */
    public ParameterType getType() {
        return type;
    }
    
    /**
     * Returns the current value.
     *
     * @return the current value
     */
    public double getValue() {
        return value;
    }
    
    /**
     * Returns the current value rounded to the nearest integer.
     *
     * @return the rounded current value
     */
    public int getIntValue() {
        return (int) Math.round(value);
    }
    
    /**
     * Sets the value, rounding first for integer parameters and then clipping to
     * the closed interval {@code [minValue, maxValue]}.
     *
     * @param newValue the requested value
     */
    public void setValue(double newValue) {
        if (type == ParameterType.INTEGER) {
            newValue = Math.round(newValue);
        }
        
        // Clip to bounds
        this.value = Math.max(minValue, Math.min(maxValue, newValue));
    }
    
    /**
     * Sets the value through {@link #setValue(double)}.
     *
     * @param newValue the requested value
     */
    public void setValue(int newValue) {
        setValue((double) newValue);
    }
    
    /**
     * Returns the lower bound.
     *
     * @return the lower bound
     */
    public double getMinValue() {
        return minValue;
    }
    
    /**
     * Returns the upper bound.
     *
     * @return the upper bound
     */
    public double getMaxValue() {
        return maxValue;
    }
    
    /**
     * Returns the initial value captured at construction.
     *
     * <p>For a parameter produced by {@link #copy()}, this is the value at the time
     * of copying.</p>
     *
     * @return the initial value
     */
    public double getInitialValue() {
        return initialValue;
    }
    
    /**
     * Returns the regularization weight.
     *
     * @return the regularization weight
     */
    public double getRegularization() {
        return regularization;
    }
    
    /**
     * Returns the width of the bounds.
     *
     * @return {@code maxValue - minValue}, which is zero when both bounds are equal
     */
    public double getRange() {
        return maxValue - minValue;
    }
    
    /**
     * Tests whether the value is close to either bound.
     *
     * @return {@code true} if the value is within {@code 1e-10} of the lower or
     *         upper bound
     */
    public boolean isAtBounds() {
        return Math.abs(value - minValue) < 1e-10 || Math.abs(value - maxValue) < 1e-10;
    }
    
    /**
     * Returns the value mapped linearly from the bounds to {@code [0, 1]}.
     *
     * @return {@code (value - minValue) / (maxValue - minValue)}, or {@code 0.0}
     *         when the range is zero
     */
    public double getNormalizedValue() {
        if (getRange() == 0) {
            return 0.0;
        }
        return (value - minValue) / getRange();
    }
    
    /**
     * Sets the value from a normalized position within the bounds.
     *
     * <p>The input is clipped to {@code [0, 1]}, mapped linearly to the bounds, and
     * rounded for integer parameters.</p>
     *
     * @param normalized the normalized value
     */
    public void setNormalizedValue(double normalized) {
        normalized = Math.max(0.0, Math.min(1.0, normalized));
        this.value = minValue + normalized * getRange();
        
        if (type == ParameterType.INTEGER) {
            this.value = Math.round(this.value);
        }
    }
    
    /**
     * Returns the regularization cost for the current value.
     *
     * <p>Continuous and discrete parameters use
     * {@code regularization * value^2}; integer parameters use
     * {@code regularization * value}; other types return {@code 0.0}. The result is
     * {@code 0.0} when the regularization weight is zero.</p>
     *
     * @return the regularization cost
     */
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
    
    /**
     * Returns a copy whose initial value is the current value of this parameter.
     * The name, type, bounds, and regularization weight are preserved.
     *
     * @return a new parameter with the current value captured as its initial value
     */
    public Parameter copy() {
        return new Parameter(name, value, minValue, maxValue, regularization, type);
    }
    
    /**
     * Restores the value to the initial value.
     *
     * <p>For a parameter produced by {@link #copy()}, this is the value captured at
     * copy time.</p>
     */
    public void reset() {
        this.value = initialValue;
    }
    
    /**
     * Adds Gaussian noise scaled by the parameter range, then clips the result
     * through {@link #setValue(double)}.
     *
     * @param noiseScale the standard deviation in units of {@link #getRange()}
     */
    public void addNoise(double noiseScale) {
        double noise = RandomGenerator.nextGaussian() * noiseScale * getRange();
        setValue(value + noise);
    }
    
    /**
     * Applies a gradient step through {@link #setValue(double)}.
     *
     * @param gradient the gradient of the objective with respect to this parameter
     * @param learningRate the step size
     */
    public void applyGradient(double gradient, double learningRate) {
        double newValue = value - learningRate * gradient;
        setValue(newValue);
    }
    
    /**
     * Compares this parameter with another object. The name, type, value, bounds,
     * and regularization weight must match, where numeric values are compared with
     * an absolute tolerance of {@code 1e-10}. The initial value is ignored.
     *
     * @param obj the object to compare with
     * @return {@code true} if all compared fields match
     */
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
    
    /**
     * Returns a hash code based on the name, type, and exact numeric values.
     *
     * <p>Because {@link #equals(Object)} uses a tolerance, parameters that compare
     * equal may have different hash codes.</p>
     *
     * @return the hash code
     */
    @Override
    public int hashCode() {
        return Objects.hash(name, type, value, minValue, maxValue, regularization);
    }
    
    /**
     * Returns a formatted summary with four decimal places.
     *
     * @return the formatted summary
     */
    @Override
    public String toString() {
        return String.format(Locale.ROOT, "Parameter[name='%s', type=%s, value=%.4f, range=[%.4f, %.4f], reg=%.4f]",
            name, type, value, minValue, maxValue, regularization);
    }
}