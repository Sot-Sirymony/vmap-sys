package com.visionmapping.entity.enums;

/**
 * FR-51: how a Dream's or Goal's target date relates to its children's dates.
 * BOTTOM_UP (the default) requires the parent date to be no earlier than the
 * latest child date; TOP_DOWN_FIXED opts a record out for a real external
 * deadline that cannot move (BR-40).
 */
public enum ScheduleMode {
    BOTTOM_UP,
    TOP_DOWN_FIXED
}
