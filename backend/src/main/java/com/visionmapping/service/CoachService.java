package com.visionmapping.service;

import com.visionmapping.security.Roles;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.visionmapping.dto.request.CoachCommentRequest;
import com.visionmapping.dto.request.PitchRatingRequest;
import com.visionmapping.dto.response.CoachCommentResponse;
import com.visionmapping.dto.response.CoachParticipantDetailResponse;
import com.visionmapping.dto.response.CoachParticipantResponse;
import com.visionmapping.dto.response.PitchReviewResponse;
import com.visionmapping.entity.AppUser;
import com.visionmapping.entity.Cohort;
import com.visionmapping.entity.ModuleCompletion;
import com.visionmapping.exception.BusinessRuleException;
import com.visionmapping.exception.ResourceNotFoundException;
import com.visionmapping.mapper.VisionMappingMapper;
import com.visionmapping.repository.AppUserRepository;
import com.visionmapping.repository.AttitudeCheckInRepository;
import com.visionmapping.repository.CohortRepository;
import com.visionmapping.repository.DreamRepository;
import com.visionmapping.repository.FearEntryRepository;
import com.visionmapping.repository.GoalRepository;
import com.visionmapping.repository.ModuleCompletionRepository;
import com.visionmapping.repository.TaskItemRepository;
import com.visionmapping.repository.VisionStepRepository;
import com.visionmapping.service.support.EntityLookup;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * V9 (v0.1 FR-16, FR-17; D-6, D-8): the coach workspace. A coach sees only the
 * participants of the active cohorts they coach, read-only, and their journals
 * only when shared. Every method checks that on the server; a participant
 * outside the coach's cohorts answers 404, the same as one that does not exist.
 */
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class CoachService {

    /** AC-05: no activity for this many days flags a participant. */
    static final int AT_RISK_AFTER_DAYS = 7;
    /** v0.1 FR-11: module 11, "Make a Clear Ask", holds the participant's pitch. */
    static final int PITCH_MODULE = 11;
    private static final TypeReference<List<Map<String, String>>> ROWS = new TypeReference<>() { };

    private final EntityLookup lookup;
    private final CohortRepository cohortRepository;
    private final AppUserRepository appUserRepository;
    private final DreamRepository dreamRepository;
    private final GoalRepository goalRepository;
    private final VisionStepRepository visionStepRepository;
    private final TaskItemRepository taskItemRepository;
    private final FearEntryRepository fearEntryRepository;
    private final AttitudeCheckInRepository attitudeCheckInRepository;
    private final VisionMappingMapper mapper;
    private final ActivityService activityService;
    private final ProgressMetricsService progressMetricsService;
    private final CoachCommentService coachCommentService;
    private final CoachRiskSignals coachRiskSignals;
    private final ModuleCompletionRepository moduleCompletionRepository;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public List<CoachParticipantResponse> participants() {
        AppUser coach = requireCoach();
        return cohortRepository.findByCoach_IdAndArchivedFalseOrderByNameAsc(coach.getId()).stream()
                .flatMap(cohort -> appUserRepository.findByCohort_IdOrderByFullNameAsc(cohort.getId()).stream())
                .filter(participant -> !participant.getId().equals(coach.getId()))
                .map(this::summary)
                .toList();
    }

    public CoachParticipantDetailResponse participant(Long participantId) {
        AppUser participant = visibleParticipant(participantId);
        Long id = participant.getId();
        boolean shared = participant.isShareJournalsWithCoach();
        return new CoachParticipantDetailResponse(
                summary(participant),
                dreamRepository.findByUser_IdAndArchivedFalse(id).stream().map(mapper::toResponse).toList(),
                goalRepository.findByUser_IdAndArchivedFalse(id).stream().map(mapper::toResponse).toList(),
                visionStepRepository.findByUser_IdAndArchivedFalse(id).stream().map(mapper::toResponse).toList(),
                taskItemRepository.findByUser_IdAndArchivedFalse(id).stream().map(mapper::toResponse).toList(),
                shared,
                shared ? fearEntryRepository.findByUser_IdAndArchivedFalse(id).stream().map(mapper::toResponse).toList() : null,
                shared ? attitudeCheckInRepository.findByUser_IdOrderByCheckInDateAsc(id).stream().map(mapper::toResponse).toList() : null,
                coachCommentService.forParticipant(id),
                moduleCompletionRepository.findByUser_IdAndModuleNumber(id, PITCH_MODULE)
                        .filter(completion -> completion.getRowsJson() != null)
                        .map(this::pitch)
                        .orElse(null));
    }

    @Transactional
    public PitchReviewResponse ratePitch(Long participantId, PitchRatingRequest request) {
        AppUser participant = visibleParticipant(participantId);
        ModuleCompletion completion = moduleCompletionRepository.findByUser_IdAndModuleNumber(participant.getId(), PITCH_MODULE)
                .filter(found -> found.getRowsJson() != null)
                .orElseThrow(() -> new BusinessRuleException("This participant has not saved a pitch yet."));
        completion.setCoachRating(request.rating());
        completion.setCoachFeedback(request.feedback() == null || request.feedback().isBlank() ? null : request.feedback().trim());
        completion.setCoachRatedBy(lookup.currentUser());
        completion.setCoachRatedAt(Instant.now(clock));
        return pitch(completion);
    }

    private PitchReviewResponse pitch(ModuleCompletion completion) {
        List<Map<String, String>> rows;
        try {
            rows = objectMapper.readValue(completion.getRowsJson(), ROWS);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Stored module rows are not valid JSON", exception);
        }
        AppUser ratedBy = completion.getCoachRatedBy();
        return new PitchReviewResponse(rows, completion.getCoachRating(),
                completion.getCoachFeedback(), ratedBy == null ? null : ratedBy.getFullName(), completion.getCoachRatedAt());
    }

    @Transactional
    public CoachCommentResponse comment(Long participantId, CoachCommentRequest request) {
        AppUser participant = visibleParticipant(participantId);
        return coachCommentService.create(participant, lookup.currentUser(), request);
    }

    private CoachParticipantResponse summary(AppUser participant) {
        Instant now = Instant.now(clock);
        Instant lastActive = activityService.lastActivity(participant);
        long daysInactive = Math.max(0, Duration.between(lastActive, now).toDays());
        Cohort cohort = participant.getCohort();
        return new CoachParticipantResponse(participant.getId(), participant.getFullName(), participant.getEmail(),
                cohort.getId(), cohort.getName(), lastActive, daysInactive, daysInactive >= AT_RISK_AFTER_DAYS,
                progressMetricsService.metricsFor(participant.getId(), LocalDate.now(clock)),
                coachRiskSignals.reasons(participant), participant.getAiConsentAt() != null);
    }

    /** For the coach co-pilot: the same check as every coach request (D-6), then the participant. */
    public AppUser requireVisibleParticipant(Long participantId) {
        return visibleParticipant(participantId);
    }

    /** One of the active cohorts the current coach coaches, or 404. */
    public Cohort requireOwnCohort(Long cohortId) {
        AppUser coach = requireCoach();
        return cohortRepository.findById(cohortId)
                .filter(cohort -> !cohort.isArchived() && cohort.getCoach() != null && cohort.getCoach().getId().equals(coach.getId()))
                .orElseThrow(() -> new ResourceNotFoundException("Cohort not found: " + cohortId));
    }

    private AppUser visibleParticipant(Long participantId) {
        AppUser coach = requireCoach();
        return appUserRepository.findById(participantId)
                .filter(participant -> !participant.getId().equals(coach.getId()))
                .filter(participant -> participant.getCohort() != null
                        && !participant.getCohort().isArchived()
                        && participant.getCohort().getCoach() != null
                        && participant.getCohort().getCoach().getId().equals(coach.getId()))
                .orElseThrow(() -> new ResourceNotFoundException("Participant not found: " + participantId));
    }

    private AppUser requireCoach() {
        return Roles.requireCoach(lookup.currentUser(), "Only a coach can open the coach workspace.");
    }
}
