There's no official complete list, but these are the main types you'll run into. Authentication (AuthN) checks **who you are**. Authorization (AuthZ) decides **what you're allowed to do**.

## Authentication types

**By factor (what proves your identity)**
- **Something you know:** password, PIN, passphrase, security questions
- **Something you have:** phone (SMS/email codes), hardware token (YubiKey), smart card, authenticator app (TOTP/HOTP)
- **Something you are (biometrics):** fingerprint, face, iris, voice, palm
- **Somewhere you are:** IP address, geolocation, network
- **Something you do (behavioral):** typing rhythm, mouse movement, gait

**By method or scheme**
- **Password-based:** username plus password
- **Multi-factor (MFA / 2FA):** two or more of the factors above
- **Passwordless:** magic links, one-time codes, passkeys
- **Passkeys / FIDO2 / WebAuthn:** public-key login tied to a device
- **Certificate-based:** X.509 client certificates, mutual TLS (mTLS)
- **Token-based:** JWTs, opaque bearer tokens, session tokens
- **Session/cookie-based:** server-side session plus cookie
- **API key:** a static key sent with each request
- **HTTP Basic:** Base64 username and password in a header
- **HTTP Digest:** a hashed challenge-response
- **HMAC signatures:** requests signed with a shared secret (e.g., AWS SigV4)
- **Kerberos:** ticket-based, common in Windows/Active Directory
- **NTLM:** older Windows challenge-response
- **LDAP bind:** login checked against a directory
- **RADIUS / TACACS+:** network device and VPN login
- **Risk-based / adaptive:** extra steps only when context looks risky
- **Continuous authentication:** identity rechecked during the session

**Federated identity and single sign-on (SSO)**
- **SAML 2.0:** XML-based enterprise SSO
- **OpenID Connect (OIDC):** an identity layer built on OAuth 2.0
- **Social login:** "Sign in with Google / Apple / GitHub" (usually OIDC)
- **WS-Federation:** Microsoft's legacy federation protocol
- **CAS:** Central Authentication Service, common at universities

**Machine-to-machine**
- OAuth 2.0 Client Credentials, service accounts, mTLS, workload identity (SPIFFE/SPIRE), cloud IAM roles, SSH keys

## Authorization models

**Access control models**
- **DAC (Discretionary):** the owner of a resource decides who gets access (e.g., Unix file permissions)
- **MAC (Mandatory):** a central policy and security labels decide (e.g., SELinux, military classifications)
- **RBAC (Role-Based):** permissions are attached to roles like admin, editor or viewer
- **ABAC (Attribute-Based):** rules use attributes of the user, resource, action and environment
- **PBAC (Policy-Based):** central written policies (e.g., OPA/Rego, AWS IAM policies, Cedar)
- **ReBAC (Relationship-Based):** access comes from relationships in a graph (e.g., Google Zanzibar, OpenFGA, SpiceDB)
- **ACL (Access Control Lists):** a list of allowed users or groups on each resource
- **Capability-based:** holding an unforgeable token grants the right
- **Rule-based:** system-wide rules, such as time-of-day or IP limits
- **Context-aware / risk-adaptive:** decisions change with device, location or risk score

**Protocols and mechanisms for delegated authorization**
- **OAuth 2.0 grant types:**
  - Authorization Code (+ PKCE)
  - Client Credentials
  - Device Authorization (for TVs and CLIs)
  - Refresh Token
  - Implicit and Resource Owner Password (both deprecated)
- **OAuth 2.1:** brings together current best practices
- **UMA (User-Managed Access):** owner-controlled sharing
- **Scopes and claims:** fine-grained permissions inside tokens
- **GNAP:** a newer proposed successor to OAuth

**Architectural principles**
- **Least privilege:** grant only what's needed
- **Zero Trust:** verify every request; never trust by network location alone
- **Just-in-Time (JIT) access:** temporary, elevated permissions
- **Separation of duties:** no single person controls a critical process from start to finish

## Quick summary
| | Authentication | Authorization |
|---|---|---|
| Question | Who are you? | What can you do? |
| Happens | First | After authentication |
| Examples | Password, MFA, OIDC, SAML | RBAC, ABAC, OAuth scopes, ACLs |
| HTTP error | 401 Unauthorized | 403 Forbidden |

I can go deeper on any of these, such as how OAuth 2.0 and OIDC fit together or when to choose RBAC, ABAC or ReBAC.