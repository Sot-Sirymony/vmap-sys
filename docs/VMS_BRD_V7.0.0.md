# Vision Mapping Management System — Business Requirements Document

| | |
|---|---|
| **Document** | VMS_BRD_V7.0.0 |
| **Version** | 7.0.0 (In progress) |
| **Date** | 2026-09-14 |
| **Status** | 🔶 In progress. Written from a gap analysis comparing V1.0.0 → V6.0.0 against Chapters 7–15 of *The Richest Man Who Ever Lived* (character, conflict, criticism, prudence, and wisdom). ✅ **FR-61** (Expectation Diagnosis) shipped 2026-09-14. FR-59, FR-60, FR-62, FR-63 remain proposed — not yet built. |
| **Baseline** | Builds on VMS_BRD_V6.0.0 (all FR-1…FR-58 remain in force) |
| **Concept source** | *The Richest Man Who Ever Lived* (Steven K. Scott), used as conceptual reference only, as in V5–V6. **No copyrighted text, named proprietary frameworks, or scripture is reproduced anywhere in this document or in the product** — see *Originality note* below for what that changed. |

New requirements start at **FR-59**. Business rules continue from **BR-47**
(→ new rules start at **BR-48**). Migrations continue from **V30** (→ new
migrations start at **V31**, assigned in this document's own build order —
see the usual caveat in *Numbering correction* below about reassignment at
actual build time).

---

## Numbering correction

Unlike V5.0.0, no renumbering surprises were found here — the codebase's
true state (FR-58, BR-47, V30 all shipped per V6.0.0) matches what this
document assumes as its starting point. The migration numbers below (V31–
V35) are assigned in this document's own proposed build order; if FRs ship
in a different order, later migrations may claim earlier numbers, the same
caveat V5.0.0 and V6.0.0 both carried before their own numbers were fixed by
actual build order.

---

## Originality note

The source gap analysis quoted a specific Bible verse twice (Proverbs 27:5,
Proverbs 16:18), named a specific author's "Rules of Engagement" and
"Emptying the Anger Cup" framework by that author's name, and used a
book-specific three-part metaphor ("Water," "Sand," "Gold") for triaging
criticism. Per this project's standing rule (`CLAUDE.md`), this document:

- Drops both scripture citations entirely — the underlying ideas (candor
  matters more than comfortable silence; unexamined pride corrodes success)
  are described in this document's own words, with no verse reference, the
  same way V5.0.0 dropped its own source citation.
- Describes the incoming-criticism triage mechanism without the book's
  "Water / Sand / Gold" labels, using three original original terms instead:
  **Overstated** (exaggeration, generalization, hyperbole), **Delivery**
  (tone, timing, or harshness — real but not the message), and **Substance**
  (the actionable truth worth keeping). The underlying three-way split is
  the same; the labels are this project's own.
- Describes the conflict checklist by its underlying mechanism (a short
  list of conduct checks before escalating a disagreement) without naming
  the book's specific named author or framework, and condenses the source's
  illustrative "10 Don'ts / 8 Dos" down to four original, non-overlapping
  checks — matching how FR-54 already built an original six-prompt
  worksheet instead of quoting the book's named "Anger Cup" steps verbatim.
- Names the new "Wisdom Account" concept generically (**Principle
  Repository**) rather than using the book's own chapter title as a product
  feature name.

---

## Origin note

