package com.visionmapping.service.support;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class ServiceSupportTest {

    @Test
    void nextCodeStartsAtOneWhenNoRecordsExist() {
        assertThat(ServiceSupport.nextCode("G", List.of())).isEqualTo("G-001");
    }

    @Test
    void nextCodeIncrementsHighestExistingSuffix() {
        assertThat(ServiceSupport.nextCode("G", List.of("G-001", "G-002", "G-003")))
                .isEqualTo("G-004");
    }

    /**
     * Regression for the production 500: after permanently deleting G-001 the
     * row count is 2, so a count-based code would regenerate the existing
     * G-003 and violate the (user_id, code) unique constraint.
     */
    @Test
    void nextCodeSkipsGapsLeftByPermanentDeletes() {
        assertThat(ServiceSupport.nextCode("G", List.of("G-002", "G-003")))
                .isEqualTo("G-004");
    }

    @Test
    void nextCodeIgnoresNullAndNonNumericCodes() {
        assertThat(ServiceSupport.nextCode("G", java.util.Arrays.asList(null, "imported", "G-ABC", "G-005")))
                .isEqualTo("G-006");
    }
}
