package com.visionmapping.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.visionmapping.service.mail.StepUpMailer;
import com.visionmapping.service.mail.StepUpMailer.Delivery;
import com.visionmapping.service.stepup.StepUpAction;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mail.MailSendException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/** V20 phase 1 (BRD 20.0.0): sending and checking "Verify it's you" codes through the API (SV-FR-10 to SV-FR-19). */
@SpringBootTest(properties = "app.step-up.enabled=true")
@AutoConfigureMockMvc
@ActiveProfiles("test")
class StepUpFlowTests {

    private static final String PASSWORD = "Steady-Harbor-Lamp-42";
    private static final String PATH = "/api/account/step-up";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @MockitoBean
    private StepUpMailer mailer;

    @BeforeEach
    void mailWorks() {
        reset(mailer);
        when(mailer.delivery()).thenReturn(Delivery.EMAIL);
    }

    @Test
    void svc01_svc03_aCodeIsSentMaskedWorksOnceAndOpensTheWindow() throws Exception {
        String email = email("step-up");
        String token = register(email);
        JsonNode sent = read(send(post(PATH + "?language=km"), token, Map.of("action", "EXPORT_DATA")).andExpect(status().isCreated()));
        assertThat(sent.get("sentTo").asText()).startsWith("s").contains("***@e").endsWith(".com").doesNotContain(email);
        assertThat(sent.get("delivery").asText()).isEqualTo("EMAIL");
        assertThat(sent.get("expiresAt").isNull()).isFalse();
        String code = sentCode(email, StepUpAction.EXPORT_DATA, true);
        long id = sent.get("challengeId").asLong();

        JsonNode wrong = read(send(post(PATH + "/" + id + "/verify"), token, Map.of("code", other(code))).andExpect(status().isBadRequest()));
        assertThat(wrong.get("code").asText()).isEqualTo("STEP_UP_WRONG_CODE");
        assertThat(wrong.get("triesLeft").asInt()).isEqualTo(4);
        assertThat(wrong.get("message").asText()).isEqualTo("That code is not right. 4 tries left.");

        JsonNode ok = read(send(post(PATH + "/" + id + "/verify"), token, Map.of("code", code.substring(0, 3) + " " + code.substring(3)))
                .andExpect(status().isOk()));
        assertThat(ok.get("verified").asBoolean()).isTrue();
        assertThat(ok.get("windowUntil").isNull()).isFalse();
        JsonNode again = read(send(post(PATH + "/" + id + "/verify"), token, Map.of("code", code)).andExpect(status().isGone()));
        assertThat(again.get("code").asText()).isEqualTo("STEP_UP_CODE_ENDED");
        assertThat(jdbcTemplate.queryForObject("select count(*) from auth_sessions s join app_users u on u.id = s.user_id "
                + "where u.email = ? and s.verified_until is not null", Integer.class, email)).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject("select code_hash from step_up_challenges where id = ?", String.class, id))
                .as("only the HMAC is kept").hasSize(64).isNotEqualTo(code);

