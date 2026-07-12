package com.visionmapping.service;

import com.visionmapping.dto.request.CommunicationMessageRequest;
import com.visionmapping.dto.request.DreamRequest;
import com.visionmapping.dto.request.GoalRequest;
import com.visionmapping.dto.request.ObstacleRequest;
import com.visionmapping.dto.request.PartnerRequest;
import com.visionmapping.dto.request.ProgressLogRequest;
import com.visionmapping.dto.request.ReviewRequest;
import com.visionmapping.dto.request.TaskItemRequest;
import com.visionmapping.dto.request.VisionAreaRequest;
import com.visionmapping.dto.request.VisionStepRequest;
import com.visionmapping.dto.response.ArchiveImpactResponse;
import com.visionmapping.dto.response.CommunicationMessageResponse;
import com.visionmapping.dto.response.DashboardSummaryResponse;
import com.visionmapping.dto.response.DreamResponse;
import com.visionmapping.dto.response.GoalResponse;
import com.visionmapping.dto.response.ObstacleResponse;
import com.visionmapping.dto.response.PartnerResponse;
import com.visionmapping.dto.response.ProgressLogResponse;
import com.visionmapping.dto.response.ReviewResponse;
import com.visionmapping.dto.response.TaskItemResponse;
import com.visionmapping.dto.response.VisionAreaResponse;
import com.visionmapping.dto.response.VisionStepResponse;
import com.visionmapping.entity.AppUser;
import com.visionmapping.entity.CommunicationMessage;
import com.visionmapping.entity.Dream;
import com.visionmapping.entity.Goal;
import com.visionmapping.entity.Obstacle;
import com.visionmapping.entity.Partner;
import com.visionmapping.entity.ProgressLog;
import com.visionmapping.entity.Review;
import com.visionmapping.entity.TaskItem;
import com.visionmapping.entity.VisionArea;
import com.visionmapping.entity.VisionStep;
import com.visionmapping.entity.enums.CommunicationStatus;
import com.visionmapping.entity.enums.DreamStatus;
import com.visionmapping.entity.enums.LifecycleStatus;
import com.visionmapping.entity.enums.ObstacleStatus;
import com.visionmapping.entity.enums.PartnerStatus;
import com.visionmapping.entity.enums.ReviewType;
import com.visionmapping.entity.enums.WorkStatus;
import com.visionmapping.exception.BusinessRuleException;
import com.visionmapping.exception.ResourceNotFoundException;
import com.visionmapping.mapper.VisionMappingMapper;
import com.visionmapping.repository.CommunicationMessageRepository;
import com.visionmapping.repository.DreamRepository;
import com.visionmapping.repository.GoalRepository;
import com.visionmapping.repository.ObstacleRepository;
import com.visionmapping.repository.PartnerRepository;
import com.visionmapping.repository.ProgressLogRepository;
import com.visionmapping.repository.ReviewRepository;
import com.visionmapping.repository.TaskItemRepository;
import com.visionmapping.repository.VisionAreaRepository;
import com.visionmapping.repository.VisionStepRepository;
import com.visionmapping.util.UserScope;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.WeekFields;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional
public class VisionMappingService {

    private static final BigDecimal ZERO = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
    private static final BigDecimal ONE_HUNDRED = BigDecimal.valueOf(100).setScale(2, RoundingMode.HALF_UP);

    private final UserScope userScope;
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
    private final Clock clock;

    @Transactional(readOnly = true)
    public List<VisionAreaResponse> listVisionAreas(boolean includeArchived) {
        List<VisionArea> entities = includeArchived
                ? visionAreaRepository.findByUser_Id(userId())
                : visionAreaRepository.findByUser_IdAndArchivedFalse(userId());
        return entities.stream().map(mapper::toResponse).toList();
    }

    public VisionAreaResponse createVisionArea(VisionAreaRequest request) {
        AppUser user = userScope.currentUser();
        VisionArea entity = VisionArea.builder()
                .code(nextCode("VA", visionAreaRepository.findByUser_Id(user.getId()).size()))
                .user(user)
                .name(request.name())
                .description(request.description())
                .priority(request.priority())
                .status(request.status())
                .build();
        return mapper.toResponse(visionAreaRepository.save(entity));
    }

    @Transactional(readOnly = true)
    public VisionAreaResponse getVisionArea(Long id) {
        return mapper.toResponse(visionArea(id));
    }

    public VisionAreaResponse updateVisionArea(Long id, VisionAreaRequest request) {
        VisionArea entity = visionArea(id);
        entity.setName(request.name());
        entity.setDescription(request.description());
        entity.setPriority(request.priority());
        entity.setStatus(request.status());
        return mapper.toResponse(entity);
    }

    public VisionAreaResponse updateVisionAreaStatus(Long id, String status) {
        VisionArea entity = visionArea(id);
        entity.setStatus(parse(LifecycleStatus.class, status));
        return mapper.toResponse(entity);
    }

    public void archiveVisionArea(Long id) {
        VisionArea entity = visionArea(id);
        entity.setStatus(LifecycleStatus.ARCHIVED);
        entity.setArchived(true);
        cascadeArchiveDreams(entity.getId());
    }

    @Transactional(readOnly = true)
    public List<DreamResponse> listDreams(boolean includeArchived) {
        List<Dream> entities = includeArchived
                ? dreamRepository.findByUser_Id(userId())
                : dreamRepository.findByUser_IdAndArchivedFalse(userId());
        return entities.stream().map(mapper::toResponse).toList();
    }

    public DreamResponse createDream(DreamRequest request) {
        AppUser user = userScope.currentUser();
        VisionArea visionArea = visionArea(request.visionAreaId());
        Dream entity = Dream.builder()
                .code(nextCode("D", dreamRepository.findByUser_Id(user.getId()).size()))
                .user(user)
                .visionArea(visionArea)
                .title(request.title())
                .description(request.description())
                .whyImportant(request.whyImportant())
                .successDefinition(request.successDefinition())
                .dreamType(request.dreamType())
                .priority(request.priority())
                .targetDate(request.targetDate())
                .status(request.status())
                .build();
        return mapper.toResponse(dreamRepository.save(entity));
    }

    @Transactional(readOnly = true)
    public DreamResponse getDream(Long id) {
        return mapper.toResponse(dream(id));
    }

    public DreamResponse updateDream(Long id, DreamRequest request) {
        Dream entity = dream(id);
        entity.setVisionArea(visionArea(request.visionAreaId()));
        entity.setTitle(request.title());
        entity.setDescription(request.description());
        entity.setWhyImportant(request.whyImportant());
        entity.setSuccessDefinition(request.successDefinition());
        entity.setDreamType(request.dreamType());
        entity.setPriority(request.priority());
        entity.setTargetDate(request.targetDate());
        entity.setStatus(request.status());
        return mapper.toResponse(entity);
    }

    public DreamResponse updateDreamStatus(Long id, String status) {
        Dream entity = dream(id);
        entity.setStatus(parse(DreamStatus.class, status));
        return mapper.toResponse(entity);
    }

    public void archiveDream(Long id) {
        Dream entity = dream(id);
        entity.setStatus(DreamStatus.ARCHIVED);
        entity.setArchived(true);
        cascadeArchiveGoals(entity.getId());
    }

