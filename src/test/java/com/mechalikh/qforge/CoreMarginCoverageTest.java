package com.mechalikh.qforge;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.mechalikh.qforge.classical.ParameterSpace;
import com.mechalikh.qforge.config.SimulationBackend;
import com.mechalikh.qforge.config.SimulationConfig;
import com.mechalikh.qforge.core.QuantumSimulator;
import com.mechalikh.qforge.core.SimulationContext;
import com.mechalikh.qforge.core.SimulationError;
import com.mechalikh.qforge.events.EventDispatcher;
import com.mechalikh.qforge.events.QuantumEvent;
import com.mechalikh.qforge.math.Complex;
import com.mechalikh.qforge.math.ComplexMatrix;
import com.mechalikh.qforge.measurement.MeasurementOperator;
import com.mechalikh.qforge.measurement.Observable;
import com.mechalikh.qforge.operations.QuantumOperation;
import com.mechalikh.qforge.operations.SingleQubitGate;
import com.mechalikh.qforge.state.DensityMatrix;
import com.mechalikh.qforge.state.QuantumState;
import com.mechalikh.qforge.state.Qubit;
import com.mechalikh.qforge.state.StateVector;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class CoreMarginCoverageTest {
    private static final double EPS = 1e-9;

    private static QuantumOperation foreignOperation() {
        return new QuantumOperation() {
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
    }

    private static SimulationConfig singleQubitConfig(boolean classicalControl) {
        SimulationConfig config = new SimulationConfig();
        config.setNumQubits(1);
        config.setBackend(SimulationBackend.STATE_VECTOR);
        config.setClassicalControlEnabled(classicalControl);
        return config;
    }

    private static QuantumEvent eventAtPast(String id, QuantumOperation operation) {
        return new QuantumEvent.Builder(id)
            .withScheduledTime(Instant.now().minusSeconds(1))
            .withOperation(operation)
            .withTargetQubits(new Qubit(0))
            .build();
    }

    // ------------------------------------------------- failure paths of the run

    @Test
    void runReportsSimulationErrorWhenAnEventFails() throws Exception {
        QuantumSimulator simulator = new QuantumSimulator();
        simulator.initialize(singleQubitConfig(true));
        simulator.scheduleEvent(eventAtPast("bad", foreignOperation()));

        SimulationError failure = assertThrows(SimulationError.class, simulator::run);
        assertTrue(failure.getMessage().contains("Simulation failed"));
        assertNotNull(failure.getCause());
    }

    @Test
    void stepReportsSimulationErrorWhenAnEventFails() throws Exception {
        QuantumSimulator simulator = new QuantumSimulator();
        simulator.initialize(singleQubitConfig(true));
        simulator.scheduleEvent(eventAtPast("bad", foreignOperation()));

        SimulationError failure = assertThrows(SimulationError.class, simulator::step);
        assertTrue(failure.getMessage().contains("Step execution failed"));
    }

    @Test
    void runWithClassicalControlProcessesMeasurementEvents() throws Exception {
        QuantumSimulator simulator = new QuantumSimulator();
        simulator.initialize(singleQubitConfig(true));
        simulator.scheduleEvent(eventAtPast("h", SingleQubitGate.createHadamard(new Qubit(0))));
        simulator.scheduleEvent(new QuantumEvent.Builder("m")
            .withScheduledTime(Instant.now().minusSeconds(1))
            .withOperation(new MeasurementOperator(Observable.pauliZ(new Qubit(0)), 4))
            .withTargetQubits(new Qubit(0))
            .build());

        var result = simulator.run();
        assertEquals(1, result.getMeasurements().size());
    }

    @Test
    void stepWithClassicalControlProcessesMeasurementEvents() throws Exception {
        QuantumSimulator simulator = new QuantumSimulator();
        simulator.initialize(singleQubitConfig(true));
        simulator.scheduleEvent(eventAtPast("m", new MeasurementOperator(
            Observable.pauliZ(new Qubit(0)), 2)));

        simulator.step();
        assertEquals(1, simulator.getContext().getStepsExecuted());
        assertEquals(1, simulator.getContext().getMeasurements().size());
    }

    // ------------------------------------------------------- dispatcher default

    @Test
    void dispatcherNotifiesAllObserverCallbacks() throws Exception {
        EventDispatcher dispatcher = new EventDispatcher();
        java.util.Set<String> seen = new java.util.concurrent.ConcurrentSkipListSet<>();
        dispatcher.registerObserver(new EventDispatcher.EventObserver() {
            @Override
            public void onDispatcherStarted(EventDispatcher d) {
                seen.add("started");
            }

            @Override
            public void onDispatcherCompleted(EventDispatcher d) {
                seen.add("completed");
            }

            @Override
            public void onDispatcherPaused(EventDispatcher d) {
                seen.add("paused");
            }

            @Override
            public void onDispatcherResumed(EventDispatcher d) {
                seen.add("resumed");
            }

            @Override
            public void onDispatcherStopped(EventDispatcher d) {
                seen.add("stopped");
            }

            @Override
            public void onQueueCleared(EventDispatcher d) {
                seen.add("cleared");
            }

            @Override
            public void onQueueEmpty(EventDispatcher d) {
                seen.add("empty");
            }

            @Override
            public void onEventScheduled(EventDispatcher d, QuantumEvent event) {
                seen.add("scheduled");
            }

            @Override
            public void onBeforeEventExecution(EventDispatcher d, QuantumEvent event) {
                seen.add("before");
            }

            @Override
            public void onAfterEventExecution(EventDispatcher d, QuantumEvent event,
                                              java.time.Duration duration,
                                              QuantumEvent.ExecutionResult result) {
                seen.add("after");
            }

            @Override
            public void onEventExecutionFailed(EventDispatcher d, QuantumEvent event, Exception error) {
                seen.add("failed");
            }
        });

        SimulationContext context = new SimulationContext(singleQubitConfig(false));
        context.setStrategy(new com.mechalikh.qforge.strategy.StateVectorStrategy());
        context.initializeState();

        dispatcher.scheduleEvent(eventAtPast("h", SingleQubitGate.createHadamard(new Qubit(0))));
        dispatcher.pause();
        dispatcher.resume();
        dispatcher.dispatchAll(context);

        dispatcher.scheduleEvent(eventAtPast("bad", foreignOperation()));
        try {
            dispatcher.dispatchAll(context);
        } catch (Exception expected) {
            // The failing event is rethrown after the observers are notified.
        }

        dispatcher.stop();
        dispatcher.clear();
        assertNull(dispatcher.dispatchNextEvent(context));
        assertTrue(dispatcher.isQueueEmpty());

        for (String expected : List.of("started", "completed", "paused", "resumed", "stopped",
            "cleared", "empty", "scheduled", "before", "after", "failed")) {
            assertTrue(seen.contains(expected), "missing callback " + expected);
        }
    }

    // ------------------------------------------------------- DensityMatrix text

    @Test
    void densityMatrixToStringListsCoherencesAndTruncation() {
        StateVector bell = StateVector.fromComputationalBasis(0, 4)
            .applySingleQubitGate(ComplexMatrix.hadamard(), 0)
            .applyGate(com.mechalikh.qforge.operations.MultiQubitGate.createCNOT(
                new Qubit(0), new Qubit(1)));
        DensityMatrix rho = bell.toDensityMatrix();
        String text = rho.toString();
        assertTrue(text.contains("Significant coherences"));
        assertTrue(text.contains("truncated"));
    }

    // ----------------------------------------------------------------- Observable

    @Test
    void observableArrayFactoriesAndEigenvectors() {
        Qubit[] one = TestSupport.qubits(1);
        Qubit[] three = TestSupport.qubits(3);

        assertEquals("X_q0", Observable.pauliX(one).getName());
        assertEquals("Y_q0", Observable.pauliY(one).getName());
        assertEquals("Z_q0", Observable.pauliZ(one).getName());

        assertEquals(3, Observable.pauliX(three).getNumQubits());
        assertEquals(3, Observable.pauliY(three).getNumQubits());
        assertEquals(3, Observable.pauliZ(three).getNumQubits());

        ComplexMatrix[] eigen = Observable.pauliZ(one).getEigenvectors();
        assertEquals(2, eigen.length);
        assertEquals(2, eigen[0].rows());
    }

    // --------------------------------------------------------------- ParameterSpace

    @Test
    void parameterSpaceNumericGettersRejectNonNumericValues() {
        ParameterSpace space = new ParameterSpace();
        space.addParameter("theta", 0.5, 0.0, 1.0);
        space.addDiscreteParameter("optimizer", List.of("adam", "sgd"), "adam");

        assertEquals(0.5, space.getDoubleValue("theta"), EPS);
        assertEquals(0, space.getIntValue("theta"), EPS);

        assertThrows(IllegalArgumentException.class, () -> space.getDoubleValue("optimizer"));
        assertThrows(IllegalArgumentException.class, () -> space.getIntValue("optimizer"));
        assertThrows(IllegalArgumentException.class, () -> space.getDoubleValue("missing"));
    }

    // --------------------------------------------------------------------- Complex

    @Test
    void complexPolarRoundTripAndText() {
        Complex c = Complex.fromPolar(Math.sqrt(2.0), Math.PI / 4);
        assertEquals(1.0, c.real(), 1e-9);
        assertEquals(1.0, c.imag(), 1e-9);
        assertEquals(c, Complex.parse(c.toString()));
    }
}
