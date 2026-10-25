package com.quantum.simulator;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.quantum.simulator.classical.VariationalQuantumEigensolver;
import com.quantum.simulator.config.SimulationBackend;
import com.quantum.simulator.config.SimulationConfig;
import com.quantum.simulator.core.QuantumSimulator;
import com.quantum.simulator.core.SimulationResult;
import com.quantum.simulator.math.ComplexMatrix;
import com.quantum.simulator.measurement.Observable;
import com.quantum.simulator.state.Qubit;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

class VqeTest {

    @Test
    @Timeout(30)
    void vqeFindsH2GroundState() throws Exception {
        ComplexMatrix identity2 = ComplexMatrix.identity(2);
        ComplexMatrix z = ComplexMatrix.pauliZ();
        ComplexMatrix x = ComplexMatrix.pauliX();

        ComplexMatrix h2 = ComplexMatrix.identity(4).scale(-1.0523);
        h2 = h2.add(z.tensorProduct(identity2).scale(0.3979));
        h2 = h2.add(identity2.tensorProduct(z).scale(0.3979));
        h2 = h2.add(z.tensorProduct(z).scale(-0.0113));
        h2 = h2.add(x.tensorProduct(x).scale(0.1809));
        double exact = h2.eigenvalues()[0];

        Qubit[] qubits = {new Qubit(0, "q0"), new Qubit(1, "q1")};
        SimulationConfig config = new SimulationConfig();
        config.setNumQubits(2);
        config.setBackend(SimulationBackend.STATE_VECTOR);
        QuantumSimulator simulator = new QuantumSimulator();
        simulator.initialize(config);

        Observable hamiltonian = new Observable(
            "H2", h2, qubits, Observable.ObservableType.HAMILTONIAN);
        Map<String, Object> parameters = new HashMap<>();
        parameters.put("max_iterations", 300);
        parameters.put("learning_rate", 0.15);
        parameters.put("momentum", 0.5);
        parameters.put("convergence_threshold", 1e-8);

        VariationalQuantumEigensolver vqe = new VariationalQuantumEigensolver(
            hamiltonian, Arrays.asList(qubits));
        vqe.initialize(simulator, parameters);
        while (vqe.executeIteration()) {
            // run until convergence or the iteration cap
        }

        double error = Math.abs(vqe.getCurrentCost() - exact);
        assertTrue(error < 1e-3, "VQE error too large: " + error);

        SimulationResult result = vqe.getResult();
        assertNotNull(result.getFinalState());
        assertNotNull(result.getMetric("steps_executed"));
    }
}
