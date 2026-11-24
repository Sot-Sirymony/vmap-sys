package com.visionmapping.service;

import com.visionmapping.dto.request.ReminderSettingsRequest;
import com.visionmapping.dto.response.DailyPlanResponse.Item;
import com.visionmapping.dto.response.ReminderSettingsResponse;
import com.visionmapping.entity.AppUser;
import com.visionmapping.entity.enums.ReportReminder;
import com.visionmapping.exception.BusinessRuleException;
import com.visionmapping.repository.AppUserRepository;
import com.visionmapping.service.mail.ReminderMailer;
import com.visionmapping.service.report.ReportReminders;
import com.visionmapping.util.UserScope;
import java.time.DateTimeException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.List;
import com.visionmapping.service.ai.AiReminderService;
import com.visionmapping.service.notice.AccountNotice;
import com.visionmapping.service.notice.AccountNotices;
import com.visionmapping.service.stepup.StepUpAction;
import com.visionmapping.service.stepup.StepUpCodes;
import com.visionmapping.service.stepup.StepUpService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * FR-77: one opt-in email a day, at most, covering due work, the weekly review
 * and a long quiet spell. A day with nothing to say sends nothing.
 */
@Service
@Transactional
@RequiredArgsConstructor
public class ReminderService {

    private static final int MAX_LISTED_ITEMS = 10;

    private final UserScope userScope;
    private final AppUserRepository appUserRepository;
    private final DailyPlanService dailyPlanService;
    private final ReminderMailer reminderMailer;
    private final ActivityService activityService;
    private final AiReminderService aiReminderService;
    private final StepUpService stepUpService;
    private final AccountNotices accountNotices;
    private final ReviewRhythmService reviewRhythms;

    @Value("${app.reminders.send-hour:8}")
    private int sendHour;

    @Value("${app.reminders.inactive-after-days:3}")
    private int inactiveAfterDays;

    @Value("${app.reminders.today-url:}")
    private String todayUrl;

    /** V19 (RM-FR-49): optional link to the Reports page in the report reminder. */
    @Value("${app.reminders.reports-url:}")
    private String reportsUrl;

    @Transactional(readOnly = true)
    public ReminderSettingsResponse settings() {
        return toResponse(userScope.currentUser());
    }

    public ReminderSettingsResponse updateSettings(ReminderSettingsRequest request) {
        return updateSettings(request, null, null);
    }

    /**
     * V20 SV-FR-04: a different reminder address needs the account check
     * (stepUp: a code to the account email, or the verified window) and, unless
     * it is being cleared, a code sent to the new address (confirm), so reminders
     * only ever go to an address the person controls (SV-FR-05). Other settings
     * need no code.
     */
    public ReminderSettingsResponse updateSettings(ReminderSettingsRequest request, @Nullable String stepUp, @Nullable String confirm) {
        AppUser user = userScope.currentUser();
        try {
            ZoneId.of(request.timeZone());
        } catch (DateTimeException exception) {
            throw new BusinessRuleException("That time zone was not recognized.");
        }
        user.setRemindersEnabled(request.enabled());
        user.setReminderTimeZone(request.timeZone());
        String reminderEmail = request.reminderEmail() == null ? "" : request.reminderEmail().trim();
        if (!reminderEmail.isEmpty() && !looksLikeEmail(reminderEmail)) {
            throw new BusinessRuleException("That reminder email address does not look right.");
        }
        String previous = user.getReminderEmail();
        boolean addressChanges = !reminderEmail.equalsIgnoreCase(previous == null ? "" : previous);
        if (addressChanges) {
            stepUpService.require(StepUpAction.CHANGE_REMINDER_EMAIL, stepUp);
            if (!reminderEmail.isEmpty()) {
                stepUpService.requireForAddress(StepUpAction.CONFIRM_REMINDER_EMAIL, confirm, reminderEmail);
            }
            // SV-FR-28, SV-FR-29: the account email and the old reminder address hear about it.
            accountNotices.changed(AccountNotice.Kind.REMINDER_EMAIL_CHANGED, user, previous,
                    reminderEmail.isEmpty() ? "your login email" : StepUpCodes.mask(reminderEmail));
        }
        user.setReminderEmail(reminderEmail.isEmpty() ? null : reminderEmail);
        if (request.reportReminder() != null) {
            ReportReminder cadence = ReportReminder.valueOf(request.reportReminder());
            if (cadence == ReportReminder.WEEKLY && request.reportReminderDay() == null) {
                throw new BusinessRuleException("Choose the weekday for your weekly report reminder.");
            }
            user.setReportReminder(cadence);
            user.setReportReminderDay(cadence == ReportReminder.WEEKLY ? request.reportReminderDay() : null);
        }
        appUserRepository.save(user);
        return toResponse(user);
    }

    /** Lets the user confirm delivery now instead of waiting for the next morning. */
    @Transactional(readOnly = true)
    public ReminderSettingsResponse sendTest() {
        AppUser user = userScope.currentUser();
        if (!reminderMailer.isConfigured()) {
            throw new BusinessRuleException("No email server is set up yet, so a test email cannot be sent.");
        }
        reminderMailer.send(sendsTo(user), "This is a test from Vision Mapping. Your daily reminders will arrive at this address.");
        return toResponse(user);
    }

