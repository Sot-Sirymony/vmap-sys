package com.visionmapping.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/** Search runs in the query, so it has to span every page and stay scoped to the caller. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PartnerSearchFlowTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void searchMatchesNameCaseInsensitivelyAndExcludesOthers() throws Exception {
        String token = registerAndToken("partner-search");
        createPartner(token, "Dr Maria Chan", "Epidemiologist", "MENTOR", "Statistics review");
        createPartner(token, "John Doe", "Accountant", "FINANCIAL", "Budget planning");

        mockMvc.perform(get("/api/partners?search=maria").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].name").value("Dr Maria Chan"));
    }

    @Test
    void searchAlsoMatchesNonNameFieldsLikeRoleAndStrength() throws Exception {
        String token = registerAndToken("partner-search-fields");
        createPartner(token, "Dr Maria Chan", "Epidemiologist", "MENTOR", "Statistics review");
        createPartner(token, "John Doe", "Accountant", "FINANCIAL", "Budget planning");

        mockMvc.perform(get("/api/partners?search=budget").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].name").value("John Doe"));
    }

    @Test
    void blankSearchReturnsEverything() throws Exception {
        String token = registerAndToken("partner-search-blank");
        createPartner(token, "Dr Maria Chan", "Epidemiologist", "MENTOR", "Statistics review");
        createPartner(token, "John Doe", "Accountant", "FINANCIAL", "Budget planning");

        mockMvc.perform(get("/api/partners?search=").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2));
    }

    @Test
    void searchNeverReachesAnotherUsersPartners() throws Exception {
        String owner = registerAndToken("partner-search-owner");
        createPartner(owner, "Dr Maria Chan", "Epidemiologist", "MENTOR", "Statistics review");

        String stranger = registerAndToken("partner-search-stranger");
        mockMvc.perform(get("/api/partners?search=maria").header("Authorization", "Bearer " + stranger))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0));
    }

    private void createPartner(String token, String name, String role, String supportType, String strength)
            throws Exception {
        mockMvc.perform(post("/api/partners")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "name", name,
                                "role", role,
                                "organization", "Health Institute",
                                "strength", strength,
                                "supportType", supportType,
                                "status", "TO_CONTACT"
                        ))))
                .andExpect(status().isCreated());
    }

    private String registerAndToken(String prefix) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "fullName", "Search Test User",
                                "email", prefix + "-" + System.nanoTime() + "@example.com",
                                "password", "Password123"
                        ))))
                .andExpect(status().isCreated())
                .andReturn();

        return objectMapper.readTree(result.getResponse().getContentAsString()).get("token").asText();
    }

    private String json(Object value) throws Exception {
        return objectMapper.writeValueAsString(value);
    }
}
