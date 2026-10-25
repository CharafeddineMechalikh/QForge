/**
 * Core simulation types: the simulator facade, the execution context, the result snapshot
 * and the checked error type.
 *
 * <p>{@link com.mechalikh.qforge.core.QuantumSimulator} owns the lifecycle: it validates
 * the configuration, schedules events, runs them and releases resources.
 * {@link com.mechalikh.qforge.core.SimulationContext} holds the state shared by
 * strategies, events and observers, while
 * {@link com.mechalikh.qforge.core.SimulationResult} captures the final state,
 * measurements and metrics of a run. Failures are reported as
 * {@link com.mechalikh.qforge.core.SimulationError}.
 */
package com.mechalikh.qforge.core;
