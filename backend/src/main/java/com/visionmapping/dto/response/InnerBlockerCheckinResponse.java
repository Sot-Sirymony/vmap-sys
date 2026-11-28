package com.visionmapping.dto.response;

import com.visionmapping.entity.enums.InnerBlockerAnswer;

/** V34 (D-34-10): one weekly answer, for the review it belongs to. */
public record InnerBlockerCheckinResponse(Long innerBlockerId, Long reviewId, InnerBlockerAnswer answer) {
}
