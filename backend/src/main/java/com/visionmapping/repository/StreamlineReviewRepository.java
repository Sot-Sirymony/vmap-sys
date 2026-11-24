package com.visionmapping.repository;

import com.visionmapping.entity.StreamlineReview;
import com.visionmapping.entity.enums.StreamlineStatus;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** V27: Streamline reviews. Every query is scoped to the user (BR-27-1). */
public interface StreamlineReviewRepository extends JpaRepository<StreamlineReview, Long> {

    List<StreamlineReview> findByUser_IdOrderByStartedAtDescIdDesc(Long userId);

    Optional<StreamlineReview> findByIdAndUser_Id(Long id, Long userId);

    boolean existsByGoal_IdAndUser_IdAndStatus(Long goalId, Long userId, StreamlineStatus status);

    boolean existsByStep_IdAndUser_IdAndStatus(Long stepId, Long userId, StreamlineStatus status);

    long countByUser_IdAndStatus(Long userId, StreamlineStatus status);

    @Query("select r.code from StreamlineReview r where r.user.id = :userId")
    List<String> findCodesByUserId(@Param("userId") Long userId);

    /** The latest completed review of a goal or a step, for the summary chip. */
    Optional<StreamlineReview> findFirstByGoal_IdAndUser_IdAndStatusOrderByCompletedAtDesc(Long goalId, Long userId, StreamlineStatus status);

    Optional<StreamlineReview> findFirstByStep_IdAndUser_IdAndStatusOrderByCompletedAtDesc(Long stepId, Long userId, StreamlineStatus status);
}
