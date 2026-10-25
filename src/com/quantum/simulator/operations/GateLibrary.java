package com.quantum.simulator.operations;

import com.quantum.simulator.state.Qubit;
import java.util.*;

/**
 * Registry of gate instances and gate factories.
 *
 * <p>Instances registered through {@link #registerGate(String, QuantumGate)} are returned by
 * {@link #getGate(String)}. Factories registered through
 * {@link #registerFactory(String, GateFactory)} are used by {@link #createGate} to build gates
 * from a parameter map and a qubit array. The two registries are keyed independently, so a name
 * can refer to an instance, a factory, or both.</p>
 *
 * <p>The constructor registers factories for the standard gates. {@code X}, {@code Y}, {@code Z},
 * {@code H}, {@code S}, and {@code T} take one qubit. {@code RX}, {@code RY}, and {@code RZ} take
 * one qubit and an optional {@code "angle"} parameter, defaulting to {@code 0.0}. {@code CNOT} and
 * {@code CZ} take a control qubit followed by a target qubit. {@code SWAP} takes two target
 * qubits. {@code CPHASE} takes a control qubit and a target qubit plus an optional angle,
 * defaulting to {@code Math.PI}. {@code Toffoli} takes two control qubits followed by a target
 * qubit.</p>
 */
public class GateLibrary {
    private final Map<String, QuantumGate> gates;
    private final Map<String, GateFactory> gateFactories;
    
    /**
     * Creates an empty registry and registers the factories for the standard gates.
     */
    public GateLibrary() {
        this.gates = new HashMap<>();
        this.gateFactories = new HashMap<>();
        registerStandardGates();
    }
    
    private void registerStandardGates() {
        // Register factory methods for standard gates
        registerFactory("X", (params, qubits) -> {
            if (qubits.length != 1) throw new IllegalArgumentException("X gate requires exactly 1 qubit");
            return SingleQubitGate.createPauliX(qubits[0]);
        });
        
        registerFactory("Y", (params, qubits) -> {
            if (qubits.length != 1) throw new IllegalArgumentException("Y gate requires exactly 1 qubit");
            return SingleQubitGate.createPauliY(qubits[0]);
        });
        
        registerFactory("Z", (params, qubits) -> {
            if (qubits.length != 1) throw new IllegalArgumentException("Z gate requires exactly 1 qubit");
            return SingleQubitGate.createPauliZ(qubits[0]);
        });
        
        registerFactory("H", (params, qubits) -> {
            if (qubits.length != 1) throw new IllegalArgumentException("H gate requires exactly 1 qubit");
            return SingleQubitGate.createHadamard(qubits[0]);
        });
        
        registerFactory("S", (params, qubits) -> {
            if (qubits.length != 1) throw new IllegalArgumentException("S gate requires exactly 1 qubit");
            return SingleQubitGate.createS(qubits[0]);
        });
        
        registerFactory("T", (params, qubits) -> {
            if (qubits.length != 1) throw new IllegalArgumentException("T gate requires exactly 1 qubit");
            return SingleQubitGate.createT(qubits[0]);
        });
        
        registerFactory("RX", (params, qubits) -> {
            if (qubits.length != 1) throw new IllegalArgumentException("RX gate requires exactly 1 qubit");
            double angle = getParam(params, "angle", 0.0);
            return SingleQubitGate.createRX(angle, qubits[0]);
        });
        
        registerFactory("RY", (params, qubits) -> {
            if (qubits.length != 1) throw new IllegalArgumentException("RY gate requires exactly 1 qubit");
            double angle = getParam(params, "angle", 0.0);
            return SingleQubitGate.createRY(angle, qubits[0]);
        });
        
        registerFactory("RZ", (params, qubits) -> {
            if (qubits.length != 1) throw new IllegalArgumentException("RZ gate requires exactly 1 qubit");
            double angle = getParam(params, "angle", 0.0);
            return SingleQubitGate.createRZ(angle, qubits[0]);
        });
        
        registerFactory("CNOT", (params, qubits) -> {
            if (qubits.length != 2) throw new IllegalArgumentException("CNOT gate requires exactly 2 qubits");
            return MultiQubitGate.createCNOT(qubits[0], qubits[1]);
        });
        
        registerFactory("CZ", (params, qubits) -> {
            if (qubits.length != 2) throw new IllegalArgumentException("CZ gate requires exactly 2 qubits");
            return MultiQubitGate.createCZ(qubits[0], qubits[1]);
        });
        
        registerFactory("SWAP", (params, qubits) -> {
            if (qubits.length != 2) throw new IllegalArgumentException("SWAP gate requires exactly 2 qubits");
            return MultiQubitGate.createSWAP(qubits[0], qubits[1]);
        });
        
        registerFactory("Toffoli", (params, qubits) -> {
            if (qubits.length != 3) throw new IllegalArgumentException("Toffoli gate requires exactly 3 qubits");
            return MultiQubitGate.createToffoli(qubits[0], qubits[1], qubits[2]);
        });
        
        registerFactory("CPHASE", (params, qubits) -> {
            if (qubits.length != 2) throw new IllegalArgumentException("CPHASE gate requires exactly 2 qubits");
            double angle = getParam(params, "angle", Math.PI);
            return MultiQubitGate.createCPHASE(angle, qubits[0], qubits[1]);
        });
    }
    
