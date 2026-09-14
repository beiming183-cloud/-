package com.codex.fx991.core;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Runtime helpers that keep the shared core inside the Android 8 API surface. */
public final class Compat {
    private Compat() { }

    @SafeVarargs
    public static <T> List<T> list(T... values) {
        T[] copy = values.clone();
        return Collections.unmodifiableList(Arrays.asList(copy));
    }

    public static <T> List<T> copyList(Collection<? extends T> values) {
        return Collections.unmodifiableList(new ArrayList<>(values));
    }

    public static <K, V> Map<K, V> emptyMap() {
        return Collections.emptyMap();
    }

    public static <K, V> Map<K, V> copyMap(Map<? extends K, ? extends V> values) {
        return Collections.unmodifiableMap(new HashMap<>(values));
    }

    public static boolean isBlank(CharSequence value) {
        if (value == null) return true;
        for (int index = 0; index < value.length(); index++) {
            if (!Character.isWhitespace(value.charAt(index))) return false;
        }
        return true;
    }

    public static String repeat(String value, int count) {
        if (count < 0) throw new IllegalArgumentException("count");
        StringBuilder result = new StringBuilder(value.length() * count);
        for (int index = 0; index < count; index++) result.append(value);
        return result.toString();
    }

    /**
     * Android did not expose {@code Math.fma} until API 33.  The calculator
     * does not require a single-rounding fused operation, so use the portable
     * multiply/add form instead of leaking a platform API into the core.
     */
    public static double multiplyAdd(double left, double right, double addend) {
        return left * right + addend;
    }

    /** Java-8-compatible join helper used by Android-facing core code. */
    public static String join(String delimiter, List<String> values) {
        StringBuilder result = new StringBuilder();
        for (int index = 0; index < values.size(); index++) {
            if (index > 0) result.append(delimiter);
            result.append(values.get(index));
        }
        return result.toString();
    }
}
