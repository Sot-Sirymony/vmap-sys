package com.visionmapping.repository;

import com.visionmapping.entity.SelfAssessmentSnapshot;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SelfAssessmentSnapshotRepository extends JpaRepository<SelfAssessmentSnapshot, Long> {

    /** Newest first; the dream's saves are few, so the whole list is read. */
    List<SelfAssessmentSnapshot> findByUser_IdAndDream_IdOrderByTakenOnDesc(Long userId, Long dreamId);

    Optional<SelfAssessmentSnapshot> findByUser_IdAndDream_IdAndTakenOn(Long userId, Long dreamId, LocalDate takenOn);

    List<SelfAssessmentSnapshot> findByUser_IdOrderByTakenOnAsc(Long userId);
}
