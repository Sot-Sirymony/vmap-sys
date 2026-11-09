package com.visionmapping.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import com.visionmapping.VisionMappingApplication;
import com.visionmapping.entity.Dream;
import com.visionmapping.entity.Goal;
import com.visionmapping.entity.TaskItem;
import com.visionmapping.entity.VisionArea;
import com.visionmapping.entity.VisionStep;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * BRD 13 EJ-FR-02: guard rails that keep the backend in line with
 * <i>Effective Java</i>. Plain JUnit, no extra library (BRD 13 Q1).
 *
 * <p>Two kinds of rule:
 * <ul>
 *   <li><b>Hard rules</b> already hold and must keep holding.</li>
 *   <li><b>Ratchet rules</b> list today's known exceptions. A new violation
 *       fails the build, and so does a fixed one that is still listed, so the
 *       list only ever shrinks. Each list names the BRD 13 phase that empties it.</li>
 * </ul>
 */
class EffectiveJavaRulesTest {

    private static final Path MAIN_SOURCES = Path.of("src/main/java");
    private static final String BASE_PACKAGE = "com.visionmapping";

    /** The method hierarchy whose lifecycle fields must change only through named methods. */
    private static final List<Class<?>> CORE_ENTITIES =
            List.of(VisionArea.class, Dream.class, Goal.class, VisionStep.class, TaskItem.class);

    private static final Set<String> LIFECYCLE_SETTERS =
            Set.of("setStatus", "setProgressPercent", "setArchived", "setBlockerReason", "setCompletedAt");

    /** Emptied in Phase 2 (8 Nov 2026); kept as a ratchet so none come back. */
    private static final Set<String> KNOWN_PUBLIC_LIFECYCLE_SETTERS = Set.of();

    /**
     * Reading the system clock directly makes time-dependent code hard to test
     * (Items 5 and 59). These files may keep doing it for good: JPA callbacks
     * cannot take a bean, and error bodies only stamp the moment they are sent.
     */
    // An error answer's timestamp is set in one place, ErrorResponse.of (V10 SEC-10-02).
    private static final Set<String> CLOCK_ALLOWED = Set.of(
            "entity/BaseAuditableEntity.java",
            "exception/ErrorResponse.java");

    /** Emptied in Phase 5 (8 Nov 2026); kept as a ratchet so none come back. */
    private static final Set<String> KNOWN_DIRECT_CLOCK_READS = Set.of();

    private static final Pattern DIRECT_CLOCK_READ = Pattern.compile(
            "\\b(Instant|LocalDate|LocalDateTime|LocalTime|ZonedDateTime|OffsetDateTime)\\.now\\(\\s*\\)"
                    + "|System\\.currentTimeMillis\\(\\)");

    private static final Pattern LOMBOK_EQUALITY = Pattern.compile("^\\s*@(Data|EqualsAndHashCode|Value)\\b", Pattern.MULTILINE);

    private static List<Class<?>> classes;

    @BeforeAll
    static void loadClasses() throws IOException, URISyntaxException {
        Path root = Path.of(VisionMappingApplication.class.getProtectionDomain().getCodeSource().getLocation().toURI());
        List<Class<?>> found = new ArrayList<>();
        try (Stream<Path> files = Files.walk(root)) {
            for (Path file : files.filter(path -> path.toString().endsWith(".class")).toList()) {
                String name = root.relativize(file).toString().replace(file.getFileSystem().getSeparator(), ".")
                        .replaceAll("\\.class$", "");
                if (name.startsWith(BASE_PACKAGE) && !name.endsWith("package-info")) {
                    found.add(load(name));
                }
            }
        }
        classes = List.copyOf(found);
        assertThat(classes).as("compiled main classes").hasSizeGreaterThan(300);
    }

    // ---- Hard rules -------------------------------------------------------

    /** Item 5: dependencies come in through the constructor, never straight into a field. */
    @Test
    void noAutowiredFields() {
        Set<String> violations = new TreeSet<>();
        for (Class<?> type : classes) {
            for (Field field : type.getDeclaredFields()) {
                if (field.isAnnotationPresent(Autowired.class)) {
                    violations.add(type.getSimpleName() + "." + field.getName());
                }
            }
        }
        assertThat(violations).as("@Autowired fields (use constructor injection)").isEmpty();
    }

