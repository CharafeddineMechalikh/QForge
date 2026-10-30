package com.mechalikh.qforge;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.mechalikh.qforge.measurement.MeasurementOperator;
import com.mechalikh.qforge.measurement.MeasurementResult;
import com.mechalikh.qforge.measurement.Observable;
import com.mechalikh.qforge.noise.ReadoutErrorModel;
import com.mechalikh.qforge.operations.MultiQubitGate;
import com.mechalikh.qforge.operations.SingleQubitGate;
import com.mechalikh.qforge.state.MPSState;
import com.mechalikh.qforge.state.Qubit;
import com.mechalikh.qforge.strategy.TensorNetworkStrategy;
import org.junit.jupiter.api.Test;

/**
 * Covers the tensor network readout paths: sampling and collapse above the dense limit, the
 * measurement operator and strategy routing, and the contracted overlap.
 */
class MPSReadoutTest {
    private static final double EPS = 1e-12;
    private static final double FREQUENCY_TOLERANCE = 0.02;

    @Test
    void samplingMatchesExactMarginalsForUnsortedTargets() {
        MPSState state = entanglingCircuit(5, 16);
        int[] targets = {4, 1, 3};
        double[] expected = marginal(((MPSState) state).getProbabilities(), 5, targets);

        int shots = 20000;
        int[] samples = state.sampleComputationalBasis(targets, shots);
        double[] observed = frequencies(samples, 1 << targets.length, shots);
        for (int outcome = 0; outcome < expected.length; outcome++) {
            assertEquals(expected[outcome], observed[outcome], FREQUENCY_TOLERANCE,
                "outcome " + outcome);
        }

        int single = state.sampleComputationalBasis(targets);
        assertTrue(single >= 0 && single < (1 << targets.length));
        assertArrayEquals(new int[0], state.sampleComputationalBasis(targets, 0));
    }

    @Test
    void samplingAndCollapseWorkAboveTwentyFourQubits() {
        MPSState state = new MPSState(25, 4);
        for (int qubit = 0; qubit < 25; qubit++) {
            state = state.applyGate(SingleQubitGate.createPauliX(new Qubit(qubit)));
        }

        assertArrayEquals(new int[]{7, 7, 7},
            state.sampleComputationalBasis(new int[]{0, 12, 24}, 3));
        assertEquals(7, state.sampleComputationalBasis(new int[]{0, 12, 24}));

        MPSState collapsed = (MPSState) state.collapseToBasisState(new int[]{12}, 1);
        assertEquals(1, collapsed.sampleComputationalBasis(new int[]{12}));
        assertEquals(1, collapsed.sampleComputationalBasis(new int[]{0}));
        assertEquals(25, collapsed.getNumQubits());
    }

    @Test
    void ghzCorrelationsSurviveSamplingAboveTheDenseLimit() {
        MPSState state = new MPSState(26, 8);
        state = state.applyGate(SingleQubitGate.createHadamard(new Qubit(0)));
        for (int qubit = 1; qubit < 26; qubit++) {
            state = state.applyGate(MultiQubitGate.createCNOT(
                new Qubit(qubit - 1), new Qubit(qubit)));
        }

        int shots = 400;
        int[] samples = state.sampleComputationalBasis(new int[]{0, 25}, shots);
        boolean sawZero = false;
        boolean sawOne = false;
        for (int sample : samples) {
            if (sample == 0) {
                sawZero = true;
            } else if (sample == 3) {
                sawOne = true;
            } else {
                throw new AssertionError("uncorrelated outcome " + sample);
            }
        }
        assertTrue(sawZero && sawOne);
    }

    @Test
    void collapseOfADistantPairAboveTheDenseLimitCorrelatesThePartner() {
        MPSState state = new MPSState(26, 8);
        state = state.applyGate(SingleQubitGate.createHadamard(new Qubit(0)));
        state = state.applyGate(MultiQubitGate.createCNOT(new Qubit(0), new Qubit(25)));

        int measured = state.sampleComputationalBasis(new int[]{0});
        MPSState collapsed = (MPSState) state.collapseToBasisState(new int[]{0}, measured);
        assertEquals(measured, collapsed.sampleComputationalBasis(new int[]{25}));

        TensorNetworkStrategy strategy = new TensorNetworkStrategy(8);
        assertEquals(1.0, strategy.calculateFidelity(collapsed, collapsed), EPS);
    }

    @Test
    void collapsingAZeroProbabilityOutcomeFailsAboveTheDenseLimit() {
        MPSState flipped = new MPSState(25, 4)
            .applyGate(SingleQubitGate.createPauliX(new Qubit(3)));

        assertThrows(IllegalStateException.class,
            () -> flipped.collapseToBasisState(new int[]{3}, 0));
        assertThrows(IllegalArgumentException.class,
            () -> flipped.collapseToBasisState(new int[]{3}, 2));
        assertThrows(IllegalArgumentException.class,
            () -> flipped.collapseToBasisState(new int[]{0, 0}, 0));
    }

