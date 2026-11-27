package com.visionmapping.controller;

import com.visionmapping.dto.response.TwoFactorReportRow;
import com.visionmapping.service.twofactor.TwoFactorAdminService;
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
import org.springframework.web.bind.annotation.GetMapping;

/** V21 (TF-FR-36): who has two-step sign-in. The reset is in {@link AdminUserTwoFactorController}. */
@RestController
@RequestMapping(value = ApiPaths.V1 + "/admin/two-factor", produces = MediaType.APPLICATION_JSON_VALUE)
@Validated
@RequiredArgsConstructor
@Tag(name = "Admin: two-step sign-in", description = "Who has two-step sign-in")
public class TwoFactorAdminController {

    private final TwoFactorAdminService twoFactorAdminService;

    @Operation(summary = "List two-step sign-in for every account")
    @GetMapping
    @PreAuthorize("hasAuthority('" + Authorities.RESET_TWO_FACTOR + "')")
    public ResponseEntity<List<TwoFactorReportRow>> report() {
        return ResponseEntity.ok(twoFactorAdminService.report());
    }
}
