package com.visionmapping.dto.response;

/** V28 BA-FR-12: what Mark as blocked saved; obstacleCreated is false when an open obstacle was reused. */
public record MarkBlockedResponse(String itemType, Long itemId, Long obstacleId, boolean obstacleCreated) {
}
