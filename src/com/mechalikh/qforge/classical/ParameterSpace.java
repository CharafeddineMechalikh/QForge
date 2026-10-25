package com.mechalikh.qforge.classical;

import java.util.*;

/**
 * Named parameter container with numeric bounds, discrete choices and optional L2
 * regularization.
 *
 * <p>Numeric parameters are added with
 * {@link #addParameter(String, double, double, double)} and accept a
 * {@link Number}, which is clamped to the declared bounds when written through
 * {@link #setValue(String, Object)}. Discrete parameters are added with
 * {@link #addDiscreteParameter(String, List, Object)} and accept only values from their
 * list. Generic parameters are added with {@link #addParameter(String, Object)} and
 * accept only {@link Number} values.
 *
 * <p>Random configurations and neighbours use an internal {@link Random}, whose seed
 * can be fixed with {@link #setRandomSeed(long)}. The class is a mutable container
 * without synchronization.
 */
public class ParameterSpace {
    private final Map<String, Parameter> parameters;
    private final Random random;
    
    /**
     * Creates an empty parameter space with an unseeded random generator.
     */
    public ParameterSpace() {
        this.parameters = new HashMap<>();
        this.random = new Random();
    }
    
    /**
     * Adds a numeric parameter without regularization.
     *
     * <p>Equivalent to calling
     * {@link #addParameter(String, double, double, double, double)} with a
     * regularization weight of {@code 0.0}.
     *
     * @param name parameter name
     * @param initialValue starting value, which must lie within the bounds
     * @param min lower bound, inclusive
     * @param max upper bound, inclusive
     * @throws IllegalArgumentException if the name already exists, if {@code min} is
     *                                  greater than {@code max}, or if the initial value
     *                                  is outside the bounds
     */
    public void addParameter(String name, double initialValue, double min, double max) {
        addParameter(name, initialValue, min, max, 0.0); // No regularization by default
    }
    
    /**
     * Adds a numeric parameter with bounds and an L2 regularization weight.
     *
     * <p>The weight is stored for use by {@link #computeRegularizationCost(Map)} and is
     * not applied by any other method. Both bounds are inclusive.
     *
     * @param name parameter name
     * @param initialValue starting value, which must lie within the bounds
     * @param min lower bound, inclusive
     * @param max upper bound, inclusive
     * @param regularization L2 penalty weight, used only when greater than zero
     * @throws IllegalArgumentException if the name already exists, if {@code min} is
     *                                  greater than {@code max}, or if the initial value
     *                                  is outside the bounds
     */
    public void addParameter(String name, double initialValue, double min, double max, double regularization) {
        if (parameters.containsKey(name)) {
            throw new IllegalArgumentException("Parameter '" + name + "' already exists");
        }
        
        if (min > max) {
            throw new IllegalArgumentException("Min cannot be greater than max");
        }
        
        if (initialValue < min || initialValue > max) {
            throw new IllegalArgumentException("Initial value must be within bounds");
        }
        
        parameters.put(name, new NumericParameter(name, initialValue, min, max, regularization));
    }
    
    /**
     * Adds a parameter that accepts one of a fixed list of values.
     *
     * <p>The value list is copied.
     *
     * @param name parameter name
     * @param values allowed values
     * @param initialValue starting value, which must be one of {@code values}
     * @throws IllegalArgumentException if the name already exists or the initial value
     *                                  is not in the list
     */
    public void addDiscreteParameter(String name, List<Object> values, Object initialValue) {
        if (parameters.containsKey(name)) {
            throw new IllegalArgumentException("Parameter '" + name + "' already exists");
        }
        
        if (!values.contains(initialValue)) {
            throw new IllegalArgumentException("Initial value must be in the allowed values list");
        }
        
        parameters.put(name, new DiscreteParameter(name, values, initialValue));
    }
    
