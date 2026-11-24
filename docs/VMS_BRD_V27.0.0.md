# BRD – Streamline review (the five-step method)

Nov 24, 2026

## Document control

This is draft 27.0.3. It keeps every requirement in BRD 26.0.2 and the earlier BRDs. It adds a **Streamline review**:
a guided, ordered review that trims an existing goal or step before the user speeds it up or automates it. The order
of the five stages follows the five-step method Elon Musk has described in public (Starbase tours, interviews, and
Walter Isaacson's 2023 biography). The wording, questions, screens and rules in this document are original to VMS.

| Item | Value |
| --- | --- |
| Document | Business Requirements Document (BRD) – Streamline review addendum |
| Version | 27.0.3 – Draft |
| Parent documents | CLAUDE.md core hierarchy and business rules (BR-1 to BR-14), BRD 17 (D-17-1 obstacles anchored to steps and tasks; D-17-2 dreams and goals describe outcomes only), FR-16 (reviews), BRD 9 (D-6 coach read access; AI with consent and the monthly cap), BRD 23 (D-23-3 admins never see plan data), BRD 25 (dashboard zones), BRD 26 (interface styles) |
| Business owner | To be confirmed |
| Product owner | To be confirmed |
| Approvers | Sponsor, Product owner, Design reviewer, Khmer reviewer |

| Version | Change | Author |
| --- | --- | --- |
| 27.0.3 | Q-27-6 revised by the product owner: AI suggestions are required (SR-FR-14 Could → Must), no longer dropped for budget reasons. At run time they still respect consent, the daily limit and the monthly cap (D-27-9) | BA |
| 27.0.2 | Q-27-1, Q-27-3, Q-27-6 and Q-27-7 answered by the product owner (24 Nov 2026), all as proposed: goals and steps only; delete archives immediately with undo and Restore; AI suggestions last, after an AI budget check; brought-back items as a count. All open questions are now answered | BA |
| 27.0.1 | Q-27-2, Q-27-4 and Q-27-5 answered by the product owner (24 Nov 2026), all as proposed: hard lock on stage order with bulk marking in stage 1; simple repeating tasks in scope; no Musk name in the app (credit kept in the BRD and the user guide) | BA |
| 27.0.0 | First draft, from the product owner's question of 24 Nov 2026 ("does VMS have Musk's five steps?"). The fit analysis comes from the code at commit `eb23ebb` | BA |

---

## Executive summary

VMS helps a user **build** a plan (Vision Area → Dream → Goal → Step → Task) and **track** it. It does not help the
user **trim** a plan. A plan only ever grows: goals gain steps, steps gain tasks, and nothing asks whether a step
should exist at all.

The five-step method says: work in this order, and never skip ahead.

1. **Question the requirement.** Every requirement has a named person who asked for it.
2. **Delete.** Remove every part you can. If you never add anything back, you did not cut enough.
3. **Simplify.** Only what survived steps 1 and 2.
4. **Speed up.** Shorten the time from start to done, only once 1–3 are done.
5. **Automate.** Last, and only for what is left.

VMS already has fragments of each stage, but they are scattered and unordered (§1). V27 adds one guided **Streamline
review** that the user runs on a **goal** (its steps and tasks) or on a **single step** (its tasks). It walks the five
stages in order, records each decision, and measures the result: what was cut, what came back, how many steps and
hours remain, and how long tasks take from start to done.

Two small additions to the data make this measurable: a **start time** on tasks and a **status history**, so the app
can show cycle time. Nothing is ever deleted for good: "delete" means archive (BR-12), and an archived item can be
brought back, which the review counts as "added back".

---

## Decisions recorded by this BRD

| ID | Decision |
| --- | --- |
| D-27-1 | One new guided flow, the **Streamline review**, rather than five separate features. The order is the point of the method, so the flow enforces it (D-27-3) |
| D-27-2 | A review is anchored (Q-27-1 answered) to a **goal** (it covers the goal's steps and their tasks) or to a **single step** (its tasks). Not to a dream or vision area: dreams and goals describe outcomes (D-17-2), and the method works on the work that serves them. A dream-level question already exists in the monthly review |
| D-27-3 | Stages unlock in order (**hard lock**, Q-27-2 answered). A stage can be marked **Not needed** only with a written reason, which is kept. Stage 4 (Speed up) and stage 5 (Automate) stay locked until stages 1–3 are done or marked Not needed. Stage 1 supports **bulk marking**, so a large goal can be questioned without answering 40 items one by one |
| D-27-4 | "Delete" archives the step or task (BR-12). Restoring it, during the review or within 90 days after, is recorded as **added back**. Deletion takes effect **immediately** (Q-27-3 answered): each delete shows an undo message, and stage 2 lists deleted items with a **Restore** button |
| D-27-5 | The "add back at least 10%" idea is a **hint, not a rule**. When a completed review deleted items but added none back, the summary says: "You brought nothing back. You may be able to cut more next time." No percentage is ever required Brought-back items are shown as a **count** ("1 of 3 brought back"), never a percentage (Q-27-7 answered) |
| D-27-6 | Every step and task in scope gets a **requirement owner**: the named person or source that asked for it (yourself, a partner, a mentor, a coach, an organisation, or "Not sure"). "Not sure" is allowed, but is flagged as the first thing to question |
| D-27-7 | Cycle time is measured from a task's first move to **In progress** (new `startedAt`) to **Completed** (`completedAt`, which exists). Waiting and Blocked time come from a new **status history**. Tasks completed before V27 have no start time and are left out of cycle-time figures, not guessed |
| D-27-8 | Automation means three things VMS can do or record: a **repeating task** (VMS creates the next one; in scope for V27 in a simple form, Q-27-4 answered), a **reminder** (the existing email reminders), or **delegate or automate outside VMS** (recorded, optionally linked to a partner, reusing the Strengths and Gaps option "Delegate or automate"). VMS does not run external automations |
| D-27-9 | AI suggestions are a **required** part of V27 (Q-27-6, revised 24 Nov 2026). They follow the V9 rules: the user's AI consent, the daily limit and the monthly cap. AI may **suggest** questions, deletions, simplifications and automation candidates; it never changes, archives or creates anything without the user's approval. The feature is always built; it is only *unavailable* to a user who has not given AI consent, or while the daily limit or monthly cap is reached. In those cases the review works fully without AI and shows why the suggestions are missing |
| D-27-10 | Coaches see a participant's Streamline reviews read-only, under the existing V9 rule (D-6). Admins and user managers never see them (D-23-3) |
| D-27-11 | The interface does not use Elon Musk's name (Q-27-5 answered). In-app help says the order "follows a five-step method popularised in engineering and manufacturing". Credit is kept in this BRD and in the user guide's background note: "This order follows a five-step method popularised by Elon Musk for engineering and manufacturing." |

---

## 1. Fit analysis: what VMS has today

| Stage | What exists today | Gap |
| --- | --- | --- |
| 1. Question the requirement | Dream coaching asks what exactly, why, what success looks like and when. Every task has a required **owner** | Goals, steps and tasks are never questioned. "Owner" means who does the task, not who asked for it. No requirement source is recorded |
| 2. Delete | Archive (soft delete) on every record. The monthly review asks "Which dreams should be revised up, paused, or removed?". The energy nudge asks "what could you drop or move?" | Only dreams are prompted, monthly. Nothing prompts cutting goals, steps or tasks. Nothing records what was cut or brought back |
| 3. Simplify | A **complex** step prompts the user to break it into tasks | Breaking down is decomposition, not simplification. Nothing merges steps, removes hand-offs or reduces scope, and nothing checks that cutting came first |
| 4. Speed up | Estimated and actual hours, progress logs, overdue and blocked detection, Next up | No start time on tasks and no status history, so no cycle time or waiting time. Nothing asks how to shorten the work |
| 5. Automate | Progress rolls up automatically; email reminders; AI plan helpers; Excel import; "Delegate or automate" in Strengths and Gaps; habits | No repeating tasks. Nothing asks whether a repeated task should be automated, or makes the user trim it first |

---

## 2. The Streamline review

### 2.1 Where it starts

- A **Streamline** button on a goal and on a step (Goals page, Steps page, Vision Map tree, goal and step detail).
- A suggestion card when the app sees a likely candidate (§6.2): "This goal has 9 steps and 3 overdue tasks. Run a
  Streamline review?" The user can dismiss it for 30 days.
- The monthly review gains one question: "Which goal or step should you streamline this month?", with a link that
  starts a review on the chosen item.

### 2.2 The flow

One page per review with a five-stage stepper. Each stage shows: what to do, the items in scope, the decisions made,
and **Done** / **Not needed (give a reason)**. The review can be left and resumed. Only one open review per goal or
step at a time.

| Stage | The user does | Recorded |
| --- | --- | --- |
| 1. Question | For each step and task in scope: who asked for it (requirement owner), why it is needed (one line), and a verdict: **Needed**, **Not sure**, **Not needed**. **Bulk marking:** select several items (or "all tasks under this step") and set the source, owner and verdict for all of them at once; each item can still be changed afterwards | Owner, source type, reason, verdict |
| 2. Delete | Every item marked Not needed or Not sure is listed first. For each item: **Delete** (archive), **Keep** (with a reason), or **Merge into** another item. Any item can be deleted, not only the flagged ones | Decision per item, merge target, counts deleted and kept |
| 3. Simplify | For what survives: merge steps that do the same thing, reduce scope (edit the title, description or success criteria), remove a hand-off (change owner), lower the estimate, mark a complex step simple. Before-and-after counts are shown live | Changes per item; step count, task count and estimated hours before and after |
| 4. Speed up | The current cycle time (median, from task history), the slowest tasks, and the time spent Waiting and Blocked. The user picks levers per item (§2.3) and sets a target cycle time for the goal or step | Baseline cycle time, levers chosen, target |
| 5. Automate | Candidates: tasks that repeat (same title pattern, linked habits, monthly chores), tasks with low energy and high repetition. For each: **Repeat** (VMS creates the next one), **Remind**, **Delegate or automate outside VMS** (optional partner link), or **Leave manual** | Decision per candidate; repeating rules created |

When stage 5 is done, the review is **Completed** and the summary is shown (§2.4).

### 2.3 Speed-up levers (stage 4)

Original wording, chosen from a list, more than one allowed:

- **Remove a wait:** ask the partner for a date; follow up sooner (opens Communication Builder).
- **Unblock first:** the blocked task's obstacle becomes the next action.
- **Do in parallel:** tasks that do not depend on each other get the same start date.
- **Batch:** similar small tasks are grouped into one sitting.
- **Shorten the loop:** get feedback earlier (a draft review with the mentor before the full piece).
- **Set a closer date:** bring the due date forward.

### 2.4 Summary

- **Cut:** items deleted, items kept, items merged. **Added back:** items restored since (updates for 90 days).
- **Simpler:** steps, tasks and estimated hours, before → after.
- **Faster:** baseline median cycle time → target, then actual once tasks complete (updates over time).
- **Automated:** repeating rules, reminders, delegated items.
- **Hint** (D-27-5) when nothing was added back.
- **Export** as part of the Excel workbook (sheet "Streamline").

---

## 3. Data model

### 3.1 New tables

**`streamline_reviews`**

| Field | Type | Notes |
| --- | --- | --- |
| id, code | | `SR-001` |
| user_id | FK app_users | owner of the review (BR-13) |
| goal_id / step_id | FK, exactly one set | the anchor (D-27-2) |
| status | `OPEN`, `COMPLETED`, `ABANDONED` | |
| current_stage | 1–5 | |
| stage_n_state (×5) | `PENDING`, `DONE`, `NOT_NEEDED` | |
| stage_n_reason (×5) | text | required when NOT_NEEDED |
| baseline_steps, baseline_tasks, baseline_hours | numbers | captured when the review starts |
| result_steps, result_tasks, result_hours | numbers | captured at completion |
| baseline_cycle_days, target_cycle_days | decimal | stage 4 |
| started_at, completed_at, created_at, updated_at | | |

**`streamline_items`** (one row per step or task in scope)

| Field | Type | Notes |
| --- | --- | --- |
| id, review_id | | |
| step_id / task_id | exactly one | |
| requirement_owner | text, 120 | a name; "Me" allowed |
| requirement_source | `SELF`, `PARTNER`, `MENTOR`, `COACH`, `ORGANISATION`, `NOT_SURE` | D-27-6 |
| partner_id | FK, optional | when the source is a partner |
| why_needed | text, 300 | |
| verdict | `NEEDED`, `NOT_SURE`, `NOT_NEEDED` | stage 1 |
| decision | `KEEP`, `DELETE`, `MERGE` | stage 2 |
| merge_into_step_id / merge_into_task_id | optional | |
| keep_reason | text | required for KEEP when the verdict was NOT_NEEDED |
| simplified | boolean, plus a short note | stage 3 |
| speed_levers | set of lever codes | stage 4 |
| automation | `REPEAT`, `REMIND`, `DELEGATE`, `MANUAL` | stage 5 |
| archived_at, restored_at | | D-27-4 |

**`task_status_changes`**: id, user_id, task_id, from_status, to_status, changed_at. Written on every status change
(the existing PATCH and PUT paths, the kanban drag, bulk updates).

**`task_repeat_rules`**: id, user_id, source_task_id, step_id, cadence (`DAILY`, `WEEKLY`, `MONTHLY`), day, next_due,
active. A daily job creates the next task from the rule (title, owner, priority, estimate copied; status Not started).
The next task is created only once the current one is due (Q-27-4). Out of scope for V27: custom schedules ("every
second Tuesday") and "repeat after completion".

### 3.2 Changed tables

- `task_items.started_at` (timestamp, null): set the first time the status becomes In progress; never cleared.
- Migration `V94__streamline_review.sql` creates the above. No backfill: `started_at` stays null for older tasks
  (D-27-7).

### 3.3 Calculations

- **Task cycle time** = `completed_at − started_at`, in days to one decimal place. **Waiting time** and **Blocked
  time** = the sum of the intervals in those statuses, from `task_status_changes`.
- **Step or goal cycle time** = the median of its tasks completed in the last 90 days. Shown only with at least 3
  tasks; otherwise "Not enough finished tasks yet".
- **Added back** = items with `restored_at` set within 90 days of `archived_at`. The add-back ratio is shown as a
  count ("2 of 14 brought back"), not as a pass or fail.

---

## 4. Business rules

| ID | Rule |
| --- | --- |
| BR-27-1 | A Streamline review belongs to one user and covers only that user's records (BR-13, BR-14) |
| BR-27-2 | Exactly one anchor: a goal or a step. One open review per anchor |
| BR-27-3 | Stages complete in order (D-27-3). Not needed requires a reason of at least 10 characters |
| BR-27-4 | Stage 1 is done when every item in scope has a verdict and a requirement source. A requirement owner is required unless the source is NOT_SURE |
| BR-27-5 | Delete archives (BR-12). Deleting a step archives its tasks too; restoring the step restores the tasks archived with it |
| BR-27-6 | Merge moves the source item's open tasks (for a step) or its notes and hours (for a task) into the target, then archives the source. The target must be in scope and not itself deleted |
| BR-27-7 | Keeping an item marked Not needed requires a keep reason |
| BR-27-8 | A goal or step may not be left with zero open steps or tasks by a review unless the user confirms "This goal (or step) is finished or no longer needed", which then follows the existing completion or archive rules (BR-8) |
| BR-27-9 | Speed-up figures use only tasks with both `started_at` and `completed_at` (D-27-7) |
| BR-27-10 | A repeat rule needs a cadence and an active parent step. Archiving the step stops the rule. The job never creates a task in the past and never creates two for the same date |
| BR-27-11 | AI suggestions are proposals; each needs the user's approval before anything changes (D-27-9) |
| BR-27-12 | Abandoning a review keeps all decisions already applied (archives, edits) and records the stage it stopped at |

---

## 5. Pages and components

- **Streamline review page** `/streamline/:id`: stepper, stage panels, summary. Uses the BRD 26 surface tokens; the
  stepper is navigation, the item lists are dense solid surfaces.
- **Streamline list** `/streamline`: open and completed reviews, with filters (vision area, dream, status) and the
  summary numbers.
- **Goal and step pages:** a Streamline button; the last review's summary chip ("Streamlined 12 Nov: −5 steps").
- **Dashboard (Insights zone):** a card "Streamline" showing open reviews, items cut this quarter, items added back,
  and the median cycle-time trend (BRD 25 chart rules, with View as table).
- **Monthly review:** the extra question and link (§2.1).
- **Coach view:** the participant's reviews, read-only (D-27-10).
- **Excel export:** a "Streamline" sheet (one row per item, with decisions) and cycle-time columns on the Tasks sheet.

All new text has Khmer drafts (`km-review-v27.csv`) and must not clip at 360px in any interface style.

---

## 6. Coaching prompts and suggestions

### 6.1 Prompts (original wording)

| Stage | Prompt |
| --- | --- |
| 1 | "Who asked for this? If the answer is 'I'm not sure', start here." · "Would anyone notice if this step disappeared?" |
| 2 | "Cut first, decide later. You can bring anything back." · "If it's 'nice to have', it can go." |
| 3 | "Can two of these become one?" · "Does this need a hand-off, or can one person finish it?" |
| 4 | "Where does work sit and wait?" · "What would make this finish a week sooner?" |
| 5 | "You do this every week. Should the app create it for you?" · "Is this worth your time, or someone else's?" |

### 6.2 When the app suggests a review

Any of: a goal with more than 7 open steps; a step with more than 10 open tasks; more than 30% of a goal's tasks
overdue; a step whose median cycle time doubled over the last 90 days; a task completed manually with the same title
at least 4 times in 60 days (an automation candidate). At most one suggestion card at a time; dismissible for 30 days.

---

## Functional requirements

| ID | Requirement | Priority |
| --- | --- | --- |
| SR-FR-01 | Start a Streamline review on a goal or a step; one open review per anchor | Must |
| SR-FR-02 | Five ordered stages with Done / Not needed (reason); later stages locked until earlier ones are done | Must |
| SR-FR-03 | Stage 1: requirement owner, source, why needed and verdict per item, with bulk marking of several items (or all tasks under a step) at once | Must |
| SR-FR-04 | Stage 2: delete (archive), keep (reason), merge, applied immediately, with an undo message after each delete and a Restore button for every deleted item | Must |
| SR-FR-05 | Stage 3: merge, edit scope, change owner, lower estimate, mark simple; live before/after counts | Must |
| SR-FR-06 | `started_at` on tasks and a task status history, written on every status change path | Must |
| SR-FR-07 | Stage 4: baseline cycle time, waiting and blocked time, levers, target | Must |
| SR-FR-08 | Stage 5: automation candidates; repeat, remind, delegate or automate outside VMS, leave manual | Must |
| SR-FR-09 | Repeating task rules (daily, weekly, monthly) and the daily job that creates the next task once the current one is due; may move to phase 6 if time is tight | Should |
| SR-FR-10 | Summary with cut / added back / simpler / faster / automated, updated for 90 days | Must |
| SR-FR-11 | Streamline list page and the goal and step summary chip | Must |
| SR-FR-12 | Suggestion cards (§6.2) and the monthly review question | Should |
| SR-FR-13 | Dashboard Streamline card | Should |
| SR-FR-14 | AI suggestions for each stage, with approval per suggestion; respects AI consent, the daily limit and the monthly cap, and the review carries on without AI when they apply (D-27-9) | Must |
| SR-FR-15 | Coach read-only view | Should |
| SR-FR-16 | Excel export sheet and cycle-time columns | Should |

## Non-functional requirements

| ID | Requirement |
| --- | --- |
| SR-NFR-01 | User scoping on every endpoint; another user's review answers 404 and is security-logged (as today) |
| SR-NFR-02 | WCAG 2.2 AA in every interface style, mode and language; the stepper is keyboard operable and announces the stage and its lock state |
| SR-NFR-03 | A review of a goal with 50 steps and 500 tasks loads in under 1.5 s on the server |
| SR-NFR-04 | The repeat-rule job is idempotent and safe to run twice |
| SR-NFR-05 | Khmer drafts for all new text in `km-review-v27.csv`; no clipping at 360px |
| SR-NFR-06 | Backend and frontend tests cover stage order, delete and restore, merge, cycle time and the repeat job; e2e covers one full review |

## Data and API changes

- Migration `V94__streamline_review.sql` (§3).
- New endpoints under `/api/streamline-reviews`: `POST` (start), `GET` (list, paginated), `GET /{id}`,
  `PUT /{id}/items/{itemId}` (record a decision), `POST /{id}/stages/{n}/complete`, `POST /{id}/stages/{n}/not-needed`,
  `POST /{id}/abandon`, `GET /{id}/summary`.
- New endpoints under `/api/task-repeat-rules`: `GET`, `POST`, `PUT /{id}`, `DELETE /{id}` (deactivate).
- `GET /api/tasks/{id}/history` (status history); task responses gain `startedAt` and `cycleTimeDays`.
- `GET /api/dashboard` gains `streamline` (open reviews, cut and added back this quarter, cycle-time trend).
- Excel export gains the Streamline sheet.

## Implementation phases

| Phase | Content | Behaviour change |
| --- | --- | --- |
| 1 | **Prototype** (front end only, sample data): the review page with all five stages and the summary, for sign-off | None |
| 2 | Migration V94; `started_at` and status history written on every status path; cycle-time calculation | Cycle time starts collecting |
| 3 | Streamline API: start, items, stages, delete/restore/merge, summary; tests for every BR-27 rule | New API |
| 4 | Review page, list page, goal and step entry points, summary chip | Feature live |
| 5 | Stage 4 metrics and levers; stage 5 candidates; repeating tasks and the daily job | Speed and automate live |
| 6 | AI suggestions for all five stages (required, D-27-9); suggestion cards, monthly review question, dashboard card, coach view, Excel sheet | AI and wider integration |
| 7 | `UAT_V27.md`, `km-review-v27.csv`, user guide section (with the D-27-11 credit note), e2e of a full review, CHANGELOG | None |

Phase 2 should ship early: cycle time needs weeks of history before stage 4 shows anything useful.

## Acceptance criteria

| ID | Criterion |
| --- | --- |
| SRC-01 | A user can run a full review on a goal and see stages unlock only in order; Not needed needs a reason; stage 1 can mark many items at once |
| SRC-02 | Every item in scope has a requirement source after stage 1; "Not sure" items are listed first in stage 2 |
| SRC-03 | Deleting archives; restoring brings the item back and counts it as added back; nothing is permanently deleted |
| SRC-04 | Merging moves open tasks into the target and archives the source |
| SRC-05 | The summary shows before → after for steps, tasks and hours, and the cut / added back counts |
| SRC-06 | Cycle time appears for tasks started after V94, with waiting and blocked time; older tasks are excluded, not guessed |
| SRC-07 | A weekly repeat rule creates exactly one task per week, never in the past, and stops when its step is archived |
| SRC-08 | A coach sees a participant's review read-only; an admin cannot see it |
| SRC-09 | The pages pass axe in all three interface styles, light and dark, English and Khmer, with no clipping at 360px |
| SRC-10 | Frontend and backend builds and tests pass |

## Open questions

| ID | Question | Proposed answer |
| --- | --- | --- |
| Q-27-1 | Anchor to goals and steps only, or also to dreams? | Goals and steps only (D-27-2). Dreams keep the monthly "revise up, pause or remove" question **Answered 24 Nov 2026: goals and steps only** |
| Q-27-2 | Hard lock on stage order, or a soft warning? | Hard lock with "Not needed (reason)" as the escape. The order is the point of the method. **Answered 24 Nov 2026: hard lock, plus bulk marking in stage 1** |
| Q-27-3 | Should "delete" take effect immediately, or only when the review is completed? | Immediately (archive), so the plan the user sees matches their decisions; undo is one click **Answered 24 Nov 2026: immediately, with Restore and an undo message after each delete** |
| Q-27-4 | Are repeating tasks in scope for V27, or only recorded as a decision? | In scope (Should): without them, "automate" in VMS would only be a note. **Answered 24 Nov 2026: in scope, simple form (daily / weekly / monthly)** |
| Q-27-5 | Name Elon Musk in the help text? | No name in the interface; the help text says "a five-step method popularised in engineering and manufacturing". The BRD keeps the attribution. **Answered 24 Nov 2026: no name in the app; credit in the BRD and the user guide** |
| Q-27-6 | Include AI suggestions in V27? | Yes, behind the existing AI consent and cap. **Answered 24 Nov 2026, revised the same day: AI suggestions must be built in V27 regardless of budget (Must). They remain subject to consent, the daily limit and the monthly cap at run time; when those apply, the review works without AI** |
| Q-27-7 | Show the add-back ratio as a percentage? | No, as a count, with the D-27-5 hint only when nothing came back **Answered 24 Nov 2026: a count, never a percentage** |

## Sign-off

| Role | Name | Date | Signature |
| --- | --- | --- | --- |
| Sponsor | | | |
| Product owner | | | |
| Design reviewer | | | |
| Khmer reviewer | | | |
