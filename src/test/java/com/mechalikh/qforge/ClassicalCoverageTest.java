package com.mechalikh.qforge;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.mechalikh.qforge.classical.ClassicalController;
import com.mechalikh.qforge.classical.ClassicalController.ComparisonOperator;
import com.mechalikh.qforge.classical.ClassicalController.Condition;
import com.mechalikh.qforge.classical.ControlDecision;
import com.mechalikh.qforge.classical.ParameterSpace;
import com.mechalikh.qforge.classical.VariationalQuantumEigensolver;
import com.mechalikh.qforge.config.SimulationBackend;
import com.mechalikh.qforge.config.SimulationConfig;
import com.mechalikh.qforge.core.QuantumSimulator;
import com.mechalikh.qforge.core.SimulationContext;
import com.mechalikh.qforge.core.SimulationResult;
import com.mechalikh.qforge.events.EventQueue;
import com.mechalikh.qforge.events.QuantumEvent;
import com.mechalikh.qforge.measurement.MeasurementResult;
import com.mechalikh.qforge.measurement.Observable;
import com.mechalikh.qforge.operations.QuantumOperation;
import com.mechalikh.qforge.operations.SingleQubitGate;
import com.mechalikh.qforge.state.Qubit;
import com.mechalikh.qforge.strategy.StateVectorStrategy;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;

class ClassicalCoverageTest {
    private static final double EPS = 1e-12;
    private static final Instant T0 = Instant.parse("2024-01-01T00:00:00Z");

    private static Qubit q(int index) {
        return new Qubit(index, "q" + index);
    }

    private static QuantumEvent event(String id, Instant time) {
        return new QuantumEvent.Builder(id).withScheduledTime(time).build();
    }

    /** Deterministic single-qubit result: outcome 0 has expectation +1, outcome 1 has -1. */
    private static MeasurementResult result(int outcome, Qubit qubit) {
        Map<Integer, Integer> counts = Map.of(outcome, 10);
        return new MeasurementResult("Z", new Qubit[]{qubit}, counts, 10, new double[]{1, -1});
    }

    /** Deterministic split result with expectation 0 and variance 1. */
    private static MeasurementResult split(Qubit qubit) {
        return new MeasurementResult("Z", new Qubit[]{qubit}, Map.of(0, 5, 1, 5), 10,
            new double[]{1, -1});
    }

    private static QuantumSimulator simulator(int numQubits) throws Exception {
        SimulationConfig config = new SimulationConfig();
        config.setNumQubits(numQubits);
        config.setBackend(SimulationBackend.STATE_VECTOR);
        QuantumSimulator simulator = new QuantumSimulator();
        simulator.initialize(config);
        return simulator;
    }

    private static final class StubRule implements ClassicalController.ControlRule {
        private final String name;
        private final boolean applies;
        private final int rulePriority;
        private final Supplier<ControlDecision> decisions;
        private final AtomicInteger evaluations = new AtomicInteger();
        private boolean enabled = true;

        StubRule(String name, boolean applies, int rulePriority, Supplier<ControlDecision> decisions) {
            this.name = name;
            this.applies = applies;
            this.rulePriority = rulePriority;
            this.decisions = decisions;
        }

        @Override
        public boolean appliesTo(MeasurementResult measurement) {
            return applies;
        }

        @Override
        public ControlDecision evaluate(MeasurementResult measurement, Map<String, Object> memory) {
            evaluations.incrementAndGet();
            return decisions.get();
        }

        @Override
        public String getName() {
            return name;
        }

        @Override
        public boolean isEnabled() {
            return enabled;
        }

        @Override
        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        @Override
        public int getPriority() {
            return rulePriority;
        }

        int evaluations() {
            return evaluations.get();
        }
    }

    // ==================== ParameterSpace ====================

    @Test
    void numericParameterLifecycleAndFormatting() {
        ParameterSpace space = new ParameterSpace();
        space.addParameter("x", 2.0, 0.0, 10.0, 0.5);

        assertTrue(space.hasParameter("x"));
        assertEquals(1, space.getParameterNames().size());
        assertEquals(2.0, space.getValue("x"));
        assertEquals(2.0, space.getDoubleValue("x"), EPS);
        assertEquals(2, space.getIntValue("x"));
        assertEquals(10.0, space.getParameterRange("x"), EPS);
        assertTrue(space.isWithinBounds("x", 0.0));
        assertTrue(space.isWithinBounds("x", 10.0));
        assertFalse(space.isWithinBounds("x", 10.0001));
        assertFalse(space.isWithinBounds("x", -0.0001));

        ParameterSpace.ParameterInfo info = space.getParameterInfo("x");
        assertEquals("x", info.getName());
        assertEquals("numeric", info.getType());
        assertEquals(0.0, info.getMinOrValues());
        assertEquals(10.0, info.getMaxOrCurrent());
        assertEquals(2.0, info.getCurrentValue());
        assertEquals(0.5, info.getRegularization());
        assertEquals("ParameterInfo[name='x', type=numeric, range=[0.0000, 10.0000], "
            + "current=2.0000, reg=0.5000]", info.toString());

        space.removeParameter("x");
        assertFalse(space.hasParameter("x"));
        assertNull(space.getParameterInfo("x"));
    }

