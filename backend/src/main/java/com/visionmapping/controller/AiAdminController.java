package com.visionmapping.controller;

import com.visionmapping.dto.request.AiApprovedResourceRequest;
import com.visionmapping.dto.request.AiModuleDraftRequest;
import com.visionmapping.dto.request.AiTranslateRequest;
import com.visionmapping.dto.response.AiAdminResponses.ModuleContentDraft;
import com.visionmapping.dto.response.AiAdminResponses.Translations;
import com.visionmapping.dto.response.AiApprovedResourceResponse;
import com.visionmapping.dto.response.AiPlanResponses.PlanMetrics;
import com.visionmapping.service.ai.AiAdminService;
import com.visionmapping.service.ai.AiPlanFeedbackService;
import jakarta.validation.Valid;
import java.util.List;
import com.visionmapping.security.Authorities;
import org.springframework.security.access.prepost.PreAuthorize;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import com.visionmapping.web.ApiPaths;
import com.visionmapping.web.ApiResponses;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Positive;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;

/** V9 Phase 9: admin-only AI tools. The service checks the role. */
@RestController
@RequestMapping(value = ApiPaths.V1 + "/admin/ai", produces = MediaType.APPLICATION_JSON_VALUE)
@Validated
@RequiredArgsConstructor
@Tag(name = "Admin: AI", description = "AI metrics, approved resources, module drafts and translations")
public class AiAdminController {

    private final AiAdminService service;
    private final AiPlanFeedbackService planFeedback;
    private final com.visionmapping.service.ai.PlanAccuracyService planAccuracy;

    /** V11 (PB-FR-36): plan builder acceptance, edits, ratings and cost per feature. */
    @Operation(summary = "Get AI plan building metrics")
    @GetMapping("/plan-metrics")
    @PreAuthorize("hasAuthority('" + Authorities.VIEW_AI_REPORTS + "')")
    public ResponseEntity<PlanMetrics> planMetrics(@RequestParam(defaultValue = "30") int days) {
        return ResponseEntity.ok(planFeedback.metrics(days));
    }

    /** V31 (DC-FR-65): how often Build my plan drafts pass the BRD 31 rules. */
    @Operation(summary = "Get AI plan accuracy metrics")
    @GetMapping("/plan-accuracy")
    @PreAuthorize("hasAuthority('" + Authorities.VIEW_AI_REPORTS + "')")
    public ResponseEntity<com.visionmapping.dto.response.AiPlanResponses.PlanAccuracyMetrics> planAccuracy(
            @RequestParam(defaultValue = "30") int days) {
        return ResponseEntity.ok(planAccuracy.metrics(days));
    }

    @Operation(summary = "List approved resources")
    @GetMapping("/resources")
    @PreAuthorize("hasAuthority('" + Authorities.MANAGE_AI_SETTINGS + "')")
    public ResponseEntity<List<AiApprovedResourceResponse>> resources() {
        return ResponseEntity.ok(service.resources());
    }

    @Operation(summary = "Add an approved resource")
    @PostMapping(value = "/resources", consumes = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("hasAuthority('" + Authorities.MANAGE_AI_SETTINGS + "')")
    public ResponseEntity<AiApprovedResourceResponse> createResource(@Valid @RequestBody AiApprovedResourceRequest request) {
        AiApprovedResourceResponse created = service.createResource(request);
        return ApiResponses.created(created.id(), created);
    }

    @Operation(summary = "Change an approved resource")
    @PutMapping(value = "/resources/{id}", consumes = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("hasAuthority('" + Authorities.MANAGE_AI_SETTINGS + "')")
    public ResponseEntity<AiApprovedResourceResponse> updateResource(@PathVariable @Positive Long id, @Valid @RequestBody AiApprovedResourceRequest request) {
        return ResponseEntity.ok(service.updateResource(id, request));
    }

    @Operation(summary = "Delete an approved resource")
    @DeleteMapping("/resources/{id}")
    @PreAuthorize("hasAuthority('" + Authorities.MANAGE_AI_SETTINGS + "')")
    public ResponseEntity<Void> deleteResource(@PathVariable @Positive Long id) {
        service.deleteResource(id);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Draft a module's content with AI")
    @PostMapping(value = "/modules/{number}/draft", consumes = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("hasAuthority('" + Authorities.MANAGE_AI_SETTINGS + "')")
    public ResponseEntity<ModuleContentDraft> draftModule(@PathVariable @Positive Integer number, @Valid @RequestBody AiModuleDraftRequest request) {
        return ResponseEntity.ok(service.draftModule(number, request));
    }

    @Operation(summary = "Translate interface text with AI")
    @PostMapping(value = "/translate", consumes = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("hasAuthority('" + Authorities.MANAGE_AI_SETTINGS + "')")
    public ResponseEntity<Translations> translate(@Valid @RequestBody AiTranslateRequest request) {
        return ResponseEntity.ok(service.translate(request));
    }
}
