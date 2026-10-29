# Changelog

All notable changes to QForge are documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

## [2.0.0] - 2026-10-29

### Added

- A stabilizer backend (`STABILIZER`) using the Aaronson-Gottesman tableau, with Clifford gate
  application, Pauli sampling, and an exact outcome distribution for up to 16 measured targets.
- A tensor-network backend (`TENSOR_NETWORK`) using a matrix product state with `maxBondDimension`
  truncation, SVD canonicalization, bond-dimension diagnostics, and a relative discarded-weight
  truncation metric.
- `AmplitudeAccess`, a capability interface implemented by the state vector, density matrix and
  tensor-network states, so metrics and probability queries skip states that cannot expose
  amplitudes.
- A device noise model attached through `SimulationConfig.setDeviceNoise` and the JSON
  `deviceNoise` object: per-qubit `T1` and `T2` thermal relaxation on idle qubits between scheduled
  events, per-gate dephasing crosstalk on idle neighbours, and per-qubit readout confusion
  (`readout_p01`, `readout_p10`).
- Classical registers and feed-forward execution: `SimulationConfig.addClassicalRegister`,
  `MeasurementOperator.withClassicalTarget`, `ConditionalOperation`, and `ShotRunner` for repeated
  shots of a circuit factory. The reported (post-readout) outcome is written to the register while
  the state collapses onto the true outcome.
- OpenQASM interchange in the new `qasm` package: `QasmParser` accepts a documented QASM 2.0 and
  3.0 subset (standard gates, custom gate expansion, `measure`, `if(c==N)`), and `QasmEmitter`
  exports QASM 2.0.
- Four standalone algorithm demos with measured output: `QaoaMaxCut` (Nelder-Mead over the exact
  cut expectation on a five-node graph), `QuantumArithmeticComplete` (exhaustive in-place adder,
  modular multiplier and modular exponentiation), `AmplitudeAmplificationComplete` (multiple marked
  items against the analytic success probability), and `QuantumClassifierDemo` (variational
  classifier on an Iris subset).
- `CrossBackendValidationTest`, comparing state vector, density matrix, stabilizer and tensor
  network on the same Clifford and non-Clifford circuits, GHZ, W, QFT and Grover fixtures, and
  branch coverage suites for QASM, the stabilizer and the tensor network.
- `example.benchmarks.BackendBenchmark` with its README: four-backend GHZ timings, density-matrix
  scaling, state-vector versus tensor-network scaling from 10 to 20 qubits, stabilizer scaling from
  100 to 1000 qubits, and measured backend limits.
- Four runnable examples with measured READMEs for the new capabilities:
  `example.qasm.QasmInterchange` (file import, simulation, emission, stable round trip and 200
  shots), `example.noise.DeviceNoiseExample` (idle relaxation, crosstalk and readout confusion,
  each isolated), `example.backends.StabilizerExample` (200-qubit GHZ, sampling, exact marginal
  and `T` rejection), and `example.backends.TensorNetworkExample` (bond-dimension sweep against
  exact probabilities, then the gate and dense-access limits).

### Changed

- The test suite grew to 511 tests, raising JaCoCo coverage to 98.0 percent instruction and 92.9
  percent branch over 130 classes.
- `SingleQubitGate.createS` and `createT` now emit the canonical names `S` and `T`, so the
  stabilizer backend can dispatch phase gates by name instead of rejecting a parametric `P` name.
- `SimulationConfig` gained `deviceNoise`, `classicalRegisters` and `maxBondDimension`, and the
  `SimulationBackend` enum exposes `STABILIZER` and `TENSOR_NETWORK` again with implementations
  behind them.
- The README, citation metadata and guides now describe four backends, device noise, feed-forward
  execution, OpenQASM exchange and the expanded algorithm set. The version is 2.0.0.

### Fixed

- `MPSState` truncation diagnostics now report the relative fraction of singular-value weight
  discarded at each truncation, instead of an unnormalized sum that could exceed one and was
  non-zero for separable circuits.
- `MetricsCollector` now checks the new `AmplitudeAccess.hasDenseAccess()` capability before
  requesting a probability array, so a matrix product state above 24 qubits runs instead of
  failing inside the fidelity metric. Above that width the state reports bond dimensions, purity
  and discarded weight, but not basis-state probabilities or measurement.

## [1.1.0] - 2026-10-29

### Added

- Five runnable examples under `src/example/algorithms/`, each with a README covering its intent,
  measured output, and diagrams: Deutsch-Jozsa and Bernstein-Vazirani oracles, quantum
  teleportation branch analysis, a BB84 key distribution with an eavesdropper, a depolarizing noise
  fidelity sweep, and Shor order finding that factors 15. The remaining example directories, and
  every runnable entry point, are documented in the same style.

- A GitHub Actions workflow (`.github/workflows/ci.yml`) that builds on JDK 17 and runs the tests
  plus the JaCoCo gates on every push and pull request to `main`, replacing the never-activated
  Travis pipeline.

