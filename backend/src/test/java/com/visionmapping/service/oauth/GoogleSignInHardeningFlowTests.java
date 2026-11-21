package com.visionmapping.service.oauth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.visionmapping.security.audit.SecurityAlertService;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * V14 phase 5 (BRD 14.0.0, GA-FR-32 to GA-FR-34, GA-SEC-10; GAC-11): account
 * deletion and export for Google accounts, the security alert and headers.
 */
class GoogleSignInHardeningFlowTests extends GoogleSignInTestSupport {

    private static final String PASSWORD = "Steady-Harbor-Lamp-42";

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private SecurityAlertService alerts;

    @Test
    void gaFr32_d14_3_aGoogleAccountIsExportedWithItsLinkAndDeletedWithTheEmailCodeAlone() throws Exception {
        String sub = "1" + Long.toUnsignedString(UUID.randomUUID().getMostSignificantBits());
        String email = "delete-google-" + System.nanoTime() + "@gmail.com";
        String token = signUpWithGoogle(sub, email);
        Long userId = jdbcTemplate.queryForObject("select id from app_users where email = ?", Long.class, email);

        JsonNode export = objectMapper.readTree(mockMvc.perform(get("/api/account/export").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        JsonNode link = export.get("linkedAccounts").get(0);
        assertThat(link.get("provider").asText()).isEqualTo("GOOGLE");
        assertThat(link.get("email").asText()).isEqualTo(email);
        assertThat(link.get("linkedAt").isNull()).isFalse();
        assertThat(export.toString()).as("never the Google ID").doesNotContain(sub);

        mockMvc.perform(post("/api/account/delete").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isNoContent());
        assertThat(jdbcTemplate.queryForObject("select count(*) from app_users where id = ?", Integer.class, userId)).isZero();
        assertThat(jdbcTemplate.queryForObject("select count(*) from external_identities where subject = ?", Integer.class, sub)).isZero();
        assertThat(jdbcTemplate.queryForObject("select count(*) from oauth_handoffs where provider_subject = ?", Integer.class, sub)).isZero();
    }

    @Test
    void gaFr32_anAccountWithAPasswordStillNeedsItToDelete() throws Exception {
        String email = "delete-password-" + System.nanoTime() + "@example.com";
        String token = objectMapper.readTree(mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("fullName", "Keeps Password", "email", email, "password", PASSWORD))))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString()).get("token").asText();

        mockMvc.perform(post("/api/account/delete").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("That password is not correct."));
        assertThat(jdbcTemplate.queryForObject("select count(*) from app_users where email = ?", Integer.class, email)).isEqualTo(1);
    }

    @Test
    void gaFr34_manyFailedGoogleSignInsFromOneNetworkRaiseTheAlert() throws Exception {
        String network = "203.0.113." + (100 + (int) (System.nanoTime() % 100));
        for (int i = 0; i < 50; i++) {
            mockMvc.perform(get("/api/auth/oauth/google/callback").param("code", "x").param("state", "forged-" + i)
                            .header("X-Forwarded-For", network))
                    .andExpect(status().isFound());
        }
        assertThat(alerts.recentAlerts()).anyMatch(alert -> alert.contains("Many failed Google sign-ins from network " + network));
        assertThat(jdbcTemplate.queryForObject("select count(*) from security_events where event_type = 'GOOGLE_SIGN_IN_FAILED' "
                + "and ip_address = ? and detail = 'expired'", Integer.class, network)).isEqualTo(50);
    }

    @Test
    void gaSec10_theGoogleAnswersKeepTheStrictApiHeaders() throws Exception {
        mockMvc.perform(get("/api/auth/oauth/google/callback").param("state", "forged"))
                .andExpect(status().isFound())
                .andExpect(header().string("Referrer-Policy", "no-referrer"))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(header().exists("Content-Security-Policy"))
                .andExpect(header().exists("Strict-Transport-Security"));
        mockMvc.perform(post("/api/auth/oauth/google/exchange").contentType(MediaType.APPLICATION_JSON).content("{\"code\":\"forged\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(header().exists("Content-Security-Policy"));
    }

    private String signUpWithGoogle(String sub, String email) throws Exception {
        Map<String, String> query = start(true, "/");
        GOOGLE.answerWith(GOOGLE.sign(GoogleStubServer.claims(query.get("nonce")).subject(sub).claim("email", email).build()));
        String code = fragment(callback("google-code", query.get("state"))).get("code");
        return objectMapper.readTree(mockMvc.perform(post("/api/auth/oauth/google/exchange").contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("code", code))))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString()).get("token").asText();
    }
}
