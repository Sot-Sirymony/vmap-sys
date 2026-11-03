# CLAUDE.md

## Project Name
Vision Mapping Management System

## Claude Code Role
You are Claude Code acting as a senior full-stack engineer, technical architect, and implementation assistant.

Your job is to help build a complete **Vision Mapping Management System** based on the Vision Mapping method inspired by Steven K. Scott’s book *Mentored by a Millionaire* and the attached Excel workbook.

Use the book only as a conceptual reference. Do **not** copy large passages, proprietary text, or copyrighted content. Create original wording, original UX, original forms, and original system design.

---

## Non-Negotiable Tech Stack

Use this stack for this project:

### Backend
- Java 17 or higher
- Spring Boot 3.x
- Maven
- Spring Web
- Spring Data JPA / Hibernate
- Spring Validation
- Spring Security
- JWT authentication
- PostgreSQL as the main database
- H2 only for local testing if useful
- Flyway or Liquibase for database migrations
- Apache POI for Excel import/export
- JUnit 5 and Mockito for tests
- Lombok may be used if it improves readability

### Frontend
- React JS 19.2.7
- React DOM 19.2.7
- Vite
- TypeScript
- React Router
- Axios or Fetch API
- Reusable components
- Responsive UI

### Do Not Use
Do not use these unless the user explicitly changes the decision:
- Next.js
- Prisma
- SQLite as the main database
- MongoDB
- Firebase
- Serverless architecture

---

## Main System Purpose

The system must help users convert vague dreams into a structured execution system:

```text
Vision Area → Dream → Goal → Step → Task → Partner/Resource → Schedule → Progress → Review
```

The system must allow the user to:

1. Define major life or work areas.
2. Add dreams under each area.
3. Convert each dream into specific goals.
4. Convert each goal into specific steps.
5. Convert complex steps into executable tasks.
6. Assign deadlines, priority, owner, status, and progress.
7. Identify required partners, mentors, experts, tools, or resources.
8. Prepare communication messages to request support from partners.
9. Track progress daily and weekly.
10. Review, revise, and update dreams, goals, steps, and tasks over time.
11. Import and export Excel data using the existing workbook structure when possible.

---

## Working Rules for Claude Code

Always follow these rules:

1. Read this `CLAUDE.md` before making decisions.
2. Work phase by phase.
3. Do not build the whole system in one response.
4. Before modifying files, inspect the project and explain the plan.
5. Preserve existing project structure if a project already exists.
6. Do not delete existing files without asking.
7. Do not overwrite the original Excel workbook.
8. Create backups before modifying or exporting workbook data.
9. Keep code clean, simple, and maintainable.
10. Use clear names and simple English.
11. Explain every major file created or changed.
12. After each phase, summarize:
    - what was completed
    - files created or changed
    - how to test
    - what should be done next
13. Run build or compile checks whenever practical.
14. Fix compile errors before moving to the next phase.
15. Stop at the end of each phase and wait for user confirmation.

---

## First Action

Start by inspecting the current project folder.

Check whether the folder contains:
- `backend/`
- `frontend/`
- `pom.xml`
- `package.json`
- `CLAUDE.md`
- `Vision_Mapping_System_Advanced.xlsx`
- `Mentored by a Millionaire Master Strategies of Super Achievers (Steven K. Scott).pdf`

Then report:

1. Current folder structure.
2. Detected technology stack.
3. Whether backend exists.
4. Whether frontend exists.
5. Whether Excel workbook exists.
6. Whether PDF exists.
7. Missing parts.
8. Recommended final structure.
9. Step-by-step implementation plan.

Do not modify files during the first inspection step unless the user confirms.

---

## Copyright and Source Use

If the PDF book exists:
- Use it only as conceptual inspiration.
- Do not reproduce copyrighted text.
- Do not copy large sections.
- Create original summaries, workflows, questions, prompts, and UI content.

