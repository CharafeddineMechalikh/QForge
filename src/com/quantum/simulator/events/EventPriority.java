package com.quantum.simulator.events;

/**
 * Priority levels for {@link QuantumEvent} instances.
 *
 * <p>Each constant carries a numeric value, and a smaller value denotes a higher priority.
 * When two events have the same scheduled time, {@link QuantumEvent#compareTo(QuantumEvent)}
 * orders them by descending priority, so {@link #HIGHEST} is dequeued before {@link #LOWEST}.
 */
public enum EventPriority {
    /** Highest priority, numeric value {@code 0}. */
    HIGHEST(0),
    /** High priority, numeric value {@code 1}. */
    HIGH(1),
    /** Normal priority, numeric value {@code 2}, the default used by event builders. */
    NORMAL(2),
    /** Low priority, numeric value {@code 3}. */
    LOW(3),
    /** Lowest priority, numeric value {@code 4}. */
    LOWEST(4);
    
    private final int value;
    
    EventPriority(int value) {
        this.value = value;
    }
    
    /**
     * Returns the numeric value of this priority.
     *
     * @return the numeric value, where a smaller value means a higher priority
     */
    public int getValue() {
        return value;
    }
    
    /**
     * Tests whether this priority is higher than another priority.
     *
     * @param other priority to compare with
     * @return {@code true} if this priority has a smaller numeric value than {@code other}
     */
    public boolean isHigherThan(EventPriority other) {
        return this.value < other.value;
    }
    
    /**
     * Tests whether this priority is lower than another priority.
     *
     * @param other priority to compare with
     * @return {@code true} if this priority has a larger numeric value than {@code other}
     */
    public boolean isLowerThan(EventPriority other) {
        return this.value > other.value;
    }
    
    /**
     * Returns the priority constant with the given numeric value.
     *
     * @param value numeric priority value
     * @return the matching priority constant
     * @throws IllegalArgumentException if no constant has the given value
     */
    public static EventPriority fromValue(int value) {
        for (EventPriority priority : values()) {
            if (priority.value == value) {
                return priority;
            }
        }
        throw new IllegalArgumentException("No EventPriority with value: " + value);
    }
    
    /**
     * Parses a priority from its constant name or a common alias.
     *
     * <p>The method first tries {@code valueOf} on the upper-cased input. If that fails, it
     * accepts the aliases {@code urgent} and {@code critical} for {@link #HIGHEST},
     * {@code important} for {@link #HIGH}, {@code medium} and {@code default} for
     * {@link #NORMAL}, {@code background} for {@link #LOW}, and {@code idle} for
     * {@link #LOWEST}.
     *
     * @param str priority name or alias
     * @return the matching priority constant
     * @throws IllegalArgumentException if the input matches no constant or alias
     */
    public static EventPriority fromString(String str) {
        try {
            return valueOf(str.toUpperCase());
        } catch (IllegalArgumentException e) {
            // Try to parse from common names
            return switch (str.toLowerCase()) {
                case "urgent", "critical" -> HIGHEST;
                case "important" -> HIGH;
                case "medium", "default" -> NORMAL;
                case "background" -> LOW;
                case "idle" -> LOWEST;
                default -> throw new IllegalArgumentException("Unknown priority: " + str);
            };
        }
    }
}