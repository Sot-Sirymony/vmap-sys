package com.visionmapping.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import com.visionmapping.VisionMappingApplication;
import com.visionmapping.service.stepup.StepUpService;
import com.visionmapping.web.ApiPaths;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;
import java.io.IOException;
import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Parameter;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.WildcardType;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Predicate;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.data.domain.Page;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * BRD 33 (CS-FR-19): the REST controller standard, checked on every build.
 * Plain JUnit with reflection, no extra library (CS-NFR-02, as BRD 13 Q1).
 *
 * <p>Two kinds of rule, as in {@link EffectiveJavaRulesTest}:
 * <ul>
 *   <li><b>Hard rules</b> already hold and must keep holding.</li>
 *   <li><b>Ratchet rules</b> list today's known exceptions by controller. A new
 *       violation fails the build, and so does a fixed controller that is still
 *       listed, so the lists only ever shrink. Each list names the BRD 33 phase
 *       that empties it.</li>
 * </ul>
 */
class ControllerRulesTest {

    private static final String BASE_PACKAGE = "com.visionmapping";
    private static final String CONTROLLER_PACKAGE = BASE_PACKAGE + ".controller";
    private static final Path CONTROLLER_SOURCES = Path.of("src/main/java/com/visionmapping/controller");
    private static final Set<String> STEP_UP_HEADERS = Set.of(StepUpService.HEADER, StepUpService.CONFIRM_HEADER);

    /** D-33-1: the one controller that stays unversioned. */
    private static final Set<String> UNVERSIONED = Set.of("HealthController");

    /** D-33-5: at most three injected collaborators. */
    private static final int MAX_COLLABORATORS = 3;

    // ---- Ratchet lists ----------------------------------------------------
    // Phases 2 (plan core, rhythm, journals), 3 (AI, Streamline, reports, files)
    // and 4 (account, sign-in, admin, coaching) empty the lists below; phase 6
    // empties the split and package lists. See docs/CONTROLLER_AUDIT_V33.md.

    /** D-33-1: a class-level mapping under /api/v1, one root, no full paths on methods. */
    private static final Set<String> KNOWN_UNVERSIONED = Set.of();

    /** D-33-4: class-level produces = application/json. */
    private static final Set<String> KNOWN_WITHOUT_JSON_PRODUCES = Set.of();

    /** D-33-4: consumes on every method with a request body. */
    private static final Set<String> KNOWN_BODIES_WITHOUT_CONSUMES = Set.of();

    /** D-33-6: every handler answers ResponseEntity. */
    private static final Set<String> KNOWN_RAW_ANSWERS = Set.of();

    /** D-33-6: no @ResponseStatus on handlers. */
    private static final Set<String> KNOWN_RESPONSE_STATUS = Set.of();

    /** D-33-7: no bare true, number or string as an answer. */
    private static final Set<String> KNOWN_BARE_VALUES = Set.of();

    /** D-33-8: @Validated on the class. */
    private static final Set<String> KNOWN_NOT_VALIDATED = Set.of();

    /** D-33-8: @Valid on every request body. */
    private static final Set<String> KNOWN_UNCHECKED_BODIES = Set.of();

    /** D-33-8: @Positive on every numeric path id. */
    private static final Set<String> KNOWN_UNCHECKED_IDS = Set.of();

    /** D-33-12: no step-up header parameters (phase 4). */
    private static final Set<String> KNOWN_STEP_UP_HEADERS = Set.of();

    /** D-33-11: no try/catch and no SecurityContextHolder in a controller. */
    private static final Set<String> KNOWN_THICK = Set.of();

    /** D-33-5: at most three collaborators (phase 6). */
    private static final Set<String> KNOWN_TOO_MANY_COLLABORATORS = Set.of(
            "AiPlanController",
            "SavedReportController");

    /** D-33-16: @Tag on the class and @Operation on every handler. */
    private static final Set<String> KNOWN_UNDOCUMENTED = Set.of();