- `CITATION.cff`, so GitHub can render a "Cite this repository" entry, plus `url` and `scm`
  metadata in `pom.xml`.

- An expanded unit test suite, 338 tests in total, raising JaCoCo instruction coverage to 97.7
  percent and branch coverage to 91.8 percent, with a `verify` rule that fails below 90 percent
  instruction or branch coverage.

- MIT license: a `LICENSE` file, a `<licenses>` entry in `pom.xml`, and `license: MIT` in
  `CITATION.cff`.

- A documentation site published with GitHub Pages from the `docs` folder on `main`, at
  <https://charafeddinemechalikh.github.io/QForge/>. It adds `docs/_config.yml`, a
  `docs/_layouts/default.html` page shell with the guide navigation, per page titles and
  descriptions and in-browser Mermaid rendering, and front matter on all nine guides.

### Changed

- Renamed the project from QuantLab to QForge. The Eclipse project name, README, docs, citation
  metadata and `pom.xml` URLs use QForge, and the Java packages stayed `com.quantum.simulator`.

- Moved the Maven groupId to `com.mechalikh`, so the coordinates are `com.mechalikh:qforge`.

- Renamed the Java packages from `com.quantum.simulator.*` to `com.mechalikh.qforge.*`. The library
  now sits in `src/com/mechalikh/qforge/` and the tests in `src/test/java/com/mechalikh/qforge/`,
  with all 13 subpackages kept as-is, so `core`, `state`, `noise`, `strategy` and the rest move
  under the new prefix. The `example` demo packages are unchanged. Imports in your own code and in
  the guides must use the new prefix; the Maven coordinates did not change again.

### Fixed

- `PriorityResourceScheduler` now reports the task whose future actually completed, instead of the
  first task in the map that had finished, so the completion order names are correct.

## [1.0.0] - 2026-10-25

First stable release. The event-driven engine, both backends, the gate set, measurement with state
collapse, the Kraus noise channels, the JSON loader, the plugin API, and the ten algorithm demos are
all implemented and covered by the test suite.

### Added

- Event-driven circuit execution with `STATE_VECTOR` and `DENSITY_MATRIX` backends, timed
  `QuantumEvent` scheduling, and priority-ordered dispatch.
- Gates: `H`, `X`, `Y`, `Z`, `S`, `T`, `RX`, `RY`, `RZ`, `P`, `CNOT`, `CZ`, `CPHASE`, `SWAP`,
  `Toffoli`, multi-controlled `Z`, and controlled `SWAP`, plus arbitrary local unitaries through
  `MultiQubitGate` and custom gate factories.
- Projective measurement with state collapse, general Hermitian observables, outcome eigenvalues,
  expectation values, variance, standard error, and mid-circuit measurement.
- Depolarizing, dephasing, and amplitude-damping noise with target-aware Kraus operators,
  deterministic application on the density matrix, and quantum trajectories on the state vector.
- JSON configuration and circuit loading with unknown-field tolerance, comments, validation, memory
  estimation, and round-trip saving.
- Observer callbacks, metrics collection, Java2D visualization, and animated GIF output.
- Plugin API with lifecycle hooks and JAR loading.
- Quantum algorithms: Grover search, QFT and phase estimation, VQE, QAOA circuits, Trotterized
  evolution, Shor error-correction encoding, continuous and discrete quantum walks, a small QNN, and
  a swap-test quantum kernel classifier.
- JUnit 5 test suite with 42 tests covering gates, measurement, noise, algorithms, JSON, metrics,
  plugins, and VQE convergence.
- Javadoc for every public type, constructor, method, field, and enum constant in the simulator
  packages and the example programs, generated by `mvn javadoc:javadoc`.
- A `docs/` guide set covering architecture, configuration, gates, measurement and noise, algorithms,
  extension points, and development workflow.

### Changed

- Renamed the project from QuantSim to QuantLab. The Maven coordinates became
  `com.quantlab:quantlab`, and the Eclipse project name and README used QuantLab. The Java packages
  remained `com.quantum.simulator`.
- Removed the `TENSOR_NETWORK`, `STABILIZER`, and `PATH_INTEGRAL` backend enum entries because they
  had no implementation. Only the two supported backends are advertised.
- Replaced placeholder fidelity and trace-distance calculations with values derived from the density
  matrices.
- Added `.gitignore` entries for build output, IDE metadata, and local agent files.

### Known gaps

Documented in the guides rather than hidden:

- `SimulationConfig.timeoutSeconds` is recorded and serialized but never enforced by the engine.
- The three default `ClassicalController` rules are placeholders, and `updateEventQueue` and
  `executeConditional` have no engine caller.
- The Shor demo performs encoding only; there is no syndrome extraction or recovery loop.
- `example.algorithms.Graph` is package-private, so custom graphs cannot be supplied from outside
  that package.
- No OpenQASM import or export.
- The `commons-math3`, `slf4j`, and `annotations` Maven dependencies are declared but unused.
