package com.visionmapping.service.support;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.function.Function;
import org.junit.jupiter.api.Test;

class ServiceSupportTest {

    private static final Function<String, String> IDENTITY = code -> code;

    @Test
    void nextCodeStartsAtOneWhenNoRecordsExist() {
        assertThat(ServiceSupport.nextCode("G", List.of(), IDENTITY)).isEqualTo("G-001");
    }

    @Test
    void nextCodeIncrementsHighestExistingSuffix() {
        assertThat(ServiceSupport.nextCode("G", List.of("G-001", "G-002", "G-003"), IDENTITY))
                .isEqualTo("G-004");
    }

    /**
     * Regression for the production 500: after permanently deleting G-001 the
     * row count is 2, so a count-based code would regenerate the existing
     * G-003 and violate the (user_id, code) unique constraint.
     */
    @Test
    void nextCodeSkipsGapsLeftByPermanentDeletes() {
        assertThat(ServiceSupport.nextCode("G", List.of("G-002", "G-003"), IDENTITY))
                .isEqualTo("G-004");
    }

    @Test
    void nextCodeIgnoresNullAndNonNumericCodes() {
        assertThat(ServiceSupport.nextCode("G", java.util.Arrays.asList(null, "imported", "G-ABC", "G-005"), IDENTITY))
                .isEqualTo("G-006");
    }
}
