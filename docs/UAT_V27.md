# UAT checklist – Streamline review (V27)

For the BRD 27.0.3 acceptance criteria (SRC-01 to SRC-10). This release adds the Streamline review, task start times
and status history, and repeating tasks (migration V94). Use a staging server and three accounts:

- **Busy**: a goal with at least 5 steps, several tasks per step (some with estimates), one task Blocked, one Waiting,
  and at least 3 tasks moved to In progress and then Completed after this release.
- **Coach**: a coach of an active cohort that includes Busy.
- **Admin**: an administrator.

Tick each line and note anything that differs.

| ID | Steps | Expected | Automated test | Result |
| --- | --- | --- | --- | --- |
| SRC-01 | Busy: Goals → a goal's menu → **Streamline**. Try to open stage 4. Choose **Not needed…** with "no" as the reason | Stages 2–5 are locked; the short reason is refused. Answer stage 1 and **Done, next stage** opens stage 2 | `StreamlineReviewFlowTests`, `StreamlineReviewPage.test.tsx`, `e2e/streamline.e2e.ts` | ☐ |
| SRC-02 | Stage 1: **Select all**, set "Myself", name "Me", verdict "Needed", **Apply to selected**; then set one item to "Not sure" with no name | All items answered at once; the "Not sure" item needs no name and is listed first in stage 2 | same | ☐ |
| SRC-03 | Stage 2: **Delete** a step; then **Undo**. Delete a task and **Restore** it from "Cut in this review" | The step and its tasks are archived at once and come back together; the summary counts "brought back"; nothing is permanently deleted | same | ☐ |
| SRC-04 | Stage 2: **Merge into** one step from another | The source step's open tasks move to the target; the source is archived | `StreamlineReviewFlowTests` | ☐ |
| SRC-05 | Finish all stages | The summary shows steps, tasks and hours before → after, and cut / brought back as counts (no percentages). If you cut but restored nothing, the hint "You brought nothing back…" appears | `StreamlineReviewFlowTests`, `StreamlineReviewPage.test.tsx` | ☐ |
| SRC-06 | Stage 4 on Busy's goal; open a completed task's details in the Excel export | Median days from start to done and waiting / blocked time appear; tasks finished before this release have no figure | `StreamlineReviewFlowTests`, `CycleTimeCalculatorTest` | ☐ |
| SRC-07 | Stage 5: **Make it repeat** weekly on a task due yesterday; wait for the hourly job (or ask a developer to run it); then archive the step | Exactly one new task appears, due in the future; after archiving the step, no more are made | `StreamlineReviewFlowTests` | ☐ |
| SRC-08 | Coach: open Busy's participant page → Streamline reviews. Admin: try to open Busy's review URL | The coach sees the review read-only (no buttons that change it); the admin gets "not found" | `StreamlineReviewFlowTests`, `AccessControlSweepTests` | ☐ |
| SRC-09 | Each page above in Modern, Classic and Liquid Glass, light and dark, English and Khmer, and at 360px | No axe violations; no sideways scrolling; Khmer text does not clip | `e2e/streamline.e2e.ts` (axe, phone) | ☐ |
| SRC-10 | CI | Frontend (`npx vitest run --testTimeout=20000`, `npm run build`) and backend (`mvn verify`) pass | CI | ☐ |
| AI | With AI on: **Suggest for this stage**, then **Apply** one card. Then turn AI consent off, or reach the daily limit | Suggestions change nothing until applied. Without AI, the panel says why and the review still works | `StreamlineReviewPage.test.tsx`, `StreamlineAiServiceTest` | ☐ |
| CHIP | After finishing a goal's review, open Goals; then a step's review and Steps. Click the chip | "Streamlined {date}: −N steps" (or tasks) appears on the goal or step and opens the review | `streamlineParts.test.tsx` | ☐ |
| MAP | Vision Map: open a goal's and a step's menu | Both offer **Streamline** | `VisionMapTree.test.tsx` | ☐ |
| REPEAT | Streamline page → Repeating tasks: change how often, **Stop repeating**, then **Restart** | The change is saved; a stopped rule makes no new tasks; tasks already made stay | `streamlineParts.test.tsx`, `StreamlineReviewFlowTests` | ☐ |
| DASH | Dashboard → Insights | The Streamline card shows open reviews, items cut and brought back this quarter, the median cycle time with View as table, and at most one suggestion | – | ☐ |
| KM | Khmer reviewer checks `docs/km-review-v27.csv` | Drafts approved or corrected | – | ☐ |
