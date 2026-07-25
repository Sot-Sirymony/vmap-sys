# Vision Mapping Management System — Business Requirements Document

| | |
|---|---|
| **Document** | VMS_BRD_V4.0.0 |
| **Version** | 4.0.0 (Draft for review — not started) |
| **Date** | 2026-07-25 |
| **Status** | In progress. **FR-34** (Energy & Asset Portfolio, Stages A + B) and **FR-37** (Life-Optimization Signals) are ✅ shipped (2026-07-25); **FR-35** and **FR-36** remain. Supersedes the earlier V4.0.0 "Life OS Upgrade Roadmap" draft, which described a strategic direction but was not buildable as written (no acceptance criteria, no business rules, no data model, and several features silently assumed a scheduling/notification platform the system does not have). |
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

## FR-35 Holistic Life Integration — Cross-Pollination *(Effort: M)*

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

## FR-36 PKM — Insight Library *(surface-only; Effort: S–M)*

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
| 3 | FR-36 Insight Library | Read-only over data that already exists; no new authoring surface | S–M | ⬜ Next |
| 4 | FR-35 Cross-Pollination | One new table + CRUD; independent of the above | M | ⬜ Pending |
| 5 | FR-34 Stage B (constraint coaching) | Depends on Stage A's data and benefits from the balance view landing first | M | ✅ Done |
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

## Migrations (new in V4.0.0)

| Migration | Purpose | Type |
|---|---|---|
| `V13__task_energy_demand.sql` | `energy_demand` on `task_items` | Additive, nullable |
| `V14__goal_synergy_link.sql` | `goal_synergy_links` table | New table |
| `V15` *(conditional)* | Index for obstacle resurfacing, only if the query plan needs it | Additive index |

## Non-Functional Notes

- V4.0.0 (FR-34…FR-37) is **additive**: two additive migrations (V13, V14),
  one optional index (V15), no destructive schema changes, and **no new
  infrastructure** — the same posture V3.0.0 could truthfully claim.
- New business rules (BR-27…BR-31) get backend test coverage where they carry
  logic (BR-28 aggregation, BR-29 link constraints, BR-31 signal computation),
  matching V1–V3 practice. BR-27/BR-30 are scoping/posture rules verified by
  existing isolation and optional-field tests.
- Everything net-requiring a scheduler, background execution, a notification
  channel, or a calendar model is confined to the [Deferred Platform
  Track](#deferred-platform-track) and is **not** part of V4.0.0.
- Versioning: per the CHANGELOG convention (system major = BRD major), shipping
  V4.0.0 requirements moves the app to `4.0.0`. Until the first FR ships, this
  BRD is *Draft — not started* and the version does not move.
