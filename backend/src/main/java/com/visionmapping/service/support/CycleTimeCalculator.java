package com.visionmapping.service.support;

import com.visionmapping.entity.TaskItem;
import com.visionmapping.entity.TaskStatusChange;
import com.visionmapping.entity.enums.WorkStatus;
import com.visionmapping.repository.TaskStatusChangeRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;

/**
 * V27 (BRD 27 §3.3): cycle time, waiting time and blocked time.
 *
 * <ul>
 *   <li>Task cycle time = completedAt − startedAt (TaskItem#cycleTimeDays).</li>
 *   <li>Waiting and blocked time = the time spent in those statuses, from the status history.</li>
 *   <li>A step's or goal's cycle time = the median of its tasks completed in the last 90 days,
 *       shown only with at least {@link #MIN_TASKS} tasks (D-27-7: older tasks without a start
 *       time are left out, not guessed).</li>
 * </ul>
 */
@Component
@RequiredArgsConstructor
public class CycleTimeCalculator {

    public static final int MIN_TASKS = 3;
    public static final int WINDOW_DAYS = 90;

    private final TaskStatusChangeRepository history;
    private final Clock clock;

    /** Waiting and blocked days for one task. */
    public record Delays(BigDecimal waitingDays, BigDecimal blockedDays) {
        public static final Delays NONE = new Delays(BigDecimal.ZERO.setScale(1), BigDecimal.ZERO.setScale(1));
    }

    /** The median cycle time of the tasks completed in the window, or null with fewer than MIN_TASKS. */
    @Nullable
    public BigDecimal medianCycleDays(Collection<TaskItem> tasks) {
        Instant since = Instant.now(clock).minus(Duration.ofDays(WINDOW_DAYS));
        List<BigDecimal> values = new ArrayList<>();
        for (TaskItem task : tasks) {
            BigDecimal days = task.cycleTimeDays();
            if (days != null && task.getCompletedAt() != null && !task.getCompletedAt().isBefore(since)) {
                values.add(days);
            }
        }
        return median(values);
    }

    @Nullable
    public static BigDecimal median(List<BigDecimal> values) {
        if (values.size() < MIN_TASKS) {
            return null;
        }
        List<BigDecimal> sorted = values.stream().sorted().toList();
        int middle = sorted.size() / 2;
        BigDecimal value = sorted.size() % 2 == 1
                ? sorted.get(middle)
                : sorted.get(middle - 1).add(sorted.get(middle)).divide(BigDecimal.valueOf(2), 1, RoundingMode.HALF_UP);
        return value.setScale(1, RoundingMode.HALF_UP);
    }

    /** Waiting and blocked days for each task, from one query. Tasks with no history get zero. */
    public Map<Long, Delays> delays(Long userId, Collection<TaskItem> tasks) {
        Map<Long, Delays> result = new HashMap<>();
        if (tasks.isEmpty()) {
            return result;
        }
        Map<Long, TaskItem> byId = new HashMap<>();
        tasks.forEach(task -> byId.put(task.getId(), task));
        Map<Long, List<TaskStatusChange>> changes = new HashMap<>();
        for (TaskStatusChange change : history.findForTasks(userId, byId.keySet())) {
            changes.computeIfAbsent(change.getTask().getId(), key -> new ArrayList<>()).add(change);
        }
        Instant now = Instant.now(clock);
        for (TaskItem task : tasks) {
            result.put(task.getId(), delaysOf(changes.getOrDefault(task.getId(), List.of()), now));
        }
        return result;
    }

    /** Sums the intervals spent Waiting and Blocked; an open interval runs to now. */
    static Delays delaysOf(List<TaskStatusChange> changes, Instant now) {
        long waitingMinutes = 0;
        long blockedMinutes = 0;
        for (int index = 0; index < changes.size(); index++) {
            TaskStatusChange change = changes.get(index);
            Instant end = index + 1 < changes.size() ? changes.get(index + 1).getChangedAt() : now;
            long minutes = Math.max(0, Duration.between(change.getChangedAt(), end).toMinutes());
            if (change.getToStatus() == WorkStatus.WAITING) {
                waitingMinutes += minutes;
            } else if (change.getToStatus() == WorkStatus.BLOCKED) {
                blockedMinutes += minutes;
            }
        }
        return new Delays(days(waitingMinutes), days(blockedMinutes));
    }

    private static BigDecimal days(long minutes) {
        return BigDecimal.valueOf(minutes / (60.0 * 24.0)).setScale(1, RoundingMode.HALF_UP);
    }
}
