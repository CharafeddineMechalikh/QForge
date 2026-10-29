package com.mechalikh.qforge;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.mechalikh.qforge.math.Complex;
import com.mechalikh.qforge.math.ComplexMatrix;
import com.mechalikh.qforge.operations.GateLibrary;
import com.mechalikh.qforge.operations.QuantumGate;
import com.mechalikh.qforge.state.Qubit;
import com.mechalikh.qforge.state.StateVector;
import com.mechalikh.qforge.state.StabilizerState;
import com.mechalikh.qforge.strategy.DensityMatrixStrategy;
import com.mechalikh.qforge.strategy.StateVectorStrategy;
import com.mechalikh.qforge.strategy.StabilizerStrategy;
import com.mechalikh.qforge.strategy.TensorNetworkStrategy;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import org.junit.jupiter.api.Test;

/**
 * Cross-checks the backends against each other and against closed-form results.
 *
 * <p>The comparisons use exact probabilities rather than sampled counts, so a failure
 * points at a state representation error rather than at statistical noise.</p>
 */
class CrossBackendValidationTest {
    private static final double TOLERANCE = 1e-9;
    private static final GateLibrary LIBRARY = new GateLibrary();

    @Test
    void randomCliffordCircuitsAgreeBetweenStateVectorAndStabilizer() {
        int numQubits = 4;
        String[] single = {"H", "S", "X", "Y", "Z"};
        for (int circuit = 0; circuit < 20; circuit++) {
            Random random = new Random(1000L + circuit);
            List<QuantumGate> gates = new ArrayList<>();
            for (int depth = 0; depth < 30; depth++) {
                if (random.nextBoolean()) {
                    Qubit qubit = qubit(random.nextInt(numQubits));
                    gates.add(LIBRARY.createGate(single[random.nextInt(single.length)], qubit));
                } else {
                    int first = random.nextInt(numQubits);
                    int second = random.nextInt(numQubits - 1);
                    if (second >= first) {
                        second++;
                    }
                    gates.add(LIBRARY.createGate("CNOT", qubit(first), qubit(second)));
                }
            }

            StateVector vector = StateVector.createZeroState(numQubits);
            StabilizerState stabilizer = new StabilizerState(numQubits);
            StateVectorStrategy vectorStrategy = new StateVectorStrategy();
            StabilizerStrategy stabilizerStrategy = new StabilizerStrategy();
            for (QuantumGate gate : gates) {
                vector = (StateVector) vectorStrategy.applyGate(
                    vector, gate, gate.getTargetQubits());
                stabilizer = (StabilizerState) stabilizerStrategy.applyGate(
                    stabilizer, gate, gate.getTargetQubits());
            }

            double[] exact = vector.getProbabilities();
            double[] tableau = stabilizer.outcomeProbabilitiesZ(allQubits(numQubits));
            for (int index = 0; index < exact.length; index++) {
                assertEquals(exact[index], tableau[index], TOLERANCE,
                    "circuit " + circuit + " outcome " + index);
            }
        }
    }

    @Test
    void stateVectorDensityMatrixAndMpsAgreeOnNonCliffordCircuits() {
        int numQubits = 4;
        StateVectorStrategy vectorStrategy = new StateVectorStrategy();
        DensityMatrixStrategy densityStrategy = new DensityMatrixStrategy();
        TensorNetworkStrategy tensorStrategy = new TensorNetworkStrategy(64);
        String[] single = {"H", "S", "T", "X", "Y", "Z"};

        for (int circuit = 0; circuit < 5; circuit++) {
            Random random = new Random(2000L + circuit);
            List<QuantumGate> gates = new ArrayList<>();
            for (int depth = 0; depth < 12; depth++) {
                Qubit qubit = qubit(random.nextInt(numQubits));
                gates.add(LIBRARY.createGate(single[random.nextInt(single.length)], qubit));
                if (random.nextInt(3) == 0) {
                    int first = random.nextInt(numQubits);
                    int second = random.nextInt(numQubits - 1);
                    if (second >= first) {
                        second++;
                    }
                    gates.add(LIBRARY.createGate("CNOT", qubit(first), qubit(second)));
                }
            }

            StateVector vector = StateVector.createZeroState(numQubits);
            com.mechalikh.qforge.state.QuantumState density =
                densityStrategy.createInitialState(numQubits);
            com.mechalikh.qforge.state.QuantumState tensor =
                tensorStrategy.createInitialState(numQubits);
            for (QuantumGate gate : gates) {
                vector = (StateVector) vectorStrategy.applyGate(
                    vector, gate, gate.getTargetQubits());
                density = densityStrategy.applyGate(density, gate, gate.getTargetQubits());
                tensor = tensorStrategy.applyGate(tensor, gate, gate.getTargetQubits());
            }

            double[] exact = vector.getProbabilities();
            double[] mixed = density.getProbabilities();
            double[] compressed = tensor.getProbabilities();
            for (int index = 0; index < exact.length; index++) {
                assertEquals(exact[index], mixed[index], TOLERANCE,
                    "density matrix differs at " + index);
                assertEquals(exact[index], compressed[index], TOLERANCE,
                    "tensor network differs at " + index);
            }
        }
    }

