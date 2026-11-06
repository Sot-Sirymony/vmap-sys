package com.visionmapping.repository;

import com.visionmapping.entity.PlanBuilderDraft;
import java.time.Instant;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PlanBuilderDraftRepository extends JpaRepository<PlanBuilderDraft, Long> {

    Optional<PlanBuilderDraft> findByUser_IdAndDream_Id(Long userId, Long dreamId);

    @Modifying
    @Query("delete from PlanBuilderDraft draft where draft.expiresAt < :now")
    int deleteExpired(@Param("now") Instant now);
}
