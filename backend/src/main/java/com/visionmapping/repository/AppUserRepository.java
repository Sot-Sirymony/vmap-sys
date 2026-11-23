package com.visionmapping.repository;

import com.visionmapping.entity.AppUser;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AppUserRepository extends JpaRepository<AppUser, Long>,
        org.springframework.data.jpa.repository.JpaSpecificationExecutor<AppUser> {

    /**
     * V21 (TF-FR-32): gives every admin and coach without two-step sign-in a
     * set-up-by date, if they have none yet.
     */
    @org.springframework.transaction.annotation.Transactional
    @org.springframework.data.jpa.repository.Modifying(clearAutomatically = true, flushAutomatically = true)
    @org.springframework.data.jpa.repository.Query("update AppUser u set u.twoFactorDueAt = :due where u.role in :privilegedRoles"
            + " and u.twoFactorDueAt is null and not exists (select t from UserTwoFactor t where t.userId = u.id and t.enabledAt is not null)")
    int assignTwoFactorDueDates(@org.springframework.data.repository.query.Param("due") java.time.Instant due,
                                @org.springframework.data.repository.query.Param("privilegedRoles") java.util.Collection<String> privilegedRoles);

    /** V24 (SA-FR-14, D-24-4): with two-step sign-in required for everyone, every active account without it gets a date. */
    @org.springframework.transaction.annotation.Transactional
    @org.springframework.data.jpa.repository.Modifying(clearAutomatically = true, flushAutomatically = true)
    @org.springframework.data.jpa.repository.Query("update AppUser u set u.twoFactorDueAt = :due where u.status = :active"
            + " and u.twoFactorDueAt is null and not exists (select t from UserTwoFactor t where t.userId = u.id and t.enabledAt is not null)")
    int assignEveryoneTwoFactorDueDates(@org.springframework.data.repository.query.Param("due") java.time.Instant due,
                                        @org.springframework.data.repository.query.Param("active") com.visionmapping.entity.enums.UserStatus active);

    /** V24 (SA-FR-14): participants with a set-up-by date, for the two-step sign-in report. */
    java.util.List<AppUser> findByTwoFactorDueAtIsNotNullAndRoleNotInOrderByFullNameAsc(java.util.Collection<String> roles);

    /** V21 (TF-FR-37): a participant has no set-up-by date. */
    @org.springframework.transaction.annotation.Transactional
    @org.springframework.data.jpa.repository.Modifying(clearAutomatically = true, flushAutomatically = true)
    @org.springframework.data.jpa.repository.Query("update AppUser u set u.twoFactorDueAt = null"
            + " where u.role not in :privilegedRoles and u.twoFactorDueAt is not null")
    int clearParticipantTwoFactorDueDates(
            @org.springframework.data.repository.query.Param("privilegedRoles") java.util.Collection<String> privilegedRoles);

    /** V21 (TF-FR-36): admins and coaches, for the two-step sign-in report. */
    java.util.List<AppUser> findByRoleInOrderByRoleAscFullNameAsc(java.util.Collection<String> roles);

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

    /** V23 (UM-FR-41): whether any active account has this role. */
    boolean existsByRoleAndStatus(String role, com.visionmapping.entity.enums.UserStatus status);

    /** V23 (UM-FR-17): a session opened. A plain update, so it never overwrites other changes. */
    @org.springframework.data.jpa.repository.Modifying(flushAutomatically = true)
    @org.springframework.data.jpa.repository.Query("update AppUser u set u.lastSignInAt = :at where u.id = :id")
    int recordSignIn(@org.springframework.data.repository.query.Param("id") Long id,
                     @org.springframework.data.repository.query.Param("at") java.time.Instant at);

    /** V23 (UM-FR-18): accounts per role and per status, for the counts on the Users page. */
    @org.springframework.data.jpa.repository.Query("select u.role, u.status, count(u) from AppUser u group by u.role, u.status")
    java.util.List<Object[]> countByRoleAndStatus();

    /** V23 (UM-FR-40): how many active accounts have this role. */
    long countByRoleAndStatus(String role, com.visionmapping.entity.enums.UserStatus status);

    /** V23 (UM-FR-08, UM-FR-14): accounts holding a role. */
    long countByRole(String role);

    java.util.List<AppUser> findByRole(String role);
}
