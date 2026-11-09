# BRD – AI Assistant and Helpers on the Modules, Assessment, Obstacles and Communication Pages

Nov 9, 2026

## Document control

This is draft 15.0.0. It keeps every requirement in BRD 14.0.0 (Sign in with Google), BRD 13.0.0 (Java Code Quality to Effective Java Practice), BRD 12.0.0 (Test Coverage to 80%), BRD 11.0.0 (AI Plan Builder Improvements), BRD 10.0.0 (Security Hardening to the OWASP Top 10), BRD 9.0.0 (Dream Conversion Program with Claude AI Integration) and earlier versions. Where this BRD is silent, the earlier BRDs still apply.

V15 brings page-aware AI help to four pages that today have little or none: **Modules**, **Assessment**, **Obstacles** and **Communication**. It reuses the pattern that V11 built for the Plan page (a side-panel assistant that knows what is open, quick actions, editable change cards, and help that still works without AI) and adds a small set of task-specific helpers on each page. The consent, safety, privacy and budget rules from V9 and V10 stay as they are.

| Item | Value |
| --- | --- |
| Document | Business Requirements Document (BRD) – page AI assistant and helpers addendum |
| Version | 15.0.0 – Draft |
| Parent documents | BRD 11.0.0 (6 Nov 2026), BRD 9.0.0 (2 Nov 2026) |
| Business owner | To be confirmed |
| Product owner | To be confirmed |
| Approvers | Sponsor, Product owner, AI governance lead, Lead coach |

| Version | Change | Author |
| --- | --- | --- |
| 15.0.0 | First draft: current state at commit `a9889e5`, page assistant (AH-FR-01 to AH-FR-12), helpers for Modules (AH-FR-13 to AH-FR-22), Assessment (AH-FR-23 to AH-FR-30), Obstacles (AH-FR-31 to AH-FR-42) and Communication (AH-FR-43 to AH-FR-54), measurement (AH-FR-55 to AH-FR-58), non-functional requirements, phased plan and acceptance criteria | BA |

## Executive summary

The method in `CLAUDE.md` runs from a vague dream to a reviewed plan: Vision Area → Dream → Goal → Step → Task → Partner/Resource → Schedule → Progress → Review. V9 and V11 put strong AI help on the first half of that path (dreams, goals, steps and tasks). The pages that carry the second half, and the learning program around it, have much less:

- **Modules.** The module list has no AI at all. Inside a module, the V9 *Module helper* can start, draft or give feedback, but its questions cannot be answered in place, a draft cannot be refined, and the ten modules whose work is done on another page get almost no help.
- **Assessment.** *Explain my result* names one module to focus on. Nothing helps a participant understand a statement while answering, and nothing turns a weak chain into a concrete practice.
- **Obstacles.** The page has no AI. The only help is a fixed "suggested partner" by obstacle type. A participant who writes "I keep putting this off" gets no help to name the type, find the root cause, think of solutions, match one of their own partners, or turn the obstacle into a next task.
- **Communication.** *Draft with AI* writes a whole outreach message, and a pitch coach exists. But the page has twelve structured fields (hook, word picture, objections, social proof and more) with no help on any single field, no check that the message is clear and not demanding, no follow-up draft for a message that got no reply, and no help reading a partner's reply.

On all four pages the general Dream Coach can be opened, but it does not know which obstacle or message is open, cannot see the unsaved form, cannot propose a change, and on the Modules list and Assessment it shows only generic quick actions.

V15 adds two layers to each page:

1. **A page assistant**: one side panel, shared by the four pages, that knows the page, the open item and the unsaved form. It offers three quick actions that fit, explains in plain words, and proposes changes as editable cards. A card fills the form on screen. **The participant always saves; Claude never does** (AI-FR-04).
2. **Page helpers**: a few one-click helpers placed where the work happens, for example *Which module next?*, *Practise this chain*, *Find the root cause*, *Turn this into a task*, *Check my message* and *Draft a follow-up*.

Every helper also works without AI, using the program's own prompts and rules, so no page loses a feature when AI is off, over budget or not consented.

## Business objectives

| ID | Objective | Measure |
| --- | --- | --- |
| AHBO-01 | Keep participants moving through the program | Share of participants who complete the next module within 7 days of finishing one rises from the pre-V15 baseline (measured in Phase 1) |
| AHBO-02 | Turn assessment results into action | At least 40% of participants who see their result start a suggested practice or open the suggested module within 7 days |
| AHBO-03 | Fewer obstacles left open with no plan | Share of Open obstacles with a root cause, a solution or a linked task rises by 30% after 4 weeks of pilot use |
| AHBO-04 | More partner requests sent and followed up | Messages moved from Draft to Sent, and Sent messages followed up by their follow-up date, both rise after 4 weeks |
| AHBO-05 | Useful suggestions, inside the budget | At least 50% of page assistant and helper suggestions applied (as-is or edited), average rating 4.0 or more, and V15 spend inside the share of the $20 cap set in AH-NFR-02 |

These extend V9 objectives BO-04 (fewer stalled plans), BO-05 (more partners), BO-08 (50% of AI suggestions accepted), BO-09 (rating) and BO-12 (cost).

## Scope

### In scope

- A shared, page-aware assistant panel on `/modules`, `/modules/:number`, `/assessment`, `/obstacles` and `/communication`.
- Page helpers on those pages, listed in requirement sections C to F.
- Program help without AI for every quick action and helper (prompts, guiding questions and rule-based checks).
- Server checks on every AI proposal, ratings, applied/edited/discarded counts and admin metrics, as in V11.
- English and Khmer for all new text and AI answers.
- Phone layout: the panel and helpers work at phone width, in line with the phone work on the Vision Map and Tasks pages (commit `271c4b2`).

