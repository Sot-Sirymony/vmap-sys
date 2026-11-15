# UAT checklist – Management Report (V18)

For the BRD 18.0.0 acceptance criteria (MRC-01 to MRC-15). Use a test account with at least two dreams, several goals,
steps and tasks, one Blocked task, one overdue task and one open blocker. Tick each line and note anything that differs.

| ID | Steps | Expected | Automated test | Result |
| --- | --- | --- | --- | --- |
| MRC-01 | Open **Management Report** from the sidebar | Under Timeline; opens `/vision-map/report` on Summary with "All dreams · as of {today}" | `ManagementReportPage.test.tsx` | ☐ |
| MRC-02 | Choose **One dream**, then download Excel and PowerPoint | Tiles, charts and URL (`?dreamId=`) change; both files cover only that dream | `ManagementReportPage.test.tsx`, `ManagementReportExcelFlowTests` | ☐ |
| MRC-03 | On **Tasks by status**, click **Show as table** | The summary ("12 of 40 tasks are completed") and the table show the same counts | `ManagementReportPage.test.tsx` | ☐ |
| MRC-04 | Look at the goal health table | A goal past its date shows **Off track**; one with only a Low open blocker **At risk**; a Completed goal **Done** | `ManagementReportFlowTests` | ☐ |
| MRC-05 | Open the Excel **Report** sheet | Header is exactly Goals, Steps, Tasks, Blockers / Management Issues, Progress (%), Priority, Due Date, Next Action (Dream first for more than one dream) | `ManagementReportExcelFlowTests` | ☐ |
| MRC-06 | Find a Blocked task whose step has an open blocker | Its issues cell lists the reason and the blocker; the row is orange | `ManagementReportExcelFlowTests` | ☐ |
| MRC-07 | Find a step with no tasks | One row, Tasks empty, with the step's progress and target date | `ManagementReportExcelFlowTests` | ☐ |
| MRC-08 | Find a task with no Next Action | Shows the next open task in its step, marked "(next task)" | `ManagementReportExcelFlowTests` | ☐ |
| MRC-09 | Open the deck in **PowerPoint** and **LibreOffice Impress** | 8 slides in order (7 when nothing needs a decision), each with a title and number, **no repair prompt** | `ManagementReportDeckFlowTests` (structure only; opening is manual) | ☐ PowerPoint ☐ LibreOffice |
| MRC-10 | With 12 blockers, look at slide 5 | 6 blockers and "and 6 more (see the Excel report)" | `ManagementReportDeckFlowTests` | ☐ |
| MRC-11 | Compare Summary, Excel Summary sheet and deck slide 2 | Same overall progress, overdue and blocked counts | `ManagementReportExcelFlowTests`, `ManagementReportDeckFlowTests` (same model) | ☐ |
| MRC-12 | Give an obstacle a private conflict note and a partner an email; export both files | Neither appears anywhere | `ManagementReportExcelFlowTests`, `ManagementReportDeckFlowTests` | ☐ |
| MRC-13 | Try another account's `dreamId` or `areaId` in the address bar | "Not found" on the page; 404 from all three endpoints | `ManagementReportFlowTests`, `ManagementReportExcelFlowTests`, `ManagementReportDeckFlowTests` | ☐ |
| MRC-14 | Switch the app to Khmer and export both files | Page, Excel headings and deck headings in Khmer, displaying correctly | `ManagementReportExcelFlowTests`, `ManagementReportDeckFlowTests` (text only; display is manual) | ☐ |
| MRC-15 | Check CI on the release commit | Backend, frontend, SonarQube and security jobs pass, coverage minimums met | CI | ☐ |

Khmer wording: review [km-review-v18.csv](km-review-v18.csv) (fill the "Reviewed Khmer" column where a draft is wrong).
