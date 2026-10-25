package com.quantum.simulator.resources;

import com.quantum.simulator.config.ResourceConstraints;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;

public class ResourceManager {
    private final ResourceConstraints constraints;
    private final Map<String, ResourceAllocation> activeAllocations;
    private final Map<String, List<ResourceAllocation>> allocationHistory;
    private final AtomicInteger availableCPUs;
    private final AtomicInteger availableGPUs;
    private final AtomicInteger availableMemory;
    private final ReentrantLock allocationLock;
    private long totalAllocations;
    private long failedAllocations;
    
    public ResourceManager(ResourceConstraints constraints) {
        this.constraints = constraints;
        this.activeAllocations = new ConcurrentHashMap<>();
        this.allocationHistory = new ConcurrentHashMap<>();
        this.availableCPUs = new AtomicInteger(constraints.getMaxCPUs());
        this.availableGPUs = new AtomicInteger(constraints.getMaxGPUs());
        this.availableMemory = new AtomicInteger((int) (constraints.getMaxMemoryBytes() / (1024 * 1024))); // MB
        this.allocationLock = new ReentrantLock();
        this.totalAllocations = 0;
        this.failedAllocations = 0;
    }
    
    public ResourceAllocation allocate(ResourceRequest request) throws ResourceAllocationException {
        Objects.requireNonNull(request, "Resource request cannot be null");
        
        allocationLock.lock();
        try {
            // Check if resources are available
            if (!canSatisfyRequest(request)) {
                failedAllocations++;
                throw new ResourceAllocationException(
                    "Insufficient resources for request: " + request,
                    getAvailableResources()
                );
            }
            
            // Generate allocation ID
            String allocationId = generateAllocationId(request.getRequesterId());
            
            // Create allocation
            ResourceAllocation allocation = new ResourceAllocation(
                allocationId,
                request.getRequesterId(),
                request.getCpuCores(),
                request.getGpuCount(),
                request.getMemoryMB(),
                request.getDuration().orElse(null),
                System.currentTimeMillis()
            );
            
            // Reserve resources
            availableCPUs.addAndGet(-request.getCpuCores());
            availableGPUs.addAndGet(-request.getGpuCount());
            availableMemory.addAndGet(-request.getMemoryMB());
            
            // Record allocation
            activeAllocations.put(allocationId, allocation);
            allocationHistory.computeIfAbsent(request.getRequesterId(), k -> new ArrayList<>())
                .add(allocation);
            
            totalAllocations++;
            
            return allocation;
            
        } finally {
            allocationLock.unlock();
        }
    }
    
    public void release(ResourceAllocation allocation) {
        Objects.requireNonNull(allocation, "Allocation cannot be null");
        
        allocationLock.lock();
        try {
            ResourceAllocation activeAlloc = activeAllocations.remove(allocation.getAllocationId());
            if (activeAlloc == null) {
                throw new IllegalArgumentException("Allocation not found or already released: " + allocation.getAllocationId());
            }
            
            // Return resources
            availableCPUs.addAndGet(activeAlloc.getCpuCores());
            availableGPUs.addAndGet(activeAlloc.getGpuCount());
            availableMemory.addAndGet(activeAlloc.getMemoryMB());
            
            // Mark as released
            activeAlloc.markReleased(System.currentTimeMillis());
            
        } finally {
            allocationLock.unlock();
        }
    }
    
    public void releaseAll(String requesterId) {
        allocationLock.lock();
        try {
            List<ResourceAllocation> toRelease = new ArrayList<>();
            
            // Find all allocations for this requester
            for (ResourceAllocation alloc : activeAllocations.values()) {
                if (alloc.getRequesterId().equals(requesterId)) {
                    toRelease.add(alloc);
                }
            }
            
            // Release all found allocations
            for (ResourceAllocation alloc : toRelease) {
                release(alloc);
            }
            
        } finally {
            allocationLock.unlock();
        }
    }
    
    public void releaseAll() {
        allocationLock.lock();
        try {
            List<ResourceAllocation> toRelease = new ArrayList<>(activeAllocations.values());
            for (ResourceAllocation alloc : toRelease) {
                release(alloc);
            }
        } finally {
            allocationLock.unlock();
        }
    }
    
    public boolean canSatisfyRequest(ResourceRequest request) {
        return availableCPUs.get() >= request.getCpuCores() &&
               availableGPUs.get() >= request.getGpuCount() &&
               availableMemory.get() >= request.getMemoryMB();
    }
    
    public ResourceStatus getAvailableResources() {
        return new ResourceStatus(
            availableCPUs.get(),
            availableGPUs.get(),
            availableMemory.get(),
            constraints.getMaxCPUs(),
            constraints.getMaxGPUs(),
            (int) constraints.getMaxMemoryBytes() / (1024 * 1024)
        );
    }
    
    public ResourceUtilization getUtilization() {
        int totalCPUs = constraints.getMaxCPUs();
        int totalGPUs = constraints.getMaxGPUs();
        int totalMemoryMB = (int) (constraints.getMaxMemoryBytes() / (1024 * 1024));
        
        int usedCPUs = totalCPUs - availableCPUs.get();
        int usedGPUs = totalGPUs - availableGPUs.get();
        int usedMemoryMB = totalMemoryMB - availableMemory.get();
        
        return new ResourceUtilization(
            usedCPUs, totalCPUs,
            usedGPUs, totalGPUs,
            usedMemoryMB, totalMemoryMB
        );
    }
    
    public List<ResourceAllocation> getActiveAllocations() {
        return new ArrayList<>(activeAllocations.values());
    }
    
    public List<ResourceAllocation> getAllocationHistory(String requesterId) {
        List<ResourceAllocation> history = allocationHistory.get(requesterId);
        return history != null ? new ArrayList<>(history) : Collections.emptyList();
    }
    
    public ResourceStatistics getStatistics() {
        return new ResourceStatistics(
            totalAllocations,
            failedAllocations,
            activeAllocations.size(),
            getUtilization(),
            System.currentTimeMillis()
        );
    }
    
    public void cleanupExpiredAllocations() {
        allocationLock.lock();
        try {
            long currentTime = System.currentTimeMillis();
            List<ResourceAllocation> toRelease = new ArrayList<>();
            
            for (ResourceAllocation alloc : activeAllocations.values()) {
                if (alloc.isExpired(currentTime)) {
                    toRelease.add(alloc);
                }
            }
            
            for (ResourceAllocation alloc : toRelease) {
                release(alloc);
            }
            
        } finally {
            allocationLock.unlock();
        }
    }
    
    private String generateAllocationId(String requesterId) {
        return requesterId + "_" + System.currentTimeMillis() + "_" + totalAllocations;
    }
    
    // Exception class
    public static class ResourceAllocationException extends Exception {
        private final ResourceStatus availableResources;
        
        public ResourceAllocationException(String message, ResourceStatus availableResources) {
            super(message);
            this.availableResources = availableResources;
        }
        
        public ResourceStatus getAvailableResources() {
            return availableResources;
        }
    }
}