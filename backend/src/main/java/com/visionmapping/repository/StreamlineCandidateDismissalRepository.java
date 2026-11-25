package com.visionmapping.repository;

import com.visionmapping.entity.StreamlineCandidateDismissal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/** V32 D-32-12: dismissed Streamline suggestions. */
public interface StreamlineCandidateDismissalRepository extends JpaRepository<StreamlineCandidateDismissal, Long> {

    List<StreamlineCandidateDismissal> findByUser_IdAndDismissedAtAfter(Long userId, Instant since);

    Optional<StreamlineCandidateDismissal> findByUser_IdAndAnchorTypeAndAnchorIdAndRule(Long userId, String anchorType, Long anchorId,
                                                                                        String rule);
}
