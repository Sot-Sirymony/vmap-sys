# BRD – AI Plan Builder Improvements

Nov 6, 2026

## Document control

This is draft 11.0.0. It keeps every requirement in BRD 10.0.0 (Security Hardening to the OWASP Top 10), BRD 9.0.0 (Dream Conversion Program with Claude AI Integration) and earlier versions. It improves the AI plan builder that V9 Phase 5 added (AI-FR-07 to AI-FR-11) and the vision area and dream assistant added after it. Where this BRD is silent, the earlier BRDs still apply.

V11 changes one feature area: how a participant turns a dream into a complete, realistic plan with Claude's help. Security, consent, safety and budget rules from V9 and V10 stay as they are.

| Item | Value |
| --- | --- |
| Document | Business Requirements Document (BRD) – AI plan builder addendum |
| Version | 11.0.0 – Draft |
| Parent documents | BRD 10.0.0 (6 Nov 2026), BRD 9.0.0 (2 Nov 2026) |
| Business owner | To be confirmed |
| Product owner | To be confirmed |
| Approvers | Sponsor, Product owner, AI governance lead, Lead coach |

| Version | Change | Author |
| --- | --- | --- |
| 11.0.0 | First draft: review of the current plan builder, problems found, requirements PB-FR-01 to PB-FR-40, phased plan and acceptance criteria | BA |

## Executive summary

The V9 plan builder can already suggest vision areas, dreams, SMART goals, a stretch goal, steps with owner, date and resources, check a plan, and rescue steps that keep slipping. Every suggestion is a draft the participant edits and adds; Claude never saves anything.

A review of the code on 6 Nov 2026 found that the builder works as a set of separate buttons rather than as a guide. A participant who starts with a vague dream has to find four buttons on three pages, ask again and again, and still ends with a plan that has no tasks, unlinked helpers and dates that may not fit their week.

The main problems are:

1. **No guided path.** Vision areas, dreams, goals, steps and the plan check are separate actions on different pages. Nothing leads the participant from a dream to a finished plan.
2. **The AI stops at steps.** The method goes down to tasks (`CLAUDE.md`: complex steps are broken into tasks), but there is no AI help for tasks, and the step's "complex" flag is never suggested.
3. **A clarifying question is a dead end.** When a dream is vague, Claude asks one question. The participant cannot answer it in place; they must edit the dream and ask again.
4. **No way to refine.** "Make these smaller", "fewer goals" or "spread the dates" means starting over, and the participant's edits are lost.
5. **Dates are not checked against the plan.** The server only checks that a suggested date is after today. A goal can still be suggested after its dream's date, or a step after its goal's date. Dates also ignore how much time the participant has.
6. **Helpers and risks go nowhere.** Suggested "people" become free text inside the resources field. They are not linked to partners, and likely blockers are not recorded as obstacles.
7. **Plan check finds problems but does not fix them.** Each issue must be fixed by hand on another screen.
8. **Adding is not safe.** Selected goals or steps are saved one request at a time. If one fails, the plan is left half added.
9. **Suggestions are lost easily.** Leaving the page throws away the suggestions, and asking again spends more of the $20 monthly budget.
10. **Quality cannot be measured.** Plan suggestions cannot be rated, and the system does not count how many are added, edited or discarded, so BO-08 ("50% of AI suggestions accepted") cannot be measured.

V11 turns the builder into a guided, step-by-step plan builder that goes from dream to tasks, lets the participant talk back to Claude, keeps dates realistic, links helpers and risks to the right records, saves a whole plan safely in one action, and measures whether suggestions are useful.

## Scope

### In scope

- A guided plan builder for one dream: clarify, goals, steps, tasks, helpers and risks, check, add.
- AI task builder for complex steps.
- Answering clarifying questions in place, and refining suggestions.
- Server-side date and capacity checks on suggestions.
- Linking suggested helpers to partners, and likely blockers to obstacles.
- One-click fixes from the plan check.
- Saving a reviewed plan in one all-or-nothing action, with undo.
- Keeping unfinished builder sessions as drafts.
- Rating suggestions and counting accepted, edited and discarded items.
- Use of more of the participant's own context (work style, energy, available time, shared fears) within the V9 consent and minimisation rules.

