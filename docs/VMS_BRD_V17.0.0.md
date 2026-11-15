# BRD – Blockers in the Right Place

Nov 15, 2026

## Document control

This is draft 17.0.0. It keeps every requirement in BRD 16.0.0 (Vision Map Timeline), BRD 15.0.0 (AI Assistant and Helpers), BRD 14.0.0 (Sign in with Google, paused), BRD 13.0.0 (Effective Java), BRD 12.0.0 (Test Coverage), BRD 11.0.0 (AI Plan Builder), BRD 10.0.0 (Security Hardening), BRD 9.0.0 (Dream Conversion Program), BRD 8.1.0 (Strengths and Gaps) and earlier versions. Where this BRD is silent, the earlier BRDs still apply.

V17 makes the app follow one rule from the Vision Mapping method: **blockers are recorded where the work is, not where the dream is.** A dream and its goals describe only the outcome. Missing knowledge, skills, money, time or people are recorded against **steps**. Friction in doing the work is cleared at the **task** level by breaking work down. Inner blockers (the six chains) are checked **before** a dream is mapped. The background research behind this rule is kept, unchanged, in Appendix A.

| Item | Value |
| --- | --- |
| Document | Business Requirements Document (BRD) – Blocker placement addendum |
| Version | 17.0.0 – Draft |
| Parent documents | BRD 16.0.0 (9 Nov 2026), BRD 8.0.0 (FR-71 chain assessment), BRD 8.1.0 (FR-79 to FR-84 Strengths and Gaps), BRD 9.0.0 (v0.1 FR-05 step resources, AI-FR-09 plan check), BRD 11.0.0 (PB-FR-23 to PB-FR-25 helpers and risks), BRD 15.0.0 (AH-FR-35, AH-FR-39, AH-FR-41 obstacle helpers) |
| Business owner | To be confirmed |
| Product owner | To be confirmed |
| Approvers | Sponsor, Product owner, Lead coach |

