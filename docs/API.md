# API Reference

Base URL:

```text
http://localhost:8080/api
```

Interactive documentation: <http://localhost:8080/swagger-ui.html> (OpenAPI description at
`/v3/api-docs`). It is generated from the controllers, so it is always current. It is on in the `local`
and `test` profiles and off in production unless `SWAGGER_ENABLED=true`. In Swagger UI, sign in with
`POST /api/auth/login`, then use **Authorize** and paste the `token`.

Protected endpoints require:

```text
Authorization: Bearer <token>
```

Auth failures are distinguished:

| Status | Meaning |
|---|---|
| **401** Unauthorized | No token, or a malformed/expired/tampered one — authenticate and retry |
| **403** Forbidden | Authenticated, but lacking the role for this action (e.g. admin-only issue triage) |

Both return the standard error body (`timestamp`, `status`, `error`, `message`,
`path`).

## Public

### Health

```text
GET /health
```

Response:

```json
{
  "status": "UP",
  "service": "Vision Mapping Management System Backend"
}
```

### Register

```text
POST /auth/register
```

Body:

```json
{
  "fullName": "Demo User",
  "email": "demo@example.com",
  "password": "Password123"
}
```

### Forgot password

```text
POST /auth/forgot-password
```

Public. Body: `{ "email": "pat@example.com" }`

Always answers `204`, whether or not the address has an account. Answering
differently would make this a membership oracle — submit addresses, learn which
are registered — and that list is exactly what is worth having before attacking
passwords. The UI wording must not claim more than the API does.

Issuing a link retires any the same user still has outstanding, so asking twice
never leaves two working links alive in two inboxes.

**Rate limited.** Three requests per address per 15 minutes, and twenty per
client IP per hour, both checked *before* the account lookup so neither can
reveal which addresses are registered. Exceeding either answers `429` with a
message. The per-address limit stops the endpoint being used to flood one
inbox; the per-IP limit stops one caller working through a list of addresses.

Limits are held in memory, so they are per instance rather than per cluster —
correct for the current single-instance deployment, but behind multiple replicas
the effective limit multiplies and this should move to Redis.

**Sending is asynchronous.** The SMTP round trip runs on a separate executor so
it cannot make the response measurably slower for an address that has an account
than for one that does not.

### Reset password

```text
POST /auth/reset-password
```

Public, and authorised by the token rather than a session: someone who has
forgotten their password has no other credential. Body:

```json
{
  "token": "<from the emailed link>",
  "newPassword": "BrandNewPassword456"
}
```

Answers `204`. There is no email field — the token identifies the account by
itself, and accepting an address alongside it would let one be paired with the
other's token.

Errors, all `400`:

| Case | Message |
|---|---|
| Unknown token | `This reset link is not valid. Request a new one.` |
| Already redeemed | `This reset link has already been used. Request a new one.` |
| Past expiry | `This reset link has expired. Request a new one.` |
| New password under 8 chars | validation error |

Unlike the request endpoint, this one *does* distinguish its failures: the user
is holding a link they believe works, and "expired" versus "already used" is the
difference between requesting a new one and realising the reset already
happened. The token is unguessable, so saying which tells an attacker nothing.

**Storage.** The emailed token is never stored — only its SHA-256 — so read
access to `password_reset_tokens` does not let anyone reset an account. A token
is single-use and expires after `PASSWORD_RESET_TTL_MINUTES` (default 30).

**Delivery.** Requires `MAIL_HOST` (plus `MAIL_USERNAME` / `MAIL_PASSWORD`).
Without it the backend still issues valid tokens but writes the link to the log
at WARN instead of sending it, so the flow can be exercised locally.

### Change password

```text
POST /auth/change-password
```

Protected — requires a valid token. The current password is required even so: a
token proves a session was opened at some point, not that the person holding it
now is the account owner.

Body:

```json
{
  "currentPassword": "Password123",
  "newPassword": "BrandNewPassword456"
}
```

Answers `204 No Content`. There is no user identifier in the body — the password
changed is always that of the token's owner, so one account cannot re-password
another.

Errors, all `400` with the message in `message`:

| Case | Message |
|---|---|
| Current password wrong | `Current password is incorrect.` |
| New password same as old | `New password must be different from the current password.` |
| New password under 8 chars | validation error |

A wrong current password is deliberately **not** a `401`: that status means "sign
in again", and the frontend acts on it by ending the session. Mistyping the old
password is an ordinary form error, not an expired session.

Since V10, a password change ends every other session of the account at once;
the session that made the change stays signed in. A password reset ends all of
them.

Since V20 (SV-FR-01) it also needs "Verify it's you": send the verified
challenge id in the `X-Step-Up` header, or rely on the session's 10-minute
verified window. Without either it answers `403` with `"code": "STEP_UP_REQUIRED"`,
**before** the current password is checked, so the endpoint cannot be used to
test passwords. A password change closes every verified window.

### Login

```text
POST /auth/login
```

Body:

```json
{
  "email": "demo@example.com",
  "password": "Password123",
  "rememberMe": true
}
```

`rememberMe` (optional, default `true`) keeps the session for 30 days, ending it
earlier if it goes unused for 14 days (`SESSION_IDLE_DAYS`); `false`
keeps it for 12 hours and the client stores it only for the browser tab. After 5
wrong passwords in 15 minutes the email is locked for 15 minutes (429 with
`Retry-After`).

Auth response:

```json
{
  "token": "...",
  "tokenType": "Bearer",
  "userId": 1,
  "fullName": "Demo User",
  "email": "demo@example.com",
  "role": "USER",
  "appearance": {
    "themePreset": "FLUENT_SYSTEM",
    "themeMode": "SYSTEM",
    "themeAccent": "BLUE",
    "uiDensity": "COMFORTABLE",
    "fontSize": "MEDIUM",
    "fontFamily": "SYSTEM",
    "backgroundTone": "NEUTRAL",
    "interfaceStyle": "MODERN",
    "interfaceStyleSource": "DEFAULT",
    "highContrast": false,
    "reduceMotion": false
  },
  "refreshToken": "...",
  "expiresIn": 1800
}
```

`token` is a short-lived access token (30 minutes; `expiresIn` is in seconds).
Send it as `Authorization: Bearer <token>`. Keep it in memory only.

### Sessions (V10)

```text
POST /auth/refresh   { "refreshToken": "..." }
POST /auth/logout    { "refreshToken": "..." }
```

Both are public. `refresh` returns a new auth response with a new access token
**and a new refresh token**; the one sent stops working. Answers:

| Status | Meaning |
| --- | --- |
| 200 | New tokens. Store the new refresh token in place of the old one. |
| 409 | The token was replaced a moment ago (two tabs refreshed together). Read the newer token and retry. |
| 401 | The session has ended (logged out, password changed or reset, expired, or a replaced token was used again later, which ends the session as stolen). Sign in again. |

`logout` ends the session on the server and always answers 204. Every request
checks that the access token's session is still open, so ending a session stops
its access tokens at once.

`appearance` (FR-39.6) is included on both register and login so the client can
apply the user's saved theme on first paint, instead of rendering the default and
swapping it once a follow-up request returns.

### Two-step sign-in (V21)

| Method and path | Access | Answer |
| --- | --- | --- |
| `POST /auth/login`, `POST /auth/oauth/google/exchange`, `POST /auth/oauth/google/link` | Public | The session as before, or `{ "status": "TWO_FACTOR_REQUIRED", "challengeToken", "method", "sentTo"?, "emailUnavailable", "expiresAt" }`. The body may carry `deviceToken` |
| `POST /auth/two-factor/verify` `{ "challengeToken", "code"?, "recoveryCode"?, "rememberDevice" }` | Public | The session (plus `deviceToken` when remembered); 400 `TWO_FACTOR_WRONG_CODE` with `triesLeft`; 410 `TWO_FACTOR_ENDED` |
| `POST /auth/two-factor/resend` `{ "challengeToken" }` | Public | A new email code; 429 within a minute |
| `GET /account/two-factor` | Signed in | Status, method, recovery codes left, set-up-by date, `emailMethodAvailable`, `canTurnOff`, trusted devices |
| `POST /account/two-factor/totp` → `POST /account/two-factor/totp/confirm` `{ "code" }` | Signed in, `X-Step-Up` for the first | The key and `otpauth://` address; then the ten recovery codes, once |
| `POST /account/two-factor/email` | Signed in, `X-Step-Up`; participants | The ten recovery codes |
| `POST /account/two-factor/recovery-codes`, `POST /account/two-factor/disable` | Signed in, `X-Step-Up` | New codes; 204 |
| `POST /account/two-factor/skip` | Admins and coaches before their date | 204 (logged) |
| `DELETE /account/trusted-devices/{id}`, `DELETE /account/trusted-devices` | Signed in | 204; another person's device 404 |
| `GET /admin/two-factor` | Admin | Admins, coaches and anyone with it on |
| `POST /admin/users/{id}/two-factor/reset` `{ "reason" }` | Admin, `X-Step-Up` (`ADMIN_TWO_FACTOR_RESET`) | 204; never the admin's own account |

An admin or coach past their set-up-by date gets 403 `TWO_FACTOR_SETUP_REQUIRED` on every call except the two-step
sign-in, "Verify it's you", `/auth/*` and the few calls that draw the page. "Verify it's you" also accepts a current
authenticator code instead of the emailed one.

### Sign in with Google (V14)

All public except `sign-in-methods`. Off until `GOOGLE_CLIENT_ID` and
`GOOGLE_CLIENT_SECRET` are set and "Verify it's you" codes can be emailed (D-14-5). Every step is limited per network (60 per 15
minutes by default; 429 with `Retry-After`).

| Method and path | Answer |
| --- | --- |
| `GET /auth/oauth/google/status` | `{ "enabled": true }` or `false`; the frontend hides the Google button when false |
| `GET /auth/oauth/google/authorize-url?remember=true&from=/tasks` | `{ "url": "https://accounts.google.com/…" }`; 503 "Google sign-in is not set up yet…" when off. `from` must be an in-app path, otherwise `/` is used |
| `GET /auth/oauth/google/callback?code&state` (or `?error=`) | Called by Google. Always a 302 to `{FRONTEND_URL}/oauth/google/callback#…`, with `Referrer-Policy: no-referrer` and `Cache-Control: no-store` |
| `POST /auth/oauth/google/exchange` `{ "code" }` | The same answer as `POST /auth/login`; 400 "This sign-in link has expired. Please try again." |
| `POST /auth/oauth/google/link` `{ "linkToken", "password" }` | The same answer as login; 400 "That password is not correct." (counts as a failed sign-in; the link code stays usable) or "This link has expired. Please sign in with Google again."; 429 when the email is locked |
| `GET /account/sign-in-methods` (signed in) | `{ "passwordSet": false, "linkedAccounts": [{ "provider": "GOOGLE", "email": "…", "linkedAt": "…" }] }` |

The callback fragment is one of:

```text
#result=signed-in&code=<60-second code>&remember=true&from=%2Ftasks
#result=link&code=<10-minute code>&remember=true&from=%2F
#result=error&reason=cancelled|expired|verification|google|unavailable|too-many
```

A fragment is never sent to a server, and no access or refresh token is ever in
a URL. Each code works once and only its SHA-256 is stored.

