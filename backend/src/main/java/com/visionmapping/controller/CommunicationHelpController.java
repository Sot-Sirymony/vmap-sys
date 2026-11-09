package com.visionmapping.controller;

import com.visionmapping.dto.request.CommunicationHelpRequest;
import com.visionmapping.dto.response.CommunicationHelpResponses.CommunicationHelpAnswer;
import com.visionmapping.dto.response.CommunicationHelpResponses.FollowUpDue;
import com.visionmapping.dto.response.CommunicationHelpResponses.RankedMessage;
import com.visionmapping.service.CommunicationSupportService;
import com.visionmapping.service.ai.CommunicationHelpService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * V15 Phase 3: the Communication helpers (AH-FR-43 to AH-FR-67). The AI
 * helpers go through one endpoint with a mode; the follow-up list and the
 * draft order need no AI (AH-NFR-03).
 */
@Validated
@RestController
@RequiredArgsConstructor
public class CommunicationHelpController {

    private final CommunicationHelpService helpService;
    private final CommunicationSupportService supportService;

    /** One AI helper. Nothing is saved or sent. */
    @PostMapping("/api/ai/communication/help")
    public CommunicationHelpAnswer help(@Valid @RequestBody CommunicationHelpRequest request,
                                        @RequestParam(defaultValue = "en") @Pattern(regexp = "en|km") String language) {
        return helpService.help(request, language);
    }

    /** AH-FR-48: Sent or Followed Up messages past their follow-up date. No AI. */
    @GetMapping("/api/communication-messages/follow-up-due")
    public List<FollowUpDue> followUpDue() {
        return supportService.followUpDue();
    }

    /** AH-FR-66: Draft messages in the rule order. No AI. */
    @GetMapping("/api/communication-messages/draft-priority")
    public List<RankedMessage> draftPriority() {
        return supportService.draftPriority();
    }
}
