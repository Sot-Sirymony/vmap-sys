package com.visionmapping.controller;

import com.visionmapping.dto.request.AcceptInvitationRequest;
import com.visionmapping.dto.request.ChangePasswordRequest;
import com.visionmapping.dto.request.ForgotPasswordRequest;
import com.visionmapping.dto.request.InvitationCheckRequest;
import com.visionmapping.dto.request.LoginRequest;
import com.visionmapping.dto.request.RefreshTokenRequest;
import com.visionmapping.dto.request.RegisterRequest;
import com.visionmapping.dto.request.ResetPasswordRequest;
import com.visionmapping.dto.response.AuthResponse;
import com.visionmapping.dto.response.InvitationInfoResponse;
import com.visionmapping.dto.response.SignInAnswer;
import com.visionmapping.service.AuthService;
import com.visionmapping.service.PasswordResetService;
import com.visionmapping.service.auth.SignInRequests;
import com.visionmapping.util.ClientIp;
import com.visionmapping.web.RequiresStepUp;
import jakarta.servlet.http.HttpServletRequest;
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
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

/** Registration, sign-in, sessions, invitations and passwords. The rate limits are in {@link SignInRequests} (V33 D-33-11). */
@RestController
@RequestMapping(value = ApiPaths.V1 + "/auth", produces = MediaType.APPLICATION_JSON_VALUE)
@Validated
@RequiredArgsConstructor
@Tag(name = "Sign-in", description = "Register, sign in, renew and end sessions, invitations and passwords")
public class AuthController {

    private final AuthService authService;
    private final PasswordResetService passwordResetService;
    private final SignInRequests signIn;

    /** V23: whose account an invitation link opens; 410 for a used, replaced or expired link. */
    @Operation(summary = "Check an invitation link")
    @PostMapping(value = "/invitation/check", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<InvitationInfoResponse> checkInvitation(@Valid @RequestBody InvitationCheckRequest request, HttpServletRequest http) {
        return ResponseEntity.ok(signIn.checkInvitation(request, ClientIp.of(http)));
    }

    /** V23 (UM-FR-24): choose a password from the invitation, then signed in as from the sign-in page. */
    @Operation(summary = "Accept an invitation and sign in")
    @PostMapping(value = "/invitation/accept", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<SignInAnswer> acceptInvitation(@Valid @RequestBody AcceptInvitationRequest request, HttpServletRequest http) {
        return ResponseEntity.ok(signIn.acceptInvitation(request, ClientIp.of(http)));
    }

    /** V10 (SEC-06-02): new accounts and join codes are both limited per network address. The new account has no other address. */
    @Operation(summary = "Register a new account")
    @PostMapping(value = "/register", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<AuthResponse> register(@Valid @RequestBody RegisterRequest request, HttpServletRequest http) {
        return ResponseEntity.status(HttpStatus.CREATED).body(signIn.register(request, ClientIp.of(http)));
    }

    @Operation(summary = "Sign in with email and password")
    @PostMapping(value = "/login", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<SignInAnswer> login(@Valid @RequestBody LoginRequest request, HttpServletRequest http) {
        return ResponseEntity.ok(authService.login(request, ClientIp.of(http)));
    }

    /**
     * V10 (SEC-07-04): a new access token, and a new refresh token in place of
     * the one sent. Public, because it is called when the access token has run
     * out. 401 when the session has ended; 409 when another tab renewed it first.
     */
    @Operation(summary = "Renew the session")
    @PostMapping(value = "/refresh", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<AuthResponse> refresh(@Valid @RequestBody RefreshTokenRequest request) {
        return ResponseEntity.ok(authService.refresh(request.refreshToken()));
    }

    /**
     * V10 (SEC-07-03): ends the session on the server, so its refresh token and
     * access tokens stop working at once. Public and always 204, so signing out
     * works even after the access token has run out.
     */
    @Operation(summary = "Sign out")
    @PostMapping(value = "/logout", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Void> logout(@Valid @RequestBody RefreshTokenRequest request) {
        authService.logout(request.refreshToken());
        return ResponseEntity.noContent().build();
    }

    /** Needs a valid token; the service then checks the current password and a fresh code. */
    @Operation(summary = "Change my password")
    @PostMapping(value = "/change-password", consumes = MediaType.APPLICATION_JSON_VALUE)
    @RequiresStepUp
    public ResponseEntity<Void> changePassword(@Valid @RequestBody ChangePasswordRequest request) {
        authService.changePassword(request);
        return ResponseEntity.noContent().build();
    }

    /** Public: the caller has forgotten their password. Always 204, so it does not tell whether the email has an account. */
    @Operation(summary = "Ask for a password reset email")
    @PostMapping(value = "/forgot-password", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Void> forgotPassword(@Valid @RequestBody ForgotPasswordRequest request, HttpServletRequest http) {
        passwordResetService.requestReset(request, ClientIp.of(http));
        return ResponseEntity.noContent().build();
    }

    /** Public, and authorised by the token in the body rather than by a session. */
    @Operation(summary = "Choose a new password with a reset token")
    @PostMapping(value = "/reset-password", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Void> resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        passwordResetService.resetPassword(request);
        return ResponseEntity.noContent().build();
    }
}
