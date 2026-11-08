package com.visionmapping.service;

import com.visionmapping.dto.request.CoachCommentRequest;
import com.visionmapping.dto.response.CoachCommentResponse;
import com.visionmapping.entity.AppUser;
import com.visionmapping.entity.CoachComment;
import com.visionmapping.entity.Dream;
import com.visionmapping.entity.Goal;
import com.visionmapping.entity.VisionStep;
import com.visionmapping.entity.enums.CoachCommentTarget;
import com.visionmapping.exception.BusinessRuleException;
import com.visionmapping.exception.ResourceNotFoundException;
import com.visionmapping.repository.CoachCommentRepository;
import com.visionmapping.repository.DreamRepository;
import com.visionmapping.repository.GoalRepository;
import com.visionmapping.repository.VisionStepRepository;
import com.visionmapping.service.support.EntityLookup;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** V9 (v0.1 FR-17): coach comments, written by a coach and read by the participant they are for. */
@Service
@Transactional
@RequiredArgsConstructor
public class CoachCommentService {

    private final EntityLookup lookup;
    private final CoachCommentRepository repository;
    private final DreamRepository dreamRepository;
    private final GoalRepository goalRepository;
    private final VisionStepRepository visionStepRepository;
    private final Clock clock;

    /** The signed-in participant's comments, newest first. */
    @Transactional(readOnly = true)
    public List<CoachCommentResponse> mine() {
        return forParticipant(lookup.userId());
    }

    public CoachCommentResponse markRead(Long id) {
        CoachComment comment = repository.findById(id)
                .filter(found -> found.getUser().getId().equals(lookup.userId()))
                .orElseThrow(() -> new ResourceNotFoundException("Comment not found: " + id));
        if (comment.getReadAt() == null) {
            comment.setReadAt(Instant.now(clock));
        }
        return toResponse(repository.save(comment));
    }

    /** Callers have already checked the coach may see this participant. */
    List<CoachCommentResponse> forParticipant(Long participantId) {
        return repository.findByUser_IdOrderByCreatedAtDesc(participantId).stream().map(this::toResponse).toList();
    }

    /** Callers have already checked the coach may see this participant. */
    CoachCommentResponse create(AppUser participant, AppUser coach, CoachCommentRequest request) {
        Long targetId = request.targetType() == CoachCommentTarget.GENERAL ? null : request.targetId();
        if (request.targetType() != CoachCommentTarget.GENERAL
                && (targetId == null || targetTitle(request.targetType(), targetId, participant.getId()).isEmpty())) {
            throw new BusinessRuleException("That item was not found in this participant's plan.");
        }
        CoachComment comment = CoachComment.builder()
                .user(participant)
                .coach(coach)
                .targetType(request.targetType())
                .targetId(targetId)
                .body(request.body().trim())
                .build();
        return toResponse(repository.save(comment));
    }

    @Nullable
    private CoachCommentResponse toResponse(CoachComment comment) {
        AppUser coach = comment.getCoach();
        String title = comment.getTargetId() == null ? null
                : targetTitle(comment.getTargetType(), comment.getTargetId(), comment.getUser().getId()).orElse(null);
        return new CoachCommentResponse(comment.getId(), coach == null ? null : coach.getFullName(),
                comment.getTargetType(), comment.getTargetId(), title, comment.getBody(), comment.getCreatedAt(), comment.getReadAt());
    }

    /** The item's title, only when it belongs to the participant. */
    private Optional<String> targetTitle(CoachCommentTarget type, Long id, Long participantId) {
        return switch (type) {
            case DREAM -> dreamRepository.findById(id).filter(dream -> dream.getUser().getId().equals(participantId)).map(Dream::getTitle);
            case GOAL -> goalRepository.findById(id).filter(goal -> goal.getUser().getId().equals(participantId)).map(Goal::getTitle);
            case STEP -> visionStepRepository.findById(id).filter(step -> step.getUser().getId().equals(participantId)).map(VisionStep::getTitle);
            case GENERAL -> Optional.empty();
        };
    }
}