Accounts created with Google have no password anyone knows (`passwordSet:
false`). For them, `POST /auth/change-password` takes only `newPassword` (with
the "Verify it's you" code) and sets their first password, and `POST
/account/delete` takes `{}` (with the code). The account export has a
`linkedAccounts` section (provider, email, link date; never Google's user ID).

## Standard CRUD Pattern

Most resources support:

```text
GET    /{resource}
POST   /{resource}
GET    /{resource}/{id}
PUT    /{resource}/{id}
PATCH  /{resource}/{id}/status
DELETE /{resource}/{id}
```

Delete usually archives, pauses, closes, or accepts instead of permanent deletion.

## Resources

### Vision Areas

```text
/vision-areas
```

Create body:

```json
{
  "name": "Career Development",
  "description": "Professional growth area",
  "priority": "HIGH",
  "status": "ACTIVE"
}
```

### Dreams

```text
/dreams
```

Create body:

```json
{
  "visionAreaId": 1,
  "title": "Become a strong AI-assisted public health researcher",
  "description": "Build skill and confidence",
  "whyImportant": "Supports practical health research impact",
  "successDefinition": "A concept note is reviewed by a mentor",
  "dreamType": "LONG_TERM",
  "priority": "HIGH",
  "targetDate": "2026-12-31",
  "status": "ACTIVE"
}
```

### Goals

```text
/goals
```

### Steps

```text
/steps
```

Use Java/API entity name `VisionStep` in backend code, but API route is `/steps`.

### Tasks

```text
/tasks
```

Blocked task rule:

```json
{
  "status": "BLOCKED",
  "blockerReason": "Need mentor feedback"
}
```

If `status` is `BLOCKED`, `blockerReason` is required.

Task order. There are two separate orders:

| Method | Path | Body | Purpose |
|---|---|---|---|
| PATCH | `/api/tasks/reorder` | `{ "parentId": stepId, "orderedIds": [...] }` | Order of tasks inside one step (Vision Map). Must list exactly that step's current tasks. Sets `sortOrder` |
| PATCH | `/api/tasks/list-order` | `{ "orderedIds": [...] }` | The user's own order across all tasks (Tasks page: "this first, that second"). Lists tasks front to back; current tasks left out keep their relative place after the listed ones. Another user's task, an archived task or a repeated id answers 400. Sets `listOrder`. 204 on success |

Task responses carry both `sortOrder` and `listOrder` (0-based, or null before the first reorder; null sorts last).

### Partners

```text
/partners
```

Supports linking to Vision Area, Dream, Goal, Step, or Task.

### Communication Messages

```text
/communication-messages
```

Supports draft, sent, followed up, replied, and closed statuses.

### Reviews

```text
/reviews
```

Review types:
- DAILY
- WEEKLY
- MONTHLY
- QUARTERLY

### Obstacles

```text
/obstacles
```

V17 placement (BRD 17.0.0):
- `POST` and `PUT` need `relatedStepId` or `relatedTaskId`; otherwise 400
  "Put this blocker on a step or a task." A task from another step answers 400
  "That task is not part of the chosen step." Another user's step or task
  answers 404. Exception: a `PARTNER` obstacle with any conflict, expectation,
  checklist or criticism answer may have neither (it may keep `relatedDreamId`).
- With a step or task, `relatedGoalId` and `relatedDreamId` are worked out by
  the server; values sent by the client are ignored.
- An obstacle saved before V17 with only a dream or goal can still be updated
  without a step as long as its dream and goal are sent back unchanged.
- Responses add `placement` (`TASK`, `STEP`, `NONE`), `placementExempt` and
  `anchorArchived`.
- `GET /obstacles?placement=UNPLACED`: Open and In Progress obstacles that still
  need a step (not placed and not exempt, or on an archived step or task).
- `GET /dashboard`: `attention.unplacedObstacleCount`.
- Excel: the Obstacles sheet ends with a **Placed on** column (`Task`, `Step`,
  `Not placed`). Import now reads the Obstacles sheet: rows with a Task or Step
  ID are placed there; rows with only a Dream or Goal ID are imported unplaced
  and reported in `warnings`; Resolved and Accepted rows are skipped with a
  reason (the sheet holds no root cause or alternatives).

V17 phase 3, gaps become steps (BRD 17.0.0, BL-FR-18 to BL-FR-22):
- Steps: `POST`/`PUT /steps` accept `gapKind` (an obstacle type name; left out
  keeps it, `""` clears it). Step responses add `gapKind`, `coversStepId`,
  `coversStepCode`, `coversObstacleId` and `createdPartnerId`.
- `POST /steps/{id}/gap-step` (201): body `{ title, description?, owner,
  priority, targetDate, gapKind?, createPartner, partnerName?,
  partnerSupportType? }`. Needs gap text on the step (else 400 "Write what is
  missing on this step first."). The new step goes just before the step in the
  same goal; later steps move down one. Returns `{ step, partner, obstacle }`
  (`obstacle` null). Without `partnerSupportType` the server suggests one from
  the gap kind; without `partnerName` it uses "To find: {step title}".
- `POST /obstacles/{id}/convert-to-step` (201): the same body plus `goalId`,
  needed only when the obstacle is not on a live step (else 400 "Choose the goal
  this step belongs to."). On a step, the new step goes just before it. The
  obstacle is anchored to the new step and set to In Progress. Partner conflicts
  (exempt) and Resolved or Accepted obstacles answer 400.
- `DELETE /steps/{id}?removePartner=true` also archives the partner that step
  created (BL-FR-21).
- Plan check (`/ai/plan/dreams/{id}/check` and the plan assistant's Resources
  section): new rule kind `GAP_NEEDS_HELP` for a step whose gap kind is
  Knowledge, Skill or Money with no linked partner, no "find help" step made for
  it and no open obstacle on it: "Step S-014 is missing a skill. Who could help?"

V17 phase 4, break it down (BRD 17.0.0, BL-FR-25 to BL-FR-29):
- `POST /tasks/{id}/split` (201): body `{ rows: [{ title, dueDate, owner?,
  priority? }] (2 to 5), originalOutcome: KEEP | REPLACE }`. New tasks are Not
  Started, under the same step, placed right after the original in the step's
  order (and on the Tasks page order, once the participant has one). Owner and
  priority default to the original's. `REPLACE` archives the original, adds
  "Split into T-041 to T-043" to its description and to its progress log.
  Returns `{ created, original }`. Nothing is deleted.
- `PATCH /tasks/{id}/status` now takes `{ status, manualOverride, changeNote? }`.
  `changeNote` (max 1000) is saved as the progress-log note when the task
  leaves Blocked, and ignored otherwise. `PUT /tasks/{id}` accepts the same
  `changeNote`.
- Task responses add `blockedCount`: each move into Blocked counts once
  (staying Blocked does not).
- `POST /ai/plan/tasks/{taskId}/smaller-tasks?language=en`: 2 to 5 smaller
  tasks for one task, from its title, step and blocker reason, never asking a
  question. Suggestions only (same shape as `task-suggestions`); same consent,
  daily limit and monthly cap as other AI calls. Another user's task answers
  404 before AI is checked.

V18 phase 1, the Management Report (BRD 18.0.0, MR-FR-03 to MR-FR-13):
- `GET /reports/management?areaId=` or `?dreamId=` (neither = all dreams; both
  = 400 "Choose either a vision area or a dream, not both."; another user's id =
  404). Read-only, no AI. Returns `scope` (`kind` ALL, AREA or DREAM, `label`),
  `asOf`, `headline` (`overallProgress`, `goalsByHealth` RED/AMBER/GREEN/DONE,
  `totalTasks`, `completedTasks`, `overdueTasks`, `blockedTasks`,
  `openBlockers`, `dueSoonTasks`), `tasksByStatus` (all six statuses),
  `openTasksByPriority` (`priority`, `onTrack`, `overdue`), `blockersByKind`,
  `unplacedObstacles`, `goals` (health rows, most urgent first),
  `attention` (up to 10: `kind`, `itemType`, `itemId`, `title`, `context`,
  `stepId`, `goalId`) and (phase 4) `trend` (`weekEnd`, `progress`: average
  task progress per week for the last 12 weeks, from the progress log of the
  tasks in scope; empty without history).
- Definitions follow BRD 18.0.0 "Definitions": only unarchived goals, steps
  and tasks; blockers are Open or In Progress obstacles on a step or task in
  scope.
- `GET /reports/management.xlsx?areaId=|dreamId=&language=en|km` (V18 phase 2):
  the Excel report, same scope rules and 404s. `language` other than `en` or
  `km` answers 400. Sheets Summary, Report and Blockers (MR-FR-16 to MR-FR-21).
  Over 10,000 rows answers 400 asking to narrow the filter. Limited per account
  (429 with Retry-After): `app.security.rate-limit.report-export-per-user`
  (30) per `report-export-window-minutes` (10). File name
  `Management_Report_{All_dreams|Area_{id}|Dream_{id}}_{yyyy-MM-dd}.xlsx`.
- `GET /reports/management.pptx?areaId=|dreamId=&language=en|km&title=&includeName=true|false`
  (V18 phase 3): the deck, same scope rules, 404s and download limit. `title`
  is at most 80 characters (400 otherwise; default "Progress report");
  `includeName=false` leaves the participant's name off the title slide. 8
  slides, or 7 when nothing needs a decision or support (MR-FR-24, MR-FR-26).

V19 phase 4, PDF, share links and download history (BRD 19.0.0, RM-FR-25, RM-FR-42, RM-FR-46 to RM-FR-52):
- `GET /reports/management.pdf?…same query…&language=&sections=` and
  `GET /reports/saved/{id}.pdf?language=&sections=`: an A4 landscape PDF
  (`application/pdf`) with the chosen sections, page numbers and the status
  in every footer ("Final · frozen …", "DRAFT · …" or "Live report · …").
  Download limit applies. Khmer prints with the embedded Noto Sans Khmer.
- `GET /reports/saved/{id}/exports`: the report's downloads (Excel,
  PowerPoint and PDF), newest first, at most 50: `id`, `format` (xlsx, pptx,
  pdf), `language`, `createdAt`. The live report keeps none.
- `GET /reports/saved/{id}/shares`: its share links: `id`, `sections`,
  `createdAt`, `expiresAt`, `revokedAt`, `open`, `viewCount`,
  `lastViewedAt` (`token` is always null here).
- `POST /reports/saved/{id}/shares` (201) with `{ "expiresInDays": 7|30|90,
  "sections": [...] }` (30 days, and COMMENTARY, GOAL_HEALTH and CHANGES, when
  left out): a read-only link. The answer's `token` is shown only this once;
  only its SHA-256 is stored. A draft, an archived report or one whose
  snapshot fails its check = 409; another expiry = 400; 20 links a day per
  account (429 after).
- `PATCH /reports/saved/{id}/shares/{shareId}/revoke`: stops a link at once.
  Archiving a report stops all its open links; restoring does not reopen them.
- Public, no sign-in: `GET /public/reports/{token}` (the shared view: `name`,
  `code`, `sharedBy` (first name), `language`, `finalizedAt`, `expiresAt`,
  `sections`, `report` (the frozen Summary without `attention` or `owners`;
  `goals` empty unless GOAL_HEALTH and `comparison` null unless CHANGES are
  shared), `commentary` (null unless COMMENTARY) and `rows` (null unless
  DETAILS)) and `GET /public/reports/{token}.pdf` (the PDF with the shared
  sections). Both answer `X-Robots-Tag: noindex, nofollow`,
  `Referrer-Policy: no-referrer` and `Cache-Control: no-store`; 60 requests a
  minute per link. An unknown, expired or stopped token, an archived report
  or one that fails its check all answer the same 404 "This report is no
  longer shared.". Only the view counts a visit.
- The security log records `SHARE_LINK_CREATED` and `SHARE_LINK_REVOKED`
  (target `report_share`, with the participant), and `SHARE_LINK_OPENED` for
  every view or refused token with no actor and no network address.

V19 phase 3, comparison, What changed and file options (BRD 19.0.0, RM-FR-32 to RM-FR-44):
- `GET /reports/management?…&compareTo={reportId}`: the live Summary compared
  with one of the participant's FINAL saved reports on the same scope.
  A draft or another scope = 400 "Choose a final report on the same scope to
  compare with."; another user's report = 404.
- The Summary adds `comparison` (null when not compared): `reportId`, `code`,
  `name`, `asOf` (the compared report's frozen date); `headline` (each figure
  now minus then); `goals` (`goalId`, `isNew`, `healthBefore`,
  `progressChange` in points, `targetBefore` only when the date changed,
  `previousTitle` only when renamed); `removed` (`goalId`, `title`, `reason`:
  COMPLETED_ARCHIVED, ARCHIVED, MOVED or null); `changes` (most important
  first: GOAL_WORSE, DUE_LATER, NEW_BLOCKER, BLOCKER_CLOSED, GOAL_BETTER,
  TASK_COMPLETED; with `itemType`, `itemId`, `title`, `context`, `before`,
  `after` and `renamed`; at most 500 kept) and `totalChanges`. Items are
  matched by id.
- A draft is compared with its `compareToReportId` (set by `POST` or `PUT
  /reports/saved/{id}`, which must name a FINAL report of the participant's on
  the same scope, else 400), or, when that is null or no longer fits, the
  latest FINAL report on its scope. **Finalise** freezes the comparison in the
  snapshot and sets `compareToReportId`; a final report answers 409 to a new
  one. `POST /reports/saved/{id}/commentary-draft` adds "moved to On track"
  lines to Highlights when compared.
- `PUT /reports/saved/{id}/sections` with `{ "sections": [...] }` (COMMENTARY,
  SUMMARY, GOAL_HEALTH, DETAILS, BLOCKERS, CHANGES, NEXT_ACTIONS, SUPPORT;
  null or empty = all): the sections the report's files include, remembered
  for the next download. Allowed on a final report. Saved reports add
  `sections`.
- All `.xlsx` and `.pptx` downloads (live and saved) take
  `sections=A,B` (a saved report defaults to its remembered sections) and
  `language=en|km` (a saved report defaults to its own language). Excel: a
  **Commentary** sheet first when there is commentary, a **Changes** sheet
  when compared, and a "Change since R-…" column on the Summary headline and a
  Change column on the goal table. Deck: a **Highlights** slide second, a
  **What changed** slide after the executive summary, the participant's own
  text first on the support slide and goal notes in the progress slide's
  notes. Files from a draft say "DRAFT" in the Excel title row and on the
  title slide. The Report sheet's columns never change.

V19 phase 2, filters, Details rows and saved reports (BRD 19.0.0, RM-FR-10 to RM-FR-31):
- The live report endpoints also take `priority` (comma-separated: LOW,
  MEDIUM, HIGH, CRITICAL), `owner`, `health` (comma-separated health values)
  and `includeCompleted` (default true). The Summary adds `filters` and
  `owners` (task owners in scope before filtering). Goal health is judged
  before filtering and never changes with it.
- `GET /reports/management/rows?…same query…&language=en|km`: the Details
  tab, `{ rows: [...] }`, the same rows as the Excel Report sheet with
  `goalId`, `stepId`, `taskId`, `issues`, `completed`, `blocked`, `overdue`,
  `dueSoon`. Over 10,000 rows answers 400.
- `GET /reports/saved?status=DRAFT|FINAL&includeArchived=&q=`: saved reports,
  newest change first, without the Summary.
- `POST /reports/saved` (201): `name` (blank = "{scope} – {period}"),
  `areaId` or `dreamId`, `period`, `from`, `to`, `priorities`, `owner`,
  `healths`, `includeCompleted`, `language`. Another user's area or dream =
  404; at 200 reports = 400.
- `GET /reports/saved/{id}`: the report, its commentary and `report` (the
  Summary: live for a DRAFT, frozen for a FINAL). `verified` is null for a
  draft and false when a final report's snapshot fails its checksum.
- `PUT /reports/saved/{id}`: a draft's settings; a final report's name only
  (anything else = 409). `PUT /reports/saved/{id}/commentary`: `highlights`,
  `risks`, `decisionsNeeded`, `nextFocus` (each up to 2,000 characters),
  `nothingNeeded`, `goalNotes` ({goalId: text up to 500}); 409 when final.
- `POST /reports/saved/{id}/commentary-draft?language=&mode=RULES|AI`: text
  for the sections still empty (null otherwise); nothing is saved. `RULES`
  (default) uses fixed rules (RM-FR-28). `AI` (V19 phase 5, RM-FR-29) asks
  Claude, reading only the report's fact sheet (period; each goal's title,
  progress, health and reasons; the rule-based draft), under the V15 rules:
  400 without AI consent, 429 at the daily limit, 503 when AI is off or the
  budget is reached. Lines are cut to "- " bullets, at most eight and 2,000
  characters a section. Logged as `REPORT_DRAFT`. Another user's report
  answers 404 and a final report 409, both before any AI check; any other
  `mode` is 400.
- `GET`, `PUT`, `DELETE /reports/saved/{id}/coach-share` (V19 phase 5,
  RM-FR-50, D-19-3): `{ "available", "coachName", "shared", "sections",
  "sharedAt" }`. `available` is false outside an active cohort with a coach.
  `PUT { "sections": [...] }` shares the report with the coach of the
  participant's active cohort, or changes the sections (default
  `COMMENTARY`, `GOAL_HEALTH`, `CHANGES`): 400 without a coach, 409 for a
  draft, an archived report or one that fails its check. `DELETE` stops
  sharing at once. Archiving the report, leaving the cohort or joining
  another also stops it.
