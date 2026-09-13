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

    @GetMapping
    public WorkStyleProfileResponse get() {
        return service.getMyProfile();
    }

    @PutMapping
    public WorkStyleProfileResponse submit(@Valid @RequestBody WorkStyleAssessmentRequest request) {
        return service.submitAssessment(request);
    }
}
