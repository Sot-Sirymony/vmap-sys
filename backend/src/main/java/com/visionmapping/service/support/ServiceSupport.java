package com.visionmapping.service.support;

import com.visionmapping.exception.BusinessRuleException;
import com.visionmapping.repository.UserScopedRepository;
import java.util.List;
import java.util.function.Function;

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
    public static <T> List<T> findAllForUser(UserScopedRepository<T> repository, Long userId, boolean includeArchived) {
        return includeArchived
                ? repository.findByUser_Id(userId)
                : repository.findByUser_IdAndArchivedFalse(userId);
    }

    /**
     * Codes look like "G-007". The next number must come from the highest
     * existing suffix, not the row count: a permanent delete shrinks the count
     * while higher codes remain, so a count-based code collides with the
     * (user_id, code) unique constraint.
     */
    public static <T> String nextCode(String prefix, List<T> existing, Function<T, String> codeOf) {
        int max = 0;
        for (T item : existing) {
            max = Math.max(max, codeSuffix(codeOf.apply(item)));
        }
        return "%s-%03d".formatted(prefix, max + 1);
    }

    /**
     * Numeric suffix of a "G-007"-style code, or 0 when absent. Imported codes
     * may have non-numeric suffixes; those can never collide with the generated
     * numeric format, so they count as 0.
     */
    private static int codeSuffix(String code) {
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

    public static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    /** Null (not an empty string) means "no search", which the query treats as "match everything". */
    public static String likeTerm(String search) {
        if (search == null || search.isBlank()) {
            return null;
        }
        return "%" + search.trim().toLowerCase() + "%";
    }

    public static void requireArchived(boolean archived, String label) {
        if (!archived) {
            throw new BusinessRuleException(label + " must be archived before it can be permanently deleted.");
        }
    }

    public static <E extends Enum<E>> E parseEnum(Class<E> enumType, String status) {
        try {
            return Enum.valueOf(enumType, status.trim().toUpperCase().replace('-', '_').replace(' ', '_'));
        } catch (IllegalArgumentException exception) {
            throw new BusinessRuleException("Invalid status: " + status);
        }
    }
}
