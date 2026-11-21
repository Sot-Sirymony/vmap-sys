package com.visionmapping.service.notice;

import com.visionmapping.entity.AppUser;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Clock;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * V20 (SV-FR-28 to SV-FR-31): records that a protected change happened. The
 * email goes out only after the change is saved ({@link AccountNoticeMailer}),
 * so a change that fails or is rolled back sends nothing.
 */
@Component
@RequiredArgsConstructor
public class AccountNotices {

    private final ApplicationEventPublisher events;
    private final Clock clock;

    public void changed(AccountNotice.Kind kind, AppUser user) {
        changed(kind, user, null, null);
    }

    /** extraRecipient: the old reminder address (SV-FR-29); detail: the new address, masked. */
    public void changed(AccountNotice.Kind kind, AppUser user, @Nullable String extraRecipient, @Nullable String detail) {
        Set<String> recipients = new LinkedHashSet<>();
        recipients.add(user.getEmail());
        if (extraRecipient != null && !extraRecipient.isBlank() && !extraRecipient.equalsIgnoreCase(user.getEmail())) {
            recipients.add(extraRecipient.trim());
        }
        events.publishEvent(new AccountNotice(kind, List.copyOf(recipients), user.getFullName(), clock.instant(),
                user.getReminderTimeZone() == null ? "UTC" : user.getReminderTimeZone(), browser(), detail));
    }

    /** "Chrome on Windows", from the request's User-Agent, or "an unknown browser". */
    static String browser() {
        HttpServletRequest request = RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes
                ? attributes.getRequest() : null;
        return describe(request == null ? null : request.getHeader("User-Agent"));
    }

    static String describe(@Nullable String userAgent) {
        if (userAgent == null || userAgent.isBlank()) {
            return "an unknown browser";
        }
        String agent = userAgent.toLowerCase(Locale.ROOT);
        String name;
        if (agent.contains("edg/")) {
            name = "Edge";
        } else if (agent.contains("firefox/")) {
            name = "Firefox";
        } else if (agent.contains("chrome/") || agent.contains("crios/")) {
            name = "Chrome";
        } else if (agent.contains("safari/")) {
            name = "Safari";
        } else {
            name = "a browser";
        }
        String system;
        if (agent.contains("android")) {
            system = "Android";
        } else if (agent.contains("iphone") || agent.contains("ipad")) {
            system = "iOS";
        } else if (agent.contains("windows")) {
            system = "Windows";
        } else if (agent.contains("mac os")) {
            system = "macOS";
        } else if (agent.contains("linux")) {
            system = "Linux";
        } else {
            return name;
        }
        return name + " on " + system;
    }
}
