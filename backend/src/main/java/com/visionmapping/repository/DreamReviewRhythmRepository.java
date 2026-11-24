package com.visionmapping.repository;

import com.visionmapping.entity.DreamReviewRhythm;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DreamReviewRhythmRepository extends JpaRepository<DreamReviewRhythm, Long> {

    Optional<DreamReviewRhythm> findByDream_IdAndUser_Id(Long dreamId, Long userId);

    List<DreamReviewRhythm> findByUser_IdAndDream_ArchivedFalse(Long userId);
}
