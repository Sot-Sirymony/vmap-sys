package com.visionmapping.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.visionmapping.security.PermissionService;
import com.visionmapping.service.mail.InvitationMailer;
import com.visionmapping.service.mail.PasswordResetMailer;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * V23 (BRD 23.0.0) phase 5: Add user by invitation, accepting it, edit, resend,
 * password reset email and permanent delete (UM-FR-22 to UM-FR-28, UM-FR-35 to UM-FR-38).
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AdminUserLifecycleFlowTests {

    private static final String PASSWORD = "Steady-Harbor-Lamp-42";
    private static final String NEW_PASSWORD = "Quiet-River-Stone-77";
    private static final String REASON = "Left the program in May";
    private static final AtomicInteger NEXT_IP = new AtomicInteger();

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private PermissionService permissions;

    @MockitoBean
    private InvitationMailer invitationMailer;

    @MockitoBean
    private PasswordResetMailer passwordResetMailer;

    @BeforeEach
    void mailWorks() {
        when(invitationMailer.canDeliver()).thenReturn(true);
    }

    @AfterEach
    void restoreCoach() {
        jdbc.update("delete from role_permissions where role_key = 'COACH' and permission in ('VIEW_USERS', 'MANAGE_USERS')");
        permissions.evict();
    }

    // ---------- Add user and the invitation ----------

    @Test
    void anAdminAddsAParticipantWhoChoosesTheirOwnPasswordFromTheInvitation() throws Exception {
        Person admin = person("ADMIN");
        String email = unique("invited");

        JsonNode detail = read(send(post("/api/admin/users"), admin.token(), Map.of("fullName", "Sokha Invited", "email", email))
                .andExpect(status().isCreated()));

        long id = detail.get("account").get("id").asLong();
        assertThat(detail.get("account").get("role").asText()).isEqualTo("USER");
        assertThat(detail.get("createdBy").asText()).isEqualTo("Person ADMIN");
        assertThat(detail.get("invitationPending").asBoolean()).isTrue();
        assertThat(detail.get("invitationExpiresAt").isNull()).isFalse();
        assertThat(jdbc.queryForObject("select password_set from app_users where id = ?", Boolean.class, id)).isFalse();
        assertThat(lastEventDetail("USER_CREATED_BY_ADMIN", id)).isEqualTo("role USER");
        assertThat(lastEventDetail("INVITATION_SENT", id)).isEqualTo("first invitation");

        // Nobody knows a password for the account until the invitation is used.
        login(email, PASSWORD).andExpect(status().isUnauthorized());

        String token = invitationToken(email);
        mockMvc.perform(post("/api/auth/invitation/check").contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("token", token))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fullName").value("Sokha Invited"))
                .andExpect(jsonPath("$.email").value(email));

        accept(token, NEW_PASSWORD).andExpect(status().isOk()).andExpect(jsonPath("$.token").isNotEmpty());
        assertThat(jdbc.queryForObject("select password_set from app_users where id = ?", Boolean.class, id)).isTrue();
        login(email, NEW_PASSWORD).andExpect(status().isOk());

        // Single use.
        accept(token, "Another-Long-Phrase-91").andExpect(status().isGone());
        assertThat(read(call(admin, id)).get("invitationPending").asBoolean()).isFalse();
    }

    @Test
    void anEmailThatAlreadyHasAnAccountIsRefused() throws Exception {
        Person admin = person("ADMIN");
        Person existing = person("USER");

        send(post("/api/admin/users"), admin.token(), Map.of("fullName", "Again", "email", existing.email().toUpperCase()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("This email already has an account."));
    }

    @Test
    void withoutMailNothingIsCreated() throws Exception {
        when(invitationMailer.canDeliver()).thenReturn(false);
        Person admin = person("ADMIN");
        String email = unique("nomail");

        send(post("/api/admin/users"), admin.token(), Map.of("fullName", "No Mail", "email", email))
                .andExpect(status().isConflict());

        assertThat(jdbc.queryForObject("select count(*) from app_users where email = ?", Integer.class, email)).isZero();
    }

    @Test
    void addingACoachStartsTheTwoStepRequirement() throws Exception {
        Person admin = person("ADMIN");

        JsonNode detail = read(send(post("/api/admin/users"), admin.token(),
                Map.of("fullName", "New Coach", "email", unique("coach"), "role", "COACH")).andExpect(status().isCreated()));

        assertThat(detail.get("account").get("role").asText()).isEqualTo("COACH");
        assertThat(detail.get("account").get("twoFactor").asText()).isEqualTo("DUE");
    }

    @Test
    void aUserManagerCannotAddSomeoneWithMorePermissionsThanThey() throws Exception {
        jdbc.update("insert into role_permissions (role_key, permission) values ('COACH', 'VIEW_USERS'), ('COACH', 'MANAGE_USERS')");
        permissions.evict();
        Person manager = person("COACH");

        send(post("/api/admin/users"), manager.token(), Map.of("fullName", "Raised", "email", unique("raised"), "role", "ADMIN"))
                .andExpect(status().isForbidden());
        send(post("/api/admin/users"), manager.token(), Map.of("fullName", "Plain", "email", unique("plain")))
                .andExpect(status().isCreated());
    }

    @Test
    void aParticipantCannotAddUsers() throws Exception {
        Person participant = person("USER");

        send(post("/api/admin/users"), participant.token(), Map.of("fullName", "Nope", "email", unique("nope")))
                .andExpect(status().isForbidden());
    }

    @Test
    void resendingReplacesTheEarlierLink() throws Exception {
        Person admin = person("ADMIN");
        String email = unique("resend");
        long id = read(send(post("/api/admin/users"), admin.token(), Map.of("fullName", "Resend Me", "email", email))
                .andExpect(status().isCreated())).get("account").get("id").asLong();
        String first = invitationToken(email);
        clearInvocations(invitationMailer);

        send(post("/api/admin/users/" + id + "/invitation"), admin.token(), Map.of()).andExpect(status().isNoContent());
        String second = invitationToken(email);

        accept(first, NEW_PASSWORD).andExpect(status().isGone());
        accept(second, NEW_PASSWORD).andExpect(status().isOk());
        // Accepted: there is nothing left to resend.
        send(post("/api/admin/users/" + id + "/invitation"), admin.token(), Map.of()).andExpect(status().isConflict());
    }

    @Test
    void anExpiredOrUnknownLinkIsGone() throws Exception {
        Person admin = person("ADMIN");
        String email = unique("expired");
        long id = read(send(post("/api/admin/users"), admin.token(), Map.of("fullName", "Late", "email", email))
                .andExpect(status().isCreated())).get("account").get("id").asLong();
        String token = invitationToken(email);
        jdbc.update("update user_invitations set expires_at = expires_at - interval '8' day where user_id = ?", id);

        accept(token, NEW_PASSWORD).andExpect(status().isGone());
        accept("not-a-real-token", NEW_PASSWORD).andExpect(status().isGone());
    }

    @Test
    void aBlockedInvitedPersonCannotAccept() throws Exception {
        Person admin = person("ADMIN");
        String email = unique("blockedinvite");
        long id = read(send(post("/api/admin/users"), admin.token(), Map.of("fullName", "Blocked Invite", "email", email))
                .andExpect(status().isCreated())).get("account").get("id").asLong();
        String token = invitationToken(email);
        send(post("/api/admin/users/" + id + "/block"), admin.token(), Map.of("reason", REASON)).andExpect(status().isOk());

        accept(token, NEW_PASSWORD).andExpect(status().isForbidden());
    }

    // ---------- edit and password reset ----------

    @Test
    void editChangesNameAndCohortAndLogsOnlyTheFieldNames() throws Exception {
        Person admin = person("ADMIN");
        Person dara = person("USER");
        long cohortId = read(send(post("/api/admin/cohorts"), admin.token(),
                Map.of("name", "Edit " + UUID.randomUUID().toString().substring(0, 6))).andExpect(status().isCreated())).get("id").asLong();

        JsonNode detail = read(send(patch("/api/admin/users/" + dara.id()), admin.token(),
                Map.of("fullName", "Dara Renamed", "cohortId", cohortId)).andExpect(status().isOk()));

        assertThat(detail.get("account").get("fullName").asText()).isEqualTo("Dara Renamed");
        assertThat(detail.get("account").get("cohortId").asLong()).isEqualTo(cohortId);
        assertThat(lastEventDetail("USER_UPDATED", dara.id())).isEqualTo("fullName, cohort");

        Map<String, Object> noCohort = new HashMap<>();
        noCohort.put("fullName", "Dara Renamed");
        noCohort.put("cohortId", null);
        send(patch("/api/admin/users/" + dara.id()), admin.token(), noCohort).andExpect(status().isOk())
                .andExpect(jsonPath("$.account.cohortId").doesNotExist());
    }

    @Test
    void anAdminSendsAResetEmailWithoutSeeingTheLink() throws Exception {
        Person admin = person("ADMIN");
        Person dara = person("USER");

        send(post("/api/admin/users/" + dara.id() + "/password-reset"), admin.token(), Map.of())
                .andExpect(status().isNoContent())
                .andExpect(result -> assertThat(result.getResponse().getContentAsString()).isEmpty());

        verify(passwordResetMailer).sendResetLink(eq(dara.email()), anyString(), anyString(), anyLong());
        assertThat(lastEventDetail("PASSWORD_RESET_REQUESTED", dara.id())).isEqualTo("sent by a program admin");
    }

    // ---------- permanent delete ----------

    @Test
    void anAccountMustBeBlockedAndItsEmailTypedBeforeItIsDeleted() throws Exception {
        Person admin = person("ADMIN");
        Person dara = person("USER");

        send(delete("/api/admin/users/" + dara.id()), admin.token(), Map.of("confirmEmail", dara.email(), "reason", REASON))
                .andExpect(status().isConflict());
        send(post("/api/admin/users/" + dara.id() + "/block"), admin.token(), Map.of("reason", REASON)).andExpect(status().isOk());
        send(delete("/api/admin/users/" + dara.id()), admin.token(), Map.of("confirmEmail", "wrong@example.com", "reason", REASON))
                .andExpect(status().isBadRequest());
        assertThat(jdbc.queryForObject("select count(*) from app_users where id = ?", Integer.class, dara.id())).isOne();
    }

    @Test
    void deletingABlockedAccountErasesItAndKeepsAnAnonymousLogEntry() throws Exception {
        Person admin = person("ADMIN");
        Person dara = person("USER");
        send(post("/api/vision-areas"), dara.token(), Map.of("name", "Health", "priority", "HIGH", "status", "ACTIVE")).andExpect(status().isCreated());
        send(post("/api/admin/users/" + dara.id() + "/block"), admin.token(), Map.of("reason", REASON)).andExpect(status().isOk());

        send(delete("/api/admin/users/" + dara.id()), admin.token(), Map.of("confirmEmail", dara.email(), "reason", REASON))
                .andExpect(status().isNoContent());

        assertThat(jdbc.queryForObject("select count(*) from app_users where id = ?", Integer.class, dara.id())).isZero();
        assertThat(jdbc.queryForObject("select count(*) from vision_areas where user_id = ?", Integer.class, dara.id())).isZero();
        String detail = lastEventDetail("USER_DELETED_BY_ADMIN", dara.id());
        assertThat(detail).isEqualTo("USER; reason: " + REASON).doesNotContain(dara.email());
    }

    @Test
    void aUserManagerWithoutDeleteUsersCannotDelete() throws Exception {
        jdbc.update("insert into role_permissions (role_key, permission) values ('COACH', 'VIEW_USERS'), ('COACH', 'MANAGE_USERS')");
        permissions.evict();
        Person manager = person("COACH");
        Person dara = person("USER");
        send(post("/api/admin/users/" + dara.id() + "/block"), manager.token(), Map.of("reason", REASON)).andExpect(status().isOk());

        send(delete("/api/admin/users/" + dara.id()), manager.token(), Map.of("confirmEmail", dara.email(), "reason", REASON))
                .andExpect(status().isForbidden());
        verify(passwordResetMailer, never()).sendResetLink(any(), any(), any(), anyLong());
    }

    // ---------- helpers ----------

    private record Person(long id, String email, String token) {
    }

    private static String unique(String prefix) {
        return prefix + "-" + UUID.randomUUID() + "@example.com";
    }

    private Person person(String role) throws Exception {
        String email = unique("lifecycle-" + role.toLowerCase());
        mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("fullName", "Person " + role, "email", email, "password", PASSWORD))))
                .andExpect(status().isCreated());
        jdbc.update("update app_users set role = ? where email = ?", role, email);
        long id = jdbc.queryForObject("select id from app_users where email = ?", Long.class, email);
        return new Person(id, email, read(login(email, PASSWORD).andExpect(status().isOk())).get("token").asText());
    }

    /** The token from the latest invitation emailed to this address. */
    private String invitationToken(String email) {
        ArgumentCaptor<String> link = ArgumentCaptor.forClass(String.class);
        verify(invitationMailer, org.mockito.Mockito.atLeastOnce())
                .sendInvitation(eq(email), anyString(), anyString(), link.capture(), anyLong());
        String latest = link.getAllValues().get(link.getAllValues().size() - 1);
        return latest.substring(latest.indexOf("token=") + "token=".length());
    }

    private ResultActions accept(String token, String password) throws Exception {
        return mockMvc.perform(post("/api/auth/invitation/accept").header("X-Forwarded-For", nextIp())
                .contentType(MediaType.APPLICATION_JSON).content(json(Map.of("token", token, "password", password))));
    }

    private ResultActions login(String email, String password) throws Exception {
        return mockMvc.perform(post("/api/auth/login").header("X-Forwarded-For", nextIp())
                .contentType(MediaType.APPLICATION_JSON).content(json(Map.of("email", email, "password", password))));
    }

    private static String nextIp() {
        return "10.94." + (NEXT_IP.incrementAndGet() % 250) + ".1";
    }

    private ResultActions call(Person viewer, long id) throws Exception {
        return mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/admin/users/" + id)
                .header("Authorization", "Bearer " + viewer.token())).andExpect(status().isOk());
    }

    private ResultActions send(MockHttpServletRequestBuilder request, String token, Object body) throws Exception {
        return mockMvc.perform(request.header("Authorization", "Bearer " + token).contentType(MediaType.APPLICATION_JSON)
                .content(json(body)));
    }

    private String json(Object body) throws Exception {
        return objectMapper.writeValueAsString(body);
    }

    private String lastEventDetail(String type, long targetId) {
        return jdbc.queryForObject("select detail from security_events where event_type = ? and target_id = ? order by id desc limit 1",
                String.class, type, targetId);
    }

    private JsonNode read(ResultActions result) throws Exception {
        return objectMapper.readTree(result.andReturn().getResponse().getContentAsString());
    }
}
