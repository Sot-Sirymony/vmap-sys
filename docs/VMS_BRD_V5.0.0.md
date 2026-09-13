# Vision Mapping Management System — Business Requirements Document

| | |
|---|---|
| **Document** | VMS_BRD_V5.0.0 |
| **Version** | 5.0.0 (In progress) |
| **Date** | 2026-09-13 |
| **Status** | 🔶 In progress. Written from a gap analysis comparing VMS_BRD_V1.0.0 → V4.0.0 against the achievement-strategy frameworks the system is conceptually inspired by. ✅ **FR-51** (Bottom-Up Target-Date Cascade), ✅ **FR-53** (Extended Diligence Review Matrix), ✅ **FR-50** (Partner Integrity Vetting Checklist), ✅ **FR-49** (Work-Style Profile & Partner Complementarity), ✅ **FR-52** (Extended Persuasion Structure), and ✅ **FR-54** (Conflict & Feedback Processing Worksheet) shipped 2026-09-13. FR-55 remains proposed — not yet built. |
| **Baseline** | Builds on VMS_BRD_V4.0.0 (all V1–V4 requirements, FR-1…FR-43, remain in force) — **and** on unreleased work already shipped past V4.0.0 (see *Numbering correction* below) |
| **Concept source** | *Mentored by a Millionaire* (Steven K. Scott), used as conceptual reference only, as in V1–V4. This document also draws on *The Richest Man Who Ever Lived* (Steven K. Scott) and a four-quadrant work-style model, for the same reason and under the same rule: **no copyrighted text, named proprietary frameworks, or scripture is reproduced anywhere in this document or in the product.** See *Originality note* below for what that changed. |

Requirement numbering continues from the **true** current state of the
codebase, not from V4.0.0's own numbering — see *Numbering correction*.
New requirements start at **FR-49**. Business rules continue from **BR-37**
(→ new rules start at **BR-38**). Migrations continue from **V20** (→ new
migrations start at **V21**).

---

## Numbering correction (read this first)

The gap analysis this document is built from proposed new requirements
starting at FR-44 / BR-38 / V19. Checking that against the actual codebase
(not just the V4.0.0 BRD file) found that **FR-44 through FR-48, and
migrations V19–V20, are already used** by real, shipped work that landed
after V4.0.0 was written and was never rolled into its own BRD document:

| Already used | What it actually is | Evidence |
|---|---|---|
| FR-44 | Background-tone/contrast correction (page canvas moved off pure white to grey-100; secondary text darkened to grey-700) | `theme.ts`, `global.css` |
| FR-45 | Semantic palette wired into MUI's own component slots | `theme.ts`, `theme.test.ts` |
| FR-46 | Accent contrast raised from a 3:1 to a 4.5:1 target | `theme.test.ts` |
| FR-47 | Palette exposed as `--palette-*` CSS variables for non-MUI surfaces | `global.css`, `palette-vars.test.ts` |
| FR-48 | **Interface Style** — a `CLASSIC` / `MODERN` / (a third, newer style) shape-and-chrome axis, additive `V20__user_interface_style.sql` | `InterfaceStyle.java`, `AppUser.java`, CHANGELOG |
| V19 | `password_reset_tokens` table (no FR number assigned — treated as an internal security hardening item, not a BRD requirement) | `V19__password_reset_tokens.sql` |

None of this is contradicted by V4.0.0 — it simply shipped after that
document was closed and was tracked only in code comments, tests, and the
CHANGELOG's "Unreleased" section rather than in a BRD. **This document does
not touch or renumber that work.** It only makes sure the new requirements
below don't collide with it. If a future documentation pass wants to give
FR-44…FR-48 and the password-reset work a proper BRD write-up, that is a
separate, smaller task from this one.

## Originality note

The source gap analysis named a specific four-type personality model by its
popularized animal-metaphor names and its usual attributed author, quoted a
Bible verse as in-app copy, and cited specific chapter numbers per
requirement. Per this project's standing rule (`CLAUDE.md`: *"Use the book
only as a conceptual reference... Create original wording, original UX,
original forms"*), every FR below keeps the **underlying mechanism** (a
four-quadrant work-style self-assessment, a partner-integrity checklist, a
bottom-up scheduling rule, an expanded persuasion structure, an expanded
diligence checklist, a structured conflict-processing worksheet, a
pre-commitment decision checklist) but:

- Renames the four work-style types to original labels instead of the
  animal-metaphor names (exactly as V2.0.0 renamed the book's own goal
  concept to "Moonshot" and V2.0.0's diligence checklist became five
  original first-person statements rather than a quoted list).
- Replaces the named partner "red flags" list with original wording and
  drops the scripture citation entirely — the warning banner is written in
  the product's own voice.
- Drops per-requirement chapter/session citations, matching how FR-14…FR-48
  cite a concept source once per document, not per paragraph.

---

## Origin note

Following the discipline V3.0.0 and V4.0.0 applied to their own predecessor
drafts, every proposed area below was checked against what the system
already ships.

| Gap area | Current VMS state | Finding |
|---|---|---|
| Work-style self-assessment & partner matching | Nothing — no personality/work-style concept exists on `AppUser` or `Partner`. | **Genuinely new.** Scoped as **FR-49**. |
| Partner integrity vetting | Partial — `Partner` has `offerType` (what the *user* offers, FR-15.2) and `supportType`/`status`, but nothing about the *partner's* trustworthiness or motivation. | **Genuinely new.** Scoped as **FR-50**. |
| Bottom-up target-date scheduling | Missing — `Dream.targetDate`, `Goal.targetDate`, `VisionStep.targetDate`, `TaskItem.dueDate` are all independent fields with no cascade or ordering rule between levels. | **Genuinely new.** Scoped as **FR-51**. |
| Full persuasion structure | Partial — FR-17 already ships Hook, Problem, Word Picture, Benefit to Partner, Expected Outcome, Request, composed in a fixed order. Missing: objection-handling, social proof, value comparison, risk/reward framing, and an explicit call-to-action field. | **Extends FR-17.** Scoped as **FR-52**. |
| Multi-factor diligence check | Partial — `Review` already has a five-item diligence checklist (FR-16: `diligenceClearVision`, `diligenceWorkedPlan`, `diligenceUsedLeverage`, `diligencePriorityFirst`, `diligenceSmarterRoute`, plus a note) and an all-or-nothing save rule. Missing: planning quality, execution quality, timeliness, resource efficiency, and outcome-quality dimensions. | **Extends FR-16.** Scoped as **FR-53**. |
| Structured conflict / feedback processing | Missing entirely. | **Genuinely new**, scoped narrowly. Scoped as **FR-54**. |
| Pre-commitment decision checklist | Missing entirely — nothing gates a Dream's `Idea → Active` transition today. | **Genuinely new**, scoped narrowly using fields that already exist (`Dream.moonshot`, `Dream.priority`, `Partner.supportType`). Scoped as **FR-55**. |

