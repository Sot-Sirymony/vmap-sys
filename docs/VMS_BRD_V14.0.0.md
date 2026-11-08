# BRD – Sign in with Google

Nov 8, 2026

## Document control

This is draft 14.0.0. It keeps every requirement in BRD 13.0.0 (Java Code Quality to Effective Java Practice), BRD 12.0.0 (Test Coverage to 80%), BRD 11.0.0 (AI Plan Builder Improvements), BRD 10.0.0 (Security Hardening to the OWASP Top 10), BRD 9.0.0 (Dream Conversion Program with Claude AI Integration) and earlier versions. Where this BRD is silent, the earlier BRDs still apply.

V14 makes the existing "Continue with Google" button work. Participants can sign up and sign in with their Google account. A person who already has a password account can link Google to it after confirming their password once. Password sign-in keeps working exactly as before.

| Item | Value |
| --- | --- |
| Document | Business Requirements Document (BRD) – Google sign-in |
| Version | 14.0.0 – Draft |
| Parent documents | BRD 13.0.0 (8 Nov 2026), BRD 10.0.0 (6 Nov 2026) |
| Business owner | To be confirmed |
| Product owner | To be confirmed |
| Approvers | Sponsor, Product owner, Engineering lead, Security lead, QA lead |

| Version | Change | Author |
| --- | --- | --- |
| 14.0.0 | First draft: current state at commit `bceea65`, sign-in flow, requirements GA-FR-01 to GA-FR-34, security requirements, phased plan, Google Cloud setup and acceptance criteria | BA |

## Executive summary

The Sign In and Sign Up pages already show a "Continue with Google" button, and the frontend already has three Google screens: a loading screen, an error screen and an account-linking screen. **The backend has no Google support at all.** When someone clicks the button, the frontend asks the backend for Google's sign-in address (`GET /api/auth/oauth/google/authorize-url`). That endpoint does not exist, so every attempt ends on "We couldn't sign you in with Google". The frontend code says so itself: *"The backend does not implement these yet"* (`frontend/src/api/oauthApi.ts`).

Participants in the program often use a phone and already have a Google account. Asking them to invent and remember another password adds friction at the first step of the method, and forgotten passwords are a common support request.

V14 builds the missing backend and the one missing frontend step:

- The backend becomes a Google OAuth client. It sends the browser to Google, receives Google's answer, and checks Google's signed ID token before it trusts anything in it.
- One Google account maps to one app account. There are three outcomes:
  1. **Already linked:** the person is signed in.
  2. **Email already used by a password account:** the person confirms their password once, then Google is linked to that account.
  3. **New email:** an account is created.
- After that, the app issues its own access and refresh tokens, exactly as password sign-in does (BRD 10). Sessions, "remember me", sign-out and every security rule stay the same.

Security is the main design constraint. The plan uses the authorization code flow with PKCE, a one-time `state` value, Google's signature check, a verified email, and a one-time hand-off code so that tokens never appear in a URL. Google is never allowed to take over an existing account without that account's password.

## Business objectives

| ID | Objective | Measure |
| --- | --- | --- |
| GBO-01 | Make starting easier | A new participant can create an account in two taps with Google, without choosing a password |
| GBO-02 | Fewer forgotten-password problems | Password-reset requests per active user go down after launch (tracked from the security log) |
| GBO-03 | No new way to break in | No account can be entered through Google without either an earlier link or the account's password; all BRD 10 controls still pass |
| GBO-04 | Nothing breaks for current users | Password sign-in, refresh, sign-out and every existing test behave as before |

## Scope

### In scope

- Backend endpoints for Google sign-in, sign-up and account linking.
- Checking Google's ID token (signature, issuer, audience, expiry, nonce, verified email).
- One database migration: Google identities, and short-lived sign-in attempt and hand-off records.
- One new frontend page that finishes the sign-in. Small changes to the Sign In page (pass "remember me").
- An on/off switch: the feature is off until Google settings are present.
- Security log events, rate limits, tests with a local Google stub, and documentation, including the Google Cloud setup steps.
- Rollout on Render.

