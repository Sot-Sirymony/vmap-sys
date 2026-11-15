package com.visionmapping.entity.enums;

/** V17 BL-FR-25: what happens to a task once it is broken into smaller tasks. */
public enum TaskSplitOutcome {
    /** The original stays as it is, as a checklist parent. */
    KEEP,
    /** The original is archived with the note "Split into T-041 to T-043". Nothing is deleted. */
    REPLACE
}
