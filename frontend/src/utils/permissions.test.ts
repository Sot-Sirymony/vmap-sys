import { describe, expect, it } from 'vitest';
import { can, isPrivileged, permissionsOf } from './permissions';

const user = (role: string, permissions?: string[]) => ({ id: 1, fullName: 'T', email: 't@example.com', role, permissions });

describe('permissions (V23 UM-FR-05)', () => {
  it('uses the list the server sent', () => {
    expect(can(user('COACH', ['USE_OWN_PLAN', 'VIEW_USERS']), 'VIEW_USERS')).toBe(true);
    expect(can(user('COACH', ['USE_OWN_PLAN', 'VIEW_USERS']), 'COACH_COHORTS')).toBe(false);
  });

  it('falls back to the built-in roles for sessions saved before V23', () => {
    expect(permissionsOf(user('USER'))).toEqual(['USE_OWN_PLAN']);
    expect(can(user('COACH'), 'COACH_COHORTS')).toBe(true);
    expect(can(user('ADMIN'), 'MANAGE_ROLES')).toBe(true);
    expect(permissionsOf(user('SOMETHING_ELSE'))).toEqual([]);
    expect(permissionsOf(null)).toEqual([]);
  });

  it('calls a person privileged when they have anything beyond their own plan', () => {
    expect(isPrivileged(user('USER'))).toBe(false);
    expect(isPrivileged(user('COACH'))).toBe(true);
    expect(isPrivileged(user('USER', ['USE_OWN_PLAN', 'HANDLE_SUPPORT']))).toBe(true);
  });
});
