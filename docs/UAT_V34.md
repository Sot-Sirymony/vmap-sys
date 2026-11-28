# UAT checklist – My inner blockers (V34)

For the BRD 34.0.0 acceptance criteria (IBC-01 to IBC-12). This release adds the participant's own inner blockers
(blocker, how it shows up, short practice), their daily habits and the weekly check-in (migration V104). Use a staging
server and three accounts:

- **Learner**: a dream such as "Learn to code", a recent 2-minute check with "Giving up too soon" scored 2, and fewer
  than three active habits.
- **Coach**: the coach of an active cohort that includes Learner.
- **Admin**: an administrator.

Tick each line and note anything that differs.

| ID | Steps | Expected | Automated test | Result |
| --- | --- | --- | --- | --- |
| IBC-01 | Learner: Build my plan → Inner blockers → **Add your own blocker**: "Inconsistency / Big bursts, then weeks of nothing / Never miss twice; 15 minutes counts" → **Save blocker**. Open the Assessment page | The card shows under **Your inner blockers**; on the Assessment page it shows under **My inner blockers** with the dream's title | `InnerBlockerFlowTests`, `InnerBlockers.test.tsx` | ☐ |
| IBC-02 | Try to save with an empty blocker or practice; paste 121 characters into **Possible blocker** | The form is not sent; the field stops at 120 characters (the server refuses more) | `InnerBlockerFlowTests`, `InnerBlockers.test.tsx` | ☐ |
| IBC-03 | On the stage, **Add as my blocker** under "Giving up too soon" | The form opens with "Giving up too soon", the chain linked and the program's practice; nothing is saved until **Save blocker** | `InnerBlockers.test.tsx` | ☐ |
| IBC-04 | (Changed during build, D-34-8.) A Build my plan draft always has a saved dream | A blocker added on the stage is linked to that dream at once | `InnerBlockers.test.tsx` | ☐ |
| IBC-05 | **Make it a daily habit** → keep the name → **Add habit**. Tick it on Today. Then **Archive** the blocker → **Keep the habit** | The habit is on Today; the card shows "Daily habit: … · 1-day streak"; after archiving, the habit is still on Today | `InnerBlockerFlowTests`, `InnerBlockers.test.tsx` | ☐ |
| IBC-06 | Add blockers until 10 are active, then try an 11th, on the Assessment page and on the stage | **Add your own blocker** is disabled with "You have 10 active blockers. Resolve or archive one first." | `InnerBlockerFlowTests`, `InnerBlockers.test.tsx` | ☐ |
| IBC-07 | Write a weekly review two weeks running, answering **No** for one blocker each time. Write a daily review | The blocker card shows "This practice isn't helping yet. Try changing it."; the daily review has no inner blocker section | `InnerBlockerFlowTests`, `InnerBlockers.test.tsx` | ☐ |
| IBC-08 | Coach: open Learner on the Coach page. Learner: Account → share journals with my coach. Coach: reload. Admin: open Learner in Users | Before sharing, no blockers; after, the blockers show read-only under Journals; the admin sees no blockers | `InnerBlockerFlowTests` | ☐ |
| IBC-09 | Learner: Account → download my data. Then delete a test account that has blockers | The download has `innerBlockers` and `innerBlockerCheckins`; the deleted account's rows are gone | `InnerBlockerFlowTests`, `AccountExportShapeTests`, account deletion tests | ☐ |
| IBC-10 | Use the Dream Coach and Draft with AI on a weekly review, then check Admin → AI usage | No blocker text is in any request | `InnerBlockerFlowTests.noAiServiceReadsInnerBlockers` | ☐ |
| IBC-11 | Build my plan → Check and add | "Inner blockers for this dream: n — …" shows; saving is not blocked by it | `InnerBlockers.test.tsx` | ☐ |
| IBC-12 | CI | Builds, tests, coverage check, `ControllerRulesTest` and the SonarQube gate pass | CI | ☐ |

Khmer: review the 59 drafts in `docs/km-review-v34.csv`, and check the Assessment page and the stage at 360px.
