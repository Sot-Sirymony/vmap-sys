# Changelog

System versions track BRD versions. Each release below implements one Business
Requirements Document in [`docs/`](.); the BRD is the source of truth for *what*
was required, this file records *what shipped and when*.

**Convention:** the system major version equals the BRD major version. A BRD
change is what opens a new version here — internal work that ships no BRD
requirement (refactors, tooling, UI polish) goes under *Unreleased* and does not
move the version.

**Where the version lives:** `frontend/package.json` (`4.0.0`) is the single
source of truth for the number shown in the UI — `vite.config.ts` reads it and
exposes it as `import.meta.env.VITE_APP_VERSION`, which the sidebar footer
renders. `backend/pom.xml` (`4.0.0`) is kept in step by hand. The prerelease
marker was dropped when the last BRD v4.0.0 requirement shipped (2026-07-25).

---

## Unreleased — no BRD change

Work on `main` after the last BRD v2.0.0 feature commit. None of this changes a
requirement; it is internal quality and UX work.

### Fixed
- **The Vermilion and Violet accents could not be saved.** FR-43 added them to
  `theme.ts` and to the swatch row but not to the backend's `AccentColor` enum,
  so `PUT /preferences/appearance` rejected either with a 400. The accent applied
  locally and the user was told only that it "could not be saved to your
  account" — with no indication that the accent itself was the reason. Both
  values are now in the enum and in `StoredAccent`.

  The class of bug matters more than the instance: each side was internally
  consistent, so nothing failed. `context/accent-wire.test.ts` now reads the Java
  enums and compares them against the frontend registries — accents, tones,
  fonts, presets, and interface styles — because that is the only place the two
  lists are ever seen together.

### Changed
- Clean Code refactor, Phases 0–2n (`375f8a26`…`e1ffef44`) — the God service was
  decomposed into per-entity services (`TaskItemService`, `GoalService`,
  `DreamService`, `VisionAreaService`, `DashboardService`, and others), with
  `EntityLookup`, `ProgressCalculator`, `ArchiveCascade`, and
  `PermanentDeleteCascade` extracted as collaborators. Behaviour unchanged;
  characterization tests added first (`375f8a26`). Plan:
  [CleanCode-Refactor-Plan.md](CleanCode-Refactor-Plan.md).
- Injectable `Clock` and a shared Excel schema (`02ab4dc1`).

### Added
- Sorting, selection, pagination, and search on all list pages (`619aaa70`).
- Permanent delete for archived records (`af9ecbfa`).
- **Interface style** (FR-48) — a ninth appearance setting, stored per account
  via `V20__user_interface_style.sql`. `CLASSIC` is the Fluent treatment that
  shipped before it and remains the default, so no existing user is redesigned;
  `MODERN` applies larger radii, soft diffused shadows, an accent-derived card
  wash, pill navigation, a sidebar search affordance wired to the existing ⌘K
  palette, and a greeting header with a partner stack in place of breadcrumbs.

  It is a separate axis rather than more theme presets because it decides *shape*
  and never colour — which is what lets it compose with all twelve accents, six
  tones, and high contrast without adding a single colour pair to validate.
  `styles/interface-style.test.ts` asserts that invariant in both directions
  (the TypeScript tokens and the `:root[data-style="modern"]` CSS block), and
  also that the two channels cannot drift apart, the same way
  `palette-vars.test.ts` guards the `--palette-*` variables.

---

## [15.0.0] — All 6 phases built; awaiting UAT, the live quality-test run and the Khmer review

Implements [BRD 15.0.0](VMS_BRD_V15.0.0.md): a page assistant and helpers on the Modules, module, Assessment,
Obstacles, Communication and Work Style pages. Two migrations: `V73__ai_use_work_style.sql` and
`V74__ai_page_switches.sql`.

### Added
- **Phase 1, page assistant (AH-FR-01 to AH-FR-12).** One side panel on the six routes, with **Help** and
  **Help with this** buttons, program help and rule checks that work without AI, a chat that sees the unsaved form,
  and change cards that only fill the form. Section B of the BRD decides what each page sends to Claude.
  `POST /api/ai/pages/assistant/check` and `/proposals`; chat through `/api/ai/chat` with `assistantPage`.
  The Dream Coach steps aside on these pages. Admin **Page assistant quality** card with per-feature metrics and the
  AHBO-01, 03 and 04 baselines (`GET /api/ai/pages/metrics`).
- **Phase 2, Obstacles (AH-FR-31 to AH-FR-39, AH-FR-41, AH-FR-42).** Classify, root cause in place, solutions, next
  task, partner matching, blocked tasks without an obstacle, which first, solved before.
- **Phase 3, Communication (AH-FR-43 to AH-FR-50, AH-FR-52 to AH-FR-54, AH-FR-61 to AH-FR-67).** Field guide, fill from
  a sentence, check, tone, rebuild, objections, follow-up list and draft, meeting prep, thank-you, which first, practise
  this ask, earlier messages as context.
- **Phase 4, Modules (AH-FR-13 to AH-FR-22).** Which module next (rule and AI), journey summary, answers in place,
  refine with kept rows, explain, check before saving, modules done elsewhere.
- **Phase 5, Assessment and Work Style (AH-FR-23 to AH-FR-30, AH-FR-59, AH-FR-60, AH-FR-68 to AH-FR-77, AH-FR-79).**
  Statement explanations, richer explanation, practise this chain, 4-week retake reminder, what changed; what my result
  means, put my style to work, balance my partners, estimate a partner's style, working with a partner, adapt to this
  partner, and the **Let AI helpers use my work style** switch (off by default).
- **Phase 6 (AH-FR-40, AH-FR-51, AH-FR-57, AH-FR-58, AH-NFR-08 to AH-NFR-12).** Conflict and criticism help (private
  note only when ticked; crisis wording gets the safety response), read a reply (never stored), per-page and per-cohort
  switches, program help editable through the interface text, the Khmer review export (`docs/km-review-v15.csv`, 447
  strings) and the quality test set (`page-assistant-eval/cases.json`, 44 cases, with a live run gated on
  `PAGE_EVAL_ANTHROPIC_KEY`).

### Fixed
- A page assistant proposal for an obstacle or message with linked plan items failed: linked records load lazily and
  the proposal view was read outside a transaction. Found while building Phase 2; regression test added.

### Still to do
- UAT, a live run of the quality test set with the pilot model, and the Khmer review of the 447 drafts.
- Lead-coach approval of the fixed practices per barrier and per work style (AH-FR-72).
- The open questions in the BRD, in particular the $6 share of the monthly cap (measure first) and the default of the
  work-style switch.
- AH-FR-78 (keep the previous work-style result) stays a later improvement.

## [11.0.0] — All 8 phases built; awaiting UAT, the live sample run and the Khmer review

Implements [`VMS_BRD_V11.0.0.md`](VMS_BRD_V11.0.0.md): AI plan builder improvements.

### Phase 1 — measure and fix the base (2026-11-06)

- **Ratings on plan suggestions (PB-FR-34).** Vision area, dream, goal, step and
  plan check answers carry a `requestId` and show thumbs up or down.
- **Outcome counts (PB-FR-35).** Each answer records how many items it
  suggested, how many were added as they were, and how many after editing.
  Counts only (migration V63).
- **Admin metrics (PB-FR-36).** *Plan builder quality* card on AI settings:
  acceptance, edits, ratings and cost per feature for the last 7, 30 or 90 days.
- **Dates checked on the server (PB-FR-15).** A suggested goal after its
  dream's date, or step after its goal's date, comes back without a date
  instead of being shown as if it fit.
- **All-or-nothing add with undo (PB-FR-31, PB-FR-32).** Goals and steps from
  the plan assistant are saved in one request; if one fails, none is saved.
  *Undo* archives what was just added.
- Vision areas and dreams from the start assistant are still added one at a
  time through the normal endpoints; only their counts are reported.

### Phase 2 — Build my plan guide and drafts (2026-11-06)

- **Build my plan (PB-FR-01, PB-FR-02, PB-FR-04).** A button on each dream opens
  `/dreams/:id/build`: six stages (Clarify, Goals, Steps, Tasks, Helpers and
  risks, Check and add) on one screen. Any stage can be skipped or revisited;
  the stepper shows new items per stage, and Check and add lists what blocks
  adding and what is worth a look.
- **Starts from the existing plan (PB-FR-03).** Existing goals and steps are
  shown and never changed; new steps can go under existing goals and new tasks
  under existing complex steps.
- **Works without AI (PB-FR-05).** Each stage has the program's own questions
  and manual add buttons; AI suggestion buttons appear only when AI is available.
  Steps can be suggested for a goal that is still only in the draft.
