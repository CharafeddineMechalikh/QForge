---
title: "The math a beginner needs"
nav: "Math you need"
order: 9
group: background
description: "Complex amplitudes, modulus squared, phase, vectors, matrices and unitarity, the small set of ideas every QForge number depends on."
---

# The math a beginner needs

Every page in this background series uses the same handful of ideas. An amplitude is a complex
number. The square of its length is a probability. A gate is a matrix. Multiplying a state by a
gate keeps the total probability at one. That is the whole toolkit.

There is no calculus here and no linear algebra course. If you can add, multiply and take a square
root, you can follow the rest of the series.

## An amplitude is an arrow in the complex plane

A complex number has a real part and an imaginary part,

```text
alpha = a + b i,      i^2 = -1
```

QForge stores every amplitude as two doubles, one for each part, and `Complex.real()` and
`Complex.imag()` return them. One amplitude answers two different questions at once: how large the
arrow is, and which way it points. The size is what measurement sees. The direction is the phase,
and it decides how amplitudes add when two paths meet.

The size you measure is the square of the modulus:

```text
|alpha|^2 = a^2 + b^2
P(0) = |alpha|^2,      P(1) = |beta|^2,      |alpha|^2 + |beta|^2 = 1
```

That last line is normalization. It says the two outcomes cover every possibility, and every gate
in QForge preserves it.

<figure class="figure-narrow">
  <img src="{{ '/assets/img/amplitude-plane.svg' | relative_url }}" alt="The complex plane with a unit circle. An arrow from the origin points at 45 degrees toward a state with equal real and imaginary parts, with dashed lines to the real and imaginary axes labeled a and b. The angle from the real axis is phi." loading="lazy">
  <figcaption>An amplitude is an arrow. Its squared length is the probability, and its angle is the
  phase.</figcaption>
</figure>

Two worked examples make the arithmetic concrete.

- `|+>` has `alpha = beta = 1 / sqrt(2)`, about 0.7071, so `|alpha|^2 = |beta|^2 = 0.5`. The two
  outcomes are equally likely.
- `alpha = (1 + i) / 2` and `beta = (1 - i) / 2` give `|alpha|^2 = (1 + 1) / 4 = 0.5` and the same
  for `beta`. Both amplitudes are complex, and the measurement is still a 50/50 split.

The amplitude can be complex without changing the odds. If you only ever measured in the Z basis,
you would never notice the imaginary parts at all. The next section shows why circuits do notice
them.

## Phase is the part that can cancel

Multiply the whole state by the same complex number and nothing observable changes. That is called
a global phase, and QForge is free to ignore it. What matters is the relative phase, the angle
between the two arrows.

The cleanest demonstration uses three gates:

1. Start in `|0>` and apply H. The state becomes `|+> = (|0> + |1>) / sqrt(2)`.
2. Apply Z. The state becomes `|-> = (|0> - |1>) / sqrt(2)`.
3. Measure in the Z basis. The outcome is still 50/50, exactly as before.
4. Apply H again and measure. The outcome is now `1` with certainty.

| State | `alpha` | `beta` | P(0) | P(1) | P(1) after H |
| --- | --- | --- | --- | --- | --- |
| plus | 0.7071 | 0.7071 | 0.50 | 0.50 | 0.00 |
| minus | 0.7071 | −0.7071 | 0.50 | 0.50 | 1.00 |

The minus sign was invisible in the Z basis and decisive in the X basis. That is what interference
means in practice: two paths whose amplitudes arrive with opposite signs cancel. The rule behind the
angles is Euler's formula, `e^(i phi) = cos(phi) + i sin(phi)`, where `phi` is the angle of the
arrow. On the Bloch sphere the relative phase is the azimuthal angle, described in
[Superposition, gates and circuits](10-superposition-gates-and-circuits.md#the-bloch-sphere).

## Multiplying by i is a quarter turn

Several gates leave the probabilities alone and only change the phase of one amplitude. They matter
because the next gate can turn that phase into a difference in the odds.

| Gate | Matrix | What it does |
| --- | --- | --- |
| S | `diag(1, i)` | quarter turn on the 1 amplitude (90 degrees) |
| T | `diag(1, e^(i pi/4))` | eighth turn on the 1 amplitude (45 degrees) |
| RZ(theta) | `diag(1, e^(i theta))` | any phase angle on the 1 amplitude |
| RX, RY | real cosines and sines | mixing, so these do change the odds |

## Gates are matrices and a state is a vector

Write the two amplitudes as a column vector and a gate as a 2 by 2 matrix. Applying the gate is an
ordinary matrix times vector product.

```text
X = [[0, 1], [1, 0]]              H = 1/sqrt(2) [[1, 1], [1, -1]]

X [1, 0] = [0, 1]                 H [1, 0] = [0.7071, 0.7071]
```

`X [1, 0]` is the notation for `X` acting on `|0>`, and it produces `|1>`. The matrices are unitary,
meaning `U` times its conjugate transpose is the identity. Unitarity is the reason probabilities
still add to one after every gate: the matrix rotates the arrow without stretching it. Two-qubit
gates are 4 by 4 matrices, and the vector they act on has four entries, which is where the next
page begins.

The full gate catalog, with each matrix and its factory method, is in
[Superposition, gates and circuits](10-superposition-gates-and-circuits.md#gates-are-reversible-rotations)
and [Gates and circuits](04-gates-and-circuits.md#gate-catalog).

## What you do not need

- Calculus and differential equations. Nothing here is differentiated.
- A linear algebra course. You need matrix times vector, not eigenvalues, until the algorithms page.
- Proofs. The simulator does the arithmetic.

You do need four operations: add complex numbers, multiply them, square a modulus, and rotate an
angle. Everything else in these pages is vocabulary built on those four.

## Where this goes next

- [Qubits, photons and polarization](09-qubits-and-photons.md) describes the physical object these
  amplitudes belong to.
- [Superposition, gates and circuits](10-superposition-gates-and-circuits.md) turns the matrices into
  a circuit diagram.
- [Entanglement, measurement and teleportation](11-entanglement-and-measurement.md) adds a second
  qubit and the rules for reading one out.
- [Noise and error correction](12-noise-and-error-correction.md) explains what happens when the
  amplitudes drift.
