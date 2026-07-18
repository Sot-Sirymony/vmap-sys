# UX&UI V1 — Visual & Interface Design Improvement Plan

**Project:** Vision Mapping Management System (VMS)
**Version target:** V2.1 "Ease of Use" release
**Date:** 2026-07-18
**Status:** Study / planning document — no code changes yet
**Companion document:** [HUCI_V1.md](HUCI_V1.md) covers interaction design (flows, navigation, input cost, accessibility of behavior). This document covers the **presentation layer**: layout, typography, color, visual hierarchy, component styling, states, and motion. Where an item touches both, this document owns the "how it looks" half and HUCI_V1 owns the "how it works" half.

---

## 1. Purpose

Improve how the interface **looks and reads** so that users can scan, understand, and trust the app faster. Ease of use is not only fewer clicks (HUCI_V1) — it is also whether a page communicates its hierarchy at a glance, whether states (loading, empty, error, success) look intentional, and whether the app feels like one coherent product on every page, in every theme, at every density.

Guiding question for every change:

> "Can a user glance at any page for 3 seconds and correctly answer: where am I, what matters most here, and what is the primary action?"

---

## 2. Current State Assessment

### 2.1 Existing design system (strong — build on it, don't replace it)

The app already has an unusually solid visual foundation in [theme.ts](frontend/src/theme.ts) and [global.css](frontend/src/styles/global.css):

| Asset | Detail |
|---|---|
| Design language | Fluent 2 tokens (Microsoft), used consistently for brand, neutrals, strokes, shadows |
| Status palette | One source of truth (`statusColors`) shared by badges and charts; colorblind-validated (WAITING moved to purple after a measured ΔE failure) |
| Priority palette | Ordinal escalation scale (neutral → gold → orange → red) with lightness doing the colorblind-safety work |
| Theming | Light/dark with real Fluent dark tokens (not inverted), 5 pre-validated accent colors, comfortable/compact density, text-size scaling (FR-18) |
| Component overrides | Flat buttons, 4px-radius chips, bordered cards with Fluent shadow scale, Fluent focus rings on inputs |
| Layout primitives | Breakpoints aligned between CSS and MUI theme (600px sm, 900px md), sidebar collapse |

**Rule for this plan:** every proposal must use these tokens. No new hex values outside `theme.ts` / the `:root` block; no change to what a status color means (BR-14).

### 2.2 Main UX/UI gaps (hypotheses to verify in Phase A)

**U1 — No typography scale (severity: High)**
`theme.ts` sets only `fontFamily`. There is no defined type ramp (page title / section title / card title / body / caption), so heading sizes, weights, and spacing are whatever each page happened to use. Result: pages don't share a vertical rhythm, and visual hierarchy inside a page depends on the author, not the system.

**U2 — Page composition is table-first, hierarchy-flat (severity: High)**
Most pages are a filter bar + a dense `DataTable`. Everything has similar visual weight: the page title, filters, and 50 rows compete equally. The primary action ("New goal") is not visually dominant, and key information (overdue count, what needs attention) is not summarized above the fold on list pages.

**U3 — Loading/error/empty states are functionally present but visually thin (severity: Medium)**
`Loading.tsx` (spinner), `ErrorMessage.tsx`, `EmptyState.tsx` exist, but: spinners cause layout jump instead of skeletons preserving shape; empty states are plain text (no illustration, weak action prominence); error states likely look like developer messages rather than designed moments.

**U4 — Dashboard visual hierarchy (severity: Medium)**
Stat cards, attention panel, and charts (`recharts`) exist, but need an audit for: equal-weight card grids (nothing reads as "most important"), chart styling consistency with the Fluent palette, number formatting (%, dates), and how the page degrades when data is sparse.

**U5 — Micro-interaction and motion layer is undefined (severity: Medium)**
No documented transition standards: hover states, row highlight, modal enter/exit, kanban card pickup/drop, progress bar animation. Some exist via MUI defaults, but they are not tuned or consistent, and `prefers-reduced-motion` handling is unverified.

