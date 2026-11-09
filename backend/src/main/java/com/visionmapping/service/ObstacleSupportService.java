package com.visionmapping.service;

import com.visionmapping.dto.response.ObstacleHelpResponses.BlockedTask;
import com.visionmapping.dto.response.ObstacleHelpResponses.PartnerMatch;
import com.visionmapping.dto.response.ObstacleHelpResponses.PartnerMatches;
import com.visionmapping.dto.response.ObstacleHelpResponses.RankedObstacle;
import com.visionmapping.entity.Goal;
import com.visionmapping.entity.Obstacle;
import com.visionmapping.entity.Partner;
import com.visionmapping.entity.TaskItem;
import com.visionmapping.entity.VisionStep;
import com.visionmapping.entity.enums.ObstacleStatus;
import com.visionmapping.entity.enums.ObstacleType;
import com.visionmapping.entity.enums.PartnerStatus;
import com.visionmapping.entity.enums.PartnerSupportType;
import com.visionmapping.entity.enums.WorkStatus;
import com.visionmapping.repository.ObstacleRepository;
import com.visionmapping.repository.PartnerRepository;
import com.visionmapping.repository.TaskItemRepository;
import com.visionmapping.service.support.EntityLookup;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * V15 Phase 2: the obstacle helpers that need no AI (AH-NFR-03). Partner
 * matching (AH-FR-35), Blocked tasks with no obstacle (AH-FR-39) and the rule
 * order for "Which obstacle first?" (AH-FR-41). Everything is the signed-in
 * participant's own data, shown only to them.
 */
@Service
@RequiredArgsConstructor
public class ObstacleSupportService {

    /** The CLAUDE.md partner rules: the support types that fit each kind of gap. The first is suggested for a new partner. */
    private static final Map<ObstacleType, List<PartnerSupportType>> SUPPORT_TYPES = new EnumMap<>(ObstacleType.class);
    /** Words in a partner's role that also fit each kind of gap. */
    private static final Map<ObstacleType, List<String>> ROLE_WORDS = new EnumMap<>(ObstacleType.class);
    private static final Set<PartnerStatus> REACHABLE = Set.of(PartnerStatus.TO_CONTACT, PartnerStatus.CONTACTED, PartnerStatus.AGREED,
            PartnerStatus.ACTIVE, PartnerStatus.WAITING);
    private static final Set<ObstacleStatus> STILL_OPEN = Set.of(ObstacleStatus.OPEN, ObstacleStatus.IN_PROGRESS);
    static final int MAX_MATCHES = 5;

    static {
        SUPPORT_TYPES.put(ObstacleType.KNOWLEDGE, List.of(PartnerSupportType.MENTOR, PartnerSupportType.EXPERT));
        SUPPORT_TYPES.put(ObstacleType.SKILL, List.of(PartnerSupportType.TECHNICAL, PartnerSupportType.EXPERT));
        SUPPORT_TYPES.put(ObstacleType.TIME, List.of(PartnerSupportType.COLLEAGUE));
        SUPPORT_TYPES.put(ObstacleType.MONEY, List.of(PartnerSupportType.FINANCIAL));
        SUPPORT_TYPES.put(ObstacleType.MOTIVATION, List.of(PartnerSupportType.EMOTIONAL));
        SUPPORT_TYPES.put(ObstacleType.DECISION, List.of(PartnerSupportType.ADVISOR, PartnerSupportType.MENTOR));
        ROLE_WORDS.put(ObstacleType.KNOWLEDGE, List.of("mentor", "expert", "professor", "researcher", "teacher"));
        ROLE_WORDS.put(ObstacleType.SKILL, List.of("trainer", "technical", "developer", "engineer", "expert", "teacher"));
        ROLE_WORDS.put(ObstacleType.TIME, List.of("assistant", "delegate", "colleague", "helper", "volunteer"));
        ROLE_WORDS.put(ObstacleType.MONEY, List.of("sponsor", "investor", "funder", "donor", "financ"));
        ROLE_WORDS.put(ObstacleType.MOTIVATION, List.of("accountability", "coach", "friend", "buddy"));
        ROLE_WORDS.put(ObstacleType.DECISION, List.of("advisor", "adviser", "consultant", "mentor"));
    }

    private final EntityLookup lookup;
    private final PartnerRepository partnerRepository;
    private final ObstacleRepository obstacleRepository;
    private final TaskItemRepository taskRepository;
    private final Clock clock;

