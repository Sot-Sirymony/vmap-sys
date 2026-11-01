package com.visionmapping.controller;

import com.visionmapping.dto.request.FearEntryRequest;
import com.visionmapping.dto.response.FearEntryResponse;
import com.visionmapping.service.FearEntryService;
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
@RequestMapping("/api/fear-entries")
@RequiredArgsConstructor
public class FearEntryController {

    private final FearEntryService service;

    @GetMapping
    public List<FearEntryResponse> list(@RequestParam(defaultValue = "false") boolean includeArchived) {
        return service.listFearEntries(includeArchived);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public FearEntryResponse create(@Valid @RequestBody FearEntryRequest request) {
        return service.createFearEntry(request);
    }

    @PutMapping("/{id}")
    public FearEntryResponse update(@PathVariable Long id, @Valid @RequestBody FearEntryRequest request) {
        return service.updateFearEntry(id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        service.archiveFearEntry(id);
    }

    @PostMapping("/{id}/restore")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void restore(@PathVariable Long id) {
        service.restoreFearEntry(id);
    }

    @DeleteMapping("/{id}/permanent")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deletePermanently(@PathVariable Long id) {
        service.permanentlyDeleteFearEntry(id);
    }
}