### Out of scope

- Other providers (Microsoft, Apple, Facebook). The data model allows them later.
- Reading any Google data beyond the basic sign-in scopes `openid`, `email` and `profile` (no Drive, Calendar or Contacts).
- One-tap or automatic Google sign-in prompts.
- Removing passwords. Password sign-in stays.
- Mobile apps.
- Disconnecting Google from an account. Proposed for a later version (Q6).

## Current state (8 Nov 2026, commit `bceea65`)

### Frontend (already built)

| Piece | File | What it does today |
| --- | --- | --- |
| Google button | `components/common/GoogleSignInButton.tsx` | On Sign In and Sign Up. Opens `/oauth/google`; Sign In also passes the page to return to (`from`) |
| Loading screen | `pages/OAuthGoogleStartPage.tsx` | Calls `GET /api/auth/oauth/google/authorize-url`, expects `{ "url": "…" }`, then sends the browser there. On any failure it opens the error screen |
| Error screen | `pages/OAuthGoogleErrorPage.tsx` | Generic message, plus a specific one passed in navigation state. "Try again" and "Use password" |
| Linking screen | `pages/AccountLinkingPage.tsx` | Reads `linkToken` from **navigation state**, asks for the password, calls `POST /api/auth/oauth/google/link` with `{ linkToken, password }`, expects the normal sign-in answer, then opens the home page |
| API calls | `api/oauthApi.ts` | `getGoogleAuthorizeUrl()`, `linkGoogleAccount()` |

**Gap:** no frontend page receives the result of Google's redirect, and the linking screen cannot be reached from a redirect, because a redirect cannot set navigation state.

### Backend (missing)

- No Google endpoints, and no OAuth or JWT-verification library for third-party tokens.
- `/api/auth/oauth/**` is not on the public path list, so even a stub would answer 401.
- `app_users.password_hash` is `NOT NULL`. Every account must have a password hash today.
- Password sign-in (`AuthService.login`) ends with `AuthSessionService.open(user, rememberMe)`, which returns the access token, the refresh token and the user. Google sign-in will end the same way.

## Target flow

```text
Browser (frontend)          Backend                                   Google
──────────────────          ───────                                   ──────
1. Click "Continue with Google"
   /oauth/google ──GET /api/auth/oauth/google/authorize-url?remember=…──▶
                            2. create sign-in attempt:
                               state, PKCE verifier, nonce (10 min)
   ◀──────── { url: accounts.google.com/…&state&code_challenge&nonce } ─
3. browser goes to Google ───────────────────────────────────────────▶ person signs in,
                                                                        agrees to share
                                                                        name and email
4. Google redirects ──GET /api/auth/oauth/google/callback?code&state──▶
                            5. check state (once, not expired)
                               exchange code + PKCE verifier ────────▶ token endpoint
                               ◀──────────────────────────── ID token
                               verify ID token (signature, iss, aud,
                               exp, nonce, email_verified)
                            6. decide the outcome:
                               a) Google ID already linked → sign-in
                               b) email has a password account → link
                               c) new email → create account
                            7. redirect to the frontend with a one-time
                               code in the URL fragment (never a token)
   ◀── 302 {FRONTEND}/oauth/google/callback#result=signed-in&code=…
       or  #result=link&code=…   or  #result=error&reason=…
8. /oauth/google/callback (NEW page)
   POST /api/auth/oauth/google/exchange { code } ─────────────────────▶
   ◀──────────── normal sign-in answer (access + refresh token, user) ─
   store session as for password sign-in, open `from` or home
   (link case: open /oauth/google/link with state { linkToken: code })
```

The URL fragment (`#…`) is never sent to any server, so it does not appear in server logs, Render logs or Referer headers.

## Functional requirements

"Must" is required for acceptance; "should" is done if the phase has room.

### A. Switch and configuration

