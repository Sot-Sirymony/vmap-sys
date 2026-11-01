package com.visionmapping.dto.request;

import jakarta.validation.constraints.NotEmpty;
import java.util.List;

public record ModuleOrderRequest(@NotEmpty List<Integer> orderedNumbers) {
}
