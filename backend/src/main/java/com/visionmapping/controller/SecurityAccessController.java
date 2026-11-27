package com.visionmapping.controller;

import com.visionmapping.dto.request.SecurityAccessUpdateRequest;
import com.visionmapping.dto.response.SecurityAccessResponse;
import com.visionmapping.service.admin.SecurityAccessService;
import com.visionmapping.web.RequiresStepUp;
import jakarta.validation.Valid;
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
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;

/** V24 (BRD 24.0.0): the sign-in method switches. Checked by path rule, @PreAuthorize and in the service. */
@RestController
@RequestMapping(value = ApiPaths.V1 + "/admin/security-access", produces = MediaType.APPLICATION_JSON_VALUE)
@Validated
@RequiredArgsConstructor
@Tag(name = "Admin: security access", description = "Which sign-in methods are on, and two-step sign-in for everyone")
public class SecurityAccessController {

    private final SecurityAccessService securityAccessService;

    @Operation(summary = "Get the sign-in method settings")
    @GetMapping
    @PreAuthorize("hasAnyAuthority('" + Authorities.MANAGE_ROLES + "', '" + Authorities.VIEW_SECURITY_LOG + "')")
    public ResponseEntity<SecurityAccessResponse> view() {
        return ResponseEntity.ok(securityAccessService.view());
    }

    @Operation(summary = "Change the sign-in method settings")
    @PutMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("hasAuthority('" + Authorities.MANAGE_ROLES + "')")
    @RequiresStepUp
    public ResponseEntity<SecurityAccessResponse> update(@Valid @RequestBody SecurityAccessUpdateRequest request) {
        return ResponseEntity.ok(securityAccessService.update(request));
    }
}