### Out of scope

- Claude saving, sending or changing anything without the participant's action (AI-FR-04 stays).
- Automatic contact with partners. Outreach drafts stay copy-and-send (AI-FR-17).
- Calendar sync with external calendars.
- Changes to coach co-pilot, Dream Coach chat, admin AI tools and security controls, except where this BRD says so.
- Raising the $20 monthly AI budget (D-3). This BRD must fit inside it.

## Baseline: current state (6 Nov 2026)

Review of the code at commit `8a71280`.

| Area | Current state | Gap |
| --- | --- | --- |
| Entry points | Vision area ideas on Vision Areas page, dream ideas on Dreams page, plan assistant card on Dream Detail (`AiPlanAssistant.tsx`) | **Gap** – no single guided path |
| Goal builder | 1 to 5 SMART goals or one stretch goal per request (`AiPlanService.suggestGoals`) | Fields: title, success criteria, date, priority. No sequence, no link between goals |
| Plan builder | 3 to 10 steps for one chosen goal (`suggestSteps`) with owner, date, resources, people | One goal at a time; no priority, complex flag, effort or order dependency |
| Task builder | None | **Gap** – complex steps get no help; method stops at steps |
| Clarifying question | One question returned instead of suggestions; shown as a message telling the participant to edit and ask again | **Gap** – cannot answer in place (AI-FR-03 wants questions one at a time, before drafting) |
| Refine | None; asking again gives a new set | **Gap** – edits lost; extra AI cost |
| Date rules | `futureDate` keeps a date only if after today | **Gap** – goal after dream date and step after goal date are only asked for in the prompt, not checked; no capacity check |
| Helpers | `people` text merged into the step's resources field | **Gap** – not linked to partners |
| Risks | None in the builder | **Gap** – obstacles not suggested |
| Plan check | Rule checks (no owner, date, resource, steps, after goal date, overdue) plus AI checks (vague, timing, gap), each with a fix sentence | **Gap** – no one-click fix |
| Adding | Front end loops `createGoal` / `createStep` per row | **Gap** – partial saves on failure; no undo |
| Persistence | Suggestions held in component state only | **Gap** – lost on navigation; repeat cost |
| Rating | Usage log has `rating` and `ratingComment`; Dream Coach chat uses them; plan assistant has no rating control | **Gap** – AI-FR-06 not met for plan suggestions |
| Acceptance tracking | None | **Gap** – BO-08 not measurable |
| Context sent | Vision areas, dream fields, goals, steps with owner, date, resources and status | Work style, energy, available time and shared fears not used |
| Model and effort | One admin-set model for all features; structured requests at medium effort | AI-NFR-15 asks for a stronger model for plan building and a cheaper one for simple tasks |
| Fallback | Plan check rules work without AI; other buttons are disabled | Guided flow must also work without AI |

## Target experience

A participant opens a dream and chooses **Build my plan**. The builder walks through six stages on one screen. Each stage can be skipped, and the participant can go back.

1. **Clarify** – If the dream is vague, Claude asks up to three short questions, one at a time. The participant answers in place. Answers can be saved to the dream's fields.
2. **Goals** – Claude suggests goals. The participant edits, removes, reorders or asks to refine ("fewer", "smaller", "bolder", "earlier").
3. **Steps** – For each kept goal, Claude suggests steps in order, with owner, date, priority, effort and whether the step is complex.
4. **Tasks** – For each complex step, Claude suggests tasks with owner, due date, priority and estimated hours.
5. **Helpers and risks** – Claude matches needed helpers to the participant's existing partners by role, or proposes new "To Contact" partners. It lists likely blockers as obstacles, with the partner type that could help (using the `CLAUDE.md` mapping: knowledge gap to mentor or expert, and so on).
6. **Check and add** – The plan check runs on the draft before anything is saved. Each problem has a fix the participant can apply in one click. Then **Add plan** saves everything they kept in one action, with an undo.

