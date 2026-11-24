# BRD – Blocker analysis in the Vision Map plan assistant

Nov 24, 2026

## Document control

This is draft 28.0.0. It keeps every requirement in BRD 27.0.3 and the earlier BRDs. It adds a **blocker analysis** to
the plan assistant on the Vision Map page: one action that finds everything holding a dream back, ranks it by impact,
explains what each blocker holds up and what is missing, and points to the next action. With AI, it also suggests a
likely root cause and a first move for each blocker.

| Item | Value |
| --- | --- |
| Document | Business Requirements Document (BRD) – Blocker analysis addendum |
| Version | 28.0.0 – Draft |
| Parent documents | CLAUDE.md core hierarchy and business rules (BR-6 blocker reason, BR-9 overdue, BR-12 soft delete, BR-13 and BR-14 user scoping), BRD 9 (AI with consent, the daily limit and the monthly cap; AI-FR-09 plan check), BRD 11 (PB-FR-34 rating, PB-NFR-03 session limit), BRD 15 (AH-FR-35 partner matches), BRD 17 (D-17-1 obstacles anchored to steps and tasks; D-17-2 blockers live on steps and tasks; BL-FR-22 gap needs a helper; BL-FR-24 to BL-FR-29 break a stuck task down), BRD 27 (D-27-7 status history and blocked time) |
| Business owner | To be confirmed |
| Product owner | To be confirmed |
| Approvers | Sponsor, Product owner, Design reviewer, Khmer reviewer |

| Version | Change | Author |
| --- | --- | --- |
| 28.0.0 | First draft, from the product owner's request of 24 Nov 2026 ("under page vision map please help improve on plan assistant to help analysis blocking"). Written after the feature was built at the product owner's request, so it records the built behaviour; open questions Q-28-1 to Q-28-5 ask the product owner to confirm the choices made | BA |

---

## Executive summary

The Vision Map plan assistant helps a user **build** a dream's plan: it suggests goals, steps, tasks and resources,
and "Check my plan" lists missing owners, dates and resources. It does not help the user see **what is stuck**.

The information is already in VMS, but scattered:

- A task can be **Blocked** (with a required blocker reason, BR-6) or **Waiting**. So can a step.
- A step can record a **gap** (missing knowledge, a skill or money, BRD 17), and the plan check flags a gap with no
  helper (BL-FR-22).
- **Obstacles** can be linked to a step or task (D-17-1), and **partners** to a goal, step or task.
- Since V27 the **status history** gives the days a task has spent Blocked or Waiting (D-27-7).
- A stuck task can be **broken into smaller tasks** (BL-FR-24 to BL-FR-29).

Nothing brings these together for one dream, answers "what should I unblock first?", or explains why a blocker
matters. V28 adds **Analyse blockers** to the plan assistant. It lists every blocker in the dream, ranked by impact
(how stuck it is, how important, how much later work waits on it, how close the goal date is), shows what each one
is missing and who could help, and links to the existing actions: break it down, record an obstacle, find a helper,
or edit it. The rule-based analysis always works; AI, when available, adds a root cause, a first move and the kind of
helper to ask for each blocker, plus what the blockers have in common.

Nothing is saved by the analysis itself.

---

## Decisions recorded by this BRD

| ID | Decision |
| --- | --- |
| D-28-1 | The analysis lives in the **existing plan assistant card** on the Vision Map page, next to "Check my plan", not on a new page. It covers the **dream shown** on the map |
| D-28-2 | A **blocker** is one of three kinds: a task or step with status **Blocked**; a task or step with status **Waiting**; or a step with a **gap that needs a helper** (the BL-FR-22 rule, reused unchanged). Overdue work that is not Blocked or Waiting is not a blocker here; "Check my plan" already lists it |
| D-28-3 | The analysis has a **rule half and an AI half**, like the plan check (AI-FR-09). The rule half always runs and needs no AI or consent. The AI half runs only under the usual V9 rules (consent, daily limit, monthly cap) and the plan session limit (PB-NFR-03); when it cannot run, the rule half is shown with a note |
| D-28-4 | Blockers are **ranked by an impact score** (§3.2), biggest first. The score is shown only as the order, never as a number |
| D-28-5 | The analysis **never saves or changes anything**. Each action opens an existing page or dialog that has its own checks: the break-down dialog (BL-FR-24), the obstacle form pre-filled with the task and reason (BL-FR-28), the partner form pre-filled with the goal and support type, or the item's edit form on the map |
| D-28-6 | The "who can help" suggestion uses the **existing obstacle-type to partner-type mapping** (Knowledge → Mentor or expert, Skill → Technical expert or trainer, Time → Delegate or assistant, Money → Financial partner or sponsor, Motivation → Accountability partner, Decision → Advisor). AI may name a role; it never names a person (AI-NFR-04) |
| D-28-7 | What is missing (the obstacle type) comes first from an **open obstacle** on the task, then on its step, then from the step's **gap kind**. Only when none exists may AI suggest a type, and it is labelled **"AI guess"** |
| D-28-8 | Only the participant's own records are read (BR-13, BR-14). Coaches and admins do not see the analysis; it is computed on request and not stored |

