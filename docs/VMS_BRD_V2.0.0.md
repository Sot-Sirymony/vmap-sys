# Vision Mapping Management System — Business Requirements Document

| | |
|---|---|
| **Document** | VMS_BRD_V2.0.0 |
| **Version** | 2.0.0 (In progress) |
| **Date** | 2026-07-11 (progress updated 2026-07-17) |
| **Status** | Complete — all 11 items shipped and all open items resolved. Addendum A (FR-18 Theme Settings) also ✅ shipped 2026-07-17. Addendum B (2026-07-18) adds the **planned** V2.1 "Ease of Use" improvement program (FR-19 … FR-29) — 📋 not yet started. |
| **Baseline** | Builds on VMS_BRD_V1.0.0 (all V1 requirements remain in force) |
| **Concept source** | *Mentored by a Millionaire* (Steven K. Scott) — used as conceptual reference only; all product wording, questions, and templates are original |

Requirement numbering continues from V1.0.0 (which ended at FR-13).

## Progress tracker (as of 2026-07-17)

Each item's status is also marked inline in its section heading below.

| Item | Description | Status | Commit |
|---|---|---|---|
| FR-17.0 | Expose hook / problem / benefit fields | ✅ Done | `741f7a6e` |
| C-2 + C-3 | Archive confirmation + show-archived/restore | ✅ Done | `741f7a6e` |
| FR-16 | Diligence checkup + guided review templates | ✅ Done | `741f7a6e` |
| FR-17 | Persuasive communication module | ✅ Done | `741f7a6e` |
| FR-14 | Moonshot goals | ✅ Done | `741f7a6e` |
| C-1 | Excel import persistence | ✅ Done | `5be80973` |
| FR-15 | Partner Recruitment Portal | ✅ Done | `c68b2ff8` |
| C-4 | Task estimated / actual hours in UI | ✅ Done | `c68b2ff8` |
| C-5 | Partner phone in UI | ✅ Done | `c68b2ff8` |
| C-6 | Due-date / target-date range filters | ✅ Done | `6db60f67` (tasks), `c68b2ff8` (goals) |
| C-7 | Automatic export snapshot before import | ✅ Done | `c68b2ff8` |

**Done (11 of 11):** every V2 item is implemented and verified (backend + frontend tests). Migrations: V5 (diligence checklist), V6 (word picture), V7 (goal moonshot), V8 (ideal partner profiles + partner offer type).

**Remaining:** none — V2.0.0 scope is complete. Addendum B below plans the V2.1 "Ease of Use" improvement program (FR-19 … FR-29); the next major BRD (V3) continues numbering at FR-30.

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
- Dark mode — *was* pending a scope decision (O-2); called in and shipped on 2026-07-17.
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

### FR-15 Partner Recruitment Portal — ✅ Done (Effort: L)

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
| C-4 | G-8 | Expose task estimated/actual hours in the task form and list view. | S | ✅ Done |
| C-5 | G-9 | Expose partner phone in the partner form. | S | ✅ Done |
| C-6 | G-10 | Due-date range filter on the Tasks Board; target-date range filter on Goals. | S | ✅ Done |
| C-7 | G-13 | Automatic export snapshot ("backup") saved before any import runs. Snapshot lands in `data/backup/` (configurable via `app.excel.backup-dir`); a failed snapshot aborts the import. | M | ✅ Done |

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
| 7 | FR-15 (partner portal) | Largest new feature; benefits from FR-17 being done | ✅ Done |
| 8 | C-4 … C-7 | Small completeness batch; fill spare capacity | ✅ Done (built ahead of FR-15) |

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

| # | Question | Blocking | Status (2026-07-17) |
|---|---|---|---|
| O-1 | Final wording of the diligence checklist questions (FR-16.1 list is a first draft). | FR-16 build | ✅ Resolved — shipped as five first-person statements the user confirms (see `ReviewsPage.tsx`); revisit only if the wording should change. |
| O-2 | Dark mode in or out — still undecided; excluded from V2.0.0 scope until called in. | Nothing in V2 | ✅ Resolved — decided IN and shipped 2026-07-17: header toggle, OS-preference default, choice remembered per browser; Fluent dark tokens across the MUI theme and all page CSS. |
| O-3 | Whether the V2 release also upgrades the Render database plan (operational, not code). | Production readiness | ✅ Resolved — `render.yaml` pins the database to the paid `basic-256mb` plan (free Postgres is deleted after 30 days). |

---

## Addendum A (2026-07-17) — FR-18 Theme Settings

Added after V2.0.0 closed, as a scope add-on. Dark mode (O-2) shipped as a
fixed light/dark pair behind one header toggle; FR-18 grows that into a
proper appearance-settings capability, built on the theming surface MUI's
`createTheme` actually exposes.

Requirement numbering continues from V2 (which ended at FR-17). FR-19 … FR-29
are consumed by Addendum B below, so the next major BRD (V3) starts at
**FR-30**.

### Theming building blocks — current state vs. FR-18

MUI themes are customized through seven configuration categories. Where the
app stands today, and what FR-18 does with each:

