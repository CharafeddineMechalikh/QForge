package com.quantum.simulator.classical;

import java.util.Locale;
import com.quantum.simulator.events.QuantumEvent;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Description of an action chosen by the classical layer.
 *
 * <p>Decisions are normally created through {@link Builder} or through the static
 * factories {@link #createScheduleDecision(String, int, QuantumEvent)},
 * {@link #createScheduleDecisions(String, int, List)} and
 * {@link #createCancelDecision(String, String)}. A decision groups an action with the
 * data that action needs: events to schedule, an event id and new time for queue edits,
 * or parameters for a parameter update. The controller interprets these fields in
 * {@link ClassicalController#updateEventQueue}.
 *
 * <p>A decision created with a single event also reports that event as its primary
 * event. The timestamp is taken when the decision is constructed. The event list is
 * copied when the decision is built, but the parameter map is not, so changing the
 * builder parameters after {@link Builder#build()} also changes the decision;
 * {@link #getParameters()} still returns a copy on each call.
 */
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
    
    /**
     * Returns the decision identifier supplied by the builder.
     *
     * @return decision identifier
     */
    public String getDecisionId() {
        return decisionId;
    }
    
    /**
     * Returns the action this decision requests.
     *
     * @return decision action
     */
    public DecisionAction getAction() {
        return action;
    }
    
    /**
     * Returns the human readable explanation of the decision.
     *
     * @return decision description
     */
    public String getDescription() {
        return description;
    }
    
    /**
     * Returns the priority used when several decisions compete.
     *
     * @return decision priority, where a larger value wins
     */
    public int getPriority() {
        return priority;
    }
    
    /**
     * Returns the primary event.
     *
     * <p>This is the event supplied directly to the builder, or the first event of a
     * supplied list when no single event was set.
     *
     * @return primary event, or {@code null} when the decision carries no event
     */
    public QuantumEvent getEvent() {
        return event;
    }
    
    /**
     * Returns the events carried by the decision.
     *
     * @return copy of the event list, empty when no event was supplied
     */
    public List<QuantumEvent> getEvents() {
        return new ArrayList<>(events);
    }
    
    /**
     * Returns the identifier of the event to cancel or reschedule.
     *
     * @return target event identifier, or {@code null} when none was supplied
     */
    public String getEventId() {
        return eventId;
    }
    
    /**
     * Returns the new scheduled time for a reschedule action.
     *
     * @return new time, or {@code null} when none was supplied
     */
    public Instant getNewTime() {
        return newTime;
    }
    
    /**
     * Returns the parameters attached to the decision.
     *
     * @return copy of the parameter map, empty when no parameters were supplied
     */
    public Map<String, Object> getParameters() {
        return new HashMap<>(parameters);
    }
    
    /**
     * Returns the instant at which this decision was constructed.
     *
     * @return construction timestamp
     */
    public Instant getTimestamp() {
        return timestamp;
    }
    
    /**
     * Creates a decision that schedules a single event.
     *
     * <p>The decision id is {@code schedule_} followed by the current clock in
     * milliseconds, so decisions created within the same millisecond can share an id.
     *
     * @param description human readable explanation
     * @param priority priority used when decisions compete
     * @param event event to schedule, may be {@code null}
     * @return decision with action {@link DecisionAction#SCHEDULE_EVENT}
     */
    public static ControlDecision createScheduleDecision(String description, int priority, QuantumEvent event) {
        return new Builder("schedule_" + System.currentTimeMillis())
            .withAction(DecisionAction.SCHEDULE_EVENT)
            .withDescription(description)
            .withPriority(priority)
            .withEvent(event)
            .build();
    }
    
    /**
     * Creates a decision that schedules several events.
     *
     * <p>The first event of the list becomes the primary event returned by
     * {@link #getEvent()}. The decision id is {@code schedule_} followed by the current
     * clock in milliseconds.
     *
     * @param description human readable explanation
     * @param priority priority used when decisions compete
     * @param events events to schedule, may be {@code null} or empty
     * @return decision with action {@link DecisionAction#SCHEDULE_EVENT}
     */
    public static ControlDecision createScheduleDecisions(String description, int priority,
                                                         List<QuantumEvent> events) {
        return new Builder("schedule_" + System.currentTimeMillis())
            .withAction(DecisionAction.SCHEDULE_EVENT)
            .withDescription(description)
            .withPriority(priority)
            .withEvents(events)
            .build();
    }
    
    /**
     * Creates a decision that cancels an event.
     *
     * <p>The priority keeps the builder default of {@code 50}. The decision id is
     * {@code cancel_} followed by the current clock in milliseconds.
     *
     * @param eventId identifier of the event to cancel
     * @param description human readable explanation
     * @return decision with action {@link DecisionAction#CANCEL_EVENT}
     */
    public static ControlDecision createCancelDecision(String eventId, String description) {
        return new Builder("cancel_" + System.currentTimeMillis())
            .withAction(DecisionAction.CANCEL_EVENT)
            .withDescription(description)
            .withEventId(eventId)
            .build();
    }
    
    /**
     * Returns a formatted summary of the decision.
     *
     * @return representation containing id, action, priority and description
     */
    @Override
    public String toString() {
        return String.format(Locale.ROOT, 
            "ControlDecision[id='%s', action=%s, priority=%d, desc='%s']",
            decisionId, action, priority, description
        );
    }
    
    /**
     * Mutable builder for {@link ControlDecision}.
     *
     * <p>Defaults are action {@link DecisionAction#NO_ACTION}, an empty description,
     * priority {@code 50}, no event, no event id, no new time and an empty parameter map.
     * The setter methods return this builder so calls can be chained.
     */
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
        
        /**
         * Creates a builder with the given decision identifier.
         *
         * @param decisionId identifier stored in the built decision
         */
        public Builder(String decisionId) {
            this.decisionId = decisionId;
        }
        
        /**
         * Sets the action.
         *
         * @param action requested action
         * @return this builder
         */
        public Builder withAction(DecisionAction action) {
            this.action = action;
            return this;
        }
        
        /**
         * Sets the description.
         *
         * @param description human readable explanation
         * @return this builder
         */
        public Builder withDescription(String description) {
            this.description = description;
            return this;
        }
        
        /**
         * Sets the priority.
         *
         * @param priority priority used when decisions compete
         * @return this builder
         */
        public Builder withPriority(int priority) {
            this.priority = priority;
            return this;
        }
        
        /**
         * Sets a single event.
         *
         * @param event event to carry, may be {@code null}
         * @return this builder
         */
        public Builder withEvent(QuantumEvent event) {
            this.event = event;
            return this;
        }
        
        /**
         * Sets the event list.
         *
         * <p>The list is copied when the decision is built.
         *
         * @param events events to carry, may be {@code null}
         * @return this builder
         */
        public Builder withEvents(List<QuantumEvent> events) {
            this.events = events;
            return this;
        }
        
        /**
         * Sets the target event identifier for cancel and reschedule actions.
         *
         * @param eventId target event identifier
         * @return this builder
         */
        public Builder withEventId(String eventId) {
            this.eventId = eventId;
            return this;
        }
        
        /**
         * Sets the new time for a reschedule action.
         *
         * @param newTime new scheduled time
         * @return this builder
         */
        public Builder withNewTime(Instant newTime) {
            this.newTime = newTime;
            return this;
        }
        
        /**
         * Adds or replaces one parameter.
         *
         * @param key parameter key
         * @param value parameter value
         * @return this builder
         */
        public Builder withParameter(String key, Object value) {
            this.parameters.put(key, value);
            return this;
        }
        
        /**
         * Copies all entries of a parameter map into the builder parameters.
         *
         * @param parameters parameters to add
         * @return this builder
         */
        public Builder withParameters(Map<String, Object> parameters) {
            this.parameters.putAll(parameters);
            return this;
        }
        
        /**
         * Builds a decision from the current builder state.
         *
         * @return new immutable decision with a fresh timestamp
         */
        public ControlDecision build() {
            return new ControlDecision(this);
        }
    }
    
    /**
     * Action requested by a {@link ControlDecision}.
     *
     * <p>The controller consumes these values in
     * {@link ClassicalController#updateEventQueue}.
     */
    public enum DecisionAction {
        /**
         * Add the events carried by the decision to the queue.
         */
        SCHEDULE_EVENT,
        /**
         * Remove the event named by the decision event id from the queue.
         */
        CANCEL_EVENT,
        /**
         * Move the event named by the decision event id to the decision new time.
         */
        RESCHEDULE_EVENT,
        /**
         * Copy the decision parameters into classical memory.
         */
        MODIFY_PARAMETERS,
        /**
         * Do nothing.
         */
        NO_ACTION
    }
}