Without AI, the same six stages work as a guided form with the program's own prompts and the rule-based check.

## Functional requirements

Priority: **M** (Must) ships in V11, **S** (Should) ships unless the sponsor defers it, **C** (Could) is a later improvement.

### A. Guided plan flow

| ID | Requirement | Traces to | Priority |
| --- | --- | --- | --- |
| PB-FR-01 | The system shall offer a **Build my plan** guide on each dream that leads through the stages Clarify, Goals, Steps, Tasks, Helpers and risks, Check and add, on one screen. | FR-05, AI-FR-07, AI-FR-08 | M |
| PB-FR-02 | The participant shall be able to skip any stage, go back to an earlier stage, and leave and return without losing work (see PB-FR-30). | AI-FR-04 | M |
| PB-FR-03 | The guide shall start from the dream's existing plan: existing goals, steps and tasks are shown and kept, and Claude only suggests what is missing. | AI-FR-07 | M |
| PB-FR-04 | The guide shall show progress (stage, items kept, items still needing a date or owner). | NFR usability | M |
| PB-FR-05 | When AI is off, unavailable or not consented, the guide shall still work as a form with the program's own prompts for each stage and the rule-based check. | AI-NFR-02, AC-14 | M |
| PB-FR-06 | The vision area and dream assistants shall offer to continue into the guide after a dream is added. | AI-FR-07 | S |
| PB-FR-07 | The existing plan assistant buttons on Dream Detail stay available for one-off use (suggest goals for this dream, steps for one goal). | Continuity | M |

### B. Clarifying conversation

| ID | Requirement | Traces to | Priority |
| --- | --- | --- | --- |
| PB-FR-08 | When the dream or goal is too vague, Claude shall ask up to three clarifying questions, one at a time, and the participant shall answer each in place. | AI-FR-03 | M |
| PB-FR-09 | Answers shall be used in the next request without the participant editing the dream first. | AI-FR-03 | M |
| PB-FR-10 | The participant may choose to save an answer into the matching dream field (why it matters, what success looks like, target date). Nothing is saved without that choice. | AI-FR-04 | M |
| PB-FR-11 | The participant can choose **Suggest anyway** to skip remaining questions. | Usability | S |

### C. Richer and realistic suggestions

| ID | Requirement | Traces to | Priority |
| --- | --- | --- | --- |
| PB-FR-12 | Step suggestions shall add priority, sequence, whether the step is complex, estimated effort in hours, and "starts after" (another suggested step) where order matters. | FR-05, `CLAUDE.md` step rules | M |
| PB-FR-13 | The guide shall be able to suggest steps for all kept goals in one stage, not only for one chosen goal. | AI-FR-08 | M |
| PB-FR-14 | **Task builder:** for a step marked complex, Claude shall suggest 2 to 8 tasks with title, owner, due date, priority and estimated hours, filling the fields a task requires. | `CLAUDE.md` rule 5, ideal flow 6 | M |
| PB-FR-15 | The server shall enforce date limits on every suggestion, not only ask for them: after today; a goal not after its dream's date; a step not after its goal's date; a task not after its step's date; a step not before a step it starts after. A date that breaks a rule is cleared and flagged "pick a date". | AI-NFR-10, BR-9 | M |
| PB-FR-16 | The participant may give their available hours per week (default from their last answer). The server shall flag weeks where suggested effort, plus effort already planned, exceeds that amount, and offer to spread the dates. | AI-FR-12, BO-04 | S |
| PB-FR-17 | Suggestions shall take the participant's work style and energy pattern into account when they exist (for example, high-energy tasks earlier in the week). | AI-FR-12 | C |
| PB-FR-18 | Owners shall be "Me" or a role, never an invented person's name (unchanged V9 rule), unless the participant links an existing partner (PB-FR-23). | AI-NFR-10 | M |

