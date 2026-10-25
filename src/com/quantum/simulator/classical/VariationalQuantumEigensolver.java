
package com.quantum.simulator.classical;

import com.quantum.simulator.core.QuantumSimulator;
import com.quantum.simulator.core.SimulationContext;
import com.quantum.simulator.core.SimulationError;
import com.quantum.simulator.core.SimulationResult;
import com.quantum.simulator.events.QuantumEvent;
import com.quantum.simulator.operations.MultiQubitGate;
import com.quantum.simulator.operations.QuantumOperation;
import com.quantum.simulator.operations.SingleQubitGate;
import com.quantum.simulator.measurement.MeasurementResult;
import com.quantum.simulator.measurement.Observable;
import com.quantum.simulator.state.Qubit;
import com.quantum.simulator.math.ComplexMatrix;
import java.time.Instant;
import java.util.*;

/**
 * Variational Quantum Eigensolver that runs its ansatz on the event driven simulator
 * and minimizes the expectation value of a Hamiltonian observable.
 *
 * <p>The ansatz has a fixed number of layers, two in this implementation. Each layer
 * applies {@code RY(theta)} followed by {@code RZ(phi)} to every qubit, and then, for
 * every adjacent pair, a {@code CNOT} from the first to the second qubit, an
 * {@code RZ(entangle)} on the second qubit and a second {@code CNOT}. Construction adds
 * a bounded parameter in {@code [-pi, pi]} for every angle, with initial values drawn
 * uniformly from {@code [-pi/2, pi/2)} by a generator seeded with {@code 42}, so the
 * starting point is reproducible for the same construction order.
 *
 * <p>Each {@link #executeIteration()} call records the current parameters, builds the
 * circuit, executes it on the simulator, evaluates the Hamiltonian expectation with
 * {@link Observable#measure(com.quantum.simulator.state.QuantumState)} and stores that
 * energy as the cost. Unless convergence is detected, the gradient of every parameter is
 * then estimated by central finite differences with a step of {@code 1e-3}, which costs
 * two extra circuit executions per parameter, and one optimizer update is applied. The
 * default optimizer is gradient descent with momentum, and proposed values are clipped
 * to the parameter bounds.
 *
 * <p>Convergence is reported when at least two costs exist and the last two differ by
 * less than the configured threshold, or when the iteration limit is reached.
 */
public class VariationalQuantumEigensolver implements HybridAlgorithm {
    private static final int ANSATZ_LAYERS = 2;
    
    private QuantumSimulator simulator;
    private ParameterSpace parameterSpace;
    private Optimizer optimizer;
    private Observable hamiltonian;
    private List<Qubit> qubits;
    private Map<String, Object> parameters;
    private List<Double> costHistory;
    private List<Map<String, Double>> parameterHistory;
    private int iterationCount;
    private double currentCost;
    private double convergenceThreshold;
    private int maxIterations;
    private boolean converged;
    private final Random initialParameterRandom = new Random(42);
    
    /**
     * Creates a solver for a Hamiltonian over a fixed set of qubits.
     *
     * <p>The ansatz parameter space, with random initial angles, and the default
     * gradient descent optimizer are created here. The simulator is supplied later
     * through {@link #initialize(QuantumSimulator, Map)}.
     *
     * @param hamiltonian observable whose expectation value is minimized
     * @param qubits qubits the ansatz acts on; the list is copied
     */
    public VariationalQuantumEigensolver(Observable hamiltonian, List<Qubit> qubits) {
        this.hamiltonian = hamiltonian;
        this.qubits = new ArrayList<>(qubits);
        this.parameters = new HashMap<>();
        this.costHistory = new ArrayList<>();
        this.parameterHistory = new ArrayList<>();
        this.iterationCount = 0;
        this.currentCost = Double.MAX_VALUE;
        this.convergenceThreshold = 1e-6;
        this.maxIterations = 100;
        this.converged = false;
        
        // Initialize parameter space
        this.parameterSpace = new ParameterSpace();
        initializeParameterSpace();
        
        // Initialize optimizer
        this.optimizer = new GradientDescentOptimizer();
    }
    
