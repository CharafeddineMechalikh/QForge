package com.quantum.simulator.resources;

import com.quantum.simulator.config.ResourceConstraints;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Tracks and arbitrates CPU, GPU, and memory allocations against a
 * {@link ResourceConstraints} limit.
 *
 * <p>Capacity is held as the number of available CPU cores, GPUs, and megabytes of
 * memory. The constructor converts
 * {@link ResourceConstraints#getMaxMemoryBytes()} to megabytes. Allocation and
 * release run while holding a {@link ReentrantLock}, and the availability counters
 * are updated in the same critical section. Successful allocations are counted in
 * the total allocation count, rejected requests in the failed allocation count, and
 * per requester history is appended on allocation and never pruned.</p>
 */
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
    
    /**
     * Creates a manager whose capacity is taken from the constraints.
     *
     * @param constraints the resource limits; the maximum CPU, GPU, and memory values
     *                    are read at construction and again by capacity queries
     * @throws NullPointerException if {@code constraints} is {@code null}
     */
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
    
    /**
     * Allocates resources for a request when the available capacity allows it.
     *
     * <p>An allocation identifier is generated as
     * {@code requesterId + "_" + currentTimeMillis + "_" + totalAllocations}, where
     * the last part is the successful allocation count before this allocation. On
     * success the requested amounts are subtracted from the available counters, the
     * allocation is added to the active set and to the requester history, and the
     * successful allocation count is incremented. On failure the failed allocation
     * count is incremented and no availability counter changes.</p>
     *
     * @param request the request to satisfy
     * @return the new active allocation
     * @throws ResourceAllocationException if the available CPU, GPU, or memory is
     *                                     lower than requested; the exception carries
     *                                     a capacity snapshot
     * @throws NullPointerException if {@code request} is {@code null}
     */
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
    
    /**
     * Releases an allocation and returns its resources to the available counters.
     *
     * <p>The active allocation stored under the same identifier is removed and marked
     * released; it is not necessarily the supplied instance.</p>
     *
     * @param allocation the allocation to release
     * @throws IllegalArgumentException if the allocation identifier is not in the
     *                                  active set, for example when it was already
     *                                  released
     * @throws NullPointerException if {@code allocation} is {@code null}
     */
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
    
    /**
     * Releases every active allocation belonging to a requester. Allocations of other
     * requesters are left untouched, and a requester with no active allocation is a
     * no-op.
     *
     * @param requesterId the requester whose allocations should be released
     */
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
    
    /**
     * Releases every active allocation.
     */
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
    
    /**
     * Tests whether the available capacity covers every requested amount.
     *
     * @param request the request to test
     * @return {@code true} when available CPUs, GPUs, and memory are all at least the
     *         requested amounts
     */
    public boolean canSatisfyRequest(ResourceRequest request) {
        return availableCPUs.get() >= request.getCpuCores() &&
               availableGPUs.get() >= request.getGpuCount() &&
               availableMemory.get() >= request.getMemoryMB();
    }
    
    /**
     * Returns a snapshot of the available and total capacity.
     *
     * @return a status with the current available counters and the maximum values from
     *         the constraints
     */
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
    
    /**
     * Returns used and total capacity, where used is the maximum minus the available
     * counter.
     *
     * @return the current utilization
     */
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
    
    /**
     * Returns the active allocations.
     *
     * @return a new list containing the currently active allocations
     */
    public List<ResourceAllocation> getActiveAllocations() {
        return new ArrayList<>(activeAllocations.values());
    }
    
    /**
     * Returns the allocations recorded for a requester, including released ones.
     *
     * @param requesterId the requester identifier
     * @return a new list with the recorded allocations, or an empty list when the
     *         requester has no history
     */
    public List<ResourceAllocation> getAllocationHistory(String requesterId) {
        List<ResourceAllocation> history = allocationHistory.get(requesterId);
        return history != null ? new ArrayList<>(history) : Collections.emptyList();
    }
    
    /**
     * Returns allocation counts and utilization at the current time.
     *
     * @return statistics with successful and failed allocation counts, the active
     *         allocation count, current utilization, and the current time in epoch
     *         milliseconds
     */
    public ResourceStatistics getStatistics() {
        return new ResourceStatistics(
            totalAllocations,
            failedAllocations,
            activeAllocations.size(),
            getUtilization(),
            System.currentTimeMillis()
        );
    }
    
    /**
     * Releases all active allocations whose duration has elapsed. Allocations without a
     * duration never expire and are left active.
     */
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
    
    /**
     * Builds an allocation identifier from the requester, the current time, and the
     * successful allocation count before the new allocation.
     *
     * @param requesterId the requester identifier
     * @return the generated identifier
     */
    private String generateAllocationId(String requesterId) {
        return requesterId + "_" + System.currentTimeMillis() + "_" + totalAllocations;
    }
    
    // Exception class
    /**
     * Checked exception raised when a request cannot be satisfied, carrying the
     * capacity status at the time of failure.
     */
    public static class ResourceAllocationException extends Exception {
        private final ResourceStatus availableResources;
        
        /**
         * Creates the exception.
         *
         * @param message the detail message
         * @param availableResources the capacity snapshot at the time of failure
         */
        public ResourceAllocationException(String message, ResourceStatus availableResources) {
            super(message);
            this.availableResources = availableResources;
        }
        
        /**
         * Returns the capacity snapshot at the time of failure.
         *
         * @return the available resources
         */
        public ResourceStatus getAvailableResources() {
            return availableResources;
        }
    }
}