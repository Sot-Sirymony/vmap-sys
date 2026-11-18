package com.visionmapping.dto.response;

/**
 * V19 RM-FR-28: suggested text for the commentary sections that are still
 * empty; a section that already has text, or has nothing to suggest, is null.
 */
public record CommentaryDraftResponse(String highlights, String risks, String decisionsNeeded, String nextFocus) {
}
