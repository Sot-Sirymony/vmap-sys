package com.visionmapping.web;

import static org.hamcrest.Matchers.matchesPattern;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.visionmapping.security.Authorities;
import com.visionmapping.service.stepup.StepUpContext;
import com.visionmapping.service.stepup.StepUpService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * BRD 33 phase 1: the version 1 foundation, end to end. No real controller has
 * moved to {@code /api/v1} yet, so a probe controller that exists only in this
 * test stands in for one (nested classes of a test are left out of component
 * scanning; it is imported here only).
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(ApiVersionFlowTests.ProbeController.class)
@TestPropertySource(properties = "app.api.legacy-sunset=Sat, 30 Jan 2027 00:00:00 GMT")
class ApiVersionFlowTests {

    private static final String PROBLEM_JSON = MediaType.APPLICATION_PROBLEM_JSON_VALUE;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    private String token;

    @BeforeEach
    void signUp() throws Exception {
        String body = mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("fullName", "Version Tester",
                                "email", "v33-" + UUID.randomUUID() + "@example.com", "password", "Steady-Harbor-Lamp-42"))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        token = objectMapper.readTree(body).get("token").asText();
    }

    /** CSC-02, D-33-2: an old path reaches the version 1 endpoint and says it is deprecated. */
    @Test
    void anOldPathIsForwardedToVersionOneWithTheDeprecationHeaders() throws Exception {
        mockMvc.perform(get("/api/v33-probes/7").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.path").value("/api/v1/v33-probes/7"))
                .andExpect(header().string(LegacyApiPathFilter.DEPRECATION_HEADER, "true"))
                .andExpect(header().string(LegacyApiPathFilter.SUNSET_HEADER, "Sat, 30 Jan 2027 00:00:00 GMT"));
    }

    @Test
    void aVersionOnePathIsNotMarkedDeprecated() throws Exception {
        mockMvc.perform(get("/api/v1/v33-probes/7").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.path").value("/api/v1/v33-probes/7"))
                .andExpect(header().doesNotExist(LegacyApiPathFilter.DEPRECATION_HEADER));
    }

    /** D-33-1: the health check is never versioned and never marked deprecated. */
    @Test
    void theHealthCheckStaysUnversioned() throws Exception {
        mockMvc.perform(get("/api/health"))
                .andExpect(status().isOk())
                .andExpect(header().doesNotExist(LegacyApiPathFilter.DEPRECATION_HEADER));
    }

    /** Phase 2: a moved controller answers on /api/v1, and its old path still works with the notice. */
    @Test
    void aMovedControllerAnswersOnBothPaths() throws Exception {
        mockMvc.perform(get("/api/v1/vision-areas").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(header().doesNotExist(LegacyApiPathFilter.DEPRECATION_HEADER));
        mockMvc.perform(get("/api/vision-areas").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(header().string(LegacyApiPathFilter.DEPRECATION_HEADER, "true"));
    }

    /** CSC-06, D-33-9, D-33-10: a paged list, its size limit and its sort whitelist. */
    @Test
    void aPagedListLimitsItsSizeAndChecksItsSort() throws Exception {
        mockMvc.perform(get("/api/v1/tasks").param("size", "500").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.size").value(200))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.content").isArray());
        mockMvc.perform(get("/api/v1/tasks").param("sort", "passwordHash").header("Authorization", "Bearer " + token))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(PROBLEM_JSON));
        mockMvc.perform(get("/api/v1/tasks").param("sort", "dueDate,desc").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sort[0].property").value("dueDate"))
                .andExpect(jsonPath("$.sort[1].property").value("id"));
    }

    /** BR-33-7, CS-NFR-04: the old path passes the same security as the new one. */
    @Test
    void bothPathsNeedATokenAndAnswerWithAProblem() throws Exception {
        for (String path : new String[] {"/api/v33-probes/7", "/api/v1/v33-probes/7"}) {
            mockMvc.perform(get(path))
                    .andExpect(status().isUnauthorized())
                    .andExpect(content().contentType(PROBLEM_JSON))
                    .andExpect(jsonPath("$.status").value(401))
                    .andExpect(jsonPath("$.title").value("Unauthorized"))
                    .andExpect(jsonPath("$.detail").value("Authentication required."))
                    .andExpect(jsonPath("$.instance").value("/api/v1/v33-probes/7"))
                    .andExpect(jsonPath("$.reference").value(matchesPattern("[0-9a-f]{12}")))
                    .andExpect(jsonPath("$.message").value("Authentication required."))
                    .andExpect(jsonPath("$.error").value("Unauthorized"));
        }
    }

    /** CSC-03, D-33-6: a create answers 201 with a Location on the version 1 path, however it was reached. */
    @Test
    void aCreateAnswers201WithALocation() throws Exception {
        for (String path : new String[] {"/api/v1/v33-probes", "/api/v33-probes"}) {
            mockMvc.perform(post(path).header("Authorization", "Bearer " + token)
                            .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"probe\"}"))
                    .andExpect(status().isCreated())
                    .andExpect(header().string("Location", "/api/v1/v33-probes/42"))
                    .andExpect(jsonPath("$.id").value(42));
        }
    }

    /** CSC-05, D-33-13: a body that fails validation lists its fields. */
    @Test
    void anInvalidBodyListsTheFieldsThatFailed() throws Exception {
        mockMvc.perform(post("/api/v1/v33-probes").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(PROBLEM_JSON))
                .andExpect(jsonPath("$.type").value("about:blank"))
                .andExpect(jsonPath("$.detail").value(startsWith("name: ")))
                .andExpect(jsonPath("$.errors[0].field").value("name"))
                .andExpect(jsonPath("$.errors[0].message").isNotEmpty());
    }

    /**
     * A handler with a @Positive id and a @Valid body: the body's errors still
     * name the field (MethodArgumentNotValidException), and a bad id is refused
     * by the class-level @Validated proxy (ConstraintViolationException).
     */
    @Test
    void anInvalidBodyNextToAPositiveIdStillNamesTheField() throws Exception {
        mockMvc.perform(put("/api/v1/v33-probes/7").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(PROBLEM_JSON))
                .andExpect(jsonPath("$.detail").value(startsWith("name: ")))
                .andExpect(jsonPath("$.errors[0].field").value("name"));
        mockMvc.perform(put("/api/v1/v33-probes/0").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"probe\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("One of the values sent is not allowed."));
    }

    /** CSC-04, D-33-8: a path id must be positive. */
    @Test
    void aPathIdMustBePositive() throws Exception {
        mockMvc.perform(get("/api/v1/v33-probes/0").header("Authorization", "Bearer " + token))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(PROBLEM_JSON))
                .andExpect(jsonPath("$.reference").isNotEmpty());
    }

    /** CSC-07, CS-NFR-06: @PreAuthorize refuses on its own, with no path rule behind it. */
    @Test
    void preAuthorizeRefusesAUserWithoutThePermission() throws Exception {
        mockMvc.perform(get("/api/v1/v33-probes/guarded").header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden())
                .andExpect(content().contentType(PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(403));
    }

    /** D-33-12: the interceptor keeps the step-up header for the service to read. */
    @Test
    void theStepUpHeaderReachesTheContext() throws Exception {
        mockMvc.perform(get("/api/v1/v33-probes/7").header("Authorization", "Bearer " + token)
                        .header(StepUpService.HEADER, "123"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.stepUp").value("123"));
    }

    @Test
    void anUnknownPathIsANotFoundProblem() throws Exception {
        mockMvc.perform(get("/api/v1/no-such-thing").header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound())
                .andExpect(content().contentType(PROBLEM_JSON));
    }

    /** A controller built to the V33 standard, for this test only. */
    @RestController
    @RequestMapping(value = ApiPaths.V1 + "/v33-probes", produces = MediaType.APPLICATION_JSON_VALUE)
    @Validated
    public static class ProbeController {

        public record ProbeRequest(@NotBlank String name) {
        }

        public record ProbeResponse(Long id, String path, String stepUp) {
        }

        @GetMapping("/{id}")
        public ResponseEntity<ProbeResponse> get(@PathVariable @Positive Long id, HttpServletRequest request) {
            return ResponseEntity.ok(new ProbeResponse(id, request.getRequestURI(), StepUpContext.current().challenge()));
        }

        @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
        public ResponseEntity<ProbeResponse> create(@Valid @RequestBody ProbeRequest body, HttpServletRequest request) {
            return ApiResponses.created(42L, new ProbeResponse(42L, request.getRequestURI(), null));
        }

        @PutMapping(value = "/{id}", consumes = MediaType.APPLICATION_JSON_VALUE)
        public ResponseEntity<ProbeResponse> update(@PathVariable @Positive Long id, @Valid @RequestBody ProbeRequest body,
                                                    HttpServletRequest request) {
            return ResponseEntity.ok(new ProbeResponse(id, request.getRequestURI(), null));
        }

        @GetMapping("/guarded")
        @PreAuthorize("hasAuthority('" + Authorities.MANAGE_ROLES + "')")
        public ResponseEntity<ProbeResponse> guarded(HttpServletRequest request) {
            return ResponseEntity.ok(new ProbeResponse(0L, request.getRequestURI(), null));
        }
    }
}
