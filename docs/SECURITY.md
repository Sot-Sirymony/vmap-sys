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
| Sign-in | BCrypt passwords; 5 wrong passwords lock an email for 15 minutes; disabled accounts refused |
| Sessions | 30-minute access tokens in memory; rotating refresh tokens; reuse ends the session; logout, password change and reset end sessions on the server |
| Access control | Every record checked against its owner (another user's record answers 404); admin and coach routes refused by role; automatic sweep test over every route |
| Headers | CSP, HSTS, frame protection, nosniff, Referrer-Policy and Permissions-Policy on the API and the frontend |
| Input | Size limits on every request field; Excel import refuses macros, unreadable and oversized files and checks each row; formula-safe Excel export |
| AI | Records sent to Claude as escaped data that cannot pose as instructions; Claude never saves or sends on its own |
| Secrets | `JWT_SECRET` and `SETTINGS_ENCRYPTION_KEY` required and separate in production; the Claude key is stored encrypted (AES-256-GCM) |
| Logging | Security event log (12 months, append-only) and alerts; see below |

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
- 20 refused requests by one user within 15 minutes;
- a refresh token used again after it was replaced (likely theft);
- the admin role granted;
- the Claude API key changed or removed.

The numbers are environment variables (`SECURITY_ALERT_*`, see the README).

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
| `SETTINGS_ENCRYPTION_KEY` leaked | Generate a new value and redeploy, then have an admin enter the Claude key again (the old encrypted value cannot be read with the new key). Rotate the Claude key at Anthropic too. |
| Claude API key leaked | Revoke it in the Anthropic console, enter a new one under Settings → AI settings. |
| Database password leaked | Rotate it in Render (the backend reads it from the Blueprint), then rotate `JWT_SECRET` and end all sessions. |
| A dependency vulnerability is being exploited | Upgrade or remove it, redeploy; if a fix is not available, turn off the affected feature. |

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
