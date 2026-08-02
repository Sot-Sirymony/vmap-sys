# API Reference

Base URL:

```text
http://localhost:8080/api
```

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

Note: existing tokens stay valid after a password change. Sessions are stateless
JWTs, so nothing is revoked server-side until they expire.

### Login

```text
POST /auth/login
```

Body:

```json
{
  "email": "demo@example.com",
  "password": "Password123"
}
```

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
    "highContrast": false,
    "reduceMotion": false
  }
}
```

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
  "highContrast": true,
  "reduceMotion": true
}
```

Allowed values:

| Field | Values |
|---|---|
| `themePreset` | `FLUENT_SYSTEM`, `FLUENT_LIGHT`, `FLUENT_DARK`, `OCEAN`, `FOREST`, `SLATE`, `MIDNIGHT`, `CUSTOM` |
| `themeMode` | `LIGHT`, `DARK`, `SYSTEM` |
| `themeAccent` | `BLUE`, `TEAL`, `PURPLE`, `GREEN`, `ORANGE`, `MAGENTA`, `RED`, `BRASS`, `STEEL`, `PINK` |
| `uiDensity` | `COMFORTABLE`, `COMPACT` |
| `fontSize` | `SMALL`, `MEDIUM`, `LARGE` |
| `fontFamily` | `SYSTEM`, `PUBLIC_SANS`, `INTER`, `DM_SANS`, `NUNITO_SANS` |
| `backgroundTone` | `NEUTRAL`, `WARM`, `COOL`, `SOFT`, `TINTED`, `FLAT` |
| `highContrast`, `reduceMotion` | `true`, `false` |

A value outside these sets is rejected with **400** rather than stored, so the
database can only ever hold something the UI can render (BR-33). `CUSTOM` is
computed from a mode/accent pair that matches no preset — it is a result, not
something a client needs to select.

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