If the Excel workbook exists:
- Inspect sheet names and columns.
- Map workbook data into the application data model when possible.
- Do not overwrite the original workbook.
- Create a backup before any modification or export.
- Import/export should preserve a clean workbook structure.

---

## Recommended Project Structure

Use this structure unless an existing structure must be preserved:

```text
vision-mapping-management-system/
  backend/
    pom.xml
    src/
      main/
        java/
          com/
            visionmapping/
              VisionMappingApplication.java
              config/
              controller/
              service/
              repository/
              entity/
              dto/
              mapper/
              exception/
              security/
              util/
              excel/
        resources/
          application.yml
          application-dev.yml
          application-test.yml
          db/
            migration/
      test/
        java/
          com/
            visionmapping/
  frontend/
    package.json
    vite.config.ts
    tsconfig.json
    src/
      api/
      components/
        dashboard/
        vision-map/
        common/
        forms/
        layout/
      pages/
      layouts/
      context/
      hooks/
      types/
      utils/
      styles/
      App.tsx
      main.tsx
  docs/
    METHOD.md
    USER_GUIDE.md
    DATA_MODEL.md
    API.md
  data/
    import/
    export/
    backup/
  README.md
  CLAUDE.md
```

---

## Backend Package Structure

Use this Java package structure:

```text
com.visionmapping
  config
  controller
  service
  repository
  entity
  dto
    request
    response
  mapper
  exception
  security
  util
  excel
```

### Backend Design Rules

- Controllers must only handle HTTP request and response.
- Services must contain business logic.
- Repositories must handle database access.
- DTOs must be used for API input and output.
- Do not expose JPA entities directly in API responses.
- Use validation annotations for request DTOs.
- Use a global exception handler.
- Use clear HTTP status codes.
- Use pagination for list endpoints when practical.
- Use soft delete or archive instead of permanent delete by default.
- Use createdAt and updatedAt fields.
- Use database migrations.

---

## Frontend Structure

Use this frontend structure:

```text
frontend/src/
  api/
    apiClient.ts
    authApi.ts
    visionAreaApi.ts
    dreamApi.ts
    goalApi.ts
    stepApi.ts
    taskApi.ts
    partnerApi.ts
    reviewApi.ts
    excelApi.ts
  components/
    common/
      Button.tsx
      Input.tsx
      Select.tsx
      Textarea.tsx
      Modal.tsx
      Loading.tsx
      ErrorMessage.tsx
      StatusBadge.tsx
      PriorityBadge.tsx
      ProgressBar.tsx
    layout/
      AppLayout.tsx
      Sidebar.tsx
      Header.tsx
    dashboard/
      DashboardCard.tsx
      DashboardSummary.tsx
    vision-map/
      VisionMapTree.tsx
      DreamNode.tsx
      GoalNode.tsx
      StepNode.tsx
      TaskNode.tsx
    forms/
      VisionAreaForm.tsx
      DreamForm.tsx
      GoalForm.tsx
      StepForm.tsx
      TaskForm.tsx
      PartnerForm.tsx
      ReviewForm.tsx
      CommunicationForm.tsx
  pages/
    LoginPage.tsx
    RegisterPage.tsx
    DashboardPage.tsx
    VisionAreasPage.tsx
    DreamsPage.tsx
    DreamDetailPage.tsx
    GoalsPage.tsx
    StepsPage.tsx
    TasksBoardPage.tsx
    PartnersPage.tsx
    CommunicationBuilderPage.tsx
    ReviewsPage.tsx
    ImportExportPage.tsx
  context/
    AuthContext.tsx
  hooks/
  types/
  utils/
  styles/
```

### Frontend Design Rules

- Use TypeScript types for all API responses.
- Keep API base URL in one place.
- Use protected routes for authenticated pages.
- Show loading, success, and error states.
- Use reusable form components.
- Use reusable table/card components.
- Use responsive layout.
- Use simple English.
- Use cards, tables, badges, and progress bars.
- Do not hardcode backend URLs in multiple files.

---

