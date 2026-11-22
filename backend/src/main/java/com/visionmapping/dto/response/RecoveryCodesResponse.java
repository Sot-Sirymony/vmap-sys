package com.visionmapping.dto.response;

import com.visionmapping.util.Copies;
import java.util.List;

/** V21 (TF-FR-04, TF-FR-07): the new recovery codes, shown once and never again. */
public record RecoveryCodesResponse(List<String> recoveryCodes) {

    public RecoveryCodesResponse {
        recoveryCodes = Copies.list(recoveryCodes);
    }
}
