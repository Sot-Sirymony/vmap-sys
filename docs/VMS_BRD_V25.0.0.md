# BRD – Dashboard appearance redesign

Nov 23, 2026

## Document control

This is draft 25.0.0. It keeps every requirement in BRD 24.0.0 and the earlier BRDs. It changes **how the Dashboard
looks and reads**, not what data it holds or who may see it. No new data, no new permission, no change to coach access
(D-6, V9) or to account management (D-23-3).

| Item | Value |
| --- | --- |
| Document | Business Requirements Document (BRD) – Dashboard appearance addendum |
| Version | 25.0.0 – Draft |
| Parent documents | BRD 24.0.0 (current baseline); the appearance settings (FR-39 presets, FR-42 font, FR-44 greys, FR-48 Modern / Classic shape); the status palette in `theme.ts` |
| Business owner | To be confirmed |
| Product owner | To be confirmed |
| Approvers | Sponsor, Product owner, Design reviewer, Accessibility reviewer |

| Version | Change | Author |
| --- | --- | --- |
| 25.0.0 (cleanup) | Product owner approved removing the V24 dashboard components the redesign no longer uses: `DashboardSummary`, `CategoryBreakdownChart` (and its test), `categoryKeys.ts`, `VisionAreaProgressChart` and `PartnerPipelineChart` with their lazy wrappers, and the theme palettes only they used (`categoricalPieColors` / `categoricalPieColor` / `CATEGORICAL_PIE_SLOTS` / `pieLabelColor` with their tests, `heatmapLevelColors`, `chartBlueRamp`, `partnerPipelineColors`). Charts use `design/tokens.ts` through `chartTheme`. `DashboardCard` stays (the report summary uses it). Frontend: 165 files, 1382 tests pass; the build passes | Dev |
| 25.0.0 (phase 6 built) | Phase 6: `UAT_V25.md` (DAC-01 to DAC-10 and feature checks DAF-01 to DAF-11), `km-review-v25.csv` (52 drafts from phases 3–5), a Dashboard section in `USER_GUIDE.md` (with the status colour key; it links to the design preview instead of screenshots, which need a running app to capture), the dashboard fields in `API.md` (including `nextUpTasks`), CHANGELOG. All phases built; awaiting deploy, UAT, the Khmer review and sign-off. Open: removing the now-unused V24 dashboard components (`DashboardSummary`, `CategoryBreakdownChart`, `VisionAreaProgressChart`, `PartnerPipelineChart`) needs the product owner's approval; area target ticks (Q-25-2) need a server calculation | Dev |
| 25.0.0 (phase 5 built) | Phase 5: the goal celebration (`Celebration.tsx`: a 24-piece, 1.2 s confetti burst confined to the progress card, plus a toast), skeletons in the final layout while loading with "Still loading…" after 10 s, a 200 ms staggered fade-and-rise for the three zones, press feedback (scale 0.98) on toggles and tabs, and the phone layout: the Insights tabs come before the trend, and the trend and the goal/task status card sit behind "Show …" buttons (`MobileCollapse.tsx`). Clarified during build: (1) the celebration fires on the Dashboard when more goals are complete than at the last visit, remembered per person in browser storage (no storage, no celebration); the first visit and area-scoped views never celebrate. It cannot name the goal, because the dashboard payload carries counts, not goal titles, so the toast says "A goal is complete. One step closer to your dream." Celebrating "from any page" (DA-FR-14) would need a shared event and stays a Could; (2) VMS has no focus-mode setting (the first draft assumed one), so D-25-7, section 8 and Q-25-1 no longer mention it; reduced motion means the device setting or Appearance → Reduce motion; (3) count-up, ring and bar growth were already in place from phase 3; the global reduced-motion rules also stop every CSS animation; (4) on phones the Insights tabs stay tabs rather than becoming one card per tab | Dev |
| 25.0.0 (phase 4 built) | Phase 4: `DashboardPage` rebuilt as Today → Plan health → Insights. Today: greeting, Next up, the progress ring with the portfolio line and moonshots, and three linked KPIs (Due in the period with completions, Overdue, Blocked; "All clear" at zero). Plan health: ranked Vision Area progress and the goal/task stacked status bars, with dreams by area. Insights: the attention panel, the restyled trend (gradient, current point labelled), the Obstacles / Partners / Reviews tabs, and the account cards (energy, gratitude, progress metrics, reminders). Every chart card has "View as table". Backend: `GET /api/dashboard` gains `nextUpTasks` (DA-FR-02 order; `priorityTasks` is unchanged). Clarified during build: (1) instead of the two optional fields, the server sends `nextUpTasks`, because the five highest-priority tasks could leave out an overdue Low task; the progress change is computed in the browser over the 12-week trend window and labelled "over 12 weeks", and the review streak counts weeks with any review; (2) no target ticks yet: the API has no per-area plan value (Q-25-2 needs a server calculation), so the bars show progress only; (3) no coach dashboard exists today, so D-25-10 changes nothing now and no data reaches coaches; (4) one request still feeds the page, so a load failure is a page-level message with Try again; the account cards already load and fail on their own; (5) quick complete uses the existing status endpoint, so the server's own rules (for example a change note when leaving Blocked) show as a toast; (6) on phones the progress card comes first, then Next up, then the KPIs; the Insights tabs stay tabs and the "More insights" collapse moves to phase 5; (7) the priority table, the four pie and donut charts, the priority bar chart, the Recharts partner bar and the green heatmap leave the dashboard; their components (`DashboardSummary`, `CategoryBreakdownChart`, `VisionAreaProgressChart`, `PartnerPipelineChart`) stay in the code, unused, until removal is approved | Dev |
| 25.0.0 (phase 3 built) | Phase 3: `StatusChip` (`components/common`), and in `components/dashboard`: `KpiCard`, `ChartCard`, `ProgressRing`, `StackedStatusBar`, `RankedProgressList`, `NextUpList` (with `sortNextUp` and `dueLabel`), `ZoneHeader`, the shared `surface.ts` card styles, and `charts/chartTheme.tsx` (`useChartTheme`, `cssVar`, `AreaGradient`); `utils/format.ts` gains `formatNumber`, `formatPercent` and `formatDelta`; hooks `useReducedMotion` and `useCountUp`; Khmer drafts for the new strings. No page uses them yet. Clarified during build: `StackedStatusBar` lists the counts under the bar with each status's icon instead of printing them on the segments, because white text on the light Blocked fill would be 3.25:1; reduced motion means the device setting *or* the Appearance "Reduce motion" switch (FR-39.4); under high contrast the chip and chart fills come from `statusColor()`, since the `--st-*` variables do not carry the high-contrast hues; `chartTheme` falls back to the token values when the stylesheet is absent (tests); a test checks that none of the new files contains a hex colour (DA-NFR-07). `StatusBadge` is replaced on the dashboard in phase 4 and elsewhere gradually | Dev |
| 25.0.0 (phase 2 built) | Phase 2: `frontend/src/design/tokens.ts` is the token source; `theme.ts` (status families, mode-specific status fills, default accent, neutrals, motion, font stack) and `global.css` (V25 values, `--st-*`, `--chart-*`, `--heat-*`, surface and text tokens, Khmer `@font-face`, Khmer line height) follow it; `design/tokens.test.ts` enforces contrast, colourblind separation and stylesheet sync. Clarified during build: the colours were re-measured with the project's own method (OKLab ×100 under the Machado protan and deutan simulations, as `theme.test.ts` uses), which is stricter than the CIE76 check in the first draft; it found Blocked↔Completed at 3.1–6.7 and dark Moving↔Waiting at 5.2, so sections 5.2 and 5.3 now carry corrected values (lightness now separates those pairs) and every work-status pair clears 10. High priority moves from `#ca5010` to `#C2410A` so it stays ≥ 9 from the new Blocked. Paused gets its own darker (light) / lighter (dark) slate so it never merges with Not started. The CSS variables are kept in step by a test that parses `global.css` (like FR-47's palette block), not by a generator script. The Khmer face is declared for the Khmer Unicode range only, so it downloads only when Khmer is on screen and never replaces a Latin face. Categorical pie colours and the green review heatmap stay until phase 4, when those charts are restyled | Dev |
| 25.0.0 (phase 1 built) | Phase 1: `docs/design/v25-dashboard-preview.html`, the static preview for design sign-off | Dev |
| 25.0.0 | First draft from the product owner's design brief of 23 Nov 2026 (best-in-class dashboard appearance). Palette values checked by script for WCAG contrast and colourblind distance before inclusion | BA / Design |