## Core Method Hierarchy

Implement this hierarchy:

### 1. Vision Area
A major life or work category.

Examples:
- Career
- Health
- Family
- Finance
- Education
- Business
- Spiritual
- Relationship
- Research
- Leadership

### 2. Dream
A meaningful desired future outcome.

Rules:
- A dream belongs to one Vision Area.
- A dream must be clear enough to convert into goals.
- If vague, the system should show clarification questions.

### 3. Goal
A specific major result required to achieve a dream.

Rules:
- A goal belongs to one Dream.
- Each dream should have multiple goals.

### 4. Step
A specific action stage required to complete one goal.

Rules:
- A step belongs to one Goal.
- Each goal should have multiple steps.
- If a step is complex, the system should prompt the user to break it into tasks.

### 5. Task
A small executable action required to complete a step.

Rules:
- A task belongs to one Step.
- Each task must have owner, due date, priority, status, and progress.

### 6. Partner / Resource
A person, mentor, expert, advisor, colleague, organization, book, tool, or funding source needed to complete a dream, goal, step, or task.

### 7. Communication
A structured message used to request help, follow up, or coordinate with a partner.

### 8. Progress Review
Daily, weekly, monthly, and quarterly review of progress, blockers, lessons, and next actions.

---

## Core Data Model

Create or update the database with these entities.

### 1. AppUser

Fields:
- id
- fullName
- email
- passwordHash
- role: USER, ADMIN
- status: Active, Disabled
- createdAt
- updatedAt

### 2. VisionArea

Fields:
- id
- code, example `VA-001`
- userId
- name
- description
- priority
- status: Active, Paused, Completed, Archived
- createdAt
- updatedAt

### 3. Dream

Fields:
- id
- code, example `D-001`
- userId
- visionAreaId
- title
- description
- whyImportant
- successDefinition
- dreamType: Short-Term, Long-Term, Lifetime
- priority
- targetDate
- status: Idea, Active, Paused, Completed, Archived
- createdAt
- updatedAt

### 4. Goal

Fields:
- id
- code, example `G-001`
- userId
- dreamId
- title
- description
- successCriteria
- priority
- targetDate
- status: Not Started, In Progress, Waiting, Blocked, Completed, Paused
- progressPercent
- manualProgressOverride
- createdAt
- updatedAt

### 5. VisionStep

Use the Java entity name `VisionStep` to avoid confusion with generic step names.

Fields:
- id
- code, example `S-001`
- userId
- goalId
- title
- description
- sequenceNumber
- isComplex
- priority
- targetDate
- status: Not Started, In Progress, Waiting, Blocked, Completed, Paused
- progressPercent
- manualProgressOverride
- createdAt
- updatedAt

### 6. TaskItem

Use the Java entity name `TaskItem` for clarity.

Fields:
- id
- code, example `T-001`
- userId
- stepId
- title
- description
- owner
- priority: Low, Medium, High, Critical
- startDate
- dueDate
- status: Not Started, In Progress, Waiting, Blocked, Completed, Paused
- progressPercent
- estimatedHours
- actualHours
- blockerReason
- nextAction
- completedAt
- createdAt
- updatedAt

### 7. Partner

Fields:
- id
- code, example `P-001`
- userId
- name
- role
- organization
- email
- phone
- strength
- supportType: Mentor, Expert, Advisor, Colleague, Financial, Technical, Emotional, Other
- relatedVisionAreaId
- relatedDreamId
- relatedGoalId
- relatedStepId
- relatedTaskId
- status: To Contact, Contacted, Active, Waiting, Declined, Completed
- notes
- createdAt
- updatedAt

### 8. CommunicationMessage

Fields:
- id
- userId
- partnerId
- relatedDreamId
- relatedGoalId
- relatedTaskId
- audience
- purpose
- subject
- hook
- problem
- request
- benefitToPartner
- expectedOutcome
- messageBody
- status: Draft, Sent, Followed Up, Replied, Closed
- followUpDate
- createdAt
- updatedAt

