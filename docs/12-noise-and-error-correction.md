---
title: "Noise and error correction"
nav: "Noise and error correction"
order: 12
group: background
description: "Decoherence times T1 and T2, Kraus channels, density matrices versus trajectories, the three-qubit repetition code, the Shor code and the threshold idea."
---

# Noise and error correction

An ideal circuit keeps its amplitudes forever. A real qubit does not. Coupling to the environment
drains energy, randomizes phase, and corrupts readout. This page explains the two clocks that
measure that decay, how QForge models the damage, and why a quantum error-correcting code can fix
errors without ever measuring the data it protects.

## Two clocks: T1 and T2

Two time constants summarize most single-qubit decoherence:

- **T1, energy relaxation.** A qubit in `|1>` can lose its excitation and fall to `|0>`. T1 is the
  time constant of that decay.
- **T2, phase coherence.** A superposition can lose the phase relationship between its two
  amplitudes even while the energy stays put. T2 is the time constant of that dephasing.

<figure>
  <img src="{{ '/assets/img/decoherence.svg' | relative_url }}" alt="Two Bloch spheres. On the left the state vector shrinks toward the north pole as energy relaxes. On the right the vector shrinks toward the centre as phase coherence is lost." loading="lazy">
  <figcaption>Energy relaxation moves the state toward the |0⟩ pole and shrinks it. Pure dephasing
  keeps
  the populations but shortens the vector toward the centre of the sphere.</figcaption>
</figure>

The two are linked. A qubit with only energy relaxation already loses phase, so T2 cannot exceed
`2 T1`. The purely dephasing part is what remains:

```text
1 / T_phi = 1 / T2 - 1 / (2 T1)
```

QForge follows the same relation in `ThermalRelaxationModel`. A model with `T2 = T1` adds no phase
noise beyond amplitude damping, and `T2 = 2 T1` is the physical limit where `T_phi` is infinite.

