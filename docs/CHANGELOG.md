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

## 35.0.0 — Plan checklist and Build my plan, joined up (built; awaiting deploy, UAT and the Khmer review)

Implements [BRD 35.0.0](VMS_BRD_V35.0.0.md), all three phases. No migration.

**Backend.**
- `GET /api/plan-status/{dreamId}` gains the `INNER` stage after `STRENGTHS`: done when an active, not archived inner
  blocker is linked to the dream or to all dreams. A count only; never part of `planReady` or the complete plans count.

**Frontend.**
- The sidebar Plan page is now **Plan checklist** (route `/plan` unchanged), with the line "Build my plan builds it.
  The plan checklist checks it." here and on the builder's Clarify stage.
- Plan checklist: **Build my plan** / **Continue Build my plan** with the draft's saved date.
- **Do this next** opens Build my plan at the matching stage (`?stage=`), with an "or open the … page" link to the
  older page. Strengths and gaps still opens the Strengths page.
- Build my plan accepts `?stage=` (`CLARIFY`, `INNER`, `GOALS`, `STEPS`, `DATES`) without changing the draft.
- **See your plan checklist** after Save my plan; a **Plan checklist** link beside Build my plan on the dream page.
- 12 Khmer drafts (`docs/km-review-v35.csv`).

**Docs.** `UAT_V35.md`, the user guide section and the plan status API line.

---

## 34.0.0 — My inner blockers (built; awaiting deploy, UAT and the Khmer review)

Implements [BRD 34.0.0](VMS_BRD_V34.0.0.md), all five phases. Migration **V104**.

**Backend.**
- New `inner_blockers` and `inner_blocker_checkins` tables, with `InnerBlocker`, `InnerBlockerCheckin`,
  `InnerBlockerService` and `InnerBlockerCheckinService`.
- `/api/v1/inner-blockers`: paged list (status, archived, dream plus all dreams, habit streak by `date`), add, edit,
  status (resolve or reopen), archive with `stopHabit`, restore, and make it a daily habit. At most 10 active (409).
- `/api/v1/reviews/{reviewId}/inner-blocker-checkins`: the weekly "Did the practice help?" answers (weekly reviews
  only). A blocker shows its last four answers and `practiceNotHelping` after two No answers in a row.
- The coach participant detail gains `innerBlockers` (only when journals are shared). The account export gains
  `innerBlockers` and `innerBlockerCheckins`. Account deletion removes both tables.
- No AI service reads inner blockers. A test checks this.

**Frontend.**
- A shared `InnerBlockerForm`, `InnerBlockerCard` and `InnerBlockerList` (`components/inner-blockers`).
- Build my plan → Inner blockers: **Add as my blocker** on each named chain, and **Your inner blockers** with
  **Add your own blocker**, linked to the dream.
- Assessment page: **My inner blockers**, with Active / No longer holds me back / Archived filters.
- **Make it a daily habit**, and the "Also stop the habit?" choice on resolve or archive.
- Reviews page: a **My inner blockers** check-in on weekly reviews.
- Check and add: "Inner blockers for this dream: n". Coach participant page: read-only blockers under Journals.
- 59 Khmer drafts (`docs/km-review-v34.csv`).

**Docs.** `UAT_V34.md`, the API, data model and user guide sections for V34.

---

## 33.0.0 — One standard for every REST controller (phases 1 to 4 of 6 built)

### Phase 4: account, sign-in, admin and coaching on /api/v1

**Backend.**
- Every controller now answers on `/api/v1/...`, except `/api/health`. The old paths are forwarded.
- "Verify it's you" codes are read once per request. Handlers are marked `@RequiresStepUp`, and services call
  `requireFromRequest`; the 23 header parameters are gone.
- Every admin and coach handler carries `@PreAuthorize` with the exact permission.
- The sign-in, cohort join and admin user actions moved into services.
- The admin user list and security log have a nested page; the failed request log is a page.
- A path id of 0 or below is refused with 400 before any lookup, where it was a 404.

**Frontend.** The Users page, the role editor and the security log read the nested page.

### Phase 3: AI, Streamline, reports, sharing and files on /api/v1

**Backend.**
- The AI, Streamline, report, sharing, Excel and learning module controllers answer on `/api/v1/...`. Their old paths
  are forwarded.
- Three controllers that served several roots are split, with the same URLs.
- The Dream Coach stream's error handling moved to `StreamingResponder`. An AI failure part way through is still sent
  as an `error` event.
- File downloads declare their type.
- The report rate limits and choices moved to `ReportActionsService`; the Excel import limit and the shared plan limit
  moved into their services.
- Saved reports are paged.
- The coach AI summaries and crisis alerts carry `@PreAuthorize`.

**Frontend.** The Reports page reads every page of saved reports, so nothing changes on screen.

### Phase 2: plan core, rhythm and journals on /api/v1

**Backend.**
- The 32 plan core, rhythm and journal controllers answer on `/api/v1/...`. Their old `/api/...` paths are forwarded,
  with `Deprecation: true`.
- Every handler answers a `ResponseEntity`: 201 with `Location` on a create, 204 when there is no body. Path ids must
  be positive. Every endpoint is documented in OpenAPI.
- Five controllers that served several roots are split, with the same URLs.
- Paged with `PagedResponse`, with checked sorts and at most 200 to a page: tasks, progress logs, reviews, obstacles,
  fear entries, gratitude entries, attitude check-ins, a task's history, partners and communication messages.
- `has-linked-partner` answers `{ "linked": true|false }`.

**Frontend.** Screens that need a whole list read every page with `fetchAllPages`, so nothing changes on screen.
Attitude check-ins read the newest 30, as before. The `Page` type has `page` (was `number`) and `sort`.

### Phase 1: foundation

No path, status code or screen changes yet. Every controller still answers on `/api/...`.

**Backend.**
- `ApiPaths.V1` (`/api/v1`). `LegacyApiPathFilter` runs before Spring Security and forwards an old `/api/...`
  path to `/api/v1/...` once a version 1 endpoint exists for it, with `Deprecation: true` and, when
  `API_LEGACY_SUNSET` is set, `Sunset`.
- Every error is now an RFC 9457 problem (`application/problem+json`) with `reference`, plus `code`, `triesLeft`,
  `action` and `errors` when they apply. For version 1 it still carries `message`, `error` and `path`.
  `ErrorResponse` is gone.
- `PagedResponse<T>`, `Pageables.whitelist` (an unknown sort answers 400), and `max-page-size` 200.
- `ApiResponses.created` (201 with `Location`).
- `@EnableMethodSecurity` with `Authorities` constants.
- `StepUpContext`, its interceptor and `@RequiresStepUp`.
- OpenAPI: common problem answers on every operation, no token on public paths, and the `X-Step-Up` header on
  marked endpoints.
- `ControllerRulesTest` enforces the standard with ratchet lists. `docs/CONTROLLER_AUDIT_V33.md` lists all 416
  endpoints.

**Frontend.** Errors read `detail` first, then `message`.

---

## 32.0.0 — Streamline AI: faster, easier, more automatic (built; awaiting deploy, UAT and the Khmer review)

### Phase 5: coach check and documents

A coach's read-only review shows none of the V32 controls, and every V32 review endpoint answers 404 to a coach.
`docs/UAT_V32.md`, the user guide section "Faster Streamline AI (V32)" and `docs/km-review-v32.csv` are complete.

### Phase 4: reminders, the cycle-time rule, the weekly nudge, saved dismissals

**Backend.** Migration **V102**. "Remind me" items keep how often they remind (`remindCadence`, `remindDay` on the
item update; weekly on Monday by default). Due reminders appear in the daily plan (`streamlineReminders`) and in the
reminder email. A new candidate rule, `CYCLE_TIME_DOUBLED`, flags a step whose median cycle time over the last 90 days
is at least twice the 90 days before (3+ tasks with a start time in each window). While the weekly review is due, one
Streamline nudge a week (the same goal or step at most once in 30 days) shows on the Dashboard (`streamline.nudge`) and
in the reminder email. It can be switched off with `streamlineNudges` in the reminder settings.
`POST`/`GET /api/streamline-reviews/candidate-dismissals` save dismissals for 90 days, per rule, and the candidate list
leaves them out.

**Frontend.** Stage 5 shows how often and on which day for "Remind me", with a "Reminds you every Monday" chip. The
Today page has a Reminders card. The Dashboard's Streamline card shows "This week's suggestion". Account settings has
the weekly Streamline suggestion switch. "Not now" is saved on the server, and old browser dismissals move there once.

### Phase 3: Draft all stages

**Backend.** Migration **V101** adds `streamline_suggestion_sets.drafted_at_stage`.
`POST /api/streamline-reviews/{id}/draft-all` asks the AI once (`STREAMLINE_DRAFT_ALL`) for the current stage and
every later stage not yet finished, checks each stage's drafts like a single stage's, and saves them as that stage's
suggestions. It needs at least US$0.50 left in the month's AI budget. `GET …/drafts` lists the saved drafts, marking
stages not yet reached as previews, and says whether Draft all stages can run. Fix to phase 1: the fingerprint covers
all the review's items, so a delete no longer drops the stage's other saved cards.

**Frontend.** **Draft all stages** next to Suggest again (not on stage 5), disabled with the reason when the budget
has no room. **Drafts for later stages** shows each later stage's drafts read-only until the user reaches it. Cards
drafted before the earlier stages were finished say so.

### Phase 2: batch apply and undo

**Backend.** Migration **V100** adds `streamline_apply_batches` and `streamline_apply_batch_entries`.
`POST /api/streamline-reviews/{id}/apply-batch` applies up to 50 actions (UPDATE, DELETE, MERGE, SIMPLIFY, REPEAT)
one after another through the normal review actions, each in its own transaction, recording what it is about to
change. It stops at the first failure. `POST …/batches/{batchId}/undo` reverses a batch in reverse order while its
stage is current and for 24 hours, leaving alone any item changed since. `GET …/batches` lists the stage's latest
batches.

**Frontend.** Apply selected and Apply all send one batch, then offer **Undo**. A **Recent changes** list on the stage
shows each batch with Undo while it is possible. Several lever suggestions for one item add up instead of
overwriting each other.

### Phase 1: saved suggestions and the Streamline model

**Backend.** Migration **V99** adds `streamline_suggestion_sets` and `ai_settings.streamline_model`. The last checked
suggestions for each review and stage are saved with a fingerprint of the stage and the review's active items.
`GET /api/streamline-reviews/{id}/stages/{stage}/suggestions` returns them (with `savedAt`) while they still fit, or
`204`; it uses no AI. Cards already acted on, or whose item is gone, are left out one by one.
`POST …/suggestions/dismiss` removes a dismissed card. Saved sets are deleted when the review is completed or
abandoned, and with the account. `PUT /api/admin/ai-settings` takes `streamlineModel` (left out keeps it, blank
clears it). When it is set, it is used for `STREAMLINE_SUGGEST` and `STREAMLINE_PICK`.

