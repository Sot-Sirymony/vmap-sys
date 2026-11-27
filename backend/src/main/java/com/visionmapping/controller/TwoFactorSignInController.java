package com.visionmapping.controller;

import com.visionmapping.dto.request.TwoFactorResendRequest;
import com.visionmapping.dto.request.TwoFactorVerifyRequest;
import com.visionmapping.dto.response.AuthResponse;
import com.visionmapping.dto.response.TwoFactorChallengeResponse;
import com.visionmapping.service.twofactor.SignInChallengeService;
import com.visionmapping.util.ClientIp;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.visionmapping.web.ApiPaths;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.MediaType;
import org.springframework.validation.annotation.Validated;

/**
 * V21 (BRD 21.0.0): the second step of signing in. Public, because no session
 * exists yet; the challenge token from the first factor is the key.
 */
@RestController
@RequestMapping(value = ApiPaths.V1 + "/auth/two-factor", produces = MediaType.APPLICATION_JSON_VALUE)
@Validated
@RequiredArgsConstructor
@Tag(name = "Two-step sign-in", description = "The second step of signing in")
public class TwoFactorSignInController {

    private final SignInChallengeService signInChallenges;

    /** TF-FR-13 to TF-FR-17: the session; 400 with triesLeft for a wrong code; 410 when the step has ended. */
    @Operation(summary = "Check the second-step code and sign in")
    @PostMapping(value = "/verify", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<AuthResponse> verify(@Valid @RequestBody TwoFactorVerifyRequest request, HttpServletRequest http) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(signInChallenges.verify(request.challengeToken(), request.code(), request.recoveryCode(),
                        request.rememberDevice(), ClientIp.of(http), http.getHeader("User-Agent")));
    }

    /** TF-FR-12: a new email code; 429 within a minute of the last. */
    @Operation(summary = "Send the second-step code again")
    @PostMapping(value = "/resend", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<TwoFactorChallengeResponse> resend(@Valid @RequestBody TwoFactorResendRequest request) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(signInChallenges.resend(request.challengeToken()));
    }
}
