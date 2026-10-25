package com.quantum.simulator.noise;

import java.util.Locale;
import com.quantum.simulator.state.Qubit;
import com.quantum.simulator.math.Complex;
import com.quantum.simulator.math.ComplexMatrix;

public class AmplitudeDampingNoise extends NoiseModel {
    private final double gamma; // Damping parameter
    
    public AmplitudeDampingNoise(double gamma, Qubit... targetQubits) {
        super("AmplitudeDampingNoise", gamma, targetQubits); // Using gamma as probability for compatibility
        if (gamma < 0.0 || gamma > 1.0) {
            throw new IllegalArgumentException("Damping parameter gamma must be between 0 and 1");
        }
        this.gamma = gamma;
    }
    
    @Override
    public ComplexMatrix[] getKrausOperators(int numQubits) {
        if (numQubits != targetQubits.length) {
            throw new IllegalArgumentException(
                String.format(Locale.ROOT, "Expected %d qubits, got %d", targetQubits.length, numQubits)
            );
        }
        
        ComplexMatrix[] singleQubit = new ComplexMatrix[2];
        
        // K0 = |0><0| + √(1-γ)|1><1|
        singleQubit[0] = new ComplexMatrix(2, 2);
        singleQubit[0].set(0, 0, Complex.ONE);
        singleQubit[0].set(1, 1, new Complex(Math.sqrt(1.0 - gamma), 0));
        
        // K1 = √γ|0><1|
        singleQubit[1] = new ComplexMatrix(2, 2);
        singleQubit[1].set(0, 1, new Complex(Math.sqrt(gamma), 0));
        
        return independentKraus(singleQubit, numQubits);
    }
    
    @Override
    public NoiseModel withProbability(double newProbability) {
        return new AmplitudeDampingNoise(newProbability, targetQubits);
    }
    
    @Override
    public NoiseModel withTargetQubits(Qubit[] newTargetQubits) {
        return new AmplitudeDampingNoise(gamma, newTargetQubits);
    }
    
    public double getGamma() {
        return gamma;
    }
    
    public static AmplitudeDampingNoise create(double gamma, Qubit target) {
        return new AmplitudeDampingNoise(gamma, target);
    }
}