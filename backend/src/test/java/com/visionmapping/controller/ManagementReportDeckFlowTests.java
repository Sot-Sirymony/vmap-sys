package com.visionmapping.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.visionmapping.support.PlanFixture;
import com.visionmapping.support.PlanFixture.Branch;
import java.io.ByteArrayInputStream;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.poi.sl.usermodel.Placeholder;
import org.apache.poi.xslf.usermodel.XMLSlideShow;
import org.apache.poi.xslf.usermodel.XSLFNotes;
import org.apache.poi.xslf.usermodel.XSLFShape;
import org.apache.poi.xslf.usermodel.XSLFSlide;
import org.apache.poi.xslf.usermodel.XSLFTable;
import org.apache.poi.xslf.usermodel.XSLFTextShape;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/**
 * V18 phase 3 (BRD 18.0.0, MR-FR-23 to MR-FR-31): the Management Report as a
 * PowerPoint deck, read back with Apache POI.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ManagementReportDeckFlowTests {

    private static final LocalDate TODAY = LocalDate.now();
    private static final List<String> TITLES = List.of("Executive summary", "Progress by goal", "Priorities and deadlines",
            "Blockers and management issues", "Overdue items", "Next actions", "Decisions and support needed");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void mrc09_eightSlidesInOrderWithTitlesNumbersAndNotes() throws Exception {
        String token = register("deck-slides");
        Branch plan = PlanFixture.branch(mockMvc, objectMapper, token);
        task(token, plan.stepId(), Map.of("title", "Clean the data", "status", "BLOCKED", "blockerReason", "Waiting for data",
                "nextAction", "Email the data office"));
        task(token, plan.stepId(), Map.of("title", "Run the model", "dueDate", TODAY.minusDays(4).toString()));
        task(token, plan.stepId(), Map.of("title", "Book the room", "dueDate", TODAY.plusDays(3).toString()));
        PlanFixture.createdId(mockMvc, objectMapper, token, "/api/obstacles", Map.of("title", "No budget", "obstacleType", "MONEY",
                "severity", "CRITICAL", "status", "OPEN", "relatedStepId", plan.stepId(), "conflictPrivateNote", "Private feelings"));
        PlanFixture.createdId(mockMvc, objectMapper, token, "/api/partners", Map.of("name", "Dr. Chan", "supportType", "MENTOR",
                "status", "TO_CONTACT", "email", "chan@example.com", "relatedStepId", plan.stepId()));

        MvcResult result = mockMvc.perform(get("/api/reports/management.pptx?dreamId=" + plan.dreamId())
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "application/vnd.openxmlformats-officedocument.presentationml.presentation"))
                .andExpect(header().string("Content-Disposition",
                        "attachment; filename=\"Management_Report_Dream_" + plan.dreamId() + "_" + TODAY + ".pptx\""))
                .andReturn();

        saveSample(result, "sample-management-report.pptx");
        try (XMLSlideShow deck = open(result)) {
            List<XSLFSlide> slides = deck.getSlides();
            assertThat(slides).hasSize(8);
            assertThat(deck.getPageSize().getWidth()).isEqualTo(960);
            assertThat(deck.getPageSize().getHeight()).isEqualTo(540);
            assertThat(titleOf(slides.get(0))).isEqualTo("Progress report");
            assertThat(allText(slides.get(0))).contains("Fixture dream · This month · as of", "Prepared by V18 Test");
            for (int index = 1; index < slides.size(); index++) {
                assertThat(slides.get(index).getTitle()).isEqualTo(TITLES.get(index - 1));
                assertThat(allText(slides.get(index))).contains((index + 1) + " / 8");
            }
            for (XSLFSlide slide : slides) {
                assertThat(notes(deck, slide)).isNotBlank();
            }
            assertThat(allText(slides.get(1))).contains("Off track: 1", "1 task(s) overdue.", "need a decision or support");
            assertThat(allText(slides.get(2))).contains("Fixture goal");
            XSLFTable priorities = slides.get(3).getShapes().stream().filter(XSLFTable.class::isInstance)
                    .map(XSLFTable.class::cast).findFirst().orElseThrow();
            assertThat(priorities.getNumberOfRows()).isEqualTo(5);
            assertThat(allText(slides.get(3))).contains("Book the room");
            String blockers = allText(slides.get(4));
            assertThat(blockers).contains("No budget", "Money, Critical", "Financial partner or sponsor",
                    "Blocked task: Clean the data", "Why: Waiting for data");
            assertThat(blockers.indexOf("No budget")).isLessThan(blockers.indexOf("Blocked task"));
            assertThat(allText(slides.get(5))).contains("Run the model", "4 day(s) late");
            assertThat(allText(slides.get(6))).contains("Email the data office");
            assertThat(allText(slides.get(7))).contains("No budget");

            // MRC-12: no private conflict note and no partner contact details anywhere, notes included.
            StringBuilder everything = new StringBuilder();
            for (XSLFSlide slide : slides) {
                everything.append(allText(slide)).append(notes(deck, slide));
            }
            assertThat(everything.toString()).doesNotContain("Private feelings", "chan@example.com");
        }
    }

    @Test
    void mrc09_mrc10_sevenSlidesWithNothingToDecideAndOverflowPointsToTheExcelReport() throws Exception {
        String token = register("deck-overflow");
        Branch plan = PlanFixture.branch(mockMvc, objectMapper, token);
        for (int index = 1; index <= 12; index++) {
            PlanFixture.createdId(mockMvc, objectMapper, token, "/api/obstacles", Map.of("title", "Blocker " + index,
                    "obstacleType", "TIME", "severity", "LOW", "status", "OPEN", "relatedStepId", plan.stepId()));
        }
        MvcResult options = mockMvc.perform(get("/api/reports/management.pptx").header("Authorization", "Bearer " + token)
                        .param("dreamId", String.valueOf(plan.dreamId())).param("title", "Quarterly review").param("includeName", "false"))
                .andExpect(status().isOk()).andReturn();
        saveSample(options, "sample-management-report-overflow.pptx");
        try (XMLSlideShow deck = open(options)) {
            assertThat(deck.getSlides()).hasSize(7);
            assertThat(titleOf(deck.getSlides().get(0))).isEqualTo("Quarterly review");
            assertThat(allText(deck.getSlides().get(0))).doesNotContain("Prepared by");
            assertThat(allText(deck.getSlides().get(4))).contains("Blocker 1", "and 6 more (see the Excel report)");
            assertThat(allText(deck.getSlides().get(5))).contains("Nothing is overdue");
        }
    }

    @Test
    void mrc14_khmerHeadingsAndAnEmptyReportStillHasEveryNeededSlide() throws Exception {
        String token = register("deck-km");
        try (XMLSlideShow deck = download(token, "?language=km")) {
            assertThat(deck.getSlides()).hasSize(7);
            assertThat(titleOf(deck.getSlides().get(0))).isEqualTo("របាយការណ៍វឌ្ឍនភាព");
            assertThat(deck.getSlides().get(2).getTitle()).isEqualTo("វឌ្ឍនភាពតាមគោលដៅ");
            assertThat(allText(deck.getSlides().get(2))).contains("មិនទាន់មានគោលដៅក្នុងរបាយការណ៍នេះទេ");
        }
    }

    @Test
    void optionsAreCheckedAndOtherUsersRecordsAnswer404() throws Exception {
        String owner = register("deck-owner");
        String other = register("deck-other");
        Branch plan = PlanFixture.branch(mockMvc, objectMapper, owner);
        mockMvc.perform(get("/api/reports/management.pptx?dreamId=" + plan.dreamId()).header("Authorization", "Bearer " + other))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/reports/management.pptx?title=" + "x".repeat(81)).header("Authorization", "Bearer " + owner))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/reports/management.pptx")).andExpect(status().isUnauthorized());
    }

    private XMLSlideShow download(String token, String query) throws Exception {
        return open(mockMvc.perform(get("/api/reports/management.pptx" + query).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andReturn());
    }

    /** Run with -Dreport.sample.dir=/some/folder to keep the file for a manual look in PowerPoint (MRC-09). */
    static void saveSample(MvcResult result, String name) throws Exception {
        String folder = System.getProperty("report.sample.dir");
        if (folder != null && !folder.isBlank()) {
            java.nio.file.Files.write(java.nio.file.Path.of(folder, name), result.getResponse().getContentAsByteArray());
        }
    }

    private static XMLSlideShow open(MvcResult result) throws Exception {
        return new XMLSlideShow(new ByteArrayInputStream(result.getResponse().getContentAsByteArray()));
    }

    private static String allText(XSLFSlide slide) {
        StringBuilder out = new StringBuilder();
        for (XSLFShape shape : slide.getShapes()) {
            if (shape instanceof XSLFTextShape textShape) {
                out.append(textShape.getText()).append('\n');
            } else if (shape instanceof XSLFTable table) {
                table.getRows().forEach(row -> row.getCells().forEach(cell -> out.append(cell.getText()).append('|')));
            }
        }
        return out.toString();
    }

    /** The title slide uses the centered title placeholder; content slides the regular one. Both are real titles. */
    private static String titleOf(XSLFSlide slide) {
        for (XSLFShape shape : slide.getShapes()) {
            if (shape instanceof XSLFTextShape textShape && (textShape.getTextType() == Placeholder.TITLE
                    || textShape.getTextType() == Placeholder.CENTERED_TITLE)) {
                return textShape.getText();
            }
        }
        return null;
    }

    private static String notes(XMLSlideShow deck, XSLFSlide slide) {
        XSLFNotes notes = deck.getNotesSlide(slide);
        StringBuilder out = new StringBuilder();
        for (XSLFTextShape shape : notes.getPlaceholders()) {
            if (shape.getTextType() == Placeholder.BODY) {
                out.append(shape.getText());
            }
        }
        return out.toString();
    }

    private void task(String token, long step, Map<String, Object> extra) throws Exception {
        Map<String, Object> body = new HashMap<>(Map.of("stepId", step, "title", "Task", "owner", "Me", "priority", "MEDIUM",
                "dueDate", TODAY.plusMonths(2).toString(), "status", "NOT_STARTED", "progressPercent", 0));
        body.putAll(extra);
        PlanFixture.createdId(mockMvc, objectMapper, token, "/api/tasks", body);
    }

    private String register(String prefix) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("fullName", "V18 Test",
                                "email", prefix + "-" + System.nanoTime() + "@example.com",
                                "password", "Steady-Harbor-Lamp-42"))))
                .andExpect(status().isCreated()).andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("token").asText();
    }
}
