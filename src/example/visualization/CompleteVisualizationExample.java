package example.visualization;

import java.util.Locale;
import com.quantum.simulator.observer.VisualizationEngine;
import com.quantum.simulator.config.SimulationBackend;
import com.quantum.simulator.config.SimulationConfig;
import com.quantum.simulator.core.*;
import com.quantum.simulator.events.EventType;
import com.quantum.simulator.events.QuantumEvent;
import com.quantum.simulator.math.ComplexMatrix;
import com.quantum.simulator.noise.DephasingNoise;
import com.quantum.simulator.state.*;
import com.quantum.simulator.operations.*; 
import com.quantum.simulator.math.Complex;
import com.quantum.simulator.observer.MetricsCollector;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import javax.imageio.ImageIO;

public class CompleteVisualizationExample {
    
    public static void main(String[] args) throws Exception {
        System.out.println("=== COMPLETE QUANTUM VISUALIZATION SUITE ===");
        
        // Create comprehensive visualization output
        File outputDir = new File("./output/quantum_viz/");
        if (!outputDir.exists()) {
            outputDir.mkdirs();
        }
        
        // 1. Bloch Sphere Visualization for Single Qubit States
        System.out.println("\n1. Generating Bloch Sphere Visualizations...");
        generateBlochSphereGallery(outputDir);
        
        // 2. Multi-Qubit State Visualizations
        System.out.println("\n2. Generating Multi-Qubit State Visualizations...");
        generateMultiQubitVisualizations(outputDir);
        
        // 3. Quantum Circuit Visualization
        System.out.println("\n3. Generating Circuit Diagram Visualizations...");
        generateCircuitVisualizations(outputDir);
        
        // 4. State Evolution Animations
        System.out.println("\n4. Creating State Evolution Animations...");
        generateEvolutionAnimations(outputDir);
        
        // 5. Quantum Tomography Visualization
        System.out.println("\n5. Generating Quantum Tomography Visualizations...");
        generateTomographyVisualizations(outputDir);
        
        // 6. Error and Noise Visualization
        System.out.println("\n6. Generating Error Analysis Visualizations...");
        generateErrorVisualizations(outputDir);
        
        System.out.println("\n=== All visualizations completed ===");
        System.out.println("Output directory: " + outputDir.getAbsolutePath());
    }
    
    // 1. Bloch Sphere Gallery
    private static void generateBlochSphereGallery(File outputDir) throws Exception {
        File blochDir = new File(outputDir, "bloch_spheres");
        blochDir.mkdirs();
        
        // Define common single-qubit states
        String[] stateNames = {
            "|0> (North Pole)", 
            "|1> (South Pole)", 
            "|+> (X-axis)", 
            "|-> (Negative X-axis)",
            "|i> (Y-axis)", 
            "|-i> (Negative Y-axis)",
            "|pi/4> (45 deg from Z-axis)",
            "Random State"
        };
        
        Complex[][] stateVectors = {
            {new Complex(1, 0), new Complex(0, 0)},          // |0>
            {new Complex(0, 0), new Complex(1, 0)},          // |1>
            {new Complex(1/Math.sqrt(2), 0), new Complex(1/Math.sqrt(2), 0)}, // |+>
            {new Complex(1/Math.sqrt(2), 0), new Complex(-1/Math.sqrt(2), 0)},// |->
            {new Complex(1/Math.sqrt(2), 0), new Complex(0, 1/Math.sqrt(2))}, // |i>
            {new Complex(1/Math.sqrt(2), 0), new Complex(0, -1/Math.sqrt(2))},// |-i>
            {new Complex(Math.cos(Math.PI/8), 0), new Complex(Math.sin(Math.PI/8), 0)}, // |π/4>
            {new Complex(Math.random(), Math.random()), new Complex(Math.random(), Math.random())} // Random
        };
        
        // Normalize random state
        Complex[] randomState = stateVectors[7];
        double norm = Math.sqrt(randomState[0].absSquare() + randomState[1].absSquare());
        randomState[0] = randomState[0].divide(norm);
        randomState[1] = randomState[1].divide(norm);
        
        // Generate Bloch spheres for each state
        for (int i = 0; i < stateNames.length; i++) {
            StateVector state = new StateVector(stateVectors[i], 1);
            BufferedImage blochImage = createBlochSphere(state, stateNames[i]);
            
            File outputFile = new File(blochDir, String.format(Locale.ROOT, "bloch_state_%02d_%s.png", 
                i, stateNames[i].replaceAll("[^a-zA-Z0-9]", "_")));
            ImageIO.write(blochImage, "PNG", outputFile);
            
            System.out.println("  Generated: " + stateNames[i]);
            
            // Also save state vector information
            File infoFile = new File(blochDir, String.format(Locale.ROOT, "bloch_state_%02d_info.txt", i));
            saveStateInfo(infoFile, stateNames[i], state);
        }
        
        // Generate a composite image showing all Bloch spheres
        createBlochSphereComposite(blochDir);
    }
    
    private static BufferedImage createBlochSphere(StateVector state, String title) {
        int width = 800;
        int height = 600;
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g2d = image.createGraphics();
        
        // Set up anti-aliasing
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        
        // Fill background
        g2d.setColor(Color.BLACK);
        g2d.fillRect(0, 0, width, height);
        
        // Draw Bloch sphere
        int sphereRadius = 200;
        int centerX = width / 2;
        int centerY = height / 2;
        
        // Draw sphere outline
        g2d.setColor(Color.WHITE);
        g2d.drawOval(centerX - sphereRadius, centerY - sphereRadius, 
                     sphereRadius * 2, sphereRadius * 2);
        
        // Draw axes
        g2d.setColor(new Color(100, 100, 255)); // Blue for Z-axis
        g2d.drawLine(centerX, centerY - sphereRadius, centerX, centerY + sphereRadius);
        g2d.drawString("|0>", centerX + 10, centerY - sphereRadius - 10);
        g2d.drawString("|1>", centerX + 10, centerY + sphereRadius + 20);
        
        g2d.setColor(new Color(255, 100, 100)); // Red for X-axis
        g2d.drawLine(centerX - sphereRadius, centerY, centerX + sphereRadius, centerY);
        g2d.drawString("|+>", centerX + sphereRadius + 10, centerY + 5);
        g2d.drawString("|->", centerX - sphereRadius - 20, centerY + 5);
        
        g2d.setColor(new Color(100, 255, 100)); // Green for Y-axis
        int yAxisRadius = (int) (sphereRadius * 0.8);
        g2d.drawLine(centerX - yAxisRadius, centerY - yAxisRadius, 
                     centerX + yAxisRadius, centerY + yAxisRadius);
        g2d.drawString("|i>", centerX + yAxisRadius + 10, centerY + yAxisRadius + 5);
        g2d.drawString("|-i>", centerX - yAxisRadius - 20, centerY - yAxisRadius - 5);
        
        // Calculate Bloch sphere coordinates
        Complex alpha = state.getAmplitude(0); // |0> amplitude
        Complex beta = state.getAmplitude(1);  // |1> amplitude
        
        double theta = 2 * Math.acos(alpha.abs()); // Polar angle
        double phi = beta.phase() - alpha.phase(); // Azimuthal angle
        
        // Convert to Cartesian coordinates on Bloch sphere
        double x = Math.sin(theta) * Math.cos(phi);
        double y = Math.sin(theta) * Math.sin(phi);
        double z = Math.cos(theta);
        
        // Scale to image coordinates
        int pointX = centerX + (int)(x * sphereRadius);
        int pointY = centerY - (int)(z * sphereRadius); // Invert Y for image coordinates
        
        // Draw state point
        g2d.setColor(Color.YELLOW);
        g2d.fillOval(pointX - 8, pointY - 8, 16, 16);
        g2d.setColor(Color.ORANGE);
        g2d.drawOval(pointX - 8, pointY - 8, 16, 16);
        
        // Draw state vector
        g2d.setColor(Color.YELLOW);
        g2d.drawLine(centerX, centerY, pointX, pointY);
        
        // Draw latitude and longitude lines
        g2d.setColor(new Color(100, 100, 100, 100));
        for (int i = 0; i < 360; i += 30) {
            double angle = Math.toRadians(i);
            int x1 = centerX + (int)(sphereRadius * Math.cos(angle));
            int y1 = centerY + (int)(sphereRadius * Math.sin(angle));
            int x2 = centerX + (int)(sphereRadius * Math.cos(angle + Math.PI));
            int y2 = centerY + (int)(sphereRadius * Math.sin(angle + Math.PI));
            g2d.drawLine(x1, y1, x2, y2);
        }
        
        // Add title and state information
        g2d.setColor(Color.WHITE);
        g2d.setFont(new Font("Arial", Font.BOLD, 16));
        g2d.drawString(title, 20, 30);
        
        g2d.setFont(new Font("Arial", Font.PLAIN, 12));
        String stateInfo = String.format(Locale.ROOT, "State: |psi> = (%.3f%+.3fi)|0> + (%.3f%+.3fi)|1>",
            alpha.real(), alpha.imag(), beta.real(), beta.imag());
        g2d.drawString(stateInfo, 20, 50);
        
        String coordInfo = String.format(Locale.ROOT, "Bloch coords: theta=%.2f deg, phi=%.2f deg, (x=%.2f, y=%.2f, z=%.2f)",
            Math.toDegrees(theta), Math.toDegrees(phi), x, y, z);
        g2d.drawString(coordInfo, 20, 70);
        
        g2d.dispose();
        return image;
    }
    
