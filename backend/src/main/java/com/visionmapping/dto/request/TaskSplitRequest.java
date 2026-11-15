package com.visionmapping.dto.request;

import com.visionmapping.entity.enums.Priority;
import com.visionmapping.entity.enums.TaskSplitOutcome;
import com.visionmapping.util.Copies;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.List;

/**
 * V17 BL-FR-25: breaks one task into 2 to 5 smaller tasks under the same
 * step. Each row's owner and priority default to the original task's.
 * {@code originalOutcome} keeps the original as a checklist parent or replaces
 * it (archived, never deleted).
 */
public record TaskSplitRequest(
        @NotNull @Size(min = 2, max = 5) List<@Valid @NotNull Row> rows,
        @NotNull TaskSplitOutcome originalOutcome
) {

    public TaskSplitRequest {
        rows = Copies.list(rows);
    }

    public record Row(
            @NotBlank @Size(max = 220) String title,
            @NotNull LocalDate dueDate,
            @Size(max = 160) String owner,
            Priority priority
    ) {
    }
}
