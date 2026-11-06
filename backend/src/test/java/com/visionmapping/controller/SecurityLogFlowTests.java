package com.visionmapping.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.visionmapping.entity.AppUser;
import com.visionmapping.entity.enums.UserRole;
import com.visionmapping.repository.AppUserRepository;
import com.visionmapping.security.audit.SecurityAlertService;
import com.visionmapping.security.audit.SecurityEventService;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
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

/** V10 phase 5: the security log, alerts and the admin view (SEC-09-01 to SEC-09-05, SEC-08-06, SEC-01-07). */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SecurityLogFlowTests {

    private static final String PASSWORD = "Password123";
    private static final AtomicInteger NEXT_IP = new AtomicInteger();

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private AppUserRepository appUserRepository;

    @Autowired
    private SecurityAlertService alerts;

    @Autowired
    private SecurityEventService securityEvents;

    // ---------- SEC-09-01, SEC-09-02: what is recorded, and what never is ----------

    @Test
    void signInsAreRecordedWithTimeIpAndAccountButNoEmailOrPassword() throws Exception {
        String email = uniqueEmail();
        long userId = userId(email, register(email));
        String ip = uniqueIp();

        login(email, "WrongPassword9", ip).andExpect(status().isUnauthorized());
        login(email, PASSWORD, ip).andExpect(status().isOk());

        Map<String, Object> failed = latest("LOGIN_FAILED", userId);
        assertThat(failed.get("outcome")).isEqualTo("DENIED");
        assertThat(failed.get("ip_address")).isEqualTo(ip);
        assertThat(failed.get("subject_hash")).isEqualTo(SecurityEventService.hashEmail(email));
        assertThat(failed.get("detail")).isEqualTo("BadCredentialsException");
        assertThat(latest("LOGIN_SUCCEEDED", userId).get("outcome")).isEqualTo("SUCCESS");

        // Nothing in the whole log carries an email address, a password or a token.
        List<String> everything = jdbc.queryForList(
                "select concat(coalesce(detail,''), ' ', coalesce(target_type,''), ' ', coalesce(subject_hash,'')) from security_events",
                String.class);
        assertThat(everything).noneMatch(row -> row.contains("@") || row.contains(PASSWORD) || row.contains("WrongPassword9")
                || row.contains("eyJ"));
    }

    @Test
    void aFailedSignInIsKeptEvenThoughTheRequestRollsBack() throws Exception {
        String email = uniqueEmail();
        register(email);
        login(email, "WrongPassword9", uniqueIp()).andExpect(status().isUnauthorized());

        assertThat(countBySubject("LOGIN_FAILED", email)).isEqualTo(1);
    }

    @Test
    void anotherUsersRecordIsAnsweredWith404AndTheAttemptIsLogged() throws Exception {
        String ownerToken = register(uniqueEmail());
        long areaId = createArea(ownerToken);
        String intruderEmail = uniqueEmail();
        String intruder = register(intruderEmail);

        mockMvc.perform(get("/api/vision-areas/" + areaId).header("Authorization", "Bearer " + intruder))
                .andExpect(status().isNotFound());

        Map<String, Object> event = latest("NOT_OWNER", userId(intruderEmail, intruder));
        assertThat(event.get("target_type")).isEqualTo("Vision area");
        assertThat(((Number) event.get("target_id")).longValue()).isEqualTo(areaId);
    }

    @Test
    void aPlainUserOnAnAdminPageIsLoggedAsAccessDenied() throws Exception {
        String email = uniqueEmail();
        String token = register(email);

        mockMvc.perform(get("/api/admin/cohorts").header("Authorization", "Bearer " + token)).andExpect(status().isForbidden());

        assertThat(latest("ACCESS_DENIED", userId(email, token)).get("detail")).isEqualTo("GET /api/admin/cohorts");
    }

    @Test
    void openingASharedPlanIsLoggedAndSoIsABadLink() throws Exception {
        String token = register(uniqueEmail());
        long dreamId = createDream(token, createArea(token));
        JsonNode share = read(send(post("/api/plan-shares"), token, Map.of("dreamId", dreamId, "expiresInDays", 7))
                .andExpect(status().isCreated()));
        String link = share.get("token").asText();

        mockMvc.perform(get("/api/shared-plans/" + link)).andExpect(status().isOk());
        mockMvc.perform(get("/api/shared-plans/not-a-real-link")).andExpect(status().isNotFound());

        assertThat(jdbc.queryForObject("select count(*) from security_events where event_type = 'SHARE_LINK_OPENED' "
                + "and outcome = 'SUCCESS' and target_id = ?", Long.class, share.get("id").asLong())).isEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from security_events where event_type = 'SHARE_LINK_CREATED' "
                + "and target_id = ?", Long.class, share.get("id").asLong())).isEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from security_events where event_type = 'SHARE_LINK_OPENED' "
                + "and outcome = 'DENIED'", Long.class)).isPositive();
    }

    // ---------- SEC-08-06: admin changes ----------

    @Test
    void adminChangesRecordWhoChangedWhatAndWhen() throws Exception {
        String adminEmail = uniqueEmail();
        String admin = registerAdmin(adminEmail);
        long adminId = userId(adminEmail, admin);
        String coachEmail = uniqueEmail();
        long coachId = userId(coachEmail, register(coachEmail));

        JsonNode cohort = read(send(post("/api/admin/cohorts"), admin, Map.of("name", "Spring cohort",
                "startDate", LocalDate.now().toString(), "coachEmail", coachEmail)).andExpect(status().is2xxSuccessful()));

        Map<String, Object> changed = latest("COHORT_CHANGED", adminId);
        assertThat(changed.get("outcome")).isEqualTo("SUCCESS");
        assertThat(changed.get("detail")).isEqualTo("create");
        assertThat(((Number) changed.get("target_id")).longValue()).isEqualTo(cohort.get("id").asLong());
        assertThat(changed.get("occurred_at")).isNotNull();
        Map<String, Object> role = latest("ROLE_CHANGED", adminId);
        assertThat(role.get("detail")).isEqualTo("USER -> COACH");
        assertThat(((Number) role.get("target_id")).longValue()).isEqualTo(coachId);
    }

    @Test
    void changingTheClaudeKeyIsLoggedAndRaisesAnAlert() throws Exception {
        String adminEmail = uniqueEmail();
        String admin = registerAdmin(adminEmail);

        send(put("/api/admin/ai-settings/api-key"), admin, Map.of("apiKey", "sk-ant-api03-" + "x".repeat(40)))
                .andExpect(status().is2xxSuccessful());

        assertThat(latest("AI_KEY_CHANGED", userId(adminEmail, admin)).get("outcome")).isEqualTo("SUCCESS");
        assertThat(alerts.recentAlerts()).anyMatch(alert -> alert.contains("Claude API key was changed"));
        // The key itself is never in the log.
        assertThat(jdbc.queryForList("select coalesce(detail,'') from security_events", String.class))
                .noneMatch(detail -> detail.contains("sk-ant"));
    }

    // ---------- SEC-09-04: alerts ----------

    @Test
    void tenFailedSignInsOnOneAccountRaiseOneAlertWithoutTheEmail() throws Exception {
        String email = uniqueEmail();
        register(email);
        String hash = SecurityEventService.hashEmail(email);

        for (int i = 0; i < 10; i++) {
            login(email, "WrongPassword9", uniqueIp());
        }

        List<String> mine = alerts.recentAlerts().stream().filter(alert -> alert.contains(hash.substring(0, 12))).toList();
        assertThat(mine).hasSize(1);
        assertThat(mine.get(0)).contains("Many failed sign-ins for one account").doesNotContain(email);
        // After 5 failures the email is locked; the locked attempts are logged too.
        assertThat(countBySubject("LOGIN_LOCKED", email)).isEqualTo(5);
    }

    // ---------- SEC-09-05: the admin view ----------

    @Test
    void anAdminCanReadAndFilterTheLogAndAPlainUserCannot() throws Exception {
        String email = uniqueEmail();
        String user = register(email);
        long userId = userId(email, user);
        login(email, "WrongPassword9", uniqueIp());
        String admin = registerAdmin(uniqueEmail());

        mockMvc.perform(get("/api/admin/security-events").param("type", "LOGIN_FAILED").param("actorId", String.valueOf(userId))
                        .header("Authorization", "Bearer " + admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.events[0].eventType").value("LOGIN_FAILED"))
                .andExpect(jsonPath("$.events[0].actorId").value(userId))
                .andExpect(jsonPath("$.events[0].subjectHash").doesNotExist())
                .andExpect(jsonPath("$.totalEvents").value(1));

        mockMvc.perform(get("/api/admin/security-events").header("Authorization", "Bearer " + user))
                .andExpect(status().isForbidden());
    }

    @Test
    void theLogCannotBeChangedThroughTheApi() throws Exception {
        String admin = registerAdmin(uniqueEmail());
        Long anyId = jdbc.queryForObject("select max(id) from security_events", Long.class);

        mockMvc.perform(delete("/api/admin/security-events/" + anyId).header("Authorization", "Bearer " + admin))
                .andExpect(result -> assertThat(result.getResponse().getStatus()).isIn(404, 405));
        mockMvc.perform(delete("/api/admin/security-events").header("Authorization", "Bearer " + admin))
                .andExpect(result -> assertThat(result.getResponse().getStatus()).isIn(404, 405));
        assertThat(jdbc.queryForObject("select count(*) from security_events where id = ?", Long.class, anyId)).isEqualTo(1);
    }

    // ---------- SEC-09-03: retention ----------

    @Test
    void eventsOlderThanTheRetentionPeriodAreRemovedAndNewerOnesKept() {
        Timestamp old = Timestamp.from(Instant.now().minus(400, ChronoUnit.DAYS));
        Timestamp recent = Timestamp.from(Instant.now().minus(300, ChronoUnit.DAYS));
        jdbc.update("insert into security_events (occurred_at, event_type, outcome, detail) values (?, 'LOGGED_OUT', 'SUCCESS', 'retention-old')", old);
        jdbc.update("insert into security_events (occurred_at, event_type, outcome, detail) values (?, 'LOGGED_OUT', 'SUCCESS', 'retention-recent')", recent);

        securityEvents.deleteExpired();

        assertThat(jdbc.queryForObject("select count(*) from security_events where detail = 'retention-old'", Long.class)).isZero();
        assertThat(jdbc.queryForObject("select count(*) from security_events where detail = 'retention-recent'", Long.class)).isEqualTo(1);
    }

    // ---------- helpers ----------

    private Map<String, Object> latest(String type, long actorId) {
        return jdbc.queryForMap("select * from security_events where event_type = ? and actor_id = ? order by id desc limit 1",
                type, actorId);
    }

    private long countBySubject(String type, String email) {
        return jdbc.queryForObject("select count(*) from security_events where event_type = ? and subject_hash = ?",
                Long.class, type, SecurityEventService.hashEmail(email));
    }

    private String register(String email) throws Exception {
        return read(mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("fullName", "Log User", "email", email, "password", PASSWORD))))
                .andExpect(status().isCreated())).get("token").asText();
    }

    private String registerAdmin(String email) throws Exception {
        register(email);
        AppUser user = appUserRepository.findByEmail(email).orElseThrow();
        user.setRole(UserRole.ADMIN);
        appUserRepository.save(user);
        return read(login(email, PASSWORD, uniqueIp()).andExpect(status().isOk())).get("token").asText();
    }

    private long userId(String email, String ignoredToken) {
        return appUserRepository.findByEmail(email).orElseThrow().getId();
    }

    private ResultActions login(String email, String password, String ip) throws Exception {
        return mockMvc.perform(post("/api/auth/login").header("X-Forwarded-For", ip).contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("email", email, "password", password))));
    }

    private long createArea(String token) throws Exception {
        return read(send(post("/api/vision-areas"), token, Map.of("name", "Career", "priority", "HIGH", "status", "ACTIVE"))
                .andExpect(status().isCreated())).get("id").asLong();
    }

    private long createDream(String token, long areaId) throws Exception {
        return read(send(post("/api/dreams"), token, Map.of("visionAreaId", areaId, "title", "Own a home",
                "whyImportant", "It matters", "successDefinition", "Keys in hand", "dreamType", "LONG_TERM", "priority", "HIGH",
                "targetDate", LocalDate.now().plusMonths(6).toString(), "status", "ACTIVE", "scheduleMode", "BOTTOM_UP"))
                .andExpect(status().isCreated())).get("id").asLong();
    }

    private ResultActions send(org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request,
                               String token, Object body) throws Exception {
        return mockMvc.perform(request.header("Authorization", "Bearer " + token).contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body)));
    }

    private JsonNode read(ResultActions result) throws Exception {
        return objectMapper.readTree(result.andReturn().getResponse().getContentAsString());
    }

    private static String uniqueEmail() {
        return "log-" + UUID.randomUUID() + "@example.com";
    }

    private static String uniqueIp() {
        int n = NEXT_IP.incrementAndGet();
        return "10.55." + (n / 250) + "." + (n % 250 + 1);
    }
}