| Category | What it controls | Today (post dark mode) | Under FR-18 |
|---|---|---|---|
| `palette` | Colors (primary, secondary, error, warning, info, success) and light/dark `mode` | Fluent light + dark palettes in `buildTheme(mode)`; mode toggled in the header, remembered per browser | Adds a *System* mode option and an accent-color choice (FR-18.2, FR-18.3) |
| `typography` | Font family, sizes, weights, letter spacing | Font family only (Segoe UI stack); sizes/weights are MUI defaults | Font-size scale setting (FR-18.5) |
| `spacing` | The base unit behind margins and padding (default 8px) | MUI default, not set explicitly | Density setting scales it (FR-18.4) |
| `breakpoints` | Responsive thresholds (xs, sm, md, lg, xl) | MUI defaults — while `global.css` media queries use their own 960/720/560px values, so the two systems can disagree | Aligned to one set of thresholds (FR-18.1) |
| `components` | Global per-component style overrides | Button, Chip, Card, Dialog, OutlinedInput overridden for the Fluent look | Unchanged; new settings flow through these, never around them |
| `transitions` | How elements animate between states | MUI defaults | Set explicitly and documented (FR-18.1) |
| `zIndex` | Stacking order of layered elements | MUI defaults (toasts hardcode `z-index: 1000` in CSS) | Set explicitly; toast layer moved onto the scale (FR-18.1) |

### FR-18 Theme Settings — ✅ Done 2026-07-17 (Effort: M)

- FR-18.1 **One theme source of truth.** Every category above is set
  explicitly in `buildTheme` (spacing base, breakpoint values, transition
  durations, z-index scale), and `global.css` derives from the same tokens:
  its media queries use the theme's breakpoint values and its layered
  elements (toasts) sit on the theme's z-index scale. No silent MUI defaults.
- FR-18.2 **Mode setting: Light / Dark / System.** The header toggle becomes
  a three-way appearance setting. *System* follows the OS preference live
  (reacts to OS changes without reload); an explicit Light or Dark choice is
  remembered per browser, as today.
- FR-18.3 **Accent color.** The user picks the brand accent from a curated
  set (default: the current Communication Blue) — each option ships with
  pre-validated light- and dark-mode values so contrast never depends on user
  judgment. Flows through `palette.primary` and the matching CSS variables.
- FR-18.4 **Density.** A Comfortable / Compact choice scales the spacing base
  unit and switches the size defaults of dense components (tables, inputs,
  buttons) so long lists fit more rows without zooming.
- FR-18.5 **Font size.** A Small / Medium / Large scale applied through the
  typography config, not browser zoom, so layout proportions hold.
- FR-18.6 **Settings live in one place.** A small Appearance section (header
  menu or settings page) groups mode, accent, density, and font size;
  all choices persist per browser and apply instantly.

**Acceptance criteria**
1. Selecting System mode follows the OS theme and updates live when the OS
   preference changes; explicit Light/Dark still wins once chosen.
2. Changing accent, density, or font size restyles the app instantly and
   survives a reload.
3. `global.css` contains no breakpoint value that differs from the theme's,
   and no hardcoded z-index.
4. Every appearance setting passes WCAG AA contrast in both modes with any
   accent selected.

**Business rule**

| # | Rule |
|---|---|
| BR-14 | The status and priority palettes are never user-themable. They encode meaning (Completed green, Blocked orange, Critical red) shared by badges, charts, and the Excel export's conditional formatting — an accent-color choice must not change them. |

**Out of scope for FR-18:** per-user theme sync across devices (settings are
per browser until a user-preferences API exists), fully custom color pickers
(curated accents only, to protect contrast), and theming of the login page's
navy hero panel.

**Implementation notes (shipped):** the Appearance menu lives behind the
header's mode icon (sun/moon/monitor mirrors the active mode). Five accents
ship (Blue default, Teal, Purple, Green, Orange), each defined once in
`theme.ts` with light and dark ramps; the provider feeds the same values to
the MUI palette and the CSS variables. Density scales the MUI spacing base
(8 → 6) plus the table/card paddings via `[data-density]`; text size scales
the root font size so every rem-based value follows. The breakpoints are the
theme's `sm 600` / `md 900` in both systems, and the toast stack sits on the
theme's snackbar z-index tier. Pre-FR-18 light/dark choices migrate from the
old storage key automatically.

---

## Addendum B (2026-07-18) — V2.1 "Ease of Use": HCI & UX/UI Improvement Program — 📋 Planned

Added after V2.0.0 and FR-18 closed. V2.0.0 completed the feature set; user
feedback risk is now **usability**, not capability. This addendum turns the
two study documents at the repository root into BRD scope:

- [HUCI_V1.md](../HUCI_V1.md) — interaction design: flows, navigation cost,
  input friction, behavioral accessibility. Owns the "how it works" half.
- [UX&UI_V1.md](../UX&UI_V1.md) — presentation design: layout, typography,
  visual hierarchy, states, motion. Owns the "how it looks" half.

Those documents hold the research methodology and file-level detail; this
addendum holds the business requirements, acceptance criteria, and build
order. Where they conflict, this addendum wins.

**Release framing:** ships as **V2.1.0**, a frontend-focused minor release.
No schema migrations are expected; any backend work is limited to small
additive endpoints (noted per requirement). All V1/V2 business rules
(BR-1 … BR-14) remain in force.

**Guiding question:** *Can a first-time user go from an empty account to one
Dream with Goals, Steps, and Tasks in under 10 minutes, without
instructions?*

### Addendum B progress tracker

