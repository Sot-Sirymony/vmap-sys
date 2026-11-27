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
import com.visionmapping.web.ApiPaths;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;

/**
 * FR-39.6: the authenticated user's appearance preferences. There is no id in
 * either path — a caller can only ever read or write their own row (BR-33),
 * which is enforced by construction rather than by a check that could be
 * forgotten.
 */
@RestController
@RequestMapping(value = ApiPaths.V1 + "/preferences/appearance", produces = MediaType.APPLICATION_JSON_VALUE)
@Validated
@RequiredArgsConstructor
@Tag(name = "Appearance", description = "My appearance preferences")
public class AppearancePreferenceController {

    private final AppearancePreferenceService service;

    @Operation(summary = "Get my appearance preferences")
    @GetMapping
    public ResponseEntity<AppearancePreferencesResponse> get() {
        return ResponseEntity.ok(service.getMyPreferences());
    }

    /**
     * Partial updates are expected: the Appearance UI saves one control at a
     * time, so an omitted field keeps its stored value.
     */
    @Operation(summary = "Change my appearance preferences")
    @PutMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<AppearancePreferencesResponse> update(@Valid @RequestBody AppearancePreferencesRequest request) {
        return ResponseEntity.ok(service.updateMyPreferences(request));
    }
}