### Out of scope

- Claude saving, sending or changing anything without the participant's action (AI-FR-04 stays).
- Sending messages to partners. Messages stay copy-and-send (AI-FR-17). No email, SMS or chat integration.
- AI choosing or changing assessment scores. The participant's self-rating stays their own (AH-FR-24).
- Unlocking a module from an AI draft. Only the participant's own save counts (V9 FR-07 change, AC-09).
- Coach-facing AI (AI-FR-18 to AI-FR-21) and admin content AI (AI-FR-22, AI-FR-23), except where this BRD says so.
- Assistant panels on pages other than the four named here. The Plan page and Build my plan keep the V11 plan assistant.
- Raising the $20 monthly AI budget (D-3). V15 must fit inside it.

## Current state (9 Nov 2026, commit `a9889e5`)

| Page | What exists today | Gap |
| --- | --- | --- |
| Dream Coach (all pages) | Chat on every screen (`DreamCoachLauncher`, AI-FR-01) with three quick actions chosen by URL (`aiFocus.ts`). Context by topic in `AiContextBuilder` (fears and obstacles for the Obstacles page, the module's journals for modules 2, 3 and 10) | Does not know the open obstacle or message, cannot see the unsaved form, cannot propose a change. `/modules` and `/assessment` get only the generic "Help me start / What should I do next? / Explain this page". `/obstacles` shares the Journal's fear-focused actions, and `/communication` shares the Partners actions |
| Modules list (`/modules`) | 15 modules in three stages (Break the chains, Launch, Boost). Finishing one unlocks the next (FR-72). Chain scores read for display | **No AI.** Nothing says which unlocked module would help most now, or why |
| Module detail (`/modules/:number`) | *Module helper* (`ModuleHelpCard`, `POST /api/ai/modules/{number}/help`, feature `MODULE_HELP`): **Help me start** (up to 3 questions and an example about someone else), **Draft my answer** (fills the form, never saves), **Feedback on my answer** (feedback and up to 3 improvements). Each module has its own focus (AIB-01 to AIB-15). Module 11 also shows the pitch coach | Questions are shown one by one but **cannot be answered in place** and used for the draft. A draft cannot be **refined** (shorter, more specific, more in my words). Ten of the 15 modules have no form (`completion_rule` other than `FORM`: modules 2, 4, 5, 8 to 10 and 12 to 15 link to another page) and get only *Help me start*; only modules 1, 3, 6, 7 and 11 have a form. No check of readiness before saving |
| Assessment (`/assessment`) | Six-chain self-assessment, scored 1 to 5, with retake and baseline comparison (FR-71). **Explain my result** (`AssessmentExplainCard`, `POST /api/ai/assessment/explain`, feature `ASSESSMENT_EXPLAIN`) explains the latest result and names one module | No help **understanding a statement** while answering. No **per-chain action** (one small practice per low chain). No trend view in words across more than two takes. No reminder logic for a retake |
| Obstacles (`/obstacles`) | Full form: type, severity, solution, required partner, root cause, creative alternatives, "how you cracked it last time", conflict worksheet, expectation release, resolve checklist, criticism mining, turn into task. Rule-based suggested partner by type (`partnerSuggestion.ts`, the `CLAUDE.md` mapping) | **No AI.** No help to classify an obstacle, find its root cause, suggest solutions, match the participant's own partners, find a similar solved obstacle, or prepare a task. Blocked tasks with a blocker reason are not offered as obstacles |
| Communication (`/communication`) | Twelve message fields (audience, purpose, subject, hook, word picture, problem, request, objections, social proof, value comparison, benefit, expected outcome, call to action). **Generate message** (rule template, two tones). **Draft with AI** (`POST /api/ai/daily/outreach-draft`, feature `OUTREACH_DRAFT`, name and role only, AI-NFR-04). Pitch coach (`PITCH_COACH`). Statuses Draft, Sent, Followed Up, Replied, Closed, with a follow-up date | No help **per field**. No **message check** (clear ask, length, demanding tone). No **follow-up draft** when a Sent message passes its follow-up date. No help to **read a reply** and choose the next step. *Draft with AI* replaces the whole message instead of filling the fields |
| Shared AI services | Consent and availability (`AiAccessService`), structured requests (`AiStructuredRequests`), streaming chat, safety and crisis handling (`AiSafety`), usage log with rating, V11 outcome counts (migration V63), plan assistant panel and change cards (`components/ai/plan-assistant`, `PlanAssistantService`) | The V11 panel and change cards are built for plan sections only |

## Target experience

A participant opens one of the four pages. A **Help** button sits in the page header, and each main area (a module exercise, an assessment statement, an obstacle form, a message field) has a small **Help with this** button.

- On a wide screen the assistant opens beside the page, so the form stays visible. On a phone it opens full screen, with a clear way back.
- The panel says what it is helping with, for example "Helping with: Obstacle O-004 – I keep putting off the literature search" or "Helping with: the Hook of your message to your mentor".
- Three quick actions fit that area. The participant can also type a question.
- When Claude proposes a change, it appears as a card: the field, the suggested text and **Apply** or **Dismiss**. **Apply** fills the form on screen. Nothing is saved until the participant presses the page's own **Save**.
- Without AI, the same panel opens with the program's own help: what the area needs, guiding questions and rule-based checks, and a line saying why AI help is not available.

Page helpers are plain buttons in the page itself, for actions people use often, so they do not have to open the panel.

## Functional requirements

Priority: **M** (Must) ships in V15, **S** (Should) ships unless the sponsor defers it, **C** (Could) is a later improvement.

### A. Page assistant (shared by the four pages)

| ID | Requirement | Traces to | Priority |
| --- | --- | --- | --- |
| AH-FR-01 | The system shall offer a **page assistant** panel on `/modules`, `/modules/:number`, `/assessment`, `/obstacles` and `/communication`. On wide screens it opens beside the content; on a phone it opens full screen with a clear way back. | AI-FR-01, PB-FR-41 | M |
| AH-FR-02 | Each page shall have a **Help** button for the whole page, and each main area shall have a **Help with this** button that opens the assistant focused on that area. The areas are: each module exercise; each assessment statement and the result; the obstacle form and each obstacle in the list; each message field, the whole message and each message in the list. | AI-FR-01, PB-FR-42 | M |
| AH-FR-03 | The panel shall always show what it is helping with, and follow the participant when they open another item, with a choice to keep the earlier topic. | PB-FR-43 | M |
| AH-FR-04 | Each area shall offer three quick actions that fit it, listed in sections C to F, plus a free question. | AI-FR-02, PB-FR-44 | M |
| AH-FR-05 | The assistant shall see the **unsaved form** of the open item (what the participant has typed but not saved), as data, so help fits what is on screen. | AI-FR-01 | M |
| AH-FR-06 | The assistant may propose changes as **change cards**: one field, the proposed value, a short reason, and **Apply** or **Dismiss**. **Apply** fills the form only; the page's own Save stores it through the normal create and update rules. A card that would create a new record (a task, an obstacle, a message) opens the normal create form already filled. | AI-FR-04, PB-FR-46 | M |
| AH-FR-07 | Every proposal shall be checked on the server before it is shown: allowed fields for that page only, allowed values for enums (obstacle type, severity, message status), dates within the parent's date (PB-FR-15), owner "Me" or a role (PB-FR-18), and references only to the participant's own records. Anything that fails is dropped. | PB-FR-47, PB-NFR-04 | M |
| AH-FR-08 | Every answer shall carry the "AI suggestion" label and a "Based on ..." reason naming the page, item and records used. | AI-FR-05, PB-FR-48 | M |
| AH-FR-09 | Every answer and card set can be rated thumbs up or down with an optional comment (AI-FR-06). Applied, edited and dismissed cards are counted with no text, under the feature names in AH-FR-55. | AI-FR-06, PB-FR-34, PB-FR-35 | M |
| AH-FR-10 | When AI is off, unavailable, over a limit or not consented, the panel shall still open and show the program's own help for the area: what it needs, guiding questions, and the rule-based checks named in sections C to F. It says why AI help is not available. | AI-NFR-02, PB-FR-49 | M |
| AH-FR-11 | On these pages the Dream Coach launcher shall open the page assistant instead of a second chat, so only one assistant is on screen. Conversations are kept as Dream Coach conversations tagged with the page and item, so the history, deletion and export rules of AI-FR-25 apply. | AI-FR-25, PB-FR-50 | S |
| AH-FR-12 | The Dream Coach quick actions for these pages (`aiFocus.ts`) shall be replaced by the page-specific ones in sections C to F, so `/modules` and `/assessment` stop showing generic actions and `/obstacles` stops sharing the Journal's. | AI-FR-02 | M |

### B. What the assistant may read on each page

| Page | Sent to Claude | Never sent |
| --- | --- | --- |
| Modules list | Module numbers, titles, stages and status; latest and first chain scores; counts of dreams, goals, steps and partners | Journal text, unless the participant's AI consent covers it and the module is 2, 3 or 10 (existing rule) |
| Module detail | The module's focus, field labels and the unsaved rows; the participant's records as today's `MODULE_HELP` sends them | Other modules' saved answers, unless needed for the module's focus (for example module 15 reads the plan's readiness) |
| Assessment | The six scores of every take with dates; module titles and status | Any free text from other pages |
| Obstacles | The open obstacle's fields (saved and unsaved), its linked dream, goal, step or task titles and dates, and the participant's other obstacles' type, status and solution | Partner names and contact details (matching is done on the server, AH-FR-35). The conflict worksheet's "your own reaction" field, unless ticked for that request (AH-FR-40) |
| Communication | The open message's fields (saved and unsaved), the partner's **name and role only** (existing outreach rule), the linked dream, goal or task titles, and a pasted reply when the participant uses AH-FR-51 | Partner email and phone. Other messages' bodies |

