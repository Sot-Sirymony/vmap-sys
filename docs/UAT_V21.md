# UAT checklist – Two-step sign-in (V21)

For the BRD 21.0.0 acceptance criteria (TFC-01 to TFC-22). Use a server with mail set up (`MAIL_HOST`), a phone with
an authenticator app (Google Authenticator, Microsoft Authenticator or similar), a participant account, a coach
account, two admin accounts and a second browser. Tick each line and note anything that differs.

| ID | Steps | Expected | Automated test | Result |
| --- | --- | --- | --- | --- |
| TFC-01 | Participant: **Security → Two-step sign-in → Set up an authenticator app**; scan the QR code; type the code; save the recovery codes; sign out and in | A "Verify it's you" code first; QR code and key; ten codes with Download and Copy; **Done** only after the tick; sign-in then asks for the app's code | `TwoFactorSettingsFlowTests`, `TwoFactorCard.test.tsx` | ☐ |
| TFC-02 | Sign in with an account that never turned it on | Exactly as before: no second step | `TwoFactorSignInFlowTests` | ☐ |
| TFC-03 | Participant: **Use email codes instead**; sign in on the second browser | A code by email; signed in after typing it; **Send a new code** after 60 seconds | `TwoFactorSignInFlowTests` | ☐ |
| TFC-04 | Tick **Remember this device for 30 days**; sign out and in; then change the password, or remove the device on the Security page | No second step on that browser; asked again after the password change or removal | `TwoFactorSignInFlowTests`, `TwoFactorSettingsFlowTests` | ☐ |
| TFC-05 | Sign in with **Use a recovery code**; try the same code again | Signed in; "9 left"; an email notice; the same code refused | `TwoFactorSignInFlowTests` | ☐ |
| TFC-06 | With two-step sign-in on, **Continue with Google** | The second step appears before you are signed in | `GoogleSignInOutcomeFlowTests` | ☐ |
| TFC-07 | Coach without two-step sign-in: sign in before the date; then set the date to the past and sign in | "Set up now / Skip for now (N days left)"; past the date, only the Security page works | `TwoFactorRequirementFlowTests`, `TwoFactorRequirementGate.test.tsx` | ☐ |
| TFC-08 | Admin: open the panel | No email choice; no **Turn off** past the date | `TwoFactorSettingsFlowTests` | ☐ |
| TFC-09 | Admin A: **Two-step sign-in** page → **Reset** a coach, with a reason and A's own code; Admin A tries to reset A | The coach is signed out everywhere and emailed; the security log shows the reason; an alert; 3 days to set up again; A cannot reset A | `TwoFactorRequirementFlowTests`, `TwoFactorAdminPage.test.tsx` | ☐ |
| TFC-10 | Turn on, switch method, make new codes and turn off | Each asks for a fresh code and sends a notice | `TwoFactorStepUpFlowTests` | ☐ |
| TFC-11 | Use **Forgot password**, then sign in | Two-step sign-in is still asked; remembered devices are forgotten | `TwoFactorSignInFlowTests` | ☐ |
| TFC-12 | **Download my data**; delete an account that uses it | The export shows status and devices but no key or code; deletion removes every row | `TwoFactorRequirementFlowTests`, `TwoFactorDataFlowTests` | ☐ |
| TFC-13 | (Automated) RFC 6238 vectors | Pass | `TotpCodesTest` | ☐ |
| TFC-14 (abuse) | Sign in with the right password but no code; call any API with only the challenge token | No session; every protected call answers 401 | `TwoFactorSignInFlowTests` | ☐ |
| TFC-15 (abuse) | Use one app code twice within 30 seconds | The second use is refused | `TwoFactorSignInFlowTests`, `TotpCodesTest` | ☐ |
| TFC-16 (abuse) | Type five wrong codes, then sign in again | The step ends; the email is locked for 15 minutes | `TwoFactorSignInFlowTests` | ☐ |
| TFC-17 (abuse) | Use another account's code, or an old challenge | Refused; an old challenge answers "This sign-in step has ended" | `TwoFactorSignInFlowTests` | ☐ |
| TFC-18 (abuse) | Copy the `vms.deviceToken` value into another account's sign-in | The second step is still asked | `TwoFactorSignInFlowTests` | ☐ |
| TFC-19 (abuse) | Coach past the date calls `GET /api/vision-areas` directly | `403` `TWO_FACTOR_SETUP_REQUIRED` | `TwoFactorRequirementFlowTests` | ☐ |
| TFC-20 (abuse) | On a participant's open session, call `POST /api/account/two-factor/disable` without a code | `403` `STEP_UP_REQUIRED`; it stays on | `TwoFactorStepUpFlowTests` | ☐ |
| TFC-21 | Read `THREAT_MODEL.md` and `ASVS_L2_CHECK.md` | Present; every "Not met" fixed or accepted (Phase 5) | – | ☐ |
| TFC-22 | Check CI on the release commit | Backend, frontend, SonarQube and security jobs pass | CI | ☐ |

Also check: in **Verify it's you**, an authenticator user can type the app's code instead of the emailed one
(TF-FR-40, `TwoFactorStepUpFlowTests`).

Khmer wording: review [km-review-v21.csv](km-review-v21.csv). Sign-in code emails and notices are in English until
the app stores each person's language (as in V20).
