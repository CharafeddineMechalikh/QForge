package com.quantum.simulator.classical;

import com.quantum.simulator.core.QuantumSimulator;
import com.quantum.simulator.core.SimulationContext;
import com.quantum.simulator.core.SimulationResult;
import com.quantum.simulator.operations.QuantumOperation;
import com.quantum.simulator.state.Qubit;
import java.util.*;

public interface HybridAlgorithm {
    void initialize(QuantumSimulator simulator, Map<String, Object> parameters);
    
    boolean executeIteration();
    
    SimulationResult getResult();
    
    double getCurrentCost();
    
    Map<String, Object> getParameters();
    
    void setParameter(String name, Object value);
    
    boolean isConverged();
    
    int getIterationCount();
    
    void reset();
    
    String getName();
    
    default List<QuantumOperation> getNextOperations() {
        return Collections.emptyList();
    }
}

 