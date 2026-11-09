# BRD – Vision Map Timeline

Nov 9, 2026

## Document control

This is draft 16.0.0. It keeps every requirement in BRD 15.0.0 (AI Assistant and Helpers), BRD 14.0.0 (Sign in with Google, paused), BRD 13.0.0 (Effective Java), BRD 12.0.0 (Test Coverage), BRD 11.0.0 (AI Plan Builder), BRD 10.0.0 (Security Hardening), BRD 9.0.0 (Dream Conversion Program) and earlier versions. Where this BRD is silent, the earlier BRDs still apply.

V16 adds a **timeline view** to the Vision Map. Today the map shows a dream as a tree (goals → steps → tasks) and every dream as a card. Neither shows *when* things happen. V16 adds a **Tree | Timeline** switch for one dream, a **Cards | Timeline** switch for all dreams, and a **View full timeline** button that opens a full-screen timeline of every dream. The timeline reads the dates the plan already holds. It adds no new required fields and does not change the scheduling rules from BRD 5.0.0 (FR-51, BR-40) or the step date rule (FR-64).

| Item | Value |
| --- | --- |
| Document | Business Requirements Document (BRD) – Vision Map timeline addendum |
| Version | 16.0.0 – Draft |
| Parent documents | BRD 15.0.0 (9 Nov 2026), BRD 5.0.0 (FR-51 target-date cascade, BR-40) |
| Business owner | To be confirmed |
| Product owner | To be confirmed |
| Approvers | Sponsor, Product owner, Lead coach |

| Version | Change | Author |
| --- | --- | --- |
| 16.0.0 | First draft: current state at commit `47a79a7`, entry points (TL-FR-01 to TL-FR-06), what is drawn (TL-FR-07 to TL-FR-16), status and health (TL-FR-17 to TL-FR-22), interaction (TL-FR-23 to TL-FR-31), full timeline page (TL-FR-32 to TL-FR-38), rescheduling (TL-FR-39 to TL-FR-43), phone layout (TL-FR-44 to TL-FR-46), sharing and export (TL-FR-47 to TL-FR-50), non-functional requirements, phased plan and acceptance criteria | BA |

## Executive summary

The method in `CLAUDE.md` runs Vision Area → Dream → Goal → Step → Task → Partner/Resource → **Schedule** → Progress → Review. Every level already carries dates: a dream has a target date and a commitment start date, goals and steps have target dates, and tasks have a start date, a due date and a completion time. Milestones, reviews and partner follow-ups have dates too.

Yet no screen puts these dates on one line. A participant who wants to know "what happens this month?", "does this goal still fit before the dream's target date?" or "which of my dreams are crowding the same quarter?" has to open goals, steps and tasks one at a time, or read dates off the Goals, Steps and Tasks tables.

V16 adds a timeline to the Vision Map in three places:

1. **One dream (Tree | Timeline).** On a dream's map, a switch shows the same goals, steps and tasks as bars on a calendar, in the same order as the tree.
2. **All dreams (Cards | Timeline).** On the Vision Map overview, a switch shows each dream as one bar, grouped by vision area, so overlaps between dreams are easy to see.
3. **Full timeline.** A **View full timeline** button opens a full-screen page with every dream, expandable to goals, steps and tasks, with zoom from weeks to years, a "today" line, milestones, reviews and follow-ups.

The timeline is a way to **see** the plan. Changing dates by dragging bars is an optional later phase (TL-FR-39 to TL-FR-43) and must obey BR-40 exactly as the forms do today.

## Business objectives

| ID | Objective | Measure |
| --- | --- | --- |
| TLBO-01 | Participants see when their plan happens, not only what it contains | 40% of active participants open a timeline view at least once in their first 30 days after release |
| TLBO-02 | Scheduling problems are found earlier | Share of goals that end up past the dream's target date (BR-40 overrun under TOP_DOWN_FIXED) falls by 25% within 90 days |
| TLBO-03 | Fewer overdue tasks go unnoticed | Median days a task stays overdue before its date or status changes falls by 20% within 90 days |
| TLBO-04 | Coaches and participants can talk about the plan from one picture | The lead coach confirms the full timeline is usable in coaching sessions (qualitative sign-off) |
| TLBO-05 | No loss of trust in dates | Zero reports of the timeline showing a date different from the record's form |

