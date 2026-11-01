package com.visionmapping.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

/** FR-68: today not being checked yet must not break a streak; a missed day must. */
class HabitStreakTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 11, 10);

    @Test
    void countsConsecutiveDaysEndingToday() {
        assertThat(HabitService.currentStreak(List.of(TODAY, TODAY.minusDays(1), TODAY.minusDays(2)), TODAY)).isEqualTo(3);
    }

    @Test
    void todayNotYetCheckedKeepsYesterdaysStreak() {
        assertThat(HabitService.currentStreak(List.of(TODAY.minusDays(1), TODAY.minusDays(2)), TODAY)).isEqualTo(2);
    }

    @Test
    void aMissedDayEndsTheStreak() {
        assertThat(HabitService.currentStreak(List.of(TODAY, TODAY.minusDays(2), TODAY.minusDays(3)), TODAY)).isEqualTo(1);
        assertThat(HabitService.currentStreak(List.of(TODAY.minusDays(2)), TODAY)).isZero();
    }

    @Test
    void noChecksMeansNoStreak() {
        assertThat(HabitService.currentStreak(List.of(), TODAY)).isZero();
    }
}
