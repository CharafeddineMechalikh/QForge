package example;

import com.mechalikh.qforge.config.*;
import com.mechalikh.qforge.core.*;
import com.mechalikh.qforge.classical.VariationalQuantumEigensolver;
import com.mechalikh.qforge.math.Complex;
import com.mechalikh.qforge.math.ComplexMatrix;
import com.mechalikh.qforge.measurement.Observable;
import com.mechalikh.qforge.state.Qubit;
import com.mechalikh.qforge.events.QuantumEvent;
import com.mechalikh.qforge.operations.SingleQubitGate;

import example.algorithms.*;

import java.util.*;
import java.time.Instant;

/**
 * Canonical entry point for the algorithm demonstrations in {@code example.algorithms}.
 *
 * <p>Each demonstration builds a simulator, schedules a circuit, runs it, and prints the final
 * state together with a {@code SUCCESS} or {@code FAILED} verdict where the code can check one.
 * The checks compare against values computed by the program itself (for example exact
 * diagonalization for the variational eigensolver and the analytic evolution for the Trotter
 * circuit), and a negative verdict is printed as an honest result rather than hidden.</p>
 *
 * <p>The algorithms are small demonstrations: the variational eigensolver, quantum neural network,
 * kernel classifier, and QAOA examples use 2 to 5 qubits and fixed data sets, and QAOA uses fixed
 * angles that are not optimized.</p>
 */
public class QuantumAlgorithmsDemo {
    
    /**
     * Runs one demonstrated algorithm or all of them.
     *
     * <p>The first argument selects the algorithm, case insensitively: {@code GROVER},
     * {@code QFT}, {@code QPE}, {@code VQE}, {@code QNN}, {@code QSVM}, {@code SHOR_CODE},
     * {@code QAOA}, {@code TROTTER}, {@code QUANTUM_WALK}, or {@code ALL}. With no argument,
     * {@code ALL} is used. Any other value prints an unknown algorithm message and does nothing.
     * Additional arguments are ignored.</p>
     *
     * <p>Output consists of section headers, circuit gate counts, final states, probability
     * tables, and verdict lines on standard output. No files are written.</p>
     *
     * @param args zero or one algorithm name
     * @throws Exception if a demonstration fails while running its simulation
     */
    public static void main(String[] args) throws Exception {
        System.out.println("=== Quantum Algorithms Demonstration ===");
        
        // Run all demos or select a specific one: e.g. "java ... QuantumAlgorithmsDemo QNN"
        String algorithm = args.length > 0 ? args[0].toUpperCase(Locale.ROOT) : "ALL";
        
        switch (algorithm) {
            case "GROVER":
                demoGrover();
                break;
            case "QFT":
                demoQFT();
                break;
            case "QPE":
                demoQPE();
                break;
            case "VQE":
                demoVQE();
                break;
            case "QNN":
                demoQNN();
                break;
            case "QSVM":
                demoQSVM();
                break;
            case "SHOR_CODE":
                demoShorCode();
                break;
            case "QAOA":
                demoQAOA();
                break;
            case "TROTTER":
                demoTrotter();
                break;
            case "QUANTUM_WALK":
                demoQuantumWalk();
                break;
            case "ALL":
                runAllDemos();
                break;
            default:
                System.out.println("Unknown algorithm: " + algorithm);
        }
    }
    
    private static void runAllDemos() throws Exception {
        System.out.println("Running all quantum algorithm demos...\n");
        
        demoGrover();
        demoQFT();
        demoQPE();
        demoVQE();
        demoQNN();
        demoQSVM();
        demoShorCode();
        demoQAOA();
        demoTrotter();
        demoQuantumWalk();
        
        System.out.println("\n=== All demos completed successfully ===");
    }
    
