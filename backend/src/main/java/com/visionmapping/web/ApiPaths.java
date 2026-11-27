package com.visionmapping.web;

import java.util.Arrays;
import java.util.stream.Stream;

/**
 * V33 (D-33-1): the API's path prefixes, in one place. Every controller is
 * mapped under {@link #V1}. {@link #HEALTH} stays unversioned, because the
 * platform's health check and the ZAP scan call it and it is not part of the
 * app's contract.
 *
 * <p>Until every controller has moved (BRD 33 phases 2 to 4) and for 30 days
 * after (D-33-2), the old {@link #LEGACY} prefix still works:
 * {@link LegacyApiPathFilter} forwards it to {@link #V1} when a version 1
 * endpoint exists.
 */
public final class ApiPaths {

    /** The prefix every endpoint had before V33. */
    public static final String LEGACY = "/api";

    /** Version 1 of the API. */
    public static final String V1 = "/api/v1";

    /** The platform health check; never versioned. */
    public static final String HEALTH = "/api/health";

    private ApiPaths() {
    }

    /**
     * The same paths under both prefixes, for path rules that must hold while
     * the old prefix still works. {@code "/auth/login"} gives
     * {@code "/api/auth/login"} and {@code "/api/v1/auth/login"}.
     */
    public static String[] underBothPrefixes(String... paths) {
        return Arrays.stream(paths)
                .flatMap(path -> Stream.of(LEGACY + path, V1 + path))
                .toArray(String[]::new);
    }
}