    /** AH-FR-35: the participant's partners whose support type or role fits the obstacle type. Never sent to Claude. */
    @Transactional(readOnly = true)
    public PartnerMatches partnerMatches(ObstacleType type) {
        List<PartnerSupportType> supportTypes = SUPPORT_TYPES.getOrDefault(type, List.of());
        List<String> words = ROLE_WORDS.getOrDefault(type, List.of());
        List<PartnerMatch> matches = partnerRepository.findByUser_IdAndArchivedFalse(lookup.userId()).stream()
                .filter(partner -> partner.getStatus() == null || REACHABLE.contains(partner.getStatus()))
                .filter(partner -> supportTypes.contains(partner.getSupportType()) || roleFits(partner.getRole(), words))
                .sorted(Comparator.comparing((Partner partner) -> !supportTypes.contains(partner.getSupportType()))
                        .thenComparing(Partner::getName, String.CASE_INSENSITIVE_ORDER))
                .limit(MAX_MATCHES)
                .map(partner -> new PartnerMatch(partner.getId(), partner.getName(), partner.getRole(),
                        partner.getSupportType() == null ? null : partner.getSupportType().name(),
                        partner.getStatus() == null ? null : partner.getStatus().name()))
                .toList();
        return new PartnerMatches(type.name(), supportTypes.isEmpty() ? PartnerSupportType.OTHER.name() : supportTypes.get(0).name(), matches);
    }

    private static boolean roleFits(@Nullable String role, List<String> words) {
        if (role == null || role.isBlank()) {
            return false;
        }
        String lower = role.toLowerCase(Locale.ROOT);
        return words.stream().anyMatch(lower::contains);
    }

    /** AH-FR-39: Blocked tasks with a blocker reason that no open-or-closed, unarchived obstacle names. */
    @Transactional(readOnly = true)
    public List<BlockedTask> blockedWithoutObstacle() {
        Long userId = lookup.userId();
        Set<Long> named = obstacleRepository.findByUser_IdAndArchivedFalse(userId).stream()
                .map(Obstacle::getRelatedTask)
                .filter(Objects::nonNull)
                .map(TaskItem::getId)
                .collect(Collectors.toSet());
        return taskRepository.findByUser_IdAndArchivedFalse(userId).stream()
                .filter(task -> task.getStatus() == WorkStatus.BLOCKED)
                .filter(task -> task.getBlockerReason() != null && !task.getBlockerReason().isBlank())
                .filter(task -> !named.contains(task.getId()))
                .sorted(Comparator.comparing(TaskItem::getId))
                .map(ObstacleSupportService::toBlocked)
                .toList();
    }

    private static BlockedTask toBlocked(TaskItem task) {
        VisionStep step = task.getStep();
        Goal goal = step == null ? null : step.getGoal();
        return new BlockedTask(task.getId(), task.getCode(), task.getTitle(), task.getBlockerReason(),
                step == null ? null : step.getId(), step == null ? null : step.getTitle(),
                goal == null ? null : goal.getId(), goal == null || goal.getDream() == null ? null : goal.getDream().getId());
    }

    /**
     * AH-FR-41: open obstacles in the rule order: severity first, then how many
     * Blocked tasks they hold up, then how long they have been open.
     */
    @Transactional(readOnly = true)
    public List<RankedObstacle> priority() {
        Long userId = lookup.userId();
        List<TaskItem> blocked = taskRepository.findByUser_IdAndArchivedFalse(userId).stream()
                .filter(task -> task.getStatus() == WorkStatus.BLOCKED)
                .toList();
        Instant now = Instant.now(clock);
        return obstacleRepository.findByUser_IdAndArchivedFalse(userId).stream()
                .filter(obstacle -> STILL_OPEN.contains(obstacle.getStatus()))
                .map(obstacle -> new RankedObstacle(obstacle.getId(), obstacle.getTitle(),
                        obstacle.getSeverity() == null ? null : obstacle.getSeverity().name(),
                        (int) blocked.stream().filter(task -> holdsUp(obstacle, task)).count(),
                        obstacle.getCreatedAt() == null ? 0 : Math.max(0, Duration.between(obstacle.getCreatedAt(), now).toDays()),
                        null))
                .sorted(Comparator.comparingInt((RankedObstacle ranked) -> severityRank(ranked.severity())).reversed()
                        .thenComparing(Comparator.comparingInt(RankedObstacle::blockedTasks).reversed())
                        .thenComparing(Comparator.comparingLong(RankedObstacle::daysOpen).reversed())
                        .thenComparing(RankedObstacle::id))
                .toList();
    }

    /** An obstacle holds up a Blocked task when it names the task, or the step, goal or dream the task sits under. */
    static boolean holdsUp(Obstacle obstacle, TaskItem task) {
        VisionStep step = task.getStep();
        Goal goal = step == null ? null : step.getGoal();
        return same(obstacle.getRelatedTask(), task.getId())
                || (step != null && same(obstacle.getRelatedStep(), step.getId()))
                || (goal != null && same(obstacle.getRelatedGoal(), goal.getId()))
                || (goal != null && goal.getDream() != null && same(obstacle.getRelatedDream(), goal.getDream().getId()));
    }

    private static boolean same(@Nullable Object entity, Long id) {
        if (entity instanceof TaskItem task) {
            return id.equals(task.getId());
        }
        if (entity instanceof VisionStep step) {
            return id.equals(step.getId());
        }
        if (entity instanceof Goal goal) {
            return id.equals(goal.getId());
        }
        if (entity instanceof com.visionmapping.entity.Dream dream) {
            return id.equals(dream.getId());
        }
        return false;
    }

    private static int severityRank(@Nullable String severity) {
        return severity == null ? -1 : com.visionmapping.entity.enums.Severity.valueOf(severity).ordinal();
    }
}