- `GET /coach/participants/{id}/reports` (coach or admin): the final reports
  this participant shared with the current coach, newest first: `{ reportId,
  code, name, scopeLabel, periodKind, periodFrom, periodTo, finalizedAt,
  sharedAt }`. `GET /coach/participants/{id}/reports/{reportId}`: the same
  read-only view as `GET /api/public/reports/{token}`, with `expiresAt` null.
  Both follow the V9 D-6 check; a participant outside the coach's active
  cohorts, or a report not shared with this coach, archived or unverified,
  answers 404. Logged as a coach view of the participant.
- `GET /reports/saved/reminder` (V19 phase 5, RM-FR-49): `{ "due",
  "cadence": "NONE|WEEKLY|MONTHLY", "dueOn", "latestFinalId",
  "latestFinalName" }`. `dueOn` is the latest reminder day on or before today
  in the reminder time zone; `due` is true from that day for 7 days, until a
  report is finalised on or after it. `latestFinal*` is the newest unarchived
  final report (for **New report from this**), or null.
- `POST /reports/saved/{id}/finalize?language=`: freezes the snapshot; 409
  when already final. `POST /reports/saved/{id}/copy` (201): a new draft for
  the next period with the commentary and `compareToReportId`.
- `PATCH /reports/saved/{id}/archive` and `/restore`. `DELETE
  /reports/saved/{id}` (204): drafts only; a final report = 409.
- `GET /reports/saved/{id}/rows`, `GET /reports/saved/{id}.xlsx` and
  `.pptx` (same options as the live files, download limit applies): from the
  snapshot once final. File name `{Report name}_{yyyy-MM-dd}`, letters,
  digits, dash and underscore only.
- Another user's report id answers 404 on every endpoint
  (`AccessControlSweepTests`).

V19 phase 1, the report's period and health reasons (BRD 19.0.0, RM-FR-01 to RM-FR-09):
- All three report endpoints also take `period` (`THIS_WEEK`, `LAST_WEEK`,
  `THIS_MONTH`, `LAST_MONTH`, `THIS_QUARTER`, `LAST_QUARTER` or `CUSTOM`;
  default `THIS_MONTH`) and, for a custom period, `from` and `to`
  (yyyy-MM-dd, inclusive). Dates without `period` mean `CUSTOM`. A custom
  period without both dates, starting after its end, ending after today or
  longer than 366 days answers 400 with the reason; an unknown `period`
  answers 400 "Unknown report period.".
- The Summary adds `period` (`kind`, `from`, `to`, `statusAsOfToday`: the
  period ended before today, so status figures are today's;
  `closedBeforeRecording`: the period starts before closing dates were
  recorded) and `periodFigures` (`tasksCompleted`, `progressGained` in points
  of average task progress, `blockersOpened`, `blockersClosed`,
  `tasksBecameOverdue`), counted on the tasks and placed blockers in scope.
- `goalsByHealth` and a goal's `health` may be `DONE_OPEN_WORK` (Completed, but
  tasks are still open). Each goal row adds `reasons` (`code`, `value`,
  `elapsed`, `title`): COMPLETED, COMPLETED_OPEN_WORK (value = open tasks),
  GOAL_OVERDUE (value = days), BLOCKED_TASKS, CRITICAL_BLOCKER (value = how
  many, title = the first), OVERDUE_WORK, OPEN_BLOCKERS, BEHIND_SCHEDULE
  (value = progress %, elapsed = time gone %) or ON_TRACK.
- `attention` may hold `REOPEN_OR_FINISH` items with `itemType` GOAL and a
  null `stepId`.
- The files' as-of line names the period ("All dreams · This month · as of
  17 Nov 2026"). The Excel Summary sheet adds a "This period" table and a
  **Why** column on the goal health table; the deck's progress slide shows each
  goal's reasons.
- Obstacles now record `resolved_at` when moved to Resolved or Accepted
  (cleared when reopened; migration V79 back-fills closed rows with their last
  update). It is not in the obstacle API response.

V17 phase 5, the six chains (BRD 17.0.0, BL-FR-30 to BL-FR-35):
- `PUT /chain-assessments` now takes `settlingScore`, `fearScore`,
  `criticismScore`, `prioritiesScore`, `persistenceScore` and `visionScore`
  (all required, 1 to 5). `knowledgeScore` and `resourcesScore` are no longer
  accepted; a request without the two new scores answers 400.
- Responses add `prioritiesScore` and `persistenceScore` (null on takes from
  before V17); `knowledgeScore` and `resourcesScore` are null on new takes.
  `averageScore` covers the chains that take measured. `improvementPercent`
  compares baseline and latest over the chains both measured.
- The account export's `assessments` rows add `priorities` and `persistence`;
  `knowledge` and `resources` may be null.
- Module helpers: "Which module next?" maps misplaced priorities to module 13
  and lack of persistence to module 12. Assessment practices for them use the
  Today page (letter rank A, one daily habit).

Obstacle types:
- KNOWLEDGE
- SKILL
- TIME
- MONEY
- MOTIVATION
- PARTNER
- SYSTEM
- DECISION
- OTHER

### Progress Logs

```text
/progress-logs
```

### Dashboard

```text
GET /dashboard
```

Returns:
- totalVisionAreas
- activeDreams
- activeGoals
- activeTasks
- completedTasks
- overdueTasks
- blockedTasks
- averageProgress
- tasksDueThisWeek
- goalsByStatus
- dreamsByVisionArea
- tasksDueInPeriod, completedTasksInPeriod (for the `from`/`to` window)
- tasksByStatus, tasksByPriority, activeObstaclesByType, partnersByStatus, reviewCadence
- progressTrend, visionAreaProgress
- priorityTasks: the five open tasks with the highest priority (earliest due date first among equals)
- nextUpTasks (V25, DA-FR-02): up to five open tasks for the dashboard's *Next up*: overdue first, then due today,
  then the rest; within each group highest priority, then earliest due date. Unlike `priorityTasks`, an overdue Low
  task comes before a Critical task due next month
- weeksWithDiligence, moonshotGoals, moonshotDreams, attention, energyBudget, gratitude

Query parameters: `visionAreaId` (optional scope), `from` and `to` (the period window, `YYYY-MM-DD`).

### Appearance Preferences

FR-39.6. Neither call takes a user id: the caller is resolved from the token, so
there is no request that could read or write another user's settings (BR-33).

```text
GET /preferences/appearance
PUT /preferences/appearance
```

`PUT` is a partial update — the Appearance UI changes one control at a time, and
any field omitted keeps its stored value. An all-null body is a no-op, not a
reset.

```json
{
  "themePreset": "MIDNIGHT",
  "themeMode": "DARK",
  "themeAccent": "PURPLE",
  "uiDensity": "COMPACT",
  "fontSize": "LARGE",
  "fontFamily": "INTER",
  "backgroundTone": "WARM",
  "interfaceStyle": "MODERN",
  "highContrast": true,
  "reduceMotion": true
}
```

Allowed values:

| Field | Values |
|---|---|
| `themePreset` | `FLUENT_SYSTEM`, `FLUENT_LIGHT`, `FLUENT_DARK`, `OCEAN`, `FOREST`, `SLATE`, `MIDNIGHT`, `CUSTOM` |
| `themeMode` | `LIGHT`, `DARK`, `SYSTEM` |
| `themeAccent` | `BLUE`, `TEAL`, `PURPLE`, `GREEN`, `ORANGE`, `MAGENTA`, `RED`, `BRASS`, `STEEL`, `PINK`, `VERMILION`, `VIOLET` |
| `uiDensity` | `COMFORTABLE`, `COMPACT` |
| `fontSize` | `SMALL`, `MEDIUM`, `LARGE` |
| `fontFamily` | `SYSTEM`, `PUBLIC_SANS`, `INTER`, `DM_SANS`, `NUNITO_SANS` |
| `backgroundTone` | `NEUTRAL`, `WARM`, `COOL`, `SOFT`, `TINTED`, `FLAT` |
| `interfaceStyle` | `MODERN` (default for new accounts since V93), `CLASSIC`, `LIQUID_GLASS` |
| `highContrast`, `reduceMotion` | `true`, `false` |

**`interfaceStyleSource`** (V26, response only; requests never send it): `DEFAULT`
until the user changes the style, then `CHOSEN`. A request that carries the style
the account already holds does not count as a choice, because the app sends the
whole appearance on every save. Accounts that held `CLASSIC` before V93 keep it
with source `DEFAULT` (Q-26-1).

A value outside these sets is rejected with **400** rather than stored, so the
database can only ever hold something the UI can render (BR-33). `CUSTOM` is
computed from a mode/accent pair that matches no preset — it is a result, not
something a client needs to select.

## Daily Execution (V8)

All dates are the user's local day as `YYYY-MM-DD`. A date more than one day past
the server's date is rejected for check-ins and habit checks.

| Method | Path | Purpose |
|---|---|---|
| GET | `/api/daily-plan?date=` | Due and overdue steps and tasks, attitude, habits, weekly review prompt |
| GET | `/api/attitude-checkins` | Last 30 check-ins |
| PUT | `/api/attitude-checkins/{date}` | Save the day's score (1 to 5) and note |
| GET, POST | `/api/habits?date=` | List habits, or add one (at most 3 active) |
| PUT | `/api/habits/{id}?date=` | Rename |
| PUT, DELETE | `/api/habits/{id}/checks/{date}` | Mark or unmark a day |
| DELETE | `/api/habits/{id}` | Archive (`POST .../restore`, `DELETE .../permanent`) |
| GET, POST | `/api/fear-entries` | List or add fear log entries |
| PUT, DELETE | `/api/fear-entries/{id}` | Update or archive (`POST .../restore`, `DELETE .../permanent`) |
| POST | `/api/dreams/{id}/commitment` | Sign or replace the commitment |

`POST` and `PUT /api/steps` now require `owner` and `targetDate`.
`PATCH /api/steps/{id}/status` does not. Partner status also accepts `AGREED`.

## Assessment, Modules and Plan (V8)

| Method | Path | Purpose |
|---|---|---|
| GET, PUT | `/api/chain-assessments` | History with improvement from baseline, or save one day's scores |
| GET | `/api/modules` | All modules in order, with status for this user |
| GET | `/api/modules/{number}` | Content, saved rows, and what is missing. **400** while locked |
| PUT | `/api/modules/{number}/completion` | Save `{ "rows": [...] }`. Data-proven modules send an empty list |
| GET | `/api/plan-status/{dreamId}` | The seven plan stages and `planReady`. The `STRENGTHS` stage (V8.1) never affects `planReady` |
| GET, POST | `/api/milestones` | List with `?dreamId=`, or add one |
| DELETE | `/api/milestones/{id}` | Archive |