    /**
     * Sets the current value of a parameter.
     *
     * <p>Discrete parameters accept only declared values. Numeric parameters require a
     * {@link Number}, which is converted to {@code double} and clamped to the inclusive
     * bounds rather than rejected. Generic parameters also require a {@link Number}.
     *
     * @param name parameter name
     * @param value new value
     * @throws IllegalArgumentException if the parameter is unknown or the value is not
     *                                  accepted by the parameter type
     */
    public void setValue(String name, Object value) {
        Parameter param = parameters.get(name);
        if (param == null) {
            throw new IllegalArgumentException("Parameter '" + name + "' not found");
        }
        
        if (param instanceof DiscreteParameter) {
            DiscreteParameter dp = (DiscreteParameter) param;
            if (!dp.values.contains(value)) {
                throw new IllegalArgumentException("Value not allowed for discrete parameter");
            }
            dp.currentValue = value;
        } else if (param instanceof NumericParameter) {
            NumericParameter np = (NumericParameter) param;
            if (value instanceof Number) {
                double doubleValue = ((Number) value).doubleValue();
                np.currentValue = Math.max(np.min, Math.min(np.max, doubleValue));
            } else {
                throw new IllegalArgumentException("Value must be numeric for this parameter");
            }
        } else {
            // Handle base Parameter class
            if (value instanceof Number) {
                // For generic parameters, just store the value
                param.currentValue = value;
            } else {
                throw new IllegalArgumentException("Invalid value type for parameter");
            }
        }
    }
    
    /**
     * Returns the current value of a parameter.
     *
     * @param name parameter name
     * @return current value
     * @throws IllegalArgumentException if the parameter is unknown
     */
    public Object getValue(String name) {
        Parameter param = parameters.get(name);
        if (param == null) {
            throw new IllegalArgumentException("Parameter '" + name + "' not found");
        }
        return param.currentValue;
    }
    
    /**
     * Returns the current value of a parameter as a {@code double}.
     *
     * @param name parameter name
     * @return current value converted to {@code double}
     * @throws IllegalArgumentException if the parameter is unknown or its value is not a
     *                                  {@link Number}
     */
    public double getDoubleValue(String name) {
        Object value = getValue(name);
        if (value instanceof Number) {
            return ((Number) value).doubleValue();
        }
        throw new IllegalArgumentException("Parameter '" + name + "' is not numeric");
    }
    
    /**
     * Returns the current value of a parameter as an {@code int}.
     *
     * @param name parameter name
     * @return current value converted to {@code int}
     * @throws IllegalArgumentException if the parameter is unknown or its value is not a
     *                                  {@link Number}
     */
    public int getIntValue(String name) {
        Object value = getValue(name);
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        throw new IllegalArgumentException("Parameter '" + name + "' is not numeric");
    }
    
    /**
     * Returns the current values of all parameters.
     *
     * @return new map from parameter names to current values
     */
    public Map<String, Object> getCurrentValues() {
        Map<String, Object> values = new HashMap<>();
        for (Map.Entry<String, Parameter> entry : parameters.entrySet()) {
            values.put(entry.getKey(), entry.getValue().currentValue);
        }
        return values;
    }
    
    /**
     * Draws a random configuration over the declared domains.
     *
     * <p>Numeric parameters are drawn uniformly from {@code [min, max)}, discrete
     * parameters uniformly from their value list, and generic parameters keep their
     * current value.
     *
     * @return new map from parameter names to values
     */
    public Map<String, Object> getRandomConfiguration() {
        Map<String, Object> config = new HashMap<>();
        
        for (Map.Entry<String, Parameter> entry : parameters.entrySet()) {
            Parameter param = entry.getValue();
            
            if (param instanceof DiscreteParameter) {
                DiscreteParameter dp = (DiscreteParameter) param;
                int randomIndex = random.nextInt(dp.values.size());
                config.put(entry.getKey(), dp.values.get(randomIndex));
            } else if (param instanceof NumericParameter) {
                NumericParameter np = (NumericParameter) param;
                double randomValue = np.min + random.nextDouble() * (np.max - np.min);
                config.put(entry.getKey(), randomValue);
            } else {
                // Generic parameter - use current value
                config.put(entry.getKey(), param.currentValue);
            }
        }
        
        return config;
    }
    