## Scope

### In scope

- A **Tree | Timeline** switch on the single-dream Vision Map (`/dreams/:dreamId`).
- A **Cards | Timeline** switch on the Vision Map overview (`/vision-map`).
- A **View full timeline** button on both, opening a new full-screen page (`/vision-map/timeline`).
- Bars for dreams, goals, steps and tasks; markers for today, milestones, reviews, partner follow-ups and the dream commitment start.
- Status, progress, overdue, blocked and schedule-overrun signals on the bars.
- Zoom (week, month, quarter, year), scroll to today, expand and collapse, and the map's existing filters.
- Opening an item's existing edit form from its bar.
- A phone layout and an accessible table alternative.
- Print-friendly output of the full timeline.
- **Later phase:** changing a task's or step's dates by dragging, validated by the server.

### Out of scope

- Dependencies between tasks ("B starts when A ends"), critical path or automatic rescheduling. The data model has no dependency link and this BRD does not add one.
- Resource levelling, workload by hours, or capacity planning.
- Two-way sync with Google, Outlook or other calendars.
- New required date fields. Records without a date are listed, never blocked (see TL-FR-14).
- Changing BR-40, FR-51 or FR-64.
- A coach view of a participant's timeline (open question Q-04).

## Current state (9 Nov 2026, commit `47a79a7`)

| Area | What exists today | Gap |
| --- | --- | --- |
| Vision Map overview (`/vision-map`) | One card per dream: area, title, status, moonshot icon, target date, progress bar, counts of goals, steps and tasks (`DreamDetailPage`, overview mode). Filter by vision area, show archived | Dates appear as text on each card. No way to see dreams side by side in time or spot overlaps |
| Single-dream map (`/dreams/:dreamId`) | Tree of goals → steps → tasks (`VisionMapTree`) with progress bars, status dropdowns, quick add, drag to reorder, phone layout (commit `271c4b2`). Filters: dream, priority, status, archived. Support cards: commitment, plan share, Build my plan, AI plan assistant | Each row shows its own date. Nothing shows the plan's shape over time: how long each goal takes, whether steps overlap, or what falls in the next few weeks |
| Dates in the data model | Dream: `targetDate`, `commitmentStartDate`, `scheduleMode`. Goal: `targetDate`, `scheduleMode`. Step: `targetDate`, `sequenceNumber`. Task: `startDate` (optional), `dueDate` (required), `completedAt`. Milestone: `achievedOn`. Review: `reviewDate`. Communication message: `followUpDate` | Only tasks have a start date. Dreams, goals and steps have only an end. The timeline must work out a start (TL-FR-08) |
| Schedule rules | FR-51 and BR-40: unless `scheduleMode = TOP_DOWN_FIXED`, a parent's target date must not be earlier than its latest child's date; under `TOP_DOWN_FIXED` an overrun is shown as a banner (`scheduleOverrun`, `scheduleOverrunDetail` on dream and goal responses). FR-64: new steps need a target date; `/steps/undated` dates older ones | Overrun is shown as text on one record. It is never drawn, so it is hard to see *by how much* a goal runs past its dream |
| Other date views | Today page (`/today`), Tasks board (`/tasks`) with due dates, Goals and Steps tables with target-date filters, Reviews page | Each is a list or board for one level. None shows several levels on a calendar |
| Charting | `recharts` is installed and used on Insights. No Gantt or timeline component | A timeline component must be built or chosen (see Data and API changes) |

## Target experience

**One dream.** A participant opens a dream on the Vision Map. Above the tree, a two-option switch reads **Tree | Timeline**. Choosing **Timeline** replaces the tree with a calendar: months across the top, a vertical "today" line, and one row per goal, step and task in the same order and nesting as the tree. Each bar is filled to its progress and coloured by status. The dream's own bar sits on top, running from its start to its target date, with a flag at the commitment start and diamonds for milestones. A **View full timeline** button sits next to the switch.

