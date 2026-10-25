package com.quantum.simulator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.quantum.simulator.config.SimulationBackend;
import com.quantum.simulator.config.SimulationConfig;
import com.quantum.simulator.core.QuantumSimulator;
import com.quantum.simulator.core.SimulationResult;
import com.quantum.simulator.math.Complex;
import com.quantum.simulator.noise.AmplitudeDampingNoise;
import com.quantum.simulator.noise.DephasingNoise;
import com.quantum.simulator.noise.DepolarizingNoise;
import com.quantum.simulator.noise.NoiseProfile;
import com.quantum.simulator.operations.SingleQubitGate;
import com.quantum.simulator.state.DensityMatrix;
import com.quantum.simulator.state.Qubit;
import com.quantum.simulator.state.StateVector;
import java.util.List;
import org.junit.jupiter.api.Test;

class NoiseTest {
    private static final double EPS = 1e-9;

    @Test
    void depolarizingChannelHasAnalyticPopulations() {
        Qubit q0 = new Qubit(0, "q0");
        DensityMatrix zero = DensityMatrix.createZeroState(1);
        DensityMatrix noisy = (DensityMatrix) new DepolarizingNoise(0.1, q0).apply(zero);
        assertEquals(1.0 - 0.2 / 3.0, noisy.getProbability(0), EPS);
        assertEquals(0.0, noisy.getMatrix().get(0, 1).abs(), EPS);
        assertEquals(1.0, trace(noisy), EPS);
    }

    @Test
    void dephasingChannelReducesCoherence() {
        Qubit q0 = new Qubit(0, "q0");
        double s = 1 / Math.sqrt(2);
        DensityMatrix plus = new StateVector(new Complex[]{
            new Complex(s, 0), new Complex(s, 0)}, 1).toDensityMatrix();
        DensityMatrix noisy = (DensityMatrix) new DephasingNoise(0.3, q0).apply(plus);
        assertEquals(0.35, noisy.getMatrix().get(0, 1).abs(), EPS);
        assertEquals(0.5, noisy.getProbability(0), EPS);
    }

    @Test
    void amplitudeDampingRelaxesToGroundState() {
        Qubit q0 = new Qubit(0, "q0");
        DensityMatrix one = StateVector.fromComputationalBasis(1, 1).toDensityMatrix();
        DensityMatrix damped = (DensityMatrix) new AmplitudeDampingNoise(1.0, q0).apply(one);
        assertEquals(1.0, damped.getProbability(0), EPS);
    }

    @Test
    void multiQubitDepolarizingUsesAllPauliStrings() {
        Qubit[] qubits = {new Qubit(0, "q0"), new Qubit(1, "q1")};
        DensityMatrix noisy = (DensityMatrix) new DepolarizingNoise(0.5, qubits)
            .apply(DensityMatrix.createZeroState(2));
        assertEquals(0.6, noisy.getProbability(0), EPS);
        assertEquals(1.0, trace(noisy), EPS);
    }

    @Test
    void independentMultiQubitDampingFactorizes() {
        Qubit[] qubits = {new Qubit(0, "q0"), new Qubit(1, "q1")};
        DensityMatrix oneOne = StateVector.fromComputationalBasis(3, 2).toDensityMatrix();
        DensityMatrix damped = (DensityMatrix) new AmplitudeDampingNoise(0.4, qubits).apply(oneOne);
        assertEquals(0.36, damped.getProbability(3), EPS);
        assertEquals(0.16, damped.getProbability(0), EPS);
    }

    @Test
    void configNoiseIsAppliedAfterGates() throws Exception {
        SimulationConfig config = new SimulationConfig();
        config.setNumQubits(1);
        config.setBackend(SimulationBackend.DENSITY_MATRIX);
        NoiseProfile profile = new NoiseProfile("test");
        profile.addGlobalNoise("depolarizing", new DepolarizingNoise(0.1));
        config.setNoiseProfile(profile);

        QuantumSimulator simulator = new QuantumSimulator();
        simulator.initialize(config);
        Qubit q0 = simulator.getContext().getQubit(0);
        simulator.scheduleEvent(TestSupport.event(
            "H", SingleQubitGate.createHadamard(q0), q0));
        SimulationResult result = simulator.run();

        double coherence = result.getFinalState()
            .getExpectationValue(com.quantum.simulator.math.ComplexMatrix.pauliX()) / 2.0;
        assertEquals(0.5, result.getFinalState().getProbability(0), EPS);
        assertEquals(0.5 * (1.0 - 4.0 * 0.1 / 3.0), coherence, EPS);
        assertTrue(result.getFinalState().getPurity() < 1.0);
    }

    @Test
    void stateVectorNoiseTrajectoriesAreDistributedCorrectly() {
        Qubit q0 = new Qubit(0, "q0");
        StateVector zero = StateVector.createZeroState(1);
        int ones = 0;
        int runs = 2000;
        for (int i = 0; i < runs; i++) {
            StateVector result = (StateVector) new DepolarizingNoise(1.0, q0).apply(zero);
            if (result.getProbability(1) > 0.5) {
                ones++;
            }
        }
        assertEquals(2.0 / 3.0, ones / (double) runs, 0.04);
    }

    @Test
    void fidelityAndTraceDistanceAreCorrect() {
        DensityMatrix zero = DensityMatrix.createZeroState(1);
        DensityMatrix one = StateVector.fromComputationalBasis(1, 1).toDensityMatrix();
        DensityMatrix mixed = DensityMatrix.createMaximallyMixedState(1);

        assertEquals(0.0, DensityMatrix.fidelity(zero, one), EPS);
        assertEquals(0.5, DensityMatrix.fidelity(zero, mixed), EPS);
        assertEquals(1.0, DensityMatrix.fidelity(mixed, mixed), EPS);
        assertEquals(1.0, DensityMatrix.traceDistance(zero, one), EPS);
        assertEquals(0.5, DensityMatrix.traceDistance(zero, mixed), EPS);
    }

    private static double trace(DensityMatrix state) {
        double trace = 0;
        for (int i = 0; i < (1 << state.getNumQubits()); i++) {
            trace += state.getMatrix().get(i, i).real();
        }
        return trace;
    }
}
