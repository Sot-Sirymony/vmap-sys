package com.visionmapping.repository;

import com.visionmapping.entity.InnerBlocker;
import com.visionmapping.entity.enums.InnerBlockerStatus;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface InnerBlockerRepository extends JpaRepository<InnerBlocker, Long>, UserScopedRepository<InnerBlocker> {

    /** BR-34-3: blockers that count toward the limit of ten. */
    long countByUser_IdAndArchivedFalseAndStatus(Long userId, InnerBlockerStatus status);

    /**
     * §2.2 and §2.1: one page of the user's blockers. A null status means any;
     * a dream id with allDreams also returns the blockers for all dreams.
     */
    @Query("""
            select b from InnerBlocker b
            where b.user.id = :userId
              and b.archived = :archived
              and (:status is null or b.status = :status)
              and (:dreamId is null or b.dream.id = :dreamId or (:allDreams = true and b.dream is null))
            """)
    Page<InnerBlocker> search(@Param("userId") Long userId, @Param("archived") boolean archived,
                              @Param("status") InnerBlockerStatus status, @Param("dreamId") Long dreamId,
                              @Param("allDreams") boolean allDreams, Pageable pageable);

    /** D-34-11: what a coach reads when the participant shares journals. */
    List<InnerBlocker> findByUser_IdAndArchivedFalseOrderByCreatedAtDesc(Long userId);
}