### 9. Review

Fields:
- id
- userId
- reviewType: Daily, Weekly, Monthly, Quarterly
- reviewDate
- relatedVisionAreaId
- relatedDreamId
- summary
- completedTasks
- delayedTasks
- blockedTasks
- lessonsLearned
- nextActions
- createdAt
- updatedAt

### 10. Obstacle

Fields:
- id
- userId
- relatedDreamId
- relatedGoalId
- relatedStepId
- relatedTaskId
- title
- description
- obstacleType: Knowledge, Skill, Time, Money, Motivation, Partner, System, Decision, Other
- severity: Low, Medium, High, Critical
- solution
- requiredPartnerId
- status: Open, In Progress, Resolved, Accepted
- createdAt
- updatedAt

### 11. ProgressLog

Fields:
- id
- userId
- relatedTaskId
- progressPercentBefore
- progressPercentAfter
- note
- loggedAt

---

## Entity Relationships

Implement these relationships:

```text
AppUser 1 → many VisionAreas
AppUser 1 → many Dreams
AppUser 1 → many Goals
AppUser 1 → many VisionSteps
AppUser 1 → many TaskItems

VisionArea 1 → many Dreams
Dream 1 → many Goals
Goal 1 → many VisionSteps
VisionStep 1 → many TaskItems

Partner may link to VisionArea, Dream, Goal, VisionStep, or TaskItem.
CommunicationMessage belongs to Partner when partner exists.
Review may link to VisionArea or Dream.
Obstacle may link to Dream, Goal, VisionStep, or TaskItem.
ProgressLog belongs to TaskItem.
```

---

## Main Application Pages

Build these pages.

### 1. Dashboard

Show:
- Total Vision Areas
- Active Dreams
- Active Goals
- Active Tasks
- Completed Tasks
- Overdue Tasks
- Blocked Tasks
- Average Progress
- Tasks Due This Week
- Top 5 Priority Tasks
- Goals by Status
- Dreams by Vision Area

### 2. Vision Areas Page

Features:
- Create Vision Area
- Edit Vision Area
- Archive Vision Area
- Delete only if safe or after confirmation
- Sort by priority
- Show number of dreams under each area

### 3. Dreams Page

Features:
- Create dream under a Vision Area
- Require title, why important, success definition, priority, and target date
- If dream is vague, display coaching questions:
  - What exactly do you want to achieve?
  - Why is this important?
  - What will success look like?
  - When do you want to achieve it?
  - Which area of life or work does this belong to?

### 4. Dream Detail / Vision Map Page

Show tree structure:

```text
Vision Area
  → Dream
    → Goals
      → Steps
        → Tasks
```

Features:
- Add goals under dream
- Add steps under goal
- Add tasks under step
- Show progress bars at dream, goal, step, and task level
- Calculate progress from child task completion where possible
- Allow manual override only if clearly marked

### 5. Goals Page

Features:
- Table of goals
- Filter by Vision Area, Dream, Status, Priority, Target Date
- Show overdue goals
- Allow bulk status update if practical

### 6. Steps Page

Features:
- Table of steps
- Mark step as complex or simple
- If complex, prompt user to break it into tasks

### 7. Tasks Board

Features:
- Kanban columns:
  - Not Started
  - In Progress
  - Waiting
  - Blocked
  - Completed
  - Paused
- Drag and drop if practical
- Filter by owner, priority, due date, dream, and goal
- Highlight overdue tasks
- Highlight blocked tasks

### 8. Partner Management Page

Features:
- Add partner
- Link partner to Dream, Goal, Step, or Task
- Record support type
- Record contact status
- Show blocked tasks that need partners
- Suggest partner type based on obstacle:
  - Knowledge gap → Mentor or expert
  - Skill gap → Technical expert or trainer
  - Time gap → Delegate or assistant
  - Money gap → Financial partner or sponsor
  - Motivation gap → Accountability partner
  - Decision gap → Advisor

