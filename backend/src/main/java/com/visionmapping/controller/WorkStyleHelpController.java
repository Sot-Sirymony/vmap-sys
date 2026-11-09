package com.visionmapping.controller;

import com.visionmapping.dto.request.WorkStyleHelpRequest;
import com.visionmapping.dto.response.WorkStyleHelpResponses.WorkStyleHelpAnswer;
import com.visionmapping.service.ai.WorkStyleHelpService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** V15 Phase 5: the AI Work Style helpers (AH-FR-70, AH-FR-71, AH-FR-73, AH-FR-75). Nothing is saved. */
@Validated
@RestController
@RequiredArgsConstructor
public class WorkStyleHelpController {

    private final WorkStyleHelpService service;

    @PostMapping("/api/ai/work-style/help")
    public WorkStyleHelpAnswer help(@Valid @RequestBody WorkStyleHelpRequest request,
                                    @RequestParam(defaultValue = "en") @Pattern(regexp = "en|km") String language) {
        return service.help(request, language);
    }
}
