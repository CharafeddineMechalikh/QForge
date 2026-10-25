package example;

import com.mechalikh.qforge.config.SimulationBackend;
import com.mechalikh.qforge.config.SimulationConfig;
import com.mechalikh.qforge.core.QuantumSimulator;
import com.mechalikh.qforge.core.SimulationContext;
import com.mechalikh.qforge.state.Qubit;
import com.mechalikh.qforge.state.StateVector;
import com.mechalikh.qforge.operations.SingleQubitGate;
import com.mechalikh.qforge.events.QuantumEvent;
import com.mechalikh.qforge.math.Complex;
import java.time.Instant;

/**
 * Diagnostic program that inspects {@link SimulationContext} through reflection and runs a single
 * Hadamard gate both with {@code run()} and with {@code step()}.
 *
 * <p>The reflection output lists only members whose names contain {@code state}. The program
 * prints each step and catches exceptions from the simulator so that a failure is reported rather
 * than propagated. It writes no files.</p>
 */
public class DebugQuantumSimulationApp {
    /**
     * Runs the reflection check and the simulator test.
     *
     * <p>No command line arguments are read. The method prints the available state related methods
     * and fields, the result of a full run, and the outcome of a single step.</p>
     *
     * @param args ignored
     * @throws Exception if creating the simulator context or running the simulator fails outside
     *     the locally handled blocks
     */
    public static void main(String[] args) throws Exception {
        System.out.println("=== Event System Debug (Fixed) ===");
        
        // Let's first check what methods SimulationContext actually has
        System.out.println("\n1. Checking SimulationContext methods...");
        checkSimulationContextMethods();
        
        // Test the actual QuantumSimulator
        testQuantumSimulatorDirectly();
    }
    
    private static void checkSimulationContextMethods() {
        System.out.println("SimulationContext available methods:");
        
        // Create a minimal config to create context
        SimulationConfig config = new SimulationConfig();
        config.setNumQubits(1);
        
        try {
            SimulationContext context = new SimulationContext(config);
            
            // Use reflection to list methods
            java.lang.reflect.Method[] methods = context.getClass().getMethods();
            System.out.println("Public methods of SimulationContext:");
            for (java.lang.reflect.Method method : methods) {
                if (method.getName().toLowerCase().contains("state")) {
                    System.out.println("  - " + method.getName() + 
                        " returns " + method.getReturnType().getSimpleName());
                }
            }
            
            // Also check fields
            System.out.println("\nChecking for state-related fields:");
            java.lang.reflect.Field[] fields = context.getClass().getDeclaredFields();
            for (java.lang.reflect.Field field : fields) {
                if (field.getName().toLowerCase().contains("state")) {
                    field.setAccessible(true);
                    System.out.println("  - " + field.getName() + " : " + field.getType().getSimpleName());
                }
            }
            
        } catch (Exception e) {
            System.out.println("Error: " + e.getMessage());
        }
    }
    
    private static void testQuantumSimulatorDirectly() throws Exception {
        System.out.println("\n\n2. Testing QuantumSimulator Directly");
        
        SimulationConfig config = new SimulationConfig();
        config.setNumQubits(1);
        config.setBackend(SimulationBackend.STATE_VECTOR);
        
        QuantumSimulator simulator = new QuantumSimulator();
        simulator.initialize(config);
        
        Qubit q0 = new Qubit(0);
        SingleQubitGate h = SingleQubitGate.createHadamard(q0);
        
        // Create event
        QuantumEvent event = new QuantumEvent.Builder("test_h")
            .withScheduledTime(Instant.now())
            .withOperation(h)
            .withTargetQubits(q0)
            .build();
        
        // Schedule event
        System.out.println("Scheduling event...");
        simulator.scheduleEvent(event);
        
        // Check if simulator is running
        System.out.println("Simulator running: " + simulator.isRunning());
        
        // Try to run
        System.out.println("Running simulator...");
        try {
            var result = simulator.run();
            System.out.println("Simulation completed!");
            System.out.println("Result: " + result);
            
            if (result != null) {
                System.out.println("Final state: " + result.getFinalState());
            }
        } catch (Exception e) {
            System.out.println("Error during run(): " + e.getMessage());
            e.printStackTrace();
        }
        
        // Try step-by-step
        System.out.println("\n\n3. Trying step-by-step execution");
        
        simulator.reset();
        simulator.initialize(config);
        
        // Schedule event again
        QuantumEvent event2 = new QuantumEvent.Builder("test_h2")
            .withScheduledTime(Instant.now())
            .withOperation(h)
            .withTargetQubits(q0)
            .build();
        
        simulator.scheduleEvent(event2);
        
        System.out.println("Calling step()...");
        try {
            simulator.step();
            System.out.println("Step completed!");
        } catch (Exception e) {
            System.out.println("Error during step(): " + e.getMessage());
            e.printStackTrace();
        }
    }
}