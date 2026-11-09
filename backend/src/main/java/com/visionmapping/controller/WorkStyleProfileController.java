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

/**
 * FR-49: the authenticated user's work-style profile. No id in either path —
 * a caller can only ever read or write their own row (BR-38), mirroring
 * {@link AppearancePreferenceController}.
 */
@RestController
@RequestMapping("/api/work-style-profile")
@RequiredArgsConstructor
public class WorkStyleProfileController {

    private final WorkStyleProfileService service;
    private final com.visionmapping.service.WorkStyleSupportService supportService;

    @GetMapping
    public WorkStyleProfileResponse get() {
        return service.getMyProfile();
    }

    @PutMapping
    public WorkStyleProfileResponse submit(@Valid @RequestBody WorkStyleAssessmentRequest request) {
        return service.submitAssessment(request);
    }

    /** V15 (AH-FR-76): turns "Let AI helpers use my work style" on or off. */
    @PutMapping("/ai-use")
    public WorkStyleProfileResponse aiUse(@Valid @RequestBody com.visionmapping.dto.request.WorkStyleAiUseRequest request) {
        return service.setAiUse(request.enabled());
    }

    /** V15 (AH-FR-73): partners by style and the complementary styles still missing. No AI. */
    @GetMapping("/partner-balance")
    public com.visionmapping.dto.response.WorkStyleBalanceResponse partnerBalance() {
        return supportService.partnerBalance();
    }
}