    private static void demoGrover() throws Exception {
        System.out.println("\n1. === Grover's Search Algorithm ===");
        System.out.println("Searching for |111> in 3-qubit space");
        
        SimulationConfig config = new SimulationConfig();
        config.setNumQubits(3);
        config.setBackend(SimulationBackend.STATE_VECTOR);
        
        QuantumSimulator simulator = new QuantumSimulator();
        simulator.initialize(config);
        
        Qubit[] qubits = new Qubit[3];
        for (int i = 0; i < 3; i++) {
            qubits[i] = new Qubit(i, "q" + i);
        }
        QuantumEvent[] circuit = GroversAlgorithm.createGroversCircuit(qubits, 7);
        // Create Grover's circuit for searching |111> (binary 111 = decimal 7)
         
        System.out.println("Circuit has " + circuit.length + " gates");
        
        for (QuantumEvent event : circuit) {
            simulator.scheduleEvent(event);
        }
        
        SimulationResult result = simulator.run();
        
        System.out.println("Final State: " + result.getFinalState());
        
        // Analyze results
        if (result.getFinalState() != null) {
            double[] probs = result.getFinalState().getProbabilities();
            System.out.println("\nMeasurement Probabilities:");
            for (int i = 0; i < probs.length; i++) {
                if (probs[i] > 0.01) {
                    String binary = String.format(Locale.ROOT, "%3s", Integer.toBinaryString(i)).replace(' ', '0');
                    System.out.printf(Locale.ROOT, "  |%s>: %.4f\n", binary, probs[i]);
                }
            }
            
            // Check that |111> is both the maximum and well above uniform
            double targetProb = probs[7]; // |111>
            double maxProb = Arrays.stream(probs).max().orElse(0.0);
            double uniform = 1.0 / probs.length;
            if (targetProb > uniform + 0.1 && Math.abs(targetProb - maxProb) < 1e-9) {
                System.out.printf(Locale.ROOT,
                    "SUCCESS: |111> has the highest probability (%.4f vs uniform %.4f)%n",
                    targetProb, uniform);
            } else {
                System.out.printf(Locale.ROOT,
                    "FAILED: |111> probability %.4f is not the unique maximum%n", targetProb);
            }
        }
        
        simulator.reset();
        System.out.println("=".repeat(60));
    }
    
    private static void demoQFT() throws Exception {
        System.out.println("\n2. === Quantum Fourier Transform ===");
        System.out.println("Applying QFT to 3-qubit |000> state");
        
        SimulationConfig config = new SimulationConfig();
        config.setNumQubits(3);
        config.setBackend(SimulationBackend.STATE_VECTOR);
        
        QuantumSimulator simulator = new QuantumSimulator();
        simulator.initialize(config);
        
        Qubit[] qubits = new Qubit[3];
        for (int i = 0; i < 3; i++) {
            qubits[i] = new Qubit(i, "q" + i);
        }
        
        // Create QFT circuit
        QuantumEvent[] circuit = CircuitFactory.createQuantumFourierTransform(qubits);
        
        System.out.println("Circuit has " + circuit.length + " gates");
        
        for (QuantumEvent event : circuit) {
            simulator.scheduleEvent(event);
        }
        
        SimulationResult result = simulator.run();
        
        System.out.println("Final State after QFT: " + result.getFinalState());
        
        // The QFT of |000> should produce uniform superposition
        if (result.getFinalState() != null) {
            double[] probs = result.getFinalState().getProbabilities();
            double expected = 1.0 / probs.length;
            boolean uniform = true;
            
            System.out.println("\nChecking uniform superposition:");
            for (int i = 0; i < probs.length; i++) {
                if (Math.abs(probs[i] - expected) > 0.01) {
                    uniform = false;
                }
            }
            
            if (uniform) {
                System.out.println("SUCCESS: QFT produced uniform superposition");
            } else {
                System.out.println("FAILED: Not perfectly uniform (numerical precision)");
            }
        }
        
        simulator.reset();
        System.out.println("=".repeat(60));
    }
    
    private static void demoQPE() throws Exception {
        System.out.println("\n3. === Quantum Phase Estimation ===");
        System.out.println("Estimating phase of T gate (phase = pi/4)");
        
        // T gate: |1> -> e^{iπ/4}|1>, so phase = 1/8
        SimulationConfig config = new SimulationConfig();
        config.setNumQubits(4); // 3 estimation qubits + 1 eigenstate qubit
        config.setBackend(SimulationBackend.STATE_VECTOR);
        
        QuantumSimulator simulator = new QuantumSimulator();
        simulator.initialize(config);
        
        // Prepare qubits: first 3 for estimation, last one for eigenstate
        Qubit[] estimationQubits = new Qubit[3];
        Qubit eigenstateQubit = new Qubit(3, "eigen");
        
        for (int i = 0; i < 3; i++) {
            estimationQubits[i] = new Qubit(i, "est" + i);
        }
        
        // Prepare eigenstate |1> for T gate
        simulator.scheduleEvent(
            EventBuilderHelper.createSingleQubitGate("X", 
                SingleQubitGate.createPauliX(eigenstateQubit), eigenstateQubit));
        
        // Create phase estimation circuit
        QuantumEvent[] unitaryCircuit = {
            EventBuilderHelper.createSingleQubitGate("T", 
                SingleQubitGate.createT(eigenstateQubit), eigenstateQubit)
        };
        
        QuantumEvent[] circuit = QuantumPhaseEstimation.createPhaseEstimationCircuit(
            estimationQubits,
            new Qubit[]{eigenstateQubit},
            unitaryCircuit,
            3
        );
        
        System.out.println("Circuit has " + circuit.length + " gates");
        
        for (QuantumEvent event : circuit) {
            simulator.scheduleEvent(event);
        }
        
        SimulationResult result = simulator.run();
        
        System.out.println("Final State: " + result.getFinalState());
        
        // Phase should be approximately 1/8 = 0.125
        if (result.getFinalState() != null) {
            double[] probs = result.getFinalState().getProbabilities();
            int bestOutcome = 0;
            for (int i = 1; i < probs.length; i++) {
                if (probs[i] > probs[bestOutcome]) {
                    bestOutcome = i;
                }
            }
            
            int phaseEstimate = bestOutcome >> 1; // remove the eigenstate qubit
            double phase = phaseEstimate / 8.0;   // 3 bits -> divide by 2^3
            String binaryString = String.format(Locale.ROOT, "%3s",
                Integer.toBinaryString(phaseEstimate)).replace(' ', '0');
            
            System.out.printf(Locale.ROOT,
                "Most likely estimate: %s -> %.3f (probability: %.3f)%n",
                binaryString, phase, probs[bestOutcome]);
            
            if (Math.abs(phase - 0.125) < 1e-9 && probs[bestOutcome] > 0.99) {
                System.out.println("SUCCESS: phase 0.125 recovered exactly with 3 bits");
            } else {
                System.out.println("FAILED: phase estimate is not concentrated at 0.125");
            }
        }
        
        simulator.reset();
        System.out.println("=".repeat(60));
    }
    
