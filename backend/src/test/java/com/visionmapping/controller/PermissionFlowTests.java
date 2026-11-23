package com.visionmapping.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.visionmapping.security.PermissionService;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/**
 * V23 phase 1 (D-23-1, UM-FR-02 to UM-FR-05): access follows the role's
 * permissions in the database, not the role's name. Giving the Coach role one
 * extra permission opens exactly that feature and nothing else.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PermissionFlowTests {

    private static final String PASSWORD = "Steady-Harbor-Lamp-42";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private PermissionService permissions;

    @AfterEach
    void restoreCoach() {
        jdbc.update("delete from role_permissions where role_key = 'COACH' and permission = 'VIEW_SECURITY_LOG'");
        permissions.evict();
    }

    @Test
    void signInAnswersCarryTheRolesPermissions() throws Exception {
        JsonNode participant = register("perm-user-" + UUID.randomUUID() + "@example.com");

        assertThat(participant.get("permissions")).hasSize(1);
        assertThat(participant.get("permissions").get(0).asText()).isEqualTo("USE_OWN_PLAN");
    }

    @Test
    void aCoachGivenOnePermissionReachesThatFeatureOnly() throws Exception {
        String email = "perm-coach-" + UUID.randomUUID() + "@example.com";
        register(email);
        jdbc.update("update app_users set role = 'COACH' where email = ?", email);
        String coach = login(email);

        // Today's coach: no admin area at all.
        mockMvc.perform(get("/api/admin/security-events").header("Authorization", "Bearer " + coach)).andExpect(status().isForbidden());

        jdbc.update("insert into role_permissions (role_key, permission) values ('COACH', 'VIEW_SECURITY_LOG')");
        permissions.evict();

        // The next request follows the new permission (UM-FR-04) ...
        mockMvc.perform(get("/api/admin/security-events").header("Authorization", "Bearer " + coach)).andExpect(status().isOk());
        // ... and nothing else in the admin area opens with it.
        for (String path : List.of("/api/admin/cohorts", "/api/admin/ai-settings", "/api/admin/two-factor", "/api/admin/modules")) {
            mockMvc.perform(get(path).header("Authorization", "Bearer " + coach))
                    .andExpect(result -> assertThat(result.getResponse().getStatus()).as(path).isIn(403, 404));
        }
        // A fresh sign-in reports the new permission.
        JsonNode again = objectMapper.readTree(mockMvc.perform(post("/api/auth/login")
                        .header("X-Forwarded-For", "10.91." + (email.hashCode() & 0xff) + ".2").contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("email", email, "password", PASSWORD))))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertThat(again.get("permissions").toString()).contains("VIEW_SECURITY_LOG").contains("COACH_COHORTS");
    }

    @Test
    void managingRolesIsNeverGivenToAnotherRole() throws Exception {
        jdbc.update("insert into role_permissions (role_key, permission) values ('COACH', 'MANAGE_ROLES')");
        try {
            permissions.evict();
            assertThat(permissions.permissionsOf("COACH")).doesNotContain(com.visionmapping.security.Permission.MANAGE_ROLES);
        } finally {
            jdbc.update("delete from role_permissions where role_key = 'COACH' and permission = 'MANAGE_ROLES'");
        }
    }

    private JsonNode register(String email) throws Exception {
        return objectMapper.readTree(mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("fullName", "Permission User", "email", email, "password", PASSWORD))))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
    }

    private String login(String email) throws Exception {
        return objectMapper.readTree(mockMvc.perform(post("/api/auth/login")
                        .header("X-Forwarded-For", "10.91." + (email.hashCode() & 0xff) + ".1").contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("email", email, "password", PASSWORD))))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString()).get("token").asText();
    }

    private String json(Object value) throws Exception {
        return objectMapper.writeValueAsString(value);
    }
}