### D. Refine and regenerate

| ID | Requirement | Traces to | Priority |
| --- | --- | --- | --- |
| PB-FR-19 | The participant shall be able to refine a set of suggestions with quick choices (Fewer, More, Smaller, Bolder, Earlier, Later) or a short note of their own. | AI-FR-03 | M |
| PB-FR-20 | Refining shall keep the items the participant has edited or locked, and change only the others. | AI-FR-04 | M |
| PB-FR-21 | The participant shall be able to replace a single suggestion ("another option for this one"). | Usability | S |
| PB-FR-22 | The participant shall be able to reorder, merge and split suggested goals, steps and tasks before adding. | Usability | S |

### E. Helpers, partners and obstacles

| ID | Requirement | Traces to | Priority |
| --- | --- | --- | --- |
| PB-FR-23 | For each helper a step needs, the guide shall offer: link an existing partner whose role or support type matches, or add a new partner with status "To Contact" and the suggested role and support type. Matching uses partner role and support type on the server; partner names and contact details are not sent to Claude. | AI-FR-13, AI-NFR-04, BO-05 | M |
| PB-FR-24 | Claude shall suggest up to five likely obstacles for the plan, each with type (knowledge, skill, time, money, motivation, partner, system, decision, other), severity and a suggested solution. | `CLAUDE.md` Obstacle, BO-03 | M |
| PB-FR-25 | Each suggested obstacle shall show the partner type that fits it, using the `CLAUDE.md` mapping, and can be linked to a goal or step when added. | `CLAUDE.md` partner rules | M |
| PB-FR-26 | When the participant has shared fears in their journal and gave AI consent, the guide may use those fears to suggest obstacles. The reason shown says it came from their fear journal. | D-2, AI-FR-05 | S |
| PB-FR-27 | After adding, the guide shall offer to draft an outreach message for each new "To Contact" partner (existing AI-FR-17 feature). | AI-FR-17 | S |

### F. Check and fix

| ID | Requirement | Traces to | Priority |
| --- | --- | --- | --- |
| PB-FR-28 | The plan check shall run on the draft plan before it is saved, as well as on the saved plan (existing). | AI-FR-09 | M |
| PB-FR-29 | Each rule-based issue shall have a one-click fix where the fix is clear: set owner to Me, move a date to the parent's date, mark a step complex and open the task builder, add the first step. AI issues (vague, timing, gap) shall offer **Fix with AI**, which returns an editable change, not a saved one. | AI-FR-09, AI-FR-04 | M |

### G. Save safely

| ID | Requirement | Traces to | Priority |
| --- | --- | --- | --- |
| PB-FR-30 | The guide shall keep an unfinished session as a draft on the server for 14 days, per participant and dream, so leaving the page or changing device loses nothing and asks Claude nothing again. | BO-12 | M |
| PB-FR-31 | **Add plan** shall save all kept goals, steps, tasks, partners and obstacles in one request that succeeds or fails as a whole, using the same validation and user scoping as the normal create endpoints. | BR-1 to BR-5, SEC-01-01 | M |
| PB-FR-32 | After **Add plan**, the participant can **Undo** for the rest of the session; undo archives what was just added (soft delete, BR-12). | BR-12 | M |
| PB-FR-33 | A draft never counts as saved data: it does not appear on the dashboard, board, exports, coach views or share links. | AC-09, FR-10 | M |

### H. Feedback and measurement

| ID | Requirement | Traces to | Priority |
| --- | --- | --- | --- |
| PB-FR-34 | Each set of suggestions shall have a thumbs up or down and an optional comment, stored on the existing usage log row. | AI-FR-06, BO-09 | M |
| PB-FR-35 | For each suggested item the system shall record whether it was added as-is, added after editing, or discarded, with the feature name and time only (no text). | BO-08, AI-NFR-14 | M |
| PB-FR-36 | The admin AI page shall show, per builder stage, acceptance rate, edit rate, average rating, requests per finished plan and average cost per finished plan. | BO-08, BO-12 | M |
| PB-FR-37 | Coaches shall see, for participants of their active cohorts, whether a plan was built with the guide (yes or no and date), not the suggestions themselves. | D-6, FR-16 | C |

