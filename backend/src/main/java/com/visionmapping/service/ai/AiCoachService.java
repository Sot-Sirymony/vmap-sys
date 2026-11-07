package com.visionmapping.service.ai;

import static com.visionmapping.service.ai.AiContextBuilder.clip;

import com.visionmapping.dto.request.AiFeedbackDraftRequest;
import com.visionmapping.dto.request.AiSummaryRatingRequest;
import com.visionmapping.dto.response.AiCoachResponses.CohortSummary;
import com.visionmapping.dto.response.AiCoachResponses.FeedbackDraft;
import com.visionmapping.dto.response.AiCoachResponses.ParticipantSummary;
import com.visionmapping.dto.response.ProgressMetricsResponse;
import com.visionmapping.entity.AiCoachSummary;
import com.visionmapping.entity.AppUser;
import com.visionmapping.entity.AttitudeCheckIn;
import com.visionmapping.entity.Cohort;
import com.visionmapping.entity.Dream;
import com.visionmapping.entity.FearEntry;
import com.visionmapping.entity.Goal;
import com.visionmapping.entity.ModuleCompletion;
import com.visionmapping.entity.TaskItem;
import com.visionmapping.entity.VisionStep;
import com.visionmapping.entity.enums.WorkStatus;
import com.visionmapping.exception.BusinessRuleException;
import com.visionmapping.exception.ResourceNotFoundException;
import com.visionmapping.repository.AiCoachSummaryRepository;
import com.visionmapping.repository.AppUserRepository;
import com.visionmapping.repository.AttitudeCheckInRepository;
import com.visionmapping.repository.DreamRepository;
import com.visionmapping.repository.FearEntryRepository;
import com.visionmapping.repository.GoalRepository;
import com.visionmapping.repository.ModuleCompletionRepository;
import com.visionmapping.repository.TaskItemRepository;
import com.visionmapping.repository.VisionStepRepository;
import com.visionmapping.service.ActivityService;
import com.visionmapping.service.CoachRiskSignals;
import com.visionmapping.service.CoachService;
import com.visionmapping.service.ProgressMetricsService;
import com.visionmapping.service.support.EntityLookup;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * V9 Phase 8: the coach co-pilot. A weekly summary per participant (AI-FR-18)
 * that explains the risk signals (AI-FR-19), feedback drafts the coach edits
 * and sends (AI-FR-20), and a cohort summary (AI-FR-21). Claude sees only what
 * the coach may see (D-6): journals only when shared (D-8), and nothing about a
 * participant who has not given AI consent (AI-NFR-03). Summaries are kept for
 * the week, so reopening one costs nothing.
 */
@Service
@RequiredArgsConstructor
public class AiCoachService {

    static final String SUMMARY = "COACH_SUMMARY";
    static final String FEEDBACK = "COACH_FEEDBACK";
    static final String COHORT = "COACH_COHORT";
    static final int MAX_LINES = 15;

    private final EntityLookup lookup;
    private final CoachService coachService;
    private final AiAccessService accessService;
    private final AiStructuredRequests requests;
    private final AiCoachSummaryRepository summaryRepository;
    private final AppUserRepository appUserRepository;
    private final DreamRepository dreamRepository;
    private final GoalRepository goalRepository;
    private final VisionStepRepository visionStepRepository;
    private final TaskItemRepository taskItemRepository;
    private final AttitudeCheckInRepository attitudeCheckInRepository;
    private final FearEntryRepository fearEntryRepository;
    private final ModuleCompletionRepository moduleCompletionRepository;
    private final ActivityService activityService;
    private final ProgressMetricsService progressMetricsService;
    private final CoachRiskSignals coachRiskSignals;
    private final TransactionTemplate transactions;
    private final Clock clock;

    /** This week's saved summary, or null when none has been made yet. Never calls Claude. */
    public ParticipantSummary participantSummary(Long participantId) {
        return transactions.execute(status -> {
            AppUser participant = coachService.requireVisibleParticipant(participantId);
            return summaryRepository.findFirstByParticipant_IdAndWeekStartOrderByIdDesc(participant.getId(), weekStart())
                    .map(summary -> toParticipantSummary(summary, coachRiskSignals.reasons(participant)))
                    .orElse(null);
        });
    }

