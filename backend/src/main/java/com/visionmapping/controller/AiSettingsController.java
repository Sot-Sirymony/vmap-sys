package com.visionmapping.controller;

import com.visionmapping.dto.request.AiApiKeyRequest;
import com.visionmapping.dto.request.AiSettingsRequest;
import com.visionmapping.dto.response.AiSettingsResponse;
import com.visionmapping.service.AiSettingsService;
import jakarta.validation.Valid;
import com.visionmapping.service.stepup.StepUpService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** V9 (AI-FR-24): admin-only AI settings. The service checks the role. */
@RestController
@RequestMapping("/api/admin/ai-settings")
@RequiredArgsConstructor
public class AiSettingsController {

    private final AiSettingsService service;

    @GetMapping
    public AiSettingsResponse get() {
        return service.get();
    }

    @PutMapping
    public AiSettingsResponse update(@Valid @RequestBody AiSettingsRequest request) {
        return service.update(request);
    }

    @PutMapping("/api-key")
    public AiSettingsResponse saveApiKey(@Valid @RequestBody AiApiKeyRequest request,
                                         @RequestHeader(value = StepUpService.HEADER, required = false) String stepUp) {
        return service.saveApiKey(request, stepUp);
    }

    @DeleteMapping("/api-key")
    public AiSettingsResponse removeApiKey(@RequestHeader(value = StepUpService.HEADER, required = false) String stepUp) {
        return service.removeApiKey(stepUp);
    }

    @PostMapping("/test")
    public AiSettingsResponse test() {
        return service.test();
    }
}
