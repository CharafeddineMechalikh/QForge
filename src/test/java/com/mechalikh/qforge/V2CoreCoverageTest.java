package com.mechalikh.qforge;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.mechalikh.qforge.classical.ClassicalRegister;
import com.mechalikh.qforge.config.SimulationBackend;
import com.mechalikh.qforge.config.SimulationConfig;
import com.mechalikh.qforge.core.QuantumSimulator;
import com.mechalikh.qforge.core.SimulationError;
import com.mechalikh.qforge.core.SimulationResult;
import com.mechalikh.qforge.events.EventType;
import com.mechalikh.qforge.events.QuantumEvent;
import com.mechalikh.qforge.measurement.MeasurementOperator;
import com.mechalikh.qforge.measurement.Observable;
import com.mechalikh.qforge.noise.AmplitudeDampingNoise;
import com.mechalikh.qforge.noise.DephasingNoise;
import com.mechalikh.qforge.noise.DeviceNoiseModel;
import com.mechalikh.qforge.noise.NoiseProfile;
import com.mechalikh.qforge.noise.ReadoutErrorModel;
import com.mechalikh.qforge.noise.ThermalRelaxationModel;
import com.mechalikh.qforge.operations.GateLibrary;
import com.mechalikh.qforge.operations.QuantumOperation;
import com.mechalikh.qforge.state.Qubit;
import com.mechalikh.qforge.state.StabilizerState;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Branch coverage for the version 2 features: device noise wiring, classical targets,
 * stabilizer measurement, configuration fields and strategy selection.
 */
class V2CoreCoverageTest {
    private static final double EPSILON = 1e-9;
    private static final Qubit Q0 = new Qubit(0, "q0");
    private static final Qubit Q1 = new Qubit(1, "q1");
    private static final Qubit Q2 = new Qubit(2, "q2");

    @Test
    void measurementOperatorRejectsInvalidClassicalTargets() {
        MeasurementOperator operator = MeasurementOperator.computationalBasis(
            new Qubit[]{Q0}, 1);
        assertThrows(NullPointerException.class,
            () -> operator.withClassicalTarget(null, 0));
        assertThrows(IllegalArgumentException.class,
            () -> operator.withClassicalTarget("c", -1));
        assertEquals("c", operator.withClassicalTarget("c", 1).getRegisterName());
        assertEquals(1, operator.withClassicalTarget("c", 1).getRegisterOffset());
    }

    @Test
    void stabilizerMeasurementSamplesAndCollapses() {
        StabilizerState state = new StabilizerState(1);
        state = state.applyGate(new GateLibrary().createGate("X", Q0));
        MeasurementOperator operator = MeasurementOperator.computationalBasis(
            new Qubit[]{Q0}, 2);

        MeasurementOperator.MeasurementOutcome outcome = operator.measureAndCollapse(state);
        assertEquals(1, outcome.getOutcome());
        assertEquals(2, outcome.getResult().getCount(1));

        ReadoutErrorModel flip = new ReadoutErrorModel(new double[]{0.0}, new double[]{1.0});
        outcome = operator.measureAndCollapse(state, flip);
        assertEquals(1, outcome.getOutcome());
        assertEquals(2, outcome.getResult().getCount(0));

        MeasurementOperator kept = new MeasurementOperator(
            "kept", Observable.pauliZ(Q0), 2, false);
        assertSame(state, kept.measureAndCollapse(state).getCollapsedState());
    }

    @Test
    void stabilizerBackendRunsAndWritesRegister() throws SimulationError {
        SimulationConfig config = new SimulationConfig();
        config.setNumQubits(1);
        config.setBackend(SimulationBackend.STABILIZER);
        config.addClassicalRegister("c", 1);
        SimulationResult result = simulate(config, List.of(
            gate("h", Instant.now(), "H", Q0),
            event("m", Instant.now().plusNanos(1),
                MeasurementOperator.computationalBasis(new Qubit[]{Q0}, 1)
                    .withClassicalTarget("c", 0))));
        assertEquals(1, result.getRegisterValues().size());
        assertTrue(result.getRegisterValues().get("c") == 0L
            || result.getRegisterValues().get("c") == 1L);
    }

    @Test
    void tensorNetworkBackendBuildsAnMpsState() throws SimulationError {
        SimulationConfig config = new SimulationConfig();
        config.setNumQubits(2);
        config.setBackend(SimulationBackend.TENSOR_NETWORK);
        config.setMaxBondDimension(8);
        SimulationResult result = simulate(config, List.of(
            gate("h", Instant.now(), "H", Q0),
            event("cx", Instant.now().plusNanos(1),
                new GateLibrary().createGate("CNOT", Q0, Q1))));
        assertEquals(0.5, result.getFinalState().getProbability(0), 1e-9);
        assertEquals(0.5, result.getFinalState().getProbability(3), 1e-9);
        assertEquals(8, config.getMaxBondDimension());
    }

