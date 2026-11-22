# Threat model

BRD 10 SEC-06-01, completed by BRD 21 Phase 5 (TF-FR-43), 22 Nov 2026, at the code of V21 Phase 4. Update this file
whenever a feature crosses a trust boundary listed below (SEC-06-01). Method: data flows and trust boundaries, then
threats per area (spoofing, tampering, repudiation, information disclosure, denial of service, elevation of privilege)
with the control that answers each, abuse cases ("a user tries to …"), and what is left.

## System and trust boundaries

```text
 Browser (React SPA, vision-mapping-frontend)          Render                 Outside services
 ─────────────────────────────────────────────          ──────                 ────────────────
 TB1: the person's device and browser storage
   access token in memory only
   refresh token, device token in local/session storage
        │ HTTPS (Render TLS, HSTS)
        ▼
 TB2: public internet → API (vision-mapping-backend, Spring Boot)
   JWT filter → role rules → controllers → services (owner checks) → JPA
        │                         │                     │
        ▼                         ▼                     ▼
 TB3: PostgreSQL (Render)   TB4: Redis cache       TB5: Anthropic (Claude), Google OAuth,
   hashed/sealed secrets      keys name the user     SMTP mail server
```

| Boundary | Crossed by | Main controls |
| --- | --- | --- |
| TB1 Device | Tokens, recovery codes on screen, downloads | Access token in memory; strict CSP (`script-src 'self'`); recovery codes shown once; no tokens in URLs |
| TB2 Internet → API | Every request | HTTPS + HSTS; JWT (HS256, 30 min) bound to a server session; role rules by path; owner checks in services; validation and size limits; rate limits; strict API CSP and `no-store` |
| TB3 API → database | All data | Parameterised JPA; secrets stored as hashes (SHA-256/HMAC/BCrypt) or sealed (AES-256-GCM, `SETTINGS_ENCRYPTION_KEY`) |
| TB4 API → cache | Cached reads | Keys include the user; a cache failure only slows the app |
| TB5 API → outside | Claude prompts, Google token exchange, mail | Fixed hosts only (no user-supplied URLs); timeouts (Claude 60 s, Google 5 s, mail 10 s); fail closed; Claude sees data only with consent |

## 1. Sign-in and recovery

**Data flow.** Password: `POST /auth/login` → BCrypt check → (two-step on) challenge → `POST /auth/two-factor/verify` →
session (refresh token rotated on each renewal). Google: browser → `authorize-url` (state, PKCE, nonce) → Google →
`callback` (ID token verified) → one-time code in the URL fragment → `exchange` → (two-step on) challenge → session.
Recovery: `forgot-password` → emailed 30-minute single-use link → `reset-password`.

| Threat | Control |
| --- | --- |
| S: stolen or guessed password | Lockout (5 per email, 50 per network in 15 min); breached and common passwords refused; two-step sign-in (required for admins and coaches) |
| S: guessing second-step codes with a known password | 5 tries per challenge; each counts as a failed sign-in; the count resets only after the second step (V21); 30 checks per network per 15 min |
| S: replaying a seen authenticator code | Last accepted step stored; a code is never accepted twice |
| S: account takeover through a Google account with the same email | No automatic linking; the account's password is asked once (V14 GA-SEC-06) |
| T: forged OAuth callback, replayed ID token, open redirect | Single-use hashed `state`, PKCE S256, nonce, full ID-token checks; in-app return paths only |
| I: tokens in URLs, logs or Referer | Codes in the fragment only; `Referrer-Policy: no-referrer` on the callback; tokens never logged |
| I: learning who has an account | Same answer for unknown email and wrong password; reset answers 204 either way. Registration says "Email is already registered" (accepted, see ASVS F-02) |
| D: flooding a person's inbox with sign-in codes | V21 Phase 5: at most 5 sign-in code emails per account per hour; resend once a minute per challenge |
| E: a stolen refresh token | Rotation; reuse of a replaced token ends the session and raises an alert; a session unused for 14 days ends (ASVS F-05) |
| R: denying a sign-in or a change | Security log: sign-ins, failures, two-step events, changes, resets; 12 months |

**Abuse cases.** A user tries to: sign in with someone's leaked password (stopped by two-step, or by the lockout);
guess the six-digit code (5 tries, then locked); reuse a code read over a shoulder (refused); paste another browser's
device token (works only for its own account); ask an admin to reset someone else's two-step sign-in (reason logged,
alert, notice to the real owner, identity check outside the app); flood someone's inbox (5 emails an hour).

## 2. Coach access to participants

