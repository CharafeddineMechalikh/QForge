package example;

import java.util.Locale;
import com.mechalikh.qforge.config.SimulationBackend;
import com.mechalikh.qforge.config.SimulationConfig;
import com.mechalikh.qforge.core.QuantumSimulator;
import com.mechalikh.qforge.core.SimulationResult; 
import com.mechalikh.qforge.state.QuantumState;
import com.mechalikh.qforge.state.Qubit;
import example.algorithms.GroversAlgorithm;
import com.mechalikh.qforge.operations.SingleQubitGate;
import com.mechalikh.qforge.operations.MultiQubitGate;
import com.mechalikh.qforge.operations.QuantumGate;
import com.mechalikh.qforge.events.QuantumEvent;
import com.mechalikh.qforge.math.Complex;
import com.mechalikh.qforge.math.ComplexMatrix;
import com.mechalikh.qforge.measurement.MeasurementOperator;
import com.mechalikh.qforge.measurement.MeasurementResult;
import com.mechalikh.qforge.noise.DepolarizingNoise;
import com.mechalikh.qforge.noise.NoiseProfile;
import com.mechalikh.qforge.observer.MetricsCollector;
import com.mechalikh.qforge.events.EventPriority;
import com.mechalikh.qforge.events.EventType;

import java.time.Duration;
import java.time.Instant; 

/**
 * Introductory demonstration of single-qubit gates, a Bell state, Grover's search, the Quantum
 * Fourier Transform, and a three-qubit bit-flip correction cycle.
 *
 * <p>The program prints intermediate states, probabilities, a metrics report for the transform,
 * and {@code [OK]} or {@code [FAIL]} lines for the checks it performs. Exceptions raised by the
 * individual demonstrations are caught by {@link #main(String[])} and printed with a stack trace;
 * no files are written.</p>
 */
public class QuantumSimulationApp {
    /**
     * Runs the five demonstration methods in sequence.
     *
     * <p>No command line arguments are read. If a demonstration throws, the remaining
     * demonstrations are skipped and the error is printed.</p>
     *
     * @param args ignored
     */
    public static void main(String[] args) {
        System.out.println("=== Quantum Simulation Framework ===");
        
        try {
            // Example 1: Basic single-qubit simulation
            runSingleQubitExample();
            
            // Example 2: Entanglement demonstration
            runEntanglementExample();
            
            // Example 3: Grover's search algorithm
            runGroversSearchExample();
            
            // Example 4: Quantum Fourier Transform
            runQuantumFourierTransformExample();
            
            // Example 5: Error correction demonstration
            runErrorCorrectionExample();
            
        } catch (Exception e) {
            System.err.println("Simulation failed: " + e.getMessage());
            e.printStackTrace();
        }
    }
    private static void runSingleQubitExample() throws Exception {
        System.out.println("\n=== Example 1: Single Qubit Operations ===");
        
        // 1. Create configuration
        SimulationConfig config = new SimulationConfig();
        config.setNumQubits(1);
        config.setBackend(SimulationBackend.STATE_VECTOR);
        
        // 2. Create simulator
        QuantumSimulator simulator = new QuantumSimulator();
        simulator.initialize(config);
        
        // 3. Create qubit
        Qubit q0 = new Qubit(0, "q0");
        
        // 4. Get the initial state
        System.out.println("Initial state: |0>");
        
        // 5. Apply Hadamard
        System.out.println("Applying Hadamard gate...");
        simulator.scheduleEvent(new QuantumEvent.Builder("H")
            .withScheduledTime(Instant.now())
            .withOperation(SingleQubitGate.createHadamard(q0))
            .withTargetQubits(q0)
            .build());
        
        // 6. Apply X gate
        System.out.println("Applying X (Pauli-X) gate...");
        simulator.scheduleEvent(new QuantumEvent.Builder("X")
            .withScheduledTime(Instant.now().plusMillis(1))
            .withOperation(SingleQubitGate.createPauliX(q0))
            .withTargetQubits(q0)
            .build());
        
        // 7. Run step by step to debug
        System.out.println("Running simulation step by step...");
        simulator.step(); // Execute H
        QuantumState afterH = simulator.getContext().getCurrentState();
        System.out.println("After H: " + afterH);
        
        simulator.step(); // Execute X
        SimulationResult result = simulator.run();
        
        // 8. Verify
        System.out.println("\nVerification:");
        System.out.println("After H then X, should still be equal superposition");
        System.out.println("Expected amplitudes: (0.707, 0.707) or (0.707, -0.707)");
        System.out.println("Actual: " + result.getFinalState());
        
        double[] probs = result.getFinalState().getProbabilities();
        System.out.printf(Locale.ROOT, "Probability |0>: %.4f\n", probs[0]);
        System.out.printf(Locale.ROOT, "Probability |1>: %.4f\n", probs[1]);
        
        // Check if probabilities sum to 1
        double total = probs[0] + probs[1];
        System.out.printf(Locale.ROOT, "Total probability: %.6f (should be 1.0)\n", total);
        
        simulator.reset();
    }
    
