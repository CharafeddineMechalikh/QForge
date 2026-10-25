package com.quantum.simulator.measurement;

import java.util.Locale;
import com.quantum.simulator.state.DensityMatrix;
import com.quantum.simulator.state.QuantumState;
import com.quantum.simulator.state.Qubit;
import com.quantum.simulator.math.Complex;
import com.quantum.simulator.math.ComplexMatrix;
import com.quantum.simulator.math.MatrixOperations;
import java.util.Arrays;
import java.util.Random;

public class Observable {
    private final String name;
    private final ComplexMatrix operator;
    private final Qubit[] targetQubits;
    private final ObservableType type;
    
    public Observable(String name, ComplexMatrix operator, Qubit[] targetQubits, ObservableType type) {
        this.name = name;
        this.operator = operator;
        this.targetQubits = targetQubits.clone();
        this.type = type;
        validateObservable();
    }
    
    private void validateObservable() {
        if (!operator.isHermitian()) {
            throw new IllegalArgumentException("Observable operator must be Hermitian");
        }
        
        int expectedDim = 1 << targetQubits.length;
        if (operator.rows() != expectedDim || operator.cols() != expectedDim) {
            throw new IllegalArgumentException(
                String.format(Locale.ROOT, "Operator dimension %dx%d does not match %d target qubits (expected %dx%d)",
                    operator.rows(), operator.cols(), targetQubits.length, expectedDim, expectedDim)
            );
        }
    }
    
    public double measure(QuantumState state) {
        double[] probabilities = getOutcomeProbabilities(state);
        double[] eigenvalues = getOutcomeEigenvalues();
        double expectation = 0.0;
        for (int i = 0; i < probabilities.length; i++) {
            expectation += probabilities[i] * eigenvalues[i];
        }
        return expectation;
    }
    
    public MeasurementResult measureMultiple(QuantumState state, int shots) {
        double[] probabilities = getOutcomeProbabilities(state);
        int[] outcomes = sampleMeasurementOutcomes(probabilities, shots);
        return new MeasurementResult(name, targetQubits, outcomes, probabilities, getOutcomeEigenvalues());
    }
    
    public double[] getOutcomeProbabilities(QuantumState state) {
        int[] targets = targetIndices();
        int numStateQubits = state.getNumQubits();
        for (int target : targets) {
            if (target < 0 || target >= numStateQubits) {
                throw new IllegalArgumentException("Observable targets qubit " + target
                    + " outside a " + numStateQubits + "-qubit state");
            }
        }
        
        int numOutcomes = 1 << targets.length;
        double[] probabilities = new double[numOutcomes];
        
        if (isComputationalBasis()) {
            int stateDimension = 1 << numStateQubits;
            for (int basisState = 0; basisState < stateDimension; basisState++) {
                int outcome = extractTargetBits(basisState, targets, numStateQubits);
                probabilities[outcome] += state.getProbability(basisState);
            }
        } else {
            DensityMatrix reduced = (DensityMatrix) state.partialTrace(targets);
            ComplexMatrix reducedMatrix = reduced.getMatrix();
            ComplexMatrix eigenvectors = operator.eigenDecomposition()[1];
            for (int i = 0; i < numOutcomes; i++) {
                Complex value = Complex.ZERO;
                for (int row = 0; row < numOutcomes; row++) {
                    for (int col = 0; col < numOutcomes; col++) {
                        value = value.add(eigenvectors.get(row, i).conjugate()
                            .multiply(reducedMatrix.get(row, col))
                            .multiply(eigenvectors.get(col, i)));
                    }
                }
                probabilities[i] = Math.max(0.0, value.real());
            }
        }
        
        normalizeProbabilities(probabilities);
        return probabilities;
    }
    
