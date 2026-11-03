package com.visionmapping.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.visionmapping.dto.request.AiChatRequest;
import com.visionmapping.dto.request.AiConsentRequest;
import com.visionmapping.dto.request.AiRatingRequest;
import com.visionmapping.dto.response.AiChatTurnResponse;
import com.visionmapping.dto.response.AiConversationResponse;
import com.visionmapping.dto.response.AiMessageResponse;
import com.visionmapping.dto.response.AiSafetyAlertResponse;
import com.visionmapping.dto.response.AiStatusResponse;
import com.visionmapping.exception.AiUnavailableException;
import com.visionmapping.service.ai.AiAccessService;
import com.visionmapping.service.ai.AiSafetyAlertService;
import com.visionmapping.service.ai.DreamCoachService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

/** V9 Phase 3: AI consent, the Dream Coach chat and crisis alerts for coaches. */
@RestController
@RequiredArgsConstructor
public class AiController {

    private final AiAccessService accessService;
    private final DreamCoachService dreamCoachService;
    private final AiSafetyAlertService alertService;
    private final ObjectMapper objectMapper;

    @GetMapping("/api/ai/status")
    public AiStatusResponse status() {
        return accessService.status();
    }

    @PutMapping("/api/ai/consent")
    public AiStatusResponse consent(@Valid @RequestBody AiConsentRequest request) {
        return accessService.setConsent(request.consent());
    }

    @GetMapping("/api/ai/conversations")
    public List<AiConversationResponse> conversations() {
        return dreamCoachService.conversations();
    }

    @GetMapping("/api/ai/conversations/{id}")
    public AiConversationResponse conversation(@PathVariable Long id) {
        return dreamCoachService.conversation(id);
    }

    @DeleteMapping("/api/ai/conversations/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        dreamCoachService.delete(id);
    }

    @PostMapping("/api/ai/chat")
    public AiChatTurnResponse chat(@Valid @RequestBody AiChatRequest request) {
        return dreamCoachService.send(request);
    }

    /**
     * AI-NFR-01: the same as /chat, as server-sent events: "delta" events carry
     * text as it arrives, then one "done" event carries the saved turn (whose
     * reply text is authoritative), or an "error" event. Problems found before
     * streaming starts, such as missing consent, answer as normal errors.
     */
    @PostMapping(value = "/api/ai/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public ResponseEntity<StreamingResponseBody> chatStream(@Valid @RequestBody AiChatRequest request) {
        DreamCoachService.Prepared prepared = dreamCoachService.prepare(request);
        StreamingResponseBody body = output -> {
            ServerSentEvents events = new ServerSentEvents(output, objectMapper);
            try {
                AiChatTurnResponse turn = dreamCoachService.stream(prepared, text -> events.send("delta", Map.of("text", text)));
                events.send("done", turn);
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

    @PutMapping("/api/ai/messages/{id}/rating")
    public AiMessageResponse rate(@PathVariable Long id, @Valid @RequestBody AiRatingRequest request) {
        return dreamCoachService.rate(id, request);
    }

    @GetMapping("/api/coach/ai-alerts")
    public List<AiSafetyAlertResponse> alerts() {
        return alertService.mine();
    }

    @PostMapping("/api/coach/ai-alerts/{id}/acknowledge")
    public AiSafetyAlertResponse acknowledge(@PathVariable Long id) {
        return alertService.acknowledge(id);
    }
}
