# Quantum teleportation

Teleport an arbitrary single-qubit state from one place to another using a shared Bell pair and two
classical bits. No qubit travels: only a measurement outcome is sent, yet the exact state reappears
on the receiver's qubit.

## What it does

`QuantumTeleportation` prepares a payload `Ry(pi/3)|0>` on qubit 0, entangles qubits 1 and 2 into a
Bell pair, and applies Alice's `CNOT(0 -> 1)` and `H(0)`. That leaves the three qubits in a
superposition of four joint outcomes on Alice's two qubits. For each outcome the state of Bob's qubit
differs from the payload by a fixed Pauli correction, so the demo splits the exact final state into
the four branches, applies the correction, and measures how well Bob's qubit matches the payload.

This is the standard teleportation protocol. The one thing to know about this particular demo is
*why* it analyses branches after a single run: the engine does not yet wire mid-circuit measurement
into conditional gates (see `docs/08-development.md`), so the correction is applied to each branch
state in code rather than by a feed-forward rule during the run. The physics is exact either way.

## Circuit

```mermaid
flowchart LR
    P["payload Ry(pi/3)|0> on q0"] --> B["Bell pair: H(q1), CNOT(q1->q2)"]
    B --> A["Alice: CNOT(q0->q1), H(q0)"]
    A --> M["joint outcome (a,b) of q0,q1"]
    M --> C["correction on q2:<br/>X if b, Z if a"]
    C --> R["q2 holds the payload"]
```

The correction Bob applies depends on Alice's two measured bits:

| (q0, q1) = (a, b) | Correction on q2 |
| --- | --- |
| (0, 0) | identity |
| (0, 1) | X |
| (1, 0) | Z |
| (1, 1) | Z then X |

## Run it

From the repository root:

```bash
mvn -q -DskipTests compile
java -cp target/classes example.algorithms.teleportation.QuantumTeleportation
```

## Expected results

Captured from a run on JDK 17. The four outcomes are equiprobable, the uncorrected state only matches
by accident in the (0,0) branch, and every corrected branch is exact:

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

## Notes

- **Qubit ordering.** Qubit 0 is the most significant bit of the basis-state index, so a branch
  `(a, b)` reads amplitudes at index `(a << 2) | (b << 1) | bob`.
- **Deterministic analysis.** The demo does not sample measurements. It reads the exact state vector
  and conditions on each outcome analytically, so the fidelities are exact rather than shot-noisy.
- **Maximally mixed marginal.** Bob's qubit on its own has `P(0)=P(1)=0.5`. The payload information
  lives in the correlation between his qubit and Alice's classical bits, not in his qubit alone.
- **Backend.** Three qubits on the `STATE_VECTOR` backend.
