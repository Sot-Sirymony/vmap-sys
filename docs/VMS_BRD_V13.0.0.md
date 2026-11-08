# BRD – Java Code Quality to Effective Java Practice

Nov 8, 2026

## Document control

This is draft 13.0.0. It keeps every requirement in BRD 12.0.0 (Test Coverage to 80%), BRD 11.0.0 (AI Plan Builder Improvements), BRD 10.0.0 (Security Hardening to the OWASP Top 10), BRD 9.0.0 (Dream Conversion Program with Claude AI Integration) and earlier versions. Where this BRD is silent, the earlier BRDs still apply.

V13 adds no new features for participants, coaches or admins. It brings the backend Java code in line with the practices in Joshua Bloch's *Effective Java* (3rd edition), as summarised in `docs/Effective_Java_Book.md`. Product behaviour, the REST API contract, the database content, security, consent and AI rules stay as they are.

| Item | Value |
| --- | --- |
| Document | Business Requirements Document (BRD) – code quality addendum |
| Version | 13.0.0 – Draft |
| Parent documents | BRD 12.0.0 (7 Nov 2026), BRD 11.0.0 (7 Nov 2026) |
| Business owner | To be confirmed |
| Product owner | To be confirmed |
| Approvers | Sponsor, Product owner, Engineering lead, QA lead |

| Version | Change | Author |
| --- | --- | --- |
| 13.0.0 | First draft: code review of `backend/src/main/java` at commit `f7e7666` against *Effective Java*; findings EJ-G-01 to EJ-G-16, requirements EJ-FR-01 to EJ-FR-32, phased plan and acceptance criteria | BA |
| 13.0.0 | Phase 1 done (8 Nov 2026): `-Xlint:all` with warnings as errors (9 warnings found and fixed: 8 missing `serialVersionUID`, 1 deprecated Claude SDK call); guard-rail test `EffectiveJavaRulesTest` (4 hard rules, 2 ratchet rules); measures script `scripts/effective-java-report.mjs`. Baseline counts corrected with the script; `UserCacheEvictor` added to EJ-FR-22 | BA |
| 13.0.0 | Phase 2 done (8 Nov 2026): the 15 public lifecycle setters on the five core entities replaced by named methods; Core Business Rule 6 and the completion rule moved into `TaskItem`; atomic `TaskItem.update(Details)`; `ProgressCalculator` uses `applyRollup`; 19 new entity tests; 610 backend tests pass. EJ-FR-06 done for `TaskItem` only (see Phase 2 results) | BA |
| 13.0.0 | Phase 3 done (8 Nov 2026): typed status requests with lenient `fromLabel` factories and a 400 that lists allowed values; account export as records; `ArchiveFilter`, `ProgressOverride`, `SuggestionScope` and `ScheduleOverrun` replace flags; builders for the 8 wide response records with a completeness guard; all 31 boxed `Boolean` fields confirmed three-way, no migration; 622 backend tests pass. EJ-FR-09 left for Q2 | BA |
| 13.0.0 | Phase 4 done (8 Nov 2026): no public method returns null without `@Nullable` (guard rule); `Optional` for `likeTerm`, `hashEmail`, `coachOf`, `SecretCipher.decrypt`; `@NonNullApi`/`@NonNullFields` on five packages with `@Nullable` on 100+ parameters, fields and returns; argument checks in shared helpers; defensive copies in all 79 records with collections; 625 backend tests pass | BA |
| 13.0.0 | Phase 5 done (8 Nov 2026): injected `Clock` in the last six classes (clock guard list empty); JWT parser uses the same clock; `PdfFonts` no longer throws `Exception`; broad catches documented, two narrowed, all six covered by tests; `PlanLinked` replaces four `areaIdOf` copies; a Java 17 tick-clock division-by-zero found and avoided; 640 backend tests pass | BA |
| 13.0.0 | Phase 6 done (8 Nov 2026): class Javadoc for every public type in the core packages; Core Business Rule numbers on the methods that enforce them; `DashboardService` split (557 → about 300 lines); `ReminderJob` package-private; coding rules in `docs/DATA_MODEL.md`; README section; final measures. Open: SonarQube Cloud run on `main` after merge (EAC-09), entity setter target (Q5), Q2–Q6 | BA |

## Executive summary

The backend is already in good shape. SonarQube Cloud reports zero bugs, zero vulnerabilities and zero code smells, and V12 lifted backend test coverage to 88.1%. A review of all 429 Java files against *Effective Java* confirms that most of the book's core advice is already followed: DTOs are records, dependencies come in through constructors, progress uses `BigDecimal`, statuses are enums stored by name, and dates use `java.time`.

The review found a smaller set of gaps. The most important one is about the method's business rules:

- **The core entities can be put into an invalid state.** All 39 entities with Lombok `@Setter` let any service write `status`, `progressPercent` or `archived` directly. The rule "a Blocked task must have a blocker reason" (CLAUDE.md Core Business Rule 6) lives in `TaskItemService.prepareTask`, not in `TaskItem`. Any new code path that calls `setStatus(BLOCKED)` skips it. There are 222 `entity.setX(...)` calls in the service layer today.

The other gaps make the code harder to change safely:

- 37 methods return `null`, and no class uses `Objects.requireNonNull`.
- Status changes arrive as free text (`String status`) and are parsed by a shared helper.
- 30 public service and controller methods take a `boolean` flag.
- `DreamResponse` has 37 positional fields and `PartnerResponse` has 32. Swapping two `String` arguments still compiles.
- 12 places read the system clock directly, although `TaskItemService` already uses an injected `Clock`. V12 found a timing bug that only showed on Linux; an injected clock makes that class of bug testable.
- The same "find the vision area of a linked record" logic is written four times in `DashboardService`.

