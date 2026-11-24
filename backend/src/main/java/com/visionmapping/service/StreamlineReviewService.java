package com.visionmapping.service;

import static com.visionmapping.service.support.ServiceSupport.nextCode;

import com.visionmapping.dto.request.StreamlineRequests;
import com.visionmapping.dto.response.StreamlineResponses;
import com.visionmapping.entity.AppUser;
import com.visionmapping.entity.Goal;
import com.visionmapping.entity.StreamlineItem;
import com.visionmapping.entity.StreamlineReview;
import com.visionmapping.entity.TaskItem;
import com.visionmapping.entity.TaskRepeatRule;
import com.visionmapping.entity.VisionStep;
import com.visionmapping.entity.enums.AutomationChoice;
import com.visionmapping.entity.enums.RequirementSource;
import com.visionmapping.entity.enums.SpeedLever;
import com.visionmapping.entity.enums.StreamlineDecision;
import com.visionmapping.entity.enums.StreamlineStageState;
import com.visionmapping.entity.enums.StreamlineStatus;
import com.visionmapping.entity.enums.StreamlineVerdict;
import com.visionmapping.entity.enums.WorkStatus;
import com.visionmapping.exception.BusinessRuleException;
import com.visionmapping.exception.ResourceNotFoundException;
import com.visionmapping.repository.StreamlineItemRepository;
import com.visionmapping.repository.StreamlineReviewRepository;
import com.visionmapping.repository.TaskItemRepository;
import com.visionmapping.repository.TaskRepeatRuleRepository;
import com.visionmapping.repository.VisionStepRepository;
import com.visionmapping.security.audit.SecurityEventService;
import com.visionmapping.security.audit.SecurityEventType;
import com.visionmapping.security.audit.SecurityOutcome;
import com.visionmapping.service.support.CycleTimeCalculator;
import com.visionmapping.service.support.EntityLookup;
import com.visionmapping.service.support.ObstacleAnchorSync;
import com.visionmapping.service.support.ProgressCalculator;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * V27 (BRD 27): the Streamline review — question, delete, simplify, speed up,
 * automate, in that order (D-27-3). A review covers a goal's steps and their
 * tasks, or one step's tasks (D-27-2). "Delete" archives at once and can be
 * undone (D-27-4, Q-27-3); a restore is counted as "added back" (D-27-5).
 * Everything is scoped to the signed-in user (BR-27-1).
 */
@Service
@Transactional
@RequiredArgsConstructor
public class StreamlineReviewService {

    static final String TARGET = "Streamline review";
    /** D-27-4: a restore counts as added back for this long after the item was cut. */
    static final Duration ADD_BACK_WINDOW = Duration.ofDays(90);
    /** V30: goal and step lines the AI reads when picking what to streamline. */
    static final int PICK_LINES = 40;

    private final EntityLookup lookup;
    private final StreamlineReviewRepository reviews;
    private final StreamlineItemRepository items;
    private final VisionStepRepository stepRepository;
    private final TaskItemRepository taskRepository;
    private final TaskRepeatRuleRepository repeatRules;
    private final VisionStepService stepService;
    private final TaskItemService taskService;
    private final TaskRepeatRuleService repeatService;
    private final ProgressCalculator progress;
    private final ObstacleAnchorSync obstacleAnchorSync;
    private final CycleTimeCalculator cycleTime;
    private final Clock clock;
    private SecurityEventService securityEvents;

    @Autowired(required = false)
    public void setSecurityEvents(SecurityEventService securityEvents) {
        this.securityEvents = securityEvents;
    }

    // ---------- Start, read, list ----------

    /** SR-FR-01 / BR-27-2: one open review per goal or step. The scope is captured now. */
    public StreamlineResponses.Review start(StreamlineRequests.Start request) {
        if ((request.goalId() == null) == (request.stepId() == null)) {
            throw new BusinessRuleException("Choose a goal or a step to streamline (one, not both).");
        }
        AppUser user = lookup.currentUser();
        Goal goal = request.goalId() == null ? null : lookup.goal(request.goalId());
        VisionStep step = request.stepId() == null ? null : lookup.step(request.stepId());
        if ((goal != null && goal.isArchived()) || (step != null && step.isArchived())) {
            throw new BusinessRuleException("Restore it before streamlining it.");
        }
        boolean open = goal != null
                ? reviews.existsByGoal_IdAndUser_IdAndStatus(goal.getId(), user.getId(), StreamlineStatus.OPEN)
                : reviews.existsByStep_IdAndUser_IdAndStatus(step.getId(), user.getId(), StreamlineStatus.OPEN);
        if (open) {
            throw new BusinessRuleException("This already has an open Streamline review. Finish or abandon it first.");
        }
        List<VisionStep> steps = goal != null
                ? stepRepository.findByGoal_IdAndUser_IdAndArchivedFalse(goal.getId(), user.getId()).stream()
                        .sorted(Comparator.comparing(VisionStep::getSequenceNumber, Comparator.nullsLast(Comparator.naturalOrder()))
                                .thenComparing(VisionStep::getId))
                        .toList()
                : List.of();
        List<TaskItem> tasks = new ArrayList<>();
        for (VisionStep scopeStep : goal != null ? steps : List.of(step)) {
            tasks.addAll(taskRepository.findByStep_IdAndUser_IdAndArchivedFalse(scopeStep.getId(), user.getId()).stream()
                    .sorted(Comparator.comparing(TaskItem::getSortOrder, Comparator.nullsLast(Comparator.naturalOrder()))
                            .thenComparing(TaskItem::getId))
                    .toList());
        }
        if (steps.isEmpty() && tasks.isEmpty()) {
            throw new BusinessRuleException(goal != null
                    ? "This goal has no open steps to streamline yet."
                    : "This step has no open tasks to streamline yet.");
        }
        Instant now = Instant.now(clock);
        StreamlineReview review = reviews.save(StreamlineReview.builder()
                .code(nextCode("SR", reviews.findCodesByUserId(user.getId())))
                .user(user)
                .goal(goal)
                .step(step)
                .status(StreamlineStatus.OPEN)
                .currentStage(1)
                .stage1State(StreamlineStageState.PENDING)
                .stage2State(StreamlineStageState.PENDING)
                .stage3State(StreamlineStageState.PENDING)
                .stage4State(StreamlineStageState.PENDING)
                .stage5State(StreamlineStageState.PENDING)
                .baselineSteps(steps.size())
                .baselineTasks(tasks.size())
                .baselineHours(hours(steps, tasks))
                .startedAt(now)
                .build());
        for (VisionStep scopeStep : steps) {
            items.save(StreamlineItem.builder().review(review).step(scopeStep).build());
        }
        for (TaskItem task : tasks) {
            items.save(StreamlineItem.builder().review(review).task(task).build());
        }
        return view(review);
    }

