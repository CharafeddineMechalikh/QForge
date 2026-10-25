package com.quantum.simulator.noise;

import java.util.Locale;
import com.quantum.simulator.state.Qubit;
import com.quantum.simulator.math.ComplexMatrix;

public class DephasingNoise extends NoiseModel {
    
    public DephasingNoise(double probability, Qubit... targetQubits) {
        super("DephasingNoise", probability, targetQubits);
    }
    
    @Override
    public ComplexMatrix[] getKrausOperators(int numQubits) {
        if (numQubits != targetQubits.length) {
            throw new IllegalArgumentException(
                String.format(Locale.ROOT, "Expected %d qubits, got %d", targetQubits.length, numQubits)
            );
        }
        
        ComplexMatrix[] singleQubit = {
            ComplexMatrix.identity(2).scale(Math.sqrt(1.0 - probability / 2.0)),
            ComplexMatrix.pauliZ().scale(Math.sqrt(probability / 2.0))
        };
        
        return independentKraus(singleQubit, numQubits);
    }
    
    @Override
    public NoiseModel withProbability(double newProbability) {
        return new DephasingNoise(newProbability, targetQubits);
    }
    
    @Override
    public NoiseModel withTargetQubits(Qubit[] newTargetQubits) {
        return new DephasingNoise(probability, newTargetQubits);
    }
    
    public static DephasingNoise createSingleQubit(double probability, Qubit target) {
        return new DephasingNoise(probability, target);
    }
}