    /**
     * Registers a prebuilt gate instance under the given name.
     *
     * <p>Only the instance registry is checked, so a factory with the same name does not cause a
     * conflict.</p>
     *
     * @param name the name used by {@link #getGate(String)}
     * @param gate the gate instance to store
     * @throws IllegalArgumentException if a gate instance with this name is already registered
     */
    public void registerGate(String name, QuantumGate gate) {
        if (gates.containsKey(name)) {
            throw new IllegalArgumentException("Gate with name '" + name + "' already exists");
        }
        gates.put(name, gate);
    }
    
    /**
     * Registers a factory that builds a gate from parameters and qubits.
     *
     * <p>Only the factory registry is checked, so a gate instance with the same name does not
     * cause a conflict.</p>
     *
     * @param name the name used by {@link #createGate}
     * @param factory the factory to invoke
     * @throws IllegalArgumentException if a factory with this name is already registered
     */
    public void registerFactory(String name, GateFactory factory) {
        if (gateFactories.containsKey(name)) {
            throw new IllegalArgumentException("Factory for gate '" + name + "' already exists");
        }
        gateFactories.put(name, factory);
    }
    
    /**
     * Returns the gate instance registered under the given name.
     *
     * <p>This method consults only the instance registry, not the factory registry.</p>
     *
     * @param name the registered gate name
     * @return the registered gate
     * @throws IllegalArgumentException if no gate instance uses this name
     */
    public QuantumGate getGate(String name) {
        QuantumGate gate = gates.get(name);
        if (gate == null) {
            throw new IllegalArgumentException("Gate not found: " + name);
        }
        return gate;
    }
    
    /**
     * Creates a gate by invoking the factory registered under the given name.
     *
     * <p>The parameter map is passed to the factory unchanged. The standard factories read an
     * {@code "angle"} entry for {@code RX}, {@code RY}, {@code RZ}, and {@code CPHASE}. The value
     * may be a {@link Number} or a string that parses as a double. {@code RX}, {@code RY}, and
     * {@code RZ} default to {@code 0.0}, while {@code CPHASE} defaults to {@code Math.PI}.</p>
     *
     * @param name the factory name
     * @param params factory parameters, may be {@code null}
     * @param qubits the qubits operated on, in the order expected by the factory
     * @return the created gate
     * @throws IllegalArgumentException if no factory is registered under the name, or if the
     *         factory fails, including when the qubit count or an angle value is invalid
     */
    public QuantumGate createGate(String name, Map<String, Object> params, Qubit... qubits) {
        GateFactory factory = gateFactories.get(name);
        if (factory == null) {
            throw new IllegalArgumentException("No factory found for gate: " + name);
        }
        
        try {
            return factory.create(params, qubits);
        } catch (Exception e) {
            throw new IllegalArgumentException("Failed to create gate '" + name + "': " + e.getMessage(), e);
        }
    }
    
    /**
     * Creates a gate using an empty parameter map.
     *
     * @param name the factory name
     * @param qubits the qubits operated on
     * @return the created gate
     * @throws IllegalArgumentException if no factory is registered under the name, or if the
     *         factory fails
     */
    public QuantumGate createGate(String name, Qubit... qubits) {
        return createGate(name, Collections.emptyMap(), qubits);
    }
    
    /**
     * Returns whether the name is registered as a gate instance or as a factory.
     *
     * @param name the name to test
     * @return {@code true} if either registry contains the name
     */
    public boolean containsGate(String name) {
        return gates.containsKey(name) || gateFactories.containsKey(name);
    }
    
    /**
     * Returns the union of registered instance names and factory names.
     *
     * @return an unmodifiable set containing a snapshot of both name sets
     */
    public Set<String> getAvailableGates() {
        Set<String> allGates = new HashSet<>(gates.keySet());
        allGates.addAll(gateFactories.keySet());
        return Collections.unmodifiableSet(allGates);
    }
    
    /**
     * Removes the name from both the instance registry and the factory registry.
     *
     * <p>No error is raised when the name is absent. Standard factories removed by this method are
     * restored only by {@link #clear()}.</p>
     *
     * @param name the name to remove
     */
    public void removeGate(String name) {
        gates.remove(name);
        gateFactories.remove(name);
    }
    
    /**
     * Clears both registries and then re-registers the standard gate factories.
     *
     * <p>Custom gate instances and custom factories are discarded.</p>
     */
    public void clear() {
        gates.clear();
        gateFactories.clear();
        registerStandardGates(); // Keep standard gates
    }
    
    private double getParam(Map<String, Object> params, String key, double defaultValue) {
        if (params == null || !params.containsKey(key)) {
            return defaultValue;
        }
        
        Object value = params.get(key);
        if (value instanceof Number) {
            return ((Number) value).doubleValue();
        } else if (value instanceof String) {
            try {
                return Double.parseDouble((String) value);
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("Invalid parameter value for '" + key + "': " + value);
            }
        } else {
            throw new IllegalArgumentException("Parameter '" + key + "' must be a number");
        }
    }
    
    /**
     * Functional interface for building a gate from a parameter map and an array of qubits.
     */
    @FunctionalInterface
    public interface GateFactory {
        /**
         * Creates a gate.
         *
         * @param params factory parameters, may be {@code null}
         * @param qubits the qubits operated on
         * @return the created gate
         */
        QuantumGate create(Map<String, Object> params, Qubit[] qubits);
    }
}