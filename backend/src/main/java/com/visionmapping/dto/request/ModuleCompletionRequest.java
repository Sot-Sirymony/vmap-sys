package com.visionmapping.dto.request;

import com.visionmapping.util.Copies;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.Map;

/** rows is only read for a form exercise; a data-proven module sends none. */
public record ModuleCompletionRequest(@Size(max = 50) List<Map<String, String>> rows) {
    public ModuleCompletionRequest {
        rows = Copies.list(rows);
    }
}