    @Test
    void numericParameterValidationAndClamping() {
        ParameterSpace space = new ParameterSpace();
        space.addParameter("x", 5.0, 0.0, 10.0);

        assertThrows(IllegalArgumentException.class,
            () -> space.addParameter("x", 1.0, 0.0, 10.0));
        assertThrows(IllegalArgumentException.class,
            () -> space.addParameter("bad", 1.0, 5.0, 4.0));
        assertThrows(IllegalArgumentException.class,
            () -> space.addParameter("bad", 11.0, 0.0, 10.0));
        assertThrows(IllegalArgumentException.class,
            () -> space.setValue("missing", 1.0));
        assertThrows(IllegalArgumentException.class,
            () -> space.setValue("x", "text"));
        assertThrows(IllegalArgumentException.class,
            () -> space.getDoubleValue("missing"));

        space.setValue("x", 100.0);
        assertEquals(10.0, space.getDoubleValue("x"), EPS);
        space.setValue("x", -100);
        assertEquals(0.0, space.getDoubleValue("x"), EPS);
        space.setValue("x", 7);
        assertEquals(7.0, space.getDoubleValue("x"), EPS);
    }

    @Test
    void discreteAndGenericParameters() {
        ParameterSpace space = new ParameterSpace();
        List<Object> allowed = new ArrayList<>(List.of("a", "b"));
        space.addDiscreteParameter("mode", allowed, "a");
        allowed.add("c"); // the parameter keeps its own copy

        assertEquals("a", space.getValue("mode"));
        assertEquals(0.0, space.getParameterRange("mode"), EPS);
        assertTrue(space.isWithinBounds("mode", 123.0));
        assertThrows(IllegalArgumentException.class,
            () -> space.addDiscreteParameter("mode", List.of("a"), "a"));
        assertThrows(IllegalArgumentException.class,
            () -> space.addDiscreteParameter("other", List.of("a"), "z"));
        assertThrows(IllegalArgumentException.class,
            () -> space.setValue("mode", "c"));

        ParameterSpace.ParameterInfo discrete = space.getParameterInfo("mode");
        assertEquals("discrete", discrete.getType());
        assertEquals(List.of("a", "b"), discrete.getMinOrValues());
        assertNull(discrete.getMaxOrCurrent());
        assertNull(discrete.getRegularization());
        assertEquals("ParameterInfo[name='mode', type=discrete, values=[a, b], current=a]",
            discrete.toString());

        space.setValue("mode", "b");
        assertEquals("b", space.getValue("mode"));

        space.addParameter("g", 7);
        assertEquals(7, space.getValue("g"));
        space.setValue("g", 9);
        assertEquals(9.0, space.getDoubleValue("g"), EPS);
        assertThrows(IllegalArgumentException.class, () -> space.setValue("g", "text"));
        assertThrows(IllegalArgumentException.class,
            () -> space.addParameter("g", 1));

        ParameterSpace.ParameterInfo generic = space.getParameterInfo("g");
        assertEquals("generic", generic.getType());
        assertNull(generic.getMinOrValues());
        assertNull(generic.getMaxOrCurrent());
        assertNull(generic.getRegularization());
        assertEquals("ParameterInfo[name='g', type=generic, current=9]", generic.toString());

        space.resetToInitial();
        assertEquals("a", space.getValue("mode"));
        assertEquals(9, space.getValue("g")); // generic parameters have no initial snapshot
    }

    @Test
    void randomConfigurationsAreBoundedAndSeeded() {
        ParameterSpace space = new ParameterSpace();
        space.addParameter("x", 5.0, 0.0, 10.0);
        space.addDiscreteParameter("mode", List.of("a", "b", "c"), "b");
        space.addParameter("g", 7);

        space.setRandomSeed(123L);
        List<Map<String, Object>> first = space.sampleConfigurations(4);
        space.setRandomSeed(123L);
        List<Map<String, Object>> second = space.sampleConfigurations(4);

        assertEquals(first, second);
        assertEquals(4, first.size());
        for (Map<String, Object> config : first) {
            double x = ((Number) config.get("x")).doubleValue();
            assertTrue(x >= 0.0 && x < 10.0);
            assertTrue(List.of("a", "b", "c").contains(config.get("mode")));
            assertEquals(7, config.get("g"));
        }
        assertTrue(space.sampleConfigurations(0).isEmpty());
        assertTrue(space.sampleConfigurations(-3).isEmpty());
    }

