package com.visionmapping.entity.enums;

/**
 * FR-18.5 / FR-39: text size. The frontend applies this by scaling the root
 * font size, which every rem-based value builds on, so proportions hold —
 * unlike browser zoom. {@link #MEDIUM} is the browser default.
 */
public enum FontSize {
    SMALL,
    MEDIUM,
    LARGE
}
