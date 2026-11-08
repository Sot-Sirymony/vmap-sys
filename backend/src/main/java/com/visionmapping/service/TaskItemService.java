package com.visionmapping.service;

import static com.visionmapping.service.support.ServiceSupport.findAllForUser;
import static com.visionmapping.service.support.ServiceSupport.nextCode;
import static com.visionmapping.service.support.ServiceSupport.requireArchived;

import com.visionmapping.config.CacheConfig;
import com.visionmapping.dto.request.TaskItemRequest;
import com.visionmapping.dto.response.TaskItemResponse;
import com.visionmapping.entity.AppUser;
import com.visionmapping.entity.ProgressLog;
import com.visionmapping.entity.TaskItem;
import com.visionmapping.entity.VisionStep;
import com.visionmapping.entity.enums.WorkStatus;
import com.visionmapping.exception.BusinessRuleException;
import com.visionmapping.mapper.VisionMappingMapper;
import com.visionmapping.repository.ProgressLogRepository;
import com.visionmapping.repository.TaskItemRepository;
import com.visionmapping.service.support.ArchiveCascade;
import com.visionmapping.service.support.ArchiveFilter;
import com.visionmapping.service.support.EntityLookup;
import com.visionmapping.service.support.PermanentDeleteCascade;
import com.visionmapping.service.support.ProgressCalculator;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The executable unit of work. Every change to a task rolls its progress up
 * into its step and goal and records a progress-log entry when the percentage
 * moves. The blocked-needs-a-reason and completed-is-100 rules live in
 * {@link TaskItem} itself, so no caller can skip them.
 */
@Service
@Transactional
@RequiredArgsConstructor
public class TaskItemService {

    private final EntityLookup lookup;
    private final ProgressCalculator progress;
    private final ArchiveCascade archiveCascade;
    private final PermanentDeleteCascade permanentDeleteCascade;
    private final VisionMappingMapper mapper;
    private final TaskItemRepository taskItemRepository;
    private final ProgressLogRepository progressLogRepository;
    private final Clock clock;

    @Cacheable(CacheConfig.TASK_LIST_CACHE)
    @Transactional(readOnly = true)
    public List<TaskItemResponse> listTasks(ArchiveFilter archiveFilter) {
        return findAllForUser(taskItemRepository, lookup.userId(), archiveFilter).stream()
                .map(mapper::toResponse)
                .toList();
    }

    /** CLAUDE.md Core Business Rule 4: the new record belongs to one step, which must be the caller's own. */
    public TaskItemResponse createTask(TaskItemRequest request) {
        AppUser user = lookup.currentUser();
        VisionStep step = lookup.step(request.stepId());
        TaskItem entity = TaskItem.builder()
                .code(nextCode("T", taskItemRepository.findCodesByUserId(user.getId())))
                .user(user)
                .step(step)
                .title(request.title())
                .description(request.description())
                .owner(request.owner())
                .priority(request.priority())
                .letterRank(request.letterRank())
                .startDate(request.startDate())
                .dueDate(request.dueDate())
                .status(request.status())
                .progressPercent(progress.normalizeProgress(request.progressPercent()))
                .estimatedHours(request.estimatedHours())
                .actualHours(request.actualHours())
                .blockerReason(request.blockerReason())
                .nextAction(request.nextAction())
                .energyDemand(request.energyDemand())
                .sortOrder(Math.toIntExact(taskItemRepository.countByStep_IdAndUser_IdAndArchivedFalse(step.getId(), user.getId())))
                .build();
        entity.checkNewTask(clock);
        TaskItem saved = taskItemRepository.save(entity);
        progress.recalculateStep(step);
        return mapper.toResponse(saved);
    }

    @Cacheable(CacheConfig.TASK_CACHE)
    @Transactional(readOnly = true)
    public TaskItemResponse getTask(Long id) {
        return mapper.toResponse(lookup.task(id));
    }

    public TaskItemResponse updateTask(Long id, TaskItemRequest request) {
        TaskItem entity = lookup.task(id);
        BigDecimal progressBefore = entity.getProgressPercent();
        VisionStep oldStep = entity.getStep();
        entity.update(new TaskItem.Details(lookup.step(request.stepId()), request.title(), request.description(),
                request.owner(), request.priority(), request.letterRank(), request.startDate(), request.dueDate(),
                request.status(), progress.normalizeProgress(request.progressPercent()), request.estimatedHours(),
                request.actualHours(), request.blockerReason(), request.nextAction(), request.energyDemand()), clock);
        progress.recalculateStep(oldStep);
        progress.recalculateStep(entity.getStep());
        logProgressChange(entity, progressBefore);
        return mapper.toResponse(entity);
    }

    public TaskItemResponse updateTaskStatus(Long id, WorkStatus status) {
        TaskItem entity = lookup.task(id);
        BigDecimal progressBefore = entity.getProgressPercent();
        entity.changeStatus(status, clock);
        progress.recalculateStep(entity.getStep());
        logProgressChange(entity, progressBefore);
        return mapper.toResponse(entity);
    }

    // Vision Map drag-and-drop: orderedTaskIds must be exactly this step's
    // current non-archived tasks, just reshuffled — otherwise a stray or
    // missing id would silently orphan a task's position.
    public void reorderTasks(Long stepId, List<Long> orderedTaskIds) {
        VisionStep step = lookup.step(stepId);
        List<TaskItem> siblings = taskItemRepository.findByStep_IdAndUser_IdAndArchivedFalse(step.getId(), lookup.userId());
        Map<Long, TaskItem> byId = siblings.stream().collect(Collectors.toMap(TaskItem::getId, task -> task));
        if (orderedTaskIds.size() != siblings.size() || !byId.keySet().containsAll(orderedTaskIds)) {
            throw new BusinessRuleException("The given order must include exactly this step's current tasks.");
        }
        for (int index = 0; index < orderedTaskIds.size(); index++) {
            byId.get(orderedTaskIds.get(index)).setSortOrder(index);
        }
    }

    public void archiveTask(Long id) {
        TaskItem entity = lookup.task(id);
        entity.archive();
        progress.recalculateStep(entity.getStep());
    }

    public void restoreTask(Long id) {
        TaskItem entity = lookup.task(id);
        archiveCascade.unarchiveGoalChain(entity.getStep().getGoal());
        entity.getStep().restore();
        entity.restore();
        progress.recalculateStep(entity.getStep());
    }

    public void permanentlyDeleteTask(Long id) {
        TaskItem task = lookup.task(id);
        requireArchived(task.isArchived(), "Task");
        permanentDeleteCascade.deleteTask(task);
    }

    private void logProgressChange(TaskItem entity, BigDecimal progressBefore) {
        BigDecimal progressAfter = entity.getProgressPercent();
        if (progressBefore.compareTo(progressAfter) == 0) {
            return;
        }
        ProgressLog entry = ProgressLog.builder()
                .user(entity.getUser())
                .relatedTask(entity)
                .progressPercentBefore(progressBefore)
                .progressPercentAfter(progressAfter)
                .loggedAt(Instant.now(clock))
                .archived(false)
                .build();
        progressLogRepository.save(entry);
    }
}
