package com.codex.fx991.core.math;

import java.util.Collections;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** NIST-SP 811-style conversions listed by the manual. */
public final class UnitConverter {
    private static final Map<String, Conversion> CONVERSIONS = new LinkedHashMap<>();

    static {
        pair("in→cm", "cm→in", 2.54);
        pair("ft→m", "m→ft", 0.3048);
        pair("yd→m", "m→yd", 0.9144);
        pair("mile→km", "km→mile", 1.609344);
        pair("n mile→m", "m→n mile", 1852.0);
        pair("pc→km", "km→pc", 3.0856775814913673e13);
        pair("acre→m²", "m²→acre", 4046.8564224);
        pair("gal(US)→L", "L→gal(US)", 3.785411784);
        pair("gal(UK)→L", "L→gal(UK)", 4.54609);
        pair("oz→g", "g→oz", 28.349523125);
        pair("lb→kg", "kg→lb", 0.45359237);
        pair("km/h→m/s", "m/s→km/h", 1.0 / 3.6);
        pair("atm→Pa", "Pa→atm", 101325.0);
        pair("mmHg→Pa", "Pa→mmHg", 133.322387415);
        pair("kgf/cm²→Pa", "Pa→kgf/cm²", 98066.5);
        pair("lbf/in²→kPa", "kPa→lbf/in²", 6.894757293168);
        pair("kgf·m→J", "J→kgf·m", 9.80665);
        pair("cal15→J", "J→cal15", 4.1855);
        pair("hp→kW", "kW→hp", 0.7456998715822702);
        CONVERSIONS.put("°F→°C", value -> (value - 32.0) * 5.0 / 9.0);
        CONVERSIONS.put("°C→°F", value -> value * 9.0 / 5.0 + 32.0);
    }

    private UnitConverter() {}

    public static double convert(String command, double value) {
        Conversion conversion = CONVERSIONS.get(command);
        if (conversion == null) throw new IllegalArgumentException("Unknown conversion: " + command);
        return conversion.apply(value);
    }

    public static Map<String, Conversion> catalog() {
        return Collections.unmodifiableMap(new LinkedHashMap<>(CONVERSIONS));
    }

    /** Nine manual catalog groups, preserving the command order on the handheld. */
    public static Map<String, List<String>> categories() {
        String[] names = {"长度", "面积", "体积", "质量", "速度", "压强", "能量", "功率", "温度"};
        int[] sizes = {12, 2, 4, 4, 2, 8, 4, 2, 2};
        List<String> commands = new ArrayList<>(CONVERSIONS.keySet());
        Map<String, List<String>> groups = new LinkedHashMap<>();
        int offset = 0;
        for (int index = 0; index < names.length; index++) {
            groups.put(names[index], Collections.unmodifiableList(new ArrayList<>(
                    commands.subList(offset, offset + sizes[index]))));
            offset += sizes[index];
        }
        return Collections.unmodifiableMap(groups);
    }

    private static void pair(String forwardName, String reverseName, double forwardFactor) {
        CONVERSIONS.put(forwardName, value -> value * forwardFactor);
        CONVERSIONS.put(reverseName, value -> value / forwardFactor);
    }

    @FunctionalInterface
    public interface Conversion { double apply(double value); }
}
