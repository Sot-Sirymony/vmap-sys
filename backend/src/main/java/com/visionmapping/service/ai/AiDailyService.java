package com.visionmapping.service.ai;

import static com.visionmapping.service.ai.AiContextBuilder.clip;
import static com.visionmapping.service.ai.AiContextBuilder.clipLong;

import com.visionmapping.dto.request.AiOutreachRequest;
import com.visionmapping.dto.request.AiPitchRequest;
import com.visionmapping.dto.response.AiDailyResponses.NextStep;
import com.visionmapping.dto.response.AiDailyResponses.OutreachDraft;
import com.visionmapping.dto.response.AiDailyResponses.Pick;
import com.visionmapping.dto.response.AiDailyResponses.PitchFeedback;
import com.visionmapping.dto.response.AiDailyResponses.ResourceIdeas;
import com.visionmapping.dto.response.AiDailyResponses.WeeklyReviewDraft;
import com.visionmapping.entity.AiApprovedResource;
import com.visionmapping.entity.AppUser;
import com.visionmapping.entity.AttitudeCheckIn;
import com.visionmapping.entity.Dream;
import com.visionmapping.entity.Goal;
import com.visionmapping.entity.Partner;
import com.visionmapping.entity.TaskItem;
import com.visionmapping.entity.VisionStep;
import com.visionmapping.entity.enums.WorkStatus;
import com.visionmapping.repository.AttitudeCheckInRepository;
import com.visionmapping.repository.TaskItemRepository;
import com.visionmapping.repository.VisionStepRepository;
import com.visionmapping.service.support.EntityLookup;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * V9 Phase 6: AI for daily use. The next best step for the time the
 * participant has (AI-FR-12), resource ideas (AI-FR-13), a weekly review draft
 * (AI-FR-14), pitch practice (AI-FR-16) and outreach drafts (AI-FR-17). Every
 * answer is a suggestion; nothing is saved here (AI-FR-04).
 */
@Service
@RequiredArgsConstructor
public class AiDailyService {

    static final int MAX_CANDIDATES = 20;
    static final int MAX_PICKS = 3;
    static final int MAX_RESOURCES = 8;
    private static final Map<String, String> AUDIENCES = Map.of(
            "PARTNER", "a possible partner who would share the work and the results",
            "BUYER", "a possible buyer or customer deciding whether to pay",
            "SPONSOR", "a possible sponsor or funder deciding whether to back the work");

    private final EntityLookup lookup;
    private final AiAccessService accessService;
    private final AiStructuredRequests requests;
    private final TaskItemRepository taskItemRepository;
    private final VisionStepRepository visionStepRepository;
    private final AttitudeCheckInRepository attitudeCheckInRepository;
    private final AiResourceIdeas resourceIdeas;
    private final TransactionTemplate transactions;
    private final Clock clock;

    private record Candidate(String type, Long id, String title, LocalDate dueDate, String line) {
    }

    /** AI-FR-12: picks from the participant's own open steps and tasks only. */
    public NextStep nextStep(int minutes, String language) {
        AppUser user = lookup.currentUser();
        LocalDate today = LocalDate.now(clock);
        List<Candidate> candidates = transactions.execute(status -> openWork(user.getId(), today));
        if (candidates.isEmpty()) {
            return new NextStep(List.of(), "Nothing open in your plan yet. Add a step to a goal, or ask the Dream Coach where to start.");
        }
        String apiKey = accessService.requireAvailable(user);
        StringBuilder records = new StringBuilder("Today is " + today + ". Open work, most urgent first:\n");
        candidates.forEach(candidate -> records.append(candidate.line()).append('\n'));
        String task = "The participant has " + minutes + " minutes now. Pick up to " + MAX_PICKS + " items from the list, best "
                + "first, that fit that time and matter most: overdue and blocking work first, unless it cannot fit. If an item is "
                + "too big for the time, pick it only if a useful part fits, and say which part. Use the ids in brackets.";
        AiDrafts.NextStepDraft draft = requests.ask(user, apiKey, "NEXT_STEP", language, records.toString(), task, AiDrafts.NextStepDraft.class);
        Map<String, Candidate> byKey = new HashMap<>();
        candidates.forEach(candidate -> byKey.put(candidate.type() + ":" + candidate.id(), candidate));
        List<Pick> picks = new ArrayList<>();
        for (AiDrafts.PickDraft pick : Optional.ofNullable(draft.picks()).orElse(List.of())) {
            String type = "TASK".equalsIgnoreCase(pick.itemType()) ? "TASK" : "STEP";
            Candidate candidate = byKey.get(type + ":" + pick.itemId());
            // Only items from this participant's list, each once.
            if (candidate == null || picks.size() >= MAX_PICKS || picks.stream().anyMatch(p -> p.itemId().equals(candidate.id()) && p.itemType().equals(type))) {
                continue;
            }
            picks.add(new Pick(type, candidate.id(), candidate.title(), candidate.dueDate(), limit(pick.reason(), 300),
                    pick.minutes() > 0 && pick.minutes() <= 600 ? pick.minutes() : null));
        }
        return new NextStep(picks, limit(draft.note(), 300));
    }