V13 closes these gaps in six small phases. Each phase changes structure, not behaviour, and the 89 backend test classes, the 88% coverage floor and the API contract snapshot must hold after every phase. New guard-rail tests stop the gaps from coming back.

## Business objectives

| ID | Objective | Measure |
| --- | --- | --- |
| EBO-01 | Protect the method's business rules in the code itself | Core Business Rules 5, 6, 9, 10 and 12 in `CLAUDE.md` are enforced inside the entity, and no service can bypass them |
| EBO-02 | Make changes safer and faster to review | Fewer ways to misuse an API: no free-text statuses, no positional 30+ field constructors, no hidden `null` returns |
| EBO-03 | Keep the gain | Automated guard-rail tests fail the build if a covered rule is broken again |
| EBO-04 | No risk to participants | Zero change in API responses, database content or user-visible behaviour |

## Scope

### In scope

- Backend Java code in `backend/src/main/java/com/visionmapping`.
- Backend tests needed to prove that behaviour did not change, and new guard-rail tests.
- One Flyway migration, only if EJ-FR-11 converts nullable flags to `NOT NULL DEFAULT false`.
- `README.md` and a new short section in `docs/DATA_MODEL.md` describing the coding rules.

### Out of scope

- Frontend code (the book is about Java).
- New features, new endpoints or changes to JSON field names or shapes.
- Upgrading Java, Spring Boot or other dependencies. Java 17 stays (records, `instanceof` patterns and switch expressions are already available).
- Rewriting the AI services. They are only split where EJ-FR-29 says so.
- Changing the database schema beyond the one optional migration above.

## Baseline: current state (8 Nov 2026, commit `f7e7666`)

### What already follows the book

| Book item | Evidence in the code |
| --- | --- |
| 1, 4 – Static factories; private constructors for utility classes | `ServiceSupport`, `LogMasks`, `ClientIp` and the five `Ai*Responses` holders are `final` with a private constructor |
| 5 – Dependency injection | `@RequiredArgsConstructor` on services; only 3 `@Autowired`, all on constructors or an optional setter |
| 9 – try-with-resources | Used in the Excel and PDF code |
| 17 – Minimise mutability | 129 of 134 DTO files are records |
| 34, 35 – Enums instead of constants; no `ordinal()` | 40 enums; all mapped with `@Enumerated(EnumType.STRING)` |
| 59 – Know the libraries | `java.time` everywhere except the JWT library boundary |
| 60 – No `double` for exact values | Progress is `BigDecimal` with scale 2 and `HALF_UP` in `ProgressCalculator` |
| 77 – Don't ignore exceptions | No empty `catch` blocks; 6 broad catches, all at system boundaries |
| 81 – Concurrency utilities | Rate limiters use `ConcurrentHashMap`, `AtomicInteger` and immutable records |
| 85 – Avoid Java serialization | No class implements `Serializable` |
| 10, 11 – `equals` / `hashCode` | No entity overrides them and no entity is kept in a `Set`, so identity equality is correct |

### Findings (gaps)

| ID | Book items | Finding | Evidence |
| --- | --- | --- | --- |
| EJ-G-01 | 15, 17, 76 | Entities expose public setters for every field; business invariants live in services | 39 entities with `@Setter @Builder @AllArgsConstructor`; 222 `entity.setX(...)` calls in `service/`; blocker rule in `TaskItemService.java:173` |
| EJ-G-02 | 76 | Updates change the entity first and validate after | `TaskItemService.updateTask` sets 15 fields, then `prepareTask` may throw; rollback saves the database, but the managed object is left half-changed |
| EJ-G-03 | 62, 34 | Status changes arrive as free text | `StatusUpdateRequest.status`, `IssueReportStatusUpdateRequest.status` (`String`); `ServiceSupport.parseEnum` |
| EJ-G-04 | 62 | Account data export is built as untyped maps | 9 uses of `Map<String, Object>` in `AccountService.exportData` and `AccountController` |
| EJ-G-05 | 51 | Boolean flag parameters | 30 public methods, e.g. `ServiceSupport.findAllForUser(..., boolean includeArchived)`, `VisionMappingMapper.toResponse(entity, false, null)` |
| EJ-G-06 | 2, 51 | Very wide positional records | 8 response records with more than 20 fields, built by hand in the mapper: `DreamResponse` 37, `ObstacleResponse` 34, `PartnerResponse` 32, `ReviewResponse` 28, `DashboardSummaryResponse` 27, `CommunicationMessageResponse` 24, `GoalResponse` 22, `TaskItemResponse` 22. (4 request records are also wide, but Jackson builds those, so they are out of scope.) |
| EJ-G-07 | 61 | Boxed `Boolean` fields in entities | 31 `private Boolean` fields; some are meant to be "not answered yet", others are never null |
| EJ-G-08 | 54, 55 | Methods return `null` | 37 `return null;` across services, Excel, AI and security code (e.g. `DashboardService` lines 372, 391, 401, 590) |
| EJ-G-09 | 49 | Little early parameter checking outside controllers | 0 `Objects.requireNonNull` in the codebase; no package-level null contract |
| EJ-G-10 | 50, 17 | Records with collection fields keep the caller's collection | 77 records with `List`, `Set` or `Map` components; none copies them |
| EJ-G-11 | 5, 59 | Direct clock reads | 6 files outside the allow-list read the system clock: `JwtService`, `SecurityLogService`, `PasswordResetService`, `PasswordResetRateLimiter`, `DreamCoachService`, `UserCacheEvictor` |
| EJ-G-12 | 71, 73, 75 | Over-broad checked exception; thin messages | `PdfFonts()` declares `throws Exception`; `parseEnum` says "Invalid status" without the allowed values |
| EJ-G-13 | 18, 20, 23 | Same "linked record" logic repeated | `Obstacle`, `Partner`, `CommunicationMessage`, `ProgressLog` share `relatedDream/Goal/Step/Task`; four `areaIdOf(...)` overloads in `DashboardService` |
| EJ-G-14 | 15, 18 | Very large service classes | `AiPlanService` 764 lines, `PlanAssistantService` 695, `DashboardService` 608 |
| EJ-G-15 | 56 | Missing Javadoc on public types | 161 of 429 files have no class-level comment |
| EJ-G-16 | 27 | Compiler warnings not measured | No `-Xlint` setting in `pom.xml`. Measured in Phase 1: 9 warnings (8 serial, 1 deprecation) |

