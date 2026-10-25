package com.quantum.simulator.resources;

import java.util.Locale;
/**
 * Snapshot of available and total CPU, GPU, and memory capacity.
 *
 * <p>Memory values are in megabytes. Utilization values are computed as
 * {@code (total - available) / total} and are {@code 0.0} when the total is zero.
 * </p>
 */
public class ResourceStatus {
    private final int availableCPUs;
    private final int availableGPUs;
    private final int availableMemoryMB;
    private final int totalCPUs;
    private final int totalGPUs;
    private final int totalMemoryMB;
    
    /**
     * Creates a capacity snapshot.
     *
     * @param availableCPUs the number of available CPU cores
     * @param availableGPUs the number of available GPUs
     * @param availableMemoryMB the available memory in megabytes
     * @param totalCPUs the total CPU cores
     * @param totalGPUs the total GPUs
     * @param totalMemoryMB the total memory in megabytes
     */
    public ResourceStatus(int availableCPUs, int availableGPUs, int availableMemoryMB,
                         int totalCPUs, int totalGPUs, int totalMemoryMB) {
        this.availableCPUs = availableCPUs;
        this.availableGPUs = availableGPUs;
        this.availableMemoryMB = availableMemoryMB;
        this.totalCPUs = totalCPUs;
        this.totalGPUs = totalGPUs;
        this.totalMemoryMB = totalMemoryMB;
    }
    
    /**
     * Returns the available CPU cores.
     *
     * @return the available CPU cores
     */
    public int getAvailableCPUs() {
        return availableCPUs;
    }
    
    /**
     * Returns the available GPUs.
     *
     * @return the available GPU count
     */
    public int getAvailableGPUs() {
        return availableGPUs;
    }
    
    /**
     * Returns the available memory.
     *
     * @return the available memory in megabytes
     */
    public int getAvailableMemoryMB() {
        return availableMemoryMB;
    }
    
    /**
     * Returns the total CPU cores.
     *
     * @return the total CPU cores
     */
    public int getTotalCPUs() {
        return totalCPUs;
    }
    
    /**
     * Returns the total GPUs.
     *
     * @return the total GPU count
     */
    public int getTotalGPUs() {
        return totalGPUs;
    }
    
    /**
     * Returns the total memory.
     *
     * @return the total memory in megabytes
     */
    public int getTotalMemoryMB() {
        return totalMemoryMB;
    }
    
    /**
     * Returns the CPU utilization.
     *
     * @return the used fraction, or {@code 0.0} when the total is zero
     */
    public double getCpuUtilization() {
        if (totalCPUs == 0) return 0.0;
        return (double) (totalCPUs - availableCPUs) / totalCPUs;
    }
    
    /**
     * Returns the GPU utilization.
     *
     * @return the used fraction, or {@code 0.0} when the total is zero
     */
    public double getGpuUtilization() {
        if (totalGPUs == 0) return 0.0;
        return (double) (totalGPUs - availableGPUs) / totalGPUs;
    }
    
    /**
     * Returns the memory utilization.
     *
     * @return the used fraction, or {@code 0.0} when the total is zero
     */
    public double getMemoryUtilization() {
        if (totalMemoryMB == 0) return 0.0;
        return (double) (totalMemoryMB - availableMemoryMB) / totalMemoryMB;
    }
    
    /**
     * Tests whether any CPU and memory capacity is available. GPUs are not considered
     * by this test.
     *
     * @return {@code true} when available CPUs and available memory are both greater
     *         than zero
     */
    public boolean hasAvailableResources() {
        return availableCPUs > 0 && availableMemoryMB > 0;
    }
    
    /**
     * Returns a description with used and total capacity and utilization percentages.
     *
     * @return the description
     */
    @Override
    public String toString() {
        return String.format(Locale.ROOT, 
            "ResourceStatus[CPUs: %d/%d (%.1f%%), GPUs: %d/%d (%.1f%%), Memory: %d/%d MB (%.1f%%)]",
            totalCPUs - availableCPUs, totalCPUs, getCpuUtilization() * 100,
            totalGPUs - availableGPUs, totalGPUs, getGpuUtilization() * 100,
            totalMemoryMB - availableMemoryMB, totalMemoryMB, getMemoryUtilization() * 100
        );
    }
}