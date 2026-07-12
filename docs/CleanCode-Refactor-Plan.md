# Clean Code Transformation Plan

| | |
|---|---|
| **Document** | CleanCode-Refactor-Plan |
| **Version** | 1.0.0 |
| **Date** | 2026-07-12 |
| **Reference** | [Principle-CleanCode.md](Principle-CleanCode.md) |
| **Scope** | Structural refactor of existing backend + frontend to match the Clean Code principles. No behavior change. |

## How to read this plan

Each phase is **independently shippable** and must leave the build green
(`mvn test` = 40 tests, `npx tsc --noEmit`, `npx vitest run` = 10 tests,
`npm run build`). Phases are ordered **lowest-risk / highest-payoff first**.
Nothing here changes what the app does — every step is a structure-only
refactor guarded by the existing test suite. Stop and confirm after each phase,
per the project's phase-by-phase rule.

**Golden rule for this whole effort:** the tests are the safety net. Do not
refactor a unit until it is covered. Where coverage is thin (see Phase 6), add
the characterization test *first*, then refactor.

---

## Audit summary (what the code looks like today)

The codebase is already clean at the micro level — no commented-out code, one
`return null`, almost no train wrecks, consistent formatting. The debt is
**structural**, concentrated in a few large units:

| Finding | Location | Principle violated |
|---|---|---|
| **God class**: 1,456 lines, **80 public methods**, owns all 11 entities | `service/VisionMappingService.java` | SRP, "classes should be small", cohesion |
| Two high-complexity methods (CC 27 and 33 before recent split) | `buildProgressTrend`, dashboard aggregation | "do one thing", one level of abstraction |
| 9 CRUD pages repeat form + filter-bar + table scaffolding | `pages/*.tsx` (528-line `TasksBoardPage` worst) | DRY, small units |
| Status/priority `<MenuItem>` option lists inlined & duplicated (8 pages) | `pages/*.tsx` | DRY, one source of truth |
| Demeter chains: `getUser().getId()` ×14, `goal.getDream().getVisionArea().getId()` | service | Law of Demeter |
| `includeArchived ? findByUser_Id : findByUser_IdAndArchivedFalse` repeated ×18 | service | DRY |
| 10 near-identical `toResponse` mappers | `mapper/VisionMappingMapper.java` | acceptable, low priority |

Everything below turns those findings into concrete, ordered work.

---

## Phase 0 — Lock in the safety net (prerequisite)

Before touching structure, make the net trustworthy and the rules enforced.

1. **Confirm the baseline is green** and record counts: `mvn test` (40),
   `vitest` (10), `tsc`, `build`. This is the "runs all the tests" rule.
2. **Add coverage where a refactor will be blind.** Today the service has good
   flow tests (progress roll-up, archive/restore, permanent delete, Excel) but
   thin coverage on **dashboard aggregation** and **overdue/blocked edge cases**.
   Write characterization tests for `dashboard()` and `buildProgressTrend`
   *before* Phase 3 splits them.
3. **Wire the linters already talking to you.** SonarLint is emitting
   `java:S3776` (cognitive complexity), `S1192` (duplicated literals), `S8688`
   (timezone). Capture the current warning count as the number to drive down;
   treat "no new warnings" as a merge gate for the rest of this plan.

**Verify:** all suites green; new dashboard tests pass against current code.
**Risk:** none (adds tests only).

---

## Phase 1 — Names & small DRY wins (warm-up, near-zero risk)

Pure rename / extract-constant work. One word per concept, searchable names.

1. **One word per concept.** Audit the verb lexicon. The service already uses a
   consistent `create/get/list/update/archive/restore/permanentlyDelete` set —
   keep it; fix any stragglers (e.g. `dashboard()` is a noun; rename to
   `buildDashboardSummary()` to match the verb-phrase rule for methods).
2. **Kill duplicated literals** flagged by `S1192` in tests and Excel code —
   extract `"Authorization"`, `"Bearer "`, sheet names, column labels into
   named constants (`HttpHeaders.AUTHORIZATION` already exists; use it).
3. **Timezone correctness** (`S8688`): introduce a single injected `Clock` bean
   and replace bare `LocalDate.now()` / `Instant.now()` so tests are repeatable
   (F.I.R.S.T. — Repeatable) and the "now" boundary is testable.

**Verify:** suites green; SonarLint literal/timezone warnings drop to ~0.
**Risk:** trivial; mechanical renames covered by compiler + tests.

---

## Phase 2 — Split the God class (the main event)

`VisionMappingService` is the single biggest violation. Break it into
**per-aggregate services**, each with one reason to change. This is the
"classes should be small / SRP / cohesion" fix.

### Target structure

```
service/
  VisionAreaService.java      (create/get/list/update/status/archive/restore/delete)
  DreamService.java
  GoalService.java
  VisionStepService.java
  TaskItemService.java
  PartnerService.java
  CommunicationMessageService.java
  ReviewService.java
  ObstacleService.java
  ProgressLogService.java
  DashboardService.java            (read-model / aggregation only)
support/
  ArchiveCascade.java              (cascade archive + archive-impact counts)
  PermanentDeleteCascade.java      (subtree collect + unlink + delete, from recent work)
  ProgressCalculator.java          (task→step→goal→dream roll-up + average())
  EntityFinder.java (or per-repo)  (the visionArea(id)/dream(id) user-scoped lookups)
```

### Method

