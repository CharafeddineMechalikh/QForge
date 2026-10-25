package com.quantum.simulator.config;

import java.util.Locale;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.OperatingSystemMXBean;

/**
 * Limits for memory, CPU, GPU and disk use of a simulation.
 *
 * <p>{@link #validate()} compares the memory and CPU limits with the current JVM limits
 * unless the corresponding overcommit flag is set. GPU and disk limits are only checked for
 * sign. The disk limit is stored, validated and serialized, but it is not checked anywhere
 * else in the library.
 *
 * <p>The no argument constructor derives defaults from the running JVM: 80 percent of the
 * maximum heap reported by {@link java.lang.management.MemoryMXBean}, at least one CPU based
 * on 80 percent of the available processors truncated to an integer, no GPUs, and 1 GiB of
 * disk space. Both overcommit flags default to {@code false}. Note that
 * {@link #validate()} compares the memory limit with {@link Runtime#maxMemory()}, which can
 * differ from the value used by the default constructor.
 */
public class ResourceConstraints {
    private long maxMemoryBytes;
    private int maxCPUs;
    private int maxGPUs;
    private boolean allowMemoryOvercommit;
    private boolean allowCPUOvercommit;
    private long maxDiskSpaceBytes;
    
    /**
     * Creates constraints with defaults derived from the current JVM.
     *
     * <p>The memory limit is 80 percent of the maximum heap reported by the memory MX bean,
     * truncated to a long. The CPU limit is {@code max(1, 80 percent of the available
     * processors)}. The GPU limit is 0, the disk limit is 1 GiB, and both overcommit flags
     * are {@code false}.
     */
    public ResourceConstraints() {
        MemoryMXBean memoryBean = ManagementFactory.getMemoryMXBean();
        OperatingSystemMXBean osBean = ManagementFactory.getOperatingSystemMXBean();
        
        // Default to 80% of available resources
        this.maxMemoryBytes = (long) (memoryBean.getHeapMemoryUsage().getMax() * 0.8);
        this.maxCPUs = Math.max(1, (int) (osBean.getAvailableProcessors() * 0.8));
        this.maxGPUs = 0; // Default no GPU
        this.allowMemoryOvercommit = false;
        this.allowCPUOvercommit = false;
        this.maxDiskSpaceBytes = 1024L * 1024L * 1024L; // 1GB default
    }
    
    /**
     * Creates constraints with explicit memory, CPU and GPU limits.
     *
     * <p>The values are stored without validation; call {@link #validate()} before use.
     * Both overcommit flags are {@code false} and the disk limit is 1 GiB.
     *
     * @param maxMemoryBytes memory limit in bytes
     * @param maxCPUs CPU limit
     * @param maxGPUs GPU limit
     */
    public ResourceConstraints(long maxMemoryBytes, int maxCPUs, int maxGPUs) {
        this.maxMemoryBytes = maxMemoryBytes;
        this.maxCPUs = maxCPUs;
        this.maxGPUs = maxGPUs;
        this.allowMemoryOvercommit = false;
        this.allowCPUOvercommit = false;
        this.maxDiskSpaceBytes = 1024L * 1024L * 1024L;
    }
    
    /**
     * Checks that the limits are internally consistent and within the current JVM limits.
     *
     * <p>The method returns false when the memory or CPU limit is not positive, when the GPU
     * limit is negative, or when the disk limit is negative. Unless memory overcommit is
     * allowed, the memory limit must not exceed {@link Runtime#maxMemory()}. Unless CPU
     * overcommit is allowed, the CPU limit must not exceed
     * {@link Runtime#availableProcessors()}.
     *
     * @return true if the constraints are acceptable
     */
    public boolean validate() {
        if (maxMemoryBytes <= 0) {
            return false;
        }
        
        if (maxCPUs <= 0) {
            return false;
        }
        
        if (maxGPUs < 0) {
            return false;
        }
        
        if (maxDiskSpaceBytes < 0) {
            return false;
        }
        
        // Check against system limits if not overcommitting
        if (!allowMemoryOvercommit) {
            long maxSystemMemory = Runtime.getRuntime().maxMemory();
            if (maxMemoryBytes > maxSystemMemory) {
                return false;
            }
        }
        
        if (!allowCPUOvercommit) {
            int availableCPUs = Runtime.getRuntime().availableProcessors();
            if (maxCPUs > availableCPUs) {
                return false;
            }
        }
        
        return true;
    }
    
    /**
     * Reports whether the requested memory fits under the memory limit.
     *
     * <p>Used memory is estimated as {@code totalMemory - freeMemory} of the JVM, and the
     * remaining amount is the memory limit minus that estimate. When memory overcommit is
     * allowed, the method returns true without inspecting the requested amount.
     *
     * @param requiredBytes memory needed for an allocation
     * @return true if overcommit is allowed or the remaining memory is at least
     *         {@code requiredBytes}
     */
    public boolean checkMemoryAvailable(long requiredBytes) {
        long usedMemory = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory();
        long availableMemory = maxMemoryBytes - usedMemory;
        
        if (allowMemoryOvercommit) {
            return true;
        }
        
        return availableMemory >= requiredBytes;
    }
    
