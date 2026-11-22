# Security

How Vision Mapping protects its users' data, how to report a problem, and what
to do when something goes wrong. The requirements behind this are in
[VMS_BRD_V10.0.0.md](VMS_BRD_V10.0.0.md) (OWASP Top 10:2025).

## Reporting a vulnerability

Email the security lead (see *Contacts*) with what you found, how to reproduce
it, and what it could expose. Please do not open a public issue or test against
other people's accounts. You will get a reply within 3 working days.

## Contacts

| Role | Who | How to reach |
| --- | --- | --- |
| Security lead | To be named (BRD V10 open question) | — |
| Data Protection Officer | To be named | — |
| Hosting (Render) | Account owner | Render dashboard |

Until the security lead is named, alerts go to the application log on Render
(search for `SECURITY ALERT`). Set `SECURITY_ALERT_EMAIL` on the backend to have
them emailed as well.

## What is in place

| Area | Control |
| --- | --- |
| Sign-in | BCrypt passwords at cost 12 (`BCRYPT_COST`; production refuses to start below 12), older hashes upgraded at the next sign-in; 5 wrong passwords lock an email for 15 minutes; disabled accounts refused |
| Passwords | At least 8 characters; common and breached passwords, the account's own email and the app's name are refused (NIST 800-63B, no other composition rules) |
| Rate limits | 429 with `Retry-After` for sign-in, password reset, registration, join codes, share link openings, Excel import and AI requests (daily limit; the monthly budget cap stays) |
| Join codes | 8 characters from a 31-letter alphabet (about 40 bits); an admin can replace one; they stop working when the cohort is archived |
| Errors | Clients get a safe message and a 12-character reference; the cause, SQL and stack traces go only to the server log under the same reference |
| Failing closed | If a session cannot be checked (database down or timing out), the request is refused with 503, even on a public endpoint; a cache failure only slows the app, and cache keys always name the user |
| Timeouts | Claude 60 s; mail 10 s; database connection 5 s and query 30 s; Redis 0.5 s |
| Sessions | 30-minute access tokens in memory; rotating refresh tokens; reuse ends the session; logout, password change and reset end sessions on the server |
| Verify it's you (V20) | Changing the password, downloading all data, deleting the account, a new reminder address, and the admin changes to roles, the AI key and cohort coaches need a 6-digit code emailed to the account (a new reminder address also gets one). Codes last 10 minutes, work once, stop after 5 wrong tries, are tied to the person, session and action, and are kept only as an HMAC (`STEP_UP_SECRET`, defaulting to `JWT_SECRET`). 5 codes an hour and 20 a day per person; 10 wrong tries in an hour end the person's other sessions. Settings actions share a 10-minute window per session; delete and admin actions never do. The code is checked before the password, so these endpoints are no password oracle. A notice goes to the account email after each change. No one, an admin included, can verify for someone else or switch it off for one person; `STEP_UP_ENABLED=false` turns it off for everyone and is logged at start-up. Without a mail server in production, no code is sent, the password checks still apply, each such action is logged as `STEP_UP_SKIPPED_NO_MAIL`, and admins see a warning |
| Google sign-in (V14) | Authorization code flow with PKCE (S256) and the client secret on the server; a 10-minute, single-use `state` kept only as a hash; Google's ID token verified (RS256 against Google's keys, re-fetched when a key ID is new; issuer, audience, expiry with 2 minutes' leeway, nonce, `email_verified`) before anything in it is used; the account is found by Google's `sub`, never by email alone. An existing account is never taken over: Google is linked only after its password is confirmed once, with the normal sign-in lock. Tokens never appear in a URL: the browser gets a 60-second, single-use code in the URL fragment (a link code lasts 10 minutes). Calls to Google time out after 5 seconds and fail closed. Every step is limited per network; disabled accounts are refused. `GOOGLE_CLIENT_SECRET` is a secret like `JWT_SECRET`: set only in the Render dashboard. Accounts created with Google get a random, unknown password hash and use the "Verify it's you" code where a password would be asked (D-14-3). Because that code is their only proof, **Google sign-in stays off until codes can be sent** (mail set up and `STEP_UP_ENABLED` on, D-14-5); a start-up warning says so |
| Two-step sign-in (V21) | Optional for participants, required for admins and coaches (14 days from release or from getting the role, then every API call except setup answers `TWO_FACTOR_SETUP_REQUIRED`). Authenticator app (RFC 6238, secret sealed with `SecretCipher`, one step of drift, no code accepted twice) or, for participants, an email code; ten single-use recovery codes (HMAC only). A correct password opens no session while a second step is pending, and the sign-in failure count is reset only after the second step, so a known password cannot be used to guess codes without limit; five wrong codes end the step and count as failed sign-ins. Remembered devices last 30 days, are stored as a hash and are dropped on a password change or reset. Every change needs a fresh "Verify it's you" code and sends a notice. An admin may reset another person's two-step sign-in (never their own) with a reason and their own fresh code: the person's sessions end, they are emailed, an alert is raised. **Before a reset, confirm the person outside the app, for example by calling a phone number already on file** (BRD 21 Q-09 asks the product owner to confirm the procedure). `TWO_FACTOR_ENABLED=false` switches it off for everyone and is logged |
| Access control | Every record checked against its owner (another user's record answers 404); admin and coach routes refused by role; automatic sweep test over every route |
| Headers | CSP, HSTS, frame protection, nosniff, Referrer-Policy and Permissions-Policy on the API and the frontend |
| Input | Size limits on every request field; Excel import refuses macros, unreadable and oversized files and checks each row; formula-safe Excel export |
| AI | Records sent to Claude as escaped data that cannot pose as instructions; Claude never saves or sends on its own |
| Excel backups | The snapshot saved before each import is readable by the server's user only and deleted after 30 days (`EXCEL_BACKUP_RETENTION_DAYS`); the browser is told only its file name. Restoring one is an admin task on the server |
| Secrets | `JWT_SECRET` and `SETTINGS_ENCRYPTION_KEY` required and separate in production; the Claude key is stored encrypted (AES-256-GCM); every secret can be rotated without losing data (see *Rotating secrets*) |
| Claude replies | Each request carries a random check value Claude is told never to repeat; a reply containing it is withheld as a refusal and stops streaming (SEC-05-08, OWASP LLM07). Prompts hold only the signed-in person's records |
| Logging | Security event log (12 months, append-only) and alerts; see below |

## Supply chain

Every push and pull request to `main` runs [`.github/workflows/ci.yml`](../.github/workflows/ci.yml):

| Job | What it checks |
| --- | --- |
| Backend tests | `mvn verify`: compile, every backend test, JaCoCo coverage, and a minimum coverage check (BRD 12) |
| Frontend tests | `npm ci` (exactly the lock file), type-check, build, every frontend test with coverage, and minimum coverage thresholds (BRD 12) |
| SonarQube | Code quality on SonarQube Cloud: bugs, code smells, security hotspots, duplication and coverage, against the quality gate. Uses the two jobs' coverage reports and [`sonar-project.properties`](../sonar-project.properties). Skipped when the `SONAR_TOKEN` secret is not available (Dependabot and fork pull requests) |
| Security scan | Builds the backend image and scans it with Trivy (operating system packages and the Java libraries in the app); scans the frontend lock file including build tools; `npm audit` on what ships to browsers; on `main`, saves a CycloneDX software bill of materials as a build artifact for 90 days |

The scan fails the build on any **High or Critical** issue that has a fix. It
also runs every Monday, so a new advisory against an unchanged dependency is
caught. Render deploys a commit only after all checks pass
(`autoDeployTrigger: checksPass` in `render.yaml`).

Other controls:

- **Pinned versions.** GitHub Actions are pinned to commit SHAs, Docker base
  images to version and digest, Trivy to version and digest, and npm packages
  through `package-lock.json`. Maven uses exact versions.
- **Maven Central only.** `backend/maven-settings.xml` sends every Maven request
  to Maven Central, even when a library's POM lists other repositories.
- **Non-root container.** The backend runs as the `app` user.
- **Dependabot** opens grouped update pull requests every Monday for Maven, npm,
  Docker images and GitHub Actions, plus security updates as they appear.

### SonarQube Cloud

One-time setup, by the repository owner:

1. Sign in at [sonarcloud.io](https://sonarcloud.io) with GitHub and import
   `Sot-Sirymony/vmap-sys`. If the organization or project key it gives differ
   from `sot-sirymony` and `Sot-Sirymony_vmap-sys`, change them in
   `sonar-project.properties`.
2. In the project, open **Administration → Analysis Method** and turn off
   **Automatic Analysis**. CI runs the analysis instead, with coverage.
3. Create a token (**My Account → Security**) and add it to the GitHub repo as
   the Actions secret `SONAR_TOKEN`.
4. After the first scan, add **SonarQube** as a required check on `main`
   alongside the other three, so a pull request that fails the quality gate
   cannot be merged.

To see coverage locally: `mvn verify` in `backend/` writes
`target/site/jacoco/index.html`, and `npm run test:coverage` in `frontend/`
writes `coverage/lcov-report/index.html`.

### Test coverage as a control (BRD 12)

Coverage on `main` is 85.1% for the whole project (backend 88.1%, frontend
83.3%). Both CI jobs fail below their minimums (backend lines 94%, branches
75%; frontend lines 85%, statements 84%, branches 78%, functions 78%), so a
change cannot quietly remove the tests that guard it. The tests that matter
most for security:

| Control | Tests |
| --- | --- |
| Another user's record answers 404; no token answers 401 (BR-13, SEC-01) | `AccessControlSweepTests` (every route), `CoreResourceLifecycleFlowTests`, `SupportingResourceFlowTests` |
| Admin and coach routes refused by role; a coach may read, never change (D-6) | `CoachingFlowTests`, `SupportingResourceFlowTests`, `CoachParticipantPage.test.tsx`, `CohortsPage.test.tsx` |
| Journals reach a coach only when shared (D-6) | `CoachingFlowTests`, `CoachParticipantPage.test.tsx` |
| What goes to Claude: the participant's own records only, never a private conflict note, wrapped so text cannot pose as instructions (AI-NFR-04, AI-NFR-13, SEC-05-07) | `AiContextBuilderTest`, `ParticipantDataBlockTest` |
| AI controls hidden while AI is off (AI-NFR-02) | `PitchCoachCard.test.tsx`, `CommunicationBuilderPage.test.tsx`, `ReviewsPage.test.tsx` |
| Crisis wording always gets the help lines (AI-NFR-09) | `AiSafetyTest`, `DreamCoachFlowTests` |
| A password reset link is logged only on local and test runs (SEC-09-07) | `MailersTest` |
| Excel export never carries formulas or private notes | `ExcelSecurityFlowTests`, `ExcelImportFlowTests` |

No test calls the Claude API, Google or a mail server: they are mocked, or
run against local stand-ins (`ClaudeStubServer`, GreenMail). Both suites were
run with outside network access blocked and passed (BRD 12 CAC-07).

### Update deadlines (SEC-03-03)

| Severity | Fix within |
| --- | --- |
| Critical | 7 days |
| High | 30 days |
| Medium and low | next monthly review |

Review Dependabot pull requests at least monthly. If a fix is not possible in
time, add the finding to [`.trivyignore.yaml`](../.trivyignore.yaml) with a
reason and an `expired_at` date no more than 30 days away. Current exceptions:
two Spring MVC advisories fixed only in Spring Boot 4 and not reachable here
(see the file), until 7 Dec 2026.

### Adding a dependency (SEC-03-06)

Before adding a library, check that it:

1. is actively maintained (a release in the last year, issues answered);
2. is widely used, or comes from an organisation you trust;
3. has a licence compatible with the project;
4. is really needed: prefer the standard library or an existing dependency.

### Repository settings (do these once, in GitHub)

These cannot be set from the code; the repository owner sets them:

1. **Protect `main`** (SEC-08-03): Settings → Branches (or Rules → Rulesets) →
   add a rule for `main`: require a pull request before merging, require status
   checks to pass (`Backend tests`, `Frontend tests`, `Security scan`), block
   force pushes and deletion.
2. **Two-factor authentication** (SEC-03-07): every maintainer turns it on for
   their GitHub account and their Render account.
3. **Dependabot alerts and security updates**: Settings → Code security → turn
   both on.

## ZAP baseline scan (SAC-06, SAC-17)

`.github/workflows/zap-baseline.yml` runs the OWASP ZAP baseline scan against production every Tuesday at 04:00 UTC,
and on demand (GitHub → Actions → **ZAP baseline** → **Run workflow**). It scans the frontend and the API separately.

- **Passive only:** ZAP's spider visits pages for about a minute and ZAP reads the answers (headers, cookies, content).
  It sends no attack payloads and signs in to nothing, so it is safe to run against production. Do not switch it to
  the full (active) scan against production; run that only against a test deployment.
- **Pass or fail:** the job fails when a Medium or High finding remains. Low and informational findings are in the
  report but do not fail it.
- **Reports:** each run keeps `zap-frontend` and `zap-api` artifacts (HTML, Markdown and JSON).

### Scanner exceptions

A finding that is accepted rather than fixed is listed in `.zap/accepted.tsv` as `<plugin id> <finding name> <reason>`
(one finding; preferred, so a new finding of the same rule still fails), or in `.zap/rules.tsv` as
`<plugin id> IGNORE <reason>` (a whole rule). Accepted findings still show on each run as warnings. Each one needs the security lead's (until named, the product owner's) agreement, a reason a reviewer can check, and a line in the
ASVS check. Review the list at each release; remove an entry as soon as its cause is fixed.

Accepted on 22 Nov 2026 by the product owner (first scan): rule 10055 **CSP: style-src unsafe-inline** (MUI/Emotion and
inline style attributes; scripts stay strict) and **CSP: Wildcard Directive** (`img-src https:`, because dream pictures
are links people paste; proxying them would break SEC-01-09).

## Threat model, ASVS check and abuse cases

- [THREAT_MODEL.md](THREAT_MODEL.md) (SEC-06-01): data flows, trust boundaries, threats and abuse cases for sign-in and
  recovery, coach access, share links, Excel import and Claude. **Update it whenever a feature crosses one of its trust
  boundaries.**
- [ASVS_L2_CHECK.md](ASVS_L2_CHECK.md): the OWASP ASVS 5.0 Level 2 check, with findings F-01 to F-08 and their
  decisions. Repeat it for each major version.
- **Every BRD's acceptance criteria include at least one abuse case** ("a user tries to …", SEC-06-04, BRD 21
  TF-FR-46). BRD 21 is the first to follow it (TFC-14 to TFC-20).