    public StreamlineResponses.Review get(Long id) {
        return view(own(id));
    }

    @Transactional(readOnly = true)
    public List<StreamlineResponses.ListItem> list() {
        return listFor(lookup.userId());
    }

    /** D-27-10: the same list for a coach, read-only (the caller checks the coach's access). */
    @Transactional(readOnly = true)
    public List<StreamlineResponses.ListItem> listFor(Long userId) {
        return reviews.findByUser_IdOrderByStartedAtDescIdDesc(userId).stream().map(this::listItem).toList();
    }

    /** D-27-10: one review for a coach, read-only. */
    @Transactional(readOnly = true)
    public StreamlineResponses.Review viewFor(Long userId, Long reviewId) {
        StreamlineReview review = reviews.findByIdAndUser_Id(reviewId, userId)
                .orElseThrow(() -> new ResourceNotFoundException(TARGET + " not found: " + reviewId));
        return build(review, items.findByReview_IdOrderByIdAsc(review.getId()));
    }

    // ---------- Stage 1: question ----------

    /** Records decisions on one item; each group of fields opens with its stage. */
    public StreamlineResponses.Review updateItem(Long reviewId, Long itemId, StreamlineRequests.UpdateItem request) {
        StreamlineReview review = own(reviewId);
        StreamlineItem item = item(review, itemId);
        boolean question = request.requirementOwner() != null || request.requirementSource() != null
                || request.partnerId() != null || request.whyNeeded() != null || request.verdict() != null;
        if (question) {
            review.requireUnlocked(1);
            applyQuestion(item, request.requirementOwner(), request.requirementSource(), request.whyNeeded(), request.verdict());
            if (request.partnerId() != null) {
                item.setPartner(lookup.partner(request.partnerId()));
            }
        }
        if (request.keep() != null || request.keepReason() != null) {
            review.requireUnlocked(2);
            if (item.getArchivedAt() != null && item.getRestoredAt() == null) {
                throw new BusinessRuleException("Restore this item first to keep it.");
            }
            if (Boolean.TRUE.equals(request.keep())) {
                item.setDecision(StreamlineDecision.KEEP);
            }
            if (request.keepReason() != null) {
                item.setKeepReason(blankToNull(request.keepReason()));
            }
        }
        if (request.speedLevers() != null) {
            review.requireUnlocked(4);
            item.setSpeedLevers(request.speedLevers().stream().distinct().map(Enum::name).collect(Collectors.joining(",")));
        }
        if (request.automation() != null || request.delegatePartnerId() != null) {
            review.requireUnlocked(5);
            if (request.automation() == AutomationChoice.REPEAT) {
                throw new BusinessRuleException("Use \"Make it repeat\" to choose how often the task comes back.");
            }
            if (request.automation() != null) {
                item.setAutomation(request.automation());
            }
            item.setDelegatePartner(request.delegatePartnerId() == null ? null : lookup.partner(request.delegatePartnerId()));
        }
        return view(review);
    }

    /** SR-FR-03 (Q-27-2): the same stage 1 answers for several items at once; each can still be changed afterwards. */
    public StreamlineResponses.Review bulkQuestion(Long reviewId, StreamlineRequests.BulkQuestion request) {
        StreamlineReview review = own(reviewId);
        review.requireUnlocked(1);
        Map<Long, StreamlineItem> byId = items.findByReview_IdOrderByIdAsc(review.getId()).stream()
                .collect(Collectors.toMap(StreamlineItem::getId, item -> item));
        for (Long id : new LinkedHashSet<>(request.itemIds())) {
            StreamlineItem item = byId.get(id);
            if (item == null) {
                throw new ResourceNotFoundException("Item not found in this review: " + id);
            }
            applyQuestion(item, request.requirementOwner(), request.requirementSource(), request.whyNeeded(), request.verdict());
        }
        return view(review);
    }

    private static void applyQuestion(StreamlineItem item, @Nullable String owner, @Nullable RequirementSource source,
                                      @Nullable String why, @Nullable StreamlineVerdict verdict) {
        if (owner != null) {
            item.setRequirementOwner(blankToNull(owner));
        }
        if (source != null) {
            item.setRequirementSource(source);
            if (source != RequirementSource.PARTNER) {
                item.setPartner(null);
            }
        }
        if (why != null) {
            item.setWhyNeeded(blankToNull(why));
        }
        if (verdict != null) {
            item.setVerdict(verdict);
        }
    }

    // ---------- Stage 2: delete, keep, merge ----------