    private static void runEntanglementExample() throws Exception {
        System.out.println("\n=== Example 2: Bell State (Entanglement) ===");
        
        // 1. Create configuration for 2 qubits
        SimulationConfig config = new SimulationConfig();
        config.setNumQubits(2);
        config.setBackend(SimulationBackend.STATE_VECTOR);
        
        // 2. Create simulator
        QuantumSimulator simulator = new QuantumSimulator();
        simulator.initialize(config);
        
        // 3. Create qubits
        Qubit q0 = new Qubit(0, "q0");
        Qubit q1 = new Qubit(1, "q1");
        
        // 4. Schedule Bell state circuit: |00> -> (|00> + |11>)/√2
        // Step 1: Apply Hadamard to q0
        QuantumEvent h0 = new QuantumEvent.Builder("hadamard_q0")
            .withScheduledTime(Instant.now())
            .withOperation(SingleQubitGate.createHadamard(q0))
            .withTargetQubits(q0)
            .withDescription("Apply Hadamard to qubit 0")
            .build();
        
        // Step 2: Apply CNOT with q0 control, q1 target
        // IMPORTANT: For CNOT, both qubits are involved in the operation
        QuantumEvent cnot = new QuantumEvent.Builder("cnot_q0_q1")
            .withScheduledTime(Instant.now().plusSeconds(1))
            .withOperation(MultiQubitGate.createCNOT(q0, q1))
            .withTargetQubits(q0, q1)  // Both qubits are targets of the 2-qubit gate
            .withDescription("Apply CNOT with control q0, target q1")
            .build();
        
        simulator.scheduleEvent(h0);
        simulator.scheduleEvent(cnot);
        
        // 5. Run simulation
        SimulationResult result = simulator.run();
        
        // 6. Analyze results
        System.out.println("Bell State Created:");
        System.out.println("Final State: " + result.getFinalState());
        
        // Check probabilities
        if (result.getFinalState() != null) {
            double[] probs = result.getFinalState().getProbabilities();
            System.out.println("Probabilities:");
            System.out.println("  |00>: " + probs[0]);
            System.out.println("  |01>: " + probs[1]);
            System.out.println("  |10>: " + probs[2]);
            System.out.println("  |11>: " + probs[3]);
            
            // Verify entanglement: |00> and |11> should have 0.5 probability each
            if (Math.abs(probs[0] - 0.5) < 0.01 && Math.abs(probs[3] - 0.5) < 0.01 &&
                Math.abs(probs[1]) < 0.01 && Math.abs(probs[2]) < 0.01) {
                System.out.println("[OK] Successfully created Bell state (|00> + |11>)/sqrt2");
            } else {
                System.out.println("[FAIL] Failed to create proper Bell state");
            }
        }
        
        simulator.reset();
    }
    