    /**
     * Binds the solver to a simulator and applies configuration parameters.
     *
     * <p>The supplied parameters are merged into the current parameter map, so values
     * from an earlier call remain unless they are overwritten.
     * {@code convergence_threshold} (default {@code 1e-6}) and {@code max_iterations}
     * (default {@code 100}) are read directly, and the optimizer additionally reads
     * {@code learning_rate} (default {@code 0.1}) and {@code momentum} (default
     * {@code 0.9}). The values are cast to {@code double} and {@code int}, so callers
     * must supply {@link Double} and {@link Integer} instances for those keys.
     *
     * <p>The qubit count is validated only when the simulator already has a context.
     * The method finishes by calling {@link #reset()}, which discards any previous
     * iteration state.
     *
     * @param simulator simulator used to execute ansatz circuits
     * @param parameters configuration values, may be {@code null}
     * @throws IllegalArgumentException if the simulator context exists and its qubit
     *                                  count differs from the number of qubits supplied
     *                                  at construction
     * @throws ClassCastException if a configuration value has the wrong type
     */
    @Override
    public void initialize(QuantumSimulator simulator, Map<String, Object> parameters) {
        this.simulator = simulator;
        if (parameters != null) {
            this.parameters.putAll(parameters);
        }
        if (simulator != null && simulator.getContext() != null
            && simulator.getContext().getConfig().getNumQubits() != qubits.size()) {
            throw new IllegalArgumentException(
                "Simulator has " + simulator.getContext().getConfig().getNumQubits()
                    + " qubits but VQE was constructed for " + qubits.size());
        }
        
        // Set algorithm parameters
        this.convergenceThreshold = (double) this.parameters.getOrDefault("convergence_threshold", 1e-6);
        this.maxIterations = (int) this.parameters.getOrDefault("max_iterations", 100);
        
        // Initialize optimizer with parameters
        optimizer.initialize(this.parameters);
        
        // Reset state
        reset();
    }
    
    /**
     * Performs one VQE iteration.
     *
     * <p>The iteration is skipped when the solver has already converged or the iteration
     * limit has been reached. Otherwise the iteration counter is incremented, the current
     * parameters are snapshotted into the parameter history, the ansatz is executed and
     * the measured energy is appended to the cost history and becomes the current cost.
     * Convergence is then checked. When the solver has not converged, the gradient is
     * estimated for every current parameter and one optimizer update is applied, with
     * the result clipped to the parameter bounds.
     *
     * @return {@code true} when another iteration should follow, {@code false} when the
     *         solver converged, the iteration limit was reached, or no iteration was
     *         possible
     * @throws IllegalStateException if no simulator has been set or circuit execution
     *                               fails
     */
    @Override
    public boolean executeIteration() {
        if (converged || iterationCount >= maxIterations) {
            return false;
        }
        
        iterationCount++;
        
        // Get current parameters
        Map<String, Double> currentParams = parameterSpace.getCurrentParameters();
        parameterHistory.add(new HashMap<>(currentParams));
        
        // Prepare parameterized quantum circuit
        List<QuantumOperation> circuit = prepareParameterizedCircuit(currentParams);
        
        // Execute circuit and measure Hamiltonian
        double energy = measureHamiltonian(circuit);
        
        // Update cost (energy is the cost)
        costHistory.add(energy);
        currentCost = energy;
        
        // Check convergence
        converged = checkConvergence();
        
        if (!converged) {
            // Optimize parameters
            Map<String, Double> gradient = computeGradient(currentParams);
            Map<String, Double> newParams = optimizer.updateParameters(currentParams, gradient, energy);
            parameterSpace.updateParameters(newParams);
        }
        
        return !converged;
    }
    
    /**
     * Executes the ansatz with the current parameters and returns the simulation result.
     *
     * <p>The result carries additional data under {@code algorithm},
     * {@code optimal_energy}, {@code optimal_parameters}, {@code iterations},
     * {@code converged} and {@code cost_history}. The energy and parameters are the
     * current ones and are labelled optimal even when the solver has not converged.
     *
     * @return simulation result of the current circuit execution
     * @throws IllegalStateException if no simulator has been set or circuit execution
     *                               fails
     */
    @Override
    public SimulationResult getResult() {
        SimulationResult result = executeCircuit(
            prepareParameterizedCircuit(parameterSpace.getCurrentParameters()));
        result.putAdditionalData("algorithm", getName());
        result.putAdditionalData("optimal_energy", currentCost);
        result.putAdditionalData("optimal_parameters", parameterSpace.getCurrentParameters());
        result.putAdditionalData("iterations", iterationCount);
        result.putAdditionalData("converged", converged);
        result.putAdditionalData("cost_history", new ArrayList<>(costHistory));
        return result;
    }
    