    /**
     * Reports whether the requested CPU count fits under the CPU limit.
     *
     * <p>When CPU overcommit is allowed, the method returns true without inspecting the
     * requested count.
     *
     * @param requiredCPUs number of CPUs needed
     * @return true if overcommit is allowed or the CPU limit is at least
     *         {@code requiredCPUs}
     */
    public boolean checkCPUAvailable(int requiredCPUs) {
        if (allowCPUOvercommit) {
            return true;
        }
        
        return maxCPUs >= requiredCPUs;
    }
    
    /**
     * Returns the memory currently available under the memory limit.
     *
     * <p>The result is {@code max(0, limit - used)} where used memory is estimated as
     * {@code totalMemory - freeMemory} of the JVM. It is not clamped to the actual free
     * heap.
     *
     * @return non negative number of available bytes according to the limit
     */
    public long getAvailableMemoryBytes() {
        long usedMemory = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory();
        return Math.max(0, maxMemoryBytes - usedMemory);
    }
    
    /**
     * Returns the fraction of the memory limit currently used.
     *
     * <p>Used memory is estimated as {@code totalMemory - freeMemory} of the JVM and divided
     * by the memory limit. The result is not clamped, so it can exceed 1.0 when the estimate
     * is larger than the limit, and a limit of 0 causes a division by zero.
     *
     * @return used memory divided by the memory limit
     */
    public double getMemoryUtilization() {
        long usedMemory = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory();
        return (double) usedMemory / maxMemoryBytes;
    }
    
    // Getters and Setters
    /**
     * Returns the memory limit in bytes.
     *
     * @return the memory limit
     */
    public long getMaxMemoryBytes() {
        return maxMemoryBytes;
    }
    
    /**
     * Sets the memory limit. The value is not validated here.
     *
     * @param maxMemoryBytes memory limit in bytes
     */
    public void setMaxMemoryBytes(long maxMemoryBytes) {
        this.maxMemoryBytes = maxMemoryBytes;
    }
    
    /**
     * Returns the CPU limit.
     *
     * @return the maximum number of CPUs
     */
    public int getMaxCPUs() {
        return maxCPUs;
    }
    
    /**
     * Sets the CPU limit. The value is not validated here.
     *
     * @param maxCPUs maximum number of CPUs
     */
    public void setMaxCPUs(int maxCPUs) {
        this.maxCPUs = maxCPUs;
    }
    
    /**
     * Returns the GPU limit.
     *
     * @return the maximum number of GPUs
     */
    public int getMaxGPUs() {
        return maxGPUs;
    }
    
    /**
     * Sets the GPU limit. The value is not validated here.
     *
     * @param maxGPUs maximum number of GPUs
     */
    public void setMaxGPUs(int maxGPUs) {
        this.maxGPUs = maxGPUs;
    }
    
    /**
     * Reports whether memory allocations may exceed the memory limit.
     *
     * @return true when memory overcommit is allowed
     */
    public boolean isAllowMemoryOvercommit() {
        return allowMemoryOvercommit;
    }
    
    /**
     * Sets whether memory allocations may exceed the memory limit.
     *
     * <p>When set to true, {@link #validate()} skips the comparison with
     * {@link Runtime#maxMemory()} and {@link #checkMemoryAvailable(long)} always returns
     * true.
     *
     * @param allowMemoryOvercommit true to allow memory overcommit
     */
    public void setAllowMemoryOvercommit(boolean allowMemoryOvercommit) {
        this.allowMemoryOvercommit = allowMemoryOvercommit;
    }
    
    /**
     * Reports whether CPU allocations may exceed the CPU limit.
     *
     * @return true when CPU overcommit is allowed
     */
    public boolean isAllowCPUOvercommit() {
        return allowCPUOvercommit;
    }
    
    /**
     * Sets whether CPU allocations may exceed the CPU limit.
     *
     * <p>When set to true, {@link #validate()} skips the comparison with
     * {@link Runtime#availableProcessors()} and {@link #checkCPUAvailable(int)} always
     * returns true.
     *
     * @param allowCPUOvercommit true to allow CPU overcommit
     */
    public void setAllowCPUOvercommit(boolean allowCPUOvercommit) {
        this.allowCPUOvercommit = allowCPUOvercommit;
    }
    
    /**
     * Returns the disk space limit.
     *
     * @return the disk limit in bytes
     */
    public long getMaxDiskSpaceBytes() {
        return maxDiskSpaceBytes;
    }
    
    /**
     * Sets the disk space limit. The value is not validated here and is not enforced
     * elsewhere in the library.
     *
     * @param maxDiskSpaceBytes disk limit in bytes
     */
    public void setMaxDiskSpaceBytes(long maxDiskSpaceBytes) {
        this.maxDiskSpaceBytes = maxDiskSpaceBytes;
    }
    
    /**
     * Returns a summary with the memory limit in GiB, the CPU and GPU limits, and the
     * current memory utilization percentage.
     *
     * @return summary text
     */
    @Override
    public String toString() {
        return String.format(Locale.ROOT, 
            "ResourceConstraints{memory=%.2f GB, CPUs=%d, GPUs=%d, memoryUtil=%.1f%%}",
            maxMemoryBytes / (1024.0 * 1024.0 * 1024.0),
            maxCPUs,
            maxGPUs,
            getMemoryUtilization() * 100
        );
    }
}