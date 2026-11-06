package com.visionmapping.service;

import com.visionmapping.dto.request.DeleteAccountRequest;
import com.visionmapping.entity.AppUser;
import com.visionmapping.exception.BusinessRuleException;
import com.visionmapping.mapper.VisionMappingMapper;
import com.visionmapping.repository.AiConsentEventRepository;
import com.visionmapping.repository.AiMessageRepository;
import com.visionmapping.repository.AttitudeCheckInRepository;
import com.visionmapping.repository.ChainAssessmentRepository;
import com.visionmapping.repository.CommunicationMessageRepository;
import com.visionmapping.repository.DreamRepository;
import com.visionmapping.repository.FearEntryRepository;
import com.visionmapping.repository.GoalRepository;
import com.visionmapping.repository.GoalSynergyLinkRepository;
import com.visionmapping.repository.GratitudeEntryRepository;
import com.visionmapping.repository.HabitCheckRepository;
import com.visionmapping.repository.HabitRepository;
import com.visionmapping.repository.IdealPartnerProfileRepository;
import com.visionmapping.repository.IssueReportRepository;
import com.visionmapping.repository.MilestoneRepository;
import com.visionmapping.repository.ModuleCompletionRepository;
import com.visionmapping.repository.ObstacleRepository;
import com.visionmapping.repository.PartnerRepository;
import com.visionmapping.repository.PlanShareRepository;
import com.visionmapping.repository.ProgressLogRepository;
import com.visionmapping.repository.ReviewRepository;
import com.visionmapping.repository.TaskItemRepository;
import com.visionmapping.repository.VisionAreaRepository;
import com.visionmapping.repository.VisionStepRepository;
import com.visionmapping.util.UserScope;
import jakarta.persistence.EntityManager;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * NFR-09: everything a user has can leave with them, or be erased. Deletion is
 * the one permanent delete with no archive step, because the user asked for
 * their data to be gone. It needs their password and runs in one transaction,
 * so it either removes everything or nothing.
 */
@Service
@Transactional
@RequiredArgsConstructor
public class AccountService {

    /**
     * AI-NFR-14: the usage log keeps no conversation text and its cost still
     * counts against the month's budget, so a deleted account only loses its
     * link to these rows.
     */
    static final List<String> USER_TABLES_ANONYMISED = List.of("ai_usage_logs");

    /** Children before parents. A test fails if a table with a user_id is missing here. */
    static final List<String> USER_TABLES_IN_DELETE_ORDER = List.of(
            "ai_messages",
            "ai_conversations",
            "ai_consent_events",
            "ai_safety_alerts",
            "coach_comments",
            "plan_shares",
            "password_reset_tokens",
            "auth_sessions",
            "issue_reports",
            "module_completions",
            "chain_assessments",
            "milestones",
            "attitude_checkins",
            "habits",
            "fear_entries",
            "gratitude_entries",
            "progress_logs",
            "communication_messages",
            "obstacles",
            "ideal_partner_profiles",
            "goal_synergy_links",
            "partners",
            "reviews",
            "task_items",
            "vision_steps",
            "goals",
            "dreams",
            "vision_areas");

    private final UserScope userScope;
    private final CoachCommentService coachCommentService;
    private final PasswordEncoder passwordEncoder;
    private final JdbcTemplate jdbcTemplate;
    private final EntityManager entityManager;
    private final VisionMappingMapper mapper;
    private final VisionAreaRepository visionAreaRepository;
    private final DreamRepository dreamRepository;
    private final GoalRepository goalRepository;
    private final VisionStepRepository visionStepRepository;
    private final TaskItemRepository taskItemRepository;
    private final PartnerRepository partnerRepository;
    private final CommunicationMessageRepository communicationMessageRepository;
    private final ReviewRepository reviewRepository;
    private final ObstacleRepository obstacleRepository;
    private final ProgressLogRepository progressLogRepository;
    private final GratitudeEntryRepository gratitudeEntryRepository;
    private final FearEntryRepository fearEntryRepository;
    private final AttitudeCheckInRepository attitudeCheckInRepository;
    private final HabitRepository habitRepository;
    private final HabitCheckRepository habitCheckRepository;
    private final ChainAssessmentRepository chainAssessmentRepository;
    private final ModuleCompletionRepository moduleCompletionRepository;
    private final MilestoneRepository milestoneRepository;
    private final IdealPartnerProfileRepository idealPartnerProfileRepository;
    private final IssueReportRepository issueReportRepository;
    private final GoalSynergyLinkRepository goalSynergyLinkRepository;
    private final PlanShareRepository planShareRepository;
    private final AiConsentEventRepository aiConsentEventRepository;
    private final AiMessageRepository aiMessageRepository;

