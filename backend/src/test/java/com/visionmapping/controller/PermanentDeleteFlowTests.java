package com.visionmapping.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/**
 * Exercises permanent delete end-to-end against the real database: it must only
 * run on archived records, cascade through the whole hierarchy, and unlink (not
 * delete) the partner that pointed at a removed dream.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PermanentDeleteFlowTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void permanentlyDeletingAnArchivedVisionAreaCascadesAndUnlinksPartners() throws Exception {
        String token = registerAndToken("perma-delete");
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

        Map<String, Object> partnerBody = new HashMap<>();
        partnerBody.put("name", "Dr. Advisor");
        partnerBody.put("supportType", "MENTOR");
        partnerBody.put("status", "TO_CONTACT");
        partnerBody.put("relatedDreamId", dreamId);
        long partnerId = postAndId("/api/partners", token, partnerBody);

        // A non-archived record cannot be permanently deleted.
        mockMvc.perform(delete("/api/vision-areas/" + areaId + "/permanent")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isBadRequest());

        // Archive, then permanently delete.
        mockMvc.perform(delete("/api/vision-areas/" + areaId).header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());
        mockMvc.perform(delete("/api/vision-areas/" + areaId + "/permanent")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());

        // The whole hierarchy is gone, even when archived records are included.
        mockMvc.perform(get("/api/vision-areas?includeArchived=true").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
        mockMvc.perform(get("/api/tasks?includeArchived=true").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));

        // The partner survives, but its link to the deleted dream is cleared.
        mockMvc.perform(get("/api/partners/" + partnerId).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Dr. Advisor"))
                .andExpect(jsonPath("$.relatedDreamId").doesNotExist());
    }

    private String registerAndToken(String prefix) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "fullName", "Delete Test User",
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
