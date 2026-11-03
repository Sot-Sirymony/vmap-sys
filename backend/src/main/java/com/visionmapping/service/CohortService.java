package com.visionmapping.service;

import com.visionmapping.dto.request.JoinCohortRequest;
import com.visionmapping.dto.request.JournalSharingRequest;
import com.visionmapping.dto.response.MyCohortResponse;
import com.visionmapping.entity.AppUser;
import com.visionmapping.entity.Cohort;
import com.visionmapping.exception.BusinessRuleException;
import com.visionmapping.repository.AppUserRepository;
import com.visionmapping.repository.CohortRepository;
import com.visionmapping.service.support.EntityLookup;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** V9 (D-7, D-8): a participant's own cohort, joining with a code, and journal sharing. */
@Service
@Transactional
@RequiredArgsConstructor
public class CohortService {

    private final EntityLookup lookup;
    private final CohortRepository cohortRepository;
    private final AppUserRepository appUserRepository;

    @Transactional(readOnly = true)
    public MyCohortResponse mine() {
        return toResponse(lookup.currentUser());
    }

    public MyCohortResponse join(JoinCohortRequest request) {
        AppUser user = lookup.currentUser();
        user.setCohort(cohortByCode(request.joinCode()));
        return toResponse(appUserRepository.save(user));
    }

    public MyCohortResponse leave() {
        AppUser user = lookup.currentUser();
        user.setCohort(null);
        return toResponse(appUserRepository.save(user));
    }

    public MyCohortResponse updateSharing(JournalSharingRequest request) {
        AppUser user = lookup.currentUser();
        user.setShareJournalsWithCoach(request.shareJournalsWithCoach());
        return toResponse(appUserRepository.save(user));
    }

    /** Also used at registration, so a wrong code is refused before an account exists. */
    public Cohort cohortByCode(String joinCode) {
        return cohortRepository.findByJoinCodeIgnoreCaseAndArchivedFalse(joinCode.trim())
                .orElseThrow(() -> new BusinessRuleException("That join code was not found. Check it with your coach or administrator."));
    }

    private static MyCohortResponse toResponse(AppUser user) {
        Cohort cohort = user.getCohort();
        if (cohort == null) {
            return new MyCohortResponse(null, null, null, null, user.isShareJournalsWithCoach());
        }
        return new MyCohortResponse(cohort.getId(), cohort.getName(),
                cohort.getCoach() == null ? null : cohort.getCoach().getFullName(),
                cohort.getStartDate(), user.isShareJournalsWithCoach());
    }
}