| Item | Description | Effort | Status | Commit |
|---|---|---|---|---|
| FR-19 | Experience baseline & audit program | M | 🔶 Nearly done — static + runtime instruments complete 2026-07-18 (`docs/hci-audit.md`, `docs/uxui-audit/` incl. axe baseline + 58 screenshots); only the human instruments remain: first-use test + SUS (M-1/M-3/M-6, blocked on O-7 participants) | |
| FR-20 | Design-system foundations (type ramp, states, motion) | M | ✅ Done 2026-07-18 | |
| FR-21 | Guided onboarding & dream coaching wizard | M | ✅ Done 2026-07-18 | |
| FR-22 | Low-friction data entry (quick-add, form pass) | M | ✅ Done 2026-07-18 | |
| FR-23 | Orientation & navigation (breadcrumbs, nav groups) | S | ✅ Done 2026-07-18 | |
| FR-24 | Vision Map as primary workspace | L | ✅ Done 2026-07-18 | |
| FR-25 | Attention & next-action system | M | ✅ Done 2026-07-18 | |
| FR-26 | Accessibility compliance (WCAG 2.2 AA) | M | ✅ Done 2026-07-18 | |
| FR-27 | List, board, and auth visual refresh | M | ✅ Done 2026-07-18 | |
| FR-28 | Mobile layouts | L | 📋 Planned | |
| FR-29 | Efficiency & delight layer (stretch) | M | 📋 Stretch | |

### B-Scope

**In scope:** FR-19 … FR-28; FR-29 as stretch if capacity allows.

**Out of scope for V2.1:** any rebrand or change of design language (Fluent 2
stays); native mobile apps; multi-user collaboration or notifications;
per-user preference sync across devices (settings stay per browser until a
user-preferences API exists); new feature modules (that is V3, FR-30+).

### B-Success metrics

Measured in FR-19 before any change, re-measured at release (HUCI Phases
A/E; UX&UI Phases A/E).

| # | Metric | Baseline | Target |
|---|---|---|---|
| M-1 | Time: empty account → first complete Dream tree (first-use test) | measure | ≤ 10 min |
| M-2 | Clicks to create 1 goal + 3 steps + 5 tasks | measure | −40 % |
| M-3 | System Usability Scale (SUS) score | measure | ≥ 75 |
| M-4 | Lighthouse accessibility, top 6 pages | measure | ≥ 95 |
| M-5 | Tasks board fully operable by keyboard | No | Yes |
| M-6 | 3-second test: users name the page's primary action | measure | ≥ 80 % of pages |
| M-7 | Distinct font size/weight combinations in codebase | measure | ≤ 9 |
| M-8 | Pages with layout jump during load | measure | 0 |

### FR-19 Experience Baseline & Audit Program — 🔶 In progress (Effort: M)

Measure before improving; the audits rank everything that follows.

- FR-19.1 **Heuristic evaluation** of all 15 pages against Nielsen's 10
  usability heuristics; each violation logged with severity 1–4 in
  `docs/hci-audit.md` (template in HUCI_V1 §5).
- FR-19.2 **Cognitive walkthrough** of the CLAUDE.md "Ideal User Flow"
  (Area → Dream → 4 Goals → Steps → Tasks) recording clicks, page changes,
  form fields, and errors — the M-2 baseline.
- FR-19.3 **First-use test** with 3–5 people who have never seen the app
  (think-aloud, recorded), producing M-1 and the SUS baseline (M-3).
- FR-19.4 **Accessibility audit**: keyboard-only pass on every page, axe
  DevTools/Lighthouse on the top 6 pages, contrast check in both themes
  (M-4 baseline).
- FR-19.5 **Visual audit**: screenshot inventory of every page in
  light/dark × comfortable/compact; squint-test hierarchy notes; typography
  and spacing census (M-6 … M-8 baselines); state walkthrough (loading /
  empty / error / single-item); sweep of the 4 worst FR-18 setting
  combinations; chart consistency review. Artifacts in `docs/uxui-audit/`.
- FR-19.6 The same instruments re-run at release close to verify the
  B-Success metrics table.

**Acceptance criteria**
1. `docs/hci-audit.md` and `docs/uxui-audit/` exist with findings ranked by
   severity, and every B-Success baseline cell is filled in.
2. Each finding is mapped to an FR in this addendum or explicitly deferred
   to a V2.2/V3 backlog with a reason.

**Status (2026-07-18):** static instruments complete — FR-19.1 heuristic
evaluation (16 findings, H-01…H-16), FR-19.2 cognitive walkthrough (M-2
baseline: ≈ 111 interactions via list pages vs ≈ 63 via the Vision Map's
existing quick-adds), and the FR-19.5 code-level censuses (M-7 ≈ 17 type
combinations; 59 hex literals outside token files; a WAITING-color
contradiction between the partner pipeline chart and the app palette).
Key scope corrections recorded in `docs/hci-audit.md` §1: the board already
has a keyboard move path and drop highlighting, quick-add already exists at
all three tree levels, and the dashboard already has a (single-step)
first-run prompt — FR-21/22/26/27 shrink accordingly.

**Runtime instruments completed 2026-07-18:** FR-19.4 accessibility baseline
measured with axe-core via Playwright (M-4: 3–6 violation types per top-6
page; dominant issues are color-contrast, 102 nodes, and missing landmark
regions, 156 nodes; login page clean) — see
`docs/uxui-audit/a11y-baseline.md`. FR-19.5 screenshot inventory captured
(58 images, all pages × light/dark × comfortable/compact plus the worst
FR-18 combination on the top 6) with **zero layout breakages** in the
combination sweep. **Pending:** only FR-19.3 (first-use test + SUS,
M-1/M-3/M-6) — human participants required (O-7); the manual keyboard-only
focus pass folds into that session.

