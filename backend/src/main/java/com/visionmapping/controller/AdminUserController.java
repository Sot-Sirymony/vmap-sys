package com.visionmapping.controller;

import com.visionmapping.dto.response.AdminUserDetailResponse;
import com.visionmapping.dto.response.AdminUserPageResponse;
import com.visionmapping.dto.request.AdminReasonRequest;
import com.visionmapping.dto.request.AdminRoleChangeRequest;
import com.visionmapping.dto.request.AdminUserCreateRequest;
import com.visionmapping.dto.request.AdminUserDeleteRequest;
import com.visionmapping.dto.request.AdminUserUpdateRequest;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import com.visionmapping.service.admin.AdminUserActionsService;
import com.visionmapping.service.admin.AdminUserService;
import com.visionmapping.service.stepup.StepUpService;
import jakarta.validation.Valid;
import org.springframework.lang.Nullable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** V23 (BRD 23.0.0, UM-FR-17 to UM-FR-19): the Users list and detail. Needs "View users". */
@RestController
@RequestMapping("/api/admin/users")
@RequiredArgsConstructor
public class AdminUserController {

    private final AdminUserService adminUserService;
    private final AdminUserActionsService actions;

    @GetMapping
    public AdminUserPageResponse list(@RequestParam(required = false) String q,
                                      @RequestParam(required = false) String role,
                                      @RequestParam(required = false) String status,
                                      @RequestParam(required = false) Long cohortId,
                                      @RequestParam(required = false) String twoFactor,
                                      @RequestParam(required = false) String sort,
                                      @RequestParam(defaultValue = "0") int page,
                                      @RequestParam(defaultValue = "25") int size) {
        return adminUserService.list(new AdminUserService.Filter(q, role, status, cohortId, twoFactor, sort), page, size);
    }

    @GetMapping("/{id}")
    public AdminUserDetailResponse detail(@PathVariable Long id) {
        return adminUserService.detail(id);
    }

    /** UM-FR-29: needs "Manage users", a reason and a fresh code. Answers the account as it is now. */
    @PutMapping("/{id}/role")
    public AdminUserDetailResponse changeRole(@PathVariable Long id, @Valid @RequestBody AdminRoleChangeRequest request,
                                              @RequestHeader(value = StepUpService.HEADER, required = false) @Nullable String stepUp) {
        actions.changeRole(id, request, stepUp);
        return adminUserService.detail(id);
    }

    /** UM-FR-31. */
    @PostMapping("/{id}/block")
    public AdminUserDetailResponse block(@PathVariable Long id, @Valid @RequestBody AdminReasonRequest request,
                                         @RequestHeader(value = StepUpService.HEADER, required = false) @Nullable String stepUp) {
        actions.block(id, request, stepUp);
        return adminUserService.detail(id);
    }

    /** UM-FR-33. */
    @PostMapping("/{id}/unblock")
    public AdminUserDetailResponse unblock(@PathVariable Long id, @Valid @RequestBody AdminReasonRequest request,
                                           @RequestHeader(value = StepUpService.HEADER, required = false) @Nullable String stepUp) {
        actions.unblock(id, request, stepUp);
        return adminUserService.detail(id);
    }

    /** UM-FR-22: Add user; the person gets an invitation. A privileged role needs a fresh code. */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public AdminUserDetailResponse create(@Valid @RequestBody AdminUserCreateRequest request,
                                          @RequestHeader(value = StepUpService.HEADER, required = false) @Nullable String stepUp) {
        return adminUserService.detail(actions.create(request, stepUp));
    }

    /** UM-FR-27: name and cohort. */
    @PatchMapping("/{id}")
    public AdminUserDetailResponse update(@PathVariable Long id, @Valid @RequestBody AdminUserUpdateRequest request) {
        actions.update(id, request);
        return adminUserService.detail(id);
    }

    /** UM-FR-24: a new invitation link in place of the old one. */
    @PostMapping("/{id}/invitation")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void resendInvitation(@PathVariable Long id) {
        actions.resendInvitation(id);
    }

    /** UM-FR-28: the normal reset email; the viewer never sees the link. */
    @PostMapping("/{id}/password-reset")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void sendPasswordReset(@PathVariable Long id) {
        actions.sendPasswordReset(id);
    }

    /** UM-FR-35 to UM-FR-38: permanent delete of a blocked account. */
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id, @Valid @RequestBody AdminUserDeleteRequest request,
                       @RequestHeader(value = StepUpService.HEADER, required = false) @Nullable String stepUp) {
        actions.delete(id, request, stepUp);
    }
}