**Frontend.** The suggestions panel shows saved cards at once with "Suggested on <date>". Auto-suggest asks the AI
only when nothing saved still fits. Saved cards stay usable without AI, with the reason new ones are missing.
Dismissing a card also removes it from the saved set. The AI settings page has **Model for Streamline suggestions**.
Khmer drafts are in `km-review-v32.csv`.

## 31.0.0 — Dream Coach: v2 step 1, owner roles, coach indicator, language retry (built; awaiting deploy, UAT and the Khmer review)

**Backend.** Migration **V98** adds `ai_settings.owner_roles`; `PUT /api/admin/ai-settings` takes `ownerRoles` (left out
keeps it, blank removes it). The owner check (`OwnerRoleCheck`) accepts these roles as whole words; names and titles
are still removed. `GET /api/coach/participants/{id}` adds `guidePlans` (dream id, finished at). Plan-building AI
answers in the wrong language are retried once with a firmer instruction.

**Frontend.** AI settings: "Extra owner roles". Coach participant page: "Planned with the Dream Coach on …" beside each
dream planned with the guide. 3 more Khmer drafts.

## 31.0.0 — Dream Coach: v1 step 3, schedule, review rhythm and plan tree (built; awaiting deploy, UAT and the Khmer review)

**Backend.** Migration **V97** `dream_review_rhythms` (one per dream: daily, weekly day, monthly day 1–28, quarterly;
removed with the account). `GET`/`PUT /api/dreams/{id}/review-rhythm` (204 when none) and `GET /api/reviews/due`: a
review is due from the start of its period until one of that kind is written for the dream; archived dreams make
nothing due. The draft's `reviewRhythm` is saved by Save my plan. The daily reminder adds `A weekly review of "…" is
due.` lines. Plan-building AI features get the Dream Coach rules in their system prompt.

**Frontend.** New stage **Schedule and review** between Dates and Check and add: hours a week and Spread the dates
(moved here), a month-by-month count of new work, and the review rhythm (starts from the saved one or the suggestion
for the plan's length; "Use the suggested rhythm"). Check and add opens with the plan tree: dream, goals, steps with
gap, helper and risk, tasks with owner and status, and the rhythm. The Reviews page shows "Reviews due" with "Start
this review". 23 more Khmer drafts.

The shared answer envelope of BRD §4.1 is not built (Q-31-7), so DCM-08 is still unmeasured.

## 31.0.0 — Dream Coach: v1 step 2, inner blockers, merged helpers and a Dates stage (built; awaiting deploy, UAT and the Khmer review)

**Backend.** The draft gains `innerBlockers` (READY or SKIPPED). Rule R-05 adds a SHOULD line `INNER_SKIPPED` when the
stage was skipped or not visited. Date conflicts carry a second fix, `altFixAction` / `altFixValue`:
`MOVE_PARENT_DATE` when the parent goal or step is new in the draft, `MOVE_DREAM_DATE` when the dream's date follows
its goals (never for a fixed deadline). Helper and risk fixes now point to Steps. No migration.

**Frontend.** Build my plan's stages: Clarify, **Inner blockers**, Goals, **Steps, gaps and helpers**, Tasks,
**Dates**, Check and add. Inner blockers shows the two chains that hold the participant back most, from their latest
check, with the program's practice, and offers the 2-minute check when there is no recent one; "I'm ready to plan" or
"Skip for now". The helpers and risks panel sits under the steps. Dates lists every date conflict with "Bring it
forward" and, when possible, "Move the parent" or "Move the dream". Clarify can edit the dream in place. The live rule
check is shared by Dates and Check and add (`useRuleCheck`), and re-runs when the dream's date changes. 25 more Khmer
drafts.

## 31.0.0 — Dream Coach: v1 step 1, start without a saved dream (built; awaiting deploy, UAT and the Khmer review)

**Backend.** `POST /api/ai/plan/vision-statement` (`COACH_AREA`) words a vision statement from the participant's own
sentence; `POST /api/ai/plan/dream-intake` (`COACH_DREAM`) asks up to three questions about a dream that is not saved
yet, then restates it from the participant's words. The target date is only the participant's own date answer. Both
save nothing and need AI consent. The draft gains `coachCreated`. No migration.

**Frontend.** New page `/plan/new`, the Dream Coach: stage 1 picks or creates the vision area (starter areas when there
are none) and confirms its vision statement, with "Word it for me"; stage 2 takes the dream in the participant's own
words, asks the coach's questions in place, and shows the restatement to edit. "This is right" saves the area if
needed and the dream as an Idea, then opens Build my plan at the goals. Everything works without AI. Entry points:
"Plan a new dream with the Dream Coach" on the Dreams page and "Plan a dream in this area" on each vision area. Undo
after Save my plan also archives the dream the coach saved. 28 more Khmer drafts in `km-review-v31.csv`.

D-31-2 amended by the product owner on 25 Nov 2026: the dream is saved when the participant confirms it at stage 2.

## 31.0.0 — Dream Coach accuracy check, MVP (built; awaiting deploy, UAT and the Khmer review)

BRD [`VMS_BRD_V31.0.0.md`](VMS_BRD_V31.0.0.md) (31.0.0, draft). The MVP phase only: the accuracy check on the existing
Build my plan guide. Phases v1 (the ten-stage Dream Coach) and v2 are not built. Migration **V96**.

**Backend.** `PlanRuleService` holds the rules R-01 to R-08 in one place (D-31-3); the draft check and Save my plan both
use it. Every issue now carries `rule` and `level` (MUST, SHOULD or INFO). New checks: goal not in its dream, step not
under its goal, missing goal measure or date, goal after its dream, more than 5 new goals, task priority, Blocked task
without a reason, risk not on a step, gap without a kind, Knowledge/Skill/Money gap without a helper, AI goal not
approved, AI steps and tasks kept unchanged, and the dream's own fields (SHOULD, since the dream was saved before).
`POST /api/ai/plan/drafts/{dreamId}/add` re-runs the rules and refuses any MUST fail (DC-FR-56); an optional
`acceptedShould` body is counted. `draft-check?rulesOnly=true` runs the rules without Claude. AI step, task and
plan-fix owners pass `AiOwnerRule`: blank stays Me, a role is kept, a name or title such as "Dr." is removed (R-07);
prompts add a no-guessing line and ask for owners in English. Draft fields: goal `approved`, step `gapKind`, task
`status` and `blockerReason`, all saved. `plan_check_events` and `ai_owner_strips` hold counts only (anonymised on
account deletion); `GET /api/admin/ai/plan-accuracy` reports DCM-01, 02, 03, 06 and 07.

**Frontend.** Check and add shows the rules as a pass/fail list that re-runs a second after each change, without AI.
MUST lines block **Save my plan** (renamed from Add plan); each rule with SHOULD lines needs "I understand". Check my
draft now shows only what the AI review found. Goals: an approve tick on AI goals, success criteria marked required.
Steps: Kind of gap with the helper type from the fixed map. Tasks: status, and a required reason when Blocked. An
owner the server removed stays empty. AI settings: a Plan accuracy card. 88 Khmer drafts in
[`km-review-v31.csv`](km-review-v31.csv).

**Clarified during build** (recorded in the BRD): SHOULD fails are counted, not enforced on the server; "I
understand" is one tick per rule, not per line; INFO is a third level for advice (missing resources, AI review);
GAP_NO_KIND is SHOULD, since AI steps often list resources; R-08 is met by the server re-running the rules at save,
plus the browser waiting for a check of the latest draft; DCM-04 and DCM-05 are guaranteed by the save gate; DCM-08 waits
for v1; the inner-blocker part of R-05 waits for stage 3 in v1.

---

## 30.0.0 — A stronger AI assistant for Streamline (built; awaiting deploy, UAT and the Khmer review)

BRD [`VMS_BRD_V30.0.0.md`](VMS_BRD_V30.0.0.md) (30.0.0, draft; the seven improvements chosen by the product owner on
24 Nov 2026; Q-30-1 to Q-30-3 open). No migration.

**Backend.** `POST /api/streamline-reviews/ai-candidates` (`STREAMLINE_PICK`): AI ranks up to 5 of the user's open goals
and steps without an open review, read from counts only; any other key is dropped. The rule cards
(`GET /candidates`) are now ranked by need and their order is stable. Stage suggestions gain stage 1 **ANSWER**
drafts (source, owner as a role, why, verdict; checked per BR-30-3) and **SIMPLIFY** `newTitle` / `newDescription`
(kept only when different). `POST /{id}/summary-advice` (`STREAMLINE_ADVICE`): 2 to 4 points on a completed review,
with item codes checked against the review. The page assistant gains page `STREAMLINE` (the open review, chat only,
ownership checked; page switch lists allow 7 pages). Tests: `StreamlineAiFlowTests` (7) and two more
`StreamlineAiServiceTest` cases.

**Frontend.** Streamline page: an **AI picks** card above the (now ranked) rule cards, with Start review. Review page:
suggestions load by themselves once per review and stage in the session (**Suggest again** asks again); tick boxes
with **Apply selected (n)** and **Apply all**, one at a time, stopping at the first failure; editable **Draft answer**
cards in stage 1 and an editable **New title** on Simplify cards; **Get AI advice** on a completed review's summary;
the page assistant's **Ask** button (not for coaches). "Streamline" added to the admin and cohort page switches. Khmer
drafts in `km.ts` and `km-review-v30.csv`. Tests: `StreamlineAiPicks`, five more suggestions-panel tests, two
review-page tests and the page mapping; the API contract snapshot gains both endpoints.

**Pending.** `UAT_V30.md`, the Khmer review, the user guide section.

---

## 29.0.0 — Archive one or several items at once from the Vision Map tree (built; awaiting deploy, UAT and the Khmer review)

BRD [`VMS_BRD_V29.0.0.md`](VMS_BRD_V29.0.0.md) (29.0.1, draft). 29.0.0 first added an archive reason text box; the
product owner clarified the same day that tick boxes on the tree were wanted, not a reason (D-29-9), so 29.0.1 removes
the text box and makes the tick boxes always visible.

**Backend** (from 29.0.0, kept for compatibility). Migration `V95__archive_reason.sql`: `archive_reason VARCHAR(1000)`
on `dreams`, `goals`, `vision_steps` and `task_items`. `POST /api/{dreams,goals,steps,tasks}/{id}/archive` takes an
optional `{ reason }` (steps also `removePartner`); the old `DELETE` is unchanged. The cascade copies a reason only to
items it newly archives; restore clears it. Responses carry `archiveReason`. Tests: `ArchiveReasonFlowTests` (6).

**Frontend** (29.0.1). The Vision Map tree shows a tick box on every row that is not archived: the dream, goals, steps
and tasks. Ticking one shows "{n} selected", **Archive selected (n)** and **Clear**; one plain confirmation archives
them all. Rows under a ticked row are skipped and archived by the cascade, and a ticked dream covers everything
(`topLevelSelection`). A partial failure reports how many were archived. Archived rows with a saved reason show
"Archived: {reason}". Archive confirmations elsewhere (row menus, timeline, list pages) are back to the plain dialog.
Khmer drafts in `km.ts` and `km-review-v29.csv`. Tests: `selectionArchive` and five `VisionMapTree` flow tests.

**Pending.** `UAT_V29.md`, the Khmer review, the user guide section.

---

## 28.0.0 — Blocker analysis in the Vision Map plan assistant (built; awaiting deploy, UAT and the Khmer review)

BRD [`VMS_BRD_V28.0.0.md`](VMS_BRD_V28.0.0.md) (28.0.1, draft; Q-28-1 to Q-28-6 await the product owner). Built at the
product owner's request before the BRD was written; the BRD records the built behaviour.

**Backend.** No migration. `BlockerAnalysisService` and `POST /api/ai/plan/dreams/{id}/blocker-analysis` find a
dream's Blocked and Waiting tasks and steps and its steps with a gap that needs a helper (the BL-FR-22 rule, reused),
then work out days stuck (from the V94 status history), times blocked, later steps held up, goal date at risk, what is
missing (open obstacle, then gap kind) and whether a helper is linked, and rank them by impact (at most 15). With AI
(consent, limits, session limit) the top 8 get a root cause, a first move and a helper role, plus a shared pattern;
answers for keys outside the list are dropped. New AI feature `BLOCKER_ANALYSIS` in the plan features (rating,
metrics). **Define blocking (28.0.1):** `POST /api/blocks` (`BlockMarkingService`, no AI) marks an open task or step
Blocked with its reason through the normal rules (status history, blocked count, roll-up), gives a step without a gap
kind the chosen type, and records an open obstacle on the item, reusing one already open (BR-28-9). The analysis
response gains `candidates`, possibly blocked work: overdue 3+ days with no change for 7+ days, in progress 21+ days
under 50%, step resources that say something is missing, steps that keep slipping, plus up to 5 AI items (only open
items of the dream are kept); at most 10, never marked automatically. Tests: `BlockerAnalysisFlowTests` (9).

**Frontend.** "Analyse blockers" in the Vision Map plan assistant (works without AI) and the `BlockerAnalysisResult`
panel: summary chips, pattern, ranked cards with facts, missing type and who can help, AI suggestions, and actions
(break down, record or open obstacle, find a helper, edit). **Define blocking:** a task picker ("Whole step" or a task)
and **Mark as blocked** in the assistant, the `MarkBlockedDialog` (what is missing, why, severity, who can help), and a
**Possibly blocked** section with Mark as blocked (pre-filled) and Not blocked (this view only); after marking, the map
reloads and the analysis re-runs. Khmer drafts in `km.ts` and `km-review-v28.csv`. Tests:
`BlockerAnalysisResult.test.tsx` (6) and two more `AiPlanAssistant` tests; the API contract snapshot gains both endpoints.

**Pending.** `UAT_V28.md`, the Khmer review, the user guide section, and the product owner's answers to Q-28-1 to
Q-28-6.

---

## 27.0.0 — Streamline review: question, delete, simplify, speed up, automate (all phases built; awaiting deploy, UAT and the Khmer review)

BRD [`VMS_BRD_V27.0.0.md`](VMS_BRD_V27.0.0.md) (27.0.3, all open questions answered). Built as working frontend and
backend at the product owner's request, not as a phase 1 prototype.

**Backend.** Migration `V94__streamline_review.sql`: `streamline_reviews`, `streamline_items`, `task_status_changes`,
`task_repeat_rules`, and `task_items.started_at` / `repeat_rule_id` (unique with `due_date`, so a rule never makes two
tasks for one date). Every task status path (create, edit, the status PATCH and board, split, progress logs) writes
the status history and stamps the first move to In progress. `StreamlineReviewService` runs the five stages in order
(D-27-3, hard lock with "Not needed (reason)"), bulk stage 1 answers (Q-27-2), delete that archives at once with
restore counted as "brought back" (D-27-4, Q-27-3; a step's tasks follow it), merge, the BR-27-8 confirmation, simplify,
targets, repeat rules (Q-27-4: daily, weekly, monthly; an hourly job, safe to run twice; archiving the step stops it),
the summary as counts (Q-27-7) and suggestion candidates (§6.2). `StreamlineAiService` (SR-FR-14, required per Q-27-6)
suggests per stage under the usual consent, daily limit and monthly cap, sends no names of people, and checks every
suggestion against the review. Coaches read reviews read-only (D-27-10). The dashboard response gains `streamline`;
the Excel export gains a Streamline sheet and Started / Cycle Days columns on Tasks. Account deletion removes the new
tables. Tests: `StreamlineReviewFlowTests` (10), `CycleTimeCalculatorTest`, `StreamlineAiServiceTest`; the access
sweep now covers every Streamline endpoint.

**Frontend.** `/streamline` (reviews and suggestion cards, dismissible for 30 days), `/streamline/new` (started from
the new Streamline action on goals and steps), `/streamline/:id` (the stepper, five stage panels, AI suggestions
with Apply, Undo after each delete, the summary), the coach's read-only view, a Streamline item in the sidebar, the
dashboard Streamline card, and the monthly review question. Khmer drafts in [`km-review-v27.csv`](km-review-v27.csv).
The Goals and Steps pages show a summary chip for the latest completed review ("Streamlined 12 Nov: −5 steps"); the
Vision Map's goal and step menus offer Streamline too; the Streamline page lists repeating tasks with how often, next
due, and Stop / Restart.
Tests: `StreamlineReviewPage.test.tsx` (9), `streamlineParts.test.tsx` (5), a Vision Map menu test, and
`e2e/streamline.e2e.ts` (a full review in a real browser, then axe on the Streamline pages in all three interface
styles, light and dark, English and Khmer, and Khmer at 360px with no sideways scrolling). Backend:
`StreamlineReviewFlowTests` (12) includes task merging and SR-NFR-03 (a review of 50 steps and 500 tasks loads in
under 1.5 s).

**Not built, or built differently from the BRD:**
- §6.2 "a step whose median cycle time doubled" is not a suggestion yet: it needs weeks of the new status history.
- Stage 5 candidates come from repeated task titles; habits are not linked.
- "Remind me" records the choice and points to the existing email reminders; there are no per-task reminders.
- Suggestion cards are dismissed per browser, not per account.

---

## 26.0.0 — Interface styles: Modern, Classic, Liquid Glass (all 7 phases built; awaiting deploy, UAT, snapshot approval and the Khmer review)

BRD [`VMS_BRD_V26.0.0.md`](VMS_BRD_V26.0.0.md). One style token layer replaces the four ways styles were applied
(D-26-1). Open questions Q-26-2 to Q-26-4 are built as proposed (KPI cards stay solid; Classic does not change
Density, with a hint; internal id `liquidGlass` renamed `glass`, wire value `LIQUID_GLASS` unchanged).

**Phase 1 (preview):** [`design/v26-interface-styles-preview.html`](design/v26-interface-styles-preview.html): the
Dashboard, a tasks table with a menu, and a dialog in all three styles, light and dark, over the plain canvas or a dark
or bright image, with a toggle that simulates the glass fallbacks. Its token CSS is generated from `tokens.ts`.

**Phase 2 (tokens):** `design/tokens.ts` adds `styleTokens[style][mode]` (shape, elevation, surfaces, layout density,
motion, chart tokens) and `styleCssVariables()`. global.css carries six generated `--style-*` blocks, switched by
`data-style="modern | classic | glass"`, which is now always set. `--radius-*`, `--shadow-*` and `--motion-*` are
aliases of the style tokens. Glass is 0.88 (light) / 0.85 (dark) opaque, 0.74 only over the plain canvas, and falls
back to solid under High contrast, `prefers-contrast: more`, `prefers-reduced-transparency: reduce` and without
`backdrop-filter` (D-26-5, D-26-6). `style-tokens.test.ts` checks CSS sync, the contrast floors over black and white,
and every fallback.

**Phase 3 (theme and components):** `createAppTheme({ mode, style, accent, font, density, highContrast, tone })`
replaces `buildTheme` and `styleShape`; MUI overrides read the `--style-*` variables, so the CSS fallbacks reach MUI.
New `design/surface.ts` (`surface('card' | 'floating' | 'dense' | 'inset')`) and `hooks/useInterfaceStyle.ts`
(style, tokens, `glassActive`, fallback reason). The V25 cards, 24 numeric radii, the literal shadows and the seven
own-surface files now use tokens. Header and Sidebar decide anatomy from the `header` / `navShape` tokens, not the
style name. `no-hardcoded-style.test.ts` fails on literal radii, shadows, blur, surface and border colours in
components and pages (ISC-04).

**Phase 4 (glass placement):** glass only on the sidebar, sticky header, bottom nav, menus, popovers, dialogs (30%
scrim; solid inputs inside), the chart tooltip and the Dashboard's Overall progress hero card. Kanban columns, metric
cards, empty states and the auth card are no longer glass. Charts read the grid dash and bar radius tokens; per-style
motion and press scale, zeroed under reduced motion.

**Phase 6 (picker):** Appearance → Interface style is a three-card radio group with live thumbnails drawn from each
style's real tokens, arrow-key movement, the Classic density hint, and a note when Liquid Glass falls back to solid.
Khmer drafts in [`km-review-v26.csv`](km-review-v26.csv).

**Phase 5 (default unified, Q-26-1 answered "keep CLASSIC"):** migration `V93__interface_style_default.sql`
makes MODERN the column default and adds `interface_style_source` (`DEFAULT` / `CHOSEN`). No stored style changes:
accounts on CLASSIC keep it as `DEFAULT`; MODERN and LIQUID_GLASS accounts are marked `CHOSEN`. `AppUser` and the
response's null fallback default to MODERN. `PUT /api/preferences/appearance` marks the style `CHOSEN` only when it
changes (the app sends the whole appearance on every save), and the response adds `interfaceStyleSource`. The app's
per-browser Classic-to-Modern switch and its `vms-style-rebaseline` flag are removed, so a chosen Classic holds on
every browser. With no migrated accounts there is no one-time notice. (Local database after V93: 69 CLASSIC/DEFAULT,
3 MODERN/CHOSEN, 2 LIQUID_GLASS/CHOSEN.)

**Follow-ups to the BRD (after the first push):** tooltips and toasts are floating layers (raised solid, glass in
Liquid Glass); the header sits on the canvas at the top and takes the floating material once content scrolls under
it (Classic stays solid); the sidebar is sunken in Modern (`--style-sidebar-bg`); heatmap cells use
`--style-heat-radius` (3 / 1 / 4px); Classic's tighter layout reaches the page gutter, section gaps, metric and list
cards and MUI `CardContent` padding.

**Phase 7 (verification):** `frontend/e2e/interface-styles.e2e.ts` (37 checks): snapshots of Dashboard, Tasks,
Vision Map and a dialog × 3 styles × light/dark × English/Khmer, plus Khmer at 360px (54 baselines); axe on all of
them, on violet and teal accents, and on Liquid Glass over a dark and a bright background image; at most 4 blurred
layers and none inside a scrolling container; glass solid under `prefers-contrast: more` and High contrast; Classic
and Modern header anatomy; main-thread work per scrolled frame at 4× CPU throttle (about 1 ms). The axe matrix found
two accessibility faults, now fixed for every style: unselected toggle buttons were 4.49:1 on the canvas (now the
secondary text colour), and the sidebar search button's spoken name did not match its visible text (WCAG 2.5.3; it
now reads "Search", translated, with `aria-keyshortcuts`). The shared e2e seed was fixed (obstacles must sit on a
step since V17), and account cleanup no longer fails a run when deletion needs a step-up code. `UAT_V26.md` and the
user guide's Interface style section (now covering Liquid Glass) are added.

---

## 25.0.0 — Dashboard appearance redesign (all 6 phases built; awaiting deploy, UAT and the Khmer review)

BRD [`VMS_BRD_V25.0.0.md`](VMS_BRD_V25.0.0.md), from the product owner's design brief of 23 Nov 2026. Visual direction
"Calm Fluent" (D-25-1); the Dashboard becomes Today → Health → Insights (D-25-2). No data, permission or API change.

**Phase 1 (design preview, no app change):** [`design/v25-dashboard-preview.html`](design/v25-dashboard-preview.html),
a static preview with sample data in light and dark, English and Khmer, with first-run, loading, error and coach
states, for design sign-off (DAC-09).

**Phase 2 (tokens and colour fixes, app-wide):** `frontend/src/design/tokens.ts` is the one token source (D-25-6).
`theme.ts` maps every status to a meaning family and reads its fill from the tokens, with a separate dark-mode set
(`statusColorsDark`), so `statusColor()` is now mode-aware. The default accent becomes Fluent 2 brand-80 (`#0F6CBD` /
`#4FA3F0`), and the neutrals move to the V25 canvas, card and text values. Motion is 150 / 200 / 250 ms on
`cubic-bezier(0.2, 0, 0, 1)`. `global.css` follows, adds the V25 variables (`--st-*`, `--chart-*`, `--heat-*`,
`--surface-*`, `--divider`, `--border-strong`, `--text-tertiary`), and aliases `--canvas`, `--surface` and
`--text-*` to the variables tones override.

