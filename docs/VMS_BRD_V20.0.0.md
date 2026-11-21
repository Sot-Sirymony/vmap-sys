# BRD – Verify It's You (Step-up Verification by Email Code)

Nov 21, 2026

## Document control

This is draft 20.0.0. It keeps every requirement in BRD 19.0.0 (Report Management), BRD 18.0.0 (Management Report),
BRD 17.0.0 (Blockers in the Right Place), BRD 16.0.0 (Vision Map Timeline), BRD 15.0.0 (AI Assistant and Helpers),
BRD 14.0.0 (Sign in with Google, paused), BRD 13.0.0 (Java coding rules), BRD 12.0.0 (coverage minimums), BRD 10.0.0
(security) and the earlier BRDs, and adds **step-up verification**: before a risky change on the Account, Security or
admin pages, the app emails a one-time code and the change saves only after the person types it.

| Item | Value |
| --- | --- |
| Document | Business Requirements Document (BRD) – Step-up verification addendum |
| Version | 20.0.0 – Draft |
| Parent documents | BRD 10.0.0 (SEC-06-05 sensitive actions, SEC-07-01 lockout, SEC-07-03 session revocation, SEC-05-06 email content, SEC-09 security log), BRD 14.0.0 (paused; relies on this for accounts without a password), BRD 12.0.0 (coverage minimums), `CLAUDE.md` (business rules 13 and 14) |
| Business owner | To be confirmed |
| Product owner | To be confirmed |
| Approvers | Sponsor, Product owner, Security reviewer |

| Version | Change | Author |
| --- | --- | --- |
| 20.0.0 (questions answered) | Product owner answers, 21 Nov 2026: Q-01 no two-factor sign-in in V20 (later BRD); Q-03 Download my data needs a code; Q-04 without mail, continue with the password checks (D-20-4 approved); Q-05 no lost-email support process in V20; Q-06 confirm the email at sign-up in a separate small BRD. Scope and assumptions updated to match. Q-02 and Q-07 stay open with their draft answers | BA |
| 20.0.0 | First draft: terms, current state at commit `03a6a16`, decisions D-20-1 to D-20-5, protected actions (SV-FR-01 to SV-FR-09), the code (SV-FR-10 to SV-FR-19), the verify dialog (SV-FR-20 to SV-FR-27), change notices (SV-FR-28 to SV-FR-31), no-mail behaviour (SV-FR-32 to SV-FR-34), carry-over (SV-FR-35), non-functional requirements, data and API, phases, acceptance criteria and open questions | BA |

## Executive summary

Today a person who is signed in can change most account settings with nothing more than the open session. Changing
the password and deleting the account ask for the current password, which meets BRD 10 SEC-06-05. But the password
is the *same* secret that opened the session: anyone who has the session and has seen or guessed the password (a
shared laptop, a shoulder-surfed password, a stolen token plus a reused password) can lock the owner out, take all
their data, or erase the account.

V20 adds a second, separate proof for those moments. The app sends a **6-digit code by email** (out-of-band: a
different channel from the browser) and the change goes through only when the code is typed back. This is called
**step-up verification** or **re-authentication**. It is not two-factor sign-in: signing in does not change. The
code is asked for only when someone tries a risky action.

### Terms used in this BRD

| Term | Meaning in this BRD |
| --- | --- |
| Step-up verification (re-authentication) | Proving again that it is really you before a risky action, even though you are already signed in |
| Email one-time code (OTP) | A 6-digit code sent by email, valid for 10 minutes and usable once |
| Two-factor authentication (2FA / MFA) | A second proof beyond the password. The email code is one kind; authenticator apps, SMS and passkeys are others. V20 uses the email code for risky actions only, not for signing in |
| Out-of-band verification | The proof arrives through a different channel (email) from the one in use (the app) |
| Protected action | An action listed in SV-FR-01 to SV-FR-09 that needs a verified code |
| Verified window | The minutes after a successful code during which other protected actions in the same session do not ask again (D-20-3) |

## Business objectives