    /** D-33-15: @PreAuthorize on every admin and coach handler (phase 4). */
    private static final Set<String> KNOWN_UNGUARDED_ADMIN_OR_COACH = Set.of();

    /** D-33-5: only @RestController classes in the controller package (phase 3 moves ServerSentEvents). */
    private static final Set<String> KNOWN_NON_CONTROLLERS = Set.of();

    /** D-33-9: raw Page answers, which phase 2 turns into PagedResponse. */
    private static final Set<String> KNOWN_RAW_PAGES = Set.of();

    /** D-33-14: setters that break a dependency cycle (phase 6 removes the cycles). */
    private static final Set<String> KNOWN_SETTER_INJECTION = Set.of(
            "DashboardService.setStreamlineDashboard()",
            "EntityLookup.setSecurityEvents()",
            "StreamlineReviewService.setSecurityEvents()");

    private static List<Class<?>> allClasses;
    private static List<Class<?>> controllerPackage;
    static List<Class<?>> controllers;

    @BeforeAll
    static void loadClasses() throws IOException, URISyntaxException, ClassNotFoundException {
        Path root = Path.of(VisionMappingApplication.class.getProtectionDomain().getCodeSource().getLocation().toURI());
        List<Class<?>> found = new ArrayList<>();
        try (Stream<Path> files = Files.walk(root)) {
            for (Path file : files.filter(path -> path.toString().endsWith(".class")).toList()) {
                String name = root.relativize(file).toString().replace(file.getFileSystem().getSeparator(), ".")
                        .replaceAll("\\.class$", "");
                if (name.startsWith(BASE_PACKAGE) && !name.endsWith("package-info")) {
                    found.add(Class.forName(name, false, ControllerRulesTest.class.getClassLoader()));
                }
            }
        }
        allClasses = List.copyOf(found);
        controllerPackage = allClasses.stream()
                .filter(type -> type.getPackageName().equals(CONTROLLER_PACKAGE) && type.getEnclosingClass() == null)
                .filter(type -> !type.isSynthetic() && !type.isAnonymousClass())
                .toList();
        controllers = controllerPackage.stream().filter(type -> type.isAnnotationPresent(RestController.class)).toList();
        assertThat(controllers).as("REST controllers").hasSizeGreaterThanOrEqualTo(69);
    }

    // ---- Hard rules -------------------------------------------------------

    /** D-33-7: no JPA entity in a controller signature; enums under entity.enums are allowed. */
    @Test
    void noEntityInAnySignature() {
        Set<String> violations = new TreeSet<>();
        for (Class<?> controller : controllers) {
            for (Method handler : handlers(controller)) {
                List<Type> types = new ArrayList<>(Arrays.asList(handler.getGenericParameterTypes()));
                types.add(handler.getGenericReturnType());
                for (Type type : types) {
                    if (mentions(type, ControllerRulesTest::isEntity)) {
                        violations.add(controller.getSimpleName() + "." + handler.getName());
                    }
                }
            }
        }
        assertThat(violations).as("controller signatures that expose an entity").isEmpty();
    }

    /** D-33-11: a controller never talks to a repository. */
    @Test
    void noRepositoryInAController() {
        Set<String> violations = new TreeSet<>();
        for (Class<?> controller : controllers) {
            for (Field field : controller.getDeclaredFields()) {
                if (field.getType().getSimpleName().endsWith("Repository")) {
                    violations.add(controller.getSimpleName() + "." + field.getName());
                }
            }
        }
        assertThat(violations).as("repositories injected into controllers").isEmpty();
    }

    /** D-33-14: no @Autowired field anywhere. An @Autowired constructor is still constructor injection. */
    @Test
    void noAutowiredFieldsAnywhere() {
        Set<String> violations = new TreeSet<>();
        for (Class<?> type : allClasses) {
            for (Field field : type.getDeclaredFields()) {
                if (field.isAnnotationPresent(Autowired.class)) {
                    violations.add(type.getSimpleName() + "." + field.getName());
                }
            }
        }
        assertThat(violations).as("@Autowired fields (use constructor injection)").isEmpty();
    }

