package com.mechalikh.qforge;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.mechalikh.qforge.math.Complex;
import com.mechalikh.qforge.math.ComplexMatrix;
import com.mechalikh.qforge.measurement.MeasurementResult;
import com.mechalikh.qforge.noise.DephasingNoise;
import com.mechalikh.qforge.operations.MultiQubitGate;
import com.mechalikh.qforge.operations.SingleQubitGate;
import com.mechalikh.qforge.state.QuantumState;
import com.mechalikh.qforge.state.Qubit;
import com.mechalikh.qforge.state.StabilizerState;
import com.mechalikh.qforge.state.StateVector;
import com.mechalikh.qforge.strategy.StabilizerStrategy;
import java.util.Random;
import org.junit.jupiter.api.Test;

/**
 * Additional branch coverage for the stabilizer state and strategy.
 */
class StabilizerBranchCoverageTest {
    private static final double EPS = 1e-12;

    private static Qubit[] qubits(StabilizerState state, int count) {
        Qubit[] targets = new Qubit[count];
        for (int i = 0; i < count; i++) {
            targets[i] = state.getQubit(i);
        }
        return targets;
    }

    private static SingleQubitGate gate(String name, Qubit target) {
        return new SingleQubitGate(name, ComplexMatrix.identity(2), target);
    }

    private static ComplexMatrix matrix(int dimension) {
        return ComplexMatrix.identity(dimension);
    }

    @Test
    void constructorAndBasicMetadataAreCovered() {
        assertThrows(IllegalArgumentException.class, () -> new StabilizerState(0));
        assertThrows(IllegalArgumentException.class, () -> new StabilizerState(-2));
        assertThrows(NullPointerException.class, () -> new StabilizerState(1, null));

        StabilizerState state = new StabilizerState(2, new Random(1));
        assertSame(state, state.normalize());
        assertTrue(state.isPure());
        assertEquals(1.0, state.getPurity(), EPS);
        assertEquals(2, state.getNumQubits());
        assertEquals("q1", state.getQubit(1).getLabel());
        assertEquals("StabilizerState[2 qubits, 2 stabilizer rows]", state.toString());

        SingleQubitGate hadamard = SingleQubitGate.createHadamard(state.getQubit(0));
        assertSame(state, state.applyOperation(hadamard));
        assertThrows(UnsupportedOperationException.class,
            () -> state.applyOperation(new DephasingNoise(0.1, state.getQubit(0))));
    }

    @Test
    void applyGateRejectsInvalidArguments() {
        StabilizerState state = new StabilizerState(3, new Random(2));
        Qubit q0 = state.getQubit(0);
        Qubit q1 = state.getQubit(1);
        Qubit q2 = state.getQubit(2);

        assertThrows(NullPointerException.class, () -> state.applyGate(null));
        assertThrows(UnsupportedOperationException.class,
            () -> state.applyGate(gate("T", q0)));
        assertThrows(UnsupportedOperationException.class,
            () -> state.applyGate(gate("rx", q0)));
        assertThrows(IllegalArgumentException.class,
            () -> state.applyGate(new MultiQubitGate("X", matrix(4),
                new Qubit[]{q0, q1})));
        assertThrows(IllegalArgumentException.class,
            () -> state.applyGate(gate("CZ", q0)));
        assertThrows(IllegalArgumentException.class,
            () -> state.applyGate(new MultiQubitGate("CZ", matrix(4),
                new Qubit[]{q0, q0})));
        assertThrows(IllegalArgumentException.class,
            () -> state.applyGate(gate("SWAP", q0)));
        assertThrows(IllegalArgumentException.class,
            () -> state.applyGate(new MultiQubitGate("SWAP", matrix(4),
                new Qubit[]{q0, q0})));
        assertThrows(IllegalArgumentException.class,
            () -> state.applyGate(gate("CX", q0)));
        assertThrows(IllegalArgumentException.class,
            () -> state.applyGate(MultiQubitGate.createCNOT(q0, q0)));
        assertThrows(IllegalArgumentException.class,
            () -> state.applyGate(gate("X", new Qubit(9))));
        assertThrows(IllegalArgumentException.class,
            () -> state.applyGate(new MultiQubitGate("CZ", matrix(4),
                new Qubit[]{q0, null})));
        assertThrows(IllegalArgumentException.class,
            () -> state.applyGate(new MultiQubitGate("CZ", matrix(8),
                new Qubit[]{q1, q2}, new Qubit[]{q0}, 1.0)));
        assertThrows(IllegalArgumentException.class,
            () -> state.applyGate(new MultiQubitGate("CZ", matrix(8),
                new Qubit[]{q2}, new Qubit[]{q0, q1}, 1.0)));
        assertThrows(IllegalArgumentException.class,
            () -> state.applyGate(new MultiQubitGate("SWAP", matrix(8),
                new Qubit[]{q2}, new Qubit[]{q0, q1}, 1.0)));
        assertThrows(IllegalArgumentException.class,
            () -> state.applyGate(new MultiQubitGate("X", matrix(4),
                new Qubit[]{q1}, new Qubit[]{q0}, 1.0)));
        assertThrows(IllegalArgumentException.class,
            () -> state.applyGate(new MultiQubitGate("CX", matrix(8),
                new Qubit[]{q1, q2}, new Qubit[]{q0}, 1.0)));
        assertThrows(IllegalArgumentException.class,
            () -> state.applyGate(gate("X", new Qubit(-1))));
    }

