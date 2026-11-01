package com.visionmapping.controller;

import com.visionmapping.dto.request.ChainAssessmentRequest;
import com.visionmapping.dto.response.ChainAssessmentResponse;
import com.visionmapping.dto.response.ChainAssessmentSummaryResponse;
import com.visionmapping.service.ChainAssessmentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/chain-assessments")
@RequiredArgsConstructor
public class ChainAssessmentController {

    private final ChainAssessmentService service;

    @GetMapping
    public ChainAssessmentSummaryResponse summary() {
        return service.summary();
    }

    @PutMapping
    public ChainAssessmentResponse save(@Valid @RequestBody ChainAssessmentRequest request) {
        return service.save(request);
    }
}
