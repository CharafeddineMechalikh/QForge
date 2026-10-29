# Quantum algorithm examples

The builders in this package assemble circuits and return them as arrays or lists of `QuantumEvent`;
none of them schedules or runs a simulation. `example.QuantumAlgorithmsDemo` is the main runner: it
builds the qubits, schedules the events on a `QuantumSimulator`, runs the circuit, and prints the
final state with a `SUCCESS` or `FAILED` verdict where it can check one. Four further demos have
their own `main` methods, because they report measured tables or a trained model rather than one
banner.

Five algorithms have their own directory and README with a deeper analysis, because they either
need more circuit structure or deserve a distribution plot:

- [oracle/README.md](oracle/README.md): Deutsch, Deutsch-Jozsa, Bernstein-Vazirani
- [teleportation/README.md](teleportation/README.md): all four Bell-measurement branches
- [bb84/README.md](bb84/README.md): key distribution with an eavesdropper
- [noise/README.md](noise/README.md): depolarizing fidelity sweep
- [shor/README.md](shor/README.md): order finding that factors 15

## Run the demos

```bash
mvn -q -DskipTests compile
java -cp "target/classes;lib/*" example.QuantumAlgorithmsDemo ALL
java -cp "target/classes;lib/*" example.QuantumAlgorithmsDemo VQE
```

On Linux and macOS use `:` instead of `;`. The optional first argument is case insensitive:
`GROVER`, `QFT`, `QPE`, `VQE`, `QNN`, `QSVM`, `SHOR_CODE`, `QAOA`, `TROTTER`, `QUANTUM_WALK`, or
`ALL`.

The standalone demos need the Maven dependency classpath because two of them use the
commons-math3 optimizer:

```bash
mvn -B -q dependency:build-classpath -Dmdep.outputFile=target/classpath.txt
CP="target/classes;$(cat target/classpath.txt)"
java -cp "$CP" example.algorithms.QaoaMaxCut
java -cp "$CP" example.algorithms.QuantumArithmeticComplete
java -cp "$CP" example.algorithms.AmplitudeAmplificationComplete
java -cp "$CP" example.algorithms.QuantumClassifierDemo
```

```mermaid
flowchart LR
    B["builder classes<br/>in this package"] --> E["QuantumEvent arrays"]
    C["CircuitFactory<br/>QFT and small circuits"] --> E
    E --> D["QuantumAlgorithmsDemo"]
    D --> S["QuantumSimulator"]
    S --> P["probabilities, energies, verdicts"]
```

## Captured results

Fresh output from `QuantumAlgorithmsDemo ALL` on JDK 17.

| Selector | Builder | Measured result |
| --- | --- | --- |
| `GROVER` | `GroversAlgorithm` | `\|111>` probability 0.9453 after 2 iterations |
| `QFT` | `CircuitFactory` (top-level package) | all eight probabilities 0.1250 |
| `QPE` | `QuantumPhaseEstimation` | phase 0.125 with probability 1.000 |
| `VQE` | `VQEAlgorithm` and `VariationalQuantumEigensolver` | -1.879702 Hartree, error 1.94e-07, 267 iterations |
| `QNN` | `QuantumNeuralNetwork` | 100 percent training accuracy after 60 epochs |
| `QSVM` | `QuantumSVM` | training and test accuracy 100 percent, kernel perceptron 1 epoch |
| `SHOR_CODE` | `QuantumErrorCorrection.createShorCode` | probability mass 1.000000 on the eight codewords |
| `QAOA` | `QAOAAlgorithm` | expected cut 0.5258 on a triangle whose maximum is 2 (fixed angles) |
| `TROTTER` | `TrotterSimulation` | fidelity 1.00000000 against the exact exponential |
| `QUANTUM_WALK` | `QuantumWalk` | probability 0.3245 and 0.3328 on the two neighbours |

`QAOA` is the one selector without a `SUCCESS` line. The demo uses fixed angles (`p = 1`,
`gamma = 0.5`, `beta = 0.3`) that are not optimized, so the largest probabilities sit on the
zero-cut states `|000>` and `|111>`; the printed expected cut size is 0.5258 against a maximum of 2.
The demo prints that note itself. Treat it as an unoptimized example of the circuit, not as evidence
that QAOA finds the maximum cut.

## Standalone demos

| Demo | What it measures | Captured result |
| --- | --- | --- |
| `QaoaMaxCut` | QAOA angles optimized by Nelder-Mead on a five-node weighted graph, exact cut 4.5 | depth 1 expected cut 3.272756 (ratio 0.727279), depth 2 3.835804 (ratio 0.852401); 714/2000 and 435/2000 sampled shots reach the optimum |
| `QuantumArithmeticComplete` | In-place adder, modular multiplier and modular exponentiation, every input pair | adder 16/16 and 64/64 cases, multiplier 49/49 and 225/225, exponentiation 49/49 and 225/225, all with probability 1.000000 |
| `AmplitudeAmplificationComplete` | Grover amplification for `k = 1, 2, 3` marked items in `N = 16` | success 0.961318970, 0.945312500, 0.949218750, matching `sin^2((2m+1)theta)` to at most 4.77e-15 |
| `QuantumClassifierDemo` | Two-qubit variational classifier on thirty Iris flowers, fixed hold-out | training accuracy 0.8000 (16/20), test accuracy 0.8000 (8/10), training MSE 0.158525 |

