package com.visionmapping.controller;

import com.visionmapping.dto.response.PermissionResponse;
import com.visionmapping.service.admin.RoleService;
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

/** V23 (UM-FR-08): the permission catalogue. Split from RoleController in V33. */
@RestController
@RequestMapping(value = ApiPaths.V1 + "/admin/permissions", produces = MediaType.APPLICATION_JSON_VALUE)
@Validated
@RequiredArgsConstructor
@Tag(name = "Admin: permissions", description = "The permissions a role can have")
public class PermissionController {

    private final RoleService roleService;

    @Operation(summary = "List every permission")
    @GetMapping
    @PreAuthorize("hasAnyAuthority('" + Authorities.MANAGE_ROLES + "', '" + Authorities.VIEW_USERS + "')")
    public ResponseEntity<List<PermissionResponse>> catalogue() {
        return ResponseEntity.ok(roleService.catalogue());
    }
}
