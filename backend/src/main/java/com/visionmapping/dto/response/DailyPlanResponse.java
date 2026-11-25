package com.visionmapping.dto.response;

import com.visionmapping.entity.enums.Priority;
import com.visionmapping.entity.enums.WorkStatus;
import com.visionmapping.util.Copies;
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
        long undatedStepCount,
        // V32 (D-32-9, SF-FR-12): Streamline "Remind me" items due today.
        List<StreamlineReminder> streamlineReminders
) {

    public DailyPlanResponse {
        items = Copies.list(items);
        habits = Copies.list(habits);
        streamlineReminders = Copies.list(streamlineReminders);
    }

    /** V32 (D-32-9): one Streamline "Remind me" item due today. */
    public record StreamlineReminder(Kind kind, Long id, String code, String title,
                                     com.visionmapping.entity.enums.RepeatCadence cadence) {
    }


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
