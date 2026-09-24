package com.visionmapping.repository;

import com.visionmapping.entity.VisionStep;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface VisionStepRepository extends JpaRepository<VisionStep, Long>, CodedRepository<VisionStep> {

    List<VisionStep> findByGoal_IdAndUser_Id(Long goalId, Long userId);

    List<VisionStep> findByGoal_IdAndUser_IdAndArchivedFalse(Long goalId, Long userId);
}
