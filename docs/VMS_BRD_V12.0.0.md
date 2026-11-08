# BRD – Test Coverage to 80%

Nov 7, 2026

## Document control

This is draft 12.0.0. It keeps every requirement in BRD 11.0.0 (AI Plan Builder Improvements), BRD 10.0.0 (Security Hardening to the OWASP Top 10), BRD 9.0.0 (Dream Conversion Program with Claude AI Integration) and earlier versions. Where this BRD is silent, the earlier BRDs still apply.

V12 adds no new features for participants, coaches or admins. It raises automated test coverage of the whole project, as measured by SonarQube Cloud, from **56.7% to more than 80%**, and keeps it there. Product behaviour, security, consent and AI rules stay as they are.

| Item | Value |
| --- | --- |
| Document | Business Requirements Document (BRD) – test coverage addendum |
| Version | 12.0.0 – Draft |
| Parent documents | BRD 11.0.0 (7 Nov 2026), BRD 10.0.0 (6 Nov 2026) |
| Business owner | To be confirmed |
| Product owner | To be confirmed |
| Approvers | Sponsor, Product owner, Engineering lead, QA lead |

| Version | Change | Author |
| --- | --- | --- |
| 12.0.0 | First draft: coverage baseline from SonarQube Cloud at commit `d76e328`, requirements COV-FR-01 to COV-FR-30, phased plan and acceptance criteria | BA |
| 12.0.0 | Phase 1 done (7 Nov 2026): Vitest 5 figure confirmed as the accurate baseline, coverage minimums in CI for both sides, exclusion list, coverage report script, shared test helpers | BA |
| 12.0.0 | Phase 2 done (7 Nov 2026): frontend foundations at 80.7%; API contract snapshot; two bugs found and fixed (descending sort put empty values first; synergy note box unnamed for screen readers) | BA |
| 12.0.0 | Phase 3 done (7 Nov 2026): core method path at 81.3%; four bugs found and fixed (unnamed search box; silent quick-add failure on Tasks; no create button on an empty Plan page; silent goal and task add failures in the Vision Map) | BA |
| 12.0.0 | Phase 4 done (7 Nov 2026): supporting pages at 86.4%; three bugs found and fixed (Insights kinds and the Work Style result stayed in English in Khmer; AI controls showed while AI was off on the Communication, Reviews and pitch module pages) | BA |
| 12.0.0 | Phase 5 done (7 Nov 2026): coach, admin and account pages at 82–100%; backend 80.2% → 88.1% with no controller below 80%; whole project about 84.5%. Claude client tested against a local stub server; no bugs found in this phase | BA |
| 12.0.0 | Phase 6 (7 Nov 2026): SonarQube Cloud on `main` at commit `f7e7666`: whole project 85.1%, backend 88.1%, frontend 83.3%, quality gate OK; final thresholds in place; both suites pass with outside network access blocked; README and SECURITY updated. Open: 10 clean CI runs in a row (CAC-09) and the QA lead's sample review (CAC-11) | BA |

## Executive summary

On 7 Nov 2026 the project was connected to SonarQube Cloud. Bugs, vulnerabilities and code smells are now at zero, and the quality gate passes. Test coverage is the one weak measure: **56.7%** for the whole project.

The two halves of the system are very different:

- **Backend (Java): 80.2%.** 465 tests. Most business rules are covered.
- **Frontend (React): 42.0%.** 507 tests, but 20 of the 44 pages have no tests at all, including the core method pages (Steps, Dream Detail, Plan, Partners) and the Dashboard.

Low coverage has already cost the project. On 7 Nov 2026 alone:

- The backend CI job had failed on every run since CI was added. A timing bug that only showed on Linux was hidden by the red build.
- Eleven Dependabot updates waited for review because nobody could tell from the tests whether they were safe.
- A code-quality fix to one email check introduced a new bug in the same line; it was caught only by SonarQube, not by a test.

The SonarQube quality gate already requires 80% coverage on changed code. That protects new work but leaves 6,847 old lines and 5,709 old branches untested. Every future change to those parts runs without a safety net.

V12 closes that gap in six phases, starting with the parts participants use most: the Vision Area → Dream → Goal → Step → Task path. It also makes CI fail if coverage ever falls back, so the gain is kept.

## Business objectives

| ID | Objective | Measure |
| --- | --- | --- |
| CBO-01 | Catch regressions before they reach participants | Whole-project coverage above 80% on SonarQube Cloud; frontend and backend each at 80% or more |
| CBO-02 | Make dependency and framework upgrades safe to accept | Dependabot pull requests can be judged by CI alone, with no manual click-through for routine updates |
| CBO-03 | Protect the method's business rules (`CLAUDE.md`) | Every rule in `CLAUDE.md` "Core Business Rules" and "Testing Requirements" has at least one automated test |
| CBO-04 | Keep the gain | CI fails when coverage falls below the agreed level |

## Scope

### In scope

- Automated tests for frontend pages, components, hooks, API modules and utilities.
- Automated tests for backend services, controllers and support classes below target.
- Coverage thresholds in CI for both frontend and backend, raised phase by phase.
- Shared test helpers so page tests are quick to write and read.
- A check that the coverage number is measured correctly after the Vitest 5 upgrade.
- Small changes to production code **only** where needed to make it testable (for example, injecting a clock or an HTTP client), with no change in behaviour.