    private static void demoVQE() throws Exception {
        System.out.println("\n4. === Variational Quantum Eigensolver ===");
        System.out.println("H2 molecule in a 2-qubit parity-mapped basis");
        
        ComplexMatrix identity2 = ComplexMatrix.identity(2);
        ComplexMatrix z = ComplexMatrix.pauliZ();
        ComplexMatrix x = ComplexMatrix.pauliX();
        
        ComplexMatrix h2 = ComplexMatrix.identity(4).scale(-1.0523);
        h2 = h2.add(z.tensorProduct(identity2).scale(0.3979));
        h2 = h2.add(identity2.tensorProduct(z).scale(0.3979));
        h2 = h2.add(z.tensorProduct(z).scale(-0.0113));
        h2 = h2.add(x.tensorProduct(x).scale(0.1809));
        
        double exactGroundEnergy = h2.eigenvalues()[0];
        System.out.printf(Locale.ROOT, "Exact ground energy: %.6f Hartree%n", exactGroundEnergy);
        
        SimulationConfig config = new SimulationConfig();
        config.setNumQubits(2);
        config.setBackend(SimulationBackend.STATE_VECTOR);
        
        QuantumSimulator simulator = new QuantumSimulator();
        simulator.initialize(config);
        
        Qubit[] qubits = {new Qubit(0, "q0"), new Qubit(1, "q1")};
        Observable hamiltonian = new Observable(
            "H2", h2, qubits, Observable.ObservableType.HAMILTONIAN);
        
        Map<String, Object> vqeParameters = new HashMap<>();
        vqeParameters.put("max_iterations", 300);
        vqeParameters.put("learning_rate", 0.15);
        vqeParameters.put("momentum", 0.5);
        vqeParameters.put("convergence_threshold", 1e-8);
        
        VariationalQuantumEigensolver vqe = new VariationalQuantumEigensolver(
            hamiltonian, Arrays.asList(qubits));
        vqe.initialize(simulator, vqeParameters);
        
        long start = System.currentTimeMillis();
        while (vqe.executeIteration()) {
            // optimize until convergence or the iteration cap
        }
        long elapsed = System.currentTimeMillis() - start;
        
        double energy = vqe.getCurrentCost();
        double error = Math.abs(energy - exactGroundEnergy);
        System.out.printf(Locale.ROOT,
            "VQE energy: %.6f Hartree after %d iterations (%d ms)%n",
            energy, vqe.getIterationCount(), elapsed);
        System.out.printf(Locale.ROOT, "Error vs exact diagonalization: %.2e Hartree%n", error);
        
        SimulationResult result = vqe.getResult();
        System.out.println("Optimized state: " + result.getFinalState());
        
        if (error < 1e-3) {
            System.out.println("SUCCESS: VQE reached the H2 ground state within 1 mHartree");
        } else {
            System.out.println("FAILED: VQE did not reach the ground state (honest negative result)");
        }
        
        simulator.reset();
        System.out.println("=".repeat(60));
    }
    
