# BRD – One standard for every REST controller

Nov 27, 2026

## Document control

This is draft 33.0.0. It keeps every requirement in BRD 32.0.0 and the earlier BRDs. It adds no new feature for
users. It brings all **70 classes in `com.visionmapping.controller`** (416 endpoints) up to one standard for REST
controllers. The standard covers versioned paths, explicit media types, `ResponseEntity` answers, validation,
pagination, thin controllers, RFC 9457 error answers, method security and OpenAPI documentation. A build test enforces
it, so new code cannot drift from it again.

| Item | Value |
| --- | --- |
| Document | Business Requirements Document (BRD) – REST controller standards addendum |
| Version | 33.0.0 – Draft |
| Parent documents | CLAUDE.md (Backend Design Rules, API Requirements, rule 13 and 14 user scoping), BRD 10 (SEC-01-03 role rules by path, SEC-10-02 no causes in error answers), BRD 13 (EJ-FR-02 plain-JUnit guard rails with ratchet lists), BRD 20 and 21 (step-up header `X-Step-Up`, error `code`, `triesLeft`, `action`), BRD 23 (D-23-1 permissions, D-23-3 no plan data for admins), BRD 14 (Google redirect URI) |
| Business owner | To be confirmed |
| Product owner | To be confirmed |
| Approvers | Sponsor, Product owner, Tech lead |

| Version | Change | Author |
| --- | --- | --- |
| 33.0.0 | First draft, from the product owner's request of 27 Nov 2026 to apply a set of Spring Boot controller best practices to every controller | BA |
| 33.0.0 (phase 1 built) | Phase 1 implemented, 27 Nov 2026 (no migration). `ApiPaths`, `LegacyApiPathFilter` (registered before Spring Security by `ApiVersionConfig`), `ApiResponses.created`, `PagedResponse` and `Pageables.whitelist`, `Problems` and `ProblemWriter` (every error, including the JWT entry point, the access-denied handler and the session check, is now `application/problem+json`), `@EnableMethodSecurity` and `Authorities`, `StepUpContext`, `StepUpContextInterceptor`, `@RequiresStepUp` and `StepUpService.requireFromRequest`, the OpenAPI customizers, `ControllerRulesTest` with full ratchet lists, and `docs/CONTROLLER_AUDIT_V33.md` (416 endpoints, written by `ControllerAuditWriter`). `SecurityConfig`, the two-factor setup check and CORS accept both prefixes. Frontend: errors read `detail` first, then `message`. Clarified during build: (1) The filter forwards an old path **only when a version 1 endpoint matches it**. That lets phases 2 to 4 move controllers one group at a time, and in phase 1 nothing is forwarded. (2) `ErrorResponse` is removed now, not in phase 6, because nothing used it after the switch. (3) The two endpoints that take `Pageable` today are `GET /api/partners` and `GET /api/communication-messages`, and both answer a raw `Page`. The admin users list and the failed request log take `page`/`size` and answer their own page records. All four move to `PagedResponse` in their phase, and the raw pages are on a ratchet list. (4) An `@Autowired` constructor is still constructor injection (it picks one of several constructors), so it is allowed. The three `@Autowired` setters (`DashboardService`, `EntityLookup`, `StreamlineReviewService`, each breaking a dependency cycle) are on a ratchet list for phase 6. (5) `Location` is a path relative to the server (`/api/v1/goals/42`), because behind Render's proxy the app does not reliably know its public host. (6) The `Problem` schema is added by the OpenAPI customizer, since springdoc replaces schemas set on the `OpenAPI` bean. (7) `Deprecation: true` is sent as written in D-33-2. RFC 9745's date form can replace it when the release date is known | Dev |
| 33.0.0 (phase 2 built) | Phase 2 implemented, 27 Nov 2026 (no migration). The 32 plan core, rhythm and journal controllers now answer on `/api/v1`. Each has a class-level `produces` JSON, `@Validated`, `@Tag`, an `@Operation` on every handler, `ResponseEntity` answers, `consumes` on body methods and `@Positive` path ids. A create answers 201 with `Location`, and an answer with no body answers 204. The frontend still calls `/api/...`; those calls are forwarded. Five controllers that served several roots are split into one-root classes, with the same URLs: `ObstacleSupportController` and `BlockedTaskController` (from `ObstacleHelpController`), `TaskHistoryController` (from `TaskRepeatRuleController`), `DueReviewController` (from `ReviewRhythmController`) and `CommunicationSupportController` (from `CommunicationHelpController`). Paged with `PagedResponse` and a sort whitelist: tasks, progress logs, reviews, obstacles (including `placement=UNPLACED`), fear entries, gratitude entries, attitude check-ins, a task's history, partners and communication messages. `has-linked-partner` answers `{ "linked": … }`. Clarified during build: (1) The screens that need a whole list (the task board, timeline, plan builder, Vision Map, journals and seven more) read every page through `fetchAllPages` (pages of 200), so they behave as before. Page controls on these screens are not added in V33. That change to the screens needs its own design, and CSM-05 is measured on the API's answer, not on these screens. (2) The default sort of each paged list keeps today's order: `id` ascending, the fear journal by `createdAt` descending, and check-ins by `checkInDate` descending, 30 to a page, as the journal showed before. A task's history is sorted by `changedAt` ascending, 50 to a page. (3) The partner and communication tables already paged on the server and read only `content` and `totalElements`, so their screens are unchanged. The page number is now `page` (it was `number`). (4) The Excel export still reads whole lists through the services' list methods, which stay. (5) `GapStepResponse` creates are located at the new step (`/api/v1/steps/{id}`). A task split creates several tasks, so it answers 201 with no `Location`. (6) The repeat-rule check moved into `TaskRepeatRuleService.createRule`, and the archive null checks into `ArchiveRequest.reasonOf` and `removesPartner`. (7) `AccessControlSweepTests` calls every version 1 route on its old path, so every ownership check also covers the forwarding | Dev |
| 33.0.0 (phase 3 built) | Phase 3 implemented, 27 Nov 2026 (no migration). The AI, Streamline, report, sharing, Excel and learning module controllers now answer on `/api/v1` to the standard. Three controllers that served several roots are split, with the same URLs: `CoachAiAlertController` (from `AiController`), `ModuleJourneyController` and `AiAssessmentController` (from `AiModuleController`), and `SharedPlanController` (from `PlanShareController`). The Dream Coach stream's `try`/`catch` is in `StreamingResponder`. `ServerSentEvents` is in `com.visionmapping.web`. File downloads declare their type (`FileDownloads`: Excel, slides, PDF), the Excel import declares `multipart/form-data`, and the stream declares `text/event-stream`. Saved reports are paged. Clarified during build: (1) The report rate limits, the live-or-compared choice and the rules-or-AI commentary choice moved to a new `ReportActionsService`. The Excel import limit moved to `ExcelService.importWorkbookLimited`, and the shared plan limit to `PlanShareService.viewFrom`. The limits themselves are unchanged. (2) The saved reports list is filtered in memory (search, status, archived), as before, and then cut into pages with `Pageables.slice`. Its only order is newest change first. The unplaced obstacles list now uses the same helper. (3) The Streamline review list and the AI conversation list are not in §3 and stay unpaged. Both are small per user. (4) Creates that make several records (adding a plan draft or AI suggestions, applying a plan assistant proposal) answer 201 with no `Location`. A copied report and a started Streamline review are located at the new record. (5) The coach AI summaries and the coach crisis alerts already carry `@PreAuthorize` (`COACH_COHORTS`), ahead of phase 4. (6) `ControllerRulesTest` accepts `@Min(1)` or more as a positive path number (the Streamline stage is `@Min(1) @Max(5)`) | Dev |
| 33.0.0 (phase 4 built) | Phase 4 implemented, 27 Nov 2026 (no migration). The account, sign-in, admin and coaching controllers now answer on `/api/v1` to the standard; `/api/health` stays unversioned. Every controller is now under `/api/v1`, and the old paths are forwarded. **Step-up:** the 23 `@RequestHeader` step-up parameters are gone. The 22 handlers concerned carry `@RequiresStepUp` (one with `confirm = true`). Their services call `StepUpService.requireFromRequest` (or `requireForAddressFromRequest` for the reminder address code, which reads `X-Step-Up-Confirm`) instead of taking the header as a parameter. **Method security:** every admin and coach handler carries `@PreAuthorize` with the exact permission its service already checks, for example "View users" to read accounts, "Manage users" to change them and "Delete users" to delete them. **Splits** (same URLs): `TrustedDeviceController`, `SignInMethodsController`, `UiTextAdminController`, `ClientErrorLogAdminController`, `PermissionController`, `AdminUserTwoFactorController` and `CoachCommentController`. **Pages:** the admin user list and the security log keep their counts and alerts, and their page is now a nested `PagedResponse` (`users`, `events`). The failed request log is a `PagedResponse`. Clarified during build: (1) The sign-in rate limits (invitations, registration, Google) moved to `SignInRequests`, and the cohort join limit to `CohortService.joinFrom`. The admin user actions that also return the account's details are in `AdminUserCommands`, the coach's Streamline reads in `CoachStreamlineService`, and the Redis check in `HealthService`. (2) A refusal from `@PreAuthorize` gets the same words as a refusal from the security filter: "You do not have permission to perform this action." For the few people who held another admin permission, this replaces a service-specific message such as "Only an administrator can edit interface text." (3) With class-level `@Validated`, a bad path id (for example `0`) is refused by Spring's validation proxy before any lookup, with 400 "One of the values sent is not allowed.", where it was a 404 before. An invalid body next to such an id still names its field, as before. `ApiVersionFlowTests` covers both. (4) The admin user list keeps its own `page`, `size` and named `sort` (for example `name`), because the sort names are part of the Users page, not entity properties. The failed request log's page filter is renamed `pageKey`, because `page` is now the page number. No screen used it. (5) The access-denied log line names the forwarded `/api/v1` path (BR-33-7). (6) Two controllers keep more than three collaborators, `AiPlanController` and `SavedReportController`, which phase 6 splits. The three `@Autowired` setters also remain | Dev |