    public double[] getOutcomeEigenvalues() {
        int numOutcomes = 1 << targetQubits.length;
        if (isComputationalBasis()) {
            double[] eigenvalues = new double[numOutcomes];
            for (int i = 0; i < numOutcomes; i++) {
                eigenvalues[i] = operator.get(i, i).real();
            }
            return eigenvalues;
        }
        double[] eigenvalues = operator.eigenvalues();
        if (eigenvalues.length != numOutcomes) {
            throw new IllegalStateException("Eigenvalue count does not match observable dimension");
        }
        return eigenvalues;
    }
    
    public QuantumState collapse(QuantumState state, int outcome) {
        int[] targets = targetIndices();
        int numOutcomes = 1 << targets.length;
        if (outcome < 0 || outcome >= numOutcomes) {
            throw new IllegalArgumentException("Measurement outcome out of range: " + outcome);
        }
        
        if (isComputationalBasis()) {
            return state.collapseToBasisState(targets, outcome);
        }
        
        ComplexMatrix eigenvectors = operator.eigenDecomposition()[1];
        Complex[] eigenvector = new Complex[numOutcomes];
        for (int row = 0; row < numOutcomes; row++) {
            eigenvector[row] = eigenvectors.get(row, outcome);
        }
        return state.collapseToEigenvector(targets, eigenvector);
    }
    
    public int sampleOutcome(QuantumState state) {
        return sampleMeasurementOutcomes(getOutcomeProbabilities(state), 1)[0];
    }
    
    int[] sampleOutcomes(double[] probabilities, int shots) {
        return sampleMeasurementOutcomes(probabilities, shots);
    }
    
    private int[] sampleMeasurementOutcomes(double[] probabilities, int shots) {
        Random random = new Random();
        int[] outcomes = new int[shots];
        for (int i = 0; i < shots; i++) {
            double rand = random.nextDouble();
            double cumulative = 0.0;
            for (int outcome = 0; outcome < probabilities.length; outcome++) {
                cumulative += probabilities[outcome];
                if (rand <= cumulative + 1e-12) {
                    outcomes[i] = outcome;
                    break;
                }
            }
        }
        return outcomes;
    }
    
    private void normalizeProbabilities(double[] probabilities) {
        double total = 0.0;
        for (double probability : probabilities) {
            total += probability;
        }
        if (total <= 0.0) {
            throw new IllegalStateException("Observable produced no measurement probability");
        }
        for (int i = 0; i < probabilities.length; i++) {
            probabilities[i] /= total;
        }
    }
    
    private int[] targetIndices() {
        int[] indices = new int[targetQubits.length];
        for (int i = 0; i < targetQubits.length; i++) {
            indices[i] = targetQubits[i].getIndex();
        }
        return indices;
    }
    
    private static int extractTargetBits(int basisState, int[] targets, int numQubits) {
        int outcome = 0;
        for (int t = 0; t < targets.length; t++) {
            int bit = (basisState >> (numQubits - 1 - targets[t])) & 1;
            outcome |= bit << (targets.length - 1 - t);
        }
        return outcome;
    }
    
    public boolean isComputationalBasis() {
        for (int row = 0; row < operator.rows(); row++) {
            for (int col = 0; col < operator.cols(); col++) {
                if (row != col && operator.get(row, col).abs() > 1e-12) {
                    return false;
                }
            }
        }
        return true;
    }
    
    
    
    public String getName() {
        return name;
    }
    
    public ComplexMatrix getOperator() {
        return operator.copy();
    }
    
    public Qubit[] getTargetQubits() {
        return targetQubits.clone();
    }
    
    public ObservableType getType() {
        return type;
    }
    
    public int getNumQubits() {
        return targetQubits.length;
    }
    
    
    
    
    public double[] getEigenvalues() {
        // Compute eigenvalues of the observable
        return operator.eigenvalues();
    }
    