    @Transactional(readOnly = true)
    public List<GoalResponse> listGoals(boolean includeArchived) {
        List<Goal> entities = includeArchived
                ? goalRepository.findByUser_Id(userId())
                : goalRepository.findByUser_IdAndArchivedFalse(userId());
        return entities.stream().map(mapper::toResponse).toList();
    }

    public GoalResponse createGoal(GoalRequest request) {
        AppUser user = userScope.currentUser();
        Dream dream = dream(request.dreamId());
        Goal entity = Goal.builder()
                .code(nextCode("G", goalRepository.findByUser_Id(user.getId()).size()))
                .user(user)
                .dream(dream)
                .title(request.title())
                .description(request.description())
                .successCriteria(request.successCriteria())
                .priority(request.priority())
                .targetDate(request.targetDate())
                .status(request.status())
                .progressPercent(ZERO)
                .manualProgressOverride(false)
                .moonshot(request.moonshot())
                .moonshotVision(request.moonshotVision())
                .build();
        return mapper.toResponse(goalRepository.save(entity));
    }

    @Transactional(readOnly = true)
    public GoalResponse getGoal(Long id) {
        return mapper.toResponse(goal(id));
    }

    public GoalResponse updateGoal(Long id, GoalRequest request) {
        Goal entity = goal(id);
        entity.setDream(dream(request.dreamId()));
        entity.setTitle(request.title());
        entity.setDescription(request.description());
        entity.setSuccessCriteria(request.successCriteria());
        entity.setPriority(request.priority());
        entity.setTargetDate(request.targetDate());
        entity.setStatus(request.status());
        entity.setMoonshot(request.moonshot());
        entity.setMoonshotVision(request.moonshotVision());
        validateGoalCompletion(entity, false);
        return mapper.toResponse(entity);
    }

    public GoalResponse updateGoalStatus(Long id, String status, boolean manualOverride) {
        Goal entity = goal(id);
        entity.setStatus(parse(WorkStatus.class, status));
        validateGoalCompletion(entity, manualOverride);
        if (manualOverride) {
            entity.setManualProgressOverride(true);
        }
        return mapper.toResponse(entity);
    }

    public void archiveGoal(Long id) {
        Goal entity = goal(id);
        entity.setArchived(true);
        cascadeArchiveSteps(entity.getId());
    }

    @Transactional(readOnly = true)
    public List<VisionStepResponse> listSteps(boolean includeArchived) {
        List<VisionStep> entities = includeArchived
                ? visionStepRepository.findByUser_Id(userId())
                : visionStepRepository.findByUser_IdAndArchivedFalse(userId());
        return entities.stream().map(mapper::toResponse).toList();
    }

    public VisionStepResponse createStep(VisionStepRequest request) {
        AppUser user = userScope.currentUser();
        Goal goal = goal(request.goalId());
        VisionStep entity = VisionStep.builder()
                .code(nextCode("S", visionStepRepository.findByUser_Id(user.getId()).size()))
                .user(user)
                .goal(goal)
                .title(request.title())
                .description(request.description())
                .sequenceNumber(request.sequenceNumber())
                .complex(request.complex())
                .priority(request.priority())
                .targetDate(request.targetDate())
                .status(request.status())
                .progressPercent(ZERO)
                .manualProgressOverride(false)
                .build();
        VisionStep saved = visionStepRepository.save(entity);
        recalculateGoal(goal);
        return mapper.toResponse(saved);
    }

    @Transactional(readOnly = true)
    public VisionStepResponse getStep(Long id) {
        return mapper.toResponse(step(id));
    }

    public VisionStepResponse updateStep(Long id, VisionStepRequest request) {
        VisionStep entity = step(id);
        Goal oldGoal = entity.getGoal();
        entity.setGoal(goal(request.goalId()));
        entity.setTitle(request.title());
        entity.setDescription(request.description());
        entity.setSequenceNumber(request.sequenceNumber());
        entity.setComplex(request.complex());
        entity.setPriority(request.priority());
        entity.setTargetDate(request.targetDate());
        entity.setStatus(request.status());
        validateComplexStep(entity);
        recalculateGoal(oldGoal);
        recalculateGoal(entity.getGoal());
        return mapper.toResponse(entity);
    }

    public VisionStepResponse updateStepStatus(Long id, String status, boolean manualOverride) {
        VisionStep entity = step(id);
        entity.setStatus(parse(WorkStatus.class, status));
        validateComplexStep(entity);
        if (manualOverride) {
            entity.setManualProgressOverride(true);
        }
        recalculateGoal(entity.getGoal());
        return mapper.toResponse(entity);
    }

    public void archiveStep(Long id) {
        VisionStep entity = step(id);
        entity.setArchived(true);
        cascadeArchiveTasks(entity.getId());
        recalculateGoal(entity.getGoal());
    }

    @Transactional(readOnly = true)
    public List<TaskItemResponse> listTasks(boolean includeArchived) {
        List<TaskItem> entities = includeArchived
                ? taskItemRepository.findByUser_Id(userId())
                : taskItemRepository.findByUser_IdAndArchivedFalse(userId());
        return entities.stream().map(mapper::toResponse).toList();
    }

    public TaskItemResponse createTask(TaskItemRequest request) {
        AppUser user = userScope.currentUser();
        VisionStep step = step(request.stepId());
        TaskItem entity = TaskItem.builder()
                .code(nextCode("T", taskItemRepository.findByUser_Id(user.getId()).size()))
                .user(user)
                .step(step)
                .title(request.title())
                .description(request.description())
                .owner(request.owner())
                .priority(request.priority())
                .startDate(request.startDate())
                .dueDate(request.dueDate())
                .status(request.status())
                .progressPercent(normalizeProgress(request.progressPercent()))
                .estimatedHours(request.estimatedHours())
                .actualHours(request.actualHours())
                .blockerReason(request.blockerReason())
                .nextAction(request.nextAction())
                .build();
        prepareTask(entity);
        TaskItem saved = taskItemRepository.save(entity);
        recalculateStep(step);
        return mapper.toResponse(saved);
    }

    @Transactional(readOnly = true)
    public TaskItemResponse getTask(Long id) {
        return mapper.toResponse(task(id));
    }

    public TaskItemResponse updateTask(Long id, TaskItemRequest request) {
        TaskItem entity = task(id);
        BigDecimal progressBefore = entity.getProgressPercent();
        VisionStep oldStep = entity.getStep();
        entity.setStep(step(request.stepId()));
        entity.setTitle(request.title());
        entity.setDescription(request.description());
        entity.setOwner(request.owner());
        entity.setPriority(request.priority());
        entity.setStartDate(request.startDate());
        entity.setDueDate(request.dueDate());
        entity.setStatus(request.status());
        entity.setProgressPercent(normalizeProgress(request.progressPercent()));
        entity.setEstimatedHours(request.estimatedHours());
        entity.setActualHours(request.actualHours());
        entity.setBlockerReason(request.blockerReason());
        entity.setNextAction(request.nextAction());
        prepareTask(entity);
        recalculateStep(oldStep);
        recalculateStep(entity.getStep());
        logProgressChange(entity, progressBefore);
        return mapper.toResponse(entity);
    }

