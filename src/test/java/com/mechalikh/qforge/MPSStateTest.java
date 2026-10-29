package com.mechalikh.qforge;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.mechalikh.qforge.math.Complex;
import com.mechalikh.qforge.math.ComplexMatrix;
import com.mechalikh.qforge.math.ComplexSvd;
import com.mechalikh.qforge.measurement.MeasurementResult;
import com.mechalikh.qforge.operations.MultiQubitGate;
import com.mechalikh.qforge.operations.QuantumGate;
import com.mechalikh.qforge.operations.SingleQubitGate;
import com.mechalikh.qforge.state.MPSState;
import com.mechalikh.qforge.state.QuantumState;
import com.mechalikh.qforge.state.Qubit;
import com.mechalikh.qforge.state.StateVector;
import com.mechalikh.qforge.strategy.StateVectorStrategy;
import com.mechalikh.qforge.strategy.TensorNetworkStrategy;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.Test;

class MPSStateTest {
    private static final double EPS = 1e-10;

    @Test
    void zeroStateHasSingleAmplitude() {
        MPSState state = new MPSState(4, 8);

        assertEquals(4, state.getNumQubits());
        assertEquals(16, state.getProbabilities().length);
        assertEquals(1.0, state.getProbability(0), EPS);
        for (int basis = 1; basis < 16; basis++) {
            assertEquals(0.0, state.getProbability(basis), EPS);
        }
        assertArrayEquals(new int[]{1, 1, 1, 1, 1}, state.getBondDimensions());
        assertEquals(0.0, state.getTruncationError(), 0.0);
        assertTrue(state.isPure());
        assertEquals(1.0, state.getPurity(), 0.0);
    }

    @Test
    void bellCircuitMatchesStateVector() {
        List<QuantumGate> gates = List.of(
            SingleQubitGate.createHadamard(new Qubit(0)),
            MultiQubitGate.createCNOT(new Qubit(0), new Qubit(1)));

        MPSState mps = runMps(2, 16, gates);
        StateVector vector = runStateVector(2, gates);

        assertEquals(0.0, maxDifference(mps.getAmplitudes(), vector.getAmplitudes()), EPS);
        assertEquals(1.0, totalProbability(mps), EPS);
        assertArrayEquals(new int[]{1, 2, 1}, mps.getBondDimensions());
        assertEquals(0.0, mps.getTruncationError(), 0.0);
    }

    @Test
    void ghzStateMatchesStateVector() {
        List<QuantumGate> gates = List.of(
            SingleQubitGate.createHadamard(new Qubit(0)),
            MultiQubitGate.createCNOT(new Qubit(0), new Qubit(1)),
            MultiQubitGate.createCNOT(new Qubit(1), new Qubit(2)));

        MPSState mps = runMps(3, 16, gates);
        StateVector vector = runStateVector(3, gates);

        assertEquals(0.0, maxDifference(mps.getAmplitudes(), vector.getAmplitudes()), EPS);
        assertEquals(1.0, totalProbability(mps), EPS);
        assertEquals(0.5, mps.getProbability(0), EPS);
        assertEquals(0.5, mps.getProbability(7), EPS);
    }

    @Test
    void nonAdjacentControlledNotMatchesStateVector() {
        List<QuantumGate> forward = List.of(
            SingleQubitGate.createHadamard(new Qubit(0)),
            MultiQubitGate.createCNOT(new Qubit(0), new Qubit(3)),
            SingleQubitGate.createRX(0.6, new Qubit(2)));
        List<QuantumGate> backward = List.of(
            SingleQubitGate.createHadamard(new Qubit(3)),
            MultiQubitGate.createCNOT(new Qubit(3), new Qubit(0)),
            MultiQubitGate.createCNOT(new Qubit(2), new Qubit(0)));

        MPSState forwardMps = runMps(4, 16, forward);
        MPSState backwardMps = runMps(4, 16, backward);

        assertEquals(0.0, maxDifference(forwardMps.getAmplitudes(),
            runStateVector(4, forward).getAmplitudes()), EPS);
        assertEquals(0.0, maxDifference(backwardMps.getAmplitudes(),
            runStateVector(4, backward).getAmplitudes()), EPS);
        assertEquals(0.0, forwardMps.getTruncationError(), 0.0);
        assertEquals(0.0, backwardMps.getTruncationError(), 0.0);
    }