| Version | Change | Author |
| --- | --- | --- |
| 17.0.0 | First draft: current state at commit `87a40fb`, gap analysis against the method, obstacle placement (BL-FR-01 to BL-FR-10), dream and goal stages (BL-FR-11 to BL-FR-16), step gaps (BL-FR-17 to BL-FR-23), task friction (BL-FR-24 to BL-FR-29), six chains (BL-FR-30 to BL-FR-36), AI, Excel and API (BL-FR-37 to BL-FR-42), migration notes, phases and acceptance criteria. The original research note moved to Appendix A | BA |
| 17.0.0 (phase 1 built) | Phase 1 implemented. Clarified during build: "active" in BL-FR-05 and BL-FR-07 means Open or In Progress (Resolved and Accepted obstacles need no action, so they are not listed as "Not on a step yet"). BL-FR-41 assumed the Excel import already read the Obstacles sheet; it did not, so phase 1 added an Obstacles importer, which skips Resolved and Accepted rows with a reason because the sheet holds no root cause or alternatives. BL-FR-04's exemption covers all FR-54 and FR-60 to FR-62 answers, including the conduct checklist and expectation agreement. Until phase 2, the AI Plan Builder and plan assistant save goal-level and dream-level risks as unplaced obstacles (listed under "Not on a step yet") | Dev |
| 17.0.0 (phase 2 built) | Phase 2 implemented. Clarified during build: BL-FR-37's "A new step for this risk" is saved as a step titled "Deal with: {risk}", owner Me, priority High, dated by its goal (else the dream, else two weeks out). BL-FR-38: proposals with a risk on the dream or a goal are dropped, and saving one answers 400. BL-FR-15's count covers obstacles placed on a step or task only (older unplaced ones are counted by "Not on a step yet" instead). The phase 1 bridge for the AI Plan Builder and plan assistant is removed | Dev |
| 17.0.0 (phase 3 built) | Phase 3 implemented. Clarified during build: a gap step from a step needs gap text on that step (400 otherwise). It takes the source step's sequence number and the later steps move down one; its default date is the day before the source step's date, never before today, or two weeks out without one. BL-FR-20 from an obstacle on a live step places the new step before that step (and records it in `covers_step_id` too); an obstacle not on a live step needs a goal and goes last. Resolved and Accepted obstacles cannot be turned into a step (they need no action). The new step inherits the gap kind, and BL-FR-22 does not flag a "find help" step itself. Without a partner name the partner is saved as "To find: {step title}". BL-FR-23 reuses the create logic but keeps FR-82's place (last in the goal). Q-07 (gaps on the Partners page) is not built | Dev |
| 17.0.0 (phase 4 built) | Phase 4 implemented. Clarified during build: split rows are placed right after the original in the step's order (`sort_order`, legacy rows numbered first) and, once the participant has ordered the Tasks page, in that order too. **Replace it** writes "Split into …" to the archived task's description and progress log. Q-04 answered with the draft: **Keep** is the default. A task created as Blocked counts as its first block, and staying Blocked through an edit does not count again. "What changed?" is asked on a move (board, status menu, Vision Map) and as a field in the task form. BL-FR-28's "Add an obstacle on this task" opens the Obstacles form on that task with the blocker reason as its title; the "What's missing?" kind is not stored on tasks, so it is not pre-filled. BL-FR-29: there is no V15 page switch for the Tasks page, so the suggestion uses the same consent, daily limit and monthly cap as the existing task builder | Dev |
| 17.0.0 (phase 5 built) | Phase 5 implemented with the Appendix A chain list, because Q-01 is still open (the BRD's draft answer). Clarified during build: the two new chains reuse existing modules, misplaced priorities → module 13 "Plan Each Day by Priority" and lack of persistence → module 12 "Keep Promises to Yourself". Lack of persistence is labelled "Giving up too soon" in the app. `averageScore` covers the chains that take measured; `improvementPercent` and the AI "what changed" compare only chains both takes measured, and a new chain's baseline is the first take that measured it. Retaking on the same day as a pre-V17 take clears its retired scores. BL-FR-34's link opens the Journal with the new-fear field focused (`/journal?newFear=1`). BL-FR-33's card shows on the Dreams page and in the first step of the dream guide; skipping is remembered in the browser for 30 days. Q-03 answered with the draft: optional for everyone | Dev |

## Executive summary

The method separates three kinds of blockers and handles each at a set place in the hierarchy:

| Kind | Example | Where it belongs |
| --- | --- | --- |
| **Inner blockers** (the six chains) | Fear of failure, settling for "realistic" | **Before** the dream is mapped |
| **Strategic blockers** (gaps) | "I don't know how to run a survey", "I have no budget for a translator" | The **step** that needs the missing thing. The gap becomes a step to recruit help |
| **Execution blockers** (friction) | "I keep putting this off", "this is too big to start" | The **task**. The fix is to break it down until the next action is easy |

Dreams and goals carry **no** blockers. They say only what success looks like.

The app already handles steps and tasks well. A step has a **Resources and people** field, a Blocked task needs a reason, and a complex step is prompted to split into tasks. But the app also lets people attach obstacles straight to a dream or a goal, so blockers end up in the wrong place. It never turns a gap into a step except inside Strengths and Gaps. Its chain assessment measures a different set of six chains from the method's list. And `CLAUDE.md` itself asks "What can block this goal?" during goal coaching.

V17 closes these gaps in five phases:

1. **Placement.** A new plan obstacle must sit on a step or a task. Its goal and dream are filled in from that anchor, so they are never chosen by hand. Existing dream-only and goal-only obstacles are kept and listed so the participant can move them. Nothing is deleted.
2. **Outcome-only dreams and goals.** The dream and goal forms coach "describe the outcome, not the limits". Dream and goal views show a read-only roll-up of the blockers in their steps and tasks.
3. **Gap → step.** One click turns a missing resource or an obstacle into a new "find help" step, and optionally a partner to contact.
4. **Break it down.** A Blocked task offers **Break into smaller tasks**. Unblocking asks what changed, so the lesson is kept.
5. **The six chains, before the dream.** The chain assessment is aligned with the method's list and offered before a participant's first dream.

## Business objectives

| ID | Objective | Measure |
| --- | --- | --- |
| BLBO-01 | Blockers are recorded where they can be acted on | 95% of Open plan obstacles created after release sit on a step or task (today, dream-only and goal-only links are allowed) |
| BLBO-02 | Gaps turn into action, not worry | 40% of step gaps or obstacles of type Knowledge, Skill, Money or Time lead to a "find help" step or a partner record within 14 days |
| BLBO-03 | Blocked tasks get unstuck faster | Median days a task stays Blocked falls by 20% within 90 days |
| BLBO-04 | Dreams stay bold | Share of dream descriptions that contain limit words (BL-FR-13) falls by 30% within 90 days. Measured on English text only |
| BLBO-05 | Inner blockers are seen before planning | 50% of new participants take the chain check before or right after their first dream |
| BLBO-06 | No data loss | Zero obstacles lost or silently re-linked by the migration (BLC-15) |

## Scope

### In scope

- A placement rule for obstacles: anchor on a step or task, with goal and dream derived from it.
- A list of existing obstacles that are not placed on a step or task, with **Move to a step** and **Turn into a step** actions.
- A read-only blocker roll-up on dream and goal views.
- Outcome-only coaching on the dream and goal forms, and a soft hint when limit words appear.
- Changing the goal coaching question in the app and in `CLAUDE.md`.
- **Turn this gap into a step** from a step's Resources field and from an obstacle.
- **Break into smaller tasks** for a Blocked task, and a short "what changed?" note when a task leaves Blocked.
- Aligning the six chains with the method, and a pre-dream chain check that can be skipped.
- Matching changes to the AI Plan Builder, plan assistant, obstacle helpers, Excel import and export, and the API.

### Out of scope

- Sub-tasks (a task inside a task). "Smaller tasks" are new tasks under the same step (BL-FR-25).
- Removing the `related_dream_id` or `related_goal_id` columns. They stay, as derived context (BL-FR-02).
- Changes to fear journal, gratitude, partner, review or communication links to dreams. Fears are inner blockers and belong at the dream level (Appendix A, section 2A).
- Making the chain check a required gate before a dream can be created (see Q-03).
- Limit-word detection in Khmer (BL-FR-13 is English-only in V17).
- Coach views of placement or chains beyond what V9 decision D-6 already allows.

## Current state (15 Nov 2026, commit `87a40fb`)

| Area | What exists today | Gap against the method |
| --- | --- | --- |
| Obstacle links | `Obstacle` has optional `relatedDream`, `relatedGoal`, `relatedStep` and `relatedTask` ([Obstacle.java](../backend/src/main/java/com/visionmapping/entity/Obstacle.java)). `ObstacleService` accepts any mix, with no rule that they agree with each other | An obstacle can sit on a dream or goal only. Its goal can belong to a different dream from its step |
| Obstacles page | Dream, Goal, Step and Task pickers, all optional ([ObstaclesPage.tsx](../frontend/src/pages/ObstaclesPage.tsx)) | Invites dream-level and goal-level blockers |
| Plan Builder risks | The risk card's **For** list starts with "The whole dream" ([HelpersStage.tsx](../frontend/src/components/plan-builder/HelpersStage.tsx)). `PlanBuilderDraftService` always sets the dream, plus a goal or step if one was chosen | Risks default to dream level |
| Plan assistant | Creates obstacles with the dream and an optional goal or step (`PlanAssistantService`) | Same as above |
| Dream form | Clarity questions when a dream is vague ([DreamsPage.tsx](../frontend/src/pages/DreamsPage.tsx)). Moonshot fields (V9) | Nothing says "leave out the limits" |
| Goal coaching | `sectionHelp.ts` and `CLAUDE.md` ("AI-Like Coaching Behavior") ask **"What can block this goal?"** | Asks for blockers at goal level |
| Step gaps | `VisionStep.resources` "Resources and people" (v0.1 FR-05). The AI plan check flags steps without it (AI-FR-09). `isComplex` prompts tasks | Free text only. No action turns a gap into a "find help" step |
| Strengths and Gaps | A high-relevance weakness can be covered by a partner, a delegate, a plan step or an exercise (FR-82, `linked_step_id`) | This is the only place where a gap becomes a step |
| Blocked tasks | `blockerReason` required (rule 6). "What's missing?" category with a suggested partner type (VisionMapTree). Blocked tasks with no obstacle are listed (AH-FR-39) | No prompt to break a blocked task down. No record of what unblocked it |
| Creative persistence | Root cause before Resolved, three alternatives before Accepted (FR-32, V10) | Works on obstacles only, not on a task that was blocked |
| Six chains | `chain_assessments`: settling, fear, criticism, vision, **knowledge**, **resources** (FR-71, V44). Not offered before the first dream | Method list: mediocrity (settling), fear of failure, criticism, **misplaced priorities**, **lack of persistence**, lack of clear vision. Knowledge and resources are step-level gaps in the method, not inner chains |
| Partner-type obstacles | Conflict worksheet, expectation release, criticism triage and conduct checklist (FR-54, FR-60 to FR-62) | These are about relationships. They do not always fit one step (see BL-FR-04) |
| Excel | Obstacles sheet: ID, Dream ID, Goal ID, Step ID, Task ID, Title, Type, Severity, Status, Solution (`ExcelService`) | Import accepts dream-only rows with no warning |
| `CLAUDE.md` | "Obstacle may link to Dream, Goal, VisionStep, or TaskItem." The goal coaching asks what can block the goal | Must be updated with a recorded decision (D-17-1, D-17-2) |

## Decisions recorded by this BRD

| ID | Decision | Effect on `CLAUDE.md` |
| --- | --- | --- |
| D-17-1 | A **plan obstacle** (any type except Partner conflicts, BL-FR-04) is anchored to exactly one step or one task. Its goal and dream are derived from that anchor and cannot be set by hand. | Entity Relationships: replace "Obstacle may link to Dream, Goal, VisionStep, or TaskItem" with "Obstacle is anchored to a VisionStep or TaskItem; its Goal and Dream are derived (V17, D-17-1)." |
| D-17-2 | Dreams and goals describe outcomes only. Goal coaching asks which steps will need something the participant lacks, not what blocks the goal. | AI-Like Coaching Behavior → "When the user enters a goal": replace "What can block this goal?" with "Which steps will need something you don't have yet?" |
| D-17-3 | The six chains follow the method's list (BL-FR-30). Knowledge and resources move to the step-level gap check. | None (chains are not in `CLAUDE.md`) |

`CLAUDE.md` is changed in phase 1 only after the product owner signs these decisions off.

## Target experience

**Writing a dream.** Above the description, a short line says: *"Describe what success looks like. Leave out what might stop you; you will deal with that in the steps."* If the text says things like "if I can afford it" or "but I don't have time", a gentle note suggests moving that thought to a step later. Saving is never blocked.

**Writing goals.** Goal coaching asks *"Which steps will need something you don't have yet?"*. The dream's map shows, under each goal, a small read-only line such as *"3 open blockers in this goal's steps"*, which links to them.

**Planning a step.** Next to **Resources and people**, a question reads *"What skill, money, time or knowledge is missing here?"* After a gap is written, a **Turn this gap into a step** button suggests a new step, such as *"Find a statistics mentor"*, placed before the current step. It can also create a partner to contact.

**Stuck on a task.** Marking a task Blocked still asks for the reason and what is missing. It now also offers **Break into smaller tasks**, which adds two to five smaller tasks under the same step. When the task leaves Blocked, a one-line *"What changed?"* note is saved to its progress log.

**Before the first dream.** A new participant sees a short card: *"Before you map a dream, take two minutes to see what might hold you back inside."* It links to the chain check. The card can be skipped.

## Functional requirements

Priority uses MoSCoW: **M** must, **S** should, **C** could.

### A. Obstacle placement

| ID | Requirement | Links | Priority |
| --- | --- | --- | --- |
| BL-FR-01 | **Anchor rule.** Creating or updating a plan obstacle requires a step or a task. If both are given, the task must belong to the step; otherwise the server returns 400 with "Put this blocker on a step or a task." | D-17-1, BLBO-01 | M |
| BL-FR-02 | **Derived parents.** On save, the server sets `relatedStep` from the task (if a task is given), then `relatedGoal` and `relatedDream` from the step. Any dream or goal sent by the client is ignored. Responses still return all four ids, so filters by dream or goal keep working. | D-17-1 | M |
| BL-FR-03 | **Obstacle form.** The Obstacles page replaces its four separate pickers with one **Where is this blocker?** picker that lists steps grouped by dream and goal, with an optional task under the chosen step. Dream and goal show as read-only text once a step is picked. | BLBO-01 | M |
| BL-FR-04 | **Partner conflicts exempt.** Obstacles of type Partner that use the conflict or criticism worksheets (FR-54, FR-60 to FR-62) may have no anchor, or a step or task anchor. If they have no anchor, they may keep a dream for context. | D-17-1 | M |
| BL-FR-05 | **Unplaced list.** The Obstacles page gets a filter and a count, **Not on a step yet**, for active, non-exempt obstacles with no step and no task. Each row offers **Move to a step** (opens BL-FR-03 with the dream and goal pre-filtered) and **Turn into a step** (BL-FR-20). | BLBO-06 | M |
| BL-FR-06 | **No silent change.** Existing unplaced obstacles are never moved, archived or deleted automatically. Until moved, they keep their current links and can be read, filtered, edited (title, status, solution) and resolved. Placement is required only when the participant changes the anchor itself or creates a new obstacle. | BLBO-06 | M |
| BL-FR-07 | **Attention item.** The dashboard attention panel shows "N blockers are not on a step yet" when N > 0, linking to BL-FR-05. It is hidden at 0. | | S |
| BL-FR-08 | **Moving the anchor.** When a step or task is moved to another goal or dream, its obstacles' derived goal and dream update in the same transaction. | BL-FR-02 | M |
| BL-FR-09 | **Archived anchors.** Archiving a step or task does not archive its obstacles. They show in the unplaced list with "Its step was archived", until moved, resolved or archived by the participant. Restoring the step brings them back. | BL-FR-06 | S |
| BL-FR-10 | **Partner matching unchanged.** Obstacle partner matching (AH-FR-35), "Blocked tasks with no obstacle" (AH-FR-39) and "Which obstacle first?" (AH-FR-41) keep working. AH-FR-41 ranks placed obstacles ahead of unplaced ones when scores tie. | BRD 15.0.0 | M |

### B. Dream and goal stages: outcome only

| ID | Requirement | Links | Priority |
| --- | --- | --- | --- |
| BL-FR-11 | **Dream outcome hint.** The dream form shows the line from the target experience above the description and the success definition. | D-17-2, BLBO-04 | M |
| BL-FR-12 | **Goal outcome hint.** The goal form shows: *"Say what result this goal delivers. Gaps and help come in the steps."* | D-17-2 | M |
| BL-FR-13 | **Limit-word note.** While a dream or goal description or success text contains a limit phrase from a fixed English list (for example "can't afford", "if I have time", "but I don't", "not realistic", "I'm not good at"), a soft, dismissible note appears: *"This sounds like a blocker. Keep the dream bold. You can add this as a gap on a step after saving."* It never blocks saving. Dismissing it hides it for that record. The list lives in one shared file. | BLBO-04 | S |
| BL-FR-14 | **Goal coaching question.** Replace "What can block this goal?" in `sectionHelp.ts`, the page assistant help, the AI plan assistant prompts and `km.ts` with "Which steps will need something you don't have yet?" | D-17-2 | M |
| BL-FR-15 | **Blocker roll-up.** Dream and goal rows on the Vision Map tree, the dream detail page and the Goals table show a read-only count of open obstacles and Blocked tasks in their steps and tasks, for example "2 blockers · 1 blocked task". The count links to the Obstacles page filtered to that dream or goal. No add-obstacle action appears at these levels. | BLBO-01 | M |
| BL-FR-16 | **No obstacle action at dream or goal.** Any "Add obstacle" or "Add risk" action reachable from a dream or goal opens BL-FR-03 and asks for a step first. If the goal has no steps, it offers **Add a step first**. | D-17-1 | M |

### C. Step stage: gaps become steps

| ID | Requirement | Links | Priority |
| --- | --- | --- | --- |
| BL-FR-17 | **Gap question.** The step form labels the Resources field **What is missing, and who or what could supply it?**, with the helper text *"Skill, money, time or knowledge you don't have yet."* The field and its column are unchanged. | v0.1 FR-05 | M |
| BL-FR-18 | **Gap category.** The step form offers an optional **Kind of gap** (Knowledge, Skill, Time, Money, Decision, Partner, Motivation, the same list as the task "What's missing?" picker). When chosen, it shows the suggested helper type from `suggestPartnerFor`. | | S |
| BL-FR-19 | **Turn this gap into a step (from a step).** When the gap text is not empty, the step shows **Turn this gap into a step**. It opens a small form pre-filled with a title ("Find a {helper type} for {short gap}"), the same goal, a sequence number just before the current step and a target date before the current step's date. The participant can edit everything, then save. Optionally (checked by default when the kind is Knowledge, Skill or Money) it also creates a Partner record with status To Contact, the suggested support type, and links to the new step. | BLBO-02 | M |
| BL-FR-20 | **Turn into a step (from an obstacle).** Every non-exempt obstacle offers **Turn into a step**. It behaves like BL-FR-19, using the obstacle's title, type and goal (for unplaced obstacles, the participant picks the goal). On save, the obstacle is anchored to the new step and its status becomes In Progress. | BLBO-02, BL-FR-05 | M |
| BL-FR-21 | **Link back.** The new step remembers the step or obstacle it came from and shows "Created to cover a gap in {step code}". Removing that step offers to remove the partner record it created, like FR-82 does today. | FR-82 | S |
| BL-FR-22 | **Plan check.** The AI plan check (AI-FR-09) and the no-AI plan check also flag a step whose gap kind is Knowledge, Skill or Money but which has no linked partner, no "find help" step before it and no open obstacle. The message reads: "Step S-014 is missing a skill. Who could help?" | AI-FR-09, BLBO-02 | S |
| BL-FR-23 | **Strengths and Gaps reuse.** FR-82's "cover with a plan step" uses the same create logic as BL-FR-19, so both behave the same. | FR-82 | C |

### D. Task stage: friction and breakdown

| ID | Requirement | Links | Priority |
| --- | --- | --- | --- |
| BL-FR-24 | **Break into smaller tasks.** When a task is Blocked, or has been In Progress past its due date, the task form and the task card show **Break into smaller tasks**. | BLBO-03 | M |
| BL-FR-25 | **How breaking down works.** The action opens a list editor with 2 to 5 rows, each with a title, a due date (by default spread evenly between today and the task's due date) and the task's owner and priority. On save, the new tasks are created under the **same step**, right after the original in list order. The original task is kept. The participant chooses one of two outcomes for it: **Keep it as a checklist parent** (stays, status unchanged) or **Replace it** (archived with the note "Split into T-041 to T-043"). Nothing is deleted. | BLBO-03 | M |
| BL-FR-26 | **First-action check.** Each new row shows the hint *"Could you start this in the next 15 minutes?"* and a tick box. If no row is ticked, a note suggests making the first one smaller. Saving is never blocked. | | C |
| BL-FR-27 | **What changed?** When a task moves from Blocked to any other status, the participant is asked one optional line: "What changed, or what will you do differently?" The answer is saved as the note of the progress log entry for that change. | Rule 6, BLBO-03 | S |
| BL-FR-28 | **Repeated blocks.** If the same task becomes Blocked a third time, the task card suggests **Break into smaller tasks** or **Add an obstacle on this task** (pre-filled with the blocker reason and kind). | AH-FR-39 | S |
| BL-FR-29 | **AI breakdown (with AI on).** When AI is on and the page switch allows it (V15), **Suggest smaller tasks** fills BL-FR-25's rows from the task, its step and the blocker reason. The participant approves or edits every row. Nothing is saved without approval. AI usage counts toward the existing monthly cap. | BRD 9.0.0 AI rules, BRD 15.0.0 | C |

### E. Inner blockers: the six chains, before the dream

| ID | Requirement | Links | Priority |
| --- | --- | --- | --- |
| BL-FR-30 | **Chain list.** The chain check measures: (1) settling for "realistic" (mediocrity), (2) fear of failure, (3) avoiding criticism, (4) misplaced priorities, (5) lack of persistence, (6) no clear vision. Each has original wording, a 1–5 scale (5 = barely holds me back), a page link and a weekly practice, like today's `assessmentContent.ts`. | D-17-3 | M |
| BL-FR-31 | **History kept.** Past assessments keep their knowledge and resources scores. The trend chart compares only chains that both assessments share and labels retired chains "No longer measured (moved to step gaps)". | BLBO-06 | M |
| BL-FR-32 | **Retired chains, new home.** The knowledge and resources practices move into the step gap flow (BL-FR-17 to BL-FR-19) and stop appearing as chain practices. | D-17-3 | M |
| BL-FR-33 | **Pre-dream card.** A participant with no dreams and no chain assessment sees the card from the target experience on the Dreams page and in the first-dream flow, with **Take the 2-minute check** and **Skip for now**. Skipping hides it for 30 days. It is never required. | BLBO-05 | M |
| BL-FR-34 | **Link to fears.** After a chain check where fear of failure or avoiding criticism scores 1 or 2, the result page offers **Write it down in the fear journal**, which opens a new fear entry. | V9 fear journal | S |
| BL-FR-35 | **Persistence and priorities practices.** Lack of persistence links to the Habits page and suggests one daily habit. Misplaced priorities links to the Today page and suggests choosing one A-rank task each day (letter rank, V35). | | S |
| BL-FR-36 | **Coach view.** Coaches see chain scores exactly as V9 D-6 allows today. V17 adds no new coach access. | Rule 14, D-6 | M |

### F. AI, Excel and API

| ID | Requirement | Links | Priority |
| --- | --- | --- | --- |
| BL-FR-37 | **Plan Builder risks.** The risk card's **For** list no longer offers "The whole dream". Risks must pick a step, or **A new step for this risk**, which creates one under a chosen goal when the plan is added. AI-suggested risks are asked to name a step. A suggestion without one is shown with an empty **For** that the participant must fill in before adding the plan. | PB-FR-24, PB-FR-25 | M |
| BL-FR-38 | **Plan assistant.** Obstacle changes proposed by the plan assistant must name a step or task. The server rejects a change without one, and the assistant is told why. | BRD 15.0.0 | M |
| BL-FR-39 | **AI prompts.** System prompts for plan building, the plan assistant and dream coaching state the placement rule and the outcome-only rule (D-17-1, D-17-2). The dream coach must not ask about obstacles while the dream is being written. | | M |
| BL-FR-40 | **Excel export.** The Obstacles sheet adds a last column, **Placed on** (Task, Step, or "Not placed"), with conditional formatting that highlights "Not placed" orange. Existing columns keep their order, so older workbooks still import. The Instructions sheet explains the rule. | Excel rules in `CLAUDE.md` | M |
| BL-FR-41 | **Excel import.** An obstacle row with a Step or Task ID is imported with derived parents. A row with only a Dream or Goal ID is imported as unplaced and counted in the import summary as "Imported, needs a step (N)", never skipped. A row whose IDs disagree (the task is not in that step) uses the most specific id and adds a warning. A backup is made first and the original workbook is never overwritten, as today. | BLBO-06 | M |
| BL-FR-42 | **API.** See "Data and API changes". Every new endpoint is user-scoped and added to `AccessControlSweepTests`. | Rule 13 | M |

## Non-functional requirements

| ID | Requirement |
| --- | --- |
| BL-NFR-01 | **No data loss.** The migration changes no obstacle's anchor, deletes no row and archives nothing. Every change is reversible by a documented SQL script. |
| BL-NFR-02 | **Data scoping.** Every pick list (steps, tasks, goals) and every new endpoint returns only the signed-in participant's records (business rule 13). Anchoring an obstacle to another user's step returns 404, as other lookups do today. |
| BL-NFR-03 | **Soft coaching.** No coaching hint, limit-word note or pre-dream card blocks saving. They can be dismissed and respect reduce-motion. |
| BL-NFR-04 | **Language.** All new text goes through `tr()` and has Khmer entries in `km.ts`, marked as first drafts for a Khmer speaker to review (as in `km-review-v15.csv`). The limit-word list (BL-FR-13) is English only in V17. |
| BL-NFR-05 | **Original wording.** Chain names, hints and coaching text are written in original words. No passages from the source books are copied into the UI (`CLAUDE.md`, Copyright and Source Use). |
| BL-NFR-06 | **Performance.** The roll-up counts (BL-FR-15) add no more than one query per page load and use the existing dashboard cache pattern (FR-30). The cache is evicted on writes to obstacles and tasks. |
| BL-NFR-07 | **Tests.** Unit tests for the anchor rule, derived parents, the exemption, the moving rule, Excel placement and chain trend comparison. Component tests for the placement picker, the unplaced list, Turn into a step, Break into smaller tasks and the pre-dream card. API contract snapshot entries for every changed DTO. Coverage stays at or above the BRD 12.0.0 target. |
| BL-NFR-08 | **Accessibility.** WCAG 2.1 AA for every new control. The roll-up count has a text label, not only an icon. |

## Data and API changes (for solution design)

**Schema**

| Migration | Change | Notes |
| --- | --- | --- |
| `V75__obstacle_anchor_consistency.sql` | Data fix only: for obstacles with a task, set `related_step_id` from the task. For obstacles with a step, set `related_goal_id` and `related_dream_id` from the step. Adds index `(user_id, related_step_id)` if missing | Changes only rows whose derived ids are null or disagree. A pre-check query (below) records how many rows will change. No anchor (step or task) is ever changed |
| `V76__step_gap_kind_and_origin.sql` | `vision_steps`: add `gap_kind VARCHAR(20)` (nullable, same values as `ObstacleType`), `covers_step_id BIGINT REFERENCES vision_steps(id) ON DELETE SET NULL`, `covers_obstacle_id BIGINT REFERENCES obstacles(id) ON DELETE SET NULL`, `created_partner_id BIGINT REFERENCES partners(id) ON DELETE SET NULL` | Additive and nullable. Supports BL-FR-18, BL-FR-19 to BL-FR-21 |
| `V77__task_block_count.sql` | `task_items`: add `blocked_count INTEGER NOT NULL DEFAULT 0` | BL-FR-28. Starts at 0 for existing tasks |
| `V78__chain_priorities_persistence.sql` | `chain_assessments`: add `priorities_score INTEGER` and `persistence_score INTEGER` (nullable). Make `knowledge_score` and `resources_score` nullable | Old rows keep their values (BL-FR-31). New rows fill the two new columns and leave the retired ones null. The service validates that the six current chains are present on new rows |

No column is dropped. `related_dream_id` and `related_goal_id` stay on `obstacles` as derived context (BL-FR-02) and for exempt obstacles (BL-FR-04).

**Entity and service**

- `Obstacle`: a `placement()` method returns TASK, STEP or NONE. An `isExempt()` method returns true for type Partner with any conflict or criticism field filled.
- `ObstacleService.create` and `update`: apply BL-FR-01, BL-FR-02 and BL-FR-04. `update` keeps an unplaced obstacle as it is unless the request changes the anchor (BL-FR-06).
- `VisionStepService` and `TaskItemService` moves: re-derive obstacle parents (BL-FR-08).
- `ChainAssessment`: two new fields. `ChainAssessmentService` validates the current six chains.

**API**

| Endpoint | Change |
| --- | --- |
| `POST/PUT /api/obstacles` | `relatedStepId` or `relatedTaskId` required (except exempt). `relatedDreamId` and `relatedGoalId` are accepted but ignored when an anchor is given. Responses add `placement` |
| `GET /api/obstacles?placement=UNPLACED` | New filter for BL-FR-05 |
| `POST /api/obstacles/{id}/convert-to-step` | BL-FR-20. Body: step fields, optional partner. Returns the new step, the updated obstacle and the optional partner |
| `POST /api/steps/{id}/gap-step` | BL-FR-19. Body: step fields, optional partner |
| `POST /api/tasks/{id}/split` | BL-FR-25. Body: rows, `originalOutcome` = `KEEP` or `REPLACE` |
| `PATCH /api/tasks/{id}/status` | Accepts an optional `changeNote` when leaving Blocked (BL-FR-27). Increments `blockedCount` when entering Blocked |
| `GET /api/dashboard` | Adds `unplacedObstacleCount` (BL-FR-07) and per-dream and per-goal blocker counts (BL-FR-15), or a new `GET /api/blocker-rollup?dreamId=` if the payload grows too large |
| `POST /api/chain-assessments` | New fields `prioritiesScore` and `persistenceScore`. `knowledgeScore` and `resourcesScore` are no longer accepted on new rows |

## Migration notes

**1. Before release (pre-check, read-only).** Run on staging and production and record the results in the release ticket:

```sql
-- Obstacles that will appear in "Not on a step yet" (excluding archived)
SELECT count(*) FROM obstacles
WHERE archived = false AND related_step_id IS NULL AND related_task_id IS NULL;

-- Of those, how many are Partner-type conflicts (exempt, BL-FR-04)
SELECT count(*) FROM obstacles
WHERE archived = false AND related_step_id IS NULL AND related_task_id IS NULL
  AND obstacle_type = 'PARTNER'
  AND (conflict_incident IS NOT NULL OR criticism_substance IS NOT NULL);

-- Rows V75 will correct (derived parents missing or disagreeing)
SELECT count(*) FROM obstacles o
JOIN vision_steps s ON s.id = o.related_step_id
JOIN goals g ON g.id = s.goal_id
WHERE o.related_goal_id IS DISTINCT FROM s.goal_id
   OR o.related_dream_id IS DISTINCT FROM g.dream_id;
```

**2. Back up.** Take a database snapshot before applying V75 to V78, as for every release (`docs/production_env.md`).

**3. Apply V75 (data fix).** It runs in one transaction. It writes the ids of the changed rows and their old `related_goal_id` and `related_dream_id` to a temporary audit table, `obstacle_anchor_fix_v75`, kept for 90 days, so the change can be reversed with one `UPDATE … FROM` script. That script is stored next to the migration as a comment.

**4. Apply V76 to V78 (additive).** No data is rewritten. V78 only relaxes NOT NULL on two columns and adds two nullable ones.

**5. Release order.** Backend first, so the server accepts both old-shaped and new-shaped requests during the deploy window: dream or goal only is still accepted for exempt obstacles, and rejected for others with a clear message. Then the frontend. Render deploys each service only when its folder changes and CI passes, so check that both services deployed.

**6. After release.** Participants with unplaced obstacles see the attention item (BL-FR-07). No email is sent. Coaches are told in release notes that dream-level obstacles now appear under "Not on a step yet" and need placing.

**7. Excel.** Workbooks exported before V17 import cleanly. Dream-only rows import as unplaced (BL-FR-41). Workbooks exported after V17 have the extra **Placed on** column, which import ignores.

**8. Rollback.** Frontend: redeploy the previous build. Backend: redeploy the previous build. V76 to V78 are additive and harmless to the old code, except that V78's nullable retired columns must be filled before rolling back: `UPDATE chain_assessments SET knowledge_score = 3, resources_score = 3 WHERE knowledge_score IS NULL` (documented neutral value, flagged in the rollback ticket). V75 can be reversed from its audit table.

**9. `CLAUDE.md`.** Update the two lines named in D-17-1 and D-17-2 in the phase 1 commit, after sign-off.

## Implementation phases

| Phase | Content | Requirements |
| --- | --- | --- |
| 1 | Placement rule and data fix: anchor rule, derived parents, exemption, moves, V75, placement picker, unplaced list, attention item, Excel export and import, `CLAUDE.md` update | BL-FR-01 to BL-FR-10, BL-FR-40 to BL-FR-42 |
| 2 | Outcome-only dreams and goals: hints, limit-word note, goal coaching question, roll-up counts, no obstacle action at dream or goal, AI prompt and Plan Builder and assistant changes | BL-FR-11 to BL-FR-16, BL-FR-37 to BL-FR-39 |
| 3 | Gaps become steps: gap question and kind, Turn this gap into a step, Turn into a step, link back, plan check, FR-82 reuse, V76 | BL-FR-17 to BL-FR-23 |
| 4 | Break it down: Break into smaller tasks, first-action hint, What changed?, repeated blocks, AI breakdown, V77 | BL-FR-24 to BL-FR-29 |
| 5 | Six chains: new list, history, retired chains, pre-dream card, fear link, practices, V78, Khmer review | BL-FR-30 to BL-FR-36 |

Each phase ends with backend and frontend builds and tests passing, and stops for confirmation (`CLAUDE.md` working rules 13 to 15).

## Acceptance criteria

| ID | Criterion |
| --- | --- |
| BLC-01 | Creating a Knowledge obstacle with only a dream id returns 400 "Put this blocker on a step or a task." |
| BLC-02 | Creating an obstacle on task T-031 (in step S-012, goal G-004, dream D-002) stores S-012, G-004 and D-002, even if the request sent dream D-001. |
| BLC-03 | Creating a Partner obstacle with the conflict worksheet filled and no anchor succeeds. |
| BLC-04 | An obstacle created before V17 with only a dream id still opens, can be marked Resolved and appears under **Not on a step yet**. It is not moved, archived or deleted by the release. |
| BLC-05 | **Move to a step** on that obstacle, choosing S-012, stores S-012, its goal and its dream, and removes it from the unplaced list. |
| BLC-06 | Moving step S-012 to goal G-005 updates every obstacle on S-012 and its tasks to G-005 and its dream. |
| BLC-07 | The dream form shows the outcome hint. Typing "if I can afford it" shows the limit-word note. The dream still saves with that text. |
| BLC-08 | Goal coaching in the app and the plan assistant no longer asks "What can block this goal?" and `CLAUDE.md` holds the new question. |
| BLC-09 | Goal G-004 with one open obstacle on S-012 and one Blocked task in S-013 shows "1 blocker · 1 blocked task" and links to the filtered Obstacles page. The goal row has no add-obstacle action. |
| BLC-10 | On step S-012 with gap "no statistics skills" and kind Skill, **Turn this gap into a step** creates step "Find a technical expert or trainer for no statistics skills" before S-012 in the same goal, and a To Contact partner of support type Technical linked to it. |
| BLC-11 | **Turn into a step** on an unplaced Money obstacle, choosing goal G-004, creates a new step, anchors the obstacle to it and sets the obstacle to In Progress. |
| BLC-12 | A Blocked task split into three rows with **Replace it** creates three tasks under the same step, right after the original, and archives the original with "Split into …". Nothing is deleted. |
| BLC-13 | Moving a task from Blocked to In Progress with the note "Got the data from Dara" saves a progress log entry with that note. |
| BLC-14 | A new chain check asks the six chains of BL-FR-30. A participant's older check still shows its knowledge and resources scores, marked as no longer measured, and the trend compares only the shared chains. |
| BLC-15 | After V75, the number of obstacles with a step or task equals the number before (from the pre-check). Only derived goal and dream ids changed, and every change is in `obstacle_anchor_fix_v75`. |
| BLC-16 | Importing a pre-V17 workbook with three dream-only obstacle rows shows "Imported, needs a step (3)" and creates three unplaced obstacles. The original workbook file is unchanged and a backup exists. |
| BLC-17 | The exported Obstacles sheet has a **Placed on** column, with "Not placed" highlighted orange. |
| BLC-18 | A Plan Builder risk with **For** left empty cannot be added until a step or **A new step for this risk** is chosen. |
| BLC-19 | A participant with no dreams and no chain check sees the pre-dream card. **Skip for now** hides it, and they can create a dream without taking the check. |
| BLC-20 | A user cannot anchor an obstacle to, convert to a step on, or split, another user's record. `AccessControlSweepTests` covers `convert-to-step`, `gap-step` and `split`. |

## Assumptions, dependencies and risks

### Assumptions

- Most existing dream-level and goal-level obstacles are plan gaps, which a participant can place on a step in a minute or less.
- Participants accept that a goal must have at least one step before a blocker can be recorded for it.
- The chain list in BL-FR-30 is confirmed by the product owner against the source (Q-01).

### Dependencies

- v0.1 FR-05 and AI-FR-09 (BRD 9.0.0) for step resources and the plan check.
- FR-82 (BRD 8.1.0) for the cover-with-a-step pattern.
- PB-FR-23 to PB-FR-25 (BRD 11.0.0) for Plan Builder risks.
- AH-FR-35, AH-FR-39 and AH-FR-41 (BRD 15.0.0) for obstacle helpers. The V15 page switches gate BL-FR-29.
- FR-71 (BRD 8.0.0) for the chain assessment.
- The dashboard cache (FR-30) for roll-up counts.

### Risks

| Risk | Impact | Mitigation |
| --- | --- | --- |
| Participants with many unplaced obstacles feel nagged | Annoyance, ignored list | No email, one attention item, actions in one click (BL-FR-05, BL-FR-07) |
| Placement feels like extra work when a goal has no steps yet | Blockers not recorded at all | **Add a step first** shortcut (BL-FR-16) and **A new step for this risk** (BL-FR-37) |
| Partner conflicts forced onto steps they don't fit | Wrong data | Exemption (BL-FR-04) |
| The limit-word note feels judgmental | Lower trust | Soft wording, dismissible per record, never blocks (BL-NFR-03) |
| Changing the six chains breaks the trend view | Confusing history | Compare shared chains only, label retired ones (BL-FR-31) |
| AI still suggests dream-level risks | Rule bypassed | Server-side rule (BL-FR-01, BL-FR-38), prompt change (BL-FR-39) |
| Chain names are copied from the books | Copyright | Original wording (BL-NFR-05) |

## Open questions

- [ ] **Q-01** Does the chain list in BL-FR-30 match the method as the product owner understands it? The research note in Appendix A was not checked against the books for this draft. (Draft: use the Appendix A list.)
- [ ] **Q-02** Should Partner conflicts be exempt from placement (BL-FR-04), or should they also sit on a step? (Draft: exempt.)
- [ ] **Q-03** Should the pre-dream chain check stay optional (BL-FR-33), or be required for cohort participants under V9? (Draft: optional for everyone.)
- [ ] **Q-04** For **Break into smaller tasks**, should the default for the original task be **Keep** or **Replace**? (Draft: Keep.)
- [ ] **Q-05** Should the roll-up count include obstacles on archived steps? (Draft: no.)
- [ ] **Q-06** Is a Khmer limit-word list wanted in a later version? (Draft: revisit after the Khmer review.)
- [ ] **Q-07** Should the Partners page "blocked tasks that need partners" list (in `CLAUDE.md`) also show steps with a Knowledge, Skill or Money gap and no partner (BL-FR-22)? (Draft: yes, Should, phase 3.)

## Sign-off

| Role | Name | Decision | Date |
| --- | --- | --- | --- |
| Sponsor | | | |
| Product owner | | | |
| Lead coach | | | |

## Sources

- `CLAUDE.md`: method hierarchy, Entity Relationships, AI-Like Coaching Behavior, Partner Management page, business rules 6, 7, 12, 13 and 14, Excel rules, working rules.
- BRD 8.0.0 (FR-71), BRD 8.1.0 (FR-79 to FR-84), BRD 9.0.0 (v0.1 FR-05, AI-FR-09, D-6), BRD 11.0.0 (PB-FR-23 to PB-FR-25), BRD 15.0.0 (AH-FR-35, AH-FR-39, AH-FR-41), BRD 16.0.0 (document format).
- Code at commit `87a40fb`: entities `Obstacle`, `VisionStep`, `TaskItem`, `ChainAssessment`, `FearEntry`; services `ObstacleService`, `ObstacleSupportService`, `ai/PlanBuilderDraftService`, `ai/PlanAssistantService`; `excel/ExcelService`; migrations V2, V10, V27, V31 to V33, V44, V57, V71; frontend `pages/ObstaclesPage.tsx`, `pages/DreamsPage.tsx`, `components/vision-map/VisionMapTree.tsx`, `components/plan-builder/HelpersStage.tsx`, `components/ai/plan-assistant/sectionHelp.ts`, `utils/assessmentContent.ts`.
- Appendix A: the original V17 research note on where the method places blockers.

---

## Appendix A – Background research note (original text, unchanged)

In Steven K. Scott’s framework—which he calls **Dream Conversion** (in *Simple Steps to Impossible Dreams*) and **Vision Mapping** (in *Mentored by a Millionaire* and *The Richest Man Who Ever Lived*)—obstacles, challenges, and blockers are **not placed at the Dream stage**. Instead, Scott splits blockers into two distinct categories and handles them at specific stages of the planning hierarchy.

### 1. Where Blockers Are Defined in the Hierarchy

| Stage in Scott's System | Role in the Plan | How Obstacles & Blockers Are Handled |
| --- | --- | --- |
| **1. Vision / Dream** | The big-picture, sensory description of what you want to achieve ("shoot for the moon"). | **Strictly excluded.** Scott instructs you *never* to let current limitations, lack of money, or obstacles shrink your dream. |
| **2. Goals** | The major milestones required to bring the vision to life. | **Not yet mapped as blockers.** Goals define *what* must be achieved, not the friction in the way. |
| **3. Steps** | The major phases or strategic actions needed to hit each goal. | **Primary stage for defining external blockers.** When mapping steps, you identify what you *don't know* and what resources/skills you *lack*—treating them as signals to recruit mentors, partners, or outside help. |
| **4. Tasks** | The smallest, daily executable actions with specific deadlines. | **Primary stage for resolving friction.** Complex steps are broken down into simple tasks specifically to eliminate overwhelm, procrastination, and execution roadblocks. |

---

### 2. How Steven K. Scott Defines and Categorizes Blockers

Rather than treating every problem the same way, Scott divides blockers into **Internal Blockers** (which must be cleared *before* you start) and **External/Tactical Blockers** (which are solved *inside* the Steps and Tasks):

#### A. Before the Dream Stage: "The Six Heavy Chains" (Internal Blockers)

In *Simple Steps to Impossible Dreams*, Scott identifies six psychological and behavioral blockers that stop people before they even build a Vision Map:

* **Programming for mediocrity** (settling for "realistic" goals instead of impossible dreams).
* **Fear of failure** (the strongest chain, which stops people from even trying).
* **Avoidance of criticism** (letting critics talk you out of a dream).
* **Misplaced priorities, lack of persistence, and lack of a clear vision**.

#### B. At the "Step" Stage: "Lack of Know-How" & Resource Gaps (Strategic Blockers)

When you convert a **Goal** into **Steps**, you inevitably hit a wall where you do not know how to do something or lack the money, time, or skills to do it.

* **How to define it:** Scott says to treat a lack of knowledge or resources not as a "stop sign," but as a **green light to go outside yourself**.
* **The Action:** At the **Step** level, you explicitly write down what expertise or resource is missing, and make **recruiting a mentor, partner, or advisor** one of your formal steps. As he notes in *Mentored by a Millionaire*, *"Every skill we would ever need to achieve any of our dreams is possessed by someone else"*.

#### C. At the "Task" Stage: Complexity & Overwhelm (Execution Blockers)

If a **Step** feels blocked or impossible to start, Scott defines the problem as **insufficient breakdown**.

* **How to define it:** A step is only a "blocker" because it is still a complex project rather than a single physical action.
* **The Action:** You break the complex step down into **simple, bite-sized tasks** (each with a realistic deadline) until the next task is so small that you can execute it immediately without resistance. When unexpected setbacks happen during tasks, Scott applies Thomas Edison's rule of **creative persistence**—analyzing what failed, cutting losses on wrong decisions quickly, and adjusting the next task.

---

### 3. Practical Summary: How to Apply This to Your Own Map

1. **Dream & Goal Stages:** Write your Vision and Goals with **zero mention of obstacles**. Only define the desired outcome.
2. **Step Stage:** List the major milestones to reach a goal. Next to each step, ask: *"What skill, money, or knowledge am I missing here?"* Turn that gap into a step to find a mentor, partner, or resource.
3. **Task Stage:** Break each step into small, concrete tasks with due dates. If you feel stuck or blocked on a task, break that task into even smaller micro-tasks until it is easy to start.