**U6 — Visual QA surface is large and unchecked (severity: Medium)**
FR-18 created a big combination space: 2 modes × 5 accents × 2 densities × 3+ text sizes. Nobody has systematically looked at every page in the worst combinations (e.g., compact + large text + dark + orange accent). Regressions hide here.

**U7 — Auth pages and app identity (severity: Low-Medium)**
Login/Register are the first screens a user sees. They likely have minimal branding — no product name treatment, no one-line explanation of what Vision Mapping is, no visual warmth. First impressions set trust.

**U8 — Iconography and label consistency (severity: Low)**
`lucide-react` is used in navigation; verify icons are used at consistent sizes/stroke widths across pages, that the same concept always gets the same icon (e.g., "dream" is always Sparkles), and that empty/CTA areas reuse the nav icon vocabulary so the visual language reinforces the mental model.

---

## 3. Design Principles for This Release

1. **One system, zero exceptions.** All colors, radii, shadows, and spacing come from the existing Fluent tokens. New needs → new token first, then use it.
2. **Hierarchy before decoration.** Every page gets exactly one visually dominant element (usually the primary action or the key number). If everything is bold, nothing is.
3. **Scan, don't read.** Users triage lists; tables and cards must expose status/priority/due-date as glanceable chips and color-plus-icon signals (never color alone — already a project rule).
4. **States are designed, not defaulted.** Loading, empty, error, success, and "partial data" each get an intentional visual treatment per component family.
5. **Calm motion.** Motion communicates causality (this opened from there, this card moved here), 150–250 ms, always disabled under `prefers-reduced-motion`.
6. **Coach visually.** The Vision Mapping method is a funnel (Area → Dream → Goal → Step → Task); the visuals should express depth — indentation, connector lines, progressive tinting in the tree — so the hierarchy is felt, not just labeled.

---

## 4. Study Phases

### Phase A — Visual Audit (3–4 days, no code changes)

| # | Activity | Method | Output |
|---|---|---|---|
| A1 | Screenshot inventory | Capture every page in light+dark, comfortable+compact, default accent; assemble into one contact sheet | `docs/uxui-audit/` screenshot set |
| A2 | Hierarchy squint test | For each screenshot, blur it and ask: what reads first, second, third? Compare against what *should* read first | Per-page hierarchy notes |
| A3 | Typography census | Grep/measure every font size, weight, and heading level in use across pages | Actual-vs-needed type ramp table |
| A4 | Spacing census | Measure paddings/gaps on the 6 main page templates; find the implicit grid (or lack of one) | Spacing token proposal |
| A5 | State walkthrough | Force loading, empty, error, and single-item states on every page (network throttle + fresh account) | State gap list |
| A6 | Combination sweep | The 4 worst FR-18 combinations on the 6 top pages | Visual bug list |
| A7 | Chart review | All `recharts` usages vs the status/priority palettes; label, tooltip, and legend consistency | Chart fix list |

**Metrics to record (before → after):**

| Metric | Baseline | Target |
|---|---|---|
| 3-second test: users correctly name the primary action per page | measure (A2 + hallway test) | ≥ 80 % of pages |
| Distinct font-size/weight combinations in the codebase | measure (A3) | ≤ 9 (one ramp) |
| Pages with layout jump during load | measure (A5) | 0 |
| Visual bugs in FR-18 combination sweep | measure (A6) | 0 open at ship |

### Phase B — Foundations (1 week)

Codify the missing system pieces once, so every later fix is cheap.

**B1. Type ramp in `theme.ts`.**
Define the full MUI `typography` object: `h1` (page title, ~24–28px semibold), `h2` (section, ~18–20px semibold), `subtitle1` (card/panel title), `body1`/`body2`, `caption` (metadata: dates, codes like `T-014`), with line heights and margins. Sweep pages to replace ad-hoc sizes with variants.
Files: [theme.ts](frontend/src/theme.ts), all `pages/*.tsx` (mechanical sweep).

