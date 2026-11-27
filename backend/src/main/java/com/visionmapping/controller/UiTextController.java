package com.visionmapping.controller;

import com.visionmapping.service.UiTextService;
import jakarta.validation.constraints.Pattern;
import java.util.Map;
import com.visionmapping.web.ApiPaths;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

/** FR-20: interface text changes. Reading is public; changing is in {@link UiTextAdminController}. */
@RestController
@RequestMapping(value = ApiPaths.V1 + "/ui-text", produces = MediaType.APPLICATION_JSON_VALUE)
@Validated
@RequiredArgsConstructor
@Tag(name = "Interface text", description = "Interface text changes, needed to draw any page")
public class UiTextController {

    private final UiTextService service;

    @Operation(summary = "Get the interface text changes for a language")
    @GetMapping
    public ResponseEntity<Map<String, String>> overrides(@RequestParam @Pattern(regexp = "[a-z]{2}") String language) {
        return ResponseEntity.ok(service.overridesFor(language));
    }
}