    /** AI-FR-18: makes (or remakes) this week's summary. */
    public ParticipantSummary generateParticipantSummary(Long participantId, String language) {
        AppUser coach = lookup.currentUser();
        record Prepared(AppUser participant, String records, List<String> signals) {
        }
        Prepared prepared = transactions.execute(status -> {
            AppUser participant = coachService.requireVisibleParticipant(participantId);
            List<String> signals = coachRiskSignals.reasons(participant);
            return new Prepared(participant, describe(participant, signals, true), signals);
        });
        String apiKey = accessService.requireAvailableForStaff(prepared.participant());
        String task = "Write this week's one-page summary of the participant for their coach: progress against the plan, the mood "
                + "trend (only from attitude check-ins in the records; leave it empty if there are none), risks with the reason "
                + "for each (use the risk signals listed and anything else the records clearly show), and one specific action "
                + "the coach could take this week.";
        AiDrafts.CoachSummaryDraft draft = requests.askForCoach(coach, apiKey, SUMMARY, language, prepared.records(), task,
                AiDrafts.CoachSummaryDraft.class);
        boolean shared = prepared.participant().isShareJournalsWithCoach();
        AiCoachSummary saved = transactions.execute(status -> summaryRepository.save(AiCoachSummary.builder()
                .scope(AiCoachSummary.SCOPE_PARTICIPANT)
                .coach(coach)
                .participant(prepared.participant())
                .weekStart(weekStart())
                .progressText(limit(draft.progress()))
                .moodText(shared ? limit(draft.mood()) : null)
                .risksText(limit(draft.risks()))
                .actionText(limit(draft.suggestedAction()))
                .build()));
        return toParticipantSummary(saved, prepared.signals());
    }

    /** AI-FR-20: a comment for the coach to edit and send; nothing is sent here. */
    public FeedbackDraft feedbackDraft(Long participantId, AiFeedbackDraftRequest request) {
        AppUser coach = lookup.currentUser();
        String language = request.language() == null ? "en" : request.language();
        record Prepared(AppUser participant, String records) {
        }
        Prepared prepared = transactions.execute(status -> {
            AppUser participant = coachService.requireVisibleParticipant(participantId);
            String about = switch (request.targetType()) {
                case GENERAL -> "The comment is about how the participant is doing overall.";
                case DREAM -> "The comment is about this dream: " + dreamOf(participant, request.targetId());
                case GOAL -> "The comment is about this goal: " + goalOf(participant, request.targetId());
                case STEP -> "The comment is about this step: " + stepOf(participant, request.targetId());
            };
            return new Prepared(participant, describe(participant, coachRiskSignals.reasons(participant), false) + "\n" + about + "\n");
        });
        String apiKey = accessService.requireAvailableForStaff(prepared.participant());
        String task = "Draft a short comment from the coach to the participant about the item named at the end of the records. "
                + "Name one thing done well, one concrete suggestion, and ask one question. Under 120 words.";
        AiDrafts.CoachFeedbackDraft draft = requests.askForCoach(coach, apiKey, FEEDBACK, language, prepared.records(), task,
                AiDrafts.CoachFeedbackDraft.class);
        return new FeedbackDraft(draft.message() == null ? null : draft.message().trim());
    }

    public CohortSummary cohortSummary(Long cohortId) {
        return transactions.execute(status -> {
            Cohort cohort = coachService.requireOwnCohort(cohortId);
            return summaryRepository.findFirstByCohort_IdAndScopeAndWeekStartOrderByIdDesc(cohort.getId(), AiCoachSummary.SCOPE_COHORT, weekStart())
                    .map(AiCoachService::toCohortSummary)
                    .orElse(null);
        });
    }

    /** AI-FR-21: common blockers and modules where people struggle, from participants who gave AI consent. */
    public CohortSummary generateCohortSummary(Long cohortId, String language) {
        AppUser coach = lookup.currentUser();
        record Prepared(Cohort cohort, String records) {
        }
        Prepared prepared = transactions.execute(status -> {
            Cohort cohort = coachService.requireOwnCohort(cohortId);
            if (!cohort.isAiEnabled()) {
                throw new BusinessRuleException("AI is switched off for this cohort.");
            }
            List<AppUser> members = appUserRepository.findByCohort_IdOrderByFullNameAsc(cohort.getId()).stream()
                    .filter(member -> !member.getId().equals(coach.getId())).toList();
            List<AppUser> consented = members.stream().filter(member -> member.getAiConsentAt() != null).toList();
            if (consented.isEmpty()) {
                throw new BusinessRuleException("No one in this cohort has turned on AI consent yet.");
            }
            return new Prepared(cohort, describeCohort(consented, members.size()));
        });
        String apiKey = accessService.requireAvailableForStaff(null);
        String task = "Summarise this cohort's week for the coach: an overview, blockers several people share, modules where "
                + "people are stuck or slow, and up to three actions for the coach. Talk about patterns, not individuals.";
        AiDrafts.CohortSummaryDraft draft = requests.askForCoach(coach, apiKey, COHORT, language, prepared.records(), task,
                AiDrafts.CohortSummaryDraft.class);
        AiCoachSummary saved = transactions.execute(status -> summaryRepository.save(AiCoachSummary.builder()
                .scope(AiCoachSummary.SCOPE_COHORT)
                .coach(coach)
                .cohort(prepared.cohort())
                .weekStart(weekStart())
                .progressText(limit(draft.overview()))
                .risksText(limit(draft.commonBlockers()))
                .moodText(limit(draft.strugglingModules()))
                .actionText(limit(draft.suggestedActions()))
                .build()));
        return toCohortSummary(saved);
    }