| ID | Objective | Measure |
| --- | --- | --- |
| BO-1 | A stolen or unattended session cannot lock the owner out, take their data or delete the account | Every protected action needs a code sent to the account email (automated test per action) |
| BO-2 | A reminder email can only be sent to an address its owner controls | Changing the reminder address needs a code sent to the new address |
| BO-3 | The owner always learns about a risky change | A notice email goes to the account email after each completed protected action |
| BO-4 | Verification is quick and does not nag | One code covers 10 minutes of related changes; median time from request to verified under 60 seconds in UAT |
| BO-5 | Nothing gets worse when mail is down or not set up | Clear message and the V19 password checks still apply (D-20-4); no account is locked out by a mail fault |

## Scope

### In scope

- A reusable step-up check on the server and a reusable **Verify it's you** dialog on the frontend.
- The protected actions in SV-FR-01 to SV-FR-09: change password, delete account, download all my data, change the
  reminder email, and the admin actions that change roles, the AI key or cohort coaches.
- Codes by email in English or Khmer, change notices, rate limits, security log events.
- Behaviour when mail is not set up (development, or a fault in production).
- The account-deletion confirmation word fix found on 21 Nov 2026 (SV-FR-35).

### Out of scope

- Two-factor **sign-in** (a code at every sign-in or from a new device): a later BRD (Q-01).
- Confirming the email address at sign-up with a code: a separate small BRD (Q-06).
- A support process for someone who has lost access to their email (Q-05).
- Authenticator apps (TOTP), SMS codes, passkeys/WebAuthn, recovery codes, "remember this device".
- Changing the account (login) email address. The app has no such feature today; when it is added it must be a
  protected action that verifies both the old and the new address (recorded here so it is not forgotten).
- Sign in with Google (BRD 14, paused). When resumed, accounts without a password will rely on this step-up check.
- Admin override of a person's verification. No one can verify on someone else's behalf (D-20-5).

## Current state (21 Nov 2026, commit `03a6a16`)

| Area | Today |
| --- | --- |
| Account page (`/settings/account`) | Daily reminder switch and reminder email, report reminder (V19), **Download my data** (JSON of everything), **Delete your account** (password + typing `DELETE`) |
| Security page (`/settings/security`) | **Change password**: current password + new password twice (`POST /api/auth/change-password`). On success every other session ends (SEC-07-03) |
| Reminder email | `PUT /api/account/reminders` saves any address that looks like an email, with no proof the person owns it. Reminders and the test email are then sent there |
| Data export | `GET /api/account/export` returns everything, including archived records, with only the session |
| Account deletion | `POST /api/account/delete` with the password; deletes everything in one transaction |
| Admin actions | Role changes (via cohort coach assignment), AI key and AI settings, logged in the security log, protected only by the admin's session |
| Mail | Spring Mail with `MAIL_HOST` etc. Without `MAIL_HOST` the password reset link is written to the server log instead of sent. Reminder and reset mailers exist; timeouts 10 s (SEC-10-04) |
| One-time secrets | Password reset tokens: 256 random bits, only the SHA-256 kept, 30-minute life, single use. Share links follow the same pattern |
| Sessions | `auth_sessions` rows; the JWT carries the session id; refresh rotation; logout ends the session |
| Rate limits | `RequestRateLimiter` actions: registration, join code, shared plan, Excel import, report export, report share, shared report. Sign-in lockout after 5 failures in 15 minutes |
| Security log | `SecurityEventType` covers sign-in, password change and reset, sharing, export, deletion and admin changes. No step-up events |
| Known issue | The delete button stays disabled unless the confirmation box holds exactly `DELETE` (uppercase, no spaces) and the page does not say why. Phones often type `Delete` or add a trailing space |

## Decisions recorded by this BRD

| ID | Decision | Effect on `CLAUDE.md` |
| --- | --- | --- |
| D-20-1 | Risky actions need **both** what the person knows (the password, where it is already asked) **and** a code sent to the account email. The code never replaces the password where the password is asked today. | None |
| D-20-2 | The code is tied to **one person, one session and one kind of action** (and, for a new reminder address, that address). A code for one purpose cannot be used for another, in another browser, or by another account. | None |
| D-20-3 | After a successful code, the same session has a **10-minute verified window** for the *settings* group of actions (password, reminder email, data download). **Deleting the account and every admin action always need their own fresh code.** | None |
| D-20-4 | When mail is not set up or fails, the app says so plainly and **does not lock anyone out**: the V19 checks (session plus password where asked) still apply and the change is logged as "verified without email". Production shows a warning to admins until mail works. *(Approved by the product owner, Q-04, 21 Nov 2026.)* | None |
| D-20-5 | No one, including an admin, can verify on another person's behalf or switch verification off for one person. Admins can only turn the whole feature off with a server setting, which is logged. | Business rule 14 is unchanged; add a line to `docs/SECURITY.md` |