    @Test
    void randomFourQubitCircuitMatchesStateVector() {
        Random random = new Random(2026);
        int[][] pairs = {{0, 1}, {1, 2}, {2, 3}, {0, 2}, {1, 3}, {3, 0}};
        List<QuantumGate> gates = new ArrayList<>();
        for (int step = 0; step < 18; step++) {
            gates.add(randomSingleQubitGate(random, random.nextInt(4)));
            int[] pair = pairs[random.nextInt(pairs.length)];
            gates.add(MultiQubitGate.createCNOT(new Qubit(pair[0]), new Qubit(pair[1])));
        }

        MPSState mps = runMps(4, 16, gates);
        StateVector vector = runStateVector(4, gates);

        assertEquals(0.0, maxDifference(mps.getAmplitudes(), vector.getAmplitudes()), EPS);
        assertEquals(1.0, totalProbability(mps), EPS);
        assertTrue(mps.getTruncationError() < 1e-20);
        for (int bond : mps.getBondDimensions()) {
            assertTrue(bond <= 16);
        }
    }

    @Test
    void smallBondDimensionTruncatesAndKeepsUnitNorm() {
        List<QuantumGate> gates = List.of(
            SingleQubitGate.createHadamard(new Qubit(0)),
            MultiQubitGate.createCNOT(new Qubit(0), new Qubit(1)),
            SingleQubitGate.createHadamard(new Qubit(2)),
            MultiQubitGate.createCNOT(new Qubit(1), new Qubit(2)),
            SingleQubitGate.createHadamard(new Qubit(3)),
            MultiQubitGate.createCNOT(new Qubit(2), new Qubit(3)),
            SingleQubitGate.createRX(0.7, new Qubit(0)),
            SingleQubitGate.createRY(0.9, new Qubit(3)),
            MultiQubitGate.createCNOT(new Qubit(3), new Qubit(0)));

        for (int bondLimit : new int[]{1, 2}) {
            MPSState state = runMps(4, bondLimit, gates);

            assertTrue(state.getTruncationError() > 1e-3);
            assertEquals(1.0, totalProbability(state), 1e-12);
            int[] bonds = state.getBondDimensions();
            for (int bond = 1; bond < bonds.length - 1; bond++) {
                assertTrue(bonds[bond] <= bondLimit);
            }
        }
    }

    @Test
    void complexSvdReconstructsMatrix() {
        Random random = new Random(11);
        Complex[][] entries = new Complex[4][3];
        for (int row = 0; row < 4; row++) {
            for (int column = 0; column < 3; column++) {
                entries[row][column] =
                    new Complex(random.nextGaussian(), random.nextGaussian());
            }
        }
        ComplexMatrix matrix = new ComplexMatrix(entries);

        ComplexSvd.Result result = ComplexSvd.decompose(matrix);
        double[] singularValues = result.getSingularValues();
        assertEquals(4, result.getU().rows());
        assertEquals(3, result.getU().cols());
        assertEquals(3, result.getVAdjoint().rows());
        assertEquals(3, result.getVAdjoint().cols());
        assertEquals(3, singularValues.length);

        double residual = 0.0;
        for (int row = 0; row < 4; row++) {
            for (int column = 0; column < 3; column++) {
                Complex sum = Complex.ZERO;
                for (int term = 0; term < singularValues.length; term++) {
                    sum = sum.add(result.getU().get(row, term)
                        .multiply(new Complex(singularValues[term], 0.0))
                        .multiply(result.getVAdjoint().get(term, column)));
                }
                residual = Math.max(residual, matrix.get(row, column).subtract(sum).abs());
            }
        }
        assertTrue(residual < EPS, "SVD residual " + residual);
    }