    /**
     * Perturbs a configuration with one random step.
     *
     * <p>Each numeric parameter is shifted by an amount drawn uniformly between
     * {@code -stepSize * range} and {@code stepSize * range}, where {@code range} is
     * {@code max - min}, and the result is clipped to the inclusive bounds. Discrete and
     * generic parameters are copied unchanged.
     *
     * @param current base configuration, which must map every numeric parameter to a
     *                number
     * @param stepSize relative step scale
     * @return new map with the perturbed configuration
     * @throws NullPointerException if {@code current} has no entry for a numeric
     *                              parameter
     */
    public Map<String, Object> getNeighborConfiguration(Map<String, Object> current, double stepSize) {
        Map<String, Object> neighbor = new HashMap<>(current);
        
        for (Map.Entry<String, Parameter> entry : parameters.entrySet()) {
            String name = entry.getKey();
            Parameter param = entry.getValue();
            
            if (param instanceof NumericParameter) {
                NumericParameter np = (NumericParameter) param;
                double currentValue = ((Number) current.get(name)).doubleValue();
                
                // Add random perturbation
                double perturbation = (random.nextDouble() * 2 - 1) * stepSize * (np.max - np.min);
                double newValue = currentValue + perturbation;
                
                // Clip to bounds
                newValue = Math.max(np.min, Math.min(np.max, newValue));
                neighbor.put(name, newValue);
            }
            // Discrete parameters stay the same in this simple implementation
        }
        
        return neighbor;
    }
    
    /**
     * Sums the L2 penalties of the numeric parameters.
     *
     * <p>A numeric parameter contributes {@code regularization * value * value} only
     * when its weight is greater than zero. Discrete and generic parameters never
     * contribute.
     *
     * @param values values to evaluate, which must contain every numeric parameter with
     *               a positive regularization weight and map it to a number
     * @return accumulated penalty
     * @throws NullPointerException if such a parameter is missing or maps to
     *                              {@code null}
     */
    public double computeRegularizationCost(Map<String, Object> values) {
        double cost = 0.0;
        
        for (Map.Entry<String, Parameter> entry : parameters.entrySet()) {
            Parameter param = entry.getValue();
            if (param instanceof NumericParameter) {
                NumericParameter np = (NumericParameter) param;
                if (np.regularization > 0) {
                    double value = ((Number) values.get(entry.getKey())).doubleValue();
                    // L2 regularization
                    cost += np.regularization * value * value;
                }
            }
        }
        
        return cost;
    }
    
    /**
     * Returns the parameter names.
     *
     * <p>The returned set is an unmodifiable view of the live key set, so it reflects
     * parameters added or removed after this call.
     *
     * @return unmodifiable view of the parameter names
     */
    public Set<String> getParameterNames() {
        return Collections.unmodifiableSet(parameters.keySet());
    }
    
    /**
     * Describes a parameter.
     *
     * @param name parameter name
     * @return description of the parameter, or {@code null} when the name is unknown
     */
    public ParameterInfo getParameterInfo(String name) {
        Parameter param = parameters.get(name);
        if (param == null) {
            return null;
        }
        
        if (param instanceof DiscreteParameter) {
            DiscreteParameter dp = (DiscreteParameter) param;
            return new ParameterInfo(name, "discrete", dp.values, dp.currentValue);
        } else if (param instanceof NumericParameter) {
            NumericParameter np = (NumericParameter) param;
            return new ParameterInfo(name, "numeric", np.min, np.max, (double) np.currentValue, np.regularization);
        } else {
            // Generic parameter
            return new ParameterInfo(name, "generic", param.currentValue);
             
        }
    }
    
    /**
     * Restores the initial values of numeric and discrete parameters.
     *
     * <p>Generic parameters added with {@link #addParameter(String, Object)} keep their
     * current value because no separate initial value is recorded for them.
     */
    public void resetToInitial() {
        for (Parameter param : parameters.values()) {
            if (param instanceof DiscreteParameter) {
                DiscreteParameter dp = (DiscreteParameter) param;
                dp.currentValue = dp.initialValue;
            } else if (param instanceof NumericParameter) {
                NumericParameter np = (NumericParameter) param;
                np.currentValue = np.initialValue;
            } else {
                // Generic parameter - reset to whatever was stored
                // (We don't have initial value for generic parameters)
            }
        }
    }
    
    // ==================== INNER CLASSES ====================
    
    // Abstract base class
    private abstract static class Parameter {
        final String name;
        Object currentValue;
        
        Parameter(String name) {
            this.name = name;
        }
    }
    
    // Numeric parameter class
    private static class NumericParameter extends Parameter {
        final double initialValue;
        final double min;
        final double max;
        final double regularization;
        
