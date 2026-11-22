package com.visionmapping.repository;

import com.visionmapping.entity.AppUser;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AppUserRepository extends JpaRepository<AppUser, Long> {

    /**
     * V21 (TF-FR-32): gives every admin and coach without two-step sign-in a
     * set-up-by date, if they have none yet.
     */
    @org.springframework.transaction.annotation.Transactional
    @org.springframework.data.jpa.repository.Modifying(clearAutomatically = true, flushAutomatically = true)
    @org.springframework.data.jpa.repository.Query("update AppUser u set u.twoFactorDueAt = :due where u.role <> com.visionmapping.entity.enums.UserRole.USER"
            + " and u.twoFactorDueAt is null and not exists (select t from UserTwoFactor t where t.userId = u.id and t.enabledAt is not null)")
    int assignTwoFactorDueDates(@org.springframework.data.repository.query.Param("due") java.time.Instant due);

    /** V21 (TF-FR-37): a participant has no set-up-by date. */
    @org.springframework.transaction.annotation.Transactional
    @org.springframework.data.jpa.repository.Modifying(clearAutomatically = true, flushAutomatically = true)
    @org.springframework.data.jpa.repository.Query("update AppUser u set u.twoFactorDueAt = null"
            + " where u.role = com.visionmapping.entity.enums.UserRole.USER and u.twoFactorDueAt is not null")
    int clearParticipantTwoFactorDueDates();

    /** V21 (TF-FR-36): admins and coaches, for the two-step sign-in report. */
    java.util.List<AppUser> findByRoleNotOrderByRoleAscFullNameAsc(com.visionmapping.entity.enums.UserRole role);

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