---

## Executive summary

The Dashboard shows the right things but reads as a report: seven KPI cards of equal weight, nine charts of equal
weight, and colour on almost everything. A person opening it cannot tell in five seconds **what to do next** or
**how they are doing**.

V25 keeps the data and reorders the page around the five decisions people make with it, in this order:

1. What to work on next.
2. Where they are stuck.
3. Which areas or dreams need attention.
4. Which partners to follow up.
5. Whether they are keeping up their reviews.

The page gets three zones. **Today** comes first: next actions and overall progress. **Health** is next: areas,
dreams and goals. **Insights** comes last: trends, obstacles, partners and reviews. The visual direction is **Calm
Fluent**: a neutral canvas, colour used only for meaning, large confident numbers, quiet labels and subtle motion.

The review that led to this BRD also found **two colour faults in today's theme** that V25 fixes:

- **Orange means two things.** It is used for both *Blocked* (work) and *Open* (obstacle).
- **Blue and purple are hard to tell apart.** Today's *In progress* blue (`#0078d4`) and *Waiting* purple
  (`#8764b8`) are almost the same colour to people with red-green colour blindness: a measured distance of about 1–2
  ΔE (CIE76, simulated deuteranopia and protanopia), where at least 8 is needed. The note in `theme.ts` measured only
  the amber and orange pair, so this one was missed.

---

## Decisions recorded by this BRD

| ID | Decision |
| --- | --- |
| D-25-1 | Visual direction is **Calm Fluent** (section 1). It stays inside Fluent 2 principles, so existing MUI components keep working |
| D-25-2 | The page has three zones in a fixed order, **Today → Health → Insights**. On mobile, cards follow the decision order above |
| D-25-3 | The seven KPI cards become **four**: Overall progress, Due this week, Overdue and Blocked. Vision Areas, Active Dreams, Active Goals and Open Tasks move into a single "Portfolio" line under the progress ring |
| D-25-4 | Orange is reserved for **Blocked** work. An *Open* obstacle uses **berry**. *Waiting* moves from purple to **indigo**. Pairs that collapse for red-green colour blindness are split by **lightness** as well as hue: Blocked is a bright orange beside a deep green Completed, and Waiting a deep indigo beside a mid blue In progress (reversed where needed in dark mode). High priority moves to `#C2410A` so it never reads as Blocked |
| D-25-5 | Status is never shown by colour alone. Every chip carries a **colour, an icon and a label**, and every chart has a direct label or a legend |
| D-25-6 | **One token source.** `tokens.ts` feeds the MUI theme, the CSS variables used by Tailwind, and a Recharts helper. No hex values in components |
| D-25-7 | The user's appearance settings (mode, accent, preset, tone, font, density, Modern / Classic / Liquid Glass, high contrast, Reduce motion) keep working. The accent drives the brand slot only. Status and chart colours are not changed by the accent, so meaning stays the same for everyone |
| D-25-8 | Motion is 150–250 ms and every animation is turned off under `prefers-reduced-motion`. The completion celebration is quiet and plays at most once per goal |
| D-25-9 | Khmer text uses **Noto Sans Khmer** as a fallback in every font stack, with a larger line height for Khmer (section 4) |
| D-25-10 | The coach's read-only view uses the same layout with the action controls removed. No new data reaches the coach |

---

## Scope

### In scope

- New Dashboard layout, KPI card, chart card, status chip and section header components.
- Design tokens for light and dark mode: colour, typography, spacing, radius, elevation and motion.
- Restyling all nine charts, and changing the chart type where a better one fits.
- Loading, empty, error and first-run states for the Dashboard.
- Responsive layout for desktop, tablet and mobile.
- A static HTML preview with sample data, in light and dark mode, for sign-off before the build.
- Updating `theme.ts` and `global.css` to the new tokens. Other pages pick up the new chips and surfaces automatically
  where they already use the shared components.

### Out of scope

- New metrics, new API fields or new endpoints. The Dashboard API is unchanged, except one optional field noted under
  Data and API changes.
- A redesign of pages other than the Dashboard. They only inherit the new tokens.
- New appearance settings. The existing settings keep their meaning.

---

## 1. Visual direction

Three directions were considered.

| Direction | Mood | Palette sketch | Fit for a personal goals app |
| --- | --- | --- | --- |
| **A. Calm Fluent** (recommended) | Quiet, clear, trustworthy, like a well-kept notebook. Space does the work; colour appears only where something needs you | Cool neutral canvas `#F6F7F9`, white cards, ink `#1B1F24`, one brand blue `#0F6CBD`, status hues only on chips and data | Best. People open it every day; calm lowers the stress of a long list. Builds on the Fluent 2 tokens already in use, so the change is low risk |
| B. Warm Motivational | Encouraging, soft, a little playful. Warm cream canvas, rounded shapes, illustrated progress | Cream `#FBF8F3`, terracotta brand `#C2562B`, sage greens | Strong emotional pull, but warm hues collide with the orange and red status colours, and it drifts away from Fluent |
| C. Data-dense Pro | Compact, analytical, like Linear's insights view. Small type, many numbers, thin rules | Near-white `#FAFAFA`, graphite `#111`, electric blue `#2563EB` | Good for power users and coaches, but it fails the five-second test for most people and feels like work, not dreams |

