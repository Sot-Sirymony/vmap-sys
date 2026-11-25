package com.visionmapping.repository;

import com.visionmapping.entity.StreamlineApplyBatch;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/** V32: batches of applied Streamline suggestions. Every query is scoped to the user. */
public interface StreamlineApplyBatchRepository extends JpaRepository<StreamlineApplyBatch, Long> {

    Optional<StreamlineApplyBatch> findByIdAndReview_IdAndUser_Id(Long id, Long reviewId, Long userId);

    List<StreamlineApplyBatch> findTop10ByReview_IdAndStageAndUser_IdOrderByCreatedAtDescIdDesc(Long reviewId, int stage, Long userId);
}
