package com.mechalikh.qforge.resources;

import java.util.Locale;
import java.time.Duration;
import java.time.Instant;

/**
 * Record of a resource allocation made by {@link ResourceManager}.
 *
 * <p>Times are epoch milliseconds as returned by {@link System#currentTimeMillis()}.
 * The duration may be {@code null}, which means the allocation does not expire. An
 * allocation is marked released when the manager returns its resources, and the
 * manager removes it from its active set at the same moment.</p>
 */
public class ResourceAllocation {
    private final String allocationId;
    private final String requesterId;
    private final int cpuCores;
    private final int gpuCount;
    private final int memoryMB;
    private final Duration duration;
    private final long allocationTime;
    private Long releaseTime;
    private boolean released;
    
    /**
     * Creates an allocation record. The allocation starts in the active state with no
     * release time.
     *
     * @param allocationId the allocation identifier
     * @param requesterId the identifier of the requester
     * @param cpuCores the number of CPU cores held
     * @param gpuCount the number of GPUs held
     * @param memoryMB the memory held, in megabytes
     * @param duration the requested lifetime, or {@code null} for no expiration
     * @param allocationTime the allocation time in epoch milliseconds
     */
    public ResourceAllocation(String allocationId, String requesterId, int cpuCores,
                            int gpuCount, int memoryMB, Duration duration, long allocationTime) {
        this.allocationId = allocationId;
        this.requesterId = requesterId;
        this.cpuCores = cpuCores;
        this.gpuCount = gpuCount;
        this.memoryMB = memoryMB;
        this.duration = duration;
        this.allocationTime = allocationTime;
        this.released = false;
        this.releaseTime = null;
    }
    
    /**
     * Returns the allocation identifier.
     *
     * @return the allocation identifier
     */
    public String getAllocationId() {
        return allocationId;
    }
    
    /**
     * Returns the requester identifier.
     *
     * @return the requester identifier
     */
    public String getRequesterId() {
        return requesterId;
    }
    
    /**
     * Returns the number of CPU cores held.
     *
     * @return the CPU cores
     */
    public int getCpuCores() {
        return cpuCores;
    }
    
    /**
     * Returns the number of GPUs held.
     *
     * @return the GPU count
     */
    public int getGpuCount() {
        return gpuCount;
    }
    
    /**
     * Returns the memory held.
     *
     * @return the memory in megabytes
     */
    public int getMemoryMB() {
        return memoryMB;
    }
    
    /**
     * Returns the requested lifetime.
     *
     * @return the duration, or {@code null} when the allocation does not expire
     */
    public Duration getDuration() {
        return duration;
    }
    
    /**
     * Returns the allocation time.
     *
     * @return the allocation time in epoch milliseconds
     */
    public long getAllocationTime() {
        return allocationTime;
    }
    
    /**
     * Returns the release time.
     *
     * @return the release time in epoch milliseconds, or {@code null} while the
     *         allocation has not been released
     */
    public Long getReleaseTime() {
        return releaseTime;
    }
    
    /**
     * Tests whether the allocation has been released.
     *
     * @return {@code true} when the allocation has been released
     */
    public boolean isReleased() {
        return released;
    }
    
    /**
     * Tests whether the allocation has expired at the given time.
     *
     * <p>Expiration is the allocation time plus the duration in milliseconds. An
     * allocation without a duration never expires.</p>
     *
     * @param currentTime the time to test, in epoch milliseconds
     * @return {@code false} when the duration is {@code null}, otherwise {@code true}
     *         when {@code currentTime} is at or past the expiration time
     */
    public boolean isExpired(long currentTime) {
        if (duration == null) {
            return false; // No expiration
        }
        
        long expirationTime = allocationTime + duration.toMillis();
        return currentTime >= expirationTime;
    }
    
    /**
     * Returns the time elapsed since the allocation.
     *
     * @param currentTime the current time in epoch milliseconds
     * @return the elapsed time, which is negative when {@code currentTime} precedes
     *         the allocation time
     */
    public Duration getAge(long currentTime) {
        return Duration.ofMillis(currentTime - allocationTime);
    }
    
    /**
     * Returns how long the allocation was or has been held.
     *
     * @param currentTime the current time in epoch milliseconds
     * @return the release time minus the allocation time when the allocation is
     *         released and has a release time, otherwise the result of
     *         {@link #getAge(long)}
     */
    public Duration getHoldTime(long currentTime) {
        if (released && releaseTime != null) {
            return Duration.ofMillis(releaseTime - allocationTime);
        }
        return getAge(currentTime);
    }
    
    /**
     * Marks the allocation as released and stores the release time. Called by
     * {@link ResourceManager#release(ResourceAllocation)} while that method holds the
     * manager lock.
     *
     * @param releaseTime the release time in epoch milliseconds
     */
    void markReleased(long releaseTime) {
        this.released = true;
        this.releaseTime = releaseTime;
    }
    
    /**
     * Returns a description with the identifier, requester, resources, status, and age.
     * The age is computed from the system clock at call time.
     *
     * @return the description
     */
    @Override
    public String toString() {
        String status = released ? "released" : "active";
        String age = Duration.ofMillis(System.currentTimeMillis() - allocationTime).toString();
        
        return String.format(Locale.ROOT, 
            "ResourceAllocation[id='%s', requester='%s', cpu=%d, gpu=%d, memory=%dMB, status=%s, age=%s]",
            allocationId, requesterId, cpuCores, gpuCount, memoryMB, status, age
        );
    }
}