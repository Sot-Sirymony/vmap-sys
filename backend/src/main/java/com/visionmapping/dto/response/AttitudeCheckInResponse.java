package com.visionmapping.dto.response;

import java.time.LocalDate;

public record AttitudeCheckInResponse(
        Long id,
        LocalDate checkInDate,
        Integer score,
        String note
) {
}