    @Test
    void neighborPerturbationClipsAndDoesNotMutateInput() {
        ParameterSpace space = new ParameterSpace();
        space.addParameter("x", 5.0, 0.0, 10.0);
        space.addDiscreteParameter("mode", List.of("a", "b"), "a");
        space.setRandomSeed(7L);

        Map<String, Object> current = new HashMap<>();
        current.put("x", 5.0);
        current.put("mode", "b");

        Map<String, Object> still = space.getNeighborConfiguration(current, 0.0);
        assertNotSame(current, still);
        assertEquals(5.0, still.get("x"));
        assertEquals("b", still.get("mode"));
        assertEquals(5.0, current.get("x"));

        current.put("x", 100.0);
        Map<String, Object> clippedHigh = space.getNeighborConfiguration(current, 1.0);
        assertEquals(10.0, clippedHigh.get("x"));
        assertEquals("b", clippedHigh.get("mode"));

        current.put("x", -100.0);
        assertEquals(0.0, space.getNeighborConfiguration(current, 1.0).get("x"));
        assertEquals(-100.0, current.get("x"));

        assertThrows(NullPointerException.class,
            () -> space.getNeighborConfiguration(new HashMap<>(), 1.0));
    }

    @Test
    void meanRegularizationClipAndBoundsHelpers() {
        ParameterSpace space = new ParameterSpace();
        space.addParameter("x", 1.0, 0.0, 10.0, 0.5);
        space.addParameter("plain", 2.0, -5.0, 5.0);
        space.addDiscreteParameter("mode", List.of("a", "b"), "a");

        List<Map<String, Object>> configs = List.of(
            Map.of("x", 2.0, "plain", 0.0, "mode", "a"),
            Map.of("x", 4.0, "plain", 0.0, "mode", "b"),
            Map.of("x", 6.0, "plain", 0.0, "mode", "b"));
        Map<String, Object> mean = space.getMeanConfiguration(configs);
        assertEquals(4.0, mean.get("x"));
        assertEquals(0.0, mean.get("plain"));
        assertEquals("b", mean.get("mode"));

        Map<String, Object> currentMean = space.getMeanConfiguration(List.of());
        assertEquals(1.0, currentMean.get("x"));
        assertEquals(2.0, currentMean.get("plain"));
        assertEquals("a", currentMean.get("mode"));

        assertEquals(2.0, space.computeRegularizationCost(Map.of("x", 2.0)), EPS);
        assertEquals(2.0, space.computeRegularizationCost(Map.of("x", -2.0)), EPS);
        assertThrows(NullPointerException.class,
            () -> space.computeRegularizationCost(Map.of("plain", 1.0)));

        Map<String, Object> values = new HashMap<>();
        values.put("x", 100.0);
        values.put("mode", "unknown");
        values.put("unknown", 5);
        space.clipToBounds(values);
        assertEquals(10.0, values.get("x"));
        assertEquals("unknown", values.get("mode"));
        assertEquals(5, values.get("unknown"));

        assertEquals(10.0, space.getParameterRange("x"), EPS);
        assertEquals(0.0, space.getParameterRange("mode"), EPS);
        assertEquals(0.0, space.getParameterRange("nope"), EPS);
        assertTrue(space.isWithinBounds("mode", 999.0));
        assertTrue(space.isWithinBounds("nope", 999.0));
        assertFalse(space.isWithinBounds("x", -0.0001));
    }

    @Test
    void defaultParameterSpaceLayout() {
        ParameterSpace space = ParameterSpace.createDefaultParameterSpace(2);
        assertEquals(Set.of("theta_0", "phi_0", "theta_1", "phi_1", "entangle_0_1"),
            space.getParameterNames());
        assertEquals(5, space.getParameterNames().size());

        for (String name : List.of("theta_0", "phi_0", "theta_1", "phi_1")) {
            assertEquals(0.0, space.getDoubleValue(name), EPS);
            assertEquals(2 * Math.PI, space.getParameterRange(name), EPS);
            assertEquals(0.001, space.getParameterInfo(name).getRegularization());
        }
        assertEquals(0.01, space.getParameterInfo("entangle_0_1").getRegularization());
        assertEquals(2 * Math.PI, space.getParameterRange("entangle_0_1"), EPS);

        assertThrows(UnsupportedOperationException.class,
            () -> space.getParameterNames().add("forbidden"));
    }

    // ==================== ClassicalController ====================

    @Test
    void defaultConditionalRuleProducesCorrectiveDecision() {
        ClassicalController controller = new ClassicalController();
        assertEquals(0, controller.getDecisionCount());
        assertTrue(controller.getDecisionHistory().isEmpty());
        assertTrue(controller.getMemorySnapshot().isEmpty());

        MeasurementResult negative = result(1, q(0));
        var decision = controller.processMeasurement(negative);
        assertEquals(ControlDecision.DecisionAction.SCHEDULE_EVENT, decision.getAction());
        assertEquals(100, decision.getPriority());
        assertEquals("Corrective gate based on measurement", decision.getDescription());
        assertNull(decision.getEvent());
        assertTrue(decision.getEvents().isEmpty());

        assertSame(negative, controller.getFromMemory("last_measurement"));
        assertSame(negative, controller.getFromMemory("measurement_1"));
        assertEquals(1, controller.getDecisionCount());
        assertEquals(1, controller.getDecisionHistory().size());

        assertNull(controller.processMeasurement(result(0, q(0))));
        assertEquals(2, controller.getDecisionCount());
        assertEquals(1, controller.getDecisionHistory().size());

        List<ControlDecision> history = controller.getDecisionHistory();
        history.clear();
        assertEquals(1, controller.getDecisionHistory().size());
        Map<String, Object> snapshot = controller.getMemorySnapshot();
        snapshot.clear();
        assertEquals(3, controller.getMemorySnapshot().size());

        controller.storeInMemory("answer", 42);
        assertEquals(42, controller.getFromMemory("answer"));
        controller.clearMemory();
        assertTrue(controller.getMemorySnapshot().isEmpty());
    }

