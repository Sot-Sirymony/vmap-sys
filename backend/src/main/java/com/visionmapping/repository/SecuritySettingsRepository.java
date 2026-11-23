package com.visionmapping.repository;

import com.visionmapping.entity.SecuritySettings;
import com.visionmapping.entity.enums.IdentityProvider;
import com.visionmapping.entity.enums.SignInOrigin;
import com.visionmapping.entity.enums.UserStatus;
import java.time.Instant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * V24 (SA-FR-01, SA-FR-02): the settings row, and the counts the Security
 * access page shows. Every count leaves out the Admin role (D-24-5).
 */
public interface SecuritySettingsRepository extends JpaRepository<SecuritySettings, Long> {

    /** Active non-Admin accounts that have a password. */
    @Query("select count(u) from AppUser u where u.status = :active and u.role <> :admin and u.passwordSet = true")
    long countWithPassword(@Param("active") UserStatus active, @Param("admin") String admin);

    /** Active non-Admin accounts linked to the provider. */
    @Query("select count(u) from AppUser u where u.status = :active and u.role <> :admin and exists "
            + "(select i.id from ExternalIdentity i where i.user = u and i.provider = :provider)")
    long countLinked(@Param("active") UserStatus active, @Param("admin") String admin,
                     @Param("provider") IdentityProvider provider);

    /** Active non-Admin accounts with a password and no link to the provider: password is their only way in. */
    @Query("select count(u) from AppUser u where u.status = :active and u.role <> :admin and u.passwordSet = true "
            + "and not exists (select i.id from ExternalIdentity i where i.user = u and i.provider = :provider)")
    long countPasswordOnly(@Param("active") UserStatus active, @Param("admin") String admin,
                           @Param("provider") IdentityProvider provider);

    /** Active non-Admin accounts with no password and a link to the provider: the provider is their only way in. */
    @Query("select count(u) from AppUser u where u.status = :active and u.role <> :admin and u.passwordSet = false "
            + "and exists (select i.id from ExternalIdentity i where i.user = u and i.provider = :provider)")
    long countProviderOnly(@Param("active") UserStatus active, @Param("admin") String admin,
                           @Param("provider") IdentityProvider provider);

    /** Open non-Admin sessions opened with the method (D-24-3). */
    @Query("select count(s) from AuthSession s where s.signInMethod = :method and s.revokedAt is null "
            + "and s.expiresAt > :now and s.user.role <> :admin")
    long countOpenSessions(@Param("method") SignInOrigin method, @Param("admin") String admin, @Param("now") Instant now);
}
