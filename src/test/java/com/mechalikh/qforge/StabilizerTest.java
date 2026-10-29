package com.mechalikh.qforge;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.mechalikh.qforge.math.Complex;
import com.mechalikh.qforge.math.ComplexMatrix;
import com.mechalikh.qforge.measurement.MeasurementResult;
import com.mechalikh.qforge.operations.MultiQubitGate;
import com.mechalikh.qforge.operations.QuantumGate;
import com.mechalikh.qforge.operations.SingleQubitGate;
import com.mechalikh.qforge.state.QuantumState;
import com.mechalikh.qforge.state.Qubit;
import com.mechalikh.qforge.state.StabilizerState;
import com.mechalikh.qforge.state.StateVector;
import com.mechalikh.qforge.strategy.StabilizerStrategy;
import com.mechalikh.qforge.strategy.StateVectorStrategy;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import org.junit.jupiter.api.Test;

class StabilizerTest {
    private static final double EPS = 1e-12;

    @Test
    void zeroStateHasDeterministicZeroOutcome() {
        StabilizerState state = new StabilizerState(3, new Random(1));
        Qubit[] targets = qubits(state, 3);

        int[] samples = state.sampleZ(targets, 500);
        for (int sample : samples) {
            assertEquals(0, sample);
        }

        double[] probabilities = state.outcomeProbabilitiesZ(targets);
        assertEquals(1.0, probabilities[0], EPS);
        for (int outcome = 1; outcome < probabilities.length; outcome++) {
            assertEquals(0.0, probabilities[outcome], EPS);
        }
    }

    @Test
    void bellStateProducesOnlyCorrelatedOutcomes() {
        StabilizerState state = bellState(7);
        Qubit[] targets = qubits(state, 2);
        int shots = 4000;

        int[] samples = state.sampleZ(targets, shots);
        int zeros = 0;
        int threes = 0;
        int other = 0;
        for (int sample : samples) {
            if (sample == 0) {
                zeros++;
            } else if (sample == 3) {
                threes++;
            } else {
                other++;
            }
        }

        assertEquals(0, other);
        assertTrue(Math.abs(zeros - shots / 2.0) < 0.1 * shots);
        assertTrue(Math.abs(threes - shots / 2.0) < 0.1 * shots);
    }

    @Test
    void ghzStateOverFiftyQubitsKeepsSampledBitsEqual() {
        int numQubits = 50;
        StabilizerState state = new StabilizerState(numQubits, new Random(11));
        state.applyGate(single("H", ComplexMatrix.hadamard(), state.getQubit(0)));
        for (int qubit = 0; qubit < numQubits - 1; qubit++) {
            state.applyGate(MultiQubitGate.createCNOT(
                state.getQubit(qubit), state.getQubit(qubit + 1)));
        }

        Qubit[] targets = new Qubit[32];
        for (int i = 0; i < 31; i++) {
            targets[i] = state.getQubit(i);
        }
        targets[31] = state.getQubit(numQubits - 1);

        int[] samples = state.sampleZ(targets, 4000);
        int allZero = 0;
        int allOne = 0;
        for (int sample : samples) {
            assertTrue(sample == 0 || sample == -1, "GHZ sample has unequal bits");
            if (sample == 0) {
                allZero++;
            } else {
                allOne++;
            }
        }
        assertTrue(allZero > 0);
        assertTrue(allOne > 0);
        assertEquals(numQubits, state.getNumQubits());
    }

