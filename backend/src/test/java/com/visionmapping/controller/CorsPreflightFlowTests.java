package com.visionmapping.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

/**
 * The frontend and API are on different sites, so the browser asks first (a
 * CORS preflight) before sending any header beyond the basic ones. The
 * "Verify it's you" headers must be allowed, or every protected action fails
 * in production with a CORS error (found 22 Nov 2026 on two-step setup).
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties = "app.cors.allowed-origins=https://frontend.example")
class CorsPreflightFlowTests {

    private static final String ORIGIN = "https://frontend.example";

    @Autowired
    private MockMvc mockMvc;

    @Test
    void theStepUpHeaderIsAllowedOnTwoStepSetup() throws Exception {
        preflight("/api/account/two-factor/totp", "authorization,content-type,x-step-up")
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", ORIGIN))
                .andExpect(header().string("Access-Control-Allow-Headers", Matchers.containsStringIgnoringCase("X-Step-Up")));
    }

    @Test
    void bothStepUpHeadersAreAllowedOnTheReminderAddress() throws Exception {
        preflight("/api/account/reminders", "authorization,content-type,x-step-up,x-step-up-confirm")
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Headers", Matchers.containsStringIgnoringCase("X-Step-Up-Confirm")));
    }

    @Test
    void anUnknownHeaderIsNotListedSoTheBrowserRefusesIt() throws Exception {
        // Spring answers 200 for the headers it allows and leaves the rest out; the browser
        // then refuses the request. That missing entry is exactly what broke X-Step-Up.
        preflight("/api/account/two-factor/totp", "authorization,x-something-else")
                .andExpect(header().string("Access-Control-Allow-Headers",
                        Matchers.not(Matchers.containsStringIgnoringCase("x-something-else"))));
    }

    @Test
    void anotherSiteIsStillRefused() throws Exception {
        mockMvc.perform(options("/api/account/two-factor/totp")
                        .header("Origin", "https://evil.example")
                        .header("Access-Control-Request-Method", "POST")
                        .header("Access-Control-Request-Headers", "authorization,x-step-up"))
                .andExpect(status().isForbidden());
    }

    private org.springframework.test.web.servlet.ResultActions preflight(String path, String headers) throws Exception {
        return mockMvc.perform(options(path)
                .header("Origin", ORIGIN)
                .header("Access-Control-Request-Method", "POST")
                .header("Access-Control-Request-Headers", headers));
    }
}
