package com.codex.fx991.core;

public enum AngleUnit {
    DEG, RAD, GRAD;

    public AngleUnit next() {
        return switch (this) {
            case DEG -> RAD;
            case RAD -> GRAD;
            case GRAD -> DEG;
        };
    }

    double toRadians(double value) {
        return switch (this) {
            case DEG -> Math.toRadians(value);
            case RAD -> value;
            case GRAD -> value * Math.PI / 200.0;
        };
    }

    double fromRadians(double value) {
        return switch (this) {
            case DEG -> Math.toDegrees(value);
            case RAD -> value;
            case GRAD -> value * 200.0 / Math.PI;
        };
    }
}
