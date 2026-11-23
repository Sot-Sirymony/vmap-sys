package com.visionmapping.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.visionmapping.dto.response.AdminUserDetailResponse;
import com.visionmapping.dto.response.AdminUserPageResponse;
import com.visionmapping.dto.response.AdminUserSummaryResponse;
import com.visionmapping.security.PermissionService;
import java.lang.reflect.RecordComponent;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
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

/** V23 (BRD 23.0.0) phase 2: the Users list and detail (UM-FR-17 to UM-FR-20), and D-23-3 privacy. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AdminUserFlowTests {

    private static final String PASSWORD = "Steady-Harbor-Lamp-42";
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
        jdbc.update("delete from role_permissions where role_key = 'COACH' and permission = 'VIEW_USERS'");
        permissions.evict();
    }

    @Test
    void anAdminFindsAnAccountByNameOrEmailAndSeesItsAccountDetails() throws Exception {
        String admin = admin();
        String tag = UUID.randomUUID().toString().substring(0, 8);
        String email = "find-" + tag + "@example.com";
        register(email, "Dara " + tag);
        login(email);

        JsonNode byEmail = list(admin, "q", "FIND-" + tag.toUpperCase(Locale.ROOT));
        assertThat(byEmail.get("totalElements").asLong()).isEqualTo(1);
        JsonNode row = byEmail.get("items").get(0);
        assertThat(row.get("email").asText()).isEqualTo(email);
        assertThat(row.get("role").asText()).isEqualTo("USER");
        assertThat(row.get("roleName").asText()).isEqualTo("Participant");
        assertThat(row.get("status").asText()).isEqualTo("ACTIVE");
        assertThat(row.get("twoFactor").asText()).isEqualTo("OFF");
        assertThat(row.get("lastSignInAt").isNull()).isFalse();
        assertThat(list(admin, "q", "dara " + tag).get("totalElements").asLong()).isEqualTo(1);
        // Typed text is literal: "%" does not match everything.
        assertThat(list(admin, "q", "%").get("totalElements").asLong()).isZero();
        // Counts cover every account, not just the page.
        assertThat(byEmail.get("totalAccounts").asLong()).isGreaterThan(1);
        assertThat(byEmail.get("countsByRole").get("ADMIN").asLong()).isPositive();
    }

    @Test
    void theListFiltersByRoleStatusAndTwoStepAndSortsAndPages() throws Exception {
        String admin = admin();
        String coach = "coach-" + UUID.randomUUID() + "@example.com";
        register(coach, "Filter Coach");
        jdbc.update("update app_users set role = 'COACH', two_factor_due_at = CURRENT_TIMESTAMP where email = ?", coach);
        String blocked = "blocked-" + UUID.randomUUID() + "@example.com";
        register(blocked, "Filter Blocked");
        jdbc.update("update app_users set status = 'DISABLED' where email = ?", blocked);

        assertThat(emails(list(admin, "role", "COACH", "size", "100"))).contains(coach).doesNotContain(blocked);
        assertThat(emails(list(admin, "status", "DISABLED", "size", "100"))).contains(blocked).doesNotContain(coach);
        assertThat(emails(list(admin, "twoFactor", "DUE", "size", "100"))).contains(coach);
        assertThat(emails(list(admin, "twoFactor", "OFF", "size", "100"))).contains(blocked).doesNotContain(coach);

        JsonNode byName = list(admin, "sort", "name", "size", "10");
        assertThat(byName.get("size").asInt()).isEqualTo(10);
        List<String> names = names(byName);
        assertThat(names).isSorted();
        // Only 10, 25, 50 or 100 per page; anything else falls back to 25.
        assertThat(list(admin, "size", "7").get("size").asInt()).isEqualTo(25);
        call(admin, "/api/admin/users?sort=password").andExpect(status().isBadRequest());
        call(admin, "/api/admin/users?role=ROOT").andExpect(status().isBadRequest());
    }

    @Test
    void theDetailShowsCountsAndRecentEventsButNeverPlanText() throws Exception {
        String admin = admin();
        String email = "detail-" + UUID.randomUUID() + "@example.com";
        String token = register(email, "Detail Person");
        String secret = "SECRET-AREA-" + UUID.randomUUID();
        send(post("/api/vision-areas"), token, Map.of("name", secret, "priority", "HIGH", "status", "ACTIVE"))
                .andExpect(status().isCreated());
        login(email);
        long id = jdbc.queryForObject("select id from app_users where email = ?", Long.class, email);

        String body = call(admin, "/api/admin/users/" + id).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        JsonNode detail = objectMapper.readTree(body);

        assertThat(detail.get("account").get("email").asText()).isEqualTo(email);
        assertThat(detail.get("createdBy").asText()).isEqualTo("self sign-up");
        assertThat(detail.get("recordCounts").get("visionAreas").asLong()).isEqualTo(1);
        assertThat(detail.get("recordCounts").get("tasks").asLong()).isZero();
        assertThat(detail.get("recentEvents").toString()).contains("LOGIN_SUCCEEDED");
        // D-23-3: counts only, never the text.
        assertThat(body).doesNotContain(secret);
        call(admin, "/api/admin/users/99999999").andExpect(status().isNotFound());
    }

    @Test
    void onlyAccountsWithViewUsersMayLook() throws Exception {
        String participant = register("look-user-" + UUID.randomUUID() + "@example.com", "Plain User");
        call(participant, "/api/admin/users").andExpect(status().isForbidden());

        String coachEmail = "look-coach-" + UUID.randomUUID() + "@example.com";
        register(coachEmail, "Look Coach");
        jdbc.update("update app_users set role = 'COACH' where email = ?", coachEmail);
        String coach = login(coachEmail);
        call(coach, "/api/admin/users").andExpect(status().isForbidden());

        // D-23-1: it is the permission that counts, not the role name.
        jdbc.update("insert into role_permissions (role_key, permission) values ('COACH', 'VIEW_USERS')");
        permissions.evict();
        call(coach, "/api/admin/users").andExpect(status().isOk());
    }

    @Test
    void adminAnswersHoldNoSecretsOrPlanFields() {
        // UM-NFR-03: a guard on the answer types, so a field added later cannot leak.
        List<String> forbidden = List.of("password", "hash", "secret", "token", "totp", "recovery", "title", "description",
                "journal", "note", "message", "body");
        for (Class<?> type : List.of(AdminUserSummaryResponse.class, AdminUserPageResponse.class, AdminUserDetailResponse.class)) {
            for (RecordComponent component : type.getRecordComponents()) {
                String name = component.getName().toLowerCase(Locale.ROOT);
                assertThat(forbidden).as(type.getSimpleName() + "." + component.getName())
                        .noneMatch(name::contains);
            }
        }
        assertThat(Arrays.stream(AdminUserSummaryResponse.class.getRecordComponents()).map(RecordComponent::getName))
                .contains("email", "role", "status", "lastSignInAt");
    }

    // ---------- helpers ----------

    private String admin() throws Exception {
        String email = "users-admin-" + UUID.randomUUID() + "@example.com";
        register(email, "Users Admin");
        jdbc.update("update app_users set role = 'ADMIN' where email = ?", email);
        return login(email);
    }

    private JsonNode list(String token, String... params) throws Exception {
        var request = org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/admin/users").header("Authorization", "Bearer " + token);
        for (int i = 0; i < params.length; i += 2) {
            request.param(params[i], params[i + 1]);
        }
        return objectMapper.readTree(mockMvc.perform(request).andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
    }

    private static List<String> emails(JsonNode page) {
        return page.get("items").findValuesAsText("email");
    }

    private static List<String> names(JsonNode page) {
        return page.get("items").findValuesAsText("fullName");
    }

    private ResultActions call(String token, String path) throws Exception {
        return mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get(path)
                .header("Authorization", "Bearer " + token));
    }

    private ResultActions send(org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request, String token,
                               Object body) throws Exception {
        return mockMvc.perform(request.header("Authorization", "Bearer " + token).contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body)));
    }

    private String register(String email, String name) throws Exception {
        return objectMapper.readTree(mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("fullName", name, "email", email, "password", PASSWORD))))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString()).get("token").asText();
    }

    private String login(String email) throws Exception {
        return objectMapper.readTree(mockMvc.perform(post("/api/auth/login")
                        .header("X-Forwarded-For", "10.92." + (NEXT_IP.incrementAndGet() % 250) + ".1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("email", email, "password", PASSWORD))))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString()).get("token").asText();
    }
}
