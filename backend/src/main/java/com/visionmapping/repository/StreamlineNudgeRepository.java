package com.visionmapping.repository;

import com.visionmapping.entity.StreamlineNudge;
import java.time.Instant;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

/** V32 D-32-11: Streamline nudges sent. */
public interface StreamlineNudgeRepository extends JpaRepository<StreamlineNudge, Long> {

    List<StreamlineNudge> findByUser_IdAndSentAtAfterOrderBySentAtDesc(Long userId, Instant since);
}
