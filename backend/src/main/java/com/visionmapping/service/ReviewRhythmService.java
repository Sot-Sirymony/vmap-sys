package com.visionmapping.service;

import com.visionmapping.dto.request.ReviewRhythmRequest;
import com.visionmapping.dto.response.AiPlanResponses.DueReview;
import com.visionmapping.dto.response.AiPlanResponses.ReviewRhythm;
import com.visionmapping.entity.AppUser;
import com.visionmapping.entity.Dream;
import com.visionmapping.entity.DreamReviewRhythm;
import com.visionmapping.entity.enums.ReviewType;
import com.visionmapping.repository.DreamReviewRhythmRepository;
import com.visionmapping.repository.ReviewRepository;
import com.visionmapping.service.support.EntityLookup;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * V31 v1 step 3 (BRD 31 stage 8, DC-FR-48 to DC-FR-50, D-31-9): a review
 * rhythm per dream, and which reviews it makes due. A review is due from the
 * day its period starts (today for daily; the chosen weekday; the chosen day of
 * the month; the first day of the quarter) until a review of that kind is
 * written for the dream. Nothing is created ahead of time. Every lookup is by
 * the caller's own dream, so another user's dream answers 404.
 */
@Service
@RequiredArgsConstructor
public class ReviewRhythmService {

    private static final Map<String, DayOfWeek> DAYS = Map.of("MON", DayOfWeek.MONDAY, "TUE", DayOfWeek.TUESDAY,
            "WED", DayOfWeek.WEDNESDAY, "THU", DayOfWeek.THURSDAY, "FRI", DayOfWeek.FRIDAY, "SAT", DayOfWeek.SATURDAY,
            "SUN", DayOfWeek.SUNDAY);

    private final DreamReviewRhythmRepository rhythms;
    private final ReviewRepository reviews;
    private final EntityLookup lookup;
    private final Clock clock;

    /** The dream's rhythm, or empty when it has none. */
    @Transactional(readOnly = true)
    public Optional<ReviewRhythm> get(Long dreamId) {
        Dream dream = lookup.dream(dreamId);
        return rhythms.findByDream_IdAndUser_Id(dream.getId(), lookup.userId()).map(ReviewRhythmService::toResponse);
    }

    /** Sets the dream's rhythm, creating it the first time. */
    @Transactional
    public ReviewRhythm save(Long dreamId, ReviewRhythmRequest request) {
        Dream dream = lookup.dream(dreamId);
        AppUser user = lookup.currentUser();
        DreamReviewRhythm rhythm = rhythms.findByDream_IdAndUser_Id(dream.getId(), user.getId())
                .orElseGet(() -> DreamReviewRhythm.builder().user(user).dream(dream).build());
        rhythm.change(request.daily(), blankToNull(request.weeklyDay()), request.monthlyDay(), request.quarterly());
        return toResponse(rhythms.save(rhythm));
    }

    /** DC-FR-50: the caller's reviews due today, by dream. */
    @Transactional(readOnly = true)
    public List<DueReview> due() {
        return dueFor(lookup.userId(), LocalDate.now(clock));
    }

    /** DC-FR-50: the reviews due on a day for one user; also used by the daily reminder. */
    @Transactional(readOnly = true)
    public List<DueReview> dueFor(Long userId, LocalDate today) {
        List<DueReview> due = new ArrayList<>();
        for (DreamReviewRhythm rhythm : rhythms.findByUser_IdAndDream_ArchivedFalse(userId)) {
            Dream dream = rhythm.getDream();
            if (rhythm.isDaily()) {
                addIfDue(due, userId, dream, ReviewType.DAILY, today);
            }
            DayOfWeek weekday = rhythm.getWeeklyDay() == null ? null : DAYS.get(rhythm.getWeeklyDay());
            if (weekday != null) {
                addIfDue(due, userId, dream, ReviewType.WEEKLY, today.with(TemporalAdjusters.previousOrSame(weekday)));
            }
            if (rhythm.getMonthlyDay() != null) {
                LocalDate thisMonth = today.withDayOfMonth(rhythm.getMonthlyDay());
                addIfDue(due, userId, dream, ReviewType.MONTHLY, thisMonth.isAfter(today) ? thisMonth.minusMonths(1) : thisMonth);
            }
            if (rhythm.isQuarterly()) {
                int firstMonth = (today.getMonthValue() - 1) / 3 * 3 + 1;
                addIfDue(due, userId, dream, ReviewType.QUARTERLY, LocalDate.of(today.getYear(), firstMonth, 1));
            }
        }
        return due;
    }

    private void addIfDue(List<DueReview> due, Long userId, Dream dream, ReviewType type, LocalDate since) {
        if (!reviews.existsByUser_IdAndRelatedDream_IdAndReviewTypeAndArchivedFalseAndReviewDateGreaterThanEqual(userId, dream.getId(),
                type, since.atStartOfDay())) {
            due.add(new DueReview(dream.getId(), dream.getTitle(), type.name(), since));
        }
    }

    private static ReviewRhythm toResponse(DreamReviewRhythm rhythm) {
        return new ReviewRhythm(rhythm.getDream().getId(), rhythm.isDaily(), rhythm.getWeeklyDay(), rhythm.getMonthlyDay(),
                rhythm.isQuarterly());
    }

    @Nullable
    private static String blankToNull(@Nullable String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