    public TaskItemResponse updateTaskStatus(Long id, String status) {
        TaskItem entity = task(id);
        BigDecimal progressBefore = entity.getProgressPercent();
        entity.setStatus(parse(WorkStatus.class, status));
        prepareTask(entity);
        recalculateStep(entity.getStep());
        logProgressChange(entity, progressBefore);
        return mapper.toResponse(entity);
    }

    public void archiveTask(Long id) {
        TaskItem entity = task(id);
        entity.setArchived(true);
        recalculateStep(entity.getStep());
    }

    private void logProgressChange(TaskItem entity, BigDecimal progressBefore) {
        BigDecimal progressAfter = entity.getProgressPercent();
        if (progressBefore.compareTo(progressAfter) == 0) {
            return;
        }
        ProgressLog entry = ProgressLog.builder()
                .user(entity.getUser())
                .relatedTask(entity)
                .progressPercentBefore(progressBefore)
                .progressPercentAfter(progressAfter)
                .loggedAt(Instant.now(clock))
                .archived(false)
                .build();
        progressLogRepository.save(entry);
    }

    @Transactional(readOnly = true)
    public Page<PartnerResponse> listPartners(Pageable pageable, boolean includeArchived) {
        Page<Partner> entities = includeArchived
                ? partnerRepository.findByUser_Id(userId(), pageable)
                : partnerRepository.findByUser_IdAndArchivedFalse(userId(), pageable);
        return entities.map(mapper::toResponse);
    }

    public PartnerResponse createPartner(PartnerRequest request) {
        AppUser user = userScope.currentUser();
        Partner entity = Partner.builder()
                .code(nextCode("P", partnerRepository.findByUser_Id(user.getId()).size()))
                .user(user)
                .name(request.name())
                .role(request.role())
                .organization(request.organization())
                .email(request.email())
                .phone(request.phone())
                .strength(request.strength())
                .supportType(request.supportType())
                .relatedVisionArea(optionalVisionArea(request.relatedVisionAreaId()))
                .relatedDream(optionalDream(request.relatedDreamId()))
                .relatedGoal(optionalGoal(request.relatedGoalId()))
                .relatedStep(optionalStep(request.relatedStepId()))
                .relatedTask(optionalTask(request.relatedTaskId()))
                .status(request.status())
                .notes(request.notes())
                .build();
        return mapper.toResponse(partnerRepository.save(entity));
    }

    @Transactional(readOnly = true)
    public PartnerResponse getPartner(Long id) {
        return mapper.toResponse(partner(id));
    }

    public PartnerResponse updatePartner(Long id, PartnerRequest request) {
        Partner entity = partner(id);
        entity.setName(request.name());
        entity.setRole(request.role());
        entity.setOrganization(request.organization());
        entity.setEmail(request.email());
        entity.setPhone(request.phone());
        entity.setStrength(request.strength());
        entity.setSupportType(request.supportType());
        entity.setRelatedVisionArea(optionalVisionArea(request.relatedVisionAreaId()));
        entity.setRelatedDream(optionalDream(request.relatedDreamId()));
        entity.setRelatedGoal(optionalGoal(request.relatedGoalId()));
        entity.setRelatedStep(optionalStep(request.relatedStepId()));
        entity.setRelatedTask(optionalTask(request.relatedTaskId()));
        entity.setStatus(request.status());
        entity.setNotes(request.notes());
        return mapper.toResponse(entity);
    }

    public PartnerResponse updatePartnerStatus(Long id, String status) {
        Partner entity = partner(id);
        entity.setStatus(parse(PartnerStatus.class, status));
        return mapper.toResponse(entity);
    }

    public void archivePartner(Long id) {
        partner(id).setArchived(true);
    }

    @Transactional(readOnly = true)
    public Page<CommunicationMessageResponse> listCommunicationMessages(Pageable pageable, boolean includeArchived) {
        Page<CommunicationMessage> entities = includeArchived
                ? communicationMessageRepository.findByUser_Id(userId(), pageable)
                : communicationMessageRepository.findByUser_IdAndArchivedFalse(userId(), pageable);
        return entities.map(mapper::toResponse);
    }

    public CommunicationMessageResponse createCommunicationMessage(CommunicationMessageRequest request) {
        CommunicationMessage entity = new CommunicationMessage();
        entity.setUser(userScope.currentUser());
        applyCommunicationRequest(entity, request);
        return mapper.toResponse(communicationMessageRepository.save(entity));
    }

    @Transactional(readOnly = true)
    public CommunicationMessageResponse getCommunicationMessage(Long id) {
        return mapper.toResponse(communicationMessage(id));
    }

    public CommunicationMessageResponse updateCommunicationMessage(Long id, CommunicationMessageRequest request) {
        CommunicationMessage entity = communicationMessage(id);
        applyCommunicationRequest(entity, request);
        return mapper.toResponse(entity);
    }

    public CommunicationMessageResponse updateCommunicationStatus(Long id, String status) {
        CommunicationMessage entity = communicationMessage(id);
        entity.setStatus(parse(CommunicationStatus.class, status));
        return mapper.toResponse(entity);
    }

    public void archiveCommunicationMessage(Long id) {
        communicationMessage(id).setArchived(true);
    }

    @Transactional(readOnly = true)
    public List<ReviewResponse> listReviews(boolean includeArchived) {
        List<Review> entities = includeArchived
                ? reviewRepository.findByUser_Id(userId())
                : reviewRepository.findByUser_IdAndArchivedFalse(userId());
        return entities.stream().map(mapper::toResponse).toList();
    }

    public ReviewResponse createReview(ReviewRequest request) {
        validateDiligenceChecklist(request);
        Review entity = Review.builder()
                .user(userScope.currentUser())
                .reviewType(request.reviewType())
                .reviewDate(request.reviewDate())
                .relatedVisionArea(optionalVisionArea(request.relatedVisionAreaId()))
                .relatedDream(optionalDream(request.relatedDreamId()))
                .summary(request.summary())
                .completedTasks(request.completedTasks())
                .delayedTasks(request.delayedTasks())
                .blockedTasks(request.blockedTasks())
                .lessonsLearned(request.lessonsLearned())
                .nextActions(request.nextActions())
                .diligenceClearVision(request.diligenceClearVision())
                .diligenceWorkedPlan(request.diligenceWorkedPlan())
                .diligenceUsedLeverage(request.diligenceUsedLeverage())
                .diligencePriorityFirst(request.diligencePriorityFirst())
                .diligenceSmarterRoute(request.diligenceSmarterRoute())
                .diligenceNote(request.diligenceNote())
                .build();
        return mapper.toResponse(reviewRepository.save(entity));
    }

    /**
     * FR-16: the diligence checklist is answered as a whole or skipped as a
     * whole — a half-answered checklist would silently read as "not met" on
     * the unanswered questions.
     */
    private void validateDiligenceChecklist(ReviewRequest request) {
        List<Boolean> answers = new ArrayList<>();
        answers.add(request.diligenceClearVision());
        answers.add(request.diligenceWorkedPlan());
        answers.add(request.diligenceUsedLeverage());
        answers.add(request.diligencePriorityFirst());
        answers.add(request.diligenceSmarterRoute());
        long answered = answers.stream().filter(java.util.Objects::nonNull).count();
        if (answered != 0 && answered != answers.size()) {
            throw new BusinessRuleException("Answer every diligence question, or skip the whole checklist.");
        }
    }

    @Transactional(readOnly = true)
    public ReviewResponse getReview(Long id) {
        return mapper.toResponse(review(id));
    }

