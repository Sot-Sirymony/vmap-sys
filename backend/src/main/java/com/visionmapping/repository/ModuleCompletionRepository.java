package com.visionmapping.repository;

import com.visionmapping.entity.ModuleCompletion;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ModuleCompletionRepository extends JpaRepository<ModuleCompletion, Long> {

    List<ModuleCompletion> findByUser_Id(Long userId);

    Optional<ModuleCompletion> findByUser_IdAndModuleNumber(Long userId, Integer moduleNumber);

    /** V15 baselines (AHBO-01): module completions since a time, across all participants. */
    List<ModuleCompletion> findByCompletedAtGreaterThanEqual(java.time.Instant from);
}
