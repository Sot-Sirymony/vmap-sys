# BRD – Interface styles (Modern, Classic, Liquid Glass)

Nov 23, 2026

## Document control

This is draft 26.0.0. It keeps every requirement in BRD 25.0.0 (the Dashboard redesign and its token source
`design/tokens.ts`) and the earlier BRDs, including FR-48 (interface style) and FR-39 (appearance settings). It makes the
three interface styles consistent on every screen, gives each one a clear purpose, and moves them onto one token layer.
No data, permission or plan behaviour changes. There is one schema change, to the default interface style.

| Item | Value |
| --- | --- |
| Document | Business Requirements Document (BRD) – Interface styles addendum |
| Version | 26.0.0 – Draft |
| Parent documents | BRD 25.0.0 (D-25-6 one token source, §6 surfaces), FR-48 (interface style), FR-39 (presets, high contrast, reduce motion), FR-40 (background tones), FR-42 (fonts), BR-35 (no appearance choice can make text unreadable) |
| Business owner | To be confirmed |
| Product owner | To be confirmed |
| Approvers | Sponsor, Product owner, Design reviewer, Accessibility reviewer |

| Version | Change | Author |
| --- | --- | --- |
| 26.0.2 | Built as specified after review: tooltips and toasts glass in Liquid Glass, header glass only once content scrolls under it, sunken Modern sidebar, per-style heatmap radius, Classic layout density on all pages. Phase 7 verification added (`e2e/interface-styles.e2e.ts`, `UAT_V26.md`). IS-NFR-04 is checked as main-thread work per frame, since headless Chrome cannot measure GPU blur cost; a real-phone check stays in UAT | Dev |
| 26.0.1 | Q-26-1 answered: keep CLASSIC. D-26-8 is replaced: V93 moves no account, so `MIGRATED` and the one-time notice are not built; `interface_style_source` holds `DEFAULT` or `CHOSEN`, and CHOSEN is set only when the style changes | BA / Dev |
| 26.0.0 | First draft from the product owner's design brief of 23 Nov 2026 (three interface styles). Findings come from the code at commit `b55ab0e`; glass opacity floors were calculated, not estimated | BA / Design |

---

## Executive summary

VMS offers three interface styles, Modern, Classic and Liquid Glass, but they are applied in four different ways:

- MUI theme overrides (`buildTheme`).
- CSS variables (`--radius-*`, `--shadow-*`).
- A hand-picked list of CSS classes that get the glass treatment.
- Header and sidebar code that checks the style by name.

Anything outside those paths ignores the style. That includes every V25 dashboard card, which is why Liquid Glass pages
look half glass, half solid.

The review also found four other faults:

1. **Glass can fail WCAG.** Glass panels are 60% opaque (light) and 66% (dark). Secondary text on them drops below 4.5:1
   whenever the backdrop is dark (light mode) or bright (dark mode). That happens over a user's background image, or
   when content scrolls under the sticky header. The calculated floor is about 85% (light) and 82% (dark).
2. **Classic keeps reverting.** The new-account default is Classic in the database but Modern in the app. A one-time,
   per-browser re-baseline converts stored Classic to Modern and saves it, but it is tracked per browser. A user who
   deliberately chose Classic is switched back to Modern the first time they sign in on a new browser.
3. **The same number means different radii.** 31 places use MUI's numeric `borderRadius: n`, which multiplies the
   style's base radius. `borderRadius: 2` is 8px in Classic, 24px in Modern and 28px in Liquid Glass, so corners
   disagree with the tokens.
4. **Nothing respects reduced transparency.** There is no `prefers-reduced-transparency` handling, no `@supports`
   fallback for browsers without `backdrop-filter`, and glass is nested: kanban columns are glass and so are the cards
   inside them.

V26 adds **one style token layer**, switched by `data-style` on `<html>` alongside `data-theme`. MUI, Tailwind,
Recharts and hand-written CSS all read it. Each style gets a purpose:

