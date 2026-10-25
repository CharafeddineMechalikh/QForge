package com.quantum.simulator.operations;

import com.quantum.simulator.state.Qubit;
import java.util.*;

public class GateLibrary {
    private final Map<String, QuantumGate> gates;
    private final Map<String, GateFactory> gateFactories;
    
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
    
    public void registerGate(String name, QuantumGate gate) {
        if (gates.containsKey(name)) {
            throw new IllegalArgumentException("Gate with name '" + name + "' already exists");
        }
        gates.put(name, gate);
    }
    
    public void registerFactory(String name, GateFactory factory) {
        if (gateFactories.containsKey(name)) {
            throw new IllegalArgumentException("Factory for gate '" + name + "' already exists");
        }
        gateFactories.put(name, factory);
    }
    
    public QuantumGate getGate(String name) {
        QuantumGate gate = gates.get(name);
        if (gate == null) {
            throw new IllegalArgumentException("Gate not found: " + name);
        }
        return gate;
    }
    
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
    
    public QuantumGate createGate(String name, Qubit... qubits) {
        return createGate(name, Collections.emptyMap(), qubits);
    }
    
    public boolean containsGate(String name) {
        return gates.containsKey(name) || gateFactories.containsKey(name);
    }
    
    public Set<String> getAvailableGates() {
        Set<String> allGates = new HashSet<>(gates.keySet());
        allGates.addAll(gateFactories.keySet());
        return Collections.unmodifiableSet(allGates);
    }
    
    public void removeGate(String name) {
        gates.remove(name);
        gateFactories.remove(name);
    }
    
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
    
    // Functional interface for gate factories
    @FunctionalInterface
    public interface GateFactory {
        QuantumGate create(Map<String, Object> params, Qubit[] qubits);
    }
}