    /** D-27-4, Q-27-3: archives at once (BR-12). Deleting a step archives its tasks too (BR-27-5). */
    public StreamlineResponses.Review deleteItem(Long reviewId, Long itemId, StreamlineRequests.Delete request) {
        StreamlineReview review = own(reviewId);
        review.requireUnlocked(2);
        StreamlineItem item = item(review, itemId);
        requireActive(item);
        List<StreamlineItem> all = items.findByReview_IdOrderByIdAsc(review.getId());
        requireNotEmptied(review, all, item, request != null && request.confirmEmpty());
        archive(review, item, all);
        item.setDecision(StreamlineDecision.DELETE);
        item.setMergeInto(null);
        return view(review);
    }

    /** Undo for a delete or merge; counts as "added back" (D-27-5). A step brings back the tasks archived with it. */
    public StreamlineResponses.Review restoreItem(Long reviewId, Long itemId) {
        StreamlineReview review = own(reviewId);
        if (review.getStatus() == StreamlineStatus.ABANDONED) {
            throw new BusinessRuleException("This review was abandoned. Restore the item from its own page.");
        }
        StreamlineItem item = item(review, itemId);
        if (item.getArchivedAt() == null || item.isArchivedWithParent()) {
            throw new BusinessRuleException("Only an item you deleted or merged in this review can be restored here.");
        }
        Instant now = Instant.now(clock);
        if (item.isStep()) {
            if (item.getStep().isArchived()) {
                stepService.restoreStep(item.getStep().getId());
            }
            for (StreamlineItem child : items.findByReview_IdOrderByIdAsc(review.getId())) {
                if (child.isArchivedWithParent() && !child.isStep() && child.getTask().getStep().getId().equals(item.getStep().getId())) {
                    if (child.getTask().isArchived()) {
                        taskService.restoreTask(child.getTask().getId());
                    }
                    child.setArchivedWithParent(false);
                    child.setArchivedAt(null);
                }
            }
        } else if (item.getTask().isArchived()) {
            taskService.restoreTask(item.getTask().getId());
        }
        item.setRestoredAt(now);
        return view(review);
    }

    /** BR-27-6: merge into another item of the same kind, then archive the source. */
    public StreamlineResponses.Review mergeItem(Long reviewId, Long itemId, StreamlineRequests.Merge request) {
        StreamlineReview review = own(reviewId);
        review.requireUnlocked(2);
        StreamlineItem source = item(review, itemId);
        StreamlineItem target = item(review, request.targetItemId());
        requireActive(source);
        requireActive(target);
        if (source.getId().equals(target.getId())) {
            throw new BusinessRuleException("Choose a different item to merge into.");
        }
        if (source.isStep() != target.isStep()) {
            throw new BusinessRuleException("Merge a step into a step, or a task into a task.");
        }
        List<StreamlineItem> all = items.findByReview_IdOrderByIdAsc(review.getId());
        requireNotEmptied(review, all, source, request.confirmEmpty());
        if (source.isStep()) {
            VisionStep from = source.getStep();
            VisionStep to = target.getStep();
            AppUser user = review.getUser();
            int next = Math.toIntExact(taskRepository.countByStep_IdAndUser_IdAndArchivedFalse(to.getId(), user.getId()));
            for (TaskItem task : taskRepository.findByStep_IdAndUser_IdAndArchivedFalse(from.getId(), user.getId())) {
                if (task.getStatus() != WorkStatus.COMPLETED) {
                    task.setStep(to);
                    task.setSortOrder(next++);
                    obstacleAnchorSync.afterTaskMoved(task);
                }
            }
            to.setDescription(appendLine(to.getDescription(), "Merged from " + from.getCode() + ": " + from.getTitle()));
            progress.recalculateStep(to);
        } else {
            TaskItem from = source.getTask();
            TaskItem to = target.getTask();
            to.setDescription(appendLine(to.getDescription(), "Merged from " + from.getCode() + ": " + from.getTitle()));
            to.setEstimatedHours(sum(to.getEstimatedHours(), from.getEstimatedHours()));
            to.setActualHours(sum(to.getActualHours(), from.getActualHours()));
        }
        archive(review, source, all);
        source.setDecision(StreamlineDecision.MERGE);
        source.setMergeInto(target);
        return view(review);
    }

    private void archive(StreamlineReview review, StreamlineItem item, List<StreamlineItem> all) {
        Instant now = Instant.now(clock);
        if (item.isStep()) {
            Long stepId = item.getStep().getId();
            for (StreamlineItem child : all) {
                if (!child.isStep() && child.getTask().getStep().getId().equals(stepId) && !child.getTask().isArchived()) {
                    child.setArchivedWithParent(true);
                    child.setArchivedAt(now);
                }
            }
            stepService.archiveStep(stepId);
        } else {
            taskService.archiveTask(item.getTask().getId());
        }
        item.setArchivedAt(now);
        item.setRestoredAt(null);
        item.setArchivedWithParent(false);
    }

    /** BR-27-8: a review may not leave its goal or step with nothing open unless the user confirms. */
    private static void requireNotEmptied(StreamlineReview review, List<StreamlineItem> all, StreamlineItem removing, boolean confirmed) {
        if (confirmed) {
            return;
        }
        boolean goalReview = review.getGoal() != null;
        long remaining = all.stream()
                .filter(other -> !other.getId().equals(removing.getId()))
                .filter(other -> goalReview ? other.isStep() : !other.isStep())
                .filter(other -> !other.targetArchived())
                .count();
        boolean anchorKind = goalReview == removing.isStep();
        if (anchorKind && remaining == 0) {
            throw new BusinessRuleException(goalReview
                    ? "This would leave the goal with no open steps. Confirm that the goal is finished or no longer needed."
                    : "This would leave the step with no open tasks. Confirm that the step is finished or no longer needed.");
        }
    }

