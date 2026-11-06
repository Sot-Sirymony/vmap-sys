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

    /** Housekeeping: sessions that ended more than a while ago. */
    @Modifying
    @Query("delete from AuthSession s where s.expiresAt < :before or (s.revokedAt is not null and s.revokedAt < :before)")
    int deleteEndedBefore(@Param("before") Instant before);
}