    public ReviewResponse updateReview(Long id, ReviewRequest request) {
        validateDiligenceChecklist(request);
        Review entity = review(id);
        entity.setReviewType(request.reviewType());
        entity.setReviewDate(request.reviewDate());
        entity.setRelatedVisionArea(optionalVisionArea(request.relatedVisionAreaId()));
        entity.setRelatedDream(optionalDream(request.relatedDreamId()));
        entity.setSummary(request.summary());
        entity.setCompletedTasks(request.completedTasks());
        entity.setDelayedTasks(request.delayedTasks());
        entity.setBlockedTasks(request.blockedTasks());
        entity.setLessonsLearned(request.lessonsLearned());
        entity.setNextActions(request.nextActions());
        entity.setDiligenceClearVision(request.diligenceClearVision());
        entity.setDiligenceWorkedPlan(request.diligenceWorkedPlan());
        entity.setDiligenceUsedLeverage(request.diligenceUsedLeverage());
        entity.setDiligencePriorityFirst(request.diligencePriorityFirst());
        entity.setDiligenceSmarterRoute(request.diligenceSmarterRoute());
        entity.setDiligenceNote(request.diligenceNote());
        return mapper.toResponse(entity);
    }

    public void archiveReview(Long id) {
        review(id).setArchived(true);
    }

    @Transactional(readOnly = true)
    public List<ObstacleResponse> listObstacles(boolean includeArchived) {
        List<Obstacle> entities = includeArchived
                ? obstacleRepository.findByUser_Id(userId())
                : obstacleRepository.findByUser_IdAndArchivedFalse(userId());
        return entities.stream().map(mapper::toResponse).toList();
    }

    public ObstacleResponse createObstacle(ObstacleRequest request) {
        Obstacle entity = Obstacle.builder()
                .user(userScope.currentUser())
                .relatedDream(optionalDream(request.relatedDreamId()))
                .relatedGoal(optionalGoal(request.relatedGoalId()))
                .relatedStep(optionalStep(request.relatedStepId()))
                .relatedTask(optionalTask(request.relatedTaskId()))
                .title(request.title())
                .description(request.description())
                .obstacleType(request.obstacleType())
                .severity(request.severity())
                .solution(request.solution())
                .requiredPartner(optionalPartner(request.requiredPartnerId()))
                .status(request.status())
                .build();
        return mapper.toResponse(obstacleRepository.save(entity));
    }

    @Transactional(readOnly = true)
    public ObstacleResponse getObstacle(Long id) {
        return mapper.toResponse(obstacle(id));
    }

    public ObstacleResponse updateObstacle(Long id, ObstacleRequest request) {
        Obstacle entity = obstacle(id);
        entity.setRelatedDream(optionalDream(request.relatedDreamId()));
        entity.setRelatedGoal(optionalGoal(request.relatedGoalId()));
        entity.setRelatedStep(optionalStep(request.relatedStepId()));
        entity.setRelatedTask(optionalTask(request.relatedTaskId()));
        entity.setTitle(request.title());
        entity.setDescription(request.description());
        entity.setObstacleType(request.obstacleType());
        entity.setSeverity(request.severity());
        entity.setSolution(request.solution());
        entity.setRequiredPartner(optionalPartner(request.requiredPartnerId()));
        entity.setStatus(request.status());
        return mapper.toResponse(entity);
    }

    public ObstacleResponse updateObstacleStatus(Long id, String status) {
        Obstacle entity = obstacle(id);
        entity.setStatus(parse(ObstacleStatus.class, status));
        return mapper.toResponse(entity);
    }

    public void archiveObstacle(Long id) {
        obstacle(id).setArchived(true);
    }

    @Transactional(readOnly = true)
    public List<ProgressLogResponse> listProgressLogs() {
        return progressLogRepository.findByUser_IdAndArchivedFalse(userId()).stream().map(mapper::toResponse).toList();
    }

    public ProgressLogResponse createProgressLog(ProgressLogRequest request) {
        AppUser user = userScope.currentUser();
        TaskItem task = task(request.relatedTaskId());
        ProgressLog entity = ProgressLog.builder()
                .user(user)
                .relatedTask(task)
                .progressPercentBefore(normalizeProgress(request.progressPercentBefore()))
                .progressPercentAfter(normalizeProgress(request.progressPercentAfter()))
                .note(request.note())
                .loggedAt(Instant.now(clock))
                .build();
        task.setProgressPercent(entity.getProgressPercentAfter());
        if (ONE_HUNDRED.compareTo(entity.getProgressPercentAfter()) == 0) {
            task.setStatus(WorkStatus.COMPLETED);
            task.setCompletedAt(Instant.now(clock));
        }
        recalculateStep(task.getStep());
        return mapper.toResponse(progressLogRepository.save(entity));
    }

    @Transactional(readOnly = true)
    public ProgressLogResponse getProgressLog(Long id) {
        return mapper.toResponse(progressLog(id));
    }

    public void archiveProgressLog(Long id) {
        progressLog(id).setArchived(true);
    }

