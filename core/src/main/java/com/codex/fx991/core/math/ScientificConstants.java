package com.codex.fx991.core.math;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** The 47 CODATA-2018 constants grouped exactly as the CN CW catalog. */
public final class ScientificConstants {
    public record Constant(String symbol, String name, double value, String unit) {}

    private static final Map<String, List<Constant>> CATEGORIES = new LinkedHashMap<>();

    static {
        CATEGORIES.put("通用常数", com.codex.fx991.core.Compat.list(
                c("h", "普朗克常数", 6.62607015e-34, "J·s"),
                c("ℏ", "约化普朗克常数", 1.054571817e-34, "J·s"),
                c("c", "真空光速", 299792458.0, "m/s"),
                c("ε0", "真空介电常数", 8.8541878128e-12, "F/m"),
                c("μ0", "真空磁导率", 1.25663706212e-6, "N/A²"),
                c("Z0", "真空特性阻抗", 376.730313668, "Ω"),
                c("G", "万有引力常数", 6.67430e-11, "m³·kg⁻¹·s⁻²"),
                c("lP", "普朗克长度", 1.616255e-35, "m"),
                c("tP", "普朗克时间", 5.391247e-44, "s")));
        CATEGORIES.put("电磁常数", com.codex.fx991.core.Compat.list(
                c("μN", "核磁子", 5.0507837461e-27, "J/T"),
                c("μB", "玻尔磁子", 9.2740100783e-24, "J/T"),
                c("e", "元电荷", 1.602176634e-19, "C"),
                c("Φ0", "磁通量子", 2.067833848e-15, "Wb"),
                c("G0", "电导量子", 7.748091729e-5, "S"),
                c("KJ", "约瑟夫森常数", 4.835978484e14, "Hz/V"),
                c("RK", "冯·克里青常数", 25812.80745, "Ω")));
        CATEGORIES.put("原子与核常数", com.codex.fx991.core.Compat.list(
                c("mp", "质子质量", 1.67262192369e-27, "kg"),
                c("mn", "中子质量", 1.67492749804e-27, "kg"),
                c("me", "电子质量", 9.1093837015e-31, "kg"),
                c("mμ", "μ子质量", 1.883531627e-28, "kg"),
                c("a0", "玻尔半径", 5.29177210903e-11, "m"),
                c("α", "精细结构常数", 7.2973525693e-3, ""),
                c("re", "经典电子半径", 2.8179403262e-15, "m"),
                c("λC", "电子康普顿波长", 2.42631023867e-12, "m"),
                c("γp", "质子旋磁比", 2.6752218744e8, "s⁻¹·T⁻¹"),
                c("λCp", "质子康普顿波长", 1.32140985539e-15, "m"),
                c("λCn", "中子康普顿波长", 1.31959090581e-15, "m"),
                c("R∞", "里德伯常数", 10973731.568160, "m⁻¹"),
                c("μp", "质子磁矩", 1.41060679736e-26, "J/T"),
                c("μe", "电子磁矩", -9.2847647043e-24, "J/T"),
                c("μn", "中子磁矩", -9.6623651e-27, "J/T"),
                c("μμ", "μ子磁矩", -4.49044830e-26, "J/T"),
                c("mτ", "τ子质量", 3.16754e-27, "kg")));
        CATEGORIES.put("物理与化学常数", com.codex.fx991.core.Compat.list(
                c("mu", "统一原子质量单位", 1.66053906660e-27, "kg"),
                c("F", "法拉第常数", 96485.33212, "C/mol"),
                c("NA", "阿伏伽德罗常数", 6.02214076e23, "mol⁻¹"),
                c("k", "玻尔兹曼常数", 1.380649e-23, "J/K"),
                c("Vm", "理想气体摩尔体积", 0.02271095464, "m³/mol"),
                c("R", "摩尔气体常数", 8.314462618, "J·mol⁻¹·K⁻¹"),
                c("c1", "第一辐射常数", 3.741771852e-16, "W·m²"),
                c("c2", "第二辐射常数", 1.438776877e-2, "m·K"),
                c("σ", "斯忒藩-玻尔兹曼常数", 5.670374419e-8, "W·m⁻²·K⁻⁴")));
        CATEGORIES.put("采用值", com.codex.fx991.core.Compat.list(
                c("gn", "标准重力加速度", 9.80665, "m/s²"),
                c("atm", "标准大气压", 101325.0, "Pa"),
                c("RK-90", "常规冯·克里青常数", 25812.807, "Ω"),
                c("KJ-90", "常规约瑟夫森常数", 4.835979e14, "Hz/V")));
        CATEGORIES.put("其他", com.codex.fx991.core.Compat.list(c("t", "摄氏温标偏移", 273.15, "K")));
    }

    private ScientificConstants() {}

    public static Map<String, List<Constant>> categories() {
        return Collections.unmodifiableMap(new LinkedHashMap<>(CATEGORIES));
    }

    public static Constant bySymbol(String symbol) {
        return CATEGORIES.values().stream().flatMap(List::stream)
                .filter(value -> value.symbol().equals(symbol)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown constant: " + symbol));
    }

    private static Constant c(String symbol, String name, double value, String unit) {
        return new Constant(symbol, name, value, unit);
    }
}