    @Test
    void defaultRuleMetadataAndApplicability() {
        ClassicalController.ConditionalGateRule conditional =
            new ClassicalController.ConditionalGateRule();
        assertEquals("ConditionalGateRule", conditional.getName());
        assertEquals(50, conditional.getPriority());
        assertTrue(conditional.appliesTo(result(0, q(0))));
        assertNull(conditional.evaluate(result(0, q(0)), new HashMap<>()));
        assertEquals(100, conditional.evaluate(result(1, q(0)), new HashMap<>()).getPriority());
        assertTrue(conditional.isEnabled());
        conditional.setEnabled(false);
        assertFalse(conditional.isEnabled());

        ClassicalController.FeedbackMeasurementRule feedback =
            new ClassicalController.FeedbackMeasurementRule();
        assertEquals("FeedbackMeasurementRule", feedback.getName());
        assertEquals(70, feedback.getPriority());
        assertTrue(feedback.appliesTo(result(0, q(0))));
        MeasurementResult xResult = new MeasurementResult("X", new Qubit[]{q(0)},
            Map.of(0, 10), 10, new double[]{1, -1});
        assertFalse(feedback.appliesTo(xResult));
        assertNull(feedback.evaluate(result(1, q(0)), new HashMap<>()));

        ClassicalController.AdaptiveNoiseRule adaptive =
            new ClassicalController.AdaptiveNoiseRule();
        assertEquals("AdaptiveNoiseRule", adaptive.getName());
        assertEquals(30, adaptive.getPriority());
        assertTrue(adaptive.appliesTo(split(q(0))));
        assertFalse(adaptive.appliesTo(result(0, q(0))));
        assertNull(adaptive.evaluate(split(q(0)), new HashMap<>()));
    }

    @Test
    void customRulePriorityTiesAndEnablement() {
        ClassicalController controller = new ClassicalController();
        controller.addRule(new StubRule("low", true, 1,
            () -> new ControlDecision.Builder("low").withPriority(200).withDescription("low").build()));
        controller.addRule(new StubRule("high", true, 999,
            () -> new ControlDecision.Builder("high").withPriority(300).withDescription("high").build()));

        assertEquals("high", controller.processMeasurement(result(0, q(0))).getDescription());

        ClassicalController tie = new ClassicalController();
        tie.addRule(new StubRule("first", true, 10,
            () -> new ControlDecision.Builder("first").withPriority(150).withDescription("first").build()));
        tie.addRule(new StubRule("second", true, 20,
            () -> new ControlDecision.Builder("second").withPriority(150).withDescription("second").build()));
        assertEquals("first", tie.processMeasurement(result(0, q(0))).getDescription());

        StubRule disabled = new StubRule("disabled", true, 10,
            () -> new ControlDecision.Builder("d").withDescription("d").build());
        disabled.setEnabled(false);
        StubRule notApplicable = new StubRule("n/a", false, 10,
            () -> new ControlDecision.Builder("n").build());
        ClassicalController filtered = new ClassicalController();
        filtered.addRule(disabled);
        filtered.addRule(notApplicable);
        assertNull(filtered.processMeasurement(result(0, q(0))));
        assertEquals(0, disabled.evaluations());
        assertEquals(0, notApplicable.evaluations());

        disabled.setEnabled(true);
        notApplicable.setEnabled(true);
        assertEquals("d", filtered.processMeasurement(result(0, q(0))).getDescription());
        assertEquals(1, disabled.evaluations());
        assertEquals(0, notApplicable.evaluations());
    }

    @Test
    void ruleRemovalAndClearingKeepDefaultRules() {
        ClassicalController controller = new ClassicalController();
        StubRule custom = new StubRule("custom", true, 10,
            () -> new ControlDecision.Builder("custom").withPriority(300).withDescription("custom").build());
        controller.addRule(custom);

        assertEquals("custom", controller.processMeasurement(result(1, q(0))).getDescription());
        controller.removeRule(custom);
        assertEquals("Corrective gate based on measurement",
            controller.processMeasurement(result(1, q(0))).getDescription());

        StubRule extra = new StubRule("extra", true, 10,
            () -> new ControlDecision.Builder("extra").withPriority(300).build());
        controller.addRule(extra);
        controller.clearRules();
        assertEquals("Corrective gate based on measurement",
            controller.processMeasurement(result(1, q(0))).getDescription());
    }

