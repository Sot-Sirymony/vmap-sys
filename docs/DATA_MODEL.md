# Data Model

## Core Hierarchy

```text
AppUser
  -> VisionArea
    -> Dream
      -> Goal
        -> VisionStep
          -> TaskItem
```

## AppUser

Stores account and authentication ownership, plus the user's appearance
preferences (FR-39.6) so a chosen look follows them across browsers and devices.

Fields:
- id
- fullName
- email
- passwordHash
- role
- status
- themePreset
- themeMode
- themeAccent
- uiDensity
- fontSize
- backgroundTone
- highContrast
- reduceMotion
- createdAt
- updatedAt

`backgroundTone` (`V17`, FR-40) selects a coordinated set of surfaces — page
canvas, cards, popovers, sidebar. `NEUTRAL` is defined as exactly the values that
shipped before FR-40, so defaulting to it changes nothing for existing users, and
`TINTED` stores no colour of its own: it is mixed from the accent at render time.

The seven appearance columns (`V16`) are `NOT NULL` with defaults equal to the
frontend's pre-FR-39 defaults — `FLUENT_SYSTEM` / `SYSTEM` / `BLUE` /
`COMFORTABLE` / `MEDIUM` / `false` / `false`. Existing rows therefore needed no
backfill, and the read path never has to interpret a NULL.

`themePreset` is derivable from (`themeMode`, `themeAccent`): a preset is a
bundle of those two knobs, not a third dimension (FR-39.1). It is stored only to
record which label the user last applied, and the frontend re-derives it on
render so a stale label can never describe a look that is no longer on screen.

They live on `app_users` rather than in a separate `user_preferences` table
because a single row per user holding only appearance data would add an entity,
repository, and join for no present benefit. If preferences later grow beyond
appearance (notifications, locale, default filters), extracting a table is a
straightforward migration.

## VisionArea

Major life or work category.

Fields:
- id
- code
- user
- name
- description
- priority
- status
- createdAt
- updatedAt

## Dream

Future outcome under a Vision Area.

Fields:
- id
- code
- user
- visionArea
- title
- description
- whyImportant
- successDefinition
- dreamType
- priority
- targetDate
- status
- createdAt
- updatedAt

## Goal

Specific result under a Dream.

Fields:
- id
- code
- user
- dream
- title
- description
- successCriteria
- priority
- targetDate
- status
- progressPercent
- manualProgressOverride
- createdAt
- updatedAt

## VisionStep

Ordered action stage under a Goal.

Fields:
- id
- code
- user
- goal
- title
- description
- sequenceNumber
- complex
- priority
- targetDate
- status
- progressPercent
- manualProgressOverride
- createdAt
- updatedAt

## TaskItem

Executable task under a VisionStep.

Fields:
- id
- code
- user
- step
- title
- description
- owner
- priority
- startDate
- dueDate
- status
- progressPercent
- estimatedHours
- actualHours
- blockerReason
- nextAction
- completedAt
- createdAt
- updatedAt

## Partner

Person or resource that supports progress.

Can link to:
- VisionArea
- Dream
- Goal
- VisionStep
- TaskItem

## CommunicationMessage

Structured partner communication draft or sent message.

Can link to:
- Partner
- Dream
- Goal
- TaskItem

## Review

Daily, weekly, monthly, or quarterly review.

Can link to:
- VisionArea
- Dream

## Obstacle

Progress blocker.

Can link to:
- Dream
- Goal
- VisionStep
- TaskItem
- required Partner

## ProgressLog

Task-level progress history.

Belongs to:
- TaskItem

## Business Rules

- A Dream must belong to a Vision Area.
- A Goal must belong to a Dream.
- A Step must belong to a Goal.
- A Task must belong to a Step.
- A blocked task must include `blockerReason`.
- Task progress is direct.
- Step progress is calculated from child tasks unless manually overridden.
- Goal progress is calculated from child steps unless manually overridden.
- Records are scoped to the authenticated user.
- Appearance preferences are read and written only for the authenticated user;
  values must be one of the curated enum options, and an unrecognised value is
  rejected rather than stored (BR-33).
- High contrast may change the lightness of a status or priority colour to meet
  its contrast target, never its hue or meaning — Completed stays green, Blocked
  orange, Critical red (BR-34).
- A background tone must be one of the curated options and must keep body text at
  7:1 and secondary text at 4.5:1 against every surface it paints. High contrast
  overrides the tone and disables its control (BR-35).



https://claude.ai/code/artifact/199cd8cd-8af3-4bd9-b68f-9c2d414def2f?via=auto_preview
