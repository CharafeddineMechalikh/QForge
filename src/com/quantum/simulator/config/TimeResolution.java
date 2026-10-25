package com.quantum.simulator.config;

import java.time.Duration;

public enum TimeResolution {
    NANOSECOND(1L),
    MICROSECOND(1_000L),
    MILLISECOND(1_000_000L),
    SECOND(1_000_000_000L),
    MINUTE(60_000_000_000L),
    HOUR(3_600_000_000_000L);
    
    private final long nanoseconds;
    
    TimeResolution(long nanoseconds) {
        this.nanoseconds = nanoseconds;
    }
    
    public long toNanoseconds() {
        return nanoseconds;
    }
    
    public double toSeconds() {
        return nanoseconds / 1_000_000_000.0;
    }
    
    public Duration toDuration(long units) {
        return Duration.ofNanos(units * nanoseconds);
    }
    
    public long convert(long time, TimeResolution fromUnit) {
        return (time * fromUnit.nanoseconds) / this.nanoseconds;
    }
    
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