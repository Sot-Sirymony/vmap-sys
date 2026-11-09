package com.visionmapping.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/** V8 Phase 4: Khmer module text, admin-only editing, reordering, and Khmer in the PDF. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ModuleContentAdminFlowTests {

    private static final List<Integer> JOURNEY = List.of(4, 1, 2, 3, 5, 6, 14, 15, 7, 8, 9, 10, 11, 12, 13);

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private String savedTitle;

    /** Other test classes share this database, so anything changed here is put back. */
    @AfterEach
    void restoreModules() {
        jdbcTemplate.update("UPDATE learning_modules SET position = position + 1000");
        for (int index = 0; index < JOURNEY.size(); index++) {
            jdbcTemplate.update("UPDATE learning_modules SET position = ? WHERE module_number = ?", index + 1, JOURNEY.get(index));
        }
        if (savedTitle != null) {
            jdbcTemplate.update("UPDATE learning_module_content SET title = ? WHERE module_number = 4 AND language = 'en'", savedTitle);
        }
    }

    @Test
    void everyModuleHasKhmerTextAndFieldLabels() throws Exception {
        String token = register("km", false);
        JsonNode modules = read(mockMvc.perform(get("/api/modules").param("language", "km").header("Authorization", "Bearer " + token)));
        modules.forEach(module -> assertThat(module.get("title").asText()).matches(".*[\\u1780-\\u17FF].*"));

        Integer count = jdbcTemplate.queryForObject(
                "select count(*) from learning_module_content k join learning_module_content e on e.module_number = k.module_number and e.language = 'en' "
                        + "where k.language = 'km' and (k.fields_json is null) = (e.fields_json is null)", Integer.class);
        assertThat(count).isEqualTo(15);
    }

    @Test
    void anUnknownLanguageFallsBackToEnglish() throws Exception {
        String token = register("fallback", false);
        mockMvc.perform(get("/api/modules").param("language", "fr").header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$[0].title").value("See Your Dream Clearly"));
    }

    @Test
    void onlyAnAdminCanEditOrReorderOrEvenList() throws Exception {
        String user = register("not-admin", false);
        mockMvc.perform(get("/api/admin/modules").header("Authorization", "Bearer " + user)).andExpect(status().isForbidden());
        send(put("/api/admin/modules/4/content/en"), user, content("Hacked")).andExpect(status().isForbidden());
        send(put("/api/admin/modules/order"), user, Map.of("orderedNumbers", JOURNEY)).andExpect(status().isForbidden());
        assertThat(jdbcTemplate.queryForObject("select title from learning_module_content where module_number = 4 and language = 'en'", String.class)).isNotEqualTo("Hacked");
    }

    @Test
    void anAdminEditShowsUpForUsers() throws Exception {
        savedTitle = jdbcTemplate.queryForObject("select title from learning_module_content where module_number = 4 and language = 'en'", String.class);
        String admin = register("admin", true);
        send(put("/api/admin/modules/4/content/en"), admin, content("A Clearer Title")).andExpect(status().isOk());

        String user = register("reader", false);
        mockMvc.perform(get("/api/modules/4").header("Authorization", "Bearer " + user))
                .andExpect(jsonPath("$.title").value("A Clearer Title"));
        send(put("/api/admin/modules/4/content/fr"), admin, content("Non")).andExpect(status().isBadRequest());
    }

    @Test
    void reorderingKeepsProgressAndRejectsAnIncompleteList() throws Exception {
        String admin = register("order", true);
        send(put("/api/admin/modules/order"), admin, Map.of("orderedNumbers", List.of(1, 4))).andExpect(status().isBadRequest());

        List<Integer> changed = new ArrayList<>(JOURNEY);
        changed.remove(Integer.valueOf(1));
        changed.add(0, 1);
        send(put("/api/admin/modules/order"), admin, Map.of("orderedNumbers", changed)).andExpect(status().isNoContent());

        JsonNode modules = read(mockMvc.perform(get("/api/modules").header("Authorization", "Bearer " + admin)));
        assertThat(modules.get(0).get("number").asInt()).isEqualTo(1);
        assertThat(modules.get(0).get("status").asText()).isEqualTo("AVAILABLE");
        assertThat(modules.get(1).get("status").asText()).isEqualTo("LOCKED");
    }

    @Test
    void aPlanWithKhmerTextEmbedsTheKhmerFont() throws Exception {
        String token = register("pdf-km", false);
        long areaId = id(send(post("/api/vision-areas"), token, Map.of("name", "អាជីព", "priority", "HIGH", "status", "ACTIVE")));
        long dreamId = id(send(post("/api/dreams"), token, Map.of("visionAreaId", areaId, "title", "ក្តីស្រមៃរបស់ខ្ញុំ Plan", "dreamType", "LONG_TERM",
                "priority", "MEDIUM", "status", "IDEA", "moonshot", false, "scheduleMode", "BOTTOM_UP", "targetDate", LocalDate.now().plusYears(1).toString())));

        byte[] pdf = mockMvc.perform(get("/api/dreams/" + dreamId + "/plan.pdf").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsByteArray();
        assertThat(new String(pdf, java.nio.charset.StandardCharsets.ISO_8859_1)).contains("NotoSansKhmer");
    }

    private Map<String, Object> content(String title) {
        return Map.of("title", title, "summary", "S", "guidance", "G", "exerciseInstructions", "E");
    }

    private ResultActions send(MockHttpServletRequestBuilder request, String token, Object body) throws Exception {
        return mockMvc.perform(request.header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body)));
    }

    private JsonNode read(ResultActions result) throws Exception {
        return objectMapper.readTree(result.andReturn().getResponse().getContentAsString());
    }

    private long id(ResultActions result) throws Exception {
        return read(result.andExpect(status().isCreated())).get("id").asLong();
    }

    private String register(String prefix, boolean admin) throws Exception {
        String email = prefix + "-" + System.nanoTime() + "@example.com";
        String content = mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("fullName", "V8 Test User", "email", email, "password", "Steady-Harbor-Lamp-42"))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        if (admin) {
            jdbcTemplate.update("update app_users set role = 'ADMIN' where email = ?", email);
        }
        return objectMapper.readTree(content).get("token").asText();
    }
}
