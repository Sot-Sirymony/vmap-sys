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

## [9.0.0] — In progress (Phases 1–7 of 9)

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
