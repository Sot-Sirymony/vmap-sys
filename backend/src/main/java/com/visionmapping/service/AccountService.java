package com.visionmapping.service;

import com.visionmapping.security.audit.SecurityEventType;
import com.visionmapping.security.audit.SecurityAudit;
import com.visionmapping.dto.request.DeleteAccountRequest;
import com.visionmapping.dto.response.AccountExportResponse;
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
import com.visionmapping.repository.SelfAssessmentItemRepository;
import com.visionmapping.repository.SelfAssessmentSnapshotRepository;
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
import java.util.List;
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
    static final List<String> USER_TABLES_ANONYMISED = List.of("ai_usage_logs", "plan_builder_finished_plans");

    /** Children before parents. A test fails if a table with a user_id is missing here. */
    static final List<String> USER_TABLES_IN_DELETE_ORDER = List.of(
            "plan_builder_drafts",
            "self_assessment_snapshots",
            "self_assessment_items",
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
    private final SelfAssessmentItemRepository selfAssessmentItemRepository;
    private final SelfAssessmentSnapshotRepository selfAssessmentSnapshotRepository;

    /** The password hash is deliberately never part of this. Archived records are. */
    @Transactional(readOnly = true)
    @SecurityAudit(value = SecurityEventType.ACCOUNT_EXPORTED)
    public AccountExportResponse exportData() {
        AppUser user = userScope.currentUser();
        Long userId = user.getId();
        return AccountExportResponse.builder()
                .account(new AccountExportResponse.Account(user.getFullName(), user.getEmail(), user.getReminderEmail(),
                        user.getAiConsentAt(), user.getCohort() == null ? null : user.getCohort().getName(),
                        user.isShareJournalsWithCoach(), user.getCreatedAt()))
                .coachComments(coachCommentService.mine())
                .visionAreas(visionAreaRepository.findByUser_Id(userId).stream().map(mapper::toResponse).toList())
                .dreams(dreamRepository.findByUser_Id(userId).stream().map(mapper::toResponse).toList())
                .goals(goalRepository.findByUser_Id(userId).stream().map(mapper::toResponse).toList())
                .steps(visionStepRepository.findByUser_Id(userId).stream().map(mapper::toResponse).toList())
                .tasks(taskItemRepository.findByUser_Id(userId).stream().map(mapper::toResponse).toList())
                .partners(partnerRepository.findByUser_Id(userId).stream().map(mapper::toResponse).toList())
                .communicationMessages(communicationMessageRepository.findByUser_Id(userId).stream().map(mapper::toResponse).toList())
                .reviews(reviewRepository.findByUser_Id(userId).stream().map(mapper::toResponse).toList())
                .obstacles(obstacleRepository.findByUser_Id(userId).stream().map(mapper::toResponse).toList())
                .progressLogs(progressLogRepository.findByUser_Id(userId).stream().map(mapper::toResponse).toList())
                .gratitudeEntries(gratitudeEntryRepository.findByUser_Id(userId).stream().map(mapper::toResponse).toList())
                .fearEntries(fearEntryRepository.findByUser_Id(userId).stream().map(mapper::toResponse).toList())
                .attitudeCheckIns(attitudeCheckInRepository.findByUser_IdOrderByCheckInDateAsc(userId).stream()
                        .map(mapper::toResponse).toList())
                .habits(habitRepository.findByUser_Id(userId).stream()
                        .map(habit -> new AccountExportResponse.Habit(habit.getName(), habit.isArchived(),
                                habitCheckRepository.findDatesByHabitId(habit.getId())))
                        .toList())
                .assessments(chainAssessmentRepository.findByUser_IdOrderByTakenOnAsc(userId).stream()
                        .map(assessment -> new AccountExportResponse.Assessment(assessment.getTakenOn(),
                                assessment.getSettlingScore(), assessment.getFearScore(), assessment.getCriticismScore(),
                                assessment.getVisionScore(), assessment.getKnowledgeScore(), assessment.getResourcesScore()))
                        .toList())
                .moduleCompletions(moduleCompletionRepository.findByUser_Id(userId).stream()
                        .map(completion -> new AccountExportResponse.ModuleCompletion(completion.getModuleNumber(),
                                completion.getCompletedAt(), completion.getRowsJson(), completion.getCoachRating(),
                                completion.getCoachFeedback(), completion.getCoachRatedAt()))
                        .toList())
                .milestones(milestoneRepository.findByUser_Id(userId).stream()
                        .map(milestone -> new AccountExportResponse.Milestone(milestone.getDream().getId(), milestone.getTitle(),
                                milestone.getAchievedOn(), milestone.getNote(), milestone.isArchived()))
                        .toList())
                .idealPartnerProfiles(idealPartnerProfileRepository.findByUser_Id(userId).stream().map(mapper::toResponse).toList())
                .issueReports(issueReportRepository.findByUser_Id(userId).stream().map(mapper::toResponse).toList())
                .goalSynergyLinks(goalSynergyLinkRepository.findByUser_Id(userId).stream()
                        .map(link -> new AccountExportResponse.GoalSynergyLink(link.getGoal().getId(),
                                link.getRelatedGoal().getId(), link.getNote()))
                        .toList())
                .aiConsentEvents(aiConsentEventRepository.findByUser_IdOrderByCreatedAtAsc(userId).stream()
                        .map(event -> new AccountExportResponse.AiConsentEvent(event.getAction(), event.getCreatedAt()))
                        .toList())
                .aiMessages(aiMessageRepository.findByUser_IdOrderByIdAsc(userId).stream()
                        .map(message -> new AccountExportResponse.AiMessage(message.getConversation().getId(),
                                message.getConversation().getTitle(), message.getRole(), message.getContent(),
                                message.getCreatedAt()))
                        .toList())
                .planShares(planShareRepository.findByUser_IdOrderByCreatedAtDesc(userId).stream()
                        .map(share -> new AccountExportResponse.PlanShare(share.getDream().getId(),
                                share.getPartner() == null ? null : share.getPartner().getId(), share.getMessage(),
                                share.getCreatedAt(), share.getExpiresAt(), share.getRevokedAt(), share.getViewCount()))
                        .toList())
                .selfAssessmentItems(selfAssessmentItemRepository.findByUser_Id(userId).stream().map(mapper::toResponse).toList())
                .selfAssessmentSnapshots(selfAssessmentSnapshotRepository.findByUser_IdOrderByTakenOnAsc(userId).stream()
                        .map(snapshot -> new AccountExportResponse.SelfAssessmentSnapshot(snapshot.getDream().getId(),
                                snapshot.getTakenOn(), snapshot.getItemsJson()))
                        .toList())
                .build();
    }

    @SecurityAudit(value = SecurityEventType.ACCOUNT_DELETED)
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
