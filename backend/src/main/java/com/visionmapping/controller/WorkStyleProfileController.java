package com.visionmapping.controller;

import com.visionmapping.dto.request.WorkStyleAssessmentRequest;
import com.visionmapping.dto.response.WorkStyleProfileResponse;
import com.visionmapping.service.WorkStyleProfileService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.visionmapping.web.ApiPaths;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;

/**
 * FR-49: the authenticated user's work-style profile. No id in either path —
 * a caller can only ever read or write their own row (BR-38), mirroring
 * {@link AppearancePreferenceController}.
 */
@RestController
@RequestMapping(value = ApiPaths.V1 + "/work-style-profile", produces = MediaType.APPLICATION_JSON_VALUE)
@Validated
@RequiredArgsConstructor
@Tag(name = "Work style profile", description = "The user's work style profile")
public class WorkStyleProfileController {

    private final WorkStyleProfileService service;
    private final com.visionmapping.service.WorkStyleSupportService supportService;

    @Operation(summary = "Get my work style profile")
    @GetMapping
    public ResponseEntity<WorkStyleProfileResponse> get() {
        return ResponseEntity.ok(service.getMyProfile());
    }

    @Operation(summary = "Submit the work style assessment")
    @PutMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<WorkStyleProfileResponse> submit(@Valid @RequestBody WorkStyleAssessmentRequest request) {
        return ResponseEntity.ok(service.submitAssessment(request));
    }

    /** V15 (AH-FR-76): turns "Let AI helpers use my work style" on or off. */
    @Operation(summary = "Turn AI use of the work style profile on or off")
    @PutMapping(value = "/ai-use", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<WorkStyleProfileResponse> aiUse(@Valid @RequestBody com.visionmapping.dto.request.WorkStyleAiUseRequest request) {
        return ResponseEntity.ok(service.setAiUse(request.enabled()));
    }

    /** V15 (AH-FR-73): partners by style and the complementary styles still missing. No AI. */
    @Operation(summary = "Get the work style balance with partners")
    @GetMapping("/partner-balance")
    public ResponseEntity<com.visionmapping.dto.response.WorkStyleBalanceResponse> partnerBalance() {
        return ResponseEntity.ok(supportService.partnerBalance());
    }
}
