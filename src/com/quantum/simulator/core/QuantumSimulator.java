package com.quantum.simulator.core;

import com.quantum.simulator.config.SimulationBackend;
import com.quantum.simulator.config.SimulationConfig;
import com.quantum.simulator.events.EventDispatcher;
import com.quantum.simulator.events.QuantumEvent;
import com.quantum.simulator.observer.SimulationObserver;
import com.quantum.simulator.measurement.MeasurementResult;
import com.quantum.simulator.resources.ResourceManager;
import com.quantum.simulator.plugin.PluginManager;
import com.quantum.simulator.strategy.DensityMatrixStrategy;
import com.quantum.simulator.strategy.SimulationStrategy;
import com.quantum.simulator.strategy.StateVectorStrategy;
import com.quantum.simulator.classical.ClassicalController;
import java.util.List;
import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicBoolean;

public class QuantumSimulator {
    private SimulationContext context;
    private EventDispatcher eventDispatcher;
    private ResourceManager resourceManager;
    private PluginManager pluginManager;
    private ClassicalController classicalController;
    private List<SimulationObserver> observers;
    private AtomicBoolean isRunning;
    private long simulationStartTime;
    
    public QuantumSimulator() {
        this.observers = new ArrayList<>();
        this.isRunning = new AtomicBoolean(false);
    }
    
    public void initialize(SimulationConfig config) throws SimulationError {
        if (isRunning.get()) {
            throw new SimulationError("Simulator is already running");
        }
        
        try {
            // Validate configuration
            if (!config.validate()) {
                throw new SimulationError("Invalid simulation configuration");
            }
            
            // Create context
            this.context = new SimulationContext(config);
            
            // Initialize components
            this.eventDispatcher = new EventDispatcher();
            this.resourceManager = new ResourceManager(config.getResourceConstraints());
            if (this.pluginManager == null) {
                this.pluginManager = new PluginManager();
            }
            this.classicalController = config.isClassicalControlEnabled() ? 
                new ClassicalController() : null;
            if (this.classicalController != null) {
                this.classicalController.setEventScheduler(event -> {
                    try {
                        scheduleEvent(event);
                    } catch (SimulationError e) {
                        throw new IllegalStateException(
                            "Classical controller failed to schedule event", e);
                    }
                });
            }
            
            // Initialize strategy based on backend
            SimulationStrategy strategy = createStrategy(config.getBackend());
            context.setStrategy(strategy);
            
            // Initialize quantum state
            context.initializeState();
            context.getMetrics().onInitialized(context);
            
            // Notify observers
            notifyObserversInitialized();
            
        } catch (Exception e) {
            throw new SimulationError("Failed to initialize simulator: " + e.getMessage(), e);
        }
    }
    
    public SimulationResult run() throws SimulationError {
        if (context == null) {
            throw new SimulationError("Simulator not initialized. Call initialize() first.");
        }
        
        isRunning.set(true);
        simulationStartTime = System.currentTimeMillis();
        
        if (pluginManager != null) {
            pluginManager.initializePlugins(context);
        }
        
        try {
            // Execute events until queue is empty or stop condition is met
            while (!eventDispatcher.isQueueEmpty() && isRunning.get()) {
                int measurementCountBefore = context.getMeasurements().size();
                QuantumEvent event = eventDispatcher.dispatchNextEvent(context);
                if (event != null) {
                    notifyObserversStepped(event);
                }
                notifyObserversMeasurements(measurementCountBefore);
                
                // Allow classical controller to intervene
                if (classicalController != null) {
                    classicalController.processEvents(context);
                }
                
                // Check stop conditions
                if (context.shouldStop()) {
                    break;
                }
            }
            
            // Collect results
            SimulationResult result = new SimulationResult(
                context.getCurrentState(),
                context.getMeasurements(),
                context.getMetrics(),
                System.currentTimeMillis() - simulationStartTime
            );
            
            context.getMetrics().onCompleted(result);
            notifyObserversCompleted(result);
            return result;
            
        } catch (Exception e) {
            if (pluginManager != null) {
                pluginManager.notifyError(e.getMessage(), e);
            }
            throw new SimulationError("Simulation failed: " + e.getMessage(), e);
        } finally {
            isRunning.set(false);
            if (pluginManager != null) {
                pluginManager.cleanupPlugins(context);
            }
            cleanup();
        }
    }
    
