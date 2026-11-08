package com.visionmapping.mapper;

import com.visionmapping.dto.response.CommunicationMessageResponse;
import com.visionmapping.dto.response.DreamResponse;
import com.visionmapping.dto.response.AttitudeCheckInResponse;
import com.visionmapping.dto.response.FearEntryResponse;
import com.visionmapping.dto.response.GratitudeEntryResponse;
import com.visionmapping.dto.response.IdealPartnerProfileResponse;
import com.visionmapping.dto.response.IssueReportResponse;
import com.visionmapping.dto.response.GoalResponse;
import com.visionmapping.dto.response.ObstacleResponse;
import com.visionmapping.dto.response.PartnerResponse;
import com.visionmapping.dto.response.ProgressLogResponse;
import com.visionmapping.dto.response.ReviewResponse;
import com.visionmapping.dto.response.TaskItemResponse;
import com.visionmapping.dto.response.VisionAreaResponse;
import com.visionmapping.dto.response.VisionStepResponse;
import com.visionmapping.entity.AppUser;
import com.visionmapping.entity.CommunicationMessage;
import com.visionmapping.entity.Dream;
import com.visionmapping.entity.Goal;
import com.visionmapping.entity.AttitudeCheckIn;
import com.visionmapping.entity.FearEntry;
import com.visionmapping.entity.GratitudeEntry;
import com.visionmapping.entity.IdealPartnerProfile;
import com.visionmapping.entity.IssueReport;
import com.visionmapping.entity.Obstacle;
import com.visionmapping.entity.Partner;
import com.visionmapping.entity.ProgressLog;
import com.visionmapping.entity.Review;
import com.visionmapping.entity.TaskItem;
import com.visionmapping.entity.VisionArea;
import com.visionmapping.entity.VisionStep;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;

/**
 * Turns entities into the API's response records, so controllers never expose
 * an entity (CLAUDE.md Backend Design Rules). Wide responses are built with their
 * builders; a guard-rail test checks that every field is set.
 */
@Component
public class VisionMappingMapper {

    public VisionAreaResponse toResponse(VisionArea entity) {
        return new VisionAreaResponse(entity.getId(), entity.getCode(), entity.getName(), entity.getDescription(),
                entity.getVisionStatement(), entity.getPriority(), entity.getLetterRank(), entity.getStatus(),
                entity.getSortOrder(), entity.isArchived(), entity.getCreatedAt(), entity.getUpdatedAt());
    }

    public DreamResponse toResponse(Dream entity) {
        return toResponse(entity, ScheduleOverrun.NONE);
    }

    // FR-51: overrun is computed by the caller (DreamService), which alone
    // holds the child-repository access needed to know it accurately.
    public DreamResponse toResponse(Dream entity, ScheduleOverrun scheduleOverrun) {
        return DreamResponse.builder()
                .id(entity.getId())
                .code(entity.getCode())
                .visionAreaId(entity.getVisionArea().getId())
                .title(entity.getTitle())
                .description(entity.getDescription())
                .whyImportant(entity.getWhyImportant())
                .successDefinition(entity.getSuccessDefinition())
                .dreamType(entity.getDreamType())
                .priority(entity.getPriority())
                .letterRank(entity.getLetterRank())
                .targetDate(entity.getTargetDate())
                .status(entity.getStatus())
                .moonshot(entity.isMoonshot())
                .moonshotVision(entity.getMoonshotVision())
                .imageUrl(entity.getImageUrl())
                .scheduleMode(entity.getScheduleMode())
                .scheduleOverrun(scheduleOverrun.overrun())
                .scheduleOverrunDetail(scheduleOverrun.detail())
                .decisionSkippedResearch(entity.getDecisionSkippedResearch())
                .decisionAssumedNoChange(entity.getDecisionAssumedNoChange())
                .decisionTrustedUnverifiedClaim(entity.getDecisionTrustedUnverifiedClaim())
                .decisionJudgedByAppearance(entity.getDecisionJudgedByAppearance())
                .decisionUnderTimePressure(entity.getDecisionUnderTimePressure())
                .decisionNoOutsideInput(entity.getDecisionNoOutsideInput())
                .decisionChasedEasyReward(entity.getDecisionChasedEasyReward())
                .decisionDismissedDisagreeingAdvice(entity.getDecisionDismissedDisagreeingAdvice())
                .decisionGateClearedAt(entity.getDecisionGateClearedAt())
                .commitmentSignedName(entity.getCommitmentSignedName())
                .commitmentStartDate(entity.getCommitmentStartDate())
                .commitmentFirstAction(entity.getCommitmentFirstAction())
                .committedAt(entity.getCommittedAt())
                .sortOrder(entity.getSortOrder())
                .archived(entity.isArchived())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }

    public GoalResponse toResponse(Goal entity) {
        return toResponse(entity, ScheduleOverrun.NONE);
    }

    // FR-51: overrun is computed by the caller (GoalService), same reasoning
    // as the Dream overload above.
    public GoalResponse toResponse(Goal entity, ScheduleOverrun scheduleOverrun) {
        return GoalResponse.builder()
                .id(entity.getId())
                .code(entity.getCode())
                .dreamId(entity.getDream().getId())
                .title(entity.getTitle())
                .description(entity.getDescription())
                .successCriteria(entity.getSuccessCriteria())
                .priority(entity.getPriority())
                .letterRank(entity.getLetterRank())
                .targetDate(entity.getTargetDate())
                .status(entity.getStatus())
                .progressPercent(entity.getProgressPercent())
                .manualProgressOverride(entity.isManualProgressOverride())
                .moonshot(entity.isMoonshot())
                .moonshotVision(entity.getMoonshotVision())
                .scheduleMode(entity.getScheduleMode())
                .scheduleOverrun(scheduleOverrun.overrun())
                .scheduleOverrunDetail(scheduleOverrun.detail())
                .sortOrder(entity.getSortOrder())
                .archived(entity.isArchived())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }

    public VisionStepResponse toResponse(VisionStep entity) {
        return new VisionStepResponse(entity.getId(), entity.getCode(), entity.getGoal().getId(), entity.getTitle(),
                entity.getDescription(), entity.getOwner(), entity.getSequenceNumber(), entity.isComplex(), entity.getPriority(),
                entity.getTargetDate(), entity.getStatus(), entity.getProgressPercent(), entity.isManualProgressOverride(),
                entity.isArchived(), entity.getCreatedAt(), entity.getUpdatedAt(), entity.getResources(),
                entity.getOverdueReschedules(), entity.getEstimatedHours(), id(entity.getStartsAfter()));
    }

    public TaskItemResponse toResponse(TaskItem entity) {
        return TaskItemResponse.builder()
                .id(entity.getId())
                .code(entity.getCode())
                .stepId(entity.getStep().getId())
                .title(entity.getTitle())
                .description(entity.getDescription())
                .owner(entity.getOwner())
                .priority(entity.getPriority())
                .letterRank(entity.getLetterRank())
                .startDate(entity.getStartDate())
                .dueDate(entity.getDueDate())
                .status(entity.getStatus())
                .progressPercent(entity.getProgressPercent())
                .estimatedHours(entity.getEstimatedHours())
                .actualHours(entity.getActualHours())
                .blockerReason(entity.getBlockerReason())
                .nextAction(entity.getNextAction())
                .energyDemand(entity.getEnergyDemand())
                .completedAt(entity.getCompletedAt())
                .sortOrder(entity.getSortOrder())
                .listOrder(entity.getListOrder())
                .archived(entity.isArchived())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }

    public PartnerResponse toResponse(Partner entity) {
        return PartnerResponse.builder()
                .id(entity.getId())
                .code(entity.getCode())
                .name(entity.getName())
                .role(entity.getRole())
                .organization(entity.getOrganization())
                .email(entity.getEmail())
                .phone(entity.getPhone())
                .strength(entity.getStrength())
                .supportType(entity.getSupportType())
                .offerType(entity.getOfferType())
                .relatedVisionAreaId(id(entity.getRelatedVisionArea()))
                .relatedDreamId(id(entity.getRelatedDream()))
                .relatedGoalId(id(entity.getRelatedGoal()))
                .relatedStepId(id(entity.getRelatedStep()))
                .relatedTaskId(id(entity.getRelatedTask()))
                .status(entity.getStatus())
                .notes(entity.getNotes())
                .flagDishonesty(entity.getFlagDishonesty())
                .flagAnger(entity.getFlagAnger())
                .flagPoorJudgment(entity.getFlagPoorJudgment())
                .flagOutsizedReward(entity.getFlagOutsizedReward())
                .flagFlatteryPressure(entity.getFlagFlatteryPressure())
                .flagGossip(entity.getFlagGossip())
                .flagDisregardBoundaries(entity.getFlagDisregardBoundaries())
                .riskOverrideNote(entity.getRiskOverrideNote())
                .primaryMotivator(entity.getPrimaryMotivator())
                .vettedAt(entity.getVettedAt())
                .workStyleType(entity.getWorkStyleType())
                .archived(entity.isArchived())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }

    public IdealPartnerProfileResponse toResponse(IdealPartnerProfile entity) {
        return new IdealPartnerProfileResponse(entity.getId(), entity.getStep().getId(), entity.getRequiredExperience(),
                entity.getCharacterTraits(), entity.getMotivation(), entity.getOfferInReturn(), entity.isArchived(),
                entity.getCreatedAt(), entity.getUpdatedAt());
    }

    public FearEntryResponse toResponse(FearEntry entity) {
        return new FearEntryResponse(entity.getId(), entity.getFear(), entity.getWorstCase(), entity.getLikelihood(),
                entity.getFirstTest(), id(entity.getRelatedDream()), entity.isArchived(), entity.getCreatedAt(), entity.getUpdatedAt());
    }

    public AttitudeCheckInResponse toResponse(AttitudeCheckIn entity) {
        return new AttitudeCheckInResponse(entity.getId(), entity.getCheckInDate(), entity.getScore(), entity.getNote());
    }

    public GratitudeEntryResponse toResponse(GratitudeEntry entity) {
        return new GratitudeEntryResponse(entity.getId(), entity.getCategory(), entity.getDescription(),
                id(entity.getRelatedDream()), id(entity.getRelatedGoal()), entity.isArchived(),
                entity.getCreatedAt(), entity.getUpdatedAt());
    }

    public CommunicationMessageResponse toResponse(CommunicationMessage entity) {
        return CommunicationMessageResponse.builder()
                .id(entity.getId())
                .partnerId(id(entity.getPartner()))
                .relatedDreamId(id(entity.getRelatedDream()))
                .relatedGoalId(id(entity.getRelatedGoal()))
                .relatedTaskId(id(entity.getRelatedTask()))
                .audience(entity.getAudience())
                .purpose(entity.getPurpose())
                .subject(entity.getSubject())
                .hook(entity.getHook())
                .problem(entity.getProblem())
                .request(entity.getRequest())
                .benefitToPartner(entity.getBenefitToPartner())
                .wordPicture(entity.getWordPicture())
                .expectedOutcome(entity.getExpectedOutcome())
                .objectionsAndAnswers(entity.getObjectionsAndAnswers())
                .socialProof(entity.getSocialProof())
                .valueComparison(entity.getValueComparison())
                .callToAction(entity.getCallToAction())
                .messageBody(entity.getMessageBody())
                .status(entity.getStatus())
                .followUpDate(entity.getFollowUpDate())
                .archived(entity.isArchived())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }

    public ReviewResponse toResponse(Review entity) {
        return ReviewResponse.builder()
                .id(entity.getId())
                .reviewType(entity.getReviewType())
                .reviewDate(entity.getReviewDate())
                .letterRank(entity.getLetterRank())
                .relatedVisionAreaId(id(entity.getRelatedVisionArea()))
                .relatedDreamId(id(entity.getRelatedDream()))
                .summary(entity.getSummary())
                .completedTasks(entity.getCompletedTasks())
                .delayedTasks(entity.getDelayedTasks())
                .blockedTasks(entity.getBlockedTasks())
                .lessonsLearned(entity.getLessonsLearned())
                .nextActions(entity.getNextActions())
                .diligenceClearVision(entity.getDiligenceClearVision())
                .diligenceWorkedPlan(entity.getDiligenceWorkedPlan())
                .diligenceUsedLeverage(entity.getDiligenceUsedLeverage())
                .diligencePriorityFirst(entity.getDiligencePriorityFirst())
                .diligenceSmarterRoute(entity.getDiligenceSmarterRoute())
                .diligenceRightlyPlanned(entity.getDiligenceRightlyPlanned())
                .diligenceRightlyPerformed(entity.getDiligenceRightlyPerformed())
                .diligenceExpeditious(entity.getDiligenceExpeditious())
                .diligenceEfficient(entity.getDiligenceEfficient())
                .diligenceQualityOutcome(entity.getDiligenceQualityOutcome())
                .diligenceScorePercent(entity.getDiligenceScorePercent())
                .diligenceNote(entity.getDiligenceNote())
                .archived(entity.isArchived())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }

    public ObstacleResponse toResponse(Obstacle entity) {
        return ObstacleResponse.builder()
                .id(entity.getId())
                .relatedDreamId(id(entity.getRelatedDream()))
                .relatedGoalId(id(entity.getRelatedGoal()))
                .relatedStepId(id(entity.getRelatedStep()))
                .relatedTaskId(id(entity.getRelatedTask()))
                .title(entity.getTitle())
                .description(entity.getDescription())
                .obstacleType(entity.getObstacleType())
                .severity(entity.getSeverity())
                .letterRank(entity.getLetterRank())
                .solution(entity.getSolution())
                .rootCause(entity.getRootCause())
                .creativeAlternatives(entity.getCreativeAlternatives())
                .conflictIncident(entity.getConflictIncident())
                .conflictCost(entity.getConflictCost())
                .conflictOtherPerspective(entity.getConflictOtherPerspective())
                .conflictLesson(entity.getConflictLesson())
                .conflictPrivateNote(entity.getConflictPrivateNote())
                .conflictNextAction(entity.getConflictNextAction())
                .conflictExpectation(entity.getConflictExpectation())
                .conflictExpectationAgreed(entity.getConflictExpectationAgreed())
                .expectationReleasedAt(entity.getExpectationReleasedAt())
                .conflictNoCharacterAttacks(entity.getConflictNoCharacterAttacks())
                .conflictStayedOnIncident(entity.getConflictStayedOnIncident())
                .conflictNoThreatsOrSarcasm(entity.getConflictNoThreatsOrSarcasm())
                .conflictDefinedWinWin(entity.getConflictDefinedWinWin())
                .criticismOverstated(entity.getCriticismOverstated())
                .criticismDelivery(entity.getCriticismDelivery())
                .criticismSubstance(entity.getCriticismSubstance())
                .requiredPartnerId(id(entity.getRequiredPartner()))
                .status(entity.getStatus())
                .archived(entity.isArchived())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }

    public IssueReportResponse toResponse(IssueReport entity) {
        AppUser reporter = entity.getUser();
        return new IssueReportResponse(entity.getId(), entity.getCode(), reporter.getId(), reporter.getFullName(),
                reporter.getEmail(), entity.getReportType(), entity.getTitle(), entity.getDescription(),
                entity.getSeverity(), entity.getContextRoute(), entity.getAppVersion(), entity.getStatus(),
                entity.getResolutionNote(), entity.isArchived(), entity.getCreatedAt(), entity.getUpdatedAt());
    }

    public ProgressLogResponse toResponse(ProgressLog entity) {
        return new ProgressLogResponse(entity.getId(), entity.getRelatedTask().getId(),
                entity.getProgressPercentBefore(), entity.getProgressPercentAfter(), entity.getNote(), entity.getLoggedAt(),
                entity.isArchived());
    }

    @Nullable
    private Long id(@Nullable VisionArea entity) {
        return entity == null ? null : entity.getId();
    }

    @Nullable
    private Long id(@Nullable Dream entity) {
        return entity == null ? null : entity.getId();
    }

    @Nullable
    private Long id(@Nullable Goal entity) {
        return entity == null ? null : entity.getId();
    }

    @Nullable
    private Long id(@Nullable VisionStep entity) {
        return entity == null ? null : entity.getId();
    }

    @Nullable
    private Long id(@Nullable TaskItem entity) {
        return entity == null ? null : entity.getId();
    }

    @Nullable
    private Long id(@Nullable Partner entity) {
        return entity == null ? null : entity.getId();
    }
}
