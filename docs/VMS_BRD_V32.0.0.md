# BRD – A faster, easier and more automatic Streamline AI

Nov 25, 2026

## Document control

This is draft 32.0.0. It keeps every requirement in BRD 31.0.0 and the earlier BRDs. It improves the **Streamline
review** (BRD 27) and its AI help (BRD 30) in five ways: suggestions are saved and shown again at once, the
Streamline AI can use its own model, a batch of applied suggestions can be undone in one click, the AI can draft a
whole review in one request, and the automation gaps left by BRD 27 are closed.

| Item | Value |
| --- | --- |
| Document | Business Requirements Document (BRD) – Streamline speed, ease of use and automation addendum |
| Version | 32.0.0 – Draft |
| Parent documents | BRD 27 (Streamline review; D-27-3 stage order, D-27-8 automation, D-27-9 AI suggestions under consent and limits, D-27-10 coach read-only, §6.2 candidates, BR-12 archive not delete), BRD 30 (D-30-1 to D-30-11; BR-30-7 suggestions never stored; Q-30-2 and Q-30-3), BRD 11 (PB-FR-39 a separate model for plan building), BRD 9 (AI consent, daily limit, monthly cap; AI-NFR-04 no names of people to AI), FR-77 (daily reminder email) |
| Business owner | To be confirmed |
| Product owner | To be confirmed |
| Approvers | Sponsor, Product owner, Design reviewer, Khmer reviewer |

| Version | Change | Author |
| --- | --- | --- |
| 32.0.0 | First draft, from the product owner's request of 25 Nov 2026 to write up all five recommendations for the Streamline AI (speed, ease of use, workflow automation) | BA |
| 32.0.0 (phase 1 built) | Phase 1 implemented, 25 Nov 2026 (migration **V99**: `streamline_suggestion_sets`, `ai_settings.streamline_model`). SF-FR-01 to SF-FR-04. Clarified during build: (1) D-32-1: the fingerprint covers the stage and the **ids and kinds** of the active items only, not titles, statuses or answers. If answers were included, applying one card would change the fingerprint and throw away every other card. Instead, each saved card is checked on its own when read back (BR-32-3) and left out once its item is gone or its change is made (a verdict, answer, decision, simplify, lever or automation already recorded). Questions stay until dismissed. (2) D-32-2: saved sets are deleted as soon as the review is completed or abandoned, not 90 days after completion, because a closed review can no longer use stage suggestions. (3) D-32-3: today Streamline suggestions, picks and advice already use the plan building model when one is set (V15 AH-NFR-02). The new setting overrides that for suggestions and picks only. Empty keeps today's rule (plan building model, else the main model), so advice and chat are unchanged. (4) Each later phase adds its own migration (V100 onwards) instead of one V99 for everything | Dev |
| 32.0.0 (phase 2 built) | Phase 2 implemented, 25 Nov 2026 (migration **V100**: `streamline_apply_batches`, `streamline_apply_batch_entries`). SF-FR-05 to SF-FR-07. `POST /api/streamline-reviews/{id}/apply-batch`, `POST …/batches/{batchId}/undo`, `GET …/batches?stage=`. Clarified during build: (1) The `stage` of Recent changes is optional and defaults to the current stage. (2) A batch where nothing applied leaves no entry and returns `batchId` null. (3) BR-32-6 compares update times taken at the **end** of the batch, so two actions on one item in the same batch do not block each other's undo. A record with no recorded time is treated as changed. (4) Undo restores a deleted or merged item without counting it as "brought back" (D-27-5 counts only Restore). (5) Undo of a repeat stops a rule the batch created (`active = false`, never deleted, BR-12) or puts back the old cadence and next date. A task the daily job already created from that rule stays. (6) A batch must be for the review's current stage. (7) After an undo, the saved suggestions (D-32-1) show the undone cards again | Dev |
| 32.0.0 (phase 3 built) | Phase 3 implemented, 25 Nov 2026 (migration **V101**: `streamline_suggestion_sets.drafted_at_stage`). SF-FR-08 to SF-FR-10. `POST /api/streamline-reviews/{id}/draft-all` (`STREAMLINE_DRAFT_ALL`, uses the Streamline model when set) and `GET …/drafts` (saved drafts for the current and later stages, `preview` for stages not reached, and `canDraftAll` with the reason when it cannot run). Clarified during build: (1) **Phase 1 fix:** the fingerprint now covers all the review's items, archived or not, not only the active ones. A review's items never change after it starts, so applying a delete at stage 2 no longer throws away that stage's other saved cards. Cards about cut items still drop out one by one (BR-32-3). D-32-1's "items added or removed" therefore never drops a set today; it is kept as a safety check. (2) D-32-8: "room in the monthly cap" means at least **US$0.50** left this month (`DRAFT_ALL_RESERVE_USD`), a cautious estimate for one large request. (3) Draft all stages drafts the current stage and the later stages not yet finished. It is offered only while there is a later stage, so not on stage 5. (4) The "Drafted before earlier changes" label shows when a draft was made at an earlier stage than the one it is for. The page words it as "Drafted before you finished the earlier stages" | Dev |
| 32.0.0 (phase 4 built) | Phase 4 implemented, 25 Nov 2026 (migration **V102**: `streamline_items.remind_cadence`/`remind_day`, `app_users.streamline_nudges_enabled`, `streamline_nudges`, `streamline_candidate_dismissals`). SF-FR-11 to SF-FR-15. Clarified during build: (1) D-32-9: the schedule is set through the existing item update (`remindCadence`, `remindDay` on `PUT …/items/{itemId}`), not a separate `…/automation` endpoint. A REMIND chosen without a schedule (for example from an AI card or Apply all) reminds **weekly on Monday** until the user picks another day. Batch undo puts the schedule back too. (2) The Today page lists due reminders whether or not email reminders are on. The email lists them only when reminders are on (BR-32-8). (3) D-32-11: "the user's weekly review day" is the day the weekly review is due (no weekly review in the last 7 days, the existing FR-67 rule), because VMS has no fixed weekly review weekday. The Dashboard is cached for up to 10 minutes, so its nudge card can lag that long. (4) D-32-12: a dismissal hides the candidate for 90 days for **that rule**, so it comes back sooner only if it meets a different rule. An AI pick dismissal is saved with the rule `AI_PICK`. Browser dismissals (kept 30 days before V32) move to the server the first time the candidate list loads, with the candidate's current rule. (5) D-32-10: candidates are weighted by how much slower the step got (ratio × 30), so a step that tripled ranks above one that doubled | Dev |
| 32.0.0 (phase 5 built) | Phase 5 completed, 25 Nov 2026 (no migration). SF-FR-16: a coach's read-only review shows no suggestions panel, Draft all stages, Recent changes, Undo or reminder settings, and every V32 review endpoint answers 404 to a coach (checked with a real cohort). `docs/UAT_V32.md`, the user guide section "Faster Streamline AI (V32)", and `docs/km-review-v32.csv` (42 strings) are complete. All five phases are built; UAT, the Khmer review and open questions Q-32-1 to Q-32-6 remain | Dev |