    @Test
    void allCliffordAliasesAndFormsExecute() {
        StabilizerState state = new StabilizerState(3, new Random(3));
        Qubit q0 = state.getQubit(0);
        Qubit q1 = state.getQubit(1);
        Qubit q2 = state.getQubit(2);

        state.applyGate(gate("x", q0));
        state.applyGate(gate("y", q0));
        state.applyGate(gate("z", q0));
        state.applyGate(gate("h", q0));
        state.applyGate(gate("s", q0));
        state.applyGate(gate("sDg", q0));
        state.applyGate(gate("S\u2020", q0));
        state.applyGate(gate("I", q0));
        state.applyGate(gate("IDENTITY", q0));
        state.applyGate(MultiQubitGate.createCNOT(q0, q1));
        state.applyGate(new MultiQubitGate("CX", matrix(4),
            new Qubit[]{q1}, new Qubit[]{q0}, 1.0));
        state.applyGate(MultiQubitGate.createCZ(q0, q1));
        state.applyGate(new MultiQubitGate("CZ", matrix(4), new Qubit[]{q0, q1}));
        state.applyGate(MultiQubitGate.createSWAP(q1, q2));
        state.applyGate(gate("h", q2));
        state.applyGate(gate("s", q2));
        state.applyGate(gate("y", q1));

        assertEquals(3, state.getNumQubits());
        double[] probabilities = state.outcomeProbabilitiesZ(qubits(state, 3));
        double total = 0;
        for (double probability : probabilities) {
            total += probability;
        }
        assertEquals(1.0, total, EPS);
    }

    @Test
    void measurementValidationIsCovered() {
        StabilizerState state = new StabilizerState(2, new Random(4));
        assertThrows(IllegalArgumentException.class, () -> state.measureZ(null));
        assertThrows(IllegalArgumentException.class,
            () -> state.measureZ(new Qubit(5)));

        assertThrows(IllegalArgumentException.class, () -> state.sampleZ(null, 1));
        assertThrows(IllegalArgumentException.class,
            () -> state.sampleZ(new Qubit[0], 1));
        assertThrows(IllegalArgumentException.class,
            () -> state.sampleZ(qubits(state, 2), -1));
        assertThrows(IllegalArgumentException.class,
            () -> state.sampleZ(new Qubit[]{state.getQubit(0), state.getQubit(0)}, 1));

        StabilizerState wide = new StabilizerState(33, new Random(5));
        assertThrows(IllegalArgumentException.class,
            () -> wide.sampleZ(qubits(wide, 33), 1));
        assertThrows(IllegalArgumentException.class,
            () -> wide.collapseZ(qubits(wide, 33), 0));
        StabilizerState exact = new StabilizerState(17, new Random(6));
        assertThrows(IllegalArgumentException.class,
            () -> exact.outcomeProbabilitiesZ(qubits(exact, 17)));

        assertThrows(IllegalArgumentException.class,
            () -> state.outcomeProbabilitiesZ(null));
        assertThrows(IllegalArgumentException.class,
            () -> state.collapseZ(null, 0));
        assertThrows(IllegalArgumentException.class,
            () -> state.collapseZ(qubits(state, 2), -1));
        assertThrows(IllegalArgumentException.class,
            () -> state.collapseZ(qubits(state, 2), 4));
    }

