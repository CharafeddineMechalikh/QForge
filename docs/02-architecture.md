# Architecture

QuantLab is an event-driven simulator. Circuits are not compiled into a static instruction list;
they are collections of timed `QuantumEvent` objects that a dispatcher drains in order, applying
one operation to one state object at a time.

## Package map

```mermaid
flowchart TD
    subgraph api["Public entry points"]
        CORE["core<br/>QuantumSimulator, SimulationContext,<br/>SimulationResult, SimulationError"]
        CFG["config<br/>SimulationConfig, SimulationBackend,<br/>TimeResolution, ResourceConstraints"]
    end

    subgraph model["Circuit model"]
        EVT["events<br/>QuantumEvent, EventQueue, EventDispatcher,<br/>EventPriority, EventType"]
        OPS["operations<br/>QuantumGate, SingleQubitGate,<br/>MultiQubitGate, GateLibrary"]
        MEAS["measurement<br/>Observable, MeasurementOperator,<br/>MeasurementResult, MeasurementHelper"]
        NOI["noise<br/>NoiseModel, NoiseProfile, channels,<br/>ErrorManager"]
    end

    subgraph exec["Execution"]
        STR["strategy<br/>SimulationStrategy,<br/>StateVectorStrategy, DensityMatrixStrategy"]
        ST["state<br/>QuantumState, StateVector,<br/>DensityMatrix, ComplexVector, Qubit"]
        MATH["math<br/>Complex, ComplexMatrix,<br/>MatrixOperations, RandomGenerator"]
    end

    subgraph obs["Observability and extension"]
        OB["observer<br/>SimulationObserver, MetricsCollector,<br/>VisualizationEngine"]
        PL["plugin<br/>Plugin, PluginManager, PluginContext"]
        CL["classical<br/>ClassicalController, ControlDecision,<br/>VQE, ParameterSpace, HybridAlgorithm"]
        RS["resources<br/>ResourceManager, ResourceRequest,<br/>ResourceAllocation, ResourceStatistics"]
    end

    CORE --> CFG
    CORE --> EVT
    CORE --> STR
    CORE --> OB
    CORE --> PL
    CORE --> CL
    CORE --> RS
    EVT --> OPS
    EVT --> MEAS
    EVT --> NOI
    STR --> ST
    ST --> MATH
    OPS --> ST
    MEAS --> ST
    NOI --> ST
    CL --> EVT
    OB --> CORE
    PL --> CORE
```

| Package | Responsibility |
| --- | --- |
| `core` | Facade, run loop, context state, results, checked errors |
| `config` | Simulation parameters, backend choice, time unit, resource limits, JSON I/O |
| `events` | Timed event objects, the priority queue, the dispatcher |
| `operations` | Gate abstraction, standard gates, gate registry |
| `state` | State representations, qubit metadata, tensor and partial-trace math |
| `strategy` | Backend-specific application of gates, noise and measurement |
| `measurement` | Observables, sampling, projective collapse, statistics |
| `noise` | Kraus channels, per-qubit and global noise profiles, error bookkeeping |
| `observer` | Callback interface, metrics collection, Java2D visualization |
| `plugin` | Plugin contract, registry, JAR and directory loading |
| `classical` | Measurement-driven feed-forward control and variational loops |
| `resources` | Allocation tracking against the configured limits |
| `math` | Complex arithmetic, matrices, eigen-decomposition helpers, RNG |

## Simulator lifecycle

```mermaid
stateDiagram-v2
    [*] --> Uninitialized
    Uninitialized --> Ready: initialize(config)
    Ready --> Ready: scheduleEvent(event)
    Ready --> Running: run()
    Ready --> Running: step()
    Running --> Completed: queue empty or stop condition
    Running --> Running: classical controller schedules events
    Completed --> Ready: scheduleEvent(event)
    Completed --> Reset: reset()
    Reset --> Ready: fresh state, queue cleared
    Running --> Stopped: stop()
    Stopped --> Completed: run() returns the partial result
    Ready --> Closed: shutdown()
    Completed --> Closed: shutdown()
    Closed --> Ready: initialize(config) again
```

The stages map to concrete work:

1. `initialize(SimulationConfig)` validates the configuration, builds a `SimulationContext`, an
   `EventDispatcher`, a `ResourceManager`, a `MetricsCollector`, the strategy for the chosen
   backend, and a `ClassicalController` when classical control is enabled. It then creates the
   all-zero state and notifies observers and plugins.
2. `scheduleEvent(QuantumEvent)` pushes the event onto the queue and reports it to the metrics
   collector, the plugins and the observers.
3. `run()` dispatches one event per iteration until the queue is empty or a stop condition holds,
   then packages state, measurements, metrics and wall-clock runtime into a `SimulationResult`.
4. `shutdown()` unloads every plugin and releases the tracked allocations. The context survives, so
   the simulator can be re-initialized.

`step()` dispatches exactly one event and skips the start and end callbacks, the stop checks and
resource release. It exists for interactive or debug driving of the queue.

## Event ordering

The queue is ordered, not merely first-in-first-out. `QuantumEvent.compareTo` applies a
three-level cascade.

```mermaid
flowchart TD
    A["Two events compete"] --> B{"Scheduled time differs?"}
    B -- yes --> B1["Earlier time first"]
    B -- no --> C{"Priority differs?"}
    C -- yes --> C1["Higher priority first<br/>HIGHEST beats LOWEST"]
    C -- no --> D["Lower construction sequence first<br/>creation order breaks the tie"]
```

`EventPriority` runs `HIGHEST(0)`, `HIGH(1)`, `NORMAL(2)`, `LOW(3)`, `LOWEST(4)`, and a smaller
value wins. When a circuit is loaded from JSON by `CircuitRunner`, every gate is stamped 10
milliseconds after the previous one, so scheduling order and time order agree. When you build
events yourself with the same `Instant`, insertion order is what decides, which is why the
sequence counter matters.

