# BRD – Two-Factor Sign-in

Nov 22, 2026

## Document control

This is draft 21.0.0. It keeps every requirement in BRD 20.0.0 (Verify it's you), BRD 19.0.0 (Report Management),
BRD 18.0.0 (Management Report), BRD 17.0.0 (Blockers in the Right Place), BRD 16.0.0 (Vision Map Timeline), BRD 15.0.0
(AI Assistant and Helpers), BRD 14.0.0 (Sign in with Google), BRD 13.0.0 (Java coding rules), BRD 12.0.0 (coverage
minimums), BRD 10.0.0 (security) and the earlier BRDs. Where this BRD is silent, the earlier BRDs still apply.

V21 adds **two-factor sign-in**: after the password (or Google), the person proves it is them a second time with a
code from an authenticator app or, for participants who choose it, a code sent by email. It is optional for
participants and required for admins and coaches. V21 also finishes the parts of BRD 10 Phase 7 that are still open:
the threat model (SEC-06-01), abuse cases in acceptance criteria (SEC-06-04) and the final OWASP ASVS Level 2 check.

| Item | Value |
| --- | --- |
| Document | Business Requirements Document (BRD) – Two-factor sign-in and BRD 10 close-out |
| Version | 21.0.0 – Draft |
| Parent documents | BRD 10.0.0 (SEC-07-07 two-factor sign-in, SEC-06-01 threat model, SEC-06-04 abuse cases, SEC-07-01 sign-in lockout, SEC-07-03 sessions, SEC-09 security log), BRD 20.0.0 (email codes, "Verify it's you"), BRD 14.0.0 (Google sign-in), BRD 12.0.0 (coverage), `CLAUDE.md` (business rules 13 and 14) |
| Business owner | To be confirmed |
| Product owner | To be confirmed |
| Approvers | Sponsor, Product owner, Security reviewer |

| Version | Change | Author |
| --- | --- | --- |
| 21.0.0 (phase 5 built) | Phase 5 implemented: `docs/THREAT_MODEL.md` (TF-FR-43) and `docs/ASVS_L2_CHECK.md` (TF-FR-44) with findings F-01 to F-08. Fixed in this phase (TF-FR-45): F-06, a known password could flood a mailbox with sign-in codes (now at most 5 emails per account per hour, `RATE_LIMIT_TWO_FACTOR_EMAIL_*`; over the limit the second step reports that the email could not be sent, which says no more than a mail fault); F-08, the step-up and two-step hash keys fell back to `JWT_SECRET` in production (now `STEP_UP_SECRET` and `TWO_FACTOR_SECRET` generated in `render.yaml`). Accepted-risk candidates for the product owner's signature: F-02 registration reveals a registered email, F-03 tokens in browser storage, F-04 in-memory limits and private-network database traffic on one instance, F-05 no idle timeout on 30-day sessions. Follow-ups dated 31 Jan 2027: F-01 email confirmation at sign-up (the BRD 20 Q-06 BRD), F-07 a signed-in devices list. TF-FR-46: the abuse-case rule is in `SECURITY.md`; adding it to `CLAUDE.md` waits for the product owner (D-21-7). The ASVS check describes control areas per chapter; the reviewer ticks the official requirement numbers at sign-off | Dev |
| 21.0.0 (phase 4 built) | Phase 4 implemented. Clarified during build: set-up-by dates are given at start-up to admins and coaches without two-step sign-in (14 days, `TWO_FACTOR_GRACE_DAYS`) and to someone made a coach; participants have none. Past the date an interceptor answers 403 `TWO_FACTOR_SETUP_REQUIRED` to every API call except two-step sign-in, "Verify it's you", `/api/auth/*`, the sign-in methods, interface text, health and appearance; the page then opens the Security page. "Skip for now" is logged (`POST /api/account/two-factor/skip`) and asked once per browser session. The admin page **Two-step sign-in** lists admins, coaches and anyone with it on, with **Reset** (reason required, the admin's own fresh code under the new step-up action `ADMIN_TWO_FACTOR_RESET`, never the admin's own account); a reset ends the person's sessions, emails them, raises an alert and gives an admin or coach 3 days. TF-FR-40 built: "Verify it's you" accepts a current authenticator code. The export gains a `twoFactor` section. Docs: `API.md`, `DATA_MODEL.md`, `USER_GUIDE.md`, `SECURITY.md` (identity check before a reset, pending Q-09), README, `UAT_V21.md`, `km-review-v21.csv` | Dev |
| 21.0.0 (phase 3 built) | Phase 3 implemented (migration `V88__two_factor_pending_setup.sql`). Clarified during build: a new authenticator waits as a pending secret until its first code is confirmed, so switching phones or methods never turns 2FA off half way; choosing the email method needs the fresh "Verify it's you" code, which is itself a code sent to the account email and typed back, so no second code is sent (TF-FR-05); **email-method participants get recovery codes too**, so a mail outage cannot lock them out; one new step-up action `TWO_FACTOR_SETTINGS` (fresh code, 10 minutes) covers turning on, switching, new codes and turning off; a notice email follows each change. Frontend: the second-step page `/sign-in/two-factor` after password, Google sign-in and linking (remember this device, recovery code, new email code after 60 seconds, lost-device help); the Security page panel (QR code with the `qrcode` library and the key in groups of four, confirm code, recovery codes with Download, Copy and a required "saved" tick, switch method, new codes, turn off, remembered devices); the browser keeps the device token in local storage and sends it only in sign-in request bodies. 65 Khmer entries. TF-FR-40 (authenticator code in "Verify it's you") moves to phase 4 | Dev |
| 21.0.0 (phase 2 built) | Phase 2 implemented. Clarified during build: login, Google `exchange` and Google `link` answer either the session or `{ "status": "TWO_FACTOR_REQUIRED", "challengeToken", "method", "sentTo"?, "emailUnavailable", "expiresAt" }` (one sealed `SignInAnswer` type); the session answer gains an optional `deviceToken`. A correct password no longer resets the sign-in failure count while a second step is pending; only a finished sign-in does, so a known password cannot be used to guess codes without limit. Wrong codes return 400 `TWO_FACTOR_WRONG_CODE` with `triesLeft`; an unknown, expired, used or ended challenge returns 410 `TWO_FACTOR_ENDED`. The email sign-in code reuses the V20 mailer (English until the language is stored). Rate limits: 30 code checks per network per 15 minutes, one new email code per challenge per minute. Password change and reset remove trusted devices. The security log page can filter the new events. No page asks for the second step yet (phase 3) | Dev |
| 21.0.0 (phase 1 built) | Phase 1 implemented (migration `V87__two_factor_sign_in.sql`). Clarified during build: `two_factor_settings` is keyed by the account id, which the code sets directly (the account link is read-only); authenticator codes follow RFC 6238 with the RFC's Appendix B vectors as tests, and Base32 keys typed by hand may include spaces, dashes and lower case; recovery codes are shown as `ABCDE-FGHJK` from a 31-letter alphabet without 0/O or 1/I/L; short codes are kept as HMAC-SHA256 with `TWO_FACTOR_SECRET` (default `JWT_SECRET`), with a "two-factor:" prefix so they never equal a V20 step-up hash; the daily clean-up (03:55) removes unfinished setups after 15 minutes, challenges a day after they end, and devices a week after they expire or are removed. Signing in does not change yet | Dev |
| 21.0.0 (questions answered) | Product owner answers, 22 Nov 2026: Q-01 coaches must use 2FA too; Q-02 the frontend may add a QR code library; Q-03 participants may use the email code; Q-05 an admin may reset another person's 2FA with the D-21-5 safeguards. D-21-1, D-21-2, D-21-5 and D-21-6 approved. Q-04 and Q-06 to Q-09 stay open with their draft answers | BA |
| 21.0.0 | First draft: terms, current state at commit `9f924d2`, proposed decisions D-21-1 to D-21-7, requirements TF-FR-01 to TF-FR-46, non-functional requirements, data and API, phases, acceptance criteria with abuse cases, and open questions | BA |

## Executive summary

Today one secret opens an account: the password (or, since V14, the Google account). Anyone who learns it, through a
reused password, a phishing page or a shoulder-surfed screen, can sign in from anywhere and see everything the
person has written. For a **coach**, that includes every participant they coach. For an **admin**, it includes roles,
the Claude key and every cohort. V20 protects a few risky *actions* with an email code, but signing in itself still
needs only one secret.

V21 asks for a **second proof at sign-in**:

- **Authenticator app** (Google Authenticator, Microsoft Authenticator, Authy and similar): a 6-digit code that changes
  every 30 seconds. This is the strong option, and it is the only option for admins and coaches.
- **Email code**: the same 6-digit email code V20 already sends. It is easier for participants without an
  authenticator app, but it is only as strong as their email account.
- **Recovery codes**: 10 single-use codes shown once at setup, for a lost phone.
- **Remember this device for 30 days**, so a person on their own phone is not asked every time.

Participants choose whether to turn it on. Admins and coaches must set it up within 14 days of V21 or of getting the
role (D-21-2).

### Terms used in this BRD

| Term | Meaning in this BRD |
| --- | --- |
| Two-factor sign-in (2FA, MFA) | Signing in needs a second proof after the first one (password or Google) |
| First factor | The password, or the Google account (BRD 14) |
| Second factor | An authenticator code, an email code or a recovery code |
| Authenticator app / TOTP | An app that shows a 6-digit code that changes every 30 seconds, made from a secret shared once through a QR code (RFC 6238, "time-based one-time password") |
| Recovery code | A single-use code given at setup, for when the authenticator is lost |
| Sign-in challenge | The short-lived step between the first factor and the second; no session exists yet |
| Trusted device | A browser the person chose to remember for 30 days, so the second step is skipped there |
| Step-up verification | BRD 20's "Verify it's you" before risky actions; unchanged except TF-FR-40 |
| Grace period | The days an admin or coach may still sign in without 2FA before setup becomes compulsory |

## Business objectives

| ID | Objective | Measure |
| --- | --- | --- |
| TBO-1 | A stolen password alone cannot open an account that uses 2FA | No sign-in completes without a valid second factor or a trusted device (automated tests per sign-in path) |
| TBO-2 | Admin and coach accounts, which can see other people's data, are protected by an authenticator | 100% of active admins and coaches have an authenticator set up 14 days after release (admin report) |
| TBO-3 | People do not get locked out | Every 2FA account has recovery codes or email; a lost-device path exists (TF-FR-27 to TF-FR-31); support requests tracked in UAT |
| TBO-4 | 2FA does not make daily use tiresome | "Remember this device" works for 30 days; median second step under 20 seconds in UAT |
| TBO-5 | BRD 10 is finished | Threat model, abuse cases and the ASVS Level 2 check are complete, and their findings are fixed or accepted (Phase 5) |

## Scope

### In scope

- A second step at sign-in after the password and after Google sign-in (BRD 14).
- Setting up, changing and turning off 2FA on the Security page; recovery codes; trusted devices.
- Methods: authenticator app (TOTP) for everyone; email code for participants (D-21-1).
- Compulsory authenticator for admins and coaches, with a 14-day grace period (D-21-2).
- Admin reset of a person's 2FA after a lost device, with safeguards (D-21-5).
- Security log events, alerts, rate limits, notices and Khmer text.
- BRD 10 close-out: `docs/THREAT_MODEL.md`, abuse cases as a standing BRD rule, and `docs/ASVS_L2_CHECK.md` with
  the findings fixed or accepted.

### Out of scope

- SMS codes. They are weaker (SIM swap), cost money per message, and need a provider.
- Passkeys / WebAuthn. These are the strongest option and are proposed for a later BRD (Q-08).
- 2FA for the public share and report links (V9, V19). They carry no account and keep their token rules.
- Confirming the email address at sign-up (BRD 20 Q-06; a separate small BRD).
- Changing the login email address (not a feature today).
- "Disconnect Google" (BRD 14 Q6).

## Current state (22 Nov 2026, commit `9f924d2`)

| Area | Today |
| --- | --- |
| Password sign-in | `POST /api/auth/login` checks the password and at once returns the sign-in answer (`token`, `refreshToken`, user, appearance) from `AuthSessionService.open(user, rememberMe)` |
| Google sign-in (V14) | The callback hands the browser a 60-second code; `POST /api/auth/oauth/google/exchange` returns the same sign-in answer. Linking to a password account asks for the password once |
| Lockout | `LoginAttemptLimiter`: 5 wrong passwords for one email in 15 minutes lock it for 15 minutes; 50 failures from one network |
| Sessions | `auth_sessions`; 30-minute access token in memory; rotating refresh token in local or session storage ("remember me"); password change and reset end other sessions |
| Email codes (V20) | `StepUpService`: 6-digit codes, HMAC only, 10 minutes, 5 tries, 5 per hour, 20 per day. **Bound to a session**, so they cannot be used before a session exists |
| Mail | Real email only with `MAIL_HOST`; in local and test runs the code goes to the server log |
| Secrets at rest | `SecretCipher` (AES-256-GCM, `SETTINGS_ENCRYPTION_KEY`) already seals the Claude key and the PKCE verifier |
| Roles | `USER`, `COACH`, `ADMIN`. The role is read from the database on every request. A coach may read the participants of the cohorts they coach (business rule 14) |
| Security page | Change password (V20 code), plus "Sign-in methods" on the Account page (V14). No 2FA settings |
| BRD 10 Phase 7 | Still open: two-factor sign-in (SEC-07-07), threat model (SEC-06-01), abuse cases (SEC-06-04), final ASVS Level 2 check |

## Decisions proposed by this BRD

These are the BA's recommendations. D-21-1, D-21-2, D-21-5 and D-21-6 were approved by the product owner on
22 Nov 2026 (Q-01, Q-02, Q-03, Q-05). D-21-3, D-21-4 and D-21-7 follow the draft answers to the questions still open.

| ID | Decision | Effect on `CLAUDE.md` |
| --- | --- | --- |
| D-21-1 | **Methods.** Everyone may use an authenticator app. Participants may choose an email code instead. Admins and coaches must use an authenticator app, because their email is a weaker second factor than a separate device and their accounts reach other people's data. Every authenticator user gets 10 recovery codes. | None |
| D-21-2 | **Who must use it.** Optional for participants (`USER`). Required for `COACH` and `ADMIN`: they may skip setup for **14 days** from V21's release or from the day they get the role; after that they must set it up before they can use the app. | Add one line under "Authentication and Authorization" if the product owner asks |
| D-21-3 | **Every sign-in path.** Once 2FA is on, it is asked after the password **and** after Google sign-in, including after linking. The app cannot know whether the person's Google account has its own second factor, so it does not assume it. | None |
| D-21-4 | **Remember this device.** Optional, 30 days, one browser. It ends early on a password change or reset, turning 2FA off, an admin reset, removing the device from the list, or signing out with "forget this device". It is never offered on the Google linking screen. | None |
| D-21-5 | **Lost device.** First use a recovery code. An email-method participant can use the email. Without either, an **admin may reset** the person's 2FA after confirming who they are outside the app (for example, a call to a known number). The reset needs the admin's own fresh "Verify it's you" code, is logged, raises a security alert and emails the person. For an admin or coach it restarts their 14-day grace period at 3 days. An admin cannot reset their own 2FA. This is not verifying on someone's behalf (D-20-5 stands): the person must still pass their first factor and set up 2FA again. | Note beside business rule 14: an admin may reset, never read or use, another person's second factor |
| D-21-6 | **No new backend library.** TOTP (RFC 6238, HMAC-SHA1, 6 digits, 30 seconds) is built on the JDK's `javax.crypto`, as the V20 codes are. The setup QR code uses one small frontend library (approved, Q-02), with the setup key as text beside it. | None |
| D-21-7 | **BRD 10 close-out in the same version.** The threat model and ASVS Level 2 check are written in Phase 5, and every later BRD includes at least one abuse case in its acceptance criteria (SEC-06-04). | Add "acceptance criteria include at least one abuse case" to the BRD working rules if the product owner asks |

## Target experience

1. **Dara (coach)** signs in after V21 ships. A screen says: "Coach accounts need two-factor sign-in. Set it up now, or
   skip for now (13 days left)." She chooses **Set up now**, scans a QR code with Microsoft Authenticator, types the
   6-digit code it shows, and saves her 10 recovery codes (download or copy). From then on, signing in asks for the
   authenticator code after her password.
2. **Sophea (participant)** opens **Security → Two-factor sign-in** and chooses **Email code**. From then on, signing in
   on a new browser emails her a code. On her own phone she ticks **Remember this device for 30 days**.
3. **Vanna** loses his phone. On the second step he chooses **Use a recovery code**, signs in, and is reminded he has
   9 codes left and should set up his new phone.
4. **Chan (admin)** gets a call from a coach who lost both her phone and her recovery codes. Chan checks it is really
   her, opens **People → Reset two-factor**, confirms with his own code, and the coach is asked to set up again at her
   next sign-in.

## Functional requirements

Priority: **M** Must, **S** Should, **C** Could.

### A. Setting up

| ID | Requirement | Priority |
| --- | --- | --- |
| TF-FR-01 | **Security → Two-factor sign-in** shows the status (Off, Authenticator app, Email code), when it was turned on, recovery codes left, and the trusted devices. | M |
| TF-FR-02 | **Set up an authenticator.** The server makes a 160-bit secret and returns it with an `otpauth://` address (issuer "Vision Mapping", the account email as the label). The page shows it as a QR code (D-21-6) and as a grouped text key to type in by hand. | M |
| TF-FR-03 | Setup finishes only after the person types a current code from the app. Until then nothing changes, and an unfinished setup is discarded after 15 minutes. | M |
| TF-FR-04 | On finishing, the page shows **10 recovery codes** (10 characters each, from an alphabet without look-alike characters) **once**, with **Download** (text file), **Copy** and a required "I have saved these codes" tick. | M |
| TF-FR-05 | **Choose email code** (participants only, D-21-1): turning it on sends a code to the account email, which must be typed back. Not offered when mail is not set up (TF-FR-38). | M |
| TF-FR-06 | Turning 2FA on, changing the method, making new recovery codes, and turning it off are **protected actions**: each needs a fresh BRD 20 "Verify it's you" code (new `StepUpAction` values, rule FRESH). An authenticator user may use the authenticator code there (TF-FR-40). | M |
| TF-FR-07 | **New recovery codes** replace all old ones. The old ones stop working at once. | M |
| TF-FR-08 | **Turn off** is not offered to admins and coaches once their grace period has ended (D-21-2). For a participant it removes the secret, the recovery codes and every trusted device. | M |
| TF-FR-09 | After each change in A, a notice goes to the account email (as BRD 20 SV-FR-28): what changed, when, which browser, and "If this was not you, change your password and contact support." | M |

### B. The second step at sign-in

| ID | Requirement | Priority |
| --- | --- | --- |
| TF-FR-10 | When the first factor succeeds for an account with 2FA on, and the browser is not a trusted device, the server **does not open a session**. It returns a **sign-in challenge** instead: a random token, the method, the masked email for the email method, and the expiry. | M |
| TF-FR-11 | The challenge lasts **5 minutes**, belongs to one account, can complete once, and remembers the "remember me" choice and the page to return to. | M |
| TF-FR-12 | The second-step page asks for the 6-digit code, with `autocomplete="one-time-code"` and a numeric keyboard on phones, plus **Use a recovery code** and, for the email method, **Send a new code** (after 60 seconds). | M |
| TF-FR-13 | **Authenticator codes** are accepted for the current 30-second step and one step either side (clock drift). A code (time step) that has already been used is refused, so a seen code cannot be replayed. | M |
| TF-FR-14 | **Email codes** at sign-in follow BRD 20's code rules (6 digits, 10 minutes, once, HMAC only, 5 per hour) but belong to the sign-in challenge instead of a session. | M |
| TF-FR-15 | **Recovery codes** each work once. Using one shows how many are left, emails a notice, and suggests making new ones when 3 or fewer are left. | M |
| TF-FR-16 | **5 wrong codes** end the challenge, and each one counts as a failed sign-in for that email in `LoginAttemptLimiter` (BRD 10 SEC-07-01). The person starts again from the password. | M |
| TF-FR-17 | Only a correct second factor opens the session, with `AuthSessionService.open(user, rememberMe)`. The answer is the same as password sign-in today. | M |
| TF-FR-18 | The same second step follows **Google sign-in** (D-21-3): `exchange` and `link` return a challenge instead of tokens when 2FA is on. | M |
| TF-FR-19 | Wrong passwords answer exactly as today, so the second step never reveals whether an account has 2FA unless the password was right. | M |
| TF-FR-20 | A disabled account is refused before any challenge is made (BRD 10 SEC-01-05). | M |

### C. Trusted devices

| ID | Requirement | Priority |
| --- | --- | --- |
| TF-FR-21 | The second-step page offers **Remember this device for 30 days** (D-21-4). If ticked, the answer includes a random device token, which the browser keeps in local storage and sends with later sign-ins. The server keeps only its SHA-256. | S |
| TF-FR-22 | A valid device token for the same account skips the second step. The first factor is still always needed. | S |
| TF-FR-23 | **Security → Trusted devices** lists each device (browser and system from the User-Agent, date added, last used) with **Remove** and **Remove all**. | S |
| TF-FR-24 | Password change, password reset, turning 2FA off, changing the method and an admin reset remove every trusted device for the account. | M |
| TF-FR-25 | Device tokens never leave the browser except in the sign-in request body, never appear in a URL, and are never logged. | M |
| TF-FR-26 | At most 10 trusted devices per account; adding an 11th removes the least recently used. | C |

### D. Lost device and admin reset

| ID | Requirement | Priority |
| --- | --- | --- |
| TF-FR-27 | The second-step page links to "Lost your device?", which explains the options in order: a recovery code; the email code for email-method accounts; contact an admin. | M |
| TF-FR-28 | **Admin reset** (D-21-5): on the admin People/cohort pages, **Reset two-factor** for a person. It asks for a reason, needs the admin's fresh "Verify it's you" code (new `StepUpAction` `ADMIN_TWO_FACTOR_RESET`), and is refused for the admin's own account. | M |
| TF-FR-29 | A reset removes the person's secret, recovery codes and trusted devices, ends all their sessions, emails them a notice, records `TWO_FACTOR_RESET_BY_ADMIN` with the reason, and raises a security alert. | M |
| TF-FR-30 | After a reset, an admin or coach must set up again; their grace period restarts at **3 days**. | M |
| TF-FR-31 | Password reset ("Forgot password") **does not** turn 2FA off. After resetting the password the person still needs the second factor. | M |

### E. Required for admins and coaches

| ID | Requirement | Priority |
| --- | --- | --- |
| TF-FR-32 | Each admin and coach has a **set-up-by date**: V21's release date + 14 days, or the day the role is given + 14 days, whichever is later. | M |
| TF-FR-33 | Before that date, signing in without 2FA shows **Set up two-factor sign-in** with **Set up now** and **Skip for now (N days left)**. | M |
| TF-FR-34 | From that date, signing in without 2FA allows only the setup flow (and signing out). Every other API call answers 403 with `"code": "TWO_FACTOR_SETUP_REQUIRED"`, and the frontend opens the setup flow. | M |
| TF-FR-35 | Giving someone the coach or admin role (BRD 20 SV-FR-06) tells the admin that the person must set up 2FA within 14 days. | S |
| TF-FR-36 | An admin report lists admins and coaches with 2FA status and set-up-by date. | S |
| TF-FR-37 | Removing the coach or admin role keeps the person's 2FA on; it only stops being required. | M |

### F. Mail, step-up and data

| ID | Requirement | Priority |
| --- | --- | --- |
| TF-FR-38 | Without working mail (`StepUpService.active()` false), the email method cannot be chosen and email-method accounts are told at sign-in to use a recovery code. Authenticator users are not affected. The admin mail warning (BRD 20 SV-FR-34) mentions this. | M |
| TF-FR-39 | Email-method participants with no working mail and no recovery code are not locked out for ever: an admin reset applies (TF-FR-28). | M |
| TF-FR-40 | **"Verify it's you" with the authenticator**: an authenticator user may type a current authenticator code in the BRD 20 dialog instead of waiting for an email; the same replay rule applies. | S |
| TF-FR-41 | The account export lists 2FA status, method, the date it was turned on, recovery codes left and trusted devices (label and dates). It never includes the secret, codes or tokens. | M |
| TF-FR-42 | Deleting the account removes every 2FA row (added to `AccountService.USER_TABLES_IN_DELETE_ORDER`). | M |

### G. BRD 10 close-out

| ID | Requirement | Priority |
| --- | --- | --- |
| TF-FR-43 | `docs/THREAT_MODEL.md` (SEC-06-01): data flows, trust boundaries and abuse cases for sign-in and recovery (password, Google, 2FA, reset), coach access, share links, Excel import and the Claude integration. | M |
| TF-FR-44 | `docs/ASVS_L2_CHECK.md`: each ASVS 5.0 Level 2 requirement that applies, marked Met, Not met (with a fix or an accepted risk signed by the product owner) or Not applicable. | M |
| TF-FR-45 | Every finding marked "Not met" without an accepted risk is fixed in Phase 5 or recorded as a follow-up BRD item with a date. | M |
| TF-FR-46 | The BRD working rules (SEC-06-04) gain one line: every new BRD's acceptance criteria include at least one abuse case. This BRD follows it (TFC-14 to TFC-20). | S |

## Non-functional requirements

| ID | Requirement |
| --- | --- |
| TF-NFR-01 | **Secrets at rest.** TOTP secrets are sealed with `SecretCipher` (AES-256-GCM). Recovery codes, challenge tokens, device tokens and email codes are stored only as HMAC-SHA256 or SHA-256, and compared in constant time. |
| TF-NFR-02 | **Nothing secret in logs or URLs.** No secret, code, challenge token or device token appears in a URL, a log line or the security log. A flow test checks the log output. |
| TF-NFR-03 | **Time.** TOTP uses the injected `Clock` (BRD 13). Render's clock is NTP-synchronised; one step of drift either side is accepted (TF-FR-13). |
| TF-NFR-04 | **Rate limits.** New `RequestRateLimiter` actions: `TWO_FACTOR_VERIFY` per challenge and per network, and `TWO_FACTOR_SETUP` per account. 429 with `Retry-After`, like the others. |
| TF-NFR-05 | **Audit.** New security events: `TWO_FACTOR_ENABLED`, `TWO_FACTOR_DISABLED`, `TWO_FACTOR_METHOD_CHANGED`, `TWO_FACTOR_CHALLENGE_STARTED`, `TWO_FACTOR_VERIFIED`, `TWO_FACTOR_FAILED`, `TWO_FACTOR_LOCKED`, `RECOVERY_CODE_USED`, `RECOVERY_CODES_REGENERATED`, `TRUSTED_DEVICE_ADDED`, `TRUSTED_DEVICE_REMOVED`, `TWO_FACTOR_RESET_BY_ADMIN`, `TWO_FACTOR_SETUP_SKIPPED`. They appear in the admin security log filter. |
| TF-NFR-06 | **Alerts.** At once: an admin reset; an admin or coach turning 2FA off before their date; a recovery code used on an admin account. Thresholds: 10 failed second steps for one account or 50 from one network in 15 minutes (the BRD 10 sign-in numbers). |
| TF-NFR-07 | **Speed.** The second step answers within 300 ms; email-method sign-in sends the code within 2 seconds when mail works. |
| TF-NFR-08 | **Accessibility.** The second-step and setup pages work with the keyboard alone, announce errors, and fit a 320 px screen. The QR code has the text key as its alternative. |
| TF-NFR-09 | **Khmer.** Every new message, page and email has a Khmer draft in `docs/km-review-v21.csv` for review. |
| TF-NFR-10 | **Quality.** BRD 12 coverage minimums (backend 94% lines, 75% branches; frontend 85/84/78/78), BRD 13 rules (`EffectiveJavaRulesTest`), a passing SonarQube gate, and RFC 6238 Appendix B test vectors in the unit tests. |
| TF-NFR-11 | **Feature switch.** `TWO_FACTOR_ENABLED` (default `true`). Off means no second step and no requirement for admins and coaches; it is logged at start-up and shown to admins. Tests may switch it off as V20 tests do. |
| TF-NFR-12 | **Nothing breaks for people without 2FA.** Participants who never turn it on sign in exactly as before; all existing tests pass. |

## Data and API changes (for solution design)

**Migration `V87__two_factor_sign_in.sql`**

| Table | Columns | Notes |
| --- | --- | --- |
| `two_factor_settings` (new) | `user_id` (PK, FK `app_users`), `method` (`TOTP` or `EMAIL`), `totp_secret` (sealed, nullable), `last_totp_step` (BIGINT, nullable; replay guard), `enabled_at`, `created_at`, `updated_at` | One row per account with 2FA on; an unfinished setup is kept with `enabled_at` null and removed after 15 minutes |
| `two_factor_recovery_codes` (new) | `id`, `user_id` (FK), `code_hash` (VARCHAR 64), `used_at` (nullable), `created_at` | 10 per account; replaced as a set |
| `sign_in_challenges` (new) | `id`, `token_hash` (unique), `user_id` (FK), `method`, `code_hash` (nullable, email method), `attempts`, `remember_me`, `return_path`, `origin` (`PASSWORD`, `GOOGLE`), `expires_at`, `completed_at`, `created_at` | 5-minute life; daily clean-up |
| `trusted_devices` (new) | `id`, `user_id` (FK), `token_hash` (unique), `label`, `created_at`, `last_used_at`, `expires_at`, `removed_at` | 30 days |
| `app_users` | add `two_factor_due_at` (TIMESTAMP, nullable) | The set-up-by date for admins and coaches (TF-FR-32) |

**API**

| Endpoint | Purpose |
| --- | --- |
| `POST /api/auth/login`, `POST /api/auth/oauth/google/exchange`, `POST /api/auth/oauth/google/link` | Unchanged when no second step is needed. Otherwise 200 `{ "status": "TWO_FACTOR_REQUIRED", "challengeToken", "method", "sentTo"?, "expiresAt" }`. The request may carry `deviceToken` |
| `POST /api/auth/two-factor/verify` `{ "challengeToken", "code"? , "recoveryCode"?, "rememberDevice" }` | The normal sign-in answer, plus `deviceToken` when remembered; 400 with `triesLeft`; 410 when the challenge has ended |
| `POST /api/auth/two-factor/resend` `{ "challengeToken" }` | Email method only; 429 within 60 seconds |
| `GET /api/account/two-factor` | Status, method, recovery codes left, set-up-by date, trusted devices |
| `POST /api/account/two-factor/totp` → `POST /api/account/two-factor/totp/confirm` `{ "code" }` | Start authenticator setup (secret and `otpauth://` address), then confirm; the confirm answer holds the recovery codes, once. Protected (TF-FR-06) |
| `POST /api/account/two-factor/email` (+ confirm) | Choose the email method (participants). Protected |
| `POST /api/account/two-factor/recovery-codes` | New recovery codes. Protected |
| `POST /api/account/two-factor/disable` | Turn off (participants, or before the date). Protected |
| `DELETE /api/account/trusted-devices/{id}`, `DELETE /api/account/trusted-devices` | Remove one or all |
| `POST /api/admin/users/{id}/two-factor/reset` `{ "reason" }` | Admin reset (TF-FR-28); fresh step-up code |
| `GET /api/admin/two-factor` | Admin and coach 2FA report (TF-FR-36) |

**Model and services**

- `TotpCodes` (RFC 6238 generate and verify with drift and replay), `RecoveryCodes`, `TwoFactorService` (setup,
  status, disable, reset), `SignInChallengeService` (start, verify, resend), `TrustedDeviceService`.
- `AuthService.login` and `GoogleAccountService.exchange`/`link` call one shared "finish first factor" step that
  either opens the session or starts a challenge.
- A request filter or interceptor enforces TF-FR-34 for admins and coaches past their date.
- Frontend: `TwoFactorPage` (second step), `TwoFactorSetup` on the Security page, a recovery-code panel, a trusted
  devices list, a set-up-now screen for admins and coaches, and an `apiClient` branch for
  `TWO_FACTOR_SETUP_REQUIRED`.

## Implementation phases

| Phase | Content | Requirements |
| --- | --- | --- |
| 1 | Data and core: migration, TOTP with RFC 6238 vectors, recovery codes, sealing, challenge and device models, clean-up; unit tests. No sign-in change yet | TF-NFR-01, TF-NFR-03, TF-NFR-10 (part), data model |
| 2 | The second step: login and Google paths return a challenge, verify and resend, trusted devices, lockout counting, rate limits, events, alerts; flow tests | TF-FR-10 to TF-FR-26, TF-FR-31, TF-NFR-02, TF-NFR-04 to TF-NFR-07 |
| 3 | Frontend and settings: Security page setup (QR, key, confirm, recovery codes), email method, turn off, new codes, trusted devices list, second-step page, lost-device help; Khmer | TF-FR-01 to TF-FR-09, TF-FR-12, TF-FR-21, TF-FR-23, TF-FR-27, TF-FR-38, TF-FR-40, TF-NFR-08, TF-NFR-09 |
| 4 | Required for admins and coaches, admin reset, report, export and delete, docs (`API.md`, `DATA_MODEL.md`, `USER_GUIDE.md`, `SECURITY.md`, README), `UAT_V21.md` | TF-FR-28 to TF-FR-30, TF-FR-32 to TF-FR-37, TF-FR-39, TF-FR-41, TF-FR-42, TF-NFR-11, TF-NFR-12 |
| 5 | BRD 10 close-out: threat model, ASVS Level 2 check, fixes for its findings, the abuse-case rule | TF-FR-43 to TF-FR-46 |

Each phase ends with the CI commands passing locally (`mvn verify`, `npm run build`, `npm run test:coverage`) and
stops for confirmation (`CLAUDE.md` working rules 13 to 15).

## Acceptance criteria

| ID | Criterion |
| --- | --- |
| TFC-01 | A participant sets up an authenticator by scanning the QR code (or typing the key), confirming a code and saving 10 recovery codes; signing in then asks for the authenticator code after the password. |
| TFC-02 | A participant without 2FA signs in exactly as before (automated: existing sign-in tests unchanged). |
| TFC-03 | An email-method participant signing in on a new browser gets a code by email and is signed in after typing it. |
| TFC-04 | With **Remember this device** ticked, the next sign-in on that browser asks only for the password; after 30 days, a password change or **Remove**, it asks for the code again. |
| TFC-05 | A recovery code signs the person in once; the same code is refused the second time; the page shows the codes left and an email notice is sent. |
| TFC-06 | After Google sign-in, an account with 2FA on is asked for its second factor before any session opens. |
| TFC-07 | A coach without 2FA sees **Set up now / Skip for now (N days left)**; after the set-up-by date, every page except setup answers `TWO_FACTOR_SETUP_REQUIRED` until setup is done. |
| TFC-08 | An admin cannot choose the email method and cannot turn 2FA off. |
| TFC-09 | An admin resets a coach's 2FA with a reason and their own fresh code; the coach's sessions end, the coach gets a notice, the security log shows `TWO_FACTOR_RESET_BY_ADMIN` and an alert is raised. An admin cannot reset their own. |
| TFC-10 | Turning 2FA on or off, changing the method and making new recovery codes each need a fresh "Verify it's you" code and send a notice. |
| TFC-11 | Password reset leaves 2FA on and removes trusted devices. |
| TFC-12 | The export shows 2FA status and devices but no secret or code; deleting the account removes every 2FA row. |
| TFC-13 | RFC 6238 Appendix B vectors pass; codes one step early or late pass; two steps off fail. |
| TFC-14 (abuse) | An attacker with the right password but no second factor cannot obtain a session or any API data (automated: every protected route answers 401 with only a challenge token). |
| TFC-15 (abuse) | An attacker who sees one authenticator code over the person's shoulder cannot reuse it, even within the same 30 seconds. |
| TFC-16 (abuse) | Guessing codes stops after 5 wrong tries per challenge, and the failures lock the email as wrong passwords do. |
| TFC-17 (abuse) | A challenge token from one account cannot complete another account's sign-in, and an expired or completed challenge answers 410. |
| TFC-18 (abuse) | A copied device token works only for its own account, stops after removal, and is never sent in a URL or logged. |
| TFC-19 (abuse) | A coach past the set-up-by date who calls the API directly (not through the page) is refused with `TWO_FACTOR_SETUP_REQUIRED`. |
| TFC-20 (abuse) | Someone holding a participant's open session cannot turn their 2FA off without a fresh code sent to the account (or a current authenticator code). |
| TFC-21 | `docs/THREAT_MODEL.md` and `docs/ASVS_L2_CHECK.md` exist; every "Not met" item is fixed or has an accepted risk signed by the product owner. |
| TFC-22 | CI passes on the release commit with every coverage minimum met and the SonarQube gate OK. |

## Assumptions, dependencies and risks

### Assumptions

- Admins and coaches have a smartphone or desktop authenticator app.
- Production mail works for the email method and notices (BRD 20, BRD 14 D-14-5 already require it for Google).
- Server time is accurate to within a few seconds.

### Dependencies

- `StepUpService`, `StepUpMailer` and the "Verify it's you" dialog (BRD 20).
- `LoginAttemptLimiter`, `RequestRateLimiter`, the security log and alerts (BRD 10).
- The Google sign-in exchange and link (BRD 14).
- `SecretCipher` and `SETTINGS_ENCRYPTION_KEY` (BRD 10 SEC-04-03).
- The QR code library, if approved (Q-02).

### Risks

| Risk | Impact | Mitigation |
| --- | --- | --- |
| People lose their phone and recovery codes | Locked out | Recovery codes with a "saved" tick; email method for participants; admin reset (D-21-5); clear lost-device help |
| Admin reset used for social engineering | An attacker talks an admin into a reset | Reset still needs the first factor; reason logged; alert; notice to the person; out-of-app identity check in `SECURITY.md` |
| Email method only as strong as the mailbox | A hacked mailbox defeats 2FA | Not allowed for admins and coaches; the page recommends the authenticator |
| Clock drift on the server or phone | Valid codes refused | One step either side; NTP on Render; "check your phone's time" hint after a wrong code |
| Admins and coaches ignore the requirement | High-value accounts unprotected | Grace period, then enforcement on every API call (TF-FR-34); admin report |
| Sign-in feels slower | Complaints | Remember this device; numeric keyboard and `one-time-code` autofill |
| ASVS check finds more than Phase 5 can fix | Release delayed | Findings ranked; non-critical ones recorded as dated follow-ups (TF-FR-45) |

## Open questions

- [x] **Q-01** Is 2FA required for **coaches** as well as admins? (BRD 10 asked the same.) **Answered by the product owner on 22 Nov 2026:** yes, coaches too (D-21-2).
- [x] **Q-02** May the frontend add one small QR code library (for example `qrcode`, MIT licence) to show the setup QR code? **Answered by the product owner on 22 Nov 2026:** yes (D-21-6). The text key stays as the alternative (TF-NFR-08).
- [x] **Q-03** May participants use the **email code** as their second factor? **Answered by the product owner on 22 Nov 2026:** yes (D-21-1). Admins and coaches still need an authenticator app.
- [ ] **Q-04** Is **30 days** right for trusted devices, and is **14 days** (3 after a reset) right for the grace period? Draft: yes.
- [x] **Q-05** May an **admin reset** another person's 2FA, with the safeguards in D-21-5? **Answered by the product owner on 22 Nov 2026:** yes, with those safeguards.
- [ ] **Q-06** Should 2FA also be asked after **Google** sign-in? Draft: yes (D-21-3).
- [ ] **Q-07** Should "Verify it's you" accept an **authenticator code** instead of email for authenticator users (TF-FR-40)? Draft: yes; it is faster and works without mail.
- [ ] **Q-08** Should **passkeys** (WebAuthn) come in a later BRD as a phishing-resistant option? Draft: yes, after V21.
- [ ] **Q-09** Who confirms a person's identity before an admin reset, and how (for example, a call to a phone number on file)? Needed for `SECURITY.md` before Phase 4.

## Sign-off

| Role | Name | Decision | Date |
| --- | --- | --- | --- |
| Sponsor | | | |
| Product owner | | | |
| Security reviewer | | | |

## Sources

- BRD 10.0.0 (SEC-06-01, SEC-06-04, SEC-07-01, SEC-07-03, SEC-07-07, SEC-09; Phase 7 status), BRD 12.0.0 (coverage), BRD 13.0.0 (Java rules), BRD 14.0.0 (Google sign-in, D-14-3, D-14-5), BRD 20.0.0 (step-up codes, D-20-1 to D-20-5).
- Code at commit `9f924d2`: `controller/AuthController.java`, `service/AuthService.java` (`login`), `service/AuthSessionService.java` (`open`), `service/oauth/GoogleAccountService.java` (`exchange`, `link`), `service/stepup/StepUpService.java`, `service/support/LoginAttemptLimiter.java`, `service/support/RequestRateLimiter.java`, `service/support/SecretCipher.java`, `service/AccountService.java` (delete order), `security/audit/SecurityEventType.java`, `entity/enums/UserRole.java`, migrations up to `V86`.
- RFC 6238 (TOTP) and RFC 4226 (HOTP); OWASP ASVS 5.0 (V6 Authentication); NIST SP 800-63B (authenticator types and recovery).
