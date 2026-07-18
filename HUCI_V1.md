# HUCI V1 — Human-Computer Interaction Improvement Plan

**Project:** Vision Mapping Management System (VMS)
**Version target:** V2.1 "Ease of Use" release
**Date:** 2026-07-18
**Status:** Study / planning document — no code changes yet

---

## 1. Purpose

This document is a detailed study plan to improve the **usability and ease of use** of the current VMS application, based on Human-Computer Interaction (HCI) principles. The system is functionally complete (V2.0.0), but functional completeness is not the same as ease of use. This plan identifies where users struggle, why, and what to change — ordered by impact and effort.

Guiding question for every change:

> "Can a first-time user go from an empty account to one Dream with Goals, Steps, and Tasks in under 10 minutes, without instructions?"

---

## 2. Current State Assessment

### 2.1 What the UI already does well

| Strength | Evidence |
|---|---|
| Consistent component library | MUI v9 + shared `components/common/` (DataTable, StatusBadge, PriorityBadge, ProgressBar, Modal, EmptyState) |
| Dark / light theme | `ThemeModeContext`, `AppearanceMenu` (FR-18) |
| Feedback on actions | `ToastContext`, `ConfirmDialog` before destructive actions |
| State kept in URL | `useUrlFilter` — filters survive refresh and are shareable |
| Kanban with drag-and-drop | `StatusBoard.tsx` (HTML5 drag events) |
| Empty states exist | `EmptyState.tsx` used on list pages |
| Some ARIA usage | `aria-` attributes in Modal, DataTable, SearchBar, and others |
| Responsive groundwork | `use-mobile.ts` hook, collapsible Sidebar |

### 2.2 Main HCI problems (hypotheses to verify in Phase A)

**P1 — Deep hierarchy is heavy for new users (severity: High)**
The core model is 5 levels deep (Area → Dream → Goal → Step → Task). A new user faces an empty dashboard and 11 sidebar entries with no guidance on where to start or in what order. There is no onboarding, no sample data, no "create your first dream" flow.

**P2 — Navigation is flat while the data is hierarchical (severity: High)**
The sidebar lists Areas, Dreams, Goals, Steps, Tasks as separate flat pages. To build one dream a user must visit 4–5 different pages, re-selecting parent context each time. The Vision Map tree (`DreamDetailPage`) exists but is not the primary working surface.

**P3 — Form cost is high (severity: Medium-High)**
Creating a Task requires title, owner, due date, priority, status (business rule 5). Every creation goes through a modal form. There is a `QuickAddTitle` in the vision map, but list pages have no quick-add; repetitive entry (e.g. 7 steps of a goal) means 7 modal round-trips.

**P4 — Feedback is transactional, not orienting (severity: Medium)**
Toasts confirm single actions, but the system does not surface "what should I do next": rule 11 (suggest completing a parent when all children are done), overdue/blocked prompts, and coaching questions from the method are only partially present in the UI.

**P5 — Accessibility is partial (severity: Medium)**
Drag-and-drop on the Tasks board has no keyboard alternative. ARIA usage is inconsistent across pages. Color-only signals (red = overdue, orange = blocked) may lack a second channel (icon/text). Contrast in dark mode has not been audited.

**P6 — Mobile experience unverified (severity: Medium)**
`use-mobile.ts` exists, but dense tables (`DataTable`, 290 lines) and a 6-column kanban are inherently hard on small screens. No mobile-specific layouts (card list fallback, column pager).

**P7 — No keyboard efficiency layer for returning users (severity: Low-Medium)**
No shortcuts (global search, "new task", navigate), no command palette. Daily-review users will feel this after week one.

---

## 3. HCI Framework Used

The plan evaluates and designs against three standard lenses:

1. **Nielsen's 10 Usability Heuristics** — the audit checklist (Section 5).
2. **Norman's Action Cycle** — gulf of execution ("how do I add a step?") and gulf of evaluation ("did my progress update?").
3. **WCAG 2.2 AA** — accessibility target (contrast, keyboard, focus, labels).

Plus one project-specific principle from the Vision Mapping method itself: **the UI should coach, not just store** (CLAUDE.md "AI-Like Coaching Behavior").

---

## 4. Study Phases

### Phase A — Measure (1 week, no code changes)

Goal: turn the hypotheses in 2.2 into confirmed, ranked findings.

| # | Activity | Method | Output |
|---|---|---|---|
| A1 | Heuristic evaluation | Walk all 15 pages against Nielsen's 10 heuristics; log each violation with severity 1–4 | Findings table (Section 5 template) |
| A2 | Cognitive walkthrough | Script the "Ideal User Flow" from CLAUDE.md (Area → Dream → 4 Goals → Steps → Tasks) and count: clicks, page changes, form fields, errors | Click/step count baseline |
| A3 | First-use test | 3–5 people who have never seen the app; task: "Create a dream about learning X and break it down to tasks." Think-aloud, screen recording | Time-to-first-task metric, confusion points |
| A4 | Accessibility audit | Keyboard-only pass on every page; axe DevTools / Lighthouse on top 6 pages; contrast check both themes | WCAG issue list |
| A5 | SUS baseline | System Usability Scale questionnaire after A3 | Baseline SUS score |

