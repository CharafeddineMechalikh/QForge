package com.mechalikh.qforge.qasm;

/**
 * Runtime exception raised when OpenQASM source cannot be parsed or when a program cannot be
 * emitted as OpenQASM.
 *
 * <p>The optional line and column describe the source position of a parse failure. Both are set
 * to {@code -1} when the position is unknown, which is the case for failures raised by the
 * emitter. When a position is known it is appended to the message in the form
 * {@code (line N, column M)}, so callers that only print the message still see the location.</p>
 */
public class QasmException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    private final int line;
    private final int column;

    /**
     * Creates an exception without a source position.
     *
     * @param message the detail message
     */
    public QasmException(String message) {
        this(message, -1, -1, null);
    }

    /**
     * Creates an exception without a source position and with a cause.
     *
     * @param message the detail message
     * @param cause the underlying failure, may be {@code null}
     */
    public QasmException(String message, Throwable cause) {
        this(message, -1, -1, cause);
    }

    /**
     * Creates an exception at a source position.
     *
     * @param message the detail message
     * @param line the one based source line, or a value below one when unknown
     * @param column the one based source column, or a value below one when unknown
     */
    public QasmException(String message, int line, int column) {
        this(message, line, column, null);
    }

    /**
     * Creates an exception at a source position with a cause.
     *
     * @param message the detail message
     * @param line the one based source line, or a value below one when unknown
     * @param column the one based source column, or a value below one when unknown
     * @param cause the underlying failure, may be {@code null}
     */
    public QasmException(String message, int line, int column, Throwable cause) {
        super(decorate(message, line, column), cause);
        this.line = line;
        this.column = column;
    }

    /**
     * Returns the source line of the failure.
     *
     * @return the one based line, or {@code -1} when the position is unknown
     */
    public int getLine() {
        return line;
    }

    /**
     * Returns the source column of the failure.
     *
     * @return the one based column, or {@code -1} when the position is unknown
     */
    public int getColumn() {
        return column;
    }

    private static String decorate(String message, int line, int column) {
        if (line <= 0) {
            return message;
        }
        return message + " (line " + line + ", column " + column + ")";
    }
}
