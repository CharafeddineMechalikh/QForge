package example;

import com.mechalikh.qforge.resources.*;
import com.mechalikh.qforge.config.ResourceConstraints;
import com.mechalikh.qforge.config.SimulationBackend;
import com.mechalikh.qforge.config.SimulationConfig;
import com.mechalikh.qforge.core.*;
import com.mechalikh.qforge.operations.*;
import com.mechalikh.qforge.state.*;
import com.mechalikh.qforge.events.*;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;

/**
 * Demonstrates several simulations sharing a single {@link ResourceManager} from a fixed thread
 * pool, with an allocation fallback for rejected requests.
 *
 * <p>Four tasks request CPU and memory derived from their qubit count; the first runs at high
 * priority. If a request is rejected, a smaller request is made and the circuit is run with one
 * fewer qubit. While the tasks run, the main thread polls utilization for a fixed monitoring
 * period. Results are collected after monitoring, so the reported parallelism reflects this
 * program's structure rather than an optimized distributed scheduler.</p>
 */
public class DistributedQuantumSimulation {
    
    /**
     * Starts four concurrent simulations, monitors resources, collects results, and prints a final
     * resource report.
     *
     * <p>No command line arguments are read. Monitoring polls every two seconds for 30 seconds,
     * which happens while the futures are still pending. The printed analysis includes per
     * simulation runtime, a total and average runtime, an efficiency ratio, and the resource
     * statistics. The method waits up to one minute for the executor to terminate.</p>
     *
     * @param args ignored
     * @throws Exception if setup, monitoring, or awaiting termination fails
     */
    public static void main(String[] args) throws Exception {
        System.out.println("=== DISTRIBUTED QUANTUM SIMULATION ===");
        
        // 1. Create resource manager with cluster-like resources
        ResourceConstraints clusterResources = new ResourceConstraints(
            1024 * 1024 * 1024L, // 1GB total memory
            16,                  // 16 CPU cores
            2                    // 2 GPUs
        );
        
        ResourceManager clusterManager = new ResourceManager(clusterResources);
        
        // 2. Simulate multiple concurrent quantum simulations
        ExecutorService executor = Executors.newFixedThreadPool(4);
        List<Future<SimulationResult>> futures = new ArrayList<>();
        
        System.out.println("Starting 4 concurrent quantum simulations...");
        
        for (int i = 0; i < 4; i++) {
            int simulationId = i;
            Future<SimulationResult> future = executor.submit(() -> {
                return runDistributedSimulation(clusterManager, simulationId);
            });
            futures.add(future);
        }
        
        // 3. Monitor resource usage during simulations
        monitorResources(clusterManager, Duration.ofSeconds(30));
        
        // 4. Collect results
        List<SimulationResult> results = new ArrayList<>();
        for (Future<SimulationResult> future : futures) {
            try {
                results.add(future.get());
            } catch (Exception e) {
                System.err.println("Simulation failed: " + e.getMessage());
            }
        }
        
        // 5. Analyze combined results
        analyzeDistributedResults(results);
        
        executor.shutdown();
        executor.awaitTermination(1, TimeUnit.MINUTES);
        
        // 6. Final resource report
        printFinalResourceReport(clusterManager);
    }
    