    @Test
    void ghzStateHasTwoEqualBranches() {
        int numQubits = 5;
        StateVector vector = StateVector.createZeroState(numQubits);
        StateVectorStrategy strategy = new StateVectorStrategy();
        vector = (StateVector) strategy.applyGate(
            vector, LIBRARY.createGate("H", qubit(0)), new Qubit[]{qubit(0)});
        for (int index = 1; index < numQubits; index++) {
            vector = (StateVector) strategy.applyGate(vector,
                LIBRARY.createGate("CNOT", qubit(index - 1), qubit(index)),
                new Qubit[]{qubit(index)});
        }
        double[] probabilities = vector.getProbabilities();
        assertEquals(0.5, probabilities[0], TOLERANCE);
        assertEquals(0.5, probabilities[(1 << numQubits) - 1], TOLERANCE);
        for (int index = 1; index < probabilities.length - 1; index++) {
            assertEquals(0.0, probabilities[index], TOLERANCE);
        }
    }

    @Test
    void wStatePreparationMatchesTheAnalyticAmplitudes() {
        int numQubits = 3;
        ComplexMatrix matrix = householder(stateVector(1 << numQubits, new int[]{1, 2, 4}));
        QuantumGate preparation = new QuantumGate.CustomGate(
            "WPreparation", matrix,
            new Qubit[]{qubit(0), qubit(1), qubit(2)}, new Qubit[0], 1.0);

        StateVector vector = (StateVector) new StateVectorStrategy().applyGate(
            StateVector.createZeroState(numQubits), preparation,
            preparation.getTargetQubits());
        double expected = 1.0 / 3.0;
        for (int basis : new int[]{1, 2, 4}) {
            assertEquals(expected, vector.getProbability(basis), TOLERANCE);
        }
        assertEquals(0.0, vector.getProbability(0), TOLERANCE);
        assertEquals(0.0, vector.getProbability(3), TOLERANCE);
        assertEquals(0.0, vector.getProbability(5), TOLERANCE);
        assertEquals(0.0, vector.getProbability(6), TOLERANCE);
        assertEquals(0.0, vector.getProbability(7), TOLERANCE);
    }

    @Test
    void qftOnABasisStateMatchesTheFourierDefinition() {
        int numQubits = 3;
        int basis = 5;
        StateVector vector = StateVector.createZeroState(numQubits);
        StateVectorStrategy strategy = new StateVectorStrategy();
        for (int index = 0; index < numQubits; index++) {
            if (((basis >> (numQubits - 1 - index)) & 1) != 0) {
                vector = (StateVector) strategy.applyGate(
                    vector, LIBRARY.createGate("X", qubit(index)), new Qubit[]{qubit(index)});
            }
        }
        for (int first = 0; first < numQubits; first++) {
            vector = (StateVector) strategy.applyGate(
                vector, LIBRARY.createGate("H", qubit(first)), new Qubit[]{qubit(first)});
            for (int second = first + 1; second < numQubits; second++) {
                double angle = Math.PI / Math.pow(2.0, second - first);
                vector = (StateVector) strategy.applyGate(vector,
                    LIBRARY.createGate("CPHASE", Map.of("angle", angle),
                        qubit(first), qubit(second)),
                    new Qubit[]{qubit(second)});
            }
        }
        vector = (StateVector) strategy.applyGate(vector,
            LIBRARY.createGate("SWAP", qubit(0), qubit(numQubits - 1)),
            new Qubit[]{qubit(numQubits - 1)});

        int size = 1 << numQubits;
        for (int outcome = 0; outcome < size; outcome++) {
            double phase = 2.0 * Math.PI * basis * outcome / size;
            assertEquals(Math.cos(phase) / Math.sqrt(size),
                vector.getAmplitude(outcome).real(), TOLERANCE);
            assertEquals(Math.sin(phase) / Math.sqrt(size),
                vector.getAmplitude(outcome).imag(), TOLERANCE);
        }
    }