    /**
     * Returns the cost of the most recent iteration.
     *
     * @return current energy, or {@link Double#MAX_VALUE} before the first iteration and
     *         after {@link #reset()}
     */
    @Override
    public double getCurrentCost() {
        return currentCost;
    }
    
    /**
     * Returns a copy of the configuration parameters.
     *
     * @return copy of the parameter map
     */
    @Override
    public Map<String, Object> getParameters() {
        return new HashMap<>(parameters);
    }
    
    /**
     * Sets a configuration parameter.
     *
     * <p>The value is only read by {@link #initialize(QuantumSimulator, Map)}, so a
     * change made after initialization takes effect at the next initialization.
     *
     * @param name parameter name
     * @param value parameter value
     */
    @Override
    public void setParameter(String name, Object value) {
        parameters.put(name, value);
    }
    
    /**
     * Reports whether the convergence criterion has been met.
     *
     * @return {@code true} when the solver has converged
     */
    @Override
    public boolean isConverged() {
        return converged;
    }
    
    /**
     * Returns the number of iterations performed since the last reset.
     *
     * @return iteration count
     */
    @Override
    public int getIterationCount() {
        return iterationCount;
    }
    
    /**
     * Clears histories and returns the solver to its initial state.
     *
     * <p>The cost history, parameter history and iteration count are cleared, the
     * current cost is set to {@link Double#MAX_VALUE}, the convergence flag is cleared,
     * the ansatz parameters return to their initial angles and the optimizer clears its
     * velocity. The simulator reference and the configuration parameters are kept.
     */
    @Override
    public void reset() {
        costHistory.clear();
        parameterHistory.clear();
        iterationCount = 0;
        currentCost = Double.MAX_VALUE;
        converged = false;
        parameterSpace.reset();
        optimizer.reset();
    }
    
    /**
     * Returns the algorithm name.
     *
     * @return the literal {@code VariationalQuantumEigensolver}
     */
    @Override
    public String getName() {
        return "VariationalQuantumEigensolver";
    }
    
    /**
     * Builds the ansatz circuit for the current parameters without executing it.
     *
     * @return new list of operations for one ansatz evaluation
     */
    @Override
    public List<QuantumOperation> getNextOperations() {
        // Return the next set of operations based on current parameters
        return prepareParameterizedCircuit(parameterSpace.getCurrentParameters());
    }
    
    /**
     * Returns the most recent energy.
     *
     * @return the same value as {@link #getCurrentCost()}
     */
    public double getEnergy() {
        return currentCost;
    }
    
    /**
     * Returns the current ansatz parameters.
     *
     * <p>The values describe the latest optimizer state and are not necessarily an
     * optimum.
     *
     * @return copy of the current parameter values
     */
    public Map<String, Double> getOptimalParameters() {
        return parameterSpace.getCurrentParameters();
    }
    
    /**
     * Returns the energy recorded in each completed iteration.
     *
     * @return copy of the cost history, oldest first
     */
    public List<Double> getCostHistory() {
        return new ArrayList<>(costHistory);
    }
    
    /**
     * Returns the parameter values recorded at the start of each completed iteration.
     *
     * <p>The returned list is a copy, but the snapshot maps inside it are shared with the
     * history.
     *
     * @return list of parameter snapshots, oldest first
     */
    public List<Map<String, Double>> getParameterHistory() {
        return new ArrayList<>(parameterHistory);
    }
    
    private void initializeParameterSpace() {
        for (int layer = 0; layer < ANSATZ_LAYERS; layer++) {
            for (int i = 0; i < qubits.size(); i++) {
                parameterSpace.addParameter(
                    "theta_" + layer + "_" + i, randomInitialValue(), -Math.PI, Math.PI);
                parameterSpace.addParameter(
                    "phi_" + layer + "_" + i, randomInitialValue(), -Math.PI, Math.PI);
            }
            for (int i = 0; i < qubits.size() - 1; i++) {
                parameterSpace.addParameter(
                    "entangle_" + layer + "_" + i + "_" + (i + 1),
                    randomInitialValue(), -Math.PI, Math.PI);
            }
        }
    }
    
    private double randomInitialValue() {
        return (initialParameterRandom.nextDouble() - 0.5) * Math.PI;
    }
    