**All dreams.** On the Vision Map overview, a **Cards | Timeline** switch shows every dream as one bar, grouped under its vision area. Two dreams that crowd the same quarter are visible at a glance.

**Full timeline.** **View full timeline** opens a full-screen page. It starts with every dream collapsed to one bar, grouped by vision area. Any dream can be expanded to goals, steps and tasks. A toolbar offers zoom (Week, Month, Quarter, Year), **Today**, **Expand all / Collapse all**, filters (vision area, dream, status, priority, show archived, show completed), marker toggles (milestones, reviews, follow-ups) and **Print**. The chosen view, zoom and filters are kept in the URL, so the link can be bookmarked and the browser's Back button works.

**Clicking a bar** opens the same edit form the tree uses, so nothing new has to be learned and dates are changed in one place. **Hovering or focusing** a bar shows its code, title, dates (marking any worked-out date), status, progress and owner.

**On a phone**, the timeline becomes a vertical agenda grouped by month (TL-FR-44).

## Functional requirements

Priority uses MoSCoW: **M** must, **S** should, **C** could.

### A. Entry points

| ID | Requirement | Links | Priority |
| --- | --- | --- | --- |
| TL-FR-01 | **Tree \| Timeline switch.** The single-dream Vision Map shall show a two-option switch above the tree. **Timeline** replaces the tree with the timeline for that dream. **Tree** brings the tree back. The support cards (commitment, plan share, Build my plan, AI plan assistant) stay above both views. | TLBO-01 | M |
| TL-FR-02 | **Cards \| Timeline switch.** The Vision Map overview shall show a two-option switch. **Timeline** shows every dream that passes the filters as one bar, grouped by vision area (TL-FR-33). | TLBO-01 | M |
| TL-FR-03 | **View full timeline button.** Both the overview and the single-dream map shall show a **View full timeline** button next to the switch. It opens `/vision-map/timeline`. From a single dream, that dream starts expanded and scrolled into view. | TLBO-01, TLBO-04 | M |
| TL-FR-04 | **Remember the choice.** The chosen view (tree or timeline, cards or timeline) shall be kept in the URL (`?view=timeline`) and remembered per browser, so returning to the map opens the view last used. Browser storage is a convenience only; the page must work without it. | | S |
| TL-FR-05 | **Navigation entry.** The full timeline shall be reachable from the sidebar under Plan → Vision Map, as a second item **Timeline**, so it can be opened without going through a dream. | | S |
| TL-FR-06 | **Empty states.** With no dreams, the timeline shows the map's existing "No dreams on the map yet" state and its **Create your first dream** action. With dreams but no dated items, it shows the dream bars and a short line pointing to the undated list (TL-FR-14). | | M |

### B. What is drawn