## Target state

| Measure | Baseline | Target |
| --- | --- | --- |
| Public setters for `status`, `progressPercent`, `archived`, `blockerReason`, `completedAt` on the five core entities | All public | None; changed only through named methods |
| `entity.setX(...)` calls in `service/` | 222 | 80 or fewer |
| `return null;` in public methods | Not measured (37 in total) | 0 in public methods; private ones marked `@Nullable` – **met in Phase 4** (6 documented `@Nullable` link lookups) |
| Free-text status fields in request DTOs | 4 | 0 |
| Public service methods with a `boolean` flag | 30 | 10 or fewer (HTTP `@RequestParam` flags at the controller edge may stay) |
| Response records with more than 20 positional fields built by hand | 8 | 0 (built through a builder) |
| Files reading the clock directly, outside the allow-list | 6 | 0 (allow-list: `BaseAuditableEntity` and the three error-body writers) – **met in Phase 5** |
| Compiler warnings (`-Xlint:all`) | 9 | 0 with warnings as errors – **met in Phase 1** |
| Backend coverage | 88.1% | 88% or more after every phase |
| API contract snapshot | Passing | Unchanged |

## Functional requirements

Each requirement names the book item it applies. "Must" is required for acceptance; "should" is done if the phase has room.

### A. Measurement and guard rails

| ID | Requirement |
| --- | --- |
| EJ-FR-01 | Turn on `-Xlint:all` in the Maven compiler plugin and record the warning count. Fix or justify each warning; a kept `@SuppressWarnings` must sit on the smallest scope with a comment (Item 27). Then add `-Werror`. |
| EJ-FR-02 | Add guard-rail tests that fail the build when: an entity in the core hierarchy has a public `setStatus`, `setProgressPercent` or `setArchived`; a class uses field injection; a class outside the clock allow-list calls `Instant.now()` or `LocalDate.now()` without a `Clock`; a utility class with only static methods is not `final` with a private constructor; an entity uses `@Data` or `@EqualsAndHashCode`. Tool choice: see Open question Q1. |
| EJ-FR-03 | Add a script or Maven report that prints the target-state counts above, so each phase can show its progress. |

### B. Encapsulate the core hierarchy (VisionArea, Dream, Goal, VisionStep, TaskItem)

| ID | Requirement |
| --- | --- |
| EJ-FR-04 | Replace the public setters for lifecycle fields with named methods that keep the entity valid, for example `task.markBlocked(reason)`, `task.complete(clock)`, `task.reopen()`, `task.changeProgress(percent)`, `archive()`, `restore()` (Items 15, 17). |
| EJ-FR-05 | Move Core Business Rule 6 (Blocked needs a reason) and the "Completed means 100% and sets `completedAt`" rule from `TaskItemService.prepareTask` into `TaskItem`. The error message and HTTP status must not change. |
| EJ-FR-06 | Add one `update(TaskDetails details)` style method per core entity that checks the new values first and only then writes them, so a failed update leaves the object unchanged (Item 76). `TaskDetails` is a record built from the request DTO. |
| EJ-FR-07 | Remove `@AllArgsConstructor` from the core entities. Keep `@Builder` on a private constructor and a `protected` no-argument constructor for JPA. |
| EJ-FR-08 | `ProgressCalculator` must update parents only through the new methods (e.g. `step.applyRollup(average, allComplete)`). Core Business Rules 10 and 11 must keep their current results, proven by the existing tests. |
| EJ-FR-09 | The same pattern should then be applied to `Partner`, `Obstacle`, `CommunicationMessage` and `Review` status changes. |

### C. Types instead of strings and flags

| ID | Requirement |
| --- | --- |
| EJ-FR-10 | Give each status enum a static factory such as `WorkStatus.fromLabel(String)` marked `@JsonCreator`, accepting the same spellings `parseEnum` accepts today ("In Progress", "in-progress", "IN_PROGRESS") (Items 1, 34). Change the status request DTOs to use the enum type. The JSON the frontend sends must not change. |
| EJ-FR-11 | An unknown status must still return HTTP 400 with a message that now lists the allowed values (Item 75). |
| EJ-FR-12 | Replace `Map<String, Object>` in the account data export with records. The exported JSON must stay byte-for-byte the same for the same data; prove it with a snapshot test (Item 62). |
| EJ-FR-13 | Replace `boolean includeArchived` and similar flags that cross layers with small enums, e.g. `ArchiveFilter.ACTIVE_ONLY` / `INCLUDE_ARCHIVED` (Item 51). Controllers may keep their `@RequestParam boolean` and convert it once. |
| EJ-FR-14 | Replace `toResponse(entity, false, null)` style calls with clearly named methods or a small value type (e.g. `ScheduleOverrun.none()`) (Item 51). |
| EJ-FR-15 | Build every response record with more than 20 fields through a builder (Lombok `@Builder` on the record), not a positional constructor (Item 2). Field names and JSON order must not change. |
| EJ-FR-16 | Review the 31 boxed `Boolean` entity fields. Where `null` has no meaning, change to `boolean` with a Flyway migration that sets `NOT NULL DEFAULT false` after filling existing nulls. Where `null` means "not answered yet" (e.g. the Dream decision-gate flags), keep `Boolean` and say so in a one-line comment (Item 61). |