    // ---- Ratchet rules ----------------------------------------------------

    /** D-33-9: a raw Spring Data Page is never an answer; use PagedResponse. */
    @Test
    void noRawPageAnswers() {
        assertRatchet("controllers answering a raw Page", violatingHandler(handler ->
                mentions(handler.getGenericReturnType(), Page.class::isAssignableFrom)), KNOWN_RAW_PAGES);
    }

    /** D-33-14: no @Autowired setter anywhere. */
    @Test
    void noSetterInjection() {
        Set<String> violations = new TreeSet<>();
        for (Class<?> type : allClasses) {
            for (Method method : type.getDeclaredMethods()) {
                if (method.isAnnotationPresent(Autowired.class)) {
                    violations.add(type.getSimpleName() + "." + method.getName() + "()");
                }
            }
        }
        assertRatchet("@Autowired setters (use constructor injection)", violations, KNOWN_SETTER_INJECTION);
    }

    @Test
    void everyControllerIsUnderVersionOne() {
        assertRatchet("controllers not under " + ApiPaths.V1, violating(controller -> {
            if (UNVERSIONED.contains(controller.getSimpleName())) {
                return false;
            }
            String[] roots = classPaths(controller);
            boolean oneVersionedRoot = roots.length == 1 && roots[0].startsWith(ApiPaths.V1 + "/");
            boolean fullPathOnAMethod = handlers(controller).stream()
                    .flatMap(handler -> Arrays.stream(mapping(handler).path()))
                    .anyMatch(path -> path.startsWith(ApiPaths.LEGACY + "/"));
            return !oneVersionedRoot || fullPathOnAMethod;
        }), KNOWN_UNVERSIONED);
    }

    @Test
    void everyControllerProducesJson() {
        assertRatchet("controllers without class-level produces JSON", violating(controller -> {
            RequestMapping root = AnnotatedElementUtils.findMergedAnnotation(controller, RequestMapping.class);
            return root == null || !Arrays.asList(root.produces()).contains(MediaType.APPLICATION_JSON_VALUE);
        }), KNOWN_WITHOUT_JSON_PRODUCES);
    }

    @Test
    void everyBodyDeclaresWhatItConsumes() {
        assertRatchet("controllers with a body method that declares no consumes", violatingHandler(handler ->
                hasParameter(handler, RequestBody.class) && mapping(handler).consumes().length == 0), KNOWN_BODIES_WITHOUT_CONSUMES);
    }

    @Test
    void everyHandlerAnswersAResponseEntity() {
        assertRatchet("controllers with handlers not answering ResponseEntity", violatingHandler(handler ->
                !ResponseEntity.class.isAssignableFrom(handler.getReturnType())), KNOWN_RAW_ANSWERS);
    }

    @Test
    void noHandlerUsesResponseStatus() {
        assertRatchet("controllers using @ResponseStatus on a handler", violatingHandler(handler ->
                AnnotatedElementUtils.hasAnnotation(handler, ResponseStatus.class)), KNOWN_RESPONSE_STATUS);
    }

    @Test
    void noHandlerAnswersABareValue() {
        assertRatchet("controllers answering a bare value", violatingHandler(handler ->
                isBareValue(answerType(handler))), KNOWN_BARE_VALUES);
    }

    @Test
    void everyControllerIsValidated() {
        assertRatchet("controllers without @Validated", violating(controller ->
                !controller.isAnnotationPresent(Validated.class)), KNOWN_NOT_VALIDATED);
    }

    @Test
    void everyBodyIsValid() {
        assertRatchet("controllers with a request body without @Valid", violatingHandler(handler ->
                Arrays.stream(handler.getParameters()).anyMatch(parameter -> parameter.isAnnotationPresent(RequestBody.class)
                        && !parameter.isAnnotationPresent(Valid.class) && !parameter.isAnnotationPresent(Validated.class))),
                KNOWN_UNCHECKED_BODIES);
    }

