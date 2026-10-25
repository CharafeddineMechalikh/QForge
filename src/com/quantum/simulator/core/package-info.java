/**
 * Core simulation types: the simulator facade, the execution context, the result snapshot
 * and the checked error type.
 *
 * <p>{@link com.quantum.simulator.core.QuantumSimulator} owns the lifecycle: it validates
 * the configuration, schedules events, runs them and releases resources.
 * {@link com.quantum.simulator.core.SimulationContext} holds the state shared by
 * strategies, events and observers, while
 * {@link com.quantum.simulator.core.SimulationResult} captures the final state,
 * measurements and metrics of a run. Failures are reported as
 * {@link com.quantum.simulator.core.SimulationError}.
 */
package com.quantum.simulator.core;
