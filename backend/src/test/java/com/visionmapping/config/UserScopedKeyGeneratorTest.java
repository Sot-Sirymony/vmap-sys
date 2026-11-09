package com.visionmapping.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.visionmapping.entity.AppUser;
import com.visionmapping.entity.enums.UserRole;
import com.visionmapping.entity.enums.UserStatus;
import com.visionmapping.security.AppUserPrincipal;
import com.visionmapping.service.support.UserCacheEvictor;
import java.lang.reflect.Method;
import java.time.Clock;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * V10 (SEC-10-05): a cache can never hand one user another user's data. Every
 * key names the signed-in user, and with no signed-in user there is no key
 * at all, so the call fails instead of sharing an entry.
 */
class UserScopedKeyGeneratorTest {

    private final UserScopedKeyGenerator generator = new UserScopedKeyGenerator(new UserCacheEvictor(Clock.systemUTC()));
    private final Method method = String.class.getMethods()[0];

    @AfterEach
    void signOut() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void theSameCallForTwoUsersGetsTwoDifferentKeys() {
        signIn(1L);
        Object first = generator.generate(this, method, 42L);
        signIn(2L);
        Object second = generator.generate(this, method, 42L);
        assertThat(first).isNotEqualTo(second);
        assertThat(first.toString()).startsWith("u1:");
        assertThat(second.toString()).startsWith("u2:");
    }

    @Test
    void withNoSignedInUserThereIsNoKey() {
        assertThatThrownBy(() -> generator.generate(this, method, 42L)).isInstanceOf(IllegalStateException.class);
    }

    private static void signIn(Long id) {
        AppUser user = AppUser.builder().id(id).email("u" + id + "@example.com").fullName("U").passwordHash("x")
                .role(UserRole.USER).status(UserStatus.ACTIVE).build();
        AppUserPrincipal principal = new AppUserPrincipal(user);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));
    }
}
