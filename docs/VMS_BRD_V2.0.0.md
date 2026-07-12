# Vision Mapping Management System — Business Requirements Document

| | |
|---|---|
| **Document** | VMS_BRD_V2.0.0 |
| **Version** | 2.0.0 (In progress) |
| **Date** | 2026-07-11 (progress updated 2026-07-12) |
| **Status** | In progress — 6 of 11 items shipped to `main`; see the tracker below |
| **Baseline** | Builds on VMS_BRD_V1.0.0 (all V1 requirements remain in force) |
| **Concept source** | *Mentored by a Millionaire* (Steven K. Scott) — used as conceptual reference only; all product wording, questions, and templates are original |

Requirement numbering continues from V1.0.0 (which ended at FR-13).

## Progress tracker (as of 2026-07-12)

Each item's status is also marked inline in its section heading below.

| Item | Description | Status | Commit |
|---|---|---|---|
| FR-17.0 | Expose hook / problem / benefit fields | ✅ Done | `741f7a6e` |
| C-2 + C-3 | Archive confirmation + show-archived/restore | ✅ Done | `741f7a6e` |
| FR-16 | Diligence checkup + guided review templates | ✅ Done | `741f7a6e` |
| FR-17 | Persuasive communication module | ✅ Done | `741f7a6e` |
| FR-14 | Moonshot goals | ✅ Done | `741f7a6e` |
| C-1 | Excel import persistence | ✅ Done | `5be80973` |
| FR-15 | Partner Recruitment Portal | ⬜ Not started | — |
| C-4 | Task estimated / actual hours in UI | ⬜ Not started | — |
| C-5 | Partner phone in UI | ⬜ Not started | — |
| C-6 | Due-date / target-date range filters | ⬜ Not started | — |
| C-7 | Automatic export snapshot before import | ⬜ Not started | — |

**Done (6):** FR-17.0, C-2+C-3, FR-16, FR-17, FR-14, C-1 — all verified (backend + frontend tests, Playwright end-to-end) and pushed to `main`. Migrations V5 (diligence checklist), V6 (word picture), and V7 (goal moonshot) are applied.

**Remaining (5):** FR-15 (largest — the next item in the build order), then the C-4…C-7 small-field batch.

Where a requirement below still says **Planned**, that is its original wording and it is not yet implemented; the ✅ marks added to headings show what has shipped.

---

## 1. Executive Summary

V1.0.0 built the execution structure: the Vision Area → Dream → Goal → Step →
Task hierarchy with progress roll-up, boards, reviews, partners, and
communication. V2.0.0 moves the system from a task tracker toward an
**accelerated achievement engine** by adding the strategy layer the method
teaches: setting goals beyond perceived limits (moonshots), recruiting the
right partners deliberately, checking that work is smart — not just busy —
and communicating requests persuasively.

V2.0.0 also closes the functional gaps carried over from the V1 baseline
(Excel import persistence, archive safety, and field completeness).

## 2. Business Objectives

1. Push users past "achievable" thinking — capture the ambitious version of
   every goal, not only the safe one.
2. Make partner recruitment a managed process (identify → contact → recruit →
   utilize) instead of a contact list.
3. Turn reviews into a quality check on *how* the user works (diligence), not
   only *what* got done.
4. Raise the acceptance rate of support requests through structured,
   persuasive message drafting.
5. Close every "Partial" and "Planned" item from the V1 baseline.

## 3. Guiding Principles

- The existing hierarchy (Vision Area → Dream → Goal → Step → Task) stays the
  core of the system. V2 features layer on top of it; none restructure it.
- All checklist questions, prompts, and message templates ship in original
  wording. The book is a conceptual reference only.
- V2 keeps the V1 conventions: soft delete everywhere, per-user data scoping,
  JWT auth, additive database migrations, tests per business rule.

## 4. Scope

### In scope
- Four new feature modules (FR-14 … FR-17).
- Carried-over V1 completion items (Section 7).

### Out of scope for V2.0.0
- Multi-user collaboration, notifications, calendar sync, mobile apps.
- Document/file uploads (remains on the long-term roadmap).
- Dark mode (still pending a scope decision).
- Admin-role functionality.

---

## 5. New Functional Requirements

### FR-14 Moonshot Goal Setting — ✅ Done (Effort: S)

Encourage goals that exceed the user's current perceived limits.

- FR-14.1 A goal can be marked as a **Moonshot**. The create/edit form asks
  one prompt when the flag is set: *"If resources were no limit, what would
  the ideal result look like?"* — the answer is stored with the goal as its
  moonshot vision statement.
