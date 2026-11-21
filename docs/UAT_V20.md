# UAT checklist – Verify it's you (V20)

For the BRD 20.0.0 acceptance criteria (SVC-01 to SVC-16). Use a server with mail set up (`MAIL_HOST`), a participant
account whose email you can read, a second browser, and an admin account. Tick each line and note anything that differs.

| ID | Steps | Expected | Automated test | Result |
| --- | --- | --- | --- | --- |
| SVC-01 | Account → **Download my data** | **Verify it's you** opens; one email with a 6-digit code in the subject; the address is masked; typing the code starts the download | `StepUpFlowTests`, `useStepUp.test.tsx` | ☐ |
| SVC-02 | Call `GET /api/account/export` without `X-Step-Up`, or with a code for another action, browser or account | `403` `STEP_UP_REQUIRED` (another person's or browser's code: `404`) | `StepUpFlowTests`, `StepUpServiceTest` | ☐ |
| SVC-03 | Type a wrong code five times; wait 10 minutes with another code | "N tries left" each time, then "Ask for a new code"; the old code no longer works | `StepUpFlowTests` | ☐ |
| SVC-04 | Press **Send a new code** straight away; ask for six codes within an hour | Disabled with a 60-second countdown; the sixth answers "You have asked for 5 codes in the last hour" | `StepUpFlowTests`, `useStepUp.test.tsx` | ☐ |
| SVC-05 | Verify for **Download my data**, then change the password within 10 minutes | Current password asked, no new code; after 10 minutes a code is asked again | `StepUpFlowTests` | ☐ |
| SVC-06 | Delete the account right after another verification; type `Delete` | A fresh code, the password and the word are all needed; `Delete`, ` delete ` and `DELETE` enable the button; another word shows "Type DELETE to enable the button" | `StepUpFlowTests`, `AccountSettingsPage.test.tsx` | ☐ |
| SVC-07 | Change the reminder email to a new address | A code to the account email, then one to the new address; saved only after both; no **Send test email** before; a notice to the account email and the old address | `StepUpFlowTests`, `AccountNoticeFlowTests`, `ReminderSmtpDeliveryTests` | ☐ |
| SVC-08 | Change the password while signed in on the second browser | The second browser is signed out; every verified window closes; a "password was changed" notice arrives | `StepUpFlowTests`, `AccountNoticeFlowTests` | ☐ |
| SVC-09 | Read a code email, in English and with the app in Khmer | Code in the subject; names the action; no link; Khmer for a Khmer request | `StepUpMailerTest` | ☐ English ☐ Khmer |
| SVC-10 | Run locally without `MAIL_HOST`; then a production server without it | Local: the code is in the backend log. Production: the dialog explains that email is not set up, the action continues with the password check, the log shows `STEP_UP_SKIPPED_NO_MAIL`, admins see the mail warning | `StepUpMailerTest`, `StepUpServiceTest`, `StepUpMailWarning.test.tsx` | ☐ Local ☐ Production |
| SVC-11 | Stop the mail server and ask for a code | "We couldn't send the code. Try again in a few minutes."; nothing changes | `StepUpFlowTests` | ☐ |
| SVC-12 | As admin, save the AI key twice; make a participant a coach; change a cohort's coach | A fresh code every time; the same code does not work twice | `StepUpFlowTests`, `useStepUp.test.tsx` | ☐ |
| SVC-13 | Look at `step_up_challenges` and the production log after a few codes | Only 64-character hashes; no code in the log or the security log | `StepUpFlowTests`, `StepUpCodesTest` | ☐ |
| SVC-14 | Delete an account that has codes; download data from another | Its codes are gone; the export holds no codes | `AccountAndProgressFlowTests` (delete order) | ☐ |
| SVC-15 | Use the dialog with the keyboard only, a screen reader, an iPhone and an Android phone, at 320 px | Focus on the code box, errors announced, the phone offers the code from the email, everything fits | `useStepUp.test.tsx` (focus, `one-time-code`, `inputmode`); the rest is manual | ☐ |
| SVC-16 | Check CI on the release commit | Backend, frontend, SonarQube and security jobs pass, coverage minimums met | CI | ☐ |

Known gap: change notices and the many-wrong-codes notice are in English until the app stores each person's language.

Khmer wording: review [km-review-v20.csv](km-review-v20.csv) (fill the "Reviewed Khmer" column where a draft is wrong).