    /** The password hash is deliberately never part of this. Archived records are. */
    @Transactional(readOnly = true)
    public Map<String, Object> exportData() {
        AppUser user = userScope.currentUser();
        Long userId = user.getId();
        Map<String, Object> data = new LinkedHashMap<>();
        Map<String, Object> account = new LinkedHashMap<>();
        account.put("fullName", user.getFullName());
        account.put("email", user.getEmail());
        account.put("reminderEmail", user.getReminderEmail());
        account.put("aiConsentAt", user.getAiConsentAt());
        account.put("cohort", user.getCohort() == null ? null : user.getCohort().getName());
        account.put("shareJournalsWithCoach", user.isShareJournalsWithCoach());
        account.put("createdAt", user.getCreatedAt());
        data.put("account", account);
        data.put("coachComments", coachCommentService.mine());
        data.put("visionAreas", visionAreaRepository.findByUser_Id(userId).stream().map(mapper::toResponse).toList());
        data.put("dreams", dreamRepository.findByUser_Id(userId).stream().map(mapper::toResponse).toList());
        data.put("goals", goalRepository.findByUser_Id(userId).stream().map(mapper::toResponse).toList());
        data.put("steps", visionStepRepository.findByUser_Id(userId).stream().map(mapper::toResponse).toList());
        data.put("tasks", taskItemRepository.findByUser_Id(userId).stream().map(mapper::toResponse).toList());
        data.put("partners", partnerRepository.findByUser_Id(userId).stream().map(mapper::toResponse).toList());
        data.put("communicationMessages", communicationMessageRepository.findByUser_Id(userId).stream().map(mapper::toResponse).toList());
        data.put("reviews", reviewRepository.findByUser_Id(userId).stream().map(mapper::toResponse).toList());
        data.put("obstacles", obstacleRepository.findByUser_Id(userId).stream().map(mapper::toResponse).toList());
        data.put("progressLogs", progressLogRepository.findByUser_Id(userId).stream().map(mapper::toResponse).toList());
        data.put("gratitudeEntries", gratitudeEntryRepository.findByUser_Id(userId).stream().map(mapper::toResponse).toList());
        data.put("fearEntries", fearEntryRepository.findByUser_Id(userId).stream().map(mapper::toResponse).toList());
        data.put("attitudeCheckIns", attitudeCheckInRepository.findByUser_IdOrderByCheckInDateAsc(userId).stream().map(mapper::toResponse).toList());
        data.put("habits", habitRepository.findByUser_Id(userId).stream()
                .map(habit -> Map.of("name", habit.getName(), "archived", habit.isArchived(),
                        "checkedDates", habitCheckRepository.findDatesByHabitId(habit.getId())))
                .toList());
        data.put("assessments", chainAssessmentRepository.findByUser_IdOrderByTakenOnAsc(userId).stream()
                .map(assessment -> Map.of("takenOn", assessment.getTakenOn(), "settling", assessment.getSettlingScore(),
                        "fear", assessment.getFearScore(), "criticism", assessment.getCriticismScore(), "vision", assessment.getVisionScore(),
                        "knowledge", assessment.getKnowledgeScore(), "resources", assessment.getResourcesScore()))
                .toList());
        data.put("moduleCompletions", moduleCompletionRepository.findByUser_Id(userId).stream()
                .map(completion -> {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("moduleNumber", completion.getModuleNumber());
                    row.put("completedAt", completion.getCompletedAt());
                    row.put("rowsJson", completion.getRowsJson());
                    row.put("coachRating", completion.getCoachRating());
                    row.put("coachFeedback", completion.getCoachFeedback());
                    row.put("coachRatedAt", completion.getCoachRatedAt());
                    return row;
                })
                .toList());
        data.put("milestones", milestoneRepository.findByUser_Id(userId).stream()
                .map(milestone -> {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("dreamId", milestone.getDream().getId());
                    row.put("title", milestone.getTitle());
                    row.put("achievedOn", milestone.getAchievedOn());
                    row.put("note", milestone.getNote());
                    row.put("archived", milestone.isArchived());
                    return row;
                })
                .toList());
        data.put("idealPartnerProfiles", idealPartnerProfileRepository.findByUser_Id(userId).stream().map(mapper::toResponse).toList());
        data.put("issueReports", issueReportRepository.findByUser_Id(userId).stream().map(mapper::toResponse).toList());
        data.put("goalSynergyLinks", goalSynergyLinkRepository.findByUser_Id(userId).stream()
                .map(link -> {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("goalId", link.getGoal().getId());
                    row.put("relatedGoalId", link.getRelatedGoal().getId());
                    row.put("note", link.getNote());
                    return row;
                })
                .toList());
        // AI-FR-25 and AI-NFR-03: Dream Coach history and the consent record.
        data.put("aiConsentEvents", aiConsentEventRepository.findByUser_IdOrderByCreatedAtAsc(userId).stream()
                .map(event -> Map.of("action", event.getAction(), "at", event.getCreatedAt()))
                .toList());
        data.put("aiMessages", aiMessageRepository.findByUser_IdOrderByIdAsc(userId).stream()
                .map(message -> {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("conversationId", message.getConversation().getId());
                    row.put("conversationTitle", message.getConversation().getTitle());
                    row.put("role", message.getRole());
                    row.put("content", message.getContent());
                    row.put("createdAt", message.getCreatedAt());
                    return row;
                })
                .toList());
        // The token hash is left out: it is a credential, not the user's content.
        data.put("planShares", planShareRepository.findByUser_IdOrderByCreatedAtDesc(userId).stream()
                .map(share -> {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("dreamId", share.getDream().getId());
                    row.put("partnerId", share.getPartner() == null ? null : share.getPartner().getId());
                    row.put("message", share.getMessage());
                    row.put("createdAt", share.getCreatedAt());
                    row.put("expiresAt", share.getExpiresAt());
                    row.put("revokedAt", share.getRevokedAt());
                    row.put("viewCount", share.getViewCount());
                    return row;
                })
                .toList());
        return data;
    }

    public void deleteAccount(DeleteAccountRequest request) {
        AppUser user = userScope.currentUser();
        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new BusinessRuleException("That password is not correct.");
        }
        Long userId = user.getId();
        entityManager.clear();
        for (String table : USER_TABLES_ANONYMISED) {
            jdbcTemplate.update("UPDATE " + table + " SET user_id = NULL WHERE user_id = ?", userId);
        }
        for (String table : USER_TABLES_IN_DELETE_ORDER) {
            jdbcTemplate.update("DELETE FROM " + table + " WHERE user_id = ?", userId);
        }
        jdbcTemplate.update("DELETE FROM app_users WHERE id = ?", userId);
    }
}