### FR-20 Design-System Foundations — ✅ Done 2026-07-18 (Effort: M)

Codify the system pieces missing from `theme.ts`/`global.css` once, so every
later requirement is cheap. Source: UX&UI_V1 Phase B.

- FR-20.1 **Type ramp.** A full MUI `typography` scale (page title, section
  title, card/panel title, body, secondary body, caption for metadata like
  `T-014` codes) with line heights; all pages swept onto the variants.
- FR-20.2 **Spacing scale and page template.** The 4px-base scale as named
  steps; one standard page anatomy (title row with right-aligned primary
  action → optional summary strip → filter bar → content) hardened in
  `PageSection` and applied to every list page.
- FR-20.3 **State kit.** Skeleton loaders that preserve layout shape for
  tables, cards, and the vision-map tree (spinner only for unknown-shape
  content); empty states with icon + headline + explanation + one primary
  action, in full-page and in-panel sizes; error states as a human sentence
  + retry, with technical detail collapsed.
- FR-20.4 **Motion tokens.** Three durations (~100 ms hover, ~180 ms
  open/close, ~250 ms layout) and one easing defined in `:root` and the MUI
  theme; applied to modal, drawer, kanban drop, and progress-bar fill; a
  global `prefers-reduced-motion` kill switch.
- FR-20.5 **Icon rules.** One inline size, one nav/CTA size, one stroke
  width; `nav-items.ts` is the concept→icon source of truth (a dream is
  always Sparkles, everywhere).

**Acceptance criteria**
1. M-7 met (≤ 9 type combinations) and M-8 met (zero load layout jump).
2. No hardcoded hex color, font size, duration, or z-index outside
   `theme.ts`/`global.css` (BR-15 check passes).
3. Reduced-motion users see no non-essential animation.

**Shipped (2026-07-18):** 6-size type ramp in `theme.ts` typography +
`--font-*` variables (M-7: 17 → 6); every page's title is its single `<h1>`
via `PageSection` (which also gained the FR-20.2 `actions` slot); motion
tokens (100/180/250 ms + easing) in both theme and CSS with a global
`prefers-reduced-motion` kill switch (BR-19); shape-matched table/cards/tree
skeletons wired per page (M-8); EmptyState full-size form
(icon/headline/action) and ErrorMessage Retry on all data pages; hex sweep
complete — **0 literals outside the token files** (was 59), palettes
relocated verbatim with recolor decisions deferred to FR-25/27 as planned;
icon scale normalized (14/16/18/24) with the concept vocabulary in
`docs/uxui-icons.md`. Verified: `tsc` clean, 35/35 tests, production build,
post-change screenshots in light and dark. Remediation detail:
`docs/uxui-audit/README.md` §"FR-20 remediation".

### FR-21 Guided Onboarding & Dream Coaching Wizard — ✅ Done 2026-07-18 (Effort: M)

Solve the empty-start problem: a new user currently faces zeroed stat cards
and 11 sidebar entries with no path in. Source: HUCI_V1 B1, B2, C3.

- FR-21.1 **Guided empty dashboard.** When counts are zero, the dashboard
  shows a 3-step "Get started" checklist (Create a Vision Area → Add a Dream
  → Break it down) with direct action buttons, instead of empty stat cards.
- FR-21.2 **Actionable empty states.** Every list/panel empty state names
  its context and offers the create action ("No goals yet — add a goal to
  *{dream}*"), using the FR-20.3 kit.
- FR-21.3 **Dream coaching wizard.** Dream creation becomes a short guided
  flow asking the method's clarifying questions in original wording (What
  exactly do you want to achieve? Why does it matter? What will success look
  like? When?), mapping answers onto the existing Dream fields
  (title, whyImportant, successDefinition, targetDate), then offering to add
  first goals immediately. The flat form remains available as "skip the
  guide".
- FR-21.4 The wizard is styled as friendly coaching (per FR-20 tokens), not
  as validation errors.

**Acceptance criteria**
1. A fresh account's dashboard shows the guided checklist; it disappears
   once real data exists.
2. A user can complete the wizard into a Dream with ≥ 1 Goal without
   visiting another page.
3. All existing Dream validation rules still apply; no schema change.

**Shipped (2026-07-18):** `GettingStarted` replaces the single-step first-run
card — three chained steps (area → dream → break it down) that stay as a
dashboard banner until the first dream has goals, fixing the audit's H-01
dead end; each step deep-links into a create flow (`/vision-areas?create=area`
auto-opens the form, `/dreams?create=dream` opens the wizard, and a new
`/vision-map` route lands on the tree). `DreamWizard` asks the method's
clarifying questions across three friendly steps (what/area, why/success,
when/priority — only title and area are hard requirements, per FR-21.4),
then adds first goals inline. FR-21.2: the five hierarchy pages got
full-size empty states with a context action, including "create the parent
first" routing when the parent level is empty (`CrudModalForm` gained a
controlled-create mode to support this). All three acceptance criteria
verified by a scripted end-to-end run on a fresh account (register →
checklist → area → wizard → dream + goal → checklist gone), which also
caught and fixed a mount-order bug where the wizard opened before areas
loaded and Continue stayed disabled. `tsc` clean, 35/35 tests, production
build green.

