package com.visionmapping.controller;

import com.visionmapping.dto.request.ObstacleHelpRequest;
import com.visionmapping.dto.response.ObstacleHelpResponses.ObstacleHelpAnswer;
import com.visionmapping.service.ai.ObstacleHelpService;
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
 * V15 Phase 2: the AI obstacle helper (AH-FR-31 to AH-FR-33, AH-FR-37, AH-FR-41).
 * The helpers that need no AI are in {@link ObstacleSupportController} and
 * {@link BlockedTaskController} (V33 D-33-1: one root per controller).
 */
@RestController
@RequestMapping(value = ApiPaths.V1 + "/ai/obstacles", produces = MediaType.APPLICATION_JSON_VALUE)
@Validated
@RequiredArgsConstructor
@Tag(name = "AI obstacle help", description = "AI help with an obstacle; nothing is saved")
public class ObstacleHelpController {

    private final ObstacleHelpService helpService;

    /** One AI helper with a mode. Nothing is saved. */
    @Operation(summary = "Ask the AI for help with an obstacle")
    @PostMapping(value = "/help", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ObstacleHelpAnswer> help(@Valid @RequestBody ObstacleHelpRequest request,
                                                   @RequestParam(defaultValue = "en") @Pattern(regexp = "en|km") String language) {
        return ResponseEntity.ok(helpService.help(request, language));
    }
}
