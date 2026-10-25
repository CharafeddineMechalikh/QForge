package com.mechalikh.qforge.events;

import com.mechalikh.qforge.core.SimulationContext;
import com.mechalikh.qforge.observer.SimulationObserver;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Dispatches {@link QuantumEvent} instances from an {@link EventQueue} and notifies registered
 * {@link EventObserver} instances about dispatcher and event activity.
 *
 * <p>The dispatcher tracks running and paused state with atomic flags, and counts successful
 * and failed dispatch attempts. Listeners are held in a {@link CopyOnWriteArrayList}:
 * registration appends, so registering the same observer more than once causes duplicate
 * notifications, and unregistering removes one occurrence.
 *
 * <p>{@link #dispatchAll(SimulationContext)} records the start time used by
 * {@link #getUptime()} and processes events until the queue is empty, the dispatcher is
 * stopped or paused, or {@link SimulationContext#shouldStop()} returns {@code true}.
 */
public class EventDispatcher {
    private final EventQueue eventQueue;
    private final List<EventObserver> observers;
    private final AtomicBoolean isRunning;
    private final AtomicBoolean isPaused;
    private Instant startTime;
    private long eventsDispatched;
    private long eventsFailed;
    private SimulationContext currentContext;
    
    /**
     * Creates a dispatcher with an empty queue and no registered observers.
     */
    public EventDispatcher() {
        this.eventQueue = new EventQueue();
        this.observers = new CopyOnWriteArrayList<>();
        this.isRunning = new AtomicBoolean(false);
        this.isPaused = new AtomicBoolean(false);
        this.eventsDispatched = 0;
        this.eventsFailed = 0;
    }
    
    /**
     * Removes and executes the next event from the queue.
     *
     * <p>If the queue is empty, listeners receive
     * {@link EventObserver#onQueueEmpty(EventDispatcher)} and the method returns {@code null}.
     * Otherwise listeners receive
     * {@link EventObserver#onBeforeEventExecution(EventDispatcher, QuantumEvent)}, the event is
     * executed, and a successful run increments the dispatched count and notifies
     * {@link EventObserver#onAfterEventExecution}. A failed run increments the failed count
     * and notifies
     * {@link EventObserver#onEventExecutionFailed(EventDispatcher, QuantumEvent, Exception)}.
     * The failure is rethrown when {@link #shouldPropagateExceptions()} returns {@code true},
     * which is the default; otherwise the method returns {@code null}.
     *
     * @param context context passed to {@link QuantumEvent#execute(SimulationContext)}
     * @return the executed event, or {@code null} if the queue was empty or a failure was
     *         suppressed
     * @throws IllegalStateException if the dispatcher is paused
     * @throws RuntimeException if execution or an observer callback throws and exception
     *         propagation is enabled
     */
    public QuantumEvent dispatchNextEvent(SimulationContext context) {
        if (isPaused.get()) {
            throw new IllegalStateException("Event dispatcher is paused");
        }
        
        if (eventQueue.isEmpty()) {
            notifyObserversQueueEmpty();
            return null;
        }
        
        QuantumEvent event = eventQueue.getNextEvent();
        if (event == null) {
            return null;
        }
        
        this.currentContext = context;
        
        try {
            // Notify observers before execution
            notifyObserversBeforeExecution(event);
            
            // Execute the event
            Instant executionStart = Instant.now();
            event.execute(context);
            Duration executionTime = Duration.between(executionStart, Instant.now());
            
            eventsDispatched++;
            
            // Notify observers after successful execution
            notifyObserversAfterExecution(event, executionTime, null);
            
            return event;
            
        } catch (Exception e) {
            eventsFailed++;
            
            // Notify observers about failure
            notifyObserversExecutionFailed(event, e);
            
            // Optionally rethrow or handle the exception
            if (shouldPropagateExceptions()) {
                throw e;
            }
            return null;
        } finally {
            this.currentContext = null;
        }
    }
    
 // In the dispatchAll method, add synchronization:
    /**
     * Runs the dispatcher until a stop condition is met.
     *
     * <p>Only one run may be active at a time. A run records the start time, notifies
     * {@link EventObserver#onDispatcherStarted(EventDispatcher)}, and then repeatedly
     * dispatches events while the queue is non-empty, the dispatcher remains running, and it
     * is not paused. The loop also ends when {@link SimulationContext#shouldStop()} returns
     * {@code true}. When the loop ends normally, listeners receive
     * {@link EventObserver#onDispatcherCompleted(EventDispatcher)}, and the running flag is
     * cleared in a {@code finally} block.
     *
     * @param context context used for every dispatched event
     * @throws IllegalStateException if the dispatcher is already running
     * @throws RuntimeException if dispatching an event fails and exception propagation is
     *         enabled
     */
    public void dispatchAll(SimulationContext context) {
        if (isRunning.getAndSet(true)) {
            throw new IllegalStateException("Event dispatcher is already running");
        }
        
        try {
            startTime = Instant.now();
            notifyObserversStarted();
            
            synchronized (this) {
                while (!eventQueue.isEmpty() && isRunning.get() && !isPaused.get()) {
                    dispatchNextEvent(context);
                    
                    // Check for stop conditions
                    if (context.shouldStop()) {
                        break;
                    }
                }
            }
            
            notifyObserversCompleted();
            
        } finally {
            isRunning.set(false);
        }
    }
    
    /**
     * Adds one event to the queue and notifies listeners.
     *
     * <p>Listeners receive {@link EventObserver#onEventScheduled(EventDispatcher, QuantumEvent)}
     * only after the event has been added successfully.
     *
     * @param event event to schedule
     * @throws NullPointerException if {@code event} is null
     * @throws IllegalArgumentException if an event with the same ID is already queued
     */
    public void scheduleEvent(QuantumEvent event) {
        eventQueue.addEvent(event);
        notifyObserversEventScheduled(event);
    }
    
    /**
     * Schedules each event in the list, in list order.
     *
     * <p>Events are added one at a time. If an addition fails, previously added events remain
     * queued and later events are not scheduled.
     *
     * @param events events to schedule
     * @throws NullPointerException if an event is null
     * @throws IllegalArgumentException if an event ID is already queued
     */
    public void scheduleEvents(List<QuantumEvent> events) {
        for (QuantumEvent event : events) {
            scheduleEvent(event);
        }
    }
    
    /**
     * Sets the paused flag and notifies listeners.
     *
     * <p>An event that is already executing is not interrupted; the dispatcher checks the
     * paused state between events.
     */
    public void pause() {
        isPaused.set(true);
        notifyObserversPaused();
    }
    
    /**
     * Clears the paused flag and notifies listeners.
     */
    public void resume() {
        isPaused.set(false);
        notifyObserversResumed();
    }
    
    /**
     * Clears the running flag and notifies listeners.
     *
     * <p>Queued events are not removed.
     */
    public void stop() {
        isRunning.set(false);
        notifyObserversStopped();
    }
    
    /**
     * Removes all queued events and notifies listeners.
     */
    public void clear() {
        eventQueue.clear();
        notifyObserversCleared();
    }
    
    /**
     * Registers an observer.
     *
     * <p>Registration appends to the listener list. The same observer may be registered more
     * than once and then receives each notification once per registration.
     *
     * @param observer observer to add
     */
    public void registerObserver(EventObserver observer) {
        observers.add(observer);
    }
    
    /**
     * Unregisters one registration of an observer.
     *
     * <p>If the observer was registered more than once, only one registration is removed. If
     * it was not registered, the call has no effect.
     *
     * @param observer observer to remove
     */
    public void unregisterObserver(EventObserver observer) {
        observers.remove(observer);
    }
    
    /**
     * Tests whether the queue is empty.
     *
     * @return {@code true} if the queue contains no events
     */
    public boolean isQueueEmpty() {
        return eventQueue.isEmpty();
    }
    
    /**
     * Tests whether the dispatcher is running.
     *
     * @return the current value of the running flag
     */
    public boolean isRunning() {
        return isRunning.get();
    }
    
    /**
     * Tests whether the dispatcher is paused.
     *
     * @return the current value of the paused flag
     */
    public boolean isPaused() {
        return isPaused.get();
    }
    
    /**
     * Returns the scheduled time of the next queued event.
     *
     * @return the next scheduled time, or {@code null} if the queue is empty
     */
    public Instant getNextEventTime() {
        return eventQueue.peekNextTime();
    }
    
    /**
     * Returns the number of queued events.
     *
     * @return the current queue size
     */
    public int getQueueSize() {
        return eventQueue.size();
    }
    
    /**
     * Returns the number of dispatch attempts whose event execution returned normally.
     *
     * <p>The count is incremented after {@link QuantumEvent#execute(SimulationContext)}
     * returns and before the after-execution observers run.
     *
     * @return the count of events that executed without throwing
     */
    public long getEventsDispatched() {
        return eventsDispatched;
    }
    
    /**
     * Returns the number of dispatch attempts that threw.
     *
     * <p>This includes exceptions thrown by event execution and by observer callbacks that
     * run inside the dispatch attempt.
     *
     * @return the count of caught failures
     */
    public long getEventsFailed() {
        return eventsFailed;
    }
    
    /**
     * Returns the ratio of successful executions to dispatched executions.
     *
     * <p>If nothing has been dispatched, the method returns {@code 1.0}. Failures are counted
     * separately and are not part of the denominator, so the value is
     * {@code (eventsDispatched - eventsFailed) / eventsDispatched} after at least one
     * dispatch.
     *
     * @return the success rate, or {@code 1.0} when no event has been dispatched
     */
    public double getSuccessRate() {
        if (eventsDispatched == 0) {
            return 1.0;
        }
        return (double) (eventsDispatched - eventsFailed) / eventsDispatched;
    }
    
    /**
     * Returns a snapshot of the queue statistics.
     *
     * @return the statistics from the underlying queue
     */
    public EventQueue.QueueStatistics getQueueStatistics() {
        return eventQueue.getStatistics();
    }
    
    /**
     * Returns the time elapsed since the last call to {@link #dispatchAll(SimulationContext)}
     * recorded a start time.
     *
     * @return the elapsed time, or {@link Duration#ZERO} if no run has started
     */
    public Duration getUptime() {
        if (startTime == null) {
            return Duration.ZERO;
        }
        return Duration.between(startTime, Instant.now());
    }
    
    /**
     * Returns up to {@code limit} queued events.
     *
     * <p>Events are returned in the iterator order of the underlying priority queue, which
     * does not guarantee priority order.
     *
     * @param limit maximum number of events to return
     * @return a list of up to {@code limit} queued events
     */
    public List<QuantumEvent> getUpcomingEvents(int limit) {
        return eventQueue.getUpcomingEvents(limit);
    }
    
    /**
     * Returns the context of the event currently being dispatched.
     *
     * @return the context passed to the active dispatch call, or {@code null} when no event is
     *         being dispatched
     */
    public SimulationContext getCurrentContext() {
        return currentContext;
    }
    
    /**
     * Decides whether a failed dispatch rethrows its exception.
     *
     * <p>This implementation returns {@code true}. Subclasses may override it to suppress
     * failures, in which case {@link #dispatchNextEvent(SimulationContext)} returns
     * {@code null} after notifying listeners.
     *
     * @return {@code true} if dispatch failures should be rethrown
     */
    protected boolean shouldPropagateExceptions() {
        return true; // Can be overridden by subclasses
    }
    
    // Observer notification methods
    private void notifyObserversStarted() {
        for (EventObserver observer : observers) {
            observer.onDispatcherStarted(this);
        }
    }
    
    private void notifyObserversCompleted() {
        for (EventObserver observer : observers) {
            observer.onDispatcherCompleted(this);
        }
    }
    
    private void notifyObserversPaused() {
        for (EventObserver observer : observers) {
            observer.onDispatcherPaused(this);
        }
    }
    
    private void notifyObserversResumed() {
        for (EventObserver observer : observers) {
            observer.onDispatcherResumed(this);
        }
    }
    
    private void notifyObserversStopped() {
        for (EventObserver observer : observers) {
            observer.onDispatcherStopped(this);
        }
    }
    
    private void notifyObserversCleared() {
        for (EventObserver observer : observers) {
            observer.onQueueCleared(this);
        }
    }
    
    private void notifyObserversQueueEmpty() {
        for (EventObserver observer : observers) {
            observer.onQueueEmpty(this);
        }
    }
    
    private void notifyObserversEventScheduled(QuantumEvent event) {
        for (EventObserver observer : observers) {
            observer.onEventScheduled(this, event);
        }
    }
    
    private void notifyObserversBeforeExecution(QuantumEvent event) {
        for (EventObserver observer : observers) {
            observer.onBeforeEventExecution(this, event);
        }
    }
    
    private void notifyObserversAfterExecution(QuantumEvent event, Duration executionTime, 
                                              QuantumEvent.ExecutionResult result) {
        for (EventObserver observer : observers) {
            observer.onAfterEventExecution(this, event, executionTime, result);
        }
    }
    
    private void notifyObserversExecutionFailed(QuantumEvent event, Exception error) {
        for (EventObserver observer : observers) {
            observer.onEventExecutionFailed(this, event, error);
        }
    }
    
    /**
     * Returns a formatted description of this dispatcher.
     *
     * @return a string containing the running and paused flags, queue size, dispatched and
     *         failed counts, and success rate as a percentage
     */
    @Override
    public String toString() {
        return String.format(Locale.ROOT, 
            "EventDispatcher[running=%s, paused=%s, queue=%d, dispatched=%d, failed=%d, successRate=%.2f%%]",
            isRunning.get(), isPaused.get(), getQueueSize(), eventsDispatched, 
            eventsFailed, getSuccessRate() * 100
        );
    }
    
    /**
     * Listener interface for dispatcher and queue activity.
     *
     * <p>Every method has an empty default implementation, so implementations override only
     * the callbacks they need. The dispatcher invokes callbacks on the thread that performs
     * the corresponding operation.
     */
    public interface EventObserver {
        /**
         * Called when a run starts, before any event is dispatched.
         *
         * @param dispatcher dispatcher that started
         */
        default void onDispatcherStarted(EventDispatcher dispatcher) {}
        /**
         * Called after a run loop ends, including runs ended by a stop request or pause.
         *
         * @param dispatcher dispatcher that completed the loop
         */
        default void onDispatcherCompleted(EventDispatcher dispatcher) {}
        /**
         * Called when the dispatcher is paused.
         *
         * @param dispatcher dispatcher that was paused
         */
        default void onDispatcherPaused(EventDispatcher dispatcher) {}
        /**
         * Called when the dispatcher is resumed.
         *
         * @param dispatcher dispatcher that was resumed
         */
        default void onDispatcherResumed(EventDispatcher dispatcher) {}
        /**
         * Called when the dispatcher is stopped.
         *
         * @param dispatcher dispatcher that was stopped
         */
        default void onDispatcherStopped(EventDispatcher dispatcher) {}
        /**
         * Called when the queue is cleared.
         *
         * @param dispatcher dispatcher whose queue was cleared
         */
        default void onQueueCleared(EventDispatcher dispatcher) {}
        /**
         * Called when a dispatch request finds an empty queue.
         *
         * @param dispatcher dispatcher with the empty queue
         */
        default void onQueueEmpty(EventDispatcher dispatcher) {}
        /**
         * Called after an event has been added to the queue.
         *
         * @param dispatcher dispatcher that accepted the event
         * @param event scheduled event
         */
        default void onEventScheduled(EventDispatcher dispatcher, QuantumEvent event) {}
        /**
         * Called immediately before an event is executed.
         *
         * @param dispatcher dispatcher running the event
         * @param event event about to execute
         */
        default void onBeforeEventExecution(EventDispatcher dispatcher, QuantumEvent event) {}
        /**
         * Called after an event executes successfully.
         *
         * @param dispatcher dispatcher that ran the event
         * @param event executed event
         * @param executionTime elapsed wall clock execution time
         * @param result result value passed by the dispatcher, currently always {@code null}
         */
        default void onAfterEventExecution(EventDispatcher dispatcher, QuantumEvent event, 
                                          Duration executionTime, QuantumEvent.ExecutionResult result) {}
        /**
         * Called when an event execution throws, before the exception is possibly rethrown.
         *
         * @param dispatcher dispatcher that ran the event
         * @param event event that failed
         * @param error exception thrown during execution
         */
        default void onEventExecutionFailed(EventDispatcher dispatcher, QuantumEvent event, Exception error) {}
    }
}