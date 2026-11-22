package com.visionmapping.repository;

import com.visionmapping.entity.SignInChallenge;
import java.time.Instant;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

/** V21 (TF-FR-10, TF-FR-11): sign-in challenges. */
public interface SignInChallengeRepository extends JpaRepository<SignInChallenge, Long> {

    Optional<SignInChallenge> findByTokenHash(String tokenHash);

    /** TF-FR-11: completes a challenge once, while it is still open. */
    @Transactional
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update SignInChallenge c set c.completedAt = :now where c.id = :id and c.completedAt is null and c.expiresAt > :now")
    int complete(@Param("id") Long id, @Param("now") Instant now);

    /** Daily clean-up of challenges that ended before a moment. */
    @Transactional
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("delete from SignInChallenge c where c.expiresAt < :before")
    int deleteExpiredBefore(@Param("before") Instant before);
}