    private static void demoQNN() throws Exception {
        System.out.println("\n5. === Quantum Neural Network (trained) ===");
        System.out.println("Binary classification with a 2-qubit variational circuit");
        
        SimulationConfig config = new SimulationConfig();
        config.setNumQubits(2);
        config.setBackend(SimulationBackend.STATE_VECTOR);
        
        QuantumSimulator simulator = new QuantumSimulator();
        simulator.initialize(config);
        
        Qubit[] qubits = new Qubit[2];
        for (int i = 0; i < 2; i++) {
            qubits[i] = new Qubit(i, "q" + i);
        }
        
        double[][] inputs = {
            {0.10, 0.20},
            {0.25, 0.80},
            {0.80, 0.25},
            {0.90, 0.85}
        };
        int[] labels = {0, 1, 1, 0};
        int layers = 2;
        
        Map<String, Double> weights = new LinkedHashMap<>();
        Random random = new Random(11);
        for (int layer = 0; layer < layers; layer++) {
            for (int i = 0; i < qubits.length; i++) {
                weights.put("w" + layer + "_" + i + "_rot", (random.nextDouble() - 0.5) * Math.PI);
            }
            weights.put("w" + layer + "_0_1", (random.nextDouble() - 0.5) * Math.PI);
        }
        
        System.out.println("Training samples: " + inputs.length + ", layers: " + layers);
        System.out.printf(Locale.ROOT, "Initial accuracy: %.1f%%%n",
            100.0 * qnnAccuracy(simulator, qubits, inputs, labels, weights, layers));
        
        int epochs = 60;
        double learningRate = 0.4;
        double epsilon = 0.1;
        double finalLoss = 0.0;
        
        for (int epoch = 0; epoch < epochs; epoch++) {
            Map<String, Double> gradient = new LinkedHashMap<>();
            for (String key : weights.keySet()) {
                gradient.put(key, 0.0);
            }
            
            double loss = 0.0;
            for (int sample = 0; sample < inputs.length; sample++) {
                double prediction = qnnPredict(
                    simulator, qubits, inputs[sample], weights, layers);
                double error = prediction - labels[sample];
                loss += error * error;
                
                for (String key : weights.keySet()) {
                    double original = weights.get(key);
                    
                    weights.put(key, original + epsilon);
                    double forward = qnnPredict(simulator, qubits, inputs[sample], weights, layers);
                    
                    weights.put(key, original - epsilon);
                    double backward = qnnPredict(simulator, qubits, inputs[sample], weights, layers);
                    
                    weights.put(key, original);
                    gradient.put(key, gradient.get(key)
                        + 2.0 * error * (forward - backward) / (2.0 * epsilon));
                }
            }
            
            for (String key : weights.keySet()) {
                weights.put(key, weights.get(key)
                    - learningRate * gradient.get(key) / inputs.length);
            }
            finalLoss = loss / inputs.length;
            
            if (epoch % 10 == 0 || epoch == epochs - 1) {
                System.out.printf(Locale.ROOT, "Epoch %2d: loss=%.6f, accuracy=%.1f%%%n",
                    epoch, finalLoss,
                    100.0 * qnnAccuracy(simulator, qubits, inputs, labels, weights, layers));
            }
        }
        
        double accuracy = qnnAccuracy(simulator, qubits, inputs, labels, weights, layers);
        System.out.printf(Locale.ROOT, "%nFinal accuracy: %.1f%%%n", 100.0 * accuracy);
        System.out.println(accuracy == 1.0
            ? "SUCCESS: the trained QNN classifies all training samples correctly"
            : "FAILED: Training stopped below 100% accuracy (honest negative result)");
        
        simulator.reset();
        System.out.println("=".repeat(60));
    }
    
    private static double qnnPredict(
        QuantumSimulator simulator, Qubit[] qubits, double[] input,
        Map<String, Double> weights, int layers) throws Exception {
        QuantumEvent[] circuit = QuantumNeuralNetwork.createQNNCircuit(
            qubits, input, weights, layers);
        double[] probabilities = runProbabilities(simulator, circuit);
        return probabilities[2] + probabilities[3];
    }
    
    private static double qnnAccuracy(
        QuantumSimulator simulator, Qubit[] qubits, double[][] inputs,
        int[] labels, Map<String, Double> weights, int layers) throws Exception {
        int correct = 0;
        for (int i = 0; i < inputs.length; i++) {
            double prediction = qnnPredict(simulator, qubits, inputs[i], weights, layers);
            int predictedLabel = prediction >= 0.5 ? 1 : 0;
            if (predictedLabel == labels[i]) {
                correct++;
            }
        }
        return correct / (double) inputs.length;
    }
    
