package com.visionmapping.repository;

import com.visionmapping.entity.InnerBlockerCheckin;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InnerBlockerCheckinRepository extends JpaRepository<InnerBlockerCheckin, Long> {

    Optional<InnerBlockerCheckin> findByInnerBlocker_IdAndReview_Id(Long innerBlockerId, Long reviewId);

    /** D-34-10: the latest answers first, by the review's date. */
    List<InnerBlockerCheckin> findTop4ByInnerBlocker_IdOrderByReview_ReviewDateDescIdDesc(Long innerBlockerId);

    List<InnerBlockerCheckin> findByReview_IdAndUser_Id(Long reviewId, Long userId);

    List<InnerBlockerCheckin> findByUser_IdOrderByIdAsc(Long userId);
}
