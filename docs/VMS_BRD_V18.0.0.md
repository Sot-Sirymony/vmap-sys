# BRD – Management Report

Nov 16, 2026

## Document control

This is draft 18.0.0. It keeps every requirement in BRD 17.0.0 (Blockers in the Right Place), BRD 16.0.0 (Vision Map Timeline), BRD 15.0.0 (AI Assistant and Helpers), BRD 14.0.0 (Sign in with Google, paused), BRD 13.0.0 (Effective Java), BRD 12.0.0 (Test Coverage), BRD 11.0.0 (AI Plan Builder), BRD 10.0.0 (Security Hardening), BRD 9.0.0 (Dream Conversion Program) and earlier versions. Where this BRD is silent, the earlier BRDs still apply.

V18 adds a **Management Report** under the Vision Map: one place that shows the health of the plan at a glance and turns it into documents a participant can hand to a manager, sponsor or mentor. It has two tabs. **Summary** shows charts of progress, priorities and blockers. **Export** produces an Excel report (one row per task, with its goal, step, blockers, progress, priority, due date and next action) and a ready-to-present PowerPoint deck for management review.

| Item | Value |
| --- | --- |
| Document | Business Requirements Document (BRD) – Management Report addendum |
| Version | 18.0.0 – Draft |
| Parent documents | BRD 17.0.0 (16 Nov 2026), BRD 16.0.0 (Vision Map Timeline), BRD 12.0.0 (coverage minimums), BRD 10.0.0 (security), `CLAUDE.md` (Dashboard, Excel export rules, business rules 9 to 14) |
| Business owner | To be confirmed |
| Product owner | To be confirmed |
| Approvers | Sponsor, Product owner, Lead coach |

| Version | Change | Author |
| --- | --- | --- |
| 18.0.0 | First draft: current state at commit `ac70e87`, the report page and filters (MR-FR-01 to MR-FR-06), Summary tab (MR-FR-07 to MR-FR-14), Excel export (MR-FR-15 to MR-FR-22), PowerPoint export (MR-FR-23 to MR-FR-31), API, phases and acceptance criteria | BA |
| 18.0.0 (phase 1 built) | Phase 1 implemented with the draft answers to Q-01 to Q-06. Clarified during build: health labels in the app are "Off track" (Red), "At risk" (Amber), "On track" (Green) and "Done". The "Open blockers" tile has no link, because the Obstacles page filters one status at a time and could not show exactly the same count (the overdue and blocked-task tiles link to the Tasks page, filtered by the same scope). A goal's next action is the first task not Completed in step and task order: its Next Action field, else its title. The existing roll-up (business rule 11) can mark a step and goal Completed when their only task is completed; such a goal shows as Done even if tasks are added later | Dev |
| 18.0.0 (phase 2 built) | Phase 2 implemented. Clarified during build: MR-NFR-05 assumed the existing Excel export was rate-limited; it was not (only import was), so a new per-account limit `REPORT_EXPORT` (30 per 10 minutes) covers both report downloads. MR-FR-19's "group heading rows" are not separate rows, because they would break sorting and filtering (MR-FR-17); instead the goal name is bold on the first row of each goal. The colours are conditional formats keyed on two hidden helper columns at the end of the Report sheet, so the eight requested columns stay exactly as listed. On a task row, Blockers / Management Issues includes open blockers on the task and on its step. "Days open" for a Blocked task counts from its last update. Fixed words in the files come from `ReportText.java` (English, Khmer drafts) and are listed in `km-review-v18.csv` | Dev |
| 18.0.0 (phase 3 built) | Phase 3 implemented. Clarified during build: the title slide uses the layout's centered title placeholder and the content slides the regular title placeholder, so every slide has a real title. Slide 5 lists open blockers on steps and tasks most severe first, then Blocked tasks with their reason; slide 8 lists open Decision, Money and Partner blockers, then steps whose Knowledge, Skill or Money gap has no help planned (the same rule as the plan check). Slide 7 gives each goal that is not done its first open task's Next Action (else the task title), owner and due date. Long titles are shortened with "…" instead of shrinking text. The deck was checked by drawing every slide with Apache POI's own renderer; a manual open in PowerPoint and LibreOffice (MRC-09) is still to do | Dev |
| 18.0.0 (phase 4 built) | Phase 4 implemented. Clarified during build: MR-FR-14's "overall progress per week" is the dashboard's "average task progress" trend (`DashboardTrend`), fed with the progress logs of the tasks in scope only. The headline's overall progress is the average of goal progress, so the two can differ; the chart is titled and summarised as average task progress to avoid confusion. Only the Summary carries the trend; the Excel report and deck do not. UAT steps for MRC-01 to MRC-15 are in `UAT_V18.md`. Every V18 screen string has a Khmer draft; strings reused from earlier, already reviewed versions are not listed again in `km-review-v18.csv` | Dev |

