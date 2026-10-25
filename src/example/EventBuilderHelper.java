package example;

import com.quantum.simulator.events.QuantumEvent;
import com.quantum.simulator.operations.MultiQubitGate;
import com.quantum.simulator.operations.QuantumOperation;
import com.quantum.simulator.state.Qubit;
import java.time.Instant;

/**
 * Creates {@link QuantumEvent} instances with monotonically increasing timestamps.
 *
 * <p>The helper keeps a static counter and a static current time. Each call to
 * {@code createEvent} appends the counter value to the event name, schedules the event at the
 * current time, and advances the shared time by 10 milliseconds. The timer is therefore shared by
 * every caller in the JVM and is not thread safe; call {@link #resetTimer()} before building a
 * new circuit to start from the current wall clock time.</p>
 */
public class EventBuilderHelper {
    
    private static long eventCounter = 0;
    private static Instant currentTime = Instant.now();
    
    /**
     * Resets the shared timer to the current wall clock time and the event counter to zero.
     */
    public static void resetTimer() {
        currentTime = Instant.now();
        eventCounter = 0;
    }
    
    /**
     * Advances the shared event time.
     *
     * @param milliseconds the number of milliseconds to add
     */
    public static void advanceTime(long milliseconds) {
        currentTime = currentTime.plusMillis(milliseconds);
    }
    
    // Generic method for any quantum operation
    /**
     * Creates an event for any quantum operation.
     *
     * <p>The event identifier is the event name followed by an underscore and the current counter
     * value. Target and control qubits are passed through to the event builder; the event
     * description is {@code Apply} followed by the event name. The shared time advances by 10
     * milliseconds after the event is built.</p>
     *
     * @param eventName the base name of the event
     * @param operation the quantum operation to apply
     * @param targetQubits the target qubits of this event
     * @param controlQubits the control qubits of this event
     * @return the constructed event
     */
    public static QuantumEvent createEvent(String eventName, QuantumOperation operation, 
                                           Qubit[] targetQubits, Qubit[] controlQubits) {
        String eventId = eventName + "_" + (eventCounter++);
        
        QuantumEvent.Builder builder = new QuantumEvent.Builder(eventId)
            .withScheduledTime(currentTime)
            .withOperation(operation)
            .withTargetQubits(targetQubits)
            .withDescription("Apply " + eventName);
        
        // Advance time for next event
        advanceTime(10);
        
        return builder.build();
    }
    
    // Single-qubit gate
    /**
     * Creates an event for a single-qubit gate.
     *
     * @param gateName the base name of the event
     * @param operation the gate operation
     * @param target the qubit the gate acts on
     * @return the constructed event, with the qubit as its only target and no controls
     */
    public static QuantumEvent createSingleQubitGate(String gateName, QuantumOperation operation, 
                                                    Qubit target) {
        return createEvent(gateName, operation, new Qubit[]{target}, new Qubit[0]);
    }
    
    // Two-qubit gate with explicit targets (for gates like SWAP)
    /**
     * Creates an event for a two-qubit gate whose two qubits are both targets.
     *
     * @param gateName the base name of the event
     * @param operation the gate operation
     * @param target1 the first target qubit
     * @param target2 the second target qubit
     * @return the constructed event with no control qubits
     */
    public static QuantumEvent createTwoQubitGate(String gateName, QuantumOperation operation,
                                                 Qubit target1, Qubit target2) {
        return createEvent(gateName, operation, new Qubit[]{target1, target2}, new Qubit[0]);
    }
    
    // CNOT gate - both qubits are involved, target qubit is the one being flipped
    /**
     * Creates an event for a CNOT gate.
     *
     * <p>The placement of the qubits depends on what the created operation reports as its target
     * qubits: if the operation reports one target, only {@code target} is used as a target and
     * {@code control} is used as a control; otherwise both qubits are passed as targets.</p>
     *
     * @param gateName the base name of the event
     * @param control the control qubit
     * @param target the target qubit
     * @return the constructed event
     */
    public static QuantumEvent createCNOT(String gateName, Qubit control, Qubit target) {
        QuantumOperation operation = MultiQubitGate.createCNOT(control, target);
        // For CNOT, both qubits are involved. The target qubit is the one that gets flipped.
        // Based on how MultiQubitGate.createCNOT is implemented, it likely expects:
        // - control qubit as first parameter
        // - target qubit as second parameter
        // Let's check what getTargetQubits() returns on the operation
        Qubit[] operationTargets = operation.getTargetQubits();
        
        if (operationTargets.length == 1) {
            // If the operation only has one target qubit (the one being flipped)
            return createEvent(gateName, operation, new Qubit[]{target}, new Qubit[]{control});
        } else if (operationTargets.length == 2) {
            // If the operation has both qubits as targets
            return createEvent(gateName, operation, new Qubit[]{control, target}, new Qubit[0]);
        } else {
            // Fallback: pass both as targets
            return createEvent(gateName, operation, new Qubit[]{control, target}, new Qubit[0]);
        }
    }
    
    // CZ gate - similar to CNOT but applies Z instead of X
    /**
     * Creates an event for a controlled Z gate.
     *
     * <p>As for {@link #createCNOT(String, Qubit, Qubit)}, the control qubit is recorded
     * separately only when the created operation reports a single target qubit.</p>
     *
     * @param gateName the base name of the event
     * @param control the control qubit
     * @param target the target qubit
     * @return the constructed event
     */
    public static QuantumEvent createCZ(String gateName, Qubit control, Qubit target) {
        QuantumOperation operation = MultiQubitGate.createCZ(control, target);
        Qubit[] operationTargets = operation.getTargetQubits();
        
        if (operationTargets.length == 1) {
            return createEvent(gateName, operation, new Qubit[]{target}, new Qubit[]{control});
        } else {
            return createEvent(gateName, operation, new Qubit[]{control, target}, new Qubit[0]);
        }
    }
    
    // SWAP gate - both qubits are targets
    /**
     * Creates an event for a SWAP gate, with both qubits recorded as targets.
     *
     * @param gateName the base name of the event
     * @param qubit1 the first qubit
     * @param qubit2 the second qubit
     * @return the constructed event with no control qubits
     */
    public static QuantumEvent createSWAP(String gateName, Qubit qubit1, Qubit qubit2) {
        QuantumOperation operation = MultiQubitGate.createSWAP(qubit1, qubit2);
        return createEvent(gateName, operation, new Qubit[]{qubit1, qubit2}, new Qubit[0]);
    }
    
    // CPHASE gate - controlled phase gate
    /**
     * Creates an event for a controlled phase gate.
     *
     * <p>As for {@link #createCNOT(String, Qubit, Qubit)}, the control qubit is recorded
     * separately only when the created operation reports a single target qubit.</p>
     *
     * @param gateName the base name of the event
     * @param angle the phase angle in radians
     * @param control the control qubit
     * @param target the target qubit
     * @return the constructed event
     */
    public static QuantumEvent createCPHASE(String gateName, double angle, 
                                           Qubit control, Qubit target) {
        QuantumOperation operation = MultiQubitGate.createCPHASE(angle, control, target);
        Qubit[] operationTargets = operation.getTargetQubits();
        
        if (operationTargets.length == 1) {
            return createEvent(gateName, operation, new Qubit[]{target}, new Qubit[]{control});
        } else {
            return createEvent(gateName, operation, new Qubit[]{control, target}, new Qubit[0]);
        }
    }
}