package com.quantum.simulator.operations;

import java.util.Locale;
import com.quantum.simulator.state.QuantumState;
import com.quantum.simulator.state.Qubit;
import com.quantum.simulator.math.ComplexMatrix;
import java.util.Arrays;
import java.util.Objects;

/**
 * Base class for gate operations represented by a complex matrix.
 *
 * <p>A gate stores zero or more control qubits and zero or more target qubits. The matrix acts on
 * the combined register formed by the control qubits followed by the target qubits. For a
 * controlled gate, the control qubits are therefore the most significant matrix bits and the
 * target qubits are the least significant ones.</p>
 *
 * <p>The matrix must be square and its dimension must equal {@code 2^n}, where {@code n} is the
 * total number of control and target qubits. {@link #getMatrixQubitIndices()} reports the qubit
 * indices in the same order, so callers can embed the matrix into a larger state.</p>
 *
 * <p>Subclasses in this package do not apply themselves: {@link #apply(QuantumState)} throws
 * {@link UnsupportedOperationException}, and gate application is performed by the simulation
 * strategy through the simulation context.</p>
 */
public abstract class QuantumGate implements QuantumOperation {
	/** The gate name. */
	protected final String name;
	/** The gate matrix, ordered with control qubits first, then target qubits. */
	protected final ComplexMatrix matrix;
	/** The target qubits, in target order. */
	protected final Qubit[] targetQubits;
	/** The control qubits, in control order; empty when there are none. */
	protected final Qubit[] controlQubits;
	/** The duration stored for the gate. */
	protected final double duration;

	/**
	 * Creates a gate and validates its matrix dimensions.
	 *
	 * <p>The matrix must be square and its size must equal {@code 2^(controlQubits.length +
	 * targetQubits.length)}. The qubit arrays are cloned into the gate.</p>
	 *
	 * @param name the gate name, must not be {@code null}
	 * @param matrix the gate matrix
	 * @param targetQubits the target qubits
	 * @param controlQubits the control qubits
	 * @param duration the gate duration
	 * @throws IllegalArgumentException if the matrix is not square or its size does not match the
	 *         qubit count
	 * @throws NullPointerException if {@code name}, {@code matrix}, {@code targetQubits}, or
	 *         {@code controlQubits} is {@code null}
	 */
	protected QuantumGate(String name, ComplexMatrix matrix, Qubit[] targetQubits, Qubit[] controlQubits,
			double duration) {
		if (matrix.rows() != matrix.cols()) {
			throw new IllegalArgumentException("Gate matrix must be square");
		}

        // Gate matrices are ordered with control qubits first, then target qubits.
		int totalQubits = targetQubits.length + controlQubits.length;
		int expectedSize = 1 << totalQubits; // 2^totalQubits

		if (matrix.rows() != expectedSize) {
			throw new IllegalArgumentException(String.format(Locale.ROOT, 
					"Matrix dimension %dx%d does not match total qubits count %d (targets=%d, controls=%d)",
					matrix.rows(), matrix.cols(), totalQubits, targetQubits.length, controlQubits.length));
		}

		this.name = Objects.requireNonNull(name);
		this.matrix = matrix;
		this.targetQubits = targetQubits.clone();
		this.controlQubits = (controlQubits != null) ? controlQubits.clone() : new Qubit[0];
		this.duration = duration;
	}
	
	/**
	 * Returns the state qubit indices covered by the gate matrix, in matrix order.
	 *
	 * <p>The returned array lists the control qubit indices first, in control order, followed by
	 * the target qubit indices, in target order. The first entry is the most significant matrix
	 * bit, matching the matrix ordering described in the class documentation.</p>
	 *
	 * @return a new array of qubit indices
	 */
	public int[] getMatrixQubitIndices() {
		int[] indices = new int[controlQubits.length + targetQubits.length];
		for (int i = 0; i < controlQubits.length; i++) {
			indices[i] = controlQubits[i].getIndex();
		}
		for (int i = 0; i < targetQubits.length; i++) {
			indices[controlQubits.length + i] = targetQubits[i].getIndex();
		}
		return indices;
	}

	/**
	 * {@inheritDoc}
	 *
	 * <p>Subclasses in this package throw {@link UnsupportedOperationException} because the
	 * simulation strategy applies the matrix instead.</p>
	 */
	@Override
	public abstract QuantumState apply(QuantumState state);

