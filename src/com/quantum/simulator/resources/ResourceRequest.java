package com.quantum.simulator.resources;

import java.util.Locale;
import java.time.Duration;
import java.util.Optional;

public class ResourceRequest {
    private final String requesterId;
    private final int cpuCores;
    private final int gpuCount;
    private final int memoryMB;
    private final Duration duration;
    private final Priority priority;
    private final String purpose;
    
    private ResourceRequest(Builder builder) {
        this.requesterId = builder.requesterId;
        this.cpuCores = builder.cpuCores;
        this.gpuCount = builder.gpuCount;
        this.memoryMB = builder.memoryMB;
        this.duration = builder.duration;
        this.priority = builder.priority;
        this.purpose = builder.purpose;
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
    
    public Optional<Duration> getDuration() {
        return Optional.ofNullable(duration);
    }
    
    public Priority getPriority() {
        return priority;
    }
    
    public String getPurpose() {
        return purpose;
    }
    
    public static Builder newBuilder(String requesterId) {
        return new Builder(requesterId);
    }
    
    public static class Builder {
        private final String requesterId;
        private int cpuCores = 1;
        private int gpuCount = 0;
        private int memoryMB = 1024; // 1GB default
        private Duration duration = null;
        private Priority priority = Priority.NORMAL;
        private String purpose = "";
        
        public Builder(String requesterId) {
            this.requesterId = requesterId;
        }
        
        public Builder withCpuCores(int cpuCores) {
            if (cpuCores <= 0) {
                throw new IllegalArgumentException("CPU cores must be positive");
            }
            this.cpuCores = cpuCores;
            return this;
        }
        
        public Builder withGpuCount(int gpuCount) {
            if (gpuCount < 0) {
                throw new IllegalArgumentException("GPU count cannot be negative");
            }
            this.gpuCount = gpuCount;
            return this;
        }
        
        public Builder withMemoryMB(int memoryMB) {
            if (memoryMB <= 0) {
                throw new IllegalArgumentException("Memory must be positive");
            }
            this.memoryMB = memoryMB;
            return this;
        }
        
        public Builder withMemoryGB(int memoryGB) {
            return withMemoryMB(memoryGB * 1024);
        }
        
        public Builder withDuration(Duration duration) {
            this.duration = duration;
            return this;
        }
        
        public Builder withPriority(Priority priority) {
            this.priority = priority;
            return this;
        }
        
        public Builder withPurpose(String purpose) {
            this.purpose = purpose;
            return this;
        }
        
        public ResourceRequest build() {
            return new ResourceRequest(this);
        }
    }
    
    public enum Priority {
        LOW,
        NORMAL,
        HIGH,
        CRITICAL
    }
    
    @Override
    public String toString() {
        return String.format(Locale.ROOT, 
            "ResourceRequest[requester='%s', cpu=%d, gpu=%d, memory=%dMB, priority=%s]",
            requesterId, cpuCores, gpuCount, memoryMB, priority
        );
    }
}