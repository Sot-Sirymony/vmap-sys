package com.visionmapping.controller;

import com.visionmapping.dto.request.AiApiKeyRequest;
import com.visionmapping.dto.request.AiSettingsRequest;
import com.visionmapping.dto.response.AiSettingsResponse;
import com.visionmapping.service.AiSettingsService;
import jakarta.validation.Valid;
import com.visionmapping.web.RequiresStepUp;
import com.visionmapping.security.Authorities;
import org.springframework.security.access.prepost.PreAuthorize;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
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

/** V9 (AI-FR-24): admin-only AI settings. The service checks the role. */
@RestController
@RequestMapping(value = ApiPaths.V1 + "/admin/ai-settings", produces = MediaType.APPLICATION_JSON_VALUE)
@Validated
@RequiredArgsConstructor
@PreAuthorize("hasAuthority('" + Authorities.MANAGE_AI_SETTINGS + "')")
@Tag(name = "Admin: AI settings", description = "The Claude key, model, budget and AI switches")
public class AiSettingsController {

    private final AiSettingsService service;

    @Operation(summary = "Get the AI settings")
    @GetMapping
    public ResponseEntity<AiSettingsResponse> get() {
        return ResponseEntity.ok(service.get());
    }

    @Operation(summary = "Change the AI settings")
    @PutMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<AiSettingsResponse> update(@Valid @RequestBody AiSettingsRequest request) {
        return ResponseEntity.ok(service.update(request));
    }

    @Operation(summary = "Save the Claude API key")
    @PutMapping(value = "/api-key", consumes = MediaType.APPLICATION_JSON_VALUE)
    @RequiresStepUp
    public ResponseEntity<AiSettingsResponse> saveApiKey(@Valid @RequestBody AiApiKeyRequest request) {
        return ResponseEntity.ok(service.saveApiKey(request));
    }

    @Operation(summary = "Remove the Claude API key")
    @DeleteMapping("/api-key")
    @RequiresStepUp
    public ResponseEntity<AiSettingsResponse> removeApiKey() {
        return ResponseEntity.ok(service.removeApiKey());
    }

    @Operation(summary = "Test the Claude connection")
    @PostMapping("/test")
    public ResponseEntity<AiSettingsResponse> test() {
        return ResponseEntity.ok(service.test());
    }
}