| ID | Requirement |
| --- | --- |
| GA-FR-01 | Google sign-in is configured with environment variables: `GOOGLE_CLIENT_ID`, `GOOGLE_CLIENT_SECRET`, `GOOGLE_REDIRECT_URI` (the backend callback address) and `FRONTEND_URL` (where the browser goes afterwards). |
| GA-FR-02 | When `GOOGLE_CLIENT_ID` or `GOOGLE_CLIENT_SECRET` is missing, the feature is off. `authorize-url` answers HTTP 503 with "Google sign-in is not set up yet. Please use your email and password." The frontend error screen already shows this message. |
| GA-FR-03 | A public `GET /api/auth/oauth/google/status` should answer `{ "enabled": true/false }`, and the frontend should hide the Google button when it is off (Q5). |
| GA-FR-04 | The client secret is never logged, never sent to the browser and never returned by any endpoint. |

### B. Starting sign-in

| ID | Requirement |
| --- | --- |
| GA-FR-05 | `GET /api/auth/oauth/google/authorize-url?remember=true|false&from=/path` is public and returns `{ "url": "https://accounts.google.com/o/oauth2/v2/auth?…" }`. |
| GA-FR-06 | Each call creates a **sign-in attempt** that stores a random `state` (stored only as a hash), a PKCE `code_verifier`, a random `nonce`, the `remember` choice and the `from` path. It expires after 10 minutes and can be used once. |
| GA-FR-07 | The Google URL asks only for `openid email profile`, uses `response_type=code`, `code_challenge_method=S256`, and includes `state`, `nonce` and `prompt=select_account`, so a person on a shared phone can choose the account. |
| GA-FR-08 | `from` must be a path inside the app (it starts with a single `/`). Anything else is replaced by `/`, so the flow cannot be used to redirect people to another site. |

### C. Google's answer

| ID | Requirement |
| --- | --- |
| GA-FR-09 | `GET /api/auth/oauth/google/callback?code=…&state=…` is public. It finds the attempt by the state's hash and refuses it when it is unknown, expired or already used. Every outcome is a redirect to the frontend, never a JSON page. |
| GA-FR-10 | When Google sends `error=access_denied` (the person pressed Cancel), the browser goes to the frontend with `#result=error&reason=cancelled`. |
| GA-FR-11 | The backend exchanges the code at Google's token endpoint with the client secret and the PKCE verifier. |
| GA-FR-12 | The backend verifies the ID token before using it: the signature against Google's published keys (cached, refreshed when a new key ID appears), `iss` is `https://accounts.google.com` or `accounts.google.com`, `aud` is our client ID, the token has not expired (2 minutes of clock leeway), `nonce` matches the attempt, and `email_verified` is `true`. Any failure leads to `#result=error&reason=verification`. |
| GA-FR-13 | The Google account is identified by the ID token's `sub` (Google's permanent user ID), never by the email address alone. |

### D. The three outcomes

