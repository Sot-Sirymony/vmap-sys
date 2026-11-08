package com.visionmapping.service;

import static com.visionmapping.service.support.ServiceSupport.findAllForUser;
import static com.visionmapping.service.support.ServiceSupport.requireArchived;
import static com.visionmapping.service.support.ServiceSupport.requireNotFutureDay;

import com.visionmapping.dto.request.HabitRequest;
import com.visionmapping.dto.response.HabitResponse;
import com.visionmapping.entity.Habit;
import com.visionmapping.entity.HabitCheck;
import com.visionmapping.exception.BusinessRuleException;
import com.visionmapping.exception.ResourceNotFoundException;
import com.visionmapping.repository.HabitCheckRepository;
import com.visionmapping.repository.HabitRepository;
import com.visionmapping.service.support.ArchiveFilter;
import com.visionmapping.service.support.EntityLookup;
import java.time.Clock;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * FR-68: up to three active disciplines with daily checks and a streak. The
 * date always comes from the client, because "today" is the user's local day
 * and the server may run in another time zone.
 */
@Service
@Transactional
@RequiredArgsConstructor
public class HabitService {

    static final int MAX_ACTIVE_HABITS = 3;

    private final EntityLookup lookup;
    private final HabitRepository habitRepository;
    private final HabitCheckRepository habitCheckRepository;
    private final Clock clock;

    @Transactional(readOnly = true)
    public List<HabitResponse> listHabits(LocalDate date, ArchiveFilter archiveFilter) {
        return findAllForUser(habitRepository, lookup.userId(), archiveFilter).stream()
                .sorted(Comparator.comparing(Habit::getId))
                .map(habit -> toResponse(habit, date))
                .toList();
    }

    public HabitResponse createHabit(HabitRequest request, LocalDate date) {
        requireRoomForAnotherHabit();
        Habit habit = Habit.builder().user(lookup.currentUser()).name(request.name()).build();
        return toResponse(habitRepository.save(habit), date);
    }

    public HabitResponse renameHabit(Long id, HabitRequest request, LocalDate date) {
        Habit habit = habit(id);
        habit.setName(request.name());
        return toResponse(habit, date);
    }

    public HabitResponse setChecked(Long id, LocalDate date, boolean checked) {
        requireNotFutureDay(date, clock);
        Habit habit = habit(id);
        var existing = habitCheckRepository.findByHabit_IdAndCheckDate(id, date);
        if (checked && existing.isEmpty()) {
            habitCheckRepository.saveAndFlush(HabitCheck.builder().habit(habit).checkDate(date).build());
        } else if (!checked && existing.isPresent()) {
            habitCheckRepository.delete(existing.get());
            habitCheckRepository.flush();
        }
        return toResponse(habit, date);
    }

    public void archiveHabit(Long id) {
        habit(id).setArchived(true);
    }

    public void restoreHabit(Long id) {
        Habit habit = habit(id);
        if (habit.isArchived()) {
            requireRoomForAnotherHabit();
            habit.setArchived(false);
        }
    }

    public void permanentlyDeleteHabit(Long id) {
        Habit habit = habit(id);
        requireArchived(habit.isArchived(), "Habit");
        habitRepository.delete(habit);
    }

    /**
     * Consecutive kept days ending today. A day not yet checked does not break
     * the streak until it has passed, so it can still count from yesterday.
     */
    static int currentStreak(List<LocalDate> checkedDates, LocalDate today) {
        Set<LocalDate> checked = new HashSet<>(checkedDates);
        LocalDate cursor = checked.contains(today) ? today : today.minusDays(1);
        int streak = 0;
        while (checked.contains(cursor)) {
            streak++;
            cursor = cursor.minusDays(1);
        }
        return streak;
    }

    private void requireRoomForAnotherHabit() {
        if (habitRepository.countByUser_IdAndArchivedFalse(lookup.userId()) >= MAX_ACTIVE_HABITS) {
            throw new BusinessRuleException("You can track up to %d habits at once. Archive one to add another.".formatted(MAX_ACTIVE_HABITS));
        }
    }

    private HabitResponse toResponse(Habit habit, LocalDate date) {
        List<LocalDate> dates = habit.getId() == null ? List.of() : habitCheckRepository.findDatesByHabitId(habit.getId());
        return new HabitResponse(habit.getId(), habit.getName(), dates.contains(date), currentStreak(dates, date), habit.isArchived());
    }

    private Habit habit(Long id) {
        return habitRepository.findById(id)
                .filter(entity -> entity.getUser().getId().equals(lookup.userId()))
                .orElseThrow(() -> new ResourceNotFoundException("Habit not found: " + id));
    }
}
