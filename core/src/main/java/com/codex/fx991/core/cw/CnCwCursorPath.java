package com.codex.fx991.core.cw;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Immutable semantic editor position.
 *
 * <p>Stage 3 introduces this alongside the legacy token-boundary cursor.  The
 * legacy boundary remains available so Android and the existing editor can be
 * migrated incrementally instead of changing every gesture and edit path at
 * once.  Nested structures will progressively populate {@link #childPath()},
 * {@link #slot()} and {@link #offset()} as fraction, power, radical and
 * function editing moves onto semantic positions.</p>
 */
public final class CnCwCursorPath {
    public enum Slot {
        /** Compatibility position between top-level semantic tokens. */
        ROW,
        FRACTION_NUMERATOR,
        FRACTION_DENOMINATOR,
        RADICAL_CONTENT,
        ROOT_INDEX,
        ROOT_CONTENT,
        SUPERSCRIPT_BASE,
        SUPERSCRIPT_EXPONENT,
        FUNCTION_ARGUMENT
    }

    private final List<Integer> childPath;
    private final Slot slot;
    private final int offset;
    private final int legacyTokenBoundary;

    private CnCwCursorPath(List<Integer> childPath, Slot slot, int offset,
                           int legacyTokenBoundary) {
        List<Integer> normalized = new ArrayList<>();
        if (childPath != null) {
            for (Integer index : childPath) {
                normalized.add(Math.max(0, index == null ? 0 : index));
            }
        }
        this.childPath = com.codex.fx991.core.Compat.copyList(normalized);
        this.slot = Objects.requireNonNull(slot, "slot");
        this.offset = Math.max(0, offset);
        this.legacyTokenBoundary = Math.max(0, legacyTokenBoundary);
    }

    /** Compatibility bridge for the current flat token cursor. */
    public static CnCwCursorPath rootBoundary(int tokenBoundary) {
        int boundary = Math.max(0, tokenBoundary);
        return new CnCwCursorPath(com.codex.fx991.core.Compat.list(), Slot.ROW,
                boundary, boundary);
    }

    /**
     * Creates a nested position while retaining the nearest legacy boundary.
     * The legacy value is only a migration bridge and must not be used to infer
     * nested structure semantics.
     */
    public static CnCwCursorPath nested(List<Integer> childPath, Slot slot, int offset,
                                        int legacyTokenBoundary) {
        return new CnCwCursorPath(childPath, slot, offset, legacyTokenBoundary);
    }

    public List<Integer> childPath() { return childPath; }
    public Slot slot() { return slot; }
    public int offset() { return offset; }
    public int legacyTokenBoundary() { return legacyTokenBoundary; }
    public boolean isRootBoundary() { return childPath.isEmpty() && slot == Slot.ROW; }

    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof CnCwCursorPath that)) return false;
        return offset == that.offset
                && legacyTokenBoundary == that.legacyTokenBoundary
                && slot == that.slot
                && childPath.equals(that.childPath);
    }

    @Override
    public int hashCode() {
        return Objects.hash(childPath, slot, offset, legacyTokenBoundary);
    }

    @Override
    public String toString() {
        return "CnCwCursorPath{" + childPath + ", " + slot + ", offset=" + offset
                + ", legacy=" + legacyTokenBoundary + "}";
    }
}
