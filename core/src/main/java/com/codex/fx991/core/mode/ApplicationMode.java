package com.codex.fx991.core.mode;

public enum ApplicationMode {
    CALCULATE("计算"),
    STATISTICS("统计"),
    DISTRIBUTION("分布"),
    SPREADSHEET("数据表格"),
    FUNCTION_TABLE("函数表格"),
    EQUATION("方程"),
    INEQUALITY("不等式"),
    COMPLEX("复数"),
    BASE_N("基数"),
    MATRIX("矩阵"),
    VECTOR("向量"),
    RATIO("比例");

    private final String chineseName;
    ApplicationMode(String chineseName) { this.chineseName = chineseName; }
    public String chineseName() { return chineseName; }
}
