package com.visionmapping.service.support;

import static org.assertj.core.api.Assertions.assertThat;

import com.visionmapping.entity.TaskStatusChange;
import com.visionmapping.entity.enums.WorkStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

/** V27 §3.3: cycle time, waiting and blocked time. */
class CycleTimeCalculatorTest {

    private static final Instant T0 = Instant.parse("2026-11-01T00:00:00Z");

    @Test
    void theMedianNeedsAtLeastThreeTasks() {
        assertThat(CycleTimeCalculator.median(List.of(new BigDecimal("2.0"), new BigDecimal("4.0")))).isNull();
        assertThat(CycleTimeCalculator.median(List.of(new BigDecimal("5.0"), new BigDecimal("1.0"), new BigDecimal("3.0"))))
                .isEqualByComparingTo("3.0");
        assertThat(CycleTimeCalculator.median(List.of(new BigDecimal("1.0"), new BigDecimal("2.0"), new BigDecimal("4.0"), new BigDecimal("9.0"))))
                .isEqualByComparingTo("3.0");
    }

    @Test
    void waitingAndBlockedTimeAreSummedFromTheHistoryAndAnOpenIntervalRunsToNow() {
        List<TaskStatusChange> changes = List.of(
                change(null, WorkStatus.NOT_STARTED, 0),
                change(WorkStatus.NOT_STARTED, WorkStatus.WAITING, 24),
                change(WorkStatus.WAITING, WorkStatus.IN_PROGRESS, 72),
                change(WorkStatus.IN_PROGRESS, WorkStatus.BLOCKED, 96));
        CycleTimeCalculator.Delays delays = CycleTimeCalculator.delaysOf(changes, T0.plusSeconds(108 * 3600L));
        assertThat(delays.waitingDays()).isEqualByComparingTo("2.0");
        assertThat(delays.blockedDays()).isEqualByComparingTo("0.5");
    }

    private static TaskStatusChange change(WorkStatus from, WorkStatus to, long hours) {
        return TaskStatusChange.builder().fromStatus(from).toStatus(to).changedAt(T0.plusSeconds(hours * 3600)).build();
    }
}