    @Test
    void everyNumericPathIdIsPositive() {
        assertRatchet("controllers with a numeric path id without @Positive", violatingHandler(handler ->
                Arrays.stream(handler.getParameters()).anyMatch(parameter -> parameter.isAnnotationPresent(PathVariable.class)
                        && isWholeNumber(parameter.getType()) && !isPositive(parameter))),
                KNOWN_UNCHECKED_IDS);
    }

    @Test
    void noControllerReadsStepUpHeaders() {
        assertRatchet("controllers reading step-up headers by hand", violatingHandler(handler ->
                Arrays.stream(handler.getParameters()).anyMatch(ControllerRulesTest::isStepUpHeader)), KNOWN_STEP_UP_HEADERS);
    }

    @Test
    void controllersAreThin() throws IOException {
        Set<String> violations = new TreeSet<>();
        for (Class<?> controller : controllerPackage) {
            Path source = CONTROLLER_SOURCES.resolve(controller.getSimpleName() + ".java");
            String text = Files.readString(source);
            if (text.contains("catch (") || text.contains("SecurityContextHolder")) {
                violations.add(controller.getSimpleName());
            }
        }
        assertRatchet("controllers that catch exceptions or read the security context", violations, KNOWN_THICK);
    }

    @Test
    void controllersHaveAtMostThreeCollaborators() {
        assertRatchet("controllers with more than " + MAX_COLLABORATORS + " collaborators", violating(controller ->
                Arrays.stream(controller.getDeclaredFields())
                        .filter(field -> !Modifier.isStatic(field.getModifiers()) && Modifier.isFinal(field.getModifiers()))
                        .count() > MAX_COLLABORATORS), KNOWN_TOO_MANY_COLLABORATORS);
    }

    @Test
    void everyControllerIsDocumented() {
        assertRatchet("controllers without @Tag or an @Operation on every handler", violating(controller ->
                !controller.isAnnotationPresent(Tag.class)
                        || handlers(controller).stream().anyMatch(handler -> !handler.isAnnotationPresent(Operation.class))),
                KNOWN_UNDOCUMENTED);
    }

    @Test
    void adminAndCoachHandlersArePreAuthorized() {
        assertRatchet("admin or coach controllers with a handler without @PreAuthorize", violating(controller -> {
            boolean classGuarded = controller.isAnnotationPresent(PreAuthorize.class);
            return handlers(controller).stream()
                    .filter(handler -> fullPaths(controller, handler).stream().anyMatch(ControllerRulesTest::isAdminOrCoachPath))
                    .anyMatch(handler -> !classGuarded && !handler.isAnnotationPresent(PreAuthorize.class));
        }), KNOWN_UNGUARDED_ADMIN_OR_COACH);
    }

