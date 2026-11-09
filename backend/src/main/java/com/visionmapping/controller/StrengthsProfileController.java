package com.visionmapping.controller;

import com.visionmapping.dto.response.StrengthsProfileResponse;
import com.visionmapping.service.StrengthsProfileService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** V8.1 FR-84: one dream's Strengths and Gaps profile, and saving it so a retake can show what changed. */
@RestController
@RequestMapping("/api/strengths-profiles")
@RequiredArgsConstructor
public class StrengthsProfileController {

    private final StrengthsProfileService service;

    @GetMapping("/{dreamId}")
    public StrengthsProfileResponse get(@PathVariable Long dreamId) {
        return service.profile(dreamId);
    }

    @PostMapping("/{dreamId}/snapshots")
    public StrengthsProfileResponse save(@PathVariable Long dreamId) {
        return service.saveSnapshot(dreamId);
    }
}