    /**
     * Items 4 and 19: a class with only static members cannot be created or
     * extended — it is final and its constructors are private.
     */
    @Test
    void utilityClassesAreFinalWithPrivateConstructors() {
        Set<String> violations = new TreeSet<>();
        for (Class<?> type : classes) {
            if (isUtilityClass(type)) {
                boolean finalClass = Modifier.isFinal(type.getModifiers());
                boolean privateConstructors = Stream.of(type.getDeclaredConstructors())
                        .allMatch(constructor -> Modifier.isPrivate(constructor.getModifiers()));
                if (!finalClass || !privateConstructors) {
                    violations.add(type.getName());
                }
            }
        }
        assertThat(violations).as("utility classes that are not final with private constructors").isEmpty();
    }

    /**
     * Items 10 and 11: entities keep identity equality. Field-based equals and
     * hashCode on a JPA entity break when lazy fields load or the id is set on save.
     */
    @Test
    void entitiesDoNotUseLombokFieldEquality() throws IOException {
        Set<String> violations = new TreeSet<>();
        try (Stream<Path> files = Files.list(MAIN_SOURCES.resolve("com/visionmapping/entity"))) {
            for (Path file : files.filter(path -> path.toString().endsWith(".java")).toList()) {
                if (LOMBOK_EQUALITY.matcher(Files.readString(file)).find()) {
                    violations.add(file.getFileName().toString());
                }
            }
        }
        assertThat(violations).as("entities with @Data, @EqualsAndHashCode or @Value").isEmpty();
    }

    /** Item 85: no class opts in to Java serialization beyond what its supertype forces. */
    @Test
    void noClassImplementsSerializableDirectly() {
        Set<String> violations = new TreeSet<>();
        for (Class<?> type : classes) {
            if (List.of(type.getInterfaces()).contains(java.io.Serializable.class)) {
                violations.add(type.getName());
            }
        }
        assertThat(violations).as("classes that implement Serializable directly").isEmpty();
    }

    /**
     * Item 2: a builder does not fail to compile when a field is forgotten, the
     * way a positional constructor does. So every {@code XResponse.builder()}
     * chain must set every component of {@code XResponse}.
     */
    @Test
    void responseBuildersSetEveryComponent() throws IOException {
        Pattern chainStart = Pattern.compile("\\b(\\w+Response)\\.builder\\(\\)");
        Pattern setter = Pattern.compile("^\\s*\\.(\\w+)\\(");
        Set<String> violations = new TreeSet<>();
        int chains = 0;
        try (Stream<Path> files = Files.walk(MAIN_SOURCES.resolve("com/visionmapping"))) {
            for (Path file : files.filter(path -> path.toString().endsWith(".java")).toList()) {
                List<String> lines = Files.readAllLines(file);
                for (int i = 0; i < lines.size(); i++) {
                    var start = chainStart.matcher(lines.get(i));
                    if (!start.find()) {
                        continue;
                    }
                    chains++;
                    Class<?> record = load(BASE_PACKAGE + ".dto.response." + start.group(1));
                    Set<String> set = new TreeSet<>();
                    for (int j = i + 1; j < lines.size() && !lines.get(j).contains(".build()"); j++) {
                        var call = setter.matcher(lines.get(j));
                        if (call.find()) {
                            set.add(call.group(1));
                        }
                    }
                    Set<String> missing = new TreeSet<>();
                    for (var component : record.getRecordComponents()) {
                        if (!set.contains(component.getName())) {
                            missing.add(component.getName());
                        }
                    }
                    if (!missing.isEmpty()) {
                        violations.add(file.getFileName() + ":" + (i + 1) + " " + start.group(1) + " misses " + missing);
                    }
                }
            }
        }
        assertThat(chains).as("builder chains found").isGreaterThanOrEqualTo(9);
        assertThat(violations).as("builder chains that leave a component unset").isEmpty();
    }