- **Drafts on the server (PB-FR-30, PB-FR-33, PB-NFR-09).** Saved as you work,
  resumed on any device, private to the owner, never plan data, removed after
  14 days (daily job), on add, on discard, with the dream and with the account
  (migration V64).
- **All-or-nothing add (PB-FR-31).** Goals, steps and tasks from the draft are
  saved in one transaction with the normal create rules; a problem names the
  item and nothing is saved. Undo now covers tasks.
- The existing plan assistant card stays on the dream page (PB-FR-07).
- Not yet: answering clarifying questions in place (Phase 3), AI task and
  helper/risk suggestions (Phases 4 and 5); the Helpers and risks stage shows
  the program's questions and links to Partners and Obstacles for now.

### Phase 3 — clarify and refine (2026-11-06)

- **Clarify the dream (PB-FR-08, PB-FR-10).** In the Clarify stage the plan
  builder asks up to three questions, one at a time, answered in place. A
  ticked answer is saved into the dream (why it matters, success, target date);
  nothing is saved otherwise.
- **Answers are used (PB-FR-09).** Answers are kept in the draft and sent with
  every later goal and step suggestion. A question asked while suggesting goals
  or steps is answered in place; **Suggest anyway** skips it (PB-FR-11).
- **Refine (PB-FR-19 to PB-FR-21).** Fewer, More, Smaller, Bolder, Earlier or
  Later, with an optional note, replace the suggestions the participant has not
  edited or locked, in place. **Another option** swaps one suggestion.
- The participant's answers, notes and kept items go to Claude inside the data
  block only, never in the instructions (AI-NFR-13); a test checks this.
- AI answers now update the draft from its latest state, so edits made while
  waiting are not lost.

### Phase 4 — richer steps and the task builder (2026-11-06)

- **Steps have effort and order (PB-FR-12).** Steps store estimated hours and
  the step of the same goal they start after (migration V65), through the normal
  step API too. Step suggestions now carry priority, complexity, effort and
  order; a suggested date before the step it waits for is left for the
  participant to pick (PB-FR-15).
- **Steps for all goals (PB-FR-13).** One button suggests steps for every goal
  in the guide that has none yet.
- **Task builder (PB-FR-14).** Suggest 2 to 8 tasks for a complex step, saved or
  only in the draft, each with owner, due date (within the step's date),
  priority and hours. Tasks can be refined, locked and swapped, and are counted
  in the plan builder metrics.
- The one-off plan assistant card keeps the suggested priority, complexity and
  effort when adding steps.
- Not yet: the regular step form and the Excel workbook do not show effort or
  order.

### Phase 5 — helpers and risks (2026-11-06)

- **Helpers (PB-FR-23).** The plan builder names the kinds of helpers a plan
  needs. The participant's own partners are matched on the server by support
  type and role, so partner names and contact details never go to Claude. A
  helper links a matching partner (only where that partner has no goal or step
  link yet, so nothing is overwritten) or becomes a new "To contact" partner.
- **Risks (PB-FR-24, PB-FR-25).** Up to five likely obstacles, each with type,
  severity, a first way to handle it, and the kind of helper that fits it from
  the CLAUDE.md guide. Added as Open obstacles, with the helper as the required
  partner.
- **Fears only when asked (PB-FR-26).** The fear journal is read only when the
  participant ticks it for that request, and only fears of this dream or of no
  dream; such risks are labelled.
- **Outreach (PB-FR-27).** After adding, a message draft for each new contact to
  copy and send.
- **Resources (PB-FR-40).** Approved resources first, others marked "check
  before use". The approved-resource rules now live in one place
  (`AiResourceIdeas`), shared with resource ideas.
- Undo also archives the partners and obstacles the add created; links given to
  existing partners stay.

### Phase 6 — check, fix, capacity and quality (2026-11-06)

- **Check my draft (PB-FR-28).** The plan check runs on the draft before it is
  saved: built-in checks (owner, date, past dates, after the goal, before the
  step it waits for, task after its step, resources, complex without tasks,
  goals without steps) always, and Claude's vague, timing and gap issues when AI
  is on. Checking saves nothing.
- **Fixes (PB-FR-29).** One click for clear fixes (make it mine, use the parent's
  date, go to Steps or Tasks). **Fix with AI** proposes a clearer title, a new
  date or the missing step, applied only if the participant chooses.
- **Hours a week (PB-FR-16).** The participant's hours are remembered (migration
  V66). Weeks where saved and new work pass them are flagged; **Spread the
  dates** moves new work a week at a time, only into weeks with room and never
  past its step's or goal's date.
- **Plan model (PB-FR-39).** Admins can set a separate model for plan building;
  other AI features keep the main model.
- **Sample set (PB-NFR-08).** 20 fixed sample dreams (10 English, 5 Khmer, 5
  vague) with a check that the set stays complete, and an opt-in run against the
  real API that checks dates and Khmer answers and reports cost
  (`PlanBuilderEvalRunTests`, see README). Not yet run with a live key.
- **Khmer review (PB-NFR-06).** `frontend/scripts/export-km-review.mjs` exports
  the 221 V11 Khmer drafts to `docs/km-review-v11.csv` for the reviewer (D-4).
  Not yet reviewed.

### Phase 7 — plan assistant (2026-11-07)

- **Plan assistant panel (PB-FR-41 to PB-FR-44).** On the Plan page and in Build
  my plan, beside the page on wide screens and full screen on smaller ones.
  **Help with this section** on each Plan page stage, Milestones and each builder
  stage. Shows what it is helping with, follows the builder stage unless the
  topic is kept, and offers three quick questions per section.
- **Section-only help (PB-FR-45, PB-NFR-12).** Chat answers see only the
  section's items and its plan check; never partner names or contacts.
- **Change cards (PB-FR-46 to PB-FR-48).** Small changes checked on the server
  against the section, the date limits and the owner rule; applied to the draft,
  or saved to the plan through the normal rules, only by choice. Rated and
  counted as `PLAN_ASSISTANT`.
- **Without AI (PB-FR-49).** The program's help, guiding questions and the
  rule-based check still show.
- **One assistant on screen (PB-FR-50).** The Dream Coach button steps aside on
  these pages; conversations are tagged with dream and section (migration V67).
- 125 more Khmer drafts in `docs/km-review-v11.csv`.

### Phase 8 — closing the gaps (2026-11-07)

- **Session limit (PB-NFR-03).** A Build my plan session (its draft) uses at
  most 15 AI requests. Past that, AI requests about that dream answer 429, the
  plan checks fall back to their rules, and the guide says why and carries on
  as a form. The guide shows how many requests are used. Crisis messages are
  never held back (migration V68).
- **Requests and cost per finished plan (PB-FR-36).** Every AI request in a
  session carries the session's key (migration V69), so each plan added from
  the guide records its exact requests and cost. The admin card shows finished
  plans with averages, and per feature the requests and cost per finished plan.
- **Arrange before adding (PB-FR-22).** Move up, Move down, Merge with the next
  one and Split into two on new goals, steps and tasks.
- **Continue into the guide (PB-FR-06).** After adding dreams, the dream
  assistant links to Build my plan for each.
- 15 more Khmer drafts.

## [10.0.0] — In progress (phases 1 to 6 built; phase 7 in part)

Implements [VMS_BRD_V10.0.0](VMS_BRD_V10.0.0.md): security hardening to the OWASP Top 10:2025.
No new user features.

### Phase 1: urgent fixes

- **Fixed** a disabled account could still sign in and use the API: `UserStatus.DISABLED`
  was stored but never checked (SEC-01-05). `AppUserPrincipal.isEnabled()` now reflects
  the status, so sign-in fails with the usual "Invalid email or password.", and
  `JwtService.isValid` refuses a token issued before the account was disabled.
- **Added** sign-in attempt limits (SEC-07-01, SEC-07-02, `LoginAttemptLimiter`): 5 wrong
  passwords for one email within 15 minutes lock that email for 15 minutes; 50 failures
  from one network lock that network for the window. Unknown emails are counted the same
  way, so the lock does not reveal who is registered. The answer is 429 with a
  `Retry-After` header. A successful sign-in clears the count; a completed password reset
  lifts the lock. All four numbers are environment variables.
