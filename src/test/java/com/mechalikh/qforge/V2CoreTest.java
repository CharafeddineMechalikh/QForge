package com.mechalikh.qforge;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.mechalikh.qforge.classical.ClassicalRegister;
import com.mechalikh.qforge.classical.ShotRunner;
import com.mechalikh.qforge.config.SimulationBackend;
import com.mechalikh.qforge.config.SimulationConfig;
import com.mechalikh.qforge.core.QuantumSimulator;
import com.mechalikh.qforge.core.SimulationError;
import com.mechalikh.qforge.core.SimulationResult;
import com.mechalikh.qforge.events.EventType;
import com.mechalikh.qforge.events.QuantumEvent;
import com.mechalikh.qforge.math.ComplexMatrix;
import com.mechalikh.qforge.measurement.MeasurementOperator;
import com.mechalikh.qforge.noise.DeviceNoiseModel;
import com.mechalikh.qforge.noise.ReadoutErrorModel;
import com.mechalikh.qforge.noise.ThermalRelaxationModel;
import com.mechalikh.qforge.operations.ConditionalOperation;
import com.mechalikh.qforge.operations.GateLibrary;
import com.mechalikh.qforge.state.Qubit;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Tests for the classical register, conditional execution, device noise and shot runner
 * features.
 */
class V2CoreTest {
    private static final double EPSILON = 1e-9;

    @Test
    void classicalRegisterWritesAndReads() {
        ClassicalRegister register = new ClassicalRegister("c", 4);
        assertEquals("c", register.getName());
        assertEquals(4, register.size());
        assertEquals(0L, register.read());
        assertEquals("0000", register.toBitString());

        register.write(0, 0b10, 2);
        assertEquals("0001", register.toBitString());
        assertEquals(1L, register.read());
        assertTrue(register.matches(1, 3));
        assertFalse(register.matches(2, 3));

        register.write(2, 0b11, 2);
        assertEquals("1101", register.toBitString());
        register.set(1, true);
        assertTrue(register.get(1));
        assertEquals("1111", register.toBitString());
        assertEquals(15L, register.read());

        ClassicalRegister copy = register.copy();
        assertEquals(register, copy);
        assertEquals(register.hashCode(), copy.hashCode());
        copy.reset();
        assertEquals("0000", copy.toBitString());
        assertNotEquals(register, copy);
        assertEquals(0L, copy.read());
        assertTrue(register.toString().contains("c=1111"));

        assertThrows(IllegalArgumentException.class, () -> new ClassicalRegister("x", 0));
        assertThrows(NullPointerException.class, () -> new ClassicalRegister(null, 2));
        assertThrows(IllegalArgumentException.class, () -> register.write(-1, 1, 1));
        assertThrows(IllegalArgumentException.class, () -> register.write(0, 1, -1));
        assertThrows(IndexOutOfBoundsException.class, () -> register.set(4, true));
    }

    @Test
    void conditionalOperationTestsMaskedValue() {
        GateLibrary library = new GateLibrary();
        Qubit qubit = new Qubit(0, "q0");
        ConditionalOperation conditional = new ConditionalOperation(
            library.createGate("X", qubit), "c", 2, 0b011);
        assertEquals("c", conditional.getRegisterName());
        assertEquals(2, conditional.getExpectedValue());
        assertEquals(3, conditional.getMask());
        assertTrue(conditional.getName().contains("if(c==2)"));
        assertTrue(conditional.test(0b110));
        assertFalse(conditional.test(0b101));
        assertTrue(conditional.isUnitary());
        assertFalse(conditional.isNoise());
        assertFalse(conditional.isMeasurement());
        assertEquals(1, conditional.getTargetQubits().length);
        assertTrue(conditional.getDuration() > 0);

        ConditionalOperation inverse = (ConditionalOperation) conditional.inverse();
        assertTrue(inverse.test(0b010));
        assertEquals(library.createGate("X", qubit).getName(),
            conditional.getGuardedOperation().getName());

        assertThrows(NullPointerException.class,
            () -> new ConditionalOperation(null, "c", 1));
        assertThrows(NullPointerException.class,
            () -> new ConditionalOperation(library.createGate("X", qubit), null, 1));
    }