    @Test
    void onlyControllersInTheControllerPackage() {
        Set<String> violations = new TreeSet<>();
        for (Class<?> type : controllerPackage) {
            if (!type.isAnnotationPresent(RestController.class)) {
                violations.add(type.getSimpleName());
            }
        }
        assertRatchet("non-controller classes in the controller package", violations, KNOWN_NON_CONTROLLERS);
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

    private static Set<String> violating(Predicate<Class<?>> breaksRule) {
        Set<String> violations = new TreeSet<>();
        for (Class<?> controller : controllers) {
            if (breaksRule.test(controller)) {
                violations.add(controller.getSimpleName());
            }
        }
        return violations;
    }

    private static Set<String> violatingHandler(Predicate<Method> breaksRule) {
        return violating(controller -> handlers(controller).stream().anyMatch(breaksRule));
    }

    /** The request-handling methods of a controller, in a stable order. */
    static List<Method> handlers(Class<?> controller) {
        return Arrays.stream(controller.getDeclaredMethods())
                .filter(method -> !method.isSynthetic() && !method.isBridge())
                .filter(method -> AnnotatedElementUtils.hasAnnotation(method, RequestMapping.class))
                .sorted((a, b) -> a.toGenericString().compareTo(b.toGenericString()))
                .toList();
    }

    static RequestMapping mapping(Method handler) {
        return AnnotatedElementUtils.findMergedAnnotation(handler, RequestMapping.class);
    }

    static String[] classPaths(Class<?> controller) {
        RequestMapping root = AnnotatedElementUtils.findMergedAnnotation(controller, RequestMapping.class);
        return root == null ? new String[0] : root.path();
    }

    /** Every full path a handler answers on. */
    static List<String> fullPaths(Class<?> controller, Method handler) {
        String[] roots = classPaths(controller);
        String[] paths = mapping(handler).path();
        List<String> roots1 = roots.length == 0 ? List.of("") : List.of(roots);
        List<String> paths1 = paths.length == 0 ? List.of("") : List.of(paths);
        List<String> full = new ArrayList<>();
        for (String root : roots1) {
            for (String path : paths1) {
                full.add(root + path);
            }
        }
        return full;
    }

    private static boolean isAdminOrCoachPath(String path) {
        return Stream.of(ApiPaths.LEGACY, ApiPaths.V1)
                .anyMatch(prefix -> path.startsWith(prefix + "/admin/") || path.equals(prefix + "/admin")
                        || path.startsWith(prefix + "/coach/") || path.equals(prefix + "/coach"));
    }

    private static boolean hasParameter(Method handler, Class<? extends Annotation> annotation) {
        return Arrays.stream(handler.getParameters()).anyMatch(parameter -> parameter.isAnnotationPresent(annotation));
    }

    private static boolean isStepUpHeader(Parameter parameter) {
        RequestHeader header = AnnotatedElementUtils.findMergedAnnotation(parameter, RequestHeader.class);
        return header != null && (STEP_UP_HEADERS.contains(header.name()) || STEP_UP_HEADERS.contains(header.value()));
    }

    /** The type of the body: the answer, or what a ResponseEntity holds. */
    private static Type answerType(Method handler) {
        Type answer = handler.getGenericReturnType();
        if (answer instanceof ParameterizedType parameterized && parameterized.getRawType() == ResponseEntity.class) {
            return parameterized.getActualTypeArguments()[0];
        }
        return answer;
    }

    private static boolean isBareValue(Type type) {
        if (!(type instanceof Class<?> plain)) {
            return false;
        }
        return plain == String.class || plain == Boolean.class || plain == boolean.class
                || (plain.isPrimitive() && plain != void.class) || Number.class.isAssignableFrom(plain);
    }

    /** @Positive, or a @Min of 1 or more (the Streamline stage is @Min(1) @Max(5)). */
    private static boolean isPositive(Parameter parameter) {
        Min min = parameter.getAnnotation(Min.class);
        return parameter.isAnnotationPresent(Positive.class) || (min != null && min.value() >= 1);
    }

    private static boolean isWholeNumber(Class<?> type) {
        return type == Long.class || type == long.class || type == Integer.class || type == int.class;
    }

    private static boolean isEntity(Class<?> type) {
        return type.getPackageName().equals(BASE_PACKAGE + ".entity");
    }

    /** Whether a type, or any type argument inside it, matches. */
    private static boolean mentions(Type type, Predicate<Class<?>> test) {
        if (type instanceof Class<?> plain) {
            return test.test(plain.isArray() ? plain.getComponentType() : plain);
        }
        if (type instanceof ParameterizedType parameterized) {
            return mentions(parameterized.getRawType(), test)
                    || Arrays.stream(parameterized.getActualTypeArguments()).anyMatch(argument -> mentions(argument, test));
        }
        if (type instanceof WildcardType wildcard) {
            return Arrays.stream(wildcard.getUpperBounds()).anyMatch(bound -> mentions(bound, test));
        }
        return false;
    }
}
