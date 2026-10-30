---
title: "Qubits, photons and polarization"
nav: "Qubits and photons"
order: 9
group: background
description: "What a qubit is, how a photon encodes one with polarization, how real hardware traps and controls two-level systems, and how QForge stores the state."
---

# Qubits, photons and polarization

Every circuit in QForge runs on the same object: a qubit. Before the gates and the algorithms, it
helps to know what that object is physically, why light is one of the cleanest ways to carry it,
and how laboratories actually build one. None of this is required to run the simulator, but it
makes the numbers on screen easier to interpret.

## A bit holds one answer, a qubit holds a direction

A classical bit is a switch with two positions. It is `0` or `1` at all times, you can read it
without changing it, and you can copy it as often as you like.

A qubit is not a switch that secretly sits in one position. Its state is a pair of complex
amplitudes,

```text
|psi> = alpha |0> + beta |1>,    |alpha|^2 + |beta|^2 = 1
```

The amplitudes are what the simulator stores and what gates rotate. A measurement returns `0`
with probability `|alpha|^2` and `1` with probability `|beta|^2`, and after that measurement the
state is the outcome that was seen. The amplitudes can be negative or complex, which is why two
paths through a circuit can cancel each other. That cancellation, called interference, is the
resource quantum algorithms use.

| Property | Classical bit | Qubit |
| --- | --- | --- |
| State | `0` or `1` | α\|0⟩ + β\|1⟩ |
| Reading | returns the stored value | returns `0` or `1`, then collapses |
| Copying | free | impossible for an unknown state |
| Operations | NOT, AND, OR | unitary gates such as X, H, CNOT |
| Physical carrier | voltage, current, magnetic domain | photon, atom, superconducting circuit |

