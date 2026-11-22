package com.visionmapping.controller;

import com.visionmapping.dto.request.TotpConfirmRequest;
import com.visionmapping.dto.response.RecoveryCodesResponse;
import com.visionmapping.dto.response.TotpSetupResponse;
import com.visionmapping.dto.response.TwoFactorStatusResponse;
import com.visionmapping.service.stepup.StepUpService;
import com.visionmapping.service.twofactor.TwoFactorService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.lang.Nullable;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * V21 (BRD 21.0.0, TF-FR-01 to TF-FR-09, TF-FR-23): the signed-in person's
 * two-factor settings and trusted devices. Changes need a fresh "Verify it's
 * you" code in the {@code X-Step-Up} header (TF-FR-06). Answers that carry a
 * secret or recovery codes are never cached.
 */
@RestController
@RequiredArgsConstructor
public class TwoFactorAccountController {

    private final TwoFactorService twoFactorService;
    private final com.visionmapping.service.twofactor.TwoFactorRequirementService twoFactorRequirement;

    @GetMapping("/api/account/two-factor")
    public TwoFactorStatusResponse status() {
        return twoFactorService.status();
    }

    @PostMapping("/api/account/two-factor/totp")
    public ResponseEntity<TotpSetupResponse> startAuthenticator(
            @RequestHeader(value = StepUpService.HEADER, required = false) @Nullable String stepUp) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(twoFactorService.startAuthenticator(stepUp));
    }

    @PostMapping("/api/account/two-factor/totp/confirm")
    public ResponseEntity<RecoveryCodesResponse> confirmAuthenticator(@Valid @RequestBody TotpConfirmRequest request) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(twoFactorService.confirmAuthenticator(request.code()));
    }

    @PostMapping("/api/account/two-factor/email")
    public ResponseEntity<RecoveryCodesResponse> useEmail(
            @RequestHeader(value = StepUpService.HEADER, required = false) @Nullable String stepUp) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(twoFactorService.useEmail(stepUp));
    }

    @PostMapping("/api/account/two-factor/recovery-codes")
    public ResponseEntity<RecoveryCodesResponse> newRecoveryCodes(
            @RequestHeader(value = StepUpService.HEADER, required = false) @Nullable String stepUp) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(twoFactorService.newRecoveryCodes(stepUp));
    }

    @PostMapping("/api/account/two-factor/disable")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void turnOff(@RequestHeader(value = StepUpService.HEADER, required = false) @Nullable String stepUp) {
        twoFactorService.turnOff(stepUp);
    }

    /** TF-FR-33: an admin or coach chose "Skip for now" (logged). */
    @PostMapping("/api/account/two-factor/skip")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void skipSetup() {
        twoFactorRequirement.skip();
    }

    @DeleteMapping("/api/account/trusted-devices/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void removeDevice(@PathVariable Long id) {
        twoFactorService.removeDevice(id);
    }

    @DeleteMapping("/api/account/trusted-devices")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void removeAllDevices() {
        twoFactorService.removeAllDevices();
    }
}