- **Added** security headers on every API response (SEC-02-01, SEC-02-02): a CSP that
  allows nothing to load (`default-src 'none'; frame-ancestors 'none'`), HSTS on every
  response (Render ends TLS at its proxy, so Spring's HTTPS-only default never sent it),
  `Referrer-Policy` and `Permissions-Policy`. `X-Frame-Options` and `nosniff` were already
  sent by Spring's defaults.
- **Added** security headers for the frontend in `render.yaml`: CSP (scripts, fonts and
  styles only from the site; images from any https URL for dream pictures; API calls only
  to the backend), HSTS, `nosniff`, `X-Frame-Options`, `Referrer-Policy`,
  `Permissions-Policy`. Checked in headless Chrome against a local build: 0 CSP violations
  across the sign-in pages and 21 signed-in pages.
- **Added** an explicit upload limit of 10 MB (SEC-02-07). A larger file now gets 413 with
  a clear message instead of a 500.
- **Changed** `SETTINGS_ENCRYPTION_KEY` is required outside local/test (SEC-04-03, SEC-02-03):
  at least 32 characters and different from `JWT_SECRET`, or the backend refuses to start.
  `render.yaml` generates it. Values sealed before V10 with the JWT-derived key still
  decrypt, so the saved Claude key keeps working; it is re-sealed with the new key the next
  time an admin saves it.
- **Changed** the client-IP helper moved from `AuthController` to `util/ClientIp` so sign-in
  and password reset share it.
- **Added** `SecurityHardeningFlowTests` (9), `LoginAttemptLimiterTest` (9), and new cases in
  `SecretCipherTest` and `PasswordResetServiceTest`. Backend suite: 361 tests, all passing.

**Deploy note:** pushing this with the Render Blueprint creates `SETTINGS_ENCRYPTION_KEY`
automatically. A backend deployed without the Blueprint must have it set first, or it will
not start.

**Not yet done from SEC-07-02:** registration still answers "Email is already registered.",
which tells a caller an email has an account. Fixing it properly needs email verification
(see the BRD's open questions).

### Phase 2: access control

- **Added** role rules by path in `SecurityConfig` (SEC-01-03): `/api/admin/**` is for ADMIN only
  and `/api/coach/**` for COACH or ADMIN, checked before any controller or body validation. A
  plain user now gets 403 on all 38 in every case; before, 16 of them answered 400
  because the body was validated before the service's role check (none was ever open).
- **Changed** the nine copies of `requireAdmin()`/`requireCoach()` in services now call one
  rule, `security/Roles`, kept as a second layer behind the path rules.
- **Changed** the five AI plan and resource endpoints check that the dream, goal, step or
  vision area is the caller's before checking whether AI is available (SEC-01-02), so
  another user's record answers 404 whatever the AI state. Before, they answered 503 first
  (ownership was still checked when AI was on).
- **Added** `AccessControlSweepTests` (SEC-01-08, SAC-04), which reads every route from
  Spring's own route table: all 233 must answer 401 without a token (except the 7 public
  ones); all 38 admin and coach routes must answer 403 to a plain user; and the 106 routes that
  take a record ID must answer 404 when user B uses user A's record, with A's records unchanged
  afterwards. IDs inside request bodies (parent dream, goal, step, task, partner) are checked
  the same way. A new endpoint is covered automatically; one with an unknown kind of ID fails
  the test until it is added. Planting an ownership bug on goals made the sweep fail on the goal endpoints and on the
  body check, as it should.
- **Added** two guards in the same test: every write route under `/api/coach` must be on a
  short list of coach actions that change no participant data (SEC-01-04), and no request
  body may carry a field such as `userId`, `role`, `archived` or `createdAt` (SEC-01-06).
  All 57 request bodies passed.
- **Added** `CoachingFlowTests` case: a coach cannot read or change a participant's records
  through the participant's own endpoints (404), only through the coach workspace.
- **Added** `utils/safeUrl.ts` in the frontend (SEC-01-10): resource links and dream images
  are shown only when they are http or https addresses; anything else, such as
  `javascript:`, shows as plain text. The server already limited both fields;
  `LinkFieldValidationTest` now proves it.

### Phase 3: sessions

- **Added** server-side sessions (SEC-07-03, migration `V61__auth_sessions.sql`). Every sign-in
  opens one; the access token names it (claim `sid`), and every request checks that the
  session is still open and belongs to the same active user, in one query that replaced the
  old user lookup. Ending a session stops its tokens at once.
- **Changed** access tokens last 30 minutes instead of 24 hours (SEC-07-04). A refresh token
  (random 256 bits, stored on the server only as a SHA-256) renews them through the new public
  `POST /api/auth/refresh`, and is replaced on every use. A replaced token used again within
  30 seconds is two tabs racing (409, retry); later it means the token was copied, and the
  session ends. "Remember me" sessions last 30 days, others 12 hours.
- **Added** `POST /api/auth/logout`, which ends the session on the server. A password change
  ends every other session (the one making the change stays); a password reset ends all.
  Account deletion removes the sessions; ended sessions are deleted daily after 7 days.
- **Changed** access tokens are stricter (SEC-07-09): HS256 with the app key only (so
  `alg: none` is refused), issuer `vision-mapping`, and an expiry, subject and session are
  required.
- **Changed** the frontend keeps the access token in memory only (SEC-07-05); browser storage
  holds just the refresh token and the user's name. Each page load renews the session first
  (protected pages wait for it); the token is renewed a minute before it runs out; a 401 is
  answered by renewing once and retrying, including for downloads and the Dream Coach stream.
  Tabs take turns renewing (Web Locks) and read the newest token, so they never spend the same
  one. Signing out in one tab signs out the others. Being offline never signs anyone out.
- **Note:** sessions saved by earlier versions hold a 24-hour access token the server no longer
  accepts, so everyone signs in once after this deploy.
- **Why not an HttpOnly cookie:** the frontend and API are on separate `onrender.com`
  subdomains. That domain is on the Public Suffix List, so browsers treat them as different
  sites and would block the cookie as third-party. Revisit with a custom domain.
- **Added** `SessionFlowTests` (15), new cases in the password tests, and rewritten frontend
  auth tests (`AuthContext.test.tsx`, `AuthContext.expiry.test.tsx`, `ProtectedRoute.test.tsx`).
  Backend: 393 tests pass; frontend: 443. Checked in headless Chrome against a local build with
  2-minute tokens: sign-in, reload, two tabs at once, background renewal, logout in one tab
  ending both, refresh token refused after logout, no CSP violations.

### Phase 4: injection and data integrity

- **Added** formula-injection protection on Excel export (SEC-05-04). Values were already
  written as text; one that starts with `=`, `+`, `-`, `@`, tab or carriage return now also
  gets Excel's quote-prefix style, so it stays text even after someone edits the cell. Nothing
  visible changes, and an import reads the same value back.
- **Changed** Excel import refuses, before the backup snapshot and before anything is saved
  (SEC-05-05, SEC-08-02): files not named `.xlsx`; macro-enabled workbooks, even when renamed
  to `.xlsx`; files that are not workbooks (a clear message instead of a 500); more than 30
  sheets or 5,000 rows in a sheet. POI's zip-bomb checks are set explicitly (a part may not
  inflate more than 100 times or past 50 MB). Error messages no longer include internal
  exception text.
- **Changed** a formula cell is imported as the value it last showed, never as its text and
  never evaluated (SEC-05-05).
- **Changed** each imported row is checked with the same rules as the form before it is saved
  (SEC-08-01). A row that fails, such as a 161-character vision area name, is skipped with the
  reason ("name: size must be between ...") and the other rows still import, instead of the
  database refusing it later.
- **Changed** every text, list and map field in a request body now has a maximum size
  (SEC-05-09). 13 had none: passwords, the sign-in email, the reset token, status and language
  fields, the AI model, the reorder lists and module field labels. A new guard in
  `AccessControlSweepTests` fails the build for any new field without one.
- **Changed** records sent to Claude go through one helper, `AiContextBuilder.asParticipantData`,
  which wraps them in the data block and neutralises every angle bracket inside it
  (SEC-05-07). Before, each field was neutralised by `clip()`, but a value that skipped it could
  have closed the block. Claude's output still never saves or sends anything by itself.
- **Added** `ExcelSecurityFlowTests` (8), `ParticipantDataBlockTest` (2), and
  `security-sinks.test.ts`, which fails if the frontend ever uses `dangerouslySetInnerHTML`,
  `innerHTML`, `eval` or similar (SEC-05-02, SEC-05-03; none is used). Backend: 404 tests
  pass; frontend: 444.
- **Checked, no change needed:** SQL is parameterised everywhere; the only SQL built from strings
  uses table names from a fixed list in code (SEC-05-01). Emails are plain text with fixed
  subjects and recipients from the database (SEC-05-06). JSON never accepts type names from a
  request (SEC-08-05).
- **Not done:** SEC-05-08 (checking Claude's replies for leaked instructions or other users'
  data) is a Should and is left for later. Claude is only ever given the caller's own records,
  so another user's data cannot appear in a reply.