### 9. Communication Builder Page

Create message templates using this structure:
- Audience
- Purpose
- Hook
- Problem
- Request
- Benefit to partner
- Expected outcome
- Full message

Generate a professional message body in this format:

```text
Dear [Name],

I am working on [Dream/Goal]. The main purpose is [Purpose]. I would appreciate your support with [Specific Request]. Your guidance would help [Expected Outcome]. This may also benefit you by [Benefit to Partner].

Would you be available for [specific meeting/request]?

Best regards,
[User Name]
```

### 10. Review Page

Daily Review questions:
- What did I complete today?
- What task moved forward?
- What is delayed?
- What is blocked?
- What is the next action tomorrow?

Weekly Review questions:
- Which goals moved forward?
- Which tasks are overdue?
- Which tasks are blocked?
- Which partner needs follow-up?
- What should be revised?
- What are the top 3 priorities next week?

Monthly Review questions:
- Which dreams are still important?
- Which dreams should be revised upward?
- Which dreams should be paused or removed?
- What new dreams should be added?
- What partners or resources are missing?

### 11. Import / Export Page

Features:
- Import from Excel workbook
- Export current system to Excel workbook
- Do not overwrite the original workbook
- Create backup before import/export

Export should include sheets:
- Dashboard
- Vision Areas
- Dreams
- Goals
- Steps
- Tasks
- Partners
- Communication
- Reviews
- Obstacles
- Progress Logs
- Instructions

Excel export requirements:
- Use professional formatting
- Use dropdown validation for Status and Priority columns
- Use formulas for progress where possible
- Use conditional formatting:
  - Overdue = red
  - Completed = green
  - Blocked = orange
  - High/Critical priority = highlighted
- Freeze header rows
- Auto-fit columns
- Keep a clean dashboard

---

## Core Business Rules

Implement these validation rules:

1. A Dream must belong to one Vision Area.
2. A Goal must belong to one Dream.
3. A Step must belong to one Goal.
4. A Task must belong to one Step.
5. A Task must have:
   - title
   - owner
   - due date
   - priority
   - status
6. If a task is Blocked, `blockerReason` is required.
7. If a step is marked complex, it should have at least one task.
8. A goal cannot be marked Completed unless all required steps/tasks are completed or the user confirms manual override.
9. Overdue tasks are tasks where dueDate is before today and status is not Completed.
10. Progress calculation:
    - Task progress is direct.
    - Step progress = average of child task progress.
    - Goal progress = average of child step progress.
    - Dream progress = average of child goal progress.
11. When all child tasks are Completed, automatically suggest marking the parent step or goal completed.
12. Do not delete records permanently by default. Prefer archive or soft delete.
13. User data must be scoped to the authenticated user.
14. One user must not access another user’s data. The one exception (V9, decision D-6 in `docs/VMS_BRD_V9.0.0.md`): a coach may read, never change, the participants of the active cohorts they coach, and their fear and attitude journals only when the participant shares them. The server checks this on every coach request.

---

## AI-Like Coaching Behavior

If the app includes helper text or coaching prompts, use this behavior.

### When the user enters a dream

Ask:
- What exactly do you want to achieve?
- Why does this matter?
- What will success look like?
- What is your target date?
- Which life or work area does this belong to?

Then suggest possible goals and ask the user to approve or edit.

### When the user enters a goal

Ask:
- What steps are required?
- What must happen first?
- What can block this goal?
- Who can help?

Then suggest 3 to 10 steps and ask the user to approve or edit.

### When the user enters a step

Ask:
- Is this step simple or complex?
- Can this be done in one action?
- Does this need multiple tasks?

If complex, suggest tasks and ask the user to approve or edit.

### When the user enters a blocked task

Ask what is missing:
- knowledge
- skill
- time
- money
- decision
- partner
- motivation

Then suggest the right partner or resource type.

### When the user requests partner communication

