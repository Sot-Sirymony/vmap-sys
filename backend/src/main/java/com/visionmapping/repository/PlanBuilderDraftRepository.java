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

    /**
     * V11 (PB-NFR-03): counts one AI request against the live draft for a
     * dream, only while it is under the limit. 0 rows when there is no live
     * draft or the limit is used.
     */
    @Modifying
    @Query("""
            update PlanBuilderDraft draft set draft.aiRequests = draft.aiRequests + 1
            where draft.user.id = :userId and draft.dream.id = :dreamId and draft.expiresAt > :now and draft.aiRequests < :limit
            """)
    int useOneRequest(@Param("userId") Long userId, @Param("dreamId") Long dreamId, @Param("now") Instant now, @Param("limit") int limit);
}