    @Test
    void conditionalComparisonsUseNumbersMeasurementsAndTolerance() {
        ClassicalController controller = new ClassicalController();
        controller.storeInMemory("v", 5.0);

        assertTrue(holds(controller, "v", ComparisonOperator.EQUALS, 5.0));
        assertTrue(holds(controller, "v", ComparisonOperator.EQUALS, 5.0 + 5e-11));
        assertFalse(holds(controller, "v", ComparisonOperator.EQUALS, 5.0 + 1e-9));
        assertFalse(holds(controller, "v", ComparisonOperator.NOT_EQUALS, 5.0));
        assertTrue(holds(controller, "v", ComparisonOperator.NOT_EQUALS, 4.0));
        assertTrue(holds(controller, "v", ComparisonOperator.GREATER_THAN, 4.999));
        assertFalse(holds(controller, "v", ComparisonOperator.GREATER_THAN, 5.0));
        assertTrue(holds(controller, "v", ComparisonOperator.LESS_THAN, 5.001));
        assertFalse(holds(controller, "v", ComparisonOperator.LESS_THAN, 5.0));
        assertTrue(holds(controller, "v", ComparisonOperator.GREATER_THAN_OR_EQUALS, 5.0));
        assertFalse(holds(controller, "v", ComparisonOperator.GREATER_THAN_OR_EQUALS, 5.001));
        assertTrue(holds(controller, "v", ComparisonOperator.LESS_THAN_OR_EQUALS, 5.0));
        assertFalse(holds(controller, "v", ComparisonOperator.LESS_THAN_OR_EQUALS, 4.999));

        MeasurementResult negative = result(1, q(0));
        controller.storeInMemory("m", negative);
        assertTrue(holds(controller, "m", ComparisonOperator.EQUALS, -1.0));
        assertFalse(holds(controller, "m", ComparisonOperator.GREATER_THAN, 0.0));

        controller.storeInMemory("text", "abc");
        assertFalse(holds(controller, "missing", ComparisonOperator.EQUALS, 0.0));
        assertFalse(holds(controller, "text", ComparisonOperator.GREATER_THAN, 0.0));

        QuantumOperation trueOp = SingleQubitGate.createPauliX(q(0));
        Condition noFalseBranch = new Condition("nofalse", "missing",
            ComparisonOperator.EQUALS, 0.0, trueOp, null);
        assertNull(controller.executeConditional(noFalseBranch));
        assertNull(controller.executeConditional(null));
        assertEquals(Boolean.FALSE, controller.getFromMemory("last_condition_nofalse"));

        Condition last = new Condition("vcheck", "v", ComparisonOperator.EQUALS, 5.0, trueOp, null);
        assertSame(trueOp, controller.executeConditional(last));
        assertEquals(Boolean.TRUE, controller.getFromMemory("last_condition_vcheck"));
    }

    private static boolean holds(ClassicalController controller, String key,
                                 ComparisonOperator operator, double comparison) {
        QuantumOperation trueOp = SingleQubitGate.createPauliX(q(0));
        QuantumOperation falseOp = SingleQubitGate.createPauliZ(q(0));
        Condition condition = new Condition("check", key, operator, comparison, trueOp, falseOp);
        return controller.executeConditional(condition) == trueOp;
    }

    @Test
    void eventQueueScheduleAndCancelActions() {
        ClassicalController controller = new ClassicalController();
        EventQueue queue = new EventQueue();
        QuantumEvent first = event("e1", T0);
        QuantumEvent second = event("e2", T0.plusSeconds(1));

        ControlDecision schedule = new ControlDecision.Builder("schedule")
            .withAction(ControlDecision.DecisionAction.SCHEDULE_EVENT)
            .withEvents(Arrays.asList(first, null, second))
            .build();
        controller.updateEventQueue(queue, schedule);
        assertEquals(2, queue.size());
        assertTrue(queue.containsEvent("e1"));
        assertTrue(queue.containsEvent("e2"));
        assertEquals("e1", queue.peekNextEvent().getId());

        ControlDecision cancel = new ControlDecision.Builder("cancel")
            .withAction(ControlDecision.DecisionAction.CANCEL_EVENT)
            .withEventId("e1")
            .build();
        controller.updateEventQueue(queue, cancel);
        assertEquals(1, queue.size());
        assertFalse(queue.containsEvent("e1"));
        assertTrue(queue.containsEvent("e2"));

        ControlDecision cancelWithoutId = new ControlDecision.Builder("cancel2")
            .withAction(ControlDecision.DecisionAction.CANCEL_EVENT)
            .build();
        controller.updateEventQueue(queue, cancelWithoutId);
        assertEquals(1, queue.size());

        EventQueue onlyNulls = new EventQueue();
        ControlDecision nullSchedule = new ControlDecision.Builder("nulls")
            .withAction(ControlDecision.DecisionAction.SCHEDULE_EVENT)
            .withEvents(Arrays.asList(null, null))
            .build();
        controller.updateEventQueue(onlyNulls, nullSchedule);
        assertTrue(onlyNulls.isEmpty());
    }