| ID | Requirement | Links | Priority |
| --- | --- | --- | --- |
| TL-FR-07 | **Rows follow the tree.** Rows shall appear in the tree's order and nesting: dream, then goals by their sort order, steps by sequence number, tasks by their order within the step. Indentation and an expand control show the level. | | M |
| TL-FR-08 | **Start dates.** Only tasks store a start date, so the timeline shall work out the others, in this order:<br>• **Task:** `startDate`; if empty, a one-day bar on `dueDate`.<br>• **Step:** earliest start of its tasks; else the day after the previous step's target date (by sequence number); else its goal's start.<br>• **Goal:** earliest start of its steps; else its dream's start.<br>• **Dream:** `commitmentStartDate`; else the earliest start of its goals; else the day it was created. | TLBO-05 | M |
| TL-FR-09 | **End dates.** A bar ends on the record's own date: `targetDate` for dreams, goals and steps, `dueDate` for tasks. A step with no target date ends on its latest task due date (see also TL-FR-14). | TLBO-05 | M |
| TL-FR-10 | **Worked-out dates are marked.** A start or end that was worked out (TL-FR-08, TL-FR-09) rather than stored shall be drawn with a faded edge, and the tooltip shall say so in plain words, for example "Start worked out from its first task". The timeline never presents a worked-out date as one the participant entered. | TLBO-05 | M |
| TL-FR-11 | **Progress inside the bar.** Each bar shall be filled to its progress, using the existing rollup (business rule 10: task direct, step = average of tasks, goal = average of steps, dream = average of goals, manual override clearly marked as today). | | M |
| TL-FR-12 | **Today line.** A vertical line marks today in the participant's time zone, labelled **Today**. | | M |
| TL-FR-13 | **Markers.** The timeline shall show, on the dream's row:<br>• a flag at the **commitment start** (`commitmentStartDate`);<br>• a diamond for each **milestone** (`achievedOn`);<br>• a small mark for each **review** linked to the dream (`reviewDate`);<br>• a small mark for each **partner follow-up** on a message linked to the dream, its goals or tasks (`followUpDate`).<br>Each marker type can be hidden from the toolbar. | TLBO-04 | S |
| TL-FR-14 | **Undated items.** Records with no usable date (older steps without a target date, FR-64) shall not be guessed onto the calendar. They are listed under the timeline in an **Undated** group with a link to `/steps/undated`. | TLBO-05 | M |
| TL-FR-15 | **Completed tasks.** A completed task ends at its due date, with a check mark at its `completedAt` date when that differs from the due date, so finishing early or late is visible. | TLBO-03 | S |
| TL-FR-16 | **Archived records.** Archived records are hidden unless **Show archived** is on, as on the tree. When shown, they are drawn faded and labelled "(archived)". | | M |

### C. Status and health

| ID | Requirement | Links | Priority |
| --- | --- | --- | --- |
| TL-FR-17 | **Status colour.** Bars use the same status colours as the tree's status dropdowns (commit `aeaebc0`) and the same progress colours (commit `a9889e5`), so the two views read the same. | | M |
| TL-FR-18 | **Overdue.** A task, step or goal whose end date is before today and whose status is not Completed (business rule 9) shall show a red end cap and an "Overdue by N days" label in its tooltip. | TLBO-03 | M |
| TL-FR-19 | **Blocked.** A blocked task shall show the blocked colour and an icon. Its tooltip shows the blocker reason (business rule 6). | | M |
| TL-FR-20 | **Schedule overrun.** When a child ends after its parent (allowed only under `TOP_DOWN_FIXED`, BR-40), the part of the child's bar past the parent's end shall be hatched, and the parent's row shall show the existing `scheduleOverrunDetail` text. | TLBO-02 | M |
| TL-FR-21 | **Never colour alone.** Every signal (status, overdue, blocked, overrun, worked-out date) shall also have a text label, icon or pattern, so it can be read without colour. | | M |
| TL-FR-22 | **Legend.** A collapsible legend explains colours, patterns and markers in plain English (and Khmer, TL-NFR-05). | | S |

### D. Interaction

| ID | Requirement | Links | Priority |
| --- | --- | --- | --- |
| TL-FR-23 | **Zoom.** Week, Month (default), Quarter and Year. Changing zoom keeps the same date centred. | | M |
| TL-FR-24 | **Today button.** Scrolls the calendar so today is in view. On first open, the timeline scrolls to today if today is within the plan's range; otherwise to the plan's start. | | M |
| TL-FR-25 | **Expand and collapse.** Each dream, goal and step row can be expanded or collapsed. **Expand all** and **Collapse all** act on the visible rows. The single-dream view starts with goals and steps expanded and tasks collapsed. | | M |
| TL-FR-26 | **Filters.** The timeline uses the map's existing filters (vision area, dream, priority, status, show archived). A **Show completed** toggle (default on) hides completed items to focus on remaining work. Filters keep a parent visible when any child matches, as the tree does today. | | M |
| TL-FR-27 | **Open the record.** Clicking a bar or its row title opens the record's existing edit form (the same dialog the tree uses). Saving refreshes the timeline. | TLBO-05 | M |
| TL-FR-28 | **Tooltip.** Hover and keyboard focus show code, title, start and end (with worked-out dates marked), status, progress, owner (tasks), priority and, where relevant, overdue days, blocker reason or overrun detail. | | M |
| TL-FR-29 | **Keyboard.** Arrow keys move between rows, Enter opens the record, Left and Right collapse and expand, and Page Up and Page Down scroll the calendar by one zoom unit. | | M |
| TL-FR-30 | **Sticky labels.** Row titles stay visible on the left and the date header stays visible at the top while scrolling. | | S |
| TL-FR-31 | **Quick add from the timeline.** A row's menu offers the same quick add as the tree (goal under a dream, step under a goal, task under a step), opening the existing form with no date pre-filled. | | C |

