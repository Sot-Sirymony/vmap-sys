package com.visionmapping.service;

import com.visionmapping.dto.request.DreamRequest;
import com.visionmapping.dto.request.GoalRequest;
import com.visionmapping.dto.request.PartnerRequest;
import com.visionmapping.dto.request.TaskItemRequest;
import com.visionmapping.dto.request.VisionAreaRequest;
import com.visionmapping.dto.request.VisionStepRequest;
import com.visionmapping.dto.response.ArchiveImpactResponse;
import com.visionmapping.dto.response.DashboardSummaryResponse;
import com.visionmapping.dto.response.DreamResponse;
import com.visionmapping.dto.response.GoalResponse;
import com.visionmapping.dto.response.PartnerResponse;
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
import com.visionmapping.entity.enums.DreamStatus;
import com.visionmapping.entity.enums.LifecycleStatus;
import com.visionmapping.entity.enums.ObstacleStatus;
import com.visionmapping.entity.enums.PartnerStatus;
import com.visionmapping.entity.enums.ReviewType;
import com.visionmapping.entity.enums.WorkStatus;
import com.visionmapping.exception.BusinessRuleException;
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
import com.visionmapping.service.support.ArchiveCascade;
import com.visionmapping.service.support.EntityLookup;
import com.visionmapping.service.support.PermanentDeleteCascade;
import com.visionmapping.service.support.ProgressCalculator;
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

    private final EntityLookup lookup;
    private final ProgressCalculator progress;
    private final PermanentDeleteCascade permanentDeleteCascade;
    private final ArchiveCascade archiveCascade;
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
                ? visionAreaRepository.findByUser_Id(lookup.userId())
                : visionAreaRepository.findByUser_IdAndArchivedFalse(lookup.userId());
        return entities.stream().map(mapper::toResponse).toList();
    }

    public VisionAreaResponse createVisionArea(VisionAreaRequest request) {
        AppUser user = lookup.currentUser();
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
        return mapper.toResponse(lookup.visionArea(id));
    }

    public VisionAreaResponse updateVisionArea(Long id, VisionAreaRequest request) {
        VisionArea entity = lookup.visionArea(id);
        entity.setName(request.name());
        entity.setDescription(request.description());
        entity.setPriority(request.priority());
        entity.setStatus(request.status());
        return mapper.toResponse(entity);
    }

    public VisionAreaResponse updateVisionAreaStatus(Long id, String status) {
        VisionArea entity = lookup.visionArea(id);
        entity.setStatus(parse(LifecycleStatus.class, status));
        return mapper.toResponse(entity);
    }

    public void archiveVisionArea(Long id) {
        VisionArea entity = lookup.visionArea(id);
        entity.setStatus(LifecycleStatus.ARCHIVED);
        entity.setArchived(true);
        archiveCascade.archiveDreamsUnder(entity.getId());
    }

    @Transactional(readOnly = true)
    public List<DreamResponse> listDreams(boolean includeArchived) {
        List<Dream> entities = includeArchived
                ? dreamRepository.findByUser_Id(lookup.userId())
                : dreamRepository.findByUser_IdAndArchivedFalse(lookup.userId());
        return entities.stream().map(mapper::toResponse).toList();
    }

    public DreamResponse createDream(DreamRequest request) {
        AppUser user = lookup.currentUser();
        VisionArea visionArea = lookup.visionArea(request.visionAreaId());
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
        return mapper.toResponse(lookup.dream(id));
    }

    public DreamResponse updateDream(Long id, DreamRequest request) {
        Dream entity = lookup.dream(id);
        entity.setVisionArea(lookup.visionArea(request.visionAreaId()));
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
        Dream entity = lookup.dream(id);
        entity.setStatus(parse(DreamStatus.class, status));
        return mapper.toResponse(entity);
    }

    public void archiveDream(Long id) {
        Dream entity = lookup.dream(id);
        entity.setStatus(DreamStatus.ARCHIVED);
        entity.setArchived(true);
        archiveCascade.archiveGoalsUnder(entity.getId());
    }

    @Transactional(readOnly = true)
    public List<GoalResponse> listGoals(boolean includeArchived) {
        List<Goal> entities = includeArchived
                ? goalRepository.findByUser_Id(lookup.userId())
                : goalRepository.findByUser_IdAndArchivedFalse(lookup.userId());
        return entities.stream().map(mapper::toResponse).toList();
    }

    public GoalResponse createGoal(GoalRequest request) {
        AppUser user = lookup.currentUser();
        Dream dream = lookup.dream(request.dreamId());
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
        return mapper.toResponse(lookup.goal(id));
    }

    public GoalResponse updateGoal(Long id, GoalRequest request) {
        Goal entity = lookup.goal(id);
        entity.setDream(lookup.dream(request.dreamId()));
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
        Goal entity = lookup.goal(id);
        entity.setStatus(parse(WorkStatus.class, status));
        validateGoalCompletion(entity, manualOverride);
        if (manualOverride) {
            entity.setManualProgressOverride(true);
        }
        return mapper.toResponse(entity);
    }

    public void archiveGoal(Long id) {
        Goal entity = lookup.goal(id);
        entity.setArchived(true);
        archiveCascade.archiveStepsUnder(entity.getId());
    }

    @Transactional(readOnly = true)
    public List<VisionStepResponse> listSteps(boolean includeArchived) {
        List<VisionStep> entities = includeArchived
                ? visionStepRepository.findByUser_Id(lookup.userId())
                : visionStepRepository.findByUser_IdAndArchivedFalse(lookup.userId());
        return entities.stream().map(mapper::toResponse).toList();
    }

    public VisionStepResponse createStep(VisionStepRequest request) {
        AppUser user = lookup.currentUser();
        Goal goal = lookup.goal(request.goalId());
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
        progress.recalculateGoal(goal);
        return mapper.toResponse(saved);
    }

    @Transactional(readOnly = true)
    public VisionStepResponse getStep(Long id) {
        return mapper.toResponse(lookup.step(id));
    }

    public VisionStepResponse updateStep(Long id, VisionStepRequest request) {
        VisionStep entity = lookup.step(id);
        Goal oldGoal = entity.getGoal();
        entity.setGoal(lookup.goal(request.goalId()));
        entity.setTitle(request.title());
        entity.setDescription(request.description());
        entity.setSequenceNumber(request.sequenceNumber());
        entity.setComplex(request.complex());
        entity.setPriority(request.priority());
        entity.setTargetDate(request.targetDate());
        entity.setStatus(request.status());
        validateComplexStep(entity);
        progress.recalculateGoal(oldGoal);
        progress.recalculateGoal(entity.getGoal());
        return mapper.toResponse(entity);
    }

    public VisionStepResponse updateStepStatus(Long id, String status, boolean manualOverride) {
        VisionStep entity = lookup.step(id);
        entity.setStatus(parse(WorkStatus.class, status));
        validateComplexStep(entity);
        if (manualOverride) {
            entity.setManualProgressOverride(true);
        }
        progress.recalculateGoal(entity.getGoal());
        return mapper.toResponse(entity);
    }

    public void archiveStep(Long id) {
        VisionStep entity = lookup.step(id);
        entity.setArchived(true);
        archiveCascade.archiveTasksUnder(entity.getId());
        progress.recalculateGoal(entity.getGoal());
    }

    @Transactional(readOnly = true)
    public List<TaskItemResponse> listTasks(boolean includeArchived) {
        List<TaskItem> entities = includeArchived
                ? taskItemRepository.findByUser_Id(lookup.userId())
                : taskItemRepository.findByUser_IdAndArchivedFalse(lookup.userId());
        return entities.stream().map(mapper::toResponse).toList();
    }

    public TaskItemResponse createTask(TaskItemRequest request) {
        AppUser user = lookup.currentUser();
        VisionStep step = lookup.step(request.stepId());
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
                .progressPercent(progress.normalizeProgress(request.progressPercent()))
                .estimatedHours(request.estimatedHours())
                .actualHours(request.actualHours())
                .blockerReason(request.blockerReason())
                .nextAction(request.nextAction())
                .build();
        prepareTask(entity);
        TaskItem saved = taskItemRepository.save(entity);
        progress.recalculateStep(step);
        return mapper.toResponse(saved);
    }

    @Transactional(readOnly = true)
    public TaskItemResponse getTask(Long id) {
        return mapper.toResponse(lookup.task(id));
    }

    public TaskItemResponse updateTask(Long id, TaskItemRequest request) {
        TaskItem entity = lookup.task(id);
        BigDecimal progressBefore = entity.getProgressPercent();
        VisionStep oldStep = entity.getStep();
        entity.setStep(lookup.step(request.stepId()));
        entity.setTitle(request.title());
        entity.setDescription(request.description());
        entity.setOwner(request.owner());
        entity.setPriority(request.priority());
        entity.setStartDate(request.startDate());
        entity.setDueDate(request.dueDate());
        entity.setStatus(request.status());
        entity.setProgressPercent(progress.normalizeProgress(request.progressPercent()));
        entity.setEstimatedHours(request.estimatedHours());
        entity.setActualHours(request.actualHours());
        entity.setBlockerReason(request.blockerReason());
        entity.setNextAction(request.nextAction());
        prepareTask(entity);
        progress.recalculateStep(oldStep);
        progress.recalculateStep(entity.getStep());
        logProgressChange(entity, progressBefore);
        return mapper.toResponse(entity);
    }

    public TaskItemResponse updateTaskStatus(Long id, String status) {
        TaskItem entity = lookup.task(id);
        BigDecimal progressBefore = entity.getProgressPercent();
        entity.setStatus(parse(WorkStatus.class, status));
        prepareTask(entity);
        progress.recalculateStep(entity.getStep());
        logProgressChange(entity, progressBefore);
        return mapper.toResponse(entity);
    }

    public void archiveTask(Long id) {
        TaskItem entity = lookup.task(id);
        entity.setArchived(true);
        progress.recalculateStep(entity.getStep());
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
                ? partnerRepository.findByUser_Id(lookup.userId(), pageable)
                : partnerRepository.findByUser_IdAndArchivedFalse(lookup.userId(), pageable);
        return entities.map(mapper::toResponse);
    }

    public PartnerResponse createPartner(PartnerRequest request) {
        AppUser user = lookup.currentUser();
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
                .relatedVisionArea(lookup.optionalVisionArea(request.relatedVisionAreaId()))
                .relatedDream(lookup.optionalDream(request.relatedDreamId()))
                .relatedGoal(lookup.optionalGoal(request.relatedGoalId()))
                .relatedStep(lookup.optionalStep(request.relatedStepId()))
                .relatedTask(lookup.optionalTask(request.relatedTaskId()))
                .status(request.status())
                .notes(request.notes())
                .build();
        return mapper.toResponse(partnerRepository.save(entity));
    }

    @Transactional(readOnly = true)
    public PartnerResponse getPartner(Long id) {
        return mapper.toResponse(lookup.partner(id));
    }

    public PartnerResponse updatePartner(Long id, PartnerRequest request) {
        Partner entity = lookup.partner(id);
        entity.setName(request.name());
        entity.setRole(request.role());
        entity.setOrganization(request.organization());
        entity.setEmail(request.email());
        entity.setPhone(request.phone());
        entity.setStrength(request.strength());
        entity.setSupportType(request.supportType());
        entity.setRelatedVisionArea(lookup.optionalVisionArea(request.relatedVisionAreaId()));
        entity.setRelatedDream(lookup.optionalDream(request.relatedDreamId()));
        entity.setRelatedGoal(lookup.optionalGoal(request.relatedGoalId()));
        entity.setRelatedStep(lookup.optionalStep(request.relatedStepId()));
        entity.setRelatedTask(lookup.optionalTask(request.relatedTaskId()));
        entity.setStatus(request.status());
        entity.setNotes(request.notes());
        return mapper.toResponse(entity);
    }

    public PartnerResponse updatePartnerStatus(Long id, String status) {
        Partner entity = lookup.partner(id);
        entity.setStatus(parse(PartnerStatus.class, status));
        return mapper.toResponse(entity);
    }

    public void archivePartner(Long id) {
        lookup.partner(id).setArchived(true);
    }

    @Transactional(readOnly = true)
    public DashboardSummaryResponse buildDashboardSummary() {
        Long userId = lookup.userId();
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
                    BigDecimal areaProgress = areaGoals.isEmpty()
                            ? ZERO
                            : areaGoals.stream()
                            .map(Goal::getProgressPercent)
                            .reduce(BigDecimal.ZERO, BigDecimal::add)
                            .divide(BigDecimal.valueOf(areaGoals.size()), 0, RoundingMode.HALF_UP);
                    return new DashboardSummaryResponse.AreaProgress(area.getName(), areaProgress);
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
                tasks.stream().filter(task -> progress.isOverdue(task, today)).count(),
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

    // --- Archive impact (what a cascade would newly archive) -----------------

    @Transactional(readOnly = true)
    public ArchiveImpactResponse visionAreaArchiveImpact(Long id) {
        return archiveCascade.impactOfVisionArea(lookup.visionArea(id));
    }

    @Transactional(readOnly = true)
    public ArchiveImpactResponse dreamArchiveImpact(Long id) {
        return archiveCascade.impactOfDream(lookup.dream(id));
    }

    @Transactional(readOnly = true)
    public ArchiveImpactResponse goalArchiveImpact(Long id) {
        return archiveCascade.impactOfGoal(lookup.goal(id));
    }

    @Transactional(readOnly = true)
    public ArchiveImpactResponse stepArchiveImpact(Long id) {
        return archiveCascade.impactOfStep(lookup.step(id));
    }

    // --- Restore (un-archive, pulling archived parents back with it) ---------

    public void restoreVisionArea(Long id) {
        archiveCascade.unarchiveVisionArea(lookup.visionArea(id));
    }

    public void restoreDream(Long id) {
        archiveCascade.unarchiveDreamChain(lookup.dream(id));
    }

    public void restoreGoal(Long id) {
        archiveCascade.unarchiveGoalChain(lookup.goal(id));
    }

    public void restoreStep(Long id) {
        VisionStep entity = lookup.step(id);
        archiveCascade.unarchiveGoalChain(entity.getGoal());
        entity.setArchived(false);
        progress.recalculateGoal(entity.getGoal());
    }

    public void restoreTask(Long id) {
        TaskItem entity = lookup.task(id);
        archiveCascade.unarchiveGoalChain(entity.getStep().getGoal());
        entity.getStep().setArchived(false);
        entity.setArchived(false);
        progress.recalculateStep(entity.getStep());
    }

    public void restorePartner(Long id) {
        lookup.partner(id).setArchived(false);
    }

    // --- Permanent delete (irreversible; only for already-archived records) --

    public void permanentlyDeleteVisionArea(Long id) {
        VisionArea area = lookup.visionArea(id);
        requireArchived(area.isArchived(), "Vision area");
        permanentDeleteCascade.deleteVisionArea(area);
    }

    public void permanentlyDeleteDream(Long id) {
        Dream dream = lookup.dream(id);
        requireArchived(dream.isArchived(), "Dream");
        permanentDeleteCascade.deleteDream(dream);
    }

    public void permanentlyDeleteGoal(Long id) {
        Goal goal = lookup.goal(id);
        requireArchived(goal.isArchived(), "Goal");
        permanentDeleteCascade.deleteGoal(goal);
    }

    public void permanentlyDeleteStep(Long id) {
        VisionStep step = lookup.step(id);
        requireArchived(step.isArchived(), "Step");
        permanentDeleteCascade.deleteStep(step);
    }

    public void permanentlyDeleteTask(Long id) {
        TaskItem task = lookup.task(id);
        requireArchived(task.isArchived(), "Task");
        permanentDeleteCascade.deleteTask(task);
    }

    public void permanentlyDeletePartner(Long id) {
        Partner partner = lookup.partner(id);
        requireArchived(partner.isArchived(), "Partner");
        Long partnerId = partner.getId();
        for (Obstacle obstacle : obstacleRepository.findByUser_Id(lookup.userId())) {
            if (obstacle.getRequiredPartner() != null && obstacle.getRequiredPartner().getId().equals(partnerId)) {
                obstacle.setRequiredPartner(null);
            }
        }
        for (CommunicationMessage message : communicationMessageRepository.findByUser_Id(lookup.userId())) {
            if (message.getPartner() != null && message.getPartner().getId().equals(partnerId)) {
                message.setPartner(null);
            }
        }
        partnerRepository.delete(partner);
    }

    /**
     * Clears every surviving record's link into the doomed subtree, removes the
     * progress logs that cannot outlive their task, then deletes the subtree
     * bottom-up so no foreign key is ever left dangling.
     */
    private void requireArchived(boolean archived, String label) {
        if (!archived) {
            throw new BusinessRuleException(label + " must be archived before it can be permanently deleted.");
        }
    }

    /** The database ids in a subtree, grouped by level, for fast link-membership checks. */
    /** Every hierarchy record a single permanent-delete will remove, gathered before the delete runs. */

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