    @Test
    void eventQueueRescheduleModifyAndNullHandling() {
        ClassicalController controller = new ClassicalController();
        EventQueue queue = new EventQueue();
        queue.addEvent(event("e1", T0));

        Instant moved = T0.plusSeconds(10);
        ControlDecision reschedule = new ControlDecision.Builder("move")
            .withAction(ControlDecision.DecisionAction.RESCHEDULE_EVENT)
            .withEventId("e1")
            .withNewTime(moved)
            .build();
        controller.updateEventQueue(queue, reschedule);
        assertEquals(moved, queue.peekNextTime());

        ControlDecision noTime = new ControlDecision.Builder("move2")
            .withAction(ControlDecision.DecisionAction.RESCHEDULE_EVENT)
            .withEventId("e1")
            .build();
        controller.updateEventQueue(queue, noTime);
        assertEquals(moved, queue.peekNextTime());

        ControlDecision unknown = new ControlDecision.Builder("move3")
            .withAction(ControlDecision.DecisionAction.RESCHEDULE_EVENT)
            .withEventId("absent")
            .withNewTime(T0)
            .build();
        assertThrows(IllegalArgumentException.class,
            () -> controller.updateEventQueue(queue, unknown));

        ControlDecision modify = new ControlDecision.Builder("modify")
            .withAction(ControlDecision.DecisionAction.MODIFY_PARAMETERS)
            .withParameter("alpha", 1)
            .withParameter("beta", "two")
            .build();
        controller.updateEventQueue(queue, modify);
        assertEquals(1, controller.getFromMemory("alpha"));
        assertEquals("two", controller.getFromMemory("beta"));
        assertEquals(1, queue.size());

        ControlDecision none = new ControlDecision.Builder("none")
            .withAction(ControlDecision.DecisionAction.NO_ACTION)
            .withEvent(event("e2", T0))
            .build();
        controller.updateEventQueue(queue, none);
        assertEquals(1, queue.size());
        assertFalse(queue.containsEvent("e2"));

        controller.updateEventQueue(queue, null);
        controller.updateEventQueue(null, none);
        assertEquals(1, queue.size());
    }

    @Test
    void processEventsDrainsOnlyNewMeasurementsAndCopiesContextState() throws Exception {
        SimulationConfig config = new SimulationConfig();
        config.setNumQubits(1);
        config.setBackend(SimulationBackend.STATE_VECTOR);
        SimulationContext context = new SimulationContext(config);
        context.setStrategy(new StateVectorStrategy());
        context.initializeState();

        ClassicalController controller = new ClassicalController();
        List<QuantumEvent> scheduled = new ArrayList<>();
        controller.setEventScheduler(scheduled::add);
        AtomicInteger ids = new AtomicInteger();
        controller.addRule(new StubRule("scheduler", true, 1, () -> {
            QuantumEvent next = event("pe" + ids.incrementAndGet(), T0.plusSeconds(ids.get()));
            return ControlDecision.createScheduleDecisions("scheduled", 5, List.of(next));
        }));

        context.addMeasurementResult(result(0, q(0)));
        controller.processEvents(context);
        assertEquals(1, scheduled.size());
        assertEquals("pe1", scheduled.get(0).getId());
        assertEquals(1, controller.getDecisionCount());
        assertEquals(0L, controller.getFromMemory("simulation_steps"));
        assertEquals(1.0, controller.getFromMemory("current_fidelity"));
        assertTrue(controller.getFromMemory("simulation_time") instanceof Long);

        controller.processEvents(context);
        assertEquals(1, scheduled.size());

        context.addMeasurementResult(result(0, q(0)));
        controller.processEvents(context);
        assertEquals(2, scheduled.size());
        assertEquals("pe2", scheduled.get(1).getId());
        assertEquals(2, controller.getDecisionCount());
    }

    // ==================== ControlDecision ====================

    @Test
    void decisionBuilderDefaults() {
        ControlDecision decision = new ControlDecision.Builder("id").build();
        assertEquals("id", decision.getDecisionId());
        assertEquals(ControlDecision.DecisionAction.NO_ACTION, decision.getAction());
        assertEquals("", decision.getDescription());
        assertEquals(50, decision.getPriority());
        assertNull(decision.getEvent());
        assertTrue(decision.getEvents().isEmpty());
        assertNull(decision.getEventId());
        assertNull(decision.getNewTime());
        assertTrue(decision.getParameters().isEmpty());
        assertTrue(decision.getTimestamp() != null);
        assertEquals("ControlDecision[id='id', action=NO_ACTION, priority=50, desc='']",
            decision.toString());
    }