## Strengths and Gaps (V8.1)

Private to the account (BR-64); no coach endpoint reads them.

| Method | Path | Purpose |
|---|---|---|
| GET, POST | `/api/self-assessment-items` | List with `?dreamId=` (and `includeArchived`), or add a strength or weakness. `evidence` is required (BR-61) |
| PUT | `/api/self-assessment-items/{id}` | Update. Changing a weakness to a strength clears its cover but keeps the linked record |
| PUT | `/api/self-assessment-items/{id}/cover` | Choose the one way a high-relevance weakness is covered: `PARTNER` (`partnerName`), `DELEGATE` or `RESOURCE` (`note`), `LEARN` (`goalId`, `targetDate`; at most 2 at once, BR-62) or `ACCEPT` (`note`). **400** if it already has one |
| DELETE | `/api/self-assessment-items/{id}/cover` | Clear the cover. `?removeLinked=true` also archives the partner or step, or removes the exercise row. Returns `{ linkedItemRemoved, note }` |
| DELETE | `/api/self-assessment-items/{id}` | Archive, with the same `removeLinked` choice (`POST .../restore`, `DELETE .../permanent`) |
| GET | `/api/strengths-profiles/{dreamId}` | The one-page profile: what I bring, what I lack, recorded only, `complete`, Learn use, and changes since the last earlier save |
| POST | `/api/strengths-profiles/{dreamId}/snapshots` | Save the profile today (a second save that day replaces it). **400** with no items |

## Progress and Account (V8)

| Method | Path | Purpose |
|---|---|---|
| GET | `/api/progress-metrics?date=` | BO-01 to BO-06 for this user |
| GET | `/api/dreams/{id}/plan.pdf` | The dream's plan as a PDF |
| GET, PUT | `/api/account/reminders` | V20 (SV-FR-04): a **different** `reminderEmail` needs `X-Step-Up` (a code verified for `CHANGE_REMINDER_EMAIL`, or the verified window) and, unless it is cleared, `X-Step-Up-Confirm` (a code verified for `CONFIRM_REMINDER_EMAIL` with `target` = the new address). Each missing check answers `403` `"code": "STEP_UP_REQUIRED"` with `"action"` naming the code to ask for. The same address in another case, and every other field, need no code. A change sends a notice to the account email and the old address. Reminder setting: `{ "enabled": true, "timeZone": "Asia/Phnom_Penh", "reminderEmail": "me@example.org" }`. A blank `reminderEmail` uses the login email; the reply's `sendsTo` says where mail goes. V19 (RM-FR-49): optional `reportReminder` (`NONE`, `WEEKLY`, `MONTHLY`) and `reportReminderDay` (1 = Monday to 7 = Sunday, required for `WEEKLY`, cleared otherwise); left out, the report reminder is unchanged. The reply always carries both |
| POST | `/api/account/reminders/test` | Sends a test reminder to `sendsTo` now; 400 when no mail server is set up |
| POST | `/api/account/step-up?language=en` | V20 (SV-FR-10 to SV-FR-14): `{ "action", "target"? }` sends a six-digit "Verify it's you" code. Actions: `CHANGE_PASSWORD`, `EXPORT_DATA`, `CHANGE_REMINDER_EMAIL` (settings group, verified window), `CONFIRM_REMINDER_EMAIL` (needs `target`, the new address, where the code goes), `DELETE_ACCOUNT`, `ADMIN_ROLE_CHANGE`, `ADMIN_AI_KEY`, `ADMIN_COHORT_COACH`. 201 `{ "challengeId", "sentTo" (masked), "expiresAt", "resendAfter", "delivery" }`; `delivery` is `EMAIL`, `LOG` (local/test run, code in the server log) or `NONE` (no mail server: `challengeId` null, the action keeps its password checks). 400 for a `target` on another action or a bad address; 429 within 60 s of the last code for the same action, over 5 codes an hour or 20 a day; 503 `"code": "STEP_UP_MAIL_FAILED"` when sending fails (nothing is kept) |
| POST | `/api/account/step-up/{challengeId}/verify` | V20 (SV-FR-13, SV-FR-16): `{ "code" }` (spaces and dashes ignored). 200 `{ "verified": true, "challengeId", "windowUntil" }` (`windowUntil` set for the settings group). 400 `"code": "STEP_UP_WRONG_CODE"` with `triesLeft`; 410 `"code": "STEP_UP_CODE_ENDED"` when used, ended (10 minutes) or out of tries; 404 for another person's or another session's code. Ten wrong tries within an hour end the person's other sessions. Error bodies may carry `code` and `triesLeft` |
| GET | `/api/account/export` | All of this user's data as JSON. Never includes the password hash. V20 (SV-FR-03): needs the `X-Step-Up` header with a code verified for `EXPORT_DATA`, or the verified window; otherwise `403` `"code": "STEP_UP_REQUIRED"` |
| POST | `/api/account/delete` | Permanently delete the account: `{ "password": "..." }`. **400** if wrong. V20 (SV-FR-02): needs `X-Step-Up` with a code verified for `DELETE_ACCOUNT` within 5 minutes (never the window), checked before the password; a wrong password does not use the code up |

## Module Content (V8, ADMIN only)

Every other role gets **403**. `GET /api/modules` and `GET /api/modules/{number}` also
accept `?language=km`; a language with no text falls back to English.

| Method | Path | Purpose |
|---|---|---|
| GET | `/api/admin/modules?language=` | All modules' text in one language (`en`, `km`) |
| PUT | `/api/admin/modules/{number}/content/{language}` | Save text and optional `fieldLabels` |
| PUT | `/api/admin/modules/order` | `{ "orderedNumbers": [...] }`, every module exactly once |
| GET | `/api/ui-text?language=` | Public. Interface text an admin changed, as `{ "English text": "replacement" }` |
| GET | `/api/admin/ui-text?language=` | Admin. The changes for one language, with ids |
| PUT | `/api/admin/ui-text` | Admin. `{ "language": "km", "sourceText": "Today", "text": "..." }`; placeholders such as `{count}` must be kept |
| DELETE | `/api/admin/ui-text/{id}` | Admin. Removes a change, restoring the app's own text |

### V9: cohorts and coaches

| Method | Path | Notes |
| --- | --- | --- |
| GET | `/api/cohort` | The signed-in person's cohort, coach and journal sharing setting |
| POST | `/api/cohort/join` | `{ "joinCode": "ABCD2345" }`; also accepted as `joinCode` on `POST /api/auth/register` |
| POST | `/api/cohort/leave` | Leaves the cohort |
| PUT | `/api/cohort/sharing` | `{ "shareJournalsWithCoach": true }` |
| GET | `/api/coach-comments` | Comments from the coach, newest first |
| POST | `/api/coach-comments/{id}/read` | Marks one as read |
| GET | `/api/coach/participants` | Coach or admin. Participants of the active cohorts they coach, with objectives, last activity and `atRisk` (7 quiet days) |
| GET | `/api/coach/participants/{userId}` | Coach. Read-only plan; `fearEntries` and `attitudeCheckIns` are null unless shared. 404 outside the coach's cohorts |
| POST | `/api/coach/participants/{userId}/comments` | Coach. `{ "targetType": "GENERAL" \| "DREAM" \| "GOAL" \| "STEP", "targetId": 12, "body": "..." }`; the target must be in that participant's plan |
| GET, POST | `/api/admin/cohorts` | Admin. List, or create `{ "name", "coachEmail", "startDate" }`; naming a coach gives them the COACH role. V20: naming a coach needs `X-Step-Up` with a fresh code: `ADMIN_ROLE_CHANGE` when it makes a USER a coach, else `ADMIN_COHORT_COACH` (the `403` names which) |
| PUT | `/api/admin/cohorts/{id}` | Admin. Update name, coach and start date. V20: a **different** coach needs a fresh code as above; other edits need none |
| POST | `/api/admin/cohorts/{id}/join-code` | Admin. New join code; the old one stops working |
| POST | `/api/admin/cohorts/{id}/archive`, `/restore` | Admin. An archived cohort's coach loses access and its code stops working |
| GET, POST | `/api/admin/cohorts/{id}/members` | Admin. List, or add `{ "email" }` (moves them from any other cohort) |
| DELETE | `/api/admin/cohorts/{id}/members/{userId}` | Admin. Removes them from the cohort |
| PUT | `/api/coach/participants/{userId}/pitch-rating` | Coach. `{ "rating": 1-5, "feedback": "..." }` on the participant's module 11 pitch (v0.1 FR-11); 400 if no pitch is saved. The participant sees it in `GET /api/modules/11` as `coachRating`, `coachFeedback`, `coachName`, `coachRatedAt`; saving a changed pitch clears it |

### V9: AI settings (AI-FR-24, admin only)

| Method | Path | Notes |
| --- | --- | --- |
| GET | `/api/admin/ai-settings` | `enabled`, `model`, `models` (choices), `monthlyBudgetUsd`, `keySource` (`DATABASE`, `ENVIRONMENT`, `NONE`), `keyReadable`, `apiKeyLast4`, last test result. The key itself is never returned |
| PUT | `/api/admin/ai-settings` | `{ "enabled", "model", "monthlyBudgetUsd": 0-1000 }`; turning AI on needs a key |
| PUT | `/api/admin/ai-settings/api-key` | `{ "apiKey": "sk-ant-..." }`; stored AES-GCM encrypted; takes priority over `ANTHROPIC_API_KEY`. V20 (SV-FR-07): needs `X-Step-Up` with a fresh code for `ADMIN_AI_KEY`, every time |
| DELETE | `/api/admin/ai-settings/api-key` | Removes the saved key; AI turns off unless `ANTHROPIC_API_KEY` is set. V20: needs a fresh `ADMIN_AI_KEY` code |
| GET | `/api/admin/step-up` | V20 (SV-FR-34). Admin. `{ "enabled", "delivery": "EMAIL|LOG|NONE", "active" }`: whether risky actions ask for an email code; the admin pages warn when `enabled` and `NONE` |
| POST | `/api/admin/ai-settings/test` | Looks up the chosen model with the key through the Models API (no tokens used) and stores the result |

### V9: Dream Coach (Phase 3)

