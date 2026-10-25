package example;

import com.quantum.simulator.classical.*;
import com.quantum.simulator.config.SimulationBackend;
import com.quantum.simulator.config.SimulationConfig;
import com.quantum.simulator.core.*;
import com.quantum.simulator.events.QuantumEvent;
import com.quantum.simulator.math.Complex;
import com.quantum.simulator.operations.*;
import com.quantum.simulator.state.*;
import com.quantum.simulator.measurement.*;
import com.quantum.simulator.measurement.Observable;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Runs three feedback experiments that use a small adaptive controller: state preparation, error
 * mitigation, and parameter optimization.
 *
 * <p>The controller is a heuristic rather than a trained model. It keeps running averages and a
 * sign-only gradient, explores randomly with a decaying exploration rate, and stores its history
 * in memory. The state preparation experiment applies two RY rotations per iteration and scores
 * them against a uniform target. The error mitigation experiment applies a fixed 5 percent
 * over-rotation and reports an error estimate derived from the measurement expectation value. The
 * optimization experiment maximizes a two-qubit concurrence approximation. Success is judged by
 * the thresholds printed by each experiment, and no result is asserted.</p>
 */
public class MachineLearningQuantumControl {
    
    /**
     * Runs the three experiments and prints a summary of the controller history.
     *
     * <p>No command line arguments are read. The method prints per iteration values, best
     * fidelities or entanglement values, learning curves, controller insights, and model
     * parameters. Experiment failures are caught and printed with a stack trace.</p>
     *
     * @param args ignored
     * @throws Exception declared by the experiment methods; failures are caught inside the method
     */
    public static void main(String[] args) throws Exception {
        System.out.println("=== MACHINE LEARNING QUANTUM CONTROL (FIXED) ===");
        
        // 1. Create quantum system with adaptive control
        SimulationConfig config = new SimulationConfig();
        config.setNumQubits(2);
        config.setBackend(SimulationBackend.STATE_VECTOR);
        config.setClassicalControlEnabled(true);
        
        QuantumSimulator simulator = new QuantumSimulator();
        simulator.initialize(config);
        
        // 2. Set up advanced classical controller with ML capabilities
        AdaptiveControllerFixed adaptiveController = new AdaptiveControllerFixed();
        
        // 3. Run quantum control experiments
        try {
            runQuantumControlExperiment(simulator, adaptiveController, "StatePreparation");
            runQuantumControlExperiment(simulator, adaptiveController, "ErrorMitigation");
            runQuantumControlExperiment(simulator, adaptiveController, "ParameterOptimization");
            
            // 4. Analyze controller performance
            analyzeControllerPerformance(adaptiveController);
            
            System.out.println("\n=== ML Quantum Control Completed Successfully ===");
            
        } catch (Exception e) {
            System.err.println("Experiment failed: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    private static void runQuantumControlExperiment(QuantumSimulator simulator, 
                                                   AdaptiveControllerFixed controller,
                                                   String experimentType) throws Exception {
        System.out.println("\n=== Running Experiment: " + experimentType + " ===");
        
        simulator.reset();
        controller.reset();
        
        Qubit q0 = new Qubit(0, "q0");
        Qubit q1 = new Qubit(1, "q1");
        
        switch (experimentType) {
            case "StatePreparation":
                runStatePreparationExperiment(simulator, controller, q0, q1);
                break;
            case "ErrorMitigation":
                runErrorMitigationExperiment(simulator, controller, q0, q1);
                break;
            case "ParameterOptimization":
                runParameterOptimizationExperiment(simulator, controller, q0, q1);
                break;
            default:
                throw new IllegalArgumentException("Unknown experiment type: " + experimentType);
        }
        
        // Collect experiment data
        controller.recordExperiment(experimentType, simulator.getContext());
    }
    
    private static void runStatePreparationExperiment(QuantumSimulator simulator,
                                                     AdaptiveControllerFixed controller,
                                                     Qubit q0, Qubit q1) throws Exception {
        System.out.println("Goal: Prepare arbitrary quantum state with feedback");
        
        // Target state: α|00> + β|01> + γ|10> + δ|11>
        // Using equal superposition for simplicity
        double[] targetAmplitudes = {0.5, 0.5, 0.5, 0.5};
        normalizeVector(targetAmplitudes);
        
        // Store target in controller memory
        controller.storeInMemory("target_state", targetAmplitudes);
        
        // Initialize model parameters for this experiment
        controller.initializeModelParameters();
        
        // Initial random preparation
        Random random = new Random(42);
        double bestFidelity = 0.0;
        double[] bestParameters = null;
        
        for (int iteration = 0; iteration < 10; iteration++) {
            System.out.println("\nIteration " + iteration + ":");
            
            // Get parameters from controller or use random exploration
            double theta0, theta1;
            if (iteration == 0 || random.nextDouble() < controller.getExplorationRate()) {
                // Exploration: random parameters
                theta0 = random.nextDouble() * Math.PI;
                theta1 = random.nextDouble() * Math.PI;
            } else {
                // Exploitation: use controller's suggested parameters
                double[] params = controller.suggestParameters();
                theta0 = params[0];
                theta1 = params[1];
            }
            
            System.out.printf(Locale.ROOT, "  Using parameters: theta0=%.3f, theta1=%.3f\n", theta0, theta1);
            
            // Apply rotations
            QuantumEvent rot0 = createGateEvent("RY0_iter" + iteration,
                SingleQubitGate.createRY(theta0, q0), q0);
            
            QuantumEvent rot1 = createGateEvent("RY1_iter" + iteration,
                SingleQubitGate.createRY(theta1, q1), q1);
            
            // Schedule and run
            simulator.scheduleEvent(rot0);
            simulator.scheduleEvent(rot1);
            
            SimulationResult result = simulator.run();
            
            // Calculate fidelity with target
            double fidelity = calculateFidelity(result.getFinalState(), targetAmplitudes);
            System.out.printf(Locale.ROOT, "  Fidelity with target: %.4f\n", fidelity);
            
            // Update best result
            if (fidelity > bestFidelity) {
                bestFidelity = fidelity;
                bestParameters = new double[]{theta0, theta1};
                System.out.println("  New best fidelity!");
            }
            
            // Controller learns from this result
            controller.learnFromMeasurement(fidelity, new double[]{theta0, theta1});
            
            // Reset for next iteration
            simulator.reset();
            
            // If we reach good fidelity, stop
            if (fidelity > 0.95) {
                System.out.println("  Target state achieved!");
                break;
            }
        }
        
        System.out.println("\n=== State Preparation Results ===");
        System.out.printf(Locale.ROOT, "Best fidelity achieved: %.4f\n", bestFidelity);
        if (bestParameters != null) {
            System.out.printf(Locale.ROOT, "Best parameters: theta0=%.3f, theta1=%.3f\n", 
                bestParameters[0], bestParameters[1]);
        }
    }
    
    private static void runErrorMitigationExperiment(QuantumSimulator simulator,
                                                    AdaptiveControllerFixed controller,
                                                    Qubit q0, Qubit q1) throws Exception {
        System.out.println("\nGoal: Mitigate errors using machine learning");
        
        // Initialize for error mitigation
        controller.storeInMemory("experiment_type", "error_mitigation");
        controller.initializeModelParameters();
        
        // Simulate systematic gate errors
        double systematicError = 0.05; // 5% systematic over-rotation
        
        List<Double> estimatedErrors = new ArrayList<>();
        
        for (int circuitDepth = 1; circuitDepth <= 3; circuitDepth++) {
            System.out.println("\nCircuit depth: " + circuitDepth);
            
            // Create circuit with increasing depth
            for (int layer = 0; layer < circuitDepth; layer++) {
                // Apply gates with potential errors
                double intendedAngle = Math.PI / 4;
                double actualAngle = intendedAngle * (1 + systematicError);
                
                QuantumEvent gate1 = createGateEvent("RX_layer" + layer,
                    SingleQubitGate.createRX(actualAngle, q0), q0);
                
                QuantumEvent gate2 = createGateEvent("RY_layer" + layer,
                    SingleQubitGate.createRY(intendedAngle, q1), q1);
                
                simulator.scheduleEvent(gate1);
                simulator.scheduleEvent(gate2);
            }
            
            // Measure
            Observable measurementObservable = Observable.pauliZ(new Qubit[]{q0, q1});
            MeasurementOperator measure = new MeasurementOperator(measurementObservable, 1000);
            
            QuantumEvent measureEvent = createGateEvent("measure_depth" + circuitDepth,
                measure, q0, q1);
            
            simulator.scheduleEvent(measureEvent);
            
            SimulationResult result = simulator.run();
            
            if (!result.getMeasurements().isEmpty()) {
                MeasurementResult measurement = result.getMeasurements().get(0);
                
                // Controller analyzes error pattern
                double errorMagnitude = controller.analyzeErrorPattern(
                    measurement.getExpectationValue(), circuitDepth);
                
                System.out.printf(Locale.ROOT, "  Estimated error magnitude: %.4f\n", errorMagnitude);
                estimatedErrors.add(errorMagnitude);
                
                // Apply correction based on learned model
                double correction = controller.calculateErrorCorrection(circuitDepth);
                System.out.printf(Locale.ROOT, "  Suggested correction: %.4f\n", correction);
            }
            
            simulator.reset();
        }
        
        // Analyze error learning
        if (!estimatedErrors.isEmpty()) {
            double avgError = estimatedErrors.stream()
                .mapToDouble(Double::doubleValue)
                .average()
                .orElse(0.0);
            System.out.printf(Locale.ROOT, "\nAverage estimated error: %.4f (actual: %.4f)\n", 
                avgError, systematicError);
        }
    }
    
    private static void runParameterOptimizationExperiment(QuantumSimulator simulator,
                                                         AdaptiveControllerFixed controller,
                                                         Qubit q0, Qubit q1) throws Exception {
        System.out.println("\nGoal: Optimize quantum circuit parameters using ML");
        
        // Initialize for optimization
        controller.storeInMemory("experiment_type", "parameter_optimization");
        controller.initializeModelParameters();
        
        // Optimization target: maximize entanglement
        int maxIterations = 15;
        double bestEntanglement = 0.0;
        double[] bestParams = null;
        
        List<Double> entanglementHistory = new ArrayList<>();
        
        for (int iter = 0; iter < maxIterations; iter++) {
            // Get parameters from controller
            double[] params = controller.suggestParametersForOptimization(iter);
            double theta = params[0];
            double phi = params[1];
            
            System.out.printf(Locale.ROOT, "\nIteration %d: theta=%.3f, phi=%.3f\n", iter, theta, phi);
            
            // Apply parameterized circuit
            QuantumEvent ry0 = createGateEvent("RY_theta",
                SingleQubitGate.createRY(theta, q0), q0);
            
            QuantumEvent rz1 = createGateEvent("RZ_phi",
                SingleQubitGate.createRZ(phi, q1), q1);
            
            QuantumEvent cnot = createGateEvent("CNOT",
                MultiQubitGate.createCNOT(q0, q1), q0,q1);
            
            simulator.scheduleEvent(ry0);
            simulator.scheduleEvent(rz1);
            simulator.scheduleEvent(cnot);
            
            SimulationResult result = simulator.run();
            
            // Calculate entanglement measure (simplified)
            double entanglement = calculateEntanglement(result.getFinalState());
            System.out.printf(Locale.ROOT, "  Entanglement: %.4f\n", entanglement);
            
            entanglementHistory.add(entanglement);
            
            // Update controller with result
            controller.updateModel(params, entanglement);
            
            // Track best result
            if (entanglement > bestEntanglement) {
                bestEntanglement = entanglement;
                bestParams = params;
                System.out.println("  New best entanglement!");
            }
            
            simulator.reset();
            
            // Early stopping if good enough
            if (entanglement > 0.95) {
                System.out.println("  Optimal entanglement achieved!");
                break;
            }
        }
        
        System.out.println("\n=== Parameter Optimization Results ===");
        System.out.printf(Locale.ROOT, "Best entanglement: %.4f\n", bestEntanglement);
        if (bestParams != null) {
            System.out.printf(Locale.ROOT, "Best parameters: theta=%.3f, phi=%.3f\n", 
                bestParams[0], bestParams[1]);
        }
        
        // Show learning progress
        if (!entanglementHistory.isEmpty()) {
            System.out.println("\nLearning progress:");
            for (int i = 0; i < entanglementHistory.size(); i++) {
                System.out.printf(Locale.ROOT, "  Iter %2d: %.4f\n", i, entanglementHistory.get(i));
            }
            
            double improvement = (bestEntanglement - entanglementHistory.get(0)) / 
                entanglementHistory.get(0) * 100;
            System.out.printf(Locale.ROOT, "Overall improvement: %.1f%%\n", improvement);
        }
    }
    
    private static void analyzeControllerPerformance(AdaptiveControllerFixed controller) {
        System.out.println("\n=== CONTROLLER PERFORMANCE ANALYSIS ===");
        
        // Get learning curves
        Map<String, List<Double>> learningCurves = controller.getLearningCurves();
        
        for (Map.Entry<String, List<Double>> entry : learningCurves.entrySet()) {
            System.out.println("\nExperiment: " + entry.getKey());
            List<Double> curve = entry.getValue();
            
            if (!curve.isEmpty()) {
                double initial = curve.get(0);
                double finalVal = curve.get(curve.size() - 1);
                
                System.out.printf(Locale.ROOT, "  Samples: %d\n", curve.size());
                System.out.printf(Locale.ROOT, "  Min: %.4f, Max: %.4f\n",
                    Collections.min(curve), Collections.max(curve));
                System.out.printf(Locale.ROOT, "  Mean: %.4f, StdDev: %.4f\n",
                    calculateMean(curve), calculateStdDev(curve));
                
                if (initial != 0) {
                    double improvement = (finalVal - initial) / Math.abs(initial) * 100;
                    System.out.printf(Locale.ROOT, "  Improvement: %.1f%%\n", improvement);
                }
                
                // Simple ASCII progress visualization
                if (curve.size() > 1) {
                    System.out.print("  Trend: ");
                    int displayPoints = Math.min(curve.size(), 20);
                    for (int i = 0; i < displayPoints; i++) {
                        int idx = i * curve.size() / displayPoints;
                        double normalized = (curve.get(idx) - Collections.min(curve)) / 
                            (Collections.max(curve) - Collections.min(curve) + 0.001);
                        int barLength = (int) (normalized * 10);
                        System.out.print(barLength > 0 ? "#".repeat(barLength) : ".");
                    }
                    System.out.println();
                }
            }
        }
        
        // Get controller insights
        Map<String, Object> insights = controller.getInsights();
        System.out.println("\n=== CONTROLLER INSIGHTS ===");
        for (Map.Entry<String, Object> entry : insights.entrySet()) {
            System.out.printf(Locale.ROOT, "%-30s: %s\n", entry.getKey(), entry.getValue());
        }
        
        // Get model parameters
        Map<String, Object> modelParams = controller.getModelParameters();
        System.out.println("\n=== MODEL PARAMETERS ===");
        for (Map.Entry<String, Object> entry : modelParams.entrySet()) {
            Object value = entry.getValue();
            String display = value instanceof double[]
                ? Arrays.toString((double[]) value)
                : String.valueOf(value);
            System.out.printf(Locale.ROOT, "%-20s: %s\n", entry.getKey(), display);
        }
    }
    
    // Helper methods
    private static QuantumEvent createGateEvent(String name, QuantumOperation operation, Qubit... targets) {
        return new QuantumEvent.Builder(name)
            .withOperation(operation)
            .withTargetQubits(targets)
            .build();
    }
    
    private static void normalizeVector(double[] vector) {
        double norm = 0.0;
        for (double v : vector) {
            norm += v * v;
        }
        norm = Math.sqrt(norm);
        
        if (norm > 0) {
            for (int i = 0; i < vector.length; i++) {
                vector[i] /= norm;
            }
        }
    }
    
    private static double calculateFidelity(QuantumState state, double[] targetAmplitudes) {
        if (state instanceof StateVector) {
            StateVector sv = (StateVector) state;
            Complex[] amplitudes = sv.getAmplitudes();
            
            // Ensure arrays have same length
            if (amplitudes.length != targetAmplitudes.length) {
                return 0.0;
            }
            
            Complex overlap = Complex.ZERO;
            for (int i = 0; i < amplitudes.length; i++) {
                Complex targetComplex = new Complex(targetAmplitudes[i], 0);
                overlap = overlap.add(amplitudes[i].conjugate().multiply(targetComplex));
            }
            
            return Math.max(0.0, Math.min(1.0, overlap.absSquare()));
        }
        return 0.0;
    }
    
    private static double calculateEntanglement(QuantumState state) {
        // Simplified entanglement measure for 2-qubit states
        if (state instanceof StateVector) {
            StateVector sv = (StateVector) state;
            Complex[] amplitudes = sv.getAmplitudes();
            
            if (amplitudes.length == 4) {
                // Calculate concurrence approximation
                double c = 2 * Math.abs(amplitudes[0].multiply(amplitudes[3])
                    .subtract(amplitudes[1].multiply(amplitudes[2])).abs());
                return Math.max(0.0, Math.min(1.0, c));
            }
        }
        return 0.0;
    }
    
    private static double calculateMean(List<Double> values) {
        return values.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
    }
    
    private static double calculateStdDev(List<Double> values) {
        double mean = calculateMean(values);
        double variance = values.stream()
            .mapToDouble(x -> Math.pow(x - mean, 2))
            .average().orElse(0.0);
        return Math.sqrt(variance);
    }
    
    // Fixed Adaptive Controller
    private static class AdaptiveControllerFixed extends ClassicalController {
        private final Map<String, List<Double>> learningData;
        private final Map<String, Object> modelParameters;
        private final Random random;
        private double explorationRate;
        
        public AdaptiveControllerFixed() {
            super();
            this.learningData = new HashMap<>();
            this.modelParameters = new HashMap<>();
            this.random = new Random(42);
            this.explorationRate = 0.3;
            
            // Initialize with default model parameters
            initializeModelParameters();
        }
        
        public void initializeModelParameters() {
            // Set default model parameters
            modelParameters.put("learning_rate", 0.1);
            modelParameters.put("exploration_rate", explorationRate);
            modelParameters.put("exploration_decay", 0.95);
            modelParameters.put("model_type", "gradient_descent");
            modelParameters.put("best_fidelity", 0.0);
            modelParameters.put("best_entanglement", 0.0);
            modelParameters.put("total_iterations", 0);
            modelParameters.put("successful_iterations", 0);
            
            // Initialize gradient memory
            modelParameters.put("gradient_theta", 0.0);
            modelParameters.put("gradient_phi", 0.0);
            
            // Initialize parameter memory
            modelParameters.put("current_theta", 0.0);
            modelParameters.put("current_phi", 0.0);
            
            // Initialize experiment tracking
            storeInMemory("experiment_start_time", System.currentTimeMillis());
        }
        
        public void learnFromMeasurement(double reward, double[] parameters) {
            if (parameters == null || parameters.length == 0) {
                return;
            }
            
            String key = "reward_history";
            learningData.computeIfAbsent(key, k -> new ArrayList<>()).add(reward);
            
            // Store parameters with rewards
            for (int i = 0; i < parameters.length; i++) {
                String paramKey = "param_" + i + "_history";
                learningData.computeIfAbsent(paramKey, k -> new ArrayList<>()).add(parameters[i]);
            }
            
            // Update internal model
            updateModelInternal(reward, parameters);
            
            // Update statistics
            incrementCounter("total_iterations");
            if (reward > 0.9) {
                incrementCounter("successful_iterations");
            }
            
            // Update best results
            updateBestResults(reward);
        }
        
        public void recordExperiment(String experimentType, SimulationContext context) {
            String key = experimentType + "_data";
            List<Double> data = learningData.computeIfAbsent(key, k -> new ArrayList<>());
            
            // Record relevant metrics
            data.add((double) context.getStepsExecuted());
            data.add(context.getErrorManager().getCurrentFidelity());
            data.add(context.getElapsedTimeMillis() / 1000.0);
            
            // Store experiment metadata
            storeInMemory("last_experiment", experimentType);
            storeInMemory("last_experiment_time", System.currentTimeMillis());
        }
        
        public double analyzeErrorPattern(double measurementValue, int circuitDepth) {
            // Simple error analysis based on deviation from expected
            double expectedValue = 0.0; // Simplified expected value
            double error = Math.abs(measurementValue - expectedValue);
            
            // Store in learning data
            String key = "error_depth_" + circuitDepth;
            learningData.computeIfAbsent(key, k -> new ArrayList<>()).add(error);
            
            // Update error model
            updateErrorModel(circuitDepth, error);
            
            return error;
        }
        
        public double calculateErrorCorrection(int circuitDepth) {
            // Calculate correction based on learned patterns
            String key = "error_depth_" + circuitDepth;
            List<Double> errors = learningData.get(key);
            
            if (errors == null || errors.isEmpty()) {
                return 0.0;
            }
            
            // Weighted average of recent errors
            double recentWeight = 0.7;
            double correction = 0.0;
            int recentCount = Math.min(3, errors.size());
            
            for (int i = errors.size() - recentCount; i < errors.size(); i++) {
                double weight = recentWeight * Math.pow(0.5, errors.size() - 1 - i);
                correction -= errors.get(i) * weight;
            }
            
            // Clip correction to reasonable range
            return Math.max(-0.5, Math.min(0.5, correction));
        }
        
        public double[] suggestParameters() {
            // Suggest parameters for state preparation
            double theta = (Double) modelParameters.getOrDefault("current_theta", 0.0);
            double phi = (Double) modelParameters.getOrDefault("current_phi", 0.0);
            
            // Add some exploration noise
            if (random.nextDouble() < explorationRate) {
                theta += random.nextGaussian() * 0.1;
                phi += random.nextGaussian() * 0.1;
            }
            
            // Clip to valid ranges
            theta = clipToRange(theta, 0, Math.PI);
            phi = clipToRange(phi, 0, 2 * Math.PI);
            
            return new double[]{theta, phi};
        }
        
        public double[] suggestParametersForOptimization(int iteration) {
            // Different strategy for optimization
            if (iteration < 5) {
                // Initial exploration: random sampling
                return new double[]{
                    random.nextDouble() * Math.PI,
                    random.nextDouble() * 2 * Math.PI
                };
            } else {
                // Use gradient information
                double gradientTheta = (Double) modelParameters.getOrDefault("gradient_theta", 0.0);
                double gradientPhi = (Double) modelParameters.getOrDefault("gradient_phi", 0.0);
                double learningRate = (Double) modelParameters.getOrDefault("learning_rate", 0.1);
                
                double theta = (Double) modelParameters.getOrDefault("current_theta", Math.PI/4);
                double phi = (Double) modelParameters.getOrDefault("current_phi", Math.PI/2);
                
                // Gradient ascent
                theta += gradientTheta * learningRate;
                phi += gradientPhi * learningRate;
                
                // Add decreasing exploration noise
                double explorationNoise = 0.1 * Math.exp(-iteration / 10.0);
                theta += random.nextGaussian() * explorationNoise;
                phi += random.nextGaussian() * explorationNoise;
                
                // Clip to valid ranges
                theta = clipToRange(theta, 0, Math.PI);
                phi = clipToRange(phi, 0, 2 * Math.PI);
                
                return new double[]{theta, phi};
            }
        }
        
        public void updateModel(double[] parameters, double performance) {
            if (parameters == null || parameters.length < 2) {
                return;
            }
            
            // Store performance with parameters
            for (int i = 0; i < parameters.length; i++) {
                String key = "param_" + i + "_performance";
                List<Double> data = learningData.computeIfAbsent(key, k -> new ArrayList<>());
                data.add(performance);
            }
            
            // Update current parameters
            modelParameters.put("current_theta", parameters[0]);
            modelParameters.put("current_phi", parameters[1]);
            
            // Update gradient (simplified)
            double oldPerformance = (Double) modelParameters.getOrDefault("last_performance", 0.0);
            if (performance > oldPerformance) {
                // Positive gradient
                modelParameters.put("gradient_theta", 0.01);
                modelParameters.put("gradient_phi", 0.01);
            } else {
                // Negative gradient
                modelParameters.put("gradient_theta", -0.01);
                modelParameters.put("gradient_phi", -0.01);
            }
            
            modelParameters.put("last_performance", performance);
            
            // Store best parameters
            updateBestParameters(parameters, performance);
            
            // Decay exploration rate
            decayExplorationRate();
        }
        
        public Map<String, List<Double>> getLearningCurves() {
            // Return a copy of learning data
            Map<String, List<Double>> copy = new HashMap<>();
            for (Map.Entry<String, List<Double>> entry : learningData.entrySet()) {
                copy.put(entry.getKey(), new ArrayList<>(entry.getValue()));
            }
            return copy;
        }
        
        public Map<String, Object> getInsights() {
            Map<String, Object> insights = new HashMap<>();
            
            // Calculate various insights
            insights.put("total_data_points", getTotalDataPoints());
            insights.put("average_reward", calculateAverageReward());
            insights.put("exploration_rate", explorationRate);
            insights.put("model_converged", isModelConverged());
            insights.put("learning_progress", calculateLearningProgress());
            
            // Add parameter insights
            for (String key : learningData.keySet()) {
                if (key.contains("performance") || key.contains("reward")) {
                    List<Double> data = learningData.get(key);
                    if (!data.isEmpty()) {
                        insights.put(key + "_trend", calculateTrend(data));
                        insights.put(key + "_stability", calculateStability(data));
                    }
                }
            }
            
            return insights;
        }
        
        public Map<String, Object> getModelParameters() {
            return new HashMap<>(modelParameters);
        }
        
        public double getExplorationRate() {
            return explorationRate;
        }
        
        public void reset() {
            learningData.clear();
            modelParameters.clear();
            super.clearMemory();
            initializeModelParameters();
        }
        
        // Private helper methods
        private void updateModelInternal(double reward, double[] parameters) {
            // Safe access to model parameters with defaults
            double learningRate = (Double) modelParameters.getOrDefault("learning_rate", 0.1);
            
            // Simplified gradient update
            // In a real implementation, this would update a neural network or other model
            double currentTheta = (Double) modelParameters.getOrDefault("current_theta", 0.0);
            double currentPhi = (Double) modelParameters.getOrDefault("current_phi", 0.0);
            
            if (parameters.length >= 2) {
                // Update towards parameters that gave good reward
                double deltaTheta = parameters[0] - currentTheta;
                double deltaPhi = parameters[1] - currentPhi;
                
                modelParameters.put("current_theta", 
                    currentTheta + deltaTheta * learningRate * reward);
                modelParameters.put("current_phi", 
                    currentPhi + deltaPhi * learningRate * reward);
            }
        }
        
        private void updateErrorModel(int depth, double error) {
            String key = "error_model_depth_" + depth;
            List<Double> model = learningData.computeIfAbsent(key, k -> new ArrayList<>());
            model.add(error);
            
            // Keep only recent errors
            if (model.size() > 10) {
                model.remove(0);
            }
        }
        
        private void updateBestResults(double reward) {
            double bestFidelity = (Double) modelParameters.getOrDefault("best_fidelity", 0.0);
            if (reward > bestFidelity) {
                modelParameters.put("best_fidelity", reward);
                modelParameters.put("best_fidelity_time", System.currentTimeMillis());
            }
        }
        
        private void updateBestParameters(double[] parameters, double performance) {
            double bestPerformance = (Double) modelParameters.getOrDefault("best_performance", 0.0);
            
            if (performance > bestPerformance) {
                modelParameters.put("best_performance", performance);
                modelParameters.put("best_parameters", Arrays.copyOf(parameters, parameters.length));
                modelParameters.put("best_parameters_time", System.currentTimeMillis());
            }
        }
        
        private void decayExplorationRate() {
            double decay = (Double) modelParameters.getOrDefault("exploration_decay", 0.95);
            explorationRate *= decay;
            explorationRate = Math.max(0.01, explorationRate); // Minimum exploration
            modelParameters.put("exploration_rate", explorationRate);
        }
        
        private void incrementCounter(String counterName) {
            int count = (Integer) modelParameters.getOrDefault(counterName, 0);
            modelParameters.put(counterName, count + 1);
        }
        
        private double clipToRange(double value, double min, double max) {
            return Math.max(min, Math.min(max, value));
        }
        
        private int getTotalDataPoints() {
            return learningData.values().stream()
                .mapToInt(List::size)
                .sum();
        }
        
        private double calculateAverageReward() {
            List<Double> rewards = learningData.get("reward_history");
            if (rewards == null || rewards.isEmpty()) {
                return 0.0;
            }
            return rewards.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
        }
        
        private boolean isModelConverged() {
            List<Double> rewards = learningData.get("reward_history");
            if (rewards == null || rewards.size() < 10) {
                return false;
            }
            
            // Check if last 5 rewards are stable
            int recentCount = Math.min(5, rewards.size());
            List<Double> recent = rewards.subList(rewards.size() - recentCount, rewards.size());
            
            double mean = calculateMean(recent);
            double stdDev = calculateStdDev(recent);
            
            return stdDev / (mean + 0.001) < 0.1; // Less than 10% variation
        }
        
        private double calculateLearningProgress() {
            List<Double> rewards = learningData.get("reward_history");
            if (rewards == null || rewards.size() < 2) {
                return 0.0;
            }
            
            double initial = rewards.get(0);
            double latest = rewards.get(rewards.size() - 1);
            
            if (Math.abs(initial) < 0.001) {
                return 0.0;
            }
            
            return (latest - initial) / Math.abs(initial);
        }
        
        private double calculateTrend(List<Double> data) {
            if (data.size() < 2) {
                return 0.0;
            }
            
            // Simple linear trend
            double sumX = 0, sumY = 0, sumXY = 0, sumX2 = 0;
            int n = data.size();
            
            for (int i = 0; i < n; i++) {
                sumX += i;
                sumY += data.get(i);
                sumXY += i * data.get(i);
                sumX2 += i * i;
            }
            
            double slope = (n * sumXY - sumX * sumY) / (n * sumX2 - sumX * sumX);
            return slope;
        }
        
        private double calculateStability(List<Double> data) {
            if (data.size() < 2) {
                return 1.0;
            }
            
            double mean = calculateMean(data);
            double stdDev = calculateStdDev(data);
            
            if (Math.abs(mean) < 0.001) {
                return 1.0;
            }
            
            return 1.0 - (stdDev / Math.abs(mean));
        }
    }
}