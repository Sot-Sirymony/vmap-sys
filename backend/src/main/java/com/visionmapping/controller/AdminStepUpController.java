package com.visionmapping.controller;

import com.visionmapping.dto.response.StepUpStatusResponse;
import com.visionmapping.service.stepup.StepUpService;
import com.visionmapping.security.Authorities;
import org.springframework.security.access.prepost.PreAuthorize;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.visionmapping.web.ApiPaths;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;

/** V20 (SV-FR-34): lets the admin pages warn when verification codes cannot be sent. */
@RestController
@RequestMapping(value = ApiPaths.V1 + "/admin/step-up", produces = MediaType.APPLICATION_JSON_VALUE)
@Validated
@RequiredArgsConstructor
@PreAuthorize("hasAnyAuthority('" + Authorities.MANAGE_COHORTS + "', '" + Authorities.MANAGE_AI_SETTINGS + "', '" + Authorities.VIEW_SECURITY_LOG + "')")
@Tag(name = "Admin: Verify it is you", description = "Whether codes can be sent")
public class AdminStepUpController {

    private final StepUpService service;

    @Operation(summary = "Whether 'Verify it is you' codes can be sent")
    @GetMapping
    public ResponseEntity<StepUpStatusResponse> status() {
        return ResponseEntity.ok(service.status());
    }
}