    @Test
    void decisionBuilderCarriesEventsAndCopiesCollections() {
        QuantumEvent first = event("e1", T0);
        QuantumEvent second = event("e2", T0.plusSeconds(1));
        List<QuantumEvent> events = new ArrayList<>(List.of(first, second));
        Instant moved = T0.plusSeconds(5);

        ControlDecision.Builder builder = new ControlDecision.Builder("full")
            .withAction(ControlDecision.DecisionAction.SCHEDULE_EVENT)
            .withDescription("desc")
            .withPriority(7)
            .withEventId("e1")
            .withNewTime(moved)
            .withEvents(events)
            .withParameter("a", 1)
            .withParameters(Map.of("b", 2));
        ControlDecision decision = builder.build();

        assertEquals(ControlDecision.DecisionAction.SCHEDULE_EVENT, decision.getAction());
        assertEquals("desc", decision.getDescription());
        assertEquals(7, decision.getPriority());
        assertEquals("e1", decision.getEventId());
        assertEquals(moved, decision.getNewTime());
        assertSame(first, decision.getEvent());
        assertEquals(List.of(first, second), decision.getEvents());
        assertEquals(Map.of("a", 1, "b", 2), decision.getParameters());
        assertEquals("ControlDecision[id='full', action=SCHEDULE_EVENT, priority=7, desc='desc']",
            decision.toString());

        events.clear();
        assertEquals(2, decision.getEvents().size());
        Map<String, Object> returned = decision.getParameters();
        returned.put("c", 3);
        assertFalse(decision.getParameters().containsKey("c"));

        // The parameter map is shared with the builder, so later builder edits are visible.
        builder.withParameter("later", 4);
        assertTrue(decision.getParameters().containsKey("later"));
    }

    @Test
    void decisionBuilderEventVariantsAndFactories() {
        QuantumEvent only = event("only", T0);
        ControlDecision single = new ControlDecision.Builder("single")
            .withEvent(only)
            .build();
        assertSame(only, single.getEvent());
        assertEquals(List.of(only), single.getEvents());

        ControlDecision none = new ControlDecision.Builder("none")
            .withEvent(null)
            .withEvents(null)
            .build();
        assertNull(none.getEvent());
        assertTrue(none.getEvents().isEmpty());

        ControlDecision scheduled = ControlDecision.createScheduleDecision("go", 12, only);
        assertTrue(scheduled.getDecisionId().startsWith("schedule_"));
        assertEquals(ControlDecision.DecisionAction.SCHEDULE_EVENT, scheduled.getAction());
        assertEquals(12, scheduled.getPriority());
        assertEquals("go", scheduled.getDescription());
        assertSame(only, scheduled.getEvent());
        assertEquals(List.of(only), scheduled.getEvents());

        QuantumEvent second = event("second", T0.plusSeconds(1));
        ControlDecision many = ControlDecision.createScheduleDecisions("many", 13,
            List.of(only, second));
        assertSame(only, many.getEvent());
        assertEquals(2, many.getEvents().size());
        assertTrue(ControlDecision.createScheduleDecisions("empty", 1, null)
            .getEvents().isEmpty());

        ControlDecision cancel = ControlDecision.createCancelDecision("e1", "stop");
        assertTrue(cancel.getDecisionId().startsWith("cancel_"));
        assertEquals(ControlDecision.DecisionAction.CANCEL_EVENT, cancel.getAction());
        assertEquals("e1", cancel.getEventId());
        assertEquals("stop", cancel.getDescription());
        assertEquals(50, cancel.getPriority());
    }

    // ==================== VariationalQuantumEigensolver ====================

    @Test
    void vqeConstructorBuildsReproducibleBoundedAnsatz() {
        Qubit qubit = q(0);
        VariationalQuantumEigensolver first =
            new VariationalQuantumEigensolver(Observable.pauliZ(qubit), List.of(qubit));
        VariationalQuantumEigensolver second =
            new VariationalQuantumEigensolver(Observable.pauliZ(qubit), List.of(qubit));

        assertEquals(Double.MAX_VALUE, first.getCurrentCost());
        assertEquals(Double.MAX_VALUE, first.getEnergy());
        assertEquals(0, first.getIterationCount());
        assertFalse(first.isConverged());
        assertTrue(first.getCostHistory().isEmpty());
        assertEquals("VariationalQuantumEigensolver", first.getName());

        Map<String, Double> parameters = first.getOptimalParameters();
        assertEquals(Set.of("theta_0_0", "phi_0_0", "theta_1_0", "phi_1_0"),
            parameters.keySet());
        assertEquals(parameters, second.getOptimalParameters());
        for (double value : parameters.values()) {
            assertTrue(value >= -Math.PI / 2 && value < Math.PI / 2);
        }

        List<QuantumOperation> operations = first.getNextOperations();
        assertEquals(4, operations.size());
        assertEquals(2, operations.stream()
            .filter(op -> op.getName().startsWith("RY(")).count());
        assertEquals(2, operations.stream()
            .filter(op -> op.getName().startsWith("RZ(")).count());
    }

    @Test
    void vqeRejectsExecutionBeforeInitialization() {
        Qubit qubit = q(0);
        VariationalQuantumEigensolver execution =
            new VariationalQuantumEigensolver(Observable.pauliZ(qubit), List.of(qubit));
        assertThrows(IllegalStateException.class, execution::executeIteration);

        VariationalQuantumEigensolver result =
            new VariationalQuantumEigensolver(Observable.pauliZ(qubit), List.of(qubit));
        assertThrows(IllegalStateException.class, result::getResult);
    }

