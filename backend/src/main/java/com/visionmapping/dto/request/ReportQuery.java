package com.visionmapping.dto.request;

import com.visionmapping.entity.enums.Priority;
import com.visionmapping.service.ManagementReportService.ReportScope;
import com.visionmapping.service.report.ReportFilters;
import com.visionmapping.service.report.ReportRules.Health;
import com.visionmapping.util.Copies;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import org.springframework.format.annotation.DateTimeFormat;

/**
 * V18 MR-FR-03, V19 RM-FR-06, RM-FR-10: the Management Report's query string.
 * At most one of areaId and dreamId; a period preset or CUSTOM with from and
 * to; and the optional filters (comma-separated lists, e.g.
 * {@code priority=HIGH,CRITICAL}). includeCompleted defaults to true.
 */
public record ReportQuery(
        Long areaId,
        Long dreamId,
        @Size(max = 20) String period,
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
        @Size(max = 4) List<Priority> priority,
        @Size(max = 120) String owner,
        @Size(max = 5) List<Health> health,
        Boolean includeCompleted,
        // V19 RM-FR-32: a final saved report on the same scope to compare with.
        Long compareTo
) {

    public ReportQuery {
        priority = Copies.list(priority);
        health = Copies.list(health);
    }

    public ReportScope toScope() {
        return new ReportScope(areaId, dreamId, period, from, to, new ReportFilters(
                priority == null ? Set.of() : Set.copyOf(priority), owner,
                health == null ? Set.of() : Set.copyOf(health), includeCompleted == null || includeCompleted));
    }
}
