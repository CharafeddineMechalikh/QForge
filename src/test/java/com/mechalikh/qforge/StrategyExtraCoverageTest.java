package com.mechalikh.qforge;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.mechalikh.qforge.math.Complex;
import com.mechalikh.qforge.math.ComplexMatrix;
import com.mechalikh.qforge.measurement.MeasurementOperator;
import com.mechalikh.qforge.measurement.MeasurementResult;
import com.mechalikh.qforge.measurement.Observable;
import com.mechalikh.qforge.noise.DepolarizingNoise;
import com.mechalikh.qforge.operations.MultiQubitGate;
import com.mechalikh.qforge.operations.QuantumOperation;
import com.mechalikh.qforge.operations.SingleQubitGate;
import com.mechalikh.qforge.state.ComplexVector;
import com.mechalikh.qforge.state.DensityMatrix;
import com.mechalikh.qforge.state.QuantumState;
import com.mechalikh.qforge.state.Qubit;
import com.mechalikh.qforge.state.StateVector;
import com.mechalikh.qforge.strategy.DensityMatrixStrategy;
import com.mechalikh.qforge.strategy.SimulationStrategy;
import com.mechalikh.qforge.strategy.StateVectorStrategy;
import java.util.Random;
import org.junit.jupiter.api.Test;

class StrategyExtraCoverageTest {
    private static final double EPS = 1e-9;

    /** A state of a third type that no shared default implementation supports. */
    private static final class ExoticState extends QuantumState {
        ExoticState() {
            super(1);
        }

        @Override
        public QuantumState applyOperation(QuantumOperation operation) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Complex getAmplitude(int basisState) {
            throw new UnsupportedOperationException();
        }

        @Override
        public double getProbability(int basisState) {
            throw new UnsupportedOperationException();
        }

        @Override
        public double[] getProbabilities() {
            throw new UnsupportedOperationException();
        }

        @Override
        public QuantumState tensorProduct(QuantumState other) {
            throw new UnsupportedOperationException();
        }

