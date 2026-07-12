package com.visionmapping.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDate;
import java.util.Map;
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

    private void buildSampleHierarchy(String token) throws Exception {
        long areaId = postAndId("/api/vision-areas", token, Map.of(
                "name", "Career", "description", "Growth", "priority", "HIGH", "status", "ACTIVE"));
        long dreamId = postAndId("/api/dreams", token, Map.of(
                "visionAreaId", areaId, "title", "Become a researcher", "whyImportant", "Impact",
                "successDefinition", "Paper", "dreamType", "LONG_TERM", "priority", "HIGH",
                "targetDate", LocalDate.now().plusMonths(6).toString(), "status", "ACTIVE"));
        long goalId = postAndId("/api/goals", token, Map.of(
                "dreamId", dreamId, "title", "Learn AI tools", "priority", "HIGH",
                "targetDate", LocalDate.now().plusMonths(3).toString(), "status", "NOT_STARTED"));
        long stepId = postAndId("/api/steps", token, Map.of(
                "goalId", goalId, "title", "Search literature", "sequenceNumber", 1, "complex", true,
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
                                "password", "Password123"))))
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
