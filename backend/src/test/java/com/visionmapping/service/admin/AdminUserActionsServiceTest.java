package com.visionmapping.service.admin;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.visionmapping.dto.request.AdminReasonRequest;
import com.visionmapping.dto.request.AdminRoleChangeRequest;
import com.visionmapping.entity.AppUser;
import com.visionmapping.entity.enums.UserRole;
import com.visionmapping.entity.enums.UserStatus;
import com.visionmapping.exception.StateConflictException;
import com.visionmapping.repository.AppUserRepository;
import com.visionmapping.entity.Role;
import com.visionmapping.repository.CohortRepository;
import com.visionmapping.repository.RoleRepository;
import com.visionmapping.security.Permission;
import com.visionmapping.security.PermissionService;
import com.visionmapping.security.audit.SecurityEventService;
import com.visionmapping.service.AccountService;
import com.visionmapping.service.AuthSessionService;
import com.visionmapping.service.PasswordResetService;
import com.visionmapping.dto.request.AdminUserCreateRequest;
import com.visionmapping.dto.request.AdminUserDeleteRequest;
import com.visionmapping.exception.BusinessRuleException;
import com.visionmapping.service.notice.AccountNotice;
import com.visionmapping.service.stepup.StepUpAction;
import org.mockito.InOrder;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.crypto.password.PasswordEncoder;
import static org.mockito.Mockito.inOrder;
import static org.mockito.ArgumentMatchers.anyLong;
import com.visionmapping.service.notice.AccountNotices;
import com.visionmapping.service.stepup.StepUpService;
import com.visionmapping.service.support.EntityLookup;
import com.visionmapping.service.support.RequestRateLimiter;
import com.visionmapping.service.twofactor.TwoFactorRequirementService;
import java.time.Clock;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * V23 (UM-FR-40, abuse case 16): the last active admin can never be demoted or
 * blocked. A unit test, because the shared test database always holds other admins.
 */
class AdminUserActionsServiceTest {

    private final AppUserRepository users = mock(AppUserRepository.class);
    private final EntityLookup lookup = mock(EntityLookup.class);
    private final StepUpService stepUp = mock(StepUpService.class);
    private final RoleRepository roles = mock(RoleRepository.class);
    private final AccountNotices notices = mock(AccountNotices.class);
    private final AccountService accountService = mock(AccountService.class);
    private final InvitationService invitations = mock(InvitationService.class);
    private final AdminUserActionsService service = new AdminUserActionsService(users, mock(CohortRepository.class), roles,
            PermissionService.withBuiltInRoles(), lookup, stepUp, mock(SecurityEventService.class), mock(AuthSessionService.class),
            notices, mock(TwoFactorRequirementService.class), mock(RequestRateLimiter.class), Clock.systemUTC(),
            invitations, mock(PasswordResetService.class), accountService, mock(PasswordEncoder.class));

    private final AppUser actor = AppUser.builder().id(3L).fullName("Actor").role(UserRole.ADMIN).status(UserStatus.ACTIVE).build();

    @BeforeEach
    void signedInAsAnotherAdmin() {
        when(lookup.currentUser()).thenReturn(actor);
        when(roles.findById("USER")).thenReturn(Optional.of(new Role("USER", "Participant", null, Set.of(Permission.USE_OWN_PLAN))));
    }

    @Test
    void theOnlyOtherActiveAdminCannotBeDemotedOrBlocked() {
        AppUser target = AppUser.builder().id(2L).fullName("Only").role(UserRole.ADMIN).status(UserStatus.ACTIVE).build();
        when(users.findById(2L)).thenReturn(Optional.of(target));
        // The target is the only active admin counted (the actor here stands for a session that is still open).
        when(users.countByRoleAndStatus("ADMIN", UserStatus.ACTIVE)).thenReturn(1L);

        assertThatThrownBy(() -> service.changeRole(2L, new AdminRoleChangeRequest("USER", "No longer runs it", null), null))
                .isInstanceOf(StateConflictException.class).hasMessage("There must be at least one active admin.");
        assertThatThrownBy(() -> service.block(2L, new AdminReasonRequest("No longer runs it"), null))
                .isInstanceOf(StateConflictException.class);
        verify(stepUp, never()).require(any(), any(String.class));
        verify(users, never()).save(any());
    }