    @Test
    void noiseEventAndConfiguredNoiseRunThroughContext() throws SimulationError {
        SimulationConfig config = new SimulationConfig();
        config.setNumQubits(2);
        NoiseProfile profile = new NoiseProfile();
        profile.addGlobalNoise("global", new DephasingNoise(0.1));
        profile.addGlobalNoise("targeted", new AmplitudeDampingNoise(0.2, Q0));
        config.setNoiseProfile(profile);

        SimulationConfig noiseEventConfig = new SimulationConfig();
        noiseEventConfig.setNumQubits(1);
        SimulationResult result = simulate(noiseEventConfig, List.of(
            gate("h", Instant.now(), "H", Q0),
            event("n", Instant.now().plusNanos(1), new DephasingNoise(0.2, Q0))));

        SimulationResult noisy = simulate(config, List.of(
            gate("h", Instant.now(), "H", Q0)));
        assertEquals(1.0, sum(noisy.getFinalState().getProbabilities()), 1e-9);
        assertEquals(1, result.getFinalState().getNumQubits());
    }

    @Test
    void idleNoiseSkipsActiveQubitsAndZeroElapsed() throws SimulationError {
        SimulationConfig config = new SimulationConfig();
        config.setNumQubits(2);
        config.setDeviceNoise(new DeviceNoiseModel(
            new double[]{1000, 1000}, new double[]{1000, 1000},
            new double[]{0, 0}, new double[]{0, 0}, 0.0, 0.0));

        Instant base = Instant.now();
        SimulationResult result = simulate(config, List.of(
            gate("h", base, "H", Q0),
            gate("x", base, "X", Q0)));
        assertEquals(1.0, sum(result.getFinalState().getProbabilities()), 1e-9);
    }

    @Test
    void crosstalkTouchesNeighboursOnBothSides() throws SimulationError {
        SimulationConfig config = new SimulationConfig();
        config.setNumQubits(3);
        config.setBackend(SimulationBackend.DENSITY_MATRIX);
        config.setDeviceNoise(new DeviceNoiseModel(
            new double[]{0, 0, 0}, new double[]{0, 0, 0},
            new double[]{0, 0, 0}, new double[]{0, 0, 0}, 0.3, 0.0));

        SimulationResult result = simulate(config, List.of(
            gate("x", Instant.now(), "X", Q1)));
        assertEquals(1.0, result.getFinalState().getProbability(2), EPSILON);
    }

    @Test
    void crosstalkHandlesBoundaryQubits() throws SimulationError {
        SimulationConfig config = new SimulationConfig();
        config.setNumQubits(2);
        config.setBackend(SimulationBackend.DENSITY_MATRIX);
        config.setDeviceNoise(new DeviceNoiseModel(
            new double[]{0, 0}, new double[]{0, 0},
            new double[]{0, 0}, new double[]{0, 0}, 0.3, 0.0));

        Instant base = Instant.now();
        SimulationResult result = simulate(config, List.of(
            gate("h", base, "H", Q0),
            event("cx", base.plusNanos(1),
                new GateLibrary().createGate("CNOT", Q0, Q1))));
        assertEquals(1.0, sum(result.getFinalState().getProbabilities()), 1e-9);
    }

    @Test
    void measurementOnUnknownRegisterFails() {
        SimulationConfig config = new SimulationConfig();
        config.setNumQubits(1);
        assertThrows(SimulationError.class, () -> simulate(config, List.of(
            event("m", Instant.now(),
                MeasurementOperator.computationalBasis(new Qubit[]{Q0}, 1)
                    .withClassicalTarget("missing", 0)))));
    }

    @Test
    void simulatorExposesAndResetsRegisters() throws SimulationError {
        SimulationConfig config = new SimulationConfig();
        config.setNumQubits(1);
        config.addClassicalRegister("c", 2);
        QuantumSimulator simulator = new QuantumSimulator();
        simulator.initialize(config);
        assertSame(simulator.getContext().getRegister("c"),
            simulator.getContext().getRegisters().get(0));
        simulator.getContext().getRegister("c").set(1, true);
        simulator.reset();
        assertFalse(simulator.getContext().getRegister("c").get(1));
        assertNull(simulator.getContext().getRegister("missing"));
    }

    @Test
    void classicalRegisterIgnoresWritesPastTheEnd() {
        ClassicalRegister register = new ClassicalRegister("c", 2);
        register.write(5, 0b11, 2);
        assertEquals("00", register.toBitString());
        assertEquals(0, register.read());
        assertFalse(register.equals("c"));
    }

    @Test
    void thermalRelaxationGuardsTargetsAndFullDecay() {
        ThermalRelaxationModel model = new ThermalRelaxationModel(1000, 1000, 100,
            new Qubit[0]);
        ThermalRelaxationModel full = (ThermalRelaxationModel) model.withProbability(1.0);
        assertEquals(1.0, full.getAmplitudeDampingProbability(), 1e-12);
        assertThrows(IllegalArgumentException.class,
            () -> new ThermalRelaxationModel(1000, 1000, 10, Q0, Q1, Q2));
    }