    @Test
    void groverOnEightStatesMatchesTheAmplitudeFormula() {
        int numQubits = 3;
        int target = 5;
        double theta = Math.asin(1.0 / Math.sqrt(1 << numQubits));
        StateVector vector = StateVector.createZeroState(numQubits);
        StateVectorStrategy strategy = new StateVectorStrategy();
        for (int index = 0; index < numQubits; index++) {
            vector = (StateVector) strategy.applyGate(
                vector, LIBRARY.createGate("H", qubit(index)), new Qubit[]{qubit(index)});
        }
        for (int iteration = 0; iteration < 2; iteration++) {
            for (int index = 0; index < numQubits; index++) {
                if (((target >> (numQubits - 1 - index)) & 1) == 0) {
                    vector = (StateVector) strategy.applyGate(vector,
                        LIBRARY.createGate("X", qubit(index)), new Qubit[]{qubit(index)});
                }
            }
            vector = applyCcz(vector, strategy);
            for (int index = 0; index < numQubits; index++) {
                if (((target >> (numQubits - 1 - index)) & 1) == 0) {
                    vector = (StateVector) strategy.applyGate(vector,
                        LIBRARY.createGate("X", qubit(index)), new Qubit[]{qubit(index)});
                }
            }
            for (int index = 0; index < numQubits; index++) {
                vector = (StateVector) strategy.applyGate(
                    vector, LIBRARY.createGate("H", qubit(index)), new Qubit[]{qubit(index)});
                vector = (StateVector) strategy.applyGate(
                    vector, LIBRARY.createGate("X", qubit(index)), new Qubit[]{qubit(index)});
            }
            vector = applyCcz(vector, strategy);
            for (int index = 0; index < numQubits; index++) {
                vector = (StateVector) strategy.applyGate(
                    vector, LIBRARY.createGate("X", qubit(index)), new Qubit[]{qubit(index)});
                vector = (StateVector) strategy.applyGate(
                    vector, LIBRARY.createGate("H", qubit(index)), new Qubit[]{qubit(index)});
            }
        }

        double expected = Math.pow(Math.sin((2 * 2 + 1) * theta), 2);
        assertEquals(expected, vector.getProbability(target), 1e-9);
        assertTrue(vector.getProbability(target) > 0.94,
            String.format(Locale.ROOT, "probability %.4f", vector.getProbability(target)));
    }

    private static StateVector applyCcz(StateVector vector, StateVectorStrategy strategy) {
        vector = (StateVector) strategy.applyGate(
            vector, LIBRARY.createGate("H", qubit(2)), new Qubit[]{qubit(2)});
        vector = (StateVector) strategy.applyGate(vector,
            LIBRARY.createGate("Toffoli", qubit(0), qubit(1), qubit(2)),
            new Qubit[]{qubit(2)});
        return (StateVector) strategy.applyGate(
            vector, LIBRARY.createGate("H", qubit(2)), new Qubit[]{qubit(2)});
    }

    private static ComplexMatrix householder(Complex[] target) {
        int size = target.length;
        Complex[] difference = new Complex[size];
        double normSquared = 0.0;
        for (int index = 0; index < size; index++) {
            Complex base = index == 0 ? Complex.ONE : Complex.ZERO;
            difference[index] = base.subtract(target[index]);
            normSquared += difference[index].absSquare();
        }
        double norm = Math.sqrt(normSquared);
        ComplexMatrix matrix = new ComplexMatrix(size, size);
        for (int row = 0; row < size; row++) {
            for (int column = 0; column < size; column++) {
                double element = row == column ? 1.0 : 0.0;
                element -= 2.0 * (difference[row].real() / norm)
                    * (difference[column].real() / norm);
                matrix.set(row, column, new Complex(element, 0.0));
            }
        }
        return matrix;
    }

    private static Complex[] stateVector(int size, int[] nonZero) {
        Complex[] state = new Complex[size];
        double amplitude = 1.0 / Math.sqrt(nonZero.length);
        for (int index = 0; index < size; index++) {
            state[index] = Complex.ZERO;
        }
        for (int index : nonZero) {
            state[index] = new Complex(amplitude, 0.0);
        }
        return state;
    }

    private static Qubit[] allQubits(int count) {
        Qubit[] qubits = new Qubit[count];
        for (int index = 0; index < count; index++) {
            qubits[index] = qubit(index);
        }
        return qubits;
    }

    private static Qubit qubit(int index) {
        return new Qubit(index, "q" + index);
    }
}
