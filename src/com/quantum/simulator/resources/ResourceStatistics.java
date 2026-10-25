package com.quantum.simulator.resources;

import java.util.Locale;
public class ResourceStatistics {
    private final long totalAllocations;
    private final long failedAllocations;
    private final int activeAllocations;
    private final ResourceUtilization utilization;
    private final long timestamp;
    
    public ResourceStatistics(long totalAllocations, long failedAllocations,
                             int activeAllocations, ResourceUtilization utilization,
                             long timestamp) {
        this.totalAllocations = totalAllocations;
        this.failedAllocations = failedAllocations;
        this.activeAllocations = activeAllocations;
        this.utilization = utilization;
        this.timestamp = timestamp;
    }
    
    public long getTotalAllocations() { return totalAllocations; }
    public long getFailedAllocations() { return failedAllocations; }
    public int getActiveAllocations() { return activeAllocations; }
    public ResourceUtilization getUtilization() { return utilization; }
    public long getTimestamp() { return timestamp; }
    
    public double getSuccessRate() {
        long attempts = totalAllocations + failedAllocations;
        if (attempts == 0) return 1.0;
        return (double) totalAllocations / attempts;
    }
    
    public double getAllocationsPerMinute(long currentTime) {
        long minutes = (currentTime - timestamp) / (1000 * 60);
        if (minutes == 0) return 0.0;
        return (double) totalAllocations / minutes;
    }
    
    @Override
    public String toString() {
        return String.format(Locale.ROOT, 
            "ResourceStatistics[total=%d, failed=%d, active=%d, success=%.2f%%, utilization=%s]",
            totalAllocations, failedAllocations, activeAllocations,
            getSuccessRate() * 100, utilization
        );
    }
}