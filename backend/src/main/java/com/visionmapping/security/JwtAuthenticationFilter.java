package com.visionmapping.security;

import com.visionmapping.entity.AppUser;
import com.visionmapping.entity.AuthSession;
import com.visionmapping.service.AuthSessionService;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Signs a request in from its bearer token.
 *
 * <p>V10 (SEC-07-03, SEC-07-09): a token is accepted only when it is a valid
 * access token from this app (JwtService.parse) <b>and</b> the session it names
 * is still open <b>and</b> belongs to the same, active user. Ending a session —
 * logout, password change or reset, refresh-token reuse — therefore stops its
 * access tokens on their next request, not when they expire. The session and
 * its user come from one query, so this costs no more than the user lookup it
 * replaced. Anything else leaves the request anonymous, and the security rules
 * answer 401.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtService jwtService;
    private final AuthSessionService authSessionService;

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain
    ) throws ServletException, IOException {
        String authHeader = request.getHeader("Authorization");
        if (authHeader == null || !authHeader.startsWith("Bearer ")
                || SecurityContextHolder.getContext().getAuthentication() != null) {
            filterChain.doFilter(request, response);
            return;
        }

        try {
            JwtService.AccessToken token = jwtService.parse(authHeader.substring(7));
            authSessionService.activeSession(token.sessionId())
                    .filter(session -> belongsTo(session, token.email()))
                    .map(session -> new AppUserPrincipal(session.getUser(), session.getId()))
                    .filter(AppUserPrincipal::isEnabled)
                    .ifPresent(principal -> {
                        UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                                principal, null, principal.getAuthorities());
                        authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                        SecurityContextHolder.getContext().setAuthentication(authentication);
                    });
        } catch (JwtException | IllegalArgumentException exception) {
            log.debug("Rejecting request with invalid JWT: {}", exception.getMessage());
        }

        filterChain.doFilter(request, response);
    }

    private static boolean belongsTo(AuthSession session, String email) {
        AppUser user = session.getUser();
        return user.getEmail().equals(email);
    }
}
