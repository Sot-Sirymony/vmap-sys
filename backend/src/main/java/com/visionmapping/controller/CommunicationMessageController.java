package com.visionmapping.controller;

import com.visionmapping.dto.request.CommunicationMessageRequest;
import com.visionmapping.dto.request.StatusUpdateRequest;
import com.visionmapping.dto.response.CommunicationMessageResponse;
import com.visionmapping.service.VisionMappingService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
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
@RequestMapping("/api/communication-messages")
@RequiredArgsConstructor
public class CommunicationMessageController {

    private final VisionMappingService service;

    @GetMapping
    public Page<CommunicationMessageResponse> list(
            @PageableDefault(size = 20, sort = "id", direction = Sort.Direction.DESC) Pageable pageable,
            @RequestParam(defaultValue = "false") boolean includeArchived) {
        return service.listCommunicationMessages(pageable, includeArchived);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CommunicationMessageResponse create(@Valid @RequestBody CommunicationMessageRequest request) {
        return service.createCommunicationMessage(request);
    }

    @GetMapping("/{id}")
    public CommunicationMessageResponse get(@PathVariable Long id) {
        return service.getCommunicationMessage(id);
    }

    @PutMapping("/{id}")
    public CommunicationMessageResponse update(@PathVariable Long id, @Valid @RequestBody CommunicationMessageRequest request) {
        return service.updateCommunicationMessage(id, request);
    }

    @PatchMapping("/{id}/status")
    public CommunicationMessageResponse updateStatus(@PathVariable Long id, @Valid @RequestBody StatusUpdateRequest request) {
        return service.updateCommunicationStatus(id, request.status());
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        service.archiveCommunicationMessage(id);
    }

    @PostMapping("/{id}/restore")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void restore(@PathVariable Long id) {
        service.restoreCommunicationMessage(id);
    }

    @DeleteMapping("/{id}/permanent")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deletePermanently(@PathVariable Long id) {
        service.permanentlyDeleteCommunicationMessage(id);
    }
}