    /**
     * Items 54 and 55: a public method does not return null. Return Optional or
     * an empty collection instead; the few deliberate exceptions (null-in,
     * null-out link lookups) must say so with {@code @Nullable}.
     */
    @Test
    void publicMethodsDoNotReturnNullUnlessMarked() throws IOException {
        Pattern signature = Pattern.compile(
                "^(\\s{4}|\\s{8})((?:public|protected|private|static|final|synchronized)\\s+)*(<[^>]+>\\s+)?([\\w.]+(?:<[^()]*>)?(?:\\[\\])?)\\s+(\\w+)\\(");
        // "return null;", or a ternary with a null branch, but not "return new X(a ? null : b)" or "return x != null;".
        Pattern returnsNull = Pattern.compile(
                "^\\s*return\\s+(?!new\\b)(?![^;]*[!=]=\\s*null\\s*;)[^;]*?(\\bnull\\s*;|\\?\\s*null\\s*:|:\\s*null\\s*;)");
        Set<String> keywords = Set.of("return", "if", "for", "while", "switch", "new", "throw", "else", "case", "try", "catch");
        Set<String> violations = new TreeSet<>();
        try (Stream<Path> files = Files.walk(MAIN_SOURCES.resolve("com/visionmapping"))) {
            for (Path file : files.filter(path -> path.toString().endsWith(".java")).toList()) {
                List<String> lines = Files.readAllLines(file);
                for (int i = 0; i < lines.size(); i++) {
                    if (!returnsNull.matcher(lines.get(i)).find()) {
                        continue;
                    }
                    for (int j = i; j >= 0; j--) {
                        var method = signature.matcher(lines.get(j));
                        if (method.find() && !keywords.contains(method.group(4)) && !keywords.contains(method.group(5))) {
                            boolean isPublic = lines.get(j).strip().startsWith("public ");
                            boolean marked = j > 0 && lines.get(j - 1).strip().equals("@Nullable");
                            if (isPublic && !marked && !lines.get(i).contains("->")) {
                                violations.add(file.getFileName() + ":" + (i + 1) + " " + method.group(5) + "()");
                            }
                            break;
                        }
                    }
                }
            }
        }
        assertThat(violations).as("public methods returning null without @Nullable").isEmpty();
    }

    // ---- Ratchet rules ----------------------------------------------------

    /** Items 15, 17 and 76: lifecycle fields change only through methods that keep the entity valid. */
    @Test
    void coreEntitiesHideLifecycleSetters() {
        Set<String> violations = new TreeSet<>();
        for (Class<?> entity : CORE_ENTITIES) {
            for (Method method : entity.getMethods()) {
                if (LIFECYCLE_SETTERS.contains(method.getName()) && Modifier.isPublic(method.getModifiers())) {
                    violations.add(entity.getSimpleName() + "." + method.getName());
                }
            }
        }
        assertRatchet("public lifecycle setters on core entities", violations, KNOWN_PUBLIC_LIFECYCLE_SETTERS);
    }

    /** Items 5 and 59: time comes from the injected Clock so tests can fix it. */
    @Test
    void timeComesFromAnInjectedClock() throws IOException {
        Path base = MAIN_SOURCES.resolve("com/visionmapping");
        Set<String> violations = new TreeSet<>();
        try (Stream<Path> files = Files.walk(base)) {
            for (Path file : files.filter(path -> path.toString().endsWith(".java")).toList()) {
                String relative = base.relativize(file).toString().replace(file.getFileSystem().getSeparator(), "/");
                if (!CLOCK_ALLOWED.contains(relative) && readsClockDirectly(file)) {
                    violations.add(relative);
                }
            }
        }
        assertRatchet("direct clock reads", violations, KNOWN_DIRECT_CLOCK_READS);
    }

    // ---- Helpers ----------------------------------------------------------

    private static void assertRatchet(String rule, Set<String> actual, Set<String> known) {
        Set<String> added = new TreeSet<>(actual);
        added.removeAll(known);
        Set<String> fixed = new TreeSet<>(known);
        fixed.removeAll(actual);
        assertThat(added).as("new " + rule + " (fix them, do not add them to the known list)").isEmpty();
        assertThat(fixed).as("fixed " + rule + " still listed as known (remove them from the list)").isEmpty();
    }

    private static boolean isUtilityClass(Class<?> type) {
        if (type.isInterface() || type.isEnum() || type.isRecord() || type.isAnnotation()
                || type.isAnonymousClass() || type.isLocalClass() || type.isSynthetic()
                || type.getAnnotations().length > 0 || type.getSuperclass() != Object.class) {
            return false;
        }
        List<Method> methods = Stream.of(type.getDeclaredMethods()).filter(method -> !method.isSynthetic()).toList();
        boolean onlyStaticMethods = !methods.isEmpty()
                && methods.stream().allMatch(method -> Modifier.isStatic(method.getModifiers()));
        boolean onlyStaticFields = Stream.of(type.getDeclaredFields())
                .filter(field -> !field.isSynthetic())
                .allMatch(field -> Modifier.isStatic(field.getModifiers()));
        return onlyStaticMethods && onlyStaticFields;
    }

    private static boolean readsClockDirectly(Path file) throws IOException {
        for (String line : Files.readAllLines(file)) {
            String code = line.strip();
            if (!code.startsWith("//") && !code.startsWith("*") && DIRECT_CLOCK_READ.matcher(code).find()) {
                return true;
            }
        }
        return false;
    }

    private static Class<?> load(String name) {
        try {
            return Class.forName(name, false, EffectiveJavaRulesTest.class.getClassLoader());
        } catch (ClassNotFoundException exception) {
            throw new IllegalStateException("Cannot load compiled class " + name, exception);
        }
    }
}