    private static SimulationResult runDistributedSimulation(ResourceManager resourceManager, int simId) 
            throws Exception {
        String requesterId = "simulation_" + simId;
        
        // Request resources based on simulation size
        int numQubits = 4 + simId; // Different sizes for each simulation
        int requiredMemory = 64 * (1 << numQubits); // Memory grows exponentially
        
        ResourceRequest request = ResourceRequest.newBuilder(requesterId)
            .withCpuCores(2)
            .withMemoryMB(requiredMemory)
            .withDuration(Duration.ofSeconds(60))
            .withPriority(simId == 0 ? ResourceRequest.Priority.HIGH : ResourceRequest.Priority.NORMAL)
            .withPurpose("Quantum circuit simulation with " + numQubits + " qubits")
            .build();
        
        System.out.println("[Sim " + simId + "] Requesting resources: " + request);
        
        ResourceAllocation allocation = null;
        try {
            allocation = resourceManager.allocate(request);
            System.out.println("[Sim " + simId + "] Resources allocated: " + allocation);
            
            // Run the actual simulation
            return runQuantumCircuit(numQubits, simId);
            
        } catch (ResourceManager.ResourceAllocationException e) {
            System.err.println("[Sim " + simId + "] Failed to allocate resources: " + e.getMessage());
            
            // Fallback: Try with reduced resources
            ResourceRequest reducedRequest = ResourceRequest.newBuilder(requesterId + "_reduced")
                .withCpuCores(1)
                .withMemoryMB(requiredMemory / 2)
                .withDuration(Duration.ofSeconds(30))
                .withPriority(ResourceRequest.Priority.LOW)
                .build();
            
            allocation = resourceManager.allocate(reducedRequest);
            System.out.println("[Sim " + simId + "] Using reduced resources");
            
            return runQuantumCircuit(numQubits - 1, simId); // Run smaller circuit
        } finally {
            if (allocation != null) {
                resourceManager.release(allocation);
                System.out.println("[Sim " + simId + "] Resources released");
            }
        }
    }
    
    private static SimulationResult runQuantumCircuit(int numQubits, int simId) throws Exception {
        System.out.println("[Sim " + simId + "] Running " + numQubits + "-qubit circuit...");
        
        SimulationConfig config = new SimulationConfig();
        config.setNumQubits(numQubits);
        config.setBackend(SimulationBackend.STATE_VECTOR);
        
        QuantumSimulator simulator = new QuantumSimulator();
        simulator.initialize(config);
        
        // Create random quantum circuit
        Random random = new Random(simId);
        List<Qubit> qubits = new ArrayList<>();
        for (int i = 0; i < numQubits; i++) {
            qubits.add(new Qubit(i, "q" + i));
        }
        
        Instant currentTime = Instant.now();
        
        // Apply random gates
        for (int gateIdx = 0; gateIdx < 20; gateIdx++) {
            int qubitIdx = random.nextInt(numQubits);
            Qubit target = qubits.get(qubitIdx);
            
            QuantumOperation gate;
            if (random.nextDouble() < 0.7) {
                // Single-qubit gate
                double angle = random.nextDouble() * Math.PI;
                switch (random.nextInt(4)) {
                    case 0: gate = SingleQubitGate.createRX(angle, target); break;
                    case 1: gate = SingleQubitGate.createRY(angle, target); break;
                    case 2: gate = SingleQubitGate.createRZ(angle, target); break;
                    default: gate = SingleQubitGate.createHadamard(target); break;
                }
            } else {
                // Two-qubit gate (if possible)
                if (numQubits >= 2) {
                    int controlIdx = random.nextInt(numQubits);
                    while (controlIdx == qubitIdx) {
                        controlIdx = random.nextInt(numQubits);
                    }
                    Qubit control = qubits.get(controlIdx);
                    
                    if (random.nextBoolean()) {
                        gate = MultiQubitGate.createCNOT(control, target);
                    } else {
                        double angle = random.nextDouble() * Math.PI;
                        gate = MultiQubitGate.createCPHASE(angle, control, target);
                    }
                } else {
                    continue; // Skip two-qubit gates for single-qubit systems
                }
            }
            
            QuantumEvent event = new QuantumEvent.Builder("gate_" + gateIdx + "_sim" + simId)
                .withScheduledTime(currentTime)
                .withOperation(gate)
                .withTargetQubits(target)
                .build();
            
            simulator.scheduleEvent(event);
            currentTime = currentTime.plusMillis(random.nextInt(10) + 1);
        }
        
        return simulator.run();
    }
    