        @Override
        public QuantumState normalize() {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean isPure() {
            throw new UnsupportedOperationException();
        }

        @Override
        public double getPurity() {
            throw new UnsupportedOperationException();
        }

        @Override
        public QuantumState copy() {
            throw new UnsupportedOperationException();
        }

        @Override
        public QuantumState collapseToBasisState(int[] targetQubits, int outcome) {
            throw new UnsupportedOperationException();
        }

        @Override
        public QuantumState collapseToEigenvector(int[] targetQubits, Complex[] eigenvector) {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean equals(Object obj) {
            return this == obj;
        }

        @Override
        public int hashCode() {
            return 1;
        }

        @Override
        public String toString() {
            return "ExoticState";
        }
    }

    private static QuantumOperation unknownOperation() {
        return new QuantumOperation() {
            @Override
            public QuantumState apply(QuantumState state) {
                return state;
            }

            @Override
            public String getName() {
                return "unknown";
            }

            @Override
            public double getDuration() {
                return 0.0;
            }

            @Override
            public Qubit[] getTargetQubits() {
                return new Qubit[0];
            }

            @Override
            public Qubit[] getControlQubits() {
                return new Qubit[0];
            }

            @Override
            public boolean isUnitary() {
                return false;
            }

            @Override
            public boolean isNoise() {
                return false;
            }

            @Override
            public boolean isMeasurement() {
                return false;
            }

            @Override
            public QuantumOperation inverse() {
                throw new UnsupportedOperationException();
            }
        };
    }

    // ------------------------------------------------- DensityMatrixStrategy

    @Test
    void densityStrategyAppliesGateAndReportsMetadata() {
        DensityMatrixStrategy strategy = new DensityMatrixStrategy();
        assertEquals("DensityMatrixSimulator", strategy.getStrategyName());
        assertEquals(12, strategy.getMaxSupportedQubits());
        assertTrue(strategy.canSimulateNoise());
        strategy.initialize();
        strategy.cleanup();

        DensityMatrix zero = DensityMatrix.createZeroState(1);
        DensityMatrix after = (DensityMatrix) strategy.applyGate(
            zero, SingleQubitGate.createHadamard(new Qubit(0)), TestSupport.qubits(1));
        assertEquals(0.5, after.getProbability(0), EPS);
        assertEquals(0.5, after.getProbability(1), EPS);
    }

    @Test
    void densityStrategyRejectsForeignStateTypes() {
        DensityMatrixStrategy strategy = new DensityMatrixStrategy();
        QuantumState exotic = new ExoticState();
        Qubit[] q0 = TestSupport.qubits(1);

        assertThrows(IllegalArgumentException.class,
            () -> strategy.applyGate(exotic, SingleQubitGate.createPauliX(q0[0]), q0));
        assertThrows(IllegalArgumentException.class,
            () -> strategy.applyNoise(exotic, new DepolarizingNoise(0.1, q0), q0));
        assertThrows(IllegalArgumentException.class, () -> strategy.measure(exotic, q0, 1));
        assertThrows(IllegalArgumentException.class, () -> strategy.cloneState(exotic));
        assertThrows(IllegalArgumentException.class, () -> strategy.calculateEntropy(exotic));
        assertThrows(IllegalArgumentException.class,
            () -> strategy.calculateFidelity(exotic, DensityMatrix.createZeroState(1)));
        assertThrows(IllegalArgumentException.class,
            () -> strategy.calculateFidelity(DensityMatrix.createZeroState(1), exotic));
    }

    @Test
    void densityStrategyFidelityCoversPureMixedAndSizePaths() {
        DensityMatrixStrategy strategy = new DensityMatrixStrategy();
        DensityMatrix zero = DensityMatrix.createZeroState(1);
        DensityMatrix one = StateVector.fromComputationalBasis(1, 1).toDensityMatrix();

        assertEquals(1.0, strategy.calculateFidelity(zero, zero), EPS);
        assertEquals(0.0, strategy.calculateFidelity(zero, one), EPS);

        DensityMatrix mixed = DensityMatrix.createMaximallyMixedState(1);
        double mixedFidelity = strategy.calculateFidelity(mixed, zero);
        assertTrue(mixedFidelity >= 0.0 && mixedFidelity <= 1.0);
        assertEquals(0.5, mixedFidelity, 1e-6);

        assertThrows(IllegalArgumentException.class,
            () -> strategy.calculateFidelity(zero, DensityMatrix.createZeroState(2)));
    }

    @Test
    void densityStrategyEntropyDistinguishesPureAndMixed() {
        DensityMatrixStrategy strategy = new DensityMatrixStrategy();
        assertEquals(0.0, strategy.calculateEntropy(DensityMatrix.createZeroState(1)), 1e-6);
        assertEquals(1.0, strategy.calculateEntropy(DensityMatrix.createMaximallyMixedState(1)), 1e-6);
    }

    @Test
    void densityStrategyCloneAndMeasure() {
        DensityMatrixStrategy strategy = new DensityMatrixStrategy();
        DensityMatrix zero = DensityMatrix.createZeroState(1);
        DensityMatrix clone = (DensityMatrix) strategy.cloneState(zero);
        assertNotSame(zero, clone);
        assertEquals(zero, clone);

        MeasurementResult result = strategy.measure(zero, TestSupport.qubits(1), 10);
        assertEquals(10, result.getCount(0));
        assertEquals(10, result.getTotalShots());
    }

    @Test
    void densityStrategyApplyNoiseMixesState() {
        DensityMatrixStrategy strategy = new DensityMatrixStrategy();
        Qubit[] q0 = TestSupport.qubits(1);
        DensityMatrix zero = DensityMatrix.createZeroState(1);
        DensityMatrix noisy = (DensityMatrix) strategy.applyNoise(
            zero, new DepolarizingNoise(0.2, q0), q0);
        assertTrue(noisy.getPurity() < 1.0 - 1e-6);
    }

    // -------------------------------------------------- StateVectorStrategy

    @Test
    void stateVectorStrategyAppliesGateDirectly() {
        StateVectorStrategy strategy = new StateVectorStrategy();
        assertEquals("StateVectorSimulator", strategy.getStrategyName());
        assertEquals(30, strategy.getMaxSupportedQubits());
        assertTrue(strategy.canSimulateNoise());
        strategy.initialize();
        strategy.cleanup();

        StateVector zero = StateVector.createZeroState(1);
        StateVector after = (StateVector) strategy.applyGate(
            zero, SingleQubitGate.createHadamard(new Qubit(0)), TestSupport.qubits(1));
        assertEquals(0.5, after.getProbability(0), EPS);
        assertEquals(0.5, after.getProbability(1), EPS);
    }

    @Test
    void stateVectorStrategyRejectsForeignStateTypes() {
        StateVectorStrategy strategy = new StateVectorStrategy();
        QuantumState exotic = new ExoticState();
        Qubit[] q0 = TestSupport.qubits(1);

        assertThrows(IllegalArgumentException.class,
            () -> strategy.applyGate(exotic, SingleQubitGate.createPauliX(q0[0]), q0));
        assertThrows(IllegalArgumentException.class,
            () -> strategy.applyNoise(exotic, new DepolarizingNoise(0.1, q0), q0));
        assertThrows(IllegalArgumentException.class, () -> strategy.measure(exotic, q0, 1));
        assertThrows(IllegalArgumentException.class, () -> strategy.cloneState(exotic));
        assertThrows(IllegalArgumentException.class, () -> strategy.calculateEntropy(exotic));
        assertThrows(IllegalArgumentException.class,
            () -> strategy.calculateFidelity(exotic, StateVector.createZeroState(1)));
        assertThrows(IllegalArgumentException.class,
            () -> strategy.calculateFidelity(StateVector.createZeroState(1), exotic));
    }

    @Test
    void stateVectorStrategyFidelityAndClone() {
        StateVectorStrategy strategy = new StateVectorStrategy();
        StateVector zero = StateVector.createZeroState(1);
        StateVector one = StateVector.fromComputationalBasis(1, 1);

        assertEquals(1.0, strategy.calculateFidelity(zero, zero), EPS);
        assertEquals(0.0, strategy.calculateFidelity(zero, one), EPS);
        assertEquals(0.5, strategy.calculateFidelity(zero, StateVector.createPlusState(1)), 1e-6);

        assertThrows(IllegalArgumentException.class,
            () -> strategy.calculateFidelity(zero, StateVector.createZeroState(2)));

        StateVector clone = (StateVector) strategy.cloneState(zero);
        assertNotSame(zero, clone);
        assertEquals(zero, clone);
    }

    @Test
    void stateVectorStrategyEntropyAndMeasurement() {
        StateVectorStrategy strategy = new StateVectorStrategy();
        assertEquals(0.0, strategy.calculateEntropy(StateVector.createZeroState(2)), EPS);
        assertEquals(1.0, strategy.calculateEntropy(StateVector.createPlusState(1)), 1e-6);

        MeasurementResult result = strategy.measure(
            StateVector.createZeroState(1), TestSupport.qubits(1), 8);
        assertEquals(8, result.getCount(0));
    }

    @Test
    void stateVectorStrategyApplyNoiseKeepsNormalizedState() {
        StateVectorStrategy strategy = new StateVectorStrategy();
        Qubit[] q0 = TestSupport.qubits(1);
        StateVector one = StateVector.fromComputationalBasis(1, 1);
        StateVector noisy = (StateVector) strategy.applyNoise(
            one, new DepolarizingNoise(0.5, q0), q0);
        assertEquals(1.0, noisy.getNorm(), 1e-6);
    }

    // ---------------------------------------------------------------- Qubit

    @Test
    void qubitRejectsNegativeIndexAndDefaultsLabel() {
        assertThrows(IllegalArgumentException.class, () -> new Qubit(-1, "x"));
        assertEquals("q3", new Qubit(3, null).getLabel());
        assertEquals("q4", new Qubit(4).getLabel());
        assertTrue(Qubit.createQubits(0).isEmpty());
        assertEquals("p1", Qubit.createQubits(2, "p").get(1).getLabel());
    }

    @Test
    void qubitLabelIsImmutableAndToStringDescribesState() {
        Qubit q = new Qubit(0, "a");
        assertThrows(UnsupportedOperationException.class, () -> q.setLabel("b"));
        assertFalse(q.isMeasured());
        assertTrue(q.toString().contains("label='a'"));

        q.setMeasured(true);
        assertTrue(q.isMeasured());
        assertTrue(q.toString().contains("measured=true"));
    }

    @Test
    void qubitMeasurementFlagFollowsStoredResult() {
        Qubit q = new Qubit(1);
        assertNull(q.getLastMeasurement());
        q.setLastMeasurement(new Qubit.MeasurementResult(1, 0.25));
        assertTrue(q.isMeasured());
        assertEquals(1, q.getLastMeasurement().getOutcome());

        q.setLastMeasurement(null);
        assertFalse(q.isMeasured());

        q.setLastMeasurement(new Qubit.MeasurementResult(0, 1.0));
        q.reset();
        assertFalse(q.isMeasured());
        assertNull(q.getLastMeasurement());
    }

    @Test
    void qubitEqualityUsesIndexOnly() {
        Qubit a = new Qubit(2, "alpha");
        Qubit b = new Qubit(2, "beta");
        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
        assertNotEquals(a, null);
        assertFalse(a.equals("not a qubit"));
        assertTrue(a.equals(a));
        assertFalse(a.equals(new Qubit(3)));
    }

    @Test
    void qubitMeasurementResultValidatesAndDescribes() {
        assertThrows(IllegalArgumentException.class, () -> new Qubit.MeasurementResult(2, 0.5));
        assertThrows(IllegalArgumentException.class, () -> new Qubit.MeasurementResult(-1, 0.5));
        assertThrows(IllegalArgumentException.class, () -> new Qubit.MeasurementResult(0, 1.5));
        assertThrows(IllegalArgumentException.class, () -> new Qubit.MeasurementResult(0, -0.1));

        Qubit.MeasurementResult r = new Qubit.MeasurementResult(1, 0.75);
        assertEquals(1, r.getOutcome());
        assertEquals(0.75, r.getProbability(), 0.0);
        assertTrue(r.getTimestamp() > 0);
        assertTrue(r.toString().contains("outcome=1"));
        assertFalse(r.equals(new Qubit.MeasurementResult(1, 0.75)));
        assertTrue(r.equals(r));
    }

    // --------------------------------------------------------- DensityMatrix

    @Test
    void densityMatrixNormalizeReturnsSelfWhenUnitTrace() {
        DensityMatrix mixed = DensityMatrix.createMaximallyMixedState(1);
        assertSame(mixed, mixed.normalize());
    }

    @Test
    void densityMatrixToStateVectorRejectsMixedStates() {
        assertThrows(IllegalStateException.class,
            () -> DensityMatrix.createMaximallyMixedState(1).toStateVector());
        assertEquals(StateVector.createZeroState(1),
            DensityMatrix.createZeroState(1).toStateVector());
    }

    @Test
    void densityMatrixApplyOperationRejectsUnsupportedTypes() {
        DensityMatrix zero = DensityMatrix.createZeroState(1);
        MeasurementOperator measurement = new MeasurementOperator(
            Observable.pauliZ(new Qubit(0)), 1);
        assertThrows(UnsupportedOperationException.class, () -> zero.applyOperation(measurement));
        assertThrows(UnsupportedOperationException.class, () -> zero.applyOperation(unknownOperation()));
    }

    @Test
    void densityMatrixCollapseAndAmplitudeEdges() {
        DensityMatrix plus = (DensityMatrix) DensityMatrix.createZeroState(1)
            .applyOperation(SingleQubitGate.createHadamard(new Qubit(0)));
        assertEquals(0.5, plus.getProbability(0), EPS);
        assertEquals(0.5, plus.getAmplitude(0).absSquare(), EPS);

        QuantumState collapsed = plus.collapseToBasisState(new int[]{0}, 1);
        assertEquals(0.0, collapsed.getProbability(0), 1e-9);

        Complex[] eig = {new Complex(1, 0), new Complex(0, 0)};
        QuantumState projected = plus.collapseToEigenvector(new int[]{0}, eig);
        assertEquals(1.0, projected.getProbability(0), 1e-9);
    }

    // ----------------------------------------------------------- StateVector

    @Test
    void stateVectorCollapseOnPlusStateCoversSparseBranches() {
        StateVector b10 = StateVector.fromComputationalBasis(2, 2);
        QuantumState collapsed = b10.collapseToBasisState(new int[]{1}, 0);
        assertEquals(1.0, collapsed.getProbability(2), 1e-9);

        StateVector b10Other = StateVector.fromComputationalBasis(1, 2);
        assertEquals(0.0, b10Other.getProbability(2), 1e-9);

        Complex[] smallEig = {Complex.ONE, new Complex(1e-9, 0)};
        StateVector projected = (StateVector) StateVector.createPlusState(5)
            .collapseToEigenvector(new int[]{0}, smallEig);
        assertEquals(1.0, projected.getNorm(), 1e-6);
    }

    @Test
    void stateVectorToStringTruncatesLongVectors() {
        String text = StateVector.createPlusState(5).toString();
        assertTrue(text.contains("more basis states"));
        assertTrue(text.contains("dim=32"));

        String shortText = StateVector.createPlusState(2).toString();
        assertFalse(shortText.contains("more basis states"));
    }

    @Test
    void stateVectorEqualsCoversLengthAndAmplitudeMismatches() {
        StateVector zero = StateVector.createZeroState(1);
        assertFalse(zero.equals(null));
        assertFalse(zero.equals("string"));
        assertFalse(zero.equals(StateVector.createZeroState(2)));
        assertFalse(zero.equals(StateVector.fromComputationalBasis(1, 1)));
        assertTrue(zero.equals(zero.copy()));
    }

    @Test
    void complexVectorToStringTruncates() {
        ComplexVector v = new ComplexVector(12);
        for (int i = 0; i < 12; i++) {
            v.set(i, i, 0);
        }
        assertTrue(v.toString().contains("..."));
        assertFalse(new ComplexVector(4).toString().contains("..."));
    }

    // --------------------------------------------------- QuantumState defaults

    @Test
    void unsupportedStateTypeRejectsSharedOperations() {
        QuantumState exotic = new ExoticState();
        assertThrows(UnsupportedOperationException.class,
            () -> exotic.applyKrausOperators(new ComplexMatrix[]{ComplexMatrix.identity(2)},
                new int[]{0}, new Random(1)));
        assertThrows(UnsupportedOperationException.class, () -> exotic.partialTrace(new int[]{0}));
        assertThrows(UnsupportedOperationException.class,
            () -> exotic.getExpectationValue(ComplexMatrix.pauliZ()));
    }
}