### E. Full timeline page

| ID | Requirement | Links | Priority |
| --- | --- | --- | --- |
| TL-FR-32 | **Route and layout.** `/vision-map/timeline` shows the timeline across the full width of the content area, with a toolbar (TL-FR-23 to TL-FR-26, TL-FR-13 marker toggles, TL-FR-38 print) and a link back to the Vision Map. | TLBO-04 | M |
| TL-FR-33 | **Grouped by vision area.** Dreams are grouped under their vision area, areas ordered by priority, dreams by letter rank and then by their usual order, as on the Strengths and Gaps page. | | M |
| TL-FR-34 | **Starting state.** Every dream starts collapsed to one bar. Opening from a single dream (TL-FR-03) expands that dream and scrolls it into view. | | M |
| TL-FR-35 | **Overlap hint.** In Quarter and Year zoom, a thin band above the calendar shows how many dreams, goals or tasks are active in each period, so crowded months stand out. It is informational only and never blocks anything. | TLBO-02 | S |
| TL-FR-36 | **Links in the URL.** Zoom, scroll position (as a centre date), expanded rows, filters and marker toggles are kept in the URL, so a link reopens the same view. | TLBO-04 | S |
| TL-FR-37 | **Large plans.** With more than 300 visible rows, the page shall render only the rows on screen (virtualised) and stay responsive (TL-NFR-01). | | M |
| TL-FR-38 | **Print.** **Print** produces a landscape, black-and-white-readable page of the visible range and rows, with the legend and the date of printing. | TLBO-04 | S |

### F. Rescheduling (later phase)

| ID | Requirement | Links | Priority |
| --- | --- | --- | --- |
| TL-FR-39 | **Drag to change dates.** In the single-dream and full timelines, a task bar can be moved (start and due date together) or its ends dragged; a step bar's end can be dragged to change its target date. Dreams and goals are changed only through their forms. | | C |
| TL-FR-40 | **Server checks every change.** A drag saves through the existing update endpoint, so BR-40, FR-64 and every other rule apply unchanged. A rejected change snaps back and shows the server's message, for example the BR-40 text. | TLBO-05 | C |
| TL-FR-41 | **Undo.** After a successful drag, an **Undo** toast is shown for 10 seconds and restores the previous dates. | | C |
| TL-FR-42 | **No accidental drags.** Dragging starts only after the pointer moves more than a few pixels and is turned off by default; a toolbar toggle **Edit dates** turns it on. On touch screens dragging is not offered; dates are changed in the form. | | C |
| TL-FR-43 | **Keyboard alternative.** With **Edit dates** on, a focused task bar can be moved a day at a time with Shift + arrow keys, saved with Enter and cancelled with Escape. | | C |

### G. Phone layout

| ID | Requirement | Links | Priority |
| --- | --- | --- | --- |
| TL-FR-44 | **Agenda view.** Below the phone breakpoint the Vision Map already uses (600 px, `isPhone` in `DreamDetailPage`), the timeline is a vertical agenda grouped by month: each month lists the items that start, end or are due in it, with the same status and overdue signals. | | M |
| TL-FR-45 | **Same entry points.** The switches and the **View full timeline** button appear on phones too, and the full timeline page uses the agenda view. | | M |
| TL-FR-46 | **No horizontal page scroll.** The phone layout never scrolls sideways. | | M |

### H. Sharing and export

