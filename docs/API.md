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

`rememberMe` (optional, default `true`) keeps the session for 30 days; `false`
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
    "interfaceStyle": "CLASSIC",
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
| `interfaceStyle` | `CLASSIC`, `MODERN` |
| `highContrast`, `reduceMotion` | `true`, `false` |

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
| GET | `/api/plan-status/{dreamId}` | The six plan stages and `planReady` |
| GET, POST | `/api/milestones` | List with `?dreamId=`, or add one |
| DELETE | `/api/milestones/{id}` | Archive |

## Progress and Account (V8)

| Method | Path | Purpose |
|---|---|---|
| GET | `/api/progress-metrics?date=` | BO-01 to BO-06 for this user |
| GET | `/api/dreams/{id}/plan.pdf` | The dream's plan as a PDF |
| GET, PUT | `/api/account/reminders` | Reminder setting: `{ "enabled": true, "timeZone": "Asia/Phnom_Penh", "reminderEmail": "me@example.org" }`. A blank `reminderEmail` uses the login email; the reply's `sendsTo` says where mail goes |
| POST | `/api/account/reminders/test` | Sends a test reminder to `sendsTo` now; 400 when no mail server is set up |
| GET | `/api/account/export` | All of this user's data as JSON. Never includes the password hash |
| POST | `/api/account/delete` | Permanently delete the account: `{ "password": "..." }`. **400** if wrong |

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
| GET, POST | `/api/admin/cohorts` | Admin. List, or create `{ "name", "coachEmail", "startDate" }`; naming a coach gives them the COACH role |
| PUT | `/api/admin/cohorts/{id}` | Admin. Update name, coach and start date |
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
| PUT | `/api/admin/ai-settings/api-key` | `{ "apiKey": "sk-ant-..." }`; stored AES-GCM encrypted; takes priority over `ANTHROPIC_API_KEY` |
| DELETE | `/api/admin/ai-settings/api-key` | Removes the saved key; AI turns off unless `ANTHROPIC_API_KEY` is set |
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

### V11: Build my plan drafts (Phase 2)

One draft per user per dream. Only its owner can read it; another user's dream answers 404. A draft is not plan data and does not appear in lists, dashboards, exports, coach views or share links. It is removed 14 days after its last change, when the plan is added, when discarded, with its dream and with the account.

| Method | Path | Notes |
| --- | --- | --- |
| GET | `/api/ai/plan/drafts/{dreamId}` | `dreamId`, `stage`, `content`, `updatedAt`, `expiresAt`, `aiRequestsUsed`, `aiRequestLimit` (V11 Phase 8); 204 when there is none |
| PUT | `/api/ai/plan/drafts/{dreamId}` | `{ "stage": "CLARIFY\|GOALS\|STEPS\|TASKS\|HELPERS\|REVIEW", "content": { "goals": [...] } }`. A goal or step with `existingId` stands for one already in the plan and only carries new children. Up to 15 goals, 25 steps per goal, 15 tasks per step. Fields may be blank while drafting. Refused for an archived dream |
| DELETE | `/api/ai/plan/drafts/{dreamId}` | Discards the draft. 204 |
| POST | `/api/ai/plan/drafts/{dreamId}/add` | Saves every new goal, step and task in one transaction with the normal create rules (a step needs title, owner and date; a task title, owner and due date) → 201 `goalIds`, `stepIds`, `taskIds`. A problem answers 400 naming the item (for example `Step "Visit banks" needs an owner.`) and nothing is saved; the draft stays. An `existingId` that is not this dream's live goal (or that goal's live step) is refused. On success the draft is removed and outcome counts are recorded for items carrying a `requestId` |
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

Drafts take `helpers` (up to 10: `key`, `role`, `supportType`, `name`, `partnerId`, `partnerName`, `linkKey`, `requestId`, `edited`) and `risks` (up to 10: `key`, `title`, `description`, `obstacleType`, `severity`, `solution`, `linkKey`, `helperKey`, `fromFears`, `requestId`, `edited`). On add, a helper with `partnerId` links that partner to its goal or step only where the partner has no link yet; one without becomes a new partner with status To Contact. Each risk becomes an Open obstacle on its goal or step, with `helperKey`'s partner as the required partner. The add result also returns `partnerIds`, `obstacleIds` (archived by Undo, which takes them too) and `linkedPartnerIds`.

### V11: draft check, fixes, capacity and the plan model (Phase 6)

| Method | Path | Notes |
| --- | --- | --- |
| POST | `/api/ai/plan/dreams/{dreamId}/draft-check` | Checks the saved draft (save it first) → `issues[]` (`kind`, `itemType`, `itemKey`, `parentKey`, `itemTitle`, `problem`, `fix`, `fixAction`, `fixValue`, `fromAi`), `aiChecked`, `requestId`. Rule kinds: `MISSING_OWNER`, `MISSING_DATE`, `PAST_DATE`, `AFTER_GOAL_DATE`, `BEFORE_WAITED_STEP`, `TASK_AFTER_STEP`, `MISSING_RESOURCE`, `COMPLEX_NO_TASKS`, `NO_STEPS`; with AI also `VAGUE_STEP`, `UNREALISTIC_TIMING`, `GAP`. `fixAction`: `SET_OWNER_ME`, `SET_DATE` (to `fixValue`), `GO_TO_STEPS`, `GO_TO_TASKS`, `FIX_WITH_AI`. Rule checks work without AI. Saves nothing |
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
| POST | `/api/ai/plan/dreams/{dreamId}/assistant/proposals?language=en` | Same body → `changes[]`, `note`, `requestId` (feature `PLAN_ASSISTANT`, rated and counted like other plan answers). A change has `kind`: `EDIT` (`itemRef`, `itemTitle`, `field` `TITLE\|SUCCESS\|WHY\|DATE\|OWNER`, `value`), `ADD_STEP` or `ADD_TASK` (`parentRef`, `parentTitle`, `title`, `owner`, `date`, null when it broke a date limit) or `ADD_RISK` (`title`, `obstacleType`, `severity`, `solution`, optional `parentRef`); and `reason`. Up to 5. Changes outside the section, to items already in the plan (Build my plan), or with a date past the parent's are dropped. 400 for a section that only explains (`PEOPLE`, `MILESTONES`, `WHOLE`). Saves nothing |
| POST | `/api/ai/plan/dreams/{dreamId}/assistant/apply` | Plan page only: one change as the participant left it, plus `requestId` and `edited` → 201 `itemType`, `id`. Saved through the normal dream, goal, step and obstacle rules (a new step needs a date). Every ref must be in this dream's plan; others answer 404. `ADD_TASK` answers 400 (tasks are changed in Build my plan) |

In Build my plan, cards are applied to the draft in the browser and counted with `POST /api/ai/plan/requests/{id}/outcome`.

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

Current import behavior validates sheet structure and reports row counts. It does not yet insert rows.
