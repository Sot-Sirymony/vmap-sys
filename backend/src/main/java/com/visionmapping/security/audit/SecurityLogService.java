package com.visionmapping.security.audit;

import com.visionmapping.dto.response.SecurityEventResponse;
import com.visionmapping.dto.response.SecurityLogResponse;
import com.visionmapping.entity.SecurityEvent;
import com.visionmapping.repository.SecurityEventRepository;
import com.visionmapping.security.Roles;
import com.visionmapping.service.support.EntityLookup;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** V10 (SEC-09-05): admins read the security log. Read-only; nothing here can change it. */
@Service
@RequiredArgsConstructor
public class SecurityLogService {

    static final int MAX_PAGE_SIZE = 100;

    private final SecurityEventRepository repository;
    private final SecurityAlertService alerts;
    private final EntityLookup lookup;

    @Transactional(readOnly = true)
    public SecurityLogResponse search(SecurityEventType type, SecurityOutcome outcome, Long actorId,
                                      LocalDate from, LocalDate to, int page, int size) {
        Roles.requireAdmin(lookup.currentUser(), "Only an administrator can read the security log.");
        int pageSize = Math.max(1, Math.min(size, MAX_PAGE_SIZE));
        Instant start = from == null ? Instant.EPOCH : from.atStartOfDay(ZoneOffset.UTC).toInstant();
        Instant end = to == null ? Instant.now().plusSeconds(60) : to.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant();
        Page<SecurityEvent> found = repository.search(type, outcome, actorId, start, end,
                PageRequest.of(Math.max(0, page), pageSize));
        return new SecurityLogResponse(
                found.getContent().stream().map(SecurityLogService::toResponse).toList(),
                found.getNumber(), found.getSize(), found.getTotalElements(), found.hasNext(), alerts.recentAlerts());
    }

    private static SecurityEventResponse toResponse(SecurityEvent event) {
        return new SecurityEventResponse(event.getId(), event.getOccurredAt(), event.getEventType().name(),
                event.getOutcome().name(), event.getActorId(), event.getIpAddress(), event.getTargetType(),
                event.getTargetId(), event.getDetail());
    }
}
