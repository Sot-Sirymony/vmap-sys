package com.visionmapping.controller;

import com.visionmapping.service.support.RequestRateLimiter;
import com.visionmapping.dto.request.ChangePasswordRequest;
import com.visionmapping.dto.request.ForgotPasswordRequest;
import com.visionmapping.dto.request.LoginRequest;
import com.visionmapping.dto.request.RefreshTokenRequest;
import com.visionmapping.dto.request.RegisterRequest;
import com.visionmapping.dto.request.ResetPasswordRequest;
import com.visionmapping.dto.response.AuthResponse;
import com.visionmapping.service.AuthService;
import com.visionmapping.service.PasswordResetService;
import com.visionmapping.util.ClientIp;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final PasswordResetService passwordResetService;
    private final RequestRateLimiter rateLimiter;

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public AuthResponse register(@Valid @RequestBody RegisterRequest request, HttpServletRequest http) {
        // V10 (SEC-06-02): new accounts and join codes are both limited per network address.
        String clientIp = ClientIp.of(http);
        rateLimiter.check(RequestRateLimiter.Action.REGISTRATION, clientIp);
        if (request.joinCode() != null && !request.joinCode().isBlank()) {
            rateLimiter.check(RequestRateLimiter.Action.JOIN_CODE, "ip:" + clientIp);
        }
        return authService.register(request);
    }

    @PostMapping("/login")
    public AuthResponse login(@Valid @RequestBody LoginRequest request, HttpServletRequest http) {
        return authService.login(request, ClientIp.of(http));
    }

    /**
     * V10 (SEC-07-04): a new access token, and a new refresh token in place of
     * the one sent. Public, because it is called when the access token has run
     * out. 401 when the session has ended; 409 when another tab renewed it a
     * moment ago (retry with the token that tab saved).
     */
    @PostMapping("/refresh")
    public AuthResponse refresh(@Valid @RequestBody RefreshTokenRequest request) {
        return authService.refresh(request.refreshToken());
    }

    /**
     * V10 (SEC-07-03): ends the session on the server, so its refresh token and
     * access tokens stop working at once. Public and always 204, so signing out
     * works even after the access token has run out.
     */
    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(@Valid @RequestBody RefreshTokenRequest request) {
        authService.logout(request.refreshToken());
    }

    /**
     * Unlike register and login, this path is not in SecurityConfig's permitAll
     * list, so it inherits `anyRequest().authenticated()` — the caller must
     * already hold a valid token, and the service then re-checks the password.
     *
     * Returns no body: the response to a password change has nothing useful to
     * say, and echoing any part of the request would put a password in a place
     * it does not need to be.
     */
    @PostMapping("/change-password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void changePassword(@Valid @RequestBody ChangePasswordRequest request) {
        authService.changePassword(request);
    }

    /**
     * Public: the caller has forgotten their password, so they cannot be
     * authenticated first.
     *
     * Always 204, whether or not the address belongs to an account. Answering
     * differently would make this a membership oracle — submit addresses, learn
     * which are registered — and that list is exactly what is worth having
     * before attacking passwords.
     */
    @PostMapping("/forgot-password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void forgotPassword(@Valid @RequestBody ForgotPasswordRequest request, HttpServletRequest http) {
        passwordResetService.requestReset(request, ClientIp.of(http));
    }

    /**
     * Public, and authorised by the token in the body rather than by a session:
     * someone who has forgotten their password has no other credential.
     */
    @PostMapping("/reset-password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        passwordResetService.resetPassword(request);
    }
}