| ID | Requirement | Links | Priority |
| --- | --- | --- | --- |
| TL-FR-47 | **Accessible table.** A **Show as table** control lists the same rows with level, code, title, start, end (worked-out dates marked), status, progress and owner, for screen readers and for copying. | | M |
| TL-FR-48 | **Shared plan page.** The read-only shared plan page (`/shared/:token`) may offer the timeline of the shared dream, with no edit form and no partner follow-ups. Depends on Q-03. | | C |
| TL-FR-49 | **Excel export.** The Excel export may add a **Timeline** sheet with one row per item and a simple bar made with conditional formatting across week columns. Depends on Q-05. | | C |
| TL-FR-50 | **Picture export.** The full timeline may be saved as a PNG of the visible range. | | C |

## Non-functional requirements

| ID | Requirement |
| --- | --- |
| TL-NFR-01 | **Performance.** For a participant with 10 dreams, 60 goals, 300 steps and 1,500 tasks, the full timeline shows its first view within 1.5 seconds after data arrives on a mid-range laptop, and scrolling and zooming stay smooth (no frame longer than 100 ms). The single-dream timeline appears within 500 ms of choosing **Timeline**, using data the map has already loaded. |
| TL-NFR-02 | **Correct dates.** Dates are calendar dates (`LocalDate`) and are never shifted by time zone. A bar's stored dates always match the record's form exactly (TLBO-05). Only `completedAt` and `reviewDate` are converted to the participant's local date. |
| TL-NFR-03 | **Data scoping.** Every timeline request returns only the signed-in participant's own records (business rule 13). No new cross-user access is added. |
| TL-NFR-04 | **Accessibility.** WCAG 2.1 AA: keyboard operation (TL-FR-29), visible focus, text alternatives (TL-FR-47), colour never the only signal (TL-FR-21), contrast in light, dark and high-contrast appearance settings. |
| TL-NFR-05 | **Language.** All new text goes through `tr()` and has Khmer entries in `km.ts`, marked as first drafts for a Khmer speaker to review. Month and day names follow the selected language. |
| TL-NFR-06 | **Appearance.** The timeline follows the participant's appearance settings (theme, density, font size, reduce motion). With reduce motion on, zoom and scroll changes are not animated. |
| TL-NFR-07 | **No new dependency without review.** The stack in `CLAUDE.md` stays as is. A timeline library may be added only if it is maintained, MIT or similar licensed, works with React 19.2.7, and passes the dependency and image scans from BRD 10.0.0. Otherwise the timeline is built in-house with CSS grid and SVG (recommended, see below). |
| TL-NFR-08 | **Tests.** Unit tests for the date rules (TL-FR-08 to TL-FR-10, TL-FR-18, TL-FR-20), component tests for the switches, tooltip, keyboard and table, and an API contract snapshot entry for any new endpoint. Coverage stays at or above the BRD 12.0.0 target. |

## Data and API changes (for solution design)

- **No schema change** for phases 1 to 4. The timeline reads existing fields only.
- **Single-dream timeline:** reuses the goals, steps and tasks the map already loads. Milestones, reviews and follow-ups for the dream are fetched once when the timeline is first opened.
- **Full timeline:** a new read-only endpoint is recommended, so the page does not issue one request per dream:

  `GET /api/timeline?from=YYYY-MM-DD&to=YYYY-MM-DD&visionAreaId=&dreamId=&includeArchived=false`

  It returns, for the signed-in user only, flat rows (`type`, `id`, `parentId`, `code`, `title`, `start`, `startWorkedOut`, `end`, `endWorkedOut`, `status`, `priority`, `progressPercent`, `owner`, `overdue`, `blocked`, `overrun`, `archived`, `sortKey`) plus markers (`type`, `date`, `dreamId`, `label`). The start and end rules (TL-FR-08, TL-FR-09) run on the server, in one place, and are unit tested. The response is cached per user like the dashboard (FR-30) and evicted on any write to the hierarchy.
- **Rescheduling (phase 5):** no new endpoint. Drags call the existing `PUT /api/tasks/{id}` and `PUT /api/steps/{id}`.
- **Component approach (recommended):** build the timeline in-house: a CSS grid for rows and a date scale, bars as absolutely positioned elements, markers as SVG. This keeps the bundle small, matches the app's MUI styling and theming, and avoids a new dependency. `recharts` is not suited to row-based timelines.
- **Excel (TL-FR-49, if approved):** a new sheet in the existing Apache POI export, after **Tasks**; the original workbook is never overwritten and a backup is made first, as today.