    @Transactional(readOnly = true)
    public DashboardSummaryResponse buildDashboardSummary() {
        Long userId = userId();
        List<VisionArea> areas = visionAreaRepository.findByUser_IdAndArchivedFalse(userId);
        List<Dream> dreams = dreamRepository.findByUser_IdAndArchivedFalse(userId);
        List<Goal> goals = goalRepository.findByUser_IdAndArchivedFalse(userId);
        List<TaskItem> tasks = taskItemRepository.findByUser_IdAndArchivedFalse(userId);
        List<Obstacle> obstacles = obstacleRepository.findByUser_IdAndArchivedFalse(userId);
        List<Partner> partners = partnerRepository.findByUser_IdAndArchivedFalse(userId);
        List<Review> reviews = reviewRepository.findByUser_IdAndArchivedFalse(userId);
        List<ProgressLog> progressLogs = progressLogRepository.findByUser_IdAndArchivedFalse(userId);
        LocalDate today = LocalDate.now(clock);
        LocalDate weekEnd = today.plusDays(7);
        BigDecimal averageProgress = goals.isEmpty()
                ? ZERO
                : goals.stream()
                .map(Goal::getProgressPercent)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .divide(BigDecimal.valueOf(goals.size()), 2, RoundingMode.HALF_UP);
        long moonshotGoals = goals.stream().filter(Goal::isMoonshot).count();
        Map<String, Long> goalsByStatus = goals.stream()
                .collect(Collectors.groupingBy(goal -> goal.getStatus().name(), Collectors.counting()));
        Map<String, Long> dreamsByArea = dreams.stream()
                .collect(Collectors.groupingBy(dream -> dream.getVisionArea().getName(), Collectors.counting()));
        Map<String, Long> tasksByStatus = tasks.stream()
                .collect(Collectors.groupingBy(task -> task.getStatus().name(), Collectors.counting()));
        Map<String, Long> tasksByPriority = tasks.stream()
                .collect(Collectors.groupingBy(task -> task.getPriority().name(), Collectors.counting()));
        Map<String, Long> activeObstaclesByType = obstacles.stream()
                .filter(obstacle -> obstacle.getStatus() == ObstacleStatus.OPEN || obstacle.getStatus() == ObstacleStatus.IN_PROGRESS)
                .collect(Collectors.groupingBy(obstacle -> obstacle.getObstacleType().name(), Collectors.counting()));
        Map<String, Long> partnersByStatus = partners.stream()
                .collect(Collectors.groupingBy(partner -> partner.getStatus().name(), Collectors.counting()));
        Map<String, Long> reviewCadence = reviews.stream()
                .filter(review -> review.getReviewType() == ReviewType.DAILY || review.getReviewType() == ReviewType.WEEKLY)
                .collect(Collectors.groupingBy(review -> review.getReviewDate().toString(), Collectors.counting()));
        // FR-16.4: weeks in the heatmap window whose weekly review carries an
        // answered diligence checklist (all-or-none, so one non-null answer
        // means the whole checklist was answered).
        WeekFields weekFields = WeekFields.ISO;
        long weeksWithDiligence = reviews.stream()
                .filter(review -> review.getReviewType() == ReviewType.WEEKLY)
                .filter(review -> review.getDiligenceClearVision() != null)
                .filter(review -> !review.getReviewDate().isBefore(today.minusDays(12L * 7 - 1)))
                .map(review -> review.getReviewDate().get(weekFields.weekBasedYear()) * 100
                        + review.getReviewDate().get(weekFields.weekOfWeekBasedYear()))
                .distinct()
                .count();
        List<DashboardSummaryResponse.AreaProgress> visionAreaProgress = areas.stream()
                .filter(area -> area.getStatus() != LifecycleStatus.ARCHIVED)
                .map(area -> {
                    List<Goal> areaGoals = goals.stream()
                            .filter(goal -> goal.getDream().getVisionArea().getId().equals(area.getId()))
                            .toList();
                    BigDecimal progress = areaGoals.isEmpty()
                            ? ZERO
                            : areaGoals.stream()
                            .map(Goal::getProgressPercent)
                            .reduce(BigDecimal.ZERO, BigDecimal::add)
                            .divide(BigDecimal.valueOf(areaGoals.size()), 0, RoundingMode.HALF_UP);
                    return new DashboardSummaryResponse.AreaProgress(area.getName(), progress);
                })
                .sorted(Comparator.comparing(DashboardSummaryResponse.AreaProgress::progress))
                .toList();
        List<TaskItemResponse> priorityTasks = tasks.stream()
                .filter(task -> task.getStatus() != WorkStatus.COMPLETED)
                .sorted(Comparator.comparingInt((TaskItem task) -> task.getPriority().ordinal()).reversed())
                .limit(5)
                .map(mapper::toResponse)
                .toList();

        return new DashboardSummaryResponse(
                areas.size(),
                dreams.stream().filter(dream -> dream.getStatus() == DreamStatus.ACTIVE).count(),
                goals.stream().filter(goal -> goal.getStatus() == WorkStatus.IN_PROGRESS || goal.getStatus() == WorkStatus.NOT_STARTED).count(),
                tasks.stream().filter(task -> task.getStatus() != WorkStatus.COMPLETED).count(),
                tasks.stream().filter(task -> task.getStatus() == WorkStatus.COMPLETED).count(),
                tasks.stream().filter(task -> isOverdue(task, today)).count(),
                tasks.stream().filter(task -> task.getStatus() == WorkStatus.BLOCKED).count(),
                averageProgress,
                tasks.stream().filter(task -> !task.getDueDate().isBefore(today) && !task.getDueDate().isAfter(weekEnd)).count(),
                goalsByStatus,
                dreamsByArea,
                tasksByStatus,
                tasksByPriority,
                activeObstaclesByType,
                partnersByStatus,
                reviewCadence,
                buildProgressTrend(progressLogs, today),
                visionAreaProgress,
                priorityTasks,
                weeksWithDiligence,
                moonshotGoals
        );
    }

    /**
     * Weekly samples of the running "average task progress" over the last
     * twelve weeks, reconstructed from the progress-log history: each task's
     * latest logged value as of a given day carries forward until its next
     * change, and each week reports the last day that had any data. Mirrors
     * the same Average Progress KPI, but as a trend instead of one number.
     */
    private List<DashboardSummaryResponse.TrendPoint> buildProgressTrend(List<ProgressLog> logs, LocalDate today) {
        int trendWeeks = 12;
        int totalDays = trendWeeks * 7;
        LocalDate start = today.minusDays(totalDays - 1L);
        Map<Long, List<ProgressLog>> byTask = logs.stream()
                .collect(Collectors.groupingBy(log -> log.getRelatedTask().getId()));
        byTask.values().forEach(entries -> entries.sort(Comparator.comparing(ProgressLog::getLoggedAt)));

        List<BigDecimal> dailyAverages = new ArrayList<>(totalDays);
        for (int i = 0; i < totalDays; i++) {
            LocalDate cursor = start.plusDays(i);
            List<BigDecimal> valuesAsOfDay = new ArrayList<>();
            for (List<ProgressLog> entries : byTask.values()) {
                ProgressLog latest = null;
                for (ProgressLog entry : entries) {
                    LocalDate logDate = entry.getLoggedAt().atZone(clock.getZone()).toLocalDate();
                    if (!logDate.isAfter(cursor)) {
                        latest = entry;
                    } else {
                        break;
                    }
                }
                if (latest != null) {
                    valuesAsOfDay.add(latest.getProgressPercentAfter());
                }
            }
            dailyAverages.add(valuesAsOfDay.isEmpty()
                    ? null
                    : valuesAsOfDay.stream().reduce(BigDecimal.ZERO, BigDecimal::add)
                    .divide(BigDecimal.valueOf(valuesAsOfDay.size()), 2, RoundingMode.HALF_UP));
        }

        List<DashboardSummaryResponse.TrendPoint> weeklyPoints = new ArrayList<>(trendWeeks);
        for (int week = 0; week < trendWeeks; week++) {
            BigDecimal lastKnown = null;
            for (int day = week * 7 + 6; day >= week * 7; day--) {
                if (dailyAverages.get(day) != null) {
                    lastKnown = dailyAverages.get(day);
                    break;
                }
            }
            weeklyPoints.add(new DashboardSummaryResponse.TrendPoint(start.plusDays(week * 7L + 6), lastKnown));
        }

        int firstDataIndex = 0;
        while (firstDataIndex < weeklyPoints.size() && weeklyPoints.get(firstDataIndex).progress() == null) {
            firstDataIndex++;
        }
        if (firstDataIndex == weeklyPoints.size()) {
            return List.of();
        }
        return weeklyPoints.subList(firstDataIndex, weeklyPoints.size()).stream()
                .map(point -> new DashboardSummaryResponse.TrendPoint(point.weekEnd(), point.progress() == null ? ZERO : point.progress()))
                .toList();
    }

    private void applyCommunicationRequest(CommunicationMessage entity, CommunicationMessageRequest request) {
        entity.setPartner(optionalPartner(request.partnerId()));
        entity.setRelatedDream(optionalDream(request.relatedDreamId()));
        entity.setRelatedGoal(optionalGoal(request.relatedGoalId()));
        entity.setRelatedTask(optionalTask(request.relatedTaskId()));
        entity.setAudience(request.audience());
        entity.setPurpose(request.purpose());
        entity.setSubject(request.subject());
        entity.setHook(request.hook());
        entity.setProblem(request.problem());
        entity.setRequest(request.request());
        entity.setBenefitToPartner(request.benefitToPartner());
        entity.setWordPicture(request.wordPicture());
        entity.setExpectedOutcome(request.expectedOutcome());
        entity.setMessageBody(request.messageBody());
        entity.setStatus(request.status());
        entity.setFollowUpDate(request.followUpDate());
    }

