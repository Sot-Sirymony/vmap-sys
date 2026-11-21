# BRD – Report Management

Nov 17, 2026

## Document control

This is draft 19.0.0. It keeps every requirement in BRD 18.0.0 (Management Report), BRD 17.0.0 (Blockers in the Right Place), BRD 16.0.0 (Vision Map Timeline), BRD 15.0.0 (AI Assistant and Helpers), BRD 14.0.0 (Sign in with Google, paused), BRD 13.0.0 (Effective Java), BRD 12.0.0 (Test Coverage), BRD 11.0.0 (AI Plan Builder), BRD 10.0.0 (Security Hardening), BRD 9.0.0 (Dream Conversion Program) and earlier versions. Where this BRD is silent, the earlier BRDs still apply.

V18 gave participants a **Management Report**: a live view of the plan's health and two downloads (Excel and PowerPoint). It answers "where are we today?". It does not answer the next questions a manager, sponsor or mentor asks: *"What changed since last time?"*, *"What do you want from me?"* and *"Can I see the report you sent last month?"*.

V19 turns the report into **managed reports**. A participant can pick a reporting period, write their own commentary, save a report, finalise it so its figures are frozen, compare it with the previous one, export it (Excel, PowerPoint and now PDF) and share a read-only link. It also closes the gaps found while building and testing V18.

| Item | Value |
| --- | --- |
| Document | Business Requirements Document (BRD) – Report Management addendum |
| Version | 19.0.0 – Draft |
| Parent documents | BRD 18.0.0 (16 Nov 2026), BRD 15.0.0 (AI rules), BRD 12.0.0 (coverage minimums), BRD 10.0.0 (security), BRD 9.0.0 (decision D-6, plan share), `CLAUDE.md` (Excel export rules, business rules 9 to 14) |
| Business owner | To be confirmed |
| Product owner | To be confirmed |
| Approvers | Sponsor, Product owner, Lead coach |

