package com.visionmapping.dto.response;

import com.visionmapping.util.Copies;
import java.util.Map;

/**
 * V23 (UM-FR-17, UM-FR-18): a page of the Users list, with the counts shown
 * above it (for all accounts, not only this page).
 *
 * @param users        V33 (D-33-9): the page of accounts, in the shared page shape
 * @param countsByRole accounts per role key
 */
public record AdminUserPageResponse(
        PagedResponse<AdminUserSummaryResponse> users,
        long totalAccounts,
        long activeAccounts,
        long blockedAccounts,
        Map<String, Long> countsByRole
) {

    public AdminUserPageResponse {
        countsByRole = Copies.map(countsByRole);
    }
}