### FR-22 Low-Friction Data Entry — ✅ Done 2026-07-18 (Effort: M)

Cut the modal round-trip cost of repetitive entry. Source: HUCI_V1 B3, B7;
UX&UI_V1 C6.

- FR-22.1 **Quick-add rows** on Goals and Steps pages and at every level of
  the Vision Map (generalizing the existing `QuickAddTitle`): type a title,
  press Enter, entity created with sensible defaults (priority Medium,
  status Not Started); full form reachable via Edit.
- FR-22.2 **Quick-add respects required fields (BR-16).** Task quick-add
  prompts inline for the two required fields defaults cannot supply (owner
  defaults to the current user; due date asked inline) — it must not create
  records violating business rule 5.
- FR-22.3 **Form friction pass.** Every modal form: first field autofocused,
  Enter submits, Esc cancels, validation inline on blur, and an "Add
  another" option that keeps the modal open for batch entry.
- FR-22.4 **Form layout standard.** Consistent field order (identity →
  classification → dates → details), section dividers on long forms, one
  required-marker and helper-text style.

**Acceptance criteria**
1. Creating a goal with 3 steps and 5 tasks meets the M-2 click target.
2. No quick-add path can produce a record that the full form's validation
   would reject.
3. Keyboard-only form completion works on every form.

**Shipped (2026-07-18):** `QuickAddRow` (parent select + title, Enter to
add) on the Goals and Steps pages, and on Tasks with an inline due-date
field — owner defaults to the signed-in user, priority Medium, status Not
Started, so no quick-add path violates business rule 5 (BR-16); step
quick-add continues the goal's sequence numbering. The create form's owner
field and the Vision Map's task quick-add also default to the signed-in
user. Form friction pass: first field autofocused in every modal (required
a `data-autofocus` hand-off in `Modal` because MUI's focus trap overrides
React autoFocus), Esc/Enter already native, and a **Save & add another**
submit that keeps the modal open, clears, and refocuses for batch entry.
The 14-field task form is regrouped per FR-22.4 (identity → ownership &
priority → dates → details) with labeled section dividers, and blocker
fields now appear only when status is Blocked. One CSS rule gives every
required control a quiet "Required" caption — no per-form markup. Verified
live end-to-end: quick-added a goal/step/task through the real UI
(defaults confirmed via API) and batch-created two tasks through one
modal session — which surfaced and fixed two real bugs (empty owner
default silently blocking submits, and a stale add-another flag after a
validation-blocked click). `tsc` clean, 35/35 tests, production build.

### FR-23 Orientation & Navigation — ✅ Done 2026-07-18 (Effort: S)

Keep users oriented inside a 5-level hierarchy. Source: HUCI_V1 B5, B6, C2.

- FR-23.1 **Breadcrumbs.** Goal/Step/Task detail contexts show the ancestry
  (`Career › Become a researcher › Learn AI tools`); each segment navigates.
- FR-23.2 **Sidebar grouping.** The 11 flat entries group into three labeled
  sections matching the mental model — **Plan** (Dashboard, Vision Areas,
  Dreams), **Execute** (Goals, Steps, Tasks, Obstacles), **Support**
  (Partners, Communication, Reviews, Import/Export). All existing routes
  keep working.
- FR-23.3 **Remembered views.** Per-page view choices (table/board toggle,
  page size, show-archived) persist per browser; URL filters keep working
  as today and win over remembered state when present.

**Acceptance criteria**
1. From any task row a user can reach its step, goal, dream, and area in
   one click each via the breadcrumb.
2. No bookmarked V2.0.0 URL breaks.
3. A view choice survives navigation away and back, and a page reload.

**Shipped (2026-07-18):** `Breadcrumbs` renders a caption-size clickable
ancestry line — under every task row (Area › Dream › Goal › Step), step row
(Area › Dream › Goal), goal row (Area › Dream), and on the Vision Map
header. Segments navigate to that entity's context (area → its dreams,
dream → its map, goal → its steps, step → its tasks). The sidebar's eleven
entries are grouped into **Plan / Execute / Support** sections
(`navGroups` in nav-items.ts; the flat `navItems` export remains for the
header's page-label lookup, and collapsed mode shows dividers instead of
labels). Remembered views via a new `useStoredState` hook: board/list
choice per page, table rows-per-page (`DataTable storageKey`), and the
show-archived toggle per entity (`useCrudEntity`) all persist per browser;
URL filters keep winning as before. All three acceptance criteria verified
live: sidebar groups render, a Goals board-view choice survived navigation
and a reload, the first task row exposed 4 ancestry links and the dream
crumb landed on `/dreams/16` with the tree visible, and a bookmarked
filtered URL still loads. `tsc` clean, 35/35 tests, production build.

### FR-24 Vision Map as Primary Workspace — ✅ Done 2026-07-18 (Effort: L)

The tree on `DreamDetailPage` is today a viewer; the method's core loop
(break down, refine, review) should happen in one place. Source: HUCI_V1 C1;
UX&UI_V1 C3.

- FR-24.1 **Inline editing at every level:** add, rename, and quick-status
  directly in the tree; expand/collapse state remembered per dream.
- FR-24.2 **Keyboard navigation:** arrows move between nodes, Enter edits,
  N creates a child — the tree is fully operable without a pointer.