    public ComplexMatrix[] getEigenvectors() {
        // Compute eigenvectors of the observable
        return operator.eigenDecomposition();
    }
    
   
    public Observable getProjector(int eigenvalueIndex) {
        // Get projector onto specific eigenspace
        double[] eigenvalues = getEigenvalues();
        if (eigenvalueIndex < 0 || eigenvalueIndex >= eigenvalues.length) {
            throw new IndexOutOfBoundsException("Eigenvalue index out of bounds");
        }
        
        ComplexMatrix[] eigendecomp = operator.eigenDecomposition();
        ComplexMatrix eigenvectors = eigendecomp[1];
        
        // Create projector matrix
        int dim = operator.rows();
        ComplexMatrix projector = new ComplexMatrix(dim, dim);
        
        // For simplicity, assume non-degenerate eigenvalues
        for (int i = 0; i < dim; i++) {
            for (int j = 0; j < dim; j++) {
                Complex element = eigenvectors.get(i, eigenvalueIndex)
                    .multiply(eigenvectors.get(j, eigenvalueIndex).conjugate());
                projector.set(i, j, element);
            }
        }
        
        return new Observable(
            name + "_Projector_" + eigenvalueIndex,
            projector,
            targetQubits,
            ObservableType.PROJECTOR
        );
    }
    
    public Observable tensorProduct(Observable other) {
        ComplexMatrix tensorOp = this.operator.tensorProduct(other.operator);
        
        Qubit[] combinedTargets = new Qubit[this.targetQubits.length + other.targetQubits.length];
        System.arraycopy(this.targetQubits, 0, combinedTargets, 0, this.targetQubits.length);
        System.arraycopy(other.targetQubits, 0, combinedTargets, this.targetQubits.length, other.targetQubits.length);
        
        return new Observable(
            this.name + "x" + other.name,
            tensorOp,
            combinedTargets,
            ObservableType.COMPOSITE
        );
    }
    
    public Observable partialTrace(int[] qubitsToKeep) {
        if (qubitsToKeep == null || qubitsToKeep.length == 0) {
            throw new IllegalArgumentException("At least one qubit must be kept");
        }
        
        boolean[] seen = new boolean[targetQubits.length];
        for (int index : qubitsToKeep) {
            if (index < 0 || index >= targetQubits.length) {
                throw new IllegalArgumentException("Qubit position out of range: " + index);
            }
            if (seen[index]) {
                throw new IllegalArgumentException("Duplicate qubit position: " + index);
            }
            seen[index] = true;
        }
        
        if (qubitsToKeep.length == targetQubits.length) {
            return this;
        }
        
        int[] keep = qubitsToKeep.clone();
        Arrays.sort(keep);
        ComplexMatrix reduced = MatrixOperations.partialTrace(
            operator, targetQubits.length, keep);
        Qubit[] reducedTargets = new Qubit[keep.length];
        for (int i = 0; i < keep.length; i++) {
            reducedTargets[i] = targetQubits[keep[i]];
        }
        
        return new Observable(name + "_partialTrace", reduced, reducedTargets, type);
    }
    
    // Factory methods for common observables
    public static Observable pauliX(Qubit qubit) {
        return new Observable(
            "X_" + qubit.getLabel(),
            ComplexMatrix.pauliX(),
            new Qubit[]{qubit},
            ObservableType.PAULI
        );
    }
    
    public static Observable pauliY(Qubit qubit) {
        return new Observable(
            "Y_" + qubit.getLabel(),
            ComplexMatrix.pauliY(),
            new Qubit[]{qubit},
            ObservableType.PAULI
        );
    }
    
    public static Observable pauliZ(Qubit qubit) {
        return new Observable(
            "Z_" + qubit.getLabel(),
            ComplexMatrix.pauliZ(),
            new Qubit[]{qubit},
            ObservableType.PAULI
        );
    }
 // MULTI-QUBIT VERSIONS (for tensor products)
    public static Observable pauliX(Qubit[] qubits) {
        if (qubits.length == 1) {
            return pauliX(qubits[0]);
        }
        Observable result = pauliX(qubits[0]);
        for (int i = 1; i < qubits.length; i++) {
            result = result.tensorProduct(pauliX(qubits[i]));
        }
        return result;
    }
    
