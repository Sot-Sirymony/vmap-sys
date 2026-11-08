package com.visionmapping.service.ai;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.visionmapping.dto.request.GoalRequest;
import com.visionmapping.dto.request.PlanDraftContent;
import com.visionmapping.dto.request.ObstacleRequest;
import com.visionmapping.dto.request.PartnerRequest;
import com.visionmapping.dto.request.PlanDraftContent.DraftGoal;
import com.visionmapping.dto.request.PlanDraftContent.DraftHelper;
import com.visionmapping.dto.request.PlanDraftContent.DraftRisk;
import com.visionmapping.dto.request.PlanDraftContent.DraftStep;
import com.visionmapping.dto.request.PlanDraftContent.DraftTask;
import com.visionmapping.dto.request.PlanDraftRequest;
import com.visionmapping.dto.request.TaskItemRequest;
import com.visionmapping.dto.request.VisionStepRequest;
import com.visionmapping.dto.response.AiPlanResponses.PlanAddResult;
import com.visionmapping.dto.response.PlanDraftResponse;
import com.visionmapping.entity.AppUser;
import com.visionmapping.entity.Dream;
import com.visionmapping.entity.Goal;
import com.visionmapping.entity.PlanBuilderDraft;
import com.visionmapping.entity.PlanBuilderFinishedPlan;
import com.visionmapping.entity.VisionStep;
import com.visionmapping.entity.enums.ObstacleStatus;
import com.visionmapping.entity.enums.ObstacleType;
import com.visionmapping.entity.enums.PartnerStatus;
import com.visionmapping.entity.enums.PartnerSupportType;
import com.visionmapping.entity.enums.Priority;
import com.visionmapping.entity.enums.Severity;
import com.visionmapping.entity.enums.WorkStatus;
import com.visionmapping.exception.BusinessRuleException;
import com.visionmapping.exception.ResourceNotFoundException;
import com.visionmapping.repository.PlanBuilderDraftRepository;
import com.visionmapping.repository.VisionStepRepository;
import com.visionmapping.service.GoalService;
import com.visionmapping.service.ObstacleService;
import com.visionmapping.service.PartnerService;
import com.visionmapping.service.TaskItemService;
import com.visionmapping.service.VisionStepService;
import com.visionmapping.service.support.EntityLookup;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.time.Instant;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.lang.Nullable;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * V11 Phase 2: the Build my plan draft for one dream (PB-FR-30) and adding it
 * as real plan records in one transaction (PB-FR-31). A draft is private to
 * its owner (PB-NFR-09), is never read as plan data (PB-FR-33), and is gone
 * 14 days after its last change. Every lookup is by the caller's own dream, so
 * another user's dream answers 404 (SEC-01-02).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PlanBuilderDraftService {

    static final Duration KEEP_FOR = Duration.ofDays(14);

    private final PlanBuilderDraftRepository repository;
    private final VisionStepRepository stepRepository;
    private final EntityLookup lookup;
    private final GoalService goalService;
    private final VisionStepService stepService;
    private final TaskItemService taskService;
    private final AiPlanFeedbackService feedback;
    private final PartnerService partnerService;
    private final ObstacleService obstacleService;
    private final ObjectMapper objectMapper;
    private final Validator validator;
    private final Clock clock;
    private final com.visionmapping.repository.PlanBuilderFinishedPlanRepository finishedPlans;
    private final com.visionmapping.repository.AiUsageLogRepository usageLogRepository;

    @Transactional(readOnly = true)
    public Optional<PlanDraftResponse> get(Long dreamId) {
        Dream dream = lookup.dream(dreamId);
        return repository.findByUser_IdAndDream_Id(lookup.userId(), dream.getId())
                .filter(draft -> draft.getExpiresAt().isAfter(clock.instant()))
                .map(this::toResponse);
    }

    /** V11 Phase 6: the caller's saved draft for a dream, for the draft check; 404 when there is none. */
    @Transactional(readOnly = true)
    public PlanDraftContent stored(Long dreamId) {
        Dream dream = lookup.dream(dreamId);
        return repository.findByUser_IdAndDream_Id(lookup.userId(), dream.getId())
                .filter(draft -> draft.getExpiresAt().isAfter(clock.instant()))
                .map(draft -> read(draft.getContent()))
                .orElseThrow(() -> new ResourceNotFoundException("There is no plan draft for this dream."));
    }

    @Transactional
    public PlanDraftResponse save(Long dreamId, PlanDraftRequest request) {
        Dream dream = openDream(dreamId);
        AppUser user = lookup.currentUser();
        PlanBuilderDraft draft = repository.findByUser_IdAndDream_Id(user.getId(), dream.getId())
                .orElseGet(() -> PlanBuilderDraft.builder().user(user).dream(dream).build());
        if (draft.getSessionKey() == null) {
            draft.setSessionKey(java.util.UUID.randomUUID().toString());
        }
        draft.setStage(request.stage());
        draft.setContent(write(request.content()));
        draft.setExpiresAt(clock.instant().plus(KEEP_FOR));
        return toResponse(repository.save(draft));
    }

    @Transactional
    public void discard(Long dreamId) {
        Dream dream = lookup.dream(dreamId);
        repository.findByUser_IdAndDream_Id(lookup.userId(), dream.getId()).ifPresent(repository::delete);
    }

    /**
     * PB-FR-31: every new goal, step and task in the draft, saved through the
     * same services (and so the same ownership checks) as the normal create
     * endpoints, after the same field rules. If anything fails, nothing is
     * saved and the draft stays. On success the draft is removed.
     */
    @Transactional
    public PlanAddResult add(Long dreamId) {
        Dream dream = openDream(dreamId);
        PlanBuilderDraft draft = repository.findByUser_IdAndDream_Id(lookup.userId(), dream.getId())
                .filter(found -> found.getExpiresAt().isAfter(clock.instant()))
                .orElseThrow(() -> new ResourceNotFoundException("There is no plan draft for this dream."));
        PlanDraftContent content = read(draft.getContent());
        List<DraftGoal> goals = Optional.ofNullable(content.goals()).orElse(List.of());
        if (countNew(content) == 0) {
            throw new BusinessRuleException("Your draft has nothing new to add yet.");
        }

        List<Long> goalIds = new ArrayList<>();
        List<Long> stepIds = new ArrayList<>();
        List<Long> taskIds = new ArrayList<>();
        Map<Long, int[]> outcomes = new LinkedHashMap<>();
        // PB-FR-23 to PB-FR-25: where each draft goal and step ended up, for helpers and risks.
        Map<String, Long> goalByKey = new java.util.HashMap<>();
        Map<String, Long[]> stepByKey = new java.util.HashMap<>();
        for (DraftGoal draftGoal : goals) {
            Long goalId;
            if (draftGoal.existingId() != null) {
                Goal existing = lookup.goal(draftGoal.existingId());
                if (!existing.getDream().getId().equals(dream.getId()) || existing.isArchived()) {
                    throw new BusinessRuleException("A goal in your draft is no longer part of this dream. Remove it and try again.");
                }
                goalId = existing.getId();
            } else {
                GoalRequest request = new GoalRequest(dream.getId(), trim(draftGoal.title()), trim(draftGoal.description()),
                        trim(draftGoal.successCriteria()), priority(draftGoal.priority()), null, draftGoal.targetDate(),
                        WorkStatus.NOT_STARTED, draftGoal.moonshot(), null, dream.getScheduleMode());
                check(request, "Goal", draftGoal.title());
                goalId = goalService.createGoal(request).id();
                goalIds.add(goalId);
                count(outcomes, draftGoal.requestId(), draftGoal.edited());
            }
            if (draftGoal.key() != null) {
                goalByKey.put(draftGoal.key(), goalId);
            }
            int sequence = stepRepository.findByGoal_IdAndUser_IdAndArchivedFalse(goalId, lookup.userId()).size();
            // PB-FR-12: draft keys of this goal's steps already handled, to resolve "starts after".
            Map<String, Long> savedByKey = new java.util.HashMap<>();
            for (DraftStep draftStep : Optional.ofNullable(draftGoal.steps()).orElse(List.of())) {
                Long stepId;
                if (draftStep.existingId() != null) {
                    VisionStep existing = lookup.step(draftStep.existingId());
                    if (!existing.getGoal().getId().equals(goalId) || existing.isArchived()) {
                        throw new BusinessRuleException("A step in your draft is no longer under its goal. Remove it and try again.");
                    }
                    stepId = existing.getId();
                } else {
                    sequence++;
                    // Only an earlier step of the same goal can be waited for; anything else is ignored.
                    Long after = draftStep.startsAfterKey() == null ? null : savedByKey.get(draftStep.startsAfterKey());
                    VisionStepRequest request = new VisionStepRequest(goalId, trim(draftStep.title()), trim(draftStep.description()),
                            trim(draftStep.owner()), sequence, draftStep.complex(), priority(draftStep.priority()),
                            draftStep.targetDate(), WorkStatus.NOT_STARTED, trim(draftStep.resources()),
                            draftStep.estimatedHours(), after);
                    check(request, "Step", draftStep.title());
                    stepId = stepService.createStep(request).id();
                    stepIds.add(stepId);
                    count(outcomes, draftStep.requestId(), draftStep.edited());
                }
                if (draftStep.key() != null) {
                    savedByKey.put(draftStep.key(), stepId);
                    stepByKey.put(draftStep.key(), new Long[] {goalId, stepId});
                }
                for (DraftTask draftTask : Optional.ofNullable(draftStep.tasks()).orElse(List.of())) {
                    TaskItemRequest request = new TaskItemRequest(stepId, trim(draftTask.title()), null, trim(draftTask.owner()),
                            priority(draftTask.priority()), null, null, draftTask.dueDate(), WorkStatus.NOT_STARTED,
                            BigDecimal.ZERO, draftTask.estimatedHours(), null, null, null, null);
                    check(request, "Task", draftTask.title());
                    taskIds.add(taskService.createTask(request).id());
                    count(outcomes, draftTask.requestId(), draftTask.edited());
                }
            }
        }
        // PB-FR-23: helpers become links to the participant's partners, or new To Contact partners.
        List<Long> partnerIds = new ArrayList<>();
        List<Long> linkedPartnerIds = new ArrayList<>();
        Map<String, Long> partnerByHelperKey = new java.util.HashMap<>();
        for (DraftHelper helper : Optional.ofNullable(content.helpers()).orElse(List.of())) {
            Long goalId = goalFor(helper.linkKey(), goalByKey, stepByKey);
            Long stepId = stepFor(helper.linkKey(), stepByKey);
            Long partnerId;
            if (helper.partnerId() != null) {
                partnerId = helper.partnerId();
                if (partnerService.linkToPlanIfFree(partnerId, goalId, stepId)) {
                    linkedPartnerIds.add(partnerId);
                }
            } else {
                String name = trim(helper.name()) == null || trim(helper.name()).isEmpty() ? trim(helper.role()) : trim(helper.name());
                PartnerRequest request = new PartnerRequest(name, trim(helper.role()), null, null, null, null,
                        helper.supportType() == null ? PartnerSupportType.OTHER : helper.supportType(), null, null, dream.getId(),
                        goalId, stepId, null, PartnerStatus.TO_CONTACT, null, null, null, null, null, null, null, null, null, null, null);
                check(request, "Helper", helper.role());
                partnerId = partnerService.createPartner(request).id();
                partnerIds.add(partnerId);
                count(outcomes, helper.requestId(), helper.edited());
            }
            if (helper.key() != null) {
                partnerByHelperKey.put(helper.key(), partnerId);
            }
        }
        // PB-FR-24, PB-FR-25: risks become Open obstacles, with the helper who could fix them.
        List<Long> obstacleIds = new ArrayList<>();
        for (DraftRisk risk : Optional.ofNullable(content.risks()).orElse(List.of())) {
            ObstacleRequest request = new ObstacleRequest(dream.getId(), goalFor(risk.linkKey(), goalByKey, stepByKey),
                    stepFor(risk.linkKey(), stepByKey), null, trim(risk.title()), trim(risk.description()),
                    risk.obstacleType() == null ? ObstacleType.OTHER : risk.obstacleType(),
                    risk.severity() == null ? Severity.MEDIUM : risk.severity(), null, trim(risk.solution()), null, null, null,
                    null, null, null, null, null, null, null, null, null, null, null, null, null, null,
                    risk.helperKey() == null ? null : partnerByHelperKey.get(risk.helperKey()), ObstacleStatus.OPEN);
            check(request, "Risk", risk.title());
            obstacleIds.add(obstacleService.createObstacle(request).id());
            count(outcomes, risk.requestId(), risk.edited());
        }
        feedback.recordDraftOutcomes(outcomes);
        recordFinishedPlan(draft);
        repository.delete(draft);
        return new PlanAddResult(goalIds, stepIds, taskIds, partnerIds, obstacleIds, linkedPartnerIds);
    }

    /**
     * PB-FR-36: the AI requests this session made and their exact cost, from
     * the usage-log rows that carry its key. The key stays on the finished
     * plan, so the admin metrics can split them by feature.
     */
    private void recordFinishedPlan(PlanBuilderDraft draft) {
        String key = draft.getSessionKey();
        Object[] totals = key == null ? new Object[] {0L, BigDecimal.ZERO} : usageLogRepository.sessionTotals(key).get(0);
        finishedPlans.save(PlanBuilderFinishedPlan.builder()
                .userId(draft.getUser().getId())
                .dreamId(draft.getDream().getId())
                .sessionKey(key)
                .aiRequests(((Number) totals[0]).intValue())
                .aiCostUsd(totals[1] instanceof BigDecimal cost ? cost : BigDecimal.valueOf(((Number) totals[1]).doubleValue()))
                .finishedAt(clock.instant())
                .build());
    }

    @Nullable
    private static Long goalFor(String key, Map<String, Long> goalByKey, Map<String, Long[]> stepByKey) {
        if (key == null) {
            return null;
        }
        Long[] step = stepByKey.get(key);
        return step != null ? step[0] : goalByKey.get(key);
    }

    private static Long stepFor(String key, Map<String, Long[]> stepByKey) {
        Long[] step = key == null ? null : stepByKey.get(key);
        return step == null ? null : step[1];
    }

    /** PB-NFR-09: drafts last 14 days after their last change. */
    @Scheduled(cron = "${app.plan-builder.draft-cleanup-cron:0 15 4 * * *}")
    @Transactional
    public void deleteExpired() {
        int deleted = repository.deleteExpired(clock.instant());
        if (deleted > 0) {
            log.info("Deleted {} expired plan builder drafts", deleted);
        }
    }

    /** Everything Add would save or link: new goals, steps and tasks, helpers and risks. */
    static int countNew(PlanDraftContent content) {
        return countNew(Optional.ofNullable(content.goals()).orElse(List.of()))
                + Optional.ofNullable(content.helpers()).map(List::size).orElse(0)
                + Optional.ofNullable(content.risks()).map(List::size).orElse(0);
    }

    /** New goals, steps and tasks in a draft. */
    static int countNew(List<DraftGoal> goals) {
        int count = 0;
        for (DraftGoal goal : goals) {
            count += goal.existingId() == null ? 1 : 0;
            for (DraftStep step : Optional.ofNullable(goal.steps()).orElse(List.of())) {
                count += step.existingId() == null ? 1 : 0;
                count += Optional.ofNullable(step.tasks()).map(List::size).orElse(0);
            }
        }
        return count;
    }

    private Dream openDream(Long dreamId) {
        Dream dream = lookup.dream(dreamId);
        if (dream.isArchived()) {
            throw new BusinessRuleException("This dream is archived. Restore it to build its plan.");
        }
        return dream;
    }

    /** The same field rules as the normal create endpoints, named in plain words. */
    private <T> void check(T request, String kind, String title) {
        Set<ConstraintViolation<T>> violations = validator.validate(request);
        if (violations.isEmpty()) {
            return;
        }
        String name = title == null || title.isBlank() ? "without a title" : "\"" + title.trim() + "\"";
        List<String> needs = violations.stream().map(violation -> need(violation.getPropertyPath().toString()))
                .filter(Objects::nonNull).distinct().sorted().toList();
        throw new BusinessRuleException(kind + " " + name + " needs " + (needs.isEmpty() ? "a fix" : String.join(" and ", needs)) + ".");
    }

    private static String need(String field) {
        return switch (field) {
            case "title" -> "a title";
            case "owner" -> "an owner";
            case "targetDate", "dueDate" -> "a date";
            case "estimatedHours" -> "a valid number of hours";
            default -> "a valid " + field;
        };
    }

    private static void count(Map<Long, int[]> outcomes, Long requestId, boolean edited) {
        if (requestId != null) {
            outcomes.computeIfAbsent(requestId, id -> new int[2])[edited ? 1 : 0]++;
        }
    }

    private PlanDraftResponse toResponse(PlanBuilderDraft draft) {
        return new PlanDraftResponse(draft.getDream().getId(), draft.getStage(), read(draft.getContent()),
                draft.getUpdatedAt() == null ? clock.instant() : draft.getUpdatedAt(), draft.getExpiresAt(),
                draft.getAiRequests(), PlanSessionGuard.LIMIT);
    }

    private String write(PlanDraftContent content) {
        try {
            return objectMapper.writeValueAsString(content);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Could not store the plan draft", exception);
        }
    }

    private PlanDraftContent read(String json) {
        try {
            return objectMapper.readValue(json, PlanDraftContent.class);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("A stored plan draft could not be read", exception);
        }
    }

    private static Priority priority(Priority value) {
        return value == null ? Priority.MEDIUM : value;
    }

    private static String trim(String value) {
        return value == null ? null : value.trim();
    }
}
