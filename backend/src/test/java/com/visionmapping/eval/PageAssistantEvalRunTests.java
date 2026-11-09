package com.visionmapping.eval;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Pattern;
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
 * V15 (AH-NFR-12, AHC-08, AHC-18, AHC-25, AHC-28): runs the quality test set
 * through the real page assistant and helpers. It calls the Claude API and
 * costs money, so it runs only when PAGE_EVAL_ANTHROPIC_KEY is set:
 *
 *   PAGE_EVAL_ANTHROPIC_KEY=sk-ant-... [PAGE_EVAL_MODEL=claude-sonnet-5-5] \
 *     mvn test -Dtest=PageAssistantEvalRunTests
 *
 * It fails when an answer matches a case's mustNot pattern (a suggested score,
 * a picked quiz answer, a label on a person, a style called better), when a
 * Khmer case is answered without Khmer, or when a request fails. It writes
 * target/page-assistant-eval.md with every case and the cost per page, for the
 * AI governance lead.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@EnabledIfEnvironmentVariable(named = "PAGE_EVAL_ANTHROPIC_KEY", matches = "sk-ant-.+")
class PageAssistantEvalRunTests {

    private static final Pattern KHMER = Pattern.compile("[\\u1780-\\u17FF]");
    private static final String[] CHAINS = {"settlingScore", "fearScore", "criticismScore", "visionScore", "knowledgeScore", "resourcesScore"};

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void theQualityTestSetPassesItsChecks() throws Exception {
        String model = System.getenv().getOrDefault("PAGE_EVAL_MODEL", "claude-opus-5-5");
        String admin = register("page-eval-admin-" + System.nanoTime() + "@example.com");
        jdbcTemplate.update("update app_users set role = 'ADMIN' where email like 'page-eval-admin-%'");
        jdbcTemplate.update("update ai_settings set daily_message_limit = 500, plan_model = null, pages_off = ''");
        send(put("/api/admin/ai-settings/api-key"), admin, Map.of("apiKey", System.getenv("PAGE_EVAL_ANTHROPIC_KEY"))).andExpect(status().isOk());
        send(put("/api/admin/ai-settings"), admin, Map.of("enabled", true, "model", model, "monthlyBudgetUsd", 1000)).andExpect(status().isOk());

        List<String> rows = new ArrayList<>();
        List<String> problems = new ArrayList<>();
        Map<String, BigDecimal[]> perPage = new TreeMap<>();
        for (PageAssistantEvalSet.Case sample : PageAssistantEvalSet.load().cases()) {
            String prefix = "page-eval-" + sample.id();
            String user = register(prefix + "-" + System.nanoTime() + "@example.com");
            send(put("/api/ai/consent"), user, Map.of("consent", true)).andExpect(status().isOk());
            long userId = jdbcTemplate.queryForObject("select max(id) from app_users", Long.class);
            String answer;
            try {
                answer = run(sample, user, prefix);
            } catch (AssertionError | RuntimeException failure) {
                problems.add(sample.id() + ": request failed: " + failure.getMessage());
                continue;
            }
            for (String pattern : sample.mustNot()) {
                if (Pattern.compile(pattern, Pattern.CASE_INSENSITIVE).matcher(answer).find()) {
                    problems.add(sample.id() + ": matched '" + pattern + "'");
                }
            }
            if ("km".equals(sample.language()) && !KHMER.matcher(answer).find()) {
                problems.add(sample.id() + ": a Khmer case answered without Khmer");
            }
            BigDecimal cost = jdbcTemplate.queryForObject("select coalesce(sum(cost_usd), 0) from ai_usage_logs where user_id = ?",
                    BigDecimal.class, userId);
            BigDecimal[] totals = perPage.computeIfAbsent(sample.page(), key -> new BigDecimal[] {BigDecimal.ZERO, BigDecimal.ZERO});
            totals[0] = totals[0].add(cost);
            totals[1] = totals[1].add(BigDecimal.ONE);
            rows.add("| " + sample.id() + " | " + sample.page() + " | " + sample.language() + " | " + sample.kind() + " | $"
                    + cost.setScale(4, RoundingMode.HALF_UP) + " | " + answer.replace("|", "/").replace("\n", " ").substring(0,
                    Math.min(140, answer.length())) + " |");
        }

        StringBuilder report = new StringBuilder("# Page assistant quality test set run\n\n")
                .append("Date: ").append(LocalDate.now(ZoneId.systemDefault())).append(" · model: ").append(model).append("\n\n")
                .append("| Case | Page | Language | Kind | Cost | Start of the answer |\n| --- | --- | --- | --- | --- | --- |\n");
        rows.forEach(row -> report.append(row).append('\n'));
        report.append("\n## Average cost per request (AHC-18: assistant answers $0.01 or less, helpers $0.02 or less)\n\n");
        perPage.forEach((page, totals) -> report.append("- ").append(page).append(": $")
                .append(totals[0].divide(totals[1], 4, RoundingMode.HALF_UP)).append('\n'));
        report.append("\nProblems: ").append(problems.isEmpty() ? "none" : "\n- " + String.join("\n- ", problems)).append('\n');
        Files.writeString(Path.of("target", "page-assistant-eval.md"), report.toString());

        assertThat(problems).as("rule breaks; see target/page-assistant-eval.md").isEmpty();
    }

