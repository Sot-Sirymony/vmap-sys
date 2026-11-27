package com.visionmapping.web;

import java.net.URI;
import org.springframework.http.ResponseEntity;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * V33 (D-33-6): the one place that builds a {@code 201 Created} answer with its
 * {@code Location} header.
 *
 * <p>The location is a path relative to the server ({@code /api/v1/goals/42}),
 * not a full address: behind Render's proxy the app does not reliably know its
 * public host, and a relative {@code Location} is valid HTTP (RFC 9110 §10.2.2).
 */
public final class ApiResponses {

    private ApiResponses() {
    }

    /** {@code 201 Created}, with {@code Location} set to the current request's path followed by {@code /id}. */
    public static <T> ResponseEntity<T> created(Object id, T body) {
        return ResponseEntity.created(locationOf(id)).body(body);
    }

    /**
     * {@code 201 Created} for a resource made through another resource, for example a
     * step made from an obstacle: {@code Location} is {@code /api/v1} + {@code resource} + {@code /id}.
     */
    public static <T> ResponseEntity<T> createdAt(String resource, Object id, T body) {
        return ResponseEntity.created(UriComponentsBuilder.fromPath(ApiPaths.V1 + resource).path("/{id}").buildAndExpand(id).toUri())
                .body(body);
    }

    static URI locationOf(Object id) {
        String path = ((ServletRequestAttributes) RequestContextHolder.currentRequestAttributes()).getRequest().getRequestURI();
        return UriComponentsBuilder.fromPath(path).path("/{id}").buildAndExpand(id).toUri();
    }
}
