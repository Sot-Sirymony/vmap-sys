package com.visionmapping.exception;

import lombok.RequiredArgsConstructor;
import com.visionmapping.security.audit.SecurityOutcome;
import com.visionmapping.security.audit.SecurityEventType;
import com.visionmapping.security.audit.SecurityEventService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

@RestControllerAdvice
@RequiredArgsConstructor
public class GlobalExceptionHandler {

    private final SecurityEventService securityEvents;

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(
            MethodArgumentNotValidException exception,
            HttpServletRequest request
    ) {
        String message = exception.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(error -> error.getField() + ": " + error.getDefaultMessage())
                .orElse("Validation failed");

        return buildResponse(HttpStatus.BAD_REQUEST, message, request);
    }

    /**
     * A query param that can't be converted to its target type — most often an
     * unknown enum constant in a filter, e.g. ?supportType=NOPE. That is bad
     * input, not a server fault, so it must not surface as a 500.
     */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorResponse> handleTypeMismatch(
            MethodArgumentTypeMismatchException exception,
            HttpServletRequest request
    ) {
        return buildResponse(
                HttpStatus.BAD_REQUEST,
                "Invalid value for '" + exception.getName() + "'.",
                request);
    }

    /**
     * A query or path parameter outside its declared limits, e.g. ?minutes=1
     * where at least 5 is allowed. Spring raises one of these two depending on
     * how the controller is validated; both are bad input, never a 500.
     */
    @ExceptionHandler({ConstraintViolationException.class, HandlerMethodValidationException.class})
    public ResponseEntity<ErrorResponse> handleParameterValidation(Exception exception, HttpServletRequest request) {
        return buildResponse(HttpStatus.BAD_REQUEST, "One of the values sent is not allowed.", request);
    }

    /** A path that exists but not for this method, e.g. POST where only PUT is mapped: 405, not a 500. */
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ErrorResponse> handleMethodNotSupported(
            HttpRequestMethodNotSupportedException exception,
            HttpServletRequest request
    ) {
        return buildResponse(HttpStatus.METHOD_NOT_ALLOWED, "This action is not supported here.", request);
    }

    /** A required query parameter left out is the caller's mistake, like a wrong one above. */
    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ErrorResponse> handleMissingParameter(
            MissingServletRequestParameterException exception,
            HttpServletRequest request
    ) {
        return buildResponse(
                HttpStatus.BAD_REQUEST,
                "Missing required parameter '" + exception.getParameterName() + "'.",
                request);
    }

    /**
     * A request body Jackson can't read — most often an unknown enum constant in
     * a JSON field, e.g. {"themeAccent":"CHARTREUSE"}. This is the same class of
     * mistake as the query-param case above, and deserves the same 400 rather
     * than falling through to the catch-all as an "Unexpected server error".
     *
     * <p>BR-33 leans on this: an unrecognised appearance value must be refused
     * at the boundary rather than stored, and the caller has to be able to tell
     * "you sent something invalid" apart from "the server broke".
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleUnreadableBody(
            HttpMessageNotReadableException exception,
            HttpServletRequest request
    ) {
        // The raw Jackson message names internal classes and echoes the payload,
        // so it isn't safe or useful to return verbatim.
        return buildResponse(HttpStatus.BAD_REQUEST, "Malformed or invalid request body.", request);
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleNotFound(
            ResourceNotFoundException exception,
            HttpServletRequest request
    ) {
        return buildResponse(HttpStatus.NOT_FOUND, exception.getMessage(), request);
    }

    @ExceptionHandler(BusinessRuleException.class)
    public ResponseEntity<ErrorResponse> handleBusinessRule(
            BusinessRuleException exception,
            HttpServletRequest request
    ) {
        return buildResponse(HttpStatus.BAD_REQUEST, exception.getMessage(), request);
    }

    /**
     * A user reaching an admin-only action (e.g. triaging another user's issue
     * report). It's a legitimate request from an authenticated user who simply
     * lacks the role — 403, not a 500.
     */
    /**
     * A rate limit, not a rejection of what was sent — the same request may well
     * succeed later, which is what 429 tells the client and 400 would not.
     */
    @ExceptionHandler(TooManyRequestsException.class)
    public ResponseEntity<ErrorResponse> handleTooManyRequests(
            TooManyRequestsException exception,
            HttpServletRequest request
    ) {
        securityEvents.record(SecurityEventType.RATE_LIMITED, SecurityOutcome.DENIED, null, null,
                request.getMethod() + " " + request.getRequestURI());
        ResponseEntity<ErrorResponse> response =
                buildResponse(HttpStatus.TOO_MANY_REQUESTS, exception.getMessage(), request);
        if (exception.getRetryAfterSeconds() == null) {
            return response;
        }
        return ResponseEntity.status(response.getStatusCode())
                .header(HttpHeaders.RETRY_AFTER, String.valueOf(exception.getRetryAfterSeconds()))
                .body(response.getBody());
    }

