package com.quantum.simulator.events;

import com.quantum.simulator.core.SimulationContext;
import com.quantum.simulator.observer.SimulationObserver;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;

public class EventDispatcher {
    private final EventQueue eventQueue;
    private final List<EventObserver> observers;
    private final AtomicBoolean isRunning;
    private final AtomicBoolean isPaused;
    private Instant startTime;
    private long eventsDispatched;
    private long eventsFailed;
    private SimulationContext currentContext;
    
    public EventDispatcher() {
        this.eventQueue = new EventQueue();
        this.observers = new CopyOnWriteArrayList<>();
        this.isRunning = new AtomicBoolean(false);
        this.isPaused = new AtomicBoolean(false);
        this.eventsDispatched = 0;
        this.eventsFailed = 0;
    }
    
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
    
    public void scheduleEvent(QuantumEvent event) {
        eventQueue.addEvent(event);
        notifyObserversEventScheduled(event);
    }
    
    public void scheduleEvents(List<QuantumEvent> events) {
        for (QuantumEvent event : events) {
            scheduleEvent(event);
        }
    }
    
    public void pause() {
        isPaused.set(true);
        notifyObserversPaused();
    }
    
    public void resume() {
        isPaused.set(false);
        notifyObserversResumed();
    }
    
    public void stop() {
        isRunning.set(false);
        notifyObserversStopped();
    }
    
    public void clear() {
        eventQueue.clear();
        notifyObserversCleared();
    }
    
    public void registerObserver(EventObserver observer) {
        observers.add(observer);
    }
    
    public void unregisterObserver(EventObserver observer) {
        observers.remove(observer);
    }
    
    public boolean isQueueEmpty() {
        return eventQueue.isEmpty();
    }
    
    public boolean isRunning() {
        return isRunning.get();
    }
    
    public boolean isPaused() {
        return isPaused.get();
    }
    
    public Instant getNextEventTime() {
        return eventQueue.peekNextTime();
    }
    
    public int getQueueSize() {
        return eventQueue.size();
    }
    
    public long getEventsDispatched() {
        return eventsDispatched;
    }
    
    public long getEventsFailed() {
        return eventsFailed;
    }
    
    public double getSuccessRate() {
        if (eventsDispatched == 0) {
            return 1.0;
        }
        return (double) (eventsDispatched - eventsFailed) / eventsDispatched;
    }
    
    public EventQueue.QueueStatistics getQueueStatistics() {
        return eventQueue.getStatistics();
    }
    
    public Duration getUptime() {
        if (startTime == null) {
            return Duration.ZERO;
        }
        return Duration.between(startTime, Instant.now());
    }
    
    public List<QuantumEvent> getUpcomingEvents(int limit) {
        return eventQueue.getUpcomingEvents(limit);
    }
    
    public SimulationContext getCurrentContext() {
        return currentContext;
    }
    
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
    
    @Override
    public String toString() {
        return String.format(Locale.ROOT, 
            "EventDispatcher[running=%s, paused=%s, queue=%d, dispatched=%d, failed=%d, successRate=%.2f%%]",
            isRunning.get(), isPaused.get(), getQueueSize(), eventsDispatched, 
            eventsFailed, getSuccessRate() * 100
        );
    }
    
    // Observer interface
    public interface EventObserver {
        default void onDispatcherStarted(EventDispatcher dispatcher) {}
        default void onDispatcherCompleted(EventDispatcher dispatcher) {}
        default void onDispatcherPaused(EventDispatcher dispatcher) {}
        default void onDispatcherResumed(EventDispatcher dispatcher) {}
        default void onDispatcherStopped(EventDispatcher dispatcher) {}
        default void onQueueCleared(EventDispatcher dispatcher) {}
        default void onQueueEmpty(EventDispatcher dispatcher) {}
        default void onEventScheduled(EventDispatcher dispatcher, QuantumEvent event) {}
        default void onBeforeEventExecution(EventDispatcher dispatcher, QuantumEvent event) {}
        default void onAfterEventExecution(EventDispatcher dispatcher, QuantumEvent event, 
                                          Duration executionTime, QuantumEvent.ExecutionResult result) {}
        default void onEventExecutionFailed(EventDispatcher dispatcher, QuantumEvent event, Exception error) {}
    }
}