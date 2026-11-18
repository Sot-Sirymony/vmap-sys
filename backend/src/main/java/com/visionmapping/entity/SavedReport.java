package com.visionmapping.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * V19 (BRD 19.0.0, RM-FR-16 to RM-FR-31): a saved Management Report. A Draft
 * shows the live plan for its scope, period and filters, with the
 * participant's commentary. Final freezes the figures in {@code snapshot}
 * (D-19-1); after that only the name may change.
 *
 * <p>Ids of the area, dream and compared report are plain columns, not
 * associations: a report must still open when the dream it covered is gone.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "management_reports")
public class SavedReport extends BaseAuditableEntity {

    public static final String DRAFT = "DRAFT";
    public static final String FINAL = "FINAL";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private AppUser user;

    @Column(nullable = false, length = 20)
    private String code;

    @Column(nullable = false, length = 120)
    private String name;

    /** ALL, AREA or DREAM. */
    @Column(name = "scope_kind", nullable = false, length = 10)
    private String scopeKind;

    @Column(name = "area_id")
    private Long areaId;

    @Column(name = "dream_id")
    private Long dreamId;

    /** The area's or dream's name when saved (or finalised), for the list. */
    @Column(name = "scope_label")
    private String scopeLabel;

    /** A ReportPeriod.Kind name. A draft's preset keeps moving with today; dates are set for CUSTOM and once final. */
    @Column(name = "period_kind", nullable = false, length = 20)
    private String periodKind;

    @Column(name = "period_from")
    private LocalDate periodFrom;

    @Column(name = "period_to")
    private LocalDate periodTo;

    /** RM-FR-10: the filters, as JSON (ReportFilters). */
    @Column(columnDefinition = "TEXT")
    private String filters;

    /** DRAFT or FINAL. */
    @Column(nullable = false, length = 10)
    private String status;

    @Column(nullable = false)
    private boolean archived;

    /** RM-FR-21, RM-FR-32: the earlier final report this one is compared with. */
    @Column(name = "compare_to_report_id")
    private Long compareToReportId;

    // RM-FR-26: the participant's commentary, each up to 2,000 characters.
    @Column(length = 2000)
    private String highlights;

    @Column(length = 2000)
    private String risks;

    @Column(name = "decisions_needed", length = 2000)
    private String decisionsNeeded;

    /** RM-FR-30: "Nothing needed this period". */
    @Column(name = "nothing_needed", nullable = false)
    private boolean nothingNeeded;

    @Column(name = "next_focus", length = 2000)
    private String nextFocus;

    /** RM-FR-26: a note per goal, as JSON {goalId: text}. */
    @Column(name = "goal_notes", columnDefinition = "TEXT")
    private String goalNotes;

    /** RM-FR-39 (phase 3): the sections chosen for downloads, as JSON. */
    @Column(columnDefinition = "TEXT")
    private String sections;

    /** The language a final report was frozen in, en or km. */
    @Column(nullable = false, length = 5)
    private String language;

    /** RM-FR-19: base64 of gzip-compressed JSON; null while a draft. */
    @Column(columnDefinition = "TEXT")
    private String snapshot;

    @Column(name = "snapshot_version")
    private Integer snapshotVersion;

    /** RM-NFR-04: SHA-256 (hex) of the uncompressed snapshot JSON. */
    @Column(name = "snapshot_checksum", length = 64)
    private String snapshotChecksum;

    @Column(name = "finalized_at")
    private Instant finalizedAt;

    public boolean isFinal() {
        return FINAL.equals(status);
    }
}
