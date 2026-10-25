package example;

import com.quantum.simulator.state.StateVector;
import com.quantum.simulator.math.Complex;

public class SimpleStateTest {
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