The QAOA depth-two run has the higher expected cut but a lower fraction of sampled optimal shots,
and its most frequent outcome has cut 4.0. The classifier does not reach 100 percent on the held-out
flowers and says so in its output. Both negative details are kept in the READMEs on purpose.

The arithmetic demo is verified exhaustively for the widths it claims: the adder uses `3n + 1`
qubits, the multiplier and exponentiation use `2n` qubits for a modulus of `n` bits, and the
exponentiation is one dense `2^(2n) x 2^(2n)` permutation, so no modulus above 15 is claimed.

## What each builder does

- **`GroversAlgorithm`** (`createGroversCircuit`): Hadamards, then
  `max(1, floor(pi/4 * sqrt(N)))` iterations of a phase oracle and a diffusion operator. The oracle
  flips the marked state with X gates around a multi-controlled Z.
- **`QuantumPhaseEstimation`** (`createPhaseEstimationCircuit`): Hadamards on the estimation
  register, controlled powers `U^(2^(t-1-j))`, then an inverse QFT. Controlled powers are formed by
  matrix exponentiation and are limited to 10 qubits. `createInverseQFT` is public and reusable.
- **`QuantumNeuralNetwork`** (`createQNNCircuit`): data encoding plus variational rotation layers.
  The demo trains it with finite-difference gradients on four samples.
- **`QuantumSVM`**: a ZZ feature map, a swap-test kernel, and a kernel perceptron. The kernel matrix
  in the captured run is symmetric with unit diagonal, as a kernel must be.
- **`QuantumErrorCorrection`** (`createBitFlipCode`, `createPhaseFlipCode`, `createShorCode`):
  encoding circuits only. Syndrome extraction, decoding, and recovery are not included, except that
  the phase-flip helper applies Hadamards as part of its representation.
- **`QAOAAlgorithm`**: one layer per entry of `gamma`, with a CNOT, RZ, CNOT problem unitary per
  edge and an RX mixer per qubit. The caller supplies the angles; nothing is optimized.
- **`TrotterSimulation`**: first-order Trotter steps for a Heisenberg `XX + YY + ZZ` Hamiltonian.
  The demo compares the result with the exact matrix exponential.
- **`QuantumWalk`**: a continuous-time walk on a small line graph, using a package-private graph
  type. The demo starts in the middle and checks that both neighbours receive probability.
- **`VQEAlgorithm`**: the ansatz builder behind the variational eigensolver demo. The optimizer
  itself lives in `com.mechalikh.qforge.classical`.
- **`QaoaMaxCut`**: the optimized QAOA builder. It returns the circuit for a supplied angle vector,
  and its `optimize(depth, initialAngles)` method drives the Nelder-Mead loop over the exact cut
  expectation.
- **`QuantumArithmeticComplete`**: `createInPlaceAdder`, `createControlledMultiplier` and
  `createModularExponentiation`, each returning a `QuantumEvent[]` that the demo runs exhaustively.
- **`AmplitudeAmplificationComplete`**: per-item marking oracles, the diffusion operator and
  `optimalIterations(numQubits, markedCount)`, with the measured success probability returned by
  `amplify(...)`.
- **`QuantumClassifierDemo`**: angle encoding, three `RY` plus CNOT layers, and a Nelder-Mead
  training loop over a fixed Iris split. The measured accuracies are returned by
  `trainAndEvaluate()`.

## Older builders without a demo

Two older classes remain as reference sketches and are not exercised by any runner:

- **`QuantumAmplitudeAmplification.amplify(...)`** appends oracle and diffusion repetitions to a
  caller-supplied initialization circuit. Its multi-controlled Z inside the diffusion step is a
  simplified decomposition (CZ for two qubits, a short CNOT/RZ sequence for three, a CNOT cascade
  above that). `AmplitudeAmplificationComplete` is the independently implemented, measured
  replacement; this class is kept for callers that already use its API.
- **`QuantumArithmetic.createQuantumAdder(...)`** emits the carry and sum CNOTs of a partial
  ripple-carry adder and omits the remaining carry logic. `createQuantumMultiplier(...)` is a
  placeholder that returns an empty circuit. `QuantumArithmeticComplete` is the verified
  replacement and does not call this class.

Neither older class is claimed to be correct for arbitrary inputs. The complete implementations are
the ones covered by the measured output above and by `AlgorithmValidationTest`.

## Notes

- **No simulation in the builders.** They only create events, so they can be unit tested and reused
  with any backend. Running is always the caller's job.
- **Qubit ordering.** Qubit 0 is the most significant bit of the basis-state index throughout the
  demos.
- **Simplified pieces are labelled.** The package documentation and the class Javadoc call out the
  simplified decompositions in amplitude amplification and arithmetic, and the missing decoding in
  the error-correction encoders.
