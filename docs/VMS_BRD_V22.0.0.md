# BRD – Confirm Your Email at Sign-up

Nov 22, 2026

## Document control

This is draft 22.0.0. It keeps every requirement in BRD 21.0.0 (Two-step sign-in), BRD 20.0.0 (Verify it's you),
BRD 14.0.0 (Sign in with Google), BRD 10.0.0 (security) and the earlier BRDs, and adds **email confirmation at
sign-up**: a new account exists only after its owner opens a link sent to the address they typed. It closes ASVS
findings **F-01** (email not confirmed at sign-up) and **F-02** (sign-up reveals that an email is registered).

| Item | Value |
| --- | --- |
| Document | Business Requirements Document (BRD) – Email confirmation addendum |
| Version | 22.0.0 – Draft (paused) |
| Parent documents | BRD 20.0.0 (Q-06: confirm the email at sign-up in a separate small BRD), BRD 21.0.0 (TF-FR-45; `docs/ASVS_L2_CHECK.md` F-01 and F-02, target 31 Jan 2027), BRD 10.0.0 (SEC-06-02 rate limits, SEC-07-02 no account enumeration, SEC-09 security log), BRD 14.0.0 (Google accounts), `CLAUDE.md` (business rules 13 and 14) |
| Business owner | To be confirmed |
| Product owner | To be confirmed |
| Approvers | Sponsor, Product owner, Security reviewer |

| Version | Change | Author |
| --- | --- | --- |
| 22.0.0 (paused) | Paused by the product owner on 22 Nov 2026 before Phase 1; nothing is built. F-01 and F-02 stay open in `ASVS_L2_CHECK.md` | BA |
| 22.0.0 (questions answered) | Product owner answers, 22 Nov 2026: Q-01 existing accounts are not asked to confirm; Q-02 opening the link signs the person in; Q-03 production sign-up stops without mail, otherwise unconfirmed accounts come back; Q-04 link only; Q-05 the link lives 24 hours. D-22-3, D-22-5 and D-22-7 approved | BA |
| 22.0.0 | First draft: terms, current state at commit `0d5ba60`, decisions D-22-1 to D-22-7, sign-up (EC-FR-01 to EC-FR-09), the confirmation link (EC-FR-10 to EC-FR-17), an email that already has an account (EC-FR-18 to EC-FR-21), existing and Google accounts (EC-FR-22 to EC-FR-25), no-mail behaviour (EC-FR-26 to EC-FR-28), non-functional requirements, data and API, phases, acceptance criteria and open questions | BA |

## Executive summary

Today anyone can create an account with any email address, and is signed in at once. Two problems follow:

1. **The address is never proved (F-01).** Someone can sign up with another person's address. Reminders, notices
   and report emails then go to a stranger, and the real owner later finds the address taken.
2. **Sign-up tells strangers who has an account (F-02).** Typing an address that is already registered answers
   "Email is already registered". Anyone can test a list of addresses to learn who uses the app, which is useful for
   targeted phishing. A rate limit (10 per network per hour) slows this but does not stop it.

V22 fixes both with one change. Sign-up always answers the same way: "**Check your email.**" A new address gets a
link that creates the account and signs the person in. An address that already has an account gets a short email
telling its owner that someone tried to sign up, with links to sign in or reset the password. The page looks
identical in both cases, so it reveals nothing.

### Terms used in this BRD

| Term | Meaning in this BRD |
| --- | --- |
| Email confirmation (verification) | Proving you control an email address by opening a link sent to it |
| Pending sign-up | A sign-up form that was sent but whose link has not been opened yet. It is not an account: it cannot sign in and holds no data |
| Confirmation link | A single-use link, valid for 24 hours, that turns a pending sign-up into an account |
| Account enumeration | Learning which email addresses have accounts by watching how the app answers |
| "Someone tried to sign up" email | The email sent to an address that already has an account when it is typed into the sign-up form |

## Business objectives

| ID | Objective | Measure |
| --- | --- | --- |
| BO-1 | Every new account's email belongs to its owner | No account is created without its confirmation link being opened (automated test) |
| BO-2 | Sign-up reveals nothing about which emails have accounts | Same status, body and page for new and registered addresses; response time difference under 100 ms at the median (test) |
| BO-3 | Signing up stays quick | Median time from pressing **Sign up** to being signed in under 2 minutes in UAT |
| BO-4 | People who already have accounts are not disturbed | No existing account must confirm anything to keep signing in (D-22-5) |
| BO-5 | A typo does not lose a sign-up | The person can fix the address and send again from the same screen |

## Scope

### In scope

- Sign-up by email and password, with or without a cohort join code.
- The confirmation email and link, in English or Khmer (the language of the sign-up page).
- The "someone tried to sign up" email to existing owners.
- A **Check your email** screen, a **Confirm email** page, and a hint on the Sign In page.
- Rate limits, security log events, clean-up of old pending sign-ups.
- Updates to `THREAT_MODEL.md` and `ASVS_L2_CHECK.md` closing F-01 and F-02.

### Out of scope

- Changing the login email of an existing account (no such feature; BRD 20 recorded that it must verify both
  addresses when added).
- Asking existing accounts to confirm their address (D-22-5; Q-01 answered no).
- Sign in with Google: Google already proves the address (D-22-6).
- Codes typed by hand instead of a link (Q-04: link only).
- CAPTCHA. The rate limits and the confirmation email are the abuse controls; revisit if bot sign-ups appear.

## Current state (22 Nov 2026, commit `0d5ba60`)

| Area | Today |
| --- | --- |
| Sign-up (`POST /api/auth/register`) | Checks the password policy, then answers 400 "Email is already registered." if the address has an account. Otherwise creates the account (`ACTIVE`) and returns a session (201) |
| Sign-up page (`/register`) | Name, email, password twice, optional join code (filled from `?code=`). After success it goes to the Sign In page with the email filled in |
| Rate limits | `REGISTRATION`: 10 per network per hour. Counts in Redis since `0d5ba60` (F-04) |
| Password reset | 256-bit single-use token, only the SHA-256 kept, 30 minutes; answers 204 for any address; link written to the server log when `MAIL_HOST` is not set |
| Google sign-in | Accounts created from Google need `email_verified` from Google; no password |
| Account data | `app_users` has no field saying whether or how the email was proved |
| Security log | `REGISTERED` event; no confirmation events |
| Findings | ASVS F-01 (not met) and F-02 (accepted-risk candidate), target 31 Jan 2027 |

## Decisions proposed by this BRD

| ID | Decision | Effect on `CLAUDE.md` |
| --- | --- | --- |
| D-22-1 | **Sign-up always answers the same way**: 202 with "Check your email", for a new address, a registered address, or a pending one. It never returns a session. Only checks that do not depend on the address (missing fields, password policy, join code) may answer differently. | None |
| D-22-2 | **No account until the link is opened.** The form is kept as a pending sign-up (with the password already hashed) in its own table. A pending address cannot sign in; signing in with it answers exactly like an unknown address. | None |
| D-22-3 | **Opening the link creates the account and signs the person in** in the browser where it is opened. The link lives 24 hours, works once, and carries its token in the URL fragment, so it does not reach server logs or Referer headers, and mail scanners that open links do not use it up (the page sends it by POST). *(Approved, Q-02 and Q-05, 22 Nov 2026.)* | None |
| D-22-4 | **A registered address gets a "someone tried to sign up" email** instead of a link, with **Sign in** and **Reset password** links. Nothing is created or changed. | None |
| D-22-5 | **Existing accounts are not asked to confirm.** They are marked "confirmed before V22" and keep working exactly as today. *(Approved by the product owner, Q-01, 22 Nov 2026.)* | None |
| D-22-6 | **Google accounts count as confirmed**, because Google proves the address (`email_verified`). The Google flow does not change. | None |
| D-22-7 | **Without mail, sign-up cannot be completed in production.** The app does not fall back to creating unconfirmed accounts. Locally the link is written to the server log, like the reset link. *(Approved by the product owner, Q-03, 22 Nov 2026.)* | None |

## Target experience

1. Dara opens **Sign up**, types his name, `dara@example.com`, a password and his cohort's join code, and presses
   **Sign up**.
2. The page shows: "**Check your email.** We sent a link to dara@example.com. It works for 24 hours." with **Send
   again** (available after a minute) and **Wrong address? Change it**.
3. He opens the email ("Confirm your email for Vision Map") and presses **Confirm my email**.
4. The app opens, says "Email confirmed", and he lands on the dashboard, signed in and in his cohort.
5. Separately, Sophea already has an account. Someone types her address into the sign-up form. They see the same
   "Check your email" screen. Sophea receives "Someone tried to sign up with your email" with **Sign in** and **Reset
   password** links and the advice to ignore it if it was her.

## Functional requirements

Priority: **M** Must, **S** Should, **C** Could.

### A. Sign-up

| ID | Requirement | Priority |
| --- | --- | --- |
| EC-FR-01 | `POST /api/auth/register` answers **202** with the same body for a new, registered or pending address (D-22-1). It no longer returns a session. | M |
| EC-FR-02 | Field checks, the password policy (including breached passwords) and the join code are checked **before** the address is looked up, and their errors are the same for every address. | M |
| EC-FR-03 | For a new address, the server stores a pending sign-up: name, email (lowercase), BCrypt hash of the password, the cohort from the join code, the page language, the hash of a new link token, and an expiry 24 hours ahead. | M |
| EC-FR-04 | Sending the form again for a pending address **replaces** the pending sign-up (new name, password, join code and link). Earlier links stop working. | M |
| EC-FR-05 | At most **3 sign-up emails per address per hour** (confirmation or "someone tried" emails together). Over the limit the answer is still 202, and no email is sent. | M |
| EC-FR-06 | The `REGISTRATION` limit (10 per network per hour) stays. | M |
| EC-FR-07 | The **Check your email** screen shows the address typed, says the link works for 24 hours and to check spam, offers **Send again** after 60 seconds (sends the same form again) and **Wrong address? Change it** (back to the form with everything but the passwords kept). | M |
| EC-FR-08 | The Sign In page shows a short hint under a failed sign-in: "Just signed up? Open the link we emailed you first." It shows for every failure, so it reveals nothing. | S |
| EC-FR-09 | Security log: `SIGNUP_REQUESTED` (new or pending address) and `SIGNUP_EXISTING_EMAIL` (registered address), both with the network address and no user id for pending sign-ups. | M |

### B. The confirmation link

| ID | Requirement | Priority |
| --- | --- | --- |
| EC-FR-10 | The link is `<frontend>/confirm-email#token=<token>`: 256 random bits, only its SHA-256 stored, single use, 24 hours. | M |
| EC-FR-11 | The **Confirm email** page sends the token with `POST /api/auth/confirm-email` and, on success, signs the person in and opens the dashboard (or the cohort welcome when they joined one). | M |
| EC-FR-12 | Confirming creates the account (`ACTIVE`, role USER, `email_confirmed_at` now, `email_confirmed_by` `LINK`), deletes the pending sign-up, records `REGISTERED` and `EMAIL_CONFIRMED`, and opens a session with "remember me" on. | M |
| EC-FR-13 | If the address got an account meanwhile (for example through Google), confirming does **not** create a second account or change the existing one; the page says "This email already has an account. Sign in." | M |
| EC-FR-14 | If the join code's cohort was archived meanwhile, the account is created without a cohort and the page says the code no longer works. | M |
| EC-FR-15 | A used, replaced, expired or unknown link answers **410** and the page says "This link has expired or was already used" with **Sign up again** and **Sign in**. | M |
| EC-FR-16 | Confirmation attempts are limited to 30 per network per 15 minutes. | M |
| EC-FR-17 | Pending sign-ups older than 24 hours are deleted daily. | M |

### C. An address that already has an account

| ID | Requirement | Priority |
| --- | --- | --- |
| EC-FR-18 | The owner receives "Someone tried to sign up with your email" with the time, the browser (from the User-Agent, as V20 notices do), **Sign in** and **Reset password** links, and "If this was you, you already have an account. If not, you can ignore this email." | M |
| EC-FR-19 | The email is sent only if the account is `ACTIVE`. For a disabled account nothing is sent (the answer is still 202). | M |
| EC-FR-20 | The email never contains a link that signs in or changes anything by itself. | M |
| EC-FR-21 | The email is in the language of the sign-up page, since the account's language is not stored. | S |

### D. Existing and Google accounts

| ID | Requirement | Priority |
| --- | --- | --- |
| EC-FR-22 | The migration marks every existing account `email_confirmed_by` `BEFORE_V22` with `email_confirmed_at` empty; they sign in and work exactly as before (D-22-5). | M |
| EC-FR-23 | Accounts created by Google sign-in are marked `GOOGLE` with the time (D-22-6). | M |
| EC-FR-24 | A completed password reset marks an account that is not yet `LINK` or `GOOGLE` as `RESET` with the time, because the reset proved the mailbox. | S |
| EC-FR-25 | Download my data includes `emailConfirmedAt` and `emailConfirmedBy`. | S |

### E. When mail is not set up or fails

| ID | Requirement | Priority |
| --- | --- | --- |
| EC-FR-26 | Locally (no `MAIL_HOST`), the confirmation link is written to the server log, as the reset link is. | M |
| EC-FR-27 | In production without mail, sign-up answers 503 "Sign-up is not available right now. Please try again later." for every address (so it reveals nothing), and the admin mail warning (V20) mentions sign-up. | M |
| EC-FR-28 | A failed send is retried once; after that it is logged. The answer to the person is still 202, so it reveals nothing; they can use **Send again**. | M |

## Non-functional requirements

| ID | Requirement |
| --- | --- |
| EC-NFR-01 | **Equal work for every address**: the password is BCrypt-hashed and the email is queued for every sign-up, and mail is sent after the transaction commits, off the request thread, so response time does not reveal whether the address is registered (BO-2). |
| EC-NFR-02 | Tokens and addresses never appear in logs in production; the token never appears in a URL path or query. |
| EC-NFR-03 | Per-address counts are kept in the shared counters (Redis, F-04), keyed by a hash of the address. |
| EC-NFR-04 | Pending sign-ups hold no data beyond the form; they are not visible to admins or coaches and are not in any export. |
| EC-NFR-05 | All new text is in English and Khmer; the Khmer is added to the review sheet. |
| EC-NFR-06 | Coverage minimums from BRD 12 apply; the access-control sweep includes the new endpoints. |

## Data and API changes (for solution design)

| Change | Detail |
| --- | --- |
| Migration `V89__email_confirmation.sql` | New table `pending_signups` (id, email unique, full_name, password_hash, cohort_id null, language, token_hash unique, expires_at, created_at). `app_users` gains `email_confirmed_at` (timestamp, null) and `email_confirmed_by` (varchar 20, not null, default `BEFORE_V22`) |
| `POST /api/auth/register` | Same request. Answers 202 `{ "message": "Check your email." }` (was 201 with a session). Breaking change for the frontend, shipped together |
| `POST /api/auth/confirm-email` | Public. Body `{ "token" }`. 200 with the usual sign-in body; 410 for a used, replaced, expired or unknown link |
| Rate limits | New `SIGNUP_EMAIL` (3 per address per hour) and `CONFIRM_EMAIL` (30 per network per 15 minutes) in `RequestRateLimiter` |
| Security events | `SIGNUP_REQUESTED`, `SIGNUP_EXISTING_EMAIL`, `EMAIL_CONFIRMED` |
| Mail | `SignUpMailer`: confirmation email and "someone tried" email, English and Khmer |
| Settings | `app.sign-up.confirm-url` (`SIGN_UP_CONFIRM_URL`), `app.sign-up.link-hours` (24) |
| Frontend | `RegisterPage` shows **Check your email**; new `ConfirmEmailPage` at `/confirm-email`; Sign In hint; `authApi.confirmEmail` |

## Implementation phases

| Phase | Content | Done when |
| --- | --- | --- |
| 1 | Migration, pending sign-ups, the two emails, `confirm-email`, limits, log events, clean-up job, no-mail behaviour | Backend tests cover EC-FR-01 to EC-FR-28 and the equal-answer checks; suites green |
| 2 | Check your email screen, Confirm email page, Sign In hint, Khmer | Frontend tests cover the flow; build green |
| 3 | `API.md`, `DATA_MODEL.md`, `USER_GUIDE.md`, `SECURITY.md`, `THREAT_MODEL.md`, `ASVS_L2_CHECK.md` (F-01 and F-02 closed), `UAT_V22.md`, CHANGELOG | Docs updated; UAT script ready |

## Acceptance criteria

1. Signing up with a new address shows **Check your email** and creates no account until the link is opened.
2. Opening the link creates the account, signs the person in and places them in the cohort from the join code.
3. Signing up with a registered address shows the same screen, creates nothing, and emails the owner.
4. The API answers for a new and a registered address have the same status and body; the median response time differs
   by less than 100 ms over 50 tries each.
5. A pending address cannot sign in and gets the same answer as an unknown address.
6. A used, replaced or expired link shows the expired page.
7. A fourth sign-up email to the same address within an hour is not sent, and the answer does not change.
8. Existing accounts and Google accounts sign in as before, without confirming anything.
9. Locally without mail, the link appears in the server log; in production without mail, sign-up answers 503 for
   every address.
10. Backend and frontend suites, the access-control sweep and the coverage gate pass.

## Assumptions, dependencies and risks

### Assumptions

- Production mail works (it does today for reset, step-up and two-step codes).
- People can open a link from their mailbox on the device they want to use, or sign in afterwards on another.

### Dependencies

- Shared counters in Redis (`0d5ba60`, F-04) for the per-address limit.
- The V20 mail settings and admin mail warning.

### Risks

| Risk | Effect | Response |
| --- | --- | --- |
| Confirmation emails land in spam | Sign-ups stall | The screen says to check spam; **Send again**; monitor `SIGNUP_REQUESTED` against `EMAIL_CONFIRMED` |
| Cohort onboarding sessions where everyone signs up at once | Slower start in a workshop | Links arrive in seconds; the coach can show the screen; the network limit is per network, so a shared office network may need `RATE_LIMIT_REGISTRATION_*` raised for the day |
| A person opens the link on a different device | They are signed in there, not where they signed up | The original tab says "Already confirmed? Sign in" |
| Someone uses the form to email a stranger repeatedly | Nuisance email | 3 per address per hour, 10 per network per hour; the emails contain no action links that change anything |

## Open questions

- [x] **Q-01** Should **existing accounts** be asked to confirm their address (for example a banner, not a block)? **Answered by the product owner on 22 Nov 2026:** no; they keep working (D-22-5).
- [x] **Q-02** Should opening the link **sign the person in** (D-22-3), or only confirm and then ask them to sign in? **Answered by the product owner on 22 Nov 2026:** yes, sign them in.
- [x] **Q-03** In production without mail, should sign-up **stop** (D-22-7, EC-FR-27)? **Answered by the product owner on 22 Nov 2026:** yes; otherwise unconfirmed accounts come back.
- [x] **Q-04** Is a **link** enough, or should the email also carry a 6-digit code for people who read mail on another device? **Answered by the product owner on 22 Nov 2026:** link only.
- [x] **Q-05** Is **24 hours** right for the link? **Answered by the product owner on 22 Nov 2026:** yes.

## Sign-off

| Role | Name | Decision | Date |
| --- | --- | --- | --- |
| Sponsor | | | |
| Product owner | | | |
| Security reviewer | | | |

## Sources

- OWASP ASVS 5.0, V6 Authentication (no account enumeration; verify the email at registration).
- OWASP Authentication Cheat Sheet, "Authentication and error messages" and "Registration".
- `docs/ASVS_L2_CHECK.md` findings F-01 and F-02; `docs/THREAT_MODEL.md` section 1.