    private static void runQuantumFourierTransformExample() throws Exception {
        System.out.println("\n=== Example 4: Quantum Fourier Transform (3 qubits) ===");
        System.out.println("Applying QFT to |000> state");
        System.out.println("QFT creates superposition with specific phase relationships");
        
        // 1. Create configuration for 3 qubits
        SimulationConfig config = new SimulationConfig();
        config.setNumQubits(3);
        config.setBackend(SimulationBackend.STATE_VECTOR);
        config.setMaxSteps(100);
        config.setTimeoutSeconds(30.0);
        
        // 2. Create simulator
        QuantumSimulator simulator = new QuantumSimulator();
        
        // 3. Add observer for metrics
        MetricsCollector metrics = new MetricsCollector();
        simulator.addObserver(metrics);
        
        simulator.initialize(config);
        
        // 4. Create qubits
        Qubit q0 = new Qubit(0, "q0");
        Qubit q1 = new Qubit(1, "q1");
        Qubit q2 = new Qubit(2, "q2");
        
        // 5. Schedule Quantum Fourier Transform circuit
        Instant currentTime = Instant.now();
        long timeStep = 100; // milliseconds between gates
        
        // QFT algorithm for 3 qubits:
        // Apply Hadamard to each qubit, followed by controlled phase rotations
        
        // Apply Hadamard to q0
        QuantumEvent h0 = new QuantumEvent(
            "h0",
            currentTime,
            EventPriority.NORMAL,
            EventType.GATE_OPERATION,
            SingleQubitGate.createHadamard(q0),
            new Qubit[]{q0},
            "QFT: Hadamard on qubit 0"
        );
        currentTime = currentTime.plus(Duration.ofMillis(timeStep));
        
        // Apply controlled phase R2 from q1 to q0
        double angle1 = Math.PI / 2.0; // π/2
        QuantumEvent cp1 = new QuantumEvent(
            "cp1",
            currentTime,
            EventPriority.NORMAL,
            EventType.GATE_OPERATION,
            MultiQubitGate.createCPHASE(angle1, q1, q0),
            new Qubit[]{q0},
            "QFT: Controlled phase R2 (pi/2) from q1 to q0"
        );
        currentTime = currentTime.plus(Duration.ofMillis(timeStep));
        
        // Apply controlled phase R3 from q2 to q0
        double angle2 = Math.PI / 4.0; // π/4
        QuantumEvent cp2 = new QuantumEvent(
            "cp2",
            currentTime,
            EventPriority.NORMAL,
            EventType.GATE_OPERATION,
            MultiQubitGate.createCPHASE(angle2, q2, q0),
            new Qubit[]{q0},
            "QFT: Controlled phase R3 (pi/4) from q2 to q0"
        );
        currentTime = currentTime.plus(Duration.ofMillis(timeStep));
        
        // Apply Hadamard to q1
        QuantumEvent h1 = new QuantumEvent(
            "h1",
            currentTime,
            EventPriority.NORMAL,
            EventType.GATE_OPERATION,
            SingleQubitGate.createHadamard(q1),
            new Qubit[]{q1},
            "QFT: Hadamard on qubit 1"
        );
        currentTime = currentTime.plus(Duration.ofMillis(timeStep));
        
        // Apply controlled phase R2 from q2 to q1
        QuantumEvent cp3 = new QuantumEvent(
            "cp3",
            currentTime,
            EventPriority.NORMAL,
            EventType.GATE_OPERATION,
            MultiQubitGate.createCPHASE(angle1, q2, q1),
            new Qubit[]{q1},
            "QFT: Controlled phase R2 (pi/2) from q2 to q1"
        );
        currentTime = currentTime.plus(Duration.ofMillis(timeStep));
        
        // Apply Hadamard to q2
        QuantumEvent h2 = new QuantumEvent(
            "h2",
            currentTime,
            EventPriority.NORMAL,
            EventType.GATE_OPERATION,
            SingleQubitGate.createHadamard(q2),
            new Qubit[]{q2},
            "QFT: Hadamard on qubit 2"
        );
        currentTime = currentTime.plus(Duration.ofMillis(timeStep));
        
        // Swap qubits to complete QFT (q0 ↔ q2)
        QuantumEvent swap1 = new QuantumEvent(
            "swap1",
            currentTime,
            EventPriority.NORMAL,
            EventType.GATE_OPERATION,
            MultiQubitGate.createSWAP(q0, q2),
            new Qubit[]{q0, q2},
            "QFT: Swap qubit 0 and qubit 2"
        );
        
        // 6. Schedule all events
        simulator.scheduleEvent(h0);
        simulator.scheduleEvent(cp1);
        simulator.scheduleEvent(cp2);
        simulator.scheduleEvent(h1);
        simulator.scheduleEvent(cp3);
        simulator.scheduleEvent(h2);
        simulator.scheduleEvent(swap1);
        
        // 7. Run simulation
        System.out.println("Running Quantum Fourier Transform simulation...");
        SimulationResult result = simulator.run();
        
        // 8. Print results
        System.out.println("\nQuantum Fourier Transform Result:");
        System.out.println("Final State: " + result.getFinalState());
        
        // Print probabilities
        if (result.getFinalState() != null) {
            double[] probabilities = result.getFinalState().getProbabilities();
            System.out.println("\nMeasurement Probabilities:");
            
            // QFT of |000> should create equal superposition of all states
            // with specific phase relationships
            double expectedUniform = 1.0 / probabilities.length; // 1/8 = 0.125
            
            for (int i = 0; i < probabilities.length; i++) {
                String binary = String.format(Locale.ROOT, "%3s", Integer.toBinaryString(i)).replace(' ', '0');
                System.out.printf(Locale.ROOT, "  |%s>: %.6f", binary, probabilities[i]);
                
                // Check if probability is close to uniform
                double error = Math.abs(probabilities[i] - expectedUniform);
                if (error < 0.001) {
                    System.out.println(" [OK] (uniform)");
                } else {
                    System.out.println();
                }
            }
            
            System.out.println("\nExpected: QFT(|000>) creates equal superposition of all 8 basis states");
            System.out.println("Each state should have probability ~ " + String.format(Locale.ROOT, "%.6f", expectedUniform));
            
            // Check if we have a uniform distribution
            boolean isUniform = true;
            double maxError = 0.0;
            for (int i = 0; i < probabilities.length; i++) {
                double error = Math.abs(probabilities[i] - expectedUniform);
                maxError = Math.max(maxError, error);
                if (error > 0.01) { // 1% tolerance
                    isUniform = false;
                }
            }
            
            if (isUniform) {
                System.out.println("\n[OK] SUCCESS: QFT created uniform superposition!");
                System.out.println("  Maximum error from uniform: " + String.format(Locale.ROOT, "%.6f", maxError));
            } else {
                System.out.println("\n[FAIL] Distribution not perfectly uniform");
                System.out.println("  Maximum error from uniform: " + String.format(Locale.ROOT, "%.6f", maxError));
                System.out.println("  This could be due to phase differences (QFT has specific phases)");
            }
        }
        
        // Print metrics
        System.out.println("\nMetrics Report:");
        System.out.println(metrics.generateReport().toSummaryString());
        
        simulator.reset();
        System.out.println("\n" + "=".repeat(70));
    }
    
