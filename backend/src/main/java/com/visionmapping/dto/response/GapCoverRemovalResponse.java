package com.visionmapping.dto.response;

/**
 * What happened to the record a gap's cover created, when the cover or the gap
 * was removed. linkedItemRemoved is false when the user chose to keep it, when
 * there was none, or when it could not be removed; note then says why.
 */
public record GapCoverRemovalResponse(boolean linkedItemRemoved, String note) {
}
