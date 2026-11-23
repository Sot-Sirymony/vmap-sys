# BRD – Security Access (sign-in methods)

Nov 23, 2026

## Document control

This is draft 24.0.0. It keeps every requirement in BRD 23.0.0 (users, roles and permissions), BRD 21.0.0 (two-step
sign-in), BRD 14.0.0 (Google sign-in), BRD 10.0.0 (security) and the earlier BRDs. It turns the front-end prototype of
BRD 24.0.0.0 (`VMS_BRD_V24.0.0.0 – Security Access Control.md`, commit `a545f3d`) into a working page: an Admin
chooses which **sign-in methods** other people may use, and the server enforces the choice.

| Item | Value |
| --- | --- |
| Document | Business Requirements Document (BRD) – Security access addendum |
| Version | 24.0.0 – Draft |
| Parent documents | BRD 24.0.0.0 (prototype and catalogue), BRD 23.0.0 (D-23-9 Admin-only role editing; blocking ends sessions at the next request), BRD 21.0.0 (D-21-2 two-step sign-in for privileged roles, 14-day set-up), BRD 14.0.0 (Google sign-in), BRD 20.0.0 (step-up codes), BRD 10.0.0 (SEC-07-03 ending sessions, SEC-09 security log) |
| Business owner | To be confirmed |
| Product owner | To be confirmed |
| Approvers | Sponsor, Product owner, Security reviewer |

| Version | Change | Author |
| --- | --- | --- |
| 24.0.0 (phase 5 built) | Phase 5: `UAT_V24.md` (SAC-01 to SAC-08 and abuse cases SAC-09 to SAC-14), `km-review-v24.csv` (72 drafts; prototype text the page no longer shows was removed from `km.ts`), `USER_GUIDE.md`, `THREAT_MODEL.md` section 7, `ASVS_L2_CHECK.md` (V6 and V8 rows), `API.md`, CHANGELOG. All phases built; awaiting deploy, UAT, the Khmer review and sign-off | Dev |
| 24.0.0 (phase 4 built) | Phase 4 implemented (`SecurityAccessPage` on the API, `securityAccessApi.ts`, `useOfferedSignInMethods`, Sign In, Sign Up and invitation pages following the settings). Clarified during build: the menu item shows for *Manage roles* or *View security log* (menu items may now name several permissions, any one of which is enough); the sign-in pages read the offered methods once per page load, and the Google button now follows them too, so it hides when an Admin turns Google off; with password sign-in off, Sign In shows "Admin? Sign in with a password" (SA-FR-13); the save dialog lists each change with the sessions it will end or the set-up dates it will give; the page warns, from the counts, when a change will be refused for stranding people, and the server still decides; the two-step set-up prompt ("Set up now" / "Skip for now") now also asks participants who have a date, in words for participants; the catalogue is a collapsed, searchable reference with no switches; the prototype's rule helper (`securityRules.ts`) is no longer used by the page | Dev |
| 24.0.0 (phase 3 built) | Phase 3 implemented (`TwoFactorRequirementService.requiredFor`, `scopeChanged`, `roleTaken`). Clarified during build: "required" now means a privileged role or scope EVERYONE everywhere it was "privileged" (the set-up check between requests, turning two-step sign-in off, the admin report, dates after a reset); participants keep the choice of emailed codes; new accounts (self sign-up, Google sign-up, Add user) get their date at creation while EVERYONE is chosen; a role change to a non-privileged role keeps the date while EVERYONE is chosen; the start-up job follows the scope; EVERYONE is refused when two-step sign-in is turned off on the server; a participant past their date sees "Set up two-step sign-in to continue. Every account needs it."; the log entry says how many dates were given or cleared | Dev |
| 24.0.0 (phase 2 built) | Phase 2 implemented (`PUT /api/admin/security-access`, step-up action `ADMIN_SECURITY_ACCESS`, event `SECURITY_ACCESS_CHANGED` with an alert, `SignInMethodOffException` answered 403, Google failure reason `google-off`). Clarified during build: a refused right password is logged as a failed sign-in ("password sign-in off") but not counted towards the lock; the setting is checked again at the Google code exchange, at Google linking and at the two-step second step, in case it changed in between; starting the Google step is never refused, because the account is only known once Google answers and Admins must still reach it; people with a pending invitation (no password, no Google) are not counted as stranded, since they accept with whichever method is on; an unchanged request answers "Nothing to change."; changing the two-step scope is refused until phase 3 | Dev |
| 24.0.0 (phase 1 built) | Phase 1 implemented (migration `V92__security_access.sql`, `SecuritySettings`, `SecurityAccessService`, `GET /api/auth/sign-in-methods`, `GET /api/admin/security-access`). Clarified during build: the settings are not cached (SA-NFR-01), since sign-ins are rare and a cache would go stale across servers; the public answer is `OfferedSignInMethodsResponse` because `SignInMethodsResponse` already names the signed-in person's own methods (V14); every way of opening a session now records its method (password sign-in, sign-up and invitation acceptance as PASSWORD; Google sign-in, sign-up and linking as GOOGLE; the two-step second step keeps the first step's method); sessions from before V24 have no method and no switch ends them. No behaviour changed (full suite green) | Dev |
| 24.0.0 | First draft from the 24.0.0.0 review. Product owner answers, 23 Nov 2026: Q-01 real methods only; Q-02 Admin only; Q-03 signed out at the next request; Q-04 "two-step for everyone" is a switch with a 14-day set-up | BA |

