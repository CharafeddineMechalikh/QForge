package com.mechalikh.qforge.events;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.PriorityBlockingQueue;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Priority queue of pending {@link QuantumEvent} instances with an ID index and bookkeeping
 * statistics.
 *
 * <p>The queue is backed by a {@link PriorityBlockingQueue}, so events are removed in the
 * natural order defined by {@link QuantumEvent#compareTo(QuantumEvent)}. A parallel map
 * indexes events by {@link QuantumEvent#getId()}, which supports lookup and removal by ID.
 * The queue rejects a second event that uses an ID already present in the index.
 *
 * <p>Mutating operations and lookups by ID acquire a {@link ReentrantLock}. The
 * {@link #peekNextEvent()}, {@link #peekNextTime()}, {@link #isEmpty()}, and {@link #size()}
 * methods read the queue without taking that lock.
 */
public class EventQueue {
    private final PriorityBlockingQueue<QuantumEvent> queue;
    private final Map<String, QuantumEvent> eventMap;
    private final ReentrantLock lock;
    private Instant currentTime;
    private long totalEventsProcessed;
    private long maxQueueSize;
    
    /**
     * Creates an empty queue whose current time is the instant of construction.
     */
    public EventQueue() {
        this.queue = new PriorityBlockingQueue<>();
        this.eventMap = new HashMap<>();
        this.lock = new ReentrantLock();
        this.currentTime = Instant.now();
        this.totalEventsProcessed = 0;
        this.maxQueueSize = 0;
    }
    
    /**
     * Adds an event to the queue and records it in the ID index.
     *
     * <p>The maximum observed queue size is updated after a successful insertion.
     *
     * @param event event to add
     * @throws NullPointerException if {@code event} is null
     * @throws IllegalArgumentException if an event with the same ID is already indexed
     */
    public void addEvent(QuantumEvent event) {
        Objects.requireNonNull(event, "Event cannot be null");
        
        lock.lock();
        try {
            if (eventMap.containsKey(event.getId())) {
                throw new IllegalArgumentException("Event with ID '" + event.getId() + "' already exists");
            }
            
            queue.offer(event);
            eventMap.put(event.getId(), event);
            
            // Update statistics
            maxQueueSize = Math.max(maxQueueSize, queue.size());
            
        } finally {
            lock.unlock();
        }
    }
    
    /**
     * Removes and returns the next event in priority order.
     *
     * <p>On success the ID index entry is removed, the current time is set to the event
     * scheduled time, and the processed count is incremented.
     *
     * @return the next event, or {@code null} if the queue is empty
     */
    public QuantumEvent getNextEvent() {
        lock.lock();
        try {
            QuantumEvent event = queue.poll();
            if (event != null) {
                eventMap.remove(event.getId());
                currentTime = event.getScheduledTime();
                totalEventsProcessed++;
            }
            return event;
        } finally {
            lock.unlock();
        }
    }
    
    /**
     * Returns the next event without removing it.
     *
     * @return the next event, or {@code null} if the queue is empty
     */
    public QuantumEvent peekNextEvent() {
        return queue.peek();
    }
    
    /**
     * Returns the scheduled time of the next event without removing it.
     *
     * @return the next scheduled time, or {@code null} if the queue is empty
     */
    public Instant peekNextTime() {
        QuantumEvent next = queue.peek();
        return next != null ? next.getScheduledTime() : null;
    }
    
    /**
     * Tests whether the queue contains no events.
     *
     * @return {@code true} if the queue is empty
     */
    public boolean isEmpty() {
        return queue.isEmpty();
    }
    
    /**
     * Returns the number of queued events.
     *
     * @return the current queue size
     */
    public int size() {
        return queue.size();
    }
    
    /**
     * Removes all queued events and clears the ID index.
     *
     * <p>The statistics and the current time are not reset.
     */
    public void clear() {
        lock.lock();
        try {
            queue.clear();
            eventMap.clear();
        } finally {
            lock.unlock();
        }
    }
    
    /**
     * Tests whether an event with the given ID is indexed.
     *
     * @param eventId event identifier
     * @return {@code true} if the ID is present in the index
     */
    public boolean containsEvent(String eventId) {
        lock.lock();
        try {
            return eventMap.containsKey(eventId);
        } finally {
            lock.unlock();
        }
    }
    
    /**
     * Returns the indexed event with the given ID.
     *
     * @param eventId event identifier
     * @return the event, or {@code null} if no event with the ID is indexed
     */
    public QuantumEvent getEvent(String eventId) {
        lock.lock();
        try {
            return eventMap.get(eventId);
        } finally {
            lock.unlock();
        }
    }
    
    /**
     * Removes the event with the given ID from the queue and the index.
     *
     * <p>If the ID is indexed, the index entry is removed first, and the return value reports
     * whether the queue also contained the event.
     *
     * @param eventId event identifier
     * @return {@code true} if the index entry was removed and the queue also removed the event
     */
    public boolean removeEvent(String eventId) {
        lock.lock();
        try {
            QuantumEvent event = eventMap.remove(eventId);
            if (event != null) {
                return queue.remove(event);
            }
            return false;
        } finally {
            lock.unlock();
        }
    }
    
    /**
     * Returns up to {@code limit} queued events.
     *
     * <p>Events are read through the iterator of the underlying priority queue, which does not
     * guarantee priority order.
     *
     * @param limit maximum number of events to return
     * @return a list of up to {@code limit} events in iterator order
     */
    public List<QuantumEvent> getUpcomingEvents(int limit) {
        lock.lock();
        try {
            List<QuantumEvent> events = new ArrayList<>();
            Iterator<QuantumEvent> iterator = queue.iterator();
            
            while (iterator.hasNext() && events.size() < limit) {
                events.add(iterator.next());
            }
            
            return events;
        } finally {
            lock.unlock();
        }
    }
    
    /**
     * Returns all queued events of the given type.
     *
     * <p>Events are returned in the iterator order of the underlying priority queue.
     *
     * @param type event type to match
     * @return a list of matching events, possibly empty
     */
    public List<QuantumEvent> getEventsByType(EventType type) {
        lock.lock();
        try {
            List<QuantumEvent> events = new ArrayList<>();
            for (QuantumEvent event : queue) {
                if (event.getType() == type) {
                    events.add(event);
                }
            }
            return events;
        } finally {
            lock.unlock();
        }
    }
    
    /**
     * Returns all queued events whose scheduled time lies in the given range.
     *
     * <p>The range is inclusive: an event is included when its time is neither before
     * {@code startTime} nor after {@code endTime}. Events are returned in iterator order.
     *
     * @param startTime inclusive start of the time range
     * @param endTime inclusive end of the time range
     * @return a list of events in the range, possibly empty
     */
    public List<QuantumEvent> getEventsInTimeRange(Instant startTime, Instant endTime) {
        lock.lock();
        try {
            List<QuantumEvent> events = new ArrayList<>();
            for (QuantumEvent event : queue) {
                Instant eventTime = event.getScheduledTime();
                if (!eventTime.isBefore(startTime) && !eventTime.isAfter(endTime)) {
                    events.add(event);
                }
            }
            return events;
        } finally {
            lock.unlock();
        }
    }
    
    /**
     * Replaces the queued event with the given ID by a copy with a new scheduled time.
     *
     * <p>The copy preserves priority, type, operation, target qubits, and description, and it
     * receives a new construction sequence number. If the ID is indexed but the queue no
     * longer contains the event, the method returns without changing anything.
     *
     * @param eventId identifier of the event to reschedule
     * @param newTime new scheduled time
     * @throws IllegalArgumentException if no event with the ID is indexed
     * @throws NullPointerException if {@code newTime} is null and the event is found
     */
    public void rescheduleEvent(String eventId, Instant newTime) {
        lock.lock();
        try {
            QuantumEvent event = eventMap.get(eventId);
            if (event == null) {
                throw new IllegalArgumentException("Event not found: " + eventId);
            }
            
            if (queue.remove(event)) {
                QuantumEvent rescheduledEvent = new QuantumEvent.Builder(eventId)
                    .withScheduledTime(newTime)
                    .withPriority(event.getPriority())
                    .withType(event.getType())
                    .withOperation(event.getOperation())
                    .withTargetQubits(event.getTargetQubits())
                    .withDescription(event.getDescription())
                    .build();
                
                queue.offer(rescheduledEvent);
                eventMap.put(eventId, rescheduledEvent);
            }
        } finally {
            lock.unlock();
        }
    }
    
    /**
     * Returns the scheduled time of the most recently dequeued event.
     *
     * @return the current time, which is the construction instant before any event is dequeued
     */
    public Instant getCurrentTime() {
        return currentTime;
    }
    
    /**
     * Returns the number of events removed by {@link #getNextEvent()}.
     *
     * @return the total number of processed events
     */
    public long getTotalEventsProcessed() {
        return totalEventsProcessed;
    }
    
    /**
     * Returns the largest queue size observed after an insertion.
     *
     * @return the maximum recorded queue size
     */
    public long getMaxQueueSize() {
        return maxQueueSize;
    }
    
    /**
     * Returns a snapshot of the queue statistics.
     *
     * @return a new statistics object with the current size, processed count, maximum size,
     *         and current time
     */
    public QueueStatistics getStatistics() {
        lock.lock();
        try {
            return new QueueStatistics(
                queue.size(),
                totalEventsProcessed,
                maxQueueSize,
                currentTime
            );
        } finally {
            lock.unlock();
        }
    }
    
    /**
     * Returns a formatted description of this queue.
     *
     * @return a string containing the size, processed count, and next scheduled time
     */
    @Override
    public String toString() {
        return String.format(Locale.ROOT, 
            "EventQueue[size=%d, processed=%d, nextTime=%s]",
            size(), totalEventsProcessed, peekNextTime()
        );
    }
    
    /**
     * Immutable snapshot of queue counters and the current queue time.
     */
    public static class QueueStatistics {
        private final int currentSize;
        private final long totalProcessed;
        private final long maxSize;
        private final Instant currentTime;
        
        /**
         * Creates a statistics snapshot.
         *
         * @param currentSize current number of queued events
         * @param totalProcessed total number of events dequeued
         * @param maxSize maximum queue size observed after an insertion
         * @param currentTime queue current time at the snapshot
         */
        public QueueStatistics(int currentSize, long totalProcessed, long maxSize, Instant currentTime) {
            this.currentSize = currentSize;
            this.totalProcessed = totalProcessed;
            this.maxSize = maxSize;
            this.currentTime = currentTime;
        }
        
        /**
         * Returns the current queue size.
         *
         * @return the number of events queued when the snapshot was taken
         */
        public int getCurrentSize() { return currentSize; }
        /**
         * Returns the total number of processed events.
         *
         * @return the number of events dequeued when the snapshot was taken
         */
        public long getTotalProcessed() { return totalProcessed; }
        /**
         * Returns the maximum observed queue size.
         *
         * @return the largest queue size recorded when the snapshot was taken
         */
        public long getMaxSize() { return maxSize; }
        /**
         * Returns the queue time at the snapshot.
         *
         * @return the queue current time at the snapshot
         */
        public Instant getCurrentTime() { return currentTime; }
        
        /**
         * Computes the average processing rate in events per second.
         *
         * <p>The elapsed time is measured from {@code startTime} to
         * {@link #getCurrentTime()}, which is the scheduled time of the last dequeued event or
         * the queue construction instant when nothing has been dequeued.
         *
         * @param startTime reference start of the measurement interval
         * @return {@code totalProcessed / elapsedSeconds}, or {@code 0.0} when nothing has been
         *         processed or the elapsed time is not positive
         */
        public double getAverageProcessingRate(Instant startTime) {
            if (totalProcessed == 0) return 0.0;
            
            Duration duration = Duration.between(startTime, currentTime);
            double seconds = duration.toMillis() / 1000.0;
            return seconds > 0 ? totalProcessed / seconds : 0.0;
        }
        
        /**
         * Returns a formatted description of this snapshot.
         *
         * @return a string containing the size, processed count, maximum size, and time
         */
        @Override
        public String toString() {
            return String.format(Locale.ROOT, 
                "QueueStats[size=%d, processed=%d, maxSize=%d, time=%s]",
                currentSize, totalProcessed, maxSize, currentTime
            );
        }
    }
}