### Out of scope

- New features or changes in behaviour. A test that finds a real bug records it; the fix follows the normal bug process and may ship in the same phase.
- Raising coverage by excluding code from measurement, except the short list in COV-FR-04.
- Counting Playwright end-to-end tests (`frontend/e2e/`) toward the SonarQube number. They stay as they are (see open questions).
- Calls to the real Claude API, Google OAuth or a real mail server in tests.
- Performance, load and penetration testing.

## Baseline: current state (7 Nov 2026)

Measured by SonarQube Cloud on commit `d76e328`. SonarQube's **Coverage** combines lines and branches (conditions): covered items ÷ all items.

### Whole project

| Measure | Value |
| --- | --- |
| Coverage | **56.7%** |
| Line coverage | 61.5% (6,847 of 17,770 lines not covered) |
| Branch coverage | 49.3% (5,709 of 11,253 branches not covered) |
| Coverage on new code (quality gate, needs 80%) | 98.4% |
| Items still to cover to reach 80% | about 6,750 lines and branches |

### By area

| Area | Files | Coverage | Lines and branches not covered | Notes |
| --- | --- | --- | --- | --- |
| Backend, all | 347 | **80.2%** | 2,225 | 465 tests; strongest area |
| – `service/ai` | 25 | 77.5% | 1,012 | `PlanAssistantService` 66%, `AiContextBuilder` 60%, `ClaudeClient` 6% |
| – `service` | 44 | 81.3% | 725 | `VisionAreaService` 51%, `TaskItemService` 65%, `ReviewService` 68% |
| – `controller` | 47 | 62.8% | 145 | 12 controllers below 50%, e.g. `TaskItemController` 31% |
| – `service/mail` | 2 | 30.2% | 37 | `PasswordResetMailer` 3% |
| Frontend, all | 229 | **42.0%** | 10,331 | 73 test files, 507 tests |
| – pages | 44 | **27.9%** | 5,691 | 20 pages at 0%, 6 between 29% and 36% |
| – `components/vision-map` | 4 | **15.2%** | 1,197 | `VisionMapTree.tsx` alone has 1,011 uncovered items |
| – `components/common` | 41 | 53.1% | 619 | `StatusBoard` 0%, `DataTable` 63% |
| – `api` | 41 | 20.1% | 474 | most API modules never called in tests |
| – `components/layout` | 10 | 4.8% | 414 | `Sidebar`, `Header`, `GlobalShortcuts` at 0% |
| – `components/dashboard` | 12 | 11.6% | 350 | `DashboardSummary` and charts at 0% |
| – `components/forms` | 9 | 0.4% | 242 | `DreamWizard`, `VisionAreaWizard` |
| – `hooks` | 7 | 23.9% | 185 | `useCrudEntity` 15% (used by every list page) |
| – `components/plan-builder`, `components/ai` | 31 | about 78% | 840 | close to target |

### Frontend pages

| Coverage | Pages |
| --- | --- |
| 0% (20) | AccountLinking, Assessment, CoachParticipant, Cohorts, CommunicationBuilder, Dashboard, DreamDetail, ImportExport, Insights, IssueReports, Journal, ModuleContent, Modules, OAuthGoogleError, OAuthGoogleStart, PartnerDetail, Partners, Plan, Steps, WorkStyle |
| 29% to 36% (6) | Dreams, Goals, Obstacles, Reviews, TasksBoard, VisionAreas |
| 60% and above (18) | AccountSettings, AiSettings, AppearanceSettings, Coach, DreamCoach, ForgotPassword, InterfaceText, Login, ModuleDetail, PageSection, PlanBuilder, Register, ResetPassword, SecurityLog, SecuritySettings, SharedPlan, Today, UndatedSteps |

### Measurement note

Coverage was 62.6% at commit `3780610` and 56.7% at `d76e328`. The only change between them was the upgrade from Vitest 3 to Vitest 5; no tests were removed. Vitest 5 maps coverage back to the source differently and reports fewer frontend lines as covered for the same tests. COV-FR-01 confirms which figure is right before any target is judged.

**Finding (Phase 1, 7 Nov 2026): the Vitest 5 figure is the accurate one; 56.7% is the baseline.** The same 507 tests were measured with both versions:

| | Vitest 3 | Vitest 5 |
| --- | --- | --- |
| Lines counted | 29,334 (every physical line, including blank lines, comments, types and closing tags) | 8,628 (only statements that can run) |
| Branches counted | 3,661 | 7,699 |
| `StepsPage.tsx` (no tests) | 709 lines, 1 branch, 1 function | 274 lines, 187 branches, 108 functions |
| `aiApi.ts` (imported, never called) | 13 lines "covered" by being imported | 1 statement covered |

Vitest 3 treated a file no test ran as one branch and one function, so it hid most untested branches, and it counted lines as covered when a file was only imported. Vitest 5 counts every statement and branch. The baseline, targets and phase figures in this BRD stand.

## Target state