### C. Modules

**Modules list (`/modules`) quick actions:** "Which module should I do next?", "What is this program about?", "How far have I come?"

| ID | Requirement | Traces to | Priority |
| --- | --- | --- | --- |
| AH-FR-13 | **Which module next?** On the Modules list, a helper shall recommend one unlocked, not completed module, with a two-sentence reason that names the participant's own data (for example "Your lowest chain is *Fear of failure* (2 of 5)" or "You have 3 dreams but no plan yet"). It links to the module. | AIB-04 to AIB-15, AHBO-01 | M |
| AH-FR-14 | Without AI, **Which module next?** shall use a rule: the next module in program order, unless the latest assessment has a chain scored 1 or 2 whose module is unlocked and not completed, in which case that module. The reason names the rule. | AI-NFR-02 | M |
| AH-FR-15 | **How far have I come?** shall give a short summary of the journey: modules done per stage, the change in chain scores since the first assessment, and the next stage to reach. | AHBO-01 | S |
| AH-FR-16 | **Answer in place.** In *Help me start*, each question shall have an answer box. Answers are kept for the session and sent with **Draft my answer**, so the draft builds on what the participant just said. Nothing is saved unless the participant saves the exercise. | AI-FR-03, PB-FR-08, PB-FR-09 | M |
| AH-FR-17 | **Refine a draft.** After **Draft my answer**, the participant can ask for **Shorter**, **More specific**, **More in my words** or **Another version**, or write a short note. Rows the participant has edited are kept unchanged. | PB-FR-19, PB-FR-20 | M |
| AH-FR-18 | **Explain this module.** The assistant shall explain the module's idea in plain words, why it matters in the method, and one example about a made-up person, using the program's own module text (never book text). | AI-NFR-11 | M |
| AH-FR-19 | **Modules done elsewhere.** For a module whose work happens on another page (completion rule other than `FORM`), the helper shall say exactly what to do and where, check whether it is already done from the participant's data, and offer a button to that page. Examples: module 2 "log one fear in your Journal", module 9 "add one partner", module 13 "date your next step". | FR-72, AIB-02 to AIB-15 | M |
| AH-FR-20 | **Readiness check.** Before saving a form module, **Check before I save** shall list any field left empty or too short and say, in one line each, how to make the answer more useful. Without AI it lists empty fields only. | AI-FR-09 pattern | S |
| AH-FR-21 | Module helper drafts shall still never save or unlock a module (AC-09), and shall still never invent facts about the participant's life (V9 rule). | AI-FR-04, AI-NFR-10 | M |
| AH-FR-22 | **Module detail quick actions:** "Explain this module", "Help me start", "Check my answer". Module 11 keeps the pitch coach. | AI-FR-02 | M |

