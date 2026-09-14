package com.codex.fx991.core;

import java.util.Objects;

/** A semantic input atom with separate screen and evaluator representations. */
public record InputToken(String display, String evaluation) {
    public InputToken {
        Objects.requireNonNull(display, "display");
        Objects.requireNonNull(evaluation, "evaluation");
    }

    public static InputToken of(String value) {
        return new InputToken(value, value);
    }
}
