# QuantLab

QuantLab is an event-driven quantum circuit simulator written in Java. It is a quantum computing project, not a quantitative finance library. Circuits are described as timed `QuantumEvent` objects and executed against a state-vector or density-matrix backend.

## Requirements

- JDK 17
- Maven 3.8 or newer

## Build and test

```bash
mvn -o compile
mvn -o test
```

The `-o` flag uses the local Maven cache. Remove it if dependencies need to be downloaded. The test suite covers gates, measurement, noise, algorithms, JSON loading, metrics, plugins, and VQE convergence.

## Quick start

Run all algorithm demos:

```bash
java -cp "target/classes;lib/*" example.QuantumAlgorithmsDemo ALL
```

On Linux or macOS use `:` instead of `;` in the classpath.

Run a specific demo (`GROVER`, `QFT`, `QPE`, `VQE`, `QNN`, `QSVM`, `SHOR_CODE`, `QAOA`, `TROTTER`, `QUANTUM_WALK`):

```bash
java -cp "target/classes;lib/*" example.QuantumAlgorithmsDemo VQE
```

Run a JSON-defined circuit:

```bash
java -cp "target/classes;lib/*" example.CircuitRunner bell
java -cp "target/classes;lib/*" example.CircuitRunner src/example/config/short_config.json
```

## Features

### Backends

- `STATE_VECTOR`: exact pure-state simulation, `2^n` complex amplitudes, up to 30 qubits in theory, limited by heap size.
- `DENSITY_MATRIX`: exact mixed-state simulation, `2^n x 2^n` matrix, up to 12 qubits. A 12-qubit density matrix alone is about 256 MiB.

The tensor-network, stabilizer, and path-integral enum entries were removed because they had no implementation. Only the two backends above are advertised.

### Gates

`H`, `X`, `Y`, `Z`, `S`, `T`, `RX`, `RY`, `RZ`, `P`, `CNOT`, `CZ`, `CPHASE`, `SWAP`, `Toffoli`, multi-controlled `Z`, and controlled `SWAP`. Arbitrary local unitaries can be applied with `MultiQubitGate`.

Gate matrices use the convention **control qubits first, then target qubits**. `QuantumGate.getMatrixQubitIndices()` returns exactly this ordering.

### Measurement

- Computational-basis and general Hermitian-observable measurement.
- Projective state collapse after destructive measurement, including partial measurement of a subset of qubits.
- Non-destructive measurement is supported.
- Reported statistics include sampled counts, outcome probabilities, eigenvalues, expectation value, variance, and standard error.
- Mid-circuit measurement updates the state before later gates run.

### Noise

- Depolarizing, dephasing, and amplitude-damping channels.
- Correct Kraus operators for one and multiple target qubits.
- Density-matrix mode applies the deterministic channel `sum K rho K^dagger`.
- State-vector mode uses quantum trajectories: one Kraus operator is sampled and the state is renormalized. Results are stochastic per run.
- A `NoiseProfile` in `SimulationConfig` is applied after every gate. Manual `NoiseModel` events can also be scheduled.
- Fidelity and trace distance are computed from the density matrices rather than placeholders.

### Algorithms and examples

- Grover search for arbitrary marked states, using exact multi-controlled phase gates.
- Quantum Fourier transform and phase estimation, including controlled powers of a supplied unitary and inverse QFT.
- Variational quantum eigensolver with circuit execution and Hamiltonian expectation values.
- QAOA MaxCut circuit construction.
- Trotterized Heisenberg evolution with `X`, `Y`, `Z`, `XX`, `YY`, and `ZZ` terms.
- Shor 9-qubit error-correction encoding.
- Continuous-time and discrete-time quantum walks.
- Small trained QNN and swap-test quantum kernel classifier demos.

The QNN and QSVM code is educational, not production machine learning.

### Other APIs

- JSON configuration and circuit loading, with unknown fields ignored and comments accepted.
- `SimulationObserver` callbacks and a populated `MetricsCollector` (state updates, operations, fidelity, distance, measurements, throughput).
- Plugin API exposed through `QuantumSimulator.getPluginManager()`, with lifecycle and hook dispatch.
- Java2D visualization: Bloch-style state rendering, density matrices, circuit diagrams, event timelines, purity plots, and animated GIFs.

## Configuration

`SimulationConfig.loadFromFile()` reads JSON. A minimal configuration:

```json
{
  "numQubits": 2,
  "backend": "DENSITY_MATRIX",
  "noiseProfile": {
    "global_noise": {
      "depolarizing": 0.001
    },
    "qubit_noise": {
      "0": {
        "dephasing": 0.002
      }
    }
  },
  "maxSteps": 1000
}
```

A value of `0` for `maxSteps` means unlimited. `SimulationConfig.saveToFile()` writes a round-trippable configuration.

A circuit file:

```json
{
  "name": "Bell State Circuit",
  "description": "Creates a Bell state",
  "gates": [
    {"id": "h0", "type": "H", "targetQubits": [0]},
    {"id": "cnot01", "type": "CNOT", "targetQubits": [1], "controlQubits": [0]}
  ]
}
```

## Programmatic use

```java
SimulationConfig config = new SimulationConfig();
config.setNumQubits(2);
config.setBackend(SimulationBackend.STATE_VECTOR);

QuantumSimulator simulator = new QuantumSimulator();
simulator.initialize(config);

Qubit q0 = simulator.getContext().getQubit(0);
Qubit q1 = simulator.getContext().getQubit(1);

simulator.scheduleEvent(new QuantumEvent.Builder("H")
    .withOperation(SingleQubitGate.createHadamard(q0))
    .withTargetQubits(q0)
    .build());
simulator.scheduleEvent(new QuantumEvent.Builder("CNOT")
    .withOperation(MultiQubitGate.createCNOT(q0, q1))
    .withTargetQubits(q1)
    .build());

SimulationResult result = simulator.run();
System.out.println(result.getFinalState());
```

## Project layout

```
src/com/quantum/simulator/      core simulator, states, gates, noise, measurement, observers, plugins
src/example/                    demos, algorithm circuits, JSON resources
src/test/java/                  JUnit 5 tests
```

The canonical algorithm entry point is `example.QuantumAlgorithmsDemo`. Older demo programs are kept as additional examples but overlap with it.

## Limitations

- No OpenQASM import or export; circuits are built in Java or JSON.
- Noise in configuration mode is gate-based, not time-based idle noise.
- State-vector noise is trajectory-based and therefore stochastic.
- Multi-qubit depolarizing is limited to 4 target qubits because the channel requires `4^n - 1` Kraus operators.
- Independent multi-qubit dephasing and damping are limited to 8 target qubits.
- Visualization uses Java2D and local PNG/GIF files; there is no interactive UI.
- No tensor-network or stabilizer backend.
