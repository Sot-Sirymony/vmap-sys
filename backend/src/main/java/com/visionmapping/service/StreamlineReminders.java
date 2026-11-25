package com.visionmapping.service;

import com.visionmapping.dto.response.DailyPlanResponse;
import com.visionmapping.entity.StreamlineItem;
import com.visionmapping.entity.enums.RepeatCadence;
import com.visionmapping.entity.enums.WorkStatus;
import com.visionmapping.repository.StreamlineItemRepository;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * V32 (D-32-9, BR-32-8, SF-FR-11, SF-FR-12): the Streamline "Remind me" items
 * due on a day, for the reminder email and the Today page. An item reminds
 * until its step or task is completed or archived, or the user changes its
 * choice. Takes the user explicitly because the reminder job runs with nobody
 * signed in.
 */
@Component
@RequiredArgsConstructor
public class StreamlineReminders {

    private final StreamlineItemRepository items;

    @Transactional(readOnly = true)
    public List<DailyPlanResponse.StreamlineReminder> dueFor(Long userId, LocalDate date) {
        List<DailyPlanResponse.StreamlineReminder> out = new ArrayList<>();
        // A step or task in two reviews reminds once.
        Set<String> seen = new HashSet<>();
        for (StreamlineItem item : items.findReminders(userId)) {
            if (item.targetArchived() || item.getArchivedAt() != null && item.getRestoredAt() == null || !due(item, date)) {
                continue;
            }
            boolean step = item.isStep();
            WorkStatus status = step ? item.getStep().getStatus() : item.getTask().getStatus();
            Long id = step ? item.getStep().getId() : item.getTask().getId();
            if (status == WorkStatus.COMPLETED || !seen.add((step ? "S" : "T") + id)) {
                continue;
            }
            out.add(new DailyPlanResponse.StreamlineReminder(step ? DailyPlanResponse.Kind.STEP : DailyPlanResponse.Kind.TASK, id,
                    item.code(), item.title(), item.getRemindCadence()));
        }
        return out;
    }

    /** Daily every day; weekly on its weekday (1 = Monday); monthly on its day of the month. */
    static boolean due(StreamlineItem item, LocalDate date) {
        RepeatCadence cadence = item.getRemindCadence();
        Integer day = item.getRemindDay();
        if (cadence == null) {
            return false;
        }
        return switch (cadence) {
            case DAILY -> true;
            case WEEKLY -> day != null && date.getDayOfWeek().getValue() == day;
            case MONTHLY -> day != null && date.getDayOfMonth() == day;
        };
    }
}