`CLAUDE.md` is changed only if the product owner asks for these decisions to be recorded there.

## Target experience

1. Sophea opens **Account** and presses **Download my data**.
2. A dialog opens: "**Verify it's you.** We sent a 6-digit code to s\*\*\*\*\*@g\*\*\*\*.com. It works for 10 minutes."
3. She opens her email, types the code (her phone offers it automatically), and the download starts.
4. Five minutes later she changes her reminder email. The app does not ask again for her account (verified window),
   but it sends a code to the **new** address to prove she owns it; the address saves when she types that code.
5. She gets a short notice at her account email: "Your reminder email was changed to m\*\*\*@work.org on 21 Nov at
   14:05. If this was not you, change your password now."
6. Later she deletes her account. Even inside the window, the app sends a fresh code and asks for her password and
   the confirmation word.

## Functional requirements

Priority: **M** Must, **S** Should, **C** Could.

### A. Protected actions

| ID | Requirement | Priority |
| --- | --- | --- |
| SV-FR-01 | **Change password** (`/settings/security`) needs the current password (today) **and** a verified code for action `CHANGE_PASSWORD`. A password reset by emailed link (forgot password) is unchanged: the link already proves the email. | M |
| SV-FR-02 | **Delete account** needs the password, the confirmation word **and** a fresh code for action `DELETE_ACCOUNT`, verified within the last 5 minutes, never covered by the verified window. | M |
| SV-FR-03 | **Download my data** (`GET /api/account/export`) needs a verified code for action `EXPORT_DATA`, or the verified window. | M |
| SV-FR-04 | **Change the reminder email** to a different address needs (a) the account check (code to the account email, or the verified window) and (b) a code sent **to the new address** for action `CONFIRM_REMINDER_EMAIL`, tied to that address. Clearing it (back to the login email) needs only (a). Other reminder settings (on/off, time zone, report reminder) need no code. | M |
| SV-FR-05 | **Send test email** to an unconfirmed reminder address is not possible; the address is only saved after SV-FR-04. | M |
| SV-FR-06 | **Admin: change a person's role**, including making someone a coach through a cohort, needs a fresh code for the admin (`ADMIN_ROLE_CHANGE`). | S |
| SV-FR-07 | **Admin: set or remove the AI API key** needs a fresh code for the admin (`ADMIN_AI_KEY`). | S |
| SV-FR-08 | **Admin: change a cohort's coach** needs a fresh code for the admin (`ADMIN_COHORT_COACH`); other cohort edits do not. | C |
| SV-FR-09 | The list of protected actions lives in one place on the server (an enum with the window rule per action), so adding one later is a one-line change plus its test. Each protected endpoint is listed in the access sweep test. | M |

### B. The code

