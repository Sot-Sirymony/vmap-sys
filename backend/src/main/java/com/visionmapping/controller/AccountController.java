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
import com.visionmapping.web.RequiresStepUp;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.visionmapping.web.ApiPaths;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;

@RestController
@RequestMapping(value = ApiPaths.V1 + "/account", produces = MediaType.APPLICATION_JSON_VALUE)
@Validated
@RequiredArgsConstructor
@Tag(name = "Account", description = "My account: data export, deletion, reminders and sign-in methods")
public class AccountController {

    private final AccountService accountService;
    private final ReminderService reminderService;
    private final SignInMethodsService signInMethodsService;

    /** V14 (GA-FR-26, GA-FR-27): whether a password is set, and any linked Google account. */
    @Operation(summary = "Get my sign-in methods")
    @GetMapping("/sign-in-methods")
    public ResponseEntity<SignInMethodsResponse> signInMethods() {
        return ResponseEntity.ok(signInMethodsService.current());
    }

    @Operation(summary = "Export all my data")
    @GetMapping("/export")
    @RequiresStepUp
    public ResponseEntity<AccountExportResponse> export() {
        return ResponseEntity.ok(accountService.exportData());
    }

    /** POST rather than DELETE: it carries the password, and DELETE bodies are poorly supported. */
    @Operation(summary = "Delete my account")
    @PostMapping(value = "/delete", consumes = MediaType.APPLICATION_JSON_VALUE)
    @RequiresStepUp
    public ResponseEntity<Void> delete(@Valid @RequestBody DeleteAccountRequest request) {
        accountService.deleteAccount(request);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Get my reminder settings")
    @GetMapping("/reminders")
    public ResponseEntity<ReminderSettingsResponse> reminders() {
        return ResponseEntity.ok(reminderService.settings());
    }

    @Operation(summary = "Change my reminder settings")
    @PutMapping(value = "/reminders", consumes = MediaType.APPLICATION_JSON_VALUE)
    @RequiresStepUp(confirm = true)
    public ResponseEntity<ReminderSettingsResponse> updateReminders(@Valid @RequestBody ReminderSettingsRequest request) {
        return ResponseEntity.ok(reminderService.updateSettings(request));
    }

    @Operation(summary = "Send me a test reminder")
    @PostMapping("/reminders/test")
    public ResponseEntity<ReminderSettingsResponse> sendTestReminder() {
        return ResponseEntity.ok(reminderService.sendTest());
    }
}
