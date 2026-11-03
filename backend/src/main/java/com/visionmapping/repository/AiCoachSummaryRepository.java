package com.visionmapping.repository;

import com.visionmapping.entity.AiCoachSummary;
import java.time.LocalDate;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AiCoachSummaryRepository extends JpaRepository<AiCoachSummary, Long> {

    Optional<AiCoachSummary> findFirstByParticipant_IdAndWeekStartOrderByIdDesc(Long participantId, LocalDate weekStart);

    Optional<AiCoachSummary> findFirstByCohort_IdAndScopeAndWeekStartOrderByIdDesc(Long cohortId, String scope, LocalDate weekStart);
}