Colour fixes (D-25-4):
- *Open* obstacles and issues are berry, no longer the *Blocked* orange.
- *Waiting* is indigo.
- Blocked/Completed and Moving/Waiting are split by lightness. Every pair of work statuses now clears ΔE 10 under
  protan and deutan simulation; V24's Waiting and In progress were about 1–2 apart.
- *Paused* has its own slate.
- *High* priority moves to `#C2410A` so it never reads as Blocked.

Khmer: Noto Sans Khmer is self-hosted (`@fontsource-variable/noto-sans-khmer`) and declared for the Khmer Unicode
range only, so it downloads only when Khmer is on screen. Khmer text gets a taller line height and no letter-spacing
(D-25-9).

`design/tokens.test.ts` enforces contrast, colourblind separation and that `global.css` matches the tokens. The full
frontend suite passes (162 files, 1344 tests); the build passes.

**Phase 3 (components, not yet on a page):** the V25 building blocks.
- `StatusChip` shows every status as colour + icon + label.
- `KpiCard`: big tabular number that counts up, an optional delta, and "All clear" at zero.
- `ChartCard`: card shell with a "View as table" toggle and its own loading, empty and error states.
- `ProgressRing` and `StackedStatusBar`. Completed sits last in the bar, Paused is striped, and the counts are listed
  under the bar.