---

## Executive summary

The controllers grew over 32 BRDs, and each BRD followed the habits of its time. A review of the code on
27 Nov 2026 (commit `fe8e87a`) found this:

| Practice | Today | Gap |
| --- | --- | --- |
| Versioned paths | Every path is `/api/...`. 16 classes have no class-level `@RequestMapping` and write the full path on each method | No room for a breaking change without breaking the app |
| Plural resource nouns | Mostly followed. Singletons (`/dashboard`, `/account`, `/daily-plan`, `/plan-status`) and state-change verbs (`/archive`, `/restore`, `/apply-batch`) are used on purpose | The rule is not written down |
| Explicit media types | 2 methods declare `produces` (SSE stream, plan PDF). None declare `consumes` | Content negotiation is left to defaults |
| One domain per controller | `AiPlanController` has 8 injected fields, `SavedReportController` 6, and four more have 4 | Some controllers mix concerns |
| `ResponseEntity<T>` | 14 of 70 files use it. 124 `@ResponseStatus` annotations. No create returns a `Location` header | Status codes and headers are set in two different ways |
| DTO isolation | No entity in any signature. All 209 DTOs are records. 9 controllers use enums from `entity.enums`. 2 endpoints return a bare `boolean` | Mostly met; the enum rule and bare values need a decision |
| Validation | 15 of 70 classes have `@Validated`. 5 `@RequestBody` parameters have no `@Valid`. No path id is checked as positive | Bad ids and some bodies reach the services unchecked |
| Pagination | 2 endpoints take `Pageable`, each with its own page DTO (`AdminUserPageResponse`, `ClientErrorLogPageResponse`). 53 endpoints return a whole `List` | Lists that grow without limit (tasks, logs, journals) are sent in full |
| Thin controllers | Mostly thin. 3 files catch exceptions (`AiController`, `HealthController`, `ServerSentEvents`, which is a helper in the controller package) | Streaming and health logic live in the web layer |
| Cross-cutting headers | `@RequestHeader(StepUpService.HEADER)` is repeated 23 times in 9 controllers | The same security plumbing is copied by hand |
| Central error handling | `GlobalExceptionHandler` exists and is good. It answers with our own `ErrorResponse` record | Not the RFC 9457 `ProblemDetail` standard |
| Constructor injection | 69 classes use `@RequiredArgsConstructor`. No `@Autowired` | Met; keep it enforced |
| Method security | 0 `@PreAuthorize`. Admin and coach rules are by path (`SecurityConfig`), and the services check again | No check next to the endpoint itself |
| OpenAPI | springdoc 2.8.9 is installed and off in production. 0 `@Tag` or `@Operation` | Swagger shows bare method names and no error answers |

