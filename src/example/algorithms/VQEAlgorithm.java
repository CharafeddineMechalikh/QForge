package example.algorithms;

import com.quantum.simulator.state.Qubit;

import example.EventBuilderHelper;

import com.quantum.simulator.operations.*;
import com.quantum.simulator.events.QuantumEvent;
import java.util.*;

/**
 * Builds variational ansatz circuits, basis rotation circuits, and small molecular Hamiltonians
 * for variational eigensolver experiments.
 *
 * <p>Four ansatz names are recognized: {@code HardwareEfficient}, {@code UnitaryCoupledCluster},
 * {@code Chemistry}, and any other value for a simple alternating layer ansatz. The coupled
 * cluster and chemistry variants contain special cases for particular qubit counts and simplified
 * excitation decompositions, which the method documentation describes.</p>
 */
public class VQEAlgorithm {
    
    /**
     * Builds an ansatz circuit.
     *
     * <p>Parameter keys depend on the selected ansatz and are read with a zero default, so a
     * partial map produces a partly unparameterized circuit.</p>
     *
     * @param qubits the qubits of the ansatz
     * @param parameters the parameter map
     * @param ansatzType the ansatz name, matched exactly
     * @return the events of the circuit, in the order they should be scheduled
     */
    public static QuantumEvent[] createVQECircuit(
        Qubit[] qubits,
        Map<String, Double> parameters,
        String ansatzType
    ) {
        List<QuantumEvent> circuit = new ArrayList<>();
        
        switch (ansatzType) {
            case "HardwareEfficient":
                circuit.addAll(createHardwareEfficientAnsatz(qubits, parameters));
                break;
            case "UnitaryCoupledCluster":
                circuit.addAll(createUCCAnsatz(qubits, parameters));
                break;
            case "Chemistry":
                circuit.addAll(createChemistryAnsatz(qubits, parameters));
                break;
            default:
                circuit.addAll(createDefaultAnsatz(qubits, parameters));
        }
        
        return circuit.toArray(new QuantumEvent[0]);
    }
    
    private static List<QuantumEvent> createHardwareEfficientAnsatz(
        Qubit[] qubits, Map<String, Double> params) {
        List<QuantumEvent> events = new ArrayList<>();
        int n = qubits.length;
        int paramIndex = 0;
        
        // First rotation layer
        for (int i = 0; i < n; i++) {
            double theta = params.getOrDefault("theta" + paramIndex++, 0.0);
            events.add(EventBuilderHelper.createSingleQubitGate(
                "RY1_" + i, SingleQubitGate.createRY(theta, qubits[i]), qubits[i]));
        }
        
        // First entangling layer (CNOT ladder)
        for (int i = 0; i < n - 1; i++) {
            events.add(EventBuilderHelper.createCNOT(
                "CNOT1_" + i, qubits[i], qubits[i+1]));
        }
        
        // Second rotation layer
        for (int i = 0; i < n; i++) {
            double phi = params.getOrDefault("phi" + paramIndex++, 0.0);
            events.add(EventBuilderHelper.createSingleQubitGate(
                "RZ1_" + i, SingleQubitGate.createRZ(phi, qubits[i]), qubits[i]));
        }
        
        // Second entangling layer (reverse CNOT ladder)
        for (int i = n - 1; i > 0; i--) {
            events.add(EventBuilderHelper.createCNOT(
                "CNOT2_" + i, qubits[i-1], qubits[i]));
        }
        
        // Third rotation layer
        for (int i = 0; i < n; i++) {
            double psi = params.getOrDefault("psi" + paramIndex++, 0.0);
            events.add(EventBuilderHelper.createSingleQubitGate(
                "RY2_" + i, SingleQubitGate.createRY(psi, qubits[i]), qubits[i]));
        }
        
        return events;
    }
    
    private static List<QuantumEvent> createUCCAnsatz(
        Qubit[] qubits, Map<String, Double> params) {
        List<QuantumEvent> events = new ArrayList<>();
        int n = qubits.length;
        
        // Hartree-Fock reference state preparation
        // For H2 molecule in minimal basis (STO-3G), we need 4 qubits
        if (n == 4) {
            // Reference state |0011> (α-spin orbital 1 and β-spin orbital 1 occupied)
            events.add(EventBuilderHelper.createSingleQubitGate(
                "X0", SingleQubitGate.createPauliX(qubits[2]), qubits[2]));
            events.add(EventBuilderHelper.createSingleQubitGate(
                "X1", SingleQubitGate.createPauliX(qubits[3]), qubits[3]));
            
            // UCCSD excitation operator exp(θ(a†₂a₀a†₃a₁ - h.c.))
            double theta = params.getOrDefault("theta", 0.0);
            
            // Decomposition of the excitation operator
            // exp(θ(σ⁺σ⁻ - σ⁻σ⁺)) = CNOT RY CNOT
            events.add(EventBuilderHelper.createCNOT("UCC1", qubits[0], qubits[2]));
            events.add(EventBuilderHelper.createCNOT("UCC2", qubits[1], qubits[3]));
            
            events.add(EventBuilderHelper.createSingleQubitGate(
                "UCC_RY", SingleQubitGate.createRY(2 * theta, qubits[3]), qubits[3]));
            
            events.add(EventBuilderHelper.createCNOT("UCC3", qubits[1], qubits[3]));
            events.add(EventBuilderHelper.createCNOT("UCC4", qubits[0], qubits[2]));
        } else {
            // Generic UCC ansatz for more qubits
            createGenericUCCAnsatz(events, qubits, params);
        }
        
        return events;
    }
    
