package com.quantum.simulator;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.quantum.simulator.math.Complex;
import com.quantum.simulator.math.ComplexMatrix;
import com.quantum.simulator.operations.GateLibrary;
import com.quantum.simulator.operations.MultiQubitGate;
import com.quantum.simulator.operations.QuantumGate;
import com.quantum.simulator.operations.QuantumOperation;
import com.quantum.simulator.operations.SingleQubitGate;
import com.quantum.simulator.state.Qubit;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class OperationsCoverageTest {

    private static final Qubit Q0 = new Qubit(0, "q0");
    private static final Qubit Q1 = new Qubit(1, "q1");
    private static final Qubit Q2 = new Qubit(2, "q2");

    @Test
    void gateLibraryRegistersEveryStandardFactory() {
        GateLibrary library = new GateLibrary();
        Set<String> names = library.getAvailableGates();

        assertTrue(names.containsAll(Set.of("X", "Y", "Z", "H", "S", "T",
            "RX", "RY", "RZ", "CNOT", "CZ", "SWAP", "Toffoli", "CPHASE")));
        assertEquals(14, names.size());
        assertTrue(library.containsGate("X"));
        assertTrue(library.containsGate("CPHASE"));
        assertFalse(library.containsGate("MISSING"));
        assertThrows(IllegalArgumentException.class, () -> library.getGate("X"));
        assertThrows(IllegalArgumentException.class,
            () -> library.createGate("MISSING", Q0));
        assertThrows(UnsupportedOperationException.class, () -> names.add("NEW"));
    }

    @Test
    void standardFactoriesCreateGatesWithExpectedProperties() {
        GateLibrary library = new GateLibrary();

        QuantumGate x = library.createGate("X", Q0);
        assertEquals("X", x.getName());
        assertEquals(1.0, x.getDuration(), 0.0);
        assertArrayEquals(new Qubit[]{Q0}, x.getTargetQubits());
        assertEquals(0, x.getControlQubits().length);
        assertEquals(1, x.getNumQubits());
        assertTrue(x.isSingleQubitGate());
        assertFalse(x.isControlledGate());
        assertTrue(x.isUnitary());
        assertFalse(x.isNoise());
        assertFalse(x.isMeasurement());
        assertArrayEquals(new int[]{0}, x.getMatrixQubitIndices());
        assertEquals(Complex.ONE, x.getMatrix().get(0, 1));
        assertThrows(UnsupportedOperationException.class, () -> x.apply(null));

        QuantumGate y = library.createGate("Y", Q0);
        assertEquals(Complex.ZERO, y.getMatrix().get(0, 0));
        assertEquals(-1.0, y.getMatrix().get(0, 1).imag(), 1e-12);
        assertEquals(1.0, y.getMatrix().get(1, 0).imag(), 1e-12);

        QuantumGate z = library.createGate("Z", Q0);
        assertEquals(-1.0, z.getMatrix().get(1, 1).real(), 1e-12);
        assertEquals(0.0, z.getMatrix().get(1, 1).imag(), 1e-12);

        QuantumGate h = library.createGate("H", Q0);
        assertEquals(1.0 / Math.sqrt(2), h.getMatrix().get(0, 0).real(), 1e-12);
        assertEquals(-1.0 / Math.sqrt(2), h.getMatrix().get(1, 1).real(), 1e-12);

        QuantumGate s = library.createGate("S", Q0);
        assertTrue(s.getName().startsWith("P("));
        assertEquals(0.0, s.getMatrix().get(1, 1).real(), 1e-12);
        assertEquals(1.0, s.getMatrix().get(1, 1).imag(), 1e-12);

        QuantumGate t = library.createGate("T", Q0);
        assertTrue(t.getName().startsWith("P("));
        assertEquals(1.0 / Math.sqrt(2), t.getMatrix().get(1, 1).real(), 1e-12);
        assertEquals(1.0 / Math.sqrt(2), t.getMatrix().get(1, 1).imag(), 1e-12);

        QuantumGate rx = library.createGate("RX", Map.of("angle", Math.PI), Q0);
        assertTrue(rx.getName().startsWith("RX("));
        assertEquals(-1.0, rx.getMatrix().get(0, 1).imag(), 1e-12);
        assertEquals(-1.0, rx.getMatrix().get(1, 0).imag(), 1e-12);

        QuantumGate ry = library.createGate("RY", Map.of("angle", Math.PI), Q0);
        assertTrue(ry.getName().startsWith("RY("));
        assertEquals(-1.0, ry.getMatrix().get(0, 1).real(), 1e-12);
        assertEquals(1.0, ry.getMatrix().get(1, 0).real(), 1e-12);

        QuantumGate rz = library.createGate("RZ", Map.of("angle", Math.PI), Q0);
        assertTrue(rz.getName().startsWith("RZ("));
        assertEquals(-1.0, rz.getMatrix().get(0, 0).imag(), 1e-12);
        assertEquals(1.0, rz.getMatrix().get(1, 1).imag(), 1e-12);

        QuantumGate cnot = library.createGate("CNOT", Q0, Q1);
        assertEquals("CNOT", cnot.getName());
        assertArrayEquals(new Qubit[]{Q1}, cnot.getTargetQubits());
        assertArrayEquals(new Qubit[]{Q0}, cnot.getControlQubits());
        assertEquals(2, cnot.getNumQubits());
        assertFalse(cnot.isSingleQubitGate());
        assertTrue(cnot.isControlledGate());
        assertArrayEquals(new int[]{0, 1}, cnot.getMatrixQubitIndices());
        assertEquals(Complex.ONE, cnot.getMatrix().get(2, 3));
        assertEquals(Complex.ONE, cnot.getMatrix().get(3, 2));
        assertThrows(UnsupportedOperationException.class, () -> cnot.apply(null));

        QuantumGate swap = library.createGate("SWAP", Q0, Q2);
        assertArrayEquals(new Qubit[]{Q0, Q2}, swap.getTargetQubits());
        assertEquals(0, swap.getControlQubits().length);
        assertEquals(2, swap.getNumQubits());

        QuantumGate toffoli = library.createGate("Toffoli", Q0, Q1, Q2);
        assertArrayEquals(new Qubit[]{Q2}, toffoli.getTargetQubits());
        assertArrayEquals(new Qubit[]{Q0, Q1}, toffoli.getControlQubits());
        assertArrayEquals(new int[]{0, 1, 2}, toffoli.getMatrixQubitIndices());
        assertEquals(8, toffoli.getMatrix().rows());
        assertEquals(Complex.ONE, toffoli.getMatrix().get(7, 6));

        QuantumGate cphase = library.createGate("CPHASE", Q0, Q2);
        assertTrue(cphase.getName().startsWith("CPHASE("));
        assertEquals(-1.0, cphase.getMatrix().get(3, 3).real(), 1e-12);
        assertEquals(0.0, cphase.getMatrix().get(3, 3).imag(), 1e-12);

        QuantumGate halfPhase = library.createGate("CPHASE",
            Map.of("angle", 0.5), Q0, Q2);
        assertEquals(Math.cos(0.5), halfPhase.getMatrix().get(3, 3).real(), 1e-12);
        assertEquals(Math.sin(0.5), halfPhase.getMatrix().get(3, 3).imag(), 1e-12);

        QuantumGate cz = library.createGate("CZ", Q1, Q2);
        assertArrayEquals(new int[]{1, 2}, cz.getMatrixQubitIndices());
        assertEquals(-1.0, cz.getMatrix().get(3, 3).real(), 1e-12);
    }

    @Test
    void factoriesValidateQubitCountsAndParameters() {
        GateLibrary library = new GateLibrary();

        assertThrows(IllegalArgumentException.class, () -> library.createGate("X"));
        assertThrows(IllegalArgumentException.class,
            () -> library.createGate("X", Q0, Q1));
        assertThrows(IllegalArgumentException.class, () -> library.createGate("CNOT", Q0));
        assertThrows(IllegalArgumentException.class,
            () -> library.createGate("CNOT", Q0, Q1, Q2));
        assertThrows(IllegalArgumentException.class, () -> library.createGate("CZ", Q0));
        assertThrows(IllegalArgumentException.class, () -> library.createGate("SWAP", Q0));
        assertThrows(IllegalArgumentException.class,
            () -> library.createGate("CPHASE", Q0));
        assertThrows(IllegalArgumentException.class,
            () -> library.createGate("Toffoli", Q0, Q1));

        QuantumGate defaultRx = library.createGate("RX",
            (Map<String, Object>) null, Q0);
        assertEquals(Complex.ONE, defaultRx.getMatrix().get(0, 0));
        assertEquals(Complex.ZERO, defaultRx.getMatrix().get(0, 1));
        assertEquals(0.0, library.createGate("RX", Map.of(), Q0)
            .getMatrix().get(0, 1).real(), 0.0);

        QuantumGate numeric = library.createGate("RY", Map.of("angle", 1), Q0);
        assertTrue(numeric.getName().startsWith("RY("));
        QuantumGate text = library.createGate("RZ", Map.of("angle", "0.5"), Q0);
        assertEquals(Math.cos(0.25), text.getMatrix().get(0, 0).real(), 1e-12);

        IllegalArgumentException badText = assertThrows(IllegalArgumentException.class,
            () -> library.createGate("RX", Map.of("angle", "not-a-number"), Q0));
        assertTrue(badText.getMessage().contains("Failed to create gate 'RX'"));
        assertNotNull(badText.getCause());
        IllegalArgumentException badType = assertThrows(IllegalArgumentException.class,
            () -> library.createGate("RX", Map.of("angle", new Object()), Q0));
        assertTrue(badType.getMessage().contains("Failed to create gate 'RX'"));

        IllegalArgumentException wrapped = assertThrows(IllegalArgumentException.class,
            () -> library.createGate("X", Q0, Q1));
        assertTrue(wrapped.getMessage().contains("Failed to create gate 'X'"));
        assertNotNull(wrapped.getCause());
    }

    @Test
    void gateLibraryRegistrationRemovalAndClear() {
        GateLibrary library = new GateLibrary();
        QuantumGate x = SingleQubitGate.createPauliX(Q0);

        library.registerGate("X", x);
        assertSame(x, library.getGate("X"));
        assertTrue(library.containsGate("X"));
        assertThrows(IllegalArgumentException.class, () -> library.registerGate("X", x));
        assertNotNull(library.createGate("X", Q0));

        library.registerFactory("CUSTOM", (params, qubits) ->
            new QuantumGate.CustomGate("CUSTOM", ComplexMatrix.identity(2),
                new Qubit[]{qubits[0]}, new Qubit[0], 2.5));
        assertTrue(library.containsGate("CUSTOM"));
        QuantumGate custom = library.createGate("CUSTOM", Q0);
        assertEquals("CUSTOM", custom.getName());
        assertEquals(2.5, custom.getDuration(), 0.0);
        assertThrows(IllegalArgumentException.class, () -> library.registerFactory("X",
            (params, qubits) -> SingleQubitGate.createPauliX(qubits[0])));

        library.removeGate("X");
        assertFalse(library.containsGate("X"));
        assertThrows(IllegalArgumentException.class, () -> library.getGate("X"));
        assertThrows(IllegalArgumentException.class, () -> library.createGate("X", Q0));
        library.removeGate("never-registered");

        library.clear();
        assertTrue(library.containsGate("X"));
        assertFalse(library.containsGate("CUSTOM"));
        assertNotNull(library.createGate("X", Q0));
        assertTrue(library.getAvailableGates().contains("Toffoli"));
    }

    @Test
    void gateConstructionValidatesMatrixAndCopiesMutableState() {
        assertThrows(IllegalArgumentException.class,
            () -> new SingleQubitGate("bad", new ComplexMatrix(3, 3), Q0));
        assertThrows(IllegalArgumentException.class,
            () -> new SingleQubitGate("bad", new ComplexMatrix(2, 3), Q0));
        assertThrows(IllegalArgumentException.class,
            () -> new MultiQubitGate("bad", ComplexMatrix.identity(2), new Qubit[]{Q0}));
        assertThrows(NullPointerException.class,
            () -> new SingleQubitGate(null, ComplexMatrix.identity(2), Q0));
        assertThrows(NullPointerException.class,
            () -> new SingleQubitGate("X", ComplexMatrix.identity(2), null));

        MultiQubitGate swap = MultiQubitGate.createSWAP(Q0, Q1);
        ComplexMatrix copied = swap.getMatrix();
        copied.set(0, 0, Complex.ZERO);
        assertEquals(Complex.ONE, swap.getMatrix().get(0, 0));

        Qubit[] targets = swap.getTargetQubits();
        targets[0] = Q2;
        assertArrayEquals(new Qubit[]{Q0, Q1}, swap.getTargetQubits());
        Qubit[] controls = swap.getControlQubits();
        assertEquals(0, controls.length);

        assertTrue(swap.toString().contains("QuantumGate"));
        assertEquals(swap, swap);
        assertNotEquals(swap, null);
        assertNotEquals(swap, "swap");
        assertEquals(MultiQubitGate.createSWAP(Q0, Q1), swap);
        assertEquals(MultiQubitGate.createSWAP(Q0, Q1).hashCode(), swap.hashCode());
        assertNotEquals(MultiQubitGate.createSWAP(Q0, Q2), swap);

        QuantumGate.CustomGate custom = new QuantumGate.CustomGate("X",
            SingleQubitGate.createPauliX(Q0).getMatrix(), new Qubit[]{Q0}, new Qubit[0], 1.0);
        assertNotEquals(SingleQubitGate.createPauliX(Q0), custom);
        assertThrows(UnsupportedOperationException.class, () -> custom.apply(null));
    }

    @Test
    void gateInverseIsTheAdjoint() {
        GateLibrary library = new GateLibrary();
        QuantumGate x = library.createGate("X", Q0);
        QuantumGate inverse = x.getInverse();

        assertTrue(inverse.getName().startsWith("X"));
        assertTrue(inverse.getName().endsWith("\u2020"));
        assertEquals(1, inverse.getNumQubits());
        assertEquals(x.getDuration(), inverse.getDuration(), 0.0);
        assertArrayEquals(x.getTargetQubits(), inverse.getTargetQubits());
        assertArrayEquals(x.getControlQubits(), inverse.getControlQubits());
        assertEquals(x.getMatrix(), inverse.getMatrix());
        assertEquals(x.getMatrix(), inverse.getInverse().getMatrix());
        assertTrue(x.inverse() instanceof QuantumGate);
        assertArrayEquals(new int[]{0}, ((QuantumGate) x.inverse()).getMatrixQubitIndices());

        QuantumGate phase = SingleQubitGate.createPhase(0.5, Q0);
        QuantumGate phaseInverse = phase.getInverse();
        assertEquals(Math.cos(0.5), phase.getMatrix().get(1, 1).real(), 1e-12);
        assertEquals(-Math.sin(0.5), phaseInverse.getMatrix().get(1, 1).imag(), 1e-12);
        assertEquals(ComplexMatrix.identity(2),
            phase.getMatrix().multiply(phaseInverse.getMatrix()));

        QuantumGate cnot = library.createGate("CNOT", Q0, Q1);
        QuantumOperation cnotInverse = cnot.inverse();
        assertTrue(cnotInverse instanceof QuantumGate);
        assertArrayEquals(new int[]{0, 1}, ((QuantumGate) cnotInverse).getMatrixQubitIndices());
        assertEquals(cnot.getMatrix(), ((QuantumGate) cnotInverse).getMatrix());
    }

    @Test
    void multiQubitHelpersUseControlFirstMatrixOrder() {
        QuantumGate toffoli = MultiQubitGate.createToffoli(Q2, Q0, Q1);
        assertArrayEquals(new int[]{2, 0, 1}, toffoli.getMatrixQubitIndices());
        assertArrayEquals(new Qubit[]{Q1}, toffoli.getTargetQubits());
        assertArrayEquals(new Qubit[]{Q2, Q0}, toffoli.getControlQubits());
        assertEquals(Complex.ONE, toffoli.getMatrix().get(6, 7));
        assertEquals(Complex.ONE, toffoli.getMatrix().get(7, 6));

        QuantumGate cswap = MultiQubitGate.createControlledSwap(Q0, Q1, Q2);
        assertEquals("CSWAP", cswap.getName());
        assertArrayEquals(new Qubit[]{Q1, Q2}, cswap.getTargetQubits());
        assertArrayEquals(new Qubit[]{Q0}, cswap.getControlQubits());
        assertArrayEquals(new int[]{0, 1, 2}, cswap.getMatrixQubitIndices());
        assertEquals(Complex.ONE, cswap.getMatrix().get(5, 6));
        assertEquals(Complex.ZERO, cswap.getMatrix().get(5, 5));
        assertEquals(Complex.ZERO, cswap.getMatrix().get(6, 6));

        QuantumGate mcz = MultiQubitGate.createMultiControlledZ(Q0, Q1);
        assertEquals("MCZ2", mcz.getName());
        assertArrayEquals(new Qubit[]{Q0, Q1}, mcz.getTargetQubits());
        assertEquals(0, mcz.getControlQubits().length);
        assertEquals(-1.0, mcz.getMatrix().get(3, 3).real(), 1e-12);
        assertEquals(Complex.ONE, mcz.getMatrix().get(0, 0));
        assertThrows(IllegalArgumentException.class,
            () -> MultiQubitGate.createMultiControlledZ(Q0));
        assertThrows(UnsupportedOperationException.class,
            () -> MultiQubitGate.createMultiControlledZ(new Qubit[11]));

        MultiQubitGate twoTargets = new MultiQubitGate("TWO",
            ComplexMatrix.identity(4), new Qubit[]{Q0, Q1}, 3.0);
        assertEquals(2, twoTargets.getNumQubits());
        assertFalse(twoTargets.isControlledGate());
        assertFalse(twoTargets.isSingleQubitGate());
        assertEquals(3.0, twoTargets.getDuration(), 0.0);
    }
}
