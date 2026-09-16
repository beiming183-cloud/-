package com.codex.fx991.core.cw;

import java.util.List;
import java.util.Objects;

/**
 * Immutable editable-region descriptor published to platform adapters.
 *
 * <p>A span identifies one semantic slot (for example a fraction denominator
 * or function argument), the legacy token-boundary interval backing that slot,
 * and the enclosing structure interval used by hit testing. Android can use
 * this information to perform geometry-aware touch routing without learning
 * calculator token syntax.</p>
 */
public final class CnCwSemanticSpan {
    private final List<Integer> childPath;
    private final CnCwCursorPath.Slot slot;
    private final int startBoundary;
    private final int endBoundary;
    private final int containerStartBoundary;
    private final int containerEndBoundary;

    CnCwSemanticSpan(List<Integer> childPath,
                     CnCwCursorPath.Slot slot,
                     int startBoundary,
                     int endBoundary,
                     int containerStartBoundary,
                     int containerEndBoundary) {
        this.childPath = com.codex.fx991.core.Compat.copyList(childPath);
        this.slot = Objects.requireNonNull(slot, "slot");
        this.startBoundary = Math.max(0, startBoundary);
        this.endBoundary = Math.max(this.startBoundary, endBoundary);
        this.containerStartBoundary = Math.max(0,
                Math.min(this.startBoundary, containerStartBoundary));
        this.containerEndBoundary = Math.max(this.endBoundary, containerEndBoundary);
    }

    public List<Integer> childPath() { return childPath; }
    public CnCwCursorPath.Slot slot() { return slot; }
    public int startBoundary() { return startBoundary; }
    public int endBoundary() { return endBoundary; }
    public int containerStartBoundary() { return containerStartBoundary; }
    public int containerEndBoundary() { return containerEndBoundary; }
    public int length() { return endBoundary - startBoundary; }

    /** Creates a validated semantic insertion position inside this slot. */
    public CnCwCursorPath position(int offset) {
        int local = Math.max(0, Math.min(length(), offset));
        return CnCwCursorPath.nested(childPath, slot, local, startBoundary + local);
    }

    public boolean matches(CnCwCursorPath path) {
        return path != null && !path.isRootBoundary()
                && slot == path.slot() && childPath.equals(path.childPath());
    }
}
