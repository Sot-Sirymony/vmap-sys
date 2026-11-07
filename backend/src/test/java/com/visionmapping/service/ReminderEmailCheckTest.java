package com.visionmapping.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ReminderEmailCheckTest {

    @Test
    void acceptsOrdinaryAddresses() {
        assertThat(ReminderService.looksLikeEmail("inbox@example.org")).isTrue();
        assertThat(ReminderService.looksLikeEmail("first.last+plans@mail.example.co.uk")).isTrue();
    }

    @Test
    void rejectsAddressesThatDoNotLookRight() {
        assertThat(ReminderService.looksLikeEmail("not-an-email")).isFalse();
        assertThat(ReminderService.looksLikeEmail("@example.org")).isFalse();
        assertThat(ReminderService.looksLikeEmail("a@b@example.org")).isFalse();
        assertThat(ReminderService.looksLikeEmail("inbox@localhost")).isFalse();
        assertThat(ReminderService.looksLikeEmail("inbox@.example.org")).isFalse();
        assertThat(ReminderService.looksLikeEmail("inbox@example..org")).isFalse();
        assertThat(ReminderService.looksLikeEmail("inbox@example.org.")).isFalse();
        assertThat(ReminderService.looksLikeEmail("in box@example.org")).isFalse();
    }

    @Test
    void staysQuickOnVeryLongInput() {
        String longDomain = "a.".repeat(200_000) + "org";
        assertThat(ReminderService.looksLikeEmail("inbox@" + longDomain)).isTrue();
        assertThat(ReminderService.looksLikeEmail("inbox@" + longDomain + "..")).isFalse();
    }
}
