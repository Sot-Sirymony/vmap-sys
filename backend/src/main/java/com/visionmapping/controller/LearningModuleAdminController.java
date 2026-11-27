package com.visionmapping.controller;

import com.visionmapping.dto.request.ModuleContentRequest;
import com.visionmapping.dto.request.ModuleOrderRequest;
import com.visionmapping.dto.response.ModuleContentResponse;
import com.visionmapping.service.LearningModuleAdminService;
import jakarta.validation.Valid;
import java.util.List;
import com.visionmapping.security.Authorities;
import org.springframework.security.access.prepost.PreAuthorize;
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
@RequestMapping(value = ApiPaths.V1 + "/admin/modules", produces = MediaType.APPLICATION_JSON_VALUE)
@Validated
@RequiredArgsConstructor
@PreAuthorize("hasAuthority('" + Authorities.MANAGE_MODULE_CONTENT + "')")
@Tag(name = "Admin: modules", description = "The learning modules content")
public class LearningModuleAdminController {

    private final LearningModuleAdminService service;

    @Operation(summary = "List the modules' content")
    @GetMapping
    public ResponseEntity<List<ModuleContentResponse>> list(@RequestParam(defaultValue = "en") String language) {
        return ResponseEntity.ok(service.listContent(language));
    }

    @Operation(summary = "Change a module's content in one language")
    @PutMapping(value = "/{number}/content/{language}", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ModuleContentResponse> update(
            @PathVariable @Positive Integer number,
            @PathVariable String language,
            @Valid @RequestBody ModuleContentRequest request) {
        return ResponseEntity.ok(service.updateContent(number, language, request));
    }

    @Operation(summary = "Reorder the modules")
    @PutMapping(value = "/order", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Void> reorder(@Valid @RequestBody ModuleOrderRequest request) {
        service.reorder(request.orderedNumbers());
        return ResponseEntity.noContent().build();
    }
}
