# UAT checklist – Faster, easier Streamline AI (V32)

For the BRD 32.0.0 acceptance criteria (SFC-01 to SFC-15). This release saves AI suggestions, adds a model setting for
Streamline, batch apply with Undo, Draft all stages, real reminders, the "taking twice as long" suggestion, the weekly
nudge and server-side dismissals (migrations V99 to V102). Use a staging server and four accounts:

- **Busy**: a goal with at least 8 open steps, several tasks per step, AI consent on, email reminders on, and no
  weekly review written in the last 7 days.
- **Slow**: a step with at least 3 tasks completed 4 to 6 months ago and 3 completed this month, the recent ones taking
  at least twice as long from In progress to Completed. (Ask a developer to set the dates if needed.)
- **Coach**: the coach of an active cohort that includes Busy.
- **Admin**: an administrator, with AI turned on and a monthly budget of at least US$5.

Tick each line and note anything that differs.

| ID | Steps | Expected | Automated test | Result |
| --- | --- | --- | --- | --- |
| SFC-01 | Busy: open a review at stage 2 and let the suggestions load. Reload the page, then open it in another browser | The same cards show at once, labelled "Suggested on {date}"; no new AI request appears in Admin → AI usage | `StreamlineAiFlowTests`, `streamlineStages.test.tsx` | ☐ |
| SFC-02 | Apply one card, then delete an item by hand. Reload | The applied card and any card about the deleted item are gone; the other cards stay | `StreamlineAiFlowTests`, `StreamlineSuggestionStoreTest` | ☐ |
| SFC-03 | Turn AI consent off in Account settings, then reopen the stage | The saved cards still show and can be applied; a note says why new ones are missing; **Suggest again** is disabled | `StreamlineAiFlowTests`, `streamlineStages.test.tsx` | ☐ |
| SFC-04 | Admin: AI settings → **Model for Streamline suggestions** = Claude Haiku 5.5. Busy: **Suggest again** | The new suggestions are logged with Haiku; summary advice still uses the main or plan-building model | `StreamlineAiFlowTests` | ☐ |
| SFC-05 | Stage 2: tick a Delete, a Merge and a Keep card, **Apply selected**, then **Undo** | All three are applied, then all three go back: the step and task are restored, the merged task's hours and description are as before, and the summary counts nothing as brought back | `StreamlineBatchFlowTests` | ☐ |
| SFC-06 | Apply two cards with **Apply all**; edit one of the items by hand; then **Undo** from **Recent changes** | The other item goes back; the edited one is named "Changed since, so left as they are" | `StreamlineBatchFlowTests`, `streamlineStages.test.tsx` | ☐ |
| SFC-07 | Apply a batch, then finish the stage. (Or ask a developer to age a batch past 24 hours.) | The batch has no **Undo** in Recent changes; undoing it by its link is refused | `StreamlineBatchFlowTests` | ☐ |
| SFC-08 | A new review at stage 1: **Draft all stages**. Open stage 3 under **Drafts for later stages**. Finish stage 1, delete an item in stage 2, finish stage 2 | One AI request; stage 3 is read-only with "You can apply these when you reach this stage". At stage 3 the cards say "Drafted before you finished the earlier stages" and none is about the deleted item | `StreamlineAiFlowTests`, `streamlineStages.test.tsx` | ☐ |
| SFC-09 | Admin: set the monthly budget to just above this month's spend (less than US$0.50 left). Busy: open a review | **Draft all stages** is disabled and says the budget has no room; single-stage suggestions still work | `StreamlineAiFlowTests`, `streamlineStages.test.tsx` | ☐ |
| SFC-10 | Stage 5: **Remind me** on a task, how often = every week, on today's weekday. Open Today; wait for the next reminder email. Then complete the task | The chip says "Reminds you every {day}"; the task is on Today under **Reminders** and in the email under "You asked to be reminded about"; after completing it, it is gone from both | `StreamlineAutomationFlowTests`, `streamlineStages.test.tsx`, `TodayPage.test.tsx` | ☐ |
| SFC-11 | Slow: Streamline page | The step appears as "Taking twice as long as before — Tasks now take X days, up from Y days" | `StreamlineAutomationFlowTests` | ☐ |
| SFC-12 | Busy: Dashboard; then the reminder email; then Account settings → switch off the weekly Streamline suggestion | "This week's suggestion" shows the goal on the Dashboard and as one line in the email; the same goal is not suggested again for 30 days; with the switch off, neither shows | `StreamlineAutomationFlowTests`, `StreamlineListPage.test.tsx`, `AccountSettingsPage.test.tsx` | ☐ |
| SFC-13 | Busy: **Not now** on a suggestion; open the Streamline page on another device or browser | The suggestion stays hidden there too; it returns after 90 days | `StreamlineAutomationFlowTests`, `StreamlineListPage.test.tsx` | ☐ |
| SFC-14 | Coach: open Busy's participant page → a Streamline review | The review is read-only: no AI suggestions panel, no **Draft all stages**, no **Recent changes** or **Undo**, no reminder settings | `StreamlineAutomationFlowTests`, `StreamlineReviewPage.test.tsx`, `AccessControlSweepTests` | ☐ |
| SFC-15 | CI | Frontend (`npx vitest run --testTimeout=20000`, `npm run build`) and backend (`mvn verify`, coverage ≥ 94% lines) pass, and the SonarCloud gate passes | CI | ☐ |
| MOVE | Before upgrading, dismiss a suggestion on one browser; after upgrading, open the Streamline page in that browser | The suggestion stays hidden, now on every device | `StreamlineListPage.test.tsx` | ☐ |
| A11Y | The pages above in Modern, Classic and Liquid Glass, light and dark, English and Khmer, and at 360px | No axe violations; no sideways scrolling; Khmer text does not clip; the Undo result is announced | `e2e/streamline.e2e.ts` (axe, phone) | ☐ |
| KM | Khmer reviewer checks `docs/km-review-v32.csv` | Drafts approved or corrected | – | ☐ |
| MEASURE | 30 days after release, compare with the 30 days before (BRD 32 "Measuring the benefits") | Report SFM-01 to SFM-06 | – | ☐ |