| ID | Requirement |
| --- | --- |
| GA-FR-14 | **Already linked:** if a Google identity with this `sub` exists, that account is signed in. If the Google email has changed since linking, it is recorded, but the account email does not change. |
| GA-FR-15 | **Email used by a password account:** if no identity exists but an account has the same email (compared lower-case), nothing is linked yet. The backend creates a **link token** (10 minutes, single use, stored as a hash, bound to the Google `sub` and that account) and redirects with `#result=link&code=<link token>`. |
| GA-FR-16 | **New email:** the backend creates an account (role USER, status Active, full name from Google's `name` or the part of the email before "@"), links the Google identity, records `REGISTERED` with the detail "google", and signs the person in. The account has no usable password (see GA-FR-25). |
| GA-FR-17 | A disabled account is never signed in through Google. The answer is the same as for a refused password sign-in (BRD 10, SEC-01-05): `#result=error&reason=unavailable`. |
| GA-FR-18 | Coach and admin accounts may use Google once linked. Their role still comes from the database on every request (BRD 10). |

### E. Handing the result to the frontend

| ID | Requirement |
| --- | --- |
| GA-FR-19 | For a sign-in, the backend creates a **hand-off code** (60 seconds, single use, stored as a hash) and redirects to `{FRONTEND_URL}/oauth/google/callback#result=signed-in&code=<hand-off code>`. Access and refresh tokens never appear in any URL. |
| GA-FR-20 | `POST /api/auth/oauth/google/exchange` with `{ "code": "…" }` is public. It turns a valid hand-off code into the normal sign-in answer (`AuthResponse`, the same as `POST /api/auth/login`), opening the session with `AuthSessionService.open(user, remember)`. A used, expired or unknown code gets HTTP 400 "This sign-in link has expired. Please try again." |
| GA-FR-21 | New frontend page `/oauth/google/callback`. It reads the fragment, clears it from the address bar at once, and then: for `signed-in`, calls `exchange`, stores the session exactly as password sign-in does ("remember me" decides localStorage or sessionStorage) and opens `from` or the home page; for `link`, opens `/oauth/google/link` with navigation state `{ linkToken: code }`, so the existing linking screen works unchanged; for `error`, opens the error screen with the message for that reason. |
| GA-FR-22 | The Sign In page passes its "remember me" choice and the `from` page to `/oauth/google`, which passes them to `authorize-url`. The Sign Up page uses `remember=true`. |

### F. Linking to an existing account

| ID | Requirement |
| --- | --- |
| GA-FR-23 | `POST /api/auth/oauth/google/link` with `{ "linkToken": "…", "password": "…" }` is public. It checks the link token, then the password with the same attempt limits as password sign-in (BRD 10, SEC-07-01). On success it links the identity, records `GOOGLE_LINKED`, and returns the normal sign-in answer. |
| GA-FR-24 | A wrong password counts as a failed sign-in for that email. The link token stays usable until it expires, so the person can try again. A token that is used, expired or bound to another account gets HTTP 400 "This link has expired. Please sign in with Google again." |

### G. Accounts created with Google

| ID | Requirement |
| --- | --- |
| GA-FR-25 | An account created through Google gets a password hash of a random 256-bit secret that nobody knows. The `password_hash` column stays `NOT NULL`, and no password check can ever succeed for that account. To add a password, the person uses "Forgot password" (BRD 10), which already works for any email. |
| GA-FR-26 | "Change password" for an account that has never had a real password should explain: "You signed up with Google. Use 'Forgot password' to set one." (Q3). |
| GA-FR-27 | The Account page should show "Google: connected as name@gmail.com" for linked accounts. |

### H. Data

| ID | Requirement |
| --- | --- |
| GA-FR-28 | New table `external_identities`: `id`, `user_id` (references `app_users`), `provider` (`GOOGLE`), `subject` (Google `sub`), `email_at_link`, `last_email`, `linked_at`, `last_used_at`, `created_at`, `updated_at`; unique on (`provider`, `subject`) and on (`user_id`, `provider`). |
| GA-FR-29 | New table `oauth_login_attempts`: `id`, `state_hash` (unique), `code_verifier` (encrypted with the existing `SecretCipher`), `nonce`, `remember_me`, `return_path`, `expires_at`, `used_at`, `created_at`. |
| GA-FR-30 | New table `oauth_handoffs`: `id`, `code_hash` (unique), `kind` (`SIGN_IN` or `LINK`), `user_id`, `provider_subject`, `remember_me`, `return_path`, `expires_at`, `used_at`, `created_at`. |
| GA-FR-31 | Expired attempts and hand-offs are deleted daily, in the same way old sessions are (`AuthSessionService.deleteOldSessions`). |
| GA-FR-32 | "Delete my account" (`AccountService.deleteAccount`) and the account data export include Google identities: the export shows provider, email and link date (never the `sub`), and deletion removes the link. "Delete my account" asks for the password today, which a Google-only account does not have; until Q3 is decided, the delete screen tells such a person to set a password with "Forgot password" first. |

### I. Security log

| ID | Requirement |
| --- | --- |
| GA-FR-33 | New security events: `GOOGLE_SIGN_IN_SUCCEEDED`, `GOOGLE_SIGN_IN_FAILED` (with a reason code, never the token), `GOOGLE_LINKED`, plus `REGISTERED` with detail "google". They appear in the admin security log (BRD 10, SEC-09). |
| GA-FR-34 | Repeated failed Google callbacks or exchanges from one network raise the existing security alert, using the same thresholds as failed sign-ins. |

## Security requirements

| ID | Requirement | Why |
| --- | --- | --- |
| GA-SEC-01 | Authorization code flow with PKCE (S256); the client secret stays on the server | The standard safe flow for a web app with a backend |
| GA-SEC-02 | `state`: random, 10 minutes, single use, stored only as a hash | Stops a forged callback (login CSRF) |
| GA-SEC-03 | `nonce` checked in the ID token | Stops a replayed ID token |
| GA-SEC-04 | ID token verified with Google's keys, issuer, audience and expiry before any use | Nothing from Google is trusted unchecked |
| GA-SEC-05 | Only a verified Google email counts (`email_verified = true`) | An unverified email could belong to someone else |
| GA-SEC-06 | No automatic linking to an existing password account; the password must be confirmed once | Stops account takeover through a Google account with the same email |
| GA-SEC-07 | Tokens never in URLs; the one-time hand-off code goes in the URL fragment and lives 60 seconds | Keeps tokens out of logs, history and Referer headers |
| GA-SEC-08 | `from` accepts only in-app paths | No open redirect |
| GA-SEC-09 | Rate limits on `authorize-url`, `callback`, `exchange` and `link`, per network | Abuse and brute-force protection, consistent with BRD 10 |
| GA-SEC-10 | All OAuth answers keep the strict API security headers; the callback redirect adds `Referrer-Policy: no-referrer` | No leakage on the redirect |
| GA-SEC-11 | Calls to Google have short time-outs (5 seconds) and fail closed with a friendly error | Google being slow never hangs sign-in |
| GA-SEC-12 | The Google client secret is a secret like `JWT_SECRET`: set in the Render dashboard (`sync: false`), never committed | Secret management (BRD 10, SEC-04) |

## Non-functional requirements

| ID | Requirement |
| --- | --- |
| GA-NFR-01 | Password sign-in, refresh, sign-out, password reset and every existing test behave exactly as before. |
| GA-NFR-02 | Tests never call Google. A local stub (like `ClaudeStubServer`) serves the token endpoint and the key set, and signs test ID tokens with a test key. Both test suites pass with outside network access off. |
| GA-NFR-03 | Backend coverage stays at or above the BRD 12 minimums; the SonarQube quality gate stays green. |
| GA-NFR-04 | BRD 13 rules apply: the new code passes `EffectiveJavaRulesTest` (injected `Clock`, no public null returns, records copy their collections) and compiles without warnings. |
| GA-NFR-05 | All new screens and messages exist in English and Khmer. |
| GA-NFR-06 | The whole Google round trip takes under 3 seconds of our own server time (the person's time at Google is not counted). |
| GA-NFR-07 | Stack: one new library, for checking Google's token, needs approval (Q1). The code exchange uses Spring's built-in `RestClient`. |

## Implementation phases

Following `CLAUDE.md`, work stops at the end of each phase for confirmation.

| Phase | Name | Requirements | Exit check |
| --- | --- | --- | --- |
| 1 | Setup and data | GA-FR-01, 02, 04, 28–31; Google Cloud setup guide (appendix); library decision (Q1) | Migration runs on H2 and PostgreSQL; feature off without settings (503); guide reviewed |
| 2 | Start and Google's answer | GA-FR-05–13; GA-SEC-01–05, 08, 11; local Google stub | Callback verifies a stub-signed ID token; every bad token, state or nonce is refused (tests) |
| 3 | The three outcomes and linking | GA-FR-14–20, 23–25; GA-SEC-06, 07 | Flow tests for linked, link-needed, new and disabled accounts; hand-off and link tokens single-use |
| 4 | Frontend | GA-FR-21, 22, 03, 26, 27; Khmer text | Page tests for each fragment result; manual run against a real Google test client locally |
| 5 | Hardening and docs | GA-FR-32–34; GA-SEC-09, 10, 12; README, `docs/API.md`, `docs/SECURITY.md`, `docs/USER_GUIDE.md` | Security review checklist passes; coverage and quality gate green |
| 6 | Production rollout | Render variables, Google consent screen published, production redirect URI | End-to-end sign-in, sign-up and linking on production with a real Google account; security log shows the events |

## Acceptance criteria

| ID | Criterion |
| --- | --- |
| GAC-01 | A new person can sign up with Google and lands on the home page, signed in, with no password asked. |
| GAC-02 | A person whose Google account is linked signs in with Google in one step, and "remember me" decides whether the session survives closing the browser. |
| GAC-03 | A person with a password account who uses Google with the same email is asked for their password once; after that, Google signs them straight in. |
| GAC-04 | A wrong password on the linking screen links nothing, counts toward the sign-in lock, and can be retried until the link token expires. |
| GAC-05 | A forged, expired or reused `state`, hand-off code or link token is refused (automated tests). |
| GAC-06 | An ID token with a wrong signature, audience, issuer or nonce, or with `email_verified=false`, is refused (automated tests). |
| GAC-07 | No access or refresh token appears in any URL, server log line or Render log during the flow. |
| GAC-08 | A disabled account cannot sign in with Google. |
| GAC-09 | With the Google settings removed, the feature answers 503 and password sign-in works normally. |
| GAC-10 | Pressing Cancel at Google returns the person to a clear message with "Try again" and "Use password". |
| GAC-11 | The security log shows Google sign-ins, failures and links; account export and deletion include the Google link. |
| GAC-12 | All tests pass with network access off; coverage minimums and the SonarQube quality gate pass; every new screen is available in Khmer. |

## Assumptions, dependencies and risks

### Assumptions

- Participants' Google accounts have verified email addresses (true for Gmail and most Google Workspace accounts).
- The frontend stays on its own domain and the backend on another (Render); the flow above works across domains because the callback goes straight to the backend.

### Dependencies

- A Google Cloud project with an OAuth client of type "Web application" and a published consent screen (appendix).
- Product owner decisions Q2–Q6; Engineering lead decision Q1.
- The Render environment variables in GA-FR-01.

### Risks

| Risk | Impact | Mitigation |
| --- | --- | --- |
| Consent screen left in "Testing" mode | Only listed test users can sign in (Google limits this to 100) | Publish the app before launch (appendix, step 4); the basic scopes need no Google review |
| Wrong redirect URI in Google Cloud | Google shows `redirect_uri_mismatch` and nobody can sign in | Exact URIs listed in the appendix; Phase 6 checklist |
| Google changes its signing keys | Verification fails if keys are cached forever | Keys are re-fetched when an unknown key ID appears (GA-FR-12) |
| Render free plan sleeps | The first sign-in after a quiet period waits for the backend to wake | Existing behaviour; the loading screen covers it |
| Someone owns both a Gmail and a different password-account email | Two separate accounts | Expected; linking only joins accounts with the same email. Merging accounts is out of scope |
| Shared phones | The wrong Google account is picked | `prompt=select_account` always shows the account chooser |

## Open questions

| ID | Question | Owner |
| --- | --- | --- |
| Q1 | Library for checking Google's ID token: `spring-security-oauth2-jose` (Spring's own, version managed by Spring Boot, recommended) or Google's `google-api-client`? Both are outside the `CLAUDE.md` list. | Engineering lead |
| Q2 | Should signing up with Google accept a cohort join code (V9, D-7), for example from a join link, or does the participant enter the code afterwards? | Product owner |
| Q3 | May a Google-only account set a password from the Account page, without "Forgot password"? | Product owner |
| Q4 | Who owns the Google Cloud project and its consent screen (name, logo, support email, privacy policy link)? | Sponsor |
| Q5 | When Google sign-in is off, hide the button (GA-FR-03) or keep it and show the "not set up" message? Recommendation: hide it. | Product owner |
| Q6 | Is "Disconnect Google" needed in V14, or later? It needs a rule for accounts that have no password. | Product owner |

## Appendix A – Google Cloud setup (for the project owner)

1. Go to <https://console.cloud.google.com/>, create or choose a project (for example "Vision Mapping").
2. **APIs & Services → OAuth consent screen**: user type **External**; app name "Vision Mapping"; support email; app domain and privacy policy link (Q4); scopes `openid`, `email`, `profile` only.
3. **APIs & Services → Credentials → Create credentials → OAuth client ID**, type **Web application**:
   - **Authorised JavaScript origins:** `https://vision-mapping-frontend.onrender.com`, `http://localhost:5173`.
   - **Authorised redirect URIs:** `https://vision-mapping-backend.onrender.com/api/auth/oauth/google/callback`, `http://localhost:8080/api/auth/oauth/google/callback`.
   - Use the real Render addresses if Render added a suffix to the service names.
4. While testing, add test users on the consent screen. Before launch, press **Publish app**. The basic scopes need no Google security review.
5. Copy the **Client ID** and **Client secret**. In Render, on **vision-mapping-backend → Environment**, set:
   - `GOOGLE_CLIENT_ID` = the client ID
   - `GOOGLE_CLIENT_SECRET` = the client secret (never commit it)
   - `GOOGLE_REDIRECT_URI` = `https://vision-mapping-backend.onrender.com/api/auth/oauth/google/callback`
   - `FRONTEND_URL` = `https://vision-mapping-frontend.onrender.com`
6. Save and deploy. Local development uses the same variables, with the localhost addresses.

## Appendix B – Endpoint summary

| Method and path | Access | Answer |
| --- | --- | --- |
| `GET /api/auth/oauth/google/status` | Public | `{ "enabled": boolean }` (should) |
| `GET /api/auth/oauth/google/authorize-url?remember&from` | Public | `{ "url": "…" }`, or 503 when off |
| `GET /api/auth/oauth/google/callback?code&state` (or `?error=…`) | Public, called by Google | 302 to `{FRONTEND_URL}/oauth/google/callback#result=…` |
| `POST /api/auth/oauth/google/exchange` `{ code }` | Public | `AuthResponse` (as `/api/auth/login`), or 400 |
| `POST /api/auth/oauth/google/link` `{ linkToken, password }` | Public | `AuthResponse`, 400, or 429 when locked |

## Sign-off

| Role | Name | Date | Decision |
| --- | --- | --- | --- |
| Sponsor | | | |
| Product owner | | | |
| Engineering lead | | | |
| Security lead | | | |
| QA lead | | | |

## Sources

- Code review at commit `bceea65` (8 Nov 2026): `frontend/src/api/oauthApi.ts`, `frontend/src/pages/OAuthGoogleStartPage.tsx`, `OAuthGoogleErrorPage.tsx`, `AccountLinkingPage.tsx`, `LoginPage.tsx`, `RegisterPage.tsx`; `backend/…/service/AuthService.java`, `AuthSessionService.java`, `entity/AppUser.java`, `config/SecurityConfig.java`.
- `docs/VMS_BRD_V10.0.0.md` – sessions, sign-in limits, security log and headers.
- `docs/VMS_BRD_V13.0.0.md` – Java coding rules that the new code must follow.
- Google's OpenID Connect documentation for web server apps (authorization code flow, PKCE, ID token validation).
- `CLAUDE.md` – stack, working rules and Core Business Rules 13 and 14 (user data scoped to its owner).
