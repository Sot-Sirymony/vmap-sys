package com.visionmapping.entity.enums;

/**
 * V17 BL-FR-01: where an obstacle sits in the plan. A plan blocker belongs on
 * a step or a task; NONE is an obstacle from before V17, an exempt partner
 * conflict (BL-FR-04), or one created by a source that is still being moved
 * over to the rule.
 */
public enum ObstaclePlacement {
    TASK,
    STEP,
    NONE
}
