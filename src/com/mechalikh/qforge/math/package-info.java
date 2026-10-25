/**
 * Complex arithmetic, dense complex matrices, and numeric support utilities used
 * by the quantum simulator.
 *
 * <p>The main types are {@link Complex} for immutable complex numbers,
 * {@link ComplexMatrix} for mutable dense matrices with tensor products and
 * Hermitian eigendecomposition by a cyclic Jacobi method,
 * {@link MatrixOperations} for static matrix helpers (including partial traces
 * and norm calculations), {@link Parameter} for bounded optimization parameters,
 * and {@link RandomGenerator} for random values drawn from a replaceable
 * {@link java.util.Random} instance.</p>
 *
 * <p>Equality comparisons use absolute tolerances, and division guards use small
 * thresholds; the specific values are documented on the relevant methods.</p>
 */
package com.mechalikh.qforge.math;