### D. Nulls, inputs and immutability

| ID | Requirement |
| --- | --- |
| EJ-FR-17 | No public method returns `null`. Return `Optional` for a value that may be absent, or an empty collection for "no rows" (Items 54, 55). `Optional` must not be used for fields or parameters; the two existing `Optional` parameters (`CoachCommentService.targetTitle`, `EntityLookup.owned`) are reviewed. |
| EJ-FR-18 | Private helpers that still return `null` are marked `@org.springframework.lang.Nullable`. |
| EJ-FR-19 | Add `package-info.java` with Spring's `@NonNullApi` and `@NonNullFields` to `service`, `service.support`, `mapper`, `util` and `excel`, so "not null" is the default and IDE and Sonar checks can see it (Item 49). |
| EJ-FR-20 | Public static helpers and hand-built value classes (`ServiceSupport`, `ProgressCalculator` inputs, `SecretCipher`, the rate limiters) check arguments at the start with `Objects.requireNonNull` or an `IllegalArgumentException` that names the bad value (Items 49, 75). |
| EJ-FR-21 | Every record with a `List`, `Set` or `Map` component copies it in a compact constructor into an unmodifiable collection (Items 17, 50). A null collection stays null so the JSON does not change (amended in Phase 4). |

### E. Time, exceptions and shared structure

| ID | Requirement |
| --- | --- |
| EJ-FR-22 | Inject the existing `Clock` bean into `JwtService`, `PasswordResetService`, `PasswordResetRateLimiter`, `DreamCoachService`, `SecurityLogService` and `UserCacheEvictor`, and replace direct clock reads (Items 5, 59). Add one test per class that uses a fixed clock. |
| EJ-FR-23 | `java.util.Date` stays only at the JWT library call, converted from `Instant` in one place. |
| EJ-FR-24 | `PdfFonts` must not declare `throws Exception`. Wrap font loading failures in an `IllegalStateException` that keeps the cause and names the font file (Items 71, 73, 75). |
| EJ-FR-25 | Each of the 6 broad `catch` blocks must log the cause, keep a comment saying why the broad catch is safe there, and be covered by a test (Item 77). |
| EJ-FR-26 | Introduce one interface, e.g. `PlanLinked`, implemented by `Obstacle`, `Partner`, `CommunicationMessage` and `ProgressLog`, with a default method that returns the most specific linked item. Replace the four `areaIdOf(...)` overloads with one method (Items 18, 20, 23). No schema change. |

### F. Readability and documentation

| ID | Requirement |
| --- | --- |
| EJ-FR-27 | Add class-level Javadoc to every public type in `entity` (core hierarchy), `service`, `service.support`, `util`, `exception` and `mapper` (Item 56). |
| EJ-FR-28 | Every public method that enforces a `CLAUDE.md` Core Business Rule names the rule number in its Javadoc. |
| EJ-FR-29 | Split `DashboardService` into package-private collaborators (for example counts, priority tasks, by-area grouping) behind the same public methods (Items 15, 18). `AiPlanService` and `PlanAssistantService` should be split the same way if the phase has room. |
| EJ-FR-30 | Keep classes as small in visibility as possible: helpers used inside one package become package-private (Item 15). |
| EJ-FR-31 | Add a "Java coding rules" section to `docs/DATA_MODEL.md` listing the rules from this BRD and the Spring limits below. |
| EJ-FR-32 | Update `README.md` with how to run the guard-rail tests and the counts report. |

## Non-functional requirements

| ID | Requirement |
| --- | --- |
| EJ-NFR-01 | **No behaviour change.** All backend tests pass after every phase without changing their expected results. Tests may be rewritten only to call the new methods. |
| EJ-NFR-02 | **API contract unchanged.** The V12 API contract snapshot passes after every phase. |
| EJ-NFR-03 | **Coverage holds.** Backend coverage stays at 88% or more; the SonarQube quality gate stays green. |
| EJ-NFR-04 | **Spring limits.** Do not make classes `final` when Spring proxies them (`@Transactional`, `@Cacheable`, `@Async`, aspects). Do not make JPA entities or their fields `final`. Keep a no-argument constructor on every entity (`protected` is enough). |
| EJ-NFR-05 | **Entity equality.** Do not add field-based `equals`/`hashCode` to entities. If an entity must go into a `Set`, use an id-based version that is safe before the entity is saved. |
| EJ-NFR-06 | **Stack.** Stay within the `CLAUDE.md` stack. Any new test library needs the Engineering lead's approval (see Q1). |
| EJ-NFR-07 | **Small changes.** One requirement group per pull request, so each review stays readable. |

## Implementation phases

Following `CLAUDE.md`, work stops at the end of each phase for confirmation.

| Phase | Name | Requirements | Main files | Exit check |
| --- | --- | --- | --- | --- |
| 1 | Measure and guard | EJ-FR-01 to 03 | `pom.xml`, new guard-rail tests, counts script | Warning count recorded; guard-rail tests in place (rules for later phases start as "known exceptions" lists that later phases empty) |
| 2 | Encapsulate the core hierarchy | EJ-FR-04 to 08 | `TaskItem`, `VisionStep`, `Goal`, `Dream`, `VisionArea`, their services, `ProgressCalculator` | No public lifecycle setters on core entities; all tests green |
| 3 | Types instead of strings and flags | EJ-FR-09 to 16 | status enums, request DTOs, `AccountService`, wide response records, Flyway migration if needed | No free-text statuses; snapshot tests unchanged |
| 4 | Nulls and immutability | EJ-FR-17 to 21 | `DashboardService`, `ExcelService`, AI and security helpers, `package-info.java` files, list-holding records | 0 `return null` in public methods |
| 5 | Time, exceptions and shared links | EJ-FR-22 to 26 | `JwtService`, password reset classes, `PdfFonts`, linked entities, `DashboardService` | No unapproved clock reads; one `areaIdOf` |
| 6 | Readability, docs and final measure | EJ-FR-27 to 32 | Javadoc, `DashboardService` split, docs | Target-state table met; `-Werror` on; SonarQube green on `main` |

