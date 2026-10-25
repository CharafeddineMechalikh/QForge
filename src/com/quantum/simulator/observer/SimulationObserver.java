package com.quantum.simulator.observer;

import com.quantum.simulator.core.SimulationContext;
import com.quantum.simulator.core.SimulationResult;
import com.quantum.simulator.events.QuantumEvent;
import com.quantum.simulator.state.QuantumState;
import com.quantum.simulator.measurement.MeasurementResult;

public interface SimulationObserver {
    default void onInitialized(SimulationContext context) {}
    default void onStateUpdate(QuantumState state, QuantumEvent event) {}
    default void onMeasurement(MeasurementResult result) {}
    default void onEventScheduled(QuantumEvent event) {}
    default void onCompleted(SimulationResult result) {}
    default void onError(String errorMessage, Throwable error) {}
    default void onReset() {}
    default void onProgress(double progress, String message) {}
    default void onConfigurationChanged(String parameter, Object oldValue, Object newValue) {}
    default void onResourceAllocated(String resourceType, int amount) {}
    default void onResourceReleased(String resourceType, int amount) {}
    default void onCheckpointCreated(String checkpointId, long timestamp) {}
    default void onCheckpointRestored(String checkpointId, long timestamp) {}
    default void onSimulationPaused() {}
    default void onSimulationResumed() {}
    default void onSimulationStopped() {}
}