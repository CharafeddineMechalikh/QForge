package com.quantum.simulator.noise;

import com.quantum.simulator.state.Qubit;
import java.util.*;

public class NoiseProfile {
    private final Map<Qubit, List<NoiseModel>> qubitNoiseMap;
    private final Map<String, NoiseModel> globalNoiseModels;
    private final String name;
    
    public NoiseProfile() {
        this("DefaultNoiseProfile");
    }
    
    public NoiseProfile(String name) {
        this.name = name;
        this.qubitNoiseMap = new HashMap<>();
        this.globalNoiseModels = new HashMap<>();
    }
    
    public void addNoise(Qubit qubit, NoiseModel noise) {
        qubitNoiseMap.computeIfAbsent(qubit, k -> new ArrayList<>()).add(noise);
    }
    
    public void addGlobalNoise(String name, NoiseModel noise) {
        globalNoiseModels.put(name, noise);
    }
    
    public void removeNoise(Qubit qubit, NoiseModel noise) {
        List<NoiseModel> noises = qubitNoiseMap.get(qubit);
        if (noises != null) {
            noises.remove(noise);
            if (noises.isEmpty()) {
                qubitNoiseMap.remove(qubit);
            }
        }
    }
    
    public void removeGlobalNoise(String name) {
        globalNoiseModels.remove(name);
    }
    
    public List<NoiseModel> getNoiseModels(Qubit qubit) {
        List<NoiseModel> result = new ArrayList<>();
        
        // Add qubit-specific noise
        List<NoiseModel> qubitNoise = qubitNoiseMap.get(qubit);
        if (qubitNoise != null) {
            result.addAll(qubitNoise);
        }
        
        // Add global noise that affects this qubit
        for (NoiseModel noise : globalNoiseModels.values()) {
            if (affectsQubit(noise, qubit)) {
                result.add(noise);
            }
        }
        
        return Collections.unmodifiableList(result);
    }
    
    public List<NoiseModel> getAllNoiseModels() {
        List<NoiseModel> allNoise = new ArrayList<>();
        
        // Add all qubit-specific noise
        for (List<NoiseModel> noises : qubitNoiseMap.values()) {
            allNoise.addAll(noises);
        }
        
        // Add all global noise
        allNoise.addAll(globalNoiseModels.values());
        
        return Collections.unmodifiableList(allNoise);
    }
    
    public boolean hasNoise(Qubit qubit) {
        if (qubitNoiseMap.containsKey(qubit)) {
            return true;
        }
        
        // Check if any global noise affects this qubit
        for (NoiseModel noise : globalNoiseModels.values()) {
            if (affectsQubit(noise, qubit)) {
                return true;
            }
        }
        
        return false;
    }
    
    public boolean isEmpty() {
        return qubitNoiseMap.isEmpty() && globalNoiseModels.isEmpty();
    }
    
    public void clear() {
        qubitNoiseMap.clear();
        globalNoiseModels.clear();
    }
    
    public void clearQubitNoise(Qubit qubit) {
        qubitNoiseMap.remove(qubit);
    }
    
    public void clearGlobalNoise() {
        globalNoiseModels.clear();
    }
    
    public String getName() {
        return name;
    }
    
    public Set<Qubit> getAffectedQubits() {
        Set<Qubit> affected = new HashSet<>();
        
        // Add qubits with specific noise
        affected.addAll(qubitNoiseMap.keySet());
        
        // Add qubits affected by global noise
        for (NoiseModel noise : globalNoiseModels.values()) {
            affected.addAll(Arrays.asList(noise.getTargetQubits()));
        }
        
        return Collections.unmodifiableSet(affected);
    }
    
    public double getTotalErrorProbability(Qubit qubit) {
        double totalProb = 0.0;
        for (NoiseModel noise : getNoiseModels(qubit)) {
            totalProb += noise.getProbability();
        }
        return Math.min(totalProb, 1.0); // Cap at 1.0
    }
    
