package com.visionmapping.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

/**
 * Writes server-sent events (event name plus one JSON data line) and flushes
 * each one at once. If the browser goes away it stops writing quietly, so the
 * reply still finishes, is saved to the history and counts against the budget.
 */
public final class ServerSentEvents {

    private final OutputStream output;
    private final ObjectMapper objectMapper;
    private boolean open = true;

    public ServerSentEvents(OutputStream output, ObjectMapper objectMapper) {
        this.output = output;
        this.objectMapper = objectMapper;
    }

    public void send(String event, Object data) {
        if (!open) {
            return;
        }
        try {
            String frame = "event: " + event + "\ndata: " + objectMapper.writeValueAsString(data) + "\n\n";
            output.write(frame.getBytes(StandardCharsets.UTF_8));
            output.flush();
        } catch (IOException exception) {
            open = false;
        }
    }
}
