package com.visionmapping.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.icegreen.greenmail.configuration.GreenMailConfiguration;
import com.icegreen.greenmail.junit5.GreenMailExtension;
import com.icegreen.greenmail.util.GreenMailUtil;
import com.icegreen.greenmail.util.ServerSetupTest;
import com.visionmapping.service.ReminderService;
import jakarta.mail.internet.MimeMessage;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/**
 * FR-77 over a real SMTP conversation: the other reminder tests mock the
 * mailer, so this one points Spring Mail at an in-process SMTP server and
 * checks the message that actually arrives.
 */
@SpringBootTest(properties = {
        "spring.mail.host=127.0.0.1",
        "spring.mail.port=3025",
        "spring.mail.properties.mail.smtp.auth=false",
        "spring.mail.properties.mail.smtp.starttls.enable=false",
        "app.mail.from=reminders@visionmapping.test",
        "app.reminders.today-url=https://vms.example/today",
})
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ReminderSmtpDeliveryTests {

    @RegisterExtension
    static final GreenMailExtension SMTP = new GreenMailExtension(ServerSetupTest.SMTP)
            .withConfiguration(GreenMailConfiguration.aConfig().withDisabledAuthentication());

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ReminderService reminderService;

    @Test
    void aDueReminderArrivesAsARealEmail() throws Exception {
        String email = "smtp-" + System.nanoTime() + "@example.com";
        String token = objectMapper.readTree(mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("fullName", "Smtp User", "email", email, "password", "Steady-Harbor-Lamp-42"))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString()).get("token").asText();
        mockMvc.perform(put("/api/account/reminders")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("enabled", true, "timeZone", "UTC"))))
                .andExpect(status().isOk());
        Long userId = jdbcTemplate.queryForObject("select id from app_users where email = ?", Long.class, email);

        // A new account has no weekly review yet, which is itself something to remind about.
        reminderService.remindIfDue(userId, LocalDate.now().atTime(9, 0).atZone(ZoneId.of("UTC")).toInstant());

        assertThat(SMTP.waitForIncomingEmail(10_000, 1)).isTrue();
        MimeMessage message = SMTP.getReceivedMessages()[0];
        assertThat(message.getAllRecipients()[0].toString()).isEqualTo(email);
        assertThat(message.getFrom()[0].toString()).isEqualTo("reminders@visionmapping.test");
        assertThat(message.getSubject()).isEqualTo("Your Vision Mapping day");
        assertThat(GreenMailUtil.getBody(message)).contains("https://vms.example/today");

        // The test button sends straight away, to the address the user chose.
        mockMvc.perform(put("/api/account/reminders")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("enabled", true, "timeZone", "UTC", "reminderEmail", "other@example.org"))))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/account/reminders/test").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
        assertThat(SMTP.waitForIncomingEmail(10_000, 2)).isTrue();
        MimeMessage test = SMTP.getReceivedMessages()[1];
        assertThat(test.getAllRecipients()[0].toString()).isEqualTo("other@example.org");
        assertThat(GreenMailUtil.getBody(test)).contains("This is a test");
    }
}