## Executive summary

The 24.0.0.0 prototype listed 57 security methods with a switch each. A review found that about 45 of them do not
exist in VMS, so their switches could never do anything, and that some suggested defaults (Social login and
Token-based blocked) would sign everyone out if enforced.

V24 therefore keeps the prototype's page and catalogue, but only **three settings are real**:

1. **Password sign-in** – on or off for everyone except Admins.
2. **Google sign-in** – on or off for everyone except Admins.
3. **Two-step sign-in required for** – "Privileged roles" (today's V21 rule) or "Everyone".

The other catalogue items stay on the page as a **read-only reference**, each marked *In VMS*, *Partly in VMS* or *Not
in VMS*, so the business can see what exists and what a later BRD could add.

## Decisions recorded by this BRD

| ID | Decision |
| --- | --- |
| D-24-1 | (Q-01) Only methods VMS has get a switch: password sign-in, Google sign-in and the two-step scope. The rest of the catalogue is information only. A switch is added only when a method is built |
| D-24-2 | (Q-02) Only the Admin role changes these settings (like *Manage roles*, D-23-9). The page is gated on *Manage roles*; roles with *View security log* may view it read-only. No new permission |
| D-24-3 | (Q-03) Switching a method off signs out, at their next request, every non-Admin session opened with it, as blocking an account does in V23 |
| D-24-4 | (Q-04) "Two-step sign-in for everyone" is a setting. Switching it on gives each person without two-step sign-in 14 days to set it up (the V21 grace period); switching it back clears the deadline for non-privileged people who have not set it up, and never turns off two-step sign-in for anyone who has |
| D-24-5 | Admins are never affected by the method switches, so an Admin cannot lock themselves or other Admins out (prototype BR-06). Two-step sign-in already applies to Admins under V21 |
| D-24-6 | A change that would leave any active non-Admin account with no way to sign in is refused, with the number of people affected (prototype BR-02, made exact). At least one method always stays on |

## Scope

### In scope

- Server-side storage of the three settings, with who changed them and when.
- Enforcement at password sign-in, self sign-up, invitation acceptance, Google sign-in, Google sign-up and Google
  linking.
- Ending sessions opened with a method that is switched off (D-24-3); sessions remember the method that opened them.
- Two-step scope "Everyone" with set-up deadlines and the existing set-up prompt.
- The Security access page wired to the server: real switches, impact counts, reason and fresh code, security log.
- Sign In, Sign Up and invitation pages showing only the methods that are on.
- The catalogue as a reference list (from the prototype).

### Out of scope

- Switches for any method VMS does not have (passkeys, SAML, LDAP and the rest of the catalogue).
- Per-role or per-cohort method settings.
- Password policy settings (length, expiry) and Google provider settings, which stay in server configuration.

## Functional requirements

### A. Settings and reference

| ID | Requirement | Priority |
| --- | --- | --- |
| SA-FR-01 | The server keeps one record of security settings: password sign-in (on/off), Google sign-in (on/off), two-step scope (PRIVILEGED / EVERYONE), last changed by and at. Defaults: on, on, PRIVILEGED (today's behaviour) | Must |
| SA-FR-02 | `GET /api/admin/security-access` returns the settings and, for each method, how many active non-Admin accounts use it and how many could sign in no other way. Allowed with *Manage roles* or *View security log* | Must |
| SA-FR-03 | `GET /api/auth/sign-in-methods` (public) returns whether password and Google sign-in are offered, so the sign-in pages show the right options. Google is offered only when it is on here **and** configured (GA-FR-02) | Must |
| SA-FR-04 | The page shows the catalogue from the prototype as a reference list with the *In VMS* / *Partly* / *Not in VMS* marks; reference items have no switch | Must |

### B. Changing the settings

| ID | Requirement | Priority |
| --- | --- | --- |
| SA-FR-05 | `PUT /api/admin/security-access` changes the settings. Admin only (D-24-2); needs a reason and a fresh "Verify it's you" code (new step-up action *change sign-in methods*) | Must |
| SA-FR-06 | Refused when both methods would be off, when Google would be the only method but is not configured, or when any active non-Admin account would be left with no way to sign in (D-24-6). The answer names how many people and what to do (send a password reset, or ask them to link Google) | Must |
| SA-FR-07 | Switching a method off ends every open non-Admin session opened with that method (D-24-3) | Must |
| SA-FR-08 | Each change is logged (`SECURITY_ACCESS_CHANGED`) with the old and new values, sessions ended and the reason, and raises an admin alert | Must |
| SA-FR-09 | Before saving, the page shows what will change and how many people it affects; the save needs the reason and code | Must |

### C. Enforcement

| ID | Requirement | Priority |
| --- | --- | --- |
| SA-FR-10 | With password sign-in off, a non-Admin's right password is refused with "Password sign-in is turned off. Sign in with Google." Wrong passwords keep the generic answer and count as failures, so the setting reveals nothing about accounts | Must |
| SA-FR-11 | With password sign-in off, self sign-up with a password and accepting an invitation with a password are refused; the pages offer Google only | Must |
| SA-FR-12 | With Google sign-in off, Google sign-in, sign-up and linking are refused for non-Admins with "Google sign-in is turned off. Sign in with your password." | Must |
| SA-FR-13 | The Sign In page always keeps a way for Admins to use a password ("Sign in with a password" link) when password sign-in is off for others | Must |
| SA-FR-14 | With two-step scope EVERYONE, every active account without two-step sign-in gets a set-up date 14 days ahead (or keeps an earlier one); after it they may only set it up (TF-FR-34). Switching back to PRIVILEGED clears the dates of non-privileged accounts | Must |

## Non-functional requirements

| ID | Requirement |
| --- | --- |
| SA-NFR-01 | Settings are read from the database on every sign-in (one row by key, no cache), so a change applies at once on every server |
| SA-NFR-02 | Default settings reproduce today's behaviour exactly; phase 1 changes no behaviour |
| SA-NFR-03 | Every new endpoint is covered by the access-control sweep; the page and API refuse non-Admin changes server-side (prototype NFR-04) |
| SA-NFR-04 | Interface text has Khmer drafts; emails stay in English (as V20 to V23) |

## Data and API changes

- Migration `V92__security_access.sql`: table `security_settings` (single row, id 1: `password_sign_in`,
  `google_sign_in`, `two_factor_scope`, `updated_by_id`, `updated_at`), seeded with today's behaviour;
  `auth_sessions.sign_in_method` (`PASSWORD` / `GOOGLE`, null for sessions opened before V24).
- Endpoints: `GET /api/auth/sign-in-methods` (public), `GET /api/admin/security-access`,
  `PUT /api/admin/security-access`.
- Step-up action `ADMIN_SECURITY_ACCESS` (FRESH, 10 minutes). Security event `SECURITY_ACCESS_CHANGED`.

## Implementation phases

| Phase | Content | Behaviour change |
| --- | --- | --- |
| 1 | Migration V92, settings entity and service with cache, sessions record their sign-in method, the two GET endpoints, tests | None |
| 2 | Enforcement (SA-FR-10 to SA-FR-12), PUT with guards, step-up, session ending, log and alert (SA-FR-05 to SA-FR-08), tests | Only when an Admin changes a setting |
| 3 | Two-step scope EVERYONE (SA-FR-14), tests | Only when switched on |
| 4 | Frontend: Security access page on the API, sign-in pages follow the settings, Khmer drafts, tests | Page becomes real |
| 5 | `UAT_V24.md`, `km-review-v24.csv`, user guide, threat model, ASVS rows, CHANGELOG | None |

## Acceptance criteria

| ID | Criterion |
| --- | --- |
| SAC-01 | With default settings, everyone signs in exactly as before V24 |
| SAC-02 | An Admin switches password sign-in off (with reason and code): a participant signed in with a password is signed out at their next request; their right password is refused with the Google message; an Admin still signs in with a password |
| SAC-03 | An Admin switches Google sign-in off: Google-signed-in participants are signed out; the Google button disappears from Sign In and Sign Up |
| SAC-04 | Switching Google off while some participants have no password is refused and names how many |
| SAC-05 | Switching both methods off is refused |
| SAC-06 | Two-step scope "Everyone": a participant without it is prompted and must set it up after 14 days; switching back removes the requirement for those who have not set it up |
| SAC-07 | A coach or custom role with *View security log* sees the page read-only; every change call answers 403 |
| SAC-08 | Each change appears in the security log with the reason, and raises an alert |

## Open questions

None at this draft. The 24.0.0.0 questions on per-role settings, Super Admin, approval workflow and per-method settings
are out of scope (D-24-1, D-24-2).

## Sign-off

| Role | Name | Date | Signature |
| --- | --- | --- | --- |
| Sponsor | | | |
| Product owner | | | |
| Security reviewer | | | |
