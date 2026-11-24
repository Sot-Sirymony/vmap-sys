# BRD – Archive reason and archiving several items on the Vision Map

Nov 24, 2026

## Document control

This is draft 29.0.0. It keeps every requirement in BRD 28.0.1 and the earlier BRDs. It adds an **archive reason**: a
text box in every archive confirmation for dreams, goals, steps and tasks, saved with the archived item and shown when
archived items are visible. It also adds a **select mode** on the Vision Map tree, so several goals, steps and tasks
can be archived at once with one reason.

| Item | Value |
| --- | --- |
| Document | Business Requirements Document (BRD) – Archive reason addendum |
| Version | 29.0.0 – Draft |
| Parent documents | CLAUDE.md business rules (BR-12 archive instead of delete, BR-13 and BR-14 user scoping), the archive cascade and impact counts (archiving a parent archives everything under it; restoring a child brings its parents back as Paused), the list-page "Archive selected" action, BRD 16 (Vision Map tree and timeline), BRD 28 (blocker analysis) |
| Business owner | To be confirmed |
| Product owner | To be confirmed |
| Approvers | Sponsor, Product owner, Design reviewer, Khmer reviewer |

| Version | Change | Author |
| --- | --- | --- |
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
| D-29-1 | The reason is **optional** (product owner, 24 Nov 2026). Leaving it empty archives exactly as today. Up to 1,000 characters; surrounding spaces are trimmed and an empty text is stored as no reason |
| D-29-2 | Scope: **dreams, goals, steps and tasks** (the plan hierarchy). Vision areas, partners, obstacles, messages and reviews keep today's confirmation; a later BRD may extend the reason to them (Q-29-2) |
| D-29-3 | The reason is saved on the archived item and on every descendant **newly** archived with it by the cascade. A descendant that was already archived keeps its own earlier reason |
| D-29-4 | **Restoring** an item clears its archive reason, and so does a parent brought back by restoring a child. A later archive asks again |
| D-29-5 | Select mode is on the **Vision Map tree** only (not the timeline), for goals, steps and tasks of the dream shown (the dream itself is archived from its own menu). Archived rows cannot be selected. When a goal or step and something under it are both selected, only the top item is archived; the cascade archives the rest with the same reason |
| D-29-6 | One reason applies to everything archived in one confirmation, in select mode and with "Archive selected" on the list pages |
| D-29-7 | The existing archive endpoints keep working unchanged (no reason). The reason travels on a new `POST /{id}/archive` endpoint, so no client breaks |
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

### 2.1 The archive confirmation

Every archive confirmation for a dream, goal, step or task keeps its current message (including the impact count) and
adds:

- a text box labelled **"Why are you archiving this? (optional)"**, at most 1,000 characters;
- the hint "Saved with the archived item. Restoring clears it."

The confirm button stays enabled whether or not a reason is written (D-29-1).

### 2.2 Select mode on the Vision Map tree

- A **Select** switch in the tree's toolbar. When on, each goal, step and task row that is not archived shows a tick
  box, and a bar shows "{n} selected", **Archive selected (n)** and **Clear**.
- Turning Select off, or changing the dream shown, clears the selection.
- **Archive selected** opens one confirmation: "Archive {n} item(s)? Everything under them is archived too. Everything
  can be restored later with "Show archived"." plus the reason box (§2.1).
- On confirm, VMS archives the selected top items in order (goals, then steps, then tasks), skipping any item under
  another selected item (D-29-5), then reloads the map and leaves select mode.
- If one archive fails, the others already archived stay archived; the bar shows how many were archived and that the
  rest failed.

### 2.3 List pages

"Archive selected" and the row menu's Archive on the **Dreams, Goals, Steps and Tasks** pages add the same reason box
to their confirmation. With "Archive selected", the one reason is saved on every item archived (D-29-6). On the Steps
page, a step that created a partner still asks "Archive that partner too?" after the confirmation, and the reason
carries through either answer.

### 2.4 Showing the reason

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
| AR-FR-01 | Optional archive reason box in the archive confirmation for dreams, goals, steps and tasks on the Vision Map tree | Must |
| AR-FR-02 | The same on the Vision Map timeline | Must |
| AR-FR-03 | The same in "Archive selected" and in the row menus on the Dreams, Goals, Steps and Tasks pages; on Steps the reason carries into the "archive the partner too?" question | Must |
| AR-FR-04 | `POST /api/{dreams,goals,steps,tasks}/{id}/archive` with `{ reason }` (steps also `removePartner`); the old `DELETE` keeps working | Must |
| AR-FR-05 | Save the reason on the item and newly archived descendants; restore clears it (D-29-3, D-29-4) | Must |
| AR-FR-06 | `archiveReason` on the four plan item responses | Must |
| AR-FR-07 | Select mode on the Vision Map tree with tick boxes, selection bar and Archive selected with one reason (§2.2) | Must |
| AR-FR-08 | Skip selected items that sit under another selected item (D-29-5) | Must |
| AR-FR-09 | Show "Archived: {reason}" on archived rows of the Vision Map tree | Should |

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
| ARC-01 | Archiving a goal on the Vision Map with the reason "Replaced by a new goal" saves the reason on the goal and on its steps and tasks that were not archived before; a task archived earlier keeps its own reason |
| ARC-02 | Archiving with the reason box left empty works exactly as before and stores no reason |
| ARC-03 | Restoring the goal clears its reason; restoring a task under an archived goal clears the reasons of the task and of the parents brought back |
| ARC-04 | With "Show archived" on, the archived goal shows "Archived: Replaced by a new goal" |
| ARC-05 | In select mode, ticking one goal and two tasks under another goal and choosing Archive selected with one reason archives all three with that reason; ticking a step and a task under it archives the step once, and the task through the cascade |
| ARC-06 | "Archive selected" on the Tasks page with a reason saves that reason on every selected task |
| ARC-07 | A reason over 1,000 characters is refused; another user's item answers 404 |
| ARC-08 | Frontend and backend builds, tests, the coverage check and the SonarQube gate pass |

## Open questions

| ID | Question | Proposed answer |
| --- | --- | --- |
| Q-29-1 | Should the reason appear in the Excel export (an "Archive Reason" column on Dreams, Goals, Steps and Tasks)? | Yes, in a follow-up, once the reason has been used for a while |
| Q-29-2 | Extend the reason to vision areas, partners, obstacles, messages and reviews? | Not now (D-29-2). Revisit after UAT |
| Q-29-3 | Offer quick reasons (Done another way, No longer needed, Replaced, Mistake) as one-tap choices beside the text box? | Not now; review the free-text reasons after UAT to see which come up most |
| Q-29-4 | Should select mode also offer other bulk actions (change status, move) later? | Not in V29; archive only |

## Sign-off

| Role | Name | Date | Signature |
| --- | --- | --- | --- |
| Sponsor | | | |
| Product owner | | | |
| Design reviewer | | | |
| Khmer reviewer | | | |
