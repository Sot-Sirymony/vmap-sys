package com.visionmapping.service;

import static com.visionmapping.service.support.ServiceSupport.findAllForUser;
import static com.visionmapping.service.support.ServiceSupport.requireArchived;

import com.visionmapping.config.CacheConfig;
import com.visionmapping.dto.request.ReviewRequest;
import com.visionmapping.dto.response.ReviewResponse;
import com.visionmapping.entity.Review;
import com.visionmapping.exception.BusinessRuleException;
import com.visionmapping.mapper.VisionMappingMapper;
import com.visionmapping.repository.ReviewRepository;
import com.visionmapping.service.support.EntityLookup;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Daily/weekly/monthly reviews, including the FR-16 diligence checklist that
 * must be answered as a whole or skipped as a whole.
 */
@Service
@Transactional
@RequiredArgsConstructor
public class ReviewService {

    private final EntityLookup lookup;
    private final VisionMappingMapper mapper;
    private final ReviewRepository reviewRepository;

    @Cacheable(CacheConfig.REVIEW_LIST_CACHE)
    @Transactional(readOnly = true)
    public List<ReviewResponse> listReviews(boolean includeArchived) {
        return findAllForUser(reviewRepository, lookup.userId(), includeArchived).stream()
                .map(mapper::toResponse)
                .toList();
    }

    public ReviewResponse createReview(ReviewRequest request) {
        validateDiligenceChecklist(request);
        Review entity = Review.builder()
                .user(lookup.currentUser())
                .reviewType(request.reviewType())
                .reviewDate(request.reviewDate())
                .relatedVisionArea(lookup.optionalVisionArea(request.relatedVisionAreaId()))
                .relatedDream(lookup.optionalDream(request.relatedDreamId()))
                .summary(request.summary())
                .completedTasks(request.completedTasks())
                .delayedTasks(request.delayedTasks())
                .blockedTasks(request.blockedTasks())
                .lessonsLearned(request.lessonsLearned())
                .nextActions(request.nextActions())
                .diligenceClearVision(request.diligenceClearVision())
                .diligenceWorkedPlan(request.diligenceWorkedPlan())
                .diligenceUsedLeverage(request.diligenceUsedLeverage())
                .diligencePriorityFirst(request.diligencePriorityFirst())
                .diligenceSmarterRoute(request.diligenceSmarterRoute())
                .diligenceRightlyPlanned(request.diligenceRightlyPlanned())
                .diligenceRightlyPerformed(request.diligenceRightlyPerformed())
                .diligenceExpeditious(request.diligenceExpeditious())
                .diligenceEfficient(request.diligenceEfficient())
                .diligenceQualityOutcome(request.diligenceQualityOutcome())
                .diligenceScorePercent(computeDiligenceScore(request))
                .diligenceNote(request.diligenceNote())
                .build();
        return mapper.toResponse(reviewRepository.save(entity));
    }

    @Cacheable(CacheConfig.REVIEW_CACHE)
    @Transactional(readOnly = true)
    public ReviewResponse getReview(Long id) {
        return mapper.toResponse(lookup.review(id));
    }

    public ReviewResponse updateReview(Long id, ReviewRequest request) {
        validateDiligenceChecklist(request);
        Review entity = lookup.review(id);
        entity.setReviewType(request.reviewType());
        entity.setReviewDate(request.reviewDate());
        entity.setRelatedVisionArea(lookup.optionalVisionArea(request.relatedVisionAreaId()));
        entity.setRelatedDream(lookup.optionalDream(request.relatedDreamId()));
        entity.setSummary(request.summary());
        entity.setCompletedTasks(request.completedTasks());
        entity.setDelayedTasks(request.delayedTasks());
        entity.setBlockedTasks(request.blockedTasks());
        entity.setLessonsLearned(request.lessonsLearned());
        entity.setNextActions(request.nextActions());
        entity.setDiligenceClearVision(request.diligenceClearVision());
        entity.setDiligenceWorkedPlan(request.diligenceWorkedPlan());
        entity.setDiligenceUsedLeverage(request.diligenceUsedLeverage());
        entity.setDiligencePriorityFirst(request.diligencePriorityFirst());
        entity.setDiligenceSmarterRoute(request.diligenceSmarterRoute());
        entity.setDiligenceRightlyPlanned(request.diligenceRightlyPlanned());
        entity.setDiligenceRightlyPerformed(request.diligenceRightlyPerformed());
        entity.setDiligenceExpeditious(request.diligenceExpeditious());
        entity.setDiligenceEfficient(request.diligenceEfficient());
        entity.setDiligenceQualityOutcome(request.diligenceQualityOutcome());
        entity.setDiligenceScorePercent(computeDiligenceScore(request));
        entity.setDiligenceNote(request.diligenceNote());
        return mapper.toResponse(entity);
    }

    public void archiveReview(Long id) {
        lookup.review(id).setArchived(true);
    }

    public void restoreReview(Long id) {
        lookup.review(id).setArchived(false);
    }

    public void permanentlyDeleteReview(Long id) {
        Review review = lookup.review(id);
        requireArchived(review.isArchived(), "Review");
        reviewRepository.delete(review);
    }

    /**
     * FR-16 / FR-53: the diligence checklist is answered as a whole or
     * skipped as a whole — a half-answered checklist would silently read as
     * "not met" on the unanswered questions. Widened from five checks to
     * ten by FR-53 (BR-42); the rule itself is unchanged.
     */
    private void validateDiligenceChecklist(ReviewRequest request) {
        List<Boolean> answers = diligenceAnswers(request);
        long answered = answers.stream().filter(Objects::nonNull).count();
        if (answered != 0 && answered != answers.size()) {
            throw new BusinessRuleException("Answer every diligence question, or skip the whole checklist.");
        }
    }

    /**
     * FR-53: met-count / 10 * 100 once every check is answered; null while
     * the checklist is skipped (validateDiligenceChecklist already rejected
     * any half-answered state before this runs).
     */
    private Integer computeDiligenceScore(ReviewRequest request) {
        List<Boolean> answers = diligenceAnswers(request);
        if (answers.stream().anyMatch(Objects::isNull)) {
            return null;
        }
        long metCount = answers.stream().filter(Boolean::booleanValue).count();
        return (int) (metCount * 100 / answers.size());
    }

    private List<Boolean> diligenceAnswers(ReviewRequest request) {
        List<Boolean> answers = new ArrayList<>();
        answers.add(request.diligenceClearVision());
        answers.add(request.diligenceWorkedPlan());
        answers.add(request.diligenceUsedLeverage());
        answers.add(request.diligencePriorityFirst());
        answers.add(request.diligenceSmarterRoute());
        answers.add(request.diligenceRightlyPlanned());
        answers.add(request.diligenceRightlyPerformed());
        answers.add(request.diligenceExpeditious());
        answers.add(request.diligenceEfficient());
        answers.add(request.diligenceQualityOutcome());
        return answers;
    }
}
