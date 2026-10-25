package com.quantum.simulator.classical;

import java.util.Locale;
import com.quantum.simulator.events.QuantumEvent;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ControlDecision {
    private final String decisionId;
    private final DecisionAction action;
    private final String description;
    private final int priority;
    private final QuantumEvent event;
    private final List<QuantumEvent> events;
    private final String eventId;
    private final Instant newTime;
    private final Map<String, Object> parameters;
    private final Instant timestamp;
    
    private ControlDecision(Builder builder) {
        this.decisionId = builder.decisionId;
        this.action = builder.action;
        this.description = builder.description;
        this.priority = builder.priority;
        this.event = builder.event != null
            ? builder.event
            : (builder.events != null && !builder.events.isEmpty() ? builder.events.get(0) : null);
        this.events = builder.events != null
            ? new ArrayList<>(builder.events)
            : (builder.event != null ? List.of(builder.event) : List.of());
        this.eventId = builder.eventId;
        this.newTime = builder.newTime;
        this.parameters = builder.parameters;
        this.timestamp = Instant.now();
    }
    
    public String getDecisionId() {
        return decisionId;
    }
    
    public DecisionAction getAction() {
        return action;
    }
    
    public String getDescription() {
        return description;
    }
    
    public int getPriority() {
        return priority;
    }
    
    public QuantumEvent getEvent() {
        return event;
    }
    
    public List<QuantumEvent> getEvents() {
        return new ArrayList<>(events);
    }
    
    public String getEventId() {
        return eventId;
    }
    
    public Instant getNewTime() {
        return newTime;
    }
    
    public Map<String, Object> getParameters() {
        return new HashMap<>(parameters);
    }
    
    public Instant getTimestamp() {
        return timestamp;
    }
    
    public static ControlDecision createScheduleDecision(String description, int priority, QuantumEvent event) {
        return new Builder("schedule_" + System.currentTimeMillis())
            .withAction(DecisionAction.SCHEDULE_EVENT)
            .withDescription(description)
            .withPriority(priority)
            .withEvent(event)
            .build();
    }
    
    public static ControlDecision createScheduleDecisions(String description, int priority,
                                                         List<QuantumEvent> events) {
        return new Builder("schedule_" + System.currentTimeMillis())
            .withAction(DecisionAction.SCHEDULE_EVENT)
            .withDescription(description)
            .withPriority(priority)
            .withEvents(events)
            .build();
    }
    
    public static ControlDecision createCancelDecision(String eventId, String description) {
        return new Builder("cancel_" + System.currentTimeMillis())
            .withAction(DecisionAction.CANCEL_EVENT)
            .withDescription(description)
            .withEventId(eventId)
            .build();
    }
    
    @Override
    public String toString() {
        return String.format(Locale.ROOT, 
            "ControlDecision[id='%s', action=%s, priority=%d, desc='%s']",
            decisionId, action, priority, description
        );
    }
    
    public static class Builder {
        private final String decisionId;
        private DecisionAction action = DecisionAction.NO_ACTION;
        private String description = "";
        private int priority = 50;
        private QuantumEvent event = null;
        private List<QuantumEvent> events = null;
        private String eventId = null;
        private Instant newTime = null;
        private Map<String, Object> parameters = new HashMap<>();
        
        public Builder(String decisionId) {
            this.decisionId = decisionId;
        }
        
        public Builder withAction(DecisionAction action) {
            this.action = action;
            return this;
        }
        
        public Builder withDescription(String description) {
            this.description = description;
            return this;
        }
        
        public Builder withPriority(int priority) {
            this.priority = priority;
            return this;
        }
        
        public Builder withEvent(QuantumEvent event) {
            this.event = event;
            return this;
        }
        
        public Builder withEvents(List<QuantumEvent> events) {
            this.events = events;
            return this;
        }
        
        public Builder withEventId(String eventId) {
            this.eventId = eventId;
            return this;
        }
        
        public Builder withNewTime(Instant newTime) {
            this.newTime = newTime;
            return this;
        }
        
        public Builder withParameter(String key, Object value) {
            this.parameters.put(key, value);
            return this;
        }
        
        public Builder withParameters(Map<String, Object> parameters) {
            this.parameters.putAll(parameters);
            return this;
        }
        
        public ControlDecision build() {
            return new ControlDecision(this);
        }
    }
    
    public enum DecisionAction {
        SCHEDULE_EVENT,
        CANCEL_EVENT,
        RESCHEDULE_EVENT,
        MODIFY_PARAMETERS,
        NO_ACTION
    }
}