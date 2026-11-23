# Threat model

BRD 10 SEC-06-01, completed by BRD 21 Phase 5 (TF-FR-43), 22 Nov 2026, at the code of V21 Phase 4; section 6 added by
BRD 23 phase 7, 23 Nov 2026. Update this file
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

## 6. User and role management (V23)

**Data flow.** Admin or custom role → `/api/admin/users/**`, `/api/admin/roles/**` → the role's permissions, read from
`role_permissions` on each request (cached, cleared after each change commits) → no-escalation check → fresh
"Verify it's you" code → change, security event, email notice. Invitation: admin → `user_invitations` (SHA-256 of a
256-bit token, 7 days) → email → public `POST /api/auth/invitation/accept` → password set → session.

| Threat | Control |
| --- | --- |
| E: a user manager giving themselves or others more power | D-23-9: the actor must hold every permission of the target's role and of the role given; only Admins act on Admins, give the Admin role or edit roles; *Manage roles* is never on another role; `AdminUserActionsFlowTests`, `RoleFlowTests`, `PermissionFlowTests` |
| E: locking everyone out | Nobody acts on their own account; the last active admin cannot be demoted, blocked or deleted; Admin and Participant roles are locked |
| E: the first-admin setting reused later | `ADMIN_BOOTSTRAP_EMAIL` acts only while no active admin exists, once, with an alert |
| S: a stolen admin session making changes | Each role change, block, unblock, delete and role edit needs a fresh emailed code; alerts for the Admin role granted, an Admin blocked or deleted, sensitive permissions given, and more than 5 blocks or deletions in an hour; 30 changes per person per 10 minutes |
| S: a guessed or leaked invitation link | 256 bits, only the hash stored, single use, 7 days, replaced on resend; 20 attempts per network per 15 min; a blocked account cannot accept |
| I: admins reading people's plans | D-23-3: answers carry account details and record counts only; a planted-text test and a type guard on every admin answer |
| T: deletion leaving data or half-done | Only a blocked account; the email typed; the self-deletion erase routine in one transaction; any failure erases nothing |
| R: denying a change | Every change is logged with the actor, target and reason; a deleted account's entry keeps its id only |

**Abuse cases.** UMC-11 to UMC-19 in [UAT_V23.md](UAT_V23.md): a user manager giving the Admin role or a role with
permissions they lack, acting on an Admin, making a role with every permission; an Admin changing the Admin role or
themselves; the bootstrap setting on a system with admins; reading plans through admin endpoints; a stolen session
blocking many accounts.

## 7. Sign-in method settings (V24)

**Data flow.** Admin → `PUT /api/admin/security-access` (*Manage roles*, reason, fresh code) → `security_settings`
(one row) → read from the database at every sign-in (no cache) → password sign-in, sign-up, invitation acceptance,
Google sign-in, sign-up and linking, and the two-step second step refuse a method that is off for non-Admins →
sessions opened with a method switched off are ended (`auth_sessions.sign_in_method`). Public
`GET /api/auth/sign-in-methods` tells the sign-in pages what to offer.

| Threat | Control |
| --- | --- |
| E: a non-Admin weakening everyone's sign-in | Changes need *Manage roles*, which only the Admin role holds (D-23-9, D-24-2); *View security log* reads only; `SecurityAccessFlowTests` |
| D: locking everyone, or every admin, out | Admins are never affected by the switches (D-24-5); at least one method stays on; password cannot be turned off without Google set up; a change that leaves any active non-Admin account with no way in is refused with the count (D-24-6) |
| S: a stolen admin session turning methods off | Fresh "Verify it's you" code, reason, 30 changes per person per 10 minutes, an alert for every change |
| I: the setting revealing which emails exist | With password sign-in off, only the right password gets the "turned off" answer; wrong passwords keep the generic answer and still count towards the lock |
| E: a session opened before the switch outliving it | Sessions remember their method and are ended at once (next request); the setting is checked again at the Google code exchange and the two-step code |
| R: denying a change | `SECURITY_ACCESS_CHANGED` with before and after values, sessions ended, set-up dates given or cleared, and the reason |

**Abuse cases.** SAC-09 to SAC-14 in [UAT_V24.md](UAT_V24.md).

## Residual risks

| Risk | Why accepted or open | Owner |
| --- | --- | --- |
| A refresh token or device token in browser storage could be read by a script that runs in the page | The SPA and API are on different domains, so an HttpOnly cookie would be a third-party cookie that browsers block; mitigated by `script-src 'self'`, no inline script, React escaping, and rotation with reuse detection (ASVS F-03) | Product owner to accept |
| Registration says when an email is already registered | Clear feedback for people signing up; limited to 10 registrations per network per hour (ASVS F-02) | Product owner to accept |
| Database and Redis connections inside Render have no TLS of their own | Traffic stays on Render's private network, which is not reachable from the internet. Rate-limit and lockout counts moved to Redis on 22 Nov 2026, so they now survive restarts; while Redis is down they fall back to memory (ASVS F-04) | Product owner to accept |
| Email is not confirmed at sign-up | Planned in a separate small BRD (BRD 20 Q-06) (ASVS F-01) | Follow-up BRD |