    /**
     * V10 (SEC-02-07): an upload over spring.servlet.multipart.max-file-size.
     * Without this it fell through to the catch-all and looked like a server
     * fault; 413 tells the user the file itself is the problem.
     */
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ErrorResponse> handleUploadTooLarge(
            MaxUploadSizeExceededException exception,
            HttpServletRequest request
    ) {
        return buildResponse(HttpStatus.PAYLOAD_TOO_LARGE,
                "The file is too large. The largest file you can upload is 10 MB.", request);
    }

    /** AI-NFR-02: AI is off or unreachable; the rest of the app still works. */
    @ExceptionHandler(AiUnavailableException.class)
    public ResponseEntity<ErrorResponse> handleAiUnavailable(
            AiUnavailableException exception,
            HttpServletRequest request
    ) {
        return buildResponse(HttpStatus.SERVICE_UNAVAILABLE, exception.getMessage(), request);
    }

    /** V10 (SEC-07-03): the refresh token no longer opens a session. */
    @ExceptionHandler(SessionEndedException.class)
    public ResponseEntity<ErrorResponse> handleSessionEnded(
            SessionEndedException exception,
            HttpServletRequest request
    ) {
        return buildResponse(HttpStatus.UNAUTHORIZED, exception.getMessage(), request);
    }

    /** V10 (SEC-07-04): another tab renewed the session first; retry with its token. */
    @ExceptionHandler(SessionSupersededException.class)
    public ResponseEntity<ErrorResponse> handleSessionSuperseded(
            SessionSupersededException exception,
            HttpServletRequest request
    ) {
        return buildResponse(HttpStatus.CONFLICT, exception.getMessage(), request);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorResponse> handleAccessDenied(
            AccessDeniedException exception,
            HttpServletRequest request
    ) {
        securityEvents.record(SecurityEventType.ACCESS_DENIED, SecurityOutcome.DENIED, null, null,
                request.getMethod() + " " + request.getRequestURI());
        return buildResponse(HttpStatus.FORBIDDEN, exception.getMessage(), request);
    }

    @ExceptionHandler({BadCredentialsException.class, AuthenticationException.class})
    public ResponseEntity<ErrorResponse> handleAuthentication(
            RuntimeException exception,
            HttpServletRequest request
    ) {
        return buildResponse(HttpStatus.UNAUTHORIZED, "Invalid email or password.", request);
    }

    /**
     * A request to a path with no controller mapping — e.g. the frontend calling
     * an endpoint a stale backend doesn't have yet. That is "not found", not a
     * server fault, and must not be swallowed by the catch-all below as a 500.
     */
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ErrorResponse> handleNoResource(
            NoResourceFoundException exception,
            HttpServletRequest request
    ) {
        return buildResponse(HttpStatus.NOT_FOUND, "No endpoint for " + request.getRequestURI(), request);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpected(
            Exception exception,
            HttpServletRequest request
    ) {
        // Log the real cause: the client only ever sees a generic message, so
        // without this the actual exception (a failed query, a missing table)
        // would be invisible and impossible to diagnose.
        log.error("Unhandled exception on {} {}", request.getMethod(), request.getRequestURI(), exception);
        return buildResponse(HttpStatus.INTERNAL_SERVER_ERROR, "Unexpected server error", request);
    }

    private ResponseEntity<ErrorResponse> buildResponse(
            HttpStatus status,
            String message,
            HttpServletRequest request
    ) {
        ErrorResponse response = new ErrorResponse(
                Instant.now(),
                status.value(),
                status.getReasonPhrase(),
                message,
                request.getRequestURI()
        );

        return ResponseEntity.status(status).body(response);
    }
}