- `RankedProgressList`: lowest first, with target ticks and "Show all".
- `NextUpList`: overdue → today → priority → due date, with relative due labels, quick complete and a read-only coach
  mode.
- `ZoneHeader`, and `chartTheme.tsx`, the shared Recharts styling, read from the CSS variables.

Motion respects both the device setting and Appearance → Reduce motion. A test keeps hex colours out of the new
files. 165 test files, 1389 tests pass; the build passes.

**Phase 4 (the redesigned Dashboard):** the page now reads Today → Plan health → Insights.
- **Today:** a greeting, then *Next up* (overdue first, then due today, then by priority, with blocker reasons and a
  done checkbox). Beside it, the overall progress ring with its change over 12 weeks and the portfolio counts. Below,
  three linked figures: Due in the period, Overdue and Blocked, showing "All clear" at zero.
- **Plan health:** vision areas ranked lowest first, goals and tasks as stacked status bars, and dreams by area.
- **Insights:** the attention panel, the restyled 12-week trend, and Obstacles / Partners / Reviews tabs. The review
  heatmap uses the V25 blue scale and shows a review streak. The energy, gratitude, progress-metric and reminder
  cards stay.
- Every chart has "View as table".
- The vision-area and period filters, the drill-down links and the getting-started steps are unchanged.

API: `GET /api/dashboard` adds `nextUpTasks`; existing fields are unchanged. Khmer drafts are added for the new text.
Frontend: 165 files, 1398 tests pass. Backend: 1095 tests pass. Both builds pass.

**Phase 5 (motion and phone layout):**
- **Goal celebration.** When more goals are complete than at your last visit, the progress card plays a short confetti
  burst and a toast says "A goal is complete. One step closer to your dream." It plays once, never on a first visit
  or in an area-scoped view, and never confetti under reduced motion.
- **Loading.** Skeletons keep the final layout, with "Still loading…" after 10 seconds.
- **Motion.** The zones fade in once (200 ms, staggered), and buttons give press feedback.
- **Phones.** The Insights tabs move above the trend, and the trend and status cards open from "Show …" buttons.

Frontend: 166 files, 1409 tests pass; the build passes.

**Phase 6 (documents):**
- [`UAT_V25.md`](UAT_V25.md), with acceptance checks DAC-01 to DAC-10 and feature checks DAF-01 to DAF-11.
- [`km-review-v25.csv`](km-review-v25.csv), 52 Khmer drafts for review.
- A Dashboard section in [`USER_GUIDE.md`](USER_GUIDE.md), including what each status colour and icon means.
- The dashboard fields in [`API.md`](API.md), including `nextUpTasks`.

**Cleanup:**
- Removed the V24 dashboard components the redesign no longer uses: `DashboardSummary`, `CategoryBreakdownChart`,
  `VisionAreaProgressChart`, `PartnerPipelineChart`, and `categoryKeys.ts`.
- Removed the theme palettes only those used (the pie palette, green heatmap ramp, blue chart ramp and partner
  pipeline colours), with their tests. Charts now use the V25 tokens.
- `DashboardCard` stays, because the report summary uses it.
- Frontend: 165 files, 1382 tests pass; the build passes.

Still open: the area target ticks (Q-25-2), which need a server calculation.

---

## 24.0.0 — Security access: sign-in methods (all 5 phases built; awaiting deploy, UAT and the Khmer review)

BRD [`VMS_BRD_V24.0.0.md`](VMS_BRD_V24.0.0.md), from the 24.0.0.0 prototype
([`VMS_BRD_V24.0.0.0 – Security Access Control.md`](VMS_BRD_V24.0.0.0%20–%20Security%20Access%20Control.md), commit
`a545f3d`). Product owner answers on 23 Nov 2026: only real methods get switches (Q-01), Admin only (Q-02), signed out
at the next request (Q-03), "two-step for everyone" with 14 days to set up (Q-04).

**Phase 1 (storage, no visible change):** migration `V92__security_access.sql` adds the one-row `security_settings`
table (password and Google sign-in on, two-step for privileged roles) and `auth_sessions.sign_in_method`; every way of
opening a session records its method. `GET /api/auth/sign-in-methods` (public) and `GET /api/admin/security-access`
(settings and per-method counts, Admins left out).

**Phase 2 (enforcement):** with a method off, non-Admins are refused at password sign-in (only after the right
password), sign-up, invitation acceptance, Google sign-in, sign-up and linking, and again at the Google code exchange
and the two-step code. `PUT /api/admin/security-access` (Admin only, reason, fresh code `ADMIN_SECURITY_ACCESS`)
refuses both off, password off without Google, and any change that strands an account; switching off ends the
sessions opened with that method; `SECURITY_ACCESS_CHANGED` is logged and alerted.

**Phase 3 (two-step for everyone):** `TwoFactorRequirementService.requiredFor` (privileged role, or scope
`EVERYONE`) replaces the privileged-only test for set-up dates, the between-requests set-up check, turning two-step
sign-in off and the admin report. *Everyone* gives accounts without it 14 days; new accounts get their date at
creation; switching back clears participants' dates and never turns two-step sign-in off.

**Phase 4 (frontend):** the Security access page works against the server: two switches with counts, the two-step
choice, a save summary with reason and code, read-only for *View security log*, and the 57-item catalogue as a
collapsed reference with *In VMS* marks. Sign In, Sign Up and the invitation page offer only the methods that are on,
with "Admin? Sign in with a password"; the Google button follows the setting; the two-step set-up prompt asks
participants who have a date. Menu items can name several permissions.

**Phase 5 (documents):** [`UAT_V24.md`](UAT_V24.md) (SAC-01 to SAC-14), [`km-review-v24.csv`](km-review-v24.csv) (72
drafts), `USER_GUIDE.md`, `THREAT_MODEL.md` section 7, `ASVS_L2_CHECK.md` (two rows; Met is now 73), `API.md`.

Tests: backend 1094, frontend 1302, all passing.

## 23.0.0 — User, role and permission management (all 7 phases built; awaiting deploy, UAT and the Khmer review)

BRD [`VMS_BRD_V23.0.0.md`](VMS_BRD_V23.0.0.md). Product owner answers on 22 Nov 2026: custom roles with editable
permissions (Q-01), no separate user-manager role (Q-02), block before delete (Q-03), no reason in the block notice
(Q-04), Add user stops without mail (Q-05), bulk import later (Q-06), `ADMIN_BOOTSTRAP_EMAIL` for the first admin
(Q-07), `CLAUDE.md` rule 14 sentence (Q-08).

