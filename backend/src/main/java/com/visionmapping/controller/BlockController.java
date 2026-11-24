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

/** V28 (BA-FR-11, BA-FR-12): Mark as blocked from the Vision Map plan assistant. No AI. */
@RestController
@RequestMapping("/api/blocks")
@RequiredArgsConstructor
public class BlockController {

    private final BlockMarkingService service;

    @PostMapping
    public MarkBlockedResponse markBlocked(@Valid @RequestBody MarkBlockedRequest request) {
        return service.markBlocked(request);
    }
}