    /** AI-FR-14: built from the last seven days of the participant's own records. */
    public WeeklyReviewDraft weeklyReview(String language) {
        AppUser user = lookup.currentUser();
        String apiKey = accessService.requireAvailable(user);
        LocalDate today = LocalDate.now(clock);
        Instant weekAgo = Instant.now(clock).minus(7, ChronoUnit.DAYS);
        String records = transactions.execute(status -> {
            StringBuilder out = new StringBuilder("Today is " + today + ". The last seven days:\n");
            List<TaskItem> tasks = taskItemRepository.findByUser_IdAndArchivedFalse(user.getId());
            List<VisionStep> steps = visionStepRepository.findByUser_IdAndArchivedFalse(user.getId());
            section(out, "Tasks finished", tasks.stream()
                    .filter(task -> task.getCompletedAt() != null && task.getCompletedAt().isAfter(weekAgo))
                    .map(task -> clip(task.getTitle())).toList());
            section(out, "Steps finished", steps.stream()
                    .filter(step -> step.getStatus() == WorkStatus.COMPLETED && step.getUpdatedAt() != null && step.getUpdatedAt().isAfter(weekAgo))
                    .map(step -> clip(step.getTitle())).toList());
            section(out, "Overdue", openWork(user.getId(), today).stream()
                    .filter(candidate -> candidate.dueDate() != null && candidate.dueDate().isBefore(today))
                    .map(Candidate::line).toList());
            section(out, "Blocked", tasks.stream()
                    .filter(task -> task.getStatus() == WorkStatus.BLOCKED)
                    .map(task -> clip(task.getTitle()) + (task.getBlockerReason() == null ? "" : " | blocked by: " + clip(task.getBlockerReason())))
                    .toList());
            List<AttitudeCheckIn> moods = attitudeCheckInRepository.findTop30ByUser_IdOrderByCheckInDateDesc(user.getId()).stream()
                    .filter(checkIn -> !checkIn.getCheckInDate().isBefore(today.minusDays(7))).toList();
            section(out, "Attitude check-ins (1 low to 5 high)", moods.stream()
                    .map(checkIn -> checkIn.getCheckInDate() + ": " + checkIn.getScore()
                            + (checkIn.getNote() == null ? "" : " | " + clip(checkIn.getNote())))
                    .toList());
            return out.toString();
        });
        String task = "Draft this participant's weekly review from their records only. Do not invent work that is not listed. "
                + "Write in the first person, as their own notes, plainly and kindly. Leave a field empty when there is nothing for it.";
        AiDrafts.WeeklyReviewDraft draft = requests.ask(user, apiKey, "WEEKLY_REVIEW", language, records, task, AiDrafts.WeeklyReviewDraft.class);
        return new WeeklyReviewDraft(limit(draft.summary(), 3000), limit(draft.completedTasks(), 3000), limit(draft.delayedTasks(), 3000),
                limit(draft.blockedTasks(), 3000), limit(draft.lessonsLearned(), 3000), limit(draft.nextActions(), 3000));
    }

    /** AI-FR-16: role-plays the audience and scores clarity, benefit and the ask. */
    public PitchFeedback pitch(AiPitchRequest request) {
        AppUser user = lookup.currentUser();
        String apiKey = accessService.requireAvailable(user);
        String language = request.language() == null ? "en" : request.language();
        String records = "The participant's pitch:\n" + request.pitch().replace('<', '‹').replace('>', '›').trim();
        String task = "Play " + AUDIENCES.get(request.audience()) + " hearing this pitch for the first time. React honestly in "
                + "your own voice, then step out of the role and score it from 1 to 5 on clarity, benefit to you, and how specific "
                + "and easy to agree to the ask is. Rewrite it better, keeping the participant's facts, and give the one tip that "
                + "would help most. Be encouraging and specific.";
        AiDrafts.PitchFeedbackDraft draft = requests.ask(user, apiKey, "PITCH_COACH", language, records, task, AiDrafts.PitchFeedbackDraft.class);
        return new PitchFeedback(request.audience(), limit(draft.reaction(), 1500), score(draft.clarity()), score(draft.benefit()),
                score(draft.ask()), limit(draft.improvedPitch(), 3000), limit(draft.tip(), 500));
    }

