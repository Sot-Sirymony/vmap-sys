package com.visionmapping.mapper;

import java.util.Objects;
import org.springframework.lang.Nullable;

/**
 * FR-51: whether a fixed (top-down) target date is now earlier than a child's
 * date, and why. Informational only; it never blocks a save.
 *
 * <p>A small value type instead of a {@code boolean} plus a {@code String}
 * passed side by side (BRD 13 EJ-FR-14, Effective Java Item 51). The two
 * always agree: there is a detail exactly when there is an overrun.
 */
public record ScheduleOverrun(boolean overrun, @Nullable String detail) {

    public static final ScheduleOverrun NONE = new ScheduleOverrun(false, null);

    public ScheduleOverrun {
        if (overrun != (detail != null)) {
            throw new IllegalArgumentException("An overrun needs a detail, and only an overrun has one: " + detail);
        }
    }

    public static ScheduleOverrun because(String detail) {
        if (Objects.requireNonNull(detail, "detail").isBlank()) {
            throw new IllegalArgumentException("An overrun needs a detail.");
        }
        return new ScheduleOverrun(true, detail);
    }
}
