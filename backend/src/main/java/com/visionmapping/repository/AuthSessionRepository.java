package com.visionmapping.repository;

import com.visionmapping.entity.AuthSession;
import java.time.Instant;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AuthSessionRepository extends JpaRepository<AuthSession, Long> {

    /** Every authenticated request: the session and its user in one query. */
    @Query("select s from AuthSession s join fetch s.user where s.id = :id")
    Optional<AuthSession> findWithUserById(@Param("id") Long id);

    Optional<AuthSession> findByRefreshTokenHash(String refreshTokenHash);

    Optional<AuthSession> findFirstByPreviousTokenHash(String previousTokenHash);

    /** Ends every open session of a user, except the one given (may be null). */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update AuthSession s set s.revokedAt = :now, s.revokedReason = :reason, s.updatedAt = :now "
            + "where s.user.id = :userId and s.revokedAt is null and (:keepId is null or s.id <> :keepId)")
    int revokeAllForUser(@Param("userId") Long userId, @Param("keepId") Long keepId,
                         @Param("reason") String reason, @Param("now") Instant now);

    /** V24 (D-24-3): ends every open session opened with the method, except Admins' (D-24-5). */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update AuthSession s set s.revokedAt = :now, s.revokedReason = :reason, s.updatedAt = :now "
            + "where s.signInMethod = :method and s.revokedAt is null and s.expiresAt > :now "
            + "and s.user.id in (select u.id from AppUser u where u.role <> :admin)")
    int revokeBySignInMethod(@Param("method") com.visionmapping.entity.enums.SignInOrigin method,
                             @Param("admin") String admin, @Param("reason") String reason, @Param("now") Instant now);

    /** V20 (SV-FR-16): a password change or reset closes every verified window of the person. */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update AuthSession s set s.verifiedUntil = null where s.user.id = :userId and s.verifiedUntil is not null")
    int closeVerifiedWindows(@Param("userId") Long userId);

    /** Housekeeping: sessions that ended more than a while ago. */
    @Modifying
    @Query("delete from AuthSession s where s.expiresAt < :before or (s.revokedAt is not null and s.revokedAt < :before)")
    int deleteEndedBefore(@Param("before") Instant before);
}