    private static void demoQSVM() throws Exception {
        System.out.println("\n6. === Quantum Support Vector Machine ===");
        System.out.println("ZZ feature map + swap-test kernel + kernel perceptron");
        
        SimulationConfig config = new SimulationConfig();
        config.setNumQubits(5); // two 2-qubit registers plus one ancilla
        config.setBackend(SimulationBackend.STATE_VECTOR);
        
        QuantumSimulator simulator = new QuantumSimulator();
        simulator.initialize(config);
        
        double[][] trainingData = {
            {0.10, 0.50},
            {0.20, 0.50},
            {0.80, 0.50},
            {0.90, 0.50}
        };
        int[] trainingLabels = {-1, -1, 1, 1};
        
        double[][] testData = {
            {0.15, 0.50},
            {0.25, 0.50},
            {0.75, 0.50},
            {0.85, 0.50}
        };
        int[] testLabels = {-1, -1, 1, 1};
        
        double[][] trainingKernel = new double[trainingData.length][trainingData.length];
        for (int i = 0; i < trainingData.length; i++) {
            for (int j = 0; j < trainingData.length; j++) {
                trainingKernel[i][j] = qsvmKernel(simulator, trainingData[i], trainingData[j]);
            }
        }
        
        System.out.println("Training kernel matrix (swap-test estimates):");
        for (double[] row : trainingKernel) {
            System.out.print("  ");
            for (double value : row) {
                System.out.printf(Locale.ROOT, "%7.4f ", value);
            }
            System.out.println();
        }
        
        double[] alpha = new double[trainingData.length];
        int trainingEpochs = 0;
        for (; trainingEpochs < 50; trainingEpochs++) {
            boolean changed = false;
            for (int i = 0; i < trainingData.length; i++) {
                double score = 0.0;
                for (int j = 0; j < trainingData.length; j++) {
                    score += alpha[j] * trainingLabels[j] * trainingKernel[j][i];
                }
                if (trainingLabels[i] * score <= 0.0) {
                    alpha[i] += 1.0;
                    changed = true;
                }
            }
            if (!changed) {
                break;
            }
        }
        
        int trainingCorrect = 0;
        for (int i = 0; i < trainingData.length; i++) {
            double score = 0.0;
            for (int j = 0; j < trainingData.length; j++) {
                score += alpha[j] * trainingLabels[j] * trainingKernel[j][i];
            }
            if (Integer.signum((int) Math.signum(score)) == trainingLabels[i]) {
                trainingCorrect++;
            }        }
        
        int testCorrect = 0;
        System.out.println("\nTest predictions:");
        for (int i = 0; i < testData.length; i++) {
            double score = 0.0;
            for (int j = 0; j < trainingData.length; j++) {
                score += alpha[j] * trainingLabels[j]
                    * qsvmKernel(simulator, testData[i], trainingData[j]);
            }
            int predicted = score >= 0.0 ? 1 : -1;
            if (predicted == testLabels[i]) {
                testCorrect++;
            }
            System.out.printf(Locale.ROOT,
                "  x=[%.2f, %.2f] score=%+.4f predicted=%+d expected=%+d%n",
                testData[i][0], testData[i][1], score, predicted, testLabels[i]);
        }
        
        double trainingAccuracy = trainingCorrect / (double) trainingData.length;
        double testAccuracy = testCorrect / (double) testData.length;
        System.out.printf(Locale.ROOT,
            "%nKernel perceptron epochs: %d, training accuracy: %.1f%%, test accuracy: %.1f%%%n",
            trainingEpochs, 100.0 * trainingAccuracy, 100.0 * testAccuracy);
        System.out.println(trainingAccuracy == 1.0 && testAccuracy >= 0.75
            ? "SUCCESS: quantum kernel classifier separates the training data and generalizes"
            : "FAILED: Kernel classifier did not reach the expected accuracy (honest negative result)");
        
        simulator.reset();
        System.out.println("=".repeat(60));
    }
    
    private static double qsvmKernel(QuantumSimulator simulator, double[] first, double[] second)
        throws Exception {
        Qubit[] registerOne = {new Qubit(0, "a0"), new Qubit(1, "a1")};
        Qubit[] registerTwo = {new Qubit(2, "b0"), new Qubit(3, "b1")};
        QuantumEvent[] circuit = QuantumSVM.createQSVMKernelCircuit(
            registerOne, registerTwo, first, second, "ZZFeatureMap");
        double[] probabilities = runProbabilities(simulator, circuit);
        
        double ancillaZero = 0.0;
        for (int index = 0; index < probabilities.length; index++) {
            if ((index & 1) == 0) { // ancilla is the final (least significant) qubit
                ancillaZero += probabilities[index];
            }
        }
        return 2.0 * ancillaZero - 1.0;
    }
    
    private static double[] runProbabilities(QuantumSimulator simulator, QuantumEvent[] circuit)
        throws Exception {
        simulator.reset();
        Instant time = Instant.now();
        int index = 0;
        for (QuantumEvent event : circuit) {
            QuantumEvent scheduled = new QuantumEvent.Builder("demo_" + (index++))
                .withScheduledTime(time)
                .withOperation(event.getOperation())
                .withTargetQubits(event.getTargetQubits())
                .build();
            simulator.scheduleEvent(scheduled);
            time = time.plusMillis(1);
        }
        return simulator.run().getFinalState().getProbabilities();
    }
    
