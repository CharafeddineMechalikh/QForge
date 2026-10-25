package com.quantum.simulator.events;

public enum EventPriority {
    HIGHEST(0),
    HIGH(1),
    NORMAL(2),
    LOW(3),
    LOWEST(4);
    
    private final int value;
    
    EventPriority(int value) {
        this.value = value;
    }
    
    public int getValue() {
        return value;
    }
    
    public boolean isHigherThan(EventPriority other) {
        return this.value < other.value;
    }
    
    public boolean isLowerThan(EventPriority other) {
        return this.value > other.value;
    }
    
    public static EventPriority fromValue(int value) {
        for (EventPriority priority : values()) {
            if (priority.value == value) {
                return priority;
            }
        }
        throw new IllegalArgumentException("No EventPriority with value: " + value);
    }
    
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