    @Test
    void matchesStateVectorOnCliffordCircuit() {
        int numQubits = 5;
        StabilizerState stabilizer = new StabilizerState(numQubits, new Random(101));
        StateVector vector = StateVector.createZeroState(numQubits);
        StateVectorStrategy vectorStrategy = new StateVectorStrategy();
        Qubit[] stateQubits = qubits(stabilizer, numQubits);

        List<QuantumGate> circuit = new ArrayList<>();
        circuit.add(single("H", ComplexMatrix.hadamard(), stateQubits[0]));
        circuit.add(single("S", phaseMatrix(false), stateQubits[1]));
        circuit.add(MultiQubitGate.createCNOT(stateQubits[0], stateQubits[1]));
        circuit.add(MultiQubitGate.createCZ(stateQubits[1], stateQubits[2]));
        circuit.add(MultiQubitGate.createSWAP(stateQubits[2], stateQubits[3]));
        circuit.add(single("H", ComplexMatrix.hadamard(), stateQubits[3]));
        circuit.add(single("X", ComplexMatrix.pauliX(), stateQubits[0]));
        circuit.add(single("Y", ComplexMatrix.pauliY(), stateQubits[2]));
        circuit.add(single("SDG", phaseMatrix(true), stateQubits[1]));
        circuit.add(MultiQubitGate.createCNOT(stateQubits[4], stateQubits[0]));
        circuit.add(MultiQubitGate.createCZ(stateQubits[0], stateQubits[4]));

        for (QuantumGate gate : circuit) {
            stabilizer.applyGate(gate);
            vector = (StateVector) vectorStrategy.applyGate(vector, gate, null);
        }

        Qubit[] targets = TestSupport.qubits(numQubits);
        int shots = 20000;
        MeasurementResult stabilizerResult =
            new StabilizerStrategy().measure(stabilizer, targets, shots);
        MeasurementResult vectorResult = vectorStrategy.measure(vector, targets, shots);

        double[] stabilizerProbabilities = stabilizerResult.getOutcomeProbabilities();
        double[] vectorProbabilities = vectorResult.getOutcomeProbabilities();
        assertEquals(vectorProbabilities.length, stabilizerProbabilities.length);
        for (int outcome = 0; outcome < vectorProbabilities.length; outcome++) {
            assertEquals(vectorProbabilities[outcome], stabilizerProbabilities[outcome], 1e-9);
        }

        Map<Integer, Integer> stabilizerCounts = stabilizerResult.getOutcomeCounts();
        Map<Integer, Integer> vectorCounts = vectorResult.getOutcomeCounts();
        for (int outcome = 0; outcome < vectorProbabilities.length; outcome++) {
            double stabilizerFrequency =
                stabilizerCounts.getOrDefault(outcome, 0) / (double) shots;
            double vectorFrequency = vectorCounts.getOrDefault(outcome, 0) / (double) shots;
            assertEquals(vectorFrequency, stabilizerFrequency, 0.04);
        }
    }

    @Test
    void forcedCollapseProducesRequestedOutcome() {
        StabilizerState bell = bellState(13);
        Qubit[] targets = qubits(bell, 2);

        StabilizerState collapsed = (StabilizerState) bell.collapseZ(targets, 2);
        double[] collapsedProbabilities = collapsed.outcomeProbabilitiesZ(targets);
        assertEquals(1.0, collapsedProbabilities[2], EPS);

        double[] originalProbabilities = bell.outcomeProbabilitiesZ(targets);
        assertEquals(0.5, originalProbabilities[0], EPS);
        assertEquals(0.5, originalProbabilities[3], EPS);

        StabilizerState viaIndices = (StabilizerState) bell.collapseToBasisState(new int[]{0, 1}, 1);
        double[] indexProbabilities = viaIndices.outcomeProbabilitiesZ(targets);
        assertEquals(1.0, indexProbabilities[1], EPS);

        StabilizerState zero = new StabilizerState(1, new Random(14));
        StabilizerState forcedOne = (StabilizerState) zero.collapseToBasisState(new int[]{0}, 1);
        double[] forcedOneProbabilities =
            forcedOne.outcomeProbabilitiesZ(new Qubit[]{forcedOne.getQubit(0)});
        assertEquals(1.0, forcedOneProbabilities[1], EPS);
    }

    @Test
    void nonCliffordGateIsRejected() {
        StabilizerState state = new StabilizerState(1, new Random(15));
        ComplexMatrix tMatrix = diagonal(
            Complex.ONE,
            new Complex(Math.cos(Math.PI / 4), Math.sin(Math.PI / 4)));
        SingleQubitGate tGate = new SingleQubitGate("T", tMatrix, state.getQubit(0));

        UnsupportedOperationException exception =
            assertThrows(UnsupportedOperationException.class, () -> state.applyGate(tGate));
        assertTrue(exception.getMessage().contains("T"));
        assertTrue(exception.getMessage().toLowerCase().contains("clifford"));
    }

