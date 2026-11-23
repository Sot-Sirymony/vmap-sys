package com.visionmapping.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.visionmapping.entity.AppUser;
import com.visionmapping.entity.enums.UserRole;
import com.visionmapping.entity.enums.UserStatus;
import com.visionmapping.repository.AppUserRepository;
import com.visionmapping.security.audit.SecurityEventService;
import com.visionmapping.security.audit.SecurityEventType;
import com.visionmapping.service.twofactor.TwoFactorRequirementService;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** V23 (UM-FR-41, Q-07): the first admin, once, and only while there is no admin. */
class AdminBootstrapTest {

    private final AppUserRepository users = mock(AppUserRepository.class);
    private final SecurityEventService events = mock(SecurityEventService.class);
    private final TwoFactorRequirementService twoFactor = mock(TwoFactorRequirementService.class);
    private final AppUser person = AppUser.builder().id(7L).email("first@example.com").role(UserRole.USER).status(UserStatus.ACTIVE).build();

    private AdminBootstrap bootstrap(String email) {
        return new AdminBootstrap(users, events, twoFactor, PermissionService.withBuiltInRoles(), email);
    }

    @Test
    void withNoAdminTheNamedAccountBecomesAdminAndItIsLogged() {
        when(users.existsByRoleAndStatus("ADMIN", UserStatus.ACTIVE)).thenReturn(false);
        when(users.findByEmail("first@example.com")).thenReturn(Optional.of(person));

        assertThat(bootstrap(" First@Example.com ").run()).contains(person);

        assertThat(person.getRole()).isEqualTo("ADMIN");
        verify(twoFactor).roleGiven(person);
        verify(events).recordFor(eq(null), eq(SecurityEventType.ADMIN_BOOTSTRAPPED), any(), eq("user"), eq(7L), eq("USER -> ADMIN"));
    }

    @Test
    void whenAnAdminExistsTheSettingDoesNothing() {
        when(users.existsByRoleAndStatus("ADMIN", UserStatus.ACTIVE)).thenReturn(true);

        assertThat(bootstrap("first@example.com").run()).isEmpty();

        verify(users, never()).findByEmail(any());
        verify(users, never()).save(any());
    }

    @Test
    void anUnknownOrBlockedAccountIsNotPromoted() {
        when(users.existsByRoleAndStatus("ADMIN", UserStatus.ACTIVE)).thenReturn(false);
        when(users.findByEmail("first@example.com")).thenReturn(Optional.of(AppUser.builder().id(8L).role(UserRole.USER)
                .status(UserStatus.DISABLED).build()));

        assertThat(bootstrap("first@example.com").run()).isEmpty();
        assertThat(bootstrap("").run()).isEmpty();
        verify(users, never()).save(any());
    }
}
