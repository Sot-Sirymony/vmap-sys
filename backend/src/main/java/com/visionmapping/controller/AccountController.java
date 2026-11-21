package com.visionmapping.controller;

import com.visionmapping.dto.request.DeleteAccountRequest;
import com.visionmapping.dto.request.ReminderSettingsRequest;
import com.visionmapping.dto.response.AccountExportResponse;
import com.visionmapping.dto.response.ReminderSettingsResponse;
import com.visionmapping.dto.response.SignInMethodsResponse;
import com.visionmapping.service.AccountService;
import com.visionmapping.service.ReminderService;
import com.visionmapping.service.SignInMethodsService;
import jakarta.validation.Valid;
import com.visionmapping.service.stepup.StepUpService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/account")
@RequiredArgsConstructor
public class AccountController {

    private final AccountService accountService;
    private final ReminderService reminderService;
    private final SignInMethodsService signInMethodsService;

    /** V14 (GA-FR-26, GA-FR-27): whether a password is set, and any linked Google account. */
    @GetMapping("/sign-in-methods")
    public SignInMethodsResponse signInMethods() {
        return signInMethodsService.current();
    }

    @GetMapping("/export")
    public AccountExportResponse export(@RequestHeader(value = StepUpService.HEADER, required = false) String stepUp) {
        return accountService.exportData(stepUp);
    }

    /** POST rather than DELETE: it carries the password, and DELETE bodies are poorly supported. */
    @PostMapping("/delete")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@Valid @RequestBody DeleteAccountRequest request,
                       @RequestHeader(value = StepUpService.HEADER, required = false) String stepUp) {
        accountService.deleteAccount(request, stepUp);
    }

    @GetMapping("/reminders")
    public ReminderSettingsResponse reminders() {
        return reminderService.settings();
    }

    @PutMapping("/reminders")
    public ReminderSettingsResponse updateReminders(@Valid @RequestBody ReminderSettingsRequest request,
                                                    @RequestHeader(value = StepUpService.HEADER, required = false) String stepUp,
                                                    @RequestHeader(value = StepUpService.CONFIRM_HEADER, required = false) String confirm) {
        return reminderService.updateSettings(request, stepUp, confirm);
    }

    @PostMapping("/reminders/test")
    public ReminderSettingsResponse sendTestReminder() {
        return reminderService.sendTest();
    }
}
