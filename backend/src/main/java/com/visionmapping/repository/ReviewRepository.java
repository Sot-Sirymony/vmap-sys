package com.visionmapping.repository;

import com.visionmapping.entity.Review;
import com.visionmapping.entity.enums.ReviewType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ReviewRepository extends JpaRepository<Review, Long>, UserScopedRepository<Review> {

    /** FR-70: when the last review of this type happened. */
    Optional<Review> findFirstByUser_IdAndReviewTypeAndArchivedFalseOrderByReviewDateDesc(Long userId, ReviewType reviewType);

    /** V31 v1 step 3 (DC-FR-50): whether a review of this kind for this dream was done since a time. */
    boolean existsByUser_IdAndRelatedDream_IdAndReviewTypeAndArchivedFalseAndReviewDateGreaterThanEqual(Long userId, Long dreamId,
            ReviewType reviewType, java.time.LocalDateTime since);
}
