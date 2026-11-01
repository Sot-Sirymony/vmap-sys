package com.visionmapping.repository;

import com.visionmapping.entity.ChainAssessment;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ChainAssessmentRepository extends JpaRepository<ChainAssessment, Long> {

    Optional<ChainAssessment> findByUser_IdAndTakenOn(Long userId, LocalDate takenOn);

    List<ChainAssessment> findByUser_IdOrderByTakenOnAsc(Long userId);
}