    @Test
    void overlapIsContractedAboveTheDenseLimit() {
        MPSState zero = new MPSState(25, 4);
        MPSState flipped = zero.applyGate(SingleQubitGate.createPauliX(new Qubit(24)));

        assertEquals(1.0, zero.overlap(zero).absSquare(), EPS);
        assertEquals(0.0, zero.overlap(flipped).absSquare(), EPS);
        assertThrows(IllegalArgumentException.class, () -> zero.overlap(null));
        assertThrows(IllegalArgumentException.class, () -> zero.overlap(new MPSState(24, 4)));

        TensorNetworkStrategy strategy = new TensorNetworkStrategy(4);
        assertEquals(0.0, strategy.calculateFidelity(zero, flipped), EPS);
        assertEquals(1.0, strategy.calculateFidelity(zero, new MPSState(25, 4)), EPS);
    }

    @Test
    void computationalBasisObservableReadsLargeStatesByContraction() {
        MPSState state = new MPSState(25, 4);
        MeasurementOperator operator =
            MeasurementOperator.computationalBasis(new Qubit[]{new Qubit(7)}, 4);

        MeasurementResult sampled = operator.measure(state);
        assertEquals(4, sampled.getTotalShots());
        assertEquals(1.0, sampled.getProbability(0), EPS);

        MeasurementOperator.MeasurementOutcome outcome = operator.measureAndCollapse(state);
        assertEquals(0, outcome.getOutcome());
        assertEquals(4, outcome.getResult().getTotalShots());
        assertEquals(0, outcome.getResult().getMostFrequentOutcome());
        MPSState collapsed = (MPSState) outcome.getCollapsedState();
        assertEquals(0, collapsed.sampleComputationalBasis(new int[]{7}));
        assertEquals(25, collapsed.getNumQubits());
    }

    @Test
    void readoutConfusionChangesReportedBitsButNotTheCollapsedState() {
        MPSState state = new MPSState(25, 4);
        ReadoutErrorModel readout = new ReadoutErrorModel(
            new double[]{1.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 1.0},
            new double[]{0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0});
        MeasurementOperator operator =
            MeasurementOperator.computationalBasis(new Qubit[]{new Qubit(7)}, 2);

        MeasurementOperator.MeasurementOutcome outcome =
            operator.measureAndCollapse(state, readout);
        assertEquals(1, outcome.getResult().getMostFrequentOutcome());
        assertEquals(0, outcome.getOutcome());
        MPSState collapsed = (MPSState) outcome.getCollapsedState();
        assertEquals(0, collapsed.sampleComputationalBasis(new int[]{7}));
    }

    @Test
    void strategyMeasurementAndNonDiagonalObservablesAboveTheDenseLimit() {
        TensorNetworkStrategy strategy = new TensorNetworkStrategy(4);
        MPSState state = (MPSState) strategy.createInitialState(25);
        MPSState flipped = state
            .applyGate(SingleQubitGate.createPauliX(new Qubit(24)));

        MeasurementResult zeros = strategy.measure(state, new Qubit[]{new Qubit(24)}, 3);
        assertEquals(3, zeros.getTotalShots());
        assertEquals(0, zeros.getMostFrequentOutcome());
        MeasurementResult ones = strategy.measure(flipped, new Qubit[]{new Qubit(24)}, 3);
        assertEquals(1, ones.getMostFrequentOutcome());

        MeasurementOperator xMeasurement =
            new MeasurementOperator(Observable.pauliX(new Qubit(2)), 1);
        assertThrows(UnsupportedOperationException.class,
            () -> xMeasurement.measureAndCollapse(state));
        assertThrows(UnsupportedOperationException.class, () -> xMeasurement.measure(state));
    }

    private static MPSState entanglingCircuit(int numQubits, int bondDimension) {
        MPSState state = new MPSState(numQubits, bondDimension);
        state = state.applyGate(SingleQubitGate.createHadamard(new Qubit(0)));
        for (int qubit = 1; qubit < numQubits; qubit++) {
            state = state.applyGate(SingleQubitGate.createHadamard(new Qubit(qubit)));
            state = state.applyGate(MultiQubitGate.createCNOT(
                new Qubit(qubit - 1), new Qubit(qubit)));
        }
        return state;
    }

    private static double[] marginal(double[] probabilities, int numQubits, int[] targets) {
        double[] marginal = new double[1 << targets.length];
        for (int basisState = 0; basisState < probabilities.length; basisState++) {
            int outcome = 0;
            for (int t = 0; t < targets.length; t++) {
                int bit = (basisState >> (numQubits - 1 - targets[t])) & 1;
                outcome |= bit << (targets.length - 1 - t);
            }
            marginal[outcome] += probabilities[basisState];
        }
        return marginal;
    }

    private static double[] frequencies(int[] samples, int numOutcomes, int shots) {
        double[] frequencies = new double[numOutcomes];
        for (int sample : samples) {
            frequencies[sample] += 1.0;
        }
        for (int outcome = 0; outcome < numOutcomes; outcome++) {
            frequencies[outcome] /= shots;
        }
        return frequencies;
    }
}