	/**
	 * {@inheritDoc}
	 */
	@Override
	public String getName() {
		return name;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public double getDuration() {
		return duration;
	}

	/**
	 * {@inheritDoc}
	 *
	 * @return a copy of the target qubit array
	 */
	@Override
	public Qubit[] getTargetQubits() {
		return targetQubits.clone();
	}

	/**
	 * {@inheritDoc}
	 *
	 * @return a copy of the control qubit array, empty when there are no controls
	 */
	@Override
	public Qubit[] getControlQubits() {
		return controlQubits.clone();
	}

	/**
	 * Always returns {@code true} for this base class.
	 *
	 * @return {@code true}
	 */
	@Override
	public boolean isUnitary() {
		return true;
	}

	/**
	 * Always returns {@code false} for this base class.
	 *
	 * @return {@code false}
	 */
	@Override
	public boolean isNoise() {
		return false;
	}

	/**
	 * Always returns {@code false} for this base class.
	 *
	 * @return {@code false}
	 */
	@Override
	public boolean isMeasurement() {
		return false;
	}

	/**
	 * Returns a copy of the gate matrix.
	 *
	 * @return a copy of the matrix
	 */
	public ComplexMatrix getMatrix() {
		return matrix.copy();
	}

	/**
	 * Returns the total number of control and target qubits.
	 *
	 * @return the number of qubits covered by the matrix
	 */
	public int getNumQubits() {
		return targetQubits.length + controlQubits.length;
	}

	/**
	 * Returns whether the gate has exactly one target qubit and no control qubits.
	 *
	 * @return {@code true} if the gate is a single-qubit gate
	 */
	public boolean isSingleQubitGate() {
		return targetQubits.length == 1 && controlQubits.length == 0;
	}

	/**
	 * Returns whether the gate has at least one control qubit.
	 *
	 * @return {@code true} if the gate is controlled
	 */
	public boolean isControlledGate() {
		return controlQubits.length > 0;
	}

	/**
	 * Returns a gate whose matrix is the adjoint of this gate matrix.
	 *
	 * <p>The result is a {@link CustomGate} with the same qubits and duration and a name formed by
	 * appending a dagger character to this gate name.</p>
	 *
	 * @return the inverse gate
	 */
	public QuantumGate getInverse() {
		// Inverse of a unitary gate is its adjoint
		ComplexMatrix inverseMatrix = matrix.adjoint();
		return new CustomGate(name + "†", inverseMatrix, targetQubits, controlQubits, duration);
	}

	/**
	 * {@inheritDoc}
	 *
	 * @return the result of {@link #getInverse()}
	 */
	@Override
	public QuantumOperation inverse() {
		return getInverse();
	}

	/**
	 * Compares this gate with another object.
	 *
	 * <p>Two gates are equal when they have the same concrete class, name, matrix, target and
	 * control arrays, and durations that differ by less than {@code 1e-10}.</p>
	 *
	 * @param obj the object to compare with
	 * @return {@code true} if the objects are equal
	 */
	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null || getClass() != obj.getClass())
			return false;

		QuantumGate other = (QuantumGate) obj;
		return name.equals(other.name) && matrix.equals(other.matrix) && Arrays.equals(targetQubits, other.targetQubits)
				&& Arrays.equals(controlQubits, other.controlQubits) && Math.abs(duration - other.duration) < 1e-10;
	}

	/**
	 * Returns a hash code derived from the name, matrix, qubit arrays, and duration.
	 *
	 * @return the hash code
	 */
	@Override
	public int hashCode() {
		int result = name.hashCode();
		result = 31 * result + matrix.hashCode();
		result = 31 * result + Arrays.hashCode(targetQubits);
		result = 31 * result + Arrays.hashCode(controlQubits);
		result = 31 * result + Double.hashCode(duration);
		return result;
	}

	/**
	 * Returns a formatted description of the gate name, targets, controls, and duration.
	 *
	 * @return the description
	 */
	@Override
	public String toString() {
		return String.format(Locale.ROOT, "QuantumGate[name='%s', targets=%s, controls=%s, duration=%.3f]", name,
				Arrays.toString(targetQubits), Arrays.toString(controlQubits), duration);
	}

	/**
	 * Gate defined entirely by a name, matrix, qubit assignment, and duration.
	 */
	public static class CustomGate extends QuantumGate {
		/**
		 * Creates a custom gate.
		 *
		 * @param name the gate name, must not be {@code null}
		 * @param matrix the gate matrix
		 * @param targetQubits the target qubits
		 * @param controlQubits the control qubits
		 * @param duration the gate duration
		 * @throws IllegalArgumentException if the matrix is not square or its size does not match
		 *         the qubit count
		 * @throws NullPointerException if {@code name}, {@code matrix}, {@code targetQubits}, or
		 *         {@code controlQubits} is {@code null}
		 */
		public CustomGate(String name, ComplexMatrix matrix, Qubit[] targetQubits, Qubit[] controlQubits,
				double duration) {
			super(name, matrix, targetQubits, controlQubits, duration);
		}

		/**
		 * Always throws {@link UnsupportedOperationException}; application depends on the
		 * simulation strategy.
		 *
		 * @param state the state to operate on
		 * @return never returns normally
		 * @throws UnsupportedOperationException always
		 */
		@Override
		public QuantumState apply(QuantumState state) {
			// Implementation depends on the simulation strategy
			throw new UnsupportedOperationException("Gate application is strategy-specific");
		}
	}
}