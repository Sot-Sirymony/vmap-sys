package com.visionmapping.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lowagie.text.pdf.LayoutProcessor;
import com.lowagie.text.pdf.PdfReader;
import com.lowagie.text.pdf.parser.PdfTextExtractor;
import com.visionmapping.support.PlanFixture;
import com.visionmapping.support.PlanFixture.Branch;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
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
 * V19 phase 4 (BRD 19.0.0): the PDF (RM-FR-42), download history (RM-FR-25),
 * share links and the public view (RM-FR-46 to RM-FR-48, RM-FR-51) and their
 * audit (RM-FR-52), through the API.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ReportShareFlowTests {

    private static final LocalDate TODAY = LocalDate.now();
    private static final String GONE = "This report is no longer shared.";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void rmc18_aFinalReportIsSharedReadOnlyWithoutSignInUntilStoppedOrExpired() throws Exception {
        String token = register("share-flow");
        Branch plan = PlanFixture.branch(mockMvc, objectMapper, token);
        task(token, plan.stepId(), Map.of("title", "First"));
        long id = created(send(post("/api/reports/saved"), token, Map.of("dreamId", plan.dreamId(), "name", "November progress")))
                .get("id").asLong();
        send(put("/api/reports/saved/" + id + "/commentary"), token, Map.of("highlights", "- Booked the hall", "nothingNeeded", false,
                "goalNotes", Map.of()));

        // A draft has nothing to share.
        send(post("/api/reports/saved/" + id + "/shares"), token, Map.of()).andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Only a final report can be shared. Finalise it first."));
        send(post("/api/reports/saved/" + id + "/finalize"), token, Map.of()).andExpect(status().isOk());
        send(post("/api/reports/saved/" + id + "/shares"), token, Map.of("expiresInDays", 14)).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("A share link lasts 7, 30 or 90 days."));

        JsonNode link = created(send(post("/api/reports/saved/" + id + "/shares"), token, Map.of()));
        String shared = link.get("token").asText();
        assertThat(shared).hasSizeGreaterThan(40);
        assertThat(link.get("sections")).extracting(JsonNode::asText).containsExactly("COMMENTARY", "GOAL_HEALTH", "CHANGES");
        assertThat(link.get("expiresAt").asText()).startsWith(TODAY.plusDays(30).toString().substring(0, 7));
        // The token is never stored or shown again.
        assertThat(jdbc.queryForObject("select count(*) from report_share_links where token_hash = ?", Integer.class, shared)).isZero();
        assertThat(list(token, id).get(0).get("token").isNull()).isTrue();

        JsonNode view = read(mockMvc.perform(get("/api/public/reports/" + shared))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Robots-Tag", "noindex, nofollow"))
                .andExpect(header().string("Referrer-Policy", "no-referrer"))
                .andExpect(header().string("Cache-Control", org.hamcrest.Matchers.containsString("no-store"))));
        assertThat(view.get("name").asText()).isEqualTo("November progress");
        assertThat(view.get("sharedBy").asText()).isEqualTo("V19");
        assertThat(view.get("commentary").get("highlights").asText()).isEqualTo("- Booked the hall");
        assertThat(view.get("report").get("goals")).hasSize(1);
        assertThat(view.get("report").get("attention")).isEmpty();
        assertThat(view.get("report").get("owners")).isEmpty();
        assertThat(view.get("rows").isNull()).isTrue();
        assertThat(list(token, id).get(0).get("viewCount").asInt()).isEqualTo(1);
        assertThat(list(token, id).get(0).get("lastViewedAt").isNull()).isFalse();

        MvcResult pdf = mockMvc.perform(get("/api/public/reports/" + shared + ".pdf"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "application/pdf"))
                .andExpect(header().string("X-Robots-Tag", "noindex, nofollow"))
                .andReturn();
        String text = pdfText(pdf.getResponse().getContentAsByteArray());
        assertThat(text).contains("November progress", "Booked the hall", "Progress by goal", "Page 1").doesNotContain("Blockers and");

        // A link with the Details rows ticked shows them; a stopped link shows nothing more.
        String detailed = created(send(post("/api/reports/saved/" + id + "/shares"), token,
                Map.of("expiresInDays", 7, "sections", List.of("DETAILS")))).get("token").asText();
        assertThat(read(mockMvc.perform(get("/api/public/reports/" + detailed)).andExpect(status().isOk())).get("rows")).hasSize(1);
        long linkId = link.get("id").asLong();
        send(patch("/api/reports/saved/" + id + "/shares/" + linkId + "/revoke"), token, Map.of()).andExpect(status().isOk())
                .andExpect(jsonPath("$.open").value(false));
        mockMvc.perform(get("/api/public/reports/" + shared)).andExpect(status().isNotFound()).andExpect(jsonPath("$.message").value(GONE));
        mockMvc.perform(get("/api/public/reports/" + shared + ".pdf")).andExpect(status().isNotFound());
        mockMvc.perform(get("/api/public/reports/not-a-real-token")).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value(GONE));

        // Expired links end on their own.
        jdbc.update("update report_share_links set expires_at = ? where report_id = ?",
                java.sql.Timestamp.valueOf(TODAY.minusDays(1).atStartOfDay()), id);
        mockMvc.perform(get("/api/public/reports/" + detailed)).andExpect(status().isNotFound());

        // RM-FR-52: made, opened (with no visitor recorded) and stopped are in the security log.
        Long userId = jdbc.queryForObject("select user_id from management_reports where id = ?", Long.class, id);
        assertThat(jdbc.queryForObject("select count(*) from security_events where event_type = 'SHARE_LINK_CREATED' "
                + "and target_type = 'report_share' and outcome = 'SUCCESS' and actor_id = ?", Integer.class, userId)).isEqualTo(2);
        // The refused attempts (a draft, a 14-day expiry) are logged as failures.
        assertThat(jdbc.queryForObject("select count(*) from security_events where event_type = 'SHARE_LINK_CREATED' "
                + "and target_type = 'report_share' and outcome = 'FAILURE' and actor_id = ?", Integer.class, userId)).isEqualTo(2);
        assertThat(jdbc.queryForObject("select count(*) from security_events where event_type = 'SHARE_LINK_REVOKED' "
                + "and target_type = 'report_share' and actor_id = ?", Integer.class, userId)).isEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from security_events where event_type = 'SHARE_LINK_OPENED' "
                + "and target_type = 'report_share' and outcome = 'SUCCESS' and target_id = ?", Integer.class, linkId)).isEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from security_events where event_type = 'SHARE_LINK_OPENED' "
                + "and target_type = 'report_share' and (ip_address is not null or actor_id is not null)", Integer.class)).isZero();
    }

    @Test
    void rmFr51_archivingStopsTheLinksAndAnUnverifiedReportCannotBeShared() throws Exception {
        String token = register("share-archive");
        Branch plan = PlanFixture.branch(mockMvc, objectMapper, token);
        long id = created(send(post("/api/reports/saved"), token, Map.of("dreamId", plan.dreamId()))).get("id").asLong();
        send(post("/api/reports/saved/" + id + "/finalize"), token, Map.of()).andExpect(status().isOk());
        String shared = created(send(post("/api/reports/saved/" + id + "/shares"), token, Map.of("expiresInDays", 90))).get("token").asText();
        mockMvc.perform(get("/api/public/reports/" + shared)).andExpect(status().isOk());

        send(patch("/api/reports/saved/" + id + "/archive"), token, Map.of()).andExpect(status().isOk());
        assertThat(list(token, id).get(0).get("open").asBoolean()).isFalse();
        mockMvc.perform(get("/api/public/reports/" + shared)).andExpect(status().isNotFound());
        send(post("/api/reports/saved/" + id + "/shares"), token, Map.of()).andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Restore this report before you share it."));
        // Restoring does not reopen a stopped link.
        send(patch("/api/reports/saved/" + id + "/restore"), token, Map.of()).andExpect(status().isOk());
        mockMvc.perform(get("/api/public/reports/" + shared)).andExpect(status().isNotFound());

        // RM-NFR-04: a report that fails its check blocks sharing and closes its links.
        String second = created(send(post("/api/reports/saved/" + id + "/shares"), token, Map.of())).get("token").asText();
        jdbc.update("update management_reports set snapshot_checksum = ? where id = ?", "0".repeat(64), id);
        send(post("/api/reports/saved/" + id + "/shares"), token, Map.of()).andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("This report could not be verified, so it cannot be shared."));
        mockMvc.perform(get("/api/public/reports/" + second)).andExpect(status().isNotFound());

        // Another user can neither list, make nor stop this report's links.
        String other = register("share-other");
        mockMvc.perform(get("/api/reports/saved/" + id + "/shares").header("Authorization", "Bearer " + other)).andExpect(status().isNotFound());
    }

    @Test
    void rmc17_rmc19_rmFr25_pdfsPrintBothLanguagesHoldNoPrivateFieldsAndDownloadsAreListed() throws Exception {
        String token = register("share-pdf");
        Branch plan = PlanFixture.branch(mockMvc, objectMapper, token);
        task(token, plan.stepId(), Map.of("title", "Clean the data", "status", "BLOCKED", "blockerReason", "Waiting for data"));
        task(token, plan.stepId(), Map.of("title", "Run the model", "dueDate", TODAY.minusDays(4).toString()));
        PlanFixture.createdId(mockMvc, objectMapper, token, "/api/obstacles", Map.of("title", "No budget", "obstacleType", "MONEY",
                "severity", "CRITICAL", "status", "OPEN", "relatedStepId", plan.stepId(), "conflictPrivateNote", "Private feelings"));
        PlanFixture.createdId(mockMvc, objectMapper, token, "/api/partners", Map.of("name", "Dr. Chan", "supportType", "MENTOR",
                "status", "TO_CONTACT", "email", "chan@example.com", "phone", "+855 12 345 678", "relatedStepId", plan.stepId()));

        // The live report's PDF.
        String live = pdfText(download(token, "/api/reports/management.pdf?dreamId=" + plan.dreamId()));
        assertThat(live).contains("Management report", "Live report", "No budget", "Run the model", "Page 1");

        long id = created(send(post("/api/reports/saved"), token, Map.of("dreamId", plan.dreamId(), "name", "Q4"))).get("id").asLong();
        String draft = pdfText(download(token, "/api/reports/saved/" + id + ".pdf"));
        assertThat(draft).contains("DRAFT ·", "Q4");
        send(post("/api/reports/saved/" + id + "/finalize"), token, Map.of()).andExpect(status().isOk());
        byte[] finalPdf = download(token, "/api/reports/saved/" + id + ".pdf");
        String text = pdfText(finalPdf);
        assertThat(text).contains("Final · frozen").doesNotContain("DRAFT");
        // RMC-19: no conflict note and no partner contact details in the PDF or the shared view.
        String shared = created(send(post("/api/reports/saved/" + id + "/shares"), token,
                Map.of("sections", List.of("COMMENTARY", "SUMMARY", "GOAL_HEALTH", "DETAILS", "BLOCKERS", "CHANGES", "NEXT_ACTIONS", "SUPPORT"))))
                .get("token").asText();
        String view = mockMvc.perform(get("/api/public/reports/" + shared)).andExpect(status().isOk()).andReturn().getResponse()
                .getContentAsString(StandardCharsets.UTF_8);
        for (String secret : List.of("Private feelings", "chan@example.com", "+855 12 345 678")) {
            assertThat(text).doesNotContain(secret);
            assertThat(view).doesNotContain(secret);
        }
        // RMC-17: the Khmer PDF embeds the Khmer font and still numbers its pages.
        byte[] khmer = download(token, "/api/reports/saved/" + id + ".pdf?language=km&sections=GOAL_HEALTH");
        assertThat(new String(khmer, StandardCharsets.ISO_8859_1)).contains("NotoSansKhmer");
        assertThat(new PdfReader(khmer).getNumberOfPages()).isPositive();
        download(token, "/api/reports/saved/" + id + ".xlsx");

        // RM-FR-25: the saved report's downloads, newest first; the live report keeps none.
        List<String> formats = new ArrayList<>();
        read(mockMvc.perform(get("/api/reports/saved/" + id + "/exports").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())).forEach(export -> formats.add(export.get("format").asText() + ":" + export.get("language").asText()));
        assertThat(formats).containsExactly("xlsx:en", "pdf:km", "pdf:en", "pdf:en");
    }

    // ---------- helpers ----------

    /**
     * The PDF's text. The app turns OpenPDF's layout engine on for Khmer
     * (PdfFonts), which stops the extractor making its built-in fonts, so it
     * is off while reading.
     */
    private static String pdfText(byte[] pdf) throws Exception {
        LayoutProcessor.disable();
        try {
            PdfReader reader = new PdfReader(pdf);
            PdfTextExtractor extractor = new PdfTextExtractor(reader);
            StringBuilder text = new StringBuilder();
            for (int page = 1; page <= reader.getNumberOfPages(); page++) {
                text.append(extractor.getTextFromPage(page)).append('\n');
            }
            return text.toString();
        } finally {
            LayoutProcessor.enable();
        }
    }

    private List<JsonNode> list(String token, long id) throws Exception {
        List<JsonNode> links = new ArrayList<>();
        read(mockMvc.perform(get("/api/reports/saved/" + id + "/shares").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())).forEach(links::add);
        return links;
    }

    private byte[] download(String token, String path) throws Exception {
        return mockMvc.perform(get(path).header("Authorization", "Bearer " + token)).andExpect(status().isOk()).andReturn()
                .getResponse().getContentAsByteArray();
    }

    private JsonNode created(ResultActions result) throws Exception {
        return read(result.andExpect(status().isCreated()));
    }

    private long task(String token, long step, Map<String, Object> extra) throws Exception {
        Map<String, Object> body = new HashMap<>(Map.of("stepId", step, "title", "Task", "owner", "Me", "priority", "MEDIUM",
                "dueDate", TODAY.plusMonths(2).toString(), "status", "NOT_STARTED", "progressPercent", 0));
        body.putAll(extra);
        return PlanFixture.createdId(mockMvc, objectMapper, token, "/api/tasks", body);
    }

    private ResultActions send(MockHttpServletRequestBuilder request, String token, Object body) throws Exception {
        return mockMvc.perform(request.header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(body)));
    }

    private JsonNode read(ResultActions result) throws Exception {
        return objectMapper.readTree(result.andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8));
    }

    private String register(String prefix) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("fullName", "V19 Test",
                                "email", prefix + "-" + System.nanoTime() + "@example.com",
                                "password", "Steady-Harbor-Lamp-42"))))
                .andExpect(status().isCreated()).andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("token").asText();
    }
}