### D. Assessment

**Quick actions:** "What does this statement mean?", "Explain my result", "What should I practise this week?"

| ID | Requirement | Traces to | Priority |
| --- | --- | --- | --- |
| AH-FR-23 | **Explain a statement.** While answering, each of the six statements shall have a **What does this mean?** link showing a plain explanation and two short examples, one of a low and one of a high score, about made-up people. This text is part of the program content, written once in English and Khmer, so it needs **no AI** and costs nothing. | FR-71, AI-NFR-02 | M |
| AH-FR-24 | The assistant shall **never suggest, change or comment on a score before the assessment is saved**. If asked "what should I score?", it explains the statement and says the score is the participant's own judgement. | Self-assessment validity | M |
| AH-FR-25 | **Explain my result** (existing) stays, and shall also explain each chain scored 1 or 2 in one sentence, and the trend across all takes, not only the first and latest. | FR-02 change | M |
| AH-FR-26 | **Practise this chain.** For each chain scored 3 or less, a helper shall propose one small practice for the next 7 days, tied to an app action: for example Fear of failure → "log one fear and plan the smallest safe test" (Journal); Lack of resources → "add one partner who could supply a gap" (Partners); Unclear vision → "rewrite your top dream's success definition" (Dreams). Each practice is a card with **Do it now** (opens that page) and, where the app supports it, **Add as a habit** (opens the normal habit form, filled). | AIB-01 to AIB-06, AHBO-02 | M |
| AH-FR-27 | Without AI, **Practise this chain** shall offer the same app action for each chain from a fixed program list, without the personal wording. | AI-NFR-02 | M |
| AH-FR-28 | **Retake reminder.** When the latest take is 4 weeks old or more and at least one module has been completed since, the page shall suggest a retake. This is a rule, with no AI. | FR-71 | S |
| AH-FR-29 | After a retake, the assistant shall offer **What changed?**: a short, encouraging comparison with the previous take that links each change to modules completed in between. | FR-71, AHBO-02 | S |
| AH-FR-30 | Assessment help shall not label people (for example "you are a fearful person"); it describes scores and actions only. | AI-NFR-12 | M |

### E. Obstacles

**Quick actions for one obstacle:** "Help me understand this obstacle", "What could I try?", "Who could help?"
**Quick actions for the list:** "Which obstacle should I tackle first?", "Do you see a pattern?", "Which blocked tasks need an obstacle?"

