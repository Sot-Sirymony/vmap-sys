package com.visionmapping.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * BRD 12 COV-FR-29, COV-FR-30: every core resource through its whole life over
 * HTTP — create, read, update, status, archive, restore, permanent delete — and
 * for each endpoint the three refusals that protect the data: invalid input
 * (400), no token (401) and another user's record (404, BR-13/BR-14).
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class CoreResourceLifecycleFlowTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    /** One user's plan, top to bottom, so each resource has its parents. */
    private record Plan(String token, long areaId, long dreamId, long goalId, long stepId, long taskId) {
    }

    /**
     * How to work with one resource: its path, a valid body (given the plan),
     * the field and value an update changes, and a status it can move to.
     */
    record Resource(String path, Function<Plan, Map<String, Object>> body, String changedField, String changedValue,
                    String nextStatus) {
        @Override
        public String toString() {
            return path;
        }
    }

    private Plan owner;
    private String stranger;

    @BeforeEach
    void createTwoUsers() throws Exception {
        owner = plan(registerAndToken("lifecycle-owner"));
        stranger = registerAndToken("lifecycle-stranger");
    }

    static Stream<Resource> resources() {
        return Stream.of(
                new Resource("/api/vision-areas", plan -> visionArea("Career"), "name", "Career and research", "PAUSED"),
                new Resource("/api/dreams", CoreResourceLifecycleFlowTests::dream, "title", "Lead a research team", "PAUSED"),
                new Resource("/api/goals", CoreResourceLifecycleFlowTests::goal, "title", "Publish two papers", "IN_PROGRESS"),
                new Resource("/api/steps", CoreResourceLifecycleFlowTests::step, "title", "Search three databases", "IN_PROGRESS"),
                new Resource("/api/tasks", CoreResourceLifecycleFlowTests::task, "title", "Search Scopus", "IN_PROGRESS"),
                new Resource("/api/obstacles", CoreResourceLifecycleFlowTests::obstacle, "title", "No weekday time at all", "IN_PROGRESS"),
                new Resource("/api/communication-messages", CoreResourceLifecycleFlowTests::message, "subject", "A second request", "SENT"),
                new Resource("/api/reviews", CoreResourceLifecycleFlowTests::review, "summary", "A better week", null));
    }

    @ParameterizedTest(name = "{0}: create, read, update, status, archive, restore, delete")
    @MethodSource("resources")
    void goesThroughItsWholeLife(Resource resource) throws Exception {
        long id = create(resource, owner);

        perform(get(resource.path() + "/" + id), owner.token()).andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id));

        Map<String, Object> changed = new HashMap<>(resource.body().apply(owner));
        changed.put(resource.changedField(), resource.changedValue());
        perform(put(resource.path() + "/" + id).contentType(MediaType.APPLICATION_JSON).content(json(changed)), owner.token())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$." + resource.changedField()).value(resource.changedValue()));

        if (resource.nextStatus() != null) {
            perform(patch(resource.path() + "/" + id + "/status").contentType(MediaType.APPLICATION_JSON)
                    .content(json(Map.of("status", resource.nextStatus(), "manualOverride", false))), owner.token())
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value(resource.nextStatus()));
        }

        // A live record cannot be deleted for good: it must be archived first (BR-12).
        perform(delete(resource.path() + "/" + id + "/permanent"), owner.token()).andExpect(status().is4xxClientError());

        perform(delete(resource.path() + "/" + id), owner.token()).andExpect(status().is2xxSuccessful());
        perform(get(resource.path() + "/" + id), owner.token()).andExpect(jsonPath("$.archived").value(true));

        perform(post(resource.path() + "/" + id + "/restore"), owner.token()).andExpect(status().is2xxSuccessful());
        perform(get(resource.path() + "/" + id), owner.token()).andExpect(jsonPath("$.archived").value(false));

        perform(delete(resource.path() + "/" + id), owner.token()).andExpect(status().is2xxSuccessful());
        perform(delete(resource.path() + "/" + id + "/permanent"), owner.token()).andExpect(status().is2xxSuccessful());
        perform(get(resource.path() + "/" + id), owner.token()).andExpect(status().isNotFound());
    }

    @ParameterizedTest(name = "{0}: invalid input is refused with 400")
    @MethodSource("resources")
    void refusesInvalidInput(Resource resource) throws Exception {
        long id = create(resource, owner);
        perform(post(resource.path()).contentType(MediaType.APPLICATION_JSON).content("{}"), owner.token())
                .andExpect(status().isBadRequest());
        perform(put(resource.path() + "/" + id).contentType(MediaType.APPLICATION_JSON).content("{}"), owner.token())
                .andExpect(status().isBadRequest());
        if (resource.nextStatus() != null) {
            perform(patch(resource.path() + "/" + id + "/status").contentType(MediaType.APPLICATION_JSON)
                    .content(json(Map.of("status", "NOT_A_STATUS"))), owner.token())
                    .andExpect(status().isBadRequest());
        }
    }

    @ParameterizedTest(name = "{0}: no token is refused with 401")
    @MethodSource("resources")
    void refusesARequestWithoutAToken(Resource resource) throws Exception {
        long id = create(resource, owner);
        mockMvc.perform(get(resource.path())).andExpect(status().isUnauthorized());
        mockMvc.perform(get(resource.path() + "/" + id)).andExpect(status().isUnauthorized());
        mockMvc.perform(put(resource.path() + "/" + id).contentType(MediaType.APPLICATION_JSON)
                .content(json(resource.body().apply(owner)))).andExpect(status().isUnauthorized());
        mockMvc.perform(delete(resource.path() + "/" + id)).andExpect(status().isUnauthorized());
    }

    @ParameterizedTest(name = "{0}: another user's record answers 404 and is left unchanged")
    @MethodSource("resources")
    void hidesAnotherUsersRecord(Resource resource) throws Exception {
        long id = create(resource, owner);
        perform(get(resource.path() + "/" + id), stranger).andExpect(status().isNotFound());
        perform(put(resource.path() + "/" + id).contentType(MediaType.APPLICATION_JSON)
                .content(json(resource.body().apply(owner))), stranger).andExpect(status().is4xxClientError());
        perform(delete(resource.path() + "/" + id), stranger).andExpect(status().isNotFound());
        perform(post(resource.path() + "/" + id + "/restore"), stranger).andExpect(status().isNotFound());
        perform(delete(resource.path() + "/" + id + "/permanent"), stranger).andExpect(status().isNotFound());

        perform(get(resource.path() + "/" + id), owner.token()).andExpect(status().isOk())
                .andExpect(jsonPath("$.archived").value(false));
    }

    @Test
    void reordersSiblingsAndRefusesAnOrderThatMixesInAnotherUsersRecords() throws Exception {
        long secondTask = create(new Resource("/api/tasks", CoreResourceLifecycleFlowTests::task, "title", "", null), owner);
        perform(patch("/api/tasks/reorder").contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("parentId", owner.stepId(), "orderedIds", List.of(secondTask, owner.taskId())))), owner.token())
                .andExpect(status().is2xxSuccessful());
        perform(get("/api/tasks/" + secondTask), owner.token()).andExpect(jsonPath("$.sortOrder").value(0));
        perform(get("/api/tasks/" + owner.taskId()), owner.token()).andExpect(jsonPath("$.sortOrder").value(1));

        long secondArea = create(new Resource("/api/vision-areas", plan -> visionArea("Health"), "name", "", null), owner);
        perform(patch("/api/vision-areas/reorder").contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("orderedIds", List.of(secondArea, owner.areaId())))), owner.token())
                .andExpect(status().is2xxSuccessful());

        Plan other = plan(stranger);
        perform(patch("/api/tasks/reorder").contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("parentId", owner.stepId(), "orderedIds", List.of(other.taskId(), owner.taskId())))), owner.token())
                .andExpect(status().is4xxClientError());
        perform(patch("/api/vision-areas/reorder").contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("orderedIds", List.of(other.areaId(), owner.areaId())))), owner.token())
                .andExpect(status().is4xxClientError());
        perform(patch("/api/tasks/reorder").contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("parentId", owner.stepId(), "orderedIds", List.of()))), owner.token())
                .andExpect(status().isBadRequest());
    }

    @Test
    void listsEachLevelShowsWhatArchivingWouldTakeWithItAndReordersDreamsGoalsAndSteps() throws Exception {
        for (String path : List.of("/api/vision-areas", "/api/dreams", "/api/goals", "/api/steps", "/api/tasks")) {
            perform(get(path), owner.token()).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1));
            perform(get(path), stranger).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(0));
        }
        perform(get("/api/dreams/" + owner.dreamId() + "/archive-impact"), owner.token()).andExpect(status().isOk())
                .andExpect(jsonPath("$.goals").value(1)).andExpect(jsonPath("$.steps").value(1)).andExpect(jsonPath("$.tasks").value(1));
        perform(get("/api/steps/" + owner.stepId() + "/archive-impact"), owner.token()).andExpect(status().isOk())
                .andExpect(jsonPath("$.tasks").value(1));
        perform(get("/api/steps/" + owner.stepId() + "/archive-impact"), stranger).andExpect(status().isNotFound());

        long secondDream = createAt("/api/dreams", dream(owner), owner.token());
        perform(patch("/api/dreams/reorder").contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("parentId", owner.areaId(), "orderedIds", List.of(secondDream, owner.dreamId())))), owner.token())
                .andExpect(status().is2xxSuccessful());
        perform(get("/api/dreams/" + secondDream), owner.token()).andExpect(jsonPath("$.sortOrder").value(0));

        long secondGoal = createAt("/api/goals", goal(owner), owner.token());
        perform(patch("/api/goals/reorder").contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("parentId", owner.dreamId(), "orderedIds", List.of(secondGoal, owner.goalId())))), owner.token())
                .andExpect(status().is2xxSuccessful());

        long secondStep = createAt("/api/steps", step(owner), owner.token());
        perform(patch("/api/steps/reorder").contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("parentId", owner.goalId(), "orderedIds", List.of(secondStep, owner.stepId())))), owner.token())
                .andExpect(status().is2xxSuccessful());
        // Steps keep their order as the sequence number (1 = first).
        perform(get("/api/steps/" + secondStep), owner.token()).andExpect(jsonPath("$.sequenceNumber").value(1));
        perform(get("/api/steps/" + owner.stepId()), owner.token()).andExpect(jsonPath("$.sequenceNumber").value(2));
        // An order missing a sibling is refused, so no step loses its place.
        perform(patch("/api/steps/reorder").contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("parentId", owner.goalId(), "orderedIds", List.of(secondStep)))), owner.token())
                .andExpect(status().is4xxClientError());
    }

    @Test
    void updatesAndRestoresAPartnerOnlyForItsOwner() throws Exception {
        Map<String, Object> partner = Map.of("name", "Dr. Mentor", "supportType", "MENTOR", "status", "TO_CONTACT");
        long partnerId = createAt("/api/partners", partner, owner.token());
        Map<String, Object> contacted = new HashMap<>(partner);
        contacted.put("status", "CONTACTED");
        perform(put("/api/partners/" + partnerId).contentType(MediaType.APPLICATION_JSON).content(json(contacted)), owner.token())
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("CONTACTED"));
        perform(put("/api/partners/" + partnerId).contentType(MediaType.APPLICATION_JSON).content(json(contacted)), stranger)
                .andExpect(status().isNotFound());
        perform(delete("/api/partners/" + partnerId), owner.token()).andExpect(status().is2xxSuccessful());
        perform(post("/api/partners/" + partnerId + "/restore"), stranger).andExpect(status().isNotFound());
        perform(post("/api/partners/" + partnerId + "/restore"), owner.token()).andExpect(status().is2xxSuccessful());
        perform(get("/api/partners/" + partnerId), owner.token()).andExpect(jsonPath("$.archived").value(false));
    }

    @Test
    void refusesToBlockATaskWithoutAReason() throws Exception {
        Map<String, Object> blocked = new HashMap<>(task(owner));
        blocked.put("status", "BLOCKED");
        perform(put("/api/tasks/" + owner.taskId()).contentType(MediaType.APPLICATION_JSON).content(json(blocked)), owner.token())
                .andExpect(status().isBadRequest());

        blocked.put("blockerReason", "Waiting for library access");
        perform(put("/api/tasks/" + owner.taskId()).contentType(MediaType.APPLICATION_JSON).content(json(blocked)), owner.token())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.blockerReason").value("Waiting for library access"));
    }

    // ---- request bodies ------------------------------------------------------

    private static Map<String, Object> visionArea(String name) {
        return Map.of("name", name, "priority", "HIGH", "status", "ACTIVE");
    }

    private static Map<String, Object> dream(Plan plan) {
        return Map.of("visionAreaId", plan.areaId(), "title", "Become a researcher", "whyImportant", "Help my community",
                "successDefinition", "One published study", "dreamType", "LONG_TERM", "priority", "MEDIUM",
                "targetDate", LocalDate.now().plusYears(2).toString(), "status", "ACTIVE", "scheduleMode", "BOTTOM_UP");
    }

    private static Map<String, Object> goal(Plan plan) {
        return Map.of("dreamId", plan.dreamId(), "title", "Publish one paper", "priority", "MEDIUM",
                "targetDate", LocalDate.now().plusYears(1).toString(), "status", "NOT_STARTED", "scheduleMode", "BOTTOM_UP");
    }

    private static Map<String, Object> step(Plan plan) {
        return Map.of("goalId", plan.goalId(), "title", "Search literature", "owner", "Me", "sequenceNumber", 1,
                "priority", "MEDIUM", "targetDate", LocalDate.now().plusMonths(3).toString(), "status", "NOT_STARTED");
    }

    private static Map<String, Object> task(Plan plan) {
        return Map.of("stepId", plan.stepId(), "title", "Search PubMed", "owner", "Me", "priority", "MEDIUM",
                "dueDate", LocalDate.now().plusWeeks(2).toString(), "status", "NOT_STARTED", "progressPercent", 0);
    }

    private static Map<String, Object> obstacle(Plan plan) {
        return Map.of("relatedGoalId", plan.goalId(), "title", "No weekday time", "obstacleType", "TIME",
                "severity", "HIGH", "status", "OPEN");
    }

    private static Map<String, Object> message(Plan plan) {
        return Map.of("relatedDreamId", plan.dreamId(), "audience", "Dr. Mentor", "subject", "A request for guidance",
                "request", "Review my draft", "status", "DRAFT");
    }

    private static Map<String, Object> review(Plan plan) {
        return Map.of("reviewType", "WEEKLY", "reviewDate", LocalDate.now().atTime(18, 0).toString(),
                "relatedDreamId", plan.dreamId(), "summary", "A steady week");
    }

    // ---- helpers -------------------------------------------------------------

    private Plan plan(String token) throws Exception {
        long areaId = createAt("/api/vision-areas", visionArea("Career"), token);
        long dreamId = createAt("/api/dreams", dream(new Plan(token, areaId, 0, 0, 0, 0)), token);
        long goalId = createAt("/api/goals", goal(new Plan(token, areaId, dreamId, 0, 0, 0)), token);
        long stepId = createAt("/api/steps", step(new Plan(token, areaId, dreamId, goalId, 0, 0)), token);
        long taskId = createAt("/api/tasks", task(new Plan(token, areaId, dreamId, goalId, stepId, 0)), token);
        return new Plan(token, areaId, dreamId, goalId, stepId, taskId);
    }

    private long create(Resource resource, Plan plan) throws Exception {
        return createAt(resource.path(), resource.body().apply(plan), plan.token());
    }

    private long createAt(String path, Map<String, Object> body, String token) throws Exception {
        MvcResult result = perform(post(path).contentType(MediaType.APPLICATION_JSON).content(json(body)), token)
                .andExpect(status().isCreated())
                .andReturn();
        return read(result).get("id").asLong();
    }

    private org.springframework.test.web.servlet.ResultActions perform(MockHttpServletRequestBuilder request, String token)
            throws Exception {
        return mockMvc.perform(request.header("Authorization", "Bearer " + token));
    }

    private String registerAndToken(String prefix) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "fullName", "Lifecycle Test User",
                                "email", prefix + "-" + System.nanoTime() + "@example.com",
                                "password", "Steady-Harbor-Lamp-42"))))
                .andExpect(status().isCreated())
                .andReturn();
        return read(result).get("token").asText();
    }

    private JsonNode read(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    private String json(Object value) throws Exception {
        return objectMapper.writeValueAsString(value);
    }
}
