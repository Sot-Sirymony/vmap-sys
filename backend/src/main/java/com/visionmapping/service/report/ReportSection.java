package com.visionmapping.service.report;

import java.util.EnumSet;
import java.util.Set;

/**
 * V19 RM-FR-39: the parts of a report file the participant can leave out. The
 * title, the executive summary (headline figures and period figures) and the
 * as-of line are always included.
 *
 * <ul>
 *   <li>COMMENTARY: the Excel Commentary sheet, the deck's Highlights slide, the participant's text on the support slide
 *       and the goal notes in the progress slide's speaker notes.</li>
 *   <li>SUMMARY: the Summary sheet's health, status and priority tables; the deck's priorities slide.</li>
 *   <li>GOAL_HEALTH: the Summary sheet's goal table; the deck's progress slide.</li>
 *   <li>DETAILS: the Excel Report sheet; the deck's overdue slide.</li>
 *   <li>BLOCKERS: the Excel Blockers sheet; the deck's blockers slide.</li>
 *   <li>CHANGES: the Excel Changes sheet and the deck's What changed slide, when compared.</li>
 *   <li>NEXT_ACTIONS: the deck's next actions slide.</li>
 *   <li>SUPPORT: the deck's decisions and support slide.</li>
 * </ul>
 */
public enum ReportSection {
    COMMENTARY, SUMMARY, GOAL_HEALTH, DETAILS, BLOCKERS, CHANGES, NEXT_ACTIONS, SUPPORT;

    /** Every section, the default. */
    public static Set<ReportSection> all() {
        return EnumSet.allOf(ReportSection.class);
    }
}
