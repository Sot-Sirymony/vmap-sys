# Vision Mapping Management System — Business Requirements Document

| | |
|---|---|
| **Document** | VMS_BRD_V1.0.0 |
| **Version** | 1.0.0 |
| **Date** | 2026-07-11 |
| **Status** | Baseline — reflects the system as currently built and deployed |
| **Product** | Vision Mapping Management System (VMS) |
| **Repository** | github.com/Sot-Sirymony/vision-map-management-system |

Each requirement carries a status: **Implemented**, **Partial**, or **Planned**.

---

## 1. Executive Summary

The Vision Mapping Management System helps a person convert vague dreams into a
structured execution system, following the Vision Mapping method (inspired by
Steven K. Scott's *Mentored by a Millionaire*):

```
Vision Area → Dream → Goal → Step → Task → Partner/Resource → Progress → Review
```

A user defines major life or work areas, captures dreams under them, breaks
dreams into goals, goals into steps, and complex steps into small executable
tasks with owners, deadlines, and priorities. The system rolls progress back up
the hierarchy, surfaces overdue and blocked work, recommends the right kind of
partner for each obstacle, helps draft support-request messages, and drives a
daily/weekly/monthly review habit. Data can be exported to a formatted Excel
workbook.

## 2. Business Objectives

1. Turn unstructured ambitions into a reviewable, prioritized execution plan.
2. Make "what needs my attention right now" visible at a glance (overdue,
   blocked, due-soon work leads the dashboard).
3. Keep every level of the plan honest through automatic progress roll-up from
   task level upward.
4. Convert blockers into action by linking them to the type of partner or
   resource that resolves them.
5. Build a sustainable review cadence (daily → weekly → monthly → quarterly).
6. Keep the user's data portable via Excel export.

## 3. Scope

### In scope (this baseline)
- Single-user personal planning (each account sees only its own data).
- Full lifecycle management of Vision Areas, Dreams, Goals, Steps, Tasks,
  Partners, Communication Messages, Reviews, Obstacles, and Progress Logs.
- Dashboard analytics, Kanban task board with list view and search, Vision Map
  tree, coaching prompts, Excel export, Excel import validation.
- JWT-secured REST API; deployment on Render (managed PostgreSQL + Docker
  backend + static frontend).

### Out of scope (this baseline)
- Multi-user collaboration / shared workspaces.
- Full Excel row-to-database import (validation only today — see FR-11.2).
- Document/file uploads and attachments.
- Notifications (email/push), calendar sync.
- Dark mode (deferred pending a scope decision).
- Mobile native apps (the web UI is responsive).

## 4. Users and Roles

| Role | Description | Status |
|---|---|---|
| USER | Owns and manages their own vision map end to end. All features below. | Implemented |
| ADMIN | Reserved in the data model (`role` on AppUser) for future administration. No admin-only screens exist yet. | Planned |

A demo account is seeded for evaluation (`demo.vision.mapping@example.com`).

## 5. The Method (Business Process)

1. Create a **Vision Area** (e.g., Career, Health, Family).
2. Add a **Dream** under it. If the dream is vague, the system shows clarity
   checks (what, why, success definition, target date) before it is saved.
3. Break the dream into **Goals** (specific major results).
4. Break each goal into ordered **Steps**; mark complex steps.
5. Break complex steps into **Tasks** with owner, due date, priority, status,
   and progress. A complex step with no tasks is flagged with a coaching
   prompt until tasks exist.
6. The system rolls progress up: Task → Step → Goal.
7. Overdue and blocked tasks are highlighted everywhere they appear; blocking
   a task requires a reason, and the reason's category drives a partner-type
   suggestion (e.g., knowledge gap → mentor/expert, money gap → sponsor).
8. The user records **Partners**, links them to work items, and drafts
   structured **Communication Messages** (hook, problem, request, benefit,
   expected outcome) with a one-click generated message body.
9. The user logs **Reviews** (daily/weekly/monthly/quarterly) and **Obstacles**
   with severity and proposed solutions.
10. Dreams, goals, steps, and tasks are revised over time; nothing is hard
    deleted — items are archived and disappear from active views.

## 6. Functional Requirements

### FR-1 Authentication & Account — Implemented
- FR-1.1 Register with full name, email, password (hashed with BCrypt).
- FR-1.2 Login returns a JWT bearer token; all business APIs require it.
- FR-1.3 Every query is scoped to the authenticated user; one user can never
  read or modify another user's records.

### FR-2 Vision Areas — Implemented
- FR-2.1 Create/edit/archive vision areas with code (VA-001…), name,
  description, priority, lifecycle status (Active/Paused/Completed/Archived).
- FR-2.2 Archiving a vision area cascades: its dreams, goals, steps, and tasks
  are archived with it.

### FR-3 Dreams — Implemented
- FR-3.1 Create/edit/archive dreams under a vision area with code (D-001…),
  title, why-important, success definition, dream type
  (Short-Term/Long-Term/Lifetime), priority, target date, status.
- FR-3.2 Vague-dream coaching: while the form is incomplete, a checklist panel
  shows which clarity questions are still unanswered.
- FR-3.3 A Dream Detail page renders the full Vision Map tree
  (Dream → Goals → Steps → Tasks) with progress bars at every level and
  quick-add forms at each level.

### FR-4 Goals — Implemented
- FR-4.1 Create/edit/archive goals under a dream with success criteria,
  priority, target date, work status, and progress percent.
- FR-4.2 Filter goals by vision area, dream, status, priority, and
  overdue-only.
- FR-4.3 Bulk status update across selected goals.
- FR-4.4 Overdue goals are highlighted in the table.

### FR-5 Steps — Implemented
- FR-5.1 Create/edit/archive ordered steps under a goal; mark steps complex.
- FR-5.2 A complex step with zero tasks shows a coaching prompt with a direct
  link to create its tasks, and cannot be completed until it has at least one.
- FR-5.3 Step progress is the average of its tasks' progress.

### FR-6 Tasks & Task Board — Implemented
- FR-6.1 Tasks carry owner, start/due dates, priority (Low→Critical), status
  (Not Started / In Progress / Waiting / Blocked / Completed / Paused),
  progress percent, next action, and blocker reason.
- FR-6.2 Kanban board with one column per status, drag-and-drop between
  columns, per-column task counts, and internally scrolling columns.
- FR-6.3 Board/List view toggle: the same filtered task set as a Kanban board
  or a sortable table.
- FR-6.4 Free-text search across title, owner, description, next action, and
  blocker reason, combined with filters for owner, priority, dream, goal
  (dream-scoped), and overdue-only.
- FR-6.5 Blocking a task requires a blocker reason; choosing what is missing
  (knowledge/skill/time/money/decision/partner/motivation) shows the matching
  partner-type suggestion inline.
- FR-6.6 Overdue tasks show a red accent; blocked tasks an orange accent, in
  both board and list views.
- FR-6.7 Completing a task (or logging 100% progress) sets completion time and
  recalculates parent step and goal progress.

### FR-7 Partners — Implemented
- FR-7.1 Create/edit/archive partners with role, organization, contact info,
  support type (Mentor/Expert/Advisor/Colleague/Financial/Technical/
  Emotional/Other), and pipeline status (To Contact → Contacted → Active →
  Waiting → Declined → Completed).
- FR-7.2 Partners may be linked to a vision area, dream, goal, step, or task.
- FR-7.3 Partner list is paginated.

### FR-8 Communication Builder — Implemented
- FR-8.1 Structured messages: audience, purpose, subject, hook, problem,
  request, benefit to partner, expected outcome, follow-up date, and status
  (Draft/Sent/Followed Up/Replied/Closed).
- FR-8.2 "Generate message" composes a respectful, professional message body
  from the structured fields in one click; the user can edit before saving.
- FR-8.3 Messages can reference a partner, dream, goal, and task.

### FR-9 Reviews — Implemented
- FR-9.1 Daily, weekly, monthly, and quarterly reviews with summary, completed
  / delayed / blocked task notes, lessons learned, and next actions.
- FR-9.2 Reviews may reference a vision area and dream.
- FR-9.3 Daily/weekly review activity feeds the dashboard's review-cadence
  heatmap (last 12 weeks).

### FR-10 Obstacles — Implemented
- FR-10.1 Obstacles carry a type (Knowledge/Skill/Time/Money/Motivation/
  Partner/System/Decision/Other), severity (Low→Critical), proposed solution,
  status (Open/In Progress/Resolved/Accepted), and optional links to a dream,
  goal, step, task, and required partner.
- FR-10.2 Each obstacle row shows the suggested partner type for its category.
- FR-10.3 Active obstacles feed the dashboard's "Top obstacles" chart.

### FR-11 Excel Import / Export
- FR-11.1 **Export — Implemented.** One click downloads a dated `.xlsx`
  workbook containing all twelve required sheets (Dashboard, Vision Areas,
  Dreams, Goals, Steps, Tasks, Partners, Communication, Reviews, Obstacles,
  Progress Logs, Instructions) with professional formatting.
- FR-11.2 **Import — Partial.** Uploading a workbook validates the required
  sheet structure and reports row counts and validation errors; it does not
  yet create database records. Full row-to-database import is the top planned
  enhancement.

### FR-12 Dashboard — Implemented
- FR-12.1 KPI tiles in two labeled clusters: **Needs attention** (Overdue,
  Blocked, Due This Week — tinted red/amber when nonzero) ahead of
  **Portfolio overview** (Vision Areas, Active Dreams, Active Goals, Open
  Tasks, Completed Tasks, Average Progress).
- FR-12.2 Priority tasks table (top five open tasks by priority) directly
  under the KPIs.
- FR-12.3 Analytics: 12-week progress trend, goals by status, dreams by vision
  area, tasks by status, tasks by priority, top obstacles, per-area goal
  progress (lowest first), partner pipeline, review-cadence heatmap.
- FR-12.4 The whole dashboard loads from a single `/api/dashboard` request;
  all aggregation happens server-side.
- FR-12.5 First-run experience: a brand-new account sees one guided "Start
  your Vision Map" prompt with a create-vision-area call to action instead of
  empty tiles and charts.

### FR-13 Delete / Archive Lifecycle
- FR-13.1 **Implemented.** No permanent deletes: every entity archives via a
  dedicated `archived` flag and disappears from active lists and dashboards.
- FR-13.2 **Implemented.** Archiving cascades down the hierarchy
  (Vision Area → Dream → Goal → Step → Task).
- FR-13.3 **Planned.** Confirmation dialog before archiving.
- FR-13.4 **Planned.** "Show archived" toggle with restore.

## 7. Business Rules

| # | Rule | Status |
|---|---|---|
| BR-1 | A dream belongs to exactly one vision area; a goal to one dream; a step to one goal; a task to one step. | Implemented |
| BR-2 | A task must have a title, owner, due date, priority, and status. | Implemented |
| BR-3 | A blocked task must have a blocker reason. | Implemented |
| BR-4 | A complex step must have at least one task before it can be completed. | Implemented |
| BR-5 | A task is overdue when its due date is past and its status is not Completed. | Implemented |
| BR-6 | Progress rolls up: step = average of its tasks; goal = average of its steps. Manual override flags exist on goals and steps. | Implemented |
| BR-7 | Completing a task stamps its completion time and forces progress to 100%. | Implemented |
| BR-8 | Records are archived, never hard-deleted; archived records leave all active views and calculations. | Implemented |
| BR-9 | All data is scoped to the authenticated owner. | Implemented |
| BR-10 | Entity codes are generated per user and type (VA-001, D-001, G-001, S-001, T-001, P-001). | Implemented |

## 8. Non-Functional Requirements

| Area | Requirement | Status |
|---|---|---|
| Security | JWT auth, BCrypt password hashing, per-user data isolation, protected APIs (only register/login/health are public). | Implemented |
| Usability | Responsive layout; consistent Fluent Design System (Communication Blue, Segoe UI, 4px radii); create/edit flows in modals; visible keyboard focus rings; WCAG AA contrast audited. | Implemented |
| Performance | Dashboard loads in one request; partner and communication lists paginated; Kanban columns cap height and scroll internally. | Implemented |
| Reliability | Flyway-versioned schema migrations; additive migration policy; soft delete protects data from destructive bugs. | Implemented |
| Deployability | One-file Render blueprint (`render.yaml`): managed PostgreSQL, Dockerized Spring Boot API with `/api/health` checks, static React frontend; all environment differences env-var driven (`prod` Spring profile). | Implemented |
| Testability | 34 backend tests (JUnit/Mockito: progress roll-up, overdue, blocked rule, dashboard aggregation, auth flow), 10 frontend tests (Vitest/RTL). | Implemented |

## 9. Data Requirements (Entity Overview)

AppUser, VisionArea, Dream, Goal, VisionStep, TaskItem, Partner,
CommunicationMessage, Review, Obstacle, ProgressLog — all with created/updated
timestamps, user ownership, and an `archived` flag. Full attribute-level
definitions live in `docs/DATA_MODEL.md`; API surface in `docs/API.md`.

## 10. Acceptance Criteria Status

| Criterion (from project charter) | Status |
|---|---|
| Register and log in | Met |
| Create Vision Areas / Dreams / Goals / Steps / Tasks through the hierarchy | Met |
| Assign task owner, status, priority, due date, progress | Met |
| Progress calculated from task level upward | Met |
| Overdue and blocked tasks identified | Met |
| Partners added and linked to work items | Met |
| Partner communication messages generated | Met |
| Daily / weekly / monthly reviews | Met (quarterly also available) |
| Dashboard summary | Met |
| Excel export | Met |
| Excel import | Partially met — structure validation only |
| Backend compile/tests pass | Met (34/34) |
| Frontend build/tests pass | Met (10/10) |
| README explains setup and use | Met |

## 11. Known Limitations & Planned Enhancements

1. **Excel import does not persist rows** — highest-priority enhancement
   (FR-11.2).
2. **No archive confirmation or restore UI** (FR-13.3 / FR-13.4).
3. **Document uploads** (attaching files to dreams/goals/tasks) — designed in
   an earlier roadmap, not started.
4. **Dark mode** — deferred pending a scope decision.
5. **Admin role** has no functionality behind it yet.
6. Free-tier Render PostgreSQL has no automatic backups and a limited
   lifetime; a paid database plan is required before storing important data.

## 12. Glossary

| Term | Meaning |
|---|---|
| Vision Area | A major life or work category (Career, Health, …). |
| Dream | A meaningful desired future outcome under one vision area. |
| Goal | A specific major result required to achieve a dream. |
| Step | An ordered action stage required to complete a goal. |
| Task | A small executable action under a step, with owner and due date. |
| Complex step | A step flagged as needing multiple tasks before it can complete. |
| Partner | A person, organization, or resource that helps the work move. |
| Obstacle | A named blocker with a type, severity, and proposed solution. |
| Progress log | A dated record of a task's progress change; feeds the trend chart. |
| Archive (soft delete) | Hiding a record from all active views without destroying it. |
