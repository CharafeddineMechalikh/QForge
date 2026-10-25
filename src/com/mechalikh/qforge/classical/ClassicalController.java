package com.mechalikh.qforge.classical;

import com.mechalikh.qforge.core.SimulationContext;
import com.mechalikh.qforge.events.EventQueue;
import com.mechalikh.qforge.events.QuantumEvent;
import com.mechalikh.qforge.measurement.MeasurementResult;
import com.mechalikh.qforge.operations.QuantumOperation;
import com.mechalikh.qforge.state.Qubit;
import java.util.*;
import java.util.function.Consumer;

/**
 * Bridges measurement outcomes and classical control decisions in the event driven
 * simulation.
 *
 * <p>Measurements are recorded in a {@link SimulationContext} when a measurement
 * operation is applied. {@link #processEvents(SimulationContext)} drains the results
 * that were added since its previous call, runs each result through the registered
 * {@link ControlRule} instances and keeps the highest priority {@link ControlDecision}
 * that the rules return. Decisions are appended to a history, and the events they carry
 * are handed to the scheduler installed with {@link #setEventScheduler(Consumer)} so
 * that follow up work enters the simulation event queue.
 *
 * <p>The controller also keeps a free form classical memory that rules may read and
 * write, and that {@link #executeConditional(Condition)} reads. State is held in plain
 * collections without synchronization.
 */
public class ClassicalController {
    private final List<ControlRule> controlRules;
    private final Map<String, Object> classicalMemory;
    private final List<ControlDecision> decisionHistory;
    private final Random random;
    private int decisionCount;
    private int processedMeasurements;
    private Consumer<QuantumEvent> eventScheduler;
    
    /**
     * Creates a controller with empty memory and decision history and installs the three
     * default rules: {@link ConditionalGateRule}, {@link FeedbackMeasurementRule} and
     * {@link AdaptiveNoiseRule}.
     */
    public ClassicalController() {
        this.controlRules = new ArrayList<>();
        this.classicalMemory = new HashMap<>();
        this.decisionHistory = new ArrayList<>();
        this.random = new Random();
        this.decisionCount = 0;
        this.processedMeasurements = 0;
        initializeDefaultRules();
    }
    
    /**
     * Installs the callback used to schedule events produced by control decisions.
     *
     * <p>Only {@link #processEvents(SimulationContext)} invokes this callback. Without a
     * callback, decisions are still recorded but their events are not scheduled.
     *
     * @param eventScheduler consumer that accepts events to schedule, or {@code null}
     *                       to disable event forwarding
     */
    public void setEventScheduler(Consumer<QuantumEvent> eventScheduler) {
        this.eventScheduler = eventScheduler;
    }
    
    private void initializeDefaultRules() {
        // Add some default control rules
        addRule(new ConditionalGateRule());
        addRule(new FeedbackMeasurementRule());
        addRule(new AdaptiveNoiseRule());
    }
    
    /**
     * Evaluates the enabled control rules against one measurement result.
     *
     * <p>The result is stored in classical memory under {@code last_measurement} and
     * under {@code measurement_<n>}, where {@code n} is the incremented decision counter.
     * Each enabled rule whose {@link ControlRule#appliesTo(MeasurementResult)} returns
     * {@code true} is asked to evaluate the result, and rules that return {@code null}
     * are ignored. If more than one decision remains, the decision with the highest
     * priority wins; when priorities tie, the decision from the earlier rule is kept.
     * The winner is appended to the history and returned.
     *
     * @param result measurement result to process
     * @return the selected decision, or {@code null} when no rule produced one
     */
    public ControlDecision processMeasurement(MeasurementResult result) {
        decisionCount++;
        
        // Store measurement in classical memory
        classicalMemory.put("last_measurement", result);
        classicalMemory.put("measurement_" + decisionCount, result);
        
        // Evaluate all control rules
        List<ControlDecision> decisions = new ArrayList<>();
        for (ControlRule rule : controlRules) {
            if (rule.isEnabled() && rule.appliesTo(result)) {
                ControlDecision decision = rule.evaluate(result, classicalMemory);
                if (decision != null) {
                    decisions.add(decision);
                }
            }
        }
        
        // Combine decisions if multiple rules apply
        ControlDecision finalDecision = combineDecisions(decisions);
        if (finalDecision != null) {
            decisionHistory.add(finalDecision);
        }
        
        return finalDecision;
    }
    