### Implementation status

| Phase | Status | Notes |
| --- | --- | --- |
| 1 | Done (8 Nov 2026) | See below |
| 2 | Done (8 Nov 2026) | EJ-FR-06 partial; see below |
| 3 | Done (8 Nov 2026) | EJ-FR-09 not done (waits on Q2); see below |
| 4 | Done (8 Nov 2026) | See below |
| 5 | Done (8 Nov 2026) | See below |
| 6 | Done (8 Nov 2026) | See below; EAC-09 waits for the SonarQube run on `main` |

**Phase 1 results.**

- EJ-FR-01: `pom.xml` compiles with `-Xlint:all,-processing` and `failOnWarning`. (`processing` only lists annotations that no annotation processor claims, which is normal.) The 9 warnings found were fixed: `@Serial serialVersionUID` added to the 7 exception classes and `AppUserPrincipal`, and `ClaudeClient.structured` now uses `StructuredOutputConfig` instead of the deprecated positional-effort call (same request sent to Claude). No `@SuppressWarnings` was needed. A probe class with a raw type was checked to fail the build.
- EJ-FR-02: `backend/src/test/java/com/visionmapping/architecture/EffectiveJavaRulesTest.java`, plain JUnit with no new library (Q1 answered: plain JUnit for now). Hard rules: no `@Autowired` fields; utility classes are `final` with private constructors; entities do not use Lombok `@Data`/`@EqualsAndHashCode`/`@Value`; no class implements `Serializable` directly. Ratchet rules: public lifecycle setters on the 5 core entities (15 known, emptied in Phase 2); direct clock reads (6 known files, emptied in Phase 5).
- EJ-FR-03: `node scripts/effective-java-report.mjs [--markdown]` prints the target-state measures from the source tree.
- Found, not in scope: 38 `@Value` annotations, 29 of them on fields (Item 5 prefers constructors). Listed for a later decision (Q4).

**Phase 2 results.**

- EJ-FR-04: `VisionArea`, `Dream`, `Goal`, `VisionStep` and `TaskItem` no longer have public setters for `status`, `progressPercent`, `archived`, `blockerReason` or `completedAt` (Lombok `@Setter(AccessLevel.NONE)` on those fields). They change only through `changeStatus(...)`, `archive()`, `restore()`, `applyRollup(...)` (goal, step), and `update(...)`, `checkNewTask(...)`, `recordProgress(...)` (task). The guard-rail list for this rule is now empty.
- EJ-FR-05: Core Business Rule 6 (Blocked needs a reason) and the "Completed means 100% and keeps its first `completedAt`" rule now live in `TaskItem`. The message and HTTP 400 are unchanged. `TaskItemService.prepareTask` was removed.
- EJ-FR-06: `TaskItem.update(TaskItem.Details, Clock)` checks first, then writes, so a refused update leaves the task unchanged (EAC-02). **Partial:** `Dream`, `Goal`, `VisionStep` and `VisionArea` still write their other fields through setters in the service. Their remaining checks (decision gate, schedule cascade, complex step, goal completion) read other records, so they still run after the writes; the transaction rollback keeps the database correct, but the in-memory object is changed. Making those atomic means validating the proposed values before writing, which is a bigger change, proposed as a follow-up (Q5).
- EJ-FR-07: `@AllArgsConstructor` is now private and the no-argument constructor protected on the five entities. `@Builder` still works.
- EJ-FR-08: `ProgressCalculator` updates steps and goals only through `applyRollup`. The archive and restore logic in `ArchiveCascade` (restored dreams and areas come back as Paused) moved into `Dream.restore()` and `VisionArea.restore()`.
- Tests: `CoreEntityRulesTest` (19 tests, EAC-01 and EAC-02); six existing tests changed only to call the new methods instead of setters. Backend 610 tests pass, coverage checks met, API contract snapshot unchanged.
- Measure: `entity.setX(...)` calls in `service/` went from 222 to 185.
- Noticed, unchanged on purpose (no behaviour change allowed): a progress-log entry below 100% on a Completed task leaves it Completed with less than 100%; a progress-log entry at 100% on an already Completed task moves `completedAt` to now. Raised as Q6.

**Phase 3 results.**