    public static Observable pauliY(Qubit[] qubits) {
        if (qubits.length == 1) {
            return pauliY(qubits[0]);
        }
        Observable result = pauliY(qubits[0]);
        for (int i = 1; i < qubits.length; i++) {
            result = result.tensorProduct(pauliY(qubits[i]));
        }
        return result;
    }
    
    public static Observable pauliZ(Qubit[] qubits) {
        if (qubits.length == 1) {
            return pauliZ(qubits[0]);
        }
        Observable result = pauliZ(qubits[0]);
        for (int i = 1; i < qubits.length; i++) {
            result = result.tensorProduct(pauliZ(qubits[i]));
        }
        return result;
    }
    
    public static Observable identity(Qubit qubit) {
        return new Observable(
            "I_" + qubit.getLabel(),
            ComplexMatrix.identity(2),
            new Qubit[]{qubit},
            ObservableType.IDENTITY
        );
    }
    public static Observable identity(Qubit[] qubits) {
        int dim = 1 << qubits.length;
        return new Observable(
            "I_" + qubits.length,
            ComplexMatrix.identity(dim),
            qubits,
            ObservableType.IDENTITY
        );
    }
    
    public static Observable projector(String name, ComplexMatrix matrix, Qubit[] qubits) {
        return new Observable(name, matrix, qubits, ObservableType.PROJECTOR);
    }
    
    public static Observable fromString(String expr, Qubit[] qubits) {
        if (expr == null || qubits == null || qubits.length == 0) {
            throw new IllegalArgumentException("Expression and qubits are required");
        }
        
        String cleaned = expr.replaceAll("\\s+", "")
            .replace("⊗", "")
            .replace("*", "")
            .replace(",", "")
            .replace("x", "")
            .toUpperCase(Locale.ROOT);
        
        if (cleaned.length() != qubits.length) {
            throw new IllegalArgumentException(
                "Expression has " + cleaned.length() + " Pauli operators but "
                    + qubits.length + " qubits were provided");
        }
        
        Observable result = null;
        for (int i = 0; i < cleaned.length(); i++) {
            Observable term;
            switch (cleaned.charAt(i)) {
                case 'I':
                    term = identity(qubits[i]);
                    break;
                case 'X':
                    term = pauliX(qubits[i]);
                    break;
                case 'Y':
                    term = pauliY(qubits[i]);
                    break;
                case 'Z':
                    term = pauliZ(qubits[i]);
                    break;
                default:
                    throw new IllegalArgumentException(
                        "Unsupported Pauli operator: " + cleaned.charAt(i));
            }
            result = result == null ? term : result.tensorProduct(term);
        }
        
        return result;
    }
    
    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        
        Observable other = (Observable) obj;
        return name.equals(other.name) &&
               operator.equals(other.operator) &&
               Arrays.equals(targetQubits, other.targetQubits) &&
               type == other.type;
    }
    
    @Override
    public int hashCode() {
        int result = name.hashCode();
        result = 31 * result + operator.hashCode();
        result = 31 * result + Arrays.hashCode(targetQubits);
        result = 31 * result + type.hashCode();
        return result;
    }
    
    @Override
    public String toString() {
        return String.format(Locale.ROOT, 
            "Observable[name='%s', type=%s, qubits=%s]",
            name, type, Arrays.toString(targetQubits)
        );
    }
    
    // Observable types
    public enum ObservableType {
        PAULI("Pauli operator"),
        PROJECTOR("Projection operator"),
        HAMILTONIAN("Hamiltonian"),
        DENSITY("Density operator"),
        IDENTITY("Identity operator"),
        COMPOSITE("Composite observable"),
        CUSTOM("Custom observable");
        
        private final String description;
        
        ObservableType(String description) {
            this.description = description;
        }
        
        public String getDescription() {
            return description;
        }
    }
}