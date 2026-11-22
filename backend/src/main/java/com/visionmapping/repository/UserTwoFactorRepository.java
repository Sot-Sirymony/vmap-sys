package com.visionmapping.repository;

import com.visionmapping.entity.UserTwoFactor;
import java.time.Instant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

/** V21: two-factor settings, keyed by the account id. */
public interface UserTwoFactorRepository extends JpaRepository<UserTwoFactor, Long> {

    /**
     * TF-FR-13: accepts an authenticator step only if it is later than the last
     * one accepted. 1 for the first caller, 0 for a replay or a race.
     */
    // Does not clear the persistence context: callers (sign-in, "Verify it's you") still have entities to save.
    @Transactional
    @Modifying(flushAutomatically = true)
    @Query("update UserTwoFactor t set t.lastTotpStep = :step where t.userId = :userId"
            + " and (t.lastTotpStep is null or t.lastTotpStep < :step)")
    int acceptStep(@Param("userId") Long userId, @Param("step") long step);

    /** TF-FR-03: a pending authenticator on settings already in use is forgotten after 15 minutes. */
    @Transactional
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update UserTwoFactor t set t.pendingTotpSecret = null, t.pendingCreatedAt = null"
            + " where t.enabledAt is not null and t.pendingCreatedAt < :before")
    int clearPendingBefore(@Param("before") Instant before);

    /** TF-FR-03: unfinished setups older than a moment are discarded by the daily clean-up. */
    @Transactional
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("delete from UserTwoFactor t where t.enabledAt is null and t.createdAt < :before")
    int deleteUnfinishedBefore(@Param("before") Instant before);
}
