package com.visionmapping.eval;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * V11 (PB-NFR-08, PAC-04, PAC-12): runs the 20 sample dreams through the real
 * goal and step builders. It calls the Claude API and costs money, so it runs
 * only when PLAN_EVAL_ANTHROPIC_KEY is set:
 *
 *   PLAN_EVAL_ANTHROPIC_KEY=sk-ant-... [PLAN_EVAL_MODEL=claude-sonnet-5-5] \
 *     mvn test -Dtest=PlanBuilderEvalRunTests
 *
 * It fails on hard rule breaks (a date outside its limits, a Khmer dream
 * answered in English) and writes target/plan-builder-eval.md with every
 * answer's counts and the measured cost, for the AI governance lead.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@EnabledIfEnvironmentVariable(named = "PLAN_EVAL_ANTHROPIC_KEY", matches = "sk-ant-.+")
class PlanBuilderEvalRunTests {

    private static final String KHMER = "[\\u1780-\\u17FF]";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void theSampleDreamsGetSoundPlans() throws Exception {
        String model = System.getenv().getOrDefault("PLAN_EVAL_MODEL", "claude-opus-5-5");
        String admin = register("eval-admin-" + System.nanoTime() + "@example.com");
        jdbcTemplate.update("update app_users set role = 'ADMIN' where email like 'eval-admin-%'");
        jdbcTemplate.update("update ai_settings set daily_message_limit = 500, plan_model = null");
        send(put("/api/admin/ai-settings/api-key"), admin, Map.of("apiKey", System.getenv("PLAN_EVAL_ANTHROPIC_KEY")))
                .andExpect(status().isOk());
        send(put("/api/admin/ai-settings"), admin, Map.of("enabled", true, "model", model, "monthlyBudgetUsd", 1000))
                .andExpect(status().isOk());

        LocalDate today = LocalDate.now();
        List<String> rows = new ArrayList<>();
        List<String> problems = new ArrayList<>();
        BigDecimal total = BigDecimal.ZERO;
        for (PlanBuilderEvalSet.Dream sample : PlanBuilderEvalSet.load().dreams()) {
            String user = register("eval-" + sample.id() + "-" + System.nanoTime() + "@example.com");
            send(put("/api/ai/consent"), user, Map.of("consent", true)).andExpect(status().isOk());
            long userId = jdbcTemplate.queryForObject("select max(id) from app_users", Long.class);
            LocalDate dreamDate = sample.months() > 0 ? today.plusMonths(sample.months()) : null;
            long dreamId = dream(user, sample, dreamDate);

            JsonNode goals = read(send(post("/api/ai/plan/dreams/" + dreamId + "/goal-suggestions?language=" + sample.language()),
                    user, Map.of()).andExpect(status().isOk()));
            int goalCount = goals.get("goals").size();
            boolean asked = goals.hasNonNull("clarifyingQuestion");
            int stepCount = 0;
            if (!sample.vague() && goalCount == 0) {
                problems.add(sample.id() + ": no goals for a clear dream");
            }
            for (JsonNode goal : goals.get("goals")) {
                checkDate(problems, sample.id() + " goal", goal.get("targetDate"), today, dreamDate);
                if (sample.language().equals("km") && !goal.get("title").asText().matches(".*" + KHMER + ".*")) {
                    problems.add(sample.id() + ": goal not in Khmer: " + goal.get("title").asText());
                }
            }
            if (goalCount > 0) {
                JsonNode first = goals.get("goals").get(0);
                Map<String, Object> body = new HashMap<>(Map.of("title", first.get("title").asText()));
                if (first.hasNonNull("targetDate")) {
                    body.put("targetDate", first.get("targetDate").asText());
                }
                JsonNode steps = read(send(post("/api/ai/plan/dreams/" + dreamId + "/draft-step-suggestions?language="
                        + sample.language()), user, body).andExpect(status().isOk()));
                stepCount = steps.get("steps").size();
                LocalDate goalDate = first.hasNonNull("targetDate") ? LocalDate.parse(first.get("targetDate").asText()) : dreamDate;
                for (JsonNode step : steps.get("steps")) {
                    checkDate(problems, sample.id() + " step", step.get("targetDate"), today, goalDate);
                }
            }
            BigDecimal cost = jdbcTemplate.queryForObject("select coalesce(sum(cost_usd), 0) from ai_usage_logs where user_id = ?",
                    BigDecimal.class, userId);
            total = total.add(cost);
            rows.add("| " + sample.id() + " | " + sample.language() + " | " + (sample.vague() ? "yes" : "no") + " | " + goalCount
                    + " | " + (asked ? "yes" : "no") + " | " + stepCount + " | $" + cost.setScale(4, java.math.RoundingMode.HALF_UP) + " |");
        }

        StringBuilder report = new StringBuilder("# Plan builder sample run\n\n")
                .append("Date: ").append(today).append(" · model: ").append(model).append("\n\n")
                .append("Each dream: one goal suggestion request, then steps for the first suggested goal.\n\n")
                .append("| Dream | Language | Vague | Goals | Asked a question | Steps | Cost |\n| --- | --- | --- | --- | --- | --- | --- |\n");
        rows.forEach(row -> report.append(row).append('\n'));
        report.append("\nTotal cost: $").append(total.setScale(4, java.math.RoundingMode.HALF_UP))
                .append(" · average per dream: $").append(total.divide(BigDecimal.valueOf(rows.size()), 4, java.math.RoundingMode.HALF_UP))
                .append("\n\nProblems: ").append(problems.isEmpty() ? "none" : "\n- " + String.join("\n- ", problems)).append('\n');
        Files.writeString(Path.of("target", "plan-builder-eval.md"), report.toString());

        assertThat(problems).as("rule breaks; see target/plan-builder-eval.md").isEmpty();
    }

    private static void checkDate(List<String> problems, String what, JsonNode value, LocalDate today, LocalDate latest) {
        if (value == null || value.isNull()) {
            return;
        }
        LocalDate date = LocalDate.parse(value.asText());
        if (!date.isAfter(today) || (latest != null && date.isAfter(latest))) {
            problems.add(what + " dated " + date + " outside " + today + " to " + latest);
        }
    }

    private long dream(String token, PlanBuilderEvalSet.Dream sample, LocalDate targetDate) throws Exception {
        long areaId = read(send(post("/api/vision-areas"), token, Map.of("name", sample.area(), "priority", "HIGH", "status", "ACTIVE")))
                .get("id").asLong();
        Map<String, Object> body = new HashMap<>(Map.of("visionAreaId", areaId, "title", sample.title(), "dreamType", "LONG_TERM",
                "priority", "MEDIUM", "status", "IDEA", "moonshot", false, "scheduleMode", "BOTTOM_UP"));
        if (!sample.whyImportant().isBlank()) {
            body.put("whyImportant", sample.whyImportant());
        }
        if (!sample.successDefinition().isBlank()) {
            body.put("successDefinition", sample.successDefinition());
        }
        if (targetDate != null) {
            body.put("targetDate", targetDate.toString());
        }
        return read(send(post("/api/dreams"), token, body).andExpect(status().isCreated())).get("id").asLong();
    }

    private String register(String email) throws Exception {
        String content = mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("fullName", "Eval Tester", "email", email, "password", "Password123"))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(content).get("token").asText();
    }

    private ResultActions send(MockHttpServletRequestBuilder request, String token, Object body) throws Exception {
        return mockMvc.perform(request.header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body)));
    }

    private JsonNode read(ResultActions result) throws Exception {
        return objectMapper.readTree(result.andReturn().getResponse().getContentAsString());
    }
}