    @Test
    void measurementOutcomesHitBothRandomAndDeterministicPaths() {
        int zeros = 0;
        int ones = 0;
        Random random = new Random(12345);
        for (int shot = 0; shot < 200; shot++) {
            StabilizerState state = new StabilizerState(1, random);
            state.applyGate(gate("H", state.getQubit(0)));
            int outcome = state.measureZ(state.getQubit(0));
            if (outcome == 0) {
                zeros++;
            } else {
                ones++;
            }
        }
        assertTrue(zeros > 0);
        assertTrue(ones > 0);

        StabilizerState zero = new StabilizerState(1, new Random(7));
        assertEquals(0, zero.measureZ(zero.getQubit(0)));
        for (int i = 0; i < 10; i++) {
            assertEquals(0, zero.measureZ(zero.getQubit(0)));
        }

        StabilizerState one = new StabilizerState(1, new Random(8));
        one.applyGate(gate("X", one.getQubit(0)));
        assertEquals(1, one.measureZ(one.getQubit(0)));
    }

    @Test
    void collapseHandlesMatchingAndMismatchingBits() {
        StabilizerState bell = bellState();
        Qubit[] targets = qubits(bell, 2);

        StabilizerState collapsed = (StabilizerState) bell.collapseZ(targets, 0);
        double[] zero = collapsed.outcomeProbabilitiesZ(targets);
        assertEquals(1.0, zero[0], EPS);

        StabilizerState mismatched = (StabilizerState) bell.collapseZ(targets, 3);
        double[] three = mismatched.outcomeProbabilitiesZ(targets);
        assertEquals(1.0, three[3], EPS);

        StabilizerState viaIndices = (StabilizerState) bell.collapseToBasisState(
            new int[]{1, 0}, 2);
        assertEquals(1.0, viaIndices.outcomeProbabilitiesZ(targets)[1], EPS);

        assertThrows(IllegalArgumentException.class,
            () -> bell.collapseToBasisState(null, 0));
        assertThrows(IllegalArgumentException.class,
            () -> bell.collapseToBasisState(new int[]{0, 0}, 0));
        assertThrows(IllegalArgumentException.class,
            () -> bell.collapseToBasisState(new int[]{5}, 0));
        assertThrows(UnsupportedOperationException.class,
            () -> bell.collapseToEigenvector(new int[]{0}, new Complex[]{Complex.ONE}));

        StabilizerState packed = new StabilizerState(32, new Random(9));
        QuantumState collapsedPacked = packed.collapseZ(qubits(packed, 32), 0);
        assertEquals(32, collapsedPacked.getNumQubits());
    }

    @Test
    void outcomeProbabilitiesSplitOnSuperpositionAndStayDeterministic() {
        StabilizerState superposition = new StabilizerState(1, new Random(10));
        superposition.applyGate(gate("H", superposition.getQubit(0)));
        double[] probabilities = superposition.outcomeProbabilitiesZ(
            new Qubit[]{superposition.getQubit(0)});
        assertArrayEquals(new double[]{0.5, 0.5}, probabilities, EPS);

        StabilizerState deterministic = new StabilizerState(2, new Random(11));
        deterministic.applyGate(gate("X", deterministic.getQubit(1)));
        probabilities = deterministic.outcomeProbabilitiesZ(qubits(deterministic, 2));
        assertEquals(1.0, probabilities[1], EPS);
        assertEquals(0.0, probabilities[0], EPS);
        assertEquals(0.0, probabilities[2], EPS);
        assertEquals(0.0, probabilities[3], EPS);
    }

