package com.visionmapping.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.visionmapping.exception.ErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

/**
 * Answers a request that arrived without usable credentials.
 *
 * <p>Without this, Spring falls back to its default access-denied handling and
 * replies <b>403 Forbidden</b> to an anonymous caller. That is the wrong answer:
 * 403 means "you are known and still may not do this", which tells a client the
 * problem is permissions rather than a missing or expired session. 401 says
 * "authenticate and try again", which is both accurate and actionable — it is
 * what lets a client distinguish "log in again" from "you will never be allowed".
 *
 * <p>The 403 case is still reachable and still correct: an authenticated user who
 * lacks a role gets it from {@link RestAccessDeniedHandler} here, or from the
 * global exception handler when a service throws {@code AccessDeniedException}
 * (as admin-only issue-report triage does).
 *
 * <p>The body matches {@link ErrorResponse} so every error the API emits has the
 * same shape, whether it came from a controller or from the security filter
 * chain — which never reaches a controller at all.
 */
@Component
@RequiredArgsConstructor
public class RestAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private final ObjectMapper objectMapper;

    @Override
    public void commence(
            HttpServletRequest request,
            HttpServletResponse response,
            AuthenticationException authException
    ) throws IOException {
        response.setStatus(HttpStatus.UNAUTHORIZED.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        // Deliberately generic: the underlying exception distinguishes "no token"
        // from "expired" from "bad signature", and echoing that back tells an
        // attacker which of their guesses was closer.
        objectMapper.writeValue(response.getOutputStream(), new ErrorResponse(
                Instant.now(),
                HttpStatus.UNAUTHORIZED.value(),
                HttpStatus.UNAUTHORIZED.getReasonPhrase(),
                "Authentication required.",
                request.getRequestURI()
        ));
    }
}