## Executive summary

Participants already track a lot: goals, steps, tasks, progress, priorities, due dates, next actions and, since V17, blockers placed on the step or task they hold up. But the information is spread over the Dashboard, the Vision Map, the Timeline, the Tasks board and the Obstacles page. When a participant has to report to someone else (a manager, a sponsor, a mentor, a funder), they copy figures by hand into a spreadsheet or slides.

V18 adds a **Management Report** page under the Vision Map:

- **Summary tab.** Charts that answer the questions a reviewer asks first: How far along are we? What is overdue? What matters most? What is stuck, and why? A goal health table rates each goal Green, Amber or Red by a fixed rule.
- **Export tab.** Two downloads built from the same data and the same filter:
  - **Excel (.xlsx):** one row per task under its goal and step, with the columns Goals, Steps, Tasks, Blockers / Management Issues, Progress (%), Priority, Due Date and Next Action, formatted to the existing Excel rules.
  - **PowerPoint (.pptx):** a short executive deck (about eight slides) summarising key goals, progress, blockers, next actions and the help needed.

The report can cover all dreams, one vision area or one dream. Everything is built on the server from the signed-in participant's own data, with the libraries the project already uses (Apache POI for both files, Recharts for the charts). No AI is used, so the report costs nothing to run and gives the same answer every time.

## Business objectives

| ID | Objective | Measure |
| --- | --- | --- |
| MRBO-01 | Reporting takes minutes, not hours | A participant can produce an Excel report and a deck for one dream in under 2 minutes from opening the page (usability test, 5 participants) |
| MRBO-02 | Reviewers see what needs a decision | Every deck shows blockers and next actions on their own slides; 4 of 5 reviewers in UAT can name the top blocker after one read |
| MRBO-03 | The report is trusted | Figures in the Summary tab, Excel and deck agree with each other and with the Dashboard for the same filter (automated test) |
| MRBO-04 | The report is used | 30% of active participants open the report, and 15% export at least once, within 60 days of release |
| MRBO-05 | Nothing private leaks | No journal content, private conflict notes or partner contact details appear in any export (automated test) |

## Scope

### In scope

- A Management Report page at `/vision-map/report`, with a sidebar entry under Timeline, and **Summary** and **Export** tabs.
- A filter: all dreams, one vision area, or one dream. Archived records are left out.
- Summary charts and tables built with Recharts.
- An Excel report and a PowerPoint deck generated on the server with Apache POI.
- A read-only report endpoint and two download endpoints, scoped to the signed-in participant.
- Khmer translations for all new text, and exports in the participant's language.

### Out of scope

- Editing anything from the report. It is read-only; links take the participant to the page where the item can be changed.
- Scheduled or emailed reports, and sharing a report by link (the existing plan share in V9 is unchanged).
- Coach or admin reports across participants. Coaches keep exactly the access V9 decision D-6 gives them.
- AI-written commentary in the deck. A later version could add it under the V15 AI rules.
- Native, editable PowerPoint charts (see MR-FR-27 and Q-03).
- PDF export (the existing plan PDF is unchanged).
- Importing either file back. The report workbook is not an import format; the existing Import / Export page is unchanged.

## Current state (16 Nov 2026, commit `ac70e87`)

