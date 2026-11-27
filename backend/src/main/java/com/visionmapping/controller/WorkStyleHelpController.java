package com.visionmapping.controller;

import com.visionmapping.dto.request.WorkStyleHelpRequest;
import com.visionmapping.dto.response.WorkStyleHelpResponses.WorkStyleHelpAnswer;
import com.visionmapping.service.ai.WorkStyleHelpService;
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

/** V15 Phase 5: the AI Work Style helpers (AH-FR-70, AH-FR-71, AH-FR-73, AH-FR-75). Nothing is saved. */
@RestController
@RequestMapping(value = ApiPaths.V1 + "/ai/work-style", produces = MediaType.APPLICATION_JSON_VALUE)
@Validated
@RequiredArgsConstructor
@Tag(name = "AI work style help", description = "AI help with the work style profile; nothing is saved")
public class WorkStyleHelpController {

    private final WorkStyleHelpService service;

    @Operation(summary = "Ask the AI for help with my work style")
    @PostMapping(value = "/help", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<WorkStyleHelpAnswer> help(@Valid @RequestBody WorkStyleHelpRequest request,
                                                    @RequestParam(defaultValue = "en") @Pattern(regexp = "en|km") String language) {
        return ResponseEntity.ok(service.help(request, language));
    }
}