    private static ComplexMatrix expMinusIHt(ComplexMatrix hamiltonian, double time) {
        ComplexMatrix[] eigen = hamiltonian.eigenDecomposition();
        ComplexMatrix eigenvalues = eigen[0];
        ComplexMatrix eigenvectors = eigen[1];
        int dimension = eigenvalues.rows();
        ComplexMatrix phases = new ComplexMatrix(dimension, dimension);
        for (int i = 0; i < dimension; i++) {
            double energy = eigenvalues.get(i, i).real();
            phases.set(i, i, new Complex(Math.cos(-energy * time), Math.sin(-energy * time)));
        }
        return eigenvectors.multiply(phases).multiply(eigenvectors.adjoint());
    }
    
    private static void demoShorCode() throws Exception {
        System.out.println("\n7. === Shor's 9-Qubit Error Correction Code ===");
        System.out.println("Encoding logical |0> state");
        
        SimulationConfig config = new SimulationConfig();
        config.setNumQubits(9);
        config.setBackend(SimulationBackend.STATE_VECTOR);
        
        QuantumSimulator simulator = new QuantumSimulator();
        simulator.initialize(config);
        
        Qubit[] qubits = new Qubit[9];
        for (int i = 0; i < 9; i++) {
            qubits[i] = new Qubit(i, "q" + i);
        }
        
        // Create Shor code encoding circuit
        QuantumEvent[] circuit = QuantumErrorCorrection.createShorCode(qubits);
        
        System.out.println("Circuit has " + circuit.length + " gates");
        
        for (QuantumEvent event : circuit) {
            simulator.scheduleEvent(event);
        }
        
        SimulationResult result = simulator.run();
        
        System.out.println("Encoded State: " + result.getFinalState());
        
        // Shor code encodes 1 logical qubit into 9 physical qubits:
        // |0>_L = (|000> + |111>)^x3 / sqrt(8)
        if (result.getFinalState() != null) {
            double[] probs = result.getFinalState().getProbabilities();
            boolean[] expected = new boolean[probs.length];
            for (int blockMask = 0; blockMask < 8; blockMask++) {
                int index = 0;
                for (int block = 0; block < 3; block++) {
                    int bit = (blockMask >> block) & 1;
                    for (int i = 0; i < 3; i++) {
                        if (bit == 1) {
                            index |= 1 << (8 - (3 * block + i));
                        }
                    }
                }
                expected[index] = true;
            }
            
            double expectedMass = 0.0;
            double outsideMass = 0.0;
            for (int i = 0; i < probs.length; i++) {
                if (expected[i]) {
                    expectedMass += probs[i];
                } else {
                    outsideMass += probs[i];
                }
            }
            
            System.out.printf(Locale.ROOT,
                "Probability mass on the 8 logical codewords: %.6f (outside: %.2e)%n",
                expectedMass, outsideMass);
            
            if (expectedMass > 0.999 && outsideMass < 1e-9) {
                System.out.println("SUCCESS: Shor code produced the logical |0> encoding");
            } else {
                System.out.println("FAILED: encoded state does not match the Shor codewords");
            }
        }
        
        simulator.reset();
        System.out.println("=".repeat(60));
    }
    
