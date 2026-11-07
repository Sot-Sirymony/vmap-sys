package com.visionmapping.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * V11 Phase 7 (PB-FR-42, PB-FR-46): which section of which page the plan
 * assistant is helping with. surface is PLAN (the Plan page) or BUILDER (Build
 * my plan). itemRef optionally names one item of that section: "goal:12" on
 * the Plan page, a draft key in Build my plan.
 */
public record PlanAssistantRequest(
        @NotNull @Pattern(regexp = SURFACES) String surface,
        @NotNull @Pattern(regexp = SECTIONS) String section,
        @Size(max = 60) String itemRef) {

    public static final String SURFACES = "PLAN|BUILDER";
    public static final String SECTIONS =
            "WHOLE|STATEMENT|GOALS|STEPS|DATES|RESOURCES|PEOPLE|MILESTONES|CLARIFY|TASKS|HELPERS|REVIEW";
}
