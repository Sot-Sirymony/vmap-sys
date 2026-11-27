package com.visionmapping.controller;

import com.visionmapping.dto.request.ModuleCompletionRequest;
import com.visionmapping.dto.response.LearningModuleDetailResponse;
import com.visionmapping.dto.response.LearningModuleSummaryResponse;
import com.visionmapping.service.LearningModuleService;
import jakarta.validation.Valid;
import java.util.List;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import com.visionmapping.web.ApiPaths;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;

@RestController
@RequestMapping(value = ApiPaths.V1 + "/modules", produces = MediaType.APPLICATION_JSON_VALUE)
@Validated
@RequiredArgsConstructor
@Tag(name = "Learning modules", description = "The program learning modules")
public class LearningModuleController {

    private final LearningModuleService service;

    @Operation(summary = "List modules")
    @GetMapping
    public ResponseEntity<List<LearningModuleSummaryResponse>> list(@RequestParam(defaultValue = "en") String language) {
        return ResponseEntity.ok(service.listModules(language));
    }

    @Operation(summary = "Get a module")
    @GetMapping("/{number}")
    public ResponseEntity<LearningModuleDetailResponse> get(@PathVariable @Positive Integer number, @RequestParam(defaultValue = "en") String language) {
        return ResponseEntity.ok(service.getModule(number, language));
    }

    @Operation(summary = "Mark a module complete")
    @PutMapping(value = "/{number}/completion", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<LearningModuleDetailResponse> complete(
            @PathVariable @Positive Integer number,
            @RequestParam(defaultValue = "en") String language,
            @Valid @RequestBody ModuleCompletionRequest request) {
        return ResponseEntity.ok(service.completeModule(number, request, language));
    }
}