    private static void requireActive(StreamlineItem item) {
        if (item.targetArchived()) {
            throw new BusinessRuleException(item.code() + " is already archived. Restore it first.");
        }
    }

    // ---------- Stage 3: simplify ----------

    /** Stage 3: simplify a surviving step or task in place (scope, owner, estimate, complex → simple). */
    public StreamlineResponses.Review simplify(Long reviewId, Long itemId, StreamlineRequests.Simplify request) {
        StreamlineReview review = own(reviewId);
        review.requireUnlocked(3);
        StreamlineItem item = item(review, itemId);
        requireActive(item);
        if (item.isStep()) {
            VisionStep step = item.getStep();
            if (request.title() != null && !request.title().isBlank()) {
                step.setTitle(request.title().strip());
            }
            if (request.description() != null) {
                step.setDescription(blankToNull(request.description()));
            }
            if (request.owner() != null) {
                step.setOwner(blankToNull(request.owner()));
            }
            if (request.estimatedHours() != null) {
                step.setEstimatedHours(request.estimatedHours());
            }
            if (Boolean.TRUE.equals(request.markSimple())) {
                step.setComplex(false);
            }
        } else {
            TaskItem task = item.getTask();
            if (request.title() != null && !request.title().isBlank()) {
                task.setTitle(request.title().strip());
            }
            if (request.description() != null) {
                task.setDescription(blankToNull(request.description()));
            }
            if (request.owner() != null) {
                if (request.owner().isBlank()) {
                    throw new BusinessRuleException("A task must keep an owner.");
                }
                task.setOwner(request.owner().strip());
            }
            if (request.estimatedHours() != null) {
                task.setEstimatedHours(request.estimatedHours());
            }
        }
        item.setSimplified(true);
        if (request.note() != null) {
            item.setSimplifyNote(blankToNull(request.note()));
        }
        return view(review);
    }

    // ---------- Stage 4: speed up ----------

    public StreamlineResponses.Review setTarget(Long reviewId, StreamlineRequests.Target request) {
        StreamlineReview review = own(reviewId);
        review.requireUnlocked(4);
        review.setTargetCycleDays(request.targetCycleDays().setScale(1, java.math.RoundingMode.HALF_UP));
        return view(review);
    }

    // ---------- Stage 5: automate ----------

    /** Stage 5 and SR-FR-09: make a task in scope repeat. */
    public StreamlineResponses.Review repeat(Long reviewId, Long itemId, StreamlineRequests.Repeat request) {
        StreamlineReview review = own(reviewId);
        review.requireUnlocked(5);
        StreamlineItem item = item(review, itemId);
        if (item.isStep()) {
            throw new BusinessRuleException("Only a task can repeat. Choose one of the step's tasks.");
        }
        requireActive(item);
        repeatService.repeatTask(item.getTask().getId(), request.cadence(), request.firstDue());
        item.setAutomation(AutomationChoice.REPEAT);
        return view(review);
    }

    // ---------- Stages ----------

    /** D-27-3: finish the current stage. Stage 1 and 2 have completion rules (BR-27-4, BR-27-7). */
    public StreamlineResponses.Review completeStage(Long reviewId, int stage) {
        StreamlineReview review = own(reviewId);
        List<StreamlineItem> all = items.findByReview_IdOrderByIdAsc(review.getId());
        List<StreamlineItem> active = all.stream().filter(item -> item.getArchivedAt() == null || item.getRestoredAt() != null).toList();
        if (stage == 1) {
            List<String> missing = active.stream().filter(Predicate.not(StreamlineReviewService::questioned)).map(StreamlineItem::code).toList();
            if (!missing.isEmpty()) {
                throw new BusinessRuleException("Answer who asked for it and whether it is needed for: " + String.join(", ", missing)
                        + ". A name is needed unless the source is \"Not sure\".");
            }
        }
        if (stage == 2) {
            List<String> undecided = new ArrayList<>();
            for (StreamlineItem item : active) {
                if (item.getDecision() == null) {
                    if (item.getVerdict() == StreamlineVerdict.NEEDED) {
                        item.setDecision(StreamlineDecision.KEEP);
                    } else {
                        undecided.add(item.code());
                    }
                } else if (item.getDecision() == StreamlineDecision.KEEP && item.getVerdict() == StreamlineVerdict.NOT_NEEDED
                        && (item.getKeepReason() == null || item.getKeepReason().isBlank())) {
                    undecided.add(item.code() + " (say why you keep it)");
                }
            }
            if (!undecided.isEmpty()) {
                throw new BusinessRuleException("Delete, merge or keep: " + String.join(", ", undecided) + ".");
            }
        }
        review.finishStage(stage, StreamlineStageState.DONE, null);
        afterStage(review, stage, all);
        return view(review);
    }

    /** D-27-3: a stage marked Not needed keeps its reason. */
    public StreamlineResponses.Review notNeeded(Long reviewId, int stage, StreamlineRequests.NotNeeded request) {
        StreamlineReview review = own(reviewId);
        review.finishStage(stage, StreamlineStageState.NOT_NEEDED, request.reason());
        afterStage(review, stage, items.findByReview_IdOrderByIdAsc(review.getId()));
        return view(review);
    }

    /** BR-27-12: decisions already applied stay; the review records where it stopped. */
    public StreamlineResponses.Review abandon(Long reviewId) {
        StreamlineReview review = own(reviewId);
        if (!review.isOpen()) {
            throw new BusinessRuleException("This review is already closed.");
        }
        review.setStatus(StreamlineStatus.ABANDONED);
        return view(review);
    }