**Recommendation: A, Calm Fluent.** It meets the brief's five tests:

- **Glanceable:** one hero zone answers both key questions.
- **Calm:** a neutral-first canvas.
- **Polished:** Fluent 2 rhythm and depth.
- **Motivating:** progress rings, positive deltas and a quiet celebration.
- **Inclusive:** checked contrast and colourblind separation.

Option C's density stays available as the existing **Compact** density setting.

---

## 2. Before → after

| Area | Before | After |
| --- | --- | --- |
| First screen | Seven equal KPI cards, then charts | **Today zone**: "Next up" list (up to 5 tasks) plus an overall progress ring with this period's change |
| KPIs | 7 cards, same size and weight | 4 KPIs. Overdue and Blocked show only when above 0; otherwise they turn into a calm "All clear" |
| Charts | 9 cards in an even grid, each with a legend | Grouped into 3 zones. Secondary charts sit in tabs ("Status", "Obstacles", "Partners", "Reviews") so only one shows at a time |
| Status charts | Donut or bar per entity | One **horizontal stacked bar** per entity (goals, tasks), directly labelled, with completed shown last and in green |
| Area progress | Bar chart | **Ranked progress bars**, lowest first, each with a target tick and a link to the area |
| Colour | Colour on many cards, headers and icons | Colour only for status, data and the primary action. Everything else is neutral |
| Depth | Bordered boxes everywhere ("box soup") | Cards with a hairline border plus a soft shadow on a tinted canvas. Inner groups are split by spacing and dividers, not nested boxes |
| Numbers | Body-weight numbers | 40 px semibold KPI numbers with tabular figures and quiet 13 px labels |
| Motion | None or default | 200 ms fades and rises, count-up numbers, chart growth on first view, one quiet celebration when a goal completes |
| Empty dashboard | Empty charts | A first-run card with three steps and a friendly line drawing |

---

## 3. Layout and hierarchy

### Grid

| Breakpoint | Width | Columns | Gutter | Side margin |
| --- | --- | --- | --- | --- |
| Mobile | < 600 px | 4 | 12 px | 16 px |
| Tablet | 600–1023 px | 8 | 16 px | 24 px |
| Desktop | 1024–1439 px | 12 | 24 px | 32 px |
| Wide | ≥ 1440 px | 12 | 24 px | auto; content max 1360 px |

Vertical rhythm: 32 px between zones, 24 px between cards in a zone, 20 px card padding (16 px on mobile).

### Reading path

```text
TODAY    "What to do next" + "How am I doing"        → decisions 1 and 2
HEALTH   Areas → Dreams → Goals, lowest progress first → decision 3
INSIGHTS Trend · Obstacles · Partners · Reviews        → decisions 2, 4 and 5 in depth
```

### Desktop wireframe (12 columns)

```text
┌──────────────────────────────────────────────────────────────────────────────────────────┐
│ Good morning, Sokha                                  [Vision Area ▾] [This month ▾] [+ Task]│
│ Thursday 23 November · 3 tasks due today                                                  │
├──────────────────────────────────────────────────────────┬───────────────────────────────┤
│ NEXT UP                                     (cols 1–8)   │ OVERALL PROGRESS   (cols 9–12)│
│ ● Draft methodology     Concept note   ⚑High  Today   ›  │        ╭───────╮              │
│ ● Save 15 articles      Literature     ⚑High  Tomorrow›  │       │  62%  │  ▲ 8 pts      │
│ ◐ Email Dr. Lina        Mentor review  Med    Fri     ›  │        ╰───────╯  this month  │
│ ⊘ Book ethics review    Ethics   Blocked: waiting form › │ 4 areas · 7 dreams · 18 goals │
│ ● Summarise findings    Literature    Med    Mon     ›   │ · 46 open tasks               │
│                                   View all tasks →       │                               │
├──────────────┬──────────────┬──────────────┬─────────────┴───────────────────────────────┤
│ DUE THIS WEEK│ OVERDUE      │ BLOCKED      │ (KPI strip, cols 1–12, 4 equal cards)        │
│ 9            │ 2  ▲1        │ 1            │ Overdue/Blocked show "All clear ✓" when 0    │
│ 3 today      │ oldest 4 days│ needs partner│                                              │
├──────────────┴──────────────┴──────────────┴─────────────────────────────────────────────┤
│ HEALTH                                                                                    │
│ ┌ Vision Area progress (cols 1–7) ──────────────┐ ┌ Goals (cols 8–12) ──────────────────┐ │
│ │ Finance      ▓▓▓░░░░░░░░│░░░  24%  ›          │ │ ▓▓▓▓▓▓▓▓░░░░▒▒▒▒████████  18 goals  │ │
│ │ Health       ▓▓▓▓▓▓░░░░░│░░░  41%  ›          │ │ Not started 4 · In progress 6 ·     │ │
│ │ Career       ▓▓▓▓▓▓▓▓▓░░│░░░  63%  ›          │ │ Waiting 2 · Blocked 1 · Done 5      │ │
│ │ Family       ▓▓▓▓▓▓▓▓▓▓▓│▓░░  78%  ›          │ │ Tasks  ▓▓▓▓▓▓░░▒▒████████  46 tasks │ │
│ │            target tick │ = period target      │ │ Dreams by area: 4 · 2 · 1 · 0       │ │
│ └───────────────────────────────────────────────┘ └─────────────────────────────────────┘ │
├──────────────────────────────────────────────────────────────────────────────────────────┤
│ INSIGHTS                                                                                  │
│ ┌ Progress trend (cols 1–7) ────────────────────┐ ┌ [Obstacles][Partners][Reviews] (8–12)┐│
│ │ 70%┤                              ╭──● 62%    │ │ Obstacles: ranked bars by severity   ││
│ │    ┤                    ╭────────╯            │ │ Partners: 4-stage pipeline, counts   ││
│ │    ┤ - - - - - - - - - - - - - - - target 60  │ │ Reviews: 12-week heatmap + streak    ││
│ │ 0% ┼──────────────────────────────── 12 weeks │ │ "5-week weekly review streak"        ││
│ └───────────────────────────────────────────────┘ └──────────────────────────────────────┘│
└──────────────────────────────────────────────────────────────────────────────────────────┘
```

Tasks by priority moves into the "Next up" card as a footer line ("2 Critical · 7 High open"), because priority only
matters for choosing what to do next.

### Mobile wireframe (4 columns, decision order)