        NumericParameter(String name, double initialValue, double min, double max, double regularization) {
            super(name);
            this.initialValue = initialValue;
            this.min = min;
            this.max = max;
            this.regularization = regularization;
            this.currentValue = initialValue;
        }
    }
    
    // Discrete parameter class
    private static class DiscreteParameter extends Parameter {
        final List<Object> values;
        final Object initialValue;
        
        DiscreteParameter(String name, List<Object> values, Object initialValue) {
            super(name);
            this.values = new ArrayList<>(values);
            this.initialValue = initialValue;
            this.currentValue = initialValue;
        }
    }
    
    // Generic parameter class (for completeness)
    private static class GenericParameter extends Parameter {
        final Object initialValue;
        
        GenericParameter(String name, Object initialValue) {
            super(name);
            this.initialValue = initialValue;
            this.currentValue = initialValue;
        }
    }
    
    // Information class for parameter inspection
    /**
     * Description of one parameter, as returned by
     * {@link ParameterSpace#getParameterInfo(String)}.
     *
     * <p>The {@code type} string selects how the fields are interpreted:
     * {@code "numeric"} uses the minimum and maximum, {@code "discrete"} uses the value
     * list, and any other value is treated as a generic parameter.
     * {@link #toString()} formats the description according to the same string.
     */
    public static class ParameterInfo {
        private final String name;
        private final String type;
        private final Object minOrValues;
        private final Object maxOrCurrent;
        private final Object currentValue;
        private final Double regularization;
        
        // Constructor for numeric parameters
        /**
         * Creates a description of a numeric parameter.
         *
         * @param name parameter name
         * @param type descriptive type string, normally {@code "numeric"}
         * @param min lower bound
         * @param max upper bound
         * @param currentValue current value
         * @param regularization L2 regularization weight
         */
        public ParameterInfo(String name, String type, double min, double max, 
                           double currentValue, double regularization) {
            this.name = name;
            this.type = type;
            this.minOrValues = min;
            this.maxOrCurrent = max;
            this.currentValue = currentValue;
            this.regularization = regularization;
        }
        
        // Constructor for discrete parameters
        /**
         * Creates a description of a discrete parameter.
         *
         * @param name parameter name
         * @param type descriptive type string, normally {@code "discrete"}
         * @param values allowed values
         * @param currentValue current value
         */
        public ParameterInfo(String name, String type, List<Object> values, Object currentValue) {
            this.name = name;
            this.type = type;
            this.minOrValues = values;
            this.maxOrCurrent = null;
            this.currentValue = currentValue;
            this.regularization = null;
        }
        
        // Constructor for generic parameters
        /**
         * Creates a description of a generic parameter.
         *
         * @param name parameter name
         * @param type descriptive type string
         * @param currentValue current value
         */
        public ParameterInfo(String name, String type, Object currentValue) {
            this.name = name;
            this.type = type;
            this.minOrValues = null;
            this.maxOrCurrent = null;
            this.currentValue = currentValue;
            this.regularization = null;
        }
         

        /**
         * Returns the parameter name.
         *
         * @return parameter name
         */
		public String getName() { return name; }
        /**
         * Returns the descriptive type string.
         *
         * @return type string
         */
        public String getType() { return type; }
        /**
         * Returns the minimum bound or the value list, depending on the parameter type.
         *
         * @return minimum bound for numeric parameters, value list for discrete
         *         parameters, and {@code null} otherwise
         */
        public Object getMinOrValues() { return minOrValues; }
        /**
         * Returns the maximum bound, when one was supplied.
         *
         * @return maximum bound for numeric parameters, {@code null} otherwise
         */
        public Object getMaxOrCurrent() { return maxOrCurrent; }
        /**
         * Returns the current value.
         *
         * @return current value
         */
        public Object getCurrentValue() { return currentValue; }
        /**
         * Returns the regularization weight, when one was supplied.
         *
         * @return regularization weight for numeric parameters, {@code null} otherwise
         */
        public Double getRegularization() { return regularization; }
        
