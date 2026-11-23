package com.visionmapping.config;

import com.visionmapping.exception.TwoFactorException;
import com.visionmapping.repository.AppUserRepository;
import com.visionmapping.security.AppUserPrincipal;
import com.visionmapping.service.twofactor.TwoFactorRequirementService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * V21 (TF-FR-34, TFC-19): an admin or coach past their set-up-by date without
 * two-step sign-in may only set it up. Every other API call answers 403
 * TWO_FACTOR_SETUP_REQUIRED, whether or not it came through the page.
 */
@Component
@RequiredArgsConstructor
public class TwoFactorSetupInterceptor implements HandlerInterceptor {

    /** What the setup flow, sign-out and drawing the page need. */
    static final List<String> ALLOWED_PREFIXES = List.of(
            "/api/account/two-factor",
            "/api/account/step-up",
            "/api/account/sign-in-methods",
            "/api/auth/",
            "/api/ui-text",
            "/api/health",
            "/api/preferences/appearance");

    private final AppUserRepository appUserRepository;
    private final TwoFactorRequirementService requirement;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof AppUserPrincipal principal)) {
            return true;
        }
        // V23 (UM-FR-06): only privileged roles have a two-step set-up-by date, unless an Admin requires two-step
        // sign-in for everyone (V24 D-24-4); the setting is one row read by key.
        boolean participant = principal.getPermissions().stream()
                .allMatch(permission -> permission == com.visionmapping.security.Permission.USE_OWN_PLAN);
        if (participant && !requirement.everyone()) {
            return true;
        }
        String path = request.getRequestURI();
        if (ALLOWED_PREFIXES.stream().anyMatch(path::startsWith)) {
            return true;
        }
        boolean blocked = appUserRepository.findById(principal.getId()).map(requirement::setupRequiredNow).orElse(false);
        if (blocked) {
            throw participant ? TwoFactorException.setupRequiredForEveryone() : TwoFactorException.setupRequired();
        }
        return true;
    }
}
