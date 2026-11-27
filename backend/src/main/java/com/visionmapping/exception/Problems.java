package com.visionmapping.exception;

import java.net.URI;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.lang.Nullable;

/**
 * V33 (D-33-13): every error answer is an RFC 9457 {@link ProblemDetail}, sent
 * as {@code application/problem+json}, and is built here.
 *
 * <p>Standard fields: {@code type}, {@code title}, {@code status},
 * {@code detail} and {@code instance}. VMS extensions: {@code reference}, the
 * only link between what the user sees and the server log (V10 SEC-10-02);
 * {@code timestamp}; {@code code}, {@code triesLeft} and {@code action} for a
 * page that acts on the answer (V20); and {@code errors}, the fields that
 * failed validation. For all of version 1 the old field names {@code message}
 * (the same text as {@code detail}), {@code error} and {@code path} are kept
 * too, so a browser tab with an older page still reads the error (Q-33-5).
 *
 * <p>{@code type} is {@code about:blank} for a plain HTTP error and
 * {@code urn:vms:problem:<code>} when there is a VMS code, for example
 * {@code urn:vms:problem:step-up-required} (Q-33-2).
 */
public final class Problems {

    static final String TYPE_PREFIX = "urn:vms:problem:";
    static final String REFERENCE = "reference";

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final int REFERENCE_BYTES = 6;

    private Problems() {
    }

    /** A field that failed validation, as listed in {@code errors}. */
    public record FieldProblem(String field, String message) {
    }

    /** A problem with no VMS code. */
    public static ProblemDetail of(HttpStatus status, String message, String path) {
        return of(status, message, path, null, null, null);
    }

    /** A problem with a code the page acts on, the tries left after a wrong code, and the code the page must ask for. */
    public static ProblemDetail of(HttpStatus status, String message, String path,
                                   @Nullable String code, @Nullable Integer triesLeft, @Nullable String action) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, message);
        problem.setTitle(status.getReasonPhrase());
        URI instance = instanceOf(path);
        if (instance != null) {
            problem.setInstance(instance);
        }
        if (code != null) {
            problem.setType(URI.create(TYPE_PREFIX + code.toLowerCase(Locale.ROOT).replace('_', '-')));
            problem.setProperty("code", code);
        }
        if (triesLeft != null) {
            problem.setProperty("triesLeft", triesLeft);
        }
        if (action != null) {
            problem.setProperty("action", action);
        }
        problem.setProperty(REFERENCE, newReference());
        problem.setProperty("timestamp", Instant.now());
        // Version 1 compatibility fields (D-33-13).
        problem.setProperty("message", message);
        problem.setProperty("error", status.getReasonPhrase());
        problem.setProperty("path", path);
        return problem;
    }

    /** A validation problem that lists every field that failed. */
    public static ProblemDetail invalid(String message, String path, List<FieldProblem> errors) {
        ProblemDetail problem = of(HttpStatus.BAD_REQUEST, message, path);
        problem.setProperty("errors", List.copyOf(errors));
        return problem;
    }

    /** The problem as an answer, with its status and {@code application/problem+json}. */
    public static ResponseEntity<ProblemDetail> answer(ProblemDetail problem) {
        return ResponseEntity.status(problem.getStatus()).contentType(MediaType.APPLICATION_PROBLEM_JSON).body(problem);
    }

    /** The reference of a problem built here, for the matching server log line. */
    public static String reference(ProblemDetail problem) {
        Object reference = problem.getProperties() == null ? null : problem.getProperties().get(REFERENCE);
        return reference == null ? "" : reference.toString();
    }

    /** Twelve hex characters: short enough to read out, random enough not to collide in a log search. */
    static String newReference() {
        byte[] bytes = new byte[REFERENCE_BYTES];
        RANDOM.nextBytes(bytes);
        return HexFormat.of().formatHex(bytes);
    }

    /** The request path as the problem's {@code instance}; none when it is not a valid URI reference. */
    @Nullable
    static URI instanceOf(@Nullable String path) {
        if (path == null || path.isEmpty()) {
            return null;
        }
        try {
            return URI.create(path);
        } catch (IllegalArgumentException notAUri) {
            return null;
        }
    }
}
