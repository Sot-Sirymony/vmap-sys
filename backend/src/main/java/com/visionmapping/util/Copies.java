package com.visionmapping.util;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.lang.Nullable;

/**
 * Defensive copies for record components (BRD 13 EJ-FR-21, Effective Java
 * Items 17 and 50). A record that keeps the caller's list changes when the
 * caller changes the list later; a copy cannot.
 *
 * <p>Unlike {@code List.copyOf}, these keep a null collection as null and
 * allow null elements, so the JSON the API sends and accepts stays exactly
 * as it was. The copies are unmodifiable and keep their order.
 */
public final class Copies {

    private Copies() {
    }

    @Nullable
    public static <T> List<T> list(@Nullable List<T> source) {
        return source == null ? null : Collections.unmodifiableList(new ArrayList<>(source));
    }

    @Nullable
    public static <T> Set<T> set(@Nullable Set<T> source) {
        return source == null ? null : Collections.unmodifiableSet(new LinkedHashSet<>(source));
    }

    @Nullable
    public static <K, V> Map<K, V> map(@Nullable Map<K, V> source) {
        return source == null ? null : Collections.unmodifiableMap(new LinkedHashMap<>(source));
    }
}
