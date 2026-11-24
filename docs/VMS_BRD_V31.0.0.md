# BRD – Dream Coach guided plan with an accuracy check

Nov 24, 2026

## Document control

This is draft 31.0.0. It keeps every requirement in BRD 30.0.0 and the earlier BRDs. It turns the **Build my plan**
guide (BRD 11) into a full, start-to-finish Dream Coach that leads a participant along the whole method chain and
checks every method rule before anything is saved:

```text
Vision Area → Dream → Goal → Step → Task → Partner/Resource → Schedule → Progress → Review
```

| Item | Value |
| --- | --- |
| Document | Business Requirements Document (BRD) – Dream Coach guided plan and accuracy check |
| Version | 31.0.0 – Draft |
| Parent documents | BRD 11 (Build my plan, PB-FR-01 to PB-FR-50), BRD 9 (AI consent AI-NFR-03, data minimisation AI-NFR-04, $20 monthly cap D-3, coach read-only D-6, crisis response D-5), BRD 17 (blocker placement D-17-1, outcome-only D-17-2, six chains D-17-3, gap steps BL-FR-17 to BL-FR-22), BRD 5 (FR-51 bottom-up date cascade, BR-40), BRD 3 (FR-33 vision statement), BRD 15 (page assistant), BRD 8 (FR-77 reminders) |
| Business owner | To be confirmed |
| Product owner | To be confirmed |
| Approvers | Sponsor, Product owner, Design reviewer, Khmer reviewer |

| Version | Change | Author |
| --- | --- | --- |
| 31.0.0 | First draft, from the product owner's request of 24 Nov 2026 for an AI goal-setting assistant that guides a participant from vision area to review "with very high accuracy", with eight accuracy rules, a ten-step flow and the existing guardrails | BA |
| 31.0.0 (v1 step 2 built) | v1 step 2 implemented, 25 Nov 2026 (no migration). Build my plan's stages are now Clarify, Inner blockers, Goals, Steps (with gaps, helpers and risks), Tasks, Dates, Check and add. Clarified during build: (1) DC-FR-19/20: the inner blockers stage names chains from the participant's latest six-chain scores (the lowest of 3 or less, at most two) with the program's own practice; it uses no AI, so the fear-journal tick of DC-FR-21 stays on the helpers and risks suggestions (PB-FR-26). The 2-minute check opens the Assessment page; the draft is kept. (2) R-05: a stage not visited, or skipped, is one SHOULD line (`INNER_SKIPPED`). (3) DC-FR-35: the Helpers stage is shown inside Steps; a draft saved on the old Helpers stage opens on Steps. (4) DC-FR-42: the second fix comes from the server as `altFixAction`/`altFixValue`: `MOVE_PARENT_DATE` for a goal or step that is new in the draft, `MOVE_DREAM_DATE` for a bottom-up dream (saved to the dream at once). Saved goals and steps are never moved by the guide, and a fixed deadline offers only "bring it forward". (5) DC-FR-18 is met on Clarify: "Edit the dream here" saves the dream's title, why, success, date and priority without leaving the guide | Dev |
| 31.0.0 (v1 step 1 built) | v1 step 1 implemented, 25 Nov 2026 (no migration): the Dream Coach start page `/plan/new` with stages 1 (vision area) and 2 (dream), entry points on the Dreams page and each vision area, `POST /api/ai/plan/vision-statement` (`COACH_AREA`) and `POST /api/ai/plan/dream-intake` (`COACH_DREAM`). D-31-2 amended (see Decisions). Clarified during build: the dream is saved as an **Idea**, because activating a dream goes through the existing decision gate (FR-55, FR-69); Save my plan does not change its status. The target date in the restatement is only the participant's own date answer, never Claude's. Starting from an existing dream (DC-FR-18) and stages 3, 7, 8 and 9 come in v1 steps 2 and 3 | Dev |
| 31.0.0 (MVP built) | MVP phase implemented, 24 Nov 2026 (migration V96). Clarified during build: (1) D-31-4: SHOULD fails are counted at save (`acceptedShould`), not enforced by the server; the browser requires the ticks. (2) DC-FR-53: "I understand" is one tick per rule, not per line. (3) A third level, INFO, carries advice that needs no tick: a step with no resources (the old MISSING_RESOURCE) and every AI review issue. (4) R-06: a gap with no kind is SHOULD, not MUST, because AI step suggestions usually fill the resources text; a Knowledge, Skill or Money gap counts as helped when a draft helper, or a risk with a helper, is tied to its step. (5) R-08 / DC-FR-04: the live list runs the rules alone (`rulesOnly=true`, no AI cost); Save waits until the list matches the latest draft, and the server re-runs the rules at save, so no check hash is stored. (6) R-07 / DC-FR-61: owners are checked by role words (English) and titles such as "Dr."; prompts ask for owners in English in every language, and "ខ្ញុំ" is accepted. In the guide a removed owner stays empty; in the plan assistant's add cards it becomes Me. (7) Metrics: DCM-01, 02, 03, 06 and 07 are on the admin page; DCM-04 and DCM-05 are guaranteed by the save gate; DCM-08 needs the v1 envelope (§4.1) and waits. (8) The inner-blocker part of R-05 waits for stage 3 (v1). (9) The dream's own missing fields are SHOULD (EC-13), one line each | Dev |

Numbering: requirements use the prefix **DC-** (Dream Coach). Validation rules are **R-01 to R-08**. Decisions are
**D-31-n**. The next free migration is **V96**.

---

## Executive summary

VMS already has most of the parts of a guided Dream Coach. BRD 11 built **Build my plan** (`/dreams/:id/build`): six
stages (Clarify, Goals, Steps, Tasks, Helpers and risks, Check and add), clarifying questions, server date limits on
AI suggestions, refine and regenerate, a 14-day server draft, a one-transaction save with undo, and usage measurement.
BRD 17 decided where each kind of blocker belongs. BRD 5 added the bottom-up date cascade.

What is missing is **accuracy from start to finish**. Six gaps remain:

1. **The guide starts too late.** It needs a dream that is already saved. Choosing the vision area, confirming its
   vision statement, writing the dream and facing inner blockers all happen elsewhere, without the coach.
2. **The check is incomplete.** The draft check (`PlanDraftCheckService`) checks steps and tasks only. It does not
   check that a goal has a measure of done and a date, that a goal fits before its dream, that a task has a priority
   and status, that blockers sit in the right place, or that every gap points to a helper.
3. **The check does not stop a bad save.** Issues are advice. **Add plan** saves even when dates conflict or tasks
   lack owners, and dates the participant typed are only warned about (PB-FR-15 status).
4. **"Never invent" is a prompt rule only.** Owners must be "Me" or a role, but no code checks it (PB-FR-18 status).
   Nothing stops Claude filling a number or date that the participant never gave.
5. **Schedule and review end the guide too early.** There is no stage for the weekly schedule or the review rhythm,
   and no per-plan review rhythm is stored.
6. **Accuracy is not measured.** BRD 11 measures acceptance and cost, not how often a plan passes the method's rules
   the first time.

V31 closes these gaps:

- **Ten stages** from vision area to save (§2). The guide can start with nothing, or from an existing area or dream.
- **One rule catalogue, R-01 to R-08** (§5), run on the server, the same with or without AI. It is shown as a
  **pass/fail list** before saving. **Must** rules block saving. **Should** rules warn and need one tick to accept.
