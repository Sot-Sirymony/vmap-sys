package com.visionmapping.controller;

import com.visionmapping.dto.request.CommunicationHelpRequest;
import com.visionmapping.dto.response.CommunicationHelpResponses.CommunicationHelpAnswer;
import com.visionmapping.service.ai.CommunicationHelpService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import com.visionmapping.web.ApiPaths;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * V15 Phase 3: the AI Communication helper (AH-FR-43 to AH-FR-67), one endpoint
 * with a mode. The helpers that need no AI are in {@link CommunicationSupportController}.
 */
@RestController
@RequestMapping(value = ApiPaths.V1 + "/ai/communication", produces = MediaType.APPLICATION_JSON_VALUE)
@Validated
@RequiredArgsConstructor
@Tag(name = "AI communication help", description = "AI help with a message to a partner; nothing is saved or sent")
public class CommunicationHelpController {

    private final CommunicationHelpService helpService;

    /** One AI helper. Nothing is saved or sent. */
    @Operation(summary = "Ask the AI for help with a message")
    @PostMapping(value = "/help", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<CommunicationHelpAnswer> help(@Valid @RequestBody CommunicationHelpRequest request,
                                                        @RequestParam(defaultValue = "en") @Pattern(regexp = "en|km") String language) {
        return ResponseEntity.ok(helpService.help(request, language));
    }
}
