package com.quantum.simulator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.quantum.simulator.config.ResourceConstraints;
import com.quantum.simulator.config.SimulationBackend;
import com.quantum.simulator.config.SimulationConfig;
import com.quantum.simulator.config.TimeResolution;
import com.quantum.simulator.core.QuantumSimulator;
import com.quantum.simulator.core.SimulationContext;
import com.quantum.simulator.core.SimulationError;
import com.quantum.simulator.core.SimulationResult;
import com.quantum.simulator.events.QuantumEvent;
import com.quantum.simulator.measurement.MeasurementOperator;
import com.quantum.simulator.measurement.MeasurementResult;
import com.quantum.simulator.observer.MetricsCollector;
import com.quantum.simulator.observer.SimulationObserver;
import com.quantum.simulator.operations.SingleQubitGate;
import com.quantum.simulator.state.DensityMatrix;
import com.quantum.simulator.state.Qubit;
import com.quantum.simulator.state.QuantumState;
import com.quantum.simulator.state.StateVector;
import com.quantum.simulator.strategy.StateVectorStrategy;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CoreCoverageTest {

    private static final long MB = 1024L * 1024L;

    @Test
    void configDefaultsValidationAndAccessors() {
        SimulationConfig config = new SimulationConfig();
        assertEquals("Simulation", config.getName());
        assertEquals("", config.getDescription());
        assertEquals(1, config.getNumQubits());
        assertEquals(SimulationBackend.STATE_VECTOR, config.getBackend());
        assertEquals(TimeResolution.NANOSECOND, config.getTimeResolution());
        assertNotNull(config.getResourceConstraints());
        assertNotNull(config.getNoiseProfile());
        assertFalse(config.hasNoise());
        assertNotNull(config.getGateLibrary());
        assertEquals(0, config.getMaxSteps());
        assertEquals(0.0, config.getTimeoutSeconds(), 0.0);
        assertFalse(config.isClassicalControlEnabled());
        assertTrue(config.getCustomParameters().isEmpty());
        assertTrue(config.validate());

        config.setCustomParameter("mode", "fast");
        assertEquals("fast", config.getCustomParameter("mode"));
        assertEquals("fast", config.getCustomParameters().get("mode"));
        assertThrows(UnsupportedOperationException.class,
            () -> config.getCustomParameters().put("other", "value"));
        assertNull(config.getCustomParameter("missing"));
        config.setCustomParameter("nullable", null);
        assertNull(config.getCustomParameter("nullable"));

        config.setName("Named");
        config.setDescription("Description");
        config.setNumQubits(3);
        config.setBackend(SimulationBackend.DENSITY_MATRIX);
        config.setTimeResolution(TimeResolution.SECOND);
        config.setMaxSteps(5);
        config.setTimeoutSeconds(2.5);
        config.setClassicalControlEnabled(true);
        config.setResourceConstraints(new ResourceConstraints(16L * MB, 1, 0));
        assertEquals("Named", config.getName());
        assertEquals("Description", config.getDescription());
        assertEquals(3, config.getNumQubits());
        assertEquals(SimulationBackend.DENSITY_MATRIX, config.getBackend());
        assertEquals(TimeResolution.SECOND, config.getTimeResolution());
        assertEquals(5, config.getMaxSteps());
        assertEquals(2.5, config.getTimeoutSeconds(), 0.0);
        assertTrue(config.isClassicalControlEnabled());
        assertTrue(config.validate());

        SimulationConfig zeroQubits = new SimulationConfig();
        zeroQubits.setNumQubits(0);
        assertFalse(zeroQubits.validate());
        zeroQubits.setNumQubits(-1);
        assertFalse(zeroQubits.validate());

        SimulationConfig nullBackend = new SimulationConfig();
        nullBackend.setBackend(null);
        assertFalse(nullBackend.validate());

        SimulationConfig tooManyQubits = new SimulationConfig();
        tooManyQubits.setNumQubits(31);
        assertFalse(tooManyQubits.validate());
        tooManyQubits.setBackend(SimulationBackend.DENSITY_MATRIX);
        tooManyQubits.setNumQubits(13);
        assertFalse(tooManyQubits.validate());

        SimulationConfig nullTime = new SimulationConfig();
        nullTime.setTimeResolution(null);
        assertFalse(nullTime.validate());

        SimulationConfig nullResources = new SimulationConfig();
        nullResources.setResourceConstraints(null);
        assertFalse(nullResources.validate());

        SimulationConfig invalidResources = new SimulationConfig();
        invalidResources.setResourceConstraints(new ResourceConstraints(0, 1, 0));
        assertFalse(invalidResources.validate());

        SimulationConfig negativeSteps = new SimulationConfig();
        negativeSteps.setMaxSteps(-1);
        assertFalse(negativeSteps.validate());

        SimulationConfig negativeTimeout = new SimulationConfig();
        negativeTimeout.setTimeoutSeconds(-0.1);
        assertFalse(negativeTimeout.validate());

        SimulationConfig tooLittleMemory = new SimulationConfig();
        tooLittleMemory.setNumQubits(2);
        tooLittleMemory.setResourceConstraints(new ResourceConstraints(1024L, 1, 0));
        assertFalse(tooLittleMemory.validate());
    }

    @Test
    void configRoundTripAndLoadRejections(@TempDir Path tempDir) throws Exception {
        SimulationConfig original = new SimulationConfig();
        original.setName("RoundTrip");
        original.setDescription("saved config");
        original.setNumQubits(2);
        original.setBackend(SimulationBackend.DENSITY_MATRIX);
        original.setTimeResolution(TimeResolution.MICROSECOND);
        original.setMaxSteps(3);
        original.setTimeoutSeconds(1.5);
        original.setClassicalControlEnabled(true);
        original.setResourceConstraints(new ResourceConstraints(16L * MB, 1, 0));
        original.setCustomParameter("mode", "fast");

        Path file = tempDir.resolve("config.json");
        original.saveToFile(file.toString());
        assertTrue(Files.size(file) > 0);

        SimulationConfig loaded = new SimulationConfig();
        loaded.loadFromFile(file.toString());
        assertEquals("RoundTrip", loaded.getName());
        assertEquals("saved config", loaded.getDescription());
        assertEquals(2, loaded.getNumQubits());
        assertEquals(SimulationBackend.DENSITY_MATRIX, loaded.getBackend());
        assertEquals(TimeResolution.MICROSECOND, loaded.getTimeResolution());
        assertEquals(3, loaded.getMaxSteps());
        assertEquals(1.5, loaded.getTimeoutSeconds(), 0.0);
        assertTrue(loaded.isClassicalControlEnabled());
        assertEquals(16L * MB, loaded.getResourceConstraints().getMaxMemoryBytes());
        assertEquals(1, loaded.getResourceConstraints().getMaxCPUs());
        assertEquals(0, loaded.getResourceConstraints().getMaxGPUs());
        assertEquals("fast", loaded.getCustomParameter("mode"));
        assertTrue(loaded.validate());

        Path commented = tempDir.resolve("commented.json");
        Files.writeString(commented, "{\n"
            + "  // comment\n"
            + "  \"numQubits\": 1,\n"
            + "  \"backend\": \"state_vector\",\n"
            + "  \"unknownField\": 42\n"
            + "}");
        SimulationConfig fromComments = new SimulationConfig();
        fromComments.loadFromFile(commented.toString());
        assertEquals(1, fromComments.getNumQubits());
        assertEquals(SimulationBackend.STATE_VECTOR, fromComments.getBackend());
        assertEquals("Simulation", fromComments.getName());

        assertIOException(tempDir, "zero-qubits.json",
            "{\"numQubits\": 0, \"backend\": \"STATE_VECTOR\"}");
        assertIOException(tempDir, "no-backend.json", "{\"numQubits\": 1}");
        assertIOException(tempDir, "negative-steps.json",
            "{\"numQubits\": 1, \"backend\": \"STATE_VECTOR\", \"maxSteps\": -1}");
        assertIOException(tempDir, "negative-timeout.json",
            "{\"numQubits\": 1, \"backend\": \"STATE_VECTOR\", \"timeoutSeconds\": -1}");
        assertThrows(IllegalArgumentException.class, () -> {
            Path unknownBackend = tempDir.resolve("unknown-backend.json");
            Files.writeString(unknownBackend,
                "{\"numQubits\": 1, \"backend\": \"TENSOR_NETWORK\"}");
            new SimulationConfig().loadFromFile(unknownBackend.toString());
        });
    }

    @Test
    void backendAndTimeResolutionEnums() {
        assertEquals("State Vector Simulation", SimulationBackend.STATE_VECTOR.getDescription());
        assertEquals("Density Matrix Simulation", SimulationBackend.DENSITY_MATRIX.getDescription());
        assertTrue(SimulationBackend.STATE_VECTOR.supportsNoise());
        assertTrue(SimulationBackend.DENSITY_MATRIX.supportsNoise());
        assertTrue(SimulationBackend.STATE_VECTOR.isExact());
        assertTrue(SimulationBackend.DENSITY_MATRIX.isExact());
        assertEquals(30, SimulationBackend.STATE_VECTOR.getMaxRecommendedQubits());
        assertEquals(12, SimulationBackend.DENSITY_MATRIX.getMaxRecommendedQubits());

        assertEquals(1L, TimeResolution.NANOSECOND.toNanoseconds());
        assertEquals(1_000L, TimeResolution.MICROSECOND.toNanoseconds());
        assertEquals(1_000_000L, TimeResolution.MILLISECOND.toNanoseconds());
        assertEquals(1_000_000_000L, TimeResolution.SECOND.toNanoseconds());
        assertEquals(60_000_000_000L, TimeResolution.MINUTE.toNanoseconds());
        assertEquals(3_600_000_000_000L, TimeResolution.HOUR.toNanoseconds());
        assertEquals(1.0, TimeResolution.SECOND.toSeconds(), 0.0);
        assertEquals(0.001, TimeResolution.MILLISECOND.toSeconds(), 0.0);
        assertEquals(Duration.ofSeconds(2), TimeResolution.SECOND.toDuration(2));
        assertEquals(Duration.ofNanos(5), TimeResolution.NANOSECOND.toDuration(5));
        assertEquals(1000L, TimeResolution.MILLISECOND.convert(1, TimeResolution.SECOND));
        assertEquals(1L, TimeResolution.SECOND.convert(1500, TimeResolution.MILLISECOND));
        assertEquals(-2L, TimeResolution.SECOND.convert(-2500, TimeResolution.MILLISECOND));

        assertEquals(TimeResolution.NANOSECOND, TimeResolution.fromString("ns"));
        assertEquals(TimeResolution.NANOSECOND, TimeResolution.fromString("nano"));
        assertEquals(TimeResolution.MICROSECOND, TimeResolution.fromString("us"));
        assertEquals(TimeResolution.MICROSECOND, TimeResolution.fromString("MICRO"));
        assertEquals(TimeResolution.MILLISECOND, TimeResolution.fromString("ms"));
        assertEquals(TimeResolution.MILLISECOND, TimeResolution.fromString("milli"));
        assertEquals(TimeResolution.SECOND, TimeResolution.fromString("s"));
        assertEquals(TimeResolution.SECOND, TimeResolution.fromString("SECOND"));
        assertEquals(TimeResolution.MINUTE, TimeResolution.fromString("m"));
        assertEquals(TimeResolution.MINUTE, TimeResolution.fromString("min"));
        assertEquals(TimeResolution.HOUR, TimeResolution.fromString("h"));
        assertEquals(TimeResolution.HOUR, TimeResolution.fromString("hr"));
        assertThrows(IllegalArgumentException.class, () -> TimeResolution.fromString("fortnight"));
    }

    @Test
    void contextExposesQubitsStateDataAndOperations() throws Exception {
        SimulationConfig config = new SimulationConfig();
        config.setNumQubits(3);
        SimulationContext context = new SimulationContext(config);

        assertSame(config, context.getConfig());
        assertNull(context.getStrategy());
        assertNull(context.getCurrentState());
        assertNull(context.getSimulationStartTime());
        assertNotNull(context.getMetrics());
        assertNotNull(context.getErrorManager());
        assertTrue(context.getMeasurements().isEmpty());
        assertEquals(0L, context.getStepsExecuted());
        assertEquals(3, context.getQubits().size());
        assertEquals("q0", context.getQubit(0).getLabel());
        assertEquals(1, context.getQubit(1).getIndex());
        assertEquals("q2", context.getQubit(2).getLabel());
        assertThrows(IndexOutOfBoundsException.class, () -> context.getQubit(-1));
        assertThrows(IndexOutOfBoundsException.class, () -> context.getQubit(3));

        List<Qubit> copy = context.getQubits();
        copy.clear();
        assertEquals(3, context.getQubits().size());

        assertThrows(SimulationError.class, context::initializeState);
        context.setStrategy(new StateVectorStrategy());
        context.initializeState();
        assertNotNull(context.getCurrentState());
        assertEquals(3, context.getCurrentState().getNumQubits());
        assertNotNull(context.getSimulationStartTime());
        assertTrue(context.getElapsedTimeMillis() >= 0);
        assertEquals(0.0, context.getMetrics().getMetric("state_updates").doubleValue(), 0.0);

        context.putData("key", "value");
        assertEquals("value", context.getData("key"));
        assertNull(context.getData("missing"));
        context.putData(null, "null-key");
        assertEquals("null-key", context.getData(null));

        context.applyOperation(SingleQubitGate.createPauliX(context.getQubit(0)));
        assertEquals(1L, context.getStepsExecuted());
        assertEquals(1.0, context.getMetrics().getMetric("state_updates").doubleValue(), 0.0);
        assertEquals(1.0, context.getMetrics().getMetric("operation_X_count").doubleValue(), 0.0);

        MeasurementResult measurement = new MeasurementResult("obs",
            new Qubit[]{context.getQubit(0)}, Map.of(0, 3, 1, 1), 4);
        context.addMeasurementResult(measurement);
        assertEquals(1, context.getMeasurements().size());
        assertSame(measurement, context.getMeasurements().get(0));
        assertEquals(1.0, context.getMetrics().getMetric("measurements").doubleValue(), 0.0);

        context.updateState(StateVector.createZeroState(3));
        assertEquals(2.0, context.getMetrics().getMetric("state_updates").doubleValue(), 0.0);
    }

    @Test
    void contextResetClearsStateAndHonorsStepLimit() throws Exception {
        SimulationConfig config = new SimulationConfig();
        config.setNumQubits(2);
        config.setMaxSteps(2);
        SimulationContext context = new SimulationContext(config);
        context.setStrategy(new StateVectorStrategy());
        context.initializeState();

        context.putData("key", "value");
        context.addMeasurementResult(new MeasurementResult("obs",
            new Qubit[]{context.getQubit(0)}, Map.of(0, 1), 1));
        context.requestStop();
        assertTrue(context.shouldStop());

        context.reset();
        assertFalse(context.shouldStop());
        assertEquals(0L, context.getStepsExecuted());
        assertTrue(context.getMeasurements().isEmpty());
        assertNull(context.getData("key"));
        assertNotNull(context.getCurrentState());
        assertNotNull(context.getSimulationStartTime());

        context.applyOperation(SingleQubitGate.createPauliX(context.getQubit(0)));
        assertFalse(context.shouldStop());
        context.applyOperation(SingleQubitGate.createPauliX(context.getQubit(0)));
        assertEquals(2L, context.getStepsExecuted());
        assertTrue(context.shouldStop());

        QuantumState oldState = context.getCurrentState();
        context.reset();
        assertNotSame(oldState, context.getCurrentState());
        assertEquals(0L, context.getStepsExecuted());
        assertFalse(context.shouldStop());

        SimulationContext uninitialized = new SimulationContext(new SimulationConfig());
        assertThrows(SimulationError.class, uninitialized::initializeState);
    }

    @Test
    void simulationResultAccessorsAndJson(@TempDir Path tempDir) throws Exception {
        Qubit q0 = new Qubit(0, "q0");
        StateVector state = StateVector.createPlusState(2);
        MeasurementResult first = new MeasurementResult("obs",
            new Qubit[]{q0}, Map.of(0, 2, 1, 2), 4);
        MeasurementResult second = new MeasurementResult("other",
            new Qubit[]{q0}, Map.of(0, 4), 4);
        MetricsCollector metrics = new MetricsCollector();
        metrics.recordMetric("steps_executed", 4);
        metrics.recordMetric("custom_metric", 2.5);

        SimulationResult result = new SimulationResult(
            state, List.of(first, second), metrics, 123L);

        assertSame(state, result.getFinalState());
        assertEquals(2, result.getMeasurements().size());
        assertSame(first, result.getMeasurement("obs"));
        assertSame(second, result.getMeasurement("other"));
        assertNull(result.getMeasurement("missing"));
        assertThrows(UnsupportedOperationException.class,
            () -> result.getMeasurements().add(first));
        assertEquals(4L, result.getMetric("steps_executed").longValue());
        assertEquals(2.5, result.getMetric("custom_metric").doubleValue(), 0.0);
        assertNull(result.getMetric("missing"));
        assertEquals(2.5, result.getMetrics().get("custom_metric").doubleValue(), 0.0);
        assertEquals(123L, result.getTotalRuntimeMs());
        assertNull(result.getAdditionalData("note"));
        result.putAdditionalData("note", "done");
        assertEquals("done", result.getAdditionalData("note"));
        Map<String, Object> dataCopy = result.getAdditionalData();
        dataCopy.clear();
        assertEquals("done", result.getAdditionalData("note"));

        String summary = result.toString();
        assertTrue(summary.contains("qubits=2"));
        assertTrue(summary.contains("runtime=123ms"));
        assertTrue(summary.contains("steps=4"));
        assertTrue(summary.contains("measurements=2"));

        String json = result.toJson();
        assertTrue(json.contains("StateVector"));
        assertTrue(json.contains("numQubits"));
        assertTrue(json.contains("runtimeMs"));
        assertTrue(json.contains("stepsExecuted"));
        assertTrue(json.contains("obs"));
        assertTrue(json.contains("other"));
        assertTrue(json.contains("additionalData"));
        assertTrue(json.contains("note"));

        Path saved = tempDir.resolve("nested/result.json");
        result.saveToFile(saved.toString());
        assertTrue(Files.exists(saved));
        assertTrue(Files.size(saved) > 0);
        assertTrue(Files.readString(saved).contains("runtimeMs"));

        SimulationResult withoutMetrics = new SimulationResult(state, List.of(), null, 5L);
        assertTrue(withoutMetrics.getMetrics().isEmpty());
        assertNull(withoutMetrics.getMetric("steps_executed"));
        assertTrue(withoutMetrics.toString().contains("steps=0"));
        assertTrue(withoutMetrics.toString().contains("measurements=0"));

        assertThrows(NullPointerException.class,
            () -> new SimulationResult(state, null, metrics, 0L));
        assertThrows(NullPointerException.class,
            () -> new SimulationResult(state, new ArrayList<>(Arrays.asList(first, null)),
                metrics, 0L));
    }

    @Test
    void simulatorLifecycleSchedulesStepsAndResets() throws Exception {
        SimulationConfig config = new SimulationConfig();
        config.setNumQubits(2);
        QuantumSimulator simulator = new QuantumSimulator();

        assertNull(simulator.getContext());
        assertNull(simulator.getPluginManager());
        assertNull(simulator.getClassicalController());
        assertFalse(simulator.isRunning());
        assertEquals(0L, simulator.getElapsedTime());
        QuantumEvent early = event("early", null);
        assertThrows(SimulationError.class, () -> simulator.scheduleEvent(early));
        assertThrows(SimulationError.class, simulator::run);
        assertThrows(SimulationError.class, simulator::step);

        RecordingObserver observer = new RecordingObserver();
        simulator.addObserver(observer);
        simulator.initialize(config);
        SimulationContext context = simulator.getContext();
        assertNotNull(context);
        assertSame(config, context.getConfig());
        assertNotNull(simulator.getPluginManager());
        assertNull(simulator.getClassicalController());
        assertEquals(2, context.getQubits().size());
        assertTrue(context.getCurrentState() instanceof StateVector);
        assertFalse(simulator.isRunning());
        assertEquals(0L, simulator.getElapsedTime());

        assertEquals(1, observer.initialized);
        Qubit q0 = context.getQubit(0);
        QuantumEvent hadamard = event("hadamard", SingleQubitGate.createHadamard(q0));
        simulator.scheduleEvent(hadamard);
        assertEquals(1, observer.scheduled);
        assertEquals(1.0, context.getMetrics().getMetric("events_scheduled").doubleValue(), 0.0);
        assertThrows(IllegalArgumentException.class, () -> simulator.scheduleEvent(hadamard));
        assertEquals(1, context.getMetrics().getMetric("events_scheduled").doubleValue(), 0.0);

        simulator.step();
        assertTrue(hadamard.isExecuted());
        assertEquals(1L, context.getStepsExecuted());
        assertEquals(1, observer.updates);
        assertThrows(SimulationError.class, simulator::step);

        QuantumState steppedState = context.getCurrentState();
        context.putData("key", "value");
        simulator.reset();
        assertEquals(1, observer.resets);
        assertEquals(0L, context.getStepsExecuted());
        assertTrue(context.getMeasurements().isEmpty());
        assertNull(context.getData("key"));
        assertNotSame(steppedState, context.getCurrentState());
        assertThrows(SimulationError.class, simulator::step);

        simulator.initialize(config);
        assertNotSame(context, simulator.getContext());
        assertFalse(simulator.isRunning());
        simulator.shutdown();
        assertFalse(simulator.isRunning());

        SimulationConfig invalid = new SimulationConfig();
        invalid.setNumQubits(0);
        assertThrows(SimulationError.class, () -> new QuantumSimulator().initialize(invalid));
    }

    @Test
    void simulatorStopEndsRunAfterCurrentEvent() throws Exception {
        SimulationConfig config = new SimulationConfig();
        config.setNumQubits(2);
        QuantumSimulator simulator = new QuantumSimulator();
        simulator.initialize(config);
        SimulationContext context = simulator.getContext();
        Qubit q0 = context.getQubit(0);
        Qubit q1 = context.getQubit(1);

        simulator.addObserver(new SimulationObserver() {
            @Override
            public void onStateUpdate(QuantumState state, QuantumEvent event) {
                assertTrue(simulator.getElapsedTime() >= 0);
                simulator.stop();
            }
        });
        simulator.scheduleEvent(event("first", SingleQubitGate.createPauliX(q0)));
        simulator.scheduleEvent(event("second", SingleQubitGate.createPauliX(q1)));

        SimulationResult firstRun = simulator.run();
        assertEquals(1L, firstRun.getMetric("steps_executed").longValue());
        assertEquals(1L, context.getStepsExecuted());
        assertFalse(simulator.isRunning());
        assertEquals(0L, simulator.getElapsedTime());

        SimulationResult secondRun = simulator.run();
        assertEquals(2L, secondRun.getMetric("steps_executed").longValue());
        assertEquals(2L, context.getStepsExecuted());
        assertTrue(secondRun.getMeasurements().isEmpty());

        SimulationResult emptyRun = simulator.run();
        assertEquals(2L, emptyRun.getMetric("steps_executed").longValue());
        assertEquals(2L, context.getStepsExecuted());
        assertTrue(emptyRun.getMeasurements().isEmpty());
        simulator.shutdown();
    }

    @Test
    void densityMatrixBackendAndMeasurementObservers() throws Exception {
        SimulationConfig dense = new SimulationConfig();
        dense.setNumQubits(1);
        dense.setBackend(SimulationBackend.DENSITY_MATRIX);
        QuantumSimulator densitySimulator = new QuantumSimulator();
        densitySimulator.initialize(dense);
        assertTrue(densitySimulator.getContext().getCurrentState() instanceof DensityMatrix);
        Qubit denseQubit = densitySimulator.getContext().getQubit(0);
        densitySimulator.scheduleEvent(event("h", SingleQubitGate.createHadamard(denseQubit)));
        SimulationResult densityResult = densitySimulator.run();
        assertTrue(densityResult.getFinalState() instanceof DensityMatrix);
        assertEquals(1, densityResult.getFinalState().getNumQubits());
        assertEquals(1L, densityResult.getMetric("steps_executed").longValue());
        densitySimulator.shutdown();

        SimulationConfig config = new SimulationConfig();
        config.setNumQubits(1);
        QuantumSimulator simulator = new QuantumSimulator();
        simulator.initialize(config);
        SimulationContext context = simulator.getContext();
        Qubit q0 = context.getQubit(0);
        RecordingObserver observer = new RecordingObserver();
        simulator.addObserver(observer);
        simulator.scheduleEvent(event("measure",
            MeasurementOperator.computationalBasis(new Qubit[]{q0}, 20), q0));
        SimulationResult result = simulator.run();
        assertEquals(1, result.getMeasurements().size());
        assertEquals(1, observer.measurements);
        assertEquals(1, observer.completed);
        assertNotNull(result.getMeasurements().get(0).getObservableName());
        simulator.shutdown();
    }

    private static QuantumEvent event(String id, com.quantum.simulator.operations.QuantumOperation operation,
                                       Qubit... targets) {
        QuantumEvent.Builder builder = new QuantumEvent.Builder(id).withOperation(operation);
        if (targets.length > 0) {
            builder.withTargetQubits(targets);
        }
        return builder.build();
    }

    private static void assertIOException(Path directory, String name, String json) throws Exception {
        Path file = directory.resolve(name);
        Files.writeString(file, json);
        SimulationConfig config = new SimulationConfig();
        assertThrows(IOException.class, () -> config.loadFromFile(file.toString()));
    }

    private static final class RecordingObserver implements SimulationObserver {
        private int initialized;
        private int scheduled;
        private int updates;
        private int measurements;
        private int completed;
        private int resets;

        @Override
        public void onInitialized(SimulationContext context) {
            initialized++;
        }

        @Override
        public void onEventScheduled(QuantumEvent event) {
            scheduled++;
        }

        @Override
        public void onStateUpdate(QuantumState state, QuantumEvent event) {
            updates++;
        }

        @Override
        public void onMeasurement(MeasurementResult result) {
            measurements++;
        }

        @Override
        public void onCompleted(SimulationResult result) {
            completed++;
        }

        @Override
        public void onReset() {
            resets++;
        }
    }
}
