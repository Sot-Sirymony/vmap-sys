package com.visionmapping.service.support;

import com.visionmapping.entity.enums.EnumLabels;
import com.visionmapping.exception.BusinessRuleException;
import com.visionmapping.repository.UserScopedRepository;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import org.springframework.lang.Nullable;

/**
 * Stateless helpers every entity service shares: generating the next display
 * code, listing a user's records, guarding a permanent delete, and parsing a
 * status string into its enum. Static because they hold no state and depend
 * on nothing injectable.
 */
public final class ServiceSupport {

    private ServiceSupport() {
    }

    /** The rows every "list X" endpoint returns: the user's records, hiding archived ones unless asked. */
    public static <T> List<T> findAllForUser(UserScopedRepository<T> repository, Long userId, ArchiveFilter archiveFilter) {
        Objects.requireNonNull(repository, "repository");
        Objects.requireNonNull(userId, "userId");
        Objects.requireNonNull(archiveFilter, "archiveFilter");
        return archiveFilter.includesArchived()
                ? repository.findByUser_Id(userId)
                : repository.findByUser_IdAndArchivedFalse(userId);
    }

    /**
     * Codes look like "G-007". The next number must come from the highest
     * existing suffix, not the row count: a permanent delete shrinks the count
     * while higher codes remain, so a count-based code collides with the
     * (user_id, code) unique constraint.
     */
    public static String nextCode(String prefix, List<String> existingCodes) {
        Objects.requireNonNull(prefix, "prefix");
        Objects.requireNonNull(existingCodes, "existingCodes");
        int max = 0;
        for (String code : existingCodes) {
            max = Math.max(max, codeSuffix(code));
        }
        return "%s-%03d".formatted(prefix, max + 1);
    }

    /**
     * Numeric suffix of a "G-007"-style code, or 0 when absent. Imported codes
     * may have non-numeric suffixes; those can never collide with the generated
     * numeric format, so they count as 0.
     */
    private static int codeSuffix(@Nullable String code) {
        if (code == null) {
            return 0;
        }
        int dash = code.lastIndexOf('-');
        if (dash < 0) {
            return 0;
        }
        try {
            return Integer.parseInt(code.substring(dash + 1));
        } catch (NumberFormatException ignored) {
            return 0;
        }
    }

    public static boolean isBlank(@Nullable String value) {
        return value == null || value.isBlank();
    }

    /**
     * The LIKE pattern for a search box, or empty when there is nothing to
     * search for (Item 55). Callers pass {@code orElse(null)} to the query,
     * which reads a null term as "match everything".
     */
    public static Optional<String> likeTerm(@Nullable String search) {
        if (search == null || search.isBlank()) {
            return Optional.empty();
        }
        return Optional.of("%" + search.trim().toLowerCase(Locale.ROOT) + "%");
    }

    /** CLAUDE.md Core Business Rule 12: only an archived record may be deleted for good. */
    public static void requireArchived(boolean archived, String label) {
        Objects.requireNonNull(label, "label");
        if (!archived) {
            throw new BusinessRuleException(label + " must be archived before it can be permanently deleted.");
        }
    }

    /** For text that is not a request body, such as list filters. Request bodies bind the enum directly. */
    public static <E extends Enum<E>> E parseEnum(Class<E> enumType, String status) {
        Objects.requireNonNull(enumType, "enumType");
        return EnumLabels.parse(enumType, status);
    }

    /**
     * Daily records use the client's local date, which can legitimately be one
     * day ahead of the server's. Anything later than that is not a real "today".
     */
    public static void requireNotFutureDay(java.time.LocalDate date, java.time.Clock clock) {
        Objects.requireNonNull(date, "date");
        Objects.requireNonNull(clock, "clock");
        if (date.isAfter(java.time.LocalDate.now(clock).plusDays(1))) {
            throw new BusinessRuleException("This date is in the future.");
        }
    }
}
