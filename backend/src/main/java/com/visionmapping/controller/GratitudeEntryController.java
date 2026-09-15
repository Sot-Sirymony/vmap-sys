package com.visionmapping.controller;

import com.visionmapping.dto.request.GratitudeEntryRequest;
import com.visionmapping.dto.response.GratitudeEntryResponse;
import com.visionmapping.service.GratitudeEntryService;
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

@RestController
@RequestMapping("/api/gratitude-entries")
@RequiredArgsConstructor
public class GratitudeEntryController {

    private final GratitudeEntryService service;

    @GetMapping
    public List<GratitudeEntryResponse> list(@RequestParam(defaultValue = "false") boolean includeArchived) {
        return service.listGratitudeEntries(includeArchived);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public GratitudeEntryResponse create(@Valid @RequestBody GratitudeEntryRequest request) {
        return service.createGratitudeEntry(request);
    }

    @GetMapping("/{id}")
    public GratitudeEntryResponse get(@PathVariable Long id) {
        return service.getGratitudeEntry(id);
    }

    @PutMapping("/{id}")
    public GratitudeEntryResponse update(@PathVariable Long id, @Valid @RequestBody GratitudeEntryRequest request) {
        return service.updateGratitudeEntry(id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        service.archiveGratitudeEntry(id);
    }

    @PostMapping("/{id}/restore")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void restore(@PathVariable Long id) {
        service.restoreGratitudeEntry(id);
    }

    @DeleteMapping("/{id}/permanent")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deletePermanently(@PathVariable Long id) {
        service.permanentlyDeleteGratitudeEntry(id);
    }
}
