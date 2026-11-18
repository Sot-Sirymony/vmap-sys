package com.visionmapping.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.visionmapping.entity.SavedReport;
import com.visionmapping.service.report.ManagementReport;
import com.visionmapping.service.report.ManagementReport.PeriodFigures;
import com.visionmapping.service.report.ReportFilters;
import com.visionmapping.service.report.ReportPeriod;
import com.visionmapping.service.report.ReportPeriod.Kind;
import com.visionmapping.service.report.ReportText;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

/** V19 RM-FR-16, RM-FR-21, RM-FR-38: default names, the next period, and safe file names. */
class SavedReportRulesTest {

    // A Wednesday in November.
    private static final LocalDate TODAY = LocalDate.of(2026, 11, 18);

    @Test
    void theNextPeriodHasTheSameLengthAndAPeriodStillRunningBecomesThisWeekMonthOrQuarter() {
        assertThat(SavedReportService.nextPeriod(new ReportPeriod(Kind.LAST_MONTH, LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30)), TODAY))
                .isEqualTo(new ReportPeriod(Kind.CUSTOM, LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31)));
        assertThat(SavedReportService.nextPeriod(new ReportPeriod(Kind.THIS_MONTH, LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 20)), TODAY))
                .isEqualTo(new ReportPeriod(Kind.THIS_MONTH, LocalDate.of(2026, 11, 1), TODAY));
        assertThat(SavedReportService.nextPeriod(new ReportPeriod(Kind.LAST_WEEK, LocalDate.of(2026, 11, 2), LocalDate.of(2026, 11, 8)), TODAY))
                .isEqualTo(new ReportPeriod(Kind.CUSTOM, LocalDate.of(2026, 11, 9), LocalDate.of(2026, 11, 15)));
        assertThat(SavedReportService.nextPeriod(new ReportPeriod(Kind.THIS_WEEK, LocalDate.of(2026, 11, 9), LocalDate.of(2026, 11, 12)), TODAY))
                .isEqualTo(new ReportPeriod(Kind.THIS_WEEK, LocalDate.of(2026, 11, 16), TODAY));
        assertThat(SavedReportService.nextPeriod(new ReportPeriod(Kind.LAST_QUARTER, LocalDate.of(2026, 4, 1), LocalDate.of(2026, 6, 30)), TODAY))
                .isEqualTo(new ReportPeriod(Kind.CUSTOM, LocalDate.of(2026, 7, 1), LocalDate.of(2026, 9, 30)));
        assertThat(SavedReportService.nextPeriod(new ReportPeriod(Kind.THIS_QUARTER, LocalDate.of(2026, 7, 1), LocalDate.of(2026, 9, 1)), TODAY))
                .isEqualTo(new ReportPeriod(Kind.THIS_QUARTER, LocalDate.of(2026, 10, 1), TODAY));
        // Custom: as many days again, ending today at the latest, or this month when it would start in the future.
        assertThat(SavedReportService.nextPeriod(new ReportPeriod(Kind.CUSTOM, LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 10)), TODAY))
                .isEqualTo(new ReportPeriod(Kind.CUSTOM, LocalDate.of(2026, 10, 11), LocalDate.of(2026, 10, 20)));
        assertThat(SavedReportService.nextPeriod(new ReportPeriod(Kind.CUSTOM, LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 10)), TODAY))
                .isEqualTo(new ReportPeriod(Kind.CUSTOM, LocalDate.of(2026, 11, 11), TODAY));
        assertThat(SavedReportService.nextPeriod(new ReportPeriod(Kind.CUSTOM, LocalDate.of(2026, 11, 1), TODAY), TODAY).kind())
                .isEqualTo(Kind.THIS_MONTH);
    }

    @Test
    void theDefaultNameIsTheScopeAndThePeriod() {
        assertThat(SavedReportService.defaultName(report("DREAM", "Publish a study"), ReportText.of("en"))).isEqualTo("Publish a study – This month");
        assertThat(SavedReportService.defaultName(report("ALL", null), ReportText.of("en"))).isEqualTo("All dreams – This month");
        assertThat(SavedReportService.defaultName(report("DREAM", "x".repeat(200)), ReportText.of("en"))).hasSize(120).endsWith("…");
    }

    @Test
    void fileNamesKeepOnlySafeCharactersAndFallBackToTheScope() {
        ManagementReport report = report("DREAM", "Publish a study");
        assertThat(SavedReportService.fileName(saved("November report: Q4 / draft!"), report, "xlsx"))
                .isEqualTo("November_report_Q4_draft_2026-11-18.xlsx");
        assertThat(SavedReportService.fileName(saved("របាយការណ៍"), report, "pptx")).isEqualTo("Management_Report_Dream_1_2026-11-18.pptx");
        assertThat(SavedReportService.fileName(saved("a".repeat(100)), report, "xlsx")).isEqualTo("a".repeat(80) + "_2026-11-18.xlsx");
    }

    private static SavedReport saved(String name) {
        return SavedReport.builder().name(name).build();
    }

    private static ManagementReport report(String kind, String label) {
        return new ManagementReport(kind, null, "DREAM".equals(kind) ? 1L : null, label, TODAY,
                ReportPeriod.resolve(null, null, null, TODAY), List.of(), List.of(), PeriodFigures.NONE, ReportFilters.NONE, List.of());
    }
}