    public NoiseProfile combine(NoiseProfile other) {
        NoiseProfile combined = new NoiseProfile(this.name + "+" + other.name);
        
        // Copy this profile's noise
        for (Map.Entry<Qubit, List<NoiseModel>> entry : this.qubitNoiseMap.entrySet()) {
            for (NoiseModel noise : entry.getValue()) {
                combined.addNoise(entry.getKey(), noise);
            }
        }
        
        for (Map.Entry<String, NoiseModel> entry : this.globalNoiseModels.entrySet()) {
            combined.addGlobalNoise(entry.getKey(), entry.getValue());
        }
        
        // Add other profile's noise
        for (Map.Entry<Qubit, List<NoiseModel>> entry : other.qubitNoiseMap.entrySet()) {
            for (NoiseModel noise : entry.getValue()) {
                combined.addNoise(entry.getKey(), noise);
            }
        }
        
        for (Map.Entry<String, NoiseModel> entry : other.globalNoiseModels.entrySet()) {
            combined.addGlobalNoise(entry.getKey(), entry.getValue());
        }
        
        return combined;
    }
    
    private boolean affectsQubit(NoiseModel noise, Qubit qubit) {
        for (Qubit target : noise.getTargetQubits()) {
            if (target.equals(qubit)) {
                return true;
            }
        }
        return false;
    }
    
    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("NoiseProfile['").append(name).append("']:\n");
        
        if (!globalNoiseModels.isEmpty()) {
            sb.append("  Global noise models:\n");
            for (NoiseModel noise : globalNoiseModels.values()) {
                sb.append("    - ").append(noise).append("\n");
            }
        }
        
        if (!qubitNoiseMap.isEmpty()) {
            sb.append("  Qubit-specific noise:\n");
            for (Map.Entry<Qubit, List<NoiseModel>> entry : qubitNoiseMap.entrySet()) {
                sb.append("    Qubit ").append(entry.getKey().getLabel()).append(":\n");
                for (NoiseModel noise : entry.getValue()) {
                    sb.append("      - ").append(noise).append("\n");
                }
            }
        }
        
        if (isEmpty()) {
            sb.append("  No noise models configured\n");
        }
        
