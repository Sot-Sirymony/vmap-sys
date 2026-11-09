package com.visionmapping.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.ByteArrayInputStream;
import java.time.LocalDate;
import java.util.Map;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/**
 * Round-trips a small hierarchy: export one user's workbook, then import it
 * into a brand-new user and confirm every level is recreated and re-linked.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ExcelImportFlowTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void exportedWorkbookImportsIntoAFreshAccountAndRecreatesTheHierarchy() throws Exception {
        String author = registerAndToken("export-author");
        buildSampleHierarchy(author);

        byte[] workbook = mockMvc.perform(post("/api/excel/export")
                        .header("Authorization", "Bearer " + author))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsByteArray();

        String importer = registerAndToken("import-target");
        MockMultipartFile upload = new MockMultipartFile(
                "file", "vision-mapping.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", workbook);

        MvcResult importResult = mockMvc.perform(multipart("/api/excel/import").file(upload)
                        .header("Authorization", "Bearer " + importer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.createdRecords").value(5))
                .andExpect(jsonPath("$.skippedRecords").value(0))
                .andReturn();

        JsonNode summary = objectMapper.readTree(importResult.getResponse().getContentAsString());
        assert summary.get("rowsBySheet").get("Vision Areas").asInt() == 1;
        assert summary.get("rowsBySheet").get("Tasks").asInt() == 1;

        mockMvc.perform(get("/api/vision-areas").header("Authorization", "Bearer " + importer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].name").value("Career"));

        mockMvc.perform(get("/api/tasks").header("Authorization", "Bearer " + importer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].title").value("Search PubMed"));
    }

    /** V16 (TL-FR-49): the export adds a week-by-week Timeline sheet after Tasks; import ignores it. */
    @Test
    void exportIncludesATimelineSheetWithWeekBarsAfterTasks() throws Exception {
        String author = registerAndToken("timeline-export");
        buildSampleHierarchy(author);

        byte[] workbook = mockMvc.perform(post("/api/excel/export").header("Authorization", "Bearer " + author))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsByteArray();

        try (XSSFWorkbook book = new XSSFWorkbook(new ByteArrayInputStream(workbook))) {
            assertThat(book.getSheetIndex("Timeline")).isEqualTo(book.getSheetIndex("Tasks") + 1);
            Sheet timeline = book.getSheet("Timeline");
            Row header = timeline.getRow(0);
            assertThat(header.getCell(0).getStringCellValue()).isEqualTo("Level");
            assertThat(header.getCell(4).getStringCellValue()).isEqualTo("End");
            // Week columns start on a Monday and hold real dates.
            LocalDate firstWeek = header.getCell(5).getLocalDateTimeCellValue().toLocalDate();
            assertThat(firstWeek.getDayOfWeek()).isEqualTo(java.time.DayOfWeek.MONDAY);

            assertThat(timeline.getRow(1).getCell(0).getStringCellValue()).isEqualTo("Dream");
            assertThat(timeline.getRow(2).getCell(0).getStringCellValue()).isEqualTo("Goal");
            assertThat(timeline.getRow(3).getCell(0).getStringCellValue()).isEqualTo("Step");
            Row task = timeline.getRow(4);
            assertThat(task.getCell(0).getStringCellValue()).isEqualTo("Task");
            assertThat(task.getCell(2).getStringCellValue()).isEqualTo("      Search PubMed");
            // No start date: the task starts on its due date.
            LocalDate due = LocalDate.now().plusDays(10);
            assertThat(task.getCell(3).getLocalDateTimeCellValue().toLocalDate()).isEqualTo(due);
            assertThat(task.getCell(4).getLocalDateTimeCellValue().toLocalDate()).isEqualTo(due);
            // The step starts with its first task; its end is its own target date.
            assertThat(timeline.getRow(3).getCell(3).getLocalDateTimeCellValue().toLocalDate()).isEqualTo(due);

            // One rule shades the weeks, one marks the current week.
            assertThat(timeline.getSheetConditionalFormatting().getNumConditionalFormattings()).isEqualTo(2);
        }
    }

    /**
     * FR-54.2 / BR-43: the conflict worksheet's private note must never
     * appear in an exported workbook, in any sheet — checked across every
     * cell of every sheet, not just the Obstacles sheet, since BR-43 says
     * "regardless of export options."
     */
    @Test
    void conflictWorksheetPrivateNoteIsNeverIncludedInExcelExport() throws Exception {
        String token = registerAndToken("conflict-worksheet");
        String secretMarker = "SECRET-NEVER-EXPORTED-" + System.nanoTime();
        mockMvc.perform(post("/api/obstacles")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "title", "Disagreement with a mentor",
                                "obstacleType", "PARTNER",
                                "severity", "MEDIUM",
                                "status", "OPEN",
                                "conflictIncident", "A visible, non-secret incident note.",
                                "conflictPrivateNote", secretMarker))))
                .andExpect(status().isCreated());

        byte[] workbook = mockMvc.perform(post("/api/excel/export")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsByteArray();

        try (XSSFWorkbook xssf = new XSSFWorkbook(new ByteArrayInputStream(workbook))) {
            for (Sheet sheet : xssf) {
                for (Row row : sheet) {
                    for (Cell cell : row) {
                        if (cell.getCellType() == CellType.STRING) {
                            assertThat(cell.getStringCellValue()).doesNotContain(secretMarker);
                        }
                    }
                }
            }
        }
    }

    private void buildSampleHierarchy(String token) throws Exception {
        long areaId = postAndId("/api/vision-areas", token, Map.of(
                "name", "Career", "description", "Growth", "priority", "HIGH", "status", "ACTIVE"));
        long dreamId = postAndId("/api/dreams", token, Map.of(
                "visionAreaId", areaId, "title", "Become a researcher", "whyImportant", "Impact",
                "successDefinition", "Paper", "dreamType", "LONG_TERM", "priority", "HIGH",
                "targetDate", LocalDate.now().plusMonths(6).toString(), "status", "ACTIVE",
                "scheduleMode", "BOTTOM_UP"));
        long goalId = postAndId("/api/goals", token, Map.of(
                "dreamId", dreamId, "title", "Learn AI tools", "priority", "HIGH",
                "targetDate", LocalDate.now().plusMonths(3).toString(), "status", "NOT_STARTED",
                "scheduleMode", "BOTTOM_UP"));
        long stepId = postAndId("/api/steps", token, Map.of(
                "owner", "Me", "goalId", goalId, "title", "Search literature", "sequenceNumber", 1, "complex", true,
                "priority", "HIGH", "targetDate", LocalDate.now().plusMonths(2).toString(), "status", "NOT_STARTED"));
        postAndId("/api/tasks", token, Map.of(
                "stepId", stepId, "title", "Search PubMed", "owner", "Mony", "priority", "HIGH",
                "dueDate", LocalDate.now().plusDays(10).toString(), "status", "NOT_STARTED", "progressPercent", 0));
    }

    private String registerAndToken(String prefix) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "fullName", "Excel Test User",
                                "email", prefix + "-" + System.nanoTime() + "@example.com",
                                "password", "Steady-Harbor-Lamp-42"))))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("token").asText();
    }

    private long postAndId(String path, String token, Map<String, Object> body) throws Exception {
        MvcResult result = mockMvc.perform(post(path)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asLong();
    }
}
