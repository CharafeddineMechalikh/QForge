# Quantum teleportation

Teleport an arbitrary single-qubit state from one place to another using a shared Bell pair and two
classical bits. No qubit travels: only a measurement outcome is sent, yet the exact state reappears
on the receiver's qubit.

This directory holds two demos of the same protocol:

| Demo | Execution model | What it shows |
| --- | --- | --- |
| `FeedForwardTeleportation` | 200 full shots through `ShotRunner`, with mid-circuit measurements and `ConditionalOperation` corrections | the engine really applies Bob's correction from Alice's live register inside each shot |
| `QuantumTeleportation` | one ideal run, then exact branch analysis | the state-vector algebra behind the protocol, branch by branch |

## Circuit

```mermaid
flowchart LR
    P["payload Ry(pi/3)|0> on q0"] --> B["Bell pair: H(q1), CNOT(q1->q2)"]
    B --> A["Alice: CNOT(q0->q1), H(q0)"]
    A --> M["measure q0 -> bit 0, q1 -> bit 1 of register alice"]
    M --> C["conditional X on q2 if bit 1, conditional Z if bit 0"]
    C --> R["q2 holds the payload"]
```

The correction depends on Alice's two bits:

| Alice's bits | Correction on Bob's qubit |
| --- | --- |
| 0, 0 | identity |
| 0, 1 | X |
| 1, 0 | Z |
| 1, 1 | Z then X |

## Feed-forward path

`FeedForwardTeleportation` declares a two-bit classical register `alice`, measures `q0` into bit 0
and `q1` into bit 1, then schedules two `ConditionalOperation` gates on Bob's qubit. The first tests
bit 1 and applies `X`; the second tests bit 0 and applies `Z`. Each shot reinitializes the simulator,
builds fresh events and runs the whole circuit, so the conditional events read the register values
produced earlier in that same shot.

The measured output, with `SEED = 20261029` and `SHOTS = 200`:

```text
=== Feed-Forward Quantum Teleportation ===
Payload: Ry(theta)|0> with theta = pi/3 -> [0.866025, 0.500000]

Alice's classical register over 200 shots:
  alice=0  count= 58  frequency=0.2900
  alice=1  count= 48  frequency=0.2400
  alice=2  count= 55  frequency=0.2750
  alice=3  count= 39  frequency=0.1950

With feed-forward:    shots=200  success=1.0000  mean fidelity=1.000000  min=1.000000
Without corrections:  shots=200  success=0.2400  mean fidelity=0.483750  min=0.000000
SUCCESS: every shot recovered the payload through conditional corrections
```

The four register values are roughly equiprobable, as expected for a Bell pair. With corrections
every one of the 200 shots reaches fidelity 1, including the worst shot (`min=1.000000`). Without
corrections only the branch that needs no correction survives, and the measured success rate of
0.2400 is close to the analytic 0.25 for this seeded run. The mean fidelity without corrections,
0.48375, is close to the analytic `(1 + 0.75 + 0.25 + 0) / 4 = 0.5`.

## Branch-analysis path

`QuantumTeleportation` runs the circuit once without measurement, splits the exact final state into
the four joint outcomes of Alice's qubits, applies the matching correction to each branch, and
reports the fidelity against the payload.

```text
=== Quantum Teleportation ===
Payload: Ry(theta)|0> with theta = pi/3 -> [0.866025, 0.500000]
Bob's qubit before correction (marginal): P(0)=0.5000 P(1)=0.5000 (maximally mixed)

Branch analysis (qubit0, qubit1 -> correction -> fidelity):
  (0,0) P=0.2500  correction=I           uncorrected F=1.0000  corrected F=1.000000
  (0,1) P=0.2500  correction=X           uncorrected F=0.7500  corrected F=1.000000
  (1,0) P=0.2500  correction=Z           uncorrected F=0.2500  corrected F=1.000000
  (1,1) P=0.2500  correction=Z then X    uncorrected F=0.0000  corrected F=1.000000
SUCCESS: every measurement branch teleports the payload with fidelity 1
```

The `uncorrected F` column is the teaching point: without the two classical bits and the matching
Pauli, the recovered state is wrong in three of the four branches. The `corrected F` column is 1 in
all four, which is the teleportation guarantee.

## Run it

From the repository root:

```bash
mvn -q -DskipTests compile
java -cp target/classes example.algorithms.teleportation.FeedForwardTeleportation
java -cp target/classes example.algorithms.teleportation.QuantumTeleportation
```

No extra dependency classpath is needed; these demos use only the library and `EventBuilderHelper`.

## Notes and limits

- **Qubit ordering.** Qubit 0 is the most significant bit of the basis-state index, so a branch
  `(a, b)` in the branch analysis reads amplitudes at index `(a << 2) | (b << 1) | bob`.
- **Register packing.** In the feed-forward demo `q0` writes bit 0 and `q1` writes bit 1, so
  `alice = 2 * q1 + q0`. The `X` condition tests value 2 and the `Z` condition tests value 1.
- **Seeding.** `RandomGenerator.setRandom(new Random(SEED))` is called before each measured phase,
  so the printed counts are reproducible.
- **Fidelity measurement.** The feed-forward demo sums the final amplitudes of Bob's qubit after
  Alice's measurements collapse her qubits, then computes the squared overlap with the payload.
- **Scope.** Three qubits on the `STATE_VECTOR` backend, one payload angle, no noise. With a device
  noise model attached the fidelities would drop, which the demo does not claim.
