package com.visionmapping.security;

import com.visionmapping.security.audit.SecurityOutcome;
import com.visionmapping.security.audit.SecurityEventType;
import com.visionmapping.security.audit.SecurityEventService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.visionmapping.exception.ErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

/**
 * Answers an <i>authenticated</i> request that is not allowed to proceed — the
 * genuine 403 case, as distinct from the missing-credentials 401 that
 * {@link RestAuthenticationEntryPoint} handles.
 *
 * <p>Paired with the entry point so the two outcomes stay distinguishable: with
 * only one of them configured, Spring collapses both into whichever is present
 * and a client can no longer tell "sign in again" from "you lack the role".
 *
 * <p>Emits the same {@link ErrorResponse} shape as every other error, so a
 * denial from the filter chain looks like one from a controller.
 */
@Component
@RequiredArgsConstructor
public class RestAccessDeniedHandler implements AccessDeniedHandler {

    private final ObjectMapper objectMapper;
    private final SecurityEventService securityEvents;

    @Override
    public void handle(
            HttpServletRequest request,
            HttpServletResponse response,
            AccessDeniedException accessDeniedException
    ) throws IOException {
        securityEvents.record(SecurityEventType.ACCESS_DENIED, SecurityOutcome.DENIED, null, null,
                request.getMethod() + " " + request.getRequestURI());
        response.setStatus(HttpStatus.FORBIDDEN.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(response.getOutputStream(), ErrorResponse.of(HttpStatus.FORBIDDEN, "You do not have permission to perform this action.", request.getRequestURI()));
    }
}