    private void prepareTask(TaskItem entity) {
        if (entity.getStatus() == WorkStatus.BLOCKED && isBlank(entity.getBlockerReason())) {
            throw new BusinessRuleException("Blocked tasks must include a blocker reason.");
        }
        if (entity.getStatus() == WorkStatus.COMPLETED) {
            entity.setProgressPercent(ONE_HUNDRED);
            if (entity.getCompletedAt() == null) {
                entity.setCompletedAt(Instant.now(clock));
            }
        } else {
            entity.setCompletedAt(null);
        }
    }

    private void validateComplexStep(VisionStep step) {
        if (step.isComplex() && step.getStatus() == WorkStatus.COMPLETED
                && taskItemRepository.findByStep_IdAndUser_IdAndArchivedFalse(step.getId(), step.getUser().getId()).isEmpty()) {
            throw new BusinessRuleException("A complex step must have at least one task before it can be completed.");
        }
    }

    private void validateGoalCompletion(Goal goal, boolean manualOverride) {
        if (goal.getStatus() != WorkStatus.COMPLETED || manualOverride) {
            return;
        }
        boolean allStepsComplete = visionStepRepository.findByGoal_IdAndUser_IdAndArchivedFalse(goal.getId(), goal.getUser().getId()).stream()
                .allMatch(step -> step.getStatus() == WorkStatus.COMPLETED);
        if (!allStepsComplete) {
            throw new BusinessRuleException("A goal cannot be completed until all steps are completed, unless manualOverride is true.");
        }
    }

    private void recalculateStep(VisionStep step) {
        if (step.isManualProgressOverride()) {
            recalculateGoal(step.getGoal());
            return;
        }
        List<TaskItem> tasks = taskItemRepository.findByStep_IdAndUser_IdAndArchivedFalse(step.getId(), step.getUser().getId());
        step.setProgressPercent(average(tasks, TaskItem::getProgressPercent));
        if (!tasks.isEmpty() && tasks.stream().allMatch(task -> task.getStatus() == WorkStatus.COMPLETED)) {
            step.setStatus(WorkStatus.COMPLETED);
        }
        recalculateGoal(step.getGoal());
    }

    private void recalculateGoal(Goal goal) {
        if (goal.isManualProgressOverride()) {
            return;
        }
        List<VisionStep> steps = visionStepRepository.findByGoal_IdAndUser_IdAndArchivedFalse(goal.getId(), goal.getUser().getId());
        goal.setProgressPercent(average(steps, VisionStep::getProgressPercent));
        if (!steps.isEmpty() && steps.stream().allMatch(step -> step.getStatus() == WorkStatus.COMPLETED)) {
            goal.setStatus(WorkStatus.COMPLETED);
        }
    }

    private void cascadeArchiveDreams(Long visionAreaId) {
        Long userId = userId();
        for (Dream dream : dreamRepository.findByVisionArea_IdAndUser_Id(visionAreaId, userId)) {
            dream.setStatus(DreamStatus.ARCHIVED);
            dream.setArchived(true);
            cascadeArchiveGoals(dream.getId());
        }
    }

    private void cascadeArchiveGoals(Long dreamId) {
        Long userId = userId();
        for (Goal goal : goalRepository.findByDream_IdAndUser_Id(dreamId, userId)) {
            goal.setArchived(true);
            cascadeArchiveSteps(goal.getId());
        }
    }

    private void cascadeArchiveSteps(Long goalId) {
        Long userId = userId();
        for (VisionStep step : visionStepRepository.findByGoal_IdAndUser_Id(goalId, userId)) {
            step.setArchived(true);
            cascadeArchiveTasks(step.getId());
        }
    }

    private void cascadeArchiveTasks(Long stepId) {
        Long userId = userId();
        for (TaskItem task : taskItemRepository.findByStep_IdAndUser_Id(stepId, userId)) {
            task.setArchived(true);
        }
    }

    // --- Archive impact (what a cascade would newly archive) -----------------

    @Transactional(readOnly = true)
    public ArchiveImpactResponse visionAreaArchiveImpact(Long id) {
        long dreams = 0;
        long goals = 0;
        long steps = 0;
        long tasks = 0;
        for (Dream dream : dreamRepository.findByVisionArea_IdAndUser_Id(visionArea(id).getId(), userId())) {
            if (!dream.isArchived()) {
                dreams++;
            }
            ArchiveImpactResponse below = dreamArchiveImpactOf(dream.getId());
            goals += below.goals();
            steps += below.steps();
            tasks += below.tasks();
        }
        return new ArchiveImpactResponse(dreams, goals, steps, tasks);
    }

    @Transactional(readOnly = true)
    public ArchiveImpactResponse dreamArchiveImpact(Long id) {
        return dreamArchiveImpactOf(dream(id).getId());
    }

    @Transactional(readOnly = true)
    public ArchiveImpactResponse goalArchiveImpact(Long id) {
        return goalArchiveImpactOf(goal(id).getId());
    }

    @Transactional(readOnly = true)
    public ArchiveImpactResponse stepArchiveImpact(Long id) {
        return stepArchiveImpactOf(step(id).getId());
    }

    private ArchiveImpactResponse dreamArchiveImpactOf(Long dreamId) {
        long goals = 0;
        long steps = 0;
        long tasks = 0;
        for (Goal goal : goalRepository.findByDream_IdAndUser_Id(dreamId, userId())) {
            if (!goal.isArchived()) {
                goals++;
            }
            ArchiveImpactResponse below = goalArchiveImpactOf(goal.getId());
            steps += below.steps();
            tasks += below.tasks();
        }
        return new ArchiveImpactResponse(0, goals, steps, tasks);
    }

    private ArchiveImpactResponse goalArchiveImpactOf(Long goalId) {
        long steps = 0;
        long tasks = 0;
        for (VisionStep step : visionStepRepository.findByGoal_IdAndUser_Id(goalId, userId())) {
            if (!step.isArchived()) {
                steps++;
            }
            tasks += stepArchiveImpactOf(step.getId()).tasks();
        }
        return new ArchiveImpactResponse(0, 0, steps, tasks);
    }

    private ArchiveImpactResponse stepArchiveImpactOf(Long stepId) {
        long tasks = taskItemRepository.findByStep_IdAndUser_Id(stepId, userId()).stream()
                .filter(task -> !task.isArchived())
                .count();
        return new ArchiveImpactResponse(0, 0, 0, tasks);
    }

    // --- Restore (un-archive, pulling archived parents back with it) ---------

    public void restoreVisionArea(Long id) {
        unarchiveVisionArea(visionArea(id));
    }

    public void restoreDream(Long id) {
        unarchiveDreamChain(dream(id));
    }

    public void restoreGoal(Long id) {
        unarchiveGoalChain(goal(id));
    }

    public void restoreStep(Long id) {
        VisionStep entity = step(id);
        unarchiveGoalChain(entity.getGoal());
        entity.setArchived(false);
        recalculateGoal(entity.getGoal());
    }

