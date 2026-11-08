package com.visionmapping.dto.response;

import com.visionmapping.entity.enums.AiConsentAction;
import com.visionmapping.entity.enums.AiMessageRole;
import com.visionmapping.util.Copies;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import lombok.Builder;

/**
 * Everything a participant can download about themselves (GET
 * /api/account/export). The password hash and share-token hashes are never
 * part of it; archived records are.
 *
 * <p>Typed records instead of {@code Map<String, Object>} (BRD 13 EJ-FR-12,
 * Effective Java Item 62). The field order is the JSON order, so it must not
 * change: {@code AccountExportShapeTests} guards it. Built through the
 * builder because it has many fields (Item 2).
 */
@Builder
public record AccountExportResponse(
        Account account,
        List<CoachCommentResponse> coachComments,
        List<VisionAreaResponse> visionAreas,
        List<DreamResponse> dreams,
        List<GoalResponse> goals,
        List<VisionStepResponse> steps,
        List<TaskItemResponse> tasks,
        List<PartnerResponse> partners,
        List<CommunicationMessageResponse> communicationMessages,
        List<ReviewResponse> reviews,
        List<ObstacleResponse> obstacles,
        List<ProgressLogResponse> progressLogs,
        List<GratitudeEntryResponse> gratitudeEntries,
        List<FearEntryResponse> fearEntries,
        List<AttitudeCheckInResponse> attitudeCheckIns,
        List<Habit> habits,
        List<Assessment> assessments,
        List<ModuleCompletion> moduleCompletions,
        List<Milestone> milestones,
        List<IdealPartnerProfileResponse> idealPartnerProfiles,
        List<IssueReportResponse> issueReports,
        List<GoalSynergyLink> goalSynergyLinks,
        List<AiConsentEvent> aiConsentEvents,
        List<AiMessage> aiMessages,
        List<PlanShare> planShares
) {

    public AccountExportResponse {
        coachComments = Copies.list(coachComments);
        visionAreas = Copies.list(visionAreas);
        dreams = Copies.list(dreams);
        goals = Copies.list(goals);
        steps = Copies.list(steps);
        tasks = Copies.list(tasks);
        partners = Copies.list(partners);
        communicationMessages = Copies.list(communicationMessages);
        reviews = Copies.list(reviews);
        obstacles = Copies.list(obstacles);
        progressLogs = Copies.list(progressLogs);
        gratitudeEntries = Copies.list(gratitudeEntries);
        fearEntries = Copies.list(fearEntries);
        attitudeCheckIns = Copies.list(attitudeCheckIns);
        habits = Copies.list(habits);
        assessments = Copies.list(assessments);
        moduleCompletions = Copies.list(moduleCompletions);
        milestones = Copies.list(milestones);
        idealPartnerProfiles = Copies.list(idealPartnerProfiles);
        issueReports = Copies.list(issueReports);
        goalSynergyLinks = Copies.list(goalSynergyLinks);
        aiConsentEvents = Copies.list(aiConsentEvents);
        aiMessages = Copies.list(aiMessages);
        planShares = Copies.list(planShares);
    }


    public record Account(String fullName, String email, String reminderEmail, Instant aiConsentAt, String cohort,
                          boolean shareJournalsWithCoach, Instant createdAt) {
    }

    public record Habit(String name, boolean archived, List<LocalDate> checkedDates) {
        public Habit {
            checkedDates = Copies.list(checkedDates);
        }
    }

    /** The six chain scores, named as the export has always named them. */
    public record Assessment(LocalDate takenOn, int settling, int fear, int criticism, int vision, int knowledge,
                             int resources) {
    }

    public record ModuleCompletion(Integer moduleNumber, Instant completedAt, String rowsJson, Integer coachRating,
                                   String coachFeedback, Instant coachRatedAt) {
    }

    public record Milestone(Long dreamId, String title, LocalDate achievedOn, String note, boolean archived) {
    }

    public record GoalSynergyLink(Long goalId, Long relatedGoalId, String note) {
    }

    /** AI-NFR-03: the consent record. */
    public record AiConsentEvent(AiConsentAction action, Instant at) {
    }

    /** AI-FR-25: Dream Coach history. */
    public record AiMessage(Long conversationId, String conversationTitle, AiMessageRole role, String content,
                            Instant createdAt) {
    }

    /** The token hash is left out: it is a credential, not the user's content. */
    public record PlanShare(Long dreamId, Long partnerId, String message, Instant createdAt, Instant expiresAt,
                            Instant revokedAt, int viewCount) {
    }
}