    /** AC-16: the coach says whether a summary was accurate. Only summaries the coach may see. */
    public void rate(Long summaryId, AiSummaryRatingRequest request) {
        transactions.executeWithoutResult(status -> {
            AiCoachSummary summary = summaryRepository.findById(summaryId)
                    .orElseThrow(() -> new ResourceNotFoundException("Summary not found: " + summaryId));
            if (AiCoachSummary.SCOPE_PARTICIPANT.equals(summary.getScope())) {
                coachService.requireVisibleParticipant(summary.getParticipant().getId());
            } else {
                coachService.requireOwnCohort(summary.getCohort().getId());
            }
            summary.setAccurate(request.accurate());
            summary.setRatingNote(request.note() == null || request.note().isBlank() ? null : request.note().trim());
            summary.setRatedAt(Instant.now(clock));
        });
    }

    /** What the coach may see of one participant (D-6, D-8), as plain records. */
    private String describe(AppUser participant, List<String> signals, boolean withWeek) {
        LocalDate today = LocalDate.now(clock);
        Instant now = Instant.now(clock);
        Long id = participant.getId();
        StringBuilder out = new StringBuilder("Today is " + today + ". Participant: " + clip(firstName(participant.getFullName())) + ".\n");
        long quietDays = Math.max(0, Duration.between(activityService.lastActivity(participant), now).toDays());
        out.append("Days since last activity: ").append(quietDays).append('\n');
        ProgressMetricsResponse metrics = progressMetricsService.metricsFor(id, today);
        metrics.metrics().forEach(metric -> out.append("Objective: ").append(metric.label()).append(" = ").append(metric.value())
                .append(metric.met() ? " (met)" : " (not yet)").append('\n'));
        List<ModuleCompletion> modules = moduleCompletionRepository.findByUser_Id(id);
        out.append("Modules completed: ").append(modules.size()).append(" of 15\n");
        for (Dream dream : dreamRepository.findByUser_IdAndArchivedFalse(id)) {
            out.append("Dream: ").append(clip(dream.getTitle())).append(" (").append(dream.getStatus()).append(")\n");
            for (Goal goal : goalRepository.findByDream_IdAndUser_IdAndArchivedFalse(dream.getId(), id)) {
                List<VisionStep> steps = visionStepRepository.findByGoal_IdAndUser_IdAndArchivedFalse(goal.getId(), id);
                long done = steps.stream().filter(step -> step.getStatus() == WorkStatus.COMPLETED).count();
                out.append("  Goal: ").append(clip(goal.getTitle())).append(" | ").append(done).append(" of ")
                        .append(steps.size()).append(" steps done").append('\n');
            }
        }
        if (withWeek) {
            Instant weekAgo = now.minus(7, ChronoUnit.DAYS);
            List<TaskItem> tasks = taskItemRepository.findByUser_IdAndArchivedFalse(id);
            lines(out, "Finished this week", tasks.stream()
                    .filter(task -> task.getCompletedAt() != null && task.getCompletedAt().isAfter(weekAgo))
                    .map(task -> clip(task.getTitle())).toList());
            lines(out, "Blocked", tasks.stream().filter(task -> task.getStatus() == WorkStatus.BLOCKED)
                    .map(task -> clip(task.getTitle()) + (task.getBlockerReason() == null ? "" : " | " + clip(task.getBlockerReason())))
                    .toList());
        }
        if (participant.isShareJournalsWithCoach()) {
            List<AttitudeCheckIn> checkIns = attitudeCheckInRepository.findTop30ByUser_IdOrderByCheckInDateDesc(id).stream()
                    .filter(checkIn -> !checkIn.getCheckInDate().isBefore(today.minusDays(14))).toList();
            lines(out, "Attitude check-ins, last two weeks (1 low to 5 high)", checkIns.stream()
                    .map(checkIn -> checkIn.getCheckInDate() + ": " + checkIn.getScore()).toList());
            lines(out, "Recent fears", fearEntryRepository.findByUser_IdAndArchivedFalse(id).stream()
                    .sorted(Comparator.comparing(FearEntry::getId).reversed()).limit(3)
                    .map(fear -> clip(fear.getFear())).toList());
        } else {
            out.append("Journals: not shared with the coach.\n");
        }
        lines(out, "Risk signals", signals);
        return out.toString();
    }

