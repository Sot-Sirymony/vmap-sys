package com.visionmapping.service.oauth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.visionmapping.service.stepup.StepUpService;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.util.MultiValueMap;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * V14: shared set-up for the Google sign-in flow tests. Google sign-in is on,
 * and Google's three addresses point at one local {@link GoogleStubServer}
 * (GA-NFR-02). The test classes share this Spring context.
 */
@SpringBootTest(properties = {
        "app.oauth.google.client-id=" + GoogleStubServer.CLIENT_ID,
        "app.oauth.google.client-secret=test-client-secret",
        "app.oauth.google.redirect-uri=http://localhost:8080/api/auth/oauth/google/callback",
        "app.oauth.google.frontend-url=https://app.example.test/",
        "app.oauth.google.timeout-seconds=1"
})
@AutoConfigureMockMvc
@ActiveProfiles("test")
abstract class GoogleSignInTestSupport {

    /** Lives for the whole test run, so every class sharing the context reaches it. */
    static final GoogleStubServer GOOGLE = startStub();
    static final String FRONTEND_CALLBACK = "https://app.example.test/oauth/google/callback#";

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ObjectMapper objectMapper;

    @Autowired
    GoogleSignInService service;

    /**
     * D-14-5: Google sign-in needs working "Verify it's you" codes. These tests
     * are not about the codes (StepUp*Tests are), so the service reports itself
     * active and lets every protected action through.
     */
    @MockitoBean
    StepUpService stepUpService;

    @DynamicPropertySource
    static void google(DynamicPropertyRegistry registry) {
        registry.add("app.oauth.google.authorization-uri", () -> GOOGLE.baseUrl() + "/auth");
        registry.add("app.oauth.google.token-uri", () -> GOOGLE.baseUrl() + "/token");
        registry.add("app.oauth.google.jwk-set-uri", () -> GOOGLE.baseUrl() + "/certs");
    }

    @BeforeEach
    void resetStub() {
        GOOGLE.reset();
        when(stepUpService.active()).thenReturn(true);
    }

    /** Starts a sign-in through the API and returns the decoded query of Google's address. */
    Map<String, String> start(boolean remember, String from) throws Exception {
        String body = mockMvc.perform(get("/api/auth/oauth/google/authorize-url")
                        .param("remember", String.valueOf(remember)).param("from", from))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", containsString("no-store")))
                .andReturn().getResponse().getContentAsString();
        String url = objectMapper.readTree(body).get("url").asText();
        assertThat(url).startsWith(GOOGLE.baseUrl() + "/auth?");
        MultiValueMap<String, String> raw = UriComponentsBuilder.fromUriString(url).build().getQueryParams();
        return raw.entrySet().stream().collect(Collectors.toMap(Map.Entry::getKey,
                entry -> URLDecoder.decode(List.copyOf(entry.getValue()).get(0), StandardCharsets.UTF_8)));
    }

    /** Calls the callback as Google's redirect would and returns the frontend Location. */
    String callback(String code, String state) throws Exception {
        return mockMvc.perform(get("/api/auth/oauth/google/callback").param("code", code).param("state", state))
                .andExpect(status().isFound())
                .andReturn().getResponse().getHeader("Location");
    }

    /** The key-value pairs of a frontend Location's fragment, decoded. */
    static Map<String, String> fragment(String location) {
        assertThat(location).startsWith(FRONTEND_CALLBACK);
        Map<String, String> values = new LinkedHashMap<>();
        for (String pair : location.substring(FRONTEND_CALLBACK.length()).split("&")) {
            int equals = pair.indexOf('=');
            values.put(pair.substring(0, equals), URLDecoder.decode(pair.substring(equals + 1), StandardCharsets.UTF_8));
        }
        return values;
    }

    static void assertFailure(ThrowingCallable call, GoogleSignInFailure expected) {
        assertThatThrownBy(call).isInstanceOf(GoogleSignInException.class)
                .extracting(thrown -> ((GoogleSignInException) thrown).failure()).isEqualTo(expected);
    }

    private static GoogleStubServer startStub() {
        try {
            return new GoogleStubServer();
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }
}
