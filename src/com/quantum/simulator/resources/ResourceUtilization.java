package com.quantum.simulator.resources;

import java.util.Locale;
public class ResourceUtilization {
    private final int usedCPUs;
    private final int totalCPUs;
    private final int usedGPUs;
    private final int totalGPUs;
    private final int usedMemoryMB;
    private final int totalMemoryMB;
    
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
    
    public int getUsedCPUs() { return usedCPUs; }
    public int getTotalCPUs() { return totalCPUs; }
    public int getUsedGPUs() { return usedGPUs; }
    public int getTotalGPUs() { return totalGPUs; }
    public int getUsedMemoryMB() { return usedMemoryMB; }
    public int getTotalMemoryMB() { return totalMemoryMB; }
    
    public double getCpuUsage() {
        return totalCPUs > 0 ? (double) usedCPUs / totalCPUs : 0.0;
    }
    
    public double getGpuUsage() {
        return totalGPUs > 0 ? (double) usedGPUs / totalGPUs : 0.0;
    }
    
    public double getMemoryUsage() {
        return totalMemoryMB > 0 ? (double) usedMemoryMB / totalMemoryMB : 0.0;
    }
    
    @Override
    public String toString() {
        return String.format(Locale.ROOT, 
            "ResourceUtilization[CPU: %.1f%%, GPU: %.1f%%, Memory: %.1f%%]",
            getCpuUsage() * 100, getGpuUsage() * 100, getMemoryUsage() * 100
        );
    }
}