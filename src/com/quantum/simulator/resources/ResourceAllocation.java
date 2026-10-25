package com.quantum.simulator.resources;

import java.util.Locale;
import java.time.Duration;
import java.time.Instant;

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
    
    public String getAllocationId() {
        return allocationId;
    }
    
    public String getRequesterId() {
        return requesterId;
    }
    
    public int getCpuCores() {
        return cpuCores;
    }
    
    public int getGpuCount() {
        return gpuCount;
    }
    
    public int getMemoryMB() {
        return memoryMB;
    }
    
    public Duration getDuration() {
        return duration;
    }
    
    public long getAllocationTime() {
        return allocationTime;
    }
    
    public Long getReleaseTime() {
        return releaseTime;
    }
    
    public boolean isReleased() {
        return released;
    }
    
    public boolean isExpired(long currentTime) {
        if (duration == null) {
            return false; // No expiration
        }
        
        long expirationTime = allocationTime + duration.toMillis();
        return currentTime >= expirationTime;
    }
    
    public Duration getAge(long currentTime) {
        return Duration.ofMillis(currentTime - allocationTime);
    }
    
    public Duration getHoldTime(long currentTime) {
        if (released && releaseTime != null) {
            return Duration.ofMillis(releaseTime - allocationTime);
        }
        return getAge(currentTime);
    }
    
    void markReleased(long releaseTime) {
        this.released = true;
        this.releaseTime = releaseTime;
    }
    
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