    /** Patterns across the cohort, without names: blockers, where people are in the modules, overdue work. */
    private String describeCohort(List<AppUser> consented, int total) {
        LocalDate today = LocalDate.now(clock);
        StringBuilder out = new StringBuilder("Today is " + today + ". " + consented.size() + " of " + total
                + " participants gave AI consent; only they are included.\n");
        Map<Integer, Integer> completedPerModule = new TreeMap<>();
        List<String> blockers = new ArrayList<>();
        int atRisk = 0;
        int overdue = 0;
        for (AppUser member : consented) {
            moduleCompletionRepository.findByUser_Id(member.getId())
                    .forEach(completion -> completedPerModule.merge(completion.getModuleNumber(), 1, Integer::sum));
            for (TaskItem task : taskItemRepository.findByUser_IdAndArchivedFalse(member.getId())) {
                if (task.getStatus() == WorkStatus.BLOCKED && task.getBlockerReason() != null) {
                    blockers.add(clip(task.getBlockerReason()));
                }
                if (task.getStatus() != WorkStatus.COMPLETED && task.getDueDate() != null && task.getDueDate().isBefore(today)) {
                    overdue++;
                }
            }
            if (!coachRiskSignals.reasons(member).isEmpty()) {
                atRisk++;
            }
        }
        out.append("Participants with risk signals: ").append(atRisk).append('\n');
        out.append("Overdue tasks across the cohort: ").append(overdue).append('\n');
        out.append("Participants who completed each module (module number: count):\n");
        for (int number = 1; number <= 15; number++) {
            out.append("  ").append(number).append(": ").append(completedPerModule.getOrDefault(number, 0)).append('\n');
        }
        lines(out, "Blocker reasons written by participants", blockers);
        return out.toString();
    }

    private String dreamOf(AppUser participant, Long id) {
        return dreamRepository.findById(id == null ? -1 : id)
                .filter(dream -> dream.getUser().getId().equals(participant.getId()) && !dream.isArchived())
                .map(dream -> clip(dream.getTitle()) + (dream.getWhyImportant() == null ? "" : " | why: " + clip(dream.getWhyImportant())))
                .orElseThrow(() -> new BusinessRuleException("That item was not found in this participant's plan."));
    }

    private String goalOf(AppUser participant, Long id) {
        return goalRepository.findById(id == null ? -1 : id)
                .filter(goal -> goal.getUser().getId().equals(participant.getId()) && !goal.isArchived())
                .map(goal -> clip(goal.getTitle()) + " | " + goal.getStatus()
                        + (goal.getTargetDate() == null ? "" : " | target " + goal.getTargetDate()))
                .orElseThrow(() -> new BusinessRuleException("That item was not found in this participant's plan."));
    }

    private String stepOf(AppUser participant, Long id) {
        return visionStepRepository.findById(id == null ? -1 : id)
                .filter(step -> step.getUser().getId().equals(participant.getId()) && !step.isArchived())
                .map(step -> clip(step.getTitle()) + " | " + step.getStatus()
                        + (step.getTargetDate() == null ? "" : " | due " + step.getTargetDate()))
                .orElseThrow(() -> new BusinessRuleException("That item was not found in this participant's plan."));
    }

    private LocalDate weekStart() {
        return LocalDate.now(clock).with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
    }

    private static ParticipantSummary toParticipantSummary(AiCoachSummary summary, List<String> signals) {
        return new ParticipantSummary(summary.getId(), summary.getWeekStart(), summary.getCreatedAt(), summary.getProgressText(),
                summary.getMoodText(), summary.getRisksText(), summary.getActionText(), signals, summary.getAccurate(),
                summary.getRatingNote());
    }

    /** Cohort summaries reuse the columns: progress holds the overview, risks the blockers, mood the modules. */
    private static CohortSummary toCohortSummary(AiCoachSummary summary) {
        return new CohortSummary(summary.getId(), summary.getWeekStart(), summary.getCreatedAt(), summary.getProgressText(),
                summary.getRisksText(), summary.getMoodText(), summary.getActionText(), summary.getAccurate(), summary.getRatingNote());
    }

    private static void lines(StringBuilder out, String title, List<String> items) {
        out.append(title).append(items.isEmpty() ? ": none\n" : ":\n");
        items.stream().limit(MAX_LINES).forEach(item -> out.append("- ").append(item).append('\n'));
    }

    private static String limit(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.length() <= 2000 ? trimmed : trimmed.substring(0, 1997) + "...";
    }

    private static String firstName(String fullName) {
        return AiStructuredRequests.firstName(fullName);
    }
}