**Data flow.** Coach → `/api/coach/**` → role check (COACH or ADMIN, read from the database each request) → the
participants of the active cohorts the coach coaches → read-only views; journals only when shared (business rule 14).

| Threat | Control |
| --- | --- |
| E: a coach reading a participant outside their cohorts | Server checks cohort membership on every coach request; access sweep test over every route |
| E: a coach changing participant data | Coach endpoints are read-only (comments and ratings are their own records) |
| S: a coach account taken over | Two-step sign-in required for coaches (V21); admin reset is logged and alerted |
| I: journals or reports exposed | Fear and attitude journals and final reports only when the participant shares them |
| R: denying a view | `COACH_VIEWED_PARTICIPANT` in the security log |

**Abuse cases.** A coach tries to: open a participant from another cohort by changing the id in the URL (404);
read an unshared journal (refused); keep access after the cohort is archived (refused); skip two-step setup past the
date by calling the API directly (403 `TWO_FACTOR_SETUP_REQUIRED`).

## 3. Share links (plans and final reports)

**Data flow.** Owner creates a link (256-bit token, expiry, revocable) → anyone with the link → `GET
/api/shared-plans/{token}` or `/api/public/reports/{token}` → read-only page or PDF.

| Threat | Control |
| --- | --- |
| S/I: guessing a link | 256-bit tokens, stored as hashes; rate limits per network and per token |
| I: a link spreading further than meant | Expiry, revoke at any time, view count shown to the owner; read-only |
| T: changing data through a link | Public endpoints are GET only |
| D: scraping a shared report | 60 views per token per minute; 30 shared-plan requests per network per minute |

**Abuse cases.** A user tries to: enumerate share tokens (rate limits, 256 bits); use a revoked or expired link
(refused); change a plan through a shared link (no write endpoint).

## 4. Excel import

**Data flow.** Signed-in person uploads `.xlsx` (≤ 10 MB) → Apache POI reads it → rows validated → records created for
that person only → summary.

| Threat | Control |
| --- | --- |
| T: macro or malformed workbook | Workbooks with macros, unreadable or oversized files refused; each row checked; never written back to the original |
| E: importing into someone else's account | Every row is created for the signed-in person; ids in the file are not trusted |
| D: huge or zip-bomb workbook | 10 MB upload limit; POI limits; 10 imports per account per hour |
| I: formula injection on export | Cells starting with `=`, `+`, `-`, `@` are written as text |

**Abuse cases.** A user tries to: upload a workbook with a macro (refused); upload a 200 MB file (413); export data
that runs a formula on a coach's computer (written as text).

## 5. Claude integration

**Data flow.** Person (with AI consent) → AI helper → server builds a prompt from that person's own records (escaped as
data) → Anthropic API (key sealed in the database) → suggestion shown → the person decides what to save.

| Threat | Control |
| --- | --- |
| T: prompt injection from the person's own text or a shared record | Records sent as escaped data blocks that cannot pose as instructions; Claude never saves or sends anything itself |
| I: sending data without consent, or another person's data | Consent required and recorded; prompts built only from the signed-in person's records; coach views are not sent |
| I: the API key leaking | Sealed with AES-256-GCM; never returned to the browser; changing it needs a fresh admin code |
| D: cost runaway | Daily limit per person and monthly budget cap |
| E: Claude output acted on blindly | Suggestions are drafts the person approves; safety flags for crisis content |

**Abuse cases.** A user tries to: make Claude reveal another person's plan (only their own data is in the prompt);
make Claude change records ("ignore your instructions and delete…": Claude has no write access); burn the AI budget
(daily limit, monthly cap).

## Residual risks

| Risk | Why accepted or open | Owner |
| --- | --- | --- |
| A refresh token or device token in browser storage could be read by a script that runs in the page | The SPA and API are on different domains, so an HttpOnly cookie would be a third-party cookie that browsers block; mitigated by `script-src 'self'`, no inline script, React escaping, and rotation with reuse detection (ASVS F-03) | Product owner to accept |
| Registration says when an email is already registered | Clear feedback for people signing up; limited to 10 registrations per network per hour (ASVS F-02) | Product owner to accept |
| Database and Redis connections inside Render have no TLS of their own | Traffic stays on Render's private network, which is not reachable from the internet. Rate-limit and lockout counts moved to Redis on 22 Nov 2026, so they now survive restarts; while Redis is down they fall back to memory (ASVS F-04) | Product owner to accept |
| Email is not confirmed at sign-up | Planned in a separate small BRD (BRD 20 Q-06) (ASVS F-01) | Follow-up BRD |