| Area | Now | Target |
| --- | --- | --- |
| **Whole project** | 56.7% | **above 80%** (planning figure 83%) |
| Backend | 80.2% | 85% or more |
| Frontend | 42.0% | 80% or more |
| Frontend pages | 27.9% | 80% or more |
| Vision map components | 15.2% | 80% or more |
| Other frontend code | 59.5% | 80% or more |
| Coverage on new code | 98.4% | 80% or more (unchanged quality gate) |

If every area meets its target, the whole project reaches about 83.8%, which leaves room for later changes.

## Functional requirements

Priority: **M** (Must) ships in V12, **S** (Should) ships unless the sponsor defers it, **C** (Could) is a later improvement.

### A. Measurement and guard rails

| ID | Requirement | Traces to | Priority |
| --- | --- | --- | --- |
| COV-FR-01 | Before Phase 2 starts, the team shall confirm that the frontend coverage report from Vitest 5 is accurate: every source file is listed, and the lines marked covered match what the tests run. The reason for the drop from 62.6% to 56.7% shall be written down, and any setting that misreports coverage shall be fixed. | CBO-01 | M |
| COV-FR-02 | CI shall fail the frontend job when frontend coverage (lines and branches) falls below a set threshold, using Vitest coverage thresholds. | CBO-04 | M |
| COV-FR-03 | CI shall fail the backend job when backend coverage falls below a set threshold, using the JaCoCo `check` goal. | CBO-04 | M |
| COV-FR-04 | Only these may be excluded from coverage, each with a written reason in the config: the frontend entry file (`main.tsx`), type-only files (`*.d.ts`, files with only types), test helpers, and generated code. Any other exclusion needs the engineering lead's approval, recorded in this BRD. | CBO-01 | M |
| COV-FR-05 | Thresholds shall start at the measured level after Phase 1 and be raised at the end of each phase to the level reached, rounded down to the whole percent. They are never lowered without the product owner's approval. | CBO-04 | M |
| COV-FR-06 | Each phase shall end with a short coverage report: whole project, backend, frontend and each area in the baseline table, taken from SonarQube Cloud on `main`. | CBO-01 | M |

### B. Shared test helpers

| ID | Requirement | Traces to | Priority |
| --- | --- | --- | --- |
| COV-FR-07 | A shared frontend helper shall render a page with a router, a signed-in user (participant, coach or admin) and toast messages, so page tests do not repeat the same set-up. | COV-NFR-04 | M |
| COV-FR-08 | A shared frontend helper shall mock all API modules and return empty lists or pages by default, so a page test only sets the data it cares about. The letter rank filter test (`letterRankFilters.test.tsx`) is the model. | COV-NFR-04 | M |
| COV-FR-09 | Shared test data builders shall create valid vision areas, dreams, goals, steps, tasks, partners, obstacles and reviews with sensible defaults. | COV-NFR-04 | S |

### C. Core method path (frontend)

The Vision Area → Dream → Goal → Step → Task path is what participants use most and where the method's rules live.

| ID | Requirement | Traces to | Priority |
| --- | --- | --- | --- |
| COV-FR-10 | Tests for the **Vision Areas, Dreams, Goals, Steps and Tasks board** pages shall cover: the list loads; create, edit and archive; required-field validation; filters and sorting; empty, loading and error states. | CLAUDE.md pages 2 to 7, BR-1 to BR-5 | M |
| COV-FR-11 | Tests shall cover the method's rules as seen on screen: a dream needs a vision area, title, why, success definition, priority and target date; a task needs title, owner, due date, priority and status; a **Blocked** task needs a blocker reason; a complex step prompts for tasks; overdue and blocked tasks are highlighted. | BR-1 to BR-9 | M |
| COV-FR-12 | Tests for the **Tasks board** shall cover moving a task between status columns, including the blocked-reason prompt, and the owner, priority, due date, dream and goal filters. | CLAUDE.md page 7 | M |
| COV-FR-13 | Tests for **Dream Detail and the Vision Map tree** shall cover: the tree shows dream, goals, steps and tasks with progress; adding a goal, step and task in place; renaming; collapsing and expanding; keyboard movement through the tree; archive with its impact message; drag to reorder where it is used. | CLAUDE.md page 4, NFR-02 | M |
| COV-FR-14 | Tests for the **Plan page** and **Plan share card** shall cover each plan stage, saving, and creating and stopping a share link. | V9, V11 | M |
| COV-FR-15 | Tests for the **Dream and Vision Area wizards** shall cover each step, going back, the coaching questions for a vague dream, and the final save. | CLAUDE.md page 3 | M |

### D. Supporting pages (frontend)