| Version | Change | Author |
| --- | --- | --- |
| 19.0.0 | First draft: current state at commit `d2c9206`, decisions D-19-1 to D-19-3, V18 carry-overs (RM-FR-01 to RM-FR-05), period and filters (RM-FR-06 to RM-FR-12), details view (RM-FR-13 to RM-FR-15), saved reports (RM-FR-16 to RM-FR-25), commentary (RM-FR-26 to RM-FR-31), compare and what changed (RM-FR-32 to RM-FR-37), exports (RM-FR-38 to RM-FR-45), sharing and reminders (RM-FR-46 to RM-FR-52), API, schema, phases and acceptance criteria | BA |
| 19.0.0 (phase 1 built) | Phase 1 implemented with the draft answer to Q-01 (default period: this month). Clarified during build: instead of a second `ReportData` model, the existing `ManagementReport` now holds plain value records (goal, step, task, blocker, dream) copied from the entities, with only the fields the report may show; the writers no longer see entities. **Tasks that became Blocked** (RM-FR-08) is not counted: nothing records when a task entered Blocked, and RM-FR-09 forbids guessing. **Progress gained** sums positive progress-log changes in the period and averages them over the tasks in scope; creating a task writes no log, so a task's starting progress does not count. **Blockers opened and closed** count blockers on steps or tasks in scope only. **Tasks that became overdue** are due inside the period, before today, and not Completed by the period's end (from `completedAt`). `ReportPeriod.RESOLVED_AT_RECORDED_FROM` (17 Nov 2026) marks periods that start before closing dates were recorded; set it to the release date. Health reasons list every rule at the level that decided the health (a Red goal does not also list its Amber rules). **Done – open work** (RM-FR-02) applies to goals only, because steps have no health in the report. On the deck's progress slide each goal row is taller to fit its reason line, so with more than 8 goals it shows 7 and the overflow line. The Excel Summary sheet already gets a "This period" table (part of RM-FR-40). RM-FR-04 is **not done**: the deck still needs a manual open in PowerPoint and LibreOffice (LibreOffice is not installed on the build machine). RM-FR-05: no entry in `km-review-v18.csv` has been reviewed yet, so there was nothing to copy | Dev |
| 19.0.0 (phase 2 built) | Phase 2 implemented with the draft answers to Q-02 (copy, never reopen), Q-03 (200 reports, 5 MB) and Q-04 (the snapshot holds the rows too). Clarified during build: each goal's health is judged on all its work **before** filters narrow its steps and tasks, so a filter never changes a goal's health; the period figures, like the headline, count only what the filters keep. A goal or step without tasks is kept by a priority filter only when its own priority matches, and never by an owner filter. The snapshot stores the report model (so the files can be rebuilt in either language) and the Summary as shown on the day, as gzip JSON in a base64 `TEXT` column (not `BYTEA`) so H2 tests and PostgreSQL behave the same; version 1 has a stored fixture test (RMC-21). A final report whose checksum does not match still opens, with a warning, and phase 4 will refuse to share it. **Downloads from a saved report (RM-FR-38) were brought forward** from phase 3: Excel and PowerPoint from a final report come from its snapshot, named `{Report name}_{date}`; the Commentary and Changes sheets and slides stay in phase 3. A final report answers 409 to a change of anything but its name, to delete and to a second Finalise. The 200-report limit counts archived reports, so the message asks to delete drafts. **New report from this** (RM-FR-21) gives a period still running as the matching "this week / month / quarter" preset, a period wholly past as fixed dates, and a custom period as many days again (ending today at the latest). Commentary saves 2 seconds after typing stops and when a field loses focus. The highlights draft uses tasks completed in the period and goals done; "goals that moved to On track" needs the comparison and comes in phase 3. Saved reports are deleted with the account; they are not yet in the account data export | Dev |
| 19.0.0 (phase 3 built) | Phase 3 implemented. Clarified during build: the comparison is worked out by a pure `ReportComparison` from two report models and Summaries, and stored **inside the Summary** (`comparison`), so Finalise freezes it in the snapshot with the figures and a version 1 snapshot simply has none (no new snapshot version needed). One query outside the comparison says why a goal left the scope (Completed and archived, Archived, Moved to another dream), else no reason is shown (for example a filter left it out). Only **open** blockers are in the model, so "blockers closed" means "no longer open" (closed, or moved out of scope). Health gets better in the order Off track, At risk, Done – open work, On track, Done. A date added or removed is not counted as moved later. At most 500 change lines are kept (all counted in `totalChanges`); the page shows 15 and the deck 7. A draft with no chosen comparison uses the **latest final report on its scope** at the time it is opened; a chosen one that no longer fits (the draft's scope changed) falls back to that. A final report's commentary is not copied into the snapshot: its columns cannot change after Finalise (409), which keeps it frozen too. **Section choice (RM-FR-39)** maps as follows: the Summary sheet's headline and period tables and the deck's title and executive summary are always in; COMMENTARY = the Commentary sheet, the Highlights slide, the participant's text on the support slide and goal notes in the progress slide's notes; SUMMARY = the health, status and priority tables and the priorities slide; GOAL_HEALTH = the goal table and the progress slide; DETAILS = the Report sheet and the overdue slide; BLOCKERS = the Blockers sheet and slide; CHANGES = the Changes sheet and the What changed slide (when compared); NEXT_ACTIONS and SUPPORT = their slides. Sections are saved with their own endpoint (`PUT …/sections`), allowed on a final report because they change how files are cut, not the frozen figures. The Commentary sheet comes first when there is commentary. Live files never carry the DRAFT mark | Dev |

## Executive summary

**The problem.** V18's report is always "as of today". Each export is a new, unnamed file, and nothing is kept in the app. So a participant who reports every month:

- cannot show progress *between* two reports, because there is nothing to compare with;
- writes their highlights, risks and requests by hand in the deck after every download, and loses them next time;
- cannot reopen what they sent last month, so if a reviewer asks about it, they rebuild it from memory;
- can only filter by area or dream, so a report on "my High and Critical work this quarter" is not possible;
- sees a goal marked **Off track** without being told why.

**The change.** V19 adds five things on top of V18:

1. **Reporting period and better filters.** This week, this month, this quarter or custom dates, plus filters by priority, owner and health. Figures for the period are shown next to the as-of figures: tasks completed, progress gained, blockers opened and closed.
2. **Saved reports.** A report has a name, scope, period, commentary and a status: **Draft → Final → Archived**. Finalising freezes the figures (a snapshot), so the report reads the same next year. A **Reports** list shows every saved report.
3. **Commentary.** Four short sections written by the participant: Highlights, Risks and issues, Decisions and support needed, Focus for next period, plus an optional note per goal. The app offers a rule-based first draft; an AI draft is optional under the V15 rules.
4. **Compare and "what changed".** A report is compared with the previous final report on the same scope: progress up or down, goals that changed health, blockers opened and closed, and due dates that moved.
5. **Better exports and sharing.** The commentary and the comparison appear in Excel, PowerPoint and a new PDF. The participant chooses which sections to include. A final report can be shared by a read-only link that expires, in the same way as the V9 plan share.

No AI is needed for any of this. The optional AI draft follows the V15 consent, daily limit and monthly cap.

## Business objectives

| ID | Objective | Measure |
| --- | --- | --- |
| RMBO-01 | Reporting a period takes minutes | A participant can produce a monthly report with commentary and a comparison in under 5 minutes, and a later month's report in under 3 minutes (usability test, 5 participants) |
| RMBO-02 | Reviewers see change, not just state | 4 of 5 reviewers in UAT can say, after one read, which goal improved most and which got worse since the last report |
| RMBO-03 | A sent report can be found and trusted later | A final report reopened 30 days later shows exactly the figures it had when finalised (automated test on the snapshot) |
| RMBO-04 | Requests to reviewers are explicit | 70% of final reports in the first 60 days have a "Decisions and support needed" section or say "Nothing needed" |
| RMBO-05 | Reports are reused | 20% of participants who export in V18 save at least two final reports within 90 days of release |
| RMBO-06 | Nothing private leaks | No journal content, private conflict notes, partner contact details, AI conversations or coach comments in any saved report, export or shared link (automated test) |

## Scope

### In scope

- V18 carry-overs: health reasons, the "Done with open tasks" case, the manual deck check, and the answers to V18's open questions.
- A reporting period and filters by priority, owner and goal health, applied to the page and every export.
- A **Details** tab: the Excel Report sheet's rows on screen, with search, sort and filter.
- Saved reports with Draft, Final and Archived status; a **Reports** list; a frozen snapshot when finalised.
- Participant commentary, with a rule-based draft and an optional AI draft.
- Comparison with a previous final report, and "what changed" in the period.
- Export updates: commentary and changes in Excel and PowerPoint, a new PDF export, and a choice of sections.
- A read-only share link for a final report, with expiry and revoke.
- An optional reminder to prepare a report on a weekly or monthly cadence, using the existing reminder job.
- Khmer translations for all new text.

### Out of scope

- Sending a report by email from the app to a reviewer's address (see Q-06). The participant downloads the file or copies the share link.
- Editing plan data from the report. It stays read-only; links lead to the page where the item is changed.
- Reports across participants for coaches or admins. Coach access to one participant's shared report is a **Could** that needs decision D-19-3.
- Native, editable PowerPoint charts (still drawn shapes, V18 Q-03), unless Q-05 is answered differently.
- Custom report templates and branding (logos, colours, fonts chosen by the participant).
- Importing a report file back into the app.

## Current state (17 Nov 2026, commit `d2c9206`)

| Area | What exists today | Gap |
| --- | --- | --- |
| Page | `/vision-map/report` with Summary and Export tabs; scope All, Area or Dream in the URL (`ManagementReportPage.tsx`) | No period, no filter below the dream, no row-level view on screen |
| Model | `ManagementReportService` builds `ManagementReport` (goals, steps, tasks and blockers as JPA entities) per request; `ReportRules` holds overdue, health and next action | The model holds live entities, so it cannot be saved and replayed later |
| Health | `GoalHealthRow.health` is GREEN, AMBER, RED or DONE | No reason given. A goal auto-completed by roll-up shows Done even after new open tasks are added (V18 phase 1 note) |
| Summary API | `GET /api/reports/management` returns headline, charts, goal health, attention items and a 12-week trend | Only "as of today" figures; nothing for a period |
| Excel | `ManagementReportExcelWriter`: Summary, Report and Blockers sheets | No commentary, no comparison |
| PowerPoint | `ManagementReportDeckWriter`: 8 slides (7 with nothing to decide) | No commentary slide, no "what changed" slide; title and name are the only options. MRC-09 manual open in PowerPoint and LibreOffice not yet done |
| PDF | OpenPDF is already a dependency (`PlanPdfService` prints one dream's plan) | No report PDF |
| History | Task `completedAt`, progress logs (before and after), task `blockedCount` | No saved report. Obstacles have no `resolvedAt`, so "blockers closed in the period" cannot be counted exactly |
| Sharing | V9 plan share: `PlanShareService` creates, lists and revokes token links to one dream's plan | No report share |
| Reminders | `ReminderJob` runs every 15 minutes; `ReminderMailer` sends mail | No report reminder |
| Limits | `REPORT_EXPORT` 30 per 10 minutes per account | Reused for every export in V19 |
| Translations | `ReportText.java` (file text), `km.ts` (screen text), `km-review-v18.csv` | Extended for V19 |

## Decisions recorded by this BRD

| ID | Decision | Effect on `CLAUDE.md` |
| --- | --- | --- |
| D-19-1 | A **final** report is a frozen record. Its figures, rows and commentary are stored as a snapshot when it is finalised and never recalculated. Later changes to the plan do not change it. To change a final report, the participant makes a new draft from it. | None |
| D-19-2 | A saved report stores only the fields on the V18 export allow-list (MR-NFR-02) plus the participant's commentary. It never stores journal content, private conflict notes, criticism text, partner contact details, AI conversations or coach comments. | None |
| D-19-3 | **(Needs sign-off before RM-FR-50 is built.)** A coach may read a participant's **final** report only when the participant shares it with that coach, within the active cohort rules of V9 D-6. The coach can never edit it or see drafts. | Core Business Rule 14: add "and final reports the participant shares with them (V19, D-19-3)" after "only when the participant shares them." |

`CLAUDE.md` is changed only after the product owner signs these decisions off.

## Target experience

**Monthly report, first time.** On 30 November, Dara opens the Management Report, picks **One dream: Become an AI-assisted public health researcher** and **Period: This month**. Next to "Overall progress 46%" the page now shows "+12 points this month" and "9 tasks completed, 2 blockers closed, 1 new". Dara clicks **Save as report**, names it "November progress", and the app fills a first draft of the commentary: *"Highlights: completed 'Search literature' (5 tasks)."* Dara edits it, adds "Need 1 hour with my mentor to review the gap statement" under **Decisions and support needed**, and clicks **Finalise**. The PowerPoint now opens with a Highlights slide in Dara's words.

**Monthly report, next time.** On 31 December, Dara opens **Reports**, chooses "November progress" and clicks **New report from this**. The commentary sections carry over as a starting point, the period moves to December, and the report is compared with November: "Prepare one concept note: At risk → On track", "Present to mentor: due date moved 10 Jan → 24 Jan". The deck has a "What changed" slide.

**A question three weeks later.** Dara's manager asks about a figure from November. Dara opens the final November report: the figures are the same as on the day it was finalised, with "Final · frozen 30 Nov 2026" at the top.

## Functional requirements

Priority uses MoSCoW: **M** must, **S** should, **C** could.

### A. V18 carry-overs

| ID | Requirement | Priority |
| --- | --- | --- |
| RM-FR-01 | **Health reasons.** Every goal health value comes with the rule or rules that set it, in plain words: "Target date passed 3 days ago", "1 Blocked task", "Critical blocker: No budget", "Progress 25% with 70% of the time gone". Shown as a tooltip and an expandable line in the goal health table, a **Why** column in the Excel goal health table, and a short line under each goal on the deck's progress slide. | M |
| RM-FR-02 | **Done with open work.** A Completed goal (or step) that has unarchived tasks that are not Completed shows health **Done – open work** with the reason "Completed, but N tasks are still open", and appears in Needs attention as "Reopen or finish". The roll-up in business rule 11 is not changed. | M |
| RM-FR-03 | **V18 answers recorded.** The V18 open questions Q-01 to Q-06 are closed in BRD 18.0.0 with the answers used in the build (the draft answers), and signed off with this BRD. | M |
| RM-FR-04 | **Deck opens cleanly.** MRC-09's manual check (no repair prompt in PowerPoint and LibreOffice Impress) is done and recorded in `UAT_V18.md` before V19 phase 4 starts; any fault found is fixed in phase 1. | M |
| RM-FR-05 | **Khmer review.** Reviewed entries in `km-review-v18.csv` are copied into `km.ts` and `ReportText.java` in phase 1. | S |

### B. Reporting period and filters

| ID | Requirement | Priority |
| --- | --- | --- |
| RM-FR-06 | **Period picker.** Next to the scope: **This week** (Monday to today), **Last week**, **This month**, **Last month**, **This quarter**, **Last quarter**, **Custom** (from and to dates, at most 366 days, the end not after today). Default: **This month**. Kept in the URL (`?period=THIS_MONTH` or `?from=&to=`). | M |
| RM-FR-07 | **As-of vs period.** The as-of figures (V18) stay as they are, measured at the end of the period (today when the period ends today). The period adds its own figures beside them. The as-of line becomes "{scope} · {period label} · as of {date}". | M |
| RM-FR-08 | **Period figures.** For the period: tasks completed (`completedAt` in the period), progress gained (sum of positive progress-log changes in scope, shown as points of average task progress), blockers opened (obstacles created in the period), blockers closed (moved to Resolved or Accepted in the period, from the new `resolvedAt`), tasks that became Blocked, and tasks that became overdue (due date inside the period and still not Completed at its end). | M |
| RM-FR-09 | **Past period honesty.** When the period ends before today, figures that cannot be rebuilt from history (for example a task's status on that day) are taken from today, and the page says so: "Status figures are as of today; period figures cover {from} to {to}." No figure is guessed. | M |
| RM-FR-10 | **More filters.** Optional filters: **Priority** (any of Low, Medium, High, Critical), **Owner** (from the owners used in scope), **Goal health** (any of the health values) and **Include completed goals** (on by default). Filters narrow goals, steps and tasks in the page and every export, and are listed in the as-of line ("High, Critical · Owner: Dara"). Kept in the URL. | S |
| RM-FR-11 | **Filter rules.** A goal is kept when it matches the health filter and has at least one task matching the priority and owner filters (or has no tasks and the priority filter includes the goal's priority). Headline figures are counted on what is kept, so the page and files still agree (MR-NFR-03). | S |
| RM-FR-12 | **Reset.** A **Reset filters** link returns to All dreams, This month, no other filters. | S |

### C. Details tab

| ID | Requirement | Priority |
| --- | --- | --- |
| RM-FR-13 | **Details tab.** A third tab **Details** (`?tab=details`) shows the Excel Report sheet's rows on screen: Dream (when more than one), Goals, Steps, Tasks, Blockers / Management Issues, Progress (%), Priority, Due Date, Next Action, using the same rules (MR-FR-17, MR-FR-18). | S |
| RM-FR-14 | **Table tools.** Search by text, sort by any column, quick filters "Overdue", "Blocked", "Due soon", paging at 50 rows. Overdue, Blocked and Completed rows are marked with an icon and a text label, never colour alone. Each task title links to the task. | S |
| RM-FR-15 | **Same rows.** The Details rows and the Excel Report sheet rows are produced by the same server code (`ReportRows`), checked by a test comparing both for one fixture. | M |

### D. Saved reports

| ID | Requirement | Priority |
| --- | --- | --- |
| RM-FR-16 | **Save as report.** From the Summary or Export tab, **Save as report** creates a report with a code (`R-001`), a name (default "{scope} – {period label}", up to 120 characters), the scope, period and filters in use, and status **Draft**. | M |
| RM-FR-17 | **Reports list.** A page **Reports** at `/vision-map/reports`, in the sidebar's Plan group under Management Report, lists saved reports: name, code, scope, period, status, compared with, last changed, finalised on. Filter by status and scope; sort by date; search by name. Archived reports are hidden unless **Show archived** is on. | M |
| RM-FR-18 | **Draft.** A draft shows **live** figures (recalculated each time it is opened) and editable commentary, with a banner "Draft · figures update until you finalise". Scope, period and filters can still be changed. | M |
| RM-FR-19 | **Finalise.** **Finalise** asks for confirmation ("Figures will be frozen as of {date}. You can still archive it or make a new draft from it."), then stores the snapshot (D-19-1): the Summary data, the Details rows, the comparison, the commentary and the language. The report shows "Final · frozen {date}". | M |
| RM-FR-20 | **Final is read-only.** A final report cannot be edited. Its page, downloads and share link are built only from the snapshot. The plan's later changes, deletions or archives do not change it; links to items that no longer exist show "(no longer in the plan)". | M |
| RM-FR-21 | **New from this.** On any report, **New report from this** creates a draft with the same scope and filters, the next period of the same length (a month after a month, a quarter after a quarter), the commentary copied as a starting point, and the source report set as the comparison. | M |
| RM-FR-22 | **Archive and restore.** A report can be archived and restored (business rule 12). A draft can also be deleted permanently after confirmation, because it holds no frozen record; a final report cannot be deleted, only archived. | M |
| RM-FR-23 | **Rename.** A final report's name can be changed; nothing else. | S |
| RM-FR-24 | **Limits.** Up to 200 saved reports per participant, archived ones included. At the limit, the app asks to delete drafts or archive old reports first. A snapshot over the V18 row limit (10,000 rows) cannot be finalised; the message asks to narrow the filter. | M |
| RM-FR-25 | **Export history.** Each report keeps a list of its downloads: format, language, date and time. The live (unsaved) report does not keep one. | C |

### E. Commentary

| ID | Requirement | Priority |
| --- | --- | --- |
| RM-FR-26 | **Sections.** Four text sections on a draft: **Highlights**, **Risks and issues**, **Decisions and support needed**, **Focus for next period**; each up to 2,000 characters, plain text with simple bullet lines ("- "). Plus an optional note per goal (up to 500 characters). | M |
| RM-FR-27 | **Autosave.** Commentary is saved as the participant types (at most once every 2 seconds) and on leaving the field, with "Saved" or "Not saved, retry" shown. | M |
| RM-FR-28 | **Rule-based draft.** **Suggest a draft** fills empty sections from the data with fixed rules, never overwriting text already written: Highlights from goals that moved to On track or Done and steps completed in the period; Risks from Off track goals with their reasons and Critical or High blockers; Decisions and support from open Decision, Money and Partner blockers and gaps without help planned (V17 BL-FR-22); Focus from the first open next action of each goal not done. Each suggested line is marked until edited. | M |
| RM-FR-29 | **AI draft (optional).** **Draft with AI** writes the four sections from the same facts the rule-based draft uses (titles, figures, health reasons, blocker titles and kinds), under the V15 rules: consent first, the daily limit and the monthly cap, and the same allow-list as D-19-2. The participant reviews and edits before saving. Hidden when AI is switched off. | C |
| RM-FR-30 | **Nothing needed.** "Decisions and support needed" has a **Nothing needed this period** check box, which prints that sentence instead of an empty section. | S |
| RM-FR-31 | **Where commentary shows.** On the Summary tab (a card above the tiles), the Excel **Commentary** sheet, the deck (RM-FR-41) and the PDF. Empty sections are left out of the files. | M |

### F. Compare and what changed

| ID | Requirement | Priority |
| --- | --- | --- |
| RM-FR-32 | **Compared with.** A saved report can be compared with one earlier **final** report on the same scope (default: the latest earlier final report on that scope). The live report can also pick one. Reports on a different scope cannot be chosen. | M |
| RM-FR-33 | **Headline changes.** Each headline tile shows the change since the compared report, as text and an arrow icon: "46% (+12)", "Overdue 3 (−2)". Whether a change is good or bad is stated in words for screen readers ("2 fewer overdue tasks"). | M |
| RM-FR-34 | **Goal changes.** The goal health table gains a **Change** column: health before → now, progress change in points, target date moved (old → new). Goals new since the compared report are marked **New**; goals in it but no longer in scope are listed under "No longer in this report" with the reason when known (Completed and archived, Archived, Moved to another dream). | M |
| RM-FR-35 | **What changed list.** A **What changed** card lists, most important first: goals that got worse in health, due dates that moved later (tasks, steps and goals, matched by id), new Critical and High blockers, blockers closed, goals that got better, tasks completed. Up to 15 items with "and N more" linking to the Details tab. | M |
| RM-FR-36 | **No comparison.** Without an earlier final report, the page says "Finalise this report to compare the next one with it" and the change columns are hidden. | M |
| RM-FR-37 | **Matching rule.** Items are matched between reports by their id. An item whose code or title changed is still the same item; the new title is shown with "(renamed)". | S |

### G. Exports

| ID | Requirement | Priority |
| --- | --- | --- |
| RM-FR-38 | **Exports from a report.** The Export tab works for the live report (as in V18), a draft and a final report. Files from a final report are built from its snapshot only, so the same report downloads the same figures every time. File names: `{Report name}_{yyyy-MM-dd}.{ext}`, made safe (letters, digits, dash and underscore, at most 80 characters; the scope name is used when the name has nothing safe left). | M |
| RM-FR-39 | **Choose sections.** Before downloading, the participant can tick which parts to include: Commentary, Summary, Goal health, Details / Report rows, Blockers, What changed, Next actions, Decisions and support. The choice applies to Excel sheets, deck slides and PDF sections, and is remembered per report. Title, executive summary and the as-of line are always included. | S |
| RM-FR-40 | **Excel additions.** New sheets **Commentary** (the four sections and goal notes, first after the title) and **Changes** (the What changed list and the goal Change columns, when compared). The Summary sheet shows the period figures and the headline changes. The Report sheet is unchanged in its columns (MRC-05 still holds). | M |
| RM-FR-41 | **Deck additions.** A **Highlights** slide after the title (the participant's Highlights, Risks and Focus), a **What changed** slide after the executive summary (when compared), and slide 8 "Decisions and support needed" opens with the participant's own text before the rule-based list. Goal notes go in the speaker notes of the progress slide. Overflow and empty-slide rules of MR-FR-25 and MR-FR-26 apply. | M |
| RM-FR-42 | **PDF export.** **Export to PDF** produces an A4 landscape report with OpenPDF (already in `pom.xml`): cover with name, scope, period and status; commentary; headline figures and changes; goal health table with reasons; What changed; blockers; next actions; decisions and support. Charts are drawn as simple bars. Page numbers and "Final · frozen {date}" or "Draft · {date}" in the footer. Khmer text uses the V18 Khmer font (V18 Q-04). | S |
| RM-FR-43 | **Draft watermark.** Files from a draft carry "DRAFT" in the title slide, the Excel title row and the PDF footer, so a reviewer cannot mistake it for a final report. | M |
| RM-FR-44 | **Language.** The participant chooses the file language at download (default: the app language). A final report keeps the language it was finalised in as its default but can be downloaded in the other; only fixed labels change, never the participant's text. | S |
| RM-FR-45 | **Native charts.** The deck's progress and priority charts can be produced as native, editable PowerPoint charts. Only if Q-05 is answered yes. | C |

### H. Sharing and reminders

| ID | Requirement | Priority |
| --- | --- | --- |
| RM-FR-46 | **Share link.** A **final** report can be shared by a read-only link, following the V9 plan share pattern: random token stored as a hash, expiry 7, 30 or 90 days (default 30), revoke at any time, and a count of views and last viewed date. A draft cannot be shared. | S |
| RM-FR-47 | **Shared view.** The link opens a public, read-only page (no sign-in) with the report's Summary, commentary, goal health and What changed from the snapshot, plus a PDF download. No links into the plan, no Details rows unless the participant ticked them when creating the link. The page sends `noindex` and no referrer. | S |
| RM-FR-48 | **Share safety.** Creating a link shows exactly which sections the reviewer will see and the expiry, and asks for confirmation. Expired or revoked links show "This report is no longer shared" and reveal nothing else. | S |
| RM-FR-49 | **Report reminder.** In Settings, an optional **Remind me to prepare a report**: weekly (a chosen weekday) or monthly (last working day), by in-app notice and, when mail is set up, by email to the participant only, through the existing `ReminderJob`. The reminder links to the Reports page with **New report from this** for the latest final report. | C |
| RM-FR-50 | **Share with my coach.** A participant in an active cohort can share a final report with their coach (D-19-3). The coach sees it read-only in the coach view, with the same sections as RM-FR-47. Unsharing removes access at once. | C |
| RM-FR-51 | **Stop sharing on archive.** Archiving a report revokes its share links and coach shares, after a confirmation that says so. | S |
| RM-FR-52 | **Audit.** Creating, viewing (anonymised: date and count only) and revoking share links are recorded in the existing security event log. | S |

## Non-functional requirements

| ID | Requirement |
| --- | --- |
| RM-NFR-01 | **Data scoping.** Every new endpoint uses only the signed-in participant's reports (business rule 13). Another user's report id answers 404. New endpoints are added to `AccessControlSweepTests`. The public share endpoint answers only for a valid, unexpired, unrevoked token, and the same 404 for every failure. |
| RM-NFR-02 | **Privacy by allow-list.** Snapshots are built from a dedicated, serialisable view model holding only allow-listed fields (D-19-2), never from entities. A test fills every private field in the fixture (journals, conflict notes, criticism, partner email and phone, AI conversation, coach comment) and checks that none appears in a snapshot, any file or the shared view. |
| RM-NFR-03 | **One source of truth.** Live reports, drafts, snapshots, the Details tab and all files come from the same view model. Final reports are formatted from the stored snapshot by the same writers. A test finalises a report, changes the plan, and checks the page and the three files still show the frozen figures. |
| RM-NFR-04 | **Snapshot integrity.** A snapshot stores a schema version and a SHA-256 checksum. On read, a checksum mismatch shows "This report could not be verified" and blocks sharing. Older snapshot versions stay readable after later releases (a reader per version, tested with a stored fixture). |
| RM-NFR-05 | **Performance.** For the V18 reference plan (20 dreams, 100 goals, 500 steps, 2,000 tasks): Summary with period figures and comparison under 2.5 seconds; finalising under 5 seconds; Excel, deck and PDF each under 5 seconds; a final report opens in under 1 second (no plan queries). Reports list under 500 ms for 200 reports. |
| RM-NFR-06 | **Storage.** A snapshot is at most 5 MB (compressed JSON). Total snapshot storage is monitored; the 200-report limit (RM-FR-24) keeps one participant under 1 GB in the worst case. |
| RM-NFR-07 | **Limits.** All downloads use the existing `REPORT_EXPORT` limit. Share link creation: 20 per day per account. Public share views: 60 per minute per token. AI drafts follow the V15 limits. |
| RM-NFR-08 | **Language.** New screen text through `tr()` with Khmer drafts in `km.ts`; new file text in `ReportText.java`; both listed in `km-review-v19.csv`. |
| RM-NFR-09 | **Accessibility.** WCAG 2.1 AA: the Details table is keyboard sortable with `aria-sort`; changes are stated in words, not only arrows or colour; the shared page and PDF have real headings; the PDF is tagged where OpenPDF allows. |
| RM-NFR-10 | **No new runtime dependency.** Excel, PowerPoint and PDF use Apache POI and OpenPDF, already in `pom.xml`. Any new library needs the engineering lead's approval, recorded here. |
| RM-NFR-11 | **Tests and coverage.** Unit tests for period figures, filter rules, health reasons, comparison and matching, rule-based drafts, file names and snapshot versioning; flow tests for the report lifecycle (draft, finalise, new from, archive, share, revoke); component tests for the period picker, Details table, commentary autosave and Reports list; API contract snapshot entries. CI coverage minimums (backend lines 94%, branches 75%; frontend lines 85%, statements 84%, branches 78%, functions 78%) stay met; each phase adds tests for its own code and runs the CI commands locally before pushing. |
| RM-NFR-12 | **AI is optional.** Everything except RM-FR-29 works with AI switched off. |

## Data and API changes (for solution design)

**Schema**

| Migration | Change | Notes |
| --- | --- | --- |
| `V79__obstacle_resolved_at.sql` | `obstacles`: add `resolved_at TIMESTAMP` (nullable). Back-fill with `updated_at` for rows already Resolved or Accepted | For RM-FR-08. The service sets it on a move to Resolved or Accepted and clears it on reopening. Back-filled values are approximate; the page notes this for periods before the release date |
| `V80__management_reports.sql` | New table `management_reports`: `id`, `user_id` (FK, indexed), `code`, `name`, `scope_kind`, `area_id`, `dream_id`, `period_kind`, `period_from`, `period_to`, `filters` (TEXT, JSON), `status` (DRAFT, FINAL), `archived`, `compare_to_report_id` (FK self, `ON DELETE SET NULL`), `highlights`, `risks`, `decisions_needed`, `nothing_needed` (BOOLEAN), `next_focus`, `goal_notes` (TEXT, JSON), `sections` (TEXT, JSON), `language`, `snapshot` (BYTEA, gzip JSON), `snapshot_version`, `snapshot_checksum`, `finalized_at`, `created_at`, `updated_at` | JSON kept in TEXT or BYTEA so tests on H2 and production on PostgreSQL behave the same. Unique `(user_id, code)` |
| `V81__report_exports_and_shares.sql` | New table `report_exports` (`id`, `report_id`, `format`, `language`, `created_at`) and `report_share_links` (`id`, `report_id`, `token_hash`, `sections` (TEXT, JSON), `expires_at`, `revoked_at`, `view_count`, `last_viewed_at`, `created_at`) | RM-FR-25, RM-FR-46. Token hashing as in V9 plan share |
| `V82__report_reminders_and_coach_shares.sql` | `app_users` (or the settings table in use): add `report_reminder` (NONE, WEEKLY, MONTHLY) and `report_reminder_day`. New table `report_coach_shares` (`report_id`, `coach_id`, `created_at`) | RM-FR-49, RM-FR-50. Phase 5 only; coach shares only after D-19-3 is signed off |

**Model and services**

- Split V18's model in two: `ManagementReportService` still loads entities in four batched queries, then maps them once to a new serialisable `ReportData` record (scope, period, filters, headline, period figures, charts, goal rows with health reasons, Details rows, blockers, attention items, next actions). The Excel, deck and new PDF writers take `ReportData` only. This is the base for snapshots (RM-NFR-02, RM-NFR-03).
- `ReportComparison` compares two `ReportData` values by id (RM-FR-32 to RM-FR-37). It never queries the plan.
- `ReportCommentaryDrafter` builds the rule-based draft (RM-FR-28). The AI draft goes through the existing V15 AI service and limits.
- `SavedReportService` handles the lifecycle (create, update draft, finalise, new from, rename, archive, restore, delete draft). Finalising runs in one transaction: build `ReportData`, compare, serialise, checksum, store.
- `ReportShareService` follows `PlanShareService`.

**API**

| Endpoint | Purpose |
| --- | --- |
| `GET /api/reports/management?areaId=&dreamId=&period=&from=&to=&priority=&owner=&health=&includeCompleted=&compareTo=` | V18 endpoint, extended with the period, filters and an optional comparison. Old calls keep working (the period defaults to this month and only adds fields) |
| `GET /api/reports/management/rows?…same filters…&q=&sort=&page=&size=` | Details tab rows (RM-FR-13 to RM-FR-15) |
| `GET /api/reports/management.{xlsx,pptx,pdf}?…same filters…&sections=&language=` | Live downloads (PDF new) |
| `GET /api/reports/saved?status=&scope=&archived=&q=&page=` | Reports list |
| `POST /api/reports/saved` | Save as report (from the current scope, period and filters) |
| `GET /api/reports/saved/{id}` | One report: live data for a draft, snapshot for a final report, plus commentary and comparison |
| `PUT /api/reports/saved/{id}` | Update a draft (name, scope, period, filters, commentary, sections, compare-to); rename only for a final report |
| `POST /api/reports/saved/{id}/finalize` | Freeze the snapshot |
| `POST /api/reports/saved/{id}/copy` | New report from this |
| `PATCH /api/reports/saved/{id}/archive`, `PATCH /api/reports/saved/{id}/restore`, `DELETE /api/reports/saved/{id}` | Archive, restore; delete only for drafts (409 for a final report) |
| `POST /api/reports/saved/{id}/commentary-draft?mode=RULES\|AI` | Suggest a draft (RM-FR-28, RM-FR-29) |
| `GET /api/reports/saved/{id}.{xlsx,pptx,pdf}?sections=&language=` | Downloads from a saved report |
| `GET/POST /api/reports/saved/{id}/shares`, `PATCH /api/reports/saved/{id}/shares/{shareId}/revoke` | Share links |
| `GET /api/public/reports/{token}` and `GET /api/public/reports/{token}.pdf` | Public shared view (no sign-in), rate-limited |

## Implementation phases

| Phase | Content | Requirements |
| --- | --- | --- |
| 1 | V18 carry-overs; `ReportData` refactor (no visible change, all V18 tests still pass); health reasons; Done – open work; period picker and period figures; `resolved_at` migration | RM-FR-01 to RM-FR-09, RM-NFR-02, RM-NFR-03 (live part) |
| 2 | Filters, Details tab, Reports list, save, draft, finalise, new from, archive, commentary with autosave and rule-based draft | RM-FR-10 to RM-FR-24, RM-FR-26 to RM-FR-28, RM-FR-30, RM-FR-31, RM-NFR-04 to RM-NFR-06 |
| 3 | Comparison, What changed, Excel and deck additions, draft watermark, section choice, language at download | RM-FR-32 to RM-FR-41, RM-FR-43, RM-FR-44 |
| 4 | PDF export, share links and the public view, audit, export history | RM-FR-25, RM-FR-42, RM-FR-46 to RM-FR-48, RM-FR-51, RM-FR-52, RM-NFR-07 |
| 5 | Could items as approved: AI draft, report reminder, coach share (after D-19-3), native charts (after Q-05); Khmer review file, docs (`API.md`, `DATA_MODEL.md`, `USER_GUIDE.md`), `UAT_V19.md` | RM-FR-29, RM-FR-45, RM-FR-49, RM-FR-50, RM-NFR-08 |

Each phase ends with the CI commands passing locally (`mvn verify`, `npm run build`, `npm run test:coverage`) and stops for confirmation (`CLAUDE.md` working rules 13 to 15).

## Acceptance criteria

| ID | Criterion |
| --- | --- |
| RMC-01 | A goal past its target date shows **Off track** with the reason "Target date passed N days ago" in the table tooltip, the Excel **Why** column and the deck. |
| RMC-02 | A Completed goal with one new open task shows **Done – open work** and appears in Needs attention. |
| RMC-03 | With **This month** chosen and 4 tasks completed this month, the period figures show "4 tasks completed", and the as-of line reads "{scope} · This month · as of {today}". |
| RMC-04 | A custom period longer than 366 days, or ending after today, cannot be applied and says why. |
| RMC-05 | Filtering by Priority **Critical** changes the tiles, the Details rows and all three files to Critical work only, and the files list the filter. |
| RMC-06 | The Details tab and the Excel Report sheet have the same rows in the same order for the same filter (automated). |
| RMC-07 | **Save as report** creates "R-001" as a Draft listed on the Reports page; reopening it after a task changes shows the new figures. |
| RMC-08 | After **Finalise**, completing another task does not change the report's page, Excel, deck or PDF; the page shows "Final · frozen {date}". |
| RMC-09 | A final report cannot be edited (only renamed) or deleted; the API answers 409 for both. A draft can be deleted after confirmation. |
| RMC-10 | **New report from this** on a monthly final report creates a draft for the next month, with the commentary copied and the source report as the comparison. |
| RMC-11 | **Suggest a draft** fills only empty sections, and a goal that moved to Done in the period appears under Highlights. |
| RMC-12 | Compared with a report where a goal was **At risk**, a goal now **On track** shows "At risk → On track" in the Change column and in What changed; a task whose due date moved later is listed with old and new dates. |
| RMC-13 | Overall progress 34% in the compared report and 46% now shows "46% (+12)" with the words "up 12 points". |
| RMC-14 | The Excel export of a compared report has Commentary and Changes sheets, and its Report sheet header is still exactly as MRC-05. |
| RMC-15 | The deck of a compared report with commentary has the Highlights slide second and the What changed slide after the executive summary; with neither, it has the V18 shape. |
| RMC-16 | Files from a draft show "DRAFT"; files from a final report do not. |
| RMC-17 | The PDF opens in a standard PDF reader, has page numbers, and shows Khmer text correctly when exported in Khmer. |
| RMC-18 | A share link to a final report opens without sign-in and shows only the ticked sections; after revoke or expiry it shows "This report is no longer shared". A draft has no **Share** button. |
| RMC-19 | With every private field filled in the fixture, no snapshot, file or shared view contains any of them (automated). |
| RMC-20 | Another user's report id answers 404 on every saved-report endpoint (`AccessControlSweepTests`); an unknown or revoked token answers the same 404. |
| RMC-21 | A stored snapshot fixture from version 1 still opens and exports after a change to the snapshot version. |
| RMC-22 | CI passes on the release commit with every coverage minimum met. |

## Assumptions, dependencies and risks

### Assumptions

- Participants report on a regular cycle (weekly, monthly or quarterly), so comparing with "the previous final report on the same scope" is the comparison they want.
- Past status cannot be rebuilt exactly from today's data; period figures rely on `completedAt`, progress logs, obstacle `createdAt` and the new `resolvedAt`, and the page says which figures are as of today (RM-FR-09).
- Reviewers read files or a link; sending mail to reviewers is not required in V19.

### Dependencies

- V18 Management Report (`ManagementReportService`, `ReportRules`, `ReportRows`, both writers, `ReportText`).
- V17 blocker placement and gap check; V15 AI rules (consent, limits, cap); V9 plan share pattern and decision D-6; V10 security event log and rate limiter.
- Apache POI 5.3.0 and OpenPDF, both already in `pom.xml`.

### Risks

| Risk | Impact | Mitigation |
| --- | --- | --- |
| A frozen report leaks data that was private or later removed | Harm, loss of trust | Allow-list view model (D-19-2, RM-NFR-02), automated check (RMC-19), shares only for final reports with confirmation (RM-FR-48) |
| Snapshots cannot be read after the model changes | Old reports lost | Versioned snapshots with a reader per version and a stored fixture test (RM-NFR-04, RMC-21) |
| Period figures look wrong for past periods | Lost trust | Only history-backed figures for the period; others labelled "as of today" (RM-FR-09) |
| The `ReportData` refactor breaks V18 behaviour | Regressions | Phase 1 refactor ships with no visible change and all V18 tests passing before new features |
| Shared links are forwarded beyond the intended reader | Exposure | Expiry, revoke, view count, no plan links, `noindex` (RM-FR-46 to RM-FR-48) |
| Storage grows with snapshots | Cost | Compression, 5 MB per snapshot, 200 reports per participant (RM-FR-24, RM-NFR-06) |
| CI coverage drops below the minimums | Blocked deploys | Tests per phase, CI commands run locally before every push (RM-NFR-11) |

## Open questions

- [ ] **Q-01** Is **This month** the right default period, or should it be the period since the last final report? (Draft: This month; "New report from this" already continues from the last report.)
- [ ] **Q-02** Should a final report be allowed to be "reopened" as a draft instead of copied? (Draft: no; copying keeps the frozen record intact, D-19-1.)
- [ ] **Q-03** Are 200 saved reports per participant and 5 MB per snapshot enough? (Draft: yes; about four years of weekly reports.)
- [ ] **Q-04** Should the Details rows be part of the snapshot, or only the Summary? (Draft: yes, both, so the Excel from a final report matches the day it was finalised.)
- [ ] **Q-05** Are native, editable PowerPoint charts now required (V18 Q-03)? (Draft: no; drawn shapes stay.)
- [ ] **Q-06** Should the app email a final report or its link to a reviewer's address? (Draft: no in V19; it sends participant data to a third party and needs its own consent and abuse controls.)
- [ ] **Q-07** May coaches read final reports their participants share with them (D-19-3)? (Draft: yes, read-only, within active cohorts.)
- [ ] **Q-08** Should a draft's commentary show in the shared view? (Draft: not applicable; only final reports can be shared.)

## Sign-off

| Role | Name | Decision | Date |
| --- | --- | --- | --- |
| Sponsor | | | |
| Product owner | | | |
| Lead coach | | | |

## Sources

- `CLAUDE.md`: Excel export requirements, Core Business Rules 9 to 14, Copyright and Source Use, working rules.
- BRD 18.0.0 (Management Report, including its build notes), BRD 17.0.0 (blocker placement, gap check), BRD 15.0.0 (AI rules), BRD 12.0.0 (coverage minimums), BRD 10.0.0 (security, rate limits), BRD 9.0.0 (D-6, plan share).
- `docs/UAT_V18.md` (MRC-09 manual check still open).
- Code at commit `d2c9206`: `controller/ManagementReportController.java`, `service/ManagementReportService.java`, `service/report/ManagementReport.java`, `service/report/ReportRules.java`, `service/report/ReportRows.java`, `service/report/ManagementReportExcelWriter.java`, `service/report/ManagementReportDeckWriter.java`, `dto/response/ManagementReportResponse.java`, `entity/Obstacle.java` (no `resolvedAt`), `entity/TaskItem.java` (`completedAt`, `blockedCount`), `service/PlanShareService.java`, `service/ReminderJob.java`, `pages/ManagementReportPage.tsx`, `components/report/ReportExportTab.tsx`, `pom.xml` (Apache POI, OpenPDF), migrations up to `V78`.
