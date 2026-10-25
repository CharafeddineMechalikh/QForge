package com.mechalikh.qforge;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.mechalikh.qforge.config.SimulationBackend;
import com.mechalikh.qforge.config.SimulationConfig;
import com.mechalikh.qforge.core.QuantumSimulator;
import com.mechalikh.qforge.events.QuantumEvent;
import com.mechalikh.qforge.operations.QuantumOperation;
import com.mechalikh.qforge.operations.SingleQubitGate;
import com.mechalikh.qforge.plugin.Plugin;
import com.mechalikh.qforge.plugin.PluginContext;
import com.mechalikh.qforge.state.QuantumState;
import com.mechalikh.qforge.state.Qubit;
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
        public void onSimulationStart(com.mechalikh.qforge.core.SimulationContext context) {
            startCalls++;
        }

        @Override
        public void onSimulationEnd(com.mechalikh.qforge.core.SimulationContext context) {
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