    /**
     * Applies a decision to an event queue.
     *
     * <p>{@link ControlDecision.DecisionAction#SCHEDULE_EVENT} adds every non-null event
     * returned by {@link ControlDecision#getEvents()}.
     * {@link ControlDecision.DecisionAction#CANCEL_EVENT} removes the event named by
     * {@link ControlDecision#getEventId()} when an id is present.
     * {@link ControlDecision.DecisionAction#RESCHEDULE_EVENT} moves that event, but only
     * when both the event id and the new time are present.
     * {@link ControlDecision.DecisionAction#MODIFY_PARAMETERS} copies the decision
     * parameters into classical memory and does not alter any queued event.
     * {@link ControlDecision.DecisionAction#NO_ACTION} does nothing.
     *
     * <p>A {@code null} decision or queue is ignored.
     *
     * @param queue queue to update
     * @param decision decision to apply, may be {@code null}
     * @throws IllegalArgumentException if the queue rejects an event insertion or the
     *                                  reschedule target is unknown
     */
    public void updateEventQueue(EventQueue queue, ControlDecision decision) {
        if (decision == null || queue == null) {
            return;
        }
        
        switch (decision.getAction()) {
            case SCHEDULE_EVENT:
                for (QuantumEvent event : decision.getEvents()) {
                    if (event != null) {
                        queue.addEvent(event);
                    }
                }
                break;
                
            case CANCEL_EVENT:
                if (decision.getEventId() != null) {
                    queue.removeEvent(decision.getEventId());
                }
                break;
                
            case RESCHEDULE_EVENT:
                if (decision.getEventId() != null && decision.getNewTime() != null) {
                    queue.rescheduleEvent(decision.getEventId(), decision.getNewTime());
                }
                break;
                
            case MODIFY_PARAMETERS:
                // Modify parameters of existing events
                modifyEventParameters(queue, decision);
                break;
                
            case NO_ACTION:
                // Do nothing
                break;
        }
    }
    
    /**
     * Evaluates a condition against classical memory and selects a quantum operation.
     *
     * <p>The value stored under {@link Condition#getParameterName()} is compared with
     * the condition comparison value. A missing value, or a value that is neither a
     * {@link Number} nor a {@link MeasurementResult}, makes the condition false. A
     * measurement result is compared through its expectation value. The boolean outcome
     * is stored under {@code last_condition_<name>}.
     *
     * @param condition condition to evaluate, may be {@code null}
     * @return the true branch operation when the condition holds, otherwise the false
     *         branch operation when one is present, otherwise {@code null}
     */
    public QuantumOperation executeConditional(Condition condition) {
        if (condition == null) {
            return null;
        }
        
        boolean conditionMet = evaluateCondition(condition);
        classicalMemory.put("last_condition_" + condition.getName(), conditionMet);
        
        if (conditionMet) {
            return condition.getOperationIfTrue();
        } else if (condition.getOperationIfFalse() != null) {
            return condition.getOperationIfFalse();
        }
        
        return null;
    }
    
