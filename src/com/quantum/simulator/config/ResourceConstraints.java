package com.quantum.simulator.config;

import java.util.Locale;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.OperatingSystemMXBean;

public class ResourceConstraints {
    private long maxMemoryBytes;
    private int maxCPUs;
    private int maxGPUs;
    private boolean allowMemoryOvercommit;
    private boolean allowCPUOvercommit;
    private long maxDiskSpaceBytes;
    
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
    
    public ResourceConstraints(long maxMemoryBytes, int maxCPUs, int maxGPUs) {
        this.maxMemoryBytes = maxMemoryBytes;
        this.maxCPUs = maxCPUs;
        this.maxGPUs = maxGPUs;
        this.allowMemoryOvercommit = false;
        this.allowCPUOvercommit = false;
        this.maxDiskSpaceBytes = 1024L * 1024L * 1024L;
    }
    
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
    
    public boolean checkMemoryAvailable(long requiredBytes) {
        long usedMemory = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory();
        long availableMemory = maxMemoryBytes - usedMemory;
        
        if (allowMemoryOvercommit) {
            return true;
        }
        
        return availableMemory >= requiredBytes;
    }
    
    public boolean checkCPUAvailable(int requiredCPUs) {
        if (allowCPUOvercommit) {
            return true;
        }
        
        return maxCPUs >= requiredCPUs;
    }
    
    public long getAvailableMemoryBytes() {
        long usedMemory = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory();
        return Math.max(0, maxMemoryBytes - usedMemory);
    }
    
    public double getMemoryUtilization() {
        long usedMemory = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory();
        return (double) usedMemory / maxMemoryBytes;
    }
    
    // Getters and Setters
    public long getMaxMemoryBytes() {
        return maxMemoryBytes;
    }
    
    public void setMaxMemoryBytes(long maxMemoryBytes) {
        this.maxMemoryBytes = maxMemoryBytes;
    }
    
    public int getMaxCPUs() {
        return maxCPUs;
    }
    
    public void setMaxCPUs(int maxCPUs) {
        this.maxCPUs = maxCPUs;
    }
    
    public int getMaxGPUs() {
        return maxGPUs;
    }
    
    public void setMaxGPUs(int maxGPUs) {
        this.maxGPUs = maxGPUs;
    }
    
    public boolean isAllowMemoryOvercommit() {
        return allowMemoryOvercommit;
    }
    
    public void setAllowMemoryOvercommit(boolean allowMemoryOvercommit) {
        this.allowMemoryOvercommit = allowMemoryOvercommit;
    }
    
    public boolean isAllowCPUOvercommit() {
        return allowCPUOvercommit;
    }
    
    public void setAllowCPUOvercommit(boolean allowCPUOvercommit) {
        this.allowCPUOvercommit = allowCPUOvercommit;
    }
    
    public long getMaxDiskSpaceBytes() {
        return maxDiskSpaceBytes;
    }
    
    public void setMaxDiskSpaceBytes(long maxDiskSpaceBytes) {
        this.maxDiskSpaceBytes = maxDiskSpaceBytes;
    }
    
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