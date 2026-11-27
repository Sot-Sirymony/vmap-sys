package com.visionmapping.service;

import static com.visionmapping.service.support.ServiceSupport.nextCode;

import com.visionmapping.dto.response.StreamlineResponses;
import com.visionmapping.dto.request.StreamlineRequests;
import com.visionmapping.entity.TaskItem;
import com.visionmapping.entity.TaskRepeatRule;
import com.visionmapping.entity.enums.RepeatCadence;
import com.visionmapping.entity.enums.WorkStatus;
import com.visionmapping.exception.BusinessRuleException;
import com.visionmapping.exception.ResourceNotFoundException;
import com.visionmapping.repository.TaskItemRepository;
import com.visionmapping.repository.TaskRepeatRuleRepository;
import com.visionmapping.service.support.EntityLookup;
import com.visionmapping.service.support.ProgressCalculator;
import com.visionmapping.service.support.TaskStatusHistory;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * V27 SR-FR-09 (Q-27-4): repeating tasks in their simple form — daily, weekly or
 * monthly. The next task is created only once the current one is due, never in
 * the past, and never twice for one date (BR-27-10; the unique index on
 * task_items(repeat_rule_id, due_date) backs that up). Archiving the step stops
 * the rule.
 */
@Service
@Transactional
@RequiredArgsConstructor
public class TaskRepeatRuleService {

    private final EntityLookup lookup;
    private final TaskRepeatRuleRepository repository;
    private final TaskItemRepository taskItemRepository;
    private final ProgressCalculator progress;
    private final TaskStatusHistory statusHistory;
    private final Clock clock;
    private final com.visionmapping.service.support.UserCacheEvictor cacheEvictor;

    @Transactional(readOnly = true)
    public List<StreamlineResponses.RepeatRule> listRules() {
        return repository.findByUser_IdOrderByIdAsc(lookup.userId()).stream().map(TaskRepeatRuleService::toResponse).toList();
    }

    /** Makes a task repeat; a second call for the same task changes the cadence of its active rule. */
    /** SR-FR-09: a new rule from the page; both the task and the cadence are needed (V33 D-33-11 moved this check here). */
    public StreamlineResponses.RepeatRule createRule(StreamlineRequests.CreateRule request) {
        if (request.taskId() == null || request.cadence() == null) {
            throw new BusinessRuleException("Choose a task and how often it repeats.");
        }
        return toResponse(repeatTask(request.taskId(), request.cadence(), request.firstDue()));
    }

    public TaskRepeatRule repeatTask(Long taskId, RepeatCadence cadence, @Nullable LocalDate firstDue) {
        TaskItem task = lookup.task(taskId);
        if (task.isArchived() || task.getStep().isArchived()) {
            throw new BusinessRuleException("Restore this task before making it repeat.");
        }
        LocalDate today = LocalDate.now(clock);
        if (firstDue != null && firstDue.isBefore(today)) {
            throw new BusinessRuleException("The first repeat cannot be in the past.");
        }
        Optional<TaskRepeatRule> existing = repository.findFirstBySourceTask_IdAndUser_IdAndActiveTrue(task.getId(), lookup.userId());
        TaskRepeatRule rule = existing.orElseGet(() -> TaskRepeatRule.builder()
                .user(task.getUser())
                .step(task.getStep())
                .sourceTask(task)
                .lastTask(task)
                .title(task.getTitle())
                .active(true)
                .build());
        rule.setCadence(cadence);
        LocalDate base = task.getDueDate() != null ? task.getDueDate() : today;
        LocalDate next = firstDue != null ? firstDue : rule.after(base);
        while (next.isBefore(today)) {
            next = rule.after(next);
        }
        rule.setNextDue(next);
        TaskRepeatRule saved = repository.save(rule);
        if (task.getRepeatRuleId() == null) {
            task.setRepeatRuleId(saved.getId());
        }
        return saved;
    }

