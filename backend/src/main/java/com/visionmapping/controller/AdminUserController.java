package com.visionmapping.controller;

import com.visionmapping.dto.request.AdminReasonRequest;
import com.visionmapping.dto.request.AdminRoleChangeRequest;
import com.visionmapping.dto.request.AdminUserCreateRequest;
import com.visionmapping.dto.request.AdminUserDeleteRequest;
import com.visionmapping.dto.request.AdminUserUpdateRequest;
import com.visionmapping.dto.response.AdminUserDetailResponse;
import com.visionmapping.dto.response.AdminUserPageResponse;
import com.visionmapping.service.admin.AdminUserActionsService;
import com.visionmapping.service.admin.AdminUserCommands;
import com.visionmapping.service.admin.AdminUserService;
import com.visionmapping.web.ApiResponses;
import com.visionmapping.web.RequiresStepUp;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
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
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * V23 (BRD 23.0.0, UM-FR-17 to UM-FR-36): the Users list, detail and account
 * actions. Reading needs "View users", changes "Manage users", deleting
 * "Delete users" (D-23-1). Plan data is never shown (D-23-3).
 */
@RestController
@RequestMapping(value = ApiPaths.V1 + "/admin/users", produces = MediaType.APPLICATION_JSON_VALUE)
@Validated
@RequiredArgsConstructor
@Tag(name = "Admin: users", description = "Accounts: list, detail, invite, role, block, delete")
public class AdminUserController {

    private static final String VIEW = "hasAuthority('" + Authorities.VIEW_USERS + "')";
    private static final String MANAGE = "hasAuthority('" + Authorities.MANAGE_USERS + "')";

    private final AdminUserService adminUserService;
    private final AdminUserActionsService actions;
    private final AdminUserCommands commands;

    /** UM-FR-18: the sort is one of the list's named orders, and the size one of its page sizes. */
    @Operation(summary = "List accounts, one page at a time, with counts")
    @GetMapping
    @PreAuthorize(VIEW)
    public ResponseEntity<AdminUserPageResponse> list(@RequestParam(required = false) @Size(max = 120) String q,
                                                      @RequestParam(required = false) @Size(max = 64) String role,
                                                      @RequestParam(required = false) @Size(max = 20) String status,
                                                      @RequestParam(required = false) @Positive Long cohortId,
                                                      @RequestParam(required = false) @Size(max = 20) String twoFactor,
                                                      @RequestParam(required = false) @Size(max = 40) String sort,
                                                      @RequestParam(defaultValue = "0") int page,
                                                      @RequestParam(defaultValue = "25") int size) {
        return ResponseEntity.ok(adminUserService.list(new AdminUserService.Filter(q, role, status, cohortId, twoFactor, sort), page, size));
    }

    @Operation(summary = "Get an account's details")
    @GetMapping("/{id}")
    @PreAuthorize(VIEW)
    public ResponseEntity<AdminUserDetailResponse> detail(@PathVariable @Positive Long id) {
        return ResponseEntity.ok(adminUserService.detail(id));
    }

    @Operation(summary = "Change an account's role")
    @PutMapping(value = "/{id}/role", consumes = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize(MANAGE)
    @RequiresStepUp
    public ResponseEntity<AdminUserDetailResponse> changeRole(@PathVariable @Positive Long id, @Valid @RequestBody AdminRoleChangeRequest request) {
        return ResponseEntity.ok(commands.changeRole(id, request));
    }

    @Operation(summary = "Block an account")
    @PostMapping(value = "/{id}/block", consumes = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize(MANAGE)
    @RequiresStepUp
    public ResponseEntity<AdminUserDetailResponse> block(@PathVariable @Positive Long id, @Valid @RequestBody AdminReasonRequest request) {
        return ResponseEntity.ok(commands.block(id, request));
    }

    @Operation(summary = "Unblock an account")
    @PostMapping(value = "/{id}/unblock", consumes = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize(MANAGE)
    @RequiresStepUp
    public ResponseEntity<AdminUserDetailResponse> unblock(@PathVariable @Positive Long id, @Valid @RequestBody AdminReasonRequest request) {
        return ResponseEntity.ok(commands.unblock(id, request));
    }

    @Operation(summary = "Invite a new account")
    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize(MANAGE)
    @RequiresStepUp
    public ResponseEntity<AdminUserDetailResponse> create(@Valid @RequestBody AdminUserCreateRequest request) {
        AdminUserDetailResponse created = commands.create(request);
        return ApiResponses.created(created.account().id(), created);
    }

    @Operation(summary = "Change an account's name, email or cohort")
    @PatchMapping(value = "/{id}", consumes = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize(MANAGE)
    public ResponseEntity<AdminUserDetailResponse> update(@PathVariable @Positive Long id, @Valid @RequestBody AdminUserUpdateRequest request) {
        return ResponseEntity.ok(commands.update(id, request));
    }

    @Operation(summary = "Send the invitation again")
    @PostMapping("/{id}/invitation")
    @PreAuthorize(MANAGE)
    public ResponseEntity<Void> resendInvitation(@PathVariable @Positive Long id) {
        actions.resendInvitation(id);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Send a password reset email")
    @PostMapping("/{id}/password-reset")
    @PreAuthorize(MANAGE)
    public ResponseEntity<Void> sendPasswordReset(@PathVariable @Positive Long id) {
        actions.sendPasswordReset(id);
        return ResponseEntity.noContent().build();
    }

    /** UM-FR-33 to UM-FR-36: a blocked account only, with the email typed again. */
    @Operation(summary = "Delete a blocked account permanently")
    @DeleteMapping(value = "/{id}", consumes = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("hasAuthority('" + Authorities.DELETE_USERS + "')")
    @RequiresStepUp
    public ResponseEntity<Void> delete(@PathVariable @Positive Long id, @Valid @RequestBody AdminUserDeleteRequest request) {
        actions.delete(id, request);
        return ResponseEntity.noContent().build();
    }
}