    private static void demoQAOA() throws Exception {
        System.out.println("\n8. === Quantum Approximate Optimization Algorithm ===");
        System.out.println("Solving MaxCut on 3-node triangle graph");
        
        SimulationConfig config = new SimulationConfig();
        config.setNumQubits(3); // One qubit per node
        config.setBackend(SimulationBackend.STATE_VECTOR);
        
        QuantumSimulator simulator = new QuantumSimulator();
        simulator.initialize(config);
        
        Qubit[] qubits = new Qubit[3];
        for (int i = 0; i < 3; i++) {
            qubits[i] = new Qubit(i, "q" + i);
        }
        
        // Define MaxCut problem on triangle graph
        List<QAOAAlgorithm.Edge> edges = Arrays.asList(
            new QAOAAlgorithm.Edge(0, 1),
            new QAOAAlgorithm.Edge(1, 2),
            new QAOAAlgorithm.Edge(2, 0)
        );
        
        QAOAAlgorithm.CostHamiltonian cost = new QAOAAlgorithm.CostHamiltonian(edges);
        
        // QAOA parameters (angles)
        double[] gamma = {0.5}; // Problem angles
        double[] beta = {0.3};  // Mixer angles
        int p = gamma.length;   // Number of layers
        
        // Create QAOA circuit
        QuantumEvent[] circuit = QAOAAlgorithm.createQAOACircuit(
            qubits, gamma, beta, cost);
        
        System.out.println("Circuit has " + circuit.length + " gates");
        System.out.println("Graph: Triangle (3 nodes, 3 edges)");
        System.out.println("QAOA parameters: p=" + p + ", gamma=" + gamma[0] + ", beta=" + beta[0]);
        
        for (QuantumEvent event : circuit) {
            simulator.scheduleEvent(event);
        }
        
        SimulationResult result = simulator.run();
        
        System.out.println("QAOA State: " + result.getFinalState());
        
        // For MaxCut, we want bipartition of graph
        // Valid cuts: |010>, |101>, |001>, |110>, etc.
        if (result.getFinalState() != null) {
            double[] probs = result.getFinalState().getProbabilities();
            
            System.out.println("\nMost probable cuts (bipartitions):");
            double expectedCut = 0.0;
            for (int i = 0; i < probs.length; i++) {
                String binary = String.format(Locale.ROOT, "%3s", Integer.toBinaryString(i)).replace(' ', '0');
                int cutSize = calculateCutSize(binary, edges);
                expectedCut += probs[i] * cutSize;
                if (probs[i] > 0.1) {
                    System.out.printf(Locale.ROOT, "  |%s>: %.4f (cut size: %d)%n",
                        binary, probs[i], cutSize);
                }
            }
            
            System.out.printf(Locale.ROOT,
                "Expected cut size: %.4f (maximum for a triangle: 2)%n", expectedCut);
            if (expectedCut > 1.9) {
                System.out.println("SUCCESS: QAOA state is concentrated on maximum cuts");
            } else {
                System.out.println(
                    "NOTE: fixed angles are not optimized; the state samples several cut sizes");
            }
        }
        
        simulator.reset();
        System.out.println("=".repeat(60));
    }
    
    private static int calculateCutSize(String assignment, List<QAOAAlgorithm.Edge> edges) {
        int cut = 0;
        for (QAOAAlgorithm.Edge edge : edges) {
            char bit1 = assignment.charAt(edge.from);
            char bit2 = assignment.charAt(edge.to);
            if (bit1 != bit2) {
                cut++;
            }
        }
        return cut;
    }
    
    private static void demoTrotter() throws Exception {
        System.out.println("\n9. === Trotter-Suzuki Hamiltonian Simulation ===");
        System.out.println("Simulating Heisenberg model on 2 spins");
        
        SimulationConfig config = new SimulationConfig();
        config.setNumQubits(2);
        config.setBackend(SimulationBackend.STATE_VECTOR);
        
        QuantumSimulator simulator = new QuantumSimulator();
        simulator.initialize(config);
        
        Qubit[] qubits = new Qubit[2];
        for (int i = 0; i < 2; i++) {
            qubits[i] = new Qubit(i, "q" + i);
        }
        
        // Initialize to |01> state
        simulator.scheduleEvent(
            EventBuilderHelper.createSingleQubitGate("X1", 
                SingleQubitGate.createPauliX(qubits[1]), qubits[1]));
        
        // Define Heisenberg Hamiltonian: H = J(XxX + YxY + ZxZ)
        List<TrotterSimulation.HamiltonianTerm> terms = Arrays.asList(
            new TrotterSimulation.HamiltonianTerm("XX", new int[]{0, 1}, 1.0),
            new TrotterSimulation.HamiltonianTerm("YY", new int[]{0, 1}, 1.0),
            new TrotterSimulation.HamiltonianTerm("ZZ", new int[]{0, 1}, 1.0)
        );
        
        TrotterSimulation.Hamiltonian hamiltonian = new TrotterSimulation.Hamiltonian(terms);
        
        // Create Trotter circuit
        double time = 0.5; // Evolution time
        int trotterSteps = 4; // Number of Trotter steps
        
        QuantumEvent[] circuit = TrotterSimulation.createTrotterCircuit(
            qubits, hamiltonian, time, trotterSteps);
        
        System.out.println("Circuit has " + circuit.length + " gates");
        System.out.println("Hamiltonian: Heisenberg (XX + YY + ZZ)");
        System.out.println("Evolution time: " + time + ", Trotter steps: " + trotterSteps);
        
        for (QuantumEvent event : circuit) {
            simulator.scheduleEvent(event);
        }
        
        SimulationResult result = simulator.run();
        
        System.out.println("Evolved State: " + result.getFinalState());
        
        // For Heisenberg model, |01> should oscillate to |10>
        if (result.getFinalState() != null) {
            double[] probs = result.getFinalState().getProbabilities();
            
            System.out.println("\nState probabilities:");
            for (int i = 0; i < probs.length; i++) {
                if (probs[i] > 0.01) {
                    String binary = String.format(Locale.ROOT, "%2s", Integer.toBinaryString(i)).replace(' ', '0');
                    System.out.printf(Locale.ROOT, "  |%s>: %.4f\n", binary, probs[i]);
                }
            }
            
            double prob01 = probs[1]; // |01>
            double prob10 = probs[2]; // |10>
            System.out.printf(Locale.ROOT, "Initial |01> probability: %.4f%n", prob01);
            System.out.printf(Locale.ROOT, "Swapped |10> probability: %.4f%n", prob10);
            
            ComplexMatrix xx = ComplexMatrix.pauliX().tensorProduct(ComplexMatrix.pauliX());
            ComplexMatrix yy = ComplexMatrix.pauliY().tensorProduct(ComplexMatrix.pauliY());
            ComplexMatrix zz = ComplexMatrix.pauliZ().tensorProduct(ComplexMatrix.pauliZ());
            ComplexMatrix exactUnitary = expMinusIHt(xx.add(yy).add(zz), time);
            Complex[] initial = {Complex.ZERO, Complex.ONE, Complex.ZERO, Complex.ZERO};
            Complex overlap = Complex.ZERO;
            for (int row = 0; row < 4; row++) {
                Complex exactAmplitude = Complex.ZERO;
                for (int col = 0; col < 4; col++) {
                    exactAmplitude = exactAmplitude.add(
                        exactUnitary.get(row, col).multiply(initial[col]));
                }
                overlap = overlap.add(exactAmplitude.conjugate()
                    .multiply(result.getFinalState().getAmplitude(row)));
            }
            double fidelity = overlap.absSquare();
            System.out.printf(Locale.ROOT,
                "Trotter fidelity vs exact e^{-iHt}: %.8f%n", fidelity);
            System.out.println(fidelity > 0.99
                ? "SUCCESS: Trotter evolution matches the exact Heisenberg evolution"
                : "FAILED: Trotter error is too large (honest negative result)");
        }
        
        simulator.reset();
        System.out.println("=".repeat(60));
    }
    
