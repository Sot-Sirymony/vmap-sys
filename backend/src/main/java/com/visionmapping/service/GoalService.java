package com.visionmapping.service;

import static com.visionmapping.service.support.ServiceSupport.findAllForUser;
import static com.visionmapping.service.support.ServiceSupport.nextCode;
import static com.visionmapping.service.support.ServiceSupport.parseEnum;
import static com.visionmapping.service.support.ServiceSupport.requireArchived;

import com.visionmapping.config.CacheConfig;
import com.visionmapping.dto.request.GoalRequest;
import com.visionmapping.dto.response.ArchiveImpactResponse;
import com.visionmapping.dto.response.GoalResponse;
import com.visionmapping.entity.AppUser;
import com.visionmapping.entity.Dream;
import com.visionmapping.entity.Goal;
import com.visionmapping.entity.VisionStep;
import com.visionmapping.entity.enums.ScheduleMode;
import com.visionmapping.entity.enums.WorkStatus;
import com.visionmapping.exception.BusinessRuleException;
import com.visionmapping.mapper.VisionMappingMapper;
import com.visionmapping.repository.GoalRepository;
import com.visionmapping.repository.PartnerRepository;
import com.visionmapping.repository.VisionStepRepository;
import com.visionmapping.service.support.ArchiveCascade;
import com.visionmapping.service.support.EntityLookup;
import com.visionmapping.service.support.PermanentDeleteCascade;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
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
    private final PartnerRepository partnerRepository;

    @Cacheable(CacheConfig.GOAL_LIST_CACHE)
    @Transactional(readOnly = true)
    public List<GoalResponse> listGoals(boolean includeArchived) {
        return findAllForUser(goalRepository, lookup.userId(), includeArchived).stream()
                .map(this::toResponse)
                .toList();
    }

    public GoalResponse createGoal(GoalRequest request) {
        AppUser user = lookup.currentUser();
        Dream dream = lookup.dream(request.dreamId());
        Goal entity = Goal.builder()
                .code(nextCode("G", goalRepository.findByUser_Id(user.getId()), Goal::getCode))
                .user(user)
                .dream(dream)
                .title(request.title())
                .description(request.description())
                .successCriteria(request.successCriteria())
                .priority(request.priority())
                .letterRank(request.letterRank())
                .targetDate(request.targetDate())
                .status(request.status())
                .progressPercent(ZERO)
                .manualProgressOverride(false)
                .moonshot(request.moonshot())
                .moonshotVision(request.moonshotVision())
                .scheduleMode(request.scheduleMode())
                .sortOrder(goalRepository.findByDream_IdAndUser_IdAndArchivedFalse(dream.getId(), user.getId()).size())
                .build();
        return toResponse(goalRepository.save(entity));
    }

    @Cacheable(CacheConfig.GOAL_CACHE)
    @Transactional(readOnly = true)
    public GoalResponse getGoal(Long id) {
        return toResponse(lookup.goal(id));
    }

    /** FR-59.4: backs the completion-time contribution nudge — never gates anything (BR-48). */
    @Transactional(readOnly = true)
    public boolean hasLinkedPartner(Long id) {
        Goal entity = lookup.goal(id);
        return partnerRepository.existsByUser_IdAndRelatedGoal_IdAndArchivedFalse(entity.getUser().getId(), entity.getId());
    }

    public GoalResponse updateGoal(Long id, GoalRequest request) {
        Goal entity = lookup.goal(id);
        entity.setDream(lookup.dream(request.dreamId()));
        entity.setTitle(request.title());
        entity.setDescription(request.description());
        entity.setSuccessCriteria(request.successCriteria());
        entity.setPriority(request.priority());
        entity.setLetterRank(request.letterRank());
        entity.setTargetDate(request.targetDate());
        entity.setStatus(request.status());
        entity.setMoonshot(request.moonshot());
        entity.setMoonshotVision(request.moonshotVision());
        entity.setScheduleMode(request.scheduleMode());
        validateGoalCompletion(entity, false);
        validateScheduleCascade(entity);
        return toResponse(entity);
    }

    public GoalResponse updateGoalStatus(Long id, String status, boolean manualOverride) {
        Goal entity = lookup.goal(id);
        entity.setStatus(parseEnum(WorkStatus.class, status));
        validateGoalCompletion(entity, manualOverride);
        if (manualOverride) {
            entity.setManualProgressOverride(true);
        }
        return toResponse(entity);
    }

    // Vision Map drag-and-drop: orderedGoalIds must be exactly this dream's
    // current non-archived goals, just reshuffled — otherwise a stray or
    // missing id would silently orphan a goal's position.
    public void reorderGoals(Long dreamId, List<Long> orderedGoalIds) {
        Dream dream = lookup.dream(dreamId);
        List<Goal> siblings = goalRepository.findByDream_IdAndUser_IdAndArchivedFalse(dream.getId(), lookup.userId());
        Map<Long, Goal> byId = siblings.stream().collect(Collectors.toMap(Goal::getId, goal -> goal));
        if (orderedGoalIds.size() != siblings.size() || !byId.keySet().containsAll(orderedGoalIds)) {
            throw new BusinessRuleException("The given order must include exactly this dream's current goals.");
        }
        for (int index = 0; index < orderedGoalIds.size(); index++) {
            byId.get(orderedGoalIds.get(index)).setSortOrder(index);
        }
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

    // FR-51: the latest targetDate among this goal's own non-archived steps
    // that actually have one set (a step with no date yet never counts).
    private LocalDate latestActiveStepDate(Goal goal) {
        return visionStepRepository.findByGoal_IdAndUser_IdAndArchivedFalse(goal.getId(), goal.getUser().getId()).stream()
                .map(VisionStep::getTargetDate)
                .filter(Objects::nonNull)
                .max(Comparator.naturalOrder())
                .orElse(null);
    }

    // BR-40: unless TOP_DOWN_FIXED, a goal's target date must not precede
    // its latest active step's target date.
    private void validateScheduleCascade(Goal goal) {
        if (goal.getTargetDate() == null || goal.getScheduleMode() != ScheduleMode.BOTTOM_UP) {
            return;
        }
        LocalDate latestStepDate = latestActiveStepDate(goal);
        if (latestStepDate != null && goal.getTargetDate().isBefore(latestStepDate)) {
            throw new BusinessRuleException(
                    "This goal's target date (%s) is earlier than one of its steps' target date (%s). Move the goal's date later, adjust the step, or switch this goal to a fixed top-down deadline."
                            .formatted(goal.getTargetDate(), latestStepDate));
        }
    }

    // FR-51: overrun is informational only — true when TOP_DOWN_FIXED lets a
    // parent date stand even though a child's date now runs past it.
    private GoalResponse toResponse(Goal entity) {
        LocalDate latestStepDate = latestActiveStepDate(entity);
        boolean overrun = entity.getScheduleMode() == ScheduleMode.TOP_DOWN_FIXED
                && entity.getTargetDate() != null && latestStepDate != null
                && entity.getTargetDate().isBefore(latestStepDate);
        String detail = overrun
                ? "A step's target date (%s) is after this goal's fixed target date (%s).".formatted(latestStepDate, entity.getTargetDate())
                : null;
        return mapper.toResponse(entity, overrun, detail);
    }
}
