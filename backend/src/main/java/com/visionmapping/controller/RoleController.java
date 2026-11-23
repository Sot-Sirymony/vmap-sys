package com.visionmapping.controller;

import com.visionmapping.dto.request.RoleDeleteRequest;
import com.visionmapping.dto.request.RoleRequest;
import com.visionmapping.dto.response.PermissionResponse;
import com.visionmapping.dto.response.RoleResponse;
import com.visionmapping.service.admin.RoleService;
import com.visionmapping.service.stepup.StepUpService;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.lang.Nullable;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * V23 (BRD 23.0.0, UM-FR-08 to UM-FR-16): the permission catalogue and roles.
 * Reading needs "Manage roles" or "View users"; changes need the Admin role
 * ("Manage roles"), a reason and a fresh code.
 */
@RestController
@RequiredArgsConstructor
public class RoleController {

    private final RoleService roleService;

    @GetMapping("/api/admin/permissions")
    public List<PermissionResponse> catalogue() {
        return roleService.catalogue();
    }

    @GetMapping("/api/admin/roles")
    public List<RoleResponse> list() {
        return roleService.list();
    }

    @PostMapping("/api/admin/roles")
    @ResponseStatus(HttpStatus.CREATED)
    public RoleResponse create(@Valid @RequestBody RoleRequest request,
                               @RequestHeader(value = StepUpService.HEADER, required = false) @Nullable String stepUp) {
        return roleService.create(request, stepUp);
    }

    @PutMapping("/api/admin/roles/{key}")
    public RoleResponse update(@PathVariable String key, @Valid @RequestBody RoleRequest request,
                               @RequestHeader(value = StepUpService.HEADER, required = false) @Nullable String stepUp) {
        return roleService.update(key, request, stepUp);
    }

    @DeleteMapping("/api/admin/roles/{key}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable String key, @Valid @RequestBody RoleDeleteRequest request,
                       @RequestHeader(value = StepUpService.HEADER, required = false) @Nullable String stepUp) {
        roleService.delete(key, request, stepUp);
    }
}
