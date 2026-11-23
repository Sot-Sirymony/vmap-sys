package com.visionmapping.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.visionmapping.entity.AppUser;
import com.visionmapping.entity.enums.UserRole;
import java.util.EnumSet;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;

/** V23 (UM-FR-01, UM-FR-03, D-23-2): what each built-in role may do. */
class PermissionServiceTest {

    private final PermissionService permissions = PermissionService.withBuiltInRoles();

    @Test
    void participantsOnlyUseTheirOwnPlan() {
        assertThat(permissions.permissionsOf("USER")).containsExactly(Permission.USE_OWN_PLAN);
    }

    @Test
    void coachesAlsoCoachCohorts() {
        assertThat(permissions.permissionsOf("COACH")).containsExactlyInAnyOrder(Permission.USE_OWN_PLAN, Permission.COACH_COHORTS);
    }

    @Test
    void adminsAlwaysHaveEveryPermission() {
        assertThat(permissions.permissionsOf("ADMIN")).containsExactlyInAnyOrderElementsOf(EnumSet.allOf(Permission.class));
    }

    @Test
    void anUnknownRoleOrNoUserHasNothing() {
        assertThat(permissions.permissionsOf("NOT_A_ROLE")).isEmpty();
        assertThat(permissions.permissionsOf((AppUser) null)).isEmpty();
    }

    @Test
    void requireRefusesWithA403AndPassesWhenHeld() {
        AppUser coach = AppUser.builder().id(1L).role(UserRole.COACH).build();

        assertThat(permissions.require(coach, Permission.COACH_COHORTS, "no")).isSameAs(coach);
        assertThatThrownBy(() -> permissions.require(coach, Permission.MANAGE_COHORTS, "Only admins"))
                .isInstanceOf(AccessDeniedException.class).hasMessage("Only admins");
        assertThat(permissions.hasAny(coach, Permission.VIEW_SECURITY_LOG, Permission.COACH_COHORTS)).isTrue();
    }

    @Test
    void onlyAdministrativePermissionsOpenTheAdminArea() {
        assertThat(Permission.USE_OWN_PLAN.administrative()).isFalse();
        assertThat(Permission.COACH_COHORTS.administrative()).isFalse();
        assertThat(Permission.MANAGE_COHORTS.administrative()).isTrue();
        assertThat(Permission.VIEW_SECURITY_LOG.authority()).isEqualTo("PERM_VIEW_SECURITY_LOG");
    }
}
