// Create a runner class
package example.visualization;

/**
 * Entry point that runs {@link CompleteVisualizationExample} and reports its outcome.
 */
public class RunVisualizations {
    /**
     * Delegates to the visualization example.
     *
     * <p>Arguments are passed through to {@code CompleteVisualizationExample.main}. On success the
     * method prints a completion line pointing at {@code output/quantum_viz/}; on failure it prints
     * the error message and a stack trace.</p>
     *
     * @param args passed unchanged to the visualization example
     */
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