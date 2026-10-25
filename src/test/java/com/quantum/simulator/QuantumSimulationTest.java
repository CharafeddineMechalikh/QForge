package com.quantum.simulator;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

import com.quantum.simulator.config.SimulationConfig;
import com.quantum.simulator.core.QuantumSimulator;
import com.quantum.simulator.core.SimulationResult;
import com.quantum.simulator.events.QuantumEvent;
import com.quantum.simulator.operations.MultiQubitGate;
import com.quantum.simulator.operations.SingleQubitGate;
import com.quantum.simulator.state.QuantumState;
import com.quantum.simulator.state.Qubit;

public class QuantumSimulationTest {
    
    @Test
    public void testSingleQubitHadamard() throws Exception {
        SimulationConfig config = new SimulationConfig();
        config.setNumQubits(1);
        
        QuantumSimulator simulator = new QuantumSimulator();
        simulator.initialize(config);
        
        Qubit q0 = new Qubit(0);
        simulator.scheduleEvent(
            new QuantumEvent.Builder("H")
                .withOperation(SingleQubitGate.createHadamard(q0))
                .build()
        );
        
        SimulationResult result = simulator.run();
        QuantumState state = result.getFinalState();
        
        // After Hadamard on |0>, we should have equal superposition
        double[] probs = state.getProbabilities();
        assertEquals(0.5, probs[0], 1e-10);
        assertEquals(0.5, probs[1], 1e-10);
    }
    
    @Test
    public void testBellStateEntanglement() throws Exception {
        SimulationConfig config = new SimulationConfig();
        config.setNumQubits(2);
        
        QuantumSimulator simulator = new QuantumSimulator();
        simulator.initialize(config);
        
        Qubit q0 = new Qubit(0);
        Qubit q1 = new Qubit(1);
        
        // Create Bell state: H on q0, CNOT q0->q1
        simulator.scheduleEvent(
            new QuantumEvent.Builder("H")
                .withOperation(SingleQubitGate.createHadamard(q0))
                .build()
        );
        
        simulator.scheduleEvent(
            new QuantumEvent.Builder("CNOT")
                .withOperation(MultiQubitGate.createCNOT(q0, q1))
                .build()
        );
        
        SimulationResult result = simulator.run();
        QuantumState state = result.getFinalState();
        
        // Bell state should have |00> and |11> with 0.5 probability each
        double[] probs = state.getProbabilities();
        assertEquals(0.5, probs[0], 1e-10); // |00>
        assertEquals(0.0, probs[1], 1e-10); // |01>
        assertEquals(0.0, probs[2], 1e-10); // |10>
        assertEquals(0.5, probs[3], 1e-10); // |11>
    }
}