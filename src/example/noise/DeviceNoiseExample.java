package example.noise;

import com.mechalikh.qforge.classical.ShotRunner;
import com.mechalikh.qforge.config.SimulationBackend;
import com.mechalikh.qforge.config.SimulationConfig;
import com.mechalikh.qforge.core.QuantumSimulator;
import com.mechalikh.qforge.events.QuantumEvent;
import com.mechalikh.qforge.math.RandomGenerator;
import com.mechalikh.qforge.measurement.MeasurementOperator;
import com.mechalikh.qforge.noise.DeviceNoiseModel;
import com.mechalikh.qforge.operations.QuantumOperation;
import com.mechalikh.qforge.operations.SingleQubitGate;
import com.mechalikh.qforge.state.DensityMatrix;
import com.mechalikh.qforge.state.QuantumState;
import com.mechalikh.qforge.state.Qubit;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Random;

/**
 * Measures the three device-level noise effects separately.
 *
 * <p>Idle relaxation is measured on a density matrix: qubit 0 is excited, a later gate on qubit 1
 * exposes qubit 0 to a 1000 ns window with T1 = 2000 ns, and the remaining population is compared
 * with {@code exp(-t / T1)}. Crosstalk is measured by preparing a superposition on a neighbour,
 * applying a gate on the adjacent qubit, and reading the surviving coherence of the neighbour,
 * which the phase-damping channel contracts by {@code 1 - rate}. Readout confusion is measured
 * with {@link ShotRunner} on a state vector, so the reported histogram is compared with the
 * product of the per-qubit confusion probabilities.</p>
 *
 * <p>Each effect is configured alone, so its number is not entangled with the other two. Event
 * timestamps are set explicitly rather than through the shared example helper, because idle
 * relaxation is driven by the gap between scheduled events.</p>
 */
public final class DeviceNoiseExample {

    private static final Instant START = Instant.parse("2026-01-01T00:00:00Z");
    private static final int READOUT_SHOTS = 4000;
    private static final long SEED = 20261029L;

    private DeviceNoiseExample() {
    }

    public static void main(String[] args) throws Exception {
        System.out.println("=== Device noise ===");
        idleRelaxation();
        crosstalk();
        readoutError();
    }

    private static void idleRelaxation() throws Exception {
        double t1 = 2000.0;
        double idle = 1000.0;
        Qubit q0 = new Qubit(0, "q0");
        Qubit q1 = new Qubit(1, "q1");

        SimulationConfig config = new SimulationConfig();
        config.setNumQubits(2);
        config.setBackend(SimulationBackend.DENSITY_MATRIX);
        config.setDeviceNoise(new DeviceNoiseModel(
            new double[] {t1, 0.0}, new double[] {t1, 0.0},
            new double[2], new double[2], 0.0, 20.0));

        List<QuantumEvent> events = List.of(
            event("x_q0", SingleQubitGate.createPauliX(q0), START),
            event("x_q1", SingleQubitGate.createPauliX(q1), START.plusNanos((long) idle)));

        DensityMatrix state = (DensityMatrix) run(config, events);
        double measured = state.getProbability(2) + state.getProbability(3);
        double expected = Math.exp(-idle / t1);

        System.out.println("\nIdle relaxation (state |1> on q0, a later gate on q1 exposes it):");
        System.out.printf(Locale.ROOT, "  T1 = %.0f ns, idle window = %.0f ns%n", t1, idle);
        System.out.printf(Locale.ROOT, "  P(q0 = 1) measured %.9f, exp(-t/T1) %.9f, "
            + "difference %.2e%n", measured, expected, Math.abs(measured - expected));
    }