**Key metrics to record (before → after targets):**

| Metric | Baseline | Target |
|---|---|---|
| Time: empty account → first complete Dream tree | measure | ≤ 10 min |
| Clicks: create 1 goal + 3 steps + 5 tasks | measure | −40 % |
| SUS score | measure | ≥ 75 |
| Lighthouse accessibility (top 6 pages) | measure | ≥ 95 |
| Task board usable with keyboard only | No | Yes |

### Phase B — Quick Wins (1–2 weeks)

Low-effort, high-impact fixes. Each item lists the touched files.

**B1. Guided empty dashboard.**
When counts are zero, replace stat cards with a 3-step "Get started" checklist (Create an Area → Add a Dream → Break it down) with direct action buttons.
Files: `pages/DashboardPage.tsx`, `components/dashboard/DashboardSummary.tsx`, new `components/dashboard/GettingStarted.tsx`.

**B2. Contextual empty states with actions.**
Every `EmptyState` gets a primary action ("No goals yet — Add a goal to *{dream}*") instead of passive text.
Files: `components/common/EmptyState.tsx` + call sites on list pages.

**B3. Quick-add rows on list pages.**
Reuse `QuickAddTitle` pattern: type a title, press Enter, entity is created with sensible defaults (priority = Medium, status = Not Started, owner = me, due date = blank until required). Full form remains available via "Edit".
Files: `components/vision-map/QuickAddTitle.tsx` → promote to `components/common/QuickAdd.tsx`; Goals/Steps pages.

**B4. Second visual channel for state.**
Add icon + text to color signals: overdue (clock icon + "Overdue"), blocked (alert icon), completed (check). Ensures color-blind safety.
Files: `StatusBadge.tsx`, `PriorityBadge.tsx`, `TasksBoardPage.tsx`.

**B5. Breadcrumb context header.**
On Goal/Step/Task views show the ancestry: `Career › Become a researcher › Learn AI tools`. Each segment navigates.
Files: `components/layout/Header.tsx` or new `components/common/Breadcrumbs.tsx`.

**B6. Consistent default sort + remembered view.**
Persist per-page view choices (table/board via `ViewToggle`, page size, show-archived) in localStorage so users don't re-configure every visit.
Files: `hooks/useUrlFilter.ts` (add storage fallback), `ViewToggle.tsx`, `ShowArchivedToggle.tsx`.

**B7. Form friction pass.**
Autofocus first field in every modal; Enter submits; Esc cancels (verify in `Modal.tsx`); inline validation on blur instead of on submit; keep the modal open with an "Add another" option for repetitive entry.
Files: `components/common/CrudModalForm.tsx`, `components/forms/*`.

### Phase C — Structural Improvements (2–4 weeks)

**C1. Make the Vision Map the primary workspace.**
Promote `DreamDetailPage` / `VisionMapTree` from a viewer to a full editor: inline add/rename at every level, expand/collapse memory, keyboard navigation (arrows to move, Enter to edit, N for new child). The flat pages remain for filtering/bulk work, reframed as "list views".
Files: `components/vision-map/*`, `pages/DreamDetailPage.tsx`.

**C2. Sidebar information architecture.**
Group 11 flat items into 3 sections to match the mental model:
- **Plan:** Dashboard, Vision Map (areas/dreams entry point)
- **Execute:** Tasks, Steps, Goals, Obstacles
- **Support:** Partners, Communication, Reviews, Import/Export
Files: `components/layout/nav-items.ts`, `Sidebar.tsx`.

**C3. Guided dream-creation wizard (the coaching flow).**
Replace the flat Dream form with a 3-step wizard that asks the CLAUDE.md coaching questions (What exactly? Why? What does success look like? When?), then offers to add first goals immediately. This is the single biggest "coach, not store" change.
Files: new `components/forms/DreamWizard.tsx`, `pages/DreamsPage.tsx`.

**C4. Next-action nudges.**
Implement rule 11 in the UI: when the last child task completes, show a non-blocking prompt "All tasks done — mark step S-003 completed?". Extend `AttentionPanel` into a "Needs attention" feed: overdue, blocked-without-partner, complex-steps-without-tasks.
Files: `components/dashboard/AttentionPanel.tsx`, task mutation paths in `hooks/useCrudEntity.ts`.

**C5. Keyboard-accessible kanban.**
Add a move menu on each card (via `RowActionsMenu`) and keyboard support: focus card → M → choose column. Announce moves with an ARIA live region. Drag-and-drop stays as the pointer path.
Files: `components/common/StatusBoard.tsx`.