    @Test
    void tensorProductCombinesIndependentStates() {
        List<QuantumGate> bellGates = List.of(
            SingleQubitGate.createHadamard(new Qubit(0)),
            MultiQubitGate.createCNOT(new Qubit(0), new Qubit(1)));
        MPSState bell = runMps(2, 16, bellGates);
        MPSState flipped = runMps(2, 16,
            List.of(SingleQubitGate.createPauliX(new Qubit(1))));

        MPSState product = (MPSState) bell.tensorProduct(flipped);
        StateVector expected = (StateVector) runStateVector(2, bellGates)
            .tensorProduct(runStateVector(2,
                List.of(SingleQubitGate.createPauliX(new Qubit(1)))));

        assertEquals(4, product.getNumQubits());
        assertEquals(0.0, maxDifference(product.getAmplitudes(),
            expected.getAmplitudes()), EPS);
        assertEquals(1.0, totalProbability(product), EPS);
    }

    @Test
    void copyIsIndependent() {
        MPSState original = runMps(3, 16, List.of(
            SingleQubitGate.createHadamard(new Qubit(0)),
            MultiQubitGate.createCNOT(new Qubit(0), new Qubit(2))));
        MPSState copy = original.copy();

        assertNotSame(original, copy);
        assertEquals(original, copy);
        assertEquals(original.hashCode(), copy.hashCode());

        MPSState changed = copy.applyGate(SingleQubitGate.createPauliX(new Qubit(0)));
        assertEquals(0.0, maxDifference(original.getAmplitudes(),
            copy.getAmplitudes()), EPS);
        assertTrue(maxDifference(changed.getAmplitudes(),
            original.getAmplitudes()) > 1e-3);
    }

    @Test
    void collapseToBasisStateMatchesStateVector() {
        Random random = new Random(7);
        List<QuantumGate> gates = List.of(
            SingleQubitGate.createHadamard(new Qubit(0)),
            SingleQubitGate.createRY(random.nextDouble(), new Qubit(1)),
            MultiQubitGate.createCNOT(new Qubit(0), new Qubit(2)),
            SingleQubitGate.createRX(random.nextDouble(), new Qubit(3)),
            MultiQubitGate.createCNOT(new Qubit(2), new Qubit(3)));
        MPSState mps = runMps(4, 16, gates);
        StateVector vector = runStateVector(4, gates);

        for (int outcome = 0; outcome < 4; outcome++) {
            MPSState collapsed = (MPSState) mps.collapseToBasisState(new int[]{0, 3}, outcome);
            StateVector expected = (StateVector) vector.collapseToBasisState(
                new int[]{0, 3}, outcome);

            assertEquals(0.0, maxDifference(collapsed.getAmplitudes(),
                expected.getAmplitudes()), EPS);
            assertEquals(1.0, totalProbability(collapsed), EPS);
        }
    }

    @Test
    void constructorNormalizesSuppliedTensors() {
        Complex[][][] tensors = new Complex[2][][];
        tensors[0] = new Complex[][]{
            {new Complex(1.0, 0.0), new Complex(0.0, 0.0)},
            {new Complex(0.0, 0.0), new Complex(1.0, 0.0)}};
        tensors[1] = new Complex[][]{
            {new Complex(1.0, 0.0)},
            {new Complex(0.0, 0.0)},
            {new Complex(0.0, 0.0)},
            {new Complex(1.0, 0.0)}};
        Complex[][] lambdas = {{new Complex(1.0, 0.0), new Complex(1.0, 0.0)}};

        MPSState state = new MPSState(tensors, lambdas, 2, 8);

        assertEquals(1.0, totalProbability(state), EPS);
        assertEquals(0.5, state.getProbability(0), EPS);
        assertEquals(0.5, state.getProbability(3), EPS);
        assertEquals(0.0, state.getProbability(1), EPS);
        assertEquals(0.0, state.getTruncationError(), 0.0);
    }