Because the overall phase of a qubit has no observable effect, a pure qubit state needs only two
real numbers to describe, not four. Those two numbers are the coordinates of a point on a sphere,
which is the Bloch sphere described in
[Superposition, gates and circuits](10-superposition-gates-and-circuits.md#the-bloch-sphere).

## A photon is a qubit you can hold a polarizer in front of

A photon is a single quantum of light. It has no mass, always travels at the speed of light, and
carries a direction of oscillation called polarization. The electric field oscillates across the
direction of travel, not along it. Choose two perpendicular directions in that transverse plane and
you have two distinguishable states, which is exactly what a qubit needs.

The conventional names are:

| Polarization | Symbol | Qubit form | In the Z basis | In the X basis |
| --- | --- | --- | --- | --- |
| Horizontal | H | \|0⟩ | bit 0 | equal H and V |
| Vertical | V | \|1⟩ | bit 1 | equal H and V |
| Diagonal, +45 degrees | D | \|+⟩ | equal H and V | bit 0 |
| Anti-diagonal, -45 degrees | A | \|-⟩ | equal H and V | bit 1 |

<figure>
  <img src="{{ '/assets/img/polarization-bases.svg' | relative_url }}" alt="Two circles show photon polarization. The left circle has horizontal and vertical double arrows labeled H and V. The right circle has diagonal double arrows labeled D and A." loading="lazy">
  <figcaption>Two ways to describe the same photon. H and V form one measurement basis, D and A
  form another. A photon prepared in one basis has no fixed value in the other.</figcaption>
</figure>

A polarizing filter is a measurement device. Send an H photon into a vertical filter and nothing
comes out. Send the same photon into a filter rotated by 45 degrees and it passes half the time,
and when it passes its polarization is now 45 degrees. The filter did not reveal a hidden
diagonal value. It chose one of the two outcomes allowed by the state and left the photon in that
outcome.

This is the whole idea behind BB84, the first quantum key distribution protocol. Alice sends one of
four photon states: H or V in one basis, D or A in the other. Bob measures in a randomly chosen
basis. When his basis matches Alice's, the result is deterministic and they keep the bit. When it
does not, the result is random. An eavesdropper who intercepts a photon, measures it, and resends
it must guess the basis, and a wrong guess destroys the state. About a quarter of the sifted bits
then carry errors, which is the signature that someone listened in.

QForge ships that protocol as `example.algorithms.bb84.BB84`. The demo runs 1000 transmissions with
seed 2026 in two scenarios. With a clean channel it reports 490 sifted bits and 0 errors. With an
intercept-resend eavesdropper it reports 490 sifted bits and 116 errors, a quantum bit error rate
of 0.2367, above the demo's detection threshold of 0.11.

```bash
java -cp "target/classes;lib/*" example.algorithms.bb84.BB84
```

The simulator does not transport light. It stores the four polarization states as one-qubit states
in the Z and X bases, which is exactly the mathematical content of the protocol.

## How to make a qubit

Any two-level quantum system is a candidate qubit. Turning one into a usable device takes four
things:

1. Two states that can be distinguished reliably.
2. Isolation from the environment, so the state survives long enough to use.
3. A control knob that rotates the state in a known way, which is a gate.
4. A readout that maps the state onto a classical bit.

Different platforms trade these requirements against each other. The table below is a map, not a
ranking.

<figure>
  <img src="{{ '/assets/img/qubit-platforms.svg' | relative_url }}" alt="Four panels with simplified icons: a superconducting circuit with a Josephson junction, a trapped ion between electrodes, a photonic waveguide with a filter and detector, and two laser tweezers holding a neutral atom." loading="lazy">
  <figcaption>Four common qubit platforms. The two-level system is an electrical circuit, an
  electronic state, a photon's polarization, or an atomic state.</figcaption>
</figure>

| Platform | Where the 0 and 1 live | How gates are applied | Typical coherence time |
| --- | --- | --- | --- |
| Superconducting transmon | two lowest levels of an anharmonic LC circuit with a Josephson junction | microwave pulses, resonators for readout | tens to hundreds of microseconds |
| Trapped ion | internal electronic states of a single ion | laser or Raman pulses, shared motion for two-qubit gates | seconds |
| Photonic | polarization, path, or time bin of a photon | wave plates, beam splitters, phase shifters | limited by loss and detector efficiency |
| Neutral atom | hyperfine or Rydberg levels of an atom in an optical tweezer | laser pulses, Rydberg blockade for two-qubit gates | seconds |

Superconducting qubits are fast and lithographically reproducible, but they need a dilution
refrigerator at roughly 10 millikelvin and they lose coherence in microseconds. Trapped ions are
slow to gate but keep coherence for seconds and can be linked through shared motion. Photons are
the natural flying qubit for communication, yet they are hard to store and every detector is
imperfect. Neutral atoms combine long coherence with flexible arrangement in tweezer arrays, at the
cost of complex laser systems. Silicon spin qubits, nitrogen-vacancy centers in diamond, and
topological proposals exist too, and each has its own error profile.

QForge does not simulate the electromagnetic hardware. It starts one level up, where a qubit is an
index and the state is a vector of amplitudes. The hardware matters because it determines the
noise: relaxation times, gate durations, readout confusion, and crosstalk all come from the
platform. [Noise and error correction](12-noise-and-error-correction.md) maps those numbers onto
the channels the simulator applies.

## What the simulator stores

A `Qubit` is a name and an index. The interesting object is the state. For `n` qubits the
`STATE_VECTOR` backend stores `2^n` complex amplitudes, one for every computational basis state.

Qubit index `0` is the most significant bit of the basis-state index. With two qubits the four
amplitudes line up like this:

| State index | Qubit 0 | Qubit 1 | Ket | Amplitude |
| --- | --- | --- | --- | --- |
| 0 | 0 | 0 | \|00⟩ | `a00` |
| 1 | 0 | 1 | \|01⟩ | `a01` |
| 2 | 1 | 0 | \|10⟩ | `a10` |
| 3 | 1 | 1 | \|11⟩ | `a11` |

```java
SimulationConfig config = new SimulationConfig();
config.setNumQubits(2);

Qubit q0 = new Qubit(0, "q0");
Qubit q1 = new Qubit(1, "q1");
```

The ordering convention is worth memorizing, because it decides which index a bit string maps to.
[Gates and circuits](04-gates-and-circuits.md#qubit-ordering-convention) spells out the full rule.

State vectors grow exponentially. Ten qubits need 1,024 amplitudes, twenty need 1,048,576, and
thirty need 1,073,741,824. The getting started guide notes that a 20-qubit state vector needs about
338 MB of heap in this implementation, which is why the default ceilings are 30 qubits for
`STATE_VECTOR` and 12 for `DENSITY_MATRIX`. The other two backends trade generality for reach:
[Architecture](02-architecture.md#state-representations) explains when each one applies.

## Where this goes next

- [Superposition, gates and circuits](10-superposition-gates-and-circuits.md) turns amplitudes into
  gates and wires.
- [Entanglement, measurement and teleportation](11-entanglement-and-measurement.md) covers what two
  qubits can do together and what a measurement really is.
- [Noise and error correction](12-noise-and-error-correction.md) explains why the coherence times in
  the table above set the limits of a real device.