- **Modern** is the calm flagship.
- **Classic** is dense Fluent 2 for power users.
- **Liquid Glass** is expressive, with glass only on floating layers.

**Modern** stays the single default, and the database is aligned with it in one tracked migration.

---

## Decisions recorded by this BRD

| ID | Decision |
| --- | --- |
| D-26-1 | One style token layer (`--style-*`) on top of the BRD 25 base tokens, selected by `data-style="modern \| classic \| glass"` on `<html>` and combined with `data-theme` and `data-contrast`. `design/tokens.ts` holds the values; global.css carries them; a test keeps the two equal (as D-25-6) |
| D-26-2 | No component sets a radius, shadow, surface, blur or border colour of its own. They use the style tokens or a shared surface helper (`surface('card' \| 'floating' \| 'dense' \| 'inset')`). A unit test fails on literals and on MUI's numeric `borderRadius` |
| D-26-3 | Glass is a **floating-layer** material. It is used on the sidebar, sticky header, menus, popovers, tooltips, dialogs and the dashboard's Today hero card. Everything that holds dense or scrolling data stays solid: tables, lists, kanban, chart plot areas, forms and Next up (Apple HIG: Liquid Glass is for controls and navigation that float above content, not for content) |
| D-26-4 | Glass never nests: there is at most one blurred layer under any point. Children of a glass layer use a solid inset surface |
| D-26-5 | Glass opacity has a floor that keeps secondary text at 4.5:1 over any backdrop: 0.88 (light) and 0.85 (dark) where the backdrop is unknown (sticky header, popovers, any surface over a background image). It is 0.74 only over the plain app canvas, where the backdrop is known. Tertiary text is promoted to secondary on glass |
| D-26-6 | Glass falls back to solid under `prefers-reduced-transparency: reduce`, `prefers-contrast: more`, the app's High contrast setting, and in browsers without `backdrop-filter` (`@supports not`) |
| D-26-7 | **Modern is the only default** (database, server and app). Migration V93 changes the column default to MODERN and adds `interface_style_source` (`DEFAULT`, `CHOSEN`, `MIGRATED`), so a deliberate choice is tracked on the account, not per browser. The client-side re-baseline is removed |
| D-26-8 | Existing accounts that hold CLASSIC but never chose it are moved to MODERN once, by the migration, and shown a one-time notice offering Classic back. Whether to do this or keep their CLASSIC is Q-26-1 for the product owner. The recommendation is to migrate, because it matches what the app already does on each new browser |
| D-26-9 | Header anatomy and navigation shape stay tied to the style: Classic has breadcrumbs and rectangular navigation; Modern and Glass have the greeting header and pill navigation |
| D-26-10 | Density stays its own setting (FR-18.4), so a power user can have Modern + Compact. Classic tightens only the *layout* (card padding and gaps); the Density setting still decides control and table-row heights |
| D-26-11 | The brief's "focus mode" is not a VMS setting (corrected in BRD 25 phase 5). The style matrix covers mode, accent, preset, tone, font, density, text size, high contrast, reduce motion and language |

---

## 1. Diagnosis

### 1.1 Why V25 cards ignore Liquid Glass

| Path | What gets the style | What it misses |
| --- | --- | --- |
| `buildTheme` → `styleShape()` | MUI `Card`, `Dialog` and `Popover` get radius, shadow and, for glass, `cardBackground`/`backdrop-filter` | Any `Box` styled as a card; MUI `Paper`, `Menu` lists, `Tooltip` and `Drawer` are only partly covered |
| global.css `:root[data-style]` | `--radius-*` and `--shadow-*` | Surface colour, translucency, border and highlight. The `--glass-*` variables exist but nothing outside the class list uses them |
| global.css glass class list | `.metric-card`, `.empty-state`, `.auth-card`, `.kanban-column` and `.MuiDrawer-paper` | Everything else, including every V25 component |
| `Header.tsx`, `Sidebar.tsx` | They check `interfaceStyle !== 'classic'` in code | Nothing; they work, but by name, not by token |