- FR-14.2 Moonshot goals are visually distinct wherever goals appear (Goals
  table, Vision Map tree, dashboard).
- FR-14.3 The dashboard's goal analytics distinguish moonshot from standard
  goals (count and status breakdown).
- FR-14.4 A moonshot flag is aspirational metadata only: it never changes
  progress calculation, completion rules, or any other business rule.

**Acceptance criteria**
1. User can flag a goal as moonshot at create or edit time and record its
   ideal-result statement.
2. Moonshot goals carry a distinct badge in the Goals table and Vision Map.
3. Dashboard shows how many active goals are moonshots.
4. All existing goal rules (BR-1…BR-10) behave identically for moonshot goals.

**Design decision (resolved):** one goal with a flag and a vision statement —
*not* two paired goal records ("achievable" + "moonshot" versions). Pairing
would double list noise and split progress tracking; the flag keeps ambition
visible without changing the data model's shape. Revisit only if users ask to
track both versions separately.

---

### FR-15 Partner Recruitment Portal — ⬜ Not started (Effort: L)

Grow the Partner module from a contact list into a recruitment workflow:
**Identify → Contact → Recruit → Utilize**.

- FR-15.1 **Ideal Partner Profile.** For any complex step, the user can define
  the partner it needs: required experience, character traits, motivation,
  and what the user can offer in return.
- FR-15.2 **Offer Type.** Partners gain an offer-type field describing the
  exchange basis: Money, Shared Vision, Recognition, Experience, or Other.
- FR-15.3 **Partner detail view.** One page per partner aggregating profile,
  pipeline status, every linked work item (vision area/dream/goal/step/task),
  and the full history of communication messages sent to them.
- FR-15.4 **Recruitment stage mapping.** The existing pipeline statuses map to
  the recruitment lifecycle — Identify (To Contact) → Contact (Contacted) →
  Recruit (Active / Declined / Waiting) → Utilize (Active with linked work) →
  Done (Completed) — so no status migration is needed.
- FR-15.5 Blocked tasks with a "partner" gap continue to suggest partner
  types (V1 behavior), and now also link to matching Ideal Partner Profiles
  when one exists for the step.

**Acceptance criteria**
1. User can create an Ideal Partner Profile on a complex step and see it from
   both the step and the Partners page.
2. Partner create/edit includes offer type.
3. Partner detail view lists that partner's communication messages and linked
   work items in one place.
4. A blocked task whose step has an Ideal Partner Profile surfaces it in the
   blocker suggestion.

**Dependencies:** none hard; benefits from FR-17 landing first so the detail
view shows fully structured messages.

---

### FR-16 Diligence Checkup in Reviews — ✅ Done (Effort: M)

Turn reviews into a check on working *smart*, and give each review type the
guided questions the method prescribes (this absorbs carried-over gap G-11).

- FR-16.1 Weekly and monthly reviews include a **diligence checklist** the
  user self-assesses. Original question set (first draft, to refine during
  build):
  1. Is my vision for this area still clear and specific?
  2. Did I work with a plan this period, or react to whatever came up?
  3. Did I use tools and partners where they would move faster than effort
     alone?
  4. Did the highest-priority work actually get my best hours?
  5. Is anything I'm doing the hard way that has a smarter route?
- FR-16.2 Checklist answers (met / not met, plus an optional note) are stored
  with the review and visible when reading it back.
- FR-16.3 Each review type gets its guided question template shown alongside
  the form — daily (what moved, what's blocked, next action), weekly (goals
  moved, overdue, partner follow-ups, top 3 next week), monthly (which dreams
  still matter, which to revise up, pause, or add), quarterly (portfolio-level
  reset).
- FR-16.4 The dashboard review-cadence widget counts a week as "reviewed with
  diligence" when its weekly review includes checklist answers.

**Acceptance criteria**
1. A weekly review cannot be saved with the checklist half-answered (all
   answered or all skipped — no silent partial state).
2. Review type changes the guidance questions shown next to the form.
3. Reading a past review shows its checklist results.

---

### FR-17 Persuasive Communication Module — ✅ Done (Effort: M)

Upgrade the Communication Builder into a persuasion assistant.

- FR-17.0 **Prerequisite (carried-over gap G-7) — ✅ Done:** expose the three
  structured fields that already exist in the API and database but not in the
  form — **Hook**, **Problem**, and **Benefit to Partner**. No backend change needed.
