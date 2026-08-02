package com.visionmapping.controller;

import com.visionmapping.dto.request.ChangePasswordRequest;
import com.visionmapping.dto.request.ForgotPasswordRequest;
import com.visionmapping.dto.request.LoginRequest;
import com.visionmapping.dto.request.RegisterRequest;
import com.visionmapping.dto.request.ResetPasswordRequest;
import com.visionmapping.dto.response.AuthResponse;
import com.visionmapping.service.AuthService;
import com.visionmapping.service.PasswordResetService;
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

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public AuthResponse register(@Valid @RequestBody RegisterRequest request) {
        return authService.register(request);
    }

    @PostMapping("/login")
    public AuthResponse login(@Valid @RequestBody LoginRequest request) {
        return authService.login(request);
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
        passwordResetService.requestReset(request, clientIp(http));
    }

    /**
     * Render terminates TLS in front of the app, so the socket address is the
     * proxy for every caller and would rate-limit the whole internet as one
     * client. The first entry of X-Forwarded-For is the original caller.
     *
     * A client can forge that header, so this is not an identity — it is a
     * throttling key that makes casual abuse cost something. The per-address
     * limit is the one that actually protects a victim's inbox, and it cannot be
     * sidestepped this way.
     */
    private String clientIp(HttpServletRequest http) {
        String forwarded = http.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return http.getRemoteAddr();
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
