package example;

import com.quantum.simulator.state.StateVector;
import com.quantum.simulator.math.Complex;

/**
 * Prints a few basic facts about {@link StateVector} instances.
 *
 * <p>The program creates the zero state, then a state with both amplitudes set to one. Because
 * the constructor normalizes its input, the printed probabilities of the second state are 0.5
 * each. It writes no files.</p>
 */
public class SimpleStateTest {
    /**
     * Creates the two states and prints their amplitudes, probabilities, and the probability sum.
     *
     * <p>No command line arguments are read and no results are checked.</p>
     *
     * @param args ignored
     */
    public static void main(String[] args) {
        System.out.println("=== Simple State Test ===");
        
        // Test 1: Create and display a state
        Complex[] amps = new Complex[2];
        amps[0] = Complex.ONE;  // |0>
        amps[1] = Complex.ZERO; // |1>
        
        StateVector state = new StateVector(amps, 1);
        System.out.println("Initial |0> state: " + state);
        System.out.println("Probability |0>: " + state.getProbability(0));
        System.out.println("Probability |1>: " + state.getProbability(1));
        
        // Test normalization
        Complex[] amps2 = new Complex[2];
        amps2[0] = new Complex(1, 0);
        amps2[1] = new Complex(1, 0);
        
        StateVector unnormalized = new StateVector(amps2, 1);
        System.out.println("\nUnnormalized (|0> + |1>): " + unnormalized);
        System.out.println("After normalization:");
        System.out.println("Probability |0>: " + unnormalized.getProbability(0));
        System.out.println("Probability |1>: " + unnormalized.getProbability(1));
        
        // Check if probabilities sum to 1
        double[] probs = unnormalized.getProbabilities();
        double sum = 0;
        for (double p : probs) sum += p;
        System.out.println("Sum of probabilities: " + sum);
    }
}