package com.visionmapping.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.visionmapping.exception.AiUnavailableException;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

/**
 * V33 (D-33-11, BR-33-5): answers with a stream of server-sent events, so a
 * controller hands over what to send and keeps no {@code try}/{@code catch}.
 *
 * <p>Once the stream has started, the HTTP status has already been sent, so the
 * global handler can no longer answer. An AI failure part way through is sent
 * as an {@code error} event instead, as before (AI-NFR-01).
 */
@Component
@RequiredArgsConstructor
public class StreamingResponder {

    /** What a stream sends, through the events it is given. */
    @FunctionalInterface
    public interface EventSource {
        void sendTo(ServerSentEvents events);
    }

    private final ObjectMapper objectMapper;

    public ResponseEntity<StreamingResponseBody> events(EventSource source) {
        StreamingResponseBody body = output -> {
            ServerSentEvents events = new ServerSentEvents(output, objectMapper);
            try {
                source.sendTo(events);
            } catch (AiUnavailableException exception) {
                events.send("error", Map.of("message", exception.getMessage()));
            }
        };
        return ResponseEntity.ok()
                .contentType(MediaType.TEXT_EVENT_STREAM)
                .header(HttpHeaders.CACHE_CONTROL, "no-cache")
                // Stops a reverse proxy from holding the stream back until the end.
                .header("X-Accel-Buffering", "no")
                .body(body);
    }
}