    private ReminderSettingsResponse toResponse(AppUser user) {
        return new ReminderSettingsResponse(user.isRemindersEnabled(), user.getReminderTimeZone(), reminderMailer.isConfigured(),
                sendHour, user.getReminderEmail(), sendsTo(user), user.getReportReminder().name(), user.getReportReminderDay());
    }

    private static String sendsTo(AppUser user) {
        return user.getReminderEmail() != null ? user.getReminderEmail() : user.getEmail();
    }

    /** Called by the job once per user, so one user's failure stays theirs. */
    public void remindIfDue(Long userId, Instant now) {
        AppUser user = appUserRepository.findById(userId).orElse(null);
        if (user == null || (!user.isRemindersEnabled() && user.getReportReminder() == ReportReminder.NONE)
                || user.getReminderTimeZone() == null) {
            return;
        }
        ZonedDateTime local = now.atZone(ZoneId.of(user.getReminderTimeZone()));
        LocalDate today = local.toLocalDate();
        if (local.getHour() < sendHour || today.equals(user.getLastReminderOn())) {
            return;
        }
        user.setLastReminderOn(today);
        String body = composeBody(user, today, now);
        if (body != null) {
            reminderMailer.send(sendsTo(user), body);
        }
    }

    /**
     * The daily parts only when daily reminders are on; the V19 report
     * reminder (RM-FR-49) on its own day, whether or not they are.
     */
    @Nullable
    private String composeBody(AppUser user, LocalDate today, Instant now) {
        boolean daily = user.isRemindersEnabled();
        List<Item> items = daily ? dailyPlanService.itemsFor(user.getId(), today) : List.of();
        boolean reviewDue = daily && dailyPlanService.weeklyReviewDueFor(user.getId(), today);
        boolean inactive = daily && isInactive(user, now);
        boolean reportDue = today.equals(ReportReminders.lastDue(user.getReportReminder(), user.getReportReminderDay(), today));
        // V31 v1 step 3 (DC-FR-50, D-31-9): reviews a dream's rhythm makes due, only for people who turned reminders on.
        List<com.visionmapping.dto.response.AiPlanResponses.DueReview> dreamReviews = daily
                ? reviewRhythms.dueFor(user.getId(), today) : List.of();
        if (items.isEmpty() && !reviewDue && !inactive && !reportDue && dreamReviews.isEmpty()) {
            return null;
        }
        List<String> lines = new ArrayList<>();
        lines.add("Hello " + user.getFullName() + ",");
        lines.add("");
        if (!items.isEmpty()) {
            lines.add(items.size() == 1 ? "1 item is due today or overdue:" : items.size() + " items are due today or overdue:");
            items.stream().limit(MAX_LISTED_ITEMS).forEach(item -> lines.add(
                    "- %s (%s)%s".formatted(item.title(), item.dueDate(), item.overdue() ? " OVERDUE" : "")));
            if (items.size() > MAX_LISTED_ITEMS) {
                lines.add("- and %d more".formatted(items.size() - MAX_LISTED_ITEMS));
            }
            lines.add("");
        }
        if (reviewDue) {
            lines.add("Your weekly review is due.");
        }
        dreamReviews.stream().limit(MAX_LISTED_ITEMS).forEach(review -> lines.add(
                "A %s review of \"%s\" is due.".formatted(review.reviewType().toLowerCase(), review.dreamTitle())));
        if (inactive) {
            lines.add("You have not updated anything for %d days or more. One small step counts.".formatted(inactiveAfterDays));
        }
        if (reportDue) {
            lines.add(user.getReportReminder() == ReportReminder.WEEKLY
                    ? "It is time to prepare your weekly report." : "It is time to prepare your monthly report.");
            if (!reportsUrl.isBlank()) {
                lines.add("Open your reports: " + reportsUrl);
            }
        }
        // AI-FR-15: only when the admin has switched it on and the participant consented.
        if (daily) {
            aiReminderService.encouragement(user).ifPresent(line -> {
                lines.add("");
                lines.add(line);
            });
        }
        if (daily && !todayUrl.isBlank()) {
            lines.add("");
            lines.add("Open your day: " + todayUrl);
        }
        lines.add("");
        lines.add("You can turn these reminders off under Account in the app.");
        return String.join("\n", lines);
    }

    private boolean isInactive(AppUser user, Instant now) {
        return activityService.lastActivity(user).isBefore(now.minus(inactiveAfterDays, ChronoUnit.DAYS));
    }

    /**
     * One @ with something before it, and after it a domain of at least two
     * parts split by dots, none empty; no spaces anywhere. Checked in plain
     * code rather than a regular expression, so long input cannot make it slow
     * or overflow the stack.
     */
    public static boolean looksLikeEmail(String value) {
        int at = value.indexOf('@');
        if (at <= 0 || at != value.lastIndexOf('@') || value.chars().anyMatch(Character::isWhitespace)) {
            return false;
        }
        String[] parts = value.substring(at + 1).split("\\.", -1);
        return parts.length >= 2 && Arrays.stream(parts).noneMatch(String::isEmpty);
    }
}