    /** AI-FR-17: a message the participant copies and sends themselves; the app never sends it. */
    public OutreachDraft outreach(AiOutreachRequest request) {
        AppUser user = lookup.currentUser();
        String apiKey = accessService.requireAvailable(user);
        String language = request.language() == null ? "en" : request.language();
        String records = transactions.execute(status -> {
            StringBuilder out = new StringBuilder();
            out.append("Sender: ").append(clip(user.getFullName())).append('\n');
            if (request.partnerId() != null) {
                // Name and role only: an outreach needs a greeting, never the partner's email or phone (AI-NFR-04).
                Partner partner = lookup.partner(request.partnerId());
                out.append("Writing to: ").append(clip(partner.getName()));
                if (partner.getRole() != null) {
                    out.append(", ").append(clip(partner.getRole()));
                }
                if (partner.getOrganization() != null) {
                    out.append(" at ").append(clip(partner.getOrganization()));
                }
                out.append('\n');
            }
            if (request.relatedDreamId() != null) {
                Dream dream = lookup.dream(request.relatedDreamId());
                out.append("The dream: ").append(clip(dream.getTitle())).append('\n');
                if (dream.getWhyImportant() != null) {
                    out.append("Why it matters: ").append(clip(dream.getWhyImportant())).append('\n');
                }
            }
            if (request.purpose() != null && !request.purpose().isBlank()) {
                out.append("Purpose: ").append(clip(request.purpose())).append('\n');
            }
            out.append("What they want to ask for: ").append(clip(request.request())).append('\n');
            return out.toString();
        });
        String task = "Draft a short outreach message from the sender. Open with something the reader cares about, say what "
                + "the work is and why it matters, make one specific, easy request, and say how it could benefit the reader. "
                + "Respectful and warm, never demanding or flattering. Do not invent facts, numbers or shared history.";
        AiDrafts.OutreachDraft draft = requests.ask(user, apiKey, "OUTREACH_DRAFT", language, records, task, AiDrafts.OutreachDraft.class);
        return new OutreachDraft(limit(draft.subject(), 220), limit(draft.message(), 6000));
    }

    /** Resource ideas are chosen for the goal's outcome, not its title alone. */
    static final String RESOURCE_GROUNDING = "Before suggesting, read the goal's description and success criteria, and the "
            + "dream's success definition. Pick only resources that help reach that outcome, starting with what the steps "
            + "still need. In each idea's why, say in one short sentence which part of the success criteria it helps meet.";

    private static void appendLong(StringBuilder out, String label, String value) {
        if (value != null && !value.isBlank()) {
            out.append(label).append(": ").append(clipLong(value)).append('\n');
        }
    }

    /** AI-FR-13: kinds of resources and people for a goal; every named one must be checked before use. */
    public ResourceIdeas resources(Long goalId, String language) {
        AppUser user = lookup.currentUser();
        // V10 (SEC-01-02): ownership first, so another user's record answers 404
        // whether or not AI is available.
        lookup.goal(goalId);
        String apiKey = accessService.requireAvailable(user);
        Map<Long, AiApprovedResource> approved = resourceIdeas.approved();
        String records = transactions.execute(status -> {
            Goal goal = lookup.goal(goalId);
            Dream dream = goal.getDream();
            StringBuilder out = new StringBuilder("Dream: ").append(clip(dream.getTitle())).append('\n');
            appendLong(out, "Dream success looks like", dream.getSuccessDefinition());
            out.append("Goal: ").append(clip(goal.getTitle()))
                    .append(goal.getTargetDate() == null ? "" : ", target " + goal.getTargetDate()).append('\n');
            appendLong(out, "Goal description", goal.getDescription());
            appendLong(out, "Goal success criteria", goal.getSuccessCriteria());
            visionStepRepository.findByGoal_IdAndUser_IdAndArchivedFalse(goal.getId(), user.getId())
                    .forEach(step -> out.append("Step: ").append(clip(step.getTitle()))
                            .append(step.getResources() == null ? "" : " | needs: " + clip(step.getResources())).append('\n'));
            out.append(AiResourceIdeas.describe(approved));
            return out.toString();
        });
        String task = AiResourceIdeas.instruction(MAX_RESOURCES) + " They are for this goal. " + RESOURCE_GROUNDING;
        AiDrafts.ResourceDrafts drafts = requests.ask(user, apiKey, "RESOURCE_IDEAS", language, records, task, AiDrafts.ResourceDrafts.class);
        return new ResourceIdeas(AiResourceIdeas.toIdeas(drafts.resources(), approved, MAX_RESOURCES));
    }