    private static void runErrorCorrectionExample() throws Exception {
        System.out.println("\n=== Example 5: Bit Flip Error Correction (3 qubits) ===");
        System.out.println("Demonstrating basic error correction using 3-qubit code");
        System.out.println("Encode |0> as |000>, |1> as |111>");
        System.out.println("Correct single bit-flip errors");
        
        // 1. Create configuration for 3 qubits
        SimulationConfig config = new SimulationConfig();
        config.setNumQubits(3);
        config.setBackend(SimulationBackend.STATE_VECTOR);
        
        // 2. Create simulator
        QuantumSimulator simulator = new QuantumSimulator();
        simulator.initialize(config);
        
        // 3. Create qubits
        Qubit q0 = new Qubit(0, "q0");
        Qubit q1 = new Qubit(1, "q1");
        Qubit q2 = new Qubit(2, "q2");
        
        Instant currentTime = Instant.now();
        
        // ============================================
        // STEP 1: Encode |0> into 3-qubit code |000>
        // ============================================
        System.out.println("\nStep 1: Encoding |0> into 3-qubit code |000>");
        
        // Encoding circuit for |0> -> |000>:
        // 1. Apply CNOT with q0 control, q1 target
        // 2. Apply CNOT with q0 control, q2 target
        
        // First CNOT: q0 control, q1 target
        // Create a custom gate to avoid validation issues
        ComplexMatrix cnotMatrix = new ComplexMatrix(4, 4);
        cnotMatrix.set(0, 0, Complex.ONE);
        cnotMatrix.set(1, 1, Complex.ONE);
        cnotMatrix.set(2, 3, Complex.ONE);
        cnotMatrix.set(3, 2, Complex.ONE);
        
        // Create CNOT operation with proper target specification
        QuantumGate cnotGate1 = new QuantumGate.CustomGate(
            "CNOT_q0_q1",
            cnotMatrix,
            new Qubit[]{q0, q1},  // Both qubits are involved
            new Qubit[0],         // No separate control qubits
            1.0
        );
        
        QuantumEvent encode1 = new QuantumEvent.Builder("encode_cnot1")
            .withScheduledTime(currentTime)
            .withOperation(cnotGate1)
            .withTargetQubits(q0, q1)  // Both qubits are targets
            .withDescription("Encode: CNOT q0 -> q1")
            .build();
        
        currentTime = currentTime.plusMillis(10);
        
        // Second CNOT: q0 control, q2 target
        QuantumGate cnotGate2 = new QuantumGate.CustomGate(
            "CNOT_q0_q2",
            cnotMatrix,
            new Qubit[]{q0, q2},  // Both qubits are involved
            new Qubit[0],         // No separate control qubits
            1.0
        );
        
        QuantumEvent encode2 = new QuantumEvent.Builder("encode_cnot2")
            .withScheduledTime(currentTime)
            .withOperation(cnotGate2)
            .withTargetQubits(q0, q2)  // Both qubits are targets
            .withDescription("Encode: CNOT q0 -> q2")
            .build();
        
        simulator.scheduleEvent(encode1);
        simulator.scheduleEvent(encode2);
        
        // ============================================
        // STEP 2: Introduce a bit-flip error
        // ============================================
        System.out.println("\nStep 2: Introducing a bit-flip error on qubit q1");
        
        currentTime = currentTime.plusMillis(10);
        QuantumEvent error = new QuantumEvent.Builder("bit_flip_error")
            .withScheduledTime(currentTime)
            .withOperation(SingleQubitGate.createPauliX(q1))
            .withTargetQubits(q1)
            .withDescription("Introduce X error on qubit 1")
            .build();
        
        simulator.scheduleEvent(error);
        
        // ============================================
        // STEP 3: Apply correction
        // ============================================
        System.out.println("\nStep 3: Applying X correction on qubit 1");
        
        currentTime = currentTime.plusMillis(10);
        QuantumEvent correction = new QuantumEvent.Builder("correction")
            .withScheduledTime(currentTime)
            .withOperation(SingleQubitGate.createPauliX(q1))
            .withTargetQubits(q1)
            .withDescription("Apply X correction on qubit 1")
            .build();
        
        simulator.scheduleEvent(correction);
        
        // ============================================
        // STEP 4: Decode back to single qubit
        // ============================================
        System.out.println("\nStep 4: Decoding back to original state");
        
        // Reverse encoding: CNOT(q0->q2), then CNOT(q0->q1)
        
        currentTime = currentTime.plusMillis(10);
        QuantumGate decodeGate1 = new QuantumGate.CustomGate(
            "DECODE_CNOT_q0_q2",
            cnotMatrix,
            new Qubit[]{q0, q2},
            new Qubit[0],
            1.0
        );
        
        QuantumEvent decode1 = new QuantumEvent.Builder("decode_cnot1")
            .withScheduledTime(currentTime)
            .withOperation(decodeGate1)
            .withTargetQubits(q0, q2)
            .withDescription("Decode: CNOT q0 -> q2")
            .build();
        
        currentTime = currentTime.plusMillis(10);
        QuantumGate decodeGate2 = new QuantumGate.CustomGate(
            "DECODE_CNOT_q0_q1",
            cnotMatrix,
            new Qubit[]{q0, q1},
            new Qubit[0],
            1.0
        );
        
        QuantumEvent decode2 = new QuantumEvent.Builder("decode_cnot2")
            .withScheduledTime(currentTime)
            .withOperation(decodeGate2)
            .withTargetQubits(q0, q1)
            .withDescription("Decode: CNOT q0 -> q1")
            .build();
        
        simulator.scheduleEvent(decode1);
        simulator.scheduleEvent(decode2);
        
        // ============================================
        // STEP 5: Run simulation and analyze
        // ============================================
        System.out.println("\nStep 5: Running simulation...");
        
        SimulationResult result = simulator.run();
        
        System.out.println("\nError Correction Results:");
        System.out.println("Final State: " + result.getFinalState());
        
        // Analyze the result
        QuantumState finalState = result.getFinalState();
        if (finalState != null) {
            double[] probs = finalState.getProbabilities();
            
            System.out.println("\nState Probabilities:");
            for (int i = 0; i < probs.length; i++) {
                if (probs[i] > 0.001) {
                    String binary = String.format(Locale.ROOT, "%3s", Integer.toBinaryString(i)).replace(' ', '0');
                    System.out.printf(Locale.ROOT, "  |%s>: %.4f\n", binary, probs[i]);
                }
            }
            
            // Check if we're back in |0> state
            double probZero = 0.0;
            // States where q0 (most significant bit) is 0: |000>, |001>, |010>, |011>
            for (int i = 0; i < 4; i++) {
                probZero += probs[i];
            }
            
            System.out.printf(Locale.ROOT, "\nProbability of logical |0>: %.4f\n", probZero);
            System.out.printf(Locale.ROOT, "Probability of logical |1>: %.4f\n", 1.0 - probZero);
            
            if (probZero > 0.99) {
                System.out.println("\n[OK] SUCCESS: Error correction worked! System returned to |0> state.");
            } else {
                System.out.println("\n[FAIL] FAILURE: Error correction may not have worked correctly.");
            }
        }
        
        simulator.reset();
    }
    
    
    /**
     * Builds and runs a two-qubit Grover search for {@code |11>} and prints the result.
     *
     * <p>The circuit comes from
     * {@link GroversAlgorithm#createGroversCircuit(Qubit[], int)} with the target state
     * {@code 3}. The probabilities of all four basis states are printed, followed by a
     * SUCCESS, PARTIAL, or FAILED verdict based on the probability of {@code |11>}.</p>
     *
     * @throws Exception if the simulation fails
     */
    public static void runGroversSearchExample() throws Exception {
        System.out.println("\n=== Corrected Grover's Search Algorithm ===");
        System.out.println("Searching for |11> in 2-qubit system");
        
        SimulationConfig config = new SimulationConfig();
        config.setNumQubits(2);
        
        QuantumSimulator simulator = new QuantumSimulator();
        simulator.initialize(config);
        
        Qubit q0 = new Qubit(0, "q0");
        Qubit q1 = new Qubit(1, "q1");
        
        // For 2 qubits, optimal number of Grover iterations = floor(π/4 * √N) = 1
        // where N = 4 (2²)
        
        System.out.println("\nBuilding Grover circuit for |11>");
        QuantumEvent[] circuit = GroversAlgorithm.createGroversCircuit(
            new Qubit[]{q0, q1}, 3);
        for (QuantumEvent event : circuit) {
            simulator.scheduleEvent(event);
        }
        
        System.out.println("Step 4: Run simulation");
        SimulationResult result = simulator.run();
        
        System.out.println("\nGrover's Search Results:");
        if (result.getFinalState() != null) {
            double[] probs = result.getFinalState().getProbabilities();
            
            System.out.println("State Probabilities after Grover iteration:");
            System.out.printf(Locale.ROOT, "  |00>: %.4f\n", probs[0]);
            System.out.printf(Locale.ROOT, "  |01>: %.4f\n", probs[1]);
            System.out.printf(Locale.ROOT, "  |10>: %.4f\n", probs[2]);
            System.out.printf(Locale.ROOT, "  |11>: %.4f\n", probs[3]);
            
            double prob11 = probs[3];
            System.out.printf(Locale.ROOT, "\nProbability of finding |11>: %.2f%%\n", prob11 * 100);
            
            if (prob11 > 0.9) {
                System.out.println("[OK] SUCCESS: Grover's algorithm amplified |11>!");
            } else if (prob11 > 0.25) {
                System.out.println("[OK] PARTIAL: Some amplification detected");
                System.out.println("Note: For perfect 2-qubit Grover, should be ~100%");
            } else {
                System.out.println("[FAIL] FAILED: No amplification detected");
                System.out.println("Possible issues:");
                System.out.println("  1. Oracle implementation");
                System.out.println("  2. Diffusion operator");
                System.out.println("  3. Gate ordering/timing");
            }
        }
        
        simulator.reset();
    }
     
}