| Area | What exists today | Gap |
| --- | --- | --- |
| Navigation | Vision Map (`/vision-map`) and Timeline (`/vision-map/timeline`) in the **Plan** group (`nav-items.ts`) | No report page |
| Dashboard | `GET /api/dashboard` returns counts, `tasksByStatus`, `tasksByPriority`, `activeObstaclesByType`, `visionAreaProgress`, attention items (`DashboardSummaryResponse`). Charts with Recharts (`PortfolioCharts`) | Portfolio-wide only, no per-dream or per-area filter, no goal-level health, nothing to hand to someone else |
| Progress | Task progress is direct; step, goal and dream progress roll up as averages (Core Business Rule 10, `ProgressCalculator`) | Reused as is |
| Blockers | V17: obstacles sit on a step or task; Blocked tasks need a reason (rule 6); unplaced obstacles are listed | Not gathered per row in any export |
| Excel export | `POST /api/excel/export` writes the full data workbook (Dashboard, Vision Areas, Dreams, Goals, Steps, Tasks, Partners, ... sheets) with formatting rules from `CLAUDE.md` (`ExcelService`) | A data dump in many sheets; not a single readable management view |
| PowerPoint | None. Apache POI (`poi-ooxml` 5.3.0) is already a dependency and includes the PowerPoint (XSLF) writer | — |
| PDF | `PlanPdfService` prints one dream's plan | Different purpose, unchanged |

## Definitions

These rules are used by the Summary tab, the Excel report and the deck, so all three agree (MRBO-03).

| Term | Rule |
| --- | --- |
| **In scope** | Unarchived goals, steps and tasks under the filter's dreams. Archived dreams, goals, steps and tasks are left out. |
| **Overdue** | Core Business Rule 9: a task whose due date is before today and is not Completed. A step or goal is overdue when its target date is before today and it is not Completed. |
| **Blocked item** | A task with status Blocked, or an obstacle with status Open or In Progress placed on a task or step in scope (V17). |
| **Management issue** | Anything a reviewer should know about a row: the task's blocker reason, open obstacles on the task or its step (title, kind, severity), "Overdue by N days", and "No owner" or "No due date" when missing. |
| **Next action** | The task's own Next Action field. When it is empty, the title of the first task in the same step that is not Completed (in the step's order), marked "(next task)". When the step has no open task, empty. |
| **Goal health** | **Red**: the goal is overdue, or has a Blocked task, or an open Critical obstacle in its steps or tasks. **Amber**: not Red, and it has an overdue task or step, or an open obstacle, or its progress is more than 20 points behind the share of time elapsed between its creation and its target date. **Green**: otherwise. **Done**: the goal is Completed. |
| **Due soon** | Due within the next 14 days and not Completed. |

## Functional requirements

Priority uses MoSCoW: **M** must, **S** should, **C** could.

### A. The report page and filter

| ID | Requirement | Priority |
| --- | --- | --- |
| MR-FR-01 | **Location.** A page **Management Report** at `/vision-map/report`, listed in the sidebar's Plan group right after Timeline, and linked from the Vision Map and Timeline page headers. | M |
| MR-FR-02 | **Tabs.** Two tabs, **Summary** (default) and **Export**. The chosen tab is kept in the URL (`?tab=export`) so it can be bookmarked. | M |
| MR-FR-03 | **Filter.** A scope picker: **All dreams** (default), **One vision area**, or **One dream**, with the area or dream chosen from the participant's own unarchived records. The filter is kept in the URL (`?areaId=` or `?dreamId=`) and applies to both tabs and both exports. | M |
| MR-FR-04 | **As-of line.** Every view and export states the scope and date: "All dreams · as of 16 Nov 2026". | M |
| MR-FR-05 | **Empty states.** With no goals in scope, the page explains what to add first and links to Dreams or the Vision Map. The Export tab's buttons are disabled with the reason shown. | M |
| MR-FR-06 | **Opened from a dream.** The Dream detail / Vision Map page offers **Management report** for that dream, opening the page with `?dreamId=` set. | S |

### B. Summary tab

