package com.quantum.simulator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.quantum.simulator.config.SimulationBackend;
import com.quantum.simulator.config.SimulationConfig;
import com.quantum.simulator.core.QuantumSimulator;
import com.quantum.simulator.events.QuantumEvent;
import com.quantum.simulator.operations.QuantumOperation;
import com.quantum.simulator.operations.SingleQubitGate;
import com.quantum.simulator.plugin.Plugin;
import com.quantum.simulator.plugin.PluginContext;
import com.quantum.simulator.state.QuantumState;
import com.quantum.simulator.state.Qubit;
import org.junit.jupiter.api.Test;

class PluginTest {

    @Test
    void pluginLifecycleAndHooksAreWired() throws Exception {
        SimulationConfig config = new SimulationConfig();
        config.setNumQubits(1);
        config.setBackend(SimulationBackend.STATE_VECTOR);
        QuantumSimulator simulator = new QuantumSimulator();
        simulator.initialize(config);

        RecordingPlugin plugin = new RecordingPlugin();
        simulator.getPluginManager().registerPlugin(plugin);
        assertEquals(1, plugin.initializeCalls);

        Qubit q0 = simulator.getContext().getQubit(0);
        simulator.scheduleEvent(TestSupport.event("H", SingleQubitGate.createHadamard(q0), q0));
        simulator.run();

        assertEquals(1, plugin.startCalls);
        assertEquals(1, plugin.endCalls);
        assertEquals(1, plugin.scheduledCalls);
        assertEquals(1, plugin.stateUpdateCalls);
        assertEquals(1, plugin.operationCalls);
        assertNotNull(simulator.getPluginManager().getPlugin("RecordingPlugin"));

        simulator.shutdown();
        assertEquals(1, plugin.cleanupCalls);
        assertTrue(simulator.getPluginManager().listPlugins().isEmpty());
    }

    private static class RecordingPlugin implements Plugin {
        int initializeCalls;
        int startCalls;
        int endCalls;
        int scheduledCalls;
        int stateUpdateCalls;
        int operationCalls;
        int cleanupCalls;

        @Override
        public void initialize(PluginContext context) {
            initializeCalls++;
        }

        @Override
        public void cleanup() {
            cleanupCalls++;
        }

        @Override
        public String getName() {
            return "RecordingPlugin";
        }

        @Override
        public String getVersion() {
            return "1.0";
        }

        @Override
        public String getDescription() {
            return "Test plugin";
        }

        @Override
        public void onSimulationStart(com.quantum.simulator.core.SimulationContext context) {
            startCalls++;
        }

        @Override
        public void onSimulationEnd(com.quantum.simulator.core.SimulationContext context) {
            endCalls++;
        }

        @Override
        public void onEventScheduled(QuantumEvent event) {
            scheduledCalls++;
        }

        @Override
        public void onStateUpdate(QuantumState state, QuantumEvent event) {
            stateUpdateCalls++;
        }

        @Override
        public void onOperationApplied(QuantumOperation operation) {
            operationCalls++;
        }
    }
}