    private List<QuantumOperation> prepareParameterizedCircuit(Map<String, Double> params) {
        List<QuantumOperation> circuit = new ArrayList<>();
        
        for (int layer = 0; layer < ANSATZ_LAYERS; layer++) {
            for (int i = 0; i < qubits.size(); i++) {
                Qubit qubit = qubits.get(i);
                double theta = params.getOrDefault("theta_" + layer + "_" + i, 0.0);
                double phi = params.getOrDefault("phi_" + layer + "_" + i, 0.0);
                
                circuit.add(SingleQubitGate.createRY(theta, qubit));
                circuit.add(SingleQubitGate.createRZ(phi, qubit));
            }
            
            for (int i = 0; i < qubits.size() - 1; i++) {
                Qubit first = qubits.get(i);
                Qubit second = qubits.get(i + 1);
                double entangle = params.getOrDefault(
                    "entangle_" + layer + "_" + i + "_" + (i + 1), 0.0);
                
                circuit.add(MultiQubitGate.createCNOT(first, second));
                circuit.add(SingleQubitGate.createRZ(entangle, second));
                circuit.add(MultiQubitGate.createCNOT(first, second));
            }
        }
        
        return circuit;
    }
    
    private double measureHamiltonian(List<QuantumOperation> circuit) {
        SimulationResult result = executeCircuit(circuit);
        return hamiltonian.measure(result.getFinalState());
    }
    
    private SimulationResult executeCircuit(List<QuantumOperation> circuit) {
        if (simulator == null) {
            throw new IllegalStateException("VQE has not been initialized with a simulator");
        }
        
        simulator.reset();
        Instant time = Instant.now();
        int index = 0;
        
        try {
            for (QuantumOperation operation : circuit) {
                QuantumEvent event = new QuantumEvent.Builder("vqe_" + (index++))
                    .withScheduledTime(time)
                    .withOperation(operation)
                    .withTargetQubits(operation.getTargetQubits())
                    .build();
                simulator.scheduleEvent(event);
                time = time.plusMillis(1);
            }
            return simulator.run();
        } catch (SimulationError e) {
            throw new IllegalStateException("VQE circuit execution failed: " + e.getMessage(), e);
        }
    }
    
    private Map<String, Double> computeGradient(Map<String, Double> params) {
        // Compute gradient using parameter shift rule or finite differences
        Map<String, Double> gradient = new HashMap<>();
        double epsilon = 1e-3;
        
        for (Map.Entry<String, Double> entry : params.entrySet()) {
            String paramName = entry.getKey();
            double originalValue = entry.getValue();
            
            // Forward finite difference
            Map<String, Double> forwardParams = new HashMap<>(params);
            forwardParams.put(paramName, originalValue + epsilon);
            
            double forwardEnergy = measureHamiltonian(prepareParameterizedCircuit(forwardParams));
            
            // Backward finite difference
            Map<String, Double> backwardParams = new HashMap<>(params);
            backwardParams.put(paramName, originalValue - epsilon);
            
            double backwardEnergy = measureHamiltonian(prepareParameterizedCircuit(backwardParams));
            
            // Compute gradient
            double grad = (forwardEnergy - backwardEnergy) / (2 * epsilon);
            gradient.put(paramName, grad);
        }
        
        return gradient;
    }
    
    private boolean checkConvergence() {
        if (iterationCount < 2) {
            return false;
        }
        
        // Check if cost has converged
        double lastCost = costHistory.get(costHistory.size() - 1);
        double prevCost = costHistory.get(costHistory.size() - 2);
        
        if (Math.abs(lastCost - prevCost) < convergenceThreshold) {
            return true;
        }
        
        // Check if maximum iterations reached
        if (iterationCount >= maxIterations) {
            return true;
        }
        
        return false;
    }
    
    // Inner class for parameter space management
    private static class ParameterSpace {
        private final Map<String, Parameter> parameters;
        private final Map<String, Double> currentValues;
        
        /**
         * Creates an empty parameter table.
         */
        public ParameterSpace() {
            this.parameters = new HashMap<>();
            this.currentValues = new HashMap<>();
        }
        
        /**
         * Adds a bounded parameter and sets its current value to the initial value.
         *
         * @param name parameter name
         * @param initialValue starting value
         * @param min lower bound, inclusive
         * @param max upper bound, inclusive
         */
        public void addParameter(String name, double initialValue, double min, double max) {
            parameters.put(name, new Parameter(name, initialValue, min, max));
            currentValues.put(name, initialValue);
        }
        
        /**
         * Returns a copy of the current values.
         *
         * @return map from parameter names to current values
         */
        public Map<String, Double> getCurrentParameters() {
            return new HashMap<>(currentValues);
        }
        