### Phase 5: logging and alerting

- **Added** a security event log (SEC-09-01 to SEC-09-03, migration `V62__security_events.sql`):
  sign-ins (success, failure, lock), registration, logout, password change and reset, refresh
  token reuse, invalid tokens, refused requests (403, another user's record, rate limits), role
  changes, coach views of a participant, share links created, opened (and refused) and stopped,
  Excel import and export, account export and deletion. Each row has the time, account number,
  network, target and outcome; never a password, token, key, email address or user text (a failed
  sign-in keeps a SHA-256 of the email). Events are saved in their own transaction, so a refused
  sign-in is recorded even though its request rolls back.
- **Added** an audit trail of admin changes (SEC-08-06) through one annotation, `@SecurityAudit`:
  AI settings, the Claude key, approved resources, module content and order, interface text,
  cohorts and members, and issue triage record who did what to which record, and whether it
  worked.
- **Added** the log is append-only: its repository has no update or delete except the daily job
  that removes rows older than 365 days. Rows stay when an account is deleted (they hold only the
  account number).
- **Added** alerts (SEC-09-04): 10 failed sign-ins for one email or 50 from one network within 15
  minutes, 20 refused requests by one user, a reused refresh token, the admin role granted, and
  the Claude key changed or removed. Each goes to the log as a `SECURITY ALERT` line, at most once
  an hour, and is emailed to `SECURITY_ALERT_EMAIL` when that is set (new in `render.yaml` as a
  dashboard value).
- **Added** Settings → Security log for admins (SEC-09-05): filter by event, outcome and account,
  with the recent alerts on top. Read-only; there is no endpoint that changes the log.
- **Fixed** with no mail server configured, the password reset email wrote the live reset link to
  the log in every environment, so anyone reading production logs could take over that account
  (SEC-09-07). The link is now printed only on a local or test run. Reminder and reset mail logs
  mask email addresses (`mo***@gmail.com`).
- **Added** `docs/SECURITY.md`: how to report a vulnerability, the controls in place, the log and
  alerts, and the incident response plan with exact steps to disable an account, end all sessions
  and rotate each secret (SEC-09-06).
- **Added** `SecurityLogFlowTests` (11) and `SecurityLogPage.test.tsx` (2). Backend: 415 tests pass;
  frontend: 446.
- **Not done:** an AI budget-spike alert (SEC-09-04 lists it); the V9 80% budget warning still
  applies.

### Phase 7, part A: error handling, rate limits and passwords

- **Added** a 12-character `reference` to every error response (SEC-10-02). A 500 or 503
  logs its real cause under the same reference; the client sees only a safe message, and
  the frontend shows the reference for server faults so the user can report it.
- **Fixed** a failed session check (database down or timing out) let the exception escape
  the token filter. It is now refused with 503 before any endpoint runs, public ones
  included (SEC-10-01, SAC-14). Database failures elsewhere answer 503 instead of 500.
- **Added** timeouts for the mail server (10 s) and the database (connection 5 s, query
  30 s); Claude (60 s) and Redis (0.5 s) already had them (SEC-10-04).
- **Added** `RequestRateLimiter` (SEC-06-02): registration per network, join codes per
  account and per network, share link openings per network and Excel imports per account,
  each answering 429 with `Retry-After`. The AI daily limit now sends `Retry-After` too.
  Join codes already met SEC-06-03 (about 40 bits, replaceable, dead when archived).
- **Added** `PasswordPolicy` (SEC-07-06): common and breached passwords, the account's own
  email and the app's name are refused at registration, password change and reset.
  Tests now use an uncommon password instead of `Password123`.
- Checked, no change needed: cache keys always name the user and fail without one
  (SEC-10-05, now with a unit test); imports, cascades and account deletion already run in
  one transaction (SEC-10-03); failed Claude calls return only a generic message.
- Tests: `ErrorHandlingFlowTests`, `RateLimitFlowTests`, `RequestRateLimiterTest`,
  `PasswordPolicyTest`, `UserScopedKeyGeneratorTest` (SEC-10-06).
- Not yet done in Phase 7: two-factor sign-in (SEC-07-07), the threat model (SEC-06-01),
  abuse cases in acceptance criteria (SEC-06-04) and the final ASVS Level 2 check.

### Phase 6: supply chain

- **Added** `.github/workflows/ci.yml`, the repository's first CI (SEC-03-01, SEC-03-04): on every
  push and pull request to `main`, and weekly, it runs the backend tests, the frontend build and
  tests (installed with `npm ci`), and a security scan. The scan builds the backend image and
  checks it with Trivy (OS packages and Java libraries), checks the frontend lock file including
  build tools, and runs `npm audit` on production packages; any High or Critical issue with a
  fix fails the build. On `main` it saves CycloneDX software bills of materials for 90 days.
  Actions are pinned to commit SHAs and Trivy to version and digest; `actionlint` passes.
- **Added** `.github/dependabot.yml`: weekly, grouped update pull requests for Maven, npm, Docker
  images and GitHub Actions (SEC-03-03).
- **Changed** Render deploys only after the CI checks pass (`autoDeployTrigger: checksPass`), and
  the frontend builds with `npm ci` instead of `npm install` (SEC-08-03, SEC-03-02).
- **Changed** the backend image pins both base images to version and digest
  (`maven:3.9.16-eclipse-temurin-17`, `eclipse-temurin:17.0.21_9-jre-alpine`) and runs as a
  non-root `app` user (SEC-03-05, SEC-02-06).
- **Added** `backend/maven-settings.xml`, used by the Dockerfile and CI: every Maven request goes
  to Maven Central. Before, some libraries' POMs made Maven also try a Gradle repository, an
  Amazon S3 bucket and GitHub Packages.
- **Fixed** frontend build-tool advisories by updating the lock file within the existing version
  ranges (`browserslist`, `nanoid`, `source-map-js`, `vite` 7.x, `baseline-browser-mapping`).
  Production packages had none. Two Critical `tinypool` advisories in the Vitest test runner
  remain, accepted in `.trivyignore.yaml` until 7 Dec 2026: the fix needs Vitest 4, which npm
  10.9.2 cannot install here (an npm peer-resolution bug).
- **Added** the supply-chain section of `docs/SECURITY.md`: update deadlines (Critical 7 days,
  High 30 days), the checklist for adding a dependency, and the GitHub settings the owner must
  turn on (branch protection with required checks, two-factor authentication, Dependabot alerts).
- IMAGE_SCAN_RESULT

## [9.0.0] — All 9 phases built; awaiting UAT

Implements [VMS_BRD_V9.0.0](VMS_BRD_V9.0.0.md): Claude AI integration, which first brings
back coaches and cohorts (decisions D-1, D-6 to D-8).

### Phase 1: cohorts and coaches

- **Added** the COACH role, cohorts with join codes (migration `V52__cohorts_and_coaching.sql`),
  and joining at registration (`/register?code=...`) or from Account settings.
- **Added** Cohorts page for admins: create, name the coach, start date, new join code,
  members by email, archive.
- **Added** Coaching page for coaches: their cohorts' participants, quietest first, with
  the six objectives and an at-risk flag after 7 days with no activity. A read-only
  participant view shows the plan; journals only when the participant shares them.
- **Added** coach comments on a participant's plan, shown on the participant's Today page.
- **Changed** `CLAUDE.md` rule 14 now names the coach exception. Every coach request is
  checked on the server; anyone outside the coach's active cohorts answers 404.
- **Added** `CoachingFlowTests` (7) and `e2e/coaching.e2e.ts`, which runs only when
  `PGPASSWORD` is set because it promotes a test account to admin.

### Phase 2: partner view and coach pitch rating

- **Added** share links (v0.1 FR-10): on a dream's page, a participant makes a read-only
  link for a partner or mentor, optionally naming the partner and what they are asking
  for. Links end after 30, 90 or 365 days and can be stopped at any time; the owner sees
  how often each was opened. Migration `V53__plan_shares_and_pitch_rating.sql`.
- **Added** the public page `/shared/{token}`: one dream's goals and steps, progress and
  the ask, with no sign-in. Journals, tasks, step owners and other dreams are never shown.
  Only a SHA-256 of the token is stored, so the link is shown once.
- **Added** coach rating of a pitch (v0.1 FR-11): the coach's participant view shows the
  module 11 pitch with a 1 to 5 rating and feedback; the participant sees it on the
  module page. Changing the pitch clears the rating so it always matches.
- **Added** `PlanShareFlowTests` (4), a pitch test in `CoachingFlowTests`, and
  `SharedPlanPage.test.tsx` (2). Khmer drafts for every new string.

### Phase 3 groundwork: AI settings

- **Added** Settings → AI settings for admins (AI-FR-24): enter, replace or remove the
  Claude API key without a restart, choose the model, set the monthly budget ($20 by
  default, D-3) and turn AI on. The key is stored AES-GCM encrypted (migration
  `V54__ai_settings.sql`); only its last four characters are shown. **Test connection**
  looks up the model through the Models API, so it costs nothing.
- **Added** the `anthropic-java` SDK and the optional `SETTINGS_ENCRYPTION_KEY` and
  `ANTHROPIC_API_KEY` environment variables (a saved key takes priority).
- **Added** `AiSettingsFlowTests` (4), `SecretCipherTest` (3), `AiSettingsPage.test.tsx` (2).

### Phase 3: AI foundation

- **Added** AI consent (AI-NFR-03): one switch in Account settings, also offered on the
  Dream Coach page. Every give and withdraw is recorded. Without it, no AI runs.
- **Added** the Dream Coach page (`/dream-coach`): conversations kept until deleted
  (AI-FR-25), replies labelled "AI suggestion" (AI-FR-05), thumbs up/down (AI-FR-06),
  answers in English or Khmer (AI-NFR-16). Plan and module context come in Phase 4.
- **Added** one backend path to Claude (`ClaudeClient`, official Java SDK, low effort,
  server-side refusal fallback on Opus and Sonnet) with coaching guidelines that decline
  financial, legal, medical and therapy advice and forbid invented facts (AI-NFR-08, -10,
  -11, -13).
- **Added** spend control (AI-NFR-15): the monthly budget pauses AI when reached, a
  warning at 80%, and a per-person daily message limit. Spend is shown on AI settings.
- **Added** the fallback (AI-NFR-02): when AI is off, over budget or unreachable, the
  API answers 503 "AI help is temporarily unavailable", nothing is saved, and the rest
  of the app is unaffected.
- **Added** crisis handling (AI-NFR-09, D-5): crisis words in English or Khmer get a
  caring reply with the help lines (editable on AI settings) without calling Claude, and
  Claude can flag one itself. The coach sees a wellbeing alert on the Coaching page (who
  and when, never the text); with no coach, administrators see it.
- **Added** the usage log (AI-NFR-14): time, feature, model, tokens, cost, outcome and
  rating, no text. Deleting an account deletes the chat history and consent record and
  removes the user from log rows, which still count toward the month's spend.
- **Added** migration `V55__ai_foundation.sql`, `DreamCoachFlowTests` (6) and
  `DreamCoachPage.test.tsx` (4).

### Phase 4: Dream Coach

- **Added** the Dream Coach on every screen (AI-FR-01): a button opens a side panel; the
  conversation carries on between pages. Hidden while AI is switched off.
- **Added** three quick questions per kind of page (AI-FR-02), such as "Help me start this
  exercise" on a module or "What should I do first today?" on Today.
- **Added** the context builder (AI-FR-01, AI-NFR-04, AI-NFR-13): the dream or module on
  screen, the plan, tasks due within a week, and on journal pages the attitude check-ins,
  fear log and open obstacles. Never partner names or contacts, never private conflict
  notes. Records are wrapped as data and angle brackets are neutralised, so text in a
  journal cannot change Claude's rules.
- **Added** streamed replies (AI-NFR-01): `POST /api/ai/chat/stream` sends server-sent
  events; the saved reply replaces the streamed text when it is done. A crisis marker at
  the start is held back, never shown. If the browser leaves, the reply is still saved
  and counted.
- **Added** "Based on your plan and your tasks" under each reply (AI-FR-05), stored with
  the message (migration `V56__ai_message_context.sql`), and a copy button.
- **Added** 3 tests to `DreamCoachFlowTests`, `aiFocus.test.ts` (3) and
  `DreamCoachLauncher.test.tsx` (2).
- **Added** ways to keep chats short: **Delete all conversations** on the Dream Coach page,
  **Clear this chat** in the side panel, and a note suggesting a new conversation once a
  chat reaches 20 messages (only the latest 20 are sent to Claude, and long chats cost
  more). `DELETE /api/ai/conversations`; one more test on each side.

### Phase 5: goal and plan builders

- **Added** the plan assistant on each dream's page: **Suggest goals** turns the dream into
  1 to 5 SMART goals (AI-FR-07); **Suggest a stretch goal** offers one bold goal (AI-FR-10);
  **Suggest steps** breaks a chosen goal into 3 to 10 steps with owner, date, resources and
  people (AI-FR-08). Every suggestion is shown as an editable draft with a reason; only the
  ones the participant ticks and adds are saved (AI-FR-04, AC-09). A vague dream or goal
  gets one clarifying question instead of guesses (AI-FR-03).
- **Added** **Check my plan** (AI-FR-09): built-in rules always flag a missing owner, date
  or resources, a goal with no steps, a step due after its goal and overdue steps, so they
  work with AI off (AC-10, AI-NFR-02). With AI, Claude adds vague steps, unrealistic
  timing and gaps, each with a fix; issues pointing outside the participant's plan are
  dropped.
- **Added** **Steps that keep slipping** on Today (AI-FR-11): a step given a new date while
  overdue, and overdue again, gets **Get ideas**: a smaller step, a new date or a helper,
  each one used only if the participant chooses it.
- **Added** a **Resources and people** field on steps (v0.1 FR-05), and a count of how often
  an overdue step was moved (migration `V57__step_resources_and_slips.sql`). Saving a step
  without the field keeps what was there.
- **Changed** Claude answers the builders in structured JSON at medium effort; every field
  is checked (dates in the future, known priorities, length limits) before it is shown.
- **Changed** the daily limit now counts all AI requests (chat, builders, plan check), not
  only chat messages; failed requests and crisis answers do not count. Labels say "AI
  requests".
- **Added** `AiPlanFlowTests` (7), `AiPlanAssistant.test.tsx` (3) and
  `SlippingStepsCard.test.tsx` (2).

### Phase 6: daily use

- **Added** **What should I do now?** on Today (AI-FR-12): pick 15 minutes to 2 hours and
  get the best next step or task from the participant's own open work, with up to two
  alternatives and a reason. Items from anyone else's plan are dropped. No AI call when
  nothing is open.
- **Added** **Resource ideas** for a goal in the plan assistant (AI-FR-13): courses, books,
  tools, organisations, funding and kinds of people. Links are stripped and every idea is
  marked "check before use" until admins keep an approved list (AI-FR-26, Phase 9).
- **Added** **Draft with AI** on the weekly review form (AI-FR-14): fills summary, done,
  blocked, lessons and next priorities from the last seven days; the participant edits
  and saves.
- **Added** AI encouragement in reminder emails (AI-FR-15): a line quoting the
  participant's own "why it matters". Admin switch on AI settings, off by default
  (migration `V58__ai_reminders.sql`); needs the participant's consent, counts toward
  budget and daily limit, and any failure leaves the reminder as it was.
- **Added** the **Pitch coach** on module 11 and the Communication page (AI-FR-16): the AI
  plays a partner, buyer or sponsor, reacts, scores clarity, benefit and ask from 1 to 5,
  and suggests better wording.
- **Added** **Draft with AI** on the Communication form (AI-FR-17): subject and message
  from the request, purpose, dream and partner. The partner's name and role may be used;
  their email and phone are never sent. The participant sends it themselves.
- **Fixed** a query parameter outside its limits (such as `?minutes=1` or `?language=fr`)
  answered 500; it now answers 400.
- **Added** `AiDailyFlowTests` (6), `NextStepCard.test.tsx` (2), `PitchCoachCard.test.tsx` (1).

### Phase 7: module help

- **Added** the **Module helper** on every module page (AIB-01 to AIB-15): **Help me start**
  gives up to three questions, shown one at a time (AI-FR-03), and an example about a
  made-up person; **Draft my answer** fills the exercise form from the participant's own
  records; **Feedback on my answer** reviews what they have typed. Each module has its own
  focus, such as the smallest safe test for fears (module 2) or a passion statement
  (module 14). Modules 2, 3 and 10 also see the fear log, attitude check-ins and open
  obstacles.
- **Changed** a draft only fills the form on screen: the module counts, and unlocks the
  next one, only when the participant saves it (FR-07 change, AC-09). Only the module's
  own text fields are sent or filled; rating fields stay for the participant to score.
- **Added** **Explain my result** on the Assessment page (FR-02 change): the latest scores in
  plain words, change since the first assessment, and one module to focus on next (only a
  module that exists is shown).
- **Fixed** calling an endpoint with the wrong HTTP method answered 500; it now answers 405.
- **Added** `AiModuleFlowTests` (4) and `ModuleHelpCard.test.tsx` (2).

### Phase 8: coach co-pilot

- **Added** the **Weekly AI summary** on a participant's coach page (AI-FR-18): progress
  against the plan, mood trend, risks with reasons and one suggested action. Kept for
  the week (migration `V59__ai_coach_summaries.sql`), so reopening it costs nothing; the
  coach can make it again and rate it accurate or not (AC-16).
- **Added** risk signals with their reasons on the Coaching list (AI-FR-19): falling
  completion, steps overdue again after a new date, three or more overdue items, and a
  falling or low attitude (only when journals are shared). Plain rules, free and
  repeatable; the summary explains them. A participant with signals but not yet quiet
  for 7 days shows "Needs attention".
- **Added** **Draft with AI** in the coach's comment box (AI-FR-20): a short comment on the
  chosen dream, goal, step or the participant overall; the coach edits it and sends it.
- **Added** the cohort summary on the Coaching page (AI-FR-21): overview, shared blockers,
  modules where people are stuck and suggested actions, from participants who gave AI
  consent only, with no names sent.
- **Changed** the co-pilot sees only what the coach may see (D-6, D-8): journals only when
  shared, and nothing about a participant without their own AI consent (AI-NFR-03). It
  uses its own guidelines (describe behaviour, never diagnose), and its requests do not
  use the coach's daily limit.
- **Added** `AiCoachFlowTests` (5), `CoachSummaryCards.test.tsx` (2) and Khmer patterns for
  the risk reasons.

### Phase 9: admin AI

- **Added** an **AI help** switch per cohort on the Cohorts page (AI-FR-24), on by default.
  Off means no AI for that cohort's members (the Dream Coach hides, AI requests answer
  "unavailable") and no co-pilot summaries for their coach (migration `V60__ai_admin.sql`).