        return sb.toString();
    }
    
    public Map<String, Object> toMap() {
        Map<String, Object> result = new LinkedHashMap<>();
        
        if (!globalNoiseModels.isEmpty()) {
            Map<String, Object> global = new LinkedHashMap<>();
            for (NoiseModel noise : globalNoiseModels.values()) {
                global.put(noiseTypeName(noise), noise.getProbability());
            }
            result.put("global_noise", global);
        }
        
        if (!qubitNoiseMap.isEmpty()) {
            Map<String, Object> qubitNoise = new LinkedHashMap<>();
            for (Map.Entry<Qubit, List<NoiseModel>> entry : qubitNoiseMap.entrySet()) {
                Map<String, Object> models = new LinkedHashMap<>();
                for (NoiseModel noise : entry.getValue()) {
                    models.put(noiseTypeName(noise), noise.getProbability());
                }
                qubitNoise.put(String.valueOf(entry.getKey().getIndex()), models);
            }
            result.put("qubit_noise", qubitNoise);
        }
        
        return result;
    }
    
    private static String noiseTypeName(NoiseModel noise) {
        if (noise instanceof DepolarizingNoise) {
            return "depolarizing";
        }
        if (noise instanceof DephasingNoise) {
            return "dephasing";
        }
        if (noise instanceof AmplitudeDampingNoise) {
            return "amplitude_damping";
        }
        return noise.getName();
    }
    
    public void loadFromMap(Map<String, Object> noiseMap) {        if (noiseMap == null || noiseMap.isEmpty()) {
            return;
        }
        
        // Clear existing noise
        clear();
        
        // Parse noise configuration
        // Expected format:
        // {
        //   "global_noise": {
        //     "depolarizing": 0.01,
        //     "dephasing": 0.02
        //   },
        //   "qubit_noise": {
        //     "0": {
        //       "depolarizing": 0.01,
        //       "amplitude_damping": 0.005
        //     },
        //     "1": {
        //       "dephasing": 0.03
        //     }
        //   }
        // }
        
        // Handle global noise
        if (noiseMap.containsKey("global_noise")) {
            Object globalNoiseObj = noiseMap.get("global_noise");
            if (globalNoiseObj instanceof Map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> globalNoise = (Map<String, Object>) globalNoiseObj;
                loadGlobalNoiseFromMap(globalNoise);
            }
        }
        
        // Handle qubit-specific noise
        if (noiseMap.containsKey("qubit_noise")) {
            Object qubitNoiseObj = noiseMap.get("qubit_noise");
            if (qubitNoiseObj instanceof Map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> qubitNoise = (Map<String, Object>) qubitNoiseObj;
                loadQubitNoiseFromMap(qubitNoise);
            }
        }
        
        // Handle simple format (all qubits get same noise)
        if (noiseMap.containsKey("noise_type") && noiseMap.containsKey("probability")) {
            String noiseType = noiseMap.get("noise_type").toString();
            double probability = Double.parseDouble(noiseMap.get("probability").toString());
            loadSimpleNoise(noiseType, probability);
        }
    }
    
    private void loadGlobalNoiseFromMap(Map<String, Object> globalNoise) {
        for (Map.Entry<String, Object> entry : globalNoise.entrySet()) {
            String noiseType = entry.getKey();
            Object value = entry.getValue();
            
            try {
                double probability = parseProbability(value);
                NoiseModel noiseModel = createNoiseModel(noiseType, probability);
                addGlobalNoise(noiseType, noiseModel);
            } catch (Exception e) {
                System.err.println("Failed to load global noise '" + noiseType + "': " + e.getMessage());
            }
        }
    }
    
    private void loadQubitNoiseFromMap(Map<String, Object> qubitNoise) {
        for (Map.Entry<String, Object> entry : qubitNoise.entrySet()) {
            String qubitIndexStr = entry.getKey();
            Object noiseObj = entry.getValue();
            
            if (!(noiseObj instanceof Map)) {
                continue;
            }
            
            @SuppressWarnings("unchecked")
            Map<String, Object> noiseMap = (Map<String, Object>) noiseObj;
            
            try {
                int qubitIndex = Integer.parseInt(qubitIndexStr);
                Qubit qubit = new Qubit(qubitIndex);
                
                for (Map.Entry<String, Object> noiseEntry : noiseMap.entrySet()) {
                    String noiseType = noiseEntry.getKey();
                    Object value = noiseEntry.getValue();
                    
                    double probability = parseProbability(value);
                    NoiseModel noiseModel = createNoiseModel(noiseType, probability, qubit);
                    addNoise(qubit, noiseModel);
                }
            } catch (NumberFormatException e) {
                System.err.println("Invalid qubit index: " + qubitIndexStr);
            } catch (Exception e) {
                System.err.println("Failed to load noise for qubit " + qubitIndexStr + ": " + e.getMessage());
            }
        }
    }
    
    private void loadSimpleNoise(String noiseType, double probability) {
        // This creates a global noise that affects all qubits
        NoiseModel noiseModel = createNoiseModel(noiseType, probability);
        addGlobalNoise("simple_" + noiseType, noiseModel);
    }
    
    private double parseProbability(Object value) {
        if (value instanceof Number) {
            return ((Number) value).doubleValue();
        } else if (value instanceof String) {
            return Double.parseDouble((String) value);
        } else {
            throw new IllegalArgumentException("Probability must be a number or string, got: " + 
                (value != null ? value.getClass().getName() : "null"));
        }
    }
    
    private NoiseModel createNoiseModel(String noiseType, double probability) {
        // Create noise model without specific target qubits (global)
        return createNoiseModel(noiseType, probability, null);
    }
    
    private NoiseModel createNoiseModel(String noiseType, double probability, Qubit target) {
        // Normalize noise type string
        String normalizedType = noiseType.toLowerCase().trim();
        Qubit[] targets = target == null ? new Qubit[0] : new Qubit[]{target};
        
        switch (normalizedType) {
            case "depolarizing":
            case "depolarizing_noise":
                return new DepolarizingNoise(probability, targets);
                
            case "dephasing":
            case "dephasing_noise":
            case "phase_damping":
                return new DephasingNoise(probability, targets);
                
            case "amplitude_damping":
            case "amplitude_damping_noise":
                return new AmplitudeDampingNoise(probability, targets);
                
            default:
                throw new IllegalArgumentException("Unknown noise type: " + noiseType);
        }
    }
}