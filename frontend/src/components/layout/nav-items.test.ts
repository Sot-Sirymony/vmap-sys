import { describe, expect, it } from 'vitest';
import { navGroupsFor } from './nav-items';

const user = (role: string, permissions?: string[]) => ({ id: 1, fullName: 'T', email: 't@example.com', role, permissions });
const paths = (who?: ReturnType<typeof user>) => navGroupsFor(who).flatMap((group) => group.items.map((item) => item.to));

describe('navGroupsFor', () => {
  it('shows coaching to coaches and admins, and cohorts to admins only (built-in roles, V9)', () => {
    expect(paths(user('USER'))).not.toContain('/coach');
    expect(paths(undefined)).not.toContain('/coach');
    expect(paths(user('COACH'))).toContain('/coach');
    expect(paths(user('COACH'))).not.toContain('/settings/cohorts');
    expect(paths(user('ADMIN'))).toEqual(expect.arrayContaining(['/coach', '/settings/cohorts']));
  });

  it('follows the permissions the server sent, not the role name (V23 UM-FR-05)', () => {
    const coachWithLog = user('COACH', ['USE_OWN_PLAN', 'COACH_COHORTS', 'VIEW_SECURITY_LOG']);
    expect(paths(coachWithLog)).toEqual(expect.arrayContaining(['/coach', '/settings/security-log']));
    expect(paths(coachWithLog)).not.toContain('/settings/cohorts');
    expect(paths(user('ADMIN', ['USE_OWN_PLAN']))).not.toContain('/settings/cohorts');
  });
});
