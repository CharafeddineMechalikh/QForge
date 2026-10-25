package com.quantum.simulator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.quantum.simulator.config.SimulationBackend;
import com.quantum.simulator.config.SimulationConfig;
import com.quantum.simulator.core.QuantumSimulator;
import com.quantum.simulator.core.SimulationResult;
import com.quantum.simulator.events.QuantumEvent;
import com.quantum.simulator.measurement.MeasurementOperator;
import com.quantum.simulator.observer.SimulationObserver;
import com.quantum.simulator.operations.SingleQubitGate;
import com.quantum.simulator.state.Qubit;
import com.quantum.simulator.state.QuantumState;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class MetricsTest {

    @Test
    void observersReceiveEventsAndMetricsArePopulated() throws Exception {
        SimulationConfig config = new SimulationConfig();
        config.setNumQubits(2);
        config.setBackend(SimulationBackend.STATE_VECTOR);
        QuantumSimulator simulator = new QuantumSimulator();

        AtomicInteger updates = new AtomicInteger();
        AtomicInteger nullEvents = new AtomicInteger();
        AtomicInteger scheduled = new AtomicInteger();
        AtomicInteger completed = new AtomicInteger();
        simulator.addObserver(new SimulationObserver() {
            @Override
            public void onStateUpdate(QuantumState state, QuantumEvent event) {
                updates.incrementAndGet();
                if (event == null) {
                    nullEvents.incrementAndGet();
                }
            }

            @Override
            public void onEventScheduled(QuantumEvent event) {
                scheduled.incrementAndGet();
            }

            @Override
            public void onCompleted(SimulationResult result) {
                completed.incrementAndGet();
            }
        });

        simulator.initialize(config);
        Qubit q0 = new Qubit(0, "q0");
        Qubit q1 = new Qubit(1, "q1");
        simulator.scheduleEvent(TestSupport.event("H", SingleQubitGate.createHadamard(q0), q0));
        simulator.scheduleEvent(TestSupport.event("X", SingleQubitGate.createPauliX(q1), q1));
        SimulationResult result = simulator.run();

        assertEquals(2, updates.get());
        assertEquals(0, nullEvents.get());
        assertEquals(2, scheduled.get());
        assertEquals(1, completed.get());

        assertEquals(2L, result.getMetric("state_updates").longValue());
        assertEquals(2L, result.getMetric("events_scheduled").longValue());
        assertEquals(2L, result.getMetric("steps_executed").longValue());
        assertEquals(2L, result.getMetric("total_operations").longValue());
        assertNotNull(result.getMetric("average_fidelity"));
        assertTrue(result.getMetric("last_state_distance").doubleValue() >= 0.0);
    }

    @Test
    void measurementMetricsAndJsonSerializationWork() throws Exception {
        Qubit[] qubits = TestSupport.qubits(2);
        MeasurementOperator measurement = MeasurementOperator.computationalBasis(qubits, 100);
        SimulationResult result = TestSupport.run(2, SimulationBackend.STATE_VECTOR,
            List.of(TestSupport.event("measure", measurement, qubits)));

        assertEquals(1L, result.getMetric("measurements").longValue());
        assertEquals(1L, result.getMetric("steps_executed").longValue());
        String json = result.toJson();
        assertTrue(json.contains("\"stepsExecuted\" : 1"));
        assertTrue(json.contains("\"measurements\""));
    }

    @Test
    void resourceSuccessRateCountsFailuresAsAttempts() {
        com.quantum.simulator.resources.ResourceStatistics statistics =
            new com.quantum.simulator.resources.ResourceStatistics(
                1, 2, 0,
                new com.quantum.simulator.resources.ResourceUtilization(0, 1, 0, 0, 0, 1),
                System.currentTimeMillis());
        assertEquals(1.0 / 3.0, statistics.getSuccessRate(), 1e-9);
    }
}
