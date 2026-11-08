package com.visionmapping.dto.request;

import com.visionmapping.util.Copies;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.NotEmpty;
import java.util.List;

public record ModuleOrderRequest(@NotEmpty @Size(max = 100) List<Integer> orderedNumbers) {
    public ModuleOrderRequest {
        orderedNumbers = Copies.list(orderedNumbers);
    }
}
