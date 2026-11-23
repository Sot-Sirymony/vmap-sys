import type { AuthUser } from '../types/auth';

/**
 * V23 (BRD 23.0.0, UM-FR-05): what the signed-in person may do, as the server
 * sent it with the session. The page uses it only to show or hide menus and
 * buttons; the server checks every request again.
 */
export type Permission =
  | 'USE_OWN_PLAN'
  | 'COACH_COHORTS'
  | 'VIEW_USERS'
  | 'MANAGE_USERS'
  | 'DELETE_USERS'
  | 'RESET_TWO_FACTOR'
  | 'MANAGE_COHORTS'
  | 'MANAGE_MODULE_CONTENT'
  | 'MANAGE_INTERFACE_TEXT'
  | 'HANDLE_SUPPORT'
  | 'MANAGE_AI_SETTINGS'
  | 'VIEW_AI_REPORTS'
  | 'VIEW_SECURITY_LOG'
  | 'MANAGE_ROLES';

const ALL: Permission[] = [
  'USE_OWN_PLAN', 'COACH_COHORTS', 'VIEW_USERS', 'MANAGE_USERS', 'DELETE_USERS', 'RESET_TWO_FACTOR', 'MANAGE_COHORTS',
  'MANAGE_MODULE_CONTENT', 'MANAGE_INTERFACE_TEXT', 'HANDLE_SUPPORT', 'MANAGE_AI_SETTINGS', 'VIEW_AI_REPORTS',
  'VIEW_SECURITY_LOG', 'MANAGE_ROLES',
];

/**
 * A session saved before V23 has no permission list; until it is renewed (within
 * 30 minutes), fall back to what the built-in roles had.
 */
const BUILT_IN: Record<string, Permission[]> = {
  USER: ['USE_OWN_PLAN'],
  COACH: ['USE_OWN_PLAN', 'COACH_COHORTS'],
  ADMIN: ALL,
};

export function permissionsOf(user: AuthUser | null | undefined): Permission[] {
  if (!user) {
    return [];
  }
  if (Array.isArray(user.permissions)) {
    return user.permissions as Permission[];
  }
  return BUILT_IN[user.role] ?? [];
}

export function can(user: AuthUser | null | undefined, permission: Permission): boolean {
  return permissionsOf(user).includes(permission);
}

/** UM-FR-06: anything beyond one's own plan; such accounts must use two-step sign-in. */
export function isPrivileged(user: AuthUser | null | undefined): boolean {
  return permissionsOf(user).some((permission) => permission !== 'USE_OWN_PLAN');
}

/** UM-FR-45: each permission in plain words, for the person's own Account page (labels match the server catalogue). */
export const PERMISSION_LABELS: Record<Permission, string> = {
  USE_OWN_PLAN: 'Use own plan',
  COACH_COHORTS: 'Coach cohorts',
  VIEW_USERS: 'View users',
  MANAGE_USERS: 'Manage users',
  DELETE_USERS: 'Delete users',
  RESET_TWO_FACTOR: 'Reset two-step sign-in',
  MANAGE_COHORTS: 'Manage cohorts',
  MANAGE_MODULE_CONTENT: 'Manage module content',
  MANAGE_INTERFACE_TEXT: 'Manage interface text',
  HANDLE_SUPPORT: 'Handle support',
  MANAGE_AI_SETTINGS: 'Manage AI settings',
  VIEW_AI_REPORTS: 'View AI reports',
  VIEW_SECURITY_LOG: 'View security log',
  MANAGE_ROLES: 'Manage roles',
};

/**
 * D-23-9: the viewer may act on an account, or give a role, only if they hold
 * every permission it has. Used to hide buttons and options; the server checks again.
 */
export function holdsAll(user: AuthUser | null | undefined, needed: readonly string[]): boolean {
  const mine = permissionsOf(user) as string[];
  return needed.every((permission) => mine.includes(permission));
}
