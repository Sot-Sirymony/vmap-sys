package com.visionmapping.repository;

import com.visionmapping.entity.StreamlineItem;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** V27: the steps and tasks in a Streamline review's scope. */
public interface StreamlineItemRepository extends JpaRepository<StreamlineItem, Long> {

    List<StreamlineItem> findByReview_IdOrderByIdAsc(Long reviewId);

    Optional<StreamlineItem> findByIdAndReview_Id(Long id, Long reviewId);

    /** Items this user cut since a date, for the dashboard (cut and brought back this quarter). */
    @Query("select i from StreamlineItem i where i.review.user.id = :userId and i.archivedAt is not null and i.archivedAt >= :since")
    List<StreamlineItem> findArchivedSince(@Param("userId") Long userId, @Param("since") Instant since);
}
