package com.visionmapping.web;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.util.pattern.PathPattern;
import org.springframework.web.util.pattern.PathPatternParser;

/** BRD 33 D-33-2: which paths are forwarded, and how the forwarded request looks. */
class LegacyApiPathFilterTest {

    private static final List<PathPattern> PATTERNS = List.of(
            PathPatternParser.defaultInstance.parse("/api/v1/goals"),
            PathPatternParser.defaultInstance.parse("/api/v1/goals/{id}"));

    private final MockHttpServletResponse response = new MockHttpServletResponse();
    private final MockFilterChain chain = new MockFilterChain();

    @Test
    void anOldPathWithAVersionOneEndpointIsForwarded() throws Exception {
        MockHttpServletRequest request = request("/api/goals/5");
        request.setQueryString("includeArchived=true");

        new LegacyApiPathFilter(() -> PATTERNS, "Sat, 30 Jan 2027 00:00:00 GMT").doFilter(request, response, chain);

        HttpServletRequest forwarded = (HttpServletRequest) chain.getRequest();
        assertThat(forwarded.getRequestURI()).isEqualTo("/api/v1/goals/5");
        assertThat(forwarded.getServletPath()).isEqualTo("/api/v1/goals/5");
        assertThat(forwarded.getPathInfo()).isNull();
        assertThat(forwarded.getRequestURL()).hasToString("http://localhost/api/v1/goals/5");
        assertThat(forwarded.getQueryString()).isEqualTo("includeArchived=true");
        assertThat(response.getHeader("Deprecation")).isEqualTo("true");
        assertThat(response.getHeader("Sunset")).isEqualTo("Sat, 30 Jan 2027 00:00:00 GMT");
    }

    @Test
    void withNoSunsetDateOnlyDeprecationIsSent() throws Exception {
        new LegacyApiPathFilter(() -> PATTERNS, "  ").doFilter(request("/api/goals"), response, chain);

        assertThat(((HttpServletRequest) chain.getRequest()).getRequestURI()).isEqualTo("/api/v1/goals");
        assertThat(response.getHeader("Deprecation")).isEqualTo("true");
        assertThat(response.containsHeader("Sunset")).isFalse();
    }

    @Test
    void theContextPathIsKept() throws Exception {
        MockHttpServletRequest request = request("/app/api/goals/5");
        request.setContextPath("/app");

        new LegacyApiPathFilter(() -> PATTERNS, null).doFilter(request, response, chain);

        assertThat(((HttpServletRequest) chain.getRequest()).getRequestURI()).isEqualTo("/app/api/v1/goals/5");
    }

    @Test
    void pathsWithoutAVersionOneEndpointPassUnchanged() throws Exception {
        for (String path : new String[] {"/api/tasks", "/api/v1/goals/5", "/api/health", "/api/goals/5/archive"}) {
            MockHttpServletRequest request = request(path);
            MockFilterChain passed = new MockFilterChain();
            MockHttpServletResponse answer = new MockHttpServletResponse();

            new LegacyApiPathFilter(() -> PATTERNS, "x").doFilter(request, answer, passed);

            assertThat(passed.getRequest()).as(path).isSameAs(request);
            assertThat(answer.containsHeader("Deprecation")).as(path).isFalse();
        }
    }

    @Test
    void onlyOldApiPathsCountAsLegacy() {
        assertThat(LegacyApiPathFilter.isLegacy("/api/goals")).isTrue();
        assertThat(LegacyApiPathFilter.isLegacy("/api/v1")).isFalse();
        assertThat(LegacyApiPathFilter.isLegacy("/api/v1/goals")).isFalse();
        assertThat(LegacyApiPathFilter.isLegacy("/api/health")).isFalse();
        assertThat(LegacyApiPathFilter.isLegacy("/swagger-ui.html")).isFalse();
        assertThat(LegacyApiPathFilter.isLegacy("/api")).isFalse();
    }

    @Test
    void bothPrefixesAreListedForPathRules() {
        assertThat(ApiPaths.underBothPrefixes("/auth/login", "/admin/**"))
                .containsExactly("/api/auth/login", "/api/v1/auth/login", "/api/admin/**", "/api/v1/admin/**");
    }

    private static MockHttpServletRequest request(String uri) {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", uri);
        request.setServletPath(uri);
        return request;
    }
}