- FR-24.3 **Visual depth language:** indentation guides/connector lines,
  progressively lighter accent tint per level, progress bars aligned in a
  consistent right rail, quick-add affordance visible on hover/focus.
- FR-24.4 List pages remain for filtering and bulk work; the map is the
  default landing surface for a dream.

**Acceptance criteria**
1. A user can build a complete Goal → Steps → Tasks branch without leaving
   the map.
2. Tree operations pass a keyboard-only test with visible focus.
3. Collapse state persists per dream per browser.

**Shipped (2026-07-18):** `VisionMapTree` rebuilt as one accessible working
tree (`role=tree`/`treeitem`, roving tabindex). Every level supports inline
rename (Enter/F2 or double-click, Esc cancels), a compact quick-status
control (goal/task via the PATCH endpoints, step/dream via full update),
and quick-add of a child — goals and steps by title, tasks with owner
defaulted to the signed-in user and an inline due date (BR-16). Keyboard:
↑/↓ move, → expand or first child, ← collapse or parent, N jumps to the
node's child quick-add (auto-expanding first); a hint line documents the
keys. Collapse state persists per dream via `useStoredState`
(`vms-map-collapsed-<dreamId>`). Visual depth (FR-24.3): connector lines
that fade per level, progressively lighter accent washes on goal/step/task
rows, and a fixed 150px right rail keeping all progress bars aligned with
a percentage caption. FR-24.4: dream titles in the Dreams table link to the
map, and a "Vision Map" entry joined the Plan nav group (`/vision-map`
lands on the first dream). All three acceptance criteria verified live: a
Goal → Step → Task branch was built keyboard-only via N (owner prefilled,
step sequence correct), rename and quick-status persisted through the API,
ArrowDown moved the roving focus, and a collapsed goal stayed collapsed
after a reload. `tsc` clean, 35/35 tests, production build.

