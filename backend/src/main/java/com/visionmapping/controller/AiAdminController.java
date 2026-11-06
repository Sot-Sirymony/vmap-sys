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
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** V9 Phase 9: admin-only AI tools. The service checks the role. */
@RestController
@RequestMapping("/api/admin/ai")
@RequiredArgsConstructor
public class AiAdminController {

    private final AiAdminService service;
    private final AiPlanFeedbackService planFeedback;

    /** V11 (PB-FR-36): plan builder acceptance, edits, ratings and cost per feature. */
    @GetMapping("/plan-metrics")
    public PlanMetrics planMetrics(@RequestParam(defaultValue = "30") int days) {
        return planFeedback.metrics(days);
    }

    @GetMapping("/resources")
    public List<AiApprovedResourceResponse> resources() {
        return service.resources();
    }

    @PostMapping("/resources")
    @ResponseStatus(HttpStatus.CREATED)
    public AiApprovedResourceResponse createResource(@Valid @RequestBody AiApprovedResourceRequest request) {
        return service.createResource(request);
    }

    @PutMapping("/resources/{id}")
    public AiApprovedResourceResponse updateResource(@PathVariable Long id, @Valid @RequestBody AiApprovedResourceRequest request) {
        return service.updateResource(id, request);
    }

    @DeleteMapping("/resources/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteResource(@PathVariable Long id) {
        service.deleteResource(id);
    }

    @PostMapping("/modules/{number}/draft")
    public ModuleContentDraft draftModule(@PathVariable Integer number, @Valid @RequestBody AiModuleDraftRequest request) {
        return service.draftModule(number, request);
    }

    @PostMapping("/translate")
    public Translations translate(@Valid @RequestBody AiTranslateRequest request) {
        return service.translate(request);
    }
}
