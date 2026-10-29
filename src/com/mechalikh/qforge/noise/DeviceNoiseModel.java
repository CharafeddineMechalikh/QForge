package com.mechalikh.qforge.noise;

import com.mechalikh.qforge.state.Qubit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Device-level noise parameters that go beyond per-gate Kraus channels.
 *
 * <p>The model holds, per qubit, an energy relaxation time {@code T1}, a coherence time
 * {@code T2}, and the two readout confusion probabilities. It also holds a crosstalk rate and
 * a gate duration, both in nanoseconds. Empty or zero entries disable the corresponding
 * effect, so a default instance is inert.</p>
 *
 * <p>The engine consumes the model in three places:
 * {@link com.mechalikh.qforge.core.SimulationContext} applies a
 * {@link ThermalRelaxationModel} to qubits that sit idle between events, applies a
 * {@link DephasingNoise} to neighbouring qubits while a gate executes, and
 * {@link com.mechalikh.qforge.measurement.MeasurementOperator} passes the readout model to
 * the sampler. The JSON shape accepted by {@link #loadFromMap(Map)} is:</p>
 *
 * <pre>{@code
 * "device_noise": {
 *   "t1_ns": [50000, 48000],
 *   "t2_ns": [30000, 29000],
 *   "readout_p01": [0.02, 0.02],
 *   "readout_p10": [0.03, 0.03],
 *   "crosstalk_rate": 0.001,
 *   "gate_time_ns": 50
 * }
 * }</pre>
 *
 * <p>Times absent from the file stay disabled. Values are validated when the model is built,
 * and a {@code T2} larger than twice {@code T1} is rejected.</p>
 */
public final class DeviceNoiseModel {
    private final double[] t1;
    private final double[] t2;
    private final double[] readoutP01;
    private final double[] readoutP10;
    private final double crosstalkRate;
    private final double gateTimeNanos;

    /**
     * Creates a model from per-qubit arrays.
     *
     * @param t1 relaxation times in nanoseconds, zero or negative to disable a qubit
     * @param t2 coherence times in nanoseconds, zero or negative to disable a qubit
     * @param readoutP01 probability of reading one for a zero, per qubit
     * @param readoutP10 probability of reading zero for a one, per qubit
     * @param crosstalkRate phase-damping strength applied to idle neighbours of an active gate
     * @param gateTimeNanos nominal gate duration in nanoseconds
     * @throws IllegalArgumentException if an array length differs, a readout probability is
     *         outside {@code [0, 1]}, a positive {@code T2} exceeds twice {@code T1}, or a rate
     *         is outside {@code [0, 1]}
     */
    public DeviceNoiseModel(double[] t1, double[] t2, double[] readoutP01, double[] readoutP10,
                            double crosstalkRate, double gateTimeNanos) {
        int width = t1.length;
        if (t2.length != width || readoutP01.length != width || readoutP10.length != width) {
            throw new IllegalArgumentException("Device noise arrays must have equal length");
        }
        for (int i = 0; i < width; i++) {
            if (t2[i] > 0.0 && t1[i] > 0.0 && t2[i] > 2.0 * t1[i]) {
                throw new IllegalArgumentException("T2 cannot exceed 2 * T1 for qubit " + i);
            }
            if (!probability(readoutP01[i]) || !probability(readoutP10[i])) {
                throw new IllegalArgumentException("Readout probability out of range for qubit " + i);
            }
        }
        if (!probability(crosstalkRate)) {
            throw new IllegalArgumentException("Crosstalk rate must be between 0 and 1");
        }
        if (gateTimeNanos < 0.0) {
            throw new IllegalArgumentException("Gate time cannot be negative");
        }
        this.t1 = t1.clone();
        this.t2 = t2.clone();
        this.readoutP01 = readoutP01.clone();
        this.readoutP10 = readoutP10.clone();
        this.crosstalkRate = crosstalkRate;
        this.gateTimeNanos = gateTimeNanos;
    }

    private static boolean probability(double value) {
        return value >= 0.0 && value <= 1.0;
    }

    /**
     * Creates an inert model for a number of qubits.
     *
     * @param numQubits number of qubits covered by the arrays
     * @return a model with every effect disabled
     * @throws IllegalArgumentException if the qubit count is negative
     */
    public static DeviceNoiseModel disabled(int numQubits) {
        if (numQubits < 0) {
            throw new IllegalArgumentException("Qubit count cannot be negative");
        }
        return new DeviceNoiseModel(new double[numQubits], new double[numQubits],
            new double[numQubits], new double[numQubits], 0.0, 0.0);
    }

    /**
     * Reports whether no effect is configured.
     *
     * @return {@code true} when relaxation, readout and crosstalk are all disabled
     */
    public boolean isEmpty() {
        return !hasRelaxation() && !hasReadout() && crosstalkRate == 0.0;
    }

    /**
     * Reports whether any qubit has a positive {@code T1} and {@code T2}.
     *
     * @return {@code true} if relaxation is configured
     */
    public boolean hasRelaxation() {
        for (int i = 0; i < t1.length; i++) {
            if (t1[i] > 0.0 && t2[i] > 0.0) {
                return true;
            }
        }
        return false;
    }

    /**
     * Reports whether any readout probability is nonzero.
     *
     * @return {@code true} if readout error is configured
     */
    public boolean hasReadout() {
        for (int i = 0; i < readoutP01.length; i++) {
            if (readoutP01[i] > 0.0 || readoutP10[i] > 0.0) {
                return true;
            }
        }
        return false;
    }

    /**
     * Returns the relaxation time of one qubit.
     *
     * @param qubit qubit index
     * @return {@code T1} in nanoseconds, zero when disabled
     */
    public double getT1(int qubit) {
        return inRange(qubit, t1) ? t1[qubit] : 0.0;
    }

    /**
     * Returns the coherence time of one qubit.
     *
     * @param qubit qubit index
     * @return {@code T2} in nanoseconds, zero when disabled
     */
    public double getT2(int qubit) {
        return inRange(qubit, t2) ? t2[qubit] : 0.0;
    }

    /**
     * Returns the crosstalk rate.
     *
     * @return the phase-damping strength applied to idle neighbours
     */
    public double getCrosstalkRate() {
        return crosstalkRate;
    }

    /**
     * Returns the nominal gate duration.
     *
     * @return the duration in nanoseconds
     */
    public double getGateTimeNanos() {
        return gateTimeNanos;
    }

    /**
     * Returns the number of qubits covered by the parameter arrays.
     *
     * @return the array width
     */
    public int getNumQubits() {
        return t1.length;
    }

    private boolean inRange(int qubit, double[] values) {
        return qubit >= 0 && qubit < values.length;
    }

    /**
     * Builds a thermal relaxation channel for one qubit and a duration.
     *
     * @param qubit qubit index
     * @param durationNanos exposure duration in nanoseconds
     * @return the channel, or {@code null} when the qubit has no relaxation configured or the
     *         duration is not positive
     */
    public NoiseModel relaxationFor(int qubit, double durationNanos) {
        if (durationNanos <= 0.0 || !inRange(qubit, t1) || t1[qubit] <= 0.0 || t2[qubit] <= 0.0) {
            return null;
        }
        return new ThermalRelaxationModel(t1[qubit], t2[qubit], durationNanos, new Qubit[0]);
    }

    /**
     * Builds the readout model.
     *
     * @return the readout confusion, or {@code null} when readout error is disabled
     */
    public ReadoutErrorModel getReadout() {
        if (!hasReadout()) {
            return null;
        }
        return new ReadoutErrorModel(readoutP01, readoutP10);
    }

    /**
     * Builds the crosstalk channel for an idle neighbour of an active gate.
     *
     * @return the phase-damping strength for one gate, or {@code null} when disabled
     */
    public DephasingNoise crosstalkForIdle() {
        if (crosstalkRate <= 0.0) {
            return null;
        }
        return new DephasingNoise(Math.min(1.0, crosstalkRate), new Qubit[0]);
    }

    /**
     * Reads a JSON-shaped map into a new model.
     *
     * <p>Recognised keys are {@code t1_ns}, {@code t2_ns}, {@code readout_p01},
     * {@code readout_p10}, {@code crosstalk_rate} and {@code gate_time_ns}. Missing arrays are
     * filled with zeros. When several arrays are present the longest one sets the width and
     * shorter arrays are padded with zeros.</p>
     *
     * @param map configuration map, may be {@code null}
     * @return a new model, inert when the map is null or empty
     */
    public static DeviceNoiseModel fromMap(Map<String, Object> map) {
        if (map == null || map.isEmpty()) {
            return disabled(0);
        }
        double[] t1 = numbers(map.get("t1_ns"));
        double[] t2 = numbers(map.get("t2_ns"));
        double[] p01 = numbers(map.get("readout_p01"));
        double[] p10 = numbers(map.get("readout_p10"));
        int width = Math.max(Math.max(t1.length, t2.length), Math.max(p01.length, p10.length));
        double[] paddedT1 = pad(t1, width);
        double[] paddedT2 = pad(t2, width);
        double[] paddedP01 = pad(p01, width);
        double[] paddedP10 = pad(p10, width);
        return new DeviceNoiseModel(paddedT1, paddedT2, paddedP01, paddedP10,
            number(map.get("crosstalk_rate"), 0.0), number(map.get("gate_time_ns"), 0.0));
    }

    private static double[] numbers(Object value) {
        if (!(value instanceof List<?>)) {
            return new double[0];
        }
        List<?> list = (List<?>) value;
        double[] result = new double[list.size()];
        for (int i = 0; i < result.length; i++) {
            result[i] = number(list.get(i), 0.0);
        }
        return result;
    }

    private static double number(Object value, double fallback) {
        return value instanceof Number ? ((Number) value).doubleValue() : fallback;
    }

    private static double[] pad(double[] values, int width) {
        double[] result = new double[width];
        System.arraycopy(values, 0, result, 0, values.length);
        return result;
    }

    /**
     * Serializes the model into the JSON shape accepted by {@link #fromMap(Map)}.
     *
     * @return a new map with every key present
     */
    public Map<String, Object> toMap() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("t1_ns", list(t1));
        map.put("t2_ns", list(t2));
        map.put("readout_p01", list(readoutP01));
        map.put("readout_p10", list(readoutP10));
        map.put("crosstalk_rate", crosstalkRate);
        map.put("gate_time_ns", gateTimeNanos);
        return map;
    }

    private static List<Double> list(double[] values) {
        List<Double> result = new ArrayList<>(values.length);
        for (double value : values) {
            result.add(value);
        }
        return result;
    }

    /**
     * Returns a description of the model.
     *
     * @return the qubit count, whether relaxation and readout are enabled, and the crosstalk
     *         rate
     */
    @Override
    public String toString() {
        return "DeviceNoiseModel[qubits=" + t1.length + ", relaxation=" + hasRelaxation()
            + ", readout=" + hasReadout() + ", crosstalk=" + crosstalkRate + "]";
    }
}
