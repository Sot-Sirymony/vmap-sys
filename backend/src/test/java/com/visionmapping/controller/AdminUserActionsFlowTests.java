package com.visionmapping.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.visionmapping.security.PermissionService;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
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

/**
 * V23 (BRD 23.0.0) phase 3: changing roles, blocking and unblocking, and the
 * safeguards around them (UM-FR-29 to UM-FR-34, UM-FR-39, UM-FR-42; abuse cases 11, 13, 16).
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AdminUserActionsFlowTests {

    private static final String PASSWORD = "Steady-Harbor-Lamp-42";
    private static final String REASON = "Leads the February cohort";
    private static final AtomicInteger NEXT_IP = new AtomicInteger();

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
        jdbc.update("delete from role_permissions where role_key = 'COACH' and permission in ('VIEW_USERS', 'MANAGE_USERS')");
        permissions.evict();
    }

    // ---------- changing a role ----------

    @Test
    void anAdminMakesAParticipantACoachWithAReason() throws Exception {
        Person admin = person("ADMIN");
        Person dara = person("USER");

        JsonNode detail = read(send(put("/api/admin/users/" + dara.id() + "/role"), admin.token(),
                Map.of("role", "COACH", "reason", REASON)).andExpect(status().isOk()));

        assertThat(detail.get("account").get("role").asText()).isEqualTo("COACH");
        assertThat(detail.get("account").get("twoFactor").asText()).isEqualTo("DUE");
        assertThat(detail.get("roleChangedBy").asText()).isEqualTo("Person ADMIN");
        assertThat(lastEventDetail("ROLE_CHANGED", dara.id())).isEqualTo("USER -> COACH; reason: " + REASON);
        // The new role applies on Dara's next request: the coach workspace opens.
        mockMvc.perform(get("/api/coach/participants").header("Authorization", "Bearer " + dara.token()))
                .andExpect(result -> assertThat(result.getResponse().getStatus()).isNotEqualTo(403));
    }

    @Test
    void aRoleChangeNeedsAProperReasonAndAKnownNewRole() throws Exception {
        Person admin = person("ADMIN");
        Person dara = person("USER");

        send(put("/api/admin/users/" + dara.id() + "/role"), admin.token(), Map.of("role", "COACH", "reason", "short"))
                .andExpect(status().isBadRequest());
        send(put("/api/admin/users/" + dara.id() + "/role"), admin.token(), Map.of("role", "ROOT", "reason", REASON))
                .andExpect(status().isBadRequest());
        send(put("/api/admin/users/" + dara.id() + "/role"), admin.token(), Map.of("role", "USER", "reason", REASON))
                .andExpect(status().isBadRequest());
        send(put("/api/admin/users/99999999/role"), admin.token(), Map.of("role", "COACH", "reason", REASON))
                .andExpect(status().isNotFound());
    }

    @Test
    void nobodyChangesOrBlocksTheirOwnAccount() throws Exception {
        Person admin = person("ADMIN");

        send(put("/api/admin/users/" + admin.id() + "/role"), admin.token(), Map.of("role", "USER", "reason", REASON))
                .andExpect(status().isForbidden());
        send(post("/api/admin/users/" + admin.id() + "/block"), admin.token(), Map.of("reason", REASON))
                .andExpect(status().isForbidden());
    }

    @Test
    void demotingACoachWithActiveCohortsNeedsAChoiceForEachCohort() throws Exception {
        Person admin = person("ADMIN");
        Person coach = person("COACH");
        String cohortName = "Handover " + UUID.randomUUID().toString().substring(0, 6);
        long cohortId = read(send(post("/api/admin/cohorts"), admin.token(), Map.of("name", cohortName, "coachEmail", coach.email()))
                .andExpect(status().isCreated())).get("id").asLong();

        send(put("/api/admin/users/" + coach.id() + "/role"), admin.token(), Map.of("role", "USER", "reason", REASON))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString(cohortName)));
        assertThat(roleOf(coach.id())).isEqualTo("COACH");

        Map<String, Object> change = new HashMap<>();
        change.put("cohortId", cohortId);
        change.put("newCoachId", null);
        send(put("/api/admin/users/" + coach.id() + "/role"), admin.token(),
                Map.of("role", "USER", "reason", REASON, "cohortChanges", List.of(change))).andExpect(status().isOk());

        assertThat(roleOf(coach.id())).isEqualTo("USER");
        assertThat(jdbc.queryForObject("select coach_id from cohorts where id = ?", Long.class, cohortId)).isNull();
    }

    // ---------- no escalation (D-23-9) ----------

    @Test
    void aUserManagerCannotRaiseAnyoneAboveThemselvesOrTouchAnAdmin() throws Exception {
        jdbc.update("insert into role_permissions (role_key, permission) values ('COACH', 'VIEW_USERS'), ('COACH', 'MANAGE_USERS')");
        permissions.evict();
        Person manager = person("COACH");
        Person participant = person("USER");
        Person admin = person("ADMIN");

        // Abuse case 11: giving the Admin role.
        send(put("/api/admin/users/" + participant.id() + "/role"), manager.token(), Map.of("role", "ADMIN", "reason", REASON))
                .andExpect(status().isForbidden());
        // Abuse case 13: acting on an Admin.
        send(post("/api/admin/users/" + admin.id() + "/block"), manager.token(), Map.of("reason", REASON))
                .andExpect(status().isForbidden());
        send(put("/api/admin/users/" + admin.id() + "/role"), manager.token(), Map.of("role", "USER", "reason", REASON))
                .andExpect(status().isForbidden());
        assertThat(roleOf(admin.id())).isEqualTo("ADMIN");
        // Within their own permissions it works.
        send(put("/api/admin/users/" + participant.id() + "/role"), manager.token(), Map.of("role", "COACH", "reason", REASON))
                .andExpect(status().isOk());
        send(post("/api/admin/users/" + participant.id() + "/block"), manager.token(), Map.of("reason", REASON))
                .andExpect(status().isOk());
    }

    @Test
    void withoutManageUsersNothingChanges() throws Exception {
        Person coach = person("COACH");
        Person participant = person("USER");

        send(post("/api/admin/users/" + participant.id() + "/block"), coach.token(), Map.of("reason", REASON))
                .andExpect(status().isForbidden());
        send(post("/api/admin/users/" + participant.id() + "/block"), participant.token(), Map.of("reason", REASON))
                .andExpect(status().isForbidden());
    }

    // ---------- blocking ----------

    @Test
    void blockingEndsSessionsAndSignInSaysBlockedOnlyAfterTheRightPassword() throws Exception {
        Person admin = person("ADMIN");
        Person dara = person("USER");

        JsonNode blocked = read(send(post("/api/admin/users/" + dara.id() + "/block"), admin.token(), Map.of("reason", REASON))
                .andExpect(status().isOk()));
        assertThat(blocked.get("account").get("status").asText()).isEqualTo("DISABLED");
        assertThat(blocked.get("blockedBy").asText()).isEqualTo("Person ADMIN");

        // UM-FR-31: the open session is refused on its next request.
        mockMvc.perform(get("/api/tasks").header("Authorization", "Bearer " + dara.token())).andExpect(status().isUnauthorized());
        // UM-FR-32: right password → told it is blocked; wrong password → the usual answer.
        login(dara.email(), PASSWORD).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("This account is blocked. Contact your program admin."));
        login(dara.email(), "Wrong-Password-Here-1").andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Invalid email or password."));
        send(post("/api/admin/users/" + dara.id() + "/block"), admin.token(), Map.of("reason", REASON))
                .andExpect(status().isConflict());

        send(post("/api/admin/users/" + dara.id() + "/unblock"), admin.token(), Map.of("reason", "Cleared after a talk"))
                .andExpect(status().isOk());
        login(dara.email(), PASSWORD).andExpect(status().isOk());
        assertThat(lastEventDetail("USER_UNBLOCKED", dara.id())).isEqualTo("USER; reason: Cleared after a talk");
    }

    @Test
    void aBlockedOwnersShareLinksPauseAndComeBack() throws Exception {
        Person admin = person("ADMIN");
        Person owner = person("USER");
        long areaId = read(send(post("/api/vision-areas"), owner.token(), Map.of("name", "Career", "priority", "HIGH", "status", "ACTIVE")))
                .get("id").asLong();
        Map<String, Object> dream = new HashMap<>(Map.of("visionAreaId", areaId, "title", "Open a bakery", "whyImportant", "Reason",
                "successDefinition", "It is done", "letterRank", "A", "dreamType", "LONG_TERM", "priority", "MEDIUM",
                "status", "ACTIVE", "moonshot", false));
        dream.put("scheduleMode", "BOTTOM_UP");
        long dreamId = read(send(post("/api/dreams"), owner.token(), dream).andExpect(status().isCreated())).get("id").asLong();
        String link = read(send(post("/api/plan-shares"), owner.token(), Map.of("dreamId", dreamId)).andExpect(status().isCreated()))
                .get("token").asText();
        mockMvc.perform(get("/api/shared-plans/" + link)).andExpect(status().isOk());

        send(post("/api/admin/users/" + owner.id() + "/block"), admin.token(), Map.of("reason", REASON)).andExpect(status().isOk());
        mockMvc.perform(get("/api/shared-plans/" + link)).andExpect(status().isNotFound());

        send(post("/api/admin/users/" + owner.id() + "/unblock"), admin.token(), Map.of("reason", REASON)).andExpect(status().isOk());
        mockMvc.perform(get("/api/shared-plans/" + link)).andExpect(status().isOk());
    }

    // ---------- helpers ----------

    private record Person(long id, String email, String token) {
    }

    private Person person(String role) throws Exception {
        String email = "actions-" + role.toLowerCase() + "-" + UUID.randomUUID() + "@example.com";
        mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("fullName", "Person " + role, "email", email, "password", PASSWORD))))
                .andExpect(status().isCreated());
        jdbc.update("update app_users set role = ? where email = ?", role, email);
        long id = jdbc.queryForObject("select id from app_users where email = ?", Long.class, email);
        return new Person(id, email, read(login(email, PASSWORD).andExpect(status().isOk())).get("token").asText());
    }

    private ResultActions login(String email, String password) throws Exception {
        return mockMvc.perform(post("/api/auth/login").header("X-Forwarded-For", "10.93." + (NEXT_IP.incrementAndGet() % 250) + ".1")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("email", email, "password", password))));
    }

    private ResultActions send(MockHttpServletRequestBuilder request, String token, Object body) throws Exception {
        return mockMvc.perform(request.header("Authorization", "Bearer " + token).contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body)));
    }

    private String roleOf(long id) {
        return jdbc.queryForObject("select role from app_users where id = ?", String.class, id);
    }

    private String lastEventDetail(String type, long targetId) {
        return jdbc.queryForObject("select detail from security_events where event_type = ? and target_id = ? order by id desc limit 1",
                String.class, type, targetId);
    }

    private JsonNode read(ResultActions result) throws Exception {
        return objectMapper.readTree(result.andReturn().getResponse().getContentAsString());
    }
}
