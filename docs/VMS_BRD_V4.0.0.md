# Vision Mapping Management System — Business Requirements Document

| | |
|---|---|
| **Document** | VMS_BRD_V4.0.0 |
| **Version** | 4.0.0 (Draft for review — not started) |
| **Date** | 2026-07-25 |
| **Status** | ✅ Original scope complete (2026-07-25). All four now-track requirements shipped — **FR-34** (Energy & Asset Portfolio, Stages A + B), **FR-35** (Cross-Pollination), **FR-36** (PKM Insight Library), and **FR-37** (Life-Optimization Signals). ✅ **FR-38** (In-App Issue & Improvement Reporting) added and shipped 2026-07-25. ✅ **FR-39** (Appearance & Theme Preferences) added and shipped 2026-07-30. ✅ **FR-40** (Background Tone) added and shipped 2026-07-31. ✅ **FR-41** (Categorical pie charts), ✅ **FR-42** (Font Family) and ✅ **FR-43** (Semantic Palette & Grey Ramp) added and shipped 2026-07-31/08-01. The Deferred Platform Track (scheduler / notifications / calendar) remains out of scope by design. Supersedes the earlier V4.0.0 "Life OS Upgrade Roadmap" draft, which described a strategic direction but was not buildable as written (no acceptance criteria, no business rules, no data model, and several features silently assumed a scheduling/notification platform the system does not have). |
| **Baseline** | Builds on VMS_BRD_V3.0.0 (all V1–V3 requirements, FR-1…FR-33, remain in force) |
| **Concept source** | *Mentored by a Millionaire* (Steven K. Scott) — used as conceptual reference only; all product wording, questions, and templates are original |

Requirement numbering continues from V3.0.0 (which ended at FR-33). Business
rules continue from BR-26 (the last rule in V3.0.0 → new rules start at BR-27).
Migrations continue from V12 (→ new migrations start at V13).

---

## Vision — "The Integrated Life"

V1–V2 built the execution structure (*Vision Area → Dream → Goal → Step →
Task*, progress roll-up, boards, reviews, partners, communication). V3 moved
from **tracking** to **activating** — creative persistence on obstacles, and a
vision statement + setup wizard per Vision Area. V4 moves from **activating**
to **integrating**: treating the user's finite time and energy as a portfolio,
and surfacing synergy across life areas rather than managing each in isolation.

| Component | V3.0 focus | V4.0 direction | Strategic basis |
|---|---|---|---|
| Feedback loops | Manual reviews | Reviews **augmented** with computed signals | Continuous improvement |
| Resource allocation | Partner tracking | **Energy/asset portfolio** | Mastering time & energy |
| Life balance | Goal management | **Holistic integration** across areas | Life/work integration |
| Learning system | Persistence logs | **Searchable insight library** | Leveraging knowledge |

**The through-line is Synergy:** ensure daily tasks are not just *done* but
move more than one Vision Area forward at once, and that no area is silently
starved while another is over-fed.

