package com.visionmapping.dto.request;

import com.visionmapping.entity.enums.GapCoverMethod;
import com.visionmapping.entity.enums.PartnerSupportType;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

/**
 * FR-82: the one way a high-relevance weakness is covered. What else is needed
 * depends on the method: PARTNER needs partnerName; DELEGATE needs note (who or
 * what carries it); RESOURCE needs note (the source); LEARN needs goalId and
 * targetDate; ACCEPT needs note (why it does not matter enough).
 */
public record GapCoverRequest(
        @NotNull GapCoverMethod method,
        @Size(max = 1000) String note,
        @Size(max = 180) String partnerName,
        PartnerSupportType partnerSupportType,
        Long goalId,
        LocalDate targetDate
) {
}
