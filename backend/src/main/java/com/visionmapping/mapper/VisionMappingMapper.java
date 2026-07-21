package com.visionmapping.mapper;

import com.visionmapping.dto.response.CommunicationMessageResponse;
import com.visionmapping.dto.response.DreamResponse;
import com.visionmapping.dto.response.IdealPartnerProfileResponse;
import com.visionmapping.dto.response.GoalResponse;
import com.visionmapping.dto.response.ObstacleResponse;
import com.visionmapping.dto.response.PartnerResponse;
import com.visionmapping.dto.response.ProgressLogResponse;
import com.visionmapping.dto.response.ReviewResponse;
import com.visionmapping.dto.response.TaskItemResponse;
import com.visionmapping.dto.response.VisionAreaResponse;
import com.visionmapping.dto.response.VisionStepResponse;
import com.visionmapping.entity.CommunicationMessage;
import com.visionmapping.entity.Dream;
import com.visionmapping.entity.Goal;
import com.visionmapping.entity.IdealPartnerProfile;
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
        return new DreamResponse(entity.getId(), entity.getCode(), entity.getVisionArea().getId(), entity.getTitle(),
                entity.getDescription(), entity.getWhyImportant(), entity.getSuccessDefinition(), entity.getDreamType(),
                entity.getPriority(), entity.getTargetDate(), entity.getStatus(), entity.isMoonshot(),
                entity.getMoonshotVision(), entity.isArchived(), entity.getCreatedAt(), entity.getUpdatedAt());
    }

    public GoalResponse toResponse(Goal entity) {
        return new GoalResponse(entity.getId(), entity.getCode(), entity.getDream().getId(), entity.getTitle(),
                entity.getDescription(), entity.getSuccessCriteria(), entity.getPriority(), entity.getTargetDate(),
                entity.getStatus(), entity.getProgressPercent(), entity.isManualProgressOverride(),
                entity.isMoonshot(), entity.getMoonshotVision(), entity.isArchived(),
                entity.getCreatedAt(), entity.getUpdatedAt());
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
                entity.getBlockerReason(), entity.getNextAction(), entity.getCompletedAt(), entity.isArchived(),
                entity.getCreatedAt(), entity.getUpdatedAt());
    }

    public PartnerResponse toResponse(Partner entity) {
        return new PartnerResponse(entity.getId(), entity.getCode(), entity.getName(), entity.getRole(),
                entity.getOrganization(), entity.getEmail(), entity.getPhone(), entity.getStrength(), entity.getSupportType(),
                entity.getOfferType(), id(entity.getRelatedVisionArea()), id(entity.getRelatedDream()), id(entity.getRelatedGoal()),
                id(entity.getRelatedStep()), id(entity.getRelatedTask()), entity.getStatus(), entity.getNotes(),
                entity.isArchived(), entity.getCreatedAt(), entity.getUpdatedAt());
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
                entity.getBenefitToPartner(), entity.getWordPicture(), entity.getExpectedOutcome(), entity.getMessageBody(), entity.getStatus(),
                entity.getFollowUpDate(), entity.isArchived(), entity.getCreatedAt(), entity.getUpdatedAt());
    }

    public ReviewResponse toResponse(Review entity) {
        return new ReviewResponse(entity.getId(), entity.getReviewType(), entity.getReviewDate(),
                id(entity.getRelatedVisionArea()), id(entity.getRelatedDream()), entity.getSummary(),
                entity.getCompletedTasks(), entity.getDelayedTasks(), entity.getBlockedTasks(), entity.getLessonsLearned(),
                entity.getNextActions(), entity.getDiligenceClearVision(), entity.getDiligenceWorkedPlan(),
                entity.getDiligenceUsedLeverage(), entity.getDiligencePriorityFirst(), entity.getDiligenceSmarterRoute(),
                entity.getDiligenceNote(), entity.isArchived(), entity.getCreatedAt(), entity.getUpdatedAt());
    }

    public ObstacleResponse toResponse(Obstacle entity) {
        return new ObstacleResponse(entity.getId(), id(entity.getRelatedDream()), id(entity.getRelatedGoal()),
                id(entity.getRelatedStep()), id(entity.getRelatedTask()), entity.getTitle(), entity.getDescription(),
                entity.getObstacleType(), entity.getSeverity(), entity.getSolution(), entity.getRootCause(),
                entity.getCreativeAlternatives(), id(entity.getRequiredPartner()),
                entity.getStatus(), entity.isArchived(), entity.getCreatedAt(), entity.getUpdatedAt());
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