- EJ-FR-09: not done. It is a "should" and waits on Q2.
- EJ-FR-10: the seven status enums (`WorkStatus`, `DreamStatus`, `LifecycleStatus`, `PartnerStatus`, `ObstacleStatus`, `CommunicationStatus`, `IssueReportStatus`) have a `@JsonCreator fromLabel(String)` factory, backed by a new `EnumLabels` helper. It accepts the same spellings as before ("In Progress", "in-progress", "IN_PROGRESS"). `StatusUpdateRequest` is now generic (`StatusUpdateRequest<WorkStatus>` and so on, one per endpoint) and `IssueReportStatusUpdateRequest` uses `IssueReportStatus`. The nine `update…Status` service methods take the enum. `ServiceSupport.parseEnum` stays only for the issue-report list filters, which are query parameters. Side effect, accepted: the full create and update bodies (`TaskItemRequest` and the others) now accept the same friendly spellings for `status`.
- EJ-FR-11: an unknown status gives HTTP 400 with "Invalid status: X. Allowed values: …" (the echoed value is cut to 40 characters). `GlobalExceptionHandler` returns that message when a rule breaks while the body is read; other unreadable bodies still get the generic message. An empty status is reported as "status: must not be null".
- EJ-FR-12: `AccountService.exportData` returns `AccountExportResponse`, a record with nested records for the hand-built rows. `AccountExportShapeTests` was written and passed against the old map-based code first, then passed unchanged after the move: same sections in the same order, same fields and values. Finding: three row types used `Map.of`, whose key order is not fixed, so the old JSON was never byte-for-byte stable. EAC-04 is therefore checked as "same keys and values", not "same bytes".
- EJ-FR-13: `ArchiveFilter` replaces `boolean includeArchived` in 14 list methods and `ServiceSupport.findAllForUser`; `ProgressOverride` replaces `boolean manualOverride` in the goal and step status changes (Core Business Rule 8); `SuggestionScope` replaces `boolean stretch` in the AI dream and goal builders. Controllers keep their query parameters and convert once. Calls now read as what they do: `listTasks(false)` became `listTasks(ArchiveFilter.ACTIVE_ONLY)`. Cache keys change text (`listTasks(ACTIVE_ONLY)`); old Redis entries simply expire.
- EJ-FR-14: `ScheduleOverrun` (a record that only allows "an overrun with a reason" or `NONE`) replaces `toResponse(entity, false, null)` and the boolean-plus-string pair.
- EJ-FR-15: the 8 response records with more than 20 fields (`DreamResponse`, `ObstacleResponse`, `PartnerResponse`, `ReviewResponse`, `DashboardSummaryResponse`, `CommunicationMessageResponse`, `GoalResponse`, `TaskItemResponse`) and the new `AccountExportResponse` are built with Lombok `@Builder`. The JSON is unchanged (API contract snapshot passes). A builder does not fail to compile when a field is forgotten, so a new guard-rail rule checks that every `XResponse.builder()` chain sets every component (checked by removing one field on purpose: the build failed).
- EJ-FR-16: all 31 boxed `Boolean` entity fields are deliberately three-way (null = not answered, not rated or never tested), including the Partner integrity flags, which the frontend shows as unanswered. None were changed and no migration was needed. One-line comments were added where the meaning was not written down (`Partner`, `Obstacle`, `AiCoachSummary.accurate`, `AiSettings.lastTestOk`).
- Kept as free text on purpose: `PlanAssistantApplyRequest.obstacleType` and `.severity`. An unknown AI value falls back to OTHER / MEDIUM by design; a strict enum would refuse it with 400.
- Tests: `StatusLabelFlowTests` (9, EAC-03), `AccountExportShapeTests` (2, EAC-04), one new guard-rail rule; 17 service-test calls changed from strings to enum constants. Backend 622 tests pass, coverage checks met, API contract snapshot unchanged.
- Measures: free-text status fields 4 → 0 (2 intended free-text AI fields remain); public methods with a `boolean` flag 30 → 10 (the script no longer counts record headers).

**Phase 4 results.**