    /**
     * Drains measurements newly added to the context and schedules the events of the
     * resulting decisions.
     *
     * <p>The controller remembers how many measurements it has already seen, so repeated
     * calls only handle results appended since the previous call. The events of each
     * decision are passed to the scheduler installed with
     * {@link #setEventScheduler(Consumer)} when one is present. The decision action
     * itself is not inspected here, so only the events carried by a decision matter.
     * After draining, the context step count, elapsed time in milliseconds and current
     * fidelity are copied into classical memory under {@code simulation_steps},
     * {@code simulation_time} and {@code current_fidelity}.
     *
     * <p>The processed measurement counter is never reset, so clearing the measurement
     * list of a context does not make this controller reconsider older results.
     *
     * @param context simulation context to read measurements and state from
     */
    public void processEvents(SimulationContext context) {
        List<MeasurementResult> measurements = context.getMeasurements();
        while (processedMeasurements < measurements.size()) {
            MeasurementResult result = measurements.get(processedMeasurements++);
            ControlDecision decision = processMeasurement(result);
            
            if (decision != null && eventScheduler != null) {
                for (QuantumEvent event : decision.getEvents()) {
                    if (event != null) {
                        eventScheduler.accept(event);
                    }
                }
            }
        }
        
        updateClassicalParameters(context);
    }
    
    /**
     * Appends a control rule.
     *
     * <p>Rules are evaluated in insertion order for every measurement.
     *
     * @param rule rule to add
     */
    public void addRule(ControlRule rule) {
        controlRules.add(rule);
    }
    
    /**
     * Removes the first occurrence of a rule.
     *
     * @param rule rule to remove, which may be absent
     */
    public void removeRule(ControlRule rule) {
        controlRules.remove(rule);
    }
    
    /**
     * Removes all rules and then reinstalls the three default rules, so this method
     * never leaves the controller without rules.
     */
    public void clearRules() {
        controlRules.clear();
        initializeDefaultRules(); // Keep default rules
    }
    
    /**
     * Stores a value in classical memory, replacing any previous value for the key.
     *
     * @param key memory key
     * @param value value to store
     */
    public void storeInMemory(String key, Object value) {
        classicalMemory.put(key, value);
    }
    
    /**
     * Returns the value stored under a key.
     *
     * @param key memory key
     * @return stored value, or {@code null} when the key is absent
     */
    public Object getFromMemory(String key) {
        return classicalMemory.get(key);
    }
    
    /**
     * Removes every entry from classical memory, including the measurement entries and
     * condition results written by this controller.
     */
    public void clearMemory() {
        classicalMemory.clear();
    }
    
    /**
     * Returns a copy of the decisions recorded so far, in the order they were produced.
     *
     * @return copy of the decision history
     */
    public List<ControlDecision> getDecisionHistory() {
        return new ArrayList<>(decisionHistory);
    }
    
    /**
     * Returns the number of measurements processed so far, which is also the counter
     * used to build the {@code measurement_<n>} memory keys.
     *
     * @return processed measurement count
     */
    public int getDecisionCount() {
        return decisionCount;
    }
    
    /**
     * Returns a copy of classical memory.
     *
     * @return copy of the memory contents
     */
    public Map<String, Object> getMemorySnapshot() {
        return new HashMap<>(classicalMemory);
    }
    
    private ControlDecision combineDecisions(List<ControlDecision> decisions) {
        if (decisions.isEmpty()) {
            return null;
        }
        
        if (decisions.size() == 1) {
            return decisions.get(0);
        }
        
        // For multiple decisions, prioritize by rule priority
        decisions.sort(Comparator.comparingInt(ControlDecision::getPriority).reversed());
        
        // Return the highest priority decision
        return decisions.get(0);
    }
    
    private boolean evaluateCondition(Condition condition) {
        // Evaluate condition based on classical memory
        Object value = classicalMemory.get(condition.getParameterName());
        
        if (value == null) {
            return false;
        }
        
        // Simple numeric comparison for now
        if (value instanceof Number) {
            double numValue = ((Number) value).doubleValue();
            return compareValues(numValue, condition.getComparisonValue(), condition.getComparisonOperator());
        }
        
        // For measurement results, check expectation value
        if (value instanceof MeasurementResult) {
            MeasurementResult result = (MeasurementResult) value;
            double expValue = result.getExpectationValue();
            return compareValues(expValue, condition.getComparisonValue(), condition.getComparisonOperator());
        }
        
        return false;
    }
    
