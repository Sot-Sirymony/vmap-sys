package com.visionmapping.dto.response;

import com.visionmapping.util.Copies;
import java.util.List;
import java.util.Map;

/**
 * V23 (UM-FR-17, UM-FR-18): a page of the Users list, with the counts shown
 * above it (for all accounts, not only this page).
 *
 * @param countsByRole accounts per role key
 */
public record AdminUserPageResponse(
        List<AdminUserSummaryResponse> items,
        int page,
        int size,
        long totalElements,
        boolean hasNext,
        long totalAccounts,
        long activeAccounts,
        long blockedAccounts,
        Map<String, Long> countsByRole
) {

    public AdminUserPageResponse {
        items = Copies.list(items);
        countsByRole = Copies.map(countsByRole);
    }
}
