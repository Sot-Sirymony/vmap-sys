package com.visionmapping.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

/**
 * FR-39: appearance preferences through the real web/security/JPA stack — the
 * column defaults the migration wrote, a saved change surviving a fresh read
 * (FR-39.6, the cross-browser guarantee), and the endpoint refusing anonymous
 * callers.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AppearancePreferenceFlowTests {

    private static final String PATH = "/api/preferences/appearance";
    private static final String ACCENT = "$.themeAccent";
    private static final String MODE = "$.themeMode";
    private static final String FONT_SIZE = "$.fontSize";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    /**
     * AC-8: an account created without touching Appearance reads back today's
     * defaults, so existing users are unaffected by this feature.
     */
    @Test
    void aNewAccountReadsBackTheDocumentedDefaults() throws Exception {
        String token = registerAndToken("appearance-defaults");

        readAppearance(token)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.themePreset").value("FLUENT_SYSTEM"))
                .andExpect(jsonPath(MODE).value("SYSTEM"))
                .andExpect(jsonPath(ACCENT).value("BLUE"))
                .andExpect(jsonPath("$.uiDensity").value("COMFORTABLE"))
                .andExpect(jsonPath(FONT_SIZE).value("MEDIUM"))
                .andExpect(jsonPath("$.highContrast").value(false))
                .andExpect(jsonPath("$.reduceMotion").value(false));
    }

    /**
     * FR-39.6: the whole point of account persistence — a saved look is still
     * there on a later, independent request, which is what makes it survive a
     * different browser.
     */
    @Test
    void savedPreferencesSurviveAFreshRead() throws Exception {
        String token = registerAndToken("appearance-persist");

        Map<String, Object> body = new HashMap<>();
        body.put("themePreset", "MIDNIGHT");
        body.put("themeMode", "DARK");
        body.put("themeAccent", "PURPLE");
        body.put("uiDensity", "COMPACT");
        body.put("fontSize", "LARGE");
        body.put("highContrast", true);
        body.put("reduceMotion", true);

        saveAppearance(token, body)
                .andExpect(status().isOk())
                .andExpect(jsonPath(ACCENT).value("PURPLE"));

        readAppearance(token)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.themePreset").value("MIDNIGHT"))
                .andExpect(jsonPath(MODE).value("DARK"))
                .andExpect(jsonPath(ACCENT).value("PURPLE"))
                .andExpect(jsonPath("$.uiDensity").value("COMPACT"))
                .andExpect(jsonPath(FONT_SIZE).value("LARGE"))
                .andExpect(jsonPath("$.highContrast").value(true))
                .andExpect(jsonPath("$.reduceMotion").value(true));
    }

    /** A one-control change must not reset the controls it didn't mention. */
    @Test
    void aPartialUpdateLeavesTheOtherControlsAlone() throws Exception {
        String token = registerAndToken("appearance-partial");

        saveAppearance(token, Map.of("themeAccent", "BRASS"))
                .andExpect(status().isOk());

        saveAppearance(token, Map.of("fontSize", "SMALL"))
                .andExpect(status().isOk())
                .andExpect(jsonPath(ACCENT).value("BRASS"))
                .andExpect(jsonPath(FONT_SIZE).value("SMALL"));
    }

    /**
     * FR-39.6: the login response carries the theme, so the client never has to
     * paint a default theme while a second request is in flight.
     */
    @Test
    void theLoginResponseCarriesTheSavedAppearance() throws Exception {
        String email = "appearance-login-" + System.nanoTime() + "@example.com";
        String token = registerWithEmail(email);

        saveAppearance(token, Map.of("themeAccent", "STEEL", "themeMode", "DARK"))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("email", email, "password", "Password123"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.appearance.themeAccent").value("STEEL"))
                .andExpect(jsonPath("$.appearance.themeMode").value("DARK"));
    }

    /**
     * BR-33: the endpoint is not public — no session, no preferences. It falls
     * under {@code anyRequest().authenticated()} rather than needing its own
     * matcher.
     *
     * <p>403 rather than 401 is the app's existing behaviour for every protected
     * endpoint: SecurityConfig configures no {@code authenticationEntryPoint},
     * so Spring's default access-denied handling answers an anonymous request.
     * Asserted as-is here — making it a 401 is a sensible change but an app-wide
     * one, not FR-39's to make.
     */
    @Test
    void anonymousCallersAreRejected() throws Exception {
        mockMvc.perform(get(PATH)).andExpect(status().isForbidden());
    }

    /**
     * BR-33: a value outside the curated set is refused at the boundary rather
     * than stored, so the database can only ever hold a renderable option.
     */
    @Test
    void anUnknownAccentIsRejectedRatherThanStored() throws Exception {
        String token = registerAndToken("appearance-bad-enum");

        mockMvc.perform(put(PATH)
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"themeAccent\":\"CHARTREUSE\"}"))
                .andExpect(status().isBadRequest());

        readAppearance(token).andExpect(jsonPath(ACCENT).value("BLUE"));
    }

    /** One user's appearance is invisible to another (AC-7). */
    @Test
    void oneUsersAppearanceDoesNotLeakIntoAnothers() throws Exception {
        String mine = registerAndToken("appearance-mine");
        String theirs = registerAndToken("appearance-theirs");

        saveAppearance(mine, Map.of("themeAccent", "MAGENTA"))
                .andExpect(status().isOk());

        readAppearance(theirs)
                .andExpect(status().isOk())
                .andExpect(jsonPath(ACCENT).value("BLUE"));
    }

    private ResultActions readAppearance(String token) throws Exception {
        return mockMvc.perform(get(PATH).header(HttpHeaders.AUTHORIZATION, bearer(token)));
    }

    private ResultActions saveAppearance(String token, Map<String, Object> body) throws Exception {
        return mockMvc.perform(put(PATH)
                .header(HttpHeaders.AUTHORIZATION, bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body)));
    }

    private static String bearer(String token) {
        return "Bearer " + token;
    }

    /** Registers a throwaway account and returns its token. */
    private String registerAndToken(String prefix) throws Exception {
        return registerWithEmail(prefix + "-" + System.nanoTime() + "@example.com");
    }

    /** For the one test that logs back in, and so needs the email it used. */
    private String registerWithEmail(String email) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "fullName", "Appearance Test User",
                                "email", email,
                                "password", "Password123"
                        ))))
                .andExpect(status().isCreated())
                .andReturn();

        return objectMapper.readTree(result.getResponse().getContentAsString()).get("token").asText();
    }
}
