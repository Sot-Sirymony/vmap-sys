package com.visionmapping.dto.response;

import com.visionmapping.util.Copies;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * V23 (UM-FR-19): the detail panel of one account. Record <b>counts</b> only:
 * no titles, text, journals or reports (D-23-3; PermissionFlow privacy test).
 *
 * @param createdBy      "self sign-up", "Google", or the name of the admin who invited them
 * @param recordCounts   vision areas, dreams, goals, steps and tasks, by name
 * @param recentEvents   the last 20 security events by or about the account
 * @param invitationPending   V23 phase 5: invited and has not chosen a password yet
 * @param invitationExpiresAt when the latest invitation link stops working; null if none works
 * @param coachedCohorts      names of active cohorts this person coaches (the delete dialog)
 */
public record AdminUserDetailResponse(
        AdminUserSummaryResponse account,
        String createdBy,
        Instant roleChangedAt,
        String roleChangedBy,
        Instant blockedAt,
        String blockedBy,
        Map<String, Long> recordCounts,
        List<SecurityEventResponse> recentEvents,
        boolean invitationPending,
        Instant invitationExpiresAt,
        List<String> coachedCohorts
) {

    public AdminUserDetailResponse {
        recordCounts = Copies.map(recordCounts);
        recentEvents = Copies.list(recentEvents);
        coachedCohorts = Copies.list(coachedCohorts);
    }
}
