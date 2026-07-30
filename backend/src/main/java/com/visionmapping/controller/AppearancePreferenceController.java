package com.visionmapping.controller;

import com.visionmapping.dto.request.AppearancePreferencesRequest;
import com.visionmapping.dto.response.AppearancePreferencesResponse;
import com.visionmapping.service.AppearancePreferenceService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * FR-39.6: the authenticated user's appearance preferences. There is no id in
 * either path — a caller can only ever read or write their own row (BR-33),
 * which is enforced by construction rather than by a check that could be
 * forgotten.
 */
@RestController
@RequestMapping("/api/preferences/appearance")
@RequiredArgsConstructor
public class AppearancePreferenceController {

    private final AppearancePreferenceService service;

    @GetMapping
    public AppearancePreferencesResponse get() {
        return service.getMyPreferences();
    }

    /**
     * Partial updates are expected: the Appearance UI saves one control at a
     * time, so an omitted field keeps its stored value.
     */
    @PutMapping
    public AppearancePreferencesResponse update(@Valid @RequestBody AppearancePreferencesRequest request) {
        return service.updateMyPreferences(request);
    }
}
