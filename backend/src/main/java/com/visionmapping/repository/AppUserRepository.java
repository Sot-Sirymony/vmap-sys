package com.visionmapping.repository;

import com.visionmapping.entity.AppUser;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AppUserRepository extends JpaRepository<AppUser, Long> {

    Optional<AppUser> findByEmail(String email);

    /** V9 (AI-FR-24): true when this person's active cohort has AI switched off. */
    boolean existsByIdAndCohort_ArchivedFalseAndCohort_AiEnabledFalse(Long id);

    java.util.List<AppUser> findByCohort_IdOrderByFullNameAsc(Long cohortId);

    long countByCohort_Id(Long cohortId);

    boolean existsByEmail(String email);

    /** FR-77: who the reminder job has to consider. */
    java.util.List<AppUser> findByRemindersEnabledTrueAndStatus(com.visionmapping.entity.enums.UserStatus status);

    /** FR-77 and V19 RM-FR-49: everyone with daily reminders or a report reminder on. */
    @org.springframework.data.jpa.repository.Query("select u from AppUser u where u.status = :status and (u.remindersEnabled = true "
            + "or u.reportReminder <> com.visionmapping.entity.enums.ReportReminder.NONE)")
    java.util.List<AppUser> findWithRemindersOn(@org.springframework.data.repository.query.Param("status") com.visionmapping.entity.enums.UserStatus status);

    /** V15 (AH-FR-57): the page switches of the person's cohort, when they are in one that is not archived. */
    @org.springframework.data.jpa.repository.Query("""
            select c.aiPagesOff from AppUser u join u.cohort c where u.id = :userId and c.archived = false
            """)
    java.util.Optional<String> findCohortPagesOff(@org.springframework.data.repository.query.Param("userId") Long userId);
}