| ID | Requirement | Priority |
| --- | --- | --- |
| MR-FR-07 | **Headline tiles.** Overall progress (average goal progress in scope), goals by health (Green, Amber, Red, Done), tasks overdue, tasks Blocked, open blockers, tasks due soon. Each tile with a count links to the filtered list behind it (Tasks board, Obstacles page). | M |
| MR-FR-08 | **Progress breakdown.** A chart of tasks by status (Not Started, In Progress, Waiting, Blocked, Completed, Paused) and a horizontal bar chart of progress (%) per goal, sorted by health then progress, with the goal's target date. | M |
| MR-FR-09 | **Priority distribution.** A chart of open tasks by priority (Low, Medium, High, Critical), split into on track and overdue. | M |
| MR-FR-10 | **Blocked items.** A chart of Blocked tasks and open obstacles by kind (Knowledge, Skill, Time, Money, Decision, Partner, Motivation, System, Other), and a count of obstacles **not on a step yet** (V17 BL-FR-05) with a link to them. | M |
| MR-FR-11 | **Goal health table.** One row per goal: dream, goal, health, progress, target date, overdue tasks, blocked items, next action. Sortable. Health shows a text label and an icon, never colour alone. | M |
| MR-FR-12 | **Needs attention.** Up to 10 items, in this order: Critical blockers, Blocked tasks, overdue High or Critical tasks, other overdue tasks, steps with a Knowledge, Skill or Money gap and no help planned (V17 BL-FR-22). Each links to the item. | S |
| MR-FR-13 | **Accessible charts.** Every chart has a text title, a short text summary ("12 of 40 tasks are completed") and a "Show as table" toggle with the same numbers. Colours follow the theme tokens and work in light and dark mode. | M |
| MR-FR-14 | **Trend.** A line of overall progress per week for the last 12 weeks, from the progress log. | C |

### C. Export to Excel

| ID | Requirement | Priority |
| --- | --- | --- |
| MR-FR-15 | **Download.** **Export to Excel** downloads `Management_Report_{scope}_{yyyy-MM-dd}.xlsx`. Nothing is written to the server's disk and no workbook is overwritten. | M |
| MR-FR-16 | **Report sheet.** A sheet **Report** with a title row, the as-of line (MR-FR-04), then a header row with exactly these columns, in this order: **Goals, Steps, Tasks, Blockers / Management Issues, Progress (%), Priority, Due Date, Next Action**. When the scope covers more than one dream, a **Dream** column is added first (Q-01). | M |
| MR-FR-17 | **Rows.** One row per task, grouped by dream, goal (by target date, then title) and step (by sequence number), tasks in their step order. A goal without steps, or a step without tasks, still gets one row with the empty cells left blank, so nothing in the plan is hidden. Goal and step names repeat on every row so the sheet can be filtered and sorted. | M |
| MR-FR-18 | **Cell rules.** Progress (%) is the task's progress (the step's or goal's on rows without a task), as a number with a % format. Priority is the task's (else the step's, else the goal's). Due Date is the task's due date (else the step's or goal's target date), as a real date cell. Blockers / Management Issues and Next Action follow the definitions above, one issue per line in the cell. | M |
| MR-FR-19 | **Formatting.** Frozen header row and auto-filter; auto-fit columns with wrapped text for Blockers and Next Action; conditional formatting: overdue due dates red, Completed rows green, rows with a Blocked task or open blocker orange, High and Critical priorities highlighted (the `CLAUDE.md` Excel rules). Group heading rows per goal are bold. | M |
| MR-FR-20 | **Summary sheet.** A first sheet **Summary** with the headline figures (MR-FR-07), tasks by status, open tasks by priority, and the goal health table (MR-FR-11), so the workbook reads well on its own. | M |
| MR-FR-21 | **Issues sheet.** A sheet **Blockers** listing every open obstacle and Blocked task in scope: where it sits (goal › step › task), kind, severity, status, days open, suggested helper type (`suggestPartnerFor`), solution so far. Unplaced obstacles are listed last as "Not on a step yet". | S |
| MR-FR-22 | **Print-ready.** Landscape, fit to one page wide, header rows repeated on every printed page, footer "Page N of M". | S |

### D. Export to PowerPoint

