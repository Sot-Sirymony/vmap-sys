package com.visionmapping.service;

import static com.visionmapping.service.support.ServiceSupport.findAllForUser;
import static com.visionmapping.service.support.ServiceSupport.nextCode;
import static com.visionmapping.service.support.ServiceSupport.requireArchived;

import com.visionmapping.config.CacheConfig;
import com.visionmapping.dto.request.VisionStepRequest;
import com.visionmapping.dto.response.ArchiveImpactResponse;
import com.visionmapping.dto.response.VisionStepResponse;
import com.visionmapping.entity.AppUser;
import com.visionmapping.entity.Goal;
import com.visionmapping.entity.TaskItem;
import com.visionmapping.entity.VisionStep;
import com.visionmapping.entity.enums.WorkStatus;
import com.visionmapping.exception.BusinessRuleException;
import com.visionmapping.mapper.VisionMappingMapper;
import com.visionmapping.repository.TaskItemRepository;
import com.visionmapping.repository.VisionStepRepository;
import com.visionmapping.service.support.ArchiveCascade;
import com.visionmapping.service.support.ArchiveFilter;
import com.visionmapping.service.support.EntityLookup;
import com.visionmapping.service.support.PermanentDeleteCascade;
import com.visionmapping.service.support.ProgressCalculator;
import com.visionmapping.service.support.ProgressOverride;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The action stages of a goal. A complex step must own at least one task before
 * it can be completed; every change rolls progress up into its goal.
 */
@Service
@Transactional
@RequiredArgsConstructor
public class VisionStepService {