| Method | Path | Notes |
| --- | --- | --- |
| GET | `/api/ai/status` | `available`, `reason` (`OK`, `OFF`, `BUDGET_REACHED`, `NO_CONSENT`, `DAILY_LIMIT`), `consentGiven`, `consentAt`, `dailyLimit`, `messagesLeftToday` |
| PUT | `/api/ai/consent` | `{ "consent": true \| false }`; each change is recorded |
| GET | `/api/ai/conversations` | The signed-in person's conversations, newest first, each with `dreamId` and `section` (set for plan assistant conversations). `?dreamId=12&section=GOALS` lists only the plan assistant's conversations for that dream and section |
| DELETE | `/api/ai/conversations` | Clears all of the signed-in person's Dream Coach conversations (204). The usage log keeps cost and ratings, never text |
| GET, DELETE | `/api/ai/conversations/{id}` | One conversation with its messages, or delete it (404 for anyone else's) |
| POST | `/api/ai/chat` | `{ "conversationId": null, "message": "...", "language": "en" \| "km", "page": "/dreams/12", "dreamId": 12, "moduleNumber": null }` (page, dreamId and moduleNumber pick the context). 400 without consent, 429 at the daily limit, 503 "AI help is temporarily unavailable." when off, over budget or unreachable (nothing saved). A crisis message gets the help lines without calling Claude; `safetyFlag` is `CRISIS` |
| POST | `/api/ai/chat/stream` | Same body; answers `text/event-stream`: `delta` events `{ "text" }`, then `done` (the saved turn, whose reply text is final) or `error` `{ "message" }`. Errors before streaming starts answer as JSON, like `/chat`. Replies carry `contextSources` (`plan`, `dream`, `tasks`, `attitude`, `fears`, `obstacles`, `module`) |
| PUT | `/api/ai/messages/{id}/rating` | `{ "rating": 1 \| -1 \| 0, "comment" }` on a reply; 0 clears it |
| GET | `/api/coach/ai-alerts` | Coach: wellbeing alerts for their participants; an admin also sees those with no coach. No message text |
| POST | `/api/coach/ai-alerts/{id}/acknowledge` | Marks that someone checked in |

`PUT /api/admin/ai-settings` also takes `dailyMessageLimit` (1-500) and `helpLines`; the response adds `spendThisMonthUsd` and `requestsThisMonth`.

### V9: vision area, dream, goal and plan builders (Phase 5)

All take `?language=en|km`. They only suggest; adding uses `POST /api/vision-areas`, `POST /api/dreams`, `POST /api/goals` and `POST /api/steps`. Each AI call needs consent and counts toward the daily limit.

| Method | Path | Notes |
| --- | --- | --- |
| POST | `/api/ai/plan/vision-area-suggestions` | `areas[]` (`name`, `description`, `visionStatement`, `priority`, `reason`), up to 5, none repeating the user's areas |
| POST | `/api/ai/plan/vision-areas/{visionAreaId}/dream-suggestions` | `dreams[]` (`title`, `description`, `whyImportant`, `successDefinition`, `dreamType`, `priority`, `targetDate`, `moonshot`, `reason`) or a `clarifyingQuestion`. `?stretch=true` returns one moonshot dream |
| POST | `/api/ai/plan/dreams/{dreamId}/goal-suggestions` | `goals[]` (`title`, `description`, `successCriteria`, `targetDate`, `priority`, `moonshot`, `reason`) or a `clarifyingQuestion`. `?stretch=true` returns one stretch goal |
| POST | `/api/ai/plan/goals/{goalId}/step-suggestions` | `steps[]` (`title`, `description`, `owner`, `targetDate`, `resources`, `people`, `reason`) or a `clarifyingQuestion` |
| POST | `/api/ai/plan/dreams/{dreamId}/check` | `issues[]` (`kind`, `itemType`, `itemId`, `itemTitle`, `problem`, `fix`, `fromAi`) and `aiChecked`. Works without AI, with the rule-based checks only |
| GET | `/api/ai/plan/slipping-steps` | Unfinished steps overdue again after being given a new date while overdue |
| POST | `/api/ai/plan/steps/{stepId}/rescue` | `smallerStep`, `newDate`, `helper`, `reason` |

Steps now take and return `resources` (left out on update keeps the saved value; blank clears it) and return `overdueReschedules`.

### V11: plan builder feedback and safe adding (Phase 1)

Every suggestion answer above now also returns `requestId` (the plan check returns it only when the AI review ran). Suggested dates are kept only when they are after today and not after the parent's date: a goal not after its dream, a step not after its goal (or its dream when the goal has none). Other dates come back empty for the participant to pick.

| Method | Path | Notes |
| --- | --- | --- |
| PUT | `/api/ai/plan/requests/{id}/rating` | `{ "rating": 1 \| -1 \| 0, "comment" }`; 0 clears. 204. Another user's answer, or one that is not a plan builder answer, answers 404 |
| POST | `/api/ai/plan/requests/{id}/outcome` | `{ "added", "edited" }` for vision areas and dreams added through the normal endpoints. Counts never pass what the answer suggested. 204 |
| POST | `/api/ai/plan/add` | `{ "requestId", "goals": [{ "goal": GoalRequest, "edited" }], "steps": [{ "step": VisionStepRequest, "edited" }] }` (up to 10 goals, 20 steps) → 201 `goalIds[]`, `stepIds[]`. One transaction: if any item fails its checks, nothing is saved. With `requestId`, goals must come from a goal answer and steps from a step answer |
| POST | `/api/ai/plan/undo` | `{ "requestId", "goalIds", "stepIds" }` archives those records (soft delete) and resets that answer's outcome counts. Every id is checked first; one that is not the caller's answers 404 and nothing changes. 204 |
| GET | `/api/admin/ai/plan-metrics?days=30` | Admins only. Per plan builder feature: `requests`, `suggestedItems`, `addedItems`, `editedItems`, `acceptanceRate`, `editRate`, `ratings`, `thumbsUpRate`, `costUsd`, `averageCostUsd`. `days` is 1 to 365 |
| GET | `/api/admin/ai/plan-accuracy?days=30` | Admins only (V31 DC-FR-65). `checkedSessions`, `firstCheckPassRate` (DCM-01), `rules[]` (`rule`, `firstRuns`, `firstPassRate` DCM-02, `saves`, `acceptedShould`), `savedPlans`, `savedValidRate` (DCM-03), `aiItemsChecked`, `aiDateConflictRate` (DCM-06), `aiOwners`, `inventedOwnerRate` (DCM-07). Rates 0 to 1, null when nothing was measured. `days` is 1 to 365 |

### V11: Build my plan drafts (Phase 2)

One draft per user per dream. Only its owner can read it; another user's dream answers 404. A draft is not plan data and does not appear in lists, dashboards, exports, coach views or share links. It is removed 14 days after its last change, when the plan is added, when discarded, with its dream and with the account.

| Method | Path | Notes |
| --- | --- | --- |
| GET | `/api/ai/plan/drafts/{dreamId}` | `dreamId`, `stage`, `content`, `updatedAt`, `expiresAt`, `aiRequestsUsed`, `aiRequestLimit` (V11 Phase 8); 204 when there is none |
| PUT | `/api/ai/plan/drafts/{dreamId}` | `{ "stage": "CLARIFY\|GOALS\|STEPS\|TASKS\|HELPERS\|REVIEW", "content": { "goals": [...] } }`. A goal or step with `existingId` stands for one already in the plan and only carries new children. Up to 15 goals, 25 steps per goal, 15 tasks per step. Fields may be blank while drafting. Refused for an archived dream |
| DELETE | `/api/ai/plan/drafts/{dreamId}` | Discards the draft. 204 |
| POST | `/api/ai/plan/drafts/{dreamId}/add` | Saves every new goal, step and task in one transaction with the normal create rules (a step needs title, owner and date; a task title, owner and due date) → 201 `goalIds`, `stepIds`, `taskIds`. A problem answers 400 naming the item (for example `Step "Visit banks" needs an owner.`) and nothing is saved; the draft stays. An `existingId` that is not this dream's live goal (or that goal's live step) is refused. On success the draft is removed and outcome counts are recorded for items carrying a `requestId`. **V31 (DC-FR-56):** the server first re-runs the rules R-01 to R-08 and refuses any MUST fail with 400 `Fix this before saving: "{item}": {problem} (and N more)`. Optional body `{ "acceptedShould": ["R-02", "R-06"] }` names the rules whose SHOULD fails the participant accepted (only `R-01` to `R-08`; counted in `plan_check_events`, not required). New draft fields saved: goal `approved`, step `gapKind`, task `status` (empty = Not started) and `blockerReason` |
| POST | `/api/ai/plan/dreams/{dreamId}/draft-step-suggestions` | `{ "title", "successCriteria", "targetDate" }` for a goal that exists only in the draft → the same answer as step suggestions, with dates kept within the goal's date (or the dream's) |

`POST /api/ai/plan/undo` also takes `taskIds` (up to 500).

### V11: clarify and refine (Phase 3)

`POST /api/ai/plan/dreams/{dreamId}/goal-suggestions` and `POST /api/ai/plan/goals/{goalId}/step-suggestions` now take an optional body, and `draft-step-suggestions` takes the same object as `ask`:

```json
{ "answers": [{ "question": "...", "answer": "..." }], "suggestAnyway": false,
  "refine": "FEWER|MORE|SMALLER|BOLDER|EARLIER|LATER|ANOTHER", "note": "...", "keep": ["..."], "replace": ["..."] }
```

`answers` (up to 6) are used in the suggestion. After `suggestAnyway`, a refinement, or three answers, no clarifying question comes back. `refine` replaces the items in `replace` and leaves those in `keep`; `FEWER` returns fewer than were replaced, `ANOTHER` exactly one. All text in the body is sent to Claude as the participant's data, never as instructions.

| Method | Path | Notes |
| --- | --- | --- |
| POST | `/api/ai/plan/dreams/{dreamId}/clarify` | `{ "answers": [...] }` (up to 3) → `question`, `field` (`WHY_IMPORTANT`, `SUCCESS_DEFINITION`, `TARGET_DATE` or null), `done`, `requestId`. With three answers it answers `done` without asking Claude |
| PUT | `/api/ai/plan/dreams/{dreamId}/clarified-field` | `{ "field", "value" }` saves one answer into the dream → the dream. `TARGET_DATE` takes YYYY-MM-DD and follows the dream form's date rule |

Drafts also keep `clarifications` (up to 6) and a `locked` flag on goals and steps.

### V11: richer steps and the task builder (Phase 4)

Steps take and return `estimatedHours` (0 to 999.99) and `startsAfterStepId` (a step of the same goal; another user's step answers 404, another goal's 400). On update, leaving either out keeps the saved value; `startsAfterStepId: 0` clears it.

Step suggestions now also return `priority`, `complex`, `estimatedHours` and `startsAfter` (the 1-based position of an earlier step in the same answer, or null). A suggested date before the step it waits for comes back empty.

| Method | Path | Notes |
| --- | --- | --- |
| POST | `/api/ai/plan/steps/{stepId}/task-suggestions` | Optional `PlanAsk` body → `tasks[]` (`title`, `owner`, `dueDate`, `priority`, `estimatedHours`, `reason`), up to 8, or a `clarifyingQuestion`, and `requestId`. Due dates are kept within the step's date (or its goal's, or its dream's) |
| POST | `/api/ai/plan/dreams/{dreamId}/draft-task-suggestions` | `{ "goalTitle", "goalTargetDate", "stepTitle", "stepDescription", "stepTargetDate", "ask" }` for a step that exists only in the draft; same answer |

### V11: helpers and risks (Phase 5)

