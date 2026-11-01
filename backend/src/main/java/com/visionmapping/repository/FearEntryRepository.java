package com.visionmapping.repository;

import com.visionmapping.entity.FearEntry;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FearEntryRepository extends JpaRepository<FearEntry, Long>, UserScopedRepository<FearEntry> {

    boolean existsByUser_IdAndArchivedFalse(Long userId);
}