> **Deliberately not in this document.** The earlier draft's "Automated Agenda
> Generation" (pre-populating a daily calendar with prime-time slots) and
> "Predictive Push Alerts" describe a **Chief-of-Staff automation layer** that
> requires infrastructure the system does not have today — a scheduler,
> background jobs, a calendar/time-slot model, and a notification channel.
> Those are captured in [Deferred Platform Track](#deferred-platform-track) with
> their infrastructure prerequisites, and are **out of V4.0.0 scope**. V4.0.0 is
> deliberately the additive, no-new-infrastructure slice of the Life-OS vision.

---

## Origin note

Following the discipline V3.0.0 applied to *its* predecessor draft, every
proposed V4 area was checked against what already ships **before** being scoped.
Two of the four original areas overlap heavily with shipped features, and one
assumes a platform the system does not have. Only the genuinely-new deltas are
scoped below.

| Original V4 draft area | Finding | Disposition |
|---|---|---|
| **Energy & Asset Portfolio** — tag tasks by energy demand, compute a weekly energy budget, force a trade-off when over-committing | **Genuinely new.** No energy concept exists on `TaskItem` today (`estimatedHours`/`actualHours` measure time, not energy). | Scoped as **FR-34**, built first. |
| **Holistic Integration / Cross-Pollination** — map how goals in different areas leverage or impact each other | **Genuinely new.** No goal-to-goal relationship exists; the hierarchy is strictly parent→child. | Scoped as **FR-35**. |
| **PKM / Insight Library** — capture lessons and surface them on similar future obstacles | **Mostly already captured; surfacing is new.** `Review.lessonsLearned` (FR-9) and `Obstacle.rootCause` + `creativeAlternatives` (FR-32) already *store* the insights. What's missing is **search and contextual resurfacing**, not another place to write them. | Scoped narrowly as **FR-36** — read/surface only, no new authoring surface. |
| **Automated Optimization** — replace manual weekly reviews; predictive "you're falling behind" alerts; auto-generate a daily agenda | **Split.** The lightweight half (compute a *starvation signal* per area on request and surface it) is achievable now. The heavy half (scheduled prediction, push notifications, calendar/agenda generation) needs new infrastructure and would *replace* the shipped Review system rather than augment it. | Achievable half scoped as **FR-37** (augments Reviews, never replaces). Heavy half **deferred** — see [Deferred Platform Track](#deferred-platform-track). |

---

## Business Objective

Make the system reason about **capacity and synergy**, not just completion: let
a user see where their limited weekly energy is being spent, discover where one
goal advances another, reuse hard-won lessons instead of relearning them, and
be warned when a whole Vision Area is quietly being neglected — all as additive
metadata and computed views over the existing hierarchy, with no change to how
tasks are executed.

---

## FR-34 Energy & Asset Portfolio *(lead — build first)* — ✅ Done 2026-07-25

Treat energy as a finite asset alongside time. Additive to `TaskItem`; ships in
two stages so the tagging data exists before any logic depends on it.

### Stage A — Energy tagging & weekly budget view *(Effort: S)* — ✅ Done

**Shipped (2026-07-25):** `EnergyDemand { CHARGE, NEUTRAL, DRAIN }` enum + migration
`V13__task_energy_demand.sql` (nullable `energy_demand`; null reads as NEUTRAL, no
backfill). Wired through `TaskItem`, request/response DTOs, mapper, and service —
diagnostic only, never gates saving or status (BR-27). `DashboardService` computes
the Monday–Sunday Energy Budget netting CHARGE/DRAIN (BR-28), returned as an
`EnergyBudget` record; surfaced by an `EnergyBudgetCard` on the dashboard and an
energy selector on both task authoring surfaces. Excel export gained an Energy
column + dropdown; import passes null. Verified: backend 86/86 (new BR-28 test),
frontend tsc/build/35 tests green.

- FR-34.1 **Energy demand tag.** `TaskItem` gains an optional `energyDemand`
  enum: `CHARGE` (energising), `NEUTRAL` (default), `DRAIN` (depleting). It is
  diagnostic metadata only — same posture as the moonshot fields.
- FR-34.2 **Weekly Energy Budget view.** A computed summary of the current
  week's tasks (by due date) that nets `CHARGE` against `DRAIN` into a simple
  balance — e.g. "8 draining vs. 3 charging this week." Surfaced on the
  Dashboard and, scoped, per Vision Area.
- FR-34.3 The tag is editable at any status and never gates saving a task or
  changing its status.

**Acceptance criteria**

1. A task can be created and updated with `energyDemand` unset; it defaults to
   `NEUTRAL` and behaves exactly as today (no regression).
2. The weekly Energy Budget counts only non-archived tasks whose due date falls
   in the current week and reports counts of `CHARGE`/`NEUTRAL`/`DRAIN` plus a
   net balance.
3. Changing a task's `energyDemand` is never rejected on account of status,
   progress, or any other rule.

### Stage B — Constraint / trade-off coaching *(Effort: M)* — ✅ Done

**Shipped (2026-07-25):** After a DRAIN task is saved into a week whose drains
exceed its charges by `DRAIN_WEEK_THRESHOLD` (3), an `EnergyOvercommitDialog`
lists that week's other draining tasks as candidates to drop or move. Pure
threshold logic (`checkDrainOvercommit`) over the same Mon–Sun week as the
backend budget; a shared `useEnergyOvercommitNudge` hook wires it into both the
Tasks Board and the Vision Map tree. Coaching, never validation — the task is
already saved (FR-34.5). Verified: frontend tsc/build green, 43 tests (8 new).

- FR-34.4 **Over-commitment prompt.** When a user adds a new `DRAIN` task to a
  week whose net balance already exceeds a threshold, the system surfaces a
  *coaching* prompt — "This week is already heavily draining. What could you
  drop or move to make room?" — listing that week's other `DRAIN` tasks as
  candidates.
- FR-34.5 The prompt is **coaching, not validation**: it never blocks the save.
  It mirrors the moonshot/creative-persistence posture — the system advises;
  the user decides.

**Acceptance criteria (Stage B)**

1. Adding a `DRAIN` task to an already-over-threshold week shows the prompt;
   the task still saves whether the user acts on it or dismisses it.
2. Below the threshold, no prompt appears.

**Business rules**

| # | Rule |
|---|---|
| BR-27 | `energyDemand` is optional diagnostic metadata and never blocks saving a task or changing its status (defaults to `NEUTRAL`). |
| BR-28 | The weekly Energy Budget aggregates only the authenticated user's non-archived tasks whose due date falls within the current week; `CHARGE` and `DRAIN` net against each other to a balance figure. |

**Data model / migration**

- `V13__task_energy_demand.sql`: add nullable `energy_demand VARCHAR(16)` to
  `task_items` (additive, no backfill — null reads as `NEUTRAL`).
- New enum `EnergyDemand { CHARGE, NEUTRAL, DRAIN }`; `TaskItem`,
  `TaskItemRequest`, `TaskItemResponse`, and the Excel importer/exporter gain
  the field (importer passes null when the column is absent, matching how
  earlier additive fields behaved).

**Design decisions**

- **Enum, not a numeric score.** Three coarse buckets are honest about how
  precisely a user can estimate energy and keep the weekly view legible; a
  0–100 "energy cost" implies a precision the input can't support.
- **Time and energy stay separate.** `estimatedHours` already measures time; a
  long task can be energising and a short one draining, so energy is its own
  axis, not derived from hours.
- **Budget is computed, never stored.** Like progress roll-up, the weekly
  balance is derived on read so it can never drift from the underlying tasks.

---

## FR-35 Holistic Life Integration — Cross-Pollination *(Effort: M)* — ✅ Done 2026-07-25

**Shipped (2026-07-25):** `GoalSynergyLink` entity + migration `V14`. The FKs
cascade on goal delete, a `CHECK` blocks self-links, and a composite `UNIQUE`
enforces pair uniqueness — pairs are normalised in the service (lower goal id
first) rather than via a `LEAST`/`GREATEST` functional index, since H2 (the test
DB) doesn't support expression indexes. `GoalSynergyLinkService` (BR-29):
create/list/delete with guards (no self-link, no duplicate pair, user-scoped);
links are read from either side and the response is perspective-aware, flagging
`crossVisionArea`; links to archived goals drop out of the listing without being
destroyed. Endpoints: `GET/POST /api/goals/{id}/synergy-links`, `DELETE
/api/goals/synergy-links/{linkId}`. Frontend: a `GoalSynergyDialog` from a
"Synergy links" goal row action lists linked goals with a Cross-area chip, an
add form, and per-link remove. The dashboard-level highlight in FR-35.2 was
treated as an optional enhancement (like FR-37.3) and not built into the
area-scoped dashboard aggregation; the cross-pollination view lives on the Goals
page. Verified: backend 96/96 (new `GoalSynergyLinkServiceTest`; V14 runs on H2,
goal permanent-delete still passes), frontend tsc/build/43 tests green.

Let the user record how a goal in one area **leverages or impacts** a goal in
another, so competing priorities can be reframed as synergy.

- FR-35.1 **Goal synergy link.** A user can link two of their own goals with a
  short note describing the relationship (e.g. "the partnership from this career
  goal also advances my leadership-development goal").
- FR-35.2 **Cross-Pollination view.** A read view that lists a goal's synergy
  links and, at the Dashboard level, highlights goals that advance more than one
  Vision Area — the "synergy candidates."
- FR-35.3 Links are informational only; they do not affect progress roll-up,
  status, or archival of either goal.

**Acceptance criteria**

1. A user can create a synergy link between two of their own goals with a note,
   see it on both goals' detail, and remove it.
2. A goal cannot be linked to itself, and the same pair cannot be linked twice
   (the second attempt is rejected with a clear message).
3. A user cannot link to, or see links referencing, another user's goals
   (user-data scoping, consistent with the app-wide isolation rule).
4. Archiving or deleting a goal removes its synergy links (no dangling links).

**Business rules**

| # | Rule |
|---|---|
| BR-29 | A goal synergy link is between two distinct goals owned by the same user; self-links and duplicate pairs are rejected. Links carry no execution semantics (no effect on progress, status, or archival). |

**Data model / migration**

- `V14__goal_synergy_link.sql`: `goal_synergy_links (id, user_id, goal_id,
  related_goal_id, note, created_at, updated_at)`, unique on the
  unordered pair `(goal_id, related_goal_id)` per user, both FKs cascading on
  goal delete.
- New `GoalSynergyLink` entity + request/response DTOs; endpoints under
  `/api/goals/{id}/synergy-links` (list/create) and
  `/api/goals/synergy-links/{linkId}` (delete), following the standard CRUD
  pattern.

**Design decisions**

- **A link table, not a self-referencing FK on `Goal`.** The relationship is
  many-to-many and carries its own note; a join entity is the honest model and
  leaves `Goal` untouched.
- **Stored once, read both ways.** The pair is normalised (unordered) so "A
  leverages B" and "B leverages A" are the same link, surfaced on both goals —
  no duplicate rows, no direction to disagree about.

---

## FR-36 PKM — Insight Library *(surface-only; Effort: S–M)* — ✅ Done 2026-07-25

**Shipped (2026-07-25):** No migration — read-only aggregation over existing
data. `InsightService` + `GET /api/insights?query=` aggregate the user's
`Review.lessonsLearned` and `Obstacle.rootCause`/`creativeAlternatives`, skip
blanks, filter case-insensitively, and sort newest-first (BR-30). Contextual
resurfacing via `ObstacleService.relatedObstacles` + `GET
/api/obstacles/{id}/related` (the user's other resolved obstacles of the same
type). Frontend: an `InsightsPage` (new Insights nav item + `/insights` route)
with debounced search over cards linking back to each source record (FR-36.3),
and a resurfacing `coaching-panel` in the Obstacle form. No new authoring
surface was added — only retrieval, per the Origin note. Verified: backend
91/91 (new `InsightServiceTest` + an `ObstacleServiceTest` case), frontend
tsc/build/43 tests green.

The insights already exist in the data; this makes them **findable and
resurfaced**. No new authoring surface is added.

- FR-36.1 **Insight search.** A searchable, read-only view over the user's own
  captured lessons: `Review.lessonsLearned` (FR-9) and `Obstacle.rootCause` +
  `creativeAlternatives` (FR-32), each shown with its source and date, filtered
  by free-text query.
- FR-36.2 **Contextual resurfacing.** When a user opens or logs an obstacle,
  the system surfaces their past **resolved** obstacles of the same
  `obstacleType`, so a prior root cause and its alternatives are one click away
  instead of a memory exercise.
- FR-36.3 Read-only: the library never creates a new record; it aggregates and
  links back to the source Review or Obstacle for editing.

**Acceptance criteria**

1. Insight search returns matching lessons drawn from the user's own reviews and
   obstacles only, each linking to its source record.
2. Opening an obstacle of type *X* shows the user's previously-resolved
   obstacles of type *X* (if any), with their root cause visible.
3. No path in the Insight Library creates or edits a record; edits happen on the
   source Review/Obstacle as they do today.

**Business rules**

| # | Rule |
|---|---|
| BR-30 | Insight search and resurfacing are scoped strictly to the authenticated user's own Reviews and Obstacles (app-wide user-isolation rule). |

**Data model / migration**

- **No new table.** A read model aggregating existing columns, exposed via
  `GET /api/insights?query=` and `GET /api/obstacles/{id}/related`. An optional
  `V15` index on `obstacles(user_id, obstacle_type, status)` may be added if the
  resurfacing query needs it — decided at build time from the query plan, not
  assumed here.

**Design decisions**

- **Reuse, don't re-author.** The original draft implied a new "Lesson Learned"
  capture step; but FR-9 and FR-32 already capture exactly this. Adding a third
  place to write lessons would fragment them. The gap is retrieval, so only
  retrieval is built.

---

## FR-37 Life-Optimization Signals *(augments Reviews; Effort: M)* — ✅ Done 2026-07-25

**Shipped (2026-07-25):** `DashboardService` computes an area-starvation signal in
`buildAttention` — a Vision Area with an active goal but no task progress in the
last 14 days *while at least one other area moved* in that window (BR-31). No
migration, no background job, no stored flag; it naturally yields nothing in an
area-scoped view. Added `starvedVisionAreas` to the `Attention` DTO; the
`AttentionPanel` surfaces a "Vision Areas being starved" finding linking to the
area's dreams (surface, don't nag — FR-37.2). FR-37.3 (weekly Review referencing
the signals) is the optional "may" clause and was not built. Verified: backend
88/88 (2 new tests), frontend tsc/build/43 tests green.

The achievable half of the original "Automated Optimization" — computed on
request, surfaced in the existing attention feed. It **augments** the Review
system; it does not replace it.

- FR-37.1 **Area-starvation signal.** Flag a Vision Area as *starved* when it
  has at least one active goal but no task progress logged in the last *N* days
  (default 14) **while** at least one other area has progress in that window —
  i.e. neglect relative to the rest of the portfolio, not mere quiet.
- FR-37.2 **Surface, don't nag.** Starvation signals appear in the Dashboard
  attention feed (the same surface FR-25.3 established), linking to the starved
  area's dreams. They are informational; they never change any record's state.
- FR-37.3 The weekly Review template (FR-16) may reference the current signals
  so a review starts from data, but the Review remains fully manual.

**Acceptance criteria**

1. An area with an active goal and no progress in *N* days, while another area
   has progress, is flagged; an area with recent progress, or where *no* area
   has progress, is not.
2. The signal is computed on request from existing `ProgressLog`/task data — no
   background job, no stored "starved" flag.
3. Acting on or ignoring a signal changes no record; it disappears when progress
   is next logged for the area.

**Business rules**

| # | Rule |
|---|---|
| BR-31 | Area-starvation is a computed, read-only signal (active goal + no progress in *N* days while another area has progress). It never mutates any entity and is never persisted as state. |

**Data model / migration**

- **No migration.** Computed in `DashboardService` from existing `ProgressLog`,
  `TaskItem`, `Goal`, and `VisionArea` data; surfaced through the existing
  dashboard/attention payload.

**Design decisions**

- **Relative, not absolute.** "No progress in 14 days" alone would fire during a
  holiday when *nothing* moves. Gating on "while another area *did* move" makes
  it a genuine imbalance signal, matching the doc's "starved of energy while
  others are fed" intent.
- **On-request, not scheduled.** Keeping it a derived dashboard value avoids
  pulling in a scheduler — the thing that pushes the predictive-alert feature
  into the deferred track.

---

## FR-38 In-App Issue & Improvement Reporting *(Effort: S–M)* — ✅ Done 2026-07-25

**Shipped (2026-07-25):** New `issue_reports` table (migration `V15`), entity,
repository, DTOs, `IssueReportService` + `IssueReportController` exposing
`POST /api/issue-reports`, `GET /api/issue-reports` (own), `GET
/api/issue-reports/all` (admin, filterable by type/status/severity), `GET
/api/issue-reports/{id}`, `PATCH /api/issue-reports/{id}/status` (admin), plus
archive/restore/permanent-delete. Role enforcement is in the service — admin
paths throw `AccessDeniedException`, now mapped to **403** by the global handler
(previously any manual denial would have surfaced as 500). BR-32 enforced: a
Bug needs a severity, and a Bug moved to `Resolved` needs a resolution note.
Frontend: a persistent "Report an issue" button in the header opens a modal on
every authenticated page (auto-capturing the current route + app version), a new
**Issue Reports** page lists the user's own reports with a details view, and for
ADMINs a "My reports / All reports" toggle adds a filterable triage queue with a
status-change modal. Verified: backend 106/106 (10 new `IssueReportServiceTest`
cases), frontend tsc/build green, 43 tests pass.

Users who hit a bug, a confusing screen, or think of an improvement while using
the system have **no way to say so from inside the app** today — the only record
of a problem is outside the product (a message to the developer). This FR adds a
lightweight, in-product capture so a user can raise an issue *in the moment*,
tagged with where they were, and an ADMIN can triage the queue. It reuses the
existing `AppUser`/role model (USER, ADMIN) and adds one additive table; it
needs **no** scheduler, email, or notification channel — a report is captured
and listed in-app, staying within the V4.0.0 "no new infrastructure" posture.

- FR-38.1 **Raise a report.** From a persistent "Report an issue" affordance
  (e.g. a header/footer link available on every authenticated page), a user can
  submit a report with: `reportType` (Bug, Improvement, Question, Other), a
  required `title`, a `description`, and — for a Bug — a `severity` (Low,
  Medium, High, Critical). The form is reachable without leaving the current
  page.
- FR-38.2 **Auto-captured context.** On submit, the system records the route the
  user was on (`contextRoute`), the app version (`appVersion`), and the
  timestamp — so a bug is reproducible without the user having to describe where
  they were.
- FR-38.3 **My reports.** A user can view a list of their own submitted reports
  with current status, so they can see a bug was received and track its
  resolution. Reports are never permanently deleted by the user (archive/soft
  behaviour, consistent with the app-wide delete rule).
- FR-38.4 **Admin triage.** An ADMIN can view all users' reports, filter by
  `reportType`/`status`/`severity`, change `status` along the lifecycle, and
  record a `resolutionNote`. Status lifecycle: **Open → In Review → Planned →
  In Progress → Resolved → Closed**, plus a terminal **Won't Fix**.
- FR-38.5 **In-app only (no push).** Notifying a user of a status change relies
  on them viewing "My reports"; email/web-push notification of resolution is
  **explicitly out of scope** and belongs to the
  [Deferred Platform Track](#deferred-platform-track) (needs a notification
  channel).

**Acceptance criteria**

1. From any authenticated page, a user can open the report form, submit a Bug or
   Improvement, and see it appear in "My reports" as **Open**.
2. A submitted report stores the originating route, app version, and creation
   timestamp automatically, without the user entering them.
3. A non-admin user sees only their own reports; an ADMIN sees all reports and
   can change status and add a resolution note.
4. A Bug report requires a `severity`; a Bug moved to **Resolved** requires a
   non-blank `resolutionNote`.
5. No path lets a normal user edit another user's report or view the full queue.

**Business rules**

| # | Rule |
|---|---|
| BR-32 | Issue reports are user-scoped for authoring and self-viewing; only an ADMIN may view or triage the full queue or change a report's `status`/`resolutionNote`. `reportType` and `title` are required; `severity` is required when `reportType = Bug`; moving a Bug to `Resolved` requires a non-blank `resolutionNote`. Reports are archived, not hard-deleted (app-wide soft-delete rule). |

**Data model / migration**

- **New table `issue_reports`** (`V15__issue_reports.sql` — the conditional V15
  obstacle index from FR-36 was never needed, so V15 was free): `id`, `code`
  (`IR-001`), `user_id` (FK → `app_user`), `report_type`, `title`,
  `description`, `severity` (nullable; required only for Bug), `context_route`,
  `app_version`, `status` (default `Open`), `resolution_note` (nullable),
  `archived`, `created_at`, `updated_at`. Exposed via
  `POST /api/issue-reports`, `GET /api/issue-reports` (own for USER, all for
  ADMIN), `GET /api/issue-reports/{id}`, and `PATCH /api/issue-reports/{id}/status`
  (ADMIN-only). Additive, no changes to existing tables.

**Design decisions**

- **Reuse the role model, add one table.** The existing USER/ADMIN roles already
  express the "reporter vs. triager" split, so no new auth concept is needed —
  only endpoint-level authorization and a scoped query, exactly as every other
  user-scoped resource works today.
- **Capture context, don't ask for it.** The most valuable part of a bug report —
  *where* it happened and *which build* — is the part users omit. Recording route
  and app version automatically makes reports actionable without extra friction.
- **No notification, by design.** Telling the user "your bug is fixed" needs a
  delivery channel the system does not have; forcing that in would drag the whole
  Deferred Platform Track into scope. In-app "My reports" is the honest,
  buildable slice.

---

## FR-39 Appearance & Theme Preferences *(Effort: M)* — ✅ Done 2026-07-30

**Shipped (2026-07-30):** Backend — migration `V16` adds seven `NOT NULL`
appearance columns to `app_users` (defaults equal to the pre-FR-39 frontend
defaults, so no backfill), five new enums (`ThemePreset`, `ThemeMode`,
`AccentColor`, `UiDensity`, `FontSize`), `AppearancePreferenceService` +
`AppearancePreferenceController` exposing `GET`/`PUT
/api/preferences/appearance` (user-scoped, partial updates), and the preferences
embedded in `AuthResponse` for a correct first paint. Frontend — accents grown
5 → 10 with contrast-validated light *and* dark ramps, seven named presets with a
derived `CUSTOM` state, `[data-contrast="high"]` token blocks for both modes plus
a thicker focus ring, `[data-motion="reduced"]`, mode-aware high-contrast status
and priority palettes (BR-34), a new `/settings/appearance` page with a live
preview built from the real badge/progress components, an upgraded header menu,
and debounced account sync in `ThemeModeProvider` (localStorage demoted to a
cache). `AuthProvider` now wraps `ThemeModeProvider`, since the theme needs the
session.

Verified: backend 124/124 (8 `AppearancePreferenceServiceTest` + 7
`AppearancePreferenceFlowTests` cases), frontend 107/107 (64 new across
`theme.test.ts`, `appearance-mapping.test.ts`, `ThemeModeContext.test.tsx`,
`AppearanceSettingsPage.test.tsx`), tsc and build clean.

Two defects were found by the new tests and fixed: an unknown enum in a JSON
request body returned 500 instead of 400 app-wide (the query-param equivalent was
already handled, the body case was not), and the appearance loaded from the
account was being echoed straight back to the server on every sign-in. Two items
were deliberately left alone and recorded in the CHANGELOG rather than changed as
a side effect: protected endpoints answer 403 rather than 401 for anonymous
callers (pre-existing, app-wide), and the five original accents still fall below
4.5:1 on their hover/pressed states (a recolour this FR does not claim).

Not built as originally written: presets carry **no surface-tone dimension**. It
was the obvious way to make them feel distinct and it is precisely the hidden
dimension FR-39.1 forbids — a look unreachable through the normal controls is one
the user cannot adjust or undo. A `FLUENT_SYSTEM` preset was added instead so a
fresh account shows a real preset name rather than "Custom".

The app already has an Appearance menu (FR-18: mode, accent, density, text size),
but it is **five accents wide, browser-bound, and has no accessibility controls**.
Three gaps follow from that. A user who signs in on a second machine starts over
from the defaults, because every choice lives in one browser's `localStorage`. A
user who needs stronger contrast or less motion has no in-app control — the app
honours the OS `prefers-reduced-motion` setting but offers no override of its own.
And setting up a look means four separate menu picks with no way to preview the
result. This FR closes all three: named theme presets, a wider accent set,
accessibility toggles, a dedicated settings surface with live preview, and
per-account persistence so appearance follows the user rather than the browser.

It is additive and needs **no new infrastructure** — one additive migration on
`app_users`, one user-scoped endpoint pair, and frontend work inside the existing
theme layer, keeping the V4.0.0 posture.

- FR-39.1 **Named theme presets.** A preset applies mode + accent together in one
  action, so a complete look is one click rather than several menu picks. Presets
  are an honest bundle of the two existing knobs — never a hidden extra
  dimension, and specifically **no separate "surface tone"**, which would have
  been exactly the hidden dimension this rule forbids. Picking a preset and then
  adjusting any individual control leaves the app in a **Custom** state rather
  than silently mislabelling it as the preset. Initial set: **Fluent System**
  (the default — System mode + Blue), **Fluent Light**, **Fluent Dark**,
  **Ocean**, **Forest**, **Slate**, **Midnight**.
- FR-39.2 **Wider accent set.** The curated accent list grows from 5 to 10 —
  adding **Magenta**, **Red**, **Brass**, **Steel**, and **Pink** alongside the
  existing Blue, Teal, Purple, Green, Orange. Every new accent ships
  pre-validated light *and* dark ramps (`main`/`hover`/`pressed`/`contrastText`
  plus the tint pair), exactly like the current five: contrast is never left to
  the user's judgment.
- FR-39.3 **High contrast mode.** An independent toggle — not a preset — that
  raises text and border contrast to a 7:1 target and strengthens focus rings.
  It composes with Light and Dark rather than replacing them (making it a preset
  would double every preset in the list).
- FR-39.4 **Reduce motion.** An in-app toggle that suppresses transitions and
  animations regardless of the OS setting. The OS `prefers-reduced-motion`
  preference continues to apply on its own (BR-19); this control lets a user opt
  in without changing an OS-wide setting, and cannot be used to *re-enable*
  motion for a user whose OS asked for less.
- FR-39.5 **Appearance settings page.** A dedicated page (`/settings/appearance`)
  exposes every control with a **live preview panel** — cards, a table row,
  status and priority badges, buttons, and a progress bar — so a choice can be
  judged on real components before it is committed. The header menu remains as
  the quick-access path and links through to the page.
- FR-39.6 **Per-account persistence.** Appearance choices are stored on the
  user's account and follow them across browsers and devices. Preferences are
  returned with the login/register response so the correct theme is applied on
  first paint, with no flash of the wrong theme and no second round trip.
  `localStorage` is retained as a cache — for the pre-login screens, for
  anonymous visitors, and as the offline fallback if the save request fails.

**Acceptance criteria**

1. Selecting a preset changes mode, accent, and surface tone together in one
   action; subsequently changing any single control shows the state as
   **Custom**, not as the preset.
2. All 10 accents are selectable and each renders legibly in both Light and Dark
   — no accent produces primary text or a primary button below its contrast
   target in either mode.
3. High contrast can be enabled in combination with Light **or** Dark, and
   raises contrast in both; disabling it returns to the previous appearance
   exactly.
4. With Reduce motion enabled, transitions and animations do not play. A user
   whose OS requests reduced motion still gets reduced motion with the toggle
   off.
5. Appearance changes persist across logout/login **and** across browsers for
   the same account. A user signing in on a fresh browser sees their saved
   appearance applied on first paint, not the defaults followed by a switch.
6. The settings page's preview reflects the current selection live, before and
   without a page reload.
7. One user's appearance preferences are never readable or writable by another
   user.
8. Existing users keep working with no migration action: absent preferences
   resolve to today's defaults (System mode, Blue, Comfortable, Medium, high
   contrast off, reduce motion off).

**Business rules**

| # | Rule |
|---|---|
| BR-33 | Appearance preferences are strictly user-scoped: a user may read and write only their own. Every stored value must be one of the curated enum options (preset, mode, accent, density, font size) — an unrecognised value resolves to the default rather than being persisted or rendered. A failed save never blocks the UI: the choice applies locally and is retried, so appearance is never a hard dependency on the backend. |
| BR-34 | High contrast may adjust the **lightness** of a status or priority colour to meet its contrast target, but never its **hue or meaning** — Completed stays green, Blocked stays orange, Critical stays red. This is a bounded, documented exception to BR-14 (accent choice must not change palette semantics): the accent still cannot touch these palettes; only the accessibility mode may, and only along lightness. |

**Data model / migration**

- **Additive columns on `app_users`** (`V16__user_appearance_preferences.sql`):
  `theme_preset`, `theme_mode`, `accent`, `density`, `font_size`,
  `high_contrast`, `reduce_motion` — all `NOT NULL` with defaults equal to
  today's frontend defaults, so existing rows need no backfill and no null
  handling. Exposed via `GET /api/preferences/appearance` and
  `PUT /api/preferences/appearance` (both user-scoped), and included in the
  `AuthResponse` returned by register/login. New enums: `ThemePreset`,
  `ThemeMode`, `AccentColor`, `Density`, `FontSize`.
- *Alternative considered:* a separate `user_preferences` table. Rejected for
  now — a single row per user holding only appearance data would add an entity,
  repository, and join for no present benefit, and additive columns match the
  precedent of `V11` and `V13`. If preferences grow beyond appearance
  (notifications, locale, default filters), extracting the table is a
  straightforward later migration.

**Design decisions**

- **Presets bundle knobs; they are not a new dimension.** A preset writes the
  same `mode`/`accent`/surface values a user could set by hand. That keeps one
  source of truth for what is actually rendered and makes the **Custom** state
  fall out naturally, instead of maintaining a parallel "preset theme" code path
  that could disagree with the individual controls.
- **High contrast and reduce motion are toggles, not presets.** Both are
  accessibility needs that must hold across *whatever* look the user chose.
  Folding them into the preset list would multiply the list and force a user who
  needs contrast to also accept someone else's colour choice.
- **Preferences ride the auth response.** Fetching them separately after login
  would paint the default theme first and swap it a moment later — the most
  visible possible bug in a theming feature. Returning them with the session
  makes the first paint correct.
- **`localStorage` stays.** The login and register screens render before any
  session exists, and the save endpoint can fail. Keeping the browser cache as
  the fallback layer means appearance degrades to "this browser only" rather than
  to "reset to defaults".
- **Reduce motion is one-way.** The toggle can only ask for *less* motion than
  the OS preference, never more. Letting an in-app switch override a stated OS
  accessibility preference would be a regression against BR-19.

---

## FR-40 Background Tone *(Effort: S–M)* — ✅ Done 2026-07-31

**Shipped (2026-07-31):** Backend — `V17` adds one `background_tone` column to
`app_users` (`NOT NULL DEFAULT 'NEUTRAL'`), a `BackgroundTone` enum, and the
field on the existing appearance DTOs; the FR-39.6 partial-update contract meant
no new endpoint and no controller change. Frontend — six tones as coordinated
surface sets in `global.css` and `theme.ts`, `Tinted` derived from the accent via
`color-mix` so it stores nothing, `Flat` compensating for its missing canvas step
with a stronger border, plus controls in the header menu and on the settings
page (disabled with a stated reason while high contrast is on).

Verified: backend 126/126, frontend 127/127, tsc and build clean. Every tone's
contrast was measured before the values were committed and is asserted in
`theme.test.ts`, so a tone added later without checking fails the build.

One implementation note worth recording: FR-40.5 is enforced twice, in the MUI
theme and in the stylesheet, because both paint surfaces. In CSS it depends on
selector ordering — the tone blocks sit above the high-contrast blocks and every
selector in both groups carries a `:root` prefix so ties break by source order.
That ordering is load-bearing and commented as such; moving either group would
let a colour preference silently outrank the accessibility mode.

FR-39 gave the user control of the *accent* — the colour the app uses to point at
things — but the surfaces underneath it are still fixed. Light mode is a white
canvas and dark mode a near-black one, and neither can be adjusted. For a user
who works in the app for hours, the canvas is the largest coloured area on
screen and the one most worth being able to soften: a slightly warm page reads
very differently from a stark white one, and some users want more separation
between the page and the cards on it than the current one-step `#fafafa` gives.

This FR adds a **background tone**: a coordinated set of surface values applied
across the page canvas, cards, popovers, and sidebar. It is additive — one
column, one enum, and one more control on a settings page that already exists.

- FR-40.1 **Curated tones, not a colour picker.** Six named tones per mode, each
  shipping pre-validated values, exactly as accents do (FR-39.2). A free-form
  colour field was considered and rejected: the guarantee that no appearance
  choice can make text unreadable is the most valuable property of this whole
  feature area, and an arbitrary hex turns that guarantee into a runtime check
  the user can fail. Initial set: **Neutral** (today's values, the default),
  **Warm**, **Cool**, **Soft** (a greyer canvas, more page-to-card separation),
  **Tinted** (derived from the chosen accent), **Flat** (no canvas step — borders
  carry all separation).
- FR-40.2 **A tone is a set, not a single colour.** Choosing one moves `--page`,
  `--card`, `--background`, `--popover`, `--sidebar`, and `--background-subtle`
  together, so the depth relationship between surfaces is preserved rather than
  one layer drifting away from the others.
- FR-40.3 **Tinted derives from the accent.** The Tinted tone mixes the active
  accent into the surface at a low percentage rather than storing its own values.
  Ten accents across two modes would otherwise mean twenty more hand-validated
  colours; deriving it keeps the count at zero and means a future accent works
  automatically.
- FR-40.4 **An independent axis, not part of a preset.** Tone sits alongside
  density, text size, and the accessibility toggles — controls a preset does not
  set either. Presets stay (mode, accent) pairs. This keeps every existing
  preset label valid instead of pushing current users to **Custom**, and because
  tone is a visible control it is not the hidden dimension FR-39.1 rules out.
- FR-40.5 **High contrast overrides it, visibly.** When high contrast is on, the
  surfaces go to pure white or pure black regardless of tone, because maximum
  separation is the entire point of that mode. The tone control is **disabled
  with a stated reason** rather than left enabled and quietly ignored — a control
  that appears to accept input it discards is worse than one that explains
  itself.
- FR-40.6 **Persisted with the rest of the appearance.** Stored on the account
  and returned with the auth response, travelling the same path FR-39.6 built.

**Acceptance criteria**

1. Selecting a tone changes the page, card, popover, and sidebar surfaces
   together, and the change is visible immediately without a reload.
2. Every tone keeps body text at ≥ 7:1 and secondary text at ≥ 4.5:1 against the
   surface it actually renders on, in both light and dark mode.
3. The Neutral tone contributes no values of its own and defers entirely to the
   base tokens. *(Originally worded as "produces exactly the values shipped
   before FR-40". That was superseded deliberately: the light canvas rendered
   white while `--page` claimed `#fafafa`, and holding Neutral byte-identical
   would have meant preserving that bug. The canvas now sits at grey-100 — see
   the CHANGELOG entry for the correction.)*
4. The Tinted tone visibly follows the accent: changing accent changes the
   canvas, with no per-accent values stored.
5. With high contrast on, surfaces are pure white/black whatever the tone, and
   the tone control is disabled with a visible explanation. Turning high contrast
   off restores the chosen tone exactly.
6. Tone persists across logout/login and across browsers for the same account,
   and is applied on first paint.
7. Existing users default to `NEUTRAL` with no migration action.
8. Tones that reduce the page-to-card difference (Flat) keep cards
   distinguishable through borders, so no surface boundary is lost.

**Business rules**

| # | Rule |
|---|---|
| BR-35 | A background tone may only be one of the curated options, and every option must hold body text at ≥ 7:1 and secondary text at ≥ 4.5:1 against the surface it renders on, in both modes. High contrast overrides the tone entirely (pure white/black surfaces) and the tone control is disabled while it is active — the accessibility mode always outranks an aesthetic preference. |

**Data model / migration**

- **Additive column on `app_users`** (`V17__user_background_tone.sql`):
  `background_tone VARCHAR(20) NOT NULL DEFAULT 'NEUTRAL'`. New enum
  `BackgroundTone` (`NEUTRAL`, `WARM`, `COOL`, `SOFT`, `TINTED`, `FLAT`). Carried
  on the existing `GET`/`PUT /api/preferences/appearance` payload and in
  `AuthResponse` — the partial-update contract from FR-39.6 means no new endpoint
  and no controller change.

**Design decisions**

- **Curated beats configurable here.** The instinct is that more freedom is
  better, but a background is not a decoration: it is what every piece of text in
  the product sits on. A picker would move the app from "no choice can break
  readability" to "most choices are fine" — a strictly weaker promise, and the
  one users cannot verify for themselves.
- **Derive Tinted rather than enumerate it.** Storing accent × mode surface
  values would add twenty values to keep in step with any future accent. Mixing
  at render time reuses the mechanism `TintedChip` already relies on.
- **Disable, don't ignore.** Leaving the tone control live under high contrast
  would let a user pick Warm, see nothing change, and reasonably conclude the
  feature is broken. Disabling it with a reason costs one sentence of UI and
  removes that whole class of confusion.
- **Keep it off the presets.** Adding tone to the preset definition would
  reclassify existing users' saved appearance as Custom the moment they upgrade,
  for no benefit. Density and text size already sit outside presets; tone
  follows the established line.

---

## FR-42 Font Family *(Effort: S–M)* — ✅ Done 2026-08-01

**Shipped (2026-08-01):** `V18` adds `font_family` to `app_users` (`NOT NULL
DEFAULT 'SYSTEM'`), with a `FontFamily` enum carried on the existing appearance
endpoint. Frontend adds a font registry, a lazy loader, and controls in both the
header menu and the settings page — each option rendered in its own face, so the
list previews itself. Verified: backend 127/127, frontend 177/177.

The app renders in the platform's own UI face and offers no choice. This FR adds
four typefaces alongside it.

- FR-42.1 **The system face stays the default.** Segoe UI Variable / SF Pro /
  Roboto, unchanged. It renders instantly and downloads nothing, so it remains
  what a user gets unless they ask for something else.
- FR-42.2 **Self-hosted, fetched on demand.** The four faces (Public Sans, Inter,
  DM Sans, Nunito Sans) ship with the app rather than coming from a font CDN, and
  each is imported only when a user selects it. A CDN link was rejected: it would
  be the app's first third-party request, would send every user's IP to a font
  host, and would break offline and on restricted networks.
- FR-42.3 **Every stack falls back to the system face.** A font that fails to
  arrive degrades to the default rather than to an unstyled serif, and a failed
  load never surfaces as an error.
- FR-42.4 **One shared size ramp.** All faces use the existing six-size ramp
  (FR-20.1); no per-font size correction.
- FR-42.5 **Persisted with the rest of the appearance**, via FR-39.6's path.

**Acceptance criteria**

1. A user on the default downloads no font file and issues no font request.
2. Selecting a font applies it to both MUI components and plain elements.
3. A font is fetched at most once, however often it is selected.
4. Existing users default to `SYSTEM` with no migration action and no visible change.
5. No request leaves the app's own origin for a font.

**Business rules**

| # | Rule |
|---|---|
| BR-36 | Font files are served from the application's own origin; no third-party font host may be contacted. A font is loaded only once selected, and a failed load falls back to the system face rather than erroring. |

**Data model / migration**

- `V18__user_font_family.sql`: `font_family VARCHAR(20) NOT NULL DEFAULT 'SYSTEM'`
  on `app_users`. Enum `FontFamily` (`SYSTEM`, `PUBLIC_SANS`, `INTER`, `DM_SANS`,
  `NUNITO_SANS`), carried on the existing appearance payload.

**Design decisions**

- **Self-hosting over a CDN.** The convenience of a `<link>` is real, but it
  trades away offline support, adds a third-party dependency to an app that had
  none, and hands user IPs to a font host. Verified in the build: the entry CSS
  contains zero `@font-face` rules and the main bundle did not grow.
- **The default must cost nothing.** Bundling all four upfront would make every
  user pay for typefaces most will never choose. A test asserts the system
  default triggers no import, because that is the property the whole arrangement
  exists for.

---

## FR-43 Semantic Palette & Grey Ramp *(Effort: M)* — ✅ Done 2026-08-01

**Shipped (2026-08-01):** A five-shade semantic palette (primary, secondary,
info, success, warning, error) and a ten-step grey ramp added to `theme.ts`, with
the grey ramp driving the neutral tokens and the semantic families driving the
tile tints and banners. The supplied primary and secondary ramps are offered as
two new accents — Vermilion and Violet — taking the accent count to twelve.
Verified: frontend 177/177.

The app's neutrals were individually-chosen Fluent values that happened to sit
near each other, and its semantic tints paired a mid-weight hue with a pale wash.
This FR replaces both with structured ramps.

- FR-43.1 **Shade roles are fixed, not interchangeable.** `lighter` is a tint
  background, `main` an icon or fill colour, `dark`/`darker` the text. This is
  the crux: measured against white text the mains land between 1.90:1 (warning)
  and 3.67:1 (primary), so `main` cannot be a button colour.
- FR-43.2 **The grey ramp splits at 600.** 50–500 are surfaces, borders and
  dividers; 600–900 carry text. `grey-600` measures 4.88:1 on white, exactly a
  secondary-text colour.
- FR-43.3 **Semantic tints use the lighter/darker pair.** Every pair clears
  6.9:1, against roughly 4.1:1 for the values they replace.
- FR-43.4 **The supplied red is not the default primary.** It is offered as the
  Vermilion accent. The app already spends red on Critical, Declined, and
  destructive actions, and a brand red beside a danger red on the same screen is
  a hazard whatever the measured separation says.
- FR-43.5 **Page and card surfaces are left to FR-40.** The grey ramp has
  tempting surface values, but those tokens are governed by the background tone;
  repointing them would break FR-40's guarantee that Neutral changes nothing.

**Acceptance criteria**

1. Every semantic family exposes all five shades, and `lighter` paired with
   `darker` clears 4.5:1 in each.
2. The grey ramp is monotonic, and the 600 split holds: `grey-600` clears 4.5:1
   on white, `grey-500` does not.
3. The default accent remains blue; the supplied red is reachable only by
   choosing it.
4. Both new accents satisfy the existing accent contract in both modes.

**Business rules**

| # | Rule |
|---|---|
| BR-37 | A semantic shade may only be used in its documented role — `lighter` as a surface, `main` as a fill or icon, `dark`/`darker` as text on that surface. `main` is never a text or button background colour; measured against white it falls between 1.90:1 and 3.67:1. |

**Design decisions**

- **Roles written down and tested.** A test asserts that `success.main`,
  `warning.main`, and `info.main` are *illegible* on white, so a later change
  cannot quietly promote them to button colours.
- **Brand red kept off the default path.** Offered, not imposed (FR-43.4).
- **`--palette-*` CSS variables, guarded rather than omitted.** The supplied
  tables name CSS variables. These were initially left out because two
  hand-maintained copies of the same colours is the drift BR-15 exists to
  prevent — then added once the drift was made impossible: a test parses
  `global.css` and compares every `--palette-*` declaration against `theme.ts` in
  both directions. The rule that matters is not "never duplicate" but "never let
  two copies disagree", and a test enforces that better than an absence does.

---

## Deferred Platform Track

Captured so the vision stays whole, but **explicitly out of V4.0.0 scope** — each
needs infrastructure the system does not have. These belong in a later BRD
(V4.x or V5) that opens with the non-functional groundwork.

| Deferred feature | Why deferred | Infrastructure prerequisite |
|---|---|---|
| **Automated Agenda Generation** (pre-populate a daily schedule; reserve prime-time slots for moonshots) | The app has no calendar, time-slot, or scheduling model at all. | A calendar/time-slot subsystem + a scheduling engine. |
| **Predictive push alerts** ("you'll fall behind before it happens") | Requires work to run without a user request and a channel to reach the user. | Background jobs (e.g. Quartz/`@Scheduled`) + a notification channel (email/web-push). |
| **Fully-automated review** (replace manual weekly reviews) | Would remove a shipped, central feature (FR-9/FR-16). V4 augments it via FR-37 instead. | Depends on both of the above being trustworthy first. |

**Non-functional groundwork these will require (not in V4.0.0):** a job
scheduler and idempotent background execution; a persisted notification model
and delivery channel; a calendar/time-slot data model; and a re-examination of
Redis caching under write-heavy scheduled jobs. None of this is present today,
which is precisely why it is deferred rather than scoped.

---

## Build Order

| Order | Work | Why this order | Effort | Status |
|---|---|---|---|---|
| 1 | FR-34 Stage A (energy tagging + budget view) | Smallest, fully additive, backend-first; the doc's own recommended starting point — establishes the energy data everything else can build on | S | ✅ Done |
| 2 | FR-37 Life-Optimization Signals | Reuses the existing dashboard/attention surface; no schema change; delivers a visible "balance" win early | M | ✅ Done |
| 3 | FR-36 Insight Library | Read-only over data that already exists; no new authoring surface | S–M | ✅ Done |
| 4 | FR-35 Cross-Pollination | One new table + CRUD; independent of the above | M | ✅ Done |
| 5 | FR-34 Stage B (constraint coaching) | Depends on Stage A's data and benefits from the balance view landing first | M | ✅ Done |
| 6 | FR-38 In-App Issue & Improvement Reporting | Added post-completion; one additive table + role-scoped CRUD, no new infrastructure | S–M | ✅ Done |
| 7 | FR-39 Appearance & Theme Preferences | Added post-completion; extends the existing FR-18 theme layer, one additive migration, no new infrastructure | M | ✅ Done |
| 8 | FR-40 Background Tone | Depends on FR-39's surface tokens, settings page, and persistence path already being in place | S–M | ✅ Done |
| 9 | FR-41 Categorical Pie Charts | Restyles the two non-semantic dashboard charts; no schema change | S | ✅ Done |
| 10 | FR-42 Font Family | One additive migration, reuses the FR-39.6 persistence path | S–M | ✅ Done |
| 11 | FR-43 Semantic Palette & Grey Ramp | Frontend-only; replaces ad-hoc neutrals and tints with structured ramps | M | ✅ Done |
| — | Deferred Platform Track | Separate future BRD; needs NFR groundwork first | XL | Deferred |

---

## Business Rules (new in V4.0.0)

| # | Rule |
|---|---|
| BR-27 | `energyDemand` is optional diagnostic metadata and never blocks saving a task or changing its status (defaults to `NEUTRAL`). |
| BR-28 | The weekly Energy Budget aggregates only the authenticated user's non-archived tasks due within the current week; `CHARGE` and `DRAIN` net to a balance. |
| BR-29 | A goal synergy link joins two distinct goals of the same user; self-links and duplicate pairs are rejected; links carry no execution semantics. |
| BR-30 | Insight search and resurfacing are scoped strictly to the authenticated user's own Reviews and Obstacles. |
| BR-31 | Area-starvation is a computed, read-only signal; it never mutates or persists any entity state. |
| BR-32 | Issue reports are user-scoped for authoring/self-viewing; only ADMIN may triage the full queue or change status. `reportType`+`title` required; `severity` required for Bug; a Bug moved to `Resolved` requires a `resolutionNote`. Reports are archived, not hard-deleted. |
| BR-33 | Appearance preferences are strictly user-scoped (read and write own only). Stored values must be curated enum options; an unrecognised value resolves to the default rather than being persisted. A failed save applies locally and retries — appearance never hard-depends on the backend. |
| BR-34 | High contrast may adjust the lightness of a status/priority colour to meet its contrast target, never its hue or meaning. A bounded, documented exception to BR-14: accent choice still cannot touch these palettes; only the accessibility mode may, and only along lightness. |
| BR-36 | Font files are served from the application's own origin; no third-party font host may be contacted. A font loads only once selected, and a failed load falls back to the system face. |
| BR-37 | A semantic shade may only be used in its documented role — `lighter` as a surface, `main` as a fill or icon, `dark`/`darker` as text. `main` is never a text or button background colour. |
| BR-35 | A background tone must be one of the curated options, and every option holds body text at ≥ 7:1 and secondary text at ≥ 4.5:1 against the surface it renders on, in both modes. High contrast overrides the tone entirely and disables its control — the accessibility mode outranks an aesthetic preference. |

## Migrations (new in V4.0.0)

| Migration | Purpose | Type |
|---|---|---|
| `V13__task_energy_demand.sql` | `energy_demand` on `task_items` | Additive, nullable |
| `V14__goal_synergy_link.sql` | `goal_synergy_links` table | New table |
| `V15__issue_reports.sql` *(FR-38)* | `issue_reports` table for in-app bug/improvement reporting | New table |
| `V16__user_appearance_preferences.sql` *(FR-39)* | Appearance columns on `app_users` (`theme_preset`, `theme_mode`, `accent`, `density`, `font_size`, `high_contrast`, `reduce_motion`) | Additive, `NOT NULL` with defaults |
| `V17__user_background_tone.sql` *(FR-40)* | `background_tone` on `app_users` | Additive, `NOT NULL` default `NEUTRAL` |
| `V18__user_font_family.sql` *(FR-42)* | `font_family` on `app_users` | Additive, `NOT NULL` default `SYSTEM` |

## Non-Functional Notes

- V4.0.0 (FR-34…FR-43) is **additive**: six additive migrations (V13, V14, V15,
  V16, V17, V18), no destructive schema changes, and **no new infrastructure** — the
  same posture V3.0.0 could truthfully claim. (The originally-optional V15
  obstacle index was never needed; V15 became the FR-38 `issue_reports` table.)
- New business rules (BR-27…BR-31) get backend test coverage where they carry
  logic (BR-28 aggregation, BR-29 link constraints, BR-31 signal computation),
  matching V1–V3 practice. BR-27/BR-30 are scoping/posture rules verified by
  existing isolation and optional-field tests. BR-33 is covered by a preference
  service test (defaults, enum validation, cross-user scoping); BR-34 is a
  palette rule verified in the frontend theme tests. BR-35 is likewise a palette
  rule: each tone's contrast ratios are asserted in the theme tests, so a tone
  added later without checking fails the build rather than shipping.
- FR-39 and FR-40 are the V4.0.0 requirements with an **accessibility**
  dimension (high contrast, reduce motion, and the surfaces text renders on).
  Their contrast targets are fixed values in the theme layer, not runtime
  computation, so the guarantee is reviewable in source rather than dependent on
  user choices. FR-40 keeps it that way deliberately by curating tones instead of
  offering a colour picker.
- Everything net-requiring a scheduler, background execution, a notification
  channel, or a calendar model is confined to the [Deferred Platform
  Track](#deferred-platform-track) and is **not** part of V4.0.0.
- Versioning: per the CHANGELOG convention (system major = BRD major), shipping
  V4.0.0 requirements moves the app to `4.0.0`. Until the first FR ships, this
  BRD is *Draft — not started* and the version does not move.