## The security log

Every sign-in (success, failure, lock), logout, password change and reset,
refused request (403, another user's record, rate limit, invalid token), role
change, coach view of a participant, share link (created, opened, stopped),
Excel import and export, account export and deletion, and admin change is
written to the `security_events` table.

Each row has the time (UTC), the account number, the network address, the event,
the target record and the outcome. It never holds a password, token, API key,
email address, or the text of a dream, journal or message: a failed sign-in for
an email keeps only a SHA-256 of the address.

Admins read it under **Settings → Security log**. Nobody can change or delete a
row through the app; rows older than `SECURITY_EVENTS_RETENTION_DAYS` (365) are
removed by a daily job. When an account is deleted, its rows stay (only its
account number is in them).

### Alerts

Raised at once, at most once an hour for the same thing:

- 10 failed sign-ins for one email within 15 minutes;
- 50 failed sign-ins from one network within 15 minutes;
- 50 failed Google sign-ins (callbacks and code exchanges) from one network within 15 minutes (V14);
- 10 failed two-step codes for one email, or 50 from one network, within 15 minutes; at once: an admin reset, an admin or
  coach turning it off, and a recovery code used on an admin account (V21);
- 20 refused requests by one user within 15 minutes;
- a refresh token used again after it was replaced (likely theft);
- the admin role granted;
- the Claude API key changed or removed;
- AI spending (checked every 15 minutes, each warning once): 80% and 100% of the monthly budget, and an hour costing
  at least $1 and 5 times the average hour of the week before (a bug, a stuck loop or abuse). Each is also an
  `AI_BUDGET_WARNING` row in the security log.

The numbers are environment variables (`SECURITY_ALERT_*` and `AI_BUDGET_*`, see the README).

## Incident response

Follow these steps in order. Write down what you do and when, as you go.

### 1. Confirm and assess (first hour)

1. Read the alert, then the security log (Settings → Security log, or the
   `security_events` table) around that time: which accounts, which network,
   which events.
2. Decide what may have been exposed: one account, several, or the whole
   database or a secret.
3. Tell the security lead and the product owner.

### 2. Contain

| Situation | Action |
| --- | --- |
| One account taken over | Disable it (`app_users.status = 'DISABLED'`); every request with its tokens is refused at once. Ask the owner to reset the password, then re-enable. |
| Many accounts, or unsure | End every session: `UPDATE auth_sessions SET revoked_at = now(), revoked_reason = 'INCIDENT' WHERE revoked_at IS NULL;` Everyone signs in again. |
| `JWT_SECRET` leaked | Generate a new value in Render and redeploy. All access tokens stop working; also end every session as above. |
| `SETTINGS_ENCRYPTION_KEY` leaked | Rotate it as in *Rotating secrets* below (old value in `SETTINGS_ENCRYPTION_KEY_PREVIOUS`, wait for the re-seal log line, then remove it). Do **not** simply replace it: every authenticator secret and the Claude key would become unreadable. Rotate the Claude key at Anthropic too. |
| `TWO_FACTOR_SECRET` leaked | Set a new value **without** `TWO_FACTOR_SECRET_PREVIOUS`. Recovery codes stop working (authenticator apps are not affected); ask admins and coaches to make new recovery codes on the Security page. |
| Claude API key leaked | Revoke it in the Anthropic console, enter a new one under Settings → AI settings. |
| Database password leaked | Rotate it in Render (the backend reads it from the Blueprint), then rotate `JWT_SECRET` and end all sessions. |
| A dependency vulnerability is being exploited | Upgrade or remove it, redeploy; if a fix is not available, turn off the affected feature. |

### Rotating secrets (SEC-04-04)

Planned rotation (for example yearly, or when someone with access leaves). Generate each new value with
`openssl rand -base64 48` and set it in the Render dashboard (backend service → Environment), then redeploy.

| Secret | What it protects | How to rotate without losing data |
| --- | --- | --- |
| `JWT_SECRET` | Signs 30-minute access tokens | Replace it. Access tokens stop working, and the app renews them at once with the refresh token (refresh tokens are stored as plain SHA-256, not keyed), so nobody is signed out. |
| `SETTINGS_ENCRYPTION_KEY` | Seals the Claude key and every authenticator secret | 1. Copy the current value into `SETTINGS_ENCRYPTION_KEY_PREVIOUS`. 2. Set a new `SETTINGS_ENCRYPTION_KEY` (32+ characters, not equal to `JWT_SECRET`). 3. Redeploy; the log says "Re-sealed N stored secrets with the current SETTINGS_ENCRYPTION_KEY" (`SecretResealJob`). 4. Remove `SETTINGS_ENCRYPTION_KEY_PREVIOUS` and redeploy. |
| `TWO_FACTOR_SECRET` | HMAC of recovery codes and emailed sign-in codes | 1. Copy the current value into `TWO_FACTOR_SECRET_PREVIOUS`. 2. Set a new `TWO_FACTOR_SECRET`. Old recovery codes keep working; new ones use the new secret. 3. Remove the previous value once people have made new recovery codes (it cannot be re-hashed, because only the hash is stored). |
| `STEP_UP_SECRET` | HMAC of "Verify it's you" codes (10 minutes) | Replace it. Codes already sent stop working; people ask for a new one. |
| `GOOGLE_CLIENT_SECRET` | Google sign-in | Add a new secret in Google Cloud, set it in Render, redeploy, then delete the old one in Google Cloud. |
| Database password | PostgreSQL | Rotate it in Render; the backend reads it from the Blueprint. |
| Claude API key | AI features | Create a new key at Anthropic, enter it under Settings → AI settings, then revoke the old one. |

### Hardening checklist (SEC-02-08)

Check at every release; record the date and who checked in the release notes. Values live in the Render dashboard and
the local `docs/production_env.md`, which git ignores and must never be committed.

| Check | Local | Test (CI) | Production |
| --- | --- | --- | --- |
| Profile | `local` | `test` | `prod` |
| `JWT_SECRET`, `SETTINGS_ENCRYPTION_KEY`, `STEP_UP_SECRET`, `TWO_FACTOR_SECRET` | Dev defaults allowed | Test values | Set, random, all different; the app refuses to start otherwise |
| `*_PREVIOUS` secrets | Unset | Unset | Unset, except during a rotation |
| `BCRYPT_COST` | Any (4+) | 5 | 12 or more (the app refuses less) |
| `CORS_ALLOWED_ORIGINS` | Local dev servers (default) | n/a | Only the production frontend origin |
| Mail (`MAIL_HOST`) and `SECURITY_ALERT_EMAIL` | Optional (links go to the log) | Off | Set; alerts reach a person |
| `CACHE_TYPE` / Redis | Optional | `simple` | `redis` (shared rate-limit counts, F-04) |
| H2 console, devtools, API docs | Allowed | n/a | Off |
| HTTPS, HSTS, CSP and other headers | n/a | Header tests | Confirmed by the weekly ZAP baseline scan |
| Dependencies | n/a | Trivy, Dependabot, SonarQube pass | Deployed only from `main` after checks pass |
| Backups | n/a | n/a | Excel snapshots deleted after 30 days; database backups per the Render plan |

### 3. Investigate

Keep the security log rows and Render logs for the period. Find how access was
gained and close that path before restoring normal service.

### 4. Notify

If personal data may have been exposed, the Data Protection Officer decides who
must be told and when, under the data protection law that applies (BRD V10 open
question). Tell affected users plainly: what happened, what data, what we did,
and what they should do (for example, change a reused password).

### 5. Learn

Within two weeks, write a short review: timeline, cause, what worked, what to
change. Add a test or a control so the same thing cannot happen again.