---

## Business Objective

Move the system from *tracking execution well* to *screening the judgment
calls that make execution worth trusting*: know your own working style and
your partners' before you lean on them, vet a partner's character before
handing them something that matters, schedule dates from the ground truth
of the work rather than wishful top-down assignment, make support requests
persuasive enough to actually land, hold reviews to a fuller standard of
diligence, give conflict and criticism a structured outlet instead of an
ignored one, and force a pause for outside counsel before committing to a
big, ambitious bet.

---

## FR-49 Work-Style Profile & Partner Complementarity — ✅ Done 2026-09-13 (Effort: M)

**Shipped (2026-09-13):** New enum `WorkStyleArchetype { DRIVER, CONNECTOR,
STEADIER, PLANNER }`. `V24__user_work_style_profile.sql` adds six nullable
columns to `app_users` (dominant, secondary, and the four raw axis scores);
`V25__partner_work_style_type.sql` adds one nullable column to `partners` —
both claimed exactly the slots the provisional table had already earmarked,
no reassignment needed. The "four raw axis sub-scores" from the original
plan turned out to mean four *pole* counts, not four archetype scores: eight
pace items split into `paceFastScore`/`paceDeliberateScore` and eight focus
items into `focusTaskScore`/`focusPeopleScore` — which is what makes
"redisplay without re-answering" (FR-49.2) and a principled secondary-
archetype rule both possible. `WorkStyleProfileService` computes the
dominant archetype from which pole leads on each axis, and a secondary only
when the *closer* of the two axis margins is within a small threshold (an
8-item axis splitting 5-3 or 4-4 counts as close; 6-2 or wider does not) —
tie-breaks are deterministic and covered by tests. The quiz's 16 items and
their wording, the per-archetype guidance copy (FR-49.5), and the
complementary-archetype lookup table (FR-49.4) all live in a frontend
utility (`utils/workStyleAssessment.ts`), mirroring how FR-16's diligence
questions live in `ReviewsPage`, not the API — only the archetype
*computation* is a backend concern. A new `GET/PUT /api/work-style-profile`
endpoint follows `AppearancePreferenceController`'s no-id-in-the-path shape,
but deliberately does **not** ride the `AuthResponse` the way appearance
preferences do: that piggy-backing is documented there as existing
specifically to avoid a theme flash on first paint, a concern this feature
doesn't share, so bundling it in would have diluted that rationale for no
benefit. New page `WorkStylePage` (`/work-style`, added to the sidebar's
Support group) hosts the quiz and result; `PartnersPage` gained a Work Style
select (FR-49.3); `StepsPage`'s Ideal Partner Profile modal shows the
complementary-archetype suggestion (FR-49.4) whenever the user has taken the
assessment, and renders nothing when they haven't (AC #3) — fetched via one
extra `getWorkStyleProfile` call alongside the page's existing data loads.

Verified: backend 174/174 (7 new `WorkStyleProfileServiceTest` cases —
decisive-both-axes, close-focus, close-pace, both-close-with-tie-break,
a second decisive-archetype case, retake-overwrites, and never-taken),
frontend `tsc -b`/production build/336 tests all green (7 new, covering the
frontend-side scoring function and the complementary-suggestion table).
Live-verified against the running dev server: a fresh account's profile
reads all-null; submitting a decisive 8-0/8-0 split returns `DRIVER` with no
secondary; retaking with a close 8-0/5-3 split returns `DRIVER` with
secondary `CONNECTOR`; a partner created with `workStyleType: PLANNER`
round-trips correctly.

A short self-assessment locating the user (and, optionally, a partner) on
two independent axes — **pace** (fast-deciding ↔ deliberate) and **focus**
(task-oriented ↔ people-oriented) — producing one of four original
archetypes, so the system can suggest a partner whose natural style covers
the user's blind spot rather than duplicating their own.

- FR-49.1 **Self-assessment.** A one-time, retakeable, 16-item forced-choice
  quiz. Each item nudges one of the two axes; the result is a dominant
  archetype (**Driver**, **Connector**, **Steadier**, **Planner** — original
  labels, not the source model's animal names) plus a secondary archetype
  when the two closest scores are within a small margin.
- FR-49.2 **`AppUser` gains a work-style profile:** `workStyleDominant`,
  `workStyleSecondary` (both nullable enums), and the four raw axis
  sub-scores, so the result can be redisplayed and retaken without
  re-answering identically.
- FR-49.3 **`Partner` gains an optional `workStyleType`** — the user's own
  estimate of the partner's style (self-reported by the partner is out of
  scope; there is no partner-facing login). Purely descriptive.
- FR-49.4 **Complementary matching suggestion.** When creating an Ideal
  Partner Profile (FR-15.1) on a complex step, or linking a partner to one,
  the system suggests a complementary archetype based on the user's own
  profile — e.g. a **Driver** user is nudged toward a **Planner** partner
  for follow-through on detail; a **Connector** user toward a **Steadier**
  or **Planner** partner to keep momentum from stalling.
- FR-49.5 **Original guidance copy per archetype** (strengths, likely blind
  spots, one communication tip) — written for this product, not adapted
  from any source text.
- FR-49.6 The profile is diagnostic metadata only: it never blocks saving a
  user, a partner, or an Ideal Partner Profile, and never changes any
  progress, status, or archival rule — same posture as `moonshot` and
  `energyDemand`.

**Acceptance criteria**

1. A user can take the assessment, see a dominant (and, when close,
   secondary) archetype with its guidance text, and retake it at any time —
   retaking overwrites the prior result.
2. A partner can optionally be tagged with a `workStyleType`; leaving it
   unset behaves exactly as today.
3. Opening the Ideal Partner Profile form for a complex step shows a
   complementary-archetype suggestion when the user has a profile, and shows
   nothing (not an error) when they don't.
4. No existing user/partner save path is blocked or altered by the absence
   of a work-style profile.

**Business rules**

| # | Rule |
|---|---|
| BR-38 | A user's work-style profile and a partner's `workStyleType` are optional diagnostic metadata; neither blocks saving any record, and complementary-archetype suggestions are advisory only — the user chooses the partner regardless of the suggestion. |

**Data model / migration**

