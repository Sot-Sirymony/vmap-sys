package com.visionmapping.dto.response;

import com.visionmapping.util.Copies;
import java.util.List;

/** V34 (D-34-10): the answers saved with one weekly review. */
public record InnerBlockerCheckinsResponse(List<InnerBlockerCheckinResponse> checkins) {
    public InnerBlockerCheckinsResponse {
        checkins = Copies.list(checkins);
    }
}