---

## Executive summary

BRD 30 made the Streamline AI useful. In use, five problems remain:

- **Waiting and paying twice.** Suggestions are not kept anywhere. Auto-suggest remembers only, in the browser
  session, that it has already run. A new tab, another device or the next day means a new request, a new wait (the
  answer arrives all at once, not word by word) and another request counted against the daily limit and the monthly
  cap.
- **One model for everything.** Short, structured suggestion cards use the same model as the long advice and the
  chat, so they are slower and cost more than they need to.
- **Apply all feels risky.** Apply all runs card by card, but nothing undoes the whole batch. A user who regrets a
  batch has to restore and re-edit items one at a time, so many users apply cards one by one instead.
- **Five waits per review.** Each stage asks the AI separately, so one review means five waits spread over five
  screens. Many reviews stop at stage 2 or 3.
- **"Automate" is partly a note.** Choosing REMIND only records the choice. The "cycle time doubled" candidate of
  BRD 27 §6.2 was never built (Q-30-2). Candidates the user dismisses are remembered only in that browser.

V32 fixes these with five features (§2). Every rule of BRD 27 and BRD 30 still applies. The AI **suggests only**,
and nothing changes until the user applies a suggestion. The stage order lock stays. AI needs the user's consent and
respects the daily limit and the monthly cap. The AI never sees names of people. Everything works without AI, and
the page says why when AI is missing.

### Expected benefits

