package com.quantum.simulator.events;

import java.util.Locale;
import com.quantum.simulator.core.SimulationContext;
import com.quantum.simulator.operations.QuantumOperation;
import com.quantum.simulator.state.Qubit;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;

public class QuantumEvent implements Comparable<QuantumEvent> {
    private static final AtomicLong SEQUENCE = new AtomicLong();
    
    private final String id;
    private final long sequence;
    private final Instant scheduledTime;
    private final EventPriority priority;
    private final EventType type;
    private final QuantumOperation operation;
    private Qubit[] targetQubits;
    private final String description;
    private Instant executionTime;
    private boolean executed;
    private ExecutionResult executionResult;
    
    public QuantumEvent(String id, Instant scheduledTime, EventPriority priority,
                       EventType type, QuantumOperation operation, Qubit[] targetQubits,
                       String description) {
        this.id = Objects.requireNonNull(id, "Event ID cannot be null");
        this.sequence = SEQUENCE.getAndIncrement();
        this.scheduledTime = Objects.requireNonNull(scheduledTime, "Scheduled time cannot be null");
        this.priority = Objects.requireNonNull(priority, "Priority cannot be null");
        this.type = Objects.requireNonNull(type, "Event type cannot be null");
        this.operation = operation;
        this.targetQubits = targetQubits != null ? targetQubits.clone() : new Qubit[0];
        this.description = description != null ? description : "";
        this.executed = false;
        this.executionResult = null;
        
        validateEvent();
    }
 // In QuantumEvent.java - modify the validateEvent method
    private void validateEvent() {
        if (operation == null) {
            return;
        }
        
        Qubit[] operationTargets = operation.getTargetQubits();
        Qubit[] operationControls = operation.getControlQubits();
        if (operationTargets.length == 0 && operationControls.length == 0) {
            return;
        }
        
        if (targetQubits.length == 0) {
            this.targetQubits = mergeQubits(operationTargets, operationControls);
            return;
        }
        
        // The event may list both controls and targets for reporting. Keep that list
        // when it only contains qubits used by the operation; otherwise adopt the
        // operation's qubits so the metadata cannot contradict the operation.
        boolean onlyOperationQubits = true;
        for (Qubit qubit : targetQubits) {
            if (!containsQubit(operationTargets, qubit) && !containsQubit(operationControls, qubit)) {
                onlyOperationQubits = false;
                break;
            }
        }
        
        boolean containsAllTargets = true;
        for (Qubit target : operationTargets) {
            if (!containsQubit(targetQubits, target)) {
                containsAllTargets = false;
                break;
            }
        }
        
        if (!onlyOperationQubits || !containsAllTargets
            || targetQubits.length < operationTargets.length) {
            this.targetQubits = mergeQubits(operationTargets, operationControls);
        }
    }
    
    private static boolean containsQubit(Qubit[] qubits, Qubit candidate) {
        for (Qubit qubit : qubits) {
            if (qubit.equals(candidate)) {
                return true;
            }
        }
        return false;
    }
    
    private static Qubit[] mergeQubits(Qubit[] targets, Qubit[] controls) {
        Qubit[] merged = new Qubit[targets.length + controls.length];
        System.arraycopy(targets, 0, merged, 0, targets.length);
        System.arraycopy(controls, 0, merged, targets.length, controls.length);
        return merged;
    }
    
    public void execute(SimulationContext context) {
        if (executed) {
            throw new IllegalStateException("Event has already been executed");
        }
        
        executionTime = Instant.now();
        try {
            if (operation != null) {
                // Apply the operation to the current state using the context
                context.applyOperation(operation);
            }
            
            executionResult = ExecutionResult.success(
                executionTime,
                Duration.between(executionTime, Instant.now()),
                "Event executed successfully"
            );
            
        } catch (Exception e) {
            executionResult = ExecutionResult.failure(
                executionTime,
                Duration.between(executionTime, Instant.now()),
                "Failed to execute event: " + e.getMessage(),
                e
            );
            throw new EventExecutionException("Failed to execute event: " + id, e);
        } finally {
            executed = true;
        }
    }
    
    public String getId() {
        return id;
    }
    
    public Instant getScheduledTime() {
        return scheduledTime;
    }
    
    public EventPriority getPriority() {
        return priority;
    }
    
    public EventType getType() {
        return type;
    }
    
