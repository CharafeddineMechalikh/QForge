/**
 * Builders for the quantum algorithm circuits used by the demonstrations.
 *
 * <p>Each class assembles a circuit and returns it as an array or list of
 * {@link com.quantum.simulator.events.QuantumEvent} instances; none of them schedules or runs a
 * simulation. The circuits target small registers, typically two to nine qubits, and several use
 * simplified decompositions that the class documentation calls out.</p>
 *
 * <p>{@link example.algorithms.QuantumWalk} also defines a small package-private graph type used to describe
 * the position space of a walk.</p>
 */
package example.algorithms;
