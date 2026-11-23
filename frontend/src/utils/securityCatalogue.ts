/**
 * V24.0.0.0 (BRD section 5, NFR-05): the security catalogue shown on the
 * Security access prototype page. Data only, so items can be added without
 * changing the page. Taken from docs/security_list.md.
 *
 * `inVms` answers the BRD's open question "which methods does VMS support
 * today?" so reviewers can see which switches could ever be real.
 */
export type SecurityFamily = 'AUTHN' | 'AUTHZ';
export type SecurityTag = 'Deprecated' | 'Legacy';
export type InVms = 'yes' | 'partly' | 'no';

export type SecurityItem = {
  id: string;
  family: SecurityFamily;
  group: string;
  name: string;
  description: string;
  defaultAllowed: boolean;
  tag?: SecurityTag;
  inVms: InVms;
};

export type SecurityGroup = { family: SecurityFamily; name: string };

export const PASSWORD_BASED_ID = 'AN-M01';
export const MFA_ID = 'AN-M02';
export const FACTOR_GROUP = 'By factor';

type Row = [id: string, name: string, description: string, allowed: boolean, inVms: InVms, tag?: SecurityTag];

function rows(family: SecurityFamily, group: string, list: Row[]): SecurityItem[] {
  return list.map(([id, name, description, defaultAllowed, inVms, tag]) => ({
    id, family, group, name, description, defaultAllowed, inVms, ...(tag ? { tag } : {}),
  }));
}