### I. Context and model

| ID | Requirement | Traces to | Priority |
| --- | --- | --- | --- |
| PB-FR-38 | The context sent for each stage shall contain only what the stage needs (AI-NFR-04): the dream, its kept draft items, clarifying answers, available hours and, where consented and relevant, work style, energy and shared fears. Never partner names or contact details. | AI-NFR-04, D-2 | M |
| PB-FR-39 | Admins shall be able to set a separate model for plan building (goals, steps, tasks, check) and for simple tasks (rating explanations, single replacements), within the same budget. | AI-NFR-15 | S |
| PB-FR-40 | Resource suggestions inside the guide shall come from the admin-approved list first; others are marked "check before use" (unchanged AI-FR-13 and AI-FR-26 rules). | AI-FR-13, AI-FR-26 | M |

## Non-functional requirements

| ID | Requirement |
| --- | --- |
| PB-NFR-01 | Response time: each stage shows progress at once and returns suggestions within 30 seconds (AI-NFR-01). A full guided plan for a dream with 3 goals should need no more than 3 minutes of waiting in total. |
| PB-NFR-02 | Cost: a finished guided plan (3 goals, about 12 steps, tasks for complex steps, helpers and risks, one check) should cost no more than $0.25 in AI use, so a 20-person pilot can build plans inside the $20 monthly cap (D-3). Measured in Phase 1 and shown on the admin page (PB-FR-36). |
| PB-NFR-03 | Request limit: one guided session uses at most 15 AI requests, refinements included. After that, the guide continues without AI and says why. Every request still counts toward the daily limit and monthly cap (AI-NFR-15). |
| PB-NFR-04 | Validation: every field Claude returns is checked and trimmed on the server before it is shown (unchanged V9 rule); item IDs that are not in the participant's own plan are dropped. |
| PB-NFR-05 | Safety, consent, crisis handling, scope guardrails and prompt-injection rules from V9 and V10 apply unchanged (AI-NFR-03, AI-NFR-08, AI-NFR-09, AI-NFR-13, SEC-05-07). Clarifying answers and refine notes are participant data, not instructions. |
| PB-NFR-06 | Language: all stages, prompts and Claude answers work in English and Khmer (AI-NFR-16). New interface text is added to the Khmer translation file. |
| PB-NFR-07 | Accessibility: the guide meets WCAG 2.1 AA, works by keyboard and screen reader, and is usable on a phone (single column). |
| PB-NFR-08 | Quality test set: the AI governance lead keeps 20 sample dreams (10 English, 5 Khmer, 5 deliberately vague). Each change to builder prompts or models is tried on the set before release (AI-NFR-18). |
| PB-NFR-09 | Drafts are user-scoped, never readable by coaches or admins, deleted after 14 days, and deleted with the account (NFR-09). |
| PB-NFR-10 | Tests: backend tests for date limits, all-or-nothing save, undo, draft scoping and cross-user refusal on every new endpoint (SEC-01-08); frontend tests for each stage and the no-AI path. Claude is mocked in tests. |

## Data and API changes (for solution design)

These are business-level needs. The solution design decides exact names.

| Need | Likely change |
| --- | --- |
| Step effort and order | Step: estimated hours, "starts after" step (optional) |
| Builder drafts | New table: plan builder draft (user, dream, stage, draft content, clarifying answers, created, expires) |
| Suggestion outcomes | New table: suggestion outcome (usage log row, item kind, outcome: added, edited, discarded, time). No text |
| Available hours | Participant setting: hours per week for plan work |
| Plan model | AI settings: plan model alongside the current model |
| Endpoints | Start or resume a draft; clarify; suggest per stage (goals, steps, tasks, helpers and risks); refine; check draft; fix with AI; add plan (one transaction); undo; rate. All under `/api/ai/plan`, all user-scoped, ownership checked before AI availability (SEC-01-02) |

