package com.visionmapping.controller;

import com.visionmapping.dto.request.GapStepRequest;
import com.visionmapping.dto.request.ReorderRequest;
import com.visionmapping.dto.request.StatusUpdateRequest;
import com.visionmapping.dto.request.VisionStepRequest;
import com.visionmapping.dto.response.ArchiveImpactResponse;
import com.visionmapping.dto.response.GapStepResponse;
import com.visionmapping.dto.response.VisionStepResponse;
import com.visionmapping.entity.enums.WorkStatus;
import com.visionmapping.service.GapStepService;
import com.visionmapping.service.VisionStepService;
import com.visionmapping.service.support.ArchiveFilter;
import com.visionmapping.service.support.ProgressOverride;
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

    private final VisionStepService service;
    private final GapStepService gapStepService;

    @GetMapping
    public List<VisionStepResponse> list(@RequestParam(defaultValue = "false") boolean includeArchived) {
        return service.listSteps(ArchiveFilter.of(includeArchived));
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
    public VisionStepResponse updateStatus(@PathVariable Long id, @Valid @RequestBody StatusUpdateRequest<WorkStatus> request) {
        return service.updateStepStatus(id, request.status(), ProgressOverride.of(request.manualOverride()));
    }

    @PatchMapping("/reorder")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void reorder(@Valid @RequestBody ReorderRequest request) {
        service.reorderSteps(request.parentId(), request.orderedIds());
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id, @RequestParam(defaultValue = "false") boolean removePartner) {
        // V17 BL-FR-21: removePartner also archives the partner a "find help" step created.
        service.archiveStep(id, removePartner);
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

    /** V17 BL-FR-19: turns this step's gap into a "find help" step placed just before it. */
    @PostMapping("/{id}/gap-step")
    @ResponseStatus(HttpStatus.CREATED)
    public GapStepResponse gapStep(@PathVariable Long id, @Valid @RequestBody GapStepRequest request) {
        return gapStepService.createFromStep(id, request);
    }

    @GetMapping("/{id}/archive-impact")
    public ArchiveImpactResponse archiveImpact(@PathVariable Long id) {
        return service.stepArchiveImpact(id);
    }

}
