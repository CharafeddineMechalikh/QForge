package example;

import com.quantum.simulator.resources.*;
import com.quantum.simulator.config.ResourceConstraints;
import com.quantum.simulator.config.SimulationBackend;
import com.quantum.simulator.config.SimulationConfig;
import com.quantum.simulator.core.*;
import com.quantum.simulator.events.QuantumEvent;
import com.quantum.simulator.operations.*;
import com.quantum.simulator.state.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;

/**
 * Submits four simulations with different priorities to a shared {@link ResourceManager} and
 * reports their completion order and the resource statistics.
 *
 * <p>Tasks are sorted by priority before submission to a two thread pool. Each task requests two
 * CPU cores and its own memory size, sleeps for one to three seconds, runs a random circuit, and
 * releases its allocation. A rejected request makes that task return {@code null}, which the
 * caller reports as a completion without a runtime.</p>
 */
public class PriorityResourceScheduler {
    
    /**
     * Runs the scheduling demonstration and prints its analysis.
     *
     * <p>No command line arguments are read. The output lists the submission parameters, the
     * completion order with runtimes, the resource statistics, the average utilization, the
     * allocation success rate, and any allocations still active. The method waits up to one
     * minute for the executor to terminate.</p>
     *
     * @param args ignored
     * @throws Exception if the executor cannot be created or awaited
     */
    public static void main(String[] args) throws Exception {
        System.out.println("=== PRIORITY RESOURCE SCHEDULER ===");
        
        // Create resource manager with limited resources
        ResourceConstraints constraints = new ResourceConstraints(
            1024 * 1024 * 100, // 100MB
            4,                 // 4 CPUs
            0                  // No GPU
        );
        
        ResourceManager resourceManager = new ResourceManager(constraints);
        
        // Create different priority simulations
        List<SimulationTask> tasks = Arrays.asList(
            new SimulationTask("HighPriorityTask", ResourceRequest.Priority.HIGH, 4, 50),
            new SimulationTask("NormalPriorityTask1", ResourceRequest.Priority.NORMAL, 3, 30),
            new SimulationTask("NormalPriorityTask2", ResourceRequest.Priority.NORMAL, 3, 25),
            new SimulationTask("LowPriorityTask", ResourceRequest.Priority.LOW, 2, 20)
        );
        
        // Run tasks with priority scheduling
        runPriorityScheduling(resourceManager, tasks);
        
        // Analyze scheduling efficiency
        analyzeSchedulingPerformance(resourceManager);
    }
    
    private static void runPriorityScheduling(ResourceManager resourceManager, 
                                            List<SimulationTask> tasks) throws Exception {
        System.out.println("\nRunning " + tasks.size() + " tasks with priority scheduling...");
        
        ExecutorService executor = Executors.newFixedThreadPool(2); // Limited thread pool
        CompletionService<SimulationResult> completionService = 
            new ExecutorCompletionService<>(executor);
        
        Map<String, Future<SimulationResult>> futures = new HashMap<>();
        
        // Submit tasks in priority order
        tasks.sort(Comparator.comparing(SimulationTask::getPriority).reversed());
        
        for (SimulationTask task : tasks) {
            System.out.printf(Locale.ROOT, "\nSubmitting %s (Priority: %s, Qubits: %d, Memory: %dMB)\n",
                task.getName(), task.getPriority(), task.getNumQubits(), task.getMemoryMB());
            
            Future<SimulationResult> future = completionService.submit(() -> {
                return executeTaskWithResources(resourceManager, task);
            });
            
            futures.put(task.getName(), future);
        }
        
        // Collect results as they complete
        System.out.println("\n=== TASK COMPLETION ORDER ===");
        for (int i = 0; i < tasks.size(); i++) {
            try {
                Future<SimulationResult> completed = completionService.take();
                SimulationResult result = completed.get();
                
                // Find which task completed
                String completedTask = findCompletedTask(futures, completed);
                System.out.println((i + 1) + ". " + completedTask + " completed");
                
                if (result != null) {
                    System.out.printf(Locale.ROOT, "   Runtime: %d ms\n", result.getTotalRuntimeMs());
                }
            } catch (Exception e) {
                System.err.println("Task failed: " + e.getMessage());
            }
        }
        
        executor.shutdown();
        executor.awaitTermination(1, TimeUnit.MINUTES);
    }
    
