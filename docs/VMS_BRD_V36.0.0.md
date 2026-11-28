# BRD – Gaps and inner blockers, kept apart

Nov 28, 2026

## Document control

This is draft 36.0.0. It keeps every requirement in BRD 35.0.0 and the earlier BRDs. It stops the **Strengths and
Gaps** page (V8.1, FR-79 to FR-84) and **My inner blockers** (BRD 34) from recording the same thing twice, and gives
both pages one rule that tells them apart:

> A gap is something someone or something can cover. An inner blocker is something you change by practising.

The product owner reviewed a clickable mock of this change before this BRD was written (28 Nov 2026).

| Item | Value |
| --- | --- |
| Document | Business Requirements Document (BRD) – Gaps and inner blockers addendum |
| Version | 36.0.0 – Draft |
| Parent documents | V8.1 FR-79 (strength or weakness), FR-80 (suggestions from Work Style and the six-chain check), FR-82 (cover methods: Partner, Delegate, Resource, Learn, Accept), BR-63; BRD 34 (D-34-1 inner blockers are not obstacles, D-34-2 fields, D-34-3 chain link, BR-34-3 ten active, D-34-11 private, D-34-12 no AI); BRD 35 (Plan checklist rows); BRD 33 (controller standard); BR-12 archive, not delete |
| Business owner | To be confirmed |
| Product owner | To be confirmed |
| Approvers | Sponsor, Product owner, Design reviewer, Khmer reviewer |

| Version | Change | Author |
| --- | --- | --- |
| 36.0.0 | First draft, from the product owner's question of 28 Nov 2026 (do Inner blockers and Strengths and Gaps duplicate each other?) and their review of the mock | BA |
| 36.0.0 (all phases built) | All three phases implemented, 28 Nov 2026 (no migration). GB-FR-01 to GB-FR-07. Clarified during build: (1) **D-36-4:** the mock asked for confirmation first and then showed a placeholder practice. Because the practice is required (D-34-2), the built move opens the filled-in inner blocker form in place of that confirmation: the participant writes the practice and chooses **Move it**. Nothing changes until then. (2) The chain is found by matching the item's text with the old suggestion text in English or in the language it was saved in; an item edited since gets no chain, which the participant can pick. (3) The info line (D-36-2) sits inside the Suggestions card, so that card shows when there is a low score even without Work Style suggestions. (4) **Add as an inner blocker** and **View it** open the Assessment page, where My inner blockers is. UAT, the Khmer review (`km-review-v36.csv`, 15 strings) and Q-36-1, Q-36-2 remain | Dev |
| 36.0.0 (follow-up) | 28 Nov 2026, after the implementation check: **Add as an inner blocker**, **View it** and **Open My inner blockers** now open `/assessment#my-inner-blockers`, and the Assessment page scrolls to the My inner blockers section once it has loaded, as D-36-2 asks. Before, they opened the top of the page | Dev |

---

## Executive summary

The two pages answer different questions:

| | Strengths and Gaps | My inner blockers |
| --- | --- | --- |
| Question | What abilities do I bring and lack for this dream? | What habits of mind might stop me? |
| Example | "No experience with databases (SQL)" | "I give up when progress is slow" |
| Solved by | Covering the gap: Partner, Delegate, Resource, Learn, Accept | Practising: a short practice, a daily habit, a weekly check |

But both read the same 2-minute six-chain check:

- Strengths and Gaps suggests a **weakness** for every chain scored **2 or lower** (FR-80), for example "I give up when
  progress is slow."
- The Inner blockers stage names the same chain, "Giving up too soon", for scores of **3 or lower**, with **Add as my
  blocker** (BRD 34).

So one low score can be written down twice, and as a gap it fits badly: you can't delegate or find a partner for
"giving up too soon". V36 sends six-chain results to inner blockers only, lets people move what they already saved,
and shows the rule on both pages.

### Expected benefits

| # | Change | Benefit to users | Benefit to the project |
| --- | --- | --- | --- |
| 1 | No six-chain suggestions in Strengths and Gaps | Each low score goes to one place, where it can be worked on | Strengths and Gaps stays about abilities; fewer gaps stuck "not covered" |
| 2 | Move to inner blockers | Nothing already written is lost or retyped | Old data is tidied without deletes (BR-12) |
| 3 | The one-line rule on both pages | People know where to write each thing | Fewer misplaced items |

---

## Decisions recorded by this BRD

