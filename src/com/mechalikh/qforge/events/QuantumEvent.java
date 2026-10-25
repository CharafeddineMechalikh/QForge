package com.mechalikh.qforge.events;

import java.util.Locale;
import com.mechalikh.qforge.core.SimulationContext;
import com.mechalikh.qforge.operations.QuantumOperation;
import com.mechalikh.qforge.state.Qubit;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;

/**
 * A schedulable simulation event with an identifier, scheduled time, priority, type, optional
 * operation, and target qubits.
 *
 * <p>Ordering is defined by {@link #compareTo(QuantumEvent)}: scheduled time ascending, then
 * priority descending, then a per-instance sequence number ascending. The sequence number is
 * assigned from a static counter when the event is constructed, so events with equal time and
 * priority are dequeued in construction order, which is first-in, first-out for otherwise
 * equal events.
 *
 * <p>{@link #equals(Object)} and {@link #hashCode()} use only the ID. The natural ordering is
 * therefore not consistent with equals for distinct events that share an ID, although the
 * unique sequence number keeps such events separate in a priority queue.
 */
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
    
    /**
     * Creates an event and reconciles its target qubit list with the operation metadata.
     *
     * <p>The ID, scheduled time, priority, and type are required. The operation may be
     * {@code null}. A {@code null} target array is stored as an empty array, a {@code null}
     * description is stored as an empty string, and the target array is cloned.
     *
     * <p>When the operation has target or control qubits, the target array is replaced by the
     * operation targets followed by the controls if the supplied array is empty, contains a
     * qubit not used by the operation, omits one of the operation targets, or is shorter than
     * the operation target list. Otherwise the supplied array is kept.
     *
     * @param id unique event identifier
     * @param scheduledTime time at which the event is scheduled
     * @param priority event priority
     * @param type event type
     * @param operation operation to execute, or {@code null} for a metadata-only event
     * @param targetQubits target qubits to report, or {@code null} to derive them
     * @param description human readable description, or {@code null} for an empty string
     * @throws NullPointerException if {@code id}, {@code scheduledTime}, {@code priority}, or
     *         {@code type} is {@code null}
     */
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
    
    /**
     * Executes the bound operation exactly once.
     *
     * <p>When the operation is non-null it is applied through
     * {@link SimulationContext#applyOperation(QuantumOperation)}. A successful run records an
     * {@link ExecutionResult} with the execution start time and elapsed duration. If the
     * context throws, a failure result is recorded and the exception is rethrown wrapped in an
     * {@link EventExecutionException}. The event is marked executed in a {@code finally}
     * block, so it cannot be executed again even after a failure.
     *
     * @param context context that applies the operation
     * @throws IllegalStateException if the event has already been executed
     * @throws EventExecutionException if applying the operation throws
     * @throws NullPointerException if the operation is non-null and {@code context} is null
     */
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
    
    /**
     * Returns the event identifier.
     *
     * @return the identifier supplied at construction
     */
    public String getId() {
        return id;
    }
    
    /**
     * Returns the time at which this event is scheduled.
     *
     * @return the scheduled time
     */
    public Instant getScheduledTime() {
        return scheduledTime;
    }
    
    /**
     * Returns the event priority.
     *
     * @return the priority supplied at construction
     */
    public EventPriority getPriority() {
        return priority;
    }
    
    /**
     * Returns the event type.
     *
     * @return the type supplied at construction
     */
    public EventType getType() {
        return type;
    }
    
    /**
     * Returns the operation executed by this event.
     *
     * @return the bound operation, or {@code null} for a metadata-only event
     */
    public QuantumOperation getOperation() {
        return operation;
    }
    
    /**
     * Returns a copy of the target qubit array.
     *
     * @return a new array containing the target qubits
     */
    public Qubit[] getTargetQubits() {
        return targetQubits.clone();
    }
    
    /**
     * Returns the human readable description.
     *
     * @return the description, which is never {@code null}
     */
    public String getDescription() {
        return description;
    }
    
    /**
     * Returns the wall clock time at which execution started.
     *
     * @return the execution start time, or {@code null} if the event has not been executed
     */
    public Instant getExecutionTime() {
        return executionTime;
    }
    
    /**
     * Tests whether execution has been attempted.
     *
     * @return {@code true} if execution ran, including a run that failed
     */
    public boolean isExecuted() {
        return executed;
    }
    
    /**
     * Returns the outcome of the last execution attempt.
     *
     * @return the execution result, or {@code null} if the event has not been executed
     */
    public ExecutionResult getExecutionResult() {
        return executionResult;
    }
    
    /**
     * Returns the wall clock delay between the scheduled time and the execution start.
     *
     * @return the delay, or {@code null} if the event has not been executed; the value is
     *         negative when execution started before the scheduled time
     */
    public Duration getDelay() {
        if (executionTime == null) {
            return null;
        }
        return Duration.between(scheduledTime, executionTime);
    }
    
    /**
     * Tests whether the scheduled time is earlier than the supplied time.
     *
     * @param currentTime time to compare with the scheduled time
     * @return {@code true} if the scheduled time is strictly before {@code currentTime}
     */
    public boolean isOverdue(Instant currentTime) {
        return scheduledTime.isBefore(currentTime);
    }
    
    /**
     * Compares events for scheduling order.
     *
     * <p>Scheduled time is compared first in ascending order. Events with the same time are
     * ordered by priority in descending order, so a smaller
     * {@link EventPriority#getValue()} comes first. Events with the same time and priority are
     * ordered by their construction sequence in ascending order, so earlier-created events
     * are dispatched first.
     *
     * @param other event to compare with
     * @return a negative integer, zero, or a positive integer as this event is ordered before,
     *         at the same position as, or after {@code other}
     */
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
    
    /**
     * Compares this event with another object by ID.
     *
     * @param obj object to compare with
     * @return {@code true} if {@code obj} is a {@code QuantumEvent} with the same ID
     */
    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        
        QuantumEvent other = (QuantumEvent) obj;
        return id.equals(other.id);
    }
    
    /**
     * Returns a hash code derived from the event ID.
     *
     * @return the hash code of the ID
     */
    @Override
    public int hashCode() {
        return id.hashCode();
    }
    
    /**
     * Returns a formatted description of this event.
     *
     * @return a string containing the ID, scheduled time, priority, type, operation name, and
     *         target qubits
     */
    @Override
    public String toString() {
        return String.format(Locale.ROOT, 
            "QuantumEvent[id='%s', time=%s, priority=%s, type=%s, operation=%s, targets=%s]",
            id, scheduledTime, priority, type, operation != null ? operation.getName() : "none",
            Arrays.toString(targetQubits)
        );
    }
    
    /**
     * Fluent builder for {@link QuantumEvent} instances.
     *
     * <p>The ID is required at construction. Scheduled time defaults to {@link Instant#now()},
     * priority defaults to {@link EventPriority#NORMAL}, and type defaults to
     * {@link EventType#GATE_OPERATION}. The operation, target qubits, and description remain
     * unset until supplied.
     */
    public static class Builder {
        private String id;
        private Instant scheduledTime;
        private EventPriority priority = EventPriority.NORMAL;
        private EventType type = EventType.GATE_OPERATION;
        private QuantumOperation operation;
        private Qubit[] targetQubits;
        private String description;
        
        /**
         * Creates a builder for an event with the given ID and the current instant as the
         * default scheduled time.
         *
         * @param id event identifier
         */
        public Builder(String id) {
            this.id = id;
            this.scheduledTime = Instant.now();
        }
        
        /**
         * Sets the scheduled time.
         *
         * @param time scheduled time
         * @return this builder
         */
        public Builder withScheduledTime(Instant time) {
            this.scheduledTime = time;
            return this;
        }
        
        /**
         * Sets the priority.
         *
         * @param priority event priority
         * @return this builder
         */
        public Builder withPriority(EventPriority priority) {
            this.priority = priority;
            return this;
        }
        
        /**
         * Sets the event type.
         *
         * @param type event type
         * @return this builder
         */
        public Builder withType(EventType type) {
            this.type = type;
            return this;
        }
        
        /**
         * Sets the operation.
         *
         * @param operation operation to execute, or {@code null} for a metadata-only event
         * @return this builder
         */
        public Builder withOperation(QuantumOperation operation) {
            this.operation = operation;
            return this;
        }
        
        /**
         * Sets the target qubits.
         *
         * @param targetQubits target qubits
         * @return this builder
         */
        public Builder withTargetQubits(Qubit... targetQubits) {
            this.targetQubits = targetQubits;
            return this;
        }
        
        /**
         * Sets the description.
         *
         * @param description human readable description
         * @return this builder
         */
        public Builder withDescription(String description) {
            this.description = description;
            return this;
        }
        
        /**
         * Builds the event from the current builder state.
         *
         * @return a new event
         * @throws NullPointerException if the ID, scheduled time, priority, or type is null
         */
        public QuantumEvent build() {
            return new QuantumEvent(id, scheduledTime, priority, type, operation, 
                                  targetQubits, description);
        }
    }
    
    /**
     * Runtime exception thrown when an event operation fails during execution.
     */
    public static class EventExecutionException extends RuntimeException {
        /**
         * Creates an exception with a detail message.
         *
         * @param message detail message
         */
        public EventExecutionException(String message) {
            super(message);
        }
        
        /**
         * Creates an exception with a detail message and a cause.
         *
         * @param message detail message
         * @param cause underlying failure
         */
        public EventExecutionException(String message, Throwable cause) {
            super(message, cause);
        }
    }
    
    /**
     * Immutable outcome of a single event execution.
     */
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
        
        /**
         * Creates a successful result.
         *
         * @param executionTime execution start time
         * @param duration elapsed execution duration
         * @param message detail message
         * @return a successful result with no error
         */
        public static ExecutionResult success(Instant executionTime, Duration duration, String message) {
            return new ExecutionResult(executionTime, duration, true, message, null);
        }
        
        /**
         * Creates a failed result.
         *
         * @param executionTime execution start time
         * @param duration elapsed execution duration
         * @param message detail message
         * @param error underlying failure
         * @return a failed result carrying the error
         */
        public static ExecutionResult failure(Instant executionTime, Duration duration,
                                            String message, Throwable error) {
            return new ExecutionResult(executionTime, duration, false, message, error);
        }
        
        /**
         * Returns the execution start time.
         *
         * @return the execution start time
         */
        public Instant getExecutionTime() { return executionTime; }
        /**
         * Returns the elapsed execution duration.
         *
         * @return the execution duration
         */
        public Duration getExecutionDuration() { return executionDuration; }
        /**
         * Tests whether execution succeeded.
         *
         * @return {@code true} if execution succeeded
         */
        public boolean isSuccess() { return success; }
        /**
         * Returns the detail message.
         *
         * @return the detail message
         */
        public String getMessage() { return message; }
        /**
         * Returns the underlying failure.
         *
         * @return the error, or {@code null} for a successful result
         */
        public Throwable getError() { return error; }
        
        /**
         * Returns a formatted description of this result.
         *
         * @return a string containing the success flag, duration, and message
         */
        @Override
        public String toString() {
            return String.format(Locale.ROOT, 
                "ExecutionResult[success=%s, duration=%s, message='%s']",
                success, executionDuration, message
            );
        }
    }
}