        /**
         * Returns a formatted description.
         *
         * <p>The format is chosen by the type string: {@code "numeric"} prints the range,
         * current value and regularization, {@code "discrete"} prints the value list and
         * current value, and any other type prints the current value.
         *
         * @return formatted description
         */
        @Override
        public String toString() {
            if (type.equals("numeric")) {
                return String.format(Locale.ROOT, "ParameterInfo[name='%s', type=numeric, range=[%.4f, %.4f], current=%.4f, reg=%.4f]",
                    name, (Double) minOrValues, (Double) maxOrCurrent, (Double) currentValue, regularization);
            } else if (type.equals("discrete")) {
                return String.format(Locale.ROOT, "ParameterInfo[name='%s', type=discrete, values=%s, current=%s]",
                    name, minOrValues, currentValue);
            } else {
                return String.format(Locale.ROOT, "ParameterInfo[name='%s', type=generic, current=%s]",
                    name, currentValue);
            }
        }
    }
    
    // ==================== ADDITIONAL UTILITY METHODS ====================
    
    /**
     * Adds a generic parameter that stores an arbitrary initial value.
     *
     * <p>Generic parameters have no bounds and, in {@link #setValue(String, Object)},
     * accept only {@link Number} values.
     *
     * @param name parameter name
     * @param initialValue starting value, also used as the current value
     * @throws IllegalArgumentException if the name already exists
     */
    public void addParameter(String name, Object initialValue) {
        if (parameters.containsKey(name)) {
            throw new IllegalArgumentException("Parameter '" + name + "' already exists");
        }
        parameters.put(name, new GenericParameter(name, initialValue));
    }
    
    /**
     * Removes a parameter.
     *
     * @param name parameter name, which may be absent
     */
    public void removeParameter(String name) {
        parameters.remove(name);
    }
    
    /**
     * Reports whether a parameter exists.
     *
     * @param name parameter name
     * @return {@code true} when the parameter exists
     */
    public boolean hasParameter(String name) {
        return parameters.containsKey(name);
    }
    
    /**
     * Seeds the internal random generator.
     *
     * <p>Seeding makes subsequent random configurations and perturbations reproducible
     * for the same sequence of calls.
     *
     * @param seed random seed
     */
    public void setRandomSeed(long seed) {
        random.setSeed(seed);
    }
    
    /**
     * Draws several independent random configurations.
     *
     * @param numSamples number of configurations to draw
     * @return list of configurations, empty when {@code numSamples} is not positive
     */
    public List<Map<String, Object>> sampleConfigurations(int numSamples) {
        List<Map<String, Object>> samples = new ArrayList<>();
        for (int i = 0; i < numSamples; i++) {
            samples.add(getRandomConfiguration());
        }
        return samples;
    }
    
    /**
     * Averages a list of configurations.
     *
     * <p>Numeric parameters are averaged arithmetically, so every configuration must
     * map them to a number. Other parameters use their most common value; ties depend on
     * hash iteration order, so the winner is not specified. An empty list yields the
     * current values.
     *
     * @param configurations configurations to average, normally drawn from this space
     * @return new map with the averaged values
     */
    public Map<String, Object> getMeanConfiguration(List<Map<String, Object>> configurations) {
        if (configurations.isEmpty()) {
            return getCurrentValues();
        }
        
        Map<String, Object> meanConfig = new HashMap<>();
        
        for (String paramName : getParameterNames()) {
            Parameter param = parameters.get(paramName);
            
            if (param instanceof NumericParameter) {
                double sum = 0.0;
                for (Map<String, Object> config : configurations) {
                    sum += ((Number) config.get(paramName)).doubleValue();
                }
                meanConfig.put(paramName, sum / configurations.size());
            } else {
                // For non-numeric parameters, use mode or most common value
                Map<Object, Integer> frequency = new HashMap<>();
                for (Map<String, Object> config : configurations) {
                    Object value = config.get(paramName);
                    frequency.put(value, frequency.getOrDefault(value, 0) + 1);
                }
                
                Object mostCommon = null;
                int maxCount = 0;
                for (Map.Entry<Object, Integer> entry : frequency.entrySet()) {
                    if (entry.getValue() > maxCount) {
                        mostCommon = entry.getKey();
                        maxCount = entry.getValue();
                    }
                }
                
                meanConfig.put(paramName, mostCommon);
            }
        }
        
        return meanConfig;
    }
    