| ID | Requirement | Priority |
| --- | --- | --- |
| SV-FR-10 | **Request a code.** A signed-in person asks for a code for one action (and, for SV-FR-04, the new address). The server makes a 6-digit code from a cryptographically secure random source (000000–999999, leading zeros kept). | M |
| SV-FR-11 | **Where it goes.** To the account (login) email, except SV-FR-04(b), which goes to the new address. The reply shows the address masked (`s*****@g****.com`), never in full when it is the account email. | M |
| SV-FR-12 | **Life.** A code works for **10 minutes** and **once**. Asking for a new code for the same action and session cancels the earlier one. | M |
| SV-FR-13 | **Wrong codes.** After **5 wrong tries** a code stops working and the person must ask for a new one. Each wrong try says how many are left. | M |
| SV-FR-14 | **Resend.** A new code can be asked for after **60 seconds**; at most **5 codes per person per hour** and **20 per day**. Over the limit answers 429 with the time to wait (SEC-06-02 style). | M |
| SV-FR-15 | **Binding.** A code is valid only for the person, session and action it was made for (D-20-2), and for SV-FR-04 only for the address it was sent to. Using it anywhere else is the same "code not valid" answer. | M |
| SV-FR-16 | **Verified window.** A verified code for a *settings* action opens a 10-minute window for the other settings actions in the **same session** (D-20-3). Signing out, a password change or a password reset closes every window. Delete and admin actions never use the window. | M |
| SV-FR-17 | **The email.** Subject "Your Vision Map code: 123456" (the code in the subject so phones can offer it). The body names the action in plain words ("to download all your data"), the code, when it ends, and "If this was not you, do not share this code and change your password." It holds **no link** (so a fake email with a link is easy to spot) and no other personal data. User text is escaped (SEC-05-06). | M |
| SV-FR-18 | **Language.** The email is in the person's app language (English or Khmer). | M |
| SV-FR-19 | **Lockout link.** Ten wrong codes for one person within an hour (across codes) end every session except the current one and send a security notice to the account email, as a stolen-session signal. | S |

### C. The Verify it's you dialog

| ID | Requirement | Priority |
| --- | --- | --- |
| SV-FR-20 | One reusable dialog used by every protected action. It opens when the action is pressed, asks the server for a code, and shows the masked address and the minutes left. | M |
| SV-FR-21 | One code box: numbers only (`inputmode="numeric"`), 6 digits, `autocomplete="one-time-code"` so phones offer the code from the email, paste accepted (spaces and dashes removed). It submits on its own when 6 digits are entered. | M |
| SV-FR-22 | **Resend code** is disabled with a countdown for 60 seconds; then enabled. **Use a different method** is not shown (out of scope). | M |
| SV-FR-23 | Clear messages for: wrong code (with tries left), code ended, too many codes (with the wait), mail not sent (D-20-4), and network errors. Messages come from the server and are translated (Khmer). | M |
| SV-FR-24 | After success the dialog closes and the original action continues **without re-entering** what the person typed (the new password, the reminder address). | M |
| SV-FR-25 | If the server answers a protected request with "verification needed" (for example the window just ended), the page opens the dialog and retries once after success, so the person never sees a raw error. | M |
| SV-FR-26 | Accessible: focus moves to the code box, the dialog has a title and description, errors are announced, it works with the keyboard alone and at 320 px width. | M |
| SV-FR-27 | The Account and Security pages show one line under each protected action: "We'll email you a code to confirm it's you." | S |

### D. Change notices

