package com.quantum.simulator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.quantum.simulator.measurement.MeasurementOperator;
import com.quantum.simulator.measurement.MeasurementResult;
import com.quantum.simulator.measurement.Observable;
import com.quantum.simulator.math.ComplexMatrix;
import com.quantum.simulator.state.DensityMatrix;
import com.quantum.simulator.state.Qubit;
import com.quantum.simulator.state.StateVector;
import java.util.List;
import org.junit.jupiter.api.Test;

class MeasurementTest {
    private static final double EPS = 1e-9;

    private static StateVector bellState() {
        double s = 1 / Math.sqrt(2);
        return new StateVector(new com.quantum.simulator.math.Complex[]{
            new com.quantum.simulator.math.Complex(s, 0),
            com.quantum.simulator.math.Complex.ZERO,
            com.quantum.simulator.math.Complex.ZERO,
            new com.quantum.simulator.math.Complex(s, 0)}, 2);
    }

    @Test
    void computationalMeasurementCollapsesBellState() {
        Qubit q0 = new Qubit(0, "q0");
        Qubit q1 = new Qubit(1, "q1");
        MeasurementOperator measurement = MeasurementOperator.computationalBasis(
            new Qubit[]{q0, q1}, 4000);

        MeasurementOperator.MeasurementOutcome outcome = measurement.measureAndCollapse(bellState());
        MeasurementResult result = outcome.getResult();

        assertEquals(0, result.getCount(1));
        assertEquals(0, result.getCount(2));
        assertTrue(result.getCount(0) > 0 || result.getCount(3) > 0);
        assertEquals(1.0, result.getExpectationValue(), 0.02);

        StateVector collapsed = (StateVector) outcome.getCollapsedState();
        double p00 = collapsed.getProbability(0);
        double p11 = collapsed.getProbability(3);
        assertEquals(1.0, p00 + p11, EPS);
        assertTrue(p00 > 0.999 || p11 > 0.999);
        assertEquals(1.0, Observable.pauliZ(new Qubit[]{q0, q1}).measure(collapsed), EPS);
    }

    @Test
    void partialMeasurementCollapsesEntangledPartner() {
        Qubit q0 = new Qubit(0, "q0");
        StateVector collapsed = (StateVector) Observable.pauliZ(q0).collapse(bellState(), 0);
        assertEquals(1.0, collapsed.getProbability(0), EPS);
        assertEquals(0.0, collapsed.getProbability(3), EPS);

        StateVector collapsedOne = (StateVector) Observable.pauliZ(q0).collapse(bellState(), 1);
        assertEquals(1.0, collapsedOne.getProbability(3), EPS);
        assertEquals(0.0, collapsedOne.getProbability(0), EPS);
    }

    @Test
    void xBasisMeasurementOnPlusState() {
        Qubit q0 = new Qubit(0, "q0");
        double s = 1 / Math.sqrt(2);
        StateVector plus = new StateVector(new com.quantum.simulator.math.Complex[]{
            new com.quantum.simulator.math.Complex(s, 0),
            new com.quantum.simulator.math.Complex(s, 0)}, 1);
        Observable x = Observable.pauliX(q0);
        double[] probabilities = x.getOutcomeProbabilities(plus);
        assertEquals(0.0, probabilities[0], EPS);
        assertEquals(1.0, probabilities[1], EPS);
        assertEquals(1.0, x.measure(plus), EPS);
        assertEquals(1.0, x.getOutcomeEigenvalues()[1], EPS);
    }

    @Test
    void nonDestructiveMeasurementPreservesState() {
        Qubit q0 = new Qubit(0, "q0");
        double s = 1 / Math.sqrt(2);
        StateVector plus = new StateVector(new com.quantum.simulator.math.Complex[]{
            new com.quantum.simulator.math.Complex(s, 0),
            new com.quantum.simulator.math.Complex(s, 0)}, 1);
        MeasurementOperator measurement = new MeasurementOperator(
            "Z", Observable.pauliZ(q0), 10, false);
        MeasurementOperator.MeasurementOutcome outcome = measurement.measureAndCollapse(plus);
        assertEquals(0.5, outcome.getCollapsedState().getProbability(0), EPS);
        assertEquals(0.5, outcome.getCollapsedState().getProbability(1), EPS);
    }

    @Test
    void densityMatrixCollapseProducesPureState() {
        DensityMatrix bell = bellState().toDensityMatrix();
        DensityMatrix collapsed = (DensityMatrix) Observable.pauliZ(new Qubit(0, "q0"))
            .collapse(bell, 1);
        assertEquals(1.0, collapsed.getProbability(3), EPS);
        assertEquals(1.0, collapsed.getPurity(), EPS);
    }

    @Test
    void measurementThroughSimulatorIsRecorded() throws Exception {
        Qubit[] qubits = TestSupport.qubits(2);
        MeasurementOperator measurement = MeasurementOperator.computationalBasis(qubits, 500);
        var result = TestSupport.run(2, com.quantum.simulator.config.SimulationBackend.STATE_VECTOR,
            List.of(TestSupport.event("measure", measurement, qubits)));
        assertEquals(1, result.getMeasurements().size());
        assertNotNull(result.getMeasurement("ComputationalBasisMeasurement"));
        assertEquals(1L, result.getMetric("measurements").longValue());
    }

    @Test
    void pauliStringsParseAndPartialTraceWorks() {
        Qubit[] qubits = TestSupport.qubits(2);
        Observable parsed = Observable.fromString("ZI", qubits);
        ComplexMatrix expected = ComplexMatrix.pauliZ()
            .tensorProduct(ComplexMatrix.identity(2));
        assertEquals(expected, parsed.getOperator());

        Observable reduced = parsed.partialTrace(new int[]{0});
        assertEquals(2.0, reduced.getOperator().get(0, 0).real(), EPS);
        assertEquals(-2.0, reduced.getOperator().get(1, 1).real(), EPS);
    }
}
