package com.mechalikh.qforge.resources;

import java.util.Locale;
/**
 * Used and total capacity for CPUs, GPUs, and memory.
 *
 * <p>Memory values are in megabytes. Usage values are computed as
 * {@code used / total} and are {@code 0.0} when the total is zero.</p>
 */
public class ResourceUtilization {
    private final int usedCPUs;
    private final int totalCPUs;
    private final int usedGPUs;
    private final int totalGPUs;
    private final int usedMemoryMB;
    private final int totalMemoryMB;
    
    /**
     * Creates a utilization record.
     *
     * @param usedCPUs the used CPU cores
     * @param totalCPUs the total CPU cores
     * @param usedGPUs the used GPUs
     * @param totalGPUs the total GPUs
     * @param usedMemoryMB the used memory in megabytes
     * @param totalMemoryMB the total memory in megabytes
     */
    public ResourceUtilization(int usedCPUs, int totalCPUs,
                              int usedGPUs, int totalGPUs,
                              int usedMemoryMB, int totalMemoryMB) {
        this.usedCPUs = usedCPUs;
        this.totalCPUs = totalCPUs;
        this.usedGPUs = usedGPUs;
        this.totalGPUs = totalGPUs;
        this.usedMemoryMB = usedMemoryMB;
        this.totalMemoryMB = totalMemoryMB;
    }
    
    /**
     * Returns the used CPU cores.
     *
     * @return the used CPU cores
     */
    public int getUsedCPUs() { return usedCPUs; }
    /**
     * Returns the total CPU cores.
     *
     * @return the total CPU cores
     */
    public int getTotalCPUs() { return totalCPUs; }
    /**
     * Returns the used GPUs.
     *
     * @return the used GPU count
     */
    public int getUsedGPUs() { return usedGPUs; }
    /**
     * Returns the total GPUs.
     *
     * @return the total GPU count
     */
    public int getTotalGPUs() { return totalGPUs; }
    /**
     * Returns the used memory.
     *
     * @return the used memory in megabytes
     */
    public int getUsedMemoryMB() { return usedMemoryMB; }
    /**
     * Returns the total memory.
     *
     * @return the total memory in megabytes
     */
    public int getTotalMemoryMB() { return totalMemoryMB; }
    
    /**
     * Returns the CPU usage.
     *
     * @return the used CPUs divided by the total CPUs, or {@code 0.0} when the total is
     *         zero
     */
    public double getCpuUsage() {
        return totalCPUs > 0 ? (double) usedCPUs / totalCPUs : 0.0;
    }
    
    /**
     * Returns the GPU usage.
     *
     * @return the used GPUs divided by the total GPUs, or {@code 0.0} when the total is
     *         zero
     */
    public double getGpuUsage() {
        return totalGPUs > 0 ? (double) usedGPUs / totalGPUs : 0.0;
    }
    
    /**
     * Returns the memory usage.
     *
     * @return the used memory divided by the total memory, or {@code 0.0} when the
     *         total is zero
     */
    public double getMemoryUsage() {
        return totalMemoryMB > 0 ? (double) usedMemoryMB / totalMemoryMB : 0.0;
    }
    
    /**
     * Returns a description with the CPU, GPU, and memory usage percentages.
     *
     * @return the description
     */
    @Override
    public String toString() {
        return String.format(Locale.ROOT, 
            "ResourceUtilization[CPU: %.1f%%, GPU: %.1f%%, Memory: %.1f%%]",
            getCpuUsage() * 100, getGpuUsage() * 100, getMemoryUsage() * 100
        );
    }
}