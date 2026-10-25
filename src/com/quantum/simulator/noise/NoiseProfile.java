package com.quantum.simulator.noise;

import com.quantum.simulator.state.Qubit;
import java.util.*;

/**
 * A collection of noise models attached either to specific qubits or globally.
 *
 * <p>The profile keeps two stores. The per qubit store maps a {@link Qubit} to an ordered list of
 * models, so several models can be chained on the same qubit and the insertion order is kept. The
 * global store maps a string key to a single model, so adding a global model with an existing key
 * replaces the previous one. Qubits are used as map keys and compare by index only, so two
 * {@code Qubit} objects for the same index address the same entry.</p>
 *
 * <p>{@link #getNoiseModels(Qubit)} merges both stores for one qubit: the qubit specific models
 * come first in insertion order, followed by every global model whose target list contains that
 * qubit. A global model with an empty target array is therefore returned for no qubit by that
 * method, even though {@link #getAllNoiseModels()} still lists it once.
 * {@link ErrorManager#applyNoise} treats such targetless models as applying to every qubit of the
 * state.</p>
 *
 * <p>The profile does not prevent a model from being added to several qubits, so
 * {@link #getAllNoiseModels()} may contain the same instance more than once.</p>
 *
 * <p>The mutable operations change the profile in place; the getters return unmodifiable views or
 * copies, so callers cannot edit the internal stores through them.</p>
 */
public class NoiseProfile {
    private final Map<Qubit, List<NoiseModel>> qubitNoiseMap;
    private final Map<String, NoiseModel> globalNoiseModels;
    private final String name;
    
    /**
     * Creates an empty profile named {@code "DefaultNoiseProfile"}.
     */
    public NoiseProfile() {
        this("DefaultNoiseProfile");
    }
    
    /**
     * Creates an empty profile with the given name.
     *
     * @param name the profile name reported by {@link #getName()}
     */
    public NoiseProfile(String name) {
        this.name = name;
        this.qubitNoiseMap = new HashMap<>();
        this.globalNoiseModels = new HashMap<>();
    }
    
    /**
     * Adds a noise model to one qubit.
     *
     * <p>The model is appended to that qubit's list, so adding the same model twice stores it
     * twice and it will be applied twice.</p>
     *
     * @param qubit the qubit the model is attached to
     * @param noise the model to add
     * @throws NullPointerException if {@code qubit} or {@code noise} is {@code null}
     */
    public void addNoise(Qubit qubit, NoiseModel noise) {
        qubitNoiseMap.computeIfAbsent(qubit, k -> new ArrayList<>()).add(noise);
    }
    
    /**
     * Adds or replaces a global noise model under a key.
     *
     * <p>Global models are applied to every qubit in their own target list. Reusing a key replaces
     * the model stored for that key rather than adding a second one.</p>
     *
     * @param name the key identifying the model in the global store
     * @param noise the model to store
     * @throws NullPointerException if {@code name} or {@code noise} is {@code null}
     */
    public void addGlobalNoise(String name, NoiseModel noise) {
        globalNoiseModels.put(name, noise);
    }
    
    /**
     * Removes one qubit specific model.
     *
     * <p>The first occurrence equal to {@code noise} is removed, and the qubit entry is dropped
     * when its list becomes empty. Global models are not affected.</p>
     *
     * @param qubit the qubit whose list is edited
     * @param noise the model instance to remove
     */
    public void removeNoise(Qubit qubit, NoiseModel noise) {
        List<NoiseModel> noises = qubitNoiseMap.get(qubit);
        if (noises != null) {
            noises.remove(noise);
            if (noises.isEmpty()) {
                qubitNoiseMap.remove(qubit);
            }
        }
    }
    
    /**
     * Removes a global noise model by key.
     *
     * @param name the key of the model to remove
     */
    public void removeGlobalNoise(String name) {
        globalNoiseModels.remove(name);
    }
    
    /**
     * Returns the models that apply to one qubit.
     *
     * <p>The result concatenates the qubit specific models, in the order they were added, with
     * every global model whose target list contains the qubit. The returned list is a new
     * unmodifiable list; changing the profile later does not change it.</p>
     *
     * @param qubit the qubit to query
     * @return an unmodifiable list of applicable models, which may be empty
     */
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
    
    /**
     * Returns every model stored in the profile.
     *
     * <p>The result lists all qubit specific models, grouped by qubit in map iteration order, then
     * all global models in map iteration order. A model attached to several qubits appears once
     * per attachment.</p>
     *
     * @return a new unmodifiable list of all stored models
     */
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
    
    /**
     * Reports whether any model applies to one qubit.
     *
     * <p>This is true when the qubit has its own list, or when some global model targets it. A
     * global model with no targets does not make this method return true.</p>
     *
     * @param qubit the qubit to query
     * @return {@code true} if at least one model applies to the qubit
     */
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
    
