package com.visionmapping.service.ai;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * BRD 12 COV-FR-28: a local HTTP server that answers like the Claude API, so
 * the client can be tested without any outside call (COV-NFR-02). Each test
 * sets the next answer; every request body is kept for checks.
 */
public final class ClaudeStubServer implements AutoCloseable {

    /** One canned answer: status, content type, body, and an optional delay before answering. */
    public record Answer(int status, String contentType, String body, long delayMillis) {
        public static Answer json(int status, String body) {
            return new Answer(status, "application/json", body, 0);
        }

        public static Answer sse(String body) {
            return new Answer(200, "text/event-stream", body, 0);
        }

        public static Answer slow(long delayMillis) {
            return new Answer(200, "application/json", "{}", delayMillis);
        }
    }

    public record Request(String method, String path, String body, String apiKey) {
    }

    private final HttpServer server;
    private final List<Request> requests = new CopyOnWriteArrayList<>();
    private volatile Answer answer = Answer.json(500, "{}");

    public ClaudeStubServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        server.createContext("/", exchange -> {
            String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            requests.add(new Request(exchange.getRequestMethod(), exchange.getRequestURI().getPath(), body,
                    apiKeyOf(exchange.getRequestHeaders())));
            Answer current = answer;
            try {
                if (current.delayMillis() > 0) {
                    Thread.sleep(current.delayMillis());
                }
                byte[] bytes = current.body().getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().add("Content-Type", current.contentType());
                exchange.sendResponseHeaders(current.status(), bytes.length);
                try (OutputStream out = exchange.getResponseBody()) {
                    out.write(bytes);
                }
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
            } catch (IOException closedByClient) {
                // The client gave up (a timeout test); nothing to answer.
            } finally {
                exchange.close();
            }
        });
        server.start();
    }

    /** The key the client sent, whichever header carries it (x-api-key, or Authorization: Bearer). */
    private static String apiKeyOf(com.sun.net.httpserver.Headers headers) {
        String apiKey = headers.getFirst("x-api-key");
        if (apiKey != null) {
            return apiKey;
        }
        String authorization = headers.getFirst("Authorization");
        return authorization != null && authorization.startsWith("Bearer ") ? authorization.substring(7) : null;
    }

    public String baseUrl() {
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }

    public void answer(Answer next) {
        this.answer = next;
    }

    public List<Request> requests() {
        return requests;
    }

    /** A Messages API reply with one text block. */
    public static String message(String model, String text, String stopReason) {
        return """
                {"id":"msg_1","type":"message","role":"assistant","model":"%s",
                 "content":[{"type":"text","text":%s}],
                 "stop_reason":"%s","stop_sequence":null,
                 "usage":{"input_tokens":120,"output_tokens":45}}
                """.formatted(model, quote(text), stopReason);
    }

    /** A streamed reply in the API's server-sent events format, split into the given pieces. */
    public static String stream(String model, String stopReason, String... pieces) {
        StringBuilder events = new StringBuilder();
        events.append(event("message_start", """
                {"type":"message_start","message":{"id":"msg_1","type":"message","role":"assistant","model":"%s",
                 "content":[],"stop_reason":null,"stop_sequence":null,"usage":{"input_tokens":120,"output_tokens":1}}}
                """.formatted(model)));
        events.append(event("content_block_start", """
                {"type":"content_block_start","index":0,"content_block":{"type":"text","text":""}}
                """));
        for (String piece : pieces) {
            events.append(event("content_block_delta", """
                    {"type":"content_block_delta","index":0,"delta":{"type":"text_delta","text":%s}}
                    """.formatted(quote(piece))));
        }
        events.append(event("content_block_stop", """
                {"type":"content_block_stop","index":0}
                """));
        events.append(event("message_delta", """
                {"type":"message_delta","delta":{"stop_reason":"%s","stop_sequence":null},"usage":{"output_tokens":45}}
                """.formatted(stopReason)));
        events.append(event("message_stop", """
                {"type":"message_stop"}
                """));
        return events.toString();
    }

    public static String error(String type, String message) {
        return """
                {"type":"error","error":{"type":"%s","message":%s}}
                """.formatted(type, quote(message));
    }

    private static String event(String name, String data) {
        return "event: " + name + "\ndata: " + data.replace("\n", "").trim() + "\n\n";
    }

    private static String quote(String text) {
        return "\"" + text.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n") + "\"";
    }

    @Override
    public void close() {
        server.stop(0);
    }
}
