# OWASP ASVS 5.0 Level 2 check

BRD 10 (security standard: OWASP Top 10:2025 with ASVS 5.0 Level 2 as the checklist) and BRD 21 Phase 5 (TF-FR-44,
TF-FR-45), 22 Nov 2026, at the code of V21 Phase 5.

**How to read this.** Rows are grouped by the ASVS 5.0 chapters (V1 to V17) and describe each Level 1 and Level 2
control area in plain words, with the evidence in the code or tests. Status: **Met**, **Partly met** (the gap is a
finding below), **Not met** (finding below), or **N/A**. ASVS requirement numbers are not repeated here. At sign-off,
the security reviewer ticks each official ASVS 5.0 L1/L2 requirement
(<https://github.com/OWASP/ASVS/tree/master/5.0/en>) against the row that covers it and adds any requirement a row
misses as a new finding.

## Summary

| Status | Rows |
| --- | --- |
| Met | 70 |
| Partly met | 5 (F-02, F-03, F-04 twice, F-05) |
| Partly met, fixed in this phase | 1 (F-08) |
| Not met | 2 (F-01, F-07) |
| N/A | 4 |

Findings F-06 and F-08 were found and fixed in this phase. Every other finding is either an accepted risk waiting for
the product owner's signature, or a follow-up with a date (TF-FR-45).

## V1 Encoding and Sanitization

| Control area | Status | Evidence |
| --- | --- | --- |
| Output encoding in the browser | Met | React escapes all output; no `dangerouslySetInnerHTML` in the app |
| SQL and query injection | Met | Spring Data JPA with bound parameters; the only built SQL (account deletion) uses fixed table names from a constant list |
| Spreadsheet formula injection | Met | `ExcelService` writes values starting with `= + - @` tab or CR as text (SEC-05-04) |
| Email content injection | Met | Plain-text emails; names and codes inserted as text, no links in code emails |
| Prompt injection into Claude | Met | Records sent as escaped data blocks; Claude has no tools that change data |
| Unsafe deserialisation | Met | Jackson without default typing; no Java serialisation of request data |

## V2 Validation and Business Logic

| Control area | Status | Evidence |
| --- | --- | --- |
| Input validated on the server, with size limits | Met | Bean Validation on every request DTO (`@Size`, `@NotBlank`, enums); access sweep test checks request bodies cannot set owner or role fields |
| Business rules enforced on the server | Met | Status, blocker, progress and completion rules in services (CLAUDE.md business rules) with unit tests |
| Anti-automation on costly or sensitive flows | Met | Rate limits for sign-in, reset, registration, join codes, share links, import, report export, AI, Google steps, second-step codes and sign-in code emails |
| Sensitive actions need re-authentication | Met | V20 "Verify it's you" for password, export, delete, reminder email, two-step settings and admin changes |

## V3 Web Frontend Security

| Control area | Status | Evidence |
| --- | --- | --- |
| Content Security Policy | Met | Frontend `script-src 'self'` (no inline script); API `default-src 'none'` |
| Clickjacking | Met | `frame-ancestors 'none'` and `X-Frame-Options: DENY` |
| Other security headers | Met | HSTS (1 year, subdomains), `nosniff`, `Referrer-Policy`, `Permissions-Policy` |
| Session tokens kept out of script reach | Partly met | Access token in memory only; refresh and device tokens in browser storage (F-03) |
| Cookies | N/A | The app sets no cookies |
| Cross-origin requests | Met | CORS allow-list from `CORS_ALLOWED_ORIGINS`; no credentials mode |

## V4 API and Web Service

| Control area | Status | Evidence |
| --- | --- | --- |
| Correct HTTP methods and content types | Met | JSON APIs; GET for reads; state changes on POST/PUT/PATCH/DELETE; public endpoints read-only except sign-in |
| CSRF | N/A | Bearer tokens in a header, no cookies |
| API description not public | Met | Swagger off in production (`SWAGGER_ENABLED`) |
| Error answers | Met | Safe message plus a 12-character reference; details only in the server log |

## V5 File Handling

| Control area | Status | Evidence |
| --- | --- | --- |
| Upload size and type limits | Met | 10 MB; `.xlsx` only; macro workbooks refused |
| Archive bombs | Met | `ZipSecureFile` inflate ratio and entry size limits |
| Uploaded files never executed or served back | Met | Rows are read and the file is discarded; the original workbook is never written |
| Downloads | Met | Generated files with safe names and `Content-Disposition`; formula-safe |

## V6 Authentication

| Control area | Status | Evidence |
| --- | --- | --- |
| Password rules | Met | At least 8, up to 100 characters; common and breached passwords, the email and the app name refused; no composition rules (NIST 800-63B) |
| Password storage | Met | BCrypt |
| Brute-force protection | Met | 5 failures per email, 50 per network, 15-minute lock; second-step failures count too |
| No user enumeration on sign-in and reset | Met | Same answer for unknown email and wrong password; reset always 204 |
| No user enumeration on registration | Partly met | "Email is already registered" (F-02, accepted risk candidate; rate limited) |
| Multi-factor authentication | Met | V21: authenticator (RFC 6238) or email code; required for admins and coaches |
| Recovery codes and lost-device path | Met | Ten single-use codes (HMAC); admin reset with reason, own code, alert and notice |
| Password reset | Met | 256-bit single-use token, 30 minutes, SHA-256 stored; ends all sessions; does not turn off 2FA |
| Email ownership confirmed | Not met | Not confirmed at sign-up (F-01) |
| Third-party sign-in | Met | V14 Google with PKCE, nonce and ID-token checks; no takeover by email alone |
| Notifications of security changes | Met | Notices after password change, export, reminder email, deletion, two-step changes and recovery code use |

## V7 Session Management

| Control area | Status | Evidence |
| --- | --- | --- |
| Server-side sessions that can be ended | Met | `auth_sessions`; every request checks the session is open |
| Token rotation and theft detection | Met | Refresh token rotated on use; reuse ends the session and alerts |
| Sessions end on sign-out, password change, reset and admin reset | Met | `AuthSessionService` |
| Absolute and idle timeouts | Partly met | 30-minute access token, 12-hour tab session, 30-day "remember me"; no idle timeout on remembered sessions (F-05) |
| Re-authentication before sensitive actions | Met | V20 step-up |
| See and end other sessions | Not met | No session list in the app (F-07) |

## V8 Authorization

| Control area | Status | Evidence |
| --- | --- | --- |
| Deny by default | Met | `anyRequest().authenticated()`; public list kept in one place and in the sweep test |
| Object-level access (another person's record) | Met | Owner checks in services; another person's record answers 404; `AccessControlSweepTests` over every route |
| Function-level access (roles) | Met | Path rules for `/api/admin/**` and `/api/coach/**`, checked again in services; role read from the database each request |
| Coach access limited to their cohorts, read-only | Met | Business rule 14; coach views logged |
| Admin actions protected | Met | Fresh step-up code; logged; alerts for sensitive ones |

## V9 Self-contained Tokens

| Control area | Status | Evidence |
| --- | --- | --- |
| Signature and algorithm fixed | Met | HS256 only (jjwt 0.12); key from `JWT_SECRET`, at least 256 bits |
| Expiry and session binding | Met | 30 minutes; the token names its session, which must still be open |
| Keys for different purposes | Partly met → fixed | Step-up and two-step hashes fell back to `JWT_SECRET` in production (F-08, fixed in `render.yaml`) |

## V10 OAuth and OIDC

| Control area | Status | Evidence |
| --- | --- | --- |
| Authorization code with PKCE, client secret on the server | Met | V14 GA-SEC-01 |
| `state` and `nonce` | Met | Single-use hashed state; nonce checked in the ID token |
| ID token validation | Met | Signature (Google keys, rotation handled), issuer, audience, expiry, `email_verified` |
| Exact redirect URIs, no open redirect | Met | Registered callback; in-app return paths only |
| Tokens never in URLs | Met | One-time code in the fragment; `no-referrer` |
| Acting as an OAuth server | N/A | The app is only a client |

## V11 Cryptography

| Control area | Status | Evidence |
| --- | --- | --- |
| Approved algorithms | Met | BCrypt, AES-256-GCM, HMAC-SHA256, SHA-256, RS256 (Google), HMAC-SHA1 only inside TOTP as RFC 6238 requires |
| Secure random | Met | `SecureRandom` for tokens, codes, secrets and recovery codes |
| Secrets at rest | Met | Claude key, PKCE verifier and authenticator secrets sealed with `SETTINGS_ENCRYPTION_KEY` |
| Constant-time comparison of secrets | Met | `MessageDigest.isEqual` for codes and hashes |
| Key management | Met | Keys in environment variables; production refuses weak or shared keys (SEC-04-03) |

## V12 Secure Communication

| Control area | Status | Evidence |
| --- | --- | --- |
| TLS for all client traffic | Met | Render terminates TLS; HSTS on every response |
| TLS to outside services | Met | Anthropic, Google and SMTP (STARTTLS) over TLS |
| Database and cache traffic | Partly met | Render private network; TLS on the internal connections is not configured (F-04) |

## V13 Configuration

| Control area | Status | Evidence |
| --- | --- | --- |
| No debug or admin surfaces in production | Met | No actuator; Swagger off; error pages without stack traces |
| Secrets not in source | Met | `sync: false` or generated in `render.yaml`; scanned by the CI security job |
| Dependencies kept up to date and scanned | Met | Trivy (image and Java libraries), `npm audit`, update deadlines in `SECURITY.md`, pinned versions |
| Least functionality | Met | Feature switches for Google, step-up and two-step sign-in are logged at start-up |

## V14 Data Protection

| Control area | Status | Evidence |
| --- | --- | --- |
| Sensitive answers not cached | Met | Every API answer `Cache-Control: no-store` |
| Personal data in logs | Met | Emails masked (`LogMasks`); no codes, tokens or passwords logged |
| Data export and deletion | Met | Full export (step-up); permanent deletion of every table with a user id (test checks the list) |
| Data sent to outside services only with consent | Met | Claude only after AI consent |
| Data minimisation | Met | Google: `openid email profile` only; no network address in notices |

## V15 Secure Coding and Architecture

| Control area | Status | Evidence |
| --- | --- | --- |
| Threat model kept up to date | Met | `docs/THREAT_MODEL.md` (this phase) |
| Secure coding rules enforced | Met | BRD 13 rules (`EffectiveJavaRulesTest`), SonarQube quality gate, coverage minimums |
| Concurrency on single-use secrets | Met | Conditional updates ("mark used where not used") for state, codes, challenges and recovery codes |
| Components separated by trust | Met | Controllers, services with owner checks, repositories; public endpoints listed in one place |

## V16 Security Logging and Error Handling

| Control area | Status | Evidence |
| --- | --- | --- |
| Security events logged with who, what, when and outcome | Met | `security_events` (sign-in, two-step, changes, refusals, admin actions), 12 months |
| Logs protected and free of secrets | Met | Append-only table; no secrets in details |
| Alerts | Met | `SecurityAlertService` (sign-in failures, refusals, token reuse, admin changes, two-step resets) |
| Rate-limit and lockout state | Partly met | In memory on one instance (F-04) |
| Errors fail closed | Met | Session check failure answers 503; Google and mail failures refuse cleanly |

## V17 WebRTC

| Control area | Status | Evidence |
| --- | --- | --- |
| All | N/A | No WebRTC |

## Findings

| ID | Finding | Status | Decision |
| --- | --- | --- | --- |
| F-01 | Email address not confirmed at sign-up, so step-up and sign-in codes may go to an address the person does not own | Not met | Follow-up: the separate small BRD agreed in BRD 20 Q-06. **Target: written by 31 Jan 2027** |
| F-02 | Registration reveals that an email is registered | Partly met | **Accepted risk candidate**: clear feedback at sign-up; 10 registrations per network per hour. Product owner to sign |
| F-03 | Refresh and device tokens in browser storage are readable by script in the page | Partly met | **Accepted risk candidate**: HttpOnly cookies would be third-party (frontend and API on different domains); mitigated by `script-src 'self'`, no inline script, React escaping, rotation and reuse detection. Revisit if the frontend and API move under one domain. Product owner to sign |
| F-04 | Rate-limit and lockout counts are in memory on one instance; internal database and cache connections rely on Render's private network without TLS | Partly met | **Accepted risk candidate** while the backend runs as one Render instance; revisit before scaling out (move counts to Redis, enable TLS). Product owner to sign |
| F-05 | "Remember me" sessions last 30 days without an idle timeout | Partly met | **Accepted risk candidate**: participants sign in weekly; sessions end on sign-out, password change, reset and admin reset, and two-step sign-in protects new sign-ins. Product owner to sign |
| F-06 | A known password could be used to flood a person's mailbox with sign-in codes | Fixed in this phase | At most 5 sign-in code emails per account per hour (`RATE_LIMIT_TWO_FACTOR_EMAIL_*`); `TwoFactorSignInFlowTests` |
| F-07 | People cannot see or end their other sessions | Not met | Follow-up: a "Signed-in devices" list with "Sign out everywhere else" in the next security BRD. **Target: written by 31 Jan 2027** |
| F-08 | Step-up and two-step code hashes used `JWT_SECRET` in production | Fixed in this phase | `STEP_UP_SECRET` and `TWO_FACTOR_SECRET` generated in `render.yaml` |

## Sign-off

| Role | Name | Decision on F-02 to F-05 | Date |
| --- | --- | --- | --- |
| Product owner | | | |
| Security reviewer | | | |