```text
┌──────────────────────────────┐
│ Good morning, Sokha     [⚙︎]  │
│ [Area ▾] [This month ▾]      │
├──────────────────────────────┤
│ ╭────╮ 62%  ▲8 pts           │  1  Overall progress (compact)
│ ╰────╯ 46 open · 18 goals    │
├──────────────────────────────┤
│ NEXT UP                      │  2  Next actions (decision 1)
│ ● Draft methodology  Today › │
│ ● Save 15 articles   Tmrw  › │
│ ⊘ Book ethics review Blocked›│
│ View all →                   │
├───────────┬──────────┬───────┤
│ Due wk 9  │ Overdue 2│Block 1│  3  KPI strip, horizontal scroll-snap
├──────────────────────────────┤
│ STUCK                        │  4  Blocked + top obstacles (decision 2)
├──────────────────────────────┤
│ AREAS NEED ATTENTION         │  5  Lowest 3 areas, "Show all" (decision 3)
├──────────────────────────────┤
│ PARTNERS TO FOLLOW UP        │  6  Count per stage + first 3 names (decision 4)
├──────────────────────────────┤
│ REVIEWS  5-week streak       │  7  Compact 4-week strip (decision 5)
├──────────────────────────────┤
│ Trend · Goals · Tasks  ▸     │  8  Collapsed "More insights"
└──────────────────────────────┘
[+]  floating "Add task" button, 56 px, bottom-right, clear of the bottom nav
```

Tablet: the hero is 5 + 3 columns, KPIs 4 across, and Health and Insights cards stack two per row.

---

## 4. Typography

The font stack keeps the user's chosen face (FR-42) and always ends with Khmer and system fallbacks:

```text
var(--font-family), "Segoe UI Variable", "Segoe UI", -apple-system, BlinkMacSystemFont,
"Noto Sans Khmer", Roboto, "Helvetica Neue", Arial, sans-serif
```

Noto Sans Khmer is self-hosted through `@fontsource-variable/noto-sans-khmer`, in line with the other fonts. Under
`:lang(km)`, line heights rise by about 25%, and letter-spacing is reset to 0, because Khmer has stacked subscript
consonants and must never be tracked.

| Token | Size / line height (EN) | Line height (KM) | Weight | Tracking | Use |
| --- | --- | --- | --- | --- | --- |
| `display` | 32 / 40 | 48 | 600 | −0.5 px | Greeting |
| `numeric-xl` | 40 / 44 | 48 | 600 | −0.5 px | KPI numbers, progress ring |
| `title` | 20 / 28 | 34 | 600 | −0.2 px | Zone headings |
| `subtitle` | 16 / 24 | 30 | 600 | 0 | Card titles |
| `body` | 14 / 20 | 26 | 400 | 0 | Rows, text |
| `body-strong` | 14 / 20 | 26 | 500 | 0 | Task titles |
| `caption` | 12 / 16 | 22 | 400 | +0.1 px | Labels, axis ticks, helper text |
| `overline` | 11 / 16 | 22 (no uppercase) | 600 | +0.6 px | "NEXT UP", "HEALTH" (EN only uppercase) |
| `numeric` | 14 / 20 | 26 | 500 | 0 | Table numbers, percentages |

- **Tabular figures:** `font-variant-numeric: tabular-nums` on every numeric token, chart tick and table column, so
  digits line up and count-ups don't jitter.
- **Number format:** use `Intl.NumberFormat` with the user's locale. Percentages have no decimals, and deltas are
  signed in points ("▲ 8 pts", "▼ 3 pts"). Dates are relative when within 7 days ("Today", "Tomorrow", "Fri"), and
  short absolute dates otherwise.
- **Quiet labels:** KPI labels use `caption` in `--text-secondary`, placed above the number. The number carries the
  weight.

---

## 5. Colour

All values below were checked by script, with the same method as `theme.test.ts`: text against its own background
(WCAG 2.2 AA, 4.5:1 for text and 3:1 for non-text), and colours against each other in OKLab (distance ×100) under the
Machado–Oliveira–Fernandes 2009 protan and deutan simulations. The floors are the project's: at least 8 under each
simulation, and 15 for full colour vision where categories must be told apart. `frontend/src/design/tokens.test.ts`
enforces them (phase 2).

### 5.1 Neutrals and brand

| Token | Light | Dark | Notes (contrast) |
| --- | --- | --- | --- |
| `--canvas` | `#F6F7F9` | `#0E1014` | Page background |
| `--surface` | `#FFFFFF` | `#171A1F` | Cards |
| `--surface-raised` | `#FFFFFF` | `#1E2228` | Menus, popovers, tooltips |
| `--surface-sunken` | `#F1F3F5` | `#12151A` | Table headers, inset wells, progress track |
| `--border` | `#E5E7EB` | `#2A2F37` | Card hairline (decorative, 1.24:1 / 1.3:1) |
| `--divider` | `#EEF0F3` | `#23272E` | Row separators |
| `--border-strong` | `#8A939F` | `#6B7480` | Inputs, selects (3.11:1 / 3.68:1, meets 1.4.11) |
| `--text-primary` | `#1B1F24` | `#E8EAED` | 16.56:1 on surface / 14.47:1 |
| `--text-secondary` | `#57606A` | `#A8B0BA` | 6.39:1 / 7.96:1 |
| `--text-tertiary` | `#646C78` | `#8E97A3` | 4.95:1 on canvas / 5.90:1 on surface |
| `--brand` | `#0F6CBD` | `#4FA3F0` | Fluent 2 brand-80. White on brand 5.38:1; dark mode uses ink `#0E1014` on brand, 7.10:1 |
| `--brand-hover` | `#115EA3` | `#6BB3F3` | |
| `--brand-pressed` | `#0C3B5E` | `#8AC3F5` | |
| `--brand-subtle` | `#EBF3FC` | `#1A2A3B` | Selected row, nav item. Text `#0B5A9E` on it 6.32:1 |
| `--focus-ring` | `#0F6CBD` | `#4FA3F0` | 2 px ring with 2 px offset in `--surface` |

**Brand is used only for:** the primary button, links, focus rings, the selected navigation item and the progress ring
fill. It is never used as a card or header background.

The user's accent setting (D-25-7) replaces `--brand*` only, and each accent keeps its current AA check.

### 5.2 Status (fixes D-25-4)