Migrations continue from the latest Flyway version (V62 at the time of writing).

## Implementation phases

Each phase ends with build, tests and a summary, then waits for confirmation (as `CLAUDE.md` requires).

| Phase | Scope | Requirements |
| --- | --- | --- |
| 1 | Measure and fix the base: suggestion ratings, outcome tracking and admin metrics; server-enforced date limits; all-or-nothing **Add** for the existing goal and step suggestions, with undo | PB-FR-15, PB-FR-31, PB-FR-32, PB-FR-34 to PB-FR-36 |
| 2 | Guide shell and drafts: six-stage screen, resume from server draft, no-AI form path, start from existing plan | PB-FR-01 to PB-FR-05, PB-FR-07, PB-FR-30, PB-FR-33, PB-NFR-09 |
| 3 | Clarify and refine: in-place answers, save answers to dream, refine quick choices, keep edited items, replace one item | PB-FR-08 to PB-FR-11, PB-FR-19 to PB-FR-21 |
| 4 | Richer steps and task builder: priority, sequence, complex flag, effort, "starts after", steps for all goals, tasks for complex steps | PB-FR-12 to PB-FR-14, PB-FR-18 |
| 5 | Helpers and risks: partner matching and "To Contact" partners, obstacle suggestions with partner type, fears with consent, outreach after adding | PB-FR-23 to PB-FR-27, PB-FR-38, PB-FR-40 |
| 6 | Check and fix on the draft, one-click and AI fixes, capacity check, plan model setting, quality test set run, Khmer review, docs | PB-FR-16, PB-FR-28, PB-FR-29, PB-FR-39, PB-NFR-06 to PB-NFR-08 |
| Later | Work style and energy, reorder, merge and split, coach visibility, continue from dream assistant | PB-FR-06, PB-FR-17, PB-FR-22, PB-FR-37 |

### Implementation status

