package com.quantum.simulator.operations;

import java.util.Locale;
import com.quantum.simulator.state.QuantumState;
import com.quantum.simulator.state.Qubit;
import com.quantum.simulator.math.ComplexMatrix;
import java.util.Arrays;
import java.util.Objects;

public abstract class QuantumGate implements QuantumOperation {
	protected final String name;
	protected final ComplexMatrix matrix;
	protected final Qubit[] targetQubits;
	protected final Qubit[] controlQubits;
	protected final double duration;

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

	@Override
	public abstract QuantumState apply(QuantumState state);

	@Override
	public String getName() {
		return name;
	}

	@Override
	public double getDuration() {
		return duration;
	}

	@Override
	public Qubit[] getTargetQubits() {
		return targetQubits.clone();
	}

	@Override
	public Qubit[] getControlQubits() {
		return controlQubits.clone();
	}

	@Override
	public boolean isUnitary() {
		return true;
	}

	@Override
	public boolean isNoise() {
		return false;
	}

	@Override
	public boolean isMeasurement() {
		return false;
	}

	public ComplexMatrix getMatrix() {
		return matrix.copy();
	}

	public int getNumQubits() {
		return targetQubits.length + controlQubits.length;
	}

	public boolean isSingleQubitGate() {
		return targetQubits.length == 1 && controlQubits.length == 0;
	}

	public boolean isControlledGate() {
		return controlQubits.length > 0;
	}

	public QuantumGate getInverse() {
		// Inverse of a unitary gate is its adjoint
		ComplexMatrix inverseMatrix = matrix.adjoint();
		return new CustomGate(name + "†", inverseMatrix, targetQubits, controlQubits, duration);
	}

	@Override
	public QuantumOperation inverse() {
		return getInverse();
	}

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

	@Override
	public int hashCode() {
		int result = name.hashCode();
		result = 31 * result + matrix.hashCode();
		result = 31 * result + Arrays.hashCode(targetQubits);
		result = 31 * result + Arrays.hashCode(controlQubits);
		result = 31 * result + Double.hashCode(duration);
		return result;
	}

	@Override
	public String toString() {
		return String.format(Locale.ROOT, "QuantumGate[name='%s', targets=%s, controls=%s, duration=%.3f]", name,
				Arrays.toString(targetQubits), Arrays.toString(controlQubits), duration);
	}

	// Inner class for custom gates
	public static class CustomGate extends QuantumGate {
		public CustomGate(String name, ComplexMatrix matrix, Qubit[] targetQubits, Qubit[] controlQubits,
				double duration) {
			super(name, matrix, targetQubits, controlQubits, duration);
		}

		@Override
		public QuantumState apply(QuantumState state) {
			// Implementation depends on the simulation strategy
			throw new UnsupportedOperationException("Gate application is strategy-specific");
		}
	}
}