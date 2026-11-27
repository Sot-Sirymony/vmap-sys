package com.visionmapping.dto.response;

import com.visionmapping.util.Copies;
import java.util.List;

/** V10 (SEC-09-05): a page of the security log, newest first, and the latest alerts. */
public record SecurityLogResponse(
        /** V33 (D-33-9): the page of events, in the shared page shape. */
        PagedResponse<SecurityEventResponse> events,
        List<String> recentAlerts
) {
    public SecurityLogResponse {
        recentAlerts = Copies.list(recentAlerts);
    }
}