## Implementation phases

| Phase | Content | Requirements |
| --- | --- | --- |
| 1 | Date rules and single-dream timeline: start and end rules, Tree \| Timeline switch, bars, progress, today line, status, overdue, blocked, overrun, worked-out marks, open record, tooltip, zoom, Today, expand and collapse, filters, undated group | TL-FR-01, TL-FR-04, TL-FR-06 to TL-FR-12, TL-FR-14, TL-FR-16 to TL-FR-21, TL-FR-23 to TL-FR-28 |
| 2 | Full timeline and overview: `/vision-map/timeline`, `GET /api/timeline`, Cards \| Timeline switch, View full timeline button, grouping, virtualisation, URL state, sidebar entry | TL-FR-02, TL-FR-03, TL-FR-05, TL-FR-32 to TL-FR-34, TL-FR-36, TL-FR-37 |
| 3 | Markers and polish: milestones, reviews, follow-ups, commitment flag, completed check, legend, overlap band, sticky labels, print | TL-FR-13, TL-FR-15, TL-FR-22, TL-FR-30, TL-FR-35, TL-FR-38 |
| 4 | Phone, accessibility and language: agenda view, keyboard, table, Khmer, appearance, performance check | TL-FR-29, TL-FR-44 to TL-FR-47, TL-NFR-01, TL-NFR-04 to TL-NFR-06 |
| 5 (optional) | Rescheduling and exports: Edit dates toggle, drag, undo, keyboard moves, quick add, shared plan, Excel sheet, PNG | TL-FR-31, TL-FR-39 to TL-FR-43, TL-FR-48 to TL-FR-50 |

Each phase ends with backend and frontend builds and tests passing, and stops for confirmation (`CLAUDE.md` working rules 13 to 15).

## Acceptance criteria

| ID | Criterion |
| --- | --- |
| TLC-01 | On a dream's Vision Map, choosing **Timeline** shows the dream, its goals, steps and tasks as bars in the tree's order; choosing **Tree** brings the tree back unchanged. |
| TLC-02 | A task with start 3 Dec and due 10 Dec is drawn from 3 Dec to 10 Dec. A task with no start date and due 10 Dec is a one-day bar on 10 Dec. |
| TLC-03 | A step with no tasks, sequence 2, whose step 1 ends 5 Dec, starts on 6 Dec, and its tooltip says the start was worked out. |
| TLC-04 | A dream with a commitment start of 1 Nov starts on 1 Nov and shows a flag there; without one, it starts at its earliest goal start. |
| TLC-05 | A task due yesterday and not Completed shows a red end cap and "Overdue by 1 day". A Completed task due yesterday does not. |
| TLC-06 | A blocked task shows the blocked colour, an icon and its blocker reason in the tooltip. |
| TLC-07 | Under `TOP_DOWN_FIXED`, a goal ending 20 days after its dream's target date shows those 20 days hatched, and the dream row shows the overrun detail text. |
| TLC-08 | An older step with no target date and no tasks is not drawn and appears under **Undated** with a link to `/steps/undated`. |
| TLC-09 | Clicking a goal bar opens the same goal edit form as the tree. Changing its target date and saving moves the bar. |
| TLC-10 | Filtering by status Blocked shows blocked tasks and keeps their step, goal and dream rows visible. |
| TLC-11 | **View full timeline** from dream D-002 opens `/vision-map/timeline` with D-002 expanded and in view; other dreams are collapsed and grouped by vision area. |
| TLC-12 | Copying the full timeline URL and opening it in a new tab shows the same zoom, range, filters and expanded rows. |
| TLC-13 | With 1,500 tasks the full timeline meets TL-NFR-01. |
| TLC-14 | On a phone-width screen, the timeline is a month-by-month agenda with no sideways scroll. |
| TLC-15 | The whole timeline can be used with the keyboard alone, and **Show as table** lists every visible row with the same dates. |
| TLC-16 | A user cannot see another user's records through `GET /api/timeline`; the access-control sweep test (`AccessControlSweepTests`) covers the new endpoint. |
| TLC-17 | Every date shown on a bar or in the table equals the date on the record's form, or is marked as worked out. |
| TLC-18 | (Phase 5) Dragging a task's end past its step's target date under `BOTTOM_UP` is rejected by the server, the bar snaps back, and the BR-40 message is shown. |