    private void afterStage(StreamlineReview review, int stage, List<StreamlineItem> all) {
        if (stage == 3 && review.getBaselineCycleDays() == null) {
            review.setBaselineCycleDays(cycleTime.medianCycleDays(scopeTasks(all)));
        }
        if (stage == StreamlineReview.STAGES) {
            review.setStatus(StreamlineStatus.COMPLETED);
            review.setCompletedAt(Instant.now(clock));
            Counts counts = counts(all);
            review.setResultSteps(counts.steps());
            review.setResultTasks(counts.tasks());
            review.setResultHours(counts.hours());
        }
    }

    private static boolean questioned(StreamlineItem item) {
        if (item.getVerdict() == null || item.getRequirementSource() == null) {
            return false;
        }
        return item.getRequirementSource() == RequirementSource.NOT_SURE
                || (item.getRequirementOwner() != null && !item.getRequirementOwner().isBlank());
    }

    // ---------- Candidates (§6.2) ----------

    /**
     * §6.2: goals and steps worth streamlining, at most five, skipping any with an open review.
     * V30 (D-30-4, SA-FR-02): ranked by need, so the five shown are the ones that need it most.
     */
    @Transactional(readOnly = true)
    public List<StreamlineResponses.Candidate> candidates() {
        Long userId = lookup.userId();
        java.time.LocalDate today = java.time.LocalDate.now(clock);
        List<VisionStep> steps = stepRepository.findByUser_IdAndArchivedFalse(userId);
        List<TaskItem> tasks = taskRepository.findByUser_IdAndArchivedFalse(userId);
        Map<Long, List<VisionStep>> stepsByGoal = steps.stream().collect(Collectors.groupingBy(step -> step.getGoal().getId()));
        Map<Long, List<TaskItem>> tasksByStep = tasks.stream().collect(Collectors.groupingBy(task -> task.getStep().getId()));
        Set<String> seen = new HashSet<>();
        List<Ranked> out = new ArrayList<>();
        for (Map.Entry<Long, List<VisionStep>> entry : stepsByGoal.entrySet()) {
            Goal goal = entry.getValue().get(0).getGoal();
            if (goal.isArchived() || goal.getStatus() == WorkStatus.COMPLETED
                    || reviews.existsByGoal_IdAndUser_IdAndStatus(goal.getId(), userId, StreamlineStatus.OPEN)) {
                continue;
            }
            long openSteps = entry.getValue().stream().filter(step -> step.getStatus() != WorkStatus.COMPLETED).count();
            List<TaskItem> goalTasks = entry.getValue().stream().flatMap(step -> tasksByStep.getOrDefault(step.getId(), List.of()).stream())
                    .filter(task -> task.getStatus() != WorkStatus.COMPLETED).toList();
            long overdue = goalTasks.stream().filter(task -> task.getDueDate() != null && task.getDueDate().isBefore(today)).count();
            if (openSteps > 7) {
                add(out, seen, openSteps * 10 + overdue, "GOAL", goal.getId(), goal.getCode(), goal.getTitle(), "MANY_STEPS",
                        openSteps + " open steps");
            } else if (goalTasks.size() >= 3 && overdue * 10 > goalTasks.size() * 3) {
                add(out, seen, overdue * 10, "GOAL", goal.getId(), goal.getCode(), goal.getTitle(), "OVERDUE",
                        overdue + " of " + goalTasks.size() + " open tasks overdue");
            }
        }
        for (VisionStep step : steps) {
            long open = tasksByStep.getOrDefault(step.getId(), List.of()).stream().filter(task -> task.getStatus() != WorkStatus.COMPLETED).count();
            if (open > 10 && !reviews.existsByStep_IdAndUser_IdAndStatus(step.getId(), userId, StreamlineStatus.OPEN)) {
                add(out, seen, open * 10, "STEP", step.getId(), step.getCode(), step.getTitle(), "MANY_TASKS", open + " open tasks");
            }
        }
        // A task done by hand with the same title at least 4 times in 60 days: an automation candidate.
        Instant since = Instant.now(clock).minus(Duration.ofDays(60));
        Map<String, List<TaskItem>> done = taskRepository.findByUser_Id(userId).stream()
                .filter(task -> task.getCompletedAt() != null && !task.getCompletedAt().isBefore(since) && task.getRepeatRuleId() == null)
                .collect(Collectors.groupingBy(task -> normalise(task.getTitle())));
        for (List<TaskItem> group : done.values()) {
            if (group.size() >= 4) {
                VisionStep step = group.get(group.size() - 1).getStep();
                if (!step.isArchived() && !reviews.existsByStep_IdAndUser_IdAndStatus(step.getId(), userId, StreamlineStatus.OPEN)) {
                    add(out, seen, group.size() * 5L, "STEP", step.getId(), step.getCode(), step.getTitle(), "REPEATED_WORK",
                            "\"" + group.get(0).getTitle() + "\" done " + group.size() + " times in 60 days");
                }
            }
        }
        return out.stream()
                .sorted(Comparator.comparingLong(Ranked::weight).reversed().thenComparing(ranked -> ranked.candidate().code()))
                .limit(5).map(Ranked::candidate).toList();
    }

    private record Ranked(StreamlineResponses.Candidate candidate, long weight) {
    }

    private static void add(List<Ranked> out, Set<String> seen, long weight, String type, Long id, String code, String title,
                            String reason, String detail) {
        if (seen.add(type + id)) {
            out.add(new Ranked(new StreamlineResponses.Candidate(type, id, code, title, reason, detail), weight));
        }
    }