    @Test
    void readoutErrorTransformsDistribution() {
        ReadoutErrorModel readout = new ReadoutErrorModel(
            new double[]{1.0, 0.0}, new double[]{0.0, 1.0});
        assertFalse(readout.isEmpty());
        assertArrayEquals(new double[]{0.0, 1.0}, readout.apply(new double[]{1.0, 0.0}, 1), EPSILON);
        assertArrayEquals(new double[]{0.0, 1.0}, readout.apply(new double[]{0.0, 1.0}, 1), EPSILON);
        ReadoutErrorModel flip = new ReadoutErrorModel(
            new double[]{1.0}, new double[]{1.0});
        assertArrayEquals(new double[]{1.0, 0.0}, flip.apply(new double[]{0.0, 1.0}, 1), EPSILON);
        assertEquals(1.0, readout.getP01(0));
        assertEquals(0.0, readout.getP10(0));
        assertEquals(0.0, readout.getP01(5));
        assertTrue(readout.toString().contains("maxP01=1.0"));

        ReadoutErrorModel perfect = new ReadoutErrorModel(new double[]{0.0}, new double[]{0.0});
        assertTrue(perfect.isEmpty());

        assertThrows(IllegalArgumentException.class,
            () -> new ReadoutErrorModel(new double[]{0.0}, new double[]{0.0, 0.0}));
        assertThrows(IllegalArgumentException.class,
            () -> new ReadoutErrorModel(new double[]{1.5}, new double[]{0.0}));
        assertThrows(IllegalArgumentException.class,
            () -> readout.apply(new double[]{1.0}, 2));
    }

    @Test
    void thermalRelaxationIsTracePreservingAndValidated() {
        ThermalRelaxationModel model = new ThermalRelaxationModel(1000, 1000, 1000,
            new Qubit(0, "q0"));
        assertEquals(1000, model.getT1());
        assertEquals(1000, model.getT2());
        assertEquals(1000, model.getDuration());
        assertEquals(1.0 - Math.exp(-1.0), model.getAmplitudeDampingProbability(), EPSILON);
        assertTrue(model.getPhaseDampingProbability() > 0.0);

        ComplexMatrix completeness = new ComplexMatrix(2, 2);
        for (ComplexMatrix operator : model.getKrausOperators(1)) {
            completeness = completeness.add(operator.adjoint().multiply(operator));
        }
        for (int row = 0; row < 2; row++) {
            for (int column = 0; column < 2; column++) {
                assertEquals(row == column ? 1.0 : 0.0, completeness.get(row, column).abs(), 1e-9);
            }
        }

        ThermalRelaxationModel rescaled = (ThermalRelaxationModel) model.withProbability(0.5);
        assertEquals(0.5, rescaled.getAmplitudeDampingProbability(), 1e-12);
        assertEquals(1, ((ThermalRelaxationModel) model.withTargetQubits(
            new Qubit[]{new Qubit(1, "q1")})).getT1() > 0 ? 1 : 0);
        assertTrue(model.toString().contains("pAmp"));

        assertThrows(IllegalArgumentException.class,
            () -> new ThermalRelaxationModel(100, 300, 10, new Qubit(0, "q0")));
        assertThrows(IllegalArgumentException.class,
            () -> new ThermalRelaxationModel(100, 100, -1, new Qubit(0, "q0")));
        assertThrows(IllegalArgumentException.class,
            () -> new ThermalRelaxationModel(0, 0, 10, new Qubit(0, "q0")));
        assertThrows(IllegalArgumentException.class,
            () -> model.getKrausOperators(2));
        assertThrows(IllegalArgumentException.class, () -> model.withProbability(2.0));
    }