| ID | Requirement | Traces to | Priority |
| --- | --- | --- | --- |
| COV-FR-16 | Tests for **Partners and Partner Detail** shall cover adding a partner, linking to a dream, goal, step or task, support type and contact status, search and filters, and the suggested partner type for each obstacle type. | CLAUDE.md page 8 | M |
| COV-FR-17 | Tests for **Obstacles** shall cover create, edit, severity, linking a required partner, and resolving. | CLAUDE.md data model 10 | M |
| COV-FR-18 | Tests for the **Communication Builder** shall cover filling each part (audience, purpose, hook, problem, request, benefit, outcome), the generated message in the agreed format, saving, and follow-up status. | CLAUDE.md page 9 | M |
| COV-FR-19 | Tests for **Reviews** shall cover daily, weekly and monthly reviews with their questions, saving, and the AI weekly draft with AI on and off. | CLAUDE.md page 10 | M |
| COV-FR-20 | Tests for the **Dashboard** shall cover every summary figure in `CLAUDE.md` page 1 (vision areas, active dreams, goals and tasks, completed, overdue, blocked, average progress, due this week, top 5 tasks, goals by status, dreams by vision area) from known data, and the empty state. | CLAUDE.md page 1 | M |
| COV-FR-21 | Tests for **Import / Export** shall cover choosing a file, the import summary (created, skipped, errors) and starting an export. | CLAUDE.md page 11 | M |
| COV-FR-22 | Tests for **Journal, Assessment, Work Style, Modules, Module Content and Insights** shall cover loading, the main action on each page, and the empty and error states. | V7 to V9 | S |

### E. Coach, admin and account pages (frontend)

| ID | Requirement | Traces to | Priority |
| --- | --- | --- | --- |
| COV-FR-23 | Tests for **Cohorts, Coach Participant and Issue Reports** shall cover what each role may see and do, and that a coach sees a participant's journals only when shared. | BR-14, D-6 | M |
| COV-FR-24 | Tests for **Account Linking and the Google sign-in pages** shall cover success and each error message. | V10 | S |

### F. Frontend foundations

| ID | Requirement | Traces to | Priority |
| --- | --- | --- | --- |
| COV-FR-25 | Tests shall cover `apiClient` (token attached, JSON and error handling, 401 handling) and, for each API module, that each function calls the right method and path with the right body. | COV-NFR-02 | M |
| COV-FR-26 | Tests shall cover the shared hooks, starting with `useCrudEntity` (load, create, update, archive, restore, errors), and the layout: `Sidebar` (items by role), `Header`, `GlobalShortcuts`. | — | M |
| COV-FR-27 | Tests shall cover the shared components below 80%, starting with `StatusBoard`, `DataTable`, `GoalSynergyDialog` and the dashboard components. | — | M |

### G. Backend gaps

| ID | Requirement | Traces to | Priority |
| --- | --- | --- | --- |
| COV-FR-28 | Tests shall cover `ClaudeClient` and `ClaudeKeyChecker` against a local stub HTTP server: a normal reply, a streamed reply, a refusal, a cut-short reply, a timeout and an error status. No real API call is made. | AI-NFR-01, COV-NFR-02 | M |
| COV-FR-29 | Tests shall raise `PlanAssistantService`, `AiContextBuilder`, `AiSafety`, `VisionAreaService`, `TaskItemService`, `ReviewService`, `CommunicationMessageService` and the mailers to at least 80%, focusing on branches that change what the user gets (limits, refusals, missing data, crisis handling). | BR-1 to BR-14, AI-NFR-03 | M |
| COV-FR-30 | Each controller below 80% shall have tests for its endpoints' normal answer, validation errors (400), no token (401) and another user's record (404). | SEC-01-08 | M |

## Non-functional requirements

| ID | Requirement |
| --- | --- |
| COV-NFR-01 | **Meaningful tests.** Every test checks behaviour a user or the business cares about: what is shown, what is saved, what is refused. A test that only renders a component without checking anything does not count toward a phase's target. Reviewers check this in code review. |
| COV-NFR-02 | **No outside calls.** Tests never call the real Claude API, Google, a mail server or the internet. Claude, OAuth and mail are mocked or stubbed, as in the existing tests. |
| COV-NFR-03 | **Speed.** The frontend CI job (install, build, tests with coverage) stays under 10 minutes and the backend job under 12 minutes, inside the current 15- and 20-minute job limits. |
| COV-NFR-04 | **Readable and maintainable.** Tests use the shared helpers (COV-FR-07 to COV-FR-09), find elements by role and label as a user would, and follow the existing naming style (a sentence describing the behaviour). |
| COV-NFR-05 | **Not flaky.** No fixed sleeps. Long page tests set their own time limit (as `PlanBuilderPage` and `InterfaceTextPage` do). The full CI run passes 10 times in a row on `main` before a phase is closed. |
| COV-NFR-06 | **No behaviour change.** Production code changes made for testability keep behaviour the same, and the existing tests still pass. |
| COV-NFR-07 | **Security tests stay.** The cross-user and no-token sweep (`AccessControlSweepTests`) keeps covering every endpoint, including any added later. |
| COV-NFR-08 | **Both languages.** Where a page's text matters to a test, at least one test per page area runs in Khmer as well as English (AI-NFR-16). |

## Implementation phases