**C6. Mobile layouts.**
Tables collapse to card lists under the mobile breakpoint; kanban becomes one column with a status switcher; sidebar becomes a bottom sheet or drawer (verify current behavior first).
Files: `components/common/DataTable.tsx`, `StatusBoard.tsx`, `Sidebar.tsx`, `hooks/use-mobile.ts`.

### Phase D — Efficiency & Delight (2 weeks, optional for V2.1)

- **D1. Command palette** (Ctrl/Cmd+K): jump to any page or entity, "new task", "new dream".
- **D2. Global shortcuts:** `g d` dashboard, `g t` tasks, `n` new item in context, `/` focus search (`SearchBar.tsx`).
- **D3. Undo for archive/status changes** via toast action button (safer than confirm dialogs for reversible actions — reserve `ConfirmDialog` for the few truly destructive ones).
- **D4. Bulk actions** on tables: extend `BulkArchiveAction` to bulk status/priority change.
- **D5. Saved filters** ("My overdue", "This week") as chips above tables.

### Phase E — Validate (1 week)

Re-run A2 (click counts), A3 (first-use test with new participants), A4 (accessibility audit), A5 (SUS). Compare against Phase A baselines and the targets table. Anything still below target feeds a HUCI_V2 backlog.

---

## 5. Heuristic Audit Template (for Phase A1)

Log every finding in this format (keep in `docs/hci-audit.md`):

| ID | Page | Heuristic | Finding | Severity (1–4) | Proposed fix | Phase |
|---|---|---|---|---|---|---|
| H-01 | Dashboard | Match with real world | Empty account shows zeroed stat cards with no guidance | 4 | B1 | B |
| H-02 | Tasks Board | Flexibility & efficiency | No keyboard alternative to drag-and-drop | 3 | C5 | C |
| … | | | | | | |

Nielsen checklist to apply per page: (1) visibility of system status, (2) match with the real world, (3) user control & freedom (undo!), (4) consistency, (5) error prevention, (6) recognition over recall, (7) flexibility & efficiency, (8) minimalist design, (9) error recovery, (10) help & documentation.

---

## 6. Accessibility Requirements (WCAG 2.2 AA checklist)

- [ ] All interactive elements reachable and operable by keyboard; visible focus ring in both themes
- [ ] Drag-and-drop has a single-pointer / keyboard alternative (WCAG 2.5.7)
- [ ] Contrast ≥ 4.5:1 for text, ≥ 3:1 for UI components — audit both themes, especially badge tints (`TintedChip.tsx`)
- [ ] Status never conveyed by color alone (B4)
- [ ] All form inputs have programmatic labels; errors linked via `aria-describedby`
- [ ] Modals trap focus, restore focus on close, close on Esc (verify `Modal.tsx`)
- [ ] Live regions for toasts and kanban moves
- [ ] Charts (`recharts` dashboards) have text alternatives (summary table or aria-label with figures)
- [ ] Touch targets ≥ 24×24 px (WCAG 2.5.8)
- [ ] `prefers-reduced-motion` respected for any transitions

---

## 7. Prioritized Roadmap Summary

| Priority | Item | Problem addressed | Effort |
|---|---|---|---|
| 1 | B1 Guided empty dashboard | P1 | S |
| 2 | C3 Dream wizard with coaching questions | P1, P4 | M |
| 3 | B3 Quick-add everywhere | P3 | S |
| 4 | C1 Vision Map as primary editor | P2 | L |
| 5 | B5 Breadcrumbs | P2 | S |
| 6 | C4 Next-action nudges | P4 | M |
| 7 | B4 + C5 Accessible states & kanban | P5 | M |
| 8 | C2 Sidebar grouping | P2 | S |
| 9 | C6 Mobile layouts | P6 | L |
| 10 | D1–D5 Efficiency layer | P7 | M |

Effort: S ≤ 1 day, M ≤ 1 week, L > 1 week.

---

## 8. Risks and Constraints

- **Don't break returning users:** C2 (nav regrouping) and C1 (map-first) change habits; keep old routes working and announce changes in-app once.
- **Business rules stay server-enforced:** quick-add (B3) must not bypass required-field rules — tasks created by quick-add stay in a "draft-complete" state until owner/due date are set, or quick-add prompts for the two required fields inline.
- **Scope discipline:** Phases B and C are the V2.1 release; Phase D only if time allows. Each phase ends with a build check and user confirmation, per project working rules.
- **Testing:** every changed common component keeps/extends its existing tests (`Modal.test.tsx`, `StatusBadge.test.tsx`, etc.); add tests for keyboard interaction in C5.

---

## 9. Definition of Done for HUCI V1

1. Phase A findings documented in `docs/hci-audit.md` with baseline metrics.
2. All Phase B items shipped and verified.
3. At least C1–C4 shipped; C5–C6 shipped or explicitly deferred with reason.
4. Phase E re-measurement shows: SUS ≥ 75, first-dream time ≤ 10 min, Lighthouse accessibility ≥ 95 on top 6 pages, keyboard-only task management possible.
5. `docs/USER_GUIDE.md` updated for the new flows.
