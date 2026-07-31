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

---

## [4.0.0] — 2026-07-25 (7 of 7 items shipped)

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

All verified with backend and frontend test suites (backend 126/126, frontend
127/127).

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

### Known issues
- The light-mode page canvas has always rendered `#ffffff`, not the `#fafafa`
  that `--page` specifies, because nothing paints `--page`. Cards therefore do
  not stand out from the page in light mode. Pre-existing and cosmetic; FR-40
  deliberately did not correct it, since AC-3 requires the Neutral tone to be
  byte-identical to what shipped. It also means Flat and Neutral look nearly
  alike in light mode — there is no canvas step for Flat to remove.
- Anonymous requests to protected endpoints answer **403**, not **401**:
  `SecurityConfig` configures no `authenticationEntryPoint`, so Spring's default
  access-denied handling responds. Pre-existing and app-wide; changing it would
  affect every endpoint and the frontend's redirect-to-login behaviour, so it was
  left alone rather than altered as a side effect of FR-39.
- The five accents added by FR-39 clear 4.5:1 against their own label text in
  every state (main, hover, pressed, both modes). The five original accents do
  not on their transient states — Orange's light hover measures 3.47:1. Raising
  them is a recolour FR-39 does not claim; the theme tests therefore assert 4.5:1
  at rest and 3:1 on hover/pressed.

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