- **No guessing, enforced in code** (R-07). Claude returns a question instead of an invented detail. The server
  removes invented owners, and every field Claude filled is marked "AI suggested" until the participant confirms it.
- **Schedule and review rhythm** stored with the plan (§6).
- **Accuracy metrics** (§7), from counts only, never text.

AI suggests and the person decides. Nothing is saved until the participant presses **Save my plan** on the summary.
Every stage works without AI.

---

## Decisions recorded by this BRD

| ID | Decision |
| --- | --- |
| D-31-1 | V31 **extends** Build my plan (BRD 11). It is not a second assistant. The existing stages, draft, refine, save and undo behaviour stay. The Dream Coach launcher on the guide keeps opening the plan assistant (PB-FR-50) |
| D-31-2 | **Amended 25 Nov 2026 (product owner):** the guide can start **without a saved dream**. Stages 1 and 2 run unsaved; when the participant confirms the restated dream ("This is right"), the area (if new, or its edited vision statement) and the dream (status **Idea**) are saved through the normal endpoints, and Build my plan opens at the goals. Goals, steps, tasks and the rest are still saved only by Save my plan. If the participant undoes that save, the dream the coach saved is archived too. *Original wording, replaced:* a new area and dream are held in the draft and saved in the same transaction as the goals, steps and tasks. Reason: 15 guide endpoints, the AI session limit and the plan assistant all read the dream from the database |
| D-31-3 | The validation rules R-01 to R-08 live in **one server service** and are used by the guide's check, by **Save my plan**, and by the AI plan check. The browser shows the result. It never decides on its own |
| D-31-4 | Rules have two levels. **Must** fails block **Save my plan**; the server rejects the save with the same list. **Should** fails are allowed after the participant ticks "I understand" for each one. That tick is stored as a count only |
| D-31-5 | Inside the guide, a **date conflict is a Must fail** (R-04), including dates the participant typed. This replaces the "warned, not blocked" behaviour of PB-FR-15 **for the guide only**. Ordinary create and edit forms keep the BR-40 behaviour they have today |
| D-31-6 | **No guessing (R-07).** Claude must leave a field empty and ask a question when the participant has not given what the field needs. The server strips owners that are not "Me", an allowed role or a linked partner, and dates outside their limits. Every AI-filled field carries an "AI suggested" marker until the participant confirms or edits it |
| D-31-7 | **Inner blockers come before mapping** (D-17-3). Stage 3 uses the six-chain check and, only with AI consent and a tick for that request, the fear journal (PB-FR-26). It can be skipped. Skipping is recorded so the summary can show it. It never blocks saving |
| D-31-8 | **Partner type comes from the server's existing gap map** (`GapStepService.SUPPORT_FOR_GAP`, the same as the frontend `suggestPartnerFor`). Claude may suggest the kind of gap. It never decides the partner type |
| D-31-9 | The **review rhythm** (daily, weekly, monthly, quarterly) is stored per dream (§6). Due reviews show on the Reviews page and, for participants who turned reminders on (FR-77), in the reminder email. No new email is sent to anyone who has not turned reminders on |
| D-31-10 | Coaches keep read-only access to their active cohorts (D-6) and see no V31 controls. They see the saved plan as today, not the draft, the chat or the check |
| D-31-11 | The $20 monthly cap is **for all participants together** (D-3). The guide meets the per-plan cost target in DC-NFR-03. Rules run without AI, so a plan can always be checked and saved for free |

---

## 1. Fit analysis: what VMS has today

| Requested capability | Today (commit `d426975`) | V31 change |
| --- | --- | --- |
| Choose a vision area, confirm its vision statement | `VisionArea.visionStatement` and the area setup wizard (FR-33). Not in the guide | Stage 1 in the guide (DC-FR-10 to DC-FR-13) |
| Capture the dream, restate it | Dream form with clarity questions; guide Clarify stage asks up to 3 questions (PB-FR-08) on a saved dream | Stage 2 captures in the participant's words, then shows a restatement to confirm (DC-FR-14 to DC-FR-18) |
| Inner blockers before mapping | Six-chain check and pre-dream card (BL-FR-30, BL-FR-33); fear journal used with a tick (PB-FR-26) | Stage 3 in the guide (DC-FR-19 to DC-FR-23) |
| 1–5 SMART goals, each approved | Goal suggestions, refine, lock (PB-FR-19, PB-FR-20). No limit of 5, no measure-of-done check, no per-goal approval | 1–5 goals, each with outcome, measure and date, each approved (DC-FR-24 to DC-FR-29) |
| Steps with gaps and partners | Step suggestions with complex flag, hours, order (PB-FR-12); gap kind and gap step (BL-FR-17 to BL-FR-19); helper matching (PB-FR-23) | Gap and helper move into the Steps stage; the Helpers stage keeps only partner linking and outreach (DC-FR-30 to DC-FR-35) |
| Tasks with owner, date, priority, status | Task builder 2–8 tasks (PB-FR-14). Status not shown in the draft | Every task shows and requires all four; Blocked needs a reason (DC-FR-36 to DC-FR-40) |
| Date cascade and conflicts | FR-51 bottom-up rule on save; draft check covers step/task dates; **no goal-after-dream check in the draft** | Dates stage with every conflict and two fixes (DC-FR-41 to DC-FR-45) |
| Schedule and review rhythm | Hours per week and overloaded weeks (PB-FR-16); timeline (V16); daily reminders (FR-77). **No stored review rhythm** | Schedule stage with weekly load, timeline preview and review rhythm (DC-FR-46 to DC-FR-50) |
| Summary tree and pass/fail list | Readiness list and check issues (PB-FR-04, PB-FR-28) | Summary stage with the full tree and R-01 to R-08 as pass/fail (DC-FR-51 to DC-FR-55) |
| Save only after confirmation | One-transaction **Add plan** with undo (PB-FR-31, PB-FR-32) | **Save my plan** re-runs the rules on the server and refuses Must fails (DC-FR-56 to DC-FR-59) |
| No guessing | Prompt rule only (PB-FR-18 "not checked by code") | Code checks and "AI suggested" markers (R-07, DC-FR-60 to DC-FR-63) |
| Accuracy measurement | Acceptance, edit rate, rating, cost per finished plan (PB-FR-34 to PB-FR-36) | Rule pass rates and completeness (§7, DC-FR-64 to DC-FR-67) |
| English and Khmer | UI strings in `km.ts`; AI answers in the participant's language | All new strings and the restatement go through Khmer review; limit-word list stays English-only (BL-FR-13) |

---

## 2. User flow, screen by screen

The guide stays one page with a stepper. Stages can be visited in any order after stage 2 (PB-FR-02), but **Save my
plan** is only on stage 10. Each stage has the same layout:

- **Left:** the stage's form (the draft).
- **Right:** the Dream Coach panel (PB-FR-41), focused on the stage.
- **Bottom:** Back, Next, and a live counter of open Must and Should fails.

