# UAT checklist – Gaps and inner blockers, kept apart (V36)

For the BRD 36.0.0 acceptance criteria (GBC-01 to GBC-08). Strengths and Gaps stops suggesting six-chain weaknesses,
six-chain weaknesses already saved can move to inner blockers, and both pages show one rule. No migration. Use a
staging server and one account, **Learner**:

- A 2-minute check with "Giving up too soon" scored 2, and a Work Style profile.
- On a dream's Strengths and Gaps: the weakness "I give up when progress is slow." saved from a suggestion before
  V36, covered by a partner; and a manual weakness "No experience with databases (SQL)".

| ID | Steps | Expected | Automated test | Result |
| --- | --- | --- | --- | --- |
| GBC-01 | Open Strengths and Gaps | Suggestions show the Work Style items only, and the line "Your check suggests something that holds you back inside…" | `StrengthsPage.test.tsx` | ☐ |
| GBC-02 | **Add as an inner blocker** | The Assessment page opens, with My inner blockers | `StrengthsPage.test.tsx` | ☐ |
| GBC-03 | Look at both weaknesses | Only "I give up when progress is slow." has **Move to inner blockers** | `StrengthsPage.test.tsx`, `GapToInnerBlockerFlowTests` | ☐ |
| GBC-04 | **Move to inner blockers** | The form shows the text, the evidence as How it shows up, "Giving up too soon" and the dream; Short practice is empty and required | `StrengthsPage.test.tsx` | ☐ |
| GBC-05 | Write a practice, **Move it**; open My inner blockers; show archived on Strengths and Gaps; restore the item; open Partners | The blocker is listed; the item is archived and can be restored; the partner is still there | `GapToInnerBlockerFlowTests` | ☐ |
| GBC-06 | Make 10 inner blockers active, then try to move | Refused with the limit message; the item stays | `GapToInnerBlockerFlowTests` | ☐ |
| GBC-07 | Look at the top of Strengths and Gaps and at My inner blockers | The rule line shows on both, each linking to the other page | `StrengthsPage.test.tsx`, `InnerBlockers.test.tsx` | ☐ |
| GBC-08 | CI | Builds, tests, coverage check, `ControllerRulesTest`, the access sweep and the SonarQube gate pass | CI | ☐ |

Khmer: review the 15 drafts in `docs/km-review-v36.csv`, and check the move form at 360px.
