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

    public enum Kind {
        PASSWORD_CHANGED, DATA_EXPORTED, REMINDER_EMAIL_CHANGED, ACCOUNT_DELETED, RECOVERY_CODE_USED, TWO_FACTOR_CHANGED,
        // V23 (D-23-8): changes a program admin made; detail is the new role's name for ROLE_CHANGED.
        ROLE_CHANGED, ACCOUNT_BLOCKED, ACCOUNT_UNBLOCKED,
        // V23 (UM-FR-13): what the person's role allows was changed; detail is the role's name.
        ROLE_UPDATED,
        // V23 (UM-FR-37): a program admin permanently deleted the account.
        ACCOUNT_DELETED_BY_ADMIN;

        /** Made by a program admin, not the person: the notice names no browser and no reason (Q-04). */
        public boolean byAdmin() {
            return this == ROLE_CHANGED || this == ACCOUNT_BLOCKED || this == ACCOUNT_UNBLOCKED || this == ROLE_UPDATED
                    || this == ACCOUNT_DELETED_BY_ADMIN;
        }
    }

    public AccountNotice {
        recipients = Copies.list(recipients);
    }
}
