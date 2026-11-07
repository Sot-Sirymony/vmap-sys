package com.visionmapping.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.test.util.ReflectionTestUtils;

/** BRD 12 COV-FR-30: the health check's cache detail (BR-21) and the Dream Coach's event stream. */
class HealthAndEventsTest {

    @SuppressWarnings("unchecked")
    private HealthController health(String cacheType, RedisConnectionFactory factory) {
        ObjectProvider<RedisConnectionFactory> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(factory);
        HealthController controller = new HealthController(provider);
        ReflectionTestUtils.setField(controller, "cacheType", cacheType);
        return controller;
    }

    private static Map<String, String> body(HealthController controller) {
        return controller.health().getBody();
    }

    @Test
    void reportsTheCacheTypeWhenRedisIsNotUsed() {
        assertThat(body(health("none", null))).containsEntry("status", "UP").containsEntry("cache", "none");
    }

    @Test
    void reportsRedisUpWhenItAnswers() {
        RedisConnectionFactory factory = mock(RedisConnectionFactory.class);
        when(factory.getConnection()).thenReturn(mock(RedisConnection.class));
        assertThat(body(health("redis", factory))).containsEntry("cache", "redis:up");
    }

    @Test
    void staysUpWhenRedisIsDownOrMissing() {
        RedisConnectionFactory factory = mock(RedisConnectionFactory.class);
        when(factory.getConnection()).thenThrow(new RedisConnectionFailureException("Connection refused"));
        assertThat(body(health("redis", factory))).containsEntry("status", "UP").containsEntry("cache", "redis:down");
        assertThat(body(health("REDIS", null))).containsEntry("status", "UP").containsEntry("cache", "redis:down");
    }

    @Test
    void writesEachEventAsANamedJsonFrame() {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ServerSentEvents events = new ServerSentEvents(output, new ObjectMapper());
        events.send("text", Map.of("text", "Start small."));
        events.send("done", Map.of("id", 7));
        assertThat(output.toString(StandardCharsets.UTF_8))
                .isEqualTo("event: text\ndata: {\"text\":\"Start small.\"}\n\nevent: done\ndata: {\"id\":7}\n\n");
    }

    @Test
    void stopsWritingQuietlyOnceTheBrowserHasGone() {
        int[] writes = {0};
        OutputStream gone = new OutputStream() {
            @Override
            public void write(int b) throws IOException {
                writes[0]++;
                throw new IOException("Broken pipe");
            }
        };
        ServerSentEvents events = new ServerSentEvents(gone, new ObjectMapper());
        events.send("text", Map.of("text", "a"));
        events.send("text", Map.of("text", "b"));
        assertThat(writes[0]).isEqualTo(1);
    }
}