| ID | Requirement | Priority |
| --- | --- | --- |
| MR-FR-23 | **Download.** **Export to PowerPoint** downloads `Management_Report_{scope}_{yyyy-MM-dd}.pptx`, 16:9, opening cleanly in PowerPoint, Keynote, Google Slides and LibreOffice Impress. | M |
| MR-FR-24 | **Slide outline.** In this order: (1) **Title**: report title, scope, participant's name, date. (2) **Executive summary**: overall progress, goals by health, three headline sentences built by fixed rules ("3 goals on track, 1 at risk, 1 off track", "5 tasks overdue", "2 blockers need a decision"). (3) **Progress by goal**: one bar per goal with % and health, up to 8 goals. (4) **Priorities and deadlines**: open tasks by priority, overdue and due-soon counts, up to 6 items due soon. (5) **Blockers and management issues**: up to 6, most severe first, each with where it sits, why, and the helper type that could clear it. (6) **Overdue items**: up to 8, most overdue first. (7) **Next actions**: up to 8, one per goal where possible, with owner and date. (8) **Decisions and support needed**: blockers of kind Decision, Money or Partner, and steps whose gap needs a helper. | M |
| MR-FR-25 | **Overflow.** When a list does not fit, the slide shows the first items and "and N more (see the Excel report)". No text runs off a slide or shrinks below 12 pt. | M |
| MR-FR-26 | **Empty slides.** A slide with nothing to show says so positively ("No blockers right now") rather than being left out, so the deck always has the same shape. Slide 8 is left out when there is nothing to ask for. | S |
| MR-FR-27 | **Visuals.** Progress bars and charts are drawn as shapes and tables (not embedded images or native chart objects), so they stay sharp and editable as shapes. Health uses a label and an icon shape, not colour alone. | M |
| MR-FR-28 | **Look.** A clean, original design: one title font, body text at least 14 pt, the app's primary colour as an accent, plenty of white space, slide numbers in the footer. No logos or text from the source books (`CLAUDE.md`, Copyright and Source Use). | M |
| MR-FR-29 | **Speaker notes.** Each slide carries short notes with the figures behind it, so the presenter can answer questions. | S |
| MR-FR-30 | **Editable.** All text is real text in text boxes and tables, so the participant can edit the deck after downloading. | M |
| MR-FR-31 | **Optional title.** The Export tab lets the participant set the deck title (default "Progress report") and choose whether to include their name. | C |

## Non-functional requirements

| ID | Requirement |
| --- | --- |
| MR-NFR-01 | **Data scoping.** Every endpoint uses only the signed-in participant's records (business rule 13). An `areaId` or `dreamId` that is not theirs answers 404. The new endpoints are added to `AccessControlSweepTests`. |
| MR-NFR-02 | **Privacy.** Exports never include fear or attitude journals, gratitude entries, private conflict notes (`conflictPrivateNote`), criticism triage text, partner emails or phone numbers, AI conversations or coach comments (MRBO-05). Blockers show title, kind, severity and solution only. |
| MR-NFR-03 | **One source of truth.** The Summary tab, Excel and deck are built from one server-side report model, so their figures always agree (MRBO-03). Dashboard rules (overdue, progress roll-up) are reused, not re-implemented. |
| MR-NFR-04 | **Performance.** For a participant with 20 dreams, 100 goals, 500 steps and 2,000 tasks: the Summary loads in under 2 seconds, Excel generates in under 5 seconds and the deck in under 5 seconds on the production plan. Queries are batched (no query per row). |
| MR-NFR-05 | **Limits.** Exports are rate-limited like the existing Excel export, and a workbook over 10,000 rows stops with a clear message asking to narrow the filter. |
| MR-NFR-06 | **Language.** Page text goes through `tr()` with Khmer entries in `km.ts`, marked as first drafts (`km-review-v18.csv`). Exports use the participant's chosen language for labels, headings and fixed sentences; the participant's own text is unchanged. Khmer text in the files uses a font with Khmer glyphs (Q-04). |
| MR-NFR-07 | **Accessibility.** WCAG 2.1 AA on the page: keyboard tabs, chart text summaries and tables (MR-FR-13), labels with icons for health. The deck uses real titles on every slide so screen readers can navigate it. |
| MR-NFR-08 | **No new runtime dependency for files.** Excel and PowerPoint are built with Apache POI, already in `pom.xml`, so the Trivy and npm audit checks are unchanged. Any new library needs the engineering lead's approval, recorded here. |
| MR-NFR-09 | **Tests and coverage.** Unit tests for the definitions (overdue, health, next action, issues), the report model, the Excel columns and formats, and the deck's slide count, titles and overflow. Component tests for the tabs, filter, charts' table view and export buttons. API contract snapshot entries. CI's coverage minimums (backend lines 94%, branches 75%; frontend lines 85%, statements 84%, branches 78%, functions 78%) stay met; they are within half a point today, so each phase adds tests for its own code before it is pushed. |
| MR-NFR-10 | **No AI.** Nothing in V18 calls Claude or counts toward AI limits. |

