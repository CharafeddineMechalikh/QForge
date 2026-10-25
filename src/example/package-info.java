/**
 * Runnable demonstrations and helpers for the QForge quantum circuit simulator.
 *
 * <p>The programs in this package build circuits from
 * {@link com.mechalikh.qforge.events.QuantumEvent} instances, schedule them on a
 * {@link com.mechalikh.qforge.core.QuantumSimulator}, and print the resulting states,
 * probabilities, and reports. {@link example.QuantumAlgorithmsDemo} is the canonical entry point
 * for the algorithm demonstrations in {@link example.algorithms};
 * {@link example.CircuitRunner} runs a circuit described by JSON files; and
 * {@link example.EventBuilderHelper} is the shared utility that timestamps events.</p>
 *
 * <p>The classes here are examples, not a stable library API. Several of them print progress and
 * diagnostics to standard output, and one writes visualization files under {@code output/}.</p>
 */
package example;