    /** V30 (D-30-4): what the AI reads to pick goals and steps to streamline, and the keys it may answer with. */
    public record PickContext(String records, Map<String, StreamlineResponses.Candidate> allowed) {
        public PickContext {
            allowed = com.visionmapping.util.Copies.map(allowed);
        }
    }

    /** Open goals and steps without an open review, with counts only (D-30-3: no names of people). */
    @Transactional(readOnly = true)
    public PickContext pickContext() {
        Long userId = lookup.userId();
        java.time.LocalDate today = java.time.LocalDate.now(clock);
        List<VisionStep> steps = stepRepository.findByUser_IdAndArchivedFalse(userId).stream()
                .filter(step -> step.getStatus() != WorkStatus.COMPLETED && !step.getGoal().isArchived()).toList();
        Map<Long, List<TaskItem>> tasksByStep = taskRepository.findByUser_IdAndArchivedFalse(userId).stream()
                .filter(task -> task.getStatus() != WorkStatus.COMPLETED)
                .collect(Collectors.groupingBy(task -> task.getStep().getId()));
        Map<Long, List<VisionStep>> stepsByGoal = steps.stream().collect(Collectors.groupingBy(step -> step.getGoal().getId(),
                java.util.TreeMap::new, Collectors.toList()));
        StringBuilder out = new StringBuilder("Today is ").append(today).append(". Open goals and steps:\n");
        Map<String, StreamlineResponses.Candidate> allowed = new java.util.LinkedHashMap<>();
        int lines = 0;
        for (List<VisionStep> goalSteps : stepsByGoal.values()) {
            Goal goal = goalSteps.get(0).getGoal();
            if (lines >= PICK_LINES || goal.getStatus() == WorkStatus.COMPLETED
                    || reviews.existsByGoal_IdAndUser_IdAndStatus(goal.getId(), userId, StreamlineStatus.OPEN)) {
                continue;
            }
            List<TaskItem> goalTasks = goalSteps.stream().flatMap(step -> tasksByStep.getOrDefault(step.getId(), List.of()).stream()).toList();
            out.append("[GOAL-").append(goal.getId()).append("] ").append(goal.getCode()).append(' ')
                    .append(com.visionmapping.service.ai.AiContextBuilder.clip(goal.getTitle())).append(" | ")
                    .append(counts(goalSteps.size(), goalTasks, today)).append('\n');
            allowed.put("GOAL-" + goal.getId(), new StreamlineResponses.Candidate("GOAL", goal.getId(), goal.getCode(), goal.getTitle(), "AI", null));
            lines++;
            for (VisionStep step : goalSteps) {
                List<TaskItem> stepTasks = tasksByStep.getOrDefault(step.getId(), List.of());
                if (lines >= PICK_LINES || stepTasks.size() < 3
                        || reviews.existsByStep_IdAndUser_IdAndStatus(step.getId(), userId, StreamlineStatus.OPEN)) {
                    continue;
                }
                out.append("  [STEP-").append(step.getId()).append("] ").append(step.getCode()).append(' ')
                        .append(com.visionmapping.service.ai.AiContextBuilder.clip(step.getTitle())).append(" | ")
                        .append(counts(0, stepTasks, today)).append('\n');
                allowed.put("STEP-" + step.getId(), new StreamlineResponses.Candidate("STEP", step.getId(), step.getCode(), step.getTitle(), "AI", null));
                lines++;
            }
        }
        return new PickContext(out.toString(), allowed);
    }

    private static String counts(int openSteps, List<TaskItem> openTasks, java.time.LocalDate today) {
        long overdue = openTasks.stream().filter(task -> task.getDueDate() != null && task.getDueDate().isBefore(today)).count();
        long waiting = openTasks.stream().filter(task -> task.getStatus() == WorkStatus.WAITING).count();
        long blocked = openTasks.stream().filter(task -> task.getStatus() == WorkStatus.BLOCKED).count();
        return (openSteps > 0 ? openSteps + " open steps, " : "") + openTasks.size() + " open tasks, " + overdue + " overdue, "
                + waiting + " waiting, " + blocked + " blocked";
    }


    // ---------- Building the response ----------

    private StreamlineResponses.Review view(StreamlineReview review) {
        List<StreamlineItem> all = items.findByReview_IdOrderByIdAsc(review.getId());
        noteRestores(all);
        return build(review, all);
    }

    /** D-27-4: an item cut in the review and restored elsewhere within 90 days counts as added back. */
    private void noteRestores(List<StreamlineItem> all) {
        Instant now = Instant.now(clock);
        for (StreamlineItem item : all) {
            if (item.getArchivedAt() != null && item.getRestoredAt() == null && !item.isArchivedWithParent()
                    && !item.targetArchived() && Duration.between(item.getArchivedAt(), now).compareTo(ADD_BACK_WINDOW) <= 0) {
                item.setRestoredAt(now);
            }
        }
    }

