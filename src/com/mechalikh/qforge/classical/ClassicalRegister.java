package com.mechalikh.qforge.classical;

import java.util.Arrays;

/**
 * A fixed-width classical bit register that receives measurement outcomes.
 *
 * <p>Bit {@code i} of the register is the value of the {@code i}-th measurement target, in
 * the same order the target qubits were supplied. {@link #read()} therefore returns the
 * integer whose least significant bit is the first target, which matches the OpenQASM
 * convention for {@code creg} comparisons. Writes are masked to the register width, and
 * reading a register that was never written returns zero.</p>
 *
 * <p>Registers are mutable and not thread safe. {@link #copy()} produces an independent
 * snapshot for a result object.</p>
 */
public final class ClassicalRegister {
    private final String name;
    private final int size;
    private final boolean[] bits;

    /**
     * Creates a zeroed register.
     *
     * @param name register name, used for lookups and reporting
     * @param size number of bits, must be positive
     * @throws IllegalArgumentException if {@code size} is not positive
     * @throws NullPointerException if {@code name} is {@code null}
     */
    public ClassicalRegister(String name, int size) {
        if (name == null) {
            throw new NullPointerException("Register name cannot be null");
        }
        if (size <= 0) {
            throw new IllegalArgumentException("Register size must be positive");
        }
        this.name = name;
        this.size = size;
        this.bits = new boolean[size];
    }

    /**
     * Returns the register name.
     *
     * @return the name supplied at construction
     */
    public String getName() {
        return name;
    }

    /**
     * Returns the register width.
     *
     * @return the number of bits
     */
    public int size() {
        return size;
    }

    /**
     * Writes packed measurement bits starting at an offset.
     *
     * <p>The most significant bit of {@code value} is written first. Bit {@code width - 1 - j}
     * of {@code value} goes to register bit {@code offset + j}, so writing a two-qubit outcome
     * {@code 10} to offset zero sets bit 0 and clears bit 1. Bits outside the register are
     * ignored; a write that starts at or beyond the size has no effect.
     *
     * @param offset first register bit to write
     * @param value packed outcome bits
     * @param width number of bits to write
     * @throws IllegalArgumentException if {@code offset} is negative or {@code width} is
     *         negative
     */
    public void write(int offset, int value, int width) {
        if (offset < 0) {
            throw new IllegalArgumentException("Register offset cannot be negative");
        }
        if (width < 0) {
            throw new IllegalArgumentException("Write width cannot be negative");
        }
        for (int j = 0; j < width; j++) {
            int target = offset + j;
            if (target >= size) {
                break;
            }
            bits[target] = ((value >> (width - 1 - j)) & 1) != 0;
        }
    }

    /**
     * Writes one bit.
     *
     * @param index register bit index
     * @param value value to write
     * @throws IndexOutOfBoundsException if {@code index} is outside the register
     */
    public void set(int index, boolean value) {
        bits[index] = value;
    }

    /**
     * Reads one bit.
     *
     * @param index register bit index
     * @return the stored bit
     * @throws IndexOutOfBoundsException if {@code index} is outside the register
     */
    public boolean get(int index) {
        return bits[index];
    }

    /**
     * Packs the register into an integer.
     *
     * <p>Bit {@code i} of the result is register bit {@code i}. Registers wider than 64 bits
     * keep their low 64 bits.</p>
     *
     * @return the packed value
     */
    public long read() {
        long value = 0;
        for (int i = 0; i < Math.min(size, Long.SIZE); i++) {
            if (bits[i]) {
                value |= 1L << i;
            }
        }
        return value;
    }

    /**
     * Tests whether the masked bits of this register equal an expected value.
     *
     * <p>The comparison is {@code (read() & mask) == (expected & mask)}. A mask of zero
     * always matches.</p>
     *
     * @param expected expected bit pattern
     * @param mask bits to compare
     * @return {@code true} when the masked bits match
     */
    public boolean matches(long expected, long mask) {
        return (read() & mask) == (expected & mask);
    }

    /**
     * Returns a binary string of the register, most significant bit first.
     *
     * @return a string of {@code size} characters, each {@code 0} or {@code 1}
     */
    public String toBitString() {
        StringBuilder builder = new StringBuilder(size);
        for (int i = size - 1; i >= 0; i--) {
            builder.append(bits[i] ? '1' : '0');
        }
        return builder.toString();
    }

    /**
     * Clears every bit.
     */
    public void reset() {
        Arrays.fill(bits, false);
    }

    /**
     * Returns an independent copy.
     *
     * @return a copy with the same name, width and bit values
     */
    public ClassicalRegister copy() {
        ClassicalRegister copy = new ClassicalRegister(name, size);
        System.arraycopy(bits, 0, copy.bits, 0, size);
        return copy;
    }

    /**
     * Compares registers by name, width and bits.
     *
     * @param obj object to compare with
     * @return {@code true} if {@code obj} is an equal register
     */
    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (!(obj instanceof ClassicalRegister)) {
            return false;
        }
        ClassicalRegister other = (ClassicalRegister) obj;
        return size == other.size && name.equals(other.name)
            && Arrays.equals(bits, other.bits);
    }

    /**
     * Returns a hash code consistent with {@link #equals(Object)}.
     *
     * @return the hash code
     */
    @Override
    public int hashCode() {
        int result = name.hashCode();
        result = 31 * result + size;
        result = 31 * result + Arrays.hashCode(bits);
        return result;
    }

    /**
     * Returns a diagnostic description.
     *
     * @return the name, width and bit string
     */
    @Override
    public String toString() {
        return "ClassicalRegister[" + name + "=" + toBitString() + "]";
    }
}