    private static void demoQuantumWalk() throws Exception {
        System.out.println("\n10. === Continuous-Time Quantum Walk ===");
        System.out.println("Quantum walk on 3-node line graph");
        
        SimulationConfig config = new SimulationConfig();
        config.setNumQubits(3); // Position qubits
        config.setBackend(SimulationBackend.STATE_VECTOR);
        
        QuantumSimulator simulator = new QuantumSimulator();
        simulator.initialize(config);
        
        Qubit[] positionQubits = new Qubit[3];
        for (int i = 0; i < 3; i++) {
            positionQubits[i] = new Qubit(i, "pos" + i);
        }
        
        // Start at middle node |010>
        simulator.scheduleEvent(
            EventBuilderHelper.createSingleQubitGate("X1", 
                SingleQubitGate.createPauliX(positionQubits[1]), positionQubits[1]));
        
        // Create quantum walk circuit
        double walkTime = 1.0;
        
        QuantumEvent[] circuit = QuantumWalk.createContinuousTimeWalk(
            positionQubits, null, null, walkTime);
        
        System.out.println("Circuit has " + circuit.length + " gates");
        System.out.println("Graph: 3-node line, starting at middle node");
        System.out.println("Walk time: " + walkTime);
        
        for (QuantumEvent event : circuit) {
            simulator.scheduleEvent(event);
        }
        
        SimulationResult result = simulator.run();
        
        System.out.println("Walk State: " + result.getFinalState());
        
        // Quantum walk should spread to neighboring nodes
        if (result.getFinalState() != null) {
            double[] probs = result.getFinalState().getProbabilities();
            
            System.out.println("\nPosition probabilities:");
            for (int i = 0; i < probs.length; i++) {
                if (probs[i] > 0.01) {
                    String binary = String.format(Locale.ROOT, "%3s", Integer.toBinaryString(i)).replace(' ', '0');
                    System.out.printf(Locale.ROOT, "  |%s>: %.4f (position: %s)\n", 
                        binary, probs[i], binary);
                }
            }
            
            double probStart = probs[2]; // |010>
            double probLeft = probs[1];  // |001>
            double probRight = probs[3]; // |011>
            
            System.out.printf(Locale.ROOT, "%nStarted at |010>: %.4f%n", probStart);
            System.out.printf(Locale.ROOT, "Spread to |001>: %.4f%n", probLeft);
            System.out.printf(Locale.ROOT, "Spread to |011>: %.4f%n", probRight);
            
            if (probLeft > 0.1 && probRight > 0.1) {
                System.out.println("SUCCESS: quantum walk spread to both neighbouring sites");
            } else {
                System.out.println("FAILED: walk did not spread to both neighbours");
            }
        }
        
        simulator.reset();
        System.out.println("=".repeat(60));
    }
}