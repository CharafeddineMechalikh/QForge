package com.mechalikh.qforge;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.mechalikh.qforge.config.SimulationBackend;
import com.mechalikh.qforge.config.SimulationConfig;
import com.mechalikh.qforge.core.QuantumSimulator;
import com.mechalikh.qforge.core.SimulationContext;
import com.mechalikh.qforge.core.SimulationResult;
import com.mechalikh.qforge.events.EventType;
import com.mechalikh.qforge.events.QuantumEvent;
import com.mechalikh.qforge.math.Complex;
import com.mechalikh.qforge.measurement.MeasurementResult;
import com.mechalikh.qforge.observer.MetricsCollector;
import com.mechalikh.qforge.observer.MetricsCollector.MeasurementRecord;
import com.mechalikh.qforge.observer.MetricsCollector.SimulationReport;
import com.mechalikh.qforge.observer.MetricsCollector.StateSnapshot;
import com.mechalikh.qforge.observer.SimulationObserver;
import com.mechalikh.qforge.observer.VisualizationEngine;
import com.mechalikh.qforge.operations.QuantumOperation;
import com.mechalikh.qforge.operations.SingleQubitGate;
import com.mechalikh.qforge.state.DensityMatrix;
import com.mechalikh.qforge.state.Qubit;
import com.mechalikh.qforge.state.QuantumState;
import com.mechalikh.qforge.state.StateVector;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ObserverCoverageTest {

    private static final Qubit Q0 = new Qubit(0, "q0");
    private static final Qubit Q1 = new Qubit(1, "q1");

    @Test
    void metricsDefaultsAndResetRestoreInitialValues() {
        MetricsCollector collector = new MetricsCollector();

        assertEquals(0L, collector.getMetric("state_updates").longValue());
        assertEquals(0L, collector.getMetric("measurements").longValue());
        assertEquals(0L, collector.getMetric("events_scheduled").longValue());
        assertEquals(0L, collector.getMetric("errors").longValue());
        assertEquals(1.0, collector.getMetric("average_fidelity").doubleValue());
        assertNull(collector.getMetric("missing"));

        collector.recordMetric("custom", 7);
        collector.onStateUpdate(StateVector.createZeroState(1), null);
        collector.onError("boom", new IllegalStateException("boom"));
        assertEquals(1L, collector.getMetric("state_updates").longValue());

        collector.onReset();

        assertEquals(0L, collector.getMetric("state_updates").longValue());
        assertNull(collector.getMetric("custom"));
        assertTrue(collector.getStateHistory().isEmpty());
        assertTrue(collector.getOperationCounts().isEmpty());
        assertTrue(collector.getErrorCounts().isEmpty());
        assertEquals(1.0, collector.getMetric("average_fidelity").doubleValue());
    }

    @Test
    void stateUpdatesCountOperationsAndKeepFirstQubitCount() {
        MetricsCollector collector = new MetricsCollector();
        StateVector state = StateVector.createZeroState(2);
        QuantumOperation gate = SingleQubitGate.createHadamard(new Qubit(6, "q6"));

        collector.onStateUpdate(state, TestSupport.event("h", gate, Q0));
        assertEquals(1L, collector.getMetric("state_updates").longValue());
        assertEquals(2L, collector.getMetric("num_qubits").longValue());
        assertEquals(1L, collector.getMetric("operation_H_count").longValue());
        assertNotNull(collector.getMetric("last_state_update"));

        collector.onStateUpdate(StateVector.createZeroState(1), null);
        assertEquals(2L, collector.getMetric("state_updates").longValue());
        assertEquals(2L, collector.getMetric("num_qubits").longValue());

        collector.recordOperation(gate);
        collector.recordOperation(gate);
        assertEquals(3L, collector.getMetric("operation_H_count").longValue());
        assertEquals(Map.of("H", 3L), collector.getOperationCounts());
    }

    @Test
    void eventSchedulingAndErrorsSetTimersAndCounts() {
        MetricsCollector collector = new MetricsCollector();
        QuantumEvent event = TestSupport.event("h", SingleQubitGate.createHadamard(Q0), Q0);

        collector.recordEventScheduled(event);
        assertEquals(1L, collector.getMetric("events_scheduled").longValue());
        assertNull(collector.getMetric("simulation_start_time"));

        collector.onEventScheduled(event);
        assertEquals(2L, collector.getMetric("events_scheduled").longValue());
        assertNotNull(collector.getMetric("simulation_start_time"));

        collector.onError("first", new IllegalArgumentException("bad"));
        collector.onError("second", null);
        assertEquals(2L, collector.getMetric("errors").longValue());
        assertEquals(1L, collector.getMetric("error_IllegalArgumentException_count").longValue());
        assertEquals(1L, collector.getMetric("error_Unknown_count").longValue());
        assertNotNull(collector.getMetric("last_error_time"));
        assertEquals(Map.of("IllegalArgumentException", 1L, "Unknown", 1L),
            collector.getErrorCounts());
    }

    @Test
    void initializationReadsConfigurationAndIgnoresNullContext() {
        MetricsCollector collector = new MetricsCollector();
        collector.onInitialized(null);
        assertNull(collector.getMetric("num_qubits"));
        assertNull(collector.getMetric("backend"));

        SimulationConfig config = new SimulationConfig();
        config.setNumQubits(3);
        config.setBackend(SimulationBackend.DENSITY_MATRIX);
        SimulationContext context = new SimulationContext(config);

        collector.onInitialized(context);

        assertEquals(3L, collector.getMetric("num_qubits").longValue());
        assertEquals((long) SimulationBackend.DENSITY_MATRIX.ordinal(),
            collector.getMetric("backend").longValue());
        assertNotNull(collector.getMetric("simulation_start_time"));
    }

    @Test
    void measurementsUpdateHistoryAndRunningStatistics() {
        MetricsCollector collector = new MetricsCollector();
        MeasurementResult balanced = new MeasurementResult("Z", new Qubit[]{Q0},
            Map.of(0, 50, 1, 50), 100, new double[]{1.0, -1.0});
        MeasurementResult zeros = new MeasurementResult("Z", new Qubit[]{Q0},
            Map.of(0, 100), 100, new double[]{1.0, -1.0});

        collector.onMeasurement(balanced);
        assertEquals(1L, collector.getMetric("measurements").longValue());
        assertEquals(0.0, collector.getMetric("average_expectation").doubleValue(), 1e-9);
        assertEquals(1.0, collector.getMetric("average_variance").doubleValue(), 1e-9);

        collector.recordMeasurement(zeros);
        assertEquals(2L, collector.getMetric("measurements").longValue());
        assertEquals(0.5, collector.getMetric("average_expectation").doubleValue(), 1e-9);
        assertEquals(0.5, collector.getMetric("average_variance").doubleValue(), 1e-9);
        assertEquals(0.0, collector.getMetric("min_expectation").doubleValue(), 1e-9);
        assertEquals(1.0, collector.getMetric("max_expectation").doubleValue(), 1e-9);
        assertEquals(0.0, collector.getMetric("min_variance").doubleValue(), 1e-9);
        assertEquals(1.0, collector.getMetric("max_variance").doubleValue(), 1e-9);
        assertNotNull(collector.getMetric("last_measurement"));

        List<MeasurementRecord> history = collector.getMeasurementHistory();
        assertEquals(2, history.size());
        assertEquals("Z", history.get(0).getObservableName());
        assertEquals(100, history.get(0).getShots());
        assertEquals(0.0, history.get(0).getExpectationValue(), 1e-9);
        assertEquals(1.0, history.get(0).getVariance(), 1e-9);
        assertNotNull(history.get(0).getTimestamp());
        assertTrue(history.get(0).toString().contains("obs='Z'"));
    }

    @Test
    void stateTransitionsTrackFidelityDistanceAndFidelitySamples() {
        MetricsCollector collector = new MetricsCollector();
        StateVector zero = StateVector.createZeroState(1);
        StateVector one = StateVector.fromComputationalBasis(1, 1);

        collector.recordStateTransition(zero, zero);
        assertEquals(1.0, collector.getMetric("current_fidelity").doubleValue(), 1e-9);
        assertEquals(0.0, collector.getMetric("last_state_distance").doubleValue(), 1e-9);
        assertEquals(1.0, collector.getMetric("average_fidelity").doubleValue(), 1e-9);

        collector.recordStateTransition(zero, one);
        collector.recordStateTransition(zero, StateVector.createZeroState(2));

        assertEquals(0.0, collector.getMetric("current_fidelity").doubleValue(), 1e-9);
        assertEquals(2.0 / 3.0, collector.getMetric("average_state_distance").doubleValue(), 1e-9);
        assertEquals(1.0 / 3.0, collector.getMetric("average_fidelity").doubleValue(), 1e-9);

        collector.recordStateTransition(DensityMatrix.createZeroState(1),
            DensityMatrix.createZeroState(1));
        assertEquals(1.0, collector.getMetric("current_fidelity").doubleValue(), 1e-9);
        assertEquals(0.0, collector.getMetric("last_state_distance").doubleValue(), 1e-9);

        collector.recordFidelity(0.5);
        assertEquals(0.5, collector.getMetric("current_fidelity").doubleValue(), 1e-9);
        assertEquals(0.5, collector.getMetric("average_fidelity").doubleValue(), 1e-9);

        collector.recordOperation(SingleQubitGate.createPauliX(Q0));
        assertEquals(1L, collector.getOperationCounts().get("X").longValue());
    }

    @Test
    void snapshotsAreCapturedEveryHundredStateUpdates() {
        MetricsCollector collector = new MetricsCollector();
        for (int i = 0; i < 100; i++) {
            collector.onStateUpdate(StateVector.createZeroState(1), null);
        }

        List<StateSnapshot> snapshots = collector.getStateHistory();
        assertEquals(1, snapshots.size());
        assertEquals(1, snapshots.get(0).getNumQubits());
        assertEquals(1.0, snapshots.get(0).getPurity(), 1e-9);
        assertEquals("StateVector", snapshots.get(0).getStateType());
        assertNotNull(snapshots.get(0).getTimestamp());
        assertTrue(snapshots.get(0).toString().contains("type=StateVector"));

        MetricsCollector densityCollector = new MetricsCollector();
        for (int i = 0; i < 100; i++) {
            densityCollector.onStateUpdate(DensityMatrix.createZeroState(1), null);
        }
        assertEquals("DensityMatrix",
            densityCollector.getStateHistory().get(0).getStateType());
    }

    @Test
    void completionComputesDurationOperationRatesAndReport() {
        MetricsCollector collector = new MetricsCollector();
        StateVector state = StateVector.createZeroState(1);
        collector.recordStartTime(Instant.now().minusSeconds(2));
        collector.recordOperation(SingleQubitGate.createHadamard(Q0));
        collector.recordOperation(SingleQubitGate.createHadamard(Q0));
        collector.onStateUpdate(state, null);

        SimulationResult result = new SimulationResult(state, List.of(), collector, 5);
        collector.onCompleted(result);

        assertNotNull(collector.getMetric("simulation_end_time"));
        assertTrue(collector.getMetric("simulation_duration_ms").longValue() >= 2000);
        assertTrue(collector.getMetric("total_simulation_time_ms").longValue() >= 2000);
        assertEquals(2L, collector.getMetric("total_operations").longValue());
        assertEquals(0.0, collector.getMetric("error_rate").doubleValue(), 1e-9);
        assertTrue(collector.getMetric("operations_per_second").doubleValue() > 0.0);
        assertTrue(collector.getMetric("state_updates_per_ms").doubleValue() > 0.0);

        SimulationReport report = collector.generateReport();
        assertNotNull(report.getStartTime());
        assertNotNull(report.getEndTime());
        assertNotNull(report.getReportTime());
        assertTrue(report.getDuration().toMillis() > 0);
        assertTrue(report.getThroughput() > 0.0);
        assertEquals(2L, report.getOperationCounts().get("H").longValue());
        assertTrue(report.toSummaryString().contains("Operations: 2"));
        assertTrue(report.toSummaryString().contains("Throughput:"));
        assertEquals(report.toSummaryString(), report.toString());
    }

    @Test
    void completionWithoutStartTimeUsesZeroDurationAndErrorRate() {
        MetricsCollector collector = new MetricsCollector();
        collector.recordOperation(SingleQubitGate.createPauliX(Q0));
        collector.onError("failure", new RuntimeException("failure"));

        SimulationResult result = new SimulationResult(StateVector.createZeroState(1),
            List.of(), collector, 3);
        collector.onCompleted(result);

        assertEquals(0L, collector.getMetric("simulation_duration_ms").longValue());
        assertEquals(0L, collector.getMetric("simulation_duration_seconds").longValue());
        assertEquals(1.0, collector.getMetric("error_rate").doubleValue(), 1e-9);
        assertNull(collector.getMetric("operations_per_second"));
        assertNull(collector.getMetric("state_updates_per_ms"));

        SimulationReport report = collector.generateReport();
        assertNull(report.getStartTime());
        assertNotNull(report.getEndTime());
        assertEquals(java.time.Duration.ZERO, report.getDuration());
        assertEquals(0.0, report.getThroughput(), 1e-12);
        assertFalse(report.toSummaryString().contains("Duration:"));
    }

    @Test
    void reportHoldsAnImmutableSnapshotOfTheCollector() {
        MetricsCollector collector = new MetricsCollector();
        collector.recordOperation(SingleQubitGate.createHadamard(Q0));
        collector.onError("failure", new IllegalStateException("bad"));
        collector.recordMeasurement(new MeasurementResult("Z", new Qubit[]{Q0},
            Map.of(0, 10), 10, new double[]{1.0, -1.0}));

        SimulationReport report = collector.generateReport();
        assertEquals(1L, report.getOperationCounts().get("H").longValue());
        assertEquals(1L, report.getErrorCounts().get("IllegalStateException").longValue());
        assertEquals(1, report.getMeasurementHistory().size());
        assertThrows(UnsupportedOperationException.class,
            () -> report.getMetrics().put("injected", 1));
        assertThrows(UnsupportedOperationException.class,
            () -> report.getOperationCounts().put("injected", 1L));
        assertThrows(UnsupportedOperationException.class,
            () -> report.getMeasurementHistory().clear());

        collector.recordOperation(SingleQubitGate.createHadamard(Q0));
        assertEquals(1L, report.getOperationCounts().get("H").longValue());
        assertTrue(report.getMetrics().get("average_fidelity").doubleValue() == 1.0);
    }

    @Test
    void visualizationRendersImagesAndWritesPng(@TempDir Path directory) throws IOException {
        VisualizationEngine engine = new VisualizationEngine(10, directory.toString());
        StateVector plus = StateVector.createPlusState(2);

        BufferedImage vectorImage = engine.renderState(plus);
        assertEquals(800, vectorImage.getWidth());
        assertEquals(600, vectorImage.getHeight());
        assertEquals(BufferedImage.TYPE_INT_RGB, vectorImage.getType());

        BufferedImage matrixImage = engine.renderState(DensityMatrix.createZeroState(2));
        assertEquals(600, matrixImage.getWidth());
        assertEquals(600, matrixImage.getHeight());

        BufferedImage placeholder = engine.renderState(new UnsupportedState());
        assertEquals(400, placeholder.getWidth());
        assertEquals(300, placeholder.getHeight());
        assertEquals(0xFFFFFF, placeholder.getRGB(0, 0) & 0xFFFFFF);
        assertEquals(0xFFFFFF, placeholder.getRGB(399, 299) & 0xFFFFFF);

        engine.saveImage(vectorImage, "state.png");
        Path image = directory.resolve("state.png");
        assertPng(image);
    }

    @Test
    void visualizationCapturesHistoryEventsAndLimitsSize(@TempDir Path directory) {
        VisualizationEngine engine = new VisualizationEngine(2, directory.toString());
        QuantumOperation gate = SingleQubitGate.createHadamard(Q0);

        for (int i = 0; i < 3; i++) {
            engine.onStateUpdate(StateVector.createPlusState(1),
                TestSupport.event("h" + i, gate, Q0));
        }

        assertEquals(2, engine.getStateHistory().size());
        assertEquals(2, engine.getEventHistory().size());
        VisualizationEngine.StateSnapshot snapshot = engine.getStateHistory().get(1);
        assertEquals(VisualizationEngine.StateType.STATE_VECTOR, snapshot.getType());
        assertNotNull(snapshot.getTimestamp());
        assertSame(snapshot.getState(), engine.getStateHistory().get(1).getState());
        assertTrue(snapshot.toString().contains("qubits=1"));

        VisualizationEngine.EventRecord record = engine.getEventHistory().get(1);
        assertEquals(EventType.GATE_OPERATION, record.getType());
        assertEquals("H", record.getOperationName());
        assertEquals(1, record.getTargetQubits().length);
        assertTrue(record.toString().contains("operation='H'"));
        Qubit[] copy = record.getTargetQubits();
        copy[0] = Q1;
        assertEquals(0, record.getTargetQubits()[0].getIndex());

        engine.recordEvent(new QuantumEvent.Builder("meta")
            .withType(EventType.SCHEDULING)
            .build());
        assertEquals("SCHEDULING",
            engine.getEventHistory().get(engine.getEventHistory().size() - 1)
                .getOperationName());

        engine.captureSnapshot(DensityMatrix.createZeroState(1));
        assertEquals(VisualizationEngine.StateType.DENSITY_MATRIX,
            engine.getStateHistory().get(engine.getStateHistory().size() - 1).getType());

        int stateSize = engine.getStateHistory().size();
        int eventSize = engine.getEventHistory().size();
        engine.setCaptureHistory(false);
        engine.onStateUpdate(StateVector.createZeroState(1),
            TestSupport.event("ignored", gate, Q0));
        assertEquals(stateSize, engine.getStateHistory().size());
        assertEquals(eventSize, engine.getEventHistory().size());

        engine.clearHistory();
        assertTrue(engine.getStateHistory().isEmpty());
        assertTrue(engine.getEventHistory().isEmpty());
    }

    @Test
    void visualizationWritesSummaryPngsAndSkipsMissingData(@TempDir Path directory)
        throws IOException {
        VisualizationEngine engine = new VisualizationEngine(10, directory.toString());
        StateVector state = StateVector.createPlusState(2);
        engine.captureSnapshot(StateVector.createZeroState(2));
        engine.captureSnapshot(state);
        engine.recordEvent(TestSupport.event("h", SingleQubitGate.createHadamard(Q0), Q0));

        SimulationResult result = new SimulationResult(state, List.of(), null, 0);
        engine.onCompleted(result);

        assertPng(directory.resolve("final_state.png"));
        assertPng(directory.resolve("event_timeline.png"));
        assertPng(directory.resolve("metrics_plot.png"));

        Path emptyDirectory = directory.resolve("empty");
        VisualizationEngine empty = new VisualizationEngine(10, emptyDirectory.toString());
        empty.generateSummaryVisualizations(new SimulationResult(null, List.of(), null, 0));
        assertFalse(Files.exists(emptyDirectory.resolve("final_state.png")));
        assertFalse(Files.exists(emptyDirectory.resolve("event_timeline.png")));
        assertFalse(Files.exists(emptyDirectory.resolve("metrics_plot.png")));
    }

    @Test
    void visualizationGeneratesAnimatedGif(@TempDir Path directory) throws IOException {
        VisualizationEngine engine = new VisualizationEngine(10, directory.toString());
        engine.generateAnimation("empty", 100);
        assertFalse(Files.exists(directory.resolve("empty.gif")));

        engine.captureSnapshot(StateVector.createZeroState(1));
        engine.captureSnapshot(StateVector.createPlusState(1));
        engine.captureSnapshot(StateVector.fromComputationalBasis(1, 1));

        engine.generateAnimation("animation", 120);
        Path gif = directory.resolve("animation.gif");
        assertTrue(Files.exists(gif));
        assertTrue(Files.size(gif) > 0);
        byte[] bytes = Files.readAllBytes(gif);
        assertArrayEquals("GIF89a".getBytes(StandardCharsets.US_ASCII),
            Arrays.copyOf(bytes, 6));
    }

    @Test
    void simulatorObserverRegistrationIsAdditiveAndRemovable() throws Exception {
        SimulationConfig config = new SimulationConfig();
        config.setNumQubits(1);
        QuantumSimulator simulator = new QuantumSimulator();
        RecordingObserver observer = new RecordingObserver();

        simulator.addObserver(observer);
        simulator.addObserver(observer);
        simulator.initialize(config);
        assertEquals(2, observer.initialized);

        simulator.run();
        assertEquals(2, observer.completed);

        simulator.removeObserver(observer);
        simulator.reset();
        assertEquals(1, observer.reset);

        simulator.removeObserver(observer);
        simulator.reset();
        assertEquals(1, observer.reset);

        simulator.removeObserver(new RecordingObserver());
        simulator.run();
        assertEquals(2, observer.completed);
    }

    private static void assertPng(Path path) throws IOException {
        assertTrue(Files.exists(path), "missing " + path);
        assertTrue(Files.size(path) > 0, "empty " + path);
        byte[] bytes = Files.readAllBytes(path);
        assertTrue(bytes.length > 8);
        assertArrayEquals(new byte[]{(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A},
            Arrays.copyOf(bytes, 8));
    }

    private static class RecordingObserver implements SimulationObserver {
        int initialized;
        int completed;
        int reset;

        @Override
        public void onInitialized(SimulationContext context) {
            initialized++;
        }

        @Override
        public void onCompleted(SimulationResult result) {
            completed++;
        }

        @Override
        public void onReset() {
            reset++;
        }
    }

    private static final class UnsupportedState extends QuantumState {
        UnsupportedState() {
            super(1);
        }

        @Override
        public QuantumState applyOperation(QuantumOperation operation) {
            return this;
        }

        @Override
        public Complex getAmplitude(int basisState) {
            return Complex.ZERO;
        }

        @Override
        public double getProbability(int basisState) {
            return 0.0;
        }

        @Override
        public double[] getProbabilities() {
            return new double[]{1.0};
        }

        @Override
        public QuantumState tensorProduct(QuantumState other) {
            return this;
        }

        @Override
        public QuantumState normalize() {
            return this;
        }

        @Override
        public boolean isPure() {
            return true;
        }

        @Override
        public double getPurity() {
            return 1.0;
        }

        @Override
        public QuantumState copy() {
            return this;
        }

        @Override
        public QuantumState collapseToBasisState(int[] targetQubits, int outcome) {
            return this;
        }

        @Override
        public QuantumState collapseToEigenvector(int[] targetQubits, Complex[] eigenvector) {
            return this;
        }

        @Override
        public boolean equals(Object obj) {
            return this == obj;
        }

        @Override
        public int hashCode() {
            return 1;
        }

        @Override
        public String toString() {
            return "UnsupportedState";
        }
    }
}