- `V24__user_work_style_profile.sql`: additive, nullable columns on
  `app_users` — `work_style_dominant`, `work_style_secondary` (enum
  `WorkStyleArchetype { DRIVER, CONNECTOR, STEADIER, PLANNER }`), and four
  nullable `INTEGER` axis scores (`work_style_pace_fast_score`,
  `work_style_pace_deliberate_score`, `work_style_focus_task_score`,
  `work_style_focus_people_score`).
- `V25__partner_work_style_type.sql`: additive, nullable
  `work_style_type VARCHAR(20)` on `partners`, same enum.

**Design decisions**

- **Two axes, four quadrants — not the source model's own four types
  by name.** The 2×2 structure is a well-understood, non-proprietary shape;
  what this document declines to reuse is the specific named framework and
  its animal labels. Original labels keep the mechanism while keeping the
  wording the project's own, consistent with how "Moonshot" replaced the
  source book's own term in FR-14.
- **No partner-facing self-assessment.** Partners have no login; the
  `workStyleType` field is explicitly the user's own estimate, labeled as
  such in the UI, to avoid presenting a guess as the partner's verified
  self-report.

---

## FR-50 Partner Integrity Vetting Checklist — ✅ Done 2026-09-13 (Effort: M)

**Shipped (2026-09-13):** Seven nullable boolean columns (`flag_dishonesty`,
`flag_anger`, `flag_poor_judgment`, `flag_outsized_reward`,
`flag_flattery_pressure`, `flag_gossip`, `flag_disregard_boundaries`), plus
`risk_override_note`, `primary_motivator`, and `vetted_at`, via
`V23__partner_integrity_vetting.sql` — went with seven plain columns rather
than the bitmask alternative the provisional plan floated, since the
frontend renders each as its own toggle and a bitmask would only add a
pack/unpack step with no benefit. New enum `PartnerMotivator`.
`PartnerService.prepareForActive` mirrors `ObstacleService.prepareObstacle`'s
established shape exactly: it checks the entity's *already-set* fields
(support type, status, existing flags, `vettedAt`) rather than accepting new
answers through a separate endpoint, so it needed no new request DTO and
runs identically from `createPartner`, `updatePartner`, and the quick-status
`updatePartnerStatus` PATCH — no path around the gate. `vettedAt` is stamped
with the injected `Clock` bean (the same testability pattern `TaskItemService`
uses for `completedAt`), and once set is never cleared, which is what makes
the gate genuinely one-time per BR-39/FR-50.2. Frontend: the checklist
renders inline in `PartnersPage`'s existing create/edit form (mirroring how
Reviews' diligence checklist is a conditional block, not a separate dialog)
whenever `supportType` is `FINANCIAL`/`TECHNICAL`, `status` is being set to
`ACTIVE`, and the partner isn't vetted yet; a client-side check blocks
submission with the same message the backend would give, before the round
trip. A board drag straight to Active for an unvetted `FINANCIAL`/`TECHNICAL`
partner opens the edit form pre-set to Active instead of completing
silently, since a drag gesture can't collect checklist answers.
`PartnerDetailPage` gained an "Integrity vetting" card (vetted date, which
concerns were flagged, the override note) — the audit trail FR-50.3 calls
for, shown only for the two gated support types.

Caught and fixed in passing: `PartnersPage`'s board-drag handler
(`handleMove`) was already silently dropping `offerType` on every drag
move — omitted from the echoed-back full update, so any drag between
Kanban columns nulled it. Fixed alongside adding the new fields to the same
echo, which needed the identical "send every field back" care.

Verified: backend 167/167 (5 new — flagged+no-note rejection, flagged+note
success, no-flags success, no-re-fire-once-vetted, and non-gated support
type — in `PartnerServiceTest`), frontend `tsc -b`/production build/329
tests all green. Live-verified against the running dev server: a FINANCIAL
partner with a flag and no note returned `400` moving to Active; the same
request with a note returned `200` with `vettedAt` stamped; a later move
away and back to Active with a *new*, unaddressed flag and still no note
succeeded anyway, confirming the gate does not re-fire once vetted.

Before leaning on a partner for something that matters, make the user
pause and actually look at seven original, plain-language warning signs —
not a personality judgment, a character-and-reliability one — and record
why they responded the way they did.

- FR-50.1 **Vetting checklist.** Seven independent yes/no checks, in the
  product's own wording, covering: a pattern of dishonesty, a pattern of
  volatile anger, a pattern of poor judgment, promising a large reward for
  little effort, excessive flattery or high-pressure persuasion, sharing
  others' private information, and disregard for agreements or boundaries.
- FR-50.2 **Trigger point.** The checklist is presented once, the first time
  a partner is moved to `ACTIVE` status *and* their `supportType` is
  `FINANCIAL` or `TECHNICAL` (the two support types where a bad-faith
  partner does the most damage) — not on every status change, and not for
  every support type.
- FR-50.3 **Warning gate.** If any check is flagged, the system shows a
  high-visibility, originally-worded caution (no source-text quotation) and
  requires a short `riskOverrideNote` before the status change is allowed to
  proceed. No flag → no gate, no note required.
- FR-50.4 **Partner motivation field.** `Partner` gains `primaryMotivator`
  (`FINANCIAL_GAIN`, `AVOIDING_LOSS`, `SHARED_VISION`, `RECOGNITION`,
  `OTHER`) — distinct from `offerType` (FR-15.2, what the *user* offers):
  this records what *drives the partner*, informing how a request should be
  framed.

**Acceptance criteria**

1. Moving a `FINANCIAL` or `TECHNICAL` partner to `ACTIVE` for the first
   time shows the seven-item checklist.
2. Checking zero items allows the status change with no extra step.
3. Checking one or more items blocks the status change until a non-blank
   `riskOverrideNote` is provided; providing one allows it to proceed.
4. The checklist and any override note are stored and visible on the
   partner's detail view afterward (an audit trail, not a one-time gate).
5. Partners with other support types, or moving to any status other than
   `ACTIVE`, are never shown the checklist.

**Business rules**

| # | Rule |
|---|---|
| BR-39 | A `FINANCIAL` or `TECHNICAL` partner cannot be moved to `ACTIVE` status with one or more integrity-checklist flags set unless a non-blank `riskOverrideNote` is recorded with the transition. |

**Data model / migration**

- `V23__partner_integrity_vetting.sql`: additive on `partners` — seven
  nullable boolean columns (`flag_dishonesty`, `flag_anger`,
  `flag_poor_judgment`, `flag_outsized_reward`, `flag_flattery_pressure`,
  `flag_gossip`, `flag_disregard_boundaries` — plain columns, not a bitmask;
  see the Shipped note above), `risk_override_note VARCHAR(1000)` nullable,
  `primary_motivator VARCHAR(20)` nullable, `vetted_at TIMESTAMP` nullable.

