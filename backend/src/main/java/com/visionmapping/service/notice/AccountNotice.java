package com.visionmapping.service.notice;

import com.visionmapping.util.Copies;
import java.time.Instant;
import java.util.List;

/**
 * V20 (SV-FR-28 to SV-FR-31): a protected change that was made, to tell the
 * person about once it is saved. recipients are the account email and, for a
 * reminder address change, the old reminder address. detail is the new address,
 * masked, for REMINDER_EMAIL_CHANGED, else null.
 */
public record AccountNotice(Kind kind, List<String> recipients, String fullName, Instant at, String timeZone, String browser,
                            String detail) {

    public enum Kind { PASSWORD_CHANGED, DATA_EXPORTED, REMINDER_EMAIL_CHANGED, ACCOUNT_DELETED }

    public AccountNotice {
        recipients = Copies.list(recipients);
    }
}
