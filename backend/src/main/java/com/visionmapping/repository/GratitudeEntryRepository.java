package com.visionmapping.repository;

import com.visionmapping.entity.GratitudeEntry;
import java.time.Instant;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GratitudeEntryRepository extends JpaRepository<GratitudeEntry, Long>, UserScopedRepository<GratitudeEntry> {

    /** FR-59.2: this week's count, and the source list the dashboard trims to "most recent". */
    List<GratitudeEntry> findByUser_IdAndArchivedFalseAndCreatedAtAfterOrderByCreatedAtDesc(Long userId, Instant since);

    List<GratitudeEntry> findByUser_IdAndArchivedFalseOrderByCreatedAtDesc(Long userId);

    /** The dashboard only needs this week's number, not the rows behind it. */
    long countByUser_IdAndArchivedFalseAndCreatedAtAfter(Long userId, Instant since);

    /** The dashboard's "most recent" strip: a limited page instead of the whole history. */
    List<GratitudeEntry> findByUser_IdAndArchivedFalseOrderByCreatedAtDesc(Long userId, Pageable pageable);
}