    @Test
    void ghzOutcomesExercisePivotAndDependentRows() {
        StabilizerState state = new StabilizerState(3, new Random(12));
        state.applyGate(gate("H", state.getQubit(0)));
        state.applyGate(MultiQubitGate.createCNOT(state.getQubit(0), state.getQubit(1)));
        state.applyGate(MultiQubitGate.createCNOT(state.getQubit(1), state.getQubit(2)));

        double[] probabilities = state.outcomeProbabilitiesZ(qubits(state, 3));
        assertEquals(0.5, probabilities[0], EPS);
        assertEquals(0.5, probabilities[7], EPS);

        StabilizerState collapsed = (StabilizerState) state.collapseZ(
            new Qubit[]{state.getQubit(0)}, 1);
        assertEquals(1.0, collapsed.outcomeProbabilitiesZ(
            new Qubit[]{collapsed.getQubit(1)})[1], EPS);
    }

    @Test
    void sampleZKeepsTheStateUnchanged() {
        StabilizerState bell = bellState();
        int[] samples = bell.sampleZ(qubits(bell, 2), 200);
        boolean sawZero = false;
        boolean sawThree = false;
        for (int sample : samples) {
            assertTrue(sample == 0 || sample == 3, "Unexpected Bell sample: " + sample);
            sawZero |= sample == 0;
            sawThree |= sample == 3;
        }
        assertTrue(sawZero);
        assertTrue(sawThree);

        double[] probabilities = bell.outcomeProbabilitiesZ(qubits(bell, 2));
        assertEquals(0.5, probabilities[0], EPS);
        assertEquals(0.5, probabilities[3], EPS);
    }

    @Test
    void tensorProductCopiesPhasesAndSpansWords() {
        StabilizerState left = new StabilizerState(1, new Random(13));
        left.applyGate(gate("H", left.getQubit(0)));
        left.applyGate(gate("S", left.getQubit(0)));

        StabilizerState right = new StabilizerState(64, new Random(14));
        right.applyGate(gate("X", right.getQubit(63)));
        right.applyGate(gate("S", right.getQubit(63)));

        StabilizerState product = (StabilizerState) left.tensorProduct(right);
        assertEquals(65, product.getNumQubits());
        double[] probabilities = product.outcomeProbabilitiesZ(
            new Qubit[]{product.getQubit(0)});
        assertEquals(0.5, probabilities[0], EPS);
        assertEquals(0.5, probabilities[1], EPS);

        StabilizerState rightOne = new StabilizerState(1, new Random(15));
        rightOne.applyGate(gate("X", rightOne.getQubit(0)));
        StabilizerState twoQubits = (StabilizerState) rightOne.tensorProduct(left);
        assertEquals(2, twoQubits.getNumQubits());
        assertEquals(1.0, twoQubits.outcomeProbabilitiesZ(
            new Qubit[]{twoQubits.getQubit(0)})[1], EPS);

        assertThrows(IllegalArgumentException.class,
            () -> left.tensorProduct(StateVector.createZeroState(1)));
        assertThrows(IllegalArgumentException.class,
            () -> left.tensorProduct(null));
    }

    @Test
    void equalityAndHashCodeCoverAllBranches() {
        StabilizerState first = new StabilizerState(2, new Random(16));
        StabilizerState second = new StabilizerState(2, new Random(17));
        assertEquals(first, first);
        assertEquals(first, second);
        assertEquals(first.hashCode(), second.hashCode());
        assertNotEquals(first, null);
        assertNotEquals(first, "not a state");
        assertNotEquals(first, StateVector.createZeroState(2));

        second.applyGate(gate("X", second.getQubit(1)));
        assertNotEquals(first, second);
        assertNotEquals(first.hashCode(), second.hashCode());

        StabilizerState rotated = first.copy();
        rotated.applyGate(gate("H", rotated.getQubit(0)));
        assertNotEquals(first, rotated);
        StabilizerState phased = first.copy();
        phased.applyGate(gate("S", phased.getQubit(0)));
        assertNotEquals(first, phased);

        StabilizerState smaller = new StabilizerState(1, new Random(18));
        assertNotEquals(first, smaller);

        StabilizerState copy = first.copy();
        assertEquals(first, copy);
        assertEquals(first.hashCode(), copy.hashCode());
        assertEquals(first.outcomeProbabilitiesZ(qubits(first, 2))[0],
            copy.outcomeProbabilitiesZ(qubits(copy, 2))[0], EPS);
    }

