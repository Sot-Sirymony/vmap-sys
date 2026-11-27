package com.visionmapping.service.admin;

import com.visionmapping.dto.request.AdminReasonRequest;
import com.visionmapping.dto.request.AdminRoleChangeRequest;
import com.visionmapping.dto.request.AdminUserCreateRequest;
import com.visionmapping.dto.request.AdminUserUpdateRequest;
import com.visionmapping.dto.response.AdminUserDetailResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * V33 (D-33-11): an account action followed by the account's details, as the
 * Users page shows them after each change. It was two calls in the controller.
 */
@Service
@Transactional
@RequiredArgsConstructor
public class AdminUserCommands {

    private final AdminUserService users;
    private final AdminUserActionsService actions;

    public AdminUserDetailResponse changeRole(Long id, AdminRoleChangeRequest request) {
        actions.changeRole(id, request);
        return users.detail(id);
    }

    public AdminUserDetailResponse block(Long id, AdminReasonRequest request) {
        actions.block(id, request);
        return users.detail(id);
    }

    public AdminUserDetailResponse unblock(Long id, AdminReasonRequest request) {
        actions.unblock(id, request);
        return users.detail(id);
    }

    public AdminUserDetailResponse create(AdminUserCreateRequest request) {
        return users.detail(actions.create(request));
    }

    public AdminUserDetailResponse update(Long id, AdminUserUpdateRequest request) {
        actions.update(id, request);
        return users.detail(id);
    }
}