    private static void monitorResources(ResourceManager resourceManager, Duration monitoringDuration) {
        System.out.println("\n=== RESOURCE MONITORING ===");
        System.out.println("Monitoring for " + monitoringDuration.getSeconds() + " seconds...");
        
        Instant start = Instant.now();
        Instant end = start.plus(monitoringDuration);
        
        while (Instant.now().isBefore(end)) {
            try {
                Thread.sleep(2000); // Check every 2 seconds
                
                ResourceStatus status = resourceManager.getAvailableResources();
                ResourceUtilization util = resourceManager.getUtilization();
                List<ResourceAllocation> active = resourceManager.getActiveAllocations();
                
                System.out.printf(Locale.ROOT, "\n[%s] CPU: %.1f%%, Memory: %.1f%%, Active: %d\n",
                    Instant.now().toString(),
                    util.getCpuUsage() * 100,
                    util.getMemoryUsage() * 100,
                    active.size());
                
                // Print active allocations
                for (ResourceAllocation alloc : active) {
                    Duration age = alloc.getAge(System.currentTimeMillis());
                    System.out.printf(Locale.ROOT, "  %s (age: %s)\n", 
                        alloc.getRequesterId(), 
                        formatDuration(age));
                }
                
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
    }
    
    private static void analyzeDistributedResults(List<SimulationResult> results) {
        System.out.println("\n=== DISTRIBUTED RESULTS ANALYSIS ===");
        
        double totalRuntime = 0;
        long totalOperations = 0;
        
        for (int i = 0; i < results.size(); i++) {
            SimulationResult result = results.get(i);
            if (result != null) {
                totalRuntime += result.getTotalRuntimeMs();
                
                // Extract metrics (simplified - in reality would use MetricsCollector)
                System.out.printf(Locale.ROOT, "Simulation %d: %d ms\n", i, result.getTotalRuntimeMs());
            }
        }
        
        System.out.printf(Locale.ROOT, "\nTotal runtime: %.0f ms\n", totalRuntime);
        System.out.printf(Locale.ROOT, "Average runtime: %.0f ms\n", totalRuntime / results.size());
        
        // Calculate efficiency
        double parallelEfficiency = calculateParallelEfficiency(results);
        System.out.printf(Locale.ROOT, "Parallel efficiency: %.1f%%\n", parallelEfficiency * 100);
    }
    
    private static double calculateParallelEfficiency(List<SimulationResult> results) {
        if (results.isEmpty()) return 0.0;
        
        // Simple efficiency calculation
        double maxRuntime = results.stream()
            .filter(Objects::nonNull)
            .mapToDouble(SimulationResult::getTotalRuntimeMs)
            .max()
            .orElse(0.0);
        
        double totalRuntime = results.stream()
            .filter(Objects::nonNull)
            .mapToDouble(SimulationResult::getTotalRuntimeMs)
            .sum();
        
        if (maxRuntime == 0) return 0.0;
        
        // Efficiency = (sum of individual runtimes) / (n * longest runtime)
        return totalRuntime / (results.size() * maxRuntime);
    }
    
    private static void printFinalResourceReport(ResourceManager resourceManager) {
        System.out.println("\n=== FINAL RESOURCE REPORT ===");
        
        ResourceStatistics stats = resourceManager.getStatistics();
        System.out.println(stats);
        
        // Print allocation history summary
        Map<String, Integer> allocationCounts = new HashMap<>();
        // In real implementation, you would track this differently
        
        System.out.println("\nResource utilization peaks:");
        System.out.printf(Locale.ROOT, "  Maximum concurrent allocations: %d\n", stats.getActiveAllocations());
        System.out.printf(Locale.ROOT, "  Success rate: %.1f%%\n", stats.getSuccessRate() * 100);
        
        // Cleanup expired allocations
        resourceManager.cleanupExpiredAllocations();
        System.out.println("Cleaned up expired allocations");
    }
    
    private static String formatDuration(Duration duration) {
        long seconds = duration.getSeconds();
        if (seconds < 60) {
            return seconds + "s";
        } else if (seconds < 3600) {
            return String.format(Locale.ROOT, "%dm %ds", seconds / 60, seconds % 60);
        } else {
            return String.format(Locale.ROOT, "%dh %dm", seconds / 3600, (seconds % 3600) / 60);
        }
    }
}