    @Test
    void deviceNoiseValidatesAndParsesLooseValues() {
        assertThrows(IllegalArgumentException.class,
            () -> new DeviceNoiseModel(new double[]{1}, new double[]{1, 2},
                new double[]{0}, new double[]{0}, 0, 0));
        assertThrows(IllegalArgumentException.class,
            () -> new DeviceNoiseModel(new double[]{1}, new double[]{1},
                new double[]{0}, new double[]{0}, 0, -1));

        DeviceNoiseModel model = DeviceNoiseModel.fromMap(Map.of(
            "t1_ns", "not a list",
            "t2_ns", List.of(50, "bad"),
            "crosstalk_rate", "bad"));
        assertEquals(2, model.getNumQubits());
        assertEquals(0.0, model.getT1(0));
        assertFalse(model.hasRelaxation());
        assertNull(model.getReadout());
        assertNull(model.crosstalkForIdle());
        assertTrue(DeviceNoiseModel.disabled(0).isEmpty());
    }

    @Test
    void configStoresRegistersDeviceNoiseAndBondDimension() throws Exception {
        SimulationConfig config = new SimulationConfig();
        config.setNumQubits(2);
        config.addClassicalRegister("c", 2);
        config.setDeviceNoise(new DeviceNoiseModel(
            new double[]{1000, 1000}, new double[]{1000, 1000},
            new double[]{0.01, 0.01}, new double[]{0.02, 0.02}, 0.0, 10));
        config.setMaxBondDimension(4);
        config.setCustomParameter("tag", "value");

        assertEquals(Map.of("c", 2), config.getClassicalRegisters());
        assertEquals("value", config.getCustomParameter("tag"));
        assertThrows(UnsupportedOperationException.class,
            () -> config.getCustomParameters().put("x", 1));
        assertThrows(NullPointerException.class,
            () -> config.addClassicalRegister(null, 1));
        assertThrows(IllegalArgumentException.class,
            () -> config.addClassicalRegister("d", 0));
        String path = "target/v2-config-coverage.json";
        config.saveToFile(path);
        SimulationConfig loaded = new SimulationConfig();
        loaded.loadFromFile(path);
        assertEquals(4, loaded.getMaxBondDimension());
        assertEquals(2, loaded.getClassicalRegisters().get("c"));
        assertEquals(1000.0, loaded.getDeviceNoise().getT1(0), EPSILON);

        config.setDeviceNoise(null);
        assertTrue(config.getDeviceNoise().isEmpty());
    }

    @Test
    void configValidatesStabilizerAndTensorNetworkEstimates() {
        SimulationConfig stabilizer = new SimulationConfig();
        stabilizer.setNumQubits(20);
        stabilizer.setBackend(SimulationBackend.STABILIZER);
        assertTrue(stabilizer.validate());

        SimulationConfig tensor = new SimulationConfig();
        tensor.setNumQubits(20);
        tensor.setBackend(SimulationBackend.TENSOR_NETWORK);
        assertTrue(tensor.validate());
        tensor.setMaxBondDimension(0);
        assertFalse(tensor.validate());
    }

    @Test
    void zeroShotMeasurementStillWritesTheCollapsedOutcome() throws SimulationError {
        SimulationConfig config = new SimulationConfig();
        config.setNumQubits(1);
        config.addClassicalRegister("c", 1);
        SimulationResult result = simulate(config, List.of(
            event("m", Instant.now(),
                MeasurementOperator.computationalBasis(new Qubit[]{Q0}, 0)
                    .withClassicalTarget("c", 0))));
        assertEquals(0L, result.getRegisterValues().get("c"));
        assertEquals(1, result.getMeasurements().size());
    }

    @Test
    void gateLibraryCanBeClearedOnTheConfiguration() {
        SimulationConfig config = new SimulationConfig();
        config.setGateLibrary(null);
        assertNull(config.getGateLibrary());
    }

    @Test
    void simulationContextExposesDataAndConfig() throws SimulationError {
        SimulationConfig config = new SimulationConfig();
        config.setNumQubits(1);
        QuantumSimulator simulator = new QuantumSimulator();
        simulator.initialize(config);
        assertSame(config, simulator.getContext().getConfig());
        simulator.getContext().putData("key", 42);
        assertEquals(42, simulator.getContext().getData("key"));
        assertNull(simulator.getContext().getData("missing"));
        assertEquals(0, simulator.getContext().getRegisterValues().size());
    }

    private static double sum(double[] values) {
        double total = 0.0;
        for (double value : values) {
            total += value;
        }
        return total;
    }

    private SimulationResult simulate(SimulationConfig config, List<QuantumEvent> events)
            throws SimulationError {
        QuantumSimulator simulator = new QuantumSimulator();
        simulator.initialize(config);
        for (QuantumEvent event : events) {
            simulator.scheduleEvent(event);
        }
        return simulator.run();
    }

    private QuantumEvent gate(String id, Instant time, String name, Qubit qubit) {
        return event(id, time, new GateLibrary().createGate(name, qubit));
    }

    private QuantumEvent event(String id, Instant time, QuantumOperation operation) {
        return new QuantumEvent.Builder(id)
            .withScheduledTime(time)
            .withType(EventType.GATE_OPERATION)
            .withOperation(operation)
            .build();
    }
}