Generate a respectful, concise message.
Focus on:
- what the partner can help with
- why the work matters
- what specific support is requested
- what value the work provides

Avoid sounding demanding.

---

## Ideal User Flow

Implement this start-to-finish flow:

1. User creates Vision Area.
   - Example: Career Development.

2. User creates Dream.
   - Example: Become a strong AI-assisted public health researcher.

3. System asks clarifying questions.
   - Why is this important?
   - What does success look like?
   - What is the target date?

4. User creates goals.
   - Learn AI research tools.
   - Build literature review workflow.
   - Prepare one concept note.
   - Present the concept note to mentor.

5. User breaks each goal into steps.
   Example for "Prepare one concept note":
   - Select topic.
   - Search literature.
   - Define research gap.
   - Write rationale.
   - Define objectives.
   - Draft methodology.
   - Review with mentor.

6. User breaks complex steps into tasks.
   Example for "Search literature":
   - Search PubMed.
   - Search Google Scholar.
   - Save 15 articles.
   - Summarize key findings.
   - Prepare reference table.

7. User assigns owner, deadline, status, and priority.

8. System calculates progress.

9. System identifies blocked tasks.

10. System recommends partner/resource.

11. User prepares communication message.

12. User performs daily or weekly review.

13. User revises dreams, goals, steps, and tasks over time.

---

## API Requirements

Create REST APIs under `/api`.

### Public APIs
- `GET /api/health`
- `POST /api/auth/register`
- `POST /api/auth/login`

### Protected APIs
- `/api/vision-areas`
- `/api/dreams`
- `/api/goals`
- `/api/steps`
- `/api/tasks`
- `/api/partners`
- `/api/communication-messages`
- `/api/reviews`
- `/api/obstacles`
- `/api/progress-logs`
- `/api/dashboard`
- `/api/excel/import`
- `/api/excel/export`

### Standard CRUD Pattern

Use this pattern where appropriate:

```text
GET    /api/{resource}
POST   /api/{resource}
GET    /api/{resource}/{id}
PUT    /api/{resource}/{id}
PATCH  /api/{resource}/{id}/status
DELETE /api/{resource}/{id}
```

For delete:
- Prefer archive/soft delete.
- Explain clearly if permanent delete is implemented.

---

## Authentication and Authorization

Implement authentication with Spring Security and JWT.

Requirements:
- User registration
- User login
- Password hashing
- JWT token generation
- JWT validation filter
- Protected APIs
- Authenticated user context
- User-scoped queries

Public endpoints:
- `POST /api/auth/register`
- `POST /api/auth/login`
- `GET /api/health`

All Vision Mapping APIs must be protected.

Frontend requirements:
- Login page
- Register page
- Store JWT token for development
- Attach token to API requests
- Protected routes
- Logout

---

## Excel Import / Export Requirements

Use Apache POI in the backend.

### Import

When importing:
1. Accept `.xlsx` files.
2. Validate sheet names and columns.
3. Map rows into the database model.
4. Show import summary:
   - created records
   - skipped records
   - validation errors
5. Do not overwrite original workbook.
6. Save uploaded files or backups only in a safe local folder during development.

### Export

When exporting:
1. Generate `.xlsx` file from current database data.
2. Include these sheets:
   - Dashboard
   - Vision Areas
   - Dreams
   - Goals
   - Steps
   - Tasks
   - Partners
   - Communication
   - Reviews
   - Obstacles
   - Progress Logs
   - Instructions
3. Apply professional formatting.
4. Freeze header rows.
5. Auto-fit columns.
6. Add dropdown validation for status and priority fields.
7. Add conditional formatting:
   - Overdue = red
   - Completed = green
   - Blocked = orange
   - High/Critical priority = highlighted
8. Do not overwrite original workbook.

---

## Testing Requirements

Add tests where practical.

### Backend Tests
Use JUnit 5 and Mockito.

Test:
- progress calculation
- overdue task detection
- blocked task validation
- parent-child relationship rules
- goal completion rule
- user data scoping
- import/export mapping if implemented

