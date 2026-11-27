package com.visionmapping.controller;

import com.visionmapping.dto.request.TwoFactorResetRequest;
import com.visionmapping.service.twofactor.TwoFactorAdminService;
import com.visionmapping.web.RequiresStepUp;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
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
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

/** V21 (TF-FR-28): reset another person's two-step sign-in. Split from TwoFactorAdminController in V33. */
@RestController
@RequestMapping(value = ApiPaths.V1 + "/admin/users", produces = MediaType.APPLICATION_JSON_VALUE)
@Validated
@RequiredArgsConstructor
@Tag(name = "Admin: user two-step sign-in", description = "Reset a person's two-step sign-in")
public class AdminUserTwoFactorController {

    private final TwoFactorAdminService twoFactorAdminService;

    @Operation(summary = "Reset a person's two-step sign-in")
    @PostMapping(value = "/{id}/two-factor/reset", consumes = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("hasAuthority('" + Authorities.RESET_TWO_FACTOR + "')")
    @RequiresStepUp
    public ResponseEntity<Void> reset(@PathVariable @Positive Long id, @Valid @RequestBody TwoFactorResetRequest request) {
        twoFactorAdminService.reset(id, request.reason());
        return ResponseEntity.noContent().build();
    }
}