export const SECURITY_CATALOGUE: SecurityItem[] = [
  ...rows('AUTHN', FACTOR_GROUP, [
    ['AN-F01', 'Something you know', 'Password, PIN, passphrase, security questions', true, 'yes'],
    ['AN-F02', 'Something you have', 'Phone (SMS/email codes), hardware token, smart card, authenticator app (TOTP/HOTP)', true, 'yes'],
    ['AN-F03', 'Something you are', 'Biometrics: fingerprint, face, iris, voice, palm', false, 'no'],
    ['AN-F04', 'Somewhere you are', 'IP address, geolocation, network', false, 'no'],
    ['AN-F05', 'Something you do', 'Behavioral: typing rhythm, mouse movement, gait', false, 'no'],
  ]),
  ...rows('AUTHN', 'By method', [
    [PASSWORD_BASED_ID, 'Password-based', 'Username plus password', true, 'yes'],
    [MFA_ID, 'Multi-factor (MFA / 2FA)', 'Two or more factors', true, 'yes'],
    ['AN-M03', 'Passwordless', 'Magic links, one-time codes, passkeys', false, 'no'],
    ['AN-M04', 'Passkeys / FIDO2 / WebAuthn', 'Public-key login tied to a device', false, 'no'],
    ['AN-M05', 'Certificate-based', 'X.509 client certificates, mutual TLS', false, 'no'],
    ['AN-M06', 'Token-based', 'JWTs, opaque bearer tokens, session tokens', false, 'yes'],
    ['AN-M07', 'Session / cookie-based', 'Server-side session plus cookie', true, 'yes'],
    ['AN-M08', 'API key', 'Static key sent with each request', false, 'no'],
    ['AN-M09', 'HTTP Basic', 'Base64 username and password in a header', false, 'no'],
    ['AN-M10', 'HTTP Digest', 'Hashed challenge-response', false, 'no'],
    ['AN-M11', 'HMAC signatures', 'Requests signed with a shared secret (e.g. AWS SigV4)', false, 'no'],
    ['AN-M12', 'Kerberos', 'Ticket-based, Windows / Active Directory', false, 'no'],
    ['AN-M13', 'NTLM', 'Older Windows challenge-response', false, 'no', 'Legacy'],
    ['AN-M14', 'LDAP bind', 'Login checked against a directory', false, 'no'],
    ['AN-M15', 'RADIUS / TACACS+', 'Network device and VPN login', false, 'no'],
    ['AN-M16', 'Risk-based / adaptive', 'Extra steps only when context looks risky', false, 'partly'],
    ['AN-M17', 'Continuous authentication', 'Identity rechecked during the session', false, 'no'],
  ]),
  ...rows('AUTHN', 'Federated / SSO', [
    ['AN-S01', 'SAML 2.0', 'XML-based enterprise SSO', false, 'no'],
    ['AN-S02', 'OpenID Connect (OIDC)', 'Identity layer on OAuth 2.0', false, 'yes'],
    ['AN-S03', 'Social login', 'Sign in with Google / Apple / GitHub', false, 'yes'],
    ['AN-S04', 'WS-Federation', "Microsoft's legacy federation protocol", false, 'no', 'Legacy'],
    ['AN-S05', 'CAS', 'Central Authentication Service', false, 'no'],
  ]),
  ...rows('AUTHN', 'Machine-to-machine', [
    ['AN-X01', 'OAuth 2.0 Client Credentials', 'App-to-app token login', false, 'no'],
    ['AN-X02', 'Service accounts', 'Non-human accounts for systems', false, 'no'],
    ['AN-X03', 'mTLS', 'Mutual TLS between services', false, 'no'],
    ['AN-X04', 'Workload identity (SPIFFE/SPIRE)', 'Identity issued to workloads', false, 'no'],
    ['AN-X05', 'Cloud IAM roles', 'Cloud provider role-based identity', false, 'no'],
    ['AN-X06', 'SSH keys', 'Key-pair login for servers', false, 'no'],
  ]),
  ...rows('AUTHZ', 'Access control model', [
    ['AZ-A01', 'DAC (Discretionary)', 'Resource owner decides who gets access', false, 'partly'],
    ['AZ-A02', 'MAC (Mandatory)', 'Central policy and security labels decide', false, 'no'],
    ['AZ-A03', 'RBAC (Role-Based)', 'Permissions attached to roles (admin, editor, viewer)', true, 'yes'],
    ['AZ-A04', 'ABAC (Attribute-Based)', 'Rules use user, resource, action and environment attributes', false, 'no'],
    ['AZ-A05', 'PBAC (Policy-Based)', 'Central written policies (OPA/Rego, AWS IAM, Cedar)', false, 'no'],
    ['AZ-A06', 'ReBAC (Relationship-Based)', 'Access from relationships in a graph (Zanzibar, OpenFGA)', false, 'no'],
    ['AZ-A07', 'ACL (Access Control Lists)', 'Allowed users or groups listed on each resource', false, 'no'],
    ['AZ-A08', 'Capability-based', 'Holding an unforgeable token grants the right', false, 'yes'],
    ['AZ-A09', 'Rule-based', 'System-wide rules such as time-of-day or IP limits', false, 'no'],
    ['AZ-A10', 'Context-aware / risk-adaptive', 'Decisions change with device, location or risk score', false, 'no'],
  ]),
  ...rows('AUTHZ', 'Delegated authorization', [
    ['AZ-D01', 'OAuth 2.0 Authorization Code (+PKCE)', 'Standard user-consent grant', false, 'partly'],
    ['AZ-D02', 'OAuth 2.0 Client Credentials', 'Grant for app-to-app access', false, 'no'],
    ['AZ-D03', 'OAuth 2.0 Device Authorization', 'Grant for TVs and CLIs', false, 'no'],
    ['AZ-D04', 'OAuth 2.0 Refresh Token', 'Renew access without new login', false, 'partly'],
    ['AZ-D05', 'OAuth 2.0 Implicit', 'Old browser grant', false, 'no', 'Deprecated'],
    ['AZ-D06', 'OAuth 2.0 Resource Owner Password', 'App collects user password directly', false, 'no', 'Deprecated'],
    ['AZ-D07', 'OAuth 2.1', 'Combines current best practices', false, 'no'],
    ['AZ-D08', 'UMA (User-Managed Access)', 'Owner-controlled sharing', false, 'no'],
    ['AZ-D09', 'Scopes and claims', 'Fine-grained permissions inside tokens', false, 'no'],
    ['AZ-D10', 'GNAP', 'Proposed successor to OAuth', false, 'no'],
  ]),
  ...rows('AUTHZ', 'Architectural principle', [
    ['AZ-P01', 'Least privilege', "Grant only what's needed", true, 'yes'],
    ['AZ-P02', 'Zero Trust', 'Verify every request; never trust by network alone', false, 'no'],
    ['AZ-P03', 'Just-in-Time (JIT) access', 'Temporary, elevated permissions', false, 'no'],
    ['AZ-P04', 'Separation of duties', 'No single person controls a critical process end to end', false, 'no'],
  ]),
];

/** The groups in catalogue order. */
export const SECURITY_GROUPS: SecurityGroup[] = SECURITY_CATALOGUE.reduce<SecurityGroup[]>((groups, item) => {
  if (!groups.some((group) => group.name === item.group)) groups.push({ family: item.family, name: item.group });
  return groups;
}, []);