---

## 1. Fit analysis: what VMS has today

| Need | What exists today | Gap |
| --- | --- | --- |
| See what is stuck in one dream | Tasks Board highlights Blocked tasks across all dreams; Vision Map shows status badges | No single list for one dream; Waiting and step-level blocks are easy to miss |
| Know what to unblock first | Obstacle priority (AH-FR-41) ranks open obstacles | Blocked tasks with no obstacle are not ranked; nothing weighs how much later work waits on a blocker |
| Know how long it has been stuck | Status history and blocked days (D-27-7), used only inside a Streamline review | Not shown where the user plans |
| Know what is missing and who can help | Gap kind on steps; obstacle types; partner-type mapping on the Partners page | Not connected to the blocked task on the map |
| Act on a blocker | Break down (BL-FR-24), obstacle form (BL-FR-28), partner form | The user has to find each one |
| Understand why | Obstacle helper (V15) works on one obstacle at a time | No view across a dream's blockers |

---

## 2. The blocker analysis

### 2.1 Where it starts

The plan assistant card on the Vision Map (`/vision-map`, `/dreams/:id`) gains an **Analyse blockers** button beside
"Check my plan". It is always enabled, also without AI consent.

### 2.2 What it shows

1. **Summary chips**: number blocked, number waiting, number of steps that need a helper, and number of later steps
   held up.
2. **What these blockers have in common** (AI only, when several share a cause).
3. **One card per blocker**, biggest impact first, at most 15:
   - rank, kind (Blocked / Waiting / Needs a helper), High or Critical priority, task or step code and title;
   - where it sits: goal › step;
   - facts: days stuck, times blocked (when more than once), later steps held up, "Goal date at risk", "Overdue";
   - what blocks it: the blocker reason, or the linked obstacle's title;
   - what is missing and who can help (D-28-6, D-28-7);
   - AI suggestion (when available): likely root cause, first move, who to ask;
   - actions: **Break into smaller tasks** (tasks only), **Record as an obstacle** or **Open the obstacle**,
     **Find a helper** (only when no helper is linked), **Edit**.
4. When nothing is stuck: "Nothing is blocked or waiting in this dream. Keep going."
5. A thumbs up or down rating when the AI half ran (PB-FR-34).

---

## 3. Data and calculations

### 3.1 Data

No new tables and no migration. The analysis reads existing data: goals, steps, tasks, obstacles, partners and the
task status history (V94). One repository query is added: whether a partner is linked to a task.

### 3.2 Calculations

| Item | Rule |
| --- | --- |
| Scope | Goals of the dream that are not Completed or archived; their steps that are not Completed or archived; those steps' tasks that are not archived |
| Days stuck | For a Blocked task, the total time it has spent Blocked; for a Waiting task, the total time Waiting (from the status history, open interval to now). Steps have no status history, so no days are shown for steps (Q-28-1) |
| Times blocked | The task's existing `blockedCount` (BL-FR-28) |
| Later steps held up | Unfinished steps in the same goal with a higher sequence number. Steps without a sequence number are not counted (Q-28-2). The summary counts each held-up step once |
| Goal date at risk | The goal has a target date no more than 14 days away, or already past |
| Overdue | Due date (task) or target date (step) before today (BR-9) |
| Helper in place | An open obstacle on the item or its step, or a partner linked to the task or its step |
| Impact score | Kind (Blocked 6, Waiting 3, gap 2) + 2 × priority (Critical 4, High 3, Medium 2, Low 1) + 2 × later steps held up (at most 5) + 4 if the goal date is at risk + 3 if overdue + 1 per 5 days stuck (at most 6) + 1 per repeat block (at most 3) + 2 if no help is in place. Ties: lower id first (Q-28-3) |

