package com.visionmapping.service;

import com.visionmapping.dto.request.ReminderSettingsRequest;
import com.visionmapping.dto.response.DailyPlanResponse.Item;
import com.visionmapping.dto.response.ReminderSettingsResponse;
import com.visionmapping.entity.AppUser;
import com.visionmapping.exception.BusinessRuleException;
import com.visionmapping.repository.AppUserRepository;
import com.visionmapping.service.mail.ReminderMailer;
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

    @Value("${app.reminders.send-hour:8}")
    private int sendHour;

    @Value("${app.reminders.inactive-after-days:3}")
    private int inactiveAfterDays;

    @Value("${app.reminders.today-url:}")
    private String todayUrl;

    @Transactional(readOnly = true)
    public ReminderSettingsResponse settings() {
        return toResponse(userScope.currentUser());
    }

    public ReminderSettingsResponse updateSettings(ReminderSettingsRequest request) {
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
        user.setReminderEmail(reminderEmail.isEmpty() ? null : reminderEmail);
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
                sendHour, user.getReminderEmail(), sendsTo(user));
    }

    private static String sendsTo(AppUser user) {
        return user.getReminderEmail() != null ? user.getReminderEmail() : user.getEmail();
    }

    /** Called by the job once per user, so one user's failure stays theirs. */
    public void remindIfDue(Long userId, Instant now) {
        AppUser user = appUserRepository.findById(userId).orElse(null);
        if (user == null || !user.isRemindersEnabled() || user.getReminderTimeZone() == null) {
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

    @Nullable
    private String composeBody(AppUser user, LocalDate today, Instant now) {
        List<Item> items = dailyPlanService.itemsFor(user.getId(), today);
        boolean reviewDue = dailyPlanService.weeklyReviewDueFor(user.getId(), today);
        boolean inactive = isInactive(user, now);
        if (items.isEmpty() && !reviewDue && !inactive) {
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
        if (inactive) {
            lines.add("You have not updated anything for %d days or more. One small step counts.".formatted(inactiveAfterDays));
        }
        // AI-FR-15: only when the admin has switched it on and the participant consented.
        aiReminderService.encouragement(user).ifPresent(line -> {
            lines.add("");
            lines.add(line);
        });
        if (!todayUrl.isBlank()) {
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
    static boolean looksLikeEmail(String value) {
        int at = value.indexOf('@');
        if (at <= 0 || at != value.lastIndexOf('@') || value.chars().anyMatch(Character::isWhitespace)) {
            return false;
        }
        String[] parts = value.substring(at + 1).split("\\.", -1);
        return parts.length >= 2 && Arrays.stream(parts).noneMatch(String::isEmpty);
    }
}
