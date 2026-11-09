package com.visionmapping.dto.request;

import com.visionmapping.util.Copies;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.Map;

/**
 * V15 Phase 3 (AH-FR-44 to AH-FR-66): one Communication helper. messageId is
 * the saved message open on screen, if any; form is the unsaved message, as for
 * the page assistant. intent is the one-sentence ask for FILL; tone is the
 * change asked for with TONE. PRIORITISE works on the whole list.
 */
public record CommunicationHelpRequest(
        @NotNull @Pattern(regexp = MODES) String mode,
        Long messageId,
        @Size(max = 60) Map<@Size(max = 60) String, @Size(max = 4000) String> form,
        @Size(max = 300) String intent,
        @Pattern(regexp = TONES) String tone,
        // V15 (AH-FR-51): a partner's reply pasted for REPLY; sent for this request only and never stored.
        @Size(max = 4000) String reply) {

    public static final String MODES = "FILL|CHECK|TONE|OBJECTIONS|BODY|FOLLOW_UP|MEETING_PREP|THANK_YOU|PRIORITISE|ADAPT|REPLY";
    public static final String TONES = "SHORTER|WARMER|FORMAL|KHMER|ENGLISH";

    public CommunicationHelpRequest {
        form = Copies.map(form);
    }

    /** The Phase 3 shape, without a pasted reply. */
    public CommunicationHelpRequest(String mode, Long messageId, Map<String, String> form, String intent, String tone) {
        this(mode, messageId, form, intent, tone, null);
    }

    /** The page assistant request for the same message, so the helper reads exactly what the assistant reads. */
    public PageAssistantRequest pageRequest() {
        return new PageAssistantRequest("COMMUNICATION", "message", messageId, form);
    }

    /** The unsaved form, or an empty map when none was sent. */
    public Map<String, String> formValues() {
        return form == null ? Map.of() : form;
    }
}
