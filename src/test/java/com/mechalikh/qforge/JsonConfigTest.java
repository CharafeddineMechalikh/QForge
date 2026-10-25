package com.mechalikh.qforge;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mechalikh.qforge.config.SimulationBackend;
import com.mechalikh.qforge.config.SimulationConfig;
import example.CircuitRunner;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class JsonConfigTest {
    private static final String CONFIG = "src/example/config/bell_state_config.json";
    private static final String CIRCUIT = "src/example/circuits/bell_state_circuit.json";

    @TempDir
    Path tempDir;

    @Test
    void loadsBellStateConfiguration() throws Exception {
        SimulationConfig config = new SimulationConfig();
        config.loadFromFile(CONFIG);
        assertEquals("Bell State Simulation", config.getName());
        assertEquals(2, config.getNumQubits());
        assertEquals(SimulationBackend.STATE_VECTOR, config.getBackend());
        assertEquals(100, config.getMaxSteps());
        assertEquals(2, config.getNoiseProfile().getAllNoiseModels().size());
    }

    @Test
    void configurationRoundTripsThroughJson() throws Exception {
        SimulationConfig config = new SimulationConfig();
        config.loadFromFile(CONFIG);
        Path saved = tempDir.resolve("config.json");
        config.saveToFile(saved.toString());

        SimulationConfig reloaded = new SimulationConfig();
        reloaded.loadFromFile(saved.toString());
        assertEquals(config.getName(), reloaded.getName());
        assertEquals(config.getNumQubits(), reloaded.getNumQubits());
        assertEquals(config.getBackend(), reloaded.getBackend());
        assertEquals(config.getMaxSteps(), reloaded.getMaxSteps());
        assertEquals(config.getNoiseProfile().getAllNoiseModels().size(),
            reloaded.getNoiseProfile().getAllNoiseModels().size());
    }

    @Test
    void unknownFieldsAreIgnored() throws Exception {
        Path file = tempDir.resolve("extra.json");
        Files.writeString(file, """
            {
              "numQubits": 1,
              "backend": "STATE_VECTOR",
              "timeResolution": "NANOSECOND",
              "unknownFutureField": {"a": 1}
            }
            """);
        SimulationConfig config = new SimulationConfig();
        config.loadFromFile(file.toString());
        assertEquals(1, config.getNumQubits());
    }

    @Test
    void missingRequiredFieldFailsClearly() throws Exception {
        Path file = tempDir.resolve("invalid.json");
        Files.writeString(file, "{\"backend\": \"STATE_VECTOR\"}");
        SimulationConfig config = new SimulationConfig();
        IOException error = assertThrows(IOException.class, () -> config.loadFromFile(file.toString()));
        assertTrue(error.getMessage().contains("numQubits"));
    }

    @Test
    void parsesCircuitFileWithDescription() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        CircuitRunner.CircuitDefinition circuit = mapper.readValue(
            Path.of(CIRCUIT).toFile(), CircuitRunner.CircuitDefinition.class);

        assertEquals("Bell State Circuit", circuit.getName());
        assertTrue(circuit.getDescription().contains("Bell state"));
        assertEquals(2, circuit.getGates().size());
        assertEquals("H", circuit.getGates().get(0).getType());
        assertEquals(0, circuit.getGates().get(0).getTargetQubits()[0]);
        assertEquals("CNOT", circuit.getGates().get(1).getType());
        assertEquals(0, circuit.getGates().get(1).getControlQubits()[0]);
        assertEquals(1, circuit.getGates().get(1).getTargetQubits()[0]);
    }
}
