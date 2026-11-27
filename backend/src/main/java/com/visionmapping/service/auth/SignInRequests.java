package com.visionmapping.service.auth;

import com.visionmapping.dto.request.AcceptInvitationRequest;
import com.visionmapping.dto.request.GoogleExchangeRequest;
import com.visionmapping.dto.request.GoogleLinkRequest;
import com.visionmapping.dto.request.InvitationCheckRequest;
import com.visionmapping.dto.request.RegisterRequest;
import com.visionmapping.dto.response.AuthResponse;
import com.visionmapping.dto.response.InvitationInfoResponse;
import com.visionmapping.dto.response.SignInAnswer;
import com.visionmapping.service.AuthService;
import com.visionmapping.service.admin.InvitationService;
import com.visionmapping.service.oauth.GoogleAccountService;
import com.visionmapping.service.oauth.GoogleSignInService;
import com.visionmapping.service.support.RequestRateLimiter;
import lombok.RequiredArgsConstructor;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;

/**
 * V33 (D-33-11): the sign-in requests that are limited per network address
 * before they run. The limits were in the controllers; they are unchanged
 * (V10 SEC-06-02, V14, V23).
 */
@Service
@RequiredArgsConstructor
public class SignInRequests {

    private final AuthService authService;
    private final InvitationService invitationService;
    private final GoogleSignInService googleSignInService;
    private final GoogleAccountService googleAccountService;
    private final RequestRateLimiter rateLimiter;

    /** V23 (UM-FR-24): whether an invitation link still works. */
    public InvitationInfoResponse checkInvitation(InvitationCheckRequest request, String clientIp) {
        rateLimiter.check(RequestRateLimiter.Action.INVITATION, clientIp);
        return invitationService.check(request.token());
    }

    /** V23 (UM-FR-24): set a password through an invitation link and sign in. */
    public SignInAnswer acceptInvitation(AcceptInvitationRequest request, String clientIp) {
        rateLimiter.check(RequestRateLimiter.Action.INVITATION, clientIp);
        return invitationService.accept(request.token(), request.password(), request.remember());
    }

    /** New accounts and join codes are both limited per network address. */
    public AuthResponse register(RegisterRequest request, String clientIp) {
        rateLimiter.check(RequestRateLimiter.Action.REGISTRATION, clientIp);
        if (request.joinCode() != null && !request.joinCode().isBlank()) {
            rateLimiter.check(RequestRateLimiter.Action.JOIN_CODE, "ip:" + clientIp);
        }
        return authService.register(request);
    }

    /** V14: the address that starts Google sign-in. */
    public String googleAuthorizeUrl(boolean remember, @Nullable String from, String clientIp) {
        rateLimiter.check(RequestRateLimiter.Action.GOOGLE_SIGN_IN, clientIp);
        return googleSignInService.authorizeUrl(remember, from);
    }

    /** V14: the one-time code from the Google callback, exchanged for a session. */
    public SignInAnswer googleExchange(GoogleExchangeRequest request, String clientIp) {
        rateLimiter.check(RequestRateLimiter.Action.GOOGLE_SIGN_IN, clientIp);
        return googleAccountService.exchange(request.code(), request.deviceToken());
    }

    /** V14: link Google to an existing password account. */
    public SignInAnswer googleLink(GoogleLinkRequest request, String clientIp) {
        rateLimiter.check(RequestRateLimiter.Action.GOOGLE_SIGN_IN, clientIp);
        return googleAccountService.link(request.linkToken(), request.password(), clientIp, request.deviceToken());
    }
}