    private boolean compareValues(double actual, double expected, ComparisonOperator operator) {
        switch (operator) {
            case EQUALS:
                return Math.abs(actual - expected) < 1e-10;
            case NOT_EQUALS:
                return Math.abs(actual - expected) >= 1e-10;
            case GREATER_THAN:
                return actual > expected;
            case LESS_THAN:
                return actual < expected;
            case GREATER_THAN_OR_EQUALS:
                return actual >= expected;
            case LESS_THAN_OR_EQUALS:
                return actual <= expected;
            default:
                return false;
        }
    }
    
    private void modifyEventParameters(EventQueue queue, ControlDecision decision) {
        // Modify parameters of events in the queue
        // This is a simplified implementation
        if (decision.getParameters() == null) {
            return;
        }
        
        // For now, just update classical memory with new parameters
        decision.getParameters().forEach(classicalMemory::put);
    }
    
    private void updateClassicalParameters(SimulationContext context) {
        // Update classical parameters based on simulation state
        classicalMemory.put("simulation_steps", context.getStepsExecuted());
        classicalMemory.put("simulation_time", context.getElapsedTimeMillis());
        classicalMemory.put("current_fidelity", context.getErrorManager().getCurrentFidelity());
    }
    
    // Inner classes and interfaces
    /**
     * Rule that inspects a measurement and optionally contributes a control decision.
     *
     * <p>Rules are registered with {@link ClassicalController#addRule(ControlRule)} and
     * are consulted for every measurement. Returning {@code null} from
     * {@link #evaluate(MeasurementResult, Map)} means that the rule takes no action for
     * that measurement, and the controller then considers the other rules.
     */
    public interface ControlRule {
        /**
         * Tests whether this rule wants to evaluate a measurement.
         *
         * @param measurement measurement to inspect
         * @return {@code true} when the rule should be evaluated for this measurement
         */
        boolean appliesTo(MeasurementResult measurement);

        /**
         * Produces a decision for a measurement.
         *
         * @param measurement measurement being processed
         * @param memory current classical memory, which the rule may read and modify
         * @return decision to consider, or {@code null} when the rule takes no action
         */
        ControlDecision evaluate(MeasurementResult measurement, Map<String, Object> memory);

        /**
         * Returns the descriptive rule name.
         *
         * @return rule name
         */
        String getName();

        /**
         * Reports whether the rule is active.
         *
         * @return {@code true} when the controller should consult this rule
         */
        boolean isEnabled();

        /**
         * Enables or disables the rule.
         *
         * @param enabled {@code true} to have the rule evaluated
         */
        void setEnabled(boolean enabled);

        /**
         * Returns the priority used to choose between competing decisions.
         *
         * @return rule priority, where a larger value wins
         */
        int getPriority();
    }
    
    /**
     * Default rule that requests a corrective action when a measurement expectation
     * value is negative.
     *
     * <p>The rule applies to every measurement and returns a schedule decision with
     * priority {@code 100} when {@link MeasurementResult#getExpectationValue()} is below
     * zero. The decision it creates carries no event, so the controller records the
     * decision but schedules nothing.
     */
    public static class ConditionalGateRule implements ControlRule {
        private boolean enabled = true;
        
        /**
         * Applies to every measurement.
         *
         * @param measurement measurement to inspect
         * @return always {@code true}
         */
        @Override
        public boolean appliesTo(MeasurementResult measurement) {
            // Apply to all measurements for now
            return true;
        }
        
