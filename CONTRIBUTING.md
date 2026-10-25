# Contributing to QuantLab

QuantLab is a teaching and research simulator, so contributions are judged on correctness,
clarity, and honesty before speed. Small, well-tested changes with matching documentation are the
easiest to review.

## Before you start

- Use JDK 17 and Maven 3.8 or newer.
- Run `mvn -o test`; the suite must stay green.
- Read [docs/08-development.md](docs/08-development.md) for the repository layout, coding
  conventions, coverage commands, and release process.
- Open an issue first for anything larger than a bug fix.

## Roadmap

Checked items are implemented. Unchecked items are open. If you take on a large item, split it into
smaller issues and land them one at a time.

### Implemented

- [x] Event-driven execution with `STATE_VECTOR` and `DENSITY_MATRIX` backends
- [x] Gate set with control-first matrix ordering, including Toffoli, multi-controlled `Z`, and
  controlled `SWAP`
- [x] Projective measurement with state collapse, observables, expectation values, and variance
- [x] Depolarizing, dephasing, and amplitude-damping noise with Kraus channels and JSON profiles
- [x] JSON configuration and circuit loading
- [x] Observer callbacks, metrics, and Java2D visualization
- [x] Plugin API with lifecycle hooks
- [x] Ten quantum algorithm demos with pass criteria
- [x] Javadoc for every public type and numbered guides `01` to `08`
- [x] 272-test JUnit 5 suite with 91.8 percent instruction and 84.6 percent branch coverage
- [x] JaCoCo coverage check that fails `mvn verify` below 70 percent instruction coverage
- [x] Travis CI and Codacy configuration files, plus `CITATION.cff` and repository metadata

### Planned

**Release and infrastructure**

- [ ] Add a `LICENSE` file, since no license is granted yet
- [ ] Activate the repository on travis-ci.com and replace the `unknown` badge
- [ ] Import the repository into Codacy and add the tokenized grade badge
- [ ] Publish `com.quantlab:quantlab` to Maven Central or JitPack

**Interoperability**

- [ ] Import and export OpenQASM 2 and 3
- [ ] Add a Qiskit JSON adapter

**Scale**

- [ ] Add a stabilizer (Clifford) backend for thousands of qubits
- [ ] Add a tensor-network or matrix-product-state backend for low-entanglement circuits

**Noise and mitigation**

- [ ] Add time-based noise: T1 and T2 relaxation, idle noise, crosstalk, and readout error
- [ ] Add error mitigation: zero-noise extrapolation and readout mitigation
- [ ] Make quantum trajectories reproducible with recorded seeds

**Teaching and tooling**

- [ ] Add notebook integration
- [ ] Add a step-through execution mode
- [ ] Add an interactive visualization UI

**Verification and performance**

- [ ] Cross-validate outputs against Qiskit reference results
- [ ] Add property-based and randomized circuit tests
- [ ] Add JMH performance benchmarks to CI

**Protocol demos**

- [ ] Add BB84 and E91 protocol demos with eavesdropper models

**Known gaps**

- [ ] Enforce `timeoutSeconds`; it is stored and validated but never applied
- [ ] Wire the default `ControlRule` implementations into the engine
- [ ] Dispatch the declared progress, checkpoint, pause, resume, and resource observer callbacks
- [ ] Expose the quantum-walk `Graph` API to external code
- [ ] Bundle circuit JSON for the `grover`, `qft`, and `teleport` names in `CircuitRunner`

## What makes a good change

- Add a JUnit 5 test for every behavior change. Tests drive the real dispatch path; mocks would not
  catch a broken pipeline.
- Keep instruction coverage above 70 percent. `mvn verify` enforces the threshold.
- Update the Javadoc when a public type, method, field, or failure mode changes.
- Update the README, the relevant guide under `docs/`, and `CHANGELOG.md` when behavior changes.
- Keep the documentation honest: if a feature is unimplemented, say so instead of implying support.
- Do not add a dependency unless the standard library cannot cover the need.

## Pull request checklist

- [ ] `mvn -o test` passes
- [ ] `mvn -o verify` passes, including the coverage gate
- [ ] New behavior has tests
- [ ] Public Javadoc is updated
- [ ] README, guides, and `CHANGELOG.md` are updated where needed
- [ ] The change is focused and does not mix unrelated refactors
