package com.visionmapping.dto.response;

import com.visionmapping.util.Copies;
import java.util.List;

/** V10 (SEC-09-05): a page of the security log, newest first, and the latest alerts. */
public record SecurityLogResponse(
        List<SecurityEventResponse> events,
        int page,
        int size,
        long totalEvents,
        boolean hasMore,
        List<String> recentAlerts
) {
    public SecurityLogResponse {
        events = Copies.list(events);
        recentAlerts = Copies.list(recentAlerts);
    }
}