**Design decisions**

- **Scoped to two support types, not every partner.** Gating every partner
  regardless of role would turn a meaningful check into a rubber-stamp
  click on the majority of partners (Mentor, Colleague, Emotional) where
  the downside of bad character is much lower. Restricting it to
  `FINANCIAL`/`TECHNICAL` keeps the friction where the risk actually is —
  the same reasoning FR-32 used to gate only the two closing obstacle
  statuses, not every one.
- **No scripture, no named source.** The warning banner is written in the
  product's own voice; the underlying idea (confidence placed in the wrong
  person costs more than the trouble of checking first) needs no citation
  to be useful advice inside the product.

---

## FR-51 Bottom-Up Target-Date Cascade — ✅ Done 2026-09-13 (Effort: M)

**Shipped (2026-09-13):** New `ScheduleMode { BOTTOM_UP, TOP_DOWN_FIXED }`
enum; `schedule_mode` added to `dreams` and `goals` via
`V21__schedule_mode.sql` (additive, `NOT NULL DEFAULT 'BOTTOM_UP'` — this
claimed the V21 slot originally reserved for FR-49's first migration, since
FR-51 was built first per the Build Order; FR-49's migrations move to
whatever number is free when it ships, the same reassignment V4.0.0 made
for FR-38/V15). `DreamService`/`GoalService` gained a
`validateScheduleCascade` check (BR-40), called from `updateDream`/
`updateGoal` only — a brand-new record has no children yet, so create-time
validation would always be a no-op; `VisionStepService` gained the same
check for the Step↔Task boundary, unconditionally (no `scheduleMode`
override at that level, exactly as scoped). `GoalRepository` gained
`findByDream_IdAndUser_IdAndArchivedFalse` to support the Dream-side query,
mirroring the existing Goal↔Step and Step↔Task lookups. `DreamResponse`/
`GoalResponse` gained `scheduleMode`, `scheduleOverrun`, and
`scheduleOverrunDetail` — the mapper kept its existing single-arg
`toResponse(Dream)`/`toResponse(Goal)` overloads (defaulting overrun to
`false`/`null`) for the many call sites that don't need it (`DashboardService`,
Excel export) and added a second overload for `DreamService`/`GoalService`'s
own use, so no other service needed to change. Frontend: a "Target date
scheduling" select on the Dreams page, Goals page, and both of the Vision
Map tree's inline edit forms (which — like `energyDemand` before it — expose
every backend field, not a subset), plus a warning-triangle badge with a
tooltip wherever `scheduleOverrun` is true, in both table and card/board
views. The Excel importer and both creation wizards (`DreamWizard`,
`VisionAreaWizard`) pass `BOTTOM_UP` for the field the workbook and wizard
forms have no column/control for, matching how `moonshot` has always been
defaulted on those same paths.

Verified: backend 160/160 (8 new — `DreamServiceTest` is new, plus 2 cases
each added to `GoalServiceTest` and `VisionStepServiceTest`), frontend
`tsc -b`/production build/329 tests all green. Live-verified against the
running local Postgres-backed dev server: a `BOTTOM_UP` dream saved with a
target date before its goal's returned `400` with a message naming the
conflicting goal and its date; the same save under `TOP_DOWN_FIXED`
returned `200` with `scheduleOverrun: true` and a matching
`scheduleOverrunDetail`.

Dates should normally be built up from the smallest committed piece of
work, not assigned top-down and hoped for.

- FR-51.1 **Default: bottom-up.** By default, a parent's target date must
  not be earlier than the latest target/due date among its non-archived
  children: `Goal.targetDate ≥ max(VisionStep.targetDate)`,
  `Dream.targetDate ≥ max(Goal.targetDate)`, and
  `VisionStep.targetDate ≥ max(TaskItem.dueDate)`. Saving a parent date
  earlier than that is rejected with a clear message naming the
  conflicting child.
- FR-51.2 **Top-down hard-deadline mode.** A `scheduleMode` flag
  (`BOTTOM_UP` default, `TOP_DOWN_FIXED`) on `Dream` and `Goal` suspends
  FR-51.1's check for that record — for the case of a real external
  deadline (a grant cycle, an application window) that cannot move. With
  `TOP_DOWN_FIXED` set, the system instead shows an informational (not
  blocking) overrun banner when the children's dates already exceed the
  fixed parent deadline, so the conflict is visible without being forced
  closed immediately.
- FR-51.3 Children created *after* a parent's date is already set are
  **not** retroactively validated against it — the check runs only on the
  save that would violate the rule, so existing data is never invalidated
  by this feature shipping.

**Acceptance criteria**

1. Setting a Goal's target date earlier than one of its Steps' target
   dates is rejected (in `BOTTOM_UP` mode) with a message naming the
   conflicting step.
2. Setting `scheduleMode = TOP_DOWN_FIXED` on a Goal allows any target date
   to be saved; if children's dates already exceed it, a non-blocking
   banner appears on the Goal.
3. The same rule applies at Dream↔Goal and Step↔Task boundaries.
4. A brand-new child with no date set yet never blocks its parent's date.
5. Existing records with dates that would already violate the rule are
   left untouched until the next time that specific record is saved.

**Business rules**

| # | Rule |
|---|---|
| BR-40 | Unless `scheduleMode = TOP_DOWN_FIXED`, a parent's target date must be greater than or equal to the latest non-archived child's target/due date; violating saves are rejected. Under `TOP_DOWN_FIXED`, the same condition is surfaced as an informational overrun banner instead of a save error. |

**Data model / migration**

- `V21__schedule_mode.sql`: additive `schedule_mode VARCHAR(20) NOT NULL
  DEFAULT 'BOTTOM_UP'` on `dreams` and `goals`. New enum `ScheduleMode
  { BOTTOM_UP, TOP_DOWN_FIXED }`.

**Design decisions**

- **A hard save-time rule, not a coaching nudge.** Several V4.0.0 features
  (energy over-commitment, area-starvation) are deliberately advisory —
  but those are *capacity* signals where the user is the better judge.
  Date sequencing is *structural* integrity, the same category as BR-4
  (a complex step needs a task before completion) and BR-25/26 (a status
  transition needs its supporting field) — both of which are hard rules in
  this system. A schedule that claims a goal finishes before its own steps
  do is simply wrong, not a matter of judgment, so this follows the
  hard-rule precedent instead of the coaching one.
