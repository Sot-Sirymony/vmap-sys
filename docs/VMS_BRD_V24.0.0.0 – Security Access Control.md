# VMS\_BRD\_V24.0.0.0 – Security Access Control

Nov 23, 2026 · @Sirymony Sot

This BRD adds a **Security** page to VMS where an Admin chooses which security methods are allowed for other users. Version 24.0.0.0 is a front-end draft only: it gives the business something to react to before detailed requirements are written.

## 1. Document control

| Field | Value |
| --- | --- |
| Document | VMS\_BRD\_V24.0.0.0 |
| Module | Administration › Security |
| Status | Draft – prototype for requirement discussion |
| Source | security\_list.md (case study list of authentication and authorization types) |
| Phase | Front-end design only (no back end, no real enforcement) |

| Version | Date | Change |
| --- | --- | --- |
| 24.0.0.0 | 2026-11-23 | First draft: Security page and security catalogue |

## 2. Purpose and background

Admins need one place to control which login and access methods VMS offers to its users. Today the security methods are only listed in a case study file (security\_list.md); none of them can be switched on or off from the application.

The Security page will:

- List every security method from the catalogue, grouped the way the case study groups them
- Let an Admin allow or block each method for other users
- Show at a glance how many methods are allowed per group

The list covers two families: **Authentication (AuthN)** checks who the user is; **Authorization (AuthZ)** decides what the user may do.

## 3. Scope

**In scope (V24.0.0.0 prototype)**

- New menu item: Administration › Security
- Read-only catalogue of all 57 security items from security\_list.md (section 5)
- Allow / block switch per item, per group (bulk) and for the whole list
- Search and filter (All / Allowed / Blocked, AuthN / AuthZ)
- Unsaved-changes bar with Save and Discard
- Simple change summary before saving

**Out of scope (later requirements)**

- Back-end storage, APIs and real enforcement at login
- Per-method settings (e.g. password length, OTP expiry, SAML metadata)
- Per-role or per-user-group assignment (prototype applies to all non-Admin users)
- Audit log screen, approval workflow, notifications
- Adding, editing or deleting catalogue items

## 4. User roles

| Role | Security page access | Effect of the page |
| --- | --- | --- |
| Admin | View and change all items | Admin decides which methods are allowed |
| Other users (all non-Admin) | No access; page hidden from menu | Can only use methods the Admin allowed |

Assumption: the prototype has a single Admin level. Whether a Super Admin or per-role setting is needed is an open question (section 10).

## 5. Security catalogue

The catalogue holds 57 items in 7 groups: 33 Authentication and 24 Authorization, taken from security\_list.md. "Default" is the suggested starting state in the prototype; "Tag" marks items the source calls deprecated or legacy.

### 5.1 Authentication (33)

| ID | Group | Security | Description | Default | Tag |
| --- | --- | --- | --- | --- | --- |
| AN-F01 | By factor | Something you know | Password, PIN, passphrase, security questions | Allowed |  |
| AN-F02 | By factor | Something you have | Phone (SMS/email codes), hardware token, smart card, authenticator app (TOTP/HOTP) | Allowed |  |
| AN-F03 | By factor | Something you are | Biometrics: fingerprint, face, iris, voice, palm | Blocked |  |
| AN-F04 | By factor | Somewhere you are | IP address, geolocation, network | Blocked |  |
| AN-F05 | By factor | Something you do | Behavioral: typing rhythm, mouse movement, gait | Blocked |  |
| AN-M01 | By method | Password-based | Username plus password | Allowed |  |
| AN-M02 | By method | Multi-factor (MFA / 2FA) | Two or more factors | Allowed |  |
| AN-M03 | By method | Passwordless | Magic links, one-time codes, passkeys | Blocked |  |
| AN-M04 | By method | Passkeys / FIDO2 / WebAuthn | Public-key login tied to a device | Blocked |  |
| AN-M05 | By method | Certificate-based | X.509 client certificates, mutual TLS | Blocked |  |
| AN-M06 | By method | Token-based | JWTs, opaque bearer tokens, session tokens | Blocked |  |
| AN-M07 | By method | Session / cookie-based | Server-side session plus cookie | Allowed |  |
| AN-M08 | By method | API key | Static key sent with each request | Blocked |  |
| AN-M09 | By method | HTTP Basic | Base64 username and password in a header | Blocked |  |
| AN-M10 | By method | HTTP Digest | Hashed challenge-response | Blocked |  |
| AN-M11 | By method | HMAC signatures | Requests signed with a shared secret (e.g. AWS SigV4) | Blocked |  |
| AN-M12 | By method | Kerberos | Ticket-based, Windows / Active Directory | Blocked |  |
| AN-M13 | By method | NTLM | Older Windows challenge-response | Blocked | Legacy |
| AN-M14 | By method | LDAP bind | Login checked against a directory | Blocked |  |
| AN-M15 | By method | RADIUS / TACACS+ | Network device and VPN login | Blocked |  |
| AN-M16 | By method | Risk-based / adaptive | Extra steps only when context looks risky | Blocked |  |
| AN-M17 | By method | Continuous authentication | Identity rechecked during the session | Blocked |  |
| AN-S01 | Federated / SSO | SAML 2.0 | XML-based enterprise SSO | Blocked |  |
| AN-S02 | Federated / SSO | OpenID Connect (OIDC) | Identity layer on OAuth 2.0 | Blocked |  |
| AN-S03 | Federated / SSO | Social login | Sign in with Google / Apple / GitHub | Blocked |  |
| AN-S04 | Federated / SSO | WS-Federation | Microsoft's legacy federation protocol | Blocked | Legacy |
| AN-S05 | Federated / SSO | CAS | Central Authentication Service | Blocked |  |
| AN-X01 | Machine-to-machine | OAuth 2.0 Client Credentials | App-to-app token login | Blocked |  |
| AN-X02 | Machine-to-machine | Service accounts | Non-human accounts for systems | Blocked |  |
| AN-X03 | Machine-to-machine | mTLS | Mutual TLS between services | Blocked |  |
| AN-X04 | Machine-to-machine | Workload identity (SPIFFE/SPIRE) | Identity issued to workloads | Blocked |  |
| AN-X05 | Machine-to-machine | Cloud IAM roles | Cloud provider role-based identity | Blocked |  |
| AN-X06 | Machine-to-machine | SSH keys | Key-pair login for servers | Blocked |  |