| Phase | Status |
| --- | --- |
| 1 | Built and tested locally, 6 Nov 2026 (Claude mocked in tests): ratings on every plan builder answer (PB-FR-34); added, edited and discarded counts with no text, migration V63 (PB-FR-35); *Plan builder quality* card for admins (PB-FR-36); server-enforced parent date limits for goals and steps (PB-FR-15, tasks follow in Phase 4); one-transaction add with undo for goals and steps (PB-FR-31, PB-FR-32). "Cost per finished plan" waits for the guide in Phase 2; Phase 1 shows cost per request. Vision areas and dreams are still added one by one. Not yet tried against the live API |
| 2 | Built and tested locally, 6 Nov 2026 (Claude mocked in tests): Build my plan page with six stages, skip and back, new-item counts and a readiness list (PB-FR-01, PB-FR-02, PB-FR-04); starts from the dream's existing goals and steps, which are never changed (PB-FR-03); a form path with the program's questions when AI is off (PB-FR-05); the plan assistant card kept (PB-FR-07); server drafts saved as the participant works, private, never plan data, removed after 14 days, with the dream and with the account, migration V64 (PB-FR-30, PB-FR-33, PB-NFR-09); one-transaction add of goals, steps and tasks with undo. Also step suggestions for a goal that is only in the draft (part of PB-FR-13). The Helpers and risks stage shows questions and links only until Phase 5; the readiness list is client-side until the draft plan check (PB-FR-28). Not yet tried in a browser against a running backend, nor against the live API |
| 3 | Built and tested locally, 6 Nov 2026 (Claude mocked in tests): Clarify asks up to three questions, one at a time, answered in place (PB-FR-08); answers kept in the draft and sent with every later suggestion (PB-FR-09); a ticked answer saved into the dream's why, success or target date (PB-FR-10); Suggest anyway (PB-FR-11); refine with Fewer, More, Smaller, Bolder, Earlier or Later and a note, keeping edited and locked items (PB-FR-19, PB-FR-20); Another option for one item (PB-FR-21). Participant text goes to Claude only as data. Not yet tried against the live API |
| 4 | Built and tested locally, 6 Nov 2026 (Claude mocked in tests): steps store effort and "starts after" (migration V65) and step suggestions carry priority, complexity, effort and order (PB-FR-12); steps for all goals without steps in one action (PB-FR-13); task builder for saved and draft complex steps, 2 to 8 tasks with owner, due date, priority and hours (PB-FR-14); task dates kept within the step's date and step dates not before the step they wait for (PB-FR-15); owners "Me" or a role (PB-FR-18). The regular step form and the Excel workbook do not show effort or order yet. Not yet tried against the live API |
| 5 | Built and tested locally, 6 Nov 2026 (Claude mocked in tests): helpers matched to the participant's own partners on the server, linked where the partner has no link yet or added as To Contact partners (PB-FR-23); up to five risks with type, severity, solution and the fitting kind of helper, added as Open obstacles (PB-FR-24, PB-FR-25); fear journal used only when ticked for the request (PB-FR-26, the cautious answer to the open question below); outreach drafts for new contacts after adding (PB-FR-27); only the dream, the draft items and ticked fears are sent, never partner names or contacts (PB-FR-38); approved resources first (PB-FR-40). Undo archives created partners and obstacles but leaves links given to existing partners. Not yet tried against the live API |
| 6 | Built and tested locally, 6 Nov 2026 (Claude mocked in tests): plan check on the draft with built-in and AI issues (PB-FR-28); one-click fixes and Fix with AI proposals applied only by choice (PB-FR-29); hours a week remembered, overloaded weeks flagged and Spread the dates (PB-FR-16); separate plan building model (PB-FR-39); Khmer for all V11 text (PB-NFR-06) with a review export, `docs/km-review-v11.csv`; form controls have labels (the tests find them by label) and the layout is one column on narrow screens, but no WCAG 2.1 AA audit has been done (PB-NFR-07); the 20-dream sample set and an opt-in live run (PB-NFR-08). Outstanding: the live sample run (needs an API key and spend; gives PAC-04 and PAC-12 numbers), the Khmer speaker's review (PAC-13), the 4-week pilot measures (PAC-14), an accessibility audit (PB-NFR-07), and UAT in a browser |

Phase 1 comes first on purpose: it gives the baseline numbers (acceptance rate, cost per plan) the later phases are judged against, and fixes the two defects that can hurt data today (unchecked dates, partial saves).

## Acceptance criteria

| ID | Criterion | Traces to |
| --- | --- | --- |
| PAC-01 | A participant with one vague dream reaches an added plan with goals, steps and tasks for complex steps, in one session on one screen, in under 30 minutes of active time. | PB-FR-01, AC-08 |
| PAC-02 | With AI turned off, the same guide can be completed using the form path, and the rule-based check still runs. | PB-FR-05, AC-14 |
| PAC-03 | A vague dream gets at most three clarifying questions, one at a time, answered in place, and suggestions follow without editing the dream. | PB-FR-08, PB-FR-09 |
| PAC-04 | In the 20-dream test set, no shown suggestion has a date before tomorrow, a goal after its dream's date, a step after its goal's date, or a task after its step's date. | PB-FR-15 |
| PAC-05 | Refining a set keeps every item the participant edited or locked unchanged. | PB-FR-20 |
| PAC-06 | If saving any one item in **Add plan** fails, nothing from that plan is saved and the participant sees what to fix. | PB-FR-31 |
| PAC-07 | **Undo** after **Add plan** archives every item just added and nothing else. | PB-FR-32 |
| PAC-08 | A draft survives a page reload and sign-in on another device, makes no new AI request when resumed, and is never visible to a coach, admin, share link, export or dashboard. | PB-FR-30, PB-FR-33 |
| PAC-09 | No partner name, email or phone number appears in any request sent to Claude by the guide (checked from request logs in tests). | PB-FR-23, AI-NFR-04 |
| PAC-10 | Every plan suggestion set can be rated, and the admin page shows acceptance rate, edit rate and cost per finished plan per stage. | PB-FR-34 to PB-FR-36 |
| PAC-11 | Every new endpoint refuses no token (401) and another user's dream, goal, step or draft (404), in the automatic route sweep. | PB-NFR-10, SEC-01-08 |
| PAC-12 | Average cost of a finished guided plan in the test set is $0.25 or less. | PB-NFR-02 |
| PAC-13 | Khmer reviewers rate at least 80% of sampled Khmer builder answers clear and correct. | PB-NFR-06, AC-15 |
| PAC-14 | After four weeks of pilot use, at least 50% of suggested items are added (as-is or edited) and the average rating is 4.0 or more. | BO-08, BO-09 |
| PAC-15 | All existing backend and frontend tests still pass. | — |