## Data and API (for solution design)

No schema change is expected. All data already exists.

| Endpoint | Purpose |
| --- | --- |
| `GET /api/reports/management?areaId=&dreamId=` | The report model as JSON for the Summary tab: scope, as-of date, headline figures, tasks by status, open tasks by priority, blocked items by kind, unplaced count, goal health rows, attention items, and (MR-FR-14) the weekly trend. At most one of `areaId` and `dreamId`. |
| `GET /api/reports/management.xlsx?areaId=&dreamId=&language=` | The Excel report (MR-FR-15 to MR-FR-22). |
| `GET /api/reports/management.pptx?areaId=&dreamId=&language=&title=&includeName=` | The deck (MR-FR-23 to MR-FR-31). |

**Design notes.**

- A `ManagementReportService` builds one `ManagementReport` model per request with batched queries (goals, steps, tasks and placed obstacles for the scope in four queries). `ManagementReportExcelWriter` and `ManagementReportDeckWriter` only format that model.
- The rule-based helpers already in the code are reused: overdue detection, progress roll-up, the partner-type suggestion (moved to the backend in V17 as `GapStepService.supportFor`), and the V17 gap check (BL-FR-22).
- Downloads use `Content-Disposition: attachment` with a safe file name built from the scope, never from free text.

## Implementation phases

| Phase | Content | Requirements |
| --- | --- | --- |
| 1 | Report model and endpoint, definitions, page, filter, Summary tab (tiles, charts, goal health, needs attention), sidebar and links | MR-FR-01 to MR-FR-13, MR-NFR-01 to MR-NFR-04, MR-NFR-07 |
| 2 | Excel report: Summary, Report and Blockers sheets, formatting, print setup, Export tab | MR-FR-15 to MR-FR-22, MR-NFR-05 |
| 3 | PowerPoint deck: eight slides, overflow, visuals, notes, options | MR-FR-23 to MR-FR-31 |
| 4 | Trend chart, Khmer review file, docs, UAT fixes | MR-FR-14, MR-NFR-06 |

Each phase ends with the CI commands passing locally (`mvn verify`, `npm run build`, `npm run test:coverage`) and stops for confirmation (`CLAUDE.md` working rules 13 to 15).

## Acceptance criteria

| ID | Criterion |
| --- | --- |
| MRC-01 | The sidebar shows **Management Report** under Timeline; it opens `/vision-map/report` on the Summary tab with "All dreams · as of {today}". |
| MRC-02 | Choosing one dream updates every tile and chart and the URL (`?dreamId=`); the Excel and deck downloaded next cover that dream only. |
| MRC-03 | With 40 tasks of which 12 are Completed, the status chart's text summary reads "12 of 40 tasks are completed" and "Show as table" lists the same counts. |
| MRC-04 | A goal past its target date and not Completed shows **Red**; a goal with only an open Low obstacle shows **Amber**; a Completed goal shows **Done**. |
| MRC-05 | The Excel **Report** sheet's header row is exactly: Goals, Steps, Tasks, Blockers / Management Issues, Progress (%), Priority, Due Date, Next Action (with Dream first when more than one dream is in scope). |
| MRC-06 | A Blocked task with reason "Waiting for data" and an open Money obstacle "No budget" on its step shows both lines in its Blockers / Management Issues cell, and the row is orange. |
| MRC-07 | A step with no tasks appears as one row with the Tasks cell empty and the step's progress and target date. |
| MRC-08 | A task with an empty Next Action shows the next open task in its step, marked "(next task)". |
| MRC-09 | The deck has 8 slides in the order of MR-FR-24 (7 when nothing needs a decision), each with a title and slide number, and opens without a repair prompt in PowerPoint and LibreOffice. |
| MRC-10 | With 12 blockers, slide 5 shows 6 and "and 6 more (see the Excel report)". |
| MRC-11 | The Summary tab, the Excel Summary sheet and the deck's executive summary show the same overall progress, overdue count and blocked count for the same filter. |
| MRC-12 | An export for a dream whose obstacle has a private conflict note, and whose partner has an email address, contains neither. |
| MRC-13 | Another user's `dreamId` or `areaId` on any of the three endpoints answers 404 (`AccessControlSweepTests`). |
| MRC-14 | With Khmer chosen, the page, the Excel headings and the deck's headings are in Khmer and display correctly. |
| MRC-15 | CI passes on the release commit with every coverage minimum met. |