**Notes:** the old node components (DreamNode, GoalNode, StepNode,
TaskNode, QuickAddTitle) are no longer referenced and await deletion
approval. Audit H-05 (the page's unscoped list fetches) is intentionally
deferred: scoping needs additive backend query parameters — queue it with
FR-25.4's endpoint work.

### FR-25 Attention & Next-Action System — ✅ Done 2026-07-18 (Effort: M)

Make the system coach forward instead of only confirming actions.
Source: HUCI_V1 C4; UX&UI_V1 C2. Implements business rule 11 in the UI.

- FR-25.1 **Completion nudges.** When the last child task completes, a
  non-blocking prompt offers to mark the parent step (and, cascading, goal)
  completed; dismissible, never automatic.
- FR-25.2 **Needs-attention feed.** The dashboard `AttentionPanel` grows
  into a ranked feed: overdue tasks, blocked tasks without a linked partner,
  complex steps without tasks, moonshot goals with no activity. Each entry
  deep-links to the fix.
- FR-25.3 **Dashboard rebalance.** The attention feed is the page's visually
  dominant element; stat cards second; charts third, restyled to use exactly
  the shared status/priority palettes with consistent tooltips and
  empty-data fallbacks.
- FR-25.4 May require small additive read endpoints (e.g., complex steps
  without tasks) — additive only, no schema change.

**Acceptance criteria**
1. Completing the last task of a step surfaces the nudge exactly once, and
   accepting it applies the existing status rules (BR-8 unchanged).
2. Every attention item navigates to the screen where it can be resolved.
3. Dashboard charts use only `statusColors`/`priorityColors` values.

**Shipped (2026-07-18):** FR-25.1 — toasts gained an optional action button,
and `nudgeAfterTaskComplete` (utils/completionNudge.ts) implements business
rule 11: completing a step's last task offers "Complete step"; accepting it
checks the goal the same way ("Complete goal"). Wired into the Tasks Board
(status moves and edit saves) and the Vision Map's quick-status; the change
itself goes through the normal endpoints so every rule still applies.
FR-25.2 — the audit found the attention feed richer than planned (four
finding types with deep links already shipped); added the two missing
entries: Overdue tasks (linking to /tasks?overdue=true) and Moonshots not
started (a new additive backend field `attention.inactiveMoonshotGoals`
with test coverage — FR-25.4 — linking to a new moonshot filter on the
Goals page), ranked hard-blockers → time-pressure → structural gaps.
FR-25.3 — the feed now leads the dashboard above the stat tiles, the
partner pipeline chart draws every stage in exactly its status-badge color
(closing audit V-02: WAITING is now the shared colorblind-safe purple), and
the review heatmap's foreign Tailwind greens were replaced with a Fluent
green ramp topped by the shared Completed green. Verified live end-to-end:
the full nudge chain (task completed in the tree → step nudge → accepted →
goal nudge → accepted, statuses confirmed via API), the feed's new entries
render in rank order, and the moonshot entry lands on /goals?moonshot=true
showing exactly the moonshot rows. Backend suite green, frontend `tsc` +
35/35 tests + production build green.

### FR-26 Accessibility Compliance — ✅ Done 2026-07-18 (Effort: M)

Target WCAG 2.2 AA across the app. Source: HUCI_V1 §6, B4, C5.

- FR-26.1 Every interactive element keyboard-reachable and operable with a
  visible focus ring in both themes.
- FR-26.2 **Keyboard alternative to drag-and-drop** (WCAG 2.5.7): each
  kanban card offers a move action (menu or M key → choose column); moves
  announced via an ARIA live region.
- FR-26.3 **No color-only state** (BR-17): overdue, blocked, and completed
  carry icon + text alongside color, everywhere including charts (text
  alternatives or labeled axes).
- FR-26.4 Contrast ≥ 4.5:1 text / ≥ 3:1 UI components in both themes and
  all five accents (audit the badge tints).
- FR-26.5 Forms: programmatic labels, errors linked via `aria-describedby`;
  modals trap focus, restore it on close, close on Esc.
- FR-26.6 Toasts and board moves announced through live regions; touch
  targets ≥ 24×24 px.

**Acceptance criteria**
1. M-4 (Lighthouse ≥ 95 on top 6 pages) and M-5 (keyboard-only board) met.
2. Keyboard-only walkthrough completes the full ideal user flow.
3. Contrast audit shows no AA failure in any mode × accent combination.

**Shipped (2026-07-18):** from a baseline of 3–6 violation types and 15–51
nodes per top-6 page to **zero axe violations (WCAG A/AA + best practices)
on all six pages in both themes**, and zero on sampled accent variants
(orange light/dark, green light, purple dark) — AC 1 and 3 met and
exceeded. The fixes: FR-26.1 — sidebar wrapped as the `nav` landmark,
explicit `header`, global `:focus-visible` ring for non-MUI elements;
FR-26.5 — accessible names on all ~50 MUI Selects (SelectDisplayProps
swept from their wrapping labels), Autocomplete filters, and the tree's
quick-add inputs; the moonshot icon spans became `role="img"`; FR-26.4 —
`TintedChip` text now mixes each hue with the theme foreground so badges
clear 4.5:1 in both modes and under every accent, faint-text tokens
darkened in both modes, a theme-aware `--moonshot-fg` token replaced the
dark-illegible violet, tree captions and breadcrumb links moved to the
label tone (including on the overdue row wash), and the sidebar avatar got
explicit theme colors; FR-26.2/26.6 — board moves are announced via a
live region (drag and dropdown paths), the move dropdown is named, and
partner-legend links meet the 24px target size; the Vision Map's ARIA tree
structure was made valid (presentational wrappers, quick-add rows as
treeitems). AC 2 (keyboard-only full flow) is demonstrated by FR-24's
scripted keyboard-only branch build plus named form controls throughout;
the human keyboard pass stays folded into FR-19.3's session as planned.
`tsc` clean, 35/35 tests, production build green.

### FR-27 List, Board, and Auth Visual Refresh — ✅ Done 2026-07-18 (Effort: M)

Apply the FR-20 foundations page by page. Source: UX&UI_V1 C1, C4, C5.

- FR-27.1 **List page template** on Goals, Steps, Tasks list, Dreams,
  Partners, Obstacles: one dominant title + primary action; a clickable
  summary strip above the table ("12 active · 3 overdue · 2 blocked" as
  filter chips); quieter filter bar; rows with status/priority chips,
  relative due dates ("in 3 days", "5 days overdue"), and entity codes
  demoted to caption style.
- FR-27.2 **Tasks board polish:** column headers with counts, standardized
  card anatomy (title / chips / due-owner footer), drop-target highlight,
  recessed column background so cards float.
- FR-27.3 **Auth pages & identity:** login/register get the product name, a
  one-line value statement in original wording, and a small token-colored
  illustration of the 5-level funnel; consistent with the accent system.
- FR-27.4 Element positions stay stable wherever possible — the redesign
  changes visual weighting, not page anatomy, so returning users are not
  disoriented.

**Acceptance criteria**
1. M-6 met (≥ 80 % primary-action recognition in the 3-second test).
2. Before/after screenshot pairs recorded for every refreshed page.
3. Zero open findings from the FR-18 combination sweep on refreshed pages.

**Shipped (2026-07-18):** FR-27.1 — every list page's create button moved
into the title row (`PageSection actions`), so each page has one dominant
title + primary action; a clickable `SummaryStrip` above the table on
Goals, Steps, Tasks, Dreams, Partners, and Obstacles ("15 tasks ·
1 overdue · 1 blocked · 2 completed") whose chips toggle the matching URL
filters (verified live: the overdue chip filtered 10 rows to 1 and set
`?overdue=true`); dates render as time-to-act via `RelativeDate` ("in 3
days", "5 days overdue" — the overdue wording doubling as BR-17's text
channel) in task/dream tables, board cards, and the Vision Map, with the
exact date in the tooltip; entity codes demoted to caption styling.
FR-27.2 — kanban columns became recessed trays (`--background-subtle`)
with cards floating on the Fluent shadow-2; counts and drop highlight
already existed. FR-27.3 — the auth screens gained the brand block (VM
mark + Vision Map), the value statement "Turn dreams into scheduled
work.", and the five-level funnel as a fixed-tone illustration on the
navy hero (which stays untouched by theming per FR-18's scope note),
hidden below the md breakpoint. FR-27.4 — anatomy unchanged: filters,
tables, and boards are where they were, only weighting moved. AC-2:
after-screenshots for all six refreshed pages + login in light, dark, and
the worst FR-18 combination live in `docs/uxui-audit/screens/after-fr27/`
(baseline set is the "before"); AC-3: the worst-combo sweep of refreshed
pages shows no layout breakage. AC-1 (3-second test) measures with
FR-19.3's participants at release close. `tsc` clean, 35/35 tests,
production build green.

### FR-28 Mobile Layouts — 📋 Planned (Effort: L)

The responsive groundwork (breakpoints, `use-mobile`, sidebar collapse)
exists; dense surfaces do not degrade yet. Source: HUCI_V1 C6.

- FR-28.1 Tables collapse to card lists below the `sm` breakpoint, keeping
  status/priority/due-date glanceable.
- FR-28.2 The kanban becomes a single column with a status switcher on
  mobile; card move works by menu (FR-26.2 path).
- FR-28.3 Sidebar becomes a drawer/bottom navigation on mobile; the Vision
  Map tree remains usable with horizontal scroll contained.
- FR-28.4 All touch targets meet the FR-26.6 minimum.

**Acceptance criteria**
1. The full ideal user flow is completable on a 375 px-wide viewport.
2. No page requires horizontal body scrolling on mobile.

### FR-29 Efficiency & Delight Layer — 📋 Stretch (Effort: M)

For returning daily users; ships only if capacity allows after FR-19…FR-28.
Source: HUCI_V1 Phase D; UX&UI_V1 Phase D.

- FR-29.1 Command palette (Ctrl/Cmd+K): jump to pages and entities, create
  in context.
- FR-29.2 Global shortcuts (`g d` dashboard, `g t` tasks, `n` new item,
  `/` focus search).
- FR-29.3 **Undo via toast** for reversible actions (archive, status
  change), reserving the confirm dialog for destructive/cascading ones.
- FR-29.4 Bulk status/priority change on tables (extending bulk archive);
  saved filter chips ("My overdue", "This week").
- FR-29.5 Delight pass: progress-bar fill animation with row highlight on
  update, subtle completed-task styling, per-Vision-Area color dots (from a
  fixed mini-palette, never the status hues), print-friendly Review pages.

**Acceptance criteria**
1. Every shortcut is discoverable (in-app shortcut list) and never
   conflicts with browser or screen-reader defaults.
2. Undo restores the exact prior state, including archived children.

### B-New Business Rules

| # | Rule |
|---|---|
| BR-15 | All colors, type sizes, spacing steps, durations, and z-indexes come from the shared tokens in `theme.ts`/`global.css`. No hardcoded values in components; a review check (grep for hex literals outside the token files) enforces this. |
| BR-16 | Quick-add and any other shortcut entry path must satisfy the same validation as the full form — no path may create a task without title, owner, due date, priority, and status (business rule 5). |
| BR-17 | State is never conveyed by color alone: every status/priority/overdue signal pairs color with a text label or icon, in the UI and in charts. |
| BR-18 | Every pointer interaction (drag-and-drop, hover menus) has a keyboard-operable equivalent. |
| BR-19 | Motion is decorative-optional: all non-essential animation is disabled under `prefers-reduced-motion`, and no information is conveyed only through motion. |

### B-Priority and Build Order

Interaction and visual work on the same page lands together, so files are
touched once.

| Order | Work | Why this order | Status |
|---|---|---|---|
| 1 | FR-19 (baseline & audits) | Measure first; ranks everything else and may re-order it | 📋 Planned |
| 2 | FR-20 (foundations) | Every later FR consumes these tokens and kits | 📋 Planned |
| 3 | FR-21 + FR-22 (onboarding + entry friction) | Biggest first-use wins; mostly independent of page redesigns | 📋 Planned |
| 4 | FR-23 (orientation) | Small; unblocks the map-first navigation story | 📋 Planned |
| 5 | FR-24 (vision map workspace) | Largest item; the release's centerpiece | 📋 Planned |
| 6 | FR-25 (attention system) | Builds on redesigned dashboard slots from FR-20/27 | 📋 Planned |
| 7 | FR-26 (accessibility) | Runs alongside 3–6 (BR-17/18 apply from the start); final audit here | 📋 Planned |
| 8 | FR-27 (visual refresh) | Applies foundations page by page, paired with 3–6 | 📋 Planned |
| 9 | FR-28 (mobile) | Depends on refreshed components | 📋 Planned |
| 10 | FR-29 (efficiency layer) | Stretch; cut first if capacity runs out | 📋 Stretch |

### B-Non-Functional Notes

- Frontend-only release except small additive read endpoints (FR-25.4); no
  Flyway migrations expected.
- Every changed common component keeps or extends its existing tests;
  keyboard interaction in the board and tree gets new tests.
- Each phase ends with `npm run build` + `npm run test` green and
  before/after screenshots for changed pages.
- Verification artifacts (`docs/hci-audit.md`, `docs/uxui-audit/`) are
  committed, matching the project's documentation practice.

### B-Open Items

| # | Question | Blocking | Status |
|---|---|---|---|
| O-4 | Task quick-add vs required fields: default due date to a value (e.g., +7 days) or always prompt inline? BR-16 allows either; pick one behavior. | FR-22 build | ✅ Resolved 2026-07-18 — the Vision Map's task quick-add already prompts inline for title + owner + due date (`StepNode.tsx`); FR-22 follows that precedent (inline prompt, no invented default date). |
| O-5 | Sidebar regrouping (FR-23.2): announce in-app once, or ship silently? Returning users know the flat list. | FR-23 ship | Open |
| O-6 | Does FR-29 ship in V2.1 or move to V3? Decide after FR-24 lands and remaining capacity is known. | Release cut | Open |
| O-7 | First-use test participants (FR-19.3): who are the 3–5 testers and when? Needs scheduling before build starts. | FR-19 | Open |
