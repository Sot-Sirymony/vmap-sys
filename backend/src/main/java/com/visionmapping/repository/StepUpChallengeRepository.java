package com.visionmapping.repository;

import com.visionmapping.entity.StepUpChallenge;
import com.visionmapping.service.stepup.StepUpAction;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** V20: "Verify it's you" codes. */
public interface StepUpChallengeRepository extends JpaRepository<StepUpChallenge, Long> {

    Optional<StepUpChallenge> findByIdAndUser_IdAndSession_Id(Long id, Long userId, Long sessionId);

    /** The codes still open for one session and action (normally at most one). */
    List<StepUpChallenge> findBySession_IdAndActionAndUsedAtIsNullOrderByCreatedAtDesc(Long sessionId, StepUpAction action);

    /** SV-FR-14: codes sent to a person since a moment, for the hourly and daily limits. */
    long countByUser_IdAndCreatedAtAfter(Long userId, Instant since);

    /** SV-FR-19: wrong tries across a person's codes since a moment. */
    @Query("select coalesce(sum(c.attempts), 0) from StepUpChallenge c where c.user.id = :userId and c.createdAt > :since")
    long sumAttemptsSince(@Param("userId") Long userId, @Param("since") Instant since);

    /** SV-NFR-07: daily clean-up of codes made more than a week ago. */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("delete from StepUpChallenge c where c.createdAt < :before")
    int deleteCreatedBefore(@Param("before") Instant before);
}
