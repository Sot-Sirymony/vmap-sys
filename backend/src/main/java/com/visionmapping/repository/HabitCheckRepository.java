package com.visionmapping.repository;

import com.visionmapping.entity.HabitCheck;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface HabitCheckRepository extends JpaRepository<HabitCheck, Long> {

    Optional<HabitCheck> findByHabit_IdAndCheckDate(Long habitId, LocalDate checkDate);

    @Query("select c.checkDate from HabitCheck c where c.habit.id = :habitId order by c.checkDate desc")
    List<LocalDate> findDatesByHabitId(@Param("habitId") Long habitId);
}