- FR-17.1 **Hook guidance.** Inline helper text and 2–3 original example
  patterns for opening lines that earn attention (a specific question, a
  shared reference, a concrete result).
- FR-17.2 **Word Picture field.** A new optional field for a short, relatable
  scenario that makes the abstract request emotionally concrete, with original
  example patterns.
- FR-17.3 **Focused message structure.** "Generate message" composes in a
  fixed persuasive order — Hook → Problem (with word picture when present) →
  Specific Request → Benefit to Partner → Expected Outcome — replacing the
  current partial composition.
- FR-17.4 The generated draft remains fully editable before saving (V1
  behavior preserved).

**Acceptance criteria**
1. Hook, Problem, Benefit to Partner, and Word Picture are all editable in
   the builder form.
2. Generate produces a message containing every filled structured field in
   the defined order, in respectful, non-demanding language.
3. An existing V1 message opens and edits without data loss.

**Dependencies:** FR-17.0 first (one small UI change); FR-17.2 needs one
additive migration (`word_picture` column).

---

## 6. New Business Rules

| # | Rule |
|---|---|
| BR-11 | A moonshot flag never alters progress, completion, or archival behavior — it is presentation and analytics metadata only. |
| BR-12 | Diligence checklist answers belong to exactly one review and are archived with it. |
| BR-13 | An Ideal Partner Profile belongs to exactly one step and is archived when its step is archived. |

## 7. Carried-Over Scope from V1 Baseline

These close the remaining Partial/Planned items in VMS_BRD_V1.0.0.

| Item | V1 reference | Requirement | Effort | Status |
|---|---|---|---|---|
| C-1 | FR-11.2 | Excel import creates records: map validated rows into vision areas, dreams, goals, steps, and tasks in hierarchy order; report created / skipped / errored counts per sheet; never partially import a broken hierarchy. | L | ✅ Done |
| C-2 | FR-13.3 | Confirmation dialog before archiving, stating the cascade ("this archives N dreams, N goals…"). | S | ✅ Done |
| C-3 | FR-13.4 | "Show archived" toggle on list pages, with restore. Restoring a child whose parent is archived also restores the parent chain. | M | ✅ Done |
| C-4 | G-8 | Expose task estimated/actual hours in the task form and list view. | S | ⬜ Not started |
| C-5 | G-9 | Expose partner phone in the partner form. | S | ⬜ Not started |
| C-6 | G-10 | Due-date range filter on the Tasks Board; target-date range filter on Goals. | S | ⬜ Not started |
| C-7 | G-13 | Automatic export snapshot ("backup") saved before any import runs. | M | ⬜ Not started |

*(G-7 became FR-17.0; G-11 was absorbed into FR-16.)*

## 8. Priority and Build Order

| Order | Work | Why this order | Status |
|---|---|---|---|
| 1 | FR-17.0 (expose hook/problem/benefit) | Smallest change; unblocks FR-17 and improves V1 immediately | ✅ Done |
| 2 | C-2 + C-3 (archive confirm + restore) | Data-safety debt; independent of everything else | ✅ Done |
| 3 | FR-16 (+ absorbed G-11) | High method value, medium effort | ✅ Done |
| 4 | FR-17 (persuasion module) | Builds directly on step 1 | ✅ Done |
| 5 | FR-14 (moonshot) | Small, independent — can slot anywhere | ✅ Done |
| 6 | C-1 (Excel import persistence) | Largest carried-over item; closes the last V1 acceptance criterion | ✅ Done |
| 7 | FR-15 (partner portal) | Largest new feature; benefits from FR-17 being done | ⬜ Not started (next) |
| 8 | C-4 … C-7 | Small completeness batch; fill spare capacity | ⬜ Not started |

## 9. Non-Functional Notes

- All new fields arrive via additive Flyway migrations (V5+); no destructive
  schema changes.
- New business rules (BR-11…BR-13) each get backend test coverage, matching
  V1 practice.
- No new infrastructure is required for V2.0.0 — no file storage, no new
  services. The Render deployment shape is unchanged.
- Reminder from V1: upgrade the free-tier Render PostgreSQL before real user
  data accumulates (no backups, limited lifetime).

## 10. Open Items

| # | Question | Blocking |
|---|---|---|
| O-1 | Final wording of the diligence checklist questions (FR-16.1 list is a first draft). | FR-16 build |
| O-2 | Dark mode in or out — still undecided; excluded from V2.0.0 scope until called in. | Nothing in V2 |
| O-3 | Whether the V2 release also upgrades the Render database plan (operational, not code). | Production readiness |