Coherence times depend on the platform. Superconducting transmons reach tens to hundreds of
microseconds, trapped ions and neutral atoms reach seconds. A useful gate must finish in much less
than T1 and T2, which is why fast gates and long coherence pull in opposite directions. The values
in the platform table in [Qubits, photons and polarization](09-qubits-and-photons.md#how-to-make-a-qubit)
are the ones that decide how noisy a circuit can be.

There are two ways to accumulate error in QForge. A `NoiseProfile` applies channels after every
gate, so error scales with gate count. A `DeviceNoiseModel` applies relaxation between events using
the timestamps, so idle time matters as well. Use the first for a simple gate-error model and the
second when you care how long a qubit sat waiting.

## How errors enter the math

A noisy operation is not a single unitary. The qubit becomes entangled with an environment that is
not tracked, and the state that remains is described by a density matrix `rho`. The action of a
channel is written with Kraus operators:

```text
rho  ->  sum over i of  K_i rho K_i-dagger
```

Each `K_i` is a possible error and the sum is over all of them, which is why the process is
deterministic on a density matrix. On a state vector, the simulator instead picks one `K_i` with
probability proportional to `||K_i psi||^2`, renormalizes, and records one trajectory. Many
trajectories approach the density-matrix answer.

| Error | What it does | In QForge |
| --- | --- | --- |
| Bit flip | applies X with some probability | `DepolarizingNoise`, or a custom `NoiseModel` |
| Phase flip | applies Z, randomizing relative phase | `DephasingNoise` |
| Depolarizing | kicks the qubit with a random Pauli | `DepolarizingNoise` |
| Amplitude damping | moves \|1⟩ to \|0⟩, losing energy | `AmplitudeDampingNoise` |
| Readout error | reports a flipped bit without changing the true collapse | `ReadoutErrorModel` |
| Crosstalk | phase noise on neighbours of a gate | `DeviceNoiseModel` |

The three channel classes in this release are:

- `DepolarizingNoise(p, ...)` mixes in the non-identity Paulis with total strength `p`.
  `1 - p` keeps the state. On many qubits the number of Pauli strings is `4^n - 1`, which is why
  the multi-qubit form is limited to four target qubits.
- `DephasingNoise(p, ...)` applies Z with the given strength. The off-diagonal entries of the
  density matrix shrink by a factor `1 - p`.
- `AmplitudeDampingNoise(gamma, ...)` uses `K0 = diag(1, sqrt(1 - gamma))` and
  `K1 = sqrt(gamma) |0><1|`, so a qubit in `|1>` decays to `|0>` with probability `gamma`.

Two consequences matter when reading results:

- The same noisy circuit run twice on `STATE_VECTOR` gives two different final states, because each
  run samples a different trajectory. The density-matrix backend gives the average in one run.
- Purity and von Neumann entropy are meaningful on a density matrix. After a single trajectory the
  state vector stays pure, so an average fidelity computed from one trajectory is not the same as
  the ensemble fidelity.

[Measurement and noise](05-measurement-and-noise.md) gives the exact Kraus operators, the JSON
format, the device-noise fields, and the fidelity and distance metrics.

## Why you cannot just copy the data

Classical error correction repeats the bit. Quantum error correction cannot copy an unknown state:
no-cloning forbids it. What it can do is spread one logical qubit across several physical qubits
using entanglement, so that an error on one physical qubit changes a pattern that can be detected
without measuring the encoded values themselves.

The three-qubit bit-flip code is the smallest example. Two CNOTs copy the computational-basis
components of the data onto two ancillas:

```text
alpha|0> + beta|1>  ->  alpha|000> + beta|111>
```

This is not cloning, because the ancillas started in `|0>` and the three qubits are now entangled.

<figure class="figure-wide">
  <img src="{{ '/assets/img/repetition-code.svg' | relative_url }}" alt="Two controlled NOT gates encode one logical qubit into three physical qubits. A table on the right maps each single bit-flip error to the signs of two parity checks and the matching correction." loading="lazy">
  <figcaption>Encoding and syndromes for the three-qubit bit-flip code. The parity checks reveal
  where the error is, never what the encoded amplitudes are.</figcaption>
</figure>

To diagnose an error, measure two parity checks: `Z0 Z1` and `Z1 Z2`. Each has outcome `+1` when
the two qubits agree and `-1` when they differ. If one qubit flips, the two checks disagree in a
pattern that identifies the flipped qubit:

| Error | `Z0 Z1` | `Z1 Z2` | Fix |
| --- | --- | --- | --- |
| none | + | + | nothing |
| X on q0 | - | + | X on q0 |
| X on q1 | - | - | X on q1 |
| X on q2 | + | - | X on q2 |

The checks commute with the encoding and reveal only parities, so the amplitudes `alpha` and `beta`
are never measured and the logical state survives the diagnosis. After the correction, the state
returns to `alpha|000> + beta|111>`.

A phase flip needs a different code. Encode in the X basis, `alpha|+++> + beta|--->`, and measure
X-type parities instead. The Shor code combines both ideas across nine qubits and corrects any
single-qubit X, Z or Y error. Larger codes, most notably the surface code, arrange many physical
qubits on a two-dimensional lattice and repeatedly measure local stabilizers.

The threshold theorem is the reason this scales. If the physical error rate per operation is below
a threshold, which is roughly one percent for the surface code depending on the assumptions, then
making the code larger suppresses the logical error rate. Below threshold, more physical qubits buy
a better logical qubit. Above threshold, they make it worse. The cost is sobering: a useful logical
qubit may need thousands of physical qubits and many rounds of syndrome measurement.

## What QForge implements

`QuantumErrorCorrection` provides the encoding circuits used in the demos:

```java
Qubit[] nine = Qubit.createQubits(9).toArray(new Qubit[0]);
QuantumEvent[] encode = QuantumErrorCorrection.createShorCode(nine);

Qubit[] data = { nine[0] };
Qubit[] ancilla = { nine[1], nine[2] };
QuantumEvent[] bitFlip = QuantumErrorCorrection.createBitFlipCode(data, ancilla);
QuantumEvent[] phaseFlip = QuantumErrorCorrection.createPhaseFlipCode(data, ancilla);
```

Be precise about the scope. The `SHOR_CODE` demo encodes logical `|0>` and checks that the
probability lands on the eight codewords of `(|000> + |111>)` tensor three, divided by `sqrt(8)`.
It does not perform syndrome extraction or recovery, so the demo verifies the codeword structure,
not a closed correction cycle. `EventType.ERROR_CORRECTION` and `EventType.RECOVERY` exist as event
labels, and `ErrorManager` tracks injected errors, but no demo closes the loop. The three-qubit
circuits above are encoding circuits as well.

There is a second limit to keep in mind. The stabilizer and tensor-network backends do not
implement noisy channels in this release. A noisy correction experiment therefore belongs on
`STATE_VECTOR` or `DENSITY_MATRIX`, while a noise-free Clifford circuit, including a code's
encoding and stabilizer measurements, can use the much larger stabilizer backend.

## Reading a noise run

A quick diagnostic checklist:

- Does fidelity decay exponentially in time or in gate count? Time decay points at T1 and T2;
  gate-count decay points at a `NoiseProfile`.
- Is the error symmetric in 0 and 1? If not, look at amplitude damping and readout confusion, which
  both bias the result toward `|0>` or toward the reported bit.
- Does the density matrix remain pure? If purity stays near 1, the noise is probably not being
  applied on the backend you think.
- Are neighbours of the active qubits affected? That is crosstalk in `DeviceNoiseModel`, not gate
  noise.

Error mitigation is not error correction. Mitigation estimates and corrects the bias in expectation
values after the fact, using extra measurements and assumptions about the noise. Correction
encodes the information so that the computation itself survives. QForge supports the noise models
and the encoding circuits, not a full fault-tolerant stack.

## Related guides

- [Measurement and noise](05-measurement-and-noise.md) documents every channel, device noise field
  and metric.
- [Configuration](03-configuration.md#noise-profile-formats) shows the JSON form of a noise profile.
- [Algorithms](06-algorithms.md#error-correction) lists the error-correction demos and their pass
  criteria.
