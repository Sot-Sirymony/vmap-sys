package com.visionmapping.controller;

import com.visionmapping.dto.request.StatusUpdateRequest;
import com.visionmapping.dto.request.VisionAreaRequest;
import com.visionmapping.dto.response.ArchiveImpactResponse;
import com.visionmapping.dto.response.VisionAreaResponse;
import com.visionmapping.service.VisionAreaService;
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
@RequestMapping("/api/vision-areas")
@RequiredArgsConstructor
public class VisionAreaController {

    private final VisionAreaService service;

    @GetMapping
    public List<VisionAreaResponse> list(@RequestParam(defaultValue = "false") boolean includeArchived) {
        return service.listVisionAreas(includeArchived);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public VisionAreaResponse create(@Valid @RequestBody VisionAreaRequest request) {
        return service.createVisionArea(request);
    }

    @GetMapping("/{id}")
    public VisionAreaResponse get(@PathVariable Long id) {
        return service.getVisionArea(id);
    }

    @PutMapping("/{id}")
    public VisionAreaResponse update(@PathVariable Long id, @Valid @RequestBody VisionAreaRequest request) {
        return service.updateVisionArea(id, request);
    }

    @PatchMapping("/{id}/status")
    public VisionAreaResponse updateStatus(@PathVariable Long id, @Valid @RequestBody StatusUpdateRequest request) {
        return service.updateVisionAreaStatus(id, request.status());
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        service.archiveVisionArea(id);
    }
    @PostMapping("/{id}/restore")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void restore(@PathVariable Long id) {
        service.restoreVisionArea(id);
    }

    @DeleteMapping("/{id}/permanent")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deletePermanently(@PathVariable Long id) {
        service.permanentlyDeleteVisionArea(id);
    }

    @GetMapping("/{id}/archive-impact")
    public ArchiveImpactResponse archiveImpact(@PathVariable Long id) {
        return service.visionAreaArchiveImpact(id);
    }

}
