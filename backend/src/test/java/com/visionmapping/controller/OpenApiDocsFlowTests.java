package com.visionmapping.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.hamcrest.Matchers;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Swagger UI and the OpenAPI description: open without a token when springdoc
 * is on, with their own Content-Security-Policy, and gone (404) when it is off,
 * which is the production default.
 */
class OpenApiDocsFlowTests {

    private static final String DOCS_CSP = "default-src 'self'; script-src 'self'; style-src 'self' 'unsafe-inline'; "
            + "img-src 'self' data:; frame-ancestors 'none'; base-uri 'none'; form-action 'self'";
    private static final String API_CSP = "default-src 'none'; frame-ancestors 'none'; base-uri 'none'; form-action 'none'";

    @Nested
    @SpringBootTest
    @AutoConfigureMockMvc
    @ActiveProfiles("test")
    class WhenOn {

        @Autowired
        private MockMvc mockMvc;

        @Test
        void theDescriptionListsTheApiWithTheJwtScheme() throws Exception {
            mockMvc.perform(get("/v3/api-docs"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.info.title").value("Vision Mapping Management System API"))
                    .andExpect(jsonPath("$.components.securitySchemes.bearerAuth.scheme").value("bearer"))
                    .andExpect(jsonPath("$.security[0].bearerAuth").isArray())
                    .andExpect(jsonPath("$.paths['/api/tasks']").exists())
                    .andExpect(jsonPath("$.paths['/api/auth/login']").exists());
        }

        @Test
        void swaggerUiOpensWithItsOwnContentSecurityPolicy() throws Exception {
            mockMvc.perform(get("/swagger-ui/index.html"))
                    .andExpect(status().isOk())
                    .andExpect(content().string(Matchers.containsString("swagger-ui")))
                    .andExpect(header().string("Content-Security-Policy", DOCS_CSP))
                    .andExpect(header().string("X-Frame-Options", "DENY"));
        }

        @Test
        void theApiKeepsItsStrictPolicyAndStillNeedsAToken() throws Exception {
            mockMvc.perform(get("/api/health"))
                    .andExpect(header().string("Content-Security-Policy", API_CSP));
            mockMvc.perform(get("/api/tasks"))
                    .andExpect(status().isUnauthorized())
                    .andExpect(header().string("Content-Security-Policy", API_CSP));
        }
    }

    @Nested
    @SpringBootTest
    @AutoConfigureMockMvc
    @ActiveProfiles("test")
    @TestPropertySource(properties = {"springdoc.api-docs.enabled=false", "springdoc.swagger-ui.enabled=false"})
    class WhenOff {

        @Autowired
        private MockMvc mockMvc;

        @Test
        void neitherTheDescriptionNorTheUiIsServed() throws Exception {
            mockMvc.perform(get("/v3/api-docs")).andExpect(status().isNotFound());
            mockMvc.perform(get("/swagger-ui/index.html")).andExpect(status().isNotFound());
        }
    }
}
