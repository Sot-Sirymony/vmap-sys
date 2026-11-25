package com.visionmapping.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.visionmapping.service.ai.ClaudeClient;
import com.visionmapping.service.ai.StreamlineAiService;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/** V30 (BRD 30): the Streamline AI picks, stage 1 drafts, summary advice, ranked rule cards and the page assistant. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class StreamlineAiFlowTests {

    private static final String KEY = "sk-ant-api03-test-key-0000000000wxyz";
    private static final String PASSWORD = "Steady-Harbor-Lamp-42";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbc;

    @MockitoBean
    private ClaudeClient claudeClient;

    private String admin;

    @BeforeEach
    void aiOn() throws Exception {
        reset(claudeClient);
        admin = register("streamline-ai-admin");
        jdbc.update("update app_users set role = 'ADMIN' where email like 'streamline-ai-admin-%' and role = 'USER'");
        jdbc.update("update ai_settings set model = 'claude-opus-5-5', plan_model = null, streamline_model = null, monthly_budget_usd = 20.00, daily_message_limit = 50, pages_off = ''");
        send(put("/api/admin/ai-settings/api-key"), admin, Map.of("apiKey", KEY)).andExpect(status().isOk());
        send(put("/api/admin/ai-settings"), admin, Map.of("enabled", true, "model", "claude-opus-5-5", "monthlyBudgetUsd", 20))
                .andExpect(status().isOk());
    }

    @Test
    void sac01_aiPicksOnlyTheUsersOwnOpenGoalsAndStepsWithoutAnOpenReview() throws Exception {
        String user = consented("picks");
        long goal = goalWithSteps(user, "Save a deposit", 2, 3);
        long busyStep = firstStep(user, goal);
        long reviewed = goalWithSteps(user, "Already reviewed", 1, 1);
        send(post("/api/streamline-reviews"), user, Map.of("goalId", reviewed)).andExpect(status().isCreated());
        ArgumentCaptor<String> records = ArgumentCaptor.forClass(String.class);
        when(claudeClient.structured(anyString(), anyString(), records.capture(), anyString(), eq(StreamlineAiService.PickDrafts.class)))
                .thenReturn(new ClaudeClient.Structured<>(new StreamlineAiService.PickDrafts(List.of(
                        new StreamlineAiService.PickDraft("STEP-" + busyStep, "Three open tasks wait on it."),
                        new StreamlineAiService.PickDraft("GOAL-" + goal, "Two steps could become one."),
                        new StreamlineAiService.PickDraft("GOAL-" + reviewed, "Has an open review"),
                        new StreamlineAiService.PickDraft("GOAL-999999", "Not yours"))), "claude-opus-5-5", 10, 10, false));

        JsonNode picks = read(send(post("/api/streamline-reviews/ai-candidates"), user, Map.of()).andExpect(status().isOk()));

        assertThat(picks.get("requestId").isNull()).isFalse();
        JsonNode candidates = picks.get("candidates");
        assertThat(candidates).hasSize(2);
        assertThat(candidates.get(0).get("anchorType").asText()).isEqualTo("STEP");
        assertThat(candidates.get(0).get("anchorId").asLong()).isEqualTo(busyStep);
        assertThat(candidates.get(0).get("reason").asText()).isEqualTo("Three open tasks wait on it.");
        assertThat(candidates.get(1).get("anchorId").asLong()).isEqualTo(goal);
        assertThat(records.getValue()).contains("[GOAL-" + goal + "]").contains("[STEP-" + busyStep + "]")
                .doesNotContain("Already reviewed").contains("3 open tasks");
    }

    @Test
    void noOpenGoalsMeansNoPicksAndNoAiRequest() throws Exception {
        String user = consented("no-picks");
        JsonNode picks = read(send(post("/api/streamline-reviews/ai-candidates"), user, Map.of()).andExpect(status().isOk()));
        assertThat(picks.get("candidates")).isEmpty();
        verify(claudeClient, never()).structured(anyString(), anyString(), anyString(), anyString(), eq(StreamlineAiService.PickDrafts.class));
    }

    @Test
    void sac08_withoutConsentThePicksAreRefused() throws Exception {
        String user = register("no-consent");
        goalWithSteps(user, "Save a deposit", 1, 1);
        send(post("/api/streamline-reviews/ai-candidates"), user, Map.of()).andExpect(result ->
                assertThat(result.getResponse().getStatus()).isGreaterThanOrEqualTo(400));
    }

    @Test
    void sac04_stage1SuggestionsCarryDraftAnswers() throws Exception {
        String user = consented("drafts");
        long goal = goalWithSteps(user, "Save a deposit", 1, 1);
        long review = read(send(post("/api/streamline-reviews"), user, Map.of("goalId", goal)).andExpect(status().isCreated())).get("id").asLong();
        String code = read(mockMvc.perform(get("/api/streamline-reviews/" + review).header("Authorization", "Bearer " + user)))
                .get("items").get(0).get("code").asText();
        when(claudeClient.structured(anyString(), anyString(), anyString(), anyString(), eq(StreamlineAiService.SuggestionDrafts.class)))
                .thenReturn(new ClaudeClient.Structured<>(new StreamlineAiService.SuggestionDrafts(List.of(
                        new StreamlineAiService.SuggestionDraft(code, "ANSWER", "", "Banks ask for it", "", "NEEDED", "", "",
                                "ORGANISATION", "The bank", "Needed for the loan", "", ""))), "claude-opus-5-5", 10, 10, false));

        JsonNode suggestion = read(send(post("/api/streamline-reviews/" + review + "/stages/1/suggestions"), user, Map.of())
                .andExpect(status().isOk())).get("suggestions").get(0);

        assertThat(suggestion.get("action").asText()).isEqualTo("ANSWER");
        assertThat(suggestion.get("requirementSource").asText()).isEqualTo("ORGANISATION");
        assertThat(suggestion.get("requirementOwner").asText()).isEqualTo("The bank");
        assertThat(suggestion.get("whyNeeded").asText()).isEqualTo("Needed for the loan");
        assertThat(suggestion.get("verdict").asText()).isEqualTo("NEEDED");
    }

    @Test
    void sac06_adviceOnlyForACompletedReviewAndOnlyWithItsOwnCodes() throws Exception {
        String user = consented("advice");
        long goal = goalWithSteps(user, "Save a deposit", 1, 1);
        long review = read(send(post("/api/streamline-reviews"), user, Map.of("goalId", goal)).andExpect(status().isCreated())).get("id").asLong();
        send(post("/api/streamline-reviews/" + review + "/summary-advice"), user, Map.of()).andExpect(status().isBadRequest());

        JsonNode items = read(mockMvc.perform(get("/api/streamline-reviews/" + review).header("Authorization", "Bearer " + user))).get("items");
        List<Long> ids = new ArrayList<>();
        items.forEach(item -> ids.add(item.get("id").asLong()));
        send(put("/api/streamline-reviews/" + review + "/items/bulk"), user,
                Map.of("itemIds", ids, "requirementSource", "SELF", "requirementOwner", "Me", "verdict", "NEEDED")).andExpect(status().isOk());
        send(post("/api/streamline-reviews/" + review + "/stages/1/complete"), user, Map.of()).andExpect(status().isOk());
        for (int stage = 2; stage <= 5; stage++) {
            send(post("/api/streamline-reviews/" + review + "/stages/" + stage + "/not-needed"), user,
                    Map.of("reason", "Nothing to change at this stage")).andExpect(status().isOk());
        }
        String code = items.get(0).get("code").asText();
        when(claudeClient.structured(anyString(), anyString(), anyString(), anyString(), eq(StreamlineAiService.AdviceDrafts.class)))
                .thenReturn(new ClaudeClient.Structured<>(new StreamlineAiService.AdviceDrafts(List.of(
                        new StreamlineAiService.AdviceDraft("Start this step earlier next time.", code),
                        new StreamlineAiService.AdviceDraft("Batch the bank calls.", "T-999"),
                        new StreamlineAiService.AdviceDraft(" ", ""))), "claude-opus-5-5", 10, 10, false));

        JsonNode points = read(send(post("/api/streamline-reviews/" + review + "/summary-advice"), user, Map.of())
                .andExpect(status().isOk())).get("points");

        assertThat(points).hasSize(2);
        assertThat(points.get(0).get("itemCode").asText()).isEqualTo(code);
        assertThat(points.get(1).get("itemCode").isNull()).isTrue();
    }

    // ---- V32 phase 1 (BRD 32): saved suggestions and the Streamline model ----

    @Test
    void sfc01_savedSuggestionsComeBackWithoutANewRequestAndDropCardsAlreadyActedOn() throws Exception {
        String user = consented("saved");
        long goal = goalWithSteps(user, "Save a deposit", 1, 2);
        long review = read(send(post("/api/streamline-reviews"), user, Map.of("goalId", goal)).andExpect(status().isCreated())).get("id").asLong();
        String path = "/api/streamline-reviews/" + review + "/stages/1/suggestions";
        mockMvc.perform(get(path).header("Authorization", "Bearer " + user)).andExpect(status().isNoContent());
        JsonNode items = read(mockMvc.perform(get("/api/streamline-reviews/" + review).header("Authorization", "Bearer " + user))).get("items");
        long first = items.get(0).get("id").asLong();
        long second = items.get(1).get("id").asLong();
        when(claudeClient.structured(anyString(), anyString(), anyString(), anyString(), eq(StreamlineAiService.SuggestionDrafts.class)))
                .thenReturn(new ClaudeClient.Structured<>(new StreamlineAiService.SuggestionDrafts(List.of(
                        new StreamlineAiService.SuggestionDraft(items.get(0).get("code").asText(), "QUESTION", "Who asked for this?", "r",
                                "", "", "", "", "", "", "", "", ""),
                        new StreamlineAiService.SuggestionDraft(items.get(1).get("code").asText(), "VERDICT", "", "r", "", "NEEDED",
                                "", "", "", "", "", "", ""))), "claude-opus-5-5", 10, 10, false));
        JsonNode fresh = read(send(post(path), user, Map.of()).andExpect(status().isOk()));
        assertThat(fresh.get("savedAt").isNull()).isTrue();

        JsonNode saved = read(mockMvc.perform(get(path).header("Authorization", "Bearer " + user)).andExpect(status().isOk()));
        assertThat(saved.get("suggestions")).hasSize(2);
        assertThat(saved.get("savedAt").isNull()).isFalse();
        assertThat(saved.get("requestId").asLong()).isEqualTo(fresh.get("requestId").asLong());
        verify(claudeClient, org.mockito.Mockito.times(1))
                .structured(anyString(), anyString(), anyString(), anyString(), eq(StreamlineAiService.SuggestionDrafts.class));

        // Acting on a card by hand leaves it out; the other card stays.
        send(put("/api/streamline-reviews/" + review + "/items/" + second), user, Map.of("verdict", "NEEDED")).andExpect(status().isOk());
        JsonNode afterVerdict = read(mockMvc.perform(get(path).header("Authorization", "Bearer " + user)).andExpect(status().isOk()));
        assertThat(afterVerdict.get("suggestions")).hasSize(1);
        assertThat(afterVerdict.get("suggestions").get(0).get("action").asText()).isEqualTo("QUESTION");

        // A dismissed card is removed from the saved set.
        send(post(path + "/dismiss"), user, Map.of("itemId", first, "action", "QUESTION")).andExpect(status().isNoContent());
        assertThat(read(mockMvc.perform(get(path).header("Authorization", "Bearer " + user)).andExpect(status().isOk()))
                .get("suggestions")).isEmpty();
    }

    @Test
    void sfc03_savedSuggestionsWorkWithoutAiAndStayWithTheirOwner() throws Exception {
        String user = consented("saved-no-ai");
        long goal = goalWithSteps(user, "Save a deposit", 1, 1);
        long review = read(send(post("/api/streamline-reviews"), user, Map.of("goalId", goal)).andExpect(status().isCreated())).get("id").asLong();
        String path = "/api/streamline-reviews/" + review + "/stages/1/suggestions";
        String code = read(mockMvc.perform(get("/api/streamline-reviews/" + review).header("Authorization", "Bearer " + user)))
                .get("items").get(0).get("code").asText();
        when(claudeClient.structured(anyString(), anyString(), anyString(), anyString(), eq(StreamlineAiService.SuggestionDrafts.class)))
                .thenReturn(new ClaudeClient.Structured<>(new StreamlineAiService.SuggestionDrafts(List.of(
                        new StreamlineAiService.SuggestionDraft(code, "QUESTION", "Is this needed?", "r", "", "", "", "", "", "", "", "", ""))),
                        "claude-opus-5-5", 10, 10, false));
        send(post(path), user, Map.of()).andExpect(status().isOk());

        send(put("/api/ai/consent"), user, Map.of("consent", false)).andExpect(status().isOk());
        assertThat(read(mockMvc.perform(get(path).header("Authorization", "Bearer " + user)).andExpect(status().isOk()))
                .get("suggestions")).hasSize(1);

        String other = consented("saved-other");
        mockMvc.perform(get(path).header("Authorization", "Bearer " + other)).andExpect(status().isNotFound());
        send(post(path + "/dismiss"), other, Map.of("itemId", 1, "action", "QUESTION")).andExpect(status().isNotFound());

        // D-32-2: a closed review keeps no saved suggestions.
        send(post("/api/streamline-reviews/" + review + "/abandon"), user, Map.of()).andExpect(status().isOk());
        mockMvc.perform(get(path).header("Authorization", "Bearer " + user)).andExpect(status().isNoContent());
        assertThat(jdbc.queryForObject("select count(*) from streamline_suggestion_sets where review_id = ?", Integer.class, review)).isZero();
    }

    @Test
    void sfc04_theStreamlineModelIsUsedForSuggestionsAndPicksOnly() throws Exception {
        send(put("/api/admin/ai-settings"), admin, Map.of("enabled", true, "model", "claude-opus-5-5", "monthlyBudgetUsd", 20,
                "streamlineModel", "gpt-4")).andExpect(status().isBadRequest());
        JsonNode settings = read(send(put("/api/admin/ai-settings"), admin, Map.of("enabled", true, "model", "claude-opus-5-5",
                "monthlyBudgetUsd", 20, "streamlineModel", "claude-haiku-5-5")).andExpect(status().isOk()));
        assertThat(settings.get("streamlineModel").asText()).isEqualTo("claude-haiku-5-5");

        String user = consented("model");
        long goal = goalWithSteps(user, "Save a deposit", 1, 1);
        long review = read(send(post("/api/streamline-reviews"), user, Map.of("goalId", goal)).andExpect(status().isCreated())).get("id").asLong();
        when(claudeClient.structured(anyString(), anyString(), anyString(), anyString(), eq(StreamlineAiService.SuggestionDrafts.class)))
                .thenReturn(new ClaudeClient.Structured<>(new StreamlineAiService.SuggestionDrafts(List.of()), "claude-haiku-5-5", 10, 10, false));
        send(post("/api/streamline-reviews/" + review + "/stages/1/suggestions"), user, Map.of()).andExpect(status().isOk());
        verify(claudeClient).structured(anyString(), eq("claude-haiku-5-5"), anyString(), anyString(), eq(StreamlineAiService.SuggestionDrafts.class));
        assertThat(jdbc.queryForObject("select model from streamline_suggestion_sets where review_id = ?", String.class, review))
                .isEqualTo("claude-haiku-5-5");

        // Blank clears it.
        assertThat(read(send(put("/api/admin/ai-settings"), admin, Map.of("enabled", true, "model", "claude-opus-5-5",
                "monthlyBudgetUsd", 20, "streamlineModel", " ")).andExpect(status().isOk())).get("streamlineModel").isNull()).isTrue();
    }

    // ---- V32 phase 3 (BRD 32): Draft all stages ----

    @Test
    void sfc08_draftAllMakesOneRequestKeepsTheStageLockAndDropsCardsAboutItemsCutEarlier() throws Exception {
        String user = consented("draft-all");
        long goal = goalWithSteps(user, "Save a deposit", 2, 0);
        long review = read(send(post("/api/streamline-reviews"), user, Map.of("goalId", goal)).andExpect(status().isCreated())).get("id").asLong();
        JsonNode items = read(mockMvc.perform(get("/api/streamline-reviews/" + review).header("Authorization", "Bearer " + user))).get("items");
        String a = items.get(0).get("code").asText();
        String b = items.get(1).get("code").asText();
        long bId = items.get(1).get("id").asLong();
        when(claudeClient.structured(anyString(), anyString(), anyString(), anyString(), eq(StreamlineAiService.StageDrafts.class)))
                .thenReturn(new ClaudeClient.Structured<>(new StreamlineAiService.StageDrafts(List.of(
                        new StreamlineAiService.StageDraft(1, List.of(card(a, "QUESTION"))),
                        new StreamlineAiService.StageDraft(2, List.of(card(b, "DELETE"), card(a, "KEEP"))),
                        new StreamlineAiService.StageDraft(3, List.of(card(b, "SIMPLIFY"), card(a, "SIMPLIFY"))),
                        new StreamlineAiService.StageDraft(9, List.of(card(a, "QUESTION"))))), "claude-opus-5-5", 10, 10, false));

        JsonNode drafted = read(send(post("/api/streamline-reviews/" + review + "/draft-all"), user, Map.of()).andExpect(status().isOk()));
        assertThat(drafted.get("stages")).hasSize(5);
        assertThat(drafted.get("stages").get(1).get("suggestions")).hasSize(2);
        verify(claudeClient, org.mockito.Mockito.times(1))
                .structured(anyString(), anyString(), anyString(), anyString(), eq(StreamlineAiService.StageDrafts.class));

        JsonNode drafts = read(mockMvc.perform(get("/api/streamline-reviews/" + review + "/drafts").header("Authorization", "Bearer " + user))
                .andExpect(status().isOk()));
        assertThat(drafts.get("stages").get(0).get("preview").asBoolean()).isFalse();
        assertThat(drafts.get("stages").get(1).get("preview").asBoolean()).isTrue();
        assertThat(drafts.get("canDraftAll").asBoolean()).isTrue();
        // The stage lock stays: a later stage's drafts are a preview, not suggestions to apply.
        mockMvc.perform(get("/api/streamline-reviews/" + review + "/stages/2/suggestions").header("Authorization", "Bearer " + user))
                .andExpect(status().isNoContent());

        List<Long> ids = new ArrayList<>();
        items.forEach(item -> ids.add(item.get("id").asLong()));
        send(put("/api/streamline-reviews/" + review + "/items/bulk"), user,
                Map.of("itemIds", ids, "requirementSource", "SELF", "requirementOwner", "Me", "verdict", "NEEDED")).andExpect(status().isOk());
        send(post("/api/streamline-reviews/" + review + "/stages/1/complete"), user, Map.of()).andExpect(status().isOk());
        JsonNode stage2 = read(mockMvc.perform(get("/api/streamline-reviews/" + review + "/stages/2/suggestions")
                .header("Authorization", "Bearer " + user)).andExpect(status().isOk()));
        assertThat(stage2.get("suggestions")).hasSize(2);
        assertThat(stage2.get("draftedBeforeChanges").asBoolean()).isTrue();

        // Deleting an item keeps the stage's other cards (the fingerprint covers all the review's items).
        send(post("/api/streamline-reviews/" + review + "/items/" + bId + "/delete"), user, Map.of()).andExpect(status().isOk());
        JsonNode afterDelete = read(mockMvc.perform(get("/api/streamline-reviews/" + review + "/stages/2/suggestions")
                .header("Authorization", "Bearer " + user)).andExpect(status().isOk()));
        assertThat(afterDelete.get("suggestions")).hasSize(1);
        assertThat(afterDelete.get("suggestions").get(0).get("action").asText()).isEqualTo("KEEP");

        // In stage 3 the card about the deleted item is gone; the other stays.
        send(post("/api/streamline-reviews/" + review + "/stages/2/not-needed"), user, Map.of("reason", "Enough was cut already"))
                .andExpect(status().isOk());
        JsonNode stage3 = read(mockMvc.perform(get("/api/streamline-reviews/" + review + "/stages/3/suggestions")
                .header("Authorization", "Bearer " + user)).andExpect(status().isOk()));
        assertThat(stage3.get("suggestions")).hasSize(1);
        assertThat(stage3.get("suggestions").get(0).get("code").asText()).isEqualTo(a);
    }

    @Test
    void sfc09_draftAllNeedsRoomInTheMonthlyBudget() throws Exception {
        String user = consented("draft-room");
        long goal = goalWithSteps(user, "Save a deposit", 1, 1);
        long review = read(send(post("/api/streamline-reviews"), user, Map.of("goalId", goal)).andExpect(status().isCreated())).get("id").asLong();
        jdbc.update("update ai_settings set monthly_budget_usd = 0.30");
        try {
            JsonNode drafts = read(mockMvc.perform(get("/api/streamline-reviews/" + review + "/drafts")
                    .header("Authorization", "Bearer " + user)).andExpect(status().isOk()));
            assertThat(drafts.get("canDraftAll").asBoolean()).isFalse();
            assertThat(drafts.get("unavailableReason").asText()).contains("does not have room");
            send(post("/api/streamline-reviews/" + review + "/draft-all"), user, Map.of()).andExpect(status().isBadRequest());
            verify(claudeClient, never()).structured(anyString(), anyString(), anyString(), anyString(), eq(StreamlineAiService.StageDrafts.class));
        } finally {
            jdbc.update("update ai_settings set monthly_budget_usd = 20.00");
        }
        String other = consented("draft-other");
        send(post("/api/streamline-reviews/" + review + "/draft-all"), other, Map.of()).andExpect(status().isNotFound());
        mockMvc.perform(get("/api/streamline-reviews/" + review + "/drafts").header("Authorization", "Bearer " + other))
                .andExpect(status().isNotFound());
    }

    private static StreamlineAiService.SuggestionDraft card(String code, String action) {
        return new StreamlineAiService.SuggestionDraft(code, action, "Detail", "Reason", "", "", "", "", "", "", "",
                "SIMPLIFY".equals(action) ? "Shorter " + code : "", "");
    }

    @Test
    void sa02_ruleCardsAreRankedByNeed() throws Exception {
        String user = register("ranked");
        long fewer = goalWithSteps(user, "Eight steps", 8, 0);
        long more = goalWithSteps(user, "Ten steps", 10, 0);
        JsonNode cards = read(mockMvc.perform(get("/api/streamline-reviews/candidates").header("Authorization", "Bearer " + user))
                .andExpect(status().isOk()));
        assertThat(cards).hasSize(2);
        assertThat(cards.get(0).get("anchorId").asLong()).isEqualTo(more);
        assertThat(cards.get(1).get("anchorId").asLong()).isEqualTo(fewer);
    }

    @Test
    void sac07_thePageAssistantKnowsTheReviewAndChecksOwnership() throws Exception {
        String user = consented("page");
        long goal = goalWithSteps(user, "Save a deposit", 1, 1);
        long review = read(send(post("/api/streamline-reviews"), user, Map.of("goalId", goal)).andExpect(status().isCreated())).get("id").asLong();
        Map<String, Object> body = Map.of("page", "STREAMLINE", "area", "page", "itemId", review);
        send(post("/api/ai/pages/assistant/check"), user, body).andExpect(status().isOk());

        String other = consented("page-other");
        send(post("/api/ai/pages/assistant/check"), other, body).andExpect(status().isNotFound());
    }

    // ---------- fixtures ----------

    private long goalWithSteps(String token, String title, int steps, int tasksInFirstStep) throws Exception {
        long area = id(send(post("/api/vision-areas"), token, Map.of("name", "Home " + UUID.randomUUID(), "priority", "HIGH", "status", "ACTIVE")));
        long dream = id(send(post("/api/dreams"), token, Map.of("visionAreaId", area, "title", "Own a home", "whyImportant", "It matters",
                "successDefinition", "Keys in hand", "dreamType", "LONG_TERM", "priority", "HIGH",
                "targetDate", LocalDate.now().plusMonths(6).toString(), "status", "ACTIVE", "scheduleMode", "BOTTOM_UP")));
        long goal = id(send(post("/api/goals"), token, Map.of("dreamId", dream, "title", title, "priority", "HIGH",
                "status", "IN_PROGRESS", "moonshot", false, "scheduleMode", "BOTTOM_UP")));
        for (int index = 1; index <= steps; index++) {
            long step = id(send(post("/api/steps"), token, Map.of("goalId", goal, "title", "Step " + index, "owner", "Me",
                    "sequenceNumber", index, "complex", false, "priority", "MEDIUM", "targetDate", LocalDate.now().plusDays(30).toString(),
                    "status", "NOT_STARTED")));
            for (int task = 1; index == 1 && task <= tasksInFirstStep; task++) {
                Map<String, Object> body = new HashMap<>(Map.of("stepId", step, "title", "Task " + task, "owner", "Me", "priority", "MEDIUM",
                        "dueDate", LocalDate.now().plusDays(10).toString(), "status", "NOT_STARTED", "progressPercent", 0));
                id(send(post("/api/tasks"), token, body));
            }
        }
        return goal;
    }

    private long firstStep(String token, long goal) throws Exception {
        JsonNode steps = read(mockMvc.perform(get("/api/steps").header("Authorization", "Bearer " + token)));
        JsonNode list = steps.has("content") ? steps.get("content") : steps;
        for (JsonNode step : list) {
            if (step.get("goalId").asLong() == goal && step.get("sequenceNumber").asInt() == 1) {
                return step.get("id").asLong();
            }
        }
        throw new AssertionError("No first step");
    }

    private String consented(String prefix) throws Exception {
        String token = register(prefix);
        send(put("/api/ai/consent"), token, Map.of("consent", true)).andExpect(status().isOk());
        return token;
    }

    private long id(ResultActions result) throws Exception {
        return read(result.andExpect(status().isCreated())).get("id").asLong();
    }

    private String register(String prefix) throws Exception {
        return read(mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("fullName", "Streamline AI User",
                                "email", prefix + "-" + System.nanoTime() + "@example.com", "password", PASSWORD))))
                .andExpect(status().isCreated())).get("token").asText();
    }

    private ResultActions send(MockHttpServletRequestBuilder request, String token, Object body) throws Exception {
        return mockMvc.perform(request.header("Authorization", "Bearer " + token).contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body)));
    }

    private JsonNode read(ResultActions result) throws Exception {
        return objectMapper.readTree(result.andReturn().getResponse().getContentAsString());
    }
}
