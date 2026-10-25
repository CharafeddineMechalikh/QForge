package com.mechalikh.qforge;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.mechalikh.qforge.config.SimulationBackend;
import com.mechalikh.qforge.core.SimulationResult;
import com.mechalikh.qforge.math.Complex;
import com.mechalikh.qforge.operations.MultiQubitGate;
import com.mechalikh.qforge.operations.SingleQubitGate;
import com.mechalikh.qforge.state.DensityMatrix;
import com.mechalikh.qforge.state.Qubit;
import com.mechalikh.qforge.state.StateVector;
import java.util.List;
import org.junit.jupiter.api.Test;

class GateTest {
    private static final double EPS = 1e-9;

    @Test
    void hadamardCreatesUniformSuperposition() throws Exception {
        Qubit[] qubits = TestSupport.qubits(1);
        StateVector state = TestSupport.runStateVector(1, List.of(
            TestSupport.event("H", SingleQubitGate.createHadamard(qubits[0]), qubits[0])));
        assertEquals(0.5, state.getProbability(0), EPS);
        assertEquals(0.5, state.getProbability(1), EPS);
    }

    @Test
    void cnotCreatesBellState() throws Exception {
        Qubit[] qubits = TestSupport.qubits(2);
        StateVector state = TestSupport.runStateVector(2, List.of(
            TestSupport.event("H", SingleQubitGate.createHadamard(qubits[0]), qubits[0]),
            TestSupport.event("CNOT", MultiQubitGate.createCNOT(qubits[0], qubits[1]), qubits[1])));
        assertEquals(0.5, state.getProbability(0), EPS);
        assertEquals(0.0, state.getProbability(1), EPS);
        assertEquals(0.0, state.getProbability(2), EPS);
        assertEquals(0.5, state.getProbability(3), EPS);
    }

    @Test
    void czFlipsOnlyAllOnesAmplitude() {
        Complex half = new Complex(0.5, 0);
        StateVector plusPlus = new StateVector(new Complex[]{
            half, half, half, half}, 2);
        StateVector out = plusPlus.applyGate(
            MultiQubitGate.createCZ(new Qubit(0, "q0"), new Qubit(1, "q1")));
        assertEquals(0.5, out.getAmplitude(0).real(), EPS);
        assertEquals(-0.5, out.getAmplitude(3).real(), EPS);
        assertEquals(0.25, out.getProbability(0), EPS);
        assertEquals(0.25, out.getProbability(3), EPS);
    }

    @Test
    void cphaseAddsPhaseToAllOnes() {
        StateVector eleven = StateVector.fromComputationalBasis(3, 2);
        StateVector out = eleven.applyGate(MultiQubitGate.createCPHASE(
            Math.PI / 2, new Qubit(0, "q0"), new Qubit(1, "q1")));
        assertEquals(1.0, out.getProbability(3), EPS);
        assertEquals(0.0, out.getAmplitude(3).real(), EPS);
        assertEquals(1.0, out.getAmplitude(3).imag(), EPS);
    }

    @Test
    void toffoliFlipsTargetOnlyWhenBothControlsSet() {
        Qubit[] qubits = TestSupport.qubits(3);
        StateVector state = StateVector.createZeroState(3)
            .applyGate(SingleQubitGate.createPauliX(qubits[0]))
            .applyGate(SingleQubitGate.createPauliX(qubits[1]))
            .applyGate(MultiQubitGate.createToffoli(qubits[0], qubits[1], qubits[2]));
        assertEquals(1.0, state.getProbability(7), EPS);

        StateVector onlyOneControl = StateVector.createZeroState(3)
            .applyGate(SingleQubitGate.createPauliX(qubits[0]))
            .applyGate(MultiQubitGate.createToffoli(qubits[0], qubits[1], qubits[2]));
        assertEquals(1.0, onlyOneControl.getProbability(4), EPS);
    }

    @Test
    void cnotWorksOnNonAdjacentQubits() throws Exception {
        Qubit[] qubits = TestSupport.qubits(3);
        StateVector state = TestSupport.runStateVector(3, List.of(
            TestSupport.event("H", SingleQubitGate.createHadamard(qubits[0]), qubits[0]),
            TestSupport.event("CNOT", MultiQubitGate.createCNOT(qubits[0], qubits[2]), qubits[2])));
        assertEquals(0.5, state.getProbability(0), EPS);
        assertEquals(0.5, state.getProbability(5), EPS);
    }

    @Test
    void densityMatrixBackendMatchesStateVector() throws Exception {
        Qubit[] qubits = TestSupport.qubits(2);
        SimulationResult result = TestSupport.run(2, SimulationBackend.DENSITY_MATRIX, List.of(
            TestSupport.event("H", SingleQubitGate.createHadamard(qubits[0]), qubits[0]),
            TestSupport.event("CNOT", MultiQubitGate.createCNOT(qubits[0], qubits[1]), qubits[1])));
        DensityMatrix state = (DensityMatrix) result.getFinalState();
        assertEquals(0.5, state.getProbability(0), EPS);
        assertEquals(0.5, state.getProbability(3), EPS);
        assertEquals(1.0, state.getPurity(), EPS);
        double trace = 0;
        for (int i = 0; i < 4; i++) {
            trace += state.getMatrix().get(i, i).real();
        }
        assertEquals(1.0, trace, EPS);
    }

    @Test
    void controlledMeasurementOfQubitIndicesIsConsistent() {
        Qubit control = new Qubit(0, "c");
        Qubit target = new Qubit(1, "t");
        var gate = MultiQubitGate.createCNOT(control, target);
        assertEquals(1, gate.getTargetQubits().length);
        assertEquals(1, gate.getControlQubits().length);
        assertTrue(gate.isControlledGate());
        assertEquals(0, gate.getMatrixQubitIndices()[0]);
        assertEquals(1, gate.getMatrixQubitIndices()[1]);    }
}
