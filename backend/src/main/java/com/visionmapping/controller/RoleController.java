package com.visionmapping.controller;

import com.visionmapping.dto.request.RoleDeleteRequest;
import com.visionmapping.dto.request.RoleRequest;
import com.visionmapping.dto.response.RoleResponse;
import com.visionmapping.service.admin.RoleService;
import com.visionmapping.web.ApiResponses;
import com.visionmapping.web.RequiresStepUp;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
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
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;

/**
 * V23 (BRD 23.0.0, UM-FR-08 to UM-FR-16): roles. Reading needs "Manage roles"
 * or "View users"; changes need "Manage roles", a reason and a fresh code.
 */
@RestController
@RequestMapping(value = ApiPaths.V1 + "/admin/roles", produces = MediaType.APPLICATION_JSON_VALUE)
@Validated
@RequiredArgsConstructor
@Tag(name = "Admin: roles", description = "Roles and the permissions they give")
public class RoleController {

    private final RoleService roleService;

    @Operation(summary = "List roles")
    @GetMapping
    @PreAuthorize("hasAnyAuthority('" + Authorities.MANAGE_ROLES + "', '" + Authorities.VIEW_USERS + "')")
    public ResponseEntity<List<RoleResponse>> list() {
        return ResponseEntity.ok(roleService.list());
    }

    @Operation(summary = "Create a role")
    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("hasAuthority('" + Authorities.MANAGE_ROLES + "')")
    @RequiresStepUp
    public ResponseEntity<RoleResponse> create(@Valid @RequestBody RoleRequest request) {
        RoleResponse created = roleService.create(request);
        return ApiResponses.created(created.key(), created);
    }

    @Operation(summary = "Change a role")
    @PutMapping(value = "/{key}", consumes = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("hasAuthority('" + Authorities.MANAGE_ROLES + "')")
    @RequiresStepUp
    public ResponseEntity<RoleResponse> update(@PathVariable @Size(min = 1, max = 64) String key, @Valid @RequestBody RoleRequest request) {
        return ResponseEntity.ok(roleService.update(key, request));
    }

    @Operation(summary = "Delete a role")
    @DeleteMapping(value = "/{key}", consumes = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("hasAuthority('" + Authorities.MANAGE_ROLES + "')")
    @RequiresStepUp
    public ResponseEntity<Void> delete(@PathVariable @Size(min = 1, max = 64) String key, @Valid @RequestBody RoleDeleteRequest request) {
        roleService.delete(key, request);
        return ResponseEntity.noContent().build();
    }
}