| ID | Requirement | Priority |
| --- | --- | --- |
| SV-FR-28 | After a completed protected action, a notice goes to the **account email**: what changed, when (in the person's time zone), from which browser and approximate place if known, and "If this was not you, change your password now". No code, no link. | M |
| SV-FR-29 | When the reminder email changes, the notice also goes to the **old** reminder address, if there was one. | S |
| SV-FR-30 | After account deletion, a final notice goes to the deleted account's email: "Your Vision Map account and all its data were deleted on …". It is sent after the transaction commits. | S |
| SV-FR-31 | Notices are never sent for actions that failed or were cancelled. | M |

### E. When mail is not set up or fails

| ID | Requirement | Priority |
| --- | --- | --- |
| SV-FR-32 | **Not set up** (`MAIL_HOST` empty). In development and tests the code is written to the server log (as the reset link is today) so the flow can be tried. In production the dialog says "Email is not set up on this server, so we can't send a code"; the action continues with the V19 checks only and is logged as `STEP_UP_SKIPPED_NO_MAIL` (D-20-4). | M |
| SV-FR-33 | **Send fails** (mail server down or timeout). The dialog says "We couldn't send the code. Try again in a few minutes." Nothing is changed. The failure is logged. Repeated failures do not count against the person's code limits. | M |
| SV-FR-34 | **Admin warning.** While step-up is on and mail is not set up in production, admins see a banner on the admin pages: "Verification codes cannot be sent. Set up mail (MAIL_HOST)." | S |

### F. Carry-over found during V19 support

| ID | Requirement | Priority |
| --- | --- | --- |
| SV-FR-35 | **Delete confirmation word.** The box accepts `delete` in any case with spaces around it ignored, and, while the word does not match, the page says "Type DELETE to enable the button" instead of silently leaving it disabled. The Khmer page accepts the same English word and says so. | M |

## Non-functional requirements

| ID | Requirement |
| --- | --- |
| SV-NFR-01 | **Codes are never stored or logged in clear** in production. The server keeps an HMAC-SHA256 of the code with a server secret (a 6-digit code is too short for a plain hash), and compares in constant time. |
| SV-NFR-02 | **No new way to find accounts.** Every endpoint here needs a signed-in session, so nothing reveals whether an email is registered (SEC-07-02). |
| SV-NFR-03 | **Server-side checks only.** The frontend dialog is convenience; every protected endpoint checks the verified code or window itself, inside the same transaction as the change. A request that skips the dialog gets "verification needed". |
| SV-NFR-04 | **Speed.** Requesting a code answers within 2 seconds when mail works (the mail timeout is 10 s; the request fails cleanly at the timeout). Verifying answers within 300 ms. |
| SV-NFR-05 | **Audit.** New security log events: `STEP_UP_CODE_SENT`, `STEP_UP_VERIFIED`, `STEP_UP_FAILED`, `STEP_UP_LOCKED`, `STEP_UP_SKIPPED_NO_MAIL`, each with the action and outcome, never the code. The admin security log page can filter them. |
| SV-NFR-06 | **Rate limits** as new `RequestRateLimiter` actions `STEP_UP_SEND` (per person) and `STEP_UP_VERIFY` (per code and per person), configurable like the others. |
| SV-NFR-07 | **Privacy.** Codes, challenges and windows are deleted with the account and are not part of the data export. Expired challenges are removed by a daily clean-up after 7 days. |
| SV-NFR-08 | **Khmer.** Every new message, email and notice has a Khmer draft in `docs/km-review-v20.csv` for review. |
| SV-NFR-09 | **Feature switch.** `STEP_UP_ENABLED` (default `true`; tests can turn it off). Turning it off is logged at start-up and shown to admins. |
| SV-NFR-10 | **Quality.** Coverage minimums from BRD 12 (backend 94% lines, 75% branches; frontend 85/84/78/78) and a passing SonarQube gate. |

## Data and API changes (for solution design)

**Migration `V84__step_up_challenges.sql`**

| Table | Columns | Notes |
| --- | --- | --- |
| `step_up_challenges` (new) | `id`, `user_id` (FK, indexed), `session_id` (FK `auth_sessions`, `ON DELETE CASCADE`), `action` (VARCHAR 40), `target_hash` (VARCHAR 64, nullable: SHA-256 of a new reminder address), `code_hash` (VARCHAR 64), `attempts` (INT), `expires_at`, `verified_at` (nullable), `used_at` (nullable), `created_at` | One open challenge per session and action; added to `AccountService` delete order before `auth_sessions` |
| `auth_sessions` | add `verified_until` (TIMESTAMP, nullable) | The verified window (D-20-3); cleared on sign-out, password change and reset |

**API**

| Endpoint | Purpose |
| --- | --- |
| `POST /api/account/step-up` `{ "action", "target"? }` | Send a code. 201 `{ "challengeId", "sentTo" (masked), "expiresAt", "resendAfter", "mailConfigured" }`; 429 over the limits; 503 when sending fails |
| `POST /api/account/step-up/{challengeId}/verify` `{ "code" }` | 200 `{ "verified": true, "windowUntil" }`; 400 `{ "triesLeft" }` for a wrong code; 410 when ended or used up |
| Protected endpoints (`/api/auth/change-password`, `/api/account/delete`, `/api/account/export`, `PUT /api/account/reminders` with a new address, the admin endpoints in SV-FR-06 to SV-FR-08) | Take the verified challenge id (`X-Step-Up` header) or rely on the session's window where allowed. Without it: 403 with `"code": "STEP_UP_REQUIRED"` and the `action`, which the frontend turns into the dialog (SV-FR-25) |

**Model and services**

- `StepUpAction` enum: name, window rule (`WINDOW` or `FRESH`), fresh-within minutes, plain-language label key.
- `StepUpService`: request, verify, `require(action, challengeId?, target?)` called by each protected service method
  before it changes anything, and window bookkeeping on the session.
- `StepUpMailer` beside `PasswordResetMailer`, sharing the escaping and language handling; `AccountNoticeMailer` for
  SV-FR-28 to SV-FR-30, sent after commit.
- Frontend: `StepUpDialog` component, `useStepUp()` hook that wraps a protected call, and an `apiClient` branch for
  `STEP_UP_REQUIRED`.

## Implementation phases

| Phase | Content | Requirements |
| --- | --- | --- |
| 1 | Data and server core: migration, `StepUpAction`, `StepUpService`, code generation and HMAC, limits, security events, mailer with log fallback in development; unit and flow tests | SV-FR-10 to SV-FR-19, SV-FR-32, SV-FR-33, SV-NFR-01 to SV-NFR-07, SV-NFR-09 |
| 2 | Protect the participant actions and build the dialog: change password, delete account, data download; `StepUpDialog`, `useStepUp`, retry on `STEP_UP_REQUIRED`; delete-word fix | SV-FR-01 to SV-FR-03, SV-FR-09, SV-FR-20 to SV-FR-27, SV-FR-35 |
| 3 | Reminder email confirmation and change notices | SV-FR-04, SV-FR-05, SV-FR-28 to SV-FR-31 |
| 4 | Admin actions, admin mail warning, Khmer review file, docs (`API.md`, `DATA_MODEL.md`, `USER_GUIDE.md`, `SECURITY.md`), `UAT_V20.md` | SV-FR-06 to SV-FR-08, SV-FR-34, SV-NFR-08, SV-NFR-10 |

Each phase ends with the CI commands passing locally (`mvn verify`, `npm run build`, `npm run test:coverage`) and
stops for confirmation (`CLAUDE.md` working rules 13 to 15).

## Acceptance criteria

| ID | Criterion |
| --- | --- |
| SVC-01 | Pressing **Download my data** opens **Verify it's you**, sends one email with a 6-digit code to the account email, and shows the address masked. Typing the code starts the download. |
| SVC-02 | Calling `GET /api/account/export` (or any protected endpoint) without a verified code answers 403 `STEP_UP_REQUIRED`; with a code verified for a different action, a different session or a different account, it still answers 403 (automated). |
| SVC-03 | A code works once, stops after 10 minutes, and stops after 5 wrong tries with "Ask for a new code"; each wrong try shows the tries left. |
| SVC-04 | **Resend code** is disabled for 60 seconds; a sixth code within an hour answers 429 with the wait. |
| SVC-05 | After verifying for **Download my data**, changing the password within 10 minutes asks for the current password but not a new code; after 10 minutes it asks for a code again. |
| SVC-06 | Deleting the account always asks for a fresh code, the password and the confirmation word, even right after another verification. `Delete`, ` delete ` and `DELETE` all enable the button; another word shows "Type DELETE to enable the button". |
| SVC-07 | Changing the reminder email to a new address sends a code to that address; the address saves only after that code; **Send test email** is not offered before. A notice goes to the account email and to the old reminder address. |
| SVC-08 | Changing the password ends every other session and closes every verified window; the account email gets a "password changed" notice. |
| SVC-09 | The code email has the code in the subject, names the action, has no link, and is in Khmer for a Khmer-language account. |
| SVC-10 | With `MAIL_HOST` unset in development the code appears in the server log; in production the dialog explains that email is not set up, the action continues with the password check, and the log shows `STEP_UP_SKIPPED_NO_MAIL`; admins see the mail warning banner. |
| SVC-11 | With the mail server timing out, the dialog says the code could not be sent and nothing changes. |
| SVC-12 | An admin changing a role or the AI key is asked for a fresh code every time. |
| SVC-13 | The database holds only code hashes; no code appears in production logs or the security log (automated check on the log output in the flow tests). |
| SVC-14 | Deleting an account removes its challenges and windows; the data export does not include them. |
| SVC-15 | The dialog works with the keyboard alone, announces errors, offers the code from the email on iOS and Android (`one-time-code`), and fits a 320 px screen. |
| SVC-16 | CI passes on the release commit with every coverage minimum met and the SonarQube gate OK. |

## Assumptions, dependencies and risks

### Assumptions

- Every account has a working email address it signed up with. The app does not confirm the address at sign-up
  today; that comes in a separate small BRD (Q-06).
- Production has mail set up (`MAIL_HOST`) before V20 is switched on; reminders and password reset already need it.

### Dependencies

- Spring Mail and the existing mail configuration (`MAIL_*`).
- `auth_sessions` and the session id in the JWT (BRD 10 SEC-07-03).
- `RequestRateLimiter` and the security log (BRD 10).
- BRD 14 (Google sign-in) will depend on this for accounts without a password.

### Risks

| Risk | Impact | Mitigation |
| --- | --- | --- |
| Email is slow or lands in spam | People cannot finish a change | Code in the subject, plain text, short; 10-minute life; resend after 60 s; SPF/DKIM checked during rollout |
| Someone loses access to their email | They can no longer make protected changes | Ordinary changes still work; the password reset path is unchanged; a support process is out of scope for V20 (Q-05) |
| Codes guessed by brute force | Account takeover of protected actions | 1-in-a-million per try, 5 tries per code, 5 codes per hour, lockout signal (SV-FR-19), all bound to the session |
| Phishing ("send me your code") | A person reads the code to an attacker | The email says never to share it and names the action; codes work only in the session that asked |
| Mail not set up in production | Feature does nothing | D-20-4 fallback, admin banner, start-up log (SV-FR-34, SV-NFR-09) |
| Verification fatigue | People find the app tiresome | Only listed risky actions; 10-minute window for the settings group |

## Open questions

- [x] **Q-01** Should signing in from a new device or after a long absence also ask for an email code (two-factor sign-in)? **Answered by the product owner on 21 Nov 2026:** no, not in V20; a later BRD.
- [ ] **Q-02** Is 10 minutes the right verified window, and 10 minutes the right code life? (Draft: yes for both.)
- [x] **Q-03** Should **Download my data** need a code, or only the password? **Answered by the product owner on 21 Nov 2026:** yes, it needs a code (SV-FR-03).
- [x] **Q-04** In production without working mail, should protected actions continue with the password only (D-20-4) or be blocked until mail works? **Answered by the product owner on 21 Nov 2026:** continue with the password checks (D-20-4, SV-FR-32).
- [x] **Q-05** What is the support process when someone has lost access to their email? **Answered by the product owner on 21 Nov 2026:** nothing in V20. Ordinary changes keep working; a support process is left to a later BRD.
- [x] **Q-06** Should sign-up confirm the email address with a code too, so step-up codes are known to reach the owner? **Answered by the product owner on 21 Nov 2026:** yes, but in a separate small BRD, not V20.
- [ ] **Q-07** Should coaches' coach-only actions (comments, pitch ratings) be protected? (Draft: no; they change no account and are logged.)

## Sign-off

| Role | Name | Decision | Date |
| --- | --- | --- | --- |
| Sponsor | | | |
| Product owner | | | |
| Security reviewer | | | |

## Sources

- BRD 10.0.0 (SEC-05-06, SEC-06-02, SEC-06-05, SEC-07-01 to SEC-07-03, SEC-09), BRD 12.0.0 (coverage), BRD 14.0.0 (paused), BRD 19.0.0 (current Account page features).
- Code at commit `03a6a16`: `controller/AccountController.java`, `controller/AuthController.java`, `service/AccountService.java` (delete order), `service/AuthService.java` (`changePassword`, `endOtherSessions`), `service/PasswordResetService.java` (token pattern), `service/ReminderService.java` (reminder email), `service/mail/PasswordResetMailer.java`, `service/mail/ReminderMailer.java`, `service/support/RequestRateLimiter.java`, `security/audit/SecurityEventType.java`, `pages/AccountSettingsPage.tsx`, `pages/SecuritySettingsPage.tsx`, `application.yml` (`MAIL_*`, `password-reset.ttl-minutes`), migrations up to `V83`.
- Support finding of 21 Nov 2026: the strict `DELETE` confirmation word (SV-FR-35).
