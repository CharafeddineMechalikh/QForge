package com.mechalikh.qforge;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.mechalikh.qforge.events.QuantumEvent;
import com.mechalikh.qforge.math.Complex;
import com.mechalikh.qforge.math.ComplexMatrix;
import com.mechalikh.qforge.operations.SingleQubitGate;
import com.mechalikh.qforge.state.Qubit;
import com.mechalikh.qforge.state.StateVector;
import example.CircuitFactory;
import example.algorithms.GroversAlgorithm;
import example.algorithms.QuantumErrorCorrection;
import example.algorithms.QuantumPhaseEstimation;
import example.algorithms.QuantumSVM;
import example.algorithms.QuantumWalk;
import example.algorithms.TrotterSimulation;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

class AlgorithmTest {
    private static final double EPS = 1e-9;

    @Test
    void groverFindsTargetState() throws Exception {
        Qubit[] qubits = TestSupport.qubits(3);
        StateVector state = TestSupport.runStateVector(3,
            Arrays.asList(GroversAlgorithm.createGroversCircuit(qubits, 5)));
        assertTrue(state.getProbability(5) > 0.9);
        double max = Arrays.stream(state.getProbabilities()).max().orElse(0);
        assertEquals(max, state.getProbability(5), EPS);
    }

    @Test
    void phaseEstimationRecoversKnownPhase() throws Exception {
        int precision = 3;
        Qubit[] estimation = new Qubit[precision];
        for (int i = 0; i < precision; i++) {
            estimation[i] = new Qubit(i, "est" + i);
        }
        Qubit eigen = new Qubit(precision, "eigen");
        QuantumEvent[] circuit = QuantumPhaseEstimation.createPhaseEstimationCircuit(
            estimation, new Qubit[]{eigen},
            new QuantumEvent[]{TestSupport.event("T", SingleQubitGate.createT(eigen), eigen)},
            precision);

        List<QuantumEvent> ordered = new ArrayList<>();
        ordered.add(TestSupport.event("prepare", SingleQubitGate.createPauliX(eigen), eigen));
        ordered.addAll(Arrays.asList(circuit));

        StateVector state = TestSupport.runStateVector(precision + 1, ordered);
        double probability = state.getProbability((1 << 1) | 1); // estimate 001, eigenstate |1>
        assertTrue(probability > 0.999);
    }

    @Test
    void qftFollowedByInverseQftIsIdentity() throws Exception {
        Qubit[] qubits = TestSupport.qubits(3);
        List<QuantumEvent> ordered = new ArrayList<>();
        ordered.add(TestSupport.event("prepare", SingleQubitGate.createPauliX(qubits[2]), qubits[2]));
        ordered.addAll(Arrays.asList(CircuitFactory.createQuantumFourierTransform(qubits)));
        ordered.addAll(QuantumPhaseEstimation.createInverseQFT(qubits, "IQFT"));

        StateVector state = TestSupport.runStateVector(3, ordered);
        assertTrue(Math.abs(state.getAmplitude(1).abs() - 1.0) < EPS);
    }

    @Test
    void trotterMatchesExactHeisenbergEvolution() throws Exception {
        Qubit[] qubits = TestSupport.qubits(2);
        TrotterSimulation.Hamiltonian hamiltonian = new TrotterSimulation.Hamiltonian(List.of(
            new TrotterSimulation.HamiltonianTerm("XX", new int[]{0, 1}, 1.0),
            new TrotterSimulation.HamiltonianTerm("YY", new int[]{0, 1}, 1.0),
            new TrotterSimulation.HamiltonianTerm("ZZ", new int[]{0, 1}, 1.0)));
        double time = 0.5;
        int steps = 8;

        List<QuantumEvent> ordered = new ArrayList<>();
        ordered.add(TestSupport.event("prepare", SingleQubitGate.createPauliX(qubits[1]), qubits[1]));
        ordered.addAll(Arrays.asList(TrotterSimulation.createTrotterCircuit(
            qubits, hamiltonian, time, steps)));
        StateVector state = TestSupport.runStateVector(2, ordered);

        ComplexMatrix x = ComplexMatrix.pauliX();
        ComplexMatrix y = ComplexMatrix.pauliY();
        ComplexMatrix z = ComplexMatrix.pauliZ();
        ComplexMatrix h = x.tensorProduct(x).add(y.tensorProduct(y)).add(z.tensorProduct(z));
        ComplexMatrix exactUnitary = expMinusIHt(h, time);
        Complex[] initial = {Complex.ZERO, Complex.ONE, Complex.ZERO, Complex.ZERO};
        Complex overlap = Complex.ZERO;
        for (int row = 0; row < 4; row++) {
            Complex exact = Complex.ZERO;
            for (int col = 0; col < 4; col++) {
                exact = exact.add(exactUnitary.get(row, col).multiply(initial[col]));
            }
            overlap = overlap.add(exact.conjugate().multiply(state.getAmplitude(row)));
        }
        assertTrue(overlap.absSquare() > 0.999);
    }

