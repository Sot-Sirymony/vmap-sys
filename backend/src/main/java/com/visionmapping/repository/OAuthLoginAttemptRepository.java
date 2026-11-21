package com.visionmapping.repository;

import com.visionmapping.entity.OAuthLoginAttempt;
import java.time.Instant;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

/** V14 (GA-FR-29): started Google sign-ins. */
public interface OAuthLoginAttemptRepository extends JpaRepository<OAuthLoginAttempt, Long> {

    /** GA-FR-09: the callback finds its attempt by the state's hash. */
    Optional<OAuthLoginAttempt> findByStateHash(String stateHash);

    /**
     * GA-FR-06, GA-SEC-02: uses the attempt once. Answers 1 only for the first
     * caller while it is still open, so two callbacks with the same state
     * cannot both go on.
     */
    @Transactional
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update OAuthLoginAttempt a set a.usedAt = :now where a.id = :id and a.usedAt is null and a.expiresAt > :now")
    int markUsed(@Param("id") Long id, @Param("now") Instant now);

    /** GA-FR-31: daily clean-up of attempts that expired before a moment. */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("delete from OAuthLoginAttempt a where a.expiresAt < :before")
    int deleteExpiredBefore(@Param("before") Instant before);
}