| ID | Decision |
| --- | --- |
| D-36-1 | **No six-chain suggestions in Strengths and Gaps.** FR-80's suggestions from the six-chain check are removed. Work Style suggestions (a strength and a likely blind spot) stay. The Suggestions card's subtitle becomes "From your Work Style. Nothing is saved until you add your own evidence." |
| D-36-2 | **One line instead.** When the latest six-chain check has any chain scored 2 or lower (the old FR-80 condition), Strengths and Gaps shows one info line: "Your check suggests something that holds you back inside. Add it as an inner blocker." with **Add as an inner blocker**, which opens the Assessment page at My inner blockers. It is shown even when Work Style has no suggestions |
| D-36-3 | **Move to inner blockers.** A saved, not archived **weakness whose source is the six-chain check** (`SIX_CHAIN`) shows **Move to inner blockers**. Strengths, manual weaknesses and Work Style items don't: they are abilities, and the participant can still archive them by hand |
| D-36-4 | **The move opens the inner blocker form, filled in.** The form (BRD 34 §2.3) opens on the item with: Possible blocker = the item's description (cut to 120 characters at a word boundary); How it shows up = the item's evidence (cut to 500); Linked chain = the chain whose suggestion text the description matches, else none; Dream = the item's dream. **Short practice is left empty for the participant to write**, because a cover method such as "Learn" is not a practice. Nothing changes until **Move it** |
| D-36-5 | **One step on the server.** **Move it** creates the inner blocker and archives the weakness in one transaction. If either fails, neither happens. The weakness is **archived, not deleted** (BR-12): it can be restored. Any record its cover created (a partner, a step, a module row) is **kept**, as when archiving with "keep" today (AC-10). The limit of 10 active inner blockers applies (BR-34-3) |
| D-36-6 | **After the move.** Strengths and Gaps says "Moved to My inner blockers. The old item is archived here." with **View it**. The archived item stays in the archived list as today. The Plan checklist's Strengths and gaps and Inner blockers rows update by themselves (BRD 35) |
| D-36-7 | **The rule on both pages.** The one-line rule above is shown near the top of Strengths and Gaps and in the My inner blockers section of the Assessment page, each with a link to the other page |
| D-36-8 | **Nothing else changes.** Real gaps and their cover methods are untouched. No AI is used (D-34-12). The `SIX_CHAIN` source stays valid for items saved before V36 |

---

## 1. Fit analysis

| Need | Today | Gap |
| --- | --- | --- |
| Record a low chain score once | Suggested in both places | Written twice |
| Work on a mindset item | Gap cover methods only | No practice, habit or weekly check for it in Strengths and Gaps |
| Tidy what was already saved | Archive and retype by hand | No move |
| Know where to write something | No guidance | No rule on either page |

---

## 2. The changes

### 2.1 Strengths and Gaps

- The rule line near the top, with **Open My inner blockers** (D-36-7).
- Suggestions: Work Style only (D-36-1); the info line when the check has a low score (D-36-2).
- Each six-chain weakness: **Move to inner blockers** opens the filled-in form in place; **Move it** and **Cancel**
  (D-36-3, D-36-4). Then the moved notice with **View it** (D-36-6).

### 2.2 My inner blockers (Assessment page)

- The rule line, with **Open Strengths and Gaps** (D-36-7).
- The moved blocker appears like any other.

---

## 3. Data model

No migration.

## 4. API

| Change | Detail |
| --- | --- |
| `POST /api/v1/self-assessment-items/{id}/move-to-inner-blocker` | Body: the inner blocker request of BRD 34 (`blocker`, `showsUp`, `practice`, `chainKey`, `dreamId`). Creates the inner blocker and archives the item in one transaction; answers `201 Created` with the new inner blocker and `Location` at it. `404` for another user's item; `400` when the item is archived, is not a weakness, or did not come from the six-chain check; `409` when 10 inner blockers are already active |

## 5. Business rules

| ID | Rule |
| --- | --- |
| BR-36-1 | Only the owner's own, not archived, six-chain weakness can be moved (CLAUDE.md rule 13, D-36-3) |
| BR-36-2 | The move is all or nothing: a blocker is never created without the item being archived, or the other way round (D-36-5) |
| BR-36-3 | The item is archived, never deleted, and records created by its cover are kept (D-36-5, BR-12) |
| BR-36-4 | The inner blocker's own rules apply: required blocker and practice, field limits, the six chains only, ten active (BRD 34) |

## 6. Pages and components