    @Test
    void shorCodeProducesLogicalZeroCodewords() throws Exception {
        Qubit[] qubits = TestSupport.qubits(9);
        StateVector state = TestSupport.runStateVector(9,
            Arrays.asList(QuantumErrorCorrection.createShorCode(qubits)));

        boolean[] expected = new boolean[512];
        for (int blockMask = 0; blockMask < 8; blockMask++) {
            int index = 0;
            for (int block = 0; block < 3; block++) {
                int bit = (blockMask >> block) & 1;
                for (int i = 0; i < 3; i++) {
                    if (bit == 1) {
                        index |= 1 << (8 - (3 * block + i));
                    }
                }
            }
            expected[index] = true;
        }
        double mass = 0;
        for (int i = 0; i < 512; i++) {
            if (expected[i]) {
                mass += state.getProbability(i);
            } else {
                assertEquals(0.0, state.getProbability(i), EPS);
            }
        }
        assertTrue(mass > 0.999);
    }

    @Test
    void continuousTimeWalkSpreadsToNeighbours() throws Exception {
        Qubit[] positions = TestSupport.qubits(3);
        List<QuantumEvent> ordered = new ArrayList<>();
        ordered.add(TestSupport.event("prepare", SingleQubitGate.createPauliX(positions[1]), positions[1]));
        ordered.addAll(Arrays.asList(QuantumWalk.createContinuousTimeWalk(
            positions, null, null, 1.0)));
        StateVector state = TestSupport.runStateVector(3, ordered);
        assertTrue(state.getProbability(1) > 0.1);
        assertTrue(state.getProbability(3) > 0.1);
    }

    @Test
    void swapTestKernelMatchesDirectStateOverlap() throws Exception {
        double[] first = {0.3, 0.7};
        double[] second = {0.9, 0.1};

        Qubit[] registerOne = {new Qubit(0, "a0"), new Qubit(1, "a1")};
        Qubit[] registerTwo = {new Qubit(2, "b0"), new Qubit(3, "b1")};
        QuantumEvent[] kernelCircuit = QuantumSVM.createQSVMKernelCircuit(
            registerOne, registerTwo, first, second, "ZZFeatureMap");
        StateVector joint = TestSupport.runStateVector(5, Arrays.asList(kernelCircuit));

        double ancillaZero = 0;
        for (int i = 0; i < 32; i++) {
            if ((i & 1) == 0) {
                ancillaZero += joint.getProbability(i);
            }
        }
        double swapKernel = 2 * ancillaZero - 1;

        Qubit[] directOne = {new Qubit(0, "a0"), new Qubit(1, "a1")};
        Qubit[] directTwo = {new Qubit(0, "b0"), new Qubit(1, "b1")};
        StateVector stateOne = TestSupport.runStateVector(2,
            Arrays.asList(QuantumSVM.createQSVMFeatureMap(directOne, first, "ZZFeatureMap")));
        StateVector stateTwo = TestSupport.runStateVector(2,
            Arrays.asList(QuantumSVM.createQSVMFeatureMap(directTwo, second, "ZZFeatureMap")));
        double directKernel = Math.pow(stateOne.innerProduct(stateTwo).abs(), 2);

        assertEquals(directKernel, swapKernel, 1e-8);
        assertTrue(swapKernel >= 0 && swapKernel <= 1);
    }

    private static ComplexMatrix expMinusIHt(ComplexMatrix hamiltonian, double time) {
        ComplexMatrix[] eigen = hamiltonian.eigenDecomposition();
        ComplexMatrix d = eigen[0];
        ComplexMatrix v = eigen[1];
        ComplexMatrix phases = new ComplexMatrix(d.rows(), d.rows());
        for (int i = 0; i < d.rows(); i++) {
            double energy = d.get(i, i).real();
            phases.set(i, i, new Complex(Math.cos(-energy * time), Math.sin(-energy * time)));
        }
        return v.multiply(phases).multiply(v.adjoint());
    }
}