    @Test
    void vqeStopsAtIterationLimit() throws Exception {
        Qubit qubit = q(0);
        VariationalQuantumEigensolver vqe =
            new VariationalQuantumEigensolver(Observable.pauliZ(qubit), List.of(qubit));
        vqe.initialize(simulator(1), Map.of("max_iterations", 1));

        assertTrue(vqe.executeIteration());
        assertFalse(vqe.isConverged());
        assertEquals(1, vqe.getIterationCount());
        assertEquals(1, vqe.getCostHistory().size());
        assertEquals(1, vqe.getParameterHistory().size());
        double energy = vqe.getCurrentCost();
        assertTrue(energy >= -1.0 - EPS && energy <= 1.0 + EPS);
        assertEquals(energy, vqe.getCostHistory().get(0), 0.0);
        assertEquals(energy, vqe.getEnergy(), 0.0);

        // The second call is refused by the iteration limit without setting convergence.
        assertFalse(vqe.executeIteration());
        assertFalse(vqe.isConverged());
        assertEquals(1, vqe.getIterationCount());
    }

    @Test
    void vqeDetectsConvergenceAndReportsResultData() throws Exception {
        Qubit qubit = q(0);
        VariationalQuantumEigensolver vqe =
            new VariationalQuantumEigensolver(Observable.pauliZ(qubit), List.of(qubit));
        vqe.initialize(simulator(1), Map.of(
            "max_iterations", 2,
            "convergence_threshold", 1e9));

        assertTrue(vqe.executeIteration());
        assertFalse(vqe.isConverged());
        assertEquals(1, vqe.getIterationCount());

        assertFalse(vqe.executeIteration());
        assertTrue(vqe.isConverged());
        assertEquals(2, vqe.getIterationCount());
        assertEquals(2, vqe.getCostHistory().size());
        assertEquals(2, vqe.getParameterHistory().size());
        for (Map<String, Double> snapshot : vqe.getParameterHistory()) {
            assertEquals(4, snapshot.size());
        }
        assertEquals(vqe.getCurrentCost(),
            vqe.getCostHistory().get(vqe.getCostHistory().size() - 1), 0.0);

        SimulationResult result = vqe.getResult();
        assertEquals("VariationalQuantumEigensolver", result.getAdditionalData("algorithm"));
        assertEquals(vqe.getCurrentCost(), result.getAdditionalData("optimal_energy"));
        assertEquals(2, result.getAdditionalData("iterations"));
        assertEquals(Boolean.TRUE, result.getAdditionalData("converged"));
        assertEquals(vqe.getOptimalParameters(),
            result.getAdditionalData("optimal_parameters"));
        assertEquals(2, ((List<?>) result.getAdditionalData("cost_history")).size());
        assertTrue(result.getFinalState() != null);

        List<Double> costs = vqe.getCostHistory();
        costs.clear();
        assertEquals(2, vqe.getCostHistory().size());
    }

    @Test
    void vqeResetRestoresInitialState() throws Exception {
        Qubit qubit = q(0);
        VariationalQuantumEigensolver vqe =
            new VariationalQuantumEigensolver(Observable.pauliZ(qubit), List.of(qubit));
        Map<String, Double> initial = vqe.getOptimalParameters();
        vqe.initialize(simulator(1), Map.of("max_iterations", 5));

        assertTrue(vqe.executeIteration());
        assertFalse(vqe.getCostHistory().isEmpty());
        assertTrue(vqe.getCurrentCost() != Double.MAX_VALUE);

        vqe.reset();
        assertEquals(Double.MAX_VALUE, vqe.getCurrentCost());
        assertEquals(0, vqe.getIterationCount());
        assertFalse(vqe.isConverged());
        assertTrue(vqe.getCostHistory().isEmpty());
        assertTrue(vqe.getParameterHistory().isEmpty());
        assertEquals(initial, vqe.getOptimalParameters());
    }

    @Test
    void vqeMergesConfigurationAndValidatesQubitCount() throws Exception {
        Qubit qubit = q(0);
        VariationalQuantumEigensolver vqe =
            new VariationalQuantumEigensolver(Observable.pauliZ(qubit), List.of(qubit));

        QuantumSimulator twoQubits = simulator(2);
        assertThrows(IllegalArgumentException.class, () -> vqe.initialize(twoQubits, null));

        vqe.initialize(null, Map.of("learning_rate", 0.25));
        QuantumSimulator oneQubit = simulator(1);
        vqe.initialize(oneQubit, Map.of("max_iterations", 1));
        Map<String, Object> configuration = vqe.getParameters();
        assertEquals(0.25, configuration.get("learning_rate"));
        assertEquals(1, configuration.get("max_iterations"));

        vqe.setParameter("momentum", 0.5);
        assertEquals(0.5, vqe.getParameters().get("momentum"));
        Map<String, Object> copy = vqe.getParameters();
        copy.clear();
        assertEquals(0.5, vqe.getParameters().get("momentum"));

        assertTrue(vqe.executeIteration());
        assertFalse(vqe.isConverged());
        assertEquals(1, vqe.getIterationCount());
        assertFalse(vqe.executeIteration());
        assertFalse(vqe.isConverged());
        assertEquals(1, vqe.getIterationCount());
    }
}
