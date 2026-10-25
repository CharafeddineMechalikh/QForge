// Create a runner class
package example.visualization;

public class RunVisualizations {
    public static void main(String[] args) {
        try {
            System.out.println("Starting Quantum Visualization Suite...\n");
            CompleteVisualizationExample.main(args);
            System.out.println("\nVisualization suite completed successfully!");
            System.out.println("Check the 'output/quantum_viz/' directory for all generated files.");
        } catch (Exception e) {
            System.err.println("Visualization failed: " + e.getMessage());
            e.printStackTrace();
        }
    }
}