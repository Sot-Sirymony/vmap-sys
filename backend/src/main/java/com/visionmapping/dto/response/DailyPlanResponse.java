package com.visionmapping.dto.response;

import com.visionmapping.entity.enums.Priority;
import com.visionmapping.entity.enums.WorkStatus;
import java.time.LocalDate;
import java.util.List;

/** FR-67: everything the Today page shows, in one request. */
public record DailyPlanResponse(
        LocalDate date,
        List<Item> items,
        AttitudeCheckInResponse attitude,
        List<HabitResponse> habits,
        boolean weeklyReviewDue,
        LocalDate lastWeeklyReviewDate,
        long undatedStepCount
) {

    public enum Kind {
        STEP,
        TASK
    }

    public record Item(
            Kind kind,
            Long id,
            String code,
            String title,
            String owner,
            Priority priority,
            LocalDate dueDate,
            WorkStatus status,
            boolean overdue,
            Long dreamId,
            String dreamTitle
    ) {
    }
}
