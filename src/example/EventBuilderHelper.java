package example;

import com.quantum.simulator.events.QuantumEvent;
import com.quantum.simulator.operations.MultiQubitGate;
import com.quantum.simulator.operations.QuantumOperation;
import com.quantum.simulator.state.Qubit;
import java.time.Instant;

public class EventBuilderHelper {
    
    private static long eventCounter = 0;
    private static Instant currentTime = Instant.now();
    
    public static void resetTimer() {
        currentTime = Instant.now();
        eventCounter = 0;
    }
    
    public static void advanceTime(long milliseconds) {
        currentTime = currentTime.plusMillis(milliseconds);
    }
    
    // Generic method for any quantum operation
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
    public static QuantumEvent createSingleQubitGate(String gateName, QuantumOperation operation, 
                                                    Qubit target) {
        return createEvent(gateName, operation, new Qubit[]{target}, new Qubit[0]);
    }
    
    // Two-qubit gate with explicit targets (for gates like SWAP)
    public static QuantumEvent createTwoQubitGate(String gateName, QuantumOperation operation,
                                                 Qubit target1, Qubit target2) {
        return createEvent(gateName, operation, new Qubit[]{target1, target2}, new Qubit[0]);
    }
    
    // CNOT gate - both qubits are involved, target qubit is the one being flipped
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
    public static QuantumEvent createSWAP(String gateName, Qubit qubit1, Qubit qubit2) {
        QuantumOperation operation = MultiQubitGate.createSWAP(qubit1, qubit2);
        return createEvent(gateName, operation, new Qubit[]{qubit1, qubit2}, new Qubit[0]);
    }
    
    // CPHASE gate - controlled phase gate
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