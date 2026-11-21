package com.visionmapping.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.after;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/**
 * V20 phase 3 (SV-FR-28 to SV-FR-31): a notice goes to the account email after
 * a protected change is saved, and never for one that failed. Mail is "set up"
 * with a mocked sender, so the step-up codes are read from the sent emails too.
 */
@SpringBootTest(properties = {"app.step-up.enabled=true", "spring.mail.host=smtp.test"})
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AccountNoticeFlowTests {

    private static final String PASSWORD = "Steady-Harbor-Lamp-42";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private JavaMailSender sender;

    @BeforeEach
    void cleanMailbox() {
        reset(sender);
    }

    @Test
    void svc08_aPasswordChangeSendsANoticeAndAWrongPasswordSendsNothing() throws Exception {
        String email = "notice-" + System.nanoTime() + "@example.com";
        String token = register(email);
        long id = verified(token, email, "CHANGE_PASSWORD");

        perform(post("/api/auth/change-password"), token, id, Map.of("currentPassword", "wrong", "newPassword", "Quiet-River-Stone-77"))
                .andExpect(status().isBadRequest());
        verify(sender, after(400).never()).send(argThat((SimpleMailMessage message) -> message.getSubject().contains("password was changed")));

        perform(post("/api/auth/change-password"), token, id, Map.of("currentPassword", PASSWORD, "newPassword", "Quiet-River-Stone-77"))
                .andExpect(status().isNoContent());
        // The notice is sent off the request thread: wait for it, not just for any email.
        verify(sender, timeout(5000)).send(argThat((SimpleMailMessage message) -> "Your Vision Map password was changed".equals(message.getSubject())));
        ArgumentCaptor<SimpleMailMessage> notice = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(sender, atLeastOnce()).send(notice.capture());
        SimpleMailMessage sent = notice.getAllValues().stream()
                .filter(message -> "Your Vision Map password was changed".equals(message.getSubject())).findFirst().orElseThrow();
        assertThat(sent.getTo()).containsExactly(email);
        assertThat(sent.getText()).contains("The password for your Vision Map account was changed", "change your password now");
    }

    @Test
    void svc07_aReminderAddressChangeTellsTheAccountAndTheOldAddress() throws Exception {
        String email = "notice-reminder-" + System.nanoTime() + "@example.com";
        String token = register(email);
        saveAddress(token, email, "old@work.org");
        reset(sender);
        saveAddress(token, email, "new@work.org");
        verify(sender, timeout(5000).times(2)).send(argThat((SimpleMailMessage message) ->
                "Your Vision Map reminder email was changed".equals(message.getSubject())));
        ArgumentCaptor<SimpleMailMessage> sent = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(sender, atLeastOnce()).send(sent.capture());
        List<SimpleMailMessage> notices = sent.getAllValues().stream()
                .filter(message -> "Your Vision Map reminder email was changed".equals(message.getSubject())).toList();
        assertThat(notices).extracting(message -> message.getTo()[0]).containsExactlyInAnyOrder(email, "old@work.org");
        assertThat(notices.get(0).getText()).contains("now go to n***@w***.org");
    }

    @Test
    void svFr30_deletingTheAccountSendsALastNoticeOnlyOnceItIsDone() throws Exception {
        String email = "notice-delete-" + System.nanoTime() + "@example.com";
        String token = register(email);
        long id = verified(token, email, "DELETE_ACCOUNT");
        perform(post("/api/account/delete"), token, id, Map.of("password", "wrong")).andExpect(status().isBadRequest());
        verify(sender, after(400).never()).send(argThat((SimpleMailMessage message) -> message.getSubject().contains("was deleted")));
        perform(post("/api/account/delete"), token, id, Map.of("password", PASSWORD)).andExpect(status().isNoContent());
        verify(sender, timeout(5000)).send(argThat((SimpleMailMessage message) -> "Your Vision Map account was deleted".equals(message.getSubject())
                && email.equals(message.getTo()[0])));
    }

    /** Saves a new reminder address through both checks, reading each code from its email. */
    private void saveAddress(String token, String email, String address) throws Exception {
        String body = objectMapper.writeValueAsString(Map.of("enabled", true, "timeZone", "Asia/Phnom_Penh", "reminderEmail", address));
        JsonNode window = read(mockMvc.perform(post("/api/account/step-up").header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON).content("{\"action\":\"CHANGE_REMINDER_EMAIL\"}")));
        if (window.hasNonNull("challengeId")) {
            verifyCode(token, window.get("challengeId").asLong(), codeSentTo(email));
        }
        long confirm = read(mockMvc.perform(post("/api/account/step-up").header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(Map.of("action", "CONFIRM_REMINDER_EMAIL", "target", address)))))
                .get("challengeId").asLong();
        verifyCode(token, confirm, codeSentTo(address));
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put("/api/account/reminders")
                .header("Authorization", "Bearer " + token).header("X-Step-Up-Confirm", String.valueOf(confirm))
                .contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isOk());
    }

    private long verified(String token, String email, String action) throws Exception {
        long id = read(mockMvc.perform(post("/api/account/step-up").header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON).content("{\"action\":\"" + action + "\"}")).andExpect(status().isCreated()))
                .get("challengeId").asLong();
        verifyCode(token, id, codeSentTo(email));
        return id;
    }

    private void verifyCode(String token, long id, String code) throws Exception {
        mockMvc.perform(post("/api/account/step-up/" + id + "/verify").header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON).content("{\"code\":\"" + code + "\"}")).andExpect(status().isOk());
    }

    /** The code in the subject of the latest code email to this address. */
    private String codeSentTo(String address) {
        ArgumentCaptor<SimpleMailMessage> sent = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(sender, atLeastOnce()).send(sent.capture());
        List<SimpleMailMessage> codes = sent.getAllValues().stream()
                .filter(message -> address.equals(message.getTo()[0]) && message.getSubject().startsWith("Your Vision Map code: ")).toList();
        return codes.get(codes.size() - 1).getSubject().substring("Your Vision Map code: ".length());
    }

    private ResultActions perform(org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request, String token, long stepUp,
                                  Object body) throws Exception {
        return mockMvc.perform(request.header("Authorization", "Bearer " + token).header("X-Step-Up", String.valueOf(stepUp))
                .contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(body)));
    }

    private JsonNode read(ResultActions result) throws Exception {
        return objectMapper.readTree(result.andReturn().getResponse().getContentAsString());
    }

    private String register(String email) throws Exception {
        return objectMapper.readTree(mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("fullName", "Notice Test", "email", email, "password", PASSWORD))))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString()).get("token").asText();
    }
}
