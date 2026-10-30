---
title: "Entanglement, measurement and teleportation"
nav: "Entanglement and measurement"
order: 11
group: background
description: "Bell pairs, nonlocal correlations, the Born rule and collapse, observables and shot noise, and how teleportation moves a state with two classical bits."
---

# Entanglement, measurement and teleportation

Two qubits can hold a joint state that neither qubit holds alone. That is entanglement. It is also
the point where measurement stops looking like reading a meter. This page covers both, then uses
them to explain teleportation.

## Two qubits can share one state

Some two-qubit states factor into a state for each qubit. For example, `|+>` on the first qubit and
`|0>` on the second is the product state

```text
|+> |0> = (|00> + |10>) / sqrt(2)
```

Other states cannot be written as a product. The Bell state from the previous page is one:

```text
|Phi+> = (|00> + |11>) / sqrt(2)
```

There is no pair of one-qubit states whose product equals `|Phi+>`. The two qubits are described by
one joint wavefunction, and the correlation is in the joint state rather than in either qubit
separately.

<figure>
  <img src="{{ '/assets/img/entangled-pair.svg' | relative_url }}" alt="Two rows. In the first row a source sends photons to two detectors set to 0 degrees, and both detect plus. In the second row detector B is rotated to 45 degrees and its outcome is unknown." loading="lazy">
  <figcaption>When both analyzers use the same angle the two outcomes match. Rotate one analyzer
  and the individual outcomes become unpredictable, while the pair still comes from one joint
  state.</figcaption>
</figure>

The correlations are stark:

- Measure both qubits in the Z basis and you see `00` or `11`, each with probability one half.
- Measure both in the X basis and you see `++` or `--`, again in equal proportions.
- Measure one in Z and the other in X and the outcomes are independent.

Look at either qubit alone and it is maximally mixed: a Z measurement gives 0 or 1 with equal
probability, on every run. The certainty exists only in the comparison between the two records.
That comparison needs a classical message, because the two outcomes are collected at two places.
Entanglement cannot be used to send a signal faster than light, and no experiment has found one.

The correlations are stronger than any classical hidden variable model can produce. Bell and
later Clauser, Horne, Shimony and Holt turned this into an inequality: local models cap a certain
combination of correlation averages at 2, while quantum mechanics reaches up to `2 sqrt(2)`, the
Tsirelson bound. Experiments from the 1970s onward, with loophole-free versions in 2015, measured
values that violate the classical bound. The 2022 Nobel Prize in Physics recognized Clauser,
Aspect and Zeilinger for that line of work.

## Measurement is a rule, not a peek

The Born rule says that a measurement of `alpha|0> + beta|1>` in the computational basis returns
0 with probability `|alpha|^2` and 1 with probability `|beta|^2`. After the measurement the state
is the outcome that was obtained, so a second measurement in the same basis repeats it.

<figure>
  <img src="{{ '/assets/img/measurement-collapse.svg' | relative_url }}" alt="Left: a superposition with equal probability for 0 and 1. Right: after one measurement the state is the observed outcome with probability one and the other outcome is empty." loading="lazy">
  <figcaption>A single run returns one outcome. The 50/50 distribution appears only when the
  experiment is repeated many times.</figcaption>
</figure>

Measuring in a different basis is not a deeper look at the same value. In circuit terms, measuring
the X basis means applying a Hadamard and then measuring Z. The Hadamard rotates the basis so that
`|+>` becomes `|0>` and `|->` becomes `|1>`:

| State | Z measurement | X measurement |
| --- | --- | --- |
| \|0⟩ | 0 every time | 0 or 1, half and half |
| \|1⟩ | 1 every time | 0 or 1, half and half |
| \|+⟩ | 0 or 1, half and half | 0 every time |
| \|-⟩ | 0 or 1, half and half | 1 every time |

This is why BB84 works. An eavesdropper cannot learn both the bit and the basis without
disturbing the state, and copying an unknown qubit is forbidden by the no-cloning theorem. The
same theorem stops a quantum program from duplicating a state to measure it twice in different
bases.

Measurement can also be partial. Measure only the first qubit of a Bell pair in the Z basis and
the second qubit collapses to the same value, even if it is far away. That is sometimes called
steering. It still cannot carry a message, because the person holding the second qubit does not
know the first outcome until it is sent classically.

