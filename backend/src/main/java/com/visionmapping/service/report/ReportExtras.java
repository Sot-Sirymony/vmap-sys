package com.visionmapping.service.report;

import com.visionmapping.dto.response.SavedReportResponse.Commentary;
import com.visionmapping.util.Copies;
import java.util.Map;
import java.util.Set;
import org.springframework.lang.Nullable;

/**
 * V19 phase 3: what a file adds to the report model and Summary. The
 * participant's commentary (RM-FR-31, null for the live report), the sections
 * to include (RM-FR-39) and whether it comes from a draft, which marks it
 * "DRAFT" (RM-FR-43). The comparison travels in the Summary.
 */
public record ReportExtras(@Nullable Commentary commentary, Set<ReportSection> sections, boolean draft) {

    /** The live report: no commentary, every section, no draft mark (the V18 shape). */
    public static final ReportExtras NONE = new ReportExtras(null, ReportSection.all(), false);

    public ReportExtras {
        sections = Copies.set(sections);
    }

    /** Null or empty sections mean every section. */
    public boolean includes(ReportSection section) {
        return sections == null || sections.isEmpty() || sections.contains(section);
    }

    /** Highlights, or "" without commentary. */
    public String highlights() {
        return commentary == null ? "" : text(commentary.highlights());
    }

    public String risks() {
        return commentary == null ? "" : text(commentary.risks());
    }

    public String decisionsNeeded() {
        return commentary == null ? "" : text(commentary.decisionsNeeded());
    }

    public boolean nothingNeeded() {
        return commentary != null && commentary.nothingNeeded();
    }

    public String nextFocus() {
        return commentary == null ? "" : text(commentary.nextFocus());
    }

    public Map<Long, String> goalNotes() {
        return commentary == null || commentary.goalNotes() == null ? Map.of() : commentary.goalNotes();
    }

    /** True when any section or goal note has text, or "Nothing needed" is ticked. */
    public boolean hasCommentary() {
        return !highlights().isEmpty() || !risks().isEmpty() || !decisionsNeeded().isEmpty() || nothingNeeded()
                || !nextFocus().isEmpty() || !goalNotes().isEmpty();
    }

    private static String text(@Nullable String value) {
        return value == null ? "" : value.strip();
    }
}
