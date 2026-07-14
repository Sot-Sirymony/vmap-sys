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

/**
 * Filters run in the query, not in the browser. The client only ever holds one
 * page of rows, so a filter applied client-side would silently report only the
 * matches on the current page — these tests pin the behaviour that prevents it.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PartnerFilterFlowTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void filterSpansEveryPageNotJustTheLoadedOne() throws Exception {
        String token = registerAndToken("partner-filter-paging");
        createPartner(token, "Mentor One", "MENTOR", "ACTIVE");
        createPartner(token, "Accountant", "FINANCIAL", "ACTIVE");
        createPartner(token, "Mentor Two", "MENTOR", "ACTIVE");

        // One row per page: the two mentors cannot both be on the page being read,
        // so a correct total can only come from the query.
        mockMvc.perform(get("/api/partners?supportType=MENTOR&page=0&size=1")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.content.length()").value(1));
    }

    @Test
    void statusFilterExcludesOtherStatuses() throws Exception {
        String token = registerAndToken("partner-filter-status");
        createPartner(token, "Contacted Partner", "MENTOR", "CONTACTED");
        createPartner(token, "Declined Partner", "MENTOR", "DECLINED");

        mockMvc.perform(get("/api/partners?status=DECLINED").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].name").value("Declined Partner"));
    }

    @Test
    void filtersCombineWithSearch() throws Exception {
        String token = registerAndToken("partner-filter-search");
        createPartner(token, "Maria Mentor", "MENTOR", "ACTIVE");
        createPartner(token, "Maria Funder", "FINANCIAL", "ACTIVE");

        mockMvc.perform(get("/api/partners?search=maria&supportType=FINANCIAL")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].name").value("Maria Funder"));
    }

    @Test
    void noFilterReturnsEverything() throws Exception {
        String token = registerAndToken("partner-filter-none");
        createPartner(token, "Mentor One", "MENTOR", "ACTIVE");
        createPartner(token, "Accountant", "FINANCIAL", "DECLINED");

        mockMvc.perform(get("/api/partners").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2));
    }

    @Test
    void unknownEnumValueIsBadRequestNotServerError() throws Exception {
        String token = registerAndToken("partner-filter-bad-enum");

        mockMvc.perform(get("/api/partners?supportType=NOT_A_TYPE").header("Authorization", "Bearer " + token))
                .andExpect(status().isBadRequest());
    }

    @Test
    void filterNeverReachesAnotherUsersPartners() throws Exception {
        String owner = registerAndToken("partner-filter-owner");
        createPartner(owner, "Mentor One", "MENTOR", "ACTIVE");

        String stranger = registerAndToken("partner-filter-stranger");
        mockMvc.perform(get("/api/partners?supportType=MENTOR").header("Authorization", "Bearer " + stranger))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0));
    }

    private void createPartner(String token, String name, String supportType, String status) throws Exception {
        mockMvc.perform(post("/api/partners")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "name", name,
                                "role", "Advisor",
                                "organization", "Health Institute",
                                "supportType", supportType,
                                "status", status
                        ))))
                .andExpect(status().isCreated());
    }

    private String registerAndToken(String prefix) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "fullName", "Filter Test User",
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
