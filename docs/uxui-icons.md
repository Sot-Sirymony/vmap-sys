# Icon Rules — FR-20.5 (V2.1)

**Library:** `lucide-react` only. Default stroke width (2) everywhere — never
override it.

## Size scale (four steps, nothing else)

| Size | Use | Examples |
|---|---|---|
| 14 | Dense contexts: inside MUI `Chip`, caption rows | Moonshot chip rocket, moonshot summary line |
| 16 | Inline with body text: table cells, buttons with labels, empty-state row form | Rocket next to a goal title, Inbox in EmptyState |
| 18 | Interactive controls and navigation | Sidebar items, header toggles, pagination arrows, modal close |
| 24 | Feature/hero spots: full-size empty states, first-run cards | Compass on the dashboard first-run card |

## Concept → icon vocabulary

[nav-items.ts](../frontend/src/components/layout/nav-items.ts) is the source
of truth: a concept uses the **same icon everywhere** it appears (nav, empty
states, cards, prompts).

| Concept | Icon | Why this one |
|---|---|---|
| Dashboard | LayoutDashboard | convention |
| Vision Area | Compass | direction for a life domain |
| Dream | MoonStar | dreaming; Sparkles now reads as "AI" |
| Goal | Target | universal goal metaphor |
| Step | Footprints | steps toward a goal; ListChecks was a near-twin of the Task icon |
| Task | CheckSquare | executable checkbox item |
| Partner | Handshake | partnership, not user administration |
| Obstacle | TrafficCone | a roadblock, distinct from generic warning triangles |
| Communication | Send | outreach to a partner |
| Review | ClipboardList | structured review questions |
| Insight | Lightbulb | lesson learned |
| Issue Report | Bug | app-defect reporting convention |
| Import/Export | FileSpreadsheet | Excel workbook |
| Moonshot | Rocket | aspirational reach |

## Rules

1. New icons for new concepts get added to the table above first.
2. Icons never carry meaning alone — always paired with a visible or
   accessible label (BR-17).
3. Decorative icons (next to visible text) take no `aria-label`; an
   icon-only control always does.