QForge distinguishes destructive and non-destructive measurement. A `MeasurementOperator` with
`destructive` set to `false` reports an outcome and leaves the input state untouched, while the
default collapses the state onto the sampled outcome. Mid-circuit measurement can write into a
classical register, which is what feed-forward uses. The readout can also be confused: the state
collapses onto the true outcome, while the reported bit is flipped with the configured readout
error. [Measurement and noise](05-measurement-and-noise.md) lists every path and every accessor.

## Observables and expectation values

A measurement is defined by a Hermitian observable. The Pauli Z observable has eigenvalues `+1`
and `-1`, so its expectation value is

```text
<Z> = P(0) - P(1)
```

For `|+>` the expectation is 0, for `|0>` it is 1, and for `|1>` it is -1. On a Bell pair
`<ZZ> = 1` and `<XX> = 1`, while `<ZI> = 0` because one qubit alone carries no bias.

QForge implements this with `Observable`. `Observable.pauliZ(q)` builds one Pauli, and
`Observable.fromString("ZZ", new Qubit[]{q0, q1})` builds a product. `measureMultiple(state, shots)`
returns a `MeasurementResult` with the sampled counts, the exact probabilities, the expectation
value, the variance, the standard error and a confidence interval. The exact probabilities come
from the state; the counts come from sampling and therefore carry shot noise. A useful rule of
thumb is that the standard error falls as `1 / sqrt(shots)`, so four times the shots halves it.

<blockquote>
Measurement is not a conscious observer effect, and it is not reading a value that was already
there. It is a physical interaction that selects one outcome from the amplitudes and leaves the
system in that outcome.
</blockquote>

## Teleportation: move a state with two bits

Teleportation transfers an unknown one-qubit state from Alice to Bob using one shared Bell pair and
two classical bits. No qubit travels, and the original is destroyed. The circuit has three qubits:
the payload on q0 and a Bell pair shared between q1 and q2.

1. Alice applies CNOT from the payload to her half of the pair, then H to the payload.
2. She measures her two qubits, producing bits `m0` and `m1`.
3. She sends the two bits to Bob.
4. Bob applies Z if `m0` is 1 and X if `m1` is 1.

<figure class="figure-wide">
  <img src="{{ '/assets/img/teleportation.svg' | relative_url }}" alt="A three-qubit teleportation circuit. A Hadamard and a controlled NOT create a Bell pair on q1 and q2. Two more gates and two measurements produce classical bits m0 and m1, which condition a Z and an X gate on q2." loading="lazy">
  <figcaption>The payload is measured away, the Bell pair is consumed, and the two classical bits
  select the correction that restores the payload on the receiver's qubit.</figcaption>
</figure>

The four possible outcomes and corrections are:

| `m0` | `m1` | Correction on q2 |
| --- | --- | --- |
| 0 | 0 | identity |
| 0 | 1 | X |
| 1 | 0 | Z |
| 1 | 1 | Z then X |

Without the two bits, Bob's qubit is maximally mixed. With them, the state is recovered exactly.
This is why teleportation does not violate no-cloning: the original is gone. It is also why the
protocol cannot run faster than light: the correction needs a classical message.

QForge ships two demonstrations. `FeedForwardTeleportation` runs 200 full shots with mid-circuit
measurements and `ConditionalOperation` corrections. With seed 20261029 it reports success 1.0000
and mean fidelity 1.000000 across all shots. A control run that skips the corrections succeeds on
0.2400 of the shots, close to the analytic 0.25. `QuantumTeleportation` instead splits the exact
state into the four branches and shows corrected fidelity 1 in every branch.

```bash
mvn -q -DskipTests compile
java -cp target/classes example.algorithms.teleportation.FeedForwardTeleportation
java -cp target/classes example.algorithms.teleportation.QuantumTeleportation
```

The conditional path is described in
[Extending QForge](07-extending.md#classical-feed-forward-control).

## Related guides

- [Superposition, gates and circuits](10-superposition-gates-and-circuits.md) builds the Bell pair
  used here.
- [Measurement and noise](05-measurement-and-noise.md) is the API reference for measurement,
  observables, shot statistics and readout error.
- [Algorithms](06-algorithms.md) uses these primitives in Grover, phase estimation and the
  variational demos.