    private static void createGenericUCCAnsatz(
        List<QuantumEvent> events, Qubit[] qubits, Map<String, Double> params) {
        int n = qubits.length;
        int paramIndex = 0;
        
        // Prepare Hartree-Fock state (first half of qubits = 1, second half = 0)
        for (int i = 0; i < n/2; i++) {
            events.add(EventBuilderHelper.createSingleQubitGate(
                "HF_X" + i, SingleQubitGate.createPauliX(qubits[i]), qubits[i]));
        }
        
        // Single excitations
        for (int i = 0; i < n/2; i++) { // occupied orbitals
            for (int a = n/2; a < n; a++) { // virtual orbitals
                double theta = params.getOrDefault("theta_s" + paramIndex++, 0.0);
                events.addAll(createSingleExcitation(qubits[i], qubits[a], theta, 
                    "SE_" + i + "_" + a));
            }
        }
        
        // Double excitations (simplified)
        if (n >= 4) {
            double theta_double = params.getOrDefault("theta_d", 0.0);
            events.addAll(createDoubleExcitation(
                qubits[0], qubits[1], qubits[2], qubits[3], theta_double, "DE"));
        }
    }
    
    private static List<QuantumEvent> createSingleExcitation(
        Qubit occupied, Qubit virtual, double theta, String name) {
        List<QuantumEvent> events = new ArrayList<>();
        
        // exp(θ(a†_virtual a_occupied - h.c.)) decomposition
        events.add(EventBuilderHelper.createCNOT(name + "_CNOT1", occupied, virtual));
        events.add(EventBuilderHelper.createSingleQubitGate(
            name + "_RY", SingleQubitGate.createRY(2 * theta, virtual), virtual));
        events.add(EventBuilderHelper.createCNOT(name + "_CNOT2", occupied, virtual));
        
        return events;
    }
    
    private static List<QuantumEvent> createDoubleExcitation(
        Qubit occ1, Qubit occ2, Qubit vir1, Qubit vir2, double theta, String name) {
        List<QuantumEvent> events = new ArrayList<>();
        
        // Simplified double excitation circuit
        events.add(EventBuilderHelper.createCNOT(name + "_CNOT1", occ1, vir1));
        events.add(EventBuilderHelper.createCNOT(name + "_CNOT2", occ2, vir2));
        
        events.add(EventBuilderHelper.createCNOT(name + "_CNOT3", vir1, vir2));
        events.add(EventBuilderHelper.createSingleQubitGate(
            name + "_RZ", SingleQubitGate.createRZ(2 * theta, vir2), vir2));
        events.add(EventBuilderHelper.createCNOT(name + "_CNOT4", vir1, vir2));
        
        events.add(EventBuilderHelper.createCNOT(name + "_CNOT5", occ2, vir2));
        events.add(EventBuilderHelper.createCNOT(name + "_CNOT6", occ1, vir1));
        
        return events;
    }
    
    private static List<QuantumEvent> createChemistryAnsatz(
        Qubit[] qubits, Map<String, Double> params) {
        List<QuantumEvent> events = new ArrayList<>();
        int n = qubits.length;
        
        // This ansatz is tailored for specific molecules
        // Example: Hydrogen molecule (H2) in minimal basis
        
        if (n == 2) {
            // Simple chemistry ansatz for 2-qubit H2 simulation
            double theta = params.getOrDefault("theta", 0.0);
            
            // Prepare Hartree-Fock state |01>
            events.add(EventBuilderHelper.createSingleQubitGate(
                "HF_X", SingleQubitGate.createPauliX(qubits[1]), qubits[1]));
            
            // UCC-like ansatz
            events.add(EventBuilderHelper.createSingleQubitGate(
                "RY1", SingleQubitGate.createRY(theta, qubits[0]), qubits[0]));
            events.add(EventBuilderHelper.createSingleQubitGate(
                "RY2", SingleQubitGate.createRY(theta, qubits[1]), qubits[1]));
            
            events.add(EventBuilderHelper.createCNOT("Chem_CNOT", qubits[0], qubits[1]));
            
            events.add(EventBuilderHelper.createSingleQubitGate(
                "RZ", SingleQubitGate.createRZ(2 * theta, qubits[1]), qubits[1]));
            
            events.add(EventBuilderHelper.createCNOT("Chem_CNOT2", qubits[0], qubits[1]));
            
        } else if (n == 4) {
            // Lithium Hydride (LiH) or larger molecule simulation
            createMoleculeSpecificAnsatz(events, qubits, params);
        }
        
        return events;
    }
    
