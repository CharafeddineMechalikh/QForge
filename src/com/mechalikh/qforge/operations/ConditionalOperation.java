package com.mechalikh.qforge.operations;

import com.mechalikh.qforge.state.QuantumState;
import com.mechalikh.qforge.state.Qubit;

/**
 * A classical condition guarding another operation.
 *
 * <p>The condition compares a {@link com.mechalikh.qforge.classical.ClassicalRegister} with an
 * expected value under a mask: {@code (register & mask) == (expected & mask)}. The guarded
 * operation is applied only when the comparison holds. {@link
 * com.mechalikh.qforge.core.SimulationContext} evaluates the condition when the operation is
 * dispatched, so a conditional event observes every measurement that has already executed.</p>
 *
 * <p>{@link #apply(QuantumState)} cannot see a register and therefore applies the guarded
 * operation unconditionally. Callers that need the condition must go through the context.</p>
 */
public class ConditionalOperation implements QuantumOperation {
    private final String name;
    private final QuantumOperation guardedOperation;
    private final String registerName;
    private final long expectedValue;
    private final long mask;

    /**
     * Creates a conditional operation.
     *
     * @param guardedOperation operation to apply when the condition holds
     * @param registerName classical register to test
     * @param expectedValue expected register value
     * @param mask bits that participate in the comparison
     * @throws NullPointerException if the operation or register name is {@code null}
     */
    public ConditionalOperation(QuantumOperation guardedOperation, String registerName,
                                long expectedValue, long mask) {
        if (guardedOperation == null) {
            throw new NullPointerException("Guarded operation cannot be null");
        }
        if (registerName == null) {
            throw new NullPointerException("Register name cannot be null");
        }
        this.name = "if(" + registerName + "==" + expectedValue + ") " + guardedOperation.getName();
        this.guardedOperation = guardedOperation;
        this.registerName = registerName;
        this.expectedValue = expectedValue;
        this.mask = mask;
    }

    /**
     * Creates a conditional operation that compares the whole register.
     *
     * @param guardedOperation operation to apply when the condition holds
     * @param registerName classical register to test
     * @param expectedValue expected register value
     */
    public ConditionalOperation(QuantumOperation guardedOperation, String registerName,
                                long expectedValue) {
        this(guardedOperation, registerName, expectedValue, -1L);
    }

    /**
     * Applies the guarded operation without evaluating the condition.
     *
     * <p>The context evaluates conditions before calling an operation, so this method is the
     * unconditional fallback required by {@link QuantumOperation}.</p>
     *
     * @param state state to transform
     * @return the state after the guarded operation
     */
    @Override
    public QuantumState apply(QuantumState state) {
        return guardedOperation.apply(state);
    }

    /**
     * Tests the condition against a register value.
     *
     * @param registerValue current value of the register
     * @return {@code true} when the masked bits match
     */
    public boolean test(long registerValue) {
        return (registerValue & mask) == (expectedValue & mask);
    }

    /**
     * Returns the guarded operation.
     *
     * @return the operation applied when the condition holds
     */
    public QuantumOperation getGuardedOperation() {
        return guardedOperation;
    }

    /**
     * Returns the name of the register that is tested.
     *
     * @return the register name
     */
    public String getRegisterName() {
        return registerName;
    }

    /**
     * Returns the expected register value.
     *
     * @return the expected value
     */
    public long getExpectedValue() {
        return expectedValue;
    }

    /**
     * Returns the comparison mask.
     *
     * @return the mask
     */
    public long getMask() {
        return mask;
    }

    /**
     * Returns the operation name, which includes the condition and the guarded name.
     *
     * @return the name
     */
    @Override
    public String getName() {
        return name;
    }

    /**
     * Returns the duration of the guarded operation.
     *
     * @return the guarded duration
     */
    @Override
    public double getDuration() {
        return guardedOperation.getDuration();
    }

    /**
     * Returns the target qubits of the guarded operation.
     *
     * @return the guarded target qubits
     */
    @Override
    public Qubit[] getTargetQubits() {
        return guardedOperation.getTargetQubits();
    }

    /**
     * Returns the control qubits of the guarded operation.
     *
     * @return the guarded control qubits
     */
    @Override
    public Qubit[] getControlQubits() {
        return guardedOperation.getControlQubits();
    }

    /**
     * Reports whether the guarded operation is unitary.
     *
     * @return the guarded flag
     */
    @Override
    public boolean isUnitary() {
        return guardedOperation.isUnitary();
    }

    /**
     * Reports whether the guarded operation is noise.
     *
     * @return the guarded flag
     */
    @Override
    public boolean isNoise() {
        return guardedOperation.isNoise();
    }

    /**
     * Reports whether the guarded operation is a measurement.
     *
     * @return the guarded flag
     */
    @Override
    public boolean isMeasurement() {
        return guardedOperation.isMeasurement();
    }

    /**
     * Returns the inverse as a conditional around the guarded inverse.
     *
     * @return a conditional with the same condition around the inverse operation
     * @throws UnsupportedOperationException if the guarded operation has no inverse
     */
    @Override
    public QuantumOperation inverse() {
        return new ConditionalOperation(guardedOperation.inverse(), registerName,
            expectedValue, mask);
    }

    /**
     * Returns a diagnostic description.
     *
     * @return the name and mask
     */
    @Override
    public String toString() {
        return "ConditionalOperation[name='" + name + "', mask=" + mask + "]";
    }
}
