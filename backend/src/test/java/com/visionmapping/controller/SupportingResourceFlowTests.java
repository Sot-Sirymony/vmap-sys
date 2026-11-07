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
import java.util.Map;
import java.util.function.LongFunction;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * BRD 12 COV-FR-30: the supporting resources (journal, habits, gratitude,
 * wanted partners, progress logs, synergy links, insights, work style, issue
 * reports, cohorts) over HTTP: the normal answer, 400 for invalid input, 401
 * without a token and 404 for another user's record (BR-13/BR-14).
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SupportingResourceFlowTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private String owner;
    private String stranger;
    private long goalId;
    private long secondGoalId;
    private long stepId;
    private long taskId;

    /** A resource that is archived, restored and deleted like the core ones; body gets a step id when it needs one. */
    record Archivable(String path, LongFunction<Map<String, Object>> body, String changedField, String changedValue, String query) {
        Archivable(String path, LongFunction<Map<String, Object>> body, String changedField, String changedValue) {
            this(path, body, changedField, changedValue, "");
        }

        /** The path with the query some resources need (habits are read for one day). */
        String at(String suffix) {
            String target = path + suffix;
            if (query.isEmpty()) {
                return target;
            }
            return target + (target.contains("?") ? "&" : "?") + query;
        }

        @Override
        public String toString() {
            return path;
        }
    }

    static Stream<Archivable> archivables() {
        return Stream.of(
                new Archivable("/api/fear-entries", step -> Map.of("fear", "Speaking at the conference", "worstCase", "I forget my words",
                        "likelihood", "LOW", "firstTest", "Present to two colleagues"), "fear", "Asking for a raise"),
                new Archivable("/api/gratitude-entries", step -> Map.of("category", "PERSON", "description", "My supervisor's time"),
                        "description", "My sister's help"),
                new Archivable("/api/habits", step -> Map.of("name", "Write 30 minutes"), "name", "Write 45 minutes",
                        "date=" + LocalDate.now()),
                new Archivable("/api/ideal-partner-profiles", step -> Map.of("stepId", step, "requiredExperience", "Systematic reviews"),
                        "requiredExperience", "Meta-analysis"));
    }

    @BeforeEach
    void createPlan() throws Exception {
        owner = registerAndToken("support-owner");
        stranger = registerAndToken("support-stranger");
        long areaId = id(send(post("/api/vision-areas"), owner, Map.of("name", "Career", "priority", "HIGH", "status", "ACTIVE")));
        long dreamId = id(send(post("/api/dreams"), owner, Map.of("visionAreaId", areaId, "title", "Become a researcher",
                "dreamType", "LONG_TERM", "priority", "MEDIUM", "status", "ACTIVE", "scheduleMode", "BOTTOM_UP")));
        goalId = id(send(post("/api/goals"), owner, goal(dreamId, "Publish one paper")));
        secondGoalId = id(send(post("/api/goals"), owner, goal(dreamId, "Win a grant")));
        stepId = id(send(post("/api/steps"), owner, Map.of("goalId", goalId, "title", "Search literature", "owner", "Me",
                "sequenceNumber", 1, "priority", "MEDIUM", "targetDate", LocalDate.now().plusMonths(3).toString(), "status", "NOT_STARTED")));
        taskId = id(send(post("/api/tasks"), owner, Map.of("stepId", stepId, "title", "Search PubMed", "owner", "Me",
                "priority", "MEDIUM", "dueDate", LocalDate.now().plusWeeks(2).toString(), "status", "NOT_STARTED", "progressPercent", 0)));
    }

    @ParameterizedTest(name = "{0}: create, list, update, archive, restore, delete")
    @MethodSource("archivables")
    void goesThroughItsWholeLife(Archivable resource) throws Exception {
        long id = id(send(post(resource.at("")), owner, resource.body().apply(stepId)));
        send(get(resource.at("")), owner).andExpect(status().isOk()).andExpect(jsonPath("$[?(@.id == " + id + ")]").exists());

        Map<String, Object> changed = new java.util.HashMap<>(resource.body().apply(stepId));
        changed.put(resource.changedField(), resource.changedValue());
        send(put(resource.at("/" + id)), owner, changed).andExpect(status().isOk())
                .andExpect(jsonPath("$." + resource.changedField()).value(resource.changedValue()));

        send(delete(resource.path() + "/" + id + "/permanent"), owner).andExpect(status().is4xxClientError());
        send(delete(resource.path() + "/" + id), owner).andExpect(status().is2xxSuccessful());
        send(get(resource.at("")), owner).andExpect(jsonPath("$[?(@.id == " + id + ")]").doesNotExist());
        send(get(resource.at("?includeArchived=true")), owner).andExpect(jsonPath("$[?(@.id == " + id + ")]").exists());
        send(post(resource.path() + "/" + id + "/restore"), owner).andExpect(status().is2xxSuccessful());
        send(get(resource.at("")), owner).andExpect(jsonPath("$[?(@.id == " + id + ")]").exists());
        send(delete(resource.path() + "/" + id), owner).andExpect(status().is2xxSuccessful());
        send(delete(resource.path() + "/" + id + "/permanent"), owner).andExpect(status().is2xxSuccessful());
        send(get(resource.at("?includeArchived=true")), owner).andExpect(jsonPath("$[?(@.id == " + id + ")]").doesNotExist());
    }

    @ParameterizedTest(name = "{0}: 400 for invalid input, 401 without a token, 404 for another user")
    @MethodSource("archivables")
    void refusesInvalidInputNoTokenAndAnotherUser(Archivable resource) throws Exception {
        long id = id(send(post(resource.at("")), owner, resource.body().apply(stepId)));

        send(post(resource.at("")), owner, Map.of()).andExpect(status().isBadRequest());
        send(put(resource.at("/" + id)), owner, Map.of()).andExpect(status().isBadRequest());
        mockMvc.perform(get(resource.at(""))).andExpect(status().isUnauthorized());
        mockMvc.perform(delete(resource.path() + "/" + id)).andExpect(status().isUnauthorized());

        send(put(resource.at("/" + id)), stranger, resource.body().apply(stepId)).andExpect(status().is4xxClientError());
        send(delete(resource.path() + "/" + id), stranger).andExpect(status().isNotFound());
        send(post(resource.path() + "/" + id + "/restore"), stranger).andExpect(status().isNotFound());
        send(delete(resource.path() + "/" + id + "/permanent"), stranger).andExpect(status().isNotFound());
        send(get(resource.at("")), stranger).andExpect(jsonPath("$[?(@.id == " + id + ")]").doesNotExist());
    }

    @Test
    void ticksAHabitForADayAndTakesTheTickBack() throws Exception {
        long habitId = id(send(post("/api/habits?date=" + LocalDate.now()), owner, Map.of("name", "Write 30 minutes")));
        String today = LocalDate.now().toString();
        send(put("/api/habits/" + habitId + "/checks/" + today), owner).andExpect(status().isOk());
        send(delete("/api/habits/" + habitId + "/checks/" + today), owner).andExpect(status().isOk());
        send(put("/api/habits/" + habitId + "/checks/" + today), stranger).andExpect(status().isNotFound());
        send(put("/api/habits/" + habitId + "/checks/not-a-date"), owner).andExpect(status().isBadRequest());
    }

    @Test
    void logsProgressOnATaskAndKeepsItFromOtherUsers() throws Exception {
        long logId = id(send(post("/api/progress-logs"), owner, Map.of("relatedTaskId", taskId,
                "progressPercentBefore", 0, "progressPercentAfter", 40, "note", "Half the searches done")));
        send(get("/api/progress-logs/" + logId), owner).andExpect(status().isOk()).andExpect(jsonPath("$.note").value("Half the searches done"));
        send(get("/api/progress-logs"), owner).andExpect(jsonPath("$[?(@.id == " + logId + ")]").exists());

        send(post("/api/progress-logs"), owner, Map.of("relatedTaskId", taskId, "progressPercentBefore", 0, "progressPercentAfter", 140))
                .andExpect(status().isBadRequest());
        send(post("/api/progress-logs"), stranger, Map.of("relatedTaskId", taskId, "progressPercentBefore", 0, "progressPercentAfter", 10))
                .andExpect(status().isNotFound());
        send(get("/api/progress-logs/" + logId), stranger).andExpect(status().isNotFound());
        send(delete("/api/progress-logs/" + logId), stranger).andExpect(status().isNotFound());

        // Deleting a log archives it (BR-12): it leaves the list but is not destroyed.
        send(delete("/api/progress-logs/" + logId), owner).andExpect(status().is2xxSuccessful());
        send(get("/api/progress-logs"), owner).andExpect(jsonPath("$[?(@.id == " + logId + ")]").doesNotExist());
    }

    @Test
    void linksTwoGoalsThatHelpEachOtherAndRemovesTheLink() throws Exception {
        long linkId = id(send(post("/api/goals/" + goalId + "/synergy-links"), owner, Map.of("relatedGoalId", secondGoalId, "note", "Same data")));
        send(get("/api/goals/" + goalId + "/synergy-links"), owner).andExpect(status().isOk()).andExpect(jsonPath("$[0].id").value(linkId));
        send(post("/api/goals/" + goalId + "/synergy-links"), owner, Map.of()).andExpect(status().isBadRequest());
        send(get("/api/goals/" + goalId + "/synergy-links"), stranger).andExpect(status().isNotFound());
        send(delete("/api/goals/synergy-links/" + linkId), stranger).andExpect(status().isNotFound());
        send(delete("/api/goals/synergy-links/" + linkId), owner).andExpect(status().is2xxSuccessful());
        send(get("/api/goals/" + goalId + "/synergy-links"), owner).andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void searchesOnlyTheUsersOwnInsights() throws Exception {
        send(post("/api/obstacles"), owner, Map.of("title", "No weekday time", "obstacleType", "TIME", "severity", "HIGH",
                "status", "RESOLVED", "rootCause", "Said yes to every meeting")).andExpect(status().isCreated());
        send(get("/api/insights?query=meeting"), owner).andExpect(status().isOk())
                .andExpect(jsonPath("$[0].content").value("Said yes to every meeting"));
        send(get("/api/insights?query=meeting"), stranger).andExpect(jsonPath("$.length()").value(0));
        mockMvc.perform(get("/api/insights")).andExpect(status().isUnauthorized());
    }

    @Test
    void savesTheWorkStyleResultAndRefusesScoresOutOfRange() throws Exception {
        send(get("/api/work-style-profile"), owner).andExpect(status().isOk()).andExpect(jsonPath("$.dominant").isEmpty());
        send(put("/api/work-style-profile"), owner, Map.of("paceFastScore", 7, "paceDeliberateScore", 1, "focusTaskScore", 6,
                "focusPeopleScore", 2)).andExpect(status().isOk()).andExpect(jsonPath("$.dominant").value("DRIVER"));
        send(put("/api/work-style-profile"), owner, Map.of("paceFastScore", 9, "paceDeliberateScore", 1, "focusTaskScore", 6,
                "focusPeopleScore", 2)).andExpect(status().isBadRequest());
        send(get("/api/work-style-profile"), stranger).andExpect(jsonPath("$.dominant").isEmpty());
    }

    @Test
    void letsAReporterTrackTheirReportAndOnlyAnAdminSeeTheQueueOrChangeStatus() throws Exception {
        long reportId = id(send(post("/api/issue-reports"), owner, Map.of("reportType", "BUG", "title", "Save does nothing", "severity", "HIGH")));
        send(get("/api/issue-reports/" + reportId), owner).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("OPEN"));
        send(post("/api/issue-reports"), owner, Map.of("reportType", "BUG")).andExpect(status().isBadRequest());
        send(get("/api/issue-reports/" + reportId), stranger).andExpect(status().isNotFound());
        send(get("/api/issue-reports/all"), owner).andExpect(status().isForbidden());
        send(patch("/api/issue-reports/" + reportId + "/status"), owner, Map.of("status", "CLOSED")).andExpect(status().isForbidden());

        String admin = admin();
        send(get("/api/issue-reports/all?reportType=BUG"), admin).andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id == " + reportId + ")]").exists());
        // BR-32: a resolved bug must say how it was resolved.
        send(patch("/api/issue-reports/" + reportId + "/status"), admin, Map.of("status", "RESOLVED")).andExpect(status().isBadRequest());
        send(patch("/api/issue-reports/" + reportId + "/status"), admin, Map.of("status", "RESOLVED", "resolutionNote", "Fixed in 1.4.1"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("RESOLVED"));

        send(delete("/api/issue-reports/" + reportId), owner).andExpect(status().is2xxSuccessful());
        send(get("/api/issue-reports?includeArchived=true"), owner).andExpect(jsonPath("$[?(@.id == " + reportId + ")].archived").value(true));
        send(post("/api/issue-reports/" + reportId + "/restore"), owner).andExpect(status().is2xxSuccessful());
        send(delete("/api/issue-reports/" + reportId), owner).andExpect(status().is2xxSuccessful());
        send(delete("/api/issue-reports/" + reportId + "/permanent"), owner).andExpect(status().is2xxSuccessful());
        send(get("/api/issue-reports/" + reportId), owner).andExpect(status().isNotFound());
    }

    @Test
    void letsOnlyAnAdminManageCohorts() throws Exception {
        send(get("/api/admin/cohorts"), owner).andExpect(status().isForbidden());
        send(post("/api/admin/cohorts"), owner, Map.of("name", "Spring 2027")).andExpect(status().isForbidden());

        String admin = admin();
        long cohortId = id(send(post("/api/admin/cohorts"), admin, Map.of("name", "Spring 2027")));
        send(post("/api/admin/cohorts"), admin, Map.of("name", "")).andExpect(status().isBadRequest());
        send(put("/api/admin/cohorts/" + cohortId), admin, Map.of("name", "Spring 2027 (Phnom Penh)", "aiEnabled", false))
                .andExpect(status().isOk()).andExpect(jsonPath("$.name").value("Spring 2027 (Phnom Penh)"))
                .andExpect(jsonPath("$.aiEnabled").value(false));
        String oldCode = read(send(get("/api/admin/cohorts"), admin).andReturn()).findValue("joinCode").asText();
        send(post("/api/admin/cohorts/" + cohortId + "/join-code"), admin).andExpect(status().isOk())
                .andExpect(jsonPath("$.joinCode").value(org.hamcrest.Matchers.not(oldCode)));
        send(put("/api/admin/cohorts/999999"), admin, Map.of("name", "Nobody")).andExpect(status().isNotFound());
        mockMvc.perform(get("/api/admin/cohorts")).andExpect(status().isUnauthorized());
    }

    @Test
    void letsAnAdminArchiveACohortAndManageItsMembers() throws Exception {
        String admin = admin();
        long cohortId = id(send(post("/api/admin/cohorts"), admin, Map.of("name", "Autumn 2027")));
        String memberEmail = "support-member-" + System.nanoTime() + "@example.com";
        registerWithEmail(memberEmail);

        send(post("/api/admin/cohorts/" + cohortId + "/members"), admin, Map.of("email", memberEmail)).andExpect(status().is2xxSuccessful());
        send(get("/api/admin/cohorts/" + cohortId + "/members"), admin).andExpect(status().isOk())
                .andExpect(jsonPath("$[0].email").value(memberEmail));
        long memberId = read(send(get("/api/admin/cohorts/" + cohortId + "/members"), admin).andReturn()).get(0).get("userId").asLong();
        send(post("/api/admin/cohorts/" + cohortId + "/members"), admin, Map.of("email", "nobody-" + System.nanoTime() + "@example.com"))
                .andExpect(status().is4xxClientError());
        send(get("/api/admin/cohorts/" + cohortId + "/members"), owner).andExpect(status().isForbidden());

        send(delete("/api/admin/cohorts/" + cohortId + "/members/" + memberId), admin).andExpect(status().is2xxSuccessful());
        send(get("/api/admin/cohorts/" + cohortId + "/members"), admin).andExpect(jsonPath("$.length()").value(0));

        send(post("/api/admin/cohorts/" + cohortId + "/archive"), admin).andExpect(status().isOk()).andExpect(jsonPath("$.archived").value(true));
        send(post("/api/admin/cohorts/" + cohortId + "/restore"), admin).andExpect(status().isOk()).andExpect(jsonPath("$.archived").value(false));
    }

    @Test
    void letsOnlyAnAdminReadTheModuleTextForEditing() throws Exception {
        send(get("/api/admin/modules?language=km"), owner).andExpect(status().isForbidden());
        send(get("/api/admin/modules?language=km"), admin()).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(15));
    }

    @Test
    void answersPasswordRecoveryTheSameWayWhetherOrNotTheAccountExistsAndRefusesABadToken() throws Exception {
        mockMvc.perform(post("/api/auth/forgot-password").contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("email", "nobody-" + System.nanoTime() + "@example.com"))))
                .andExpect(status().isNoContent());
        mockMvc.perform(post("/api/auth/forgot-password").contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("email", "not-an-email"))))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/auth/reset-password").contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("token", "made-up-token", "newPassword", "NewPassword123"))))
                .andExpect(status().is4xxClientError());
        mockMvc.perform(post("/api/auth/reset-password").contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("token", "x", "newPassword", "short"))))
                .andExpect(status().isBadRequest());
    }

    // ---- helpers -------------------------------------------------------------

    private static Map<String, Object> goal(long dreamId, String title) {
        return Map.of("dreamId", dreamId, "title", title, "priority", "MEDIUM",
                "targetDate", LocalDate.now().plusYears(1).toString(), "status", "NOT_STARTED", "scheduleMode", "BOTTOM_UP");
    }

    private String admin() throws Exception {
        String email = "support-admin-" + System.nanoTime() + "@example.com";
        String token = registerWithEmail(email);
        jdbcTemplate.update("update app_users set role = 'ADMIN' where email = ?", email);
        return token;
    }

    private ResultActions send(MockHttpServletRequestBuilder request, String token) throws Exception {
        return mockMvc.perform(request.header("Authorization", "Bearer " + token));
    }

    private ResultActions send(MockHttpServletRequestBuilder request, String token, Object body) throws Exception {
        return send(request.contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(body)), token);
    }

    private long id(ResultActions created) throws Exception {
        return read(created.andExpect(status().isCreated()).andReturn()).get("id").asLong();
    }

    private String registerAndToken(String prefix) throws Exception {
        return registerWithEmail(prefix + "-" + System.nanoTime() + "@example.com");
    }

    private String registerWithEmail(String email) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("fullName", "Support Test User", "email", email,
                                "password", "Password123"))))
                .andExpect(status().isCreated())
                .andReturn();
        return read(result).get("token").asText();
    }

    private JsonNode read(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }
}