    private static void createMoleculeSpecificAnsatz(
        List<QuantumEvent> events, Qubit[] qubits, Map<String, Double> params) {
        // Custom ansatz for specific molecular geometry
        int n = qubits.length;
        int paramIndex = 0;
        
        // Initialize based on molecular orbital occupations
        // For LiH minimal basis example: 6 qubits
        if (n == 6) {
            // Occupied orbitals: 1s (Li), 1s (H), 2s (Li) - 3 alpha, 3 beta
            for (int i = 0; i < 3; i++) {
                events.add(EventBuilderHelper.createSingleQubitGate(
                    "Init_X" + i, SingleQubitGate.createPauliX(qubits[i]), qubits[i]));
                events.add(EventBuilderHelper.createSingleQubitGate(
                    "Init_X" + (i+3), SingleQubitGate.createPauliX(qubits[i+3]), qubits[i+3]));
            }
            
            // Parameterized excitations
            double[] thetas = new double[3];
            for (int i = 0; i < 3; i++) {
                thetas[i] = params.getOrDefault("theta" + i, 0.0);
            }
            
            // Single excitations within active space
            for (int i = 0; i < 2; i++) {
                events.addAll(createSingleExcitation(
                    qubits[i], qubits[i+1], thetas[i], "Chem_SE" + i));
                events.addAll(createSingleExcitation(
                    qubits[i+3], qubits[i+4], thetas[i], "Chem_SE" + (i+3)));
            }
        }
    }
    
    private static List<QuantumEvent> createDefaultAnsatz(
        Qubit[] qubits, Map<String, Double> params) {
        List<QuantumEvent> events = new ArrayList<>();
        int n = qubits.length;
        
        // Simple alternating layers of rotations and entangling gates
        int layers = params.containsKey("layers") ? 
            params.get("layers").intValue() : 2;
        
        for (int layer = 0; layer < layers; layer++) {
            // Rotation layer
            for (int i = 0; i < n; i++) {
                String key = "layer" + layer + "_qubit" + i;
                double angle = params.getOrDefault(key, 0.0);
                
                // Alternate between RY and RZ
                if (layer % 2 == 0) {
                    events.add(EventBuilderHelper.createSingleQubitGate(
                        "L" + layer + "_RY" + i,
                        SingleQubitGate.createRY(angle, qubits[i]), qubits[i]));
                } else {
                    events.add(EventBuilderHelper.createSingleQubitGate(
                        "L" + layer + "_RZ" + i,
                        SingleQubitGate.createRZ(angle, qubits[i]), qubits[i]));
                }
            }
            
            // Entangling layer
            if (layer < layers - 1) {
                // Linear entanglement
                for (int i = 0; i < n - 1; i += 2) {
                    events.add(EventBuilderHelper.createCNOT(
                        "L" + layer + "_CNOT" + i,
                        qubits[i], qubits[i+1]));
                }
                for (int i = 1; i < n - 1; i += 2) {
                    events.add(EventBuilderHelper.createCNOT(
                        "L" + layer + "_CNOT" + i,
                        qubits[i], qubits[i+1]));
                }
            }
        }
        
        return events;
    }
    
    // Helper method to create measurement circuits for different observables
    /**
     * Builds basis rotation gates for a list of Pauli strings.
     *
     * <p>For each string, an {@code X} position receives a Hadamard, a {@code Y} position receives
     * a phase gate of {@code -PI/2} followed by a Hadamard, and {@code Z} or {@code I} positions
     * receive nothing. The rotations for all observables are concatenated; measurement itself is
     * left to the caller.</p>
     *
     * @param qubits the qubits corresponding to the characters of each string
     * @param observables the Pauli strings to rotate into the computational basis
     * @param parameters unused by the implementation
     * @return the rotation events, in the order they should be scheduled
     * @throws ArrayIndexOutOfBoundsException if a string is longer than the qubit array
     */
    public static QuantumEvent[] createMeasurementCircuit(
        Qubit[] qubits, String[] observables, Map<String, Double> parameters) {
        // Creates circuits for measuring different Pauli terms in Hamiltonian
        List<QuantumEvent> events = new ArrayList<>();
        
        for (String observable : observables) {
            events.addAll(createBasisRotation(qubits, observable, parameters));
            // Measurement would be done classically after circuit execution
        }
        
        return events.toArray(new QuantumEvent[0]);
    }
    
