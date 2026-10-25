package com.mechalikh.qforge;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.mechalikh.qforge.config.ResourceConstraints;
import com.mechalikh.qforge.resources.ResourceAllocation;
import com.mechalikh.qforge.resources.ResourceManager;
import com.mechalikh.qforge.resources.ResourceManager.ResourceAllocationException;
import com.mechalikh.qforge.resources.ResourceRequest;
import com.mechalikh.qforge.resources.ResourceStatistics;
import com.mechalikh.qforge.resources.ResourceStatus;
import com.mechalikh.qforge.resources.ResourceUtilization;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;

class ResourcesCoverageTest {

    private static final long MB = 1024L * 1024L;

    @Test
    void defaultConstraintsDeriveFromJvm() {
        ResourceConstraints constraints = new ResourceConstraints();

        assertTrue(constraints.getMaxMemoryBytes() > 0);
        assertTrue(constraints.getMaxCPUs() >= 1);
        assertEquals(0, constraints.getMaxGPUs());
        assertEquals(1024L * MB, constraints.getMaxDiskSpaceBytes());
        assertFalse(constraints.isAllowMemoryOvercommit());
        assertFalse(constraints.isAllowCPUOvercommit());
        assertTrue(constraints.getAvailableMemoryBytes() >= 0);
        assertTrue(constraints.getMemoryUtilization() >= 0.0);
        assertTrue(constraints.toString().contains("ResourceConstraints"));
        assertTrue(constraints.toString().contains("GB"));
        assertDoesNotThrow(constraints::validate);
    }

    @Test
    void explicitConstraintsValidateLimitsAndOvercommit() {
        long maxMemory = Runtime.getRuntime().maxMemory();
        int processors = Runtime.getRuntime().availableProcessors();

        ResourceConstraints constraints = new ResourceConstraints(64L * MB, 1, 0);
        assertTrue(constraints.validate());
        assertEquals(64L * MB, constraints.getMaxMemoryBytes());
        assertEquals(1, constraints.getMaxCPUs());
        assertEquals(0, constraints.getMaxGPUs());

        assertFalse(new ResourceConstraints(0, 1, 0).validate());
        assertFalse(new ResourceConstraints(64L * MB, 0, 0).validate());
        assertFalse(new ResourceConstraints(64L * MB, 1, -1).validate());
        ResourceConstraints negativeDisk = new ResourceConstraints(64L * MB, 1, 0);
        negativeDisk.setMaxDiskSpaceBytes(-1);
        assertFalse(negativeDisk.validate());

        ResourceConstraints tooMuchMemory = new ResourceConstraints(maxMemory + 1, 1, 0);
        assertFalse(tooMuchMemory.validate());
        tooMuchMemory.setAllowMemoryOvercommit(true);
        assertTrue(tooMuchMemory.validate());

        ResourceConstraints tooManyCpus = new ResourceConstraints(64L * MB, processors + 1, 0);
        assertFalse(tooManyCpus.validate());
        tooManyCpus.setAllowCPUOvercommit(true);
        assertTrue(tooManyCpus.validate());

        assertTrue(constraints.checkCPUAvailable(1));
        assertFalse(constraints.checkCPUAvailable(2));
        constraints.setAllowCPUOvercommit(true);
        assertTrue(constraints.checkCPUAvailable(2));

        constraints.setAllowMemoryOvercommit(true);
        assertTrue(constraints.checkMemoryAvailable(Long.MAX_VALUE));

        constraints.setMaxCPUs(2);
        constraints.setMaxGPUs(3);
        constraints.setMaxDiskSpaceBytes(42L);
        assertEquals(2, constraints.getMaxCPUs());
        assertEquals(3, constraints.getMaxGPUs());
        assertEquals(42L, constraints.getMaxDiskSpaceBytes());
    }