    /** Resource ideas for one step, chosen for what the step is for and what it does not have yet. */
    static final String STEP_RESOURCE_GROUNDING = "Before suggesting, read the step's description, its resources and people, "
            + "the tasks it already has, and the goal's success criteria. Pick only resources that help finish this step "
            + "toward that outcome. The step's resources and people line is what the participant already has or plans to "
            + "use: do not repeat it; fill what is still missing. In each idea's why, say in one short sentence how it "
            + "helps finish the step.";

    /** AI-FR-13 for one step: the same checked ideas as for a goal, built from the step's own details. */
    public ResourceIdeas stepResources(Long stepId, String language) {
        AppUser user = lookup.currentUser();
        // V10 (SEC-01-02): ownership first, so another user's record answers 404
        // whether or not AI is available.
        lookup.step(stepId);
        String apiKey = accessService.requireAvailable(user);
        Map<Long, AiApprovedResource> approved = resourceIdeas.approved();
        String records = transactions.execute(status -> {
            VisionStep step = lookup.step(stepId);
            Goal goal = step.getGoal();
            Dream dream = goal.getDream();
            StringBuilder out = new StringBuilder("Dream: ").append(clip(dream.getTitle())).append('\n');
            appendLong(out, "Dream success looks like", dream.getSuccessDefinition());
            out.append("Goal: ").append(clip(goal.getTitle())).append('\n');
            appendLong(out, "Goal success criteria", goal.getSuccessCriteria());
            out.append("Step: ").append(clip(step.getTitle()))
                    .append(step.getTargetDate() == null ? "" : ", due " + step.getTargetDate()).append('\n');
            appendLong(out, "Step description", step.getDescription());
            if (step.getOwner() != null && !step.getOwner().isBlank()) {
                out.append("Step owner: ").append(clip(step.getOwner())).append('\n');
            }
            appendLong(out, "Step resources and people", step.getResources());
            taskItemRepository.findByStep_IdAndUser_IdAndArchivedFalse(step.getId(), user.getId())
                    .forEach(task -> out.append("Task: ").append(clip(task.getTitle())).append(" | ").append(task.getStatus()).append('\n'));
            out.append(AiResourceIdeas.describe(approved));
            return out.toString();
        });
        String task = AiResourceIdeas.instruction(MAX_RESOURCES) + " They are for this step. " + STEP_RESOURCE_GROUNDING;
        AiDrafts.ResourceDrafts drafts = requests.ask(user, apiKey, "RESOURCE_IDEAS", language, records, task, AiDrafts.ResourceDrafts.class);
        return new ResourceIdeas(AiResourceIdeas.toIdeas(drafts.resources(), approved, MAX_RESOURCES));
    }

    private List<Candidate> openWork(Long userId, LocalDate today) {
        List<Candidate> candidates = new ArrayList<>();
        for (TaskItem task : taskItemRepository.findByUser_IdAndArchivedFalse(userId)) {
            if (task.getStatus() != WorkStatus.COMPLETED) {
                candidates.add(new Candidate("TASK", task.getId(), task.getTitle(), task.getDueDate(), line("TASK", task.getId(),
                        task.getTitle(), task.getDueDate(), today, task.getStatus(), task.getPriority().name(), task.getBlockerReason())));
            }
        }
        for (VisionStep step : visionStepRepository.findByUser_IdAndArchivedFalse(userId)) {
            if (step.getStatus() != WorkStatus.COMPLETED) {
                candidates.add(new Candidate("STEP", step.getId(), step.getTitle(), step.getTargetDate(), line("STEP", step.getId(),
                        step.getTitle(), step.getTargetDate(), today, step.getStatus(), step.getPriority().name(), null)));
            }
        }
        return candidates.stream()
                .sorted(Comparator.comparing(Candidate::dueDate, Comparator.nullsLast(Comparator.naturalOrder())))
                .limit(MAX_CANDIDATES)
                .toList();
    }

    private static String line(String type, Long id, String title, LocalDate due, LocalDate today, WorkStatus status,
                               String priority, String blocker) {
        return "- " + type + " [" + id + "] " + clip(title) + " | due " + (due == null ? "none" : due)
                + (due != null && due.isBefore(today) ? " (overdue)" : "") + " | " + status + " | " + priority + " priority"
                + (blocker == null ? "" : " | blocked by: " + clip(blocker));
    }

    private static void section(StringBuilder out, String title, List<String> lines) {
        out.append(title).append(lines.isEmpty() ? ": none\n" : ":\n");
        lines.stream().limit(15).forEach(item -> out.append(item.startsWith("- ") ? item : "- " + item).append('\n'));
    }

    private static int score(int value) {
        return Math.max(1, Math.min(5, value));
    }

    @Nullable
    private static String limit(String value, int max) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.length() <= max ? trimmed : trimmed.substring(0, max - 3) + "...";
    }
}