V33 closes these gaps without changing what a user sees or can do. It does this in six phases, each one small enough
to review, and adds a build test (`ControllerRulesTest`) so the standard keeps holding.

### Expected benefits

| # | Change | Benefit to users | Benefit to the project |
| --- | --- | --- | --- |
| 1 | `/api/v1` paths | None visible; old tabs keep working during the move | Breaking changes can go to `/api/v2` without breaking the app |
| 2 | `ResponseEntity`, `Location`, media types | None visible | One way to set status and headers; fewer 406/415 surprises |
| 3 | Validation everywhere | Clear messages instead of server errors for bad ids | Bad input stops at the edge |
| 4 | Pagination of growing lists | Long lists open faster | Response size and memory stay bounded |
| 5 | `ProblemDetail` errors | Same messages as today | A standard error format that tools and clients understand |
| 6 | Step-up as a cross-cutting concern | None visible | 23 copied header parameters removed |
| 7 | `@PreAuthorize` | None visible | A second lock next to each admin and coach endpoint |
| 8 | OpenAPI annotations | None visible | Accurate Swagger documentation and client generation |
| 9 | `ControllerRulesTest` | None visible | The standard cannot drift again |

---

## Decisions recorded by this BRD

| ID | Decision |
| --- | --- |
| D-33-1 | **API version 1.** Every endpoint moves to `/api/v1/...`. The prefix lives in one constant, `ApiPaths.V1 = "/api/v1"`, and every controller has a class-level `@RequestMapping(value = ApiPaths.V1 + "/<resource>", produces = MediaType.APPLICATION_JSON_VALUE)`. A controller that serves more than one root (for example `CohortController` with `/api/cohort` and `/api/coach/...`) is split (D-33-5) so that each class has one root. `/api/health` stays unversioned, because Render and the ZAP scan call it and it is not part of the app's contract |
| D-33-2 | **Old paths keep working for a while.** A `LegacyApiPathFilter` runs **before** Spring Security. It forwards any `/api/<x>` that is not `/api/v1/...` or `/api/health` to `/api/v1/<x>` and adds the headers `Deprecation: true` and `Sunset: <date>`. Because it runs first, the security rules, the two-factor setup interceptor and the rate limits all see the `/api/v1` path. The filter is kept for **30 days** after the release (Q-33-1), so that open browser tabs with the old bundle and the Google redirect URI keep working. Removing it is its own small release |
| D-33-3 | **Resource names.** Paths name resources with plural nouns (`/goals`, `/fear-entries`). Three kinds of exception are allowed and written down in `docs/API.md`: (1) **singletons**, one per user, which stay singular (`/dashboard`, `/account`, `/daily-plan`, `/plan-status`, `/work-style-profile`, `/preferences/appearance`); (2) **state changes**, which stay as verb sub-paths on the item (`/{id}/archive`, `/{id}/restore`, `/reorder`, `/{id}/apply-batch`, `/batches/{batchId}/undo`, `/draft-all`); (3) **AI actions**, which stay under `/ai/...`. V33 renames no other path, to keep the move safe. A later rename goes to `/api/v2` |
| D-33-4 | **Media types.** Every controller declares `produces = MediaType.APPLICATION_JSON_VALUE` at class level. Every method with a `@RequestBody` declares `consumes = MediaType.APPLICATION_JSON_VALUE`. Endpoints that send or receive something else override this on the method: Excel import (`multipart/form-data`), Excel export (`application/vnd.openxmlformats-officedocument.spreadsheetml.sheet`), PDFs (`application/pdf`), the Dream Coach stream (`text/event-stream`), and any CSV download (`text/csv`). An error from these endpoints is still sent as `application/problem+json` (BR-33-5) |
| D-33-5 | **One domain per controller.** A controller injects **at most three** collaborators and serves one aggregate or concept. Phase 6 splits the six controllers over that limit (`AiPlanController`, `SavedReportController`, `ManagementReportController`, `CohortController`, `AuthController`, `AiController`). URLs stay the same; only the Java classes change. `ServerSentEvents` moves out of the controller package to `com.visionmapping.web` |
| D-33-6 | **`ResponseEntity<T>` everywhere.** Every handler returns `ResponseEntity<T>`. `@ResponseStatus` is no longer used on controller methods (it stays allowed on exception classes). The codes: `200 OK` for reads and updates; `201 Created` with a `Location` header for creates; `202 Accepted` for work that finishes later; `204 No Content` for an answer with no body (archive, restore, reorder, delete). The `Location` header is built by one helper, `ApiResponses.created(id, body)`, which uses `ServletUriComponentsBuilder.fromCurrentRequest()` |
| D-33-7 | **DTOs only.** Request bodies are `*Request` records and answers are `*Response` records (already true). Enums under `entity.enums` **may** appear in request and response records and parameters, because they are plain value types and are already part of the JSON contract. Moving them would change nothing for clients. An answer is always a JSON object or an array of objects, never a bare `true` or a bare string. The two `has-linked-partner` endpoints answer `{ "linked": true }`. A `Map<String, String>` (UI text) counts as an object and stays |
| D-33-8 | **Validation everywhere.** Every controller has `@Validated` at class level. Every `@RequestBody` has `@Valid`. Every id in a path is `@Positive Long`. Text parameters with a fixed set of values (for example `language`) are checked with `@Pattern`. Paging limits are set in configuration (D-33-9). A failed check answers `400` through the global handler, with the field errors |
| D-33-9 | **Pagination of growing lists.** A collection that can grow without limit takes Spring Data's `Pageable` with `@PageableDefault(size = 20, sort = "createdAt", direction = DESC)` and answers with **one** shared record, `PagedResponse<T>` (Appendix B). `spring.data.web.pageable.max-page-size` is **200**. The two page DTOs of today become `PagedResponse`. A raw `Page<…>` is never returned, because its JSON shape is not stable across Spring versions. Collections that are **bounded by design** stay unpaged, but each one is listed with its reason and a server-side maximum in `docs/API.md` and in the `ControllerRulesTest` allow list (§3) |
| D-33-10 | **Sort is whitelisted.** Each paged endpoint names the properties it can sort by. An unknown sort property answers `400`, never `500`, and never reaches the query. The whitelist lives in the service next to the query, not in the controller |
| D-33-11 | **Thin controllers.** A handler does three things only: take and check the input, call **one** service method, and wrap the result in `ResponseEntity`. A controller has no `if`, loop, `try`/`catch`, repository, `SecurityContextHolder` call or mapping code. Small argument plumbing, such as passing `request == null ? null : request.reason()`, moves into the request record or the service. The Dream Coach stream's `try`/`catch` (an error after the stream has started can only be an SSE `error` event) moves to a `StreamingResponder` in `com.visionmapping.web`. The Redis check in `HealthController` moves to a `HealthService` |
| D-33-12 | **Step-up as a cross-cutting concern.** The 23 `@RequestHeader(StepUpService.HEADER)` parameters are removed. A `StepUpContextInterceptor` reads `X-Step-Up` and `X-Step-Up-Confirm` once per request into a request-scoped `StepUpContext`. A method that needs step-up is marked `@RequiresStepUp`, which documents it, adds the header to OpenAPI and lets the build test find it. **The service still decides** (BR-33-6): it calls `stepUpService.require(action, target)`, which reads the context. The interceptor never allows or refuses on its own, because the check depends on the action and its target |
| D-33-13 | **Errors as RFC 9457 `ProblemDetail`.** `GlobalExceptionHandler` answers every error with `ProblemDetail` and the media type `application/problem+json`. The standard fields are `type`, `title`, `status`, `detail` and `instance`. VMS adds the extensions `reference` (SEC-10-02), `timestamp`, `code`, `triesLeft` and `action` (V20), and `errors` (field errors). During version 1, `message` (the same text as `detail`), `error` and `path` are also kept, so that an old bundle still reads errors. The JWT entry point and the access-denied handler answer the same way. `type` is `about:blank` for a plain HTTP error and `urn:vms:problem:<code>` for a VMS code such as `urn:vms:problem:step-up-required` (Q-33-2). The `ErrorResponse` record is removed in phase 6 |
| D-33-14 | **Constructor injection.** `private final` fields with `@RequiredArgsConstructor` (already the case). `@Autowired` on fields or setters is not allowed anywhere in `com.visionmapping`, and the build test checks it |
| D-33-15 | **Method security.** `@EnableMethodSecurity` is turned on. Every handler under `/api/v1/admin/**` and `/api/v1/coach/**`, and every handler that needs a permission, carries `@PreAuthorize("hasAuthority('PERM_<NAME>')")`. The names come from string constants in `Authorities`, which a test keeps equal to `Permission.authority()`. The path rules in `SecurityConfig` stay (SEC-01-03), and so do the checks in the services. This is a third lock, not a replacement. **Ownership is never checked in SpEL.** User scoping stays in the services (CLAUDE.md rule 13 and 14), and the coach read-only rule (D-6) stays in `CoachService`. A user's own archive and delete need no admin role; the blueprint's `hasRole('ADMIN')` on delete does not apply to VMS |
| D-33-16 | **OpenAPI.** Every controller has `@Tag(name, description)` and every handler `@Operation(summary)`. Answers other than `200` are declared with `@ApiResponse` (`201`, `202`, `204`, and any `404`/`409` the handler can give). The common answers `400`, `401`, `403`, `429` and `500`, with the `ProblemDetail` schema, are added to every operation by one `OpenApiCustomizer`, not copied onto each method. A bearer JWT security scheme is declared, and the public endpoints are marked as needing none. Swagger UI stays **off in production** (`SWAGGER_ENABLED=false`, Q-33-3) |
| D-33-17 | **The contract is checked in the build.** The generated `openapi.json` for `/api/v1` is saved as `docs/openapi-v1.json`. A test fails when the generated document differs from the saved one, so a change to the contract is always seen in review |
| D-33-18 | **No change of behaviour.** Apart from what this BRD lists (the path prefix, `201` with `Location` on creates, `ProblemDetail` errors, the two `has-linked-partner` answers and the paged lists in §4), every endpoint keeps its status codes, body fields and rules. Every such change is listed in `docs/API.md` under "Changes in v1" |

