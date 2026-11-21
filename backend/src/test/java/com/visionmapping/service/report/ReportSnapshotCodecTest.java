package com.visionmapping.service.report;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.visionmapping.dto.response.ManagementReportResponse;
import com.visionmapping.entity.enums.ObstacleStatus;
import com.visionmapping.entity.enums.ObstacleType;
import com.visionmapping.entity.enums.Priority;
import com.visionmapping.entity.enums.Severity;
import com.visionmapping.entity.enums.WorkStatus;
import com.visionmapping.exception.BusinessRuleException;
import com.visionmapping.service.report.ManagementReport.BlockerInfo;
import com.visionmapping.service.report.ManagementReport.DreamInfo;
import com.visionmapping.service.report.ManagementReport.GoalInfo;
import com.visionmapping.service.report.ManagementReport.GoalNode;
import com.visionmapping.service.report.ManagementReport.PeriodFigures;
import com.visionmapping.service.report.ManagementReport.StepInfo;
import com.visionmapping.service.report.ManagementReport.StepNode;
import com.visionmapping.service.report.ManagementReport.TaskInfo;
import com.visionmapping.service.report.ManagementReport.TaskNode;
import com.visionmapping.service.report.ReportSnapshotCodec.Decoded;
import com.visionmapping.service.report.ReportSnapshotCodec.Encoded;
import com.visionmapping.service.report.ReportSnapshotCodec.Snapshot;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** V19 RM-FR-19, RM-NFR-04, RMC-21: a final report's snapshot round-trips, is checked, and old versions stay readable. */
class ReportSnapshotCodecTest {

    static final LocalDate AS_OF = LocalDate.of(2026, 11, 17);
    private static final ObjectMapper MAPPER = JsonMapper.builder().findAndAddModules()
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS).build();
    private final ReportSnapshotCodec codec = new ReportSnapshotCodec(MAPPER);

    @Test
    void aSnapshotReadsBackExactlyAndItsChecksumMatches() {
        Snapshot snapshot = sample();
        Encoded encoded = codec.encode(snapshot);

        assertThat(encoded.version()).isEqualTo(ReportSnapshotCodec.CURRENT_VERSION);
        assertThat(encoded.checksum()).hasSize(64);
        Decoded decoded = codec.decode(encoded.data(), encoded.checksum(), encoded.version());
        assertThat(decoded.verified()).isTrue();
        assertThat(decoded.snapshot()).isEqualTo(snapshot);
    }

    @Test
    void aChangedChecksumIsReportedAndAnUnknownVersionRefused() {
        Encoded encoded = codec.encode(sample());

        assertThat(codec.decode(encoded.data(), "0".repeat(64), encoded.version()).verified()).isFalse();
        assertThatThrownBy(() -> codec.decode(encoded.data(), encoded.checksum(), 99))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessage(ReportSnapshotCodec.UNKNOWN_VERSION);
    }

    /**
     * RMC-21: a snapshot stored by the first release (report-snapshots/v1.txt:
     * version, checksum, data) still opens. If this fails after a model
     * change, add a version 2 and keep a reader for version 1.
     */
    @Test
    void rmc21_aVersion1SnapshotFromTheFirstReleaseStillOpens() throws IOException {
        String[] lines;
        try (InputStream in = getClass().getResourceAsStream("/report-snapshots/v1.txt")) {
            assertThat(in).as("stored fixture").isNotNull();
            lines = new String(in.readAllBytes(), StandardCharsets.UTF_8).strip().split("\n");
        }

        Decoded decoded = codec.decode(lines[2], lines[1], Integer.parseInt(lines[0]));

        assertThat(decoded.verified()).isTrue();
        assertThat(decoded.snapshot().report().goals().get(0).goal().title()).isEqualTo("Collect the data");
        assertThat(decoded.snapshot().report().goals().get(0).assessment().health()).isEqualTo(ReportRules.Health.RED);
        assertThat(decoded.snapshot().summary().headline().overallProgress()).isEqualByComparingTo("40.0");
        assertThat(decoded.snapshot().report().filters().priorities()).containsExactly(Priority.HIGH);
        // Phase 3 added the comparison; a version 1 snapshot simply has none.
        assertThat(decoded.snapshot().summary().comparison()).isNull();
    }

    /** A small report with every kind of value the model holds. */
    static Snapshot sample() {
        TaskNode task = new TaskNode(new TaskInfo(30L, "Run the survey", WorkStatus.BLOCKED, Priority.HIGH, new BigDecimal("40.00"),
                AS_OF.minusDays(2), "Dara", "Call the printer", "Printer broken", AS_OF, null),
                List.of(new BlockerInfo(40L, "No budget", ObstacleType.MONEY, Severity.CRITICAL, ObstacleStatus.OPEN, null, AS_OF, "")));
        StepNode step = new StepNode(new StepInfo(20L, "Field work", WorkStatus.IN_PROGRESS, Priority.HIGH, new BigDecimal("40.00"),
                AS_OF.plusDays(10), ObstacleType.MONEY), List.of(task), List.of(), false);
        GoalNode goal = GoalNode.assessed(new GoalInfo(10L, "G-001", "Collect the data", WorkStatus.IN_PROGRESS, Priority.HIGH,
                new BigDecimal("40.00"), AS_OF.plusDays(30), AS_OF.minusDays(60)), new DreamInfo(1L, "Publish a study"), List.of(step), AS_OF);
        ManagementReport report = new ManagementReport("DREAM", null, 1L, "Publish a study", AS_OF,
                new ReportPeriod(ReportPeriod.Kind.THIS_MONTH, AS_OF.withDayOfMonth(1), AS_OF), List.of(goal), List.of(),
                new PeriodFigures(2, new BigDecimal("12.5"), 1, 0, 1),
                new ReportFilters(Set.of(Priority.HIGH), null, Set.of(), true), List.of("Dara"));
        ManagementReportResponse summary = new ManagementReportResponse(
                new ManagementReportResponse.Scope("DREAM", null, 1L, "Publish a study"), AS_OF,
                new ManagementReportResponse.Period("THIS_MONTH", AS_OF.withDayOfMonth(1), AS_OF, false, false),
                new ManagementReportResponse.PeriodFigures(2, new BigDecimal("12.5"), 1, 0, 1),
                new ManagementReportResponse.Headline(new BigDecimal("40.0"), Map.of("RED", 1L), 1, 0, 1, 1, 1, 0),
                Map.of("BLOCKED", 1L), List.of(), Map.of("MONEY", 1L), 0, List.of(), List.of(), List.of(),
                new ManagementReportResponse.Filters(List.of("HIGH"), null, List.of(), true), List.of("Dara"), null);
        return new Snapshot(report, summary);
    }
}