    private static void saveStateInfo(File file, String name, StateVector state) throws Exception {
        StringBuilder sb = new StringBuilder();
        sb.append("Quantum State Information\n");
        sb.append("========================\n\n");
        sb.append("Name: ").append(name).append("\n\n");
        
        Complex alpha = state.getAmplitude(0);
        Complex beta = state.getAmplitude(1);
        
        sb.append("State Vector:\n");
        sb.append(String.format(Locale.ROOT, "  |psi> = (%.6f%+.6fi)|0> + (%.6f%+.6fi)|1>\n\n",
            alpha.real(), alpha.imag(), beta.real(), beta.imag()));
        
        sb.append("Probabilities:\n");
        sb.append(String.format(Locale.ROOT, "  P(|0>) = %.6f\n", alpha.absSquare()));
        sb.append(String.format(Locale.ROOT, "  P(|1>) = %.6f\n\n", beta.absSquare()));
        
        sb.append("Bloch Sphere Coordinates:\n");
        double theta = 2 * Math.acos(alpha.abs());
        double phi = beta.phase() - alpha.phase();
        sb.append(String.format(Locale.ROOT, "  theta (polar angle) = %.4f rad = %.2f deg\n", 
            theta, Math.toDegrees(theta)));
        sb.append(String.format(Locale.ROOT, "  phi (azimuthal angle) = %.4f rad = %.2f deg\n\n",
            phi, Math.toDegrees(phi)));
        
        sb.append("Cartesian Coordinates:\n");
        double x = Math.sin(theta) * Math.cos(phi);
        double y = Math.sin(theta) * Math.sin(phi);
        double z = Math.cos(theta);
        sb.append(String.format(Locale.ROOT, "  x = %.6f\n", x));
        sb.append(String.format(Locale.ROOT, "  y = %.6f\n", y));
        sb.append(String.format(Locale.ROOT, "  z = %.6f\n\n", z));
        
        sb.append("State Properties:\n");
        sb.append(String.format(Locale.ROOT, "  Purity = %.6f\n", state.getPurity()));
        sb.append(String.format(Locale.ROOT, "  Norm = %.6f\n", Math.sqrt(alpha.absSquare() + beta.absSquare())));
        
        java.nio.file.Files.write(file.toPath(), sb.toString().getBytes());
    }
    
    private static void createBlochSphereComposite(File blochDir) throws Exception {
        // Create a composite image showing all Bloch spheres in a grid
        int gridSize = 4;
        int imageSize = 400;
        int compositeWidth = imageSize * gridSize;
        int compositeHeight = imageSize * ((8 + gridSize - 1) / gridSize);
        
        BufferedImage composite = new BufferedImage(compositeWidth, compositeHeight, 
                                                   BufferedImage.TYPE_INT_RGB);
        Graphics2D g2d = composite.createGraphics();
        g2d.setColor(Color.WHITE);
        g2d.fillRect(0, 0, compositeWidth, compositeHeight);
        
        File[] blochImages = blochDir.listFiles((dir, name) -> name.endsWith(".png") && 
                                                   !name.contains("composite"));
        
        if (blochImages != null) {
            for (int i = 0; i < Math.min(blochImages.length, 8); i++) {
                BufferedImage img = ImageIO.read(blochImages[i]);
                int row = i / gridSize;
                int col = i % gridSize;
                int x = col * imageSize;
                int y = row * imageSize;
                
                // Scale and draw
                g2d.drawImage(img, x, y, imageSize, imageSize, null);
                
                // Add border
                g2d.setColor(Color.GRAY);
                g2d.drawRect(x, y, imageSize, imageSize);
            }
        }
        
        g2d.dispose();
        
        File compositeFile = new File(blochDir, "bloch_spheres_composite.png");
        ImageIO.write(composite, "PNG", compositeFile);
        System.out.println("  Generated composite image: " + compositeFile.getName());
    }
    
    // 2. Multi-Qubit State Visualizations
    private static void generateMultiQubitVisualizations(File outputDir) throws Exception {
        File multiQubitDir = new File(outputDir, "multi_qubit");
        multiQubitDir.mkdirs();
        
        VisualizationEngine visualizer = new VisualizationEngine(50, multiQubitDir.getPath());
        
        // Create various multi-qubit states
        System.out.println("  Generating Bell states...");
        
        // Bell states
        StateVector bellPlus = createBellState(1, 1);  // (|00> + |11>)/√2
        StateVector bellMinus = createBellState(1, -1); // (|00> - |11>)/√2
        StateVector psiPlus = createPsiState(1, 1);     // (|01> + |10>)/√2
        StateVector psiMinus = createPsiState(1, -1);   // (|01> - |10>)/√2
        
        // GHZ state (3 qubits)
        System.out.println("  Generating GHZ state...");
        StateVector ghzState = createGHZState(3);
        
        // W state (3 qubits)
        System.out.println("  Generating W state...");
        StateVector wState = createWState(3);
        
        // Render and save
        saveMultiQubitVisualization(visualizer, multiQubitDir, "bell_plus", bellPlus);
        saveMultiQubitVisualization(visualizer, multiQubitDir, "bell_minus", bellMinus);
        saveMultiQubitVisualization(visualizer, multiQubitDir, "psi_plus", psiPlus);
        saveMultiQubitVisualization(visualizer, multiQubitDir, "psi_minus", psiMinus);
        saveMultiQubitVisualization(visualizer, multiQubitDir, "ghz_state", ghzState);
        saveMultiQubitVisualization(visualizer, multiQubitDir, "w_state", wState);
        
        // Generate entanglement visualization
        System.out.println("  Generating entanglement diagram...");
        generateEntanglementDiagram(multiQubitDir, bellPlus, "bell_state_entanglement");
    }
    
    private static StateVector createBellState(double signReal, double signImag) {
        Complex[] amplitudes = new Complex[4];
        amplitudes[0] = new Complex(1.0/Math.sqrt(2), 0); // |00>
        amplitudes[1] = Complex.ZERO; // |01>
        amplitudes[2] = Complex.ZERO; // |10>
        amplitudes[3] = new Complex(signReal/Math.sqrt(2), signImag/Math.sqrt(2)); // |11>
        return new StateVector(amplitudes, 2);
    }
    
    private static StateVector createPsiState(double signReal, double signImag) {
        Complex[] amplitudes = new Complex[4];
        amplitudes[0] = Complex.ZERO; // |00>
        amplitudes[1] = new Complex(1.0/Math.sqrt(2), 0); // |01>
        amplitudes[2] = new Complex(signReal/Math.sqrt(2), signImag/Math.sqrt(2)); // |10>
        amplitudes[3] = Complex.ZERO; // |11>
        return new StateVector(amplitudes, 2);
    }
    
