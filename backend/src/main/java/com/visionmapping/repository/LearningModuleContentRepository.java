package com.visionmapping.repository;

import com.visionmapping.entity.LearningModuleContent;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LearningModuleContentRepository extends JpaRepository<LearningModuleContent, Long> {

    List<LearningModuleContent> findByLanguage(String language);

    Optional<LearningModuleContent> findByModuleNumberAndLanguage(Integer moduleNumber, String language);
}