- **Added** **Program coaching guidelines** on AI settings (AI-FR-24): the admin's own
  guidance, added after the built-in rules and marked as never overriding them, so an
  edit can tune tone and examples but cannot switch off a safety rule.
- **Added** **Approved resources** on AI settings (AI-FR-26). Resource ideas use these
  first, with the admin's own name, kind and link and an "Approved by the program" mark;
  anything else stays "check before use" without a link (AI-FR-13).
- **Added** **Draft with AI** in the English module editor (AI-FR-22): clearer text in
  original wording, two or three examples and up to five quiz questions; the text only
  fills the form for the admin to save (the app has no quiz yet, so questions are for
  copying).
- **Added** **Translate from English with AI** in the Khmer module editor and **Suggest
  Khmer** for each interface text (AI-FR-23), using a fixed glossary for the app's terms.
  A draft that loses a `{placeholder}` is dropped. Drafts go to the admin, ideally checked
  by the Khmer reviewer (D-4), before saving.
- **Changed** admin AI requests, like coach ones, do not use the admin's own daily limit.
- **Added** `AiAdminFlowTests` (5) and `ModuleContentAiCard.test.tsx` (2).

## [8.1.0] — Phase 5 built (Strengths and Gaps); Khmer text awaiting review

Implements the v0.2 additions in [VMS_BRD_V8.1.0](VMS_BRD_V8.1.0.md): FR-79 to FR-84,
NFR-11, BR-61 to BR-64.

