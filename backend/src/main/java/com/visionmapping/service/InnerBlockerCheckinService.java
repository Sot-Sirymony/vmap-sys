package com.visionmapping.service;

import com.visionmapping.dto.request.InnerBlockerCheckinsRequest;
import com.visionmapping.dto.response.InnerBlockerCheckinResponse;
import com.visionmapping.dto.response.InnerBlockerCheckinsResponse;
import com.visionmapping.entity.InnerBlocker;
import com.visionmapping.entity.InnerBlockerCheckin;
import com.visionmapping.entity.Review;
import com.visionmapping.entity.enums.ReviewType;
import com.visionmapping.exception.BusinessRuleException;
import com.visionmapping.exception.ResourceNotFoundException;
import com.visionmapping.repository.InnerBlockerCheckinRepository;
import com.visionmapping.repository.InnerBlockerRepository;
import com.visionmapping.service.support.EntityLookup;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * V34 (D-34-10, BR-34-4): "Did the practice help this week?" saved with a
 * weekly review. The review and every blocker must be the caller's own.
 * Saving replaces this review's answers: a blocker left out keeps none.
 */
@Service
@Transactional
@RequiredArgsConstructor
public class InnerBlockerCheckinService {

    private final EntityLookup lookup;
    private final InnerBlockerRepository blockerRepository;
    private final InnerBlockerCheckinRepository repository;

    @Transactional(readOnly = true)
    public InnerBlockerCheckinsResponse list(Long reviewId) {
        Review review = lookup.review(reviewId);
        return new InnerBlockerCheckinsResponse(repository.findByReview_IdAndUser_Id(review.getId(), lookup.userId()).stream()
                .map(InnerBlockerCheckinService::toResponse)
                .toList());
    }

    public InnerBlockerCheckinsResponse save(Long reviewId, InnerBlockerCheckinsRequest request) {
        Review review = lookup.review(reviewId);
        if (review.getReviewType() != ReviewType.WEEKLY) {
            throw new BusinessRuleException("Inner blocker check-ins belong to weekly reviews only.");
        }
        Set<Long> seen = new HashSet<>();
        for (InnerBlockerCheckinsRequest.Checkin checkin : request.checkins()) {
            if (!seen.add(checkin.innerBlockerId())) {
                throw new BusinessRuleException("Each inner blocker can have one answer per review.");
            }
        }
        Map<Long, InnerBlockerCheckin> existing = new HashMap<>();
        for (InnerBlockerCheckin checkin : repository.findByReview_IdAndUser_Id(review.getId(), lookup.userId())) {
            existing.put(checkin.getInnerBlocker().getId(), checkin);
        }
        for (InnerBlockerCheckinsRequest.Checkin answer : request.checkins()) {
            InnerBlockerCheckin checkin = existing.remove(answer.innerBlockerId());
            if (checkin == null) {
                checkin = InnerBlockerCheckin.builder()
                        .user(lookup.currentUser())
                        .innerBlocker(blocker(answer.innerBlockerId()))
                        .review(review)
                        .build();
            }
            checkin.setAnswer(answer.answer());
            repository.save(checkin);
        }
        repository.deleteAll(existing.values());
        repository.flush();
        return list(reviewId);
    }

    private InnerBlocker blocker(Long id) {
        return blockerRepository.findById(id)
                .filter(entity -> entity.getUser().getId().equals(lookup.userId()))
                .orElseThrow(() -> new ResourceNotFoundException("Inner blocker not found: " + id));
    }

    private static InnerBlockerCheckinResponse toResponse(InnerBlockerCheckin checkin) {
        return new InnerBlockerCheckinResponse(checkin.getInnerBlocker().getId(), checkin.getReview().getId(), checkin.getAnswer());
    }

    /** IB-FR-12: every answer of the user, for the account export. */
    @Transactional(readOnly = true)
    public List<InnerBlockerCheckinResponse> forExport(Long userId) {
        return repository.findByUser_IdOrderByIdAsc(userId).stream().map(InnerBlockerCheckinService::toResponse).toList();
    }
}
