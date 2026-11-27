package com.visionmapping.controller;

import com.visionmapping.dto.response.CommunicationHelpResponses.FollowUpDue;
import com.visionmapping.dto.response.CommunicationHelpResponses.RankedMessage;
import com.visionmapping.service.CommunicationSupportService;
import java.util.List;
import com.visionmapping.web.ApiPaths;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.GetMapping;

/** V15 Phase 3: Communication helpers that need no AI (AH-FR-48, AH-FR-66, AH-NFR-03). Split from CommunicationHelpController in V33. */
@RestController
@RequestMapping(value = ApiPaths.V1 + "/communication-messages", produces = MediaType.APPLICATION_JSON_VALUE)
@Validated
@RequiredArgsConstructor
@Tag(name = "Communication support", description = "Message helpers that need no AI")
public class CommunicationSupportController {

    private final CommunicationSupportService supportService;

    /** AH-FR-48: Sent or Followed Up messages past their follow-up date. */
    @Operation(summary = "List messages whose follow-up date has passed")
    @GetMapping("/follow-up-due")
    public ResponseEntity<List<FollowUpDue>> followUpDue() {
        return ResponseEntity.ok(supportService.followUpDue());
    }

    /** AH-FR-66: Draft messages in the rule order. */
    @Operation(summary = "List draft messages in the order to send them")
    @GetMapping("/draft-priority")
    public ResponseEntity<List<RankedMessage>> draftPriority() {
        return ResponseEntity.ok(supportService.draftPriority());
    }
}