| Meaning | Statuses | Light fill | Light chip bg / text | Dark fill | Dark chip bg / text | Icon (lucide) |
| --- | --- | --- | --- | --- | --- | --- |
| Moving | In progress, Active, Contacted, Agreed, Sent, Replied, Planned | `#0F6CBD` | `#EBF3FC` / `#0B5A9E` | `#4FA3F0` | `#203040` / `#4FA3F0` | `CircleDot` |
| Waiting | Waiting, Followed up, In review | `#3B2596` | `#EFECF9` / `#3B2596` | `#7B6BE6` | `#27273F` / `#ABA0F5` | `Hourglass` |
| Blocked | Blocked (work only) | `#E46C1E` | `#FDF0E7` / `#A8400C` | `#E0612A` | `#372521` / `#F08A5D` | `OctagonX` |
| Open issue | Open (obstacles, issue reports) | `#B4009E` | `#FBEAF8` / `#9C0089` | `#EE6FD3` | `#39283C` / `#EE6FD3` | `CircleAlert` |
| Negative | Declined | `#C50F1F` | `#FDECEE` / `#B00E1C` | `#F47A84` | `#3A292F` / `#F47A84` | `CircleSlash` |
| Done | Completed, Resolved, Closed | `#0E6B0E` | `#E9F4E9` / `#0B5E0B` | `#72D872` | `#26382C` / `#72D872` | `CircleCheck` |
| Idle | Not started, Idea, To contact, Draft | `#6B7380` | `#F1F3F5` / `#57606A` | `#98A1AD` | `#2C3036` / `#98A1AD` | `Circle` |
| Held | Paused, Archived, Accepted, Won't fix | `#3F4650` | `#EEF0F3` / `#3F4650` | `#D3D8DE` | `#35383E` / `#D3D8DE` | `CirclePause` |

- **Fills** are for icons, dots and chart marks. Every fill has at least 3:1 against its card. A light fill used as
  text (for example the Waiting indigo) has at least 4.5:1.
- **Chip text** has at least 4.5:1 on its chip background and on the card. The narrowest are dark Moving, Open and
  Idle at 5.0–5.1:1.
- **Dark chip backgrounds** are the fill mixed at 16% over `#171A1F`, written as hex so the test measures what
  renders.
- **Held vs Idle:** Held is a darker slate than Idle in light mode and a lighter one in dark mode, so Paused never
  merges with Not started in a chart. In the stacked status bar it is also striped (section 7.2).

**Colourblind separation** for the six work statuses that share one chart (Not started, In progress, Waiting,
Blocked, Paused, Completed), using OKLab under the protan and deutan simulations:

- In light mode, every pair is at least 10.5 apart. The closest is Blocked and Completed: a bright orange beside a
  deep green.
- In dark mode, every pair is at least 10.3 apart.
- The V24 palette scored 1–2 for Waiting and In progress, and the first draft of this BRD scored 3.1–6.7 for Blocked
  and Completed. Both are fixed.

Pairs that never share a chart may sit closer, because a label and an icon always go with them (D-25-5). Blocked and
Declined are 3.1 apart, but Blocked is a task status and Declined a partner status. Waiting and Open are 3.3 apart,
but Waiting is a task status and Open an obstacle status.

**Priority:** High moves from `#ca5010` to `#C2410A`. It is at least 9.0 from Blocked in both modes and from Critical
under both simulations, with 5.18:1 on white. Low, Medium and Critical keep their values.

### 5.3 Chart palette (categorical)

Used for vision areas and any non-status series. **At most five colours.** A sixth or later series is grouped as
"Other", which is dataviz best practice and also how the palette stays colourblind-safe.

| Slot | Light | Dark | Name |
| --- | --- | --- | --- |
| `--chart-1` | `#2F6FDE` | `#4A86F0` | Blue |
| `--chart-2` | `#E07A1F` | `#F09A4A` | Amber |
| `--chart-3` | `#0F8A7C` | `#1E8F81` | Teal (deep, so it differs from orchid by lightness) |
| `--chart-4` | `#D25AA8` | `#F290D2` | Orchid (light) |
| `--chart-5` | `#2A2370` | `#D9D4FF` | Indigo (dark in light mode, pale in dark mode, so it differs from blue by lightness) |
| `--chart-other` | `#8A939F` | `#6B7480` | Other |

- Every slot meets 3:1 against its card (non-text, 1.4.11).
- Every pair is at least 8 apart under both simulations: the closest is teal and orchid, at 8.3 in light mode and 10.8
  in dark mode. Every pair is at least 15 apart for full colour vision.
- Series labels are written in `--text-secondary`, never in the series colour.
- The existing categorical pie palette (`categoricalPieColors`, labels drawn on the slices) stays in use until phase
  4 replaces those pies with directly labelled bars.

Sequential scale (review heatmap, single hue from the brand family): light `#EEF0F3 → #C7DDF5 → #8BB8EA → #3F85D4 →
#0F5AA6`, dark `#1E2228 → #1D3550 → #245487 → #3477C2 → #6BB3F3`. Zero is always the neutral step, so "no review"
never looks like data.

---

## 6. Surfaces and depth

| Level | Light | Dark | Used for |
| --- | --- | --- | --- |
| 0 – Canvas | `--canvas`, no shadow | `--canvas` | Page |
| 1 – Card | `--surface`, 1 px `--border`, shadow `0 1px 2px rgba(16,24,40,.04), 0 1px 3px rgba(16,24,40,.06)` | `--surface`, 1 px `--border`, **no shadow** (shadows disappear on dark), top inner highlight `inset 0 1px 0 rgba(255,255,255,.03)` | All cards |
| 2 – Hover | shadow `0 4px 12px rgba(16,24,40,.08)`, border `#D9DDE3` | border `#363C46`, surface `#1A1E24` | Clickable cards and rows |
| 3 – Overlay | `--surface-raised`, shadow `0 12px 32px rgba(16,24,40,.14)` | `--surface-raised`, border `#363C46`, shadow `0 12px 32px rgba(0,0,0,.5)` | Menus, tooltips, dialogs |

Card anatomy:

- **Radius:** 12 px (Modern) or 4 px (Classic, FR-48).
- **Padding:** 20 px (16 px on mobile).
- **Header row:** the title (`subtitle`), an optional caption, and a right-aligned action ("View all →") in
  `--text-secondary`.
- **Spacing:** 16 px between the header and the content.

**Avoiding box soup:**

- Never nest bordered boxes. Inside a card, groups are separated by 16–24 px of space or a 1 px `--divider`.
- KPI cards have no inner borders.
- Lists use dividers, not cards per row.
- Zones are not boxed. Each starts with an `overline` label and 32 px of space.

---

## 7. Data visualisation

### 7.1 Global chart style

