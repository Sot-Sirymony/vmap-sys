package com.visionmapping.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
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

/** FR-20: interface text an admin changes without a code change. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class UiTextFlowTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    /** Other test classes share this database. */
    @AfterEach
    void clear() {
        jdbcTemplate.update("DELETE FROM ui_text_overrides");
    }

    @Test
    void anAdminChangesTextThatEveryoneThenReadsEvenBeforeSignIn() throws Exception {
        String admin = register("text-admin", true);
        send(put("/api/admin/ui-text"), admin, Map.of("language", "km", "sourceText", "Today", "text", "  ថ្ងៃនេះរបស់ខ្ញុំ  "))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.text").value("ថ្ងៃនេះរបស់ខ្ញុំ"));
        // Saving the same string again replaces it rather than adding a second row.
        send(put("/api/admin/ui-text"), admin, Map.of("language", "km", "sourceText", "Today", "text", "ថ្ងៃនេះ!"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/ui-text").param("language", "km"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.Today").value("ថ្ងៃនេះ!"));
        mockMvc.perform(get("/api/ui-text").param("language", "en"))
                .andExpect(jsonPath("$.Today").doesNotExist());
    }

    @Test
    void resettingRestoresTheDefault() throws Exception {
        String admin = register("text-reset", true);
        long id = objectMapper.readTree(send(put("/api/admin/ui-text"), admin, Map.of("language", "en", "sourceText", "Goals", "text", "Targets"))
                .andReturn().getResponse().getContentAsString()).get("id").asLong();

        mockMvc.perform(delete("/api/admin/ui-text/" + id).header("Authorization", "Bearer " + admin)).andExpect(status().isNoContent());
        mockMvc.perform(get("/api/ui-text").param("language", "en")).andExpect(jsonPath("$.Goals").doesNotExist());
    }

    @Test
    void placeholdersMustSurviveTheEdit() throws Exception {
        String admin = register("text-placeholder", true);
        send(put("/api/admin/ui-text"), admin, Map.of("language", "en", "sourceText", "{count} days overdue", "text", "Late by some days"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Keep every placeholder from the original, such as {count}: {count}."));
        send(put("/api/admin/ui-text"), admin, Map.of("language", "en", "sourceText", "{count} days overdue", "text", "Late by {count} days"))
                .andExpect(status().isOk());
    }

    @Test
    void onlyAnAdminCanListOrChangeText() throws Exception {
        String user = register("text-user", false);
        mockMvc.perform(get("/api/admin/ui-text").param("language", "km").header("Authorization", "Bearer " + user))
                .andExpect(status().isForbidden());
        send(put("/api/admin/ui-text"), user, Map.of("language", "km", "sourceText", "Today", "text", "x"))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/ui-text").param("language", "fr")).andExpect(status().isBadRequest());
    }

    private ResultActions send(MockHttpServletRequestBuilder request, String token, Object body) throws Exception {
        return mockMvc.perform(request.header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body)));
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
