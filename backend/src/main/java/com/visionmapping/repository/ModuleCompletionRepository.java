package com.visionmapping.repository;

import com.visionmapping.entity.ModuleCompletion;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ModuleCompletionRepository extends JpaRepository<ModuleCompletion, Long> {

    List<ModuleCompletion> findByUser_Id(Long userId);
}