Duplicate ids are rejected by the queue with `IllegalArgumentException`, and an event that was
already executed throws `IllegalStateException` if executed again.

## One dispatch, end to end

```mermaid
sequenceDiagram
    participant SIM as QuantumSimulator
    participant DIS as EventDispatcher
    participant QUE as EventQueue
    participant EVT as QuantumEvent
    participant CTX as SimulationContext
    participant STR as SimulationStrategy
    participant OBS as Observers and plugins

    SIM->>DIS: dispatchNextEvent(context)
    DIS->>QUE: getNextEvent()
    QUE-->>DIS: head event
    DIS->>EVT: execute(context)
    EVT->>CTX: applyOperation(operation)
    alt gate
        CTX->>STR: applyGate(state, gate, targets)
        STR-->>CTX: new state
        CTX->>STR: applyNoise for each profile entry
    else noise model
        CTX->>STR: applyNoise(state, model, targets)
    else measurement operator
        CTX->>CTX: measureAndCollapse(state)
    end
    CTX->>CTX: updateState(newState), stepsExecuted++
    CTX-->>DIS: done
    DIS->>OBS: onStateUpdate, onMeasurement
    SIM->>SIM: classical controller processes measurements
    SIM->>SIM: check shouldStop()
```

Three details of `SimulationContext.applyOperation` matter when you read results:

- Configured noise from `SimulationConfig.getNoiseProfile()` is applied after every gate. It is
  not applied after a noise event or after a measurement, so manual `NoiseModel` events are not
  double-noised.
- A global profile entry with no assigned qubits is expanded to one single-qubit application per
  qubit in the register, in qubit index order.
- Each step writes a fresh state object through `updateState`, records the step count as the
  `steps_executed` metric, and increments `operation_<name>_count`.

## Stop conditions

| Condition | Where it is checked | Effect |
| --- | --- | --- |
| Queue empty | `run()` loop guard | Normal completion |
| `context.requestStop()` | `SimulationContext.shouldStop()` after each event | Loop exits, partial result returned |
| `maxSteps` positive and reached | `SimulationContext.shouldStop()` | Loop exits at the step limit; `0` means unlimited |
| `stop()` called | `run()` loop guard on the running flag | Loop exits after the current event |
| `timeoutSeconds` | nowhere | Recorded and serialized, but not enforced |

The timeout field is validated and written to JSON, and no code path aborts a run on it. Treat it
as metadata until it is wired up.

## State representations

```mermaid
classDiagram
    class QuantumState {
        <<abstract>>
        #int numQubits
        #List~Qubit~ qubits
        +getProbabilities() double[]
        +applyKrausOperators(ComplexMatrix[], int[]) QuantumState
        +getPurity() double
        +copy() QuantumState
    }
    class StateVector {
        +createZeroState(int) StateVector
        +getAmplitudes() Complex[]
        +applyGate(QuantumGate) StateVector
        +collapseToBasisState(int[], int) QuantumState
        +toDensityMatrix() DensityMatrix
    }
    class DensityMatrix {
        +createZeroState(int) DensityMatrix
        +createMaximallyMixedState(int) DensityMatrix
        +fromStateVector(StateVector) DensityMatrix
        +getMatrix() ComplexMatrix
        +getVonNeumannEntropy() double
        +fidelity(DensityMatrix, DensityMatrix) double$
        +traceDistance(DensityMatrix, DensityMatrix) double$
    }
    QuantumState <|-- StateVector
    QuantumState <|-- DensityMatrix
```

Qubit `0` is the most significant bit of the basis index. With three qubits, index `5` is `|101>`,
meaning qubit 0 in state 1, qubit 1 in state 0, qubit 2 in state 1. Amplitude arrays are ordered by
that index, so `getProbabilities()[i]` is the probability of the basis string of `i`.

### Backend comparison

| Property | `STATE_VECTOR` | `DENSITY_MATRIX` |
| --- | --- | --- |
| Storage | `2^n` complex amplitudes | `2^n x 2^n` complex elements |
| Memory estimate in `validate()` | 16 bytes per amplitude plus 1 KiB | 16 bytes per element plus 2 KiB |
| Recommended qubit ceiling | 30 (about 16 GiB) | 12 (about 256 MiB) |
| Pure states | Yes | Yes |
| Mixed states | No | Yes |
| Noise semantics | One Kraus operator sampled per application, then renormalized, so one stochastic trajectory | Full `sum K rho K dagger`, deterministic |
| Purity and entropy | Trivial purity of 1, von Neumann entropy of a pure state | Real values, used by metrics and plots |
| Backed by | `StateVectorStrategy` | `DensityMatrixStrategy` |

`SimulationConfig.validate()` rejects any configuration whose qubit count exceeds the backend
recommendation, so the ceilings are enforced at initialization rather than at allocation time.

## Threading and error model

- The run loop is single-threaded. `isRunning` is an `AtomicBoolean` so another thread can call
  `stop()`, but nothing else in the loop is synchronized and the classes make no thread-safety
  promise.
- Failures surface as the checked `SimulationError`, which always wraps the original cause.
  Argument problems raise unchecked `IllegalArgumentException` or `NullPointerException` instead.
- `QuantumEvent.EventExecutionException` wraps an operation failure inside the dispatcher; the
  dispatcher rethrows it while exception propagation is enabled, which is the default.

## Related guides

- [Configuration](03-configuration.md) for what `validate()` accepts.
- [Measurement and noise](05-measurement-and-noise.md) for collapse and trajectory semantics.
- [Extending QuantLab](07-extending.md) for plugins, observers and classical control.
