package com.visionmapping.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.visionmapping.security.audit.SecurityAlertService;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
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

/**
 * V23 (BRD 23.0.0) phase 4: custom roles with chosen permissions (UM-FR-08 to
 * UM-FR-16), used through the Users actions, with abuse cases 12, 14 and 15.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class RoleFlowTests {

    private static final String PASSWORD = "Steady-Harbor-Lamp-42";
    private static final String REASON = "Share the cohort work with assistants";
    private static final AtomicInteger NEXT_IP = new AtomicInteger();

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private SecurityAlertService alerts;

    @Test
    void theCatalogueAndBuiltInRolesAreShownAndLockedWhereTheyShouldBe() throws Exception {
        Person admin = person("ADMIN");

        JsonNode catalogue = read(call(get("/api/admin/permissions"), admin.token()).andExpect(status().isOk()));
        assertThat(catalogue.findValuesAsText("key")).contains("MANAGE_USERS", "COACH_COHORTS", "MANAGE_ROLES");
        JsonNode roles = read(call(get("/api/admin/roles"), admin.token()).andExpect(status().isOk()));
        Map<String, JsonNode> byKey = new HashMap<>();
        roles.forEach(role -> byKey.put(role.get("key").asText(), role));
        assertThat(byKey.get("ADMIN").get("editable").asBoolean()).isFalse();
        assertThat(byKey.get("USER").get("editable").asBoolean()).isFalse();
        assertThat(byKey.get("COACH").get("editable").asBoolean()).isTrue();
        assertThat(byKey.get("COACH").get("renameable").asBoolean()).isFalse();
        assertThat(byKey.get("ADMIN").get("holders").asLong()).isPositive();

        // Abuse case 15: the Admin role cannot be stripped; Participant is locked (Q-09); built-ins keep their names.
        send(put("/api/admin/roles/ADMIN"), admin.token(), role("Admin", List.of())).andExpect(status().isConflict());
        send(put("/api/admin/roles/USER"), admin.token(), role("Participant", List.of("VIEW_USERS"))).andExpect(status().isConflict());
        send(put("/api/admin/roles/COACH"), admin.token(), role("Head coach", List.of("COACH_COHORTS"))).andExpect(status().isBadRequest());
        call(delete("/api/admin/roles/COACH"), admin.token(), Map.of("reason", REASON)).andExpect(status().isConflict());
    }

    @Test
    void aCustomRoleGivesExactlyItsPermissionsAndChangesApplyAtOnce() throws Exception {
        Person admin = person("ADMIN");
        String name = "Assistant " + UUID.randomUUID().toString().substring(0, 6);
        JsonNode created = read(send(post("/api/admin/roles"), admin.token(), role(name, List.of("MANAGE_USERS", "MANAGE_COHORTS")))
                .andExpect(status().isCreated()));
        String key = created.get("key").asText();
        // UM-FR-07: View users comes with Manage users; Use own plan always.
        assertThat(created.get("permissions").toString()).contains("VIEW_USERS", "MANAGE_USERS", "MANAGE_COHORTS", "USE_OWN_PLAN");
        assertThat(created.get("privileged").asBoolean()).isTrue();
        send(post("/api/admin/roles"), admin.token(), role(name.toUpperCase(), List.of())).andExpect(status().isBadRequest());

        Person dara = person("USER");
        send(put("/api/admin/users/" + dara.id() + "/role"), admin.token(), Map.of("role", key, "reason", REASON))
                .andExpect(status().isOk());
        assertThat(jdbc.queryForObject("select two_factor_due_at from app_users where id = ?", java.sql.Timestamp.class, dara.id()))
                .isNotNull();

        call(get("/api/admin/cohorts"), dara.token()).andExpect(status().isOk());
        call(get("/api/admin/users"), dara.token()).andExpect(status().isOk());
        call(get("/api/admin/roles"), dara.token()).andExpect(status().isOk());
        call(get("/api/admin/ai-settings"), dara.token()).andExpect(status().isForbidden());
        call(get("/api/admin/security-events"), dara.token()).andExpect(status().isForbidden());
        // Abuse case 14: only Admins change roles, whatever else a role allows.
        send(post("/api/admin/roles"), dara.token(), role("Everything " + UUID.randomUUID().toString().substring(0, 6), List.of("MANAGE_AI_SETTINGS")))
                .andExpect(status().isForbidden());

        // UM-FR-04: removing a permission is refused on the holder's next request.
        send(put("/api/admin/roles/" + key), admin.token(), role(name, List.of("MANAGE_USERS"))).andExpect(status().isOk());
        call(get("/api/admin/cohorts"), dara.token()).andExpect(status().isForbidden());
        assertThat(lastRoleEventDetail("ROLE_UPDATED")).startsWith(key + ": -MANAGE_COHORTS");
    }

    @Test
    void aUserManagerCannotGiveARoleWithPermissionsTheyLack() throws Exception {
        Person admin = person("ADMIN");
        String managerKey = read(send(post("/api/admin/roles"), admin.token(),
                role("Manager " + UUID.randomUUID().toString().substring(0, 6), List.of("MANAGE_USERS")))
                .andExpect(status().isCreated())).get("key").asText();
        String auditorKey = read(send(post("/api/admin/roles"), admin.token(),
                role("Auditor " + UUID.randomUUID().toString().substring(0, 6), List.of("VIEW_SECURITY_LOG")))
                .andExpect(status().isCreated())).get("key").asText();
        // UM-FR-43: giving a role "View security log" raises an alert.
        assertThat(alerts.recentAlerts()).anyMatch(alert -> alert.contains("VIEW_SECURITY_LOG") && alert.contains(auditorKey));

        Person manager = person("USER");
        send(put("/api/admin/users/" + manager.id() + "/role"), admin.token(), Map.of("role", managerKey, "reason", REASON))
                .andExpect(status().isOk());
        Person manager2 = relogin(manager);
        Person participant = person("USER");

        // Abuse case 12.
        send(put("/api/admin/users/" + participant.id() + "/role"), manager2.token(), Map.of("role", auditorKey, "reason", REASON))
                .andExpect(status().isForbidden());
        send(put("/api/admin/users/" + participant.id() + "/role"), manager2.token(), Map.of("role", "COACH", "reason", REASON))
                .andExpect(status().isForbidden());
        send(put("/api/admin/users/" + participant.id() + "/role"), manager2.token(), Map.of("role", managerKey, "reason", REASON))
                .andExpect(status().isOk());
    }

    @Test
    void deletingARoleMovesItsHoldersFirst() throws Exception {
        Person admin = person("ADMIN");
        String key = read(send(post("/api/admin/roles"), admin.token(),
                role("Temporary " + UUID.randomUUID().toString().substring(0, 6), List.of("HANDLE_SUPPORT")))
                .andExpect(status().isCreated())).get("key").asText();
        Person holder = person("USER");
        send(put("/api/admin/users/" + holder.id() + "/role"), admin.token(), Map.of("role", key, "reason", REASON))
                .andExpect(status().isOk());

        call(delete("/api/admin/roles/" + key), admin.token(), Map.of("reason", REASON)).andExpect(status().isConflict());
        call(delete("/api/admin/roles/" + key), admin.token(), Map.of("moveTo", "USER", "reason", REASON))
                .andExpect(status().isNoContent());

        assertThat(jdbc.queryForObject("select role from app_users where id = ?", String.class, holder.id())).isEqualTo("USER");
        assertThat(jdbc.queryForObject("select two_factor_due_at from app_users where id = ?", java.sql.Timestamp.class, holder.id()))
                .isNull();
        assertThat(jdbc.queryForObject("select count(*) from roles where role_key = ?", Long.class, key)).isZero();
    }

    @Test
    void removingCoachingFromARoleNeedsANewCoachForItsCohorts() throws Exception {
        Person admin = person("ADMIN");
        String name = "Mentor " + UUID.randomUUID().toString().substring(0, 6);
        String key = read(send(post("/api/admin/roles"), admin.token(), role(name, List.of("COACH_COHORTS")))
                .andExpect(status().isCreated())).get("key").asText();
        Person mentor = person("USER");
        send(put("/api/admin/users/" + mentor.id() + "/role"), admin.token(), Map.of("role", key, "reason", REASON))
                .andExpect(status().isOk());
        long cohortId = read(send(post("/api/admin/cohorts"), admin.token(),
                Map.of("name", "Cohort " + UUID.randomUUID().toString().substring(0, 6), "coachEmail", mentor.email()))
                .andExpect(status().isCreated())).get("id").asLong();
        // Naming them coach did not replace the custom role.
        assertThat(jdbc.queryForObject("select role from app_users where id = ?", String.class, mentor.id())).isEqualTo(key);

        send(put("/api/admin/roles/" + key), admin.token(), role(name, List.of("HANDLE_SUPPORT"))).andExpect(status().isConflict());
        Map<String, Object> change = new HashMap<>();
        change.put("cohortId", cohortId);
        change.put("newCoachId", null);
        Map<String, Object> body = new HashMap<>(role(name, List.of("HANDLE_SUPPORT")));
        body.put("cohortChanges", List.of(change));
        send(put("/api/admin/roles/" + key), admin.token(), body).andExpect(status().isOk());
        assertThat(jdbc.queryForObject("select coach_id from cohorts where id = ?", Long.class, cohortId)).isNull();
    }

    // ---------- helpers ----------

    private record Person(long id, String email, String token) {
    }

    private static Map<String, Object> role(String name, List<String> permissions) {
        return Map.of("name", name, "description", "", "permissions", permissions, "reason", REASON);
    }

    private Person person(String role) throws Exception {
        String email = "roles-" + role.toLowerCase() + "-" + UUID.randomUUID() + "@example.com";
        mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("fullName", "Role " + role, "email", email, "password", PASSWORD))))
                .andExpect(status().isCreated());
        jdbc.update("update app_users set role = ? where email = ?", role, email);
        long id = jdbc.queryForObject("select id from app_users where email = ?", Long.class, email);
        return relogin(new Person(id, email, null));
    }

    private Person relogin(Person person) throws Exception {
        String token = read(mockMvc.perform(post("/api/auth/login").header("X-Forwarded-For", "10.94." + (NEXT_IP.incrementAndGet() % 250) + ".1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("email", person.email(), "password", PASSWORD))))
                .andExpect(status().isOk())).get("token").asText();
        return new Person(person.id(), person.email(), token);
    }

    private ResultActions call(MockHttpServletRequestBuilder request, String token) throws Exception {
        return mockMvc.perform(request.header("Authorization", "Bearer " + token));
    }

    private ResultActions call(MockHttpServletRequestBuilder request, String token, Object body) throws Exception {
        return send(request, token, body);
    }

    private ResultActions send(MockHttpServletRequestBuilder request, String token, Object body) throws Exception {
        return mockMvc.perform(request.header("Authorization", "Bearer " + token).contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body)));
    }

    private String lastRoleEventDetail(String type) {
        return jdbc.queryForObject("select detail from security_events where event_type = ? order by id desc limit 1", String.class, type);
    }

    private JsonNode read(ResultActions result) throws Exception {
        return objectMapper.readTree(result.andReturn().getResponse().getContentAsString());
    }
}
