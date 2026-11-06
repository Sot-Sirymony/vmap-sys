package com.visionmapping.service.ai;

import com.visionmapping.security.Roles;
import com.visionmapping.dto.response.AiSafetyAlertResponse;
import com.visionmapping.entity.AiSafetyAlert;
import com.visionmapping.entity.AppUser;
import com.visionmapping.entity.Cohort;
import com.visionmapping.exception.ResourceNotFoundException;
import com.visionmapping.repository.AiSafetyAlertRepository;
import com.visionmapping.repository.AppUserRepository;
import com.visionmapping.service.support.EntityLookup;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * AI-NFR-09 (D-5): crisis alerts. The participant's coach sees theirs; an
 * administrator sees those for participants with no coach, so no alert is
 * left unseen. An alert says who and when, never what was written.
 */
@Slf4j
@Service
@Transactional
@RequiredArgsConstructor
public class AiSafetyAlertService {

    private final EntityLookup lookup;
    private final AiSafetyAlertRepository repository;
    private final AppUserRepository appUserRepository;
    private final Clock clock;

    /** Returns true when a coach was alerted, false when it went to the administrators. */
    public boolean raise(Long participantId) {
        AppUser participant = appUserRepository.findById(participantId).orElseThrow();
        AppUser coach = coachOf(participant);
        repository.save(AiSafetyAlert.builder().user(participant).coach(coach).alertType("CRISIS").build());
        log.warn("AI safety alert raised for user {} ({})", participant.getId(), coach == null ? "no coach" : "coach notified");
        return coach != null;
    }

    public static AppUser coachOf(AppUser participant) {
        Cohort cohort = participant.getCohort();
        return cohort == null || cohort.isArchived() ? null : cohort.getCoach();
    }

    @Transactional(readOnly = true)
    public List<AiSafetyAlertResponse> mine() {
        AppUser user = requireCoach();
        List<AiSafetyAlert> alerts = new ArrayList<>(repository.findByCoach_IdOrderByCreatedAtDesc(user.getId()));
        if (Roles.isAdmin(user)) {
            alerts.addAll(repository.findByCoachIsNullOrderByCreatedAtDesc());
        }
        return alerts.stream()
                .sorted(Comparator.comparing(AiSafetyAlert::getCreatedAt).reversed())
                .map(AiSafetyAlertService::toResponse)
                .toList();
    }

    public AiSafetyAlertResponse acknowledge(Long id) {
        AppUser user = requireCoach();
        AiSafetyAlert alert = repository.findById(id)
                .filter(found -> found.getCoach() == null
                        ? Roles.isAdmin(user)
                        : found.getCoach().getId().equals(user.getId()))
                .orElseThrow(() -> new ResourceNotFoundException("Alert not found: " + id));
        if (alert.getAcknowledgedAt() == null) {
            alert.setAcknowledgedAt(Instant.now(clock));
            alert.setAcknowledgedBy(user);
        }
        return toResponse(alert);
    }

    private static AiSafetyAlertResponse toResponse(AiSafetyAlert alert) {
        AppUser participant = alert.getUser();
        Cohort cohort = participant.getCohort();
        return new AiSafetyAlertResponse(alert.getId(), participant.getId(), participant.getFullName(),
                cohort == null ? null : cohort.getName(), alert.getCreatedAt(), alert.getAcknowledgedAt());
    }

    private AppUser requireCoach() {
        return Roles.requireCoach(lookup.currentUser(), "Only a coach can see safety alerts.");
    }
}
