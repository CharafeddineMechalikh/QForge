package com.quantum.simulator.classical;

import com.quantum.simulator.core.QuantumSimulator;
import com.quantum.simulator.core.SimulationContext;
import com.quantum.simulator.core.SimulationResult;
import com.quantum.simulator.operations.QuantumOperation;
import com.quantum.simulator.state.Qubit;
import java.util.*;

/**
 * Contract for algorithms that alternate quantum circuit execution with classical
 * update steps.
 *
 * <p>A driver calls {@link #initialize(QuantumSimulator, Map)} once, then calls
 * {@link #executeIteration()} repeatedly. Each iteration performs one unit of work on
 * the simulator and reports whether another iteration should follow. The current cost
 * and the convergence flag describe the state after the last iteration, and
 * {@link #getResult()} exposes the current simulation output.
 * {@link #getNextOperations()} lets a driver obtain the operations that would run next
 * without executing them.
 *
 * <p>The interface does not prescribe how parameters are typed or how the cost is
 * computed; implementations define those details.
 */
public interface HybridAlgorithm {
    /**
     * Prepares the algorithm and merges the supplied configuration parameters.
     *
     * @param simulator simulator used to execute circuits
     * @param parameters configuration values, may be {@code null}
     */
    void initialize(QuantumSimulator simulator, Map<String, Object> parameters);
    
    /**
     * Performs one iteration of the algorithm.
     *
     * @return {@code true} when another iteration should follow, {@code false} when the
     *         algorithm is finished or no work was possible
     */
    boolean executeIteration();
    
    /**
     * Returns the simulation result of the current algorithm state.
     *
     * @return simulation result
     */
    SimulationResult getResult();
    
    /**
     * Returns the cost of the most recent iteration.
     *
     * @return current cost
     */
    double getCurrentCost();
    
    /**
     * Returns the algorithm configuration parameters.
     *
     * @return parameter map
     */
    Map<String, Object> getParameters();
    
    /**
     * Sets or replaces one configuration parameter.
     *
     * @param name parameter name
     * @param value parameter value
     */
    void setParameter(String name, Object value);
    
    /**
     * Reports whether the algorithm has converged.
     *
     * @return {@code true} when the convergence criterion has been met
     */
    boolean isConverged();
    
    /**
     * Returns the number of iterations performed since the last reset.
     *
     * @return iteration count
     */
    int getIterationCount();
    
    /**
     * Returns the algorithm to its initial state so it can run again.
     */
    void reset();
    
    /**
     * Returns the algorithm name.
     *
     * @return algorithm name
     */
    String getName();
    
    /**
     * Returns the operations that would be executed next.
     *
     * <p>The default implementation returns an empty list.
     *
     * @return list of operations, empty when the algorithm does not expose them
     */
    default List<QuantumOperation> getNextOperations() {
        return Collections.emptyList();
    }
}

 