/**
 * Simulation observers, metric collection, and Java2D visualization.
 *
 * <p>{@link com.quantum.simulator.observer.SimulationObserver} defines the lifecycle
 * callbacks. {@link com.quantum.simulator.observer.MetricsCollector} records counts,
 * timings, fidelity, and measurement statistics and produces
 * {@link com.quantum.simulator.observer.MetricsCollector.SimulationReport} snapshots.
 * {@link com.quantum.simulator.observer.VisualizationEngine} captures state and event
 * history and renders PNG images and animated GIFs with Java2D.</p>
 */
package com.quantum.simulator.observer;
