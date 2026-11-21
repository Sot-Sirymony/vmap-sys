package com.visionmapping.controller;

import com.visionmapping.dto.request.GoogleExchangeRequest;
import com.visionmapping.dto.request.GoogleLinkRequest;
import com.visionmapping.dto.response.AuthResponse;
import com.visionmapping.dto.response.GoogleSignInStatusResponse;
import com.visionmapping.service.oauth.GoogleAccountService;
import com.visionmapping.service.oauth.GoogleSignInService;
import com.visionmapping.service.support.RequestRateLimiter;
import com.visionmapping.util.ClientIp;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.lang.Nullable;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * V14 (BRD 14.0.0, Appendix B): Sign in with Google. Every endpoint is public,
 * because the person is not signed in yet.
 */
@RestController
@RequestMapping("/api/auth/oauth/google")
@RequiredArgsConstructor
public class GoogleSignInController {

    private final GoogleSignInService googleSignInService;
    private final GoogleAccountService googleAccountService;
    private final RequestRateLimiter rateLimiter;

    /** GA-FR-03. */
    @GetMapping("/status")
    public ResponseEntity<GoogleSignInStatusResponse> status() {
        return ResponseEntity.ok(googleSignInService.status());
    }

    /** GA-FR-05; 503 while Google sign-in is off (GA-FR-02). */
    @GetMapping("/authorize-url")
    public ResponseEntity<Map<String, String>> authorizeUrl(
            @RequestParam(name = "remember", defaultValue = "false") boolean remember,
            @RequestParam(name = "from", required = false) @Nullable String from,
            HttpServletRequest http
    ) {
        rateLimiter.check(RequestRateLimiter.Action.GOOGLE_SIGN_IN, ClientIp.of(http));
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(Map.of("url", googleSignInService.authorizeUrl(remember, from)));
    }

    /**
     * GA-FR-09: Google sends the browser here. Every outcome is a redirect to
     * the frontend, never a JSON page. No Referer leaves this answer and it is
     * never cached (GA-SEC-10).
     */
    @GetMapping("/callback")
    public ResponseEntity<Void> callback(
            @RequestParam(name = "code", required = false) @Nullable String code,
            @RequestParam(name = "state", required = false) @Nullable String state,
            @RequestParam(name = "error", required = false) @Nullable String error,
            HttpServletRequest http
    ) {
        return ResponseEntity.status(HttpStatus.FOUND)
                .location(googleSignInService.callbackRedirect(code, state, error, ClientIp.of(http)))
                .header("Referrer-Policy", "no-referrer")
                .cacheControl(CacheControl.noStore())
                .build();
    }

    /** GA-FR-20: the one-time sign-in code for the normal sign-in answer; 400 when used or expired. */
    @PostMapping("/exchange")
    public ResponseEntity<AuthResponse> exchange(@Valid @RequestBody GoogleExchangeRequest request, HttpServletRequest http) {
        rateLimiter.check(RequestRateLimiter.Action.GOOGLE_SIGN_IN, ClientIp.of(http));
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(googleAccountService.exchange(request.code()));
    }

    /** GA-FR-23: links Google to an existing account after its password; 400, or 429 when locked. */
    @PostMapping("/link")
    public ResponseEntity<AuthResponse> link(@Valid @RequestBody GoogleLinkRequest request, HttpServletRequest http) {
        rateLimiter.check(RequestRateLimiter.Action.GOOGLE_SIGN_IN, ClientIp.of(http));
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(googleAccountService.link(request.linkToken(), request.password(), ClientIp.of(http)));
    }
}