    public void restoreTask(Long id) {
        TaskItem entity = task(id);
        unarchiveGoalChain(entity.getStep().getGoal());
        entity.getStep().setArchived(false);
        entity.setArchived(false);
        recalculateStep(entity.getStep());
    }

    public void restorePartner(Long id) {
        partner(id).setArchived(false);
    }

    public void restoreCommunicationMessage(Long id) {
        communicationMessage(id).setArchived(false);
    }

    public void restoreReview(Long id) {
        review(id).setArchived(false);
    }

    public void restoreObstacle(Long id) {
        obstacle(id).setArchived(false);
    }

    // --- Permanent delete (irreversible; only for already-archived records) --

    public void permanentlyDeleteVisionArea(Long id) {
        VisionArea area = visionArea(id);
        requireArchived(area.isArchived(), "Vision area");
        Subtree subtree = new Subtree();
        subtree.visionAreas.add(area);
        for (Dream dream : dreamRepository.findByVisionArea_IdAndUser_Id(area.getId(), userId())) {
            collectDreamSubtree(dream, subtree);
        }
        unlinkAndDelete(subtree);
    }

    public void permanentlyDeleteDream(Long id) {
        Dream dream = dream(id);
        requireArchived(dream.isArchived(), "Dream");
        Subtree subtree = new Subtree();
        collectDreamSubtree(dream, subtree);
        unlinkAndDelete(subtree);
    }

    public void permanentlyDeleteGoal(Long id) {
        Goal goal = goal(id);
        requireArchived(goal.isArchived(), "Goal");
        Subtree subtree = new Subtree();
        collectGoalSubtree(goal, subtree);
        unlinkAndDelete(subtree);
    }

    public void permanentlyDeleteStep(Long id) {
        VisionStep step = step(id);
        requireArchived(step.isArchived(), "Step");
        Subtree subtree = new Subtree();
        collectStepSubtree(step, subtree);
        unlinkAndDelete(subtree);
    }

    public void permanentlyDeleteTask(Long id) {
        TaskItem task = task(id);
        requireArchived(task.isArchived(), "Task");
        Subtree subtree = new Subtree();
        subtree.tasks.add(task);
        unlinkAndDelete(subtree);
    }

    public void permanentlyDeletePartner(Long id) {
        Partner partner = partner(id);
        requireArchived(partner.isArchived(), "Partner");
        Set<Long> partnerId = Set.of(partner.getId());
        for (Obstacle obstacle : obstacleRepository.findByUser_Id(userId())) {
            if (references(obstacle.getRequiredPartner(), Partner::getId, partnerId)) {
                obstacle.setRequiredPartner(null);
            }
        }
        for (CommunicationMessage message : communicationMessageRepository.findByUser_Id(userId())) {
            if (references(message.getPartner(), Partner::getId, partnerId)) {
                message.setPartner(null);
            }
        }
        partnerRepository.delete(partner);
    }

    public void permanentlyDeleteCommunicationMessage(Long id) {
        CommunicationMessage message = communicationMessage(id);
        requireArchived(message.isArchived(), "Communication message");
        communicationMessageRepository.delete(message);
    }

    public void permanentlyDeleteReview(Long id) {
        Review review = review(id);
        requireArchived(review.isArchived(), "Review");
        reviewRepository.delete(review);
    }

    public void permanentlyDeleteObstacle(Long id) {
        Obstacle obstacle = obstacle(id);
        requireArchived(obstacle.isArchived(), "Obstacle");
        obstacleRepository.delete(obstacle);
    }

    private void collectDreamSubtree(Dream dream, Subtree subtree) {
        subtree.dreams.add(dream);
        for (Goal goal : goalRepository.findByDream_IdAndUser_Id(dream.getId(), userId())) {
            collectGoalSubtree(goal, subtree);
        }
    }

    private void collectGoalSubtree(Goal goal, Subtree subtree) {
        subtree.goals.add(goal);
        for (VisionStep step : visionStepRepository.findByGoal_IdAndUser_Id(goal.getId(), userId())) {
            collectStepSubtree(step, subtree);
        }
    }

    private void collectStepSubtree(VisionStep step, Subtree subtree) {
        subtree.steps.add(step);
        subtree.tasks.addAll(taskItemRepository.findByStep_IdAndUser_Id(step.getId(), userId()));
    }

    /**
     * Clears every surviving record's link into the doomed subtree, removes the
     * progress logs that cannot outlive their task, then deletes the subtree
     * bottom-up so no foreign key is ever left dangling.
     */
    private void unlinkAndDelete(Subtree subtree) {
        DeletedIds deleted = new DeletedIds(subtree);
        Long userId = userId();
        for (TaskItem task : subtree.tasks) {
            progressLogRepository.deleteAll(progressLogRepository.findByRelatedTask_IdAndUser_Id(task.getId(), userId));
        }
        partnerRepository.findByUser_Id(userId).forEach(partner -> unlinkPartner(partner, deleted));
        obstacleRepository.findByUser_Id(userId).forEach(obstacle -> unlinkObstacle(obstacle, deleted));
        communicationMessageRepository.findByUser_Id(userId).forEach(message -> unlinkMessage(message, deleted));
        reviewRepository.findByUser_Id(userId).forEach(review -> unlinkReview(review, deleted));

        taskItemRepository.deleteAll(subtree.tasks);
        visionStepRepository.deleteAll(subtree.steps);
        goalRepository.deleteAll(subtree.goals);
        dreamRepository.deleteAll(subtree.dreams);
        visionAreaRepository.deleteAll(subtree.visionAreas);
    }

    private void unlinkPartner(Partner partner, DeletedIds deleted) {
        if (references(partner.getRelatedVisionArea(), VisionArea::getId, deleted.visionAreas)) {
            partner.setRelatedVisionArea(null);
        }
        if (references(partner.getRelatedDream(), Dream::getId, deleted.dreams)) {
            partner.setRelatedDream(null);
        }
        if (references(partner.getRelatedGoal(), Goal::getId, deleted.goals)) {
            partner.setRelatedGoal(null);
        }
        if (references(partner.getRelatedStep(), VisionStep::getId, deleted.steps)) {
            partner.setRelatedStep(null);
        }
        if (references(partner.getRelatedTask(), TaskItem::getId, deleted.tasks)) {
            partner.setRelatedTask(null);
        }
    }

    private void unlinkObstacle(Obstacle obstacle, DeletedIds deleted) {
        if (references(obstacle.getRelatedDream(), Dream::getId, deleted.dreams)) {
            obstacle.setRelatedDream(null);
        }
        if (references(obstacle.getRelatedGoal(), Goal::getId, deleted.goals)) {
            obstacle.setRelatedGoal(null);
        }
        if (references(obstacle.getRelatedStep(), VisionStep::getId, deleted.steps)) {
            obstacle.setRelatedStep(null);
        }
        if (references(obstacle.getRelatedTask(), TaskItem::getId, deleted.tasks)) {
            obstacle.setRelatedTask(null);
        }
    }

    private void unlinkMessage(CommunicationMessage message, DeletedIds deleted) {
        if (references(message.getRelatedDream(), Dream::getId, deleted.dreams)) {
            message.setRelatedDream(null);
        }
        if (references(message.getRelatedGoal(), Goal::getId, deleted.goals)) {
            message.setRelatedGoal(null);
        }
        if (references(message.getRelatedTask(), TaskItem::getId, deleted.tasks)) {
            message.setRelatedTask(null);
        }
    }

