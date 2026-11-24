# BRD – Archiving several items at once on the Vision Map

Nov 24, 2026

## Document control

This is draft 29.0.1. It keeps every requirement in BRD 28.0.1 and the earlier BRDs. It adds **tick boxes on every row
of the Vision Map tree** (dream, goals, steps and tasks), so the user can select one or several items and archive them
in one go.

29.0.0 also asked for an archive reason in a text box. The product owner clarified on 24 Nov 2026 that a reason was
never wanted (D-29-9): the text box is removed. The backend keeps the optional reason field and endpoint from 29.0.0,
so nothing breaks and the few reasons saved that day still show on archived rows.

| Item | Value |
| --- | --- |
| Document | Business Requirements Document (BRD) – Vision Map multi-select archive addendum |
| Version | 29.0.1 – Draft |
| Parent documents | CLAUDE.md business rules (BR-12 archive instead of delete, BR-13 and BR-14 user scoping), the archive cascade and impact counts (archiving a parent archives everything under it; restoring a child brings its parents back as Paused), the list-page "Archive selected" action, BRD 16 (Vision Map tree and timeline), BRD 28 (blocker analysis) |
| Business owner | To be confirmed |
| Product owner | To be confirmed |
| Approvers | Sponsor, Product owner, Design reviewer, Khmer reviewer |

| Version | Change | Author |
| --- | --- | --- |
| 29.0.1 | Product owner's clarification (24 Nov 2026): "I don't mean a confirmation reason; I want checkboxes on the tree so I can select and archive one or multiple items at once". Tick boxes are **always visible** (no Select button, D-29-5 revised), the **dream row** gets one too (D-29-10), and the reason text box is **removed** everywhere (D-29-9). AR-FR-01 to AR-FR-03 withdrawn; AR-FR-07 and AR-FR-08 revised; AR-FR-10 added. The archive reason API (AR-FR-04 to AR-FR-06) stays for compatibility | BA |
| 29.0.0 | First draft, from the product owner's request of 24 Nov 2026 ("under vision mapping page while i want archive one or multiple don't has text box for text to archive"). Product owner's answers the same day: the reason is **optional**; the Vision Map gets a **select mode** for archiving several items; the reason box also appears on the **Dreams, Goals, Steps and Tasks list pages** | BA |

---

## Executive summary

Archiving is how VMS removes plan items without deleting them (BR-12). Today every archive asks only "Are you sure?".
The user cannot say **why** an item was archived, so months later an archived goal or step gives no clue whether it
was finished another way, dropped on purpose, replaced, or archived by mistake. On the Vision Map the user can also
archive only **one item at a time**; archiving several steps or tasks means opening one menu after another.

V29 adds:

1. **An archive reason** (optional, up to 1,000 characters) in every archive confirmation for a dream, goal, step or
   task: the Vision Map tree, the Vision Map timeline, and "Archive selected" on the Dreams, Goals, Steps and Tasks
   pages. The reason is saved on the item and on everything archived with it.
2. **Select mode on the Vision Map tree**: a "Select" switch shows a tick box on every goal, step and task, and a bar
   with "Archive selected (n)". One confirmation, with one reason, archives them all.
3. **The reason shown** on archived rows when "Show archived" is on, so the user can decide whether to restore.

---

## Decisions recorded by this BRD

| ID | Decision |
| --- | --- |
| D-29-1 | *(Superseded in the interface by D-29-9.)* The reason is **optional** (product owner, 24 Nov 2026). Leaving it empty archives exactly as today. Up to 1,000 characters; surrounding spaces are trimmed and an empty text is stored as no reason |
| D-29-2 | Scope: **dreams, goals, steps and tasks** (the plan hierarchy). Vision areas, partners, obstacles, messages and reviews keep today's confirmation; a later BRD may extend the reason to them (Q-29-2) |
| D-29-3 | The reason is saved on the archived item and on every descendant **newly** archived with it by the cascade. A descendant that was already archived keeps its own earlier reason |
| D-29-4 | **Restoring** an item clears its archive reason, and so does a parent brought back by restoring a child. A later archive asks again |
| D-29-5 | *(Revised in 29.0.1.)* Tick boxes are on the **Vision Map tree** only (not the timeline), **always visible** on every row of the dream shown that is not archived: the dream, its goals, steps and tasks. There is no Select button. Archived rows have no tick box. When a goal or step and something under it are both selected, only the top item is archived; the cascade archives the rest with the same reason |
| D-29-6 | One reason applies to everything archived in one confirmation, in select mode and with "Archive selected" on the list pages |
| D-29-7 | The existing archive endpoints keep working unchanged (no reason). The reason travels on a new `POST /{id}/archive` endpoint, so no client breaks |
| D-29-9 | (29.0.1) **No archive reason text box** in any confirmation. Archiving from row menus, the timeline and the list pages works exactly as before V29. The `archive_reason` column, the `POST /{id}/archive` endpoint and `archiveReason` in responses stay (unused by the interface), and an archived row that has a reason saved still shows "Archived: {reason}" |
| D-29-10 | (29.0.1) Ticking the **dream** archives the whole dream; every other ticked row is under it and is archived by the cascade |
| D-29-8 | The reason is the user's own plan data: user-scoped like the item (BR-13, BR-14), visible to a coach only where the coach can already read the item (D-6), never to admins (D-23-3) |