        /**
         * Stores new values, clipping each one to its bounds.
         *
         * <p>Names that are not present in this table are ignored.
         *
         * @param newValues proposed values
         */
        public void updateParameters(Map<String, Double> newValues) {
            for (Map.Entry<String, Double> entry : newValues.entrySet()) {
                String name = entry.getKey();
                Double value = entry.getValue();
                
                if (parameters.containsKey(name)) {
                    Parameter param = parameters.get(name);
                    // Clip value to bounds
                    double clipped = Math.max(param.min, Math.min(param.max, value));
                    currentValues.put(name, clipped);
                }
            }
        }
        
        /**
         * Restores the initial value of every parameter.
         */
        public void reset() {
            for (Map.Entry<String, Parameter> entry : parameters.entrySet()) {
                currentValues.put(entry.getKey(), entry.getValue().initialValue);
            }
        }
        
        private static class Parameter {
            final String name;
            final double initialValue;
            final double min;
            final double max;
            
            Parameter(String name, double initialValue, double min, double max) {
                this.name = name;
                this.initialValue = initialValue;
                this.min = min;
                this.max = max;
            }
        }
    }
    
    // Interface for optimizers
    /**
     * Strategy that turns a gradient estimate into a new parameter set.
     *
     * <p>An implementation is initialized with the algorithm configuration, receives one
     * update per non-converged iteration, and can be reset between runs. The solver
     * clips the returned values to the parameter bounds and ignores names it does not
     * know.
     */
    public interface Optimizer {
        /**
         * Applies configuration parameters to the optimizer.
         *
         * @param parameters configuration values
         */
        void initialize(Map<String, Object> parameters);
        /**
         * Computes the next parameter values.
         *
         * @param currentParams current parameter values
         * @param gradient estimated derivative for each parameter
         * @param cost cost of the current parameters
         * @return proposed parameter values
         */
        Map<String, Double> updateParameters(Map<String, Double> currentParams, 
                                           Map<String, Double> gradient, double cost);
        /**
         * Clears any optimizer state accumulated across iterations.
         */
        void reset();
    }
    
    // Simple gradient descent optimizer
    private static class GradientDescentOptimizer implements Optimizer {
        private double learningRate;
        private double momentum;
        private Map<String, Double> velocity;
        
        /**
         * Creates an optimizer with learning rate {@code 0.1} and momentum
         * {@code 0.9}.
         */
        public GradientDescentOptimizer() {
            this.learningRate = 0.1;
            this.momentum = 0.9;
            this.velocity = new HashMap<>();
        }
        
        /**
         * Reads the learning rate and momentum from the configuration.
         *
         * <p>The keys {@code learning_rate} and {@code momentum} default to {@code 0.1}
         * and {@code 0.9}; their values must be {@link Double} instances. The stored
         * velocity is cleared.
         *
         * @param parameters configuration values
         */
        @Override
        public void initialize(Map<String, Object> parameters) {
            this.learningRate = (double) parameters.getOrDefault("learning_rate", 0.1);
            this.momentum = (double) parameters.getOrDefault("momentum", 0.9);
            velocity.clear();
        }
        
        /**
         * Applies one gradient descent step with momentum.
         *
         * <p>For every current parameter the velocity is updated to
         * {@code momentum * velocity - learningRate * gradient} and the proposed value is
         * {@code current + velocity}. A missing gradient entry counts as zero.
         *
         * @param currentParams current parameter values
         * @param gradient estimated derivative for each parameter
         * @param cost current cost, not used by this optimizer
         * @return proposed parameter values
         */
        @Override
        public Map<String, Double> updateParameters(Map<String, Double> currentParams, 
                                                  Map<String, Double> gradient, double cost) {
            Map<String, Double> newParams = new HashMap<>();
            
            for (String paramName : currentParams.keySet()) {
                double currentValue = currentParams.get(paramName);
                double grad = gradient.getOrDefault(paramName, 0.0);
                
                // Update velocity (momentum)
                double currentVelocity = velocity.getOrDefault(paramName, 0.0);
                double newVelocity = momentum * currentVelocity - learningRate * grad;
                velocity.put(paramName, newVelocity);
                
                // Update parameter
                double newValue = currentValue + newVelocity;
                newParams.put(paramName, newValue);
            }
            
            return newParams;
        }
        
        /**
         * Clears the stored velocity.
         */
        @Override
        public void reset() {
            velocity.clear();
        }
    }
}