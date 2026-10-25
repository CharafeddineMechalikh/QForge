package com.quantum.simulator.resources;

import java.util.Locale;
public class ResourceStatus {
    private final int availableCPUs;
    private final int availableGPUs;
    private final int availableMemoryMB;
    private final int totalCPUs;
    private final int totalGPUs;
    private final int totalMemoryMB;
    
    public ResourceStatus(int availableCPUs, int availableGPUs, int availableMemoryMB,
                         int totalCPUs, int totalGPUs, int totalMemoryMB) {
        this.availableCPUs = availableCPUs;
        this.availableGPUs = availableGPUs;
        this.availableMemoryMB = availableMemoryMB;
        this.totalCPUs = totalCPUs;
        this.totalGPUs = totalGPUs;
        this.totalMemoryMB = totalMemoryMB;
    }
    
    public int getAvailableCPUs() {
        return availableCPUs;
    }
    
    public int getAvailableGPUs() {
        return availableGPUs;
    }
    
    public int getAvailableMemoryMB() {
        return availableMemoryMB;
    }
    
    public int getTotalCPUs() {
        return totalCPUs;
    }
    
    public int getTotalGPUs() {
        return totalGPUs;
    }
    
    public int getTotalMemoryMB() {
        return totalMemoryMB;
    }
    
    public double getCpuUtilization() {
        if (totalCPUs == 0) return 0.0;
        return (double) (totalCPUs - availableCPUs) / totalCPUs;
    }
    
    public double getGpuUtilization() {
        if (totalGPUs == 0) return 0.0;
        return (double) (totalGPUs - availableGPUs) / totalGPUs;
    }
    
    public double getMemoryUtilization() {
        if (totalMemoryMB == 0) return 0.0;
        return (double) (totalMemoryMB - availableMemoryMB) / totalMemoryMB;
    }
    
    public boolean hasAvailableResources() {
        return availableCPUs > 0 && availableMemoryMB > 0;
    }
    
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