    @Test
    void allocationAndReleaseUpdateCapacity() throws Exception {
        ResourceManager manager = new ResourceManager(new ResourceConstraints(256L * MB, 2, 0));
        ResourceRequest request = ResourceRequest.newBuilder("alice")
            .withCpuCores(1)
            .withMemoryMB(64)
            .withPurpose("test")
            .build();

        ResourceAllocation allocation = manager.allocate(request);

        assertTrue(allocation.getAllocationId().startsWith("alice_"));
        assertEquals("alice", allocation.getRequesterId());
        assertEquals(1, allocation.getCpuCores());
        assertEquals(0, allocation.getGpuCount());
        assertEquals(64, allocation.getMemoryMB());
        assertNull(allocation.getDuration());
        assertFalse(allocation.isReleased());
        assertNull(allocation.getReleaseTime());
        assertEquals(Duration.ofMinutes(100), allocation.getAge(
            allocation.getAllocationTime() + Duration.ofMinutes(100).toMillis()));

        ResourceStatus status = manager.getAvailableResources();
        assertEquals(1, status.getAvailableCPUs());
        assertEquals(192, status.getAvailableMemoryMB());
        assertEquals(2, status.getTotalCPUs());
        assertEquals(256, status.getTotalMemoryMB());

        ResourceUtilization utilization = manager.getUtilization();
        assertEquals(1, utilization.getUsedCPUs());
        assertEquals(0.5, utilization.getCpuUsage(), 1e-12);
        assertEquals(0.25, utilization.getMemoryUsage(), 1e-12);

        ResourceStatistics statistics = manager.getStatistics();
        assertEquals(1, statistics.getTotalAllocations());
        assertEquals(0, statistics.getFailedAllocations());
        assertEquals(1, statistics.getActiveAllocations());
        assertEquals(1.0, statistics.getSuccessRate(), 1e-12);
        assertEquals(1, manager.getActiveAllocations().size());

        manager.release(allocation);
        assertTrue(allocation.isReleased());
        assertNotNull(allocation.getReleaseTime());
        assertEquals(Duration.ofMillis(allocation.getReleaseTime() - allocation.getAllocationTime()),
            allocation.getHoldTime(Long.MAX_VALUE));
        assertEquals(2, manager.getAvailableResources().getAvailableCPUs());
        assertEquals(256, manager.getAvailableResources().getAvailableMemoryMB());
        assertTrue(manager.getActiveAllocations().isEmpty());
        assertEquals(1, manager.getAllocationHistory("alice").size());

        assertThrows(IllegalArgumentException.class, () -> manager.release(allocation));
        assertThrows(NullPointerException.class, () -> manager.release(null));
        assertThrows(NullPointerException.class, () -> manager.allocate(null));
        assertTrue(manager.getAllocationHistory("unknown").isEmpty());
    }

    @Test
    void insufficientAllocationsAreCountedAndReported() {
        ResourceManager manager = new ResourceManager(new ResourceConstraints(128L * MB, 1, 0));

        ResourceAllocationException cpuFailure = assertThrows(ResourceAllocationException.class,
            () -> manager.allocate(ResourceRequest.newBuilder("cpu")
                .withCpuCores(2).withMemoryMB(16).build()));
        assertNotNull(cpuFailure.getAvailableResources());
        assertEquals(1, cpuFailure.getAvailableResources().getAvailableCPUs());
        assertTrue(cpuFailure.getMessage().contains("Insufficient resources"));

        assertThrows(ResourceAllocationException.class,
            () -> manager.allocate(ResourceRequest.newBuilder("memory")
                .withCpuCores(1).withMemoryMB(129).build()));
        assertThrows(ResourceAllocationException.class,
            () -> manager.allocate(ResourceRequest.newBuilder("gpu")
                .withCpuCores(1).withMemoryMB(16).withGpuCount(1).build()));

        ResourceStatistics statistics = manager.getStatistics();
        assertEquals(0, statistics.getTotalAllocations());
        assertEquals(3, statistics.getFailedAllocations());
        assertEquals(0, statistics.getActiveAllocations());
        assertEquals(0.0, statistics.getSuccessRate(), 1e-12);
        assertTrue(manager.canSatisfyRequest(
            ResourceRequest.newBuilder("ok").withCpuCores(1).withMemoryMB(128).build()));
    }