### 5.2 Authorization (24)

| ID | Group | Security | Description | Default | Tag |
| --- | --- | --- | --- | --- | --- |
| AZ-A01 | Access control model | DAC (Discretionary) | Resource owner decides who gets access | Blocked |  |
| AZ-A02 | Access control model | MAC (Mandatory) | Central policy and security labels decide | Blocked |  |
| AZ-A03 | Access control model | RBAC (Role-Based) | Permissions attached to roles (admin, editor, viewer) | Allowed |  |
| AZ-A04 | Access control model | ABAC (Attribute-Based) | Rules use user, resource, action and environment attributes | Blocked |  |
| AZ-A05 | Access control model | PBAC (Policy-Based) | Central written policies (OPA/Rego, AWS IAM, Cedar) | Blocked |  |
| AZ-A06 | Access control model | ReBAC (Relationship-Based) | Access from relationships in a graph (Zanzibar, OpenFGA) | Blocked |  |
| AZ-A07 | Access control model | ACL (Access Control Lists) | Allowed users or groups listed on each resource | Blocked |  |
| AZ-A08 | Access control model | Capability-based | Holding an unforgeable token grants the right | Blocked |  |
| AZ-A09 | Access control model | Rule-based | System-wide rules such as time-of-day or IP limits | Blocked |  |
| AZ-A10 | Access control model | Context-aware / risk-adaptive | Decisions change with device, location or risk score | Blocked |  |
| AZ-D01 | Delegated authorization | OAuth 2.0 Authorization Code (+PKCE) | Standard user-consent grant | Blocked |  |
| AZ-D02 | Delegated authorization | OAuth 2.0 Client Credentials | Grant for app-to-app access | Blocked |  |
| AZ-D03 | Delegated authorization | OAuth 2.0 Device Authorization | Grant for TVs and CLIs | Blocked |  |
| AZ-D04 | Delegated authorization | OAuth 2.0 Refresh Token | Renew access without new login | Blocked |  |
| AZ-D05 | Delegated authorization | OAuth 2.0 Implicit | Old browser grant | Blocked | Deprecated |
| AZ-D06 | Delegated authorization | OAuth 2.0 Resource Owner Password | App collects user password directly | Blocked | Deprecated |
| AZ-D07 | Delegated authorization | OAuth 2.1 | Combines current best practices | Blocked |  |
| AZ-D08 | Delegated authorization | UMA (User-Managed Access) | Owner-controlled sharing | Blocked |  |
| AZ-D09 | Delegated authorization | Scopes and claims | Fine-grained permissions inside tokens | Blocked |  |
| AZ-D10 | Delegated authorization | GNAP | Proposed successor to OAuth | Blocked |  |
| AZ-P01 | Architectural principle | Least privilege | Grant only what's needed | Allowed |  |
| AZ-P02 | Architectural principle | Zero Trust | Verify every request; never trust by network alone | Blocked |  |
| AZ-P03 | Architectural principle | Just-in-Time (JIT) access | Temporary, elevated permissions | Blocked |  |
| AZ-P04 | Architectural principle | Separation of duties | No single person controls a critical process end to end | Blocked |  |

## 6. Functional requirements