    @Test
    void deviceNoiseModelRoundTrips() {
        DeviceNoiseModel model = DeviceNoiseModel.fromMap(Map.of(
            "t1_ns", List.of(50000, 40000),
            "t2_ns", List.of(30000, 20000),
            "readout_p01", List.of(0.02, 0.03),
            "readout_p10", List.of(0.04),
            "crosstalk_rate", 0.001,
            "gate_time_ns", 50));
        assertEquals(2, model.getNumQubits());
        assertEquals(50000, model.getT1(0));
        assertEquals(30000, model.getT2(0));
        assertEquals(0.04, model.getReadout().getP10(0));
        assertTrue(model.hasRelaxation());
        assertTrue(model.hasReadout());
        assertEquals(0.001, model.getCrosstalkRate());
        assertEquals(50, model.getGateTimeNanos());
        assertTrue(model.relaxationFor(0, 100) instanceof ThermalRelaxationModel);
        assertNull(model.relaxationFor(0, 0));
        assertNull(model.relaxationFor(9, 100));
        assertTrue(model.crosstalkForIdle() != null);
        assertTrue(model.toString().contains("crosstalk=0.001"));

        DeviceNoiseModel restored = DeviceNoiseModel.fromMap(model.toMap());
        assertEquals(model.getT1(1), restored.getT1(1));
        assertEquals(0.0, restored.getT2(5));
        assertEquals(0.0, restored.getT1(9));

        assertTrue(DeviceNoiseModel.disabled(3).isEmpty());
        assertTrue(DeviceNoiseModel.fromMap(null).isEmpty());
        assertThrows(IllegalArgumentException.class,
            () -> new DeviceNoiseModel(new double[]{100}, new double[]{300},
                new double[]{0}, new double[]{0}, 0, 0));
        assertThrows(IllegalArgumentException.class,
            () -> new DeviceNoiseModel(new double[]{100}, new double[]{100},
                new double[]{1.2}, new double[]{0}, 0, 0));
        assertThrows(IllegalArgumentException.class,
            () -> new DeviceNoiseModel(new double[]{100}, new double[]{100},
                new double[]{0}, new double[]{0}, 2, 0));
        assertThrows(IllegalArgumentException.class, () -> DeviceNoiseModel.disabled(-1));
    }

    @Test
    void conditionalGateRunsOnlyWhenRegisterMatches() throws Exception {
        SimulationConfig config = new SimulationConfig();
        config.setNumQubits(1);
        config.addClassicalRegister("c", 1);

        SimulationResult applied = simulate(config, List.of(
            measurement("m0", 0, 0),
            conditional("cx", 0, 0, 0)));
        assertEquals(1.0, applied.getFinalState().getProbability(1), EPSILON);
        assertEquals(Map.of("c", 0L), applied.getRegisterValues());

        SimulationResult skipped = simulate(config, List.of(
            measurement("m0", 0, 0),
            conditional("skip", 1, 0, 1)));
        assertEquals(0.0, skipped.getFinalState().getProbability(1), EPSILON);
    }

    @Test
    void conditionalOnUnknownRegisterFails() {
        SimulationConfig config = new SimulationConfig();
        config.setNumQubits(1);
        config.addClassicalRegister("c", 1);
        SimulationError error = assertThrows(SimulationError.class,
            () -> simulate(config, List.of(conditional("bad", 0, "missing", 0))));
        assertTrue(error.getMessage().contains("missing"), error.getMessage());
    }

    @Test
    void measurementWritesClassicalRegisterAndReadoutFlips() throws Exception {
        SimulationConfig config = new SimulationConfig();
        config.setNumQubits(1);
        config.addClassicalRegister("c", 1);
        config.setDeviceNoise(new DeviceNoiseModel(new double[]{0}, new double[]{0},
            new double[]{1.0}, new double[]{0.0}, 0.0, 0.0));

        SimulationResult result = simulate(config, List.of(measurement("m", 0, 0)));
        assertEquals(1L, result.getRegisterValues().get("c"));
        assertEquals(1, result.getMeasurements().get(0).getMostFrequentOutcome());
        assertEquals(1.0, result.getFinalState().getProbability(0), EPSILON);
    }

    @Test
    void idleRelaxationDecaysAnExcitedQubit() throws Exception {
        SimulationConfig config = new SimulationConfig();
        config.setNumQubits(2);
        config.setBackend(SimulationBackend.DENSITY_MATRIX);
        config.setDeviceNoise(new DeviceNoiseModel(
            new double[]{1000, 1000}, new double[]{1000, 1000},
            new double[]{0, 0}, new double[]{0, 0}, 0.0, 0.0));

        Instant base = Instant.now();
        SimulationResult result = simulateAt(config, List.of(
            gate("x", base, "X", 0),
            gate("idle", base.plusNanos(1000), "X", 1)));
        double excited = result.getFinalState().getProbability(2)
            + result.getFinalState().getProbability(3);
        assertEquals(Math.exp(-1.0), excited, 1e-6);
    }

