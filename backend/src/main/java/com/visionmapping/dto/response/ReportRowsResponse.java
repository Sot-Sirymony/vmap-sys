package com.visionmapping.dto.response;

import com.visionmapping.util.Copies;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * V19 RM-FR-13 to RM-FR-15: the Details tab, the same rows as the Excel
 * Report sheet. Priority is the enum name (or null); issues are in the
 * requested language.
 */
public record ReportRowsResponse(List<Row> rows) {

    public ReportRowsResponse {
        rows = Copies.list(rows);
    }

    public record Row(Long goalId, Long stepId, Long taskId, String dream, String goal, String step, String task, List<String> issues,
                      BigDecimal progress, String priority, LocalDate due, String nextAction, boolean completed, boolean blocked,
                      boolean overdue, boolean dueSoon) {

        public Row {
            issues = Copies.list(issues);
        }
    }
}
