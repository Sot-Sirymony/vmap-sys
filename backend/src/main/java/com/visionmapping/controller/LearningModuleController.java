package com.visionmapping.controller;

import com.visionmapping.dto.request.ModuleCompletionRequest;
import com.visionmapping.dto.response.LearningModuleDetailResponse;
import com.visionmapping.dto.response.LearningModuleSummaryResponse;
import com.visionmapping.service.LearningModuleService;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/modules")
@RequiredArgsConstructor
public class LearningModuleController {

    private final LearningModuleService service;

    @GetMapping
    public List<LearningModuleSummaryResponse> list(@RequestParam(defaultValue = "en") String language) {
        return service.listModules(language);
    }

    @GetMapping("/{number}")
    public LearningModuleDetailResponse get(@PathVariable Integer number, @RequestParam(defaultValue = "en") String language) {
        return service.getModule(number, language);
    }

    @PutMapping("/{number}/completion")
    public LearningModuleDetailResponse complete(
            @PathVariable Integer number,
            @RequestParam(defaultValue = "en") String language,
            @Valid @RequestBody ModuleCompletionRequest request) {
        return service.completeModule(number, request, language);
    }
}