    @Test
    void crosstalkDampsNeighbourCoherence() throws Exception {
        SimulationConfig config = new SimulationConfig();
        config.setNumQubits(2);
        config.setBackend(SimulationBackend.DENSITY_MATRIX);
        config.setDeviceNoise(new DeviceNoiseModel(
            new double[]{0, 0}, new double[]{0, 0},
            new double[]{0, 0}, new double[]{0, 0}, 0.5, 0.0));

        Instant base = Instant.now();
        SimulationResult result = simulateAt(config, List.of(
            gate("h", base, "H", 1),
            gate("x", base.plusNanos(1), "X", 0)));
        com.mechalikh.qforge.state.DensityMatrix matrix =
            (com.mechalikh.qforge.state.DensityMatrix) result.getFinalState();
        ComplexMatrix rho = matrix.getMatrix();
        assertEquals(0.25, rho.get(2, 3).abs(), 1e-9);
    }

    @Test
    void shotRunnerAggregatesRegisterValues() throws Exception {
        SimulationConfig config = new SimulationConfig();
        config.setNumQubits(1);
        config.addClassicalRegister("c", 1);
        GateLibrary library = new GateLibrary();
        Qubit qubit = new Qubit(0, "q0");
        ShotRunner.ShotStatistics statistics = ShotRunner.run(config, () -> List.of(
            event("h", Instant.now(), library.createGate("H", qubit)),
            event("m", Instant.now().plusNanos(1),
                MeasurementOperator.computationalBasis(new Qubit[]{qubit}, 1)
                    .withClassicalTarget("c", 0))), 400);
        assertEquals(400, statistics.getShots());
        assertEquals(400, statistics.getCounts("c").values().stream().mapToInt(i -> i).sum());
        double ones = statistics.frequency("c", 1);
        assertTrue(ones > 0.4 && ones < 0.6, "frequency " + ones);
        assertEquals(0.0, statistics.frequency("missing", 1));
        assertTrue(statistics.toString().contains("shots=400"));
        assertThrows(IllegalArgumentException.class,
            () -> ShotRunner.run(config, List::of, 0));
    }

    private SimulationResult simulate(SimulationConfig config, List<QuantumEvent> events)
            throws SimulationError {
        return simulateAt(config, events);
    }

    private SimulationResult simulateAt(SimulationConfig config, List<QuantumEvent> events)
            throws SimulationError {
        QuantumSimulator simulator = new QuantumSimulator();
        simulator.initialize(config);
        for (QuantumEvent event : events) {
            simulator.scheduleEvent(event);
        }
        return simulator.run();
    }

    private QuantumEvent measurement(String id, int qubit, int offset) {
        Qubit target = new Qubit(qubit, "q" + qubit);
        return event(id, Instant.now(),
            MeasurementOperator.computationalBasis(new Qubit[]{target}, 1)
                .withClassicalTarget("c", offset));
    }

    private QuantumEvent conditional(String id, long expected, int offset, int qubit) {
        return conditional(id, expected, "c", qubit);
    }

    private QuantumEvent conditional(String id, long expected, String register, int qubit) {
        Qubit target = new Qubit(qubit, "q" + qubit);
        ConditionalOperation operation = new ConditionalOperation(
            new GateLibrary().createGate("X", target), register, expected, 1L);
        return event(id, Instant.now().plusNanos(1), operation);
    }

    private QuantumEvent gate(String id, Instant time, String gate, int qubit) {
        Qubit target = new Qubit(qubit, "q" + qubit);
        return event(id, time, new GateLibrary().createGate(gate, target));
    }

    private QuantumEvent event(String id, Instant time, com.mechalikh.qforge.operations
            .QuantumOperation operation) {
        return new QuantumEvent.Builder(id)
            .withScheduledTime(time)
            .withType(EventType.GATE_OPERATION)
            .withOperation(operation)
            .build();
    }
}
