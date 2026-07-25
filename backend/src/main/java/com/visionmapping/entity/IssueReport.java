package com.visionmapping.entity;

import com.visionmapping.entity.enums.IssueReportStatus;
import com.visionmapping.entity.enums.ReportType;
import com.visionmapping.entity.enums.Severity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * FR-38: an in-app bug report or improvement request. The reporter (owner)
 * authors it and tracks its status; an ADMIN triages the queue. The route and
 * app version are captured automatically on submit (FR-38.2) so a bug is
 * reproducible without the user describing where they were.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "issue_reports")
public class IssueReport extends BaseAuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 40)
    private String code;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private AppUser user;

    @Enumerated(EnumType.STRING)
    @Column(name = "report_type", nullable = false, length = 40)
    private ReportType reportType;

    @Column(nullable = false, length = 220)
    private String title;

    @Column(length = 3000)
    private String description;

    // Required only for BUG reports (BR-32); null for other types.
    @Enumerated(EnumType.STRING)
    @Column(length = 40)
    private Severity severity;

    // FR-38.2: auto-captured context — the route the user was on and the build.
    @Column(name = "context_route", length = 300)
    private String contextRoute;

    @Column(name = "app_version", length = 40)
    private String appVersion;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private IssueReportStatus status;

    // FR-38.4: set by an ADMIN when triaging; required to resolve a bug (BR-32).
    @Column(name = "resolution_note", length = 3000)
    private String resolutionNote;

    @Column(nullable = false)
    private boolean archived;
}