| # | Stage | Entry condition | What the participant does | What the coach does | Leaves the stage when |
| --- | --- | --- | --- | --- | --- |
| 1 | **Vision area** | Always | Picks an existing area or creates one; reads and confirms or edits the vision statement | Offers starter areas (FR-58) if none exist; asks one question if the statement is empty; drafts a statement only from the participant's answer | An area is chosen and its statement is confirmed or deliberately left empty (a Should fail under R-02) |
| 2 | **Dream** | Area chosen | Writes the dream in their own words; answers up to 3 clarifying questions; confirms or edits the restatement; fills why it matters, success, target date, priority, type | Asks only about missing parts (what, why, success, when, area); restates the dream in one or two sentences using only the participant's words | Restatement confirmed; title, why, success, target date and priority present |
| 3 | **Inner blockers** | Dream confirmed | Sees their latest six-chain result (or takes the 2-minute check); optionally ticks "use my fear journal for this"; reads 1–2 practices; chooses **I'm ready to plan** or **Skip for now** | Names the one or two chains that may affect this dream and offers one short practice each. Never diagnoses and never gives therapy | Ready or Skip chosen |
| 4 | **Goals** | Dream confirmed | Reviews 1–5 suggested goals; edits, locks, removes or adds; ticks **Approve** on each | Suggests 1–5 goals, each with an outcome, a measure of done and a date within the dream's date; asks a question instead when the dream lacks something a goal needs | Every new goal is approved and passes R-02 |
| 5 | **Steps** | ≥1 approved goal | Reviews steps per goal; marks complex or simple; writes what is missing (gap) and its kind | Suggests 3–10 steps per goal with order, complex flag, hours and date; for each gap, shows the helper type from the server map | Every goal has ≥1 step; every gap has a kind |
| 6 | **Tasks** | ≥1 step | Reviews tasks per complex step; sets owner, due date, priority, status; gives a reason for any Blocked task; adds a friction note to a task if useful | Suggests 2–8 tasks per complex step with all four fields; owner "Me" or a role only | Every complex step has ≥1 task; every task is complete |
| 7 | **Dates** | ≥1 item has a date | Reviews the conflict list; picks a fix for each conflict | Explains a conflict in plain words on request; proposes a realistic date only inside the allowed range | No date conflicts remain |
| 8 | **Schedule and review** | Dates stage visited | Gives hours per week; sees overloaded weeks and the timeline preview; spreads dates if needed; chooses the review rhythm | Suggests a rhythm from the plan's length (rule in DC-FR-49); never moves a date by itself | Rhythm chosen (default accepted counts) |
| 9 | **Summary and check** | Always available | Reads the plan tree and the R-01 to R-08 pass/fail list; fixes items from each line; ticks "I understand" on Should fails | Explains any failed line on request; offers **Fix with AI** for vague text only | No Must fails; every Should fail ticked |
| 10 | **Save** | Stage 9 clear | Presses **Save my plan**; then may draft outreach to new helpers (PB-FR-27) or **Undo** | Nothing. The server saves | Saved, or the server returns the fail list and the guide goes back to stage 9 |

Helpers (partner linking and outreach) stay as a panel inside stage 5 and as the post-save offer in stage 10
(PB-FR-23, PB-FR-27), so the separate Helpers stage of BRD 11 is merged into Steps.

---

## 3. Conversation design

Samples use the method's standard example (a participant who wants to become an AI-assisted public health
researcher). Text in *italics* is the participant. Every coach answer carries the "AI suggestion" label and a
"Based on …" line (PB-FR-48). Khmer versions of fixed coach lines go through the usual review file
(`docs/km-review-v31.csv`); Claude's free text is written in the participant's chosen language.

### 3.1 Stage 1 – Vision area

> **Coach:** Which part of your life or work is this dream about? You have **Career** and **Health** already, or you can add a new one.
>
> *Career.*
>
> **Coach:** Your Career vision statement is empty. In one sentence, what do you want your working life to look like in a few years?
>
> *I want my research to change how clinics in Cambodia use data.*
>
> **Coach:** Here is your sentence as a statement: "My research changes how clinics in Cambodia use data." Keep it, edit it, or leave the statement empty for now?

The coach rewrites only the participant's own sentence. It does not add places, numbers or roles they did not say.

### 3.2 Stage 2 – Dream, with restatement

> *I want to get better at research with AI.*
>
> **Coach:** Thanks. Two quick questions so the plan fits you. First: what would "better" look like when you have done it?
>
> *Writing a concept note my mentor accepts, using AI tools for the literature search.*
>
> **Coach:** And by when would you like that?
>
> *End of June next year.*
>
> **Coach:** Here is your dream as I understand it: **"Become an AI-assisted public health researcher who can write a concept note my mentor accepts, by 30 June 2027."** Is that right? You can edit any word.

If the participant answers "I don't know" to the date question, the coach does **not** pick a date. It says: "That's
fine. A date helps the plan, but you can set it later. The plan check will remind you." The target date stays empty
and R-02 shows a Must fail until a date is set.

### 3.3 Stage 3 – Inner blockers

> **Coach:** Your last chain check (12 Nov) shows **fear of failure** as the one most likely to slow this dream. A small practice: before each study session, write the smallest next action and do only that. Want to keep this as a reminder, or move on?
>
> *Move on.*
>
> **Coach:** Great. You can come back to this any time from the Assessment page.

If the participant writes something that suggests risk of harm, the existing crisis response runs (D-5): help lines
are shown and the coach follows the safeguarding policy. The guide pauses until the participant chooses to continue.

### 3.4 Stage 4 – Goals

> **Coach:** Here are four goals for your dream. Each says what result it delivers, how you will know it is done, and a date before 30 June 2027.
>
> 1. **Use two AI research tools confidently.** Done when: I have finished one literature search with each tool. By 31 Jan 2027.
> 2. **Build a literature review workflow.** Done when: a written workflow is used for one review. By 15 Mar 2027.
> 3. **Prepare one concept note.** Done when: the full draft is ready for review. By 31 May 2027.
> 4. **Present the concept note to my mentor.** Done when: my mentor accepts the note. By 30 Jun 2027.
>
> Approve, edit or remove each one. You can also ask for fewer, smaller or bolder goals.

When the dream has no measure the coach can use, it asks instead: "What would show your mentor the note is good
enough? For example: accepted as is, or accepted with small changes."

### 3.5 Stage 5 – Steps, gaps and helpers

> **Coach:** For goal 3, "Prepare one concept note", here are seven steps in order: select topic, search literature, define research gap, write rationale, define objectives, draft methodology, review with mentor. **Search literature** looks complex (several separate actions), so I marked it complex.
>
> Which steps need something you don't have yet?
>
> *I don't know how to write a methodology.*
>
> **Coach:** I've written that as a gap on **Draft methodology**: missing **knowledge**. People who usually help with this are a **mentor or expert**. Do you want to link one of your partners, add a new helper to contact, or add a "find help" step before it?

The helper type always comes from the server map (D-31-8). The coach never names a person.

### 3.6 Stage 6 – Tasks

> **Coach:** For **Search literature** (due 20 Apr 2027), here are five tasks, all owned by **Me**, status **Not started**:
>
> | Task | Due | Priority |
> | --- | --- | --- |
> | Search PubMed | 2 Apr | High |
> | Search Google Scholar | 6 Apr | High |
> | Save 15 articles | 10 Apr | Medium |
> | Summarise key findings | 16 Apr | Medium |
> | Prepare reference table | 20 Apr | Medium |
>
> Change any owner, date or priority. If you set a task to Blocked, I'll ask what's in the way.
>
> *Set "Search PubMed" to Blocked.*
>
> **Coach:** What is in the way? (Required for a blocked task.) If it's that you keep putting it off, I can help split it into smaller tasks.

