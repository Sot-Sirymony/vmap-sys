package com.visionmapping.repository;

import com.visionmapping.entity.AppUser;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AppUserRepository extends JpaRepository<AppUser, Long> {

    Optional<AppUser> findByEmail(String email);

    boolean existsByEmail(String email);

    /** FR-77: who the reminder job has to consider. */
    java.util.List<AppUser> findByRemindersEnabledTrueAndStatus(com.visionmapping.entity.enums.UserStatus status);
}
