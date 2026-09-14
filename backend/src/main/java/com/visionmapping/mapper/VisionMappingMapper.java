package com.visionmapping.mapper;

import com.visionmapping.dto.response.CommunicationMessageResponse;
import com.visionmapping.dto.response.DreamResponse;
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
import com.visionmapping.entity.IdealPartnerProfile;
import com.visionmapping.entity.IssueReport;
import com.visionmapping.entity.Obstacle;
import com.visionmapping.entity.Partner;
import com.visionmapping.entity.ProgressLog;
import com.visionmapping.entity.Review;
import com.visionmapping.entity.TaskItem;
import com.visionmapping.entity.VisionArea;
import com.visionmapping.entity.VisionStep;
import org.springframework.stereotype.Component;

@Component
public class VisionMappingMapper {

    public VisionAreaResponse toResponse(VisionArea entity) {
        return new VisionAreaResponse(entity.getId(), entity.getCode(), entity.getName(), entity.getDescription(),
                entity.getVisionStatement(), entity.getPriority(), entity.getStatus(), entity.isArchived(),
                entity.getCreatedAt(), entity.getUpdatedAt());
    }

    public DreamResponse toResponse(Dream entity) {
        return toResponse(entity, false, null);
    }

    // FR-51: overrun is computed by the caller (DreamService), which alone
    // holds the child-repository access needed to know it accurately.
    public DreamResponse toResponse(Dream entity, boolean scheduleOverrun, String scheduleOverrunDetail) {
        return new DreamResponse(entity.getId(), entity.getCode(), entity.getVisionArea().getId(), entity.getTitle(),
                entity.getDescription(), entity.getWhyImportant(), entity.getSuccessDefinition(), entity.getDreamType(),
                entity.getPriority(), entity.getLetterRank(), entity.getTargetDate(), entity.getStatus(), entity.isMoonshot(),
                entity.getMoonshotVision(), entity.getImageUrl(), entity.getScheduleMode(), scheduleOverrun, scheduleOverrunDetail,
                entity.getDecisionSkippedResearch(), entity.getDecisionAssumedNoChange(),
                entity.getDecisionTrustedUnverifiedClaim(), entity.getDecisionJudgedByAppearance(),
                entity.getDecisionUnderTimePressure(), entity.getDecisionNoOutsideInput(),
                entity.getDecisionChasedEasyReward(), entity.getDecisionDismissedDisagreeingAdvice(),
                entity.getDecisionGateClearedAt(),
                entity.isArchived(), entity.getCreatedAt(), entity.getUpdatedAt());
    }

    public GoalResponse toResponse(Goal entity) {
        return toResponse(entity, false, null);
    }

    // FR-51: overrun is computed by the caller (GoalService), same reasoning
    // as the Dream overload above.
    public GoalResponse toResponse(Goal entity, boolean scheduleOverrun, String scheduleOverrunDetail) {
        return new GoalResponse(entity.getId(), entity.getCode(), entity.getDream().getId(), entity.getTitle(),
                entity.getDescription(), entity.getSuccessCriteria(), entity.getPriority(), entity.getTargetDate(),
                entity.getStatus(), entity.getProgressPercent(), entity.isManualProgressOverride(),
                entity.isMoonshot(), entity.getMoonshotVision(), entity.getScheduleMode(), scheduleOverrun,
                scheduleOverrunDetail, entity.isArchived(), entity.getCreatedAt(), entity.getUpdatedAt());
    }

    public VisionStepResponse toResponse(VisionStep entity) {
        return new VisionStepResponse(entity.getId(), entity.getCode(), entity.getGoal().getId(), entity.getTitle(),
                entity.getDescription(), entity.getSequenceNumber(), entity.isComplex(), entity.getPriority(),
                entity.getTargetDate(), entity.getStatus(), entity.getProgressPercent(), entity.isManualProgressOverride(),
                entity.isArchived(), entity.getCreatedAt(), entity.getUpdatedAt());
    }

    public TaskItemResponse toResponse(TaskItem entity) {
        return new TaskItemResponse(entity.getId(), entity.getCode(), entity.getStep().getId(), entity.getTitle(),
                entity.getDescription(), entity.getOwner(), entity.getPriority(), entity.getStartDate(), entity.getDueDate(),
                entity.getStatus(), entity.getProgressPercent(), entity.getEstimatedHours(), entity.getActualHours(),
                entity.getBlockerReason(), entity.getNextAction(), entity.getEnergyDemand(), entity.getCompletedAt(),
                entity.isArchived(), entity.getCreatedAt(), entity.getUpdatedAt());
    }