| ID | Requirement | Priority |
| --- | --- | --- |
| FR-01 | Show a "Security" menu item under Administration, visible to Admin only | Must |
| FR-02 | List all 57 catalogue items, grouped by family (AuthN / AuthZ) and group | Must |
| FR-03 | Each item shows ID, name, description, tag (if any) and an Allow switch | Must |
| FR-04 | Admin can allow or block a single item with its switch | Must |
| FR-05 | Admin can allow all or block all items within one group | Must |
| FR-06 | Admin can allow all or block all items on the page | Should |
| FR-07 | Each group header shows "x of y allowed"; page header shows overall totals | Must |
| FR-08 | Search by item name, ID or description | Should |
| FR-09 | Filter by status (All / Allowed / Blocked) and family (All / AuthN / AuthZ) | Should |
| FR-10 | Groups can be collapsed and expanded | Could |
| FR-11 | Changes are held as unsaved until Admin clicks Save; a bar shows the number of pending changes | Must |
| FR-12 | Discard reverts all unsaved changes | Must |
| FR-13 | Save opens a summary listing items being allowed and blocked; Admin confirms | Should |
| FR-14 | Reset to default restores the suggested defaults in section 5 | Could |
| FR-15 | Leaving the page with unsaved changes asks for confirmation | Should |

## 7. Business rules

| ID | Rule |
| --- | --- |
| BR-01 | Only items set to Allowed can be used by other users; Blocked items are hidden from or rejected for them |
| BR-02 | At least one Authentication item must stay allowed; blocking the last one is not permitted, and "Block all" keeps Password-based allowed |
| BR-03 | Allowing an item tagged Deprecated shows a warning that needs confirmation; bulk "Allow" actions skip Deprecated items |
| BR-04 | Allowing "Multi-factor (MFA / 2FA)" requires at least two "By factor" items to be allowed; the page warns if not |
| BR-05 | Admin settings apply to all non-Admin users in the prototype (no per-role setting yet) |
| BR-06 | The Admin's own login methods are not changed by this page, so the Admin cannot lock themselves out |
| BR-07 | Every saved change should record who changed it and when (for the later audit log) |

BR-04 and BR-06 are proposals for discussion; confirm or remove them in the requirements review.

## 8. UI prototype

The clickable draft is here: [VMS V24.0.0.0 – Security Page Prototype](https://claude.ai/artifact/8YpVivxCNMU1LWv63UoVYM). It is front end only; nothing is stored, and a refresh resets it.

The screen, top to bottom:

1. **Header and side menu**: Administration menu with Security selected; "Draft prototype" badge
2. **Page title**: Security, with one line explaining the page
3. **Summary cards**: allowed in total, Authentication allowed, Authorization allowed (e.g. 7 of 57)
4. **Messages**: red alert when a rule stops a change (BR-02); amber warning for MFA without two factors (BR-04)
5. **Toolbar**: search; Family filter (All / AuthN / AuthZ); Status filter (All / Allowed / Blocked); Allow all, Block all, Reset to default
6. **Group cards** (7): collapsible header with family badge, group name, "x of y allowed", Allow group / Block group
7. **Item rows** (57): switch, name, ID, Deprecated / Legacy tag, "Unsaved" marker, description, Allowed / Blocked label
8. **Unsaved-changes bar**: appears at the bottom with the change count, Discard and Save changes
9. **Save summary dialog**: lists "Will be allowed" and "Will be blocked" before Confirm and save
10. **Deprecated warning dialog**: Keep blocked / Allow anyway

## 9. Non-functional requirements

| ID | Requirement |
| --- | --- |
| NFR-01 | Page loads the full catalogue (57 items) in under 2 seconds |
| NFR-02 | Works on desktop and tablet; side menu stacks on narrow screens |
| NFR-03 | Accessible: switches are keyboard operable and announce Allowed / Blocked; text contrast at least 4.5:1 |
| NFR-04 | Security page and its API restricted to Admin role (server-side check in later phase) |
| NFR-05 | Catalogue is data-driven so items can be added later without UI changes |

## 10. Open questions and assumptions

**Open questions**

- [ ] Should settings apply per role or user group, instead of to all non-Admin users?
- [ ] Is a Super Admin level needed above Admin?
- [ ] Which methods does VMS actually support today? Items not supported could be shown as "Coming soon" or hidden
- [ ] Do allowed methods need their own settings (password policy, OTP expiry, SAML/OIDC provider details)?
- [ ] What happens to signed-in users when a method they use is blocked: immediate logout or next login?
- [ ] Are the suggested defaults in section 5 correct for VMS?
- [ ] Should Authorization models be selectable, or shown as information only? Most systems use one model (e.g. RBAC) at a time
- [ ] Is an approval step or audit log screen needed for this page?

**Assumptions**

- The catalogue is fixed to the 57 items in security\_list.md for this phase
- "Other users" means all non-Admin users
- Item IDs (AN-xxx, AZ-xxx) are draft IDs created for this BRD
