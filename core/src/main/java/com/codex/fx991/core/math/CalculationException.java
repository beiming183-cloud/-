package com.codex.fx991.core.math;

/** Structured calculation failure with a source position for editor recovery. */
public class CalculationException extends IllegalArgumentException {
    private final CalculationError error;
    private final int position;

    public CalculationException(CalculationError error, String detail, int position) {
        super(detail == null || detail.isEmpty() ? error.display() : detail);
        if (error == null) throw new IllegalArgumentException("error");
        this.error = error;
        this.position = Math.max(0, position);
    }

    public CalculationException(CalculationError error, String detail, int position,
                                Throwable cause) {
        super(detail == null || detail.isEmpty() ? error.display() : detail, cause);
        if (error == null) throw new IllegalArgumentException("error");
        this.error = error;
        this.position = Math.max(0, position);
    }

    public CalculationError error() {
        return error;
    }

    public int position() {
        return position;
    }
}
