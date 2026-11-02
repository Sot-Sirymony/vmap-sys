package com.visionmapping.controller;

import com.visionmapping.dto.request.DeleteAccountRequest;
import com.visionmapping.dto.request.ReminderSettingsRequest;
import com.visionmapping.dto.response.ReminderSettingsResponse;
import com.visionmapping.service.AccountService;
import com.visionmapping.service.ReminderService;
import jakarta.validation.Valid;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/account")
@RequiredArgsConstructor
public class AccountController {

    private final AccountService accountService;
    private final ReminderService reminderService;

    @GetMapping("/export")
    public Map<String, Object> export() {
        return accountService.exportData();
    }

    /** POST rather than DELETE: it carries the password, and DELETE bodies are poorly supported. */
    @PostMapping("/delete")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@Valid @RequestBody DeleteAccountRequest request) {
        accountService.deleteAccount(request);
    }

    @GetMapping("/reminders")
    public ReminderSettingsResponse reminders() {
        return reminderService.settings();
    }

    @PutMapping("/reminders")
    public ReminderSettingsResponse updateReminders(@Valid @RequestBody ReminderSettingsRequest request) {
        return reminderService.updateSettings(request);
    }

    @PostMapping("/reminders/test")
    public ReminderSettingsResponse sendTestReminder() {
        return reminderService.sendTest();
    }
}
