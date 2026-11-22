package com.visionmapping.service.twofactor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.visionmapping.service.mail.StepUpMailer;
import com.visionmapping.service.mail.StepUpMailer.Delivery;
import com.visionmapping.service.stepup.StepUpAction;
import java.time.Clock;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * V21 phase 4 (TF-FR-06, TF-FR-40; TFC-10, TFC-20) with "Verify it's you" on:
 * two-step sign-in changes need a fresh code, and an authenticator user may
 * type the app's code instead of the emailed one.
 */
@SpringBootTest(properties = "app.step-up.enabled=true")
@AutoConfigureMockMvc
@ActiveProfiles("test")
class TwoFactorStepUpFlowTests {

    private static final String PASSWORD = "Steady-Harbor-Lamp-42";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private Clock clock;

    @MockitoBean
    private StepUpMailer mailer;

    @BeforeEach
    void mailWorks() {
        reset(mailer);
        when(mailer.delivery()).thenReturn(Delivery.EMAIL);
    }

    @Test
    void tfc10_tfc20_changingTwoStepSignInNeedsAFreshCode_soAnOpenSessionAloneCannotTurnItOff() throws Exception {
        String email = email("tf-su");
        String token = register(email);
        send(post("/api/account/two-factor/totp"), token).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("STEP_UP_REQUIRED"))
                .andExpect(jsonPath("$.action").value("TWO_FACTOR_SETTINGS"));

        long challenge = verifiedByEmail(token, email, "TWO_FACTOR_SETTINGS");
        String secret = read(send(post("/api/account/two-factor/totp").header("X-Step-Up", challenge), token)
                .andExpect(status().isOk())).get("secret").asText();
        send(post("/api/account/two-factor/totp/confirm"), token, Map.of("code", code(secret, 0))).andExpect(status().isOk());

        // TFC-20: someone holding the open session cannot turn it off.
        send(post("/api/account/two-factor/disable"), token).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("STEP_UP_REQUIRED"));
        assertThat(read(send(get("/api/account/two-factor"), token)).get("enabled").asBoolean()).isTrue();
    }

    @Test
    void tfFr40_anAuthenticatorCodeWorksInVerifyItsYou_once() throws Exception {
        String email = email("tf-su-app");
        String token = register(email);
        long setupChallenge = verifiedByEmail(token, email, "TWO_FACTOR_SETTINGS");
        String secret = read(send(post("/api/account/two-factor/totp").header("X-Step-Up", setupChallenge), token)).get("secret").asText();
        send(post("/api/account/two-factor/totp/confirm"), token, Map.of("code", code(secret, 0))).andExpect(status().isOk());

        long exportChallenge = request(token, "EXPORT_DATA");
        String appCode = code(secret, 1);
        send(post("/api/account/step-up/" + exportChallenge + "/verify"), token, Map.of("code", appCode))
                .andExpect(status().isOk()).andExpect(jsonPath("$.verified").value(true));
        send(get("/api/account/export").header("X-Step-Up", exportChallenge), token).andExpect(status().isOk());

        long again = request(token, "DELETE_ACCOUNT");
        // The same app code twice.
        send(post("/api/account/step-up/" + again + "/verify"), token, Map.of("code", appCode)).andExpect(status().isBadRequest());
    }

    private long verifiedByEmail(String token, String email, String action) throws Exception {
        long id = request(token, action);
        ArgumentCaptor<String> code = ArgumentCaptor.forClass(String.class);
        verify(mailer, atLeastOnce()).sendCode(eq(email), code.capture(), eq(StepUpAction.valueOf(action)), anyBoolean(), anyLong());
        send(post("/api/account/step-up/" + id + "/verify"), token, Map.of("code", code.getValue())).andExpect(status().isOk());
        return id;
    }

    private long request(String token, String action) throws Exception {
        return read(send(post("/api/account/step-up"), token, Map.of("action", action)).andExpect(status().isCreated()))
                .get("challengeId").asLong();
    }

    private String code(String secret, int stepsAhead) {
        return TotpCodes.code(secret, TotpCodes.step(clock.instant()) + stepsAhead);
    }

    private ResultActions send(MockHttpServletRequestBuilder request, String token) throws Exception {
        return mockMvc.perform(request.header("Authorization", "Bearer " + token));
    }

    private ResultActions send(MockHttpServletRequestBuilder request, String token, Object body) throws Exception {
        return mockMvc.perform(request.header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(body)));
    }

    private String register(String email) throws Exception {
        return read(mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("fullName", "Two Factor", "email", email, "password", PASSWORD))))
                .andExpect(status().isCreated())).get("token").asText();
    }

    private JsonNode read(ResultActions result) throws Exception {
        return objectMapper.readTree(result.andReturn().getResponse().getContentAsString());
    }

    private static String email(String prefix) {
        return prefix + "-" + System.nanoTime() + "@example.com";
    }
}
