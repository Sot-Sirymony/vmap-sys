# BRD – My inner blockers

Nov 28, 2026

## Document control

This is draft 34.0.0. It keeps every requirement in BRD 33.0.0 and the earlier BRDs. It lets a participant write
down **their own inner blockers** in three parts: the **blocker**, **how it shows up**, and a **short practice**. They
can do this in the Inner blockers stage of Build my plan (BRD 31 stage 3) and on the Assessment page. A practice can
become a daily habit in one click, and the weekly review asks whether each practice helped.

| Item | Value |
| --- | --- |
| Document | Business Requirements Document (BRD) – Personal inner blockers addendum |
| Version | 34.0.0 – Draft |
| Parent documents | BRD 31 (D-31-7 inner blockers before mapping; DC-FR-19 to DC-FR-23; R-05 `INNER_SKIPPED`), BRD 17 (D-17-1 obstacles anchored to a step or task; D-17-2 blockers on steps and tasks; D-17-3 six chains; BL-FR-30 to BL-FR-35), BRD 15 (AH-FR-27 the program's practice per chain), BRD 9 (D-6 coach read-only; D-8 journals private until shared; AI-NFR-04), BRD 23 (D-23-3 no plan data for admins), BRD 33 (controller standard, `/api/v1`, `PagedResponse`, `ResponseEntity`, RFC 9457 errors), FR-68 (habits), CLAUDE.md rules 12 to 14 |
| Business owner | To be confirmed |
| Product owner | To be confirmed |
| Approvers | Sponsor, Product owner, Design reviewer, Khmer reviewer |

| Version | Change | Author |
| --- | --- | --- |
| 34.0.0 | First draft, from the product owner's request of 28 Nov 2026: a place to enter "Possible blocker / How it shows up / Short practice", as in their plan guide for learning to code alone | BA |
| 34.0.0 (all phases built) | All five phases implemented, 28 Nov 2026 (migration **V104**: `inner_blockers`, `inner_blocker_checkins`). IB-FR-01 to IB-FR-13. Clarified during build: (1) **D-34-8:** a Build my plan draft always belongs to a saved dream (`plan_builder_drafts.dream_id` is not null), so a blocker added in stage 3 is linked to that dream at once. `innerBlockerIds` in the draft is not needed and was not added; IB-FR-06 and IBC-04 are met by design. (2) **D-34-9:** the Check and add line ("Inner blockers for this dream: n — names") is shown by the page above the plan tree, not as an INFO rule in `PlanRuleService`, so the rule check still reads only the draft. It never blocks saving. (3) **D-34-11:** the coach reads blockers in the existing participant detail (`innerBlockers`, null unless journals are shared), like the fear and attitude journals, not through a separate `/coach/.../inner-blockers` endpoint. (4) Archive takes `stopHabit` as a query parameter (`POST …/{id}/archive?stopHabit=true`). (5) Saving a review's check-ins replaces its answers: a blocker left out keeps no answer for that review. An answer is a choice, not a record, so it is not archived. (6) Making a habit follows FR-68: at most three active habits; the fourth is refused with the habits page's message. (7) `habitStreak` is filled only when the request gives the local day (`date`), as habits do. (8) The 10-active limit answers `409` with "You have 10 active inner blockers. Resolve or archive one first." (9) Example ideas under the form: "Starting many things, finishing few", "Waiting until I feel ready", "Saying yes to everything except my plan". UAT, the Khmer review (`km-review-v34.csv`, 59 strings) and Q-34-1 to Q-34-5 remain | Dev |

---

## Executive summary

The Inner blockers stage (BRD 31) shows the participant's six-chain result and, for at most two low chains, the
program's fixed practice. It has no place to type anything. Participants who know their own blockers can't record
them there, for example:

| Possible blocker | How it shows up | Short practice |
| --- | --- | --- |
| Fear of not being good enough | Rewatching lessons instead of writing code; not applying for jobs | Write code before you feel ready: for every hour of video, one hour of typing your own code |
| Inconsistency | Big bursts, then weeks of nothing | Never miss twice. A bad day still counts if you do 15 minutes |
| Tutorial trap | Many course certificates, no own projects | Each goal ends with a project you design yourself, without a video |
| Comparing yourself to others | Feeling behind people with CS degrees | Compare only with your own log from last month |

The places that come close today don't fit:
- **Obstacles** must be tied to a step or task (D-17-1). An inner blocker belongs to the person.
- **The fear journal** fits fears only.
- **Habits** hold a name only.

V34 adds a small personal list, **My inner blockers**, with the three fields from the table above. Each blocker can
link to one of the six chains and to one dream. The list:
- is filled in by hand, with no AI;
- is private by default;
- turns a practice into a habit in one click;
- comes back in the weekly review, so the participant sees whether each practice is helping.

### Expected benefits

| # | Feature | Benefit to users | Benefit to the project |
| --- | --- | --- | --- |
| 1 | Own blockers, three fields | The participant names blockers in their own words, not only the six chains | The Inner blockers stage gives the participant something to keep, not just something to read |
| 2 | Practice → habit | The practice is done daily on the Today page, not forgotten | Links the method's inner work to tracked daily action |
| 3 | Weekly check-in | The participant sees which practices work and changes the ones that don't | Fewer "skipped" Inner blockers stages (R-05) |

These benefits are estimates. "Measuring the benefits" says how they are measured after release.

---

## Decisions recorded by this BRD

| ID | Decision |
| --- | --- |
| D-34-1 | **A personal list, not an obstacle.** An inner blocker belongs to the participant, not to a step or task, so it is a new record, `InnerBlocker`, and not an `Obstacle`. D-17-1 and D-17-2 are unchanged: gaps in knowledge, skill, time or money are still recorded on steps and tasks. The help text says so: "Inner blockers are about you. Gaps in knowledge, skill or money go on the steps." |
| D-34-2 | **Three fields, as in the plan guide.** **Possible blocker** (required, up to 120 characters), **How it shows up** (optional, up to 500 characters), **Short practice** (required, up to 300 characters). An empty "How it shows up" is allowed, but the form asks for it: "What do you see yourself doing when this blocker is at work?" |
| D-34-3 | **Optional links.** A blocker may link to **one chain** of the six (D-17-3) and to **one dream**. A blocker with no dream applies to all dreams ("For all my dreams"). The retired chains (`knowledgeScore`, `resourcesScore`, BL-FR-31) can't be chosen |
| D-34-4 | **Two places to add a blocker.** (1) The **Inner blockers stage** of Build my plan, under the chain result: "Add your own blocker". A blocker added there links to the draft's dream (D-34-8). (2) A **My inner blockers** section on the Assessment page, to see, edit, resolve and archive every blocker. Both use one shared form component |
| D-34-5 | **Start from a chain.** Each chain the stage names (DC-FR-20) gets **Add as my blocker**. This opens the form with the chain linked, the chain's label as the blocker and the program's practice (AH-FR-27) as the practice, all editable. Nothing is saved until the participant chooses Save |
| D-34-6 | **Practice → habit.** Each active blocker offers **Make it a daily habit**. This creates a habit (FR-68) whose name is the practice, cut to the habit name limit at a word boundary and editable before saving, and links it to the blocker. The blocker card then shows "Daily habit: <name>" and the habit's streak. Resolving or archiving the blocker asks "Also stop the habit?" (default No). The habit is never removed without that choice (BR-12) |
| D-34-7 | **Status.** **Active** (default) or **No longer holds me back** (resolved, with the date). Archive hides a blocker. Nothing is deleted, except when the account is deleted (BR-12). At most **10 active** blockers per participant, to keep the list focused (Q-34-3) |
| D-34-8 | **Linking to a dream from a draft.** If the draft already has a saved dream, a blocker added in stage 3 links to it at once. If not (a v1 draft with no saved dream yet), the blocker is saved with no dream, and its id is kept in the draft (`innerBlockerIds`). When the plan is saved, those blockers that still have no dream are linked to the new dream. If the draft is abandoned, they stay as "For all my dreams" |
| D-34-9 | **Stage 3 and the check.** Stage 3 lists the participant's active blockers for this dream and for all dreams, under the chain result. "I'm ready to plan" and "Skip for now" work as before (DC-FR-22). R-05 (`INNER_SKIPPED`) is unchanged. The **Check and add** summary shows one INFO line, "Inner blockers for this dream: n", with their names. It never blocks saving |
| D-34-10 | **Weekly check-in.** A weekly review gains a **My inner blockers** section listing each active blocker with "Did the practice help this week?": **Yes**, **Partly**, **No**, **Didn't do it**. Answers are optional and saved with the review. The blocker card shows the last four answers. After two **No** answers in a row, the card says "This practice isn't helping yet. Try changing it." and offers Edit. These are rules only, with no AI |
| D-34-11 | **Private by default.** Inner blockers are treated like the fear and attitude journals (D-8). A coach sees a participant's blockers and check-ins, read-only, **only** when the participant shares journals with their coach (`share_journals_with_coach`, one switch for both, Q-34-1). Admins never see them (D-23-3). They are included in the account export and deleted with the account |
| D-34-12 | **No AI in V34.** Inner blockers are not sent to any AI: not to the Dream Coach, plan building, page assistant or weekly review draft. AI suggestions for practices are a possible later change (Q-34-2), which would need consent and a tick for that request, as DC-FR-21 does for the fear journal |
| D-34-13 | **The app never diagnoses.** The section keeps the stage's note "This is a practice, not a diagnosis. If something feels heavy, talk to your coach or someone you trust." (DC-FR-23). The wording is original (BL-NFR-05). Example blockers in the help text are offered as ideas and are not prefilled |

---

## 1. Fit analysis

| Need | Today | Gap |
| --- | --- | --- |
| Name a blocker in your own words | Six fixed chains | No free-text blocker |
| Say how it shows up | Nothing | No field |
| Choose your own practice | One fixed practice per chain (AH-FR-27) | Can't be changed or added to |
| Do the practice daily | Habits, typed separately | No link from blocker to habit |
| Know if the practice works | Nothing | No follow-up |
| Record a person-level blocker | Obstacles need a step or task (D-17-1) | No place that fits |

---

## 2. The feature

### 2.1 Inner blockers stage (Build my plan)

Under the chain result:

- Each named chain gets **Add as my blocker** (D-34-5).
- **Your inner blockers** lists the active blockers for this dream and for all dreams, as three-part cards, each with
  Edit.
- **Add your own blocker** opens the form inline. Below the form, three short example ideas are listed, for example
  "Starting many things, finishing few". They are not prefilled.
- If the participant already has 10 active blockers, the add button is disabled and says "You have 10 active
  blockers. Resolve or archive one first."

### 2.2 My inner blockers (Assessment page)

A section below the six-chain history:

- A table on wide screens and cards on phones. Columns: **Possible blocker**, **How it shows up**, **Short
  practice**, Chain, Dream, Habit, Last check-ins.
- Filters: Active (default), No longer holds me back, Archived.
- Actions on each blocker: Edit, Make it a daily habit, Mark as no longer holding me back, Archive, Restore.

### 2.3 The form

| Field | Control | Rule |
| --- | --- | --- |
| Possible blocker | Text | Required, 1 to 120 characters |
| How it shows up | Text area | Optional, up to 500 characters; prompt as in D-34-2 |
| Short practice | Text area | Required, 1 to 300 characters; hint: "One small action you can repeat. Make it easy to do on a bad day." |
| Linked chain | Select: None, or one of the six | Optional |
| Dream | Select: For all my dreams, or one active dream | Optional; set for you in stage 3 (D-34-8) |

### 2.4 Weekly review

On the Reviews page, for a **Weekly** review only, after the questions: **My inner blockers**, one row per active
blocker with the four answers as toggle buttons (D-34-10). With no active blockers, the section shows one line, "No
inner blockers yet", with a link to the Assessment page.

---

## 3. Data model

Migration **V104**.

| Table or column | Detail |
| --- | --- |
| `inner_blockers` | id, user_id (not null), blocker (varchar 120, not null), shows_up (varchar 500), practice (varchar 300, not null), chain_key (varchar 32, one of the six score keys, null allowed), dream_id (null = all dreams), habit_id (null allowed), status (`ACTIVE`, `RESOLVED`), resolved_at, archived (default false), created_at, updated_at. Index on (user_id, archived, status) |
| `inner_blocker_checkins` | id, user_id, inner_blocker_id, review_id, answer (`YES`, `PARTLY`, `NO`, `NOT_DONE`), created_at. Unique (inner_blocker_id, review_id) |
| Plan draft content | Gains `innerBlockerIds` (list of ids, at most 10) (D-34-8) |

The dream, habit and review links use `ON DELETE SET NULL`, except `inner_blocker_checkins.review_id`, which is
deleted with the review. A dream or habit that is archived keeps its link.

---

## 4. API

Every endpoint follows the BRD 33 standard: `/api/v1`, `ResponseEntity`, DTO records, `@Valid`, `@Positive` ids,
`PagedResponse` and RFC 9457 errors.

| Endpoint | Detail |
| --- | --- |
| `GET /api/v1/inner-blockers?status=&dreamId=&includeAllDreams=&page=&size=` | `PagedResponse<InnerBlockerResponse>`, newest first; sortable by `createdAt`, `updatedAt` (D-33-10). `dreamId` with `includeAllDreams=true` returns that dream's blockers plus those for all dreams (stage 3) |
| `POST /api/v1/inner-blockers` | `InnerBlockerRequest { blocker, showsUp, practice, chainKey, dreamId }`; `201 Created` with `Location`. `409` when 10 are already active (BR-34-3) |
| `GET /api/v1/inner-blockers/{id}` | One blocker, with its last four check-ins |
| `PUT /api/v1/inner-blockers/{id}` | Edit the five fields |
| `PATCH /api/v1/inner-blockers/{id}/status` | `{ status, stopHabit }`; RESOLVED sets `resolved_at` |
| `POST /api/v1/inner-blockers/{id}/archive` and `/restore` | `204 No Content`; archive takes `{ stopHabit }` |
| `POST /api/v1/inner-blockers/{id}/habit` | `{ name }`; creates and links the habit (D-34-6); `201` |
| `PUT /api/v1/reviews/{id}/inner-blocker-checkins` | `{ checkins: [{ innerBlockerId, answer }] }`; weekly reviews only |
| `GET /api/v1/coach/participants/{id}/inner-blockers` | Read-only, only when the participant shares journals (D-34-11); otherwise `404` |
| Plan draft save | Accepts `innerBlockerIds`; plan save links them (D-34-8) |
| Account export | Gains `innerBlockers` and `innerBlockerCheckins` |

---

## 5. Business rules

| ID | Rule |
| --- | --- |
| BR-34-1 | Every endpoint is user-scoped: another user's blocker, check-in or review answers `404` (CLAUDE.md rule 13) |
| BR-34-2 | `dreamId` and the habit must belong to the same user; `chainKey` must be one of the six active chains (D-34-3) |
| BR-34-3 | At most 10 blockers that are active and not archived per user. Restoring or reopening one when 10 are already active answers `409` with the code `inner-blocker-limit` |
| BR-34-4 | Check-ins are accepted only for weekly reviews, one per blocker per review; a new answer replaces the old one |
| BR-34-5 | Archiving or resolving never changes the linked habit unless `stopHabit` is true; then the habit is archived, never deleted (BR-12) |
| BR-34-6 | A coach sees blockers only for a participant in an active cohort they coach, who shares journals, and only read-only (D-6, D-8). Admins never see them (D-23-3) |
| BR-34-7 | Inner blockers and check-ins are never included in any AI request (D-34-12); a test checks the prompt builders |
| BR-34-8 | Plan save links only blockers in `innerBlockerIds` that belong to the user and still have no dream (D-34-8) |

---

## 6. Pages and components

- **`InnerBlockerForm`** (`components/forms`): the shared form (§2.3).
- **`InnerBlockerCard`** and **`InnerBlockerList`** (`components/inner-blockers`): the three-part card, habit line
  and last check-ins.
- **`InnerStage`**: Add as my blocker on each chain, Your inner blockers, Add your own blocker (§2.1).
- **`ReviewStage`** (Check and add): the INFO line (D-34-9).
- **`AssessmentPage`**: the My inner blockers section (§2.2).
- **`ReviewsPage`**: the weekly check-in section (§2.4).
- **Coach participant view**: a read-only list when shared.
- **`api/innerBlockerApi.ts`** and types in `types/vision.ts`.
- All new text has Khmer drafts (`km-review-v34.csv`).

---

## Functional requirements

| ID | Requirement | Priority |
| --- | --- | --- |
| IB-FR-01 | Create, read, edit, resolve, archive and restore inner blockers with the three fields (D-34-2, D-34-7) | Must |
| IB-FR-02 | Optional link to one of the six chains and to one dream (D-34-3) | Must |
| IB-FR-03 | My inner blockers section on the Assessment page with filters (§2.2) | Must |
| IB-FR-04 | Add your own blocker and Your inner blockers in the Inner blockers stage (§2.1, D-34-4) | Must |
| IB-FR-05 | Add as my blocker from a named chain, prefilled and editable (D-34-5) | Should |
| IB-FR-06 | Link the draft's blockers to the dream at plan save (D-34-8) | Must |
| IB-FR-07 | INFO line in Check and add (D-34-9) | Should |
| IB-FR-08 | Make it a daily habit, with the habit line and streak on the card; ask before stopping the habit (D-34-6) | Must |
| IB-FR-09 | At most 10 active blockers (BR-34-3) | Must |
| IB-FR-10 | Weekly review check-in, last four answers on the card, "isn't helping yet" hint after two No answers (D-34-10) | Must |
| IB-FR-11 | Coach read-only view only when journals are shared; nothing for admins (D-34-11) | Must |
| IB-FR-12 | Account export and account deletion include inner blockers and check-ins (D-34-11) | Must |
| IB-FR-13 | Never sent to AI (D-34-12) | Must |

## Non-functional requirements

| ID | Requirement |
| --- | --- |
| IB-NFR-01 | Original wording; no text copied from the source book (BL-NFR-05). No diagnosis or labels (DC-FR-23) |
| IB-NFR-02 | WCAG 2.2 AA: the form fields are labelled with their limits announced; the check-in toggles are a named group; cards and the table are keyboard reachable |
| IB-NFR-03 | Works at 360px with no horizontal scroll; the table turns into cards on phones |
| IB-NFR-04 | Khmer drafts for all new text in `km-review-v34.csv`; no clipping at 360px |
| IB-NFR-05 | Backend tests: CRUD and ownership (404 for another user), chain and dream checks, 10-active limit, habit creation and stopHabit, plan save linking, weekly-only check-ins, coach shared and not shared, admin refused, export, no blocker text in AI prompts. Frontend tests: form validation, stage 3 add and prefill, Assessment section filters, Make it a daily habit, weekly check-in, the hint after two No answers |
| IB-NFR-06 | `mvn verify` (line ≥ 94%, branch ≥ 75%), `ControllerRulesTest` (BRD 33), frontend tests with `--testTimeout=20000`, and the SonarQube gate pass |

## Implementation phases

Each phase builds the backend and the frontend together, in full.

| Phase | Content |
| --- | --- |
| 1 | Migration V104; entity, repository, service, controller (`/api/v1/inner-blockers`); the shared form, card and list; My inner blockers on the Assessment page (IB-FR-01 to 03, 09); tests |
| 2 | Inner blockers stage: Add your own blocker, Your inner blockers, Add as my blocker; `innerBlockerIds` and plan save linking; Check and add INFO line (IB-FR-04 to 07); tests |
| 3 | Make it a daily habit (IB-FR-08); weekly check-in (IB-FR-10); tests |
| 4 | Coach view, account export and deletion, the AI exclusion test (IB-FR-11 to 13); tests |
| 5 | `km-review-v34.csv`, CHANGELOG, `UAT_V34.md`, `docs/API.md`, `docs/DATA_MODEL.md`, user guide section "My inner blockers (V34)" |

## Acceptance criteria

| ID | Criterion |
| --- | --- |
| IBC-01 | In stage 3, the participant adds "Inconsistency / Big bursts, then weeks of nothing / Never miss twice; 15 minutes counts". It shows under Your inner blockers and on the Assessment page, linked to the dream |
| IBC-02 | Saving without a blocker or a practice shows the field error; a 121-character blocker is refused |
| IBC-03 | Add as my blocker on "Giving up too soon" opens the form with the chain linked and the program's practice filled in; nothing is saved until Save |
| IBC-04 | A blocker added in a draft with no saved dream is linked to the new dream when the plan is saved; in an abandoned draft it stays "For all my dreams" |
| IBC-05 | Make it a daily habit creates a habit on the Today page; the card shows its streak. Archiving the blocker asks about the habit, and with No the habit stays |
| IBC-06 | With 10 active blockers, adding an 11th is refused with a clear message in both places |
| IBC-07 | In a weekly review, answering "No" for a blocker two weeks in a row shows "This practice isn't helping yet" on its card; daily reviews show no check-in section |
| IBC-08 | Another user's blocker id answers 404; a coach sees blockers only after the participant turns on journal sharing; an admin sees none |
| IBC-09 | The account export holds the blockers and check-ins; deleting the account removes them |
| IBC-10 | Dream Coach, plan building and weekly review drafts send no blocker text to the AI (checked in the usage log and by test) |
| IBC-11 | The Check and add summary shows "Inner blockers for this dream: n" and saving is not blocked |
| IBC-12 | Builds, tests, the coverage check, `ControllerRulesTest` and the SonarQube gate pass |

## Measuring the benefits

Measured 30 days before and 30 days after release.

| ID | Measure | Target |
| --- | --- | --- |
| IBM-01 | Share of saved plans whose stage 3 was "Skip for now" | Down by at least 10 points |
| IBM-02 | Share of active participants with at least one inner blocker | At least 30% |
| IBM-03 | Share of blockers with a linked habit | At least 40% |
| IBM-04 | Share of weekly reviews with at least one check-in | At least 50% of reviews by participants who have blockers |
| IBM-05 | Blockers marked "No longer holds me back" | Counted; no target in V34 |

## Open questions

| ID | Question | Proposed answer |
| --- | --- | --- |
| Q-34-1 | Should inner blockers have their own sharing switch, or reuse "Share journals with my coach"? | Reuse the journals switch in V34, renamed in the UI to "Share journals and inner blockers with my coach". Split later only if participants ask |
| Q-34-2 | Should AI suggest a practice for a blocker? | Not in V34. A later BRD could add it, with consent and a tick for that request, as with the fear journal (DC-FR-21) |
| Q-34-3 | Is 10 active blockers the right limit? | Yes. Most plan guides name 3 to 5, and a longer list stops being worked on |
| Q-34-4 | Should "Tutorial trap" or "Comparing yourself to others" become new chains in the six-chain check? | No. The six chains stay (D-17-3); own blockers cover the rest, with an optional chain link |
| Q-34-5 | Should the practice also show on the Today page without a habit? | No. The habit is the daily form of the practice (D-34-6) |

## Sign-off

| Role | Name | Date | Signature |
| --- | --- | --- | --- |
| Sponsor | | | |
| Product owner | | | |
| Design reviewer | | | |
| Khmer reviewer | | | |