    /**
     * Clamps numeric values to the parameter bounds in place.
     *
     * <p>Entries for unknown or non-numeric parameters are left unchanged.
     *
     * @param values values to clamp; entries for numeric parameters must hold a
     *               {@link Number}
     */
    public void clipToBounds(Map<String, Object> values) {
        for (Map.Entry<String, Object> entry : values.entrySet()) {
            String name = entry.getKey();
            Parameter param = parameters.get(name);
            
            if (param instanceof NumericParameter) {
                NumericParameter np = (NumericParameter) param;
                double value = ((Number) entry.getValue()).doubleValue();
                value = Math.max(np.min, Math.min(np.max, value));
                values.put(name, value);
            }
        }
    }
    
    /**
     * Returns the width of a numeric parameter range.
     *
     * @param name parameter name
     * @return {@code max - min} for a numeric parameter, and {@code 0.0} for other or
     *         unknown names
     */
    public double getParameterRange(String name) {
        Parameter param = parameters.get(name);
        if (param instanceof NumericParameter) {
            NumericParameter np = (NumericParameter) param;
            return np.max - np.min;
        }
        return 0.0;
    }
    
    /**
     * Tests whether a value lies within the inclusive bounds of a numeric parameter.
     *
     * <p>Non-numeric and unknown parameters have no bounds, so this method returns
     * {@code true} for them.
     *
     * @param name parameter name
     * @param value value to test
     * @return {@code true} when the value is inside the bounds or the parameter has no
     *         bounds
     */
    public boolean isWithinBounds(String name, double value) {
        Parameter param = parameters.get(name);
        if (param instanceof NumericParameter) {
            NumericParameter np = (NumericParameter) param;
            return value >= np.min && value <= np.max;
        }
        return true; // Non-numeric parameters have no bounds
    }
    
    // Factory method for common parameter types
    /**
     * Builds a parameter space with rotation and entanglement angles for a register.
     *
     * <p>For each qubit {@code i} the space contains {@code theta_i} and {@code phi_i}
     * with bounds {@code [-pi, pi]}, initial value {@code 0.0} and regularization
     * {@code 0.001}. For each adjacent pair it adds {@code entangle_i_(i+1)} with the
     * same bounds and initial value and a regularization of {@code 0.01}.
     *
     * @param numQubits number of qubits
     * @return new parameter space
     */
    public static ParameterSpace createDefaultParameterSpace(int numQubits) {
        ParameterSpace space = new ParameterSpace();
        
        // Add rotation angles for each qubit
        for (int i = 0; i < numQubits; i++) {
            space.addParameter("theta_" + i, 0.0, -Math.PI, Math.PI, 0.001);
            space.addParameter("phi_" + i, 0.0, -Math.PI, Math.PI, 0.001);
        }
        
        // Add entangling parameters for adjacent qubits
        for (int i = 0; i < numQubits - 1; i++) {
            space.addParameter("entangle_" + i + "_" + (i + 1), 0.0, -Math.PI, Math.PI, 0.01);
        }
        
        return space;
    }
    
    // Example usage
    /**
     * Prints a small demonstration of the parameter space to standard output.
     *
     * @param args command line arguments, not used
     */
    public static void main(String[] args) {
        ParameterSpace space = new ParameterSpace();
        
        // Add numeric parameters
        space.addParameter("learning_rate", 0.01, 0.0001, 1.0, 0.001);
        space.addParameter("momentum", 0.9, 0.0, 1.0);
        
        // Add discrete parameter
        List<Object> optimizers = Arrays.asList("adam", "sgd", "rmsprop", "adagrad");
        space.addDiscreteParameter("optimizer", optimizers, "adam");
        
        // Get current values
        Map<String, Object> current = space.getCurrentValues();
        System.out.println("Current parameters: " + current);
        
        // Get parameter info
        ParameterSpace.ParameterInfo lrInfo = space.getParameterInfo("learning_rate");
        System.out.println("Learning rate info: " + lrInfo);
        
        // Sample random configurations
        List<Map<String, Object>> samples = space.sampleConfigurations(3);
        System.out.println("Random samples:");
        for (int i = 0; i < samples.size(); i++) {
            System.out.println("  Sample " + i + ": " + samples.get(i));
        }
    }
}