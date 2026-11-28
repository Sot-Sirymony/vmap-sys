# UAT checklist – Plan checklist and Build my plan (V35)

For the BRD 35.0.0 acceptance criteria (PCC-01 to PCC-11). This release renames the sidebar Plan page to Plan
checklist, links it with Build my plan both ways, and adds an Inner blockers row. No migration. Use a staging server
and two accounts:

- **Planner**: two dreams. Dream A has no goals. Dream B has an unfinished Build my plan draft (open its builder, add
  a goal, leave without saving).
- **Coach**: the coach of an active cohort that includes Planner.

Tick each line and note anything that differs.

| ID | Steps | Expected | Automated test | Result |
| --- | --- | --- | --- | --- |
| PCC-01 | Look at the sidebar; open `/plan`; open the Strengths page from the checklist | The sidebar says **Plan checklist**; `/plan` opens it; the Strengths page says **Back to the plan checklist** | `AppLayout.test.tsx`, `PlanPage.test.tsx` | ☐ |
| PCC-02 | Plan checklist, Dream A: **Build my plan** | Dream A's builder opens | `PlanPage.test.tsx` | ☐ |
| PCC-03 | Plan checklist, Dream B | The button says **Continue Build my plan**; "Draft saved {date}…" shows; the builder opens with the goal you added | `PlanPage.test.tsx` | ☐ |
| PCC-04 | Dream A: **Do this next** on the first row not done (Goals, or Inner blockers if none named); go back and use the "or open the … page" link | The builder opens at that stage; the link opens the old page | `PlanPage.test.tsx` | ☐ |
| PCC-05 | Make Strengths and gaps the first row not done; **Do this next** | The Strengths page opens for that dream | `PlanPage.test.tsx` | ☐ |
| PCC-06 | Open `/dreams/{B}/build?stage=DATES`, then `?stage=NOPE` | The Dates stage shows with the draft unchanged; the unknown value opens the draft's own stage | `PlanBuilderPage.test.tsx` | ☐ |
| PCC-07 | Dream A with no inner blockers; add one for Dream A; add one for all dreams; resolve one; archive one | The row starts not done, then shows the count of active blockers only | `PlanChecklistFlowTests` | ☐ |
| PCC-08 | Compare "The core of this plan is written" before and after naming an inner blocker | Unchanged by the Inner blockers row | `PlanChecklistFlowTests` | ☐ |
| PCC-09 | Look at the checklist, Download PDF, and the coach's view of Planner | No blocker text anywhere (the coach sees blockers only under Journals when shared, BRD 34) | `PlanChecklistFlowTests`, `InnerBlockerFlowTests` | ☐ |
| PCC-10 | Save a plan in the builder; open the dream on the Vision Map | **See your plan checklist** opens `/plan?dreamId=…`; a **Plan checklist** link sits beside **Build my plan** | `PlanBuilderPage.test.tsx`, `DreamDetailPage.test.tsx` | ☐ |
| PCC-11 | CI | Builds, tests, coverage check, `ControllerRulesTest` and the SonarQube gate pass | CI | ☐ |

Khmer: review the 12 drafts in `docs/km-review-v35.csv`, and check the checklist header buttons at 360px.
