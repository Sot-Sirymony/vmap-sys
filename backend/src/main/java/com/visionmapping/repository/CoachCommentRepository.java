package com.visionmapping.repository;

import com.visionmapping.entity.CoachComment;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CoachCommentRepository extends JpaRepository<CoachComment, Long> {

    List<CoachComment> findByUser_IdOrderByCreatedAtDesc(Long userId);
}