- EJ-FR-17: four public methods returned null and now return `Optional`: `ServiceSupport.likeTerm`, `SecurityEventService.hashEmail`, `AiSafetyAlertService.coachOf` and `SecretCipher.decrypt` (the last was missed by the first count because it returns a variable). Each caller turns the empty value into null only where a query parameter or a nullable column needs it. **Justified exception:** the six `EntityLookup.optionalX(id)` lookups map an optional link id to an optional relation (null in, null out); wrapping them would put `.orElse(null)` at 35 call sites, which Item 55 itself advises against. They are marked `@Nullable` instead. The two `Optional` items from the baseline: `EntityLookup.owned` no longer takes an `Optional` parameter (it takes the repository's finder); `CoachCommentService.targetTitle` returns `Optional`, which is correct use (it was a false count).
- EJ-FR-18: every remaining method that returns null (35, all private or package-private) is marked `@Nullable`. A new guard-rail rule fails the build if a public method returns null without `@Nullable` (checked with a probe method: the build failed).
- EJ-FR-19: `package-info.java` with `@NonNullApi` and `@NonNullFields` in `service`, `service.support`, `mapper`, `util` and `excel`. Before adding them, every parameter or field that the code checks for null, sets to null, or receives a literal null was marked `@Nullable` (about 100), so tools do not report the existing null checks as dead code. `pom.xml` now also turns off the `classfile` lint category: Spring's `@NonNullApi` refers to JSR-305 meta-annotations whose jar is not on the class path, and adding it would be a new dependency.
- EJ-FR-20: argument checks (`Objects.requireNonNull` with the argument's name) at the start of the public methods of `ServiceSupport`, `ProgressCalculator`, `SecretCipher.encrypt` and `PasswordResetRateLimiter.check`; `ScheduleOverrun.because` too. `requireNonNull` uses: 0 → 28. The login limiter's email and client IP are documented as nullable, because they are.
- EJ-FR-21: all 79 records with a `List`, `Set` or `Map` component copy it in a compact constructor through a new `Copies` helper. **Changed from the requirement text:** a null collection stays null (not an empty list), and null elements are allowed, because turning null into `[]` would change the JSON the API sends and would let a missing list pass `@NotNull`. The copies are unmodifiable; no code was found that changes a collection it got from a record.
- Tests: `RecordCopiesTest` checks all 79 records by reflection (caller changes do not leak in, the copy cannot be changed, null stays null) and the helper; one new guard-rail rule; `SecretCipherTest` and `SecurityLogFlowTests` changed to read the `Optional`. Backend 625 tests pass, coverage checks met, API contract snapshot unchanged.
- Small fix included: `likeTerm` lower-cases with `Locale.ROOT`, so a search behaves the same on a server with a Turkish default locale.

**Phase 5 results.**

- EJ-FR-22: `JwtService`, `SecurityLogService`, `PasswordResetService`, `DreamCoachService`, `PasswordResetRateLimiter` and `UserCacheEvictor` take the shared `Clock` bean. The guard-rail list of direct clock reads is now empty, so a new `Instant.now()` outside the four allowed files fails the build. Fixed-clock tests were added for five of the six classes (JWT expiry, reset-token expiry and spent time, rate-limit window, cache generations, security-log search window); `DreamCoachService` only stamps a conversation's "updated" time and stays covered by its flow tests. The `MutableClock` test helper moved to `src/test/java/com/visionmapping/support/` so tests share it.
- **Bug found and avoided:** the app's clock is `Clock.tick(…, 1 µs)`. On Java 17, `millis()` of a tick clock finer than a millisecond divides by zero. The first version of the `UserCacheEvictor` change called `clock.millis()` and broke 10 flow tests; it now uses `clock.instant().toEpochMilli()`, with a comment and a test that uses the app's real clock.
- EJ-FR-23: the JWT parser now reads time from the same clock (`.clock(() -> Date.from(clock.instant()))`), so issue and expiry checks agree. `java.util.Date` appears only in those two JWT library calls.
- EJ-FR-24: `PdfFonts()` no longer declares `throws Exception`. Each font loads through `PdfFonts.font(…)`, which wraps a failure in `IllegalStateException("Cannot load the PDF font <file>")` with the cause kept. Tested with a missing font file.
- EJ-FR-25: the six broad `catch` blocks. `SecurityAuditAspect`, `SecurityEventService`, `ReminderJob` and `HealthController` keep their broad catch with a comment saying why it is safe. `AiContextBuilder` used to swallow every exception silently; it now catches the two expected ones ("module locked", "module unknown") quietly and logs anything else before falling back the same way. `PlanAssistantService.idOf` caught every `RuntimeException` to spot a bad reference; it now checks for null and catches only the parse errors. All six catch bodies are covered by a test (checked in the JaCoCo report); the new tests are `SecurityEventServiceFailureTest`, `ReminderJobTest`, a second `AiContextBuilderTest` case and a mangled-reference case in `PlanAssistantChangesFlowTests`.
- EJ-FR-26: a new `PlanLinked` interface, implemented by `Partner`, `Obstacle`, `CommunicationMessage`, `ProgressLog` and `Review`, with one default `linkedVisionAreaId()` that checks the links in the fixed order the old copies used (area, task, step, goal, dream). The core entities got `visionAreaId()` (`Dream`, `Goal`, `VisionStep`, `TaskItem`). The nine `areaIdOf` overloads in `DashboardService` were removed. No schema change. `PlanLinkedTest` pins the order.
- Backend 640 tests pass, coverage checks met, API contract snapshot unchanged.

**Phase 6 results.**

- EJ-FR-27: every public type in `service`, `service.support`, `util`, `exception`, `mapper` and the core entities (`VisionArea`, `Dream`, `Goal`, `VisionStep`, `TaskItem`, `BaseAuditableEntity`, `PlanLinked`) has a class-level Javadoc comment. Thirteen were missing; each was written by hand and checked against the code.
- EJ-FR-28: the methods that enforce a CLAUDE.md Core Business Rule name it: Rules 1–4 on the four `create…` methods, Rule 5 on `TaskItemRequest`, Rule 6 in `TaskItem`, Rule 7 on `validateComplexStep`, Rule 8 on `validateGoalCompletion`, Rule 9 on `isOverdue`, Rules 10–11 on `ProgressCalculator` and `applyRollup`, Rule 12 on `archive()` and `requireArchived`, Rules 13–14 on `EntityLookup.owned` and `UserScope`.
- EJ-FR-29: `DashboardService` (557 lines) was split into two package-private, stateless collaborators behind the same public methods: `DashboardTrend` (the twelve-week progress trend) and `DashboardAttention` (the method's rules checked against the records), plus a package-private `DashboardData` record. `DashboardService` is now about 300 lines. Its constructor did not change, and all 22 dashboard tests pass without edits. The AI service splits wait on Q3.
- EJ-FR-30: a search for public classes used only inside their own package found one, `ReminderJob`, which is now package-private.
- EJ-FR-31: `docs/DATA_MODEL.md` has a new section "Java coding rules (BRD 13)", saying which rules the build checks.
- EJ-FR-32: `README.md` has a new section "Java code quality (BRD 13)" with the commands for the guard-rail tests and the measures script.
- Backend 640 tests pass; JaCoCo line coverage 95.2%, branch 76.0%; coverage checks met; API contract snapshot unchanged.

### Final measures (8 Nov 2026)

| Measure | Baseline | Now | Target | Met |
| --- | --- | --- | --- | --- |
| Public lifecycle setters on the five core entities | 15 | 0 | 0 | Yes |
| `entity.setX(...)` calls in `service/` | 222 | 185 | 80 or fewer | **No** – needs EJ-FR-06 for Dream, Goal, Step and Area (Q5) |
| Public methods returning null | 4 (+6 lookups) | 0 (6 documented `@Nullable` lookups) | 0 | Yes |
| Free-text status fields in request DTOs | 4 | 0 (2 deliberate AI free-text fields) | 0 | Yes |
| Public methods with a `boolean` mode flag | 30 | 10 | 10 or fewer | Yes |
| Wide response records built by hand | 8 | 0 | 0 | Yes |
| Records with collections that do not copy them | 77 | 0 | 0 | Yes |
| Files reading the clock directly | 6 | 0 | 0 | Yes |
| Compiler warnings | 9 | 0, with warnings as errors | 0 | Yes |
| Backend tests | 591 | 640 | – | – |
| Backend coverage | 88.1% (Sonar) | checks met; JaCoCo line 95.2% | 88% or more | Yes (Sonar figure to confirm after merge) |
| API contract snapshot | Passing | Passing | Unchanged | Yes |

### Why this order

Phase 1 comes first so every later phase can show its numbers. Phase 2 comes next because it protects the business rules, which is the main reason for V13. Phases 3 to 5 are independent of each other and can be reordered. Phase 6 is last because Javadoc and class splits are easier once the method names have settled.

## Acceptance criteria

| ID | Criterion |
| --- | --- |
| EAC-01 | A unit test that calls only entity methods cannot put a `TaskItem` into Blocked without a reason, or into Completed without 100% progress and a `completedAt` time. |
| EAC-02 | A failed `TaskItem` update leaves every field of the object as it was before the call. |
| EAC-03 | Sending `"In Progress"`, `"in-progress"` and `"IN_PROGRESS"` to `PATCH /api/tasks/{id}/status` gives the same result as today; an unknown value gives HTTP 400 listing the allowed values. |
| EAC-04 | The account data export JSON is identical before and after for the same seed data. |
| EAC-05 | No public method in `backend/src/main/java` returns `null` (checked by the guard rail or a Sonar rule). |
| EAC-06 | Every record with a collection component is unaffected when the caller changes the original list afterwards (one test per record). |
| EAC-07 | Clock-dependent classes have tests that use a fixed clock, and the guard rail blocks new direct clock reads. |
| EAC-08 | The build compiles with `-Xlint:all -Werror`. |
| EAC-09 | All target-state measures are met, backend coverage is 88% or more, the API contract snapshot passes and the SonarQube quality gate is green on `main`. |
| EAC-10 | `docs/DATA_MODEL.md` and `README.md` describe the coding rules and how to check them. |

### Acceptance status (8 Nov 2026)

| ID | Status | Evidence |
| --- | --- | --- |
| EAC-01 | Met | `CoreEntityRulesTest` |
| EAC-02 | Met for `TaskItem` | `CoreEntityRulesTest.aRefusedUpdateLeavesEveryFieldAsItWas`; other core entities wait on Q5 |
| EAC-03 | Met | `StatusLabelFlowTests` |
| EAC-04 | Met (same keys and values; the old JSON was never byte-stable) | `AccountExportShapeTests` |
| EAC-05 | Met | Guard rule `publicMethodsDoNotReturnNullUnlessMarked` |
| EAC-06 | Met | `RecordCopiesTest` (all 79 records) |
| EAC-07 | Met | Fixed-clock tests in five classes; guard rule `timeComesFromAnInjectedClock` |
| EAC-08 | Met | `pom.xml` `failOnWarning`; probe class failed the build |
| EAC-09 | Partly met | All measures but the setter count met, coverage checks met, contract snapshot passes. Open: SonarQube Cloud quality gate on `main` after merge |
| EAC-10 | Met | `docs/DATA_MODEL.md`, `README.md` |

## Assumptions, dependencies and risks

### Assumptions

- The V12 test suite (89 backend test classes, 88.1% coverage) is strong enough to catch behaviour changes from refactoring.
- The frontend never relies on a field being sent as `null` versus `false` for the flags changed in EJ-FR-16.

### Dependencies

- The API contract snapshot from V12 Phase 2.
- Engineering lead approval for the guard-rail tool (Q1).
- A database backup before running the optional Flyway migration in production.

### Risks

| Risk | Impact | Mitigation |
| --- | --- | --- |
| Removing setters breaks Excel import, which builds entities field by field | Import fails | Give import its own factory method per entity (e.g. `TaskItem.imported(...)`); run the existing import tests in Phase 2 |
| Lombok `@Builder` on JPA entities without `@AllArgsConstructor` | Compile errors | Put `@Builder` on an explicit private constructor |
| Enum `@JsonCreator` changes accepted input | Frontend calls rejected | EAC-03 test covers every spelling used by the frontend |
| Boolean migration fills nulls with `false` where null meant "unknown" | Lost meaning | EJ-FR-16 reviews each field before any migration; tri-state fields stay `Boolean` |
| Large class splits create merge conflicts with feature work | Slower delivery | Do Phase 6 splits when no feature branch touches `DashboardService` |

## Open questions

| ID | Question | Owner |
| --- | --- | --- |
| Q1 | Guard-rail tool: add ArchUnit (a test-only library, outside the `CLAUDE.md` list), or write the checks as plain JUnit tests that scan the source tree? **Phase 1 used plain JUnit** so no new dependency was needed; the rules can move to ArchUnit later if it is approved. | Engineering lead |
| Q2 | Should EJ-FR-09 (encapsulating `Partner`, `Obstacle`, `CommunicationMessage`, `Review`) be required instead of optional? | Product owner |
| Q3 | Should the AI service splits in EJ-FR-29 be required in V13 or moved to a later version? | Engineering lead |
| Q4 | Should the 29 `@Value` fields move into constructors (Item 5)? It is not in V13 today; the guard-rail test could add it as a ratchet rule. | Engineering lead |
| Q5 | Should EJ-FR-06 also be finished for `Dream`, `Goal`, `VisionStep` and `VisionArea` (validate the proposed values before writing)? It would also bring `entity.setX(...)` calls close to the target of 80. | Engineering lead |
| Q6 | Progress logs on Completed tasks: should a log below 100% reopen the task (In Progress), and should a log at 100% keep the first completion time? Both are behaviour changes. | Product owner |

## Sign-off

| Role | Name | Date | Decision |
| --- | --- | --- | --- |
| Sponsor | | | |
| Product owner | | | |
| Engineering lead | | | |
| QA lead | | | |

## Sources

- `docs/Effective_Java_Book.md` – own-words summary of *Effective Java*, 3rd edition (item numbers from that edition). The book is used only as a reference for good practice; no text is copied.
- Code review of `backend/src/main/java/com/visionmapping` at commit `f7e7666` (8 Nov 2026).
- `CLAUDE.md` – Core Business Rules and Backend Design Rules.
- `docs/VMS_BRD_V12.0.0.md` – coverage baseline and API contract snapshot.
