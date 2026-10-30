package com.mechalikh.qforge.observer;

import com.mechalikh.qforge.core.SimulationContext;
import com.mechalikh.qforge.core.SimulationResult;
import com.mechalikh.qforge.events.QuantumEvent;
import com.mechalikh.qforge.state.QuantumState;
import com.mechalikh.qforge.measurement.MeasurementResult;

/**
 * Observer for simulation lifecycle events.
 *
 * <p>Every callback has an empty default implementation, so an implementation can
 * override only the callbacks it needs. The simulator invokes the callbacks
 * directly from the code that initializes, steps, resets, completes or fails a
 * simulation; no separate notification thread is created.</p>
 *
 * <p>Only events that the engine can raise are declared here. Configuration
 * changes, resource accounting and checkpoints have no engine support, so they are
 * not part of this interface.</p>
 *
 * <p>Observers receive the live objects held by the simulation context, such as
 * the current {@link QuantumState}. A callback that needs to keep data beyond the
 * call should copy it.</p>
 */
public interface SimulationObserver {
    /**
     * Called after the simulation context and its quantum state have been created.
     *
     * @param context the simulation context, which provides the configuration,
     *                current state, measurements, and metrics collector
     */
    default void onInitialized(SimulationContext context) {}
    /**
     * Called after an event has been applied to the quantum state.
     *
     * @param state the current state after the update
     * @param event the event that produced the update, or {@code null} when the
     *              update is not associated with an event
     */
    default void onStateUpdate(QuantumState state, QuantumEvent event) {}
    /**
     * Called once for each new measurement result added to the simulation.
     *
     * @param result the measurement result, including the observable name, outcome
     *               counts, expectation value, and variance
     */
    default void onMeasurement(MeasurementResult result) {}
    /**
     * Called after an event has been added to the event dispatcher queue.
     *
     * @param event the scheduled event
     */
    default void onEventScheduled(QuantumEvent event) {}
    /**
     * Called after the simulation run loop has finished.
     *
     * @param result the final result, which provides the final state, measurements,
     *               metrics, and total runtime in milliseconds
     */
    default void onCompleted(SimulationResult result) {}
    /**
     * Called to report an error.
     *
     * @param errorMessage a description of the error
     * @param error the throwable that caused the error, or {@code null} when only a
     *              message is available
     */
    default void onError(String errorMessage, Throwable error) {}
    /**
     * Called when the simulation is reset and its queued events are cleared.
     */
    default void onReset() {}
    /**
     * Called after each event of a run, before the next event is dispatched.
     *
     * @param progress the fraction of the events known at that moment that have been
     *                 dispatched, in the range {@code [0, 1]}
     * @param message a description of the current activity
     */
    default void onProgress(double progress, String message) {}
    /**
     * Called when the simulation is paused.
     */
    default void onSimulationPaused() {}
    /**
     * Called when the simulation is resumed.
     */
    default void onSimulationResumed() {}
    /**
     * Called when the simulation is stopped.
     */
    default void onSimulationStopped() {}
}