- **`strengthSuggestions`**: Work Style only; a separate check for "the check has a low score".
- **`StrengthsPage`**: rule line, info line, Move to inner blockers with the inner blocker form, moved notice.
- **`InnerBlockerForm`**: can take its own save action, so the move can use the move endpoint.
- **`MyInnerBlockersCard`**: rule line.
- **`SelfAssessmentService`** and **`SelfAssessmentItemController`**: the move.
- Khmer drafts for all new text (`km-review-v36.csv`).

---

## Functional requirements

| ID | Requirement | Priority |
| --- | --- | --- |
| GB-FR-01 | Remove six-chain suggestions from Strengths and Gaps; keep Work Style suggestions; new subtitle (D-36-1) | Must |
| GB-FR-02 | Info line with Add as an inner blocker when the check has a score of 2 or lower (D-36-2) | Must |
| GB-FR-03 | Move to inner blockers on not archived six-chain weaknesses only (D-36-3) | Must |
| GB-FR-04 | The move form filled in from the item, practice left empty (D-36-4) | Must |
| GB-FR-05 | Create and archive in one transaction; archive, never delete; cover records kept; ten-active limit (D-36-5) | Must |
| GB-FR-06 | Moved notice with View it (D-36-6) | Should |
| GB-FR-07 | The rule line on both pages with a link to the other (D-36-7) | Must |

## Non-functional requirements

| ID | Requirement |
| --- | --- |
| GB-NFR-01 | No migration and no change to existing items other than the one moved |
| GB-NFR-02 | WCAG 2.2 AA: the move button names its item ("Move {item} to inner blockers"); the form keeps its labels |
| GB-NFR-03 | Works at 360px |
| GB-NFR-04 | Khmer drafts for all new text in `km-review-v36.csv` |
| GB-NFR-05 | Backend tests: move works and archives; cover records kept; another user's item 404; archived, strength, manual or Work Style item 400; ten-active 409 leaves the item unarchived; invalid blocker 400 leaves the item unarchived. Frontend tests: no six-chain suggestions; the info line; Move only on six-chain weaknesses; the form filled in with the chain; the moved notice; the rule line on both pages |
| GB-NFR-06 | `mvn verify` (line ≥ 94%, branch ≥ 75%), `ControllerRulesTest`, the access sweep, frontend tests with `--testTimeout=20000`, and the SonarQube gate pass |

## Implementation phases

Each phase builds the backend and the frontend together, in full.

| Phase | Content |
| --- | --- |
| 1 | GB-FR-01, 02, 07: suggestions, info line, rule line on both pages; tests |
| 2 | GB-FR-03 to 06: the move endpoint and the move on Strengths and Gaps; tests |
| 3 | `km-review-v36.csv`, CHANGELOG, `UAT_V36.md`, `docs/API.md`, user guide section |

## Acceptance criteria

| ID | Criterion |
| --- | --- |
| GBC-01 | With "Giving up too soon" scored 2, Strengths and Gaps suggests nothing from the check and shows the info line; Work Style suggestions still show |
| GBC-02 | **Add as an inner blocker** opens My inner blockers on the Assessment page |
| GBC-03 | Only a saved six-chain weakness shows **Move to inner blockers** |
| GBC-04 | The move form shows the description, the evidence as How it shows up, the chain and the dream; Short practice is empty and required |
| GBC-05 | **Move it** adds the blocker to My inner blockers and archives the weakness; the weakness can be restored; its cover's partner or step is still there |
| GBC-06 | With 10 active inner blockers, **Move it** is refused with the limit message and the weakness stays as it was |
| GBC-07 | The rule line shows on both pages, each linking to the other |
| GBC-08 | Builds, tests, the coverage check, `ControllerRulesTest`, the access sweep and the SonarQube gate pass |

## Measuring the benefits

| ID | Measure | Target |
| --- | --- | --- |
| GBM-01 | New six-chain weaknesses saved in Strengths and Gaps | Zero after release |
| GBM-02 | Six-chain weaknesses moved to inner blockers | Counted; no target |
| GBM-03 | Share of high-relevance gaps covered (FR-82) | Up, since mindset items no longer sit uncovered |

## Open questions

| ID | Question | Proposed answer |
| --- | --- | --- |
| Q-36-1 | Should a manual weakness that reads like a mindset item also be movable? | Not in V36. Only six-chain items are clearly mindset items; review after GBM-02 |
| Q-36-2 | Should moved items be moved automatically for everyone at release? | No. Each person moves their own, because the practice must be written by them |

## Sign-off

| Role | Name | Date | Signature |
| --- | --- | --- | --- |
| Sponsor | | | |
| Product owner | | | |
| Design reviewer | | | |
| Khmer reviewer | | | |
