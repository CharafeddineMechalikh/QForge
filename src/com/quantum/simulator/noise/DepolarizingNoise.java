package com.quantum.simulator.noise;

import java.util.Locale;
import com.quantum.simulator.state.Qubit;
import com.quantum.simulator.math.ComplexMatrix;
import java.util.ArrayList;
import java.util.List;

public class DepolarizingNoise extends NoiseModel {
    
    public DepolarizingNoise(double probability, Qubit... targetQubits) {
        super("DepolarizingNoise", probability, targetQubits);
    }
    
    @Override
    public ComplexMatrix[] getKrausOperators(int numQubits) {
        if (numQubits != targetQubits.length) {
            throw new IllegalArgumentException(
                String.format(Locale.ROOT, "Expected %d qubits, got %d", targetQubits.length, numQubits)
            );
        }
        if (numQubits > 4) {
            throw new UnsupportedOperationException(
                "Depolarizing noise on more than 4 qubits requires "
                    + ((1 << (2 * numQubits)) - 1) + " Kraus operators");
        }
        
        int dim = 1 << numQubits;
        List<ComplexMatrix> krausOperators = new ArrayList<>();
        
        krausOperators.add(ComplexMatrix.identity(dim).scale(Math.sqrt(1.0 - probability)));
        
        int numPauliStrings = (1 << (2 * numQubits)) - 1;
        if (numPauliStrings > 0 && probability > 0.0) {
            double weight = Math.sqrt(probability / numPauliStrings);
            for (int code = 1; code < (1 << (2 * numQubits)); code++) {
                ComplexMatrix pauliString = ComplexMatrix.identity(1);
                for (int q = 0; q < numQubits; q++) {
                    pauliString = pauliString.tensorProduct(pauliMatrix((code >> (2 * q)) & 3));
                }
                krausOperators.add(pauliString.scale(weight));
            }
        }
        
        return krausOperators.toArray(new ComplexMatrix[0]);
    }
    
    private static ComplexMatrix pauliMatrix(int index) {
        switch (index) {
            case 0:
                return ComplexMatrix.identity(2);
            case 1:
                return ComplexMatrix.pauliX();
            case 2:
                return ComplexMatrix.pauliY();
            case 3:
                return ComplexMatrix.pauliZ();
            default:
                throw new IllegalArgumentException("Unknown Pauli index: " + index);
        }
    }
    
    @Override
    public NoiseModel withProbability(double newProbability) {
        return new DepolarizingNoise(newProbability, targetQubits);
    }
    
    @Override
    public NoiseModel withTargetQubits(Qubit[] newTargetQubits) {
        return new DepolarizingNoise(probability, newTargetQubits);
    }
    
    public static DepolarizingNoise createSingleQubit(double probability, Qubit target) {
        return new DepolarizingNoise(probability, target);
    }
    
    public static DepolarizingNoise createMultiQubit(double probability, Qubit... targets) {
        return new DepolarizingNoise(probability, targets);
    }
}