    private static SimulationResult executeTaskWithResources(ResourceManager resourceManager, 
                                                           SimulationTask task) throws Exception {
        String requesterId = task.getName();
        
        // Request resources with task priority
        ResourceRequest request = ResourceRequest.newBuilder(requesterId)
            .withCpuCores(2)
            .withMemoryMB(task.getMemoryMB())
            .withDuration(Duration.ofSeconds(60))
            .withPriority(task.getPriority())
            .withPurpose("Quantum simulation: " + task.getName())
            .build();
        
        ResourceAllocation allocation = null;
        
        try {
            // Try to allocate resources (may wait if resources are busy)
            System.out.println("[" + requesterId + "] Waiting for resources...");
            allocation = resourceManager.allocate(request);
            System.out.println("[" + requesterId + "] Resources acquired");
            
            // Simulate some work
            Thread.sleep(1000 + (long)(Math.random() * 2000));
            
            // Run actual quantum simulation
            return runQuantumSimulation(task.getNumQubits(), requesterId);
            
        } catch (ResourceManager.ResourceAllocationException e) {
            System.err.println("[" + requesterId + "] Failed to allocate resources: " + e.getMessage());
            return null;
        } finally {
            if (allocation != null) {
                resourceManager.release(allocation);
                System.out.println("[" + requesterId + "] Resources released");
            }
        }
    }
    
    private static SimulationResult runQuantumSimulation(int numQubits, String taskId) throws Exception {
        SimulationConfig config = new SimulationConfig();
        config.setNumQubits(numQubits);
        config.setBackend(SimulationBackend.STATE_VECTOR);
        
        QuantumSimulator simulator = new QuantumSimulator();
        simulator.initialize(config);
        
        // Create simple circuit
        List<Qubit> qubits = new ArrayList<>();
        for (int i = 0; i < numQubits; i++) {
            qubits.add(new Qubit(i, "q" + i));
        }
        
        // Apply some gates
        Random random = new Random(taskId.hashCode());
        int numGates = 10 + random.nextInt(20);
        
        for (int i = 0; i < numGates; i++) {
            int qubitIdx = random.nextInt(numQubits);
            Qubit target = qubits.get(qubitIdx);
            
            QuantumOperation gate;
            if (random.nextDouble() < 0.7) {
                // Single-qubit gate
                double angle = random.nextDouble() * Math.PI;
                gate = SingleQubitGate.createRX(angle, target);
            } else {
                // Two-qubit gate (if possible)
                if (numQubits >= 2) {
                    int controlIdx = random.nextInt(numQubits);
                    while (controlIdx == qubitIdx) {
                        controlIdx = random.nextInt(numQubits);
                    }
                    Qubit control = qubits.get(controlIdx);
                    gate = MultiQubitGate.createCNOT(control, target);
                } else {
                    continue;
                }
            }
            
            QuantumEvent event = new QuantumEvent.Builder("gate_" + i + "_" + taskId)
                .withOperation(gate)
                .withTargetQubits(target)
                .build();
            
            simulator.scheduleEvent(event);
        }
        
        return simulator.run();
    }
    
    private static String findCompletedTask(Map<String, Future<SimulationResult>> futures, 
                                          Future<SimulationResult> completed) {
        for (Map.Entry<String, Future<SimulationResult>> entry : futures.entrySet()) {
            if (entry.getValue() == completed || 
                (entry.getValue().isDone() && completed.isDone())) {
                return entry.getKey();
            }
        }
        return "Unknown";
    }
    
    private static void analyzeSchedulingPerformance(ResourceManager resourceManager) {
        System.out.println("\n=== SCHEDULING PERFORMANCE ANALYSIS ===");
        
        ResourceStatistics stats = resourceManager.getStatistics();
        System.out.println(stats);
        
        // Calculate resource utilization efficiency
        ResourceUtilization util = resourceManager.getUtilization();
        double avgUtilization = (util.getCpuUsage() + util.getMemoryUsage()) / 2;
        
        System.out.printf(Locale.ROOT, "Average resource utilization: %.1f%%\n", avgUtilization * 100);
        System.out.printf(Locale.ROOT, "Allocation success rate: %.1f%%\n", stats.getSuccessRate() * 100);
        
        // List active allocations (should be empty after completion)
        List<ResourceAllocation> active = resourceManager.getActiveAllocations();
        if (!active.isEmpty()) {
            System.out.println("\nWarning: " + active.size() + " allocations still active!");
            for (ResourceAllocation alloc : active) {
                System.out.println("  " + alloc);
            }
        }
        
        // Cleanup any remaining allocations
        resourceManager.cleanupExpiredAllocations();
        System.out.println("Cleanup completed");
    }
    
    private static class SimulationTask {
        private final String name;
        private final ResourceRequest.Priority priority;
        private final int numQubits;
        private final int memoryMB;
        
        public SimulationTask(String name, ResourceRequest.Priority priority, 
                            int numQubits, int memoryMB) {
            this.name = name;
            this.priority = priority;
            this.numQubits = numQubits;
            this.memoryMB = memoryMB;
        }
        
        public String getName() { return name; }
        public ResourceRequest.Priority getPriority() { return priority; }
        public int getNumQubits() { return numQubits; }
        public int getMemoryMB() { return memoryMB; }
    }
}