### 3.7 Stage 7 – Dates

> **Coach:** One date conflict: **Prepare reference table** is due **25 Apr**, after its step **Search literature** (20 Apr). You can bring the task forward to 20 Apr, or move the step later to 25 Apr. The step's goal is due 31 May, so either fix keeps the goal on time.

When a parent is a fixed deadline (TOP_DOWN_FIXED, BR-40), the coach offers only "bring the child forward" and says
why.

### 3.8 Stage 8 – Schedule and review

> **Coach:** With 6 hours a week, the week of 5 Apr has 9 planned hours. **Spread the dates** would move two medium-priority tasks into the following week, still before their step's date.
>
> For a plan of about seven months, a common rhythm is a **daily** two-minute check-in, a **weekly** review on a day you choose, and a **monthly** review of the dream. Which day suits your weekly review?

### 3.9 Stage 9 – Summary and check

> **Coach:** Your plan: 1 dream, 4 goals, 24 steps, 31 tasks, 2 helpers to contact.
>
> ✅ R-01 Every item is in the right place
> ✅ R-02 Every goal has an outcome, a measure and a date
> ❌ R-03 1 task has no due date: "Summarise key findings" (**must fix**)
> ✅ R-04 No date conflicts
> ✅ R-05 Blockers are in the right place
> ⚠️ R-06 1 gap has no helper yet: "Draft methodology" (**you can save, tick to accept**)
> ✅ R-07 Every suggested detail is confirmed by you
> ✅ R-08 Check complete
>
> Fix the one red line to save.

### 3.10 Stage 10 – Save

> **Coach:** Your plan is saved. You can undo this until you make another change. Want me to draft a short message to the mentor you added?

---

## 4. System prompt for Claude as the Dream Coach

This text is the stage-independent part of the system prompt. It is added after the existing shared safety and
coaching rules (`AiSafety`, `CoachingGuidelines`), which keep priority. Each stage adds a short instruction and a JSON
schema (§4.1). The server enforces every rule again (D-31-3). The prompt makes good answers likely; it is **not** the
control.

```text
You are the Dream Coach in VMS, a coached program that turns a dream into a written plan.
You suggest; the participant decides. You never save anything. Every answer is a draft they can edit.

METHOD (strict order and hierarchy)
Vision area > Dream > Goal > Step > Task > Helper/Resource > Schedule > Progress > Review.
Each dream sits in one area, each goal in one dream, each step in one goal, each task in one step.
Only refer to items by the keys given in CONTEXT. Never create an item without a parent key.

ACCURACY RULES
1. Dreams and goals describe outcomes only. Never put limits, fears or gaps in a dream or goal.
2. A goal has: a specific outcome (title), a measure of done (successCriteria), and a targetDate
   on or before its dream's date. Suggest 1 to 5 goals.
3. A step is one stage of work for one goal. Mark complex=true when it needs more than one action.
   Suggest 3 to 10 steps per goal.
4. A task is one action. It has owner, dueDate, priority (LOW|MEDIUM|HIGH|CRITICAL) and
   status (default NOT_STARTED). A BLOCKED task needs blockerReason.
5. Dates: today < task.dueDate <= step.targetDate <= goal.targetDate <= dream.targetDate.
   A step that startsAfter another is not dated before it. Never break this; if no valid date
   exists, leave the date empty and add a question.
6. Blockers: fears and doubts are inner blockers, handled before planning, never in the plan.
   Missing knowledge, skill, money, time, a decision or a partner is a gap: put it on the STEP as
   gapKind + gap text. Putting things off or "too big to start" is friction: put it on the TASK.
7. Owner is "Me", a role (for example "Mentor", "Statistician", "Funding body") or a partner key
   from CONTEXT. Never write a person's name.
8. NO GUESSING. If the participant has not given what a field needs (a number, a date, a measure,
   a place, a name), leave that field null and add a short question to "questions". Do not invent
   figures, deadlines, organisations, tools or people. Prefer one good question to a full draft.

SAFETY
No financial, legal, medical or therapy advice: say a qualified professional should help, then
continue with the plan. If the participant may be at risk of harm, stop planning and follow the
crisis rules above.

STYLE
Simple, warm, respectful. Short sentences. No praise padding. Write in LANGUAGE.
Return only JSON that matches SCHEMA. Keep every text field under its length limit.
```

### 4.1 Stage schemas (summary)

Every stage answer has the same envelope, so the server can apply R-07 the same way everywhere:

```json
{
  "items": [ { "key": "g-new-1", "parentKey": "d-1", "fields": { "...": "..." }, "reason": "≤160 chars" } ],
  "questions": [ { "field": "goal.successCriteria", "itemKey": "g-new-1", "text": "≤200 chars" } ],
  "notes": "≤300 chars, optional"
}
```

