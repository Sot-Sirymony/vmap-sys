package com.visionmapping.controller;

import com.visionmapping.dto.response.StepUpStatusResponse;
import com.visionmapping.service.stepup.StepUpService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** V20 (SV-FR-34): lets the admin pages warn when verification codes cannot be sent. */
@RestController
@RequestMapping("/api/admin/step-up")
@RequiredArgsConstructor
public class AdminStepUpController {

    private final StepUpService service;

    @GetMapping
    public StepUpStatusResponse status() {
        return service.status();
    }
}
