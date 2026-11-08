package com.visionmapping.controller;

import com.visionmapping.dto.request.IdealPartnerProfileRequest;
import com.visionmapping.dto.response.IdealPartnerProfileResponse;
import com.visionmapping.service.IdealPartnerProfileService;
import com.visionmapping.service.support.ArchiveFilter;
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
@RequestMapping("/api/ideal-partner-profiles")
@RequiredArgsConstructor
public class IdealPartnerProfileController {

    private final IdealPartnerProfileService service;

    @GetMapping
    public List<IdealPartnerProfileResponse> list(@RequestParam(defaultValue = "false") boolean includeArchived) {
        return service.listProfiles(ArchiveFilter.of(includeArchived));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public IdealPartnerProfileResponse create(@Valid @RequestBody IdealPartnerProfileRequest request) {
        return service.createProfile(request);
    }

    @GetMapping("/{id}")
    public IdealPartnerProfileResponse get(@PathVariable Long id) {
        return service.getProfile(id);
    }

    @PutMapping("/{id}")
    public IdealPartnerProfileResponse update(@PathVariable Long id, @Valid @RequestBody IdealPartnerProfileRequest request) {
        return service.updateProfile(id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        service.archiveProfile(id);
    }

    @PostMapping("/{id}/restore")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void restore(@PathVariable Long id) {
        service.restoreProfile(id);
    }

    @DeleteMapping("/{id}/permanent")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deletePermanently(@PathVariable Long id) {
        service.permanentlyDeleteProfile(id);
    }
}