## Assumptions, dependencies and risks

### Assumptions

- The pilot stays at about 20 participants under the $20 monthly cap.
- Participants have given the V9 AI consent before using AI stages; without it they use the form path.
- The method hierarchy (Vision Area → Dream → Goal → Step → Task) does not change.

### Dependencies

- A Khmer reviewer for builder answers (D-4).
- The AI governance lead to build and keep the 20-dream test set before Phase 4.
- A lead coach to review the program prompts used in the no-AI form path.

### Risks

| Risk | Impact | Mitigation |
| --- | --- | --- |
| Guided flow uses more AI requests than separate buttons | Budget overrun | Drafts avoid repeat requests; 15-request session limit; per-stage model choice; cost shown per plan (PB-NFR-02, PB-NFR-03) |
| Participants accept a whole AI plan without thinking | Shallow plans, low ownership | Clarify stage first; items start unticked when the dream had no answers to clarifying questions; coach review at week 4 stays |
| Too many stages feel long | Drop-off | Skip any stage; resume later; progress shown; existing one-off buttons kept |
| Capacity check gives false warnings | Annoyance | Should priority; participant can dismiss; uses their own hours setting |
| Partner matching by role links the wrong person | Wrong helper contacted | Matching only suggests; participant confirms each link; no automatic outreach |
| Fears used for obstacles feel intrusive | Trust loss | Only with consent and only shared fears; reason shown; can be turned off per session |
| Larger structured answers cut short at the token limit | Missing items | One stage per request; tasks per step; cut-short answers retried once with fewer items |

## Open questions

- [ ] Should the guide replace the plan assistant card on Dream Detail, or sit beside it? (Draft keeps both, PB-FR-07.)
- [ ] Is $0.25 per finished plan the right cost target, or should Phase 1 measure first and set it then?
- [ ] Should suggested items start ticked (faster) or unticked (more thought)? Draft: ticked, except after an unanswered clarifying stage.
- [ ] May the guide use shared fears to suggest obstacles by default, or only when the participant turns it on each time? (PB-FR-26)
- [ ] Should available hours per week be asked once in Account settings, or in each guide session?
- [ ] Which model should the admin set for plan building, and does that model fit the budget at pilot scale?
- [ ] How long should drafts be kept: 14 days as drafted, or until the dream is archived?
- [ ] Should coaches see that a plan was built with the guide (PB-FR-37), or is that unnecessary?

## Sign-off

| Role | Name | Decision | Date |
| --- | --- | --- | --- |
| Sponsor | | | |
| Product owner | | | |
| AI governance lead | | | |
| Lead coach | | | |

## Sources

- BRD 10.0.0 – Security Hardening to the OWASP Top 10 (6 Nov 2026)
- BRD 9.0.0 – Dream Conversion Program with Claude AI Integration (2 Nov 2026), and its parent documents
- `CLAUDE.md` – method hierarchy, business rules and partner mapping
- Code review of commit `8a71280`, 6 Nov 2026: `AiPlanService`, `AiPlanController`, `AiDrafts`, `AiStructuredRequests`, `ClaudeClient`, `AiPlanAssistant.tsx`, `AiStartAssistant.tsx`