---

## 1. Fit analysis

| Best practice | VMS rule after V33 | Where VMS differs, and why |
| --- | --- | --- |
| Version prefix `/api/v1` | D-33-1, D-33-2 | `/api/health` stays unversioned for the platform's health check |
| Plural nouns, verbs only for state changes | D-33-3 | Singletons per user stay singular |
| `produces` / `consumes` | D-33-4 | File and stream endpoints declare their own type |
| Split controllers by sub-resource | D-33-5 | Limit set at three collaborators |
| `ResponseEntity<T>` | D-33-6 | — |
| DTO records only | D-33-7 | Entity enums may be used as value types |
| `@Valid`, `@Validated`, `@Positive` | D-33-8 | — |
| `Pageable` and a page DTO | D-33-9, D-33-10 | Lists bounded by design stay unpaged, with a stated maximum |
| Thin controllers | D-33-11 | — |
| Headers through interceptors | D-33-12 | The interceptor collects, the service decides |
| `@RestControllerAdvice` with `ProblemDetail` | D-33-13 | Old field names kept during v1 |
| Constructor injection | D-33-14 | Already met |
| `@PreAuthorize` | D-33-15 | Permissions, not roles; ownership stays in services |
| springdoc annotations | D-33-16, D-33-17 | Common error answers added once, not per method |