    @Test
    void releaseAllByRequesterAndGlobally() throws Exception {
        ResourceManager manager = new ResourceManager(new ResourceConstraints(3L * 1024L * MB, 3, 0));
        ResourceAllocation first = manager.allocate(
            ResourceRequest.newBuilder("alice").withCpuCores(1).withMemoryMB(16).build());
        ResourceAllocation second = manager.allocate(
            ResourceRequest.newBuilder("alice").withCpuCores(1).withMemoryMB(16).build());
        ResourceAllocation other = manager.allocate(
            ResourceRequest.newBuilder("bob").withCpuCores(1).withMemoryMB(16).build());

        manager.releaseAll("alice");
        assertTrue(first.isReleased());
        assertTrue(second.isReleased());
        assertFalse(other.isReleased());
        assertEquals(List.of(other), manager.getActiveAllocations());
        assertEquals(2, manager.getAllocationHistory("alice").size());

        manager.releaseAll("unknown");
        assertEquals(1, manager.getActiveAllocations().size());

        manager.releaseAll();
        assertTrue(other.isReleased());
        assertTrue(manager.getActiveAllocations().isEmpty());
        assertEquals(3, manager.getAvailableResources().getAvailableCPUs());
        assertEquals(1, manager.getAllocationHistory("bob").size());
    }

    @Test
    void expiredAllocationsAreReleasedOnCleanup() throws Exception {
        ResourceManager manager = new ResourceManager(new ResourceConstraints(64L * MB, 2, 0));
        ResourceAllocation expiring = manager.allocate(ResourceRequest.newBuilder("expiring")
            .withCpuCores(1).withMemoryMB(16)
            .withDuration(Duration.ofNanos(1))
            .build());
        ResourceAllocation permanent = manager.allocate(ResourceRequest.newBuilder("permanent")
            .withCpuCores(1).withMemoryMB(16)
            .build());

        assertEquals(Duration.ofNanos(1), expiring.getDuration());
        assertTrue(expiring.isExpired(expiring.getAllocationTime()));
        assertTrue(expiring.isExpired(Long.MAX_VALUE));
        assertNull(permanent.getDuration());
        assertFalse(permanent.isExpired(Long.MAX_VALUE));

        manager.cleanupExpiredAllocations();

        assertTrue(expiring.isReleased());
        assertFalse(permanent.isReleased());
        assertEquals(List.of(permanent), manager.getActiveAllocations());
        assertEquals(1, manager.getAvailableResources().getAvailableCPUs());
        assertEquals(48, manager.getAvailableResources().getAvailableMemoryMB());
    }

