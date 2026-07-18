package com.visionmapping.service;

import static com.visionmapping.service.support.ServiceSupport.findAllForUser;
import static com.visionmapping.service.support.ServiceSupport.isBlank;
import static com.visionmapping.service.support.ServiceSupport.nextCode;
import static com.visionmapping.service.support.ServiceSupport.parseEnum;
import static com.visionmapping.service.support.ServiceSupport.requireArchived;

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
import com.visionmapping.service.support.EntityLookup;
import com.visionmapping.service.support.PermanentDeleteCascade;
import com.visionmapping.service.support.ProgressCalculator;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The executable unit of work. Every change to a task rolls its progress up
 * into its step and goal, records a progress-log entry when the percentage
 * moves, and enforces the blocked-needs-a-reason and completed-is-100 rules.
 */
@Service
@Transactional
@RequiredArgsConstructor
public class TaskItemService {

    private static final BigDecimal ONE_HUNDRED = BigDecimal.valueOf(100).setScale(2, RoundingMode.HALF_UP);

    private final EntityLookup lookup;
    private final ProgressCalculator progress;
    private final ArchiveCascade archiveCascade;
    private final PermanentDeleteCascade permanentDeleteCascade;
    private final VisionMappingMapper mapper;
    private final TaskItemRepository taskItemRepository;
    private final ProgressLogRepository progressLogRepository;
    private final Clock clock;

    @Transactional(readOnly = true)
    public List<TaskItemResponse> listTasks(boolean includeArchived) {
        return findAllForUser(taskItemRepository, lookup.userId(), includeArchived).stream()
                .map(mapper::toResponse)
                .toList();
    }

    public TaskItemResponse createTask(TaskItemRequest request) {
        AppUser user = lookup.currentUser();
        VisionStep step = lookup.step(request.stepId());
        TaskItem entity = TaskItem.builder()
                .code(nextCode("T", taskItemRepository.findByUser_Id(user.getId()), TaskItem::getCode))
                .user(user)
                .step(step)
                .title(request.title())
                .description(request.description())
                .owner(request.owner())
                .priority(request.priority())
                .startDate(request.startDate())
                .dueDate(request.dueDate())
                .status(request.status())
                .progressPercent(progress.normalizeProgress(request.progressPercent()))
                .estimatedHours(request.estimatedHours())
                .actualHours(request.actualHours())
                .blockerReason(request.blockerReason())
                .nextAction(request.nextAction())
                .build();
        prepareTask(entity);
        TaskItem saved = taskItemRepository.save(entity);
        progress.recalculateStep(step);
        return mapper.toResponse(saved);
    }

    @Transactional(readOnly = true)
    public TaskItemResponse getTask(Long id) {
        return mapper.toResponse(lookup.task(id));
    }

    public TaskItemResponse updateTask(Long id, TaskItemRequest request) {
        TaskItem entity = lookup.task(id);
        BigDecimal progressBefore = entity.getProgressPercent();
        VisionStep oldStep = entity.getStep();
        entity.setStep(lookup.step(request.stepId()));
        entity.setTitle(request.title());
        entity.setDescription(request.description());
        entity.setOwner(request.owner());
        entity.setPriority(request.priority());
        entity.setStartDate(request.startDate());
        entity.setDueDate(request.dueDate());
        entity.setStatus(request.status());
        entity.setProgressPercent(progress.normalizeProgress(request.progressPercent()));
        entity.setEstimatedHours(request.estimatedHours());
        entity.setActualHours(request.actualHours());
        entity.setBlockerReason(request.blockerReason());
        entity.setNextAction(request.nextAction());
        prepareTask(entity);
        progress.recalculateStep(oldStep);
        progress.recalculateStep(entity.getStep());
        logProgressChange(entity, progressBefore);
        return mapper.toResponse(entity);
    }

    public TaskItemResponse updateTaskStatus(Long id, String status) {
        TaskItem entity = lookup.task(id);
        BigDecimal progressBefore = entity.getProgressPercent();
        entity.setStatus(parseEnum(WorkStatus.class, status));
        prepareTask(entity);
        progress.recalculateStep(entity.getStep());
        logProgressChange(entity, progressBefore);
        return mapper.toResponse(entity);
    }

    public void archiveTask(Long id) {
        TaskItem entity = lookup.task(id);
        entity.setArchived(true);
        progress.recalculateStep(entity.getStep());
    }

    public void restoreTask(Long id) {
        TaskItem entity = lookup.task(id);
        archiveCascade.unarchiveGoalChain(entity.getStep().getGoal());
        entity.getStep().setArchived(false);
        entity.setArchived(false);
        progress.recalculateStep(entity.getStep());
    }

    public void permanentlyDeleteTask(Long id) {
        TaskItem task = lookup.task(id);
        requireArchived(task.isArchived(), "Task");
        permanentDeleteCascade.deleteTask(task);
    }

    private void prepareTask(TaskItem entity) {
        if (entity.getStatus() == WorkStatus.BLOCKED && isBlank(entity.getBlockerReason())) {
            throw new BusinessRuleException("Blocked tasks must include a blocker reason.");
        }
        if (entity.getStatus() == WorkStatus.COMPLETED) {
            entity.setProgressPercent(ONE_HUNDRED);
            if (entity.getCompletedAt() == null) {
                entity.setCompletedAt(Instant.now(clock));
            }
        } else {
            entity.setCompletedAt(null);
        }
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
