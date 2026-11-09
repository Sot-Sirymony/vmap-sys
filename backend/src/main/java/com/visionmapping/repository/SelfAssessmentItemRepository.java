package com.visionmapping.repository;

import com.visionmapping.entity.SelfAssessmentItem;
import com.visionmapping.entity.enums.GapCoverMethod;
import com.visionmapping.entity.enums.Relevance;
import com.visionmapping.entity.enums.StrengthKind;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SelfAssessmentItemRepository
        extends JpaRepository<SelfAssessmentItem, Long>, UserScopedRepository<SelfAssessmentItem> {

    List<SelfAssessmentItem> findByUser_IdAndDream_Id(Long userId, Long dreamId);

    List<SelfAssessmentItem> findByUser_IdAndDream_IdAndArchivedFalse(Long userId, Long dreamId);

    /** BR-62: how many high-relevance weaknesses are being learned at once, across every dream. */
    long countByUser_IdAndArchivedFalseAndKindAndRelevanceAndCoverMethod(
            Long userId, StrengthKind kind, Relevance relevance, GapCoverMethod coverMethod);
}
