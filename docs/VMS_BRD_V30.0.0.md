# BRD – A stronger AI assistant for Streamline

Nov 24, 2026

## Document control

This is draft 30.0.0. It keeps every requirement in BRD 29.0.1 and the earlier BRDs. It improves the AI help in the
**Streamline review** (BRD 27) in seven ways the product owner chose on 24 Nov 2026: AI picks what to streamline,
suggestions load by themselves, several suggestions can be applied at once, AI drafts the stage 1 answers, Simplify
suggestions really edit the item, AI gives advice on the summary, and the user can ask the AI a question about the
review.

| Item | Value |
| --- | --- |
| Document | Business Requirements Document (BRD) – Streamline AI addendum |
| Version | 30.0.0 – Draft |
| Parent documents | BRD 27 (Streamline review; D-27-3 stage order, D-27-6 requirement owner, D-27-9 AI suggestions under consent and limits, D-27-10 coach read-only, §6.2 candidates), BRD 15 (page assistant, AH-FR-01 to AH-FR-12, page switches AH-FR-57), BRD 9 (AI consent, daily limit, monthly cap; AI-NFR-04 no partner names to AI), BRD 11 (PB-FR-34 rating) |
| Business owner | To be confirmed |
| Product owner | To be confirmed |
| Approvers | Sponsor, Product owner, Design reviewer, Khmer reviewer |

| Version | Change | Author |
| --- | --- | --- |
| 30.0.0 | First draft, from the product owner's request of 24 Nov 2026 ("improve AI assistant on Streamline can do suggestions"), with the seven improvements chosen the same day | BA |

---

## Executive summary

Today the Streamline AI does one thing: inside an open review, the user presses **Suggest for this stage** and gets up
to 8 cards to apply one by one. Five gaps remain:

- choosing **what** to streamline uses fixed rules only, and the suggestion cards are not ranked;
- suggestions must be asked for on every stage and applied **one at a time**;
- stage 1 still needs the user to type who asked for each item and why;
- a **Simplify** suggestion only saves a note; it does not change the item;
- the summary shows numbers but no advice, and there is no way to **ask** the AI about the review.

V30 closes them with seven features (§2). All follow the same rules as every AI feature in VMS: they need the user's
AI consent and respect the daily limit and monthly cap; they **suggest only**, and nothing changes until the user
applies a suggestion; the AI never sees names of people; and everything works without AI, with a message saying why
suggestions are missing.

---

## Decisions recorded by this BRD

