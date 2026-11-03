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
- fontFamily
- backgroundTone
- interfaceStyle
- highContrast
- reduceMotion
- createdAt
- updatedAt

`fontFamily` (`V18`, FR-42) selects the typeface. `SYSTEM` is the platform's own
UI face and loads nothing, so it is both the default and the zero-cost option.

`backgroundTone` (`V17`, FR-40) selects a coordinated set of surfaces — page
canvas, cards, popovers, sidebar. `NEUTRAL` is defined as exactly the values that
shipped before FR-40, so defaulting to it changes nothing for existing users, and
`TINTED` stores no colour of its own: it is mixed from the accent at render time.

`interfaceStyle` (`V20`, FR-48) selects the *shape* of the app rather than its
colour: corner radius, shadow diffusion, the card wash, and which navigation
chrome renders. `CLASSIC` is the Fluent treatment that shipped before FR-48, so
defaulting to it changes nothing for existing users; `MODERN` is the rounded,
soft-shadowed SaaS treatment. Because it decides no hue, it composes with every
accent, tone, and font without adding colour pairs to validate — and unlike the
tone, it is *not* suppressed by high contrast, since a corner radius cannot make
anything harder to read.

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

## V8 Daily Execution

- **VisionStep** adds `owner`. **Dream** adds `commitmentSignedName`,
  `commitmentStartDate`, `commitmentFirstAction`, `committedAt`.
  **Partner** status adds `AGREED`.
- **FearEntry:** `fear`, `worstCase`, `likelihood` (Low, Medium, High), `firstTest`,
  optional `relatedDreamId`, `archived`. The dream link clears if the dream is
  permanently deleted.
- **AttitudeCheckIn:** `checkInDate`, `score` (1 to 5), `note`. One per user per day.
- **Habit:** `name`, `archived`. **HabitCheck:** `habitId`, `checkDate`, one per
  habit per day.

## V8 Assessment and Modules

- **ChainAssessment:** `takenOn` and six scores from 1 to 5. One per user per day.
- **LearningModule:** `number`, `position`, `stage`, `completionRule`, `linkPath`.
  **LearningModuleContent:** text per `language`, including form fields as JSON.
  Both are seeded by migration and read-only for users.
- **ModuleCompletion:** `moduleNumber`, `rowsJson`, `completedAt`. One per user per module.
- **Milestone:** `dreamId`, `title`, `achievedOn`, `note`, `archived`. Removed with its
  dream on permanent delete.

## V9 Cohorts and coaches

**UserRole** adds `COACH`. **Cohort** (`cohorts`, V52): `name`, `joinCode` (8 characters,
unique), `coach` (nullable, set to null if the coach's account is deleted), `startDate`,
`archived`. **AppUser** adds `cohort` (nullable) and `shareJournalsWithCoach` (default
false). **CoachComment** (`coach_comments`): `user` (the participant), `coach`,
`targetType` (`GENERAL`, `DREAM`, `GOAL`, `STEP`), `targetId`, `body`, `readAt`. Comments
belong to the participant, so they are in their export and deleted with their account.

**PlanShare** (`plan_shares`, V53; v0.1 FR-10): `user`, `dream` (deleted with the dream),
`partner` (optional label, set to null if the partner is deleted), `tokenHash` (SHA-256 of
the link token, unique), `message`, `expiresAt`, `revokedAt`, `lastViewedAt`, `viewCount`.
In the owner's export without the hash; deleted with their account.

**ModuleCompletion** adds the coach's pitch rating (V53; v0.1 FR-11): `coachRating` (1 to
5), `coachFeedback`, `coachRatedBy` (set to null if the coach's account is deleted),
`coachRatedAt`. Saving a changed exercise clears all four.

## V8 Interface text (FR-20)

**UiTextOverride** (`ui_text_overrides`, V51): `language` (`en` or `km`), `sourceText`
(the English text the app uses as its key), `text` (the replacement), `createdAt`,
`updatedAt`. One row per language and source text. It belongs to the server, not to a
user, so it is not in a user's data export. Deleting a row restores the default.

## V8 Reminders

**AppUser** adds `remindersEnabled` (default false), `reminderTimeZone` and
`lastReminderOn`. V50 adds `reminderEmail` (nullable): where reminders go, or the login
email when it is null.

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

**AiSettings** (`ai_settings`, V54; AI-FR-24): one row (`id` = 1). `enabled` (default false),
`model` (default `claude-opus-5-5`), `monthlyBudgetUsd` (default 20, D-3), `apiKeyEncrypted`
(AES-256-GCM, key from `SETTINGS_ENCRYPTION_KEY` or `JWT_SECRET`), `apiKeyLast4`,
`apiKeyUpdatedAt`, `apiKeyUpdatedBy` (set to null if that admin's account is deleted),
`lastTestAt`, `lastTestOk`, `lastTestMessage`.

**V55 AI foundation.** **AppUser** adds `aiConsentAt` (null = no consent). **AiConsentEvent**
(`ai_consent_events`): `user`, `action` (`GIVEN`, `WITHDRAWN`). **AiConversation**
(`ai_conversations`): `user`, `title`. **AiMessage** (`ai_messages`): `user`, `conversation`
(deleted with it), `role` (`USER`, `ASSISTANT`), `content`, `safetyFlag` (`CRISIS`), `usageLog`.
**AiUsageLog** (`ai_usage_logs`): `user` (cleared, not deleted, with the account), `feature`,
`model`, `inputTokens`, `outputTokens`, `costUsd`, `outcome` (`OK`, `CUT_SHORT`, `REFUSED`,
`CRISIS_HANDLED`, `ERROR`), `rating` (1 or -1), `ratingComment`; never conversation text.
**AiSafetyAlert** (`ai_safety_alerts`): `user`, `coach` (null means administrators see it),
`alertType`, `acknowledgedAt`, `acknowledgedBy`. **AiSettings** adds `dailyMessageLimit`
(default 20) and `helpLines`.

**V56.** **AiMessage** adds `contextSources`: the comma-separated source keys a reply was
based on (AI-FR-05), never the data itself.

**V57.** **VisionStep** adds `resources` (the resources and people the step needs, v0.1
FR-05) and `overdueReschedules` (times it was given a new date while overdue, AI-FR-11).

**V58.** **AiSettings** adds `aiReminders` (default false): add an AI encouragement that
quotes the participant's passion statement to reminder emails (AI-FR-15).

**V59.** **AiCoachSummary** (`ai_coach_summaries`): `scope` (`PARTICIPANT` or `COHORT`), `coach`,
`participant` (deleted with their account) or `cohort`, `weekStart`, `progressText`, `moodText`,
`risksText`, `actionText` (a cohort summary stores its overview, modules and blockers in the
same columns), and the coach's `accurate`, `ratingNote`, `ratedAt` (AC-16).