| Element | Rule |
| --- | --- |
| Gridlines | Horizontal only, 1 px `--divider`, dashed `2 4`, at most 4 lines. No vertical gridlines. No chart border |
| Axes | No axis lines. Ticks in `caption` `--text-tertiary` with tabular figures. Y axis on the left, with at most 4 ticks at round values |
| Labels | Direct labels at the end of a bar or line instead of legends, when there are 5 or fewer series. A legend only for stacked bars, placed above the chart, left-aligned, with chip-style keys |
| Bars | Rounded ends of 4 px (`radius={[4,4,4,4]}` horizontal, `[4,4,0,0]` vertical), bar thickness 12–16 px, gap 40% |
| Lines and areas | 2 px stroke, `monotone` curve, area fill as a vertical gradient from 18% to 0% of the series colour. Dots hidden except the **current** (last) point: 4 px with a 2 px `--surface` ring |
| Current value | The last point or current period is highlighted. Other periods sit at 55% opacity in bar charts |
| Reference | Dashed 1 px `--text-tertiary` target line with an inline label ("Target 60%") |
| Tooltip | `--surface-raised`, radius 8 px, level-3 shadow, 12 px padding, a title in `caption`, then rows with a 8 px swatch, label and right-aligned tabular value. No cursor box: a 1 px vertical `--border-strong` guide instead |
| Empty | A neutral placeholder with one line of copy, never an empty axis |
| Accessibility | Each chart has `role="img"`, an `aria-label` summary ("Tasks: 46 total, 5 completed, 1 blocked"), and a "View as table" toggle that shows the same data in an accessible table |

### 7.2 Chart-by-chart

| Current chart | V25 form | Why |
| --- | --- | --- |
| Average progress (KPI) | **Radial ring**, 120 px, 10 px stroke, brand fill on `--surface-sunken` track, centre number `numeric-xl`, delta below | A single proportion reads fastest as a ring, and it feels like progress, which motivates |
| Goals by status | **One horizontal 100% stacked bar**, ordered Not started → In progress → Waiting → Blocked → Paused → Completed, with counts in a chip legend; Paused is drawn striped so it never reads as Not started | A part-to-whole for one entity compares better as a stacked bar than as a donut (length beats angle). Completed sits last, so the bar fills towards green |
| Tasks by status | Same stacked bar, under the goals bar, sharing its legend | Two bars on one scale make goals and tasks directly comparable |
| Tasks by priority | Footer line on "Next up" ("2 Critical · 7 High open"), plus a priority flag on each row | Priority only matters when choosing tasks, so a chart was noise |
| Dreams by area | **Small numbers in the Health card** (area name with a dot in its chart colour, then the count) | Counts of 0–10 don't need a chart |
| Vision Area progress | **Ranked horizontal progress bars**, lowest first, with a target tick, a percentage at the bar end and a chevron link | Answers decision 3 directly: the top row is the area needing attention |
| 12-week progress trend | **Area line** with a gradient fill, a target reference line and the current point highlighted with a label | A trend over time is a line. The target line shows whether you're on pace |
| Top obstacles | **Ranked bars by count**, with a severity chip beside each label, in the Insights tabs | Ranking plus severity answers "what's blocking most" |
| Partner pipeline | **4-stage horizontal pipeline**: To contact → Contacted / Waiting → Active / Agreed → Completed, with counts and the first 3 names of "follow up" partners, each stage clickable | A pipeline reads left to right. Names turn it into actions (decision 4) |
| Review heatmap | **12-week heatmap** on the sequential scale, 12 px cells with 3 px gaps, plus a **streak line** ("5-week weekly review streak") | The streak makes the habit visible and motivating, and the heatmap gives detail |

### 7.3 Reusable Recharts theme helper (specification)

A new file, `frontend/src/components/dashboard/charts/chartTheme.ts`, reads the CSS variables, so charts follow mode
and accent with no hex values in components:

```ts
// chartTheme.ts — one place for every Recharts style decision (D-25-6)
export function cssVar(name: string): string {
  return getComputedStyle(document.documentElement).getPropertyValue(name).trim();
}

export function useChartTheme() {
  const mode = useThemeMode();               // re-render on light/dark/accent change
  return useMemo(() => ({
    grid:   { stroke: cssVar('--divider'), strokeDasharray: '2 4', vertical: false },
    axis:   { axisLine: false, tickLine: false, tickMargin: 8,
              tick: { fill: cssVar('--text-tertiary'), fontSize: 12,
                      style: { fontVariantNumeric: 'tabular-nums' } } },
    bar:    { radius: [4, 4, 4, 4] as [number, number, number, number], maxBarSize: 16 },
    line:   { strokeWidth: 2, type: 'monotone' as const, dot: false,
              activeDot: { r: 4, strokeWidth: 2, stroke: cssVar('--surface') } },
    reference: { stroke: cssVar('--text-tertiary'), strokeDasharray: '4 4' },
    tooltip: { cursor: { stroke: cssVar('--border-strong'), strokeWidth: 1 },
               content: ChartTooltipContent },  // existing component, restyled
    categorical: [1, 2, 3, 4, 5].map((i) => cssVar(`--chart-${i}`)),
    other:  cssVar('--chart-other'),
    status: (s: StatusKey) => cssVar(`--status-${statusGroup(s)}`),
    animation: prefersReducedMotion()
      ? { isAnimationActive: false }
      : { isAnimationActive: true, animationDuration: 600, animationEasing: 'ease-out' as const },
  }), [mode]);
}

/** Vertical gradient for area fills: <defs>{areaGradient('trend', color)}</defs> */
export function areaGradient(id: string, color: string) { /* stop 0%: .18, stop 100%: 0 */ }
```

---

## 8. Micro-interactions and motion

| Token | Value | Use |
| --- | --- | --- |
| `--motion-fast` | 150 ms | Hover, press, colour changes |
| `--motion-base` | 200 ms | Card entry, tab switch, chip changes |
| `--motion-slow` | 250 ms | Overlays, expanding sections |
| `--ease-standard` | `cubic-bezier(0.2, 0, 0, 1)` | Fluent 2 "ease" |
| `--ease-exit` | `cubic-bezier(0.4, 0, 1, 1)` | Leaving elements |

- **Hover:** clickable cards rise to level 2 (shadow and border, no movement). Rows tint with `--surface-sunken`.
- **Press:** `transform: scale(0.98)` for 150 ms on buttons and chips. Cards don't scale.
- **Focus:** `:focus-visible` only. A 2 px `--focus-ring` with a 2 px offset, never removed, and it meets 1.4.11
  against both the canvas and the surface.
- **Skeletons:** the layout keeps its final shape while loading. Blocks in `--surface-sunken` have a 1.2 s shimmer, a
  5% lighter band. After 10 s, a calm "Still loading…" line appears.
- **Count-up:** KPI numbers count from their previous value (0 on first load) over 600 ms with ease-out, using
  tabular figures so the width is stable. The screen reader gets the final value only (`aria-live="off"` on the
  animated span, with the real value in `aria-label`).
- **Chart entry:** bars grow from the baseline and lines draw in, both over 600 ms, once per page load, and only when
  the chart enters the viewport.