---

## 2. Inventory of controllers

Phase 1 produces the full list in `docs/CONTROLLER_AUDIT_V33.md`: one line per endpoint with its current path, new
path, status codes, whether it is paged, its `@PreAuthorize`, and the phase that moves it. The groups:

| Group | Controllers | Phase |
| --- | --- | --- |
| Plan core | VisionArea, Dream, Goal, GoalSynergyLink, VisionStep, TaskItem, TaskRepeatRule, Milestone, Block, Obstacle, ObstacleHelp, Partner, IdealPartnerProfile, CommunicationMessage, CommunicationHelp, ProgressLog, ProgressMetrics, PlanStatus, Dashboard, Insight | 2 |
| Rhythm and journals | DailyPlan, Review, ReviewRhythm, Habit, GratitudeEntry, FearEntry, AttitudeCheckIn, ChainAssessment, SelfAssessmentItem, StrengthsProfile, WorkStyleProfile, WorkStyleHelp | 2 |
| AI and Streamline | Ai, AiCoach, AiDaily, AiModule, AiPlan, PlanAssistant, PageAssistant, StreamlineReview | 3 |
| Reports, sharing and files | ManagementReport, SavedReport, PublicReport, PlanShare, Excel, LearningModule | 3 |
| Account and sign-in | Auth, GoogleSignIn, TwoFactorSignIn, TwoFactorAccount, Account, StepUp, SecurityAccess, AppearancePreference, UiText, ClientErrorLog, IssueReport, Health | 4 |
| Admin and coaching | AdminUser, AdminStepUp, Role, AiAdmin, AiSettings, CohortAdmin, Cohort, Coach, LearningModuleAdmin, SecurityLog, TwoFactorAdmin | 4 |

---

## 3. Pagination scope

