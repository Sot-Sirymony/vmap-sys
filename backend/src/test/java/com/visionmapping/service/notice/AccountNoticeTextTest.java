package com.visionmapping.service.notice;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

/** V20 SV-FR-28 to SV-FR-30: what a change notice says. */
class AccountNoticeTextTest {

    private static final Instant AT = Instant.parse("2026-11-21T07:05:00Z");

    @Test
    void theNoticeSaysWhatChangedWhenInThePersonsTimeZoneAndFromWhichBrowser() {
        String body = AccountNoticeMailer.body(new AccountNotice(AccountNotice.Kind.REMINDER_EMAIL_CHANGED, List.of("me@example.com"), "Sophea",
                AT, "Asia/Phnom_Penh", "Chrome on Windows", "i****@w***.org"));
        assertThat(body).contains("Hello Sophea,", "Your Vision Map reminders now go to i****@w***.org on 21 Nov 2026 at 14:05 (Asia/Phnom_Penh)",
                "from Chrome on Windows", "If this was not you, change your password now.").doesNotContain("http");
        assertThat(AccountNoticeMailer.subject(AccountNotice.Kind.REMINDER_EMAIL_CHANGED)).isEqualTo("Your Vision Map reminder email was changed");
    }

    @Test
    void everyKindHasItsOwnWordsAndABadTimeZoneFallsBackToUtc() {
        for (AccountNotice.Kind kind : AccountNotice.Kind.values()) {
            String body = AccountNoticeMailer.body(new AccountNotice(kind, List.of("me@example.com"), "Me", AT, "Not/AZone", "Safari", null));
            assertThat(body).contains("on 21 Nov 2026 at 07:05 (Not/AZone)");
            assertThat(AccountNoticeMailer.subject(kind)).startsWith("Your Vision Map");
        }
        assertThat(AccountNoticeMailer.body(new AccountNotice(AccountNotice.Kind.ACCOUNT_DELETED, List.of("x@y.z"), "Me", AT, "UTC", "Safari", null)))
                .contains("account and all its data were deleted", "contact your coach or administrator");
    }

    @Test
    void theBrowserIsNamedPlainlyFromItsUserAgent() {
        assertThat(AccountNotices.describe(null)).isEqualTo("an unknown browser");
        assertThat(AccountNotices.describe(" ")).isEqualTo("an unknown browser");
        assertThat(AccountNotices.describe("Mozilla/5.0 (Windows NT 10.0) AppleWebKit Chrome/131.0 Safari/537.36 Edg/131.0")).isEqualTo("Edge on Windows");
        assertThat(AccountNotices.describe("Mozilla/5.0 (Windows NT 10.0; rv:133.0) Gecko/20100101 Firefox/133.0")).isEqualTo("Firefox on Windows");
        assertThat(AccountNotices.describe("Mozilla/5.0 (Linux; Android 14) Chrome/131.0 Mobile Safari/537.36")).isEqualTo("Chrome on Android");
        assertThat(AccountNotices.describe("Mozilla/5.0 (iPhone; CPU iPhone OS 18_1 like Mac OS X) Version/18.1 Mobile Safari/604.1"))
                .isEqualTo("Safari on iOS");
        assertThat(AccountNotices.describe("Mozilla/5.0 (Macintosh; Intel Mac OS X 14_7) Version/18.1 Safari/605.1.15")).isEqualTo("Safari on macOS");
        assertThat(AccountNotices.describe("Mozilla/5.0 (X11; Linux x86_64) Chrome/131.0")).isEqualTo("Chrome on Linux");
        assertThat(AccountNotices.describe("curl/8.7.1")).isEqualTo("a browser");
    }
}
