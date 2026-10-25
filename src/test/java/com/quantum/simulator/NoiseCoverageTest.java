package com.quantum.simulator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.quantum.simulator.noise.AmplitudeDampingNoise;
import com.quantum.simulator.noise.DephasingNoise;
import com.quantum.simulator.noise.DepolarizingNoise;
import com.quantum.simulator.noise.ErrorManager;
import com.quantum.simulator.noise.NoiseModel;
import com.quantum.simulator.noise.NoiseProfile;
import com.quantum.simulator.state.DensityMatrix;
import com.quantum.simulator.state.QuantumState;
import com.quantum.simulator.state.Qubit;
import com.quantum.simulator.state.StateVector;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class NoiseCoverageTest {
    private static final double EPS = 1e-9;

    private static Qubit q(int index) {
        return new Qubit(index, "q" + index);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> asMap(Object value) {
        return (Map<String, Object>) value;
    }

    // ------------------------------------------------------------------
    // NoiseProfile storage
    // ------------------------------------------------------------------

    @Test
    void defaultAndNamedProfilesStartEmpty() {
        NoiseProfile profile = new NoiseProfile();
        assertEquals("DefaultNoiseProfile", profile.getName());
        assertTrue(profile.isEmpty());
        assertTrue(profile.getAllNoiseModels().isEmpty());
        assertTrue(profile.getAffectedQubits().isEmpty());
        assertFalse(profile.hasNoise(q(0)));
        assertEquals(0.0, profile.getTotalErrorProbability(q(0)), EPS);
        assertTrue(profile.toString().contains("NoiseProfile['DefaultNoiseProfile']"));
        assertTrue(profile.toString().contains("No noise models configured"));

        NoiseProfile named = new NoiseProfile("custom");
        assertEquals("custom", named.getName());
    }

    @Test
    void qubitNoiseKeepsInsertionOrderAndDuplicates() {
        Qubit q0 = q(0);
        NoiseModel first = new DepolarizingNoise(0.1, q0);
        NoiseModel second = new DephasingNoise(0.2, q0);
        NoiseProfile profile = new NoiseProfile("local");

        profile.addNoise(q0, first);
        profile.addNoise(q0, second);
        profile.addNoise(q0, first);

        List<NoiseModel> models = profile.getNoiseModels(q0);
        assertEquals(3, models.size());
        assertSame(first, models.get(0));
        assertSame(second, models.get(1));
        assertSame(first, models.get(2));
        assertEquals(3, profile.getAllNoiseModels().size());
        assertTrue(profile.hasNoise(new Qubit(0, "renamed")));
        assertFalse(profile.isEmpty());
        assertEquals(1, profile.getAffectedQubits().size());
        assertTrue(profile.getAffectedQubits().contains(q(0)));

        assertThrows(UnsupportedOperationException.class, () -> models.add(first));
        assertThrows(UnsupportedOperationException.class,
            () -> profile.getAllNoiseModels().clear());
        assertThrows(UnsupportedOperationException.class,
            () -> profile.getAffectedQubits().add(q(1)));
    }

    @Test
    void globalNoiseReplacesByKeyAndFiltersByTarget() {
        Qubit q0 = q(0);
        Qubit q1 = q(1);
        NoiseProfile profile = new NoiseProfile();

        profile.addGlobalNoise("d", new DepolarizingNoise(0.1, q0));
        profile.addGlobalNoise("d", new DepolarizingNoise(0.5, q0));

        assertEquals(1, profile.getAllNoiseModels().size());
        assertEquals(0.5, profile.getAllNoiseModels().get(0).getProbability(), EPS);
        assertEquals(1, profile.getNoiseModels(q0).size());
        assertTrue(profile.hasNoise(q0));
        assertFalse(profile.hasNoise(q1));
        assertTrue(profile.getAffectedQubits().contains(q0));
        assertFalse(profile.getAffectedQubits().contains(q1));

        profile.addGlobalNoise("targetless", new DephasingNoise(0.3));
        assertEquals(2, profile.getAllNoiseModels().size());
        assertEquals(1, profile.getNoiseModels(q0).size());
        assertFalse(profile.hasNoise(q1));
        assertFalse(profile.isEmpty());
    }

    @Test
    void totalErrorProbabilitySumsAndCaps() {
        Qubit q0 = q(0);
        NoiseProfile profile = new NoiseProfile();
        profile.addNoise(q0, new DepolarizingNoise(0.4, q0));
        profile.addGlobalNoise("g", new DepolarizingNoise(0.4, q0));

        assertEquals(0.8, profile.getTotalErrorProbability(q0), EPS);

        profile.addGlobalNoise("h", new DepolarizingNoise(0.7, q0));
        assertEquals(1.0, profile.getTotalErrorProbability(q0), 0.0);
        assertEquals(0.0, profile.getTotalErrorProbability(q(1)), EPS);
    }

    @Test
    void removalAndClearingUpdateBothStores() {
        Qubit q0 = q(0);
        Qubit q1 = q(1);
        NoiseModel first = new DepolarizingNoise(0.1, q0);
        NoiseModel second = new DepolarizingNoise(0.2, q0);
        NoiseModel global = new DephasingNoise(0.3, q0);
        NoiseProfile profile = new NoiseProfile();

        profile.addNoise(q0, first);
        profile.addNoise(q0, second);
        profile.addNoise(q1, first);
        profile.addGlobalNoise("g", global);

        profile.removeNoise(q0, first);
        List<NoiseModel> remaining = profile.getNoiseModels(q0);
        assertEquals(2, remaining.size());
        assertSame(second, remaining.get(0));
        assertSame(global, remaining.get(1));

        profile.removeNoise(q0, second);
        assertEquals(1, profile.getNoiseModels(q0).size());
        assertTrue(profile.hasNoise(q0));

        profile.removeNoise(q1, first);
        assertFalse(profile.hasNoise(q1));
        profile.removeNoise(q(5), first);

        profile.clearQubitNoise(q0);
        assertTrue(profile.getNoiseModels(q0).get(0) == global);
        profile.removeGlobalNoise("g");
        assertTrue(profile.isEmpty());

        profile.addNoise(q0, first);
        profile.addGlobalNoise("g", second);
        profile.clearGlobalNoise();
        assertFalse(profile.isEmpty());
        profile.clear();
        assertTrue(profile.isEmpty());
    }

    @Test
    void combineMergesModelsWithoutMutatingInputs() {
        Qubit q0 = q(0);
        NoiseProfile first = new NoiseProfile("A");
        NoiseModel firstLocal = new DepolarizingNoise(0.1, q0);
        first.addNoise(q0, firstLocal);
        first.addGlobalNoise("shared", new DephasingNoise(0.2, q0));

        NoiseProfile second = new NoiseProfile("B");
        NoiseModel secondLocal = new DephasingNoise(0.3, q0);
        second.addNoise(q0, secondLocal);
        second.addGlobalNoise("shared", new DepolarizingNoise(0.4, q0));

        NoiseProfile combined = first.combine(second);

        assertEquals("A+B", combined.getName());
        List<NoiseModel> models = combined.getNoiseModels(q0);
        assertEquals(3, models.size());
        assertSame(firstLocal, models.get(0));
        assertSame(secondLocal, models.get(1));
        assertEquals(0.4, models.get(2).getProbability(), EPS);
        assertEquals(0.8, combined.getTotalErrorProbability(q0), EPS);

        assertEquals(2, first.getNoiseModels(q0).size());
        assertEquals(2, second.getNoiseModels(q0).size());
        assertThrows(NullPointerException.class, () -> first.combine(null));
    }

    @Test
    void toStringListsStoresAndModels() {
        Qubit q0 = q(0);
        NoiseProfile profile = new NoiseProfile("local");
        profile.addNoise(q0, new DepolarizingNoise(0.1, q0));

        String localText = profile.toString();
        assertTrue(localText.contains("NoiseProfile['local']"));
        assertTrue(localText.contains("Qubit-specific noise:"));
        assertTrue(localText.contains("Qubit q0:"));
        assertTrue(localText.contains("DepolarizingNoise"));
        assertFalse(localText.contains("Global noise models:"));

        profile.addGlobalNoise("g", new DephasingNoise(0.2, q0));
        assertTrue(profile.toString().contains("Global noise models:"));
    }

    // ------------------------------------------------------------------
    // NoiseProfile JSON-style mapping
    // ------------------------------------------------------------------

    @Test
    void toMapExportsGlobalAndQubitEntries() {
        Qubit q0 = q(0);
        NoiseProfile profile = new NoiseProfile("io");
        profile.addGlobalNoise("g", new DepolarizingNoise(0.01));
        profile.addNoise(q0, new DepolarizingNoise(0.02, q0));
        profile.addNoise(q0, new DephasingNoise(0.03, q0));

        Map<String, Object> map = profile.toMap();
        assertEquals(2, map.size());
        assertEquals(Arrays.asList("global_noise", "qubit_noise"),
            new ArrayList<>(map.keySet()));

        Map<String, Object> global = asMap(map.get("global_noise"));
        assertEquals(0.01, (Double) global.get("depolarizing"), EPS);

        Map<String, Object> qubitNoise = asMap(map.get("qubit_noise"));
        Map<String, Object> zero = asMap(qubitNoise.get("0"));
        assertEquals(0.02, (Double) zero.get("depolarizing"), EPS);
        assertEquals(0.03, (Double) zero.get("dephasing"), EPS);
    }

    @Test
    void loadFromMapRebuildsProfileAndSkipsMissingMaps() {
        Qubit q0 = q(0);
        NoiseProfile source = new NoiseProfile("io");
        source.addGlobalNoise("g", new DepolarizingNoise(0.01));
        source.addNoise(q0, new DepolarizingNoise(0.02, q0));
        source.addNoise(q0, new DephasingNoise(0.03, q0));
        Map<String, Object> map = source.toMap();

        NoiseProfile loaded = new NoiseProfile("loaded");
        loaded.addNoise(q(9), new DepolarizingNoise(0.9, q(9)));
        loaded.loadFromMap(map);

        assertEquals(3, loaded.getAllNoiseModels().size());
        assertEquals(2, loaded.getNoiseModels(q0).size());
        assertFalse(loaded.hasNoise(q(9)));
        assertEquals(0.05, loaded.getTotalErrorProbability(q0), EPS);
        assertEquals(1, loaded.getAffectedQubits().size());
        assertTrue(loaded.getAllNoiseModels().get(0) instanceof DepolarizingNoise);
        assertTrue(loaded.getAllNoiseModels().get(1) instanceof DephasingNoise);
        assertTrue(loaded.getAllNoiseModels().get(2) instanceof DepolarizingNoise);

        loaded.loadFromMap(null);
        loaded.loadFromMap(Map.of());
        assertEquals(3, loaded.getAllNoiseModels().size());
    }

    @Test
    void loadFromMapAcceptsAliasesAndStringProbabilities() {
        Map<String, Object> qubitZero = new LinkedHashMap<>();
        qubitZero.put("depolarizing_noise", "0.25");
        qubitZero.put("  DEPHASING_NOISE ", 0.5);
        qubitZero.put("phase_damping", 0.125);
        qubitZero.put("amplitude_damping_noise", "0.0625");

        Map<String, Object> qubitNoise = new LinkedHashMap<>();
        qubitNoise.put("1", qubitZero);
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("qubit_noise", qubitNoise);

        NoiseProfile profile = new NoiseProfile();
        profile.loadFromMap(map);

        List<NoiseModel> models = profile.getNoiseModels(q(1));
        assertEquals(4, models.size());
        assertTrue(models.get(0) instanceof DepolarizingNoise);
        assertEquals(0.25, models.get(0).getProbability(), EPS);
        assertTrue(models.get(1) instanceof DephasingNoise);
        assertEquals(0.5, models.get(1).getProbability(), EPS);
        assertTrue(models.get(2) instanceof DephasingNoise);
        assertEquals(0.125, models.get(2).getProbability(), EPS);
        assertTrue(models.get(3) instanceof AmplitudeDampingNoise);
        assertEquals(0.0625, models.get(3).getProbability(), EPS);
        assertEquals(1, models.get(3).getTargetQubits()[0].getIndex());

        Map<String, Object> globalMap = new LinkedHashMap<>();
        globalMap.put("Amplitude_Damping", 0.2);
        Map<String, Object> global = new LinkedHashMap<>();
        global.put("global_noise", globalMap);
        NoiseProfile globalProfile = new NoiseProfile();
        globalProfile.loadFromMap(global);
        assertEquals(1, globalProfile.getAllNoiseModels().size());
        NoiseModel globalModel = globalProfile.getAllNoiseModels().get(0);
        assertTrue(globalModel instanceof AmplitudeDampingNoise);
        assertEquals(0.2, globalModel.getProbability(), EPS);
        assertEquals(0, globalModel.getTargetQubits().length);
        assertEquals(0.0, globalProfile.getTotalErrorProbability(q(0)), EPS);
    }

    @Test
    void loadFromMapSupportsSimpleLayout() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("noise_type", "dephasing");
        map.put("probability", "0.3");

        NoiseProfile profile = new NoiseProfile();
        profile.loadFromMap(map);

        assertEquals(1, profile.getAllNoiseModels().size());
        NoiseModel model = profile.getAllNoiseModels().get(0);
        assertTrue(model instanceof DephasingNoise);
        assertEquals(0.3, model.getProbability(), EPS);
        assertEquals(0, model.getTargetQubits().length);
    }

    @Test
    void loadFromMapIgnoresMalformedEntries() {
        Map<String, Object> globalNoise = new LinkedHashMap<>();
        globalNoise.put("unknown", 0.1);
        globalNoise.put("depolarizing", "bogus");
        globalNoise.put("dephasing", null);
        Map<String, Object> global = new LinkedHashMap<>();
        global.put("global_noise", globalNoise);

        NoiseProfile profile = new NoiseProfile();
        profile.loadFromMap(global);
        assertTrue(profile.isEmpty());

        Map<String, Object> qubitNoise = new LinkedHashMap<>();
        qubitNoise.put("notAnIndex", Map.of("depolarizing", 0.1));
        qubitNoise.put("0", "notAMap");
        qubitNoise.put("1", Map.of("depolarizing", "bogus"));
        qubitNoise.put("2", Map.of("dephasing", 0.2));
        Map<String, Object> qubits = new LinkedHashMap<>();
        qubits.put("qubit_noise", qubitNoise);

        profile.loadFromMap(qubits);
        assertEquals(1, profile.getAllNoiseModels().size());
        assertEquals(2, profile.getNoiseModels(q(2)).get(0)
            .getTargetQubits()[0].getIndex());

        Map<String, Object> ignored = new LinkedHashMap<>();
        ignored.put("global_noise", "text");
        ignored.put("qubit_noise", "text");
        NoiseProfile ignoredProfile = new NoiseProfile();
        ignoredProfile.loadFromMap(ignored);
        assertTrue(ignoredProfile.isEmpty());
    }

    @Test
    void simpleLayoutRejectsBadTypeAndProbability() {
        Map<String, Object> unknownType = new LinkedHashMap<>();
        unknownType.put("noise_type", "bogus");
        unknownType.put("probability", 0.1);
        assertThrows(IllegalArgumentException.class,
            () -> new NoiseProfile().loadFromMap(unknownType));

        Map<String, Object> badProbability = new LinkedHashMap<>();
        badProbability.put("noise_type", "dephasing");
        badProbability.put("probability", "abc");
        assertThrows(NumberFormatException.class,
            () -> new NoiseProfile().loadFromMap(badProbability));
    }

    // ------------------------------------------------------------------
    // ErrorManager statistics and reporting
    // ------------------------------------------------------------------

    @Test
    void freshManagerReportsNeutralStatistics() {
        ErrorManager manager = new ErrorManager();

        assertEquals(1.0, manager.getCurrentFidelity(), EPS);
        assertEquals(0, manager.getErrorCount());
        assertEquals(0, manager.getTotalOperations());
        assertEquals(0.0, manager.getErrorRate(), EPS);
        assertTrue(manager.getErrorLog().isEmpty());

        ErrorManager.ErrorReport report = manager.generateErrorReport();
        assertTrue(report.getErrors().isEmpty());
        assertEquals(1.0, report.getTotalFidelity(), EPS);
        assertEquals(0, report.getErrorCount());
        assertEquals(0, report.getTotalOperations());
        assertEquals(0.0, report.getErrorRate(), EPS);
        assertTrue(report.getGenerationTime() > 0);
        assertTrue(report.getErrorsPerQubit().isEmpty());
        assertTrue(report.getErrorsByType().isEmpty());

        String text = report.toString();
        assertTrue(text.contains("Average Fidelity: 1.000000"));
        assertTrue(text.contains("Error Rate: 0.0000% (0/0)"));
        assertTrue(text.contains("Total Errors: 0"));
        assertFalse(text.contains("Recent Errors"));

        assertThrows(UnsupportedOperationException.class,
            () -> manager.getErrorLog().add(null));
    }

    @Test
    void nullOrEmptyProfileLeavesStateAndCountersAlone() {
        DensityMatrix state = DensityMatrix.createZeroState(1);
        ErrorManager manager = new ErrorManager();

        assertSame(state, manager.applyNoise(state, null));
        assertSame(state, manager.applyNoise(state, new NoiseProfile()));
        assertEquals(0, manager.getTotalOperations());
        assertEquals(1.0, manager.getCurrentFidelity(), EPS);
    }

    @Test
    void successfulChannelRecordsFidelityRateAndTraceDistance() {
        Qubit q0 = q(0);
        DensityMatrix zero = DensityMatrix.createZeroState(1);
        NoiseProfile profile = new NoiseProfile();
        profile.addNoise(q0, new DepolarizingNoise(1.0, q0));
        ErrorManager manager = new ErrorManager();

        QuantumState noisy = manager.applyNoise(zero, profile);

        // p = 1 mixes the three non-identity Paulis equally, so the populations are 1/3 and 2/3.
        assertEquals(1.0 / 3.0, noisy.getProbability(0), EPS);
        assertEquals(2.0 / 3.0, noisy.getProbability(1), EPS);
        assertEquals(1.0 / 3.0, manager.getCurrentFidelity(), EPS);
        assertEquals(1, manager.getErrorCount());
        assertEquals(1, manager.getTotalOperations());
        assertEquals(1.0, manager.getErrorRate(), EPS);
        assertEquals(1, manager.getErrorLog().size());

        ErrorManager.ErrorLogEntry entry = manager.getErrorLog().get(0);
        assertEquals("DepolarizingNoise", entry.getNoiseType());
        assertEquals(0, entry.getAffectedQubit().getIndex());
        assertEquals(1.0, entry.getNoiseProbability(), EPS);
        assertEquals(2.0 / 3.0, entry.getStateDistance(), EPS);
        assertFalse(entry.hasError());
        assertNull(entry.getErrorMessage());
        assertTrue(entry.toString().contains("distance=0.6667"));
    }

    @Test
    void targetlessGlobalModelIsAppliedToEveryQubit() {
        DensityMatrix zero = DensityMatrix.createZeroState(2);
        NoiseProfile profile = new NoiseProfile();
        profile.addGlobalNoise("d", new DepolarizingNoise(0.0));
        ErrorManager manager = new ErrorManager();

        QuantumState noisy = manager.applyNoise(zero, profile);

        assertEquals(2, manager.getTotalOperations());
        assertEquals(2, manager.getErrorCount());
        assertEquals(1.0, manager.getCurrentFidelity(), EPS);
        assertEquals(1.0, noisy.getProbability(0), EPS);
        assertEquals(2, manager.getErrorLog().size());
        assertEquals(0, manager.getErrorLog().get(0).getAffectedQubit().getIndex());
        assertEquals(1, manager.getErrorLog().get(1).getAffectedQubit().getIndex());
        assertEquals(0.0, manager.getErrorLog().get(0).getStateDistance(), EPS);
    }

    @Test
    void failedApplicationIsLoggedButNotCountedAsCompleted() {
        Qubit missing = new Qubit(5, "q5");
        DensityMatrix zero = DensityMatrix.createZeroState(1);
        NoiseProfile profile = new NoiseProfile();
        profile.addNoise(missing, new DepolarizingNoise(0.1, missing));
        ErrorManager manager = new ErrorManager();

        QuantumState noisy = manager.applyNoise(zero, profile);

        assertSame(zero, noisy);
        assertEquals(1, manager.getTotalOperations());
        assertEquals(0, manager.getErrorCount());
        assertEquals(0.0, manager.getErrorRate(), EPS);
        assertEquals(1.0, manager.getCurrentFidelity(), EPS);
        assertEquals(1, manager.getErrorLog().size());

        ErrorManager.ErrorLogEntry entry = manager.getErrorLog().get(0);
        assertTrue(entry.hasError());
        assertTrue(entry.getErrorMessage().contains("Failed to apply noise"));
        assertEquals(0.0, entry.getStateDistance(), EPS);
        assertEquals("DepolarizingNoise", entry.getNoiseType());
        assertEquals(5, entry.getAffectedQubit().getIndex());
        assertTrue(entry.toString().contains("Failed to apply noise"));
    }

    @Test
    void fidelityIsAveragedOverApplyCallsAndResetRestores() {
        Qubit q0 = q(0);
        DensityMatrix zero = DensityMatrix.createZeroState(1);
        NoiseProfile identityNoise = new NoiseProfile();
        identityNoise.addGlobalNoise("zero", new DepolarizingNoise(0.0));
        NoiseProfile fullNoise = new NoiseProfile();
        fullNoise.addNoise(q0, new DepolarizingNoise(1.0, q0));

        ErrorManager manager = new ErrorManager();
        manager.applyNoise(zero, identityNoise);
        manager.applyNoise(zero, identityNoise);
        assertEquals(1.0, manager.getCurrentFidelity(), EPS);

        manager.applyNoise(zero, fullNoise);
        assertEquals(7.0 / 9.0, manager.getCurrentFidelity(), 1e-12);
        assertEquals(3, manager.getTotalOperations());
        assertEquals(3, manager.getErrorCount());

        ErrorManager.ErrorReport report = manager.generateErrorReport();
        assertEquals(3, report.getErrors().size());
        assertEquals(3, report.getTotalOperations());

        manager.applyNoise(zero, fullNoise);
        assertEquals(2.0 / 3.0, manager.getCurrentFidelity(), EPS);
        assertEquals(3, report.getErrors().size());
        assertEquals(7.0 / 9.0, report.getTotalFidelity(), 1e-12);

        manager.reset();
        assertEquals(1.0, manager.getCurrentFidelity(), EPS);
        assertEquals(0, manager.getTotalOperations());
        assertEquals(0, manager.getErrorCount());
        assertEquals(0.0, manager.getErrorRate(), EPS);
        assertTrue(manager.getErrorLog().isEmpty());
    }

    @Test
    void stateVectorChannelsAreSupported() {
        StateVector zero = StateVector.createZeroState(1);
        NoiseProfile profile = new NoiseProfile();
        profile.addGlobalNoise("phase", new DephasingNoise(0.0));
        ErrorManager manager = new ErrorManager();

        QuantumState noisy = manager.applyNoise(zero, profile);

        assertTrue(noisy instanceof StateVector);
        assertEquals(1.0, manager.getCurrentFidelity(), EPS);
        assertEquals(1, manager.getErrorCount());
        assertEquals(0.0, manager.getErrorLog().get(0).getStateDistance(), EPS);
    }

    @Test
    void calculateFidelityComparesStatesAndValidatesQubitCounts() {
        ErrorManager manager = new ErrorManager();
        DensityMatrix zero = DensityMatrix.createZeroState(1);
        DensityMatrix one = StateVector.fromComputationalBasis(1, 1).toDensityMatrix();
        StateVector zeroVector = StateVector.createZeroState(1);

        assertEquals(1.0, manager.calculateFidelity(zero, zero), EPS);
        assertEquals(0.0, manager.calculateFidelity(zero, one), EPS);
        assertEquals(1.0, manager.calculateFidelity(zeroVector, zero), EPS);
        assertThrows(IllegalArgumentException.class,
            () -> manager.calculateFidelity(zero, DensityMatrix.createZeroState(2)));
    }

    @Test
    void errorReportAggregatesEntriesByQubitAndType() {
        Qubit q0 = q(0);
        Qubit q1 = q(1);
        ErrorManager.ErrorLogEntry first =
            new ErrorManager.ErrorLogEntry(1000L, "DepolarizingNoise", q0, 0.1, 0.25);
        ErrorManager.ErrorLogEntry second =
            new ErrorManager.ErrorLogEntry(2000L, "DephasingNoise",
                new Qubit(0, "other"), 0.2, 0.05);
        ErrorManager.ErrorLogEntry failed =
            new ErrorManager.ErrorLogEntry(3000L, "DepolarizingNoise", q1, 0.3, "boom");

        List<ErrorManager.ErrorLogEntry> entries =
            new ArrayList<>(List.of(first, second, failed));
        ErrorManager.ErrorReport report = new ErrorManager.ErrorReport(
            entries, 0.9, 2, 3, 2.0 / 3.0);
        entries.clear();

        assertEquals(3, report.getErrors().size());
        assertEquals(0.9, report.getTotalFidelity(), EPS);
        assertEquals(2, report.getErrorCount());
        assertEquals(3, report.getTotalOperations());
        assertEquals(2.0 / 3.0, report.getErrorRate(), 1e-12);

        Map<Qubit, Integer> perQubit = report.getErrorsPerQubit();
        assertEquals(2, perQubit.get(q0).intValue());
        assertEquals(1, perQubit.get(q1).intValue());

        Map<String, Integer> byType = report.getErrorsByType();
        assertEquals(2, byType.get("DepolarizingNoise").intValue());
        assertEquals(1, byType.get("DephasingNoise").intValue());

        String text = report.toString();
        assertTrue(text.contains("Average Fidelity: 0.900000"));
        assertTrue(text.contains("Error Rate: 66.6667% (2/3)"));
        assertTrue(text.contains("Total Errors: 2"));
        assertTrue(text.contains("Recent Errors:"));
        assertTrue(text.contains("boom"));

        assertEquals(1000L, first.getTimestamp());
        assertEquals(0, first.getAffectedQubit().getIndex());
        assertEquals(0.1, first.getNoiseProbability(), EPS);
        assertNull(first.getErrorMessage());
        assertFalse(first.hasError());
        assertEquals(0.0, failed.getStateDistance(), EPS);
        assertTrue(failed.hasError());
        assertEquals("boom", failed.getErrorMessage());
        assertTrue(first.toString().contains("(p=0.100, distance=0.2500)"));
        assertTrue(failed.toString().contains("(p=0.300): boom"));

        assertThrows(UnsupportedOperationException.class, () -> report.getErrors().clear());
    }

    @Test
    void errorReportShowsOnlyTheLastTenEntries() {
        Qubit q0 = q(0);
        List<ErrorManager.ErrorLogEntry> entries = new ArrayList<>();
        for (int i = 0; i < 12; i++) {
            entries.add(new ErrorManager.ErrorLogEntry(
                i, "Noise", q0, 0.1, String.format("entry-%02d", i)));
        }
        ErrorManager.ErrorReport report =
            new ErrorManager.ErrorReport(entries, 1.0, 12, 12, 1.0);

        String text = report.toString();
        assertFalse(text.contains("entry-00"));
        assertFalse(text.contains("entry-01"));
        assertTrue(text.contains("entry-02"));
        assertTrue(text.contains("entry-11"));
    }

    @Test
    void reportSnapshotSurvivesLaterApplications() {
        Qubit q0 = q(0);
        DensityMatrix zero = DensityMatrix.createZeroState(1);
        NoiseProfile profile = new NoiseProfile();
        profile.addNoise(q0, new DepolarizingNoise(1.0, q0));
        ErrorManager manager = new ErrorManager();

        manager.applyNoise(zero, profile);
        ErrorManager.ErrorReport snapshot = manager.generateErrorReport();

        manager.applyNoise(zero, profile);

        assertEquals(1, snapshot.getErrors().size());
        assertEquals(1, snapshot.getTotalOperations());
        assertEquals(1.0 / 3.0, snapshot.getTotalFidelity(), EPS);
        assertEquals(2, manager.getErrorLog().size());
    }
}