| Stage | `items[].fields` | Limits |
| --- | --- | --- |
| 1 Area | `visionStatement` (only when rewording the participant's sentence) | 1 item, ≤300 chars |
| 2 Dream | `restatement`, and only fields the participant answered: `whyImportant`, `successDefinition`, `targetDate`, `dreamType`, `priority` | 1 item; ≤3 questions per turn (PB-FR-08) |
| 3 Inner blockers | `chain`, `practice` | ≤2 items |
| 4 Goals | `title`, `successCriteria`, `targetDate`, `priority` | 1–5 items |
| 5 Steps | `title`, `sequence`, `complex`, `estimatedHours`, `targetDate`, `startsAfterKey`, `gapKind`, `gap` | 3–10 per goal |
| 6 Tasks | `title`, `owner`, `dueDate`, `priority`, `status`, `blockerReason`, `friction` | 2–8 per step |
| 7 Dates | `itemKey`, `proposedDate` | 1 per conflict asked about |
| 8 Schedule | `rhythm` | 1 item |
| 9 Summary | `itemKey`, `rewrite` (Fix with AI for vague text only) | 1 item |

---

## 5. Validation rules

### 5.1 Rule catalogue

All rules run on the server (D-31-3) over the **draft plus the dream's live plan**. Each line in the pass/fail list
names the rule, the count of items that fail, and links to each item.

| Rule | Name | Checks | Level |
| --- | --- | --- | --- |
| R-01 | Strict hierarchy | Every new dream has an area; every goal a dream; every step a goal; every task a step; every parent key exists in the draft or the participant's own live plan, is not archived and belongs to the participant | Must |
| R-02 | Clear, measurable outcomes | Dream: title, why it matters, success definition, target date, priority. Goal: title, success criteria (measure of done), target date. 1–5 new goals per session. Area vision statement present | Must, except: area statement empty = Should; more than 5 goals = Should; limit words in dream or goal text (BL-FR-13) = Should |
| R-03 | Complete tasks | Every task: title, owner, due date, priority, status. Every complex step has ≥1 non-archived task. Every goal has ≥1 step | Must |
| R-04 | Valid dates | No new item dated before today; task ≤ step; step ≤ goal; goal ≤ dream; step not before the step it starts after; every step has a date | Must (D-31-5) |
| R-05 | Blockers in the right place | No obstacle in the draft sits on a dream or goal (D-17-1); every Blocked task has a reason; friction notes sit on tasks; gap text sits on steps; inner-blocker stage done or skipped | Must, except inner-blocker stage skipped = Should |
| R-06 | Partner matching | Every step with a gap has a gap kind; every gap kind shows the server-mapped helper type; every Knowledge, Skill or Money gap has a linked partner, a new helper to contact, or a "find help" step before it (BL-FR-22) | Gap kind missing = Must; no helper or find-help step = Should |
| R-07 | No guessing | Every AI-filled field the participant has not confirmed or edited; every owner that is not "Me", an allowed role or a linked partner; every open coach question attached to a field | Unconfirmed AI field in a goal's success criteria or any date = Must; other unconfirmed AI fields = Should; invalid owner = removed by server, then R-03 |
| R-08 | Check complete | The check ran on the current version of the draft (no change since the last run) | Must |

The allowed role list for owners starts as: Me, Mentor, Expert, Advisor, Colleague, Assistant, Trainer, Sponsor,
Funding body, Team, Family member. Admins can extend it (Q-31-3).

### 5.2 Acceptance criteria (Given / When / Then)

| ID | Rule | Given | When | Then |
| --- | --- | --- | --- | --- |
| AC-31-01 | R-01 | a draft goal whose parent dream key does not exist | the check runs | R-01 fails, listing that goal, and **Save my plan** is disabled |
| AC-31-02 | R-01 | a request to save a draft step whose goal belongs to another user | the server saves | it answers 400 with R-01 failed and saves nothing |
| AC-31-03 | R-02 | a new goal with no success criteria | the check runs | R-02 fails with "Say how you'll know this goal is done" |
| AC-31-04 | R-02 | a dream with no target date | the participant opens stage 4 | the coach asks for a date and suggests no goals with dates |
| AC-31-05 | R-02 | six new goals in the draft | the check runs | R-02 shows a Should fail "More than 5 goals"; saving is allowed after the tick |
| AC-31-06 | R-03 | a new task with owner, due date and status but no priority | the check runs | R-03 fails for that task |
| AC-31-07 | R-03 | a step marked complex with no tasks | the check runs | R-03 fails with "Break this step into tasks" and the fix opens stage 6 for it |
| AC-31-08 | R-04 | a task due 25 Apr under a step due 20 Apr | the check runs | R-04 fails and offers "Bring task to 20 Apr" and "Move step to 25 Apr" |
| AC-31-09 | R-04 | the same conflict and the step's goal is TOP_DOWN_FIXED on 20 Apr | the check runs | only "Bring task to 20 Apr" is offered |
| AC-31-10 | R-04 | a goal dated after its dream | the check runs | R-04 fails with the goal listed (new check `GOAL_AFTER_DREAM`) |
| AC-31-11 | R-04 | a date the participant typed that breaks a limit | the participant presses Save my plan | the server rejects the save with R-04 (D-31-5) |
| AC-31-12 | R-05 | a draft risk attached to a goal | the check runs | R-05 fails and the fix asks the participant to pick a step |
| AC-31-13 | R-05 | a draft task with status Blocked and no reason | the participant leaves stage 6 | R-05 fails and the reason field is focused |
| AC-31-14 | R-05 | the participant chose Skip in stage 3 | the check runs | R-05 shows a Should fail "Inner blockers skipped" and saving is allowed after the tick |
| AC-31-15 | R-06 | a step with gap text and no gap kind | the check runs | R-06 fails as Must |
| AC-31-16 | R-06 | a step with gap kind Money | stage 5 shows it | the helper type shown is Financial, from the server map |
| AC-31-17 | R-06 | a Knowledge gap with no partner, no new helper and no find-help step | the check runs | R-06 shows a Should fail |
| AC-31-18 | R-07 | Claude returns a task owner "Dr. Sok" not among the participant's partners | the server receives the answer | the owner is removed, the task shows "Owner needed", and the strip is counted |
| AC-31-19 | R-07 | Claude fills a goal's success criteria | the participant has not edited or confirmed it | the field shows "AI suggested", and R-07 fails as Must |
| AC-31-20 | R-07 | the dream has no number for success | Claude is asked for goals | the answer contains a question on `goal.successCriteria` and no invented figure; the goal's field is empty |
| AC-31-21 | R-08 | the check passed, then the participant edits a task date | they look at the Save button | it is disabled until the check runs again (it re-runs by itself within 1 second of the last change) |
| AC-31-22 | All | AI is off or not consented | the participant completes the guide | every stage works as a form, the check runs the same rules, and the plan can be saved |
| AC-31-23 | Save | the check is clear | the participant presses Save my plan | area (if new), dream (if new), goals, steps, tasks, gaps, helpers, obstacles and rhythm are saved in one transaction, and Undo is offered |
| AC-31-24 | Save | any write in the save fails | the server saves | nothing is saved and the draft is unchanged |
| AC-31-25 | Privacy | a coach of the participant's cohort | they open the participant's plan | they see saved items only, with no draft, chat, check or V31 control |
| AC-31-26 | Language | the participant's language is Khmer | they open any stage | every fixed label and rule message is Khmer, and Claude answers in Khmer |

---

## 6. Data model mapping

| Stage | Creates or updates (at **Save my plan** only) | Fields | Notes |
| --- | --- | --- | --- |
| 1 | `VisionArea` (create or update) | `name`, `description`, `visionStatement`, `priority`, `status`=Active | Update only if the participant edited an existing area |
| 2 | `Dream` (create or update) | `visionArea`, `title`, `description`, `whyImportant`, `successDefinition`, `dreamType`, `priority`, `targetDate`, `status`=Active, `scheduleMode` (default BOTTOM_UP) | The restatement becomes `title` (and `description` if longer) only if confirmed |
| 3 | `FearEntry` (create, optional) | as today | Only if the participant chooses "Write it down" (BL-FR-34). Ready/Skip is stored in the draft and in the metrics count, not on the dream |
| 4 | `Goal` (create) | `dream`, `title`, `description`, `successCriteria`, `targetDate`, `priority`, `status`=NOT_STARTED, `scheduleMode`, `sortOrder` | |
| 5 | `VisionStep` (create) | `goal`, `title`, `sequenceNumber`, `complex`, `priority`, `targetDate`, `estimatedHours`, `startsAfter`, `resources` (gap text), `gapKind`, `owner`, `status`=NOT_STARTED | "Find help" steps use the BL-FR-19 logic (`coversStep`, `createdPartner`) |
| 5 | `Partner` (create or link) | `name` (typed by the participant), `role`, `supportType` (server map), `status`=To Contact, `relatedStepId` | Partner names never go to Claude (AI-NFR-04) |
| 5–6 | `Obstacle` (create, optional) | `title`, `obstacleType`, `severity`, `solution`, `relatedStep` or `relatedTask` | Goal and dream derived (BL-FR-02) |
| 6 | `TaskItem` (create) | `step`, `title`, `owner`, `dueDate`, `priority`, `status`, `blockerReason`, `estimatedHours`, `nextAction` (friction note, see Q-31-4) | |
| 7 | (updates the draft items above) | dates | No extra table |
| 8 | `AppUser` (update) | hours per week (V66 field, unchanged) | |
| 8 | **New:** `dream_review_rhythm` (V96) | `dream_id`, `daily` (bool), `weekly_day` (MON–SUN or null), `monthly_day` (1–28 or null), `quarterly` (bool), `created_at`, `updated_at` | One row per dream. The Reviews page lists reviews due from it. No `Review` rows are pre-created |
| 9–10 | **New:** columns on `plan_builder_drafts` (V96) | `dream_id` becomes nullable; `vision_area_id` nullable; `inner_blockers` (READY, SKIPPED, null); `last_check_hash` | Draft may exist before the dream is saved (D-31-2) |
| All | **New:** `plan_check_events` (V96) | `user_id`, `session_key`, `rule` (R-01…R-08), `level`, `fail_count`, `first_run` (bool), `at_save` (bool), `created_at` | Counts only, no text (§7) |
| All | `ai_usage` (existing) | stage feature names `COACH_AREA`, `COACH_DREAM`, `COACH_INNER`, plus the BRD 11 names | Cost per finished plan (PB-FR-36) keeps working |

---

## 7. Accuracy metrics

All metrics come from counts (PB-FR-35, `plan_check_events`) and saved plan data. No participant text is stored for
measurement. They show on the admin AI page beside the BRD 11 figures, per week and per language.

| ID | Metric | Formula | Target (90 days after release) |
| --- | --- | --- | --- |
| DCM-01 | First-check pass rate | sessions whose **first** check has zero Must fails ÷ sessions that ran a check | ≥ 60% |
| DCM-02 | Rule first-pass rate | per rule: first checks passing that rule ÷ first checks | each Must rule ≥ 80% |
| DCM-03 | Saved-plan validity | saved plans with zero Must fails when re-checked 1 day after save ÷ saved plans | 100% (anything less is a bug) |
| DCM-04 | Goal quality | new goals saved with outcome, success criteria and date ÷ new goals saved | 100% in the guide; reported for outside-guide goals as a baseline |
| DCM-05 | Task completeness | new tasks with owner, due date, priority and status ÷ new tasks | 100% in the guide |
| DCM-06 | Date-conflict rate | AI-suggested items with an R-04 fail before any participant edit ÷ AI-suggested dated items | ≤ 2% |
| DCM-07 | Invented-detail rate | AI fields stripped by R-07 (owners, out-of-range dates) ÷ AI-filled fields | ≤ 1% |
| DCM-08 | Ask-instead-of-guess rate | AI answers with ≥1 question when a required input was missing ÷ AI answers with a missing required input | ≥ 90% |
| DCM-09 | Edit rate on AI goals | AI goals edited before approval ÷ AI goals approved | tracked; a fall over time means better first drafts |
| DCM-10 | Blocker placement | obstacles created in the guide on a step or task ÷ obstacles created in the guide | 100% |
| DCM-11 | Gap coverage | Knowledge, Skill or Money gaps saved with a helper or find-help step ÷ such gaps | ≥ 70% |
| DCM-12 | Guide completion | sessions saved ÷ sessions started | ≥ 50% |
| DCM-13 | Plan durability | tasks from guide plans not archived and not rescheduled 30 days after save ÷ those tasks | ≥ 70% |
| DCM-14 | Cost per finished plan | AI cost of a session ÷ 1 (only saved sessions) | ≤ $0.10 (see DC-NFR-03) |

---

## 8. Edge cases

| ID | Case | How the guide handles it |
| --- | --- | --- |
| EC-01 | Participant has no vision areas | Stage 1 shows starter areas (FR-58) and a blank "New area" |
| EC-02 | Dream is too vague after 3 questions | **Suggest anyway** (PB-FR-11) is offered; any field still unknown stays empty with an R-02 or R-07 fail |
| EC-03 | Participant says "I don't know" to a date | Coach does not pick one; date stays empty; R-02/R-04 shows it; participant can save only after setting it |
| EC-04 | Dream date in the past or today | Stage 2 asks for a future date; R-04 fails until fixed |
| EC-05 | Dream is several dreams in one ("a house, a PhD and a business") | Coach suggests splitting and offers to keep one now and note the others; it does not create extra dreams without approval |
| EC-06 | Dream text is a limit ("I can't afford…") | BL-FR-13 note (English only); coach suggests the outcome wording and moving the limit to a step gap |
| EC-07 | Dream touches health, money, law or mental health | Plan help continues; any advice-type request gets "a qualified professional should help with this" and a "find a professional" step can be suggested |
| EC-08 | Signs of risk of harm in any text | Crisis response (D-5): help lines, coach alert per policy, planning paused |
| EC-09 | Participant wants more than 5 goals | Allowed by hand; R-02 Should fail suggests focusing or splitting the dream |
| EC-10 | A goal has only one step | Allowed; coach asks once whether it should be a task under another goal instead |
| EC-11 | No valid date exists (step date already before today) | Coach leaves dates empty and asks; fix offers "Move step later" if the goal allows it |
| EC-12 | Parent date is TOP_DOWN_FIXED and children cannot fit | R-04 Must fail; fixes are to shorten work, remove items or change the fixed date in the dream or goal form, with a note on why it was fixed |
| EC-13 | Existing live plan already breaks rules | Live items are shown and checked; their fails are listed under "Already in your plan" as **Should** (they were saved before V31) and never block saving new items |
| EC-14 | AI off, over the daily limit, over the monthly cap, or no consent | Form path with the program's questions (PB-FR-05); the check is unchanged; the panel says why AI is missing |
| EC-15 | Claude returns invalid JSON or times out | One retry; then "AI help is temporarily unavailable" (AI-NFR-02); the request is still counted |
| EC-16 | Claude answers in the wrong language | Server checks the script (Khmer or Latin) against the participant's language; on mismatch, one retry with a stronger instruction; else show it labelled "Answer may be in the wrong language" |
| EC-17 | Participant changes the dream after goals exist | Coach asks whether to re-check goals; goals are never replaced without approval; R-02/R-04 re-run |
| EC-18 | Draft expired (14 days) | Draft is gone as today (PB-FR-30); nothing was saved, so there is nothing to clean up |
| EC-19 | Two tabs or devices edit the same draft | Last save wins per stage as today; R-08 forces a fresh check before saving |
| EC-20 | Participant leaves at stage 9 with Must fails | Draft kept; dashboard shows "Your plan has 1 thing to fix before saving" for 14 days |
| EC-21 | Partner name typed into a task owner | If it matches a partner, it links that partner; otherwise it is kept as typed (the participant wrote it, so R-07 does not apply) |
| EC-22 | Coach opens the participant's guide URL | 403; coaches cannot open drafts (D-31-10) |

---

## Functional requirements

Priority uses MoSCoW: **M** must, **S** should, **C** could.

### A. Guide frame

| ID | Requirement | Links | Priority |
| --- | --- | --- | --- |
| DC-FR-01 | The guide shall have the ten stages in §2, in that order, on one page, with a stepper. | D-31-1 | M |
| DC-FR-02 | The guide shall be reachable from: the Dreams page (**Plan a new dream with the Dream Coach**), a vision area (**Plan a dream in this area**) and a dream (**Build my plan**, existing). | D-31-2 | M |
| DC-FR-03 | Stages 3–9 may be visited in any order once stage 2 is confirmed. **Save my plan** appears only on stage 10. | PB-FR-02 | M |
| DC-FR-04 | The footer shall show live counts of open Must and Should fails, updated within 1 second of the last change. | R-08 | M |
| DC-FR-05 | A draft may exist without a saved dream or area (D-31-2), and keeps the 14-day expiry of PB-FR-30. | PB-FR-30 | M |
| DC-FR-06 | Every stage shall work without AI as a form with the program's questions, and the check shall be the same. | AI-NFR-02, PB-FR-05 | M |

### B. Stage 1 – Vision area

| ID | Requirement | Links | Priority |
| --- | --- | --- | --- |
| DC-FR-10 | The participant shall pick an existing active area or create a new one in the draft. | | M |
| DC-FR-11 | The stage shall show the area's vision statement with **Keep**, **Edit** and **Leave empty for now**. | FR-33 | M |
| DC-FR-12 | When the statement is empty, the coach shall ask one question and may reword only the participant's answer. | R-07 | S |
| DC-FR-13 | With no areas, starter areas (FR-58) shall be offered. | FR-58 | S |

### C. Stage 2 – Dream

| ID | Requirement | Links | Priority |
| --- | --- | --- | --- |
| DC-FR-14 | The participant shall write the dream in a free-text box first, before any form field is shown. | | M |
| DC-FR-15 | The coach shall ask up to 3 clarifying questions, one at a time, only about missing parts (what, why, success, when, area), answered in place. | PB-FR-08 | M |
| DC-FR-16 | The coach shall show a **restatement** of the dream built only from the participant's words and answers, with **This is right**, **Edit** and **Try again**. | R-07 | M |
| DC-FR-17 | The confirmed restatement and answers shall fill title, why it matters, success definition, target date, priority and type in the draft, each editable. | | M |
| DC-FR-18 | Starting from an existing dream shows its saved fields to confirm; changes are saved only at stage 10. | D-31-2 | M |

### D. Stage 3 – Inner blockers

| ID | Requirement | Links | Priority |
| --- | --- | --- | --- |
| DC-FR-19 | The stage shall show the participant's latest six-chain result, or offer the 2-minute check inline if none exists or it is older than 90 days. | BL-FR-30, BL-FR-33 | M |
| DC-FR-20 | The coach shall name at most two chains that may affect this dream and offer one practice for each, from the chain practices. | BL-FR-35 | M |
| DC-FR-21 | The fear journal shall be used only with AI consent and a tick for this request. | PB-FR-26, AI-NFR-03 | M |
| DC-FR-22 | The stage shall end with **I'm ready to plan** or **Skip for now**; both continue, and the choice is stored in the draft. | D-31-7 | M |
| DC-FR-23 | The coach shall never diagnose, label the participant or give therapy; crisis signs trigger D-5. | AI-NFR-09 | M |

### E. Stage 4 – Goals

| ID | Requirement | Links | Priority |
| --- | --- | --- | --- |
| DC-FR-24 | The coach shall suggest 1 to 5 goals, each with title, success criteria, target date within the dream's date, and priority. | R-02, R-04 | M |
| DC-FR-25 | When the dream lacks what a goal's measure or date needs, the coach shall return a question instead of the missing value. | R-07 | M |
| DC-FR-26 | Each goal shall have an **Approve** tick; a goal counts toward the plan only when approved. | R-07 | M |
| DC-FR-27 | Refine, lock, replace, reorder, merge and split stay as in PB-FR-19 to PB-FR-22. | PB-FR-19 | M |
| DC-FR-28 | The goal form shall show the outcome hint (BL-FR-12) and the limit-word note (BL-FR-13). | D-17-2 | M |
| DC-FR-29 | The participant may add goals by hand; above 5 new goals, R-02 shows a Should fail. | R-02 | S |

### F. Stage 5 – Steps, gaps and helpers

| ID | Requirement | Links | Priority |
| --- | --- | --- | --- |
| DC-FR-30 | The coach shall suggest 3 to 10 steps per approved goal with sequence, complex flag, hours, date and "starts after". | PB-FR-12, PB-FR-13 | M |
| DC-FR-31 | Each step shall show the gap question and **Kind of gap**; choosing a kind shows the helper type from the server map. | BL-FR-17, BL-FR-18, D-31-8 | M |
| DC-FR-32 | The coach may suggest a gap kind from the participant's gap text; it never sets the helper type. | D-31-8 | M |
| DC-FR-33 | For each gap the participant can link a partner, add a helper to contact, or add a find-help step before it. | PB-FR-23, BL-FR-19 | M |
| DC-FR-34 | Risks suggested by the coach shall attach to a step or task only. | D-17-1, BL-FR-38 | M |
| DC-FR-35 | The separate Helpers stage of BRD 11 shall be merged into this stage. | | M |

### G. Stage 6 – Tasks

| ID | Requirement | Links | Priority |
| --- | --- | --- | --- |
| DC-FR-36 | The coach shall suggest 2 to 8 tasks per complex step with owner, due date, priority and status (default Not started). | PB-FR-14 | M |
| DC-FR-37 | Each task row shall show all four required fields and the status, editable in place. | R-03 | M |
| DC-FR-38 | Setting a task to Blocked shall require a reason in the same row. | Rule 6 | M |
| DC-FR-39 | A task may carry a friction note ("what makes this hard to start") and offers **Break into smaller tasks**. | BL-FR-24, BL-FR-25 | S |
| DC-FR-40 | Simple steps may have tasks but do not need them. | Rule 7 | M |

### H. Stage 7 – Dates

| ID | Requirement | Links | Priority |
| --- | --- | --- | --- |
| DC-FR-41 | The stage shall list every R-04 conflict across dream, goals, steps and tasks, including the new goal-after-dream check. | R-04 | M |
| DC-FR-42 | Each conflict shall offer **Bring the child forward** and, when the parent is BOTTOM_UP, **Move the parent later**. | FR-51, BR-40 | M |
| DC-FR-43 | Moving a parent later shall re-check its own parent and list any new conflict. | R-04 | M |
| DC-FR-44 | The coach may propose a date only inside the allowed range; the server clears any other. | PB-FR-15 | M |
| DC-FR-45 | A participant-typed date that breaks a limit shall be a Must fail inside the guide. | D-31-5 | M |

### I. Stage 8 – Schedule and review

| ID | Requirement | Links | Priority |
| --- | --- | --- | --- |
| DC-FR-46 | The stage shall show hours per week, overloaded weeks and **Spread the dates**. | PB-FR-16 | M |
| DC-FR-47 | The stage shall show a read-only timeline preview of the draft. | V16 | S |
| DC-FR-48 | The participant shall choose a review rhythm: daily on/off, weekly day, monthly day, quarterly on/off. | D-31-9 | M |
| DC-FR-49 | The default rhythm shall be: plan ≤ 3 months: daily + weekly; 3–12 months: daily + weekly + monthly; > 12 months: weekly + monthly + quarterly. | | S |
| DC-FR-50 | The Reviews page shall list reviews due from the rhythm, and the reminder email (only when on) shall mention a due review. | FR-77 | M |

### J. Stage 9 – Summary and check

| ID | Requirement | Links | Priority |
| --- | --- | --- | --- |
| DC-FR-51 | The stage shall show the full draft tree (area → dream → goals → steps → tasks, with helpers and gaps), live items marked as existing. | | M |
| DC-FR-52 | The stage shall show R-01 to R-08 as a pass/fail list with level, count and a link to each failing item. | D-31-3 | M |
| DC-FR-53 | Each Should fail shall have an "I understand" tick; the ticks are counted, not stored as text. | D-31-4 | M |
| DC-FR-54 | Fails in live items saved before V31 shall be shown as Should under "Already in your plan". | EC-13 | M |
| DC-FR-55 | **Fix with AI** shall be offered only for vague titles and descriptions and returns an editable change. | PB-FR-29 | S |

### K. Stage 10 – Save

| ID | Requirement | Links | Priority |
| --- | --- | --- | --- |
| DC-FR-56 | **Save my plan** shall re-run R-01 to R-08 on the server and save nothing if any Must rule fails, returning the list. | D-31-3 | M |
| DC-FR-57 | A clean save shall create or update every item in §6 in one transaction. | PB-FR-31 | M |
| DC-FR-58 | Undo shall archive everything just created and restore edited area and dream fields. | PB-FR-32 | M |
| DC-FR-59 | After saving, the guide shall offer outreach drafts for new helpers. | PB-FR-27 | S |

### L. No guessing

| ID | Requirement | Links | Priority |
| --- | --- | --- | --- |
| DC-FR-60 | Every AI answer shall use the envelope in §4.1; answers that fail the schema are retried once, then dropped. | | M |
| DC-FR-61 | The server shall remove owners that are not "Me", an allowed role or a linked partner key, and count each removal. | R-07, PB-FR-18 | M |
| DC-FR-62 | Every AI-filled field shall show an "AI suggested" marker until confirmed or edited. | R-07 | M |
| DC-FR-63 | Coach questions shall attach to the field they are about and show beside it. | R-07 | M |

### M. Measurement and admin

| ID | Requirement | Links | Priority |
| --- | --- | --- | --- |
| DC-FR-64 | Each check run shall write one `plan_check_events` row per rule (counts only). | §7 | M |
| DC-FR-65 | The admin AI page shall show DCM-01 to DCM-14 per week and per language. | PB-FR-36 | M |
| DC-FR-66 | Admins shall be able to extend the owner role list. | Q-31-3 | S |
| DC-FR-67 | Coaches shall see whether a plan was built with the guide (yes or no, and date). | PB-FR-37 | C |

---

## Non-functional requirements

| ID | Requirement |
| --- | --- |
| DC-NFR-01 | **Rule check speed.** The server check returns within 500 ms for a plan of up to 10 goals, 100 steps and 400 tasks |
| DC-NFR-02 | **AI speed.** As AI-NFR-01: first words within 3 seconds, a full stage draft within 30 seconds |
| DC-NFR-03 | **Budget.** A finished plan costs ≤ $0.10 on average (DCM-14). Means: only the stage's context is sent (PB-FR-38); the live plan is sent as keys, titles and dates only; the shared system prompt stays under 900 tokens; stage outputs are capped (§4.1); the check never calls AI. Assumption: ≤ 100 finished plans a month leaves at least half of the $20 cap for other features (Q-31-1) |
| DC-NFR-04 | **Privacy.** No partner names or contacts to Claude (AI-NFR-04); journals only with consent and a tick (AI-NFR-03); no text in measurement tables |
| DC-NFR-05 | **Scoping.** Every draft, check and save is limited to the signed-in participant (Rule 13); coaches have no access to drafts (D-31-10) |
| DC-NFR-06 | **Language.** All new labels, rule messages and coach fixed lines in English and Khmer, reviewed in `docs/km-review-v31.csv` |
| DC-NFR-07 | **Accessibility.** Pass/fail lines use text and icon, not colour alone; the stepper and lists work by keyboard; a phone at 360 px wide shows one column |
| DC-NFR-08 | **Tests.** Every acceptance criterion in §5.2 has an automated test; Claude is mocked; backend coverage stays ≥ 94% (CI rule) |

---

## Implementation phases (roadmap)

Each phase is built in full, frontend and backend. No prototype phase.

| Phase | Name | Content | Requirements |
| --- | --- | --- | --- |
| **MVP** | Accuracy check on the existing guide | One rule service R-01 to R-08 (with goal-after-dream, goal measure, task priority/status, blocker placement, gap kind); pass/fail list; Save blocked on Must; owner stripping; "AI suggested" markers; per-goal Approve; `plan_check_events`; DCM-01 to DCM-08 on the admin page; migration V96 (check events only) | DC-FR-04, DC-FR-24 to DC-FR-26, DC-FR-41, DC-FR-45, DC-FR-51 to DC-FR-58, DC-FR-60 to DC-FR-65 |
| **v1** | Full ten-stage Dream Coach | Draft without a saved dream; stages 1–3 (area, dream with restatement, inner blockers); stage 5 gaps and helpers merged; stage 7 dates with both fixes; stage 8 schedule and review rhythm (`dream_review_rhythm`, Reviews page, reminder line); new system prompt and envelopes; Khmer review; remaining metrics | DC-FR-01 to DC-FR-03, DC-FR-05, DC-FR-06, DC-FR-10 to DC-FR-23, DC-FR-27 to DC-FR-40, DC-FR-42 to DC-FR-50, DC-FR-59 |
| **v2** | Refinement | Work style and energy in suggestions (PB-FR-17); coach "built with the guide" indicator; admin role list; Khmer limit words (needs a reviewed list); language mismatch retry (EC-16); an offline set of 30 sample dreams to score prompt changes against R-01 to R-08 before release, run by hand within the budget | DC-FR-66, DC-FR-67, EC-16, Q-31-5 |

---

## Assumptions

| ID | Assumption |
| --- | --- |
| A-31-1 | The guide is used by participants (and admins on their own plans), not by coaches for others |
| A-31-2 | The existing goal field `successCriteria` is the "measure of done"; no new goal field is needed |
| A-31-3 | The existing fields cover the dream and goal; "specific outcome" is checked by presence plus the AI vague check, not by a word count |
| A-31-4 | Reviews themselves (the questions and the Review record) are unchanged; V31 only stores the rhythm and shows what is due |
| A-31-5 | The model choice and admin AI settings stay as in PB-FR-39 |

## Open questions

| ID | Question | Draft answer |
| --- | --- | --- |
| Q-31-1 | How many finished plans a month should the budget support? This sets DC-NFR-03 | 100 a month at ≤ $0.10 each |
| Q-31-2 | Should a date conflict also block saving on the ordinary forms, not just inside the guide? | No; keep BR-40 behaviour outside the guide (D-31-5) |
| Q-31-3 | Is the starting owner role list in §5.1 right, and may admins extend it? | Yes and yes (DC-FR-66, v2) |
| Q-31-4 | Where is a task's friction note stored: `nextAction`, `description`, or a new field? | Store it in `description` with the prefix "Hard to start because:"; no new column |
| Q-31-5 | May the v2 offline sample set send made-up sample dreams (no participant data) to Claude, counted in the cap? | Yes, by hand only, at most once per prompt change |
| Q-31-6 | Should "Inner blockers skipped" stay a Should fail, or show only as information? | Should fail, so the participant sees it once |

## Sign-off

| Role | Name | Date | Decision |
| --- | --- | --- | --- |
| Sponsor | | | |
| Product owner | | | |
| Design reviewer | | | |
| Khmer reviewer | | | |
