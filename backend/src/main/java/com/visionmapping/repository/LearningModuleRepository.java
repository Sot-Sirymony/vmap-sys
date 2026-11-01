package com.visionmapping.repository;

import com.visionmapping.entity.LearningModule;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LearningModuleRepository extends JpaRepository<LearningModule, Integer> {

    List<LearningModule> findAllByOrderByPositionAsc();
}
