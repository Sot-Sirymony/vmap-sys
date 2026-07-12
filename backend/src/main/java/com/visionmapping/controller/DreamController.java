package com.visionmapping.controller;

import com.visionmapping.dto.request.DreamRequest;
import com.visionmapping.dto.request.StatusUpdateRequest;
import com.visionmapping.dto.response.ArchiveImpactResponse;
import com.visionmapping.dto.response.DreamResponse;
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
@RequestMapping("/api/dreams")
@RequiredArgsConstructor
public class DreamController {

    private final VisionMappingService service;

    @GetMapping
    public List<DreamResponse> list(@RequestParam(defaultValue = "false") boolean includeArchived) {
        return service.listDreams(includeArchived);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public DreamResponse create(@Valid @RequestBody DreamRequest request) {
        return service.createDream(request);
    }

    @GetMapping("/{id}")
    public DreamResponse get(@PathVariable Long id) {
        return service.getDream(id);
    }

    @PutMapping("/{id}")
    public DreamResponse update(@PathVariable Long id, @Valid @RequestBody DreamRequest request) {
        return service.updateDream(id, request);
    }

    @PatchMapping("/{id}/status")
    public DreamResponse updateStatus(@PathVariable Long id, @Valid @RequestBody StatusUpdateRequest request) {
        return service.updateDreamStatus(id, request.status());
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        service.archiveDream(id);
    }
    @PostMapping("/{id}/restore")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void restore(@PathVariable Long id) {
        service.restoreDream(id);
    }

    @DeleteMapping("/{id}/permanent")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deletePermanently(@PathVariable Long id) {
        service.permanentlyDeleteDream(id);
    }

    @GetMapping("/{id}/archive-impact")
    public ArchiveImpactResponse archiveImpact(@PathVariable Long id) {
        return service.dreamArchiveImpact(id);
    }

}