---

## 1. Fit analysis: what VMS has today

| Need | What exists today | Gap |
| --- | --- | --- |
| Say why an item is archived | Confirmation dialog with the impact count ("also archives 3 steps and 9 tasks") | No text box; nothing is stored |
| Archive several items | "Archive selected" on list pages | Not on the Vision Map; list pages give no reason either |
| Decide whether to restore | "Show archived" lists archived rows with Restore and Delete permanently | No clue why each was archived |

---

## 2. Behaviour

### 2.1 The archive confirmation (withdrawn in 29.0.1, D-29-9)

Every archive confirmation for a dream, goal, step or task keeps its current message (including the impact count) and
adds:

- a text box labelled **"Why are you archiving this? (optional)"**, at most 1,000 characters;
- the hint "Saved with the archived item. Restoring clears it."

The confirm button stays enabled whether or not a reason is written (D-29-1).

### 2.2 Tick boxes on the Vision Map tree (29.0.1)

- Every row of the tree that is not archived (the dream, its goals, steps and tasks) shows a tick box, named after its
  row for screen readers ("Select Write the report").
- As soon as one row is ticked, a bar appears above the tree: "{n} selected", **Archive selected (n)** and **Clear**.
  Clear unticks everything and hides the bar. Changing the dream shown clears the selection.
- **Archive selected** opens one plain confirmation: "Archive {n} item(s)? Everything under them is archived too.
  Everything can be restored later with "Show archived"." with Archive and Cancel. There is no text box (D-29-9).
- On confirm, VMS archives the ticked top items in order (dream, goals, steps, tasks), skipping any item under another
  ticked item (D-29-5, D-29-10), then reloads the map and clears the selection.
- If one archive fails, the others already archived stay archived; the bar shows how many were archived and that the
  rest failed.

### 2.3 List pages (withdrawn in 29.0.1, D-29-9)

"Archive selected" and the row menu's Archive on the **Dreams, Goals, Steps and Tasks** pages add the same reason box
to their confirmation. With "Archive selected", the one reason is saved on every item archived (D-29-6). On the Steps
page, a step that created a partner still asks "Archive that partner too?" after the confirmation, and the reason
carries through either answer.

### 2.4 Showing the reason (kept for reasons already saved)

When "Show archived" is on, an archived dream, goal, step or task on the Vision Map tree shows a line under its title:
"Archived: {reason}". Items archived without a reason show nothing extra. Every plan item response carries
`archiveReason`, so other pages can show it later.

---

## 3. Data

| Change | Detail |
| --- | --- |
| Migration `V95__archive_reason.sql` | `archive_reason VARCHAR(1000)` on `dreams`, `goals`, `vision_steps` and `task_items`; nullable; no backfill |
| Entities | `archiveReason` on Dream, Goal, VisionStep and TaskItem; `archive(reason)` sets it only when the item is not yet archived; `restore()` clears it |
| Responses | `archiveReason` on DreamResponse, GoalResponse, VisionStepResponse and TaskItemResponse |

---

## 4. Business rules

| ID | Rule |
| --- | --- |
| BR-29-1 | The reason is optional; at most 1,000 characters; blank is stored as null (D-29-1). Longer text answers 400 |
| BR-29-2 | Only the user's own items can be archived (404 otherwise), as today |
| BR-29-3 | The cascade copies the reason only to descendants that were not already archived (D-29-3) |
| BR-29-4 | Restore clears the reason on the restored item and on parents it brings back (D-29-4) |
| BR-29-5 | Archiving an already archived item through the new endpoint changes nothing, including its reason |
| BR-29-6 | Every other archive rule is unchanged: impact counts, the step's "also archive the partner it created" option (BL-FR-21), the ideal partner profile following its step, progress roll-up |

---

## 5. Pages and components

- **`ArchiveConfirmDialog`** (new, common): message, optional reason box, confirm and cancel.
- **`RowActionsMenu`**: uses `ArchiveConfirmDialog` when asked for a reason; passes the reason to `onArchive`.
- **Vision Map tree** (`VisionMapTree`): reason for dream, goal, step and task archive; Select switch, tick boxes and
  the selection bar; "Archived: {reason}" on archived rows.
- **Vision Map timeline** (`useTimelineActions`): reason in its archive confirmation.
- **`BulkArchiveAction`**: optional reason box, used by the Dreams, Goals, Steps and Tasks pages.
- All new text has Khmer drafts (`km-review-v29.csv`).

---

## Functional requirements

