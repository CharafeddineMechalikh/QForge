package com.mechalikh.qforge.core;

import com.mechalikh.qforge.config.SimulationBackend;
import com.mechalikh.qforge.config.SimulationConfig;
import com.mechalikh.qforge.events.EventDispatcher;
import com.mechalikh.qforge.events.QuantumEvent;
import com.mechalikh.qforge.observer.SimulationObserver;
import com.mechalikh.qforge.measurement.MeasurementResult;
import com.mechalikh.qforge.resources.ResourceManager;
import com.mechalikh.qforge.plugin.PluginManager;
import com.mechalikh.qforge.strategy.DensityMatrixStrategy;
import com.mechalikh.qforge.strategy.SimulationStrategy;
import com.mechalikh.qforge.strategy.StabilizerStrategy;
import com.mechalikh.qforge.strategy.StateVectorStrategy;
import com.mechalikh.qforge.strategy.TensorNetworkStrategy;
import com.mechalikh.qforge.classical.ClassicalController;
import java.util.List;
import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Facade for building and running quantum circuit simulations.
 *
 * <p>A simulator instance follows a four stage lifecycle:
 * <ol>
 *   <li>{@link #initialize(SimulationConfig)} validates the configuration, creates a
 *       {@link SimulationContext}, an event dispatcher, a resource manager and, when
 *       classical control is enabled, a classical controller, then builds the initial
 *       quantum state.</li>
 *   <li>{@link #scheduleEvent(QuantumEvent)} places events in the dispatcher queue.</li>
 *   <li>{@link #run()} dispatches queued events until the queue is empty or the context
 *       reports a stop condition.</li>
 *   <li>{@link #shutdown()} unloads plugins and releases managed resources.</li>
 * </ol>
 *
 * <p>Each dispatched event is executed through
 * {@link QuantumEvent#execute(SimulationContext)}, which applies the event operation to the
 * context. Observers added with {@link #addObserver} receive initialization, scheduling,
 * state update, measurement, reset and completion callbacks. Plugins registered with the
 * plugin manager receive the matching plugin callbacks.
 *
 * <p>Results are returned by {@link #run()} as a {@link SimulationResult}. Failures are
 * reported as checked {@link SimulationError} exceptions that wrap the original cause. The
 * class makes no thread safety guarantees; the running flag is an
 * {@link java.util.concurrent.atomic.AtomicBoolean} that lets {@link #stop()} end a run
 * loop.
 */
public class QuantumSimulator {
    private SimulationContext context;
    private EventDispatcher eventDispatcher;
    private ResourceManager resourceManager;
    private PluginManager pluginManager;
    private ClassicalController classicalController;
    private List<SimulationObserver> observers;
    private AtomicBoolean isRunning;
    private long simulationStartTime;
    
    /**
     * Creates an uninitialized simulator with an empty observer list and a cleared running
     * flag.
     *
     * <p>No context, dispatcher, resource manager or classical controller exists until
     * {@link #initialize(SimulationConfig)} is called.
     */
    public QuantumSimulator() {
        this.observers = new ArrayList<>();
        this.isRunning = new AtomicBoolean(false);
    }
    
    /**
     * Validates the configuration and prepares this simulator for a run.
     *
     * <p>The method creates a new context and event dispatcher, replaces the resource
     * manager, creates a plugin manager only when none exists yet (so plugins survive a
     * reinitialization), and creates a classical controller only when classical control is
     * enabled. The strategy matching the configured backend is installed and the initial
     * quantum state is created. Finally the metrics collector and the observers are
     * notified of initialization.
     *
     * @param config configuration to use; it is checked with
     *        {@link SimulationConfig#validate()}
     * @throws SimulationError if the simulator is already running, if the configuration is
     *         invalid, or if any initialization step fails; such failures, including the
     *         invalid configuration error, are wrapped in a new error with the original
     *         exception as cause
     */
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
            notifyObserversError(e.getMessage(), e);
            throw new SimulationError("Failed to initialize simulator: " + e.getMessage(), e);
        }
    }
    
    /**
     * Runs the queued events to completion or until a stop condition is reached.
     *
     * <p>Requires a successful call to {@link #initialize(SimulationConfig)}. The running
     * flag is set, plugins receive their simulation start callback, and the queue is drained
     * one event per iteration. Before each dispatch {@link SimulationContext#shouldStop()} is
     * checked, so the step limit, the wall clock timeout and {@link #stop()} all end the run
     * without executing one extra event. After each dispatch the method notifies observers of
     * state changes, of newly recorded measurements and of progress, and lets the classical
     * controller process measurements, which may schedule further events.
     *
     * <p>{@link #pause()} also ends the run, after the event in flight. The partial result is
     * returned and observers receive {@link SimulationObserver#onCompleted(SimulationResult)};
     * the queue keeps the remaining events, so {@link #resume()} followed by another call to
     * this method continues from the same state.
     *
     * <p>The returned result captures the current state, the measurements, the metrics
     * collector and the elapsed wall clock time in milliseconds. On success or failure the
     * running flag is cleared, plugins receive their simulation end callback, and resources
     * held by the resource manager are released.
     *
     * @return the completed simulation result
     * @throws SimulationError if the simulator was not initialized or if event execution or
     *         any other part of the run fails; the original exception is attached as cause
     */
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
            long dispatched = 0;
            // Execute events until queue is empty, a limit is reached or the run is stopped
            while (!eventDispatcher.isQueueEmpty() && isRunning.get()
                    && !eventDispatcher.isPaused()) {
                if (context.shouldStop()) {
                    break;
                }
                int measurementCountBefore = context.getMeasurements().size();
                QuantumEvent event = eventDispatcher.dispatchNextEvent(context);
                if (event != null) {
                    notifyObserversStepped(event);
                }
                notifyObserversMeasurements(measurementCountBefore);
                dispatched++;
                notifyObserversProgress(dispatched, eventDispatcher.getQueueSize());
                
                // Allow classical controller to intervene
                if (classicalController != null) {
                    classicalController.processEvents(context);
                }
            }
            
            // Collect results
            SimulationResult result = new SimulationResult(
                context.getCurrentState(),
                context.getMeasurements(),
                context.getMetrics(),
                System.currentTimeMillis() - simulationStartTime,
                context.getRegisterValues()
            );
            
            context.getMetrics().onCompleted(result);
            notifyObserversCompleted(result);
            return result;
            
        } catch (Exception e) {
            if (pluginManager != null) {
                pluginManager.notifyError(e.getMessage(), e);
            }
            notifyObserversError(e.getMessage(), e);
            throw new SimulationError("Simulation failed: " + e.getMessage(), e);
        } finally {
            isRunning.set(false);
            if (pluginManager != null) {
                pluginManager.cleanupPlugins(context);
            }
            cleanup();
        }
    }
    
    /**
     * Dispatches exactly one event from the queue.
     *
     * <p>Requires initialization and a non empty queue. After dispatch, observers are
     * notified of the state update and of any new measurements, and the classical controller
     * processes measurements when it exists. This method does not set the running flag, does
     * not check stop conditions, does not call the plugin start or end callbacks, and does
     * not release resources; it is meant for manual stepping outside {@link #run()}.
     *
     * @throws SimulationError if the simulator is not initialized, if the queue is empty, or
     *         if the event fails to execute
     */
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
            notifyObserversError(e.getMessage(), e);
            throw new SimulationError("Step execution failed: " + e.getMessage(), e);
        }
    }
    
    /**
     * Adds an event to the dispatch queue.
     *
     * <p>Requires initialization. The event is queued, recorded in the metrics collector,
     * and reported to plugins and observers. Queue order is defined by
     * {@link QuantumEvent#compareTo(QuantumEvent)}: scheduled time first, then priority,
     * then insertion sequence.
     *
     * @param event event to schedule; must not be null
     * @throws SimulationError if the simulator is not initialized
     * @throws IllegalArgumentException if an event with the same identifier is already
     *         queued; this is raised by the queue before metrics, plugins or observers are
     *         notified
     * @throws NullPointerException if {@code event} is null
     */
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
    
    /**
     * Resets the context and clears the event queue.
     *
     * <p>When a context exists, its quantum state is recreated through the strategy, and its
     * measurements, custom data, step counter, stop flag, start time and metrics are reset.
     * When a dispatcher exists, its queue is cleared and its paused flag is cleared. The running
     * flag is cleared and observers receive {@link SimulationObserver#onReset()}. Plugins, the
     * classical controller and the resources held by the resource manager are left untouched.
     */
    public void reset() {
        if (context != null) {
            context.reset();
        }
        if (eventDispatcher != null) {
            eventDispatcher.clear();
            eventDispatcher.resume();
        }
        isRunning.set(false);
        notifyObserversReset();
    }
    
    /**
     * Clears the running flag so that an active {@link #run()} loop exits after the current
     * event. Has no effect when no run is in progress.
     *
     * <p>When a run was in progress, observers receive
     * {@link SimulationObserver#onSimulationStopped()} after the flag is cleared.</p>
     */
    public void stop() {
        if (isRunning.getAndSet(false)) {
            notifyObserversStopped();
        }
    }
    
    /**
     * Requests that an active run stop after the event in flight.
     *
     * <p>Requires initialization. The paused flag is checked by {@link #run()} between events,
     * so the call returns a partial result and leaves the remaining events queued. Observers
     * receive {@link SimulationObserver#onSimulationPaused()}. The method does not block and
     * does not interrupt an event that is already executing.</p>
     *
     * @throws SimulationError if the simulator is not initialized
     */
    public void pause() throws SimulationError {
        if (eventDispatcher == null || context == null) {
            throw new SimulationError("Simulator not initialized");
        }
        eventDispatcher.pause();
        notifyObserversPaused();
    }
    
    /**
     * Clears the paused flag so that a later {@link #run()} can dispatch the queued events.
     *
     * <p>Requires initialization. Observers receive
     * {@link SimulationObserver#onSimulationResumed()} even when the simulator was not paused,
     * because the call always clears the flag.</p>
     *
     * @throws SimulationError if the simulator is not initialized
     */
    public void resume() throws SimulationError {
        if (eventDispatcher == null || context == null) {
            throw new SimulationError("Simulator not initialized");
        }
        eventDispatcher.resume();
        notifyObserversResumed();
    }
    
    /**
     * Reports whether the simulator is paused.
     *
     * @return true when {@link #pause()} was called and {@link #resume()} or {@link #reset()}
     *         has not cleared the flag
     */
    public boolean isPaused() {
        return eventDispatcher != null && eventDispatcher.isPaused();
    }
    
    /**
     * Registers an observer for lifecycle notifications.
     *
     * <p>Observers are kept in a list and notified in registration order. Adding the same
     * instance twice results in two notifications per event.
     *
     * @param observer observer to add; it must not be null because it is called during later
     *        notifications
     */
    public void addObserver(SimulationObserver observer) {
        observers.add(observer);
    }
    
    /**
     * Removes the first occurrence of an observer from the notification list.
     *
     * @param observer observer to remove, compared with {@link Object#equals(Object)}; a
     *        value that is not registered is ignored
     */
    public void removeObserver(SimulationObserver observer) {
        observers.remove(observer);
    }
    
    /**
     * Returns the context created by the last successful initialization.
     *
     * @return the current context, or null when {@link #initialize(SimulationConfig)} has not
     *         completed successfully
     */
    public SimulationContext getContext() {
        return context;
    }
    
    /**
     * Returns the plugin manager used by this simulator.
     *
     * <p>The manager is created on the first successful initialization and then reused by
     * later initializations, so registered plugins survive a reinitialization.
     * {@link #shutdown()} unloads all plugins but keeps the manager instance.
     *
     * @return the plugin manager, or null when the simulator has never been initialized
     */
    public PluginManager getPluginManager() {
        return pluginManager;
    }
    
    /**
     * Returns the classical controller created during initialization.
     *
     * @return the controller, or null when classical control is disabled in the configuration
     *         or when the simulator has not been initialized
     */
    public ClassicalController getClassicalController() {
        return classicalController;
    }
    
    /**
     * Unloads all plugins, releases managed resources and clears the running flag.
     *
     * <p>Every loaded plugin is unloaded, which runs its cleanup callback, and every
     * allocation tracked by the resource manager is released. The context, dispatcher and
     * configuration are retained, so the simulator can be initialized again.
     */
    public void shutdown() {
        if (pluginManager != null) {
            pluginManager.unloadAll();
        }
        cleanup();
        isRunning.set(false);
    }
    
    /**
     * Reports whether a run is currently in progress.
     *
     * @return true between the start of {@link #run()} and its completion, a call to
     *         {@link #stop()}, or a call to {@link #shutdown()}, whichever happens first
     */
    public boolean isRunning() {
        return isRunning.get();
    }
    
    /**
     * Returns the wall clock time elapsed since the current run started.
     *
     * @return elapsed milliseconds while a run is in progress, or 0 when no run is active;
     *         the start time is captured by {@link #run()}
     */
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
            case STABILIZER:
                return new StabilizerStrategy();
            case TENSOR_NETWORK:
                return new TensorNetworkStrategy(context.getConfig().getMaxBondDimension());
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
    
    private void notifyObserversProgress(long dispatched, int queued) {
        long total = dispatched + queued;
        double progress = total <= 0 ? 1.0 : (double) dispatched / total;
        String message = "Dispatched " + dispatched + " of " + total + " known events";
        for (SimulationObserver observer : observers) {
            observer.onProgress(progress, message);
        }
    }
    
    private void notifyObserversError(String message, Throwable error) {
        for (SimulationObserver observer : observers) {
            observer.onError(message, error);
        }
    }
    
    private void notifyObserversPaused() {
        for (SimulationObserver observer : observers) {
            observer.onSimulationPaused();
        }
    }
    
    private void notifyObserversResumed() {
        for (SimulationObserver observer : observers) {
            observer.onSimulationResumed();
        }
    }
    
    private void notifyObserversStopped() {
        for (SimulationObserver observer : observers) {
            observer.onSimulationStopped();
        }
    }
    
    private void cleanup() {
        if (resourceManager != null) {
            resourceManager.releaseAll();
        }
    }
}