- **Celebration (D-25-8):** when a goal reaches Completed, its row shows a 1.2 s confetti burst of 24 particles in
  the five chart colours, confined to the card. A toast reads "Goal completed: *title*. One step closer to *dream*."
  It plays once per goal, recorded in browser storage and wrapped in try/catch. It is off under
  reduced motion; the toast still shows.
- **Reduced motion:** under `@media (prefers-reduced-motion: reduce)`, every duration is set to 0 ms, count-ups show
  the final value, and charts render without animation. This is a hard rule, tested.

---

## 9. Empty, loading and error states

| State | Design and copy (EN; Khmer drafts in the review file) |
| --- | --- |
| **First run** (no vision area) | A full-width card with a simple line drawing (a path up a hill to a flag, two-colour, in brand and neutral), the heading "Let's map your first dream", the body "Three small steps and your dashboard comes alive.", and a checklist: ① Add a vision area ② Write a dream ③ Break it into a goal. A primary button opens the next step. This reuses the existing `GettingStarted` component with the new style |
| Areas but no tasks | The Today zone shows "No tasks yet. Pick a goal and add its first step." with a button, and Health shows the areas at 0% with a soft hint |
| All done today | "Nothing due today. Enjoy it, or get ahead:" followed by the next 3 upcoming tasks |
| Overdue / Blocked = 0 | The KPI shows a green `CircleCheck` with "All clear", in a neutral tone, never a celebratory colour block |
| Chart with no data | A neutral placeholder: "No reviews logged in the last 12 weeks. Your first one takes 5 minutes." with a link |
| Loading | Skeletons (section 8) |
| Error | An inline card banner: "We couldn't load this part of your dashboard." with "Try again", plus the error code in `caption` for support. The other cards stay usable (each card loads and fails on its own) |
| Coach view | The same states, worded for the coach ("Sokha hasn't added tasks yet."), with no action buttons (D-25-10) |

---

## 10. Responsive behaviour

| Element | Desktop (12 col) | Tablet (8 col) | Mobile (4 col) |
| --- | --- | --- | --- |
| Header | Greeting left; filters and "+ Task" right | Filters wrap below the greeting | Filters as two compact selects; "+ Task" becomes a floating button |
| Next up | 8 col | 5 col | Full width, position 2 |
| Overall progress | 4 col | 3 col | Compact row (ring 64 px), position 1 |
| KPI strip | 4 × 3 col | 4 × 2 col | Horizontal scroll-snap, 3 visible |
| Health | 7 + 5 col | 8 + 8 (stacked) | Stacked; areas show the lowest 3 with "Show all" |
| Insights | 7 + 5 col, tabs | 8 + 8, tabs | Separate cards in decision order; the trend is collapsed under "More insights" |
| Touch targets | — | 44 × 44 px minimum | 44 × 44 px minimum (WCAG 2.5.8 needs 24 px; 44 px is the Fluent guidance) |

At no width does the page scroll sideways (only the KPI strip scrolls, inside itself). Charts set their height from
the width, with a minimum height of 160 px.

---

## 11. Consistency and tokens

- **One source (D-25-6):** `frontend/src/design/tokens.ts` holds the light and dark values. From it:
  - `theme.ts` builds the MUI palette, the status colours and the default accent.
  - `global.css` carries the same values as CSS variables in `:root` and `[data-theme="dark"]`. `design/tokens.test.ts`
    parses the stylesheet and fails on any drift (the guard FR-47 already uses for `--palette-*`). The V25 names
    `--canvas`, `--surface`, `--text-primary` and `--text-secondary` are aliases of `--page`, `--card`,
    `--foreground` and `--muted-foreground`, so background tones and high contrast keep working.
  - `chartTheme.ts` reads the same variables at run time.
  - A test fails if any `.tsx` file under `components/dashboard` or `pages/DashboardPage.tsx` contains a hex colour.
- **Spacing (4 px base):** `0, 4, 8, 12, 16, 20, 24, 32, 40, 48, 64`. Only these values are allowed in dashboard
  components.
- **Radius:** `--radius-sm 6`, `--radius-md 8`, `--radius-lg 12` (cards), `--radius-xl 16` (dialogs),
  `--radius-pill 999`, with Classic overriding them all to 4 (FR-48 unchanged).
- **Icons:** lucide at 16 px inline, 20 px in buttons and headers, and 24 px only in empty states. Stroke 1.75.
  Icons always use `currentColor`.
- **Status chip:** `StatusChip` is the only way to show a status. It has an icon and a label, is 24 px high, uses
  `caption` weight 500, and comes in two variants: `subtle` (tinted background, the default) and `outline` (for
  dense tables). It replaces `StatusBadge` gradually; `StatusBadge` becomes a thin wrapper so nothing breaks.
- **Numbers:** one `formatNumber`, `formatPercent` and `formatDelta` in `utils/format.ts`, all locale-aware.

### New and changed components

| Component | Purpose |
| --- | --- |
| `KpiCard` | Label, big number, optional delta (`▲ 8 pts`, coloured only when meaningful), optional caption, and an optional "All clear" state; clickable to a filtered list |
| `ChartCard` | Card shell with title, caption, action, "View as table" toggle, skeleton, empty and error slots |
| `StatusChip` | Colour, icon and label (section 5.2) |
| `ProgressRing` | Radial progress (section 7.2) |
| `RankedProgressList` | Vision Area progress bars with a target tick |
| `StackedStatusBar` | Goal and task status bars with a chip legend |
| `NextUpList` | Today's actions with priority flag, due label, blocked reason and quick complete |
| `ZoneHeader` | Overline label with an optional right action |
| `DashboardPage` | Rebuilt around the three zones; existing `AttentionPanel`, `EnergyBudgetCard` and `GratitudeCard` move into Insights as optional cards, still behind their current settings |

---

## Functional requirements

