package com.codex.fx991.core.cw;

import java.util.List;
import java.util.Objects;

/**
 * Immutable natural-display expression tree published by the calculator core.
 *
 * <p>The evaluator still consumes the semantic token stream. This parallel
 * tree carries only layout intent, so Android never has to infer fractions,
 * roots, or exponents from a display string.</p>
 */
public final class CnCwExpressionNode {
    public enum Kind {
        ROW,
        TEXT,
        CURSOR,
        FRACTION,
        MIXED_FRACTION,
        RADICAL,
        NTH_ROOT,
        SUPERSCRIPT
    }

    private final Kind kind;
    private final String text;
    private final List<CnCwExpressionNode> children;
    private final boolean selected;

    CnCwExpressionNode(Kind kind, String text, List<CnCwExpressionNode> children,
                       boolean selected) {
        this.kind = Objects.requireNonNull(kind, "kind");
        this.text = text == null ? "" : text;
        this.children = com.codex.fx991.core.Compat.copyList(children);
        this.selected = selected;
    }

    static CnCwExpressionNode row(List<CnCwExpressionNode> children) {
        return new CnCwExpressionNode(Kind.ROW, "", children, false);
    }

    static CnCwExpressionNode text(String text, boolean selected) {
        return new CnCwExpressionNode(Kind.TEXT, text,
                com.codex.fx991.core.Compat.list(), selected);
    }

    static CnCwExpressionNode cursor() {
        return new CnCwExpressionNode(Kind.CURSOR, "",
                com.codex.fx991.core.Compat.list(), false);
    }

    static CnCwExpressionNode compound(Kind kind, List<CnCwExpressionNode> children,
                                       boolean selected) {
        return new CnCwExpressionNode(kind, "", children, selected);
    }

    public Kind kind() { return kind; }
    public String text() { return text; }
    public List<CnCwExpressionNode> children() { return children; }
    public boolean selected() { return selected; }

    public boolean containsCursor() {
        if (kind == Kind.CURSOR) return true;
        for (CnCwExpressionNode child : children) {
            if (child.containsCursor()) return true;
        }
        return false;
    }
}