- **Added** the Strengths and Gaps page (`/strengths`, under Plan): strengths and
  weaknesses with required evidence, relevance to one dream, an optional outside view with
  a mismatch flag, and suggestions from Work Style and six-chain scores of 2 or lower that
  are saved only once confirmed.
- **Added** one way to cover each high-relevance weakness. Partner creates a partner
  (To Contact); Delegate and Resource add a row to module 7 or 6 (saving that module's
  exercise if it had none); Learn adds a dated step to one of the dream's goals, at most 2
  at a time across all dreams; Accept needs a reason. Removing the gap or its cover asks
  whether to remove that record too. The last row of a module exercise is always kept, so
  a finished module never becomes unfinished.
- **Added** the one-page profile, saving and retakes with what changed, a `STRENGTHS`
  stage on the Plan page after the dream statement (it does not affect `planReady`), a
  *Strengths and gaps* section in the plan PDF, and a link from the Assessment page and its
  retake reminder.
- **Added** tables `self_assessment_items` and `self_assessment_snapshots` (migration
  `V71__strengths_and_gaps.sql`), included in the account export and in account deletion.
- **Added** about 120 Khmer entries (drafts for a Khmer speaker to review).
- Tests: `StrengthsAndGapsFlowTests` (8), `StrengthsPage.test.tsx` (6), plus updates to
  the access sweep, export shape, plan status, Plan page and API contract tests.

## [8.0.0] — In progress (all 4 phases built; NFR-03 partial, see Phase 4)

Implements [VMS_BRD_V8.0.0](VMS_BRD_V8.0.0.md) for personal use. See its
*Personal-use scope* section for what was dropped and how IDs were renumbered.

### Interface text editing (FR-20)

- **Added** Settings → Account → *Edit interface text* (admins only): search any text
  the app shows, change it in English or Khmer, or reset it to the default. Changes apply
  to every account on this server and keep placeholders such as `{count}`.
- **Added** `ui_text_overrides` (migration `V51__ui_text_overrides.sql`),
  `GET /api/ui-text` (public, so the sign-in page uses it) and `/api/admin/ui-text`.
- The app caches the changes on each device and refreshes them on load; text built at
  start-up picks up a change on the next load.

### Khmer for every page (NFR-03)

- **Added** Khmer for the rest of the interface: every page, dialog, menu, status and
  priority label, chart legend, empty state and error message (about 1,080 new entries in
  `i18n/km.ts`). Text with numbers or names in it uses placeholders, so word order can
  follow Khmer grammar.
- **Added** `tr()` in `i18n/translate.ts`, which any file can call, not only components.
  Changing the language now reloads the page once, so lists built when the app loads,
  such as table columns, also switch.
- **Added** `i18n/formatDate.ts`: Khmer day and month names built in, because Chrome
  ships without Khmer date data and quietly showed English dates.
- **Added** Khmer for server text that has numbers in it (plan stages, objective targets),
  matched by pattern in `translate.ts`.
- **Added** `e2e/khmer.e2e.ts`: every signed-in page in Khmer on a phone, checked for
  sideways scroll and accessibility, with any English still on screen reported.
- **Kept in English on purpose:** your own data, brand and font names, the word typed to
  confirm deleting the account, and partner messages generated by the Communication
  Builder, since those are sent to other people.

### Verification: dialogs, phone check-in, reminder email

- **Added** `e2e/dialogs.e2e.ts`: opens each kind of dialog (the create forms, the vision
  area wizard, Report an issue, an archive confirmation, the command palette and keyboard
  shortcuts) and checks axe rules, an accessible name, focus held inside, Escape closing
  it, and no sideways scroll on a phone in dark mode (NFR-02).
- **Fixed** from those checks: the command palette nested buttons inside list options and
  had no accessible name. It is now a standard combobox (focus stays in the search box,
  the active option is announced). Its "Go to" labels and the ticked dream coaching
  questions were also below AA contrast.
- **Added** an NFR-01 browser check: the whole daily check-in on a phone screen is three
  taps (attitude, habit, due item) with no scrolling sideways.
- **Added** `ReminderSmtpDeliveryTests`: a reminder sent over real SMTP to an in-process
  server (GreenMail) arrives with the right sender, recipient, subject and Today link
  (FR-18).
- **Fixed** A missing required query parameter (for example `date` on `POST /api/habits`)
  answered 500 instead of 400.
- **Added** A reminder email address: reminders can go somewhere other than the login
  email (blank uses the login email), and a *Send test email* button sends one now. The
  reminder card is on the Dashboard as well as the Account page. Migration
  `V50__reminder_email.sql`; the data export includes the address.
- **Checked** the production Docker image for the first time: built, then run with
  Render's limits (512MB, 0.5 CPU) against a throwaway database. It starts in about 30s
  natively, uses about 400MB, logs no errors, and returns a correct Khmer plan PDF.
- **Fixed** Khmer vowel signs in plan PDF table rows sat on the cell border; rows now size
  to the font's real height.

### Browser test suite

- **Added** `npm run test:e2e` in `frontend/`: Playwright checks of the main V8 flows, plus
  axe (WCAG A and AA) on every signed-in page at desktop, dark mode and phone width, and
  on the signed-out pages. It needs the backend running locally and Chrome installed,
  and each run creates and deletes its own temporary accounts.
- **Fixed** from that audit: Dashboard and Appearance controls whose accessible names did
  not match their visible text, unlabelled heatmap cells, scrollable tables keyboard users
  could not reach, and low-contrast text on two Appearance buttons and on summary chips
  in dark mode.