    private void unlinkReview(Review review, DeletedIds deleted) {
        if (references(review.getRelatedVisionArea(), VisionArea::getId, deleted.visionAreas)) {
            review.setRelatedVisionArea(null);
        }
        if (references(review.getRelatedDream(), Dream::getId, deleted.dreams)) {
            review.setRelatedDream(null);
        }
    }

    private void requireArchived(boolean archived, String label) {
        if (!archived) {
            throw new BusinessRuleException(label + " must be archived before it can be permanently deleted.");
        }
    }

    private <T> boolean references(T related, Function<T, Long> idOf, Set<Long> deletedIds) {
        return related != null && deletedIds.contains(idOf.apply(related));
    }

    /** The database ids in a subtree, grouped by level, for fast link-membership checks. */
    private static final class DeletedIds {
        private final Set<Long> visionAreas;
        private final Set<Long> dreams;
        private final Set<Long> goals;
        private final Set<Long> steps;
        private final Set<Long> tasks;

        private DeletedIds(Subtree subtree) {
            visionAreas = ids(subtree.visionAreas, VisionArea::getId);
            dreams = ids(subtree.dreams, Dream::getId);
            goals = ids(subtree.goals, Goal::getId);
            steps = ids(subtree.steps, VisionStep::getId);
            tasks = ids(subtree.tasks, TaskItem::getId);
        }

        private static <T> Set<Long> ids(List<T> entities, Function<T, Long> idOf) {
            return entities.stream().map(idOf).collect(Collectors.toSet());
        }
    }

    /** Every hierarchy record a single permanent-delete will remove, gathered before the delete runs. */
    private static final class Subtree {
        private final List<VisionArea> visionAreas = new ArrayList<>();
        private final List<Dream> dreams = new ArrayList<>();
        private final List<Goal> goals = new ArrayList<>();
        private final List<VisionStep> steps = new ArrayList<>();
        private final List<TaskItem> tasks = new ArrayList<>();
    }

    private void unarchiveVisionArea(VisionArea area) {
        if (area.isArchived()) {
            area.setArchived(false);
            // ARCHIVED was set by the archive cascade; the original status is
            // unknown, so come back as Paused rather than silently Active.
            if (area.getStatus() == LifecycleStatus.ARCHIVED) {
                area.setStatus(LifecycleStatus.PAUSED);
            }
        }
    }

    private void unarchiveDreamChain(Dream dream) {
        unarchiveVisionArea(dream.getVisionArea());
        if (dream.isArchived()) {
            dream.setArchived(false);
            if (dream.getStatus() == DreamStatus.ARCHIVED) {
                dream.setStatus(DreamStatus.PAUSED);
            }
        }
    }

    private void unarchiveGoalChain(Goal goal) {
        unarchiveDreamChain(goal.getDream());
        goal.setArchived(false);
    }

    private <T> BigDecimal average(List<T> values, Function<T, BigDecimal> progressGetter) {
        if (values.isEmpty()) {
            return ZERO;
        }
        return values.stream()
                .map(progressGetter)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .divide(BigDecimal.valueOf(values.size()), 2, RoundingMode.HALF_UP);
    }

    private boolean isOverdue(TaskItem task, LocalDate today) {
        return task.getDueDate().isBefore(today) && task.getStatus() != WorkStatus.COMPLETED;
    }

    private BigDecimal normalizeProgress(BigDecimal value) {
        if (value == null) {
            return ZERO;
        }
        if (value.compareTo(BigDecimal.ZERO) < 0 || value.compareTo(ONE_HUNDRED) > 0) {
            throw new BusinessRuleException("Progress percent must be between 0 and 100.");
        }
        return value.setScale(2, RoundingMode.HALF_UP);
    }

    private VisionArea visionArea(Long id) {
        return visionAreaRepository.findById(id)
                .filter(entity -> entity.getUser().getId().equals(userId()))
                .orElseThrow(() -> new ResourceNotFoundException("Vision area not found: " + id));
    }

    private Dream dream(Long id) {
        return dreamRepository.findById(id)
                .filter(entity -> entity.getUser().getId().equals(userId()))
                .orElseThrow(() -> new ResourceNotFoundException("Dream not found: " + id));
    }

    private Goal goal(Long id) {
        return goalRepository.findById(id)
                .filter(entity -> entity.getUser().getId().equals(userId()))
                .orElseThrow(() -> new ResourceNotFoundException("Goal not found: " + id));
    }

    private VisionStep step(Long id) {
        return visionStepRepository.findById(id)
                .filter(entity -> entity.getUser().getId().equals(userId()))
                .orElseThrow(() -> new ResourceNotFoundException("Step not found: " + id));
    }

    private TaskItem task(Long id) {
        return taskItemRepository.findById(id)
                .filter(entity -> entity.getUser().getId().equals(userId()))
                .orElseThrow(() -> new ResourceNotFoundException("Task not found: " + id));
    }

    private Partner partner(Long id) {
        return partnerRepository.findById(id)
                .filter(entity -> entity.getUser().getId().equals(userId()))
                .orElseThrow(() -> new ResourceNotFoundException("Partner not found: " + id));
    }

    private CommunicationMessage communicationMessage(Long id) {
        return communicationMessageRepository.findById(id)
                .filter(entity -> entity.getUser().getId().equals(userId()))
                .orElseThrow(() -> new ResourceNotFoundException("Communication message not found: " + id));
    }

    private Review review(Long id) {
        return reviewRepository.findById(id)
                .filter(entity -> entity.getUser().getId().equals(userId()))
                .orElseThrow(() -> new ResourceNotFoundException("Review not found: " + id));
    }

    private Obstacle obstacle(Long id) {
        return obstacleRepository.findById(id)
                .filter(entity -> entity.getUser().getId().equals(userId()))
                .orElseThrow(() -> new ResourceNotFoundException("Obstacle not found: " + id));
    }

    private ProgressLog progressLog(Long id) {
        return progressLogRepository.findById(id)
                .filter(entity -> entity.getUser().getId().equals(userId()))
                .orElseThrow(() -> new ResourceNotFoundException("Progress log not found: " + id));
    }

    private VisionArea optionalVisionArea(Long id) {
        return id == null ? null : visionArea(id);
    }

    private Dream optionalDream(Long id) {
        return id == null ? null : dream(id);
    }

    private Goal optionalGoal(Long id) {
        return id == null ? null : goal(id);
    }

    private VisionStep optionalStep(Long id) {
        return id == null ? null : step(id);
    }

    private TaskItem optionalTask(Long id) {
        return id == null ? null : task(id);
    }

    private Partner optionalPartner(Long id) {
        return id == null ? null : partner(id);
    }

    private Long userId() {
        return userScope.currentUser().getId();
    }

    private String nextCode(String prefix, int currentCount) {
        return "%s-%03d".formatted(prefix, currentCount + 1);
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private <E extends Enum<E>> E parse(Class<E> enumType, String status) {
        try {
            return Enum.valueOf(enumType, status.trim().toUpperCase().replace('-', '_').replace(' ', '_'));
        } catch (IllegalArgumentException exception) {
            throw new BusinessRuleException("Invalid status: " + status);
        }
    }
}
