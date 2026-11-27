package com.visionmapping.controller;

import com.visionmapping.dto.request.CommunicationMessageRequest;
import com.visionmapping.dto.request.StatusUpdateRequest;
import com.visionmapping.dto.response.CommunicationMessageResponse;
import com.visionmapping.entity.enums.CommunicationStatus;
import com.visionmapping.service.CommunicationMessageService;
import com.visionmapping.service.support.ArchiveFilter;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
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
import com.visionmapping.dto.response.PagedResponse;

@RestController
@RequestMapping(value = ApiPaths.V1 + "/communication-messages", produces = MediaType.APPLICATION_JSON_VALUE)
@Validated
@RequiredArgsConstructor
@Tag(name = "Communication messages", description = "Messages to partners")
public class CommunicationMessageController {

    private final CommunicationMessageService service;

    @Operation(summary = "List messages, one page at a time")
    @GetMapping
    public ResponseEntity<PagedResponse<CommunicationMessageResponse>> list(
            @PageableDefault(size = 20, sort = "id", direction = Sort.Direction.DESC) Pageable pageable,
            @RequestParam(defaultValue = "false") boolean includeArchived,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) Long partnerId,
            @RequestParam(required = false) CommunicationStatus status) {
        return ResponseEntity.ok(service.pageCommunicationMessages(pageable, ArchiveFilter.of(includeArchived), search, partnerId, status));
    }

    @Operation(summary = "Create a message")
    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<CommunicationMessageResponse> create(@Valid @RequestBody CommunicationMessageRequest request) {
        CommunicationMessageResponse created = service.createCommunicationMessage(request);
        return ApiResponses.created(created.id(), created);
    }

    @Operation(summary = "Get a message")
    @GetMapping("/{id}")
    public ResponseEntity<CommunicationMessageResponse> get(@PathVariable @Positive Long id) {
        return ResponseEntity.ok(service.getCommunicationMessage(id));
    }

    @Operation(summary = "Update a message")
    @PutMapping(value = "/{id}", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<CommunicationMessageResponse> update(@PathVariable @Positive Long id, @Valid @RequestBody CommunicationMessageRequest request) {
        return ResponseEntity.ok(service.updateCommunicationMessage(id, request));
    }

    @Operation(summary = "Change a message's status")
    @PatchMapping(value = "/{id}/status", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<CommunicationMessageResponse> updateStatus(@PathVariable @Positive Long id, @Valid @RequestBody StatusUpdateRequest<CommunicationStatus> request) {
        return ResponseEntity.ok(service.updateCommunicationStatus(id, request.status()));
    }

    @Operation(summary = "Archive a message")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable @Positive Long id) {
        service.archiveCommunicationMessage(id);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Restore an archived message")
    @PostMapping("/{id}/restore")
    public ResponseEntity<Void> restore(@PathVariable @Positive Long id) {
        service.restoreCommunicationMessage(id);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Delete a message permanently")
    @DeleteMapping("/{id}/permanent")
    public ResponseEntity<Void> deletePermanently(@PathVariable @Positive Long id) {
        service.permanentlyDeleteCommunicationMessage(id);
        return ResponseEntity.noContent().build();
    }
}
