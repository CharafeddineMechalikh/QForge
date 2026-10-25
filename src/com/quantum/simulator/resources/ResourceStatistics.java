package com.quantum.simulator.resources;

import java.util.Locale;
/**
 * Allocation counters and utilization captured by
 * {@link ResourceManager#getStatistics()}.
 */
public class ResourceStatistics {
    private final long totalAllocations;
    private final long failedAllocations;
    private final int activeAllocations;
    private final ResourceUtilization utilization;
    private final long timestamp;
    
    /**
     * Creates a statistics snapshot.
     *
     * @param totalAllocations the number of successful allocations
     * @param failedAllocations the number of requests rejected for insufficient
     *                          resources
     * @param activeAllocations the number of currently active allocations
     * @param utilization the utilization at capture time
     * @param timestamp the capture time in epoch milliseconds
     */
    public ResourceStatistics(long totalAllocations, long failedAllocations,
                             int activeAllocations, ResourceUtilization utilization,
                             long timestamp) {
        this.totalAllocations = totalAllocations;
        this.failedAllocations = failedAllocations;
        this.activeAllocations = activeAllocations;
        this.utilization = utilization;
        this.timestamp = timestamp;
    }
    
    /**
     * Returns the successful allocation count.
     *
     * @return the successful allocation count
     */
    public long getTotalAllocations() { return totalAllocations; }
    /**
     * Returns the failed allocation count.
     *
     * @return the failed allocation count
     */
    public long getFailedAllocations() { return failedAllocations; }
    /**
     * Returns the active allocation count.
     *
     * @return the active allocation count
     */
    public int getActiveAllocations() { return activeAllocations; }
    /**
     * Returns the utilization at capture time.
     *
     * @return the utilization
     */
    public ResourceUtilization getUtilization() { return utilization; }
    /**
     * Returns the capture time.
     *
     * @return the capture time in epoch milliseconds
     */
    public long getTimestamp() { return timestamp; }
    
    /**
     * Returns the share of allocation attempts that succeeded.
     *
     * <p>The value is {@code totalAllocations / (totalAllocations + failedAllocations)}
     * as a ratio between {@code 0.0} and {@code 1.0}; when no attempts were recorded
     * the method returns {@code 1.0}.</p>
     *
     * @return the success rate ratio, not a percentage
     */
    public double getSuccessRate() {
        long attempts = totalAllocations + failedAllocations;
        if (attempts == 0) return 1.0;
        return (double) totalAllocations / attempts;
    }
    
    /**
     * Returns successful allocations per elapsed minute since the capture time.
     *
     * <p>The elapsed time is divided by 60000 using integer division, so the rate is
     * {@code 0.0} when less than one minute has elapsed. The rate is relative to the
     * capture time, not to the start of the manager.</p>
     *
     * @param currentTime the current time in epoch milliseconds
     * @return successful allocations per minute
     */
    public double getAllocationsPerMinute(long currentTime) {
        long minutes = (currentTime - timestamp) / (1000 * 60);
        if (minutes == 0) return 0.0;
        return (double) totalAllocations / minutes;
    }
    
    /**
     * Returns a description with the counts, the success rate as a percentage, and the
     * utilization.
     *
     * @return the description
     */
    @Override
    public String toString() {
        return String.format(Locale.ROOT, 
            "ResourceStatistics[total=%d, failed=%d, active=%d, success=%.2f%%, utilization=%s]",
            totalAllocations, failedAllocations, activeAllocations,
            getSuccessRate() * 100, utilization
        );
    }
}