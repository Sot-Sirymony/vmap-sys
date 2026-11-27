import type { Page } from './vision';

/** V23 (BRD 23.0.0): the Users and Roles pages. Mirrors the server's records. */

export type TwoFactorState = 'ON' | 'DUE' | 'OFF';

export type AdminUserSummary = {
  id: number;
  fullName: string;
  email: string;
  role: string;
  roleName: string;
  status: 'ACTIVE' | 'DISABLED';
  cohortId?: number | null;
  cohortName?: string | null;
  twoFactor: TwoFactorState;
  twoFactorDueAt?: string | null;
  joinedAt?: string | null;
  lastSignInAt?: string | null;
};

/** V33 (D-33-9): the page of accounts is in the shared page shape; the counts are for all accounts. */
export type AdminUserPage = {
  users: Page<AdminUserSummary>;
  totalAccounts: number;
  activeAccounts: number;
  blockedAccounts: number;
  countsByRole: Record<string, number>;
};

export type AdminSecurityEvent = {
  id: number;
  occurredAt: string;
  eventType: string;
  outcome: string;
  actorId?: number | null;
  ipAddress?: string | null;
  targetType?: string | null;
  targetId?: number | null;
  detail?: string | null;
};

export type AdminUserDetail = {
  account: AdminUserSummary;
  createdBy: string;
  roleChangedAt?: string | null;
  roleChangedBy?: string | null;
  blockedAt?: string | null;
  blockedBy?: string | null;
  recordCounts: Record<string, number>;
  recentEvents: AdminSecurityEvent[];
  invitationPending: boolean;
  invitationExpiresAt?: string | null;
  coachedCohorts: { id: number; name: string }[];
};

export type AdminUserFilter = {
  q?: string;
  role?: string;
  status?: string;
  cohortId?: number;
  twoFactor?: string;
  sort?: 'joined' | 'name' | 'lastSignIn';
};

export type CohortCoachChange = { cohortId: number; newCoachId: number | null };

export type AdminUserCreateInput = { fullName: string; email: string; role?: string; cohortId?: number | null };
export type AdminUserUpdateInput = { fullName: string; cohortId: number | null };
export type AdminRoleChangeInput = { role: string; reason: string; cohortChanges?: CohortCoachChange[] };

export type PermissionInfo = {
  key: string;
  group: string;
  label: string;
  description: string;
  adminOnly: boolean;
  always: boolean;
};

export type RoleInfo = {
  key: string;
  name: string;
  description?: string | null;
  builtIn: boolean;
  editable: boolean;
  renameable: boolean;
  holders: number;
  privileged: boolean;
  permissions: string[];
};

export type RoleInput = {
  name: string;
  description?: string;
  permissions: string[];
  reason: string;
  cohortChanges?: CohortCoachChange[];
};

export type InvitationInfo = { fullName: string; email: string; expiresAt: string };
