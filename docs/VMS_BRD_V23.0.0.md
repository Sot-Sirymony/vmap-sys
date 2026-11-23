# BRD – User, Role and Permission Management

Nov 22, 2026

## Document control

This is draft 23.0.0. It keeps every requirement in BRD 22.0.0 (paused), BRD 21.0.0 (Two-step sign-in), BRD 20.0.0
(Verify it's you), BRD 10.0.0 (security) and the earlier BRDs, and adds **user, role and permission management**:
admins see every account in one list; create, update, block, unblock and delete accounts; assign roles; and create
roles of their own whose permissions they choose from a fixed list.

| Item | Value |
| --- | --- |
| Document | Business Requirements Document (BRD) – User, role and permission management addendum |
| Version | 23.0.0 – Draft |
| Parent documents | BRD 10.0.0 (SEC-01-03 one consistent authorisation check, SEC-01-05 disabled accounts, SEC-06-05 sensitive actions, SEC-06-06 deletion, SEC-07-03 ending sessions, SEC-09 security log), BRD 20.0.0 (step-up codes), BRD 21.0.0 (two-step sign-in for privileged accounts; admin reset), BRD 9.0.0 (D-6, D-8 coach access), `CLAUDE.md` (business rules 12, 13 and 14) |
| Business owner | To be confirmed |
| Product owner | To be confirmed |
| Approvers | Sponsor, Product owner, Security reviewer, Data Protection Officer |

| Version | Change | Author |
| --- | --- | --- |
| 23.0.0 (phase 7 built) | Phase 7: `UAT_V23.md` (UMC-01 to UMC-19), `km-review-v23.csv`, `USER_GUIDE.md`, `THREAT_MODEL.md` section 6, `ASVS_L2_CHECK.md` V8, CHANGELOG, and the D-23-3 sentence in business rule 14 of `CLAUDE.md` (Q-08). Abuse case 19 gained an automated test. All phases built; awaiting deploy, UAT, the Khmer review and sign-off | Dev |
| 23.0.0 (phase 6 built) | Phase 6 implemented (`UsersPage`, `UserDetailPanel`, `RolesPage`, `RoleEditor`, `AcceptInvitationPage`, `adminUsersApi.ts`, `rolesApi.ts`). Clarified during build: permission checks use the existing `can()` helper instead of a new `useCan` hook; the Roles page shows in the menu for *Manage roles* (readers with *View users* can open it); the cohort filter and cohort pickers appear only for viewers with *Manage cohorts*; coach choices in handovers list up to 100 active accounts whose role can coach; Google sign-in on a blocked account says it is blocked (reason `blocked`), as a right password does; CSV download (UM-FR-21, C) is not built | Dev |
| 23.0.0 (phase 5 built) | Phase 5 implemented (migration `V91__user_invitations.sql`, Add user, edit, resend invitation, password reset email, permanent delete, invitation acceptance). Clarified during build: the invitations table is in V91, not V90; `POST /api/auth/invitation/check` was added so the invitation page can greet the person before they choose a password; the invitation email goes out only after the account is saved; accepting an invitation for a blocked account answers 403; an admin may edit their own name and cohort (UM-FR-39 covers role, block and delete only); the deletion notice is a separate admin notice that names no browser; deletions count with blocks towards the UM-FR-43 hourly alert; the account detail adds the invitation state and the coached cohorts for the dialogs | Dev |
| 23.0.0 (phase 4 built) | Phase 4 implemented (`RoleService`, `/api/admin/roles`, `/api/admin/permissions`). Clarified during build: custom role keys come from the name (for example `PROGRAM_ASSISTANT`) and never change; Coach's name and description stay fixed; deleting a role whose holders coach active cohorts and whose target role cannot coach is refused until those cohorts get another coach; naming a cohort coach whose custom role cannot coach is refused instead of replacing the role; the permission cache is cleared again after each change commits | Dev |
| 23.0.0 (Q-09, Q-10 answered) | Product owner answers, 22 Nov 2026: Q-09 Participant is locked to *Use own plan* (UM-FR-10 and D-23-2 updated); Q-10 no limit on custom roles | BA |
| 23.0.0 (phase 3 built) | Phase 3 implemented (`AdminUserActionsService`; role, block and unblock endpoints). Clarified during build: the escalation rule (D-23-9) is one test, "the actor holds every permission of the target's role and of the role given", which also means only Admins act on Admins; a refused change does not use the step-up code (checked last); the role-change notice names the new role and no reason; blocked sign-in with Google keeps today's generic error page for now (UM-FR-32 covers the password path; Google in phase 6 with the pages); "coach blocked" on the Cohorts page comes with the pages in phase 6; reminders already skipped blocked accounts | Dev |
| 23.0.0 (phase 2 built) | Phase 2 implemented (migration `V90__user_management.sql`, `GET /api/admin/users` and `/{id}`). Clarified during build: the invitations table moves to phase 5, where it is used; no functional indexes on lower(email) and lower(full_name) (H2 cannot build them; not needed at 5,000 accounts); last sign-in is set when any session opens; "created by" reads self sign-up, Google (no password and a Google link) or the inviting admin's name; typed search text is matched literally | Dev |
| 23.0.0 (phase 1 built) | Phase 1 implemented (migration `V89__roles_and_permissions.sql`). Clarified during build: `app_users.role` keeps the role's **key** with a foreign key to `roles` instead of a new `role_id` (same model, no rewrite of accounts or existing SQL); a permission *Handle support* was added to UM-FR-01 for issue-report triage and the failed request log, which the list had missed; safety alerts with no coach go to *Manage cohorts*; the admin step-up warning needs *Manage cohorts*, *Manage AI settings* or *View security log*; *Manage roles* is removed from any non-Admin role in code, whatever the table holds; the two-step requirement keeps following the built-in roles until phase 4. No behaviour changed for any built-in role (full suites green) | Dev |
| 23.0.0 (questions answered) | Product owner answers, 22 Nov 2026: **Q-01 custom roles with editable permissions in V23** (confirmed: admins create roles and edit the permissions of every role except Admin); Q-02 no separate built-in "user manager" role (admins can make one as a custom role); Q-03 an account must be blocked before it is deleted; Q-04 the block notice does not give the reason; Q-05 without mail in production, Add user stops; Q-06 bulk import later; Q-07 the first admin comes from a one-time `ADMIN_BOOTSTRAP_EMAIL` setting; Q-08 business rule 14 in `CLAUDE.md` gains the D-23-3 sentence. The draft was reworked for custom roles: permission catalogue, role management (UM-FR-08 to UM-FR-21), escalation safeguards (D-23-9, D-23-10), permission-based checks everywhere, two more phases | BA |
| 23.0.0 | First draft: fixed roles, user list, create, update, block, delete, safeguards, open questions | BA |

## Executive summary

Today an admin has no way to see who uses the system or to act on an account. A person becomes a coach only as a side
effect of being named a cohort's coach; nobody can be made an admin, demoted, blocked or removed inside the app, and
what each role may do is fixed in code with no way to share part of the admin's work.

V23 adds three things for admins:

1. A **Users** page: a searchable list of every account, a detail panel, and actions to create (by invitation),
   edit, block, unblock, change role and delete.
2. A **Roles and permissions** page: the three built-in roles (Participant, Coach, Admin) and any number of **custom
   roles** (for example "Program assistant" who can manage participants and cohorts but not AI settings), each with
   permissions ticked from a fixed list.
3. **Permission-based checks**: every protected action on the server asks "does this person's role have permission
   X?" instead of "is this person an admin?", so a custom role works everywhere the permission is used.

Admins manage **accounts and roles, not plans**: no page shows another person's dreams, goals, tasks, journals or
reports, except the existing read-only coach view (D-6, D-8).

### Terms used in this BRD

| Term | Meaning in this BRD |
| --- | --- |
| Account | A person's sign-in record: name, email, role, status, cohort, two-step sign-in and dates. Not their plan data |
| Permission | One thing the system lets a role do, such as "Manage cohorts". The list of permissions is fixed in code (UM-FR-08); admins choose which roles have which |
| Role | A named set of permissions. Every account has exactly one role |
| Built-in role | Participant, Coach and Admin. They cannot be deleted or renamed. Admin always has every permission |
| Custom role | A role an admin creates, with a name, description and chosen permissions |
| Privileged role | A role with any permission beyond using one's own plan. Accounts with one must use two-step sign-in (V21) |
| Block | Set an account to `DISABLED`: no sign-in, every open session ends at once. Reversible |
| Delete | Permanently erase the account and everything it owns, as self-deletion does today. Not reversible |
| Invitation | An email that lets a person whose account an admin created choose their own password |

## Business objectives

| ID | Objective | Measure |
| --- | --- | --- |
| BO-1 | Admins can see and manage every account without database access | All jobs in section C to F done in the app in UAT |
| BO-2 | Admin work can be shared safely | A custom role can be given exactly the permissions chosen, and nothing more (automated test per permission) |
| BO-3 | Nobody can raise their own power | No account can grant itself, or a role it holds, a permission it does not already have; only Admins edit roles (tests) |
| BO-4 | A misused or stolen session cannot lock everyone out | No self-demotion, self-block or self-deletion; the last active Admin cannot be removed; the Admin role cannot be edited (tests) |
| BO-5 | Privacy is kept | No user or role management answer contains plan data (privacy guard and access sweep tests) |
| BO-6 | Changes take effect at once | A permission removed from a role, or a blocked account, is refused on the next request |

## Scope

### In scope

- **Users** page (`/settings/users`): list, search, filters, detail panel, create, edit, block, unblock, change role,
  delete.
- **Roles and permissions** page (`/settings/roles`): built-in and custom roles; create, rename, describe, edit
  permissions, delete custom roles; see who holds each role.
- A fixed **permission catalogue**, enforced on the server for every protected action.
- Moving every existing role check (`/api/admin/**`, `/api/coach/**`, `Roles.requireAdmin/requireCoach`, the admin
  menu) to permission checks, with the built-in roles keeping exactly today's behaviour.
- Invitations, notices, step-up codes, security log events, alerts, two-step requirement for privileged roles.
- A one-time `ADMIN_BOOTSTRAP_EMAIL` setting for the first admin (Q-07).
- English and Khmer text; `CLAUDE.md` rule 14 sentence (Q-08).

### Out of scope

- **New kinds of permission** beyond the catalogue in UM-FR-08. Adding one is a code change with its own tests.
- **More than one role per account.** One role each; make a custom role that combines what is needed.
- **Permissions scoped to part of the data** (for example "manage only cohort X"). Permissions apply system-wide,
  except coaching, which keeps its "only the cohorts you coach" rule (D-6).
- **Changing a person's login email** (must verify both addresses; later BRD).
- Reading or editing anyone's plan data as an admin, and signing in as another user (D-23-3).
- Setting or seeing anyone's password (D-23-5).
- Bulk import of users (Q-06, later).
- Email confirmation at self sign-up (BRD 22, paused).

## Current state (22 Nov 2026, commit `e477e1e`)

| Area | Today |
| --- | --- |
| Roles | `USER`, `COACH`, `ADMIN` (`UserRole` enum on `app_users.role`), read from the database on every request |
| Checks | Path rules: `/api/admin/**` `hasRole("ADMIN")`, `/api/coach/**` `hasAnyRole("COACH","ADMIN")`; services call `Roles.requireAdmin` / `requireCoach` (SEC-01-03); the frontend shows admin menu items by role name |
| Admin features | Cohorts, Security log, Two-step sign-in reset (V21), AI settings and AI admin reports, Module content, Interface text, step-up status |
| Coach features | Read-only view of participants in the active cohorts they coach; shared journals and reports; comments and ratings |
| Becoming a coach | Only by being named a cohort's coach (`CohortAdminService`), after a fresh `ADMIN_ROLE_CHANGE` code; logged `ROLE_CHANGED` |
| Becoming an admin, demoting, blocking | Not possible in the app; database edits only |
| Status | `ACTIVE`, `DISABLED`; `DISABLED` refused at sign-in and on every request (SEC-01-05) |
| Deleting | Self-deletion only (`AccountService`): erases every user table, anonymises AI usage and finished plans |
| Two-step requirement | Admins and coaches (V21), with a 14-day grace period |
| Last sign-in | Not on the account; `LOGIN_SUCCEEDED` events in the security log |

## Decisions recorded by this BRD

| ID | Decision | Effect on `CLAUDE.md` |
| --- | --- | --- |
| D-23-1 | **Permissions, not role names, decide access.** Every protected endpoint and service names the permission it needs; the server checks the person's role has it on every request. The three built-in roles get exactly the permissions that reproduce today's behaviour. | None |
| D-23-2 | **Custom roles with editable permissions** *(approved, Q-01)*. Admins create roles and tick permissions from the fixed catalogue; they can also edit the permissions of **Coach**. **Admin** always has every permission and **Participant** only *Use own plan*; neither can be edited, renamed or deleted (Q-09). | None |
| D-23-3 | **Accounts and roles, not plans.** No user or role management answer contains plan data, journals, messages or reports, and nobody can sign in as someone else. *(Approved, Q-08.)* | Business rule 14 gains: "An admin, or a role with the user management permissions, may see and manage account details (name, email, role, status, cohort), never another user's plan data." |
| D-23-4 | **Risky actions need a fresh code and a reason**: role change, block, unblock, delete, creating a privileged account, and any change to a role's permissions. | None |
| D-23-5 | **Invitations, never passwords.** New accounts get a single-use link (7 days) to choose a password. | None |
| D-23-6 | **Delete only after block** *(approved, Q-03)*: deletion is permanent erasure, the same as self-deletion, and is offered only for a blocked account. | Business rule 12 unchanged |
| D-23-7 | **Nobody can lock the admins out**: no one changes their own role, blocks or deletes themselves; the last active Admin cannot be demoted, blocked or deleted. | None |
| D-23-8 | **The person is told**: role change, block, unblock and deletion send a notice to the account email. The block notice does not give the reason *(approved, Q-04)*. | None |
| D-23-9 | **No escalation.** Only accounts with the Admin role may create, edit or delete roles, or give anyone the Admin role. An account with "Manage users" may assign only roles whose permissions are all ones it holds itself, and may act only on accounts whose role it could assign. | None |
| D-23-10 | **Coaching keeps its rule.** The "Coach cohorts" permission lets a person be named a cohort's coach and read, never change, the participants of the active cohorts they coach (D-6, D-8). It never widens to other cohorts. | Business rule 14 unchanged in substance |

## Target experience

1. Sokha (Admin) opens **Settings → Roles and permissions** and chooses **New role**: "Program assistant", "Helps run
   cohorts and participant accounts". She ticks *View users*, *Manage users*, *Manage cohorts* and *Coach cohorts*,
   types a reason, enters her "Verify it's you" code and saves.
2. On **Settings → Users** she searches "dara", opens Dara's account, chooses **Change role → Program assistant**,
   types a reason and her code. Dara gets an email and must set up two-step sign-in within 14 days.
3. Dara now sees **Users** and **Cohorts** in Settings, but not AI settings or the Security log. He can invite new
   participants and block a participant, but he cannot change Sokha's account, open the Roles page, or give anyone a
   role with permissions he does not have.
4. Sokha later removes *Manage cohorts* from Program assistant. Dara's next click on Cohorts answers "You no longer
   have permission for this page."

## Functional requirements

Priority: **M** Must, **S** Should, **C** Could.

### A. The permission catalogue

| ID | Requirement | Priority |
| --- | --- | --- |
| UM-FR-01 | The permissions are a fixed list on the server, each with a key, a plain name and description in English and Khmer, and a group: **Own plan**: *Use own plan* (every role; cannot be removed). **Coaching**: *Coach cohorts*. **People**: *View users*, *Manage users* (create, edit, block, unblock, resend invitation, send password reset), *Delete users*, *Reset two-step sign-in*. **Program**: *Manage cohorts*, *Manage module content*, *Manage interface text*, *Handle support* (issue reports and the failed request log; added in phase 1). **AI**: *Manage AI settings* (key, budget, model, page switches), *View AI reports*. **Security**: *View security log*. **Roles**: *Manage roles* (Admin only; never on another role). | M |
| UM-FR-02 | Every protected endpoint names the permission it needs, and the access-control sweep checks each one: an account without it gets 403 and an `ACCESS_DENIED` event. | M |
| UM-FR-03 | Built-in permissions, set by the migration so nothing changes for anyone: **Participant**: *Use own plan*. **Coach**: *Use own plan*, *Coach cohorts*. **Admin**: all. | M |
| UM-FR-04 | Permissions are read from the database on every request (with a cache cleared on any role change), so a change applies on the person's next request. | M |
| UM-FR-05 | `GET /api/account/me` returns the person's role name and permission keys; the frontend shows menu items and buttons by permission, never by role name. | M |
| UM-FR-06 | A role with any permission beyond *Use own plan* is **privileged**: its holders must use two-step sign-in, with the V21 14-day grace period from the day they receive it. | M |
| UM-FR-07 | Some permissions need others and are ticked together: *Manage users* needs *View users*; *Delete users* needs *Manage users*; *Reset two-step sign-in* needs *View users*. | M |

### B. Roles

| ID | Requirement | Priority |
| --- | --- | --- |
| UM-FR-08 | `/settings/roles` lists every role: name, description, built-in or custom, number of holders, and its permissions grouped as in UM-FR-01. Visible to accounts with *Manage roles* (Admins) and, read-only, *View users*. | M |
| UM-FR-09 | **New role**: name (unique, 3 to 40 characters), description (up to 200), permissions. Starts with no holders. | M |
| UM-FR-10 | **Edit** a custom role, or the permissions of Coach: name, description, permissions. The Admin and Participant roles cannot be edited (UM-FR-01, D-23-2, Q-09). | M |
| UM-FR-11 | Saving a change to permissions shows what changes ("adds Manage cohorts; removes View AI reports") and how many people it affects, and needs a reason and a fresh `ADMIN_ROLE_EDIT` code. | M |
| UM-FR-12 | Removing *Coach cohorts* from a role whose holders coach **active** cohorts is refused until those cohorts get another coach or none (the dialog lists them and offers both). | M |
| UM-FR-13 | Making a role privileged starts the two-step requirement for its holders (UM-FR-06), with a notice to each. | M |
| UM-FR-14 | **Delete** a custom role only when nobody holds it; otherwise the dialog lists the holders and offers to move them all to another role first (one action, fresh code). | M |
| UM-FR-15 | Built-in roles cannot be deleted or renamed. | M |
| UM-FR-16 | Only accounts with the Admin role can create, edit or delete roles (D-23-9); the server refuses anyone else, whatever their permissions. | M |

### C. The user list

| ID | Requirement | Priority |
| --- | --- | --- |
| UM-FR-17 | `/settings/users` (needs *View users*) lists every account, 25 per page (10, 25, 50 or 100): name, email, role, status, cohort, two-step sign-in (on / off / due by date), joined, last sign-in. | M |
| UM-FR-18 | Search by name or email (contains, case-insensitive). Filters: role, status, cohort, two-step state. Sort by name, joined, last sign-in. Counts: total, active, blocked, and per role. | M |
| UM-FR-19 | The detail panel shows the account fields, how it was created (self sign-up, Google, or invited by whom), when the role last changed and by whom, **counts** of vision areas, dreams, goals, steps and tasks (no titles or content, D-23-3), and the last 20 security events about the account. | M |
| UM-FR-20 | Buttons appear only for actions the viewer may take on this account (D-23-9); the server checks again. | M |
| UM-FR-21 | The list can be downloaded as CSV with the same columns; the download is logged. | C |

### D. Creating and updating accounts

| ID | Requirement | Priority |
| --- | --- | --- |
| UM-FR-22 | **Add user** (*Manage users*): full name, email, role (any role the viewer may assign, default Participant), optional cohort. A privileged role needs a fresh `ADMIN_ROLE_CHANGE` code. | M |
| UM-FR-23 | An email that already has an account is refused with "This email already has an account". | M |
| UM-FR-24 | The account starts `ACTIVE`, with no usable password (`password_set = false`) and `created_by` the viewer, and the person gets an **invitation**: single-use link (256 bits, SHA-256 stored, 7 days) to choose a password, which signs them in. **Resend invitation** replaces the link. | M |
| UM-FR-25 | Without mail locally, the link goes to the server log. In production without mail, **Add user** explains that invitations cannot be sent and creates nothing *(approved, Q-05)*. | M |
| UM-FR-26 | A person invited by email may complete the invitation with **Sign in with Google** on the same address. | S |
| UM-FR-27 | **Edit** (*Manage users*): full name and cohort. The login email is read-only with a short explanation. Logged as `USER_UPDATED` (field names, not values). | M |
| UM-FR-28 | **Send password reset email** (*Manage users*) sends the normal 30-minute reset link; the viewer never sees it. **Reset two-step sign-in** (*Reset two-step sign-in*) uses the V21 admin reset. | S |

### E. Changing a role, blocking, unblocking

| ID | Requirement | Priority |
| --- | --- | --- |
| UM-FR-29 | **Change role** (*Manage users*, within D-23-9): pick a role, type a reason (10 to 300 characters), enter a fresh `ADMIN_ROLE_CHANGE` code. Giving the Admin role needs the viewer to be an Admin. | M |
| UM-FR-30 | Moving a person who coaches active cohorts to a role without *Coach cohorts* asks for a new coach (or none) for each; everything saves together or not at all. | M |
| UM-FR-31 | **Block** (*Manage users*, within D-23-9): reason and fresh `ADMIN_USER_BLOCK` code; status `DISABLED`; every open session ends at once. | M |
| UM-FR-32 | A blocked person who signs in with the right password (or Google) sees "This account is blocked. Contact your program admin." A wrong password still gets the usual answer, so the message reveals nothing to a guesser. | M |
| UM-FR-33 | **Unblock**: reason and fresh code; status `ACTIVE`; the person signs in again. | M |
| UM-FR-34 | While blocked, the person's share links do not open, scheduled emails are not sent, and cohorts they coach show "coach blocked" (participants keep their own access). | M |

### F. Deleting accounts

| ID | Requirement | Priority |
| --- | --- | --- |
| UM-FR-35 | **Delete permanently** (*Delete users*, within D-23-9) is offered only for a blocked account *(Q-03)*. | M |
| UM-FR-36 | It needs typing the person's email exactly, a reason and a fresh `ADMIN_USER_DELETE` code; the dialog lists what is erased (counts), what is kept anonymised, and the cohorts left without a coach. | M |
| UM-FR-37 | It uses the self-deletion erase routine in one transaction; any failure deletes nothing. A notice goes to the account email first. | M |
| UM-FR-38 | The security log keeps the event with the deleted account id, the viewer, the time and the reason, without the person's name or email. | M |

### G. Safeguards, first admin, audit and alerts

| ID | Requirement | Priority |
| --- | --- | --- |
| UM-FR-39 | No one may change their own role, block, unblock or delete their own account (403; buttons hidden on their own row). | M |
| UM-FR-40 | The last active Admin cannot be demoted, blocked or deleted (409 "There must be at least one active admin"). | M |
| UM-FR-41 | **First admin** *(approved, Q-07)*: at start-up, if `ADMIN_BOOTSTRAP_EMAIL` names an existing account and **no** active Admin exists, that account becomes Admin once, the event `ADMIN_BOOTSTRAPPED` is logged and an alert raised. If an Admin already exists, the setting is ignored with a warning, so it cannot be used later to add admins. | M |
| UM-FR-42 | Security events: `ROLE_CREATED`, `ROLE_UPDATED` (permissions added and removed), `ROLE_DELETED`, `USER_CREATED_BY_ADMIN`, `USER_UPDATED`, `ROLE_CHANGED` (existing), `USER_BLOCKED`, `USER_UNBLOCKED`, `USER_DELETED_BY_ADMIN`, `INVITATION_SENT`, `INVITATION_ACCEPTED`, `ADMIN_BOOTSTRAPPED`, each with the actor, the target and the reason. | M |
| UM-FR-43 | Alerts (SEC-09-04): the Admin role granted (existing); any role gaining *Delete users*, *Manage AI settings*, *View security log* or *Reset two-step sign-in*; an Admin blocked or deleted; more than 5 accounts blocked or deleted by one person within an hour; the first-admin bootstrap. | M |
| UM-FR-44 | Rate limit: 30 user or role management changes per person per 10 minutes. | M |
| UM-FR-45 | The person's own Account page shows their role and its permissions in plain words. | S |

## Non-functional requirements

| ID | Requirement |
| --- | --- |
| UM-NFR-01 | The list answers in under 500 ms for 5,000 accounts (paged; indexed search on email and name). |
| UM-NFR-02 | The permission check adds under 5 ms per request (cached per role, cleared on change). |
| UM-NFR-03 | No user or role management answer contains plan data, password hashes, two-step secrets, recovery codes or tokens (guard test over the response types). |
| UM-NFR-04 | The frontend only hides what a person may not do; the server decides (deny by default for any endpoint that names no permission: a guard test fails the build). |
| UM-NFR-05 | All new text in English and Khmer; Khmer added to the review sheet. |
| UM-NFR-06 | Coverage minimums from BRD 12; `THREAT_MODEL.md` gains a "User, role and permission management" section (a new trust boundary for delegated admin power). |
| UM-NFR-07 | Accessible tables and dialogs (headers, focus trap, keyboard use). |

## Data and API changes (for solution design)

| Change | Detail |
| --- | --- |
| Migration `V89__roles_and_permissions.sql` | New `roles` (id, key unique, name unique, description, built_in, created/updated by and at) with Participant, Coach and Admin; `role_permissions` (role_id, permission) with UM-FR-03. `app_users.role_id` (not null, FK) filled from the old `role` column, which is dropped after the code no longer reads it |
| Migration `V90__user_management.sql` | `app_users` gains `created_by_id`, `role_changed_at`, `role_changed_by_id`, `blocked_at`, `blocked_by_id`, `last_sign_in_at`; new `user_invitations` (id, user_id, token_hash unique, expires_at, used_at, created_by_id, created_at); indexes on lower(email) and lower(full_name) |
| `Permission` enum, `PermissionService` | The catalogue; `require(Permission)` used by services and a method-security annotation on controllers; replaces `Roles.requireAdmin/requireCoach` and the path role rules |
| `GET /api/admin/permissions`, `GET/POST /api/admin/roles`, `PUT/DELETE /api/admin/roles/{id}` | Catalogue and roles (UM-FR-08 to UM-FR-16); writes need `X-Step-Up` |
| `GET /api/admin/users`, `GET /api/admin/users/{id}` | List and detail |
| `POST /api/admin/users`, `PATCH /api/admin/users/{id}` | Create with invitation; edit name and cohort |
| `PUT /api/admin/users/{id}/role`, `POST …/block`, `POST …/unblock`, `DELETE …/{id}` | With reason and `X-Step-Up` |
| `POST …/invitation`, `POST …/password-reset` | Resend invitation; send reset email |
| `POST /api/auth/invitation/accept` | Public; `{ token, password }`; 410 for a used or expired link |
| Step-up actions | New `ADMIN_ROLE_EDIT`, `ADMIN_USER_BLOCK`, `ADMIN_USER_DELETE`; `ADMIN_ROLE_CHANGE` reused |
| Settings | `ADMIN_BOOTSTRAP_EMAIL` (UM-FR-41); invitation link base URL and lifetime |
| Frontend | `UsersPage`, `UserDetailPanel`, `RolesPage`, `RoleEditor`, `AcceptInvitationPage` (`/invitation`), `adminUsersApi.ts`, `rolesApi.ts`; permission-aware `useCan(permission)` hook used by the sidebar and buttons |

## Implementation phases

| Phase | Content | Done when |
| --- | --- | --- |
| 1 | Permission catalogue, roles tables (built-ins only), `PermissionService`; move every existing check to permissions with identical behaviour; `me` returns permissions; frontend menus by permission; first-admin bootstrap | Full suites green with no behaviour change; access sweep covers every endpoint by permission; deny-by-default guard test |
| 2 | Users list and detail, last sign-in, privacy guard | UM-FR-17 to UM-FR-21 tests |
| 3 | Change role, block, unblock, safeguards, escalation rules, events, alerts, rate limit | UM-FR-29 to UM-FR-34, UM-FR-39, UM-FR-40, UM-FR-42 to UM-FR-44 tests, abuse cases 11 to 18 |
| 4 | Custom roles: create, edit, delete, built-in edits, coach-cohort handling, two-step requirement on privilege | UM-FR-08 to UM-FR-16 tests |
| 5 | Create by invitation, edit, resend, password reset email, delete | UM-FR-22 to UM-FR-28, UM-FR-35 to UM-FR-38 tests |
| 6 | Users page, detail panel, dialogs, Roles page and editor, invitation page, Khmer | Frontend tests and build green |
| 7 | `API.md`, `DATA_MODEL.md`, `USER_GUIDE.md`, `SECURITY.md`, `THREAT_MODEL.md`, `ASVS_L2_CHECK.md`, `UAT_V23.md`, CHANGELOG, `CLAUDE.md` rule 14 | Docs updated; UAT script ready |

## Acceptance criteria

1. After phase 1, every participant, coach and admin can do exactly what they could before (full suites and UAT
   smoke test).
2. An Admin creates "Program assistant" with chosen permissions; a holder can use exactly those features and gets 403
   on every other protected endpoint (sweep).
3. Removing a permission from a role is refused on the holder's next request.
4. An admin sees every account in the list, searches and filters it; the detail panel shows counts, never content.
5. Changing a role, blocking, unblocking and deleting need a reason and a fresh code, notify the person and are logged.
6. A blocked person's session is refused on its next request, and they cannot sign in.
7. Only a blocked account can be deleted; deletion erases the same data as self-deletion.
8. An invited person sets a password from the link and is signed in.
9. With `ADMIN_BOOTSTRAP_EMAIL` set and no Admin, that account becomes Admin once; with an Admin present, nothing
   happens.
10. Backend and frontend suites, the access-control sweep and the coverage gate pass.

**Abuse cases (SEC-06-04):**

11. A "Program assistant" (with *Manage users*) tries to give themselves or anyone the Admin role: refused (403).
12. They try to assign a custom role that has *Manage AI settings*, which they do not hold: refused (403).
13. They try to block, edit or delete an Admin's account: refused (403).
14. They call `POST /api/admin/roles` to make a role with every permission: refused; only Admins edit roles.
15. An Admin tries to remove permissions from the Admin role, or delete it: refused.
16. An Admin tries to demote, block or delete themselves, or the last Admin is targeted: refused (403 / 409).
17. Someone sets `ADMIN_BOOTSTRAP_EMAIL` on a running system that already has an Admin: ignored, warning logged.
18. A person with *View users* tries to read a participant's dreams through any admin endpoint: none returns them.
19. A stolen Admin session tries to block many accounts: each needs a fresh code, and more than 5 in an hour raise an
    alert.

## Assumptions, dependencies and risks

### Assumptions

- Mail works in production (invitations, notices, codes).
- At least one Admin exists today; new installations use `ADMIN_BOOTSTRAP_EMAIL`.

### Dependencies

- V20 step-up, V21 two-step sign-in and admin reset, the V10 security log and alerts, the CORS step-up headers
  (`e477e1e`).

### Risks

| Risk | Effect | Response |
| --- | --- | --- |
| Phase 1 changes every access check | A page opens to the wrong people, or closes to the right ones | Phase 1 ships alone with no visible change; access sweep per permission; deny-by-default guard |
| Privilege escalation through role management | Someone gains Admin power | D-23-9, abuse cases 11 to 14, alerts on sensitive permissions |
| Admin deletes the wrong account | Data lost | Block first, typed email, fresh code, counts shown |
| Too many custom roles, unclear who can do what | Mistakes, confusion | Roles page shows holders and permissions; Account page shows one's own; permission changes logged with a diff |
| Coaches lose cohorts by accident | Participants without a coach | UM-FR-12 and UM-FR-30 make the choice explicit and atomic |
| Admins see more than they should | Privacy complaint | D-23-3, privacy guard test |

## Open questions

- [x] **Q-01** Are fixed roles enough, or must admins create roles and edit permissions? **Answered by the product owner on 22 Nov 2026:** custom roles with editable permissions in V23 (D-23-2); Admin stays fixed.
- [x] **Q-02** A separate built-in "user manager" role? **Answered:** no; admins can create one as a custom role.
- [x] **Q-03** Must an account be blocked before it can be deleted? **Answered:** yes (D-23-6).
- [x] **Q-04** Should the block notice give the reason? **Answered:** no.
- [x] **Q-05** In production without mail, should Add user stop? **Answered:** yes, stop.
- [x] **Q-06** Bulk import of users now? **Answered:** later BRD.
- [x] **Q-07** How is the first admin created? **Answered:** a one-time `ADMIN_BOOTSTRAP_EMAIL` setting (UM-FR-41).
- [x] **Q-08** Should business rule 14 in `CLAUDE.md` gain the D-23-3 sentence? **Answered:** yes; added in phase 7.
- [x] **Q-09** Should **Participant** be editable? **Answered by the product owner on 22 Nov 2026:** no; Participant is locked to *Use own plan*, like Admin is locked. Extra permissions come through a custom role. Coach stays editable.
- [x] **Q-10** Is a limit on the number of custom roles wanted? **Answered by the product owner on 22 Nov 2026:** no limit; the Roles page sorts by holders.

## Sign-off

| Role | Name | Decision | Date |
| --- | --- | --- | --- |
| Sponsor | | | |
| Product owner | | | |
| Security reviewer | | | |
| Data Protection Officer | | | |

## Sources

- OWASP ASVS 5.0 V8 Authorization (least privilege, deny by default, server-side checks).
- OWASP Authorization Cheat Sheet (role-based access control, preventing privilege escalation).
- NIST RBAC model (roles as named permission sets).
- `docs/THREAT_MODEL.md`, `docs/ASVS_L2_CHECK.md`, `docs/SECURITY.md`.