    @Test
    void statisticsAndUtilizationAccessors() {
        ResourceUtilization utilization = new ResourceUtilization(1, 4, 1, 2, 64, 256);
        ResourceStatistics statistics = new ResourceStatistics(5, 1, 2, utilization, 0L);

        assertEquals(5, statistics.getTotalAllocations());
        assertEquals(1, statistics.getFailedAllocations());
        assertEquals(2, statistics.getActiveAllocations());
        assertEquals(0L, statistics.getTimestamp());
        assertEquals(5.0 / 6.0, statistics.getSuccessRate(), 1e-12);
        assertEquals(2.5, statistics.getAllocationsPerMinute(120_000L), 1e-12);
        assertEquals(0.0, statistics.getAllocationsPerMinute(59_999L), 1e-12);
        assertTrue(statistics.toString().contains("ResourceStatistics"));
        assertEquals(1, utilization.getUsedCPUs());
        assertEquals(4, utilization.getTotalCPUs());
        assertEquals(1, utilization.getUsedGPUs());
        assertEquals(2, utilization.getTotalGPUs());
        assertEquals(64, utilization.getUsedMemoryMB());
        assertEquals(256, utilization.getTotalMemoryMB());
        assertEquals(0.25, utilization.getCpuUsage(), 1e-12);
        assertEquals(0.5, utilization.getGpuUsage(), 1e-12);
        assertEquals(0.25, utilization.getMemoryUsage(), 1e-12);
        assertTrue(utilization.toString().contains("ResourceUtilization"));

        ResourceStatistics empty = new ResourceStatistics(0, 0, 0,
            new ResourceUtilization(0, 0, 0, 0, 0, 0), 1000L);
        assertEquals(1.0, empty.getSuccessRate(), 1e-12);
        assertEquals(0.0, empty.getAllocationsPerMinute(2000L), 1e-12);
        assertEquals(0.0, empty.getUtilization().getCpuUsage(), 1e-12);
        assertEquals(0.0, empty.getUtilization().getGpuUsage(), 1e-12);
        assertEquals(0.0, empty.getUtilization().getMemoryUsage(), 1e-12);

        ResourceStatus status = new ResourceStatus(0, 0, 0, 2, 1, 128);
        assertFalse(status.hasAvailableResources());
        assertEquals(1.0, status.getCpuUtilization(), 1e-12);
        assertEquals(1.0, status.getGpuUtilization(), 1e-12);
        assertEquals(1.0, status.getMemoryUtilization(), 1e-12);
        assertTrue(status.toString().contains("ResourceStatus"));

        ResourceStatus available = new ResourceStatus(1, 0, 64, 2, 0, 128);
        assertTrue(available.hasAvailableResources());
        assertEquals(0.5, available.getCpuUtilization(), 1e-12);
        assertEquals(0.5, available.getMemoryUtilization(), 1e-12);
        assertEquals(0.0, available.getGpuUtilization(), 1e-12);
    }

    @Test
    void requestBuilderDefaultsValidateAndFormat() {
        ResourceRequest defaults = ResourceRequest.newBuilder("alice").build();
        assertEquals("alice", defaults.getRequesterId());
        assertEquals(1, defaults.getCpuCores());
        assertEquals(0, defaults.getGpuCount());
        assertEquals(1024, defaults.getMemoryMB());
        assertTrue(defaults.getDuration().isEmpty());
        assertEquals(ResourceRequest.Priority.NORMAL, defaults.getPriority());
        assertEquals("", defaults.getPurpose());
        assertTrue(defaults.toString().contains("ResourceRequest"));

        ResourceRequest custom = ResourceRequest.newBuilder("bob")
            .withCpuCores(2)
            .withGpuCount(1)
            .withMemoryGB(2)
            .withDuration(Duration.ofSeconds(30))
            .withPriority(ResourceRequest.Priority.HIGH)
            .withPurpose("simulation")
            .build();
        assertEquals(2, custom.getCpuCores());
        assertEquals(1, custom.getGpuCount());
        assertEquals(2048, custom.getMemoryMB());
        assertEquals(Duration.ofSeconds(30), custom.getDuration().orElseThrow());
        assertEquals(ResourceRequest.Priority.HIGH, custom.getPriority());
        assertEquals("simulation", custom.getPurpose());
        assertTrue(custom.toString().contains("priority=HIGH"));

        assertThrows(IllegalArgumentException.class,
            () -> ResourceRequest.newBuilder("x").withCpuCores(0));
        assertThrows(IllegalArgumentException.class,
            () -> ResourceRequest.newBuilder("x").withGpuCount(-1));
        assertThrows(IllegalArgumentException.class,
            () -> ResourceRequest.newBuilder("x").withMemoryMB(0));
        assertThrows(IllegalArgumentException.class,
            () -> ResourceRequest.newBuilder("x").withMemoryGB(0));
    }
}
