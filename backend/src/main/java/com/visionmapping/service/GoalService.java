package com.visionmapping.service;

import static com.visionmapping.service.support.ServiceSupport.nextCode;
import static com.visionmapping.service.support.ServiceSupport.parseEnum;
import static com.visionmapping.service.support.ServiceSupport.requireArchived;

import com.visionmapping.dto.request.GoalRequest;
import com.visionmapping.dto.response.ArchiveImpactResponse;
import com.visionmapping.dto.response.GoalResponse;
import com.visionmapping.entity.AppUser;
import com.visionmapping.entity.Dream;
import com.visionmapping.entity.Goal;
import com.visionmapping.entity.enums.WorkStatus;
import com.visionmapping.exception.BusinessRuleException;
import com.visionmapping.mapper.VisionMappingMapper;
import com.visionmapping.repository.GoalRepository;
import com.visionmapping.repository.VisionStepRepository;
import com.visionmapping.service.support.ArchiveCascade;
import com.visionmapping.service.support.EntityLookup;
import com.visionmapping.service.support.PermanentDeleteCascade;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The major results of a dream, including the moonshot flag. A goal cannot be
 * marked complete until every step is complete unless the caller overrides.
 */
@Service
@Transactional
@RequiredArgsConstructor
public class GoalService {

    private static final BigDecimal ZERO = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);

    private final EntityLookup lookup;
    private final ArchiveCascade archiveCascade;
    private final PermanentDeleteCascade permanentDeleteCascade;
    private final VisionMappingMapper mapper;
    private final GoalRepository goalRepository;
    private final VisionStepRepository visionStepRepository;

    @Transactional(readOnly = true)
    public List<GoalResponse> listGoals(boolean includeArchived) {
        List<Goal> entities = includeArchived
                ? goalRepository.findByUser_Id(lookup.userId())
                : goalRepository.findByUser_IdAndArchivedFalse(lookup.userId());
        return entities.stream().map(mapper::toResponse).toList();
    }

    public GoalResponse createGoal(GoalRequest request) {
        AppUser user = lookup.currentUser();
        Dream dream = lookup.dream(request.dreamId());
        Goal entity = Goal.builder()
                .code(nextCode("G", goalRepository.findByUser_Id(user.getId()).size()))
                .user(user)
                .dream(dream)
                .title(request.title())
                .description(request.description())
                .successCriteria(request.successCriteria())
                .priority(request.priority())
                .targetDate(request.targetDate())
                .status(request.status())
                .progressPercent(ZERO)
                .manualProgressOverride(false)
                .moonshot(request.moonshot())
                .moonshotVision(request.moonshotVision())
                .build();
        return mapper.toResponse(goalRepository.save(entity));
    }

    @Transactional(readOnly = true)
    public GoalResponse getGoal(Long id) {
        return mapper.toResponse(lookup.goal(id));
    }

    public GoalResponse updateGoal(Long id, GoalRequest request) {
        Goal entity = lookup.goal(id);
        entity.setDream(lookup.dream(request.dreamId()));
        entity.setTitle(request.title());
        entity.setDescription(request.description());
        entity.setSuccessCriteria(request.successCriteria());
        entity.setPriority(request.priority());
        entity.setTargetDate(request.targetDate());
        entity.setStatus(request.status());
        entity.setMoonshot(request.moonshot());
        entity.setMoonshotVision(request.moonshotVision());
        validateGoalCompletion(entity, false);
        return mapper.toResponse(entity);
    }

    public GoalResponse updateGoalStatus(Long id, String status, boolean manualOverride) {
        Goal entity = lookup.goal(id);
        entity.setStatus(parseEnum(WorkStatus.class, status));
        validateGoalCompletion(entity, manualOverride);
        if (manualOverride) {
            entity.setManualProgressOverride(true);
        }
        return mapper.toResponse(entity);
    }

    public void archiveGoal(Long id) {
        Goal entity = lookup.goal(id);
        entity.setArchived(true);
        archiveCascade.archiveStepsUnder(entity.getId());
    }

    @Transactional(readOnly = true)
    public ArchiveImpactResponse goalArchiveImpact(Long id) {
        return archiveCascade.impactOfGoal(lookup.goal(id));
    }

    public void restoreGoal(Long id) {
        archiveCascade.unarchiveGoalChain(lookup.goal(id));
    }

    public void permanentlyDeleteGoal(Long id) {
        Goal goal = lookup.goal(id);
        requireArchived(goal.isArchived(), "Goal");
        permanentDeleteCascade.deleteGoal(goal);
    }

    private void validateGoalCompletion(Goal goal, boolean manualOverride) {
        if (goal.getStatus() != WorkStatus.COMPLETED || manualOverride) {
            return;
        }
        boolean allStepsComplete = visionStepRepository.findByGoal_IdAndUser_IdAndArchivedFalse(goal.getId(), goal.getUser().getId()).stream()
                .allMatch(step -> step.getStatus() == WorkStatus.COMPLETED);
        if (!allStepsComplete) {
            throw new BusinessRuleException("A goal cannot be completed until all steps are completed, unless manualOverride is true.");
        }
    }
}
