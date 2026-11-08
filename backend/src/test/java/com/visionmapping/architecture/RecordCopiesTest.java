package com.visionmapping.architecture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.visionmapping.VisionMappingApplication;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.RecordComponent;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * BRD 13 EAC-06 (EJ-FR-21, Effective Java Items 17 and 50): every record with
 * a List, Set or Map component keeps its own copy. Changing the caller's
 * collection afterwards does not change the record, the record's collection
 * cannot be changed, and a null collection stays null so the JSON is unchanged.
 */
class RecordCopiesTest {

    private static final Object ELEMENT = "first";

    @Test
    void everyRecordWithACollectionKeepsItsOwnCopy() throws Exception {
        List<Class<?>> records = recordsWithCollections();
        assertThat(records).as("records with collection components").hasSizeGreaterThanOrEqualTo(70);

        Set<String> failures = new TreeSet<>();
        for (Class<?> type : records) {
            String problem = check(type);
            if (problem != null) {
                failures.add(type.getName() + ": " + problem);
            }
        }
        assertThat(failures).isEmpty();
    }

    @Test
    void theHelperKeepsNullsAndRefusesChanges() {
        assertThat(com.visionmapping.util.Copies.list(null)).isNull();
        assertThat(com.visionmapping.util.Copies.set(null)).isNull();
        assertThat(com.visionmapping.util.Copies.map(null)).isNull();
        List<String> withNull = new ArrayList<>();
        withNull.add(null);
        assertThat(com.visionmapping.util.Copies.list(withNull)).containsExactly((String) null);
        assertThatThrownBy(() -> com.visionmapping.util.Copies.list(List.of("a")).add("b"))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    private static String check(Class<?> type) throws ReflectiveOperationException {
        RecordComponent[] components = type.getRecordComponents();
        Class<?>[] types = Stream.of(components).map(RecordComponent::getType).toArray(Class<?>[]::new);
        Constructor<?> constructor = type.getDeclaredConstructor(types);
        constructor.setAccessible(true);

        Object[] filled = new Object[components.length];
        Object[] nulls = new Object[components.length];
        for (int i = 0; i < components.length; i++) {
            filled[i] = sample(types[i]);
            nulls[i] = isCollection(types[i]) ? null : sample(types[i]);
        }
        Object record;
        Object empty;
        try {
            record = constructor.newInstance(filled);
            empty = constructor.newInstance(nulls);
        } catch (InvocationTargetException exception) {
            return "could not be built for the test: " + exception.getCause();
        }

        for (int i = 0; i < components.length; i++) {
            if (!isCollection(types[i])) {
                continue;
            }
            String name = components[i].getName();
            components[i].getAccessor().setAccessible(true);
            Object held = components[i].getAccessor().invoke(record);
            if (held == filled[i]) {
                return name + " keeps the caller's collection";
            }
            grow(filled[i]);
            if (size(held) != 1) {
                return name + " changed when the caller's collection changed";
            }
            try {
                grow(held);
                return name + " can be changed through the record";
            } catch (UnsupportedOperationException expected) {
                // The copy is unmodifiable.
            }
            if (components[i].getAccessor().invoke(empty) != null) {
                return name + " turned a null collection into a value";
            }
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private static void grow(Object collection) {
        if (collection instanceof Map<?, ?> map) {
            ((Map<Object, Object>) map).put("second", "value");
        } else {
            ((Collection<Object>) collection).add("second");
        }
    }

    private static int size(Object collection) {
        return collection instanceof Map<?, ?> map ? map.size() : ((Collection<?>) collection).size();
    }

    private static boolean isCollection(Class<?> type) {
        return type == List.class || type == Set.class || type == Map.class;
    }

    private static Object sample(Class<?> type) {
        if (type == List.class) {
            return new ArrayList<>(List.of(ELEMENT));
        }
        if (type == Set.class) {
            return new HashSet<>(Set.of(ELEMENT));
        }
        if (type == Map.class) {
            return new HashMap<>(Map.of(ELEMENT, "value"));
        }
        if (type == boolean.class) {
            return false;
        }
        if (type == int.class) {
            return 0;
        }
        if (type == long.class) {
            return 0L;
        }
        if (type == double.class) {
            return 0d;
        }
        return null;
    }

    private static List<Class<?>> recordsWithCollections() throws Exception {
        Path root = Path.of(VisionMappingApplication.class.getProtectionDomain().getCodeSource().getLocation().toURI());
        List<Class<?>> found = new ArrayList<>();
        try (Stream<Path> files = Files.walk(root)) {
            for (Path file : files.filter(path -> path.toString().endsWith(".class")).toList()) {
                String name = root.relativize(file).toString().replace(file.getFileSystem().getSeparator(), ".")
                        .replaceAll("\\.class$", "");
                Class<?> type = Class.forName(name, false, RecordCopiesTest.class.getClassLoader());
                if (type.isRecord() && Stream.of(type.getRecordComponents()).anyMatch(c -> isCollection(c.getType()))) {
                    found.add(type);
                }
            }
        }
        return found;
    }
}
