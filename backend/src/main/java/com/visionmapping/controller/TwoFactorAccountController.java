package com.visionmapping.controller;

import com.visionmapping.dto.request.TotpConfirmRequest;
import com.visionmapping.dto.response.RecoveryCodesResponse;
import com.visionmapping.dto.response.TotpSetupResponse;
import com.visionmapping.dto.response.TwoFactorStatusResponse;
import com.visionmapping.service.twofactor.TwoFactorRequirementService;
import com.visionmapping.service.twofactor.TwoFactorService;
import com.visionmapping.web.RequiresStepUp;
import jakarta.validation.Valid;
import com.visionmapping.web.ApiPaths;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.http.CacheControl;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

/**
 * V21 (BRD 21.0.0, TF-FR-01 to TF-FR-09, TF-FR-23): the signed-in person's
 * two-factor settings. Changes need a fresh "Verify it's you" code in the
 * {@code X-Step-Up} header (TF-FR-06). Answers that carry a secret or recovery
 * codes are never cached. Trusted devices are in {@link TrustedDeviceController}.
 */
@RestController
@RequestMapping(value = ApiPaths.V1 + "/account/two-factor", produces = MediaType.APPLICATION_JSON_VALUE)
@Validated
@RequiredArgsConstructor
@Tag(name = "Two-step sign-in settings", description = "My two-step sign-in: authenticator app, email codes, recovery codes")
public class TwoFactorAccountController {

    private final TwoFactorService twoFactorService;
    private final TwoFactorRequirementService twoFactorRequirement;

    @Operation(summary = "Get my two-step sign-in settings")
    @GetMapping
    public ResponseEntity<TwoFactorStatusResponse> status() {
        return ResponseEntity.ok(twoFactorService.status());
    }

    @Operation(summary = "Start setting up an authenticator app")
    @PostMapping("/totp")
    @RequiresStepUp
    public ResponseEntity<TotpSetupResponse> startAuthenticator() {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(twoFactorService.startAuthenticator());
    }

    @Operation(summary = "Confirm the authenticator app with its first code")
    @PostMapping(value = "/totp/confirm", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<RecoveryCodesResponse> confirmAuthenticator(@Valid @RequestBody TotpConfirmRequest request) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(twoFactorService.confirmAuthenticator(request.code()));
    }

    @Operation(summary = "Use email codes for two-step sign-in")
    @PostMapping("/email")
    @RequiresStepUp
    public ResponseEntity<RecoveryCodesResponse> useEmail() {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(twoFactorService.useEmail());
    }

    @Operation(summary = "Make new recovery codes")
    @PostMapping("/recovery-codes")
    @RequiresStepUp
    public ResponseEntity<RecoveryCodesResponse> newRecoveryCodes() {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(twoFactorService.newRecoveryCodes());
    }

    @Operation(summary = "Turn off two-step sign-in")
    @PostMapping("/disable")
    @RequiresStepUp
    public ResponseEntity<Void> turnOff() {
        twoFactorService.turnOff();
        return ResponseEntity.noContent().build();
    }

    /** TF-FR-34: an admin or coach may put off setting it up until the set-up-by date. */
    @Operation(summary = "Set up two-step sign-in later")
    @PostMapping("/skip")
    public ResponseEntity<Void> skipSetup() {
        twoFactorRequirement.skip();
        return ResponseEntity.noContent().build();
    }
}