**Phase 1 (permissions under the hood, no visible change):** migration `V89__roles_and_permissions.sql` adds `roles`
and `role_permissions`, with Participant, Coach and Admin given exactly today's access, and a foreign key from
`app_users.role`. `Permission` (the catalogue) and `PermissionService` replace the role-name checks (`Roles`, removed)
in every service; `/api/admin/**` and `/api/coach/**` now check permissions; the sign-in answer carries
`permissions`, and the frontend shows menus and buttons by permission (falling back to the role for sessions saved
before V23). One permission was added to the catalogue during build: *Handle support* (issue-report triage and the
failed request log, which the BRD's list had missed). Safety alerts with no coach go to accounts with *Manage
cohorts*; the admin step-up warning shows to anyone with *Manage cohorts*, *Manage AI settings* or *View security log*.
`ADMIN_BOOTSTRAP_EMAIL` makes the first admin once (UM-FR-41). The two-step requirement still follows the built-in
roles until custom roles arrive (phase 4).

**Phase 2 (Users list and detail, backend):** migration `V90__user_management.sql` adds who created the account, the
last role change and block, and `last_sign_in_at` (set whenever a session opens). `GET /api/admin/users` (needs *View
users*) searches by name or email, filters by role, status, cohort and two-step state, sorts by joined, name or last
sign-in, and returns counts for all accounts; `GET /api/admin/users/{id}` adds who created the account, the last role
change and block, record counts and the last 20 security events. Answers hold no plan text (tested with a planted
value) and their types are guarded against secret or plan fields. The case-insensitive search indexes in the BRD were
left out: H2 cannot build them and the list does not need them at this size. The pages come in phase 6.

**Phase 3 (role change, block, unblock, backend):** `PUT /api/admin/users/{id}/role`, `POST …/block` and `POST
…/unblock` need *Manage users*, a reason and a fresh code (new step-up action `ADMIN_USER_BLOCK`). Safeguards: no
action on one's own account; no escalation (the actor must hold every permission of the target's role and of the role
given, so only Admins act on Admins or give the Admin role); the last active admin cannot be demoted or blocked;
demoting a coach of active cohorts needs a new coach, or none, for each, saved together. Blocking ends every session,
pauses the person's plan and report share links (reminders already skipped blocked accounts), and signing in with the
right password now answers 403 "This account is blocked. Contact your program admin." (a wrong password still gets the
usual answer). New events `USER_BLOCKED`, `USER_UNBLOCKED`; notices to the person for a role change, block and unblock
(no reason, no admin browser); alerts for an Admin blocked and more than 5 blocks by one person in an hour; the "Admin
role granted" alert no longer fires on a demotion from Admin; rate limit 30 changes per person per 10 minutes.

**Phase 4 (custom roles, backend):** `GET /api/admin/permissions`, and `GET/POST/PUT/DELETE /api/admin/roles`.
Admins create roles with chosen permissions and change Coach's permissions; Admin and Participant are locked
(Q-09); there is no limit on the number of roles (Q-10). Needed permissions are added together, *Manage roles* is
refused, names are unique regardless of case, and a role is deleted only after its holders move. Removing *Coach
cohorts* asks for a new coach for each active cohort. A role becoming privileged starts the two-step requirement for
its holders (and clears it when it stops being privileged); holders are emailed. Events `ROLE_CREATED`,
`ROLE_UPDATED` (with the permissions added and removed), `ROLE_DELETED`; alerts for sensitive permissions. Under the
hood, `AppUser.role` became a role key so accounts can hold custom roles; every "is not a participant" check (two-step
sign-in, the admin two-step report, the start-up job) now asks whether the role is privileged. Naming someone a
cohort coach no longer overwrites a custom role (it refuses if their role cannot coach). Found and fixed during build:
the permission cache could keep a role's old permissions if read while the change was being saved; it is now
cleared again after the change commits.

**Phase 5 (Add user, edit, resend, reset, delete, backend):** migration `V91__user_invitations.sql`. `POST
/api/admin/users` (*Manage users*) creates an account with no usable password and emails a 7-day single-use
invitation (only its SHA-256 is stored; the email goes out after the change is saved); a privileged role needs a fresh
`ADMIN_ROLE_CHANGE` code and starts the two-step requirement; an email that already has an account is refused; without
mail outside a local run, nothing is created (Q-05). The person opens `/invitation`, chooses a password
(`POST /api/auth/invitation/check` and `/accept`, public, 20 per 15 minutes per network, 410 for a used, replaced or
expired link) and is signed in, or signs in with Google on the same address (UM-FR-26). `PATCH /api/admin/users/{id}`
changes name and cohort (logged as `USER_UPDATED` with field names only); `POST …/invitation` replaces the link;
`POST …/password-reset` sends the normal reset email without showing the link. `DELETE /api/admin/users/{id}` (*Delete
users*) works only on a blocked account (Q-03), needs the email typed exactly, a reason and a fresh `ADMIN_USER_DELETE`
code, emails the person first, then uses the self-deletion erase routine in the same transaction; the log keeps the id,
the viewer and the reason, never the name or email. Events `USER_CREATED_BY_ADMIN`, `USER_UPDATED`,
`USER_DELETED_BY_ADMIN`, `INVITATION_SENT`, `INVITATION_ACCEPTED`; an Admin deleted raises an alert, and deletions
count with blocks towards the "more than 5 in an hour" alert. The account detail adds `invitationPending`,
`invitationExpiresAt` and `coachedCohorts` (id and name) for the dialogs. New settings `INVITATION_URL`, `INVITATION_TTL_DAYS`.

**Phase 6 (the pages):** **Users** (`/settings/users`, *View users*): search, filters by role, status, two-step state
and cohort, sort, counts, 10/25/50/100 rows per page, and a detail panel with only the actions the viewer may take
(edit, change role with a new coach for each coached cohort, block, unblock, resend invitation, send password reset
email, reset two-step sign-in, delete permanently once blocked, with the email typed). **Add user** sends the
invitation. **Roles** (`/settings/roles`, *Manage roles*): every role and what it allows; create, edit (with what
changes and how many people it affects, needed permissions added together, *Manage roles* never offered, a new coach
for each cohort when *Coach cohorts* is removed) and delete (holders moved first). **Invitation page** (`/invitation`):
greets the person, sets their password and signs them in, or explains a used or expired link. Also: the Account page
shows your role and its permissions (UM-FR-45); Cohorts show "coach blocked"; Google sign-in on a blocked account now
says "This account is blocked. Contact your program admin." (new reason `blocked`). Khmer drafts for every new string,
for review. Backend: cohorts answer `coachBlocked`; the account detail's `coachedCohorts` carry id and name.

**Phase 7 (documents):** [`UAT_V23.md`](UAT_V23.md) (UMC-01 to UMC-19, each with its automated test),
[`km-review-v23.csv`](km-review-v23.csv) (133 Khmer drafts), `USER_GUIDE.md` (your role, accepting an invitation, Users
and Roles for admins), `THREAT_MODEL.md` section 6, `ASVS_L2_CHECK.md` V8 rows, and business rule 14 in `CLAUDE.md`
gains the D-23-3 sentence (Q-08). A test was added for abuse case 19 (more than 5 blocks in an hour raise an alert),
which had no automated check.

## 21.0.0 — Two-factor sign-in (live 22 Nov 2026; awaiting UAT, sign-off of F-02 to F-04 and Q-09, and the Khmer review)

BRD [`VMS_BRD_V21.0.0.md`](VMS_BRD_V21.0.0.md). Product owner answers on 22 Nov 2026: coaches must use 2FA too
(Q-01), a QR code library may be added (Q-02), participants may use an email code (Q-03), and admins may reset
another person's 2FA (Q-05).

**Live on production 22 Nov 2026** (`48098fa`; frontend by manual deploy after the SonarCloud S4790 HMAC-SHA1
finding was accepted in SonarCloud, RFC 6238 requires it).

**ASVS F-05 fixed after release:** a session not used for 14 days (`SESSION_IDLE_DAYS`) now ends at its next
refresh, with reason `IDLE_TIMEOUT`, even inside the 30-day "remember me" limit.

**ASVS F-04 (counts) fixed after release:** sign-in lockouts and all rate limits are kept in Redis when it is
configured (production), so a restart or deploy no longer clears them; keys are hashed, and the limiters fall back
to in-memory counts while Redis does not answer. Internal TLS stays an accepted-risk candidate.

**BRD 10 close-out, step 1 (SEC-04-02):** passwords are hashed with BCrypt at cost 12 (`BCRYPT_COST`; production
refuses to start below 12). Hashes made at the old cost 10 are replaced at the person's next successful sign-in, from
the password they typed, so nobody has to change their password.

**BRD 10 close-out, step 2 (SEC-04-06):** pre-import Excel snapshots are deleted daily after 30 days
(`EXCEL_BACKUP_RETENTION_DAYS`), are readable by the server's user only, and get a random part in their name so two
imports in the same second no longer overwrite each other. The import answer now gives only the file name instead of
the server's full path, and the Import page says how long the copy is kept.

**BRD 10 close-out, step 3 (SEC-09-04):** AI spending alerts. Every 15 minutes `AiBudgetWatch` checks the month's
spend against the budget (warnings at 80% and 100%, once each per month) and the last hour against the week's average
(at least $1 and 5 times the average). Each warning is an `AI_BUDGET_WARNING` security event, raised once even across
restarts, and goes to the server log and the alert email like the other alerts.

**BRD 10 close-out, step 4 (SAC-06, SAC-17):** a weekly OWASP ZAP baseline scan of the production frontend and API
(`.github/workflows/zap-baseline.yml`, also run on demand). It is passive and fails on a Medium or High finding that is
not accepted, with a reason, in `.zap/rules.tsv`. The action and the ZAP image are pinned; no GitHub issues are
created.

**BRD 10 close-out, step 5:**
- SEC-04-04: secrets can be rotated without losing data. `SETTINGS_ENCRYPTION_KEY_PREVIOUS` keeps the Claude key and
  every authenticator secret readable while `SecretResealJob` re-seals them at start-up; `TWO_FACTOR_SECRET_PREVIOUS`
  keeps recovery codes working. Before this, rotating the encryption key would have broken everyone's authenticator
  app. The incident plan is corrected and `SECURITY.md` has a rotation table.
- SEC-05-08: each Claude request carries a random check value that Claude is told never to repeat; a reply containing
  it (the instructions being repeated) is withheld as a refusal, and streaming stops before it is shown.
- SEC-02-08: a hardening checklist per environment in `SECURITY.md`.

**First ZAP baseline scan (22 Nov 2026):** the API has no Medium or High findings. The frontend's two Medium CSP
findings (style-src `'unsafe-inline'` for MUI, and `img-src https:` for pasted dream pictures) were accepted by the
product owner and are listed by name in `.zap/accepted.tsv`, so any new CSP finding still fails the scan. Findings now
show as annotations on the run page.

### Phase 1 — data and core (2026-11-22)
- **Data.** Migration `V87__two_factor_sign_in.sql`: `two_factor_settings`, `two_factor_recovery_codes`,
  `sign_in_challenges`, `trusted_devices`, and `app_users.two_factor_due_at`. Deleting an account removes them.
- **Authenticator codes (TF-FR-02, TF-FR-13).** `TotpCodes`: RFC 6238 (HMAC-SHA1, 6 digits, 30 seconds) on the JDK,
  one step of drift either side, no reuse of an accepted step, Base32 keys and `otpauth://` addresses. Tested with the
  RFC's Appendix B vectors.
- **Recovery codes and tokens (TF-FR-04, TF-NFR-01).** `TwoFactorCodes`: ten `ABCDE-FGHJK` codes, 256-bit tokens,
  keyed hashes apart from V20's; authenticator secrets sealed with `SecretCipher` (`TotpSecretVault`).
- **Settings.** `TWO_FACTOR_ENABLED`, `TWO_FACTOR_SECRET`, `TWO_FACTOR_CHALLENGE_MINUTES`,
  `TWO_FACTOR_TRUSTED_DEVICE_DAYS`, `TWO_FACTOR_GRACE_DAYS`; a daily clean-up at 03:55.
- Signing in does not change yet (phase 2).

### Phase 2 — the second step at sign-in (2026-11-22)
- **Challenge (TF-FR-10, TF-FR-11, TF-FR-18).** With 2FA on, `POST /api/auth/login`, Google `exchange` and `link`
  answer `{ "status": "TWO_FACTOR_REQUIRED", "challengeToken", … }` instead of a session (5 minutes, used once).