| ID | Requirement | Priority |
| --- | --- | --- |
| AR-FR-01 | ~~Optional archive reason box on the Vision Map tree~~ Withdrawn in 29.0.1 (D-29-9) | – |
| AR-FR-02 | ~~The same on the Vision Map timeline~~ Withdrawn in 29.0.1 (D-29-9) | – |
| AR-FR-03 | ~~The same on the Dreams, Goals, Steps and Tasks pages~~ Withdrawn in 29.0.1 (D-29-9) | – |
| AR-FR-04 | `POST /api/{dreams,goals,steps,tasks}/{id}/archive` with `{ reason }` (steps also `removePartner`); the old `DELETE` keeps working | Must |
| AR-FR-05 | Save the reason on the item and newly archived descendants; restore clears it (D-29-3, D-29-4) | Must |
| AR-FR-06 | `archiveReason` on the four plan item responses | Must |
| AR-FR-07 | Always-visible tick boxes on the dream, goal, step and task rows of the Vision Map tree; the selection bar with Archive selected and Clear; a plain confirmation (§2.2) | Must |
| AR-FR-08 | Skip selected items that sit under another selected item (D-29-5) | Must |
| AR-FR-09 | Show "Archived: {reason}" on archived rows of the Vision Map tree when a reason was saved | Should |
| AR-FR-10 | Ticking the dream archives the whole dream; other ticked rows are left to the cascade (D-29-10) | Must |

## Non-functional requirements

| ID | Requirement |
| --- | --- |
| AR-NFR-01 | User scoping on every new endpoint; another user's item answers 404 |
| AR-NFR-02 | WCAG 2.2 AA: the reason box has a visible label; each tick box is named after its row ("Select Write the report"); the selection bar announces the count |
| AR-NFR-03 | Khmer drafts for all new text in `km-review-v29.csv`; no clipping at 360px |
| AR-NFR-04 | Backend tests: reason saved, cascaded only to newly archived children, cleared on restore, length limit, ownership, old endpoint unchanged. Frontend tests: reason passed from the tree menu, the bulk action and select mode, and the skip rule |
| AR-NFR-05 | The backend coverage check (`mvn verify`, line ≥ 94%, branch ≥ 75%) and the SonarQube quality gate still pass |

## Data and API changes

- Migration `V95__archive_reason.sql` (§3).
- New endpoints: `POST /api/dreams/{id}/archive`, `POST /api/goals/{id}/archive`, `POST /api/steps/{id}/archive`,
  `POST /api/tasks/{id}/archive`, each with an optional body `{ "reason": "...", "removePartner": false }`
  (`removePartner` applies to steps only). 204 No Content.
- Responses gain `archiveReason`.

## Implementation phases

| Phase | Content |
| --- | --- |
| 1 | Migration V95, entity fields, cascade with reason, restore clearing, new endpoints, responses; backend tests |
| 2 | `ArchiveConfirmDialog`, `RowActionsMenu`, tree and timeline reason, list-page bulk reason, API clients; tests |
| 3 | Select mode on the tree, the skip rule, "Archived: {reason}" on archived rows; tests |
| 4 | `km-review-v29.csv`, CHANGELOG, `UAT_V29.md`, user guide section |

## Acceptance criteria

| ID | Criterion |
| --- | --- |
| ARC-01 | *(API only after 29.0.1.)* Archiving a goal through `POST /api/goals/{id}/archive` with the reason "Replaced by a new goal" saves the reason on the goal and on its steps and tasks that were not archived before; a task archived earlier keeps its own reason |
| ARC-02 | Archiving from any row menu, the timeline or a list page shows the plain confirmation, with no text box, and works exactly as before V29 |
| ARC-03 | Restoring the goal clears its reason; restoring a task under an archived goal clears the reasons of the task and of the parents brought back |
| ARC-04 | With "Show archived" on, the archived goal shows "Archived: Replaced by a new goal" |
| ARC-05 | With no Select button, ticking one goal and two tasks under another goal and choosing Archive selected archives all three; ticking a step and a task under it archives the step once, and the task through the cascade |
| ARC-06 | Ticking the dream and a task and choosing Archive selected archives the dream once, and the task with it |
| ARC-07 | A reason over 1,000 characters is refused; another user's item answers 404 |
| ARC-08 | Frontend and backend builds, tests, the coverage check and the SonarQube gate pass |

## Open questions

| ID | Question | Proposed answer |
| --- | --- | --- |
| Q-29-1 | *(Moot after D-29-9 unless the reason returns.)* Should the reason appear in the Excel export (an "Archive Reason" column on Dreams, Goals, Steps and Tasks)? | Yes, in a follow-up, once the reason has been used for a while |
| Q-29-2 | Extend the reason to vision areas, partners, obstacles, messages and reviews? | Not now (D-29-2). Revisit after UAT |
| Q-29-3 | *(Moot after D-29-9.)* Offer quick reasons (Done another way, No longer needed, Replaced, Mistake) as one-tap choices beside the text box? | Not now; review the free-text reasons after UAT to see which come up most |
| Q-29-4 | Should select mode also offer other bulk actions (change status, move) later? | Not in V29; archive only |

## Sign-off

| Role | Name | Date | Signature |
| --- | --- | --- | --- |
| Sponsor | | | |
| Product owner | | | |
| Design reviewer | | | |
| Khmer reviewer | | | |