| Phase | Scope | Requirements | Expected whole-project coverage after the phase |
| --- | --- | --- | --- |
| 1 | Measurement and guard rails: confirm the Vitest 5 figure, coverage thresholds in CI at the current level, exclusion list, shared test helpers | COV-FR-01 to COV-FR-09 | about 57% (no new tests yet; thresholds stop any drop) |
| 2 | Frontend foundations: API modules, hooks, layout, shared components, forms and wizards, dashboard components | COV-FR-15, COV-FR-25 to COV-FR-27 | about 64% |
| 3 | Core method path: Vision Areas, Dreams, Dream Detail and Vision Map tree, Goals, Steps, Tasks board, Plan page and share card | COV-FR-10 to COV-FR-14 | about 72% |
| 4 | Supporting pages: Partners, Partner Detail, Obstacles, Communication Builder, Reviews, Dashboard, Import / Export, Journal, Assessment, Work Style, Modules, Insights | COV-FR-16 to COV-FR-22 | about 79% |
| 5 | Coach, admin and account pages; backend gaps (Claude client, plan assistant, services, controllers, mailers) | COV-FR-23, COV-FR-24, COV-FR-28 to COV-FR-30 | about 83% |
| 6 | Lock in: raise thresholds to the final level, 10 clean CI runs, final coverage report, update `docs/SECURITY.md` and the README test section | COV-FR-05, COV-FR-06, COV-NFR-05 | above 80%, kept by CI |

The expected figures assume each phase brings its areas to the target in the "Target state" table. They are planning figures, worked out from SonarQube's per-file data on `d76e328`; the real figure after each phase is taken from SonarQube (COV-FR-06).

Each phase ends with the coverage report, raised thresholds, and a stop for the product owner to confirm before the next phase starts (`CLAUDE.md` working rule 15).

### Implementation status

