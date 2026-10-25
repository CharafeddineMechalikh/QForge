package com.quantum.simulator.config;

import java.time.Duration;

/**
 * Time units from nanosecond to hour, with conversions between them.
 *
 * <p>Each constant stores its length in nanoseconds: 1, 1,000, 1,000,000, 1,000,000,000,
 * 60,000,000,000 and 3,600,000,000,000. Conversions use {@code long} arithmetic, truncate
 * toward zero where division is involved, and perform no overflow checks.
 *
 * <p>{@link SimulationConfig} stores a preferred unit. Event timestamps are
 * {@link java.time.Instant} values and are not converted with this unit by the simulator.
 */
public enum TimeResolution {
    /**
     * Nanosecond, 1 nanosecond.
     */
    NANOSECOND(1L),
    /**
     * Microsecond, 1,000 nanoseconds.
     */
    MICROSECOND(1_000L),
    /**
     * Millisecond, 1,000,000 nanoseconds.
     */
    MILLISECOND(1_000_000L),
    /**
     * Second, 1,000,000,000 nanoseconds.
     */
    SECOND(1_000_000_000L),
    /**
     * Minute, 60,000,000,000 nanoseconds.
     */
    MINUTE(60_000_000_000L),
    /**
     * Hour, 3,600,000,000,000 nanoseconds.
     */
    HOUR(3_600_000_000_000L);
    
    private final long nanoseconds;
    
    /**
     * Stores the length of the unit.
     *
     * @param nanoseconds length of the unit in nanoseconds
     */
    TimeResolution(long nanoseconds) {
        this.nanoseconds = nanoseconds;
    }
    
    /**
     * Returns the length of this unit in nanoseconds.
     *
     * @return length in nanoseconds
     */
    public long toNanoseconds() {
        return nanoseconds;
    }
    
    /**
     * Returns the length of this unit in seconds.
     *
     * @return length in seconds as a double, computed by dividing the nanosecond length by
     *         1,000,000,000.0
     */
    public double toSeconds() {
        return nanoseconds / 1_000_000_000.0;
    }
    
    /**
     * Converts a number of these units to a {@link Duration}.
     *
     * <p>The product {@code units * nanoseconds} is computed in {@code long} arithmetic, so
     * very large counts can overflow.
     *
     * @param units number of units; may be negative
     * @return a duration representing that many units
     */
    public Duration toDuration(long units) {
        return Duration.ofNanos(units * nanoseconds);
    }
    
    /**
     * Converts a time value from another unit to this unit.
     *
     * <p>The computation is {@code (time * fromUnit.nanoseconds) / nanoseconds} in
     * {@code long} arithmetic. Division truncates toward zero, so converting a smaller unit
     * to a larger unit loses the remainder, and large values can overflow.
     *
     * @param time time value expressed in {@code fromUnit}
     * @param fromUnit unit of {@code time}; must not be null
     * @return the time value expressed in this unit, truncated toward zero
     */
    public long convert(long time, TimeResolution fromUnit) {
        return (time * fromUnit.nanoseconds) / this.nanoseconds;
    }
    
    /**
     * Resolves a time unit from a constant name or a common abbreviation.
     *
     * <p>The text is first matched against constant names after conversion to upper case, so
     * {@code "second"} and {@code "SECOND"} both resolve. If that fails, lower case
     * abbreviations are accepted: {@code ns} or {@code nano}, {@code us} or {@code micro},
     * {@code ms} or {@code milli}, {@code s} or {@code sec}, {@code m} or {@code min}, and
     * {@code h} or {@code hr}.
     *
     * @param str unit name or abbreviation; must not be null
     * @return the matching unit
     * @throws IllegalArgumentException if the text matches neither a constant name nor an
     *         abbreviation
     * @throws NullPointerException if {@code str} is null
     */
    public static TimeResolution fromString(String str) {
        try {
            return valueOf(str.toUpperCase());
        } catch (IllegalArgumentException e) {
            // Try to match with common abbreviations
            return switch (str.toLowerCase()) {
                case "ns", "nano" -> NANOSECOND;
                case "us", "micro" -> MICROSECOND;
                case "ms", "milli" -> MILLISECOND;
                case "s", "sec" -> SECOND;
                case "m", "min" -> MINUTE;
                case "h", "hr" -> HOUR;
                default -> throw new IllegalArgumentException("Unknown time resolution: " + str);
            };
        }
    }
}