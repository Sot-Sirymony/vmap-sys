package com.visionmapping.repository;

import com.visionmapping.entity.Habit;
import org.springframework.data.jpa.repository.JpaRepository;

public interface HabitRepository extends JpaRepository<Habit, Long>, UserScopedRepository<Habit> {

    long countByUser_IdAndArchivedFalse(Long userId);
}