        // SV-FR-16: changing the password closes the window, in this session too.
        send(post("/api/auth/change-password"), token, Map.of("currentPassword", PASSWORD, "newPassword", "Quiet-River-Stone-77"))
                .andExpect(status().isNoContent());
        assertThat(jdbcTemplate.queryForObject("select count(*) from auth_sessions s join app_users u on u.id = s.user_id "
                + "where u.email = ? and s.verified_until is not null", Integer.class, email)).isZero();
    }

    @Test
    void svc03_fiveWrongTriesOrTenMinutesEndACode_andACodeIsTiedToItsPersonAndSession() throws Exception {
        String email = email("step-up-tries");
        String token = register(email);
        long id = request(token, "DELETE_ACCOUNT");
        for (int left = 4; left >= 0; left--) {
            JsonNode wrong = read(send(post(PATH + "/" + id + "/verify"), token, Map.of("code", "abc")).andExpect(status().isBadRequest()));
            assertThat(wrong.get("triesLeft").asInt()).isEqualTo(left);
        }
        assertThat(read(send(post(PATH + "/" + id + "/verify"), token, Map.of("code", "000000"))).get("message").asText())
                .isEqualTo("This code no longer works. Ask for a new code.");

        jdbcTemplate.update("update step_up_challenges set created_at = created_at - interval '2' minute where id = ?", id);
        long fresh = request(token, "DELETE_ACCOUNT");
        String code = sentCode(email, StepUpAction.DELETE_ACCOUNT, false);
        // Another session of the same person, and another person, cannot use it.
        String otherSession = login(email);
        send(post(PATH + "/" + fresh + "/verify"), otherSession, Map.of("code", code)).andExpect(status().isNotFound());
        send(post(PATH + "/" + fresh + "/verify"), register(email("step-up-stranger")), Map.of("code", code)).andExpect(status().isNotFound());
        jdbcTemplate.update("update step_up_challenges set expires_at = current_timestamp - interval '1' second where id = ?", fresh);
        send(post(PATH + "/" + fresh + "/verify"), token, Map.of("code", code)).andExpect(status().isGone());
    }

    @Test
    void svc04_resendWaitsAMinuteAndAPersonGetsAtMostFiveCodesAnHour() throws Exception {
        String token = register(email("step-up-limits"));
        long first = request(token, "EXPORT_DATA");
        JsonNode tooSoon = read(send(post(PATH), token, Map.of("action", "EXPORT_DATA")).andExpect(status().isTooManyRequests()));
        assertThat(tooSoon.get("message").asText()).isEqualTo("Wait a moment before asking for a new code.");
        send(post(PATH), token, Map.of("action", "CHANGE_PASSWORD")).andExpect(status().isCreated());

        jdbcTemplate.update("update step_up_challenges set created_at = created_at - interval '2' minute where id = ?", first);
        long second = request(token, "EXPORT_DATA");
        assertThat(jdbcTemplate.queryForObject("select used_at is not null from step_up_challenges where id = ?", Boolean.class, first))
                .as("a new code cancels the earlier one").isTrue();
        assertThat(second).isNotEqualTo(first);
        send(post(PATH), token, Map.of("action", "DELETE_ACCOUNT")).andExpect(status().isCreated());
        send(post(PATH), token, Map.of("action", "CHANGE_REMINDER_EMAIL")).andExpect(status().isCreated());
        JsonNode hour = read(send(post(PATH), token, Map.of("action", "ADMIN_AI_KEY")).andExpect(status().isTooManyRequests()));
        assertThat(hour.get("message").asText()).isEqualTo("You have asked for 5 codes in the last hour. Try again later.");

        jdbcTemplate.update("update step_up_challenges set created_at = created_at - interval '2' hour "
                + "where user_id = (select user_id from step_up_challenges where id = ?)", first);
        for (int index = 0; index < 15; index++) {
            jdbcTemplate.update("insert into step_up_challenges (user_id, session_id, action, code_hash, attempts, expires_at, used_at, created_at) "
                    + "select user_id, session_id, 'EXPORT_DATA', 'x', 0, current_timestamp, current_timestamp, current_timestamp - interval '3' hour "
                    + "from step_up_challenges where id = ?", first);
        }
        JsonNode day = read(send(post(PATH), token, Map.of("action", "ADMIN_AI_KEY")).andExpect(status().isTooManyRequests()));
        assertThat(day.get("message").asText()).isEqualTo("You have asked for 20 codes today. Try again tomorrow.");
    }

    @Test
    void svFr04_aReminderAddressCodeGoesToTheNewAddressAndOtherActionsTakeNoAddress() throws Exception {
        String token = register(email("step-up-target"));
        send(post(PATH), token, Map.of("action", "CONFIRM_REMINDER_EMAIL")).andExpect(status().isBadRequest());
        send(post(PATH), token, Map.of("action", "CONFIRM_REMINDER_EMAIL", "target", "not-an-email")).andExpect(status().isBadRequest());
        send(post(PATH), token, Map.of("action", "EXPORT_DATA", "target", "new@work.org")).andExpect(status().isBadRequest());
        send(post(PATH), token, Map.of("action", "NOT_AN_ACTION")).andExpect(status().isBadRequest());
        JsonNode sent = read(send(post(PATH), token, Map.of("action", "CONFIRM_REMINDER_EMAIL", "target", " New@Work.org "))
                .andExpect(status().isCreated()));
        assertThat(sent.get("sentTo").asText()).isEqualTo("N***@W***.org");
        verify(mailer).sendCode(eq("New@Work.org"), anyString(), eq(StepUpAction.CONFIRM_REMINDER_EMAIL), eq(false), eq(10L));
        long id = sent.get("challengeId").asLong();
        assertThat(read(send(post(PATH + "/" + id + "/verify"), token, Map.of("code", sentCode("New@Work.org", StepUpAction.CONFIRM_REMINDER_EMAIL, false)))
                .andExpect(status().isOk())).get("windowUntil").isNull()).as("a FRESH action opens no window").isTrue();
    }

    @Test
    void svFr32_svFr33_withoutMailNoCodeIsSentAndAFailedSendKeepsNothing() throws Exception {
        String token = register(email("step-up-mail"));
        when(mailer.delivery()).thenReturn(Delivery.NONE);
        JsonNode none = read(send(post(PATH), token, Map.of("action", "EXPORT_DATA")).andExpect(status().isCreated()));
        assertThat(none.get("delivery").asText()).isEqualTo("NONE");
        assertThat(none.get("challengeId").isNull()).isTrue();

        when(mailer.delivery()).thenReturn(Delivery.EMAIL);
        doThrow(new MailSendException("down")).when(mailer).sendCode(anyString(), anyString(), any(), anyBoolean(), anyLong());
        JsonNode failed = read(send(post(PATH), token, Map.of("action", "EXPORT_DATA")).andExpect(status().isServiceUnavailable()));
        assertThat(failed.get("code").asText()).isEqualTo("STEP_UP_MAIL_FAILED");
        assertThat(jdbcTemplate.queryForObject("select count(*) from step_up_challenges c join app_users u on u.id = c.user_id "
                + "where u.email like 'step-up-mail-%'", Integer.class)).isZero();
    }

    @Test
    void svFr19_tenWrongTriesInAnHourEndTheOtherSessionsAndSendANotice() throws Exception {
        String email = email("step-up-lockout");
        String token = register(email);
        String elsewhere = login(email);
        long first = request(token, "EXPORT_DATA");
        for (int index = 0; index < 5; index++) {
            send(post(PATH + "/" + first + "/verify"), token, Map.of("code", "999999x")).andExpect(status().isBadRequest());
        }
        jdbcTemplate.update("update step_up_challenges set created_at = created_at - interval '2' minute where id = ?", first);
        long second = request(token, "EXPORT_DATA");
        for (int index = 0; index < 5; index++) {
            send(post(PATH + "/" + second + "/verify"), token, Map.of("code", "abc")).andExpect(status().isBadRequest());
        }
        verify(mailer).sendSecurityNotice(email, false);
        send(post(PATH), elsewhere, Map.of("action", "EXPORT_DATA")).andExpect(status().isUnauthorized());
        assertThat(jdbcTemplate.queryForObject("select count(*) from auth_sessions s join app_users u on u.id = s.user_id "
                + "where u.email = ? and s.revoked_reason = 'STEP_UP_LOCKED'", Integer.class, email)).isEqualTo(1);
    }

    @Test
    void svc02_svc05_downloadAndPasswordChangeNeedACodeOrTheWindow() throws Exception {
        String email = email("step-up-protect");
        String token = register(email);
        JsonNode refused = read(mockMvc.perform(get("/api/account/export").header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden()));
        assertThat(refused.get("code").asText()).isEqualTo("STEP_UP_REQUIRED");
        // No password oracle before the code.
        send(post("/api/auth/change-password"), token, Map.of("currentPassword", "wrong", "newPassword", "Quiet-River-Stone-77"))
                .andExpect(status().isForbidden());

        long id = request(token, "EXPORT_DATA");
        send(post(PATH + "/" + id + "/verify"), token, Map.of("code", sentCode(email, StepUpAction.EXPORT_DATA, false)))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/account/export").header("Authorization", "Bearer " + token).header("X-Step-Up", String.valueOf(id)))
                .andExpect(status().isOk());
        // Inside the 10-minute window the other settings actions need no new code.
        mockMvc.perform(get("/api/account/export").header("Authorization", "Bearer " + token)).andExpect(status().isOk());
        send(post("/api/auth/change-password"), token, Map.of("currentPassword", PASSWORD, "newPassword", "Quiet-River-Stone-77"))
                .andExpect(status().isNoContent());
        // The password change closed the window.
        mockMvc.perform(get("/api/account/export").header("Authorization", "Bearer " + token)).andExpect(status().isForbidden());
        mockMvc.perform(get("/api/account/export").header("Authorization", "Bearer " + token).header("X-Step-Up", "not-a-number"))
                .andExpect(status().isForbidden());
    }

    @Test
    void svc06_deleteAlwaysNeedsItsOwnFreshCodeAndAWrongPasswordDoesNotUseItUp() throws Exception {
        String email = email("step-up-delete");
        String token = register(email);
        long export = request(token, "EXPORT_DATA");
        send(post(PATH + "/" + export + "/verify"), token, Map.of("code", sentCode(email, StepUpAction.EXPORT_DATA, false)))
                .andExpect(status().isOk());
        // The window never covers delete.
        send(post("/api/account/delete"), token, Map.of("password", PASSWORD)).andExpect(status().isForbidden());
        mockMvc.perform(post("/api/account/delete").header("Authorization", "Bearer " + token).header("X-Step-Up", String.valueOf(export))
                .contentType(MediaType.APPLICATION_JSON).content("{\"password\":\"" + PASSWORD + "\"}"))
                .andExpect(status().isForbidden());

        long id = request(token, "DELETE_ACCOUNT");
        send(post(PATH + "/" + id + "/verify"), token, Map.of("code", sentCode(email, StepUpAction.DELETE_ACCOUNT, false)))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/account/delete").header("Authorization", "Bearer " + token).header("X-Step-Up", String.valueOf(id))
                .contentType(MediaType.APPLICATION_JSON).content("{\"password\":\"wrong\"}")).andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/account/delete").header("Authorization", "Bearer " + token).header("X-Step-Up", String.valueOf(id))
                .contentType(MediaType.APPLICATION_JSON).content("{\"password\":\"" + PASSWORD + "\"}")).andExpect(status().isNoContent());
        assertThat(jdbcTemplate.queryForObject("select count(*) from app_users where email = ?", Integer.class, email)).isZero();
    }

    @Test
    void svc07_aNewReminderAddressNeedsTheAccountCheckThenACodeSentToThatAddress() throws Exception {
        String email = email("step-up-reminder");
        String token = register(email);
        Map<String, Object> body = Map.of("enabled", true, "timeZone", "UTC", "reminderEmail", "inbox@work.org");
        JsonNode first = read(send(put("/api/account/reminders"), token, body).andExpect(status().isForbidden()));
        assertThat(first.get("action").asText()).isEqualTo("CHANGE_REMINDER_EMAIL");

        long account = request(token, "CHANGE_REMINDER_EMAIL");
        send(post(PATH + "/" + account + "/verify"), token, Map.of("code", sentCode(email, StepUpAction.CHANGE_REMINDER_EMAIL, false)))
                .andExpect(status().isOk());
        JsonNode second = read(mockMvc.perform(put("/api/account/reminders").header("Authorization", "Bearer " + token)
                .header("X-Step-Up", String.valueOf(account)).contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body))).andExpect(status().isForbidden()));
        assertThat(second.get("action").asText()).isEqualTo("CONFIRM_REMINDER_EMAIL");

        // A code for another address does not confirm this one.
        long wrongAddress = read(send(post(PATH), token, Map.of("action", "CONFIRM_REMINDER_EMAIL", "target", "other@work.org"))
                .andExpect(status().isCreated())).get("challengeId").asLong();
        send(post(PATH + "/" + wrongAddress + "/verify"), token,
                Map.of("code", sentCode("other@work.org", StepUpAction.CONFIRM_REMINDER_EMAIL, false))).andExpect(status().isOk());
        mockMvc.perform(put("/api/account/reminders").header("Authorization", "Bearer " + token).header("X-Step-Up-Confirm",
                String.valueOf(wrongAddress)).contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isForbidden());

        jdbcTemplate.update("update step_up_challenges set created_at = created_at - interval '2' minute where id = ?", wrongAddress);
        long confirm = read(send(post(PATH), token, Map.of("action", "CONFIRM_REMINDER_EMAIL", "target", "inbox@work.org"))
                .andExpect(status().isCreated())).get("challengeId").asLong();
        send(post(PATH + "/" + confirm + "/verify"), token,
                Map.of("code", sentCode("inbox@work.org", StepUpAction.CONFIRM_REMINDER_EMAIL, false))).andExpect(status().isOk());
        JsonNode saved = read(mockMvc.perform(put("/api/account/reminders").header("Authorization", "Bearer " + token)
                .header("X-Step-Up-Confirm", String.valueOf(confirm)).contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body))).andExpect(status().isOk()));
        assertThat(saved.get("reminderEmail").asText()).isEqualTo("inbox@work.org");

        // The same address, other settings: no code. Clearing it: only the account check (the window is still open).
        send(put("/api/account/reminders"), token, Map.of("enabled", false, "timeZone", "UTC", "reminderEmail", "INBOX@work.org"))
                .andExpect(status().isOk());
        assertThat(read(send(put("/api/account/reminders"), token, Map.of("enabled", false, "timeZone", "UTC", "reminderEmail", ""))
                .andExpect(status().isOk())).get("sendsTo").asText()).isEqualTo(email);
    }

    @Test
    void svc12_adminChangesToTheAiKeyOrACoachNeedAFreshCodeEveryTime() throws Exception {
        String adminEmail = email("step-up-admin");
        String admin = register(adminEmail);
        jdbcTemplate.update("update app_users set role = 'ADMIN' where email = ?", adminEmail);
        JsonNode status = read(mockMvc.perform(get("/api/admin/step-up").header("Authorization", "Bearer " + admin)).andExpect(status().isOk()));
        assertThat(status.get("enabled").asBoolean()).isTrue();
        assertThat(status.get("delivery").asText()).isEqualTo("EMAIL");
        assertThat(status.get("active").asBoolean()).isTrue();
        mockMvc.perform(get("/api/admin/step-up").header("Authorization", "Bearer " + register(email("step-up-plain"))))
                .andExpect(status().isForbidden());

        Map<String, Object> key = Map.of("apiKey", "sk-ant-api03-test-key-0000000000wxyz");
        assertThat(read(send(put("/api/admin/ai-settings/api-key"), admin, key).andExpect(status().isForbidden())).get("action").asText())
                .isEqualTo("ADMIN_AI_KEY");
        send(put("/api/admin/ai-settings/api-key"), admin, Map.of("apiKey", "not-a-key")).andExpect(status().isBadRequest());
        long keyCode = verifiedFor(admin, adminEmail, "ADMIN_AI_KEY");
        withCode(put("/api/admin/ai-settings/api-key"), admin, keyCode, key).andExpect(status().isOk());
        // A fresh code every time: the same one is used up.
        withCode(put("/api/admin/ai-settings/api-key"), admin, keyCode, key).andExpect(status().isForbidden());
        mockMvc.perform(delete("/api/admin/ai-settings/api-key").header("Authorization", "Bearer " + admin)).andExpect(status().isForbidden());

        String coachEmail = email("step-up-coach");
        register(coachEmail);
        Map<String, Object> cohort = Map.of("name", "Pilot", "coachEmail", coachEmail);
        assertThat(read(send(post("/api/admin/cohorts"), admin, cohort).andExpect(status().isForbidden())).get("action").asText())
                .isEqualTo("ADMIN_ROLE_CHANGE");
        long roleCode = verifiedFor(admin, adminEmail, "ADMIN_ROLE_CHANGE");
        long cohortId = read(withCode(post("/api/admin/cohorts"), admin, roleCode, cohort).andExpect(status().isCreated())).get("id").asLong();
        send(put("/api/admin/cohorts/" + cohortId), admin, Map.of("name", "Pilot 2", "coachEmail", coachEmail)).andExpect(status().isOk());

        String secondCoach = email("step-up-coach-two");
        register(secondCoach);
        jdbcTemplate.update("update app_users set role = 'COACH' where email = ?", secondCoach);
        assertThat(read(send(put("/api/admin/cohorts/" + cohortId), admin, Map.of("name", "Pilot 2", "coachEmail", secondCoach))
                .andExpect(status().isForbidden())).get("action").asText()).isEqualTo("ADMIN_COHORT_COACH");
        long coachCode = verifiedFor(admin, adminEmail, "ADMIN_COHORT_COACH");
        withCode(put("/api/admin/cohorts/" + cohortId), admin, coachCode, Map.of("name", "Pilot 2", "coachEmail", secondCoach))
                .andExpect(status().isOk());
    }

    private long verifiedFor(String token, String email, String action) throws Exception {
        long id = request(token, action);
        send(post(PATH + "/" + id + "/verify"), token, Map.of("code", sentCode(email, StepUpAction.valueOf(action), false)))
                .andExpect(status().isOk());
        return id;
    }

    private ResultActions withCode(MockHttpServletRequestBuilder request, String token, long code, Object body) throws Exception {
        return mockMvc.perform(request.header("Authorization", "Bearer " + token).header("X-Step-Up", String.valueOf(code))
                .contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(body)));
    }

    private long request(String token, String action) throws Exception {
        return read(send(post(PATH), token, Map.of("action", action)).andExpect(status().isCreated())).get("challengeId").asLong();
    }

    /** The code the mocked mailer was last asked to send for this address and action. */
    private String sentCode(String to, StepUpAction action, boolean khmer) {
        ArgumentCaptor<String> code = ArgumentCaptor.forClass(String.class);
        verify(mailer, org.mockito.Mockito.atLeastOnce()).sendCode(eq(to), code.capture(), eq(action), eq(khmer), eq(10L));
        return code.getValue();
    }

    private static String other(String code) {
        return code.equals("000000") ? "111111" : "000000";
    }

    private static String email(String prefix) {
        return prefix + "-" + System.nanoTime() + "@example.com";
    }

    private ResultActions send(MockHttpServletRequestBuilder request, String token, Object body) throws Exception {
        return mockMvc.perform(request.header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(body)));
    }

    private JsonNode read(ResultActions result) throws Exception {
        return objectMapper.readTree(result.andReturn().getResponse().getContentAsString());
    }

    private String register(String email) throws Exception {
        Map<String, Object> body = new HashMap<>(Map.of("fullName", "Step Up", "email", email, "password", PASSWORD));
        return objectMapper.readTree(mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body))).andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString()).get("token").asText();
    }

    private String login(String email) throws Exception {
        return objectMapper.readTree(mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("email", email, "password", PASSWORD)))).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString()).get("token").asText();
    }
}
