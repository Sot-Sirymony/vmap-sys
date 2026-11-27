package com.visionmapping.controller;

import com.visionmapping.dto.request.AiChatRequest;
import com.visionmapping.dto.request.AiConsentRequest;
import com.visionmapping.dto.request.AiRatingRequest;
import com.visionmapping.dto.request.PlanAssistantRequest;
import com.visionmapping.dto.response.AiChatTurnResponse;
import com.visionmapping.dto.response.AiConversationResponse;
import com.visionmapping.dto.response.AiMessageResponse;
import com.visionmapping.dto.response.AiStatusResponse;
import com.visionmapping.service.ai.AiAccessService;
import com.visionmapping.service.ai.DreamCoachService;
import com.visionmapping.web.StreamingResponder;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import java.util.List;
import java.util.Map;
import com.visionmapping.web.ApiPaths;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

/**
 * V9 Phase 3: AI consent and the Dream Coach chat. The coaches' crisis alerts
 * are in {@link CoachAiAlertController} (V33 D-33-1: one root per controller).
 */
@RestController
@RequestMapping(value = ApiPaths.V1 + "/ai", produces = MediaType.APPLICATION_JSON_VALUE)
@Validated
@RequiredArgsConstructor
@Tag(name = "AI and Dream Coach", description = "AI consent, status and the Dream Coach chat")
public class AiController {

    private final AiAccessService accessService;
    private final DreamCoachService dreamCoachService;
    private final StreamingResponder streamingResponder;

    @Operation(summary = "Get my AI status: consent, limits and whether AI is on")
    @GetMapping("/status")
    public ResponseEntity<AiStatusResponse> status() {
        return ResponseEntity.ok(accessService.status());
    }

    @Operation(summary = "Give or withdraw consent to AI help")
    @PutMapping(value = "/consent", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<AiStatusResponse> consent(@Valid @RequestBody AiConsentRequest request) {
        return ResponseEntity.ok(accessService.setConsent(request.consent()));
    }

    /** V11 (PB-FR-50): with dreamId and section, only the plan assistant's conversations for that section. V15: a PAGE: section lists the page assistant's. */
    @Operation(summary = "List my AI conversations")
    @GetMapping("/conversations")
    public ResponseEntity<List<AiConversationResponse>> conversations(
            @RequestParam(required = false) @Positive Long dreamId,
            @RequestParam(required = false) @Pattern(regexp = PlanAssistantRequest.SECTIONS + "|PAGE:[A-Z_]{1,20}(:\\d{1,12})?") String section) {
        return ResponseEntity.ok(dreamCoachService.listConversations(dreamId, section));
    }

    @Operation(summary = "Get one AI conversation with its messages")
    @GetMapping("/conversations/{id}")
    public ResponseEntity<AiConversationResponse> conversation(@PathVariable @Positive Long id) {
        return ResponseEntity.ok(dreamCoachService.conversation(id));
    }

    /** AI-FR-25: a participant may clear their whole Dream Coach history at once. */
    @Operation(summary = "Delete all my AI conversations")
    @DeleteMapping("/conversations")
    public ResponseEntity<Void> deleteAll() {
        dreamCoachService.deleteAll();
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Delete one AI conversation")
    @DeleteMapping("/conversations/{id}")
    public ResponseEntity<Void> delete(@PathVariable @Positive Long id) {
        dreamCoachService.delete(id);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Send a message to the Dream Coach")
    @PostMapping(value = "/chat", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<AiChatTurnResponse> chat(@Valid @RequestBody AiChatRequest request) {
        return ResponseEntity.ok(dreamCoachService.send(request));
    }

    /**
     * AI-NFR-01: the same as /chat, as server-sent events: "delta" events carry
     * text as it arrives, then one "done" event carries the saved turn (whose
     * reply text is authoritative), or an "error" event. Problems found before
     * streaming starts, such as missing consent, answer as normal errors.
     */
    @Operation(summary = "Send a message to the Dream Coach and stream the reply as server-sent events")
    @PostMapping(value = "/chat/stream", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public ResponseEntity<StreamingResponseBody> chatStream(@Valid @RequestBody AiChatRequest request) {
        DreamCoachService.Prepared prepared = dreamCoachService.prepare(request);
        return streamingResponder.events(events -> events.send("done",
                dreamCoachService.stream(prepared, text -> events.send("delta", Map.of("text", text)))));
    }

    @Operation(summary = "Rate a Dream Coach reply")
    @PutMapping(value = "/messages/{id}/rating", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<AiMessageResponse> rate(@PathVariable @Positive Long id, @Valid @RequestBody AiRatingRequest request) {
        return ResponseEntity.ok(dreamCoachService.rate(id, request));
    }
}
