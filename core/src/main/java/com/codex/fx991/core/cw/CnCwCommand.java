package com.codex.fx991.core.cw;

/**
 * A first-screen command offered by an application.  Commands deliberately
 * describe the boundary of a mode; the corresponding numerical engines can
 * be wired in independently without putting hidden flags in the Android
 * view.  The shell therefore never presents a command as a completed
 * calculation until an engine has actually returned a result.
 */
public record CnCwCommand(String id, String label, String description) {
    public CnCwCommand {
        if (com.codex.fx991.core.Compat.isBlank(id)) throw new IllegalArgumentException("id");
        if (com.codex.fx991.core.Compat.isBlank(label)) throw new IllegalArgumentException("label");
        if (description == null) description = "";
    }
}