    @Test
    void tensorProductConcatenatesTableaus() {
        StabilizerState left = new StabilizerState(1, new Random(16));
        StabilizerState right = new StabilizerState(1, new Random(17));
        right.applyGate(single("X", ComplexMatrix.pauliX(), right.getQubit(0)));

        StabilizerState product = (StabilizerState) left.tensorProduct(right);
        assertEquals(2, product.getNumQubits());
        double[] probabilities = product.outcomeProbabilitiesZ(qubits(product, 2));
        assertEquals(1.0, probabilities[1], EPS);

        assertThrows(IllegalArgumentException.class,
            () -> left.tensorProduct(StateVector.createZeroState(1)));
    }

    @Test
    void copyIsIndependent() {
        StabilizerState original = new StabilizerState(1, new Random(18));
        StabilizerState copy = original.copy();

        assertNotSame(original, copy);
        assertEquals(original, copy);
        assertEquals(original.hashCode(), copy.hashCode());

        copy.applyGate(single("X", ComplexMatrix.pauliX(), copy.getQubit(0)));
        assertNotEquals(original, copy);
        double[] originalProbabilities =
            original.outcomeProbabilitiesZ(new Qubit[]{original.getQubit(0)});
        double[] copyProbabilities = copy.outcomeProbabilitiesZ(new Qubit[]{copy.getQubit(0)});
        assertEquals(1.0, originalProbabilities[0], EPS);
        assertEquals(1.0, copyProbabilities[1], EPS);
    }

    @Test
    void outcomeProbabilitiesOfBellPairAreExact() {
        StabilizerState bell = bellState(19);
        double[] probabilities = bell.outcomeProbabilitiesZ(qubits(bell, 2));
        assertEquals(4, probabilities.length);
        assertEquals(0.5, probabilities[0], EPS);
        assertEquals(0.0, probabilities[1], EPS);
        assertEquals(0.0, probabilities[2], EPS);
        assertEquals(0.5, probabilities[3], EPS);
    }

    @Test
    void strategyReportsStabilizerCapabilities() {
        StabilizerStrategy strategy = new StabilizerStrategy();
        assertEquals("StabilizerSimulator", strategy.getStrategyName());
        assertEquals(2000, strategy.getMaxSupportedQubits());
        assertFalse(strategy.canSimulateNoise());

        QuantumState state = strategy.createInitialState(2);
        assertTrue(state instanceof StabilizerState);
        QuantumState cloned = strategy.cloneState(state);
        assertNotSame(state, cloned);
        assertEquals(state, cloned);

        assertThrows(UnsupportedOperationException.class, () -> strategy.calculateEntropy(state));
        assertThrows(UnsupportedOperationException.class,
            () -> strategy.calculateFidelity(state, cloned));
        assertThrows(UnsupportedOperationException.class,
            () -> strategy.applyNoise(state, null, qubits((StabilizerState) state, 1)));
    }

    private static StabilizerState bellState(long seed) {
        StabilizerState state = new StabilizerState(2, new Random(seed));
        state.applyGate(single("H", ComplexMatrix.hadamard(), state.getQubit(0)));
        state.applyGate(MultiQubitGate.createCNOT(state.getQubit(0), state.getQubit(1)));
        return state;
    }

    private static Qubit[] qubits(StabilizerState state, int count) {
        Qubit[] qubits = new Qubit[count];
        for (int i = 0; i < count; i++) {
            qubits[i] = state.getQubit(i);
        }
        return qubits;
    }

    private static SingleQubitGate single(String name, ComplexMatrix matrix, Qubit target) {
        return new SingleQubitGate(name, matrix, target);
    }

    private static ComplexMatrix phaseMatrix(boolean dagger) {
        return diagonal(Complex.ONE, new Complex(0, dagger ? -1 : 1));
    }

    private static ComplexMatrix diagonal(Complex first, Complex second) {
        ComplexMatrix matrix = new ComplexMatrix(2, 2);
        matrix.set(0, 0, first);
        matrix.set(1, 1, second);
        return matrix;
    }
}
