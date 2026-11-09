package com.visionmapping.service;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import org.springframework.lang.Nullable;

/** V15 (AH-FR-57): page keys stored as a comma-separated list. */
public final class PageSwitches {

    private PageSwitches() {
    }

    public static String join(Collection<String> pages) {
        return String.join(",", pages.stream().filter(Objects::nonNull).map(String::trim).filter(page -> !page.isEmpty())
                .distinct().sorted().toList());
    }

    public static List<String> split(@Nullable String stored) {
        if (stored == null || stored.isBlank()) {
            return List.of();
        }
        return Arrays.stream(stored.split(",")).map(String::trim).filter(page -> !page.isEmpty()).toList();
    }
}
