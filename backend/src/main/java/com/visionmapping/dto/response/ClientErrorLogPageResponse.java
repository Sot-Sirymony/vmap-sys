package com.visionmapping.dto.response;

import com.visionmapping.util.Copies;
import java.util.List;

/** A page of the failed-request log for admins, newest first. */
public record ClientErrorLogPageResponse(
        List<ClientErrorLogResponse> entries,
        int page,
        int size,
        long totalEntries,
        boolean hasMore
) {
    public ClientErrorLogPageResponse {
        entries = Copies.list(entries);
    }
}
