package com.codex.fx991.core.math;

/** Error categories exposed by the fx-991 calculation application. */
public enum CalculationError {
    SYNTAX("Syntax ERROR"),
    MATH("Math ERROR"),
    STACK("Stack ERROR"),
    ARGUMENT("Argument ERROR"),
    TIMEOUT("Timeout"),
    CIRCULAR("Circular ERROR"),
    CANNOT_SIMPLIFY("Cannot Simplify"),
    NO_OPERATOR("No Operator"),
    UNDEFINED("Undefined");

    private final String display;

    CalculationError(String display) {
        this.display = display;
    }

    public String display() {
        return display;
    }
}