    private static List<QuantumEvent> createBasisRotation(
        Qubit[] qubits, String pauliString, Map<String, Double> params) {
        List<QuantumEvent> events = new ArrayList<>();
        
        for (int i = 0; i < pauliString.length(); i++) {
            char pauli = pauliString.charAt(i);
            Qubit qubit = qubits[i];
            
            switch (pauli) {
                case 'X':
                    // Rotate from X basis to computational basis: H
                    events.add(EventBuilderHelper.createSingleQubitGate(
                        "Basis_H_" + i, SingleQubitGate.createHadamard(qubit), qubit));
                    break;
                case 'Y':
                    // Rotate from Y basis to computational basis: S† H
                    events.add(EventBuilderHelper.createSingleQubitGate(
                        "Basis_Sdg_" + i, 
                        SingleQubitGate.createPhase(-Math.PI/2, qubit), qubit));
                    events.add(EventBuilderHelper.createSingleQubitGate(
                        "Basis_H_" + i, SingleQubitGate.createHadamard(qubit), qubit));
                    break;
                case 'Z':
                    // Already in computational basis
                    break;
                case 'I':
                    // Identity - no rotation needed
                    break;
            }
        }
        
        return events;
    }
    
    // Energy estimation from measurement results
    /**
     * Computes a weighted sum of expectation values.
     *
     * <p>Terms present in the coefficients but absent from the expectation values contribute zero.
     * Terms present only in the expectation values are ignored.</p>
     *
     * @param expectationValues the measured expectation value per Pauli string
     * @param coefficients the coefficient per Pauli string
     * @return the sum of coefficient times expectation value
     */
    public static double estimateEnergy(
        Map<String, Double> expectationValues,
        Map<String, Double> coefficients) {
        
        double totalEnergy = 0.0;
        
        for (Map.Entry<String, Double> entry : coefficients.entrySet()) {
            String pauliTerm = entry.getKey();
            double coefficient = entry.getValue();
            double expectation = expectationValues.getOrDefault(pauliTerm, 0.0);
            
            totalEnergy += coefficient * expectation;
        }
        
        return totalEnergy;
    }
    
    // Example Hamiltonians for common molecules
    /**
     * Returns a simplified H2 Hamiltonian as Pauli coefficients.
     *
     * <p>For two qubits five terms are returned in the parity mapping. For four qubits seven terms
     * of a Jordan-Wigner mapped Hamiltonian are returned, and the code notes that more terms would
     * be needed. Other qubit counts return an empty map.</p>
     *
     * @param numQubits the number of qubits of the target simulation
     * @return the coefficient map keyed by Pauli strings
     */
    public static Map<String, Double> getH2Hamiltonian(int numQubits) {
        Map<String, Double> hamiltonian = new HashMap<>();
        
        if (numQubits == 2) {
            // H2 Hamiltonian in minimal basis (parity mapping)
            hamiltonian.put("II", -1.0523);
            hamiltonian.put("ZI", 0.3979);
            hamiltonian.put("IZ", 0.3979);
            hamiltonian.put("ZZ", -0.0113);
            hamiltonian.put("XX", 0.1809);
        } else if (numQubits == 4) {
            // H2 Hamiltonian in Jordan-Wigner mapping
            hamiltonian.put("IIII", -0.8108);
            hamiltonian.put("ZIII", 0.1209);
            hamiltonian.put("IZII", 0.1209);
            hamiltonian.put("ZZII", 0.1659);
            hamiltonian.put("XXII", 0.1659);
            hamiltonian.put("YYII", 0.1659);
            // ... more terms
        }
        
        return hamiltonian;
    }
    
    /**
     * Returns a simplified LiH Hamiltonian with three Pauli terms on six qubits.
     *
     * <p>The code marks the map as simplified and incomplete.</p>
     *
     * @return the coefficient map keyed by Pauli strings
     */
    public static Map<String, Double> getLiHHamiltonian() {
        Map<String, Double> hamiltonian = new HashMap<>();
        // LiH Hamiltonian terms (simplified)
        hamiltonian.put("IIIIII", -7.882);
        hamiltonian.put("ZIIIII", 0.145);
        hamiltonian.put("IZIIII", 0.145);
        // ... more complex terms
        return hamiltonian;
    }
}