    /** Runs one case and returns all the text the participant would see. */
    private String run(PageAssistantEvalSet.Case sample, String user, String prefix) throws Exception {
        String language = sample.language();
        return switch (sample.page()) {
            case "MODULE" -> {
                jdbcTemplate.update("insert into module_completions (user_id, module_number, completed_at, created_at, updated_at) "
                        + "select id, 4, current_timestamp, current_timestamp, current_timestamp from app_users where email like ?", prefix + "-%");
                boolean written = sample.rows().stream().anyMatch(row -> row.values().stream().anyMatch(value -> !value.isBlank()));
                yield text(send(post("/api/ai/modules/" + sample.moduleNumber() + "/help"), user,
                        Map.of("mode", written ? "FEEDBACK" : "START", "rows", sample.rows(), "language", language)));
            }
            case "ASSESSMENT" -> {
                Map<String, Object> scores = new HashMap<>(Map.of("takenOn", LocalDate.now(ZoneId.systemDefault()).toString()));
                for (int index = 0; index < CHAINS.length; index++) {
                    scores.put(CHAINS[index], sample.scores().get(index));
                }
                send(put("/api/chain-assessments"), user, scores).andExpect(status().isOk());
                yield chat(user, language, "ASSESSMENT", "statement:fearScore", sample.question());
            }
            case "WORK_STYLE" -> {
                send(put("/api/work-style-profile"), user, Map.of("paceFastScore", sample.counts().get(0), "paceDeliberateScore",
                        sample.counts().get(1), "focusTaskScore", sample.counts().get(2), "focusPeopleScore", sample.counts().get(3)))
                        .andExpect(status().isOk());
                yield chat(user, language, "WORK_STYLE", sample.question().contains("pick") || sample.question().contains("ជ្រើស")
                        ? "question:4" : "result", sample.question());
            }
            case "OBSTACLES" -> {
                Map<String, String> form = new HashMap<>(sample.obstacle());
                yield text(send(post("/api/ai/obstacles/help?language=" + language), user, Map.of("mode", sample.mode(), "form", form)));
            }
            default -> {
                Map<String, Object> partner = new HashMap<>(Map.of("name", "Eval Partner", "supportType", "MENTOR", "status", "ACTIVE"));
                partner.putAll(sample.partner());
                long partnerId = read(send(post("/api/partners"), user, partner).andExpect(status().isCreated())).get("id").asLong();
                Map<String, String> form = new HashMap<>(sample.message());
                form.put("partnerId", String.valueOf(partnerId));
                yield text(send(post("/api/ai/communication/help?language=" + language), user, Map.of("mode", sample.mode(), "form", form)));
            }
        };
    }

    private String chat(String user, String language, String page, String area, String question) throws Exception {
        JsonNode turn = read(send(post("/api/ai/chat"), user, Map.of("message", question, "language", language, "assistantPage", page,
                "area", area)).andExpect(status().isOk()));
        return turn.get("reply").get("content").asText();
    }

    private String text(ResultActions result) throws Exception {
        result.andExpect(status().isOk());
        return result.andReturn().getResponse().getContentAsString();
    }

    private String register(String email) throws Exception {
        String content = mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("fullName", "Eval Tester", "email", email, "password", "Steady-Harbor-Lamp-42"))))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(content).get("token").asText();
    }

    private ResultActions send(MockHttpServletRequestBuilder request, String token, Object body) throws Exception {
        return mockMvc.perform(request.header("Authorization", "Bearer " + token).contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body)));
    }

    private JsonNode read(ResultActions result) throws Exception {
        return objectMapper.readTree(result.andReturn().getResponse().getContentAsString());
    }
}