### Follow-up fixes

- **Fixed** Permanently deleting a dream or goal linked to a gratitude entry failed with
  a server error. The entry now stays and loses the link.
- **Added** Fear entries can be edited and restored on the Journal page.
- **Added** `/steps/undated` lists older steps with no date, and Today links to it when
  there are any.
- App version is now 8.0.0.

### Shipped: Phase 4, Khmer, content editing and checks

- **NFR-03, partial** English and Khmer switch on the Account page, remembered per
  device. Translated: navigation, Today, Journal, Assessment, Modules, Plan, Account,
  the commitment and objectives cards, and all 15 modules. Older pages stay in English
  until translated. Khmer text is a first draft that needs a Khmer speaker's review.
- **FR-76** The plan PDF now prints Khmer, using the bundled Noto Sans Khmer font.
- **FR-78** Admins can edit module text and order at `/settings/module-content`.
- **NFR-02** Automated WCAG A and AA checks (axe) pass on the new pages in English and
  on Modules and Today in Khmer. This does not replace a manual screen reader test.
- **NFR-07** Simulated 4G, cold cache, local server: page heading in about 2.4s, and
  all data settled in about 2.6s, or 3.4s on the Dashboard. Not measured in production.
- **Fixed** Quick-add rows forced sideways scrolling on the phone-width Vision Map; the
  account menu's accessible name left out its visible text.

The backend Dockerfile now installs `fontconfig` and `freetype`, which the Khmer PDF
needs. That image has not been built or run yet.

### Migrations (Phase 4)

`V49__learning_module_content_km`

### Shipped: Phase 3, progress, export and account

- **FR-75** Dashboard card measuring BO-01 to BO-06 for one person.
- **FR-76** Download a dream's plan as a PDF from the Plan page. The built-in font
  covers Latin text only, so Khmer text does not print correctly yet.
- **FR-77** Optional daily reminder email, off by default, in the user's time zone.
  Needs `MAIL_HOST`; without it nothing is sent and only the fact is logged.
- **NFR-09** Account page: export all data as JSON, or permanently delete the account
  after entering the password.

New dependency: OpenPDF. New optional settings: `REMINDERS_TODAY_URL`,
`REMINDERS_SEND_HOUR`, `REMINDERS_INACTIVE_AFTER_DAYS`, `REMINDERS_CRON`,
`PROGRESS_TARGET_IMPROVEMENT_PERCENT`, `PROGRESS_TARGET_WEEKLY_COMPLETION_PERCENT`.

### Migrations (Phase 3)

`V48__user_reminders`

### Shipped: Phase 2, assessment, modules and plan

- **FR-71** Assessment page: six barriers scored 1 to 5, compared to the first
  result as a baseline, with a retake prompt after 12 weeks.
- **FR-72** Fifteen original modules in journey order, each unlocking the next.
  Five save their own exercise form; ten are proven by the user's existing data.
- **FR-73** Plan page: one dream's progress from statement through to people.
- **FR-74** Milestones recorded per dream.

### Migrations (Phase 2)

`V44__chain_assessments`, `V45__learning_modules`, `V46__module_completions`,
`V47__milestones`

### Shipped: Phase 1, daily execution

- **FR-64** Steps have an owner, and owner and target date are required when a
  step is saved in full. Status changes use the PATCH endpoint, so older undated
  steps can still be moved.
- **FR-65** Journal page with a fear log and attitude history. Attitude is scored
  on the Today page.
- **FR-66** New `AGREED` partner status between Contacted and Active.
- **FR-67** Today page and `GET /api/daily-plan`: steps and tasks due or overdue.
- **FR-68** Up to three habits with daily checks and streaks.
- **FR-69** Signed, dated dream commitment. An Idea becomes Active when signed.
- **FR-70** Today prompts a weekly review when the last one is 7 or more days old.

The mobile bottom tab for Reviews is replaced by Today. Excel step sheets gain an
Owner column at the end; older workbooks still import, using the account name.
Imported rows may keep an empty target date, so a backup always restores.

### Migrations

`V39__step_owner`, `V40__fear_entries`, `V41__attitude_checkins`, `V42__habits`,
`V43__dream_commitment`

---

## [4.0.0] — 2026-07-25 (10 of 10 items shipped)

Implements **[VMS_BRD_V4.0.0](VMS_BRD_V4.0.0.md)** (dated 2026-07-25).
Builds on BRD v3.0.0 — all v1–v3 requirements remain in force. Requirement
numbering continues from v3 (which ended at FR-33).

Moves the system from *activating* to *integrating* — energy as a portfolio,
cross-area synergy, a searchable insight library, and computed balance signals.

### Shipped
| Item | Description | Commit |
|---|---|---|
| FR-34 Stage A | Energy demand tag + weekly Energy Budget (BR-27, BR-28) | `1c5c20b` |
| FR-34 Stage B | Over-commitment coaching prompt for draining weeks (FR-34.4/34.5) | `4fb6946` |
| FR-37 | Area-starvation signal in the attention feed (BR-31) | `8488b55` |
| FR-36 | PKM Insight Library — search + contextual resurfacing (BR-30) | `cebbf0f` |
| FR-35 | Goal synergy links / cross-pollination (BR-29) | `b78662f` |
| FR-38 | In-app issue & improvement reporting — header report modal, My/All reports, admin triage (BR-32) | `5bf2301` |
| FR-39 | Appearance & theme preferences — presets, 10 accents, high contrast, reduce motion, settings page with live preview, per-account persistence (BR-33, BR-34) | `2ae5df7` |
| FR-40 | Background tone — six curated surface sets, accent-derived Tinted, overridden by high contrast (BR-35) | `4077e17` |
| FR-41 | Categorical pie charts — solid pie with on-slice labels for the two non-semantic dashboard charts | `e033ac5` |
| FR-42 | Font family — system default plus four self-hosted faces, fetched on demand (BR-36) | `e033ac5` |
| FR-43 | Semantic palette & grey ramp — five-shade families, ten-step greys, two new accents (BR-37) | `e033ac5` |

All verified with backend and frontend test suites (backend 127/127, frontend
177/177).

FR-38, FR-39, and FR-40 were added to the v4.0.0 BRD after the original four
items shipped. All are additive with no new infrastructure — FR-38 one table and
role-scoped CRUD, FR-39 seven columns on `app_users` and one user-scoped endpoint
pair, FR-40 one more column on the same endpoint — so they stay within the v4.0.0
release rather than opening a new version.

### Fixed
- An unknown enum value in a JSON request body returned **500 "Unexpected server
  error"** instead of **400**. `GlobalExceptionHandler` already mapped the same
  mistake in a *query parameter* to 400, but the request-body case
  (`HttpMessageNotReadableException`) fell through to the catch-all. Found while
  testing FR-39's enum validation; the fix applies to every `POST`/`PUT` in the
  app, not just the appearance endpoint.
- Appearance settings loaded from the account were written straight back to the
  server on every sign-in — a redundant write on each session start. The
  "skip the next save" flag was being consumed by the save effect's own mount run
  before the loaded state landed, so the echo slipped through. Replaced with a
  comparison against the last-known server payload, which has no ordering
  dependency and also drops no-op writes when a control is toggled off and back on.

- FR-40's Cool, Soft, Tinted, and Flat tones did nothing in **light mode** —
  they returned `#ffffff`, byte-identical to Neutral. Each tone's canvas colour
  was written only to the `--page` CSS variable, which has a single consumer
  (`:root`) that `CssBaseline` paints over when it sets `body` from
  `palette.background.default`. The canvas the user sees comes from the MUI
  theme, so the values now live there. Dark mode was unaffected, which is why it
  looked half-working. Found by manual testing against a real database; the
  existing tests had checked that each tone *had* values and that their contrast
  was sound, but never that they reached the screen — an assertion to that effect
  was added.

- Anonymous requests to protected endpoints answered **403** instead of **401**.
  `SecurityConfig` configured no `authenticationEntryPoint`, so Spring's default
  access-denied handling replied to callers who had presented no credentials at
  all — claiming the caller was known and merely lacked a permission. A
  `RestAuthenticationEntryPoint` (401) and `RestAccessDeniedHandler` (403) are
  now both registered; both are required, because configuring only one makes
  Spring fall back to it for the other case and collapses the two answers into
  one. The genuine 403 path is unchanged: an authenticated non-admin hitting
  admin-only triage still gets 403. Both handlers emit the standard
  `ErrorResponse` shape, so a denial from the filter chain now looks like one
  from a controller.

