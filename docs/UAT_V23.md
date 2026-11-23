# UAT checklist – Users, roles and permissions (V23)

For the BRD 23.0.0 acceptance criteria (UMC-01 to UMC-10) and abuse cases (UMC-11 to UMC-19). Use a server with mail
set up (`MAIL_HOST`, so invitations and "Verify it's you" codes arrive), two admin accounts (A and B), a coach who
coaches one active cohort, two participants, an inbox you can read for a new person, and a second browser. Tick each
line and note anything that differs.

| ID | Steps | Expected | Automated test | Result |
| --- | --- | --- | --- | --- |
| UMC-01 | Sign in as a participant, a coach and an admin; use each person's usual pages | Exactly what each could do before V23: participants their own plan, coaches **Coaching**, admins every admin page | `PermissionServiceTest`, `PermissionFlowTests`, full suites | ☐ |
| UMC-02 | Admin A: **Roles → New role** "Program assistant" with *Manage cohorts* only; give it to participant 1 (**Users → Change role**); participant 1 signs in again | **Cohorts** appears in the menu and works; **Users**, **AI settings** and **Security log** are missing, and calling them directly answers 403 | `RoleFlowTests.aCustomRoleGivesExactlyItsPermissionsAndChangesApplyAtOnce`, `PermissionFlowTests` | ☐ |
| UMC-03 | Admin A: edit "Program assistant", remove *Manage cohorts*; participant 1 reloads the Cohorts page | The edit shows "removes Manage cohorts" and "affects 1 people"; participant 1's next request is refused (403) | `RoleFlowTests.aCustomRoleGivesExactlyItsPermissionsAndChangesApplyAtOnce` | ☐ |
| UMC-04 | Admin A: **Users**; search by part of a name, then an email; filter by role, status and two-step state; open someone | The list narrows each time; counts at the top; the panel shows account details, record **counts** and recent events, never dream or goal titles | `AdminUserFlowTests` | ☐ |
| UMC-05 | Admin A: change participant 2's role, block them, unblock them, then block and delete them (UMC-07) | Each asks for a reason and a fresh code; participant 2 gets an email for each (no reason given); the security log shows each with the reason | `AdminUserActionsFlowTests`, `AdminUserLifecycleFlowTests`, `UsersPage.test.tsx` | ☐ |
| UMC-06 | Participant 1 is signed in on the second browser; admin A blocks them; participant 1 clicks anything, then tries to sign in with the right password, and with Google | Signed out at the next request; sign-in says "This account is blocked. Contact your program admin." (Google too); a wrong password still says "Invalid email or password." | `AdminUserActionsFlowTests.blockingEndsSessionsAndSignInSaysBlockedOnlyAfterTheRightPassword`, `GoogleSignInOutcomeFlowTests` | ☐ |
| UMC-07 | Admin A opens an active account: no **Delete permanently**; block it; **Delete permanently**: type a wrong email, then the right one, a reason and the code | Delete appears only once blocked; the button stays off until the email matches; the dialog lists counts and cohorts left without a coach; afterwards the account and its data are gone, and the log keeps the account number and reason only | `AdminUserLifecycleFlowTests`, `AdminUserActionsServiceTest`, `UsersPage.test.tsx` | ☐ |
| UMC-08 | Admin A: **Users → Add user** for the new person's address; the person opens the email, chooses a password; then admin A adds a second person, who uses **Sign in with Google instead** on the same address | An invitation email arrives; the link opens a page that greets them by name; they are signed in after saving; the link does not work twice; the Google person is signed in with no password asked | `AdminUserLifecycleFlowTests`, `GoogleSignInOutcomeFlowTests.umFr26_*`, `AcceptInvitationPage.test.tsx` | ☐ |
| UMC-09 | (Staging) Remove the admin role from everyone with SQL, set `ADMIN_BOOTSTRAP_EMAIL` to a participant, restart; restart again with an admin present | That account becomes Admin once, with an alert; the second restart changes nothing and logs a warning. Remove the setting afterwards | `AdminBootstrapTest` | ☐ |
| UMC-10 | Check CI on the release commit | Backend, frontend, access-control sweep, coverage gate, SonarQube and security jobs pass | CI | ☐ |
| UMC-11 (abuse) | Give "Program assistant" *Manage users*; as participant 1, try to give anyone, including yourself, the Admin role (call `PUT /api/admin/users/{id}/role` directly too) | Admin is not offered; the call answers 403 | `AdminUserActionsFlowTests.aUserManagerCannotRaiseAnyoneAboveThemselvesOrTouchAnAdmin` | ☐ |
| UMC-12 (abuse) | Make a role with *Manage AI settings*; participant 1 tries to give it to someone | Not offered; the call answers 403 | `RoleFlowTests.aUserManagerCannotGiveARoleWithPermissionsTheyLack` | ☐ |
| UMC-13 (abuse) | Participant 1 opens admin B in **Users**; tries block, edit and delete by calling the API | The panel says the account has permissions they do not have; every call answers 403 | `AdminUserActionsFlowTests`, `AdminUserLifecycleFlowTests.aUserManagerCannotAddSomeoneWithMorePermissionsThanThey`, `UsersPage.test.tsx` | ☐ |
| UMC-14 (abuse) | Participant 1 calls `POST /api/admin/roles` with every permission | 403: only Admins change roles; *Manage roles* is never given to another role | `PermissionFlowTests.managingRolesIsNeverGivenToAnotherRole`, `RoleFlowTests` | ☐ |
| UMC-15 (abuse) | Admin A: try to edit or delete the Admin role (page and API) | No **Edit** or **Delete** on the page; the API answers 409 | `RoleFlowTests.theCatalogueAndBuiltInRolesAreShownAndLockedWhereTheyShouldBe` | ☐ |
| UMC-16 (abuse) | Admin A opens their own account; tries to demote, block or delete themselves (API too); with B blocked, A tries to demote the last active admin | Own account: only **Edit**, and 403 from the API; last active admin: 409 "There must be at least one active admin" | `AdminUserActionsFlowTests.nobodyChangesOrBlocksTheirOwnAccount`, `AdminUserActionsServiceTest` | ☐ |
| UMC-17 (abuse) | With admins present, set `ADMIN_BOOTSTRAP_EMAIL` and restart | Nothing changes; a warning in the log | `AdminBootstrapTest.whenAnAdminExistsTheSettingDoesNothing` | ☐ |
| UMC-18 (abuse) | Someone with *View users* calls every `/api/admin/users…` endpoint for a participant who has dreams | No answer contains a dream, goal, journal or report text | `AdminUserFlowTests.theDetailShowsCountsAndRecentEventsButNeverPlanText`, `AdminUserFlowTests.adminAnswersHoldNoSecretsOrPlanFields` | ☐ |
| UMC-19 (abuse) | Admin A blocks six test accounts within an hour | Each block asks for a fresh code; after the sixth, an alert "blocked or deleted more than 5 accounts within an hour" | `AdminUserActionsFlowTests.blockingMoreThanFiveAccountsInAnHourRaisesAnAlert` | ☐ |

Also check:

- **Account** page: everyone sees their role and its permissions in plain words (UM-FR-45).
- **Cohorts**: a cohort whose coach is blocked shows "coach blocked"; its participants keep using their plans (UM-FR-34).
- A blocked person's plan and report share links stop opening, and open again after unblocking (UM-FR-34,
  `AdminUserActionsFlowTests.aBlockedOwnersShareLinksPauseAndComeBack`).
- Without mail on a non-local server, **Add user** explains that invitations cannot be sent and creates nothing (Q-05).
- More than 30 user or role changes by one person within 10 minutes answer "Too many account changes" (UM-FR-44).

Khmer wording: review [km-review-v23.csv](km-review-v23.csv). Invitation emails and notices are in English until the
app stores each person's language (as in V20 and V21).
