package com.visionmapping.entity.enums;

import com.visionmapping.exception.BusinessRuleException;
import java.util.Arrays;
import java.util.Locale;
import java.util.stream.Collectors;

/**
 * Turns a status label from a request into its enum constant. Accepts the
 * spellings people and older clients send: "In Progress", "in-progress" and
 * "IN_PROGRESS" all mean {@code IN_PROGRESS}. Used by each status enum's
 * {@code fromLabel} factory (BRD 13 EJ-FR-10, Effective Java Items 1 and 34).
 */
public final class EnumLabels {

    /** Longest part of a rejected value repeated in the error message. */
    private static final int MAX_ECHO = 40;

    private EnumLabels() {
    }

    /**
     * The matching constant.
     *
     * @throws BusinessRuleException (HTTP 400) naming the allowed values when nothing matches
     */
    public static <E extends Enum<E>> E parse(Class<E> type, String label) {
        if (label == null || label.isBlank()) {
            throw invalid(type, "");
        }
        String name = label.trim().toUpperCase(Locale.ROOT).replace('-', '_').replace(' ', '_');
        try {
            return Enum.valueOf(type, name);
        } catch (IllegalArgumentException exception) {
            throw invalid(type, label);
        }
    }

    /**
     * For JSON binding: an empty value becomes null, so the request's
     * {@code @NotNull} reports it the same way as a missing field.
     */
    static <E extends Enum<E>> E parseOrNull(Class<E> type, String label) {
        return label == null || label.isBlank() ? null : parse(type, label);
    }

    private static BusinessRuleException invalid(Class<? extends Enum<?>> type, String label) {
        String echo = label.length() > MAX_ECHO ? label.substring(0, MAX_ECHO) + "…" : label;
        String allowed = Arrays.stream(type.getEnumConstants()).map(Enum::name).collect(Collectors.joining(", "));
        return new BusinessRuleException("Invalid status: " + echo + ". Allowed values: " + allowed + ".");
    }
}
