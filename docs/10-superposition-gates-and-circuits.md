---
title: "Superposition, gates and circuits"
nav: "10 Gates and circuits"
order: 10
group: background
description: "Amplitudes, phase and interference, the Bloch sphere, the gate set, how to read a circuit diagram, and how a Hadamard and a CNOT build a Bell pair in QForge."
---

# Superposition, gates and circuits

Superposition gets most of the attention, but phase and interference do the work. This page builds
the picture from a single qubit to two, then shows how a circuit diagram and the QForge event queue
describe the same thing.

## Amplitudes can add and cancel

The Hadamard gate is the standard way to create an equal superposition:

```text
H |0> = (|0> + |1>) / sqrt(2)
H |1> = (|0> - |1>) / sqrt(2)
```

The minus sign on the second line is a relative phase. It changes nothing if you measure that qubit
immediately in the computational basis, because both outcomes still have probability one half. It
changes everything if another gate mixes the two paths, because amplitudes combine before they are
squared.

Apply H twice and the two paths cancel for `|1>`:

```text
H (H |0>) = H (|0> + |1>) / sqrt(2)
          = [(|0> + |1>) + (|0> - |1>)] / 2
          = |0>
```

This is the smallest example of quantum interference. A qubit is not carrying both answers at once
in a classical sense; it is carrying amplitudes that can reinforce or cancel.

## The Bloch sphere

A pure qubit state has two real parameters after normalization and after removing a global phase
that cannot be observed. Those parameters are the angles of a point on a sphere.

<figure class="figure-narrow">
  <img src="{{ '/assets/img/bloch-sphere.svg' | relative_url }}" alt="A sphere with state zero at the north pole, state one at the south pole, and the equal superpositions on the equator. A vector points to a state inside the upper right quadrant." loading="lazy">
  <figcaption>Every pure one-qubit state is a point on the surface. Gates rotate the vector. Mixed
  states, which come from noise, sit inside the sphere.</figcaption>
</figure>

The north pole is `|0>` and the south pole is `|1>`. The equator holds the equal superpositions:
`|+>` on the right, `|->` on the left, `|+i>` at the back, and `|-i>` at the front. Orthogonal
states are antipodal. The polar angle `theta` measures how much of the state is in `|0>`, and the
azimuthal angle `phi` is the relative phase.

The sphere is a map of rotations, not just a picture:

- X is a 180 degree rotation about the x axis, which swaps the poles.
- Z is a 180 degree rotation about the z axis, which leaves the poles fixed and reverses the
  equator.
- H is a 180 degree rotation about the diagonal between x and z, which maps the z axis onto the x
  axis.
- RX, RY and RZ rotate by an arbitrary angle about their axis.

Measuring Z asks whether the state is closer to the north or south pole. Measuring X asks the same
question on the equator, and in circuit terms it is the same as applying H and then measuring Z.
A mixed state sits inside the sphere, with a shorter vector; [Noise and error
correction](12-noise-and-error-correction.md) explains why.

## Gates are reversible rotations

Every gate is a unitary matrix, so it preserves the norm of the state. The three most common
single-qubit matrices are:

```text
X = [[0, 1], [1, 0]]
Z = [[1, 0], [0, -1]]
H = 1/sqrt(2) * [[1, 1], [1, -1]]
```

Two-qubit gates are matrices over four amplitudes. The controlled NOT gate, written CNOT with the
control first, flips the target when the control is `1`:

```text
|00> -> |00>
|01> -> |01>
|10> -> |11>
|11> -> |10>
```

The QForge factories follow the same naming. Single-qubit gates are created with
`SingleQubitGate`, multi-qubit gates with `MultiQubitGate`:

| Gate | Action | QForge factory |
| --- | --- | --- |
| X | bit flip | `createPauliX(q)` |
| Y | bit flip plus phase flip | `createPauliY(q)` |
| Z | phase flip on the 1 component | `createPauliZ(q)` |
| H | creates or removes an equal superposition | `createHadamard(q)` |
| S | quarter turn of phase | `createS(q)` |
| T | eighth turn of phase | `createT(q)` |
| RX, RY, RZ | rotation by an angle in radians | `createRX(a, q)`, `createRY(a, q)`, `createRZ(a, q)` |
| CNOT | flips the target when the control is 1 | `createCNOT(control, target)` |
| CZ | phase flip when both qubits are 1 | `createCZ(control, target)` |
| SWAP | exchanges two qubits | `createSWAP(q1, q2)` |
| Toffoli | flips the target when both controls are 1 | `createToffoli(c1, c2, target)` |
| CPHASE | controlled phase by an angle | `createCPHASE(a, control, target)` |

`createPhase(angle, q)` builds a general phase gate, which covers S and T when the angle is pi/2 or
pi/4. Custom matrices and their JSON registration are described in
[Gates and circuits](04-gates-and-circuits.md#custom-gates).

## A circuit is a sequence of gates on wires

A circuit diagram is a compact program. Time runs from left to right, each horizontal line is a
qubit, and a box is a gate on that qubit. A filled dot with a line down to a circled plus is a
CNOT, where the dot is the control. A box with a meter symbol is a measurement. A double line
carries a classical bit, which is what feed-forward uses. Gates on different qubits may share a
horizontal position because they can act in parallel.

QForge does not use diagram columns. It schedules `QuantumEvent` objects with explicit timestamps,
then dispatches them in time order, with priority and construction order as tie-breakers.
[Scheduling and event ordering](02-architecture.md#event-ordering) describes the queue. The
diagram is a readable summary of the same ordered list, and JSON circuits and OpenQASM both lower
to it.

## Build a Bell pair

Start with two qubits in `|00>`. A Hadamard on qubit 0 puts the first qubit into an equal
superposition, and a CNOT from qubit 0 to qubit 1 turns that into a perfectly correlated pair:

```text
|00>
  --H(q0)--------> (|00> + |10>) / sqrt(2)
  --CNOT(q0,q1)--> (|00> + |11>) / sqrt(2)
```

<figure>
  <img src="{{ '/assets/img/bell-circuit.svg' | relative_url }}" alt="A two-qubit circuit with a Hadamard on q0, a controlled NOT from q0 to q1, and measurements on both qubits. The outcome bars show 00 and 11 at 50 percent each." loading="lazy">
  <figcaption>One Hadamard and one CNOT produce the Bell state. Measuring both qubits returns 00
  or 11, never 01 or 10.</figcaption>
</figure>

The same circuit in Java schedules two events:

```java
Qubit q0 = new Qubit(0, "q0");
Qubit q1 = new Qubit(1, "q1");

simulator.scheduleEvent(new QuantumEvent.Builder("h0")
    .withOperation(SingleQubitGate.createHadamard(q0))
    .withTargetQubits(q0)
    .build());
simulator.scheduleEvent(new QuantumEvent.Builder("cx")
    .withOperation(MultiQubitGate.createCNOT(q0, q1))
    .withTargetQubits(q0, q1)
    .build());
```

After a measurement of both qubits, the exact probabilities are 0.5 for `|00>` and 0.5 for `|11>`.
The amplitudes for `|01>` and `|10>` are zero, so those outcomes never appear. If you instead
measure only one qubit and keep the state, the other qubit is left in the matching state, which is
the starting point for entanglement.

Remember that qubit 0 is the most significant bit, so `|10>` is state index 2, not index 1.
[Qubit ordering](04-gates-and-circuits.md#qubit-ordering-convention) has the full convention.

## Interference is the resource

A quantum algorithm is a plan for making the amplitudes of wrong answers cancel and the amplitudes
of right answers add. Grover's algorithm does this with repeated amplitude amplification, the
Quantum Fourier Transform does it by rephasing, and phase estimation reads the phase back out. The
algorithms guide walks through each construction with a runnable demo. Without interference, a
superposition is just expensive randomness.

## Related guides

- [Entanglement, measurement and teleportation](11-entanglement-and-measurement.md) picks up from
  the Bell pair.
- [Gates and circuits](04-gates-and-circuits.md) is the reference for every gate, the JSON schema
  and OpenQASM interchange.
- [Architecture](02-architecture.md) explains how scheduled events reach the state.
