package com.visionmapping.controller;

import com.visionmapping.dto.request.UiTextOverrideRequest;
import com.visionmapping.dto.response.UiTextOverrideResponse;
import com.visionmapping.service.UiTextService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import java.util.List;
import com.visionmapping.web.ApiPaths;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.visionmapping.security.Authorities;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

/** FR-20: changing interface text needs "Manage interface text". Split from UiTextController in V33. */
@RestController
@RequestMapping(value = ApiPaths.V1 + "/admin/ui-text", produces = MediaType.APPLICATION_JSON_VALUE)
@Validated
@RequiredArgsConstructor
@PreAuthorize("hasAuthority('" + Authorities.MANAGE_INTERFACE_TEXT + "')")
@Tag(name = "Admin: interface text", description = "Change the interface text and its translations")
public class UiTextAdminController {

    private final UiTextService service;

    @Operation(summary = "List the interface text changes for a language")
    @GetMapping
    public ResponseEntity<List<UiTextOverrideResponse>> list(@RequestParam @Pattern(regexp = "[a-z]{2}") String language) {
        return ResponseEntity.ok(service.list(language));
    }

    @Operation(summary = "Save an interface text change")
    @PutMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<UiTextOverrideResponse> save(@Valid @RequestBody UiTextOverrideRequest request) {
        return ResponseEntity.ok(service.save(request));
    }

    @Operation(summary = "Put an interface text back to the original")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> reset(@PathVariable @Positive Long id) {
        service.reset(id);
        return ResponseEntity.noContent().build();
    }
}
