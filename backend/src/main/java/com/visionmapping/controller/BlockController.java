package com.visionmapping.controller;

import com.visionmapping.dto.request.MarkBlockedRequest;
import com.visionmapping.dto.response.MarkBlockedResponse;
import com.visionmapping.service.BlockMarkingService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.visionmapping.web.ApiPaths;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;

/** V28 (BA-FR-11, BA-FR-12): Mark as blocked from the Vision Map plan assistant. No AI. */
@RestController
@RequestMapping(value = ApiPaths.V1 + "/blocks", produces = MediaType.APPLICATION_JSON_VALUE)
@Validated
@RequiredArgsConstructor
@Tag(name = "Blocks", description = "Marking work as blocked")
public class BlockController {

    private final BlockMarkingService service;

    @Operation(summary = "Mark a step or task as blocked")
    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<MarkBlockedResponse> markBlocked(@Valid @RequestBody MarkBlockedRequest request) {
        return ResponseEntity.ok(service.markBlocked(request));
    }
}
