# UAT checklist – Security access (V24)

For the BRD 24.0.0 acceptance criteria (SAC-01 to SAC-08) and abuse cases (SAC-09 to SAC-14). Use a staging server
with mail set up (`MAIL_HOST`, so "Verify it's you" codes arrive) and Google sign-in set up, two admin accounts (A
and B), a coach, a participant with a password only (P1), a participant made with Google only (P2), a participant
with both (P3), and a second browser. Tick each line and note anything that differs.

On a server with real participants, turning a method off is refused while anyone can only sign in with it (D-24-6).
Use a staging server with only the test accounts above, or prepare them (P1 links Google, P2 gets a password reset)
before SAC-02 and SAC-03.

| ID | Steps | Expected | Automated test | Result |
| --- | --- | --- | --- | --- |
| SAC-01 | Before changing anything: sign in as P1 with a password, P2 with Google, and admin A; open Sign In and Sign Up signed out | Everyone signs in as before V24; both pages offer Google and the password form | `SecurityAccessFlowTests.theDefaultsMatchTodaysBehaviourAndThePublicListNeedsNoToken`, full suites | ☐ |
| SAC-02 | P3 signed in with a password on the second browser. Admin A: **Security access**, switch **Password sign-in** off, save with a reason and the code. P3 clicks anything, then signs in with the right password, then a wrong one. Admin B signs in with a password | The save dialog names the sessions to end; P3 is signed out at the next request; the right password answers "Password sign-in is turned off. Sign in with Google."; a wrong one "Invalid email or password."; admin B is signed in. Sign In shows the Google button, the notice and "Admin? Sign in with a password"; Sign Up offers Google only; an invitation link offers Google only | `SecurityAccessEnforcementFlowTests`, `SecurityAccessServiceTest`, `LoginPage.test.tsx`, `RegisterPage.test.tsx`, `AcceptInvitationPage.test.tsx` | ☐ |
| SAC-03 | Turn password sign-in back on. P3 signed in with Google on the second browser. Admin A switches **Google sign-in** off. P3 clicks anything, then tries Google again; a new address tries Google sign-up | P3 is signed out; Google answers "Google sign-in is turned off. Sign in with your password."; the Google button disappears from Sign In and Sign Up; an admin can still sign in with Google | `SecurityAccessEnforcementFlowTests.withGoogleOffGoogleSignInSignUpAndLinkingAreRefusedButAdminsStillSignIn` | ☐ |
| SAC-04 | With P2 (Google only) present and Google on, admin A switches Google off and saves | Refused: "1 person can only sign in with Google. Send each a password reset…"; the page warned before saving; nothing changed | `SecurityAccessEnforcementFlowTests.changesThatWouldStrandPeopleOrDoNothingAreRefusedAndOnlyAdminsMayChange` | ☐ |
| SAC-05 | Admin A switches both methods off | The page says "At least one sign-in method must stay on." and **Save changes** stays off; the API answers 400 | `SecurityAccessPage.test.tsx`, `SecurityAccessEnforcementFlowTests` | ☐ |
| SAC-06 | Admin A chooses **Everyone** and saves. P1 signs in; then (SQL on staging) move P1's set-up date to yesterday and P1 reloads; P1 sets up two-step sign-in. Then admin A chooses **Privileged roles** | P1 sees "Set up now" / "Skip for now (14 days left)" with words for participants; after the date P1 can only open the Security page ("Every account needs it."); after setting it up P1 cannot turn it off. After switching back, participants without it have no date, coaches keep theirs, and P1 keeps two-step sign-in and may now turn it off | `TwoFactorScopeFlowTests`, `TwoFactorRequirementGate.test.tsx` | ☐ |
| SAC-07 | Give Coach *View security log* (**Roles**); the coach opens **Security access**, then calls `PUT /api/admin/security-access` directly | The menu shows the page; "View only", switches off; the call answers 403. Remove the permission afterwards | `SecurityAccessFlowTests.viewSecurityLogReadsButCannotChangeAndParticipantsAreRefused`, `SecurityAccessPage.test.tsx` | ☐ |
| SAC-08 | After the changes above, open **Security log** and the admin alert inbox | One `SECURITY_ACCESS_CHANGED` entry per save with before and after, sessions ended, dates given or cleared, and the reason; an alert email for each | `SecurityAccessServiceTest`, `TwoFactorScopeFlowTests` | ☐ |
| SAC-09 (abuse) | P1 calls `GET` and `PUT /api/admin/security-access` | 403 for both | `SecurityAccessFlowTests` | ☐ |
| SAC-10 (abuse) | Admin A saves a change with a wrong or old "Verify it's you" code | Refused; nothing changes. A change refused for stranding people does not use up the code | `SecurityAccessServiceTest.refusalsComeBeforeTheStepUpCode` | ☐ |
| SAC-11 (abuse) | With password sign-in off, P1 tries many passwords | Wrong passwords count towards the usual lock; only the right one gets the "turned off" answer, so the setting reveals nothing about which emails exist | `SecurityAccessEnforcementFlowTests.withPasswordOffARightPasswordIsRefusedPlainlyButWrongOnesStayGenericAndAdminsStillSignIn` | ☐ |
| SAC-12 (abuse) | With Google off, P2 starts Google sign-in before the switch and finishes after it (start in one tab, switch off, finish) | Refused at the code exchange or the two-step code; no session opens | `SecurityAccessEnforcementFlowTests` (code exchange); `SignInChallengeService` re-check | ☐ |
| SAC-13 (abuse) | On a server without Google set up, admin A switches password sign-in off | Refused: "Google sign-in is not set up on this server, so password sign-in must stay on." | `SecurityAccessServiceTest.refusalsComeBeforeTheStepUpCode` | ☐ |
| SAC-14 (abuse) | Admin A tries to lock admins out: switches password off (with Google on), then signs in as admin B with a password | Admin B signs in; Admins are never affected by the switches (D-24-5) | `SecurityAccessEnforcementFlowTests` | ☐ |

Also check:

- **Security access** shows the catalogue as a collapsed reference: searchable, each item marked *In VMS today*,
  *Partly in VMS* or *Not in VMS*, with no switches (SA-FR-04).
- "Last changed by … on …" appears after the first save.
- On a phone-sized screen the page and the unsaved-changes bar fit without sideways scrolling.

Khmer wording: review [km-review-v24.csv](km-review-v24.csv). Notices and alerts are in English, as in V20 to V23.