    public PartnerResponse toResponse(Partner entity) {
        return new PartnerResponse(entity.getId(), entity.getCode(), entity.getName(), entity.getRole(),
                entity.getOrganization(), entity.getEmail(), entity.getPhone(), entity.getStrength(), entity.getSupportType(),
                entity.getOfferType(), id(entity.getRelatedVisionArea()), id(entity.getRelatedDream()), id(entity.getRelatedGoal()),
                id(entity.getRelatedStep()), id(entity.getRelatedTask()), entity.getStatus(), entity.getNotes(),
                entity.getFlagDishonesty(), entity.getFlagAnger(), entity.getFlagPoorJudgment(), entity.getFlagOutsizedReward(),
                entity.getFlagFlatteryPressure(), entity.getFlagGossip(), entity.getFlagDisregardBoundaries(),
                entity.getRiskOverrideNote(), entity.getPrimaryMotivator(), entity.getVettedAt(),
                entity.getWorkStyleType(), entity.isArchived(), entity.getCreatedAt(), entity.getUpdatedAt());
    }

    public IdealPartnerProfileResponse toResponse(IdealPartnerProfile entity) {
        return new IdealPartnerProfileResponse(entity.getId(), entity.getStep().getId(), entity.getRequiredExperience(),
                entity.getCharacterTraits(), entity.getMotivation(), entity.getOfferInReturn(), entity.isArchived(),
                entity.getCreatedAt(), entity.getUpdatedAt());
    }

    public CommunicationMessageResponse toResponse(CommunicationMessage entity) {
        return new CommunicationMessageResponse(entity.getId(), id(entity.getPartner()), id(entity.getRelatedDream()),
                id(entity.getRelatedGoal()), id(entity.getRelatedTask()), entity.getAudience(), entity.getPurpose(),
                entity.getSubject(), entity.getHook(), entity.getProblem(), entity.getRequest(),
                entity.getBenefitToPartner(), entity.getWordPicture(), entity.getExpectedOutcome(),
                entity.getObjectionsAndAnswers(), entity.getSocialProof(), entity.getValueComparison(), entity.getCallToAction(),
                entity.getMessageBody(), entity.getStatus(),
                entity.getFollowUpDate(), entity.isArchived(), entity.getCreatedAt(), entity.getUpdatedAt());
    }

    public ReviewResponse toResponse(Review entity) {
        return new ReviewResponse(entity.getId(), entity.getReviewType(), entity.getReviewDate(),
                id(entity.getRelatedVisionArea()), id(entity.getRelatedDream()), entity.getSummary(),
                entity.getCompletedTasks(), entity.getDelayedTasks(), entity.getBlockedTasks(), entity.getLessonsLearned(),
                entity.getNextActions(), entity.getDiligenceClearVision(), entity.getDiligenceWorkedPlan(),
                entity.getDiligenceUsedLeverage(), entity.getDiligencePriorityFirst(), entity.getDiligenceSmarterRoute(),
                entity.getDiligenceRightlyPlanned(), entity.getDiligenceRightlyPerformed(), entity.getDiligenceExpeditious(),
                entity.getDiligenceEfficient(), entity.getDiligenceQualityOutcome(), entity.getDiligenceScorePercent(),
                entity.getDiligenceNote(), entity.isArchived(), entity.getCreatedAt(), entity.getUpdatedAt());
    }

    public ObstacleResponse toResponse(Obstacle entity) {
        return new ObstacleResponse(entity.getId(), id(entity.getRelatedDream()), id(entity.getRelatedGoal()),
                id(entity.getRelatedStep()), id(entity.getRelatedTask()), entity.getTitle(), entity.getDescription(),
                entity.getObstacleType(), entity.getSeverity(), entity.getSolution(), entity.getRootCause(),
                entity.getCreativeAlternatives(), entity.getConflictIncident(), entity.getConflictCost(),
                entity.getConflictOtherPerspective(), entity.getConflictLesson(), entity.getConflictPrivateNote(),
                entity.getConflictNextAction(), entity.getConflictExpectation(), entity.getConflictExpectationAgreed(),
                entity.getExpectationReleasedAt(), entity.getConflictNoCharacterAttacks(),
                entity.getConflictStayedOnIncident(), entity.getConflictNoThreatsOrSarcasm(),
                entity.getConflictDefinedWinWin(), entity.getCriticismOverstated(), entity.getCriticismDelivery(),
                entity.getCriticismSubstance(), id(entity.getRequiredPartner()),
                entity.getStatus(), entity.isArchived(), entity.getCreatedAt(), entity.getUpdatedAt());
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

    private Long id(VisionArea entity) {
        return entity == null ? null : entity.getId();
    }

    private Long id(Dream entity) {
        return entity == null ? null : entity.getId();
    }

    private Long id(Goal entity) {
        return entity == null ? null : entity.getId();
    }

    private Long id(VisionStep entity) {
        return entity == null ? null : entity.getId();
    }

    private Long id(TaskItem entity) {
        return entity == null ? null : entity.getId();
    }

    private Long id(Partner entity) {
        return entity == null ? null : entity.getId();
    }
}
