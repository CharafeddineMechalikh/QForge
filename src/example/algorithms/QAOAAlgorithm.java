package example.algorithms;

import java.util.ArrayList;
import java.util.List;

import com.quantum.simulator.events.QuantumEvent;
import com.quantum.simulator.operations.SingleQubitGate;
import com.quantum.simulator.state.Qubit;

import example.EventBuilderHelper;

/**
 * Builds QAOA circuits for a MaxCut style cost Hamiltonian.
 *
 * <p>Each layer applies the problem unitary for every edge as a CNOT, an RZ rotation by
 * {@code 2 * gamma}, and a second CNOT, then applies the transverse field mixer as an RX rotation
 * by {@code 2 * beta} on every qubit. The angles are supplied by the caller; this class does not
 * optimize them.</p>
 */
public class QAOAAlgorithm {
    
    /**
     * Builds a QAOA circuit with one layer per entry of {@code gamma}.
     *
     * <p>The circuit starts with a Hadamard on every qubit to prepare the plus state and then
     * alternates problem and mixer unitaries. In the implementation {@code gamma} drives the
     * problem unitary and {@code beta} drives the mixer.</p>
     *
     * @param qubits the qubits of the problem register, indexed by the edge endpoints
     * @param gamma the problem angles, one per layer; its length sets the layer count
     * @param beta the mixer angles, one per layer
     * @param costHamiltonian the cost terms, whose edges are applied in order
     * @return the events of the circuit, in the order they should be scheduled
     * @throws ArrayIndexOutOfBoundsException if {@code beta} has fewer entries than {@code gamma}
     */
    public static QuantumEvent[] createQAOACircuit(
        Qubit[] qubits,
        double[] gamma, // Mixing angles
        double[] beta,  // Problem angles
        CostHamiltonian costHamiltonian
    ) {
        // |γ,β> = ∏ e^{-iβ_p H_M} e^{-iγ_p H_C} ... e^{-iβ_1 H_M} e^{-iγ_1 H_C} |+>xn
        List<QuantumEvent> circuit = new ArrayList<>();
        int p = gamma.length; // Number of layers
        
        // Initial state: |+>xn
        for (int i = 0; i < qubits.length; i++) {
            circuit.add(EventBuilderHelper.createSingleQubitGate(
                "H_init" + i, SingleQubitGate.createHadamard(qubits[i]), qubits[i]));
        }
        
        // Apply p layers
        for (int layer = 0; layer < p; layer++) {
            // Problem unitary: e^{-iγ H_C}
            circuit.addAll(createProblemUnitary(qubits, gamma[layer], costHamiltonian, layer));
            
            // Mixer unitary: e^{-iβ H_M}
            circuit.addAll(createMixerUnitary(qubits, beta[layer], layer));
        }
        
        return circuit.toArray(new QuantumEvent[0]);
    }
    
    private static List<QuantumEvent> createProblemUnitary(
        Qubit[] qubits, double gamma, CostHamiltonian cost, int layer) {
        List<QuantumEvent> events = new ArrayList<>();
        
        // Example: MaxCut problem on a graph
        // H_C = Σ_{(i,j)∈E} Z_i Z_j
        // e^{-iγ Z_i Z_j} = CNOT RZ(2γ) CNOT
        
        // For each edge in the graph
        for (Edge edge : cost.getEdges()) {
            int i = edge.from;
            int j = edge.to;
            
            // Implement e^{-iγ Z_i Z_j}
            events.add(EventBuilderHelper.createCNOT(
                "P_CNOT1_" + layer + "_" + i + "_" + j, qubits[i], qubits[j]));
            events.add(EventBuilderHelper.createSingleQubitGate(
                "P_RZ_" + layer + "_" + j, SingleQubitGate.createRZ(2 * gamma, qubits[j]), qubits[j]));
            events.add(EventBuilderHelper.createCNOT(
                "P_CNOT2_" + layer + "_" + i + "_" + j, qubits[i], qubits[j]));
        }
        
        return events;
    }
    
    private static List<QuantumEvent> createMixerUnitary(
        Qubit[] qubits, double beta, int layer) {
        List<QuantumEvent> events = new ArrayList<>();
        
        // Transverse field mixer: H_M = Σ_i X_i
        // e^{-iβ X_i} = RX(2β)
        for (int i = 0; i < qubits.length; i++) {
            events.add(EventBuilderHelper.createSingleQubitGate(
                "M_RX_" + layer + "_" + i,
                SingleQubitGate.createRX(2 * beta, qubits[i]), qubits[i]));
        }
        
        return events;
    }
    
    /**
     * Holds the edges of a MaxCut cost Hamiltonian.
     */
    public static class CostHamiltonian {
        private List<Edge> edges;
        
        /**
         * Creates a cost Hamiltonian from the given edges.
         *
         * @param edges the graph edges, used in the order supplied
         */
        public CostHamiltonian(List<Edge> edges) {
            this.edges = edges;
        }
        
        /**
         * Returns the edges of the cost Hamiltonian.
         *
         * @return the edge list
         */
        public List<Edge> getEdges() {
            return edges;
        }
    }
    
    /**
     * An undirected edge between two qubit indices.
     */
    public static class Edge {
        /** The index of the first endpoint. */
        public final int from;
        /** The index of the second endpoint. */
        public final int to;
        
        /**
         * Creates an edge.
         *
         * @param from the index of the first endpoint
         * @param to the index of the second endpoint
         */
        public Edge(int from, int to) {
            this.from = from;
            this.to = to;
        }
    }
}