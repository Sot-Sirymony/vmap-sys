package com.visionmapping.dto.request;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * V10 (SEC-01-10): every web address a user or admin can save is limited to
 * http and https on the server, so a stored link can never run script when it
 * is shown. The frontend checks again with safeHttpUrl.
 */
class LinkFieldValidationTest {

    private static ValidatorFactory factory;
    private static Validator validator;

    @BeforeAll
    static void setUp() {
        factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @AfterAll
    static void tearDown() {
        factory.close();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "javascript:alert(1)",
            "JAVASCRIPT:alert(1)",
            "data:text/html,<script>alert(1)</script>",
            "vbscript:msgbox(1)",
            "file:///etc/passwd",
            "//example.org/no-scheme",
            " https://example.org/leading-space"})
    void unsafeAddressesAreRefused(String address) {
        assertThat(validator.validateValue(DreamRequest.class, "imageUrl", address)).isNotEmpty();
        assertThat(validator.validateValue(AiApprovedResourceRequest.class, "url", address)).isNotEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"https://example.org/picture.png", "http://example.org/guide"})
    void webAddressesAreAccepted(String address) {
        assertThat(validator.validateValue(DreamRequest.class, "imageUrl", address)).isEmpty();
        assertThat(validator.validateValue(AiApprovedResourceRequest.class, "url", address)).isEmpty();
    }
}