- **Verify (TF-FR-13 to TF-FR-17).** `POST /api/auth/two-factor/verify` takes an authenticator code (one step of drift,
  never twice), an email code or a recovery code (once each). Five wrong codes end the challenge, and each counts as a
  failed sign-in; the count resets only after the full sign-in.
- **Email method (TF-FR-14, TF-FR-38).** The code is emailed; `POST /api/auth/two-factor/resend` once a minute;
  without mail the answer says so and a recovery code is needed.
- **Trusted devices (TF-FR-21 to TF-FR-26).** "Remember this device" returns a `deviceToken`; later sign-ins with it
  skip the second step for 30 days; at most 10 per account; password change and reset remove them.
- **Audit.** 13 new security events; alerts for many failed codes and for a recovery code used on an admin account;
  an email notice when a recovery code is used.

### Phase 3 — setup and the second-step page (2026-11-22)
- **Setup (TF-FR-01 to TF-FR-09).** `GET /api/account/two-factor`; `POST …/totp` and `…/totp/confirm` (a pending
  authenticator until its first code, migration `V88__two_factor_pending_setup.sql`); `POST …/email` (participants);
  `POST …/recovery-codes`; `POST …/disable` (not for an admin or coach past their date). Each change needs a fresh
  "Verify it's you" code (`TWO_FACTOR_SETTINGS`) and sends a notice. Both methods get ten recovery codes.
- **Trusted devices (TF-FR-23).** Listed on the Security page; `DELETE /api/account/trusted-devices/{id}` and
  `DELETE /api/account/trusted-devices`.
- **Frontend.** `/sign-in/two-factor` after password, Google sign-in and linking; the Security page panel with a QR
  code (`qrcode` 1.5.4, approved in Q-02) and the key as text; recovery codes shown once with Download and Copy;
  remembered devices. 65 Khmer entries.

### Phase 4 — admins and coaches, admin reset (2026-11-22)
- **Required for admins and coaches (TF-FR-32 to TF-FR-37).** A set-up-by date 14 days ahead at start-up and when
  someone is made a coach; before it, "Set up now / Skip for now (N days left)"; from it, every API call except setup
  answers `403 TWO_FACTOR_SETUP_REQUIRED` and the app opens the Security page.
- **Admin page (TF-FR-28 to TF-FR-30, TF-FR-36).** **Settings → Two-step sign-in** lists admins, coaches and anyone
  with it on; **Reset** needs a reason and the admin's own fresh code, ends the person's sessions, emails them, raises
  an alert and gives an admin or coach 3 days. Never for the admin's own account.
- **"Verify it's you" with an authenticator (TF-FR-40).** A current app code works instead of the emailed one.
- **Export (TF-FR-41).** A `twoFactor` section with status, method, codes left and devices; never a secret or code.
- **Alerts.** An admin reset, and an admin or coach turning it off, raise an alert at once.
- **Docs.** `API.md`, `DATA_MODEL.md`, `USER_GUIDE.md`, `SECURITY.md`, README, `UAT_V21.md`, `km-review-v21.csv`.

### Phase 5 — threat model and ASVS Level 2 check (2026-11-22)
- **Threat model (TF-FR-43, SEC-06-01).** `docs/THREAT_MODEL.md`: trust boundaries, threats, abuse cases and
  residual risks for sign-in and recovery, coach access, share links, Excel import and Claude.
- **ASVS 5.0 Level 2 (TF-FR-44).** `docs/ASVS_L2_CHECK.md`: 82 control areas across V1–V17; 70 met; findings F-01
  to F-08.
- **Fixed (TF-FR-45).** F-06: at most 5 sign-in code emails per account per hour. F-08: `STEP_UP_SECRET` and
  `TWO_FACTOR_SECRET` generated in `render.yaml` instead of falling back to `JWT_SECRET`.
- **Abuse cases (TF-FR-46, SEC-06-04).** Every BRD's acceptance criteria include at least one; rule in `SECURITY.md`.
- Closes BRD 10 Phase 7 except the items above that wait for the product owner.

---

## 14.0.0 — Sign in with Google (live on production since 22 Nov 2026)

BRD [`VMS_BRD_V14.0.0.md`](VMS_BRD_V14.0.0.md), resumed 2026-11-21.

### Phase 1 — setup and data (2026-11-21)
- **Settings and switch (GA-FR-01, 02, 04).** `GOOGLE_CLIENT_ID`,
  `GOOGLE_CLIENT_SECRET`, `GOOGLE_REDIRECT_URI` and `FRONTEND_URL`. Without the
  client ID and secret the feature is off: `GET /api/auth/oauth/google/status`
  answers `{ "enabled": false }` and `authorize-url` answers 503. The secret is
  never logged or returned.
- **Data (GA-FR-28 to 31).** Migration `V85__google_sign_in.sql`:
  `external_identities`, `oauth_login_attempts`, `oauth_handoffs`; expired
  attempts and codes are deleted daily at 03:50. "Delete my account" removes
  Google links.
- **Library (D-14-1).** `spring-security-oauth2-jose` added for Phase 2.

### Phase 2 — start and Google's answer (2026-11-21)
- **Start (GA-FR-05 to 08).** `authorize-url` records a 10-minute, single-use
  attempt (state stored as a SHA-256, PKCE verifier sealed, nonce) and returns
  Google's address with `openid email profile`, S256 and `prompt=select_account`.
  `from` keeps only in-app paths; anything else becomes `/`.
- **Google's answer (GA-FR-09 to 13).** `GET /api/auth/oauth/google/callback`
  uses the state once, exchanges the code with the client secret and PKCE
  verifier (5-second timeout, fails closed), and verifies the ID token:
  RS256 signature against Google's key set (re-fetched on an unknown key ID),
  issuer, audience, expiry with 2 minutes' leeway, nonce and `email_verified`.
  Every outcome is a 302 to `{FRONTEND_URL}/oauth/google/callback#result=error&reason=…`
  (`cancelled`, `expired`, `verification`, `google`, `unavailable`), with
  `Referrer-Policy: no-referrer` and `Cache-Control: no-store`.
- **Tests.** A local Google stub (`GoogleStubServer`) signs test ID tokens; no
  test calls Google.

### Phase 3 — the three outcomes and linking (2026-11-21)
- **Outcomes (GA-FR-14 to 19).** A known Google `sub` signs in (a changed Gmail
  address is recorded, the account email stays). An email that already has an
  account gets `#result=link&code=…` and nothing is linked yet (GA-SEC-06). A
  new email creates a USER account with a random, unknown password hash
  (GA-FR-25) and records `REGISTERED` "google". Disabled accounts end on
  `reason=unavailable`. Fragments carry `from` for the page to open afterwards.
- **Codes (GA-FR-19, 20, GA-SEC-07).** Sign-in codes last 60 seconds and link
  codes 10 minutes; each works once and only its SHA-256 is kept.
  `POST /api/auth/oauth/google/exchange` returns the normal sign-in answer, or 400.
- **Linking (GA-FR-23, 24).** `POST /api/auth/oauth/google/link` checks the
  password with the sign-in lock: a wrong password is a failed sign-in, 400,
  and the code stays usable; 429 when locked.
- **Data (D-14-3).** Migration `V86__app_users_password_set.sql` adds
  `app_users.password_set`: false for accounts created with Google, set to
  true by a password reset.
- **Security log (GA-FR-33).** `GOOGLE_SIGN_IN_SUCCEEDED`, `GOOGLE_SIGN_IN_FAILED`
  (reason code only) and `GOOGLE_LINKED`, also in the admin log's filter.

### Phase 4 — frontend (2026-11-21)
- **Callback page (GA-FR-21).** New `/oauth/google/callback` reads the fragment,
  clears it from the address bar at once, and then signs in (session stored
  where "remember me" chose), opens the linking screen, or opens the error
  screen with a message for the reason. The code is used once, even under
  React's development double effects.
- **Button (GA-FR-03, 22, D-14-4).** The Google button and its divider show
  only when `GET …/status` says Google sign-in is on. Sign In passes "remember
  me" and the page to return to; Sign Up remembers the session. The fragment
  now also carries `remember`.
- **First password (GA-FR-26, D-14-3).** An account created with Google sets a
  password on the Security page with only the email code; no current password
  is asked. `POST /api/auth/change-password` accepts a missing
  `currentPassword` for such accounts only.
- **Account page (GA-FR-27).** New `GET /api/account/sign-in-methods`; a
  "Sign-in methods" card shows "Google: connected as …" and whether a password
  is set. It stays hidden for accounts that never used Google.
- **Khmer (GA-NFR-05).** 21 new entries, including the server's Google messages.