    private StreamlineResponses.Review build(StreamlineReview review, List<StreamlineItem> all) {
        Long userId = review.getUser().getId();
        List<TaskItem> tasks = scopeTasks(all);
        Map<Long, CycleTimeCalculator.Delays> delays = cycleTime.delays(userId, tasks);
        Set<String> repeated = repeatedTitles(userId);
        Map<Long, Long> ruleBySource = new HashMap<>();
        for (TaskRepeatRule rule : repeatRules.findByUser_IdOrderByIdAsc(userId)) {
            if (rule.isActive() && rule.getSourceTask() != null) {
                ruleBySource.put(rule.getSourceTask().getId(), rule.getId());
            }
        }
        List<StreamlineResponses.Item> itemViews = all.stream().map(item -> itemView(item, delays, repeated, ruleBySource)).toList();
        BigDecimal waiting = delays.values().stream().map(CycleTimeCalculator.Delays::waitingDays).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal blocked = delays.values().stream().map(CycleTimeCalculator.Delays::blockedDays).reduce(BigDecimal.ZERO, BigDecimal::add);
        List<StreamlineResponses.Stage> stages = new ArrayList<>();
        for (int stage = 1; stage <= StreamlineReview.STAGES; stage++) {
            stages.add(new StreamlineResponses.Stage(stage, review.stageState(stage), review.stageReason(stage), review.isUnlocked(stage)));
        }
        BigDecimal cycleNow = currentCycle(review, tasks);
        return new StreamlineResponses.Review(review.getId(), review.getCode(), review.getGoal() != null ? "GOAL" : "STEP",
                review.getGoal() == null ? null : review.getGoal().getId(), review.getStep() == null ? null : review.getStep().getId(),
                review.getGoal() != null ? review.getGoal().getCode() : review.getStep().getCode(), review.anchorTitle(),
                review.getStatus(), review.getCurrentStage(), stages, review.getBaselineCycleDays(), review.getTargetCycleDays(),
                cycleNow, waiting, blocked, review.getStartedAt(), review.getCompletedAt(), itemViews, summary(review, all, cycleNow));
    }

    private StreamlineResponses.Item itemView(StreamlineItem item, Map<Long, CycleTimeCalculator.Delays> delays, Set<String> repeated,
                                              Map<Long, Long> ruleBySource) {
        TaskItem task = item.getTask();
        CycleTimeCalculator.Delays delay = task == null ? null : delays.getOrDefault(task.getId(), CycleTimeCalculator.Delays.NONE);
        List<SpeedLever> levers = item.getSpeedLevers() == null || item.getSpeedLevers().isBlank() ? List.of()
                : Arrays.stream(item.getSpeedLevers().split(",")).map(SpeedLever::valueOf).toList();
        return new StreamlineResponses.Item(item.getId(), item.isStep() ? "STEP" : "TASK",
                item.isStep() ? item.getStep().getId() : null, task == null ? null : task.getId(),
                task == null ? null : task.getStep().getId(), item.code(), item.title(),
                item.isStep() ? item.getStep().getStatus() : task.getStatus(), item.targetArchived(),
                item.isStep() ? item.getStep().getEstimatedHours() : task.getEstimatedHours(),
                item.getRequirementOwner(), item.getRequirementSource(), item.getPartner() == null ? null : item.getPartner().getId(),
                item.getWhyNeeded(), item.getVerdict(), item.getDecision(), item.getMergeInto() == null ? null : item.getMergeInto().getId(),
                item.getKeepReason(), item.isSimplified(), item.getSimplifyNote(), levers, item.getAutomation(),
                item.getDelegatePartner() == null ? null : item.getDelegatePartner().getId(), item.isArchivedWithParent(),
                item.getArchivedAt(), item.getRestoredAt(), task == null ? null : task.cycleTimeDays(),
                delay == null ? null : delay.waitingDays(), delay == null ? null : delay.blockedDays(),
                task != null && repeated.contains(normalise(task.getTitle())), task == null ? null : ruleBySource.get(task.getId()));
    }

    private StreamlineResponses.Summary summary(StreamlineReview review, List<StreamlineItem> all, @Nullable BigDecimal cycleNow) {
        long deleted = all.stream().filter(item -> item.getDecision() == StreamlineDecision.DELETE).count();
        long merged = all.stream().filter(item -> item.getDecision() == StreamlineDecision.MERGE).count();
        long kept = all.stream().filter(item -> item.getDecision() == StreamlineDecision.KEEP).count();
        long addedBack = all.stream().filter(item -> item.getRestoredAt() != null && item.getDecision() != StreamlineDecision.KEEP).count();
        Counts now = counts(all);
        boolean completed = review.getStatus() == StreamlineStatus.COMPLETED;
        return new StreamlineResponses.Summary(deleted, kept, merged, addedBack,
                review.getBaselineSteps(), completed && review.getResultSteps() != null ? review.getResultSteps() : now.steps(),
                review.getBaselineTasks(), completed && review.getResultTasks() != null ? review.getResultTasks() : now.tasks(),
                review.getBaselineHours(), completed && review.getResultHours() != null ? review.getResultHours() : now.hours(),
                review.getBaselineCycleDays(), review.getTargetCycleDays(), cycleNow,
                all.stream().filter(item -> item.getAutomation() == AutomationChoice.REPEAT).count(),
                all.stream().filter(item -> item.getAutomation() == AutomationChoice.REMIND).count(),
                all.stream().filter(item -> item.getAutomation() == AutomationChoice.DELEGATE).count(),
                completed && deleted + merged > 0 && addedBack == 0);
    }

    /** Before completion: the median of the scope now; after: of tasks completed since the review finished. */
    @Nullable
    private BigDecimal currentCycle(StreamlineReview review, List<TaskItem> tasks) {
        if (review.getCompletedAt() == null) {
            return cycleTime.medianCycleDays(tasks);
        }
        return cycleTime.medianCycleDays(tasks.stream()
                .filter(task -> task.getCompletedAt() != null && task.getCompletedAt().isAfter(review.getCompletedAt())).toList());
    }

    private record Counts(int steps, int tasks, BigDecimal hours) {
    }

