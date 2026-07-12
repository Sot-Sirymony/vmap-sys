package com.visionmapping.controller;

import com.visionmapping.dto.request.StatusUpdateRequest;
import com.visionmapping.dto.request.VisionStepRequest;
import com.visionmapping.dto.response.ArchiveImpactResponse;
import com.visionmapping.dto.response.VisionStepResponse;
import com.visionmapping.service.VisionMappingService;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/steps")
@RequiredArgsConstructor
public class VisionStepController {

    private final VisionMappingService service;

    @GetMapping
    public List<VisionStepResponse> list(@RequestParam(defaultValue = "false") boolean includeArchived) {
        return service.listSteps(includeArchived);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public VisionStepResponse create(@Valid @RequestBody VisionStepRequest request) {
        return service.createStep(request);
    }

    @GetMapping("/{id}")
    public VisionStepResponse get(@PathVariable Long id) {
        return service.getStep(id);
    }

    @PutMapping("/{id}")
    public VisionStepResponse update(@PathVariable Long id, @Valid @RequestBody VisionStepRequest request) {
        return service.updateStep(id, request);
    }

    @PatchMapping("/{id}/status")
    public VisionStepResponse updateStatus(@PathVariable Long id, @Valid @RequestBody StatusUpdateRequest request) {
        return service.updateStepStatus(id, request.status(), request.manualOverride());
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        service.archiveStep(id);
    }
    @PostMapping("/{id}/restore")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void restore(@PathVariable Long id) {
        service.restoreStep(id);
    }

    @DeleteMapping("/{id}/permanent")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deletePermanently(@PathVariable Long id) {
        service.permanentlyDeleteStep(id);
    }

    @GetMapping("/{id}/archive-impact")
    public ArchiveImpactResponse archiveImpact(@PathVariable Long id) {
        return service.stepArchiveImpact(id);
    }

}