    private static final BigDecimal ZERO = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);

    private final EntityLookup lookup;
    private final ProgressCalculator progress;
    private final ArchiveCascade archiveCascade;
    private final PermanentDeleteCascade permanentDeleteCascade;
    private final VisionMappingMapper mapper;
    private final VisionStepRepository visionStepRepository;
    private final TaskItemRepository taskItemRepository;
    private final Clock clock;

    @Cacheable(CacheConfig.STEP_LIST_CACHE)
    @Transactional(readOnly = true)
    public List<VisionStepResponse> listSteps(ArchiveFilter archiveFilter) {
        return findAllForUser(visionStepRepository, lookup.userId(), archiveFilter).stream()
                .map(mapper::toResponse)
                .toList();
    }

    /** CLAUDE.md Core Business Rule 3: the new record belongs to one goal, which must be the caller's own. */
    public VisionStepResponse createStep(VisionStepRequest request) {
        AppUser user = lookup.currentUser();
        Goal goal = lookup.goal(request.goalId());
        VisionStep entity = VisionStep.builder()
                .code(nextCode("S", visionStepRepository.findCodesByUserId(user.getId())))
                .user(user)
                .goal(goal)
                .title(request.title())
                .description(request.description())
                // Only the Excel import omits it: an older workbook has no Owner column.
                .owner(request.owner() != null ? request.owner() : user.getFullName())
                .sequenceNumber(request.sequenceNumber())
                .complex(request.complex())
                .priority(request.priority())
                .targetDate(request.targetDate())
                .status(request.status())
                .resources(blankToNull(request.resources()))
                .estimatedHours(request.estimatedHours())
                .progressPercent(ZERO)
                .manualProgressOverride(false)
                .build();
        entity.setStartsAfter(startsAfter(request.startsAfterStepId(), goal, null));
        VisionStep saved = visionStepRepository.save(entity);
        progress.recalculateGoal(goal);
        return mapper.toResponse(saved);
    }

    @Cacheable(CacheConfig.STEP_CACHE)
    @Transactional(readOnly = true)
    public VisionStepResponse getStep(Long id) {
        return mapper.toResponse(lookup.step(id));
    }

    public VisionStepResponse updateStep(Long id, VisionStepRequest request) {
        VisionStep entity = lookup.step(id);
        Goal oldGoal = entity.getGoal();
        entity.setGoal(lookup.goal(request.goalId()));
        entity.setTitle(request.title());
        entity.setDescription(request.description());
        entity.setOwner(request.owner());
        entity.setSequenceNumber(request.sequenceNumber());
        entity.setComplex(request.complex());
        entity.setPriority(request.priority());
        // AI-FR-11: moving an overdue, unfinished step to a new date counts as a slip.
        LocalDate today = LocalDate.now(clock);
        if (entity.getTargetDate() != null && entity.getTargetDate().isBefore(today)
                && entity.getStatus() != WorkStatus.COMPLETED
                && request.targetDate() != null && !request.targetDate().equals(entity.getTargetDate())) {
            entity.setOverdueReschedules(entity.getOverdueReschedules() + 1);
        }
        entity.setTargetDate(request.targetDate());
        entity.changeStatus(request.status());
        if (request.resources() != null) {
            entity.setResources(blankToNull(request.resources()));
        }
        if (request.estimatedHours() != null) {
            entity.setEstimatedHours(request.estimatedHours());
        }
        if (request.startsAfterStepId() != null) {
            entity.setStartsAfter(request.startsAfterStepId() == 0 ? null
                    : startsAfter(request.startsAfterStepId(), entity.getGoal(), entity.getId()));
        } else if (entity.getStartsAfter() != null && !entity.getStartsAfter().getGoal().getId().equals(entity.getGoal().getId())) {
            // Moved to another goal: the old order no longer applies.
            entity.setStartsAfter(null);
        }
        validateComplexStep(entity);
        validateScheduleCascade(entity);
        progress.recalculateGoal(oldGoal);
        progress.recalculateGoal(entity.getGoal());
        return mapper.toResponse(entity);
    }

    public VisionStepResponse updateStepStatus(Long id, WorkStatus status, ProgressOverride override) {
        VisionStep entity = lookup.step(id);
        entity.changeStatus(status);
        validateComplexStep(entity);
        if (override.isManual()) {
            entity.setManualProgressOverride(true);
        }
        progress.recalculateGoal(entity.getGoal());
        return mapper.toResponse(entity);
    }

    // Vision Map drag-and-drop: orderedStepIds must be exactly this goal's
    // current non-archived steps, just reshuffled — otherwise a stray or
    // missing id would silently orphan a step's position. Reuses
    // sequenceNumber (1-based, matching its existing convention) rather than
    // adding a second order column.
    public void reorderSteps(Long goalId, List<Long> orderedStepIds) {
        Goal goal = lookup.goal(goalId);
        List<VisionStep> siblings = visionStepRepository.findByGoal_IdAndUser_IdAndArchivedFalse(goal.getId(), lookup.userId());
        Map<Long, VisionStep> byId = siblings.stream().collect(Collectors.toMap(VisionStep::getId, step -> step));
        if (orderedStepIds.size() != siblings.size() || !byId.keySet().containsAll(orderedStepIds)) {
            throw new BusinessRuleException("The given order must include exactly this goal's current steps.");
        }
        for (int index = 0; index < orderedStepIds.size(); index++) {
            byId.get(orderedStepIds.get(index)).setSequenceNumber(index + 1);
        }
    }

    public void archiveStep(Long id) {
        VisionStep entity = lookup.step(id);
        entity.archive();
        archiveCascade.archiveTasksUnder(entity.getId());
        progress.recalculateGoal(entity.getGoal());
    }

    @Transactional(readOnly = true)
    public ArchiveImpactResponse stepArchiveImpact(Long id) {
        return archiveCascade.impactOfStep(lookup.step(id));
    }

    public void restoreStep(Long id) {
        VisionStep entity = lookup.step(id);
        archiveCascade.unarchiveGoalChain(entity.getGoal());
        entity.restore();
        // BR-13: the step's ideal partner profile follows the step back.
        archiveCascade.setProfileArchived(entity.getId(), false);
        progress.recalculateGoal(entity.getGoal());
    }

    public void permanentlyDeleteStep(Long id) {
        VisionStep step = lookup.step(id);
        requireArchived(step.isArchived(), "Step");
        permanentDeleteCascade.deleteStep(step);
    }

    /**
     * V11 (PB-FR-12): the step this one starts after must be the caller's own
     * (another user's answers 404), under the same goal, and not the step itself.
     */
    @Nullable
    private VisionStep startsAfter(@Nullable Long stepId, Goal goal, Long ownId) {
        if (stepId == null || stepId == 0) {
            return null;
        }
        VisionStep earlier = lookup.step(stepId);
        if (!earlier.getGoal().getId().equals(goal.getId()) || earlier.getId().equals(ownId)) {
            throw new BusinessRuleException("A step can only start after another step of the same goal.");
        }
        return earlier;
    }

    /** CLAUDE.md Core Business Rule 7: a complex step cannot be completed before it has at least one task. */
    private void validateComplexStep(VisionStep step) {
        if (step.isComplex() && step.getStatus() == WorkStatus.COMPLETED
                && taskItemRepository.findByStep_IdAndUser_IdAndArchivedFalse(step.getId(), step.getUser().getId()).isEmpty()) {
            throw new BusinessRuleException("A complex step must have at least one task before it can be completed.");
        }
    }

    // BR-40: a step's target date must not precede its latest active task's
    // due date. Unlike Dream/Goal, a step has no schedule-mode override — the
    // task-to-step boundary is the smallest unit and always enforced.
    private void validateScheduleCascade(VisionStep step) {
        if (step.getTargetDate() == null) {
            return;
        }
        LocalDate latestTaskDueDate = taskItemRepository.findByStep_IdAndUser_IdAndArchivedFalse(step.getId(), step.getUser().getId()).stream()
                .map(TaskItem::getDueDate)
                .filter(Objects::nonNull)
                .max(Comparator.naturalOrder())
                .orElse(null);
        if (latestTaskDueDate != null && step.getTargetDate().isBefore(latestTaskDueDate)) {
            throw new BusinessRuleException(
                    "This step's target date (%s) is earlier than one of its tasks' due date (%s). Move the step's date later or adjust the task."
                            .formatted(step.getTargetDate(), latestTaskDueDate));
        }
    }

    @Nullable
    private static String blankToNull(@Nullable String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