    public void step() throws SimulationError {
        if (context == null) {
            throw new SimulationError("Simulator not initialized");
        }
        
        if (eventDispatcher.isQueueEmpty()) {
            throw new SimulationError("No events in queue");
        }
        
        try {
            int measurementCountBefore = context.getMeasurements().size();
            QuantumEvent event = eventDispatcher.dispatchNextEvent(context);
            if (event != null) {
                notifyObserversStepped(event);
            }
            notifyObserversMeasurements(measurementCountBefore);
            if (classicalController != null) {
                classicalController.processEvents(context);
            }
        } catch (Exception e) {
            if (pluginManager != null) {
                pluginManager.notifyError(e.getMessage(), e);
            }
            throw new SimulationError("Step execution failed: " + e.getMessage(), e);
        }
    }
    
    public void scheduleEvent(QuantumEvent event) throws SimulationError {
        if (context == null) {
            throw new SimulationError("Simulator not initialized");
        }
        
        eventDispatcher.scheduleEvent(event);
        context.getMetrics().recordEventScheduled(event);
        if (pluginManager != null) {
            pluginManager.notifyEventScheduled(event);
        }
        notifyObserversEventScheduled(event);
    }
    
    public void reset() {
        if (context != null) {
            context.reset();
        }
        if (eventDispatcher != null) {
            eventDispatcher.clear();
        }
        isRunning.set(false);
        notifyObserversReset();
    }
    
    public void stop() {
        isRunning.set(false);
    }
    
    public void addObserver(SimulationObserver observer) {
        observers.add(observer);
    }
    
    public void removeObserver(SimulationObserver observer) {
        observers.remove(observer);
    }
    
    public SimulationContext getContext() {
        return context;
    }
    
    public PluginManager getPluginManager() {
        return pluginManager;
    }
    
    public ClassicalController getClassicalController() {
        return classicalController;
    }
    
    public void shutdown() {
        if (pluginManager != null) {
            pluginManager.unloadAll();
        }
        cleanup();
        isRunning.set(false);
    }
    
    public boolean isRunning() {
        return isRunning.get();
    }
    
    public long getElapsedTime() {
        return isRunning.get() ? 
            System.currentTimeMillis() - simulationStartTime : 0;
    }
    
    private SimulationStrategy createStrategy(SimulationBackend backend) throws SimulationError {
        switch (backend) {
            case STATE_VECTOR:
                return new StateVectorStrategy();
            case DENSITY_MATRIX:
                return new DensityMatrixStrategy();
            default:
                throw new SimulationError("Unsupported backend: " + backend);
        }
    }
    
    private void notifyObserversInitialized() {
        for (SimulationObserver observer : observers) {
            observer.onInitialized(context);
        }
    }
    
    private void notifyObserversStepped(QuantumEvent event) {
        for (SimulationObserver observer : observers) {
            observer.onStateUpdate(context.getCurrentState(), event);
        }
        if (pluginManager != null) {
            pluginManager.notifyStateUpdate(context.getCurrentState(), event);
            if (event != null && event.getOperation() != null) {
                pluginManager.notifyOperationApplied(event.getOperation());
            }
        }
    }
    
    private void notifyObserversEventScheduled(QuantumEvent event) {
        for (SimulationObserver observer : observers) {
            observer.onEventScheduled(event);
        }
    }
    
    private void notifyObserversCompleted(SimulationResult result) {
        for (SimulationObserver observer : observers) {
            observer.onCompleted(result);
        }
    }
    
    private void notifyObserversMeasurements(int previousCount) {
        List<MeasurementResult> measurements = context.getMeasurements();
        for (int i = previousCount; i < measurements.size(); i++) {
            MeasurementResult measurement = measurements.get(i);
            for (SimulationObserver observer : observers) {
                observer.onMeasurement(measurement);
            }
        }
    }
    
    private void notifyObserversReset() {
        for (SimulationObserver observer : observers) {
            observer.onReset();
        }
    }
    
    private void cleanup() {
        if (resourceManager != null) {
            resourceManager.releaseAll();
        }
    }
}