| Gap area (chapter) | Current VMS state | Finding |
|---|---|---|
| Gratitude / happiness discipline (Ch. 7) | Missing entirely — Reviews (FR-9, FR-16, FR-53) score work execution only; nothing captures satisfaction, gratitude, or non-work life assets. | **Genuinely new.** Scoped as **FR-59**. |
| Character-trait self-assessment (Ch. 8) | Partial — diligence is scored (FR-16/FR-53) and partner honesty is screened (FR-50), but there is no self-facing kindness/generosity tracking. | **Acknowledged, not addressed this round** — see *Scope note* below. |
| Conflict conduct checklist (Ch. 9) | Partial — FR-54 gives a conflict-processing worksheet, but nothing checks *how* a confrontation is conducted, only that it was processed. | **Genuinely new**, additive to FR-54. Scoped as **FR-62**. |
| Incoming criticism triage (Ch. 10) | Partial — FR-54.3 helps compose outgoing constructive feedback; nothing helps process *incoming* criticism before it lands as discouragement. | **Genuinely new**, additive to FR-54. Scoped as **FR-60**. |
| Anger root-cause / expectation diagnosis (Ch. 11) | Partial — FR-54 captures the incident, its cost, and a lesson, but never asks what specific expectation was actually violated. | **Genuinely new**, additive to FR-54. Scoped as **FR-61**. |
| Due diligence scope (Ch. 12) | Partial — FR-55 already gates High/Critical Moonshot Dreams specifically. | **No new work** — FR-55's scope (Moonshots only) was a deliberate V5.0.0 design decision, not an oversight; widening it to every strategic decision is a materially larger feature than this round's other items and is not proposed here. |
| Personal financial prudence (Ch. 13) | Missing — no debt/pace-of-spending checks exist anywhere in the system. | **Acknowledged, not addressed this round** — see *Scope note* below. |
| Pride / contribution tracking (Ch. 14) | Missing — nothing records who helped a Goal or Dream succeed. | **Genuinely new**, folded into **FR-59** (reuses the existing Partner-linking mechanism — see FR-59's design decisions). |
| Proactive principle repository (Ch. 15) | Partial — FR-36's Insight Library is deliberately **read-only and retrospective** (its own design decision explicitly ruled out a new authoring surface). | **Genuinely new** — a *different* surface from FR-36, not an extension of it. Scoped as **FR-63**. |

**Scope note — two gaps knowingly left open.** The source gap analysis's
own "Recommended Features" section proposes four feature groups, but two of
the nine chapter-level gaps it identifies (Ch. 8's character-trait
self-assessment, and Ch. 13's personal financial-prudence checks) are not
actually covered by any of those four groups — the summary table's claim
that Group 1 "addresses" Ch. 8 and Ch. 13 does not hold up against what
Group 1 actually specifies (a gratitude log and a contribution prompt,
neither of which checks debt pace or tracks kindness/generosity). Rather
than inventing scope beyond what was actually specified, this document
carries both forward as **open gaps for a future round**, the same way
FR-37.3 was explicitly left unbuilt in V4.0.0 rather than silently assumed.

---

## Business Objective

Extend the system's judgment-screening posture (established in V5.0.0)
from *decisions* into *character and relationships*: notice what's already
going well instead of only what's broken, defuse incoming criticism before
it triggers defensiveness, name the specific expectation behind a flash of
anger before it hardens into resentment, hold confrontation to a short
conduct standard, and give hard-won principles a home to be written down
once and resurfaced later — not just reconstructed after the next mistake.

---

## FR-59 Gratitude & Contribution Log *(Effort: M)*

A lightweight, optional log for what's already going well and who helped —
the counterweight to a system that otherwise only tracks what's unfinished,
blocked, or overdue.

- FR-59.1 **Gratitude entries.** A user can log a short freeform entry
  under one of four original categories — **Gift** (something received,
  not earned), **Health**, **Person** (someone who helped), or **Other** —
  optionally linked to the Dream or Goal it relates to. Entries are
  archived, never hard-deleted, matching every other entity in the system.
- FR-59.2 **Dashboard visibility.** A small dashboard card shows the
  count of gratitude entries logged in the current week and the two or
  three most recent, with a quick-add action — visible, not buried in a
  settings page.
- FR-59.3 **Review integration.** The Weekly/Monthly Review form gains an
  optional prompt — *"What's something you're grateful for this period?"*
  — that, if answered, creates a gratitude entry; leaving it blank has no
  effect on the review's existing BR-42 diligence-checklist gate.
- FR-59.4 **Contribution nudge.** When a Goal or Dream transitions to
  `COMPLETED`, if it has no `Partner` linked to it (directly, or via a
  Goal, reusing the exact linkage FR-50/FR-55 already use), show a
  non-blocking coaching panel: *"Who helped make this possible?"* with a
  shortcut to link an existing Partner or log a **Person**-category
  gratitude entry. This never blocks the completion.

**Acceptance criteria**

1. A gratitude entry can be created with just a category and a description;
   linking a Dream/Goal is optional.
2. The dashboard card's counts and recent entries reflect only the
   authenticated user's own, non-archived entries.
3. Answering the Review prompt creates a gratitude entry linked to nothing
   in particular; leaving it blank saves the review exactly as today.
4. Completing a Goal or Dream with zero linked Partners shows the
   contribution nudge; completing one that already has a linked Partner
   does not. Either way, the completion itself always succeeds.

**Business rules**

| # | Rule |
|---|---|
| BR-48 | The contribution nudge (FR-59.4) is advisory only: it never blocks, delays, or reverses a Goal/Dream's transition to `COMPLETED`, and logging a gratitude entry is never required to complete anything. |

**Data model / migration**

- `V32__gratitude_entries.sql` *(provisional — see Migrations table)*: a new table, `gratitude_entries` — `id`,
  `user_id`, `category` (`GIFT`/`HEALTH`/`PERSON`/`OTHER`), `description`
  (`VARCHAR(2000)`, required), `related_dream_id` (nullable FK),
  `related_goal_id` (nullable FK), `archived`, `created_at`, `updated_at`.

**Design decisions**

- **Reuse Partner-linking for "contribution," don't invent a new relation.**
  `Partner.relatedDreamId`/`relatedGoalId` already exist and already mean
  "this person helped with this." Ch. 14's actual gap — nothing *prompts*
  the user to record that link — is closed by a coaching nudge at
  completion time, not by a new schema.
- **Advisory, not a gate.** Every other character/judgment feature in this
  system that touches a self-report (BR-38's work-style pairing, FR-55's
  decision checklist) is advisory or "complete it, don't score it." A hard
  gate on logging gratitude would turn a wellbeing practice into a chore.

---

## FR-60 Incoming Criticism Triage *(Effort: M)*

A structured way to process criticism *received* — separating what's real
from what's just loud — extending the same `PARTNER`-type Obstacle
worksheet FR-54 already established, rather than building a second,
disconnected tool.

- FR-60.1 On a `PARTNER`-type Obstacle, alongside FR-54's existing six
  prompts, a fourth block offers three short original fields: **Overstated**
  (what was exaggerated or absolute and can be set aside), **Delivery**
  (what was about tone or timing, not substance), and **Substance** (the
  one actionable truth worth keeping, if any).
- FR-60.2 **Convert to action.** A "Turn this into a task" shortcut next to
  the Substance field opens the Task creation form pre-filled with that
  text as the title, targeting the Obstacle's linked Step if it has one
  (the same relation Obstacle already carries); if the Obstacle has no
  linked Step, the shortcut asks the user to pick one first. This is a
  manual, repeatable action — it never creates a task automatically.
- FR-60.3 All three fields are optional and diagnostic: leaving them blank
  changes nothing about the Obstacle's status, severity, or FR-32/FR-54/
  BR-25/BR-26 rules.

**Acceptance criteria**

1. The three triage fields appear only on `PARTNER`-type obstacles,
   alongside (not replacing) FR-54's existing worksheet.
2. Using "Turn this into a task" opens a pre-filled Task form; saving that
   form creates a real Task, but nothing is created until the user submits it.
3. Leaving all three fields blank has no effect on anything else about the
   Obstacle.

**Business rules**

| # | Rule |
|---|---|
| BR-49 | The criticism-triage fields (FR-60.1) and the "turn into a task" shortcut (FR-60.2) are diagnostic only: they never change an Obstacle's status, severity, or any FR-32/BR-25/BR-26 rule, and converting Substance into a Task is always an explicit, user-initiated save — never automatic. |

**Data model / migration**

- `V33__obstacle_criticism_triage.sql` *(provisional — see Migrations table)*: additive, nullable columns on
  `obstacles` — `criticism_overstated`, `criticism_delivery`,
  `criticism_substance` (all `VARCHAR(2000)`).

**Design decisions**

- **Extend FR-54's worksheet, don't fork a second tool.** Incoming
  criticism is still fundamentally a `PARTNER`-type obstacle; adding three
  fields to the same gated block avoids a second private-note-style export
  exclusion to maintain and a second "which obstacle types show this"
  decision to keep in sync.
- **A shortcut, not an automation.** Auto-creating a Task from freeform text
  risks silently generating junk tasks from a half-finished note. A
  pre-filled form the user still has to submit keeps a human in the loop,
  matching how every other "quick add" in this system already works.

---

## FR-61 Expectation Diagnosis — ✅ Done 2026-09-14 *(Effort: S)*

**Shipped (2026-09-14):** Built first in this document's Build Order, so it
claimed migration `V31` rather than the `V33` sketched when this document
was drafted (the same kind of reassignment V5.0.0 and V6.0.0 both saw —
migration numbers follow actual build order, not draft order). New
`ExpectationAgreement` enum (`YES`/`NO`/`UNSURE`) on both the entity and the
frontend type. `ObstacleService` gained a `Clock` dependency (matching
`DreamService`/`PartnerService`'s existing pattern) and a `releaseExpectation`
method — idempotent by construction (`if (getExpectationReleasedAt() == null)`),
exposed as `POST /api/obstacles/{id}/release-expectation`. No new
`prepareObstacle` gate: FR-61's fields and the release action are diagnostic
only per BR-50, so nothing was added to that method. Frontend: the two new
fields and a "Release this expectation" button/confirmation note were added
to `ObstaclesPage.tsx`'s existing `PARTNER`-only conflict-worksheet block;
the button calls the new endpoint directly (no local echo of the timestamp
back into a save) and disables itself while in flight. Verified: backend
202/202 (2 new `ObstacleServiceTest` cases for idempotency, 4 new
`ObstacleExpectationReleaseFlowTests` integration cases including a real
double-POST to the release endpoint), frontend `tsc -b`/build/345 tests all
green. Live-verified against the running dev server: creating a `PARTNER`
obstacle with both new fields round-trips them; releasing twice in a row
returns the identical timestamp both times; an invalid `conflictExpectationAgreed`
value is rejected with 400.

Before an unfulfilled expectation hardens into resentment, name it
specifically — extending FR-54's worksheet with the one upstream question
it currently skips.

- FR-61.1 Two more fields on the same `PARTNER`-type worksheet block:
  *"What did you expect to happen — spoken or not?"* and a Yes/No/Unsure
  answer to *"Did the other person actually agree to this expectation?"*
- FR-61.2 A **Release this expectation** action stamps a timestamp
  (`expectationReleasedAt`) on the Obstacle — a personal marker that the
  user consciously chose to let the expectation go, not a status change
  and not visible to anyone else.
- FR-61.3 Once stamped, the action is idempotent: clicking it again (or
  editing the worksheet further) does not un-set or re-stamp it — matching
  FR-55's "once cleared, it never re-fires" precedent for a one-time marker.

**Acceptance criteria**

1. The two expectation fields and the Release action appear only alongside
   FR-54/FR-60's worksheet on `PARTNER`-type obstacles.
2. Clicking "Release this expectation" sets a timestamp that round-trips on
   reload; clicking it again (or saving the worksheet again) leaves that
   timestamp unchanged.
3. Neither field nor the release action changes the Obstacle's status,
   severity, or FR-32/BR-25/BR-26 rules.

**Business rules**

| # | Rule |
|---|---|
| BR-50 | `Obstacle.expectationReleasedAt` is set exactly once per Obstacle and never re-fires or reverses; the expectation fields and the release action are diagnostic only and never change status, severity, or any FR-32/BR-25/BR-26 rule. |

**Data model / migration**

- `V31__obstacle_expectation_release.sql`: additive, nullable columns on
  `obstacles` — `conflict_expectation` (`VARCHAR(2000)`),
  `conflict_expectation_agreed` (`VARCHAR(10)`, one of `YES`/`NO`/`UNSURE`),
  `expectation_released_at` (`TIMESTAMP`).

**Design decisions**

- **A timestamp, not a checkbox.** A plain boolean "released: yes/no" could
  be flipped back and forth, which would cheapen what is meant to be a
  one-time, conscious act — the same reasoning FR-55 already applied to
  `decisionGateClearedAt`.
- **Three-way agreement answer, not Yes/No.** Most unfulfilled expectations
  in practice were never actually agreed to by anyone — forcing a binary
  answer would misrepresent that, so `UNSURE` is a first-class, honest
  answer rather than a forced guess.

---

## FR-62 Conflict Engagement Checklist *(Effort: S)*

A short, four-item conduct check before a `PARTNER`-type conflict obstacle
can be marked resolved — checking *how* a disagreement was handled, which
FR-54's worksheet never verified.

- FR-62.1 Four original Yes/No checks, added to the same worksheet block:
  *avoided attacking character or name-calling*, *stayed on this specific
  incident rather than reopening past ones*, *avoided threats, ultimatums,
  or sarcasm*, and *named an outcome that works for both sides, not just
  one*.
- FR-62.2 The checklist is completion-gated the same way FR-16/FR-53's
  diligence checklist already is: a `PARTNER`-type Obstacle cannot be
  marked `Resolved` until all four are answered — but, matching FR-55's
  "answering honestly is still answered" precedent, a "No" answer never
  blocks anything; only a blank answer does.

**Acceptance criteria**

1. A `PARTNER`-type Obstacle with any of the four checks unanswered cannot
   be saved with `status = Resolved`; a clear message names what's missing.
2. All four answered — regardless of whether any are "No" — allows the
   `Resolved` transition, alongside FR-32's existing BR-25 `rootCause` gate.
3. Non-`PARTNER` obstacles are entirely unaffected; they never see this
   checklist and never require it.

**Business rules**

| # | Rule |
|---|---|
| BR-51 | A `PARTNER`-type Obstacle cannot transition to `Resolved` unless all four FR-62.1 checklist items are answered, alongside BR-25's existing `rootCause` requirement. The answers themselves never gate the transition — only their completeness does. |

**Data model / migration**

- `V34__obstacle_conflict_checklist.sql` *(provisional — see Migrations table)*: additive, nullable columns on
  `obstacles` — `conflict_no_character_attacks`,
  `conflict_stayed_on_incident`, `conflict_no_threats_or_sarcasm`,
  `conflict_defined_win_win` (all `BOOLEAN`).

**Design decisions**

- **Gate completeness, not content.** Exactly the same principle FR-55's
  decision checklist and FR-16/FR-53's diligence checklist already
  established: this system checks that a self-assessment happened, never
  what its answers were — grading answers would turn honest self-report
  into performance.
- **Extends `prepareObstacle`, doesn't fork a new gate.** BR-51 slots into
  the same method that already enforces BR-25/BR-26, guarded by
  `obstacleType == PARTNER`, matching how FR-50 and FR-55 each added their
  own gate to an existing "validate before save" method rather than
  building a parallel one.

---

## FR-63 Principle Repository *(Effort: M)*

A proactive, user-authored home for hard-won life principles — deliberately
a **different** surface from FR-36's Insight Library, which was scoped
read-only on purpose.

- FR-63.1 A user can write a **Principle**: a short maxim (`VARCHAR(500)`),
  an optional longer note on why it matters, and a life domain — four
  original categories: **Relationships**, **Finance**, **Leadership**,
  **Health**. Principles are archived, never hard-deleted.
- FR-63.2 **Contextual resurfacing on Obstacles**, mirroring FR-36.2's
  existing pattern exactly: opening or logging an Obstacle shows the user's
  own Principles whose domain plausibly matches the obstacle's
  `obstacleType` (e.g. `PARTNER`/`MOTIVATION` → Relationships;
  `MONEY` → Finance), informational only.
- FR-63.3 A dedicated, searchable Principles view (same list/search/archive
  pattern every other entity page already uses) — the write surface FR-36
  deliberately didn't build, because FR-36 was reusing existing Review/
  Obstacle text, and a proactive maxim isn't naturally tied to either.

**Acceptance criteria**

1. Creating a Principle requires only a maxim and a domain; the note is
   optional.
2. Opening or creating an Obstacle whose type maps to a domain with at
   least one Principle shows those Principles in a coaching panel; an
   Obstacle whose type maps to a domain with none shows nothing extra.
3. The Principles page supports create, edit, archive, and search, matching
   every other list page in the system.
4. Nothing about FR-36's existing Insight Library changes — this is an
   additional page, not a modification to it.

**Business rules**

| # | Rule |
|---|---|
| BR-52 | Principle resurfacing on an Obstacle (FR-63.2) is informational only — it never blocks creating, editing, or resolving an Obstacle, matching FR-37.2's "surface, don't nag" precedent. |

**Data model / migration**

- `V35__wisdom_principles.sql` *(provisional — see Migrations table)*: a new table, `wisdom_principles` — `id`,
  `user_id`, `domain` (`RELATIONSHIPS`/`FINANCE`/`LEADERSHIP`/`HEALTH`),
  `principle` (`VARCHAR(500)`, required), `note` (`VARCHAR(2000)`,
  optional), `archived`, `created_at`, `updated_at`.

**Design decisions**

- **A new surface, not an FR-36 extension.** FR-36's own design decision
  explicitly said "reuse, don't re-author" because its source data (Review/
  Obstacle text) already existed. A proactive maxim has no existing source
  record to reuse from — it has to be authored somewhere, so it gets its
  own entity rather than bending FR-36's read-only model to fit.
- **Obstacle-only resurfacing, not Goal-planning resurfacing.** The source
  material also asked for principles to resurface "when a user plans a
  Goal." `Goal` has no domain/category field today, and mapping one would
  be a materially larger change than this item's Effort:M budget — the
  Obstacle path alone already delivers the core "don't relearn the same
  lesson twice" value, using a mapping (`obstacleType` → domain) that
  already exists.

---

## Business Rules (new in V7.0.0)

| # | Rule | Status |
|---|---|---|
| BR-48 | The contribution nudge (FR-59.4) is advisory only: it never blocks, delays, or reverses a Goal/Dream's transition to `COMPLETED`, and logging a gratitude entry is never required to complete anything. | Not started |
| BR-49 | The criticism-triage fields (FR-60.1) and the "turn into a task" shortcut (FR-60.2) are diagnostic only: they never change an Obstacle's status, severity, or any FR-32/BR-25/BR-26 rule, and converting Substance into a Task is always an explicit, user-initiated save — never automatic. | Not started |
| BR-50 | `Obstacle.expectationReleasedAt` is set exactly once per Obstacle and never re-fires or reverses; the expectation fields and the release action are diagnostic only and never change status, severity, or any FR-32/BR-25/BR-26 rule. | ✅ Done 2026-09-14 |
| BR-51 | A `PARTNER`-type Obstacle cannot transition to `Resolved` unless all four FR-62.1 checklist items are answered, alongside BR-25's existing `rootCause` requirement. The answers themselves never gate the transition — only their completeness does. | Not started |
| BR-52 | Principle resurfacing on an Obstacle (FR-63.2) is informational only — it never blocks creating, editing, or resolving an Obstacle, matching FR-37.2's "surface, don't nag" precedent. | Not started |

## Migrations (V7.0.0)

`V31` shipped with FR-61 (built first per the Build Order) and claimed the
slot originally sketched below for FR-59 — the same kind of reassignment
V5.0.0 and V6.0.0 both saw. `V32` through `V35` remain provisional and will
be assigned to whichever FR actually lands in that slot next, not
necessarily the FR each currently names.

| Migration | Purpose | Type | Status |
|---|---|---|---|
| `V31__obstacle_expectation_release.sql` | Expectation fields + release timestamp on `obstacles` (FR-61) | Additive, nullable | ✅ Done 2026-09-14 |
| `V32__gratitude_entries.sql` *(provisional)* | New `gratitude_entries` table (FR-59) | New table | Not started |
| `V33__obstacle_criticism_triage.sql` *(provisional)* | Three criticism-triage fields on `obstacles` (FR-60) | Additive, nullable | Not started |
| `V34__obstacle_conflict_checklist.sql` *(provisional)* | Four conduct-check booleans on `obstacles` (FR-62) | Additive, nullable | Not started |
| `V35__wisdom_principles.sql` *(provisional)* | New `wisdom_principles` table (FR-63) | New table | Not started |

## Build Order

| Order | Item | Why this order | Effort | Status |
|---|---|---|---|---|
| 1 | FR-61 Expectation Diagnosis | Smallest; two fields + a timestamp on the worksheet FR-54 already ships | S | ✅ Done 2026-09-14 |
| 2 | FR-62 Conflict Engagement Checklist | Same worksheet block as FR-61; independent fields, natural to land alongside it | S | Not started |
| 3 | FR-60 Incoming Criticism Triage | Same worksheet block again, plus the one cross-feature link (Task creation shortcut) | M | Not started |
| 4 | FR-59 Gratitude & Contribution Log | Independent of the worksheet work above; its own table and dashboard card | M | Not started |
| 5 | FR-63 Principle Repository | Independent new surface; benefits from landing last so its Obstacle-resurfacing mapping can reuse whatever `obstacleType` conventions the worksheet work above settles on | M | Not started |

## Non-Functional Notes

- Every migration in this document is additive or a brand-new table — no
  destructive schema change, matching every prior version's unbroken
  practice.
- FR-59.4, BR-49, BR-50, and BR-52 are all "diagnostic/advisory, never
  blocks" rules — consistent with how every character-and-judgment feature
  in this system (BR-38, FR-55's checklist) has been designed since
  V5.0.0. BR-51 is the one genuine hard gate in this document, and even it
  gates *completeness* of a self-report, never its content — the same
  restraint BR-42 and FR-55's checklist already apply.
- Two chapter-level gaps (Ch. 8 character-trait self-assessment, Ch. 13
  personal financial prudence) are explicitly **not** addressed in this
  document — see the Origin note's *Scope note*. Closing them would need
  their own gap analysis rather than being folded into features that don't
  actually cover them.
- FR-59, FR-60, FR-61, FR-62 all extend the existing `PARTNER`-obstacle
  worksheet (FR-54) rather than introducing a second worksheet surface;
  only FR-59's gratitude log and FR-63's principle repository are new
  top-level pages.
