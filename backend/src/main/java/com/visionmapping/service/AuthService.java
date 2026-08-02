package com.visionmapping.service;

import com.visionmapping.dto.request.ChangePasswordRequest;
import com.visionmapping.dto.request.LoginRequest;
import com.visionmapping.dto.request.RegisterRequest;
import com.visionmapping.dto.response.AuthResponse;
import com.visionmapping.entity.AppUser;
import com.visionmapping.entity.enums.UserRole;
import com.visionmapping.entity.enums.UserStatus;
import com.visionmapping.exception.BusinessRuleException;
import com.visionmapping.repository.AppUserRepository;
import com.visionmapping.security.AppUserPrincipal;
import com.visionmapping.security.JwtService;
import com.visionmapping.util.UserScope;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional
public class AuthService {

    private final AppUserRepository appUserRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;
    private final UserScope userScope;

    public AuthResponse register(RegisterRequest request) {
        String email = request.email().trim().toLowerCase();
        if (appUserRepository.existsByEmail(email)) {
            throw new BusinessRuleException("Email is already registered.");
        }

        AppUser user = AppUser.builder()
                .fullName(request.fullName())
                .email(email)
                .passwordHash(passwordEncoder.encode(request.password()))
                .role(UserRole.USER)
                .status(UserStatus.ACTIVE)
                .build();
        AppUser saved = appUserRepository.save(user);
        return response(saved);
    }

    public AuthResponse login(LoginRequest request) {
        String email = request.email().trim().toLowerCase();
        authenticationManager.authenticate(new UsernamePasswordAuthenticationToken(email, request.password()));
        AppUser user = appUserRepository.findByEmail(email)
                .orElseThrow(() -> new BusinessRuleException("Invalid email or password."));
        return response(user);
    }

    /**
     * Sets a new password for the signed-in user, after proving they know the
     * current one.
     *
     * The old password is verified against the stored hash rather than through
     * the AuthenticationManager. Authenticating here would be the more familiar
     * shape, but a failure would surface as BadCredentialsException, which the
     * global handler answers with 401 and the message "Invalid email or
     * password." — the wording for a failed sign-in, and a status the frontend
     * treats as an expired session. On this endpoint that would sign the user out
     * mid-change for the ordinary mistake of mistyping their old password.
     */
    public void changePassword(ChangePasswordRequest request) {
        AppUser user = userScope.currentUser();

        if (!passwordEncoder.matches(request.currentPassword(), user.getPasswordHash())) {
            throw new BusinessRuleException("Current password is incorrect.");
        }

        // Otherwise the form reports success having changed nothing, which reads
        // as though the new password took effect when it is the old one.
        if (passwordEncoder.matches(request.newPassword(), user.getPasswordHash())) {
            throw new BusinessRuleException("New password must be different from the current password.");
        }

        user.setPasswordHash(passwordEncoder.encode(request.newPassword()));
        appUserRepository.save(user);
    }

    private AuthResponse response(AppUser user) {
        AppUserPrincipal principal = new AppUserPrincipal(user);
        return new AuthResponse(
                jwtService.generateToken(principal),
                "Bearer",
                user.getId(),
                user.getFullName(),
                user.getEmail(),
                user.getRole().name(),
                // FR-39.6: the theme travels with the session so the first paint
                // after signing in is already the user's own appearance.
                AppearancePreferenceService.toResponse(user)
        );
    }
}