| Phase | Status |
| --- | --- |
| 1 | Done, 7 Nov 2026. COV-FR-01: the Vitest 5 figure is accurate (see the measurement note). COV-FR-02 and COV-FR-05: Vitest thresholds in `frontend/vite.config.ts` at lines 41%, statements 41%, branches 40%, functions 36% (measured 41.5%, 41.5%, 40.5%, 36.4%). COV-FR-03: JaCoCo `check` in `backend/pom.xml` at lines 85%, branches 68% (measured 85.6%, 68.4%), as the properties `coverage.line.minimum` and `coverage.branch.minimum`. Both checks were shown to fail when the minimum is raised (CAC-04, locally). COV-FR-04: exclusions limited to tests, test helpers, `*.d.ts`, the type-only `src/types/**` and `src/main.tsx`, each with its reason in the config; SonarQube excludes `frontend/src/main.tsx` from coverage. COV-FR-06: `node scripts/coverage-report.mjs [--markdown]` prints this BRD's area table from SonarQube Cloud. COV-FR-07 to COV-FR-09: `src/test/render.tsx` (`renderPage`, `signedIn`, `CurrentLocation`), `src/test/apiMocks.ts` (`answerEmptyLists`, `emptyPage`, `pageOf`) and `src/test/builders.ts` (a valid record for each entity); `AuthContext` is exported for them, with no change in behaviour; `letterRankFilters.test.tsx` now uses them. Whole-project coverage unchanged at 56.7%, as planned |
| 2 | Done, 7 Nov 2026. Frontend outside pages and the vision map: 58.8% → **80.7%** (target 80%); whole frontend 41.5% → 53.3% lines; 599 frontend tests in 84 files, 36 seconds with coverage. COV-FR-25: `apiContract.test.ts` calls all 233 API functions with sample arguments picked from their parameter names and records method, path, body and token for each in a snapshot, so an unintended change to any route fails the test; a second test checks every request made with a session token carries it. COV-FR-26: `useCrudEntity` (load, create versus update, archive with Undo, bulk actions with partial failures, show archived); the layout through `AppLayout` (sidebar by role, current page, header title and greeting, log out, collapse, partner shortcut, "g" + letter, "n", "?", Ctrl+K, issue report). COV-FR-27: `StatusBoard` (columns, move by menu and by drag, archived cards, phone view), `DataTable` (empty values, yes/no sort, server paging, drag to reorder, phone cards), `QuickAddRow`, `RowActionsMenu`, `CrudModalForm`, `GoalSynergyDialog`, the dashboard cards, `I18nContext`, `AssessmentExplainCard`, `ApprovedResourcesCard`. COV-FR-15: both wizards. BR-11 completion nudges (`completionNudge`). Frontend thresholds raised to lines 53%, statements 52%, branches 49%, functions 50%. **Bugs found and fixed:** (1) `DataTable` put rows with no value (for example no due date) first when a column was sorted descending; empty values now stay last in both directions, as the code intended. (2) `Textarea` passed `aria-label` to MUI's outer wrapper, so the goal synergy note box had no name for screen readers; it now reaches the text box, as in `Input`. **Gap noted, not changed:** on a dream tree (`/dreams/:id`) the Vision Map menu item is highlighted but not marked `aria-current`, so screen readers are not told the current section |
| 3 | Done, 7 Nov 2026. The core method path, all at 80% or more: Vision Areas 83.0%, Dreams 80.0%, Dream Detail 89.2%, Goals 80.8%, Steps 82.9%, Tasks board 82.6%, Plan 86.2%, `VisionMapTree` 16.8% → 81.2%, plan share card 88.4%, commitment card 90.7%; these files together 21.1% → 81.3%. Whole frontend 53.3% → 69.7% lines; 729 frontend tests in 93 files, 58 seconds with coverage. COV-FR-10 to COV-FR-12: list, create, edit, archive with impact, bulk status and priority, filters, board moves, reorder within the parent only, and the method's rules on screen: the dream coaching questions (CLAUDE.md "If dream is vague"), the decision gate for high-priority moonshots (FR-55.1), required owner, due date, priority and status for tasks (BR-5), a blocker reason and partner suggestion for blocked tasks (BR-6), complex steps prompted to break into tasks (BR-7), overdue and blocked highlighting (BR-9), completion suggestions (BR-11), and the "who helped" suggestion on completing a goal or dream. COV-FR-13: the Vision Map overview and tree (adding at every level, status changes, edit forms for all four levels, archive, restore, permanent delete, rename, keyboard navigation, collapse, drag to reorder). COV-FR-14: plan stages, milestones, PDF, share links. Frontend thresholds raised to lines 69%, statements 68%, branches 64%, functions 64%. **Bugs found and fixed:** (3) the search box on every list page had no name for screen readers (`SearchBar` put its label on the outer wrapper); (4) a failed quick add on the Tasks board showed nothing; (5) `EmptyState` dropped its button in the compact form, so the Plan page gave a participant with no dreams no way to create one; (6) in the Vision Map tree, a failed goal or task add showed nothing (a failed step add already did). **Gaps noted, not changed:** the Dreams list does not highlight overdue dreams (the board and the Goals list do); `components/vision-map/QuickAddTitle.tsx` is not used anywhere and could be removed |
| 4 | Done, 7 Nov 2026. The 13 supporting pages, all at 80% or more: Partners 81.5%, Partner Detail 95.4%, Obstacles 86.1%, Communication Builder 85.2%, Reviews 87.4%, Dashboard 80.4%, Import / Export 90.2%, Journal 89.1%, Assessment 90.6%, Work Style 91.5%, Modules 93.1%, Module Content 89.6%, Insights 88.9%; these files together 0–36% → 86.4%. Whole frontend 69.7% → 82.0% lines (lines and branches together 78.7%); 887 frontend tests in 106 files, about 84 seconds with coverage, passing on three full runs in a row. Whole project, estimated locally from the backend's Phase 3 figure: about 79% (planned 79%); the SonarQube figure follows the next push to `main`. COV-FR-16: adding a partner with links to a dream, goal, step and task, support type and contact status, server-side search and filters, the board, the integrity check before a financial or technical partner first becomes Active (FR-50), the partner detail page with its recruitment stage, linked work, vetting record and message history. COV-FR-17 and the obstacle half of COV-FR-16: create, edit, severity, rank, required partner, resolving with a root cause, accepting with three alternatives, the suggested partner type for each obstacle type (CLAUDE.md page 8), lessons from past obstacles of the same type, the conflict worksheet and turning criticism into a task. COV-FR-18: each part of the message, the generated message in the agreed format (CLAUDE.md page 9) and in the constructive feedback tone, saving, follow-up status, board moves and the AI draft. COV-FR-19: the daily, weekly, monthly and quarterly questions, the all-or-none diligence checkup, saving, and the AI weekly draft with AI on and off. COV-FR-20: every summary figure from known data, the top priority tasks with overdue marked, dreams by vision area, goals by status, partner pipeline, review cadence, period and vision area scope, the empty and first-run states. COV-FR-21: choosing a workbook, the import summary (created, skipped, each error, backup file) and the dated export download. COV-FR-22: Journal, Assessment, Work Style, Modules, Module Content (including the AI draft and Khmer translation) and Insights: loading, the main action, empty and error states. COV-NFR-08: each page has a test in Khmer. Frontend thresholds raised to lines 82%, statements 80%, branches 75%, functions 74%. **Bugs found and fixed:** (7) the Insights kind labels (Lesson learned, Root cause, Creative alternatives) stayed in English in Khmer although their translations existed; (8) the Work Style result (style name, strengths, blind spot, tip) stayed in English in Khmer; two new Khmer strings, `Dominant: {style}` and `Secondary: {style}`, need a Khmer speaker's check; (9) AI controls showed while AI was switched off, against AI-NFR-02: the pitch coach (Communication Builder and the pitch module) and Draft with AI on the Communication Builder and Reviews pages; they are now hidden while AI is off, as the other AI cards already were. **Gaps noted, not changed:** some English text has no Khmer translation and is shown in English in Khmer: the review questions, their heading and the tenth diligence question on Reviews; the Purpose, Hook, Problem, Request and Message labels on the Communication Builder; the Description label, rank hint, root cause and alternatives hints and the "You've resolved…" line on Obstacles; "No messages yet." on Partner Detail; the "Why proceed anyway" label on Partners. These need a Khmer speaker. Text built when a page module loads (for example the suggested partner types) is translated only after the language change reloads the page, which is how the app already works. Outside this phase, `DreamCoachPage` measures 56% locally, below the 60% floor in CAC-03, and is picked up in Phase 5 or 6 |
| 5 | Done, 7 Nov 2026. **Frontend:** Cohorts 88.7%, Coach Participant 87.4%, Issue Reports 88.8%, Account Linking 96.6%, Google sign-in start 82.4%, Google sign-in error 100%, and Dream Coach raised from 56% to 82.2%. Every page is now at 60% or more (lowest: Today 60.7%); the pages folder is at 82.1%. Whole frontend 82.0% → 85.4% lines (lines and branches together 82.0%); 938 frontend tests in 110 files. COV-FR-23: an admin creates, edits, archives and restores cohorts, issues join codes, switches AI per cohort, and adds or removes members; participants and coaches are refused. A coach reads a participant's plan with no way to change it, sees journals only when shared (D-6, even if entries arrive), rates the pitch and sends comments; the AI comment draft appears only with AI on and the participant's consent, and sends nothing itself. Issue reports: a participant sees only their own; an admin sees the queue, filters it and changes status, with a resolution note required for a resolved bug (BR-32). COV-FR-24: the Google sign-in start, its error screen (with and without a reason, in Khmer too) and account linking (no link token, success, wrong password). The shared `renderPage` helper now takes navigation state. **Backend:** 80.2% → **88.1%** (lines 94.1%, branches 75.3%); 585 tests, under 2 minutes. COV-FR-28: `ClaudeClient` (6% → 96%) and `ClaudeKeyChecker` (9% → 97%) run against a local stub of the Claude API built on the JDK's HTTP server: a normal reply, a streamed reply in pieces, a refusal, a cut-short reply, a timeout, an unreachable API, error statuses, a structured answer and invalid JSON; the fallback setting is sent only for Opus and Sonnet; a new key takes effect at once. No real API call is made (COV-NFR-02). Both classes gained a package-private constructor taking the server address, time limit and retry count for tests; Spring still uses the default constructor, so behaviour is unchanged (COV-NFR-06). COV-FR-29: `AiContextBuilder` 60% → 96% (what goes to Claude for each page; never another user's dream or a private conflict note; record text cannot close the data block), `AiSafety` 100% (crisis wording in English and Khmer), `PlanAssistantService` 66% → 85% (every section's rules for proposed changes, and saving each kind of card), `VisionAreaService` 98%, `TaskItemService` 98%, `ReviewService` and `CommunicationMessageService` 100%, `PasswordResetMailer` 3% → 100% and `ReminderMailer` 96% (a reset link is logged only on local and test runs, never a reminder's content). COV-FR-30: every controller is now at 80% or more (controller package 62.8% → 96.9%). One lifecycle test takes each core resource through create, read, update, status, archive, restore and permanent delete, and checks 400 for invalid input, 401 without a token and 404 for another user's record; a second does the same for the journal, habits, gratitude, wanted partners, progress logs, synergy links, insights, work style, issue reports, cohorts and admin module text. **Whole project**, from the local reports (the SonarQube figure follows the next push to `main`): about 84.5% (23,323 of 27,594 lines and branches); planned 83%. Thresholds raised: frontend lines 85%, statements 84%, branches 78%, functions 78%; backend lines 94%, branches 75%. **Bugs found:** none in this phase. **Notes:** the Claude Java SDK sends the API key as `Authorization: Bearer` to a custom address, so the stub accepts either header; deleting a progress log archives it, so the owner can still read it by id, which matches BR-12 |
| 6 | Mostly done, 7 Nov 2026. COV-FR-06: final report from SonarQube Cloud on `main`, commit `f7e7666`, quality gate OK: **whole project 85.1%** (4,329 lines and branches not covered), backend 88.1%, frontend 83.3% (pages 83.4%, vision map 82.2%, other 83.3%); from the 56.7% baseline. COV-FR-05: the thresholds raised at the end of Phase 5 are the final ones (frontend lines 85%, statements 84%, branches 78%, functions 78%; backend lines 94%, branches 75%). CAC-06: a named login form test was the one gap (`LoginPage.test.tsx`: required fields, invalid email, wrong password, remember me keeps only the email, return to the page the person came from, Khmer). CAC-07: both suites were run under a macOS sandbox that blocks every outgoing connection except to the machine itself, and passed (945 frontend tests; every backend test, with Java set to IPv4 because the sandbox's local rule does not match Java's IPv6 loopback). CAC-08: on the CI run for `f7e7666` the backend job took 2.0 minutes and the frontend job 3.7 minutes. `README.md` (Test coverage section) and `docs/SECURITY.md` (Test coverage as a control) describe the figures, the minimums, how the tests are written and which tests guard which control. **Open:** COV-NFR-05 / CAC-09 needs 10 clean CI runs in a row on `main` (one so far for the final code: `f7e7666`); CAC-11 needs the QA lead's sample review of 20 V12 tests; CAC-04 was shown locally in Phase 1, not yet by a test commit on a branch |

