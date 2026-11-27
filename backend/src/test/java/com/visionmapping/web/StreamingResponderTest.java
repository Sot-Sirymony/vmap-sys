package com.visionmapping.web;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.visionmapping.exception.AiUnavailableException;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

/** BRD 33 D-33-11, BR-33-5: the stream sends what it is given, and an AI failure part way through as an error event. */
class StreamingResponderTest {

    private final StreamingResponder responder = new StreamingResponder(new ObjectMapper());

    @Test
    void theEventsAreSentAsServerSentEventsWithNoBuffering() throws Exception {
        ResponseEntity<StreamingResponseBody> answer = responder.events(events -> {
            events.send("delta", Map.of("text", "Hel"));
            events.send("done", Map.of("text", "Hello"));
        });

        assertThat(answer.getHeaders().getContentType()).isEqualTo(MediaType.TEXT_EVENT_STREAM);
        assertThat(answer.getHeaders().getFirst(HttpHeaders.CACHE_CONTROL)).isEqualTo("no-cache");
        assertThat(answer.getHeaders().getFirst("X-Accel-Buffering")).isEqualTo("no");
        assertThat(written(answer)).isEqualTo("event: delta\ndata: {\"text\":\"Hel\"}\n\nevent: done\ndata: {\"text\":\"Hello\"}\n\n");
    }

    @Test
    void anAiFailureAfterTheStreamStartedIsAnErrorEvent() throws Exception {
        ResponseEntity<StreamingResponseBody> answer = responder.events(events -> {
            events.send("delta", Map.of("text", "Hel"));
            throw new AiUnavailableException();
        });

        assertThat(written(answer)).endsWith("event: error\ndata: {\"message\":\"AI help is temporarily unavailable.\"}\n\n");
    }

    private static String written(ResponseEntity<StreamingResponseBody> answer) throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        answer.getBody().writeTo(output);
        return output.toString(StandardCharsets.UTF_8);
    }
}