    /**
     * Reports whether the profile holds no models at all.
     *
     * @return {@code true} if both stores are empty
     */
    public boolean isEmpty() {
        return qubitNoiseMap.isEmpty() && globalNoiseModels.isEmpty();
    }
    
    /**
     * Removes every model from both stores.
     */
    public void clear() {
        qubitNoiseMap.clear();
        globalNoiseModels.clear();
    }
    
    /**
     * Removes all qubit specific models attached to one qubit.
     *
     * @param qubit the qubit whose entry is removed
     */
    public void clearQubitNoise(Qubit qubit) {
        qubitNoiseMap.remove(qubit);
    }
    
    /**
     * Removes every global model.
     */
    public void clearGlobalNoise() {
        globalNoiseModels.clear();
    }
    
    /**
     * Returns the profile name.
     *
     * @return the name supplied at construction
     */
    public String getName() {
        return name;
    }
    
    /**
     * Returns the qubits known to be affected by some model.
     *
     * <p>The result contains every qubit with its own entry plus every target of every global
     * model, compared by index. It is a new set and is not tied to the profile afterwards.</p>
     *
     * @return an unmodifiable set of affected qubits
     */
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
    
    /**
     * Sums the probabilities of the models that apply to one qubit.
     *
     * <p>The sum uses {@link #getNoiseModels(Qubit)}, so it includes global models targeting the
     * qubit. The result is capped at one; the sum itself is not a channel composition and can
     * exceed one when several models overlap.</p>
     *
     * @param qubit the qubit to query
     * @return the total probability, capped at {@code 1.0}
     */
    public double getTotalErrorProbability(Qubit qubit) {
        double totalProb = 0.0;
        for (NoiseModel noise : getNoiseModels(qubit)) {
            totalProb += noise.getProbability();
        }
        return Math.min(totalProb, 1.0); // Cap at 1.0
    }
    
    /**
     * Returns a new profile containing the models of this profile and another one.
     *
     * <p>The caller's profiles are not changed. The new name is
     * {@code this.name + "+" + other.name}, qubit entries are appended in turn, and global entries
     * of {@code other} overwrite global entries of this profile that use the same key.</p>
     *
     * @param other the profile to merge in
     * @return a new profile holding both sets of models
     * @throws NullPointerException if {@code other} is {@code null}
     */
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
    
    /**
     * Returns a multi line description of the profile.
     *
     * <p>Global models are listed first, then qubit specific models grouped by qubit label. When
     * the profile is empty a single line states that no models are configured.</p>
     *
     * @return the formatted description
     */
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
    
    /**
     * Exports the profile as a nested map suitable for serialization.
     *
     * <p>The result uses two optional keys. {@code "global_noise"} holds a map from noise type
     * name to probability, and {@code "qubit_noise"} holds a map from qubit index as a string to
     * another map from noise type name to probability. Keys are only present when the
     * corresponding store is not empty, and insertion order is preserved.</p>
     *
     * <p>The model's class decides its type name: {@code "depolarizing"}, {@code "dephasing"},
     * {@code "amplitude_damping"}, or the model name for anything else. Because the type name is
     * the inner key, two models of the same type on the same qubit collapse into a single entry
     * and only the last one is exported.</p>
     *
     * @return a new ordered map describing the profile
     */
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
    
    /**
     * Replaces the contents of this profile from a nested map.
     *
     * <p>The profile is cleared before loading. Three optional layouts are understood:</p>
     * <ul>
     *   <li>{@code "global_noise"}: a map from noise type to probability, each entry becoming a
     *       global model with no target qubits, stored under the type key</li>
     *   <li>{@code "qubit_noise"}: a map from qubit index as a string to a map from noise type to
     *       probability, each entry becoming a model targeted at a new {@link Qubit} with that
     *       index</li>
     *   <li>{@code "noise_type"} together with {@code "probability"}: a single global model
     *       stored under the key {@code "simple_" + noiseType}</li>
     * </ul>
     *
     * <p>Probabilities may be numbers or numeric strings. Recognised type names are
     * {@code "depolarizing"}, {@code "dephasing"} and {@code "amplitude_damping"}, with the
     * aliases {@code "depolarizing_noise"}, {@code "dephasing_noise"},
     * {@code "phase_damping"} and {@code "amplitude_damping_noise"}, compared case insensitively
     * after trimming. Errors in the first two layouts are reported on standard error and the
     * load continues with the remaining qubits, so a partially valid map still loads what it can.
     * The simple layout is not guarded: an unknown type or a non numeric probability propagates
     * as an exception.</p>
     *
     * @param noiseMap the map to read, which is left unchanged; {@code null} or empty leaves the
     *        profile untouched
     * @throws NumberFormatException if the simple layout probability is not numeric
     * @throws IllegalArgumentException if the simple layout names an unknown noise type
     */
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