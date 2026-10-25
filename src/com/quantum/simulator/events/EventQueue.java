package com.quantum.simulator.events;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.PriorityBlockingQueue;
import java.util.concurrent.locks.ReentrantLock;

public class EventQueue {
    private final PriorityBlockingQueue<QuantumEvent> queue;
    private final Map<String, QuantumEvent> eventMap;
    private final ReentrantLock lock;
    private Instant currentTime;
    private long totalEventsProcessed;
    private long maxQueueSize;
    
    public EventQueue() {
        this.queue = new PriorityBlockingQueue<>();
        this.eventMap = new HashMap<>();
        this.lock = new ReentrantLock();
        this.currentTime = Instant.now();
        this.totalEventsProcessed = 0;
        this.maxQueueSize = 0;
    }
    
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
    
    public QuantumEvent peekNextEvent() {
        return queue.peek();
    }
    
    public Instant peekNextTime() {
        QuantumEvent next = queue.peek();
        return next != null ? next.getScheduledTime() : null;
    }
    
    public boolean isEmpty() {
        return queue.isEmpty();
    }
    
    public int size() {
        return queue.size();
    }
    
    public void clear() {
        lock.lock();
        try {
            queue.clear();
            eventMap.clear();
        } finally {
            lock.unlock();
        }
    }
    
    public boolean containsEvent(String eventId) {
        lock.lock();
        try {
            return eventMap.containsKey(eventId);
        } finally {
            lock.unlock();
        }
    }
    
    public QuantumEvent getEvent(String eventId) {
        lock.lock();
        try {
            return eventMap.get(eventId);
        } finally {
            lock.unlock();
        }
    }
    
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
    
    public Instant getCurrentTime() {
        return currentTime;
    }
    
    public long getTotalEventsProcessed() {
        return totalEventsProcessed;
    }
    
    public long getMaxQueueSize() {
        return maxQueueSize;
    }
    
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
    
    @Override
    public String toString() {
        return String.format(Locale.ROOT, 
            "EventQueue[size=%d, processed=%d, nextTime=%s]",
            size(), totalEventsProcessed, peekNextTime()
        );
    }
    
    // Inner class for statistics
    public static class QueueStatistics {
        private final int currentSize;
        private final long totalProcessed;
        private final long maxSize;
        private final Instant currentTime;
        
        public QueueStatistics(int currentSize, long totalProcessed, long maxSize, Instant currentTime) {
            this.currentSize = currentSize;
            this.totalProcessed = totalProcessed;
            this.maxSize = maxSize;
            this.currentTime = currentTime;
        }
        
        public int getCurrentSize() { return currentSize; }
        public long getTotalProcessed() { return totalProcessed; }
        public long getMaxSize() { return maxSize; }
        public Instant getCurrentTime() { return currentTime; }
        
        public double getAverageProcessingRate(Instant startTime) {
            if (totalProcessed == 0) return 0.0;
            
            Duration duration = Duration.between(startTime, currentTime);
            double seconds = duration.toMillis() / 1000.0;
            return seconds > 0 ? totalProcessed / seconds : 0.0;
        }
        
        @Override
        public String toString() {
            return String.format(Locale.ROOT, 
                "QueueStats[size=%d, processed=%d, maxSize=%d, time=%s]",
                currentSize, totalProcessed, maxSize, currentTime
            );
        }
    }
}