package com.visionmapping.dto.request;

import org.springframework.lang.Nullable;
import jakarta.validation.constraints.Size;

/**
 * V29 (AR-FR-04, BR-29-1): an optional reason for archiving a dream, goal,
 * step or task. removePartner applies to steps only (BL-FR-21).
 */
public record ArchiveRequest(
        @Size(max = 1000) String reason,
        Boolean removePartner
) {

    /** V33 (D-33-11): the reason of an optional archive body, so controllers need no null check. */
    @Nullable
    public static String reasonOf(@Nullable ArchiveRequest request) {
        return request == null ? null : request.reason();
    }

    /** V33: whether an optional archive body asks to archive the step's partner too (BL-FR-21). */
    public static boolean removesPartner(@Nullable ArchiveRequest request) {
        return request != null && Boolean.TRUE.equals(request.removePartner());
    }
}