| ID | Decision |
| --- | --- |
| D-30-1 | Every V30 AI answer is a **suggestion**. Applying one goes through the existing review actions and their rules (BR-27-1 to BR-27-12); the AI never changes the plan itself (D-27-9) |
| D-30-2 | V30 AI runs only under the V9 rules: AI consent, daily limit, monthly cap. When AI cannot run, the review works fully and each panel says why (the D-27-9 messages) |
| D-30-3 | The AI sees plan item codes, titles, statuses, estimates, cycle times and the requirement **source type**, never names of people (requirement owner names, partner names or contacts; AI-NFR-04) |
| D-30-4 | **AI picks what to streamline** ranks the user's open goals and steps by how much a review would help, at most 5, each with a one-line reason; only goals and steps of the user's own plan, not completed, not archived and without an open review, are accepted. The rule cards of BRD 27 §6.2 stay and still work without AI; they are now **ranked** too (more open steps or tasks, then more overdue) |
| D-30-5 | **Auto-suggest**: when the user opens the current stage of an open review and AI is available, suggestions load by themselves, once per review and stage in the browser session. "Suggest again" asks again. Each automatic request counts toward the daily limit like any other |
| D-30-6 | **Apply all / apply selected**: suggestion cards get tick boxes; "Apply selected (n)" and "Apply all" apply them **one after another**, in the order shown. If one fails, the rest stop, the applied ones stay applied, and the panel says how many were applied. QUESTION cards (questions to ask yourself) have nothing to apply and are never ticked |
| D-30-7 | **AI drafts stage 1**: a new stage 1 action, ANSWER, proposes for one item its requirement source, an owner as a **role** (for example "Me", "Mentor", "Funding body"; never a person's name), why it is needed, and a verdict. Applying it saves those four answers; the user can edit them first. Requirement owner stays required unless the source is NOT_SURE (BR-27-4) |
| D-30-8 | **Simplify really edits**: a SIMPLIFY suggestion carries a proposed new title (and, when useful, a shorter description). The card shows the title in an editable box; applying it updates the step or task title and saves the reason as the simplify note. Leaving the box unchanged applies the AI's wording |
| D-30-9 | **AI advice on the summary**: on a completed review (and during its 90-day follow-up), "Get AI advice" returns 2 to 4 short points on what to do next, each optionally tied to an item code, read from the summary and the items. Advice only; nothing to apply |
| D-30-10 | **Ask the AI**: the Streamline review page joins the page assistant (BRD 15) as page `STREAMLINE`. The chat sees the review (anchor, stage, items as in D-30-3, summary) and answers questions; it proposes no change cards. Admins and cohorts can switch it off like any other page (AH-FR-57) |
| D-30-11 | A coach viewing a participant's review read-only (D-27-10) sees no V30 AI controls |

---

## 1. Fit analysis

| Need | Today | Gap |
| --- | --- | --- |
| Know which goal to streamline | Rule cards (more than 7 open steps, more than 30% overdue, more than 10 open tasks, a task done by hand 4+ times) | No AI judgement; cards not ranked |
| Get suggestions | "Suggest for this stage" per stage | Must be pressed every stage |
| Act on many suggestions | Apply one card at a time | No batch apply |
| Answer stage 1 | Type source, owner, why and verdict per item (bulk marking exists) | No drafts |
| Simplify | SIMPLIFY card saves a note | Title is not changed |
| Learn from the result | Summary numbers | No advice |
| Ask a question | Dream Coach on other pages | Not on the review page |

---

## 2. The seven features

### 2.1 AI picks what to streamline (Streamline page)

- A card **"AI picks"** above the rule suggestions, with **Ask AI which to streamline**.
- The answer lists up to 5 goals or steps, best first: code, title, a one-line reason, and **Start review**.
- The rule cards stay below, now ranked. Without AI, only the rule cards show, with the usual message.

### 2.2 Auto-suggest on each stage

- Opening the current stage of an open review loads suggestions automatically (D-30-5); the button reads
  **Suggest again**.

### 2.3 Apply all / apply selected

- Each applicable card has a tick box. Above the cards: **Apply selected (n)** and **Apply all**.
- Progress shows "Applying 2 of 5…". When done, applied cards disappear and the panel says "Applied 5 suggestions."
  On a failure: "Applied 2 of 5. The rest were not applied."

### 2.4 AI drafts stage 1

- In stage 1, suggestions include **Draft answer** cards: source, owner (role), why needed, verdict, each editable on
  the card. **Apply** saves them for that item. These cards can be applied in bulk (§2.3).

### 2.5 Simplify really edits

- In stage 3, a **Simplify** card shows "New title" in an editable box, prefilled with the AI's wording, and the
  reason. **Apply** changes the item's title.

### 2.6 AI advice on the summary

- On the summary card: **Get AI advice**, then 2 to 4 points, each with an item code when it is about one item.

### 2.7 Ask the AI

- The review page shows the page assistant's **Ask** button. The chat knows the review; quick questions include "What
  should I cut first?", "Why is this goal slow?", "What could repeat by itself?".

---

## 3. Data and API

No migration.

| Change | Detail |
| --- | --- |
| `POST /api/streamline-reviews/ai-candidates?language=` | AI-ranked goals and steps to streamline (D-30-4): `{ requestId, candidates: [{ anchorType, anchorId, code, title, reason }] }` |
| `GET /api/streamline-reviews/candidates` | Unchanged shape; now ranked |
| `POST /api/streamline-reviews/{id}/stages/{stage}/suggestions` | Suggestions gain `requirementSource`, `requirementOwner`, `whyNeeded` (ANSWER) and `newTitle`, `newDescription` (SIMPLIFY) |
| `POST /api/streamline-reviews/{id}/summary-advice?language=` | `{ requestId, points: [{ text, itemCode }] }` (D-30-9) |
| Page assistant | `STREAMLINE` added to the pages (request pattern, page switches, labels, help); `itemId` is the review id, checked as the user's own |
| AI features | `STREAMLINE_PICK` and `STREAMLINE_ADVICE`, logged, rated and counted with the page assistant features |

---

## 4. Business rules

| ID | Rule |
| --- | --- |
| BR-30-1 | Every V30 endpoint is user-scoped: another user's review answers 404 before AI is checked |
| BR-30-2 | AI candidates are kept only for the user's own open goals and steps without an open review; anything else is dropped; at most 5 |
| BR-30-3 | ANSWER drafts keep only valid sources and verdicts; the owner is at most 120 characters, why at most 300; a draft with source other than NOT_SURE and no owner is given the owner "Me" only when the source is SELF, otherwise dropped |
| BR-30-4 | SIMPLIFY drafts keep a new title only when it is not blank and at most 220 characters, and differs from the current title |
| BR-30-5 | Summary advice is available only for a completed review; at most 4 points, each at most 300 characters; an item code not in the review is removed from the point |
| BR-30-6 | Applying in bulk runs one action at a time; a failure stops the run (D-30-6) |
| BR-30-7 | Suggestions, picks and advice are never stored; only their usage-log rows and ratings |

---

## 5. Pages and components

- **Streamline list page**: the "AI picks" card (`StreamlineAiPicks`); ranked rule cards.
- **`StreamlineSuggestionsPanel`**: auto-suggest, tick boxes, Apply selected and Apply all, editable Draft answer and
  Simplify cards.
- **Summary card**: Get AI advice.
- **Review page**: wrapped in the page assistant for page `STREAMLINE`.
- **Admin and cohort page switches**: "Streamline" added.
- All new text has Khmer drafts (`km-review-v30.csv`).

---

## Functional requirements

| ID | Requirement | Priority |
| --- | --- | --- |
| SA-FR-01 | AI-ranked candidates on the Streamline page with Start review (D-30-4, §2.1) | Must |
| SA-FR-02 | Rule candidates ranked by need (D-30-4) | Should |
| SA-FR-03 | Auto-suggest on opening the current stage, once per review and stage per session; Suggest again (D-30-5) | Must |
| SA-FR-04 | Tick boxes, Apply selected and Apply all, one at a time, stop on failure (D-30-6) | Must |
| SA-FR-05 | Stage 1 ANSWER drafts with editable source, owner (role), why and verdict; Apply saves them (D-30-7) | Must |
| SA-FR-06 | SIMPLIFY cards with an editable new title; Apply updates the title (D-30-8) | Must |
| SA-FR-07 | Summary advice on completed reviews (D-30-9) | Must |
| SA-FR-08 | Page assistant on the review page as page STREAMLINE, chat only, with page switch (D-30-10) | Must |
| SA-FR-09 | No V30 AI controls in a coach's read-only view (D-30-11) | Must |

## Non-functional requirements

| ID | Requirement |
| --- | --- |
| SA-NFR-01 | No names of people sent to the AI (D-30-3), checked by tests |
| SA-NFR-02 | WCAG 2.2 AA: tick boxes named after their card; bulk progress announced; editable fields labelled |
| SA-NFR-03 | Khmer drafts for all new text in `km-review-v30.csv`; no clipping at 360px |
| SA-NFR-04 | Backend tests for each new endpoint (ownership, AI off, invalid drafts dropped, limits) and for ANSWER and SIMPLIFY checks; frontend tests for auto-suggest, bulk apply, editable cards, picks and advice |
| SA-NFR-05 | `mvn verify` (line ≥ 94%, branch ≥ 75%) and the SonarQube gate pass |

## Implementation phases

| Phase | Content |
| --- | --- |
| 1 | Backend: ANSWER and SIMPLIFY fields, AI candidates, ranked rule candidates, summary advice, page assistant STREAMLINE; tests |
| 2 | Frontend: suggestions panel (auto-suggest, bulk apply, editable cards), AI picks, summary advice, page assistant on the review page, page switch; tests |
| 3 | `km-review-v30.csv`, CHANGELOG, `UAT_V30.md`, user guide section |

## Acceptance criteria

| ID | Criterion |
| --- | --- |
| SAC-01 | With AI on, "Ask AI which to streamline" lists up to 5 of the user's own goals or steps with reasons; a goal with an open review or another user's goal never appears |
| SAC-02 | Opening stage 2 of an open review shows suggestions without pressing a button; reopening the same stage in the same session does not ask again; Suggest again does |
| SAC-03 | Ticking three cards and choosing Apply selected applies them in order; Apply all applies every applicable card; a failure stops the run and says how many were applied |
| SAC-04 | A stage 1 Draft answer, edited to source MENTOR and owner "Research mentor", saves those values on Apply |
| SAC-05 | A stage 3 Simplify card, with the new title edited, changes the step's title on Apply |
| SAC-06 | A completed review shows 2 to 4 advice points; an open review offers no advice |
| SAC-07 | The review page's Ask button answers questions about the review; with the page switched off by an admin, it says AI help is off on this page |
| SAC-08 | With AI consent off, every V30 panel shows why AI is missing and the review works fully |
| SAC-09 | Builds, tests, the coverage check and the SonarQube gate pass |

## Open questions

| ID | Question | Proposed answer |
| --- | --- | --- |
| Q-30-1 | Should auto-suggest also run on a stage the user reopens later in another session? | Yes, once per session (D-30-5); a stored "seen" flag is not needed |
| Q-30-2 | Should the missing BRD 27 §6.2 rule (a step whose median cycle time doubled in 90 days) be built now? | In a follow-up, once enough tasks have start times |
| Q-30-3 | Should Apply all ask for confirmation first? | No: each change can be undone (delete restores, merge restores, titles can be edited back); the panel shows what was applied |

## Sign-off

| Role | Name | Date | Signature |
| --- | --- | --- | --- |
| Sponsor | | | |
| Product owner | | | |
| Design reviewer | | | |
| Khmer reviewer | | | |
