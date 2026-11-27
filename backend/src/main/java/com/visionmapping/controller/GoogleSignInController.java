package com.visionmapping.controller;

import com.visionmapping.dto.request.GoogleExchangeRequest;
import com.visionmapping.dto.request.GoogleLinkRequest;
import com.visionmapping.dto.response.GoogleSignInStatusResponse;
import com.visionmapping.dto.response.SignInAnswer;
import com.visionmapping.service.auth.SignInRequests;
import com.visionmapping.service.oauth.GoogleSignInService;
import com.visionmapping.util.ClientIp;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import java.util.Map;
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
import org.springframework.http.HttpStatus;
import org.springframework.lang.Nullable;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * V14 (BRD 14.0.0, Appendix B): Sign in with Google. Every endpoint is public,
 * because the person is not signed in yet. The rate limits are in {@link SignInRequests}.
 */
@RestController
@RequestMapping(value = ApiPaths.V1 + "/auth/oauth/google", produces = MediaType.APPLICATION_JSON_VALUE)
@Validated
@RequiredArgsConstructor
@Tag(name = "Google sign-in", description = "Sign in with Google")
public class GoogleSignInController {

    private final GoogleSignInService googleSignInService;
    private final SignInRequests signIn;

    @Operation(summary = "Whether Google sign-in is set up and on")
    @GetMapping("/status")
    public ResponseEntity<GoogleSignInStatusResponse> status() {
        return ResponseEntity.ok(googleSignInService.status());
    }

    @Operation(summary = "Get the address that starts Google sign-in")
    @GetMapping("/authorize-url")
    public ResponseEntity<Map<String, String>> authorizeUrl(
            @RequestParam(name = "remember", defaultValue = "false") boolean remember,
            @RequestParam(name = "from", required = false) @Nullable @Size(max = 200) String from,
            HttpServletRequest http) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(Map.of("url", signIn.googleAuthorizeUrl(remember, from, ClientIp.of(http))));
    }

    /** Google sends the browser here; it is sent on to the app with a one-time code. */
    @Operation(summary = "Google's callback; answers with a redirect to the app")
    @GetMapping("/callback")
    public ResponseEntity<Void> callback(
            @RequestParam(name = "code", required = false) @Nullable String code,
            @RequestParam(name = "state", required = false) @Nullable String state,
            @RequestParam(name = "error", required = false) @Nullable String error,
            HttpServletRequest http) {
        return ResponseEntity.status(HttpStatus.FOUND)
                .location(googleSignInService.callbackRedirect(code, state, error, ClientIp.of(http)))
                .header("Referrer-Policy", "no-referrer")
                .cacheControl(CacheControl.noStore())
                .build();
    }

    @Operation(summary = "Exchange the one-time code for a session")
    @PostMapping(value = "/exchange", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<SignInAnswer> exchange(@Valid @RequestBody GoogleExchangeRequest request, HttpServletRequest http) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(signIn.googleExchange(request, ClientIp.of(http)));
    }

    @Operation(summary = "Link Google to my password account")
    @PostMapping(value = "/link", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<SignInAnswer> link(@Valid @RequestBody GoogleLinkRequest request, HttpServletRequest http) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(signIn.googleLink(request, ClientIp.of(http)));
    }
}