## Assumptions, dependencies and risks

### Assumptions

- Participants mostly keep plans of up to a few years; the Year zoom covers longer dreams adequately.
- Due dates on tasks are reliable enough to draw (they are required today).
- The existing edit forms remain the single place to change dates in phases 1 to 4.

### Dependencies

- FR-51 and BR-40 (BRD 5.0.0) for overrun data; FR-64 for step dates.
- The status and progress colours from commits `aeaebc0` and `a9889e5`.
- The phone layout work in commit `271c4b2` (the 600 px `isPhone` breakpoint and map layout).
- The dashboard cache (FR-30) pattern for caching `GET /api/timeline`.

### Risks

| Risk | Impact | Mitigation |
| --- | --- | --- |
| Worked-out start dates are mistaken for real ones | Loss of trust in dates (TLBO-05) | Faded edge, tooltip wording, table marking (TL-FR-10); TLC-17 |
| Many old steps have no date, so the timeline looks empty | Poor first impression | Undated group and link to `/steps/undated` (TL-FR-14); empty-state wording (TL-FR-06) |
| Large plans make the page slow | Unusable full timeline | Server-side flattening, virtualised rows (TL-FR-37), performance criterion TLC-13 |
| Drag-to-reschedule changes dates by accident | Wrong plans | Phase 5 only, off by default, threshold, undo, server checks (TL-FR-39 to TL-FR-43) |
| A timeline library adds weight or licence risk | Bundle size, security scan failures | In-house build recommended (TL-NFR-07) |
| Timeline and tree drift apart in rules | Confusing differences | Same colours, same filters, same forms; date rules in one server function |

## Open questions

- [ ] **Q-01** Should the sidebar get a separate **Timeline** item (TL-FR-05), or is the button on the Vision Map enough? (Draft: Should, add it.)
- [ ] **Q-02** Should the single-dream timeline open with tasks collapsed (draft) or expanded?
- [ ] **Q-03** Should the shared plan page show the timeline (TL-FR-48)? It would reveal dates to anyone with the link. (Draft: Could, off by default, chosen per share.)
- [ ] **Q-04** Should coaches see a participant's timeline, read-only, within the V9 coach rule (D-6)? (Draft: out of scope for V16; revisit after phase 4.)
- [ ] **Q-05** Is an Excel **Timeline** sheet (TL-FR-49) wanted, or is Print enough? (Draft: Could, phase 5.)
- [ ] **Q-06** Should drag-to-reschedule (phase 5) be built at all, or should dates always be changed in forms? (Draft: build after phase 4 feedback.)
- [ ] **Q-07** Should weekends be shaded in Week zoom? (Draft: yes, light shading, no effect on dates.)

## Sign-off

| Role | Name | Decision | Date |
| --- | --- | --- | --- |
| Sponsor | | | |
| Product owner | | | |
| Lead coach | | | |

## Sources

- `CLAUDE.md`: method hierarchy, Vision Map page (Dream Detail / Vision Map), business rules 6, 9, 10 and 13, working rules.
- BRD 5.0.0: FR-51 bottom-up target-date cascade, BR-40.
- BRD 15.0.0: document format and current-state conventions.
- Code at commit `47a79a7`: `frontend/src/pages/DreamDetailPage.tsx`, `frontend/src/components/vision-map/VisionMapTree.tsx`, `frontend/src/pages/UndatedStepsPage.tsx`, entities `Dream`, `Goal`, `VisionStep`, `TaskItem`, `Milestone`, `Review`, `CommunicationMessage`, enum `ScheduleMode`.
