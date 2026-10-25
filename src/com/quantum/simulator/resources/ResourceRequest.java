package com.quantum.simulator.resources;

import java.util.Locale;
import java.time.Duration;
import java.util.Optional;

/**
 * Description of the resources a requester asks for, created through
 * {@link #newBuilder(String)}.
 *
 * <p>Requests are immutable once built. The builder defaults are one CPU core, no
 * GPUs, 1024 MB of memory, a {@code null} duration (no requested lifetime),
 * {@link Priority#NORMAL}, and an empty purpose string.</p>
 */
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
    
    /**
     * Returns the requester identifier.
     *
     * @return the requester identifier
     */
    public String getRequesterId() {
        return requesterId;
    }
    
    /**
     * Returns the requested CPU cores.
     *
     * @return the CPU cores
     */
    public int getCpuCores() {
        return cpuCores;
    }
    
    /**
     * Returns the requested GPU count.
     *
     * @return the GPU count
     */
    public int getGpuCount() {
        return gpuCount;
    }
    
    /**
     * Returns the requested memory.
     *
     * @return the memory in megabytes
     */
    public int getMemoryMB() {
        return memoryMB;
    }
    
    /**
     * Returns the requested lifetime.
     *
     * @return the duration, or {@link Optional#empty()} when none was set
     */
    public Optional<Duration> getDuration() {
        return Optional.ofNullable(duration);
    }
    
    /**
     * Returns the request priority.
     *
     * @return the priority
     */
    public Priority getPriority() {
        return priority;
    }
    
    /**
     * Returns the purpose description.
     *
     * @return the purpose, empty when none was set
     */
    public String getPurpose() {
        return purpose;
    }
    
    /**
     * Creates a builder for a requester.
     *
     * @param requesterId the requester identifier; it is not validated
     * @return a new builder with default values
     */
    public static Builder newBuilder(String requesterId) {
        return new Builder(requesterId);
    }
    
    /**
     * Builder for {@link ResourceRequest} instances. Setter methods validate their
     * arguments and return this builder.
     */
    public static class Builder {
        private final String requesterId;
        private int cpuCores = 1;
        private int gpuCount = 0;
        private int memoryMB = 1024; // 1GB default
        private Duration duration = null;
        private Priority priority = Priority.NORMAL;
        private String purpose = "";
        
        /**
         * Creates a builder with the default values.
         *
         * @param requesterId the requester identifier
         */
        public Builder(String requesterId) {
            this.requesterId = requesterId;
        }
        
        /**
         * Sets the requested CPU cores.
         *
         * @param cpuCores the number of cores, which must be positive
         * @return this builder
         * @throws IllegalArgumentException if {@code cpuCores} is zero or negative
         */
        public Builder withCpuCores(int cpuCores) {
            if (cpuCores <= 0) {
                throw new IllegalArgumentException("CPU cores must be positive");
            }
            this.cpuCores = cpuCores;
            return this;
        }
        
        /**
         * Sets the requested GPU count.
         *
         * @param gpuCount the number of GPUs, which must be zero or positive
         * @return this builder
         * @throws IllegalArgumentException if {@code gpuCount} is negative
         */
        public Builder withGpuCount(int gpuCount) {
            if (gpuCount < 0) {
                throw new IllegalArgumentException("GPU count cannot be negative");
            }
            this.gpuCount = gpuCount;
            return this;
        }
        
        /**
         * Sets the requested memory.
         *
         * @param memoryMB the memory in megabytes, which must be positive
         * @return this builder
         * @throws IllegalArgumentException if {@code memoryMB} is zero or negative
         */
        public Builder withMemoryMB(int memoryMB) {
            if (memoryMB <= 0) {
                throw new IllegalArgumentException("Memory must be positive");
            }
            this.memoryMB = memoryMB;
            return this;
        }
        
        /**
         * Sets the requested memory in gigabytes by multiplying the value by 1024 and
         * delegating to {@link #withMemoryMB(int)}.
         *
         * @param memoryGB the number of gigabytes
         * @return this builder
         * @throws IllegalArgumentException if the converted value is not positive
         */
        public Builder withMemoryGB(int memoryGB) {
            return withMemoryMB(memoryGB * 1024);
        }
        
        /**
         * Sets the requested lifetime.
         *
         * @param duration the duration, or {@code null} for no requested lifetime
         * @return this builder
         */
        public Builder withDuration(Duration duration) {
            this.duration = duration;
            return this;
        }
        
        /**
         * Sets the request priority.
         *
         * @param priority the priority, which is stored without validation
         * @return this builder
         */
        public Builder withPriority(Priority priority) {
            this.priority = priority;
            return this;
        }
        
        /**
         * Sets the purpose description.
         *
         * @param purpose the purpose text, which is stored without validation
         * @return this builder
         */
        public Builder withPurpose(String purpose) {
            this.purpose = purpose;
            return this;
        }
        
        /**
         * Builds the request.
         *
         * @return a new request with the current builder values
         */
        public ResourceRequest build() {
            return new ResourceRequest(this);
        }
    }
    
    /**
     * Request priority. {@link ResourceManager} does not use this value when deciding
     * allocations.
     */
    public enum Priority {
        /**
         * Low priority.
         */
        LOW,
        /**
         * Normal priority, the builder default.
         */
        NORMAL,
        /**
         * High priority.
         */
        HIGH,
        /**
         * Critical priority.
         */
        CRITICAL
    }
    
    /**
     * Returns a description with the requester, resources, and priority.
     *
     * @return the description
     */
    @Override
    public String toString() {
        return String.format(Locale.ROOT, 
            "ResourceRequest[requester='%s', cpu=%d, gpu=%d, memory=%dMB, priority=%s]",
            requesterId, cpuCores, gpuCount, memoryMB, priority
        );
    }
}