| ID | Requirement | Traces to | Priority |
| --- | --- | --- | --- |
| AH-FR-31 | **Classify.** From the title and description, Claude shall propose the obstacle type (knowledge, skill, time, money, motivation, partner, system, decision, other) and severity (low, medium, high, critical), each with a one-line reason, as change cards. | `CLAUDE.md` Obstacle, AIB-02 | M |
| AH-FR-32 | **Find the root cause.** A helper shall ask up to three "why" questions, one at a time, answered in place, then propose a root cause as a change card for the Root Cause field. | AI-FR-03, PB-FR-08 | M |
| AH-FR-33 | **What could I try?** Claude shall propose up to three solutions, each with a first small step, as cards that fill the Solution or Creative Alternatives field. Solutions must respect the obstacle's type (for example a money obstacle gets funding and cost ideas, not study advice). | AIB-05, AIB-06 | M |
| AH-FR-34 | **Who could help?** The page shall keep the rule-based partner type from the `CLAUDE.md` mapping (knowledge gap → mentor or expert; skill gap → technical expert or trainer; time gap → delegate or assistant; money gap → financial partner or sponsor; motivation gap → accountability partner; decision gap → advisor). | `CLAUDE.md` partner rules | M |
| AH-FR-35 | **Match my partners.** The server shall find the participant's own partners whose role or support type fits that partner type and offer them as **Set as required partner** (fills the Required Partner field). Matching is done on the server; partner names and contact details are not sent to Claude. When no partner fits, the helper offers **Add a partner** (opens the partner form with the suggested support type and status To Contact). | PB-FR-23, AI-NFR-04, BO-05 | M |
| AH-FR-36 | **Ask for help.** When the obstacle has a required partner, a helper shall offer **Draft a message to them**, opening the Communication page with a new message filled from the obstacle (partner, audience, purpose, problem, request). The participant edits and saves it there. | AI-FR-17, `CLAUDE.md` ideal flow 10–11 | M |
| AH-FR-37 | **Turn this into a task.** Claude shall propose one next task (title, owner "Me", due date within the linked step's date, priority) that moves the obstacle forward. **Apply** opens the normal task form, filled and linked to the obstacle's step. | `CLAUDE.md` rule 5, PB-FR-14 | M |
| AH-FR-38 | **Solved before.** The server shall find up to three of the participant's resolved obstacles of the same type, and the assistant may say how they were solved, so the participant can reuse it in "How you cracked it last time". | Existing field, AHBO-03 | S |
| AH-FR-39 | **Blocked tasks without an obstacle.** On the list, a helper shall show tasks that are Blocked with a blocker reason but no linked obstacle, and offer **Make this an obstacle** (opens the obstacle form, filled from the task and its blocker reason). This list is a rule, with no AI. | BR-6, `CLAUDE.md` ideal flow 9 | M |
| AH-FR-40 | **Conflict and criticism help.** For the conflict worksheet and criticism mining, Claude may help separate the useful point from the emotion, describe the other person's likely view, and suggest one calm next action (AIB-03). The private "your own reaction" field is sent only when the participant ticks it for that request. Crisis wording triggers the V9 safety response (AI-NFR-09). | AIB-03, AI-NFR-09, BR-43 | S |
| AH-FR-41 | **Which obstacle first?** For the list, the assistant shall rank Open obstacles by severity, the number of blocked tasks they hold up, and how long they have been open, with a reason for the top three. Without AI, the list is sorted by the same rule with no reasons. | AIB-13, AHBO-03 | S |
| AH-FR-42 | Obstacle help shall respect the existing rule that the resolve checklist and worksheet never block saving, and shall never mark an obstacle Resolved itself. | Existing obstacle rules | M |

### F. Communication

**Quick actions for one field:** "Improve this", "Give me two other versions", "Why does this matter?"
**Quick actions for the whole message:** "Check my message", "Make it shorter", "Make it warmer" (or "Make it more formal")
**Quick actions for the list:** "Who should I follow up with?", "Which message should I send first?"

| ID | Requirement | Traces to | Priority |
| --- | --- | --- | --- |
| AH-FR-43 | **Help per field.** Each message field (hook, word picture, problem, request, objections and answers, social proof, value comparison, benefit to partner, expected outcome, call to action, subject) shall have **Help with this**, which explains what the field is for and proposes a value or two alternatives as change cards. | AI-FR-17, `CLAUDE.md` communication structure | M |
| AH-FR-44 | **Fill from a short intent.** The participant can write one sentence ("ask my mentor to review my concept note") and Claude shall propose values for the empty fields as cards. Fields the participant has written are not changed. This works beside the existing **Draft with AI**, which still writes the full message. | AI-FR-17, PB-FR-20 | M |
| AH-FR-45 | **Check my message.** A helper shall check the message and list issues, each with a fix card: no clear single request, request not specific (what, when, how long), no benefit to the partner, demanding or pushy tone (`CLAUDE.md`: "avoid sounding demanding"), too long for the channel, and missing call to action. | `CLAUDE.md` communication rules, AI-FR-09 pattern | M |
| AH-FR-46 | Without AI, **Check my message** shall run rule checks only: empty Request, empty Benefit to Partner, no call to action, and a body over 250 words. | AI-NFR-02 | M |
| AH-FR-47 | **Tone and length.** The participant can ask to make the message shorter, warmer, more formal, or in Khmer or English. The result is a card for the message body; it does not overwrite the structured fields. | AI-NFR-16 | M |
| AH-FR-48 | **Follow-up list.** The page shall show messages that are Sent or Followed Up whose follow-up date has passed with no reply, as a rule with no AI. | `CLAUDE.md` weekly review: "Which partner needs follow-up?" | M |
| AH-FR-49 | **Draft a follow-up.** For a message in the follow-up list, Claude shall draft a short, polite follow-up that refers to the first message and makes the ask easier (smaller request or a choice of times). It opens as a new message linked to the same partner, for the participant to edit and save. | AI-FR-17, AHBO-04 | M |
| AH-FR-50 | Without AI, **Draft a follow-up** shall fill a program template with the partner name, the first subject and the original request. | AI-NFR-02 | M |
| AH-FR-51 | **Read a reply.** The participant may paste a partner's reply. Claude shall summarise it in two sentences, say whether it is a yes, a no, a question or a "later", propose the next status (Replied or Closed) and a next action, and offer a reply draft. The pasted text is data, never instructions (AI-NFR-13), is sent only for that request, and is not stored unless the participant saves it in the message. | AI-NFR-13, AHBO-04 | S |
| AH-FR-52 | The helpers shall never change a message's status or follow-up date themselves; a proposed status is a card the participant applies and saves. | AI-FR-04 | M |
| AH-FR-53 | Partner data sent to Claude stays limited to name and role, as the existing outreach draft does (AI-NFR-04). | AI-NFR-04 | M |
| AH-FR-54 | The pitch coach stays on the page. The assistant shall offer **Practise this ask**, which opens the pitch coach with the message's request and benefit filled in. | AI-FR-16 | S |

### G. Measurement and admin

| ID | Requirement | Traces to | Priority |
| --- | --- | --- | --- |
| AH-FR-55 | Usage shall be logged under these feature names, so cost and quality can be seen per page: `PAGE_ASSISTANT_CHAT` (tagged with the page), `MODULE_NEXT`, `MODULE_HELP` (existing), `ASSESSMENT_EXPLAIN` (existing), `ASSESSMENT_PRACTICE`, `OBSTACLE_HELP`, `MESSAGE_FIELD_HELP`, `MESSAGE_CHECK`, `FOLLOW_UP_DRAFT`, `REPLY_HELP`. | AI-NFR-14 | M |
| AH-FR-56 | The admin AI page shall show, per feature: requests, cost, average rating, and applied, edited and dismissed rates, as V11 does for plan building. | PB-FR-36, BO-08, BO-12 | M |
| AH-FR-57 | Admins shall be able to turn the page assistant and the helpers on or off per page and per cohort, within the existing AI settings (AI-FR-24). | AI-FR-24 | S |
| AH-FR-58 | The program help shown without AI (guiding questions, statement explanations, practice list, follow-up template) shall be editable by admins with the other interface text, in English and Khmer. | AI-NFR-02, AI-FR-23 | S |

## Non-functional requirements

| ID | Requirement |
| --- | --- |
| AH-NFR-01 | Speed: the first words of an assistant answer appear within 3 seconds and are streamed (AI-NFR-01). A helper that returns cards answers within 15 seconds, with progress shown. |
| AH-NFR-02 | Cost: an assistant answer should cost no more than $0.01 on average and a helper no more than $0.02, using the simple-task model where the admin has set one (PB-FR-39). At pilot scale (about 20 participants), V15 features together should use no more than $6 of the $20 monthly cap. Phase 1 measures the real figures and shows them on the admin page (AH-FR-56). Every request counts toward the per-participant daily limit and the monthly cap (AI-NFR-15). |
| AH-NFR-03 | Free where possible: statement explanations (AH-FR-23), the retake reminder (AH-FR-28), the follow-up list (AH-FR-48), the blocked-tasks list (AH-FR-39), partner matching (AH-FR-35) and the no-AI fallbacks make **no** AI request. |
| AH-NFR-04 | Validation: every field Claude returns is checked and trimmed on the server before it is shown (V9 rule); enum values outside the allowed list, ids not in the participant's own records and dates past the parent's date are dropped (AH-FR-07). |
| AH-NFR-05 | Privacy and minimisation: each request sends only what section B lists for that page. Partner email and phone never leave the server. Pasted replies (AH-FR-51) and the private reaction field (AH-FR-40) are sent only for the request they were given for. |
| AH-NFR-06 | Consent, safety and injection: the V9 consent (AI-NFR-03), scope guardrails (AI-NFR-08), crisis response (AI-NFR-09, D-5) and prompt-injection rules (AI-NFR-13, SEC-05-07) apply unchanged. Obstacle text, conflict notes, message fields and pasted replies are data, not instructions. |
| AH-NFR-07 | Isolation: conversations and drafts are private to the participant, never shown to coaches or admins (D-6, D-8), and deleted with the account (NFR-09). Every new endpoint checks ownership before AI availability and answers 404 for another user's module completion, obstacle or message (SEC-01-02). |
| AH-NFR-08 | Language: every quick action, helper, card and program help text works in English and Khmer, and Claude answers in the chosen language (AI-NFR-16). New text is added to the Khmer translation file and the Khmer review export. |
| AH-NFR-09 | Accessibility: the panel, **Help with this** buttons and cards meet WCAG 2.1 AA, work by keyboard and screen reader, move focus into the panel on open and back on close, and announce new answers (PB-NFR-13). |
| AH-NFR-10 | Phone: at 600 px wide or less, the panel opens full screen, cards stack in one column, tap targets are at least 40 px, inputs use 16 px text so iOS does not zoom, and nothing scrolls sideways (matching the phone layouts in commit `271c4b2`). |
| AH-NFR-11 | Honesty: Claude must not invent facts, contacts, statistics or quotes (AI-NFR-10). Resource and learning suggestions come from the admin-approved list first; others are marked "check before use" (AI-FR-13, AI-FR-26). |
| AH-NFR-12 | Quality test set: the AI governance lead keeps a fixed set per page before release: 10 module answers, 6 assessment results, 10 obstacles and 10 messages, in English and Khmer, including vague and sensitive cases. Each prompt or model change is tried on the set first (AI-NFR-18). |
| AH-NFR-13 | Tests: backend tests for every new endpoint (ownership, consent off, budget reached, server checks on proposals, partner details never sent); frontend tests for each helper, applying and dismissing a card, the no-AI path and the phone layout. Claude is mocked in tests. The full frontend and backend suites must pass before each commit, as CI requires. |

## Data and API changes (for solution design)

These are business-level needs. The solution design decides exact names.

| Need | Likely change |
| --- | --- |
| Page assistant | One streamed endpoint, for example `POST /api/ai/pages/{page}/assistant`, taking the page, the area key, the open item id and the unsaved form. The server loads saved records itself and checks ownership. The answer holds text and optional change cards. Reuses the V11 plan assistant panel and card components, made page-neutral |
| Assistant history | Dream Coach conversation tagged with page and item, using the dream and section tags added in migration V67 where they fit |
| Module helpers | `POST /api/ai/modules/next`; module help gains answers to questions and a refine mode (`REFINE` with a note and the edited rows); a readiness check mode |
| Assessment helpers | `POST /api/ai/assessment/practice`; statement explanations and the practice fallback list as program content (no endpoint) |
| Obstacle helpers | `POST /api/ai/obstacles/help` with a mode (`CLASSIFY`, `ROOT_CAUSE`, `SOLUTIONS`, `NEXT_TASK`, `CONFLICT`, `PRIORITISE`) and the unsaved form; `GET /api/obstacles/{id}/partner-matches` and `GET /api/obstacles/similar-solved` (no AI); `GET /api/tasks/blocked-without-obstacle` (no AI) |
| Communication helpers | `POST /api/ai/communication/help` with a mode (`FIELD`, `FILL`, `CHECK`, `TONE`, `FOLLOW_UP`, `REPLY`) and the unsaved message; `GET /api/communication-messages/follow-up-due` (no AI) |
| Usage and outcomes | New feature names (AH-FR-55) on the existing usage log and V11 outcome counts; no new table |
| Settings | Per-page on/off switches in AI settings (AH-FR-57) |
| Program help content | Guiding questions, statement explanations, practice list and follow-up template in English and Khmer, kept with the interface text |

Migrations continue from the latest Flyway version (V70 at the time of writing). V15 should need at most one migration (settings switches); the rest reuses existing tables.

## Implementation phases

Each phase ends with build, the **full** test suites, a summary, and a wait for confirmation, as `CLAUDE.md` requires.

| Phase | Scope | Requirements |
| --- | --- | --- |
| 1 | Shared page assistant: page-neutral panel and change cards (from the V11 plan assistant), page and area context, server checks, no-AI help framework, Dream Coach hand-over, new feature names and admin metrics, baseline measures for AHBO-01 to AHBO-04 | AH-FR-01 to AH-FR-12, AH-FR-55, AH-FR-56, AH-NFR-01 to AH-NFR-07 |
| 2 | Obstacles: classify, root cause, solutions, partner matching, ask for help, next task, blocked tasks without an obstacle, solved before, which first | AH-FR-31 to AH-FR-39, AH-FR-41, AH-FR-42 |
| 3 | Communication: help per field, fill from intent, check my message, tone and length, follow-up list and draft, practise this ask | AH-FR-43 to AH-FR-50, AH-FR-52 to AH-FR-54 |
| 4 | Modules: which module next, answer in place, refine, explain, modules done elsewhere, readiness check, journey summary | AH-FR-13 to AH-FR-22 |
| 5 | Assessment: statement explanations, score guard, richer explanation, practise this chain, retake reminder, what changed | AH-FR-23 to AH-FR-30 |
| 6 | Sensitive and optional helpers, settings and content: conflict and criticism help, read a reply, per-page switches, editable program help, Khmer review, quality test set run, accessibility pass, docs | AH-FR-40, AH-FR-51, AH-FR-57, AH-FR-58, AH-NFR-08 to AH-NFR-12 |

Obstacles and Communication come first because they continue the method's execution path (`CLAUDE.md` ideal flow steps 9 to 11: identify blocked tasks, recommend a partner, prepare a message), which today stops without help. The sensitive helpers (conflict notes, pasted replies) come last, after the shared safety and privacy controls have been tested on the other pages.

## Acceptance criteria

| ID | Criterion | Traces to |
| --- | --- | --- |
| AHC-01 | On each of the five routes, **Help** and every **Help with this** button open the assistant showing what it is helping with and three quick actions that fit. | AH-FR-01 to AH-FR-04, AH-FR-12 |
| AHC-02 | A change card fills the form only when applied; nothing is saved until the participant presses the page's Save; dismissing changes nothing. | AH-FR-06, AI-FR-04 |
| AHC-03 | A proposal with an unknown enum value, another user's record id, or a date past its parent's date is never shown (checked in tests). | AH-FR-07 |
| AHC-04 | With AI turned off, every quick action and helper still gives the program's own help or rule result, and says why AI is not available. | AH-FR-10, AH-FR-14, AH-FR-27, AH-FR-46, AH-FR-50 |
| AHC-05 | **Which module next?** always names an unlocked, not completed module, with a reason from the participant's own data. | AH-FR-13, AH-FR-14 |
| AHC-06 | Answers given to *Help me start* questions are used by the next draft without the participant saving anything; refining keeps edited rows unchanged. | AH-FR-16, AH-FR-17 |
| AHC-07 | No AI draft saves a module exercise or unlocks a module. | AH-FR-21, AC-09 |
| AHC-08 | Before the assessment is saved, the assistant never suggests a score, in the 6-result test set and in tests with "what should I score?". | AH-FR-24 |
| AHC-09 | Each chain scored 3 or less gets one practice tied to an app action, with or without AI. | AH-FR-26, AH-FR-27 |
| AHC-10 | No partner name, email or phone is sent to Claude by obstacle helpers, and no partner email or phone by communication helpers (checked from request logs in tests). | AH-FR-35, AH-FR-53, AH-NFR-05 |
| AHC-11 | **Turn this into a task** opens a task form with owner "Me" and a due date not after the linked step's date. | AH-FR-37 |
| AHC-12 | Every Blocked task with a blocker reason and no linked obstacle appears in the blocked-tasks list, with no AI request. | AH-FR-39, AH-NFR-03 |
| AHC-13 | **Check my message** flags a message with no request, no benefit to the partner or a demanding tone in the 10-message test set. | AH-FR-45 |
| AHC-14 | Every Sent or Followed Up message past its follow-up date with no reply appears in the follow-up list, with no AI request. | AH-FR-48 |
| AHC-15 | No helper changes a message status, an obstacle status or a follow-up date without the participant saving it. | AH-FR-42, AH-FR-52 |
| AHC-16 | Every new endpoint refuses no token (401) and another user's records (404) in the automatic route sweep, and checks ownership before AI availability. | AH-NFR-07, SEC-01-08 |
| AHC-17 | At 390 px wide the panel opens full screen, cards stack, and nothing scrolls sideways on any of the five routes. | AH-NFR-10 |
| AHC-18 | Average cost per assistant answer is $0.01 or less and per helper $0.02 or less on the test sets; V15 spend in the first pilot month is inside the AH-NFR-02 share. | AH-NFR-02 |
| AHC-19 | Khmer reviewers rate at least 80% of sampled Khmer answers clear and correct. | AH-NFR-08, AC-15 |
| AHC-20 | After four weeks of pilot use, at least 50% of suggestions are applied (as-is or edited) and the average rating is 4.0 or more. | AHBO-05, BO-08, BO-09 |
| AHC-21 | All existing backend and frontend tests still pass, and CI is green on the commit. | AH-NFR-13 |

## Assumptions, dependencies and risks

### Assumptions

- The pilot stays at about 20 participants under the $20 monthly cap.
- Participants have given the V9 AI consent before using AI help; without it they get the program help.
- The 15 modules, the six chains and the message structure do not change during V15.

### Dependencies

- The V11 plan assistant panel and change cards can be made page-neutral without changing their behaviour on the Plan page.
- A lead coach writes or approves the program help: statement explanations, guiding questions, practice list and follow-up template.
- A Khmer reviewer for the new text and answers (D-4).
- The AI governance lead builds the per-page test sets before Phase 2.

### Risks

| Risk | Impact | Mitigation |
| --- | --- | --- |
| Four more pages of AI use push spend over the cap | AI pauses for everyone | No-AI features where possible (AH-NFR-03); simple-task model; per-feature cost on the admin page; per-page switches (AH-FR-57); daily limits |
| AI help on the assessment biases self-ratings | Results stop meaning anything | Score guard (AH-FR-24); statement help is fixed text, not AI (AH-FR-23) |
| Participants let AI write their module exercises | Shallow learning | Answer-in-place questions first; drafts never unlock; feedback mode encourages own words |
| Conflict notes and pasted replies are sensitive | Trust loss, privacy harm | Sent only when ticked or pasted for that request; never stored by AI; crisis response; built last (Phase 6) |
| Message help makes messages sound generic or pushy | Partners say no | **Check my message** looks for demanding tone; fields the participant wrote are never changed; participant always sends |
| Partner matching suggests the wrong person | Wrong person asked | Matching only proposes; participant confirms; no automatic outreach |
| Two assistants on one page confuse people | Confusion | Dream Coach hands over to the page assistant (AH-FR-11) |
| Helpers overlap with the plan builder's obstacle and helper suggestions (V11 Phase 5) | Duplicate work | Obstacle helpers work on one obstacle at a time on the Obstacles page; the plan builder still suggests risks for a whole plan |

## Open questions

- [ ] Is $6 of the $20 monthly cap the right share for V15 features, or should Phase 1 measure first and set it then? (AH-NFR-02)
- [ ] Should **Which module next?** follow program order strictly, or may it recommend a later unlocked module when a chain score is low? (Draft: may, AH-FR-14.)
- [ ] Should **Practise this chain** be able to add a habit, or only open the right page? (Draft: both, where the habit form exists.)
- [ ] May the obstacle helpers read the participant's fear journal (as V11 PB-FR-26 does with a tick), or only the obstacle itself?
- [ ] Should **Read a reply** (AH-FR-51) ship in V15 at all, given pasted text may contain third-party personal data? (Draft: Should, Phase 6, not stored.)
- [ ] Should coaches see that a participant used AI help on an exercise (yes or no), or nothing? (Draft: nothing.)
- [ ] Should page assistant conversations be kept in Dream Coach history (AH-FR-11), or only for the session?
- [ ] Should the follow-up list (AH-FR-48) also appear on the Today page and in the weekly review?

## Sign-off

| Role | Name | Decision | Date |
| --- | --- | --- | --- |
| Sponsor | | | |
| Product owner | | | |
| AI governance lead | | | |
| Lead coach | | | |

## Sources

- BRD 11.0.0 – AI Plan Builder Improvements (6 Nov 2026): plan assistant panel, change cards and measurement (PB-FR-34 to PB-FR-50)
- BRD 9.0.0 – Dream Conversion Program with Claude AI Integration (2 Nov 2026): AI-FR, AI-NFR, AIB-01 to AIB-15, decisions D-1 to D-8
- BRD 10.0.0 – Security Hardening to the OWASP Top 10 (6 Nov 2026): SEC-01-02, SEC-01-08, SEC-05-07
- `CLAUDE.md` – method hierarchy, obstacle and partner rules, communication structure, coaching behaviour and ideal user flow
- Code review of commit `a9889e5`, 9 Nov 2026: `ModulesPage.tsx`, `ModuleDetailPage.tsx`, `ModuleHelpCard.tsx`, `AssessmentPage.tsx`, `AssessmentExplainCard.tsx`, `ObstaclesPage.tsx`, `partnerSuggestion.ts`, `CommunicationBuilderPage.tsx`, `aiFocus.ts`, `AiModuleService`, `AiDailyService` (outreach draft), `AiContextBuilder`, `components/ai/plan-assistant`, migration `V45__learning_modules.sql`
