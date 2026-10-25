package com.quantum.simulator.plugin;

import com.quantum.simulator.core.SimulationContext;
import com.quantum.simulator.operations.QuantumOperation;
import com.quantum.simulator.state.QuantumState;
import com.quantum.simulator.events.QuantumEvent;

public interface Plugin {
    void initialize(PluginContext context);
    
    void cleanup();
    
    String getName();
    
    String getVersion();
    
    String getDescription();
    
    default void onSimulationStart(SimulationContext context) {}
    
    default void onSimulationEnd(SimulationContext context) {}
    
    default void onStateUpdate(QuantumState state, QuantumEvent event) {}
    
    default void onEventScheduled(QuantumEvent event) {}
    
    default void onOperationApplied(QuantumOperation operation) {}
    
    default void onError(String errorMessage, Throwable error) {}
    
    default boolean supportsExtension(String extensionPoint) {
        return false;
    }
    
    default Object getExtension(String extensionPoint) {
        return null;
    }
 
}