| ID | Requirement | Priority |
| --- | --- | --- |
| DA-FR-01 | The Dashboard shows the Today, Health and Insights zones in that order (D-25-2) | Must |
| DA-FR-02 | "Next up" lists up to 5 open tasks: overdue first, then due today, then by priority and due date. Each row shows title, goal, priority flag, due label and, when blocked, the blocker reason | Must |
| DA-FR-03 | The overall progress ring shows average progress for the chosen area and period, with the change since the start of the period in points | Must |
| DA-FR-04 | The KPI strip shows Due this week, Overdue, Blocked and the portfolio counts line (D-25-3). Each KPI opens the matching filtered task list | Must |
| DA-FR-05 | Overdue and Blocked show "All clear" when 0 | Should |
| DA-FR-06 | Vision Area progress lists areas lowest first, with a target tick and a link to the area | Must |
| DA-FR-07 | Goals and tasks by status show as stacked status bars sharing one legend | Must |
| DA-FR-08 | Insights shows the trend chart with a target line, and tabs for Obstacles, Partners and Reviews | Must |
| DA-FR-09 | Partners shows a 4-stage pipeline with the first 3 partners to follow up. Each stage opens the filtered Partners page (as today) | Must |
| DA-FR-10 | Reviews shows the 12-week heatmap and the current weekly review streak | Should |
| DA-FR-11 | Every status uses `StatusChip` (colour, icon and label). *Open* obstacles use berry, *Blocked* tasks orange, *Waiting* indigo (D-25-4) | Must |
| DA-FR-12 | Every chart offers "View as table" and has an `aria-label` summary | Must |
| DA-FR-13 | First-run, empty, all-done, loading and error states follow section 9. Each card loads and fails on its own | Must |
| DA-FR-14 | A goal completed from any page triggers the celebration once (section 8) | Could |
| DA-FR-15 | The coach's read-only dashboard uses the same layout without action controls (D-25-10) | Must |
| DA-FR-16 | The Vision Area and period filters keep working as today and are kept in the URL | Must |

## Non-functional requirements

| ID | Requirement |
| --- | --- |
| DA-NFR-01 | WCAG 2.2 AA in light and dark mode for every accent: text 4.5:1, large text and non-text 3:1, focus visible (2.4.11), targets ≥ 24 px (2.5.8). Checked by axe in the Playwright suite and by an extended `palette-vars.test.ts` |
| DA-NFR-02 | Status and chart colours pass the colourblind check in a unit test: work statuses ≥ 8 ΔE apart under simulated deuteranopia and protanopia, and the categorical palette ≥ 10 ΔE apart |
| DA-NFR-03 | All motion is off under `prefers-reduced-motion` (tested) |
| DA-NFR-04 | The first contentful dashboard paint is no slower than today's. Charts below the fold load lazily. The Noto Sans Khmer subset loads only when the language is Khmer |
| DA-NFR-05 | No horizontal page scroll from 320 px to 2560 px |
| DA-NFR-06 | Khmer text never clips: line heights per section 4, checked in a Playwright snapshot at 360 px and 1280 px |
| DA-NFR-07 | No hex colour values in dashboard components (D-25-6, tested) |
| DA-NFR-08 | Interface text has Khmer drafts in `km-review-v25.csv` |

## Data and API changes

- No migration and no new endpoint.
- Optional: `GET /api/dashboard` adds `progressDeltaPoints` (the change in average progress since the period start)
  and `weeklyReviewStreak`. Both are computed from existing data. If not added, the frontend computes them from the
  existing trend and heatmap data, and the backend change is skipped.
- Frontend dependency: `@fontsource-variable/noto-sans-khmer`.

## Implementation phases

| Phase | Content | Behaviour change |
| --- | --- | --- |
| 1 | **Static HTML preview** (`docs/design/v25-dashboard-preview.html`) with realistic sample data, light and dark toggle, EN and KM sample text, for design sign-off | None (document only) |
| 2 | `tokens.ts`, CSS variable generation, `theme.ts` and `global.css` updated; status colour fix (D-25-4); Noto Sans Khmer; palette and colourblind tests | Colours change app-wide, and the status fix applies everywhere |
| 3 | Components: `KpiCard`, `ChartCard`, `StatusChip`, `ProgressRing`, `StackedStatusBar`, `RankedProgressList`, `NextUpList`, `ZoneHeader`, `chartTheme.ts`; unit tests | None until used |
| 4 | `DashboardPage` rebuilt around the three zones; charts restyled; empty, loading and error states; coach view; optional API fields | Dashboard redesign live |
| 5 | Motion, count-up, celebration, reduced-motion handling; responsive polish | Motion |
| 6 | `UAT_V25.md`, `km-review-v25.csv`, `USER_GUIDE.md` (dashboard section with new screenshots), CHANGELOG | None |

### Roadmap: quick wins → bigger upgrades

1. **Quick wins (phase 2, about 1–2 days):** fix the Waiting and Open colours, adopt the canvas and surface tokens,
   add tabular figures, quieter KPI labels and Noto Sans Khmer. Visible improvement with almost no layout risk.
2. **Medium (phases 3–4):** the new components, the three-zone layout, stacked status bars, the ranked area progress
   and the chart theme helper.
3. **Bigger upgrades (phase 5 and later):** motion and celebration, the review streak, "View as table" on every
   chart, and later a personalised "Next up" (for example, learning from tasks the user usually does first). That
   last item needs its own BRD.

## Acceptance criteria

| ID | Criterion |
| --- | --- |
| DAC-01 | A new tester can say, within 5 seconds of the dashboard loading, what their next task is and their overall progress (5 of 5 testers in moderated UAT) |
| DAC-02 | The page shows Today → Health → Insights on desktop; on a 360 px phone, cards follow the decision order in section 3 |
| DAC-03 | Blocked tasks show orange with `OctagonX`; Open obstacles show berry with `CircleAlert`; Waiting shows indigo with `Hourglass`; every chip has a label |
| DAC-04 | axe reports no colour-contrast violations on the dashboard in light and dark mode, for every accent and preset |
| DAC-05 | With reduced motion on, no element animates and numbers show their final values at once |
| DAC-06 | In Khmer, no text clips or overlaps at 360 px or 1280 px |
| DAC-07 | A new account sees the first-run card; after adding an area, dream and goal, the dashboard fills in with no empty axes |
| DAC-08 | A coach sees the same layout read-only, with no action buttons, and no new data compared with V24 |
| DAC-09 | The preview HTML is approved by the product owner before phase 4 starts |
| DAC-10 | Backend and frontend builds and tests pass (frontend with `--testTimeout=20000`) |

## Open questions

| ID | Question | Proposed answer |
| --- | --- | --- |
| Q-25-1 | Should the celebration be on by default? | Yes, off under reduced motion, from the device or from Appearance → Reduce motion (D-25-8). (The first draft also said "focus mode"; VMS has no focus-mode setting, so that condition was dropped in phase 5) |
| Q-25-2 | What is the "target" shown on the area bars and the trend line? | The expected progress for today's date, between the dream's start and its target date. If there is no target date, no tick is shown |
| Q-25-3 | Should the new canvas and surface colours apply to every page in phase 2, or only to the Dashboard first? | Every page. The tokens are shared, and a mixed look would be worse |
| Q-25-4 | Is the illustrated first-run drawing drawn in-house (SVG) or commissioned? | In-house simple SVG line drawing in the two token colours |

## Sign-off

| Role | Name | Date | Signature |
| --- | --- | --- | --- |
| Sponsor | | | |
| Product owner | | | |
| Design reviewer | | | |
| Accessibility reviewer | | | |