| # | Feature | Benefit to users | Benefit to the project |
| --- | --- | --- | --- |
| 1 | Saved suggestions | Cards show at once when the user comes back | Fewer repeat requests, so lower cost and less of the daily limit used |
| 2 | Streamline model setting | Faster suggestion cards | The monthly cap covers more reviews; the stronger model is kept for advice |
| 3 | Undo a batch | Apply all can be used without worry | Answers Q-30-3; BR-12 stays easy to trust |
| 4 | Draft the whole review | One wait instead of five; a review takes minutes | More reviews finished, so plans really are trimmed |
| 5 | Real automation | REMIND works; the app spots slow steps by itself | Closes the BRD 27 gaps and Q-30-2 |

These benefits are estimates. "Measuring the benefits" says how they are measured after release.

---

## Decisions recorded by this BRD

| ID | Decision |
| --- | --- |
| D-32-1 | **Saved suggestions.** The last AI suggestions for each review and stage are stored on the server with a **fingerprint** of what the AI was shown (the stage, and each active item's id, title, status and stage answers). If the fingerprint still matches, opening the stage shows the saved cards at once, labelled "Suggested on <date>", with no request. If it does not match, the saved cards are dropped and auto-suggest asks again (D-30-5). **Suggest again** always asks fresh and replaces the saved set. Applied or dismissed cards are removed from the saved set. This **replaces BR-30-7** for stage suggestions only. AI picks, summary advice and chat answers are still not stored |
| D-32-2 | Saved suggestions contain only what the AI was already allowed to see (D-30-3): codes, titles, statuses, numbers and source types, never names of people. They are user-scoped, never shown to a coach (D-30-11), deleted when the review is archived or 90 days after it is completed, and deleted with the account |
| D-32-3 | **Streamline model setting.** AI settings gain **Streamline suggestions model**, following PB-FR-39. When it is set, it is used for `STREAMLINE_SUGGEST`, `STREAMLINE_PICK` and the new `STREAMLINE_DRAFT_ALL` (D-32-7). Summary advice (`STREAMLINE_ADVICE`) and the page chat keep the main model. Empty means the main model, which is the default until the comparison in Q-32-1 is done. Usage logs and costs record the model actually used, as today |
| D-32-4 | **Batch apply on the server.** Apply selected and Apply all are sent as one batch request. The server applies the actions one after another, in the order shown, each through the existing review action and its rules (BR-27-1 to BR-27-12). The first failure stops the run, and the actions already applied stay applied (D-30-6 unchanged). Before each action the server records what it is about to change, so the batch can be undone |
| D-32-5 | **Undo a batch.** After a batch, the panel shows "Applied n suggestions. **Undo**". Undo reverses the batch's actions in reverse order: an archived item is restored, an edited title or description goes back, stage answers go back to what they were, a merge is restored and a moved item goes back. Undo is possible until the user completes that stage, or for 24 hours, whichever comes first. An item changed again since the batch is **not** undone; it is listed as "Changed since, left as it is" |
| D-32-6 | Undo is offered for batches only. A single card applied on its own keeps the actions it has today (for example the Restore message after a delete). Apply all does **not** ask for confirmation first: the undo is the safety net. This answers Q-30-3 |
| D-32-7 | **Draft the whole review.** An open review offers **Draft all stages**, which asks the AI once (`STREAMLINE_DRAFT_ALL`) for suggestions for the current stage and every later stage. The drafts are saved as suggestion sets (D-32-1), one per stage. The **stage order lock stays** (D-27-3): the user approves only the current stage. Later stages show their drafts as a read-only preview. When the user reaches a stage, its drafted cards become that stage's suggestions. Cards about items removed or merged in an earlier stage are dropped by themselves. The other cards stay, marked "Drafted before earlier changes" |
| D-32-8 | Draft all stages is **one** request toward the daily limit. Because it costs more than one stage, it is offered only when the monthly cap has room for it (the same estimate as other large requests). If the user stops halfway, the cost is already spent; the drafts stay saved until the review is completed or archived |
| D-32-9 | **REMIND really reminds.** Choosing REMIND for an item in stage 5 asks for **how often** (daily, weekly on a weekday, monthly on a day), using the same choices as repeating tasks. On a due day, the item appears as a line in the daily reminder email (FR-77) and on the Today page. If the user's reminders are off, the stage 5 card says "Turn on reminders in Settings for this to reach you" with a link. A reminder stops when the item is completed or archived, or when the user changes its choice |
| D-32-10 | **The "cycle time doubled" candidate** (BRD 27 §6.2, Q-30-2). A step is a candidate when at least 3 of its tasks were completed in the last 90 days and at least 3 in the 90 days before that, all with a start time, and the recent median cycle time is at least twice the earlier one. It joins the other rule candidates and their ranking (D-30-4). Tasks without a start time are left out, never guessed (D-27-7) |
| D-32-11 | **Weekly Streamline nudge.** On the user's weekly review day, the daily reminder email and the Dashboard show at most **one** nudge for the highest-ranked rule candidate, for example "Step S-014 is taking twice as long as it used to. Streamline it?", with a link that starts the review. The same goal or step is nudged at most once every 30 days. Reminder settings gain **Streamline nudges**, which is on by default when reminders are on (Q-32-3). Nudges use rules only, never AI |
| D-32-12 | **Dismissed candidates on the server.** Dismissing a rule candidate or an AI pick stores it for the user, so it stays dismissed on every device. A dismissal lasts 90 days, or until the candidate meets a **different** rule, whichever comes first. Dismissals already kept in the browser are sent to the server the first time the page loads, then removed from the browser |
| D-32-13 | A coach viewing a participant's review read-only (D-27-10) sees no V32 controls: no saved suggestions, no drafts, no undo and no nudges |

---

## 1. Fit analysis

| Need | Today | Gap |
| --- | --- | --- |
| See suggestions again | Auto-suggest once per browser session (D-30-5) | New tab, device or day means a new paid request and a wait |
| Fast, cheap suggestion cards | One model for all AI, plus the plan model (PB-FR-39) | No separate model for Streamline |
| Apply many suggestions safely | Apply all, stop on failure (D-30-6) | No undo for a batch |
| Get through a review quickly | One AI request per stage | Five waits and five screens |
| Be reminded about a REMIND item | The choice is recorded | Nothing is sent |
| Spot steps that are slowing down | Rule candidates without the cycle-time rule | §6.2 rule missing (Q-30-2); no nudge |
| Dismiss a candidate | Kept in that browser only | Comes back on other devices |

---

## 2. The five features

### 2.1 Saved suggestions (speed)

- Opening a stage whose saved set still matches shows the cards at once, with the line "Suggested on 25 Nov 2026.
  **Suggest again**".
- When the items have changed since, the old cards are not shown. Auto-suggest asks again, following D-30-5.
- Without AI, saved cards that still match are **still shown** and can still be applied, because applying uses no
  AI. A note says new suggestions are not available and why.

### 2.2 Streamline model setting (speed)

- On the AI settings page, under the main model and the plan building model: **Streamline suggestions model**, with
  the same model list and "Use the main model" as the first choice.
- The help text says: "Suggestions are short. A faster model such as Haiku answers sooner and costs less. Advice and
  chat keep the main model."

### 2.3 Undo a batch (ease of use)

- After Apply selected or Apply all: "Applied 5 suggestions. **Undo**", or on a failure "Applied 2 of 5. The rest
  were not applied. **Undo**".
- Undo shows "Undid 5 changes." or "Undid 4 changes. 1 item was changed since and was left as it is: S-014."
- The Undo button stays on the stage until the stage is completed or 24 hours pass. Each batch is listed under
  **Recent changes** on that stage, newest first.

### 2.4 Draft the whole review (ease of use)

- On an open review, next to the stage title: **Draft all stages**. While it runs: "Drafting stages 2 to 5…".
- When done, a strip shows each stage with its number of drafted cards. The current stage's cards are ready to apply.
  Later stages open as a read-only preview: "You can apply these when you reach this stage."
- Without AI, or when the monthly cap has no room, the button is disabled and says why.

### 2.5 Real automation (workflow automation)

- **REMIND**: choosing "Remind me" in stage 5 opens a small form: how often, and which day. The item card then shows
  "Reminds you every Monday".
- **Cycle time doubled**: a new candidate card, "Step S-014: tasks now take 6.5 days, up from 3.0 days. Streamline
  it?"
- **Weekly nudge**: one line in the weekly-day reminder email and one card on the Dashboard, with **Start review**
  and **Not now** (which dismisses it as in D-32-12).
- **Dismissals**: the same Dismiss button as today, now saved on the server.

---

## 3. Data model

Migration **V99** (phase 1) holds the suggestion sets and the Streamline model; each later phase adds its own migration.

| Table or column | Detail |
| --- | --- |
| `streamline_suggestion_sets` | id, user_id, review_id, stage, fingerprint (SHA-256, hex), source (`STAGE` or `DRAFT_ALL`), model, cards (JSON), created_at. One row per review and stage; a new answer replaces it |
| `streamline_apply_batches` (V100) | id, user_id, review_id, stage, total, applied_count, failed_at_position (null when all applied), error, created_at, undone_at |
| `streamline_apply_batch_entries` (V100) | id, batch_id, position, action, item_id, before_state (JSON), item_updated_at, target_updated_at, other_updated_at (the merge target): the update times at the end of the batch, used to detect later changes |
| `streamline_items.remind_cadence`, `remind_day` | The REMIND schedule (D-32-9): `DAILY`, `WEEKLY`, `MONTHLY`, and the weekday or day of the month |
| `streamline_candidate_dismissals` | id, user_id, anchor_type, anchor_id, rule (the rule code, or `AI_PICK`), dismissed_at |
| `streamline_nudges` | id, user_id, anchor_type, anchor_id, rule, sent_at; used for the 30-day limit (D-32-11) |
| `ai_settings.streamline_model` | Null uses the main model (D-32-3) |
| `app_users.streamline_nudges_enabled` | Default true (D-32-11) |

---

## 4. API

| Change | Detail |
| --- | --- |
| `GET /api/streamline-reviews/{id}/stages/{stage}/suggestions` | The saved set when its fingerprint still matches: `{ cards, createdAt, source, stale: false }`; otherwise `204 No Content`. Uses no AI |
| `POST /api/streamline-reviews/{id}/stages/{stage}/suggestions` | Unchanged request; the answer is now also saved (D-32-1) |
| `POST /api/streamline-reviews/{id}/draft-all?language=` | `STREAMLINE_DRAFT_ALL` (D-32-7): `{ requestId, stages: [{ stage, cards }] }`; saves one set per stage |
| `POST /api/streamline-reviews/{id}/apply-batch` | `{ stage, actions: [...] }`, each action in the same shape as the existing single actions. Answer: `{ batchId, applied, failedAtPosition, error }` (D-32-4) |
| `POST /api/streamline-reviews/{id}/batches/{batchId}/undo` | `{ undone, skipped: [{ code, reason }] }` (D-32-5) |
| `GET /api/streamline-reviews/{id}/batches?stage=` | Recent batches for the Recent changes list |
| `PUT /api/streamline-reviews/{id}/items/{itemId}/automation` | Gains `remindCadence` and `remindDay` when the choice is REMIND |
| `POST` / `GET /api/streamline-reviews/candidate-dismissals` | Store or list dismissals (D-32-12); the POST accepts a list, for the one-time move from the browser |
| `GET /api/streamline-reviews/candidates` | Gains the `CYCLE_TIME_DOUBLED` rule; leaves out dismissed candidates |
| `GET /api/dashboard` | `streamline` gains `nudge` (at most one) |
| AI settings and reminder settings | Gain `streamlineModel` and `streamlineNudgesEnabled` |

---

## 5. Business rules

| ID | Rule |
| --- | --- |
| BR-32-1 | Every V32 endpoint is user-scoped: another user's review, batch or dismissal answers 404, before AI is checked |
| BR-32-2 | A saved set is shown only when its fingerprint matches the review as it is now (D-32-1); cards about items no longer active are never shown |
| BR-32-3 | A saved card is applied through the same checks as a fresh card (BR-30-3, BR-30-4 and the BRD 27 rules); a card that no longer passes is dropped, with a short message |
| BR-32-4 | A batch holds at most 50 actions. QUESTION cards are never part of a batch (D-30-6) |
| BR-32-5 | Undo works only on the user's own batch, only once, only while its stage is the current stage and less than 24 hours have passed (D-32-5) |
| BR-32-6 | Undo never changes an item whose updated time is later than its `target_updated_at`; it lists it as skipped |
| BR-32-7 | Draft all stages drafts only the current stage and later ones; it never changes the stage order or completes a stage (D-27-3) |
| BR-32-8 | A REMIND reminder is sent only when the user's reminders are on, at most once a day per item, and never for a completed or archived item |
| BR-32-9 | The cycle-time-doubled rule uses only tasks with both a start time and a completed time (D-27-7) |
| BR-32-10 | At most one Streamline nudge a week per user, and at most one every 30 days for the same goal or step (D-32-11) |
| BR-32-11 | A dismissal hides a candidate for 90 days, or until it meets a different rule (D-32-12) |

---

## 6. Pages and components

- **`StreamlineSuggestionsPanel`**: loads the saved set first; the "Suggested on" line; batch apply through the new
  endpoint; Undo and Recent changes.
- **Review page**: Draft all stages, the stage strip, read-only previews of later stages.
- **Stage 5**: the Remind me form and the "Reminds you…" line.
- **Streamline list page**: dismissals from the server; the one-time move from the browser; the new candidate.
- **Dashboard**: the nudge card.
- **AI settings page**: Streamline suggestions model.
- **Reminder settings**: Streamline nudges switch.
- **Reminder email and Today page**: REMIND lines; the weekly nudge line.
- All new text has Khmer drafts (`km-review-v32.csv`).

---

## Functional requirements

| ID | Requirement | Priority |
| --- | --- | --- |
| SF-FR-01 | Save stage suggestions with a fingerprint and show them at once when it matches; Suggest again replaces them (D-32-1) | Must |
| SF-FR-02 | Saved suggestions stay applicable without AI, with the reason new ones are missing (§2.1) | Must |
| SF-FR-03 | Delete saved suggestions when the review is archived, 90 days after completion, and with the account (D-32-2) | Must |
| SF-FR-04 | Streamline suggestions model in AI settings, used for SUGGEST, PICK and DRAFT_ALL (D-32-3) | Must |
| SF-FR-05 | Batch apply on the server, one action at a time, stop on failure, record the state before each change (D-32-4) | Must |
| SF-FR-06 | Undo a batch, in reverse order, skipping items changed since, within the stage and 24 hours (D-32-5) | Must |
| SF-FR-07 | Recent changes list per stage | Should |
| SF-FR-08 | Draft all stages in one request, saved per stage, with the stage lock kept and later stages read-only (D-32-7) | Must |
| SF-FR-09 | Drop drafted cards about items removed in earlier stages; mark the others (D-32-7) | Must |
| SF-FR-10 | Draft all stages offered only when the monthly cap has room (D-32-8) | Must |
| SF-FR-11 | REMIND with cadence and day; reminder email line (D-32-9) | Must |
| SF-FR-12 | REMIND line on the Today page (D-32-9) | Should |
| SF-FR-13 | Cycle-time-doubled candidate, ranked with the others (D-32-10) | Must |
| SF-FR-14 | Weekly Streamline nudge in the email and on the Dashboard, with the settings switch (D-32-11) | Should |
| SF-FR-15 | Dismissals saved on the server, 90 days or until a different rule; one-time move from the browser (D-32-12) | Must |
| SF-FR-16 | No V32 controls in a coach's read-only view (D-32-13) | Must |

## Non-functional requirements

| ID | Requirement |
| --- | --- |
| SF-NFR-01 | Opening a stage with a matching saved set shows the cards within 1 second on a normal connection, with no AI request |
| SF-NFR-02 | No names of people are stored in saved sets or sent to the AI (D-30-3, D-32-2), checked by tests |
| SF-NFR-03 | A batch of 50 actions and its undo each finish within 5 seconds on the 50-step / 500-task test plan of BRD 27 |
| SF-NFR-04 | WCAG 2.2 AA: the Undo result is announced; the stage strip and previews are keyboard reachable and named; the Remind me form is labelled |
| SF-NFR-05 | Khmer drafts for all new text in `km-review-v32.csv`; no clipping at 360px |
| SF-NFR-06 | Backend tests: fingerprint match and mismatch, ownership, batch stop on failure, undo of each action type, undo skip and time limit, draft-all stage lock, REMIND sending, the doubled rule (including tasks without a start time), the nudge limits, dismissal expiry. Frontend tests: saved cards, Suggest again, Undo, Draft all, Remind me, nudge card, dismissal move |
| SF-NFR-07 | `mvn verify` (line ≥ 94%, branch ≥ 75%) and the SonarQube gate pass |

## Implementation phases

Each phase builds the backend and the frontend together.

| Phase | Content |
| --- | --- |
| 1 | Migration V99; saved suggestions (SF-FR-01 to 03); Streamline suggestions model (SF-FR-04); tests |
| 2 | Batch apply and undo, Recent changes (SF-FR-05 to 07); tests |
| 3 | Draft all stages (SF-FR-08 to 10); tests |
| 4 | REMIND, cycle-time-doubled candidate, weekly nudge, server dismissals (SF-FR-11 to 15); tests |
| 5 | Coach view check (SF-FR-16), `km-review-v32.csv`, CHANGELOG, `UAT_V32.md`, user guide section |

## Acceptance criteria

| ID | Criterion |
| --- | --- |
| SFC-01 | Suggestions shown on stage 2, then the page reopened in a new browser: the same cards show at once, labelled "Suggested on", and no AI request is logged |
| SFC-02 | After a step's title is edited, reopening stage 2 does not show the old cards; auto-suggest asks again |
| SFC-03 | With AI consent turned off, matching saved cards still show and can be applied, with a note that new ones are not available |
| SFC-04 | With Streamline suggestions model set to Haiku 5.5, a stage suggestion is logged with Haiku and summary advice with the main model |
| SFC-05 | Apply all on 5 cards, one archiving a step and one editing a title, then Undo: the step is restored and the title is back |
| SFC-06 | A batch is applied, one of its steps is edited by hand, then Undo: that step is listed as changed since and kept; the rest are undone |
| SFC-07 | After the stage is completed, or after 24 hours, the batch has no Undo |
| SFC-08 | Draft all stages from stage 1 makes one AI request and shows drafts for stages 1 to 5; stages 2 to 5 cannot be applied until reached; a drafted card about a step deleted in stage 2 is not shown in stage 3 |
| SFC-09 | With too little room under the monthly cap, Draft all stages is disabled and says why |
| SFC-10 | An item set to REMIND weekly on Monday appears in Monday's reminder email and on the Today page, and not after it is completed |
| SFC-11 | A step whose recent median cycle time is twice the earlier one appears as a candidate; tasks without start times are not counted |
| SFC-12 | On the weekly review day, one nudge shows in the email and on the Dashboard; the same step is not nudged again within 30 days; with Streamline nudges off, none shows |
| SFC-13 | A candidate dismissed on one device does not show on another; it returns after 90 days, or sooner when it meets a different rule |
| SFC-14 | A coach viewing a participant's review sees no saved cards, drafts, Undo or nudges |
| SFC-15 | Builds, tests, the coverage check and the SonarQube gate pass |

## Measuring the benefits

Measured from the AI usage log and review records, 30 days before and 30 days after release.

| ID | Measure | Target |
| --- | --- | --- |
| SFM-01 | Stage suggestion requests per completed review | Down by at least 30% |
| SFM-02 | Average Streamline AI cost per completed review | Down by at least 30% |
| SFM-03 | Share of applied cards applied through Apply selected or Apply all | Up |
| SFM-04 | Share of started reviews that reach stage 5 | Up by at least 10 points |
| SFM-05 | Reviews started from a nudge or the cycle-time-doubled candidate | Counted; no target in V32 |
| SFM-06 | Batches undone, as a share of all batches | Watched; above 20% means the suggestions need work |

## Open questions

| ID | Question | Proposed answer |
| --- | --- | --- |
| Q-32-1 | Should the Streamline suggestions model default to Haiku 5.5? | Not at first. Compare Haiku and the main model on 20 real reviews during UAT (accepted cards, dropped drafts); default to Haiku only if accepted cards fall by less than 10% |
| Q-32-2 | Is 24 hours the right undo window? | Yes: long enough to notice a mistake, short enough that the before-state is still meaningful. The stage-completed limit stays either way |
| Q-32-3 | Should Streamline nudges be on by default? | Yes, for users who already have reminders on; one a week at most |
| Q-32-4 | Can Draft all stages run from any stage, or only stage 1? | From any stage; it drafts the current stage and later ones |
| Q-32-5 | Is 90 days right for a dismissal? | Yes, matching the 90-day follow-up of BRD 27 |
| Q-32-6 | Should REMIND also send a phone notification? | Not in V32; VMS has no push notifications. Email and the Today page only |

## Sign-off

| Role | Name | Date | Signature |
| --- | --- | --- | --- |
| Sponsor | | | |
| Product owner | | | |
| Design reviewer | | | |
| Khmer reviewer | | | |