---

## 4. Business rules

| ID | Rule |
| --- | --- |
| BR-28-1 | The analysis reads only the signed-in user's records. Another user's dream answers 404 |
| BR-28-2 | The rule half always answers, whether or not AI is enabled, consented, within limits, or available |
| BR-28-3 | At most 15 blockers are listed; the summary counts cover the whole dream |
| BR-28-4 | At most the top 8 blockers are sent to AI (Q-28-4). The request contains the dream description the plan builders already use and the blocker lines; never partner names or contact details |
| BR-28-5 | AI answers are matched to blockers by their key (TASK-id or STEP-id). Any answer for a key not in the list is dropped. An AI obstacle type is used only when the rules found none, and only when it is a valid type |
| BR-28-6 | Nothing is saved. Every action goes through the existing page or dialog and its rules (BR-6, BL-FR-24 to BL-FR-29, D-17-1) |
| BR-28-7 | AI text is limited in length (root cause and first move 300 characters, helper 200, pattern 400) |

---

## 5. Pages and components

- **Vision Map plan assistant** (`AiPlanAssistant`): the Analyse blockers button and the result panel.
- **Blocker analysis panel** (`BlockerAnalysisResult`): summary chips, pattern, blocker cards and actions. Reuses the
  break-down dialog (`BreakDownDialog`), the obstacle form's `create=obstacle&taskId&stepId&title` link, the partner
  form's `create=partner&relatedGoalId&supportType` link, and the map's `?edit=task-id` / `?edit=step-id` link.
- All new text has Khmer drafts (`km-review-v28.csv`).

---

## 6. Coaching prompts (original wording)

| Where | Text |
| --- | --- |
| Above the cards | "Biggest impact first. Clear the top one to free the most work." |
| Blocked step with no reason | "No reason written yet. Say what is missing so you can find the right help." |
| AI off | "Showing the built-in analysis only. Turn on AI for root causes and first moves." |
| AI instruction | Act as a calm, practical coach; name what is really missing, a root cause without blame, one first move that could start within a day, and the kind of helper as a role; build on what the participant wrote; do not invent blockers |

---

## Functional requirements

| ID | Requirement | Priority | Status |
| --- | --- | --- | --- |
| BA-FR-01 | Analyse blockers button in the Vision Map plan assistant, available without AI | Must | Built |
| BA-FR-02 | Find Blocked and Waiting tasks and steps, and steps with a gap that needs a helper (D-28-2) | Must | Built |
| BA-FR-03 | For each blocker: days stuck, times blocked, later steps held up, goal date at risk, overdue, reason | Must | Built |
| BA-FR-04 | What is missing and who can help, from obstacles and gap kind (D-28-6, D-28-7) | Must | Built |
| BA-FR-05 | Rank by impact (§3.2); list at most 15; summary counts for the whole dream | Must | Built |
| BA-FR-06 | Actions: break down (tasks), record or open obstacle, find a helper, edit (D-28-5) | Must | Built |
| BA-FR-07 | AI root cause, first move and helper per blocker, and the shared pattern, under consent, limits and the session limit (D-28-3) | Must | Built |
| BA-FR-08 | AI obstacle type only when the rules found none, labelled "AI guess" | Should | Built |
| BA-FR-09 | Rate the AI answer (PB-FR-34); the new feature appears in plan metrics | Should | Built |
| BA-FR-10 | Empty state when nothing is stuck | Must | Built |

## Non-functional requirements

| ID | Requirement |
| --- | --- |
| BA-NFR-01 | User scoping on the endpoint; another user's dream answers 404 |
| BA-NFR-02 | The rule half answers in under 1 s on the server for a dream with 50 steps and 500 tasks (one status-history query per step) |
| BA-NFR-03 | WCAG 2.2 AA in every interface style, mode and language; each card has an accessible name ("Blocker 1: title") |
| BA-NFR-04 | Khmer drafts for all new text in `km-review-v28.csv`; no clipping at 360px |
| BA-NFR-05 | Backend and frontend tests cover ranking without AI, the empty dream, the AI merge (unknown keys dropped), ownership, the card facts and links, and the break-down action |

