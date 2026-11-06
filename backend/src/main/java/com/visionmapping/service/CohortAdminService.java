package com.visionmapping.service;

import com.visionmapping.security.audit.SecurityOutcome;
import com.visionmapping.security.audit.SecurityEventService;
import com.visionmapping.security.audit.SecurityEventType;
import com.visionmapping.security.audit.SecurityAudit;
import com.visionmapping.security.Roles;
import com.visionmapping.dto.request.CohortMemberRequest;
import com.visionmapping.dto.request.CohortRequest;
import com.visionmapping.dto.response.CohortMemberResponse;
import com.visionmapping.dto.response.CohortResponse;
import com.visionmapping.entity.AppUser;
import com.visionmapping.entity.Cohort;
import com.visionmapping.entity.enums.UserRole;
import com.visionmapping.exception.BusinessRuleException;
import com.visionmapping.exception.ResourceNotFoundException;
import com.visionmapping.repository.AppUserRepository;
import com.visionmapping.repository.CohortRepository;
import com.visionmapping.service.support.EntityLookup;
import java.security.SecureRandom;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * V9 (v0.1 FR-01, FR-20): admins create cohorts, choose each one's coach, and
 * move participants. Naming someone as a coach gives them the COACH role.
 */
@Service
@Transactional
@RequiredArgsConstructor
public class CohortAdminService {

    // No 0/O or 1/I/L, so a code read aloud or copied by hand is not mistyped.
    private static final String CODE_ALPHABET = "ABCDEFGHJKMNPQRSTUVWXYZ23456789";
    private static final int CODE_LENGTH = 8;
    private static final SecureRandom RANDOM = new SecureRandom();

    private final EntityLookup lookup;
    private final SecurityEventService securityEvents;
    private final CohortRepository cohortRepository;
    private final AppUserRepository appUserRepository;

    @Transactional(readOnly = true)
    public List<CohortResponse> list() {
        requireAdmin();
        return cohortRepository.findAllByOrderByArchivedAscNameAsc().stream().map(this::toResponse).toList();
    }

    @SecurityAudit(value = SecurityEventType.COHORT_CHANGED, target = "cohort")
    public CohortResponse create(CohortRequest request) {
        requireAdmin();
        Cohort cohort = Cohort.builder().joinCode(newCode()).build();
        apply(cohort, request);
        return toResponse(cohortRepository.save(cohort));
    }

    @SecurityAudit(value = SecurityEventType.COHORT_CHANGED, target = "cohort")
    public CohortResponse update(Long id, CohortRequest request) {
        requireAdmin();
        Cohort cohort = cohort(id);
        apply(cohort, request);
        return toResponse(cohortRepository.save(cohort));
    }

    /** A new code stops the old one working, for example after it was shared too widely. */
    @SecurityAudit(value = SecurityEventType.COHORT_CHANGED, target = "cohort")
    public CohortResponse newJoinCode(Long id) {
        requireAdmin();
        Cohort cohort = cohort(id);
        cohort.setJoinCode(newCode());
        return toResponse(cohortRepository.save(cohort));
    }

    @SecurityAudit(value = SecurityEventType.COHORT_CHANGED, target = "cohort")
    public CohortResponse setArchived(Long id, boolean archived) {
        requireAdmin();
        Cohort cohort = cohort(id);
        cohort.setArchived(archived);
        return toResponse(cohortRepository.save(cohort));
    }

    @Transactional(readOnly = true)
    public List<CohortMemberResponse> members(Long id) {
        requireAdmin();
        cohort(id);
        return appUserRepository.findByCohort_IdOrderByFullNameAsc(id).stream()
                .map(user -> new CohortMemberResponse(user.getId(), user.getFullName(), user.getEmail()))
                .toList();
    }

    /** Puts a registered person in this cohort, moving them out of any other. */
    @SecurityAudit(value = SecurityEventType.COHORT_MEMBER_CHANGED, target = "cohort")
    public CohortMemberResponse addMember(Long id, CohortMemberRequest request) {
        requireAdmin();
        Cohort cohort = cohort(id);
        AppUser user = userByEmail(request.email());
        user.setCohort(cohort);
        appUserRepository.save(user);
        return new CohortMemberResponse(user.getId(), user.getFullName(), user.getEmail());
    }

    @SecurityAudit(value = SecurityEventType.COHORT_MEMBER_CHANGED, target = "cohort")
    public void removeMember(Long id, Long userId) {
        requireAdmin();
        AppUser user = appUserRepository.findById(userId)
                .filter(member -> member.getCohort() != null && member.getCohort().getId().equals(id))
                .orElseThrow(() -> new ResourceNotFoundException("That person is not in this cohort."));
        user.setCohort(null);
        appUserRepository.save(user);
    }

    private void apply(Cohort cohort, CohortRequest request) {
        cohort.setName(request.name().trim());
        cohort.setStartDate(request.startDate());
        if (request.aiEnabled() != null) {
            cohort.setAiEnabled(request.aiEnabled());
        }
        String coachEmail = request.coachEmail() == null ? "" : request.coachEmail().trim();
        if (coachEmail.isEmpty()) {
            cohort.setCoach(null);
            return;
        }
        AppUser coach = userByEmail(coachEmail);
        if (coach.getRole() == UserRole.USER) {
            coach.setRole(UserRole.COACH);
            appUserRepository.save(coach);
            securityEvents.record(SecurityEventType.ROLE_CHANGED, SecurityOutcome.SUCCESS, "user", coach.getId(), "USER -> COACH");
        }
        cohort.setCoach(coach);
    }

    private AppUser userByEmail(String email) {
        return appUserRepository.findByEmail(email.trim().toLowerCase())
                .orElseThrow(() -> new BusinessRuleException("No account uses " + email.trim() + ". They need to register first."));
    }

    private Cohort cohort(Long id) {
        return cohortRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Cohort not found: " + id));
    }

    private String newCode() {
        String code;
        do {
            StringBuilder builder = new StringBuilder(CODE_LENGTH);
            for (int index = 0; index < CODE_LENGTH; index++) {
                builder.append(CODE_ALPHABET.charAt(RANDOM.nextInt(CODE_ALPHABET.length())));
            }
            code = builder.toString();
        } while (cohortRepository.existsByJoinCodeIgnoreCase(code));
        return code;
    }

    private CohortResponse toResponse(Cohort cohort) {
        AppUser coach = cohort.getCoach();
        return new CohortResponse(cohort.getId(), cohort.getName(), cohort.getJoinCode(),
                coach == null ? null : coach.getId(), coach == null ? null : coach.getFullName(), coach == null ? null : coach.getEmail(),
                cohort.getStartDate(), cohort.isArchived(), appUserRepository.countByCohort_Id(cohort.getId()), cohort.isAiEnabled());
    }

    private void requireAdmin() {
        Roles.requireAdmin(lookup.currentUser(), "Only an administrator can manage cohorts.");
    }
}
