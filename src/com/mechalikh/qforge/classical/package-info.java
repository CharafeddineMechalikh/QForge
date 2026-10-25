/**
 * Classical control and hybrid quantum classical algorithms.
 *
 * <p>{@link ClassicalController} reacts to
 * {@link com.mechalikh.qforge.measurement.MeasurementResult} objects, applies its
 * {@link ClassicalController.ControlRule} instances and turns the resulting
 * {@link ControlDecision} objects into follow up events for the simulation queue. The
 * decision data is described by {@link ControlDecision} and its
 * {@link ControlDecision.DecisionAction} values.
 *
 * <p>{@link HybridAlgorithm} defines the contract for algorithms that interleave circuit
 * execution with classical update steps.
 * {@link VariationalQuantumEigensolver} implements that contract with a layered ansatz
 * and gradient based optimization, and {@link ParameterSpace} provides the named,
 * bounded parameters used by such algorithms.
 */
package com.mechalikh.qforge.classical;
