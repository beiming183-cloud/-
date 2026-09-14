package com.codex.fx991.core.mode;

import java.util.List;

/** Product-profile gate derived from the shared fx-991CN CW / fx-999CN CW manual. */
public enum CnCwModel {
    FX_991_CN_CW("991CN CW", false),
    FX_999_CN_CW("999CN CW", true);

    private final String displayName;
    private final boolean extended;

    CnCwModel(String displayName, boolean extended) {
        this.displayName = displayName;
        this.extended = extended;
    }

    public String displayName() { return displayName; }
    public boolean supportsDistribution() { return extended; }
    public boolean supportsSpreadsheet() { return extended; }

    public List<ApplicationMode> applications() {
        return extended
                ? com.codex.fx991.core.Compat.list(ApplicationMode.CALCULATE, ApplicationMode.STATISTICS,
                        ApplicationMode.DISTRIBUTION, ApplicationMode.SPREADSHEET,
                        ApplicationMode.FUNCTION_TABLE, ApplicationMode.EQUATION,
                        ApplicationMode.INEQUALITY, ApplicationMode.COMPLEX,
                        ApplicationMode.BASE_N, ApplicationMode.MATRIX,
                        ApplicationMode.VECTOR, ApplicationMode.RATIO)
                : com.codex.fx991.core.Compat.list(ApplicationMode.CALCULATE, ApplicationMode.STATISTICS,
                        ApplicationMode.FUNCTION_TABLE, ApplicationMode.EQUATION,
                        ApplicationMode.INEQUALITY, ApplicationMode.COMPLEX,
                        ApplicationMode.BASE_N, ApplicationMode.MATRIX,
                        ApplicationMode.VECTOR, ApplicationMode.RATIO);
    }
}
