package com.mechalikh.qforge;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.mechalikh.qforge.classical.ClassicalController;
import com.mechalikh.qforge.config.SimulationBackend;
import com.mechalikh.qforge.config.SimulationConfig;
import com.mechalikh.qforge.core.QuantumSimulator;
import com.mechalikh.qforge.core.SimulationContext;
import com.mechalikh.qforge.core.SimulationError;
import com.mechalikh.qforge.core.SimulationResult;
import com.mechalikh.qforge.events.QuantumEvent;
import com.mechalikh.qforge.math.Complex;
import com.mechalikh.qforge.math.ComplexMatrix;
import com.mechalikh.qforge.noise.AmplitudeDampingNoise;
import com.mechalikh.qforge.noise.DepolarizingNoise;
import com.mechalikh.qforge.noise.DephasingNoise;
import com.mechalikh.qforge.noise.ErrorManager;
import com.mechalikh.qforge.noise.NoiseModel;
import com.mechalikh.qforge.noise.NoiseProfile;
import com.mechalikh.qforge.operations.SingleQubitGate;
import com.mechalikh.qforge.observer.SimulationObserver;
import com.mechalikh.qforge.operations.QuantumOperation;
import com.mechalikh.qforge.state.DensityMatrix;
import com.mechalikh.qforge.state.QuantumState;
import com.mechalikh.qforge.state.Qubit;
import com.mechalikh.qforge.state.StateVector;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class NoiseCoreExtraCoverageTest {
    private static final double EPS = 1e-9;

    /** A state type that the error manager does not support. */
    private static final class ExoticState extends QuantumState {
        ExoticState() {
            super(1);
        }

        @Override
        public QuantumState applyOperation(QuantumOperation operation) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Complex getAmplitude(int basisState) {
            throw new UnsupportedOperationException();
        }

        @Override
        public double getProbability(int basisState) {
            throw new UnsupportedOperationException();
        }

        @Override
        public double[] getProbabilities() {
            throw new UnsupportedOperationException();
        }

        @Override
        public QuantumState tensorProduct(QuantumState other) {
            throw new UnsupportedOperationException();
        }

        @Override
        public QuantumState normalize() {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean isPure() {
            throw new UnsupportedOperationException();
        }

        @Override
        public double getPurity() {
            throw new UnsupportedOperationException();
        }

        @Override
        public QuantumState copy() {
            throw new UnsupportedOperationException();
        }

        @Override
        public QuantumState collapseToBasisState(int[] targetQubits, int outcome) {
            throw new UnsupportedOperationException();
        }

        @Override
        public QuantumState collapseToEigenvector(int[] targetQubits, Complex[] eigenvector) {
            throw new UnsupportedOperationException();
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
            return "ExoticState";
        }
    }

    /** A noise model that is not one of the three known subclasses. */
    private static final class CustomNoise extends NoiseModel {
        CustomNoise() {
            super("CustomNoise", 0.1, new Qubit[]{new Qubit(0)});
        }

        @Override
        public ComplexMatrix[] getKrausOperators(int numQubits) {
            return new ComplexMatrix[]{ComplexMatrix.identity(2)};
        }

        @Override
        public NoiseModel withProbability(double newProbability) {
            return this;
        }

        @Override
        public NoiseModel withTargetQubits(Qubit[] newTargetQubits) {
            return this;
        }
    }

    /** A noise model whose Kraus operators do not match its single target qubit. */
    private static final class BrokenNoise extends NoiseModel {
        BrokenNoise() {
            super("BrokenNoise", 0.1, new Qubit[0]);
        }

        @Override
        public ComplexMatrix[] getKrausOperators(int numQubits) {
            return new ComplexMatrix[]{ComplexMatrix.zero(4, 4)};
        }

        @Override
        public NoiseModel withProbability(double newProbability) {
            return this;
        }

        @Override
        public NoiseModel withTargetQubits(Qubit[] newTargetQubits) {
            return new BrokenNoise();
        }
    }

    // ------------------------------------------------------- SimulationError

    @Test
    void simulationErrorConstructorsAndFactories() {
        SimulationError plain = new SimulationError("boom");
        assertEquals(SimulationError.ErrorType.UNKNOWN_ERROR, plain.getErrorType());
        assertEquals("Unknown", plain.getComponent());
        assertTrue(plain.getTimestamp() > 0);
        assertTrue(plain.getDetailedMessage().contains("[UNKNOWN_ERROR]"));
        assertTrue(plain.getDetailedMessage().contains("boom"));

        RuntimeException cause = new RuntimeException("root");
        SimulationError withCause = new SimulationError("wrapped", cause);
        assertSame(cause, withCause.getCause());

        SimulationError typed = new SimulationError("typed",
            SimulationError.ErrorType.VALIDATION_ERROR, "Validator");
        assertEquals(SimulationError.ErrorType.VALIDATION_ERROR, typed.getErrorType());
        assertEquals("Validator", typed.getComponent());
        assertNull(typed.getCause());

        SimulationError full = new SimulationError("full",
            SimulationError.ErrorType.RESOURCE_ERROR, "Resources", cause);
        assertSame(cause, full.getCause());

        SimulationError init = SimulationError.initializationError("init", cause);
        assertEquals(SimulationError.ErrorType.INITIALIZATION_ERROR, init.getErrorType());
        assertEquals("Initialization", init.getComponent());

        SimulationError conf = SimulationError.configurationError("conf");
        assertEquals(SimulationError.ErrorType.CONFIGURATION_ERROR, conf.getErrorType());
        assertEquals("Configuration", conf.getComponent());

        SimulationError runtime = SimulationError.runtimeError("run", "Dispatcher");
        assertEquals(SimulationError.ErrorType.RUNTIME_ERROR, runtime.getErrorType());
        assertEquals("Dispatcher", runtime.getComponent());
        assertEquals(7, SimulationError.ErrorType.values().length);
    }

    // ------------------------------------------------------------- NoiseModel

    @Test
    void noiseModelRejectsOutOfRangeProbability() {
        Qubit q0 = new Qubit(0);
        assertThrows(IllegalArgumentException.class, () -> new DephasingNoise(-0.1, q0));
        assertThrows(IllegalArgumentException.class, () -> new DephasingNoise(1.5, q0));
    }

    @Test
    void noiseModelWithoutTargetsCannotApply() {
        NoiseModel global = new DepolarizingNoise(0.1);
        assertEquals(0, global.getTargetQubits().length);
        IllegalStateException failure = assertThrows(IllegalStateException.class,
            () -> global.apply(StateVector.createZeroState(1)));
        assertTrue(failure.getMessage().contains("no target qubits"));
    }

    @Test
    void noiseModelCommonBehaviour() {
        Qubit q0 = new Qubit(0);
        DephasingNoise noise = new DephasingNoise(0.2, q0);
        assertEquals(0.0, noise.getDuration(), 0.0);
        assertEquals(0, noise.getControlQubits().length);
        assertFalse(noise.isUnitary());
        assertTrue(noise.isNoise());
        assertFalse(noise.isMeasurement());
        assertThrows(UnsupportedOperationException.class, noise::inverse);
        assertTrue(noise.toString().contains("DephasingNoise"));

        // Target array is defensively copied.
        noise.getTargetQubits()[0] = new Qubit(5);
        assertEquals(0, noise.getTargetQubits()[0].getIndex());
    }

    @Test
    void noiseModelEqualityUsesClassAndValues() {
        Qubit q0 = new Qubit(0);
        DephasingNoise a = new DephasingNoise(0.2, q0);
        DephasingNoise b = new DephasingNoise(0.2, new Qubit(0, "other"));
        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
        assertTrue(a.equals(a));
        assertFalse(a.equals(null));
        assertFalse(a.equals("string"));
        assertFalse(a.equals(new DepolarizingNoise(0.2, q0)));
        assertFalse(a.equals(new DephasingNoise(0.3, q0)));

        DepolarizingNoise custom = new DepolarizingNoise(0.2, q0);
        assertEquals(custom, new DepolarizingNoise(0.2, q0));
        assertEquals(custom.hashCode(), new DepolarizingNoise(0.2, q0).hashCode());
    }

    @Test
    void independentKrausRejectsTooManyQubits() {
        Qubit[] nine = TestSupport.qubits(9);
        DephasingNoise noise = new DephasingNoise(0.1, nine);
        assertThrows(UnsupportedOperationException.class, () -> noise.getKrausOperators(9));
    }

    // --------------------------------------------------------- DephasingNoise

    @Test
    void dephasingNoiseEdges() {
        Qubit q0 = new Qubit(0);
        DephasingNoise noise = DephasingNoise.createSingleQubit(0.25, q0);
        assertThrows(IllegalArgumentException.class, () -> noise.getKrausOperators(2));

        ComplexMatrix[] zeroStrength = new DephasingNoise(0.0, q0).getKrausOperators(1);
        assertEquals(2, zeroStrength.length);
        assertTrue(maxAbs(zeroStrength[1]) < 1e-12);

        NoiseModel copy = noise.withProbability(0.5);
        assertEquals(0.5, copy.getProbability(), 0.0);
        assertThrows(IllegalArgumentException.class, () -> copy.withProbability(-1.0));

        NoiseModel retargeted = noise.withTargetQubits(TestSupport.qubits(2));
        assertEquals(2, retargeted.getTargetQubits().length);
    }

    // ------------------------------------------------- AmplitudeDampingNoise

    @Test
    void amplitudeDampingEdges() {
        Qubit q0 = new Qubit(0);
        assertThrows(IllegalArgumentException.class,
            () -> new AmplitudeDampingNoise(-0.5, q0));

        AmplitudeDampingNoise gammaZero = AmplitudeDampingNoise.create(0.0, q0);
        assertEquals(0.0, gammaZero.getGamma(), 0.0);
        assertEquals(q0, gammaZero.getTargetQubits()[0]);

        assertThrows(IllegalArgumentException.class, () -> gammaZero.getKrausOperators(3));

        ComplexMatrix[] ops = new AmplitudeDampingNoise(1.0, q0).getKrausOperators(1);
        assertEquals(2, ops.length);
        assertEquals(0.0, ops[1].get(1, 1).abs(), EPS);

        NoiseModel copy = gammaZero.withProbability(0.3);
        assertEquals(0.3, copy.getProbability(), 0.0);
        NoiseModel retargeted = gammaZero.withTargetQubits(TestSupport.qubits(2));
        assertEquals(2, retargeted.getTargetQubits().length);
    }

    // ----------------------------------------------------- DepolarizingNoise

    @Test
    void depolarizingNoiseEdges() {
        Qubit[] five = TestSupport.qubits(5);
        DepolarizingNoise noise = DepolarizingNoise.createMultiQubit(0.1, five);
        UnsupportedOperationException failure = assertThrows(UnsupportedOperationException.class,
            () -> noise.getKrausOperators(5));
        assertTrue(failure.getMessage().contains("Kraus operators"));

        assertThrows(IllegalArgumentException.class, () -> noise.getKrausOperators(2));

        Qubit q0 = new Qubit(0);
        DepolarizingNoise single = DepolarizingNoise.createSingleQubit(0.3, q0);
        assertEquals(4, single.getKrausOperators(1).length);
        NoiseModel copy = single.withProbability(0.4);
        assertEquals(0.4, copy.getProbability(), 0.0);
        NoiseModel retargeted = single.withTargetQubits(new Qubit[]{new Qubit(1)});
        assertEquals(1, retargeted.getTargetQubits()[0].getIndex());
    }

    // ------------------------------------------------------------ ErrorManager

    @Test
    void errorManagerFidelityAcrossBackends() {
        ErrorManager manager = new ErrorManager();
        StateVector sv = StateVector.createZeroState(1);
        DensityMatrix dm = DensityMatrix.createZeroState(1);
        assertEquals(1.0, manager.calculateFidelity(sv, sv), EPS);
        assertEquals(1.0, manager.calculateFidelity(dm, sv), EPS);
        assertEquals(1.0, manager.calculateFidelity(sv, dm), EPS);
    }

    @Test
    void errorManagerRejectsUnsupportedStatesAndSizes() throws Exception {
        ErrorManager manager = new ErrorManager();
        Method toDensityMatrix = ErrorManager.class.getDeclaredMethod(
            "toDensityMatrix", QuantumState.class);
        toDensityMatrix.setAccessible(true);
        assertThrows(java.lang.reflect.InvocationTargetException.class,
            () -> toDensityMatrix.invoke(null, new ExoticState()));

        Method distance = ErrorManager.class.getDeclaredMethod(
            "calculateStateDistance", QuantumState.class, QuantumState.class);
        distance.setAccessible(true);
        assertThrows(java.lang.reflect.InvocationTargetException.class,
            () -> distance.invoke(manager, StateVector.createZeroState(1),
                StateVector.createZeroState(2)));
    }

    // ------------------------------------------------------------- NoiseProfile

    @Test
    void noiseProfileTypeLabelsAndUnknownTypeFallback() {
        Qubit q0 = new Qubit(0);
        NoiseProfile profile = new NoiseProfile("labels");
        profile.addNoise(q0, DephasingNoise.createSingleQubit(0.1, q0));
        profile.addNoise(q0, AmplitudeDampingNoise.create(0.1, q0));
        profile.addNoise(q0, DepolarizingNoise.createSingleQubit(0.1, q0));
        profile.addNoise(q0, new CustomNoise());

        Map<String, Object> map = profile.toMap();
        assertNotNull(map);
        String text = String.valueOf(map);
        assertTrue(text.contains("dephasing"));
        assertTrue(text.contains("amplitude_damping"));
        assertTrue(text.contains("depolarizing"));
        assertTrue(text.contains("CustomNoise"));
    }

    private static double maxAbs(ComplexMatrix m) {
        double max = 0.0;
        for (int i = 0; i < m.rows(); i++) {
            for (int j = 0; j < m.cols(); j++) {
                max = Math.max(max, m.get(i, j).abs());
            }
        }
        return max;
    }

    @Test
    void noiseProfileLoadFromMapHandlesBadEntries() {
        NoiseProfile profile = new NoiseProfile("bad");

        Map<String, Object> qubitNoise = new HashMap<>();
        qubitNoise.put("xx", Map.of("dephasing", 0.1));
        Map<String, Object> outer = new HashMap<>();
        outer.put("qubit_noise", qubitNoise);
        profile.loadFromMap(outer);
        assertTrue(profile.isEmpty());

        Map<String, Object> globalNoise = new HashMap<>();
        globalNoise.put("not_a_type", 0.1);
        Map<String, Object> outer2 = new HashMap<>();
        outer2.put("global_noise", globalNoise);
        profile.loadFromMap(outer2);
        assertTrue(profile.isEmpty());

        Map<String, Object> qubitNoise2 = new HashMap<>();
        qubitNoise2.put("0", Map.of("dephasing", "not_a_number"));
        Map<String, Object> outer3 = new HashMap<>();
        outer3.put("qubit_noise", qubitNoise2);
        profile.loadFromMap(outer3);

        Map<String, Object> outer4 = new HashMap<>();
        outer4.put("qubit_noise", Map.of("0", "not a map"));
        profile.loadFromMap(outer4);
        assertTrue(profile.isEmpty());
    }

    // ------------------------------------------------------------ Core runner

    @Test
    void runGuardRejectsMissingInitialization() {
        QuantumSimulator simulator = new QuantumSimulator();
        assertThrows(SimulationError.class, simulator::run);
        assertThrows(SimulationError.class, simulator::step);
    }

    @Test
    void stepGuardRejectsEmptyQueue() throws Exception {
        SimulationConfig config = new SimulationConfig();
        config.setNumQubits(1);
        config.setBackend(SimulationBackend.STATE_VECTOR);
        QuantumSimulator simulator = new QuantumSimulator();
        simulator.initialize(config);
        assertThrows(SimulationError.class, simulator::step);
    }

    @Test
    void initializeWhileRunningIsRejected() throws Exception {
        SimulationConfig config = new SimulationConfig();
        config.setNumQubits(1);
        config.setBackend(SimulationBackend.STATE_VECTOR);
        QuantumSimulator simulator = new QuantumSimulator();
        simulator.initialize(config);

        Field runningField = QuantumSimulator.class.getDeclaredField("isRunning");
        runningField.setAccessible(true);
        java.util.concurrent.atomic.AtomicBoolean isRunning =
            (java.util.concurrent.atomic.AtomicBoolean) runningField.get(simulator);
        isRunning.set(true);
        try {
            SimulationError failure = assertThrows(SimulationError.class,
                () -> simulator.initialize(config));
            assertTrue(failure.getMessage().contains("already running"));
        } finally {
            isRunning.set(false);
        }
        simulator.initialize(config);
    }

    @Test
    void classicalControllerSchedulerReportsUnschedulableEvents() throws Exception {
        SimulationConfig config = new SimulationConfig();
        config.setNumQubits(1);
        config.setBackend(SimulationBackend.STATE_VECTOR);
        config.setClassicalControlEnabled(true);
        QuantumSimulator simulator = new QuantumSimulator();
        simulator.initialize(config);

        Field controllerField = QuantumSimulator.class.getDeclaredField("classicalController");
        controllerField.setAccessible(true);
        ClassicalController controller = (ClassicalController) controllerField.get(simulator);
        assertNotNull(controller);

        Field schedulerField = ClassicalController.class.getDeclaredField("eventScheduler");
        schedulerField.setAccessible(true);
        @SuppressWarnings("unchecked")
        java.util.function.Consumer<QuantumEvent> scheduler =
            (java.util.function.Consumer<QuantumEvent>) schedulerField.get(controller);
        assertNotNull(scheduler);

        // With no context the scheduler installed by the simulator cannot schedule, and the
        // SimulationError it receives is converted into an IllegalStateException.
        Field contextField = QuantumSimulator.class.getDeclaredField("context");
        contextField.setAccessible(true);
        contextField.set(simulator, null);
        QuantumEvent event = new QuantumEvent.Builder("probe")
            .withOperation(SingleQubitGate.createPauliX(new Qubit(0)))
            .build();
        IllegalStateException failure = assertThrows(IllegalStateException.class,
            () -> scheduler.accept(event));
        assertTrue(failure.getMessage().contains("failed to schedule"));
    }

    @Test
    void maxStepsStopsTheRunLoopEarly() throws Exception {
        SimulationConfig config = new SimulationConfig();
        config.setNumQubits(1);
        config.setBackend(SimulationBackend.STATE_VECTOR);
        config.setMaxSteps(2);
        QuantumSimulator simulator = new QuantumSimulator();
        simulator.initialize(config);

        Instant time = Instant.now().minusSeconds(5);
        for (int i = 0; i < 5; i++) {
            simulator.scheduleEvent(new QuantumEvent.Builder("h" + i)
                .withScheduledTime(time)
                .withOperation(SingleQubitGate.createHadamard(new Qubit(0)))
                .withTargetQubits(new Qubit(0))
                .build());
            time = time.plusMillis(1);
        }
        SimulationResult result = simulator.run();
        assertNotNull(result);
        assertEquals(2, simulator.getContext().getStepsExecuted());
    }

    @Test
    void contextRejectsUnknownOperationTypes() throws Exception {
        SimulationConfig config = new SimulationConfig();
        config.setNumQubits(1);
        config.setBackend(SimulationBackend.STATE_VECTOR);
        SimulationContext context = new SimulationContext(config);
        context.setStrategy(new com.mechalikh.qforge.strategy.StateVectorStrategy());
        context.initializeState();

        QuantumOperation foreign = new QuantumOperation() {
            @Override
            public QuantumState apply(QuantumState state) {
                return state;
            }

            @Override
            public String getName() {
                return "foreign";
            }

            @Override
            public double getDuration() {
                return 0.0;
            }

            @Override
            public Qubit[] getTargetQubits() {
                return new Qubit[0];
            }

            @Override
            public Qubit[] getControlQubits() {
                return new Qubit[0];
            }

            @Override
            public boolean isUnitary() {
                return false;
            }

            @Override
            public boolean isNoise() {
                return false;
            }

            @Override
            public boolean isMeasurement() {
                return false;
            }

            @Override
            public QuantumOperation inverse() {
                throw new UnsupportedOperationException();
            }
        };

        SimulationError failure = assertThrows(SimulationError.class,
            () -> context.applyOperation(foreign));
        assertTrue(failure.getMessage().contains("Unsupported operation type"));
    }

    @Test
    void configuredNoiseWithoutTargetsFailsCleanly() throws Exception {
        SimulationConfig config = new SimulationConfig();
        config.setNumQubits(1);
        config.setBackend(SimulationBackend.STATE_VECTOR);
        NoiseProfile profile = new NoiseProfile("broken");
        profile.addGlobalNoise("broken", new BrokenNoise());
        config.setNoiseProfile(profile);

        SimulationContext context = new SimulationContext(config);
        context.setStrategy(new com.mechalikh.qforge.strategy.StateVectorStrategy());
        context.initializeState();

        SimulationError failure = assertThrows(SimulationError.class, () -> context.applyOperation(
            SingleQubitGate.createHadamard(new Qubit(0))));
        assertTrue(failure.getMessage().contains("Failed to apply configured noise"));
    }

    @Test
    void defaultObserverCallbacksAreNoOps() throws Exception {
        SimulationConfig config = new SimulationConfig();
        config.setNumQubits(1);
        config.setBackend(SimulationBackend.DENSITY_MATRIX);
        QuantumSimulator simulator = new QuantumSimulator();
        simulator.addObserver(new SimulationObserver() { });
        simulator.initialize(config);
        simulator.scheduleEvent(new QuantumEvent.Builder("h")
            .withScheduledTime(Instant.now().minusSeconds(1))
            .withOperation(SingleQubitGate.createHadamard(new Qubit(0)))
            .withTargetQubits(new Qubit(0))
            .build());
        SimulationResult result = simulator.run();
        assertNotNull(result.getFinalState());
    }
}
