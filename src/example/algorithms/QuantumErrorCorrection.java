package example.algorithms;

import java.util.ArrayList;
import java.util.List;

import com.quantum.simulator.events.QuantumEvent;
import com.quantum.simulator.operations.SingleQubitGate;
import com.quantum.simulator.state.Qubit;

import example.EventBuilderHelper;

/**
 * Builds encoding circuits for simple quantum error correcting codes.
 *
 * <p>These methods only produce the encoding gates. Syndrome extraction, decoding, and recovery
 * operations are not included, with the exception of the phase flip code, which applies
 * Hadamards after encoding but does not undo them at the end.</p>
 */
public class QuantumErrorCorrection {
    
    /**
     * Builds a three-qubit bit-flip encoding: two CNOTs copy the data qubit into both ancillary
     * qubits, mapping {@code alpha|0> + beta|1>} to {@code alpha|000> + beta|111>}.
     *
     * @param dataQubits the data register; only the first element is used
     * @param ancillaryQubits the ancillary register; the first two elements are used
     * @return the encoding events, in the order they should be scheduled
     * @throws IllegalArgumentException if fewer than one data qubit or fewer than two ancillary
     *     qubits are supplied
     */
    public static QuantumEvent[] createBitFlipCode(
        Qubit[] dataQubits, // 1 data qubit
        Qubit[] ancillaryQubits // 2 ancillary qubits
    ) {
        // Encodes |ψ> = α|0> + β|1> into α|000> + β|111>
        List<QuantumEvent> circuit = new ArrayList<>();
        
        if (dataQubits.length < 1 || ancillaryQubits.length < 2) {
            throw new IllegalArgumentException("Need 1 data qubit and 2 ancillary qubits");
        }
        
        Qubit data = dataQubits[0];
        Qubit anc1 = ancillaryQubits[0];
        Qubit anc2 = ancillaryQubits[1];
        
        // Encoding circuit
        circuit.add(EventBuilderHelper.createCNOT("CNOT1", data, anc1));
        circuit.add(EventBuilderHelper.createCNOT("CNOT2", data, anc2));
        
        return circuit.toArray(new QuantumEvent[0]);
    }
    
    /**
     * Builds a phase-flip encoding by applying the bit-flip encoding followed by a Hadamard on
     * each of the three qubits, which moves phase flips into the bit-flip basis.
     *
     * <p>Unlike {@link #createBitFlipCode(Qubit[], Qubit[])}, this method does not validate the
     * array lengths.</p>
     *
     * @param dataQubits the data register; only the first element is used
     * @param ancillaryQubits the ancillary register; the first two elements are used
     * @return the encoding events, in the order they should be scheduled
     * @throws ArrayIndexOutOfBoundsException if either array is shorter than the code requires
     */
    public static QuantumEvent[] createPhaseFlipCode(
        Qubit[] dataQubits,
        Qubit[] ancillaryQubits
    ) {
        // Phase flip code (bit flip in Hadamard basis)
        List<QuantumEvent> circuit = new ArrayList<>();
        
        Qubit data = dataQubits[0];
        Qubit anc1 = ancillaryQubits[0];
        Qubit anc2 = ancillaryQubits[1];
        
        // Encode against bit flips, then rotate each qubit into the X basis
        circuit.add(EventBuilderHelper.createCNOT("CNOT1", data, anc1));
        circuit.add(EventBuilderHelper.createCNOT("CNOT2", data, anc2));
        
        circuit.add(EventBuilderHelper.createSingleQubitGate(
            "H_data2", SingleQubitGate.createHadamard(data), data));
        circuit.add(EventBuilderHelper.createSingleQubitGate(
            "H_anc1_2", SingleQubitGate.createHadamard(anc1), anc1));
        circuit.add(EventBuilderHelper.createSingleQubitGate(
            "H_anc2_2", SingleQubitGate.createHadamard(anc2), anc2));
        
        return circuit.toArray(new QuantumEvent[0]);
    }
    
    /**
     * Builds the nine-qubit Shor encoding circuit.
     *
     * <p>The circuit applies a Hadamard to the first qubit of each three-qubit block and then CNOTs
     * from that qubit to the other two qubits of the block. Starting from the all-zero state this
     * prepares the product of three GHZ blocks, which is the logical zero codeword. The circuit
     * contains no syndrome measurement or recovery stage.</p>
     *
     * @param qubits the nine qubits of the code, grouped as three consecutive blocks
     * @return the encoding events, in the order they should be scheduled
     * @throws IllegalArgumentException if fewer than nine qubits are supplied
     */
    public static QuantumEvent[] createShorCode(
        Qubit[] qubits // 9 qubits needed
    ) {
        // Shor's 9-qubit code (corrects arbitrary single-qubit error)
        if (qubits.length < 9) {
            throw new IllegalArgumentException("Shor code requires 9 qubits");
        }
        
        List<QuantumEvent> circuit = new ArrayList<>();
        
        // Encode against phase flips, then against bit flips inside each block
        for (int block = 0; block < 3; block++) {
            int base = 3 * block;
            circuit.add(EventBuilderHelper.createSingleQubitGate(
                "H_" + base, SingleQubitGate.createHadamard(qubits[base]), qubits[base]));
        }
        
        for (int block = 0; block < 3; block++) {
            int base = 3 * block;
            for (int i = 1; i <= 2; i++) {
                circuit.add(EventBuilderHelper.createCNOT(
                    "CNOT_bit_" + block + "_" + i, qubits[base], qubits[base + i]));
            }
        }
        
        return circuit.toArray(new QuantumEvent[0]);
    }
}