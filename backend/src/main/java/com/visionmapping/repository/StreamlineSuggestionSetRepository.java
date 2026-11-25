package com.visionmapping.repository;

import com.visionmapping.entity.StreamlineSuggestionSet;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface StreamlineSuggestionSetRepository extends JpaRepository<StreamlineSuggestionSet, Long> {

    Optional<StreamlineSuggestionSet> findByReview_IdAndStage(Long reviewId, int stage);

    /** D-32-2: a completed or abandoned review keeps no saved suggestions. */
    @Modifying
    @Query("delete from StreamlineSuggestionSet s where s.review.id = :reviewId")
    int deleteByReviewId(@Param("reviewId") Long reviewId);
}
