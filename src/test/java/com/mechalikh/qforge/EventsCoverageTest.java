package com.mechalikh.qforge;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.mechalikh.qforge.config.SimulationConfig;
import com.mechalikh.qforge.core.SimulationContext;
import com.mechalikh.qforge.events.EventDispatcher;
import com.mechalikh.qforge.events.EventPriority;
import com.mechalikh.qforge.events.EventQueue;
import com.mechalikh.qforge.events.EventType;
import com.mechalikh.qforge.events.QuantumEvent;
import com.mechalikh.qforge.operations.MultiQubitGate;
import com.mechalikh.qforge.operations.SingleQubitGate;
import com.mechalikh.qforge.state.Qubit;
import com.mechalikh.qforge.strategy.StateVectorStrategy;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class EventsCoverageTest {

    private static final Instant BASE = Instant.parse("2024-01-01T00:00:00Z");

    @Test
    void eventBuilderDefaultsAccessorsAndIdentity() {
        QuantumEvent defaults = new QuantumEvent.Builder("default").build();
        assertNotNull(defaults.getScheduledTime());
        assertEquals(EventPriority.NORMAL, defaults.getPriority());
        assertEquals(EventType.GATE_OPERATION, defaults.getType());
        assertNull(defaults.getOperation());
        assertEquals(0, defaults.getTargetQubits().length);
        assertEquals("", defaults.getDescription());
        assertFalse(defaults.isExecuted());
        assertNull(defaults.getExecutionTime());
        assertNull(defaults.getExecutionResult());
        assertNull(defaults.getDelay());

        Qubit q0 = new Qubit(0, "q0");
        Qubit q1 = new Qubit(1, "q1");
        Qubit q2 = new Qubit(2, "q2");
        QuantumEvent event = new QuantumEvent.Builder("custom")
            .withScheduledTime(BASE)
            .withPriority(EventPriority.HIGH)
            .withType(EventType.DELAY)
            .withDescription("desc")
            .withTargetQubits(q0, q1)
            .build();
        assertEquals("custom", event.getId());
        assertEquals(BASE, event.getScheduledTime());
        assertEquals(EventPriority.HIGH, event.getPriority());
        assertEquals(EventType.DELAY, event.getType());
        assertEquals("desc", event.getDescription());
        assertArrayEquals(new Qubit[]{q0, q1}, event.getTargetQubits());
        assertEquals(EventPriority.HIGH, event.getPriority());
        assertTrue(event.toString().contains("QuantumEvent"));

        Qubit[] returned = event.getTargetQubits();
        returned[0] = q2;
        assertEquals(q0, event.getTargetQubits()[0]);

        QuantumEvent sameId = new QuantumEvent.Builder("custom").build();
        assertEquals(event, sameId);
        assertEquals(event.hashCode(), sameId.hashCode());
        assertNotEquals(event, new QuantumEvent.Builder("other").build());
        assertNotEquals(event, null);
        assertNotEquals(event, "custom");

        QuantumEvent nulls = new QuantumEvent("n", BASE, EventPriority.LOW,
            EventType.CUSTOM_OPERATION, null, null, null);
        assertEquals(0, nulls.getTargetQubits().length);
        assertEquals("", nulls.getDescription());

        assertThrows(NullPointerException.class,
            () -> new QuantumEvent(null, BASE, EventPriority.NORMAL, EventType.DELAY, null, null, null));
        assertThrows(NullPointerException.class,
            () -> new QuantumEvent("x", null, EventPriority.NORMAL, EventType.DELAY, null, null, null));
        assertThrows(NullPointerException.class,
            () -> new QuantumEvent("x", BASE, null, EventType.DELAY, null, null, null));
        assertThrows(NullPointerException.class,
            () -> new QuantumEvent("x", BASE, EventPriority.NORMAL, null, null, null, null));
        assertThrows(NullPointerException.class,
            () -> new QuantumEvent.Builder("x").withScheduledTime(null).build());
    }

    @Test
    void eventTargetsAreReconciledWithOperationQubits() {
        Qubit q0 = new Qubit(0, "q0");
        Qubit q1 = new Qubit(1, "q1");
        Qubit q2 = new Qubit(2, "q2");
        MultiQubitGate cnot = MultiQubitGate.createCNOT(q0, q1);

        QuantumEvent derived = new QuantumEvent("derived", BASE, EventPriority.NORMAL,
            EventType.GATE_OPERATION, cnot, null, "cnot");
        assertArrayEquals(new Qubit[]{q1, q0}, derived.getTargetQubits());

        QuantumEvent unrelated = new QuantumEvent("unrelated", BASE, EventPriority.NORMAL,
            EventType.GATE_OPERATION, cnot, new Qubit[]{q2}, null);
        assertArrayEquals(new Qubit[]{q1, q0}, unrelated.getTargetQubits());

        QuantumEvent kept = new QuantumEvent("kept", BASE, EventPriority.NORMAL,
            EventType.GATE_OPERATION, cnot, new Qubit[]{q0, q1}, null);
        assertArrayEquals(new Qubit[]{q0, q1}, kept.getTargetQubits());

        QuantumEvent single = new QuantumEvent("single", BASE, EventPriority.NORMAL,
            EventType.GATE_OPERATION, SingleQubitGate.createPauliX(q0), new Qubit[]{q2}, null);
        assertArrayEquals(new Qubit[]{q0}, single.getTargetQubits());
    }

    @Test
    void eventOrderingUsesTimeThenPriorityThenSequence() {
        Qubit q = new Qubit(0, "q0");
        QuantumEvent later = new QuantumEvent.Builder("later")
            .withScheduledTime(BASE.plusSeconds(1)).build();
        QuantumEvent earlier = new QuantumEvent.Builder("earlier")
            .withScheduledTime(BASE).build();
        assertTrue(earlier.compareTo(later) < 0);
        assertTrue(later.compareTo(earlier) > 0);

        QuantumEvent first = new QuantumEvent.Builder("first")
            .withScheduledTime(BASE).withPriority(EventPriority.NORMAL).build();
        QuantumEvent second = new QuantumEvent.Builder("second")
            .withScheduledTime(BASE).withPriority(EventPriority.NORMAL).build();
        assertEquals(0, first.compareTo(first));
        assertTrue(first.compareTo(second) < 0);
        assertTrue(second.compareTo(first) > 0);

        // The current compareTo reverses the enum comparison, so among equal times the
        // numerically lower priority is dequeued first. Pin the existing behavior.
        QuantumEvent highest = new QuantumEvent.Builder("highest")
            .withScheduledTime(BASE).withPriority(EventPriority.HIGHEST).build();
        QuantumEvent lowest = new QuantumEvent.Builder("lowest")
            .withScheduledTime(BASE).withPriority(EventPriority.LOWEST).build();
        assertTrue(highest.compareTo(lowest) > 0);

        EventQueue queue = new EventQueue();
        queue.addEvent(lowest);
        queue.addEvent(highest);
        assertSame(lowest, queue.getNextEvent());
        assertSame(highest, queue.getNextEvent());

        EventQueue sequenceQueue = new EventQueue();
        sequenceQueue.addEvent(first);
        sequenceQueue.addEvent(second);
        assertSame(first, sequenceQueue.getNextEvent());
        assertSame(second, sequenceQueue.getNextEvent());

        QuantumEvent overdue = new QuantumEvent.Builder("overdue")
            .withScheduledTime(BASE).withOperation(SingleQubitGate.createPauliX(q)).build();
        assertTrue(overdue.isOverdue(BASE.plusMillis(1)));
        assertFalse(overdue.isOverdue(BASE));
        assertTrue(overdue.toString().contains("operation=X"));
    }

    @Test
    void eventExecuteAppliesOperationAndRecordsSuccess() throws Exception {
        SimulationContext context = initializedContext(1);
        Qubit q0 = context.getQubit(0);
        QuantumEvent event = new QuantumEvent.Builder("apply")
            .withScheduledTime(BASE)
            .withOperation(SingleQubitGate.createHadamard(q0))
            .build();

        event.execute(context);

        assertTrue(event.isExecuted());
        assertNotNull(event.getExecutionTime());
        assertNotNull(event.getExecutionResult());
        assertTrue(event.getExecutionResult().isSuccess());
        assertNull(event.getExecutionResult().getError());
        assertTrue(event.getExecutionResult().getMessage().contains("successfully"));
        assertNotNull(event.getExecutionResult().getExecutionTime());
        assertFalse(event.getExecutionResult().getExecutionDuration().isNegative());
        assertNotNull(event.getDelay());
        assertEquals(1L, context.getStepsExecuted());
        assertEquals(1L, context.getMetrics().getMetric("steps_executed").longValue());

        assertTrue(QuantumEvent.ExecutionResult.success(BASE, Duration.ofMillis(1), "ok")
            .toString().contains("success=true"));
        assertTrue(QuantumEvent.ExecutionResult.failure(BASE, Duration.ofMillis(1), "bad", null)
            .toString().contains("success=false"));

        QuantumEvent metadataOnly = new QuantumEvent.Builder("metadata").build();
        metadataOnly.execute(null);
        assertTrue(metadataOnly.isExecuted());
        assertTrue(metadataOnly.getExecutionResult().isSuccess());
    }

    @Test
    void eventExecutionFailureIsWrappedAndFinal() {
        Qubit q0 = new Qubit(0, "q0");
        QuantumEvent event = new QuantumEvent.Builder("failing")
            .withOperation(SingleQubitGate.createPauliX(q0))
            .build();

        QuantumEvent.EventExecutionException error = assertThrows(
            QuantumEvent.EventExecutionException.class, () -> event.execute(null));

        assertNotNull(error.getCause());
        assertTrue(error.getMessage().contains("failing"));
        assertTrue(event.isExecuted());
        assertNotNull(event.getExecutionResult());
        assertFalse(event.getExecutionResult().isSuccess());
        assertNotNull(event.getExecutionResult().getError());
        assertTrue(event.getExecutionResult().getError() instanceof NullPointerException);
        assertNotNull(event.getExecutionTime());
        assertNotNull(event.getDelay());

        assertThrows(IllegalStateException.class, () -> event.execute(null));
    }

    @Test
    void eventQueueAddsFindsRemovesAndRejectsDuplicates() {
        EventQueue queue = new EventQueue();
        QuantumEvent event = metadata("one", BASE);

        queue.addEvent(event);
        assertEquals(1, queue.size());
        assertFalse(queue.isEmpty());
        assertTrue(queue.containsEvent("one"));
        assertSame(event, queue.getEvent("one"));
        assertNull(queue.getEvent("missing"));
        assertThrows(IllegalArgumentException.class, () -> queue.addEvent(metadata("one", BASE)));
        assertThrows(NullPointerException.class, () -> queue.addEvent(null));

        assertTrue(queue.removeEvent("one"));
        assertFalse(queue.removeEvent("one"));
        assertFalse(queue.containsEvent("one"));
        assertTrue(queue.isEmpty());
        assertTrue(queue.getUpcomingEvents(5).isEmpty());
        assertTrue(queue.getEventsByType(EventType.DELAY).isEmpty());
        assertTrue(queue.getEventsInTimeRange(BASE, BASE.plusSeconds(1)).isEmpty());
        assertNull(queue.getNextEvent());
        assertEquals(0, queue.getTotalEventsProcessed());
        assertTrue(queue.toString().contains("EventQueue"));
    }

    @Test
    void eventQueueQueriesRescheduleAndAccounting() {
        EventQueue queue = new EventQueue();
        assertNotNull(queue.getCurrentTime());
        assertNull(queue.peekNextEvent());
        assertNull(queue.peekNextTime());
        assertEquals(0, queue.getMaxQueueSize());

        QuantumEvent first = metadata("first", BASE);
        QuantumEvent second = metadata("second", BASE.plusSeconds(1));
        QuantumEvent third = new QuantumEvent.Builder("third")
            .withScheduledTime(BASE.plusSeconds(2))
            .withType(EventType.DELAY)
            .withDescription("delay")
            .build();
        queue.addEvent(first);
        queue.addEvent(second);
        queue.addEvent(third);

        assertEquals(3, queue.getMaxQueueSize());
        assertEquals(BASE, queue.peekNextTime());
        assertSame(first, queue.peekNextEvent());
        assertEquals(2, queue.getUpcomingEvents(2).size());
        assertEquals(3, queue.getUpcomingEvents(10).size());
        assertEquals(1, queue.getEventsByType(EventType.DELAY).size());
        assertEquals("third", queue.getEventsByType(EventType.DELAY).get(0).getId());
        assertEquals(2, queue.getEventsInTimeRange(BASE, BASE.plusSeconds(1)).size());
        assertEquals(1, queue.getEventsInTimeRange(BASE.plusSeconds(1), BASE.plusSeconds(1)).size());
        assertEquals(0, queue.getEventsInTimeRange(BASE.minusSeconds(1), BASE.minusMillis(1)).size());

        queue.rescheduleEvent("second", BASE.plusSeconds(5));
        assertEquals(BASE.plusSeconds(5), queue.getEvent("second").getScheduledTime());
        assertEquals(EventPriority.NORMAL, queue.getEvent("second").getPriority());
        assertEquals(EventType.GATE_OPERATION, queue.getEvent("second").getType());
        assertEquals(BASE, queue.peekNextTime());
        assertThrows(IllegalArgumentException.class,
            () -> queue.rescheduleEvent("missing", BASE));

        EventQueue nullTimeQueue = new EventQueue();
        nullTimeQueue.addEvent(metadata("null-time", BASE));
        assertThrows(NullPointerException.class,
            () -> nullTimeQueue.rescheduleEvent("null-time", null));

        assertSame(first, queue.getNextEvent());
        assertEquals(BASE, queue.getCurrentTime());
        assertEquals(1, queue.getTotalEventsProcessed());
        assertEquals(2, queue.size());
        EventQueue.QueueStatistics statistics = queue.getStatistics();
        assertEquals(2, statistics.getCurrentSize());
        assertEquals(1, statistics.getTotalProcessed());
        assertEquals(3, statistics.getMaxSize());
        assertEquals(BASE, statistics.getCurrentTime());

        queue.clear();
        assertTrue(queue.isEmpty());
        assertFalse(queue.containsEvent("second"));
        assertEquals(1, queue.getTotalEventsProcessed());
        assertEquals(3, queue.getMaxQueueSize());
    }

    @Test
    void queueStatisticsComputeRatesAndDescription() {
        EventQueue.QueueStatistics statistics =
            new EventQueue.QueueStatistics(2, 4, 5, BASE.plusSeconds(2));
        assertEquals(2, statistics.getCurrentSize());
        assertEquals(4, statistics.getTotalProcessed());
        assertEquals(5, statistics.getMaxSize());
        assertEquals(BASE.plusSeconds(2), statistics.getCurrentTime());
        assertEquals(2.0, statistics.getAverageProcessingRate(BASE), 1e-12);
        assertTrue(statistics.toString().contains("QueueStats"));

        assertEquals(0.0, new EventQueue.QueueStatistics(0, 0, 0, BASE)
            .getAverageProcessingRate(BASE), 1e-12);
        assertEquals(0.0, new EventQueue.QueueStatistics(0, 4, 4, BASE)
            .getAverageProcessingRate(BASE), 1e-12);
    }

    @Test
    void eventPriorityAndTypeHelpers() {
        assertEquals(0, EventPriority.HIGHEST.getValue());
        assertEquals(4, EventPriority.LOWEST.getValue());
        assertTrue(EventPriority.HIGHEST.isHigherThan(EventPriority.LOW));
        assertFalse(EventPriority.LOW.isHigherThan(EventPriority.HIGH));
        assertTrue(EventPriority.LOWEST.isLowerThan(EventPriority.HIGH));
        assertFalse(EventPriority.HIGH.isLowerThan(EventPriority.NORMAL));
        assertEquals(EventPriority.HIGH, EventPriority.fromValue(1));
        assertThrows(IllegalArgumentException.class, () -> EventPriority.fromValue(99));
        assertEquals(EventPriority.HIGHEST, EventPriority.fromString("urgent"));
        assertEquals(EventPriority.HIGHEST, EventPriority.fromString("critical"));
        assertEquals(EventPriority.HIGH, EventPriority.fromString("IMPORTANT"));
        assertEquals(EventPriority.NORMAL, EventPriority.fromString("medium"));
        assertEquals(EventPriority.NORMAL, EventPriority.fromString("default"));
        assertEquals(EventPriority.LOW, EventPriority.fromString("BACKGROUND"));
        assertEquals(EventPriority.LOWEST, EventPriority.fromString("idle"));
        assertEquals(EventPriority.NORMAL, EventPriority.fromString("normal"));
        assertThrows(IllegalArgumentException.class, () -> EventPriority.fromString("nope"));

        assertEquals("Apply quantum gate", EventType.GATE_OPERATION.getDescription());
        assertTrue(EventType.GATE_OPERATION.isQuantumOperation());
        assertTrue(EventType.MEASUREMENT_OPERATION.isQuantumOperation());
        assertTrue(EventType.RESET_OPERATION.isQuantumOperation());
        assertTrue(EventType.STATE_INITIALIZATION.isQuantumOperation());
        assertFalse(EventType.STATE_MEASUREMENT.isQuantumOperation());
        assertTrue(EventType.NOISE_APPLICATION.isNoiseRelated());
        assertTrue(EventType.ERROR_INJECTION.isNoiseRelated());
        assertTrue(EventType.ERROR_CORRECTION.isNoiseRelated());
        assertFalse(EventType.DELAY.isNoiseRelated());
        assertTrue(EventType.DELAY.isControlEvent());
        assertTrue(EventType.BARRIER.isControlEvent());
        assertTrue(EventType.CONDITIONAL.isControlEvent());
        assertFalse(EventType.CHECKPOINT.isControlEvent());
        assertTrue(EventType.SIMULATION_START.isSystemEvent());
        assertTrue(EventType.SIMULATION_STOP.isSystemEvent());
        assertTrue(EventType.CHECKPOINT.isSystemEvent());
        assertTrue(EventType.RECOVERY.isSystemEvent());
        assertFalse(EventType.GATE_OPERATION.isSystemEvent());

        assertEquals(EventType.GATE_OPERATION, EventType.fromString("gate"));
        assertEquals(EventType.MEASUREMENT_OPERATION, EventType.fromString("measure qubit"));
        assertEquals(EventType.RESET_OPERATION, EventType.fromString("reset qubit"));
        assertEquals(EventType.NOISE_APPLICATION, EventType.fromString("noise model"));
        assertEquals(EventType.NOISE_APPLICATION, EventType.fromString("inject error"));
        assertEquals(EventType.DELAY, EventType.fromString("wait 10"));
        assertEquals(EventType.BARRIER, EventType.fromString("barrier"));
        assertEquals(EventType.SIMULATION_START, EventType.fromString("begin"));
        assertEquals(EventType.SIMULATION_STOP, EventType.fromString("end"));
        assertEquals(EventType.DELAY, EventType.fromString("DELAY"));
        assertEquals(EventType.CUSTOM_OPERATION, EventType.fromString("unrecognized"));
    }

    @Test
    void dispatcherSchedulesDispatchesAndPauses() throws Exception {
        EventDispatcher dispatcher = new EventDispatcher();
        SimulationContext context = initializedContext(1);
        assertEquals(0, dispatcher.getQueueSize());
        assertTrue(dispatcher.isQueueEmpty());
        assertFalse(dispatcher.isRunning());
        assertFalse(dispatcher.isPaused());
        assertNull(dispatcher.getNextEventTime());
        assertNull(dispatcher.getCurrentContext());
        assertTrue(dispatcher.toString().contains("EventDispatcher"));

        RecordingObserver observer = new RecordingObserver();
        dispatcher.registerObserver(observer);
        assertNull(dispatcher.dispatchNextEvent(context));
        assertEquals(List.of("empty"), observer.calls);

        QuantumEvent first = metadata("first", BASE);
        QuantumEvent second = metadata("second", BASE.plusSeconds(1));
        dispatcher.scheduleEvent(first);
        dispatcher.scheduleEvents(List.of(second));
        assertEquals(2, dispatcher.getQueueSize());
        assertEquals(BASE, dispatcher.getNextEventTime());
        assertEquals(2, dispatcher.getUpcomingEvents(10).size());

        assertSame(first, dispatcher.dispatchNextEvent(context));
        assertTrue(first.isExecuted());
        assertEquals(1, dispatcher.getEventsDispatched());
        assertEquals(0, dispatcher.getEventsFailed());
        assertEquals(1.0, dispatcher.getSuccessRate(), 1e-12);
        assertNull(dispatcher.getCurrentContext());
        assertFalse(dispatcher.getUptime().isNegative());
        assertEquals(1, dispatcher.getQueueStatistics().getCurrentSize());
        assertEquals(1, dispatcher.getQueueStatistics().getTotalProcessed());

        dispatcher.pause();
        assertTrue(dispatcher.isPaused());
        assertThrows(IllegalStateException.class, () -> dispatcher.dispatchNextEvent(context));
        assertEquals(1, dispatcher.getQueueSize());
        dispatcher.resume();
        assertFalse(dispatcher.isPaused());
        assertSame(second, dispatcher.dispatchNextEvent(context));

        dispatcher.scheduleEvent(metadata("third", BASE.plusSeconds(2)));
        dispatcher.stop();
        assertFalse(dispatcher.isRunning());
        dispatcher.clear();
        assertTrue(dispatcher.isQueueEmpty());
        assertTrue(observer.calls.contains("paused"));
        assertTrue(observer.calls.contains("resumed"));
        assertTrue(observer.calls.contains("stopped"));
        assertTrue(observer.calls.contains("cleared"));
    }

    @Test
    void dispatcherNotifiesPerRegistrationAndRemovesOneAtATime() throws Exception {
        EventDispatcher dispatcher = new EventDispatcher();
        SimulationContext context = initializedContext(1);
        RecordingObserver observer = new RecordingObserver();
        dispatcher.registerObserver(observer);
        dispatcher.registerObserver(observer);

        dispatcher.scheduleEvent(metadata("one", BASE));
        assertEquals(List.of("scheduled", "scheduled"), observer.calls);

        observer.calls.clear();
        dispatcher.dispatchNextEvent(context);
        assertEquals(List.of("before", "before", "after", "after"), observer.calls);
        assertNotNull(observer.executionTime);
        assertFalse(observer.executionTime.isNegative());
        assertNull(observer.result);

        observer.calls.clear();
        dispatcher.unregisterObserver(observer);
        dispatcher.scheduleEvent(metadata("two", BASE.plusSeconds(1)));
        assertEquals(List.of("scheduled"), observer.calls);

        observer.calls.clear();
        dispatcher.unregisterObserver(observer);
        dispatcher.scheduleEvent(metadata("three", BASE.plusSeconds(2)));
        assertTrue(observer.calls.isEmpty());
        assertEquals(2, dispatcher.getQueueSize());
    }

    @Test
    void dispatcherFailurePropagationCanBeSuppressed() {
        SimulationContext uninitialized = new SimulationContext(new SimulationConfig());
        Qubit q0 = new Qubit(0, "q0");

        EventDispatcher propagating = new EventDispatcher();
        propagating.scheduleEvent(new QuantumEvent.Builder("failing")
            .withOperation(SingleQubitGate.createPauliX(q0)).build());
        QuantumEvent.EventExecutionException error = assertThrows(
            QuantumEvent.EventExecutionException.class,
            () -> propagating.dispatchNextEvent(uninitialized));
        assertNotNull(error.getCause());
        assertEquals(1, propagating.getEventsFailed());
        assertEquals(0, propagating.getEventsDispatched());

        EventDispatcher quiet = new EventDispatcher() {
            @Override
            protected boolean shouldPropagateExceptions() {
                return false;
            }
        };
        RecordingObserver observer = new RecordingObserver();
        quiet.registerObserver(observer);
        quiet.scheduleEvent(new QuantumEvent.Builder("quiet-failure")
            .withOperation(SingleQubitGate.createPauliX(q0)).build());

        assertNull(quiet.dispatchNextEvent(uninitialized));
        assertEquals(1, quiet.getEventsFailed());
        assertEquals(0, quiet.getEventsDispatched());
        assertEquals(1.0, quiet.getSuccessRate(), 1e-12);
        assertTrue(observer.calls.contains("before"));
        assertTrue(observer.calls.contains("failed"));
        assertNotNull(observer.error);
        assertTrue(observer.error instanceof QuantumEvent.EventExecutionException);
    }

    @Test
    void dispatchAllNotifiesLifecycleAndRunsQueueToCompletion() throws Exception {
        EventDispatcher dispatcher = new EventDispatcher();
        SimulationContext context = initializedContext(1);
        RecordingObserver observer = new RecordingObserver();
        dispatcher.scheduleEvents(List.of(
            metadata("one", BASE),
            metadata("two", BASE.plusSeconds(1))));
        dispatcher.registerObserver(observer);

        dispatcher.dispatchAll(context);

        assertEquals(List.of("started", "before", "after", "before", "after", "completed"),
            observer.calls);
        assertEquals(2, dispatcher.getEventsDispatched());
        assertTrue(dispatcher.isQueueEmpty());
        assertFalse(dispatcher.isRunning());
        assertFalse(dispatcher.getUptime().isNegative());
    }

    @Test
    void dispatchAllHonorsPauseStopAndSingleRunGuard() throws Exception {
        EventDispatcher paused = new EventDispatcher();
        SimulationContext context = initializedContext(1);
        RecordingObserver pausedObserver = new RecordingObserver();
        paused.registerObserver(pausedObserver);
        paused.pause();
        paused.scheduleEvent(metadata("paused", BASE));

        paused.dispatchAll(context);
        assertEquals(1, paused.getQueueSize());
        assertTrue(pausedObserver.calls.contains("completed"));
        paused.resume();
        paused.dispatchAll(context);
        assertTrue(paused.isQueueEmpty());
        assertEquals(1, paused.getEventsDispatched());

        EventDispatcher stopped = new EventDispatcher();
        SimulationContext stoppingContext = initializedContext(1);
        stoppingContext.requestStop();
        stopped.scheduleEvents(List.of(
            metadata("one", BASE),
            metadata("two", BASE.plusSeconds(1))));
        stopped.dispatchAll(stoppingContext);
        assertEquals(1, stopped.getEventsDispatched());
        assertEquals(1, stopped.getQueueSize());
        assertFalse(stopped.isRunning());

        EventDispatcher guarded = new EventDispatcher();
        RecordingObserver guardedObserver = new RecordingObserver();
        AtomicReference<IllegalStateException> nested = new AtomicReference<>();
        guarded.registerObserver(new EventDispatcher.EventObserver() {
            @Override
            public void onDispatcherStarted(EventDispatcher dispatcher) {
                guardedObserver.calls.add("started");
                try {
                    dispatcher.dispatchAll(context);
                } catch (IllegalStateException e) {
                    nested.set(e);
                }
            }
        });
        guarded.scheduleEvent(metadata("guarded", BASE));
        guarded.dispatchAll(context);
        assertNotNull(nested.get());
        assertTrue(guarded.isQueueEmpty());
        assertFalse(guarded.isRunning());
    }

    private static QuantumEvent metadata(String id, Instant time) {
        return new QuantumEvent.Builder(id).withScheduledTime(time).build();
    }

    private static SimulationContext initializedContext(int qubits) throws Exception {
        SimulationConfig config = new SimulationConfig();
        config.setNumQubits(qubits);
        SimulationContext context = new SimulationContext(config);
        context.setStrategy(new StateVectorStrategy());
        context.initializeState();
        return context;
    }

    private static final class RecordingObserver implements EventDispatcher.EventObserver {
        private final List<String> calls = new ArrayList<>();
        private Duration executionTime;
        private QuantumEvent.ExecutionResult result;
        private Exception error;

        @Override
        public void onDispatcherStarted(EventDispatcher dispatcher) {
            calls.add("started");
        }

        @Override
        public void onDispatcherCompleted(EventDispatcher dispatcher) {
            calls.add("completed");
        }

        @Override
        public void onDispatcherPaused(EventDispatcher dispatcher) {
            calls.add("paused");
        }

        @Override
        public void onDispatcherResumed(EventDispatcher dispatcher) {
            calls.add("resumed");
        }

        @Override
        public void onDispatcherStopped(EventDispatcher dispatcher) {
            calls.add("stopped");
        }

        @Override
        public void onQueueCleared(EventDispatcher dispatcher) {
            calls.add("cleared");
        }

        @Override
        public void onQueueEmpty(EventDispatcher dispatcher) {
            calls.add("empty");
        }

        @Override
        public void onEventScheduled(EventDispatcher dispatcher, QuantumEvent event) {
            calls.add("scheduled");
        }

        @Override
        public void onBeforeEventExecution(EventDispatcher dispatcher, QuantumEvent event) {
            calls.add("before");
        }

        @Override
        public void onAfterEventExecution(EventDispatcher dispatcher, QuantumEvent event,
                                          Duration duration, QuantumEvent.ExecutionResult executionResult) {
            calls.add("after");
            executionTime = duration;
            result = executionResult;
        }

        @Override
        public void onEventExecutionFailed(EventDispatcher dispatcher, QuantumEvent event,
                                           Exception failure) {
            calls.add("failed");
            error = failure;
        }
    }
}