    private static StateVector createGHZState(int numQubits) {
        int dim = 1 << numQubits;
        Complex[] amplitudes = new Complex[dim];
        
        // GHZ: (|0...0> + |1...1>)/√2
        for (int i = 0; i < dim; i++) {
            if (i == 0 || i == dim - 1) {
                amplitudes[i] = new Complex(1.0/Math.sqrt(2), 0);
            } else {
                amplitudes[i] = Complex.ZERO;
            }
        }
        return new StateVector(amplitudes, numQubits);
    }
    
    private static StateVector createWState(int numQubits) {
        int dim = 1 << numQubits;
        Complex[] amplitudes = new Complex[dim];
        
        // W: (|100...0> + |010...0> + ... + |000...1>)/√n
        double amplitude = 1.0 / Math.sqrt(numQubits);
        for (int i = 0; i < dim; i++) {
            // Count number of 1s in binary representation
            int ones = Integer.bitCount(i);
            if (ones == 1) {
                amplitudes[i] = new Complex(amplitude, 0);
            } else {
                amplitudes[i] = Complex.ZERO;
            }
        }
        return new StateVector(amplitudes, numQubits);
    }
    
    private static void saveMultiQubitVisualization(VisualizationEngine visualizer, 
                                                   File outputDir, String name, 
                                                   StateVector state) throws Exception {
        BufferedImage img = visualizer.renderStateVector(state);
        File outputFile = new File(outputDir, name + ".png");
        ImageIO.write(img, "PNG", outputFile);
        
        // Also save density matrix if state is pure
        if (state.isPure()) {
            DensityMatrix densityMatrix = state.toDensityMatrix();
            BufferedImage densityImg = visualizer.renderDensityMatrix(densityMatrix);
            File densityFile = new File(outputDir, name + "_density.png");
            ImageIO.write(densityImg, "PNG", densityFile);
        }
    }
    