    public QuantumOperation getOperation() {
        return operation;
    }
    
    public Qubit[] getTargetQubits() {
        return targetQubits.clone();
    }
    
    public String getDescription() {
        return description;
    }
    
    public Instant getExecutionTime() {
        return executionTime;
    }
    
    public boolean isExecuted() {
        return executed;
    }
    
    public ExecutionResult getExecutionResult() {
        return executionResult;
    }
    
    public Duration getDelay() {
        if (executionTime == null) {
            return null;
        }
        return Duration.between(scheduledTime, executionTime);
    }
    
    public boolean isOverdue(Instant currentTime) {
        return scheduledTime.isBefore(currentTime);
    }
    
    @Override
    public int compareTo(QuantumEvent other) {
        // First compare by scheduled time
        int timeCompare = this.scheduledTime.compareTo(other.scheduledTime);
        if (timeCompare != 0) {
            return timeCompare;
        }
        
        // Then compare by priority (higher priority comes first)
        int priorityCompare = other.priority.compareTo(this.priority);
        if (priorityCompare != 0) {
            return priorityCompare;
        }
        
        // Finally preserve scheduling order for otherwise equal events
        return Long.compare(this.sequence, other.sequence);
    }
    
    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        
        QuantumEvent other = (QuantumEvent) obj;
        return id.equals(other.id);
    }
    
    @Override
    public int hashCode() {
        return id.hashCode();
    }
    
    @Override
    public String toString() {
        return String.format(Locale.ROOT, 
            "QuantumEvent[id='%s', time=%s, priority=%s, type=%s, operation=%s, targets=%s]",
            id, scheduledTime, priority, type, operation != null ? operation.getName() : "none",
            Arrays.toString(targetQubits)
        );
    }
    
    // Builder pattern for creating events
    public static class Builder {
        private String id;
        private Instant scheduledTime;
        private EventPriority priority = EventPriority.NORMAL;
        private EventType type = EventType.GATE_OPERATION;
        private QuantumOperation operation;
        private Qubit[] targetQubits;
        private String description;
        
        public Builder(String id) {
            this.id = id;
            this.scheduledTime = Instant.now();
        }
        
        public Builder withScheduledTime(Instant time) {
            this.scheduledTime = time;
            return this;
        }
        
        public Builder withPriority(EventPriority priority) {
            this.priority = priority;
            return this;
        }
        
        public Builder withType(EventType type) {
            this.type = type;
            return this;
        }
        
        public Builder withOperation(QuantumOperation operation) {
            this.operation = operation;
            return this;
        }
        
        public Builder withTargetQubits(Qubit... targetQubits) {
            this.targetQubits = targetQubits;
            return this;
        }
        
        public Builder withDescription(String description) {
            this.description = description;
            return this;
        }
        
        public QuantumEvent build() {
            return new QuantumEvent(id, scheduledTime, priority, type, operation, 
                                  targetQubits, description);
        }
    }
    
    // Inner classes
    public static class EventExecutionException extends RuntimeException {
        public EventExecutionException(String message) {
            super(message);
        }
        
        public EventExecutionException(String message, Throwable cause) {
            super(message, cause);
        }
    }
    
    public static class ExecutionResult {
        private final Instant executionTime;
        private final Duration executionDuration;
        private final boolean success;
        private final String message;
        private final Throwable error;
        
        private ExecutionResult(Instant executionTime, Duration executionDuration,
                              boolean success, String message, Throwable error) {
            this.executionTime = executionTime;
            this.executionDuration = executionDuration;
            this.success = success;
            this.message = message;
            this.error = error;
        }
        
        public static ExecutionResult success(Instant executionTime, Duration duration, String message) {
            return new ExecutionResult(executionTime, duration, true, message, null);
        }
        
        public static ExecutionResult failure(Instant executionTime, Duration duration,
                                            String message, Throwable error) {
            return new ExecutionResult(executionTime, duration, false, message, error);
        }
        
        public Instant getExecutionTime() { return executionTime; }
        public Duration getExecutionDuration() { return executionDuration; }
        public boolean isSuccess() { return success; }
        public String getMessage() { return message; }
        public Throwable getError() { return error; }
        
        @Override
        public String toString() {
            return String.format(Locale.ROOT, 
                "ExecutionResult[success=%s, duration=%s, message='%s']",
                success, executionDuration, message
            );
        }
    }
}