## Data and API changes

- No migration.
- New endpoint `POST /api/ai/plan/dreams/{dreamId}/blocker-analysis?language=en|km` returning
  `BlockerAnalysis { blockers[], blockedCount, waitingCount, gapCount, heldUpSteps, pattern, aiChecked, requestId }`.
  Each `BlockerItem` carries `itemType`, `itemId`, `code`, `title`, `kind`, `goalId`, `goalTitle`, `stepId`,
  `stepTitle`, `reason`, `priority`, `dueDate`, `overdue`, `daysStuck`, `timesBlocked`, `heldUpSteps`, `goalAtRisk`,
  `obstacleType`, `obstacleTypeGuessed`, `obstacleId`, `hasHelper`, `score`, `rootCause`, `firstMove`, `helper`.
- New AI feature name `BLOCKER_ANALYSIS`, added to the plan features (rating, metrics, plan model choice).
- `PartnerRepository.existsByUser_IdAndRelatedTask_IdAndArchivedFalse`.

## Implementation phases

| Phase | Content | Status |
| --- | --- | --- |
| 1 | Backend: `BlockerAnalysisService`, endpoint, response and AI draft records, feature registration; `BlockerAnalysisFlowTests` | Built 24 Nov 2026 |
| 2 | Frontend: types, `analyseBlockers` API, `BlockerAnalysisResult` panel, assistant button, tasks passed from the page; Khmer drafts; tests and contract snapshot | Built 24 Nov 2026 |
| 3 | `UAT_V28.md`, Khmer review of `km-review-v28.csv`, user guide section, CHANGELOG | CHANGELOG and Khmer drafts done; UAT, review and user guide pending |

## Acceptance criteria

| ID | Criterion |
| --- | --- |
| BAC-01 | With AI off, Analyse blockers lists every Blocked and Waiting task and step and every gap that needs a helper in the dream, with a note that only the built-in analysis ran |
| BAC-02 | A Critical Blocked task that holds up two later steps is listed above a Low Waiting task in the same step |
| BAC-03 | Each card shows the facts in §2.2 that apply, and the "who can help" role for its missing type |
| BAC-04 | Record as an obstacle opens the obstacle form with the task, step and reason filled in; Find a helper opens the partner form with the goal and support type filled in; Break into smaller tasks opens the break-down dialog |
| BAC-05 | With AI on and consent given, each of the top blockers shows a root cause, a first move and who to ask; an AI answer for an item not in the plan is never shown |
| BAC-06 | A dream with nothing stuck shows the empty message |
| BAC-07 | Another user's dream answers 404 |
| BAC-08 | Nothing in the plan changes until the user saves through one of the linked pages or dialogs |
| BAC-09 | Frontend and backend builds and tests pass |

## Open questions

| ID | Question | Proposed answer |
| --- | --- | --- |
| Q-28-1 | Days stuck is the **total** time a task has spent Blocked (or Waiting), across every time it was blocked. Should it be only the **current** stretch? | Keep the total, and show "Blocked N times" beside it, so a task that keeps getting stuck stays near the top. Revisit after UAT |
| Q-28-2 | Later steps held up counts only steps with a sequence number. Should undated or unnumbered steps count too? | No: without an order the app cannot say a step waits on another. Steps added through the plan assistant are numbered; others can be numbered in the step form |
| Q-28-3 | Are the impact score weights (§3.2) right? | Use them for UAT; the product owner may re-weight after seeing real plans. The score is never shown, so changing it does not confuse users |
| Q-28-4 | Send only the top 8 blockers to AI? | Yes, to keep the request small and within the monthly cap; the rule half still lists up to 15 |
| Q-28-5 | Should the same analysis appear on the Dashboard or Today page, or for a coach? | Not in V28. If wanted, a later BRD can reuse the endpoint; a coach view would need the D-6 read rule |

## Sign-off

| Role | Name | Date | Signature |
| --- | --- | --- | --- |
| Sponsor | | | |
| Product owner | | | |
| Design reviewer | | | |
| Khmer reviewer | | | |