1. **Extract the cross-cutting helpers first** — they are shared, so pull them
   out before the entity services depend on them:
   - `ProgressCalculator` (recalculateStep/Goal, `average`, `normalizeProgress`,
     `isOverdue`).
   - `ArchiveCascade` and `PermanentDeleteCascade` (already self-contained after
     the recent permanent-delete work — the `Subtree`/`DeletedIds` helpers move
     here wholesale).
   - User-scoped finders (`visionArea(id)` … ) → a small `EntityLookup` that
     wraps each repository + `UserScope`.
2. **Peel entities off one at a time**, easiest first (`ProgressLogService`,
   then leaf `TaskItemService`, up to `VisionAreaService`). After each peel:
   update the matching controller to inject the new service, run `mvn test`,
   commit. One entity per commit keeps every step reversible.
3. **`DashboardService` last**, since it reads across all aggregates — inject the
   per-entity services (or repositories) it needs.
4. Delete `VisionMappingService` once empty.

**Law of Demeter cleanup, done during the peel:** replace
`entity.getUser().getId()` with a `userId()` on `UserScope`, and the
`goal.getDream().getVisionArea().getId()` train wreck with a query method on the
repository (`existsByIdAndUser…` / a projection) so the service stops walking the
object graph.

**Verify:** `mvn test` green after **every** entity peel (the 40 tests are the
proof the split preserved behavior); controllers compile against the new
services. **Risk:** medium — mitigated by one-entity-per-commit and the existing
integration tests exercising each controller path.

---

## Phase 3 — Tame the remaining complex methods

With the class split done, fix the functions that still "do more than one thing".

1. **`buildProgressTrend` (CC 27):** extract the per-bucket grouping, the
   date-window iteration, and the point-construction into named steps so the
   method reads as a top-down narrative (Step-down Rule).
2. **Dashboard aggregation:** one private query-method per metric
   (`countOverdueTasks`, `averageProgress`, `goalsByStatus`, …); the public
   method becomes a flat assembly of named results — one level of abstraction.
3. **Command/Query Separation sweep:** ensure no method both mutates and
   returns derived state; `updateXStatus` returning the response is fine (it is
   the created/updated resource), but flag any helper that both writes and
   computes.

**Verify:** SonarLint `S3776` count → 0; dashboard characterization tests
(Phase 0) still green. **Risk:** low; covered by Phase 0 tests.

---

## Phase 4 — Frontend DRY: one source of truth for options & filters

1. **Centralize enum option lists.** There is already `utils/enumLabels.ts` —
   extend it (or add `constants/options.ts`) to export `STATUS_OPTIONS`,
   `PRIORITY_OPTIONS`, etc., and replace the inlined `<MenuItem value="LOW">`
   blocks (priority ×8 pages, status ×4) and the two local `statusOptions`
   arrays in `GoalsPage`/`StepsPage`. One list, imported everywhere.
2. **Extract a `<FilterBar>` / `<EntityOptionSelect>` component.** All 9 pages
   render the same `filter-bar` shape; a small reusable `Select`-with-options
   component removes the repetition and makes the pages read at one level of
   abstraction.

**Verify:** `tsc` + `vitest` + `build` green; visually spot-check one page.
**Risk:** low; typed and compiler-checked.

---

## Phase 5 — Frontend: shrink the giant page components

`TasksBoardPage` (528), `DashboardPage` (434), `GoalsPage` (425),
`CommunicationBuilderPage` (396) are doing too much per file.

1. **Split by concern**, not arbitrarily: e.g. `TasksBoardPage` → `TaskColumn`,
   `TaskCard`, `TaskFilters`, and a thin page that composes them and owns state.
2. **Lift shared CRUD-page scaffolding** (create-modal + table + archive toggle
   + row menu) into a `CrudPageShell` where the per-entity page supplies only
   columns and form fields. `useCrudEntity` already centralizes the state
   machine; this does the same for the JSX.

**Verify:** `tsc`/`vitest`/`build` green; manual smoke of the split pages.
**Risk:** medium (most JSX churn) — do one page per commit, last.

---

## Phase 6 — Tests as first-class citizens (finish the net)

1. **One concept per test.** The recent `PermanentDeleteFlowTests` asserts
   several things in one method (cascade + unlink + guard). Split into three
   focused tests (guard rejects non-archived; cascade removes subtree; partner
   is unlinked not deleted) so a failure names the broken concept.
2. **Add the missing frontend tests** the project's own guide asks for: task
   form validation, protected-route behavior (exists), status/priority badge
   rendering — and a `RowActionsMenu` test proving "Delete permanently" only
   shows for archived rows and opens the confirm dialog.
3. **F.I.R.S.T. pass:** confirm every test is independent (no shared DB state
   leaking between the MockMvc flows — they already register unique users; keep
   that) and fast.

**Verify:** suites green, higher count, each test single-concept.
**Risk:** none (tests only).

---

## Explicitly out of scope (avoid pointless dogmatism)

- The 10 `toResponse` mappers are repetitive but readable and stable; a
  MapStruct migration is optional polish, not debt — defer.
- Entities exposing `@Getter/@Setter` (anemic) is idiomatic JPA here; not worth
  fighting the framework.
- No new features, no dependency upgrades, no API/DTO shape changes — this plan
  is behavior-preserving only.

## Suggested order & sizing

| Phase | Effort | Risk | Payoff |
|---|---|---|---|
| 0 Safety net | S | none | unblocks everything |
| 1 Names & literals | S | trivial | quick warning cleanup |
| 2 Split God class | **L** | medium | **largest structural win** |
| 3 Complex methods | M | low | readability |
| 4 Frontend option/filter DRY | S | low | removes duplication |
| 5 Split giant pages | M | medium | maintainability |
| 6 Tests | M | none | durable safety |

Recommended: do 0 → 1 → 2 first (that removes the dominant debt), then confirm
before continuing to 3–6.