    @Test
    void expectationValueMatchesStateVector() {
        List<QuantumGate> gates = List.of(
            SingleQubitGate.createHadamard(new Qubit(0)),
            MultiQubitGate.createCNOT(new Qubit(0), new Qubit(1)),
            SingleQubitGate.createRZ(0.4, new Qubit(1)));
        MPSState mps = runMps(2, 16, gates);
        StateVector vector = runStateVector(2, gates);
        ComplexMatrix observable = ComplexMatrix.pauliZ()
            .tensorProduct(ComplexMatrix.pauliZ());

        assertEquals(vector.getExpectationValue(observable),
            mps.getExpectationValue(observable), EPS);
    }

    @Test
    void strategyReportsCapabilitiesAndRejectsNoise() {
        TensorNetworkStrategy strategy = new TensorNetworkStrategy(8);
        QuantumState state = strategy.createInitialState(2);

        assertTrue(state instanceof MPSState);
        assertFalse(strategy.canSimulateNoise());
        assertEquals(100, strategy.getMaxSupportedQubits());
        assertEquals("TensorNetworkSimulator", strategy.getStrategyName());
        assertThrows(UnsupportedOperationException.class,
            () -> strategy.applyNoise(state, null, new Qubit[]{new Qubit(0)}));
        assertThrows(IllegalArgumentException.class,
            () -> new TensorNetworkStrategy(0));
    }

    @Test
    void measurementOnBellStateReturnsCorrelatedOutcomes() {
        TensorNetworkStrategy strategy = new TensorNetworkStrategy(16);
        QuantumState state = strategy.createInitialState(2);
        state = strategy.applyGate(state,
            SingleQubitGate.createHadamard(new Qubit(0)), new Qubit[]{new Qubit(0)});
        state = strategy.applyGate(state,
            MultiQubitGate.createCNOT(new Qubit(0), new Qubit(1)),
            new Qubit[]{new Qubit(0), new Qubit(1)});

        MeasurementResult result = strategy.measure(state,
            new Qubit[]{new Qubit(0), new Qubit(1)}, 400);

        assertEquals(400, result.getTotalShots());
        assertEquals(0, result.getCount(1));
        assertEquals(0, result.getCount(2));
        assertTrue(result.getCount(0) > 0);
        assertTrue(result.getCount(3) > 0);
    }

    @Test
    void collapseToEigenvectorIsUnsupported() {
        MPSState state = new MPSState(2, 4);

        assertThrows(UnsupportedOperationException.class,
            () -> state.collapseToEigenvector(new int[]{0}, new Complex[]{
                Complex.ONE, Complex.ZERO}));
    }

    private static QuantumGate randomSingleQubitGate(Random random, int qubit) {
        Qubit target = new Qubit(qubit);
        double angle = random.nextDouble() * 2.0 * Math.PI;
        switch (random.nextInt(5)) {
            case 0:
                return SingleQubitGate.createHadamard(target);
            case 1:
                return SingleQubitGate.createRX(angle, target);
            case 2:
                return SingleQubitGate.createRY(angle, target);
            case 3:
                return SingleQubitGate.createRZ(angle, target);
            default:
                return SingleQubitGate.createPhase(angle, target);
        }
    }

    private static MPSState runMps(int numQubits, int maxBondDimension,
            List<QuantumGate> gates) {
        TensorNetworkStrategy strategy = new TensorNetworkStrategy(maxBondDimension);
        QuantumState state = strategy.createInitialState(numQubits);
        for (QuantumGate gate : gates) {
            state = strategy.applyGate(state, gate, gate.getTargetQubits());
        }
        return (MPSState) state;
    }

    private static StateVector runStateVector(int numQubits, List<QuantumGate> gates) {
        StateVectorStrategy strategy = new StateVectorStrategy();
        QuantumState state = StateVector.createZeroState(numQubits);
        for (QuantumGate gate : gates) {
            state = strategy.applyGate(state, gate, gate.getTargetQubits());
        }
        return (StateVector) state;
    }

    private static double maxDifference(Complex[] first, Complex[] second) {
        double maximum = 0.0;
        for (int index = 0; index < first.length; index++) {
            maximum = Math.max(maximum, first[index].subtract(second[index]).abs());
        }
        return maximum;
    }

    private static double totalProbability(MPSState state) {
        double total = 0.0;
        for (double probability : state.getProbabilities()) {
            total += probability;
        }
        return total;
    }
}
