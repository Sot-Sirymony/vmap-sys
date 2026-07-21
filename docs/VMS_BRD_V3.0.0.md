# Vision Mapping Management System — Business Requirements Document

| | |
|---|---|
| **Document** | VMS_BRD_V3.0.0 |
| **Version** | 3.0.0 (In progress) |
| **Date** | 2026-07-21 |
| **Status** | Complete — FR-32 (Obstacle Creative Persistence) and FR-33 (Vision Area Setup Wizard) both ✅ shipped 2026-07-21. |
| **Baseline** | Builds on VMS_BRD_V2.0.0 (all V1/V2 requirements, FR-1…FR-31, remain in force) |
| **Concept source** | *Mentored by a Millionaire* (Steven K. Scott) — used as conceptual reference only; all product wording, questions, and templates are original |

Requirement numbering continues from V2.0.0 (which ended at FR-31, Addendum D).

## Origin note

This document replaces an earlier unstructured draft ("VMS 3.0.0 Strategic
Upgrade Roadmap") that proposed four upgrade areas without checking them
against the system already shipped. Reviewing that draft against the current
codebase found that three of its four areas are already built:

| Draft area | Finding |
|---|---|
| "Reprogramming Dashboard" — Moonshot goals vs. attainable goals | **Already shipped.** FR-14 (Moonshot Goal Setting, V2.0.0) and FR-31 (Moonshot Dreams, Addendum D) cover this exactly — a flag plus an ideal-result vision statement, at both Goal and Dream level. No new work. |
| "Strategic Partner Recruitment Engine" — profile generator, pitch drafts, relationship tracker | **Already shipped.** FR-15 (Partner Recruitment Portal, V2.0.0) covers this: Ideal Partner Profiles (required experience/traits/motivation/offer-in-return) per complex step, an `OfferType` field, the Identify → Contact → Recruit → Utilize pipeline, and a partner detail view aggregating linked work and messages. No new work. |
| "Persuasive Communication Module" — hook, word picture, clear request | **Already shipped.** FR-17 (Persuasive Communication Module, V2.0.0) is a byte-for-byte match: Hook, Problem, Word Picture, Benefit to Partner fields, composed in that fixed order by "Generate message." No new work. |
| "Reprogramming Dashboard" — a setup step per Vision Area forcing a written vision before goals | **Genuinely new.** No wizard or vision-statement field exists at the Vision Area level today (only at Dream level, via FR-21.3's `DreamWizard`). Scoped below as **FR-33**. |
| "Creative Persistence & Obstacle Analysis" — root-cause analysis before closing, ≥3 alternatives before giving up | **Genuinely new.** `Obstacle` has no root-cause field and no alternatives field, and its status transition is completely unguarded today. Scoped below as **FR-32**, built first. |

Only FR-32 and FR-33 are new V3.0.0 scope. The three already-shipped areas
are cross-referenced above rather than rebuilt or restructured.

## Business Objective

Turn Obstacle logging from a passive record into a discipline: an obstacle
cannot be waved away as "accepted" without first proving the user looked for
a way around it, and cannot be marked "resolved" without capturing why it
happened in the first place — so the same setback is less likely to repeat
silently next time.

---

## FR-32 Obstacle Creative Persistence — ✅ Done 2026-07-21 (Effort: S)

Two fields and two status-transition guards, additive to the existing
`Obstacle` entity (V1 baseline, `docs/VMS_BRD_V1.0.0.md`).

- FR-32.1 **Root cause field.** An optional free-text `rootCause` field
  captures why the obstacle happened, not just how it was worked around
  (that's what `solution` already records).
- FR-32.2 **Creative alternatives field.** An optional free-text
  `creativeAlternatives` field, one alternative per line, for the
  "creative persistence" brainstorm — the different ways the user
  considered getting past the obstacle before deciding.
- FR-32.3 **Resolved requires a root cause.** Moving an obstacle's status to
  **Resolved** requires `rootCause` to be filled in. This is the
  "analyze the setback before closing it" rule — resolving without
  understanding why it happened just invites a repeat.
- FR-32.4 **Accepted requires three alternatives.** Moving an obstacle's
  status to **Accepted** (the "we're not solving this" status) requires
  `creativeAlternatives` to contain at least three non-blank lines. This is
  the "don't give up too easily" rule — accepting defeat requires having
  first written down at least three ways out.
- FR-32.5 Both fields are plain text, editable at any status, and have no
  effect on any other business rule (progress, archival, etc.) — same
  "aspirational/diagnostic metadata only" posture as the moonshot fields.

**Acceptance criteria**

1. Creating or updating an obstacle with status **Resolved** and a blank
   `rootCause` is rejected with a clear error; the same request with
   `rootCause` filled in succeeds.
2. Creating or updating an obstacle with status **Accepted** and fewer than
   three non-blank lines in `creativeAlternatives` is rejected; three or
   more succeeds.
3. Every other status (Open, In Progress) accepts either field blank —
   the guard only fires on the two closing statuses.
4. The status PATCH endpoint (`updateObstacleStatus`) enforces the same
   two rules as the full update, so there is no way around the guard via
   the quick-status path.
5. Existing obstacles created before this change (both fields null) keep
   working normally in every status except a move into Resolved or
   Accepted, which now asks for the missing field like any other record.

**Business rules**

| # | Rule |
|---|---|
| BR-25 | An obstacle cannot be marked Resolved unless `rootCause` is non-blank. |
| BR-26 | An obstacle cannot be marked Accepted unless `creativeAlternatives` contains at least three non-blank lines. |

**Design decisions**

- **Two single-purpose fields, not one combined field or a child table.**
  Root cause and alternatives answer different questions ("why did this
  happen" vs. "what else did I try") and gate different transitions
  (Resolved vs. Accepted); combining them would blur which one a given
  status actually requires. A child table (one row per alternative) was
  considered and rejected as over-engineering for a field the user fills in
  once, freeform, in one sitting — matching the existing `solution` field's
  own free-text precedent rather than introducing a new list-editing UI.
- **Line-count validation, not a rigid three-input form.** Splitting
  `creativeAlternatives` on newlines and counting non-blank lines (≥ 3)
  keeps the field a natural "type your alternatives, one per line" text box
  instead of forcing exactly three separate inputs — consistent with how
  the rest of the app treats free-text fields.
- **Guard lives in the service layer, not a DTO annotation.** Both fields
  are optional on `ObstacleRequest` (`@Size` only, no `@NotBlank`) exactly
  like `TaskItem.blockerReason`; the actual gate is conditional on the
  target status, which a static annotation can't express — mirrors
  `TaskItemService.prepareTask`'s existing enforcement of "Blocked requires
  a blocker reason" (BR-5).

**Implementation notes**

- Migration `V10__obstacle_creative_persistence.sql`: adds
  `root_cause VARCHAR(3000)` and `creative_alternatives VARCHAR(3000)` to
  `obstacles`, both nullable — additive, no backfill needed.
- `Obstacle` entity gains `rootCause` / `creativeAlternatives`;
  `ObstacleRequest` / `ObstacleResponse` gain the same two fields.
- `ObstacleService` gains a private `prepareObstacle(entity)` validation
  step (mirroring `TaskItemService.prepareTask`), called from
  `createObstacle`, `updateObstacle`, and `updateObstacleStatus` so the
  guard applies on every path that can set status, including the
  quick-status PATCH endpoint.
- Frontend: `ObstaclesPage`'s form shows a live "alternatives" line-count
  hint and disables the confirming action into Resolved/Accepted until the
  relevant field satisfies BR-25/BR-26, mirroring the existing
  Blocked-requires-blocker-reason UX on the Tasks Board.

**Shipped (2026-07-21):** Migration `V10__obstacle_creative_persistence.sql`
adds `root_cause` and `creative_alternatives` (both nullable `VARCHAR(3000)`)
to `obstacles`. `Obstacle`/`ObstacleRequest`/`ObstacleResponse` gain the two
fields; `ObstacleService` gained `prepareObstacle(entity)` (mirroring
`TaskItemService.prepareTask`), called from `createObstacle`,
`updateObstacle`, and `updateObstacleStatus` so the guard applies on the
quick-status PATCH path too, not just full updates. Frontend: `ObstaclesPage`
gained Root Cause and Creative Alternatives fields with contextual hints,
including a live "N of 3 listed so far" counter derived from non-blank
lines; the Root Cause field also carries an HTML `required` attribute when
status is Resolved, so the browser blocks an empty submit before the
request even reaches the server (BR-25 is enforced twice: client-side for
instant feedback, server-side as the real gate). The count-of-three rule
(BR-26) has no client-side equivalent to `required`, so the backend's
rejection message is what the user sees for that one.

Verified: 8 new `ObstacleServiceTest` cases (backend, all passing — creating
or updating into Resolved without a root cause throws; into Accepted with
0–2 alternatives throws; 3+ alternatives succeeds; Open/In Progress need
neither field; the quick-status PATCH path enforces the same rules).
Backend suite: 83/83 green. Frontend: `tsc -b` clean, production build
green, 35/35 tests green. Live end-to-end verification via a seeded
obstacle: a raw PATCH to Resolved with no root cause returned HTTP 400 with
the expected message; in the browser, submitting Resolved with a blank Root
Cause was blocked client-side by the required-field browser prompt, filling
it in saved successfully; submitting Accepted with 2 alternatives showed
the backend's rejection banner with a live "2 of 3" hint, and adding a
third alternative saved successfully.

---

## FR-33 Vision Area Vision Statement & Setup Wizard — ✅ Done 2026-07-21 (Effort: M)

- FR-33.1 **Vision statement field.** `VisionArea` gains an optional
  `visionStatement` free-text field — the written answer to "what does
  this area of your life or work look like when it's going well?", one
  level above a Dream's own `whyImportant`/`successDefinition`.
- FR-33.2 **Guided setup wizard.** Creating a Vision Area offers a short
  guided flow (mirroring `DreamWizard`'s pattern) that asks the vision
  statement question before the flat create form's fields, then offers to
  jump straight into adding a first Dream — so a new area doesn't sit
  empty with no path in, same problem FR-21.3 solved for Dreams.
- FR-33.3 The flat create form remains available as "skip the guide,"
  exactly like the Dream flow — the wizard is coaching, not validation, so
  `visionStatement` is never required to save a Vision Area.

**Acceptance criteria**

1. A Vision Area can be created via the flat form with no vision statement,
   exactly as today (no regression).
2. The guided wizard asks the vision-statement question, then offers to
   add a first Dream inline, without leaving the page.
3. An existing Vision Area's vision statement is editable from its edit
   form; the field is visible wherever a Vision Area's detail is shown.

**O-11, resolved during build:** the wizard does prompt for a first Dream
inline, mirroring `DreamWizard`'s own goal quick-add rather than stopping
at the vision statement — same "don't leave the page" rationale that drove
FR-21.3.

**Shipped (2026-07-21):** Migration `V11__vision_area_vision_statement.sql`
adds nullable `vision_statement VARCHAR(3000)` to `vision_areas`.
`VisionArea`/`VisionAreaRequest`/`VisionAreaResponse`/mapper/service gain
the field; the Excel importer's `VisionAreaRequest` construction passes
`null` (the workbook has no such column, matching how Dream/Goal import
already behave for their own new fields). `VisionAreasPage` gained a Vision
Statement textarea (always visible, optional, coaching hint) and switched
its "Create vision area" trigger — plus the existing `?create=area` deep
link used by the dashboard checklist, global shortcut, and command
palette — from opening the flat form directly to opening the new
`VisionAreaWizard`, exactly the same swap FR-21.3 made for Dreams; "Skip
the guide" still reaches the flat form.

`VisionAreaWizard.tsx` mirrors `DreamWizard.tsx` scaled to Vision Area's
smaller field set: step 1 asks name + priority, step 2 asks the vision
statement (optional, never a save gate), then a third screen offers to
add a first Dream inline (title only; sensible defaults — `LONG_TERM`,
`MEDIUM` priority, `IDEA` status, the dream-level "hasn't started yet"
status FR-31 introduced) without leaving the page, same pattern as
`DreamWizard`'s goal quick-add.

**Bug caught and fixed during live verification:** `VisionAreasPage`'s
per-area Dream count (used by the Dreams column) loaded once on mount and
never refreshed after the wizard added a dream, so a freshly-wizarded area
showed "0 Dreams" until a manual page reload. Fixed by extracting the
count load into `reloadDreamCounts` and calling it from the wizard's
`onCreated` callback too, mirroring `DreamsPage`'s existing
`reloadGoalCounts` pattern.

Verified: backend suite 83/83 green (no new backend tests needed —
`visionStatement` carries no business rule, unlike FR-32's guarded
fields). Frontend `tsc -b` clean, production build green, 35/35 tests
green. Live end-to-end verification via a fresh account: empty-state →
wizard → name "Career" + priority → vision statement question → area
created → first Dream added inline without leaving the modal → list shows
the new area with the Dream count now correctly at 1 → Edit modal confirms
the vision statement persisted verbatim.

---

## Build Order

| Order | Work | Why this order | Status |
|---|---|---|---|
| 1 | FR-32 Obstacle Creative Persistence | Small, fully additive, backend-first; no UI redesign required | ✅ Done |
| 2 | FR-33 Vision Area Setup Wizard | Larger, frontend-heavy; benefits from being scoped after FR-32 ships and settles | ✅ Done |

## Non-Functional Notes

- FR-32 ships via one additive Flyway migration (V10); no destructive schema
  changes, no new endpoints (existing Obstacle CRUD/status routes are
  reused).
- New business rules (BR-25, BR-26) get backend test coverage, matching
  V1/V2 practice.
- No new infrastructure required.