## Acceptance criteria

| ID | Criterion | Traces to |
| --- | --- | --- |
| CAC-01 | SonarQube Cloud shows whole-project coverage above 80% on `main`. | CBO-01 |
| CAC-02 | SonarQube Cloud shows backend coverage of 85% or more and frontend coverage of 80% or more. | CBO-01 |
| CAC-03 | Every frontend page has tests, and no page is below 60%. The 26 pages below 50% in the baseline are each at 80% or more. | COV-FR-10 to COV-FR-24 |
| CAC-04 | Lowering coverage below the threshold makes CI fail, in both the frontend and backend jobs (shown by a test commit on a branch). | COV-FR-02, COV-FR-03 |
| CAC-05 | The coverage configuration excludes only the files allowed by COV-FR-04, each with its reason. | COV-FR-04 |
| CAC-06 | Every item in `CLAUDE.md` "Testing Requirements" (backend: progress calculation, overdue detection, blocked task validation, parent-child rules, goal completion rule, user data scoping, import/export mapping; frontend: login validation, protected routes, progress bar, status badge, task form validation) has at least one named test. | CBO-03 |
| CAC-07 | No test calls an outside service (checked by running the suite with network access turned off). | COV-NFR-02 |
| CAC-08 | The frontend CI job finishes in under 10 minutes and the backend job in under 12 minutes. | COV-NFR-03 |
| CAC-09 | The full CI run passes 10 times in a row on `main` at the end of Phase 6. | COV-NFR-05 |
| CAC-10 | All tests that existed before V12 still pass, and the SonarQube quality gate still passes. | COV-NFR-06 |
| CAC-11 | Sample review: the QA lead reads 20 tests picked at random from those added in V12 and finds that each one checks a real behaviour. | COV-NFR-01 |