### Phase 5 — hardening and docs (2026-11-21)
- **Delete and export (GA-FR-32, D-14-3).** An account created with Google is
  deleted with the fresh email code alone (the Account page hides the password
  box); accounts with a password still need it. The data export gains a
  `linkedAccounts` section (provider, email, link date; never Google's `sub`).
  Deleting an account removes its Google link and open codes.
- **Rate limits (GA-SEC-09).** `authorize-url`, `callback`, `exchange` and
  `link`: 60 per network per 15 minutes (`RATE_LIMIT_GOOGLE_SIGN_IN_*`). Over
  the limit the callback still redirects, with `reason=too-many`.
- **Alerts (GA-FR-34).** Failed exchanges are logged as `GOOGLE_SIGN_IN_FAILED`
  "exchange"; 50 failed Google sign-ins from one network in 15 minutes raise a
  `SECURITY ALERT`.
- **Headers (GA-SEC-10).** Tested: the callback keeps CSP, HSTS and nosniff and
  adds `Referrer-Policy: no-referrer`.
- **Render (GA-SEC-12).** `GOOGLE_CLIENT_ID` and `GOOGLE_CLIENT_SECRET` as
  dashboard-only secrets; `GOOGLE_REDIRECT_URI` and `FRONTEND_URL` set.
- **Docs.** README, `docs/API.md`, `docs/SECURITY.md`, `docs/USER_GUIDE.md`.

### Phase 6 — rollout preparation (2026-11-21)
- **Mail required (D-14-5).** Google sign-in is on only when its settings are
  present and "Verify it's you" codes can be emailed; otherwise the button is
  hidden, the endpoints answer 503 and a start-up warning says why.
- Production switch-on (Google Cloud client, Render secrets, published consent
  screen, live check) is done by the project owner; see BRD 14 Appendix A.

---

## Unreleased — no BRD change

**Build my plan session limit is now an AI setting** (PB-NFR-03 follow-up): admins set "AI requests per Build my plan
session" (1 to 100, default 15, migration V103) on the AI settings page instead of the fixed 15. The guide, the draft's
counter and the "used its N AI requests" message all use the setting; raising it lets a session that ran out carry on.

**Management Report Summary tab, per the Optimize Report-management prototype** (`docs/Optimize Repot-management/summary.html`):
a Summary header with scope and generated date; six tiles with sub-lines and "View … →" links; Progress by goal and Open
tasks by priority as bar lists with Show as table; Goal health (reasons and a health pill) beside Needs attention (with
Open task / Resolve / Open step / Open goal links). The period figures and What changed stay below; the other three charts
and the full goal table moved under **More detail**. The due-in-14-days tile shows "Due by {date}" instead of the
prototype's on-track / at-risk split, which the report does not calculate. Fixes found on the way: hidden report charts are
now `inert` (Recharts left a focusable layer inside a screen-reader-hidden area), and the visually hidden labels in the
report and dashboard used `width: 1` (100% in MUI), which could cause sideways scrolling on phones. The report page joins
the e2e accessibility sweep (desktop, dark, phone).

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

## [20.0.0] — Phases 1 to 4 built

Implements [BRD 20.0.0](VMS_BRD_V20.0.0.md) ("Verify it's you"). Migration
`V84__step_up_challenges.sql`.

### Added in phase 4
- **Admin actions (SV-FR-06 to SV-FR-08).** Saving or removing the AI key, making someone a coach (a role change) and
  changing a cohort's coach need a fresh code for the admin every time. The cohort form asks for whichever code the
  server names; other cohort edits need none.
- **Mail warning (SV-FR-34).** `GET /api/admin/step-up` and a warning on the Cohorts, AI settings and Security log pages
  while codes are on but no mail server can send them.
- Docs: `UAT_V20.md`, `SECURITY.md`, `DATA_MODEL.md`, `USER_GUIDE.md`, `API.md`.

### Added in phase 3
- **Confirm a new reminder address (SV-FR-04, SV-FR-05).** Saving a different reminder address needs the account check
  (a code to the account email, or the verified window) and then a code sent to the new address, so reminders and the
  test email only ever go to an address the person controls. Clearing it needs only the account check; other reminder
  settings need no code. The page asks for both codes in turn and saves once.
- **Change notices (SV-FR-28 to SV-FR-31).** After a password change, a data download, a reminder address change or an
  account deletion, a short email goes to the account email (and, for a reminder address, to the old address): what
  changed, when in the person's time zone, from which browser, and "If this was not you, change your password now".
  Sent only after the change is saved, never for one that failed; no code and no link.
- The `STEP_UP_REQUIRED` answer names the code it wants (`action`).

### Added in phase 2
- **Protected actions (SV-FR-01 to SV-FR-03).** Changing the password, downloading all your data and deleting the
  account now need an emailed code (`X-Step-Up` header), checked before the password. Password change and download
  share the 10-minute verified window; deleting always needs its own code from the last 5 minutes.
- **Verify it's you dialog (SV-FR-20 to SV-FR-27).** One dialog for every protected action: the masked address and
  minutes left, a code box that phones can fill from the email and that submits at six digits, a resend countdown,
  clear messages for wrong, ended and unsent codes, and a "no mail on this server" path that goes on with the password
  checks. When the server asks for a code, the page opens the dialog and repeats the action once afterwards, keeping
  what was typed. Each protected action says "We'll email you a code to confirm it's you."
- **Delete confirmation word (SV-FR-35).** `DELETE` in any case, with spaces around it, enables the button; another
  word shows "Type DELETE to enable the button."
- Khmer drafts in `docs/km-review-v20.csv`.

### Changed in phase 2
- Error answers carry `code` and `triesLeft` to the frontend (`ApiError`), including for file downloads.
- The shared `Input` passes `inputMode` to the real input element.

### Added in phase 1
- **Email codes (SV-FR-10 to SV-FR-18).** `POST /api/account/step-up` sends a six-digit code by email for one action
  (in English or Khmer); `POST /api/account/step-up/{id}/verify` checks it. A code works for 10 minutes and once, stops
  after 5 wrong tries, is tied to the person, the session and the action, and only its HMAC is stored. A new code can be
  asked for after 60 seconds, at most 5 an hour and 20 a day.
- **Verified window (SV-FR-16).** A code for a settings action (change password, download my data, change reminder email)
  covers the others in the same session for 10 minutes. Deleting the account and admin actions always need their own
  code. A password change or reset closes every window.
- **Stolen-session signal (SV-FR-19).** Ten wrong codes within an hour end the person's other sessions and send a
  security notice.
- **No mail (SV-FR-32, SV-FR-33).** Without a mail server the code is written to the log on a local or test run only;
  in production no code is sent and the action keeps its password checks (D-20-4). A failed send keeps nothing.
- **Security log.** `STEP_UP_CODE_SENT`, `STEP_UP_VERIFIED`, `STEP_UP_FAILED`, `STEP_UP_LOCKED` and
  `STEP_UP_SKIPPED_NO_MAIL`, never with the code; the admin log filter lists them.
- New optional settings: `STEP_UP_ENABLED` (default true) and `STEP_UP_SECRET` (the code HMAC key; defaults to
  `JWT_SECRET`).

---

## [19.0.0] — Phases 1 to 5 built

Implements [BRD 19.0.0](VMS_BRD_V19.0.0.md) (Report Management). Migrations `V79__obstacle_resolved_at.sql`,
`V80__management_reports.sql`, `V81__report_exports_and_shares.sql`, `V82__report_reminders.sql` and
`V83__report_coach_shares.sql`.

### Added in phase 5
- **Draft with AI (RM-FR-29).** Next to **Suggest a draft**, a draft report's commentary has **Draft with AI**, shown
  only while AI can run for the participant. Claude reads only the facts the rule-based draft is built from (period,
  each goal's progress, health and reasons, and the rule-based notes), under the V15 rules: consent, the daily limit
  and the monthly cap. Like the rule-based draft it fills only empty sections, marked **Suggested**, and saves
  nothing. Logged as `REPORT_DRAFT`.
- **Report reminder (RM-FR-49).** The reminder card (Dashboard and Account) gains **Remind me to prepare a report**:
  weekly on a chosen weekday, or monthly on the last working day. When it falls, a notice shows on the Dashboard
  (with **Open Reports**) and on the Reports page (with **New report from this** for the latest final report) for a
  week, until a report is finalised; and, when mail is set up, the reminder job emails the participant that day, even
  with daily reminders off. `REMINDERS_REPORTS_URL` adds a link to the Reports page in that email.

- **Share with my coach (RM-FR-50, D-19-3 approved 21 Nov 2026).** A participant in an active cohort can share a final
  report with their coach from the report page, choosing the sections the coach sees. The coach finds it under
  **Shared reports** on the participant's page and reads it read-only, the same view as a share link. **Stop
  sharing**, archiving the report, or leaving the cohort removes access at once. `CLAUDE.md` Core Business Rule 14 now
  includes these reports.

### Changed in phase 5
- The shared report view is now one component used by the public share link and the coach view.
- Archiving a report that is shared with the coach asks first and says the coach will lose it.

### Not built
- **Native PowerPoint charts (RM-FR-45)** were not built: Q-05's draft answer is no, so the deck keeps drawn shapes.

### Added in phase 4
- **PDF (RM-FR-42).** **Export to PDF** on the live report and on saved reports: A4 landscape, with the cover,
  commentary, headline and period figures (and their changes), goal health with reasons and progress bars, What
  changed, blockers, overdue work, next actions and the support needed; only the chosen sections; page numbers and
  "Final · frozen …" or "DRAFT · …" in every footer. Khmer prints.
- **Share links (RM-FR-46 to RM-FR-48, RM-FR-51).** A final report can be shared by a read-only link lasting 7, 30 or
  90 days. Before making it, the participant sees exactly which sections the reviewer will see and when it ends; the
  link is shown once. Each link shows its views and can be stopped at once; archiving a report stops them all.
- **Shared page (RM-FR-47).** Reviewers open the report without an account: the frozen figures, the sections shared
  and a PDF download, with no links into the plan. The page is kept out of search engines and sends no referrer. A
  link that ended says only "This report is no longer shared".
- **Download history (RM-FR-25).** A saved report lists its Excel, PowerPoint and PDF downloads under the Export tab.
- **Audit and limits (RM-FR-52, RM-NFR-07).** Making and stopping links is in the security log; views are logged
  with no visitor details. 20 links a day per account and 60 views a minute per link.

### Changed in phase 4
- The deck's lists (blockers, overdue, next actions, support, What changed) now come from one shared class that the
  PDF uses too, so both always say the same thing. No visible change to the deck.

### Added in phase 3
- **Compare and What changed (RM-FR-32 to RM-FR-37).** A report is compared with an earlier final report on the same
  scope: by default a draft uses the latest one, and both drafts and the live report can pick one. Each headline tile
  shows its change ("+12", with "up 12 points" for screen readers); the goal health table gains a Change column ("At
  risk → On track · +12 points · Target 10 Jan → 24 Jan"); a **What changed** card lists, most important first, goals
  that got worse, dates moved later, new Critical and High blockers, blockers closed, goals that got better and tasks
  completed, and the goals no longer in the report with why. Items are matched by id, so a renamed item is still the
  same item. Finalise freezes the comparison with the figures.
- **Files (RM-FR-39 to RM-FR-41, RM-FR-43, RM-FR-44).** Excel gains a Commentary sheet and a Changes sheet; the deck
  gains a Highlights slide, a What changed slide and the participant's own words on the support slide, with goal
  notes in the speaker notes. Draft files are marked **DRAFT**. The Export tab chooses which sections go in the files
  (remembered per saved report) and the file language.
- **Suggest a draft** now lists goals that moved to On track since the compared report.

### Fixed in phase 3
- On a saved draft, choosing a report to compare with no longer resets itself because an empty area was read as a
  change of scope.

### Added in phase 2
- **Filters (RM-FR-10 to RM-FR-12).** Priority, owner, goal health and "Include completed goals" below the scope and
  period, kept in the URL, listed in the files' as-of line, with **Reset filters**.
- **Details tab (RM-FR-13 to RM-FR-15).** The Excel Report sheet's rows on screen with search, sorting, Overdue /
  Blocked / Due soon quick filters and 50 rows a page, made by the same server code as the Excel file.
- **Saved reports (RM-FR-16 to RM-FR-24).** **Save as report** keeps a report as a Draft with live figures;
  **Finalise** freezes its figures in a checksummed snapshot; a final report can only be renamed or archived;
  **New report from this** starts the next period with the commentary carried over. A **Saved reports** page lists
  them. Files downloaded from a final report come from its snapshot.
- **Commentary (RM-FR-26 to RM-FR-31).** Highlights, Risks and issues, Decisions and support needed (or "Nothing needed
  this period"), Focus for next period and a note per goal, saved as you type, with a rule-based **Suggest a draft**.

### Added in phase 1
- **Reporting period (RM-FR-06 to RM-FR-09).** The Management Report has a period picker (this or last week, month or
  quarter, or custom dates up to 366 days), kept in the URL. A "This period" card shows tasks completed, progress
  gained, blockers opened and closed, and tasks that became overdue; for a past period it says status figures are as
  of today. The as-of line on the page and in both files names the period.
- **Health reasons (RM-FR-01).** Every goal's health says why ("Target date passed 3 day(s) ago", "Critical blocker:
  No budget"): under the label in the goal health table, in a **Why** column in the Excel Summary sheet, and under each
  goal on the deck's progress slide.
- **Done – open work (RM-FR-02).** A Completed goal with open tasks gets its own health and a "Reopen or finish" item in
  Needs attention, linking to its tasks.
- Obstacles record when they were closed (`resolved_at`), so blockers closed in a period can be counted.

### Changed in phase 1
- The report model holds plain values copied from the plan, never entities, so private fields cannot reach a file and
  a later phase can store it as a snapshot (RM-NFR-02). No visible change from this alone.
- BRD 18.0.0's open questions Q-01 to Q-06 are recorded as answered with their draft answers (RM-FR-03).

---

## [18.0.0] — All 4 phases built; awaiting UAT, sign-off of Q-01 to Q-06 and the Khmer review

Implements phase 1 of [BRD 18.0.0](VMS_BRD_V18.0.0.md): a **Management Report** under the Vision Map. No migration.

### Fixed
- SonarQube quality gate on `5abebcf` (reliability rating C on new code): `ManagementReportService.ReportFile` holds a
  `byte[]`, so a record's generated `equals`, `hashCode` and `toString` compared and printed the array's identity
  (rule java:S6218). They now use its contents, and `toString` shows only the size.

### Added in phase 4
- **Progress over the last 12 weeks (MR-FR-14).** A line chart on the Summary tab of average task progress per week,
  rebuilt from the progress log for the tasks in scope (the same measure as the dashboard trend), with a text summary
  and a table view.
- **Docs and review.** A Management Report section in the user guide and README, a UAT checklist
  ([UAT_V18.md](UAT_V18.md)) and the completed Khmer review file ([km-review-v18.csv](km-review-v18.csv)), including
  every fixed word inside the exported files.

### Added in phase 3
- **Export to PowerPoint (MR-FR-23 to MR-FR-31).** The Export tab downloads `Management_Report_{scope}_{date}.pptx`, a
  16:9 deck: title, executive summary (overall progress, goals by health, three rule-based sentences), progress by
  goal (drawn bars with health labels), priorities and deadlines (a table and what is due in 14 days), blockers and
  management issues (where, why, who could help), overdue items, next actions (one per goal) and, when there is
  something to ask for, decisions and support needed. Long lists stop at 6 to 8 items with "and N more (see the Excel
  report)". Every slide has a real title, a slide number and speaker notes; all text stays editable. Optional deck
  title and "Put my name on the title slide". English or Khmer.
- New endpoint `GET /api/reports/management.pptx`, sharing the report download limit.

### Added in phase 2
- **Export to Excel (MR-FR-15 to MR-FR-22).** The Export tab downloads `Management_Report_{scope}_{date}.xlsx` for the
  chosen scope, in English or Khmer. Three sheets: **Summary** (headline figures, goal health, tasks by status, open
  tasks by priority, the goal health table), **Report** (one row per task under its goal and step, with exactly the
  columns Goals, Steps, Tasks, Blockers / Management Issues, Progress (%), Priority, Due Date and Next Action, and a
  Dream column first when more than one dream is in scope) and **Blockers** (every open blocker and Blocked task, with
  who could help). Frozen header, filter, wrapped text, Completed rows green, blocked rows orange, overdue dates red,
  High and Critical highlighted; landscape, one page wide, header repeated, page numbers. Nothing is written to disk.
- New endpoint `GET /api/reports/management.xlsx`, limited to 30 downloads per account per 10 minutes
  (`RATE_LIMIT_REPORT_EXPORT`, `RATE_LIMIT_REPORT_EXPORT_WINDOW`).

### Added in phase 1
- **Report page (MR-FR-01 to MR-FR-06).** `/vision-map/report`, in the sidebar after Timeline and linked from the
  Vision Map and Timeline headers (with the dream already chosen from a dream's map). Summary and Export tabs, and a
  scope picker (all dreams, one vision area, one dream), all kept in the URL. Every view states its scope and date.
- **Summary tab (MR-FR-07 to MR-FR-13).** Headline tiles (overall progress, goals by health, overdue, blocked, open
  blockers, due in 14 days), charts of progress by goal, tasks by status, open tasks by priority (on track and overdue)
  and blocked items by kind, a goal health table (Off track, At risk, On track, Done, each with an icon and a label)
  and a "Needs attention" list. Every chart has a text summary and a "Show as table" view.
- **One report model.** `GET /api/reports/management` builds the report from the participant's own unarchived plan
  with a fixed number of queries. The rules (overdue, blocked items, goal health, next action) live in
  `service/report/ReportRules.java`, so the Excel report and deck in phases 2 and 3 use the same figures.
- The Export tab shows the two downloads, disabled until phases 2 and 3.

---

## [17.0.0] — All 5 phases built; awaiting UAT, sign-off of D-17-1 to D-17-3 and Q-01, and the Khmer review

Implements phases 1 and 2 of [BRD 17.0.0](VMS_BRD_V17.0.0.md): blockers sit on a step or a task, not on a dream or goal,
and dreams and goals describe only the outcome. Migrations: `V75__obstacle_anchor_consistency.sql` (phase 1), `V76__step_gap_kind_and_origin.sql` (phase 3), `V77__task_block_count.sql` (phase 4), `V78__chain_priorities_persistence.sql` (phase 5). `CLAUDE.md`
updated for decisions D-17-1 and D-17-2.

### Added in phase 5
- **The six chains (BL-FR-30 to BL-FR-32).** The chain check asks settling, fear of failure, avoiding criticism,
  misplaced priorities, giving up too soon and unclear vision. Not knowing how and lack of resources are step gaps now.
  Past takes keep their scores, shown as "No longer measured (moved to step gaps)"; the trend compares only shared
  chains. Migration `V78__chain_priorities_persistence.sql`.
- **Before the first dream (BL-FR-33).** With no dreams and no chain check, the Dreams page and the dream guide offer
  "Take the 2-minute check" or "Skip for now" (hidden for 30 days). Never required.
- **Fear journal link (BL-FR-34).** A fear or criticism score of 1 or 2 offers "Write it down in the fear journal".
- **Practices (BL-FR-35).** Priorities: one A-rank task first each day. Persistence: one small daily habit. Both on
  the Today page.

### Added in phase 4
- **Break into smaller tasks (BL-FR-24 to BL-FR-26).** A Blocked task, or one In Progress past its due date, offers
  **Break into smaller tasks** on the Tasks board (row menu, card, form) and the Vision Map. Two to five rows, due
  dates spread up to the task's date, a "Could you start this in the next 15 minutes?" tick box, and a choice to keep
  the original as a checklist parent (default) or replace it (archived). New endpoint `POST /api/tasks/{id}/split`.
- **What changed? (BL-FR-27).** Moving a task out of Blocked asks one optional line, saved to its progress log.
- **Repeated blocks (BL-FR-28).** The third block suggests breaking it down or adding an obstacle on the task.
  Migration `V77__task_block_count.sql`.
- **Suggest smaller tasks (BL-FR-29).** With AI on, Claude fills the rows; nothing is saved until the participant saves.

### Added in phase 3
- **Gap question and kind (BL-FR-17, BL-FR-18).** The step form's Resources field now asks "What is missing, and who or
  what could supply it?", with an optional **Kind of gap** that shows the suggested helper type. Migration
  `V76__step_gap_kind_and_origin.sql` (additive).
- **Turn this gap into a step (BL-FR-19, BL-FR-20).** From a step with gap text (Steps page and Vision Map) or from any
  open, non-exempt obstacle (Obstacles page), a pre-filled "Find a {helper} for {gap}" step is added before the work
  that needs it, optionally with a To Contact partner. An obstacle moves onto the new step and to In Progress.
- **Link back (BL-FR-21).** A "find help" step shows "Created to cover a gap in S-012"; archiving it asks whether to
  archive the partner it created too.
- **Plan check (BL-FR-22).** New `GAP_NEEDS_HELP` issue: "Step S-014 is missing a skill. Who could help?"
- **FR-82 reuse (BL-FR-23).** Strengths and Gaps' learning step is created by the same logic.

### Added in phase 2
- **Outcome-only coaching (BL-FR-11 to BL-FR-13).** The dream and goal forms (Dreams page, Goals page and the Vision Map)
  say "describe the outcome; gaps come in the steps". A soft, dismissible note appears when the text names a limit
  ("can't afford", "if I have time"). The phrase list is in `frontend/src/utils/limitPhrases.ts`, English only.
- **Goal coaching question (BL-FR-14).** "What can block this goal?" is now "Which steps will need something you don't
  have yet?" in the plan assistant help and `CLAUDE.md`.
- **Blocker counts (BL-FR-15, BL-FR-16).** Dream and goal rows on the Vision Map, and a Blockers column on the Goals
  table, show "2 blockers · 1 blocked task", linking to the Obstacles page filtered by dream or goal (new `goalId`
  filter). No add-obstacle action at these levels; the obstacle form offers "Add a step first" when there are no steps.
- **Risks on steps (BL-FR-37, BL-FR-38, BL-FR-39).** Build my plan's risk card offers the plan's steps or "A new step for
  this risk, in: {goal}" (saved as a step "Deal with: {risk}"), never "The whole dream"; Add refuses a risk without one.
  The plan assistant only proposes and saves risks on a step. AI-suggested risks keep a step key only. The Dream Coach
  system prompt states the outcome-only and placement rules.

### Changed in phase 2
- The phase 1 bridge is gone: the AI Plan Builder and plan assistant now create obstacles through the same placement
  rule as the Obstacles page. Only the Excel import still saves unplaced obstacles (from older workbooks).

### Added in phase 1

- **Placement rule (BL-FR-01, BL-FR-02, BL-FR-04).** A new obstacle needs a step or task; its goal and dream are worked
  out from it. Partner conflicts with worksheet answers may stay off the steps.
- **Moves follow (BL-FR-08).** Moving a task, step or goal re-derives the goal and dream of the obstacles on it.
- **"Not on a step yet" (BL-FR-03, BL-FR-05, BL-FR-06, BL-FR-07, BL-FR-09).** One **Where is this blocker?** picker on
  the Obstacles page, a **Placed on** column, a count chip and banner, a **Move to a step** row action, and a dashboard
  attention item. Older dream-level obstacles are kept as they are until moved.
- **Which obstacle first (BL-FR-10).** Placed obstacles rank ahead of unplaced ones on a tie.
- **Excel (BL-FR-40, BL-FR-41).** Export adds **Placed on** with "Not placed" highlighted. Import now reads the
  Obstacles sheet and reports dream-only rows as "Imported, needs a step (N)" in a new `warnings` list.

### Changed
- `/api/obstacles/priority`: an obstacle on a task or step now holds up only that task or the tasks under that step.
  Before, its derived dream would have counted every Blocked task in the dream.

### Decisions taken during phase 1
- Resolved and Accepted obstacles are not counted as "Not on a step yet" (they need no action).
- Excel import skips Resolved and Accepted obstacle rows with a reason, because the sheet holds no root cause or
  alternatives, which the app requires for those statuses.

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
