package com.visionmapping.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.visionmapping.service.ManagementReportService.ReportFile;
import org.junit.jupiter.api.Test;

/** Sonar java:S6218: a downloaded file compares by its contents, and never prints its bytes. */
class ReportFileTest {

    @Test
    void comparesByNameAndContentsAndPrintsOnlyTheSize() {
        ReportFile file = new ReportFile("Management_Report_All_dreams_2026-11-16.xlsx", new byte[] {1, 2, 3});
        ReportFile same = new ReportFile("Management_Report_All_dreams_2026-11-16.xlsx", new byte[] {1, 2, 3});

        assertThat(file).isEqualTo(same).hasSameHashCodeAs(same);
        assertThat(file).isNotEqualTo(new ReportFile("Management_Report_All_dreams_2026-11-16.xlsx", new byte[] {1, 2, 4}));
        assertThat(file).isNotEqualTo(new ReportFile("Other.xlsx", new byte[] {1, 2, 3}));
        assertThat(file).isNotEqualTo("not a file");
        assertThat(file.toString()).isEqualTo("ReportFile[fileName=Management_Report_All_dreams_2026-11-16.xlsx, size=3]");
    }
}