    public StreamlineResponses.RepeatRule updateRule(Long id, @Nullable RepeatCadence cadence, @Nullable Boolean active) {
        TaskRepeatRule rule = ownRule(id);
        if (cadence != null) {
            rule.setCadence(cadence);
        }
        if (active != null) {
            rule.setActive(active);
        }
        return toResponse(rule);
    }

    public void stopRule(Long id) {
        ownRule(id).setActive(false);
    }

    /**
     * The job's work for one rule, run in its own transaction. Returns the task
     * created, or null when nothing was due.
     */
    @Nullable
    public TaskItem runRule(Long ruleId) {
        TaskRepeatRule rule = repository.findById(ruleId).orElse(null);
        if (rule == null || !rule.isActive()) {
            return null;
        }
        if (rule.getStep().isArchived()) {
            rule.setActive(false);
            return null;
        }
        LocalDate today = LocalDate.now(clock);
        TaskItem current = rule.getLastTask();
        LocalDate currentDue = current == null ? null : current.getDueDate();
        // Q-27-4: the next task appears only once the current one is due.
        if (currentDue != null && today.isBefore(currentDue)) {
            return null;
        }
        LocalDate due = rule.getNextDue();
        while (due.isBefore(today) || (currentDue != null && !due.isAfter(currentDue))) {
            due = rule.after(due);
        }
        TaskItem template = rule.getSourceTask() != null ? rule.getSourceTask() : current;
        if (template == null) {
            rule.setActive(false);
            return null;
        }
        Long userId = rule.getUser().getId();
        final LocalDate dueDate = due;
        Optional<TaskItem> already = taskItemRepository.findByStep_IdAndUser_Id(rule.getStep().getId(), userId).stream()
                .filter(task -> rule.getId().equals(task.getRepeatRuleId()) && dueDate.equals(task.getDueDate()))
                .findFirst();
        if (already.isPresent()) {
            rule.setLastTask(already.get());
            rule.setNextDue(rule.after(dueDate));
            return null;
        }
        TaskItem created = TaskItem.builder()
                .code(nextCode("T", taskItemRepository.findCodesByUserId(userId)))
                .user(rule.getUser())
                .step(rule.getStep())
                .title(rule.getTitle())
                .description(template.getDescription())
                .owner(template.getOwner())
                .priority(template.getPriority())
                .letterRank(template.getLetterRank())
                .dueDate(dueDate)
                .status(WorkStatus.NOT_STARTED)
                .progressPercent(BigDecimal.ZERO.setScale(2))
                .estimatedHours(template.getEstimatedHours())
                .energyDemand(template.getEnergyDemand())
                .sortOrder(Math.toIntExact(taskItemRepository.countByStep_IdAndUser_IdAndArchivedFalse(rule.getStep().getId(), userId)))
                .repeatRuleId(rule.getId())
                .build();
        created.checkNewTask(clock);
        TaskItem saved = taskItemRepository.save(created);
        statusHistory.record(saved, null);
        progress.recalculateStep(rule.getStep());
        rule.setLastTask(saved);
        rule.setNextDue(rule.after(dueDate));
        // The job runs with no signed-in user, so the usual after-write eviction does not see this user.
        cacheEvictor.evictAll(userId);
        return saved;
    }

    @Transactional(readOnly = true)
    public List<Long> activeRuleIds() {
        return repository.findByActiveTrue().stream().map(TaskRepeatRule::getId).toList();
    }

    private TaskRepeatRule ownRule(Long id) {
        return repository.findByIdAndUser_Id(id, lookup.userId())
                .orElseThrow(() -> new ResourceNotFoundException("Repeat rule not found: " + id));
    }

    public static StreamlineResponses.RepeatRule toResponse(TaskRepeatRule rule) {
        return new StreamlineResponses.RepeatRule(rule.getId(), rule.getStep().getId(),
                rule.getSourceTask() == null ? null : rule.getSourceTask().getId(),
                rule.getLastTask() == null ? null : rule.getLastTask().getId(),
                rule.getTitle(), rule.getCadence(), rule.getNextDue(), rule.isActive());
    }
}
