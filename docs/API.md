# API Reference

Base URL:

```text
http://localhost:8080/api
```

Protected endpoints require:

```text
Authorization: Bearer <token>
```

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