    private static void generateEntanglementDiagram(File outputDir, StateVector state, 
                                                   String name) throws Exception {
        int width = 600;
        int height = 400;
        BufferedImage img = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g2d = img.createGraphics();
        
        // Setup
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2d.setColor(Color.BLACK);
        g2d.fillRect(0, 0, width, height);
        
        // Draw entanglement diagram
        int qubitCount = state.getNumQubits();
        int qubitSpacing = width / (qubitCount + 1);
        
        g2d.setColor(Color.WHITE);
        g2d.setFont(new Font("Arial", Font.BOLD, 14));
        
        // Draw qubits
        for (int i = 0; i < qubitCount; i++) {
            int x = (i + 1) * qubitSpacing;
            int y = 100;
            
            // Qubit circle
            g2d.setColor(new Color(100, 200, 255));
            g2d.fillOval(x - 20, y - 20, 40, 40);
            g2d.setColor(Color.WHITE);
            g2d.drawOval(x - 20, y - 20, 40, 40);
            
            // Qubit label
            g2d.drawString("Q" + i, x - 10, y + 5);
        }
        
        // Draw entanglement connections
        g2d.setColor(new Color(255, 100, 100, 150));
        g2d.setStroke(new BasicStroke(3, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        
        if (qubitCount == 2) {
            // Bell state entanglement
            int x1 = qubitSpacing;
            int x2 = 2 * qubitSpacing;
            int y = 100;
            g2d.drawLine(x1, y, x2, y);
            
            // Draw entanglement strength (for mixed states, would vary)
            g2d.setColor(new Color(255, 255, 100));
            g2d.fillOval((x1 + x2) / 2 - 10, y - 10, 20, 20);
            g2d.setColor(Color.ORANGE);
            g2d.drawOval((x1 + x2) / 2 - 10, y - 10, 20, 20);
            g2d.drawString("Entangled", (x1 + x2) / 2 - 30, y - 15);
        }
        
        // Add state information
        g2d.setColor(Color.WHITE);
        g2d.setFont(new Font("Arial", Font.PLAIN, 12));
        
        double[] probs = state.getProbabilities();
        g2d.drawString("State Probabilities:", 20, 200);
        
        int yPos = 220;
        for (int i = 0; i < probs.length; i++) {
            if (probs[i] > 0.001) {
                String basis = String.format(Locale.ROOT, "%" + qubitCount + "s", 
                    Integer.toBinaryString(i)).replace(' ', '0');
                g2d.drawString(String.format(Locale.ROOT, "|%s>: %.4f", basis, probs[i]), 40, yPos);
                yPos += 20;
            }
        }
        
        // Calculate and display entanglement entropy
        double entropy = calculateEntanglementEntropy(state);
        g2d.drawString(String.format(Locale.ROOT, "Entanglement Entropy: %.4f", entropy), 20, yPos + 20);
        
        g2d.dispose();
        
        File outputFile = new File(outputDir, name + ".png");
        ImageIO.write(img, "PNG", outputFile);
    }
    
    private static double calculateEntanglementEntropy(StateVector state) {
        // For simplicity, calculate von Neumann entropy of reduced density matrix
        if (state.getNumQubits() < 2) return 0.0;
        
        try {
            DensityMatrix density = state.toDensityMatrix();
            // Trace out all but first qubit
            int[] keep = {0};
            DensityMatrix reduced = (DensityMatrix) density.partialTrace(keep);
            return reduced.getVonNeumannEntropy();
        } catch (Exception e) {
            return 0.0;
        }
    }
    
    // 3. Circuit Diagram Visualization
    private static void generateCircuitVisualizations(File outputDir) throws Exception {
        File circuitDir = new File(outputDir, "circuits");
        circuitDir.mkdirs();
        
        System.out.println("  Generating quantum circuit diagrams...");
        
        // Example circuits to visualize
        String[] circuitNames = {
            "Bell State Preparation",
            "Quantum Fourier Transform (3 qubits)",
            "Grover's Search (2 qubits)",
            "Quantum Teleportation",
            "Toffoli Gate Implementation"
        };
        
        for (int i = 0; i < circuitNames.length; i++) {
            BufferedImage circuitImg = createCircuitDiagram(circuitNames[i], i);
            File outputFile = new File(circuitDir, 
                String.format(Locale.ROOT, "circuit_%02d_%s.png", i, 
                    circuitNames[i].replaceAll("[^a-zA-Z0-9]", "_")));
            ImageIO.write(circuitImg, "PNG", outputFile);
            System.out.println("    Generated: " + circuitNames[i]);
        }
        
        // Create circuit timing diagram
        System.out.println("  Generating circuit timing diagram...");
        BufferedImage timingImg = createTimingDiagram();
        File timingFile = new File(circuitDir, "circuit_timing_diagram.png");
        ImageIO.write(timingImg, "PNG", timingFile);
    }
    
    private static BufferedImage createCircuitDiagram(String circuitName, int circuitType) {
        int width = 800;
        int height = 400;
        BufferedImage img = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g2d = img.createGraphics();
        
        // Setup
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2d.setColor(Color.WHITE);
        g2d.fillRect(0, 0, width, height);
        g2d.setColor(Color.BLACK);
        
        // Draw circuit based on type
        int numQubits = 3;
        int qubitSpacing = height / (numQubits + 1);
        int timeStep = width / 10;
        
        // Draw qubit lines
        g2d.setStroke(new BasicStroke(2));
        for (int i = 0; i < numQubits; i++) {
            int y = (i + 1) * qubitSpacing;
            g2d.drawLine(50, y, width - 50, y);
            
            // Qubit labels
            g2d.drawString("q" + i, 20, y + 5);
        }
        
        // Draw gates based on circuit type
        g2d.setFont(new Font("Arial", Font.BOLD, 12));
        
        switch (circuitType) {
            case 0: // Bell State
                drawGate(g2d, 100, qubitSpacing, "H", Color.BLUE);
                drawControlGate(g2d, 200, qubitSpacing, qubitSpacing * 2, Color.RED);
                drawGate(g2d, 300, qubitSpacing * 2, "+", Color.GREEN); // Measurement
                break;
                
            case 1: // QFT
                for (int i = 0; i < numQubits; i++) {
                    int y = (i + 1) * qubitSpacing;
                    drawGate(g2d, 100 + i * 50, y, "H", Color.BLUE);
                    
                    // Controlled phase gates
                    for (int j = i + 1; j < numQubits; j++) {
                        int targetY = (j + 1) * qubitSpacing;
                        drawControlGate(g2d, 150 + i * 50, y, targetY, 
                                       new Color(100, 100, 200));
                        drawGate(g2d, 160 + i * 50, targetY, "R", new Color(200, 100, 100));
                    }
                }
                break;
                
            case 2: // Grover's
                drawGate(g2d, 100, qubitSpacing, "H", Color.BLUE);
                drawGate(g2d, 100, qubitSpacing * 2, "H", Color.BLUE);
                drawControlGate(g2d, 200, qubitSpacing, qubitSpacing * 2, Color.RED);
                // ... more gates
                break;
        }
        
        // Add circuit name
        g2d.setFont(new Font("Arial", Font.BOLD, 16));
        g2d.setColor(Color.DARK_GRAY);
        g2d.drawString(circuitName, width/2 - 100, 30);
        
        // Add legend
        g2d.setFont(new Font("Arial", Font.PLAIN, 10));
        g2d.drawString("H: Hadamard", 20, height - 60);
        g2d.drawString("+: CNOT", 20, height - 40);
        g2d.drawString("●: Control", 20, height - 20);
        
        g2d.dispose();
        return img;
    }
    
    private static void drawGate(Graphics2D g2d, int x, int y, String symbol, Color color) {
        g2d.setColor(color);
        g2d.fillRect(x - 15, y - 15, 30, 30);
        g2d.setColor(Color.WHITE);
        g2d.drawString(symbol, x - 5, y + 5);
        g2d.setColor(color.darker());
        g2d.drawRect(x - 15, y - 15, 30, 30);
    }
    
    private static void drawControlGate(Graphics2D g2d, int x, int controlY, int targetY, Color color) {
        // Draw control dot
        g2d.setColor(color);
        g2d.fillOval(x - 5, controlY - 5, 10, 10);
        
        // Draw line to target
        g2d.drawLine(x, controlY, x, targetY);
        
        // Draw target gate
        drawGate(g2d, x, targetY, "+", color);
    }
    
    private static BufferedImage createTimingDiagram() {
        int width = 1000;
        int height = 500;
        BufferedImage img = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g2d = img.createGraphics();
        
        // Setup
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2d.setColor(Color.WHITE);
        g2d.fillRect(0, 0, width, height);
        g2d.setColor(Color.BLACK);
        
        // Draw timeline
        g2d.drawLine(100, 100, 900, 100);
        g2d.drawLine(100, 300, 900, 300);
        
        // Time markers
        for (int i = 0; i <= 10; i++) {
            int x = 100 + i * 80;
            g2d.drawLine(x, 95, x, 105);
            g2d.drawString(i + "ns", x - 10, 120);
        }
        
        // Draw gate operations on timeline
        Color[] gateColors = {Color.BLUE, Color.RED, Color.GREEN, Color.ORANGE, Color.MAGENTA};
        String[] gateNames = {"H", "X", "Y", "Z", "CNOT"};
        
        for (int i = 0; i < 5; i++) {
            int startX = 150 + i * 120;
            int duration = 20 + i * 10;
            
            // Draw gate block
            g2d.setColor(gateColors[i]);
            g2d.fillRect(startX, 250, duration, 40);
            g2d.setColor(gateColors[i].darker());
            g2d.drawRect(startX, 250, duration, 40);
            
            // Gate name
            g2d.setColor(Color.WHITE);
            g2d.drawString(gateNames[i], startX + 5, 275);
            
            // Draw connection to timeline
            g2d.setColor(Color.GRAY);
            g2d.drawLine(startX + duration/2, 250, startX + duration/2, 300);
            
            // Duration label
            g2d.setColor(Color.BLACK);
            g2d.drawString(duration + "ns", startX, 240);
        }
        
        // Add labels
        g2d.setFont(new Font("Arial", Font.BOLD, 14));
        g2d.drawString("Quantum Circuit Timing Diagram", width/2 - 100, 50);
        g2d.setFont(new Font("Arial", Font.PLAIN, 12));
        g2d.drawString("Time ->", 450, 380);
        g2d.drawString("Gate Operations ↓", 50, 200);
        
        g2d.dispose();
        return img;
    }
    
    // 4. State Evolution Animations
    private static void generateEvolutionAnimations(File outputDir) throws Exception {
        File animationDir = new File(outputDir, "animations");
        animationDir.mkdirs();
        
        System.out.println("  Creating state evolution animations...");
        
        VisualizationEngine visualizer = new VisualizationEngine(50, animationDir.getPath());
        
        // Animation 1: Rabi oscillations
        System.out.println("    Creating Rabi oscillations animation...");
        createRabiOscillationsAnimation(visualizer, animationDir);
        
        // Animation 2: Quantum walk
        System.out.println("    Creating quantum walk animation...");
        createQuantumWalkAnimation(visualizer, animationDir);
        
        // Animation 3: Decoherence
        System.out.println("    Creating decoherence animation...");
        createDecoherenceAnimation(visualizer, animationDir);
    }
    
    private static void createRabiOscillationsAnimation(VisualizationEngine visualizer, 
                                                       File outputDir) throws Exception {
        SimulationConfig config = new SimulationConfig();
        config.setNumQubits(1);
        config.setBackend(SimulationBackend.STATE_VECTOR);
        
        QuantumSimulator simulator = new QuantumSimulator();
        simulator.initialize(config);
        
        // Add visualizer to capture states
        simulator.addObserver(visualizer);
        
        Qubit qubit = new Qubit(0);
        
        // Apply sequence of RX gates to create oscillations
        for (int i = 0; i < 40; i++) {
            double angle = i * Math.PI / 20.0; // Complete 2π rotation
            QuantumEvent event = new QuantumEvent.Builder("RX_" + i)
                .withOperation(SingleQubitGate.createRX(angle, qubit))
                .withTargetQubits(qubit)
                .build();
            simulator.scheduleEvent(event);
        }
        
        simulator.run();
        
        // Generate animation
        visualizer.generateAnimation("rabi_oscillations", 100);
        
        // Also create a summary plot
        createEvolutionPlot(outputDir, "rabi_evolution.png", 
                           "Rabi Oscillations: |0> -> |1> -> |0>");
        
        simulator.reset();
    }
    
    private static void createQuantumWalkAnimation(VisualizationEngine visualizer, 
                                                  File outputDir) throws Exception {
        // Simplified quantum walk on a line of 3 qubits
        SimulationConfig config = new SimulationConfig();
        config.setNumQubits(3);
        config.setBackend(SimulationBackend.STATE_VECTOR);
        
        QuantumSimulator simulator = new QuantumSimulator();
        simulator.initialize(config);
        
        simulator.addObserver(visualizer);
        
        Qubit q0 = new Qubit(0);
        Qubit q1 = new Qubit(1);
        Qubit q2 = new Qubit(2);
        
        // Initialize in |100> (walker at position 1)
        // Apply Hadamard to coin space (simplified)
        QuantumEvent init = new QuantumEvent.Builder("init")
            .withOperation(SingleQubitGate.createHadamard(q0))
            .withTargetQubits(q0)
            .build();
        
        // Step 1: Coin flip and shift
        QuantumEvent step1_coin = new QuantumEvent.Builder("step1_coin")
            .withOperation(SingleQubitGate.createHadamard(q0))
            .withTargetQubits(q0)
            .build();
        
        QuantumEvent step1_shift = new QuantumEvent.Builder("step1_shift")
            .withOperation(MultiQubitGate.createSWAP(q0, q1))
            .withTargetQubits(q0, q1)
            .build();
        
        // Step 2
        QuantumEvent step2_coin = new QuantumEvent.Builder("step2_coin")
            .withOperation(SingleQubitGate.createHadamard(q0))
            .withTargetQubits(q0)
            .build();
        
        QuantumEvent step2_shift = new QuantumEvent.Builder("step2_shift")
            .withOperation(MultiQubitGate.createSWAP(q1, q2))
            .withTargetQubits(q1, q2)
            .build();
        
        // Schedule events
        simulator.scheduleEvent(init);
        simulator.scheduleEvent(step1_coin);
        simulator.scheduleEvent(step1_shift);
        simulator.scheduleEvent(step2_coin);
        simulator.scheduleEvent(step2_shift);
        
        simulator.run();
        visualizer.generateAnimation("quantum_walk", 150);
        
        simulator.reset();
    }
    
    private static void createDecoherenceAnimation(VisualizationEngine visualizer, 
                                                  File outputDir) throws Exception {
        // Show pure state becoming mixed due to decoherence
        SimulationConfig config = new SimulationConfig();
        config.setNumQubits(1);
        config.setBackend(SimulationBackend.DENSITY_MATRIX); // Need density matrix for mixed states
        
        QuantumSimulator simulator = new QuantumSimulator();
        simulator.initialize(config);
        
        simulator.addObserver(visualizer);
        
        Qubit qubit = new Qubit(0);
        
        // Start in superposition
        QuantumEvent init = new QuantumEvent.Builder("init_superposition")
            .withOperation(SingleQubitGate.createHadamard(qubit))
            .withTargetQubits(qubit)
            .build();
        
        // Apply dephasing noise over time (simulated as multiple dephasing channels)
        for (int i = 0; i < 10; i++) {
            QuantumEvent dephase = new QuantumEvent.Builder("dephase_" + i)
                .withOperation(new DephasingNoise(0.1, qubit))
                .withTargetQubits(qubit)
                .withType(EventType.NOISE_APPLICATION)
                .build();
            simulator.scheduleEvent(dephase);
        }
        
        simulator.scheduleEvent(init);
        simulator.run();
        visualizer.generateAnimation("decoherence", 200);
        
        simulator.reset();
    }
    
    private static void createEvolutionPlot(File outputDir, String filename, String title) 
            throws Exception {
        int width = 800;
        int height = 400;
        BufferedImage img = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g2d = img.createGraphics();
        
        // Setup
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2d.setColor(Color.WHITE);
        g2d.fillRect(0, 0, width, height);
        g2d.setColor(Color.BLACK);
        
        // Draw axes
        g2d.drawLine(50, 50, 50, height - 50);
        g2d.drawLine(50, height - 50, width - 50, height - 50);
        
        // Axis labels
        g2d.drawString("Time (steps)", width/2 - 30, height - 20);
        g2d.drawString("State Probability", 10, 30);
        
        // Title
        g2d.setFont(new Font("Arial", Font.BOLD, 14));
        g2d.drawString(title, width/2 - 100, 30);
        
        // Draw evolution curves
        g2d.setFont(new Font("Arial", Font.PLAIN, 12));
        
        // Simulate Rabi oscillations: P(|0>) = cos²(ωt/2), P(|1>) = sin²(ωt/2)
        int steps = 100;
        int[] xPoints0 = new int[steps];
        int[] yPoints0 = new int[steps];
        int[] xPoints1 = new int[steps];
        int[] yPoints1 = new int[steps];
        
        for (int i = 0; i < steps; i++) {
            double t = i * 2 * Math.PI / steps;
            double p0 = Math.pow(Math.cos(t/2), 2);
            double p1 = Math.pow(Math.sin(t/2), 2);
            
            xPoints0[i] = 50 + (int)(i * (width - 100) / steps);
            yPoints0[i] = height - 50 - (int)(p0 * (height - 100));
            xPoints1[i] = 50 + (int)(i * (width - 100) / steps);
            yPoints1[i] = height - 50 - (int)(p1 * (height - 100));
        }
        
        // Draw |0> probability curve
        g2d.setColor(Color.BLUE);
        g2d.setStroke(new BasicStroke(2));
        g2d.drawPolyline(xPoints0, yPoints0, steps);
        g2d.drawString("P(|0>)", width - 100, yPoints0[steps-1] - 10);
        
        // Draw |1> probability curve
        g2d.setColor(Color.RED);
        g2d.drawPolyline(xPoints1, yPoints1, steps);
        g2d.drawString("P(|1>)", width - 100, yPoints1[steps-1] + 20);
        
        // Add grid
        g2d.setColor(new Color(200, 200, 200));
        g2d.setStroke(new BasicStroke(1));
        for (int i = 0; i <= 10; i++) {
            int y = height - 50 - i * (height - 100) / 10;
            g2d.drawLine(50, y, width - 50, y);
            g2d.drawString(String.format(Locale.ROOT, "%.1f", i/10.0), 30, y + 5);
        }
        
        g2d.dispose();
        
        File outputFile = new File(outputDir, filename);
        ImageIO.write(img, "PNG", outputFile);
    }
    
    // 5. Quantum Tomography Visualization
    private static void generateTomographyVisualizations(File outputDir) throws Exception {
        File tomographyDir = new File(outputDir, "tomography");
        tomographyDir.mkdirs();
        
        System.out.println("  Generating quantum tomography visualizations...");
        
        // Create different quantum states
        StateVector[] states = {
            StateVector.createZeroState(1),      // |0>
            StateVector.fromComputationalBasis(1, 1), // |1>
            StateVector.createPlusState(1),      // |+>
            createRandomState(1)                 // Random pure state
        };
        
        String[] stateNames = {"Zero State", "One State", "Plus State", "Random State"};
        
        for (int i = 0; i < states.length; i++) {
            System.out.println("    Reconstructing: " + stateNames[i]);
            
            // Simulate tomography measurements
            DensityMatrix reconstructed = simulateTomography(states[i]);
            
            // Create visualization
            BufferedImage img = createTomographyVisualization(states[i], reconstructed, stateNames[i]);
            File outputFile = new File(tomographyDir, 
                String.format(Locale.ROOT, "tomography_%s.png", stateNames[i].replace(" ", "_")));
            ImageIO.write(img, "PNG", outputFile);
            
            // Save reconstruction error
            saveTomographyResults(tomographyDir, stateNames[i], states[i], reconstructed);
        }
        
        // Create comparison visualization
        createTomographyComparison(tomographyDir, states, stateNames);
    }
    
    private static StateVector createRandomState(int numQubits) {
        int dim = 1 << numQubits;
        Complex[] amplitudes = new Complex[dim];
        double norm = 0.0;
        
        // Generate random amplitudes
        for (int i = 0; i < dim; i++) {
            double real = Math.random() * 2 - 1;
            double imag = Math.random() * 2 - 1;
            amplitudes[i] = new Complex(real, imag);
            norm += amplitudes[i].absSquare();
        }
        
        // Normalize
        norm = Math.sqrt(norm);
        for (int i = 0; i < dim; i++) {
            amplitudes[i] = amplitudes[i].divide(norm);
        }
        
        return new StateVector(amplitudes, numQubits);
    }
    
    private static DensityMatrix simulateTomography(StateVector state) {
        // Simplified tomography simulation
        // In real tomography, we would measure in multiple bases
        
        // Convert to density matrix (ideal reconstruction)
        DensityMatrix ideal = state.toDensityMatrix();
        
        // Add some noise to simulate imperfect reconstruction
        int dim = ideal.getMatrix().rows();
        ComplexMatrix noisyMatrix = ideal.getMatrix().copy();
        
        
        
        // Add Hermitian-preserving noise
        for (int i = 0; i < dim; i++) {
            for (int j = i; j < dim; j++) { // Only upper triangle
                Complex current = noisyMatrix.get(i, j);
                double noiseReal = (Math.random() - 0.5) * 0.05;
                double noiseImag = (Math.random() - 0.5) * 0.05;

                if (i == j) {
                    // Diagonal: must stay real -> ignore imag noise
                    Complex noisy = new Complex(current.real() + noiseReal, 0.0);
                    noisyMatrix.set(i, i, noisy);
                } else {
                    // Off-diagonal: add noise, then enforce Hermitian symmetry
                    Complex noisy = new Complex(
                        current.real() + noiseReal,
                        current.imag() + noiseImag
                    );
                    noisyMatrix.set(i, j, noisy);
                    // Set conjugate at (j, i)
                    noisyMatrix.set(j, i, noisy.conjugate());
                }
            }
        }
        
        // Ensure it's still a valid density matrix
        // (trace = 1, Hermitian, positive)
        Complex trace = MatrixOperations.trace(noisyMatrix);
        noisyMatrix = noisyMatrix.multiply(new Complex(1.0/trace.real(), 0));
        
        return new DensityMatrix(noisyMatrix, state.getNumQubits());
    }
    
    private static BufferedImage createTomographyVisualization(StateVector ideal, 
                                                              DensityMatrix reconstructed, 
                                                              String title) {
        int width = 1000;
        int height = 400;
        BufferedImage img = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g2d = img.createGraphics();
        
        // Setup
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2d.setColor(Color.WHITE);
        g2d.fillRect(0, 0, width, height);
        g2d.setColor(Color.BLACK);
        
        // Title
        g2d.setFont(new Font("Arial", Font.BOLD, 16));
        g2d.drawString("Quantum State Tomography: " + title, width/2 - 150, 30);
        
        // Draw ideal state (left side)
        g2d.setFont(new Font("Arial", Font.BOLD, 14));
        g2d.drawString("Ideal State", 100, 70);
        
        // Draw reconstructed state (right side)
        g2d.drawString("Reconstructed State", width/2 + 100, 70);
        
        // Calculate fidelity
        double fidelity = calculateFidelity(ideal, reconstructed);
        
        // Draw fidelity
        g2d.setColor(new Color(0, 100, 0));
        g2d.drawString(String.format(Locale.ROOT, "Reconstruction Fidelity: %.4f", fidelity), 
                      width/2 - 100, height - 30);
        
        // Draw state information
        g2d.setColor(Color.BLACK);
        g2d.setFont(new Font("Arial", Font.PLAIN, 12));
        
        // Ideal state info
        Complex alpha = ideal.getAmplitude(0);
        Complex beta = ideal.getAmplitude(1);
        g2d.drawString(String.format(Locale.ROOT, "|psi> = (%.3f%+.3fi)|0> + (%.3f%+.3fi)|1>",
            alpha.real(), alpha.imag(), beta.real(), beta.imag()), 50, 100);
        
        g2d.drawString(String.format(Locale.ROOT, "Purity: %.4f", ideal.getPurity()), 50, 120);
        
        // Reconstructed state info
        double reconPurity = reconstructed.getPurity();
        g2d.drawString(String.format(Locale.ROOT, "Reconstructed Purity: %.4f", reconPurity), 
                      width/2 + 50, 120);
        
        // Draw Bloch spheres for comparison
        int sphereSize = 150;
        
        // Ideal Bloch sphere
        int idealX = 100;
        int idealY = 200;

        double[] reconBloch = densityMatrixToBlochVector(reconstructed);
       
        // Reconstructed Bloch sphere
        int reconX = width/2 + 100;
        int reconY = 200;
        drawSimpleBlochSphereFromVector(g2d, reconX, reconY, sphereSize, reconBloch, "Reconstructed");
        
        
        // Draw error visualization
        g2d.setColor(Color.RED);
        g2d.drawString(String.format(Locale.ROOT, "Reconstruction Error: %.4f", 1 - fidelity), 
                      width/2 - 80, height - 10);
        
        // Draw color bar for fidelity
        drawFidelityColorBar(g2d, width/2 - 100, height - 80, 200, 20, fidelity);
        
        g2d.dispose();
        return img;
    }
    

    private static double[] densityMatrixToBlochVector(DensityMatrix rho) {
        // For a single qubit: ρ = (I + r·σ)/2 -> r = [Tr(ρ X), Tr(ρ Y), Tr(ρ Z)]
        ComplexMatrix m = rho.getMatrix();
        if (m.rows() != 2) {
            throw new IllegalArgumentException("Only single-qubit states supported");
        }

        // Pauli matrices
        ComplexMatrix X = new ComplexMatrix(new Complex[][]{
            {Complex.ZERO, Complex.ONE},
            {Complex.ONE, Complex.ZERO}
        });
        ComplexMatrix Y = new ComplexMatrix(new Complex[][]{
            {Complex.ZERO, new Complex(0, -1)},
            {new Complex(0, 1), Complex.ZERO}
        });
        ComplexMatrix Z = new ComplexMatrix(new Complex[][]{
            {Complex.ONE, Complex.ZERO},
            {Complex.ZERO, new Complex(-1, 0)}
        });

        Complex rx = MatrixOperations.trace(MatrixOperations.multiply(m, X));
        Complex ry = MatrixOperations.trace(MatrixOperations.multiply(m, Y));
        Complex rz = MatrixOperations.trace(MatrixOperations.multiply(m, Z));

        return new double[]{rx.real(), ry.real(), rz.real()};
    }
    
    private static void drawSimpleBlochSphereFromVector(Graphics2D g2d, int x, int y, int size,
            double[] blochVec, String label) {
        int radius = size / 2;
        g2d.setColor(Color.LIGHT_GRAY);
        g2d.fillOval(x - radius, y - radius, size, size);
        g2d.setColor(Color.DARK_GRAY);
        g2d.drawOval(x - radius, y - radius, size, size);

        // Axes
        g2d.setColor(Color.BLACK);
        g2d.drawLine(x - radius, y, x + radius, y); // X
        g2d.drawLine(x, y - radius, x, y + radius); // Z

        // Clamp to unit ball (in case numerical errors push it outside)
        double norm = Math.sqrt(blochVec[0]*blochVec[0] + blochVec[1]*blochVec[1] + blochVec[2]*blochVec[2]);
        if (norm > 1.0) {
            blochVec[0] /= norm;
            blochVec[1] /= norm;
            blochVec[2] /= norm;
        }

        int pointX = x + (int)(blochVec[0] * radius);
        int pointY = y - (int)(blochVec[2] * radius); // Note: Z is vertical axis

        g2d.setColor(Color.RED);
        g2d.fillOval(pointX - 5, pointY - 5, 10, 10);

        g2d.setColor(Color.BLACK);
        g2d.drawString(label, x - 30, y + radius + 20);
    }


	private static double calculateFidelity(StateVector ideal, DensityMatrix reconstructed) {
        try {
            // Fidelity F = <ψ|ρ|ψ> for pure state |ψ>
            DensityMatrix idealDensity = ideal.toDensityMatrix();
            ComplexMatrix product = MatrixOperations.multiply(
                idealDensity.getMatrix(), reconstructed.getMatrix());
            Complex trace = MatrixOperations.trace(product);
            return Math.max(0, Math.min(1, trace.real()));
        } catch (Exception e) {
            return 0.0;
        }
    }
    
    private static void drawFidelityColorBar(Graphics2D g2d, int x, int y, int width, int height, 
                                            double fidelity) {
        // Draw gradient from red (0) to green (1)
        for (int i = 0; i < width; i++) {
            double value = (double) i / width;
            Color color;
            if (value < 0.5) {
                // Red to yellow
                int red = 255;
                int green = (int)(255 * (value * 2));
                color = new Color(red, green, 0);
            } else {
                // Yellow to green
                int red = (int)(255 * (2 - value * 2));
                int green = 255;
                color = new Color(red, green, 0);
            }
            g2d.setColor(color);
            g2d.fillRect(x + i, y, 1, height);
        }
        
        // Draw fidelity marker
        int markerX = x + (int)(fidelity * width);
        g2d.setColor(Color.BLACK);
        g2d.fillRect(markerX - 2, y - 10, 5, height + 20);
        
        // Draw labels
        g2d.drawString("0.0", x - 10, y + height + 15);
        g2d.drawString("0.5", x + width/2 - 10, y + height + 15);
        g2d.drawString("1.0", x + width - 10, y + height + 15);
        
        g2d.drawString(String.format(Locale.ROOT, "Fidelity: %.3f", fidelity), markerX - 30, y - 15);
    }
    
    private static void saveTomographyResults(File outputDir, String name, 
                                             StateVector ideal, DensityMatrix reconstructed) 
            throws Exception {
        File file = new File(outputDir, name.replace(" ", "_") + "_results.txt");
        
        StringBuilder sb = new StringBuilder();
        sb.append("Quantum State Tomography Results\n");
        sb.append("================================\n\n");
        sb.append("State: ").append(name).append("\n\n");
        
        sb.append("Ideal State:\n");
        Complex alpha = ideal.getAmplitude(0);
        Complex beta = ideal.getAmplitude(1);
        sb.append(String.format(Locale.ROOT, "  |psi> = (%.6f%+.6fi)|0> + (%.6f%+.6fi)|1>\n",
            alpha.real(), alpha.imag(), beta.real(), beta.imag()));
        sb.append(String.format(Locale.ROOT, "  Purity: %.6f\n\n", ideal.getPurity()));
        
        sb.append("Reconstructed Density Matrix:\n");
        ComplexMatrix rho = reconstructed.getMatrix();
        for (int i = 0; i < rho.rows(); i++) {
            sb.append("  [");
            for (int j = 0; j < rho.cols(); j++) {
                Complex element = rho.get(i, j);
                sb.append(String.format(Locale.ROOT, " %.3f%+.3fi", element.real(), element.imag()));
                if (j < rho.cols() - 1) sb.append(", ");
            }
            sb.append(" ]\n");
        }
        sb.append("\n");
        
        sb.append(String.format(Locale.ROOT, "Reconstructed Purity: %.6f\n", reconstructed.getPurity()));
        
        double fidelity = calculateFidelity(ideal, reconstructed);
        sb.append(String.format(Locale.ROOT, "Fidelity: %.6f\n", fidelity));
        
        sb.append(String.format(Locale.ROOT, "Trace Distance: %.6f\n", 1 - fidelity));
        
        java.nio.file.Files.write(file.toPath(), sb.toString().getBytes());
    }
    
    private static void createTomographyComparison(File outputDir, StateVector[] states, 
                                                 String[] names) throws Exception {
        int width = 800;
        int height = 600;
        BufferedImage img = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g2d = img.createGraphics();
        
        // Setup
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2d.setColor(Color.WHITE);
        g2d.fillRect(0, 0, width, height);
        g2d.setColor(Color.BLACK);
        
        // Title
        g2d.setFont(new Font("Arial", Font.BOLD, 18));
        g2d.drawString("Quantum Tomography Comparison", width/2 - 150, 40);
        
        // Draw comparison table
        int rowHeight = 100;
        int startY = 80;
        
        g2d.setFont(new Font("Arial", Font.BOLD, 14));
        g2d.drawString("State", 50, startY - 10);
        g2d.drawString("Ideal Purity", 200, startY - 10);
        g2d.drawString("Reconstructed Purity", 350, startY - 10);
        g2d.drawString("Fidelity", 550, startY - 10);
        
        g2d.setFont(new Font("Arial", Font.PLAIN, 12));
        
        for (int i = 0; i < states.length; i++) {
            int y = startY + i * rowHeight;
            
            // State name
            g2d.drawString(names[i], 50, y + 20);
            
            // Ideal purity
            g2d.drawString(String.format(Locale.ROOT, "%.4f", states[i].getPurity()), 200, y + 20);
            
            // Reconstructed purity and fidelity
            DensityMatrix reconstructed = simulateTomography(states[i]);
            double reconPurity = reconstructed.getPurity();
            double fidelity = calculateFidelity(states[i], reconstructed);
            
            g2d.drawString(String.format(Locale.ROOT, "%.4f", reconPurity), 350, y + 20);
            g2d.drawString(String.format(Locale.ROOT, "%.4f", fidelity), 550, y + 20);
            
            // Draw fidelity bar
            int barWidth = 150;
            int barX = 600;
            int barHeight = 20;
            
            // Background
            g2d.setColor(Color.LIGHT_GRAY);
            g2d.fillRect(barX, y, barWidth, barHeight);
            
            // Filled part
            int filledWidth = (int)(fidelity * barWidth);
            Color barColor;
            if (fidelity > 0.9) barColor = Color.GREEN;
            else if (fidelity > 0.7) barColor = Color.YELLOW;
            else barColor = Color.RED;
            
            g2d.setColor(barColor);
            g2d.fillRect(barX, y, filledWidth, barHeight);
            
            // Border
            g2d.setColor(Color.BLACK);
            g2d.drawRect(barX, y, barWidth, barHeight);
        }
        
        // Add summary statistics
        double totalFidelity = 0;
        for (int i = 0; i < states.length; i++) {
            DensityMatrix reconstructed = simulateTomography(states[i]);
            totalFidelity += calculateFidelity(states[i], reconstructed);
        }
        double avgFidelity = totalFidelity / states.length;
        
        g2d.setColor(new Color(0, 100, 0));
        g2d.drawString(String.format(Locale.ROOT, "Average Fidelity: %.4f", avgFidelity), 
                      50, startY + states.length * rowHeight + 40);
        
        g2d.dispose();
        
        File outputFile = new File(outputDir, "tomography_comparison.png");
        ImageIO.write(img, "PNG", outputFile);
    }
    
    // 6. Error and Noise Visualization
    private static void generateErrorVisualizations(File outputDir) throws Exception {
        File errorDir = new File(outputDir, "error_analysis");
        errorDir.mkdirs();
        
        System.out.println("  Generating error analysis visualizations...");
        
        // Create error analysis for different noise models
        System.out.println("    Analyzing depolarizing noise...");
        analyzeNoiseModel(errorDir, "depolarizing", 0.1);
        
        System.out.println("    Analyzing dephasing noise...");
        analyzeNoiseModel(errorDir, "dephasing", 0.1);
        
        System.out.println("    Analyzing amplitude damping...");
        analyzeNoiseModel(errorDir, "amplitude_damping", 0.1);
        
        System.out.println("    Creating noise comparison...");
        createNoiseComparison(errorDir);
    }
    
    private static void analyzeNoiseModel(File outputDir, String noiseType, double maxProbability) 
            throws Exception {
        int width = 800;
        int height = 600;
        BufferedImage img = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g2d = img.createGraphics();
        
        // Setup
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2d.setColor(Color.WHITE);
        g2d.fillRect(0, 0, width, height);
        g2d.setColor(Color.BLACK);
        
        // Title
        String noiseName = noiseType.substring(0, 1).toUpperCase() + noiseType.substring(1);
        noiseName = noiseName.replace("_", " ");
        g2d.setFont(new Font("Arial", Font.BOLD, 16));
        g2d.drawString(noiseName + " Noise Analysis", width/2 - 100, 30);
        
        // Analyze noise effect on different initial states
        String[] initialStates = {"|0>", "|1>", "|+>", "Random"};
        Color[] colors = {Color.BLUE, Color.RED, Color.GREEN, Color.ORANGE};
        
        int numPoints = 20;
        int[][] xPoints = new int[initialStates.length][numPoints];
        int[][] yPoints = new int[initialStates.length][numPoints];
        
        // Simulate noise at different probabilities
        for (int s = 0; s < initialStates.length; s++) {
            StateVector initialState = createInitialState(initialStates[s]);
            
            for (int p = 0; p < numPoints; p++) {
                double probability = p * maxProbability / (numPoints - 1);
                
                // Simulate noise effect (simplified)
                double fidelity = simulateNoiseEffect(initialState, noiseType, probability);
                
                xPoints[s][p] = 100 + (int)(p * (width - 200) / (numPoints - 1));
                yPoints[s][p] = height - 100 - (int)(fidelity * (height - 200));
            }
        }
        
        // Draw curves
        g2d.setStroke(new BasicStroke(2));
        for (int s = 0; s < initialStates.length; s++) {
            g2d.setColor(colors[s]);
            g2d.drawPolyline(xPoints[s], yPoints[s], numPoints);
            
            // Draw legend
            g2d.fillRect(650, 100 + s * 30, 20, 20);
            g2d.setColor(Color.BLACK);
            g2d.drawString(initialStates[s], 680, 115 + s * 30);
        }
        
        // Draw axes
        g2d.setColor(Color.BLACK);
        g2d.drawLine(100, height - 100, width - 100, height - 100);
        g2d.drawLine(100, height - 100, 100, 100);
        
        // Axis labels
        g2d.drawString("Noise Probability", width/2 - 50, height - 70);
        g2d.drawString("State Fidelity", 30, height/2);
        
        // Grid
        g2d.setColor(new Color(200, 200, 200));
        for (int i = 0; i <= 10; i++) {
            int x = 100 + i * (width - 200) / 10;
            g2d.drawLine(x, 100, x, height - 100);
            g2d.drawString(String.format(Locale.ROOT, "%.1f", i * maxProbability / 10), x - 10, height - 80);
            
            int y = height - 100 - i * (height - 200) / 10;
            g2d.drawLine(100, y, width - 100, y);
            g2d.drawString(String.format(Locale.ROOT, "%.1f", i / 10.0), 70, y + 5);
        }
        
        // Add statistics
        g2d.setColor(Color.DARK_GRAY);
        g2d.setFont(new Font("Arial", Font.PLAIN, 12));
        
        // Calculate average fidelity degradation
        double avgFidelityAtMax = 0;
        for (int s = 0; s < initialStates.length; s++) {
            double fidelity = 1 - (height - yPoints[s][numPoints-1] - 100) / (double)(height - 200);
            avgFidelityAtMax += fidelity;
        }
        avgFidelityAtMax /= initialStates.length;
        
        g2d.drawString(String.format(Locale.ROOT, "Average fidelity at p=%.2f: %.4f", 
            maxProbability, avgFidelityAtMax), 100, 80);
        
        g2d.dispose();
        
        File outputFile = new File(outputDir, noiseType + "_analysis.png");
        ImageIO.write(img, "PNG", outputFile);
    }
    
    private static StateVector createInitialState(String type) {
        switch (type) {
            case "|0>":
                return StateVector.createZeroState(1);
            case "|1>":
                return StateVector.fromComputationalBasis(1, 1);
            case "|+>":
                return StateVector.createPlusState(1);
            case "Random":
                return createRandomState(1);
            default:
                return StateVector.createZeroState(1);
        }
    }
    
    private static double simulateNoiseEffect(StateVector initialState, String noiseType, 
                                             double probability) {
        // Simplified noise simulation
        // In reality, would apply actual noise models
        
        switch (noiseType) {
            case "depolarizing":
                // Depolarizing noise: ρ -> (1-p)ρ + pI/2
                return 1 - (3.0/4.0) * probability;
                
            case "dephasing":
                // Dephasing noise: coherence decays
                double initialCoherence = 2 * Math.abs(
                    initialState.getAmplitude(0).multiply(
                    initialState.getAmplitude(1).conjugate()).real());
                double finalCoherence = initialCoherence * (1 - probability);
                return 0.5 + 0.5 * finalCoherence;
                
            case "amplitude_damping":
                // Amplitude damping: |1> decays to |0>
                double p1 = initialState.getAmplitude(1).absSquare();
                double remainingP1 = p1 * (1 - probability);
                double newFidelity = Math.sqrt(1 - remainingP1) + Math.sqrt(remainingP1 * (1 - probability));
                return newFidelity * newFidelity;
                
            default:
                return 1 - probability;
        }
    }
    
    private static void createNoiseComparison(File outputDir) throws Exception {
        int width = 1000;
        int height = 700;
        BufferedImage img = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g2d = img.createGraphics();
        
        // Setup
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2d.setColor(Color.WHITE);
        g2d.fillRect(0, 0, width, height);
        g2d.setColor(Color.BLACK);
        
        // Title
        g2d.setFont(new Font("Arial", Font.BOLD, 18));
        g2d.drawString("Quantum Noise Model Comparison", width/2 - 150, 40);
        
        // Test on |+> state
        StateVector plusState = StateVector.createPlusState(1);
        
        String[] noiseTypes = {"depolarizing", "dephasing", "amplitude_damping"};
        String[] noiseNames = {"Depolarizing", "Dephasing", "Amplitude Damping"};
        Color[] colors = {Color.BLUE, Color.RED, Color.GREEN};
        
        int numPoints = 50;
        double maxProbability = 1.0;
        
        // Plot each noise type
        for (int n = 0; n < noiseTypes.length; n++) {
            int[] xPoints = new int[numPoints];
            int[] yPoints = new int[numPoints];
            
            for (int p = 0; p < numPoints; p++) {
                double probability = p * maxProbability / (numPoints - 1);
                double fidelity = simulateNoiseEffect(plusState, noiseTypes[n], probability);
                
                xPoints[p] = 150 + (int)(p * (width - 300) / (numPoints - 1));
                yPoints[p] = height - 150 - (int)(fidelity * (height - 300));
            }
            
            // Draw curve
            g2d.setColor(colors[n]);
            g2d.setStroke(new BasicStroke(3));
            g2d.drawPolyline(xPoints, yPoints, numPoints);
            
            // Draw legend
            g2d.fillRect(800, 100 + n * 40, 30, 30);
            g2d.setColor(Color.BLACK);
            g2d.drawString(noiseNames[n], 840, 120 + n * 40);
        }
        
        // Draw axes
        g2d.setColor(Color.BLACK);
        g2d.setStroke(new BasicStroke(2));
        g2d.drawLine(150, height - 150, width - 150, height - 150);
        g2d.drawLine(150, height - 150, 150, 150);
        
        // Axis labels
        g2d.drawString("Noise Probability (p)", width/2 - 50, height - 120);
        g2d.drawString("State Fidelity F(rho, rho0)", 40, height/2);
        
        // Grid and ticks
        g2d.setColor(new Color(200, 200, 200));
        g2d.setStroke(new BasicStroke(1));
        for (int i = 0; i <= 10; i++) {
            int x = 150 + i * (width - 300) / 10;
            g2d.drawLine(x, 150, x, height - 150);
            g2d.drawString(String.format(Locale.ROOT, "%.1f", i * 0.1), x - 10, height - 130);
            
            int y = height - 150 - i * (height - 300) / 10;
            g2d.drawLine(150, y, width - 150, y);
            g2d.drawString(String.format(Locale.ROOT, "%.1f", i * 0.1), 120, y + 5);
        }
        
        // Add analysis
        g2d.setColor(Color.DARK_GRAY);
        g2d.setFont(new Font("Arial", Font.PLAIN, 12));
        g2d.drawString("Initial State: |+> = (|0> + |1>)/sqrt2", 150, 130);
        g2d.drawString("Analysis of different noise models on superposition state", 150, 110);
        
        // Calculate and display noise strengths at 50% fidelity
        g2d.drawString("Noise probability at 50% fidelity:", 650, height - 200);
        for (int n = 0; n < noiseTypes.length; n++) {
            double p50 = findProbabilityForFidelity(noiseTypes[n], 0.5);
            g2d.setColor(colors[n]);
            g2d.drawString(String.format(Locale.ROOT, "%s: p = %.3f", noiseNames[n], p50), 
                         650, height - 180 + n * 20);
        }
        
        g2d.dispose();
        
        File outputFile = new File(outputDir, "noise_comparison.png");
        ImageIO.write(img, "PNG", outputFile);
    }
    
    private static double findProbabilityForFidelity(String noiseType, double targetFidelity) {
        // Binary search for probability that gives target fidelity
        double low = 0;
        double high = 1;
        StateVector state = StateVector.createPlusState(1);
        
        for (int i = 0; i < 20; i++) {
            double mid = (low + high) / 2;
            double fidelity = simulateNoiseEffect(state, noiseType, mid);
            
            if (fidelity > targetFidelity) {
                low = mid;
            } else {
                high = mid;
            }
        }
        
        return (low + high) / 2;
    }
}

// Helper class for matrix operations (simplified)
class MatrixOperations {
    public static Complex trace(ComplexMatrix matrix) {
        Complex sum = Complex.ZERO;
        for (int i = 0; i < matrix.rows(); i++) {
            sum = sum.add(matrix.get(i, i));
        }
        return sum;
    }
    
    public static ComplexMatrix multiply(ComplexMatrix a, ComplexMatrix b) {
        // Simplified multiplication for 2x2 matrices
        if (a.rows() == 2 && b.rows() == 2) {
            ComplexMatrix result = new ComplexMatrix(2, 2);
            for (int i = 0; i < 2; i++) {
                for (int j = 0; j < 2; j++) {
                    Complex sum = Complex.ZERO;
                    for (int k = 0; k < 2; k++) {
                        sum = sum.add(a.get(i, k).multiply(b.get(k, j)));
                    }
                    result.set(i, j, sum);
                }
            }
            return result;
        }
        return a; // Simplified
    }
    

}