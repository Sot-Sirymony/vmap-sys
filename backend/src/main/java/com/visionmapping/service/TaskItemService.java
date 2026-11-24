package com.visionmapping.service;

import static com.visionmapping.service.support.ServiceSupport.findAllForUser;
import static com.visionmapping.service.support.ServiceSupport.isBlank;
import static com.visionmapping.service.support.ServiceSupport.nextCode;
import static com.visionmapping.service.support.ServiceSupport.requireArchived;

import com.visionmapping.config.CacheConfig;
import com.visionmapping.dto.request.TaskItemRequest;
import com.visionmapping.dto.request.TaskSplitRequest;
import com.visionmapping.dto.response.TaskItemResponse;
import com.visionmapping.dto.response.TaskSplitResponse;
import com.visionmapping.entity.AppUser;
import com.visionmapping.entity.ProgressLog;
import com.visionmapping.entity.TaskItem;
import com.visionmapping.entity.VisionStep;
import com.visionmapping.entity.enums.TaskSplitOutcome;
import com.visionmapping.entity.enums.WorkStatus;
import com.visionmapping.exception.BusinessRuleException;
import com.visionmapping.mapper.VisionMappingMapper;
import com.visionmapping.repository.ProgressLogRepository;
import com.visionmapping.repository.TaskItemRepository;
import com.visionmapping.service.support.ArchiveCascade;
import com.visionmapping.service.support.ArchiveFilter;
import com.visionmapping.service.support.EntityLookup;
import com.visionmapping.service.support.ObstacleAnchorSync;
import com.visionmapping.service.support.PermanentDeleteCascade;
import com.visionmapping.service.support.ProgressCalculator;
import com.visionmapping.service.support.TaskStatusHistory;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.lang.Nullable;
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
    private final ObstacleAnchorSync obstacleAnchorSync;
    private final TaskStatusHistory statusHistory;

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
        statusHistory.record(saved, null);
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
        WorkStatus statusBefore = entity.getStatus();
        VisionStep oldStep = entity.getStep();
        entity.update(new TaskItem.Details(lookup.step(request.stepId()), request.title(), request.description(),
                request.owner(), request.priority(), request.letterRank(), request.startDate(), request.dueDate(),
                request.status(), progress.normalizeProgress(request.progressPercent()), request.estimatedHours(),
                request.actualHours(), request.blockerReason(), request.nextAction(), request.energyDemand()), clock);
        statusHistory.record(entity, statusBefore);
        progress.recalculateStep(oldStep);
        progress.recalculateStep(entity.getStep());
        if (!oldStep.getId().equals(entity.getStep().getId())) {
            obstacleAnchorSync.afterTaskMoved(entity);
        }
        logChange(entity, progressBefore, changeNoteFor(statusBefore, entity, request.changeNote()));
        return mapper.toResponse(entity);
    }

    public TaskItemResponse updateTaskStatus(Long id, WorkStatus status) {
        return updateTaskStatus(id, status, null);
    }

    /** V17 BL-FR-27: a move out of Blocked may carry a "What changed?" note for the progress log. */
    public TaskItemResponse updateTaskStatus(Long id, WorkStatus status, @Nullable String changeNote) {
        TaskItem entity = lookup.task(id);
        BigDecimal progressBefore = entity.getProgressPercent();
        WorkStatus statusBefore = entity.getStatus();
        entity.changeStatus(status, clock);
        statusHistory.record(entity, statusBefore);
        progress.recalculateStep(entity.getStep());
        logChange(entity, progressBefore, changeNoteFor(statusBefore, entity, changeNote));
        return mapper.toResponse(entity);
    }

    /** V28 BA-FR-12: Blocked with a blocker reason, with the status history, progress and log of any status change. */
    public TaskItemResponse blockTask(Long id, String reason) {
        TaskItem entity = lookup.task(id);
        if (entity.isArchived() || entity.getStatus() == WorkStatus.COMPLETED) {
            throw new BusinessRuleException("Only an open task can be marked as blocked.");
        }
        BigDecimal progressBefore = entity.getProgressPercent();
        WorkStatus statusBefore = entity.getStatus();
        entity.markBlocked(reason, clock);
        statusHistory.record(entity, statusBefore);
        progress.recalculateStep(entity.getStep());
        logChange(entity, progressBefore, null);
        return mapper.toResponse(entity);
    }

    /**
     * V17 BL-FR-25: breaks a task into 2 to 5 smaller tasks under the same
     * step, placed right after it in the step's order and on the Tasks page.
     * KEEP leaves the original as a checklist parent; REPLACE archives it with
     * the note "Split into T-041 to T-043". Nothing is deleted.
     */
    public TaskSplitResponse splitTask(Long id, TaskSplitRequest request) {
        TaskItem original = lookup.task(id);
        if (original.isArchived()) {
            throw new BusinessRuleException("Restore this task before breaking it down.");
        }
        AppUser user = lookup.currentUser();
        VisionStep step = original.getStep();
        int count = request.rows().size();
        int stepPosition = makeRoomInStep(original, count);
        Integer listPosition = makeRoomInList(original, count);
        List<String> codes = new ArrayList<>(taskItemRepository.findCodesByUserId(user.getId()));
        List<TaskItem> created = new ArrayList<>();
        for (int index = 0; index < count; index++) {
            TaskSplitRequest.Row row = request.rows().get(index);
            String code = nextCode("T", codes);
            codes.add(code);
            TaskItem task = TaskItem.builder()
                    .code(code)
                    .user(user)
                    .step(step)
                    .title(row.title().strip())
                    .owner(isBlank(row.owner()) ? original.getOwner() : row.owner().strip())
                    .priority(row.priority() != null ? row.priority() : original.getPriority())
                    .dueDate(row.dueDate())
                    .status(WorkStatus.NOT_STARTED)
                    .progressPercent(progress.normalizeProgress(BigDecimal.ZERO))
                    .energyDemand(original.getEnergyDemand())
                    .sortOrder(stepPosition + 1 + index)
                    .listOrder(listPosition == null ? null : listPosition + 1 + index)
                    .build();
            task.checkNewTask(clock);
            TaskItem saved = taskItemRepository.save(task);
            statusHistory.record(saved, null);
            created.add(saved);
        }
        if (request.originalOutcome() == TaskSplitOutcome.REPLACE) {
            String note = "Split into " + created.get(0).getCode() + " to " + created.get(count - 1).getCode();
            original.setDescription(appendLine(original.getDescription(), note));
            original.archive();
            saveLog(original, original.getProgressPercent(), note);
        }
        progress.recalculateStep(step);
        return new TaskSplitResponse(created.stream().map(mapper::toResponse).toList(), mapper.toResponse(original));
    }

    /**
     * Gives every active task of the step a position (legacy rows have none,
     * so they are numbered in their shown order: position, then id), and
     * leaves {@code count} free places right after the original. Returns the
     * original's position.
     */
    private int makeRoomInStep(TaskItem original, int count) {
        List<TaskItem> siblings = taskItemRepository.findByStep_IdAndUser_IdAndArchivedFalse(original.getStep().getId(),
                        original.getUser().getId()).stream()
                .sorted(Comparator.comparing(TaskItem::getSortOrder, Comparator.nullsLast(Comparator.naturalOrder()))
                        .thenComparing(TaskItem::getId))
                .toList();
        int position = 0;
        int originalPosition = 0;
        for (TaskItem sibling : siblings) {
            sibling.setSortOrder(position);
            if (sibling.getId().equals(original.getId())) {
                originalPosition = position;
                position += count;
            }
            position++;
        }
        return originalPosition;
    }

    /** The same on the Tasks page order, but only once the participant has ordered it (the original has a place). */
    @Nullable
    private Integer makeRoomInList(TaskItem original, int count) {
        Integer place = original.getListOrder();
        if (place == null) {
            return null;
        }
        taskItemRepository.findByUser_IdAndArchivedFalse(original.getUser().getId()).stream()
                .filter(task -> task.getListOrder() != null && task.getListOrder() > place)
                .forEach(task -> task.setListOrder(task.getListOrder() + count));
        return place;
    }

    /** BL-FR-27: the note is kept only when the task has just left Blocked. */
    @Nullable
    private static String changeNoteFor(WorkStatus statusBefore, TaskItem entity, @Nullable String changeNote) {
        boolean leftBlocked = statusBefore == WorkStatus.BLOCKED && entity.getStatus() != WorkStatus.BLOCKED;
        return leftBlocked && !isBlank(changeNote) ? changeNote.strip() : null;
    }

    private static String appendLine(@Nullable String text, String line) {
        String joined = isBlank(text) ? line : text.strip() + "\n\n" + line;
        return joined.length() <= 3000 ? joined : line;
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

    // Tasks page drag-and-drop: the participant's own order across every
    // step. orderedTaskIds lists the user's tasks front to back; any active
    // task it leaves out keeps its relative place after the listed ones, so
    // a client that only knows some tasks can't lose the rest's order.
    public void reorderTaskList(List<Long> orderedTaskIds) {
        List<TaskItem> active = taskItemRepository.findByUser_IdAndArchivedFalse(lookup.userId());
        Map<Long, TaskItem> byId = active.stream().collect(Collectors.toMap(TaskItem::getId, task -> task));
        if (new HashSet<>(orderedTaskIds).size() != orderedTaskIds.size() || !byId.keySet().containsAll(orderedTaskIds)) {
            throw new BusinessRuleException("The given order may only list your current tasks, each once.");
        }
        Set<Long> listed = new HashSet<>(orderedTaskIds);
        List<TaskItem> rest = active.stream()
                .filter(task -> !listed.contains(task.getId()))
                .sorted(Comparator.comparing(TaskItem::getListOrder, Comparator.nullsLast(Comparator.naturalOrder()))
                        .thenComparing(TaskItem::getId))
                .toList();
        int position = 0;
        for (Long id : orderedTaskIds) {
            byId.get(id).setListOrder(position++);
        }
        for (TaskItem task : rest) {
            task.setListOrder(position++);
        }
    }

    public void archiveTask(Long id) {
        archiveTask(id, null);
    }

    /** V29 AR-FR-04: archives with an optional reason. */
    public void archiveTask(Long id, @Nullable String reason) {
        TaskItem entity = lookup.task(id);
        entity.archive(reason);
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

    /** A progress-log entry when the percentage moved, or when there is a note to keep (BL-FR-27). */
    private void logChange(TaskItem entity, BigDecimal progressBefore, @Nullable String note) {
        if (progressBefore.compareTo(entity.getProgressPercent()) == 0 && note == null) {
            return;
        }
        saveLog(entity, progressBefore, note);
    }

    private void saveLog(TaskItem entity, BigDecimal progressBefore, @Nullable String note) {
        ProgressLog entry = ProgressLog.builder()
                .user(entity.getUser())
                .relatedTask(entity)
                .progressPercentBefore(progressBefore)
                .progressPercentAfter(entity.getProgressPercent())
                .note(note)
                .loggedAt(Instant.now(clock))
                .archived(false)
                .build();
        progressLogRepository.save(entry);
    }
}