### Acceptance status (7 Nov 2026)

| ID | Status |
| --- | --- |
| CAC-01 | Met: whole project 85.1% on `main` (`f7e7666`) |
| CAC-02 | Met: backend 88.1%, frontend 83.3% |
| CAC-03 | Met: every page has tests and none is below 60% (lowest: Today 60.7%); the 26 baseline pages below 50% are each at 80% or more |
| CAC-04 | Shown locally in Phase 1 (both checks fail when the minimum is raised); a test commit on a branch is still to do |
| CAC-05 | Met: only the exclusions COV-FR-04 allows, each with its reason in `frontend/vite.config.ts` |
| CAC-06 | Met: each CLAUDE.md testing requirement has a named test |
| CAC-07 | Met: both suites pass with outside network access blocked |
| CAC-08 | Met: backend 2.0 minutes, frontend 3.7 minutes |
| CAC-09 | Open: 1 of 10 clean runs on `main` for the final code |
| CAC-10 | Met: every test from before V12 still passes; quality gate OK |
| CAC-11 | Open: needs the QA lead |

## Assumptions, dependencies and risks

### Assumptions

- SonarQube Cloud stays connected and analyses every push to `main`.
- The tech stack in `CLAUDE.md` stays: React 19.2.7 with Vitest, Spring Boot 3.x with JUnit 5 and Mockito.
- No large new features land during V12. Features that do land bring their own tests, as the quality gate already requires.

### Dependencies

- An engineering lead to approve any coverage exclusion beyond COV-FR-04.
- A QA lead for the sample review (CAC-11).
- CI minutes on GitHub Actions for the extra test time.

### Risks

| Risk | Impact | Mitigation |
| --- | --- | --- |
| Tests written to raise the number, not to check behaviour | False sense of safety | COV-NFR-01; code review; sample review CAC-11 |
| The Vitest 5 figure is wrong, so targets are judged on a bad number | Wasted effort or false pass | COV-FR-01 first, before any target |
| Page tests are slow and the CI job passes its time limit | Blocked merges | Shared helpers; mock APIs, not real servers; COV-NFR-03 limits watched each phase |
| Long page tests time out on slower CI runners | Flaky builds | Per-test time limits for long tests; no fixed sleeps; 10 clean runs (COV-NFR-05) |
| `VisionMapTree.tsx` (1,011 uncovered items) is hard to test as one large file | Phase 3 slips | Test by behaviour through the tree; if needed, split it into smaller components with no change in behaviour (COV-NFR-06) |
| Tests find real bugs | Phase takes longer | Expected and welcome; bugs are logged and fixed through the normal process |
| A large feature lands mid-way | Coverage target moves | The quality gate's 80% on new code keeps new work covered; thresholds stop drops |
| Mocks drift from the real API | Tests pass, app fails | API module tests (COV-FR-25) check method, path and body; backend controller tests check the other side |

## Open questions

- [ ] Is "above 80%" enough, or should the final target be 85%?
- [ ] Should the SonarQube Cloud plan be upgraded so the quality gate itself can require 80% overall coverage? On the current plan only the built-in "Sonar way" gate (80% on new code) is available, so V12 enforces the overall figure in CI instead (COV-FR-02, COV-FR-03).
- [ ] Should coverage from the Playwright end-to-end tests be collected and added to the SonarQube figure later? It would reflect real use but needs extra build set-up.
- [ ] If COV-FR-01 shows the Vitest 3 figure (62.6%) was the accurate one, should the baseline and phase figures in this BRD be restated?
- [ ] May `VisionMapTree.tsx` be split into smaller components during Phase 3 if that makes it testable, given it is a no-behaviour-change refactor?

## Sign-off

| Role | Name | Decision | Date |
| --- | --- | --- | --- |
| Sponsor | | | |
| Product owner | | | |
| Engineering lead | | | |
| QA lead | | | |

## Sources

- BRD 11.0.0 – AI Plan Builder Improvements (7 Nov 2026), and its parent documents
- `CLAUDE.md` – method hierarchy, core business rules, testing requirements and working rules
- SonarQube Cloud project `Sot-Sirymony_vmap-sys`: measures, per-file coverage and coverage history for commits `7a84aac` to `d76e328`, 7 Nov 2026
- CI workflow `.github/workflows/ci.yml` and test configuration (`backend/pom.xml` JaCoCo, `frontend/vite.config.ts` Vitest coverage)
- `docs/SECURITY.md` – CI jobs and SonarQube Cloud set-up
