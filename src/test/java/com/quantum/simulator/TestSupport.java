package com.quantum.simulator;

import com.quantum.simulator.config.SimulationBackend;
import com.quantum.simulator.config.SimulationConfig;
import com.quantum.simulator.core.QuantumSimulator;
import com.quantum.simulator.core.SimulationResult;
import com.quantum.simulator.events.QuantumEvent;
import com.quantum.simulator.operations.QuantumOperation;
import com.quantum.simulator.state.Qubit;
import com.quantum.simulator.state.StateVector;
import java.time.Instant;
import java.util.List;

final class TestSupport {
    private TestSupport() {
    }

    static SimulationResult run(int numQubits, SimulationBackend backend, List<QuantumEvent> events)
        throws Exception {
        SimulationConfig config = new SimulationConfig();
        config.setNumQubits(numQubits);
        config.setBackend(backend);
        QuantumSimulator simulator = new QuantumSimulator();
        simulator.initialize(config);

        Instant time = Instant.now().minusSeconds(5);
        int index = 0;
        for (QuantumEvent event : events) {
            simulator.scheduleEvent(new QuantumEvent.Builder("test_" + (index++))
                .withScheduledTime(time)
                .withOperation(event.getOperation())
                .withTargetQubits(event.getTargetQubits())
                .build());
            time = time.plusMillis(1);
        }
        return simulator.run();
    }

    static StateVector runStateVector(int numQubits, List<QuantumEvent> events) throws Exception {
        return (StateVector) run(numQubits, SimulationBackend.STATE_VECTOR, events).getFinalState();
    }

    static QuantumEvent event(String name, QuantumOperation operation, Qubit... targetQubits) {
        return new QuantumEvent.Builder(name)
            .withOperation(operation)
            .withTargetQubits(targetQubits)
            .build();
    }

    static Qubit[] qubits(int count) {
        Qubit[] qubits = new Qubit[count];
        for (int i = 0; i < count; i++) {
            qubits[i] = new Qubit(i, "q" + i);
        }
        return qubits;
    }
}
