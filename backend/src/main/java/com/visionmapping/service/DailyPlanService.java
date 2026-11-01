package com.visionmapping.service;

import com.visionmapping.dto.response.AttitudeCheckInResponse;
import com.visionmapping.dto.response.DailyPlanResponse;
import com.visionmapping.dto.response.DailyPlanResponse.Item;
import com.visionmapping.dto.response.DailyPlanResponse.Kind;
import com.visionmapping.entity.Dream;
import com.visionmapping.entity.Review;
import com.visionmapping.entity.TaskItem;
import com.visionmapping.entity.VisionStep;
import com.visionmapping.entity.enums.ReviewType;
import com.visionmapping.entity.enums.WorkStatus;
import com.visionmapping.mapper.VisionMappingMapper;
import com.visionmapping.repository.AttitudeCheckInRepository;
import com.visionmapping.repository.ReviewRepository;
import com.visionmapping.repository.TaskItemRepository;
import com.visionmapping.repository.VisionStepRepository;
import com.visionmapping.service.support.EntityLookup;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * FR-67 and FR-70: the day's work. Only steps and tasks due on the date or
 * overdue appear (AC-04). Completed work is done and paused work is on hold by
 * the user's own choice, so neither asks for attention today.
 */
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class DailyPlanService {

    private static final List<WorkStatus> NOT_ACTIONABLE = List.of(WorkStatus.COMPLETED, WorkStatus.PAUSED);
    private static final int WEEKLY_REVIEW_INTERVAL_DAYS = 7;

    private final EntityLookup lookup;
    private final VisionMappingMapper mapper;
    private final VisionStepRepository visionStepRepository;
    private final TaskItemRepository taskItemRepository;
    private final AttitudeCheckInRepository attitudeCheckInRepository;
    private final ReviewRepository reviewRepository;
    private final HabitService habitService;

    public DailyPlanResponse planFor(LocalDate date) {
        Long userId = lookup.userId();
        List<Item> items = itemsFor(userId, date);
        AttitudeCheckInResponse attitude = attitudeCheckInRepository.findByUser_IdAndCheckInDate(userId, date)
                .map(mapper::toResponse)
                .orElse(null);
        LocalDate lastWeeklyReview = lastWeeklyReview(userId);
        return new DailyPlanResponse(date, items, attitude, habitService.listHabits(date, false),
                weeklyReviewDue(lastWeeklyReview, date), lastWeeklyReview);
    }

    /** Takes the user explicitly because the reminder job runs with nobody signed in. */
    public List<Item> itemsFor(Long userId, LocalDate date) {
        return Stream.concat(
                        visionStepRepository.findDueForDailyPlan(userId, date, NOT_ACTIONABLE).stream().map(step -> toItem(step, date)),
                        taskItemRepository.findDueForDailyPlan(userId, date, NOT_ACTIONABLE).stream().map(task -> toItem(task, date)))
                .sorted(Comparator.comparing(Item::priority).reversed()
                        .thenComparing(Item::dueDate)
                        .thenComparing(Item::title))
                .toList();
    }

    public boolean weeklyReviewDueFor(Long userId, LocalDate date) {
        return weeklyReviewDue(lastWeeklyReview(userId), date);
    }

    private LocalDate lastWeeklyReview(Long userId) {
        return reviewRepository
                .findFirstByUser_IdAndReviewTypeAndArchivedFalseOrderByReviewDateDesc(userId, ReviewType.WEEKLY)
                .map(Review::getReviewDate)
                .map(reviewDate -> reviewDate.toLocalDate())
                .orElse(null);
    }

    private static boolean weeklyReviewDue(LocalDate lastWeeklyReview, LocalDate date) {
        return lastWeeklyReview == null || !lastWeeklyReview.plusDays(WEEKLY_REVIEW_INTERVAL_DAYS).isAfter(date);
    }

    private Item toItem(VisionStep step, LocalDate date) {
        Dream dream = step.getGoal().getDream();
        return new Item(Kind.STEP, step.getId(), step.getCode(), step.getTitle(), step.getOwner(), step.getPriority(),
                step.getTargetDate(), step.getStatus(), step.getTargetDate().isBefore(date), dream.getId(), dream.getTitle());
    }

    private Item toItem(TaskItem task, LocalDate date) {
        Dream dream = task.getStep().getGoal().getDream();
        return new Item(Kind.TASK, task.getId(), task.getCode(), task.getTitle(), task.getOwner(), task.getPriority(),
                task.getDueDate(), task.getStatus(), task.getDueDate().isBefore(date), dream.getId(), dream.getTitle());
    }
}