## Assumptions, dependencies and risks

### Assumptions

- Participants report to a person who reads Excel and PowerPoint; PDF is not needed in V18.
- The existing fields (progress, priority, due date, next action, blockers) are complete enough to report on; empty fields are shown as management issues rather than guessed.

### Dependencies

- V17 blocker placement and gap check (BL-FR-01 to BL-FR-22).
- Core Business Rules 9 and 10 (overdue, progress roll-up) and the `CLAUDE.md` Excel formatting rules.
- Apache POI 5.3.0 (`poi-ooxml`) and Recharts 3, both already in the project.

### Risks

| Risk | Impact | Mitigation |
| --- | --- | --- |
| Figures differ between tabs and files | Lost trust | One report model for all three (MR-NFR-03), tested together (MRC-11) |
| Decks look different in other slide apps | Poor first impression | Shapes and tables only, standard fonts, tested in PowerPoint and LibreOffice (MRC-09) |
| Khmer text shows as boxes in exported files | Unreadable report | Khmer-capable font set in both files (Q-04) and a manual check (MRC-14) |
| Large plans make slow or huge files | Timeouts | Batched queries, row limit, overflow rules (MR-NFR-04, MR-NFR-05, MR-FR-25) |
| Private notes reach a manager | Harm, loss of trust | Fixed allow-list of fields in exports, automated check (MR-NFR-02, MRC-12) |
| CI coverage drops below the minimums | Blocked deploys (as on `3fbf1e4`) | Tests per phase, CI commands run locally before every push (MR-NFR-09) |

## Open questions

- [x] **Q-01** In the Excel **Report** sheet, should a **Dream** column be added before Goals when the scope covers more than one dream? (Draft: yes, only then; the eight requested columns stay in their order.) **Answered: the draft, as built (closed by BRD 19.0.0, RM-FR-03).**
- [x] **Q-02** Is the Goal health rule (Red, Amber, Green, Done) right, in particular "more than 20 points behind the time elapsed" for Amber? (Draft: yes.) **Answered: the draft, as built; BRD 19.0.0 adds "Done – open work" (RM-FR-02).**
- [x] **Q-03** Are drawn shapes enough for the deck's charts, or are native, editable PowerPoint charts required? (Draft: drawn shapes; native charts could come later.) **Answered: the draft, as built; asked again as BRD 19.0.0 Q-05.**
- [x] **Q-04** Which Khmer font should the files name? (Draft: "Noto Sans Khmer", falling back to "Khmer OS Battambang"; the reader's computer must have one of them.) **Answered: the draft, as built (`ReportText.KHMER_FONT`).**
- [x] **Q-05** Should the participant's name be on the title slide by default? (Draft: yes, with the option to leave it out, MR-FR-31.) **Answered: the draft, as built.**
- [x] **Q-06** Should coaches be able to download a participant's report? (Draft: no in V18; it would need a new decision under V9 D-6.) **Answered: the draft, as built; BRD 19.0.0 proposes decision D-19-3.**

## Sign-off

| Role | Name | Decision | Date |
| --- | --- | --- | --- |
| Sponsor | | | |
| Product owner | | | |
| Lead coach | | | |

## Sources

- `CLAUDE.md`: Dashboard page, Excel export requirements, Core Business Rules 9, 10, 12, 13 and 14, Copyright and Source Use, working rules.
- BRD 17.0.0 (blocker placement, gap check), BRD 16.0.0 (document format, Vision Map timeline), BRD 12.0.0 (coverage minimums), BRD 10.0.0 (security, rate limits).
- Code at commit `ac70e87`: `components/layout/nav-items.ts`, `components/dashboard/charts/PortfolioCharts.tsx`, `dto/response/DashboardSummaryResponse.java`, `excel/ExcelService.java`, `service/support/ProgressCalculator.java`, `service/GapStepService.java`, `service/PlanPdfService.java`, `pom.xml` (Apache POI 5.3.0).
