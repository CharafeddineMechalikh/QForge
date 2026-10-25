package com.quantum.simulator.classical;

import java.util.*;

public class ParameterSpace {
    private final Map<String, Parameter> parameters;
    private final Random random;
    
    public ParameterSpace() {
        this.parameters = new HashMap<>();
        this.random = new Random();
    }
    
    public void addParameter(String name, double initialValue, double min, double max) {
        addParameter(name, initialValue, min, max, 0.0); // No regularization by default
    }
    
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
    
    public void addDiscreteParameter(String name, List<Object> values, Object initialValue) {
        if (parameters.containsKey(name)) {
            throw new IllegalArgumentException("Parameter '" + name + "' already exists");
        }
        
        if (!values.contains(initialValue)) {
            throw new IllegalArgumentException("Initial value must be in the allowed values list");
        }
        
        parameters.put(name, new DiscreteParameter(name, values, initialValue));
    }
    
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
    
    public Object getValue(String name) {
        Parameter param = parameters.get(name);
        if (param == null) {
            throw new IllegalArgumentException("Parameter '" + name + "' not found");
        }
        return param.currentValue;
    }
    
    public double getDoubleValue(String name) {
        Object value = getValue(name);
        if (value instanceof Number) {
            return ((Number) value).doubleValue();
        }
        throw new IllegalArgumentException("Parameter '" + name + "' is not numeric");
    }
    
    public int getIntValue(String name) {
        Object value = getValue(name);
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        throw new IllegalArgumentException("Parameter '" + name + "' is not numeric");
    }
    
    public Map<String, Object> getCurrentValues() {
        Map<String, Object> values = new HashMap<>();
        for (Map.Entry<String, Parameter> entry : parameters.entrySet()) {
            values.put(entry.getKey(), entry.getValue().currentValue);
        }
        return values;
    }
    
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
    
    public Set<String> getParameterNames() {
        return Collections.unmodifiableSet(parameters.keySet());
    }
    
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
    public static class ParameterInfo {
        private final String name;
        private final String type;
        private final Object minOrValues;
        private final Object maxOrCurrent;
        private final Object currentValue;
        private final Double regularization;
        
        // Constructor for numeric parameters
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
        public ParameterInfo(String name, String type, List<Object> values, Object currentValue) {
            this.name = name;
            this.type = type;
            this.minOrValues = values;
            this.maxOrCurrent = null;
            this.currentValue = currentValue;
            this.regularization = null;
        }
        
        // Constructor for generic parameters
        public ParameterInfo(String name, String type, Object currentValue) {
            this.name = name;
            this.type = type;
            this.minOrValues = null;
            this.maxOrCurrent = null;
            this.currentValue = currentValue;
            this.regularization = null;
        }
         

		public String getName() { return name; }
        public String getType() { return type; }
        public Object getMinOrValues() { return minOrValues; }
        public Object getMaxOrCurrent() { return maxOrCurrent; }
        public Object getCurrentValue() { return currentValue; }
        public Double getRegularization() { return regularization; }
        
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
    
    public void addParameter(String name, Object initialValue) {
        if (parameters.containsKey(name)) {
            throw new IllegalArgumentException("Parameter '" + name + "' already exists");
        }
        parameters.put(name, new GenericParameter(name, initialValue));
    }
    
    public void removeParameter(String name) {
        parameters.remove(name);
    }
    
    public boolean hasParameter(String name) {
        return parameters.containsKey(name);
    }
    
    public void setRandomSeed(long seed) {
        random.setSeed(seed);
    }
    
    public List<Map<String, Object>> sampleConfigurations(int numSamples) {
        List<Map<String, Object>> samples = new ArrayList<>();
        for (int i = 0; i < numSamples; i++) {
            samples.add(getRandomConfiguration());
        }
        return samples;
    }
    
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
    
    public double getParameterRange(String name) {
        Parameter param = parameters.get(name);
        if (param instanceof NumericParameter) {
            NumericParameter np = (NumericParameter) param;
            return np.max - np.min;
        }
        return 0.0;
    }
    
    public boolean isWithinBounds(String name, double value) {
        Parameter param = parameters.get(name);
        if (param instanceof NumericParameter) {
            NumericParameter np = (NumericParameter) param;
            return value >= np.min && value <= np.max;
        }
        return true; // Non-numeric parameters have no bounds
    }
    
    // Factory method for common parameter types
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