    @Test
    void aBlockedAdminCanBeDemotedWhileAnotherAdminIsActive() {
        AppUser blocked = AppUser.builder().id(1L).fullName("Old").role(UserRole.ADMIN).status(UserStatus.DISABLED).build();
        when(users.findById(1L)).thenReturn(Optional.of(blocked));
        when(users.countByRoleAndStatus("ADMIN", UserStatus.ACTIVE)).thenReturn(1L);

        service.changeRole(1L, new AdminRoleChangeRequest("USER", "Left the program", List.of()), null);

        verify(users).save(blocked);
    }

    // ---------- phase 5: delete and Add user ----------

    private AppUser target(UserStatus status) {
        AppUser target = AppUser.builder().id(7L).fullName("Leaving").email("leaving@example.com")
                .role(UserRole.USER).status(status).build();
        when(users.findById(7L)).thenReturn(Optional.of(target));
        return target;
    }

    @Test
    void anActiveAccountOrAWrongEmailIsRefusedBeforeTheCodeIsUsed() {
        target(UserStatus.ACTIVE);
        assertThatThrownBy(() -> service.delete(7L, new AdminUserDeleteRequest("leaving@example.com", "Left the program"), null))
                .isInstanceOf(StateConflictException.class).hasMessage("Block this account before deleting it.");

        target(UserStatus.DISABLED);
        assertThatThrownBy(() -> service.delete(7L, new AdminUserDeleteRequest("someone@example.com", "Left the program"), null))
                .isInstanceOf(BusinessRuleException.class);
        verify(stepUp, never()).require(any(), any(String.class));
        verify(accountService, never()).erase(anyLong());
    }

    @Test
    void deletingABlockedAccountNeedsAFreshCodeThenTellsThePersonThenErases() {
        AppUser leaving = target(UserStatus.DISABLED);

        service.delete(7L, new AdminUserDeleteRequest("Leaving@Example.com ", "Left the program"), "code");

        InOrder order = inOrder(stepUp, notices, accountService);
        order.verify(stepUp).require(StepUpAction.ADMIN_USER_DELETE, "code");
        order.verify(notices).changed(AccountNotice.Kind.ACCOUNT_DELETED_BY_ADMIN, leaving);
        order.verify(accountService).erase(7L);
    }

    @Test
    void onlyDeleteUsersMayDelete() {
        AppUser coach = AppUser.builder().id(4L).fullName("Coach").role(UserRole.COACH).status(UserStatus.ACTIVE).build();
        when(lookup.currentUser()).thenReturn(coach);
        target(UserStatus.DISABLED);

        assertThatThrownBy(() -> service.delete(7L, new AdminUserDeleteRequest("leaving@example.com", "Left the program"), null))
                .isInstanceOf(AccessDeniedException.class);
        verify(accountService, never()).erase(anyLong());
    }

    @Test
    void withoutMailAddUserCreatesNothing() {
        when(invitations.canDeliver()).thenReturn(false);

        assertThatThrownBy(() -> service.create(new AdminUserCreateRequest("New Person", "new@example.com", null, null), null))
                .isInstanceOf(StateConflictException.class).hasMessage(AdminUserActionsService.NO_MAIL);
        verify(users, never()).save(any());
        verify(invitations, never()).issue(any(), any());
    }

    @Test
    void addingSomeoneWithAPrivilegedRoleNeedsAFreshCode() {
        when(invitations.canDeliver()).thenReturn(true);
        when(roles.findById("COACH")).thenReturn(Optional.of(new Role("COACH", "Coach", null,
                Set.of(Permission.USE_OWN_PLAN, Permission.COACH_COHORTS))));
        when(users.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        service.create(new AdminUserCreateRequest("New Coach", "coach@example.com", "coach", null), "code");

        verify(stepUp).require(StepUpAction.ADMIN_ROLE_CHANGE, "code");
        verify(invitations).issue(any(), org.mockito.ArgumentMatchers.eq(actor));
    }
}
