# UAT checklist – Report Management (V19)

For the BRD 19.0.0 acceptance criteria (RMC-01 to RMC-22) and the phase 5 items (RM-FR-29, RM-FR-49). Use a test
account with at least two dreams, several goals, steps and tasks, one Blocked task, one overdue task, one open blocker
and one Completed goal. Tick each line and note anything that differs.

| ID | Steps | Expected | Automated test | Result |
| --- | --- | --- | --- | --- |
| RMC-01 | Give a goal a target date in the past | **Off track** with "Target date passed N days ago" in the tooltip, the Excel **Why** column and the deck | `ReportRulesTest`, `ManagementReportFlowTests` | ☐ |
| RMC-02 | Add an open task under a Completed goal | **Done – open work**, listed in Needs attention | `ReportRulesTest` | ☐ |
| RMC-03 | Choose **This month** after completing 4 tasks this month | "4 tasks completed"; as-of line "{scope} · This month · as of {today}" | `ManagementReportFlowTests`, `ReportPeriodTest` | ☐ |
| RMC-04 | Try a custom period over 366 days, or ending after today | Cannot be applied; the page says why | `ReportPeriodTest`, `ReportControls` tests | ☐ |
| RMC-05 | Filter by Priority **Critical** | Tiles, Details rows and all three files show Critical work only; the files list the filter | `SavedReportFlowTests` | ☐ |
| RMC-06 | Compare the Details tab with the Excel Report sheet | Same rows, same order | `SavedReportFlowTests` | ☐ |
| RMC-07 | **Save as report**, change a task, reopen | "R-001" Draft on Saved reports, showing the new figures | `SavedReportFlowTests` | ☐ |
| RMC-08 | **Finalise**, then complete another task | Page, Excel, deck and PDF unchanged; "Final · frozen {date}" | `SavedReportFlowTests` | ☐ |
| RMC-09 | Try to edit or delete a final report; delete a draft | Only rename works (409 otherwise); a draft deletes after confirmation | `SavedReportFlowTests`, `SavedReportPage.test.tsx` | ☐ |
| RMC-10 | **New report from this** on a monthly final report | Draft for the next month, commentary copied, compared with the source | `SavedReportFlowTests` | ☐ |
| RMC-11 | **Suggest a draft** with one section written | Only empty sections fill; a goal done in the period is under Highlights | `ReportCommentaryDrafterTest`, `SavedReportFlowTests` | ☐ |
| RMC-12 | Compare with a report where a goal was At risk, now On track | "At risk → On track" in Change and What changed; a later due date shows old and new dates | `ReportComparison` tests | ☐ |
| RMC-13 | Overall progress 34% before, 46% now | "46% (+12)", read as "up 12 points" | frontend report tests | ☐ |
| RMC-14 | Excel of a compared report | Commentary and Changes sheets; Report sheet header as MRC-05 | Excel flow tests | ☐ |
| RMC-15 | Deck of a compared report with commentary | Highlights slide second, What changed after the summary; V18 shape with neither | Deck flow tests | ☐ |
| RMC-16 | Download from a draft and from a final report | "DRAFT" only on the draft's files | Excel, deck and PDF tests | ☐ |
| RMC-17 | Open the PDF in a PDF reader, in English and Khmer | Opens, has page numbers, Khmer displays correctly | `ManagementReportPdfWriter` tests (text only; display is manual) | ☐ English ☐ Khmer |
| RMC-18 | Share a final report, open the link signed out, then stop it | Only ticked sections; afterwards "This report is no longer shared"; no **Share** on a draft | `ReportShareFlowTests`, `SharedReportPage.test.tsx` | ☐ |
| RMC-19 | Fill every private field, then check snapshot, files and shared view | None of them appear | privacy flow tests | ☐ |
| RMC-20 | Try another account's report id or a revoked token | 404 everywhere | `AccessControlSweepTests` | ☐ |
| RMC-21 | Open a version 1 snapshot fixture | Opens and exports | `ReportSnapshotCodecTest` | ☐ |
| RMC-22 | Check CI on the release commit | Backend, frontend, SonarQube and security jobs pass, coverage minimums met | CI | ☐ |
| RM-FR-29 | With AI on and consent given, press **Draft with AI** on a draft with Risks written | Empty sections fill with "- " lines marked Suggested; Risks unchanged; nothing saved until edited. Hidden with AI off | `ReportPhase5FlowTests`, `SavedReportPage.test.tsx` | ☐ |
| RM-FR-49 | Choose a weekly report reminder for today's weekday | Notice on Dashboard (**Open Reports**) and Saved reports (**New report from this**); gone after Finalise; email that day when mail is set up | `ReportPhase5FlowTests`, `ReportRemindersTest`, `ReportReminderNotice.test.tsx` | ☐ |
| RM-FR-04 | Open a V19 deck in **PowerPoint** and **LibreOffice Impress** | No repair prompt (carried over from MRC-09) | manual | ☐ PowerPoint ☐ LibreOffice |

Not in this release: **Share with my coach** (RM-FR-50, waiting on D-19-3) and native PowerPoint charts (RM-FR-45,
Q-05 answered no).

Khmer wording: review [km-review-v19.csv](km-review-v19.csv) (fill the "Reviewed Khmer" column where a draft is wrong).