### Frontend Tests
Use Vitest and React Testing Library if practical.

Test:
- login form validation
- protected route behavior
- progress bar rendering
- status badge rendering
- task form validation

---

## Documentation Requirements

Create or update:

```text
README.md
docs/METHOD.md
docs/USER_GUIDE.md
docs/DATA_MODEL.md
docs/API.md
```

### README.md must include:
- project overview
- tech stack
- backend setup
- frontend setup
- database setup
- environment variables
- how to run backend
- how to run frontend
- how to run tests
- API summary
- Excel import guide
- Excel export guide
- limitations and future improvements

---

## Implementation Phases

Work in phases. Stop after each phase and wait for user confirmation.

### Phase 1: Project Inspection and Plan
- Inspect files.
- Identify stack.
- Read Excel structure if available.
- Propose implementation plan.
- Do not modify files yet.

### Phase 2: Backend Foundation
- Create or fix Spring Boot 3 backend.
- Add Maven dependencies.
- Configure application profiles.
- Add health check API.
- Add database connection placeholders.
- Run backend compile/startup check.

### Phase 3: Backend Data Model
- Create entities.
- Create repositories.
- Create DTOs.
- Add migrations.
- Add seed data if practical.
- Run backend compile check.

### Phase 4: Core Logic and REST APIs
- Add services.
- Add progress calculation.
- Add status rules.
- Add overdue detection.
- Add blocked task rules.
- Add CRUD REST APIs.
- Add global exception handling.
- Run backend compile check.

### Phase 5: Authentication and Security
- Add AppUser.
- Add register/login APIs.
- Add password hashing.
- Add JWT security.
- Protect APIs.
- Run backend compile check.

### Phase 6: Frontend Foundation
- Create React 19.2.7 + Vite + TypeScript frontend.
- Add routing.
- Add layout.
- Add navigation.
- Add placeholder pages.
- Run frontend build check.

### Phase 7: Frontend and Backend Integration
- Connect login/register.
- Add protected routes.
- Connect CRUD pages.
- Add loading/error/success states.
- Run frontend build check.

### Phase 8: Advanced Features
- Dashboard summary.
- Vision Map tree.
- Tasks board.
- Partner management.
- Obstacle tracking.
- Communication builder.
- Review system.
- Excel import/export.
- Run backend and frontend checks.

### Phase 9: Testing, Cleanup, and Documentation
- Add tests.
- Fix build issues.
- Update README.
- Add user guide.
- Add API documentation.
- Add data model documentation.
- Provide final completed checklist.

---

## Acceptance Criteria

The project is complete when:

1. User can register and log in.
2. User can create Vision Areas.
3. User can create Dreams under Vision Areas.
4. User can convert Dreams into Goals.
5. User can convert Goals into Steps.
6. User can convert complex Steps into Tasks.
7. User can assign task owner, status, priority, due date, and progress.
8. System calculates progress from task level upward.
9. System identifies overdue and blocked tasks.
10. User can add partners and link them to dreams, goals, steps, or tasks.
11. User can generate partner communication messages.
12. User can perform daily, weekly, and monthly reviews.
13. User can view dashboard summary.
14. User can import Excel data.
15. User can export Excel workbook.
16. Backend compile/tests pass.
17. Frontend build/tests pass where practical.
18. README explains how to run and use the system.

---

## Required Output After Each Phase

At the end of each phase, provide:

1. Summary of files created or changed.
2. What was completed.
3. How to run or test the result.
4. Problems found, if any.
5. Recommended next step.
6. Ask for confirmation before moving to the next phase.

---

## Final Required Output

At the end of the full project, provide:

1. Summary of all files created or changed.
2. How to run the backend.
3. How to run the frontend.
4. How to configure PostgreSQL.
5. How to import the Excel file.
6. How to export the Vision Mapping workbook.
7. How to use the system from start to finish.
8. Known limitations.
9. Recommended future improvements.