- The light-mode page canvas rendered pure white while `--page` claimed
  `#fafafa`, so cards never stood out from the page and the Flat tone had no
  canvas step to remove — it looked identical to Neutral. Nothing paints
  `--page`: `CssBaseline` sets `body` from `palette.background.default`, so the
  canvas now lives there, at grey-100. This deliberately supersedes FR-40's AC-3
  (Neutral byte-identical to what shipped): the guarantee was preserving a bug.
- Secondary text was grey-600, which measures 4.88:1 on white and fell **below**
  the 4.5:1 floor as soon as a background tone tinted the canvas — 4.37:1 on
  Cool and 4.27:1 on Soft. Page subtitles render directly on the canvas, not
  inside a card, so this was real rather than theoretical. Moved to grey-700,
  worst case 7.29:1. Found while fixing the canvas above; the tone contrast test
  had hardcoded `#616161` for muted text and so kept passing after FR-43 moved
  that token to the grey ramp — it now reads the value from `surfaceTokens`, so
  the same drift cannot recur.

- The FR-43 palette was largely inert: nine values were defined and referenced by
  nothing. `success`, `warning` and `info` are now wired into MUI's own palette
  slots, so `<Alert severity="success">` and `<Chip color="warning">` draw from
  this app's palette instead of MUI's stock colours; the grey ramp is exposed
  through `palette.grey`; and grey-400/500 back the disabled states. `contrastText`
  is set explicitly on each: MUI derives it from a threshold and would have chosen
  white, which measures 1.90:1 on the warning fill. Nine unused values down to
  three.

- The five original accents (Blue, Teal, Purple, Green, Orange) dropped below
  4.5:1 on their hover and pressed states — six failures across four of them,
  worst 3.34:1 on Purple's light hover. The cause was direction: their
  light-mode hovers were *lighter* than their mains, so contrast fell exactly
  when a user pressed the button. Hover and pressed now darken progressively,
  matching Fluent's own convention and the seven accents added later. Every
  `main` is byte-identical, so no brand colour moved. The theme test asserted
  only 3:1 on transient states to accommodate this; it now asserts 4.5:1
  uniformly, plus a new assertion on the direction of the ramp.

- The `--palette-*` CSS variables named in the FR-43 source tables did not
  exist; the values were reachable only from `theme.ts`. All 40 are now declared
  in `global.css` (30 semantic shades, 10 greys), so plain CSS and components
  ported from a design that names those variables can use them. The objection to
  duplicating them — drift between two hand-maintained copies, which BR-15 exists
  to prevent — is answered by a test that parses the stylesheet and compares
  every declaration against `theme.ts` in both directions: a changed value fails,
  and so does a variable left behind after a shade is renamed. The guard was
  verified by injecting a wrong value and confirming it fails.

- Three high-severity advisories cleared; `npm audit` now reports **0
  vulnerabilities**. `postcss` was a transitive dependency of Vite and took a
  patch bump (8.5.16 → 8.5.25). `react-router` needed more: the fix lands in
  8.3.0, and `react-router-dom` — what the app depended on — is retired at 7.18.2,
  the package having consolidated into `react-router` for v8. So this is a
  migration rather than a version bump: the dependency was replaced and the
  import specifier updated across 33 files. No application code changed; the app
  uses only the classic component APIs (`BrowserRouter`, `Routes`, `Route`,
  `Link`, `NavLink`, `Navigate`, `Outlet` and the hooks), all of which v8 still
  exports.

  Worth recording for context: the advisory is an **RSC Mode CSRF bypass**, and
  this app has no RSC mode — no `createBrowserRouter`, no `RouterProvider`, no
  route actions. It was therefore not exploitable here. The upgrade was done
  anyway, because "not currently reachable" degrades quietly the moment someone
  adopts data routers.

### Known issues
- Three values from the FR-43 palette remain defined but unreferenced: `error.main`
  (the app needs the darker, button-capable shade for its destructive colour, so
  the mid-tone fill has no caller), grey-50 and grey-900. No home was invented for
  them — the row hover that grey-50 might have taken already uses grey-200, and
  switching it would make hover *less* visible, not more. They stay as part of a
  coherent ramp.

### Deferred (needs new infrastructure — future BRD)
Automated agenda generation, predictive push alerts, and fully-automated reviews —
each requires a scheduler, background jobs, a notification channel, or a calendar
model the system does not have. Out of v4.0.0 scope by design.

### Migrations
- `V13__task_energy_demand.sql` (FR-34 Stage A)
- `V14__goal_synergy_link.sql` (FR-35)
- `V15__issue_reports.sql` (FR-38)
- `V16__user_appearance_preferences.sql` (FR-39)
- `V17__user_background_tone.sql` (FR-40)
- `V18__user_font_family.sql` (FR-42)

---

## [3.0.0] — 2026-07-21

Implements **[VMS_BRD_V3.0.0](VMS_BRD_V3.0.0.md)** (dated 2026-07-21).
Builds on BRD v2.0.0 — all v1/v2 requirements remain in force. Requirement
numbering continues from v2 (which ended at FR-31, Addendum D).

Moves the system from *tracking* to *activating*: obstacles must prove creative
persistence before they can be closed, and each Vision Area gets a written
vision statement and a guided setup wizard.

### Shipped
| Item | Description | Commit |
|---|---|---|
| FR-32 | Obstacle Creative Persistence — `rootCause` + `creativeAlternatives`, with Resolved/Accepted status guards (BR-25, BR-26) | `7fe6815` |
| FR-33 | Vision Area vision statement + guided setup wizard | `7fe6815` |

Both verified with backend tests (8 new `ObstacleServiceTest` cases; suite
83/83 green), frontend `tsc -b` + build + 35/35 tests green, and live
end-to-end runs (see the BRD for the full verification log).

### Migrations
- `V10__obstacle_creative_persistence.sql`
- `V11__vision_area_vision_statement.sql`

---

## [2.0.0] — In progress (6 of 11 items shipped)

Implements **[VMS_BRD_V2.0.0](VMS_BRD_V2.0.0.md)** (dated 2026-07-11).
Builds on BRD v1.0.0 — all v1 requirements remain in force. Requirement
numbering continues from v1 (which ended at FR-13).

Moves the system from a task tracker toward an *accelerated achievement engine*
by adding the strategy layer above the execution hierarchy.

### Shipped
| Item | Description | Commit |
|---|---|---|
| FR-17.0 | Expose hook / problem / benefit fields | `741f7a6e` |
| FR-16 | Diligence checkup + guided review templates | `741f7a6e` |
| FR-17 | Persuasive communication module | `741f7a6e` |
| FR-14 | Moonshot goals | `741f7a6e` |
| C-2 + C-3 | Archive confirmation + show-archived / restore | `741f7a6e` |
| C-1 | Excel import persistence — recreates the hierarchy from a workbook | `5be80973` |

All six verified with backend and frontend tests plus Playwright end-to-end runs.

### Pending
- **FR-15** Partner Recruitment Portal — the largest remaining item, next in the
  build order.
- **C-4** Task estimated / actual hours in the UI.
- **C-5** Partner phone in the UI.
- **C-6** Due-date / target-date range filters.
- **C-7** Automatic export snapshot before import.

### Migrations
- `V5__review_diligence_checklist.sql`
- `V6__communication_word_picture.sql`
- `V7__goal_moonshot.sql`

---

## [1.0.0] — 2026-07-11

Implements **[VMS_BRD_V1.0.0](VMS_BRD_V1.0.0.md)** — the baseline BRD, written to
describe the system as already built and deployed.

Established the execution structure: the
`Vision Area → Dream → Goal → Step → Task` hierarchy with progress roll-up,
boards, reviews, partners, and communication.

### Requirements
FR-1 Authentication & account · FR-2 Vision Areas · FR-3 Dreams · FR-4 Goals ·
FR-5 Steps · FR-6 Tasks & task board · FR-7 Partners ·
FR-8 Communication builder · FR-9 Reviews · FR-10 Obstacles ·
FR-11 Excel import / export · FR-12 Dashboard · FR-13 Delete / archive lifecycle

FR-1 through FR-10 and FR-12 shipped as *Implemented*. FR-11 shipped partial —
export complete, import validated structure only; import persistence landed
later as C-1 in v2.0.0.

### Migrations
- `V1__backend_foundation.sql`
- `V2__core_data_model.sql`
- `V3__soft_delete_reviews_and_progress_logs.sql`
- `V4__standardize_archived_flag.sql`

---

## Before 1.0.0

Initial build, `a3fd1734` (2026-07-05) onward — backend foundation, data model,
REST APIs, JWT security, React frontend, and Excel export, delivered in the
phases described in [CLAUDE.md](../CLAUDE.md). Predates the first BRD.