    private static Counts counts(List<StreamlineItem> all) {
        List<VisionStep> steps = all.stream().filter(StreamlineItem::isStep).map(StreamlineItem::getStep).filter(step -> !step.isArchived()).toList();
        List<TaskItem> tasks = all.stream().filter(item -> !item.isStep()).map(StreamlineItem::getTask).filter(task -> !task.isArchived()).toList();
        return new Counts(steps.size(), tasks.size(), hours(steps, tasks));
    }

    /** Estimated hours: each task's estimate, plus the step's own estimate when the step has no open tasks. */
    private static BigDecimal hours(List<VisionStep> steps, List<TaskItem> tasks) {
        BigDecimal total = tasks.stream().map(TaskItem::getEstimatedHours).filter(Objects::nonNull).reduce(BigDecimal.ZERO, BigDecimal::add);
        Set<Long> stepsWithTasks = tasks.stream().map(task -> task.getStep().getId()).collect(Collectors.toSet());
        for (VisionStep step : steps) {
            if (!stepsWithTasks.contains(step.getId()) && step.getEstimatedHours() != null) {
                total = total.add(step.getEstimatedHours());
            }
        }
        return total.setScale(2, java.math.RoundingMode.HALF_UP);
    }

    private static List<TaskItem> scopeTasks(List<StreamlineItem> all) {
        return all.stream().filter(item -> !item.isStep()).map(StreamlineItem::getTask).toList();
    }

    /** Titles that appear on at least two of the user's tasks: stage 5 automation candidates. */
    private Set<String> repeatedTitles(Long userId) {
        Map<String, Long> counts = taskRepository.findByUser_Id(userId).stream()
                .collect(Collectors.groupingBy(task -> normalise(task.getTitle()), Collectors.counting()));
        return counts.entrySet().stream().filter(entry -> entry.getValue() >= 2 && !entry.getKey().isBlank())
                .map(Map.Entry::getKey).collect(Collectors.toSet());
    }

    /** Lower case, digits and punctuation removed: "Weekly report 12" and "weekly report 13" match. */
    static String normalise(@Nullable String title) {
        if (title == null) {
            return "";
        }
        return title.toLowerCase(Locale.ROOT).replaceAll("[^\\p{L}\\s]", " ").replaceAll("\\s+", " ").strip();
    }

    private StreamlineResponses.ListItem listItem(StreamlineReview review) {
        List<StreamlineItem> all = items.findByReview_IdOrderByIdAsc(review.getId());
        long deleted = all.stream().filter(item -> item.getDecision() == StreamlineDecision.DELETE || item.getDecision() == StreamlineDecision.MERGE).count();
        long addedBack = all.stream().filter(item -> item.getRestoredAt() != null && item.getDecision() != StreamlineDecision.KEEP).count();
        Counts now = counts(all);
        int stepsAfter = review.getResultSteps() != null ? review.getResultSteps() : now.steps();
        int tasksAfter = review.getResultTasks() != null ? review.getResultTasks() : now.tasks();
        return new StreamlineResponses.ListItem(review.getId(), review.getCode(), review.getGoal() != null ? "GOAL" : "STEP",
                review.getGoal() == null ? null : review.getGoal().getId(), review.getStep() == null ? null : review.getStep().getId(),
                review.anchorTitle(), review.getStatus(), review.getCurrentStage(), review.getStartedAt(), review.getCompletedAt(),
                deleted, addedBack, review.getBaselineSteps(), stepsAfter, review.getBaselineTasks(), tasksAfter);
    }

    // ---------- Export ----------

    /** SR-FR-16: one row per item of each of the user's reviews, for the "Streamline" sheet. */
    @Transactional(readOnly = true)
    public List<List<Object>> exportRows() {
        List<List<Object>> rows = new ArrayList<>();
        for (StreamlineReview review : reviews.findByUser_IdOrderByStartedAtDescIdDesc(lookup.userId())) {
            for (StreamlineItem item : items.findByReview_IdOrderByIdAsc(review.getId())) {
                rows.add(List.of(review.getCode(), review.anchorTitle(), review.getStatus().name(), item.code(), item.title(),
                        value(item.getRequirementSource()), value(item.getVerdict()), value(item.getDecision()),
                        item.isSimplified() ? "Yes" : "", value(item.getSpeedLevers()), value(item.getAutomation()),
                        value(item.getArchivedAt()), value(item.getRestoredAt())));
            }
        }
        return rows;
    }

    private static Object value(@Nullable Object value) {
        return value == null ? "" : value.toString();
    }

    // ---------- Helpers ----------

    /** BR-27-1: the caller's own review, or 404 (and a NOT_OWNER security event for someone else's). */
    private StreamlineReview own(Long id) {
        StreamlineReview review = reviews.findById(id).orElseThrow(() -> new ResourceNotFoundException(TARGET + " not found: " + id));
        if (!review.getUser().getId().equals(lookup.userId())) {
            if (securityEvents != null) {
                securityEvents.record(SecurityEventType.NOT_OWNER, SecurityOutcome.DENIED, TARGET, id, null);
            }
            throw new ResourceNotFoundException(TARGET + " not found: " + id);
        }
        return review;
    }

    private StreamlineItem item(StreamlineReview review, Long itemId) {
        return items.findByIdAndReview_Id(itemId, review.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Item not found in this review: " + itemId));
    }

    @Nullable
    private static String blankToNull(@Nullable String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }

    @Nullable
    private static BigDecimal sum(@Nullable BigDecimal a, @Nullable BigDecimal b) {
        if (a == null) {
            return b;
        }
        return b == null ? a : a.add(b);
    }

    private static String appendLine(@Nullable String text, String line) {
        String joined = text == null || text.isBlank() ? line : text.strip() + "\n\n" + line;
        return joined.length() <= 3000 ? joined : text;
    }
}