| Kind | Endpoints | Rule |
| --- | --- | --- |
| **Paged** (grow without limit) | `GET /tasks`, `/progress-logs`, `/communication-messages`, `/reviews`, `/obstacles`, `/partners`, `/fear-entries`, `/gratitude-entries`, `/attitude-checkins`, `/issue-reports`, `/reports/saved`, `/admin/users`, `/admin/security-events`, the failed request log, AI usage lists | `Pageable`, `PagedResponse<T>`, default size 20, maximum 200 |
| **Bounded by design** (unpaged, with a maximum) | `/vision-areas` (max 50), `/dreams` and `/goals` used by pickers and the Vision Map (max 500 each), `/steps?goalId=` (one goal's steps), `/milestones?goalId=`, `/habits` (max 100), lookups (`/ui-text`, `/admin/roles`, `/modules`, `/plan-status`), one cohort's participants, one review's items | Listed in the allow list with its reason. Above the maximum the server answers `422` with a clear message, never silently cuts the list |
| **Not a list** | Singletons, the dashboard summary, the Vision Map tree of one dream | No change |

A table page that becomes paged shows page controls and sends filters to the server. A picker that needs every
item of a bounded list keeps calling the unpaged endpoint.

---

## 4. API

| Change | Detail |
| --- | --- |
| Base path | `/api/...` → `/api/v1/...` for every endpoint except `/api/health` (D-33-1). The frontend's single `API_BASE_URL` and Render's `VITE_API_BASE_URL` change to `…/api/v1` |
| Old paths | Forwarded for 30 days with `Deprecation` and `Sunset` headers (D-33-2) |
| Google redirect URI | `…/api/v1/auth/oauth/google/callback` added in the Google Cloud console and in `GOOGLE_REDIRECT_URI` **before** the old path's alias is removed |
| Creates | Every `POST` that creates a resource answers `201` with `Location: /api/v1/<resource>/{id}` |
| Errors | `application/problem+json` `ProblemDetail` (D-33-13). Example: `{ "type": "urn:vms:problem:step-up-required", "title": "Forbidden", "status": 403, "detail": "Enter your code to continue.", "instance": "/api/v1/account/export", "reference": "a1b2c3d4e5f6", "code": "STEP_UP_REQUIRED", "action": "TOTP", "message": "Enter your code to continue." }` |
| Paged lists | The endpoints in §3 accept `page`, `size` and `sort` and answer `PagedResponse<T>`: `{ content, page, size, totalElements, totalPages, first, last, sort: [{ property, direction }] }` |
| `has-linked-partner` | `GET /api/v1/dreams/{id}/has-linked-partner` and `/goals/{id}/has-linked-partner` answer `{ "linked": true }` |
| Step-up | Unchanged for clients: still the `X-Step-Up` and `X-Step-Up-Confirm` headers |
| API description | `docs/openapi-v1.json`, kept in step by a test (D-33-17). Swagger UI at `/swagger-ui.html` locally |

---

## 5. Business rules

| ID | Rule |
| --- | --- |
| BR-33-1 | Nothing a user can do or see changes, except page controls on lists that become paged (D-33-18) |
| BR-33-2 | User scoping and the coach read-only rule stay in the services. `@PreAuthorize` is never the only check (CLAUDE.md rule 14) |
| BR-33-3 | An error answer never shows a cause, class name, SQL or stack trace. It keeps the `reference` that links it to the server log (SEC-10-02) |
| BR-33-4 | A paged endpoint always sorts by a whitelisted property, with `id` as the last tie-breaker, so that pages never repeat or skip rows |
| BR-33-5 | An error from a file or stream endpoint is still `application/problem+json`, whatever the request's `Accept` header says. An error after an SSE stream has started is an SSE `error` event, as today |
| BR-33-6 | Step-up is decided by `StepUpService` in the service layer, with the action and its target. The interceptor only collects the headers |
| BR-33-7 | Requests on old paths pass exactly the same security, two-factor setup and rate-limit checks as `/api/v1` requests, because they are forwarded before any of them run |
| BR-33-8 | A new controller or endpoint that breaks the standard fails the build (`ControllerRulesTest`) |

---

## 6. Pages and components

There is no new page. Changes in the frontend:

- `src/api/apiClient.ts`: the default base URL becomes `http://localhost:8080/api/v1`. No other file holds a backend
  URL (CLAUDE.md frontend rule).
- The error reader reads `detail` first, then `message`, and keeps reading `code`, `triesLeft`, `action` and
  `reference`. The "reference" line on error messages is unchanged.
- The tables in §3 that become paged use the existing pagination component of the admin users page, with page size
  choices 20, 50 and 100. Their filters are sent to the server.
- The test mocks (MSW handlers and fixtures) move to `/api/v1`.
- New text is limited to page controls, if the existing component does not already have it. Any new string goes into
  `docs/km-review-v33.csv`.

---

## Functional requirements

| ID | Requirement | Priority |
| --- | --- | --- |
| CS-FR-01 | `ApiPaths.V1`; every controller has a class-level `@RequestMapping` under `/api/v1`, one root per class; `/api/health` unversioned (D-33-1) | Must |
| CS-FR-02 | `LegacyApiPathFilter` before Spring Security, with `Deprecation` and `Sunset`, removed 30 days after release (D-33-2) | Must |
| CS-FR-03 | `SecurityConfig` matchers, `TwoFactorSetupInterceptor` paths, rate-limit paths, `render.yaml`, CI and the frontend base URL updated to `/api/v1` | Must |
| CS-FR-04 | Naming rule and its exceptions written in `docs/API.md` (D-33-3) | Must |
| CS-FR-05 | Class-level `produces` JSON; `consumes` on every body method; file and stream endpoints override (D-33-4) | Must |
| CS-FR-06 | Controllers with more than three collaborators split; `ServerSentEvents` moved out of the controller package (D-33-5) | Should |
| CS-FR-07 | Every handler returns `ResponseEntity<T>`; no `@ResponseStatus` on controller methods; `201` with `Location` through `ApiResponses.created` (D-33-6) | Must |
| CS-FR-08 | No bare primitive answers; `has-linked-partner` answers an object (D-33-7) | Must |
| CS-FR-09 | `@Validated` on every controller, `@Valid` on every body, `@Positive` on every path id, `@Pattern` on fixed-value text parameters (D-33-8) | Must |
| CS-FR-10 | `PagedResponse<T>` with `from(Page, mapper)`; the endpoints in §3 paged; the two old page DTOs replaced; maximum page size 200 (D-33-9) | Must |
| CS-FR-11 | Sort whitelist per paged endpoint; unknown property answers 400 (D-33-10, BR-33-4) | Must |
| CS-FR-12 | Bounded lists listed with reason and maximum; above the maximum answers 422 (§3) | Must |
| CS-FR-13 | Thin controllers: no branching, catching, repository or security-context use; `StreamingResponder` and `HealthService` (D-33-11) | Must |
| CS-FR-14 | `StepUpContextInterceptor`, `StepUpContext`, `@RequiresStepUp`; the 23 header parameters removed; services read the context (D-33-12) | Must |
| CS-FR-15 | `ProblemDetail` for every error, including 401 and 403 from the security chain, with VMS extensions and the v1 compatibility fields (D-33-13) | Must |
| CS-FR-16 | `@EnableMethodSecurity`; `@PreAuthorize` with `Authorities` constants on admin, coach and permission-guarded handlers (D-33-15) | Must |
| CS-FR-17 | `@Tag`, `@Operation`, `@ApiResponse` on every controller and handler; common answers and the bearer scheme added by an `OpenApiCustomizer` (D-33-16) | Must |
| CS-FR-18 | `docs/openapi-v1.json` saved and checked by a test (D-33-17) | Should |
| CS-FR-19 | `ControllerRulesTest` enforces CS-FR-01, 05, 07, 08, 09, 13, 14, 16 and 17 and D-33-14, with ratchet lists that only shrink and are empty at the end of phase 6 | Must |
| CS-FR-20 | Frontend: base URL, `ProblemDetail` error reader, paged tables, mocks (§6) | Must |
| CS-FR-21 | `docs/API.md` rewritten for `/api/v1`, with "Changes in v1"; `docs/CONTROLLER_AUDIT_V33.md` | Must |

## Non-functional requirements

| ID | Requirement |
| --- | --- |
| CS-NFR-01 | No behaviour change outside D-33-18. Every existing backend and frontend test passes after only path and error-shape updates |
| CS-NFR-02 | `ControllerRulesTest` is plain JUnit with reflection, no new library (as BRD 13 Q1). Ratchet lists name the phase that empties them |
| CS-NFR-03 | A paged list of 200 rows answers within 300 ms (p95) on the 50-step / 500-task test plan of BRD 27 |
| CS-NFR-04 | `AccessControlSweepTests` covers every `/api/v1` endpoint, and a new test proves that an old path gets the same `401`/`403`/`404` as its `/api/v1` path |
| CS-NFR-05 | Tests for `ProblemDetail` cover each exception type in `GlobalExceptionHandler`, the security entry point and the access-denied handler, and check that no cause leaks (BR-33-3) |
| CS-NFR-06 | Tests that each `@PreAuthorize` refuses a user without the permission even when the path rule is removed (the lock works on its own) |
| CS-NFR-07 | `mvn verify` (line ≥ 94%, branch ≥ 75%), the frontend build and tests (`--testTimeout=20000`), and the SonarQube new-code gate pass in every phase |
| CS-NFR-08 | Every phase is released on its own and leaves the app working; no phase depends on a later one |

## Implementation phases

Each phase builds the backend and the frontend together and passes CS-NFR-07.

| Phase | Content |
| --- | --- |
| 1 | Foundation: `ApiPaths`, `LegacyApiPathFilter`, `ApiResponses`, `PagedResponse`, `ProblemDetail` handler (with v1 compatibility fields), `@EnableMethodSecurity` and `Authorities`, `StepUpContext` and its interceptor, `OpenApiCustomizer`, `ControllerRulesTest` with full ratchet lists, `docs/CONTROLLER_AUDIT_V33.md`. Frontend: error reader. Path matchers accept both prefixes |
| 2 | Plan core, rhythm and journals controllers (§2): `/api/v1`, `ResponseEntity`, validation, media types, OpenAPI; `has-linked-partner`; paging of their growing lists with frontend tables |
| 3 | AI, Streamline, reports, sharing and files controllers: the same, plus `StreamingResponder` for SSE and the media types of file endpoints |
| 4 | Account, sign-in, admin and coaching controllers: the same, plus `@RequiresStepUp` replacing the header parameters, `@PreAuthorize` on every admin and coach handler, the admin page DTOs replaced by `PagedResponse` |
| 5 | Switch over: frontend `API_BASE_URL` and Render `VITE_API_BASE_URL` to `/api/v1`, Google redirect URI, `render.yaml`, CI, MSW mocks; `docs/openapi-v1.json` and its test |
| 6 | Split the six large controllers, remove `ErrorResponse`, empty every ratchet list, `docs/API.md`, CHANGELOG, `UAT_V33.md`. 30 days later, a small release removes `LegacyApiPathFilter` and the v1 compatibility fields stay until `/api/v2` |

## Acceptance criteria

| ID | Criterion |
| --- | --- |
| CSC-01 | Every endpoint except `/api/health` answers on `/api/v1/...`; the Swagger list shows no path outside `/api/v1` apart from health |
| CSC-02 | `GET /api/goals` with a valid token returns the same body as `/api/v1/goals`, plus `Deprecation` and `Sunset` headers; without a token both answer `401` |
| CSC-03 | Creating a goal answers `201` with a `Location` header that opens the new goal |
| CSC-04 | `GET /api/v1/goals/0` and `/api/v1/goals/abc` answer `400` as `application/problem+json` with a `reference` |
| CSC-05 | A body with a missing required field answers `400` with an `errors` list naming the field; the page shows the same message as before V33 |
| CSC-06 | `GET /api/v1/tasks?page=1&size=50&sort=dueDate,asc` answers a `PagedResponse`; `size=500` gives at most 200 rows; `sort=passwordHash` answers `400` |
| CSC-07 | An admin endpoint called by a user without the permission answers `403` `ProblemDetail`, and still does when its path rule is removed in a test |
| CSC-08 | Exporting the account without a step-up code answers `403` with `code` `STEP_UP_REQUIRED`; with a valid code it works; no controller has a `@RequestHeader` for step-up |
| CSC-09 | The Dream Coach stream still streams, and an AI failure mid-stream arrives as an SSE `error` event |
| CSC-10 | Excel export, plan PDF and report PDF download as before; a failing export answers `application/problem+json` |
| CSC-11 | A coach can still read only their own cohort's participants; another participant answers `404` |
| CSC-12 | Google sign-in works through `/api/v1/auth/oauth/google/callback` |
| CSC-13 | `ControllerRulesTest` has empty ratchet lists, and a sample controller that breaks any rule fails it |
| CSC-14 | Changing a response field without updating `docs/openapi-v1.json` fails the build |
| CSC-15 | Builds, tests, the coverage check and the SonarQube gate pass |

## Measuring the benefits

| ID | Measure | Target |
| --- | --- | --- |
| CSM-01 | Controllers passing every rule of `ControllerRulesTest` | 70 of 70 (from 0) |
| CSM-02 | `@RequestHeader` step-up parameters | 0 (from 23) |
| CSM-03 | Unbounded list endpoints | 0 (from 53 list endpoints without a stated limit) |
| CSM-04 | Requests on old paths in the 30 days after release, from the server log | Falling to near 0 before the filter is removed |
| CSM-05 | Answer size of the tasks list for a user with 500 tasks | Down by at least 80% |

## Open questions

| ID | Question | Proposed answer |
| --- | --- | --- |
| Q-33-1 | Is 30 days long enough to keep the old paths? | Yes. The frontend ships with the backend, so only open tabs and the Google redirect use them. Check CSM-04 before removing |
| Q-33-2 | Should problem `type` values be web addresses that open a help page? | Not in V33. Use `urn:vms:problem:<code>`, and add help pages later if users need them |
| Q-33-3 | Should Swagger UI be turned on in production? | No. Keep it off (BRD 10); the saved `docs/openapi-v1.json` serves readers and client generation |
| Q-33-4 | Is 20 the right default page size, and 200 the right maximum? | Yes. 20 matches the admin users page; 200 keeps one answer small |
| Q-33-5 | How long are `message`, `error` and `path` kept in error answers? | For all of version 1; drop them in `/api/v2` |
| Q-33-6 | Should the rules be enforced with the ArchUnit library? | No. Follow BRD 13 Q1: plain JUnit with reflection, so there is no new dependency |
| Q-33-7 | Who updates the Google Cloud console redirect URI? | The product owner, before phase 5 is released |

## Sign-off

| Role | Name | Date | Signature |
| --- | --- | --- | --- |
| Sponsor | | | |
| Product owner | | | |
| Tech lead | | | |

---

## Appendix A. The VMS controller blueprint

Every controller follows this shape after V33.

```java
@RestController
@RequestMapping(value = ApiPaths.V1 + "/goals", produces = MediaType.APPLICATION_JSON_VALUE)
@Validated
@RequiredArgsConstructor
@Tag(name = "Goals", description = "Goals under a dream")
public class GoalController {

    private final GoalService service;

    @Operation(summary = "List the user's goals, one page at a time")
    @GetMapping
    public ResponseEntity<PagedResponse<GoalResponse>> list(
            @RequestParam(defaultValue = "false") boolean includeArchived,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return ResponseEntity.ok(service.listGoals(ArchiveFilter.of(includeArchived), pageable));
    }

    @Operation(summary = "Get one goal")
    @ApiResponse(responseCode = "404", description = "No such goal for this user")
    @GetMapping("/{id}")
    public ResponseEntity<GoalResponse> get(@PathVariable @Positive Long id) {
        return ResponseEntity.ok(service.getGoal(id));
    }

    @Operation(summary = "Create a goal")
    @ApiResponse(responseCode = "201", description = "Created")
    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<GoalResponse> create(@Valid @RequestBody GoalRequest request) {
        GoalResponse created = service.createGoal(request);
        return ApiResponses.created(created.id(), created);
    }

    @Operation(summary = "Update a goal")
    @PutMapping(value = "/{id}", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<GoalResponse> update(@PathVariable @Positive Long id,
                                               @Valid @RequestBody GoalRequest request) {
        return ResponseEntity.ok(service.updateGoal(id, request));
    }

    @Operation(summary = "Archive a goal (it can be restored)")
    @ApiResponse(responseCode = "204", description = "Archived")
    @PostMapping(value = "/{id}/archive", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Void> archive(@PathVariable @Positive Long id,
                                        @Valid @RequestBody(required = false) ArchiveRequest request) {
        service.archiveGoal(id, request);
        return ResponseEntity.noContent().build();
    }
}
```

An admin handler adds the permission next to the endpoint, and step-up is declared, not read by hand:

```java
@Operation(summary = "Block a user")
@PostMapping("/{id}/block")
@PreAuthorize("hasAuthority('" + Authorities.MANAGE_USERS + "')")
@RequiresStepUp
public ResponseEntity<AdminUserResponse> block(@PathVariable @Positive Long id) {
    return ResponseEntity.ok(adminUserService.block(id));   // the service calls stepUpService.require(...)
}
```

## Appendix B. `PagedResponse` and the mapper pattern

One record serves every paged endpoint. It is built from a Spring Data `Page` and a mapper method reference, so the
entity never leaves the service and the JSON shape never depends on Spring's `PageImpl`.

```java
package com.visionmapping.dto.response;

/** One page of a list, in a JSON shape VMS owns (D-33-9). */
public record PagedResponse<T>(
        List<T> content,
        int page,
        int size,
        long totalElements,
        int totalPages,
        boolean first,
        boolean last,
        List<SortOrder> sort) {

    public PagedResponse {
        content = List.copyOf(content);   // BRD 13 EJ-FR-21: the record keeps its own copy
        sort = List.copyOf(sort);
    }

    /** Maps each entity with {@code mapper} and keeps the page numbers. */
    public static <E, T> PagedResponse<T> from(Page<E> page, Function<? super E, ? extends T> mapper) {
        List<T> content = page.getContent().stream().<T>map(mapper).toList();
        List<SortOrder> sort = page.getSort().stream()
                .map(order -> new SortOrder(order.getProperty(), order.getDirection().name()))
                .toList();
        return new PagedResponse<>(content, page.getNumber(), page.getSize(), page.getTotalElements(),
                page.getTotalPages(), page.isFirst(), page.isLast(), sort);
    }

    public record SortOrder(String property, String direction) { }
}
```

The service owns the query, the user scoping, the sort whitelist and the mapping:

```java
@Service
@RequiredArgsConstructor
public class TaskItemService {

    private static final Set<String> SORTABLE = Set.of("createdAt", "dueDate", "priority", "status", "code");

    private final TaskItemRepository repository;
    private final TaskItemMapper mapper;
    private final CurrentUser currentUser;

    @Transactional(readOnly = true)
    public PagedResponse<TaskItemResponse> listTasks(TaskFilter filter, Pageable pageable) {
        Pageable checked = Pageables.whitelist(pageable, SORTABLE);       // unknown sort → 400 (D-33-10)
        Page<TaskItem> page = repository.findAll(TaskSpecifications.of(currentUser.id(), filter), checked);
        return PagedResponse.from(page, mapper::toResponse);
    }
}
```

`Pageables.whitelist` throws a `BusinessRuleException` (answered as `400`) for a property outside the set, and adds
`id` as the last sort order (BR-33-4). The mapper is a plain Spring component with one `toResponse(entity)` method
per entity, as the `mapper` package already does.
