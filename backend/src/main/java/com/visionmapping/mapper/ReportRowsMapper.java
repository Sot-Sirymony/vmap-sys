package com.visionmapping.mapper;

import com.visionmapping.dto.response.ReportRowsResponse;
import com.visionmapping.service.report.ReportRows;
import java.util.List;

/** V19 RM-FR-13: the Details tab's rows as the API returns them. */
public final class ReportRowsMapper {

    private ReportRowsMapper() {
    }

    public static ReportRowsResponse toResponse(List<ReportRows.Row> rows) {
        return new ReportRowsResponse(rows.stream()
                .map(row -> new ReportRowsResponse.Row(row.goalId(), row.stepId(), row.taskId(), row.dream(), row.goal(), row.step(),
                        row.task(), row.issues(), row.progress(), row.priority() == null ? null : row.priority().name(), row.due(),
                        row.nextAction(), row.completed(), row.blocked(), row.overdue(), row.dueSoon()))
                .toList());
    }
}