    @Test
    void strategyAcceptsStabilizerStatesAndRejectsOthers() {
        StabilizerStrategy strategy = new StabilizerStrategy();
        QuantumState state = strategy.createInitialState(3);
        assertTrue(state instanceof StabilizerState);
        assertThrows(IllegalArgumentException.class,
            () -> strategy.createInitialState(0));

        SingleQubitGate hadamard = gate("H", state.getQubit(0));
        assertSame(state, strategy.applyGate(state, hadamard, null));
        assertThrows(IllegalArgumentException.class,
            () -> strategy.applyGate(StateVector.createZeroState(1), hadamard, null));
        assertEquals(0.5,
            strategy.measure(state, new Qubit[]{state.getQubit(0)}, 100)
                .getOutcomeProbabilities()[0], EPS);

        assertThrows(IllegalArgumentException.class,
            () -> strategy.applyNoise(StateVector.createZeroState(1), null, null));
        assertThrows(UnsupportedOperationException.class,
            () -> strategy.applyNoise(state, new DephasingNoise(0.1), null));

        assertThrows(IllegalArgumentException.class,
            () -> strategy.measure(StateVector.createZeroState(1),
                new Qubit[]{new Qubit(0)}, 1));
        assertThrows(IllegalArgumentException.class,
            () -> strategy.measure(state, null, 1));
        assertThrows(IllegalArgumentException.class,
            () -> strategy.measure(state, new Qubit[0], 1));
        StabilizerState wide = new StabilizerState(17, new Random(19));
        assertThrows(IllegalArgumentException.class,
            () -> strategy.measure(wide, qubits(wide, 17), 1));

        assertThrows(IllegalArgumentException.class,
            () -> strategy.cloneState(StateVector.createZeroState(1)));
        QuantumState clone = strategy.cloneState(state);
        assertNotSame(state, clone);
        assertEquals(state, clone);
        assertTrue(strategy.cloneState(clone) instanceof StabilizerState);

        assertThrows(UnsupportedOperationException.class,
            () -> strategy.calculateFidelity(state, clone));
        assertThrows(UnsupportedOperationException.class,
            () -> strategy.calculateEntropy(state));
        assertFalse(strategy.canSimulateNoise());
        assertEquals(2000, strategy.getMaxSupportedQubits());
        assertEquals("StabilizerSimulator", strategy.getStrategyName());
        strategy.initialize();
        strategy.cleanup();
    }

    @Test
    void strategyMeasurementBuildsObservableNameForMultipleTargets() {
        StabilizerStrategy strategy = new StabilizerStrategy();
        StabilizerState state = new StabilizerState(2, new Random(20));
        Qubit[] targets = qubits(state, 2);
        MeasurementResult result = strategy.measure(state, targets, 50);
        assertEquals("Z_q0xZ_q1", result.getObservableName());
        assertEquals(2, result.getMeasuredQubits().length);
        assertEquals(4, result.getOutcomeProbabilities().length);
        assertEquals(1.0, result.getOutcomeProbabilities()[0], EPS);
        assertEquals(1.0, result.getOutcomeEigenvalues()[0], EPS);
        assertEquals(-1.0, result.getOutcomeEigenvalues()[1], EPS);
        assertEquals(1.0, result.getOutcomeEigenvalues()[3], EPS);
    }

    private static StabilizerState bellState() {
        StabilizerState state = new StabilizerState(2, new Random(21));
        state.applyGate(gate("H", state.getQubit(0)));
        state.applyGate(MultiQubitGate.createCNOT(state.getQubit(0), state.getQubit(1)));
        return state;
    }
}