- **Per-record opt-out, not a global toggle.** A hard external deadline is
  the exception, not the rule, and it applies to one specific Dream or
  Goal, not to a user's entire account.

---

## FR-52 Extended Persuasion Structure — ✅ Done 2026-09-13 *(extends FR-17; Effort: M)*

**Shipped (2026-09-13):** Four additive nullable `VARCHAR(2000)` columns on
`communication_messages` via `V26__communication_persuasion_fields.sql` —
landed exactly where the provisional table already reserved it, first
migration this initiative didn't need reassigning. Backend change is pure
plumbing: the entity, both DTOs, the mapper, and
`CommunicationMessageService.applyCommunicationRequest` each gained four
fields with no new validation, since — as the Origin note already
established — persuasion completeness (BR-41) was never a server-side gate
in this codebase; "Generate message" has always been a frontend concern
that composes from structured fields the backend just stores verbatim.

The generator itself (previously an inline closure inside
`CommunicationBuilderPage`) was extracted to
`utils/communicationMessageGenerator.ts` as a pure `generateMessageBody`
function, specifically so FR-52's AC #2 ("byte-for-byte unchanged when the
four new fields are blank") is something a unit test can assert directly
rather than something that has to be eyeballed in the browser. Doing that
surfaced a near-miss: the extraction's first draft used a plain
`charAt(0).toLowerCase()` for `lowerFirst`, dropping the original's
special case that leaves a standalone "I" capitalized ("I'd love to..." must
not become "i'd love to..."). Caught by porting the exact original regex
rather than reimplementing it, and confirmed by a dedicated test case.

The three new freeform fields (`objectionsAndAnswers`, `socialProof`,
`valueComparison`) are pushed as their own paragraphs verbatim, the same
"no synthetic wrapper" treatment `hook` already gets — each is described in
FR-52.1 as covering its own point in the user's own words, so adding a
lead-in phrase would fight whatever framing the user already chose.
`callToAction`, when filled, **replaces** the generic closing line
("Would you be open to a short conversation...") rather than appending to
it — a message asking for one specific thing twice, once specific and once
generic, would undercut the specific ask.

One pre-existing quirk, found and deliberately left alone: FR-17's own
documentation states the closing order as "Benefit to Partner → Expected
Outcome," but the shipped generator has always composed it the other way
around (outcome, then benefit). Reordering it now would be an unrelated
behavior change hiding inside an additive feature, and could itself violate
FR-52's own AC #2 for any saved-but-not-yet-regenerated message. Recorded
here rather than silently fixed or silently perpetuated without comment.