    private static void crosstalk() throws Exception {
        double rate = 0.3;
        System.out.println("\nCrosstalk (q1 in |+>, one gate on q0, q1 is an idle neighbour):");
        System.out.println("  rate   <X> on q1");
        for (double configured : new double[] {0.0, rate}) {
            double expectation = crosstalkExpectationX(configured);
            System.out.printf(Locale.ROOT, "  %.2f   %.6f%n", configured, expectation);
        }
        double measured = crosstalkExpectationX(rate);
        double expected = 1.0 - rate;
        System.out.printf(Locale.ROOT, "  expected <X> = 1 - rate = %.6f, difference %.2e%n",
            expected, Math.abs(measured - expected));
    }

    private static double crosstalkExpectationX(double rate) throws Exception {
        Qubit q0 = new Qubit(0, "q0");
        Qubit q1 = new Qubit(1, "q1");

        SimulationConfig config = new SimulationConfig();
        config.setNumQubits(3);
        config.setBackend(SimulationBackend.DENSITY_MATRIX);
        config.setDeviceNoise(new DeviceNoiseModel(
            new double[3], new double[3], new double[3], new double[3], rate, 20.0));

        List<QuantumEvent> events = List.of(
            event("h_q1", SingleQubitGate.createHadamard(q1), START),
            event("x_q0", SingleQubitGate.createPauliX(q0), START.plusNanos(100)));

        DensityMatrix state = (DensityMatrix) run(config, events);
        DensityMatrix reduced = (DensityMatrix) state.partialTrace(new int[] {1});
        return 2.0 * reduced.getMatrix().get(0, 1).abs();
    }

    private static void readoutError() throws Exception {
        double p01q0 = 0.1;
        double p10q1 = 0.2;
        Qubit q0 = new Qubit(0, "q0");
        Qubit q1 = new Qubit(1, "q1");

        SimulationConfig config = new SimulationConfig();
        config.setNumQubits(2);
        config.setBackend(SimulationBackend.STATE_VECTOR);
        config.addClassicalRegister("c", 2);
        config.setDeviceNoise(new DeviceNoiseModel(
            new double[2], new double[2],
            new double[] {p01q0, 0.0}, new double[] {0.0, p10q1}, 0.0, 20.0));

        RandomGenerator.setRandom(new Random(SEED));
        ShotRunner.ShotStatistics statistics = ShotRunner.run(config,
            () -> measurementEvents(q0, q1), READOUT_SHOTS);

        double[] expected = {
            (1.0 - p01q0) * p10q1,
            p01q0 * p10q1,
            (1.0 - p01q0) * (1.0 - p10q1),
            p01q0 * (1.0 - p10q1)
        };

        System.out.printf(Locale.ROOT, "%nReadout confusion (q0 = |0>, q1 = |1>, %d shots):%n",
            READOUT_SHOTS);
        System.out.println("  c   measured   expected");
        for (long value = 0; value < 4; value++) {
            System.out.printf(Locale.ROOT, "  %d   %.4f     %.4f%n", value,
                statistics.frequency("c", value), expected[(int) value]);
        }
    }

    private static List<QuantumEvent> measurementEvents(Qubit q0, Qubit q1) {
        List<QuantumEvent> events = new ArrayList<>();
        events.add(event("x_q1", SingleQubitGate.createPauliX(q1), START));
        events.add(event("measure_q0", MeasurementOperator
            .computationalBasis(new Qubit[] {q0}, 1)
            .withClassicalTarget("c", 0), START.plusMillis(1)));
        events.add(event("measure_q1", MeasurementOperator
            .computationalBasis(new Qubit[] {q1}, 1)
            .withClassicalTarget("c", 1), START.plusMillis(2)));
        return events;
    }

    private static QuantumEvent event(String name, QuantumOperation operation, Instant time) {
        return new QuantumEvent.Builder(name)
            .withScheduledTime(time)
            .withOperation(operation)
            .build();
    }

    private static QuantumState run(SimulationConfig config, List<QuantumEvent> events)
            throws Exception {
        QuantumSimulator simulator = new QuantumSimulator();
        simulator.initialize(config);
        for (QuantumEvent event : events) {
            simulator.scheduleEvent(event);
        }
        return simulator.run().getFinalState();
    }
}
