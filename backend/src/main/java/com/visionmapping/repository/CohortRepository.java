package com.visionmapping.repository;

import com.visionmapping.entity.Cohort;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CohortRepository extends JpaRepository<Cohort, Long> {

    Optional<Cohort> findByJoinCodeIgnoreCaseAndArchivedFalse(String joinCode);

    boolean existsByJoinCodeIgnoreCase(String joinCode);

    List<Cohort> findByCoach_IdAndArchivedFalseOrderByNameAsc(Long coachId);

    List<Cohort> findAllByOrderByArchivedAscNameAsc();
}
