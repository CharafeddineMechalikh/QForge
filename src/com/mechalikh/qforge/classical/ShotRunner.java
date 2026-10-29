package com.mechalikh.qforge.classical;

import com.mechalikh.qforge.config.SimulationConfig;
import com.mechalikh.qforge.core.QuantumSimulator;
import com.mechalikh.qforge.core.SimulationError;
import com.mechalikh.qforge.core.SimulationResult;
import com.mechalikh.qforge.events.QuantumEvent;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Runs a circuit many times and aggregates classical register values.
 *
 * <p>The engine executes one shot per initialization, because an event is consumed when it is
 * dispatched and a destructive measurement collapses the state. This runner creates a fresh
 * {@link QuantumSimulator} run for every shot from the same configuration and the same
 * circuit factory, then counts the final classical register values. It is the execution model
 * needed for feed-forward protocols, where the result of one measurement decides which gates
 * are applied later in the same shot.</p>
 *
 * <p>Each shot is independent. The configuration should therefore request a backend whose
 * randomness is per run, and the circuit factory must build new {@link QuantumEvent} objects
 * for each call, because an event cannot be executed twice.</p>
 */
public final class ShotRunner {

    /**
     * Builds the event list of one shot.
     *
     * <p>The factory is called once per shot and must create new events with the identifiers
     * used only within that shot.</p>
     */
    @FunctionalInterface
    public interface CircuitFactory {
        /**
         * Builds the events of one shot.
         *
         * @return events in the order they should be scheduled
         */
        List<QuantumEvent> build();
    }

    private ShotRunner() {
    }

    /**
     * Runs a circuit for a number of shots.
     *
     * @param config configuration used for every shot; registers declared here are the ones
     *        reported
     * @param factory circuit factory called once per shot
     * @param shots number of shots, positive
     * @return the aggregated register values
     * @throws SimulationError if a shot fails to initialize or run
     * @throws IllegalArgumentException if {@code shots} is not positive
     */
    public static ShotStatistics run(SimulationConfig config, CircuitFactory factory, int shots)
            throws SimulationError {
        if (shots <= 0) {
            throw new IllegalArgumentException("Shot count must be positive");
        }
        QuantumSimulator simulator = new QuantumSimulator();
        List<Map<String, Long>> values = new ArrayList<>(shots);
        for (int shot = 0; shot < shots; shot++) {
            simulator.initialize(config);
            for (QuantumEvent event : factory.build()) {
                simulator.scheduleEvent(event);
            }
            SimulationResult result = simulator.run();
            values.add(new LinkedHashMap<>(result.getRegisterValues()));
        }
        return new ShotStatistics(values);
    }

    /**
     * Aggregated results of a set of shots.
     *
     * <p>The statistics store one map per shot and a per-register histogram. They are
     * immutable after construction.</p>
     */
    public static final class ShotStatistics {
        private final List<Map<String, Long>> perShot;
        private final Map<String, Map<Long, Integer>> counts;

        private ShotStatistics(List<Map<String, Long>> perShot) {
            List<Map<String, Long>> copy = new ArrayList<>(perShot.size());
            Map<String, Map<Long, Integer>> histogram = new LinkedHashMap<>();
            for (Map<String, Long> shot : perShot) {
                copy.add(Map.copyOf(shot));
                for (Map.Entry<String, Long> entry : shot.entrySet()) {
                    histogram.computeIfAbsent(entry.getKey(), key -> new LinkedHashMap<>())
                        .merge(entry.getValue(), 1, Integer::sum);
                }
            }
            this.perShot = List.copyOf(copy);
            this.counts = histogram;
        }

        /**
         * Returns the register values of every shot.
         *
         * @return an immutable list of immutable maps
         */
        public List<Map<String, Long>> getPerShot() {
            return perShot;
        }

        /**
         * Returns the shot count.
         *
         * @return the number of recorded shots
         */
        public int getShots() {
            return perShot.size();
        }

        /**
         * Returns the histogram of one register.
         *
         * @param register register name
         * @return an immutable map from value to count, empty when the register is absent
         */
        public Map<Long, Integer> getCounts(String register) {
            Map<Long, Integer> histogram = counts.get(register);
            return histogram != null ? Map.copyOf(histogram) : Map.of();
        }

        /**
         * Returns the empirical frequency of one register value.
         *
         * @param register register name
         * @param value register value
         * @return the count divided by the shot count, zero when the value never occurred
         */
        public double frequency(String register, long value) {
            Map<Long, Integer> histogram = counts.get(register);
            if (histogram == null || perShot.isEmpty()) {
                return 0.0;
            }
            Integer count = histogram.get(value);
            return count == null ? 0.0 : (double) count / perShot.size();
        }

        /**
         * Returns a description of the histograms.
         *
         * @return a string with one line per register
         */
        @Override
        public String toString() {
            return "ShotStatistics[shots=" + perShot.size() + ", registers=" + counts + "]";
        }
    }
}
