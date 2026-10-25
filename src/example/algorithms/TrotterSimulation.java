package example.algorithms;

import java.util.ArrayList;
import java.util.List;

import com.quantum.simulator.events.QuantumEvent;
import com.quantum.simulator.operations.SingleQubitGate;
import com.quantum.simulator.state.Qubit;

import example.EventBuilderHelper;

public class TrotterSimulation {
    
    public static QuantumEvent[] createTrotterCircuit(
        Qubit[] qubits,
        Hamiltonian hamiltonian,
        double time,
        int trotterSteps
    ) {
        // e^{-iHt} ≈ (∏_j e^{-iH_j Δt})^r where Δt = t/r
        if (trotterSteps <= 0) {
            throw new IllegalArgumentException("Trotter steps must be positive");
        }
        List<QuantumEvent> circuit = new ArrayList<>();
        double deltaT = time / trotterSteps;
        
        for (int step = 0; step < trotterSteps; step++) {
            for (HamiltonianTerm term : hamiltonian.getTerms()) {
                circuit.addAll(createTermEvolution(qubits, term, deltaT, step));
            }
        }
        
        return circuit.toArray(new QuantumEvent[0]);
    }
    
    private static List<QuantumEvent> createTermEvolution(
        Qubit[] qubits, HamiltonianTerm term, double deltaT, int step) {
        List<QuantumEvent> events = new ArrayList<>();
        double angle = 2.0 * term.coefficient * deltaT;
        String tag = term.type + "_" + step;
        
        switch (term.type) {
            case "I":
                break;
            case "X":
                requireQubits(term, 1);
                events.add(EventBuilderHelper.createSingleQubitGate(
                    "Trotter_" + tag, SingleQubitGate.createRX(angle, qubits[term.qubits[0]]),
                    qubits[term.qubits[0]]));
                break;
            case "Y":
                requireQubits(term, 1);
                events.add(EventBuilderHelper.createSingleQubitGate(
                    "Trotter_" + tag, SingleQubitGate.createRY(angle, qubits[term.qubits[0]]),
                    qubits[term.qubits[0]]));
                break;
            case "Z":
                requireQubits(term, 1);
                events.add(EventBuilderHelper.createSingleQubitGate(
                    "Trotter_" + tag, SingleQubitGate.createRZ(angle, qubits[term.qubits[0]]),
                    qubits[term.qubits[0]]));
                break;
            case "ZZ":
                requireQubits(term, 2);
                events.addAll(pauliProductEvolution(qubits, term, angle, tag, false, false));
                break;
            case "XX":
                requireQubits(term, 2);
                events.addAll(pauliProductEvolution(qubits, term, angle, tag, true, false));
                break;
            case "YY":
                requireQubits(term, 2);
                events.addAll(pauliProductEvolution(qubits, term, angle, tag, true, true));
                break;
            default:
                throw new IllegalArgumentException(
                    "Unsupported Trotter term type: " + term.type);
        }
        
        return events;
    }
    
    private static void requireQubits(HamiltonianTerm term, int expected) {
        if (term.qubits == null || term.qubits.length != expected) {
            throw new IllegalArgumentException(
                "Term " + term.type + " requires " + expected + " qubit indices");
        }
    }
    
    private static List<QuantumEvent> pauliProductEvolution(
        Qubit[] qubits, HamiltonianTerm term, double angle, String tag,
        boolean xBasis, boolean yBasis) {
        List<QuantumEvent> events = new ArrayList<>();
        int i = term.qubits[0];
        int j = term.qubits[1];
        
        if (yBasis) {
            // e^{-iθYY} = (SxS) e^{-iθXX} (S†xS†)
            events.add(phase(-Math.PI / 2, qubits[i], "Trotter_" + tag + "_Sdg_i"));
            events.add(phase(-Math.PI / 2, qubits[j], "Trotter_" + tag + "_Sdg_j"));
        }
        if (xBasis || yBasis) {
            // e^{-iθXX} = (HxH) e^{-iθZZ} (HxH)
            events.add(hadamard(qubits[i], "Trotter_" + tag + "_H_i"));
            events.add(hadamard(qubits[j], "Trotter_" + tag + "_H_j"));
        }
        
        events.add(EventBuilderHelper.createCNOT(
            "Trotter_CNOT1_" + tag + "_" + i + "_" + j, qubits[i], qubits[j]));
        events.add(EventBuilderHelper.createSingleQubitGate(
            "Trotter_RZ_" + tag + "_" + j,
            SingleQubitGate.createRZ(angle, qubits[j]), qubits[j]));
        events.add(EventBuilderHelper.createCNOT(
            "Trotter_CNOT2_" + tag + "_" + i + "_" + j, qubits[i], qubits[j]));
        
        if (xBasis || yBasis) {
            events.add(hadamard(qubits[i], "Trotter_" + tag + "_H_i2"));
            events.add(hadamard(qubits[j], "Trotter_" + tag + "_H_j2"));
        }
        if (yBasis) {
            events.add(phase(Math.PI / 2, qubits[i], "Trotter_" + tag + "_S_i"));
            events.add(phase(Math.PI / 2, qubits[j], "Trotter_" + tag + "_S_j"));
        }
        
        return events;
    }
    
    private static QuantumEvent hadamard(Qubit qubit, String name) {
        return EventBuilderHelper.createSingleQubitGate(
            name, SingleQubitGate.createHadamard(qubit), qubit);
    }
    
    private static QuantumEvent phase(double angle, Qubit qubit, String name) {
        return EventBuilderHelper.createSingleQubitGate(
            name, SingleQubitGate.createPhase(angle, qubit), qubit);
    }
    
    public static class Hamiltonian {
        private List<HamiltonianTerm> terms;
        
        public Hamiltonian(List<HamiltonianTerm> terms) {
            this.terms = terms;
        }
        
        public List<HamiltonianTerm> getTerms() {
            return terms;
        }
    }
    
    public static class HamiltonianTerm {
        public final String type; // "Z", "X", "ZZ", "XX", etc.
        public final int[] qubits;
        public final double coefficient;
        
        public HamiltonianTerm(String type, int[] qubits, double coefficient) {
            this.type = type;
            this.qubits = qubits;
            this.coefficient = coefficient;
        }
    }
}