The V25 dashboard cards are `Box` elements using `components/dashboard/surface.ts`, which sets `bgcolor: var(--surface)`
(solid), `border: var(--border)` and `boxShadow: var(--shadow-2)`. Radius and shadow follow the style through
the variables, but the surface never becomes translucent. So on a Liquid Glass page the old cards are glass and the V25
cards are solid.

Other inconsistencies found:

- Modern's MUI `Card` uses 16px (`--radius-xl`), while the V25 cards use 12px (`--radius-lg`). The BRD 25 §6 card radius
  is 12px.
- Glass shadows carry a white top highlight at 60% alpha in light mode, which shows as a hard line on light backdrops.

### 1.2 Components with their own styling (to refactor)

| Kind | Where | Count |
| --- | --- | --- |
| Own surface colour (`bgcolor: background.paper / var(--card) / var(--surface…)`) | `VisionMapTimeline`, `Sidebar`, `dashboard/surface.ts`, `ChartTooltipContent`, `ChartCard`, `MobileCollapse`, `AppearanceSettingsPage` | 7 files |
| Numeric MUI `borderRadius: n` (multiplies the style's base radius) | `AppearanceSettingsPage` (4), `VisionMapTimeline` (3), `AiPlanAssistant` (3), `TimelineAlternateViews`, `plan-builder/parts`, `InsightTabs`, `ModuleHelpCard` (2 each), and 1 each in `PartnerDetailPage`, `AssessmentPage`, `ReviewStage`, `Sidebar`, `AppearanceMenu`, `RankedProgressList`, `ChartTooltipContent`, `ProgressBar`, `DataTable`, `PitchCoachCard`, `ModuleContentAiCard`, `DreamCoachChat`, `AiStartAssistant` | 31 uses in 20 files |
| Literal shadow | `DashboardCard` (`0 1.6px 3.6px rgba(…)`) | 1 |
| Style checked by name | `Header.tsx`, `Sidebar.tsx` | 2 |
| Glass by class list | global.css (5 selectors), including `.kanban-column`, which nests under glass cards | 1 block |

### 1.3 The default mismatch, exactly

- **Database:** `V20__user_interface_style.sql` sets `NOT NULL DEFAULT 'CLASSIC'`, and `AppUser.interfaceStyle` defaults
  to `CLASSIC`, as does the response mapper's null fallback.
- **App:** `ThemeModeContext` defaults to `modern`. `rebaselineInterfaceStyle` turns a stored Classic into Modern once
  per browser (flag `vms-style-rebaseline` in localStorage) and saves MODERN back to the account.
- **Effect:** each new browser repeats the conversion, so a deliberate Classic choice survives only on the browser
  where it was made.
- **Local data:** 69 CLASSIC, 3 MODERN, 2 LIQUID_GLASS, almost all test accounts. Production numbers are needed before
  the migration (Q-26-1).

---

## 2. Style definitions

| | **Modern** (default) | **Classic** | **Liquid Glass** |
| --- | --- | --- | --- |
| Purpose | The flagship: calm, spacious, motivating; the V25 dashboard as designed | Clarity and density for heavy daily use; close to Microsoft 365 / Fluent 2 "productive" surfaces | Expressive and premium for people who enjoy a personalised look, especially with a background image |
| Target user | Most participants; anyone new | Power users, coaches and admins who scan many rows; people on small laptop screens; anyone who finds rounded, soft UI vague | People who want the app to feel personal; tablet and phone users on modern devices |
| Principles | Space does the work; one soft elevation; colour only for meaning (BRD 25) | Crisp edges, hairline borders, tight shadows, compact layout; breadcrumbs for orientation (Fluent 2 rest/hover elevation, 4px corners) | Glass only on floating layers; content stays solid; motion feels physical; readability first (Apple HIG Materials, WCAG 1.4.3 and 1.4.11) |
| Header / nav | Greeting header, pill navigation | Breadcrumbs, rectangular navigation with a 3px accent bar on the selected item | Greeting header, pill navigation on a glass sidebar |

---

## 3. Token matrix

All values sit in `design/tokens.ts` (`styleTokens[style][mode]`) and global.css under
`:root[data-style="…"]` / `:root[data-theme="dark"][data-style="…"]`. "=" means the same as light.

### 3.1 Shape

| Token | Modern | Classic | Glass |
| --- | --- | --- | --- |
| `--style-radius-control` (buttons, inputs, selects) | 8px | 4px | 12px |
| `--style-radius-card` | 12px | 4px | 18px |
| `--style-radius-chip` | 999px | 4px | 999px |
| `--style-radius-dialog` | 16px | 4px | 24px |
| `--style-radius-menu` (menus, popovers, tooltips) | 8px | 4px | 14px |
| `--style-nav-shape` | pill (999px) | rect (4px) + 3px accent bar | pill (999px) |
| `--style-header` | greeting | breadcrumbs | greeting |

Legacy `--radius-sm/md/lg/xl/pill` become aliases of these, so existing CSS keeps working.

### 3.2 Elevation

| Token | Modern light | Modern dark | Classic light | Classic dark | Glass light | Glass dark |
| --- | --- | --- | --- | --- | --- | --- |
| `--style-shadow-rest` (cards) | `0 1px 2px rgba(16,24,40,.04), 0 1px 3px rgba(16,24,40,.06)` | none (border carries the edge) | `0 0.6px 1.8px rgba(0,0,0,.10), 0 0.3px 0.9px rgba(0,0,0,.06)` | `0 0.6px 1.8px rgba(0,0,0,.40)` | `0 6px 18px -6px rgba(16,24,40,.12)` | `0 6px 18px -6px rgba(0,0,0,.45)` |
| `--style-shadow-hover` | `0 4px 12px rgba(16,24,40,.08)` | none; border `#363C46` | `0 1.6px 3.6px rgba(0,0,0,.13), 0 0.3px 0.9px rgba(0,0,0,.10)` | `0 1.6px 3.6px rgba(0,0,0,.45)` | `0 10px 30px -6px rgba(16,24,40,.16)` | `0 10px 30px -6px rgba(0,0,0,.55)` |
| `--style-shadow-overlay` (menus, dialogs) | `0 12px 32px rgba(16,24,40,.14)` | `0 12px 32px rgba(0,0,0,.50)` | `0 6.4px 14.4px rgba(0,0,0,.13), 0 1.2px 3.6px rgba(0,0,0,.08)` | `0 6.4px 14.4px rgba(0,0,0,.50)` | `0 28px 64px -12px rgba(16,24,40,.26)` | `0 32px 72px -12px rgba(0,0,0,.70)` |

### 3.3 Surfaces

| Token | Modern | Classic | Glass light | Glass dark |
| --- | --- | --- | --- | --- |
| `--style-card-bg` | `var(--surface)` | `var(--surface)` | `var(--surface)` (cards stay solid, D-26-3) | = |
| `--style-card-border` | `1px solid var(--border)` | `1px solid var(--border)` | `1px solid var(--border)` | = |
| `--style-card-wash` | none (the V24 accent wash is dropped; BRD 25 colour-for-meaning) | none | none | none |
| `--style-floating-bg` (sidebar, header, menus, dialogs, hero) | `var(--surface-raised)` | `var(--surface-raised)` | `color-mix(in srgb, var(--surface) 88%, transparent)` | `color-mix(in srgb, var(--surface-raised) 85%, transparent)` |
| `--style-floating-bg-canvas` (glass over the plain canvas only) | = floating | = floating | `… 74% …` | `… 74% …` |
| `--style-blur` | none | none | `blur(20px) saturate(1.4)` | `blur(20px) saturate(1.2)` |
| `--style-floating-border` | `1px solid var(--border)` | `1px solid var(--border)` | `1px solid rgba(255,255,255,.55)` | `1px solid rgba(255,255,255,.10)` |
| `--style-highlight` (top edge) | none | none | `inset 0 1px 0 rgba(255,255,255,.35)` | `inset 0 1px 0 rgba(255,255,255,.06)` |
| `--style-inset-bg` (children of floating layers) | `var(--surface-sunken)` | `var(--surface-sunken)` | `var(--surface-sunken)` (solid, D-26-4) | = |

The opacity floors come from calculation (sRGB compositing, worst-case backdrop):

| Text on glass | Light: worst backdrop black | Dark: worst backdrop white |
| --- | --- | --- |
| Primary (`#1B1F24` / `#E8EAED`) | ≥ 0.53 | ≥ 0.66 |
| Secondary (`#57606A` / `#A8B0BA`) | ≥ 0.85 | ≥ 0.82 |
| Tertiary (`#646C78` / `#8E97A3`) | ≥ 0.93 | ≥ 0.91 → promoted to secondary on glass |

0.88 and 0.85 give a margin over the secondary-text floor. 0.74 is used only where the backdrop is the app canvas (near
white in light, near black in dark), where any opacity passes.

### 3.4 Density (layout only, D-26-10)

| Token | Modern | Classic | Glass |
| --- | --- | --- | --- |
| `--style-card-pad` | 20px (16px < 600px) | 12px | 20px (16px < 600px) |
| `--style-gap` (grid) | 24px / 16px / 12px | 12px / 12px / 8px | 24px / 16px / 12px |
| `--style-zone-gap` | 32px | 20px | 32px |

Control heights and table-row heights stay with the Density setting: Comfortable gives 36px controls and 48px rows,
Compact gives 32px and 36px.

### 3.5 Motion

| Token | Modern | Classic | Glass |
| --- | --- | --- | --- |
| `--style-motion-fast` / `base` / `slow` | 150 / 200 / 250 ms | 100 / 150 / 200 ms (Fluent "productive") | 200 / 250 / 350 ms |
| `--style-ease` | `cubic-bezier(0.2, 0, 0, 1)` | `cubic-bezier(0.33, 0, 0.67, 1)` | `cubic-bezier(0.2, 0.9, 0.1, 1)` |
| Press scale | 0.98 | none (colour change only) | 0.97 |

All of these are zeroed under `prefers-reduced-motion` and Appearance → Reduce motion, as today.

### 3.6 Charts

| Token | Modern | Classic | Glass |
| --- | --- | --- | --- |
| `--style-chart-grid-width` | 1px dashed `2 4` | 1px solid | 1px dashed `2 4` |
| `--style-chart-bar-radius` | 4px | 0px | 6px |
| `--style-chart-tooltip-bg` | `var(--surface-raised)` | `var(--surface-raised)` | `var(--style-floating-bg)` + blur (one floating layer) |
| `--style-chart-tooltip-radius` | 8px | 4px | 14px |

---

## 4. Component specs

| Component | Modern | Classic | Liquid Glass |
| --- | --- | --- | --- |
| Card (incl. V25 KPI, chart, insight cards) | Solid surface, hairline border, rest shadow, 12px, 20px padding; hover lift on clickable cards | Solid, border, Fluent shadow, 4px, 12px padding; hover darkens the border only | Solid surface, 18px, deeper soft shadow; **not** glass (D-26-3), so data stays crisp |
| Today hero card (progress ring) | As card | As card | **Glass** (`floating-bg-canvas`, blur, highlight), the one glass card on the page |
| Sidebar | Solid sunken panel, pill selection | Solid, rectangular selection with a 3px accent bar | Glass (`floating-bg`), pill selection; items solid on hover |
| Header | Greeting, solid on scroll | Breadcrumbs, solid | Greeting, glass when content scrolls under it (`floating-bg` 0.88) |
| Menus, popovers, tooltips | Raised solid, overlay shadow, 8px | Raised solid, Fluent shadow, 4px | Glass, 14px, overlay shadow |
| Dialogs | Raised solid, 16px; backdrop scrim 40% | Raised solid, 4px; scrim 40% | Glass panel 24px over a 30% scrim; inputs inside are solid (`inset-bg`) |
| Buttons and inputs | 8px; focus ring 2px brand + 2px offset | 4px; focus ring 2px brand + 1px offset | 12px; same focus ring, never blurred |
| Status chips | Pill, tinted (BRD 25 §5.2) | 4px, tinted | Pill, tinted, always solid |
| Tables, lists, kanban, Next up | Solid | Solid, tighter | Solid. Kanban columns stop being glass (they nest cards and scroll) |
| Charts | BRD 25 §7 | Solid gridlines, square bars | 6px bars; glass tooltip only |
| Empty states | Solid card | Solid card | Solid card (they hold body text) |
| Heatmap | Cells with 3px radius | 1px radius | 4px radius |

---

## 5. Liquid Glass usage rules

**Allowed (floating layers):** sidebar, sticky header, menus, popovers, tooltips, dialogs, toasts, and the dashboard's
Today hero card.

**Not allowed (content):**

- tables, lists, kanban columns and cards, and Next up
- chart plot areas
- forms and inputs
- empty states and long text (guides, reports, journal)
- anything inside a scrolling container

**Safeguards**

1. Opacity floors are set by D-26-5 and enforced by a unit test that composites each floating token over black and
   white and checks 4.5:1 for primary and secondary text.
2. Tertiary text on glass is rendered as secondary.
3. With a background image on, glass uses the 0.88 / 0.85 floors everywhere, and the existing background wash applies
   underneath.
4. Glass never nests (D-26-4). A test checks that no glass surface renders inside another.
5. Glass falls back to solid under High contrast, `prefers-contrast: more`, `prefers-reduced-transparency: reduce`, and
   `@supports not (backdrop-filter: blur(1px))`.
6. Performance:
   - At most 4 blurred layers on screen (sidebar, header, one overlay, hero).
   - Blur radius 20px, with `will-change` only while an overlay animates.
   - Below 600px the hero card and sidebar drawer use the solid fallback; the header keeps glass.
   - Blur inside scrolling lists or charts is not allowed.

---

## 6. Default and migration plan

1. **Pick Modern.** The V25 dashboard was designed in it. The app already defaults to it and silently converts most
   accounts. Fluent 2's own default corner radius for cards is in the 8–12px range, which Modern matches.
2. **Migration `V93__interface_style_default.sql`:**
   - `ALTER TABLE app_users ALTER COLUMN interface_style SET DEFAULT 'MODERN'`.
   - Add `interface_style_source VARCHAR(10) NOT NULL DEFAULT 'DEFAULT'` (`DEFAULT`, `CHOSEN`, `MIGRATED`).
   - Rows with `interface_style = 'CLASSIC'` become `MODERN` with source `MIGRATED` (subject to Q-26-1).
   - Rows already MODERN or LIQUID_GLASS keep their value with source `CHOSEN`, since those were opt-ins.
3. **Server:**
   - `AppUser` defaults to `MODERN`.
   - A `PUT /preferences/appearance` that includes `interfaceStyle` sets the source to `CHOSEN`.
   - The response gains `interfaceStyleSource`.
4. **App:**
   - Remove `rebaselineInterfaceStyle` and the `vms-style-rebaseline` flag.
   - When the source is `MIGRATED`, show a one-time notice: "Your app now uses the Modern look. Prefer Classic? Change it
     in Appearance." Dismissing it, or picking a style, sets `CHOSEN`.
5. **Test accounts:** no separate handling. The migration treats every account the same, so production behaviour is
   what UAT sees.

---

## 7. Appearance settings: interface style picker

- Three cards side by side (stacked on phones). Each shows:
  - a live mini preview drawn from the real tokens (a header strip, a nav pill or bar, one card, one chip and a
    two-line table), in the current mode and accent
  - the style's name, and a one-line "who it suits"
  - selection state (radio semantics, arrow keys to move)
- One-line descriptions:
  - **Modern:** "Calm and spacious. The recommended look."
  - **Classic:** "Compact and crisp, for scanning lots of rows."
  - **Liquid Glass:** "Frosted panels that float over your background."
- Under Liquid Glass, a note appears when it falls back to solid: "Your device asks for less transparency, so panels
  stay solid."
- The existing side-by-side StylePreview is replaced by the three thumbnails.

---

## Functional requirements

| ID | Requirement | Priority |
| --- | --- | --- |
| IS-FR-01 | `data-style` on `<html>` takes `modern`, `classic` or `glass` (renamed from `liquidGlass`; the wire value `LIQUID_GLASS` is unchanged) and switches every style token | Must |
| IS-FR-02 | Every card, button, input, chip, table, dialog, menu, tooltip, sidebar, header, empty state and V25 dashboard component takes its radius, shadow, surface, blur and border from style tokens or the shared surface helper | Must |
| IS-FR-03 | Liquid Glass applies glass only to the layers in §5 and never nests | Must |
| IS-FR-04 | Glass meets the opacity floors (D-26-5) and falls back to solid per D-26-6 | Must |
| IS-FR-05 | Classic shows breadcrumbs and rectangular navigation; Modern and Glass show the greeting header and pill navigation, decided by the `--style-header` / `--style-nav-shape` tokens via the hook, not by style name | Must |
| IS-FR-06 | `createAppTheme({ mode, style, accent, font, density, highContrast, tone })` builds the MUI theme from the tokens (replacing `buildTheme`'s positional parameters) | Must |
| IS-FR-07 | `useInterfaceStyle()` returns the active style, its tokens and whether glass is currently active (after fallbacks), and sets the style with persistence through the existing appearance save | Must |
| IS-FR-08 | `chartTheme` reads the chart style tokens (grid weight, bar radius, tooltip surface) | Must |
| IS-FR-09 | Default MODERN everywhere; migration V93 with `interface_style_source`; the client re-baseline is removed; a one-time notice for MIGRATED accounts | Must |
| IS-FR-10 | The Appearance interface-style picker per §7 | Should |
| IS-FR-11 | Motion tokens per style (§3.5), all zeroed under reduced motion | Should |

## Non-functional requirements

| ID | Requirement |
| --- | --- |
| IS-NFR-01 | WCAG 2.2 AA in every style × mode × accent × tone × high-contrast combination: text 4.5:1 (1.4.3), UI and graphics 3:1 (1.4.11), focus visible (2.4.7, 2.4.11). Checked by axe in Playwright per combination, and by a token unit test that composites glass over black and white |
| IS-NFR-02 | No hard-coded radius, shadow, surface, blur or border colour in components (D-26-2), enforced by a unit test that scans `src/components` and `src/pages` |
| IS-NFR-03 | At most 4 `backdrop-filter` layers on screen; none inside scrolling containers. A Playwright check counts them on the Dashboard, Tasks board and Vision Map |
| IS-NFR-04 | Scrolling stays smooth on a mid-range phone: Dashboard scroll frames under 16 ms p95 in Chrome's 4× CPU throttle profile with Liquid Glass on |
| IS-NFR-05 | Khmer layouts do not clip in any style (Classic's tighter padding included); Playwright snapshots at 360px and 1280px |
| IS-NFR-06 | Interface text has Khmer drafts in `km-review-v26.csv` |

## Data and API changes

- Migration `V93__interface_style_default.sql` (§6).
- `AppearancePreferencesResponse` adds `interfaceStyleSource`; requests are unchanged.
- No new endpoint.

## Implementation phases

| Phase | Content | Behaviour change |
| --- | --- | --- |
| 1 | **Style preview** (`docs/design/v26-interface-styles-preview.html`): the Dashboard, a table page and a dialog in all three styles × light/dark, with the glass fallbacks toggleable, for sign-off | None |
| 2 | Style tokens in `design/tokens.ts` and global.css (`--style-*`, legacy aliases), the glass contrast and sync tests, `prefers-reduced-transparency` / `prefers-contrast` / `@supports` fallbacks | Small: Classic and Glass radii and shadows align to the matrix |
| 3 | `createAppTheme`, `useInterfaceStyle`, the shared `surface()` helper; refactor the V25 dashboard components and the 7 surface files, 31 numeric radii and 1 literal shadow; header and sidebar read the tokens; the hard-coded-values test | Glass consistent everywhere |
| 4 | Glass placement per §5 (hero card, header, overlays; kanban becomes solid), chart style tokens, per-style motion | Liquid Glass reworked |
| 5 | Migration V93, server source tracking, re-baseline removal, the one-time notice | Default unified |
| 6 | Appearance picker with live thumbnails (§7) | New picker |
| 7 | Playwright visual snapshots (Dashboard, Tasks, Vision Map, a dialog) × 3 styles × 2 modes × 2 languages, axe per combination, blur-layer count, `UAT_V26.md`, `km-review-v26.csv`, user guide (adds Liquid Glass, which it does not mention today), CHANGELOG | None |

### Roadmap: quick wins → bigger changes

1. **Quick wins (phase 2–3, small diffs):**
   - Make the V25 cards and the hero card style-aware.
   - Raise glass opacity to the floors.
   - Add the reduced-transparency and `@supports` fallbacks.
   - Replace the 31 numeric radii.
2. **Medium:** the token layer and `createAppTheme`, glass placement rules, and the default migration.
3. **Larger:** the picker with live thumbnails, visual-regression coverage across the matrix, and per-style motion.

## Acceptance criteria

| ID | Criterion |
| --- | --- |
| ISC-01 | On every main page, switching style changes every surface consistently; with Liquid Glass no V25 card or data surface is translucent and the layers in §5 are |
| ISC-02 | axe reports no contrast violations for any style × mode × accent combination, including Liquid Glass with a dark and with a bright background image |
| ISC-03 | With `prefers-reduced-transparency: reduce` (or High contrast, or a browser without backdrop-filter), Liquid Glass renders solid and the picker says why |
| ISC-04 | The hard-coded-values test passes and fails when a literal radius, shadow or surface is added to a component |
| ISC-05 | No more than 4 blurred layers on any tested page; none inside a scrolling list or chart |
| ISC-06 | A new account gets Modern; an account that picks Classic keeps it on every browser; a migrated account sees the notice once |
| ISC-07 | Classic shows breadcrumbs and rectangular navigation; Modern and Glass show the greeting and pills |
| ISC-08 | Visual snapshots are approved for each style × mode × language on the Dashboard, Tasks board, Vision Map and a dialog |
| ISC-09 | Frontend and backend builds and tests pass |

## Open questions

| ID | Question | Proposed answer |
| --- | --- | --- |
| Q-26-1 | Move existing CLASSIC accounts to MODERN (with a one-time notice), or keep CLASSIC and only fix the default for new accounts? Production counts are needed | Move them with the notice. The app already does this per browser, so the migration makes it happen once, honestly . **Answered 23 Nov 2026: keep CLASSIC.** V93 changes only the default for new accounts; no stored style is changed, and no notice is shown |
| Q-26-2 | Should Liquid Glass make the KPI cards glass too, as the brief allows? | No. Only the hero card, to stay within the blur budget and keep numbers crisp; revisit after UAT |
| Q-26-3 | Should picking Classic also switch Density to Compact? | No. Keep them independent (D-26-10), but show a hint under Classic: "Pair with Compact density for the densest view" |
| Q-26-4 | Rename the internal id `liquidGlass` to `glass`? | Yes, in code and CSS only; the stored value `LIQUID_GLASS` stays |

## Sign-off

| Role | Name | Date | Signature |
| --- | --- | --- | --- |
| Sponsor | | | |
| Product owner | | | |
| Design reviewer | | | |
| Accessibility reviewer | | | |