Verified: backend 174/174 (unchanged — no new backend logic to test),
frontend `tsc -b`/production build/339 tests all green (3 new in
`communicationMessageGenerator.test.ts`: AC #2's blank-fields equivalence,
AC #3's documented ordering, and the "I" capitalization regression catch).
Live-verified against the running dev server: a message using only the
original FR-17 fields round-trips with all four new fields `null`; a
message using all four round-trips them verbatim.

Round out the Communication Builder with the remaining structural pieces a
high-stakes request benefits from, without disturbing what FR-17 already
ships.

- FR-52.1 **Four new optional fields on `CommunicationMessage`:**
  `objectionsAndAnswers` (anticipated pushback and the planned response),
  `socialProof` (credibility points — prior results, references),
  `valueComparison` (why the ask is worth more than it costs the partner),
  and `callToAction` (the one specific, unambiguous next step being
  requested).
- FR-52.2 **Extended generation order.** "Generate message" composes, when
  present: Hook → Problem (with Word Picture) → Objections & Answers →
  Social Proof → Value Comparison → Specific Request → Benefit to Partner →
  Expected Outcome → Call to Action. Any field left blank is simply
  skipped — the order among *filled* fields is what's fixed.
- FR-52.3 All four fields are optional; a message with none of them
  generates exactly as it does today (no regression to FR-17's existing
  behavior).

**Acceptance criteria**

1. All four new fields are editable in the builder form and persist with
   the message.
2. Generating a message that uses only the original FR-17 fields is
   byte-for-byte unchanged from today's output.
3. Filling in the new fields produces a generated message containing all of
   them in the documented order.
4. An existing message created before this change opens and edits with no
   data loss.

**Business rules**

| # | Rule |
|---|---|
| BR-41 | A generated message must include, at minimum, a Hook or Problem, a Request, and a Benefit to Partner (unchanged from the FR-17 baseline). The four FR-52 fields are additive and never required. |

**Data model / migration**

- `V26__communication_persuasion_fields.sql`: four additive, nullable
  columns on `communication_messages` — `objections_and_answers`,
  `social_proof`, `value_comparison`, `call_to_action` (all
  `VARCHAR(2000)`, matching the sizing of FR-17's existing fields).

**Design decisions**

- **Extend the existing entity, don't fork a new "worksheet" form.** FR-17
  already established the pattern of a single structured entity generating
  one message; adding four more optional fields keeps one editing surface
  and one generator instead of splitting persuasion logic across two
  places.

---

## FR-53 Extended Diligence Review Matrix — ✅ Done 2026-09-13 *(extends FR-16; Effort: S–M)*

**Shipped (2026-09-13):** Five additive nullable `BOOLEAN` columns plus one
nullable `INTEGER` score on `reviews`, via `V22__review_diligence_extended.sql`
(claimed the slot the provisional migrations table had earmarked for FR-49;
FR-53 was built second per the Build Order). `ReviewService.validateDiligenceChecklist`
widened from five to ten items by extracting a shared `diligenceAnswers(request)`
helper, reused by a new `computeDiligenceScore` method (`metCount * 100 /
10`, returning `null` whenever any of the ten is unanswered — the existing
validation already guarantees no partially-answered state reaches it).
`Integer`, not `NUMERIC`/`BigDecimal` as originally sketched below: ten
equal-weight yes/no items only ever produce a multiple of 10, so a decimal
type would carry a precision the value never needs. Frontend: `ReviewsPage`'s
`DILIGENCE_QUESTIONS` array (already data-driven for rendering and the
answered-count check) grew from five entries to ten; the three places that
still named fields explicitly — the save payload, the edit-open handler, and
`EMPTY_DILIGENCE` — were extended to match. Added a live "N of 10 answered
so far" hint (mirroring FR-32's "N of 3" pattern) and a `Done (70%)`-style
score in the Diligence table column, falling back to plain `Done` for
reviews saved before this change (which have answers but no stored score).

Verified: backend 162/162 (2 new — a widened-rule rejection case and a
score-computation case in `ReviewServiceTest`), frontend `tsc -b`/production
build/329 tests all green. Live-verified against the running dev server: a
review with the original five checks plus one of the new five returned
`400` with the existing rejection message; all ten answered (7 true, 3
false) returned `201` with `diligenceScorePercent: 70`.

FR-16 already checks whether the *plan* was sound. This adds whether the
*execution* was sound, using the same all-or-nothing pattern FR-16
established.

- FR-53.1 **Five additional diligence checks** on `Review`, in the same
  first-person, self-assessed style as FR-16's existing five:
  planning quality (*"Did I plan this properly before starting, with input
  where I needed it?"*), execution quality (*"Did I do the work to a
  standard I'd stand behind?"*), timeliness (*"Did I move on this promptly
  rather than letting it sit?"*), resource efficiency (*"Did I use my time
  and tools well, without waste?"*), and outcome quality (*"Is the result
  actually solid, not just finished?"*).
- FR-53.2 **Diligence score.** When all ten checks are answered, the review
  stores a computed `diligenceScorePercent` (met-count ÷ 10 × 100) — a
  simple, transparent number rather than a weighted formula.
- FR-53.3 The existing all-or-nothing rule extends to all ten items: a
  weekly or monthly review with the checklist started must have every item
  answered, not just the original five (FR-16.1's rule, widened).

**Acceptance criteria**

1. A weekly/monthly review shows all ten diligence checks together, not the
   original five with the new five hidden elsewhere.
2. Saving with some of the ten answered and others blank is rejected, same
   as FR-16.1 today for the original five.
3. Saving with all ten answered stores a `diligenceScorePercent` visible
   when the review is read back.
4. A daily or quarterly review (which never carried the checklist) is
   unaffected.
5. An existing review saved under the five-item rule (before this change)
   continues to display and edit normally; its score is simply left unset
   until the review is next saved with all ten answered.

**Business rules**

| # | Rule |
|---|---|
| BR-42 | A weekly or monthly review's diligence checklist, once started, requires all ten checks answered (met/not-met) before saving; there is no partial state. `diligenceScorePercent` is computed, never entered directly. |

**Data model / migration**

- `V22__review_diligence_extended.sql`: five additive nullable `BOOLEAN`
  columns on `reviews` (`diligence_rightly_planned`,
  `diligence_rightly_performed`, `diligence_expeditious`,
  `diligence_efficient`, `diligence_quality_outcome`) and one nullable
  `diligence_score_percent INTEGER` (changed from the originally-sketched
  `NUMERIC` — see the Shipped note above).

**Design decisions**

- **Widen the existing rule rather than add a second checklist.** A second,
  parallel diligence concept would force the user to context-switch between
  two "how did I do" checklists on the same review. Extending the one
  FR-16 already built keeps the review form coherent.
- **A plain percentage, not a weighted score.** Ten equally-weighted yes/no
  items keep the number auditable at a glance; a weighted formula would add
  a tuning knob nobody asked for.

---

## FR-54 Conflict & Feedback Processing Worksheet — ✅ Done 2026-09-13 *(Effort: S–M)*

**Shipped (2026-09-13):** Six additive nullable `VARCHAR(2000)` columns on
`obstacles` via `V27__obstacle_conflict_worksheet.sql`, landing exactly
where already provisioned. No new validation in `ObstacleService` — the six
fields flow through `createObstacle`/`updateObstacle` alongside the
existing FR-32 fields, and `prepareObstacle`'s BR-25/26 gates are untouched
(FR-54.4). BR-43 needed no exclusion logic to write: `ExcelService`'s
Obstacles sheet has always listed its columns explicitly (`ID`, the four
related-entity IDs, `Title`, `Type`, `Severity`, `Status`, `Solution`) and
never included even FR-32's `rootCause`/`creativeAlternatives`, so simply
never adding `conflictPrivateNote` (or any of the other five) to that list
*is* the enforcement — there's no separate switch to flip. Proved rather
than assumed: a new integration test creates an obstacle with a private
note containing a unique marker, exports the workbook through the real
`/api/excel/export` endpoint, and asserts with Apache POI that the marker
string appears in zero cells across every sheet — not just the Obstacles
sheet, matching BR-43's "regardless of export options" wording.

Frontend: `ObstaclesPage` gained a "Conflict worksheet" block — six original
prompts (what happened, what it cost, their likely perspective, what's
worth keeping, a private note, one next action) — rendered only when
`obstacleType === 'PARTNER'`, with the private note field visibly labeled
"never shared or exported" right where it's edited, not just in
documentation. FR-54.3's constructive-feedback tone was added to the FR-52
generator rather than as a new one: `generateMessageBody` gained an
optional `tone` parameter, and `CommunicationBuilderPage` gained a tone
selector next to "Generate message." The three-part structure reuses
existing structured fields rather than inventing new ones — `hook` as the
positive observation, `request` as the specific correction (it was already
"the specific ask," which for feedback *is* the correction), and
`expectedOutcome` as the closing note of confidence — so FR-8.2/FR-17.3's
generator is genuinely reused, not duplicated.

Verified: backend 175/175 (1 new — the export-exclusion integration test
in `ExcelImportFlowTests`), frontend `tsc -b`/production build/341 tests
all green (2 new: default-tone-unchanged and the three-part structure's
field ordering, both in `communicationMessageGenerator.test.ts`).
Live-verified against the running dev server: a `PARTNER`-type obstacle
saved with all six worksheet fields round-trips them verbatim; exporting
that account's workbook and inspecting the raw XLSX XML directly (not just
the parsed cell values) confirms the private note's marker text appears in
zero files.

A structured, private way to work through an interpersonal obstacle
instead of leaving `Obstacle.solution` as the only outlet — plus a
ready-made tone option for delivering hard feedback constructively.

- FR-54.1 **Guided worksheet**, available on any Obstacle whose
  `obstacleType` is `PARTNER`: six short original prompts — name the
  specific incident, name what it cost (time, trust, an opportunity), the
  other person's likely perspective, anything of value learned from it, a
  private note to work through the reaction (never sent to anyone), and one
  concrete next action.
- FR-54.2 **The private note is excluded from Excel export** and carries a
  visible "private — never shared or exported" label wherever it appears,
  addressing the sensitivity of the content without requiring a new
  storage/encryption subsystem the app does not otherwise have.
- FR-54.3 **Constructive-feedback tone.** The Communication Builder's
  "Generate message" gains an optional tone selector; choosing
  *Constructive feedback* composes the message as: one specific positive
  observation → the specific correction needed → a closing note of
  confidence — reusing FR-8.2/FR-17.3's existing generator, not a new one.
- FR-54.4 The worksheet is optional and diagnostic: it never changes the
  Obstacle's status, severity, or any FR-32 rule (BR-25/26 still govern
  Resolved/Accepted exactly as today).

**Acceptance criteria**

1. Opening a `PARTNER`-type obstacle offers the worksheet; opening any other
   type does not.
2. The worksheet's private note never appears in an Excel export, in any
   sheet, under any circumstances.
3. Selecting *Constructive feedback* tone and generating a message produces
   the three-part structure; the default tone's output is unchanged from
   today.
4. Completing, skipping, or leaving the worksheet blank has no effect on
   the obstacle's status transitions or on BR-25/BR-26.

**Business rules**

| # | Rule |
|---|---|
| BR-43 | An obstacle's conflict-worksheet private note is never included in any Excel export sheet, regardless of export options. |

**Data model / migration**

- `V27__obstacle_conflict_worksheet.sql`: additive, nullable columns on
  `obstacles` — `conflict_incident`, `conflict_cost`,
  `conflict_other_perspective`, `conflict_lesson`, `conflict_private_note`,
  `conflict_next_action` (all `VARCHAR(2000)`).
- No migration needed for FR-54.3 — it is a frontend-only addition to the
  existing message generator.

**Design decisions**

- **Exclusion from export, not encryption.** The source gap analysis asked
  for the private note to be "stored client-side or encrypted." This system
  has no client-side storage layer and no existing field-level encryption
  anywhere in the schema — introducing one for a single field would be a
  new security subsystem, not a feature. The achievable and honest
  equivalent is guaranteeing the note never leaves the user-scoped database
  via export, which is the concrete way this data has actually been shown
  to travel outside the app (FR-11).
- **Scoped to `PARTNER`-type obstacles.** A conflict-processing worksheet
  answering "what did this person do and how do I feel about it" doesn't
  fit a `TIME` or `MONEY` obstacle; narrowing the surface keeps the
  worksheet meaningful where it's offered instead of generic filler on
  every obstacle type.

---

## FR-55 Decision Prudence Checklist *(Effort: S)*

A brief, mandatory pause before committing fully to a big, ambitious bet —
using fields the system already has rather than inventing a new "financial
risk" concept.

- FR-55.1 **Trigger.** Moving a **Moonshot** Dream (FR-31) with priority
  **High** or **Critical** from `IDEA` to `ACTIVE` requires clearing one of
  two gates.
- FR-55.2 **Gate A — checklist.** An eight-item, originally-worded
  self-check covering common decision traps: skipping real research,
  assuming nothing will change, trusting a claim without checking it,
  judging by appearance or charisma alone, deciding under artificial time
  pressure, deciding alone with no outside input, chasing a reward that
  looks too easy, and dismissing advice that disagrees with the preferred
  answer. All eight must be answered; the checklist itself never blocks —
  answering honestly that a trap applies is still "answered."
- FR-55.3 **Gate B — counselor confirmation.** Alternatively, the
  transition is allowed once at least two `Partner` records with
  `supportType` of `ADVISOR` or `MENTOR` are linked to the Dream (directly,
  or via a linked Goal) — i.e., the user has recorded actually consulting
  someone.
- FR-55.4 Clearing **either** gate (not both) unlocks the transition; the
  checklist's answers are stored for later review but do not themselves
  pass/fail the gate — the discipline is answering it, not scoring well
  on it.

**Acceptance criteria**

1. A non-Moonshot Dream, or a Moonshot Dream with Low/Medium priority,
   transitions `IDEA → ACTIVE` exactly as today — no new gate.
2. A High/Critical Moonshot Dream with neither the checklist completed nor
   two Advisor/Mentor partners linked is blocked from `ACTIVE` with a clear
   message describing both ways to proceed.
3. Completing the eight-item checklist (regardless of answers) clears the
   gate.
4. Linking a second Advisor/Mentor-type partner to the Dream (or one of its
   Goals) clears the gate without requiring the checklist.
5. Once cleared, the gate does not re-block a later status round-trip
   (e.g. Active → Paused → Active) — it is a one-time commitment check, not
   a recurring one.

**Business rules**

| # | Rule |
|---|---|
| BR-44 | A Moonshot Dream with High or Critical priority cannot transition from `IDEA` to `ACTIVE` unless either its eight-item decision checklist has been completed, or at least two `ADVISOR`/`MENTOR` partners are linked to it (directly or via a Goal). Once cleared for a given Dream, the gate does not re-apply on subsequent transitions back into `ACTIVE`. |

**Data model / migration**

- `V28__dream_decision_checklist.sql`: additive on `dreams` — eight nullable
  `BOOLEAN` columns and one `decision_gate_cleared_at` timestamp (set the
  first time either gate is satisfied; its presence is what makes the gate
  "one-time").

**Design decisions**

- **Trigger from existing fields, not a new risk flag.** The source gap
  analysis imagined a general "financial or time risk" flag that doesn't
  exist anywhere in the data model. `moonshot + High/Critical priority` is
  the closest existing proxy for "a big, ambitious commitment" and needs no
  new authoring step to compute.
- **Two gates, either one sufficient.** Forcing both the checklist *and*
  two counselors would be heavier than either V1–V4 precedent (FR-32's
  gates each require exactly one thing) or the underlying goal (make the
  user pause and do *one* real check, not run a compliance gauntlet).
- **One-time, not recurring.** Re-litigating the same Dream's prudence every
  time its status round-trips would turn a meaningful pause into an
  annoying tax on normal pause/resume usage (which FR-6/FR-31 treat as
  routine).

---

## Build Order

| Order | Work | Why this order | Effort | Status |
|---|---|---|---|---|
| 1 | FR-51 Bottom-Up Target-Date Cascade | Structural, backend-first, independent of every other item here | M | ✅ Done 2026-09-13 |
| 2 | FR-53 Extended Diligence Review Matrix | Small, additive columns on an entity (`Review`) that already has the pattern half-built | S–M | ✅ Done 2026-09-13 |
| 3 | FR-50 Partner Integrity Vetting Checklist | Independent; benefits from landing before FR-49 so "vetting" and "matching" aren't built in the same PR | M | ✅ Done 2026-09-13 |
| 4 | FR-49 Work-Style Profile & Partner Complementarity | Depends conceptually on FR-50 existing so the partner detail view has one integrity/character section, not two built separately | M | ✅ Done 2026-09-13 |
| 5 | FR-52 Extended Persuasion Structure | Extends FR-17's existing generator; independent of the above | M | ✅ Done 2026-09-13 |
| 6 | FR-54 Conflict & Feedback Processing Worksheet | Smaller; benefits from FR-52's generator changes landing first (shares the tone-selector idea) | S–M | ✅ Done 2026-09-13 |
| 7 | FR-55 Decision Prudence Checklist | Smallest in isolation but depends on FR-50's `ADVISOR`/`MENTOR` partner concept being solid, since Gate B counts those partner records | S | Not started |

---

## Business Rules (new in V5.0.0)

| # | Rule | Status |
|---|---|---|
| BR-38 | A user's work-style profile and a partner's `workStyleType` are optional diagnostic metadata; neither blocks saving any record, and complementary-archetype suggestions are advisory only. | ✅ Done 2026-09-13 |
| BR-39 | A `FINANCIAL` or `TECHNICAL` partner cannot be moved to `ACTIVE` status with one or more integrity-checklist flags set unless a non-blank `riskOverrideNote` is recorded with the transition. | ✅ Done 2026-09-13 |
| BR-40 | Unless `scheduleMode = TOP_DOWN_FIXED`, a parent's target date must be ≥ the latest non-archived child's target/due date; violating saves are rejected. Under `TOP_DOWN_FIXED`, the same condition is an informational banner instead of a save error. | ✅ Done 2026-09-13 |
| BR-41 | A generated message must include, at minimum, a Hook or Problem, a Request, and a Benefit to Partner. The FR-52 fields are additive and never required. | ✅ Done 2026-09-13 |
| BR-42 | A weekly/monthly review's diligence checklist, once started, requires all ten checks answered before saving. `diligenceScorePercent` is computed, never entered directly. | ✅ Done 2026-09-13 |
| BR-43 | An obstacle's conflict-worksheet private note is never included in any Excel export sheet. | ✅ Done 2026-09-13 |
| BR-44 | A Moonshot Dream with High/Critical priority cannot transition `IDEA → ACTIVE` unless its eight-item decision checklist is completed, or ≥2 `ADVISOR`/`MENTOR` partners are linked to it. The gate does not re-apply once cleared for that Dream. | Not started |

## Migrations (V5.0.0)

`V21` and `V22` shipped with FR-51 and FR-53 respectively (built first and
second per the Build Order) and claimed the slots originally sketched for
FR-49 and FR-53 below — the same reassignment V4.0.0 made when FR-38's
`issue_reports` table took the V15 slot originally reserved for an FR-36
index that turned out not to be needed. `V23` through `V27` each shipped
exactly where already provisioned (FR-50, FR-49's two migrations, FR-52,
then FR-54), so no further reassignment was needed this round. The only
number still provisional is `V28` (FR-55).

| Migration | Purpose | Type | Status |
|---|---|---|---|
| `V21__schedule_mode.sql` | `schedule_mode` on `dreams` and `goals` (FR-51) | Additive, `NOT NULL` with default | ✅ Done 2026-09-13 |
| `V22__review_diligence_extended.sql` | Five diligence checks + `INTEGER` score on `reviews` (FR-53) | Additive, nullable | ✅ Done 2026-09-13 |
| `V23__partner_integrity_vetting.sql` | Integrity checklist flags, override note, motivator, vetted-at on `partners` (FR-50) | Additive, nullable | ✅ Done 2026-09-13 |
| `V24__user_work_style_profile.sql` | Work-style archetype + axis scores on `app_users` (FR-49) | Additive, nullable | ✅ Done 2026-09-13 |
| `V25__partner_work_style_type.sql` | `work_style_type` on `partners` (FR-49) | Additive, nullable | ✅ Done 2026-09-13 |
| `V26__communication_persuasion_fields.sql` | Four persuasion fields on `communication_messages` (FR-52) | Additive, nullable | ✅ Done 2026-09-13 |
| `V27__obstacle_conflict_worksheet.sql` | Conflict-worksheet fields on `obstacles` (FR-54) | Additive, nullable | ✅ Done 2026-09-13 |
| `V28__dream_decision_checklist.sql` *(provisional)* | Decision checklist + gate timestamp on `dreams` (FR-55) | Additive, nullable | Not started |

## Non-Functional Notes

- Every migration in this document is additive — no destructive schema
  change, matching V1.0.0 through V4.0.0's unbroken practice.
- FR-51 (BR-40) and FR-55 (BR-44) are the two **hard** business rules in
  this document; everything else (FR-49, FR-50's checklist itself, FR-52,
  FR-53, FR-54) is diagnostic/advisory metadata, consistent with how this
  system has always drawn that line (compare BR-27, BR-29, BR-31 — all
  advisory — against BR-4, BR-25, BR-26 — all hard gates).
- No new infrastructure is required: no scheduler, no notification channel,
  no file storage, no encryption subsystem. FR-54's privacy requirement is
  met by export exclusion rather than by adding encryption, which would
  have been the one item here to actually require new infrastructure.
- New business rules (BR-38…BR-44) should get backend test coverage where
  they carry logic, matching V1–V4 practice. **BR-38 through BR-43 are all
  done**: BR-38 has no gate to test directly (its whole content is "never
  blocks"), but the archetype-computation logic FR-49 actually carries is
  covered by seven new `WorkStyleProfileServiceTest` cases; BR-39 covered by
  five new cases in `PartnerServiceTest` (flagged+no-note rejection,
  flagged+note success, no-flags success, no-re-fire-once-vetted, non-gated
  support type); BR-40 covered by `DreamServiceTest` (new), plus new cases
  in `GoalServiceTest` and `VisionStepServiceTest`; BR-41 was never a
  backend gate (the FR-17 baseline never enforced it server-side either —
  it describes what "Generate message" produces, not a save-time
  validation), so its coverage is frontend `communicationMessageGenerator.test.ts`
  cases instead; BR-42 covered by two new cases in `ReviewServiceTest` (a
  widened-rule rejection and a score computation); BR-43 covered by a new
  `ExcelImportFlowTests` integration test that exports a real workbook and
  confirms a marker string is absent from every cell of every sheet —
  backend 175/175, frontend 341/341, both green.
- This document is a **living plan, mostly built**: FR-49/BR-38,
  FR-50/BR-39, FR-51/BR-40, FR-52/BR-41, FR-53/BR-42, and FR-54/BR-43 are
  shipped; only FR-55/BR-44 remains unbuilt. Per this project's standing
  rule of working phase by phase and stopping for confirmation between
  phases, the Build Order table
  is a recommendation, not a commitment — confirm which item to build next
  before
  further code
  changes begin.
