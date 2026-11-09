package com.visionmapping.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.visionmapping.entity.AiConversation;
import com.visionmapping.entity.AiMessage;
import com.visionmapping.entity.AiUsageLog;
import com.visionmapping.entity.AppUser;
import com.visionmapping.entity.CoachComment;
import com.visionmapping.entity.enums.AiMessageRole;
import com.visionmapping.entity.enums.AiOutcome;
import com.visionmapping.entity.enums.CoachCommentTarget;
import com.visionmapping.repository.AiConversationRepository;
import com.visionmapping.repository.AiMessageRepository;
import com.visionmapping.repository.AiUsageLogRepository;
import com.visionmapping.repository.AppUserRepository;
import com.visionmapping.repository.CoachCommentRepository;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

/**
 * V10 (SEC-01-08, SAC-04): every endpoint, read from Spring's own route table,
 * is tried three ways and must refuse each:
 *
 * <ol>
 *   <li>with no token: 401, unless it is on the public list;</li>
 *   <li>as a plain user on /api/admin/** or /api/coach/**: 403;</li>
 *   <li>as user B on a record that belongs to user A: 404, the same answer as a
 *       record that does not exist (SEC-01-02).</li>
 * </ol>
 *
 * Because the routes come from the running app, a new endpoint is checked
 * automatically. A new endpoint whose path holds an ID this test does not know
 * how to fill makes the test fail until it is added to {@link #idFor} or, with
 * a reason, to {@link #NOT_A_RECORD_ID}.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class AccessControlSweepTests {

    /** Endpoints anyone may call. Everything else must answer 401 without a token. */
    private static final Set<String> PUBLIC = Set.of(
            "GET /api/health",
            "POST /api/auth/register",
            "POST /api/auth/login",
            "POST /api/auth/refresh",
            "POST /api/auth/logout",
            "POST /api/auth/forgot-password",
            "POST /api/auth/reset-password",
            "GET /api/ui-text",
            "GET /api/shared-plans/{token}");

    /**
     * Paths whose variable is not another user's record, so the cross-user
     * check does not apply. Each has the reason it is safe.
     */
    private static final Map<String, String> NOT_A_RECORD_ID = Map.of(
            "/api/modules/{number}", "module content is shared program content; the completion is the caller's own",
            "/api/modules/{number}/completion", "always writes the caller's own completion for that module",
            "/api/ai/modules/{number}/help", "helps with the caller's own module work",
            "/api/attitude-checkins/{date}", "always the caller's own check-in for that date",
            "/api/shared-plans/{token}", "public by design; the 256-bit token is the key (PlanShareFlowTests)");

    /**
     * Admin-only actions that live outside /api/admin, so a plain user gets 403
     * before the record is looked up. Each has the reason.
     */
    private static final Map<String, String> ADMIN_ONLY_ACTIONS = Map.of(
            "PATCH /api/issue-reports/{id}/status", "triage is for administrators (IssueReportService)");

    private static final String DATE = "2026-11-01";
    private static final Pattern PATH_VARIABLE = Pattern.compile("\\{([^}]+)}");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    @Qualifier("requestMappingHandlerMapping")
    private RequestMappingHandlerMapping handlerMapping;

    @Autowired
    private AppUserRepository appUserRepository;

    @Autowired
    private AiConversationRepository aiConversationRepository;

    @Autowired
    private AiMessageRepository aiMessageRepository;

    @Autowired
    private CoachCommentRepository coachCommentRepository;

    @Autowired
    private AiUsageLogRepository aiUsageLogRepository;

    private String ownerToken;
    private String otherToken;
    /** User A's records, by the path segment that names them. */
    private final Map<String, Long> ownerIds = new HashMap<>();
    /** The body that created each record, reused for PUT on it. */
    private final Map<String, Map<String, Object>> createBodies = new HashMap<>();

    @BeforeAll
    void createUsersAndOneOfEachRecord() throws Exception {
        String ownerEmail = "owner-" + UUID.randomUUID() + "@example.com";
        ownerToken = register(ownerEmail);
        otherToken = register("other-" + UUID.randomUUID() + "@example.com");
        AppUser owner = appUserRepository.findByEmail(ownerEmail).orElseThrow();

        long areaId = create("vision-areas", Map.of("name", "Career", "priority", "HIGH", "status", "ACTIVE"));
        long dreamId = create("dreams", Map.of(
                "visionAreaId", areaId, "title", "Become a researcher", "whyImportant", "Impact",
                "successDefinition", "A paper", "dreamType", "LONG_TERM", "priority", "HIGH",
                "targetDate", LocalDate.now().plusMonths(6).toString(), "status", "ACTIVE", "scheduleMode", "BOTTOM_UP"));
        long goalId = create("goals", Map.of(
                "dreamId", dreamId, "title", "Learn AI tools", "priority", "HIGH",
                "targetDate", LocalDate.now().plusMonths(3).toString(), "status", "NOT_STARTED", "scheduleMode", "BOTTOM_UP"));
        long secondGoalId = postAndId("/api/goals", ownerToken, Map.of(
                "dreamId", dreamId, "title", "Write a concept note", "priority", "MEDIUM",
                "targetDate", LocalDate.now().plusMonths(3).toString(), "status", "NOT_STARTED", "scheduleMode", "BOTTOM_UP"));
        long stepId = create("steps", Map.of(
                "goalId", goalId, "title", "Search literature", "owner", "Me", "sequenceNumber", 1,
                "priority", "HIGH", "targetDate", LocalDate.now().plusMonths(2).toString(), "status", "NOT_STARTED"));
        long taskId = create("tasks", Map.of(
                "stepId", stepId, "title", "Search PubMed", "owner", "Me", "priority", "HIGH",
                "dueDate", LocalDate.now().plusDays(10).toString(), "status", "NOT_STARTED", "progressPercent", 0));
        long partnerId = create("partners", Map.of(
                "name", "Dr. Advisor", "supportType", "MENTOR", "status", "TO_CONTACT", "relatedDreamId", dreamId));
        create("communication-messages", Map.of("partnerId", partnerId, "subject", "Hello", "status", "DRAFT"));
        create("reviews", Map.of("reviewType", "WEEKLY", "reviewDate", LocalDateTime.now().withNano(0).toString(),
                "summary", "A good week"));
        create("obstacles", Map.of("title", "No time", "obstacleType", "TIME", "severity", "HIGH", "status", "OPEN",
                "relatedTaskId", taskId, "expectation", "It should be easy"));
        create("progress-logs", Map.of("relatedTaskId", taskId, "progressPercentBefore", 0, "progressPercentAfter", 10));
        create("gratitude-entries", Map.of("category", "PERSON", "description", "My mentor",
                "entryDate", LocalDate.now().toString()));
        create("self-assessment-items", Map.of("dreamId", dreamId, "kind", "WEAKNESS", "description", "Bookkeeping",
                "evidence", "Missed a filing", "relevance", "HIGH"));
        create("fear-entries", Map.of("fear", "Rejection", "worstCase", "They say no", "likelihood", "LOW",
                "firstTest", "Ask once"));
        Map<String, Object> habit = Map.of("name", "Read daily");
        ownerIds.put("habits", postAndId("/api/habits?date=" + DATE, ownerToken, habit));
        createBodies.put("habits", habit);
        create("ideal-partner-profiles", Map.of("stepId", stepId, "requiredExperience", "Research"));
        create("issue-reports", Map.of("reportType", "IMPROVEMENT", "title", "Something broke", "description", "Details"));
        create("milestones", Map.of("dreamId", dreamId, "title", "First draft", "achievedOn", LocalDate.now().toString()));
        create("plan-shares", Map.of("dreamId", dreamId, "expiresInDays", 7));
        ownerIds.put("goals/synergy-links", postAndId("/api/goals/" + goalId + "/synergy-links", ownerToken,
                Map.of("relatedGoalId", secondGoalId)));

        // No plain-user API creates these, so they are written directly.
        AiConversation conversation = aiConversationRepository.save(
                AiConversation.builder().user(owner).title("Private chat").build());
        AiMessage message = aiMessageRepository.save(AiMessage.builder()
                .user(owner).conversation(conversation).role(AiMessageRole.ASSISTANT).content("A private reply").build());
        CoachComment comment = coachCommentRepository.save(CoachComment.builder()
                .user(owner).targetType(CoachCommentTarget.GENERAL).body("Well done").build());
        ownerIds.put("ai/conversations", conversation.getId());
        ownerIds.put("ai/messages", message.getId());
        ownerIds.put("coach-comments", comment.getId());
        // V11: a plan builder answer to rate and report outcomes on.
        AiUsageLog planAnswer = aiUsageLogRepository.save(AiUsageLog.builder().user(owner).feature("GOAL_BUILDER")
                .model("claude-opus-5-5").costUsd(java.math.BigDecimal.ZERO).outcome(AiOutcome.OK).suggestedItems(3).build());
        ownerIds.put("ai/plan/requests", planAnswer.getId());
    }

    // ---------- 1. no token ----------

    @Test
    void everyNonPublicEndpointRefusesARequestWithoutAToken() throws Exception {
        List<String> problems = new ArrayList<>();
        for (Route route : routes()) {
            if (PUBLIC.contains(route.key())) {
                continue;
            }
            MockHttpServletResponse response = perform(route, fillWithPlaceholders(route.pattern()), null, "{}");
            if (response.getStatus() != 401) {
                problems.add(route.key() + " -> " + response.getStatus());
            }
        }
        assertThat(problems).as("endpoints that did not answer 401 without a token").isEmpty();
    }

    @Test
    void thePublicListOnlyNamesEndpointsThatExist() {
        Set<String> existing = new TreeSet<>();
        routes().forEach(route -> existing.add(route.key()));
        assertThat(existing).containsAll(PUBLIC);
    }

    // ---------- 2. role ----------

    @Test
    void aPlainUserIsRefusedEveryAdminAndCoachEndpoint() throws Exception {
        List<String> problems = new ArrayList<>();
        int checked = 0;
        for (Route route : routes()) {
            if (!route.pattern().startsWith("/api/admin/") && !route.pattern().startsWith("/api/coach/")) {
                continue;
            }
            checked++;
            MockHttpServletResponse response = perform(route, fillWithPlaceholders(route.pattern()), otherToken, "{}");
            if (response.getStatus() != 403) {
                problems.add(route.key() + " -> " + response.getStatus());
            }
        }
        assertThat(checked).as("admin and coach endpoints found").isGreaterThan(30);
        assertThat(problems).as("admin/coach endpoints that did not answer 403 to a plain user").isEmpty();
    }

    /**
     * SEC-01-04: a coach reads participants and never changes their data. The
     * only coach writes are these actions on the coach's own work. A new write
     * endpoint under /api/coach fails here until someone checks it changes no
     * participant data and adds it with a reason.
     */
    private static final Map<String, String> COACH_ACTIONS = Map.of(
            "POST /api/coach/participants/{id}/comments", "adds the coach's own comment",
            "PUT /api/coach/participants/{id}/pitch-rating", "the coach's rating, stored beside the pitch (v0.1 FR-11)",
            "POST /api/coach/participants/{id}/ai-summary", "creates the coach's summary",
            "POST /api/coach/participants/{id}/feedback-draft", "returns a draft and saves nothing",
            "POST /api/coach/cohorts/{cohortId}/ai-summary", "creates the coach's cohort summary",
            "PUT /api/coach/ai-summaries/{id}/rating", "rates the coach's own summary",
            "POST /api/coach/ai-alerts/{id}/acknowledge", "marks the coach's own alert as seen");

    @Test
    void coachWriteEndpointsAreOnlyTheKnownCoachActions() {
        Set<String> writes = new TreeSet<>();
        routes().stream()
                .filter(route -> route.pattern().startsWith("/api/coach/"))
                .filter(route -> route.method() != HttpMethod.GET)
                .forEach(route -> writes.add(route.key()));
        assertThat(writes).isEqualTo(new TreeSet<>(COACH_ACTIONS.keySet()));
    }

    // ---------- 3. another user's records ----------

    @Test
    void anotherUsersRecordAnswers404OnEveryEndpointThatTakesAnId() throws Exception {
        Map<String, String> snapshot = snapshotOwnerRecords();
        List<String> problems = new ArrayList<>();
        List<String> unknown = new ArrayList<>();
        int checked = 0;

        List<Route> withIds = routes().stream()
                .filter(route -> route.pattern().contains("{"))
                .filter(route -> !route.pattern().startsWith("/api/admin/") && !route.pattern().startsWith("/api/coach/"))
                .filter(route -> !NOT_A_RECORD_ID.containsKey(route.pattern()))
                // Deletes last: if one wrongly succeeded, the records the others
                // probe would be gone and they would pass for the wrong reason.
                .sorted(Comparator.comparing((Route route) -> route.method() == HttpMethod.DELETE).thenComparing(Route::key))
                .toList();

        for (Route route : withIds) {
            String path = fillWithOwnerIds(route.pattern());
            if (path == null) {
                unknown.add(route.key());
                continue;
            }
            checked++;
            if (route.method() == HttpMethod.PUT && route.pattern().equals("/api/habits/{id}")) {
                path += "?date=" + DATE;
            }
            int expected = ADMIN_ONLY_ACTIONS.containsKey(route.key()) ? 403 : 404;
            MockHttpServletResponse response = perform(route, path, otherToken, bodyFor(route));
            if (response.getStatus() != expected) {
                problems.add(route.key() + " -> " + response.getStatus() + " " + abbreviate(response.getContentAsString()));
            }
        }

        assertThat(unknown).as("endpoints with an ID this test cannot fill; add them to idFor or NOT_A_RECORD_ID").isEmpty();
        assertThat(checked).as("endpoints with a record ID checked").isGreaterThan(90);
        assertThat(problems).as("endpoints that did not answer 404 for another user's record").isEmpty();
        assertThat(snapshotOwnerRecords()).as("the owner's records after the sweep").isEqualTo(snapshot);
    }

    /** IDs inside a request body are checked too, not only IDs in the path. */
    @Test
    void anotherUsersRecordInARequestBodyAnswers404() throws Exception {
        Map<String, Map<String, Object>> attempts = new LinkedHashMap<>();
        attempts.put("/api/dreams", with(createBodies.get("dreams"), "visionAreaId", ownerIds.get("vision-areas")));
        attempts.put("/api/goals", with(createBodies.get("goals"), "dreamId", ownerIds.get("dreams")));
        attempts.put("/api/steps", with(createBodies.get("steps"), "goalId", ownerIds.get("goals")));
        attempts.put("/api/tasks", with(createBodies.get("tasks"), "stepId", ownerIds.get("steps")));
        attempts.put("/api/partners", with(createBodies.get("partners"), "relatedDreamId", ownerIds.get("dreams")));
        attempts.put("/api/communication-messages",
                with(createBodies.get("communication-messages"), "partnerId", ownerIds.get("partners")));
        attempts.put("/api/obstacles", with(createBodies.get("obstacles"), "relatedTaskId", ownerIds.get("tasks")));
        attempts.put("/api/progress-logs", with(createBodies.get("progress-logs"), "relatedTaskId", ownerIds.get("tasks")));
        attempts.put("/api/ideal-partner-profiles",
                with(createBodies.get("ideal-partner-profiles"), "stepId", ownerIds.get("steps")));
        attempts.put("/api/self-assessment-items", with(createBodies.get("self-assessment-items"), "dreamId", ownerIds.get("dreams")));
        attempts.put("/api/milestones", with(createBodies.get("milestones"), "dreamId", ownerIds.get("dreams")));
        attempts.put("/api/plan-shares", with(createBodies.get("plan-shares"), "dreamId", ownerIds.get("dreams")));
        attempts.put("/api/reviews", with(createBodies.get("reviews"), "relatedDreamId", ownerIds.get("dreams")));
        // V11: the all-at-once add and its undo check every id they carry.
        attempts.put("/api/ai/plan/add", Map.of("goals", List.of(Map.of("goal",
                with(createBodies.get("goals"), "dreamId", ownerIds.get("dreams")), "edited", false))));
        attempts.put("/api/ai/plan/undo", Map.of("goalIds", List.of(ownerIds.get("goals"))));
        // V11 Phase 7: a plan assistant message about another user's dream.
        attempts.put("/api/ai/chat", Map.of("message", "Hello", "dreamId", ownerIds.get("dreams"), "surface", "PLAN", "section", "GOALS"));

        List<String> problems = new ArrayList<>();
        for (Map.Entry<String, Map<String, Object>> attempt : attempts.entrySet()) {
            MockHttpServletResponse response = mockMvc.perform(post(attempt.getKey())
                            .header("Authorization", "Bearer " + otherToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(attempt.getValue())))
                    .andReturn().getResponse();
            if (response.getStatus() != 404) {
                problems.add("POST " + attempt.getKey() + " -> " + response.getStatus() + " "
                        + abbreviate(response.getContentAsString()));
            }
        }
        assertThat(problems).as("creates that accepted another user's record in the body").isEmpty();
    }

    // ---------- 4. fields a user must never set (SEC-01-06) ----------

    /**
     * Field names that would let a caller set who owns a record, their own role
     * or status, or values the server manages. No request body may have one,
     * unless it is listed below with the reason it means something else.
     */
    private static final Set<String> PRIVILEGED_FIELDS = Set.of(
            "id", "userId", "user", "ownerId", "passwordHash", "role", "userRole", "accountStatus", "userStatus",
            "createdAt", "updatedAt", "archived", "deleted", "cohortId", "coachId", "code");

    private static final Map<String, String> ALLOWED_PRIVILEGED_FIELDS = Map.of(
            "PartnerRequest.role", "the partner's job title, free text, not an app role");

    @Test
    void noRequestBodyLetsTheCallerSetPrivilegedFields() {
        List<String> problems = new ArrayList<>();
        Set<Class<?>> bodies = new TreeSet<>(Comparator.comparing(Class::getSimpleName));
        handlerMapping.getHandlerMethods().values().forEach(method -> {
            for (var parameter : method.getMethodParameters()) {
                if (parameter.hasParameterAnnotation(org.springframework.web.bind.annotation.RequestBody.class)) {
                    bodies.add(parameter.getParameterType());
                }
            }
        });
        for (Class<?> body : bodies) {
            assertThat(body.isRecord()).as(body.getName() + " should be a request DTO record, not an entity").isTrue();
            assertThat(body.getPackageName()).as(body.getName()).isEqualTo("com.visionmapping.dto.request");
            for (var component : body.getRecordComponents()) {
                String key = body.getSimpleName() + "." + component.getName();
                if (PRIVILEGED_FIELDS.contains(component.getName()) && !ALLOWED_PRIVILEGED_FIELDS.containsKey(key)) {
                    problems.add(key);
                }
            }
        }
        assertThat(bodies).as("request body types found").hasSizeGreaterThan(40);
        assertThat(problems).as("request bodies with a field the caller must not set").isEmpty();
    }

    // ---------- 5. every text field has a size limit (SEC-05-09) ----------

    /**
     * Every String, list or map in a request body must have @Size with a max,
     * or a @Pattern that cannot match long text (no *, + or open {n,}). Without
     * a limit, one request could store megabytes or make a slow hash of them.
     */
    @Test
    void everyTextListAndMapInARequestBodyHasAMaximumSize() throws Exception {
        List<String> unbounded = new ArrayList<>();
        for (Class<?> body : requestBodyTypes()) {
            for (var component : body.getRecordComponents()) {
                Class<?> type = component.getType();
                boolean sized = String.class.equals(type) || java.util.Collection.class.isAssignableFrom(type)
                        || Map.class.isAssignableFrom(type);
                if (!sized) {
                    continue;
                }
                var field = body.getDeclaredField(component.getName());
                jakarta.validation.constraints.Size size = field.getAnnotation(jakarta.validation.constraints.Size.class);
                jakarta.validation.constraints.Pattern pattern = field.getAnnotation(jakarta.validation.constraints.Pattern.class);
                boolean boundedPattern = String.class.equals(type) && pattern != null
                        && !pattern.regexp().matches(".*([*+]|\\{\\d+,}).*");
                if ((size == null || size.max() == Integer.MAX_VALUE) && !boundedPattern) {
                    unbounded.add(body.getSimpleName() + "." + component.getName());
                }
            }
        }
        assertThat(unbounded).as("request fields without a maximum size").isEmpty();
    }

    private Set<Class<?>> requestBodyTypes() {
        Set<Class<?>> bodies = new TreeSet<>(Comparator.comparing(Class::getSimpleName));
        handlerMapping.getHandlerMethods().values().forEach(method -> {
            for (var parameter : method.getMethodParameters()) {
                if (parameter.hasParameterAnnotation(org.springframework.web.bind.annotation.RequestBody.class)) {
                    bodies.add(parameter.getParameterType());
                }
            }
        });
        return bodies;
    }

    // ---------- route table ----------

    private record Route(HttpMethod method, String pattern) {
        String key() {
            return method.name() + " " + pattern;
        }
    }

    private List<Route> routes() {
        List<Route> routes = new ArrayList<>();
        for (RequestMappingInfo info : handlerMapping.getHandlerMethods().keySet()) {
            Set<RequestMethod> methods = info.getMethodsCondition().getMethods();
            for (String pattern : info.getPatternValues()) {
                if (!pattern.startsWith("/api/")) {
                    continue;
                }
                for (RequestMethod method : methods) {
                    routes.add(new Route(HttpMethod.valueOf(method.name()), pattern));
                }
            }
        }
        assertThat(routes).as("routes found").hasSizeGreaterThan(200);
        return routes;
    }

    // ---------- filling path variables ----------

    private String fillWithPlaceholders(String pattern) {
        return replaceVariables(pattern, name -> switch (name) {
            case "date" -> DATE;
            case "language" -> "en";
            case "token" -> "not-a-token";
            default -> "1";
        });
    }

    /** Null when a variable is not known. */
    private String fillWithOwnerIds(String pattern) {
        return replaceVariables(pattern, name -> {
            if (name.equals("date")) {
                return DATE;
            }
            Long id = idFor(pattern, name);
            return id == null ? null : String.valueOf(id);
        });
    }

    private Long idFor(String pattern, String variable) {
        return switch (variable) {
            case "visionAreaId" -> ownerIds.get("vision-areas");
            case "dreamId" -> ownerIds.get("dreams");
            case "goalId" -> ownerIds.get("goals");
            case "stepId" -> ownerIds.get("steps");
            case "linkId" -> ownerIds.get("goals/synergy-links");
            case "id" -> ownerIds.get(pattern.substring("/api/".length(), pattern.indexOf("/{id}")));
            default -> null;
        };
    }

    private static String replaceVariables(String pattern, java.util.function.Function<String, String> value) {
        Matcher matcher = PATH_VARIABLE.matcher(pattern);
        StringBuilder path = new StringBuilder();
        while (matcher.find()) {
            String replacement = value.apply(matcher.group(1));
            if (replacement == null) {
                return null;
            }
            matcher.appendReplacement(path, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(path);
        return path.toString();
    }

    // ---------- bodies ----------

    /**
     * A body that passes validation, so the request reaches the ownership
     * check. An invalid body would be refused with 400 before it, and would
     * prove nothing about ownership.
     */
    private String bodyFor(Route route) throws Exception {
        String pattern = route.pattern();
        if (pattern.endsWith("/status")) {
            String resource = pattern.substring("/api/".length(), pattern.indexOf("/{id}"));
            return json(Map.of("status", switch (resource) {
                case "vision-areas", "dreams" -> "ACTIVE";
                case "partners" -> "CONTACTED";
                case "communication-messages" -> "SENT";
                case "issue-reports" -> "RESOLVED";
                default -> "IN_PROGRESS";
            }));
        }
        if (pattern.endsWith("/commitment")) {
            return json(Map.of("signedName", "Other", "startDate", LocalDate.now().toString(), "firstAction", "Start"));
        }
        if (pattern.endsWith("/cover") && route.method() == HttpMethod.PUT) {
            return json(Map.of("method", "ACCEPT", "note", "Someone else's reason"));
        }
        if (pattern.endsWith("/synergy-links")) {
            return json(Map.of("relatedGoalId", ownerIds.get("goals")));
        }
        if (pattern.equals("/api/ai/plan/drafts/{dreamId}") && route.method() == HttpMethod.PUT) {
            return json(Map.of("stage", "GOALS", "content", Map.of("goals", List.of())));
        }
        if (pattern.endsWith("/clarified-field")) {
            return json(Map.of("field", "WHY_IMPORTANT", "value", "Someone else's answer"));
        }
        // V11 Phase 7: the plan assistant.
        if (pattern.endsWith("/assistant/section-check") || pattern.endsWith("/assistant/proposals")) {
            return json(Map.of("surface", "PLAN", "section", "STEPS"));
        }
        if (pattern.endsWith("/assistant/apply")) {
            return json(Map.of("kind", "EDIT", "itemRef", "goal:" + ownerIds.get("goals"), "field", "TITLE", "value", "Someone else's"));
        }
        if (pattern.endsWith("/draft-fix")) {
            return json(Map.of("itemKey", "g1", "kind", "GAP"));
        }
        if (pattern.endsWith("/draft-task-suggestions")) {
            return json(Map.of("stepTitle", "A new step"));
        }
        if (pattern.endsWith("/draft-step-suggestions")) {
            return json(Map.of("title", "A new goal"));
        }
        if (pattern.endsWith("/outcome")) {
            return json(Map.of("added", 1, "edited", 0));
        }
        if (pattern.endsWith("/rating")) {
            return json(Map.of("rating", 1));
        }
        if (route.method() == HttpMethod.PUT && pattern.endsWith("/{id}")) {
            String resource = pattern.substring("/api/".length(), pattern.indexOf("/{id}"));
            Map<String, Object> body = createBodies.get(resource);
            if (body != null) {
                return json(body);
            }
        }
        return "{}";
    }

    // ---------- helpers ----------

    private MockHttpServletResponse perform(Route route, String path, String token, String body) throws Exception {
        MockHttpServletRequestBuilder builder = request(route.method(), path)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body);
        if (token != null) {
            builder.header("Authorization", "Bearer " + token);
        }
        return mockMvc.perform(builder).andReturn().getResponse();
    }

    /** The owner's own view of each record that has a GET, to prove nothing changed. */
    private Map<String, String> snapshotOwnerRecords() throws Exception {
        Map<String, String> snapshot = new LinkedHashMap<>();
        for (String resource : List.of("vision-areas", "dreams", "goals", "steps", "tasks", "partners",
                "communication-messages", "reviews", "obstacles", "progress-logs", "gratitude-entries",
                "ideal-partner-profiles", "issue-reports", "ai/conversations")) {
            MockHttpServletResponse response = perform(new Route(HttpMethod.GET, "/api/" + resource + "/{id}"),
                    "/api/" + resource + "/" + ownerIds.get(resource), ownerToken, "{}");
            assertThat(response.getStatus()).as("owner reads own " + resource).isEqualTo(200);
            snapshot.put(resource, response.getContentAsString());
        }
        return snapshot;
    }

    private long create(String resource, Map<String, Object> body) throws Exception {
        long id = postAndId("/api/" + resource, ownerToken, body);
        ownerIds.put(resource, id);
        createBodies.put(resource, body);
        return id;
    }

    private long postAndId(String path, String token, Map<String, Object> body) throws Exception {
        MockHttpServletResponse response = mockMvc.perform(post(path)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(body)))
                .andReturn().getResponse();
        assertThat(response.getStatus()).as("create " + path + ": " + response.getContentAsString()).isBetween(200, 201);
        return objectMapper.readTree(response.getContentAsString()).get("id").asLong();
    }

    private String register(String email) throws Exception {
        String body = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("fullName", "Sweep User", "email", email, "password", "Password123"))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).get("token").asText();
    }

    private static Map<String, Object> with(Map<String, Object> body, String key, Object value) {
        Map<String, Object> copy = new HashMap<>(body);
        copy.put(key, value);
        return copy;
    }

    private String json(Object value) throws Exception {
        return objectMapper.writeValueAsString(value);
    }

    private static String abbreviate(String text) {
        return text.length() > 120 ? text.substring(0, 120) + "..." : text;
    }
}
