package com.visionmapping.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.visionmapping.dto.request.StreamlineRequests;
import com.visionmapping.dto.response.StreamlineResponses;
import com.visionmapping.entity.BaseAuditableEntity;
import com.visionmapping.entity.StreamlineApplyBatch;
import com.visionmapping.entity.StreamlineApplyBatchEntry;
import com.visionmapping.entity.StreamlineItem;
import com.visionmapping.entity.StreamlineReview;
import com.visionmapping.entity.TaskItem;
import com.visionmapping.entity.TaskRepeatRule;
import com.visionmapping.entity.VisionStep;
import com.visionmapping.entity.enums.AutomationChoice;
import com.visionmapping.entity.enums.RepeatCadence;
import com.visionmapping.entity.enums.RequirementSource;
import com.visionmapping.entity.enums.StreamlineDecision;
import com.visionmapping.entity.enums.StreamlineStatus;
import com.visionmapping.entity.enums.StreamlineVerdict;
import com.visionmapping.entity.enums.WorkStatus;
import com.visionmapping.exception.BusinessRuleException;
import com.visionmapping.exception.ResourceNotFoundException;
import com.visionmapping.repository.PartnerRepository;
import com.visionmapping.repository.StreamlineApplyBatchEntryRepository;
import com.visionmapping.repository.StreamlineApplyBatchRepository;
import com.visionmapping.repository.StreamlineItemRepository;
import com.visionmapping.repository.StreamlineReviewRepository;
import com.visionmapping.repository.TaskItemRepository;
import com.visionmapping.repository.TaskRepeatRuleRepository;
import com.visionmapping.service.support.EntityLookup;
import com.visionmapping.service.support.ObstacleAnchorSync;
import com.visionmapping.service.support.ProgressCalculator;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * V32 phase 2 (D-32-4 to D-32-6, SF-FR-05 to SF-FR-07): Apply selected and
 * Apply all as one batch on the server, and Undo for the whole batch.
 *
 * <p>Each action runs in its own transaction through the normal review action
 * (so every BRD 27 rule still applies), after the server records what it is
 * about to change. The first failure stops the run; the actions already
 * applied stay applied (D-30-6). Undo reverses the batch in reverse order while
 * its stage is still current and for 24 hours, and leaves alone any item
 * changed again after the batch (BR-32-6).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class StreamlineBatchService {

    static final Duration UNDO_WINDOW = Duration.ofHours(24);

    private final EntityLookup lookup;
    private final StreamlineReviewService reviewService;
    private final StreamlineReviewRepository reviews;
    private final StreamlineItemRepository items;
    private final StreamlineApplyBatchRepository batches;
    private final StreamlineApplyBatchEntryRepository entries;
    private final TaskItemRepository taskRepository;
    private final TaskRepeatRuleRepository repeatRules;
    private final PartnerRepository partners;
    private final VisionStepService stepService;
    private final TaskItemService taskService;
    private final ProgressCalculator progress;
    private final ObstacleAnchorSync obstacleAnchorSync;
    private final ObjectMapper objectMapper;
    private final PlatformTransactionManager transactionManager;
    private final Clock clock;

    /**
     * What an action is about to change, recorded before the change. Item
     * fields always; the step or task's own fields for SIMPLIFY; the target's
     * fields and the moved tasks for MERGE; the repeat rule for REPEAT.
     */
    public record Snapshot(
            String requirementOwner, RequirementSource requirementSource, Long partnerId, String whyNeeded,
            StreamlineVerdict verdict, StreamlineDecision decision, Long mergeIntoItemId, String keepReason,
            boolean simplified, String simplifyNote, String speedLevers, AutomationChoice automation, Long delegatePartnerId,
            Instant archivedAt, Instant restoredAt, boolean archivedWithParent,
            String title, String description, String owner, BigDecimal estimatedHours, BigDecimal actualHours, Boolean complex,
            Long mergeTargetItemId, String mergeTargetDescription, BigDecimal mergeTargetEstimatedHours,
            BigDecimal mergeTargetActualHours, List<Long> movedTaskIds,
            boolean repeatRuleExisted, Long repeatRuleId, RepeatCadence repeatCadence, LocalDate repeatNextDue,
            Long taskRepeatRuleId, RepeatCadence remindCadence, Integer remindDay) {

        public Snapshot {
            movedTaskIds = com.visionmapping.util.Copies.list(movedTaskIds);
        }

        Snapshot withRepeatRuleId(Long ruleId) {
            return new Snapshot(requirementOwner, requirementSource, partnerId, whyNeeded, verdict, decision, mergeIntoItemId,
                    keepReason, simplified, simplifyNote, speedLevers, automation, delegatePartnerId, archivedAt, restoredAt,
                    archivedWithParent, title, description, owner, estimatedHours, actualHours, complex, mergeTargetItemId,
                    mergeTargetDescription, mergeTargetEstimatedHours, mergeTargetActualHours, movedTaskIds, repeatRuleExisted,
                    ruleId, repeatCadence, repeatNextDue, taskRepeatRuleId, remindCadence, remindDay);
        }
    }

    // ---------- Apply ----------

    /** D-32-4: applies the actions one after another; stops at the first failure. */
    public StreamlineResponses.BatchResult apply(Long reviewId, StreamlineRequests.ApplyBatch request) {
        // Ownership first: someone else's review answers 404.
        StreamlineResponses.Review current = reviewService.get(reviewId);
        if (current.status() != StreamlineStatus.OPEN) {
            throw new BusinessRuleException("This review is closed.");
        }
        if (request.stage() != current.currentStage()) {
            throw new BusinessRuleException("These suggestions are for a stage that is no longer the current one.");
        }
        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        Long userId = lookup.userId();
        Long batchId = tx.execute(status -> batches.save(StreamlineApplyBatch.builder()
                .user(lookup.currentUser())
                .review(reviews.getReferenceById(reviewId))
                .stage(request.stage())
                .total(request.actions().size())
                .build()).getId());
        int applied = 0;
        Integer failedAt = null;
        String error = null;
        for (int index = 0; index < request.actions().size(); index++) {
            StreamlineRequests.BatchAction action = request.actions().get(index);
            int position = index + 1;
            try {
                tx.executeWithoutResult(status -> applyOne(batchId, reviewId, userId, position, action));
                applied++;
            } catch (BusinessRuleException | ResourceNotFoundException exception) {
                failedAt = position;
                error = exception.getMessage();
                break;
            } catch (RuntimeException exception) {
                log.warn("Streamline batch {} stopped at action {}", batchId, position, exception);
                failedAt = position;
                error = "That did not work. Try again.";
                break;
            }
        }
        int done = applied;
        Integer stoppedAt = failedAt;
        String reason = error;
        tx.executeWithoutResult(status -> finish(batchId, done, stoppedAt, reason));
        return new StreamlineResponses.BatchResult(done == 0 ? null : batchId, done, request.actions().size(), stoppedAt, reason,
                reviewService.get(reviewId));
    }

    private void applyOne(Long batchId, Long reviewId, Long userId, int position, StreamlineRequests.BatchAction action) {
        StreamlineItem item = items.findByIdAndReview_Id(action.itemId(), reviewId)
                .orElseThrow(() -> new ResourceNotFoundException("Item not found in this review: " + action.itemId()));
        Snapshot before = capture(item, action, userId);
        switch (action.type()) {
            case "UPDATE" -> reviewService.updateItem(reviewId, item.getId(), require(action.update(), "UPDATE"));
            case "DELETE" -> reviewService.deleteItem(reviewId, item.getId(), new StreamlineRequests.Delete(false));
            case "MERGE" -> reviewService.mergeItem(reviewId, item.getId(),
                    new StreamlineRequests.Merge(require(action.targetItemId(), "MERGE"), false));
            case "SIMPLIFY" -> reviewService.simplify(reviewId, item.getId(), require(action.simplify(), "SIMPLIFY"));
            case "REPEAT" -> {
                reviewService.repeat(reviewId, item.getId(), new StreamlineRequests.Repeat(require(action.cadence(), "REPEAT"), null));
                // A new rule's id is only known now; undo stops it.
                before = before.withRepeatRuleId(repeatRules.findFirstBySourceTask_IdAndUser_IdAndActiveTrue(item.getTask().getId(), userId)
                        .map(TaskRepeatRule::getId).orElse(null));
            }
            default -> throw new BusinessRuleException("Unknown action: " + action.type());
        }
        entries.save(StreamlineApplyBatchEntry.builder()
                .batch(batches.getReferenceById(batchId))
                .position(position)
                .action(action.type())
                .item(item)
                .beforeState(write(before))
                .build());
    }

    private static <T> T require(@Nullable T value, String type) {
        if (value == null) {
            throw new BusinessRuleException("A " + type + " action is missing its details.");
        }
        return value;
    }

    private Snapshot capture(StreamlineItem item, StreamlineRequests.BatchAction action, Long userId) {
        String title;
        String description;
        String owner;
        BigDecimal estimated;
        BigDecimal actual = null;
        Boolean complex = null;
        if (item.isStep()) {
            VisionStep step = item.getStep();
            title = step.getTitle();
            description = step.getDescription();
            owner = step.getOwner();
            estimated = step.getEstimatedHours();
            complex = step.isComplex();
        } else {
            TaskItem task = item.getTask();
            title = task.getTitle();
            description = task.getDescription();
            owner = task.getOwner();
            estimated = task.getEstimatedHours();
            actual = task.getActualHours();
        }
        Long mergeTarget = null;
        String mergeDescription = null;
        BigDecimal mergeEstimated = null;
        BigDecimal mergeActual = null;
        List<Long> moved = List.of();
        if ("MERGE".equals(action.type()) && action.targetItemId() != null) {
            Optional<StreamlineItem> target = items.findByIdAndReview_Id(action.targetItemId(), item.getReview().getId());
            if (target.isPresent()) {
                mergeTarget = target.get().getId();
                if (target.get().isStep()) {
                    mergeDescription = target.get().getStep().getDescription();
                    if (item.isStep()) {
                        moved = taskRepository.findByStep_IdAndUser_IdAndArchivedFalse(item.getStep().getId(), userId).stream()
                                .filter(task -> task.getStatus() != WorkStatus.COMPLETED)
                                .map(TaskItem::getId)
                                .toList();
                    }
                } else {
                    TaskItem to = target.get().getTask();
                    mergeDescription = to.getDescription();
                    mergeEstimated = to.getEstimatedHours();
                    mergeActual = to.getActualHours();
                }
            }
        }
        boolean ruleExisted = false;
        Long ruleId = null;
        RepeatCadence cadence = null;
        LocalDate nextDue = null;
        Long taskRule = null;
        if ("REPEAT".equals(action.type()) && !item.isStep()) {
            taskRule = item.getTask().getRepeatRuleId();
            Optional<TaskRepeatRule> existing = repeatRules.findFirstBySourceTask_IdAndUser_IdAndActiveTrue(item.getTask().getId(), userId);
            if (existing.isPresent()) {
                ruleExisted = true;
                ruleId = existing.get().getId();
                cadence = existing.get().getCadence();
                nextDue = existing.get().getNextDue();
            }
        }
        return new Snapshot(item.getRequirementOwner(), item.getRequirementSource(),
                item.getPartner() == null ? null : item.getPartner().getId(), item.getWhyNeeded(), item.getVerdict(),
                item.getDecision(), item.getMergeInto() == null ? null : item.getMergeInto().getId(), item.getKeepReason(),
                item.isSimplified(), item.getSimplifyNote(), item.getSpeedLevers(), item.getAutomation(),
                item.getDelegatePartner() == null ? null : item.getDelegatePartner().getId(), item.getArchivedAt(),
                item.getRestoredAt(), item.isArchivedWithParent(), title, description, owner, estimated, actual, complex,
                mergeTarget, mergeDescription, mergeEstimated, mergeActual, moved, ruleExisted, ruleId, cadence, nextDue, taskRule,
                item.getRemindCadence(), item.getRemindDay());
    }

    /** Records the counts, and each changed record's update time as the batch left it (BR-32-6). */
    private void finish(Long batchId, int applied, @Nullable Integer failedAt, @Nullable String error) {
        StreamlineApplyBatch batch = batches.findById(batchId).orElseThrow();
        if (applied == 0) {
            batches.delete(batch);
            return;
        }
        batch.setAppliedCount(applied);
        batch.setFailedAtPosition(failedAt);
        batch.setError(error == null ? null : error.substring(0, Math.min(error.length(), 500)));
        for (StreamlineApplyBatchEntry entry : entries.findByBatch_IdOrderByPositionAsc(batchId)) {
            StreamlineItem item = entry.getItem();
            entry.setItemUpdatedAt(item.getUpdatedAt());
            entry.setTargetUpdatedAt(target(item).getUpdatedAt());
            mergeTarget(entry).ifPresent(other -> entry.setOtherUpdatedAt(target(other).getUpdatedAt()));
        }
    }

    // ---------- Undo ----------

    /** D-32-5: reverses the batch in reverse order; items changed since are left as they are. */
    @Transactional
    public StreamlineResponses.UndoResult undo(Long reviewId, Long batchId) {
        reviewService.get(reviewId);
        StreamlineApplyBatch batch = batches.findByIdAndReview_IdAndUser_Id(batchId, reviewId, lookup.userId())
                .orElseThrow(() -> new ResourceNotFoundException("Change not found"));
        StreamlineReview review = batch.getReview();
        if (batch.getUndoneAt() != null) {
            throw new BusinessRuleException("These changes were already undone.");
        }
        if (!canUndo(batch, review)) {
            throw new BusinessRuleException("These changes can no longer be undone. Undo works until the stage is finished, for up to 24 hours.");
        }
        List<StreamlineApplyBatchEntry> list = entries.findByBatch_IdOrderByPositionDesc(batchId);
        // Decided before anything is reverted, so this undo's own changes never count as "changed since".
        Set<Long> changed = new HashSet<>();
        for (StreamlineApplyBatchEntry entry : list) {
            if (changedSince(entry)) {
                changed.add(entry.getItem().getId());
            }
        }
        Map<Long, StreamlineResponses.UndoSkipped> skipped = new LinkedHashMap<>();
        int undone = 0;
        for (StreamlineApplyBatchEntry entry : list) {
            StreamlineItem item = entry.getItem();
            if (changed.contains(item.getId())) {
                skipped.putIfAbsent(item.getId(), new StreamlineResponses.UndoSkipped(item.code(), "Changed since, left as it is"));
                continue;
            }
            revert(review, entry, read(entry.getBeforeState()));
            undone++;
        }
        batch.setUndoneAt(Instant.now(clock));
        return new StreamlineResponses.UndoResult(undone, new ArrayList<>(skipped.values()), reviewService.get(reviewId));
    }

    /** SF-FR-07: the latest batches on one stage, newest first. */
    @Transactional(readOnly = true)
    public List<StreamlineResponses.BatchView> recent(Long reviewId, @Nullable Integer stage) {
        StreamlineResponses.Review review = reviewService.get(reviewId);
        int chosen = stage == null ? review.currentStage() : stage;
        return batches.findTop10ByReview_IdAndStageAndUser_IdOrderByCreatedAtDescIdDesc(reviewId, chosen, lookup.userId()).stream()
                .map(batch -> new StreamlineResponses.BatchView(batch.getId(), batch.getStage(), batch.getAppliedCount(),
                        batch.getTotal(), batch.getFailedAtPosition(), batch.getCreatedAt(), batch.getUndoneAt(),
                        batch.getUndoneAt() == null && canUndo(batch, batch.getReview()),
                        entries.findByBatch_IdOrderByPositionAsc(batch.getId()).stream()
                                .map(entry -> new StreamlineResponses.BatchChange(entry.getItem().code(), entry.getAction()))
                                .toList()))
                .toList();
    }

    /** BR-32-5: while the review is open on the batch's stage, and for 24 hours. */
    private boolean canUndo(StreamlineApplyBatch batch, StreamlineReview review) {
        return review.getStatus() == StreamlineStatus.OPEN
                && review.getCurrentStage() == batch.getStage()
                && batch.getCreatedAt().plus(UNDO_WINDOW).isAfter(Instant.now(clock));
    }

    /** BR-32-6: the item, its step or task, or a merge target was saved after the batch. */
    private boolean changedSince(StreamlineApplyBatchEntry entry) {
        StreamlineItem item = entry.getItem();
        if (after(item.getUpdatedAt(), entry.getItemUpdatedAt()) || after(target(item).getUpdatedAt(), entry.getTargetUpdatedAt())) {
            return true;
        }
        return mergeTarget(entry).map(other -> after(target(other).getUpdatedAt(), entry.getOtherUpdatedAt())).orElse(false);
    }

    private static boolean after(@Nullable Instant now, @Nullable Instant recorded) {
        // No recorded time means the batch did not finish cleanly: leave the record alone.
        return recorded == null || (now != null && now.isAfter(recorded));
    }

    private static BaseAuditableEntity target(StreamlineItem item) {
        return item.isStep() ? item.getStep() : item.getTask();
    }

    private Optional<StreamlineItem> mergeTarget(StreamlineApplyBatchEntry entry) {
        if (!"MERGE".equals(entry.getAction())) {
            return Optional.empty();
        }
        Long targetId = read(entry.getBeforeState()).mergeTargetItemId();
        return targetId == null ? Optional.empty() : items.findById(targetId);
    }

    private void revert(StreamlineReview review, StreamlineApplyBatchEntry entry, Snapshot before) {
        StreamlineItem item = entry.getItem();
        switch (entry.getAction()) {
            case "DELETE" -> unarchive(review, item);
            case "MERGE" -> {
                unarchive(review, item);
                revertMergeTarget(item, before);
            }
            case "SIMPLIFY" -> revertTarget(item, before);
            case "REPEAT" -> revertRepeat(item, before);
            default -> {
                // UPDATE changes item fields only, restored below.
            }
        }
        restoreItemFields(item, before);
    }

    /** Brings back an item archived by the batch, and a step's tasks archived with it (as restoreItem does). */
    private void unarchive(StreamlineReview review, StreamlineItem item) {
        if (item.isStep()) {
            Long stepId = item.getStep().getId();
            if (item.getStep().isArchived()) {
                stepService.restoreStep(stepId);
            }
            for (StreamlineItem child : items.findByReview_IdOrderByIdAsc(review.getId())) {
                if (child.isArchivedWithParent() && !child.isStep() && child.getTask().getStep().getId().equals(stepId)) {
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
    }

    private void revertMergeTarget(StreamlineItem source, Snapshot before) {
        if (before.mergeTargetItemId() == null) {
            return;
        }
        items.findById(before.mergeTargetItemId()).ifPresent(target -> {
            if (target.isStep()) {
                VisionStep to = target.getStep();
                to.setDescription(before.mergeTargetDescription());
                if (source.isStep()) {
                    VisionStep from = source.getStep();
                    for (Long taskId : Optional.ofNullable(before.movedTaskIds()).orElse(List.of())) {
                        taskRepository.findById(taskId).filter(task -> task.getStep().getId().equals(to.getId())).ifPresent(task -> {
                            task.setStep(from);
                            obstacleAnchorSync.afterTaskMoved(task);
                        });
                    }
                    progress.recalculateStep(from);
                }
                progress.recalculateStep(to);
            } else {
                TaskItem to = target.getTask();
                to.setDescription(before.mergeTargetDescription());
                to.setEstimatedHours(before.mergeTargetEstimatedHours());
                to.setActualHours(before.mergeTargetActualHours());
            }
        });
    }

    private static void revertTarget(StreamlineItem item, Snapshot before) {
        if (item.isStep()) {
            VisionStep step = item.getStep();
            step.setTitle(before.title());
            step.setDescription(before.description());
            step.setOwner(before.owner());
            step.setEstimatedHours(before.estimatedHours());
            step.setComplex(Boolean.TRUE.equals(before.complex()));
        } else {
            TaskItem task = item.getTask();
            task.setTitle(before.title());
            task.setDescription(before.description());
            task.setOwner(before.owner());
            task.setEstimatedHours(before.estimatedHours());
        }
    }

    /** A rule the batch created is stopped (never deleted, BR-12); a rule it changed gets its old cadence back. */
    private void revertRepeat(StreamlineItem item, Snapshot before) {
        if (before.repeatRuleId() == null) {
            return;
        }
        repeatRules.findById(before.repeatRuleId()).ifPresent(rule -> {
            if (before.repeatRuleExisted()) {
                rule.setCadence(before.repeatCadence());
                rule.setNextDue(before.repeatNextDue());
            } else {
                rule.setActive(false);
                if (!item.isStep()) {
                    item.getTask().setRepeatRuleId(before.taskRepeatRuleId());
                }
            }
        });
    }

    private void restoreItemFields(StreamlineItem item, Snapshot before) {
        item.setRequirementOwner(before.requirementOwner());
        item.setRequirementSource(before.requirementSource());
        item.setPartner(before.partnerId() == null ? null : partners.getReferenceById(before.partnerId()));
        item.setWhyNeeded(before.whyNeeded());
        item.setVerdict(before.verdict());
        item.setDecision(before.decision());
        item.setMergeInto(before.mergeIntoItemId() == null ? null : items.getReferenceById(before.mergeIntoItemId()));
        item.setKeepReason(before.keepReason());
        item.setSimplified(before.simplified());
        item.setSimplifyNote(before.simplifyNote());
        item.setSpeedLevers(before.speedLevers());
        item.setAutomation(before.automation());
        item.setDelegatePartner(before.delegatePartnerId() == null ? null : partners.getReferenceById(before.delegatePartnerId()));
        item.setArchivedAt(before.archivedAt());
        item.setRestoredAt(before.restoredAt());
        item.setArchivedWithParent(before.archivedWithParent());
        item.setRemindCadence(before.remindCadence());
        item.setRemindDay(before.remindDay());
    }

    private String write(Snapshot snapshot) {
        try {
            return objectMapper.writeValueAsString(snapshot);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Could not record the change", exception);
        }
    }

    private Snapshot read(String json) {
        try {
            return objectMapper.readValue(json, Snapshot.class);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Could not read a recorded change", exception);
        }
    }
}
