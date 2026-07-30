package com.visionmapping.entity.enums;

/**
 * FR-18.2 / FR-39: the colour mode. {@link #SYSTEM} defers to the operating
 * system's preference and tracks it live, so it is a stored choice in its own
 * right rather than a resolved light/dark value.
 */
public enum ThemeMode {
    LIGHT,
    DARK,
    SYSTEM
}