| Method | Path | Notes |
| --- | --- | --- |
| POST | `/api/ai/plan/dreams/{dreamId}/helpers-and-risks` | `{ "items": [{ "key", "kind": "GOAL\|STEP", "title", "resources", "goalKey" }], "useFears": false, "ask": PlanAsk }` → `helpers[]` (`role`, `supportType`, `forKey`, `reason`, `matches[]` of the caller's own partners: `partnerId`, `name`, `role`, `supportType`, `status`), `risks[]` (`title`, `description`, `obstacleType`, `severity`, `solution`, `forKey`, `helperType`, `helperLabel`, `fromFears`), `resources[]` (approved ones first, others `checkBeforeUse`), `requestId`. Partners are matched on the server; their names and contact details are never sent to Claude. The fear journal (fears of this dream or of none) is read only with `useFears: true` |

Drafts take `helpers` (up to 10: `key`, `role`, `supportType`, `name`, `partnerId`, `partnerName`, `linkKey`, `requestId`, `edited`) and `risks` (up to 10: `key`, `title`, `description`, `obstacleType`, `severity`, `solution`, `linkKey`, `helperKey`, `fromFears`, `requestId`, `edited`). V17 (BL-FR-37): a risk's `linkKey` must be a draft step's key, or `newstep:{goalKey}`, which adds a step "Deal with: {title}" under that goal (dated by the goal, else the dream); otherwise Add answers 400 "Choose the step this risk holds up: {title}" and saves nothing. Suggested risks' `forKey` is a step key or null. On add, a helper with `partnerId` links that partner to its goal or step only where the partner has no link yet; one without becomes a new partner with status To Contact. Each risk becomes an Open obstacle on its goal or step, with `helperKey`'s partner as the required partner. The add result also returns `partnerIds`, `obstacleIds` (archived by Undo, which takes them too) and `linkedPartnerIds`.

### V11: draft check, fixes, capacity and the plan model (Phase 6)

| Method | Path | Notes |
| --- | --- | --- |
| POST | `/api/ai/plan/dreams/{dreamId}/draft-check` | Checks the saved draft (save it first) → `issues[]` (`kind`, `itemType`, `itemKey`, `parentKey`, `itemTitle`, `problem`, `fix`, `fixAction`, `fixValue`, `fromAi`), `aiChecked`, `requestId`. Rule kinds: `MISSING_OWNER`, `MISSING_DATE`, `PAST_DATE`, `AFTER_GOAL_DATE`, `BEFORE_WAITED_STEP`, `TASK_AFTER_STEP`, `MISSING_RESOURCE`, `COMPLEX_NO_TASKS`, `NO_STEPS`; with AI also `VAGUE_STEP`, `UNREALISTIC_TIMING`, `GAP`. `fixAction`: `SET_OWNER_ME`, `SET_DATE` (to `fixValue`), `GO_TO_STEPS`, `GO_TO_TASKS`, `FIX_WITH_AI`. Rule checks work without AI. Saves nothing. **V31:** every issue adds `rule` (`R-01` to `R-08`) and `level` (`MUST` blocks Save my plan, `SHOULD` needs "I understand", `INFO` is advice; AI issues are `INFO`). New kinds: `GOAL_NOT_IN_DREAM`, `STEP_NOT_IN_GOAL` (R-01); `DREAM_NO_WHY`, `DREAM_NO_SUCCESS`, `DREAM_NO_DATE`, `DREAM_NO_PRIORITY`, `MISSING_TITLE`, `GOAL_NO_MEASURE`, `GOAL_NO_DATE`, `TOO_MANY_GOALS` (R-02); `TASK_NO_PRIORITY` (R-03); `GOAL_AFTER_DREAM` (R-04); `BLOCKED_NO_REASON`, `RISK_NOT_ON_STEP` (R-05); `GAP_NO_KIND`, `GAP_NO_HELPER` (R-06); `GOAL_NOT_APPROVED`, `AI_UNCHANGED` (R-07). New `fixAction`s: `SET_PRIORITY`, `APPROVE`, `GO_TO_CLARIFY`, `GO_TO_GOALS`, `GO_TO_HELPERS`. `?rulesOnly=true` runs the rules alone and never calls Claude (the live pass/fail list). Each run writes one `plan_check_events` row per rule (counts only). **V31 v1 step 2:** a date conflict may carry `altFixAction` (`MOVE_PARENT_DATE` for a new parent goal or step, `MOVE_DREAM_DATE` for a dream that is not a fixed deadline) and `altFixValue` (the child's date). New kind `INNER_SKIPPED` (R-05, SHOULD) unless the draft's `innerBlockers` is `READY`. `GAP_NO_HELPER` and `RISK_NOT_ON_STEP` now fix with `GO_TO_STEPS` |
| POST | `/api/ai/plan/vision-statement?language=en` | V31 v1 (DC-FR-12). Body `{ areaName, sentence }` → `statement`, `requestId`. Words the participant's sentence as a vision statement; adds no details. Feature `COACH_AREA`. Needs AI consent (400 without, 503 when AI is off). Saves nothing |
| POST | `/api/ai/plan/dream-intake?language=en` | V31 v1 (DC-FR-15, DC-FR-16). Body `{ areaName?, words, answers[] (≤3: question, answer, field?), suggestAnyway? }` → `done`, `question`, `field` (`WHY_IMPORTANT`, `SUCCESS_DEFINITION`, `TARGET_DATE`), and when done `title`, `description` (the participant's words), `whyImportant`, `successDefinition`, `targetDate` (only a future date the participant gave as a `TARGET_DATE` answer). No question after 3 answers or with `suggestAnyway`. Feature `COACH_DREAM`. Saves nothing |
| POST | `/api/ai/plan/dreams/{dreamId}/draft-fix` | `{ "itemKey", "kind": "VAGUE_STEP\|UNREALISTIC_TIMING\|GAP", "problem" }` → `title` (vague), `targetDate` (timing, within the goal's date) or `newStepTitle`, `newStepOwner`, `newStepTargetDate` (gap, `itemKey` is the goal), `note`, `requestId`. Saves nothing |
| GET | `/api/ai/plan/capacity` | `hoursPerWeek` (or null) and `planned[]` (`weekStart` Monday, `hours`) for the next 26 weeks: open tasks by due date, and open steps without tasks by date |
| PUT | `/api/ai/plan/capacity` | `{ "hoursPerWeek": 0.5 to 100 \| null }` |

`/api/admin/ai-settings` takes and returns `planModel`: the model for plan building (goal, step and task suggestions, clarify, helpers and risks, plan checks and fixes). Left out keeps it; blank or null uses the main model.

Draft steps take `estimatedHours` and `startsAfterKey` (the key of an earlier step of the same goal in the draft); draft tasks take `requestId`, `edited` and `locked`. On add, `startsAfterKey` becomes the saved step's `startsAfterStepId`.

### V11: plan assistant (Phase 7)

Helps with one section of the Plan page (`surface` `PLAN`, sections `WHOLE`, `STATEMENT`, `GOALS`, `STEPS`, `DATES`, `RESOURCES`, `PEOPLE`, `MILESTONES`) or of Build my plan (`BUILDER`, sections `WHOLE`, `CLARIFY`, `GOALS`, `STEPS`, `TASKS`, `HELPERS`, `REVIEW`). A section not on that page answers 400. Another user's dream answers 404, before AI is checked. Only the section's items go to Claude, never partner names or contacts. Items have a ref: `dream`, `goal:12` or `step:34` on the Plan page, the draft key in Build my plan.

| Method | Path | Notes |
| --- | --- | --- |
| POST | `/api/ai/chat` and `/api/ai/chat/stream` | Add `"surface"`, `"section"`, optional `"itemRef"` and `"dreamId"` to send the message to the plan assistant. The answer's `contextSources` is `["section"]`; usage is logged as `PLAN_ASSISTANT_CHAT`. A new conversation is tagged with the dream and section |
| POST | `/api/ai/plan/dreams/{dreamId}/assistant/section-check` | `{ "surface", "section", "itemRef" }` → `issues[]` (`kind`, `itemType`, `itemRef`, `itemTitle`, `problem`, `fix`) from the rule-based plan check for that section, and `canPropose`. Never uses AI |
| POST | `/api/ai/plan/dreams/{dreamId}/assistant/proposals?language=en` | Same body → `changes[]`, `note`, `requestId` (feature `PLAN_ASSISTANT`, rated and counted like other plan answers). A change has `kind`: `EDIT` (`itemRef`, `itemTitle`, `field` `TITLE\|SUCCESS\|WHY\|DATE\|OWNER`, `value`), `ADD_STEP` or `ADD_TASK` (`parentRef`, `parentTitle`, `title`, `owner`, `date`, null when it broke a date limit) or `ADD_RISK` (`title`, `obstacleType`, `severity`, `solution`, `parentRef` of a step; V17 BL-FR-38 drops risks on the dream or a goal); and `reason`. Up to 5. Changes outside the section, to items already in the plan (Build my plan), or with a date past the parent's are dropped. 400 for a section that only explains (`PEOPLE`, `MILESTONES`, `WHOLE`). Saves nothing |
| POST | `/api/ai/plan/dreams/{dreamId}/assistant/apply` | Plan page only: one change as the participant left it, plus `requestId` and `edited` → 201 `itemType`, `id`. Saved through the normal dream, goal, step and obstacle rules (a new step needs a date; V17: `ADD_RISK` needs a `step:` parent, else 400 "Choose the step this risk holds up."). Every ref must be in this dream's plan; others answer 404. `ADD_TASK` answers 400 (tasks are changed in Build my plan) |

In Build my plan, cards are applied to the draft in the browser and counted with `POST /api/ai/plan/requests/{id}/outcome`.

### V15: page assistant (Phase 1)

Helps with one area of the Modules list (`page` `MODULES`), a module (`MODULE`, `itemId` = module number), the Assessment (`ASSESSMENT`), Obstacles (`OBSTACLES`), Communication (`COMMUNICATION`) or Work Style (`WORK_STYLE`) page. `area` names the part of the page, for example `page`, `list`, `item`, `form`, `message`, `field:hook`, `statement:fearScore`, `result`, `question:3` or `exercise`. `itemId` is the open obstacle or message; another user's answers 404, before AI is checked. `form` is the unsaved form as `{ "field": "text" }` (up to 60 fields), sent as data and never stored. What goes to Claude follows BRD 15 section B: never partner names or contacts on the Obstacles page, only a partner's name and role on the Communication page, never the obstacle's private reaction note, and the subjects, dates and statuses (never bodies) of earlier messages to the same partner.

| Method | Path | Notes |
| --- | --- | --- |
| POST | `/api/ai/chat` and `/api/ai/chat/stream` | Add `"assistantPage"`, `"area"`, optional `"itemId"` and `"form"` to send the message to the page assistant. Usage is logged as `PAGE_ASSISTANT_CHAT`. A new conversation is tagged `PAGE:{page}` or `PAGE:{page}:{itemId}`. The Assessment and Work Style pages carry their answer guards (never suggest a score or pick a quiz answer) |
| GET | `/api/ai/conversations?section=PAGE:OBSTACLES:12` | The page assistant's conversations for that page and item, newest first |
| POST | `/api/ai/pages/assistant/check` | `{ "page", "area", "itemId", "form" }` → `canPropose` and `issues[]` (`field`, `problem`, `fix`). Rule checks only, never AI: obstacles without a description, root cause or solution; messages with no request, no benefit to the partner, no call to action, or a body over 250 words; empty module exercise fields. `canPropose` is false on the Modules list, Assessment and Work Style |
| POST | `/api/ai/pages/assistant/proposals?language=en` | Same body → `changes[]` (`field`, `value`, `reason`), `note`, `requestId` (feature `PAGE_ASSISTANT`), `sources`. Only the page's own form fields are kept: obstacle `title`, `description`, `obstacleType`, `severity`, `rootCause`, `solution`, `creativeAlternatives`; the message text fields and `status`; a module's `row{n}.{fieldKey}` (n 0 to 2, ratings 1 to 5). Values outside an enum, unknown fields and empty values are dropped; at most two versions of one field and five changes. With `area` `field:{name}`, only that field. Saves nothing |
| POST | `/api/ai/pages/assistant/requests/{id}/outcome` | `{ "added", "edited" }` → 204. Cards applied as suggested or after editing; the rest count as dismissed. Ratings use `PUT /api/ai/plan/requests/{id}/rating` |
| GET | `/api/ai/pages/metrics?days=30` | Admin only. Per V15 feature (AH-FR-55): `requests`, `suggestedItems`, `appliedItems`, `editedItems`, `dismissedItems`, `appliedRate`, `ratings`, `thumbsUpRate`, `costUsd`, `averageCostUsd`; and `baselines` for AHBO-01 (next module within 7 days), AHBO-03 (open obstacles with a root cause, solution or task) and AHBO-04 (drafts, sent, follow-ups past their date) |

The page assistant and helpers use the simple-task model (`planModel`) when the admin has set one (AH-NFR-02).

### V15: obstacle helpers (Phase 2)

| Method | Path | Notes |
| --- | --- | --- |
| POST | `/api/ai/obstacles/help?language=en` | `{ "mode", "obstacleId", "form", "answers" }`. Modes: `CLASSIFY` (cards for `obstacleType` and `severity`), `ROOT_CAUSE` (with up to three `answers` `{ question, answer }`: returns the next `question`, or a `rootCause` card; after three answers always the card), `SOLUTIONS` (up to three cards for `solution` or `creativeAlternatives`, each "idea First step: ..."), `NEXT_TASK` (`task`: `title`, `description`, `owner` always `Me`, `dueDate` between today and the linked step's date or null, `priority`, `stepId`, `stepTitle`, `latestDate`; the step comes from the form's or saved `relatedStepId`, else the linked task's step) and `PRIORITISE` (`ranking` in the rule order below, with a `reason` on up to the top three). Reads the obstacle exactly as the page assistant does: never partner names or contacts, never the private reaction note; resolved obstacles of the same type are included (AH-FR-38). Another user's obstacle or linked record answers 404 before AI is checked; 400 with no obstacle and no form. Logged as `OBSTACLE_HELP`; cards are counted with `POST /api/ai/pages/assistant/requests/{id}/outcome`. Saves nothing and never changes an obstacle's status |
| GET | `/api/obstacles/partner-matches?obstacleType=KNOWLEDGE` | No AI. `suggestedSupportType` (the CLAUDE.md partner rule; `OTHER` for partner, system and other gaps) and up to five of the participant's own partners, To Contact to Waiting, whose support type or role fits: `id`, `name`, `role`, `supportType`, `status` |
| GET | `/api/obstacles/priority` | No AI. Open and In Progress obstacles: severity first, then Blocked tasks they hold up (V17: for an obstacle on a task or step, only that task or the tasks under that step; for an unplaced one, the goal or dream it names), then days open, then placed before unplaced (BL-FR-10). `id`, `title`, `severity`, `blockedTasks`, `daysOpen`, `reason` (null) |
| GET | `/api/tasks/blocked-without-obstacle` | No AI. Blocked tasks with a blocker reason that no unarchived obstacle names: `id`, `code`, `title`, `blockerReason`, `stepId`, `stepTitle`, `goalId`, `dreamId` |

### V15: Communication helpers (Phase 3)

| Method | Path | Notes |
| --- | --- | --- |
| POST | `/api/ai/communication/help?language=en` | `{ "mode", "messageId", "form", "intent", "tone" }`. Modes: `FILL` (needs `intent`, one sentence up to 300 characters: cards only for structured fields that are empty; never the body or status; `MESSAGE_FIELD_HELP`), `CHECK` (`issues[]`: `problem` and an optional `fix` card for one text field; `MESSAGE_CHECK`), `TONE` (needs `tone` `SHORTER`, `WARMER`, `FORMAL`, `KHMER` or `ENGLISH`: one `messageBody` card; `KHMER` and `ENGLISH` answer in that language; `MESSAGE_BODY`), `BODY` (the body rebuilt from the fields only, one `messageBody` card; `MESSAGE_BODY`), `OBJECTIONS` (one `objectionsAndAnswers` card; `MESSAGE_OBJECTIONS`), `FOLLOW_UP` and `THANK_YOU` (need a saved `messageId`: `message` with `partnerId`, `subject`, `request` (empty for a thank-you), `messageBody` and the same linked dream, goal and task, for a new message; `FOLLOW_UP_DRAFT`, `THANK_YOU_DRAFT`), `MEETING_PREP` (`task`: `title` "Meet {partner role}", never the name, `description` with the outcome, three questions, what to bring and the closing ask, `owner` `Me`) and `PRIORITISE` (`ranking` in the draft order below, with a `reason` on up to the top three). Reads the message exactly as the page assistant does. Another user's message, partner or linked record answers 404 before AI is checked. Saves and sends nothing, and never changes a status or follow-up date |
| GET | `/api/communication-messages/follow-up-due` | No AI. Sent or Followed Up messages whose follow-up date has passed, longest overdue first: `id`, `subject`, `request`, `status`, `followUpDate`, `partnerId`, `partnerName`, `daysOverdue` |
| GET | `/api/communication-messages/draft-priority` | No AI. Draft messages: a Blocked linked task first, then the linked task's due date (none last), then days waiting. `id`, `subject`, `partnerName`, `taskDueDate`, `taskBlocked`, `daysWaiting`, `reason` (null) |

Help with one message field through the page assistant (`area` `field:{name}`) is logged as `MESSAGE_FIELD_HELP`. Without AI, the follow-up and thank-you open the program's templates, and Rebuild message uses Generate message.

### V15: Modules helpers (Phase 4)

| Method | Path | Notes |
| --- | --- | --- |
| GET | `/api/modules/next?language=en` | No AI. `moduleNumber`, `title`, `reason`, `rule`, `source` `RULE`, `requestId` null. `rule` is `LOW_CHAIN` (the latest assessment scored a barrier 1 or 2 and its module (settling 1, fear 2, criticism 3, vision 4, knowing how 5, resources 6) is open; the lowest score first), `PROGRAM_ORDER` (the first open module) or `ALL_DONE` (every open module is done; number and title null) |
| POST | `/api/ai/modules/next?language=en` | Claude picks one open (unlocked, not completed) module with a two-sentence reason from the participant's own data (`source` `AI`, `rule` `AI`). A pick outside the open modules, or with no reason, returns the rule answer with `source` `RULE`. `requestId` for rating; logged as `MODULE_NEXT`. Modules unlock one at a time, so there is usually one open module |
| GET | `/api/modules/journey?language=en` | No AI. `done`, `total`, `stages[]` (`stage`, `done`, `total`, in program order), `assessments` (how many takes), `chains[]` (`key`, `label`, `first`, `latest`; empty with no assessment) and `nextStage` (the first stage not finished, or null) |
| POST | `/api/ai/modules/{number}/help` | V15 adds modes and fields. `answers` (up to three `{ question, answer }` from Help me start) are used by `DRAFT` and `REFINE` and never saved. `EXPLAIN` returns `explanation`, `whyItMatters` and an `example` about a made-up person. `REFINE` needs rows in the form and `refine` (`SHORTER`, `SPECIFIC`, `MY_WORDS`, `ANOTHER`) or a `note` (up to 300 characters); rows listed in `keepRows` (0 to 2) come back exactly as sent. `CHECK` returns `checks[]` (`row` from 0, `field`, `tip`) for fields that are empty or too short. `REFINE` and `CHECK` answer 400 for a module done on another page. Nothing is saved or unlocked |

### V15: Assessment and Work Style helpers (Phase 5)

| Method | Path | Notes |
| --- | --- | --- |
| GET | `/api/chain-assessments` | Adds `retakeAfterModule`: true when the latest take is 28 days old or more and a module was completed after it (AH-FR-28). No AI |
| POST | `/api/ai/assessment/explain?language=en` | Adds `lowChains[]` (`key`, `label`, `score`, `note`) for each barrier scored 1 or 2 (other keys are dropped) and `trend` across every take (null with one take). Describes scores, never the person (AH-FR-25, AH-FR-30) |
| POST | `/api/ai/assessment/practice?language=en` | `requestId`, `practices[]` (`chainKey`, `label`, `score`, `practice`, `habit`) for each barrier scored 3 or less, each tied to the program's app action for that barrier; other keys dropped. No AI request when none is 3 or less; 400 with no assessment. `ASSESSMENT_PRACTICE` |
| POST | `/api/ai/assessment/what-changed?language=en` | `requestId`, `summary`, `modulesBetween[]` (titles of modules completed after the previous take, up to the latest). 400 with fewer than two takes. `ASSESSMENT_EXPLAIN` |
| PUT | `/api/work-style-profile/ai-use` | `{ "enabled": true }` → the profile, with `aiUseWorkStyle`. Off by default (migration V73). While on, the step and task builders, next step, module help, the assessment practice and message adaptation get the dominant and secondary style only; while off, none of them gets the style (AH-FR-76) |
| GET | `/api/work-style-profile/partner-balance` | No AI. `dominant`, `complementary` (FR-49.4), `counts` per style, `noStyle`, `missing` (complementary styles no active partner has) and `partners[]` (`id`, `name`, `role`, `supportType`, `workStyleType`, `primaryMotivator`) |
| POST | `/api/ai/work-style/help?language=en` | `{ "mode", "partnerId", "partnerNotes" }`. `EXPLAIN` (`text`, from the pole counts; `WORK_STYLE_EXPLAIN`), `BALANCE` (`text`, why a missing complementary style would help; `WORK_STYLE_EXPLAIN`), `TIPS` (up to three `practices[]` with `text`, `action` `UNDATED_STEPS`, `STEPS`, `TASKS`, `PARTNERS`, `TODAY` or `PLAN` (null otherwise) and `habit`; `WORK_STYLE_TIPS`), `PARTNER` (needs a partner with a style: two or three `tips[]`; the partner's style estimate, role and support type only, and `partnerNotes` for this request only; `WORK_STYLE_PARTNER`). 400 before the quiz is taken; another user's partner answers 404 |
| POST | `/api/ai/communication/help` | Adds mode `ADAPT` (AH-FR-59): cards for `hook`, `benefitToPartner` and `callToAction` only, fitted to the partner's work style and primary motivator (category values only; never email, phone or notes). 400 when the partner has neither. `MESSAGE_ADAPT` |

The page assistant on Work Style also accepts `area` `partner:{id}` and reads that partner's style estimate, role and support type, never the name. Pre-filled links: `/today?habit=` fills the new-habit box; `/partners?edit={id}&workStyleType=` opens that partner's form with the estimate filled in.

### V15: sensitive helpers and page switches (Phase 6)

| Method | Path | Notes |
| --- | --- | --- |
| POST | `/api/ai/obstacles/help` | Adds mode `CONFLICT` (AH-FR-40): cards for `criticismSubstance`, `conflictOtherPerspective` and `conflictNextAction` only. The private reaction (`conflictPrivateNote` in `form`) is sent only with `"includePrivateNote": true`, for that request. Crisis wording in the conflict or criticism text returns `mode` `CRISIS` with the safety response in `note`, raises the coach alert, logs `CRISIS_HANDLED` and never calls Claude |
| POST | `/api/ai/communication/help` | Adds mode `REPLY` (AH-FR-51): needs `reply` (up to 4000 characters), wrapped as data. Returns `note` (two-sentence summary), `reading` (`kind` `YES`, `NO`, `QUESTION` or `LATER`, and `nextAction`), a `status` card (`REPLIED` or `CLOSED` only) and a reply draft in `message`. The reply is never stored or logged. `REPLY_HELP` |
| PUT | `/api/admin/ai-settings` | Adds `pagesOff` (AH-FR-57): page keys where the page assistant and helpers are off for everyone. Left out keeps the list |
| POST, PUT | `/api/admin/cohorts`, `/api/admin/cohorts/{id}` | Adds `aiPagesOff`: the same, for one cohort. Left out keeps it |
| GET | `/api/ai/status` | Adds `pagesOff`: the pages off for this person (everyone's list plus their cohort's). Every V15 AI endpoint on such a page answers 400 "AI help is switched off on this page. The program's own help still works."; the rule checks and lists still work |

Program help text (guiding questions, statement explanations, the field guide, templates, practices and tips) goes through
the interface text, so admins can change it in English and Khmer on **Interface text** (AH-FR-58). The Khmer drafts for
review are in `docs/km-review-v15.csv` (`node frontend/scripts/export-km-review.mjs V15`). The quality test set is
`backend/src/test/resources/page-assistant-eval/cases.json`; run it with
`PAGE_EVAL_ANTHROPIC_KEY=sk-ant-... mvn test -Dtest=PageAssistantEvalRunTests` (writes `target/page-assistant-eval.md`).

Pre-filled forms opened from an obstacle (nothing is saved until the participant saves there): `/tasks?create=task&parent=&title=&description=&owner=&dueDate=&priority=`, `/partners?create=partner&supportType=&status=TO_CONTACT`, `/communication?create=message&partnerId=&audience=&purpose=&problem=&request=`.

### V11: session limit, finished plans, arranging (Phase 8)

- **Session limit (PB-NFR-03).** While a live draft exists for a dream, it allows 15 AI requests: goal, step and task suggestions (saved or draft), clarify, helpers and risks, draft fixes, plan assistant proposals and chat with `surface` `BUILDER`, and the AI part of both plan checks. Past the limit these answer 429 "This plan session has used its 15 AI requests. ..."; the plan check and draft check return their rule issues with `aiChecked` false. A crisis chat message is still answered. `PUT` and `GET /api/ai/plan/drafts/{dreamId}` return `aiRequestsUsed` and `aiRequestLimit`. Adding or discarding the draft ends the session.
- **Finished plans (PB-FR-36).** Every AI request counted in a session carries the session's key. `POST /api/ai/plan/drafts/{dreamId}/add` records the session's exact requests and cost. `GET /api/admin/ai/plan-metrics` adds `finishedPlans`, `requestsPerFinishedPlan` and `costPerFinishedPlan` (null when there are none), and each feature adds its own `requestsPerFinishedPlan` and `costPerFinishedPlan`. `PLAN_ASSISTANT_CHAT` is listed with the plan features.
- Reordering, merging and splitting (PB-FR-22) happen in the draft in the browser; no endpoint changes.

### V9: daily use (Phase 6)

All need AI consent and count toward the daily limit. Suggestions only.

| Method | Path | Notes |
| --- | --- | --- |
| POST | `/api/ai/daily/next-step?minutes=30&language=en` | `minutes` 5 to 480. `picks[]` (`itemType` STEP or TASK, `itemId`, `title`, `dueDate`, `reason`, `minutes`), best first, and a `note`. Empty with a note, and no AI call, when nothing is open |
| POST | `/api/ai/daily/weekly-review-draft?language=en` | `summary`, `completedTasks`, `delayedTasks`, `blockedTasks`, `lessonsLearned`, `nextActions` |
| POST | `/api/ai/daily/pitch-feedback` | `{ "audience": "PARTNER" \| "BUYER" \| "SPONSOR", "pitch", "language" }` → `reaction`, `clarity`, `benefit`, `ask` (1 to 5), `improvedPitch`, `tip` |
| POST | `/api/ai/daily/outreach-draft` | `{ "partnerId", "relatedDreamId", "purpose", "request", "language" }` → `subject`, `message`. Uses the partner's name, role and organisation, never email or phone |
| POST | `/api/ai/daily/goals/{goalId}/resource-ideas?language=en` | `resources[]` (`kind`, `name`, `why`, `checkBeforeUse`), without links |

`PUT /api/admin/ai-settings` also takes `aiReminders` (AI encouragement in reminder emails); the response returns it.

### V9: module help (Phase 7)

| Method | Path | Notes |
| --- | --- | --- |
| POST | `/api/ai/modules/{number}/help` | `{ "mode": "START" \| "DRAFT" \| "FEEDBACK", "rows": [...], "language" }`. START → `questions` (up to 3) and `example`; DRAFT → `rows` keyed by the module's fields (forms only; rating fields blank); FEEDBACK → `feedback` and `improvements` (needs typed rows). 400 for a locked module. Nothing is saved |
| POST | `/api/ai/assessment/explain?language=en` | `explanation`, `focusModule`, `focusTitle`, `reason`. 400 before the first assessment |

### V9: coach co-pilot (Phase 8)

Coach only, for participants of the coach's own active cohorts (404 otherwise). Participant summaries and drafts need that participant's AI consent (400 otherwise). These requests do not count toward the coach's daily limit.

| Method | Path | Notes |
| --- | --- | --- |
| GET, POST | `/api/coach/participants/{id}/ai-summary?language=en` | GET: this week's saved summary or 204 (no AI call). POST: make it again. `progress`, `mood` (null unless journals are shared), `risks`, `suggestedAction`, `riskSignals[]`, `accurate` |
| POST | `/api/coach/participants/{id}/feedback-draft` | `{ "targetType": "GENERAL" \| "DREAM" \| "GOAL" \| "STEP", "targetId", "language" }` → `message`. Nothing is sent |
| GET, POST | `/api/coach/cohorts/{cohortId}/ai-summary?language=en` | `overview`, `commonBlockers`, `strugglingModules`, `suggestedActions`; consenting participants only, no names |
| PUT | `/api/coach/ai-summaries/{id}/rating` | `{ "accurate": true, "note" }` (AC-16) |

`GET /api/coach/participants` rows add `riskReasons[]` and `aiConsent`.

### V9: admin AI (Phase 9)

Admin only.

| Method | Path | Notes |
| --- | --- | --- |
| GET, POST | `/api/admin/ai/resources` | Approved resources: `{ "kind", "name", "url", "description", "active" }`; `url` must start with http:// or https:// |
| PUT, DELETE | `/api/admin/ai/resources/{id}` | Update or remove one |
| POST | `/api/admin/ai/modules/{number}/draft` | `{ "instruction" }` → `title`, `summary`, `guidance`, `exerciseInstructions`, `examples`, `quiz[]`. Nothing is saved |
| POST | `/api/admin/ai/translate` | `{ "texts": [...], "targetLanguage": "km" }` (up to 40 texts, 20,000 characters) → `translations[]`, same order, null where a draft was dropped |

`PUT /api/admin/cohorts/{id}` also takes `aiEnabled` (left out keeps it); cohorts return `aiEnabled`. `PUT /api/admin/ai-settings` also takes `coachingGuidelines` (blank removes them). Resource ideas now return `url` for approved resources only.

### V9: plan sharing (v0.1 FR-10)

| Method | Path | Notes |
| --- | --- | --- |
| GET | `/api/plan-shares?dreamId=12` | The signed-in person's share links, newest first; `dreamId` is optional. `token` is always null here |
| POST | `/api/plan-shares` | `{ "dreamId", "partnerId", "message", "expiresInDays": 1-365 }` (default 90). The answer holds `token` once; only its SHA-256 is stored. The link is `/shared/{token}` |
| POST | `/api/plan-shares/{id}/revoke` | Stops the link at once |
| GET | `/api/shared-plans/{token}` | **Public.** One dream's title, description, success definition, target date, progress, goals and steps, the owner's first name and the ask. No journals, tasks, step owners or contact details. A stopped, expired or unknown link, or an archived dream, answers 404 |

## Users (V23, needs "View users")

Account details only; no plan data (D-23-3). A caller without the permission gets 403.

```text
GET /api/admin/users?q=&role=&status=&cohortId=&twoFactor=&sort=&page=0&size=25
GET /api/admin/users/{id}
```

- `q`: part of the name or email, case-insensitive, matched literally.
- `role`: `USER`, `COACH`, `ADMIN`; `status`: `ACTIVE`, `DISABLED`; `twoFactor`: `ON`, `DUE`, `OFF`.
- `sort`: `joined` (newest first, default), `name`, `lastSignIn` (most recent first, never-signed-in last).
- `size`: 10, 25, 50 or 100 (anything else gives 25). An unknown role, status, two-step value or sort is 400.

The list answers `{ items, page, size, totalElements, hasNext, totalAccounts, activeAccounts, blockedAccounts,
countsByRole }`; each item has `id, fullName, email, role, roleName, status, cohortId, cohortName, twoFactor,
twoFactorDueAt, joinedAt, lastSignInAt`. The detail answers `{ account, createdBy, roleChangedAt, roleChangedBy,
blockedAt, blockedBy, recordCounts: { visionAreas, dreams, goals, steps, tasks }, recentEvents }` with the last 20
security events by or about the account.

### Changing a role, blocking and unblocking (needs "Manage users")

```text
PUT  /api/admin/users/{id}/role     { role, reason, cohortChanges?: [{ cohortId, newCoachId | null }] }
POST /api/admin/users/{id}/block    { reason }
POST /api/admin/users/{id}/unblock  { reason }
```

Each needs a reason of 10 to 300 characters and a fresh "Verify it's you" code in `X-Step-Up` (`ADMIN_ROLE_CHANGE` or
`ADMIN_USER_BLOCK`), and answers the account detail as it is afterwards. Refusals: your own account 403; an account or
role with permissions you do not hold 403 (only an Admin acts on Admins or gives the Admin role); the last active admin
409; demoting a coach of active cohorts without a choice for each cohort 409 (the message names them); already blocked
or not blocked 409; 30 changes per person per 10 minutes 429. Blocking ends every session of the account; signing in
with the right password then answers 403 "This account is blocked. Contact your program admin.", and the account's
share links stop opening until it is unblocked.

### Add user, edit, invitation, password reset, delete (phase 5)

```text
POST   /api/admin/users                     { fullName, email, role?, cohortId? }     201, the new account's detail
PATCH  /api/admin/users/{id}                { fullName, cohortId | null }             the account's detail
POST   /api/admin/users/{id}/invitation     (no body)                                 204, a new link replaces the old
POST   /api/admin/users/{id}/password-reset (no body)                                 204, the normal 30-minute reset email
DELETE /api/admin/users/{id}                { confirmEmail, reason }                  204
```

Add user, edit, invitation and reset need *Manage users*; delete needs *Delete users*. The account gets no usable
password and a 7-day single-use invitation by email. `role` defaults to Participant and must be one the caller may
give; a privileged role needs a fresh `ADMIN_ROLE_CHANGE` code. Refusals: an email that already has an account 409
"This email already has an account."; email not set up (outside a local run) 409, nothing created; an archived cohort
400; resending after the person chose a password 409; resetting or resending for a blocked account 409. Delete works
only on a blocked account (409 otherwise), needs `confirmEmail` equal to the account email (400 otherwise), a reason of
10 to 300 characters and a fresh `ADMIN_USER_DELETE` code, and erases the account as self-deletion does. The detail
also answers `invitationPending`, `invitationExpiresAt` and `coachedCohorts` (`[{ id, name }]`: the cohorts that need
a new coach on a role change, or are left without one on delete). Cohorts (`/api/admin/cohorts`) answer
`coachBlocked` when the coach's account is blocked. Google sign-in on a blocked account returns
`#result=error&reason=blocked`.

Public, for the person invited (20 attempts per 15 minutes per network):

```text
POST /api/auth/invitation/check    { token }                            { fullName, email, expiresAt }
POST /api/auth/invitation/accept   { token, password, rememberMe? }     the normal sign-in answer
```

Both answer 410 for a used, replaced or expired link; accepting for a blocked account answers 403. Signing in with
Google on the invited address also completes the invitation.

## Roles and permissions (V23)

```text
GET    /api/admin/permissions                 the catalogue: key, group, label, description, adminOnly, always
GET    /api/admin/roles                       key, name, description, builtIn, editable, renameable, holders, privileged, permissions
POST   /api/admin/roles                       { name, description, permissions: [keys], reason }
PUT    /api/admin/roles/{key}                 { name, description, permissions, reason, cohortChanges? }
DELETE /api/admin/roles/{key}                 { moveTo?, reason }
```

Reading needs *Manage roles* or *View users*; changes need *Manage roles* (the Admin role only), a reason of 10 to 300
characters and a fresh code (`ADMIN_ROLE_EDIT`). *Use own plan* is always added; *Manage users* brings *View users*,
*Delete users* brings *Manage users*, *Reset two-step sign-in* brings *View users*; *Manage roles* cannot be given
(400). Admin and Participant cannot be changed (409); Coach keeps its name (400) but its permissions can change.
Removing *Coach cohorts* while holders coach active cohorts needs `cohortChanges` for each (409 otherwise). A role
with holders is deleted only with `moveTo` (409 otherwise); built-in roles are never deleted (409). A custom role's
key is made from its name (for example `PROGRAM_ASSISTANT`) and never changes; `PUT /api/admin/users/{id}/role`
accepts it.

## Security access (V24)

```text
GET /api/auth/sign-in-methods            public: { password, google } — what the sign-in pages offer
GET /api/admin/security-access           { passwordSignIn, googleSignIn, twoFactorScope, googleAvailable, canChange,
                                           updatedAt, updatedBy, password: { accounts, onlyWay, openSessions }, google: {…} }
PUT /api/admin/security-access           { passwordSignIn, googleSignIn, twoFactorScope?, reason }
```

Reading needs *Manage roles* or *View security log* (`canChange` is true only with *Manage roles*); changing needs
*Manage roles* (the Admin role only), a reason of 10 to 300 characters and a fresh code (`ADMIN_SECURITY_ACCESS`).
Counts leave out Admins. `google` in the public answer is true only when Google is on here and set up on the server.
Refusals, all before the code is checked: nothing changed (400), both methods off (400), password off without Google
set up (409), anyone left with no way to sign in (409, with the count), *Everyone* with two-step sign-in turned off
on the server (409). Switching a method off ends non-Admin sessions opened with it. `twoFactorScope` is `PRIVILEGED`
or `EVERYONE`; *Everyone* gives each account without two-step sign-in a set-up date 14 days ahead, and `PRIVILEGED`
clears the dates of non-privileged accounts.

With a method off, sign-in answers 403 with "Password sign-in is turned off. Sign in with Google.", "Sign-up with a
password is turned off. Sign up with Google." or "Google sign-in is turned off. Sign in with your password.", and a
refused Google sign-in returns to the app with `reason=google-off`. Admins are never refused.


## Streamline review (V27)

A guided review that trims a goal or a step in five stages, in order: question,
delete, simplify, speed up, automate (BRD 27). Every endpoint is scoped to the
signed-in user; another user's review answers **404**.

| Method | Path | Body | Notes |
|---|---|---|---|
| POST | `/api/streamline-reviews` | `{ goalId }` or `{ stepId }` | Starts a review; **400** if one is already open for it, or it has nothing open |
| GET | `/api/streamline-reviews` | | Your reviews, newest first, with steps and tasks before and after |
| GET | `/api/streamline-reviews/candidates` | | Goals and steps worth streamlining (at most 5) |
| GET | `/api/streamline-reviews/{id}` | | The review, its items, stages and summary |
| GET | `/api/streamline-reviews/{id}/summary` | | The summary only |
| PUT | `/api/streamline-reviews/{id}/items/{itemId}` | `{ requirementOwner, requirementSource, partnerId, whyNeeded, verdict, keep, keepReason, speedLevers, automation, delegatePartnerId }` | Each group of fields opens with its stage (1, 2, 4, 5); null leaves a field as it is |
| PUT | `/api/streamline-reviews/{id}/items/bulk` | `{ itemIds, requirementOwner, requirementSource, whyNeeded, verdict }` | Stage 1 answers for several items at once |
| POST | `/api/streamline-reviews/{id}/items/{itemId}/delete` | `{ confirmEmpty }` | Archives at once (a step takes its tasks with it). **400** if it would leave the goal or step with nothing open, unless `confirmEmpty` |
| POST | `/api/streamline-reviews/{id}/items/{itemId}/restore` | | Undo; counts as "brought back" |
| POST | `/api/streamline-reviews/{id}/items/{itemId}/merge` | `{ targetItemId, confirmEmpty }` | Same kind only; a step's open tasks move to the target |
| PUT | `/api/streamline-reviews/{id}/items/{itemId}/simplify` | `{ title, description, owner, estimatedHours, markSimple, note }` | Stage 3 |
| PUT | `/api/streamline-reviews/{id}/target` | `{ targetCycleDays }` | Stage 4 |
| POST | `/api/streamline-reviews/{id}/items/{itemId}/repeat` | `{ cadence }` | Stage 5; tasks only |
| POST | `/api/streamline-reviews/{id}/stages/{n}/complete` | | Current stage only. Stage 1 needs every item answered; stage 2 needs every unclear item decided |
| POST | `/api/streamline-reviews/{id}/stages/{n}/not-needed` | `{ reason }` | Reason of at least 10 characters, kept |
| POST | `/api/streamline-reviews/{id}/abandon` | | Decisions already applied stay |
| POST | `/api/streamline-reviews/{id}/stages/{n}/suggestions?language=en` | | AI suggestions for an open stage. Subject to AI consent, the daily limit (**429**) and the monthly cap (**503**); changes nothing |
| GET | `/api/coach/participants/{id}/streamline-reviews` and `/{reviewId}` | | A coach's read-only view |

Values: `requirementSource` SELF, PARTNER, MENTOR, COACH, ORGANISATION, NOT_SURE ·
`verdict` NEEDED, NOT_SURE, NOT_NEEDED · `decision` KEEP, DELETE, MERGE ·
`speedLevers` REMOVE_WAIT, UNBLOCK_FIRST, PARALLEL, BATCH, SHORTEN_LOOP, CLOSER_DATE ·
`automation` REPEAT, REMIND, DELEGATE, MANUAL · `cadence` DAILY, WEEKLY, MONTHLY.

### Repeating tasks and task history (V27)

| Method | Path | Body | Notes |
|---|---|---|---|
| GET | `/api/task-repeat-rules` | | Your rules |
| POST | `/api/task-repeat-rules` | `{ taskId, cadence, firstDue }` | One active rule per task; a second call changes the cadence |
| PUT | `/api/task-repeat-rules/{id}` | `{ cadence, active }` | |
| DELETE | `/api/task-repeat-rules/{id}` | | Stops the rule; tasks it made stay |
| GET | `/api/tasks/{id}/history` | | Status changes, oldest first |

An hourly job creates a rule's next task once the current one is due, never in
the past and never twice for one date. Task responses gain `startedAt` (the first
move to In progress), `cycleTimeDays` (completed minus started, in days) and
`repeatRuleId`. `GET /api/dashboard` gains `streamline` (open reviews, items cut
and brought back this quarter, median cycle time and an eight-week trend).

## Excel

### Export

```text
POST /excel/export
```

Returns an `.xlsx` workbook.

### Import

```text
POST /excel/import
Content-Type: multipart/form-data
```

Form field:

```text
file
```

The import checks the workbook, saves a snapshot of the person's current data, then creates the valid rows for the
signed-in person and reports created and skipped rows, errors and warnings. `backupFile` is the snapshot's file name
only (for example `vision-mapping-backup-20261122-101010-0a1b2c3d.xlsx`), never a server path; snapshots are deleted
after 30 days (`EXCEL_BACKUP_RETENTION_DAYS`).