**B2. Spacing scale + page template.**
Adopt the 4px-base scale (Fluent's own) as named steps; define one `PageSection` standard (it already exists as [PageSection.tsx](frontend/src/pages/PageSection.tsx) — audit and harden it): title row (title + primary action right-aligned) → summary strip (optional) → filter bar → content. Every list page conforms.
Files: `pages/PageSection.tsx`, `styles/global.css`.

**B3. State component kit.**
- `Loading`: skeleton variants for table rows, cards, and the vision-map tree (MUI Skeleton), replacing spinners where layout shape is known; spinner only for unknown-shape content.
- `EmptyState`: icon (from the nav icon vocabulary), one-line headline, one-line explanation, one primary action button. Two sizes (full-page, in-panel).
- `ErrorMessage`: human sentence + retry button; technical detail collapsed behind "Details".
Files: [Loading.tsx](frontend/src/components/common/Loading.tsx), [EmptyState.tsx](frontend/src/components/common/EmptyState.tsx), [ErrorMessage.tsx](frontend/src/components/common/ErrorMessage.tsx).

**B4. Motion tokens.**
Define 3 durations (fast 100ms hover, base 180ms open/close, slow 250ms layout) and 1 easing in `:root`; apply to modal, drawer, kanban drop, progress bar fill; global `@media (prefers-reduced-motion: reduce)` kill switch.
Files: `styles/global.css`, `theme.ts` (MUI transitions).

**B5. Icon rules.**
Document one size pair (16px inline, 20px nav/CTA), one stroke width, and the concept→icon mapping (reuse [nav-items.ts](frontend/src/components/layout/nav-items.ts) as the source of truth). Sweep for violations.
Files: new `docs/uxui-icons.md`, minor sweeps.

### Phase C — Page-Level Redesigns (2–3 weeks, priority order)

**C1. List page template applied (Goals, Steps, Tasks list, Dreams, Partners, Obstacles).**
Per B2: dominant page title + one primary button; a compact summary strip above the table (e.g., Goals: `12 active · 3 overdue · 2 blocked` as clickable filter chips); filters visually quieter than content; `DataTable` rows tightened — status/priority as chips, due dates with relative phrasing ("in 3 days", "5 days overdue" in the overdue red), code (`G-004`) demoted to caption style.
Files: [DataTable.tsx](frontend/src/components/common/DataTable.tsx), list pages, [TintedChip.tsx](frontend/src/components/common/TintedChip.tsx).

**C2. Dashboard rebalance.**
One hero row: the single most important signal (e.g., "3 tasks overdue · 2 blocked" attention strip) visually first; stat cards second with deliberate ordering and sparkline-free clarity; charts third, restyled per A7 to use exactly `statusColors`/`priorityColors`, consistent tooltips ([ChartTooltipContent.tsx](frontend/src/components/dashboard/ChartTooltipContent.tsx)), and empty-data fallbacks.
Files: `pages/DashboardPage.tsx`, `components/dashboard/*`.

**C3. Vision Map tree visual language.**
Express depth visually: connector lines or indentation guides, level-specific accent tinting (area → dream → goal → step → task get progressively lighter tint washes of the accent), progress bars aligned in a consistent right rail, quick-add affordance visible on hover/focus at each level. This is the visual half of HUCI_V1's C1 (map as primary editor).
Files: `components/vision-map/*`.

**C4. Tasks board polish.**
Column headers with count + WIP visual cap coloring; card layout standardized (title / chips row / due date+owner footer); drop-target highlight; overdue cards get the icon+text treatment (HUCI_V1 B4); column background uses `neutralBackground3` so cards float.
Files: [StatusBoard.tsx](frontend/src/components/common/StatusBoard.tsx), `pages/TasksBoardPage.tsx`.

**C5. Auth pages + identity.**
Two-panel or centered-card login: product name, one-line value statement ("Turn dreams into scheduled work"), the 5-level funnel as a small illustrative graphic (pure CSS/SVG, token colors), consistent with the accent system. Register page mirrors it.
Files: `pages/LoginPage.tsx`, `pages/RegisterPage.tsx`, `layouts/AuthLayout.tsx`.

**C6. Forms visual pass.**
Consistent field order (identity → classification → dates → details), section dividers in long forms, required-field marking standard, helper text style, and the coaching questions (HUCI_V1 C3 wizard) styled as friendly prompts, not validation errors.
Files: `components/forms/*`, [CrudModalForm.tsx](frontend/src/components/common/CrudModalForm.tsx).

### Phase D — Polish & Delight (1 week, optional)

- **D1.** Progress bar fill animation on change + brief highlight pulse on the row that just updated (causality feedback).
- **D2.** Completed-task treatment: subtle strikethrough/fade rather than identical row styling.
- **D3.** Per-Vision-Area color dots (from a fixed neutral-safe mini-palette, *not* the status hues) used in trees, tables, and chart legends ([CategoryBreakdownChart.tsx](frontend/src/components/dashboard/CategoryBreakdownChart.tsx)).
- **D4.** Toast redesign: icon + verb-first message + optional undo action (pairs with HUCI_V1 D3).
- **D5.** Print/PDF-friendly stylesheet for the Review pages (weekly review is a natural print/share artifact).

### Phase E — Validate (3–4 days)

Re-run A1 (screenshot contact sheet, before/after side by side), A2 (squint test with fresh eyes), A5 (state walkthrough), A6 (combination sweep). Check the metric targets from Phase A. Hallway-test the 3-second question on 5 pages with 3 people. Leftovers → UX&UI_V2 backlog.

---

## 5. Page-by-Page Priority Matrix

| Page | Current visual risk | Phase items | Priority |
|---|---|---|---|
| Dashboard | Flat hierarchy, chart consistency | C2, B3 | 1 |
| Dream Detail / Vision Map | Depth not expressed visually | C3, B3 | 1 |
| Tasks Board | Card/column styling ad-hoc | C4 | 2 |
| Goals / Steps / Tasks lists | Table-wall, no summary | C1 | 2 |
| Login / Register | No identity | C5 | 3 |
| Dreams / Partners / Obstacles | Same as lists | C1 | 3 |
| Communication Builder | Long form fatigue | C6 | 4 |
| Reviews | Print/share opportunity | C6, D5 | 4 |
| Import/Export | Low traffic, keep simple | B3 only | 5 |

---

## 6. Deliverables & Working Rules

- All audit artifacts live in `docs/uxui-audit/` (screenshots, findings tables).
- New/changed tokens documented in `theme.ts` comments in the same style as the existing palette rationale (that documentation standard is a project strength — keep it).
- Every phase ends with `npm run build` + `npm run test` green and a before/after screenshot pair for each changed page.
- No visual change may alter behavior covered by existing tests (`StatusBadge.test.tsx`, `Modal.test.tsx`, etc.) without updating them intentionally.
- Coordinate with HUCI_V1 phases: HUCI B (quick wins) and UX&UI B (foundations) can run in the same sprint; page redesigns (UX&UI C) should land together with their interaction counterparts (HUCI C) per page to avoid re-touching files twice.

---

## 7. Risks

- **Token drift:** the fastest way to ruin the current system is one "temporary" hardcoded color. Mitigation: a grep check (`#[0-9a-f]{6}` outside `theme.ts`/`global.css`) added to the review checklist.
- **Combination explosion (U6):** every visual change must be checked in at least light+dark × comfortable+compact before merge; the full accent sweep only at phase end.
- **Redesign creep:** this plan restyles within Fluent 2 — it is not a rebrand. Any proposal to change the design language itself is out of scope for V1.
- **User familiarity:** V2 users know the current screens; C1/C2 keep all element positions stable where possible (same page anatomy, better weighting), so changes read as "cleaner", not "moved my cheese".

---

## 8. Definition of Done for UX&UI V1

1. Phase A audit artifacts committed under `docs/uxui-audit/` with baseline metrics.
2. Type ramp, spacing scale, state kit, and motion tokens shipped (B1–B4) and used by every top-6 page.
3. C1–C4 shipped; C5–C6 shipped or deferred with reason.
4. Phase E shows: ≥ 80 % primary-action recognition, ≤ 9 type combinations, zero load-layout-jump pages, zero open combination-sweep bugs.
5. Before/after screenshot pairs for every redesigned page attached to the release notes.