        /**
         * Requests a corrective schedule decision when the expectation value is
         * negative.
         *
         * @param measurement measurement being processed
         * @param memory current classical memory, not used by this rule
         * @return a schedule decision with priority {@code 100} and no event, or
         *         {@code null} when the expectation value is not negative
         */
        @Override
        public ControlDecision evaluate(MeasurementResult measurement, Map<String, Object> memory) {
            // Simple rule: if measurement expectation is negative, apply X gate
            if (measurement.getExpectationValue() < 0) {
                // Create corrective X gate event
                // This is a simplified example
                return ControlDecision.createScheduleDecision(
                    "Corrective gate based on measurement",
                    100,
                    null // Event would be created here in real implementation
                );
            }
            return null;
        }
        
        /**
         * Returns the rule name.
         *
         * @return the literal {@code ConditionalGateRule}
         */
        @Override
        public String getName() {
            return "ConditionalGateRule";
        }
        
        /**
         * Reports whether the rule is active.
         *
         * @return current enabled flag
         */
        @Override
        public boolean isEnabled() {
            return enabled;
        }
        
        /**
         * Enables or disables the rule.
         *
         * @param enabled new enabled flag
         */
        @Override
        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }
        
        /**
         * Returns the rule priority.
         *
         * @return constant priority {@code 50}
         */
        @Override
        public int getPriority() {
            return 50;
        }
    }
    
    /**
     * Default rule that is a placeholder for feedback based measurement adaptation.
     *
     * <p>It applies when the observable name contains {@code Z}, has priority
     * {@code 70}, and currently returns {@code null} from
     * {@link #evaluate(MeasurementResult, Map)}.
     */
    public static class FeedbackMeasurementRule implements ControlRule {
        private boolean enabled = true;
        
        /**
         * Applies when the observable name contains {@code Z}.
         *
         * @param measurement measurement to inspect
         * @return {@code true} when the observable name contains {@code Z}
         */
        @Override
        public boolean appliesTo(MeasurementResult measurement) {
            return measurement.getObservableName().contains("Z");
        }
        
        /**
         * Takes no action.
         *
         * @param measurement measurement being processed
         * @param memory current classical memory
         * @return always {@code null}
         */
        @Override
        public ControlDecision evaluate(MeasurementResult measurement, Map<String, Object> memory) {
            // Rule for feedback-based measurement adaptation
            return null; // Simplified
        }
        
        /**
         * Returns the rule name.
         *
         * @return the literal {@code FeedbackMeasurementRule}
         */
        @Override
        public String getName() {
            return "FeedbackMeasurementRule";
        }
        
        /**
         * Reports whether the rule is active.
         *
         * @return current enabled flag
         */
        @Override
        public boolean isEnabled() {
            return enabled;
        }
        
        /**
         * Enables or disables the rule.
         *
         * @param enabled new enabled flag
         */
        @Override
        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }
        
        /**
         * Returns the rule priority.
         *
         * @return constant priority {@code 70}
         */
        @Override
        public int getPriority() {
            return 70;
        }
    }
    
    /**
     * Default rule that is a placeholder for noise adaptation.
     *
     * <p>It applies when the measurement variance is greater than {@code 0.1}, has
     * priority {@code 30}, and currently returns {@code null} from
     * {@link #evaluate(MeasurementResult, Map)}.
     */
    public static class AdaptiveNoiseRule implements ControlRule {
        private boolean enabled = true;
        
        /**
         * Applies when the measurement variance exceeds {@code 0.1}.
         *
         * @param measurement measurement to inspect
         * @return {@code true} when the variance is greater than {@code 0.1}
         */
        @Override
        public boolean appliesTo(MeasurementResult measurement) {
            return measurement.getVariance() > 0.1;
        }
        
        /**
         * Takes no action.
         *
         * @param measurement measurement being processed
         * @param memory current classical memory
         * @return always {@code null}
         */
        @Override
        public ControlDecision evaluate(MeasurementResult measurement, Map<String, Object> memory) {
            // Adjust noise parameters based on measurement variance
            return null; // Simplified
        }
        
        /**
         * Returns the rule name.
         *
         * @return the literal {@code AdaptiveNoiseRule}
         */
        @Override
        public String getName() {
            return "AdaptiveNoiseRule";
        }
        
        /**
         * Reports whether the rule is active.
         *
         * @return current enabled flag
         */
        @Override
        public boolean isEnabled() {
            return enabled;
        }
        
        /**
         * Enables or disables the rule.
         *
         * @param enabled new enabled flag
         */
        @Override
        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }
        
        /**
         * Returns the rule priority.
         *
         * @return constant priority {@code 30}
         */
        @Override
        public int getPriority() {
            return 30;
        }
    }
    
    /**
     * Description of a classical branch: compares the memory value named by
     * {@link #getParameterName()} with a constant and supplies the operation for each
     * outcome.
     *
     * <p>Instances are immutable. Conditions are evaluated by
     * {@link ClassicalController#executeConditional(Condition)}.
     */
    public static class Condition {
        private final String name;
        private final String parameterName;
        private final ComparisonOperator operator;
        private final double comparisonValue;
        private final QuantumOperation operationIfTrue;
        private final QuantumOperation operationIfFalse;
        
        /**
         * Creates an immutable condition.
         *
         * @param name condition name, also used in the {@code last_condition_} memory key
         * @param parameterName memory key whose value is tested
         * @param operator comparison applied to the value
         * @param comparisonValue constant the value is compared with
         * @param operationIfTrue operation returned when the comparison holds
         * @param operationIfFalse operation returned when it does not hold, may be
         *                        {@code null}
         */
        public Condition(String name, String parameterName, ComparisonOperator operator,
                        double comparisonValue, QuantumOperation operationIfTrue,
                        QuantumOperation operationIfFalse) {
            this.name = name;
            this.parameterName = parameterName;
            this.operator = operator;
            this.comparisonValue = comparisonValue;
            this.operationIfTrue = operationIfTrue;
            this.operationIfFalse = operationIfFalse;
        }
        
        /**
         * Returns the condition name.
         *
         * @return condition name
         */
        public String getName() { return name; }
        /**
         * Returns the memory key that supplies the tested value.
         *
         * @return memory key
         */
        public String getParameterName() { return parameterName; }
        /**
         * Returns the comparison applied to the tested value.
         *
         * @return comparison operator
         */
        public ComparisonOperator getComparisonOperator() { return operator; }
        /**
         * Returns the constant the tested value is compared with.
         *
         * @return comparison value
         */
        public double getComparisonValue() { return comparisonValue; }
        /**
         * Returns the operation for a true comparison.
         *
         * @return true branch operation
         */
        public QuantumOperation getOperationIfTrue() { return operationIfTrue; }
        /**
         * Returns the operation for a false comparison.
         *
         * @return false branch operation, or {@code null} when none was supplied
         */
        public QuantumOperation getOperationIfFalse() { return operationIfFalse; }
    }
    
    /**
     * Comparison operators available to {@link Condition}.
     *
     * <p>{@link #EQUALS} and {@link #NOT_EQUALS} treat values that differ by less than
     * {@code 1e-10} as equal, while the ordering operators compare the raw values.
     */
    public enum ComparisonOperator {
        /**
         * Actual and expected values differ by less than {@code 1e-10}.
         */
        EQUALS,
        /**
         * Actual and expected values differ by at least {@code 1e-10}.
         */
        NOT_EQUALS,
        /**
         * Actual value is strictly greater than the expected value.
         */
        GREATER_THAN,
        /**
         * Actual value is strictly less than the expected value.
         */
        LESS_THAN,
        /**
         * Actual value is greater than or equal to the expected value.
         */
        GREATER_THAN_OR_EQUALS,
        /**
         * Actual value is less than or equal to the expected value.
         */
        LESS_THAN_OR_EQUALS
    }
}