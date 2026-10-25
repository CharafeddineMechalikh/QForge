package com.quantum.simulator.noise;

import java.util.Locale;
import com.quantum.simulator.operations.QuantumOperation;
import com.quantum.simulator.state.QuantumState;
import com.quantum.simulator.state.Qubit;
import com.quantum.simulator.math.ComplexMatrix;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public abstract class NoiseModel implements QuantumOperation {
    protected final String name;
    protected final double probability;
    protected final Qubit[] targetQubits;
    
    protected NoiseModel(String name, double probability, Qubit[] targetQubits) {
        if (probability < 0.0 || probability > 1.0) {
            throw new IllegalArgumentException("Noise probability must be between 0 and 1");
        }
        
        this.name = name;
        this.probability = probability;
        this.targetQubits = targetQubits.clone();
    }
    
    @Override
    public QuantumState apply(QuantumState state) {
        if (targetQubits.length == 0) {
            throw new IllegalStateException(
                "Noise model '" + name + "' has no target qubits; assign targets before applying");
        }
        int[] targets = new int[targetQubits.length];
        for (int i = 0; i < targetQubits.length; i++) {
            targets[i] = targetQubits[i].getIndex();
        }
        return state.applyKrausOperators(getKrausOperators(targetQubits.length), targets);
    }
    
    protected static ComplexMatrix[] independentKraus(ComplexMatrix[] singleQubitKraus, int numQubits) {
        if (numQubits > 8) {
            throw new UnsupportedOperationException(
                "Independent noise on more than 8 qubits produces too many Kraus operators");
        }
        List<ComplexMatrix> accumulated = new ArrayList<>();
        accumulated.add(ComplexMatrix.identity(1));
        
        for (int q = 0; q < numQubits; q++) {
            List<ComplexMatrix> next = new ArrayList<>();
            for (ComplexMatrix existing : accumulated) {
                for (ComplexMatrix single : singleQubitKraus) {
                    next.add(existing.tensorProduct(single));
                }
            }
            accumulated = next;
        }
        
        return accumulated.toArray(new ComplexMatrix[0]);
    }
    
    @Override
    public String getName() {
        return name;
    }
    
    @Override
    public double getDuration() {
        return 0.0; // Noise application is instantaneous
    }
    
    @Override
    public Qubit[] getTargetQubits() {
        return targetQubits.clone();
    }
    
    @Override
    public Qubit[] getControlQubits() {
        return new Qubit[0]; // Noise doesn't have control qubits
    }
    
    @Override
    public boolean isUnitary() {
        return false;
    }
    
    @Override
    public boolean isNoise() {
        return true;
    }
    
    @Override
    public boolean isMeasurement() {
        return false;
    }
    
    @Override
    public QuantumOperation inverse() {
        throw new UnsupportedOperationException("Noise models don't have inverses");
    }
    
    public double getProbability() {
        return probability;
    }
    
    public abstract ComplexMatrix[] getKrausOperators(int numQubits);
    
    public abstract NoiseModel withProbability(double newProbability);
    
    public abstract NoiseModel withTargetQubits(Qubit[] newTargetQubits);
    
    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        
        NoiseModel other = (NoiseModel) obj;
        return name.equals(other.name) &&
               Math.abs(probability - other.probability) < 1e-10 &&
               Arrays.equals(targetQubits, other.targetQubits);
    }
    
    @Override
    public int hashCode() {
        int result = name.hashCode();
        result = 31 * result + Double.hashCode(probability);
        result = 31 * result + Arrays.hashCode(targetQubits);
        return result;
    }
    
    @Override
    public String toString() {
        return String.format(Locale.ROOT, "NoiseModel[name='%s', probability=%.4f, targets=%s]",
            name, probability, Arrays.toString(targetQubits));
    }
}